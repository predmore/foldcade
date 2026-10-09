package app.foldcade.plugins.romm

import app.foldcade.api.plugin.PluginException
import app.foldcade.romm.PlatformSummary
import app.foldcade.romm.RomSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class RommCatalog {
    private val roms = HashMap<String, RomSummary>()
    private val platforms = ArrayList<PlatformSummary>()

    fun rememberPlatforms(items: List<PlatformSummary>) = synchronized(this) {
        items.forEach { incoming ->
            val index = platforms.indexOfFirst { it.id == incoming.id }
            if (index >= 0) platforms[index] = incoming else platforms.add(incoming)
        }
    }

    fun rememberRoms(items: List<RomSummary>) = synchronized(this) {
        items.forEach { roms[it.id.toString()] = it }
    }

    fun rom(remoteKey: String): RomSummary? = synchronized(this) { roms[remoteKey] }

    fun platforms(): List<PlatformSummary> = synchronized(this) { platforms.toList() }
}

internal class RommGate(
    private val source: () -> RommWiring?,
) {
    val catalog = RommCatalog()
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
