package app.foldcade.language

/** The command center's words for a game's achievements. */
object AchievementCopy {
    const val title = "Achievements"
    const val back = "Back"
}

/**
 * One line for the command center: what the user has earned out of [total],
 * or only how many there are when [earned] is unknown. Null with none.
 */
fun achievementLine(earned: Int?, total: Int): String? = when {
    total <= 0 -> null
    earned == null -> if (total == 1) "1 achievement" else "$total achievements"
    else -> "${earned.coerceIn(0, total)} of $total achievements"
}

/** An achievement's points, as the list shows them beside its title. */
fun achievementPoints(points: Int?): String? = when {
    points == null || points <= 0 -> null
    points == 1 -> "1 point"
    else -> "$points points"
}
