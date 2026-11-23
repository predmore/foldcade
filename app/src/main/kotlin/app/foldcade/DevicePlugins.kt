package app.foldcade

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import app.foldcade.language.DEVICE_SCOPE
import app.foldcade.language.DeviceCapability
import app.foldcade.language.DeviceReply
import app.foldcade.language.DeviceRoot
import app.foldcade.language.DeviceScreens
import app.foldcade.language.DeviceState
import app.foldcade.language.FanTier
import app.foldcade.language.GameFanCall
import app.foldcade.language.HostScreen
import app.foldcade.language.StickLights
import app.foldcade.language.gameFanCall
import app.foldcade.language.lights
import app.foldcade.language.parseDeviceReply
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/** Device protocol 1, see docs/device-plugins.md. Foldcade calls the plugin; it never holds root itself. */
internal object DevicePluginProtocol {
    const val VERSION = 1
    const val ACTION = "app.foldcade.plugin.action.DEVICE"

    /** A device plugin's provider authority is its package name plus this. */
    const val AUTHORITY_SUFFIX = ".device"
}

/**
 * Every installed device plugin: an exported, enabled provider with the DEVICE action, the
 * contract's authority, and a usable PROTOCOL. Its label and version are its package's.
 */
internal fun discoverDevicePlugins(pm: PackageManager): List<ApkPlugin> =
    pm.queryIntentContentProviders(Intent(DevicePluginProtocol.ACTION), PackageManager.GET_META_DATA).mapNotNull { resolved ->
        val provider = resolved.providerInfo ?: return@mapNotNull null
        if (!provider.exported || !provider.enabled || !provider.applicationInfo.enabled) return@mapNotNull null
        // The contract names the authority. Any other would be a provider Foldcade never meant to call.
        val authority = provider.packageName + DevicePluginProtocol.AUTHORITY_SUFFIX
        if (provider.authority?.split(';')?.contains(authority) != true) return@mapNotNull null
        val meta = provider.metaData ?: return@mapNotNull null
        @Suppress("DEPRECATION")
        val protocol = meta.get(ApkPluginProtocol.META + "PROTOCOL")?.toString()?.toIntOrNull()?.takeIf { it >= 1 }
            ?: return@mapNotNull null
        val info = try {
            pm.getPackageInfo(provider.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (_: PackageManager.NameNotFoundException) {
            return@mapNotNull null
        }
        val signers = info.signerDigests().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        ApkPlugin(
            packageName = provider.packageName,
            activity = provider.name,
            label = provider.applicationInfo.loadLabel(pm).toString(),
            version = app.foldcade.language.AboutCopy.version(info.versionName.orEmpty(), info.longVersionCode),
            signers = signers,
            protocol = minOf(protocol, DevicePluginProtocol.VERSION),
            platform = "",
            bothScreens = false,
            scopes = setOf(DEVICE_SCOPE),
            authority = authority,
            device = meta.getString(ApkPluginProtocol.META + "DEVICE").orEmpty(),
        )
    }.sortedBy { it.packageName }.distinctBy { it.packageName }

/** One blocking call to a device plugin's provider: its reply's keys and values, or null when it threw. */
internal fun interface DeviceTransport {
    fun call(authority: String, method: String, arg: String?, extras: Map<String, Any?>): Map<String, Any?>?
}

/** The real transport: `ContentResolver.call`. */
internal class ProviderTransport(private val context: Context) : DeviceTransport {
    override fun call(authority: String, method: String, arg: String?, extras: Map<String, Any?>): Map<String, Any?>? {
        val bundle = if (extras.isEmpty()) null else Bundle().apply {
            extras.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                }
            }
        }
        return context.contentResolver.call(authority, method, arg, bundle)?.let(::bundleValues)
    }
}

/**
 * Keeps one authorized device plugin where Foldcade wants the device. It is level-triggered:
 * Foldcade states what it wants (the panels, the games' fan, and the newest fan tier, lights mode
 * and bottom level the player chose), and one worker thread sends whatever differs from what the
 * plugin last confirmed, a call at a time, until nothing does. A signal (a change, Home resuming, a
 * display turning on) wakes it. Nothing is dropped: a call that never returns only keeps the worker
 * busy, and when it returns the worker reads what is wanted now, so it never sends a stale value. A
 * refused call waits for the next signal rather than retrying in a loop.
 *
 * Authorization is a generation. Letting a plugin go ([release]) first wants both panels on and
 * no game override, waits a bound for the plugin to confirm that, then moves the generation on;
 * the worker checks it before every call, so nothing reaches a plugin let go. Only a new grant
 * ([granted]) lets the same package be used again.
 *
 * Without an authorized plugin nothing waits and nothing is sent.
 */
