package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GamePage
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.ListedPlatform
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.PLUGIN_API_MINOR
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.PlacedSave
import app.foldcade.api.plugin.Placement
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.SaveSet
import app.foldcade.api.plugin.StartDisplay
import app.foldcade.api.plugin.SyncOutcome
import app.foldcade.api.plugin.SyncResult
import app.foldcade.host.PluginHost
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A folder copy that plays keeps its saves on the server copy of the same file. */
class LaunchSavesTest {
    private val folder = RecordingLibrary("folder")
    private val server = RecordingLibrary("server")
    private val docs = Documents()
    private val marks = HashMap<String, String>()
    private val saves = PlayerSaves(docs, Marks(marks), Files.createTempDirectory("launch-saves"))
    private val awaiting = mutableListOf<String>()
    private val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()).apply {
        register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val apiMinor = PLUGIN_API_MINOR
            override val libraries: List<LibraryBackend> = listOf(folder, server)
        })
    }
    private val play = Game("folder", "content://rf4", "nintendo-ds", Availability.LocalOnly, "Tetris")
    private val owner = Game("server", "12", "nintendo-ds", Availability.RemoteOnly, "Tetris")
    private val local = LaunchTarget.ContentUri("content://rf4")

    init {
        docs.names["content://rf4"] = "Tetris DS (USA).nds"
    }

    @Test
    fun aSaveTheFolderCopyMadeGoesToTheServerFirst() = runBlocking {
        docs.files["$SAVES/Tetris DS (USA).sav"] = "played in the folder".toByteArray()
        server.toPlace = "server copy"
        val prepared = LaunchSaves(saves, plugins) { awaiting += it }.prepare(GameRoute("tile", play, owner), Melon, local)
        assertEquals(listOf("reconcile:12", "prepareSaves:12"), server.calls)
        assertEquals(emptyList<String>(), folder.calls)
        assertEquals(md5Hex("played in the folder".toByteArray()), server.observed.single())
        // The library's copy, now one it has seen, is what the player opens.
        assertEquals("server copy", String(docs.files.getValue("$SAVES/Tetris DS (USA).sav")))
        assertEquals(local, prepared.target)
        assertEquals(listOf("melonds"), awaiting)
        // Read back later under the server copy, not the folder's.
        assertTrue(saves.changedSince("server", owner, Melon, prepared.spots).slots.isEmpty())
    }

    @Test
    fun aCopyThatKeepsItsOwnSavesLaunchesAsBefore() = runBlocking {
        LaunchSaves(saves, plugins) { awaiting += it }.prepare(GameRoute("tile", owner, owner), Melon, local)
        assertEquals(listOf("prepareLaunch:12"), server.calls)
    }

    private class RecordingLibrary(override val id: String) : LibraryBackend {
        override val displayName = id
        val calls = mutableListOf<String>()
        val observed = mutableListOf<String>()
        var toPlace: String? = null

        override suspend fun connect() = Unit
        override suspend fun disconnect() = Unit
        override suspend fun listPlatforms(): List<ListedPlatform> = emptyList()
        override suspend fun listGames(platformId: String, query: GameQuery) = GamePage(emptyList(), null)
        override suspend fun ensureLocal(game: Game): LaunchTarget = error("no download")
        override suspend fun saves(game: Game) = SaveSet(id, emptyList())

        override suspend fun prepareLaunch(game: Game, player: Player): Placement {
            calls += "prepareLaunch:${game.remoteKey}"
            return Placement(LaunchTarget.ContentUri("content://own"), placed())
        }

        override suspend fun prepareSaves(game: Game, player: Player, target: LaunchTarget): Placement {
            calls += "prepareSaves:${game.remoteKey}"
            return Placement(target, placed())
        }

        override suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves): SyncResult {
            calls += "reconcile:${game.remoteKey}"
            this.observed += observed.slots.map { it.contentHash }
            return SyncResult(SyncOutcome.Queued)
        }

        private fun placed(): List<PlacedSave> =
            toPlace?.let { listOf(PlacedSave("sav", "lib://$id", md5Hex(it.toByteArray()))) }.orEmpty()
    }

    private inner class Documents : SaveDocuments {
        val names = HashMap<String, String>()
        val files = HashMap<String, ByteArray>()

        override fun displayName(uri: String): String? = names[uri]
        override fun find(folderUri: String, name: String): String? = "$folderUri/$name".takeIf { it in files }
        override fun create(folderUri: String, name: String): String = "$folderUri/$name".also { files[it] = ByteArray(0) }
        override fun read(uri: String): ByteArray? =
            if (uri.startsWith("lib://")) server.toPlace?.toByteArray() else files[uri]
        override fun delete(uri: String): Boolean = files.remove(uri) != null
        override fun write(uri: String, bytes: ByteArray) {
            files[uri] = bytes
        }
    }

    private class Marks(private val stored: HashMap<String, String>) : SaveMarks {
        override fun mark(key: String): String? = stored[key]
        override fun setMark(key: String, hash: String) {
            stored[key] = hash
        }
        override fun clearMark(key: String) {
            stored.remove(key)
        }
    }

    private object Melon : Player {
        override val id = "melonds"
        override val displayName = "melonDS"
        override val platformId = "nintendo-ds"
        override val packageNames = emptyList<String>()
        override val needsLocalFile = true
        override val startDisplay = StartDisplay.Primary
        override val occupiesBothDisplays = true
        override val requiresImportedGame = false
        override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("sav", SAVES))
        override fun saveFileName(slot: String, romFileName: String): String = romFileName.replaceAfterLast('.', "sav")
        override fun launchIntent(request: LaunchRequest): PlayerIntent = error("unused")
    }

    private companion object {
        const val SAVES = "content://tree/saves"
    }
}
