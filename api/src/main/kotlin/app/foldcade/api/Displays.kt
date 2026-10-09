// SPDX-License-Identifier: Apache-2.0

package app.foldcade.api

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

fun assignDisplays(
    defaultDisplayId: Int,
    otherDisplayIds: List<Int>,
    defaultDisplayIsTop: Boolean,
): DisplayAssignment {
    val other = otherDisplayIds
        .asSequence()
        .filter { it != defaultDisplayId }
        .distinct()
        .sorted()
        .firstOrNull()
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
