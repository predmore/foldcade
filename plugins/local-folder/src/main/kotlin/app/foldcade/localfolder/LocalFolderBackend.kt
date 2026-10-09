package app.foldcade.localfolder

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GamePage
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.ListedPlatform
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.Placement
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveSet
import app.foldcade.api.plugin.SaveSlot
import app.foldcade.api.plugin.SyncOutcome
import app.foldcade.api.plugin.SyncResult
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Local-folder [LibraryBackend]. Games come from one cached [scanFolderTree].
 * Later pages read that cache. They do not walk the tree again.
 *
 * Platform ids are the canonical ids the scan already stored, such as
 * `nintendo-3ds`. Alias resolution stays on the plugin API. This type does
 * not match `3ds` or `n3ds` itself, and it is not a metadata provider.
 *
 * [Game.remoteKey] is the document URI. Cancellation is the calling
 * coroutine. This type does not read a filesystem path.
 */
class LocalFolderBackend(
    root: FolderEntry? = null,
    childrenOf: ((FolderEntry) -> List<FolderEntry>)? = null,
) : LibraryBackend {
    override val id: String = ID
    override val displayName: String = DISPLAY_NAME

    private val generation = AtomicInteger()
    private val binding = AtomicReference<Binding?>(null)

    @Volatile
    private var stored: StoredScan? = null

    init {
        if (root != null && childrenOf != null) {
            bindTree(root, childrenOf)
        }
    }

    /**
     * Document tree to scan. Call this before [connect].
     * Replaces any tree already set and drops the cached scan.
     * A scan that started on the previous tree does not publish after this returns.
     * The generation is the check. This method does not take [gate].
     */
    fun bindTree(root: FolderEntry, childrenOf: (FolderEntry) -> List<FolderEntry>) {
        val ticket = generation.incrementAndGet()
        val next = Binding(ticket, root, childrenOf)
        while (true) {
            val current = binding.get()
            if (current != null && current.generation > ticket) return
            stored = null
            if (binding.compareAndSet(current, next)) return
        }
    }

    private val gate = Mutex()
    private val remembered = HashMap<String, Map<String, RememberedSave>>()

    override suspend fun connect() {
        val current = binding.get() ?: throw PluginException.Unavailable("Choose a folder.")
        val scan = scanOf(current)
        gate.withLock { publish(current.generation, scan) }
    }

    override suspend fun disconnect() {
        coroutineContext.ensureActive()
        gate.withLock { stored = null }
    }

    override suspend fun listPlatforms(): List<ListedPlatform> {
        val scan = load()
        return scan.platforms.map { platform ->
            ListedPlatform(platformId = platform.id, displayName = platform.name)
        }
    }

    override suspend fun listGames(platformId: String, query: GameQuery): GamePage {
        val scan = load()
        return page(scan, platformId, query)
    }

    override suspend fun ensureLocal(game: Game): LaunchTarget {
        val found = requireGame(game)
        return LaunchTarget.ContentUri(uri = found.documentUri)
    }

    override suspend fun saves(game: Game): SaveSet {
        requireGame(game)
        val slots = gate.withLock { remembered[game.remoteKey].orEmpty() }
        return SaveSet(
            ownerBackendId = id,
            slots = slots.values
                .sortedBy { it.slot }
                .map { SaveSlot(slot = it.slot, contentHash = it.contentHash, lastPlayerId = it.lastPlayerId) },
        )
    }

    override suspend fun prepareLaunch(game: Game, player: Player): Placement {
        val found = requireGame(game)
        return Placement(
            target = LaunchTarget.ContentUri(uri = found.documentUri),
            savesToPlace = emptyList(),
            sync = SyncResult(SyncOutcome.Unchanged),
        )
    }

    override suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves): SyncResult {
        requireGame(game)
        return gate.withLock {
            coroutineContext.ensureActive()
            val prior = remembered[game.remoteKey].orEmpty()
            val merged = mergeSaves(prior, observed)
            remembered[game.remoteKey] = merged.saves
            merged.result
        }
    }

    private suspend fun load(): FolderScan {
        while (true) {
            coroutineContext.ensureActive()
            published()?.let { return it }
            val current = binding.get() ?: throw PluginException.Unavailable("Choose a folder.")
            val scan = scanOf(current)
            val fresh = gate.withLock {
                val latest = binding.get()
                if (latest == null || latest.generation != current.generation) {
                    null
                } else {
                    published() ?: scan.also { publish(current.generation, it) }
                }
            }
            if (fresh != null) return fresh
        }
    }

    private suspend fun scanOf(current: Binding): FolderScan {
        coroutineContext.ensureActive()
        val context = coroutineContext
        val scan = scanFolderTree(
            root = current.root,
            isCancelled = { !context.isActive },
            childrenOf = current.childrenOf,
        )
        if (scan.cancelled || !context.isActive) {
            throw CancellationException("Local folder scan was cancelled")
        }
        return scan
    }

    /** Publishes [scan] only when [ticket] is still the bound tree. */
    private fun publish(ticket: Int, scan: FolderScan) {
        val current = binding.get()
        if (current == null || current.generation != ticket) return
        stored = StoredScan(ticket, scan)
        val latest = binding.get()
        if (latest == null || latest.generation != ticket) {
            stored = null
        }
    }

    private fun published(): FolderScan? {
        val current = binding.get() ?: return null
        val snap = stored ?: return null
        return if (snap.generation == current.generation) snap.scan else null
    }

    private suspend fun requireGame(game: Game): FolderGame {
        if (game.backendId != id) {
            throw PluginException.NotFound("Game is not in this folder.")
        }
        val scan = load()
        return scan.games.firstOrNull { it.documentUri == game.remoteKey }
            ?: throw PluginException.NotFound("Game is not in this folder.")
    }

    companion object {
        const val ID: String = "local-folder"
        const val DISPLAY_NAME: String = "Local folder"
    }
}

