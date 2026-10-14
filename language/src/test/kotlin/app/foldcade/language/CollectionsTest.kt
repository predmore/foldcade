package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One game in several places: its folder, collections, Favorites, Recently played, and a pin. */
class CollectionsTest {
    private val platforms = listOf(
        HomePlatform("nintendo-3ds", "Nintendo 3DS", setOf("3ds")),
        HomePlatform("nintendo-ds", "Nintendo DS", setOf("nds")),
    )
    private val threeDs = platformFolderId("nintendo-3ds")
    private val ds = platformFolderId("nintendo-ds")
    private val zelda = rom("lib:local:zelda", "3ds")
    private val mario = rom("lib:local:mario", "3ds")
    private val kirby = rom("lib:local:kirby", "nds")
    private val catalog = listOf(zelda, mario, kirby)

    @Test
    fun aGameJoinsTwoCollectionsAndStaysInItsFolder() {
        var board = mergeHome(HomeBoard(), catalog, platforms)
        board = newCollection(board, "home.user.1", "Quests", member = zelda.id)
        board = newCollection(board, "home.user.2", "Couch", member = null)
        board = toggleCollection(board, "home.user.2", zelda.id)
        board = mergeHome(board, catalog, platforms)
        assertEquals(listOf(zelda.id), board.folders.getValue("home.user.1").slots)
        assertEquals(listOf(zelda.id), board.folders.getValue("home.user.2").slots)
        assertEquals(listOf(zelda.id, mario.id), board.folders.getValue(threeDs).slots)
        assertEquals(
            listOf(CollectionChoice(HOME_FAVORITES, Copy.favorites, false), CollectionChoice("home.user.1", "Quests", true), CollectionChoice("home.user.2", "Couch", true)),
            collectionChoices(board, zelda.id),
        )
        // Toggling again takes it out of that collection alone.
        val out = toggleCollection(board, "home.user.2", zelda.id)
        assertEquals(listOf(null), out.folders.getValue("home.user.2").slots)
        assertEquals(listOf(zelda.id), out.folders.getValue("home.user.1").slots)
        assertTrue(onHome(out, zelda.id))
    }

    @Test
    fun favoritesAppearsWithTheFirstFavoriteAndStaysEmpty() {
        val merged = mergeHome(HomeBoard(), catalog, platforms)
        val board = merged.copy(slots = listOf(HOME_ALL, null) + merged.slots.drop(1))
        assertFalse(HOME_FAVORITES in board.folders)
        val loved = toggleFavorite(board, kirby.id)
        // The first free root slot, which is the gap.
        assertEquals(1, loved.slots.indexOf(HOME_FAVORITES))
        assertTrue(favorite(loved, kirby.id))
        assertEquals(Copy.favorites, loved.folders.getValue(HOME_FAVORITES).name)
        assertTrue(loved.folders.getValue(HOME_FAVORITES).collection)
        val unloved = mergeHome(toggleFavorite(loved, kirby.id), catalog, platforms)
        assertFalse(favorite(unloved, kirby.id))
        assertEquals(1, unloved.slots.indexOf(HOME_FAVORITES))
        // Favorites in Add to makes it too, and the game stays in its folder.
        val chosen = toggleCollection(board, HOME_FAVORITES, mario.id)
        assertTrue(favorite(chosen, mario.id))
        assertTrue(mario.id in chosen.folders.getValue(threeDs).slots)
    }

    @Test
    fun aCollectionWithNothingInItStays() {
        val made = newCollection(mergeHome(HomeBoard(), catalog, platforms), "home.user.1", "Later")
        val again = mergeHome(made, emptyList(), platforms)
        assertTrue("home.user.1" in again.folders)
        assertTrue("home.user.1" in again.slots)
    }

    @Test
    fun aHiddenGameLeavesEveryPlaceAndComesBackToItsFolder() {
        var board = mergeHome(HomeBoard(), catalog, platforms)
        board = pinToHome(board, zelda.id)
        board = newCollection(board, "home.user.1", "Quests", member = zelda.id)
        board = toggleFavorite(board, zelda.id)
        val inFolder = openFolder(board, threeDs)
        assertEquals(RemoveVerb.Hide, removeVerb(inFolder, 0))
        val hidden = mergeHome(closeFolder(removeFromHome(inFolder, 0)), catalog, platforms)
        assertTrue(zelda.id in hidden.hidden)
        assertFalse(zelda.id in hidden.slots)
        assertTrue(hidden.folders.values.none { zelda.id in it.slots })
        assertFalse(onHome(hidden, zelda.id))
        // Add to Home in All brings it back to its folder. Collections and the pin forgot it.
        val back = mergeHome(addItemToHome(hidden, zelda, platforms), catalog, platforms)
        assertTrue(zelda.id in back.folders.getValue(threeDs).slots)
        assertFalse(zelda.id in back.slots)
        assertFalse(favorite(back, zelda.id))
        assertFalse(zelda.id in back.folders.getValue("home.user.1").slots)
    }

