package app.foldcade.romm

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Collections

internal class Recorded(
    val method: String,
    val path: String,
    val query: String?,
    val body: ByteArray,
    val headers: Map<String, List<String>>,
)

internal class MockRomm : AutoCloseable {
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val routes = mutableListOf<Route>()
    val recorded: MutableList<Recorded> = Collections.synchronizedList(mutableListOf())
    val origin: String

    init {
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.readBytes()
            val headers = exchange.requestHeaders.entries.associate { it.key to it.value.toList() }
            recorded += Recorded(
                exchange.requestMethod,
                exchange.requestURI.path,
                exchange.requestURI.rawQuery,
                body,
                headers,
            )
            val route = routes.lastOrNull { route ->
                route.method == exchange.requestMethod && route.path == exchange.requestURI.path
            }
            if (route == null) {
                val msg = "unmapped ${exchange.requestMethod} ${exchange.requestURI.path}".toByteArray()
                exchange.sendResponseHeaders(500, msg.size.toLong())
                exchange.responseBody.use { it.write(msg) }
            } else {
                route.handler(exchange, body)
            }
        }
        server.start()
        origin = "http://127.0.0.1:${server.address.port}"
    }

    fun route(method: String, path: String, handler: (HttpExchange, ByteArray) -> Unit) {
        routes += Route(method, path, handler)
    }

    fun assertKnownPaths() {
        val unknown = recorded.map { it.path }.distinct().filter { path ->
            allowed.none { it.matches(path) }
        }
        check(unknown.isEmpty()) { "request used a path that is not in the RomM 5.4 schema: $unknown" }
    }

    override fun close() {
        server.stop(0)
    }

    private data class Route(
        val method: String,
        val path: String,
        val handler: (HttpExchange, ByteArray) -> Unit,
    )

    companion object {
        private val allowed = listOf(
            Regex("^/openapi\\.json$"),
            Regex("^/api/heartbeat$"),
            Regex("^/api/auth/device/init$"),
            Regex("^/api/auth/device/token$"),
            Regex("^/api/platforms$"),
            Regex("^/api/roms$"),
            Regex("^/api/roms/\\d+/simple$"),
            Regex("^/api/roms/\\d+/content/.+"),
            Regex("^/api/devices$"),
            Regex("^/api/devices/[^/]+$"),
            Regex("^/api/sync/negotiate$"),
            Regex("^/api/saves$"),
            Regex("^/api/saves/\\d+/content$"),
            Regex("^/api/saves/\\d+/downloaded$"),
            Regex("^/api/sync/sessions/\\d+/complete$"),
        )
    }
}

internal fun json(exchange: HttpExchange, status: Int, text: String) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.add("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

internal fun bytes(exchange: HttpExchange, status: Int, body: ByteArray) {
    exchange.responseHeaders.add("Content-Type", "application/octet-stream")
    exchange.sendResponseHeaders(status, body.size.toLong())
    exchange.responseBody.use { it.write(body) }
}
