package app.foldcade.plugins.romm

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.ObservedSlot
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.StartDisplay
import app.foldcade.api.plugin.SyncOutcome
import app.foldcade.romm.RegisteredDevice
import app.foldcade.romm.RommClient
import app.foldcade.romm.RommHttpException
import app.foldcade.romm.RommResponseException
import app.foldcade.romm.RommUnavailable
import app.foldcade.romm.SaveSyncReport
import app.foldcade.romm.TRASH_DIR
import app.foldcade.romm.md5File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RommSyncTest {
    private lateinit var cache: Path
    private lateinit var data: Path
    private val ops = ScriptedOps()
    private var token: String? = "rmm_test"
    private var reader: (String) -> ByteArray? = { null }

    @Before
    fun setUp() {
        cache = Files.createTempDirectory("romm-sync-cache")
        data = Files.createTempDirectory("romm-sync-data")
    }

    @After
    fun tearDown() {
        cache.toFile().deleteRecursively()
        data.toFile().deleteRecursively()
    }

    @Test
    fun aVerifiedCachedRomStartsWithNoNetworkAndNoToken() = runBlocking {
        token = null
        cacheRom()
        ops.onDownload = { _, _, _, _, _, _ -> error("no download") }
        val target = library().ensureLocal(GAME) as LaunchTarget.ContentUri
        assertTrue(target.uri.endsWith("/roms/1234/mario.nds"))
        assertTrue(target.uri.contains("/romm/servers/${RommData.serverKey(ORIGIN)}/"))
    }

    @Test
    fun aLaunchWithRommUnreachableStillPlacesTheLocalSave() = runBlocking {
        cacheRom()
        writeSlot("sav", "progress")
        ops.onSync = { _, _, _, _ -> throw RommUnavailable("offline") }
        val placement = library().prepareLaunch(GAME, NamedPlayer)
        assertEquals(SyncOutcome.Queued, placement.sync.outcome)
        val placed = placement.savesToPlace.single()
        assertEquals("sav", placed.slot)
        assertEquals(md5("progress"), placed.contentHash)
        assertTrue(placed.contentUri.contains("/romm-data/servers/${RommData.serverKey(ORIGIN)}/placing/1234/"))
    }

    @Test
    fun aServerErrorOrAPortalPageDoesNotStopTheLaunch() = runBlocking {
        cacheRom()
        writeSlot("sav", "progress")
        for (failure in listOf(RommHttpException(502, "bad gateway"), RommResponseException("not JSON"))) {
            ops.onSync = { _, _, _, _ -> throw failure }
            val placement = library().prepareLaunch(GAME, NamedPlayer)
            assertEquals(SyncOutcome.Queued, placement.sync.outcome)
            assertEquals(1, placement.savesToPlace.size)
        }
        token = null
        val signedOut = library().prepareLaunch(GAME, NamedPlayer)
        assertEquals(SyncOutcome.Queued, signedOut.sync.outcome)
    }

    @Test
    fun whatThePlayerWroteIsKeptBeforeAnyNetworkCall() = runBlocking {
        val file = writeSlot("sav", "old")
        val rommData = RommData(data, ORIGIN)
        rommData.ledger(1234).markSynced("sav", md5("old"), "melonds")
        reader = { uri -> if (uri == "content://player/sav") "new".toByteArray() else null }
        ops.onSync = { _, _, _, _ -> throw RommUnavailable("offline") }
        val result = library().reconcile(
            GAME,
            NamedPlayer,
            ObservedSaves(listOf(ObservedSlot("sav", md5("new"), "melonds", "content://player/sav"))),
        )
        assertEquals(SyncOutcome.Queued, result.outcome)
        assertEquals("new", Files.readString(file))
        val trashed = Files.list(file.parent.resolve(TRASH_DIR)).use { it.toList() }
        assertEquals(listOf("old"), trashed.map { Files.readString(it) })
        assertTrue(rommData.ledger(1234).unsynced("sav", md5("new")))
        assertFalse(Files.list(file.parent).use { s -> s.anyMatch { it.fileName.toString().endsWith(".tmp") } })
    }

    @Test
    fun connectSyncsEachRomWithUnsyncedSavesInTheBackground() = runBlocking {
        val rommData = RommData(data, ORIGIN)
        writeSlot("sav", "changed", romId = 1234)
        rommData.ledger(1234).update("sav") { it.copy(syncedHash = md5("before"), emulator = "melonds") }
        writeSlot("sav", "same", romId = 99)
        rommData.ledger(99).markSynced("sav", md5("same"), "melonds")
        val synced = Collections.synchronizedList(mutableListOf<Pair<List<Long>, List<String>?>>())
        val done = CompletableDeferred<Unit>()
        ops.onSync = { _, _, romIds, emulators ->
            synced += romIds to emulators
            done.complete(Unit)
            report()
        }
        library().connect()
        withTimeout(5_000) { done.await() }
        assertEquals(listOf(listOf(1234L) to listOf("melonds")), synced.toList())
    }

    @Test
    fun theDeviceIsKeptAcrossRestartsWithOneHostName() = runBlocking {
        val registered = mutableListOf<RegisteredDevice?>()
        ops.onRegister = { stored, _, version ->
            registered += stored
            stored ?: RegisteredDevice("device-1", version)
        }
        library().connect()
        library().connect()
        assertEquals(listOf(null, RegisteredDevice("device-1", "0.1.0")), registered)
        assertEquals(2, ops.hostnames.size)
        assertEquals(1, ops.hostnames.distinct().size)
        assertTrue(ops.hostnames.first()!!.startsWith("foldcade-"))
        assertFalse(cacheHas("device-1"))
    }

    @Test
    fun aDeviceRommForgotIsRegisteredAgainOnce() = runBlocking {
        cacheRom()
        writeSlot("sav", "progress")
        RommData(data, ORIGIN).saveDevice(ORIGIN, RegisteredDevice("gone", "0.1.0"))
        ops.onRegister = { stored, _, version -> stored ?: RegisteredDevice("device-2", version) }
        val devices = mutableListOf<String>()
        ops.onSync = { deviceId, _, _, _ ->
            devices += deviceId
            if (deviceId == "gone") throw RommHttpException(404, "Device with ID gone not found")
            report()
        }
        val placement = library().prepareLaunch(GAME, NamedPlayer)
        assertEquals(SyncOutcome.Unchanged, placement.sync.outcome)
        assertEquals(listOf("gone", "device-2"), devices)
        assertEquals("device-2", RommData(data, ORIGIN).device(ORIGIN)?.deviceId)
    }

    @Test
    fun connectDropsTheOldCacheCopiesAndKeepsTheSaves() = runBlocking {
        val oldSave = cache.resolve("saves/1234/sav/mario.srm")
        val oldQueue = cache.resolve("upload-queue/a.bin")
        Files.createDirectories(oldSave.parent)
        Files.createDirectories(oldQueue.parent)
        Files.writeString(oldSave, "server copy")
        Files.writeString(oldQueue, "stale")
        val kept = writeSlot("sav", "progress")
        RommData(data, ORIGIN).ledger(1234).markSynced("sav", md5("progress"), "melonds")
        library().connect()
        assertFalse(Files.exists(cache.resolve("saves")))
        assertFalse(Files.exists(cache.resolve("upload-queue")))
        assertEquals("progress", Files.readString(kept))
    }

    @Test
    fun oneRomThatFailsDoesNotStopTheOthers() = runBlocking {
        val rommData = RommData(data, ORIGIN)
        writeSlot("sav", "a", romId = 7)
        writeSlot("sav", "b", romId = 8)
        val seen = Collections.synchronizedList(mutableListOf<Long>())
        val done = CompletableDeferred<Unit>()
        ops.onSync = { _, _, romIds, _ ->
            seen += romIds.single()
            if (seen.size == 2) done.complete(Unit)
            if (romIds.single() == 7L) throw IllegalStateException("local failure") else report()
        }
        library().connect()
        withTimeout(5_000) { done.await() }
        assertEquals(setOf(7L, 8L), seen.toSet())
        assertTrue(rommData.ledger(7).unsynced("sav", md5("a")))
    }

    @Test
    fun anotherServersSavesAndRomsAreNotThisServers() = runBlocking {
        cacheRom()
        writeSlot("sav", "first server")
        val other = RommWiring(
            origin = "http://other.example",
            tokenSource = RommTokenSource { token },
            cacheRoot = cache,
            dataRoot = data,
        )
        other.open = { _, _ -> ops }
        ops.onSync = { _, saves, _, _ ->
            assertTrue(saves.isEmpty())
            report()
        }
        ops.onRoms = { query -> app.foldcade.romm.RomPage(emptyList(), 0, query.limit, query.offset) }
        val library = RommLibrary(RommGate { other })
        val missing = runCatching { library.ensureLocal(GAME) }.exceptionOrNull()
        assertTrue(missing is app.foldcade.api.plugin.PluginException.NotFound)
        assertTrue(snapshotSaves(other, 1234).isEmpty())
    }

    @Test
    fun romsFromTheOldCacheLayoutMoveUnderTheCurrentServer() = runBlocking {
        val old = cache.resolve("roms/1234/mario.nds")
        Files.createDirectories(old.parent)
        Files.writeString(old, "hello-rom")
        library().connect()
        assertFalse(Files.exists(cache.resolve("roms")))
        assertEquals("hello-rom", Files.readString(romRoot().resolve("roms/1234/mario.nds")))
    }

    @Test
    fun aSaveThatCannotBeReadFailsReconcileSoTheShellKeepsIt() = runBlocking {
        val file = writeSlot("sav", "kept")
        reader = { null }
        val error = runCatching {
            library().reconcile(
                GAME,
                NamedPlayer,
                ObservedSaves(listOf(ObservedSlot("sav", md5("new"), "melonds", "content://player/sav"))),
            )
        }.exceptionOrNull()
        assertTrue(error is app.foldcade.api.plugin.PluginException.Unavailable)
        assertEquals("kept", Files.readString(file))
    }

    @Test
    fun theSameServerTypedTwoWaysIsOneServer() {
        assertEquals(RommData.serverKey("https://Romm.Example/"), RommData.serverKey("https://romm.example/api"))
        assertFalse(RommData.serverKey("https://romm.example") == RommData.serverKey("https://other.example"))
    }

    @Test
    fun oldRomsMergeIntoAServerDirectoryThatAlreadyExists() = runBlocking {
        Files.createDirectories(cache.resolve("roms/1/"))
        Files.writeString(cache.resolve("roms/1/a.nds"), "old")
        Files.createDirectories(cache.resolve("roms/2/"))
        Files.writeString(cache.resolve("roms/2/b.nds"), "old")
        Files.createDirectories(romRoot().resolve("roms/2/"))
        Files.writeString(romRoot().resolve("roms/2/b.nds"), "new")
        library().connect()
        assertEquals("old", Files.readString(romRoot().resolve("roms/1/a.nds")))
        assertEquals("new", Files.readString(romRoot().resolve("roms/2/b.nds")))
        assertFalse(Files.exists(cache.resolve("roms")))
    }

    private fun library(): RommLibrary {
        val wiring = RommWiring(
            origin = ORIGIN,
            tokenSource = RommTokenSource { token },
            cacheRoot = cache,
            dataRoot = data,
            saveBytes = SaveBytes { uri -> reader(uri) },
        )
        wiring.open = { _, _ -> ops }
        return RommLibrary(RommGate { wiring })
    }

    /** A downloaded ROM with its verified marker and file list, as a finished download leaves it. */
    private fun cacheRom() {
        val spec = RomBytes("mario.nds", emptyList(), 9, md5("hello-rom"))
        val roms = romRoot()
        val file = RommClient.romCacheFile(roms, 1234, spec.fileName, spec.fileIds)
        Files.createDirectories(file.parent)
        Files.writeString(file, "hello-rom")
        Files.writeString(
            file.resolveSibling("mario.nds.verified"),
            """{"size":9,"md5":"${md5File(file)}"}""",
        )
        writeRomSpec(roms, 1234, spec)
        assertNotNull(verifiedRom(roms, 1234, spec))
    }

    private fun romRoot(): Path = cache.resolve("servers").resolve(RommData.serverKey(ORIGIN))

    private fun writeSlot(slot: String, text: String, romId: Long = 1234): Path {
        val file = RommData(data, ORIGIN).slotFile(romId, slot)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
        return file
    }

    private fun cacheHas(needle: String): Boolean = Files.walk(cache).use { stream ->
        stream.filter { Files.isRegularFile(it) }.anyMatch { Files.readString(it).contains(needle) }
    }

    private fun report() = SaveSyncReport(
        sessionId = 1,
        serverVersion = "5.4.0",
        downloaded = emptyList(),
        uploadedSaveIds = emptyList(),
        keptBoth = emptyList(),
        deleted = emptyList(),
        queued = emptyList(),
        failed = emptyList(),
        completedSession = true,
    )

    private object NamedPlayer : Player {
        override val id = "melonds"
        override val displayName = "melonDS"
        override val platformId = "nintendo-ds"
        override val packageNames = emptyList<String>()
        override val needsLocalFile = true
        override val startDisplay = StartDisplay.Primary
        override val occupiesBothDisplays = true
        override val requiresImportedGame = false
        override fun saveDeclarations(game: Game): List<SaveDeclaration> = emptyList()
        override fun launchIntent(request: LaunchRequest): PlayerIntent = error("unused")
    }

    private companion object {
        const val ORIGIN = "http://romm.example"
        val GAME = Game(ROMM_LIBRARY_ID, "1234", "nintendo-ds", Availability.Cached, "Mario")

        fun md5(text: String): String =
            MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
