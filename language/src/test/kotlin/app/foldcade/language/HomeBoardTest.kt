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
    fun aHomeGameIdGivesBackItsWholeRemoteKey() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AROMs/document/primary%3AROMs%2Fa.gba"
        assertEquals(uri, homeGameKey(homeGameId("local-folder", uri)))
        assertEquals("1234", homeGameKey(homeGameId("romm", "1234")))
        assertNull(homeGameKey(uri))
        assertNull(homeGameKey("android:com.example"))
        assertNull(homeGameKey("lib:"))
        assertNull(homeGameKey("lib:romm"))
        assertNull(homeGameKey("lib:romm:"))
        assertNull(homeGameKey("lib::1234"))
    }

    @Test
    fun moveLeavesAnEmptySlotAndSwapsANeighbor() {
        val board = board(listOf("a", null, "b"))
        val moved = moveHeld(pickUp(board, 0), 1, emptyList(), platforms)
        assertEquals(listOf(null, "a", "b"), moved.slots)
        val swapped = moveHeld(pickUp(board, 0), 2, emptyList(), platforms)
        assertEquals(listOf("b", null, "a"), swapped.slots)
    }

    @Test
    fun aLiftedGapMovesAsTheInverseOfATile() {
        val board = board(listOf("a", null, "b"))
        val lifted = pickUp(board, 1)
        assertEquals(1, lifted.hold?.index)
        // Carrying the gap onto a tile is the same as carrying that tile onto the gap.
        assertEquals(moveHeld(pickUp(board, 0), 1, emptyList(), platforms).slots, moveHeld(lifted, 0, emptyList(), platforms).slots)
        assertEquals(listOf("a", "b", null), moveHeld(lifted, 2, emptyList(), platforms).slots)
        // A long walk trades with one tile, and cancel puts the gap back.
        val long = moveHeld(pickUp(board(listOf(null, "a", "b", "c")), 0), 3, emptyList(), platforms)
        assertEquals(listOf("c", "a", "b", null), long.slots)
        assertEquals(listOf(null, "a", "b", "c"), cancelHold(long).slots)
    }

    @Test
    fun aGapPastTheLastTilePullsATileOutAndLeavesNoPadding() {
        val board = board(listOf("a", "b"))
        val lifted = pickUp(board, 4)
        assertEquals(4, lifted.hold?.index)
        // Walking among empties changes nothing on the board.
        assertEquals(board.slots, moveHeld(lifted, 3, emptyList(), platforms).slots)
        // Onto a tile, that tile goes where the gap was.
        assertEquals(listOf("a", null, null, null, "b"), moveHeld(moveHeld(lifted, 3, emptyList(), platforms), 1, emptyList(), platforms).slots)
    }

    @Test
    fun aGapDoesNotDropIntoAFolder() {
        val mine = HomeFolder("folder.mine", "Mine", userMade = true)
        val board = HomeBoard(slots = listOf(null, mine.id), folders = mapOf(mine.id to mine))
        val passed = moveHeld(pickUp(board, 0), 1, emptyList(), platforms)
        assertEquals(1, passed.hold?.index)
        assertEquals(listOf(mine.id, null), passed.slots)
        assertTrue(passed.folders.getValue(mine.id).slots.isEmpty())
    }

    @Test
    fun droppingOntoAFolderPutsTheTileInside() {
        val folder = HomeFolder("folder.gba", "Game Boy Advance", platformId = "game-boy-advance")
        val board = HomeBoard(
            slots = listOf("game.cart", "folder.gba"),
            folders = mapOf("folder.gba" to folder),
        )
        val catalog = listOf(item("game.cart", platformId = "gba"))
        val over = moveHeld(pickUp(board, 0), 1, catalog, platforms)
        // Walking onto it only hovers. The drop puts it in.
        assertEquals(board.slots, over.slots)
        val dropped = drop(over, catalog, platforms)
        assertNull(dropped.hold)
        assertEquals(listOf(null, "folder.gba"), dropped.slots)
        assertEquals(listOf("game.cart"), dropped.folders.getValue("folder.gba").slots)
    }

    @Test
    fun aLongMoveTradesPlacesWithOneTileAndLeavesTheRestWhereTheyWere() {
        val board = board(listOf("a", "b", "c", "d", "e"))
        var moving = pickUp(board, 0)
        for (to in 1..4) moving = moveHeld(moving, to, emptyList(), platforms)
        assertEquals(listOf("e", "b", "c", "d", "a"), moving.slots)
        // Walking back past the tiles puts each one back.
        moving = moveHeld(moving, 3, emptyList(), platforms)
        assertEquals(listOf("d", "b", "c", "a", "e"), moving.slots)
        moving = moveHeld(moving, 0, emptyList(), platforms)
        assertEquals(board.slots, moving.slots)
        // Past the end and back, no empty slots are left behind.
        moving = moveHeld(moveHeld(moving, 6, emptyList(), platforms), 4, emptyList(), platforms)
        assertEquals(listOf("e", "b", "c", "d", "a"), moving.slots)
    }

    @Test
    fun aGameDoesNotGoInAnotherSystemsFolder() {
        val ds = HomeFolder(platformFolderId("nintendo-ds"), "Nintendo DS", platformId = "nintendo-ds")
        val mine = HomeFolder("folder.mine", "Mine", userMade = true)
        val board = HomeBoard(
            slots = listOf("game.3ds", ds.id, "folder.mine", HOME_ANDROID_GAMES),
            folders = mapOf(
                ds.id to ds,
                mine.id to mine,
                HOME_ANDROID_GAMES to HomeFolder(HOME_ANDROID_GAMES, "Android Games", bucket = HomeKind.AndroidGame),
            ),
        )
        val catalog = listOf(item("game.3ds", platformId = "3ds"))
        // Walking onto the DS folder trades places with it instead of dropping in.
        val passed = moveHeld(pickUp(board, 0), 1, catalog, platforms)
        assertEquals(listOf(ds.id, "game.3ds", "folder.mine", HOME_ANDROID_GAMES), passed.slots)
        assertTrue(passed.folders.getValue(ds.id).slots.isEmpty())
        // A collection takes it, and the pin drops in.
        val inMine = drop(moveHeld(passed, 2, catalog, platforms), catalog, platforms)
        assertNull(inMine.hold)
        assertEquals(listOf("game.3ds"), inMine.folders.getValue("folder.mine").slots)
        assertFalse("game.3ds" in inMine.slots)

        assertEquals(board, fileGame(board, "game.3ds", ds.id, catalog, platforms))
        assertEquals(board, fileGame(board, "game.3ds", HOME_ANDROID_GAMES, catalog, platforms))
        // Not in the catalog yet, so it only goes in a collection.
        assertEquals(board, fileGame(board, "game.3ds", ds.id, emptyList(), platforms))
        val filed = fileGame(board, "game.3ds", "folder.mine", emptyList(), platforms)
        assertEquals(listOf("game.3ds"), filed.folders.getValue("folder.mine").slots)
        // Filed in a collection, the game keeps its pin.
        assertEquals(board.slots, filed.slots)
    }

    @Test
    fun removingAFolderRetiresItWithItsGamesAndSpillsNothing() {
        val gba = platformFolderId("game-boy-advance")
        val cart = item("game.cart", platformId = "gba")
        val drift = item("game.drift", platformId = "gba")
        val placed = mergeHome(HomeBoard(), listOf(cart, drift), platforms)
        val pinned = pinToHome(placed, cart.id)
        val removed = removeFolder(pinned, gba)
        assertFalse(gba in removed.slots)
        assertTrue(gba in removed.retired)
        // Nothing lands on the root, and the pin stays.
        assertEquals(pinned.slots.map { if (it == gba) null else it }, removed.slots)
        assertEquals(listOf(cart.id, drift.id), removed.folders.getValue(gba).slots)
        assertTrue(onHome(removed, cart.id))
        assertFalse(onHome(removed, drift.id))
        // A rescan does not take its games for new ones and bring it back.
        val again = mergeHome(removed, listOf(cart, drift), platforms)
        assertFalse(gba in again.slots)
        assertTrue(gba in again.retired)
        assertFalse(onHome(again, drift.id))
        // New games join it, but an explicit removal stays off Home until Show.
        val fresh = item("game.fresh", platformId = "gba")
        val back = mergeHome(again, listOf(cart, drift, fresh), platforms)
        assertFalse(gba in back.slots)
        assertTrue(gba in back.retired)
        assertEquals(listOf(cart.id, drift.id, fresh.id), back.folders.getValue(gba).slots)
    }

    @Test
    fun removingACollectionDeletesOnlyTheCollection() {
        val cart = item("game.cart", platformId = "gba")
        val placed = mergeHome(HomeBoard(), listOf(cart), platforms)
        val made = newCollection(placed, "home.user.1", "Mine", member = cart.id)
        val index = made.slots.indexOf("home.user.1")
        assertEquals(RemoveVerb.Remove, removeVerb(made, index))
        val removed = removeFromHome(made, index)
        assertFalse("home.user.1" in removed.folders)
        assertNull(removed.slots[index])
        assertFalse("home.user.1" in removed.retired)
        assertEquals(placed.folders, removed.folders)
        assertTrue(onHome(removed, cart.id))
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
        val shown = pinToHome(rescanned, "game.cart")
        assertFalse("game.cart" in shown.hidden)
        assertTrue(onHome(shown, "game.cart"))
    }

    @Test
    fun pinToHomeUsesTheFirstFreeSlotAndFilingAddsToACollection() {
        val folder = HomeFolder("folder.user", "Mine", userMade = true, slots = listOf(null, "kept"))
        val board = HomeBoard(
            slots = listOf(HOME_ALL, "taken", null),
            folders = mapOf("folder.user" to folder),
        )
        val rooted = pinToHome(board, "game.cart")
        assertEquals(listOf(HOME_ALL, "taken", "game.cart"), rooted.slots)
        assertEquals(rooted, pinToHome(rooted, "game.cart"))
        val chosen = fileGame(board, "game.drift", "folder.user", emptyList(), platforms)
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
        val moved = moveHeld(pickUp(board, 0), 2, emptyList(), platforms)
        val restored = cancelHold(moved)
        assertEquals(listOf("a", "b", null), restored.slots)
        assertNull(restored.hold)
        val renamed = renameFolder(
            newCollection(board, "home.user.1", "Mine"),
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
        val made = newCollection(HomeBoard(), "home.user.1", "Zelda")
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
        // Theme glyphs lead, then the libraries and platforms. A collection is a folder already.
        assertEquals(COLLECTION_GLYPHS, FOLDER_ICONS.take(COLLECTION_GLYPHS.size))
        assertTrue(FOLDER_ICONS.containsAll(PLATFORM_MARKS))
        assertFalse(MARK_FOLDER in FOLDER_ICONS)
    }

    @Test
    fun allLibraryOmitsSetupTilesWhileKeepingPcAndStreamedGames() {
        val pc = item("exported-pc", kind = HomeKind.GameNative)
        val streamed = item("streamed", kind = HomeKind.Moonlight)
        val placeholders = listOf(
            item("empty-pc", kind = HomeKind.GameNative).copy(placeholder = true),
            item("empty-stream", kind = HomeKind.Moonlight).copy(placeholder = true),
        )
        assertEquals(setOf(pc.id, streamed.id), allItems(listOf(pc, streamed) + placeholders).map { it.id }.toSet())
    }

    @Test
    fun allLibraryHoldsEveryGameKindAndNoApps() {
        val games = listOf(HomeKind.Rom, HomeKind.AndroidGame, HomeKind.GameNative, HomeKind.Moonlight, HomeKind.DroidDeck)
            .map { item(it.name, kind = it) }
        val others = listOf(HomeKind.AndroidApp, HomeKind.Emulator, HomeKind.Loose).map { item(it.name, kind = it) }
        assertEquals(games.map { it.id }.toSet(), allItems(games + others).map { it.id }.toSet())
    }

    @Test
    fun allLibraryRunsAToZAndJumpsLetters() {
        val items = listOf(
            item("b", title = "Beta", platformId = "gba"),
            item("a", title = "alpha", platformId = "nds"),
            item("d", title = "Drift", platformId = "gba", lastPlayedMillis = 50),
        )
        assertEquals(listOf("alpha", "Beta", "Drift"), allItems(items).map { it.title })
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
        assertTrue(hidden.folders.containsKey(folderId))
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
        assertEquals(setOf(azahar.id, moonlight.id), merged.filedEmulators)

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
        assertTrue(loading.filedEmulators.isEmpty())
        assertEquals(listOf(azahar.id, notes.id), loading.folders.getValue(HOME_ANDROID_APPS).slots)

        val filed = mergeHome(loading, listOf(azahar, notes), platforms)
        assertEquals(listOf(null, notes.id), filed.folders.getValue(HOME_ANDROID_APPS).slots)
        assertEquals(listOf(azahar.id), filed.folders.getValue(HOME_EMULATORS).slots)
        assertEquals(setOf(azahar.id), decodeHome(encodeHome(filed)).filedEmulators)

        // The user moves it back. It stays.
        val moved = fileGame(filed, azahar.id, HOME_ANDROID_APPS, listOf(azahar, notes), platforms)
        val again = mergeHome(decodeHome(encodeHome(moved)), listOf(azahar, notes), platforms)
        assertTrue(azahar.id in again.folders.getValue(HOME_ANDROID_APPS).slots)
        assertFalse(again.folders.containsKey(HOME_EMULATORS))
    }

    @Test
    fun anAppThatBecomesAnEmulatorLaterMovesToEmulatorsOnce() {
        val azahar = item("android:org.azahar_emu.azahar", kind = HomeKind.Emulator)
        val asGame = item("android:com.retroarch.aarch64", kind = HomeKind.AndroidGame)
        val retroArch = asGame.copy(kind = HomeKind.Emulator)
        // RetroArch says it is a game. Before its player loads, it files with the Android games.
        val before = mergeHome(HomeBoard(), listOf(azahar, asGame), platforms)
        assertEquals(listOf(asGame.id), before.folders.getValue(HOME_ANDROID_GAMES).slots)
        assertEquals(setOf(azahar.id), before.filedEmulators)

        val after = mergeHome(decodeHome(encodeHome(before)), listOf(azahar, retroArch), platforms)
        assertEquals(listOf(azahar.id, retroArch.id), after.folders.getValue(HOME_EMULATORS).slots)
        assertFalse(after.folders.containsKey(HOME_ANDROID_GAMES))

        // The user moves it back. It stays.
        val withGame = listOf(azahar, retroArch, item("android:com.example.racer", kind = HomeKind.AndroidGame))
        val moved = fileGame(mergeHome(after, withGame, platforms), retroArch.id, HOME_ANDROID_GAMES, withGame, platforms)
        val again = mergeHome(decodeHome(encodeHome(moved)), withGame, platforms)
        assertTrue(retroArch.id in again.folders.getValue(HOME_ANDROID_GAMES).slots)
        assertFalse(retroArch.id in again.folders.getValue(HOME_EMULATORS).slots)
    }

    @Test
    fun aBoardFiledByAnOlderReleaseFilesTheEmulatorsItMissed() {
        val retroArch = item("android:com.retroarch.aarch64", kind = HomeKind.Emulator)
        val old = decodeHome(
            encodeHome(
                HomeBoard(
                    slots = listOf(HOME_ALL, HOME_ANDROID_GAMES),
                    folders = mapOf(
                        HOME_ANDROID_GAMES to HomeFolder(HOME_ANDROID_GAMES, "Android Games", bucket = HomeKind.AndroidGame, slots = listOf(retroArch.id)),
                    ),
                ),
            ).replace("filedEmulators\n", "emulatorsFiled\tMQ\n"),
        )
        val merged = mergeHome(old, listOf(retroArch), platforms)
        assertEquals(listOf(retroArch.id), merged.folders.getValue(HOME_EMULATORS).slots)
        assertFalse(merged.folders.containsKey(HOME_ANDROID_GAMES))
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
    fun aNewCollectionHoldsTheGameAndLeavesItWhereItIs() {
        val board = board(listOf(HOME_ALL, "game.cart", "game.drift"))
        val made = newCollection(board, "home.user.1", "Collection 1", member = "game.cart")
        assertEquals(listOf(HOME_ALL, "game.cart", "game.drift", "home.user.1"), made.slots)
        val collection = made.folders.getValue("home.user.1")
        assertEquals(listOf("game.cart"), collection.slots)
        assertTrue(collection.userMade)
        assertTrue(collection.collection)
        assertEquals(MARK_FOLDER, collection.mark)
        // On an empty slot it takes that slot, even one past the last tile.
        val gap = newCollection(board(listOf(HOME_ALL, null, "game.drift")), "home.user.1", "Collection 1", at = 1)
        assertEquals(listOf(HOME_ALL, "home.user.1", "game.drift"), gap.slots)
        assertEquals(emptyList<String?>(), gap.folders.getValue("home.user.1").slots)
        val beyond = newCollection(board, "home.user.1", "Collection 1", at = 4)
        assertEquals(listOf(HOME_ALL, "game.cart", "game.drift", null, "home.user.1"), beyond.slots)
        // On a taken slot it takes the first free one.
        val taken = newCollection(board(listOf(HOME_ALL, null, "game.drift")), "home.user.1", "Collection 1", at = 0)
        assertEquals(listOf(HOME_ALL, "home.user.1", "game.drift"), taken.slots)
    }

    @Test
    fun unpinTakesOnlyTheRootTileAndALoneTileHidesInstead() {
        val cart = item("game.cart", platformId = "gba")
        val loose = item("game.loose")
        val placed = pinToHome(pinToHome(mergeHome(HomeBoard(), listOf(cart, loose), platforms), cart.id), loose.id)
        val gba = platformFolderId("game-boy-advance")
        assertTrue(cart.id in placed.folders.getValue(gba).slots)
        val at = placed.slots.indexOf(cart.id)
        assertEquals(RemoveVerb.Unpin, removeVerb(placed, at))
        val unpinned = removeFromHome(placed, at)
        assertNull(unpinned.slots[at])
        assertTrue(cart.id in unpinned.folders.getValue(gba).slots)
        assertTrue(onHome(unpinned, cart.id))
        assertFalse(cart.id in unpinned.hidden)
        // A game with no folder is only on Home through its tile, so X hides it.
        val lone = placed.slots.indexOf(loose.id)
        assertEquals(RemoveVerb.Hide, removeVerb(placed, lone))
        assertTrue(loose.id in removeFromHome(placed, lone).hidden)
        // In a collection as well, its tile can go.
        val kept = newCollection(placed, "home.user.1", "Mine", member = loose.id)
        assertEquals(RemoveVerb.Unpin, removeVerb(kept, lone))
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
        // With its folder taken off Home there is nowhere on Home to return to.
        val removed = removeFolder(placed, gba)
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
