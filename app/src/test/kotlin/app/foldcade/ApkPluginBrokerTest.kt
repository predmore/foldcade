package app.foldcade

import app.foldcade.net.LanHttpException
import app.foldcade.net.LanReplyException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import org.junit.Assert.*
import org.junit.Test

internal fun testApkPlugin(signers: Set<String> = setOf("a".repeat(64)), packageName: String = "test.plugin") = ApkPlugin(
    packageName, "test.plugin.Play", "Test plugin", "1", signers, 1, "moonlight", true, setOf("gamestream"),
)

class ApkPluginBrokerTest {
    private val plugin = testApkPlugin()

    private fun call(
        caller: String? = plugin.packageName, method: String = "gamestream.request", path: String = "serverinfo", query: String? = null,
        granted: Boolean = true, request: suspend (String, String, String?) -> Pair<Int, ByteArray> = { _, _, _ -> 200 to "<root/>".toByteArray() },
    ): BrokerReply = runBlocking {
        callPluginBroker(caller, method, "host-uuid", path, query, "test.host", listOf(plugin), { _, _ -> granted }, { emptyList() }, request)
    }

    @Test fun launchAndResumeRequireTheMatchingRecentHostChosenStart() = runBlocking {
        var now = 100L
        val tokens = ApkPluginLaunchTokens { now }
        val token = tokens.issue(plugin.packageName, "host-uuid", "42")
        val other = plugin.copy(packageName = "other.plugin")
        var requested = 0
        suspend fun play(
            caller: String = plugin.packageName, host: String = "host-uuid", query: String? = "appid=42",
            provided: String? = token, path: String = "launch",
        ): BrokerReply = callPluginBroker(
            caller, "gamestream.request", host, path, query, "test.host", listOf(plugin, other), { _, _ -> true }, { emptyList() },
            request = { _, _, _ -> requested++; 200 to "<root/>".toByteArray() },
            token = provided, launchAllowed = tokens::allows, touchToken = tokens::touch,
        )
        assertEquals("not_launched", play(caller = other.packageName).values["error"])
        assertEquals("not_launched", play(host = "another-host").values["error"])
        assertEquals("not_launched", play(query = "appid=43").values["error"])
        assertEquals("not_launched", play(query = "appid=42&%61ppid=43").values["error"])
        assertEquals("not_launched", play(query = "appid=").values["error"])
        assertEquals("not_launched", play(provided = null).values["error"])
        assertEquals("not_launched", play(provided = "unknown").values["error"])
        // A start must name its app, and only by appid: Apollo can pick an app by appuuid.
        assertEquals("not_launched", play(path = "resume", query = null).values["error"])
        assertEquals("not_launched", play(query = "appid=42&appuuid=other").values["error"])
        assertEquals("not_launched", play(query = "appid=42&%41PPUUID=other").values["error"])
        assertEquals(0, requested)
        // Query names and values use the sanitiser's URL decoding; retries do not consume the token.
        assertEquals(true, play(query = "%61pp%69d=%34%32").values["ok"])
        assertEquals(true, play(path = "resume", query = "appid=42").values["ok"])
        assertEquals(true, play(path = "resume", query = "APPID=42&appid=42").values["ok"])
        now += ApkPluginLaunchTokens.WINDOW_MS - 1
        assertEquals(true, play().values["ok"])
        now += ApkPluginLaunchTokens.WINDOW_MS
        assertEquals("not_launched", play().values["error"])
        assertEquals(4, requested)
    }

    @Test fun serverInfoRefreshKeepsTheStartAlivePastItsOriginalWindow() = runBlocking {
        var now = 0L
        val tokens = ApkPluginLaunchTokens { now }
        val token = tokens.issue(plugin.packageName, "host-uuid", "42")
        suspend fun request(path: String, query: String? = null): BrokerReply = callPluginBroker(
            plugin.packageName, "gamestream.request", "host-uuid", path, query, "test.host", listOf(plugin), { _, _ -> true },
            hosts = { emptyList() }, request = { _, _, _ -> 200 to "<root/>".toByteArray() },
            token = token, launchAllowed = tokens::allows, touchToken = tokens::touch,
        )
        // The plugin's two-minute heartbeat can keep a long-running session authorized.
        repeat(6) {
            now += 2 * 60 * 1000L
            assertEquals(true, request("serverinfo").values["ok"])
        }
        assertTrue(now > ApkPluginLaunchTokens.WINDOW_MS)
        assertEquals(true, request("resume", "appid=42").values["ok"])
        assertEquals("not_launched", request("resume", "appid=43").values["error"])
        assertEquals("not_launched", request("resume", "appid=42&appuuid=other").values["error"])
        now += ApkPluginLaunchTokens.WINDOW_MS
        assertEquals("not_launched", request("resume", "appid=42").values["error"])
    }

