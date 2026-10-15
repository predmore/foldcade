package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.PlayCopy
import app.foldcade.language.homeGameId
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayNameTest {
    /** A ROM picked through the storage picker, as the folder library keys it. */
    private val emerald = "content://com.android.externalstorage.documents/tree/primary%3AROMs/document/" +
        "primary%3AROMs%2Fgba%2FPok%C3%A9mon%20-%20Emerald%20Version%20(USA%2C%20Europe).gba"

    /** The same kind of key on an SD card, whose volume id is not "primary". */
    private val zelda = "content://com.android.externalstorage.documents/tree/1234-ABCD%3AGames/document/" +
        "1234-ABCD%3AGames%2Fsnes%2FLegend%20of%20Zelda%2C%20The%20-%20A%20Link%20to%20the%20Past%20(USA).sfc"

    @Test
    fun aDocumentUriReadsAsItsGamesTitle() {
        assertEquals("Pokémon - Emerald Version", playName(homeGameId("local-folder", emerald), null, null))
        assertEquals("The Legend of Zelda - A Link to the Past", playName(homeGameId("local-folder", zelda), null, null))
        assertEquals("loose", playName("content://tree/document/primary%3Aloose.nds", null, null))
    }

    @Test
    fun olderHistoryUnderABareDocumentUriReadsTheSame() {
        assertEquals("Pokémon - Emerald Version", playName(emerald, null, null))
        assertEquals("The Legend of Zelda - A Link to the Past", playName(zelda, null, null))
    }

    @Test
    fun anIdWithNothingReadableIsARemovedGameNeverTheId() {
        val ids = listOf(
            "lib:romm:1234",
            "1234",
            "android:com.example.game",
            "nintendo-ds.melonds",
            "lib:",
            "lib:local-folder:",
            // Cut short inside a percent escape.
            "content://com.android.externalstorage.documents/tree/primary%3AROMs/document/primary%3AROMs%2Fgba%2F%E0%A4%A",
            "content://com.android.externalstorage.documents/tree/primary%3AROMs/document/primary%3AROMs%2Fgba%2F",
            "content://com.android.externalstorage.documents/document/",
        )
        for (id in ids) assertEquals(id, PlayCopy.removedGame, playName(id, null, null))
    }

    @Test
    fun theCatalogNameWinsSoARenameShows() {
        assertEquals("Emerald (Renamed)", playName(emerald, "Emerald (Renamed)", "Pokémon Emerald"))
        assertEquals("Pokémon Emerald", playName(emerald, "  ", "Pokémon Emerald"))
    }

    @Test
    fun aRememberedNameWinsOverOneReadFromTheId() {
        assertEquals("Pokémon Emerald", playName(emerald, null, "Pokémon Emerald"))
        assertEquals("Tetris DX", playName("lib:romm:1234", null, "Tetris DX"))
        assertEquals(PlayCopy.removedGame, playName("lib:romm:1234", null, " "))
    }

    @Test
    fun aStartedGameKeepsItsNameAfterReopening() {
        val file = eventsFile()
        val id = homeGameId("romm", "1234")
        val plays = PlaySessions.open(file)
        val session = plays.start(id, "Tetris DX")
        plays.signal(session, app.foldcade.host.play.PlaySignal.End)
        assertEquals("Tetris DX", plays.name(id))

        val reopened = PlaySessions.open(file)
        assertEquals("Tetris DX", reopened.name(id))
        assertEquals(2, reopened.events().size)
        // The event stream stays as it was: seven fields and no name.
        assertTrue(file.readLines().all { it.split('\t').size == 7 && "Tetris" !in it })
        assertTrue(File(file.parentFile, "names.log").isFile)
    }

    @Test
    fun aCatalogNameSeenOnThePageIsKept() {
        val file = eventsFile()
        PlaySessions.open(file).rememberName(emerald, "Pokémon Emerald")
        assertEquals("Pokémon Emerald", PlaySessions.open(file).name(emerald))
    }

    @Test
    fun historyWrittenBeforeNamesStillLoads() {
        val file = eventsFile()
        file.parentFile.mkdirs()
        file.writeText(
            "1\ts1\t$emerald\tStarted\t1000\t-\t0\n" +
                "1\ts1\t$emerald\tEnded\t61000\t-\t60000\n",
        )
        val plays = PlaySessions.open(file)
        assertEquals(60_000L, plays.totals(emerald).activeMillis)
        assertEquals(emerald, plays.spans().single().gameId)
        assertNull(plays.name(emerald))
        assertFalse(File(file.parentFile, "names.log").exists())
        assertEquals("Pokémon - Emerald Version", playName(emerald, null, plays.name(emerald)))
    }

    @Test
    fun anExternalLaunchKeepsTheNameItShowed() {
        val plays = PlaySessions.open(eventsFile())
        ExternalPlay().open(plays, homeGameId("local-folder", emerald), "Pokémon Emerald", 2) {}
        assertEquals("Pokémon Emerald", plays.name(homeGameId("local-folder", emerald)))
    }

    @Test
    fun aBareKeyFromOlderHistoryTakesTheHomeGamesName() {
        val shell = shell()
        shell.ingestLibrary("local-folder", listOf(entry(emerald, "Pokémon - Emerald Version")), emptyList())
        assertEquals("Pokémon - Emerald Version", shell.playTitle(homeGameId("local-folder", emerald)))
        assertEquals("Pokémon - Emerald Version", shell.playTitle(emerald))

        shell.ingestLibrary("local-folder", listOf(entry(emerald, "Emerald Hack")), emptyList())
        assertEquals("Emerald Hack", shell.playTitle(emerald))
    }

    @Test
    fun aBareKeyTwoLibrariesShareNamesNeither() {
        val shell = shell()
        shell.ingestLibrary("local-folder", listOf(entry("12", "Folder Twelve")), emptyList())
        shell.ingestLibrary("romm", listOf(entry("12", "Server Twelve", backendId = "romm")), emptyList())
        assertNull(shell.playTitle("12"))
        assertEquals("Server Twelve", shell.playTitle(homeGameId("romm", "12")))
    }

    @Test
    fun aGameGoneFromTheCatalogsKeepsItsRememberedName() {
        val shell = shell()
        val id = homeGameId("local-folder", emerald)
        shell.ingestLibrary("local-folder", listOf(entry(emerald, "Pokémon Emerald")), emptyList())
        val plays = PlaySessions.open(eventsFile())
        plays.start(id, shell.playTitle(id))

        // The folder was forgotten: nothing Foldcade lists has the game now.
        val later = shell()
        assertNull(later.playTitle(id))
        assertEquals("Pokémon Emerald", playName(id, later.playTitle(id), plays.name(id)))
    }

    private fun eventsFile(): File =
        Files.createTempDirectory("play-names").resolve("play-sessions/events.log").toFile()

    private fun shell(): ShellController =
        ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))

    private fun entry(key: String, title: String, backendId: String = "local-folder"): GridEntry = GridEntry(
        id = key,
        title = title,
        shortText = "",
        platformId = "game-boy-advance",
        availabilityLabel = null,
        occupiesBothDisplays = false,
        game = Game(
            backendId = backendId,
            remoteKey = key,
            platformId = "game-boy-advance",
            availability = Availability.LocalOnly,
            label = title,
        ),
    )
}
