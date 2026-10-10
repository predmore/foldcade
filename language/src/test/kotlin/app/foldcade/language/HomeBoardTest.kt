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
        assertTrue(merged.folders.containsKey(HOME_ANDROID_GAMES))
        assertTrue(merged.folders.containsKey(HOME_ANDROID_APPS))
        assertTrue(merged.folders.containsKey(HOME_GAMENATIVE))
        assertTrue(merged.folders.containsKey(HOME_MOONLIGHT))
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
    fun aHiddenGameKeepsItsPlatformFolder() {
        val drift = item("game.drift", platformId = "nds")
        val folderId = platformFolderId("nintendo-ds")
        val placed = mergeHome(HomeBoard(), listOf(drift), platforms)
        val hidden = placed.copy(
            folders = placed.folders + (folderId to placed.folders.getValue(folderId).copy(slots = listOf(null))),
            hidden = setOf("game.drift"),
        )
        val again = mergeHome(hidden, listOf(drift), platforms)
        assertTrue(again.folders.containsKey(folderId))
        assertTrue(again.slots.contains(folderId))
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
