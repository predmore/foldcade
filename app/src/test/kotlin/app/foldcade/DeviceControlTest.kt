package app.foldcade

import app.foldcade.language.FanTier
import app.foldcade.language.HostScreen
import app.foldcade.language.ScreenLayout
import app.foldcade.language.wantedScreens
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
 * The reconciler against a fake plugin, through the races reviews reproduced: a stuck call, a
 * cancelled start, a release behind a call in flight, a stale refresh, a game starting during a
 * fan choice, and a late publication. Every check waits for the worker to settle ([DeviceControl.awaitIdle]),
 * never for wall-clock time, except to hold a call open.
 */
class DeviceControlTest {
    private class Plugin : DeviceTransport {
        @Volatile var screens = "top"
        @Volatile var game: String? = null
        @Volatile var blocking: String? = null
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val calls = CopyOnWriteArrayList<String>()
        val returned = AtomicInteger()

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
            returned.incrementAndGet()
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
    private val shown = CopyOnWriteArrayList<String?>()

    /** The games on the screens, the layout, and the panels the session's apps hold. */
    private val live = AtomicReference<Map<String, FanTier?>>(emptyMap())
    private val layout = AtomicReference(ScreenLayout.TopOnly)
    private val held = AtomicReference<Set<HostScreen>>(emptySet())

    private fun control(releaseMs: Long = 8_000) = DeviceControl(
        transport = plugin, scope = scope, onState = { shown += it?.packageName }, main = main,
        wantedScreens = { taking -> wantedScreens(layout.get(), held.get() + taking) },
        liveFans = { live.get() },
        releaseTimeoutMs = releaseMs,
    )

    private suspend fun onMain(block: () -> Unit) = withContext(main) { block() }

    private suspend fun blocked() = check(withContext(Dispatchers.IO) { plugin.entered.await(5, TimeUnit.SECONDS) }) { "the call never came" }

    private suspend fun DeviceControl.started() {
        onMain { use(thor) }
        withTimeout(10_000) { awaitIdle() }
    }

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
        onMain { control.setFan(FanTier.Quiet) }
        control.reconcile()
        onMain { control.onHomeResumed() }
        control.release("test.thor")
        control.awaitIdle()
        assertEquals(emptyList<String>(), plugin.calls)
        assertNull(control.state)
    }

    @Test fun aStuckCallDelaysTheLastGamesEndButNeverDropsIt() = runBlocking {
        plugin.screens = "dual"
        live.set(mapOf("kart" to FanTier.Performance))
        held.set(setOf(HostScreen.Bottom))
        val control = control()
        control.started()
        assertEquals("performance", plugin.game)
        plugin.blocking = "brightness.set"
        control.setBottomBrightness(0.4f)
        blocked()
        // The game leaves while the worker is stuck. Every signal meanwhile is kept, not dropped.
        live.set(emptyMap())
        held.set(emptySet())
        control.reconcile()
        onMain { control.onHomeResumed() }
        plugin.unblock.countDown()
        control.awaitIdle()
        assertNull(plugin.game)
        assertEquals("top", plugin.screens)
    }

