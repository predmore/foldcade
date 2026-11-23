package app.foldcade.language

import kotlin.math.roundToInt

/**
 * A device plugin's controls, as its `state` reply describes them ([docs/device-plugins.md]).
 * Foldcade calls only a granted plugin, so [PickerModel.device] is null without one, and every
 * control below is absent: Foldcade then looks and behaves as it does without the protocol.
 */

/** The one scope a device plugin asks for. */
const val DEVICE_SCOPE = "device"

/** The device protocol 1 knows. A plugin for another is listed as unsupported and never called. */
const val DEVICE_THOR = "ayn-thor"

/** The devices this host can drive. */
val KNOWN_DEVICES: Set<String> = setOf(DEVICE_THOR)

/** A fan tier. [Stock] is the device's own trip points (`default` on the wire): read, never chosen. */
enum class FanTier(val id: String) {
    Quiet("quiet"),
    Balanced("balanced"),
    Performance("performance"),
    Stock("default"),
    ;

    companion object {
        /** The tiers a player chooses, in the order the choice rows step through them. */
        val chosen: List<FanTier> = listOf(Quiet, Balanced, Performance)

        fun fromId(id: String?): FanTier? = entries.firstOrNull { it.id == id }
    }
}

/** The stick lights' modes. */
enum class StickLights(val id: String) {
    Off("off"),
    Charging("charge"),
    Breathe("breathe"),
    Usb("usb"),
    ;

    companion object {
        fun fromId(id: String?): StickLights? = entries.firstOrNull { it.id == id }
    }
}

/** Which panels are powered: both, or only the one named. */
enum class DeviceScreens(val id: String) {
    Dual("dual"),
    Top("top"),
    Bottom("bottom"),
    ;

    companion object {
        fun fromId(id: String?): DeviceScreens? = entries.firstOrNull { it.id == id }
    }
}

enum class DeviceRoot(val id: String) {
    Granted("granted"),
    /** The root manager refused the plugin. */
    Denied("denied"),
    /** No `su` at all. */
    Absent("absent"),
    ;

    companion object {
        fun fromId(id: String?): DeviceRoot? = entries.firstOrNull { it.id == id }
    }
}

enum class DeviceCapability(val id: String) {
    Fan("fan"),
    Lights("lights"),
    Brightness("brightness"),
    Screens("screens"),
    GameFan("gameFan"),
    ;