    @Test fun aWrongCallerOrHostCannotExtendTheWindow() = runBlocking {
        val other = plugin.copy(packageName = "other.plugin")
        for ((caller, host) in listOf(other.packageName to "host-uuid", plugin.packageName to "other-host")) {
            var now = 0L
            val tokens = ApkPluginLaunchTokens { now }
            val token = tokens.issue(plugin.packageName, "host-uuid", "42")
            now = ApkPluginLaunchTokens.WINDOW_MS - 1
            val reply = callPluginBroker(
                caller, "gamestream.request", host, "serverinfo", null, "test.host", listOf(plugin, other), { _, _ -> true },
                hosts = { emptyList() }, request = { _, _, _ -> 200 to "<root/>".toByteArray() },
                token = token, launchAllowed = tokens::allows, touchToken = tokens::touch,
            )
            assertEquals(true, reply.values["ok"])
            now++
            assertFalse(tokens.allows(plugin.packageName, "host-uuid", token, listOf("42")))
        }
    }

    @Test fun optionalInvalidTokensAreIgnoredAndCannotReviveAnExpiredStart() = runBlocking {
        var now = 0L
        val tokens = ApkPluginLaunchTokens { now }
        val expired = tokens.issue(plugin.packageName, "host-uuid", "42")
        now = ApkPluginLaunchTokens.WINDOW_MS
        var requested = 0
        for (path in listOf("serverinfo", "applist", "cancel")) {
            for (token in listOf(expired, "unknown", null)) {
                val reply = callPluginBroker(
                    plugin.packageName, "gamestream.request", "host-uuid", path, null, "test.host", listOf(plugin), { _, _ -> true },
                    hosts = { emptyList() }, request = { _, _, _ -> requested++; 200 to "<root/>".toByteArray() },
                    token = token, launchAllowed = tokens::allows, touchToken = tokens::touch,
                )
                assertEquals(true, reply.values["ok"])
            }
        }
        assertEquals(9, requested)
        assertFalse(tokens.touch(plugin.packageName, "host-uuid", expired))
        assertFalse(tokens.allows(plugin.packageName, "host-uuid", expired, listOf("42")))
    }

    @Test fun metadataAndCancelRequestsDoNotRequireAToken() {
        for (path in listOf("serverinfo", "applist", "cancel")) assertEquals(true, call(path = path).values["ok"])
    }

    @Test fun eachPlayStartGetsAnIndependentRandomMemoryOnlyToken() {
        val tokens = ApkPluginLaunchTokens { 1L }
        val first = tokens.issue(plugin.packageName, "host-uuid", "42")
        val resumed = tokens.issue(plugin.packageName, "host-uuid", "42")
        assertNotEquals(first, resumed)
        assertTrue(java.util.Base64.getUrlDecoder().decode(first).size >= 16)
        assertTrue(tokens.allows(plugin.packageName, "host-uuid", first, listOf("42")))
        assertTrue(tokens.allows(plugin.packageName, "host-uuid", resumed, listOf("42")))
        assertFalse(ApkPluginLaunchTokens { 1L }.allows(plugin.packageName, "host-uuid", first, listOf("42")))
    }

    @Test fun helloDoesNotWaitForApplicationInitializationAndDataCallsWaitBeforeAccess() = runBlocking {
        val ready = kotlinx.coroutines.CompletableDeferred<Unit>()
        val plugins = listOf(plugin)
        suspend fun ask(method: String, wait: suspend () -> Boolean): BrokerReply = callPluginBroker(
            plugin.packageName, method, "host-uuid", "serverinfo", null, "test.host", plugins, { _, _ -> true },
            hosts = { check(ready.isCompleted); emptyList() },
            request = { _, _, _ -> check(ready.isCompleted); 200 to "<root/>".toByteArray() },
            awaitReady = wait,
        )
        assertEquals(true, ask("hello") { error("hello touched startup state") }.values["ok"])
        assertEquals("internal", ask("gamestream.hosts") { awaitApkPluginBrokerReady(ready, 1) }.values["error"])
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            ask("gamestream.hosts") { awaitApkPluginBrokerReady(ready) }
        }
        assertFalse(waiting.isCompleted)
        ready.complete(Unit)
        assertEquals(true, waiting.await().values["ok"])
        assertEquals(true, ask("gamestream.request") { awaitApkPluginBrokerReady(ready) }.values["ok"])
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

    @Test fun moonlightGamesRouteToTheOnePluginMostRecentlyChosen() {
        val grants = ApkPluginGrants(MemoryPrefs())
        val release = testApkPlugin(packageName = "app.foldcade.moonlight")
        val debug = testApkPlugin(packageName = "app.foldcade.moonlight.debug")
        grants.setGranted(release, "gamestream", true)
        assertTrue(grants.routes(release))
        // Granting a second plugin makes it the one, and the first switch reads off.
        grants.setGranted(debug, "gamestream", true)
        assertTrue(grants.routes(debug))
        assertFalse(grants.routes(release))
        assertTrue(grants.granted(release, "gamestream"))
        // Turning the first back on takes routing back.
        grants.setRouting(release, true)
        assertTrue(grants.routes(release))
        assertFalse(grants.routes(debug))
        // A plugin for another platform can be granted without taking Moonlight games away.
        val other = testApkPlugin(packageName = "example.other").copy(platform = "other")
        grants.setGranted(other, "gamestream", true)
        grants.setRouting(other, true)
        assertTrue(grants.routes(release))
        // Turning one off leaves none routing; nothing else switches on by itself.
        grants.setRouting(release, false)
        assertFalse(grants.routes(release))
        assertFalse(grants.routes(debug))
    }
}
