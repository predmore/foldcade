package app.foldcade

import app.foldcade.language.Chrome
import app.foldcade.language.Effect
import app.foldcade.language.GridFocus
import app.foldcade.language.HostScreen
import app.foldcade.language.Meaning
import app.foldcade.language.Metrics
import app.foldcade.language.PickerModel
import app.foldcade.language.displayOrder
import app.foldcade.language.focusAndActivateDialog
import app.foldcade.language.homePrompt
import app.foldcade.language.reduce
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ShellController(private val store: SessionStore) {
    var model by mutableStateOf(initial())
        private set

    fun onMeaning(meaning: Meaning, screen: HostScreen): Effect? {
        val (next, effect) = reduce(prepared(), meaning, screen)
        publish(next)
        return effect
    }

    fun setRowsPerPage(rows: Int) {
        val next = rows.coerceAtLeast(1)
        if (model.rowsPerPage == next) return
        model = model.copy(rowsPerPage = next)
    }

    fun setHomeRoleHeld(held: Boolean) {
        if (model.homeRoleHeld == held) return
        model = model.copy(homeRoleHeld = held)
    }

    fun noteFolderExplained() {
        store.setFolderGrantExplained()
        model = model.copy(folderGrantPending = false)
    }

    fun touchCell(index: Int, screen: HostScreen) {
        if (model.dialog != null || model.panel != null || model.connectOpen) return
        val current = model.focus
        val already = current.chrome == null && current.cellIndex == index
        if (!already) {
            publish(
                model.copy(
                    focus = GridFocus(
                        cellIndex = index,
                        lastColumn = index % Metrics.columns,
                        chrome = null,
                    ),
                ),
            )
            return
        }
        onMeaning(Meaning.Activate, screen)
    }

    fun touchChrome(chrome: Chrome, screen: HostScreen) {
        if (model.dialog != null || model.panel != null || model.connectOpen) return
        publish(model.copy(focus = model.focus.copy(chrome = chrome)))
        onMeaning(Meaning.Activate, screen)
    }

    /** One tap focuses the button and activates it. Keys still move, then Activate. */
    fun touchDialog(index: Int, screen: HostScreen): Effect? {
        val dialog = model.dialog ?: return null
        if (dialog.screen != screen) return null
        val (next, effect) = focusAndActivateDialog(model, index)
        publish(next)
        return effect
    }

    fun maybeAskHome(roleHeld: Boolean) {
        if (roleHeld) {
            store.setHomePromptSettled()
            return
        }
        if (store.homePromptSettled() || model.dialog != null) return
        publish(model.copy(dialog = homePrompt()))
    }

    fun focusedGame(): ShelfGame? {
        val index = model.focus.cellIndex
        val source = displayOrder(model).getOrElse(index) { index }
        return Shelf.games.getOrNull(source)
    }

    private fun prepared(): PickerModel {
        val game = focusedGame()
        val visible = store.session.launchTargetControlVisible(game?.occupiesBothDisplays == true)
        return model.copy(count = Shelf.games.size, showLaunchTarget = visible)
    }

    private fun publish(next: PickerModel) {
        if (next.primaryIsTop != store.session.defaultDisplayIsTop) {
            store.update { it.withDefaultDisplayIsTop(next.primaryIsTop) }
        }
        model = next.copy(count = Shelf.games.size)
    }

    private fun initial(): PickerModel = PickerModel(
        count = Shelf.games.size,
        rowsPerPage = 2,
        showLaunchTarget = true,
        primaryIsTop = store.session.defaultDisplayIsTop,
        folderGrantPending = store.folderGrantPending(),
    )
}
