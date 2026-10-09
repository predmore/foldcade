package app.foldcade

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
)

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
    }
}

internal fun Context.openAndroidSetting(setting: AndroidSetting) {
    for (target in androidSettingTargets(setting)) {
        val intent = Intent(target.action)
        if (target.packageData) {
            intent.data = Uri.fromParts("package", packageName, null)
        }
        try {
            if (this !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            continue
        }
    }
}
