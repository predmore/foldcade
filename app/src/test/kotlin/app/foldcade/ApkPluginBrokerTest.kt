package app.foldcade

import app.foldcade.net.LanHttpException
import app.foldcade.net.LanReplyException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

internal fun testApkPlugin(signers: Set<String> = setOf("a".repeat(64)), packageName: String = "test.plugin") = ApkPlugin(
    packageName, "test.plugin.Play", "Test plugin", "1", signers, 1, "moonlight", true, setOf("gamestream"),
)

class ApkPluginBrokerTest {
    private val plugin = testApkPlugin()

    private fun call(
        caller: String? = plugin.packageName, method: String = "gamestream.request", path: String = "launch", query: String? = null,
        granted: Boolean = true, request: suspend (String, String, String?) -> Pair<Int, ByteArray> = { _, _, _ -> 200 to "<root/>".toByteArray() },
    ): BrokerReply = runBlocking {
        callPluginBroker(caller, method, "host-uuid", path, query, "test.host", listOf(plugin), { _, _ -> granted }, { emptyList() }, request)
    }

    @Test fun callerMustBeDiscoveredAndGrantedOnEveryCall() {
        assertEquals("not_granted", call(caller = null).values["error"])
        assertEquals("not_granted", call(caller = "other.plugin").values["error"])
        assertEquals("not_granted", call(granted = false).values["error"])
        assertEquals(true, call().values["ok"])
        assertEquals("not_granted", call(granted = false).values["error"])
    }

    @Test fun helloNeedsDiscoveryButDoesNotNeedConsent() {
        val reply = call(method = "hello", granted = false).values
        assertEquals(true, reply["ok"])
        assertEquals(1, reply["protocol"])
        assertEquals("test.host", reply["hostPackage"])
        assertArrayEquals(emptyArray<String>(), reply["granted"] as Array<*>)
    }

    @Test fun invalidRequestsNeverReachTheTransport() {
        val mustNotRun: suspend (String, String, String?) -> Pair<Int, ByteArray> = { _, _, _ -> error("Transport called") }
        for (path in listOf("pair", "unpair", "../launch", "/launch")) assertEquals("bad_request", call(path = path, request = mustNotRun).values["error"])
        for (query in listOf("#", "\n", "\r", "x".repeat(4097))) assertEquals("bad_request", call(query = query, request = mustNotRun).values["error"])
        assertEquals("bad_request", call(method = "unknown", request = mustNotRun).values["error"])
    }

    @Test fun repliesCarryTheContractErrorsWithoutThrowing() {
        val failures = listOf(
            BrokerFailure("unknown_host", "No pairing") to "unknown_host",
            BrokerFailure("unreachable", "No address") to "unreachable",
            LanHttpException("TLS failed") to "unreachable",
            LanReplyException("Too large") to "host_error",
            IllegalStateException("Unexpected") to "internal",
        )
        failures.forEach { (failure, code) ->
            val reply = call(request = { _, _, _ -> throw failure }).values
            assertEquals(false, reply["ok"])
            assertEquals(code, reply["error"])
            assertTrue((reply["message"] as String).isNotEmpty())
        }
        assertEquals("host_error", call(request = { _, _, _ -> 500 to byteArrayOf() }).values["error"])
        assertEquals("host_error", call(request = { _, _, _ -> 200 to ByteArray(1024 * 1024 + 1) }).values["error"])
        assertEquals("host_error", call(request = { _, _, _ -> 200 to ByteArray(BROKER_MAX_STRING_CHARS + 1) { 'x'.code.toByte() } }).values["error"])
        assertEquals("host_error", call(request = { _, _, _ -> 200 to byteArrayOf(0xc3.toByte()) }).values["error"])
        assertEquals("<root/>", call().values["body"])
    }

    @Test fun grantsBindTheCurrentSignerSetAndPackageAndRevokeImmediately() {
        val prefs = MemoryPrefs()
        val grants = ApkPluginGrants(prefs)
        val twoSigners = plugin.copy(signers = setOf("b".repeat(64), "a".repeat(64)))
        grants.setGranted(twoSigners, "gamestream", true)
        assertTrue(ApkPluginGrants(prefs).granted(twoSigners.copy(signers = twoSigners.signers.reversed().toSet()), "gamestream"))
        assertFalse(grants.granted(plugin, "gamestream"))
        assertFalse(grants.granted(twoSigners.copy(packageName = "other"), "gamestream"))
        assertFalse(grants.granted(twoSigners, "unknown"))
        assertTrue(grants.routes(twoSigners))
        grants.setRouting(twoSigners, false)
        assertFalse(grants.routes(twoSigners))
        grants.setGranted(twoSigners, "gamestream", false)
        assertFalse(grants.granted(twoSigners, "gamestream"))
        grants.setGranted(twoSigners, "gamestream", true)
        assertTrue(grants.routes(twoSigners))
        grants.forget(plugin.packageName)
        assertFalse(ApkPluginGrants(prefs).granted(twoSigners, "gamestream"))
    }
}
