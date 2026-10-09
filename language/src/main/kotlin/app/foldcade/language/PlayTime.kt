package app.foldcade.language

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Library order. Recently played uses the host's last-played times. */
enum class LibrarySort {
    Listed,
    RecentlyPlayed,
}

fun LibrarySort.toggled(): LibrarySort = when (this) {
    LibrarySort.Listed -> LibrarySort.RecentlyPlayed
    LibrarySort.RecentlyPlayed -> LibrarySort.Listed
}

fun orderLabel(sort: LibrarySort): String = when (sort) {
    LibrarySort.Listed -> "Order  Library"
    LibrarySort.RecentlyPlayed -> "Order  Recently played"
}

/** Total active play time. Zero means this game has no recorded play. */
fun playedLine(activeMillis: Long): String {
    if (activeMillis <= 0L) return "Played  —"
    val minutes = activeMillis / 60_000L
    if (minutes < 1L) return "Played  <1m"
    val hours = minutes / 60L
    val rest = minutes % 60L
    val body = when {
        hours > 0L && rest > 0L -> "${hours}h ${rest}m"
        hours > 0L -> "${hours}h"
        else -> "${rest}m"
    }
    return "Played  $body"
}

/** Last time this game was played. Null means it has not been played. */
fun lastPlayedLine(atMillis: Long?, nowMillis: Long, zone: ZoneId): String {
    if (atMillis == null) return "Last played  —"
    val played = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val whenText = when (played) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> {
            val pattern = if (played.year == today.year) "d MMM" else "d MMM yyyy"
            DateTimeFormatter.ofPattern(pattern, Locale.US).format(played)
        }
    }
    return "Last played  $whenText"
}
