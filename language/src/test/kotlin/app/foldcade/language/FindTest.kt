package app.foldcade.language

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Find: what a query ranks first, the filters, the list with no query, and where Show on Home lands. */
class FindTest {
    private fun game(id: String, title: String, played: Long = 0L, hidden: Boolean = false, platform: String? = null, name: String? = null) =
        FindEntry(id, title, FindKind.Game, platformId = platform, platformName = name, hidden = hidden, lastPlayedMillis = played)

    private val all = FindFilter(FindFilterKind.All, FindCopy.all)

    private fun titles(entries: List<FindEntry>, query: String, filter: FindFilter = all): List<String> =
        findResults(entries, query, filter).map { it.title }

    @Test
    fun foldingIgnoresCaseAccentsAndPunctuation() {
        assertEquals("pokemon mystery dungeon", foldForFind("Pokémon: Mystery  Dungeon!"))
        assertEquals("assassins creed", foldForFind("Assassin’s Creed"))
        assertEquals("strasse", foldForFind("STRAßE"))
        assertEquals("the legend of zelda ocarina of time", foldForFind("The Legend of Zelda: Ocarina of Time"))
        assertEquals("", foldForFind("  -- "))
    }

    @Test
    fun aTitleThatStartsWithTheQueryIsTheBestMatch() {
        assertEquals(FindMatch(MatchTier.Start, 0), matchTitle("Zelda II", "zel"))
        assertEquals(FindMatch(MatchTier.Start, 0), matchTitle("Pokémon X", "POKE"))
        // Typed without its space, a title still starts with it.
        assertEquals(FindMatch(MatchTier.Start, 0), matchTitle("Mario Kart 7", "mariokart"))
    }

    @Test
    fun wordPrefixesMatchInAnyOrder() {
        assertEquals(FindMatch(MatchTier.Words, 3), matchTitle("The Legend of Zelda", "ze"))
        assertEquals(FindMatch(MatchTier.Words, 1), matchTitle("Mario Kart DS", "kart mar"))
        // Two query words cannot both claim the same title word.
        assertTrue(matchTitle("Mario Kart DS", "ma mar")?.tier != MatchTier.Words)
    }

    @Test
    fun initialsReachATitleByItsWordsFirstLetters() {
        assertEquals(FindMatch(MatchTier.Initials, 0), matchTitle("Ocarina of Time", "oot"))
        assertEquals(FindMatch(MatchTier.Initials, 4), matchTitle("The Legend of Zelda: Ocarina of Time", "oot"))
        assertEquals(FindMatch(MatchTier.Initials, 0), matchTitle("Super Mario Bros.", "smb"))
        // A number keeps all its digits, so Mario Kart 8 is mk8.
        assertEquals(FindMatch(MatchTier.Initials, 0), matchTitle("Mario Kart 8", "mk8"))
        // Spaced out, the letters are word starts instead.
        assertEquals(MatchTier.Words, matchTitle("Ocarina of Time", "o t")?.tier)
    }

    @Test
    fun anythingInsideTheTitleStillMatches() {
        assertEquals(FindMatch(MatchTier.Contains, 5), matchTitle("MarioKart DS", "kart"))
        // One letter alone is too loose to search inside words.
        assertNull(matchTitle("Fez", "z"))
        assertEquals(MatchTier.Words, matchTitle("Fez Zone", "z")?.tier)
    }

    @Test
    fun oneTypoIsForgiven() {
        assertEquals(MatchTier.Typo, matchTitle("The Legend of Zelda", "zleda")?.tier)
        assertEquals(MatchTier.Typo, matchTitle("Mario Kart DS", "mraio kart")?.tier)
        assertEquals(MatchTier.Typo, matchTitle("Metroid Prime", "metriod")?.tier)
        assertEquals(MatchTier.Typo, matchTitle("Kirby", "kirbby")?.tier)
        // Review r2: one typo across several words, in the first, middle or last of them.
        assertEquals(MatchTier.Typo, matchTitle("Super Mario Kart", "mraio kart")?.tier)
        assertEquals(MatchTier.Typo, matchTitle("Super Mario Kart", "supr mario kart")?.tier)
        assertEquals(MatchTier.Typo, matchTitle("Super Mario Kart", "super mraio kart")?.tier)
        assertEquals(MatchTier.Typo, matchTitle("Super Mario Kart", "super mario katr")?.tier)
        assertEquals(MatchTier.Typo, matchTitle("Super Mario Kart", "kart mraio")?.tier)
        assertNull(matchTitle("Super Mario Kart", "supr mraio kart"))
        // Two typos, or one in a very short query, are not a match.
        assertNull(matchTitle("Mario Kart DS", "mraio krat"))
        assertNull(matchTitle("Mario", "mx"))
        assertNull(matchTitle("Metroid", "xyz"))
    }

