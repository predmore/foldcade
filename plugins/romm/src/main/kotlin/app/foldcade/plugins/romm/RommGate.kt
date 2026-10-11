package app.foldcade.plugins.romm

import app.foldcade.api.plugin.PluginException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class RommGate(
    private val source: () -> RommWiring?,
) {
    private val catalogs = ConcurrentHashMap<Path, RommCatalog>()
    val catalog: RommCatalog get() = catalog(wiring())

    fun catalog(wiring: RommWiring): RommCatalog = catalogs.computeIfAbsent(wiring.data.catalogFile()) {
        RommCatalog(it, wiring.origin)
    }

    suspend fun restoreCatalog(wiring: RommWiring = wiring()): RommCatalog =
        catalog(wiring).also { runInterruptible(Dispatchers.IO) { it.restore() } }
    private val gate = Mutex()
    private var held: Held? = null

    fun peek(): RommWiring? = source()

    fun wiring(): RommWiring {
        val wiring = source() ?: throw PluginException.NotAuthenticated("RomM is not set up")
        if (wiring.origin.isBlank()) throw PluginException.NotAuthenticated("RomM is not set up")
        return wiring
    }

    /**
     * Reuses one client while the origin and token stay the same.
     * The caller holds that client until [block] returns. A replacement or
     * [disconnect] retires it and closes it once no call is still inside.
     */
    suspend fun <T> session(block: suspend (RommWiring, RommOps) -> T): T {
        val wiring = wiring()
        restoreCatalog(wiring)
        val token = runInterruptible(Dispatchers.IO) {
            wiring.tokenSource.accessToken()?.trim()?.removePrefix("Bearer ")?.trim()?.ifEmpty { null }
        } ?: throw PluginException.NotAuthenticated("Sign in to RomM")
        val connection = gate.withLock { borrow(wiring, token) }
        try {
            return block(wiring, connection.ops)
        } finally {
            withContext(NonCancellable) {
                gate.withLock { release(connection) }
            }
        }
    }

    suspend fun <T> call(block: suspend (RommWiring, RommOps) -> T): T =
        translate { session(block) }

    suspend fun disconnect() {
        gate.withLock {
            val current = held ?: return@withLock
            held = null
            retire(current)
        }
    }

    private fun borrow(wiring: RommWiring, token: String): Held {
        val current = held
        if (current != null && !current.retired && current.origin == wiring.origin && current.token == token) {
            current.users += 1
            return current
        }
        if (current != null) {
            held = null
            retire(current)
        }
        val opened = wiring.open(wiring.origin) { token }
        return Held(wiring.origin, token, opened).also {
            it.users = 1
            held = it
        }
    }

    private fun retire(connection: Held) {
        connection.retired = true
        closeIfDrained(connection)
    }

    private fun release(connection: Held) {
        if (connection.users > 0) connection.users -= 1
        closeIfDrained(connection)
    }

    private fun closeIfDrained(connection: Held) {
        if (!connection.retired || connection.users != 0 || connection.closed) return
        connection.closed = true
        connection.ops.close()
    }

    private class Held(val origin: String, val token: String, val ops: RommOps) {
        var users: Int = 0
        var retired: Boolean = false
        var closed: Boolean = false
    }
}