    @Test
    fun aGameWhoseSourceGoesLeavesEveryPlaceButWaitsWhileItsSourceLoads() {
        var board = mergeHome(HomeBoard(), catalog, platforms)
        board = pinToHome(board, zelda.id)
        board = newCollection(board, "home.user.1", "Quests", member = zelda.id)
        board = toggleFavorite(board, zelda.id)
        val loading = mergeHome(board, listOf(kirby), platforms, unsettled = { it.startsWith("lib:local:") })
        assertEquals(board.slots, loading.slots)
        assertEquals(board.folders, loading.folders)
        val gone = mergeHome(loading, listOf(kirby), platforms)
        assertFalse(zelda.id in gone.slots)
        assertTrue(gone.folders.values.none { zelda.id in it.slots })
        // The 3DS folder went with its last game. The collections stay, empty.
        assertFalse(threeDs in gone.folders)
        assertTrue("home.user.1" in gone.folders)
        assertTrue(HOME_FAVORITES in gone.folders)
    }

    @Test
    fun foldersHoldEveryGameOnHomeWithoutMovingOrMakingAnything() {
        val board = HomeBoard(
            slots = listOf(HOME_ALL, threeDs, mario.id, "home.user.1"),
            folders = mapOf(
                threeDs to HomeFolder(threeDs, "Nintendo 3DS", platformId = "nintendo-3ds", slots = listOf(null, "lib:local:other", null)),
                "home.user.1" to HomeFolder("home.user.1", "Mix", userMade = true, slots = listOf(zelda.id, kirby.id)),
            ),
        )
        val other = rom("lib:local:other", "3ds")
        val merged = mergeHome(board, catalog + other, platforms)
        // After the last game, in the empty slot there; the hole in front stays.
        assertEquals(listOf(null, other.id, mario.id, zelda.id), merged.folders.getValue(threeDs).slots)
        // No DS folder is made for Kirby, and nothing on the root moves.
        assertFalse(ds in merged.folders)
        assertEquals(board.slots, merged.slots)
        assertEquals(listOf(zelda.id, kirby.id), merged.folders.getValue("home.user.1").slots)
        assertEquals(merged, mergeHome(merged, catalog + other, platforms))
    }

    @Test
    fun anAppTheUserMovedToTheOtherAndroidFolderStaysThere() {
        val notes = HomeItem("android:notes", "Notes", HomeKind.AndroidApp)
        val board = HomeBoard(
            slots = listOf(HOME_ALL, HOME_ANDROID_GAMES, HOME_ANDROID_APPS, notes.id),
            folders = mapOf(
                HOME_ANDROID_GAMES to HomeFolder(HOME_ANDROID_GAMES, "Android Games", bucket = HomeKind.AndroidGame, slots = listOf(notes.id)),
                HOME_ANDROID_APPS to HomeFolder(HOME_ANDROID_APPS, "Android Apps", bucket = HomeKind.AndroidApp, slots = listOf("android:other")),
            ),
        )
        val merged = mergeHome(board, listOf(notes, HomeItem("android:other", "Other", HomeKind.AndroidApp)), platforms)
        assertEquals(listOf(notes.id), merged.folders.getValue(HOME_ANDROID_GAMES).slots)
        assertEquals(listOf("android:other"), merged.folders.getValue(HOME_ANDROID_APPS).slots)
    }

    @Test
    fun aRetiredFolderStaysRetiredWhileItsGamesArePinned() {
        // As an older release left it: the folder deleted, its games spilled onto the root.
        val board = HomeBoard(
            slots = listOf(HOME_ALL, zelda.id, mario.id),
            retired = setOf(threeDs),
        )
        val merged = mergeHome(board, catalog, platforms)
        assertFalse(threeDs in merged.slots)
        assertTrue(threeDs in merged.retired)
        assertFalse(threeDs in merged.folders)
        assertEquals(listOf(HOME_ALL, zelda.id, mario.id, ds), merged.slots)
    }

    @Test
    fun movingInsideACollectionOrAFolderReordersOnlyIt() {
        var board = mergeHome(HomeBoard(), catalog, platforms)
        board = newCollection(board, "home.user.1", "Quests", member = zelda.id)
        board = addToCollection(board, "home.user.1", mario.id)
        val inCollection = openFolder(board, "home.user.1")
        val moved = drop(moveHeld(pickUp(inCollection, 0), 1, catalog, platforms), 1)
        assertEquals(listOf(mario.id, zelda.id), moved.folders.getValue("home.user.1").slots)
        assertEquals(listOf(zelda.id, mario.id), moved.folders.getValue(threeDs).slots)
        assertEquals(board.slots, moved.slots)
        val inFolder = openFolder(board, threeDs)
        val shuffled = drop(moveHeld(pickUp(inFolder, 0), 1, catalog, platforms), 1)
        assertEquals(listOf(mario.id, zelda.id), shuffled.folders.getValue(threeDs).slots)
        assertEquals(listOf(zelda.id, mario.id), shuffled.folders.getValue("home.user.1").slots)
    }

