// SPDX-License-Identifier: MIT

package app.foldcade.api.plugin

import java.time.Year
import java.util.Locale

/** Facts for a game's info page and top screen. Plain text; every field is optional. */
data class GameDetails(
    val summary: String? = null,
    val releaseYear: Int? = null,
    val genres: List<String> = emptyList(),
    val developer: String? = null,
    val publisher: String? = null,
    /** As the source writes it, such as `1` or `1-4`. */
    val players: String? = null,
)

/**
 * Displayable, bounded facts, or null when [details] has none. [currentYear] is
 * injectable for tests. Sources keep their wording and first genre's spelling.
 * Long summaries end at a sentence or word boundary, followed by an ellipsis.
 */
fun normalizeGameDetails(details: GameDetails?, currentYear: Int = Year.now().value): GameDetails? {
    if (details == null) return null
    val seen = HashSet<String>()
    val normalized = GameDetails(
        summary = details.summary.clean()?.shortened(1_200),
        releaseYear = details.releaseYear?.takeIf { it in 1950..currentYear + 2 },
        genres = details.genres.mapNotNull { it.clean()?.shortened(120) }
            .filter { seen.add(it.lowercase(Locale.ROOT)) }.take(16),
        developer = details.developer.clean()?.shortened(240),
        publisher = details.publisher.clean()?.shortened(240),
        players = details.players.clean()?.shortened(80),
    )
    return normalized.takeUnless {
        it.summary == null && it.releaseYear == null && it.genres.isEmpty() &&
            it.developer == null && it.publisher == null && it.players == null
    }
}

private val DETAILS_WHITESPACE = Regex("[\\s\\p{Z}]+")

private fun String?.clean(): String? = this?.replace(DETAILS_WHITESPACE, " ")?.trim()?.ifEmpty { null }

private fun String.shortened(limit: Int): String {
    if (length <= limit) return this
    val prefix = take(limit - 1)
    val sentence = prefix.indexOfLast { it in ".!?" }
    val word = prefix.lastIndexOf(' ')
    val end = when {
        sentence >= limit / 2 -> sentence + 1
        word > 0 -> word
        prefix.last().isHighSurrogate() -> prefix.length - 1
        else -> prefix.length
    }
    return prefix.take(end).trimEnd() + "…"
}