private class Binding(
    val generation: Int,
    val root: FolderEntry,
    val childrenOf: (FolderEntry) -> List<FolderEntry>,
)

private class StoredScan(
    val generation: Int,
    val scan: FolderScan,
)

private data class RememberedSave(
    val slot: String,
    val contentHash: String,
    val lastPlayerId: String?,
)

private data class MergedSaves(
    val saves: Map<String, RememberedSave>,
    val result: SyncResult,
)

private fun page(scan: FolderScan, platformId: String, query: GameQuery): GamePage {
    val needle = query.text.trim()
    val matched = scan.games.filter { game ->
        game.platformId == platformId &&
            (needle.isEmpty() ||
                game.title.contains(needle, ignoreCase = true) ||
                game.fileName.contains(needle, ignoreCase = true))
    }
    val offset = query.offset.coerceAtLeast(0)
    // A negative limit is an empty page, same as zero. Zero still names the
    // next offset when this offset is inside the matches, so the caller can
    // see that a later page exists. A limit past the remainder returns that
    // remainder. An offset past the end returns an empty page.
    val limit = query.limit.coerceAtLeast(0)
    val games = matched.drop(offset).take(limit).map { it.toGame() }
    val consumed = offset + games.size
    val nextOffset = if (consumed < matched.size) consumed else null
    return GamePage(games = games, nextOffset = nextOffset, total = matched.size)
}

private fun FolderGame.toGame(): Game = Game(
    backendId = LocalFolderBackend.ID,
    remoteKey = documentUri,
    platformId = platformId,
    availability = Availability.LocalOnly,
    label = title,
)

private fun mergeSaves(prior: Map<String, RememberedSave>, observed: ObservedSaves): MergedSaves {
    if (observed.slots.isEmpty()) {
        return MergedSaves(prior, SyncResult(SyncOutcome.Unchanged))
    }
    val next = prior.toMutableMap()
    var keptBoth = false
    for (slot in observed.slots) {
        val hash = slot.contentHash.lowercase(Locale.ROOT)
        val previous = next[slot.slot]
        val conflict = previous != null &&
            previous.lastPlayerId != null &&
            previous.lastPlayerId != slot.playerId &&
            previous.contentHash != hash
        if (conflict) {
            keptBoth = true
            val archive = "${slot.slot}.kept"
            if (archive !in next) {
                next[archive] = previous.copy(slot = archive)
            }
        }
        next[slot.slot] = RememberedSave(
            slot = slot.slot,
            contentHash = hash,
            lastPlayerId = slot.playerId,
        )
    }
    val result = if (keptBoth) {
        SyncResult(outcome = SyncOutcome.KeptBoth, note = "Both saves were kept.")
    } else {
        SyncResult(outcome = SyncOutcome.Unchanged)
    }
    return MergedSaves(saves = next, result = result)
}
