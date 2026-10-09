package app.foldcade.credentials

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import app.foldcade.api.plugin.CredentialUnreadable
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal sealed interface Opened {
    class Plain(val bytes: ByteArray) : Opened
    data object KeyGone : Opened
    data object BadBlob : Opened
}

/**
 * Encrypts and decrypts credential bytes. [KeyGone] means the keystore key
 * was invalidated. The caller drops the ciphertext and asks for sign-in.
 */
internal interface SecretBox {
    fun seal(plain: ByteArray): ByteArray

    fun open(sealed: ByteArray): Opened
}

internal class AesGcmSecretBox(
    private val key: SecretKey,
) : SecretBox {
    override fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val body = cipher.doFinal(plain)
        val out = ByteArray(1 + iv.size + body.size)
        out[0] = iv.size.toByte()
        System.arraycopy(iv, 0, out, 1, iv.size)
        System.arraycopy(body, 0, out, 1 + iv.size, body.size)
        return out
    }

    override fun open(sealed: ByteArray): Opened {
        if (sealed.isEmpty()) return Opened.BadBlob
        val ivLength = sealed[0].toInt() and 0xff
        if (ivLength == 0 || ivLength >= sealed.size) return Opened.BadBlob
        val iv = sealed.copyOfRange(1, 1 + ivLength)
        val body = sealed.copyOfRange(1 + ivLength, sealed.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            Opened.Plain(cipher.doFinal(body))
        } catch (_: AEADBadTagException) {
            Opened.BadBlob
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

/**
 * AES-GCM key in the Android Keystore. The key is not exportable.
 * StrongBox is requested and, when the device has none, the keystore's
 * default (TEE or software) is used.
 *
 * The key is not bound to user authentication. Binding it would refuse a
 * device with no lock screen and would prompt during library sync. If the
 * keystore still drops the key, [open] returns [Opened.KeyGone] instead of
 * throwing, and the shell asks for sign-in.
 */
internal class AndroidKeystoreBox : SecretBox {
    override fun seal(plain: ByteArray): ByteArray = try {
        AesGcmSecretBox(key()).seal(plain)
    } catch (_: android.security.keystore.KeyPermanentlyInvalidatedException) {
        deleteKey()
        AesGcmSecretBox(key()).seal(plain)
    } catch (_: UnrecoverableKeyException) {
        deleteKey()
        AesGcmSecretBox(key()).seal(plain)
    } catch (e: GeneralSecurityException) {
        throw CredentialUnreadable("Sign in again", e)
    } catch (e: ProviderException) {
        throw CredentialUnreadable("Sign in again", e)
    }

    override fun open(sealed: ByteArray): Opened = try {
        AesGcmSecretBox(key()).open(sealed)
    } catch (_: android.security.keystore.KeyPermanentlyInvalidatedException) {
        deleteKey()
        Opened.KeyGone
    } catch (_: UnrecoverableKeyException) {
        deleteKey()
        Opened.KeyGone
    } catch (_: GeneralSecurityException) {
        Opened.KeyGone
    } catch (_: ProviderException) {
        Opened.KeyGone
    }

    private fun key(): SecretKey {
        synchronized(this) {
            val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = store.getKey(ALIAS, null) as? SecretKey
            if (existing != null) return existing
            return generate()
        }
    }

    private fun deleteKey() {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        store.deleteEntry(ALIAS)
    }

    private fun generate(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        try {
            generator.init(spec(strongBox = true))
            return generator.generateKey()
        } catch (_: StrongBoxUnavailableException) {
            generator.init(spec(strongBox = false))
            return generator.generateKey()
        } catch (_: ProviderException) {
            generator.init(spec(strongBox = false))
            return generator.generateKey()
        }
    }

    private fun spec(strongBox: Boolean): KeyGenParameterSpec =
        KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setIsStrongBoxBacked(strongBox)
            .build()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "foldcade.credentials"
    }
}
