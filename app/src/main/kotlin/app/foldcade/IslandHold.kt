package app.foldcade

import app.foldcade.language.Meaning
import app.foldcade.language.Side

/**
 * Debug capture hold for the island morph. A missing extra means the live animation.
 * Progress is 0.5 at mid-morph and 1 when the menu is fully open.
 */
data class IslandHold(val side: Side, val progress: Float)

fun parseIslandHold(raw: String?): IslandHold? = when (raw) {
    "left-mid" -> IslandHold(Side.Left, 0.5f)
    "left-open" -> IslandHold(Side.Left, 1f)
    "right-mid" -> IslandHold(Side.Right, 0.5f)
    "right-open" -> IslandHold(Side.Right, 1f)
    else -> null
}

fun IslandHold.meaning(): Meaning =
    if (side == Side.Left) Meaning.LeftPanel else Meaning.RightPanel
