package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class HomeRecoveryHostTest {
    private var time = 100L
    private val app = LaunchableApp("notes", "Notes", false)
    private val other = LaunchableApp("browser", "Browser", false)
    private val prefs = MemoryPrefs()
    private val store = SessionStore(prefs)
    private fun shell(deleted: MutableList<String> = mutableListOf()) = ShellController(store,
        PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()), nowMillis = { time }, onMoonlightRemoved = { deleted += it })

    @After fun clean() { Shelf.moonlightGames = emptyList() }

    @Test fun hidingTheLastGameThenUndoRestoresTheSystemFolderInItsRootSlot() {
        val game = LaunchableApp("solo", "Solo", true)
        val id = androidHomeId(game.packageName)
        val folder = HomeFolder(HOME_ANDROID_GAMES, "Games", bucket = HomeKind.AndroidGame, slots = listOf(id))
        store.saveHomeBoard(encodeHome(HomeBoard(slots = listOf(HOME_ALL, null, folder.id), folders = mapOf(folder.id to folder))))
        val shell = shell()
        shell.setInstalledApps(listOf(game), emptySet())
        focus(shell, 2)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        val before = decodeHome(store.homeBoardRaw())
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.Hide)
        assertFalse(folder.id in decodeHome(store.homeBoardRaw()).slots)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.Undo)
        val restored = decodeHome(store.homeBoardRaw())
        assertEquals(before.slots, restored.slots)
        assertEquals(before.folders, restored.folders)
    }

    @Test fun hideFromAFolderShowRestoresAndUndoRestoresTheExactBoard() {
        val shell = shell()
        shell.setInstalledApps(listOf(app, other), emptySet())
        shell.openHomeFolder(HOME_ANDROID_APPS)
        focusTitle(shell, "Notes")
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.AddToCollection)
        choose(shell, Row.Collection(HOME_ROOT, Copy.home))
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        val before = decodeHome(store.homeBoardRaw())
        assertTrue(androidHomeId("notes") in before.slots)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.Hide)
        assertTrue(androidHomeId("notes") in decodeHome(store.homeBoardRaw()).hidden)
        assertEquals(Copy.hidden, shell.model.undoVerb)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertEquals(Row.Undo, panelRows(shell.model.panel!!, shell.model).first())
        assertEquals(0, shell.model.panel!!.index)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(before, decodeHome(store.homeBoardRaw()))
        assertNull(shell.model.undoVerb)
        assertEquals(1, shell.model.rewind)

        focusTitle(shell, "Notes")
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.Hide)
        shell.showHomeGrid(HomeGrid.Hidden)
        assertEquals("Notes", shell.focusedGame()?.title)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertEquals(0, shell.model.count)
        assertEquals(before.slots, decodeHome(store.homeBoardRaw()).slots)
        assertEquals(before.folders, decodeHome(store.homeBoardRaw()).folders)
    }

    @Test fun undoStaysFocusedPastItsDeadlineUntilTheMenuCloses() {
        val shell = shell()
        shell.setInstalledApps(listOf(app, other), emptySet())
        shell.openHomeFolder(HOME_ANDROID_APPS)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.Hide)
        time += 7_000L
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        val rows = panelRows(shell.model.panel!!, shell.model)
        assertEquals(Row.Undo, rows.first())
        time += 30_000L
        shell.tickRecovery()
        assertEquals(rows, panelRows(shell.model.panel!!, shell.model))
        assertEquals(0, shell.model.panel!!.index)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertTrue(decodeHome(store.homeBoardRaw()).hidden.isEmpty())
        assertNull(shell.model.undoVerb)
    }

    @Test fun closingTheMenuResumesTheRemainingUndoTime() {
        val shell = shell()
        shell.setInstalledApps(listOf(app, other), emptySet())
        shell.openHomeFolder(HOME_ANDROID_APPS)
        focusTitle(shell, "Notes")
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.Hide)
        time += 7_000L
        focusTitle(shell, "Browser")
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        chooseFocus(shell, Row.AddToCollection)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        time += 30_000L
        shell.tickRecovery()
        assertNotNull(shell.model.undoVerb)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertEquals(Row.Undo, panelRows(shell.model.panel!!, shell.model).first())
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertNull(shell.model.panel)
        time += 999L
        shell.tickRecovery()
        assertNotNull(shell.model.undoVerb)
        time += 1L
        shell.tickRecovery()
        assertNull(shell.model.undoVerb)
    }

    private fun chooseFocus(shell: ShellController, row: Row) {
        val target = panelRows(shell.model.panel!!, shell.model).indexOf(row)
        assertTrue(target >= 0)
        walkPanelTo(shell, target, HostScreen.Bottom)
    }

    @Test fun undoExpiresAndMoveInvalidatesItWithoutSavingUndo() {
        val shell = shell()
        shell.setInstalledApps(listOf(app, other), emptySet())
        shell.openHomeFolder(HOME_ANDROID_APPS)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.Hide)
        time += UNDO_MILLIS
        shell.tickRecovery()
        assertNull(shell.model.undoVerb)
        shell.showHomeGrid(HomeGrid.Hidden)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.ShowHidden)
        shell.goHome()
        focus(shell, decodeHome(store.homeBoardRaw()).slots.indexOf(HOME_ANDROID_APPS))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        focusTitle(shell, "Notes")
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.Hide)
        assertNotNull(shell.model.undoVerb)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertNull(shell.model.undoVerb)
        val restarted = shell()
        restarted.setInstalledApps(listOf(app, other), emptySet())
        assertNull(restarted.model.undoVerb)
    }

    @Test fun aShortReleaseCancelsButAFullHoldDeletesExactlyOneShortcutOnce() {
        val key = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6|7"
        Shelf.moonlightGames = listOf(ShelfGame(key, "Hades", "Moonlight", platformId = "moonlight", mark = "moonlight", libraryId = "moonlight", remoteKey = key))
        val deleted = mutableListOf<String>()
        val shell = shell(deleted)
        shell.settleMoonlight()
        shell.noteShelfChanged()
        focus(shell, List(shell.model.count) { shell.homeFace(it)?.title }.indexOf("Moonlight"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.DeleteShortcut)
        assertNotNull(shell.model.deleteHold)
        assertTrue(deleted.isEmpty())
        shell.setPromptHeld(PromptKey.FaceA, true)
        time += 799L
        shell.tickRecovery()
        shell.setPromptHeld(PromptKey.FaceA, false)
        time += 1L
        assertNull(shell.tickRecovery())
        assertTrue(deleted.isEmpty())
        assertEquals(1, shell.model.deleteShake)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        time += DELETE_HOLD_MILLIS
        shell.tickRecovery()
        assertEquals(listOf(key), deleted)
        assertFalse(key in decodeHome(store.homeBoardRaw()).hidden)
        assertTrue(key in decodeHome(store.homeBoardRaw()).offHome)
        shell.tickRecovery()
        assertEquals(listOf(key), deleted)
    }

    @Test fun reinstallingAHiddenAppCannotEndACarryBeforeCancelOrDrop() {
        for (finish in listOf(Meaning.Back, Meaning.Activate)) {
            val session = SessionStore(MemoryPrefs())
            val shell = ShellController(session, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()), nowMillis = { time })
            shell.setInstalledApps(listOf(app, other), emptySet())
            shell.openHomeFolder(HOME_ANDROID_APPS)
            focusTitle(shell, "Notes")
            shell.onMeaning(Meaning.Options, HostScreen.Bottom)
            choose(shell, Row.Hide)
            assertTrue(androidHomeId("notes") in decodeHome(session.homeBoardRaw()).hidden)
            // Uninstalled, it is no longer hidden: there is nothing left to hide.
            shell.setInstalledApps(listOf(other), emptySet())
            assertFalse(androidHomeId("notes") in decodeHome(session.homeBoardRaw()).hidden)
            shell.goHome()
            val before = decodeHome(session.homeBoardRaw())
            val origin = before.slots.indexOf(HOME_ANDROID_APPS)
            focus(shell, origin)
            shell.pickUpHome(origin)
            shell.dragHome(Meaning.MoveRight)
            val carried = shell.homeAnimToken()
            val destination = shell.model.focus.cellIndex
            assertNotEquals(origin, destination)
            shell.setInstalledApps(listOf(app, other), emptySet())
            assertEquals(carried, shell.homeAnimToken())
            assertEquals(destination, shell.homeLiftedIndex())
            assertEquals(before.slots, decodeHome(session.homeBoardRaw()).slots)
            shell.onMeaning(finish, HostScreen.Bottom)
            val after = decodeHome(session.homeBoardRaw())
            assertEquals(if (finish == Meaning.Back) origin else destination, after.slots.indexOf(HOME_ANDROID_APPS))
            // Reinstalled, it is a new app, filed once the carry ends.
            assertFalse(androidHomeId("notes") in after.hidden)
            assertTrue(androidHomeId("notes") in after.folders.getValue(HOME_ANDROID_APPS).slots)
        }
    }

    @Test fun tidyFromTheNewMoveMenusCanBeUndone() {
        val folder = HomeFolder(HOME_ANDROID_APPS, "Android Apps", bucket = HomeKind.AndroidApp,
            slots = listOf(androidHomeId("notes"), null, androidHomeId("browser")))
        store.saveHomeBoard(encodeHome(HomeBoard(slots = listOf(HOME_ALL, null, folder.id),
            folders = mapOf(folder.id to folder))))
        val shell = shell()
        shell.setInstalledApps(listOf(app, other), emptySet())
        val saved = store.homeBoardRaw()
        focus(shell, 1)
        // Tidy is Edit home's: L1, Edit home, Tidy.
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        walkPanelTo(shell, leftRows().indexOf(Row.EditHome), HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        choose(shell, Row.Tidy)
        assertNotEquals(saved, store.homeBoardRaw())
        assertNotNull(shell.model.undoVerb)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(saved, store.homeBoardRaw())
    }

    @Test fun folderAndCollectionRemovalUndoRestoresPositionsAndMemberships() {
        val folder = HomeFolder(HOME_ANDROID_APPS, "Android Apps", bucket = HomeKind.AndroidApp,
            slots = listOf(null, androidHomeId("notes")))
        val collection = HomeFolder("home.user.1", "Tools", userMade = true, slots = listOf(androidHomeId("notes"), null))
        val board = HomeBoard(slots = listOf(HOME_ALL, null, folder.id, collection.id),
            folders = mapOf(folder.id to folder, collection.id to collection))
        store.saveHomeBoard(encodeHome(board))
        val shell = shell()
        shell.setInstalledApps(listOf(app), emptySet())
        for (target in listOf(folder.id, collection.id)) {
            shell.goHome()
            val saved = store.homeBoardRaw()
            focus(shell, decodeHome(saved).slots.indexOf(target))
            shell.onMeaning(Meaning.Options, HostScreen.Bottom)
            choose(shell, Row.RemoveTile)
            assertEquals(Copy.removed, shell.model.undoVerb)
            shell.onMeaning(Meaning.Options, HostScreen.Bottom)
            shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
            assertEquals(saved, store.homeBoardRaw())
        }
    }

    @Test fun anAppInItsFolderMovesBetweenAndroidFoldersAndHides() {
        val shell = shell()
        shell.setInstalledApps(listOf(app), emptySet())
        shell.openHomeFolder(HOME_ANDROID_APPS)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        val rows = panelRows(shell.model.panel!!, shell.model)
        assertTrue(Row.AddToCollection in rows && Row.MoveApp in rows && Row.Hide in rows)
        // A folder holds every app of its kind, so there is nothing to Remove, and Y Y is Move.
        assertFalse(Row.RemoveTile in rows)
        assertEquals(rows.indexOf(Row.MoveTile), chordIndex(rows, Meaning.Options))
        choose(shell, Row.MoveApp)
        val board = decodeHome(store.homeBoardRaw())
        assertTrue(androidHomeId("notes") in board.folders.getValue(HOME_ANDROID_GAMES).slots)
        // Its last app gone, Android Apps leaves Home.
        assertFalse(androidHomeId("notes") in board.folders[HOME_ANDROID_APPS]?.slots.orEmpty())
    }

    @Test fun aHoldCanceledWhenTheSurfaceHidesCannotDeleteLater() {
        val key = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6|7"
        Shelf.moonlightGames = listOf(ShelfGame(key, "Hades", "Moonlight", platformId = "moonlight", mark = "moonlight", libraryId = "moonlight", remoteKey = key))
        val deleted = mutableListOf<String>()
        val shell = shell(deleted)
        shell.settleMoonlight()
        shell.noteShelfChanged()
        focus(shell, List(shell.model.count) { shell.homeFace(it)?.title }.indexOf("Moonlight"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.DeleteShortcut)
        shell.cancelDeleteHold()
        time += 1000L
        shell.tickRecovery()
        assertTrue(deleted.isEmpty())
    }

    @Test fun keyboardConfirmationReleaseCancelsBeforeTheDeadlineAndCompletesAtIt() {
        val key = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6|7"
        Shelf.moonlightGames = listOf(ShelfGame(key, "Hades", "Moonlight", platformId = "moonlight", mark = "moonlight", libraryId = "moonlight", remoteKey = key))
        val deleted = mutableListOf<String>()
        val shell = shell(deleted)
        shell.settleMoonlight()
        shell.noteShelfChanged()
        focus(shell, List(shell.model.count) { shell.homeFace(it)?.title }.indexOf("Moonlight"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.DeleteShortcut)
        time += 799L
        shell.releaseMeaning(Meaning.Activate)
        time += 1L
        shell.tickRecovery()
        assertTrue(deleted.isEmpty())
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        time += DELETE_HOLD_MILLIS
        shell.releaseMeaning(Meaning.Activate)
        assertEquals(listOf(key), deleted)
    }

    private fun focus(shell: ShellController, target: Int) {
        val columns = GridStrip.column(target) - GridStrip.column(shell.model.focus.cellIndex)
        repeat(kotlin.math.abs(columns)) { shell.onMeaning(if (columns > 0) Meaning.MoveRight else Meaning.MoveLeft, HostScreen.Bottom) }
        val rows = GridStrip.row(target) - GridStrip.row(shell.model.focus.cellIndex)
        repeat(kotlin.math.abs(rows)) { shell.onMeaning(if (rows > 0) Meaning.MoveDown else Meaning.MoveUp, HostScreen.Bottom) }
        assertEquals(target, shell.model.focus.cellIndex)
    }

    private fun focusTitle(shell: ShellController, title: String) =
        focus(shell, (0 until shell.model.count).first { shell.homeFace(it)?.title == title })

    private fun choose(shell: ShellController, row: Row) {
        val rows = panelRows(shell.model.panel!!, shell.model)
        val target = rows.indexOf(row)
        assertTrue("Missing $row in $rows", target >= 0)
        walkPanelTo(shell, target, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
    }
}
