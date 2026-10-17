package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import org.junit.Assert.assertEquals
import org.junit.Test

class SameGameRouteTest {
    private val folder = Game("local-folder", "content://rf4", "nintendo-3ds", Availability.LocalOnly, "RF4")
    private val server = Game("romm", "31", "nintendo-3ds", Availability.RemoteOnly, "RF4")
    private val second = Game("romm#2", "8", "nintendo-3ds", Availability.Cached, "RF4")
    private val rank = { id: String -> listOf("romm", "romm#2").indexOf(id).let { if (it < 0) 99 else it } }

    @Test
    fun aCopyOnTheDevicePlaysBeforeOneThatMustDownload() {
        assertEquals(folder, routeSameGame("t", listOf(server, second, folder), libraryRank = rank).play)
        assertEquals(second, routeSameGame("t", listOf(server, second), libraryRank = rank).play)
        assertEquals(server, routeSameGame("t", listOf(server), libraryRank = rank).play)
    }

    @Test
    fun theFirstServerKeepsTheSavesWhicheverCopyPlays() {
        assertEquals(server, routeSameGame("t", listOf(folder, second, server), libraryRank = rank).saves)
        assertEquals(second, routeSameGame("t", listOf(folder, second), libraryRank = rank).saves)
        // A game only in a folder keeps its saves where it always has.
        assertEquals(folder, routeSameGame("t", listOf(folder), libraryRank = rank).saves)
    }

    @Test
    fun theCopyALibraryGridPressedPlays() {
        val route = routeSameGame("t", listOf(folder, server), pressed = server, libraryRank = rank)
        assertEquals(server, route.play)
        assertEquals(server, route.saves)
        assertEquals("t", route.tileId)
    }
}
