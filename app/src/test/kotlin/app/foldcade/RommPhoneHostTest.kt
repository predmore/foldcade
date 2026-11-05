package app.foldcade

import app.foldcade.language.KeyDrop
import app.foldcade.language.KeyDropPage
import app.foldcade.language.RommPhoneCopy
import app.foldcade.romm.RommClient
import app.foldcade.romm.rommPhoneApprovalUrl
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URLEncoder
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class RommPhoneHostTest {
    @Test fun relativeDeviceInitPayloadReachesThePhoneAfterTheFormCollectorClosesTheDrop() = runBlocking {
        DeviceInitFixture("/pair/device?user_code=ABCD1234").use { romm ->
            val page = handoff(romm, romm.origin)
            assertTrue(page.contains("href=\"${romm.origin}/pair/device?user_code=ABCD1234\""))
            assertTrue(page.contains(RommPhoneCopy.approve))
            assertFalse(page.contains("secret-device-code"))
        }
    }

    @Test fun explicitDefaultPortInDeviceInitStillGivesThePhoneItsApprovalLink() = runBlocking {
        DeviceInitFixture("https://ROMM.example:443/pair/device?user_code=ABCD1234").use { romm ->
            val page = handoff(romm, "https://romm.example")
            assertTrue(page.contains("href=\"https://ROMM.example:443/pair/device?user_code=ABCD1234\""))
            assertFalse(page.contains(RommPhoneCopy.failed))
        }
    }

    @Test fun aDifferentAuthorityInDeviceInitTellsThePhoneToScanTheThorsApprovalCode() = runBlocking {
        DeviceInitFixture("https://public.example/pair/device?user_code=ABCD1234").use { romm ->
            val page = handoff(romm, romm.origin)
            assertTrue(page.contains("approval QR code, scan it to continue"))
            assertFalse(page.contains("href=\"https://public.example"))
            assertFalse(page.contains("secret-device-code"))
        }
    }

    private suspend fun handoff(romm: DeviceInitFixture, submitted: String): String {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { main ->
            val scope = CoroutineScope(SupervisorJob() + main)
            val failed = AtomicReference<Throwable?>()
            lateinit var host: RommPhoneHost
            host = RommPhoneHost(
                connectivity = null, scope = scope,
                colors = { KeyDropPage.Colors("#000000", "#111111", "#ffffff", "#888888", "#ffaa00") },
                main = main, addressOf = { InetAddress.getByName("127.0.0.1") },
                onAddress = { selected ->
                    try {
                        assertEquals(submitted, selected)
                        // Exactly what the real form collector does once the address is set.
                        host.want(false, retry = false)
                        withContext(Dispatchers.IO) {
                            RommClient(romm.origin, { null },
                                connectTimeout = Duration.ofSeconds(1), readTimeout = Duration.ofSeconds(1)).use { client ->
                                val challenge = client.beginDeviceAuth("phone-test-install", "Foldcade", "test")
                                rommPhoneApprovalUrl(selected, client.verificationUrl(challenge))
                            }
                        }
                    } catch (failure: Throwable) {
                        failed.set(failure)
                        throw failure
                    }
                },
            )
            try {
                withContext(main) { host.want(true, retry = false) }
                val url = withTimeout(5_000) {
                    var opened: KeyDrop.Open? = null
                    while (opened == null) {
                        opened = withContext(main) { host.state as? KeyDrop.Open }
                        if (opened == null) delay(10)
                    }
                    opened.url
                }
                val response = withContext(Dispatchers.IO) { post(url, submitted) }
                failed.get()?.let { throw AssertionError("Address handoff failed", it) }
                assertTrue(response.startsWith("HTTP/1.1 200 OK\r\n"))
                assertNull(withContext(main) { host.state })
                return response.substringAfter("\r\n\r\n")
            } finally {
                withContext(main) { host.want(false, retry = false) }
                scope.coroutineContext[Job]!!.cancelAndJoin()
            }
        }
    }

    private fun post(url: String, value: String): String {
        val uri = URI(url)
        val body = "key=" + URLEncoder.encode(value, "UTF-8")
        return Socket(uri.host, uri.port).use { socket ->
            socket.soTimeout = 15_000
            socket.getOutputStream().apply {
                write(("POST ${uri.path} HTTP/1.1\r\nHost: ${uri.authority}\r\n" +
                    "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: ${body.length}\r\n\r\n$body").toByteArray())
                flush()
            }
            socket.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
    }

    /** Real RommClient parsing of RomM 5.4.1's heartbeat and device-init response shape. */
    private class DeviceInitFixture(verification: String) : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                exchange.requestBody.use { it.readBytes() }
                val init = exchange.requestURI.path == "/api/auth/device/init"
                val body = (if (init) """{"device_code":"secret-device-code","user_code":"ABCD1234","verification_path":"/pair/device","verification_path_complete":"$verification","expires_in":600,"interval":5}"""
                    else """{"SYSTEM":{"VERSION":"5.4.1"}}""").toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(if (init) 201 else 200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val origin = "http://127.0.0.1:${server.address.port}"
        override fun close() = server.stop(0)
    }
}
