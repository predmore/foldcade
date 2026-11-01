package app.foldcade

import app.foldcade.host.play.PlayArchive
import app.foldcade.host.play.PlayClock
import app.foldcade.host.play.PlaySignal
import app.foldcade.host.play.playArtName
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A game's play moved after its ROM was renamed, kept once the game is gone, and carried
 * to another device in an export. These are what feed Recent and Most played.
 */
class PlayHistoryTest {
    private var now = 1_000_000L

    @Test
    fun movedPlayFeedsTheNewTilesTotalsAndScheduleAndUndoPutsItBack() {
        val plays = open(dir())
        play(plays, "old.gba", "Emerald", 60_000L)
        play(plays, "new.gba", "Emerald", 30_000L)

        val moves = plays.move("old.gba", "new.gba")

        assertEquals(90_000L, plays.totals("new.gba").activeMillis)
        assertEquals(0L, plays.totals("old.gba").activeMillis)
        assertEquals(setOf("new.gba"), plays.spans().map { it.gameId }.toSet())
        assertEquals(setOf("new.gba"), plays.played().keys)
        plays.undo(moves)
        assertEquals(30_000L, plays.totals("new.gba").activeMillis)
        assertEquals(60_000L, plays.totals("old.gba").activeMillis)
    }

    @Test
    fun aFoldedTilesCopiesMoveAndCountAsOne() {
        val plays = open(dir())
        plays.historyIds = { id -> if (id == "tile") setOf("tile", "copy") else setOf(id) }
        plays.tileOf = { id -> if (id == "copy") "tile" else id }
        play(plays, "tile", "Zelda", 10_000L)
        play(plays, "copy", "Zelda", 20_000L)
        assertEquals(30_000L, plays.played().getValue("tile").activeMillis)

        plays.move("tile", "renamed")

        assertEquals(30_000L, plays.totals("renamed").activeMillis)
        assertEquals(0L, plays.totals("tile").activeMillis)
        // A cover kept under a copy before the copies folded still shows for the tile.
        File(dirOf(plays), "art").apply { mkdirs() }.resolve(playArtName("copy")).writeBytes(byteArrayOf(1))
        assertEquals(playArtName("copy"), plays.keptCover("tile")?.name)
        // Moving a tile onto one of its own copies moves nothing.
        assertTrue(plays.move("renamed", "renamed").isEmpty())
    }

    @Test
    fun historyStaysUnderAGameThatLeftAndCountsAgainWhenItReturns() {
        val folder = dir()
        val plays = open(folder)
        play(plays, "lib:romm:7", "Metroid Fusion", 45_000L)
        // The catalog no longer has it; nothing in the log forgets it, and its name stays.
        val reopened = open(folder)
        assertEquals(45_000L, reopened.totals("lib:romm:7").activeMillis)
        assertEquals("Metroid Fusion", reopened.name("lib:romm:7"))
    }

    @Test
    fun anExportCarriesPlayNamesAndCoversToAnotherDeviceOnce() {
        val there = dir()
        val plays = open(there)
        play(plays, "lib:romm:7", "Metroid Fusion", 45_000L)
        play(plays, "old.gba", "Emerald", 60_000L)
        plays.move("old.gba", "new.gba")
        File(there, "art").apply { mkdirs() }.resolve(playArtName("lib:romm:7")).writeBytes(byteArrayOf(9, 8, 7))
        val file = ByteArrayOutputStream().also(plays::export).toByteArray()

        val here = open(dir())
        play(here, "lib:romm:7", "Metroid Fusion", 5_000L)
        val read = PlayArchive.read(ByteArrayInputStream(file))
        assertEquals(1, here.art.adopt(read.art))
        val merged = here.merge(read)

        assertEquals(2, merged.sessions)
        assertEquals(50_000L, here.totals("lib:romm:7").activeMillis)
        assertEquals(60_000L, here.totals("new.gba").activeMillis)
        assertEquals("Emerald", here.name("old.gba"))
        assertArrayEquals(byteArrayOf(9, 8, 7), here.art.file("lib:romm:7")!!.readBytes())
        // The same file again adds nothing.
        assertTrue(here.merge(PlayArchive.read(ByteArrayInputStream(file))).empty)
        assertEquals(0, here.art.adopt(read.art))
    }

    private fun play(plays: PlaySessions, game: String, name: String, millis: Long) {
        val id = plays.start(game, name)
        now += millis
        plays.signal(id, PlaySignal.End)
        now += 1_000L
    }

    private val folders = HashMap<PlaySessions, File>()

    private fun open(folder: File): PlaySessions = PlaySessions.open(File(folder, "events.log"), PlayClock { now }).also { folders[it] = folder }

    private fun dirOf(plays: PlaySessions): File = folders.getValue(plays)

    private fun dir(): File = Files.createTempDirectory("play-history").toFile()
}
