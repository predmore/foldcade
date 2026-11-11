package app.foldcade

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import app.foldcade.gamestream.BROKER_PATHS
import app.foldcade.gamestream.brokerQuery
import app.foldcade.gamestream.brokerAppIds
import app.foldcade.gamestream.brokerNamesAppOtherwise
import app.foldcade.net.LanHttpException
import app.foldcade.net.LanReplyException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

internal data class BrokerHost(
    val uuid: String, val name: String, val address: String,
    val httpPort: Int, val httpsPort: Int, val mac: String? = null,
)

internal data class BrokerReply(val values: Map<String, Any>) {
    companion object {
        fun success(vararg values: Pair<String, Any>): BrokerReply = BrokerReply(mapOf("ok" to true) + values)
        fun error(code: String, message: String): BrokerReply = BrokerReply(mapOf("ok" to false, "error" to code, "message" to message))
    }
}

internal class BrokerFailure(val code: String, message: String) : Exception(message)

/** Binder's shared 1 MiB transaction buffer carries UTF-16 strings plus the reply parcel. */
internal const val BROKER_MAX_STRING_CHARS = 384 * 1024

internal suspend fun awaitApkPluginBrokerReady(ready: Deferred<Unit>, timeoutMillis: Long = 10_000): Boolean =
    withTimeoutOrNull(timeoutMillis) { ready.await(); true } ?: false

/** Android supplies the caller package; discovery and grants are read again for every call. */
internal suspend fun callPluginBroker(
    caller: String?, method: String, arg: String?, path: String?, query: String?, hostPackage: String,
    plugins: List<ApkPlugin>, granted: (ApkPlugin, String) -> Boolean,
    hosts: suspend () -> List<BrokerHost>, request: suspend (String, String, String?) -> Pair<Int, ByteArray>,
    token: String? = null,
    launchAllowed: (String, String, String?, List<String>) -> Boolean = { _, _, _, _ -> false },
    awaitReady: suspend () -> Boolean = { true },
    touchToken: (String, String, String?) -> Boolean = { _, _, _ -> false },
): BrokerReply {
    val plugin = plugins.firstOrNull { it.packageName == caller }
        ?: return BrokerReply.error("not_granted", "Caller is not a discovered plugin")
    val scopes = plugin.scopes.filter { granted(plugin, it) }.sorted()
    if (method == "hello") return BrokerReply.success(
        "protocol" to ApkPluginProtocol.VERSION, "granted" to scopes.toTypedArray(), "hostPackage" to hostPackage,
    )
    if (ApkPluginProtocol.GAMESTREAM !in scopes) return BrokerReply.error("not_granted", "GameStream access is not granted")
    return try {
        when (method) {
            "gamestream.hosts" -> {
                if (!awaitReady()) return BrokerReply.error("internal", "Host initialization timed out")
                BrokerReply.success("hosts" to hosts())
            }
            "gamestream.request" -> {
                if (arg.isNullOrEmpty() || path !in BROKER_PATHS) return BrokerReply.error("bad_request", "Invalid host or path")
                try { brokerQuery(query, "0000000000000000") } catch (_: IllegalArgumentException) {
                    return BrokerReply.error("bad_request", "Invalid query")
                }
                // Optional on metadata/cancel calls; only a matching live token is refreshed.
                touchToken(plugin.packageName, arg, token)
                if ((path == "launch" || path == "resume") && (brokerNamesAppOtherwise(query) || !launchAllowed(plugin.packageName, arg, token, brokerAppIds(query)))) {
                    return BrokerReply.error("not_launched", "No matching Foldcade play start")
                }
                if (!awaitReady()) return BrokerReply.error("internal", "Host initialization timed out")
                val (status, bytes) = request(arg, path!!, query)
                if (status !in 200..299) return BrokerReply.error("host_error", "Host returned HTTP $status")
                if (bytes.size > app.foldcade.gamestream.BROKER_MAX_REPLY_BYTES) return BrokerReply.error("host_error", "Host reply is too large")
                val body = try {
                    Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                } catch (_: java.nio.charset.CharacterCodingException) {
                    return BrokerReply.error("host_error", "Host reply is not UTF-8")
                }
                if (body.length > BROKER_MAX_STRING_CHARS) return BrokerReply.error("host_error", "Host reply exceeds the Binder transport budget")
                BrokerReply.success("status" to status, "body" to body)
            }
            else -> BrokerReply.error("bad_request", "Unknown method")
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: BrokerFailure) {
        BrokerReply.error(failure.code, failure.message.orEmpty())
    } catch (_: LanReplyException) {
        BrokerReply.error("host_error", "Host reply is oversized or unreadable")
    } catch (_: LanHttpException) {
        BrokerReply.error("unreachable", "Host connection failed")
    } catch (_: Exception) {
        BrokerReply.error("internal", "Broker request failed")
    }
}

class ApkPluginBroker : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val context = context ?: return BrokerReply.error("internal", "Host unavailable").bundle()
        return try {
            val caller = callingPackage
            val grants = ApkPluginGrants.open(context)
            val app = context.applicationContext as FoldcadeApp
            runBlocking {
                callPluginBroker(
                    caller, method, arg, extras?.getString("path"), extras?.getString("query"), context.packageName,
                    discoverApkPlugins(context.packageManager), grants::granted,
                    hosts = { app.moonlightStreams.brokerHosts() },
                    request = { uuid, path, query -> app.moonlightStreams.brokerCall(uuid, path, query) },
                    token = extras?.getString("token"), launchAllowed = app.apkPluginLaunchTokens::allows,
                    awaitReady = { awaitApkPluginBrokerReady(app.apkPluginBrokerReady) },
                    touchToken = app.apkPluginLaunchTokens::touch,
                )
            }.bundle()
        } catch (_: SecurityException) {
            BrokerReply.error("not_granted", "Caller identity could not be verified").bundle()
        } catch (_: android.os.BadParcelableException) {
            BrokerReply.error("bad_request", "Invalid arguments").bundle()
        } catch (_: Exception) {
            BrokerReply.error("internal", "Broker unavailable").bundle()
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

private fun BrokerReply.bundle(): Bundle = Bundle().apply {
    for ((key, value) in values) when (value) {
        is Boolean -> putBoolean(key, value)
        is Int -> putInt(key, value)
        is String -> putString(key, value)
        is Array<*> -> putStringArray(key, value.filterIsInstance<String>().toTypedArray())
        is List<*> -> putParcelableArrayList(key, ArrayList(value.filterIsInstance<BrokerHost>().map { host ->
            Bundle().apply {
                putString("uuid", host.uuid); putString("name", host.name); putString("address", host.address)
                putInt("httpPort", host.httpPort); putInt("httpsPort", host.httpsPort)
                host.mac?.let { putString("mac", it) }
            }
        }))
    }
}