    companion object {
        fun fromId(id: String?): DeviceCapability? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The granted device plugin's state bundle. A capability is listed only with the value it
 * reports, so a control never shows without something to show. [fan] is the tier in force,
 * [fanBaseline] the one the player chose, and [fanGame] a game's override while one runs.
 * Brightness is 0 to 1 for each panel.
 */
data class DeviceState(
    val packageName: String,
    val device: String,
    val root: DeviceRoot,
    val capabilities: Set<DeviceCapability> = emptySet(),
    val fan: FanTier? = null,
    val fanBaseline: FanTier? = null,
    val fanGame: FanTier? = null,
    val lights: StickLights? = null,
    val brightnessTop: Float? = null,
    val brightnessBottom: Float? = null,
    val screens: DeviceScreens? = null,
    /**
     * Foldcade's, not the plugin's: the last `lights.set` was refused with `needs_color`, as stickfx
     * refuses Charging and Breathe while every stick color is black. Cleared once one goes through.
     */
    val lightsNeedColor: Boolean = false,
    /** Foldcade's: AYN's RGB lighting screen can be opened to set a stick color. */
    val colorSettings: Boolean = false,
    /** Foldcade's: root isn't granted, and Magisk can be opened to grant it. */
    val rootSettings: Boolean = false,
) {
    fun has(capability: DeviceCapability): Boolean = capability in capabilities
}

/** A reply's outcome: [state] when it carried the state bundle, [error] when `ok` was false. */
data class DeviceReply(val ok: Boolean, val error: String? = null, val state: DeviceState? = null, val values: Map<String, Any?> = emptyMap())

/**
 * Reads a reply [Bundle][android.os.Bundle] given as its keys and values. Nothing in it is
 * trusted: an unknown value drops its capability rather than showing a control that can't say
 * where it is. Capabilities count only while root is granted, as the contract says.
 */
fun parseDeviceReply(packageName: String, values: Map<String, Any?>): DeviceReply {
    if (values["ok"] != true) return DeviceReply(ok = false, error = (values["error"] as? String) ?: "internal", values = values)
    return DeviceReply(ok = true, state = parseDeviceState(packageName, values), values = values)
}

/** The state bundle in [values], or null when the reply has none (no `root`, or no `device`). */
fun parseDeviceState(packageName: String, values: Map<String, Any?>): DeviceState? {
    val root = DeviceRoot.fromId(values["root"] as? String) ?: return null
    val device = (values["device"] as? String)?.takeIf { it.isNotBlank() } ?: return null
    val listed = when (val raw = values["capabilities"]) {
        is Array<*> -> raw.toList()
        is Collection<*> -> raw.toList()
        else -> emptyList()
    }.mapNotNull { DeviceCapability.fromId(it as? String) }.toSet().takeIf { root == DeviceRoot.Granted }.orEmpty()
    val fan = FanTier.fromId(values["fan"] as? String)?.takeIf { DeviceCapability.Fan in listed }
    val lights = StickLights.fromId(values["lights"] as? String)?.takeIf { DeviceCapability.Lights in listed }
    val top = level(values["brightnessTop"])
    val bottom = level(values["brightnessBottom"])
    val brightness = DeviceCapability.Brightness in listed && top != null && bottom != null
    val screens = DeviceScreens.fromId(values["screens"] as? String)?.takeIf { DeviceCapability.Screens in listed }
    val capabilities = listed.filterTo(mutableSetOf()) {
        when (it) {
            DeviceCapability.Fan -> fan != null
            DeviceCapability.Lights -> lights != null
            DeviceCapability.Brightness -> brightness
            DeviceCapability.Screens -> screens != null
            // A game's tier needs the fan it sets.
            DeviceCapability.GameFan -> fan != null
        }
    }
    return DeviceState(
        packageName = packageName,
        device = device,
        root = root,
        capabilities = capabilities,
        fan = fan,
        fanBaseline = fan?.let { FanTier.fromId(values["fanBaseline"] as? String) ?: it },
        fanGame = fan?.let { FanTier.fromId(values["fanGame"] as? String)?.takeIf { tier -> tier != FanTier.Stock } },
        lights = lights,
        brightnessTop = top.takeIf { brightness },
        brightnessBottom = bottom.takeIf { brightness },
        screens = screens,
    )
}

private fun level(value: Any?): Float? = (value as? Number)?.toFloat()?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)

/** The tier after [current] one step [direction] along Quiet · Balanced · Performance. Stock steps in from either end. */
fun steppedFan(current: FanTier?, direction: Int): FanTier {
    val at = FanTier.chosen.indexOf(current)
    if (at < 0) return if (direction >= 0) FanTier.chosen.first() else FanTier.chosen.last()
    return FanTier.chosen[Math.floorMod(at + direction, FanTier.chosen.size)]
}

/** A game's tier one step along Default · Quiet · Balanced · Performance. Default is null: nothing is sent. */
fun steppedGameFan(current: FanTier?, direction: Int): FanTier? {
    val values = listOf<FanTier?>(null) + FanTier.chosen
    return values[Math.floorMod(values.indexOf(current).coerceAtLeast(0) + direction, values.size)]
}

/**
 * The modes Stick lights offers. Once the plugin has refused one for want of a stick color, only
 * those that need none, until the colors may have changed.
 */
fun offeredLights(device: DeviceState?): List<StickLights> =
    if (device?.lightsNeedColor == true) listOf(StickLights.Off, StickLights.Usb) else StickLights.entries

fun steppedLights(current: StickLights?, direction: Int, modes: List<StickLights> = StickLights.entries): StickLights {
    val at = modes.indexOf(current)
    if (at < 0) return if (direction >= 0) modes.first() else modes.last()
    return modes[Math.floorMod(at + direction, modes.size)]
}

/**
 * Which panels a one-screen layout wants powered. Foldcade's own screen is always lit; a panel
 * a game holds, or is about to take, is lit before the game takes it, and dark again once it
 * leaves. A two-screen layout wants both.
 */
fun wantedScreens(layout: ScreenLayout, held: Set<HostScreen>): DeviceScreens {
    val dark = layout.darkScreen ?: return DeviceScreens.Dual
    if (dark in held) return DeviceScreens.Dual
    return when (dark) {
        HostScreen.Bottom -> DeviceScreens.Top
        HostScreen.Top -> DeviceScreens.Bottom
    }
}

/** What to tell the plugin about games' fan tiers: start an override, end it, or nothing. */
sealed interface GameFanCall {
    data class Begin(val tier: FanTier) : GameFanCall
    data object End : GameFanCall
}

/** The highest of [live]'s tiers, as appwatch picks the highest of its profiles: quiet, then balanced, then performance. */
fun highestFan(live: Collection<FanTier?>): FanTier? =
    live.filterNotNull().filter { it in FanTier.chosen }.maxByOrNull { FanTier.chosen.indexOf(it) }

/**
 * The games on the screens now each have a tier or Default ([live]); [active] is the override the
 * plugin reports (`fanGame`). The highest tier among the games should be the override; with none,
 * an override ends, whoever began it, which also heals one a crash left behind.
 */
fun gameFanCall(active: FanTier?, live: Collection<FanTier?>): GameFanCall? {
    val wanted = highestFan(live)
    return when {
        wanted != null && wanted != active -> GameFanCall.Begin(wanted)
        wanted == null && active != null -> GameFanCall.End
        else -> null
    }
}

/** True when these powered panels include every one of [panels]. */
fun DeviceScreens.lights(panels: Set<HostScreen>): Boolean = when (this) {
    DeviceScreens.Dual -> true
    DeviceScreens.Top -> panels.all { it == HostScreen.Top }
    DeviceScreens.Bottom -> panels.all { it == HostScreen.Bottom }
}

/**
 * R1's Top slider writes Android's level for the default display, and Bottom the plugin's bottom
 * panel. That split holds only while the default display is the top panel, as it always is on the
 * Thor; otherwise R1 keeps today's one slider for both screens, so the plugin's brightness is left out.
 */
fun brightnessFor(state: DeviceState, defaultDisplayIsTop: Boolean): DeviceState =
    if (defaultDisplayIsTop) state else state.copy(capabilities = state.capabilities - DeviceCapability.Brightness, brightnessTop = null, brightnessBottom = null)

/** The bottom slider's position, 0 to 100, for a panel [level] from 0 to 1. */
fun bottomBrightnessPosition(level: Float): Int = (level.coerceIn(0f, 1f) * 100f).roundToInt()

/** The level `brightness.set bottom` takes for a slider [position]. The plugin clamps it to 0.01 at the least. */
fun bottomBrightnessLevel(position: Int): Float = position.coerceIn(0, 100) / 100f

/** The device controls a model shows: the granted plugin's, when it reports [capability]. */
fun PickerModel.deviceHas(capability: DeviceCapability): Boolean = device?.has(capability) == true

/**
 * The screen A opens a one-screen game on. In a one-screen layout whose dark panel is powered
 * off, a game opens on the lit screen whatever Play on says, and X lights the other. Otherwise it
 * is Play on, as without a device plugin.
 */
fun PickerModel.playScreen(): HostScreen {
    // Bottom only is offered only with a panel the plugin powers: its games play on the bottom even
    // before the plugin has said so, as on a cold start.
    if (layout == ScreenLayout.BottomOnly) return HostScreen.Bottom
    val dark = layout.darkScreen ?: return playOn
    // A granted device plugin that hasn't answered yet may have the dark panel off: play on the lit
    // one, rather than light a panel for a game A meant for the screen in front of the player.
    val powered = device?.has(DeviceCapability.Screens) ?: deviceGranted()
    return if (powered) dark.other() else playOn
}

/** True with a granted device plugin this host can drive, before it has answered as after. */
fun PickerModel.deviceGranted(): Boolean =
    apkPlugins.any { it.device != null && it.supported && DEVICE_SCOPE in it.granted }

/** The layouts Settings › System › Layout steps through. Bottom only needs a panel it can power off, unless it is already chosen. */
fun offeredLayouts(model: PickerModel): List<ScreenLayout> = ScreenLayout.entries.filter {
    it != ScreenLayout.BottomOnly || model.deviceHas(DeviceCapability.Screens) || model.layout == ScreenLayout.BottomOnly
}

/** A game's own fan tier, in Y's menu. Null [tier] is Default: nothing is sent at launch. */
data class GameFanSetting(val tier: FanTier? = null)

object DeviceCopy {
    /** Settings' category for the device's controls, named for the device. */
    const val thor = "Thor"
    const val controlDevice = "Control this device"
    const val consent = "It can change the fan, stick lights and screens. Foldcade tells it when a game with its own fan setting starts and stops."
    const val fan = "Fan"
    const val quiet = "Quiet"
    const val balanced = "Balanced"
    const val performance = "Performance"
    /** The device's own fan curve, before the player chooses a tier. */
    const val stock = "Stock"
    const val gameDefault = "Default"
    const val stickLights = "Stick lights"
    const val lightsOff = "Off"
    const val charging = "Charging"
    const val breathe = "Breathe"
    /** stickfx's `usb`: lit while a cable is plugged in. */
    const val usb = "Plugged in"
    const val top = "Top"
    const val bottom = "Bottom"
    const val rootAccess = "Root access"
    const val rootDenied = "Not allowed"
    const val rootAbsent = "Not found"
    const val checkAgain = "Check again"
    /** A plugin's card while it can do nothing without root. */
    const val needsRoot = "Needs root"
    const val unsupported = "Foldcade does not support this plugin’s device yet."

