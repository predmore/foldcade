package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GamePage
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.FirmwareBytes
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
import app.foldcade.api.plugin.GameFileReader
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.SaveFolderAccess
import app.foldcade.api.plugin.SaveSet
import app.foldcade.api.plugin.SaveSlot
import app.foldcade.api.plugin.StartDisplay
import app.foldcade.api.plugin.SyncOutcome
import app.foldcade.api.plugin.SyncResult
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import app.foldcade.plugins.duckstation.DuckStationCardSettings
import app.foldcade.plugins.duckstation.DuckStationPlayer
import app.foldcade.plugins.explus.ExPlusEntry
import app.foldcade.plugins.melonds.MelonDsPlayer
import app.foldcade.plugins.retroarch.RetroArchEntry
import app.foldcade.plugins.retroarch.RetroArchPlayer
import app.foldcade.plugins.retroarch.SHARED_STORAGE
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** A folder copy that plays keeps its saves on the server copy of the same file. */
class LaunchSavesTest {
    @Test fun aFreshProcessResolvesMatchedWorkingSavesWithoutLaunchingOrDownloading() = runBlocking {
        val file = "$SAVES/Tetris DS (USA).sav"
        docs.files[file] = "matched progress".toByteArray()
        val backend = object : LibraryBackend by server {
            override suspend fun saves(game: Game) = SaveSet(game.backendId, listOf(SaveSlot("sav", md5Hex("matched progress".toByteArray()), null)))
            override suspend fun saveReceipts(game: Game): Map<String, Long?> = mapOf("sav" to 123L)
        }
        val spots = resolveReceiptSpots(saves, backend, owner, Melon) { local }!!
        val receipt = readSaveReceipt(saves, backend, owner, spots)
        assertEquals(OffDeviceFact.OnRomm(123), receipt)
        val root = FolderRef("primary:saves", "saves")
        val facts = SaveFacts(SaveKeeper.Emulator(Melon.displayName), LocationFact.Verified(root, Evidence(EvidenceKind.SessionWrite, "saved"), 200),
            SpotFact.Resolved, receipt, null, emptyList(), true)
        assertTrue(deriveSaveStatus(facts) is SaveStatus.Protected)
        docs.files[file] = "played outside Foldcade".toByteArray()
        assertEquals(OffDeviceFact.Unknown, readSaveReceipt(saves, backend, owner, spots))
        assertTrue(server.calls.isEmpty())
    }

    @Test fun backgroundReceiptResolutionSkipsGamesWithoutHeldSavesAndMissingLocalRoms() = runBlocking {
        val untracked = object : Player by Melon {
            override fun saveDeclarations(game: Game): List<SaveDeclaration> = error("Do not resolve an untracked game without held saves")
        }
        assertEquals(null, resolveReceiptSpots(saves, server, owner, untracked) { error("Must not resolve a game without held saves") })
        server.held = listOf("sav")
        assertEquals(null, resolveReceiptSpots(saves, server, owner, Melon) { null })
        assertTrue(docs.files.isEmpty())
        assertTrue(server.calls.isEmpty())
    }

