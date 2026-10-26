package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.GridStrip
import app.foldcade.language.HostScreen
import app.foldcade.language.Meaning
import app.foldcade.language.homeGameId
import app.foldcade.language.platformFolderId
import app.foldcade.language.sameGameId
import app.foldcade.language.sameKey
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A game a folder and a RomM server both have is one tile, whichever lists it first. */
class SameGameHomeTest {
    private val prefs = MemoryPrefs()
    private val store = SessionStore(prefs).apply { setRommOrigin("https://romm.example") }
    private val local = homeGameId("local-folder", RF4_URI)
    private val server = homeGameId("romm", "31")
    private val tile = sameGameId(sameKey("nintendo-3ds", RF4_FILE)!!)

    @Test
    fun aFolderGameAndItsServerCopyAreOneTileEitherWay() {
        for (serverFirst in listOf(false, true)) {
            val shell = shell(SessionStore(MemoryPrefs()).apply { setRommOrigin("https://romm.example") })
            if (serverFirst) listServer(shell) else listFolder(shell)
            if (serverFirst) listFolder(shell) else listServer(shell)
            shell.openHomeAll()
            assertEquals(listOf("Mario", "Rune Factory 4"), titles(shell).filter { it.isNotEmpty() }.sorted())
            assertEquals(1, titles(shell).count { it == "Rune Factory 4" })
        }
    }

    @Test
    fun theTileKeepsThePlacesOfBothCopies() {
        val shell = shell()
        listFolder(shell)
        val folder = platformFolderId("nintendo-3ds")
        assertTrue(local in folderSlots(shell, folder))
        listServer(shell)
        assertEquals(listOf(tile), folderSlots(shell, folder).filter { it == tile || it == local || it == server })
        assertEquals(mapOf(local to tile, server to tile), store.sameGames())
    }

    @Test
    fun aRestartKeepsTheTileBeforeAnyLibraryListsAgain() {
        listBoth(shell())
        val again = shell()
        assertTrue(tile in folderSlots(again, platformFolderId("nintendo-3ds")))
        // The folder scan settles first, alone, and the tile stays.
        listFolder(again)
        assertTrue(tile in folderSlots(again, platformFolderId("nintendo-3ds")))
        assertTrue(local !in folderSlots(again, platformFolderId("nintendo-3ds")))
    }

    @Test
    fun removingTheServerKeepsTheTileOnItsFolderCopy() {
        val shell = shell()
        listBoth(shell)
        shell.dropLibrary("romm")
        assertTrue(tile in folderSlots(shell, platformFolderId("nintendo-3ds")))
        assertFalse(shell.onServer(tile))
        assertEquals(listOf("local-folder"), shell.launchRoute(tile)?.let { listOf(it.play.backendId, it.saves.backendId).distinct() })
    }

    @Test
    fun aServerNoLongerSavedDoesNotHoldItsTileAfterARestart() {
        val shell = shell()
        listFolder(shell)
        shell.ingestLibrary(
            "romm",
            listOf(entry("31", RF4_FILE, "romm", Availability.RemoteOnly, RF4_FILE)),
            emptyList(),
        )
        assertEquals(mapOf(local to tile, "lib:romm:31" to tile), store.sameGames())
        // After signing out, a restart lists the folder without the game.
        store.clearRommOrigin()
        val again = shell()
        again.ingestLibrary("local-folder", listOf(entry("other", "Other", "local-folder", Availability.LocalOnly, "Other.cci")), emptyList())
        assertFalse(tile in folderSlots(again, platformFolderId("nintendo-3ds")))
    }

    @Test
    fun theFolderCopyPlaysAndTheServerKeepsTheSaves() {
        val shell = shell()
        listBoth(shell, serverAvailability = Availability.RemoteOnly)
        val route = shell.launchRoute(tile)!!
        assertEquals(tile, route.tileId)
        assertEquals("local-folder", route.play.backendId)
        assertEquals("romm", route.saves.backendId)
        assertFalse(shell.onServer(tile))
        // Nothing to delete: the server's copy is not downloaded.
        assertNull(shell.downloadFor(tile))
    }

