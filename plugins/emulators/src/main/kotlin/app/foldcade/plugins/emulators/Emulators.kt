package app.foldcade.plugins.emulators

import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.PlayerExtra

/**
 * Replaces a game that is already running in that emulator.
 * Declared before [EMULATORS]: top-level vals initialize in file order.
 */
private val RELAUNCH: Set<LaunchFlag> = setOf(LaunchFlag.NewTask, LaunchFlag.ClearTop, LaunchFlag.ClearTask)

/**
 * Application ids, activities, and handoffs. Read from ES-DE's Android
 * `es_find_rules.xml` and `es_systems.xml` (master, October 2026). Only the
 * launches that take a content URI are here. A launch that needs a file path,
 * such as RetroArch's `ROM` extra, does not fit this player.
 *
 * Platform ids are the local-folder catalog's. Order sets launch priority
 * within a platform.
 */
val EMULATORS: List<Emulator> = listOf(
    // Game Boy, Game Boy Color, Game Boy Advance.
    Emulator(
        id = "my-boy",
        displayName = "My Boy!",
        platformIds = listOf(GBA),
        activities = mapOf("com.fastemulator.gba" to "com.fastemulator.gba.EmulatorActivity"),
    ),
    Emulator(
        id = "my-oldboy",
        displayName = "My OldBoy!",
        platformIds = listOf(GB, GBC),
        activities = mapOf("com.fastemulator.gbc" to "com.fastemulator.gbc.EmulatorActivity"),
    ),
    Emulator(
        id = "pizza-boy-gba",
        displayName = "Pizza Boy GBA",
        platformIds = listOf(GBA),
        activities = mapOf(
            "it.dbtecno.pizzaboygbapro" to "it.dbtecno.pizzaboygbapro.MainActivity",
            "it.dbtecno.pizzaboygba" to "it.dbtecno.pizzaboygba.MainActivity",
        ),
        action = ACTION_MAIN,
        handoff = RomHandoff.Extra("rom_uri"),
        flags = RELAUNCH,
    ),
    Emulator(
        id = "pizza-boy-gbc",
        displayName = "Pizza Boy GBC",
        platformIds = listOf(GB, GBC),
        activities = mapOf(
            "it.dbtecno.pizzaboypro" to "it.dbtecno.pizzaboypro.MainActivity",
            "it.dbtecno.pizzaboy" to "it.dbtecno.pizzaboy.MainActivity",
        ),
        action = ACTION_MAIN,
        handoff = RomHandoff.Extra("rom_uri"),
        flags = RELAUNCH,
    ),
    explusAlpha("gba-emu", "GBA.emu", "com.explusalpha.GbaEmu", GBA),
    explusAlpha("gbc-emu", "GBC.emu", "com.explusalpha.GbcEmu", GB, GBC),

    // Nintendo home consoles.
    explusAlpha("nes-emu", "NES.emu", "com.explusalpha.NesEmu", NES),
    explusAlpha("snes9x-ex", "Snes9x EX+", "com.explusalpha.Snes9xPlus", SNES),
    Emulator(
        id = "m64plus-fz",
        displayName = "M64Plus FZ",
        platformIds = listOf(N64),
        activities = listOf(
            "org.mupen64plusae.v3.fzurita.pro",
            "org.mupen64plusae.v3.fzurita",
            "org.mupen64plusae.v3.fzurita.amazon",
        ).associateWith { MUPEN_SPLASH },
    ),
    Emulator(
        id = "mupen64plus-ae",
        displayName = "Mupen64Plus AE",
        platformIds = listOf(N64),
        activities = mapOf("org.mupen64plusae.v3.alpha" to MUPEN_SPLASH),
    ),
    Emulator(
        id = "dolphin",
        displayName = "Dolphin",
        platformIds = listOf(GAMECUBE, WII),
        // ES-DE starts TvMainActivity, not MainActivity, with AutoStartFile.
        activities = mapOf(
            "org.dolphinemu.dolphinemu" to "org.dolphinemu.dolphinemu.ui.main.TvMainActivity",
        ),
        action = ACTION_MAIN,
        handoff = RomHandoff.Extra("AutoStartFile"),
    ),
    Emulator(
        id = "dolphin-mmjr",
        displayName = "Dolphin MMJR",
        platformIds = listOf(GAMECUBE, WII),
        activities = listOf("org.dolphinemu.mmjr", "org.mm.jr")
            .associateWith { "org.dolphinemu.dolphinemu.ui.main.MainActivity" },
        handoff = RomHandoff.Extra("AutoStartFile"),
    ),
    Emulator(
        id = "eden",
        displayName = "Eden",
        platformIds = listOf(SWITCH),
        activities = listOf("dev.eden.eden_emulator", "dev.legacy.eden_emulator")
            .associateWith { "org.yuzu.yuzu_emu.activities.EmulationActivity" },
        // ES-DE launches Eden with this action, not VIEW.
        action = "android.nfc.action.TECH_DISCOVERED",
    ),

    // Sony.
    Emulator(
        id = "duckstation",
        displayName = "DuckStation",
        platformIds = listOf(PLAYSTATION),
        activities = mapOf(
            "com.github.stenzek.duckstation" to "com.github.stenzek.duckstation.EmulationActivity",
        ),
        action = ACTION_MAIN,
        handoff = RomHandoff.Extra("bootPath"),
        extras = listOf(PlayerExtra.BooleanFlag("resumeState", false)),
        flags = RELAUNCH,
    ),
    Emulator(
        id = "nethersx2",
        displayName = "NetherSX2",
        platformIds = listOf(PLAYSTATION_2),
        // AetherSX2 and the Turnip builds share this activity.
        activities = listOf("xyz.aethersx2.android", "xyz.aethersx2.tturnip", "xyz.aethersx2.cturnip")
            .associateWith { "xyz.aethersx2.android.EmulationActivity" },
        action = ACTION_MAIN,
        handoff = RomHandoff.Extra("bootPath"),
        flags = RELAUNCH,
    ),
    Emulator(
        id = "armsx2",
        displayName = "ARMSX2",
        platformIds = listOf(PLAYSTATION_2),
        activities = listOf("com.armsx2", "com.armsx2.nightly").associateWith { "com.armsx2.MainActivity" },
    ),
    Emulator(
        id = "ppsspp",
        displayName = "PPSSPP",
        platformIds = listOf(PSP),
        activities = listOf("org.ppsspp.ppssppgold", "org.ppsspp.ppsspp")
            .associateWith { "org.ppsspp.ppsspp.PpssppActivity" },
    ),

    // Sega.
    explusAlpha("md-emu", "MD.emu", "com.explusalpha.MdEmu", GENESIS, MASTER_SYSTEM),
    Emulator(
        id = "pizza-boy-sc",
        displayName = "Pizza Boy SC",
        platformIds = listOf(GENESIS, MASTER_SYSTEM, GAME_GEAR),
        activities = mapOf(
            "it.dbtecno.pizzaboyscpro" to "it.dbtecno.pizzaboyscpro.MainActivity",
            "it.dbtecno.pizzaboyscbasic" to "it.dbtecno.pizzaboyscbasic.MainActivity",
        ),
        action = ACTION_MAIN,
        handoff = RomHandoff.Extra("rom_uri"),
        flags = RELAUNCH,
    ),
    Emulator(
        id = "yaba-sanshiro-2",
        displayName = "Yaba Sanshiro 2",
        platformIds = listOf(SATURN),
        activities = listOf("org.devmiyax.yabasanshioro2.pro", "org.devmiyax.yabasanshioro2")
            .associateWith { "org.uoyabause.android.Yabause" },
        handoff = RomHandoff.Extra("org.uoyabause.android.FileNameUri"),
        flags = RELAUNCH,
    ),
    explusAlpha("saturn-emu", "Saturn.emu", "com.explusalpha.SaturnEmu", SATURN),
    Emulator(
        id = "flycast",
        displayName = "Flycast",
        platformIds = listOf(DREAMCAST),
        activities = mapOf("com.flycast.emulator" to "com.flycast.emulator.MainActivity"),
    ),
    Emulator(
        id = "redream",
        displayName = "Redream",
        platformIds = listOf(DREAMCAST),
        activities = mapOf("io.recompiled.redream" to "io.recompiled.redream.MainActivity"),
    ),
)

/** The EX emulators share one engine activity. The game is the intent data. */
private fun explusAlpha(id: String, name: String, packageName: String, vararg platformIds: String) =
    Emulator(
        id = id,
        displayName = name,
        platformIds = platformIds.toList(),
        activities = mapOf(packageName to "com.imagine.BaseActivity"),
    )

private const val MUPEN_SPLASH: String = "paulscode.android.mupen64plusae.SplashActivity"

const val GB: String = "game-boy"
const val GBC: String = "game-boy-color"
const val GBA: String = "game-boy-advance"
const val NES: String = "nes"
const val SNES: String = "snes"
const val N64: String = "nintendo-64"
const val GAMECUBE: String = "gamecube"
const val WII: String = "wii"
const val SWITCH: String = "nintendo-switch"
const val PLAYSTATION: String = "playstation"
const val PLAYSTATION_2: String = "playstation-2"
const val PSP: String = "psp"
const val GENESIS: String = "genesis"
const val MASTER_SYSTEM: String = "master-system"
const val GAME_GEAR: String = "game-gear"
const val SATURN: String = "saturn"
const val DREAMCAST: String = "dreamcast"
