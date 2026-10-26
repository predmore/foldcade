package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A folder's Sort: Custom shows its arrangement, the others an order, and none of them rearranges it. */
class FolderSortTest {
    private val platform = HomePlatform("nintendo-3ds", "Nintendo 3DS")
    private val threeDs = platformFolderId(platform.id)
    private val zelda = rom("zelda", "Zelda", last = 300L, played = 10L)
    private val kirby = rom("kirby", "kirby", last = 0L, played = 50L)
    private val mario = rom("mario", "Mario", last = 900L, played = 50L)
    private val ace = rom("ace", "Ace", last = 0L, played = 0L)
    private val catalog = listOf(zelda, kirby, mario, ace)

    /** The 3DS folder arranged with gaps, open, as QA found it: no order a player can read. */
    private val arranged: List<String?> = listOf(zelda.id, null, kirby.id, mario.id, null, ace.id)
    private val board = mergeHome(
        HomeBoard(
            slots = listOf(HOME_ALL, threeDs),
            folders = mapOf(threeDs to HomeFolder(threeDs, "Nintendo 3DS", platformId = platform.id, slots = arranged)),
            openFolderId = threeDs,
        ),
        catalog,
        listOf(platform),
    )

    private fun sorted(sort: FolderSort): HomeBoard = setFolderSort(board, threeDs, sort, catalog)

    @Test fun customShowsTheArrangementGapsAndAll() {
        assertEquals(arranged, visibleSlots(board))
        assertTrue(board.ranks.isEmpty())
    }

    @Test fun recentPutsTheLastPlayedFirstThenTheUnplayedAToZ() {
        assertEquals(listOf(mario.id, zelda.id, ace.id, kirby.id), visibleSlots(sorted(FolderSort.Recent)))
    }

    @Test fun aToZComparesTitlesWithoutCase() {
        assertEquals(listOf(ace.id, kirby.id, mario.id, zelda.id), visibleSlots(sorted(FolderSort.Alphabetical)))
    }

    @Test fun mostPlayedPutsTheLongestPlayedFirstAndTiesAToZ() {
        assertEquals(listOf(kirby.id, mario.id, zelda.id, ace.id), visibleSlots(sorted(FolderSort.MostPlayed)))
    }

    @Test fun aSortedFolderHasNoGapsAndKeepsItsArrangementForCustom() {
        for (sort in FolderSort.entries - FolderSort.Custom) {
            val shown = sorted(sort)
            assertTrue(visibleSlots(shown).none { it == null })
            assertEquals(arranged, shown.folders.getValue(threeDs).slots)
            assertFalse(canTidy(shown))
            assertSame(shown, tidy(shown))
            val back = setFolderSort(shown, threeDs, FolderSort.Custom, catalog)
            assertEquals(arranged, visibleSlots(back))
            assertEquals(arranged, back.folders.getValue(threeDs).slots)
        }
    }

    @Test fun nothingInASortedFolderLiftsWhileCustomStillDoes() {
        val shown = sorted(FolderSort.Recent)
        assertNull(pickUp(shown, 0).hold)
        assertFalse(pickUp(shown, 0).editing)
        assertEquals(2, pickUp(board, 2).hold?.index)
        // A tile already in the hand keeps the sort from changing under it.
        val held = pickUp(board, 2)
        assertSame(held, setFolderSort(held, threeDs, FolderSort.Recent, catalog))
    }

    @Test fun recentlyPlayedOrdersItselfAndTakesNoSort() {
        val recent = HomeFolder(HOME_RECENT, Copy.recentlyPlayed, slots = listOf(mario.id, zelda.id))
        val withRecent = board.copy(folders = board.folders + (HOME_RECENT to recent))
        assertSame(withRecent, setFolderSort(withRecent, HOME_RECENT, FolderSort.Alphabetical, catalog))
        assertFalse(recent.copy(sort = FolderSort.Alphabetical).sorted)
    }

