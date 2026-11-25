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

    @Test fun multipleChangedCandidatesStillProveAMismatchWithNoSingleLiveRoot() {
        val other = FolderRef("primary:Azahar2", "Azahar2")
        val before = snap(root(), root(live), root(other), at = 100)
        val after = snap(root(), root(live, hash = "new"), root(other, hash = "newer"))
        assertEquals(SessionVerdict.WroteElsewhere(null), judgeSession(before, after, 400))
    }

    @Test fun besideWriteNeedsAReadableUnchangedBoundAndTheSameReadableCandidate() {
        val beside = root(FolderRef("primary:ROMs", "ROMs"), hash = null).copy(spotFile = "Game.sav", besideGame = true)
        val saved = beside.copy(spotHash = "new bytes")
        val before = snap(root(), beside, at = 100)
        val after = snap(root(), saved)
        assertEquals(SessionVerdict.WroteElsewhere(beside.root, besideGame = true), judgeSession(before, after, 400))
        assertEquals(SessionVerdict.Wrote(bound), judgeSession(before, snap(root(hash = "bound write"), saved), 400))
        assertEquals(SessionVerdict.NoNews, judgeSession(before, snap(root(), beside), 400))
        assertEquals(SessionVerdict.NoNews, judgeSession(before, snap(root(), saved.copy(spotReadable = false)), 400))
        assertEquals(SessionVerdict.NoNews, judgeSession(before, snap(root(), saved.copy(spotFile = "Other.sav")), 400))
        assertEquals(SessionVerdict.NoNews, judgeSession(snap(root(), beside.copy(spotReadable = false)), after, 400))
        assertEquals(SessionVerdict.NoNews, judgeSession(snap(root().copy(spotReadable = false), beside), after, 400))
        assertEquals(SessionVerdict.NoNews, judgeSession(before, after.copy(installFirstMillis = 2), 400))
        assertEquals(SessionVerdict.WroteElsewhere(null, besideGame = true),
            judgeSession(snap(root(), beside, root(live)), snap(root(), saved, root(live, hash = "other write")), 400))
    }

    @Test fun configMismatchComparesFilesAndRequiresPositivePrecedenceEvidence() {
        val config = ConfigSavePath("/saves/mGBA/Game.srm", "/saves/Game.srm", bound, false)
        val snapshot = snap(root()).copy(configSavePath = config)
        assertEquals(RootVerdict.NoNews, judgeRoots(snapshot, last))
        val verdict = judgeRoots(snapshot.copy(configSavePath = config.copy(proves = true)), last) as RootVerdict.UsesOther
        assertEquals(EvidenceKind.Config, verdict.evidence.kind)
        assertEquals(bound, verdict.live) // Turning core sorting off can leave the base folder unchanged.
        assertTrue(verdict.evidence.detail.contains("/saves/Game.srm"))
        assertEquals(RootVerdict.NoNews, judgeRoots(snapshot.copy(configSavePath = config.copy(proves = true, effective = config.expected)), last))
    }

    @Test fun candidateCanShareTheBoundDirectoryButStillNeedsAReadableBaseline() {
        val beside = root(hash = null).copy(bound = false, spotFile = "Game.srm")
        val before = snap(root(), beside, at = 100)
        val after = snap(root(), beside.copy(spotHash = "played"))
        assertEquals(SessionVerdict.WroteElsewhere(bound), judgeSession(before, after, 400))
        assertEquals(SessionVerdict.NoNews, judgeSession(snap(root(), beside.copy(spotReadable = false)), after, 400))
        assertEquals(SessionVerdict.NoNews, judgeSession(snap(root()), after, 400))
    }

    @Test fun anApprovedPerStartSignalCatchesColdStartsWithoutLogMovement() {
        val before = snap(root(signals = listOf(signal(bound, 50, perStart = true))), at = 100)
        val after = snap(root(signals = listOf(signal(bound, 50, perStart = true))), at = 30_100)
        assertEquals(SessionVerdict.SilentStart("log/azahar_log.txt"), judgeSession(before, after, 30_000))
        assertEquals(SessionVerdict.NoNews, judgeSession(before, after, SAVE_START_CHECK_MIN_MILLIS - 1))
        assertEquals(SessionVerdict.NoNews, judgeSession(before, after, 30_000, startedByFoldcade = false))
        assertEquals(SessionVerdict.NoNews, judgeSession(before, after.copy(roots = listOf(root(signals = emptyList()))), 30_000))
        assertEquals(SessionVerdict.NoNews, judgeSession(before.copy(roots = listOf(root(signals = listOf(signal(bound, null, perStart = true))))), after, 30_000))
        assertFalse(SAVE_NEGATIVE_EVIDENCE_ENABLED) // Emulator-grant evidence remains off.
    }

    @Test fun aWarmLaunchWhoseLogAdvancedHasNoNegativeVerdict() {
        val before = snap(root(signals = listOf(signal(bound, 50, perStart = true))), at = 100)
        // Activity without a fresh directory declaration cannot verify a root by itself.
        val moved = SignalReading("log/azahar_log.txt", null, 300, false, true)
        val after = snap(root(signals = listOf(moved)), at = 30_100)
        assertEquals(SessionVerdict.NoNews, judgeSession(before, after, 30_000))
        val named = after.copy(roots = listOf(root(signals = listOf(signal(bound, 300, perStart = true)))))
        assertTrue(judgeSession(before, named, 30_000) is SessionVerdict.Signalled)
        assertEquals(SessionVerdict.NoNews, judgeSession(before, named.copy(installFirstMillis = 2), 30_000))
    }

    @Test fun aBoundSaveWriteWinsOverAnUnchangedPerStartLog() {
        val log = listOf(signal(bound, 50, perStart = true))
        val before = snap(root(hash = "placed", signals = log), at = 100)
        val after = snap(root(hash = "played", signals = log), at = 30_100)
        assertEquals(SessionVerdict.Wrote(bound), judgeSession(before, after, 30_000))
    }

    @Test fun clockMovementOrADisabledSignalCannotCountAsASilentStart() {
        val before = snap(root(signals = listOf(signal(bound, 50, perStart = true))), at = 100)
        // Both are below the snapshot's timestamp, but neither is an unchanged file.
        for (at in listOf(40L, 80L)) {
            val after = snap(root(signals = listOf(signal(bound, at, perStart = true))), at = 30_100)
            assertEquals(SessionVerdict.NoNews, judgeSession(before, after, 30_000))
        }
        val capped = snap(root(signals = listOf(signal(bound, 50, perStart = false))), at = 30_100)
        assertEquals(SessionVerdict.NoNews, judgeSession(before, capped, 30_000))
    }

    @Test fun aFreshSelfNamedCustomRootWinsOverTheSilentOldRoot() {
        val custom = FolderRef("primary:AzaharQA", "AzaharQA")
        val before = snap(root(signals = listOf(signal(bound, 50, perStart = true))), at = 100)
        val after = snap(root(signals = listOf(signal(bound, 50, perStart = true))),
            root(custom, hash = "fresh game", signals = listOf(signal(custom, 20_100, perStart = true))), at = 30_100)
        val verdict = judgeSession(before, after, 30_000) as SessionVerdict.Signalled
        assertEquals(custom, (verdict.verdict as RootVerdict.UsesOther).live)
    }

    @Test fun anotherInstalledFlavorNeedsAnExactVersionMatch() {
        assertFalse(provingSignal(true, false, 2, null, "2126.2"))
        assertFalse(provingSignal(true, false, 2, "2126.1", "2126.2"))
        assertTrue(provingSignal(true, false, 2, "2126.2", "2126.2"))
        assertTrue(provingSignal(true, true, 2, null, "2126.2"))
        assertTrue(provingSignal(true, false, 1, null, "2126.2"))
    }
}
