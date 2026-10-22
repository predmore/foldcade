package app.foldcade

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.foldcade.gamestream.ClientIdentity
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.X509KeyManager
import javax.security.auth.x500.X500Principal

/**
 * This device's GameStream identity, kept in Android's keystore. The key never
 * leaves it: pairing signs with it there, and TLS presents its certificate.
 * The keystore writes a certificate for the key, signed by the key itself,
 * which is what a GameStream host expects of a client.
 *
 * Made once, on the first pairing. Clearing Foldcade's data or reinstalling
 * makes a new one, and every computer has to be paired again.
 */
internal class GameStreamIdentity private constructor(
    private val key: PrivateKey,
    override val certificate: X509Certificate,
) : ClientIdentity {
    override fun sign(data: ByteArray): ByteArray = Signature.getInstance("SHA256withRSA").run {
        initSign(key)
        update(data)
        sign()
    }

    /** Tells this key from one made after a reinstall or on another device. */
    val fingerprint: String = certificateFingerprint(certificate)

    override val keyManager: X509KeyManager = object : X509KeyManager {
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
        override fun getCertificateChain(alias: String?) = arrayOf(certificate)
        override fun getPrivateKey(alias: String?) = key
    }

    companion object {
        private const val ALIAS = "foldcade.gamestream"
        private const val STORE = "AndroidKeyStore"

        /** The identity, made on first use. Slow the first time; call off the main thread. */
        fun load(): GameStreamIdentity {
            val store = KeyStore.getInstance(STORE).apply { load(null) }
            if (!store.containsAlias(ALIAS)) make()
            return read(store)
        }

        /** The identity, or null before the first pairing made one. */
        fun existing(): GameStreamIdentity? {
            val store = KeyStore.getInstance(STORE).apply { load(null) }
            return if (store.containsAlias(ALIAS)) read(store) else null
        }

        private fun read(store: KeyStore): GameStreamIdentity {
            val key = store.getKey(ALIAS, null) as PrivateKey
            val certificate = store.getCertificate(ALIAS) as X509Certificate
            return GameStreamIdentity(key, certificate)
        }

        private fun make() {
            val now = System.currentTimeMillis()
            // Pairing signs with SHA-256 and PKCS#1. TLS 1.3 signs with PSS, which Android's TLS
            // does with a keystore key as raw RSA: padded first, then a decrypt with no padding.
            // A key without that purpose fails the handshake with INCOMPATIBLE_PADDING_MODE.
            val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Nothing is encrypted to this key; the raw operation only finishes a signature.
                .setRandomizedEncryptionRequired(false)
                // The name Moonlight's certificate has. Hosts show their own name for a client, not this.
                .setCertificateSubject(X500Principal("CN=NVIDIA GameStream Client"))
                .setCertificateSerialNumber(BigInteger.valueOf(now))
                .setCertificateNotBefore(Date(now - DAY_MS))
                .setCertificateNotAfter(Date(now + 20 * 365 * DAY_MS))
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, STORE).apply { initialize(spec) }.generateKeyPair()
        }

        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

/** SHA-256 of [certificate], in hex. */
internal fun certificateFingerprint(certificate: X509Certificate): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }
