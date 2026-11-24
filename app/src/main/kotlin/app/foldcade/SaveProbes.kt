package app.foldcade

import android.content.Context
import android.content.pm.PackageManager
import app.foldcade.api.plugin.*
import app.foldcade.language.*

/** Read-only snapshots, judged in language. Caller runs on IO under the existing save flow. */
internal class SaveProbes(
    private val context: Context,
    private val saves: PlayerSaves,
    private val store: SaveProtectionStore,
    private val folder: (Player) -> String?,
) {
    private val android = AndroidSaveFolderFinder(context)

    @Synchronized fun snapshot(player: Player, game: Game? = null, target: LaunchTarget? = null, hasKnownSave: Boolean = false): ProbeSnapshot? {
        val holder = player as? SaveFolderHolder ?: return null
        // DuckStation's data folder holds firmware; its actual saves are hosted.
        val boundUri = if (player is HostedSaveFolder) player.saveDeclarations(game ?: Game("", "", player.platformId, Availability.LocalOnly, "")).firstOrNull()?.locationUri
            else folder(player)
        boundUri ?: return null
        val probe = player as? SaveLocationProbe
        val grants = android.grants(listOf(holder))
        val finder = android.finder(player, grants)
        val boundIdentity = storageIdentity(boundUri)
        val uris = (listOf(boundUri) + (holder.saveFolderPlaces + probe?.probePlaces.orEmpty())
            .distinct().mapNotNull { finder.reachable("primary:$it") }.filter { storageIdentity(it) != boundIdentity }.take(4))
            .distinctBy(::storageIdentity)
        val installed = player.packageNames.mapNotNull { name ->
            try { context.packageManager.getPackageInfo(name, 0) } catch (_: PackageManager.NameNotFoundException) { null }
        }
        val resolved = installed.firstOrNull()
        val key = player.saveFolderId()
        val roots = uris.map { uri ->
            val ref = FolderRef(storageIdentity(uri), runCatching { folderPathLabel(uri) }.getOrDefault(uri))
            val isBound = ref.identity == boundIdentity
            val access = saves.folderAccess(player, uri, allowWrite = false)
            val children = access.list("")
            val reachable = children != null
            val signals = if (!reachable) emptyList() else try { probe?.liveSignals(access).orEmpty() } catch (_: Exception) { emptyList() }
            // Version matching is conservative: the entire version text must match the resolved APK.
            val logVersion = if (!isBound && installed.size > 1 && signals.isNotEmpty())
                access.read(signals.first().source, 0, 32 * 1024)?.toString(Charsets.UTF_8)?.lineSequence()
                    ?.firstOrNull { "Azahar Version: " in it }?.substringAfter("Azahar Version: ")?.trim() else null
            var readable = reachable
            val hashes = if (game == null || target == null) emptyList() else try {
                val spots = saves.probeSpots(player, game, target, uri)
                if (spots.isEmpty()) readable = false
                spots.mapNotNull { spot -> saves.readSpot(spot, strict = true, limit = 256L * 1024 * 1024)?.first?.let { spot.slot + ":" + md5Hex(it) } }
            } catch (_: Exception) { readable = false; emptyList() }
            RootReading(ref, isBound, signals.map { signal -> SignalReading(signal.source,
                signal.namesUri?.let(::storageIdentity), signal.modifiedAtMillis,
                provingSignal(signal.strength == SignalStrength.Proves, isBound, installed.size, logVersion, resolved?.versionName),
                signal.perStart && SAVE_NEGATIVE_EVIDENCE_ENABLED) },
                hashes.takeIf { it.isNotEmpty() }?.sorted()?.joinToString("\n")?.toByteArray()?.let(::md5Hex), readable,
                if (children == null || holder.saveFolderMarkers.isEmpty()) null else children.any { child -> holder.saveFolderMarkers.any { it.equals(child.name, true) } },
                if (holder.saveFolderPickedInPlayer) android.playerHolds(player, folderDocumentId(uri)) else null,
                store.hadMarkers(key, ref), hasKnownSave,
                // Hosted trees are inside our process and need no persisted grant of our own.
                (player is HostedSaveFolder || android.holdsGrant(uri, grants)))
        }
        return ProbeSnapshot(System.currentTimeMillis(), resolved?.firstInstallTime, resolved?.packageName ?: player.packageNames.firstOrNull().orEmpty(), roots)
    }

    fun location(player: Player, snapshot: ProbeSnapshot): LocationFact {
        val bound = snapshot.roots.single { it.bound }.root
        val last = store.location(player.saveFolderId(), bound, snapshot.packageName, snapshot.installFirstMillis)
        val mismatch = store.incidents().firstOrNull { it.playerKey == player.saveFolderId() && it.kind == IncidentKind.WrongFolder && it.bound?.identity == bound.identity }
        return if (mismatch == null) last else LocationFact.Mismatch(bound, mismatch.live, mismatch.evidence,
            maxOf(mismatch.openedAtMillis, (last as? LocationFact.Verified)?.atMillis ?: 0))
    }

    @Synchronized fun markBound(player: Player) {
        val snapshot = snapshot(player) ?: return
        val root = snapshot.roots.single { it.bound }.root
        store.bound(player.saveFolderId(), root, snapshot.packageName, snapshot.installFirstMillis, snapshot.atMillis)
    }

    /** Snapshot and judgement cannot interleave with another check or a session return. */
    @Synchronized fun refresh(player: Player, hasKnownSave: Boolean): LocationFact? {
        val snapshot = snapshot(player, hasKnownSave = hasKnownSave) ?: return null
        check(player, snapshot)
        return location(player, snapshot)
    }

    fun check(player: Player, snapshot: ProbeSnapshot, game: Game? = null): RootVerdict {
        val verdict = judgeRoots(snapshot, location(player, snapshot))
        apply(player, snapshot, verdict, game)
        return verdict
    }

    fun apply(player: Player, snapshot: ProbeSnapshot, verdict: RootVerdict, game: Game? = null) {
        val key = player.saveFolderId()
        val bound = snapshot.roots.singleOrNull { it.bound } ?: return
        when (verdict) {
            is RootVerdict.Uses -> {
                store.verified(key, verdict.root, verdict.evidence, verdict.atMillis, snapshot.packageName, snapshot.installFirstMillis, bound.markersPresent)
                store.incidents().filter { it.playerKey == key && it.kind != IncidentKind.MaybeNewSave && it.repair == RepairState.NotStarted }.forEach { store.close(it.id) }
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
        // A root-level finding affects every game, even when first discovered on one launch.
        store.put(SaveIncident(id, kind, key, player.displayName, emptyList(), bound, live, evidence,
            old?.openedAtMillis ?: at, old?.kept.orEmpty(), old?.staged.orEmpty(), old?.repair ?: RepairState.NotStarted))
    }

    @Synchronized fun gate(player: Player, game: Game, target: LaunchTarget, hasKnownSave: Boolean = false) {
        snapshot(player, game, target, hasKnownSave)?.let { check(player, it, game) }
        store.incidents().firstOrNull { it.playerKey == player.saveFolderId() && it.kind.gatesLaunch }?.let { throw SaveProtectionStop(it) }
    }

    @Synchronized fun returned(player: Player, game: Game, target: LaunchTarget?, before: ProbeSnapshot?) {
        val after = snapshot(player, game, target)
        if (before != null && after != null) when (val verdict = judgeSession(before, after, after.atMillis - before.atMillis)) {
            is SessionVerdict.Wrote -> apply(player, after, RootVerdict.Uses(verdict.root, Evidence(EvidenceKind.SessionWrite, "${game.label} saved there"), after.atMillis), game)
            is SessionVerdict.WroteElsewhere -> apply(player, after, RootVerdict.UsesOther(verdict.live, Evidence(EvidenceKind.SessionWrite, "${game.label} saved in ${verdict.live.label}"), after.atMillis), game)
            is SessionVerdict.Signalled -> apply(player, after, verdict.verdict, game)
            is SessionVerdict.SilentStart -> open(player, after.roots.single { it.bound }.root, null,
                IncidentKind.MaybeWrongFolder, Evidence(EvidenceKind.FreshLog, "${verdict.source} did not change during play"), after.atMillis, game)
            SessionVerdict.NoNews -> Unit
        }
        if (target != null) {
            for (incident in store.incidents().filter { it.playerKey == player.saveFolderId() && it.kind == IncidentKind.WrongFolder }) {
                val live = incident.live ?: continue
                val holder = player as? SaveFolderHolder ?: continue
                val finder = android.finder(player, android.grants(listOf(holder)))
                val uri = if (live.identity.startsWith("primary:")) finder.reachable(live.identity) else null
                if (uri != null) {
                    val staged = saves.probeSpots(player, game, target, uri).mapNotNull { saves.stageLiveCopy(game, player, it) }
                    if (staged.isNotEmpty()) store.put(incident.copy(staged = (incident.staged + staged).distinct()))
                }
            }
        }
    }
}

internal val IncidentKind.gatesLaunch: Boolean get() = this in setOf(IncidentKind.WrongFolder, IncidentKind.FolderEmptied, IncidentKind.LostAccess)
