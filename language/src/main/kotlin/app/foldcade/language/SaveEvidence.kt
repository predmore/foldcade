package app.foldcade.language

/** Generic negative evidence (including emulator grants) stays off. perStart is a probe opt-in. */
const val SAVE_NEGATIVE_EVIDENCE_ENABLED = false
const val SAVE_START_CHECK_MIN_MILLIS = 20_000L

data class SignalReading(val source: String, val names: String?, val modifiedAtMillis: Long?, val proves: Boolean, val perStart: Boolean)
data class RootReading(
    val root: FolderRef,
    val bound: Boolean,
    val signals: List<SignalReading>,
    val spotHash: String?,
    val spotReadable: Boolean,
    val markersPresent: Boolean?,
    val emulatorHolds: Boolean?,
    /** Recorded marker presence and held/server bytes, needed to distinguish an emptied folder. */
    val hadMarkers: Boolean = false,
    val hasKnownSave: Boolean = false,
    val reachable: Boolean = true,
    /** An exact candidate file, which can share a directory with a different bound spot. */
    val spotFile: String? = null,
    /** The ROM's parent is a candidate for this game only. */
    val besideGame: Boolean = false,
)

data class ConfigSavePath(val expected: String?, val effective: String?, val live: FolderRef?, val proves: Boolean) {
    val matches: Boolean get() = proves && effective != null && expected == effective
}

data class ProbeSnapshot(val atMillis: Long, val installFirstMillis: Long?, val packageName: String, val roots: List<RootReading>,
    val configSavePath: ConfigSavePath? = null, val configUnavailable: Boolean = false, val configDetail: String? = null)
sealed interface RootVerdict {
    data class Uses(val root: FolderRef, val evidence: Evidence, val atMillis: Long) : RootVerdict
    data class UsesOther(val live: FolderRef?, val evidence: Evidence, val atMillis: Long) : RootVerdict
    data class Lost(val kind: IncidentKind, val evidence: Evidence) : RootVerdict
    data object NoNews : RootVerdict
}
sealed interface SessionVerdict {
    data class Wrote(val root: FolderRef) : SessionVerdict
    data class WroteElsewhere(val live: FolderRef?, val besideGame: Boolean = false) : SessionVerdict
    data class Signalled(val verdict: RootVerdict) : SessionVerdict
    data class SilentStart(val source: String) : SessionVerdict
    data object NoNews : SessionVerdict
}

fun judgeRoots(now: ProbeSnapshot, last: LocationFact, negativeEvidence: Boolean = SAVE_NEGATIVE_EVIDENCE_ENABLED): RootVerdict {
    val bound = now.roots.singleOrNull { it.bound } ?: return RootVerdict.NoNews
    if (!bound.reachable) return RootVerdict.Lost(IncidentKind.LostAccess, Evidence(EvidenceKind.Config, "Folder access is gone"))
    now.configSavePath?.let { config ->
        if (config.proves && config.expected != null && config.effective != null && !config.matches)
            return RootVerdict.UsesOther(config.live, Evidence(EvidenceKind.Config, "RetroArch configured ${config.effective}"), now.atMillis)
    }
    if (negativeEvidence && bound.emulatorHolds == false) {
        val other = now.roots.filter { !it.bound && it.emulatorHolds == true }.singleOrNull()
        return if (other != null) RootVerdict.UsesOther(other.root, Evidence(EvidenceKind.Config, "${now.packageName} holds ${other.root.label}, not ${bound.root.label}"), now.atMillis)
        else RootVerdict.Lost(IncidentKind.LostAccess, Evidence(EvidenceKind.Config, "The emulator no longer holds this folder"))
    }
    // Unknown mtimes and tied newest readings cannot distinguish a live log from an old copy.
    val signals = now.roots.flatMap { root -> root.signals.filter { it.proves && it.names != null }.map { root to it } }
    if (signals.isNotEmpty() && signals.none { it.second.modifiedAtMillis == null }) {
        val newest = signals.maxOf { it.second.modifiedAtMillis!! }
        val latest = signals.filter { it.second.modifiedAtMillis == newest }.singleOrNull()
        val lastAt = when (last) {
            is LocationFact.Verified -> last.atMillis
            is LocationFact.Mismatch -> last.atMillis
            is LocationFact.Bound -> last.setAtMillis ?: now.installFirstMillis ?: 0L
            else -> now.installFirstMillis ?: 0L
        }
        if (latest != null && newest > lastAt && newest <= now.atMillis) {
            val (root, signal) = latest
            val named = now.roots.firstOrNull { it.root.identity == signal.names }?.root
                ?: FolderRef(signal.names!!, signal.names.substringAfter(':'))
            val evidence = Evidence(EvidenceKind.FreshLog, "${signal.source} in ${root.root.label}")
            return if (named.identity == bound.root.identity) RootVerdict.Uses(bound.root, evidence, newest)
            else RootVerdict.UsesOther(named, evidence, newest)
        }
    }
    if (bound.hadMarkers && bound.markersPresent == false && bound.hasKnownSave) {
        return RootVerdict.Lost(IncidentKind.FolderEmptied, Evidence(EvidenceKind.Config, "The folder’s save markers are gone"))
    }
    return RootVerdict.NoNews
}

