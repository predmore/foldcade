package app.foldcade.plugins.retroarch

import app.foldcade.api.plugin.FirmwareOffer
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.SaveFolderAccess
import java.security.MessageDigest

/** Firmware paths, optional flags and documented MD5s from the pinned core-info snapshot. */
internal data class CoreFirmware(val core: String, val path: String, val optional: Boolean, val md5: String?)

internal val coreFirmware: List<CoreFirmware> by lazy {
    requireNotNull(RetroArchEntry::class.java.getResourceAsStream("/firmware.tsv")).bufferedReader().useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
            val fields = line.split('\t')
            CoreFirmware(fields[0], fields[1], fields[2] == "true", fields[3].takeUnless { it == "-" })
        }.toList().let { files ->
            // Some shared cores omit a checksum another core documents for the
            // exact same BIOS path (Genesis Plus GX / PicoDrive's Sega CD BIOS).
            files.map { file -> if (file.md5 != null) file else file.copy(
                md5 = files.firstOrNull { it.path == file.path && it.md5 != null }?.md5,
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

internal fun RetroArchPlayer.firmwareList(): List<CoreFirmware> = coreFirmware.filter { it.core == firmwareCore() }

/** Known bytes win over the filename. A documented hash mismatch is never installed. */
internal fun identifyBios(files: List<CoreFirmware>, offer: FirmwareOffer): CoreFirmware? {
    if (offer.header.isEmpty()) return null
    val hash = biosMd5(offer.header)
    return files.firstOrNull { it.md5 == hash }
        ?: files.firstOrNull { it.md5 == null && it.path.substringAfterLast('/').equals(biosLeaf(offer.name), true) }
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
        system.platformId == "sega-cd" -> regional("bios_CD_J.bin", "bios_CD_U.bin", "bios_CD_E.bin")
        system.platformId == "pc-engine-cd" -> listOf(listOf("syscard3.pce"))
        firmwareCore() == "swanstation" -> listOf(files.map { it.path })
        firmwareCore() in setOf("mednafen_psx", "mednafen_psx_hw") ->
            regional("scph5500.bin", "scph5501.bin", "scph5502.bin")
        firmwareCore() == "mednafen_saturn" -> if (region == 'J') listOf(listOf("sega_101.bin"))
            else if (region != null) listOf(listOf("mpr-17933.bin")) else listOf(listOf("mpr-17933.bin", "sega_101.bin"))
        else -> files.filterNot { it.optional }.map { listOf(it.path) }
    }
}

internal fun RetroArchPlayer.missingBios(game: Game, folder: SaveFolderAccess): List<String> = requiredBios(game)
    .filter { alternatives -> alternatives.none { path ->
        val spec = firmwareList().firstOrNull { it.path == path }
        val bytes = folder.read(path, FIRMWARE_HEADER_SIZE)
        bytes != null && bytes.isNotEmpty() && (spec?.md5 == null || biosMd5(bytes) == spec.md5)
    } }.map { it.joinToString(" / ") }

/** Full bytes for hashing normal BIOS dumps, plus one byte to reject oversized lookalikes. */
internal const val FIRMWARE_HEADER_SIZE = 4 * 1024 * 1024 + 1

/** Core-info's directory entries stand for the full Databases and Machines bundles. */
internal fun blueMsxPath(name: String): String? {
    if (biosLeaf(name) == null) return null
    val parts = name.split('/')
    val start = parts.indexOfFirst { it == "Databases" || it == "Machines" }
    return if (start >= 0 && start < parts.lastIndex) parts.drop(start).joinToString("/") else null
}