    @Test
    fun aPinCarriedOntoACollectionDropsInAndOntoItsFolderGoesHome() {
        var board = mergeHome(HomeBoard(), catalog, platforms)
        board = newCollection(board, "home.user.1", "Quests")
        board = pinToHome(board, zelda.id)
        val pin = board.slots.indexOf(zelda.id)
        val collection = board.slots.indexOf("home.user.1")
        val dropped = moveHeld(pickUp(board, pin), collection, catalog, platforms)
        assertNull(dropped.hold)
        assertNull(dropped.slots[pin])
        assertEquals(listOf(zelda.id), dropped.folders.getValue("home.user.1").slots)
        assertTrue(zelda.id in dropped.folders.getValue(threeDs).slots)
        // Onto its own folder the pin goes, and the folder keeps it once.
        val home = moveHeld(pickUp(board, pin), board.slots.indexOf(threeDs), catalog, platforms)
        assertNull(home.slots[pin])
        assertEquals(listOf(zelda.id, mario.id), home.folders.getValue(threeDs).slots)
    }

    @Test
    fun anIdListedTwiceShowsOnce() {
        val board = HomeBoard(
            slots = listOf(HOME_ALL, zelda.id, zelda.id, "home.user.1"),
            folders = mapOf("home.user.1" to HomeFolder("home.user.1", "Mix", userMade = true, slots = listOf(kirby.id, kirby.id))),
        )
        assertEquals(listOf(HOME_ALL, zelda.id, null, "home.user.1"), visibleSlots(board))
        assertEquals(listOf(kirby.id, null), visibleSlots(openFolder(board, "home.user.1")))
        val merged = mergeHome(board, catalog, platforms)
        assertEquals(1, merged.slots.count { it == zelda.id })
        assertEquals(listOf(kirby.id, null), merged.folders.getValue("home.user.1").slots)
    }

    @Test
    fun whatXDoesDependsOnWhereTheGameIs() {
        var board = mergeHome(HomeBoard(), catalog, platforms)
        board = newCollection(board, "home.user.1", "Quests", member = zelda.id)
        board = pinToHome(board, zelda.id)
        board = mergeHome(board, catalog + rom("lib:local:played", "3ds", 50L), platforms)
        assertNull(removeVerb(board, board.slots.indexOf(HOME_ALL)))
        assertEquals(RemoveVerb.Unpin, removeVerb(board, board.slots.indexOf(zelda.id)))
        assertEquals(RemoveVerb.Remove, removeVerb(board, board.slots.indexOf(threeDs)))
        assertEquals(RemoveVerb.Remove, removeVerb(board, board.slots.indexOf("home.user.1")))
        assertEquals(RemoveVerb.Remove, removeVerb(board, board.slots.indexOf(HOME_RECENT)))
        assertEquals(RemoveVerb.Remove, removeVerb(openFolder(board, "home.user.1"), 0))
        assertEquals(RemoveVerb.Hide, removeVerb(openFolder(board, threeDs), 0))
        assertNull(removeVerb(openFolder(board, HOME_RECENT), 0))
        assertNull(removeVerb(board, 40))
        // Out of the collection on screen, the game stays everywhere else.
        val out = removeFromHome(openFolder(board, "home.user.1"), 0)
        assertEquals(listOf(null), out.folders.getValue("home.user.1").slots)
        assertTrue(zelda.id in out.slots)
        assertFalse(zelda.id in out.hidden)
    }

    @Test
    fun recentlyPlayedListsTheTwelveNewestAndSkipsHiddenGamesAndApps() {
        val played = (1..14).map { rom("lib:local:g$it", "3ds", lastPlayed = it * 10L) }
        val app = HomeItem("android:notes", "Notes", HomeKind.AndroidApp, lastPlayedMillis = 999L)
        val board = mergeHome(HomeBoard(hidden = setOf("lib:local:g14")), played + app, platforms)
        val recent = board.folders.getValue(HOME_RECENT)
        assertEquals((13 downTo 2).map { "lib:local:g$it" }, recent.slots)
        assertEquals(RECENT_COUNT, recent.slots.size)
        assertEquals(Copy.recentlyPlayed, recent.name)
        assertEquals(MARK_RECENT, recent.mark)
        assertFalse(recent.collection)
        assertFalse(recent.system)
        // Opened like a folder, nothing in it lifts, and a pin does not drop into it.
        val open = openFolder(board, HOME_RECENT)
        assertEquals(recent.slots, visibleSlots(open))
        assertNull(pickUp(open, 0).hold)
        assertFalse(folderTakes(recent, played.first(), platforms))
        // It reflects play, so it does not put a game on Home.
        val onlyRecent = HomeBoard(slots = listOf(HOME_ALL, HOME_RECENT), folders = mapOf(HOME_RECENT to recent))
        assertFalse(onHome(onlyRecent, "lib:local:g13"))
    }

