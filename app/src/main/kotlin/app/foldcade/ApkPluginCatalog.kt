package app.foldcade

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Bundle
import app.foldcade.language.ApkPluginCopy
import app.foldcade.language.PluginCatalogSetting
import app.foldcade.language.PluginCatalogStatus
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

internal const val MOONLIGHT_RELEASE_API = "https://api.github.com/repos/predmore/foldcade-moonlight/releases/latest"
internal const val MOONLIGHT_ASSET = "foldcade-moonlight.apk"
internal const val MAX_PLUGIN_APK_BYTES = 128L * 1024 * 1024

internal data class PluginRelease(val tag: String, val url: String, val bytes: Long)

/** A missing publication or asset is an ordinary empty catalog. Other malformed responses fail. */
internal fun parsePluginRelease(json: String): PluginRelease? {
    val root = JSONObject(json)
    if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
    val assets = root.getJSONArray("assets")
    val matches = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }.filter { it.optString("name") == MOONLIGHT_ASSET }
    if (matches.isEmpty()) return null
    require(matches.size == 1) { "Duplicate release asset" }
    val asset = matches.single()
    val bytes = asset.getLong("size")
    require(bytes in 1..MAX_PLUGIN_APK_BYTES) { "Invalid asset size" }
    val url = asset.getString("browser_download_url")
    val uri = URI(url)
    require(uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 && uri.rawUserInfo == null &&
        uri.rawFragment == null && uri.rawQuery == null && uri.rawPath.startsWith("/predmore/foldcade-moonlight/releases/download/") &&
        uri.rawPath.endsWith("/$MOONLIGHT_ASSET") && uri.rawPath.split('/').none { it == "." || it == ".." }) { "Unexpected release URL" }
    return PluginRelease(root.optString("tag_name"), url, bytes)
}

internal data class PluginArchive(val packageName: String, val versionCode: Long, val signers: Set<String>)
internal enum class PluginVerification { Verified, WrongPackage, NotNewer, WrongSigner }

internal fun verifyPluginArchive(archive: PluginArchive, installedCode: Long?, trustedSigners: Set<String>): PluginVerification = when {
    archive.packageName != ApkPluginCopy.catalogPackage -> PluginVerification.WrongPackage
    archive.versionCode <= 0 || (installedCode != null && archive.versionCode <= installedCode) -> PluginVerification.NotNewer
    trustedSigners.isEmpty() || archive.signers.isEmpty() || archive.signers != trustedSigners -> PluginVerification.WrongSigner
    else -> PluginVerification.Verified
}

/** Catalog downloads are public HTTPS. APK contents, rather than a release tag, decide update and trust. */
internal class ApkPluginCatalog(
    private val context: Context,
    private val scope: CoroutineScope,
    private val publish: (PluginCatalogSetting) -> Unit,
) {
    private var work: Job? = null
    private var release: PluginRelease? = null
    private var state = PluginCatalogSetting()
    private fun installedCode(): Long? = try {
        context.packageManager.getPackageInfo(ApkPluginCopy.catalogPackage, 0).longVersionCode
    } catch (_: PackageManager.NameNotFoundException) { null }

    private suspend fun show(status: PluginCatalogStatus, progress: Int? = null, detail: String? = null) = withContext(Dispatchers.Main.immediate) {
        state = PluginCatalogSetting(status, installedCode() != null, progress, detail)
        publish(state)
    }

    fun checkRelease() {
        if (work?.isActive == true) return
        work = scope.launch {
            show(PluginCatalogStatus.Checking)
            try {
                release = fetchRelease()
                show(if (release == null) PluginCatalogStatus.NotPublished else PluginCatalogStatus.Ready)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                release = null
                show(PluginCatalogStatus.Unavailable)
            }
        }
    }

    fun install() {
        if (work?.isActive == true) return
        work = scope.launch {
            val staged = File(context.cacheDir, "apk-plugins/$MOONLIGHT_ASSET")
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
                publicClient().use { http ->
                    http.download(latest.url, staged, MAX_PLUGIN_APK_BYTES) { _, _ -> }
                }
                require(staged.length() == latest.bytes) { "Incomplete release asset" }
                failureMessage = ApkPluginCopy.verificationFailed
                val pm = context.packageManager
                val info = pm.getPackageArchiveInfo(staged.path, PackageManager.GET_SIGNING_CERTIFICATES)
                val own = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signerDigests()
                val archive = info?.let { PluginArchive(it.packageName, it.longVersionCode, it.signerDigests()) }
                if (archive == null) { show(PluginCatalogStatus.Failed, detail = ApkPluginCopy.verificationFailed); return@launch }
                when (verifyPluginArchive(archive, installedCode(), own)) {
                    PluginVerification.NotNewer -> { show(PluginCatalogStatus.Current); return@launch }
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

    fun completed(status: Int) {
        scope.launch {
            show(when (status) {
                PackageInstaller.STATUS_SUCCESS -> PluginCatalogStatus.Installed
                else -> PluginCatalogStatus.Failed
            }, detail = if (status == PackageInstaller.STATUS_FAILURE_ABORTED) ApkPluginCopy.installDeclined else null)
        }
    }

    private suspend fun fetchRelease(): PluginRelease? = publicClient().use { http ->
        val response = http.get(MOONLIGHT_RELEASE_API, headers = mapOf("Accept" to "application/vnd.github+json"), maxBytes = 1024 * 1024)
        if (response.status == 404) return null
        check(response.status == 200) { "Release unavailable" }
        parsePluginRelease(response.body.decodeToString())
    }

    private fun stageInstall(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ApkPluginCopy.catalogPackage)
            setSize(file.length())
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite(MOONLIGHT_ASSET, 0, file.length()).use { output ->
                    file.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                val callback = Intent(context, ApkPluginInstallActivity::class.java).apply {
                    action = context.packageName + ".PLUGIN_INSTALL_RESULT"
                    putExtra("session", id)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val result = PendingIntent.getActivity(context, id, callback, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                session.commit(result.intentSender)
            }
        } catch (failure: Exception) {
            installer.abandonSession(id)
            throw failure
        }
    }

    private fun publicClient(): PublicHttps = PublicHttps(setOf(
        "api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com",
    ))
}

/** PackageInstaller alone can fill this explicit PendingIntent; the activity is not exported. */
class ApkPluginInstallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            if (confirmation != null) {
                try { startActivity(confirmation) } catch (_: RuntimeException) {
                    (application as FoldcadeApp).apkPluginCatalog.completed(PackageInstaller.STATUS_FAILURE)
                }
            } else (application as FoldcadeApp).apkPluginCatalog.completed(PackageInstaller.STATUS_FAILURE)
        } else {
            (application as FoldcadeApp).apkPluginCatalog.completed(status)
        }
        finish()
    }
}