    @Test fun aTrackedGameWithoutHeldSavesKeepsNothingYetByRereadingItsWorkingSpots() = runBlocking {
        val spots = saves.spots(Melon, owner, local)
        val resolved = resolveReceiptSpots(saves, server, owner, Melon, cached = spots) {
            error("A known empty save needs no ROM lookup")
        }!!
        assertEquals(spots, resolved)
        val destination = readSaveReceipt(saves, server, owner, resolved)
        assertEquals(OffDeviceFact.NothingYet, destination)
        val root = FolderRef("primary:saves", "saves")
        assertEquals(SaveStatus.NothingYet, deriveSaveStatus(SaveFacts(SaveKeeper.Emulator(Melon.displayName),
            LocationFact.Verified(root, Evidence(EvidenceKind.SessionWrite, "saved"), 200), SpotFact.Resolved,
            destination, null, emptyList(), true)))
        val rebound = object : Player by Melon {
            override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("sav", "content://another-folder"))
        }
        assertEquals(null, resolveReceiptSpots(saves, server, owner, rebound, cached = spots) { error("No held save") })
        assertTrue(server.calls.isEmpty())
    }

    @Test fun aVerifiedExistingBackupDrainsTheQueueWithoutAnExtraBackup() = runBlocking {
        docs.files["$SAVES/Tetris DS (USA).sav"] = "already on RomM".toByteArray()
        val store = SaveProtectionStore(Files.createTempDirectory("duplicate-copy"))
        val incident = incident(IncidentKind.WrongFolder)
        store.put(incident)
        val copies = SaveIncidentCopies(saves, plugins, store)
        copies.stage(owner, Melon, saves.spots(Melon, owner, local), incident, mark = true)
        server.existingVersionId = "backup-131"
        copies.keep(owner, Melon)
        assertTrue(saves.queuedCopies().isEmpty())
        assertTrue(store.incidents().single().staged.isEmpty())
        assertTrue(server.backups.isEmpty())
        assertTrue(server.calls.isEmpty())
    }

    @Test fun incidentCopiesUseTheLibraryAndOriginRecordedInEachSidecar() {
        val copies = SaveIncidentCopies(saves, plugins, SaveProtectionStore(Files.createTempDirectory("copy-destinations")))
        val spot = SaveSpot("sav", SAVES, "Tetris DS (USA).sav")
        val bytes = "working save".toByteArray()
        val device = saves.stageCopy(owner.copy(backendId = app.foldcade.localfolder.LocalFolderBackend.ID), Melon, spot, bytes)
        val romm = owner.copy(backendId = app.foldcade.plugins.romm.ROMM_LIBRARY_ID)
        val fromOldServer = saves.stageCopy(romm, Melon, spot, bytes, "https://old.romm.test")
        val legacy = saves.stageCopy(romm, Melon, spot, bytes)
        val unknown = saves.stageCopy(owner.copy(backendId = "another-plugin"), Melon, spot, bytes)
        assertEquals(mapOf(
            device to SaveFixCopyDestination.Device,
            fromOldServer to SaveFixCopyDestination.Romm,
            legacy to SaveFixCopyDestination.Romm,
        ), copies.destinations(listOf(device, fromOldServer, legacy, unknown, "file:///missing")))
        assertEquals(mapOf(device to SaveFixCopyDestination.Device), copies.destinations(listOf(device)))
    }

    @Test fun everyPreviouslyUnguardedAdapterStopsWhenAKnownSaveHasNoSpot() = runBlocking {
        val players = listOf(
            app.foldcade.plugins.azahar.AzaharPlayer(), app.foldcade.plugins.melonds.MelonDsPlayer(),
            app.foldcade.plugins.armsx2.ArmsX2Player(), app.foldcade.plugins.aps3e.Aps3ePlayer(),
            app.foldcade.plugins.duckstation.DuckStationPlayer(), app.foldcade.plugins.retroarch.RetroArchEntry().players.first(),
        )
        docs.names.remove("content://rf4") // unreadable ROM: none may guess an identity
        for (player in players) {
            (player as app.foldcade.api.plugin.SaveFolderHolder).bindSaveFolder(SAVES)
            (player as? app.foldcade.api.plugin.HostedSaveFolder)?.bindHostedFolder(SAVES, true)
            val game = owner.copy(platformId = player.platformId)
            server.held = player.saveDeclarations(game).map { it.slot }
            assertTrue(player.id, server.held.isNotEmpty())
            val failure = runCatching { LaunchSaves(saves, plugins) { awaiting += it }.prepare(GameRoute("tile", game, game), player, local) }.exceptionOrNull()
            assertTrue("${player.id}: $failure", failure is UnresolvedSaveNotPlaced)
            assertTrue(server.calls.isEmpty())
            assertTrue(awaiting.isEmpty())
        }
    }

    @Test fun positiveIncidentsStopBeforeAnyReconciliationButPlayAnywayIsOneLaunch() = runBlocking {
        val root = app.foldcade.language.FolderRef("primary:saves", "saves")
        for (kind in listOf(app.foldcade.language.IncidentKind.WrongFolder, app.foldcade.language.IncidentKind.FolderEmptied, app.foldcade.language.IncidentKind.LostAccess)) {
            val incident = app.foldcade.language.SaveIncident("${kind}:melonds", kind, "melonds", "melonDS", emptyList(), root, null,
                app.foldcade.language.Evidence(app.foldcade.language.EvidenceKind.FreshLog, "proof"), 1)
            val launch = LaunchSaves(saves, plugins, protectionGate = { _, _, _ -> throw SaveProtectionStop(incident) }) { awaiting += it }
            val failure = runCatching { launch.prepare(GameRoute("tile", owner, owner), Melon, local) }.exceptionOrNull()
            assertTrue(failure is SaveProtectionStop)
            assertTrue(failure is UnresolvedSaveNotPlaced)
            assertTrue(server.calls.isEmpty())
            val prepared = launch.prepare(GameRoute("tile", owner, owner), Melon, local, syncing = false)
            assertTrue(prepared.unprotectedSession)
            assertTrue(prepared.spots.isEmpty())
            val dialog = app.foldcade.language.saveProtectionDialog(incident, app.foldcade.language.HostScreen.Bottom)
            assertTrue(dialog.holdsLaunch)
            assertEquals(app.foldcade.language.BACK_CLOSES, dialog.safeIndex)
        }
    }

    @Test fun anOptionalProbeFailureWithoutPositiveEvidenceDoesNotStopLaunch() = runBlocking {
        val launch = LaunchSaves(saves, plugins, protectionGate = { _, _, _ -> throw java.io.IOException("evidence could not be recorded") }) { awaiting += it }
        launch.prepare(GameRoute("tile", owner, owner), Melon, local)
        assertEquals(listOf("prepareLaunch:12"), server.calls)
    }

    @Test fun guidedBindingKeepsLiveBytesAsABackupAndTheNextLaunchPlacesRommHeadWithoutPromotion() = runBlocking {
        docs.files["$SAVES/Tetris DS (USA).sav"] = "fresh live copy".toByteArray()
        server.toPlace = "RomM head"
        val store = SaveProtectionStore(Files.createTempDirectory("incident"))
        val incident = incident(IncidentKind.WrongFolder)
        store.put(incident)
        val copies = SaveIncidentCopies(saves, plugins, store)
        val spots = saves.spots(Melon, owner, local)
        copies.stage(owner, Melon, spots, incident, mark = true)
        copies.keep(owner, Melon)
        assertEquals(listOf(md5Hex("fresh live copy".toByteArray())), server.backups)
        assertTrue(store.incidents().single().staged.isEmpty())
        assertEquals(listOf("backup-1"), store.incidents().single().kept)
        assertTrue(saves.changedSince(owner.backendId, owner, Melon, spots).slots.isEmpty())
        LaunchSaves(saves, plugins) { awaiting += it }.prepare(GameRoute("tile", play, owner), Melon, local)
        assertEquals(listOf("prepareSaves:12"), server.calls)
        assertEquals("RomM head", docs.files.getValue("$SAVES/Tetris DS (USA).sav").toString(Charsets.UTF_8))
        assertTrue(server.observed.isEmpty())
    }

    @Test fun aRetroArchBesideRomCopyRetriesAsABackupWithoutReconcilingTheSlot() = runBlocking {
        val retroArch = app.foldcade.plugins.retroarch.RetroArchEntry().players.single { it.platformId == "game-boy-advance" }
        val gba = owner.copy(platformId = "game-boy-advance")
        val directory = Files.createTempDirectory("retroarch-backup")
        val store = SaveProtectionStore(directory)
        val live = FolderRef("primary:ROMs/GBA", "ROMs/GBA")
        val incident = incident(IncidentKind.WrongFolder).copy(playerKey = "retroarch", gameIds = listOf(homeGameId(gba.backendId, gba.remoteKey)), live = live)
        store.put(incident)
        val content = "progress beside the ROM".toByteArray()
        val candidate = RetroArchCandidate(live, SaveSpot("srm", storageFolderDocument(live.identity), "Game.srm"), FileReading.Bytes(content))
        val copies = SaveIncidentCopies(saves, plugins, store)
        server.backupsFail = true
        copies.stageCandidate(gba, retroArch, candidate, incident)
        copies.keep(gba, retroArch)
        assertEquals("srm", saves.queuedCopies().single().slot.slot)
        val restartedStore = SaveProtectionStore(directory)
        server.backupsFail = false
        SaveIncidentCopies(saves, plugins, restartedStore).keep(gba, retroArch)
        assertEquals(listOf(md5Hex(content)), server.backups)
        assertEquals(listOf("backup-1"), restartedStore.incidents().single().kept)
        assertTrue(saves.queuedCopies().isEmpty())
        assertTrue(server.calls.isEmpty())
        assertTrue(server.observed.isEmpty())
    }

    @Test fun contentWritesScopeOnlyBesideRomIncidentsToOneLibraryGame() = runBlocking {
        for (player in listOf(app.foldcade.plugins.melonds.MelonDsPlayer(),
            app.foldcade.plugins.explus.ExPlusEntry().players.single { it.id == "gbc-emu.game-boy-color" })) {
            val game = owner.copy(platformId = player.platformId)
            val key = player.saveFolderId()
            val directory = Files.createTempDirectory("scoped-content-write")
            val store = SaveProtectionStore(directory)
            val copies = SaveIncidentCopies(saves, plugins, store)
            val probes = SaveProbes(android.content.ContextWrapper(null), saves, store, { SAVES }, copies)
            val bound = RootReading(FolderRef("primary:saves", "saves"), true, emptyList(), "placed", true, null, null)
            val live = FolderRef("primary:ROMs", "ROMs")
            for (beside in listOf(false, true)) {
                val alternate = RootReading(live, false, emptyList(), null, true, null, null, besideGame = beside)
                val before = ProbeSnapshot(100, 1, player.packageNames.first(), listOf(bound, alternate))
                val after = before.copy(atMillis = 500, roots = listOf(bound, alternate.copy(spotHash = "session bytes")))
                probes.noteSession(player, game, before, after)
                val incident = SaveProtectionStore(directory).incidents().single()
                assertEquals(beside, incident.besideGame)
                assertTrue(incident.appliesTo(game))
                assertEquals(!beside, incident.appliesTo(game.copy(remoteKey = "another")))
                assertEquals(!beside, incident.appliesTo(game.copy(backendId = "other-library")))
                assertEquals(beside, store.pendingBackup(key, game))
                assertFalse(store.pendingBackup(key, game.copy(remoteKey = "another")))
                assertEquals(beside && player !is app.foldcade.plugins.melonds.MelonDsPlayer, probes.location(player, after) is LocationFact.Bound)
                store.close(incident.id)
            }
        }
    }

    @Test fun aWatchedMelonDsStartupFileCannotVerifyTheFolderOrMaskABesideWrite() {
        val player = MelonDsPlayer()
        val spot = saves.probeSpots(player, owner, local, SAVES).single()
        val path = "$SAVES/${spot.fileName}"
        val watched = RootReading(FolderRef("primary:saves", "saves"), true, emptyList(), null, true, null, null)
        val beside = RootReading(FolderRef("primary:roms", "roms"), false, emptyList(), null, true, null, null, besideGame = true)
        val before = ProbeSnapshot(100, 1, player.packageNames.first(), listOf(watched, beside), configUnavailable = true)
        docs.files[path] = ByteArray(0)
        val startup = watched.copy(spotHash = saves.probeSpotHash(player, spot, bound = true, limit = SAVE_ZIP_LIMIT))
        val returned = before.copy(atMillis = 200, roots = listOf(startup, beside))
        assertEquals(SessionVerdict.NoNews, judgeSession(before, returned, 100))
        assertEquals(SessionVerdict.WroteElsewhere(beside.root, besideGame = true), judgeSession(before,
            returned.copy(roots = listOf(startup, beside.copy(spotHash = "beside progress"))), 100))

        docs.files[path] = byteArrayOf(10, 20, 30)
        val progress = startup.copy(spotHash = saves.probeSpotHash(player, spot, bound = true, limit = SAVE_ZIP_LIMIT))
        assertEquals(SessionVerdict.Wrote(watched.root), judgeSession(returned, returned.copy(atMillis = 300, roots = listOf(progress, beside)), 100))
    }

    @Test fun exStartupBatteriesOnlyProveTheBesideLocationUntilTheWatchedBatteryHoldsProgress() {
        for ((id, ext) in listOf("gba-emu.game-boy-advance" to "gba", "gbc-emu.game-boy-color" to "gbc")) {
            val player = ExPlusEntry().players.single { it.id == id }
            val game = owner.copy(platformId = player.platformId)
            docs.names[local.uri] = "Counter.$ext"
            val spot = saves.probeSpots(player, game, local, SAVES).single()
            val watched = RootReading(FolderRef("primary:saves", "saves"), true, emptyList(), null, true, null, null)
            val besideRoot = FolderRef("primary:roms", "roms")
            val before = ProbeSnapshot(100, 1, player.packageNames.first(), listOf(watched,
                BesideGameCandidate(besideRoot, spot, FileReading.Missing).reading()), configUnavailable = true)
            for (fill in listOf(0.toByte(), (-1).toByte())) {
                val battery = ByteArray(8192) { fill }
                val entries = mutableMapOf("Counter.sav" to battery)
                if (ext == "gbc") entries["Counter.rtc"] = byteArrayOf(4, 3, 2, 1)
                for ((name, bytes) in entries) docs.files["$SAVES/$name"] = bytes
                val startup = watched.copy(spotHash = saves.probeSpotHash(player, spot, bound = true, limit = SAVE_ZIP_LIMIT))
                val returned = before.copy(atMillis = 200, roots = listOf(startup, before.roots.last()))
                assertEquals(SessionVerdict.NoNews, judgeSession(before, returned, 100))
                val blankBeside = BesideGameCandidate(besideRoot, spot, FileReading.Bytes(saveZip(entries)), blankBattery = true)
                assertEquals(SessionVerdict.WroteElsewhere(besideRoot, besideGame = true),
                    judgeSession(before, returned.copy(roots = listOf(startup, blankBeside.reading())), 100))
                battery[12] = 1
                val progress = watched.copy(spotHash = saves.probeSpotHash(player, spot, bound = true, limit = SAVE_ZIP_LIMIT))
                assertEquals(SessionVerdict.Wrote(watched.root), judgeSession(returned, returned.copy(atMillis = 300, roots = listOf(progress)), 100))
            }
        }
    }

    @Test fun melonDsBesideRomWriteGatesOtherGamesWhileExKeepsItsGatePerGame() = runBlocking {
        val ds = app.foldcade.plugins.melonds.MelonDsPlayer()
        val ex = app.foldcade.plugins.explus.ExPlusEntry().players.single { it.id == "gbc-emu.game-boy-color" }
        for ((player, global) in listOf(ds to true, ex to false)) {
            val gameA = owner.copy(platformId = player.platformId)
            val gameB = gameA.copy(remoteKey = "another")
            val store = SaveProtectionStore(Files.createTempDirectory("beside-launch-gate"))
            val copies = SaveIncidentCopies(saves, plugins, store)
            // No Android folder provider is needed to honor an incident already observed on return.
            val probes = SaveProbes(android.content.ContextWrapper(null), saves, store, { null }, copies)
            val bound = FolderRef("primary:saves", "saves")
            val live = FolderRef("primary:ROMs", "ROMs")
            val watched = RootReading(bound, true, emptyList(), "placed", true, null, null)
            val beside = RootReading(live, false, emptyList(), null, true, null, null, besideGame = true)
            val before = ProbeSnapshot(100, 1, player.packageNames.first(), listOf(watched, beside))
            val after = before.copy(atMillis = 500, roots = listOf(watched, beside.copy(spotHash = "session bytes")))
            probes.noteSession(player, gameA, before, after)
            val incident = store.incidents().single()
            assertFalse(incident.appliesTo(gameB))
            assertEquals(incident, (runCatching { probes.gate(player, gameA, local) }.exceptionOrNull() as SaveProtectionStop).incident)
            val gameBStop = runCatching { probes.gate(player, gameB, local) }.exceptionOrNull()
            if (global) {
                assertEquals(incident, (gameBStop as SaveProtectionStop).incident)
                assertEquals(LocationFact.Mismatch(bound, null, incident.evidence, 500), probes.location(player, after))
            } else {
                assertEquals(null, gameBStop)
                assertTrue(probes.location(player, after) is LocationFact.Bound)
            }
            assertEquals(global, copies.beginUnprotected(gameB, player))
            assertEquals(global, store.pendingBackup(player.saveFolderId(), gameB))
            assertEquals(incident, store.incidents().single())
            store.put(incident.copy(repair = RepairState.Done))
            probes.gate(player, gameB, local)
        }
    }

    @Test fun besideCopyKeepsItsExactBytesOnceAcrossFailureRestartAndRecapture() = runBlocking {
        val dir = Files.createTempDirectory("beside-copy-retry")
        val store = SaveProtectionStore(dir)
        val incident = incident(IncidentKind.WrongFolder).copy(besideGame = true, gameIds = listOf(homeGameId(owner.backendId, owner.remoteKey)))
        store.put(incident)
        val spot = SaveSpot("sav", primaryFolderDocument("ROMs/NDS"), "Tetris DS (USA).sav")
        val bytes = "progress beside the ROM".toByteArray()
        val copies = SaveIncidentCopies(saves, plugins, store)
        server.backupsFail = true
        repeat(2) {
            copies.stageBackup(owner, Melon, spot, bytes, incident)
            copies.keep(owner, Melon)
        }
        val staged = saves.queuedCopies().single()
        assertArrayEquals(bytes, Files.readAllBytes(java.nio.file.Paths.get(java.net.URI(staged.slot.contentUri!!))))
        val restarted = SaveProtectionStore(dir)
        val resumedCopies = SaveIncidentCopies(saves, plugins, restarted)
        server.backupsFail = false
        resumedCopies.keep(owner, Melon)
        resumedCopies.stageBackup(owner, Melon, spot, bytes, incident)
        resumedCopies.keep(owner, Melon)
        assertTrue(saves.queuedCopies().isEmpty())
        assertEquals(listOf(md5Hex(bytes)), server.backups)
        assertEquals(listOf("backup-1"), restarted.incident(incident.id)?.kept)
        assertTrue(server.calls.isEmpty())
        assertTrue(server.observed.isEmpty())
    }

    @Test fun captureContentReturnStagesProgressButUsesBlankBatteryOnlyAsEvidence() = runBlocking {
        val player = app.foldcade.plugins.explus.ExPlusEntry().players.single { it.id == "gbc-emu.game-boy-color" }
        val game = owner.copy(platformId = player.platformId)
        val store = SaveProtectionStore(Files.createTempDirectory("content-return"))
        val copies = SaveIncidentCopies(saves, plugins, store)
        val probes = SaveProbes(android.content.ContextWrapper(null), saves, store, { SAVES }, copies)
        val bound = RootReading(FolderRef("primary:saves/GBC.emu", "saves/GBC.emu"), true, emptyList(), "placed", true, null, null)
        val live = FolderRef("primary:ROMs", "ROMs")
        val spot = SaveSpot("battery", primaryFolderDocument("ROMs"), "Counter.zip")
        val blank = BesideGameCandidate(live, spot, FileReading.Bytes(byteArrayOf(0)), blankBattery = true)
        val progress = blank.copy(read = FileReading.Bytes(byteArrayOf(21, 42)), blankBattery = false)
        val before = ProbeSnapshot(100, 1, player.packageNames.single(), listOf(bound, blank.reading().copy(spotHash = null)))
        fun capture(candidate: BesideGameCandidate) {
            val after = before.copy(atMillis = 500, roots = listOf(bound, candidate.reading()))
            probes.noteSession(player, game, before, after)
            probes.captureContentReturn(player, game, local, before, after, backupOnly = false,
                readBeside = { candidate }, reachable = { error("Only the ROM folder changed") })
        }
        capture(blank)
        assertTrue(store.incidents().single().blocksLaunch)
        assertEquals(BesideSaveSource.Blank, store.incidents().single().besideSource)
        assertTrue(saves.queuedCopies().isEmpty())
        repeat(2) { capture(progress) }
        assertEquals(BesideSaveSource.Progress, store.incidents().single().besideSource)
        assertEquals(md5Hex(byteArrayOf(21, 42)), saves.queuedCopies().single().slot.contentHash)
        copies.keep(game, player)
        assertEquals(listOf(md5Hex(byteArrayOf(21, 42))), server.backups)
        assertTrue(server.observed.isEmpty())
    }

    @Test fun captureContentReturnKeepsIdenticalCandidateBytesSeparatelyForDifferentGames() = runBlocking {
        val player = app.foldcade.plugins.melonds.MelonDsPlayer()
        val store = SaveProtectionStore(Files.createTempDirectory("shared-content-return"))
        val copies = SaveIncidentCopies(saves, plugins, store)
        val probes = SaveProbes(android.content.ContextWrapper(null), saves, store, { SAVES }, copies)
        val bound = RootReading(FolderRef("primary:saves", "saves"), true, emptyList(), "placed", true, null, null)
        val live = FolderRef("primary:saves/melonDS", "saves/melonDS")
        val candidate = RootReading(live, false, emptyList(), null, true, null, null)
        val tree = treeUri(SharedPaths.EXTERNAL_STORAGE_AUTHORITY, live.identity)
        val before = ProbeSnapshot(100, 1, player.packageNames.first(), listOf(bound, candidate))
        val after = before.copy(atMillis = 500, roots = listOf(bound, candidate.copy(spotHash = "changed")))
        for (game in listOf(owner, owner.copy(remoteKey = "second"))) {
            val name = "Counter-${game.remoteKey}"
            docs.names[local.uri] = "$name.nds"
            docs.files["$tree/$name.sav"] = byteArrayOf(21, 42)
            probes.noteSession(player, game, before, after)
            probes.captureContentReturn(player, game, local, before, after, backupOnly = false,
                readBeside = { error("The ROM folder did not change") }, reachable = { tree })
            copies.keep(game, player)
        }
        assertTrue(store.incidents().single().gameIds.isEmpty())
        assertEquals(2, server.backups.size)
        assertTrue(server.observed.isEmpty())
    }

    @Test fun frozenRomNameStillResolvesTheWatchedSaveAfterTheRomDisappears() {
        val ds = app.foldcade.plugins.melonds.MelonDsPlayer()
        val nes = app.foldcade.plugins.explus.ExPlusEntry().players.single { it.id == "nes-emu.nes" }
        docs.names.remove(local.uri)
        for ((player, rom, file) in listOf(Triple(ds, "Counter.nds", "Counter.sav"), Triple(nes, "Counter.fds", "Counter.zip"))) {
            val game = owner.copy(platformId = player.platformId)
            assertTrue(saves.probeSpots(player, game, local, SAVES).isEmpty())
            val spot = saves.spotsForFileName(player, game, rom, root = SAVES).single()
            assertEquals(SAVES, spot.folderUri)
            assertEquals(file, spot.fileName)
            spot.filter?.let { assertTrue(it.accepts("Counter.fds.sav", ByteArray(0))) }
        }
    }

    @Test fun aLocalOnlyBesideCopyStaysOnTheDevice() = runBlocking {
        val dir = Files.createTempDirectory("local-beside-copy")
        val store = SaveProtectionStore(dir)
        val game = owner.copy(backendId = "unmatched-local", availability = Availability.LocalOnly)
        val incident = incident(IncidentKind.WrongFolder).copy(besideGame = true)
        val spot = SaveSpot("sav", primaryFolderDocument("ROMs/NDS"), "Counter.sav")
        val bytes = byteArrayOf(3, 2, 1)
        store.put(incident)
        SaveIncidentCopies(saves, plugins, store, rommOrigin = { "https://another-server.test" })
            .stageBackup(game, Melon, spot, bytes, incident)
        SaveIncidentCopies(saves, plugins, SaveProtectionStore(dir)).keep(game, Melon)
        val localCopy = saves.queuedCopies().single()
        assertEquals(game.backendId, localCopy.game.backendId)
        assertEquals(null, localCopy.origin)
        assertArrayEquals(bytes, Files.readAllBytes(java.nio.file.Paths.get(java.net.URI(localCopy.slot.contentUri!!))))
        assertTrue(server.backups.isEmpty())
        assertTrue(server.observed.isEmpty())
    }

    @Test fun removingARetroArchOverrideKeepsTheNewestAlternateBytesBeforePlacingRommHead() = runBlocking {
        val pkg = "com.retroarch.aarch64"
        val authority = SharedPaths.EXTERNAL_STORAGE_AUTHORITY
        val bound = treeUri(authority, "primary:RetroArch/saves")
        val overrides = treeUri(authority, "primary:RetroArch/config")
        val alternate = treeUri(authority, "primary:OtherSaves")
        val config = treeUri("$pkg.documents", "$SHARED_STORAGE/Android/data/$pkg/files")
        val target = LaunchTarget.SharedFile("$SHARED_STORAGE/ROMs/GBA/Game.v1.gba", "content://roms/opaque-id")
        docs.names[target.uri] = "Game.v1.gba"
        docs.folders += listOf("$bound/mGBA", "$overrides/mGBA")
        docs.files["$config/retroarch.cfg"] = """
            savefile_directory = "$SHARED_STORAGE/RetroArch/saves"
            rgui_config_directory = "$SHARED_STORAGE/RetroArch/config"
        """.trimIndent().toByteArray()
        val override = "$overrides/mGBA/Game.v1.cfg"
        docs.files[override] = """
            savefile_directory = "$SHARED_STORAGE/OtherSaves"
            sort_savefiles_enable = "false"
        """.trimIndent().toByteArray()
        val alternateFile = "$alternate/Game.v1.srm"
        val a1 = "A1".toByteArray()
        val a2 = "A2".toByteArray()
        docs.files[alternateFile] = a1
        val game = owner.copy(platformId = "game-boy-advance")
        val player = RetroArchEntry().players.filterIsInstance<RetroArchPlayer>().single { it.platformId == game.platformId }
        player.bindSaveFolder(bound)
        val files = RetroArchSaveFiles(listOf(bound, overrides, alternate, config).map { SaveFolderGrant(it, true, false) },
            "app.foldcade", pkg, docs, null)
        val root = RootReading(FolderRef("primary:RetroArch/saves", "RetroArch/saves"), true, emptyList(), null, true, null, null)
        val directory = Files.createTempDirectory("retroarch-config-incident")
        var store = SaveProtectionStore(directory)
        var copies = SaveIncidentCopies(saves, plugins, store)
        fun checkLaunch() {
            val reading = files.savePath(player, target, bound)
            assertEquals("$SHARED_STORAGE/RetroArch/saves/mGBA/Game.v1.srm", reading.expected)
            val probes = SaveProbes(android.content.ContextWrapper(null), saves, store, { bound }, copies)
            probes.checkRetroArchLaunch(player, game, ProbeSnapshot(100, 1, pkg, listOf(root), reading), files)
            store.incidents().firstOrNull { it.blocksLaunch }?.let { throw SaveProtectionStop(it) }
        }

        assertTrue(runCatching { checkLaunch() }.exceptionOrNull() is SaveProtectionStop)
        assertEquals("$SHARED_STORAGE/OtherSaves/Game.v1.srm", store.incidents().single().alternateSavePath)
        assertEquals(md5Hex(a1), saves.queuedCopies().single().slot.contentHash)
        copies.keep(game, player)
        assertEquals(listOf(md5Hex(a1)), server.backups)
        store = SaveProtectionStore(directory)
        copies = SaveIncidentCopies(saves, plugins, store)
        assertTrue(runCatching { checkLaunch() }.exceptionOrNull() is SaveProtectionStop)
        assertTrue(saves.queuedCopies().isEmpty()) // A1 already verified, including after restart.

        docs.files[alternateFile] = a2 // Played directly in RetroArch before removing the override.
        docs.files.remove(override)
        docs.denied += alternateFile
        assertTrue(runCatching { checkLaunch() }.exceptionOrNull() is SaveProtectionStop)
        docs.denied.clear()
        server.toPlace = "RomM head"
        server.placementSlot = "srm"
        val launch = LaunchSaves(saves, plugins, protectionGate = { _, _, _ ->
            checkLaunch()
            assertEquals(md5Hex(a2), saves.queuedCopies().single().slot.contentHash)
            assertTrue(server.calls.isEmpty())
        }, preserveOutside = copies::preserveBeforeLaunch) { awaiting += it }
        launch.prepare(GameRoute("tile", game, game), player, target)
        assertTrue(store.incidents().isEmpty())
        assertEquals(listOf(md5Hex(a1), md5Hex(a2)), server.backups)
        assertEquals("RomM head", docs.files.getValue("$bound/mGBA/Game.v1.srm").decodeToString())
        assertArrayEquals(a2, docs.files.getValue(alternateFile))
        assertTrue(server.observed.isEmpty())
    }

    @Test fun anOldRetroArchReturnKeepsAPreviouslyGuessedSaveFileOnlyAsABackup() = runBlocking {
        val ds = RetroArchEntry().players.filterIsInstance<RetroArchPlayer>().single { it.platformId == "nintendo-ds" }
        val oldSlot = SaveSpot("srm", SAVES, "melonDS/Tetris DS (USA).srm")
        val bytes = "older session's progress".toByteArray()
        docs.folders += "$SAVES/melonDS"
        docs.files["$SAVES/${oldSlot.fileName}"] = bytes
        val oldReturn = LibraryReturn(owner.backendId, owner, ds.withCore("melonds"), listOf(oldSlot), local.uri)
        val restored = decodeLibraryReturn(oldReturn.encode()) { ds }!!
        val store = SaveProtectionStore(Files.createTempDirectory("retroarch-legacy-return"))
        val copies = SaveIncidentCopies(saves, plugins, store)
        assertTrue(copies.preserveUnsupportedRetroArchReturn(restored))
        assertTrue(returnSpots(saves, restored).isEmpty())
        assertEquals(listOf(oldSlot), returnSpots(saves, restored.copy(player = ds.withCore("melondsds"))))
        copies.keep(owner, restored.player)
        assertEquals(listOf(md5Hex(bytes)), server.backups)
        assertTrue(server.calls.isEmpty())
        assertTrue(server.observed.isEmpty())
    }

    @Test fun bypassReturnsKeepEveryCopyWithoutReconciliationOrClearingTheAlarm() = runBlocking {
        for (kind in listOf(IncidentKind.WrongFolder, IncidentKind.FolderEmptied, IncidentKind.LostAccess)) {
            val store = SaveProtectionStore(Files.createTempDirectory("incident"))
            store.put(incident(kind))
            val copies = SaveIncidentCopies(saves, plugins, store)
            copies.beginUnprotected(owner, Melon)
            val spots = saves.spots(Melon, owner, local)
            for (progress in listOf("fresh", "more progress")) {
                docs.files["$SAVES/Tetris DS (USA).sav"] = progress.toByteArray()
                assertTrue(copies.preserveReturn(owner, Melon, spots, preserveAsBackup = true))
                assertTrue(saves.changedSince(owner.backendId, owner, Melon, spots).slots.isEmpty())
                val open = store.incidents().single()
                assertTrue(open.recoveryRequired)
                assertTrue(open.blocksLaunch)
                assertFalse(open.causeResolved(RootReading(open.bound!!, true, emptyList(), "fresh", true, true, true)))
            }
            assertTrue(server.calls.isEmpty())
            assertTrue(server.observed.isEmpty())
            assertEquals(2, store.incidents().single().kept.size)
        }
    }

    @Test fun failedBackupUploadKeepsDurableStagingAndMarksSoItCannotBecomeHead() = runBlocking {
        docs.files["$SAVES/Tetris DS (USA).sav"] = "live".toByteArray()
        server.backupsFail = true
        val store = SaveProtectionStore(Files.createTempDirectory("incident"))
        val incident = incident(IncidentKind.WrongFolder)
        store.put(incident)
        val copies = SaveIncidentCopies(saves, plugins, store)
        val spots = saves.spots(Melon, owner, local)
        assertTrue(copies.preserveReturn(owner, Melon, spots, preserveAsBackup = true))
        val staged = store.incidents().single().staged.single()
        assertEquals("live", Files.readString(java.nio.file.Paths.get(java.net.URI(staged))))
        assertTrue(saves.changedSince(owner.backendId, owner, Melon, spots).slots.isEmpty())
        assertTrue(server.calls.isEmpty())
        server.backupsFail = false
        copies.keep(owner, Melon)
        assertTrue(store.incidents().single().staged.isEmpty())
    }

    @Test fun ordinaryReturnsAndUngatedOptOutsUseNormalReconciliation() = runBlocking {
        val store = SaveProtectionStore(Files.createTempDirectory("incident"))
        val copies = SaveIncidentCopies(saves, plugins, store)
        val spots = saves.spots(Melon, owner, local)
        val ordinary = copies.beginUnprotected(owner, Melon)
        assertFalse(ordinary)
        assertFalse(copies.preserveReturn(owner, Melon, spots, preserveAsBackup = ordinary))
        store.put(incident(IncidentKind.WrongFolder))
        assertFalse(copies.preserveReturn(owner, Melon, spots, preserveAsBackup = false))
    }

    @Test fun existingRunsResumeAndKeepTheirProgressAsBackupsWhileFreshLaunchesStayGated() = runBlocking {
        val bytesKept = mutableListOf<String>()
        for (kind in listOf(IncidentKind.WrongFolder, IncidentKind.FolderEmptied, IncidentKind.LostAccess)) {
            val dir = Files.createTempDirectory("resume-save-protection")
            val store = SaveProtectionStore(dir)
            val warning = incident(kind)
            store.put(warning)
            val copies = SaveIncidentCopies(saves, plugins, store)
            val probes = SaveProbes(android.content.ContextWrapper(null), saves, store, { SAVES }, copies)
            var gateReads = 0
            val fresh = LaunchSaves(saves, plugins, protectionGate = { route, player, eden ->
                gateReads++
                probes.gate(player, route.saves, local, eden = eden)
            }) { awaiting += it }
            val route = GameRoute("tile", owner, owner)
            val pending = LibraryReturn(owner.backendId, owner, Melon, saves.spots(Melon, owner, local), local.uri)
            val running = RunningGame(route.tileId, owner.label, owner.platformId, "melon", null, Panel.Top, false, 100, pending)

            assertEquals(null, copies.resume(running) { false })
            assertFalse(store.pendingBackup(Melon.id, owner))
            var starts = 0
            val resumed = copies.resume(running) { starts++; true }!!
            assertEquals(1, starts)
            assertEquals(0, gateReads)
            val restored = decodeLibraryReturn(resumed.saves!!.encode()) { Melon }!!
            assertTrue(restored.preserveAsBackup)
            assertFalse(restored.startedByFoldcade)
            assertTrue(SaveProtectionStore(dir).incidents().single().recoveryRequired)

            val bytes = "resumed progress after $kind".toByteArray()
            docs.files["$SAVES/Tetris DS (USA).sav"] = bytes
            assertTrue(copies.preserveReturn(restored.game, restored.player, restored.spots, restored.preserveAsBackup))
            bytesKept += md5Hex(bytes)
            assertEquals(bytesKept, server.backups)
            assertTrue(server.observed.isEmpty())
            assertTrue(store.incidents().single().blocksLaunch)

            assertTrue(runCatching { fresh.prepare(route, Melon, local) }.exceptionOrNull() is SaveProtectionStop)
            assertEquals(1, gateReads)
            assertTrue(server.calls.isEmpty())
        }
    }

    @Test fun aResumedBackupLeavesTheExistingTwoSavesChoiceIntact() = runBlocking {
        val dir = Files.createTempDirectory("resume-save-regression")
        val store = SaveProtectionStore(dir)
        val copies = SaveIncidentCopies(saves, plugins, store)
        val warning = incident(IncidentKind.WrongFolder)
        store.put(warning)
        val reset = "quarantined reset".toByteArray()
        val subject = incidentGame(owner, Melon, local.uri, "https://romm.example", null)
        val quarantine = warning.copy(
            id = regressionIncidentId(owner, Melon), kind = IncidentKind.MaybeNewSave, gameIds = listOf(owner.remoteKey),
            subject = subject, quarantinedHash = md5Hex(reset), quarantineBackupId = "reset-id",
            repair = RepairState.Choosing("head-id", "reset-id", "head-hash", suspect = true),
        )
        store.put(quarantine)
        val pending = LibraryReturn(owner.backendId, owner, Melon, saves.spots(Melon, owner, local), local.uri)
        val running = RunningGame("tile", owner.label, owner.platformId, "melon", null, Panel.Top, false, 100, pending)
        val resumed = decodeLibraryReturn(copies.resume(running) { true }!!.saves!!.encode()) { Melon }!!
        assertTrue(resumed.preserveAsBackup)

        // A repair can finish before this session returns. Its record must still carry the latch.
        store.close(warning.id)
        store.handled(Melon.id, owner)
        assertFalse(store.pendingBackup(Melon.id, owner))
        val bytes = "later resumed progress".toByteArray()
        docs.files["$SAVES/Tetris DS (USA).sav"] = bytes
        assertTrue(copies.preserveReturn(resumed.game, resumed.player, resumed.spots, resumed.preserveAsBackup))
        assertEquals(listOf(md5Hex(bytes)), server.backups)
        assertTrue(server.observed.isEmpty())
        val regression = SaveRegression(saves, store, baseline = { _, _, _ -> error("Already preserved") })
        val (observed, returned) = regression.capture(owner.backendId, owner, Melon, resumed.spots)
        assertTrue(observed.slots.isEmpty())
        assertEquals(null, returned)
        assertEquals(quarantine, SaveProtectionStore(dir).incident(quarantine.id))
    }

    @Test fun duckStationCardMismatchKeepsChangedCopiesOutOfTheSlotAndIsScopedToTheGame() = runBlocking {
        with(DuckStationReturnFixture()) {
            val other = game.copy(remoteKey = "another game")
            val head = "existing head".toByteArray()
            docs.files["$hostedUri/$expected"] = head
            val placed = saves.changedSince(server.id, game, duck, spots)
            saves.noteHandedOver(server.id, game, duck, spots, placed)
            opened(alternate, 110, "r")
            judge()
            val incident = store.incidents().single()
            assertEquals(IncidentKind.MaybeNewSave, incident.kind)
            assertEquals(alternate, incident.live?.label)
            assertFalse(incident.blocksLaunch)
            assertTrue(copies.preserveReturn(game, duck, spots, preserveAsBackup = false))
            assertTrue(server.backups.isEmpty())
            assertFalse(copies.preserveReturn(other, duck, spots, preserveAsBackup = false))

            val live = "live card progress".toByteArray()
            docs.files["$hostedUri/$alternate"] = live
            probes.stageDuckStationReturn(duck, other, spots, 100, 200)
            probes.stageDuckStationReturn(duck, game, spots, 111, 200)
            assertTrue(saves.queuedCopies().isEmpty())
            probes.stageDuckStationReturn(duck, game, spots, 100, 200)
            assertEquals(md5Hex(live), saves.queuedCopies().single().slot.contentHash)

            val changed = "uncertain card progress".toByteArray()
            docs.files["$hostedUri/$expected"] = changed
            assertTrue(copies.preserveReturn(game, duck, spots, preserveAsBackup = false))
            assertEquals(setOf(md5Hex(live), md5Hex(changed)), server.backups.toSet())
            assertTrue(saves.changedSince(server.id, game, duck, spots).slots.isEmpty())
            assertTrue(server.observed.isEmpty())
            assertTrue(server.calls.isEmpty())
        }
    }

    @Test fun duckStationFlushFromPreviousGameCreatesNeitherIncidentNorBackup() = runBlocking {
        with(DuckStationReturnFixture()) {
            docs.files["$hostedUri/$alternate"] = "previous game's progress".toByteArray()
            opened(alternate, 120, "w")
            judge()
            probes.stageDuckStationReturn(duck, game, spots, 100, 200)
            assertFalse(copies.preserveReturn(game, duck, spots, preserveAsBackup = false))
            assertTrue(store.incidents().isEmpty())
            assertTrue(saves.queuedCopies().isEmpty())
            assertTrue(server.backups.isEmpty())
            assertEquals(LocationFact.Bound(bound), probes.location(duck, reading))
        }
    }

    @Test fun duckStationSameStartAfterClosingUsesTheSerialCardFromTheNewBoot() = runBlocking {
        val title = "Castlevania - Symphony of the Night (USA)_1.mcd"
        val serial = "SLUS-00067_1.mcd"
        with(DuckStationReturnFixture(expected = title)) {
            val serialBytes = ByteArray(128 * 1024) { 2 }
            docs.files["$hostedUri/$title"] = ByteArray(128 * 1024) { 1 }
            docs.files["$hostedUri/$serial"] = serialBytes
            saves.noteHandedOver(server.id, game, duck, spots, saves.changedSince(server.id, game, duck, spots))
            val first = LibraryReturn(game.backendId, game, duck, spots, baseline = reading.copy(atMillis = 90),
                sessionStartedAtMillis = 100, sessionEndedAtMillis = 200)
            opened(title, 110, "r", HostedCardClose.Detached)
            judge(start = first.sessionStartedAtMillis!!, end = first.sessionEndedAtMillis!!, startedByFoldcade = true)
            assertTrue(store.hostedCardChecked(duck.id, game))
            assertTrue(store.incidents().isEmpty())
            assertEquals(OffDeviceFact.Unknown, readSaveReceipt(saves, server, game, spots))

            // DuckStation was closed and changed to Serial in its own settings. Foldcade
            // still holds the run, so the same start boots it using the new card type.
            val running = RunningGame(homeGameId(game.backendId, game.remoteKey), game.label, game.platformId,
                duck.packageNames.single(), DuckStationPlayer.EMULATION_ACTIVITY, Panel.Bottom, false, 100, first)
            val resumed = copies.resume(running, sessionStartedAtMillis = 300) { true }!!.saves!!
            assertEquals(null, resumed.sessionEndedAtMillis)
            assertFalse(resumed.startedByFoldcade)
            opened(serial, 310, "r", HostedCardClose.Detached)
            val start = resumed.sessionStartedAtMillis!!
            val end = 400L
            judge(start = maxOf(start, resumed.baseline!!.atMillis), end = end, startedByFoldcade = resumed.startedByFoldcade)
            probes.stageDuckStationReturn(duck, game, spots, start, end, startedByFoldcade = resumed.startedByFoldcade)
            assertTrue(copies.preserveReturn(game, duck, spots, preserveAsBackup = false))
            copies.keep(game, duck)

            val incident = store.incidents().single()
            assertEquals(serial, incident.live?.label)
            assertEquals("Using SLUS-00067_1.mcd. Check before you play.", SaveProtectionCopy.noticeBody(incident))
            assertEquals(listOf(md5Hex(serialBytes)), server.backups)
            assertEquals(1, incident.kept.size)
            assertTrue(saves.queuedCopies().isEmpty())
            assertTrue(server.observed.isEmpty())
            assertTrue(server.calls.isEmpty())
        }
    }

    @Test fun duckStationWarmResumeKeepsNewBytesFromThePreviouslyOpenedCardOnce() = runBlocking {
        with(DuckStationReturnFixture()) {
            val serial = "SLUS-00067_1.mcd"
            val initial = ByteArray(128 * 1024) { 1 }
            val changed = initial.copyOf().also { it[0] = 2 }
            val card = "$hostedUri/$serial"
            docs.files[card] = initial
            opened(serial, 110, "r", HostedCardClose.Detached)
            judge(start = 100, end = 200, startedByFoldcade = true)
            probes.stageDuckStationReturn(duck, game, spots, 100, 200)
            copies.keep(game, duck)
            assertEquals(listOf(md5Hex(initial)), server.backups)
            judge(start = 100, end = 200, startedByFoldcade = true)
            probes.stageDuckStationReturn(duck, game, spots, 100, 200)
            assertTrue(saves.queuedCopies().isEmpty())
            val incident = store.incidents().single()

            val first = LibraryReturn(game.backendId, game, duck, spots, sessionStartedAtMillis = 100, sessionEndedAtMillis = 200)
            val running = RunningGame(homeGameId(game.backendId, game.remoteKey), game.label, game.platformId,
                duck.packageNames.single(), DuckStationPlayer.EMULATION_ACTIVITY, Panel.Bottom, false, 100, first)
            val resumed = copies.resume(running, sessionStartedAtMillis = 300) { true }!!.saves!!
            val start = resumed.sessionStartedAtMillis!!
            // The activity is still running. Saving writes a temp file and publishes the same card.
            opened("card.tmp", 350, "w", HostedCardClose.Detached)
            published("card.tmp", serial, 352)
            docs.files[card] = changed
            judge(start = start, end = 400, startedByFoldcade = resumed.startedByFoldcade)
            assertEquals(incident, store.incidents().single())
            probes.stageDuckStationReturn(duck, game, spots, start, 400, startedByFoldcade = resumed.startedByFoldcade)
            assertEquals(listOf(md5Hex(changed)), saves.queuedCopies().map { it.slot.contentHash })
            assertTrue(copies.preserveReturn(game, duck, spots, preserveAsBackup = resumed.preserveAsBackup))
            copies.keep(game, duck)

            probes.stageDuckStationReturn(duck, game, spots, start, 400, startedByFoldcade = resumed.startedByFoldcade)
            assertTrue(saves.queuedCopies().isEmpty())
            assertEquals(listOf(md5Hex(initial), md5Hex(changed)), server.backups)
            assertTrue(server.observed.isEmpty())
        }
    }

    @Test fun duckStationFolderStopRequiresNewerMatchingConfigBeforeAnOpenCanVerify() {
        with(DuckStationReturnFixture()) {
            opened(expected, 110, "r")
            judge(end = 115)
            assertTrue(store.hostedCardChecked(duck.id, game))
            assertEquals(RootVerdict.NoNews, check(100, "/sdcard/Old Cards"))

            val moved = check(130, "/sdcard/Scratch Cards")
            assertEquals(FolderRef("primary:Scratch Cards", "Scratch Cards"), (moved as RootVerdict.UsesOther).live)
            opened("tmp", 140, "w")
            published("tmp", expected, 141)
            judge(moved)
            assertTrue(probes.location(duck, reading) is LocationFact.Mismatch)
            assertFalse(store.hostedCardChecked(duck.id, game))

            assertEquals(RootVerdict.NoNews, check(130, hostedPath))
            judge() // A missing config reading must also leave the stored stop in place.
            assertEquals(130L, store.incidents().single().openedAtMillis)
            assertTrue(store.incidents().single().blocksLaunch)
            assertFalse(store.hostedCardChecked(duck.id, game))
            assertEquals(RootVerdict.NoNews, check(129, hostedPath))
            assertTrue(store.incidents().single().blocksLaunch)

            assertEquals(RootVerdict.NoNews, check(150, hostedPath))
            assertTrue(store.incidents().isEmpty())
            judge() // An open before the confirmed repair cannot verify the rebound folder.
            assertEquals(LocationFact.Bound(bound, 150), probes.location(duck, reading))
            assertEquals(RootVerdict.NoNews, check(145, "/sdcard/Scratch Cards"))
            assertEquals(RootVerdict.NoNews, check(150, "/sdcard/Scratch Cards"))
            assertTrue(store.incidents().isEmpty())

            opened(expected, 160, "r")
            judge(start = 151)
            assertEquals(LocationFact.Verified(bound, Evidence(EvidenceKind.HostedOpen, expected), 160), probes.location(duck, reading))
            assertTrue(store.hostedCardChecked(duck.id, game))
        }
    }

    @Test fun duckStationSilenceAfterAConfirmedLaunchIsDismissibleAndLaterProofClearsIt() = runBlocking {
        with(DuckStationReturnFixture()) {
            opened(expected, 110, "r")
            judge()
            assertTrue(store.hostedCardChecked(duck.id, game))

            judge(start = 1_000, end = 21_000, startedByFoldcade = true, gameReached = true)
            val incident = store.incidents().single()
            assertEquals(IncidentKind.MaybeWrongFolder, incident.kind)
            assertTrue(canConfirmSaveLocation(incident))
            assertFalse(incident.blocksLaunch)
            assertFalse(copies.preserveReturn(game, duck, spots, preserveAsBackup = false))
            assertFalse(store.hostedCardChecked(duck.id, game))
            assertEquals(LocationFact.Bound(bound, 110), probes.location(duck, reading))

            judge() // A retried read from the older game cannot clear a later warning.
            assertEquals(incident, store.incidents().single())
            opened(expected, 22_000, "r")
            judge(start = 22_000, end = 23_000)
            assertTrue(store.incidents().isEmpty())
            assertTrue(store.hostedCardChecked(duck.id, game))
        }
    }

    @Test fun duckStationSilenceSkipsShortResumedUnconfirmedAndDisabledSessions() {
        with(DuckStationReturnFixture()) {
            val start = 1_000L
            val end = 21_000L
            judge(start = start, end = end - 1, startedByFoldcade = true, gameReached = true)
            judge(start = start, end = end, gameReached = true)
            judge(start = start, end = end, startedByFoldcade = true)
            judge(start = start, end = end, startedByFoldcade = true, gameReached = true, hadExpectedCard = false)
            judge(start = start, end = end, startedByFoldcade = true, gameReached = true, config = config(hostedPath, "None"))
            assertTrue(store.incidents().isEmpty())
            // Card 2 counts as activity even though it cannot verify card 1.
            opened("Other slot_2.mcd", 2_000, "r")
            judge(start = start, end = end, startedByFoldcade = true, gameReached = true)
            assertTrue(store.incidents().isEmpty())
        }
        with(DuckStationReturnFixture(recordingSince = 2_000)) {
            judge(start = 1_000, end = 21_000, startedByFoldcade = true, gameReached = true)
            assertTrue(store.incidents().isEmpty())
        }
    }

    private inner class DuckStationReturnFixture(recordingSince: Long = 0, val expected: String = "RomM name_1.mcd") {
        private val appPackage = "app.foldcade"
        val duck = DuckStationPlayer()
        val game = owner.copy(platformId = duck.platformId)
        private val caller = HostedCardCaller(1443, DuckStationPlayer.PACKAGE)
        private val rootId = HostedFolders.rootId(duck.id)
        val hostedUri = treeUri("$appPackage.saves", rootId)
        val hostedPath = "/android_content/$appPackage.saves/tree@foldcade/${duck.id}"
        val bound = FolderRef(storageIdentity(hostedUri), duck.hostedFolderTitle)
        val reading = ProbeSnapshot(200, 10, caller.packageName, listOf(RootReading(bound, true, emptyList(), null, true, null, true)))
        val alternate = "DuckStation title_1.mcd"
        val spots = listOf(SaveSpot(DuckStationPlayer.CARD_SLOT, hostedUri, expected))
        val store = SaveProtectionStore(Files.createTempDirectory("duckstation-return"))
        private val opens = HostedCardOpens(recordingSinceMillis = recordingSince)
        val copies = SaveIncidentCopies(saves, plugins, store)
        private val context = object : android.content.ContextWrapper(null) {
            override fun getPackageName() = appPackage
        }
        val probes = SaveProbes(context, saves, store, { null }, copies, hostedCardOpens = opens)

        fun opened(name: String, at: Long, mode: String, closeStatus: HostedCardClose? = null) {
            val id = opens.opened(HostedCardOpen(caller, "$rootId/$name", mode, canRead = 'r' in mode, canWrite = 'w' in mode, openedAtMillis = at))
            if (closeStatus != null) opens.closed(id, at + 1, closeStatus)
        }

        fun published(from: String, to: String, at: Long) =
            opens.published(caller, "$rootId/$from", "$rootId/$to", at)

        fun config(directory: String, type: String = "PerGameTitle") =
            DuckStationSaveEvidence(listOf(DuckStationCardSettings(directory, type, null, null)), duck.suggestedSaveFolder, appPackage)

        fun check(at: Long, directory: String) = probes.checkDuckStationConfig(duck, reading, at to config(directory))

        fun judge(verdict: RootVerdict = RootVerdict.NoNews, start: Long = 100, end: Long = 200,
            startedByFoldcade: Boolean = false, gameReached: Boolean = false, config: DuckStationSaveEvidence? = null,
            hadExpectedCard: Boolean = true) =
            probes.judgeDuckStationReturn(duck, game, reading.copy(atMillis = end), config, verdict, caller, spots,
                start, end, startedByFoldcade, hadExpectedCard) { gameReached }
    }

    @Test fun offlineFixThenPlayAndIncidentClosureStillUploadsItsBackupAfterRestartAndReconnect() = runBlocking {
        val directory = Files.createTempDirectory("incident")
        val store = SaveProtectionStore(directory)
        val incident = incident(IncidentKind.WrongFolder)
        store.put(incident)
        val copies = SaveIncidentCopies(saves, plugins, store)
        val spots = saves.spots(Melon, owner, local)
        docs.files["$SAVES/Tetris DS (USA).sav"] = "live before Fix".toByteArray()
        server.backupsFail = true
        copies.stage(owner, Melon, spots, incident, mark = true)
        copies.keep(owner, Melon)
        val queued = saves.queuedCopies().single()
        val file = java.nio.file.Paths.get(java.net.URI(queued.slot.contentUri!!))
        val sidecar = file.resolveSibling("${file.fileName}.json")
        store.put(store.incidents().single().copy(repair = RepairState.Done))
        server.toPlace = "RomM head"
        LaunchSaves(saves, plugins, preserveOutside = copies::preserveBeforeLaunch) { awaiting += it }
            .prepare(GameRoute("tile", play, owner), Melon, local)
        assertEquals("RomM head", docs.files.getValue("$SAVES/Tetris DS (USA).sav").toString(Charsets.UTF_8))
        docs.files["$SAVES/Tetris DS (USA).sav"] = "normal progress".toByteArray()
        val observed = saves.changedSince(owner.backendId, owner, Melon, spots)
        plugins.reconcile(owner.backendId, owner, Melon, observed)
        saves.noteHandedOver(owner.backendId, owner, Melon, spots, observed)
        copies.keep(owner, Melon) // still offline
        val probes = SaveProbes(android.content.ContextWrapper(null), saves, store, { SAVES }, copies)
        val reading = RootReading(incident.bound!!, true, emptyList(), "normal", true, true, true)
        probes.apply(Melon, ProbeSnapshot(200, 1, "emulator", listOf(reading)), RootVerdict.Uses(reading.root, incident.evidence, 200))
        assertTrue(store.incidents().isEmpty())
        assertTrue(Files.exists(file))
        assertTrue(Files.exists(sidecar))
        val restartedSaves = PlayerSaves(docs, Marks(marks), Files.createTempDirectory("backups"), handover)
        val restarted = SaveIncidentCopies(restartedSaves, plugins, SaveProtectionStore(directory))
        server.backupsFail = false
        restarted.keepAll(owner.backendId)
        assertEquals(listOf(md5Hex("live before Fix".toByteArray())), server.backups)
        assertEquals(listOf(md5Hex("normal progress".toByteArray())), server.observed)
        assertTrue(restartedSaves.queuedCopies().isEmpty())
        assertFalse(Files.exists(file))
        assertFalse(Files.exists(sidecar))
    }

    @Test fun aReboundFenceBacksUpGameBFirstSeenAfterRestartInsteadOfPromotingIt() = runBlocking {
        val directory = Files.createTempDirectory("handover-fence")
        val store = SaveProtectionStore(directory)
        store.rebound(Melon.id, 100)
        store.handled(Melon.id, owner) // Fix only saw game A in the previous process.
        val gameB = owner.copy(remoteKey = "other", label = "Other")
        val target = LaunchTarget.ContentUri("content://other")
        docs.names[target.uri] = "Other.nds"
        docs.files["$SAVES/Other.sav"] = "live B".toByteArray()
        server.toPlace = "RomM B head"
        val restored = SaveProtectionStore(directory)
        val copies = SaveIncidentCopies(saves, plugins, restored)
        val launch = LaunchSaves(saves, plugins, preserveOutside = copies::preserveBeforeLaunch) { awaiting += it }
        launch.prepare(GameRoute("other-tile", gameB, gameB), Melon, target)
        assertEquals(listOf("prepareLaunch:other"), server.calls)
        assertTrue(server.observed.isEmpty())
        assertEquals(listOf(md5Hex("live B".toByteArray())), server.backups)
        assertEquals("RomM B head", docs.files.getValue("$SAVES/Other.sav").toString(Charsets.UTF_8))
        assertFalse(SaveProtectionStore(directory).needsBackup(Melon.id, gameB))
        launch.prepare(GameRoute("other-tile", gameB, gameB), Melon, target)
        assertEquals(1, server.backups.size)
    }

    @Test fun aVerifiedHeadPlacementClearsTheFenceForAnAbsentOrMatchingFirstCopy() = runBlocking {
        val file = "$SAVES/Tetris DS (USA).sav"
        for (matching in listOf(false, true)) {
            server.calls.clear(); server.observed.clear(); server.backups.clear()
            docs.files.remove(file)
            server.toPlace = "RomM head"
            val spots = saves.spots(Melon, owner, local)
            if (matching) {
                docs.files[file] = "RomM head".toByteArray()
                assertTrue(saves.place(owner.backendId, owner, Melon, spots, listOf(PlacedSave("sav", "lib://server", md5Hex("RomM head".toByteArray())))))
            }
            val directory = Files.createTempDirectory("placement-fence")
            val store = SaveProtectionStore(directory)
            store.rebound(Melon.id, 100)
            val copies = SaveIncidentCopies(saves, plugins, store)
            val launch = LaunchSaves(saves, plugins, preserveOutside = copies::preserveBeforeLaunch,
                onHeadPlaced = copies::headPlaced) { awaiting += it }
            launch.prepare(GameRoute("tile", play, owner), Melon, local)
            assertEquals("RomM head", String(docs.files.getValue(file)))
            assertFalse(SaveProtectionStore(directory).needsBackup(Melon.id, owner))
            docs.files[file] = "later legitimate progress".toByteArray()
            server.toPlace = "later legitimate progress"
            launch.prepare(GameRoute("tile", play, owner), Melon, local)
            assertEquals(listOf(md5Hex("later legitimate progress".toByteArray())), server.observed)
            assertTrue(server.backups.isEmpty())
        }
    }

    @Test fun failedNonStrictReadsAndFailedPlacementDoNotClearTheFence() = runBlocking {
        val store = SaveProtectionStore(Files.createTempDirectory("unreadable-fence"))
        store.rebound(Melon.id, 100)
        val copies = SaveIncidentCopies(saves, plugins, store)
        server.toPlace = "RomM head"
        docs.files["$SAVES/Tetris DS (USA).sav"] = "unreadable outside progress".toByteArray()
        docs.unreadable = true
        val launch = LaunchSaves(saves, plugins, preserveOutside = copies::preserveBeforeLaunch,
            onHeadPlaced = copies::headPlaced) { awaiting += it }
        assertTrue(runCatching { launch.prepare(GameRoute("tile", play, owner), Melon, local) }.exceptionOrNull() is SaveNotPlaced)
        assertTrue(store.needsBackup(Melon.id, owner))
        assertTrue(server.observed.isEmpty())
        assertEquals("unreadable outside progress", String(docs.files.getValue("$SAVES/Tetris DS (USA).sav")))
    }

    @Test fun anUnresolvableSpotKeepsItsPendingLatchWithoutInventingALaunchStop() = runBlocking {
        val unresolved = object : Player by Melon {
            override fun saveFileName(slot: String, romFileName: String): String? = null
        }
        val store = SaveProtectionStore(Files.createTempDirectory("unresolved-return"))
        store.requireBackup(unresolved.id, owner)
        val copies = SaveIncidentCopies(saves, plugins, store)
        val launch = LaunchSaves(saves, plugins, preserveOutside = copies::preserveBeforeLaunch,
            onHeadPlaced = copies::headPlaced) { awaiting += it }
        val prepared = launch.prepare(GameRoute("tile", owner, owner), unresolved, local)
        assertTrue(prepared.spots.isEmpty())
        assertTrue(store.pendingBackup(unresolved.id, owner))
        assertTrue(store.needsBackup(unresolved.id, owner))
        assertEquals(listOf("prepareLaunch:12"), server.calls)
        assertTrue(server.observed.isEmpty())
    }

    @Test fun aFencedOptOutLatchesWithoutAnIncidentAndKeepsItsReturnAsABackup() = runBlocking {
        val store = SaveProtectionStore(Files.createTempDirectory("fenced-opt-out"))
        store.rebound(Melon.id, 100)
        assertTrue(store.incidents().isEmpty())
        assertFalse(store.pendingBackup(Melon.id, owner))
        val copies = SaveIncidentCopies(saves, plugins, store)
        val backupOnly = copies.beginUnprotected(owner, Melon)
        assertTrue(backupOnly)
        assertTrue(store.pendingBackup(Melon.id, owner))
        docs.files["$SAVES/Tetris DS (USA).sav"] = "opt-out progress".toByteArray()
        assertTrue(copies.preserveReturn(owner, Melon, saves.spots(Melon, owner, local), backupOnly))
        assertEquals(listOf(md5Hex("opt-out progress".toByteArray())), server.backups)
        assertTrue(server.observed.isEmpty())
    }

    @Test fun aStartedBypassRemainsBackupOnlyIfFixClosesItsIncidentBeforeReturn() = runBlocking {
        val directory = Files.createTempDirectory("bypass-return")
        val store = SaveProtectionStore(directory)
        val incident = incident(IncidentKind.WrongFolder)
        store.put(incident)
        val copies = SaveIncidentCopies(saves, plugins, store)
        LaunchSaves(saves, plugins) { awaiting += it }.prepare(GameRoute("tile", owner, owner), Melon, local, syncing = false)
        // Preparation, cancelling, or a refused Android start cannot set this latch.
        assertFalse(store.incidents().single().recoveryRequired)
        assertFalse(store.pendingBackup(Melon.id, owner))
        val backupOnly = copies.beginUnprotected(owner, Melon) // actual successful start
        assertTrue(backupOnly)
        val pending = LibraryReturn(owner.backendId, owner, Melon, targetUri = local.uri, unprotectedSession = true, preserveAsBackup = backupOnly)
        store.close(incident.id) // guided Fix completed from R1 while the emulator remained open
        val restored = SaveProtectionStore(directory)
        val returned = decodeLibraryReturn(pending.encode()) { Melon }!!
        docs.files["$SAVES/Tetris DS (USA).sav"] = "bypass progress".toByteArray()
        val restarted = SaveIncidentCopies(saves, plugins, restored)
        assertTrue(restarted.preserveReturn(returned.game, returned.player, returnSpots(saves, returned), returned.preserveAsBackup))
        assertEquals(listOf(md5Hex("bypass progress".toByteArray())), server.backups)
        assertTrue(server.calls.isEmpty())
        assertTrue(server.observed.isEmpty())
        assertFalse(restored.pendingBackup(Melon.id, owner))
    }

    @Test fun aFailedStrictBypassReadKeepsItsLatchAndStopsTheNextPlacementUntilItCanStage() = runBlocking {
        val directory = Files.createTempDirectory("bypass-retry")
        val store = SaveProtectionStore(directory)
        store.put(incident(IncidentKind.WrongFolder))
        val copies = SaveIncidentCopies(saves, plugins, store)
        val backupOnly = copies.beginUnprotected(owner, Melon)
        val spots = saves.spots(Melon, owner, local)
        docs.files["$SAVES/Tetris DS (USA).sav"] = "unread progress".toByteArray()
        docs.unreadable = true
        assertTrue(runCatching { copies.preserveReturn(owner, Melon, spots, backupOnly) }.exceptionOrNull() is SaveNotPlaced)
        store.close(store.incidents().single().id)
        val restored = SaveProtectionStore(directory)
        val restarted = SaveIncidentCopies(saves, plugins, restored)
        server.toPlace = "RomM head"
        val launch = LaunchSaves(saves, plugins, preserveOutside = restarted::preserveBeforeLaunch) { awaiting += it }
        assertTrue(runCatching { launch.prepare(GameRoute("tile", play, owner), Melon, local) }.exceptionOrNull() is SaveNotPlaced)
        assertTrue(restored.pendingBackup(Melon.id, owner))
        assertTrue(server.calls.isEmpty())
        assertEquals("unread progress", docs.files.getValue("$SAVES/Tetris DS (USA).sav").toString(Charsets.UTF_8))
        docs.unreadable = false
        launch.prepare(GameRoute("tile", play, owner), Melon, local)
        assertEquals(listOf(md5Hex("unread progress".toByteArray())), server.backups)
        assertTrue(server.observed.isEmpty())
        assertFalse(restored.pendingBackup(Melon.id, owner))
    }

    @Test fun storedIncidentsStillGateWhenProbingThrowsButDoneDoesNot() {
        val store = SaveProtectionStore(Files.createTempDirectory("incident"))
        val copies = SaveIncidentCopies(saves, plugins, store)
        val player = object : Player by Melon, app.foldcade.api.plugin.SaveFolderHolder {
            override fun bindSaveFolder(contentUri: String) = Unit
            override fun hasSaveFolder() = true
        }
        val probes = SaveProbes(android.content.ContextWrapper(null), saves, store, { throw java.io.IOException("probe failed") }, copies)
        var publications = 0
        probes.gate(player, owner, local) { publications++; assertEquals(null, it) }
        assertEquals(1, publications)
        store.put(incident(IncidentKind.WrongFolder))
        assertTrue(runCatching { probes.gate(player, owner, local) }.exceptionOrNull() is SaveProtectionStop)
        store.put(incident(IncidentKind.WrongFolder).copy(repair = RepairState.Done))
        probes.gate(player, owner, local)
    }

    @Test fun aRetroArchOverrideOnlyGatesTheAffectedGameAndFencesItsBytes() {
        val store = SaveProtectionStore(Files.createTempDirectory("retroarch-gate"))
        val copies = SaveIncidentCopies(saves, plugins, store)
        val player = app.foldcade.plugins.retroarch.RetroArchEntry().players.single { it.platformId == "game-boy-advance" }
        val gba = owner.copy(platformId = player.platformId)
        val probes = SaveProbes(android.content.ContextWrapper(null), saves, store, { SAVES }, copies)
        val bound = FolderRef("primary:RetroArch/saves", "RetroArch/saves")
        val live = FolderRef("primary:ROMs/GBA", "ROMs/GBA")
        val config = ConfigSavePath("/saves/mGBA/Game.srm", "/ROMs/GBA/Game.srm", live, true)
        val snapshot = ProbeSnapshot(100, 1, "com.retroarch.aarch64",
            listOf(RootReading(bound, true, emptyList(), null, true, null, null)), config)
        assertTrue(probes.check(player, snapshot, gba) is RootVerdict.UsesOther)
        assertTrue(store.pendingBackup("retroarch", gba))
        assertTrue(runCatching { probes.gate(player, gba, local) }.exceptionOrNull() is SaveProtectionStop)
        val other = gba.copy(remoteKey = "other")
        probes.gate(player, other, local)
        assertFalse(store.pendingBackup("retroarch", other))
        assertFalse(probes.location(player, snapshot) is LocationFact.Mismatch)
    }

    @Test fun anUnknownEdenConfigClearsTheCheckedLocationAcrossRestart() {
        val directory = Files.createTempDirectory("eden-check")
        val store = SaveProtectionStore(directory)
        val player = app.foldcade.plugins.eden.EdenPlayer()
        val pkg = player.packageNames.first()
        val root = FolderRef("$pkg.user:root", "Eden")
        val snapshot = ProbeSnapshot(100, 1, pkg, listOf(RootReading(root, true, emptyList(), null, true, true, null)))
        val probes = SaveProbes(android.content.ContextWrapper(null), saves, store, { error("Eden resolution supplied") }, SaveIncidentCopies(saves, plugins, store))
        store.verified(player.id, root, Evidence(EvidenceKind.Config, "Eden's save folder"), 50, pkg, 1, true)
        assertTrue(probes.location(player, snapshot) is LocationFact.Verified)

        assertEquals(RootVerdict.NoNews, probes.check(player, snapshot, eden = EdenSaveAccess.Unknown))
        assertEquals(LocationFact.Bound(root, 100), probes.location(player, snapshot))
        assertEquals(LocationFact.Bound(root, 100), SaveProtectionStore(directory).location(player.id, root, pkg, 1))
        assertTrue(store.incidents().isEmpty())

        val nand = EdenSaveDirectory("primary:nand", "content://$pkg.user/tree/root", "nand")
        val recovered = EdenSaveAccess.Ready(nand.uri, nand, nand, app.foldcade.plugins.eden.EdenProfile.Missing, configured = false)
        probes.check(player, snapshot.copy(atMillis = 200), eden = recovered)
        assertEquals(LocationFact.Verified(root, Evidence(EvidenceKind.OwnProvider, "Eden’s own folder"), 200), probes.location(player, snapshot))
    }

    @Test fun rootRecoveryClosesItsCauseAndUsesClosesBindingAndDoneButBypassesNeverVerify() {
        val store = SaveProtectionStore(Files.createTempDirectory("incident"))
        val copies = SaveIncidentCopies(saves, plugins, store)
        val probes = SaveProbes(android.content.ContextWrapper(null), saves, store, { SAVES }, copies)
        val incident = incident(IncidentKind.WrongFolder)
        val reading = RootReading(incident.bound!!, true, emptyList(), null, true, true, true)
        val snapshot = ProbeSnapshot(100, 1, "emulator", listOf(reading))
        for (kind in listOf(IncidentKind.LostAccess, IncidentKind.FolderEmptied)) {
            store.put(incident(kind))
            probes.check(Melon, snapshot)
            assertTrue(store.incidents().isEmpty())
        }
        for (repair in listOf(RepairState.Binding, RepairState.Done)) {
            store.put(incident.copy(repair = repair))
            probes.apply(Melon, snapshot, RootVerdict.Uses(reading.root, incident.evidence, 100))
            assertTrue(store.incidents().isEmpty())
        }
        store.put(incident.copy(repair = RepairState.Done))
        probes.apply(Melon, snapshot, RootVerdict.UsesOther(null, incident.evidence, 200))
        assertTrue(store.incidents().single().blocksLaunch)
        assertEquals(RepairState.NotStarted, store.incidents().single().repair)
        store.bound(Melon.id, reading.root, "emulator", 1, 1)
        store.put(incident.copy(recoveryRequired = true))
        probes.apply(Melon, snapshot, RootVerdict.Uses(reading.root, incident.evidence, 100))
        assertTrue(store.incidents().isNotEmpty())
        assertTrue(store.location(Melon.id, reading.root, "emulator", 1) is LocationFact.Bound)
    }

    private fun incident(kind: IncidentKind) = SaveIncident("$kind:melonds", kind, "melonds", "melonDS", emptyList(),
        FolderRef("primary:saves", "saves"), null, Evidence(EvidenceKind.SessionWrite, "saved elsewhere"), 1)

    @Test fun anUnmovedLaunchLogOpensAWrongFolderIncidentImmediatelyButAResumeDoesNot() {
        val store = SaveProtectionStore(Files.createTempDirectory("silent-start"))
        val copies = SaveIncidentCopies(saves, plugins, store)
        val probes = SaveProbes(android.content.ContextWrapper(null), saves, store, { SAVES }, copies)
        val root = FolderRef("primary:Azahar", "Azahar")
        val signal = SignalReading("log/azahar_log.txt", root.identity, 50, true, true)
        val reading = RootReading(root, true, listOf(signal), "unchanged", true, true, true)
        val before = ProbeSnapshot(100, 1, "azahar", listOf(reading))
        val after = before.copy(atMillis = 30_100)
        probes.noteSession(Melon, owner, before, after, startedByFoldcade = false)
        assertTrue(store.incidents().isEmpty())
        probes.noteSession(Melon, owner, before, after, sessionMillis = 5_000)
        assertTrue(store.incidents().isEmpty()) // Preparation time does not count as time in the game.
        probes.noteSession(Melon, owner, before, after)
        val incident = store.incidents().single()
        assertEquals(IncidentKind.WrongFolder, incident.kind)
        assertTrue(incident.blocksLaunch)
        assertEquals(null, incident.live)
        assertEquals("${Melon.displayName} didn’t use this folder last time you played.", incident.evidence.detail)
        val location = probes.location(Melon, after)
        val facts = SaveFacts(SaveKeeper.Emulator("Azahar"), location, SpotFact.Resolved, OffDeviceFact.OnRomm(100), null, store.incidents(), true)
        val status = deriveSaveStatus(facts)
        assertTrue(status is SaveStatus.Problem)
        val hero = HeroSubject.Item(HeroItem("game", "Game", "Nintendo 3DS"))
        assertFalse(heroFactText(hero, status).text.contains("Saved to RomM"))
    }

    @Test fun aNewlyDiscoveredLiveRootIsKeptAsABackupWithoutChangingTheHead() = runBlocking {
        val store = SaveProtectionStore(Files.createTempDirectory("discovered-copy"))
        val copies = SaveIncidentCopies(saves, plugins, store)
        val probes = SaveProbes(android.content.ContextWrapper(null), saves, store, { SAVES }, copies)
        val bound = FolderRef("primary:Azahar", "Azahar")
        val live = FolderRef("primary:AzaharQA", "AzaharQA")
        val root = RootReading(bound, true, listOf(SignalReading("log", bound.identity, 50, true, true)), "old", true, true, true)
        val before = ProbeSnapshot(100, 1, "azahar", listOf(root))
        val after = before.copy(atMillis = 30_100, roots = listOf(root, root.copy(root = live, bound = false,
            signals = listOf(SignalReading("log", live.identity, 20_100, true, true)), spotHash = "live progress")))
        probes.noteSession(Melon, owner, before, after)
        assertEquals(live, store.incidents().single().live)
        val uri = "content://live"
        docs.files["$uri/Tetris DS (USA).sav"] = "live progress".toByteArray()
        val spot = SaveSpot("sav", uri, "Tetris DS (USA).sav")
        copies.stage(owner, Melon, listOf(spot), store.incidents().single(), mark = false)
        copies.keep(owner, Melon)
        assertEquals(listOf(md5Hex("live progress".toByteArray())), server.backups)
        assertTrue(server.calls.isEmpty())
        assertTrue(server.observed.isEmpty())
    }

    @Test fun baselineIsTakenAfterFoldcadesOwnPlacement() = runBlocking {
        server.toPlace = "placed"
        var snapshotted = false
        LaunchSaves(saves, plugins, baseline = { _, _, _, _ ->
            assertEquals("placed", docs.files["$SAVES/Tetris DS (USA).sav"]?.toString(Charsets.UTF_8))
            snapshotted = true
            null
        }) { awaiting += it }.prepare(GameRoute("tile", play, owner), Melon, local)
        assertTrue(snapshotted)
    }

    @Test fun unresolvedPspAndGameCubeSavesNeverAcknowledgeAnIncomingHead() = runBlocking {
        val players = listOf(app.foldcade.plugins.ppsspp.PpssppPlayer(), app.foldcade.plugins.dolphin.DolphinPlayer(), app.foldcade.plugins.explus.ExPlusEntry().players.first())
        for (player in players) {
            (player as app.foldcade.api.plugin.SaveFolderHolder).bindSaveFolder("content://test/tree/saves")
            server.held = player.saveDeclarations(owner).map { it.slot }
            server.listingFails = true
            val failure = runCatching { prepareFiles(GameRoute("tile", owner, owner), player) }.exceptionOrNull()
            assertTrue(failure is UnresolvedSaveNotPlaced)
            val pending = unresolvedSaveHold(failure as SaveNotPlaced) { PendingBackendLaunch("Game", player.platformId, GameRoute("tile", owner, owner), player.id, Panel.Bottom) }
            assertNotNull(pending)
            assertFalse((failure as UnresolvedSaveNotPlaced).needsEdenUser)
            assertTrue(server.calls.isEmpty())
            assertTrue(firmwarePasses.isEmpty())
        }
    }

    @Test fun unresolvedPspAndGameCubePlayOfflineWithoutNegotiationAndRemainUnreachable() = runBlocking {
        val players = listOf(app.foldcade.plugins.ppsspp.PpssppPlayer(), app.foldcade.plugins.dolphin.DolphinPlayer(), app.foldcade.plugins.explus.ExPlusEntry().players.first())
        for (player in players) {
            (player as app.foldcade.api.plugin.SaveFolderHolder).bindSaveFolder("content://test/tree/saves")
            server.listingFails = true
            var reported = false
            val listingCount = server.listings
            val passes = firmwarePasses.size
            val prepared = prepareFiles(GameRoute("tile", owner, owner), player, onSpots = { _, picked, spots ->
                assertEquals(player.id, picked.id)
                assertTrue(spots.isEmpty())
                reported = true
            })
            assertTrue(reported)
            assertEquals(local, prepared.target)
            assertTrue(prepared.spots.isEmpty())
            assertEquals(listingCount + 1, server.listings)
            assertTrue(server.calls.isEmpty())
            assertTrue(docs.files.isEmpty())
            assertTrue(marks.isEmpty())
            assertTrue(awaiting.isEmpty())
            assertEquals(passes + 1, firmwarePasses.size)
            assertEquals(local to owner.backendId, firmwarePasses.last())
        }
    }

    @Test fun anUnavailableFirstListingDoesNotHideAnIncomingSaveFromAnotherCopy() = runBlocking {
        val second = owner.copy(backendId = "folder", remoteKey = "other")
        val players = listOf(app.foldcade.plugins.ppsspp.PpssppPlayer(), app.foldcade.plugins.dolphin.DolphinPlayer(), app.foldcade.plugins.explus.ExPlusEntry().players.first())
        for (player in players) {
            (player as app.foldcade.api.plugin.SaveFolderHolder).bindSaveFolder("content://test/tree/saves")
            server.listingFails = true
            folder.remoteSlot = player.saveDeclarations(second).single().slot
            val listingCount = server.listings
            val secondListingCount = folder.listings
            val failure = runCatching {
                prepareFiles(GameRoute("tile", owner, owner, servers = listOf(owner, second)), player)
            }.exceptionOrNull()
            assertTrue(failure is UnresolvedSaveNotPlaced)
            assertEquals(listingCount + 1, server.listings)
            assertEquals(secondListingCount + 1, folder.listings)
            assertTrue(server.calls.isEmpty())
            assertTrue(folder.calls.isEmpty())
            assertTrue(docs.files.isEmpty())
            assertTrue(firmwarePasses.isEmpty())
        }
    }

    @Test fun unresolvedPspAndGameCubeWithAnIncomingSaveRequireTheExplicitPerLaunchChoice() = runBlocking {
        val players = listOf(app.foldcade.plugins.ppsspp.PpssppPlayer(), app.foldcade.plugins.dolphin.DolphinPlayer(), app.foldcade.plugins.explus.ExPlusEntry().players.first())
        for (player in players) {
            (player as app.foldcade.api.plugin.SaveFolderHolder).bindSaveFolder("content://test/tree/saves")
            server.remoteSlot = player.saveDeclarations(owner).single().slot
            val route = GameRoute("tile", owner, owner)
            val passes = firmwarePasses.size
            val failure = runCatching { prepareFiles(route, player) }.exceptionOrNull()
            assertTrue(failure is UnresolvedSaveNotPlaced)
            assertEquals(passes, firmwarePasses.size)
            val pending = unresolvedSaveHold(failure as SaveNotPlaced) { PendingBackendLaunch("Game", player.platformId, route, player.id, Panel.Bottom) }
            val prompt = app.foldcade.language.saveNotPlacedDialog("Game", app.foldcade.language.HostScreen.Bottom, playAnyway = pending != null)
            assertTrue(prompt.buttons.contains(app.foldcade.language.DialogButton.PlayWithoutSyncing))
            assertEquals(app.foldcade.language.Copy.saveUnresolved, prompt.body)
            var declinedFor: String? = null
            answerUnresolvedSaveHold(app.foldcade.language.DialogButton.PlayWithoutSyncing,
                cancel = { error("explicit play does not cancel") }, openPlayer = { error("explicit play does not open settings") },
                playWithoutSyncing = { declinedFor = player.id })
            assertFalse(saveSyncForLaunch(player, declinedFor))
            val listingCount = server.listings
            val prepared = prepareFiles(route, player, syncing = saveSyncForLaunch(player, declinedFor))
            assertEquals(local, prepared.target)
            assertTrue(prepared.spots.isEmpty())
            assertEquals(listingCount, server.listings)
            assertTrue(server.calls.isEmpty())
            assertTrue(docs.files.isEmpty())
            assertTrue(marks.isEmpty())
            assertTrue(awaiting.isEmpty())
            assertEquals(passes + 1, firmwarePasses.size)
            assertEquals(local to owner.backendId, firmwarePasses.last())
            assertTrue(saveSyncForLaunch(player, null))
            assertTrue(saveSyncForLaunch(player, "another-player"))
        }
    }
    @Test fun aRenamedExRomOffersOnlyAPerLaunchOptOutAndLeavesEveryCopyAlone() = runBlocking {
        val player = app.foldcade.plugins.explus.ExPlusEntry().players.first()
        (player as app.foldcade.api.plugin.SaveFolderHolder).bindSaveFolder(SAVES)
        docs.names["content://rf4"] = "New.gba"
        docs.files["$SAVES/Old.sav"] = "original local battery".toByteArray()
        server.placementSlot = "battery"
        server.archiveToPlace = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.ZipOutputStream(out).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("Old.sav"))
                zip.write("original server battery".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        var reachable = true
        val route = GameRoute("tile", play, owner)
        suspend fun launch(syncing: Boolean = true) = prepareFiles(route, player, syncing,
            onSpots = { _, _, spots -> reachable = spots.isNotEmpty() })
        val before = docs.files.mapValues { it.value.copyOf() }
        repeat(2) {
            val passes = firmwarePasses.size
            val failure = runCatching { launch() }.exceptionOrNull()
            assertEquals(passes, firmwarePasses.size)
            assertTrue(failure is UnresolvedSaveNotPlaced)
            assertTrue((failure as UnresolvedSaveNotPlaced).incompatible)
            val dialog = app.foldcade.language.saveNotPlacedDialog("New", app.foldcade.language.HostScreen.Bottom,
                playAnyway = true, incompatible = failure.incompatible)
            assertEquals(app.foldcade.language.SavePickerCopy.incompatible, dialog.body)
            assertFalse(dialog.buttons.contains(app.foldcade.language.DialogButton.SetUpSaves))
            assertEquals(app.foldcade.language.DialogButton.NotNow, dialog.buttons[dialog.index])
            assertFalse(reachable)
            assertTrue(awaiting.isEmpty())
            assertTrue(marks.isEmpty())
            assertEquals(before.keys, docs.files.keys)
            assertTrue(docs.files["$SAVES/Old.sav"]!!.contentEquals(before.getValue("$SAVES/Old.sav")))
            assertEquals(local, launch(syncing = false).target)
            assertEquals(passes + 1, firmwarePasses.size)
        }
        // A compatible bundle whose write fails must still block, with no opt-out.
        docs.names["content://rf4"] = "Old.gba"
        docs.files.clear()
        docs.failWrites = true
        val passes = firmwarePasses.size
        val failure = runCatching { launch() }.exceptionOrNull()
        assertTrue(failure is SaveNotPlaced)
        assertFalse(failure is UnresolvedSaveNotPlaced)
        assertEquals(passes, firmwarePasses.size)
    }

    @Test fun aReachableExBatteryPlacesItsSaveThenPassesFirmwareExactlyOnce() = runBlocking {
        val player = app.foldcade.plugins.explus.ExPlusEntry().players.first()
        (player as app.foldcade.api.plugin.SaveFolderHolder).bindSaveFolder(SAVES)
        docs.names["content://rf4"] = "New.gba"
        server.placementSlot = "battery"
        val battery = "server battery".toByteArray()
        server.archiveToPlace = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.ZipOutputStream(out).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("New.sav")); zip.write(battery); zip.closeEntry()
            }
        }.toByteArray()
        val prepared = prepareFiles(GameRoute("tile", play, owner), player, firmware = {
            assertArrayEquals(battery, docs.files["$SAVES/New.sav"])
        })
        assertEquals(listOf("prepareSaves:12"), server.calls)
        assertEquals(1, prepared.spots.size)
        assertEquals(local, prepared.target)
        assertEquals(listOf(local to owner.backendId), firmwarePasses)
        assertEquals(0, docs.firmwarePublications) // EX players request no BIOS.
        assertEquals(listOf(player.saveFolderId()), awaiting)
    }

    // First link (design §7.1): a folder copy's save meets a RomM copy that already holds a different one.
    private val linkedPlay get() = play.copy(backendId = app.foldcade.localfolder.LocalFolderBackend.ID)
    private val folderSave = "$SAVES/Tetris DS (USA).sav"
    /** Repair can only finish a choice for a folder-holding emulator, so the first-link tests use one. */
    private val folderMelon = object : Player by Melon, app.foldcade.api.plugin.SaveFolderHolder {
        override fun bindSaveFolder(contentUri: String) = Unit
        override fun hasSaveFolder() = true
    }
    private fun version(text: String, slot: String? = "sav", id: String = "head-1") =
        app.foldcade.plugins.romm.RommRepairSave(id, slot, app.foldcade.romm.rommSaveHash(text.toByteArray())!!, 1, 1)
    private var origin = "https://romm.test"
    private fun firstLinkCopies(store: SaveProtectionStore, heads: () -> List<app.foldcade.plugins.romm.RommRepairSave>) =
        SaveIncidentCopies(saves, plugins, store, { origin }) { _, _ -> heads() }
    private fun firstLinkLaunch(copies: SaveIncidentCopies) = LaunchSaves(saves, plugins, firstLink = copies::holdFirstLink) { awaiting += it }
    private suspend fun launchStops(copies: SaveIncidentCopies, player: Player = folderMelon, route: GameRoute = GameRoute("tile", linkedPlay, owner)) =
        (runCatching { firstLinkLaunch(copies).prepare(route, player, local) }.exceptionOrNull() as? SaveProtectionStop)?.incident

    @Test fun aFirstLinkWithASaveRommNeverSawStopsBeforeEitherCopyIsPlacedOrHandedOver() = runBlocking {
        docs.files[folderSave] = "months in the folder".toByteArray()
        server.toPlace = "another device"
        val store = SaveProtectionStore(Files.createTempDirectory("first-link"))
        val copies = firstLinkCopies(store) { listOf(version("another device")) }
        val incident = launchStops(copies)!!
        assertTrue(incident.firstLink)
        assertEquals(IncidentKind.MaybeNewSave, incident.kind)
        assertEquals(RepairState.Keeping, incident.repair)
        assertEquals(regressionIncidentId(owner, folderMelon), incident.id)
        assertEquals("content://rf4", incident.subject?.targetUri)
        assertEquals(origin, incident.subject?.origin)
        // Nothing reached RomM's sync, nothing was placed, and the folder's progress is untouched and queued for a Backup.
        assertTrue(server.calls.isEmpty())
        assertTrue(marks.isEmpty())
        assertEquals("months in the folder", String(docs.files.getValue(folderSave)))
        assertEquals(1, saves.queuedCopies().size)
        assertEquals(incident.quarantineSourceUri, incident.staged.single())
        assertTrue(awaiting.isEmpty())
        // Every launch stops again until a copy is chosen, without staging a second copy.
        assertEquals(incident.id, launchStops(copies)?.id)
        assertTrue(server.calls.isEmpty())
        assertEquals(1, saves.queuedCopies().size)
    }

    @Test fun aSaveRommAlreadyKnowsIsAnOldSyncedSaveEvenAfterAReinstall() = runBlocking {
        // A reinstall wiped marks and RomM's cache here: no local history says this slot was ever synced.
        docs.files[folderSave] = "synced last spring".toByteArray()
        for (known in listOf(
            version("synced last spring", id = "older-3"),           // an older version in the same slot
            version("synced last spring", slot = null, id = "kept-9"), // an unslotted Backup
            version("synced last spring", slot = "autosave", id = "argosy-2"), // another client's slot
        )) {
            server.calls.clear()
            marks.clear()
            val store = SaveProtectionStore(Files.createTempDirectory("first-link-known"))
            assertNull(launchStops(firstLinkCopies(store) { listOf(version("newer from the Deck"), known) }))
            // The ordinary sync places RomM's newer head; its existing policy keeps the local bytes.
            assertEquals(listOf("reconcile:12", "prepareSaves:12"), server.calls)
            assertTrue(store.incidents().isEmpty())
        }
    }

    @Test fun aFirstLinkWithTheSameNoOrAnUnverifiableRommHeadSyncsAsBefore() = runBlocking {
        val blankHead = app.foldcade.plugins.romm.RommRepairSave("head-1", "sav", "", 1, 1)
        for (heads in listOf(listOf(version("months in the folder")), emptyList(), listOf(blankHead))) {
            server.calls.clear()
            marks.clear()
            docs.files[folderSave] = "months in the folder".toByteArray()
            val store = SaveProtectionStore(Files.createTempDirectory("first-link-same"))
            assertNull(launchStops(firstLinkCopies(store) { heads }))
            assertEquals(listOf("reconcile:12", "prepareSaves:12"), server.calls)
            assertTrue(store.incidents().isEmpty())
        }
    }

    @Test fun anUnreadableRommOrAnEarlierLinkLeavesTheOrdinarySyncInCharge() = runBlocking {
        docs.files[folderSave] = "months in the folder".toByteArray()
        val store = SaveProtectionStore(Files.createTempDirectory("first-link-offline"))
        assertNull(launchStops(firstLinkCopies(store) { throw java.io.IOException("offline") }))
        assertTrue(store.incidents().isEmpty())
        // Linked before: the library holds this slot, so RomM's sync decides as it always has.
        server.calls.clear()
        server.held = listOf("sav")
        docs.files[folderSave] = "newer here".toByteArray()
        assertNull(launchStops(firstLinkCopies(store) { listOf(version("another device")) }))
        assertTrue(store.incidents().isEmpty())
        assertEquals(listOf("reconcile:12", "prepareSaves:12"), server.calls)
    }

    @Test fun onlyAnExplicitChoiceClosesAFirstLinkStop() = runBlocking {
        docs.files[folderSave] = "months in the folder".toByteArray()
        val store = SaveProtectionStore(Files.createTempDirectory("first-link-chosen"))
        val copies = firstLinkCopies(store) { listOf(version("another device")) }
        val incident = launchStops(copies)!!
        // Local history changing is not a choice: the library holding the slot, or marks, leave the stop open.
        server.held = listOf("sav")
        marks["anything"] = "x"
        assertEquals(incident.id, launchStops(copies)?.id)
        // A save played from Y › Saves, or a choice in the two-saves card, is.
        copies.firstLinkChosen(owner, folderMelon)
        assertTrue(store.incidents().isEmpty())
    }

    @Test fun playingAnywayKeepsTheSessionAsABackupAndTheStopOpenThroughReturnAndRelaunch() = runBlocking {
        docs.files[folderSave] = "months in the folder".toByteArray()
        val store = SaveProtectionStore(Files.createTempDirectory("first-link-anyway"))
        val copies = firstLinkCopies(store) { listOf(version("another device")) }
        val first = launchStops(copies)!!
        // Play anyway: the session is Backup-only from the start.
        assertTrue(copies.beginUnprotected(owner, folderMelon))
        docs.files[folderSave] = "an evening more".toByteArray()
        val spots = saves.spots(folderMelon, owner, local)
        // The return keeps the session as a Backup. Its marks are bookkeeping, not a choice.
        assertTrue(copies.preserveReturn(owner, folderMelon, spots, preserveAsBackup = true))
        assertTrue(marks.isNotEmpty())
        val after = store.incident(first.id)!!
        assertTrue(after.firstLink)
        // This Thor in the choice is now the newer progress, kept and verified again before it is offered.
        assertEquals(app.foldcade.romm.rommSaveHash("an evening more".toByteArray()), after.quarantinedHash)
        assertEquals(RepairState.Keeping, after.repair)
        // Kept and verified at once here, so the choice's This Thor is that Backup.
        assertNotNull(after.quarantineBackupId)
        assertTrue(md5Hex("an evening more".toByteArray()) in server.backups)
        // Relaunch: still stopped, nothing placed over the device's save.
        server.calls.clear()
        assertEquals(first.id, launchStops(copies)?.id)
        assertTrue(server.calls.isEmpty())
        assertEquals("an evening more", String(docs.files.getValue(folderSave)))
    }

    @Test fun aStopRaisedForAnotherServerDecidesAfresh() = runBlocking {
        docs.files[folderSave] = "months in the folder".toByteArray()
        val store = SaveProtectionStore(Files.createTempDirectory("first-link-origin"))
        val copies = firstLinkCopies(store) { listOf(version("another device")) }
        launchStops(copies)!!
        origin = "https://other.romm.test"
        val again = launchStops(firstLinkCopies(store) { listOf(version("months in the folder")) })
        assertNull(again)
    }

    @Test fun onlyAFolderCopyMeetingRommWithAnEmulatorRepairCanFinishIsAFirstLink() = runBlocking {
        docs.files[folderSave] = "months in the folder".toByteArray()
        val store = SaveProtectionStore(Files.createTempDirectory("first-link-scope"))
        val copies = firstLinkCopies(store) { listOf(version("another device")) }
        // A RomM game played on its own keeps today's policy: RomM's sync keeps both and asks after.
        assertNull(launchStops(copies, route = GameRoute("tile", owner, owner)))
        // Eden and hosted cards can't finish a chosen placement yet (follow-up in §7.1); so can't a player without a folder.
        assertNull(launchStops(copies, player = Melon))
        val hosted = object : Player by Melon, app.foldcade.api.plugin.SaveFolderHolder, app.foldcade.api.plugin.HostedSaveFolder {
            override fun bindSaveFolder(contentUri: String) = Unit
            override fun hasSaveFolder() = true
            override val hostedFolderTitle = "cards"
            override val hostedFolderSteps = ""
            override fun bindHostedFolder(contentUri: String, inUse: Boolean) = Unit
            override fun hostedFolderInUse() = true
        }
        assertNull(launchStops(copies, player = hosted))
        assertTrue(store.incidents().isEmpty())
    }

    @Test fun firstLinkDiffersOnlyAgainstAVerifiableHeadAndNoKnownVersion() {
        val hash = app.foldcade.romm.rommSaveHash("mine".toByteArray())!!
        assertTrue(firstLinkDiffers(hash, "sav", listOf(version("theirs"))))
        assertFalse(firstLinkDiffers(hash, "sav", listOf(version("theirs"), version("mine", id = "old"))))
        assertFalse(firstLinkDiffers(hash.uppercase(), "sav", listOf(version("mine"))))
        assertFalse(firstLinkDiffers(hash, "sav", emptyList()))
        assertFalse(firstLinkDiffers(hash, "sav", listOf(version("theirs", slot = "other"))))
        assertFalse(firstLinkDiffers(hash, "sav", listOf(app.foldcade.plugins.romm.RommRepairSave("h", "sav", "", 1, 1))))
    }

    private val libraries = HashMap<String, RecordingLibrary>()
    private val folder = RecordingLibrary("folder")
    private val server = RecordingLibrary("server")
    private val docs = Documents()
    private val marks = HashMap<String, String>()
    private val handover = Files.createTempDirectory("launch-handover")
    private val saves = PlayerSaves(docs, Marks(marks), Files.createTempDirectory("launch-saves"), handover)
    private val awaiting = mutableListOf<String>()
    private val firmwarePasses = mutableListOf<Pair<LaunchTarget, String>>()
    private val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()).apply {
        register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val apiMinor = PLUGIN_API_MINOR
            override val libraries: List<LibraryBackend> = listOf(folder, server)
            override val players: List<Player> = listOf(Melon)
        })
    }
    private val play = Game("folder", "content://rf4", "nintendo-ds", Availability.LocalOnly, "Tetris")
    private val owner = Game("server", "12", "nintendo-ds", Availability.RemoteOnly, "Tetris")
    private val local = LaunchTarget.ContentUri("content://rf4")

    init {
        docs.names["content://rf4"] = "Tetris DS (USA).nds"
    }

    /** The same post-save gate used by both Home launch paths. */
    private suspend fun prepareFiles(
        route: GameRoute,
        player: Player,
        syncing: Boolean = true,
        onSpots: suspend (Game, Player, List<SaveSpot>) -> Unit = { _, _, _ -> },
        firmware: (LaunchTarget) -> Unit = {},
    ): PreparedLaunch {
        val work = LaunchWork()
        return work.prepareFiles(work.begin(), route.play,
            saves = { LaunchSaves(saves, plugins, onSpots = onSpots) { awaiting += it }.prepare(route, player, local, syncing) },
            firmware = { target, backendId ->
                firmwarePasses += target to backendId
                firmware(target)
                true
            },
        ) ?: error("launch unexpectedly blocked")
    }

    @Test
    fun playingWithoutSyncingStillAsksForFirmwareWithoutOfferingSaveSlots() = runBlocking {
        val firmware = object : Player by Melon {
            override fun firmwareFileName(name: String) = "bios/$name"
        }
        val prepared = LaunchSaves(saves, plugins) { awaiting += it }
            .prepare(GameRoute("tile", play, owner), firmware, local, syncing = false)
        assertEquals(listOf("prepareSaves:12"), server.calls)
        assertEquals(local, prepared.target)
        assertTrue(prepared.spots.isEmpty())
        assertTrue(awaiting.isEmpty())
        assertTrue(docs.files.isEmpty())
    }

    @Test
    fun aMissingDataGrantDoesNotDownloadAnUnusedArchiveBeforePlaying() = runBlocking {
        val firmware = object : Player by Melon, app.foldcade.api.plugin.SaveFolderHolder {
            override fun bindSaveFolder(contentUri: String) = Unit
            override fun hasSaveFolder() = false
            override fun firmwareFileName(name: String) = "bios/$name"
        }
        val prepared = LaunchSaves(saves, plugins) { awaiting += it }
            .prepare(GameRoute("tile", play, owner), firmware, local, syncing = false)
        assertTrue(server.calls.isEmpty())
        assertEquals(local, prepared.target)
        assertEquals(null, playerForSaveSpots(firmware, owner, emptyList()).firmwareFileName("bios.zip"))
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
    fun aCopyThatAlreadyHoldsTheSaveKeepsItOnADeviceWithNone() = runBlocking {
        val other = RecordingLibrary("other")
        plugins.register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val apiMinor = PLUGIN_API_MINOR
            override val libraries: List<LibraryBackend> = listOf(other)
        })
        val second = Game("other", "4", "nintendo-ds", Availability.Cached, "Tetris")
        other.held = listOf("sav")
        other.toPlace = "kept on the second copy"
        val route = GameRoute("tile", play, owner, servers = listOf(owner, second))
        val prepared = LaunchSaves(saves, plugins) { awaiting += it }.prepare(route, Melon, local)
        assertEquals(second, prepared.owner)
        assertEquals(listOf("prepareSaves:4"), other.calls)
        assertEquals(emptyList<String>(), server.calls)
    }

    @Test
    fun aSaveOnlyTheSecondCopyHasStillLoadsOnADeviceWithNone() = runBlocking {
        val other = RecordingLibrary("other")
        plugins.register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val apiMinor = PLUGIN_API_MINOR
            override val libraries: List<LibraryBackend> = listOf(other)
        })
        val second = Game("other", "4", "nintendo-ds", Availability.Cached, "Tetris")
        // Neither keeps a save here yet. Only the second copy's sync brings one.
        other.toPlace = "from the second copy"
        server.toPlace = null
        val prepared = LaunchSaves(saves, plugins) { awaiting += it }
            .prepare(GameRoute("tile", play, owner, servers = listOf(owner, second)), Melon, local)
        assertEquals(second, prepared.owner)
        assertEquals(listOf("prepareSaves:12"), server.calls)
        assertEquals(listOf("prepareSaves:4"), other.calls)
        assertEquals("from the second copy", String(docs.files.getValue("$SAVES/Tetris DS (USA).sav")))
    }

    @Test
    fun cardSetupStartsThePlayerFreshEvenWhenNoSaveIsPlaced() = runBlocking {
        val player = object : Player by Melon {
            override val id = "armsx2"
            override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("card1", SAVES))
            override fun saveFileName(slot: String, romFileName: String): String? = null
            override fun findSaveFolders(
                slot: String,
                game: GameFileReader,
                folder: SaveFolderAccess,
                forLaunch: Boolean,
            ): app.foldcade.api.plugin.SaveFolderSearch? {
                check(forLaunch)
                check(folder.write("memcards/card/note", byteArrayOf(7)))
                return null
            }
        }
        server.toPlace = null
        LaunchSaves(saves, plugins) { awaiting += it }.prepare(GameRoute("tile", owner, owner), player, local)
        assertEquals(listOf("armsx2"), awaiting)
        assertEquals(7, docs.files["$SAVES/memcards/card/note"]?.single()?.toInt())
    }

    @Test
    fun aSaveSearchThatThrowsStopsTheLaunch() = runBlocking {
        val player = object : Player by Melon {
            override fun findSaveFolders(
                slot: String,
                game: GameFileReader,
                folder: SaveFolderAccess,
                forLaunch: Boolean,
            ): app.foldcade.api.plugin.SaveFolderSearch? = error("card setup failed")
        }
        val failure = runCatching {
            LaunchSaves(saves, plugins) { awaiting += it }.prepare(GameRoute("tile", owner, owner), player, local)
        }.exceptionOrNull()
        assertTrue(failure is SaveNotPlaced)
        assertEquals(emptyList<String>(), server.calls)
        assertTrue(awaiting.isEmpty())
    }

    @Test
    fun aCopyThatKeepsItsOwnSavesLaunchesAsBefore() = runBlocking {
        LaunchSaves(saves, plugins) { awaiting += it }.prepare(GameRoute("tile", owner, owner), Melon, local)
        assertEquals(listOf("prepareLaunch:12"), server.calls)
    }

    @Test
    fun decliningDuckStationCardSetupStillPreparesAndPlacesItsBios() = runBlocking {
        val player = app.foldcade.plugins.duckstation.DuckStationPlayer().apply { bindSaveFolder(SAVES) }
        val game = owner.copy(platformId = player.platformId)
        assertTrue(saveLaunchOffer(player, onServer = true) is SaveLaunchOffer.Setup)
        val continuation = saveLaunchContinuation(player.id, SaveFolderAnswer.PlayWithoutSyncing, false) as SaveLaunchContinuation.Launch
        server.firmware = listOf(FirmwareBytes("SCPH1001.BIN", "content://firmware"))
        // No console dump is stored in tests. Substitute this generated image's
        // hash for the upstream database at the publication boundary only.
        val bytes = ByteArray(524288) { (it % 251).toByte() }
        docs.files["content://firmware"] = bytes
        val publisher = object : Player by player {
            override fun chooseFirmware(offered: List<app.foldcade.api.plugin.FirmwareOffer>) =
                offered.filter { md5Hex(it.header) == md5Hex(bytes) }
        }
        assertTrue(player.chooseFirmware(listOf(app.foldcade.api.plugin.FirmwareOffer("SCPH1001.BIN", bytes))).isEmpty())

        val prepared = prepareFiles(GameRoute("tile", game, game), player,
            syncing = saveSyncForLaunch(player, continuation.declinedFor),
            firmware = { target ->
                assertFalse(docs.files.containsKey("$SAVES/bios/SCPH1001.BIN")) // Saves only returned the offer.
                assertEquals(listOf("bios/SCPH1001.BIN"), saves.placeFirmware(publisher, game, target, SAVES))
            })

        assertEquals(listOf("prepareSaves:12"), server.calls)
        assertTrue(prepared.spots.isEmpty())
        assertEquals(emptyList<SaveDeclaration>(), server.declarationsSeen)
        assertArrayEquals(bytes, docs.files["$SAVES/bios/SCPH1001.BIN"])
        assertEquals(listOf(prepared.target to game.backendId), firmwarePasses)
        assertEquals(1, docs.firmwarePublications)
        assertTrue(docs.files.keys.none { it.endsWith(".partial") })
        assertTrue(awaiting.isEmpty())
    }

    @Test
    fun decliningArmsX2CardSetupStillPreparesItsLaunch() = runBlocking {
        val player = app.foldcade.plugins.armsx2.ArmsX2Player().apply { bindSaveFolder(SAVES) }
        val game = owner.copy(platformId = player.platformId)
        val continuation = saveLaunchContinuation(player.id, SaveFolderAnswer.PlayWithoutSyncing, false) as SaveLaunchContinuation.Launch

        val prepared = prepareFiles(GameRoute("tile", game, game), player,
            syncing = saveSyncForLaunch(player, continuation.declinedFor),
            firmware = { target -> assertTrue(saves.placeFirmware(player, game, target, SAVES).isEmpty()) })

        assertEquals(listOf("prepareSaves:12"), server.calls)
        assertEquals("content://rf4", (prepared.target as LaunchTarget.ContentUri).uri)
        assertTrue(prepared.spots.isEmpty())
        assertEquals(emptyList<SaveDeclaration>(), server.declarationsSeen)
        assertTrue(docs.files.isEmpty())
        assertTrue(awaiting.isEmpty())
        assertEquals(listOf(prepared.target to game.backendId), firmwarePasses)
        assertEquals(0, docs.firmwarePublications)
    }

    @Test
    fun decliningArmsX2CardSetupPlacesItsBiosOnceWithoutChangingTheCard() = runBlocking {
        val player = app.foldcade.plugins.armsx2.ArmsX2Player().apply { bindSaveFolder(SAVES) }
        val game = owner.copy(platformId = player.platformId)
        val continuation = saveLaunchContinuation(player.id, SaveFolderAnswer.PlayWithoutSyncing, false) as SaveLaunchContinuation.Launch
        // Generated ROM-directory structure, matching the plugin's parser tests.
        val bytes = ByteArray(144)
        fun entry(offset: Int, name: String, size: Int) {
            name.toByteArray().copyInto(bytes, offset)
            bytes[offset + 12] = size.toByte()
        }
        entry(64, "RESET", 64)
        entry(80, "ROMDIR", 64)
        entry(96, "ROMVER", 14)
        "0230AC20080220".toByteArray().copyInto(bytes, 128)
        server.firmware = listOf(FirmwareBytes("ps2.bin", "content://firmware"))
        docs.files["content://firmware"] = bytes
        val card = "$SAVES/memcards/Mcd001.ps2"
        val existing = "player's file card".toByteArray()
        docs.files[card] = existing

        val prepared = prepareFiles(GameRoute("tile", game, game), player,
            syncing = saveSyncForLaunch(player, continuation.declinedFor),
            firmware = { target ->
                assertFalse(docs.files.containsKey("$SAVES/bios/ps2.bin"))
                assertEquals(listOf("bios/ps2.bin"), saves.placeFirmware(player, game, target, SAVES))
            })

        assertEquals(listOf("prepareSaves:12"), server.calls)
        assertEquals(emptyList<SaveDeclaration>(), server.declarationsSeen)
        assertEquals(listOf(prepared.target to game.backendId), firmwarePasses)
        assertTrue(prepared.spots.isEmpty())
        assertArrayEquals(bytes, docs.files["$SAVES/bios/ps2.bin"])
        assertArrayEquals(existing, docs.files[card])
        assertEquals(1, docs.firmwarePublications)
        assertTrue(awaiting.isEmpty())
    }

    @Test
    fun aDeclaredFolderWithNoSpotForThisGameIsReportedUnreachableBeforeSync() = runBlocking {
        val player = object : Player by Melon {
            override val id = "armsx2"
            override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("card1", SAVES))
            override fun saveFileName(slot: String, romFileName: String): String? = null
        }
        var reported = false
        LaunchSaves(saves, plugins, onSpots = { _, _, spots ->
            assertTrue(spots.isEmpty())
            assertTrue(server.calls.isEmpty())
            reported = true
        }) { awaiting += it }.prepare(GameRoute("tile", owner, owner), player, local)
        assertTrue(reported)
        assertEquals(listOf(SaveDeclaration("card1", null)), server.declarationsSeen)
        assertTrue(awaiting.isEmpty())
        assertTrue(docs.files.isEmpty())
    }

    private inner class RecordingLibrary(override val id: String) : LibraryBackend {
        init {
            libraries[id] = this
        }

        override val displayName = id
        val calls = mutableListOf<String>()
        val observed = mutableListOf<String>()
        val backups = mutableListOf<String>()
        var backupsFail = false
        var existingVersionId: String? = null
        var toPlace: String? = null
        var archiveToPlace: ByteArray? = null
        var placementSlot = "sav"
        var held: List<String> = emptyList()
        var declarationsSeen: List<SaveDeclaration> = emptyList()
        var listingFails = false
        var listings = 0
        var remoteSlot: String? = null
        var firmware: List<FirmwareBytes> = emptyList()

        override suspend fun connect() = Unit
        override suspend fun disconnect() = Unit
        override suspend fun listPlatforms(): List<ListedPlatform> = emptyList()
        override suspend fun listGames(platformId: String, query: GameQuery) = GamePage(emptyList(), null)
        override suspend fun ensureLocal(game: Game): LaunchTarget = error("no download")
        override suspend fun saves(game: Game) = SaveSet(id, held.map { SaveSlot(it, "0".repeat(32), null) })

        override suspend fun serverSaves(game: Game, player: Player?): List<app.foldcade.api.plugin.ServerSave> {
            listings++
            if (listingFails) throw java.io.IOException("offline")
            return remoteSlot?.let { listOf(app.foldcade.api.plugin.ServerSave("1", it, player?.id, null, 0, 1, false, null)) }.orEmpty()
        }

        override suspend fun prepareLaunch(game: Game, player: Player): Placement {
            declarationsSeen = player.saveDeclarations(game)
            calls += "prepareLaunch:${game.remoteKey}"
            return Placement(LaunchTarget.ContentUri("content://own", firmware), placed())
        }

        override suspend fun prepareSaves(game: Game, player: Player, target: LaunchTarget): Placement {
            calls += "prepareSaves:${game.remoteKey}"
            declarationsSeen = player.saveDeclarations(game)
            val withFirmware = if (target is LaunchTarget.ContentUri) LaunchTarget.ContentUri(target.uri, firmware) else target
            return Placement(withFirmware, placed())
        }

        override suspend fun keepAsBackups(game: Game, player: Player, observed: ObservedSaves): List<String> {
            if (backupsFail) throw java.io.IOException("offline")
            return observed.slots.map { slot ->
                val bytes = Files.readAllBytes(java.nio.file.Paths.get(java.net.URI(slot.contentUri!!)))
                assertEquals(md5Hex(bytes), slot.contentHash)
                existingVersionId?.let { return@map it }
                backups += slot.contentHash
                "backup-${backups.size}"
            }
        }

        override suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves): SyncResult {
            calls += "reconcile:${game.remoteKey}"
            this.observed += observed.slots.map { it.contentHash }
            return SyncResult(SyncOutcome.Queued)
        }

        private fun placed(): List<PlacedSave> =
            (archiveToPlace ?: toPlace?.toByteArray())?.let { listOf(PlacedSave(placementSlot, "lib://$id", md5Hex(it))) }.orEmpty()
    }

    private inner class Documents : SaveDocuments {
        val names = HashMap<String, String>()
        val files = HashMap<String, ByteArray>()
        val folders = HashSet<String>()
        val denied = HashSet<String>()
        var firmwarePublications = 0
        var failWrites = false
        var unreadable = false
        override fun list(folderUri: String, name: String) = files.keys.filter { it.startsWith("$folderUri/") }.map {
            SaveEntry(it.removePrefix("$folderUri/"), it, false)
        }

        override fun displayName(uri: String): String? = names[uri] ?: uri.substringAfterLast('/').takeIf { uri in files }
        override fun find(folderUri: String, name: String): String? = "$folderUri/$name".takeIf { it in files }
        override fun create(folderUri: String, name: String): String = "$folderUri/$name".also { files[it] = ByteArray(0) }
        override fun read(uri: String): ByteArray? =
            if (unreadable || uri in denied) null else if (uri.startsWith("lib://")) libraries[uri.removePrefix("lib://")]?.let { it.archiveToPlace ?: it.toPlace?.toByteArray() } else files[uri]
        override fun head(uri: String, size: Int): ByteArray? = read(uri)?.take(size)?.toByteArray()
        override fun children(folderUri: String, path: String): List<SaveChild>? {
            if (unreadable) return null
            val prefix = folderUri + "/" + if (path.isEmpty()) "" else "$path/"
            return files.filterKeys { it.startsWith(prefix) && '/' !in it.removePrefix(prefix) }
                .map { (uri, bytes) -> SaveChild(uri.substringAfterLast('/'), uri, false, bytes.size.toLong()) } +
                folders.filter { it.startsWith(prefix) && '/' !in it.removePrefix(prefix) }
                    .map { uri -> SaveChild(uri.substringAfterLast('/'), uri, true, -1) }
        }
        override fun publishFirmware(folder: String, name: String, source: String, hash: String, header: ByteArray): Boolean =
            publishNewFirmware(this, folder, name, source, hash, header)
        override fun renameFirmware(uri: String, leaf: String): String {
            val target = uri.substringBeforeLast('/') + "/$leaf"
            check(target !in files)
            files[target] = files.remove(uri)!!
            firmwarePublications++
            return target
        }
        override fun delete(uri: String): Boolean = files.remove(uri) != null
        override fun write(uri: String, bytes: ByteArray) {
            if (failWrites) throw java.io.IOException("provider refused write")
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
