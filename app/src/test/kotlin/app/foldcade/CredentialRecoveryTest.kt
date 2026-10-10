package app.foldcade

import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.CredentialLookup
import app.foldcade.api.plugin.CredentialStore
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.credentials.MemoryBlobs
import app.foldcade.credentials.Opened
import app.foldcade.credentials.SealedCredentialStore
import app.foldcade.credentials.SecretBox
import app.foldcade.credentials.StoredBlob
import app.foldcade.credentials.openSealed
import app.foldcade.host.PluginHost
import app.foldcade.language.Copy
import app.foldcade.language.DialogKind
import app.foldcade.language.SignedInBackend
import app.foldcade.romm.CLEARTEXT_CREDENTIAL_WARNING
import app.foldcade.romm.CLEARTEXT_TAILNET_NOTE
import app.foldcade.romm.CLEARTEXT_LAN_ONLY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialRecoveryTest {
    @Test
    fun invalidatedOrMissingKeySignsOutAndPrompts() = runBlocking {
        val blobs = MemoryBlobs()
        blobs.put("romm", "access-token", StoredBlob("api-token", byteArrayOf(1, 2, 3)))
        blobs.put("other", "access-token", StoredBlob("api-token", byteArrayOf(4)))
        val missing = SealedCredentialStore(blobs, object : SecretBox {
            override fun seal(plain: ByteArray, aad: ByteArray): ByteArray = plain
            override fun open(sealed: ByteArray, aad: ByteArray): Opened = openSealed(null, sealed, aad)
        })
        val lookup = lookupOrUnreadable(missing, "romm", "access-token")
        assertEquals(CredentialLookup.Unreadable, lookup)
        assertTrue(blobs.pluginIds().isEmpty())
        assertSignedOut(lookup)

        val thrown = lookupOrUnreadable(ThrowingStore(), "romm", "access-token")
        assertEquals(CredentialLookup.Unreadable, thrown)
        assertSignedOut(thrown)
    }

    @Test
    fun httpSetupShowsTheCleartextWarning() {
        val shell = ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.openConnect("https://romm.example")
        assertNull(shell.model.connectWarning)
        shell.editOrigin("http://192.168.1.20:8080")
        assertEquals(CLEARTEXT_CREDENTIAL_WARNING, shell.model.connectWarning)
        shell.editOrigin("  HTTP://romm.local")
        assertEquals(CLEARTEXT_CREDENTIAL_WARNING, shell.model.connectWarning)
        shell.editOrigin("https://romm.example")
        assertNull(shell.model.connectWarning)
        shell.editOrigin("http://romm.example")
        assertEquals(CLEARTEXT_LAN_ONLY, shell.model.connectWarning)
        shell.openConnect("http://10.0.0.5")
        assertEquals(CLEARTEXT_CREDENTIAL_WARNING, shell.model.connectWarning)
        shell.editOrigin("http://100.64.12.34:8080")
        assertEquals(CLEARTEXT_TAILNET_NOTE, shell.model.connectWarning)
        shell.editOrigin("http://box.tail1234.ts.net")
        assertEquals(CLEARTEXT_TAILNET_NOTE, shell.model.connectWarning)
    }

    private fun assertSignedOut(lookup: CredentialLookup) {
        val shell = ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.setSignedIn(listOf(SignedInBackend("romm", "RomM"), SignedInBackend("other", "other")))
        shell.applyRecovery(signedInRecovery(lookup, shell.model.signedIn))
        assertTrue(shell.model.signedIn.isEmpty())
        assertEquals(DialogKind.ReLogin, shell.model.dialog?.kind)
        assertEquals(Copy.signInAgainTitle, shell.model.dialog?.title)
        assertEquals(Copy.signInAgainBody, shell.model.dialog?.body)
    }
}

private class ThrowingStore : CredentialStore {
    override suspend fun put(pluginId: String, key: String, credential: Credential) = error("unused")

    override suspend fun lookup(pluginId: String, key: String): CredentialLookup =
        throw IllegalStateException("keystore key missing")

    override suspend fun forget(pluginId: String, key: String) = Unit

    override suspend fun forgetPlugin(pluginId: String) = Unit

    override suspend fun keys(pluginId: String): Set<String> = emptySet()

    override suspend fun pluginIds(): Set<String> = setOf("romm")
}