    @Test
    fun oneOffCountsAChangeAnAddADropOrASwap() {
        assertTrue(oneOff("zleda", "zelda"))
        assertTrue(oneOff("zelda", "zelda"))
        assertTrue(oneOff("zeldx", "zelda"))
        assertTrue(oneOff("kirb", "kirby"))
        assertTrue(oneOff("kirbby", "kirby"))
        assertFalse(oneOff("abcd", "badc"))
        assertFalse(oneOff("mario", "mxrxo"))
        assertFalse(oneOff("ab", "abcd"))
        // Against the start of a longer word, as a prefix is matched.
        assertTrue(oneOff("metriod", "metroid prime", 7))
    }

    @Test
    fun resultsRankByTierThenPlaceThenRecentPlayThenName() {
        val entries = listOf(
            game("blaze", "Blaze Union"),
            game("loz", "The Legend of Zelda"),
            game("z2", "Zelda II"),
            game("zbotw", "Zelda Breath"),
            game("ze", "Zero Escape"),
            game("typo", "Zelad Tales"),
        )
        // Start beats words, words beat a match inside a word.
        assertEquals(listOf("Zelad Tales", "Zelda Breath", "Zelda II", "Zero Escape", "The Legend of Zelda", "Blaze Union"), titles(entries, "ze"))
        // One letter off still finds a title, after every exact match.
        assertEquals(listOf("Zelda Breath", "Zelda II", "The Legend of Zelda", "Zelad Tales"), titles(entries, "zelda"))
        // A game played more recently wins a tie.
        val played = entries.map { if (it.id == "z2") it.copy(lastPlayedMillis = 50L) else it }
        assertEquals("Zelda II", titles(played, "zel").first())
        assertEquals(listOf("Zelda II", "Zelda Breath", "The Legend of Zelda", "Zelad Tales"), titles(played, "zelda"))
    }

    @Test
    fun aHiddenGameRanksAfterAVisibleOneItTies() {
        val entries = listOf(game("a", "Metroid", hidden = true), game("b", "Metroid"))
        assertEquals(listOf("b", "a"), findResults(entries, "metroid", all).map { it.id })
        // Hidden or not, it is still found.
        assertEquals(listOf("a"), findResults(entries.take(1), "met", all).map { it.id })
    }

    @Test
    fun atMostTwentyFourResults() {
        val many = (1..40).map { game("g$it", "Game $it") }
        assertEquals(FIND_LIMIT, findResults(many, "game", all).size)
        assertEquals(FIND_LIMIT, findResults(many, "", all).size)
    }

    @Test
    fun noQueryListsWhatWasPlayedThenEverythingAToZ() {
        val entries = listOf(
            game("c", "Castlevania"),
            game("a", "Advance Wars", played = 10L),
            game("b", "Bomberman", played = 30L),
            game("d", "Dig Dug", hidden = true),
            game("e", "Excitebike"),
            FindEntry("home.user.1", "Couch", FindKind.Group),
        )
        assertEquals(listOf("Bomberman", "Advance Wars", "Castlevania", "Couch", "Excitebike", "Dig Dug"), titles(entries, ""))
        assertEquals(listOf("Bomberman", "Advance Wars"), titles(entries, " ", FindFilter(FindFilterKind.Recent, FindCopy.recent)))
    }

    @Test
    fun filtersListOnlyKindsThatHaveSomethingAndThePlatformsPresent() {
        val entries = listOf(
            game("z", "Zelda", platform = "nintendo-3ds", name = "Nintendo 3DS"),
            game("k", "Kirby", platform = "nintendo-ds", name = "Nintendo DS", played = 5L),
            game("p", "Pong", platform = "atari"),
            FindEntry("android:clock", "Clock", FindKind.App),
        )
        val filters = findFilters(entries)
        assertEquals(listOf(FindCopy.all, FindCopy.games, FindCopy.apps, FindCopy.recent, "atari", "Nintendo 3DS", "Nintendo DS"), filters.map { it.label })
        val ds = filters.first { it.platformId == "nintendo-ds" }
        assertEquals(listOf("Kirby"), titles(entries, "", ds))
        assertEquals(listOf("Clock"), titles(entries, "", filters.first { it.kind == FindFilterKind.Apps }))
        // Hidden and Collections join once something is hidden or a group is listed.
        val more = entries + game("h", "Hidden Game", hidden = true) + FindEntry("home.favorites", "Favorites", FindKind.Group)
        val kinds = findFilters(more).map { it.kind }
        assertTrue(FindFilterKind.Hidden in kinds && FindFilterKind.Collections in kinds)
        assertEquals(listOf("Hidden Game"), titles(more, "", findFilters(more).first { it.kind == FindFilterKind.Hidden }))
        assertEquals(listOf("Favorites"), titles(more, "", findFilters(more).first { it.kind == FindFilterKind.Collections }))
    }

