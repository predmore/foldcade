package app.foldcade.plugins.retroarch

/**
 * One libretro core. [file] is the core's name on the libretro buildbot, the
 * file RetroArch's Core Downloader installs as `<file>_libretro_android.so`.
 * [libraryName] is the name the core reports to RetroArch. RetroArch sorts its
 * saves into a folder by that name, and its Core Downloader lists the core by it.
 */
data class Core(
    val file: String,
    val libraryName: String,
) {
    val fileName: String get() = "${file}_libretro_android.so"
}

/**
 * The cores Foldcade starts for one platform, best first. The first one
 * installed is used.
 *
 * [aheadOfStandalone] puts RetroArch, with a core installed, ahead of a
 * standalone emulator for the platform. The Nintendo DS is the exception:
 * melonDS draws the touch screen on the Thor's other display.
 */
data class RetroArchSystem(
    val platformId: String,
    val cores: List<Core>,
    val aheadOfStandalone: Boolean = true,
)

// Declared before [SYSTEMS]: top-level vals initialize in file order.
private val GAMBATTE = Core("gambatte", "Gambatte")
private val SAMEBOY = Core("sameboy", "SameBoy")
private val MGBA = Core("mgba", "mGBA")
private val VBAM = Core("vbam", "VBA-M")
private val GPSP = Core("gpsp", "gpSP")
private val MELONDS_DS = Core("melondsds", "melonDS DS")
private val MELONDS = Core("melonds", "melonDS")
private val DESMUME = Core("desmume", "DeSmuME")
private val MESEN = Core("mesen", "Mesen")
private val FCEUMM = Core("fceumm", "FCEUmm")
private val NESTOPIA = Core("nestopia", "Nestopia")
private val SNES9X = Core("snes9x", "Snes9x")
private val SNES9X_2010 = Core("snes9x2010", "Snes9x 2010")
private val BSNES = Core("bsnes", "bsnes")
private val MUPEN64PLUS_NEXT = Core("mupen64plus_next_gles3", "Mupen64Plus-Next")
private val PARALLEL_N64 = Core("parallel_n64", "ParaLLEl N64")
private val GENESIS_PLUS_GX = Core("genesis_plus_gx", "Genesis Plus GX")
private val PICODRIVE = Core("picodrive", "PicoDrive")
private val GEARSYSTEM = Core("gearsystem", "Gearsystem")
private val YABASANSHIRO = Core("yabasanshiro", "YabaSanshiro")
private val BEETLE_SATURN = Core("mednafen_saturn", "Beetle Saturn")
private val YABAUSE = Core("yabause", "Yabause")
private val PCSX_REARMED = Core("pcsx_rearmed", "PCSX-ReARMed")
private val SWANSTATION = Core("swanstation", "SwanStation")
private val BEETLE_PSX_HW = Core("mednafen_psx_hw", "Beetle PSX HW")
private val BEETLE_PSX = Core("mednafen_psx", "Beetle PSX")
private val BEETLE_PCE_FAST = Core("mednafen_pce_fast", "Beetle PCE Fast")
private val BEETLE_PCE = Core("mednafen_pce", "Beetle PCE")
private val BEETLE_SUPERGRAFX = Core("mednafen_supergrafx", "Beetle SuperGrafx")
private val BEETLE_NEOPOP = Core("mednafen_ngp", "Beetle NeoPop")
private val RACE = Core("race", "RACE")
private val BEETLE_WONDERSWAN = Core("mednafen_wswan", "Beetle WonderSwan")

/**
 * Platform ids are the local-folder catalog's. The order of [cores] follows
 * ES-DE's Android defaults, with lighter cores ahead where ES-DE's first choice
 * needs a desktop-class device.
 */
val SYSTEMS: List<RetroArchSystem> = listOf(
    // Nintendo handhelds.
    RetroArchSystem("game-boy", listOf(GAMBATTE, SAMEBOY, MGBA)),
    RetroArchSystem("game-boy-color", listOf(GAMBATTE, SAMEBOY, MGBA)),
    RetroArchSystem("game-boy-advance", listOf(MGBA, VBAM, GPSP)),
    RetroArchSystem("nintendo-ds", listOf(MELONDS_DS, MELONDS, DESMUME), aheadOfStandalone = false),
    RetroArchSystem("virtual-boy", listOf(Core("mednafen_vb", "Beetle VB"))),

    // Nintendo home consoles.
    RetroArchSystem("nes", listOf(MESEN, FCEUMM, NESTOPIA)),
    RetroArchSystem("snes", listOf(SNES9X, SNES9X_2010, BSNES)),
    RetroArchSystem("nintendo-64", listOf(MUPEN64PLUS_NEXT, PARALLEL_N64)),

    // Sega.
    RetroArchSystem("master-system", listOf(GENESIS_PLUS_GX, PICODRIVE, GEARSYSTEM)),
    RetroArchSystem("game-gear", listOf(GENESIS_PLUS_GX, GEARSYSTEM)),
    RetroArchSystem("genesis", listOf(GENESIS_PLUS_GX, PICODRIVE)),
    RetroArchSystem("sega-cd", listOf(GENESIS_PLUS_GX, PICODRIVE)),
    RetroArchSystem("sega-32x", listOf(PICODRIVE)),
    RetroArchSystem("saturn", listOf(YABASANSHIRO, BEETLE_SATURN, YABAUSE)),
    RetroArchSystem("dreamcast", listOf(Core("flycast", "Flycast"))),

    // Sony.
    RetroArchSystem("playstation", listOf(PCSX_REARMED, SWANSTATION, BEETLE_PSX_HW, BEETLE_PSX)),
    RetroArchSystem("psp", listOf(Core("ppsspp", "PPSSPP"))),

    // NEC.
    RetroArchSystem("pc-engine", listOf(BEETLE_PCE_FAST, BEETLE_PCE, BEETLE_SUPERGRAFX)),
    RetroArchSystem("pc-engine-cd", listOf(BEETLE_PCE_FAST, BEETLE_PCE)),

    // Atari.
    RetroArchSystem("atari-2600", listOf(Core("stella", "Stella"), Core("stella2014", "Stella 2014"))),
    RetroArchSystem("atari-7800", listOf(Core("prosystem", "ProSystem"))),
    RetroArchSystem("atari-lynx", listOf(Core("handy", "Handy"), Core("mednafen_lynx", "Beetle Lynx"))),

    // SNK and Bandai handhelds.
    RetroArchSystem("neo-geo-pocket", listOf(BEETLE_NEOPOP, RACE)),
    RetroArchSystem("neo-geo-pocket-color", listOf(BEETLE_NEOPOP, RACE)),
    RetroArchSystem("wonderswan", listOf(BEETLE_WONDERSWAN)),
    RetroArchSystem("wonderswan-color", listOf(BEETLE_WONDERSWAN)),

    // Coleco.
    RetroArchSystem("colecovision", listOf(Core("gearcoleco", "Gearcoleco"), Core("bluemsx", "blueMSX"))),
)
