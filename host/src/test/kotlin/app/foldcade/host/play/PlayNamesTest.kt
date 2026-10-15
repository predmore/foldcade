package app.foldcade.host.play

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayNamesTest {
    private val documentId = "lib:local-folder:content://com.android.externalstorage.documents/tree/" +
        "primary%3AROMs/document/primary%3AROMs%2Fgba%2FPok%C3%A9mon%20-%20Emerald%20Version%20(USA%2C%20Europe).gba"

    @Test
    fun aNameSurvivesReopeningUnderADocumentUriId() {
        val file = namesFile()
        val names = PlayNames(file)
        assertTrue(names.remember(documentId, "Pokémon - Emerald Version"))
        assertEquals("Pokémon - Emerald Version", names.name(documentId))

        val reopened = PlayNames(file)
        assertEquals("Pokémon - Emerald Version", reopened.name(documentId))
    }

    @Test
    fun aRenameWinsAndAnUnchangedNameIsNotWrittenAgain() {
        val file = namesFile()
        val names = PlayNames(file)
        names.remember("lib:romm:42", "Tetris")
        assertFalse(names.remember("lib:romm:42", "  Tetris "))
        assertTrue(names.remember("lib:romm:42", "Tetris DX"))
        assertEquals("Tetris DX", names.name("lib:romm:42"))

        val reopened = PlayNames(file)
        assertEquals("Tetris DX", reopened.name("lib:romm:42"))
        assertEquals(2, file.readLines().size)
    }

    @Test
    fun anIdOrBlankOrUriIsNotKeptAsAName() {
        val file = namesFile()
        val names = PlayNames(file)
        assertFalse(names.remember("lib:romm:42", "   "))
        assertFalse(names.remember("lib:romm:42", "lib:romm:42"))
        assertFalse(names.remember(documentId, "content://com.android.externalstorage.documents/document/primary%3Ax.gba"))
        assertFalse(names.remember(" ", "Tetris"))
        assertNull(names.name("lib:romm:42"))
        assertNull(names.name(documentId))
        assertFalse(file.exists() && file.readText().isNotEmpty())
    }

    @Test
    fun tabsAndLineBreaksInANameRoundTrip() {
        val file = namesFile()
        PlayNames(file).remember("odd\tid", "Line one\nline\\two\tend")
        assertEquals("Line one\nline\\two\tend", PlayNames(file).name("odd\tid"))
    }

    @Test
    fun tornUnknownAndMalformedLinesAreSkipped() {
        val file = namesFile()
        file.parentFile.mkdirs()
        file.writeText(
            listOf(
                "1\tgood\tGood Game",
                "",
                "2\tfuture\tFrom a later schema",
                "1\tmissing-name",
                "1\textra\tName\tfield",
                "1\t \tNo id",
                "1\tblank\t   ",
                "1\turi\tcontent://provider/document/x",
                "x\tbad\tVersion",
                "1\ttorn\tEnds in an escape\\",
                "1\tgood\tGood Game Renamed",
                "1\tlast\tA write cut sh",
            ).joinToString("\n"),
        )
        val names = PlayNames(file)
        assertEquals("Good Game Renamed", names.name("good"))
        listOf("future", "missing-name", "extra", " ", "blank", "uri", "bad", "torn", "last").forEach {
            assertNull(it, names.name(it))
        }

        // The next write cuts the torn line off before it adds its own.
        assertTrue(names.remember("next", "Next Game"))
        val reopened = PlayNames(file)
        assertEquals("Next Game", reopened.name("next"))
        assertEquals("Good Game Renamed", reopened.name("good"))
        assertNull(reopened.name("last"))
        assertTrue(file.readText().endsWith("1\tgood\tGood Game Renamed\n1\tnext\tNext Game\n"))
    }

    @Test
    fun noFileIsNoNames() {
        val names = PlayNames(namesFile())
        assertNull(names.name("anything"))
    }

    @Test
    fun theEventLineFormatIsUnchanged() {
        val event = PlayEvent("s", documentId, PlayKind.Started, 1_000, null, 0)
        assertEquals("1\ts\t$documentId\tStarted\t1000\t-\t0\n", encodePlayEvent(event))
        assertEquals(event, decodePlayEvent("1\ts\t$documentId\tStarted\t1000\t-\t0"))
        assertNull(decodePlayEvent(encodePlayName(documentId, "Emerald").trimEnd()))
    }

    private fun namesFile(): File = Files.createTempDirectory("play-names").resolve("play-sessions/names.log").toFile()
}
