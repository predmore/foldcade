// SPDX-License-Identifier: MIT

package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameDetails
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.normalizeGameDetails
import app.foldcade.host.PluginCallException
import app.foldcade.host.PluginHost
import kotlin.coroutines.cancellation.CancellationException

/**
 * Facts for a library record, in metadata-provider order. Held misses avoid
 * repeated reads when focusing a game without info. [forget] lets a new local
 * media index replace a miss; an older in-flight read cannot publish over it.
 */
class GameInfo(private val plugins: PluginHost) {
    private class Held(val details: GameDetails?)
    private val gate = Any()
    private val held = HashMap<String, Held>()
    private var revision = 0L

    fun cached(record: Game?): GameDetails? {
        record ?: return null
        synchronized(gate) { held[keyOf(record)]?.let { return it.details } }
        return plugins.metadataIds().firstNotNullOfOrNull { id ->
            quietly { normalizeGameDetails(plugins.cachedDetails(id, record)) }
        }
    }

    suspend fun fetch(record: Game?): GameDetails? {
        record ?: return null
        val key = keyOf(record)
        while (true) {
            val ticket = synchronized(gate) {
                held[key]?.let { return it.details }
                revision
            }
            val found = plugins.metadataIds().firstNotNullOfOrNull { id ->
                quietly { normalizeGameDetails(plugins.fetchDetails(id, record)) }
            }
            synchronized(gate) {
                if (revision == ticket) {
                    held[key] = Held(found)
                    return found
                }
            }
        }
    }

    /** Forgets [backendId]'s held results whose remote key [which] picks. */
    fun forget(backendId: String, which: (String) -> Boolean = { true }) {
        synchronized(gate) {
            revision++
            val prefix = backendId + "\u0000"
            held.keys.removeAll { it.startsWith(prefix) && which(it.removePrefix(prefix)) }
        }
    }

    private fun keyOf(game: Game): String = game.backendId + "\u0000" + game.remoteKey

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
