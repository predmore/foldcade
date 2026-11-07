package app.foldcade.ui

import app.foldcade.language.HintKey

/** Everything the footer said on one surface, before that surface starts leaving. */
internal data class GridHintRow(val keys: List<HintKey>, val prefixes: List<String>)

internal class GridHintSnapshot(initial: GridHintRow) {
    private var row = initial

    fun shown(next: GridHintRow, leaving: Boolean): GridHintRow {
        if (!leaving) row = next
        return row
    }
}
