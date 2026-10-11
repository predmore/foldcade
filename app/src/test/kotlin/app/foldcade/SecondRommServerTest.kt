package app.foldcade

import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.CredentialLookup
import app.foldcade.api.plugin.CredentialStore
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.SignedInBackend
import app.foldcade.plugins.romm.RommEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondRommServerTest {
    @Test
    fun addingASecondServerMintsTheInstanceAndStoresTheOrigin() = runBlocking {
        val credentials = MemoryCredentialStore()
        val host = host(credentials)
        val session = SessionStore(MemoryPrefs())
        session.setRommOrigin("https://one.example")
        credentials.put(RommCredentials.PLUGIN_ID, "access-token", Credential.ApiToken("rmm_one"))

        val added = addRommServer(
            host = host,
            session = session,
            credentials = credentials,
            origin = "https://two.example",
            token = "rmm_two",
            defaultOrigin = session.rommOrigin(),
            extras = session.rommInstances(),
            pendingInstanceId = null,
        )

        assertEquals(RommServerAdd.Added("romm#2"), added)
        assertEquals("romm#2", host.library("romm#2")?.instanceId)
        assertEquals("romm", host.library("romm#2")?.id)
        assertEquals(listOf("romm#2" to "https://two.example"), session.rommInstances())
        assertEquals("https://one.example", session.rommOrigin())
        assertEquals("rmm_two", token(credentials, "access-token@romm#2"))
        assertEquals("rmm_one", token(credentials, "access-token"))
        assertEquals(
            listOf(SignedInBackend("romm", "RomM"), SignedInBackend("romm#2", "two.example")),
            rommSignedInBackends(session.rommOrigin(), session.rommInstances()),
        )
    }

    @Test
    fun theSameCanonicalOriginIsRefusedBeforeASecondInstance() = runBlocking {
        val credentials = MemoryCredentialStore()
        val host = host(credentials)
        val session = SessionStore(MemoryPrefs())
        session.setRommOrigin("https://one.example/")

        val againstDefault = addRommServer(
            host, session, credentials,
            origin = "https://one.example/api",
            token = "rmm_two",
            defaultOrigin = session.rommOrigin(),
            extras = session.rommInstances(),
            pendingInstanceId = null,
        )
        assertEquals(RommServerAdd.SameServer, againstDefault)
        assertNull(host.library("romm#2"))
        assertTrue(session.rommInstances().isEmpty())
        assertTrue(credentials.keys(RommCredentials.PLUGIN_ID).isEmpty())

        session.setRommInstance("romm#2", "https://two.example")
        val againstExtra = addRommServer(
            host, session, credentials,
            origin = "https://two.example/api",
            token = "rmm_three",
            defaultOrigin = "https://one.example",
            extras = session.rommInstances(),
            pendingInstanceId = null,
        )
        assertEquals(RommServerAdd.SameServer, againstExtra)
        assertNull(host.library("romm#2"))
        assertTrue(credentials.keys(RommCredentials.PLUGIN_ID).isEmpty())
    }

    @Test
    fun removingTheSecondServerForgetsThatKeyOnly() = runBlocking {
        val delegate = MemoryCredentialStore()
        val credentials = object : CredentialStore by delegate {
            var forgotPlugin = false
            override suspend fun forgetPlugin(pluginId: String) {
                forgotPlugin = true
                delegate.forgetPlugin(pluginId)
            }
        }
        val host = host(credentials)
        val session = SessionStore(MemoryPrefs())
        session.setRommOrigin("https://one.example")
        credentials.put(RommCredentials.PLUGIN_ID, "access-token", Credential.ApiToken("rmm_one"))
        val added = addRommServer(
            host, session, credentials,
            origin = "https://two.example",
            token = "rmm_two",
            defaultOrigin = session.rommOrigin(),
            extras = emptyList(),
            pendingInstanceId = null,
        )
        assertEquals(RommServerAdd.Added("romm#2"), added)

        removeRommServer(credentials, session, "romm#2")

        assertFalse(credentials.forgotPlugin)
        assertEquals("rmm_one", token(credentials, "access-token"))
        assertTrue(credentials.lookup(RommCredentials.PLUGIN_ID, "access-token@romm#2") is CredentialLookup.Absent)
        assertTrue(session.rommInstances().isEmpty())
        assertEquals("https://one.example", session.rommOrigin())
        assertEquals(
            listOf(SignedInBackend("romm", "RomM")),
            mergeSignedIn(
                listOf(SignedInBackend("romm", "RomM"), SignedInBackend("romm#2", "two.example")),
                rommSignedInBackends(session.rommOrigin(), session.rommInstances()),
            ),
        )
    }

    private fun host(credentials: CredentialStore): PluginHost {
        val host = PluginHost(Dispatchers.Unconfined, credentials)
        host.register(RommEntry())
        return host
    }

    private suspend fun token(credentials: CredentialStore, key: String): String {
        val present = credentials.lookup(RommCredentials.PLUGIN_ID, key) as CredentialLookup.Present
        return (present.credential as Credential.ApiToken).value
    }
}
