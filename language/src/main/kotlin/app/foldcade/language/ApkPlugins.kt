package app.foldcade.language

/** Installed APK metadata and current device consent. No pairing material enters the UI model. */
data class ApkPluginSetting(
    val packageName: String, val activity: String, val label: String, val version: String, val fingerprint: String,
    val supported: Boolean, val scopes: Set<String>, val granted: Set<String>, val useForMoonlight: Boolean,
)

/**
 * A plugin Foldcade can install and update from its GitHub releases. The registry names trusted
 * repositories, never versions: each plugin releases on its own schedule, so Foldcade asks
 * [repository] for its latest full release whenever it checks.
 *
 * [asset] is the one release file Foldcade downloads. Trust is [signers], the SHA-256 of each
 * certificate the APK must be signed with, exactly. It does not depend on how this copy of
 * Foldcade is signed, so a debug Foldcade installs the same release a signed one does.
 */
data class PluginSource(
    val packageName: String,
    val label: String,
    /** The line under the plugin's page title, for it and its own debug build. */
    val detail: String,
    /** The [ShelfArt] mark its catalog tile wears. */
    val mark: String,
    /** GitHub `owner/name`. */
    val repository: String,
    val asset: String,
    val signers: Set<String>,
)

object PluginRegistry {
    /** First-party plugins are signed with Foldcade's own release key. */
    private const val FOLDCADE_RELEASE_SIGNER = "36f6883086e694e0a8b245421543063e6b96450970bd4030198543a59e6581ff"

    val moonlight = PluginSource(
        packageName = "app.foldcade.moonlight",
        label = "Foldcade Moonlight",
        detail = "Streams Moonlight games on both screens.",
        mark = "moonlight",
        repository = "predmore/foldcade-moonlight",
        asset = "foldcade-moonlight.apk",
        signers = setOf(FOLDCADE_RELEASE_SIGNER),
    )

    val sources: List<PluginSource> = listOf(moonlight).also { requireValidRegistry(it) }

    fun source(packageName: String): PluginSource? = sources.firstOrNull { it.packageName == packageName }
}

private val REPOSITORY = Regex("""[A-Za-z0-9-]+/[A-Za-z0-9._-]+""")
private val ASSET = Regex("""[A-Za-z0-9][A-Za-z0-9._-]*\.apk""")
private val SIGNER = Regex("""[0-9a-f]{64}""")
private val PACKAGE = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+""")

/** A registry entry becomes a GitHub API path and a download check, so each field must be plain. */
fun requireValidRegistry(sources: List<PluginSource>) {
    require(sources.map { it.packageName }.toSet().size == sources.size) { "Duplicate plugin package" }
    sources.forEach { source ->
        require(PACKAGE.matches(source.packageName)) { "Invalid plugin package ${source.packageName}" }
        require(REPOSITORY.matches(source.repository) && source.repository.split('/').none { it == "." || it == ".." }) {
            "Invalid repository for ${source.packageName}"
        }
        require(ASSET.matches(source.asset)) { "Invalid asset for ${source.packageName}" }
        require(source.signers.isNotEmpty() && source.signers.all { SIGNER.matches(it) }) { "Invalid signers for ${source.packageName}" }
        require(source.label.isNotBlank()) { "Missing label for ${source.packageName}" }
    }
}

enum class PluginCatalogStatus { Checking, Ready, UpdateAvailable, NotPublished, Unavailable, Downloading, Installing, Installed, Current, NeedsPermission, Failed }

data class PluginCatalogSetting(
    val status: PluginCatalogStatus = PluginCatalogStatus.Checking,
    val installed: Boolean = false,
    val progress: Int? = null,
    val detail: String? = null,
    /** The latest release's name, such as 12.2-foldcade.2, once a check has read it. */
    val latest: String? = null,
) {
    // Installed and Current only hold while the package is there; after an uninstall, Install works again.
    val canInstall: Boolean get() = !busy && status !in setOf(PluginCatalogStatus.NotPublished, PluginCatalogStatus.Unavailable) &&
        !(installed && status in setOf(PluginCatalogStatus.Installed, PluginCatalogStatus.Current))
    val busy: Boolean get() = status in setOf(PluginCatalogStatus.Checking, PluginCatalogStatus.Downloading, PluginCatalogStatus.Installing)
}

/** [packageName]'s catalog state. A registry plugin no check has reported on yet is still checking. */
fun PickerModel.pluginCatalog(packageName: String): PluginCatalogSetting =
    pluginCatalogs[packageName] ?: PluginCatalogSetting(installed = apkPlugins.any { it.packageName == packageName })

object ApkPluginCopy {
    const val title = "Plugins"
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
        PluginCatalogStatus.UpdateAvailable -> catalog.latest?.let { "Update available · $it" } ?: "Update available"
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
    fun arrived(model: PickerModel, packageName: String) =
        model.pluginCatalog(packageName).status == PluginCatalogStatus.Installed && model.apkPlugins.any { it.packageName == packageName }
    val source = PluginRegistry.sources.firstOrNull { arrived(next, it.packageName) && !arrived(previous, it.packageName) } ?: return next
    val page = next.settings ?: return next
    if (settingsCategories(next).getOrNull(page.category) != SettingsCategory.Plugins) return next
    val inner = SettingsInner.ApkPlugin(source.packageName)
    if (page.inner != SettingsInner.PluginCatalog(source.packageName) && page.inner != inner) return next
    return next.copy(settings = page.copy(inner = inner, row = settingsInnerFirstRow(inner, next), onRows = true))
}

/** Opening a registry plugin's page asks for its latest release, unless a check is already under way. */
fun pluginPageCheck(inner: SettingsInner, model: PickerModel): Effect? {
    val packageName = when (inner) {
        is SettingsInner.PluginCatalog -> inner.packageName
        is SettingsInner.ApkPlugin -> inner.packageName
        else -> return null
    }
    if (PluginRegistry.source(packageName) == null || model.pluginCatalog(packageName).busy) return null
    return Effect.CheckPluginRelease(packageName, onlyIfStale = true)
}
