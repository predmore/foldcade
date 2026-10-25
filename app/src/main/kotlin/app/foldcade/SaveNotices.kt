package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameActivity
import app.foldcade.api.plugin.SyncOutcome
import app.foldcade.api.plugin.SyncResult
import app.foldcade.language.Copy
import app.foldcade.language.FoldNotice

/**
 * What R1 says after a game's saves were handed back. Only the outcomes worth
 * knowing about: both copies kept, or a save waiting for the server. One notice
 * per game, so playing it again replaces the old one.
 */
internal fun saveNotice(game: Game, outcome: SyncOutcome, nowMillis: Long = System.currentTimeMillis()): FoldNotice? {
    val (title, body) = when (outcome) {
        SyncOutcome.KeptBoth -> Copy.savesKeptTitle to Copy.savesKeptBody
        SyncOutcome.Queued -> Copy.offlineSaveTitle to Copy.offlineSaveBody
        else -> return null
    }
    return FoldNotice(
        id = "foldcade:save:${game.backendId}:${game.remoteKey}",
        title = title,
        body = "${game.label} · $body",
        postedMillis = nowMillis,
    )
}

/**
 * Holds a "waiting for the server" notice until the library's sync says how it
 * ended. A library may keep the save and return [SyncOutcome.Queued] at once,
 * then upload in the background and report [GameActivity.Synced] a moment later,
 * so Queued from a hand-back alone does not mean the server could not be reached.
 *
 * A game's syncs report no hand-back of their own, so this follows the game:
 * - a sync that reached the server, or kept both copies, settles every hand-back
 *   of the game still waiting, and the one on its way, see [handingBack]. Kept
 *   both says so once;
 * - a sync still waiting settles nothing, since another may yet get through;
 * - a hand-back still waiting after [patienceMillis] says it is waiting.
 * A launch's sync, with no hand-back waiting, says nothing. Returns go one at a
 * time, so one hand-back of a game is on its way at most.
 * Used on the main thread.
 */
internal class ReturnSyncNotices(private val patienceMillis: Long = RETURN_SYNC_PATIENCE_MS) {
    /**
     * A game's hand-backs: [dueMillis] when one came back Queued and waits, [onItsWay]
     * while one is with the library, and [settled] what a sync said before it came back.
     */
    private class Waiting(val game: Game) {
        var dueMillis: Long? = null
        var onItsWay = false
        var settled: SyncOutcome? = null
    }

    private val waiting = mutableMapOf<Pair<String, String>, Waiting>()

    /** A hand-back of [game] is about to go to library [libraryId], which may sync it before it returns. */
    fun handingBack(libraryId: String, game: Game) {
        val held = waiting.getOrPut(libraryId to game.remoteKey) { Waiting(game) }
        held.onItsWay = true
        held.settled = null
    }

    /** The hand-back [handingBack] announced did not reach the library. One that waits from before still does. */
    fun dropped(libraryId: String, game: Game) {
        val key = libraryId to game.remoteKey
        val held = waiting[key] ?: return
        held.onItsWay = false
        held.settled = null
        if (held.dueMillis == null) waiting.remove(key)
    }

    /**
     * The notice to post now for a hand-back of [game] in library [libraryId] that
     * returned [outcome], or null. A Queued one waits for [synced] or [expire],
     * unless a sync already settled it.
     */
    fun returned(libraryId: String, game: Game, outcome: SyncOutcome, nowMillis: Long): FoldNotice? {
        val key = libraryId to game.remoteKey
        val held = waiting[key]
        val settled = held?.settled
        held?.onItsWay = false
        held?.settled = null
        if (outcome != SyncOutcome.Queued || settled != null) {
            // The game's save is where the library keeps it now, so nothing before this waits either.
            waiting.remove(key)
            return saveNotice(game, settled ?: outcome, nowMillis)
        }
        (held ?: Waiting(game).also { waiting[key] = it }).dueMillis = nowMillis + patienceMillis
        return null
    }

