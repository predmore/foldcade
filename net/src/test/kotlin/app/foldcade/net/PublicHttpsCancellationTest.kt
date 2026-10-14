package app.foldcade.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class PublicHttpsCancellationTest {
    @Test fun cancellationAfterHeadersClosesTheBodyWithoutWaitingForReadTimeout() = runBlocking {
        val reading = CountDownLatch(1)
        val released = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                reading.countDown()
                check(released.await(5, TimeUnit.SECONDS)) { "Cancellation never released the body" }
                throw IOException("Canceled")
            }
            override fun close() { closed.countDown() }
            override fun timeout() = Timeout.NONE
        }.buffer()
        val body = object : ResponseBody() {
            override fun contentType() = null
            override fun contentLength() = -1L
            override fun source(): BufferedSource = source
        }
        val call = BodyCall(body) { released.countDown() }
        val result = async(Dispatchers.IO) { call.readResponse { it.body!!.byteStream().readNBytes(1024) } }
        assertTrue("Body was not being read", reading.await(5, TimeUnit.SECONDS))
        result.cancel()
        withTimeout(1000) { result.join() }
        assertTrue(call.isCanceled())
        assertTrue("Response was not closed", closed.await(1, TimeUnit.SECONDS))
        assertTrue("Callback was still blocked", call.finished.await(1, TimeUnit.SECONDS))
    }

    @Test fun aBodyReadFailureClosesTheResponseAndReachesTheCaller() = runBlocking {
        val call = BodyCall("body".toResponseBody()) { }
        try {
            call.readResponse<Unit> { throw IOException("Read failed") }
            fail("The read error was lost")
        } catch (error: IOException) {
            assertEquals("Read failed", error.message)
        }
        assertTrue(call.finished.await(1, TimeUnit.SECONDS))
    }

    private class BodyCall(private val body: ResponseBody, private val onCancel: () -> Unit) : Call {
        val finished = CountDownLatch(1)
        @Volatile private var canceled = false
        private val request = Request.Builder().url("https://cdn2.steamgriddb.com/thumb/test.png").build()
        override fun request() = request
        override fun execute(): Response = error("Async only")
        override fun enqueue(responseCallback: Callback) {
            thread(isDaemon = true) {
                try {
                    responseCallback.onResponse(this, Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                        .code(200).message("OK").body(body).build())
                } finally { finished.countDown() }
            }
        }
        override fun cancel() { canceled = true; onCancel() }
        override fun isExecuted() = true
        override fun isCanceled() = canceled
        override fun timeout() = Timeout.NONE
        override fun clone(): Call = error("No retry")
    }
}