    @Test fun stepsWrapBothWays() {
        assertEquals(FolderSort.Recent, FolderSort.Custom.stepped(1))
        assertEquals(FolderSort.MostPlayed, FolderSort.Custom.stepped(-1))
        assertEquals(FolderSort.Custom, FolderSort.MostPlayed.stepped(1))
    }

    @Test fun aMergeReadsNewPlayIntoTheOrder() {
        val shown = sorted(FolderSort.Recent)
        val now = catalog.map { if (it.id == ace.id) it.copy(lastPlayedMillis = 5_000L) else it }
        val played = mergeHome(shown, now, listOf(platform))
        assertEquals(listOf(ace.id, mario.id, zelda.id, kirby.id), visibleSlots(played))
        // A game whose library has not reported keeps its rank, and one never ranked waits at the end.
        val loading = mergeHome(played, now.filter { it.id != mario.id }, listOf(platform), unsettled = { it == mario.id })
        assertEquals(visibleSlots(played), visibleSlots(loading))
        assertEquals(listOf("b", "a"), sortedGames(listOf("b", "a"), FolderSort.Alphabetical, emptyMap()))
    }

    @Test fun anIndexInASortedFolderNamesTheGameShownThere() {
        val collection = HomeFolder("home.user.1", "Quests", userMade = true, slots = listOf(zelda.id, null, ace.id))
        var shown = mergeHome(board.copy(slots = board.slots + collection.id, folders = board.folders + (collection.id to collection),
            openFolderId = collection.id), catalog, listOf(platform))
        shown = setFolderSort(shown, collection.id, FolderSort.Alphabetical, catalog)
        assertEquals(listOf(ace.id, zelda.id), visibleSlots(shown))
        assertEquals(HomeLanding.Inside(threeDs, 1, 3), homeLanding(sorted(FolderSort.Alphabetical), zelda.id))
        val removed = removeFromHome(shown, 0)
        assertEquals(listOf(zelda.id, null, null), removed.folders.getValue(collection.id).slots)
    }

    @Test fun aFolderSortRoundTripsAndAnOlderSaveIsCustom() {
        val shown = sorted(FolderSort.MostPlayed)
        val saved = encodeHome(shown)
        val restored = decodeHome(saved)
        assertEquals(FolderSort.MostPlayed, restored.folders.getValue(threeDs).sort)
        assertEquals(arranged, restored.folders.getValue(threeDs).slots)
        // An older release's board has no sort rows: every folder shows as arranged.
        val older = saved.lines().filter { !it.startsWith("sort") }.joinToString("\n")
        assertEquals(FolderSort.Custom, decodeHome(older).folders.getValue(threeDs).sort)
        assertEquals(arranged, decodeHome(older).folders.getValue(threeDs).slots)
        assertFalse(older.contains("\nsort"))
        // A sort this release does not know, or one on Recently played, is not read.
        val unknown = "$older\nsort\t${Base64Url.of(threeDs)}\t${Base64Url.of("Shuffle")}\n" +
            "sort\t${Base64Url.of(HOME_RECENT)}\t${Base64Url.of(FolderSort.Alphabetical.name)}\n"
        assertEquals(FolderSort.Custom, decodeHome(unknown).folders.getValue(threeDs).sort)
        assertEquals(FolderSort.Custom, decodeHome(unknown).folders.getValue(HOME_RECENT).sort)
    }

