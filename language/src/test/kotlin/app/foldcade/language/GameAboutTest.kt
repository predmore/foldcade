package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hero's facts line for a game, and Y › About: its row, its keys and its facts. */
class GameAboutTest {
    private val game = "lib:local-folder:metroid.sfc"
    private val played = "Played 4h · Yesterday"

    @Test
    fun aGamesLineLeadsWithPlatformYearAndGenreThenWhereItIsAndItsPlay() {
        val line = heroGameFacts("Super Metroid", "Super Nintendo", 1994, "Action", listOf("On RomM · 3 MB", null, played)) { true }
        assertEquals("Super Nintendo · 1994 · Action · On RomM · 3 MB · $played", line)
    }

    @Test
    fun aLineTooLongDropsTheGenreThenTheYearButKeepsThePlayLine() {
        val rest = listOf("On this device", Copy.usesBothScreens, played)
        val full = heroGameFacts("Zelda", "Nintendo 3DS", 2011, "Adventure", rest) { true }
        val noGenre = "Nintendo 3DS · 2011 · On this device · ${Copy.usesBothScreens} · $played"
        val noYear = "Nintendo 3DS · On this device · ${Copy.usesBothScreens} · $played"
        assertEquals("Nintendo 3DS · 2011 · Adventure · On this device · ${Copy.usesBothScreens} · $played", full)
        assertEquals(noGenre, heroGameFacts("Zelda", "Nintendo 3DS", 2011, "Adventure", rest) { it.length <= full.length - 1 })
        assertEquals(noYear, heroGameFacts("Zelda", "Nintendo 3DS", 2011, "Adventure", rest) { it.length <= noGenre.length - 1 })
        // Past that, nothing more goes: the card ends what is left in "…", play time still in it.
        assertEquals(noYear, heroGameFacts("Zelda", "Nintendo 3DS", 2011, "Adventure", rest) { false })
    }

    @Test
    fun onlyLinesThatDifferAreMeasured() {
        val asked = mutableListOf<String>()
        val line = heroGameFacts("Tetris", "Game Boy", 1989, null, listOf(played)) { asked += it; false }
        assertEquals("Game Boy · $played", line)
        assertEquals(listOf("Game Boy · 1989 · $played", "Game Boy · $played"), asked)
        // A genre that repeats the title or the platform is said once, as heroFacts does.
        assertEquals("Game Boy · 1989", heroGameFacts("Tetris", "Game Boy", 1989, "Tetris", emptyList()) { true })
    }

    @Test
    fun theFactsListWhatIsKnownInOrderAndNameAMakerOnce() {
        assertEquals(
            listOf(
                AboutFact(GameAboutCopy.released, "1994"),
                AboutFact(GameAboutCopy.genres, "Action, Adventure"),
                AboutFact(GameAboutCopy.developer, "Nintendo R&D1"),
                AboutFact(GameAboutCopy.publisher, "Nintendo"),
                AboutFact(GameAboutCopy.players, "1"),
            ),
            aboutFacts(1994, listOf("Action", " Adventure "), "Nintendo R&D1", "Nintendo", "1"),
        )
        assertEquals(
            listOf(AboutFact(GameAboutCopy.genre, "Puzzle"), AboutFact(GameAboutCopy.developer, "Nintendo")),
            aboutFacts(null, listOf("Puzzle", " "), "Nintendo", "nintendo", " "),
        )
        assertEquals(listOf(AboutFact(GameAboutCopy.publisher, "Sega")), aboutFacts(null, emptyList(), " ", "Sega", null))
        assertTrue(aboutFacts(null, emptyList(), null, null, null).isEmpty())
    }

    @Test
    fun onlyAGameALibraryListsHasAnAboutRow() {
        assertTrue(hasAboutPage(HomeItem(game, "Super Metroid", HomeKind.Rom)))
        listOf(HomeKind.AndroidGame, HomeKind.AndroidApp, HomeKind.GameNative, HomeKind.Moonlight, HomeKind.DroidDeck, HomeKind.Emulator, HomeKind.Loose)
            .forEach { assertFalse(it.name, hasAboutPage(HomeItem(game, "x", it))) }
        assertFalse(hasAboutPage(HomeItem(game, "Add games", HomeKind.Rom, placeholder = true)))

        val tile = TileActions(move = true, collections = emptyList(), playTime = 60_000L, about = true, game = game, remove = RemoveVerb.Remove, hide = true)
        assertEquals(listOf(Row.MoveTile, Row.AddToCollection, Row.PlayTime, Row.About, Row.RemoveTile, Row.Hide), actionRows(tile))
        assertFalse(Row.About in actionRows(tile.copy(about = false)))
        assertEquals(RowText(GameAboutCopy.about), rowText(Row.About, PickerModel(count = 1, rowsPerPage = 1, tileActions = tile)))
    }

