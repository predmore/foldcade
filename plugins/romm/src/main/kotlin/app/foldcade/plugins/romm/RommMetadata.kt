package app.foldcade.plugins.romm

import app.foldcade.api.plugin.Artwork
import app.foldcade.api.plugin.ArtworkRole
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameMeta
import app.foldcade.api.plugin.MetadataProvider
import app.foldcade.romm.RomQuery
import app.foldcade.romm.RomSummary

internal class RommMetadata(
    private val gate: RommGate,
) : MetadataProvider {
    override val id = ROMM_METADATA_ID
    override val displayName = "RomM metadata"

    override fun cached(game: Game): GameMeta? {
        if (game.backendId != ROMM_LIBRARY_ID) return null
        val wiring = gate.peek() ?: return null
        val rom = gate.catalog.rom(game.remoteKey) ?: return null
        return rom.toMeta(wiring.origin)
    }

    override suspend fun fetch(game: Game): GameMeta? {
        if (game.backendId != ROMM_LIBRARY_ID) return null
        cached(game)?.let { return it }
        return translate {
            gate.session { wiring, ops ->
                val romId = game.remoteKey.toLongOrNull() ?: return@session null
                val page = ops.roms(
                    RomQuery(
                        searchTerm = game.label.trim().ifEmpty { null },
                        limit = 50,
                        offset = 0,
                    ),
                )
                gate.catalog.rememberRoms(page.items)
                page.items.find { it.id == romId }?.toMeta(wiring.origin)
            }
        }
    }
}

internal fun RomSummary.toMeta(origin: String): GameMeta {
    val artwork = mutableListOf<Artwork>()
    fun add(role: ArtworkRole, raw: String?) {
        val uri = absoluteHttp(origin, raw) ?: return
        if (artwork.none { it.role == role && it.uri == uri }) artwork += Artwork(role, uri)
    }
    add(ArtworkRole.Cover, pathCoverLarge)
    add(ArtworkRole.Icon, pathCoverSmall)
    if (artwork.none { it.role == ArtworkRole.Cover }) add(ArtworkRole.Cover, urlCover)
    return GameMeta(
        title = name?.takeIf { it.isNotBlank() } ?: fsName,
        artwork = artwork,
    )
}

internal fun absoluteHttp(origin: String, raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (value.startsWith("http://") || value.startsWith("https://")) return value
    if (!value.startsWith("/")) return null
    return origin.trimEnd('/') + value
}
