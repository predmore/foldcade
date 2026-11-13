package app.foldcade.language

/** Installed APK metadata and current device consent. No pairing material enters the UI model. */
data class ApkPluginSetting(
    val packageName: String, val activity: String, val label: String, val version: String, val fingerprint: String,
    val supported: Boolean, val scopes: Set<String>, val granted: Set<String>, val useForMoonlight: Boolean,
    /** The SHA-256 of each certificate the installed APK is signed with. */
    val signers: Set<String> = emptySet(),
) {
    /** Signed exactly as its [PluginRegistry] entry pins, as an install from the registry is. */
    val verified: Boolean get() = signers.isNotEmpty() && PluginRegistry.source(packageName)?.signers == signers
}

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
    /** Its full name, as its page and Android call it. */
    val label: String,
    /**
     * What its tile says: a word or two that fits a tile on one line. The tile sits under
     * Plugins and wears [mark], so this leaves out what those already say.
     */
    val name: String,
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
        name = "Moonlight",
        detail = "Streams Moonlight games on both screens.",
        mark = "moonlight",
        repository = "predmore/foldcade-moonlight",
        asset = "foldcade-moonlight.apk",
        signers = setOf(FOLDCADE_RELEASE_SIGNER),
    )

    val sources: List<PluginSource> = listOf(moonlight).also { requireValidRegistry(it) }

    fun source(packageName: String): PluginSource? = sources.firstOrNull { it.packageName == packageName }

    /** [packageName]'s entry, or the entry of the plugin it is the debug build of. */
    fun sourceOrDebug(packageName: String): PluginSource? = source(packageName) ?: source(packageName.removeSuffix(".debug"))
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
        require(source.name.isNotBlank()) { "Missing name for ${source.packageName}" }
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
    const val installing = "Installing"
    const val updating = "Updating"
    const val allowInstalls = "Allow installs"
    const val unsupported = "Foldcade does not support this plugin’s platform yet."

    /** The signer of a plugin signed as its registry entry pins. */
    const val verified = "Verified"

    /** A debug build's tile, so it reads apart from the release beside it. */
    fun debugName(name: String) = "$name Debug"
    const val officialMoonlight = "Moonlight games open in Moonlight instead."
    const val unknownSources = "Allow Foldcade to install apps in Android, then come back here."
    const val downloadFailed = "Download failed. Check your connection and try again."
    const val verificationFailed = "This APK could not be verified. It was not installed."
    const val installFailed = "Installation did not finish. Try again."
    const val installDeclined = "Installation canceled."

    /** Under the sharing switch: everything the switch allows, in one breath. */
    const val consent = "It can stream and quit games on your paired computers. Your pairing key stays in Foldcade."

    /**
     * The one row that installs, updates, or checks: what A does, and the state on its right.
     * A plugin that is current offers the check; one with a release to take offers that.
     */
    fun action(catalog: PluginCatalogSetting): PluginAction {
        val take = if (catalog.installed) update else install
        val check = if (catalog.installed) check else checkAgain
        return when (catalog.status) {
            PluginCatalogStatus.Checking -> PluginAction(check, "Checking…")
            // Installed, with a release that does not say its version: only the download can tell whether it is newer.
            PluginCatalogStatus.Ready -> PluginAction(take, catalog.latest)
            PluginCatalogStatus.UpdateAvailable -> PluginAction(update, catalog.latest, fresh = true)
            PluginCatalogStatus.NotPublished -> PluginAction(check, "Not available yet")
            PluginCatalogStatus.Unavailable -> PluginAction(check, "Unavailable", attention = true)
            PluginCatalogStatus.Downloading -> PluginAction(if (catalog.installed) updating else installing, catalog.progress?.let { "$it%" } ?: "Downloading…")
            PluginCatalogStatus.Installing -> PluginAction(if (catalog.installed) updating else installing, "Waiting for Android…")
            // Just installed: its installed version is the one this check took.
            PluginCatalogStatus.Installed -> if (catalog.installed) PluginAction(check, "Up to date", healthy = true) else PluginAction(install, catalog.latest)
            PluginCatalogStatus.Current -> if (catalog.installed) PluginAction(check, "Up to date", healthy = true) else PluginAction(install, catalog.latest)
            PluginCatalogStatus.NeedsPermission -> PluginAction(allowInstalls, null)
            PluginCatalogStatus.Failed -> PluginAction(take, "Didn’t finish", attention = true)
        }
    }

    /** Under the action row: why it stopped, or what Android needs first. */
    fun actionCaption(catalog: PluginCatalogSetting): String? = when (catalog.status) {
        PluginCatalogStatus.NeedsPermission -> unknownSources
        PluginCatalogStatus.Failed -> catalog.detail ?: installFailed
        else -> null
    }
}

/** [label] says what A does; [value] is the state on the right. [fresh] marks a release newer than the one installed. */
data class PluginAction(
    val label: String,
    val value: String?,
    val attention: Boolean = false,
    val healthy: Boolean = false,
    val fresh: Boolean = false,
)

/** One of a plugin's facts under its rows: what it is, not something to change. */
data class PluginFact(val label: String, val value: String, val verified: Boolean = false)

/** A plugin page's facts: the version installed, its package, and who signed it. */
fun pluginFacts(packageName: String, model: PickerModel): List<PluginFact> {
    val plugin = model.apkPlugins.firstOrNull { it.packageName == packageName } ?: return emptyList()
    return listOf(
        PluginFact(ApkPluginCopy.version, plugin.version),
        PluginFact(ApkPluginCopy.packageName, plugin.packageName),
        if (plugin.verified) PluginFact(ApkPluginCopy.signer, "${ApkPluginCopy.verified} · ${plugin.fingerprint}", verified = true)
        else PluginFact(ApkPluginCopy.signer, plugin.fingerprint),
    )
}

/** A plugin tile's badge: an update waiting, or nothing. */
fun pluginTileFresh(inner: SettingsInner, model: PickerModel): Boolean {
    val packageName = (inner as? SettingsInner.ApkPlugin)?.packageName ?: return false
    return PluginRegistry.source(packageName) != null && model.pluginCatalog(packageName).status == PluginCatalogStatus.UpdateAvailable
}

/**
 * Installation and discovery can report in either order. Once both have arrived, a first install,
 * made from the catalog page, moves on to the plugin's page and its consent. An update leaves the
 * player where they are: the page's first row is a switch one more A would flip.
 */
fun afterApkPluginInstall(previous: PickerModel, next: PickerModel): PickerModel {
    fun arrived(model: PickerModel, packageName: String) =
        model.pluginCatalog(packageName).status == PluginCatalogStatus.Installed && model.apkPlugins.any { it.packageName == packageName }
    val source = PluginRegistry.sources.firstOrNull { arrived(next, it.packageName) && !arrived(previous, it.packageName) } ?: return next
    val page = next.settings ?: return next
    if (settingsCategories(next).getOrNull(page.category) != SettingsCategory.Plugins) return next
    val inner = SettingsInner.ApkPlugin(source.packageName)
    if (page.inner != SettingsInner.PluginCatalog(source.packageName)) return next
    return next.copy(settings = page.copy(inner = inner, row = 0, onRows = true))
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
