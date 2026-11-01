package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    @Test fun exportedPacksThatDoNotFitAreNamedInTheDoneLine() {
        assertEquals("Backed up.", BackupCopy.saved(0))
        assertEquals("Backed up. 1 theme was too large to include.", BackupCopy.saved(1))
        assertEquals("Backed up. 2 themes were too large to include.", BackupCopy.saved(2))
    }
    private val tree = "content://documents/tree/primary%3AROMs"
    private val local = homeGameId("local-folder", "$tree/document/primary%3AROMs%2Fgame.zip")
    private val server = homeGameId("romm", "42")
    private val missingApp = androidHomeId("missing.app")
    private val hidden = homeGameId("romm", "hidden")
    private val collection = HomeFolder("home.collection.1", "Mine", userMade = true, slots = listOf(local, server, missingApp))
    private val board = HomeBoard(slots = listOf(HOME_ALL, local, server, collection.id),
        folders = mapOf(collection.id to collection), hidden = setOf(hidden), offHome = setOf("lib:romm:off"),
        hiddenSlots = mapOf(hidden to listOf(HiddenSlot(collection.id, 4))))

    @Test fun holdsKeepPlacesCollectionsHidingAndRemovalUntilEachSourceReports() {
        var holds = RestoreHolds.forBoard(board, emptyMap(), listOf(tree))
        assertFalse(holds.awaiting(missingApp))
        holds = RestoreHolds.decode(holds.encode()) // A restart keeps exactly the same pending sources.
        val waiting = mergeHome(decodeHome(encodeHome(board)), emptyList(), emptyList(), holds::awaiting)
        assertEquals(local, waiting.slots[1]); assertEquals(server, waiting.slots[2])
        assertEquals(listOf(local, server, null), waiting.folders.getValue(collection.id).slots)
        assertTrue(hidden in waiting.hidden); assertTrue("lib:romm:off" in waiting.offHome)
        assertEquals(board.hiddenSlots, waiting.hiddenSlots)
        holds = holds.reported(setOf("romm"))
        val one = mergeHome(waiting, emptyList(), emptyList(), holds::awaiting)
        assertEquals(local, one.slots[1]); assertNull(one.slots[2])
        assertEquals(emptySet<String>(), one.hidden); assertEquals(emptySet<String>(), one.offHome)
        assertEquals(listOf(local, null, null), one.folders.getValue(collection.id).slots)
        holds = holds.reported(setOf("folder:$tree"))
        val all = mergeHome(one, emptyList(), emptyList(), holds::awaiting)
        assertFalse(all.slots.contains(local)); assertTrue(all.folders.getValue(collection.id).slots.all { it == null })
    }

    @Test fun oneConnectedFolderOrComputerCannotReleaseAnother() {
        val otherTree = "content://documents/tree/primary%3ACard"
        val other = homeGameId("local-folder", "$otherTree/document/game")
        val firstPc = "12345678-abcd-1234-abcd-123456789abc|1"
        val secondPc = "aaaaaaaa-abcd-1234-abcd-123456789abc|2"
        val all = board.copy(slots = board.slots + listOf(other, firstPc, secondPc))
        val holds = RestoreHolds.forBoard(all, emptyMap(), listOf(tree, otherTree))
            .reported(setOf("folder:$tree", "moonlight:${firstPc.substringBefore('|')}"))
        assertFalse(holds.awaiting(local)); assertTrue(holds.awaiting(other))
        assertFalse(holds.awaiting(firstPc)); assertTrue(holds.awaiting(secondPc))
    }

    @Test fun foldedCopiesWaitUntilEveryRememberedSourceHasReported() {
        val tile = sameGameId(sameKey("genesis", "game.zip")!!)
        val aliases = mapOf(local to tile, server to tile)
        val folded = adoptSameGames(board, aliases)
        val holds = RestoreHolds.forBoard(folded, aliases, listOf(tree)).reported(setOf("romm"))
        assertTrue(holds.awaiting(tile))
        val kept = mergeHome(folded, emptyList(), emptyList(), holds::awaiting)
        assertTrue(tile in kept.slots)
        val done = holds.reported(setOf("folder:$tree"))
        assertFalse(mergeHome(kept, emptyList(), emptyList(), done::awaiting).slots.contains(tile))
    }

    @Test fun previewCountsUniqueGamesCollectionsHiddenAndMissingApps() {
        val summary = backupHomeSummary(board, setOf(local))
        assertEquals(BackupHomeSummary(4, 1, 1, 3, 1), summary)
        assertEquals("Home: 4 games, 1 collection, 1 hidden · Theme: Afterglow · Not here yet: 3 games, 1 app",
            BackupCopy.preview(summary.games, summary.collections, summary.hidden, "Afterglow", summary.missingGames, summary.missingApps))
    }

    @Test fun dialogsOpenSafelyAndHeldButtonsCannotAnswerThem() {
        val simple = backupDialog(HostScreen.Top)
        assertEquals(DialogButton.BackUp, simple.buttons[simple.index])
        assertEquals(DialogButton.NotNow, simple.buttons[simple.safeIndex])
        assertFalse(DialogButton.UndoRestore in simple.buttons)
        assertTrue(DialogButton.UndoRestore in backupDialog(HostScreen.Top, true).buttons)
        val confirm = restoreDialog("Nov 1, 2026", "Home: 4 games", HostScreen.Top)
        assertEquals(DialogButton.NotNow, confirm.buttons[confirm.index])
        for (dialog in listOf(simple, confirm, backupDone(BackupCopy.saved, HostScreen.Top))) {
            val (next, effect) = reduce(PickerModel(count = 0, rowsPerPage = 1, dialog = dialog), Meaning.Activate, HostScreen.Top, repeatCount = 1)
            assertNull(effect); assertEquals(dialog, next.dialog)
        }
        assertEquals(RowControl.Opens, rowControl(Row.Backup, PickerModel(0, 1)))
    }

    @Test fun aFinishedRestoreAlwaysHasANonemptyMessageWithoutRepeatingItsTitle() {
        val complete = backupDone("", HostScreen.Top, restored = true)
        assertEquals(BackupCopy.title, complete.title)
        assertEquals("Restored.", complete.body)
        val remaining = backupDone(BackupCopy.signIn, HostScreen.Top, restored = true)
        assertEquals(BackupCopy.restored, remaining.title)
        assertEquals(BackupCopy.signIn, remaining.body)
    }

    @Test fun systemFitsWithSettledPermissionsButEightPendingRowsExceedTheExistingCard() {
        val normal = PickerModel(0, 1, homeRoleHeld = true, askNoticeAccess = false)
        val rows = settingsRows(SettingsCategory.Screens, normal)
        assertEquals(Row.Backup, rows[rows.indexOf(Row.PlayHistory) - 1])
        assertTrue(rows.size * 52 + (rows.size - 1) * 4 <= 410)
        val pending = settingsRows(SettingsCategory.Screens, normal.copy(homeRoleHeld = false, askNoticeAccess = true))
        assertEquals(8, pending.size)
        // The current Settings card uses fixed 52dp rows, unlike island menus' adaptive MenuFit.
        assertTrue(pending.size * 52 + (pending.size - 1) * 4 > 410)
        assertFalse(menuFit(pending.size, 410, 1f, 4).scrolls)
    }
}
