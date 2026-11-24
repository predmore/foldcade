package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class SaveEvidenceTest {
    private val bound = FolderRef("primary:saves/Azahar", "saves/Azahar")
    private val live = FolderRef("primary:Azahar", "Azahar")
    private val last = LocationFact.Verified(bound, Evidence(EvidenceKind.SessionWrite, "Game saved"), 100)
    private fun signal(root: FolderRef, at: Long?, proves: Boolean = true, perStart: Boolean = false) = SignalReading("log/azahar_log.txt", root.identity, at, proves, perStart)
    private fun root(ref: FolderRef = bound, hash: String? = "old", signals: List<SignalReading> = emptyList()) = RootReading(ref, ref == bound, signals, hash, true, true, null)
    private fun snap(vararg roots: RootReading, at: Long = 500) = ProbeSnapshot(at, 1, "azahar", roots.toList())

    @Test fun unreachableFolderAndRevokedEmulatorGrant() {
        assertEquals(RootVerdict.NoNews, judgeRoots(snap(root().copy(spotReadable = false, markersPresent = null)), last))
        val lost = judgeRoots(snap(root().copy(reachable = false)), last) as RootVerdict.Lost
        assertEquals(IncidentKind.LostAccess, lost.kind)
        val grants = snap(root().copy(emulatorHolds = false), root(live).copy(emulatorHolds = true))
        assertEquals(RootVerdict.NoNews, judgeRoots(grants, last))
        assertEquals(live, (judgeRoots(grants, last, negativeEvidence = true) as RootVerdict.UsesOther).live)
        assertEquals(IncidentKind.LostAccess, (judgeRoots(snap(root().copy(emulatorHolds = false)), last, true) as RootVerdict.Lost).kind)
    }

    @Test fun staleOldRootLogDoesNotBeatTheNewLiveLog() {
        val snapshot = snap(root(signals = listOf(signal(bound, 110))), root(live, signals = listOf(signal(live, 300))))
        assertEquals(live, (judgeRoots(snapshot, last) as RootVerdict.UsesOther).live)
        val same = snap(root(signals = listOf(signal(bound, 300))), root(live, signals = listOf(signal(live, 110))))
        assertEquals(bound, (judgeRoots(same, last) as RootVerdict.Uses).root)
        assertEquals(RootVerdict.NoNews, judgeRoots(snap(root(signals = listOf(signal(bound, 99)))), last))
    }

    @Test fun unknownTiedRestoredAndFutureMtimesStayUnknown() {
        for (snapshot in listOf(
            snap(root(signals = listOf(signal(bound, null)))),
            snap(root(signals = listOf(signal(bound, 300))), root(live, signals = listOf(signal(live, 300)))),
            snap(root(signals = listOf(signal(bound, 100)))),
            snap(root(signals = listOf(signal(live, 501)))),
            snap(root(signals = listOf(signal(live, 300, proves = false)))),
        )) assertEquals(RootVerdict.NoNews, judgeRoots(snapshot, last))
    }

    @Test fun emptyMarkersNeedBothPriorMarkersAndAKnownSave() {
        val empty = root().copy(markersPresent = false, hadMarkers = true, hasKnownSave = true)
        assertEquals(IncidentKind.FolderEmptied, (judgeRoots(snap(empty), last) as RootVerdict.Lost).kind)
        assertEquals(RootVerdict.NoNews, judgeRoots(snap(empty.copy(hasKnownSave = false)), last))
        assertEquals(RootVerdict.NoNews, judgeRoots(snap(empty.copy(hadMarkers = false)), last))
        assertEquals(RootVerdict.NoNews, judgeRoots(snap(empty.copy(markersPresent = null)), last))
    }

    @Test fun sessionWriteAndCandidateDiffUseBytesAfterPlacement() {
        val before = snap(root(hash = "placed"), root(live, hash = null), at = 100)
        assertEquals(SessionVerdict.Wrote(bound), judgeSession(before, snap(root(hash = "played"), root(live, hash = "new")), 400))
        assertEquals(SessionVerdict.WroteElsewhere(live), judgeSession(before, snap(root(hash = "placed"), root(live, hash = "new")), 400))
        assertEquals(SessionVerdict.NoNews, judgeSession(before, snap(root(hash = "placed"), root(live, hash = null)), 5_000))
        assertEquals(SessionVerdict.NoNews, judgeSession(before, snap(root(hash = "placed").copy(spotReadable = false), root(live, hash = "new")), 400))
        assertEquals(SessionVerdict.NoNews, judgeSession(before, snap(root(hash = null), root(live, hash = null)), 400))
    }

    @Test fun freshSignalsAfterBaselineAndDisabledNegativeRule() {
        val before = snap(root(signals = listOf(signal(bound, 50, perStart = true))), at = 100)
        val after = snap(root(signals = listOf(signal(live, 300))))
        assertTrue(judgeSession(before, after, 400) is SessionVerdict.Signalled)
        val unchanged = snap(root(signals = listOf(signal(bound, 50, perStart = true))))
        assertEquals(SessionVerdict.NoNews, judgeSession(before, unchanged, 59_999, true))
        assertEquals(SessionVerdict.SilentStart("log/azahar_log.txt"), judgeSession(before, unchanged, 60_000, true))
        assertEquals(SessionVerdict.NoNews, judgeSession(before, unchanged, 60_000))
        assertFalse(SAVE_NEGATIVE_EVIDENCE_ENABLED)
        assertEquals(SessionVerdict.NoNews, judgeSession(before, after.copy(installFirstMillis = 2), 400))
    }

    @Test fun anotherInstalledFlavorNeedsAnExactVersionMatch() {
        assertFalse(provingSignal(true, false, 2, null, "2126.2"))
        assertFalse(provingSignal(true, false, 2, "2126.1", "2126.2"))
        assertTrue(provingSignal(true, false, 2, "2126.2", "2126.2"))
        assertTrue(provingSignal(true, true, 2, null, "2126.2"))
        assertTrue(provingSignal(true, false, 1, null, "2126.2"))
    }
}
