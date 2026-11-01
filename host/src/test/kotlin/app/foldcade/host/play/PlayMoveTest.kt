package app.foldcade.host.play

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Moving play to another game, and bringing an exported history back. */
class PlayMoveTest {
    private var now = 0L
    private var nextId = 0

    @Test
    fun movedTimeAndLastPlayedCountForTheNewGame() {
        val log = memoryLog()
        play(log, "old", 10_000)
        play(log, "old", 5_000)
        play(log, "new", 1_000)
        assertEquals(16_000L, log.totals("old").lastPlayedMillis)

        now += 60_000
        val moves = log.move(setOf("old"), "new")

        assertEquals(2, moves.size)
        assertTrue(moves.all { it.from == "old" && it.to == "new" })
        assertEquals(0L, log.totals("old").activeMillis)
        assertNull(log.totals("old").lastPlayedMillis)
        assertEquals(16_000L, log.totals("new").activeMillis)
        // The move is not a play: last played is still when the games were played.
        assertEquals(18_000L, log.totals("new").lastPlayedMillis)
        assertEquals(setOf("new"), log.played().keys)
        assertEquals(16_000L, log.played().getValue("new").activeMillis)
        // The sessions keep the game they were played as; a reader counting play sees the new one.
        assertEquals(setOf("new"), log.counted().map { it.gameId }.toSet())
        assertTrue(log.events().any { it.kind == PlayKind.Started && it.gameId == "old" })
    }

    @Test
    fun undoPutsBackOnlyWhatStillCountsWhereItWent() {
        val log = memoryLog()
        play(log, "old", 4_000)
        play(log, "old", 6_000)
        play(log, "new", 2_000)
        val moves = log.move(setOf("old"), "new")
        // One of them moves on again before the undo.
        log.move(setOf("new"), "third").let { assertEquals(3, it.size) }
        log.undo(log.move(setOf("third"), "new"))
        assertEquals(12_000L, log.totals("third").activeMillis)

        log.move(setOf("third"), "new")
        assertEquals(2, log.undo(moves))
        assertEquals(10_000L, log.totals("old").activeMillis)
        assertEquals(2_000L, log.totals("new").activeMillis)
        // Twice is nothing more.
        assertEquals(0, log.undo(moves))
    }

    @Test
    fun anOpenOrRemovedSessionDoesNotMove() {
        val log = memoryLog()
        val night = play(log, "old", 30_000)
        log.remove(night)
        play(log, "old", 3_000)
        val running = log.start("old")
        now += 2_000

        val moves = log.move(setOf("old"), "new")

        assertEquals(1, moves.size)
        assertEquals(3_000L, log.totals("new").activeMillis)
        assertEquals(2_000L, log.totals("old").activeMillis)
        log.signal(running, PlaySignal.End)
        assertEquals(2_000L, log.totals("old").activeMillis)
        // Removing a moved session takes it out of the game it went to.
        assertTrue(log.remove(moves.single().sessionId))
        assertEquals(0L, log.totals("new").activeMillis)
    }

    @Test
    fun aMoveOfSeveralCopiesLandsOnOneGame() {
        val log = memoryLog()
        play(log, "copy-a", 1_000)
        play(log, "copy-b", 2_000)
        play(log, "other", 4_000)
        log.move(setOf("copy-a", "copy-b"), "renamed")
        assertEquals(3_000L, log.totals("renamed").activeMillis)
        assertEquals(4_000L, log.totals("other").activeMillis)
        assertEquals(emptyList<PlayMove>(), log.move(setOf("renamed"), "renamed"))
    }

    @Test
    fun movesAreKeptThroughAReload() {
        val file = tempLog()
        val first = fileLog(file)
        play(first, "old", 7_000)
        first.move(setOf("old"), "new")
        val second = fileLog(file)
        assertEquals(7_000L, second.totals("new").activeMillis)
        assertEquals(0L, second.totals("old").activeMillis)
        assertEquals(first.events(), second.events())
        val moved = second.events().last()
        assertEquals(moved, decodePlayEvent(encodePlayEvent(moved).trimEnd('\n')))
    }

    @Test
    fun aBuildThatDoesNotKnowMovedCountsTheTimeWhereItWasPlayed() {
        val file = tempLog()
        val first = fileLog(file)
        play(first, "old", 7_000)
        first.move(setOf("old"), "new")
        // An older build's decoder has no Moved: the kind does not parse, so the line is skipped.
        file.writeText(file.readText().replace("\tMoved\t", "\tUnknownKind\t"))
        val older = fileLog(file)
        assertEquals(7_000L, older.totals("old").activeMillis)
        assertEquals(0L, older.totals("new").activeMillis)
        assertEquals(listOf(PlayKind.Started, PlayKind.Ended), older.events().map { it.kind })
    }

