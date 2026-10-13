package app.foldcade

/**
 * Maps the default display and whatever other displays exist onto panels.
 * Display ids are discovered at runtime. Nothing here is a Thor panel id.
 */
data class DisplayAssignment(
    val topDisplayId: Int,
    val bottomDisplayId: Int?,
) {
    fun panelFor(displayId: Int): Panel? = when (displayId) {
        topDisplayId -> Panel.Top
        bottomDisplayId -> Panel.Bottom
        else -> null
    }

    fun allIds(): List<Int> = listOfNotNull(topDisplayId, bottomDisplayId)
}

/** The panel that is not the default display: the lowest other id. */
fun otherDisplay(defaultDisplayId: Int, otherDisplayIds: List<Int>): Int? =
    otherDisplayIds.filter { it != defaultDisplayId }.minOrNull()

fun assignDisplays(
    defaultDisplayId: Int,
    otherDisplayIds: List<Int>,
    defaultDisplayIsTop: Boolean,
): DisplayAssignment {
    val other = otherDisplay(defaultDisplayId, otherDisplayIds)
    return when {
        defaultDisplayIsTop -> DisplayAssignment(
            topDisplayId = defaultDisplayId,
            bottomDisplayId = other,
        )
        other != null -> DisplayAssignment(
            topDisplayId = other,
            bottomDisplayId = defaultDisplayId,
        )
        else -> DisplayAssignment(
            topDisplayId = defaultDisplayId,
            bottomDisplayId = null,
        )
    }
}
