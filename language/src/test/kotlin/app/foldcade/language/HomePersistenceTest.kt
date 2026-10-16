package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePersistenceTest {
    @Test
    fun opaqueIdsAndNamesRoundTripWithoutSplittingRecords() {
        val id = "lib:source:a\tb\nc\rd"
        val folder = HomeFolder("home.user.1", "Favorites\rEvening", userMade = true, namedByUser = true, mark = "psp", slots = listOf(id, null, "-"))
        val board = HomeBoard(
            slots = listOf(HOME_ALL, null, folder.id), folders = mapOf(folder.id to folder),
            hidden = setOf("hidden\titem"), retired = setOf(HOME_ANDROID_APPS), addNewToHome = false,
        )
        assertEquals(board, decodeHome(encodeHome(board)))
    }

    @Test
    fun gamesTakenOffHomeAndTheRecentStartRoundTrip() {
        val board = HomeBoard(offHome = setOf("lib:a\tb", "lib:c"), recentAfter = 1234L)
        val restored = decodeHome(encodeHome(board), nowMillis = 9999L)
        assertEquals(board, restored)
        assertFalse(savedBeforeCollections(encodeHome(board)))
        assertTrue(savedBeforeCollections("v2\nslots\taG9tZS5hbGw\n"))
        assertFalse(savedBeforeCollections(null))
    }

    @Test
    fun v1LayoutsMigrateWithoutLosingNamesIconsHiddenItemsOrEmptySlots() {
        val raw = "v1\naddNew\t0\nhidden\tlib:source:hidden\nretired\thome.android-apps\n" +
            "slots\thome.all\t-\thome.user.1\nfolder\thome.user.1\tFavorites\t\t\t1\t1\tpsp\tlib:source:game\t-\n"
        val restored = decodeHome(raw)
        assertFalse(restored.addNewToHome)
        assertEquals(listOf(HOME_ALL, null, "home.user.1"), restored.slots)
        assertEquals(setOf("lib:source:hidden"), restored.hidden)
        assertEquals(setOf(HOME_ANDROID_APPS), restored.retired)
        assertEquals("Favorites", restored.folders.getValue("home.user.1").name)
        assertEquals("psp", restored.folders.getValue("home.user.1").mark)
        assertEquals(listOf("lib:source:game", null), restored.folders.getValue("home.user.1").slots)
        assertEquals(restored, decodeHome(encodeHome(restored)))
    }

    @Test
    fun anUnconfirmedMoveRestoresItsOriginalPositionAndAConfirmedMoveIsSaved() {
        val board = HomeBoard(slots = listOf(HOME_ALL, "game.a", null))
        val moving = moveHeld(pickUp(board, 1), 2, emptyList(), emptyList())
        assertEquals(board, decodeHome(encodeHome(moving)))
        assertEquals(listOf(HOME_ALL, null, "game.a"), decodeHome(encodeHome(drop(moving, emptyList(), emptyList()))).slots)
        assertNull(decodeHome(encodeHome(moving)).hold)
        assertFalse(decodeHome(encodeHome(moving)).editing)
    }

    @Test
    fun invalidRecordsDoNotDiscardOtherSavedFields() {
        val board = HomeBoard(addNewToHome = false)
        // allSort is a row older releases saved for All's header, which is gone.
        val raw = encodeHome(board) + "folder\t%%%\nunknown\tvalue\nallSort\tU3lzdGVt\n"
        assertEquals(board, decodeHome(raw))
    }

    @Test
    fun aRenamedPlatformFolderSurvivesWhileItsGamesAreStillLoading() {
        val gameId = homeGameId("romm", "game")
        val platform = HomePlatform("nintendo-ds", "Nintendo DS")
        val folder = HomeFolder(
            platformFolderId(platform.id),
            "DS favorites",
            platformId = platform.id,
            namedByUser = true,
            slots = listOf(gameId),
        )
        val board = HomeBoard(slots = listOf(HOME_ALL, folder.id), folders = mapOf(folder.id to folder))
        val loading = mergeHome(decodeHome(encodeHome(board)), emptyList(), listOf(platform), unsettled = { it == gameId })
        assertEquals("DS favorites", loading.folders[folder.id]?.name)
        assertEquals(1, loading.slots.indexOf(folder.id))
        val loaded = mergeHome(loading, listOf(HomeItem(gameId, "Game", HomeKind.Rom, platformId = platform.id)), listOf(platform))
        assertEquals("DS favorites", loaded.folders[folder.id]?.name)
        assertEquals(listOf(gameId), loaded.folders[folder.id]?.slots)
    }

    /**
     * A Home arranged by hand before collections, shaped like a real one: the All tile, the
     * Android and Emulators folders, a full 3DS folder with a hole, DS, GBA and DroidDeck,
     * two folders the user made with gamepad icons, empty middle-row slots, games carried out
     * of their folders onto the root and into a folder of the user's, a hidden game, a removed
     * folder, and play history from before the update.
     */
    private object RealHome {
        val threeDs = platformFolderId("nintendo-3ds")
        val ds = platformFolderId("nintendo-ds")
        val gba = platformFolderId("game-boy-advance")
        val platforms = listOf(
            HomePlatform("nintendo-3ds", "Nintendo 3DS", setOf("3ds")),
            HomePlatform("nintendo-ds", "Nintendo DS", setOf("nds")),
            HomePlatform("game-boy-advance", "Game Boy Advance", setOf("gba")),
        )
        val threeDsGames = (1..27).map { rom("lib:local-folder:3ds$it", "3ds") }
        val dsGames = (1..4).map { rom("lib:local-folder:ds$it", "nds") }
        val gbaGames = (1..3).map { rom("lib:romm:gba$it", "gba") }
        val deckGames = (1..3).map { HomeItem("droiddeck.steam.$it", "Deck $it", HomeKind.DroidDeck) }
        val apps = (1..3).map { HomeItem("android:app.$it", "App $it", HomeKind.AndroidApp) }
        val androidGames = (1..2).map { HomeItem("android:game.$it", "Game $it", HomeKind.AndroidGame) }
        val emulators = (1..2).map { HomeItem("android:emu.$it", "Emu $it", HomeKind.Emulator) }

        /** Carried out of their folders: onto the root, and into the user's first folder. */
        val pinned3ds = threeDsGames[25]
        val filed3ds = threeDsGames[26]
        val pinnedDs = dsGames[3]
        val filedGba = gbaGames[2]
        val filedDeck = deckGames[2]
        val hidden3ds = threeDsGames[24]

        val catalog = threeDsGames + dsGames + gbaGames + deckGames + apps + androidGames + emulators

        val folders = listOf(
            HomeFolder(HOME_ANDROID_APPS, "Android Apps", bucket = HomeKind.AndroidApp, slots = apps.map { it.id }),
            HomeFolder(HOME_EMULATORS, "Emulators", bucket = HomeKind.Emulator, slots = emulators.map { it.id }),
            HomeFolder(HOME_ANDROID_GAMES, "Android Games", bucket = HomeKind.AndroidGame, slots = androidGames.map { it.id }),
            HomeFolder(
                threeDs, "Nintendo 3DS", platformId = "nintendo-3ds",
                slots = threeDsGames.take(12).map { it.id } + null + threeDsGames.drop(12).take(12).reversed().map { it.id },
            ),
            HomeFolder(ds, "Nintendo DS", platformId = "nintendo-ds", slots = dsGames.take(3).map { it.id }),
            HomeFolder(gba, "GBA", platformId = "game-boy-advance", namedByUser = true, slots = gbaGames.take(2).map { it.id } + null),
            HomeFolder(HOME_DROIDDECK, "DroidDeck", bucket = HomeKind.DroidDeck, slots = deckGames.take(2).map { it.id }),
            HomeFolder("home.user.1", "Folder 1", userMade = true, mark = MARK_CONSOLE, slots = listOf(filed3ds.id, null, filedGba.id)),
            HomeFolder("home.user.2", "Handhelds", userMade = true, namedByUser = true, mark = MARK_CONSOLE, slots = listOf(filedDeck.id)),
        ).associateBy { it.id }

        val board = HomeBoard(
            slots = listOf(
                HOME_ALL, HOME_ANDROID_APPS, HOME_EMULATORS,
                HOME_ANDROID_GAMES, null, threeDs,
                ds, null, gba,
                HOME_DROIDDECK, "home.user.1", "home.user.2",
                pinned3ds.id, null, pinnedDs.id,
            ),
            folders = folders,
            hidden = setOf(hidden3ds.id),
            retired = setOf(HOME_MOONLIGHT),
        )

        /** The board as the last release saved it, which had no Recently played start. */
        val saved: String = encodeHome(board).lines().filter { !it.startsWith("recentAfter") }.joinToString("\n")

        /** Played before the update. */
        fun played(at: Long): List<HomeItem> = catalog.map { if (it.id == threeDsGames[0].id) it.copy(lastPlayedMillis = at) else it }

        private fun rom(id: String, platform: String) = HomeItem(id, id.substringAfterLast(':'), HomeKind.Rom, platformId = platform)
    }

    @Test
    fun aHandArrangedHomeLooksTheSameAfterTheUpdateExceptFoldersRegainTheirGames() {
        val update = 5_000L
        val decoded = decodeHome(RealHome.saved, nowMillis = update)
        val migrated = mergeHome(decoded, RealHome.played(at = 1_000L), RealHome.platforms)

        // Nothing on the root moves, and no tile is added: no Favorites, no Recently played, no folder.
        assertEquals(RealHome.board.slots, migrated.slots)
        assertFalse(HOME_FAVORITES in migrated.folders)
        assertFalse(HOME_RECENT in migrated.folders)
        assertFalse(HOME_MOONLIGHT in migrated.folders)
        assertEquals(setOf(HOME_MOONLIGHT), migrated.retired)
        assertEquals(update, migrated.recentAfter)

        // The folders the user made are collections, with the same names, icons and order.
        for (id in listOf("home.user.1", "home.user.2")) {
            val before = RealHome.folders.getValue(id)
            val after = migrated.folders.getValue(id)
            assertTrue(after.collection)
            assertEquals(before.name, after.name)
            assertEquals(before.mark, after.mark)
            assertEquals(before.slots, after.slots)
        }

        // Every folder keeps what it had, in its order and with its holes, and gains the games carried out of it at the end.
        fun kept(id: String, vararg gained: HomeItem) {
            val before = RealHome.folders.getValue(id).slots
            val after = migrated.folders.getValue(id).slots
            val filled = before.indexOfLast { it != null } + 1
            assertEquals(before.take(filled), after.take(filled))
            assertEquals(gained.map { it.id }, after.drop(filled).filterNotNull())
        }
        kept(RealHome.threeDs, RealHome.filed3ds, RealHome.pinned3ds)
        kept(RealHome.ds, RealHome.pinnedDs)
        kept(RealHome.gba, RealHome.filedGba)
        kept(HOME_DROIDDECK, RealHome.filedDeck)
        kept(HOME_ANDROID_APPS)
        kept(HOME_ANDROID_GAMES)
        kept(HOME_EMULATORS)
        assertEquals(26, migrated.folders.getValue(RealHome.threeDs).slots.count { it != null })
        // The GBA folder had an empty slot after its last game. The game takes it.
        assertEquals(RealHome.gbaGames.map { it.id }, migrated.folders.getValue(RealHome.gba).slots)
        assertEquals("GBA", migrated.folders.getValue(RealHome.gba).name)

        // The hidden game stays hidden and out of every place.
        assertTrue(RealHome.hidden3ds.id in migrated.hidden)
        assertTrue(migrated.folders.values.none { RealHome.hidden3ds.id in it.slots })
        // Every game is still reachable from Home.
        assertTrue((RealHome.catalog - RealHome.hidden3ds).all { onHome(migrated, it.id) })

        // It settles: the next merge and a restart change nothing.
        assertEquals(migrated, mergeHome(migrated, RealHome.played(at = 1_000L), RealHome.platforms))
        assertEquals(migrated, decodeHome(encodeHome(migrated), nowMillis = 9_000L))
    }

    @Test
    fun aHandArrangedHomeWaitsForItsLibrariesBeforeFoldersRegainTheirGames() {
        val decoded = decodeHome(RealHome.saved, nowMillis = 5_000L)
        val apps = RealHome.apps + RealHome.androidGames + RealHome.emulators
        val loading = mergeHome(decoded, apps, RealHome.platforms, unsettled = { !it.startsWith("android:") })
        assertEquals(RealHome.board.slots, loading.slots)
        assertEquals(RealHome.board.folders.mapValues { it.value.slots }, loading.folders.mapValues { it.value.slots })
        // A first play after the update brings Recently played, in the first free root slot.
        val played = mergeHome(loading, RealHome.played(at = 6_000L), RealHome.platforms)
        assertEquals(4, played.slots.indexOf(HOME_RECENT))
        assertEquals(listOf(RealHome.threeDsGames[0].id), played.folders.getValue(HOME_RECENT).slots)
        assertEquals(RealHome.board.slots.map { it ?: HOME_RECENT }.take(5), played.slots.take(5))
    }
}
