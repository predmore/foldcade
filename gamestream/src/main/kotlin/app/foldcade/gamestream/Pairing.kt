package app.foldcade.gamestream

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.X509KeyManager

/**
 * This device's GameStream identity: a certificate a host keeps once paired,
 * and the key that signs for it. The host knows this device by the certificate.
 */
interface ClientIdentity {
    val certificate: X509Certificate

    /** SHA-256 with RSA over [data], by the key [certificate] names. */
    fun sign(data: ByteArray): ByteArray

    /** Presents [certificate] when a paired host asks for it over https. */
    val keyManager: X509KeyManager
}

/** How a pairing ended. */
sealed interface PairOutcome {
    /** The host now knows this device. Only [serverCertificate] is trusted for its https. */
    data class Paired(val serverCertificate: X509Certificate) : PairOutcome

    /** The code entered on the host was not this one. */
    data object WrongPin : PairOutcome

    /** The host is pairing another device, or refused this one. */
    data object Refused : PairOutcome

    /** The host stopped answering. */
    data object Unreachable : PairOutcome
}

/** The requests a pairing makes, so the steps can be checked without a network. */
interface PairLink {
    /**
     * `/pair` over plain http with [query], or null when the host does not answer.
     * [waitForUser] is the first request, which the host answers only once
     * someone enters the code on it.
     */
    suspend fun pair(query: List<Pair<String, String>>, waitForUser: Boolean): String?

    /** `/pair?phrase=pairchallenge` over https, trusting only [server]. Null when it does not answer. */
    suspend fun pairChallenge(server: X509Certificate): String?
}

/**
 * GameStream pairing, as Moonlight does it and Sunshine and Apollo accept it.
 *
 * Read from moonlight-android `PairingManager` and Sunshine `nvhttp.cpp`
 * (`getservercert` through `clientpairingsecret`). This device shows a
 * four-digit code; the user types it into the host's web page. Both sides
 * derive an AES key from a salt and the code, prove they hold it, and sign a
 * secret with their certificate's key. A host that answers with another code,
 * or a signature that is not its certificate's, is refused.
 *
 * Hosts of generation 7 and later hash with SHA-256. Sunshine and Apollo
 * report 7, and older GameStream hosts are gone, so SHA-1 is not offered.
 */