    private val platforms = listOf(HomePlatform("nintendo-3ds", "Nintendo 3DS", setOf("3ds")))
    private val threeDs = platformFolderId("nintendo-3ds")
    private val zelda = HomeItem("lib:local:zelda", "Zelda", HomeKind.Rom, platformId = "3ds")
    private val mario = HomeItem("lib:local:mario", "Mario", HomeKind.Rom, platformId = "3ds")

    private fun board(): HomeBoard = mergeHome(HomeBoard(), listOf(zelda, mario), platforms)

    @Test
    fun showOnHomeLandsOnAPinFirst() {
        val pinned = pinToHome(board(), mario.id)
        assertEquals(HomeLanding.Root(pinned.slots.indexOf(mario.id)), homeLanding(pinned, mario.id))
    }

    @Test
    fun showOnHomeOpensTheFolderNext() {
        val board = board()
        val folderAt = board.slots.indexOf(threeDs)
        assertEquals(HomeLanding.Inside(threeDs, folderAt, 1), homeLanding(board, mario.id))
        // A group itself is its own tile on the root.
        assertEquals(HomeLanding.Root(folderAt), homeLanding(board, threeDs))
    }

    @Test
    fun showOnHomeFallsBackToACollectionThenRecentlyPlayed() {
        var board = newCollection(board(), "home.user.1", "Quests", member = zelda.id)
        board = removeFolder(board, threeDs)
        assertEquals(HomeLanding.Inside("home.user.1", board.slots.indexOf("home.user.1"), 0), homeLanding(board, zelda.id))
        // Mario is only in the folder taken off Home: nowhere to show.
        assertNull(homeLanding(board, mario.id))
        assertNull(homeLanding(board, threeDs))
        val played = mergeHome(board, listOf(zelda, mario.copy(lastPlayedMillis = 5L)), platforms)
        assertEquals(HomeLanding.Inside(HOME_RECENT, played.slots.indexOf(HOME_RECENT), 0), homeLanding(played, mario.id))
    }

    @Test
    fun aHiddenGameHasNowhereToShow() {
        val hidden = hideGame(pinToHome(board(), zelda.id), zelda.id)
        assertNull(homeLanding(hidden, zelda.id))
        assertNull(homeLanding(board(), "lib:local:unknown"))
    }

    @Test
    fun factsSayWhereAResultLives() {
        val board = pinToHome(board(), zelda.id)
        val zeldaEntry = game(zelda.id, "Zelda", platform = "nintendo-3ds", name = "Nintendo 3DS")
        assertEquals(listOf("Nintendo 3DS", FindCopy.pinned), findFacts(board, zeldaEntry))
        assertEquals(listOf(FindCopy.inPlace("Nintendo 3DS")), findFacts(board, zeldaEntry.copy(id = mario.id)))
        val hidden = hideGame(board, mario.id)
        assertEquals(listOf("Nintendo 3DS", FindCopy.hidden), findFacts(hidden, zeldaEntry.copy(id = mario.id, hidden = true)))
        assertEquals(listOf(FindCopy.notOnHome), findFacts(board, game("lib:local:gone", "Gone")))
        assertEquals(listOf(folderCountLine(2)), findFacts(board, FindEntry(threeDs, "Nintendo 3DS", FindKind.Group)))
    }

