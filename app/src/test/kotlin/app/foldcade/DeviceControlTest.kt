package app.foldcade

import app.foldcade.language.DeviceScreens
import app.foldcade.language.FanTier
import app.foldcade.language.HostScreen
import app.foldcade.language.wantedScreens
import app.foldcade.language.ScreenLayout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reconciler against a fake plugin. The races a reviewer reproduced against the queue it
 * replaced are here as tests: a stuck call, a cancelled start, a release, and a stale refresh.
 */
class DeviceControlTest {
    private class Plugin : DeviceTransport {
        @Volatile var screens = "top"
        @Volatile var game: String? = null
        @Volatile var blocking: String? = null
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val calls = CopyOnWriteArrayList<String>()

        override fun call(authority: String, method: String, arg: String?, extras: Map<String, Any?>): Map<String, Any?> {
            calls += "$method:${arg ?: extras["fan"] ?: ""}"
            if (method == blocking) {
                blocking = null
                entered.countDown()
                check(unblock.await(10, TimeUnit.SECONDS))
            }
            when (method) {
                "screens.set" -> screens = arg!!
                "game.begin" -> game = extras["fan"] as String
                "game.end", "fan.set" -> game = null
            }
            if (method == "brightness.set") return mapOf("ok" to true, "level" to extras["level"])
            return mapOf(
                "ok" to true, "protocol" to 1, "device" to "ayn-thor", "root" to "granted",
                "capabilities" to arrayOf("fan", "gameFan", "screens", "brightness"),
                "fan" to (game ?: "balanced"), "fanBaseline" to "balanced", "fanGame" to game,
                "screens" to screens, "brightnessTop" to 0.5f, "brightnessBottom" to 0.5f,
            )
        }
    }

    private val thor = ApkPlugin("test.thor", "provider", "Test", "1", setOf("a".repeat(64)), 1, "", false, setOf("device"), "test.thor.device", "ayn-thor")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val plugin = Plugin()

    /** The games on the screens, and the layout. */
    private val live = AtomicReference<Map<String, FanTier?>>(emptyMap())
    private val layout = AtomicReference(ScreenLayout.TopOnly)
    private val held = AtomicReference<Set<HostScreen>>(emptySet())

    private fun control(lightMs: Long = 2_500, releaseMs: Long = 8_000) = DeviceControl(
        transport = plugin, scope = scope, onState = {}, main = main,
        wantedScreens = { taking -> wantedScreens(layout.get(), held.get() + taking) },
        liveFans = { live.get() },
        lightTimeoutMs = lightMs, releaseTimeoutMs = releaseMs,
    )

    private suspend fun onMain(block: () -> Unit) = withContext(main) { block() }

