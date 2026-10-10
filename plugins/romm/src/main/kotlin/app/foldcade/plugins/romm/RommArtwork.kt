package app.foldcade.plugins.romm

/**
 * Artwork bytes from the configured RomM server, for the shell's image loader.
 * No token is sent: an artwork address is loaded as that string alone.
 * [app.foldcade.romm.RommClient.artwork] refuses an address on another origin,
 * so this reaches no other host. One client is kept while the origin stays the same.
 */
class RommArtwork(
    private val wiring: () -> RommWiring? = { RommPlugins.wiring },
) : AutoCloseable {
    private var held: Pair<String, RommOps>? = null

    /**
     * Null when RomM is not set up. A failed load is a
     * [app.foldcade.api.plugin.PluginException].
     */
    suspend fun load(uri: String): ByteArray? {
        val current = wiring()?.takeIf { it.origin.isNotBlank() } ?: return null
        return translate { client(current).artwork(uri) }
    }

    /**
     * A load still running on a client this replaces can fail. The shell asks
     * for that image again the next time it is shown.
     */
    private fun client(current: RommWiring): RommOps = synchronized(this) {
        val open = held
        if (open != null && open.first == current.origin) return open.second
        open?.second?.close()
        val next = current.open(current.origin) { null }
        held = current.origin to next
        next
    }

    override fun close() = synchronized(this) {
        held?.second?.close()
        held = null
    }
}
