package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameActivity
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.api.plugin.SaveFolderAccess
import app.foldcade.api.plugin.SaveFolderEntry
import app.foldcade.host.PluginHost
import app.foldcade.language.TileActivity
import app.foldcade.language.activityLine
import app.foldcade.language.homeGameId
import app.foldcade.plugins.retroarch.CoreChoice
import app.foldcade.plugins.retroarch.RetroArchEntry
import app.foldcade.plugins.retroarch.RetroArchPlayer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Combined core/BIOS preparation uses the same launch ticket, progress surfaces and file writers as Home. */
class RetroArchPreparationTest {
    @get:Rule val temp = TemporaryFolder()
    private val pkg = "com.retroarch.aarch64"
    private val game = Game("local-folder", "disc", "sega-cd", Availability.LocalOnly, "Disc (USA)")
    private val id = homeGameId(game.backendId, game.remoteKey)
    private val player = (RetroArchEntry().also { it.cores.update(mapOf(pkg to emptySet())) }
        .players.single { it.platformId == game.platformId } as RetroArchPlayer)
    private val core get() = (player.coreChoice(pkg) as CoreChoice.Missing).wanted
    private val bios = ByteArray(131072) { (it % 251).toByte() } // Generated test data, not a console dump.

    private class Documents(bytes: ByteArray) : SaveDocuments {
        val files = linkedMapOf("source" to bytes)
        var source: () -> java.io.InputStream = { ByteArrayInputStream(bytes) }
        override fun read(uri: String) = files[uri]
        override fun head(uri: String, size: Int) = files[uri]?.take(size)?.toByteArray()
        override fun displayName(uri: String) = uri.substringAfterLast('/')
        override fun find(folderUri: String, name: String) = "$folderUri/$name".takeIf { it in files }
        override fun create(folderUri: String, name: String) = "$folderUri/$name".also { files[it] = byteArrayOf() }
        override fun write(uri: String, bytes: ByteArray) { files[uri] = bytes }
        override fun delete(uri: String) = files.remove(uri) != null
        override fun copyFirmware(source: String, target: String): Boolean {
            val output = ByteArrayOutputStream()
            this.source().use { copyFirmwareBytes(it, output) }
            files[target] = output.toByteArray()
            return true
        }
        override fun renameFirmware(uri: String, leaf: String) = "system/$leaf".also {
            check(it !in files)
            files[it] = files.remove(uri)!!
        }
        fun folder() = object : SaveFolderAccess {
            override fun list(path: String) = files.filterKeys { it.startsWith("system/") }
                .map { (name, bytes) -> SaveFolderEntry(name.substringAfter('/'), false, bytes.size.toLong()) }
            override fun read(path: String, limit: Int) = files["system/$path"]?.take(limit)?.toByteArray()
            override fun read(path: String, offset: Long, length: Int) = files["system/$path"]?.let {
                it.drop(offset.toInt()).take(length).toByteArray()
            }
            override fun write(path: String, bytes: ByteArray) = false
            override fun createFolder(path: String) = false
            override fun moveToBackup(path: String) = false
        }
    }

    private fun downloader(): RetroArchCoreDownload {
        // A checked network response in the normal retry cache exercises the real ZIP/ELF validation without HTTP.
        val folder = File(temp.root, "arm64-v8a/${core.file}").apply { mkdirs() }
        val elf = ByteArray(128).apply {
            byteArrayOf(0x7f, 69, 76, 70, 2, 1, 1).copyInto(this)
            this[16] = 3; this[18] = 183.toByte()
        }
        ZipOutputStream(File(folder, "core.zip").outputStream()).use {
            it.putNextEntry(ZipEntry(core.fileName)); it.write(elf); it.closeEntry()
        }
        return RetroArchCoreDownload { temp.root }
    }

