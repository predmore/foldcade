package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class RommCachedHomeTest {
    private val pinned = homeGameId("romm", "42")
    private val hidden = homeGameId("romm", "43")
    private val offHome = homeGameId("romm", "44")
    private val collected = homeGameId("romm", "45")
    private val collection = HomeFolder("home.collection.1", "Mine", userMade = true, slots = listOf(collected, pinned))
    private val entries = (42..46).map { id ->
        val game = Game("romm", id.toString(), "snes", Availability.RemoteOnly, "Game $id")
        GridEntry(game.remoteKey, game.label, "", game.platformId, null, false, game)
    }

    @Test fun theFirstOfflineStartKeepsUncataloguedGamesAsRommWaitingTiles() {
        val (_, shell) = fixture()
        shell.openHomeFolder(collection.id)
        val face = shell.homeFace(0)!!
        assertEquals(collected, face.id)
        assertEquals("Waiting for RomM", face.title)
        assertEquals("cloud", face.mark)
        assertTrue(face.waitingForRomm)
        assertFalse(face.empty)
        assertFalse(shell.focusedLaunchesOnOneScreen())
        assertEquals(PlayKey.Play, shell.focusedPlayKey())
        val hero = (shell.focusedHero() as HeroSubject.Item).item
        assertEquals(face.title, hero.title)
        assertEquals("cloud", hero.mark)
        assertNull(shell.launchRoute(collected))
        assertNull(shell.artQuery(shell.findGame(collected)))
        // Pressing A still uses the existing catalog recovery/launch path; the view stays in its collection.
        shell.beginCatalogWait(collected)
        shell.endCatalogWait(collected)
        assertEquals("Waiting for RomM", (shell.focusedHero() as HeroSubject.Item).item.title)
        val record = entries.first { it.game!!.remoteKey == "45" }
        shell.ingestLibrary("romm", entries, emptyList())
        assertEquals(record.title, shell.homeFace(0)!!.title)
        assertFalse(shell.homeFace(0)!!.waitingForRomm)
        assertNotNull(shell.launchRoute(collected))
    }

    @Test fun aBoardThatKeptTheGamesNameUsesItAboveTheWaitingState() {
        val board = HomeBoard(slots = listOf(pinned), ranks = mapOf(pinned to TileRank("Super Metroid")))
        val home = HomeSession(null).apply { restore(board) }
        val face = home.faceOf(pinned)
        assertEquals("Super Metroid", face.title)
        assertEquals("Waiting for RomM", face.shortText)
        assertEquals("cloud", face.mark)
        assertTrue(face.waitingForRomm)
        assertTrue(home.asShelf(face).waitingForRomm)
        // Internal ids or blank old names never become player-facing labels.
        for (name in listOf(pinned, " ")) {
            val unknown = HomeSession(null).apply { restore(board.copy(ranks = mapOf(pinned to TileRank(name)))) }.faceOf(pinned)
            assertEquals("Waiting for RomM", unknown.title)
        }
    }

    @Test fun anEarlySnapshotKeepsStartupPlacementsUntilTheCompleteListingReportsMissingGames() {
        val (store, shell) = fixture()
        shell.ingestLibrary("romm", listOf(entries.last()), emptyList(), restoreReported = false, settles = false)
        assertPlacements(store)
        assertNotNull("Snapshot games are already available", shell.saveGame("romm", "46"))
        // A complete live or offline listing retains the existing removal behavior.
        shell.ingestLibrary("romm", listOf(entries.last()), emptyList())
        val reported = decodeHome(store.homeBoardRaw())
        assertFalse(pinned in reported.slots)
        assertFalse(hidden in reported.hidden)
        assertFalse(offHome in reported.offHome)
        assertTrue(reported.folders.getValue(collection.id).slots.none { it == collected || it == pinned })
    }

    @Test fun aStaleSnapshotKeepsReconnectRecordsAndDoesNotLoseEditsWhenLiveGamesReturn() {
        val (store, shell) = fixture()
        shell.ingestLibrary("romm", entries, emptyList())
        assertPlacements(store)
        shell.ingestLibrary("romm", listOf(entries.last()), emptyList(), restoreReported = false, settles = false)
        assertPlacements(store)
        // Merely skipping settledLibraries += would still erase these already-settled records.
        for (id in 42..46) assertNotNull(shell.saveGame("romm", id.toString()))
        shell.ingestLibrary("romm", entries, emptyList())
        assertPlacements(store)
    }

    private fun assertPlacements(store: SessionStore) {
        val board = decodeHome(store.homeBoardRaw())
        assertEquals(pinned, board.slots[1])
        assertTrue(hidden in board.hidden)
        assertTrue(offHome in board.offHome)
        assertEquals(collection.slots, board.folders.getValue(collection.id).slots)
    }

    private fun fixture(): Pair<SessionStore, ShellController> {
        val board = HomeBoard(
            slots = listOf(HOME_ALL, pinned, collection.id),
            folders = mapOf(collection.id to collection),
            hidden = setOf(hidden), offHome = setOf(offHome),
        )
        val prefs = MemoryPrefs().apply {
            edit().putString("romm_origin", "https://romm.example").putString("home_board", encodeHome(board))
                .putString("restore_holds", "{}").commit()
        }
        val store = SessionStore(prefs)
        return store to ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
    }
}
