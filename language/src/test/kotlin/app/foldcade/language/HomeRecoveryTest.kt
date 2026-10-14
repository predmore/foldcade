package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class HomeRecoveryTest {
    private val game = HomeItem("lib:local:a", "Alpha", HomeKind.Rom, "gba")
    private val other = HomeItem("lib:local:b", "Beta", HomeKind.Rom, "gba")
    private val platforms = listOf(HomePlatform("gba", "Game Boy Advance"))
    private val folder = platformFolderId("gba")

    @Test fun hiddenCombinesGamesAppsDefaultHiddenEmulatorsAndRetiredFoldersOnce() {
        val app = LaunchableApp("notes", "Notes", false)
        val emulator = LaunchableApp("emu", "Emulator", false)
        val board = HomeBoard(hidden = setOf(game.id, androidHomeId("notes")), retired = setOf(HOME_MOONLIGHT))
        val rows = hiddenItems(board, listOf(game, HomeItem(androidHomeId("notes"), "Notes", HomeKind.AndroidApp)),
            listOf(app, emulator), AppShelfState().hide("notes"), setOf("emu"))
        assertEquals(listOf("Alpha", "Emulator", "Moonlight", "Notes"), rows.map { it.title })
    }

    @Test fun showRestoresFreeSlotsPinsAndEveryCollectionAfterRestart() {
        var before = mergeHome(HomeBoard(), listOf(game, other), platforms)
        before = pinToHome(before, game.id)
        before = newCollection(before, "home.user.1", "First", member = game.id)
        before = newCollection(before, "home.user.2", "Second", member = game.id)
        val hidden = mergeHome(hideGame(before, game.id), listOf(game, other), platforms)
        assertEquals(hidden, hideGame(hidden, game.id))
        val loaded = decodeHome(encodeHome(hidden))
        assertEquals(hidden.hiddenSlots, loaded.hiddenSlots)
        val shown = showHidden(loaded, game.id, listOf(game, other), platforms)
        assertEquals(before.slots, shown.slots)
        assertEquals(before.folders, shown.folders)
        assertFalse(game.id in shown.hidden)
        assertFalse(game.id in shown.hiddenSlots)
    }

    @Test fun occupiedFolderSlotAppendsWhilePinUsesFirstFreeRootSlot() {
        var before = mergeHome(HomeBoard(), listOf(game, other), platforms)
        before = pinToHome(before, game.id)
        val pin = before.slots.indexOf(game.id)
        var hidden = hideGame(before, game.id)
        val group = hidden.folders.getValue(folder)
        hidden = hidden.copy(slots = hidden.slots.mapIndexed { i, v -> if (i == pin) "taken" else v } + null,
            folders = hidden.folders + (folder to group.copy(slots = listOf("taken", other.id))))
        val shown = showHidden(hidden, game.id, listOf(game, other), platforms)
        assertEquals(listOf("taken", other.id, game.id), shown.folders.getValue(folder).slots)
        assertEquals("taken", shown.slots[pin])
        assertEquals(game.id, shown.slots.last())
    }

    @Test fun retiredFolderShowsAtItsOldSlotOrFirstFreeAndKeepsItsMembers() {
        val before = mergeHome(HomeBoard(), listOf(game, other), platforms)
        val removed = removeFolder(before, folder)
        val saved = decodeHome(encodeHome(removed))
        assertEquals(before.slots, showHidden(saved, folder, listOf(game, other), platforms).slots)
        val occupied = saved.copy(slots = listOf(HOME_ALL, "taken", null))
        val shown = showHidden(occupied, folder, listOf(game, other), platforms)
        assertEquals(listOf(HOME_ALL, "taken", folder), shown.slots)
        assertEquals(listOf(game.id, other.id), shown.folders.getValue(folder).slots)
        assertFalse(folder in shown.retired)
    }

    @Test fun oldHiddenAndRetiredRecordsStillShowWithoutSavedPositions() {
        val old = decodeHome("v1\nslots\thome.all\nhidden\tlib:local:a\nretired\thome.moonlight\n")
        assertTrue(onHome(showHidden(old, game.id, listOf(game), platforms), game.id))
        assertTrue(HOME_MOONLIGHT in showHidden(old, HOME_MOONLIGHT, listOf(game), platforms).slots)
    }

    @Test fun hidingTheLastGameKeepsFolderMetadataForItsReturn() {
        val before = mergeHome(HomeBoard(), listOf(game), platforms)
        val named = renameFolder(before, folder, "Handheld")
        val hidden = mergeHome(hideGame(named, game.id), listOf(game), platforms)
        assertFalse(folder in hidden.slots)
        val shown = showHidden(decodeHome(encodeHome(hidden)), game.id, listOf(game), platforms)
        assertEquals(named.slots, shown.slots)
        assertEquals(named.folders, shown.folders)
    }

    @Test fun undoExpiresAtEightSecondsAndAnyEditInvalidatesIt() {
        val before = HomeBoard(slots = listOf(HOME_ALL, null, game.id))
        val shelves = AppShelfState().pin("notes", true)
        val snapshot = HomeUndo(before, shelves, Copy.hidden, 100L)
        assertTrue(snapshot.available(8099L))
        assertFalse(snapshot.available(8100L))
        assertFalse(snapshot.available(99L))
        assertEquals(before, snapshot.board)
        assertEquals(shelves, snapshot.shelves)
        assertEquals(snapshot, snapshot.afterEdit(before, before))
        assertNull(snapshot.afterEdit(before, hideGame(before, game.id)))
    }

    @Test fun holdUsesElapsedTimeAndAnEarlyReleaseNeverCompletes() {
        val hold = DeleteHold(Row.RemoveDownload, PromptKey.FaceA, 100L, game.id)
        assertEquals(0f, hold.progress(99L), 0f)
        assertEquals(0.5f, hold.progress(500L), 0f)
        assertFalse(hold.release(899L))
        assertTrue(hold.complete(900L))
        assertEquals(1f, hold.progress(999L), 0f)
    }

    @Test fun undoNeverTakesMoveOrShowChordsAndDeleteOnlyTakesAnUnusedX() {
        val rows = actionRows(TileActions(undo = true, move = true, remove = RemoveVerb.Hide, deleteShortcut = true), null)
        assertEquals(Row.Undo, rows.first())
        assertNull(chords(rows).first())
        assertEquals(rows.indexOf(Row.MoveTile), chordIndex(rows, Meaning.Options))
        assertEquals(rows.indexOf(Row.RemoveTile), chordIndex(rows, Meaning.ActivateBottom))
        assertEquals(1, chordIndex(actionRows(TileActions(undo = true, show = true), null), Meaning.Options))
        assertEquals(0, chordIndex(listOf(Row.DeleteShortcut), Meaning.ActivateBottom))
        assertNull(chordIndex(listOf(Row.MoveApp), Meaning.Options))
    }
}
