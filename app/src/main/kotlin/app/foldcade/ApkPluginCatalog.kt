package app.foldcade

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.annotation.MainThread
import app.foldcade.language.ApkPluginCopy
import app.foldcade.language.PluginCatalogSetting
import app.foldcade.language.PluginCatalogStatus
import app.foldcade.language.PluginSource
import app.foldcade.language.PluginRegistry
import app.foldcade.net.PublicHttps
import app.foldcade.romm.writeDurably
import java.io.File
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal const val MAX_PLUGIN_APK_BYTES = 128L * 1024 * 1024
/** A page that opens again soon after a check shows that check rather than starting another. */
internal const val PLUGIN_CHECK_FRESH_MS = 10 * 60 * 1000L

/** The registry pins a repository, never a version: whichever full release is latest is the one offered. */
internal fun latestReleaseApi(source: PluginSource): String = "https://api.github.com/repos/${source.repository}/releases/latest"

/** [versionCode] is the release's own claim, a hint for "Update available". The downloaded APK still decides. */
internal data class PluginRelease(val tag: String, val url: String, val bytes: Long, val versionCode: Long? = null, val body: String = "", val versionName: String? = null) {
    val name: String? get() = versionName ?: tag.removePrefix("v").ifBlank { null }
}

/** Version hints may be absent or disagree with a replaced rolling asset. The asset identifies reuse. */
internal fun mayReuseStagedRelease(staged: PluginRelease?, latest: PluginRelease): Boolean =
    staged != null && staged.url == latest.url && staged.bytes == latest.bytes

internal fun restoredAutomaticSession(id: Int, automatic: Boolean): Int? = id.takeIf { automatic && it >= 0 }
internal fun shouldAbandonInstallSession(committed: Boolean?): Boolean = committed == false

private val VERSION_CODE_LINE = Regex("""versionCode:[ \t]*([1-9][0-9]{0,17})""")

/** A `versionCode: N` line in the release notes, as Foldcade's and its plugins' release scripts write it. */
internal fun releaseVersionCode(body: String): Long? =
    body.lineSequence().firstNotNullOfOrNull { VERSION_CODE_LINE.matchEntire(it.trim())?.groupValues?.get(1)?.toLongOrNull() }

/** A missing publication or asset is an ordinary empty catalog. Other malformed responses fail. */
internal enum class ReleaseChannel { Stable, Prerelease }
internal fun prereleaseApi(source: PluginSource): String = "https://api.github.com/repos/${source.repository}/releases/tags/pre-release"

/** Stable wins a tie, including two releases without version hints. */
internal fun chooseRelease(stable: PluginRelease?, prerelease: PluginRelease?): PluginRelease? = when {
    stable == null -> prerelease
    prerelease == null -> stable
    (prerelease.versionCode ?: 0) > (stable.versionCode ?: 0) -> prerelease
    else -> stable
}

internal fun parsePluginRelease(json: String, source: PluginSource, channel: ReleaseChannel = ReleaseChannel.Stable): PluginRelease? {
    val root = JSONObject(json)
    if (root.optBoolean("draft")) return null
    if (channel == ReleaseChannel.Stable && root.optBoolean("prerelease")) return null
    if (channel == ReleaseChannel.Prerelease && (!root.optBoolean("prerelease") || root.optString("tag_name") != "pre-release")) return null
    val assets = root.getJSONArray("assets")
    val matches = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }.filter { it.optString("name") == source.asset }
    if (matches.isEmpty()) return null
    require(matches.size == 1) { "Duplicate release asset" }
    val asset = matches.single()
    val bytes = asset.getLong("size")
    require(bytes in 1..MAX_PLUGIN_APK_BYTES) { "Invalid asset size" }
    val url = asset.getString("browser_download_url")
    val uri = URI(url)
    require(uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 && uri.rawUserInfo == null &&
        uri.rawFragment == null && uri.rawQuery == null && uri.rawPath.startsWith("/${source.repository}/releases/download/") &&
        uri.rawPath.endsWith("/${source.asset}") && uri.rawPath.split('/').none { it == "." || it == ".." }) { "Unexpected release URL" }
    val body = if (root.isNull("body")) "" else root.optString("body")
    val name = body.lineSequence().firstOrNull { it.startsWith("versionName:") }?.substringAfter(':')?.trim()?.ifBlank { null }
    return PluginRelease(root.optString("tag_name"), url, bytes, releaseVersionCode(body), body, name)
}