    @Test
    fun findsKeyboardStepsFiltersWithTheTriggersAndSelectCloses() {
        val keys = HomeKeys.Finding
        assertEquals(Meaning.LetterBackward, meaningOf(KeyEvent.KEYCODE_BUTTON_L2, homeKeys = keys))
        assertEquals(Meaning.LetterForward, meaningOf(KeyEvent.KEYCODE_BUTTON_R2, homeKeys = keys))
        assertEquals(Meaning.View, meaningOf(KeyEvent.KEYCODE_BUTTON_SELECT, homeKeys = keys))
        assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_R2, repeatCount = 1, homeKeys = keys))
        // Everything else types as the naming keyboard does.
        assertEquals(Meaning.Erase, meaningOf(KeyEvent.KEYCODE_BUTTON_X, homeKeys = keys))
        assertEquals(Meaning.Space, meaningOf(KeyEvent.KEYCODE_BUTTON_Y, homeKeys = keys))
        assertEquals(Meaning.Done, meaningOf(KeyEvent.KEYCODE_BUTTON_START, homeKeys = keys))
        assertEquals(Meaning.CaretBack, meaningOf(KeyEvent.KEYCODE_BUTTON_L1, homeKeys = keys))
        assertEquals(Meaning.Shift, meaningOf(KeyEvent.KEYCODE_BUTTON_L2, homeKeys = HomeKeys.Typing))
        assertTrue(keys.guardsHeldKeys && HomeKeys.Typing.guardsHeldKeys)
        assertFalse(HomeKeys.Idle.guardsHeldKeys)
    }

    @Test
    fun findsResultsTakeTheGridsKeysAndKeepAHeldKeyToThemselves() {
        val keys = HomeKeys.FindResults
        assertEquals(Meaning.Activate, meaningOf(KeyEvent.KEYCODE_BUTTON_A, homeKeys = keys))
        assertEquals(Meaning.ActivateBottom, meaningOf(KeyEvent.KEYCODE_BUTTON_X, homeKeys = keys))
        assertEquals(Meaning.Options, meaningOf(KeyEvent.KEYCODE_BUTTON_Y, homeKeys = keys))
        assertEquals(Meaning.View, meaningOf(KeyEvent.KEYCODE_BUTTON_SELECT, homeKeys = keys))
        // Review r1: A held on a folder result opened it, and its repeats then launched the first game.
        val guard = TypingKeyGuard()
        assertFalse(guard.swallows(KeyEvent.KEYCODE_BUTTON_A, 0, keys.guardsHeldKeys))
        assertTrue(guard.swallows(KeyEvent.KEYCODE_BUTTON_A, 1, HomeKeys.Idle.guardsHeldKeys))
        guard.up(KeyEvent.KEYCODE_BUTTON_A)
        assertFalse(guard.swallows(KeyEvent.KEYCODE_BUTTON_A, 0, HomeKeys.Idle.guardsHeldKeys))
    }

    @Test
    fun openingFindCancelsAPendingLaunch() {
        // Review r1: Select opened Find over a launch still waiting, which then went ahead.
        assertTrue(cancelsPendingLaunch(Meaning.View, dialogOpen = false, conflictOpen = false))
        assertFalse(cancelsPendingLaunch(Meaning.View, dialogOpen = true, conflictOpen = false))
        assertFalse(cancelsPendingLaunch(Meaning.View, dialogOpen = false, conflictOpen = true))
    }

    @Test
    fun hintsNameFindsKeysAndSelectJoinsARootRowWithRoom() {
        val faces = FaceMap.standard()
        assertEquals(FindCopy.close, findKeyboardHints(faces, typed = false).last().label)
        assertEquals(FindCopy.clear, findKeyboardHints(faces, typed = true).last().label)
        assertTrue(findKeyboardHints(faces, typed = false).any { it.key == PromptKey.R2 && it.label == FindCopy.filter })
        // On the keys, A types: the row says so, and never names the results' Top or Bottom.
        assertEquals(HintKey(PromptKey.FaceA, FindCopy.type), findKeyboardHints(faces, typed = false).first())
        assertTrue(findKeyboardHints(faces, typed = true).none { it.label == Copy.hintTop || it.label == Copy.hintBottom || it.label == Copy.hintOptions })
        val results = findResultHints(faces, screens = true, options = true).map { it.key }
        assertEquals(listOf(PromptKey.FaceA, PromptKey.FaceX, PromptKey.FaceY, PromptKey.L2, PromptKey.R2, PromptKey.FaceB), results)
        val root = listOf(HintKey(PromptKey.FaceY, Copy.hintOptions))
        assertEquals(HintKey(PromptKey.Select, FindCopy.find), withFindHint(root, atRoot = true).last())
        assertEquals(root, withFindHint(root, atRoot = false))
        val full = List(4) { HintKey(PromptKey.FaceA, "") }
        assertEquals(full, withFindHint(full, atRoot = true))
    }

    @Test
    fun showOnHomeLeadsTheRowsUnderTheTilesAndTakesSelect() {
        val tile = TileActions(showOnHome = "g", collections = emptyList(), hide = true, game = "g")
        val rows = actionRows(tile)
        assertEquals(listOf(Row.AddToCollection, Row.ShowOnHome, Row.Hide), rows)
        assertEquals(0..0, tileBlock(rows))
        assertEquals(1, chordIndex(rows, Meaning.View))
        val model = PickerModel(count = 4, rowsPerPage = 2, tileActions = tile)
        val menu = reduce(model, Meaning.Options).first
        val (closed, effect) = reduce(menu, Meaning.View)
        assertEquals(Effect.ShowOnHome("g"), effect)
        assertNull(closed.panel)
        assertEquals(FindCopy.showOnHome, rowText(Row.ShowOnHome, model).label)
    }
}
