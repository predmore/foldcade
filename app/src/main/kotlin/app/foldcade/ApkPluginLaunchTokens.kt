package app.foldcade

import android.content.Intent
import app.foldcade.gamestream.isAppId
import java.security.SecureRandom
import java.util.Base64

/** In-memory evidence of a play start Foldcade chose, never a stored scope grant. */
internal class ApkPluginLaunchTokens(private val nowMillis: () -> Long = android.os.SystemClock::elapsedRealtime) {
    private data class Start(val packageName: String, val hostUuid: String, val appId: String, val at: Long)
    private val starts = mutableMapOf<String, Start>()
    private val random = SecureRandom()

    @Synchronized
    fun issue(packageName: String, hostUuid: String, appId: String): String {
        require(packageName.isNotBlank() && hostUuid.isNotBlank() && isAppId(appId)) { "Invalid plugin play start" }
        val now = nowMillis()
        prune(now)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        starts[token] = Start(packageName, hostUuid, appId, now)
        return token
    }

    @Synchronized
    fun allows(packageName: String, hostUuid: String, token: String?, appIds: List<String>): Boolean {
        prune(nowMillis())
        val start = starts[token] ?: return false
        // Moonlight always names the app; a start with no app id cannot be tied to the one Foldcade chose.
        return start.packageName == packageName && start.hostUuid.equals(hostUuid, ignoreCase = true) &&
            appIds.isNotEmpty() && appIds.all { it == start.appId }
    }

    /** Only the same caller and computer can extend a live token; expired tokens stay expired. */
    @Synchronized
    fun touch(packageName: String, hostUuid: String, token: String?): Boolean {
        val now = nowMillis()
        prune(now)
        val key = token ?: return false
        val start = starts[key] ?: return false
        if (start.packageName != packageName || !start.hostUuid.equals(hostUuid, ignoreCase = true)) return false
        starts[key] = start.copy(at = now)
        return true
    }

    private fun prune(now: Long) {
        starts.entries.removeAll { now - it.value.at !in 0 until WINDOW_MS }
    }

    companion object {
        const val WINDOW_MS = 10 * 60 * 1000L
    }
}

/** Called immediately before sending fresh starts, task resumes and replacement starts. */
internal fun FoldcadeApp.authorizeApkPluginStart(intent: Intent) {
    if (intent.action != ApkPluginProtocol.PLAY) return
    val packageName = requireNotNull(intent.component).packageName
    val hostUuid = requireNotNull(intent.getStringExtra(ApkPluginProtocol.GAMESTREAM_EXTRA + "HOST_UUID"))
    val appId = requireNotNull(intent.getStringExtra(ApkPluginProtocol.GAMESTREAM_EXTRA + "APP_ID"))
    intent.putExtra(ApkPluginProtocol.EXTRA + "LAUNCH_TOKEN", apkPluginLaunchTokens.issue(packageName, hostUuid, appId))
}