    @Test
    fun selectOpensAboutUnlessFindsShowOnHomeIsThere() {
        val rows = actionRows(TileActions(move = true, about = true, game = game))
        assertEquals(PromptKey.Select, rowKeys(rows)[rows.indexOf(Row.About)])
        val found = actionRows(TileActions(showOnHome = game, about = true, game = game))
        assertEquals(listOf(Row.ShowOnHome, Row.About), found)
        assertEquals(listOf(PromptKey.Select, null), rowKeys(found))
    }

    @Test
    fun aboutOpensTheGamesPageAndKeepsWhereTheGridWas() {
        val opened = openAbout()
        assertNull(opened.panel)
        assertEquals(game, opened.gameAbout?.game)
        assertEquals(GridFocus(cellIndex = 4, lastColumn = 1), opened.gameAbout?.grid)
        assertEquals(listOf(Copy.back), gameAboutHints(opened).map { it.label })
        assertEquals(listOf(PromptKey.FaceB), gameAboutHints(opened).map { it.key })
    }

    @Test
    fun aboutOpensOnTheTopScreenInEveryLayoutWhereverItsMenuWas() {
        for (layout in ScreenLayout.entries) {
            // Y's menu opens beside its tile, on the grid's screen.
            val menu = yMenu(layout)
            assertEquals(layout.name, layout.gridScreen, menu.panel?.screen)
            // The page is a full page, as Settings and Play activity are: the larger top screen.
            assertEquals(layout.name, HostScreen.Top, openAbout(layout).gameAbout?.screen)
        }
    }

    @Test
    fun aPageAimedAtAScreenFoldcadeDoesNotShowMovesToOneItDoes() {
        // A game holds the top: the page opens on the screen beside it, as Settings does.
        for (layout in listOf(ScreenLayout.ShowcaseTop, ScreenLayout.ShowcaseBottom)) {
            val opened = openAbout(layout)
            val moved = opened.onShownScreens { it == HostScreen.Bottom }
            assertEquals(layout.name, HostScreen.Bottom, moved.gameAbout?.screen)
            assertEquals(opened.gameAbout!!.copy(screen = HostScreen.Bottom), moved.gameAbout)
        }
        // Top only never leaves it on the dark screen.
        val dark = openAbout(ScreenLayout.TopOnly).let { it.copy(gameAbout = it.gameAbout!!.copy(screen = HostScreen.Bottom)) }
        assertEquals(HostScreen.Top, dark.offTheDarkScreen().gameAbout?.screen)
        // Already shown: nothing moves.
        val shown = openAbout(ScreenLayout.TopOnly)
        assertTrue(shown === shown.offTheDarkScreen())
    }

    @Test
    fun aKeyStillHeldFromTheMenuDoesNothingOnThePage() {
        val opened = openAbout()
        Meaning.entries.forEach { meaning ->
            val (next, effect) = reduce(opened, meaning, repeatCount = 1)
            assertEquals(meaning.name, opened, next)
            assertNull(effect)
        }
    }

    @Test
    fun backL1R1AndSelectCloseThePageAndPutTheGridBack() {
        val opened = openAbout()
        listOf(Meaning.Back, Meaning.LeftPanel, Meaning.RightPanel, Meaning.View).forEach { meaning ->
            val (closed, effect) = reduce(opened, meaning)
            assertNull(meaning.name, closed.gameAbout)
            assertNull(closed.panel)
            assertEquals(opened.gameAbout!!.grid, closed.focus)
            assertNull(effect)
        }
        // Nothing else does anything: the page is only read.
        listOf(Meaning.Activate, Meaning.ActivateBottom, Meaning.Options, Meaning.Menu, Meaning.MoveUp, Meaning.MoveLeft).forEach { meaning ->
            val (next, effect) = reduce(opened, meaning)
            assertEquals(meaning.name, opened.gameAbout, next.gameAbout)
            assertNull(next.panel)
            assertNull(effect)
        }
    }

    @Test
    fun setUpSavesReplacesThePage() {
        assertNull(openAbout().openSaveSetup(HostScreen.Top).gameAbout)
    }

    private fun yMenu(layout: ScreenLayout = ScreenLayout.Default): PickerModel {
        val tile = TileActions(move = true, playTime = 0L, about = true, game = game)
        val focus = GridFocus(cellIndex = 4, lastColumn = 1)
        return reduce(PickerModel(count = 9, rowsPerPage = 3, focus = focus, tileActions = tile, layout = layout), Meaning.Options, layout.gridScreen).first
    }

    private fun openAbout(layout: ScreenLayout = ScreenLayout.Default): PickerModel {
        val menu = yMenu(layout)
        val at = menu.copy(panel = menu.panel!!.copy(index = panelRows(menu.panel!!, menu).indexOf(Row.About)))
        val (opened, effect) = reduce(at, Meaning.Activate, layout.menuScreen)
        assertNull(effect)
        return opened
    }
}
