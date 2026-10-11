package app.foldcade.plugins.romm

import app.foldcade.api.plugin.Achievement
import app.foldcade.api.plugin.Artwork
import app.foldcade.api.plugin.ArtworkRole
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameAchievements
import app.foldcade.api.plugin.GameMeta
import app.foldcade.api.plugin.MetadataProvider
import app.foldcade.api.plugin.credentialFreeArtworkUri
import app.foldcade.romm.RomQuery
import app.foldcade.romm.RomSummary
import app.foldcade.romm.RommException
import kotlin.coroutines.cancellation.CancellationException
import java.text.Normalizer
import java.util.Locale

internal class RommMetadata(
    private val resolve: (String) -> RommGate?,
) : MetadataProvider {
    constructor(gate: RommGate) : this({ backendId ->
        if (isRommInstance(backendId)) gate else null
    })

    override val id = ROMM_METADATA_ID
    override val displayName = "RomM metadata"

    override fun cached(game: Game): GameMeta? {
        val gate = resolve(game.backendId) ?: return null
        val wiring = gate.peek() ?: return null
        val rom = gate.catalog.rom(game.remoteKey) ?: return null
        return rom.toMeta(wiring.origin)
    }

    override suspend fun fetch(game: Game): GameMeta? {
        val gate = resolve(game.backendId) ?: return null
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

    /**
     * The ROM's RetroAchievements list, fresh from the server, and what the
     * user has earned when RomM knows. A ROM RomM matched to no game has none.
     */
    override suspend fun achievements(game: Game): GameAchievements? {
        val gate = resolve(game.backendId) ?: return null
        val romId = game.remoteKey.toLongOrNull()?.takeIf { it >= 1 } ?: return null
        return translate {
            gate.session { wiring, ops ->
                val rom = ops.rom(romId)
                gate.catalog.rememberRoms(listOf(rom))
                if (rom.achievements.isEmpty()) return@session null
                rom.toAchievements(wiring.origin, earnedOn(ops, rom.raId))
            }
        }
    }
}

/**
 * Badge ids the user has earned on [raId]. Null when RomM cannot say: no
 * RetroAchievements username linked there, or a token without `me.read`.
 * That leaves the list without progress; it never asks for a new sign-in.
 */
private suspend fun earnedOn(ops: RommOps, raId: Long?): Set<String>? {
    if (raId == null) return null
    val user = try {
        ops.me()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: RommException) {
        return null
    }
    if (user.raUsername.isNullOrBlank()) return null
    return user.progress.firstOrNull { it.romRaId == raId }?.earnedBadgeIds.orEmpty()
}

/**
 * The list as the plugin API carries it. Earned is a badge id in [earned].
 * Badges load from the RomM server: colour when earned or when progress is
 * unknown, the locked one otherwise. An achievement with no title is dropped.
 */
internal fun RomSummary.toAchievements(origin: String, earned: Set<String>?): GameAchievements =
    GameAchievements(
        achievements = achievements.mapNotNull { ra ->
            val title = ra.title?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val got = earned != null && ra.badgeId != null && ra.badgeId in earned
            val path = if (got || earned == null) ra.badgePath else ra.badgePathLock ?: ra.badgePath
            Achievement(
                title = title,
                description = ra.description?.trim()?.takeIf { it.isNotEmpty() },
                points = ra.points,
                earned = got,
                badge = absoluteHttp(origin, path),
            )
        },
        progressKnown = earned != null,
    )

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
    return credentialFreeArtworkUri(encodeIllegalUriChars(absolute))
}

/**
 * [url] with every character a URI cannot hold percent-encoded. RomM 5.3 sends
 * cover paths such as `/cover/big.png?ts=2026-10-03 04:20:36`, and a raw space
 * makes the address unparseable, so the cover would be dropped. Escapes already
 * in [url] are kept as they are.
 */
internal fun encodeIllegalUriChars(url: String): String {
    val bytes = url.toByteArray(Charsets.UTF_8)
    val out = StringBuilder(bytes.size)
    var index = 0
    while (index < bytes.size) {
        val code = bytes[index].toInt() and 0xff
        val char = code.toChar()
        val escape = char == '%' && index + 2 < bytes.size &&
            isHex(bytes[index + 1].toInt().toChar()) && isHex(bytes[index + 2].toInt().toChar())
        when {
            escape -> out.append(char)
            code < 0x80 && char != '%' && (char.isLetterOrDigit() || char in URI_PUNCTUATION) -> out.append(char)
            else -> out.append('%').append(HEX[code shr 4]).append(HEX[code and 0xf])
        }
        index++
    }
    return out.toString()
}

private fun isHex(char: Char): Boolean = char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'

/** RFC 3986 unreserved and reserved punctuation. Letters and digits are legal too. */
private const val URI_PUNCTUATION = "-._~:/?#[]@!$&'()*+,;="

private const val HEX = "0123456789ABCDEF"
