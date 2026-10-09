package app.foldcade.plugins.romm

import app.foldcade.api.plugin.PluginException
import app.foldcade.romm.PlatformSummary
import app.foldcade.romm.RomSummary
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    suspend fun <T> session(block: suspend (RommWiring, RommOps) -> T): T {
        val wiring = wiring()
        val token = runInterruptible(Dispatchers.IO) {
            wiring.tokenSource.accessToken()?.trim()?.removePrefix("Bearer ")?.trim()?.ifEmpty { null }
        } ?: throw PluginException.NotAuthenticated("Sign in to RomM")
        val ops = gate.withLock {
            val current = held
            if (current != null && current.origin == wiring.origin && current.token == token) {
                current.ops
            } else {
                current?.ops?.close()
                val opened = try {
                    wiring.open(wiring.origin) { token }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                }
                held = Held(wiring.origin, token, opened)
                opened
            }
        }
        return block(wiring, ops)
    }

    suspend fun <T> call(block: suspend (RommWiring, RommOps) -> T): T =
        translate { session(block) }

    suspend fun disconnect() {
        gate.withLock {
            held?.ops?.close()
            held = null
        }
    }

    private class Held(val origin: String, val token: String, val ops: RommOps)
}