    private suspend fun waitUntil(test: () -> Boolean) = try { withTimeout(4_000) { while (!test()) delay(5) } } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) { throw AssertionError("Timed out; calls: ${plugin.calls}", timeout) }

    @After fun done() {
        plugin.unblock.countDown()
        scope.cancel()
        main.close()
    }

    @Test fun withoutAPluginNothingWaitsAndNothingIsSent() = runBlocking {
        val control = control()
        assertFalse(control.mustLight(setOf(HostScreen.Bottom)))
        var started = false
        control.whenLit(setOf(HostScreen.Bottom)) { started = true }
        assertTrue(started)
        control.setFan(FanTier.Quiet)
        control.reconcile()
        control.onHomeResumed()
        control.release("test.thor")
        delay(100)
        assertEquals(emptyList<String>(), plugin.calls)
        assertNull(control.state)
    }

    @Test fun aStuckCallDelaysTheLastGamesEndButNeverDropsIt() = runBlocking {
        plugin.screens = "dual"
        live.set(mapOf("kart" to FanTier.Performance))
        held.set(setOf(HostScreen.Bottom))
        val control = control()
        onMain { control.use(thor) }
        waitUntil { plugin.game == "performance" }
        plugin.blocking = "brightness.set"
        control.setBottomBrightness(0.4f)
        withContext(Dispatchers.IO) { plugin.entered.await() }
        // The game leaves while the worker is stuck. Every signal meanwhile is kept, not dropped.
        live.set(emptyMap())
        held.set(emptySet())
        control.reconcile()
        onMain { control.onHomeResumed() }
        delay(200)
        plugin.unblock.countDown()
        waitUntil { plugin.game == null && plugin.screens == "top" }
    }

    @Test fun aCancelledStartNeverLeavesAPanelLit() = runBlocking {
        val control = control()
        onMain { control.use(thor) }
        waitUntil { control.state != null }
        plugin.blocking = "fan.set"
        control.setFan(FanTier.Quiet)
        withContext(Dispatchers.IO) { plugin.entered.await() }
        assertTrue(control.mustLight(setOf(HostScreen.Bottom)))
        val start = scope.launch { control.whenLit(setOf(HostScreen.Bottom)) { error("a cancelled start must not run") } }
        delay(100)
        start.cancelAndJoin()
        plugin.unblock.countDown()
        delay(300)
        assertEquals("top", plugin.screens)
        assertFalse(plugin.calls.any { it == "screens.set:dual" })
    }

    @Test fun aStartWaitsForItsPanelThenRuns() = runBlocking {
        val control = control()
        onMain { control.use(thor) }
        waitUntil { control.state != null }
        val lit = control.whenLit(setOf(HostScreen.Bottom)) { plugin.screens }
        assertEquals("dual", lit)
        // Once the start is over and nothing holds the panel, it goes dark again.
        waitUntil { plugin.screens == "top" }
    }

    @Test fun releaseCleansUpBeforeLettingGoEvenBehindAStuckCall() = runBlocking {
        plugin.screens = "bottom"
        layout.set(ScreenLayout.BottomOnly)
        live.set(mapOf("kart" to FanTier.Performance))
        val control = control()
        onMain { control.use(thor) }
        waitUntil { plugin.game == "performance" }
        plugin.blocking = "brightness.set"
        control.setBottomBrightness(0.4f)
        withContext(Dispatchers.IO) { plugin.entered.await() }
        val release = scope.async { control.release("test.thor") }
        delay(300)
        assertFalse(release.isCompleted)
        plugin.unblock.countDown()
        release.await()
        assertEquals("dual", plugin.screens)
        assertNull(plugin.game)
        assertNull(control.pluginPackage)
        // Nothing calls it afterwards, whatever is signalled.
        val before = plugin.calls.size
        control.reconcile()
        onMain { control.onHomeResumed() }
        delay(200)
        assertEquals(before, plugin.calls.size)
    }

    @Test fun aStaleRefreshCantUndoARelease() = runBlocking {
        plugin.screens = "bottom"
        layout.set(ScreenLayout.BottomOnly)
        live.set(mapOf("kart" to FanTier.Performance))
        val control = control(releaseMs = 300)
        onMain { control.use(thor) }
        waitUntil { plugin.game == "performance" }
        plugin.blocking = "state"
        control.check()
        withContext(Dispatchers.IO) { plugin.entered.await() }
        // Stuck past the bound: it is let go untouched.
        control.release("test.thor")
        assertNull(control.pluginPackage)
        // A refresh computed before the consent went names it again: it stays let go.
        onMain { control.use(thor) }
        assertNull(control.pluginPackage)
        val before = plugin.calls.size
        plugin.unblock.countDown()
        delay(300)
        assertEquals(before, plugin.calls.size)
        // Granted anew, it is used again.
        onMain {
            control.granted("test.thor")
            control.use(thor)
        }
        assertEquals("test.thor", control.pluginPackage)
        waitUntil { plugin.calls.size > before }
    }

    @Test fun theHighestTierOfTheGamesOnScreenHolds() = runBlocking {
        plugin.screens = "dual"
        layout.set(ScreenLayout.ShowcaseTop)
        live.set(mapOf("kart" to FanTier.Quiet))
        val control = control()
        onMain { control.use(thor) }
        waitUntil { plugin.game == "quiet" }
        // A second game on the other screen with a higher tier takes over; the first keeps running.
        live.set(mapOf("kart" to FanTier.Quiet, "zelda" to FanTier.Performance))
        control.reconcile()
        waitUntil { plugin.game == "performance" }
        live.set(mapOf("kart" to FanTier.Quiet))
        control.reconcile()
        waitUntil { plugin.game == "quiet" }
        live.set(emptyMap())
        control.reconcile()
        waitUntil { plugin.game == null }
        // The player taking the fan during a game keeps their tier until another game starts.
        live.set(mapOf("kart" to FanTier.Quiet))
        control.reconcile()
        waitUntil { plugin.game == "quiet" }
        control.setFan(FanTier.Performance)
        waitUntil { plugin.game == null && plugin.calls.last().startsWith("fan.set") }
        control.reconcile()
        delay(200)
        assertNull(plugin.game)
        live.set(mapOf("kart" to FanTier.Quiet, "zelda" to FanTier.Balanced))
        control.reconcile()
        waitUntil { plugin.game == "balanced" }
    }
}
