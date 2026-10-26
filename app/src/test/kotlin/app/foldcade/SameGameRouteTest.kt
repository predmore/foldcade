package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import org.junit.Assert.assertEquals
import org.junit.Test

class SameGameRouteTest {
    private val folder = Game("local-folder", "content://rf4", "nintendo-3ds", Availability.LocalOnly, "RF4")
    private val server = Game("romm", "31", "nintendo-3ds", Availability.RemoteOnly, "RF4")
    private val rank = { id: String -> listOf("romm").indexOf(id).let { if (it < 0) 99 else it } }

    @Test
    fun aCopyOnTheDevicePlaysBeforeOneThatMustDownload() {
        assertEquals(folder, routeSameGame("t", listOf(server, folder), libraryRank = rank).play)
        assertEquals(server, routeSameGame("t", listOf(server), libraryRank = rank).play)
    }

    @Test
    fun theServerKeepsTheSavesWhicheverCopyPlays() {
        assertEquals(server, routeSameGame("t", listOf(folder, server), libraryRank = rank).saves)
        // A game only in a folder keeps its saves where it always has.
        assertEquals(folder, routeSameGame("t", listOf(folder), libraryRank = rank).saves)
    }

    @Test
    fun theCopyALibraryGridPressedPlaysAndKeepsItsSaves() {
        val route = routeSameGame("t", listOf(folder, server), pressed = server, libraryRank = rank)
        assertEquals(server, route.play)
        assertEquals(server, route.saves)
        assertEquals("t", route.tileId)
        // A pressed folder copy plays, and its saves still go to the server.
        assertEquals(server, routeSameGame("t", listOf(folder, server), pressed = folder, libraryRank = rank).saves)
    }
}
