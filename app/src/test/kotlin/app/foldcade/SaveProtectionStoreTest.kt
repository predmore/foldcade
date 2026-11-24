package app.foldcade

import app.foldcade.language.*
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class SaveProtectionStoreTest {
    private val root = FolderRef("primary:saves/Azahar", "saves/Azahar")
    private val live = FolderRef("primary:Azahar", "Azahar")
    private val evidence = Evidence(EvidenceKind.FreshLog, "Azahar’s log")

    @Test fun evidenceAndIncidentsRoundTripAndInstallChangeDropsTheCheck() {
        val dir = Files.createTempDirectory("save-protection")
        val store = SaveProtectionStore(dir)
        store.verified("azahar", root, evidence, 30, "azahar.app", 10, true)
        val incident = SaveIncident("WrongFolder:azahar", IncidentKind.WrongFolder, "azahar", "Azahar", listOf("game"), root, live, evidence, 40,
            listOf("server-1"), listOf("file:///staged"), RepairState.Binding, recoveryRequired = true)
        store.put(incident)
        val restored = SaveProtectionStore(dir)
        assertEquals(listOf(incident), restored.incidents())
        assertEquals(LocationFact.Verified(root, evidence, 30), restored.location("azahar", root, "azahar.app", 10))
        assertTrue(restored.hadMarkers("azahar", root))
        assertEquals(LocationFact.Bound(root), restored.location("azahar", root, "azahar.app", 11))
        assertEquals(LocationFact.Bound(root), restored.location("azahar", root, "lime.app", 10))
        assertEquals(LocationFact.Bound(live), restored.location("azahar", live, "azahar.app", 10))
        restored.close(incident.id)
        assertTrue(SaveProtectionStore(dir).incidents().isEmpty())
    }

    @Test fun aGuidedChoiceRemainsBoundAndOldLogsCannotTurnItIntoProof() {
        val store = SaveProtectionStore(Files.createTempDirectory("save-protection"))
        store.bound("azahar", root, "azahar.app", 10, 100)
        val bound = store.location("azahar", root, "azahar.app", 10)
        assertEquals(LocationFact.Bound(root, 100), bound)
        val reading = RootReading(root, true, listOf(SignalReading("log", root.identity, 90, true, false)), null, true, true, null)
        assertEquals(RootVerdict.NoNews, judgeRoots(ProbeSnapshot(200, 10, "azahar.app", listOf(reading)), bound))
        assertTrue(judgeRoots(ProbeSnapshot(200, 10, "azahar.app", listOf(reading.copy(signals = listOf(SignalReading("log", root.identity, 150, true, false))))), bound) is RootVerdict.Uses)
    }

    @Test fun reboundFencesAndPendingBypassesSurviveBindingVerificationAndIncidentClosure() {
        val dir = Files.createTempDirectory("save-handovers")
        val store = SaveProtectionStore(dir)
        val game = app.foldcade.api.plugin.Game("romm", "37", "nintendo-3ds", app.foldcade.api.plugin.Availability.Cached, "Game")
        val other = game.copy(remoteKey = "38")
        assertFalse(store.needsBackup("azahar", game))
        store.rebound("azahar", 100)
        store.handled("azahar", game)
        store.bound("azahar", live, "azahar.app", 1, 100)
        store.verified("azahar", live, evidence, 200, "azahar.app", 1, true)
        store.requireBackup("azahar", game)
        val restored = SaveProtectionStore(dir)
        assertTrue(restored.pendingBackup("azahar", game))
        assertTrue(restored.needsBackup("azahar", other))
        restored.handled("azahar", game)
        assertFalse(restored.pendingBackup("azahar", game))
        assertFalse(restored.needsBackup("azahar", game))
        assertTrue(restored.needsBackup("azahar", other))
        restored.rebound("azahar", 100) // a repeated timestamp is still a new binding generation
        assertTrue(SaveProtectionStore(dir).needsBackup("azahar", game))
    }

    @Test fun corruptFilesAreMovedAsideWithoutManufacturingProof() {
        val dir = Files.createTempDirectory("save-protection")
        Files.writeString(dir.resolve("locations.json"), "{not json")
        Files.writeString(dir.resolve("incidents.json"), "{\"a\":{\"kind\":\"future\"}}")
        val store = SaveProtectionStore(dir)
        assertEquals(LocationFact.Bound(root), store.location("azahar", root, "azahar.app", 10))
        assertTrue(store.incidents().isEmpty())
        assertEquals(2L, Files.list(dir).use { files -> files.filter { ".unreadable-" in it.fileName.toString() }.count() })
    }

    @Test fun sessionBaselineSurvivesJsonAndAllFilesAliasMatchesSaf() {
        val snapshot = ProbeSnapshot(100, 10, "azahar.app", listOf(RootReading(root, true,
            listOf(SignalReading("log/azahar_log.txt", root.identity, 90, true, false)), "hash", true, true, false, true, true)))
        assertEquals(snapshot, snapshotFromJson(snapshot.toJson()))
        val external = "content://com.android.externalstorage.documents/tree/primary%3Asaves%2FAzahar"
        val alias = "content://app.foldcade.storage/tree/primary%3A/document/primary%3Asaves%2FAzahar"
        assertEquals(root.identity, storageIdentity(external))
        assertEquals(storageIdentity(external), storageIdentity(alias, "app.foldcade"))
        assertEquals("primary:PSP", storageIdentity("content://com.android.externalstorage.documents/tree/PRIMARY%3APSP"))
        assertNotEquals(storageIdentity(alias, "other.package"), storageIdentity(external))
        assertNotEquals(storageIdentity("content://a.provider/tree/root"), storageIdentity("content://b.provider/tree/root"))
    }
}
