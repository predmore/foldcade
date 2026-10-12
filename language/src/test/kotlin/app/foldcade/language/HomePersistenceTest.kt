package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertEquals(listOf(HOME_ALL, null, "game.a"), decodeHome(encodeHome(drop(moving, 2))).slots)
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
}
