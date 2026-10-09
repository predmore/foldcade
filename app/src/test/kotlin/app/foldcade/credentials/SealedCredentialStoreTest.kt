package app.foldcade.credentials

import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.CredentialLookup
import javax.crypto.KeyGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SealedCredentialStoreTest {
    @Test
    fun roundTripHidesTheTokenAndAGoneKeyAsksForSignIn() = runBlocking {
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256)
        val box = AesGcmSecretBox(generator.generateKey())
        val blobs = MemoryBlobs()
        val store = SealedCredentialStore(blobs, box)
        store.put("romm", "access-token", Credential.ApiToken("rmm_supersecret"))
        val found = store.lookup("romm", "access-token") as CredentialLookup.Present
        val token = found.credential as Credential.ApiToken
        assertEquals("rmm_supersecret", token.value)
        assertFalse(token.toString().contains("rmm_supersecret"))
        val sealed = blobs.get("romm", "access-token")!!
        assertFalse(String(sealed.bytes).contains("rmm_supersecret"))

        val gone = SealedCredentialStore(blobs, object : SecretBox {
            override fun seal(plain: ByteArray): ByteArray = error("unused")
            override fun open(sealed: ByteArray) = Opened.KeyGone
        })
        assertEquals(CredentialLookup.Unreadable, gone.lookup("romm", "access-token"))
        assertTrue(blobs.pluginIds().isEmpty())
    }

    @Test
    fun indexRoundTripKeepsPluginIdsApart() {
        val encoded = encodeIndex(
            listOf(
                IndexRow("romm", "access-token", "api-token"),
                IndexRow("other/plugin", "line\nkey", "password"),
            ),
        )
        assertFalse(encoded.contains("rmm_supersecret"))
        assertEquals(
            listOf(
                IndexRow("romm", "access-token", "api-token"),
                IndexRow("other/plugin", "line\nkey", "password"),
            ),
            decodeIndex(encoded),
        )
    }

    @Test
    fun aBadBlobForgetsOnlyThatEntry() = runBlocking {
        val blobs = MemoryBlobs()
        blobs.put("romm", "access-token", StoredBlob("api-token", byteArrayOf(1)))
        blobs.put("other", "access-token", StoredBlob("api-token", byteArrayOf(2)))
        val store = SealedCredentialStore(blobs, object : SecretBox {
            override fun seal(plain: ByteArray): ByteArray = plain
            override fun open(sealed: ByteArray): Opened = Opened.BadBlob
        })
        assertEquals(CredentialLookup.Unreadable, store.lookup("romm", "access-token"))
        assertEquals(setOf("other"), blobs.pluginIds())
    }
}
