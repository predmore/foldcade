package app.foldcade.plugins.droiddeck

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SyncOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DroidDeckLibraryTest {
    private val hades = DroidDeckGame("1145350", "Hades II")
    private val added = DroidDeckGame("18446744069448138752", "Celeste (itch)")

    @Test
    fun theHandedOverGamesAreTheCatalog() = runBlocking {
        val library = DroidDeckLibrary()
        library.replaceGames(listOf(hades, added, DroidDeckGame("1145350", "Hades 2 file")))
        assertEquals(listOf(hades, added), library.knownGames())
        val page = library.listGames(DroidDeckPlatform.ID, GameQuery())
        assertEquals(listOf("Hades II", "Celeste (itch)"), page.games.map { it.label })
        assertEquals(listOf("1145350", "18446744069448138752"), page.games.map { it.remoteKey })
        assertTrue(page.games.all { it.platformId == DroidDeckPlatform.ID && it.availability == Availability.LocalOnly })
        assertEquals(2, page.total)
        assertNull(page.nextOffset)
    }

    @Test
    fun searchAndPagesAndOtherPlatforms() = runBlocking {
        val library = DroidDeckLibrary()
        library.replaceGames(listOf(hades, added))
        assertEquals(listOf("Celeste (itch)"), library.listGames(DroidDeckPlatform.ID, GameQuery(text = "cel")).games.map { it.label })
        val first = library.listGames(DroidDeckPlatform.ID, GameQuery(limit = 1))
        assertEquals(1, first.nextOffset)
        assertNull(library.listGames(DroidDeckPlatform.ID, GameQuery(offset = 1, limit = 1)).nextOffset)
        assertTrue(library.listGames("pc", GameQuery()).games.isEmpty())
    }

    @Test
    fun launchHandsTheGameIdAndSyncIsANoOp() = runBlocking {
        val library = DroidDeckLibrary()
        library.replaceGames(listOf(added))
        val game = gameOf(added.remoteKey)
        val expected = LaunchTarget.AppRef(mapOf(DroidDeckPlayer.GAME_ID to added.gameId))
        assertEquals(expected, library.ensureLocal(game))
        assertEquals(expected, library.prepareLaunch(game, DroidDeckPlayer()).target)
        assertTrue(library.saves(game).slots.isEmpty())
        val result = library.reconcile(game, DroidDeckPlayer(), ObservedSaves(emptyList()))
        assertEquals(SyncOutcome.Unchanged, result.outcome)
    }

    @Test
    fun aGameItDoesNotHaveIsNotFound() = runBlocking {
        val library = DroidDeckLibrary()
        library.replaceGames(listOf(hades))
        for (game in listOf(gameOf("620"), gameOf(hades.remoteKey).copy(backendId = "gamenative"))) {
            try {
                library.ensureLocal(game)
                fail("expected NotFound")
            } catch (_: PluginException.NotFound) {
                Unit
            }
        }
    }

    @Test
    fun aGameNeedsAValidIdAndATitle() {
        assertEquals(DroidDeckGame("620", "620"), droidDeckGame(" 620 ", "  "))
        assertEquals("Portal 2", droidDeckGame("620", "Portal\n2")?.title)
        assertNull(droidDeckGame("0620", "Portal 2"))
        assertNull(droidDeckGame(null, "Portal 2"))
    }

    private fun gameOf(remoteKey: String) = Game(
        backendId = DroidDeckLibrary.ID,
        remoteKey = remoteKey,
        platformId = DroidDeckPlatform.ID,
        availability = Availability.LocalOnly,
        label = "",
    )
}
