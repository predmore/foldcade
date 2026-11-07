package app.foldcade.ui

import app.foldcade.language.HintKey
import app.foldcade.language.PromptKey
import org.junit.Assert.*
import org.junit.Test

class GridHintSnapshotTest {
    @Test fun aLeavingFooterKeepsItsKeysAndEveryPrefixTogether() {
        val selection = GridHintRow(listOf(HintKey(PromptKey.FaceA, "Check")), listOf("2 selected ·"))
        val snapshot = GridHintSnapshot(selection)
        val destinations = listOf(
            GridHintRow(listOf(HintKey(PromptKey.FaceY, "Undo")), listOf("Removed ·")),
            GridHintRow(listOf(HintKey(PromptKey.FaceA, "Delete")), listOf("Hold", "After closing ·")),
            GridHintRow(emptyList(), emptyList()), // A dialog owns its own hints.
        )
        destinations.forEach { assertSame(selection, snapshot.shown(it, leaving = true)) }
    }

    @Test fun theLastWholeVisibleRowIsKeptAndReentryUpdatesIt() {
        val undo = GridHintRow(listOf(HintKey(PromptKey.FaceY, "Undo")), listOf("Removed ·"))
        val panel = GridHintRow(listOf(HintKey(PromptKey.FaceA, "Delete")), listOf("Hold", "After closing ·"))
        val empty = GridHintRow(emptyList(), emptyList())
        val snapshot = GridHintSnapshot(undo)
        assertSame(panel, snapshot.shown(panel, leaving = false))
        assertSame(panel, snapshot.shown(empty, leaving = true))
        assertSame(empty, snapshot.shown(empty, leaving = false))
        assertSame(empty, snapshot.shown(undo, leaving = true))
    }
}