    @Test fun theMenuEndsWithSortWhichStepsTheFolderLive() {
        val choice = FolderSortChoice(threeDs, FolderSort.Custom)
        val tile = TileActions(move = true, select = true, collections = emptyList(), hide = true, sort = choice)
        val rows = actionRows(tile)
        assertEquals(listOf(Row.MoveTile, Row.AddToCollection, Row.SelectTile, Row.Hide, Row.SortFolder), rows)
        assertNull(chords(rows).last())
        val panel = SidePanel(Side.Left, HostScreen.Bottom, PanelLevel.Actions, rows.lastIndex, GridFocus())
        val open = PickerModel(1, 3, panel = panel, tileActions = tile)
        assertEquals(RowText(Copy.sort, Copy.sortCustom), rowText(Row.SortFolder, open))
        assertEquals(RowControl.Choice(Copy.sortCustom), rowControl(Row.SortFolder, open))
        assertEquals(Effect.SortFolder(threeDs, 1), reduce(open, Meaning.MoveRight).second)
        assertEquals(Effect.SortFolder(threeDs, -1), reduce(open, Meaning.MoveLeft).second)
        assertEquals(Effect.SortFolder(threeDs, 1), reduce(open, Meaning.Activate).second)
        // The menu stays open on the row.
        assertEquals(panel, reduce(open, Meaning.MoveRight).first.panel)
        assertEquals(panel, reduce(open, Meaning.Activate).first.panel)
        assertEquals(listOf(Row.Hide), actionRows(TileActions(hide = true)))
    }

    @Test fun sortingReadsTheMenuAgainSoMoveGoesAndComesBack() {
        val panel = SidePanel(Side.Left, HostScreen.Bottom, PanelLevel.Actions, 0, GridFocus())
        val custom = TileActions(move = true, sort = FolderSortChoice(threeDs, FolderSort.Custom))
        val closed = PickerModel(1, 3, tileActions = custom)
        val opened = retainMenuRows(closed, closed.copy(panel = panel.copy(index = 1)))
        assertEquals(listOf(Row.MoveTile, Row.SortFolder), panelRows(opened.panel!!, opened))
        val recent = retainMenuRows(opened, opened.copy(tileActions = TileActions(sort = FolderSortChoice(threeDs, FolderSort.Recent))))
        assertEquals(listOf(Row.SortFolder), panelRows(recent.panel!!, recent))
        assertEquals(0, recent.panel!!.index)
        assertEquals(RowText(Copy.sort, Copy.sortRecent), rowText(Row.SortFolder, recent))
        val back = retainMenuRows(recent, recent.copy(tileActions = custom))
        assertEquals(listOf(Row.MoveTile, Row.SortFolder), panelRows(back.panel!!, back))
        assertEquals(Row.SortFolder, panelRows(back.panel!!, back)[back.panel!!.index])
    }

    @Test fun theSortChoicesReadAsAGamerSaysThem() {
        assertEquals(listOf("Custom", "Recent", "A–Z", "Most played"), FolderSort.entries.map(::folderSortLabel))
    }

    private fun rom(key: String, title: String, last: Long, played: Long) =
        HomeItem(homeGameId("romm", key), title, HomeKind.Rom, platformId = platform.id, lastPlayedMillis = last, playMillis = played)

    private object Base64Url {
        fun of(text: String): String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())
    }

    @Test fun insideAFolderEditHomeSortsItAndYDoesNot() {
        val inFolder = TileActions(move = true, collections = emptyList(), boardSort = FolderSortChoice(threeDs, FolderSort.Custom))
        assertFalse(Row.SortFolder in actionRows(inFolder))
        val model = PickerModel(1, 3, tileActions = inFolder,
            panel = SidePanel(Side.Left, HostScreen.Top, PanelLevel.EditHome, 0, GridFocus()))
        assertEquals(Row.SortFolder, editHomeRows(model).first())
        assertEquals(RowText(Copy.sort, Copy.sortCustom), rowText(Row.SortFolder, model))
        assertEquals(Effect.SortFolder(threeDs, 1), reduce(model, Meaning.MoveRight).second)
        // A folder tile's own Sort stays in its Y menu.
        val tile = TileActions(move = true, sort = FolderSortChoice(threeDs, FolderSort.Custom))
        assertEquals(Row.SortFolder, actionRows(tile).last())
    }
}
