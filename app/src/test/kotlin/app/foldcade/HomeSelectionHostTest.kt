package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class HomeSelectionHostTest {
    private val a = androidHomeId("notes")
    private val b = androidHomeId("browser")
    private val c = androidHomeId("camera")
    private val group = HomeFolder("home.user.1", "Quests", userMade = true, slots = listOf(a))
    private val initial = HomeBoard(slots = listOf(HOME_ALL, a, b, null, group.id), folders = mapOf(group.id to group))
    private val apps = listOf(LaunchableApp("notes", "Notes", false), LaunchableApp("browser", "Browser", false))
    private val store = SessionStore(MemoryPrefs()).also { it.saveHomeBoard(encodeHome(initial)) }
    private fun shell() = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())).also { it.setInstalledApps(apps, emptySet()) }
    private fun board() = decodeHome(store.homeBoardRaw())

    @Test fun selectionChecksOnlyGamesAndBEndsWithoutLaunchingOrEditing() {
        val shell = shell()
        start(shell, a)
        assertEquals(HomeKeys.Selecting, shell.homeKeys())
        assertEquals(setOf(a), shell.homeSelection()?.checked)
        val before = store.homeBoardRaw()
        focus(shell, 0)
        assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        assertEquals(1, shell.homeSelection()?.refusals)
        assertEquals(setOf(a), shell.homeSelection()?.checked)
        focus(shell, board().slots.indexOf(group.id))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertNull(board().openFolderId)
        focus(shell, board().slots.size + 3)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(3, shell.homeSelection()?.refusals)
        focus(shell, board().slots.indexOf(b))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(setOf(a, b), shell.homeSelection()?.checked)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom, repeatCount = 2)
        assertEquals(setOf(a, b), shell.homeSelection()?.checked)
        assertNull(shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom))
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertNull(shell.homeSelection())
        assertEquals(before, store.homeBoardRaw())
    }

    @Test fun bulkMenuKeepsItsIdsAcrossFocusAndDefersCatalogMergesUntilDone() {
        val shell = shell()
        start(shell, b)
        focus(shell, board().slots.indexOf(a))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertEquals(listOf(a, b), shell.model.tileActions?.bulk)
        choose(shell, Row.AddToCollection)
        val mixed = rowControl(Row.Collection(group.id, group.name), shell.model) as RowControl.Check
        assertTrue(mixed.some)
        shell.setInstalledApps(apps + LaunchableApp("camera", "Camera", false), emptySet())
        assertFalse(shell.homeSlots().contains(c))
        choose(shell, Row.Collection(group.id, group.name))
        assertNull(shell.homeSelection())
        assertNull(shell.model.panel)
        assertEquals(listOf(a, b), board().folders.getValue(group.id).slots)
        assertTrue(board().folders.values.any { c in it.slots })
    }

    @Test fun oneUndoRestoresBothHiddenAppsAndKeepsAnArrivalAfterTheBatch() {
        val shell = shell()
        start(shell, a)
        focus(shell, board().slots.indexOf(b))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        shell.setInstalledApps(apps + LaunchableApp("camera", "Camera", false), emptySet())
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        // A batch has no spot to take its games off, so no Remove; Hide is its own row, without X.
        val rows = panelRows(shell.model.panel!!, shell.model)
        assertFalse(Row.RemoveTile in rows)
        assertNull(chordIndex(rows, Meaning.ActivateBottom))
        choose(shell, Row.Hide)
        assertNull(shell.homeSelection())
        assertTrue(board().hidden.containsAll(listOf(a, b)))
        assertEquals(Copy.hidden, shell.model.undoVerb)
        assertTrue(board().folders.values.any { c in it.slots })
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertEquals(Row.Undo, panelRows(shell.model.panel!!, shell.model).first())
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertTrue(board().hidden.isEmpty())
        assertTrue(a in board().slots && b in board().slots)
        assertEquals(listOf(a), board().folders.getValue(group.id).slots)
        assertTrue(board().folders.values.any { c in it.slots })
        assertNull(shell.model.undoVerb)
    }

    @Test fun zeroChecksHaveNoActionsAndChangingTheViewClearsSelection() {
        val shell = shell()
        start(shell, a)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertTrue(shell.homeSelection()!!.checked.isEmpty())
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertNull(shell.model.panel)
        shell.showHomeGrid(HomeGrid.Hidden)
        assertNull(shell.homeSelection())
    }

    @Test fun aNewCollectionUsesViewOrderAndOpensControllerNaming() {
        val shell = shell()
        start(shell, b)
        focus(shell, board().slots.indexOf(a))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        // New collection is Add to's last row, for a batch as for one game.
        choose(shell, Row.AddToCollection)
        choose(shell, Row.NewCollection)
        assertNull(shell.homeSelection())
        assertEquals(HomeKeys.Typing, shell.homeKeys())
        val made = board().folders.values.single { it.collection && it.id != group.id }
        assertEquals(listOf(a, b), made.slots)
        assertEquals(made.id, board().slots[1])
        assertTrue(a in board().slots && b in board().slots)
    }

    @Test fun removingAllMembersHasOneUndoAndAFollowingEditInvalidatesIt() {
        val shell = shell()
        repeat(2) {
            start(shell, a)
            focus(shell, board().slots.indexOf(b))
            shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
            shell.onMeaning(Meaning.Options, HostScreen.Bottom)
            choose(shell, Row.AddToCollection)
            choose(shell, Row.Collection(group.id, group.name))
        }
        assertTrue(board().folders.getValue(group.id).slots.all { it == null })
        assertEquals(Copy.removed, shell.model.undoVerb)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.Undo)
        assertEquals(listOf(a, b), board().folders.getValue(group.id).slots)
        start(shell, a)
        focus(shell, board().slots.indexOf(b))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.AddToCollection)
        choose(shell, Row.Collection(group.id, group.name))
        assertEquals(Copy.removed, shell.model.undoVerb)
        start(shell, a)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.AddToCollection)
        choose(shell, Row.Collection(group.id, group.name))
        assertNull(shell.model.undoVerb)
    }

    @Test fun selectDoesNotRetargetWhenTheMenusGameLeavesItsSlot() {
        val shell = shell()
        focus(shell, board().slots.indexOf(a))
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertTrue(Row.SelectTile in panelRows(shell.model.panel!!, shell.model))
        shell.setInstalledApps(listOf(apps[1], LaunchableApp("camera", "Camera", false)), emptySet())
        assertEquals(a, shell.model.tileActions?.game)
        assertTrue(shell.model.tileActions?.kept == true)
        assertFalse(Row.SelectTile in panelRows(shell.model.panel!!, shell.model))
    }

    private fun start(shell: ShellController, id: String) {
        focus(shell, board().slots.indexOf(id))
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.SelectTile)
    }
    private fun focus(shell: ShellController, target: Int) {
        val columns = GridStrip.column(target) - GridStrip.column(shell.model.focus.cellIndex)
        repeat(kotlin.math.abs(columns)) { shell.onMeaning(if (columns > 0) Meaning.MoveRight else Meaning.MoveLeft, HostScreen.Bottom) }
        val rows = GridStrip.row(target) - GridStrip.row(shell.model.focus.cellIndex)
        repeat(kotlin.math.abs(rows)) { shell.onMeaning(if (rows > 0) Meaning.MoveDown else Meaning.MoveUp, HostScreen.Bottom) }
        assertEquals(target, shell.model.focus.cellIndex)
    }
    private fun choose(shell: ShellController, row: Row) {
        val target = panelRows(shell.model.panel!!, shell.model).indexOf(row)
        assertTrue("Missing $row", target >= 0)
        walkPanelTo(shell, target, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
    }
}
