package app.foldcade

import app.foldcade.api.plugin.*
import app.foldcade.host.PluginHost
import app.foldcade.language.SaveDestination
import app.foldcade.language.SavePickerTarget
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ServerSaveFlowTest {
    private val root = Files.createTempDirectory("server-save-flow")
    private val events = mutableListOf<String>()
    private val marks = mutableMapOf<String, String>()
    private val docs = Documents()
    private val saves = PlayerSaves(docs, object : SaveMarks {
        override fun mark(key: String) = marks[key]
        override fun setMark(key: String, hash: String) { marks[key] = hash }
        override fun clearMark(key: String) { marks.remove(key) }
    }, root.resolve("backups"), root.resolve("handover"))
    private val library = Library()
    private val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()).apply {
        register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val apiMinor = PLUGIN_API_MINOR
            override val libraries = listOf(library)
        })
    }
    private val player = object : Player {
        override val id = "melonds"
        override val displayName = "melonDS"
        override val platformId = "nintendo-ds"
        override val packageNames = emptyList<String>()
        override val needsLocalFile = true
        override val startDisplay = StartDisplay.Primary
        override val occupiesBothDisplays = true
        override val requiresImportedGame = false
        override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("sav", "content://folder"))
        override fun saveFileName(slot: String, romFileName: String) = "Game.sav"
        override fun launchIntent(request: LaunchRequest): PlayerIntent = error("unused")
    }
    private val game = Game("server", "original", "nintendo-ds", Availability.Cached, "Original game")
    private val target = SavePickerTarget("original-tile", "server", "original", player.id)
    private val request = SavePickerRequest(target, GameRoute("original-tile", game, game), game, player)
    private val flow = ServerSaveFlow(saves, plugins) { events += "await-load:$it" }
    private var onChoose: () -> Unit = {}

    @After fun cleanup() { root.toFile().deleteRecursively() }

    @Test fun capturesCurrentProgressAndMarksFreshLoadBeforeChoosingAndPlacing() = runBlocking {
        docs.current = "current progress".toByteArray()
        flow.perform(request, "chosen-id")
        assertEquals(listOf("local:original", "capture:original", "await-load:melonds", "choose:original:chosen-id", "place"), events)
        assertEquals("chosen", String(docs.current!!))
        assertEquals(md5Hex("current progress".toByteArray()), library.observed.single().contentHash)
        assertTrue(saves.changedSince(game.backendId, game, player, listOf(SaveSpot("sav", "content://folder", "Game.sav"))).slots.isEmpty())
        val backup = Files.walk(root.resolve("backups")).use { it.filter(Files::isRegularFile).toList() }
        assertEquals(listOf("current progress"), backup.map { Files.readString(it) })
    }

    @Test fun anUnreadableSaveOrFolderRefusesBeforeAnyPromotion() = runBlocking {
        docs.current = "current progress".toByteArray()
        docs.readable = false
        assertTrue(runCatching { flow.perform(request, "chosen-id") }.exceptionOrNull() is SaveNotPlaced)
        assertEquals(listOf("local:original"), events)
        assertEquals("current progress", String(docs.current!!))
        docs.readable = true
        docs.folderReadable = false
        events.clear()
        assertTrue(runCatching { flow.perform(request, "chosen-id") }.exceptionOrNull() is SaveNotPlaced)
        assertEquals(listOf("local:original"), events)
    }

    @Test fun aVerifiedMissingSaveCanTakeItsFirstServerCopy() = runBlocking {
        assertNull(docs.current)
        flow.perform(request, "chosen-id")
        assertEquals("chosen", String(docs.current!!))
        assertFalse(events.any { it.startsWith("capture") })
    }

    @Test fun aFileThatChangesDuringTheNetworkRequestIsKeptAndTheLaunchRefuses() = runBlocking {
        docs.current = "current progress".toByteArray()
        onChoose = { docs.current = "newer outside progress".toByteArray() }
        assertTrue(runCatching { flow.perform(request, "chosen-id") }.exceptionOrNull() is SaveNotPlaced)
        assertEquals("newer outside progress", String(docs.current!!))
        assertTrue(events.contains("await-load:melonds"))
    }

    @Test fun aSyncRefusalLeavesThePlayerFileAsItWasAndKeepsTheFreshLoadMarker() = runBlocking {
        docs.current = "current progress".toByteArray()
        onChoose = { throw PluginException.Unavailable("offline") }
        assertNotNull(runCatching { flow.perform(request, "chosen-id") }.exceptionOrNull())
        assertEquals("current progress", String(docs.current!!))
        assertTrue(events.indexOf("await-load:melonds") < events.indexOf("choose:original:chosen-id"))
        assertFalse(events.contains("place"))
    }

    @Test fun foreignCopiesCaptureProgressButDoNotReplaceThePlayerFileOrSetFreshLoad() = runBlocking {
        docs.current = "current progress".toByteArray()
        flow.perform(request, "chosen-id", SaveDestination("autosave", "argosy", "Steam Deck"))
        assertEquals(listOf("local:original", "capture:original", "copy:original:chosen-id:autosave:argosy"), events)
        assertEquals("current progress", String(docs.current!!))
    }

    @Test fun copyingIntoThePlayersOwnSlotAlsoPlacesAndMarksFreshLoad() = runBlocking {
        docs.current = "current progress".toByteArray()
        flow.perform(request, "chosen-id", SaveDestination("sav", "melonds", "This device"))
        assertEquals("chosen", String(docs.current!!))
        assertTrue(events.indexOf("await-load:melonds") < events.indexOf("copy:original:chosen-id:sav:melonds"))
    }

    @Test fun syncNowCapturesBeforeTheStrictSyncWithoutReplacingThePlayerFile() = runBlocking {
        docs.current = "current progress".toByteArray()
        flow.perform(request, null)
        assertEquals(listOf("local:original", "capture:original", "sync:original"), events)
        assertEquals("current progress", String(docs.current!!))
    }

    @Test fun heldProgressCanSyncAfterItsPlayerIsUninstalled() = runBlocking {
        flow.perform(request.copy(player = null), null)
        assertEquals(listOf("sync:original"), events)
        assertNull(docs.current)
    }

    private inner class Library : LibraryBackend {
        override val id = "server"
        override val displayName = "Server"
        val observed = mutableListOf<ObservedSlot>()
        override suspend fun connect() = Unit
        override suspend fun disconnect() = Unit
        override suspend fun listPlatforms(): List<ListedPlatform> = emptyList()
        override suspend fun listGames(platformId: String, query: GameQuery) = GamePage(emptyList(), null)
        override suspend fun ensureLocal(game: Game): LaunchTarget { events += "local:${game.remoteKey}"; return LaunchTarget.ContentUri("content://rom") }
        override suspend fun saves(game: Game) = SaveSet(id, emptyList())
        override suspend fun prepareLaunch(game: Game, player: Player) = error("Must not use launch sync to capture")
        override suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves): SyncResult {
            events += "capture:${game.remoteKey}"; this.observed += observed.slots; return SyncResult(SyncOutcome.Queued)
        }
        override suspend fun syncServerSaves(game: Game, player: Player?): SyncResult { events += "sync:${game.remoteKey}"; return SyncResult(SyncOutcome.Unchanged) }
        override suspend fun playServerSave(game: Game, player: Player, saveId: String): List<PlacedSave> {
            events += "choose:${game.remoteKey}:$saveId"; onChoose(); return placed()
        }
        override suspend fun copyServerSave(game: Game, saveId: String, toSlot: String, toEmulator: String?, player: Player?): List<PlacedSave> {
            events += "copy:${game.remoteKey}:$saveId:$toSlot:$toEmulator"; onChoose(); return if (toSlot == "sav") placed() else emptyList()
        }
        private fun placed() = listOf(PlacedSave("sav", "content://chosen", md5Hex("chosen".toByteArray())))
    }

    private inner class Documents : SaveDocuments {
        var current: ByteArray? = null
        var readable = true
        var folderReadable = true
        override fun displayName(uri: String) = "Game.nds"
        override fun find(folderUri: String, name: String) = "content://current".takeIf { current != null }
        override fun create(folderUri: String, name: String) = "content://current"
        override fun read(uri: String) = if (uri == "content://chosen") "chosen".toByteArray() else current.takeIf { readable }
        override fun write(uri: String, bytes: ByteArray) { events += "place"; current = bytes }
        override fun delete(uri: String): Boolean { current = null; return true }
        override fun children(folderUri: String, path: String): List<SaveChild>? = if (!folderReadable) null else if (current == null) emptyList() else listOf(SaveChild("Game.sav", "content://current", false, current!!.size.toLong()))
    }
}
