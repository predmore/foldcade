package app.foldcade.net

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A one-time page on the local network where a phone hands Foldcade one short value, such
 * as an API key, so nobody types it on a controller. The QR code Foldcade shows holds the
 * page's address with a fresh random token in its path.
 *
 * It is made to be small and short-lived rather than general:
 * - It listens on one LAN address only ([open] refuses any other), on a port the system
 *   picks, and only while the caller holds it: the caller [close]s it when its page closes.
 * - Only the exact path `/<token>` answers, 128 random bits compared in constant time.
 *   Anything else is a bare 404, and after [MAX_STRAYS] of those it closes by itself.
 * - It serves the form on GET and takes exactly one POST to that path, then closes,
 *   whatever the POST held. A second value needs a new drop with a new token.
 * - One connection at a time, each with a short read timeout and small size limits, with
 *   `Connection: close`. A POST from another origin is refused.
 *
 * The page is plain HTTP: a phone can't trust a certificate for a LAN address. What
 * crosses the network is the one value, once, on the player's own network.
 */
class LanDrop private constructor(
    private val server: ServerSocket,
    private val token: String,
    private val pages: Pages,
    private val listener: Listener,
) : Closeable {
    /** The page as the phone opens it, for the QR code. */
    val url: String

    private val origin: String
    private val path = "/$token"
    private val closed = AtomicBoolean(false)
    private val thread: Thread

    init {
        val host = server.inetAddress.hostAddress.let { if (':' in it) "[${it.substringBefore('%')}]" else it }
        origin = "http://$host:${server.localPort}"
        url = "$origin$path"
        thread = Thread(::serve, "LanDrop").apply { isDaemon = true }
    }

    /** What the page says. [form] posts a field named [FIELD] to [action]. */
    interface Pages {
        fun form(action: String): String

        /** After the one POST: [received] is false when it held nothing usable. */
        fun done(received: Boolean): String

        /** A bounded handoff may return a continuation link after accepting the value. Runs off Main. */
        fun received(value: String?): String = done(value != null)
    }

    /** Called on the drop's own thread. */
    interface Listener {
        /** A phone opened the page. */
        fun visited() {}

        /** The one POST came, holding [value], or null when it held nothing usable. The drop has closed. */
        fun received(value: String?)

        /** The drop closed without a value: [close], or too many strays. */
        fun stopped() {}
    }

    private var strays = 0
    private var visited = false

    private fun serve() {
        try {
            while (!closed.get()) {
                val socket = try {
                    server.accept()
                } catch (_: IOException) {
                    break
                }
                socket.use { if (handle(it)) return }
            }
        } finally {
            if (closed.compareAndSet(false, true)) {
                runCatching { server.close() }
                listener.stopped()
            }
        }
    }

    /** True once the one POST has been taken and the drop is done. */
    private fun handle(socket: Socket): Boolean {
        socket.soTimeout = READ_TIMEOUT_MILLIS
        val input = socket.getInputStream().buffered()
        val output = socket.getOutputStream()
        // One slow phone can't hold the page for others: the whole request has a deadline.
        val deadline = System.nanoTime() + REQUEST_DEADLINE_NANOS
        val head = try {
            readHead(input, deadline)
        } catch (_: IOException) {
            return false
        } ?: return stray(output, 400)
        val lines = head.split("\r\n")
        val parts = lines.first().split(' ')
        if (parts.size != 3 || !parts[2].startsWith("HTTP/1.")) return stray(output, 400)
        val (method, target) = parts
        if (!sameToken(target)) return stray(output, 404)
        val headers = lines.drop(1).mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) null else line.substring(0, colon).trim().lowercase() to line.substring(colon + 1).trim()
        }.toMap()
        return when (method) {
            "GET" -> {
                if (!visited) {
                    visited = true
                    listener.visited()
                }
                respond(output, 200, pages.form(path))
                false
            }
            "POST" -> post(input, output, headers, deadline)
            else -> stray(output, 405)
        }
    }

    private fun post(input: InputStream, output: OutputStream, headers: Map<String, String>, deadline: Long): Boolean {
        // A page elsewhere that somehow learnt the token still can't post to it from the phone's browser.
        headers["origin"]?.let { if (it != origin) return stray(output, 403) }
        val length = headers["content-length"]?.toIntOrNull()
        if (length == null || length < 0 || length > MAX_BODY) return stray(output, 413)
        val type = headers["content-type"].orEmpty().substringBefore(';').trim().lowercase()
        if (type != "application/x-www-form-urlencoded") return stray(output, 415)
        val body = try {
            readExactly(input, length, deadline)
        } catch (_: IOException) {
            return false
        }
        // The token is spent: whatever came, this drop takes no more.
        closed.set(true)
        runCatching { server.close() }
        val value = formValue(String(body, Charsets.UTF_8))
        runCatching { respond(output, 200, pages.received(value)) }
        listener.received(value)
        return true
    }

    private fun stray(output: OutputStream, status: Int): Boolean {
        runCatching { respond(output, status, null) }
        strays += 1
        if (strays >= MAX_STRAYS) close()
        return false
    }

    private fun sameToken(target: String): Boolean =
        MessageDigest.isEqual(target.toByteArray(Charsets.UTF_8), path.toByteArray(Charsets.UTF_8))

    /** Starts listening. */
    private fun start(): LanDrop = apply { thread.start() }

    /** Stops listening at once. A page open on the phone can no longer send. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { server.close() }
        listener.stopped()
    }

    val isOpen: Boolean get() = !closed.get()

    companion object {
        /** The form field the value comes in. */
        const val FIELD = "key"

        /** Strays, such as a browser's favicon request or a scan, before the drop closes by itself. */
        const val MAX_STRAYS = 64

        private const val READ_TIMEOUT_MILLIS = 5_000
        private const val REQUEST_DEADLINE_NANOS = 10_000_000_000L
        private const val MAX_HEAD = 8 * 1024
        private const val MAX_BODY = 4 * 1024

        /** The longest value taken. A SteamGridDB key is 32 characters. */
        const val MAX_VALUE = 256

        /**
         * Listens on [address], which must be a LAN address ([isLanHost]) and not a wildcard,
         * until [LanDrop.close] or the one POST.
         */
        fun open(address: InetAddress, pages: Pages, listener: Listener, random: SecureRandom = SecureRandom()): LanDrop {
            require(!address.isAnyLocalAddress && isLanHost(address.hostAddress)) { "Not a LAN address" }
            val bytes = ByteArray(16).also(random::nextBytes)
            val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            val server = ServerSocket()
            try {
                server.bind(InetSocketAddress(address, 0), BACKLOG)
            } catch (failure: IOException) {
                runCatching { server.close() }
                throw failure
            }
            return LanDrop(server, token, pages, listener).start()
        }

        private const val BACKLOG = 4

        /** The value of [FIELD] in a form body: trimmed, printable, and short, or null. */
        internal fun formValue(body: String): String? {
            val raw = body.split('&').firstNotNullOfOrNull { pair ->
                val name = pair.substringBefore('=')
                if (name == FIELD && '=' in pair) pair.substringAfter('=') else null
            } ?: return null
            val value = try {
                URLDecoder.decode(raw, "UTF-8")
            } catch (_: IllegalArgumentException) {
                return null
            }.trim()
            if (value.isEmpty() || value.length > MAX_VALUE) return null
            if (value.any { it.isISOControl() || it.isWhitespace() }) return null
            return value
        }

        private fun readHead(input: InputStream, deadline: Long): String? {
            val bytes = ByteArrayOutputStream()
            var last = 0
            while (bytes.size() < MAX_HEAD) {
                if (System.nanoTime() > deadline) throw SocketException("Too slow")
                val next = input.read()
                if (next < 0) return null
                bytes.write(next)
                last = (last shl 8) or next
                if (last == END_OF_HEAD) return bytes.toString(Charsets.ISO_8859_1.name()).removeSuffix("\r\n\r\n")
            }
            return null
        }

        private const val END_OF_HEAD = 0x0d0a0d0a

        private fun readExactly(input: InputStream, length: Int, deadline: Long): ByteArray {
            val out = ByteArray(length)
            var read = 0
            while (read < length) {
                if (System.nanoTime() > deadline) throw SocketException("Too slow")
                val count = input.read(out, read, length - read)
                if (count < 0) throw SocketException("Body ended early")
                read += count
            }
            return out
        }

        private fun respond(output: OutputStream, status: Int, html: String?) {
            val body = (html ?: "").toByteArray(Charsets.UTF_8)
            val reason = when (status) {
                200 -> "OK"
                400 -> "Bad Request"
                403 -> "Forbidden"
                404 -> "Not Found"
                405 -> "Method Not Allowed"
                413 -> "Payload Too Large"
                415 -> "Unsupported Media Type"
                else -> "Error"
            }
            val head = buildString {
                append("HTTP/1.1 $status $reason\r\n")
                append("Content-Type: text/html; charset=utf-8\r\n")
                append("Content-Length: ${body.size}\r\n")
                append("Cache-Control: no-store\r\n")
                append("Referrer-Policy: no-referrer\r\n")
                append("X-Content-Type-Options: nosniff\r\n")
                append("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'\r\n")
                append("Connection: close\r\n\r\n")
            }
            output.write(head.toByteArray(Charsets.ISO_8859_1))
            output.write(body)
            output.flush()
        }
    }
}
