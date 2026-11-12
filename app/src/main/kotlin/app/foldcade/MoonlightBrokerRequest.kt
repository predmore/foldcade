package app.foldcade

import app.foldcade.net.LanHttpException
import app.foldcade.net.LanReplyException

/** A cached address gets one relocation attempt after a transport failure, never after a reply failure. */
internal suspend fun <T> requestLocatedStreamHost(
    last: LocatedHost?,
    locate: suspend () -> LocatedHost?,
    request: suspend (LocatedHost) -> T,
): T {
    val at = last ?: locate() ?: throw BrokerFailure("unreachable", "Paired computer could not be reached")
    return try {
        request(at)
    } catch (failure: LanReplyException) {
        throw failure
    } catch (failure: LanHttpException) {
        if (last == null) throw failure
        val moved = locate()?.takeIf { it.address != at.address } ?: throw failure
        request(moved)
    }
}
