package app.foldcade

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import app.foldcade.language.AndroidSetting

/** Battery usage screen. The compile SDK no longer exposes this historical Settings constant. */
private const val ACTION_POWER_USAGE_SUMMARY = "android.intent.action.POWER_USAGE_SUMMARY"

/**
 * One Android settings screen, in the order Foldcade tries them.
 * [packageData] is the `package:` URI for this app's details screen.
 */
internal data class SettingTarget(
    val action: String,
    val packageData: Boolean = false,
    /** An explicit screen, as AYN's RGB lighting, rather than whatever answers [action]. */
    val component: ComponentName? = null,
    /** An app's own launcher entry, as Magisk's, rather than a Settings screen. */
    val launches: String? = null,
)

/** Magisk, where the plugin is given root. */
internal const val MAGISK_PACKAGE = "com.topjohnwu.magisk"

/**
 * Where the sticks' colors are set on LineageOS for the Thor: AynParts' RGB lighting, or, where
 * that screen isn't open to other apps (it isn't in AynParts today), AYN's own settings, one tap
 * from it.
 */
internal val AYN_STICK_COLORS = listOf(
    ComponentName("org.lineageos.settings.ayn", "org.lineageos.settings.ayn.lights.RGBLightingSettingsActivity"),
    ComponentName("org.lineageos.settings.ayn", "org.lineageos.settings.ayn.AynSettingsActivity"),
)

private fun PackageManager.opens(component: ComponentName): Boolean = try {
    getActivityInfo(component, 0).let { it.exported && it.enabled && it.applicationInfo.enabled }
} catch (_: PackageManager.NameNotFoundException) {
    false
}

/** True when one of [AYN_STICK_COLORS] is installed, enabled and open to other apps. */
internal fun aynStickColorsOpen(pm: PackageManager): Boolean = AYN_STICK_COLORS.any(pm::opens)

/**
 * Panel intents first, where Android defines one. Everything else is a
 * settings screen, and the last entry is a broader screen that still exists
 * when a panel or a specific page does not resolve.
 */
internal fun androidSettingTargets(setting: AndroidSetting): List<SettingTarget> {
    val root = SettingTarget(Settings.ACTION_SETTINGS)
    return when (setting) {
        AndroidSetting.Settings -> listOf(root)
        AndroidSetting.Home -> listOf(
            SettingTarget(Settings.ACTION_HOME_SETTINGS),
            SettingTarget(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            root,
        )
        AndroidSetting.Wifi -> listOf(
            SettingTarget(Settings.Panel.ACTION_WIFI),
            SettingTarget(Settings.ACTION_WIFI_SETTINGS),
            SettingTarget(Settings.Panel.ACTION_INTERNET_CONNECTIVITY),
            SettingTarget(Settings.ACTION_WIRELESS_SETTINGS),
            root,
        )
        AndroidSetting.Bluetooth -> listOf(
            SettingTarget(Settings.ACTION_BLUETOOTH_SETTINGS),
            SettingTarget(Settings.ACTION_WIRELESS_SETTINGS),
            root,
        )
        AndroidSetting.Display -> listOf(
            SettingTarget(Settings.ACTION_DISPLAY_SETTINGS),
            root,
        )
        AndroidSetting.Sound -> listOf(
            SettingTarget(Settings.Panel.ACTION_VOLUME),
            SettingTarget(Settings.ACTION_SOUND_SETTINGS),
            root,
        )
        AndroidSetting.Battery -> listOf(
            SettingTarget(ACTION_POWER_USAGE_SUMMARY),
            SettingTarget(Settings.ACTION_BATTERY_SAVER_SETTINGS),
            root,
        )
        AndroidSetting.AppInfo -> listOf(
            SettingTarget(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageData = true),
            SettingTarget(Settings.ACTION_APPLICATION_SETTINGS),
            SettingTarget(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
            root,
        )
        // Only offered where it opens; there is no broader screen to fall back to.
        AndroidSetting.StickColor -> AYN_STICK_COLORS.map { SettingTarget(Intent.ACTION_MAIN, component = it) }
        AndroidSetting.RootManager -> listOf(SettingTarget(Intent.ACTION_MAIN, launches = MAGISK_PACKAGE))
    }
}

/** A rejected or throwing launch must not leave Home covered in the session. */
internal inline fun SessionStore.handoffAndroidSetting(panel: Panel?, launch: () -> Boolean): Boolean {
    if (panel != null) place(panel, ExternalApp(OTHER_APP_ID))
    var opened = false
    try {
        opened = launch()
        return opened
    } finally {
        if (!opened && panel != null) update { it.foldcadeResumed(panel) }
    }
}

/**
 * Opens [setting]. [packageName] is the app whose details screen [AndroidSetting.AppInfo]
 * asks for; every other setting ignores it. A screen this device does not have is skipped.
 */
internal fun Context.openAndroidSetting(setting: AndroidSetting, packageName: String = this.packageName): Boolean {
    for (target in androidSettingTargets(setting)) {
        val intent = target.launches?.let { packageManager.getLaunchIntentForPackage(it) ?: continue } ?: Intent(target.action)
        if (target.packageData) {
            intent.data = Uri.fromParts("package", packageName, null)
        }
        target.component?.let(intent::setComponent)
        try {
            if (this !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return true
        } catch (_: ActivityNotFoundException) {
            continue
        } catch (_: SecurityException) {
            continue
        }
    }
    return false
}
