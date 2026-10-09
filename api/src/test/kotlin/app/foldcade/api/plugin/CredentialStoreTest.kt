package app.foldcade.api.plugin

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialStoreTest {
    @Test
    fun toStringMasksTokensAndPasswords() {
        val token = Credential.ApiToken("rmm_supersecret")
        val password = Credential.Password("p@ssw0rd")
        assertFalse(token.toString().contains("rmm_supersecret"))
        assertFalse("$token".contains("rmm_supersecret"))
        assertTrue(token.toString().contains("***"))
        assertEquals("rmm_supersecret", token.value)
        assertFalse(password.toString().contains("p@ssw0rd"))
        assertFalse("$password".contains("p@ssw0rd"))
        assertTrue(password.toString().contains("***"))
    }

    @Test
    fun memoryStoreKeepsPluginsApart() = runBlocking {
        val store = MemoryCredentialStore()
        store.put("romm", "access-token", Credential.ApiToken("rmm_a"))
        store.put("other", "access-token", Credential.ApiToken("rmm_b"))
        val romm = store.lookup("romm", "access-token") as CredentialLookup.Present
        val other = store.lookup("other", "access-token") as CredentialLookup.Present
        assertEquals("rmm_a", (romm.credential as Credential.ApiToken).value)
        assertEquals("rmm_b", (other.credential as Credential.ApiToken).value)
        store.forgetPlugin("romm")
        assertEquals(CredentialLookup.Absent, store.lookup("romm", "access-token"))
        assertTrue(store.lookup("other", "access-token") is CredentialLookup.Present)
        assertEquals(setOf("other"), store.pluginIds())
        val access = BoundCredentialAccess(setOf("other"), store)
        try {
            access.open("romm")
            error("opened another plugin")
        } catch (denied: CredentialDenied) {
            assertEquals("romm", denied.pluginId)
        }
        assertEquals(setOf("access-token"), access.open("other").keys())
    }
}
