package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Foldcade's folders on Home and off it: Remove, Folders' switch, browsing one off Home, and new platforms. */
class FoldersOnHomeTest {
    private val platforms = listOf(
        HomePlatform("nintendo-3ds", "Nintendo 3DS", setOf("3ds")),
        HomePlatform("nintendo-switch", "Nintendo Switch", setOf("switch")),
        HomePlatform("playstation-2", "PlayStation 2", setOf("ps2")),
    )
    private val threeDs = platformFolderId("nintendo-3ds")
    private val switch = platformFolderId("nintendo-switch")
    private val ps2 = platformFolderId("playstation-2")
    private val zelda = rom("lib:local:zelda", "3ds")
    private val mario = rom("lib:local:mario", "3ds")
    private val kart = rom("lib:local:kart", "switch")
    private val okami = rom("lib:local:okami", "ps2")
    private val chat = HomeItem(androidHomeId("chat"), "Chat", HomeKind.AndroidApp)
    private val catalog = listOf(zelda, mario, kart, okami, chat)

    @Test
    fun foldersListsEveryFoldcadeFolderAToZWithItsCountAndWhetherItIsOnHome() {
        var board = mergeHome(HomeBoard(), catalog, platforms)
        board = newCollection(board, "home.user.1", "Couch", member = kart.id)
        board = setRecentOnHome(board, true, catalog, platforms)
        board = removeFolder(board, ps2)
        val listed = foldcadeFolders(board, catalog, platforms)
        // Collections and Recently played are not Foldcade's folders: they are not listed.
        assertEquals(listOf("Android Apps", "Nintendo 3DS", "Nintendo Switch", "PlayStation 2"), listed.map { it.folder.name })
        assertEquals(listOf(1, 2, 1, 1), listed.map { it.count })
        assertEquals(listOf(true, true, true, false), listed.map { it.onHome })
        assertEquals(listOf("1 app", "2 games"), listed.take(2).map { folderCountLine(it.count, apps = it.folder.holdsApps) })
    }

    @Test
    fun removeTakesAFolderOffHomeWithItsGamesAndItIsStillOffAfterARestart() {
        val board = mergeHome(HomeBoard(), catalog, platforms)
        val at = board.slots.indexOf(threeDs)
        assertEquals(RemoveVerb.Remove, removeVerb(board, at))
        val off = removeFromHome(board, at)
        assertFalse(threeDs in off.slots)
        assertNull(off.slots[at])
        // Never deleted: the folder keeps every game, which All still lists.
        assertEquals(listOf(zelda.id, mario.id), off.folders.getValue(threeDs).slots)
        assertFalse(onHome(off, zelda.id))
        assertTrue(allItems(catalog).any { it.id == zelda.id })
        val reloaded = mergeHome(decodeHome(encodeHome(off)), catalog, platforms)
        assertFalse(threeDs in reloaded.slots)
        assertEquals(off.folders.getValue(threeDs).slots, reloaded.folders.getValue(threeDs).slots)
        assertFalse(foldcadeFolders(reloaded, catalog, platforms).first { it.folder.id == threeDs }.onHome)
    }

    @Test
    fun theSwitchTakesAFolderOffAndPutsItBackInItsOldSlot() {
        val board = mergeHome(HomeBoard(), catalog, platforms)
        val at = board.slots.indexOf(switch)
        val off = setFolderOnHome(board, switch, on = false, catalog, platforms)
        assertFalse(groupOnHome(off, switch))
        assertTrue(switch in off.retired)
        assertEquals(off, setFolderOnHome(off, switch, on = false, catalog, platforms))
        val on = mergeHome(setFolderOnHome(off, switch, on = true, catalog, platforms), catalog, platforms)
        assertEquals(at, on.slots.indexOf(switch))
        assertFalse(switch in on.retired)
        assertEquals(listOf(kart.id), on.folders.getValue(switch).slots)
        assertEquals(board.slots, on.slots)
        assertEquals(on, setFolderOnHome(on, switch, on = true, catalog, platforms))
    }

    @Test
    fun aFolderSwitchedOnFindsTheFirstFreeSlotWhenItsOldOneIsTaken() {
        val board = mergeHome(HomeBoard(), catalog, platforms)
        val at = board.slots.indexOf(switch)
        val off = setFolderOnHome(board, switch, on = false, catalog, platforms)
        val taken = newCollection(off, "home.user.1", "Couch", at = at)
        val on = setFolderOnHome(taken, switch, on = true, catalog, platforms)
        assertEquals("home.user.1", on.slots[at])
        assertTrue(groupOnHome(on, switch))
    }