/** What a check can say before anything is downloaded. Without a hint, only the APK can tell. */
internal fun releaseStatus(release: PluginRelease?, installedCode: Long?): PluginCatalogStatus = when {
    release == null -> PluginCatalogStatus.NotPublished
    installedCode == null || release.versionCode == null -> PluginCatalogStatus.Ready
    release.versionCode > installedCode -> PluginCatalogStatus.UpdateAvailable
    else -> PluginCatalogStatus.Current
}

/**
 * What a status shown earlier should become now, or null to keep it. It changes only for news:
 * Android allowed installs while the row asked for that, or the installed version is no longer
 * the one the status was worked out against (installed, updated or removed some other way).
 * That keeps an install result and Not published. Installed stays while the plugin is there,
 * so discovery can still move on to its consent, however late Android reports the package.
 * A running check, download or install is never interrupted, except an install whose Android
 * session has gone without a word: Android does not always report a declined confirmation, so
 * the version installed then says whether it took. Declined, the release is offered again as before.
 */
internal fun reassessedStatus(
    current: PluginCatalogSetting,
    assessedCode: Long?,
    installedCode: Long?,
    installsAllowed: Boolean,
    release: PluginRelease?,
    sessionGone: Boolean = false,
    /**
     * An attempt was settled as declined because its session went quietly. Android may report
     * the new package a little after the session goes, so a version change seen later is that
     * install arriving, not an update made some other way.
     */
    quietAttempt: Boolean = false,
): PluginCatalogStatus? = when {
    current.status == PluginCatalogStatus.Installing && sessionGone ->
        if (installedCode != null && installedCode != assessedCode) PluginCatalogStatus.Installed else releaseStatus(release, installedCode)
    current.busy -> null
    quietAttempt && installedCode != null && installedCode != assessedCode -> PluginCatalogStatus.Installed
    // Removed only once a version it was recorded with has gone; a first install Android has not reported yet is still Installed.
    current.status == PluginCatalogStatus.Installed -> when {
        installedCode == null && assessedCode != null -> releaseStatus(release, null)
        // Android has now reported the package: record its version, still Installed.
        installedCode != null && assessedCode == null -> PluginCatalogStatus.Installed
        else -> null
    }
    current.status == PluginCatalogStatus.NeedsPermission && installsAllowed -> releaseStatus(release, installedCode)
    release != null && installedCode != assessedCode -> releaseStatus(release, installedCode)
    else -> null
}

internal data class PluginArchive(val packageName: String, val versionCode: Long, val signers: Set<String>)
internal enum class PluginVerification { Verified, WrongPackage, NotNewer, WrongSigner }

/** The registry's pinned signers decide trust, exactly: one extra or missing signer refuses the APK. */
internal fun verifyPluginArchive(archive: PluginArchive, installedCode: Long?, source: PluginSource): PluginVerification = when {
    archive.packageName != source.packageName -> PluginVerification.WrongPackage
    archive.versionCode <= 0 || (installedCode != null && archive.versionCode <= installedCode) -> PluginVerification.NotNewer
    source.signers.isEmpty() || archive.signers.isEmpty() || archive.signers != source.signers -> PluginVerification.WrongSigner
    else -> PluginVerification.Verified
}

/**
 * One [PluginSource]’s releases: checks, verified staging, and installs. One job runs at a time.
 * Catalog downloads are public HTTPS. APK contents, rather than a release tag, decide update and trust.
 */
