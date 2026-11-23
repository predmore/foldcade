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
import app.foldcade.language.DeviceScreens
import app.foldcade.language.DeviceState
import app.foldcade.language.FanTier
import app.foldcade.language.GameFanCall
import app.foldcade.language.StickLights
import app.foldcade.language.gameFanCall
import app.foldcade.language.parseDeviceReply
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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

/**
 * Calls one granted device plugin, one call at a time, in the order asked, on one thread of its
 * own. Every call is bounded by a timeout and nothing it does throws into the UI: a plugin that
 * fails, hangs or goes away leaves the last state shown, or none. While a call that timed out is
 * still stuck in the plugin, later calls are dropped rather than queued behind it. Replies
 * carrying the state bundle replace [state], which [onState] hands to the shell on the main thread.
 *
 * It also keeps the device where Foldcade wants it: the panels a one-screen layout leaves dark
 * ([reconcileScreens], [light]), and the highest fan tier among the games on the screens
 * ([reconcileFan]). The device lights both panels by itself on every wake, so a display turning
 * on, and Home coming back, apply the wanted panels again.
 */
internal class DeviceControl(
    private val context: Context,
    private val scope: CoroutineScope,
    /** Called on the main thread with every new state, or null once no plugin is in use. */
    private val onState: (DeviceState?) -> Unit,
    /** The panels Foldcade wants powered now. Read on the main thread. */
    private val wantedScreens: () -> DeviceScreens,
    /** The games on the screens now, by id, each with its own tier or null for Default. Read on the main thread. */
    private val liveFans: () -> Map<String, FanTier?>,
    /** True when AYN's RGB lighting screen can be opened, to set a stick colour. */
    private val colorSettings: () -> Boolean = { false },
) {
    private val jobs = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    @Volatile private var plugin: ApkPlugin? = null

    /** The package of the plugin in use, or null. */
    val pluginPackage: String? get() = plugin?.packageName

    /** The plugin's last state, or null without one in use. */
    @Volatile var state: DeviceState? = null
        private set

    /** The tier Foldcade last began for a game, until it ends. Read and written only by jobs. */
    private var appliedFan: FanTier? = null

    /** The games on the screens when the fan was last settled. A game not among them is new. */
    private var fanGames: Set<String> = emptySet()

    /** The player chose a tier during a game's override, so those games keep it until another game starts. */
    private var fanTaken = false

    /** The last `lights.set` was refused for want of a stick colour ([DeviceState.lightsNeedColor]). */
    private var lightsNeedColor = false

    /** A package whose consent went before its cleanup could run. Nothing calls it again until it is granted anew. */
    @Volatile private var revoked: String? = null
    private val callThread = Executors.newSingleThreadExecutor { Thread(it, "FoldcadeDevice") }.asCoroutineDispatcher()

    /** True while a call is in the plugin, including one that timed out and is still stuck there. */
    private val calling = AtomicBoolean(false)
    private val bottomPending = AtomicReference<Float?>(null)
    private val fanPending = AtomicReference<FanTier?>(null)
    private val lightsPending = AtomicReference<StickLights?>(null)
    private val screensQueued = AtomicBoolean(false)
    private val fanQueued = AtomicBoolean(false)
    private val displaysOn = HashMap<Int, Boolean>()

    init {
        scope.launch {
            for (job in jobs) {
                try {
                    job()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.w(TAG, "Device plugin call failed", failure)
                }
            }
        }
    }

    private fun enqueue(job: suspend () -> Unit) {
        jobs.trySend(job)
    }

    /**
     * The device plugin to use now: the granted one, or null. A new plugin, or a new build of it,
     * is asked to probe its capabilities; then the games' fan and the wanted panels are applied,
     * which also ends an override Foldcade lost track of.
     */
    fun use(next: ApkPlugin?) = enqueue {
        // Granted again: it may be called again.
        if (next != null && next.packageName == revoked) revoked = null
        val was = plugin
        if (next == null) {
            if (was != null || state != null) detach()
            return@enqueue
        }
        if (was?.packageName == next.packageName && was.signers == next.signers && was.version == next.version && was.authority == next.authority) return@enqueue
        plugin = next
        appliedFan = null
        fanGames = emptySet()
        fanTaken = false
        lightsNeedColor = false
        publish(read(refresh = true)?.state)
        settle()
    }

    /*
     * A held key sends only the newest value: steps that arrive while one is applied replace each
     * other, so the plugin never works through a backlog of root commands.
     */

    fun setFan(tier: FanTier) {
        if (fanPending.getAndSet(tier) != null) return
        enqueue {
            val next = fanPending.getAndSet(null) ?: return@enqueue
            val reply = call("fan.set", next.id)
            // fan.set ends a game's override without restoring: the games on screen keep the player's tier.
            if (reply?.ok == true && appliedFan != null) {
                appliedFan = null
                fanTaken = true
            }
            reply.orRead()
        }
    }

    fun setLights(mode: StickLights) {
        if (lightsPending.getAndSet(mode) != null) return
        enqueue {
            val next = lightsPending.getAndSet(null) ?: return@enqueue
            val reply = call("lights.set", next.id)
            val needsColor = when {
                reply?.ok == true -> false
                reply?.error == NEEDS_COLOR -> true
                else -> lightsNeedColor
            }
            if (needsColor != lightsNeedColor) {
                lightsNeedColor = needsColor
                state?.let { publish(it) }
            }
            reply.orRead()
        }
    }

    fun setBottomBrightness(level: Float) {
        if (bottomPending.getAndSet(level) != null) return
        enqueue {
            val next = bottomPending.getAndSet(null) ?: return@enqueue
            val reply = call("brightness.set", "bottom", Bundle().apply { putFloat("level", next) }, BRIGHTNESS_TIMEOUT_MS)
            if (reply?.ok != true) read()
        }
    }

    /** Asks again, probing what the plugin can do now, as after root was granted in Magisk. */
    fun check() = enqueue {
        read(refresh = true)
        settle()
    }

    /** The screens changed hands, or the layout changed: power the panels the session wants. */
    fun reconcileScreens() {
        if (!screensQueued.compareAndSet(false, true)) return
        enqueue {
            screensQueued.set(false)
            applyScreens(state)
        }
    }

    /** A game came onto a screen or left one, or a game's tier changed. */
    fun reconcileFan() {
        if (!fanQueued.compareAndSet(false, true)) return
        enqueue {
            fanQueued.set(false)
            applyFan()
        }
    }

    /**
     * Lights [wanted] before a game takes a dark panel, waiting at most [LIGHT_TIMEOUT_MS]: a
     * start that has to wait longer goes ahead anyway.
     */
    suspend fun light(wanted: DeviceScreens) {
        val done = CompletableDeferred<Unit>()
        enqueue {
            try {
                if (state?.screens != wanted) {
                    val reply = call("screens.set", wanted.id, timeoutMs = LIGHT_TIMEOUT_MS)
                    done.complete(Unit)
                    reply.orRead()
                }
            } finally {
                done.complete(Unit)
            }
        }
        withTimeoutOrNull(LIGHT_TIMEOUT_MS) { done.await() }
    }

    /**
     * Home is in front again. With no game on the screens that has a tier, any game override ends,
     * which also heals one a crash or force-stop of either app left behind. The panels are read
     * and set again.
     */
    fun onHomeResumed() = enqueue {
        // A plugin that didn't answer before, as one still starting, is asked again.
        val known = state ?: (if (plugin != null) read(refresh = true)?.state else null) ?: return@enqueue
        val fresh = applyFan(heal = true)
        applyScreens(fresh ?: if (known.has(DeviceCapability.Screens)) read()?.state else null)
    }

    /**
     * Before [packageName] stops being the device plugin, as its consent goes or another is
     * granted: both panels on, and any game override ended, then it is let go. If the queue can't
     * get there within a bound, it is let go untouched: nothing calls it after this returns.
     */
    suspend fun release(packageName: String) {
        val done = CompletableDeferred<Unit>()
        enqueue {
            try {
                if (plugin?.packageName == packageName) {
                    val known = state
                    if (known?.has(DeviceCapability.Screens) == true && known.screens != DeviceScreens.Dual) call("screens.set", DeviceScreens.Dual.id)
                    if (known?.has(DeviceCapability.GameFan) == true) call("game.end")
                    detach()
                }
            } finally {
                done.complete(Unit)
            }
        }
        if (withTimeoutOrNull(RELEASE_TIMEOUT_MS) { done.await() } == null) revoked = packageName
    }

    /** Wakes light both panels. Listen for a display turning on, and put the dark one back. */
    fun watchDisplays() {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return
        val main = Handler(Looper.getMainLooper())
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
        }, main)
    }

    /**
     * The device lights its panels as it wakes, possibly a moment after Android reports the
     * display on, so the panels are read and set now and once more shortly after.
     */
    private fun onDisplayOn() {
        val again = suspend {
            if (state?.has(DeviceCapability.Screens) == true) applyScreens(read()?.state)
        }
        enqueue(again)
        scope.launch {
            delay(WAKE_SETTLE_MS)
            enqueue(again)
        }
    }

    /** After a plugin arrives or answers anew: the games' fan, healing a lost override, then the panels. */
    private suspend fun settle() {
        val fresh = applyFan(heal = true)
        applyScreens(fresh ?: state)
    }

    private suspend fun detach() {
        plugin = null
        appliedFan = null
        fanGames = emptySet()
        fanTaken = false
        lightsNeedColor = false
        publish(null)
    }

    private suspend fun applyScreens(known: DeviceState?) {
        val current = known ?: return
        if (!current.has(DeviceCapability.Screens)) return
        val wanted = withContext(Dispatchers.Main.immediate) { wantedScreens() }
        if (current.screens != wanted) call("screens.set", wanted.id).orRead()
    }

    /** Begins or ends a game's override as the games on screen want. The reply's state, when a call was made. */
    private suspend fun applyFan(heal: Boolean = false): DeviceState? {
        if (state?.has(DeviceCapability.GameFan) != true) return null
        val live = withContext(Dispatchers.Main.immediate) { liveFans() }
        if ((live.keys - fanGames).isNotEmpty()) fanTaken = false
        fanGames = live.keys
        if (fanTaken && live.values.any { it != null }) return null
        fanTaken = false
        return when (val next = gameFanCall(appliedFan, live.values, heal)) {
            is GameFanCall.Begin -> {
                val reply = call("game.begin", extras = Bundle().apply { putString("fan", next.tier.id) })
                if (reply?.ok == true) appliedFan = next.tier else reply.orRead()
                reply?.state
            }
            GameFanCall.End -> {
                appliedFan = null
                val reply = call("game.end")
                reply.orRead()
                reply?.state
            }
            null -> null
        }
    }

    private suspend fun read(refresh: Boolean = false): DeviceReply? =
        call("state", extras = if (refresh) Bundle().apply { putBoolean("refresh", true) } else null,
            timeoutMs = if (refresh) PROBE_TIMEOUT_MS else CALL_TIMEOUT_MS)

    /** A refused or lost change: read what the device has, so the model stops showing what it asked for. */
    private suspend fun DeviceReply?.orRead() {
        if (this?.ok != true || state == null) read()
    }

    /**
     * One call to the plugin's provider, bounded by [timeoutMs]. A provider stuck past it keeps
     * the call thread, and until it lets go every call is dropped. The reply's state bundle, when
     * it has one, is published.
     */
    private suspend fun call(method: String, arg: String? = null, extras: Bundle? = null, timeoutMs: Long = CALL_TIMEOUT_MS): DeviceReply? {
        val target = plugin ?: return null
        if (target.packageName == revoked) return null
        val authority = target.authority ?: return null
        if (!calling.compareAndSet(false, true)) {
            Log.w(TAG, "Device plugin $method dropped: an earlier call is still waiting")
            return null
        }
        val pending = scope.async(callThread) {
            try {
                context.contentResolver.call(authority, method, arg, extras)?.let(::bundleValues)
            } catch (failure: Exception) {
                Log.w(TAG, "Device plugin $method failed", failure)
                null
            } finally {
                calling.set(false)
            }
        }
        val values = withTimeoutOrNull(timeoutMs) { pending.await() }
        if (values == null) {
            if (pending.isActive) Log.w(TAG, "Device plugin $method did not answer")
            return null
        }
        val reply = parseDeviceReply(target.packageName, values)
        if (!reply.ok) Log.w(TAG, "Device plugin $method: ${reply.error} ${values["message"] ?: ""}")
        reply.state?.let { next ->
            // A newer choice is on its way: showing this reply would step the row back for a moment.
            if (fanPending.get() == null && lightsPending.get() == null && bottomPending.get() == null) publish(next) else state = annotated(next)
        }
        return reply
    }

    /** Adds what Foldcade knows of the device to the plugin's state. */
    private fun annotated(next: DeviceState): DeviceState =
        next.copy(lightsNeedColor = lightsNeedColor, colorSettings = lightsNeedColor && colorSettings())

    private suspend fun publish(next: DeviceState?) {
        val shown = next?.let(::annotated)
        state = shown
        withContext(Dispatchers.Main.immediate) { onState(shown) }
    }

    private companion object {
        const val TAG = "FoldcadeDevice"
        const val CALL_TIMEOUT_MS = 10_000L

        /** The first `state` probes every capability, each a root command. */
        const val PROBE_TIMEOUT_MS = 20_000L
        const val BRIGHTNESS_TIMEOUT_MS = 3_000L

        /** How long a start waits for its panel to light. */
        const val LIGHT_TIMEOUT_MS = 2_500L
        const val RELEASE_TIMEOUT_MS = 8_000L
        const val WAKE_SETTLE_MS = 1_500L

        /** `lights.set` refused Charging or Breathe because every stick colour is black. */
        const val NEEDS_COLOR = "needs_color"
    }
}

@Suppress("DEPRECATION")
private fun bundleValues(bundle: Bundle): Map<String, Any?> = bundle.keySet().associateWith { bundle.get(it) }
