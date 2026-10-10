package app.foldcade

import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.CredentialLookup
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.api.plugin.secret
import app.foldcade.api.plugin.Platform
import app.foldcade.plugins.romm.RommPlugins
import app.foldcade.romm.RegisteredDevice
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RommTokenSourceTest {
    @Test
    fun sourceReadsTheHostStoreAndNotAnotherPlugin() = runBlocking {
        val store = MemoryCredentialStore()
        val source = rommTokenSource(store)
        assertNull(source.accessToken())
        store.put("other", RommCredentials.ACCESS_TOKEN, Credential.ApiToken("other-secret"))
        store.put(
            RommCredentials.PLUGIN_ID,
            RommCredentials.ACCESS_TOKEN,
            Credential.ApiToken("rmm_live"),
        )
        assertEquals("rmm_live", source.accessToken())
        store.forgetPlugin(RommCredentials.PLUGIN_ID)
        assertNull(source.accessToken())
        val other = store.lookup("other", RommCredentials.ACCESS_TOKEN)
        assertEquals("other-secret", (other as CredentialLookup.Present).credential.secret())
    }

    @Test
    fun tokenReadRefusesTheMainThreadWithoutTouchingTheStore() {
        var lookedUp = false
        val store = object : app.foldcade.api.plugin.CredentialStore {
            override suspend fun put(pluginId: String, key: String, credential: Credential) = Unit
            override suspend fun lookup(pluginId: String, key: String): CredentialLookup {
                lookedUp = true
                return CredentialLookup.Absent
            }
            override suspend fun forget(pluginId: String, key: String) = Unit
            override suspend fun forgetPlugin(pluginId: String) = Unit
            override suspend fun keys(pluginId: String): Set<String> = emptySet()
            override suspend fun pluginIds(): Set<String> = emptySet()
        }
        val failure = runCatching { readRommToken(store, onMainThread = true) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertFalse(lookedUp)
    }

    @Test
    fun passwordIsNotReturnedAsTheRommToken() = runBlocking {
        val store = MemoryCredentialStore()
        store.put(
            RommCredentials.PLUGIN_ID,
            RommCredentials.ACCESS_TOKEN,
            Credential.Password("hunter2"),
        )
        assertNull(rommTokenSource(store).accessToken())
    }

    @Test
    fun wiringSeesLaterWritesToTheSameStore() = runBlocking {
        val store = MemoryCredentialStore()
        val cache = Files.createTempDirectory("romm-cache")
        try {
            publishRommWiring("https://romm.example", store, cache)
            val wiring = RommPlugins.wiring
            check(wiring != null)
            assertNull(wiring.tokenSource.accessToken())
            store.put(
                RommCredentials.PLUGIN_ID,
                RommCredentials.ACCESS_TOKEN,
                Credential.ApiToken("rmm_after"),
            )
            assertEquals("rmm_after", wiring.tokenSource.accessToken())
            publishRommWiring(null, store, cache)
            assertNull(RommPlugins.wiring)
            assertEquals(
                "rmm_after",
                (store.lookup(RommCredentials.PLUGIN_ID, RommCredentials.ACCESS_TOKEN) as CredentialLookup.Present)
                    .credential
                    .secret(),
            )
        } finally {
            RommPlugins.clear()
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun theSameOriginCacheAndPlatformsKeepTheWiring() {
        val store = MemoryCredentialStore()
        val cache = Files.createTempDirectory("romm-cache")
        val nes = platform("nes")
        val snes = platform("snes")
        try {
            publishRommWiring("https://romm.example", store, cache, listOf(nes, snes))
            val installed = RommPlugins.wiring
            check(installed != null)
            installed.rememberedDevice = RegisteredDevice("device-1", "0.1.0")
            publishRommWiring("https://romm.example", store, cache, listOf(nes, snes))
            assertSame(installed, RommPlugins.wiring)
            assertEquals(listOf(nes, snes), installed.platforms)
            publishRommWiring("https://romm.example", store, cache, listOf(snes, nes))
            val reordered = RommPlugins.wiring
            check(reordered != null)
            assertTrue(reordered !== installed)
            assertEquals(listOf(snes, nes), reordered.platforms)
            publishRommWiring("  ", store, cache, listOf(snes, nes))
            assertNull(RommPlugins.wiring)
            publishRommWiring(null, store, cache, emptyList())
            assertNull(RommPlugins.wiring)
        } finally {
            RommPlugins.clear()
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun publishReadsOriginThenPlatformsThenCache() {
        val seen = mutableListOf<String>()
        val request = readRommPublish(
            origin = {
                seen += "origin"
                "https://romm.example"
            },
            platforms = {
                seen += "platforms"
                emptyList()
            },
            cacheRoot = {
                seen += "cache"
                Path.of("cache")
            },
        )
        assertEquals(listOf("origin", "platforms", "cache"), seen)
        assertEquals("https://romm.example", request.origin)
        assertEquals(Path.of("cache"), request.cacheRoot)
    }

    @Test
    fun aLaterPublishSupersedesAnEarlierRead() {
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        var origin = "https://first.example"
        val installed = mutableListOf<String?>()
        val gate = RommPublish(
            read = {
                val seen = origin
                if (seen == "https://first.example") {
                    entered.countDown()
                    check(release.await(2, TimeUnit.SECONDS))
                }
                RommPublishRequest(seen, emptyList(), Path.of("cache"))
            },
            apply = { installed += it.origin },
        )
        val first = Thread { gate.publish() }
        first.start()
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        origin = "https://second.example"
        gate.publish()
        release.countDown()
        first.join(2_000)
        assertEquals(listOf("https://second.example"), installed)
    }
}

private fun platform(id: String) = object : Platform {
    override val id = id
    override val displayName = id
    override val extensions = emptySet<String>()
    override val aliases = emptySet<String>()
}