    @Test fun aCancelledStartNeverLeavesAPanelLit() = runBlocking {
        val control = control()
        control.started()
        plugin.blocking = "fan.set"
        onMain { control.setFan(FanTier.Quiet) }
        blocked()
        assertTrue(control.mustLight(setOf(HostScreen.Bottom)))
        val start = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            control.whenLit(setOf(HostScreen.Bottom)) { error("a cancelled start must not run") }
        }
        start.cancelAndJoin()
        plugin.unblock.countDown()
        control.awaitIdle()
        assertEquals("top", plugin.screens)
        assertFalse(plugin.calls.any { it == "screens.set:dual" })
    }

    @Test fun aStartWaitsForItsPanelThenRuns() = runBlocking {
        val control = control()
        control.started()
        val lit = control.whenLit(setOf(HostScreen.Bottom)) { plugin.screens }
        assertEquals("dual", lit)
        // Once the start is over and nothing holds the panel, it goes dark again.
        control.awaitIdle()
        assertEquals("top", plugin.screens)
    }

    @Test fun releaseWaitsForTheCallInFlightThenCleansUp() = runBlocking {
        // Whatever the worker is in when consent goes, release returns only after it, with both panels on and no override.
        for (method in listOf("screens.set", "game.begin", "state", "brightness.set")) {
            val plugin = Plugin()
            plugin.screens = if (method == "screens.set") "dual" else "bottom"
            plugin.game = if (method == "state") "performance" else null
            val games = if (method == "game.begin" || method == "brightness.set") mapOf("kart" to FanTier.Performance) else emptyMap()
            val control = DeviceControl(
                transport = plugin, scope = scope, onState = {}, main = main,
                wantedScreens = { if (method == "screens.set") app.foldcade.language.DeviceScreens.Top else app.foldcade.language.DeviceScreens.Bottom },
                liveFans = { games }, releaseTimeoutMs = 8_000,
            )
            plugin.blocking = method
            onMain { control.use(thor) }
            if (method == "brightness.set") {
                control.awaitIdle()
                control.setBottomBrightness(0.4f)
            }
            check(withContext(Dispatchers.IO) { plugin.entered.await(5, TimeUnit.SECONDS) }) { method }
            val release = scope.async { control.release("test.thor") }
            delay(100)
            assertFalse(method, release.isCompleted)
            plugin.unblock.countDown()
            release.await()
            assertNull(method, control.pluginPackage)
            assertEquals(method, "dual", plugin.screens)
            assertNull(method, plugin.game)
            // Nothing calls it afterwards, whatever is signalled.
            val before = plugin.calls.size
            control.reconcile()
            onMain { control.onHomeResumed() }
            control.awaitIdle()
            assertEquals(method, before, plugin.calls.size)
        }
    }

    @Test fun aStaleRefreshCantUndoARelease() = runBlocking {
        plugin.screens = "bottom"
        layout.set(ScreenLayout.BottomOnly)
        live.set(mapOf("kart" to FanTier.Performance))
        val control = control(releaseMs = 300)
        control.started()
        assertEquals("performance", plugin.game)
        plugin.blocking = "state"
        control.check()
        blocked()
        // Stuck past the bound: it is let go untouched.
        control.release("test.thor")
        assertNull(control.pluginPackage)
        // A refresh computed before the consent went names it again: it stays let go.
        onMain { control.use(thor) }
        assertNull(control.pluginPackage)
        val before = plugin.calls.size
        plugin.unblock.countDown()
        control.awaitIdle()
        assertEquals(before, plugin.calls.size)
        // Granted anew, it is used again.
        onMain {
            control.granted("test.thor")
            control.use(thor)
        }
        assertEquals("test.thor", control.pluginPackage)
        control.awaitIdle()
        assertTrue(plugin.calls.size > before)
    }

    @Test fun theHighestTierOfTheGamesOnScreenHolds() = runBlocking {
        plugin.screens = "dual"
        layout.set(ScreenLayout.ShowcaseTop)
        live.set(mapOf("kart" to FanTier.Quiet))
        val control = control()
        control.started()
        assertEquals("quiet", plugin.game)
        suspend fun games(now: Map<String, FanTier?>): String? {
            live.set(now)
            control.reconcile()
            control.awaitIdle()
            return plugin.game
        }
        // A second game on the other screen with a higher tier takes over; the first keeps running.
        assertEquals("performance", games(mapOf("kart" to FanTier.Quiet, "zelda" to FanTier.Performance)))
        assertEquals("quiet", games(mapOf("kart" to FanTier.Quiet)))
        assertNull(games(emptyMap()))
        // The player taking the fan during a game keeps their tier until another game starts.
        assertEquals("quiet", games(mapOf("kart" to FanTier.Quiet)))
        onMain { control.setFan(FanTier.Performance) }
        control.awaitIdle()
        assertNull(plugin.game)
        assertNull(games(mapOf("kart" to FanTier.Quiet)))
        assertEquals("balanced", games(mapOf("kart" to FanTier.Quiet, "zelda" to FanTier.Balanced)))
    }

    @Test fun aGameStartingDuringAFanChoiceKeepsItsTier() = runBlocking {
        plugin.screens = "dual"
        layout.set(ScreenLayout.ShowcaseTop)
        live.set(mapOf("kart" to FanTier.Quiet))
        val control = control()
        control.started()
        assertEquals("quiet", plugin.game)
        plugin.blocking = "fan.set"
        onMain { control.setFan(FanTier.Performance) }
        blocked()
        // Zelda starts while the choice is still in the plugin: the choice was not for it.
        live.set(mapOf("kart" to FanTier.Quiet, "zelda" to FanTier.Balanced))
        control.reconcile()
        plugin.unblock.countDown()
        control.awaitIdle()
        assertEquals("balanced", plugin.game)
    }

    @Test fun aLatePublicationCantBringBackControlsAlreadyGone() = runBlocking {
        val control = control()
        control.started()
        assertEquals("test.thor", shown.last())
        // The worker answers a read while the main thread is busy; consent goes before its state is shown.
        val busy = CountDownLatch(1)
        val blocker = scope.launch(main) {
            busy.await(5, TimeUnit.SECONDS)
            control.use(null)
        }
        val returned = plugin.returned.get()
        control.check()
        withTimeout(5_000) { while (plugin.returned.get() == returned) delay(1) }
        delay(50)
        busy.countDown()
        blocker.join()
        control.awaitIdle()
        withContext(main) { }
        assertNull(shown.last())
        assertNull(control.state)
    }
}
