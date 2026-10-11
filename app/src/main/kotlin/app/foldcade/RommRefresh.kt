package app.foldcade

import app.foldcade.api.plugin.PluginException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Independent, bounded refreshes. A slow or failed connection never holds the next one up. */
internal suspend fun <T> refreshRommServers(
    ids: List<String>,
    load: suspend (String) -> T?,
    onLoaded: suspend (String, T) -> Unit,
    onSignIn: suspend (String) -> Unit,
    onFailure: (String, Exception) -> Unit,
) = supervisorScope {
    val permits = Semaphore(3)
    for (id in ids) launch {
        permits.withPermit {
            try {
                load(id)?.let { onLoaded(id, it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: PluginException.NotAuthenticated) {
                onSignIn(id)
            } catch (failure: Exception) {
                onFailure(id, failure)
            }
        }
    }
}
