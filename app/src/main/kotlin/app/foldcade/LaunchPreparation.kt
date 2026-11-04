package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchTarget
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Both Home launch paths finish here after core preparation. Save negotiation
 * only returns firmware offers; every successful route, including a declined
 * card setup or an unresolved save, gets exactly one firmware placement pass.
 * A failed save never reaches firmware or the player.
 */
internal suspend fun LaunchWork.prepareFiles(
    ticket: Int,
    game: Game,
    saves: suspend () -> PreparedLaunch,
    firmware: suspend (target: LaunchTarget, backendId: String) -> Boolean,
): PreparedLaunch? {
    currentCoroutineContext().ensureActive()
    if (!current(ticket)) return null
    val prepared = saves()
    return if (prepare(ticket) { firmware(prepared.target, (prepared.owner ?: game).backendId) }) prepared else null
}
