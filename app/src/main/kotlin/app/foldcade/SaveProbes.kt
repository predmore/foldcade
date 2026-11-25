package app.foldcade

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import app.foldcade.api.plugin.*
import app.foldcade.language.*
import app.foldcade.plugins.eden.EdenPlayer
import kotlin.coroutines.cancellation.CancellationException

/** Read-only snapshots, judged in language. Caller runs on IO under the existing save flow. */
internal class SaveProbes(
    private val context: Context,
    private val saves: PlayerSaves,
    private val store: SaveProtectionStore,
    private val folder: (Player) -> String?,
    private val copies: SaveIncidentCopies,
    private val edenReadsAllowed: () -> Boolean = { true },
) {
    private val android by lazy { AndroidSaveFolderFinder(context) }

    @Synchronized fun snapshot(player: Player, game: Game? = null, target: LaunchTarget? = null, hasKnownSave: Boolean = false, resolvedPackage: String? = null, discoverAfterMillis: Long? = null, edenSpots: List<SaveSpot>? = null): ProbeSnapshot? {
        val holder = player as? SaveFolderHolder ?: return null
        if (player is EdenPlayer && !edenReadsAllowed()) return null
        // DuckStation's data folder holds firmware; its actual saves are hosted.
        val boundUri = if (player is HostedSaveFolder) player.saveDeclarations(game ?: Game("", "", player.platformId, Availability.LocalOnly, "")).firstOrNull()?.locationUri
            else folder(player)
        boundUri ?: return null
        val probe = player as? SaveLocationProbe
        val grants = android.grants(listOf(holder))
        val finder = android.finder(player, grants)
        val boundIdentity = storageIdentity(boundUri, context.packageName)
        val installed = player.packageNames.mapNotNull { name ->
            try { context.packageManager.getPackageInfo(name, 0) } catch (_: PackageManager.NameNotFoundException) { null }
        }
        val resolved = installed.firstOrNull { it.packageName == resolvedPackage } ?: installed.firstOrNull()
        val key = player.saveFolderId()
        fun readRoot(uri: String): RootReading {
            val ref = FolderRef(storageIdentity(uri, context.packageName), if (player is EdenPlayer) player.displayName
                else runCatching { folderPathLabel(uri) }.getOrDefault(uri))
            val isBound = ref.identity == boundIdentity
            val access = saves.folderAccess(player, uri, allowWrite = false)
            val children = access.list("")
            val reachable = children != null
            val signals = if (!reachable) emptyList() else try { probe?.liveSignals(access).orEmpty() } catch (_: Exception) { emptyList() }
            // A candidate from another installed flavour must name the launched APK version.
            val logVersion = if (!isBound && installed.size > 1 && signals.isNotEmpty())
                access.read(signals.first().source, 0, 32 * 1024)?.toString(Charsets.UTF_8)?.let(::azaharLogVersion) else null
            var readable = reachable
            val hashes = if (game == null || target == null) emptyList() else try {
                val spots = if (player is EdenPlayer) edenSpots.orEmpty() else saves.probeSpots(player, game, target, uri)
                if (spots.isEmpty()) readable = false
                spots.mapNotNull { spot -> saves.readSpot(spot, strict = true, limit = 256L * 1024 * 1024)?.first?.let { spot.slot + ":" + md5Hex(it) } }
            } catch (_: Exception) { readable = false; emptyList() }
            return RootReading(ref, isBound, signals.map { signal -> SignalReading(signal.source,
                signal.namesUri?.let { storageIdentity(it, context.packageName) }, signal.modifiedAtMillis,
                provingSignal(signal.strength == SignalStrength.Proves, isBound, installed.size, logVersion, resolved?.versionName),
                signal.perStart) },
                hashes.takeIf { it.isNotEmpty() }?.sorted()?.joinToString("\n")?.toByteArray()?.let(::md5Hex), readable,
                if (children == null || holder.saveFolderMarkers.isEmpty()) null else children.any { child -> holder.saveFolderMarkers.any { it.equals(child.name, true) } },
                if (holder.saveFolderPickedInPlayer) android.playerHolds(player, folderDocumentId(uri)) else null,
                store.hadMarkers(key, ref), hasKnownSave,
                // Hosted trees are inside our process and need no persisted grant of our own.
                (player is HostedSaveFolder || android.holdsGrant(uri, grants)))
        }
        val bound = readRoot(boundUri)
        val signature = probe?.signatureFile
        val discovered = if (discoverAfterMillis != null && signature != null && grants.any { it.allFiles } &&
            needsSignatureDiscovery(bound, signature, discoverAfterMillis)) {
            saveSignatureRoots(Environment.getExternalStorageDirectory(), signature, discoverAfterMillis) { id ->
                finder.reachable(id)?.let { probe.liveSignals(saves.folderAccess(player, it, allowWrite = false)) }.orEmpty()
            }
        } else emptyList()
        val knownLive = store.incidents().filter { it.playerKey == player.saveFolderId() && it.blocksLaunch }.mapNotNull { it.live?.identity }
        val candidates = if (player is EdenPlayer) emptyList() else
            discovered + knownLive + (holder.saveFolderPlaces + probe?.probePlaces.orEmpty()).map { "primary:$it" }
        val roots = listOf(bound) + candidates.distinct().mapNotNull(finder::reachable)
            .filter { storageIdentity(it, context.packageName) != boundIdentity }.distinctBy { storageIdentity(it, context.packageName) }.take(4).map(::readRoot)
        return ProbeSnapshot(System.currentTimeMillis(), resolved?.firstInstallTime, resolved?.packageName ?: player.packageNames.firstOrNull().orEmpty(), roots)
    }

    fun location(player: Player, snapshot: ProbeSnapshot): LocationFact {
        val bound = snapshot.roots.single { it.bound }.root
        val last = store.location(player.saveFolderId(), bound, snapshot.packageName, snapshot.installFirstMillis)
        val mismatch = store.incidents().firstOrNull { it.playerKey == player.saveFolderId() && it.kind == IncidentKind.WrongFolder && it.repair != RepairState.Done && it.bound?.identity == bound.identity }
        return if (mismatch == null) last else LocationFact.Mismatch(bound, mismatch.live, mismatch.evidence,
            maxOf(mismatch.openedAtMillis, (last as? LocationFact.Verified)?.atMillis ?: 0))
    }

    @Synchronized fun markBound(player: Player) {
        if (player !is SaveFolderHolder) return
        val uri = if (player is HostedSaveFolder) player.saveDeclarations(Game("", "", player.platformId, Availability.LocalOnly, "")).firstOrNull()?.locationUri else folder(player)
        uri ?: return
        val resolved = player.packageNames.firstNotNullOfOrNull { name ->
            try { context.packageManager.getPackageInfo(name, 0) } catch (_: PackageManager.NameNotFoundException) { null }
        }
        val root = FolderRef(storageIdentity(uri, context.packageName), runCatching { folderPathLabel(uri) }.getOrDefault(uri))
        store.bound(player.saveFolderId(), root, resolved?.packageName ?: player.packageNames.firstOrNull().orEmpty(), resolved?.firstInstallTime, System.currentTimeMillis())
    }

    /** Snapshot and judgement cannot interleave with another check or a session return. */
    @Synchronized fun refresh(player: Player, hasKnownSave: Boolean): LocationFact? {
        val snapshot = snapshot(player, hasKnownSave = hasKnownSave) ?: return null
        check(player, snapshot)
        return location(player, snapshot)
    }

    fun check(player: Player, snapshot: ProbeSnapshot, game: Game? = null, eden: EdenSaveAccess? = null): RootVerdict {
        val bound = snapshot.roots.singleOrNull { it.bound }
        if (bound != null) store.incidents().filter { it.playerKey == player.saveFolderId() && it.causeResolved(bound) }.forEach { store.close(it.id) }
        val verdict = if (player is EdenPlayer && bound?.reachable == true) {
            val key = player.saveFolderId()
            val access = eden ?: folder(player)?.let { saves.edenSaveAccess(player, it) } ?: EdenSaveAccess.Unknown
            if (access == EdenSaveAccess.Unknown && store.location(key, bound.root, snapshot.packageName, snapshot.installFirstMillis) is LocationFact.Verified) {
                store.bound(key, bound.root, snapshot.packageName, snapshot.installFirstMillis, snapshot.atMillis)
            }
            (access as? EdenSaveAccess.Ready)?.session()?.let { store.observeEdenContext(key, it, snapshot.atMillis) }
            edenRootVerdict(bound.root, access, snapshot.packageName, snapshot.atMillis)
        } else judgeRoots(snapshot, location(player, snapshot))
        apply(player, snapshot, verdict, game)
        return verdict
    }

    fun apply(player: Player, snapshot: ProbeSnapshot, verdict: RootVerdict, game: Game? = null) {
        val key = player.saveFolderId()
        val bound = snapshot.roots.singleOrNull { it.bound } ?: return
        when (verdict) {
            is RootVerdict.Uses -> {
                if (store.incidents().any { it.playerKey == key && it.recoveryRequired }) return
                store.verified(key, verdict.root, verdict.evidence, verdict.atMillis, snapshot.packageName, snapshot.installFirstMillis, bound.markersPresent)
                store.incidents().filter { it.playerKey == key && it.kind != IncidentKind.MaybeNewSave }.forEach { store.close(it.id) }
            }
            is RootVerdict.UsesOther -> open(player, bound.root, verdict.live, IncidentKind.WrongFolder, verdict.evidence, verdict.atMillis, game)
            is RootVerdict.Lost -> open(player, bound.root, null, verdict.kind, verdict.evidence, snapshot.atMillis, game)
            RootVerdict.NoNews -> Unit
        }
    }

    private fun open(player: Player, bound: FolderRef, live: FolderRef?, kind: IncidentKind, evidence: Evidence, at: Long, game: Game?) {
        val key = player.saveFolderId()
        val id = "${kind.name}:$key"
        val old = store.incidents().firstOrNull { it.id == id }
        // A fresh positive finding reopens a completed fix, including one retaining offline copies.
        val repair = old?.repair?.takeUnless { it == RepairState.Done } ?: RepairState.NotStarted
        // A root-level finding affects every game, even when first discovered on one launch.
        store.put(SaveIncident(id, kind, key, player.displayName, emptyList(), bound, live, evidence,
            old?.openedAtMillis ?: at, old?.kept.orEmpty(), old?.staged.orEmpty(), repair, old?.recoveryRequired ?: false))
    }

    @Synchronized fun gate(player: Player, game: Game, hasKnownSave: Boolean = false, resolvedPackage: String? = null,
        eden: EdenSaveAccess? = null, onReading: (ProbeSnapshot?) -> Unit = {}) {
        var reading: ProbeSnapshot? = null
        try {
            // Root judgement needs no game hashes. A failed optional probe is not a positive fact.
            reading = snapshot(player, hasKnownSave = hasKnownSave, resolvedPackage = resolvedPackage)
            reading?.let { check(player, it, game, eden) }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { Unit
        } finally { onReading(reading) }
        store.incidents().firstOrNull { it.playerKey == player.saveFolderId() && it.blocksLaunch }?.let { throw SaveProtectionStop(it) }
    }

    fun noteSession(player: Player, game: Game, before: ProbeSnapshot, after: ProbeSnapshot,
        sessionMillis: Long = after.atMillis - before.atMillis, startedByFoldcade: Boolean = true, unprotected: Boolean = false) {
        when (val verdict = judgeSession(before, after, sessionMillis, startedByFoldcade)) {
            is SessionVerdict.Wrote -> if (!unprotected) apply(player, after, RootVerdict.Uses(verdict.root, Evidence(EvidenceKind.SessionWrite, "${game.label} saved there"), after.atMillis), game)
            is SessionVerdict.WroteElsewhere -> apply(player, after, RootVerdict.UsesOther(verdict.live, Evidence(EvidenceKind.SessionWrite, verdict.live?.let { "${game.label} saved in ${it.label}" } ?: "${game.label} saved in another folder"), after.atMillis), game)
            is SessionVerdict.Signalled -> if (!unprotected || verdict.verdict !is RootVerdict.Uses) apply(player, after, verdict.verdict, game)
            is SessionVerdict.SilentStart -> {
                val bound = after.roots.single { it.bound }.root
                open(player, bound, null, IncidentKind.WrongFolder,
                    Evidence(EvidenceKind.FreshLog, SaveProtectionCopy.unchangedLog(player.displayName)), after.atMillis, game)
            }
            SessionVerdict.NoNews -> Unit
        }
    }

    fun judgeReturn(player: Player, game: Game, target: LaunchTarget?, before: ProbeSnapshot?, unprotected: Boolean = false,
        startedByFoldcade: Boolean = true, sessionStartedAtMillis: Long? = null, sessionEndedAtMillis: Long? = null,
        edenSpots: List<SaveSpot>? = null,
    ): ProbeSnapshot? {
        val after = snapshot(player, game, target, resolvedPackage = before?.packageName, discoverAfterMillis = before?.atMillis, edenSpots = edenSpots)
        if (before != null && after != null) noteSession(player, game, before, after,
            (sessionEndedAtMillis ?: after.atMillis) - (sessionStartedAtMillis ?: before.atMillis), startedByFoldcade, unprotected)
        return after
    }

    suspend fun captureReturn(player: Player, game: Game, target: LaunchTarget?) {
        if (player is EdenPlayer) return // Eden stages only the account frozen for the session.
        if (target != null) {
            for (incident in store.incidents().filter { it.playerKey == player.saveFolderId() && it.kind == IncidentKind.WrongFolder }) {
                val live = incident.live ?: continue
                val holder = player as? SaveFolderHolder ?: continue
                val finder = android.finder(player, android.grants(listOf(holder)))
                val uri = if (live.identity.startsWith("primary:")) finder.reachable(live.identity) else null
                if (uri != null) {
                    copies.stage(game, player, saves.probeSpots(player, game, target, uri), incident, mark = false)
                    copies.keep(game, player)
                }
            }
        }
    }
}

internal fun edenRootVerdict(bound: FolderRef, access: EdenSaveAccess, packageName: String, at: Long): RootVerdict = when (access) {
    EdenSaveAccess.Unknown -> RootVerdict.NoNews
    is EdenSaveAccess.NeedsGrant -> {
        val id = access.documentId
        RootVerdict.UsesOther(FolderRef(id, id.substringAfter(':').substringAfterLast('/').ifEmpty { "Storage" }),
            Evidence(EvidenceKind.Config, "Eden’s configuration names a folder Foldcade can’t access."), at)
    }
    is EdenSaveAccess.Ready -> {
        val own = java.net.URI(access.dataUri).authority == EdenFolders.authority(packageName)
        val evidence = if (!access.configured && own) Evidence(EvidenceKind.OwnProvider, "Eden’s own folder")
            else Evidence(EvidenceKind.Config, "Eden’s configuration names its save folder.")
        RootVerdict.Uses(bound, evidence, at)
    }
}

/** The host compares Azahar's version token with PackageInfo.versionName. */
internal fun azaharLogVersion(text: String): String? = text.lineSequence()
    .firstOrNull { "Azahar Version: " in it }?.substringAfter("Azahar Version: ")?.substringBefore(" | ")?.trim()?.takeIf { it.isNotEmpty() }