class GameStreamPairing(
    private val identity: ClientIdentity,
    private val random: SecureRandom = SecureRandom(),
) {
    /** A fresh four-digit code, the only length Sunshine accepts. */
    fun newPin(): String = buildString { repeat(4) { append(random.nextInt(10)) } }

    suspend fun pair(link: PairLink, pin: String): PairOutcome {
        require(PIN.matches(pin)) { "pin" }
        val salt = bytes(16)
        val key = sha256(salt + pin.toByteArray(Charsets.UTF_8)).copyOf(16)

        val certReply = link.pair(
            listOf("phrase" to "getservercert", "salt" to hex(salt), "clientcert" to hex(pem(identity.certificate))),
            waitForUser = true,
        ) ?: return PairOutcome.Unreachable
        val certXml = paired(certReply) ?: return PairOutcome.Refused
        // A host pairing another device sends no certificate.
        val serverCert = certXml.first("plaincert")?.let(::unhex)?.let(::certificate) ?: return PairOutcome.Refused

        val challenge = bytes(16)
        val challengeReply = link.pair(listOf("clientchallenge" to hex(aes(key, challenge, encrypt = true))), waitForUser = false)
            ?: return PairOutcome.Unreachable
        val sealed = paired(challengeReply)?.first("challengeresponse")?.let(::unhex)
            ?.takeIf { it.size == HASH_BYTES + 16 } ?: return PairOutcome.Refused
        val opened = aes(key, sealed, encrypt = false)
        val serverResponse = opened.copyOfRange(0, HASH_BYTES)
        val serverChallenge = opened.copyOfRange(HASH_BYTES, HASH_BYTES + 16)

        val clientSecret = bytes(16)
        val answer = sha256(serverChallenge + identity.certificate.signature + clientSecret)
        val secretReply = link.pair(listOf("serverchallengeresp" to hex(aes(key, answer, encrypt = true))), waitForUser = false)
            ?: return PairOutcome.Unreachable
        val pairingSecret = paired(secretReply)?.first("pairingsecret")?.let(::unhex)
            ?.takeIf { it.size > 16 } ?: return PairOutcome.Refused
        val serverSecret = pairingSecret.copyOfRange(0, 16)
        val serverSignature = pairingSecret.copyOfRange(16, pairingSecret.size)
        // Someone between here and the host could answer, but cannot sign as the host.
        if (!verifies(serverCert, serverSecret, serverSignature)) return PairOutcome.Refused
        // The host's answer to this device's challenge holds only with the same code.
        val expected = sha256(challenge + serverCert.signature + serverSecret)
        if (!MessageDigest.isEqual(expected, serverResponse)) return PairOutcome.WrongPin

        val signed = clientSecret + identity.sign(clientSecret)
        val finalReply = link.pair(listOf("clientpairingsecret" to hex(signed)), waitForUser = false)
            ?: return PairOutcome.Unreachable
        paired(finalReply) ?: return PairOutcome.Refused
        // Sunshine has kept the certificate already. This proves https works both ways,
        // which every later request needs.
        val challengeOverTls = link.pairChallenge(serverCert) ?: return PairOutcome.Unreachable
        paired(challengeOverTls) ?: return PairOutcome.Refused
        return PairOutcome.Paired(serverCert)
    }

    private fun paired(xml: String): FlatXml? =
        readFlatXml(xml)?.takeIf { it.status == 200 && it.first("paired")?.trim() == "1" }

    private fun bytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    private companion object {
        const val HASH_BYTES = 32
        val PIN = Regex("[0-9]{4}")
    }
}

/** [certificate] as PEM, the form a host takes in `clientcert`. */
fun pem(certificate: X509Certificate): ByteArray {
    val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(certificate.encoded)
    return "-----BEGIN CERTIFICATE-----\n$body\n-----END CERTIFICATE-----\n".toByteArray(Charsets.US_ASCII)
}

/** A PEM or DER certificate, or null when [bytes] is not one. */
fun certificate(bytes: ByteArray): X509Certificate? = try {
    CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(bytes)) as? X509Certificate
} catch (_: CertificateException) {
    null
}

internal fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

/** AES-128 in ECB mode over whole blocks, as GameStream pairing uses it. */
internal fun aes(key: ByteArray, data: ByteArray, encrypt: Boolean): ByteArray {
    require(data.size % 16 == 0) { "whole blocks" }
    val cipher = Cipher.getInstance("AES/ECB/NoPadding")
    cipher.init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
    return cipher.doFinal(data)
}

internal fun verifies(certificate: X509Certificate, data: ByteArray, signature: ByteArray): Boolean = try {
    Signature.getInstance("SHA256withRSA").run {
        initVerify(certificate.publicKey)
        update(data)
        verify(signature)
    }
} catch (_: java.security.GeneralSecurityException) {
    false
}

internal fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
    bytes.forEach { byte ->
        append(HEX[(byte.toInt() shr 4) and 0xf])
        append(HEX[byte.toInt() and 0xf])
    }
}

internal fun unhex(text: String): ByteArray? {
    val clean = text.trim()
    if (clean.length % 2 != 0) return null
    val out = ByteArray(clean.length / 2)
    for (index in out.indices) {
        val high = Character.digit(clean[index * 2], 16)
        val low = Character.digit(clean[index * 2 + 1], 16)
        if (high < 0 || low < 0) return null
        out[index] = ((high shl 4) or low).toByte()
    }
    return out
}

private const val HEX = "0123456789ABCDEF"
