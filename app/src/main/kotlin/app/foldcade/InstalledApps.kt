package app.foldcade

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import app.foldcade.language.LaunchableApp

internal data class ScannedLaunchable(
    val packageName: String,
    val label: String,
    val category: Int,
    val flags: Int,
)

/**
 * CATEGORY_GAME is the current flag. FLAG_IS_GAME is the older one.
 * Unset category is CATEGORY_UNDEFINED, not CATEGORY_GAME.
 */
@Suppress("DEPRECATION")
internal fun isSystemGame(category: Int, flags: Int): Boolean =
    category == ApplicationInfo.CATEGORY_GAME || (flags and ApplicationInfo.FLAG_IS_GAME) != 0

internal fun launchablesFrom(scanned: List<ScannedLaunchable>, ownPackage: String): List<LaunchableApp> {
    return scanned.mapNotNull { row ->
        if (row.packageName.isBlank() || row.packageName == ownPackage) return@mapNotNull null
        LaunchableApp(
            packageName = row.packageName,
            label = row.label.ifBlank { row.packageName },
            systemGame = isSystemGame(row.category, row.flags),
        )
    }.distinctBy { it.packageName }
}

/**
 * Launchable apps from MAIN / LAUNCHER.
 * The manifest queries intent is the same filter. The flag passed to the query is 0.
 */
internal class InstalledAppCatalog(
    private val packages: PackageManager,
    private val ownPackage: String,
) {
    fun list(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = packages.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        return launchablesFrom(resolved.map { it.toScanned(packages) }, ownPackage)
    }
}

private fun ResolveInfo.toScanned(packages: PackageManager): ScannedLaunchable {
    val activity = activityInfo
    val appInfo = activity?.applicationInfo
    return ScannedLaunchable(
        packageName = activity?.packageName.orEmpty(),
        label = loadLabel(packages)?.toString().orEmpty(),
        category = appInfo?.category ?: ApplicationInfo.CATEGORY_UNDEFINED,
        flags = appInfo?.flags ?: 0,
    )
}
