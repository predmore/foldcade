package app.foldcade.language

/** Installed APK metadata and current device consent. No pairing material enters the UI model. */
data class ApkPluginSetting(
    val packageName: String, val activity: String, val label: String, val version: String, val fingerprint: String,
    val supported: Boolean, val scopes: Set<String>, val granted: Set<String>, val useForMoonlight: Boolean,
)

enum class PluginCatalogStatus { Checking, Ready, NotPublished, Unavailable, Downloading, Installing, Installed, Current, NeedsPermission, Failed }

data class PluginCatalogSetting(
    val status: PluginCatalogStatus = PluginCatalogStatus.Checking,
    val installed: Boolean = false,
    val progress: Int? = null,
    val detail: String? = null,
) {
    val canInstall: Boolean get() = !busy && status !in setOf(PluginCatalogStatus.NotPublished, PluginCatalogStatus.Unavailable, PluginCatalogStatus.Installed, PluginCatalogStatus.Current)
    val busy: Boolean get() = status in setOf(PluginCatalogStatus.Checking, PluginCatalogStatus.Downloading, PluginCatalogStatus.Installing)
}

object ApkPluginCopy {
    const val title = "Plugins"
    const val moonlight = "Foldcade Moonlight"
    const val catalogPackage = "app.foldcade.moonlight"
    const val pairedComputers = "Share paired computers"
    const val useForMoonlight = "Use for Moonlight games"
    const val signer = "Signer"
    const val version = "Version"
    const val packageName = "Package"
    const val checkAgain = "Check again"
    const val check = "Check for updates"
    const val install = "Install"
    const val update = "Update"
    const val allowInstalls = "Allow installs"
    const val unsupported = "Foldcade does not support this plugin’s platform yet."
    const val officialMoonlight = "Moonlight games open in Moonlight instead."
    const val catalogDetail = "Streams Moonlight games on both screens."
    const val unknownSources = "Allow Foldcade to install apps in Android, then return here to install."
    const val downloadFailed = "Download failed. Check your connection and try again."
    const val verificationFailed = "This APK could not be verified. It was not installed."
    const val installFailed = "Installation did not finish. Try again."
    const val installDeclined = "Installation canceled."

    fun consent(label: String): String = "$label can see your paired computers and stream the games you start in Foldcade. It can also quit games on them. Your pairing key stays in Foldcade."

    fun installValue(catalog: PluginCatalogSetting): String = when (catalog.status) {
        PluginCatalogStatus.NotPublished -> "Not available yet"
        PluginCatalogStatus.Unavailable -> "Unavailable"
        else -> status(catalog)
    }

    fun status(catalog: PluginCatalogSetting): String = catalog.detail ?: when (catalog.status) {
        PluginCatalogStatus.Checking -> "Checking release…"
        PluginCatalogStatus.Ready -> "Ready to install"
        PluginCatalogStatus.NotPublished -> "Not published yet"
        PluginCatalogStatus.Unavailable -> "Release unavailable. Try again later."
        PluginCatalogStatus.Downloading -> catalog.progress?.let { "Downloading · $it%" } ?: "Downloading…"
        PluginCatalogStatus.Installing -> "Waiting for Android…"
        PluginCatalogStatus.Installed -> "Installed"
        PluginCatalogStatus.Current -> "Already up to date"
        PluginCatalogStatus.NeedsPermission -> unknownSources
        PluginCatalogStatus.Failed -> installFailed
    }
}

/** Installation and discovery can report in either order. Focus consent once both have arrived. */
fun afterApkPluginInstall(previous: PickerModel, next: PickerModel): PickerModel {
    if (next.pluginCatalog.status != PluginCatalogStatus.Installed || next.apkPlugins.none { it.packageName == ApkPluginCopy.catalogPackage }) return next
    if (previous.pluginCatalog.status == PluginCatalogStatus.Installed && previous.apkPlugins.any { it.packageName == ApkPluginCopy.catalogPackage }) return next
    val page = next.settings ?: return next
    if (settingsCategories(next).getOrNull(page.category) != SettingsCategory.Plugins) return next
    val inner = SettingsInner.ApkPlugin(ApkPluginCopy.catalogPackage)
    if (page.inner != SettingsInner.PluginCatalog && page.inner != inner) return next
    return next.copy(settings = page.copy(inner = inner, row = settingsInnerFirstRow(inner, next), onRows = true))
}
