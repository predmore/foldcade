package app.foldcade.plugins.romm

import app.foldcade.api.plugin.Artwork
import app.foldcade.api.plugin.ArtworkRole
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameMeta
import app.foldcade.api.plugin.MetadataProvider
import app.foldcade.api.plugin.credentialFreeArtworkUri
import app.foldcade.romm.RomQuery
import app.foldcade.romm.RomSummary
import java.text.Normalizer
import java.util.Locale

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
        val romId = game.remoteKey.toLongOrNull()?.takeIf { it >= 1 }
        return translate {
            gate.session { wiring, ops ->
                if (romId != null) {
                    val rom = ops.rom(romId)
                    gate.catalog.rememberRoms(listOf(rom))
                    rom.toMeta(wiring.origin)
                } else {
                    val term = game.label.trim().ifEmpty { return@session null }
                    val page = ops.roms(RomQuery(searchTerm = term, limit = 50, offset = 0))
                    val rom = page.items.byTitle(term) ?: return@session null
                    gate.catalog.rememberRoms(listOf(rom))
                    rom.toMeta(wiring.origin)
                }
            }
        }
    }
}

/**
 * One search hit is not a match. RomM's search is partial, so metadata attaches
 * only when the ROM name or fsName equals the label after normalization.
 */
private fun List<RomSummary>.byTitle(term: String): RomSummary? {
    val needle = normalizeRomTitle(term)
    if (needle.isEmpty()) return null
    return firstOrNull { rom ->
        normalizeRomTitle(rom.name) == needle || normalizeRomTitle(rom.fsName) == needle
    }
}

private fun normalizeRomTitle(raw: String?): String =
    Normalizer.normalize(raw.orEmpty(), Normalizer.Form.NFKC)
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")

internal fun RomSummary.toMeta(origin: String): GameMeta {
    val artwork = mutableListOf<Artwork>()
    fun add(role: ArtworkRole, raw: String?) {
        val uri = absoluteHttp(origin, raw) ?: return
        if (artwork.none { it.role == role && it.uri == uri }) artwork += Artwork(role, uri)
    }
    add(ArtworkRole.Cover, pathCoverLarge)
    add(ArtworkRole.Icon, pathCoverSmall)
    if (artwork.none { it.role == ArtworkRole.Cover }) add(ArtworkRole.Cover, urlCover)
    // The top screen draws a screenshot crisp when there is no wide art.
    add(ArtworkRole.Screenshot, screenshots.firstOrNull())
    return GameMeta(
        title = name?.takeIf { it.isNotBlank() } ?: fsName,
        artwork = artwork,
    )
}

internal fun absoluteHttp(origin: String, raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val absolute = when {
        value.startsWith("http://") || value.startsWith("https://") -> value
        value.startsWith("/") -> origin.trimEnd('/') + value
        else -> return null
    }
    return credentialFreeArtworkUri(absolute)
}
