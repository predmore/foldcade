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
            override fun seal(plain: ByteArray, aad: ByteArray): ByteArray = error("unused")
            override fun open(sealed: ByteArray, aad: ByteArray) = Opened.KeyGone
        })
        assertEquals(CredentialLookup.Unreadable, gone.lookup("romm", "access-token"))
        assertTrue(blobs.pluginIds().isEmpty())
    }

    @Test
    fun indexRoundTripKeepsPluginIdsApart() {
        val encoded = encodeIndex(
            listOf(
                IndexRow("romm.metadata", "access-token", "api-token"),
                IndexRow("other/plugin", "line\nkey", "password"),
            ),
        )
        assertFalse(encoded.contains("rmm_supersecret"))
        assertEquals(
            listOf(
                IndexRow("romm.metadata", "access-token", "api-token"),
                IndexRow("other/plugin", "line\nkey", "password"),
            ),
            decodeIndex(encoded),
        )
    }

    @Test
    fun dottedIdsDoNotCollideAndSwappedAadFails() = runBlocking {
        assertFalse(blobPreferenceName("a.b", "c") == blobPreferenceName("a", "b.c"))
        assertFalse(
            blobPreferenceName("romm.metadata", "access-token") ==
                blobPreferenceName("romm", "metadata"),
        )
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256)
        val box = AesGcmSecretBox(generator.generateKey())
        val sealed = box.seal("rmm_secret".toByteArray(), credentialAad("a.b", "c", "api-token"))
        assertTrue(box.open(sealed, credentialAad("a", "b.c", "api-token")) is Opened.BadBlob)
        assertTrue(box.open(sealed, credentialAad("a.b", "c", "password")) is Opened.BadBlob)
        val opened = box.open(sealed, credentialAad("a.b", "c", "api-token")) as Opened.Plain
        assertEquals("rmm_secret", opened.bytes.toString(Charsets.UTF_8))
        assertFalse(String(sealed).contains("rmm_secret"))
    }

    @Test
    fun aCorruptBase64BlobIsBadAndForgetsOnlyThatEntry() = runBlocking {
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256)
        val box = AesGcmSecretBox(generator.generateKey())
        val corrupt = credentialCiphertext("***")
        assertEquals(0, corrupt.size)
        assertTrue(box.open(corrupt, credentialAad("romm", "access-token", "api-token")) is Opened.BadBlob)
        val blobs = MemoryBlobs()
        blobs.put("romm", "access-token", StoredBlob("api-token", corrupt))
        val kept = box.seal("rmm_ok".toByteArray(), credentialAad("other", "access-token", "api-token"))
        blobs.put("other", "access-token", StoredBlob("api-token", kept))
        val store = SealedCredentialStore(blobs, box)
        assertEquals(CredentialLookup.Unreadable, store.lookup("romm", "access-token"))
        assertEquals(setOf("other"), blobs.pluginIds())
        val left = store.lookup("other", "access-token") as CredentialLookup.Present
        assertEquals("rmm_ok", (left.credential as Credential.ApiToken).value)
    }

    @Test
    fun aBadBlobForgetsOnlyThatEntry() = runBlocking {
        val blobs = MemoryBlobs()
        blobs.put("romm", "access-token", StoredBlob("api-token", byteArrayOf(1)))
        blobs.put("other", "access-token", StoredBlob("api-token", byteArrayOf(2)))
        val store = SealedCredentialStore(blobs, object : SecretBox {
            override fun seal(plain: ByteArray, aad: ByteArray): ByteArray = plain
            override fun open(sealed: ByteArray, aad: ByteArray): Opened = Opened.BadBlob
        })
        assertEquals(CredentialLookup.Unreadable, store.lookup("romm", "access-token"))
        assertEquals(setOf("other"), blobs.pluginIds())
    }

    @Test
    fun aTransientKeystoreErrorKeepsTheCiphertext() = runBlocking {
        val blobs = MemoryBlobs()
        blobs.put("romm", "access-token", StoredBlob("api-token", byteArrayOf(9)))
        blobs.put("other", "access-token", StoredBlob("api-token", byteArrayOf(8)))
        val store = SealedCredentialStore(blobs, object : SecretBox {
            override fun seal(plain: ByteArray, aad: ByteArray): ByteArray = plain
            override fun open(sealed: ByteArray, aad: ByteArray): Opened = Opened.Unavailable
        })
        assertEquals(CredentialLookup.Unreadable, store.lookup("romm", "access-token"))
        assertEquals(setOf("romm", "other"), blobs.pluginIds())
    }
}