    private fun shell() = ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))

    @Test fun rommAdviceFollowsTheFirmwareOwnerRatherThanAnySavedConnection() {
        assertFalse(canUseRommFirmware(game.backendId, connected = true))
        assertFalse(canUseRommFirmware(app.foldcade.plugins.romm.ROMM_LIBRARY_ID, connected = false))
        val server = game.copy(backendId = app.foldcade.plugins.romm.ROMM_LIBRARY_ID, availability = Availability.Cached)
        val folded = GameRoute(id, play = game, saves = server)
        assertTrue(canUseRommFirmware(folded.saves.backendId, connected = true))
        assertFalse(canUseRommFirmware(folded.play.backendId, connected = true))
    }

    @Test fun missingCoreThenBiosHaveDistinctProgressAndOneFinalHandoff() = runBlocking {
        val work = LaunchWork()
        val ticket = work.begin()
        val ui = shell()
        val docs = Documents(bios)
        val owner = Any()
        assertEquals(listOf("bios_CD_U.bin"), player.missingFirmware(game, docs.folder()))
        val getting = downloader()
        val lines = mutableListOf<String>()
        val preparing = launch {
            assertTrue(work.prepare(ticket) {
                ui.noteCoreDownload(id, owner, core.libraryName, null)
                lines += activityLine(ui.activity.getValue(id))
                try { work.retroArchCore = getting.prepare(pkg, core, RetroArchAbi.Arm64) { _, _ -> } }
                finally { ui.endCoreDownload(id, owner) }
                true
            })
            assertNotNull(work.prepareFiles(ticket, game,
                saves = { PreparedLaunch(LaunchTarget.ContentUri("content://disc"), emptyList(), game) },
                firmware = { target, backendId ->
                    assertEquals("content://disc", (target as LaunchTarget.ContentUri).uri)
                    assertEquals(game.backendId, backendId)
                    assertNotNull(work.retroArchCore)
                    assertFalse(ui.activity.containsKey(id))
                    ui.beginDownload(game.backendId, game.remoteKey)
                    ui.noteActivity(game.backendId, game.remoteKey, GameActivity.GettingFirmware(0, null), 0)
                    assertEquals(TileActivity.Downloading(null, firmware = true), ui.activity[id])
                    lines += activityLine(ui.activity.getValue(id))
                    try { runInterruptible(Dispatchers.IO) { publishNewFirmware(docs, "system", "bios_CD_U.bin", "source", md5Hex(bios), bios) } }
                    finally { ui.endDownload(game.backendId, game.remoteKey); ui.settleActivity(game.backendId, game.remoteKey) }
                },
            ))
            assertTrue(player.missingFirmware(game, docs.folder()).isEmpty())
            val intent = player.launchIntent(LaunchRequest(game, LaunchTarget.SharedFile("/sdcard/disc.chd", "content://disc"), pkg))
            assertEquals(CORE_SIDELOAD_ACTIVITY, work.retroArchCore!!.sideload(intent).componentClass)
        }
        work.job = preparing
        preparing.join()
        assertEquals(listOf("Getting Genesis Plus GX…", "Getting firmware"), lines)
        assertArrayEquals(bios, docs.files["system/bios_CD_U.bin"])
        assertFalse(ui.activity.containsKey(id))
    }

    @Test fun oneCancelStopsEitherPhaseAndNeverStartsTheNextOne() = runBlocking {
        for (cancelCore in listOf(true, false)) {
            val work = LaunchWork()
            val ticket = work.begin()
            val ui = shell()
            val reached = CompletableDeferred<Unit>()
            val owner = Any()
            var biosStarted = false
            var launched = false
            val preparing = launch {
                if (!work.prepare(ticket) {
                    ui.noteCoreDownload(id, owner, core.libraryName, null)
                    try {
                        if (cancelCore) { reached.complete(Unit); CompletableDeferred<Unit>().await() }
                        work.retroArchCore = PreparedRetroArchCore(pkg, core, File("core.so"))
                        true
                    } finally { ui.endCoreDownload(id, owner) }
                }) return@launch
                if (work.prepareFiles(ticket, game,
                    saves = { PreparedLaunch(LaunchTarget.ContentUri("content://disc"), emptyList(), game) },
                    firmware = { _, _ ->
                        biosStarted = true
                        ui.beginDownload(game.backendId, game.remoteKey)
                        ui.noteActivity(game.backendId, game.remoteKey, GameActivity.GettingFirmware(0, null), 0)
                        reached.complete(Unit)
                        try { CompletableDeferred<Unit>().await(); true }
                        finally { ui.endDownload(game.backendId, game.remoteKey); ui.settleActivity(game.backendId, game.remoteKey) }
                    },
                ) == null) return@launch
                launched = true
            }
            work.job = preparing
            reached.await()
            work.cancel() // The shared B/shoulder cancel from either display.
            preparing.join()
            assertTrue(preparing.isCancelled)
            assertEquals(!cancelCore, biosStarted)
            assertFalse(launched)
            assertNull(work.retroArchCore)
            assertFalse(work.current(ticket))
            assertFalse(ui.activity.containsKey(id))
        }
    }

    @Test fun cancellingInsideBiosTransferDeletesTheUnpublishedFile() = runBlocking {
        val docs = Documents(bios)
        val reading = CountDownLatch(1)
        docs.source = {
            object : ByteArrayInputStream(bios) {
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                    if (pos > 0) { reading.countDown(); CountDownLatch(1).await() }
                    return super.read(bytes, offset, length)
                }
            }
        }
        val work = LaunchWork()
        val ticket = work.begin()
        val preparing = launch {
            work.prepare(ticket) { runInterruptible(Dispatchers.IO) { publishNewFirmware(docs, "system", "bios_CD_U.bin", "source", md5Hex(bios), bios) } }
        }
        work.job = preparing
        assertTrue(withContext(Dispatchers.IO) { reading.await(5, TimeUnit.SECONDS) })
        work.cancel()
        withTimeout(5000) { preparing.join() }
        assertTrue(preparing.isCancelled)
        assertEquals(setOf("source"), docs.files.keys)
    }

    @Test fun anInterruptAfterReadStopsBeforeWritingThatChunk() {
        val output = ByteArrayOutputStream()
        val source = object : ByteArrayInputStream(bios) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                super.read(bytes, offset, length).also { Thread.currentThread().interrupt() }
        }
        try {
            copyFirmwareBytes(source, output)
            fail("cancelled copy wrote a BIOS")
        } catch (_: CancellationException) {
            assertEquals(0, output.size())
        } finally { Thread.interrupted() }
    }
}
