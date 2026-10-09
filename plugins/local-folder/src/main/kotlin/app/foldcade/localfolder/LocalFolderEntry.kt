package app.foldcade.localfolder

import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.PLUGIN_API_MINOR
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.PluginEntry

/**
 * Classpath entry for the local-folder library.
 * The service file is `META-INF/services/app.foldcade.api.plugin.PluginEntry`.
 * This entry contributes a library only. It does not contribute a metadata provider.
 *
 * A plugin Foldcade loads in-process is part of the GPLv3 work when distributed.
 */
class LocalFolderEntry : PluginEntry {
    override val apiVersion: Int = PLUGIN_API_VERSION
    override val apiMinor: Int = PLUGIN_API_MINOR

    /** The library instance the host stores. Bind a document tree on it before connect. */
    val folder: LocalFolderBackend = LocalFolderBackend()

    override val libraries: List<LibraryBackend> = listOf(folder)
}
