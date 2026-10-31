package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private val platforms = listOf(
        HomePlatform("n64", "Nintendo 64", aliases = setOf("nintendo-64")),
        HomePlatform("nds", "Nintendo DS"),
        HomePlatform("3ds", "Nintendo 3DS"),
    )

    private val kart64 = HomeItem("rom:kart64", "Mario Kart 64", HomeKind.Rom, platformId = "nintendo-64")
    private val kartDs = HomeItem("rom:kartds", "Mario Kart DS", HomeKind.Rom, platformId = "nds")
    private val kart7 = HomeItem("rom:kart7", "Mario Kart 7", HomeKind.Rom, platformId = "3ds")
    private val zelda = HomeItem("rom:oot", "The Legend of Zelda: Ocarina of Time", HomeKind.Rom, platformId = "n64")
    private val asphalt = HomeItem("android:asphalt", "Asphalt 9", HomeKind.AndroidGame)
    private val stream = HomeItem("moonlight:pc:1", "Forza Horizon", HomeKind.Moonlight)
    private val notes = HomeItem("android:notes", "Notes", HomeKind.AndroidApp)
    private val setup = HomeItem("gamenative.setup", "Set up GameNative", HomeKind.GameNative, placeholder = true)
    private val catalog = listOf(kart64, kartDs, kart7, zelda, asphalt, stream, notes, setup)

    private fun board(vararg placed: String, hidden: Set<String> = emptySet()) =
        HomeBoard(slots = listOf(HOME_ALL) + placed, hidden = hidden)

    private fun games(
        home: HomeBoard = board(*catalog.map { it.id }.toTypedArray()),
        played: Map<String, Long> = emptyMap(),
        time: Map<String, Long> = emptyMap(),
    ) = libraryGames(catalog, home, platforms, lastPlayed = { played[it] }, playTime = { time[it] })

    @Test
    fun everyGameOnEverySystemIsListedAndAppsAndSetupTilesAreNot() {
        val listed = games()
        assertEquals(
            setOf(kart64.id, kartDs.id, kart7.id, zelda.id, asphalt.id, stream.id),
            listed.map { it.id }.toSet(),
        )
        // An alias reads as its platform, so both N64 games are one system.
        assertEquals(listOf("n64", "n64"), listed.filter { it.item.platformId in setOf("n64", "nintendo-64") }.map { it.systemId })
        assertEquals("Nintendo 64", listed.first { it.id == kart64.id }.systemName)
        // A game with no platform is filed under its library's folder, as Home files it.
        assertEquals(HOME_ANDROID_GAMES to "Android Games", listed.first { it.id == asphalt.id }.let { it.systemId to it.systemName })
        assertEquals(HOME_MOONLIGHT, listed.first { it.id == stream.id }.systemId)
    }

    @Test
    fun aGameListedTwiceIsOneRow() {
        val twice = libraryGames(listOf(kart64, kart64.copy(title = "Mario Kart 64 (copy)")), board(), platforms)
        assertEquals(listOf(kart64.id), twice.map { it.id })
    }

    @Test
    fun allRunsAToZAndTiesKeepAnOrder() {
        val same = HomeItem("rom:kart64-eu", "Mario Kart 64", HomeKind.Rom, platformId = "n64")
        val listed = libraryGames(catalog + same, board(), platforms)
        val all = libraryRows(listed, libraryTabs(listed).first())
        assertEquals(
            listOf("Asphalt 9", "Forza Horizon", "Mario Kart 64", "Mario Kart 64", "Mario Kart 7", "Mario Kart DS", "The Legend of Zelda: Ocarina of Time"),
            all.map { it.item.title },
        )
        assertEquals(listOf(kart64.id, same.id), all.filter { it.item.title == "Mario Kart 64" }.map { it.id })
    }

    @Test
    fun tabsAreAllThenWaysToLookThenEachSystemAToZ() {
        val home = board(kart64.id, kartDs.id, kart7.id, asphalt.id, stream.id)
        val tabs = libraryTabs(games(home, played = mapOf(kart7.id to 20L), time = mapOf(kart7.id to 600_000L)))
        assertEquals(
            listOf(
                LibraryTabKind.All to LibraryCopy.all,
                LibraryTabKind.Recent to LibraryCopy.recent,
                LibraryTabKind.MostPlayed to LibraryCopy.mostPlayed,
                LibraryTabKind.NotOnHome to LibraryCopy.notOnHome,
                // A to Z by the short names the tabs show.
                LibraryTabKind.System to "3DS",
                LibraryTabKind.System to "Android",
                LibraryTabKind.System to "DS",
                LibraryTabKind.System to "Moonlight",
                LibraryTabKind.System to "N64",
            ),
            tabs.map { it.kind to it.label },
        )
    }

    @Test
    fun aTabThatWouldListNothingOrRepeatAllIsLeftOut() {
        // Nothing played, everything on Home: All and the systems only.
        val quiet = libraryTabs(games())
        assertFalse(quiet.any { it.kind == LibraryTabKind.Recent || it.kind == LibraryTabKind.MostPlayed || it.kind == LibraryTabKind.NotOnHome })
        // Nothing on Home at all: Not on Home would be All again.
        assertFalse(libraryTabs(games(board())).any { it.kind == LibraryTabKind.NotOnHome })
        // One system is All again too.
        val one = libraryGames(listOf(kart64, zelda), board(), platforms)
        assertEquals(listOf(LibraryTabKind.All), libraryTabs(one).map { it.kind })
        assertEquals(listOf(LibraryTab(LibraryTabKind.All, LibraryCopy.all)), libraryTabs(emptyList()))
    }

    @Test
    fun recentAndMostPlayedListPlayedGamesLatestOrLongestFirst() {
        val listed = games(
            played = mapOf(kartDs.id to 10L, kart7.id to 30L, zelda.id to 20L),
            time = mapOf(kartDs.id to 9_000L, kart7.id to 1_000L, zelda.id to 9_000L),
        )
        val tabs = libraryTabs(listed)
        val recent = libraryRows(listed, tabs.first { it.kind == LibraryTabKind.Recent })
        assertEquals(listOf(kart7.id, zelda.id, kartDs.id), recent.map { it.id })
        val most = libraryRows(listed, tabs.first { it.kind == LibraryTabKind.MostPlayed })
        // Two of the same time go A to Z.
        assertEquals(listOf(kartDs.id, zelda.id, kart7.id), most.map { it.id })
    }

    @Test
    fun playTimeIsOnlyReadForGamesPlayedAtAll() {
        val asked = mutableListOf<String>()
        libraryGames(catalog, board(), platforms, lastPlayed = { if (it == kart7.id) 5L else null }, playTime = { asked += it; 1L })
        assertEquals(listOf(kart7.id), asked)
    }

    @Test
    fun notOnHomeListsHiddenGamesAndGamesWithNoPlaceLeft() {
        val home = board(kart64.id, kartDs.id, asphalt.id, stream.id, kart7.id, hidden = setOf(kart7.id))
        val listed = games(home)
        val tab = libraryTabs(listed).first { it.kind == LibraryTabKind.NotOnHome }
        assertEquals(listOf(kart7.id, zelda.id), libraryRows(listed, tab).map { it.id })
        // A game in a folder on Home is on Home.
        val inFolder = HomeBoard(
            slots = listOf(HOME_ALL, "folder.n64"),
            folders = mapOf("folder.n64" to HomeFolder("folder.n64", "Nintendo 64", platformId = "n64", slots = listOf(kart64.id, zelda.id))),
        )
        assertEquals(setOf(kart64.id, zelda.id), games(inFolder).filter { it.onHome }.map { it.id }.toSet())
    }

    @Test
    fun systemTabsUseShortNamesAndKeepTheirOwnOtherwise() {
        assertEquals("GBA", systemTabLabel("game-boy-advance", "Game Boy Advance"))
        assertEquals("3DS", systemTabLabel("3DS", "Nintendo 3DS"))
        assertEquals("Moonlight", systemTabLabel(HOME_MOONLIGHT, "Moonlight"))
        assertEquals("Atari Lynx", systemTabLabel("atari-lynx", "Atari Lynx"))
    }

    @Test
    fun aSystemTabListsThatSystemAToZ() {
        val listed = games()
        val n64 = libraryTabs(listed).first { it.systemId == "n64" }
        assertEquals(listOf(kart64.id, zelda.id), libraryRows(listed, n64).map { it.id })
    }

    @Test
    fun theOpenTabIsFoundByWhatItShowsAndSteppingGoesRoundTheEnds() {
        val tabs = libraryTabs(games())
        val ds = tabs.indexOfFirst { it.systemId == "nds" }
        assertEquals(ds, tabs.indexOfTab(LibraryTab(LibraryTabKind.System, "renamed", "nds")))
        assertEquals(-1, tabs.indexOfTab(LibraryTab(LibraryTabKind.Recent, LibraryCopy.recent)))
        assertEquals(-1, tabs.indexOfTab(null))
        assertEquals(1, steppedTab(tabs, 0, 1))
        assertEquals(tabs.lastIndex, steppedTab(tabs, 0, -1))
        assertEquals(0, steppedTab(tabs, tabs.lastIndex, 1))
        assertEquals(0, steppedTab(emptyList(), 3, 1))
    }

    @Test
    fun searchFromAllGamesStartsOnGamesAndFromASystemOnThatSystem() {
        val entries = listOf(
            FindEntry(kart64.id, kart64.title, FindKind.Game, platformId = "n64", platformName = "Nintendo 64"),
            FindEntry(kartDs.id, kartDs.title, FindKind.Game, platformId = "nds", platformName = "Nintendo DS"),
            FindEntry(asphalt.id, asphalt.title, FindKind.Game),
            FindEntry(notes.id, notes.title, FindKind.App),
        )
        val filters = findFilters(entries)
        val games = filters.indexOfFirst { it.kind == FindFilterKind.Games }
        assertEquals(games, libraryFindFilter(LibraryTab(LibraryTabKind.All, LibraryCopy.all), filters))
        assertEquals(games, libraryFindFilter(LibraryTab(LibraryTabKind.Recent, LibraryCopy.recent), filters))
        val n64 = libraryFindFilter(LibraryTab(LibraryTabKind.System, "Nintendo 64", "n64"), filters)
        assertEquals("n64", filters[n64].platformId)
        // Find has no chip for a library folder, so its tab searches all games.
        assertEquals(games, libraryFindFilter(LibraryTab(LibraryTabKind.System, "Android Games", HOME_ANDROID_GAMES), filters))
        assertEquals(0, libraryFindFilter(null, listOf(FindFilter(FindFilterKind.All, FindCopy.all))))
    }

    @Test
    fun marioKartIsFoundOnEverySystemFromAFewLetters() {
        val entries = games().map { FindEntry(it.id, it.item.title, FindKind.Game, platformId = it.systemId, platformName = it.systemName) }
        val filters = findFilters(entries)
        val start = filters[libraryFindFilter(LibraryTab(LibraryTabKind.All, LibraryCopy.all), filters)]
        for (query in listOf("mario kart", "mk", "kart", "mraio kart")) {
            val found = findResults(entries, query, start).map { it.id }.toSet()
            assertTrue("$query found $found", found.containsAll(setOf(kart64.id, kartDs.id, kart7.id)))
        }
    }

    @Test
    fun addingFromAllGamesPinsTheGameAndBringsAHiddenOneBack() {
        val home = HomeBoard(slots = listOf(HOME_ALL), hidden = setOf(kart7.id))
        val listed = games(home)
        assertFalse(listed.first { it.id == kart7.id }.onHome)
        // Add to › Home pins it on the root, and a hidden game is shown again to be pinned.
        val added = toggleCollection(home, HOME_ROOT, kart7.id, kart7, platforms)
        assertTrue(pinned(added, kart7.id))
        assertFalse(kart7.id in added.hidden)
        assertTrue(games(added).first { it.id == kart7.id }.onHome)
        // Its Not on Home tab no longer lists it; the next game left there does.
        val tab = libraryTabs(games(added)).first { it.kind == LibraryTabKind.NotOnHome }
        assertFalse(libraryRows(games(added), tab).any { it.id == kart7.id })
        // Add to › Home again unpins it, leaving it where it was filed.
        assertFalse(pinned(toggleCollection(added, HOME_ROOT, kart7.id, kart7, platforms), kart7.id))
    }

    @Test
    fun addToHomeFilesAGameBackInItsSystemsFolder() {
        val folder = HomeFolder(platformFolderId("n64"), "Nintendo 64", platformId = "n64", slots = listOf(kart64.id))
        val home = HomeBoard(slots = listOf(HOME_ALL, folder.id), folders = mapOf(folder.id to folder), hidden = setOf(zelda.id))
        val back = addItemToHome(home, zelda, platforms)
        assertTrue(zelda.id in back.folders.getValue(folder.id).slots)
        assertFalse(zelda.id in back.hidden)
        assertTrue(games(back).first { it.id == zelda.id }.onHome)
    }

    @Test
    fun aBigLibraryBuildsAndSortsQuickly() {
        val big = (0 until 20_000).map { n ->
            HomeItem("rom:$n", "Game ${(n * 7919) % 20_000}", HomeKind.Rom, platformId = listOf("n64", "nds", "3ds", "gba")[n % 4], lastPlayedMillis = if (n % 50 == 0) n.toLong() else 0L)
        }
        val home = HomeBoard(slots = listOf(HOME_ALL) + big.take(500).map { it.id })
        // Warm up, then time one full build: every game, its tabs, and two tabs' rows.
        repeat(3) { libraryRows(libraryGames(big, home, platforms), LibraryTab(LibraryTabKind.All, LibraryCopy.all)) }
        val started = System.nanoTime()
        val listed = libraryGames(big, home, platforms)
        val tabs = libraryTabs(listed)
        libraryRows(listed, tabs.first())
        libraryRows(listed, tabs.first { it.kind == LibraryTabKind.Recent })
        val millis = (System.nanoTime() - started) / 1_000_000
        assertEquals(20_000, listed.size)
        assertTrue("A 20,000-game build took $millis ms", millis < 1_000)
    }
}