    @Test
    fun deleteDownloadNamesTheServerCopyAndTheTileStays() {
        val shell = shell()
        listBoth(shell, serverAvailability = Availability.Cached)
        shell.openHomeAll()
        assertEquals(server, shell.downloadFor(tile)?.let { homeGameId(it.backendId, it.remoteKey) })
        shell.noteOffDevice("romm", "31")
        assertNull(shell.downloadFor(tile))
        assertFalse(shell.onServer(tile))
    }

    @Test
    fun playKeptUnderEitherCopyCountsOnTheTile() {
        val shell = shell()
        listBoth(shell)
        assertEquals(setOf(tile, local, server), shell.historyIds(tile))
        assertEquals(tile, shell.tileOf(local))
        assertEquals("lib:romm:7", shell.tileOf("lib:romm:7"))
        assertEquals(setOf("lib:romm:7"), shell.historyIds("lib:romm:7"))
        assertEquals(listOf(server, local), shell.sameGroups().getValue(tile))
    }

    @Test
    fun differentFilesStayTwoGames() {
        val shell = shell()
        listFolder(shell)
        shell.ingestLibrary("romm", listOf(entry("31", "Rune Factory 4", "romm", Availability.RemoteOnly, "Rune Factory 4 (Europe).cci")), emptyList())
        shell.openHomeAll()
        assertEquals(2, titles(shell).count { it == "Rune Factory 4" })
        assertTrue(store.sameGames().isEmpty())
    }

    private fun listBoth(shell: ShellController, serverAvailability: Availability = Availability.RemoteOnly) {
        listFolder(shell)
        listServer(shell, serverAvailability)
    }

    private fun listFolder(shell: ShellController) {
        shell.ingestLibrary(
            "local-folder",
            listOf(entry(RF4_URI, "Rune Factory 4", "local-folder", Availability.LocalOnly, RF4_FILE)),
            emptyList(),
        )
    }

    private fun listServer(shell: ShellController, availability: Availability = Availability.RemoteOnly) {
        shell.ingestLibrary(
            "romm",
            listOf(
                entry("31", RF4_FILE, "romm", availability, RF4_FILE),
                entry("7", "Mario", "romm", Availability.RemoteOnly, "Mario.cci"),
            ),
            emptyList(),
        )
    }

    private fun entry(key: String, title: String, backendId: String, availability: Availability, fileName: String) = GridEntry(
        id = key,
        title = title,
        shortText = "",
        platformId = "nintendo-3ds",
        availabilityLabel = null,
        occupiesBothDisplays = false,
        game = Game(backendId, key, "nintendo-3ds", availability, title),
        fileName = fileName,
    )

    private fun folderSlots(shell: ShellController, folderId: String): List<String?> {
        shell.goHome()
        val at = titles(shell).indexOf("Nintendo 3DS")
        assertTrue("no 3DS folder in ${titles(shell)}", at >= 0)
        focus(shell, at)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        return List(shell.model.count) { shell.homeFace(it)?.id }.also { shell.goHome() }
    }

    private fun focus(shell: ShellController, index: Int) {
        val at = shell.model.focus.cellIndex
        val columns = GridStrip.column(index) - GridStrip.column(at)
        repeat(maxOf(columns, 0)) { shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom) }
        repeat(maxOf(-columns, 0)) { shell.onMeaning(Meaning.MoveLeft, HostScreen.Bottom) }
        val rows = GridStrip.row(index) - GridStrip.row(shell.model.focus.cellIndex)
        repeat(maxOf(rows, 0)) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        repeat(maxOf(-rows, 0)) { shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom) }
    }

    private fun titles(shell: ShellController): List<String> =
        List(shell.model.count) { index -> shell.homeFace(index)?.title.orEmpty() }

    private fun shell(session: SessionStore = store): ShellController =
        ShellController(session, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))

    private companion object {
        const val RF4_FILE = "Rune Factory 4 (USA).cci"
        const val RF4_URI = "content://com.android.externalstorage.documents/tree/primary%3AROMs/document/primary%3AROMs%2F3ds%2FRune%20Factory%204%20(USA).cci"
    }
}