    @Test
    fun anExportLeavesOutASessionStillOpenUntilItEnds() {
        val log = memoryLog()
        play(log, "done", 2_000)
        val running = log.start("running")
        now += 3_000
        assertEquals(setOf("done"), log.finished().map { it.gameId }.toSet())
        val here = memoryLog()
        here.merge(log.finished())
        log.signal(running, PlaySignal.End)
        assertEquals(1, here.merge(log.finished()).sessions)
        assertEquals(3_000L, here.totals("running").activeMillis)
    }

    @Test
    fun aDirectoryEntryCountsAgainstTheBudgetToo() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("foldcade-play.txt")); zip.write("foldcade-play-history 1\n".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("padding/"))
            val zeros = ByteArray(1024 * 1024)
            repeat(200) { zip.write(zeros) }
            zip.closeEntry()
        }
        assertThrows(PlayArchiveException::class.java) { PlayArchive.read(ByteArrayInputStream(out.toByteArray())) }
    }

    @Test
    fun aFileThatInflatesPastItsBudgetIsRefusedEvenInEntriesItSkips() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("foldcade-play.txt")); zip.write("foldcade-play-history 1\n".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("padding.bin"))
            val zeros = ByteArray(1024 * 1024)
            repeat(200) { zip.write(zeros) }
            zip.closeEntry()
        }
        // Two hundred megabytes of zeros pack into a fraction of one.
        assertTrue(out.size() < 1024 * 1024)
        assertThrows(PlayArchiveException::class.java) { PlayArchive.read(ByteArrayInputStream(out.toByteArray())) }
    }

    @Test
    fun mergingAnExportAddsItsSessionsOnceAndKeepsItsMoves() {
        val there = memoryLog()
        play(there, "old", 5_000)
        play(there, "kept", 3_000)
        val gone = play(there, "kept", 50_000)
        there.remove(gone)
        there.move(setOf("old"), "new")

        val here = PlayLog(PlayClock { now }, MoveSink()) { "here-${nextId++}" }
        play(here, "kept", 1_000)

        val merged = here.merge(there.events())
        assertEquals(3, merged.sessions)
        assertEquals(2, merged.changes)
        assertEquals(5_000L, here.totals("new").activeMillis)
        assertEquals(0L, here.totals("old").activeMillis)
        assertEquals(4_000L, here.totals("kept").activeMillis)

        // The same file again, or this log's own events, add nothing.
        val events = here.events()
        assertTrue(here.merge(there.events()).empty)
        assertTrue(here.merge(here.events()).empty)
        assertEquals(events, here.events())
    }

    @Test
    fun reimportingKeepsAMoveTheFileMadeAfterOneThisLogAlreadyHad() {
        val there = memoryLog()
        play(there, "a", 5_000)
        val here = memoryLog()
        here.merge(there.events())
        // Both logs move the session to b, at different times; the file then moves it on to c.
        here.move(setOf("a"), "b")
        now += 1_000
        there.move(setOf("a"), "b")
        now += 1_000
        there.move(setOf("b"), "c")

        here.merge(there.events())
        assertEquals(5_000L, here.totals("c").activeMillis)
        assertTrue(here.merge(there.events()).empty)
        assertEquals(5_000L, here.totals("c").activeMillis)
        assertEquals(0L, here.totals("b").activeMillis)
    }

    @Test
    fun coversPastTheirShareAreLeftOutOfAnImport() {
        val big = ByteArray(1024 * 1024 + 1)
        val within = ByteArray(1024 * 1024)
        val names = (0 until 60).map { "%040x.webp".format(it) }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("foldcade-play.txt")); zip.write("foldcade-play-history 1\n".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("art/${"f".repeat(40)}.webp")); zip.write(big); zip.closeEntry()
            names.forEach { name -> zip.putNextEntry(ZipEntry("art/$name")); zip.write(within); zip.closeEntry() }
        }
        val archive = out.toByteArray()
        val read = PlayArchive.read(ByteArrayInputStream(archive))
        // One over a cover's own limit, and the ones past every cover's share, are not read.
        assertFalse("${"f".repeat(40)}.webp" in read.art)
        assertTrue(read.art.values.sumOf { it.size.toLong() } <= 48L * 1024 * 1024)
        assertEquals(48, read.art.size)
    }

    @Test
    fun aMergedSessionThatNeverEndedIsClosedAtItsLastEvent() {
        val there = memoryLog()
        val open = there.start("game")
        now += 4_000
        there.signal(open, PlaySignal.Background)
        now += 100_000

        val here = memoryLog()
        assertEquals(1, here.merge(there.events()).sessions)
        now += 100_000
        // It does not keep counting as if it were running here.
        assertEquals(4_000L, here.totals("game").activeMillis)
        assertEquals(PlayKind.Ended, here.events().last().kind)
        // A stray line with no start is not a session.
        assertTrue(here.merge(listOf(PlayEvent("stray", "game", PlayKind.Ended, 1L, null, 9_000L))).empty)
    }

    @Test
    fun anExportRoundTripsItsLogNamesAndArt() {
        val dir = Files.createTempDirectory("play-art").toFile()
        val cover = dir.resolve(playArtName("lib:romm:42")).apply { writeBytes(byteArrayOf(1, 2, 3)) }
        dir.resolve("notes.txt").writeText("not art")
        val log = memoryLog()
        play(log, "lib:romm:42", 2_000)
        log.move(setOf("lib:romm:42"), "lib:romm:43")

        val out = ByteArrayOutputStream()
        PlayArchive.write(out, log.events(), mapOf("lib:romm:42" to "Tetris\tDX"), dir.listFiles()!!.toList())
        val read = PlayArchive.read(ByteArrayInputStream(out.toByteArray()))

        assertEquals(log.events(), read.events)
        assertEquals(mapOf("lib:romm:42" to "Tetris\tDX"), read.names)
        assertEquals(setOf(cover.name), read.art.keys)
        assertArrayEquals(byteArrayOf(1, 2, 3), read.art.getValue(cover.name))
    }

    @Test
    fun aZipThatIsNotAPlayHistoryIsRefused() {
        val stranger = zip("events.log" to "x", "art/../../evil.webp" to "x")
        assertThrows(PlayArchiveException::class.java) { PlayArchive.read(ByteArrayInputStream(stranger)) }
        val newer = zip("foldcade-play.txt" to "foldcade-play-history 2\n")
        assertThrows(PlayArchiveException::class.java) { PlayArchive.read(ByteArrayInputStream(newer)) }
        assertThrows(Exception::class.java) { PlayArchive.read(ByteArrayInputStream("plain text".toByteArray())) }
        // Ours, with an entry outside its names, reads only what it knows.
        val ours = zip("foldcade-play.txt" to "foldcade-play-history 1\n", "art/../../evil.webp" to "x", "art/${"a".repeat(40)}.webp" to "ok")
        assertEquals(setOf("${"a".repeat(40)}.webp"), PlayArchive.read(ByteArrayInputStream(ours)).art.keys)
    }

    @Test
    fun artNamesAreHashesOfTheId() {
        val name = playArtName("lib:local-folder:content://x/document/primary%3AROMs%2Fa.gba")
        assertTrue(isPlayArtName(name))
        assertFalse(isPlayArtName("../$name"))
        assertFalse(isPlayArtName("cover.png"))
        assertEquals(name, playArtName("lib:local-folder:content://x/document/primary%3AROMs%2Fa.gba"))
    }

    @Test
    fun namesMergeWithoutReplacingOnesKeptHere() {
        val file = Files.createTempDirectory("play-names").resolve("names.log").toFile()
        val names = PlayNames(file)
        names.remember("a", "Here")
        assertEquals(1, names.merge(mapOf("a" to "There", "b" to "Imported")))
        assertEquals("Here", names.name("a"))
        assertEquals("Imported", PlayNames(file).name("b"))
        assertEquals(mapOf("a" to "Here", "b" to "Imported"), names.all())
    }

    /** One finished session of [millis] in front. Returns its id. */
    private fun play(log: PlayLog, game: String, millis: Long): String {
        val id = log.start(game)
        now += millis
        log.signal(id, PlaySignal.End)
        now += 1_000
        return id
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun memoryLog(): PlayLog = PlayLog(PlayClock { now }, MoveSink()) { "session-${nextId++}" }

    private fun fileLog(file: java.io.File): PlayLog = PlayLog(PlayClock { now }, FilePlayLog(file)) { "session-${nextId++}" }

    private fun tempLog() = Files.createTempDirectory("play-log").resolve("events.log").toFile()
}

private class MoveSink : PlaySink {
    private val rows = mutableListOf<PlayEvent>()
    private var checkpoint: PlayCheckpoint? = null

    override fun append(event: PlayEvent) {
        rows.add(event)
    }

    override fun read(): List<PlayEvent> = rows.toList()

    override fun writeCheckpoint(checkpoint: PlayCheckpoint) {
        this.checkpoint = checkpoint
    }

    override fun readCheckpoint(): PlayCheckpoint? = checkpoint

    override fun clearCheckpoint(sessionId: String) {
        if (checkpoint?.sessionId == sessionId) checkpoint = null
    }
}
