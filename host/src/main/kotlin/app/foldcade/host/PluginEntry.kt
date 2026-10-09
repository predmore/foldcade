package app.foldcade.host

import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.MetadataProvider
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.Platform
import app.foldcade.api.plugin.Player

/**
 * Classpath entry point for an in-tree or out-of-tree plugin.
 * One entry may contribute to any of the four slots. The host stores each
 * contribution in its own slot. A library and a metadata provider from the
 * same entry stay separate objects.
 *
 * A plugin loaded in-process is part of the GPLv3 work when it is distributed.
 */
interface PluginEntry {
    /**
     * [PLUGIN_API_VERSION] this plugin was compiled against.
     * The host rejects a different major.
     */
    val apiMajor: Int

    val platforms: List<Platform> get() = emptyList()
    val players: List<Player> get() = emptyList()
    val libraries: List<LibraryBackend> get() = emptyList()
    val metadataProviders: List<MetadataProvider> get() = emptyList()
}