internal class ApkPluginCatalog(
    private val context: Context,
    private val scope: CoroutineScope,
    val source: PluginSource,
    private val includePrereleases: () -> Boolean = { false },
    private val onRelease: suspend (PluginRelease) -> Unit = {},
    private val onStaged: () -> Unit = {},
    private val automaticAllowed: () -> Boolean = { false },
    private val publish: (PluginCatalogSetting) -> Unit,
) {
    @Volatile private var work: Job? = null
    /**
     * Self-update waits for plugin downloads, staging, automatic commits and their Android results. A manual
     * confirmation the player left open does not hold it: that can wait for weeks, and the installed version
     * settles it after a restart anyway.
     */
    val installInFlight: Boolean get() = work?.isActive == true || (session != null && automaticSession)
    private var release: PluginRelease? = null
    private var stagedRelease: PluginRelease? = null
    private var state = PluginCatalogSetting()
    /** When the last check read GitHub's answer, in [SystemClock.elapsedRealtime] time. */
    @Volatile private var checkedAt: Long? = null
    /** The installed versionCode [state] was worked out against. */
    private var assessedCode: Long? = null
    /** The Android install session waiting on the player's confirmation, until it reports or goes. */
    @Volatile private var session: Int? = null

    /** The last attempt's session went without a report; see [reassessedStatus]'s quietAttempt. */
    private var quietAttempt = false
    private val stagedFile = File(context.noBackupFilesDir, "updates/${source.packageName}.apk")
    private val stagedInfo = File(context.noBackupFilesDir, "updates/${source.packageName}.json")
    var stagedCode: Long? = null
        private set
    var stagedName: String? = null
        private set
    var stagedAt: Long = 0
        private set
    var needsConfirmation: Boolean = false
        private set
    private var automaticSession = false
    private var attemptedAutomatically = false
    private val awake by lazy { DownloadAwake(context) }

    init {
        try {
            val saved = JSONObject(stagedInfo.readText())
            if (stagedFile.isFile) {
                release = parsePluginRelease(saved.getString("release"), source,
                    if (saved.optBoolean("prerelease")) ReleaseChannel.Prerelease else ReleaseChannel.Stable)
                stagedCode = saved.getLong("code")
                stagedName = saved.getString("name")
                release = release?.copy(versionCode = stagedCode, versionName = stagedName)
                stagedRelease = release
                stagedAt = saved.optLong("atMillis", System.currentTimeMillis())
                needsConfirmation = saved.optBoolean("confirmation")
                automaticSession = saved.optBoolean("automatic")
                session = restoredAutomaticSession(saved.optInt("session", -1), automaticSession)
                assessedCode = if (saved.has("assessedCode") && !saved.isNull("assessedCode")) saved.getLong("assessedCode") else null
                // Forget manual confirmations from older metadata; they are reassessed by installed version.
                if (!automaticSession && saved.optInt("session", -1) >= 0) saveStaged()
                abandonUncommittedSession()
                if (session != null) state = PluginCatalogSetting(PluginCatalogStatus.Installing, installed = assessedCode != null)
                else state = PluginCatalogSetting(releaseStatus(release, assessedCode), installed = assessedCode != null)
            }
        } catch (_: Exception) { clearStaged() }
    }

    private fun saveStaged() {
        val latest = stagedRelease ?: return
        val root = JSONObject().put("tag_name", latest.tag).put("body", latest.body)
            .put("prerelease", latest.tag == "pre-release").put("assets", org.json.JSONArray().put(JSONObject()
                .put("name", source.asset).put("size", latest.bytes).put("browser_download_url", latest.url)))
        val saved = JSONObject().put("release", root.toString()).put("prerelease", latest.tag == "pre-release")
            .put("code", stagedCode).put("name", stagedName).put("confirmation", needsConfirmation)
            .put("atMillis", stagedAt).put("session", session.takeIf { automaticSession } ?: -1)
            .put("automatic", automaticSession).put("assessedCode", assessedCode)
        stagedInfo.parentFile?.mkdirs()
        try { writeDurably(stagedInfo.toPath(), saved.toString().toByteArray()) }
        catch (failure: IOException) { Log.w("FoldcadeUpdates", "Staged metadata not saved: ${source.packageName}", failure) }
    }

    /** A process can die after copying a session but before commit. Keep the APK and retry later. */
    private fun abandonUncommittedSession(): Boolean {
        if (!automaticSession) return false
        val id = session ?: return false
        val installer = context.packageManager.packageInstaller
        val info = installer.getSessionInfo(id)
        if (!shouldAbandonInstallSession(info?.isCommitted)) return false
        try { installer.abandonSession(id) } catch (failure: RuntimeException) {
            Log.w("FoldcadeUpdates", "Could not abandon stale install session", failure)
        }
        session = null
        automaticSession = false
        attemptedAutomatically = false
        saveStaged()
        return true
    }

    private fun clearStaged() {
        stagedCode = null
        stagedRelease = null
        stagedAt = 0
        stagedName = null
        needsConfirmation = false
        stagedFile.delete()
        stagedInfo.delete()
    }

    /** The scheduler owns retries. A gate change never retries the same failed install. */
    suspend fun automaticCheck(automatic: Boolean): Boolean {
        withContext(Dispatchers.Main.immediate) {
            attemptedAutomatically = false
            checkRelease()
        }
        work?.join()
        if (checkedAt == null) return false
        if (automatic && installedCode() != null && state.status in setOf(PluginCatalogStatus.Ready, PluginCatalogStatus.UpdateAvailable)) {
            withContext(Dispatchers.Main.immediate) { install(automatic = true, stageOnly = true) }
            work?.join()
        }
        return true
    }

    /** A channel change during a download takes effect as soon as that work has finished. */
    fun channelChanged() {
        scope.launch {
            work?.join()
            withContext(Dispatchers.Main.immediate) { checkRelease() }
            work?.join()
            withContext(Dispatchers.Main.immediate) { onStaged() }
        }
    }

    /** Main thread, only after the coordinator has opened the quiet-moment gate. */
    fun installAutomatically() {
        if (stagedCode == null || needsConfirmation || attemptedAutomatically || state.busy || work?.isActive == true) return
        if (stagedRelease?.tag == "pre-release" && !includePrereleases()) return
        attemptedAutomatically = true
        // A check since staging (About opening, a channel change) may have found a newer release: take that one.
        // The gate is read again just before commit, so the download it needs first cannot catch a game.
        val latest = release
        install(automatic = true, useStaged = latest == null || mayReuseStagedRelease(stagedRelease, latest))
    }

    /** Automatic confirmation is reported by a receiver, so no activity can cover a game. */
    fun pendingConfirmation(sessionId: Int) {
        if (session != sessionId || !automaticSession) return
        session = null
        try { context.packageManager.packageInstaller.abandonSession(sessionId) } catch (_: RuntimeException) { }
        needsConfirmation = true
        saveStaged()
        showFor(PluginCatalogStatus.UpdateAvailable, installedCode())
        onStaged()
    }
    private fun installedCode(): Long? = try {
        context.packageManager.getPackageInfo(source.packageName, 0).longVersionCode
    } catch (_: PackageManager.NameNotFoundException) { null }

    private suspend fun show(status: PluginCatalogStatus, progress: Int? = null, detail: String? = null) = withContext(Dispatchers.Main.immediate) {
        showFor(status, installedCode(), progress, detail)
    }

    /** A status chosen from the installed version [code], recorded with that same reading. */
    private suspend fun showChosen(status: PluginCatalogStatus, code: Long?) = withContext(Dispatchers.Main.immediate) {
        showFor(status, code)
    }

    /** Main thread only. [code] is the installed version [status] was worked out against. */
    private fun showFor(status: PluginCatalogStatus, code: Long?, progress: Int? = null, detail: String? = null) {
        assessedCode = code
        state = PluginCatalogSetting(status, code != null, progress, detail, release?.name)
        publish(state)
    }

    /** [onlyIfStale] keeps a recent answer, so reopening a page does not ask GitHub again. */
    fun checkRelease(onlyIfStale: Boolean = false) {
        if (work?.isActive == true) return
        if (session != null && state.status == PluginCatalogStatus.Installing) { reassess(); if (session != null) return }
        val last = checkedAt
        if (onlyIfStale && last != null && SystemClock.elapsedRealtime() - last < PLUGIN_CHECK_FRESH_MS) { reassess(); return }
        quietAttempt = false
        work = scope.launch {
            show(PluginCatalogStatus.Checking)
            try {
                release = fetchRelease()
                if (stagedRelease?.tag == "pre-release" && !includePrereleases() && session == null) clearStaged()
                checkedAt = SystemClock.elapsedRealtime()
                val code = installedCode()
                showChosen(releaseStatus(release, code), code)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                release = null
                checkedAt = null
                show(PluginCatalogStatus.Unavailable)
            }
        }
    }

    /**
     * Called on the main thread on return to Home and after discovery. See [reassessedStatus].
     * It reads the installed version once and records that same read, so two returns close
     * together cannot leave a status worked out from an older version.
     */
    @MainThread
    fun reassess() {
        if (work?.isActive == true) return
        val code = installedCode()
        if (abandonUncommittedSession()) showFor(releaseStatus(release, code), code)
        if (stagedCode?.let { code != null && code >= it } == true || code == null) {
            clearStaged()
            onStaged()
        }
        val pending = session
        val gone = state.status == PluginCatalogStatus.Installing && pending != null &&
            context.packageManager.packageInstaller.getSessionInfo(pending) == null
        reassessedStatus(state, assessedCode, code, context.packageManager.canRequestPackageInstalls(), release, gone, quietAttempt)?.let {
            if (gone) {
                session = null
                if (stagedCode != null) saveStaged()
            }
            // Settled as declined: a version change still to come is this install's. Anything else ends the attempt.
            quietAttempt = gone && it != PluginCatalogStatus.Installed
            showFor(it, code)
        }
    }

    fun install(automatic: Boolean = false, stageOnly: Boolean = false, useStaged: Boolean = false) {
        if (work?.isActive == true || (session != null && state.status == PluginCatalogStatus.Installing)) return
        quietAttempt = false
        // A new attempt: only the session it commits is waiting on the player.
        session = null
        work = scope.launch {
            val staged = stagedFile
            var failureMessage = ApkPluginCopy.downloadFailed
            try {
                withContext(Dispatchers.Main.immediate) { awake.hold(true) }
                show(PluginCatalogStatus.Checking)
                val latest = (if (useStaged) stagedRelease else release) ?: fetchRelease().also { release = it }
                if (latest == null) { show(PluginCatalogStatus.NotPublished); return@launch }
                if (!automatic && !context.packageManager.canRequestPackageInstalls()) {
                    show(PluginCatalogStatus.NeedsPermission)
                    return@launch
                }
                staged.parentFile?.mkdirs()
                if (stagedCode == null || !staged.isFile || !mayReuseStagedRelease(stagedRelease, latest)) {
                    clearStaged()
                    show(PluginCatalogStatus.Downloading)
                    // Each whole percent fills the row a little more; the release's own size stands in for a missing length.
                    var shown = -1
                    publicClient().use { http ->
                        http.download(latest.url, staged, MAX_PLUGIN_APK_BYTES) { read, total ->
                            val size = total ?: latest.bytes
                            val percent = if (size > 0) (read * 100 / size).toInt().coerceIn(0, 100) else return@download
                            if (percent != shown) {
                                shown = percent
                                scope.launch(Dispatchers.Main.immediate) {
                                    if (state.status == PluginCatalogStatus.Downloading) {
                                        state = state.copy(progress = percent)
                                        publish(state)
                                    }
                                }
                            }
                        }
                    }
                }
                if (staged.length() != latest.bytes) { clearStaged(); error("Incomplete release asset") }
                failureMessage = ApkPluginCopy.verificationFailed
                val pm = context.packageManager
                val info = pm.getPackageArchiveInfo(staged.path, PackageManager.GET_SIGNING_CERTIFICATES)
                val archive = info?.let { PluginArchive(it.packageName, it.longVersionCode, it.signerDigests()) }
                if (archive == null) { clearStaged(); show(PluginCatalogStatus.Failed, detail = ApkPluginCopy.verificationFailed); return@launch }
                val code = installedCode()
                when (verifyPluginArchive(archive, code, source)) {
                    PluginVerification.NotNewer -> { clearStaged(); showChosen(PluginCatalogStatus.Current, code); return@launch }
                    PluginVerification.Verified -> Unit
                    else -> { clearStaged(); show(PluginCatalogStatus.Failed, detail = ApkPluginCopy.verificationFailed); return@launch }
                }
                java.io.RandomAccessFile(staged, "rw").use { it.fd.sync() }
                val verifiedRelease = latest.copy(versionCode = archive.versionCode, versionName = info.versionName ?: latest.name.orEmpty())
                stagedRelease = verifiedRelease
                if (stagedCode != archive.versionCode) stagedAt = System.currentTimeMillis()
                stagedCode = archive.versionCode
                stagedName = info.versionName ?: latest.name.orEmpty()
                saveStaged()
                onRelease(verifiedRelease)
                if (stageOnly) {
                    showChosen(PluginCatalogStatus.UpdateAvailable, code)
                    return@launch
                }
                // Removed since it was staged: an automatic update installs nothing new, and the row offers it again.
                if (automatic && installedCode() == null) { clearStaged(); showChosen(releaseStatus(latest, null), null); return@launch }
                show(PluginCatalogStatus.Installing)
                failureMessage = ApkPluginCopy.installFailed
                stageInstall(staged, automatic)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: SecurityException) {
                if (automatic) attemptedAutomatically = true
                show(PluginCatalogStatus.NeedsPermission)
            } catch (_: Exception) {
                if (automatic) attemptedAutomatically = true
                show(PluginCatalogStatus.Failed, detail = failureMessage)
            } finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main.immediate) { awake.hold(false) }
                if (stagedCode == null) staged.delete()
                scope.launch(Dispatchers.Main.immediate) {
                    work?.join()
                    onStaged()
                }
            }
        }
    }

    /**
     * Main thread, from [ApkPluginInstallActivity]. [sessionId] is the attempt Android reports on:
     * only the session still waiting on the player counts, so a late report on an older attempt,
     * or on one already settled as quietly declined, changes nothing. The status is written here
     * at once, so no later attempt can start in between.
     */
    @MainThread
    fun completed(status: Int, sessionId: Int) {
        if (session != sessionId) return
        session = null
        quietAttempt = false
        val code = installedCode()
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> { clearStaged(); showFor(PluginCatalogStatus.Installed, code) }
            // Declined in Android's dialog: the player said no to the dialog, not to the release, so it is offered again.
            PackageInstaller.STATUS_FAILURE_ABORTED -> showFor(releaseStatus(release, code), code)
            else -> showFor(PluginCatalogStatus.Failed, code)
        }
        if (stagedCode != null) saveStaged()
        onStaged()
    }

    private suspend fun fetchRelease(): PluginRelease? = publicClient().use { http ->
        suspend fun fetch(url: String, channel: ReleaseChannel): PluginRelease? {
            val response = http.get(url, headers = mapOf("Accept" to "application/vnd.github+json"), maxBytes = 1024 * 1024)
            if (response.status == 404) return null
            check(response.status == 200) { "Release unavailable" }
            return parsePluginRelease(response.body.decodeToString(), source, channel)
        }
        val stable = fetch(latestReleaseApi(source), ReleaseChannel.Stable)
        if (source != PluginRegistry.foldcade || !includePrereleases()) return@use stable
        // The rolling pre-release failing to answer, or answering badly, must not cost the stable update already read.
        val prerelease = try { fetch(prereleaseApi(source), ReleaseChannel.Prerelease) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        chooseRelease(stable, prerelease)
    }

    private suspend fun stageInstall(file: File, automatic: Boolean) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(source.packageName)
            setSize(file.length())
            setRequireUserAction(if (automatic) PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED else PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite(source.asset, 0, file.length()).use { output ->
                    file.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                val callback = Intent(context, if (automatic) ApkPluginInstallReceiver::class.java else ApkPluginInstallActivity::class.java).apply {
                    action = context.packageName + ".PLUGIN_INSTALL_RESULT"
                    putExtra("session", id)
                    putExtra("automatic", automatic)
                    putExtra(EXTRA_PLUGIN_PACKAGE, source.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                val result = if (automatic) PendingIntent.getBroadcast(context, id, callback, flags) else PendingIntent.getActivity(context, id, callback, flags)
                this.session = id
                automaticSession = automatic
                saveStaged()
                val self = (context.applicationContext as? FoldcadeApp)?.takeIf { source == PluginRegistry.foldcade }
                if (self != null) {
                    self.componentUpdates.flush()
                    // Blocking preference IO finishes before the final Main-thread gate reading.
                    withContext(Dispatchers.IO) { self.store.flush() }
                }
                withContext(Dispatchers.Main.immediate) {
                    // Staging can take time. Read the gate again on the same main-thread turn as commit.
                    // Manual self updates also leave plugin work intact and remain available to retry.
                    if ((automatic && !automaticAllowed()) || self?.componentUpdates?.pluginWorkInFlight == true) {
                        this@ApkPluginCatalog.session = null
                        attemptedAutomatically = false
                        installer.abandonSession(id)
                        saveStaged()
                        showFor(PluginCatalogStatus.UpdateAvailable, installedCode())
                    } else {
                        session.commit(result.intentSender)
                    }
                }
            }
        } catch (failure: Exception) {
            // Abandoned: its late report must not count as the attempt still waiting.
            session = null
            automaticSession = false
            saveStaged()
            try { installer.abandonSession(id) } catch (_: RuntimeException) { }
            throw failure
        }
    }

    private fun publicClient(): PublicHttps = PublicHttps(setOf(
        "api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com",
    ))
}

internal const val EXTRA_PLUGIN_PACKAGE = "plugin_package"

/** PackageInstaller alone can fill this explicit PendingIntent; the activity is not exported. */
class ApkPluginInstallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val catalog = (application as FoldcadeApp).apkPluginCatalogs[intent.getStringExtra(EXTRA_PLUGIN_PACKAGE)]
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val sessionId = intent.getIntExtra("session", -1)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            if (confirmation != null) {
                try { startActivity(confirmation) } catch (_: RuntimeException) {
                    catalog?.completed(PackageInstaller.STATUS_FAILURE, sessionId)
                }
            } else catalog?.completed(PackageInstaller.STATUS_FAILURE, sessionId)
        } else {
            catalog?.completed(status, sessionId)
        }
        finish()
    }
}

/** Silent attempts report here even with Home covered. Android's confirmation intent is never opened. */
class ApkPluginInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val catalog = (context.applicationContext as FoldcadeApp).apkPluginCatalogs[intent.getStringExtra(EXTRA_PLUGIN_PACKAGE)] ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val id = intent.getIntExtra("session", -1)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) catalog.pendingConfirmation(id) else catalog.completed(status, id)
    }
}