fun judgeSession(before: ProbeSnapshot, after: ProbeSnapshot, sessionMillis: Long, startedByFoldcade: Boolean = true): SessionVerdict {
    if (before.packageName != after.packageName || before.installFirstMillis != after.installFirstMillis) return SessionVerdict.NoNews
    val bound = after.roots.singleOrNull { it.bound } ?: return SessionVerdict.NoNews
    val oldBound = before.roots.singleOrNull { it.bound && it.root.identity == bound.root.identity } ?: return SessionVerdict.NoNews
    if (spotChanged(before, bound)) return SessionVerdict.Wrote(bound.root)
    if (oldBound.spotReadable && bound.spotReadable && oldBound.spotFile == bound.spotFile && oldBound.spotHash == bound.spotHash) {
        val changedCandidates = after.roots.filter { !it.bound && spotChanged(before, it) }
        if (changedCandidates.isNotEmpty()) return SessionVerdict.WroteElsewhere(
            changedCandidates.singleOrNull()?.root, changedCandidates.any { it.besideGame })
    }
    val advanced = after.roots.any { root -> root.signals.any { signal ->
        signal.proves && signal.modifiedAtMillis != null && signal.modifiedAtMillis > before.atMillis &&
            before.roots.firstOrNull { it.root.identity == root.root.identity }?.signals
                ?.firstOrNull { it.source == signal.source }?.modifiedAtMillis != signal.modifiedAtMillis
    } }
    val signalled = if (advanced) judgeRoots(after.copy(configSavePath = null),
        LocationFact.Verified(bound.root, Evidence(EvidenceKind.FreshLog, "Session baseline"), before.atMillis)) else RootVerdict.NoNews
    if (signalled is RootVerdict.UsesOther) return SessionVerdict.Signalled(signalled)
    if (startedByFoldcade && sessionMillis >= SAVE_START_CHECK_MIN_MILLIS) {
        oldBound.signals.firstOrNull { signal -> signal.perStart && signal.modifiedAtMillis != null && signal.modifiedAtMillis <= before.atMillis &&
            bound.signals.any { it.source == signal.source && it.perStart && it.modifiedAtMillis == signal.modifiedAtMillis }
        }?.let { return SessionVerdict.SilentStart(it.source) }
    }
    if (signalled != RootVerdict.NoNews) return SessionVerdict.Signalled(signalled)
    return SessionVerdict.NoNews
}

fun spotChanged(before: ProbeSnapshot, root: RootReading): Boolean {
    val old = before.roots.singleOrNull {
        it.bound == root.bound && it.root.identity == root.root.identity && it.spotFile == root.spotFile && it.besideGame == root.besideGame
    }
        ?: return false
    return old.spotReadable && root.spotReadable && root.spotHash != null && root.spotHash != old.spotHash
}

/** A candidate from another installed flavour needs a matching version line. */
fun provingSignal(proves: Boolean, bound: Boolean, installedFlavors: Int, logVersion: String?, resolvedVersion: String?): Boolean =
    proves && (bound || installedFlavors <= 1 || logVersion != null && logVersion == resolvedVersion)

/** A bypassed session cannot clear its own alarm, even when fresh saves restore markers. */
fun SaveIncident.causeResolved(bound: RootReading): Boolean = !recoveryRequired && when (kind) {
    IncidentKind.LostAccess -> bound.reachable && bound.spotReadable && (evidence.detail != "The emulator no longer holds this folder" || bound.emulatorHolds == true)
    IncidentKind.FolderEmptied -> bound.markersPresent == true
    else -> false
}

val IncidentKind.gatesLaunch: Boolean get() = this in setOf(IncidentKind.WrongFolder, IncidentKind.FolderEmptied, IncidentKind.LostAccess)
val SaveIncident.blocksLaunch: Boolean get() = kind.gatesLaunch && repair != RepairState.Done
