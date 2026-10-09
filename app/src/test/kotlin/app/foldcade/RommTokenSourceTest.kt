package app.foldcade

import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.CredentialLookup
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.api.plugin.RommCredentials
import app.foldcade.api.plugin.secret
import app.foldcade.plugins.romm.RommPlugins
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
