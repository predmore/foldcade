// SPDX-License-Identifier: MIT

package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameDetails
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.normalizeGameDetails
import app.foldcade.host.PluginCallException
import app.foldcade.host.PluginHost
import kotlin.coroutines.cancellation.CancellationException

/** Facts read directly from providers' current snapshots, in metadata-provider order. */
class GameInfo(private val plugins: PluginHost) {
    fun cached(record: Game?): GameDetails? {
        record ?: return null
        return plugins.metadataIds().firstNotNullOfOrNull { id ->
            quietly { normalizeGameDetails(plugins.cachedDetails(id, record)) }
        }
    }

    suspend fun fetch(record: Game?): GameDetails? {
        record ?: return null
        return plugins.metadataIds().firstNotNullOfOrNull { id ->
            quietly { normalizeGameDetails(plugins.fetchDetails(id, record)) }
        }
    }

    /** Kept for callers that invalidate provider snapshots; this lookup holds no results itself. */
    @Suppress("UNUSED_PARAMETER")
    fun forget(backendId: String, which: (String) -> Boolean = { true }) = Unit

    private inline fun <T> quietly(block: () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: PluginException) {
        null
    } catch (_: PluginCallException) {
        null
    }
}
