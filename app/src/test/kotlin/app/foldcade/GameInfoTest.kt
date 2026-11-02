package app.foldcade

import app.foldcade.api.plugin.*
import app.foldcade.host.PluginHost
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GameInfoTest {
    private val game = Game("local-folder", "content://game", "snes", Availability.LocalOnly, "Game")
    private class Provider(override val id: String) : MetadataProvider {
        override val displayName = id
        var facts: GameDetails? = null
        var calls = 0
        var read: suspend () -> GameDetails? = { facts }
        override fun cached(game: Game): GameMeta? = null
        override suspend fun fetch(game: Game): GameMeta? = null
        override fun cachedDetails(game: Game) = facts
        override suspend fun details(game: Game): GameDetails? { calls++; return read() }
    }
    private fun info(vararg providers: MetadataProvider): GameInfo {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        host.register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val metadataProviders = providers.toList()
        })
        return GameInfo(host)
    }

    @Test fun firstNonEmptyProviderWinsAndProviderChangesAreImmediatelyVisible() = runBlocking {
        val first = Provider("one")
        val second = Provider("two")
        val info = info(first, second)
        assertNull(info.fetch(game))
        second.facts = GameDetails(" Now available. ")
        assertEquals("Now available.", info.cached(game)?.summary)
        assertEquals("Now available.", info.fetch(game)?.summary)
        first.facts = GameDetails("First provider")
        info.forget(game.backendId)
        assertEquals(first.facts, info.fetch(game))
        assertNull(info.cached(null))
    }

    @Test fun anOlderInFlightMissIsNeverHeldOverANewIndex() = runBlocking {
        val provider = Provider("local.media")
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        provider.read = { started.complete(Unit); release.await(); null }
        val info = info(provider)
        val fetched = async { info.fetch(game) }
        started.await()
        info.forget(game.backendId)
        provider.facts = GameDetails("New index")
        provider.read = { provider.facts }
        release.complete(Unit)
        assertNull(fetched.await())
        assertEquals(provider.facts, info.cached(game))
    }

    @Test fun aProviderFailureFallsThroughButCancellationPropagates() = runBlocking {
        val failed = Provider("broken")
        failed.read = { throw PluginException.Unavailable("Unavailable") }
        val available = Provider("fine").also { it.facts = GameDetails(releaseYear = 1995) }
        assertEquals(available.facts, info(failed, available).fetch(game))
        val retry = info(failed)
        assertNull(retry.fetch(game))
        failed.facts = GameDetails(summary = "Signed in")
        failed.read = { failed.facts }
        assertEquals(failed.facts, retry.fetch(game))
        failed.read = { throw kotlin.coroutines.cancellation.CancellationException("Cancelled") }
        val result = runCatching { info(failed, available).fetch(game) }
        assertTrue(result.exceptionOrNull() is kotlin.coroutines.cancellation.CancellationException)
    }
}
