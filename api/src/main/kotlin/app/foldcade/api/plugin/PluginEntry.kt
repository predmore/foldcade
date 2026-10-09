package app.foldcade.api.plugin

/**
 * Classpath entry point for an in-tree or out-of-tree plugin.
 * One entry may contribute to any of the four slots. The host stores each
 * contribution in its own slot. A library and a metadata provider from the
 * same entry stay separate objects.
 *
 * The service file name is `META-INF/services/app.foldcade.api.plugin.PluginEntry`.
 * That name is part of this contract.
 *
 * A plugin loaded in-process is part of the GPLv3 work when it is distributed.
 */
interface PluginEntry {
    /**
     * [PLUGIN_API_VERSION] major this plugin was compiled against.
     * The host rejects a different major.
     */
    val apiVersion: Int

    /**
     * [PLUGIN_API_MINOR] this plugin was compiled against.
     * The default is 0. The host loads a minor less than or equal to its own
     * and rejects a newer minor.
     */
    val apiMinor: Int
        get() = 0

    val platforms: List<Platform> get() = emptyList()
    val players: List<Player> get() = emptyList()
    val libraries: List<LibraryBackend> get() = emptyList()
    val metadataProviders: List<MetadataProvider> get() = emptyList()

    /**
     * The host calls this after validation and before the entry is stored.
     * [access] opens credentials only for plugin ids this entry contributed.
     * The default does nothing, so an older plugin minor of this major stays loadable.
     */
    fun bind(access: CredentialAccess) {}
}
