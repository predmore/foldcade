package app.foldcade.plugins.romm

import app.foldcade.api.plugin.credentialFreeArtworkUri
import app.foldcade.artwork.ArtQuery
import app.foldcade.artwork.ArtSet
import app.foldcade.artwork.ArtSource
import app.foldcade.artwork.artworkTitleMatches
import app.foldcade.romm.RomCoverMatch

/**
 * Covers for any game, using the configured RomM server's artwork providers.
 * Search uses the existing client token; the image address carries no credentials.
 * The host supplies the same public-image guard its loader uses. Scenes keep
 * using their existing sources: RomM's cover endpoint has no heroes or logos.
 */
class RommCoverArt(
    private val allowsImage: (String) -> Boolean,
    wiring: () -> RommWiring? = { RommPlugins.wiring },
) : ArtSource {
    override val id = "romm.cover"
    private val gate = RommGate(wiring)

    override fun handles(query: ArtQuery): Boolean =
        query.title.isNotBlank() && gate.peek()?.origin?.isNotBlank() == true

    override suspend fun find(query: ArtQuery): ArtSet? {
        if (!handles(query)) return null
        return gate.call { _, ops ->
            // RomM orders these by the server's configured provider priority.
            ops.searchCovers(query.title.trim()).firstNotNullOfOrNull { match ->
                if (artworkTitleMatches(match.name, query.title)) match.art() else null
            }
        }
    }

    private fun RomCoverMatch.art(): ArtSet? {
        val resources = resources
            .filter { it.type == "static" && !it.nsfw && !it.humor && !it.epilepsy }
            .mapNotNull { resource ->
                val uri = credentialFreeArtworkUri(encodeIllegalUriChars(resource.url))
                    ?.takeIf(allowsImage) ?: return@mapNotNull null
                resource.copy(url = uri)
            }
        // Prefer the portrait library grid, then another portrait. A square
        // is still useful when it is the only cover the provider has.
        val cover = resources.firstOrNull { it.width == 600 && it.height == 900 }
            ?: resources.firstOrNull { it.width > 0 && it.height > it.width }
            ?: resources.firstOrNull { it.width > 0 && it.height == it.width }
            ?: return null
        val square = resources.firstOrNull { it.width > 0 && it.width == it.height }
        return ArtSet(source = id, cover = cover.url, square = square?.url)
    }

    /** Retires the authenticated client when the host changes its connection. */
    suspend fun disconnect() = gate.disconnect()
}
