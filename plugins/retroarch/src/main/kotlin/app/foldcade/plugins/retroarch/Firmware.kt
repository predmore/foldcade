package app.foldcade.plugins.retroarch

import app.foldcade.api.plugin.FirmwareOffer
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.SaveFolderAccess
import java.security.MessageDigest

/** Firmware paths, optional flags and documented MD5s from the pinned core-info snapshot. */
internal data class CoreFirmware(val core: String, val path: String, val optional: Boolean, val md5: String?, val size: Long? = null)

internal val coreFirmware: List<CoreFirmware> by lazy {
    requireNotNull(RetroArchEntry::class.java.getResourceAsStream("/firmware.tsv")).bufferedReader().useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
            val fields = line.split('\t')
            CoreFirmware(fields[0], fields[1], fields[2] == "true", fields[3].takeUnless { it == "-" }, fields.getOrNull(4)?.toLongOrNull())
        }.toList().let { files ->
            // Some shared cores omit a checksum another core documents for the
            // exact same BIOS path (Genesis Plus GX / PicoDrive's Sega CD BIOS).
            files.map { file -> if (file.md5 != null) file else file.copy(
                md5 = files.firstOrNull { it.path == file.path && it.md5 != null }?.md5,
                size = file.size ?: files.firstOrNull { it.path == file.path && it.size != null }?.size,
            ) }
        }
    }
}

internal fun biosMd5(bytes: ByteArray): String = MessageDigest.getInstance("MD5").digest(bytes)
    .joinToString("") { "%02x".format(it.toInt() and 255) }

/** Reject traversal, but accept the zip/member names supplied by the shared firmware unpacker. */
internal fun biosLeaf(name: String): String? = name.split('/').takeIf { parts ->
    parts.all { it.isNotBlank() && it != "." && it != ".." && it.none { c -> c == '\\' || c.code <= 31 } }
}?.last()

internal fun RetroArchPlayer.firmwareCore(): String {
    val pkg = cores.activePackage()
    return when (val choice = pkg?.let(::coreChoice)) {
        is CoreChoice.Ready -> choice.core.file
        is CoreChoice.Missing -> choice.wanted.file
        is CoreChoice.Unknown -> choice.assumed.file
        else -> system.cores.first().file
    }
}

// PicoDrive also opens these names, as raw images or ZIPs, before its core-info names.
// https://github.com/libretro/picodrive/blob/master/platform/libretro/libretro.c (biosfiles_*, find_bios)
private val picoDriveBiosNames = mapOf(
    "bios_CD_U.bin" to listOf("bios_CD_U", "us_scd2_9306", "SegaCDBIOS9303", "us_scd1_9210"),
    "bios_CD_E.bin" to listOf("bios_CD_E", "eu_mcd2_9306", "eu_mcd2_9303", "eu_mcd1_9210"),
    "bios_CD_J.bin" to listOf("bios_CD_J", "jp_mcd2_921222", "jp_mcd1_9112", "jp_mcd1_9111"),
)
private val picoDriveBiosPaths = picoDriveBiosNames.values.flatten().flatMap { base -> listOf("$base.bin", "$base.zip") }.toSet()

// SwanStation's directory scan also recognizes the documented PCSX PS1 dumps.
// Its core-info list names only a subset (HostInterface::FindBIOSImageInDirectory).
internal fun RetroArchPlayer.firmwareList(): List<CoreFirmware> {
    val core = firmwareCore()
    val files = coreFirmware.filter { it.core == core || core == "swanstation" && it.core == "pcsx_rearmed" }
    val alternates = if (core != "picodrive") emptyList() else picoDriveBiosPaths
        .filterNot { path -> files.any { it.path == path } }
        .map { path -> CoreFirmware(core, path, true, null, if (path.endsWith(".bin")) 131072L else null) }
    return (files + alternates).filter { file ->
        // Shared cores list firmware for several systems. Only this player's system
        // matters when deciding whether its optional BIOS is already in place.
        when (platformId) {
            "game-boy" -> file.path in setOf("gb_bios.bin", "dmg_boot.bin", "sgb_bios.bin", "sgb_boot.bin")
            "game-boy-color" -> file.path in setOf("gbc_bios.bin", "cgb_boot.bin")
            "game-boy-advance" -> file.path == "gba_bios.bin"
            "sega-cd" -> file.path.startsWith("bios_CD_") || core == "picodrive" && file.path in picoDriveBiosPaths
            "master-system" -> file.path.endsWith(".sms")
            "game-gear" -> file.path == "bios.gg"
            "genesis", "sega-32x" -> !file.path.startsWith("bios_CD_") && file.path !in picoDriveBiosPaths && !file.path.endsWith(".sms") && file.path != "bios.gg"
            else -> true
        }
    }
}

/** Known bytes win over the filename. A documented hash mismatch is never installed. */
internal fun identifyBios(files: List<CoreFirmware>, offer: FirmwareOffer): CoreFirmware? {
    if (offer.header.isEmpty()) return null
    val hash = biosMd5(offer.header)
    return files.firstOrNull { it.md5 == hash }
        ?: files.firstOrNull { it.md5 == null && it.path.substringAfterLast('/').equals(biosLeaf(offer.name), true) && (it.size == null || it.size == offer.header.size.toLong()) }
}

