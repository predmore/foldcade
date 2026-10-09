package app.foldcade

import android.content.SharedPreferences
import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.CredentialLookup
import app.foldcade.api.plugin.CredentialStore
import app.foldcade.credentials.MemoryBlobs
import app.foldcade.credentials.Opened
import app.foldcade.credentials.SealedCredentialStore
import app.foldcade.credentials.SecretBox
import app.foldcade.credentials.StoredBlob
import app.foldcade.credentials.openSealed
import app.foldcade.language.Copy
import app.foldcade.language.DialogKind
import app.foldcade.language.SignedInBackend
import app.foldcade.romm.CLEARTEXT_CREDENTIAL_WARNING
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
            override fun seal(plain: ByteArray): ByteArray = plain
            override fun open(sealed: ByteArray): Opened = openSealed(null, sealed)
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
        val shell = ShellController(SessionStore(MemoryPrefs()))
        shell.openConnect("https://romm.example")
        assertNull(shell.model.connectWarning)
        shell.editOrigin("http://192.168.1.20:8080")
        assertEquals(CLEARTEXT_CREDENTIAL_WARNING, shell.model.connectWarning)
        shell.editOrigin("  HTTP://romm.local")
        assertEquals(CLEARTEXT_CREDENTIAL_WARNING, shell.model.connectWarning)
        shell.editOrigin("https://romm.example")
        assertNull(shell.model.connectWarning)
        shell.openConnect("http://10.0.0.5")
        assertEquals(CLEARTEXT_CREDENTIAL_WARNING, shell.model.connectWarning)
    }

    private fun assertSignedOut(lookup: CredentialLookup) {
        val shell = ShellController(SessionStore(MemoryPrefs()))
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

private class MemoryPrefs : SharedPreferences {
    override fun getAll(): MutableMap<String, Any?> = mutableMapOf()

    override fun getString(key: String?, defValue: String?): String? = defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues

    override fun getInt(key: String?, defValue: Int): Int = defValue

    override fun getLong(key: String?, defValue: Long): Long = defValue

    override fun getFloat(key: String?, defValue: Float): Float = defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue

    override fun contains(key: String?): Boolean = false

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?) = this
        override fun putStringSet(key: String?, values: MutableSet<String>?) = this
        override fun putInt(key: String?, value: Int) = this
        override fun putLong(key: String?, value: Long) = this
        override fun putFloat(key: String?, value: Float) = this
        override fun putBoolean(key: String?, value: Boolean) = this
        override fun remove(key: String?) = this
        override fun clear() = this
        override fun commit(): Boolean = true
        override fun apply() = Unit
    }

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit
}