internal class DeviceControl(
    private val transport: DeviceTransport,
    private val scope: CoroutineScope,
    /** Called on [main] with every state to show, or null once no plugin is in use. */
    private val onState: (DeviceState?) -> Unit,
    /** The panels Foldcade wants powered, given the panels starts are waiting to take. Read on [main]. */
    private val wantedScreens: (Set<HostScreen>) -> DeviceScreens,
    /** The games on the screens now, by id, each with its own tier or null for Default. Read on [main]. */
    private val liveFans: () -> Map<String, FanTier?>,
    /** True when AYN's RGB lighting screen can be opened, to set a stick color. */
    private val colorSettings: () -> Boolean = { false },
    /** True when Magisk can be opened, to grant the plugin root. */
    private val rootSettings: () -> Boolean = { false },
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    worker: CoroutineDispatcher = Executors.newSingleThreadExecutor { Thread(it, "FoldcadeDevice").apply { isDaemon = true } }.asCoroutineDispatcher(),
    private val lightTimeoutMs: Long = LIGHT_TIMEOUT_MS,
    private val releaseTimeoutMs: Long = RELEASE_TIMEOUT_MS,
) {
    @Volatile private var target: ApkPlugin? = null
    @Volatile private var generation = 0

    /** A package let go before its consent went. Only [granted] lets it be used again. */
    @Volatile private var revoked: String? = null

    /** The package being let go: the worker wants both panels on and no game override for it. */
    @Volatile private var releasing: String? = null

    /** Completed by the worker once it has nothing more to do for [releasing], any call in flight included. */
    @Volatile private var releaseDone: CompletableDeferred<Unit>? = null

    /** For [awaitIdle]: each pass takes an id as it starts, and [settled] holds the id of the last one finished. */
    private val passes = AtomicInteger(0)
    private val settled = MutableStateFlow(0)

    /** The plugin's last confirmed state. Waiters watch it. */
    private val confirmed = MutableStateFlow<DeviceState?>(null)

    /** What is shown: [confirmed], with what Foldcade knows of the device and the choices on their way. */
    @Volatile var state: DeviceState? = null
        private set

    /** The package of the plugin in use, or null. */
    val pluginPackage: String? get() = target?.packageName

    private val signals = Channel<Unit>(Channel.CONFLATED)

    /** A read the worker owes: none, `state`, or `state` probing again ([PROBE]). */
    private val readWanted = AtomicInteger(NONE)

    // The player's newest choices, until the plugin answers them.
    private val fanWrite = AtomicReference<FanTier?>(null)

    /** The games on screen when the player chose [fanWrite]: the ones that keep the player's tier. */
    private val fanWriteGames = AtomicReference<Map<String, FanTier?>>(emptyMap())
    private val lightsWrite = AtomicReference<StickLights?>(null)
    private val bottomWrite = AtomicReference<Float?>(null)

    /** Starts waiting for a dark panel to light, by token: the panels each will take. */
    private val pendingLit = ConcurrentHashMap<Any, Set<HostScreen>>()

    /** The last `lights.set` was refused for want of a stick color ([DeviceState.lightsNeedColor]). */
    private val lightsNeedColor = AtomicBoolean(false)

    /** The player chose a tier during a game's override, so the games then on screen keep it. Cleared with each generation. */
    @Volatile private var fanTaken = false
    /** The games the player's fan choice covers, with the tier each had when it was made or last seen. */
    @Volatile private var fanGames: Map<String, FanTier?> = emptyMap()

    private val displaysOn = HashMap<Int, Boolean>()

    init {
        scope.launch(worker) {
            for (signal in signals) {
                val pass = passes.incrementAndGet()
                // Only a settle that began after the release was asked for may answer it: it saw the cleanup wanted.
                val release = releaseDone?.takeIf { releasing != null }
                try {
                    settle()
                    // A release isn't answered until its cleanup is confirmed: a refused or failed call is
                    // tried again shortly, until it goes through or the release gives up and lets go.
                    while (release != null && !release.isCompleted && releasing != null && target != null && !cleanedUp()) {
                        delay(CLEANUP_RETRY_MS)
                        settle()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.w(TAG, "Device plugin reconcile failed", failure)
                } finally {
                    // Whatever was in flight has returned and the cleanup has gone as far as it can.
                    release?.complete(Unit)
                    settled.value = pass
                }
            }
        }
    }

    private fun signal() {
        signals.trySend(Unit)
    }

    private fun owe(read: Int) {
        readWanted.accumulateAndGet(read, ::maxOf)
    }

    /**
     * The device plugin the grants name now, or null. Called on the main thread. A new plugin, or
     * a new build of it, starts a new generation and is asked to probe its capabilities. A package
     * let go stays let go until [granted].
     */
    fun use(next: ApkPlugin?) {
        if (next != null && next.packageName == revoked) return
        val was = target
        if (next == null) {
            if (was != null) detach()
            return
        }
        if (was?.packageName == next.packageName && was.signers == next.signers && was.version == next.version && was.authority == next.authority) return
        generation++
        target = next
        reset()
        owe(PROBE)
        signal()
    }

    /** The player granted [packageName] the device scope: it may be used again. */
    fun granted(packageName: String) {
        if (revoked == packageName) revoked = null
    }

    /** Main thread: the games on screen now are the ones the choice is for. */
    fun setFan(tier: FanTier) {
        fanWriteGames.set(liveFans())
        fanWrite.set(tier)
        signal()
    }

    fun setLights(mode: StickLights) {
        lightsWrite.set(mode)
        signal()
    }

    fun setBottomBrightness(level: Float) {
        bottomWrite.set(level)
        signal()
    }

    /** Asks again, probing what the plugin can do now, as after root was granted. Stick colors may have changed. */
    fun check() {
        if (target == null) return
        lightsNeedColor.set(false)
        owe(PROBE)
        signal()
    }

    /** The screens changed hands, the layout changed, or a game's tier did. */
    fun reconcile() {
        if (target != null) signal()
    }

    /**
     * Home is in front again: read the device, which the device itself, AYN's or Magisk's screens
     * may have changed meanwhile, and put it back where Foldcade wants it.
     */
    fun onHomeResumed() {
        if (target == null) return
        // Back from AYN's RGB lighting, or anywhere: the stick colors may have changed.
        if (lightsNeedColor.getAndSet(false)) publishNow()
        owe(if (confirmed.value?.root != DeviceRoot.Granted) PROBE else READ)
        signal()
    }

    /**
     * True when a start that takes [panels] must wait for one to light. Main thread. Before the
     * plugin has said what it can do, a panel the layout leaves dark may be off, so that waits too.
     */
    fun mustLight(panels: Set<HostScreen>): Boolean {
        if (target == null) return false
        val known = confirmed.value ?: return !wantedScreens(emptySet()).lights(panels)
        val current = known.takeIf { it.has(DeviceCapability.Screens) }?.screens ?: return false
        return !current.lights(panels)
    }

    /**
     * Runs [start] once [panels] are lit, waiting at most [LIGHT_TIMEOUT_MS]: a start that would
     * have to wait longer goes ahead anyway. The panels stay wanted until [start] returns, and stop
     * being wanted if the wait is cancelled, so a cancelled start never leaves a panel lit.
     */
    suspend fun <T> whenLit(panels: Set<HostScreen>, start: suspend () -> T): T {
        if (!mustLight(panels)) return start()
        val token = Any()
        pendingLit[token] = panels
        signal()
        try {
            withTimeoutOrNull(lightTimeoutMs) {
                // Lit, or known to be none of the plugin's doing, or no plugin any more. Not yet known is no answer.
                confirmed.first { target == null || (it != null && (!it.has(DeviceCapability.Screens) || it.screens?.lights(panels) == true)) }
            }
            return start()
        } finally {
            pendingLit.remove(token)
            signal()
        }
    }

    /**
     * Lets [packageName] go before its consent goes, or before another plugin is granted: both
     * panels on and any game override ended, confirmed within [RELEASE_TIMEOUT_MS], then the
     * generation moves on and nothing calls it again.
     */
    suspend fun release(packageName: String) {
        if (target?.packageName != packageName) return
        val done = CompletableDeferred<Unit>()
        releaseDone = done
        releasing = packageName
        signal()
        // The worker answers once the call it is in has returned and nothing more differs.
        withTimeoutOrNull(releaseTimeoutMs) { done.await() }
        withContext(main) {
            if (target?.packageName == packageName) {
                revoked = packageName
                detach()
            }
        }
        releasing = null
        releaseDone = null
    }

    /** Tests: returns once the worker has settled everything signalled before this call. */
    internal suspend fun awaitIdle() {
        // A pass that starts after this read sees everything that happened before the call.
        val before = passes.get()
        signal()
        settled.first { it > before }
    }

    /** Wakes light both panels. Listen for a display turning on, and put the dark one back. */
    fun watchDisplays(context: Context) {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return
        val handler = Handler(Looper.getMainLooper())
        manager.displays.forEach { displaysOn[it.displayId] = it.state == Display.STATE_ON }
        manager.registerDisplayListener(object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = onDisplayChanged(displayId)
            override fun onDisplayRemoved(displayId: Int) {
                displaysOn.remove(displayId)
            }
            override fun onDisplayChanged(displayId: Int) {
                val on = manager.getDisplay(displayId)?.state == Display.STATE_ON
                val was = displaysOn.put(displayId, on)
                if (on && was == false && state?.has(DeviceCapability.Screens) == true) onDisplayOn()
            }
        }, handler)
    }

    /**
     * The device lights its panels as it wakes, possibly a moment after Android reports the
     * display on, so the panels are read and set now and once more shortly after.
     */
    private fun onDisplayOn() {
        owe(READ)
        signal()
        scope.launch {
            delay(WAKE_SETTLE_MS)
            owe(READ)
            signal()
        }
    }

    /** Both panels on and no game override, as confirmed by the plugin: what a release leaves behind. */
    private fun cleanedUp(): Boolean {
        val known = confirmed.value ?: return false
        return (!known.has(DeviceCapability.Screens) || known.screens == DeviceScreens.Dual) &&
            (!known.has(DeviceCapability.GameFan) || known.fanGame == null)
    }

    /** Main thread. */
    private fun detach() {
        generation++
        target = null
        reset()
        publishNow()
    }

    private fun reset() {
        confirmed.value = null
        readWanted.set(NONE)
        fanWrite.set(null)
        lightsWrite.set(null)
        bottomWrite.set(null)
        lightsNeedColor.set(false)
        // A plugin attached anew, or updated, owes the games on screen their own tiers again.
        fanTaken = false
        fanGames = emptyMap()
    }

    /** Steps toward what is wanted until nothing differs, a call fails, or the steps run out. */
    private suspend fun settle() {
        repeat(MAX_STEPS) { if (!step()) return }
    }

    /** One call toward what is wanted. False when nothing differs, or a call failed: then the next signal tries again. */
    private suspend fun step(): Boolean {
        val gen = generation
        val plugin = target ?: return false
        val read = readWanted.getAndSet(NONE)
        val known = confirmed.value
        if (read != NONE || known == null) {
            val reply = call(gen, plugin, "state", extras = if (read == PROBE || known == null) mapOf("refresh" to true) else emptyMap())
            if (reply?.state == null) return false
            return true
        }
        if (releasing == plugin.packageName) {
            // Both cleanup calls are tried on every pass, each on its own: one that keeps failing never
            // keeps the other from going out. The release retries until both are confirmed.
            if (known.has(DeviceCapability.Screens) && known.screens != DeviceScreens.Dual) call(gen, plugin, "screens.set", DeviceScreens.Dual.id)
            if ((confirmed.value ?: known).let { it.has(DeviceCapability.GameFan) && it.fanGame != null }) call(gen, plugin, "game.end")
            return false
        }
        writeStep(gen, plugin)?.let { return it }
        val wanted = withContext(main) { wantedScreens(pendingLit.values.flatten().toSet()) to liveFans() }
        if (known.has(DeviceCapability.Screens) && known.screens != wanted.first) {
            return call(gen, plugin, "screens.set", wanted.first.id)?.ok == true
        }
        if (known.has(DeviceCapability.GameFan)) {
            val live = wanted.second
            // Only a newly arrived game with a tier of its own ends the player's choice. A notice's
            // app, or a game with no tier, opened beside the game leaves the player's tier in force.
            // A game's own Y › Fan changed while it runs is a choice too: it takes the fan back.
            if (live.any { (id, tier) -> tier != null && (id !in fanGames || fanGames[id] != tier) }) fanTaken = false
            fanGames = live
            if (fanTaken && live.values.any { it != null }) return false
            fanTaken = false
            when (val next = gameFanCall(known.fanGame, live.values)) {
                is GameFanCall.Begin -> return call(gen, plugin, "game.begin", extras = mapOf("fan" to next.tier.id))?.ok == true
                GameFanCall.End -> return call(gen, plugin, "game.end")?.ok == true
                null -> Unit
            }
        }
        return false
    }

    /**
     * The player's newest choice, if one waits: sent, and cleared once the plugin answered it. A
     * refused one is dropped and the device read again; one the plugin never got waits for the
     * next signal. Null when none waits.
     */
    private suspend fun writeStep(gen: Int, plugin: ApkPlugin): Boolean? {
        fanWrite.get()?.let { tier ->
            val reply = call(gen, plugin, "fan.set", tier.id) ?: return false
            return underGeneration(gen) {
                fanWrite.compareAndSet(tier, null)
                // The player's tier holds for the games on screen when they chose it, whether or not their
                // override had begun (fan.set ends one without restoring); one that starts later gets its own.
                if (reply.ok) {
                    fanTaken = true
                    fanGames = fanWriteGames.get()
                }
                if (!reply.ok) owe(READ)
            }
        }
        lightsWrite.get()?.let { mode ->
            val reply = call(gen, plugin, "lights.set", mode.id) ?: return false
            return underGeneration(gen) {
                lightsWrite.compareAndSet(mode, null)
                when {
                    reply.ok -> lightsNeedColor.set(false)
                    reply.error == NEEDS_COLOR -> lightsNeedColor.set(true)
                }
                if (!reply.ok) owe(READ)
            }
        }
        val bottom: Float? = bottomWrite.get()
        if (bottom != null) {
            val level: Float = bottom
            val reply = call(gen, plugin, "brightness.set", "bottom", mapOf("level" to level)) ?: return false
            return underGeneration(gen) {
                // The very box stored, so a newer level set meanwhile stays.
                bottomWrite.compareAndSet(bottom, null)
                if (reply.ok) {
                    val applied = (reply.values["level"] as? Number)?.toFloat() ?: level
                    confirmed.value = confirmed.value?.copy(brightnessBottom = applied)
                } else owe(READ)
            }
        }
        return null
    }

    /**
     * One call, if [gen] is still the generation and [plugin] not let go. A reply arriving after
     * the generation moved on means nothing. The reply's state bundle, when it has one, is confirmed.
     */
    private suspend fun call(gen: Int, plugin: ApkPlugin, method: String, arg: String? = null, extras: Map<String, Any?> = emptyMap()): DeviceReply? {
        val authority = plugin.authority ?: return null
        if (gen != generation || plugin.packageName == revoked) return null
        val values = try {
            transport.call(authority, method, arg, extras)
        } catch (failure: Exception) {
            Log.w(TAG, "Device plugin $method failed", failure)
            null
        } ?: return null
        val reply = parseDeviceReply(plugin.packageName, values)
        if (!reply.ok) Log.w(TAG, "Device plugin $method: ${reply.error} ${values["message"] ?: ""}")
        // A reply from a plugin let go, or taken up anew, since the call began means nothing now.
        if (!underGeneration(gen) { reply.state?.let { confirmed.value = it } }) return null
        return reply
    }

    /**
     * Runs [block] on the main thread, where [detach] runs, only if [gen] is still the generation,
     * then shows [confirmed] as it stands. So a late reply can neither change what is kept nor bring
     * back controls already gone. False when the generation moved on.
     */
    private suspend fun underGeneration(gen: Int, block: () -> Unit): Boolean = withContext(main) {
        if (gen != generation) return@withContext false
        block()
        publishNow()
        true
    }

    /** Main thread. */
    private fun publishNow() {
        val next = confirmed.value?.let(::annotated)
        state = next
        onState(next)
    }

    private fun annotated(next: DeviceState): DeviceState {
        val needsColor = lightsNeedColor.get()
        var shown = next.copy(lightsNeedColor = needsColor, colorSettings = needsColor && colorSettings(),
            rootSettings = next.root != DeviceRoot.Granted && rootSettings())
        fanWrite.get()?.takeIf { shown.has(DeviceCapability.Fan) }?.let { shown = shown.copy(fan = it, fanBaseline = it, fanGame = null) }
        lightsWrite.get()?.takeIf { shown.has(DeviceCapability.Lights) }?.let { shown = shown.copy(lights = it) }
        bottomWrite.get()?.takeIf { shown.has(DeviceCapability.Brightness) }?.let { shown = shown.copy(brightnessBottom = it) }
        return shown
    }

    internal companion object {
        const val TAG = "FoldcadeDevice"

        private const val NONE = 0
        private const val READ = 1
        private const val PROBE = 2

        /** Calls one signal may make before it waits for the next: a plugin that never converges can't spin. */
        private const val MAX_STEPS = 16

        /** How long a start waits for its panel to light. */
        const val LIGHT_TIMEOUT_MS = 2_500L
        const val RELEASE_TIMEOUT_MS = 8_000L
        private const val WAKE_SETTLE_MS = 1_500L

        /** Between tries of a release's cleanup that the plugin refused or failed. */
        private const val CLEANUP_RETRY_MS = 250L

        /** `lights.set` refused Charging or Breathe because every stick color is black. */
        private const val NEEDS_COLOR = "needs_color"
    }
}

@Suppress("DEPRECATION")
private fun bundleValues(bundle: Bundle): Map<String, Any?> = bundle.keySet().associateWith { bundle.get(it) }