    @Test
    fun aFolderOffHomeStaysOffAndStillTakesItsNewGames() {
        var board = mergeHome(HomeBoard(), catalog, platforms)
        board = setFolderOnHome(board, threeDs, on = false, catalog, platforms)
        val pokemon = rom("lib:local:pokemon", "3ds")
        val more = catalog + pokemon
        board = mergeHome(board, more, platforms)
        assertFalse(groupOnHome(board, threeDs))
        assertEquals(listOf(zelda.id, mario.id, pokemon.id), board.folders.getValue(threeDs).slots)
        // Switched back on, it is whole.
        val on = setFolderOnHome(board, threeDs, on = true, more, platforms)
        assertEquals(3, folderCount(on, threeDs))
        assertTrue(onHome(on, pokemon.id))
    }

    @Test
    fun aPinFromAFolderOffHomeStaysOnHome() {
        var board = mergeHome(HomeBoard(), catalog, platforms)
        board = pinToHome(board, zelda.id)
        board = mergeHome(setFolderOnHome(board, threeDs, on = false, catalog, platforms), catalog, platforms)
        assertTrue(pinned(board, zelda.id))
        assertTrue(onHome(board, zelda.id))
        assertFalse(onHome(board, mario.id))
    }

    @Test
    fun aNewPlatformJoinsHomeWhileNewGamesOnHomeIsOn() {
        val board = mergeHome(HomeBoard(), listOf(zelda), platforms)
        val grown = mergeHome(board, listOf(zelda, kart), platforms)
        assertTrue(groupOnHome(grown, switch))
        assertTrue(foldcadeFolders(grown, listOf(zelda, kart), platforms).first { it.folder.id == switch }.onHome)
    }

    @Test
    fun aNewPlatformWaitsInFoldersWhileNewGamesOnHomeIsOff() {
        val board = setAddNewToHome(mergeHome(HomeBoard(), listOf(zelda), platforms), false)
        val more = listOf(zelda, kart, rom("lib:local:splatoon", "switch"))
        val grown = mergeHome(board, more, platforms)
        assertFalse(groupOnHome(grown, switch))
        assertNull(grown.folders[switch])
        // Folders lists it off Home, with the games it would hold.
        val waiting = foldcadeFolders(grown, more, platforms).first { it.folder.id == switch }
        assertFalse(waiting.onHome)
        assertEquals(2, waiting.count)
        assertEquals("Nintendo Switch", waiting.folder.name)
        // Its switch makes it, with every one of its games, on Home.
        val on = mergeHome(setFolderOnHome(grown, switch, on = true, more, platforms), more, platforms)
        assertTrue(groupOnHome(on, switch))
        assertEquals(listOf(kart.id, "lib:local:splatoon"), on.folders.getValue(switch).slots)
        assertTrue(onHome(on, kart.id))
        assertEquals(on.folders, mergeHome(decodeHome(encodeHome(on)), more, platforms).folders)
    }

    @Test
    fun aWaitingFolderOpensOffHomeToBrowseAndStaysOff() {
        val board = mergeHome(setAddNewToHome(mergeHome(HomeBoard(), listOf(zelda), platforms), false), listOf(zelda, kart), platforms)
        val opened = openAnyFolder(board, switch, listOf(zelda, kart), platforms)
        assertEquals(switch, opened.openFolderId)
        assertEquals(listOf(kart.id), visibleSlots(opened))
        assertFalse(groupOnHome(opened, switch))
        // Made off Home, so neither a merge nor turning New games on Home back on brings its tile.
        val later = mergeHome(setAddNewToHome(closeFolder(opened), true), listOf(zelda, kart, rom("lib:local:arms", "switch")), platforms)
        assertFalse(groupOnHome(later, switch))
        assertEquals(listOf(kart.id, "lib:local:arms"), later.folders.getValue(switch).slots)
    }

