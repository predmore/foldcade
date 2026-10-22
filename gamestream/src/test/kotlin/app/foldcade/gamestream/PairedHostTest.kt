package app.foldcade.gamestream

import app.foldcade.net.LanHttp
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsExchange
import com.sun.net.httpserver.HttpsParameters
import com.sun.net.httpserver.HttpsServer
import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A paired host over real https: this device's certificate goes out, and only the host's comes back trusted. */
class PairedHostTest {
    private val device = TestIdentity("client")
    private val hostIdentity = TestIdentity("server")
    private var seenClient: X509Certificate? = null
    private val server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        httpsConfigurator = object : HttpsConfigurator(serverContext()) {
            override fun configure(params: HttpsParameters) {
                params.setSSLParameters(sslContext.defaultSSLParameters.apply { needClientAuth = true })
            }
        }
        createContext("/applist") { exchange ->
            seenClient = (exchange as HttpsExchange).sslSession.peerCertificates.first() as X509Certificate
            reply(exchange, APP_LIST.toByteArray())
        }
        createContext("/appasset") { exchange ->
            val id = exchange.requestURI.query.split('&').first { it.startsWith("appid=") }.substringAfter('=')
            if (id == "1") reply(exchange, PNG) else reply(exchange, """<?xml version="1.0"?><root status_code="404"/>""".toByteArray())
        }
        start()
    }
    private val client = GameStreamClient(LanHttp(), "0123456789abcdef", device)

    @After
    fun stop() = server.stop(0)

    @Test
    fun listsTheHostsAppsAsThisDevice() = runBlocking {
        val apps = client.apps(host(hostIdentity.certificate))
        assertEquals(listOf(HostApp("881448767", "Desktop"), HostApp("1", "Tom & Jo's Game")), apps)
        assertEquals(device.certificate, seenClient)
    }

    @Test
    fun refusesAHostWithAnotherCertificate() = runBlocking {
        // Paired with one host; another answers at its address.
        assertNull(client.apps(host(device.certificate)))
        assertNull(seenClient)
    }

    @Test
    fun boxArtIsTheImageOrNothing() = runBlocking {
        assertEquals(PNG.toList(), client.boxArt(host(hostIdentity.certificate), "1")!!.toList())
        assertNull(client.boxArt(host(hostIdentity.certificate), "2"))
    }

    @Test
    fun skipsAppsWithoutATitleOrId() {
        val list = parseAppList(
            """<root status_code="200"><App><AppTitle>Ok</AppTitle><ID>5</ID></App>""" +
                """<App><AppTitle></AppTitle><ID>6</ID></App><App><AppTitle>Bad</AppTitle><ID>-1</ID></App></root>""",
        )
        assertEquals(listOf(HostApp("5", "Ok")), list)
        assertNull(parseAppList("""<root status_code="401" status_message="The client is not authorized."/>"""))
    }

    private fun host(trusted: X509Certificate) = PairedHost("127.0.0.1", server.address.port, trusted)

    private fun serverContext(): SSLContext {
        val store = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("host", hostIdentity.key, CharArray(0), arrayOf(hostIdentity.certificate))
        }
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, CharArray(0)) }
        // Like Sunshine, the host takes any client certificate in the handshake and checks it itself.
        val anyClient = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        return SSLContext.getInstance("TLS").apply { init(keys.keyManagers, arrayOf(anyClient), null) }
    }

    private fun reply(exchange: com.sun.net.httpserver.HttpExchange, body: ByteArray) {
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private companion object {
        const val APP_LIST = """<?xml version="1.0" encoding="utf-8"?>
<root status_code="200"><App><IsHdrSupported>0</IsHdrSupported><AppTitle>Desktop</AppTitle><ID>881448767</ID></App><App><IsHdrSupported>1</IsHdrSupported><AppTitle>Tom &amp; Jo&apos;s Game</AppTitle><ID>1</ID></App></root>"""
        val PNG = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
    }
}
