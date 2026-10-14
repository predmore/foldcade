package app.foldcade.artwork

import java.text.Normalizer
import java.util.Locale

/** A compilation prefix should not hide the actual game name from autocomplete. */
fun artworkSearchTitles(title: String): List<String> = buildList {
    add(title.trim())
    Regex(" - |:").findAll(title).forEach { separator ->
        title.substring(separator.range.last + 1).trim().takeIf { it.isNotEmpty() }?.let(::add)
    }
}.filter { it.isNotEmpty() }.distinct()

fun artworkMatchScore(candidate: String, title: String): Double {
    val wanted = artworkWords(title)
    val found = artworkWords(candidate)
    if (wanted.isEmpty() || found.isEmpty()) return 0.0
    if (wanted.joinToString("") == found.joinToString("")) return 1.0
    val shared = wanted.intersect(found.toSet()).size
    return 2.0 * shared / (wanted.size + found.size)
}

/** Stable ties keep the API's order; suffix matches outrank matches to packaging words alone. */
fun rankedArtworkGames(title: String, games: List<GridGame>): List<GridGame> {
    val queries = artworkSearchTitles(title)
    return games.distinctBy { it.id }.sortedByDescending { game ->
        queries.mapIndexed { index, query ->
            val score = artworkMatchScore(game.name, query)
            when {
                index == 0 && score == 1.0 -> 1.0
                index > 0 -> score * 0.99
                queries.size > 1 -> score * 0.8
                else -> score
            }
        }.maxOrNull() ?: 0.0
    }.take(8)
}

private fun artworkWords(name: String): List<String> =
    Normalizer.normalize(name, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace("&", " and ")
        .replace(Regex("^\\W*the\\s+"), "")
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotEmpty() }
