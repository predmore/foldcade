package app.foldcade.plugins.romm

import app.foldcade.api.plugin.*
import app.foldcade.romm.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ServerSavesTest {
    private val root = Files.createTempDirectory("server-save-picker")
    private val data = RommData(root.resolve("data"), ORIGIN)
    private val ops = ScriptedOps()
    private val events = mutableListOf<String>()
    private val server = mutableListOf<RommSave>()
    private val bytes = mutableMapOf<Long, ByteArray>()
    private var nextId = 10L
    private var failAfterUpload = false
    private var pruneSlot = false
    private var token: String? = "rmm_test"
    private val player = object : Player {
        override val id = "melonds"
        override val displayName = "melonDS"
        override val platformId = "nintendo-ds"
        override val packageNames = emptyList<String>()
        override val needsLocalFile = true
        override val startDisplay = StartDisplay.Primary
        override val occupiesBothDisplays = true
        override val requiresImportedGame = false
        override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("sav", "content://saves"))
        override fun launchIntent(request: LaunchRequest): PlayerIntent = error("unused")
    }

    init {
        ops.onListSaves = { rom, device -> assertEquals(ROM, rom); assertNotNull(device); server.toList() }
        ops.onDownloadSave = { save, _, path -> events += "download:${save.id}"; writeDurably(path, bytes.getValue(save.id)) }
        ops.onArchive = { save, _, hash, content ->
            assertEquals(rommSaveHash(content), hash)
            events += "archive:${save.id}"
            add(content, null, save.emulator, at = 0).id
        }
        ops.onUploadSave = { rom, _, slot, emulator, _, hash, content ->
            assertEquals(ROM, rom)
            assertEquals(rommSaveHash(content), hash)
            events += "upload:$slot:$emulator"
            if (pruneSlot) server.removeAll { it.slot == slot }
            val save = add(content, slot, emulator, id = nextId++, at = 100 + nextId)
            if (failAfterUpload) throw RommUnavailable("Response was lost")
            save.id
        }
        syncNormally()
    }

    @After fun cleanup() { root.toFile().deleteRecursively() }

    @Test fun listsEverySlotAndUsesTheOriginsDeviceNameAndTheHeldHash() = runBlocking {
        val old = add("current".toByteArray(), "sav", "melonds", at = 1)
        add("newer".toByteArray(), "sav", "melonds", at = 2)
        val argosy = add("elsewhere".toByteArray(), "autosave", "azahar", at = 3)
        server[server.indexOf(argosy)] = argosy.copy(originDeviceId = "deck", deviceSyncs = listOf(SaveDeviceSync("other", "Other"), SaveDeviceSync("deck", "Steam Deck")), screenshotId = 17)
        hold("current")
        val listed = library().serverSaves(GAME, player)
        assertEquals(listOf("autosave", "sav", "sav"), listed.map { it.slot })
        assertEquals(old.id.toString(), listed.single { it.playing }.id)
        assertEquals("Steam Deck", listed.first().source)
        assertEquals("$ORIGIN/api/screenshots/17/content", listed.first().screenshotUri)
    }

    @Test fun zipPlayingUsesTheContentHashRatherThanTheContainersMd5() = runBlocking {
        val zipped = zip()
        val save = add(zipped, "sav", "melonds")
        writeDurably(data.slotFile(ROM, "sav"), zipped)
        data.ledger(ROM).markSynced("sav", md5File(data.slotFile(ROM, "sav")), "melonds")
        assertNotEquals(save.contentHash, md5File(data.slotFile(ROM, "sav")))
        assertTrue(library().serverSaves(GAME, player).single().playing)
    }

    @Test fun aNamelessOriginFallsBackToTheEmulator() = runBlocking {
        val save = add("progress".toByteArray(), "sav", "melonds")
        server[0] = save.copy(originDeviceId = "nameless", deviceSyncs = listOf(SaveDeviceSync("nameless", null)))
        assertEquals("melonds", library().serverSaves(GAME, player).single().source)
    }

    @Test fun resolvesOriginsOncePerListingAndCachesThemByGameWithoutChangingSaveSources() = runBlocking {
        var calls = 0
        ops.onDevices = {
            calls++
            listOf(RommDevice("device-1", "Thor", "foldcade"), RommDevice("argosy", "AYN AYN Thor", "argosy"))
        }
        val own = add("own".toByteArray(), "sav", "melonds")
        val other = add("other".toByteArray(), "autosave", "azahar")
        server[0] = own.copy(originDeviceId = "device-1", deviceSyncs = listOf(SaveDeviceSync("device-1", "Old name")))
        server[1] = other.copy(originDeviceId = "argosy", deviceSyncs = listOf(SaveDeviceSync("argosy", "Old name")))
        val unknown = add("unknown".toByteArray(), null, null)
        val library = library()
        val saves = library.serverSaves(GAME, player)
        assertEquals(1, calls)
        assertEquals("Old name", saves.first { it.id == own.id.toString() }.source)
        val origins = mapOf(
            own.id.toString() to ServerSaveOrigin("foldcade", "Thor", true),
            other.id.toString() to ServerSaveOrigin("argosy", "AYN AYN Thor", false),
        )
        assertEquals(origins, library.serverSaveOrigins(GAME))
        assertEquals(origins, library.serverSaveOrigins(GAME))
        assertFalse(library.serverSaveOrigins(GAME).containsKey(unknown.id.toString()))
        assertTrue(library.serverSaveOrigins(GAME.copy(remoteKey = "99")).isEmpty())
        assertEquals(1, calls)
        library.serverSaves(GAME, player)
        assertEquals(2, calls)
    }

    @Test fun failedDeviceLookupQuietlyKeepsFallbacksAndDoesNotReuseStaleOrigins() = runBlocking {
        val save = add("progress".toByteArray(), "sav", "melonds")
        server[0] = save.copy(originDeviceId = "argosy", deviceSyncs = listOf(SaveDeviceSync("argosy", "Thor")))
        ops.onDevices = { listOf(RommDevice("argosy", "Thor", "argosy")) }
        val library = library()
        library.serverSaves(GAME, player)
        assertFalse(library.serverSaveOrigins(GAME).isEmpty())
        ops.onDevices = { throw RommForbidden("no devices.read") }
        assertEquals("Thor", library.serverSaves(GAME, player).single().source)
        assertTrue(library.serverSaveOrigins(GAME).isEmpty())
        server[0] = save.copy(originDeviceId = null, deviceSyncs = listOf(SaveDeviceSync("missing", null), SaveDeviceSync("named", "Steam Deck")))
        // A device that only synced the save did not make it: an unknown origin falls back to the emulator.
        assertEquals(save.emulator, library.serverSaves(GAME, player).single().source)
    }

    @Test fun deviceLookupCancellationStillCancelsTheScreenLoad() = runBlocking {
        add("progress".toByteArray(), "sav", "melonds")
        ops.onDevices = { throw kotlinx.coroutines.CancellationException("closed") }
        assertTrue(runCatching { library().serverSaves(GAME, player) }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }

    @Test fun aForeignCopyKeepsTheDestinationsFileNameAndExtension() = runBlocking {
        hold("current")
        val source = add("choice".toByteArray(), "srm", "retroarch")
        val destination = add("destination".toByteArray(), "autosave", "melonds")
        server[server.indexOf(destination)] = destination.copy(fileName = "Kid Icarus [old].sav")
        val upload = ops.onUploadSave
        var name: String? = null
        ops.onUploadSave = { rom, file, slot, emulator, device, hash, content ->
            name = file
            upload(rom, file, slot, emulator, device, hash, content)
        }
        library().copyServerSave(GAME, source.id.toString(), "autosave", "melonds", player)
        assertEquals("Kid Icarus [old].sav", name)
    }

    @Test fun theCommitHookRunsOnlyAfterValidationAndBeforePromotion() = runBlocking {
        hold("current")
        val source = add("choice".toByteArray(), "autosave", "melonds")
        library().playServerSave(GAME, player, source.id.toString()) { events += "commit-hook" }
        assertTrue(events.indexOfFirst { it.startsWith("archive:") } < events.indexOf("commit-hook"))
        assertTrue(events.indexOf("commit-hook") < events.indexOfFirst { it.startsWith("upload:") })
        events.clear()
        val corrupt = source.copy(contentHash = "0".repeat(32))
        server[server.indexOfFirst { it.id == source.id }] = corrupt
        assertNotNull(runCatching { library().playServerSave(GAME, player, source.id.toString()) { events += "commit-hook" } }.exceptionOrNull())
        assertFalse(events.contains("commit-hook"))
    }

    @Test fun aDefinitiveUploadRefusalAbandonsItsIntent() = runBlocking {
        hold("current")
        val source = add("choice".toByteArray(), "autosave", "melonds")
        ops.onUploadSave = { _, _, _, _, _, _, _ -> throw RommHttpException(409, "Refused") }
        val failure = runCatching { library().playServerSave(GAME, player, source.id.toString()) { events += "commit-hook" } }.exceptionOrNull()
        assertTrue(failure is PluginException.SaveChoiceRefused)
        assertFalse(SaveSelection(data.romSaves(ROM)).pending())
        assertEquals("current", Files.readString(data.slotFile(ROM, "sav")))
        assertTrue(events.contains("commit-hook"))
    }

    @Test fun aSlotMovedRefusalAlsoAbandonsTheIntent() = runBlocking {
        hold("current")
        val source = add("choice".toByteArray(), "autosave", "melonds")
        ops.onUploadSave = { _, _, _, _, _, _, _ -> throw RommSlotMoved("Slot changed") }
        assertTrue(runCatching { library().playServerSave(GAME, player, source.id.toString()) }.exceptionOrNull() is PluginException.SaveChoiceRefused)
        assertFalse(SaveSelection(data.romSaves(ROM)).pending())
        assertEquals("current", Files.readString(data.slotFile(ROM, "sav")))
    }

    @Test fun aConflictFromStrictSyncRefusesBeforeTheCommitHook() = runBlocking {
        hold("current")
        val source = add("choice".toByteArray(), "autosave", "melonds")
        ops.onSync = { _, _, _, _ -> report().copy(keptBoth = listOf(KeptBoth(ROM, "sav", null, source.id, data.slotFile(ROM, "sav")))) }
        assertNotNull(runCatching { library().playServerSave(GAME, player, source.id.toString()) { events += "commit-hook" } }.exceptionOrNull())
        assertFalse(events.contains("commit-hook"))
        assertFalse(events.any { it.startsWith("upload:") })
        assertEquals("current", Files.readString(data.slotFile(ROM, "sav")))
    }

    @Test fun ownUnslottedArchivesAreIdentifiedWithoutInventingASlot() = runBlocking {
        val backup = add("progress".toByteArray(), null, "azahar")
        server[0] = backup.copy(fileName = "foldcade-kept-12345678-1234-1234-1234-123456789abc.zip")
        add("other client".toByteArray(), null, "azahar")
        val library = library()
        val saves = library.serverSaves(GAME, player)
        assertTrue(saves.all { it.slot == null })
        assertEquals(setOf(backup.id.toString()), library.serverSaveBackups(GAME))
    }

    @Test fun syncNowUploadsHeldProgressWithoutAnInstalledPlayer() = runBlocking {
        hold("waiting progress", synced = false)
        library().syncServerSaves(GAME, null)
        assertEquals(listOf("sync"), events)
        assertEquals("waiting progress", Files.readString(data.slotFile(ROM, "sav")))
        assertTrue(library().unsyncedSaves().isEmpty())
        assertTrue(server.any { bytes[it.id]?.contentEquals("waiting progress".toByteArray()) == true })
    }

    @Test fun choosingFirstSyncsThenDownloadsThenUploadsAndPlacesExactlyThoseBytes() = runBlocking {
        hold("current", synced = false)
        val source = add("choice".toByteArray(), "autosave", "melonds")
        val library = library()
        val activity = mutableListOf<GameActivity>()
        library.observeActivity { _, a -> activity += a }
        val placed = library.playServerSave(GAME, player, source.id.toString())
        assertEquals("sync", events.first())
        assertTrue(events.indexOf("download:${source.id}") < events.indexOfFirst { it.startsWith("archive") })
        assertTrue(events.indexOfFirst { it.startsWith("archive") } < events.indexOf("upload:sav:melonds"))
        assertEquals("choice", Files.readString(data.slotFile(ROM, "sav")))
        assertEquals(md5File(data.slotFile(ROM, "sav")), data.ledger(ROM).slot("sav")?.syncedHash)
        assertEquals("sav", placed.single().slot)
        assertTrue(server.any { bytes[it.id]?.contentEquals("current".toByteArray()) == true })
        assertTrue(server.any { it.id == source.id })
        assertEquals(GameActivity.SyncingSaves, activity.first())
        assertTrue(activity.last() is GameActivity.Synced)
    }

    @Test fun everyIncompleteSyncRefusesBeforeDownloadingOrChangingLocalBytes() = runBlocking {
        val chosen = add("choice".toByteArray(), "autosave", "melonds")
        for (result in listOf(report().copy(queued = listOf("sav")), report().copy(failed = listOf("bad")), report().copy(completedSession = false), report())) {
            hold("unsynced", synced = false)
            ops.onSync = { _, _, _, _ -> result }
            events.clear()
            assertNotNull(runCatching { library().playServerSave(GAME, player, chosen.id.toString()) }.exceptionOrNull())
            assertEquals("unsynced", Files.readString(data.slotFile(ROM, "sav")))
            assertTrue(events.isEmpty())
        }
    }

    @Test fun offlineWithAnAlreadySyncedLocalSaveStillRefusesAndClearsActivity() = runBlocking {
        hold("current")
        val chosen = add("choice".toByteArray(), "autosave", "melonds")
        ops.onSync = { _, _, _, _ -> throw RommUnavailable("offline") }
        val activity = mutableListOf<GameActivity>()
        val library = library().also { it.observeActivity { _, a -> activity += a } }
        assertNotNull(runCatching { library.playServerSave(GAME, player, chosen.id.toString()) }.exceptionOrNull())
        assertEquals("current", Files.readString(data.slotFile(ROM, "sav")))
        assertTrue(events.isEmpty())
        assertEquals(GameActivity.Idle, activity.last())
        token = null
        assertNotNull(runCatching { library().playServerSave(GAME, player, chosen.id.toString()) }.exceptionOrNull())
    }

    @Test fun corruptHashOrSizeOrAnUnknownSaveNeverReachesAnUpload() = runBlocking {
        hold("current")
        val chosen = add("choice".toByteArray(), "autosave", "melonds")
        for (corrupt in listOf(chosen.copy(contentHash = "0".repeat(32)), chosen.copy(sizeBytes = 999), chosen.copy(contentHash = null), chosen.copy(romId = 9))) {
            server[server.indexOfFirst { it.id == chosen.id }] = corrupt
            events.clear()
            assertNotNull(runCatching { library().playServerSave(GAME, player, chosen.id.toString()) }.exceptionOrNull())
            assertFalse(events.any { it.startsWith("upload") })
            assertEquals("current", Files.readString(data.slotFile(ROM, "sav")))
        }
    }

    @Test fun aLostUploadResponseRecoversOnReconnectEvenThoughTheOldSlotIsSynced() = runBlocking {
        hold("current")
        val chosen = add("choice".toByteArray(), "autosave", "melonds")
        failAfterUpload = true
        assertNotNull(runCatching { library().playServerSave(GAME, player, chosen.id.toString()) }.exceptionOrNull())
        assertEquals("current", Files.readString(data.slotFile(ROM, "sav")))
        assertTrue(SaveSelection(data.romSaves(ROM)).pending())
        failAfterUpload = false
        events.clear()
        library().syncPending()
        assertEquals("choice", Files.readString(data.slotFile(ROM, "sav")))
        assertEquals(md5File(data.slotFile(ROM, "sav")), data.ledger(ROM).slot("sav")?.syncedHash)
        assertFalse(SaveSelection(data.romSaves(ROM)).pending())
        assertEquals(listOf("sync"), events) // Recovery never repeats overwrite.
        assertTrue(server.any { bytes[it.id]?.contentEquals("current".toByteArray()) == true })
        assertTrue(server.any { it.id == chosen.id })
    }

    @Test fun ledgerWriteFailureAfterPromotionRetainsTheIntentAndRecovers() = runBlocking {
        hold("current")
        val chosen = add("choice".toByteArray(), "autosave", "melonds")
        val upload = ops.onUploadSave
        val ledger = data.romSaves(ROM).resolve(SaveLedger.FILE_NAME)
        val before = ledger.resolveSibling("before-ledger.json")
        ops.onUploadSave = { rom, name, slot, emulator, device, hash, content ->
            val id = upload(rom, name, slot, emulator, device, hash, content)
            Files.move(ledger, before)
            Files.createDirectory(ledger)
            id
        }
        assertNotNull(runCatching { library().playServerSave(GAME, player, chosen.id.toString()) }.exceptionOrNull())
        assertEquals("choice", Files.readString(data.slotFile(ROM, "sav")))
        assertTrue(SaveSelection(data.romSaves(ROM)).pending())
        Files.delete(ledger)
        Files.move(before, ledger)
        ops.onUploadSave = upload
        library().syncPending()
        assertFalse(SaveSelection(data.romSaves(ROM)).pending())
        assertEquals(md5File(data.slotFile(ROM, "sav")), data.ledger(ROM).slot("sav")?.syncedHash)
        assertTrue(server.any { bytes[it.id]?.contentEquals("current".toByteArray()) == true })
        assertTrue(server.any { it.id == chosen.id })
    }

    @Test fun deathAfterInstallingTheSlotButBeforeWritingItsLedgerIsRepeatable() = runBlocking {
        hold("current")
        val selected = staged("choice")
        add("choice".toByteArray(), "sav", "melonds", at = 100)
        writeDurably(data.slotFile(ROM, "sav"), "choice".toByteArray())
        library().syncPending()
        assertFalse(selected.pending())
        assertEquals("choice", Files.readString(data.slotFile(ROM, "sav")))
        assertEquals(md5File(data.slotFile(ROM, "sav")), data.ledger(ROM).slot("sav")?.syncedHash)
    }

    @Test fun deathBeforeUploadingDoesNotReplayTheOverwrite() = runBlocking {
        hold("current")
        staged("choice")
        events.clear()
        library().syncPending()
        assertEquals("current", Files.readString(data.slotFile(ROM, "sav")))
        assertEquals(listOf("sync"), events)
        assertFalse(SaveSelection(data.romSaves(ROM)).pending())
    }

    @Test fun aNewerServerHeadAfterAnAmbiguousUploadWinsWithoutReplay() = runBlocking {
        hold("current")
        staged("choice")
        add("choice".toByteArray(), "sav", "melonds", at = 100)
        add("other device advanced".toByteArray(), "sav", "melonds", at = 200)
        library().syncPending()
        assertFalse(events.any { it.startsWith("upload") })
        assertEquals("current", Files.readString(data.slotFile(ROM, "sav"))) // Normal sync owns the next negotiation.
        assertTrue(server.any { bytes[it.id]?.contentEquals("choice".toByteArray()) == true })
        assertTrue(server.any { bytes[it.id]?.contentEquals("current".toByteArray()) == true })
    }

    @Test fun localProgressAfterAnInterruptedChoiceIsNeverReplacedByRecovery() = runBlocking {
        hold("current")
        staged("choice")
        add("choice".toByteArray(), "sav", "melonds", at = 100)
        writeDurably(data.slotFile(ROM, "sav"), "new progress".toByteArray())
        library().syncPending()
        assertEquals("new progress", Files.readString(data.slotFile(ROM, "sav")))
        assertFalse(events.any { it.startsWith("upload") })
        assertTrue(server.any { bytes[it.id]?.contentEquals("new progress".toByteArray()) == true })
    }

    @Test fun aServerRetentionCapCannotPruneTheCurrentSaveDuringPlayOrCopy() = runBlocking {
        hold("current")
        val source = add("choice".toByteArray(), "autosave", "melonds")
        pruneSlot = true
        library().playServerSave(GAME, player, source.id.toString())
        assertTrue(server.any { it.slot == null && bytes[it.id]?.contentEquals("current".toByteArray()) == true })
        assertTrue(server.any { it.slot == "sav" && bytes[it.id]?.contentEquals("choice".toByteArray()) == true })
        add("destination progress".toByteArray(), "other-slot", "argosy")
        library().copyServerSave(GAME, source.id.toString(), "other-slot", "argosy", player)
        assertTrue(server.any { it.slot == null && bytes[it.id]?.contentEquals("destination progress".toByteArray()) == true })
        assertTrue(server.any { it.id == source.id })
    }

    @Test fun aFailedArchiveRefusesPromotionAndARetryReusesAnAcknowledgedArchive() = runBlocking {
        hold("current")
        val source = add("choice".toByteArray(), "autosave", "melonds")
        val archive = ops.onArchive
        ops.onArchive = { save, device, hash, content ->
            archive(save, device, hash, content)
            throw RommUnavailable("Archive response lost")
        }
        assertNotNull(runCatching { library().playServerSave(GAME, player, source.id.toString()) }.exceptionOrNull())
        assertEquals("current", Files.readString(data.slotFile(ROM, "sav")))
        assertFalse(events.any { it.startsWith("upload") })
        ops.onArchive = { _, _, _, _ -> error("Must reuse the existing archive") }
        library().playServerSave(GAME, player, source.id.toString())
        assertEquals("choice", Files.readString(data.slotFile(ROM, "sav")))
        assertEquals(1, events.count { it.startsWith("archive") })
    }

    @Test fun foreignSlotCopyUsesItsLatestEmulatorAndLeavesLocalFilesAndLedgerAlone() = runBlocking {
        hold("current")
        val source = add("choice".toByteArray(), "sav", "melonds")
        val destination = add("old target".toByteArray(), "autosave", "argosy")
        val ledger = Files.readString(data.romSaves(ROM).resolve(SaveLedger.FILE_NAME))
        assertTrue(library().copyServerSave(GAME, source.id.toString(), "autosave", "argosy", player).isEmpty())
        assertTrue(events.contains("upload:autosave:argosy"))
        assertEquals("current", Files.readString(data.slotFile(ROM, "sav")))
        assertEquals(ledger, Files.readString(data.romSaves(ROM).resolve(SaveLedger.FILE_NAME)))
        assertTrue(server.any { it.id == source.id })
        assertTrue(server.any { it.id == destination.id })
    }

    @Test fun copyIntoThePlayersOwnSlotAlsoCommitsLocalBytesAndLedger() = runBlocking {
        hold("current")
        val source = add("choice".toByteArray(), "autosave", "melonds")
        assertEquals("sav", library().copyServerSave(GAME, source.id.toString(), "sav", "melonds", player).single().slot)
        assertEquals("choice", Files.readString(data.slotFile(ROM, "sav")))
        assertEquals(md5File(data.slotFile(ROM, "sav")), data.ledger(ROM).slot("sav")?.syncedHash)
    }

    @Test fun changedDestinationOrUnconfiguredPlayerRefusesWithoutUpload() = runBlocking {
        hold("current")
        val source = add("choice".toByteArray(), "sav", "melonds")
        add("old target".toByteArray(), "autosave", "new-emulator")
        assertNotNull(runCatching { library().copyServerSave(GAME, source.id.toString(), "autosave", "old-emulator", player) }.exceptionOrNull())
        val missing = object : Player by player { override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("sav", null)) }
        assertNotNull(runCatching { library().playServerSave(GAME, missing, source.id.toString()) }.exceptionOrNull())
        assertFalse(events.any { it.startsWith("upload") })
    }

    private fun library(): RommLibrary {
        val wiring = RommWiring(ORIGIN, RommTokenSource { token }, root.resolve("cache"), root.resolve("data"))
        wiring.open = { _, _ -> ops }
        return RommLibrary(RommGate { wiring })
    }

    private fun staged(text: String): SaveSelection {
        val selection = SaveSelection(data.romSaves(ROM))
        writeDurably(selection.staged, text.toByteArray())
        selection.begin("sav", "melonds", md5File(selection.staged), rommSaveHash(text.toByteArray())!!, md5File(data.slotFile(ROM, "sav")))
        return selection
    }

    private fun hold(text: String, synced: Boolean = true) {
        writeDurably(data.slotFile(ROM, "sav"), text.toByteArray())
        if (synced) {
            data.ledger(ROM).markSynced("sav", md5File(data.slotFile(ROM, "sav")), "melonds")
            if (server.none { bytes[it.id]?.contentEquals(text.toByteArray()) == true }) add(text.toByteArray(), "sav", "melonds", at = 0)
        } else data.ledger(ROM).update("sav") { null }
    }

    private fun syncNormally() {
        ops.onSync = { _, held, _, _ ->
            events += "sync"
            held.forEach { save ->
                val content = Files.readAllBytes(save.file)
                if (server.none { bytes[it.id]?.contentEquals(content) == true }) add(content, save.slot, save.emulator, at = 0)
                data.ledger(ROM).markSynced(save.slot!!, md5File(save.file), save.emulator)
            }
            report()
        }
    }

    private fun add(content: ByteArray, slot: String?, emulator: String?, id: Long = nextId++, at: Long = id): RommSave {
        val save = RommSave(id, ROM, "save.sav", slot, emulator, rommSaveHash(content), Instant.ofEpochSecond(at), content.size.toLong(), null, emptyList(), null)
        server += save
        bytes[id] = content
        return save
    }

    private fun report() = SaveSyncReport(1, "5.4.1", emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), true)
    private fun zip(): ByteArray = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { it.putNextEntry(ZipEntry("sdmc/save.bin")); it.write("progress".toByteArray()); it.closeEntry() } }.toByteArray()
    companion object {
        const val ROM = 1234L
        const val ORIGIN = "https://romm.example"
        val GAME = Game("romm", ROM.toString(), "nintendo-ds", Availability.Cached, "Mario")
    }
}
