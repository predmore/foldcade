package app.foldcade.localfolder

import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.PLUGIN_API_MINOR
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.Platform
import app.foldcade.api.plugin.PluginEntry

/**
 * Classpath entry for the local-folder library and its platform definitions.
 * The service file is `META-INF/services/app.foldcade.api.plugin.PluginEntry`.
 * The host stores [platforms], aliases included, and passes that list to RomM.
 * A slug such as `3ds` or `nds` becomes `nintendo-3ds` or `nintendo-ds`.
 * Ids that already match a RomM slug, such as `snes`, stay the id.
 * This entry does not contribute a metadata provider.
 *
 * A plugin Foldcade loads in-process is part of the GPLv3 work when distributed.
 */
class LocalFolderEntry : PluginEntry {
    override val apiVersion: Int = PLUGIN_API_VERSION
    override val apiMinor: Int = PLUGIN_API_MINOR

    /** The library instance the host stores. Bind a document tree on it before connect. */
    val folder: LocalFolderBackend = LocalFolderBackend()

    override val libraries: List<LibraryBackend> = listOf(folder)

    override val platforms: List<Platform> = PlatformCatalog.specs.map { spec ->
        CatalogPlatform(
            id = spec.platform.id,
            displayName = spec.platform.name,
            extensions = spec.extensions,
            aliases = spec.aliases,
        )
    }
}

private class CatalogPlatform(
    override val id: String,
    override val displayName: String,
    override val extensions: Set<String>,
    override val aliases: Set<String>,
) : Platform
