package app.foldcade

import app.foldcade.language.HostScreen
import app.foldcade.language.Meaning
import app.foldcade.language.panelRows
import app.foldcade.language.tileBlock

/**
 * Walks the open menu's focus to row [target] with the D-pad, as a player would: up and down
 * between rows, left and right along the round tiles. Fails rather than walking forever.
 */
internal fun walkPanelTo(shell: ShellController, target: Int, screen: HostScreen) {
    repeat(64) {
        val panel = shell.model.panel ?: error("the menu closed")
        val at = panel.index
        if (at == target) return
        val block = tileBlock(panelRows(panel, shell.model))
        val step = when {
            at in block && target in block -> if (target > at) Meaning.MoveRight else Meaning.MoveLeft
            at < target -> Meaning.MoveDown
            else -> Meaning.MoveUp
        }
        shell.onMeaning(step, screen)
    }
    error("could not reach row $target")
}