    @Test
    fun recentlyPlayedJoinsTheRootAfterANewPlayAndStaysOffOnceRemoved() {
        val old = rom("lib:local:old", "3ds", lastPlayed = 100L)
        val folder = HomeFolder(threeDs, "Nintendo 3DS", platformId = "nintendo-3ds", slots = listOf(old.id))
        val saved = encodeHome(HomeBoard(slots = listOf(HOME_ALL, null, threeDs), folders = mapOf(threeDs to folder)))
            .lines().filter { !it.startsWith("recentAfter") }.joinToString("\n")
        // A board from before collections: plays before the update add no tile.
        val updated = mergeHome(decodeHome(saved, nowMillis = 500L), listOf(old), platforms)
        assertEquals(500L, updated.recentAfter)
        assertFalse(HOME_RECENT in updated.folders)
        assertEquals(500L, decodeHome(encodeHome(updated), nowMillis = 900L).recentAfter)
        // A play after it does, in the first free slot.
        val now = mergeHome(updated, listOf(old.copy(lastPlayedMillis = 600L)), platforms)
        assertTrue(HOME_RECENT in now.folders)
        assertEquals(1, now.slots.indexOf(HOME_RECENT))
        // Taken off Home, it stays off.
        val removed = removeFolder(now, HOME_RECENT)
        assertTrue(HOME_RECENT in removed.retired)
        val later = mergeHome(removed, listOf(old.copy(lastPlayedMillis = 700L)), platforms)
        assertFalse(HOME_RECENT in later.slots)
        assertFalse(HOME_RECENT in later.folders)
        // A board with no saved Home starts fresh, so its first play shows it.
        assertTrue(HOME_RECENT in mergeHome(decodeHome(null, nowMillis = 500L), listOf(old), platforms).folders)
    }

    @Test
    fun recentlyPlayedKeepsAGameWhoseSourceIsStillLoading() {
        val first = rom("lib:romm:a", "3ds", lastPlayed = 300L)
        val second = rom("lib:local:b", "3ds", lastPlayed = 200L)
        val third = rom("lib:local:c", "3ds", lastPlayed = 100L)
        val board = mergeHome(HomeBoard(), listOf(first, second, third), platforms)
        assertEquals(listOf(first.id, second.id, third.id), board.folders.getValue(HOME_RECENT).slots)
        val loading = mergeHome(board, listOf(second, third), platforms, unsettled = { it.startsWith("lib:romm:") })
        assertEquals(listOf(first.id, second.id, third.id), loading.folders.getValue(HOME_RECENT).slots)
        val gone = mergeHome(loading, listOf(second, third), platforms)
        assertEquals(listOf(second.id, third.id), gone.folders.getValue(HOME_RECENT).slots)
    }

    @Test
    fun aShelfAppIsOfferedOnlyPlacesItIsNotYet() {
        val notes = HomeItem("android:notes", "Notes", HomeKind.AndroidApp)
        var board = mergeHome(HomeBoard(), listOf(notes, HomeItem("android:game", "Game", HomeKind.AndroidGame)), platforms)
        board = newCollection(board, "home.user.1", "Tools")
        assertEquals(listOf(null, HOME_ANDROID_GAMES, "home.user.1"), placementChoices(board, notes, platforms).map { it.folderId })
        board = fileGame(board, notes.id, "home.user.1", listOf(notes), platforms)
        board = pinToHome(board, notes.id)
        assertEquals(listOf(HOME_ANDROID_GAMES), placementChoices(board, notes, platforms).map { it.folderId })
        // Filed in the other Android folder, it leaves its old one.
        val moved = fileGame(board, notes.id, HOME_ANDROID_GAMES, listOf(notes), platforms)
        assertTrue(notes.id in moved.folders.getValue(HOME_ANDROID_GAMES).slots)
        assertFalse(notes.id in moved.folders.getValue(HOME_ANDROID_APPS).slots)
        assertTrue(notes.id in moved.folders.getValue("home.user.1").slots)
        assertTrue(notes.id in moved.slots)
    }

    private fun rom(id: String, platform: String, lastPlayed: Long = 0L): HomeItem =
        HomeItem(id = id, title = id.substringAfterLast(':'), kind = HomeKind.Rom, platformId = platform, lastPlayedMillis = lastPlayed)
}
