package app.foldcade

import app.foldcade.language.*
import app.foldcade.plugins.romm.RommRepairSave
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SaveRepairTest {
    @get:Rule val temp = TemporaryFolder()
    private val bound = FolderRef("primary:Azahar", "Azahar")
    private val live = FolderRef("primary:AzaharQA", "AzaharQA")
    private val incident = SaveIncident("WrongFolder:azahar", IncidentKind.WrongFolder, "azahar", "Azahar",
        emptyList(), bound, live, Evidence(EvidenceKind.FreshLog, "Used AzaharQA"), 1,
        subject = SaveIncidentGame("romm", "7", "3ds", "Picross", "azahar", "content://rom", "https://romm.test", "azahar.app", 1))

    private class Work(
        var copies: RepairCopies = RepairCopies("head", null, null),
        var online: Boolean = true,
        var holdsGame: Boolean = false,
    ) : SaveRepairWork {
        override val slot = "3ds"
        var head = RommRepairSave("head-id", slot, "head", 1, 20)
        var hasHead = true
        val backups = mutableMapOf<String, RommRepairSave>()
        val staged = linkedSetOf<String>()
        val events = mutableListOf<String>()
        override suspend fun running() = holdsGame
        override suspend fun versions(): List<RommRepairSave> {
            check(online)
            return listOfNotNull(head.takeIf { hasHead }) + backups.values
        }
        override suspend fun readCopies(keptHashes: Set<String>, stage: Boolean): RepairCopies {
            if (stage) staged += copies.hashes() - keptHashes
            return copies
        }
        override suspend fun uploadBackups(): Boolean {
            if (!online) return false
            events += "keep"
            staged.forEach { hash -> backups.putIfAbsent(hash, RommRepairSave("backup-$hash", null, hash, 2, 12)) }
            staged.clear()
            return true
        }
        override suspend fun bind() { events += "bind" }
        var refuseStrictSync = false
        var syncing = false
        override suspend fun place(selection: RepairState.Placing, copies: RepairCopies, backedUpLive: Boolean) {
            if (refuseStrictSync) throw app.foldcade.plugins.romm.RommRepairNeedsSync()
            if (syncing) throw app.foldcade.plugins.romm.RommRepairWaitingForSync()
            events += "place:${selection.saveId}"
            val chosen = versions().single { it.id == selection.saveId }
            this.copies = this.copies.copy(live = chosen.hash)
        }
        override suspend fun verify() { events += "verify" }
    }

    @Test fun resumesAfterInterruptionAtEachAutomaticWriteBoundary() = runBlocking {
        for (stage in listOf(RepairState.Keeping, RepairState.Rebinding, RepairState.Placing("head-id", "head", null), RepairState.Done)) {
            val dir = temp.newFolder().toPath()
            val initial = SaveProtectionStore(dir)
            val work = Work()
            initial.put(incident)
            val interrupted = SaveRepair(initial, { work }, settle = {}, changed = {
                if (initial.incident(incident.id)?.repair == stage) throw CancellationException("process stopped")
            })
            try {
                interrupted.advance(incident.id, start = true)
                fail("Expected interruption at $stage")
            } catch (_: CancellationException) { Unit }
            val restarted = SaveProtectionStore(dir)
            assertEquals(stage, restarted.incident(incident.id)?.repair)
            assertEquals(incident.subject, restarted.incident(incident.id)?.subject)
            val done = SaveRepair(restarted, { work }, settle = {}).advance(incident.id)
            assertEquals(RepairState.Done, done?.repair)
            assertNull(SaveProtectionStore(dir).incident(incident.id))
            assertEquals(listOf("place:head-id"), work.events.filter { it.startsWith("place") })
        }
    }

    @Test fun offlineKeepsDurableWorkAndReconnectRestoresTheHead() = runBlocking {
        val dir = temp.newFolder().toPath()
        val first = SaveProtectionStore(dir)
        val work = Work(online = false)
        first.put(incident)
        assertNull(SaveRepair(first, { work }, settle = {}).advance(incident.id, start = true))
        assertEquals(setOf("head"), work.staged)
        val second = SaveProtectionStore(dir)
        assertEquals(RepairState.WaitingForRomm(), second.incident(incident.id)?.repair)
        assertTrue(work.events.isEmpty())
        work.online = true
        assertNotNull(SaveRepair(second, { work }, settle = {}).advance(incident.id))
        assertEquals(listOf("keep", "bind", "place:head-id", "verify"), work.events)
    }

    @Test fun waitsForAClosedGameAndMatchingReads() = runBlocking {
        val dir = temp.newFolder().toPath()
        val store = SaveProtectionStore(dir)
        val work = Work(holdsGame = true)
        store.put(incident)
        SaveRepair(store, { work }, settle = {}).advance(incident.id, start = true)
        assertEquals(RepairState.WaitingForClose(), store.incident(incident.id)?.repair)
        assertTrue(work.events.isEmpty())
        assertTrue(work.staged.isEmpty())
        work.holdsGame = false
        SaveRepair(store, { work }, settle = { work.copies = work.copies.copy(live = "new write") }).advance(incident.id)
        assertEquals(RepairState.WaitingForClose(backupsKept = true), SaveProtectionStore(dir).incident(incident.id)?.repair)
        assertEquals(listOf("keep"), work.events)
    }

    @Test fun equalCopiesUseOneUnslottedBackupEvenWhenTheyMatchTheHead() = runBlocking {
        val store = SaveProtectionStore(temp.newFolder().toPath())
        val work = Work(RepairCopies("head", "head", "head"))
        store.put(incident)
        assertNotNull(SaveRepair(store, { work }, settle = {}).advance(incident.id, start = true))
        assertEquals(listOf("backup-head"), work.backups.values.map { it.id })
    }

    @Test fun guidedBesideRepairRestoresOnlyTheExistingHeadAfterVerification() = runBlocking {
        val store = SaveProtectionStore(temp.newFolder().toPath())
        val detected = incident.copy(besideGame = true, repair = RepairState.Binding)
        val work = Work(RepairCopies("older-bound", "older-bound", "beside-progress"), online = false)
        store.put(detected)
        val repair = SaveRepair(store, { work }, settle = {})
        assertNull(repair.advance(detected.id, start = true))
        assertTrue(work.events.isEmpty())

        store.put(detected.copy(repair = RepairState.Keeping))
        assertNull(repair.advance(detected.id))
        assertTrue(store.incident(detected.id)?.repair is RepairState.WaitingForRomm)
        assertEquals(setOf("older-bound", "beside-progress"), work.staged)

        work.online = true
        val done = repair.advance(detected.id)!!
        assertTrue(done.besideGame && done.automaticDone)
        assertEquals(listOf("keep", "place:head-id", "verify"), work.events)
        assertEquals(setOf("older-bound", "beside-progress"), work.backups.keys)
        assertTrue(work.backups.values.all { it.slot == null })
        assertEquals("head-id", work.head.id)
    }

    @Test fun guidedBesideRepairWithNoHeadKeepsABackupWithoutCreatingASlot() = runBlocking {
        val store = SaveProtectionStore(temp.newFolder().toPath())
        val confirmed = incident.copy(besideGame = true, repair = RepairState.Keeping)
        val work = Work(RepairCopies(null, null, "first-save")).apply { hasHead = false }
        store.put(confirmed)
        val done = SaveRepair(store, { work }, settle = {}).advance(confirmed.id)!!
        assertEquals(RepairState.Done, done.repair)
        assertFalse(done.automaticDone)
        assertEquals(listOf("keep"), work.events)
        assertEquals(listOf("backup-first-save"), done.kept)
    }

    @Test fun confirmBesideSaveFolderRejectsTheRomFolderAndAnOldPickAfterSettingsChanges() = runBlocking {
        val dir = temp.newFolder().toPath()
        val store = SaveProtectionStore(dir)
        val expected = incident.copy(besideGame = true, repair = RepairState.Binding, staged = listOf("file:///prior.save"),
            subject = incident.subject?.copy(romFileName = "Game.nds"))
        store.put(expected)
        var remembers = 0
        suspend fun confirm(picked: FolderRef, watched: FolderRef) = confirmBesideSaveFolder(store, expected.id, picked.identity, watched.identity,
            hasRomm = { true }, preserveLocal = { _, _ -> error("RomM repair stages later") }, remember = { remembers++; true })
        assertFalse(confirm(live, bound))
        assertFalse(confirm(bound, live))
        assertEquals(0, remembers)

        val newFolder = FolderRef("primary:saves/new", "saves/new")
        store.retargetBesideRepairs(expected.playerKey, newFolder)
        val retargeted = SaveProtectionStore(dir).incident(expected.id)!!
        assertEquals(newFolder, retargeted.bound)
        assertEquals(RepairState.Guided, retargeted.repair)
        assertEquals(expected.staged, retargeted.staged)
        assertEquals("Game.nds", retargeted.subject?.romFileName)
        store.put(retargeted.copy(repair = RepairState.Binding))
        assertFalse(confirm(bound, newFolder))
        assertTrue(confirm(newFolder, newFolder))
        assertEquals(1, remembers)
        assertEquals(RepairState.Keeping, store.incident(expected.id)?.repair)
    }

    @Test fun confirmBesideSaveFolderKeepsTheLocalCopyBeforeFinishing() = runBlocking {
        val store = SaveProtectionStore(temp.newFolder().toPath())
        val pending = incident.copy(besideGame = true, repair = RepairState.Binding)
        store.put(pending)
        val events = mutableListOf<String>()
        val restore = confirmBesideSaveFolder(store, pending.id, bound.identity, bound.identity, hasRomm = { false },
            preserveLocal = { current, _ ->
                events += "preserve"
                store.put(current.copy(staged = listOf("file:///progress.save")))
                BesideSaveSource.Progress
            },
            remember = { events += "choose"; true })
        assertFalse(restore)
        assertEquals(listOf("preserve", "choose"), events)
        val done = store.incident(pending.id)!!
        assertEquals(RepairState.Done, done.repair)
        assertEquals(listOf("file:///progress.save"), done.staged)
        assertEquals(BesideSaveSource.Progress, done.besideSource)
    }

    @Test fun repairCanLoseTheRomLocationAfterItsBesideCopyHasAlreadyBeenCaptured() {
        val initial = incident.copy(besideGame = true)
        val spot = SaveSpot("sav", primaryFolderDocument("ROMs"), "Game.sav")
        assertThrows(RepairFoldersUnavailable::class.java) { besideBytesForRepair(null, initial) }
        val captured = initial.copy(capturedAlternateHashes = listOf("prior-progress"))
        assertNull(besideBytesForRepair(null, captured))
        assertNull(besideBytesForRepair(null, initial.copy(kept = listOf("123"))))
        assertNull(besideBytesForRepair(null, initial.copy(besideSource = BesideSaveSource.Blank)))
        assertNull(besideBytesForRepair(BesideGameCandidate(live, spot, FileReading.Missing), captured))
        assertThrows(RepairFoldersUnavailable::class.java) {
            besideBytesForRepair(BesideGameCandidate(live, spot, FileReading.Unreadable), captured)
        }
    }

    @Test fun aPreviouslySyncedLiveCopyIsKeptWhileTheNewerHeadIsRestored() = runBlocking {
        val store = SaveProtectionStore(temp.newFolder().toPath())
        val work = Work(RepairCopies(null, "old-head", null, liveMatchesSynced = true))
        store.put(incident)
        assertNotNull(SaveRepair(store, { work }, settle = {}).advance(incident.id, start = true))
        assertTrue("old-head" in work.backups)
        assertEquals("head", work.copies.live)
    }

    @Test fun twoHistoriesWaitAcrossRestartAndOnlyPlaceAnExplicitChoice() = runBlocking {
        val dir = temp.newFolder().toPath()
        val store = SaveProtectionStore(dir)
        val work = Work(RepairCopies("old", "live", "third"))
        store.put(incident)
        SaveRepair(store, { work }, settle = {}).advance(incident.id, start = true)
        val restored = SaveProtectionStore(dir)
        val choice = restored.incident(incident.id)?.repair as RepairState.Choosing
        assertEquals(RepairState.Choosing("head-id", "backup-live", "head", suspect = false), choice)
        assertEquals(setOf("old", "live", "third"), work.backups.keys)
        val resumed = SaveRepair(restored, { work }, settle = {})
        assertNull(resumed.advance(incident.id))
        assertNull(resumed.advance(incident.id, choice = choice to SaveConflictAction.DecideLater))
        assertFalse(work.events.any { it.startsWith("place") })
        assertNotNull(resumed.advance(incident.id, choice = choice to SaveConflictAction.KeepLocal))
        assertEquals("live", work.copies.live)
    }

    @Test fun aChangedHeadInvalidatesAnOpenChoice() = runBlocking {
        val store = SaveProtectionStore(temp.newFolder().toPath())
        val work = Work(RepairCopies(null, "live", null))
        store.put(incident)
        val repair = SaveRepair(store, { work }, settle = {})
        repair.advance(incident.id, start = true)
        val old = store.incident(incident.id)?.repair as RepairState.Choosing
        work.head = work.head.copy(id = "new-head", hash = "new")
        assertNull(repair.advance(incident.id, choice = old to SaveConflictAction.KeepRemote))
        assertEquals("new-head", (store.incident(incident.id)?.repair as RepairState.Choosing).headId)
        assertFalse(work.events.any { it.startsWith("place") })
    }

    @Test fun leaveAsIsCannotPlaceTheHeadDuringAWrongFolderRepair() = runBlocking {
        val store = SaveProtectionStore(temp.newFolder().toPath())
        val work = Work(RepairCopies(null, "live", null))
        store.put(incident)
        val repair = SaveRepair(store, { work }, settle = {})
        repair.advance(incident.id, start = true)
        val choice = store.incident(incident.id)?.repair as RepairState.Choosing
        assertNull(repair.advance(incident.id, choice = choice to SaveConflictAction.KeepBoth))
        assertEquals(choice, store.incident(incident.id)?.repair)
        assertFalse(work.events.any { it.startsWith("place") })
    }

    @Test fun aStrictSyncConflictGoesToGuidedRecovery() = runBlocking {
        val store = SaveProtectionStore(temp.newFolder().toPath())
        val work = Work().apply { refuseStrictSync = true }
        store.put(incident)
        assertNull(SaveRepair(store, { work }, settle = {}).advance(incident.id, start = true))
        assertEquals(RepairState.Guided, store.incident(incident.id)?.repair)
    }

    @Test fun aQuarantineWithoutRepairWorkOffersGuidedRecovery() = runBlocking {
        val store = SaveProtectionStore(temp.newFolder().toPath())
        val quarantine = incident.copy(kind = IncidentKind.MaybeNewSave, repair = RepairState.WaitingForClose())
        store.put(quarantine)
        assertNull(SaveRepair(store, { null }, settle = {}).advance(quarantine.id))
        val current = store.incident(quarantine.id)!!
        assertEquals(RepairState.Guided, current.repair)
        assertEquals(listOf(SaveFixAction.OpenSaves), saveFixActions(current, SaveFixPage(current.id, HostScreen.Bottom, null, hasRommDestination = true)))
    }

    @Test fun aSlotStillSyncingKeepsPlacementPendingUntilRommIsReady() = runBlocking {
        val store = SaveProtectionStore(temp.newFolder().toPath())
        val work = Work().apply { syncing = true }
        store.put(incident)
        val repair = SaveRepair(store, { work }, settle = {})
        assertNull(repair.advance(incident.id, start = true))
        assertEquals(RepairState.WaitingForRomm(RepairState.Placing("head-id", "head", null)), store.incident(incident.id)?.repair)
        work.syncing = false
        assertNotNull(repair.advance(incident.id))
        assertEquals(listOf("place:head-id"), work.events.filter { it.startsWith("place:") })
    }

    @Test fun aSuspectCopyWithNoShapeOrHashIsFoundByItsVerifiedBackupId() = runBlocking {
        val store = SaveProtectionStore(temp.newFolder().toPath())
        val work = Work(RepairCopies("reset", "reset", null))
        work.backups["reset"] = RommRepairSave("reset-id", null, "reset", 2, 5)
        val quarantine = incident.copy(kind = IncidentKind.MaybeNewSave, repair = RepairState.Keeping, quarantineBackupId = "reset-id")
        store.put(quarantine)
        SaveRepair(store, { work }, settle = {}).advance(quarantine.id)
        assertEquals(RepairState.Choosing("head-id", "reset-id", "head", suspect = true), store.incident(quarantine.id)?.repair)
    }

    @Test fun aResetIsOfferedWithoutGatingAndLeavingItAloneKeepsTheHead() = runBlocking {
        val store = SaveProtectionStore(temp.newFolder().toPath())
        val work = Work(RepairCopies("reset", "reset", null))
        work.backups["reset"] = RommRepairSave("reset-id", null, "reset", 2, 5)
        val quarantine = incident.copy(kind = IncidentKind.MaybeNewSave, repair = RepairState.Keeping, quarantinedHash = "reset")
        store.put(quarantine)
        val repair = SaveRepair(store, { work }, settle = {})
        repair.advance(quarantine.id)
        val choosing = store.incident(quarantine.id)!!
        assertFalse(choosing.blocksLaunch)
        val choice = choosing.repair as RepairState.Choosing
        assertTrue(choice.suspect)
        assertNotNull(repair.advance(quarantine.id, choice = choice to SaveConflictAction.KeepBoth))
        assertEquals("head-id", work.head.id)
        assertTrue(work.events.none { it.startsWith("place") })
        assertTrue("reset" in work.backups)
    }

    @Test fun aNormalLaunchCanRestoreTheHeadBeforeTheQuarantineChoice() = runBlocking {
        val store = SaveProtectionStore(temp.newFolder().toPath())
        val work = Work(RepairCopies("head", "head", null))
        work.backups["reset"] = RommRepairSave("reset-id", null, "reset", 2, 5)
        val quarantine = incident.copy(kind = IncidentKind.MaybeNewSave, repair = RepairState.Keeping, quarantinedHash = "reset")
        store.put(quarantine)
        SaveRepair(store, { work }, settle = {}).advance(quarantine.id)
        assertEquals(RepairState.Choosing("head-id", "reset-id", "head", suspect = true), store.incident(quarantine.id)?.repair)
    }

    @Test fun aGuidedPickMustMatchTheProvenStorageIdentity() {
        val picked = "content://com.android.externalstorage.documents/tree/primary%3AAzaharQA"
        assertTrue(repairFolderMatches(live.identity, storageIdentity(picked)))
        assertFalse(repairFolderMatches(live.identity, bound.identity))
        assertTrue(repairFolderMatches(null, bound.identity))
    }

    @Test fun theFolderShapeCountsFilesInsteadOfDirectoryEntriesOrCompressedBytes() {
        val archive = saveZip(mapOf("data/" to null, "data/one" to ByteArray(2048), "data/two" to ByteArray(512)))
        assertEquals(SaveShape(2560, 2, folder = true), saveShape(archive, folder = true))
        assertNull(saveShape(archive.copyOf(archive.size - 22), folder = true))
        assertEquals(SaveShape(archive.size.toLong(), 1), saveShape(archive, folder = false))
    }

    @Test fun freshRootRequiresNewMarkersInTheSameNonHostedFolder() {
        fun snap(root: FolderRef, markers: Boolean) = ProbeSnapshot(1, 1, "azahar.app",
            listOf(RootReading(root, true, emptyList(), null, true, markers, null)))
        fun context(before: ProbeSnapshot?, after: ProbeSnapshot?, hosted: Boolean = false) =
            returnRegressionContext(before, after, hosted, null, null, reinstalled = false, openIncident = false)
        val before = snap(bound, false)
        assertTrue(context(before, before, hosted = true).isEmpty())
        assertTrue(context(before, snap(bound, true), hosted = true).isEmpty())
        assertTrue(context(before, before).isEmpty())
        assertTrue(context(before, null).isEmpty())
        assertTrue(context(before, snap(live, true)).isEmpty())
        assertEquals(setOf(RegressionContext.FreshRoot), context(before, snap(bound, true)))
        assertEquals(setOf(RegressionContext.NewProfile, RegressionContext.OpenIncident),
            returnRegressionContext(before, before, true, "old", "new", reinstalled = true, openIncident = true))
        assertEquals(setOf(RegressionContext.Reinstalled),
            returnRegressionContext(before, before, false, null, null, reinstalled = true, openIncident = false))
    }

    @Test fun hostedCardIncidentWithSubjectStillAppliesOnlyToItsGame() {
        val subject = incident.subject!!.copy(platformId = "playstation", playerId = "duckstation")
        val game = subject.game()
        val mismatch = incident.copy(kind = IncidentKind.MaybeNewSave, playerKey = subject.playerId, subject = subject,
            gameIds = listOf(homeGameId(game.backendId, game.remoteKey)), evidence = Evidence(EvidenceKind.HostedOpen, "SLUS-00067_1.mcd"))
        assertTrue(mismatch.appliesTo(game))
        assertFalse(mismatch.appliesTo(game.copy(remoteKey = "another game")))
        assertFalse(mismatch.appliesTo(game.copy(backendId = "local-folder")))
    }
}
