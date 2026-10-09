package app.foldcade.plugins.romm

import app.foldcade.api.plugin.PluginException
import app.foldcade.romm.RommConversionPending
import app.foldcade.romm.RommException
import app.foldcade.romm.RommForbidden
import app.foldcade.romm.RommHttpException
import app.foldcade.romm.RommUnauthorized
import app.foldcade.romm.RommProtocolMismatch
import app.foldcade.romm.RommProtocolUnreadable
import app.foldcade.romm.RommSlotMoved
import app.foldcade.romm.RommUnauthenticated
import app.foldcade.romm.RommUnavailable
import app.foldcade.romm.RommUnsupportedServer
import kotlin.coroutines.cancellation.CancellationException

internal fun RommException.toPluginException(): PluginException = when (this) {
    is RommUnauthenticated -> PluginException.NotAuthenticated(message ?: "Sign in to RomM", this)
    is RommUnauthorized -> PluginException.NotAuthenticated(message ?: "Sign in to RomM", this)
    is RommForbidden -> PluginException.NotAuthenticated(message ?: "Sign in to RomM", this)
    is RommHttpException -> when (status) {
        401, 403 -> PluginException.NotAuthenticated(message ?: "Sign in to RomM", this)
        404 -> PluginException.NotFound(message ?: "RomM could not find that", this)
        else -> PluginException.Unavailable(message ?: "RomM returned HTTP $status", this)
    }
    is RommUnavailable -> PluginException.Unavailable(message ?: "RomM is unreachable", this)
    is RommProtocolMismatch -> PluginException.ProtocolMismatch(
        serverVersion,
        message ?: "RomM protocol mismatch",
        this,
    )
    is RommProtocolUnreadable -> PluginException.ProtocolMismatch(
        "unreadable",
        message ?: "RomM protocol is unreadable",
        this,
    )
    is RommUnsupportedServer -> PluginException.ProtocolMismatch(
        serverVersion,
        message ?: "RomM server is not supported",
        this,
    )
    is RommConversionPending -> PluginException.Unavailable(
        message ?: "RomM is converting this ROM",
        this,
    )
    is RommSlotMoved -> PluginException.Unavailable(message ?: "RomM save slot changed", this)
    else -> PluginException.Unavailable(message ?: "RomM request failed", this)
}

internal suspend fun <T> translate(block: suspend () -> T): T =
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (plugin: PluginException) {
        throw plugin
    } catch (error: RommException) {
        throw error.toPluginException()
    }
