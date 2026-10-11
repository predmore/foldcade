package app.foldcade.plugins.romm

import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.credentialFreeArtworkUri
import app.foldcade.artwork.ArtBytes
import app.foldcade.romm.sameOrigin
import java.nio.file.Path

/**
 * Artwork bytes from the configured RomM server, for the shell's image loader.
 * No token is sent: an artwork address is loaded as that string alone.
 * [app.foldcade.romm.RommClient.artwork] refuses an address on another origin,
 * so this reaches no other host. Cached bytes survive process death and are
 * bounded and scoped to the server. Origin checks also apply to cache hits.
 */
class RommArtwork(
    private val wiring: () -> RommWiring? = { RommPlugins.wiring },
) : AutoCloseable {
    private data class Held(val origin: String, val root: Path, val ops: RommOps, val images: ArtBytes)
    private var held: Held? = null

    /**
     * Null when RomM is not set up. A failed load is a
     * [app.foldcade.api.plugin.PluginException].
     */
    suspend fun load(uri: String): ByteArray? {
        val current = wiring()?.takeIf { it.origin.isNotBlank() } ?: return null
        if (!sameOrigin(current.origin, uri) || credentialFreeArtworkUri(uri) != uri) {
            throw PluginException.Unavailable("Artwork must be a credential-free address on the RomM origin")
        }
        return translate { client(current).images.load(uri) }
    }

    /**
     * A load still running on a client this replaces can fail. The shell asks
     * for that image again the next time it is shown.
     */
    private fun client(current: RommWiring): Held = synchronized(this) {
        val open = held
        val root = current.romRoot.resolve("artwork")
        if (open != null && open.origin == current.origin && open.root == root) return open
        open?.ops?.close()
        val ops = current.open(current.origin) { null }
        Held(current.origin, root, ops, ArtBytes(root.toFile(), { ops.artwork(it) })).also { held = it }
    }

    override fun close() = synchronized(this) {
        held?.ops?.close()
        held = null
    }
}