    /** The library finished a sync of [remoteKey]: the notice for hand-backs it settles, or null. */
    fun synced(libraryId: String, remoteKey: String, result: SyncResult, nowMillis: Long): FoldNotice? {
        val key = libraryId to remoteKey
        val held = waiting[key] ?: return null
        if (result.outcome == SyncOutcome.Queued) return null
        val notice = if (held.dueMillis != null) saveNotice(held.game, result.outcome, nowMillis) else null
        held.dueMillis = null
        if (!held.onItsWay) {
            waiting.remove(key)
        } else {
            // Said once: a kept-both notice posted now is not posted again when the hand-back returns.
            held.settled = if (notice != null) SyncOutcome.Unchanged else result.outcome
        }
        return notice
    }

    /**
     * Waiting notices whose sync never got through, as of [nowMillis]. One whose game has
     * another hand-back on its way waits for that one, which sets its own patience.
     */
    fun expire(nowMillis: Long): List<FoldNotice> {
        val due = waiting.filterValues { held -> !held.onItsWay && held.dueMillis?.let { it <= nowMillis } == true }
        for ((key, held) in due) {
            held.dueMillis = null
            if (!held.onItsWay) waiting.remove(key)
        }
        return due.values.mapNotNull { saveNotice(it.game, SyncOutcome.Queued, nowMillis) }
    }
}

/** How long a hand-back's notice waits for the library to say how its sync ended. */
internal const val RETURN_SYNC_PATIENCE_MS = 45_000L

/** Standing setup notices. Dismissal lasts until that player's next launch attempt. */
internal class SaveSetupNotices {
    private var players = emptyMap<String, String>()
    private val dismissed = mutableSetOf<String>()
    private val posted = mutableMapOf<String, Long>()

    fun update(next: Map<String, String>) {
        dismissed.retainAll(next.keys)
        posted.keys.retainAll(next.keys)
        players = next
    }

    fun launch(playerId: String) { dismissed.remove(playerId) }
    fun dismiss(id: String) { players.keys.firstOrNull { noticeId(it) == id }?.let(dismissed::add) }
    fun clear() { dismissed.addAll(players.keys) }

    fun notices(nowMillis: Long): List<FoldNotice> = players.filterKeys { it !in dismissed }.map { (id, name) ->
        FoldNotice(
            id = noticeId(id), title = Copy.savesNotSyncingTitle(name), body = Copy.savesNotSyncingBody,
            postedMillis = posted.getOrPut(id) { nowMillis }, savePlayerId = id,
        )
    }

    private fun noticeId(playerId: String) = "foldcade:save-setup:$playerId"
}

/** Cold-start ledger saves get the same patience as a hand-back. One notice until fixed. */
internal class WaitingSaveNotices(private val patienceMillis: Long = RETURN_SYNC_PATIENCE_MS) {
    private val since = mutableMapOf<Pair<String, String>, Long>()
    private val told = mutableSetOf<Pair<String, String>>()
    private val posted = mutableMapOf<Pair<String, String>, FoldNotice>()

    fun nextDueMillis(): Long? = since.filterKeys { it !in told }.values.minOrNull()?.plus(patienceMillis)

    fun update(games: List<Game>, nowMillis: Long): List<FoldNotice> {
        val keys = games.map { it.backendId to it.remoteKey }.toSet()
        since.keys.retainAll(keys)
        told.retainAll(keys)
        return games.mapNotNull { game ->
            val key = game.backendId to game.remoteKey
            val start = since.getOrPut(key) { nowMillis }
            if (nowMillis - start >= patienceMillis && told.add(key)) {
                saveNotice(game, SyncOutcome.Queued, nowMillis)?.also { posted[key] = it }
            } else null
        }
    }

    /** Only this producer's own notices expire; a hand-back may replace one under the same id. */
    fun settled(notices: List<FoldNotice>): List<FoldNotice> {
        val done = posted.filterKeys { it !in since }
        done.keys.forEach(posted::remove)
        return notices.filter { notice -> done.values.any { it === notice } }
    }
}
