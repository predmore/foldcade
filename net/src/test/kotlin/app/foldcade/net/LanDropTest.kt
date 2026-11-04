package app.foldcade.net

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LanDropTest {
    private val pages = object : LanDrop.Pages {
        override fun form(action: String) = "<form action=\"$action\">"
        override fun done(received: Boolean) = if (received) "sent" else "empty"
    }

    private class Heard : LanDrop.Listener {
        val got = CountDownLatch(1)
        val stoppedLatch = CountDownLatch(1)
        @Volatile var visits = 0
        @Volatile var value: String? = "unset"
        @Volatile var receivedCount = 0
        override fun visited() { visits += 1 }
        override fun received(value: String?) { this.value = value; receivedCount += 1; got.countDown() }
        override fun stopped() { stoppedLatch.countDown() }
    }

    private val loopback = InetAddress.getByName("127.0.0.1")
    private val drops = mutableListOf<LanDrop>()

    @After
    fun closeAll() = drops.forEach { it.close() }

    private fun open(heard: Heard) = LanDrop.open(loopback, pages, heard).also { drops += it }

    @Test fun continuationRunsAfterTheOneTimeListenerHasClosed() {
        val heard = Heard()
        lateinit var drop: LanDrop
        drop = LanDrop.open(loopback, object : LanDrop.Pages {
            override fun form(action: String) = "form"
            override fun done(received: Boolean) = "done"
            override fun received(value: String?): String {
                assertFalse(drop.isOpen)
                assertEquals("https://romm.example", value)
                return "approval link"
            }
        }, heard).also { drops += it }
        assertEquals(200 to "approval link", post(drop, "key=https%3A%2F%2Fromm.example"))
        assertTrue(heard.got.await(1, TimeUnit.SECONDS))
        assertEquals(1, heard.receivedCount)
    }

    /** One raw HTTP exchange: the status code and the body. */
    private fun exchange(drop: LanDrop, request: String): Pair<Int, String> {
        val uri = URI(drop.url)
        Socket(uri.host, uri.port).use { socket ->
            socket.soTimeout = 5_000
            socket.getOutputStream().apply { write(request.toByteArray()); flush() }
            val text = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            val status = text.substringAfter(' ').substringBefore(' ').toInt()
            return status to text.substringAfter("\r\n\r\n")
        }
    }

    private fun path(drop: LanDrop) = URI(drop.url).path

    private fun post(drop: LanDrop, body: String, target: String = path(drop), origin: String? = null): Pair<Int, String> =
        exchange(drop, "POST $target HTTP/1.1\r\nHost: x\r\nContent-Type: application/x-www-form-urlencoded\r\n" +
            (origin?.let { "Origin: $it\r\n" } ?: "") + "Content-Length: ${body.toByteArray().size}\r\n\r\n$body")

    @Test
    fun servesTheFormOnlyAtItsTokenAndTakesOnePost() {
        val heard = Heard()
        val drop = open(heard)
        assertTrue(drop.url.startsWith("http://127.0.0.1:"))
        // 128 random bits in the path.
        assertEquals(22, path(drop).length - 1)
        assertEquals(404, exchange(drop, "GET / HTTP/1.1\r\nHost: x\r\n\r\n").first)
        assertEquals(404, exchange(drop, "GET ${path(drop)}x HTTP/1.1\r\nHost: x\r\n\r\n").first)
        assertEquals(0, heard.visits)
        val (status, body) = exchange(drop, "GET ${path(drop)} HTTP/1.1\r\nHost: x\r\n\r\n")
        assertEquals(200, status)
        assertEquals("<form action=\"${path(drop)}\">", body)
        assertEquals(1, heard.visits)
        // A POST to the wrong path is refused and the drop stays open.
        assertEquals(404, post(drop, "key=nope", target = "/wrong").first)
        assertTrue(drop.isOpen)
        val origin = drop.url.removeSuffix(path(drop))
        assertEquals("sent" to 200, post(drop, "key=%20abc123%20&other=1", origin = origin).let { it.second to it.first })
        assertTrue(heard.got.await(5, TimeUnit.SECONDS))
        assertEquals("abc123", heard.value)
        assertFalse(drop.isOpen)
        // The token is spent: nothing listens any more.
        val refused = try {
            post(drop, "key=again")
            false
        } catch (_: IOException) {
            true
        }
        assertTrue(refused)
        assertEquals(1, heard.receivedCount)
    }

    @Test
    fun aPostWithNothingUsableStillSpendsTheToken() {
        val heard = Heard()
        val drop = open(heard)
        assertEquals("empty", post(drop, "key=").second)
        assertTrue(heard.got.await(5, TimeUnit.SECONDS))
        assertNull(heard.value)
        assertFalse(drop.isOpen)
    }

    @Test
    fun aPostFromAnotherOriginOrOfAnotherKindIsRefused() {
        val heard = Heard()
        val drop = open(heard)
        assertEquals(403, post(drop, "key=abc", origin = "http://evil.example").first)
        assertEquals(415, exchange(drop, "POST ${path(drop)} HTTP/1.1\r\nContent-Type: application/json\r\nContent-Length: 2\r\n\r\n{}").first)
        assertEquals(413, exchange(drop, "POST ${path(drop)} HTTP/1.1\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: 99999\r\n\r\n").first)
        assertEquals(405, exchange(drop, "PUT ${path(drop)} HTTP/1.1\r\n\r\n").first)
        assertTrue(drop.isOpen)
        assertEquals(1L, heard.got.count)
    }

    @Test
    fun closesItselfAfterTooManyStrays() {
        val heard = Heard()
        val drop = open(heard)
        repeat(LanDrop.MAX_STRAYS) { exchange(drop, "GET /guess$it HTTP/1.1\r\n\r\n") }
        assertTrue(heard.stoppedLatch.await(5, TimeUnit.SECONDS))
        assertFalse(drop.isOpen)
    }

    @Test
    fun closeStopsListeningAtOnce() {
        val heard = Heard()
        val drop = open(heard)
        drop.close()
        assertTrue(heard.stoppedLatch.await(5, TimeUnit.SECONDS))
        assertFalse(drop.isOpen)
        assertEquals(1L, heard.got.count)
    }

    @Test
    fun listensOnlyOnALanAddress() {
        val wildcard = runCatching { LanDrop.open(InetAddress.getByName("0.0.0.0"), pages, Heard()) }
        assertTrue(wildcard.isFailure)
        val public = runCatching { LanDrop.open(InetAddress.getByName("8.8.8.8"), pages, Heard()) }
        assertTrue(public.isFailure)
    }

    @Test
    fun formValuesAreShortPrintableAndTrimmed() {
        assertEquals("abc", LanDrop.formValue("key=abc"))
        assertEquals("a+b", LanDrop.formValue("x=1&key=a%2Bb"))
        assertNull(LanDrop.formValue("key=a+b"))
        assertNull(LanDrop.formValue("key="))
        assertNull(LanDrop.formValue("nokey"))
        assertNull(LanDrop.formValue("key=ab%0Ac"))
        assertNull(LanDrop.formValue("key=%zz"))
        assertNull(LanDrop.formValue("key=" + "a".repeat(LanDrop.MAX_VALUE + 1)))
    }
}