    /** Under Stick lights after stickfx refused Charging or Breathe for want of a color. */
    const val lightsNeedColor = "Charging and Breathe need a stick color. Set one in AYN › RGB lighting."

    /** Under Root access while the root manager refuses: Magisk 31 lists the plugin only when it is added by hand. */
    fun rootSteps(plugin: String, checkAgain: Boolean) =
        "Magisk won’t ask. In Magisk › Superuser, tap + and add $plugin" + if (checkAgain) ", then check again." else "."
    const val rootMissing = "It needs a rooted Thor, as with Magisk."

    /** A on Root access while Magisk can be opened. */
    const val openMagisk = "Open Magisk"

    fun category(device: String): String = when (device) {
        DEVICE_THOR -> thor
        else -> device
    }

    fun fan(tier: FanTier?): String = when (tier) {
        FanTier.Quiet -> quiet
        FanTier.Balanced -> balanced
        FanTier.Performance -> performance
        FanTier.Stock, null -> stock
    }

    fun gameFan(tier: FanTier?): String = tier?.let(::fan) ?: gameDefault

    fun lights(mode: StickLights?): String = when (mode) {
        StickLights.Off, null -> lightsOff
        StickLights.Charging -> charging
        StickLights.Breathe -> breathe
        StickLights.Usb -> usb
    }

    fun root(root: DeviceRoot): String = when (root) {
        DeviceRoot.Granted -> ""
        DeviceRoot.Denied -> rootDenied
        DeviceRoot.Absent -> rootAbsent
    }
}
