package app.foldcade.gamestream

import java.net.Socket
import java.security.KeyFactory
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.X509KeyManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTest {
    private val client = TestIdentity("client")
    private val pairing = GameStreamPairing(client)

    @Test
    fun theSameCodeOnBothSidesPairs() = runBlocking {
        val host = SunshineHost(typed = "4821")
        val outcome = pairing.pair(host, "4821")
        assertEquals(PairOutcome.Paired(host.certificate), outcome)
        assertTrue(host.authorized)
        // The host got this device's certificate as PEM, hex encoded.
        assertEquals(client.certificate, host.clientCert)
    }

    @Test
    fun anotherCodeOnTheHostIsAWrongPin() = runBlocking {
        val host = SunshineHost(typed = "1111")
        assertEquals(PairOutcome.WrongPin, pairing.pair(host, "4821"))
        // Nothing past the check is sent, so the host never keeps this device.
        assertEquals(false, host.authorized)
        assertEquals(listOf("getservercert", "clientchallenge", "serverchallengeresp"), host.steps)
    }

    @Test
    fun aSecretTheHostsCertificateDidNotSignIsRefused() = runBlocking {
        val host = SunshineHost(typed = "4821", signingKey = TestIdentity("client").key)
        assertEquals(PairOutcome.Refused, pairing.pair(host, "4821"))
        assertEquals(false, host.authorized)
    }

    @Test
    fun aHostPairingAnotherDeviceSendsNoCertificate() = runBlocking {
        val host = SunshineHost(typed = "4821", busy = true)
        assertEquals(PairOutcome.Refused, pairing.pair(host, "4821"))
    }

    @Test
    fun aHostThatStopsAnsweringIsUnreachable() = runBlocking {
        val host = SunshineHost(typed = "4821", silentAt = "clientchallenge")
        assertEquals(PairOutcome.Unreachable, pairing.pair(host, "4821"))
        val noTls = SunshineHost(typed = "4821", tlsAnswers = false)
        assertEquals(PairOutcome.Unreachable, pairing.pair(noTls, "4821"))
    }

    @Test
    fun codesAreFourDigits() {
        repeat(50) { assertTrue(Regex("[0-9]{4}").matches(pairing.newPin())) }
    }

    @Test
    fun pemRoundTrips() {
        assertEquals(client.certificate, certificate(pem(client.certificate)))
        assertEquals(client.certificate, certificate(unhex(hex(pem(client.certificate)))!!))
    }

    /**
     * Sunshine's side of pairing, step for step from `nvhttp.cpp`:
     * `getservercert`, `clientchallenge`, `serverchallengeresp`, `clientpairingsecret`.
     */
    private class SunshineHost(
        private val typed: String,
        private val signingKey: PrivateKey = TestIdentity("server").key,
        private val busy: Boolean = false,
        private val silentAt: String? = null,
        private val tlsAnswers: Boolean = true,
    ) : PairLink {
        val certificate: X509Certificate = TestIdentity("server").certificate
        val steps = mutableListOf<String>()
        var clientCert: X509Certificate? = null
        var authorized = false
        private lateinit var key: ByteArray
        private lateinit var serverSecret: ByteArray
        private lateinit var serverChallenge: ByteArray
        private lateinit var clientHash: ByteArray
        private val random = SecureRandom()

        override suspend fun pair(query: List<Pair<String, String>>, waitForUser: Boolean): String? {
            val args = query.toMap()
            val step = args["phrase"] ?: query.first().first
            steps += step
            if (step == silentAt) return null
            return when (step) {
                "getservercert" -> {
                    assertTrue(waitForUser)
                    if (busy) return ok("<paired>1</paired>")
                    clientCert = certificate(unhex(args.getValue("clientcert"))!!)
                    key = sha256(unhex(args.getValue("salt"))!! + typed.toByteArray()).copyOf(16)
                    ok("<paired>1</paired><plaincert>${hex(pem(certificate))}</plaincert>")
                }
                "clientchallenge" -> {
                    val decrypted = aes(key, unhex(args.getValue(step))!!, encrypt = false)
                    serverSecret = ByteArray(16).also(random::nextBytes)
                    serverChallenge = ByteArray(16).also(random::nextBytes)
                    val hash = sha256(decrypted + certificate.signature + serverSecret)
                    ok("<paired>1</paired><challengeresponse>${hex(aes(key, hash + serverChallenge, encrypt = true))}</challengeresponse>")
                }
                "serverchallengeresp" -> {
                    clientHash = aes(key, unhex(args.getValue(step))!!, encrypt = false)
                    val sign = Signature.getInstance("SHA256withRSA").run {
                        initSign(signingKey)
                        update(serverSecret)
                        sign()
                    }
                    ok("<pairingsecret>${hex(serverSecret + sign)}</pairingsecret><paired>1</paired>")
                }
                "clientpairingsecret" -> {
                    val sent = unhex(args.getValue(step))!!
                    val secret = sent.copyOfRange(0, 16)
                    val sign = sent.copyOfRange(16, sent.size)
                    val cert = clientCert!!
                    val sameHash = sha256(serverChallenge + cert.signature + secret).contentEquals(clientHash)
                    authorized = sameHash && verifies(cert, secret, sign)
                    ok("<paired>${if (authorized) 1 else 0}</paired>")
                }
                else -> error("unexpected $step")
            }
        }

        override suspend fun pairChallenge(server: X509Certificate): String? {
            assertEquals(certificate, server)
            steps += "pairchallenge"
            return if (tlsAnswers) ok("<paired>${if (authorized) 1 else 0}</paired>") else null
        }

        private fun ok(body: String) = """<?xml version="1.0" encoding="utf-8"?><root status_code="200">$body</root>"""
    }
}

/** A test-only key and certificate from `src/test/resources/pairing`. */
internal class TestIdentity(name: String) : ClientIdentity {
    override val certificate: X509Certificate = certificate(resource("$name-cert.pem"))!!
    val key: PrivateKey = KeyFactory.getInstance("RSA").generatePrivate(
        PKCS8EncodedKeySpec(
            Base64.getMimeDecoder().decode(
                String(resource("$name-key.pk8.pem")).replace(Regex("-----[A-Z ]+-----"), ""),
            ),
        ),
    )

    override fun sign(data: ByteArray): ByteArray = Signature.getInstance("SHA256withRSA").run {
        initSign(key)
        update(data)
        sign()
    }

    override val keyManager: X509KeyManager = object : X509KeyManager {
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
        override fun getCertificateChain(alias: String?) = arrayOf(certificate)
        override fun getPrivateKey(alias: String?) = key
    }

    private fun resource(file: String): ByteArray =
        PairingTest::class.java.getResourceAsStream("/pairing/$file")!!.use { it.readBytes() }

    private companion object {
        const val ALIAS = "gamestream"
    }
}