    @Test
    fun aFolderOffHomeOpensWhereItIs() {
        val board = setFolderOnHome(mergeHome(HomeBoard(), catalog, platforms), ps2, on = false, catalog, platforms)
        val opened = openAnyFolder(openFolders(board), ps2, catalog, platforms)
        assertEquals(ps2, opened.openFolderId)
        assertFalse(opened.foldersOpen)
        assertEquals(listOf(okami.id), visibleSlots(opened))
        assertEquals(board.slots, opened.slots)
    }

    @Test
    fun hiddenNoLongerListsFoldersButStillListsHiddenGames() {
        val board = hideGame(setFolderOnHome(mergeHome(HomeBoard(), catalog, platforms), ps2, on = false, catalog, platforms), kart.id)
        assertEquals(listOf(kart.id), hiddenItems(board, catalog, platforms).map { it.id })
        // A hidden game is left out of its folder's count.
        assertNull(foldcadeFolders(board, catalog, platforms).firstOrNull { it.folder.id == switch })
    }

    @Test
    fun foldersIsAViewThatNothingOnTheBoardMovesIn() {
        val board = openFolders(mergeHome(HomeBoard(), catalog, platforms))
        assertTrue(board.foldersOpen)
        assertFalse(board.atRoot)
        assertEquals(board, pickUp(board, 1))
        assertNull(removeVerb(board, 1))
        assertFalse(canTidy(board))
        assertEquals(board.slots, removeFromHome(board, 1).slots)
        // It is a place on screen, not a choice: it is not saved.
        assertFalse(decodeHome(encodeHome(board)).foldersOpen)
        assertFalse(openAll(board).foldersOpen)
        assertFalse(closeFolders(board).foldersOpen)
        assertTrue(closeFolders(board).atRoot)
    }

    @Test
    fun recentlyPlayedAndCollectionsHaveNoFolderSwitch() {
        val board = newCollection(mergeHome(HomeBoard(), catalog, platforms), "home.user.1", "Couch", member = kart.id)
        assertEquals(board, setFolderOnHome(board, "home.user.1", on = false, catalog, platforms))
        assertEquals(board, setFolderOnHome(board, HOME_RECENT, on = true, catalog, platforms))
        assertEquals(board, setFolderOnHome(board, platformFolderId("nes"), on = true, catalog, platforms))
    }

    @Test
    fun editHomeOpensFoldersAndTheSwitchFlipsInPlace() {
        val edit = PickerModel(count = 8, rowsPerPage = 2).let { reduce(it, Meaning.LeftPanel).first }.let { at(it, Row.EditHome) }
            .let { reduce(it, Meaning.Activate).first }
        assertEquals(listOf(Row.RecentOnHome, Row.AddNewGames, Row.Folders, Row.Hidden), panelRows(edit.panel!!, edit))
        val (closed, open) = reduce(at(edit, Row.Folders), Meaning.Activate)
        assertEquals(Effect.OpenFolders, open)
        assertNull(closed.panel)

        val tile = TileActions(folderOnHome = FolderSwitch(switch, on = true))
        assertEquals(listOf(Row.FolderOnHome), actionRows(tile))
        val menu = reduce(PickerModel(count = 8, rowsPerPage = 2, tileActions = tile), Meaning.Options).first
        assertEquals(RowText(Copy.onHome, Copy.on), rowText(Row.FolderOnHome, menu))
        assertEquals(RowControl.Toggle(true), rowControl(Row.FolderOnHome, menu))
        val (flipped, effect) = reduce(at(menu, Row.FolderOnHome), Meaning.Activate)
        assertEquals(Effect.FolderOnHome(switch, on = false), effect)
        // The menu stays open on the switch, which reads off at once.
        assertEquals(PanelLevel.Actions, flipped.panel?.level)
        assertEquals(RowControl.Toggle(false), rowControl(Row.FolderOnHome, flipped))
        // A held A flips it once: its repeats leave it as the press did.
        val held = reduce(at(flipped, Row.FolderOnHome), Meaning.Activate, repeatCount = 1)
        assertNull(held.second)
        assertEquals(RowControl.Toggle(false), rowControl(Row.FolderOnHome, held.first))
    }

    private fun at(model: PickerModel, row: Row): PickerModel {
        val panel = model.panel!!
        return model.copy(panel = panel.copy(index = panelRows(panel, model).indexOf(row)))
    }

    private fun rom(id: String, platform: String): HomeItem = HomeItem(id, id.substringAfterLast(':'), HomeKind.Rom, platformId = platform)
}
