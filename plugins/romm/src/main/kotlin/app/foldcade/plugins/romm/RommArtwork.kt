package app.foldcade.plugins.romm

import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.credentialFreeArtworkUri
import app.foldcade.artwork.ArtBytes
import app.foldcade.romm.sameOrigin
import app.foldcade.romm.RommClient
import java.nio.file.Path
import java.net.URI

/**
 * Artwork bytes from the configured RomM server, for the shell's image loader.
 * Public artwork stays credential-free. Private save screenshots use the connection token.
 * [app.foldcade.romm.RommClient.artwork] refuses an address on another origin,
 * so this reaches no other host. Cached bytes survive process death and are
 * bounded and scoped to the server. Origin checks also apply to cache hits.
 */
class RommArtwork(
    private val wiring: () -> RommWiring? = { RommPlugins.wiring },
) : AutoCloseable {
    private class Held(val ops: RommOps, val images: ArtBytes) {
        var users = 0
        var retired = false
    }
    private val held = mutableMapOf<Pair<Path, Boolean>, Held>()

    /**
     * Null when RomM is not set up. A failed load is a
     * [app.foldcade.api.plugin.PluginException].
     */
    suspend fun load(uri: String): ByteArray? {
        val current = wiring()?.takeIf { it.origin.isNotBlank() } ?: return null
        if (credentialFreeArtworkUri(uri) != uri) {
            throw PluginException.Unavailable("Artwork must be a credential-free address on the RomM origin")
        }
        if (!sameOrigin(current.origin, uri) || !withinBasePath(current.origin, uri)) {
            throw PluginException.Unavailable("Artwork must be a credential-free address on the RomM origin")
        }
        val client = client(current, RommClient.isSaveScreenshotUri(current.origin, uri))
        try {
            return translate { client.images.load(uri) }
        } finally {
            synchronized(this) {
                client.users--
                if (client.retired && client.users == 0) client.ops.close()
            }
        }
    }

    /**
     * Separate public and authenticated clients. Changing the origin retires old clients after active loads drain.
     */
    private fun client(current: RommWiring, authenticated: Boolean): Held = synchronized(this) {
        val removed = held.keys.filter { it.first != current.romRoot }
        removed.forEach { root -> held.remove(root)?.let(::retire) }
        held.getOrPut(current.romRoot to authenticated) {
            val ops = current.open(current.origin) { if (authenticated) current.tokenSource.accessToken() else null }
            Held(ops, ArtBytes(current.artRoot.toFile(), { ops.artwork(it) }))
        }.also { it.users++ }
    }

    override fun close() = synchronized(this) {
        held.values.forEach(::retire)
        held.clear()
    }

    private fun retire(client: Held) {
        client.retired = true
        if (client.users == 0) client.ops.close()
    }

    private fun withinBasePath(origin: String, uri: String): Boolean {
        val base = URI(RommData.canonicalOrigin(origin)).path.orEmpty().trimEnd('/')
        val path = URI(uri).path.orEmpty()
        return base.isEmpty() || path == base || path.startsWith("$base/")
    }
}