/** Region-sensitive cores require the BIOS for the disc's region, not every region's BIOS. */
internal fun RetroArchPlayer.requiredBios(game: Game): List<List<String>> {
    val files = firmwareList()
    val region = when {
        Regex("\\((Japan|Japan,|Asia|Korea)", RegexOption.IGNORE_CASE).containsMatchIn(game.label) -> 'J'
        Regex("\\((Europe|Europe,|Australia|France|Germany|Spain|Italy)", RegexOption.IGNORE_CASE).containsMatchIn(game.label) -> 'E'
        Regex("\\((USA|USA,|World|Brazil|Canada)", RegexOption.IGNORE_CASE).containsMatchIn(game.label) -> 'U'
        else -> null
    }
    fun regional(j: String, u: String, e: String) = listOf(when (region) {
        'J' -> listOf(j)
        'U' -> listOf(u)
        'E' -> listOf(e)
        else -> listOf(u, e, j)
    })
    return when {
        // Current cores have open BIOS / graceful asset fallback despite stale
        // core-info required flags. Never turn those warnings into launch gates.
        // https://github.com/libretro/gpsp/blob/master/libretro/libretro.c (selected_bios = builtin_bios)
        // https://github.com/hrydgard/ppsspp/blob/master/libretro/libretro.cpp ("Core system files missing, expect bugs.")
        // https://github.com/libretro/beetle-psx-libretro/blob/master/libretro.c (BIOSFile absent -> bundled openbios)
        firmwareCore() in setOf("gpsp", "ppsspp", "mednafen_psx", "mednafen_psx_hw") -> emptyList()
        firmwareCore() == "yabasanshiro" -> listOf(listOf("saturn_bios.bin", "sega_101.bin", "mpr-17933.bin"))
        system.platformId == "sega-cd" -> regional("bios_CD_J.bin", "bios_CD_U.bin", "bios_CD_E.bin").map { regionFiles ->
            if (firmwareCore() != "picodrive") regionFiles else regionFiles.flatMap { path ->
                picoDriveBiosNames.getValue(path).flatMap { base -> listOf("$base.bin", "$base.zip") }
            }
        }
        system.platformId == "pc-engine-cd" -> listOf(listOf("syscard3.pce"))
        firmwareCore() == "swanstation" -> listOf(files.map { it.path }.distinct())
        firmwareCore() == "mednafen_saturn" -> if (region == 'J') listOf(listOf("sega_101.bin"))
            else if (region != null) listOf(listOf("mpr-17933.bin")) else listOf(listOf("mpr-17933.bin", "sega_101.bin"))
        else -> files.filterNot { it.optional }.map { listOf(it.path) }
    }
}

/** The cores also scan unnamed/alternate dumps; a name-only gate cannot prove these absent.
 * https://github.com/libretro/swanstation/blob/master/src/core/host_interface.cpp (FindBIOSImageInDirectory)
 * https://github.com/libretro/beetle-psx-libretro/blob/master/libretro.c (search_firmware, firmware_is_present)
 */
internal fun RetroArchPlayer.hasScanningPs1Bios(folder: SaveFolderAccess): Boolean {
    val sizes = when (firmwareCore()) {
        "swanstation" -> setOf(524288L, 4194304L, 0x3E66F0L)
        "mednafen_psx", "mednafen_psx_hw" -> setOf(524288L, 1048576L)
        else -> return false
    }
    return folder.list("").orEmpty().any { file ->
        !file.isFolder && file.size in sizes
    }
}

internal fun RetroArchPlayer.missingBios(game: Game, folder: SaveFolderAccess): List<String> =
    if (hasScanningPs1Bios(folder)) emptyList() else requiredBios(game)
    .filter { alternatives -> alternatives.none { path ->
        val spec = firmwareList().firstOrNull { it.path == path }
        val entry = folder.list(path.substringBeforeLast('/', "")).orEmpty()
            .firstOrNull { !it.isFolder && it.name.equals(path.substringAfterLast('/'), true) }
        // Hashes identify incoming dumps. An already installed alternate BIOS
        // revision is the player's choice, not proof that the BIOS is missing.
        entry != null && entry.size != 0L && (entry.size < 0 || spec?.size == null || entry.size == spec.size)
    } }.map { if (firmwareCore() == "picodrive" && platformId == "sega-cd") it.first() else it.joinToString(" / ") }

/** Full bytes for hashing normal BIOS dumps, plus one byte to reject oversized lookalikes. */
internal const val FIRMWARE_HEADER_SIZE = 4 * 1024 * 1024 + 1

/** Core-info's directory entries stand for the full Databases and Machines bundles. */
internal fun blueMsxPath(name: String): String? {
    if (biosLeaf(name) == null) return null
    val parts = name.split('/')
    val start = parts.indexOfFirst { it == "Databases" || it == "Machines" }
    return if (start >= 0 && start < parts.lastIndex) parts.drop(start).joinToString("/") else null
}
