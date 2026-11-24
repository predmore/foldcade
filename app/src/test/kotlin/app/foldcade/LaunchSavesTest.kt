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
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** A folder copy that plays keeps its saves on the server copy of the same file. */
class LaunchSavesTest {
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
        probes.gate(player, owner) { publications++; assertEquals(null, it) }
        assertEquals(1, publications)
        store.put(incident(IncidentKind.WrongFolder))
        assertTrue(runCatching { probes.gate(player, owner) }.exceptionOrNull() is SaveProtectionStop)
        store.put(incident(IncidentKind.WrongFolder).copy(repair = RepairState.Done))
        probes.gate(player, owner)
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

    @Test fun baselineIsTakenAfterFoldcadesOwnPlacement() = runBlocking {
        server.toPlace = "placed"
        var snapshotted = false
        LaunchSaves(saves, plugins, baseline = { _, _, _ ->
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
            if (unreadable) null else if (uri.startsWith("lib://")) libraries[uri.removePrefix("lib://")]?.let { it.archiveToPlace ?: it.toPlace?.toByteArray() } else files[uri]
        override fun head(uri: String, size: Int): ByteArray? = read(uri)?.take(size)?.toByteArray()
        override fun children(folderUri: String, path: String): List<SaveChild>? {
            if (unreadable) return null
            val prefix = folderUri + "/" + if (path.isEmpty()) "" else "$path/"
            return files.filterKeys { it.startsWith(prefix) && '/' !in it.removePrefix(prefix) }
                .map { (uri, bytes) -> SaveChild(uri.substringAfterLast('/'), uri, false, bytes.size.toLong()) }
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
