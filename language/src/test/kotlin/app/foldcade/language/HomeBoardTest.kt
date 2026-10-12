package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBoardTest {
    private val platforms = listOf(
        HomePlatform("nintendo-3ds", "Nintendo 3DS", setOf("3ds", "n3ds")),
        HomePlatform("game-boy-advance", "Game Boy Advance", setOf("gba")),
        HomePlatform("nintendo-ds", "Nintendo DS", setOf("nds")),
    )

    @Test
    fun moveLeavesAnEmptySlotAndSwapsANeighbor() {
        val board = board(listOf("a", null, "b"))
        val moved = moveHeld(pickUp(board, 0), 1)
        assertEquals(listOf(null, "a", "b"), moved.slots)
        val swapped = moveHeld(pickUp(board, 0), 2)
        assertEquals(listOf("b", null, "a"), swapped.slots)
    }

    @Test
    fun movingOntoAFolderDropsTheTileInside() {
        val folder = HomeFolder("folder.gba", "Game Boy Advance", platformId = "game-boy-advance")
        val board = HomeBoard(
            slots = listOf("game.cart", "folder.gba"),
            folders = mapOf("folder.gba" to folder),
        )
        val dropped = moveHeld(pickUp(board, 0), 1)
        assertNull(dropped.hold)
        assertEquals(listOf(null, "folder.gba"), dropped.slots)
        assertEquals(listOf("game.cart"), dropped.folders.getValue("folder.gba").slots)
    }

    @Test
    fun deleteFolderMovesContentsOutAndKeepsTheGames() {
        val folder = HomeFolder("folder.gba", "Game Boy Advance", slots = listOf("game.cart", null, "game.drift"))
        val board = HomeBoard(
            slots = listOf(HOME_ALL, "folder.gba", "loose"),
            folders = mapOf("folder.gba" to folder),
        )
        val deleted = deleteFolder(board, "folder.gba")
        assertFalse(deleted.folders.containsKey("folder.gba"))
        assertFalse(deleted.slots.contains("folder.gba"))
        assertTrue(deleted.slots.contains("game.cart"))
        assertTrue(deleted.slots.contains("game.drift"))
        assertTrue(deleted.slots.contains("loose"))
        assertTrue(deleted.slots.contains(HOME_ALL))
        assertEquals(listOf("game.cart", "game.drift"), deleted.slots.filter { it == "game.cart" || it == "game.drift" })
    }

    @Test
    fun hideAndUnhideLeaveTheCatalogAlone() {
        val board = board(listOf(HOME_ALL, "game.cart", "game.drift"))
        val hidden = removeFromHome(board, 1)
        assertEquals(setOf("game.cart"), hidden.hidden)
        assertFalse(hidden.slots.contains("game.cart"))
        assertFalse(onHome(hidden, "game.cart"))
        val catalog = listOf(item("game.cart"), item("game.drift"))
        val rescanned = mergeHome(hidden, catalog, platforms)
        assertTrue("game.cart" in rescanned.hidden)
        assertFalse(onHome(rescanned, "game.cart"))
        val shown = addToHome(rescanned, "game.cart")
        assertFalse("game.cart" in shown.hidden)
        assertTrue(onHome(shown, "game.cart"))
        assertEquals(catalog, catalog)
    }

    @Test
    fun addToHomeUsesTheFirstFreeSlotOrAChosenFolder() {
        val folder = HomeFolder("folder.user", "Mine", userMade = true, slots = listOf(null, "kept"))
        val board = HomeBoard(
            slots = listOf(HOME_ALL, "taken", null),
            folders = mapOf("folder.user" to folder),
        )
        val rooted = addToHome(board, "game.cart")
        assertEquals(listOf(HOME_ALL, "taken", "game.cart"), rooted.slots)
        val chosen = addToHome(board, "game.drift", "folder.user")
        assertEquals(listOf("game.drift", "kept"), chosen.folders.getValue("folder.user").slots)
        assertFalse(chosen.slots.contains("game.drift"))
    }

    @Test
    fun rescanSettingPlacesNewGamesOnlyWhenItIsOn() {
        val existing = item("game.cart", platformId = "gba")
        val fresh = item("game.drift", platformId = "gba")
        val placed = mergeHome(HomeBoard(), listOf(existing), platforms)
        val folderId = platformFolderId("game-boy-advance")
        assertEquals(listOf("game.cart"), placed.folders.getValue(folderId).slots)
        val quiet = mergeHome(setAddNewToHome(placed, false), listOf(existing, fresh), platforms)
        assertFalse(onHome(quiet, "game.drift"))
        assertTrue(onHome(quiet, "game.cart"))
        val loud = mergeHome(setAddNewToHome(quiet, true), listOf(existing, fresh), platforms)
        assertEquals(listOf("game.cart", "game.drift"), loud.folders.getValue(folderId).slots)
    }

    @Test
    fun aliasesShareOneSystemFolderAndTheAllTileCannotBeRemoved() {
        val gba = item("game.cart", platformId = "gba")
        val alias = item("game.drift", platformId = "game-boy-advance")
        val merged = mergeHome(HomeBoard(), listOf(gba, alias), platforms)
        val folderId = platformFolderId("game-boy-advance")
        assertEquals("Game Boy Advance", merged.folders.getValue(folderId).name)
        assertEquals("game-boy-advance", merged.folders.getValue(folderId).mark)
        assertEquals(listOf("game.cart", "game.drift"), merged.folders.getValue(folderId).slots)
        assertTrue(merged.slots.contains(HOME_ALL))
        // No games, no folder.
        assertEquals(setOf(folderId), merged.folders.keys)
        val allIndex = merged.slots.indexOf(HOME_ALL)
        val kept = removeFromHome(merged, allIndex)
        assertTrue(kept.slots.contains(HOME_ALL))
        assertFalse(HOME_ALL in kept.hidden)
    }

    @Test
    fun aRescanKeepsPlacementAndClearsAMissingGame() {
        val cart = item("game.cart", platformId = "nds")
        val drift = item("game.drift", platformId = "nds")
        val merged = mergeHome(HomeBoard(), listOf(cart, drift), platforms)
        val folderId = platformFolderId("nintendo-ds")
        val punched = merged.copy(
            folders = merged.folders + (
                folderId to merged.folders.getValue(folderId).copy(slots = listOf("game.drift", null, "game.cart"))
                ),
        )
        val again = mergeHome(punched, listOf(cart), platforms)
        assertEquals(listOf(null, null, "game.cart"), again.folders.getValue(folderId).slots)
        assertTrue(onHome(again, "game.cart"))
    }

    @Test
    fun cancelRestoresThePickupAndEncodeRoundTrips() {
        val board = board(listOf("a", "b", null))
        val moved = moveHeld(pickUp(board, 0), 2)
        val restored = cancelHold(moved)
        assertEquals(listOf("a", "b", null), restored.slots)
        assertNull(restored.hold)
        val renamed = renameFolder(
            createFolder(board, "home.user.1", "Mine"),
            "home.user.1",
            "Night\nshift",
        )
        val decoded = decodeHome(encodeHome(renamed.copy(addNewToHome = false, hidden = setOf("a"))))
        assertFalse(decoded.addNewToHome)
        assertEquals(setOf("a"), decoded.hidden)
        assertEquals("Night shift", decoded.folders.getValue("home.user.1").name)
        assertEquals(listOf(HOME_ALL) + renamed.slots, decoded.slots)
    }

    @Test
    fun aFolderTheUserMadeKeepsTheIconTheyChose() {
        val made = createFolder(HomeBoard(), "home.user.1", "Zelda")
        val iconed = setFolderIcon(made, "home.user.1", "nintendo-3ds")
        assertEquals("nintendo-3ds", iconed.folders.getValue("home.user.1").mark)
        // A rescan and a saved board keep it.
        val merged = mergeHome(iconed, emptyList(), platforms)
        assertEquals("nintendo-3ds", merged.folders.getValue("home.user.1").mark)
        assertEquals("nintendo-3ds", decodeHome(encodeHome(merged)).folders.getValue("home.user.1").mark)
        // Only icons from the set, and only on folders the user made.
        assertEquals(iconed, setFolderIcon(iconed, "home.user.1", "not-an-icon"))
        val system = mergeHome(HomeBoard(), emptyList(), platforms)
        assertEquals(system, setFolderIcon(system, HOME_GAMENATIVE, "nintendo-3ds"))
        assertEquals(MARK_FOLDER, FOLDER_ICONS.first())
        assertTrue(FOLDER_ICONS.containsAll(PLATFORM_MARKS))
    }

    @Test
    fun allLibraryOmitsSetupTilesWhileKeepingPcAndStreamedGames() {
        val pc = item("exported-pc", kind = HomeKind.GameNative)
        val streamed = item("streamed", kind = HomeKind.Moonlight)
        val placeholders = listOf(
            item("empty-pc", kind = HomeKind.GameNative).copy(placeholder = true),
            item("empty-stream", kind = HomeKind.Moonlight).copy(placeholder = true),
        )
        val catalog = listOf(pc, streamed) + placeholders
        assertEquals(setOf(pc.id, streamed.id), allItems(catalog, platforms, AllTab.Games, AllSort.Title, null).map { it.id }.toSet())
        assertTrue(allItems(catalog, platforms, AllTab.Apps, AllSort.Title, null).isEmpty())
        val rom = item("rom", kind = HomeKind.Rom).copy(platformId = "nintendo-ds")
        val pcHint = placeholders.first().copy(platformId = "pc")
        val choices = platforms + HomePlatform("pc", "PC")
        assertEquals("nintendo-ds", cycleSystemFilter(HomeBoard(), choices, listOf(pcHint, rom)).allSystemId)
    }

    @Test
    fun allGamesSystemChoicesExcludePlatformsWithOnlyApplications() {
        val android = HomePlatform("android", "Android")
        val app = item("utility", kind = HomeKind.AndroidApp).copy(platformId = android.id)
        val rom = item("rom", kind = HomeKind.Rom).copy(platformId = "nintendo-ds")
        val systems = platforms + android
        assertNull(cycleSystemFilter(HomeBoard(), systems, listOf(app)).allSystemId)
        val ds = cycleSystemFilter(HomeBoard(), systems, listOf(app, rom))
        assertEquals("nintendo-ds", ds.allSystemId)
        assertNull(cycleSystemFilter(ds, systems, listOf(app, rom)).allSystemId)
    }

    @Test
    fun allGamesIncludesEveryGameKindAndAllAppsContainsOnlyApplications() {
        val games = listOf(HomeKind.Rom, HomeKind.AndroidGame, HomeKind.GameNative, HomeKind.Moonlight)
            .map { item(it.name, kind = it) }
        val app = item("utility", kind = HomeKind.AndroidApp)
        val loose = item("loose", kind = HomeKind.Loose)
        val catalog = games + app + loose
        assertEquals(games.map { it.id }.toSet(), allItems(catalog, platforms, AllTab.Games, AllSort.Title, null).map { it.id }.toSet())
        assertEquals(listOf(app.id), allItems(catalog, platforms, AllTab.Apps, AllSort.Title, null).map { it.id })
        assertTrue(allItems(catalog, platforms, AllTab.Games, AllSort.Title, "nintendo-ds").isEmpty())
    }

    @Test
    fun allLibrarySortsFiltersAndJumpsLetters() {
        val items = listOf(
            item("b", title = "Beta", platformId = "gba"),
            item("a", title = "Alpha", platformId = "nds"),
            item("c", title = "Amber", platformId = "gba", kind = HomeKind.AndroidApp),
            item("d", title = "Drift", platformId = "gba", lastPlayedMillis = 50),
        )
        val titles = allItems(items, platforms, AllTab.Games, AllSort.Title, null).map { it.title }
        assertEquals(listOf("Alpha", "Beta", "Drift"), titles)
        val recent = allItems(items, platforms, AllTab.Games, AllSort.RecentlyPlayed, null).map { it.id }
        assertEquals("d", recent.first())
        val gba = allItems(items, platforms, AllTab.Games, AllSort.Title, "game-boy-advance").map { it.id }
        assertEquals(listOf("b", "d"), gba)
        val apps = allItems(items, platforms, AllTab.Apps, AllSort.Title, null).map { it.id }
        assertEquals(listOf("c"), apps)
        val labels = listOf("Alpha", "Amber", "Beta")
        assertEquals(2, jumpLetter(labels, 0, forward = true))
        assertEquals(2, jumpLetter(labels, 2, forward = true))
        assertEquals(0, jumpLetter(labels, 2, forward = false))
    }

    @Test
    fun aPlatformFolderLeavesWithItsLastGameAndComesBackWithTheNext() {
        val drift = item("game.drift", platformId = "nds")
        val folderId = platformFolderId("nintendo-ds")
        // A real board already has its system folders when the first ROM arrives.
        val fresh = mergeHome(HomeBoard(), emptyList(), platforms)
        val withGame = mergeHome(fresh, listOf(drift), platforms)
        val slot = withGame.slots.indexOf(folderId)
        assertEquals(withGame.slots.lastIndex, slot)

        val gone = mergeHome(withGame, emptyList(), platforms)
        assertFalse(gone.folders.containsKey(folderId))
        assertFalse(gone.slots.contains(folderId))
        // It was the last tile, so no empty slot is left behind.
        assertEquals(withGame.slots.take(slot), gone.slots)

        val back = mergeHome(gone, listOf(drift), platforms)
        assertTrue(back.folders.containsKey(folderId))
        assertEquals(slot, back.slots.indexOf(folderId))
        assertEquals(listOf("game.drift"), back.folders.getValue(folderId).slots)
    }

    @Test
    fun aPlatformFolderLeavingTheMiddleKeepsItsNeighboursInPlace() {
        val drift = item("game.drift", platformId = "nds")
        val cart = item("game.cart", platformId = "gba")
        val both = mergeHome(HomeBoard(), listOf(drift, cart), platforms)
        val ds = both.slots.indexOf(platformFolderId("nintendo-ds"))
        val gba = both.slots.indexOf(platformFolderId("game-boy-advance"))
        assertTrue(ds in 0 until gba)

        val gone = mergeHome(both, listOf(cart), platforms)
        assertNull(gone.slots[ds])
        assertEquals(gba, gone.slots.indexOf(platformFolderId("game-boy-advance")))
    }

    @Test
    fun aGameFromALibraryStillScanningKeepsItsSlotAndFolder() {
        val drift = item("lib:local-folder:drift", platformId = "nds")
        val folderId = platformFolderId("nintendo-ds")
        val placed = mergeHome(HomeBoard(), listOf(drift), platforms)
        val launch = mergeHome(placed, emptyList(), platforms, unsettled = { it.startsWith("lib:local-folder:") })
        assertEquals(listOf("lib:local-folder:drift"), launch.folders.getValue(folderId).slots)
        assertEquals(placed.slots, launch.slots)
    }

    @Test
    fun aFolderWhoseGamesAreAllHiddenGoesAndComesBackWithOne() {
        val drift = item("game.drift", platformId = "nds")
        val folderId = platformFolderId("nintendo-ds")
        val placed = mergeHome(HomeBoard(), listOf(drift), platforms)
        val hidden = mergeHome(removeFromHome(openFolder(placed, folderId), 0), listOf(drift), platforms)
        assertFalse(hidden.folders.containsKey(folderId))
        assertFalse(hidden.slots.contains(folderId))
        assertNull(hidden.openFolderId)

        val back = addItemToHome(hidden, drift, platforms)
        assertEquals(listOf("game.drift"), back.folders.getValue(folderId).slots)
        assertTrue(back.slots.contains(folderId))
        assertEquals(back, mergeHome(back, listOf(drift), platforms))
    }

    @Test
    fun aFreshBoardHasNoFolderWithoutAGame() {
        val merged = mergeHome(HomeBoard(), emptyList(), platforms)
        assertEquals(listOf(HOME_ALL), merged.slots)
        assertTrue(merged.folders.isEmpty())
    }

    @Test
    fun emulatorsGetTheirOwnFolderApartFromGames() {
        val azahar = item("android:org.azahar_emu.azahar", kind = HomeKind.Emulator)
        val moonlight = item("android:com.limelight", kind = HomeKind.Emulator)
        val stream = item("host|7", kind = HomeKind.Moonlight)
        val merged = mergeHome(HomeBoard(), listOf(azahar, moonlight, stream), platforms)
        assertEquals(listOf(azahar.id, moonlight.id), merged.folders.getValue(HOME_EMULATORS).slots)
        assertEquals(listOf(stream.id), merged.folders.getValue(HOME_MOONLIGHT).slots)
        assertEquals("emulators", merged.folders.getValue(HOME_EMULATORS).mark)
        assertEquals("Emulators", merged.folders.getValue(HOME_EMULATORS).name)
        assertTrue(merged.emulatorsFiled)

        // Uninstalled, the last emulator takes the folder with it.
        val gone = mergeHome(merged, listOf(stream), platforms)
        assertFalse(gone.folders.containsKey(HOME_EMULATORS))
    }

    @Test
    fun emulatorsFiledWithTheAndroidAppsMoveOnceAndThenStayWhereTheUserPutsThem() {
        val azahar = item("android:org.azahar_emu.azahar", kind = HomeKind.Emulator)
        val notes = item("android:com.example.notes", kind = HomeKind.AndroidApp)
        val old = HomeBoard(
            slots = listOf(HOME_ALL, HOME_ANDROID_APPS),
            folders = mapOf(
                HOME_ANDROID_APPS to HomeFolder(HOME_ANDROID_APPS, "Android Apps", bucket = HomeKind.AndroidApp, slots = listOf(azahar.id, notes.id)),
            ),
        )
        // Before the installed apps report, nothing moves.
        val loading = mergeHome(old, emptyList(), platforms, unsettled = { it.startsWith("android:") })
        assertFalse(loading.emulatorsFiled)
        assertEquals(listOf(azahar.id, notes.id), loading.folders.getValue(HOME_ANDROID_APPS).slots)

        val filed = mergeHome(loading, listOf(azahar, notes), platforms)
        assertEquals(listOf(null, notes.id), filed.folders.getValue(HOME_ANDROID_APPS).slots)
        assertEquals(listOf(azahar.id), filed.folders.getValue(HOME_EMULATORS).slots)
        assertTrue(decodeHome(encodeHome(filed)).emulatorsFiled)

        // The user moves it back. It stays.
        val moved = moveToFolder(filed, azahar.id, HOME_ANDROID_APPS)
        val again = mergeHome(decodeHome(encodeHome(moved)), listOf(azahar, notes), platforms)
        assertTrue(azahar.id in again.folders.getValue(HOME_ANDROID_APPS).slots)
        assertFalse(again.folders.containsKey(HOME_EMULATORS))
    }

    @Test
    fun aLibraryFolderWaitsForGamesStillLoading() {
        val game = item("lib:romm:7", kind = HomeKind.AndroidGame)
        val placed = mergeHome(HomeBoard(), listOf(game), platforms)
        val loading = mergeHome(placed, emptyList(), platforms, unsettled = { it == "lib:romm:7" })
        assertEquals(placed.folders, loading.folders)
        val gone = mergeHome(loading, emptyList(), platforms)
        assertTrue(gone.folders.isEmpty())
    }

    @Test
    fun aFolderTheUserMadeStaysWhenItsGamesAreGone() {
        val mine = HomeFolder("folder.mine", "Mine", platformId = "nintendo-ds", userMade = true, slots = listOf("game.drift"))
        val board = HomeBoard(slots = listOf(HOME_ALL, "folder.mine"), folders = mapOf("folder.mine" to mine))
        val again = mergeHome(board, emptyList(), platforms)
        assertTrue(again.folders.containsKey("folder.mine"))
        assertTrue(again.slots.contains("folder.mine"))
    }

    @Test
    fun anOpenPlatformFolderClosesWhenItsGamesAreGone() {
        val drift = item("game.drift", platformId = "nds")
        val folderId = platformFolderId("nintendo-ds")
        val open = mergeHome(HomeBoard(), listOf(drift), platforms).copy(openFolderId = folderId)
        val gone = mergeHome(open, emptyList(), platforms)
        assertNull(gone.openFolderId)
    }

    @Test
    fun aSavedBoardTradesOldMarksForEachFoldersOwn() {
        val saved = HomeBoard(
            folders = mapOf(
                platformFolderId("nintendo-3ds") to HomeFolder(
                    platformFolderId("nintendo-3ds"), "Nintendo 3DS", platformId = "nintendo-3ds", mark = "dual",
                ),
                platformFolderId("segacd") to HomeFolder(platformFolderId("segacd"), "Sega CD", platformId = "segacd", mark = "desk"),
                HOME_MOONLIGHT to HomeFolder(HOME_MOONLIGHT, "Moonlight", bucket = HomeKind.Moonlight, mark = "beam"),
                "folder.mine" to HomeFolder("folder.mine", "Mine", userMade = true, mark = "desk"),
            ),
        )
        val folders = decodeHome(encodeHome(saved)).folders
        assertEquals("nintendo-3ds", folders.getValue(platformFolderId("nintendo-3ds")).mark)
        assertEquals("console", folders.getValue(platformFolderId("segacd")).mark)
        assertEquals("moonlight", folders.getValue(HOME_MOONLIGHT).mark)
        assertEquals("folder", folders.getValue("folder.mine").mark)
    }

    @Test
    fun aNewFolderWrapsTheGameInItsPlace() {
        val board = board(listOf(HOME_ALL, "game.cart", "game.drift"))
        val made = folderAround(board, 1, "home.user.1", "Folder 1")
        assertEquals(listOf(HOME_ALL, "home.user.1", "game.drift"), made.slots)
        val folder = made.folders.getValue("home.user.1")
        assertEquals(listOf("game.cart"), folder.slots)
        assertTrue(folder.userMade)
        assertEquals(MARK_FOLDER, folder.mark)
        // On the All tile there is no game to wrap, so the folder takes the first free slot.
        val empty = folderAround(board(listOf(HOME_ALL, null, "game.drift")), 0, "home.user.1", "Folder 1")
        assertEquals(listOf(HOME_ALL, "home.user.1", "game.drift"), empty.slots)
        assertEquals(emptyList<String?>(), empty.folders.getValue("home.user.1").slots)
    }

    @Test
    fun aNewFolderMadeInsideAFolderLandsOnTheRoot() {
        val gba = HomeFolder("folder.gba", "Game Boy Advance", slots = listOf("game.cart", "game.drift"))
        val board = HomeBoard(
            slots = listOf(HOME_ALL, "folder.gba"),
            folders = mapOf("folder.gba" to gba),
            openFolderId = "folder.gba",
        )
        val made = folderAround(board, 1, "home.user.1", "Folder 1")
        assertEquals(listOf(HOME_ALL, "folder.gba", "home.user.1"), made.slots)
        assertEquals(listOf("game.cart", null), made.folders.getValue("folder.gba").slots)
        assertEquals(listOf("game.drift"), made.folders.getValue("home.user.1").slots)
    }

    @Test
    fun moveToSendsAGameIntoAFolderOrBackToTheRoot() {
        val mine = HomeFolder("folder.mine", "Mine", userMade = true, slots = listOf("kept"))
        val board = HomeBoard(
            slots = listOf(HOME_ALL, "game.cart", "folder.mine"),
            folders = mapOf("folder.mine" to mine),
        )
        val inside = moveToFolder(board, "game.cart", "folder.mine")
        assertEquals(listOf(HOME_ALL, null, "folder.mine"), inside.slots)
        assertEquals(listOf("kept", "game.cart"), inside.folders.getValue("folder.mine").slots)
        val back = moveToFolder(inside, "game.cart", null)
        assertEquals(listOf(HOME_ALL, "game.cart", "folder.mine"), back.slots)
        assertEquals(listOf("kept", null), back.folders.getValue("folder.mine").slots)
        // Folders and the All tile stay where they are.
        assertEquals(board, moveToFolder(board, "folder.mine", null))
        assertEquals(board, moveToFolder(board, HOME_ALL, "folder.mine"))
    }

    @Test
    fun aGameBroughtBackFromAllReturnsToItsFolderWhileItIsThere() {
        val cart = item("game.cart", platformId = "gba")
        val placed = mergeHome(HomeBoard(), listOf(cart, item("game.drift", platformId = "gba")), platforms)
        val gba = platformFolderId("game-boy-advance")
        assertEquals(gba, homeFolderFor(placed, cart, platforms))
        val app = item("android:example", kind = HomeKind.AndroidApp)
        // No app has made the Android Apps folder.
        assertNull(homeFolderFor(placed, app, platforms))
        assertEquals(listOf("android:example"), addItemToHome(placed, app, platforms).folders.getValue(HOME_ANDROID_APPS).slots)
        // With its folder removed there is nowhere to return to, so it goes on the root.
        val removed = deleteFolder(placed, gba)
        assertNull(homeFolderFor(removed, cart, platforms))
        assertNull(homeFolderFor(placed, item("loose", kind = HomeKind.Loose), platforms))
    }

    private fun board(slots: List<String?>): HomeBoard = HomeBoard(slots = slots)

    private fun item(
        id: String,
        title: String = id,
        platformId: String? = null,
        kind: HomeKind = HomeKind.Rom,
        lastPlayedMillis: Long = 0L,
    ): HomeItem = HomeItem(
        id = id,
        title = title,
        kind = kind,
        platformId = platformId,
        lastPlayedMillis = lastPlayedMillis,
    )
}
