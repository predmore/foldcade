package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.SyncOutcome
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
