package app.foldcade

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.MainThread
import app.foldcade.language.ApkPluginCopy
import app.foldcade.language.PluginCatalogSetting
import app.foldcade.language.PluginCatalogStatus
import app.foldcade.language.PluginSource
import app.foldcade.net.PublicHttps
import java.io.File
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
internal data class PluginRelease(val tag: String, val url: String, val bytes: Long, val versionCode: Long? = null) {
    val name: String? get() = tag.removePrefix("v").ifBlank { null }
}

private val VERSION_CODE_LINE = Regex("""versionCode:[ \t]*([1-9][0-9]{0,17})""")

/** A `versionCode: N` line in the release notes, as Foldcade's and its plugins' release scripts write it. */
internal fun releaseVersionCode(body: String): Long? =
    body.lineSequence().firstNotNullOfOrNull { VERSION_CODE_LINE.matchEntire(it.trim())?.groupValues?.get(1)?.toLongOrNull() }

/** A missing publication or asset is an ordinary empty catalog. Other malformed responses fail. */
internal fun parsePluginRelease(json: String, source: PluginSource): PluginRelease? {
    val root = JSONObject(json)
    if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
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
    return PluginRelease(root.optString("tag_name"), url, bytes, releaseVersionCode(body))
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
    current.status == PluginCatalogStatus.Installed -> if (installedCode == null) releaseStatus(release, null) else null
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
 * One [PluginSource]'s releases: checks, downloads, and user-confirmed installs. One job runs at a time.
 * Catalog downloads are public HTTPS. APK contents, rather than a release tag, decide update and trust.
 */
internal class ApkPluginCatalog(
    private val context: Context,
    private val scope: CoroutineScope,
    val source: PluginSource,
    private val publish: (PluginCatalogSetting) -> Unit,
) {
    private var work: Job? = null
    private var release: PluginRelease? = null
    private var state = PluginCatalogSetting()
    /** When the last check read GitHub's answer, in [SystemClock.elapsedRealtime] time. */
    @Volatile private var checkedAt: Long? = null
    /** The installed versionCode [state] was worked out against. */
    private var assessedCode: Long? = null
    /** The Android install session waiting on the player's confirmation, until it reports or goes. */
    @Volatile private var session: Int? = null

    /** The last attempt's session went without a report; see [reassessedStatus]'s quietAttempt. */
    private var quietAttempt = false
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
        val last = checkedAt
        if (onlyIfStale && last != null && SystemClock.elapsedRealtime() - last < PLUGIN_CHECK_FRESH_MS) { reassess(); return }
        quietAttempt = false
        work = scope.launch {
            show(PluginCatalogStatus.Checking)
            try {
                release = fetchRelease()
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
        val pending = session
        val gone = state.status == PluginCatalogStatus.Installing && pending != null &&
            context.packageManager.packageInstaller.getSessionInfo(pending) == null
        reassessedStatus(state, assessedCode, code, context.packageManager.canRequestPackageInstalls(), release, gone, quietAttempt)?.let {
            if (gone) session = null
            // Settled as declined: a version change still to come is this install's. Anything else ends the attempt.
            quietAttempt = gone && it != PluginCatalogStatus.Installed
            showFor(it, code)
        }
    }

    fun install() {
        if (work?.isActive == true) return
        quietAttempt = false
        // A new attempt: only the session it commits is waiting on the player.
        session = null
        work = scope.launch {
            val staged = File(context.cacheDir, "apk-plugins/${source.packageName}.apk")
            var failureMessage = ApkPluginCopy.downloadFailed
            try {
                show(PluginCatalogStatus.Checking)
                val latest = release ?: fetchRelease().also { release = it }
                if (latest == null) { show(PluginCatalogStatus.NotPublished); return@launch }
                if (!context.packageManager.canRequestPackageInstalls()) {
                    show(PluginCatalogStatus.NeedsPermission)
                    return@launch
                }
                staged.parentFile?.mkdirs()
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
                require(staged.length() == latest.bytes) { "Incomplete release asset" }
                failureMessage = ApkPluginCopy.verificationFailed
                val pm = context.packageManager
                val info = pm.getPackageArchiveInfo(staged.path, PackageManager.GET_SIGNING_CERTIFICATES)
                val archive = info?.let { PluginArchive(it.packageName, it.longVersionCode, it.signerDigests()) }
                if (archive == null) { show(PluginCatalogStatus.Failed, detail = ApkPluginCopy.verificationFailed); return@launch }
                val code = installedCode()
                when (verifyPluginArchive(archive, code, source)) {
                    PluginVerification.NotNewer -> { showChosen(PluginCatalogStatus.Current, code); return@launch }
                    PluginVerification.Verified -> Unit
                    else -> { show(PluginCatalogStatus.Failed, detail = ApkPluginCopy.verificationFailed); return@launch }
                }
                show(PluginCatalogStatus.Installing)
                failureMessage = ApkPluginCopy.installFailed
                stageInstall(staged)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: SecurityException) {
                show(PluginCatalogStatus.NeedsPermission)
            } catch (_: Exception) {
                show(PluginCatalogStatus.Failed, detail = failureMessage)
            } finally {
                staged.delete()
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
            PackageInstaller.STATUS_SUCCESS -> showFor(PluginCatalogStatus.Installed, code)
            // Declined in Android's dialog: the player said no to the dialog, not to the release, so it is offered again.
            PackageInstaller.STATUS_FAILURE_ABORTED -> showFor(releaseStatus(release, code), code)
            else -> showFor(PluginCatalogStatus.Failed, code)
        }
    }

    private suspend fun fetchRelease(): PluginRelease? = publicClient().use { http ->
        val response = http.get(latestReleaseApi(source), headers = mapOf("Accept" to "application/vnd.github+json"), maxBytes = 1024 * 1024)
        if (response.status == 404) return null
        check(response.status == 200) { "Release unavailable" }
        parsePluginRelease(response.body.decodeToString(), source)
    }

    private fun stageInstall(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(source.packageName)
            setSize(file.length())
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite(source.asset, 0, file.length()).use { output ->
                    file.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                val callback = Intent(context, ApkPluginInstallActivity::class.java).apply {
                    action = context.packageName + ".PLUGIN_INSTALL_RESULT"
                    putExtra("session", id)
                    putExtra(EXTRA_PLUGIN_PACKAGE, source.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val result = PendingIntent.getActivity(context, id, callback, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                this.session = id
                session.commit(result.intentSender)
            }
        } catch (failure: Exception) {
            // Abandoned: its late report must not count as the attempt still waiting.
            session = null
            installer.abandonSession(id)
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
