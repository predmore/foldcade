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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * Calls one granted device plugin, one call at a time, in the order asked, off the main thread.
 * Every call is bounded by a timeout and nothing it does throws into the UI: a plugin that fails,
 * hangs or goes away leaves the last state shown, or none. Replies carrying the state bundle
 * replace [state], which [onState] hands to the shell on the main thread.
 *
 * It also keeps the device where Foldcade wants it: the panels a one-screen layout leaves dark
 * ([reconcileScreens]), and a game's fan tier while that game's play session is open
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
    /** Each open play session's game tier, oldest first. Read on the main thread. */
    private val liveFans: () -> List<FanTier?>,
) {
    private val jobs = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var plugin: ApkPlugin? = null

    /** The plugin's last state, or null without one in use. */
    @Volatile var state: DeviceState? = null
        private set

    /** The tier Foldcade last began for a game, until it ends. Read and written only by jobs. */
    private var appliedFan: FanTier? = null
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
     * is asked to probe its capabilities, and the wanted panels are applied.
     */
    fun use(next: ApkPlugin?) = enqueue {
        val was = plugin
        if (was?.packageName == next?.packageName && was?.signers == next?.signers && was?.version == next?.version && was?.authority == next?.authority) return@enqueue
        plugin = next
        appliedFan = null
        if (next == null) {
            publish(null)
            return@enqueue
        }
        publish(read(refresh = true)?.state)
        applyScreens(state)
    }

    /*
     * A held key sends only the newest value: steps that arrive while one is applied replace each
     * other, so the plugin never works through a backlog of root commands.
     */

    fun setFan(tier: FanTier) {
        if (fanPending.getAndSet(tier) != null) return
        enqueue { fanPending.getAndSet(null)?.let { call("fan.set", it.id).orRead() } }
    }

    fun setLights(mode: StickLights) {
        if (lightsPending.getAndSet(mode) != null) return
        enqueue { lightsPending.getAndSet(null)?.let { call("lights.set", it.id).orRead() } }
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
        applyScreens(state)
    }

    /** The screens changed hands, or the layout changed: power the panels the session wants. */
    fun reconcileScreens() {
        if (!screensQueued.compareAndSet(false, true)) return
        enqueue {
            screensQueued.set(false)
            applyScreens(state)
        }
    }

    /** A play session opened or closed, or a game's tier changed. */
    fun reconcileFan() {
        if (!fanQueued.compareAndSet(false, true)) return
        enqueue {
            fanQueued.set(false)
            applyFan()
        }
    }

    /**
     * Home is in front again. With no play session open, any game override ends, which also heals
     * one a crash or force-stop of either app left behind. The panels are read and set again.
     */
    fun onHomeResumed() = enqueue {
        // A plugin that didn't answer before, as one still starting, is asked again.
        val known = state ?: (if (plugin != null) read(refresh = true)?.state else null) ?: return@enqueue
        val open = withContext(Dispatchers.Main.immediate) { liveFans().isNotEmpty() }
        val fresh = if (!open && known.has(DeviceCapability.GameFan)) {
            appliedFan = null
            call("game.end")?.state
        } else null
        applyScreens(fresh ?: if (known.has(DeviceCapability.Screens)) read()?.state else null)
    }

    /**
     * Before the device grant goes: both panels on, and any game override ended, since Foldcade
     * may not call the plugin afterwards. Waits for the plugin, within a bound.
     */
    suspend fun release(packageName: String) {
        val done = CompletableDeferred<Unit>()
        enqueue {
            try {
                val known = state?.takeIf { it.packageName == packageName && plugin?.packageName == packageName } ?: return@enqueue
                if (known.has(DeviceCapability.Screens) && known.screens != DeviceScreens.Dual) call("screens.set", DeviceScreens.Dual.id)
                if (known.has(DeviceCapability.GameFan)) call("game.end")
                appliedFan = null
            } finally {
                done.complete(Unit)
            }
        }
        withTimeoutOrNull(RELEASE_TIMEOUT_MS) { done.await() }
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

    private suspend fun applyScreens(known: DeviceState?) {
        val current = known ?: return
        if (!current.has(DeviceCapability.Screens)) return
        val wanted = withContext(Dispatchers.Main.immediate) { wantedScreens() }
        if (current.screens != wanted) call("screens.set", wanted.id).orRead()
    }

    private suspend fun applyFan() {
        if (state?.has(DeviceCapability.GameFan) != true) return
        val live = withContext(Dispatchers.Main.immediate) { liveFans() }
        when (val next = gameFanCall(appliedFan, live)) {
            is GameFanCall.Begin -> {
                val reply = call("game.begin", extras = Bundle().apply { putString("fan", next.tier.id) })
                if (reply?.ok == true) appliedFan = next.tier else reply.orRead()
            }
            GameFanCall.End -> {
                appliedFan = null
                call("game.end").orRead()
            }
            null -> Unit
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
     * its thread, but the queue moves on. The reply's state bundle, when it has one, is published.
     */
    private suspend fun call(method: String, arg: String? = null, extras: Bundle? = null, timeoutMs: Long = CALL_TIMEOUT_MS): DeviceReply? {
        val target = plugin ?: return null
        val authority = target.authority ?: return null
        val pending = scope.async(Dispatchers.IO) {
            try {
                context.contentResolver.call(authority, method, arg, extras)?.let(::bundleValues)
            } catch (failure: Exception) {
                Log.w(TAG, "Device plugin $method failed", failure)
                null
            }
        }
        val values = withTimeoutOrNull(timeoutMs) { pending.await() }
        if (values == null) {
            pending.cancel()
            Log.w(TAG, "Device plugin $method did not answer")
            return null
        }
        val reply = parseDeviceReply(target.packageName, values)
        if (!reply.ok) Log.w(TAG, "Device plugin $method: ${reply.error} ${values["message"] ?: ""}")
        reply.state?.let { next ->
            // A newer choice is on its way: showing this reply would step the row back for a moment.
            if (fanPending.get() == null && lightsPending.get() == null && bottomPending.get() == null) publish(next) else state = next
        }
        return reply
    }

    private suspend fun publish(next: DeviceState?) {
        state = next
        withContext(Dispatchers.Main.immediate) { onState(next) }
    }

    private companion object {
        const val TAG = "FoldcadeDevice"
        const val CALL_TIMEOUT_MS = 10_000L

        /** The first `state` probes every capability, each a root command. */
        const val PROBE_TIMEOUT_MS = 20_000L
        const val BRIGHTNESS_TIMEOUT_MS = 3_000L
        const val RELEASE_TIMEOUT_MS = 8_000L
        const val WAKE_SETTLE_MS = 1_500L
    }
}

@Suppress("DEPRECATION")
private fun bundleValues(bundle: Bundle): Map<String, Any?> = bundle.keySet().associateWith { bundle.get(it) }
