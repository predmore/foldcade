package app.foldcade

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.security.MessageDigest

internal object ApkPluginProtocol {
    const val VERSION = 1
    const val PLAY = "app.foldcade.plugin.action.PLAY"
    const val META = "app.foldcade.plugin."
    const val EXTRA = "app.foldcade.plugin.extra."
    const val GAMESTREAM_EXTRA = "app.foldcade.gamestream.extra."
    const val GAMESTREAM = "gamestream"
}

internal data class ApkPlugin(
    val packageName: String,
    val activity: String,
    val label: String,
    val version: String,
    val signers: Set<String>,
    val protocol: Int,
    val platform: String,
    val bothScreens: Boolean,
    val scopes: Set<String>,
) {
    val supported: Boolean get() = platform == "moonlight"
    val fingerprint: String get() = signers.sorted().joinToString(" / ") { it.take(8).chunked(2).joinToString(":") }
    val component: ComponentName get() = ComponentName(packageName, activity)
}

/** Current APK signers only. A rotation history does not grant the new signing identity access. */
internal fun PackageInfo.signerDigests(): Set<String> = signingInfo?.apkContentsSigners.orEmpty().map {
    MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
}.toSet()

internal fun discoverApkPlugins(pm: PackageManager): List<ApkPlugin> =
    pm.queryIntentActivities(Intent(ApkPluginProtocol.PLAY), PackageManager.GET_META_DATA).mapNotNull { resolved ->
        val activity = resolved.activityInfo ?: return@mapNotNull null
        if (!activity.exported || !activity.enabled || !activity.applicationInfo.enabled) return@mapNotNull null
        val meta = activity.metaData ?: return@mapNotNull null
        val protocol = meta.get(ApkPluginProtocol.META + "PROTOCOL")?.toString()?.toIntOrNull()?.takeIf { it >= 1 }
            ?: return@mapNotNull null
        val info = try {
            pm.getPackageInfo(activity.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (_: PackageManager.NameNotFoundException) {
            return@mapNotNull null
        }
        val signers = info.signerDigests().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        ApkPlugin(
            activity.packageName, activity.name, resolved.loadLabel(pm).toString(),
            app.foldcade.language.AboutCopy.version(info.versionName.orEmpty(), info.longVersionCode), signers, minOf(protocol, ApkPluginProtocol.VERSION),
            meta.getString(ApkPluginProtocol.META + "PLATFORM").orEmpty(),
            meta.getString(ApkPluginProtocol.META + "SCREENS") == "both",
            meta.getString(ApkPluginProtocol.META + "ACCESS").orEmpty().split(',').map(String::trim)
                .filter { it == ApkPluginProtocol.GAMESTREAM }.toSet(),
        )
    }.sortedWith(compareBy({ it.packageName }, { it.activity })).distinctBy { it.packageName }

/** Host-owned device consent. This separate preferences file is excluded from every transfer. */
internal class ApkPluginGrants(private val prefs: SharedPreferences) {
    fun granted(plugin: ApkPlugin, scope: String): Boolean = plugin.signers.isNotEmpty() && scope in plugin.scopes &&
        prefs.getStringSet("grants", emptySet()).orEmpty().contains(key(plugin, scope))

    fun routes(plugin: ApkPlugin): Boolean = granted(plugin, ApkPluginProtocol.GAMESTREAM) &&
        !prefs.getStringSet("disabled", emptySet()).orEmpty().contains(key(plugin, ApkPluginProtocol.GAMESTREAM))

    @Synchronized
    fun setGranted(plugin: ApkPlugin, scope: String, on: Boolean) {
        if (scope !in plugin.scopes || plugin.signers.isEmpty()) return
        val key = key(plugin, scope)
        val grants = prefs.getStringSet("grants", emptySet()).orEmpty().toMutableSet()
        if (on) grants.add(key) else grants.remove(key)
        val disabled = prefs.getStringSet("disabled", emptySet()).orEmpty() - key
        check(prefs.edit().putStringSet("grants", grants).putStringSet("disabled", disabled).commit()) { "Could not save plugin consent" }
    }

    @Synchronized
    fun setRouting(plugin: ApkPlugin, on: Boolean) {
        val key = key(plugin, ApkPluginProtocol.GAMESTREAM)
        val disabled = prefs.getStringSet("disabled", emptySet()).orEmpty().toMutableSet()
        if (on) disabled.remove(key) else disabled.add(key)
        check(prefs.edit().putStringSet("disabled", disabled).commit()) { "Could not save plugin choice" }
    }

    @Synchronized
    fun forget(packageName: String) {
        val edit = prefs.edit()
        for (name in listOf("grants", "disabled")) {
            edit.putStringSet(name, prefs.getStringSet(name, emptySet()).orEmpty().filterNot { it.substringBefore('|') == packageName }.toSet())
        }
        check(edit.commit()) { "Could not remove plugin consent" }
    }

    private fun key(plugin: ApkPlugin, scope: String): String = "${plugin.packageName}|${plugin.signers.sorted().joinToString(",")}|$scope"

    companion object {
        private var instance: ApkPluginGrants? = null

        /** Provider, Settings and package removal share the same mutation lock. */
        @Synchronized
        fun open(context: Context): ApkPluginGrants = instance ?: ApkPluginGrants(
            context.getSharedPreferences("apk_plugins", Context.MODE_PRIVATE),
        ).also { instance = it }
    }
}

/** Also runs while Home is stopped. Replacement broadcasts must keep consent for the same signer. */
class ApkPluginRemovedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_PACKAGE_FULLY_REMOVED && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            intent.data?.schemeSpecificPart?.let { ApkPluginGrants.open(context).forget(it) }
        }
    }
}
