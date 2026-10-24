package app.foldcade.plugins.armsx2

import app.foldcade.api.plugin.GameFileReader
import app.foldcade.api.plugin.SaveFolderAccess
import app.foldcade.api.plugin.SaveFolderEntry
import app.foldcade.api.plugin.SaveFolderSearch
import app.foldcade.api.plugin.SaveSyncState

/**
 * ARMSX2's slot 1 card, and the game's saves on it.
 *
 * The card is the one `armsx2-settings.json` names. A game uses its per-game
 * `memoryCardSlot1Filename` and `memoryCardSlot1Enabled` when that serial has
 * them, and the global values otherwise. [state] has no serial, so it uses the
 * global values. `PCSX2-Android.ini` is used only when that mirror is missing
 * or cannot be parsed: `Slot1_Filename` under `[MemoryCards]`, or `mcd001.ps2`
 * when that line is absent. A disabled slot is left alone.
 * A folder card with a formatted superblock is where the game's saves sync.
 * A missing card, a folder card with no saves and no formatted superblock, a
 * formatted file card whose root directory holds only `.` and `..`, or a file
 * card of either 8 MB size whose bytes are all `0xFF`, becomes a folder card,
 * and only when [forLaunch] is true. The empty file card is copied into
 * Foldcade's backups before it is removed. A file card that holds saves, or
 * one that cannot be read, stays where it is. A setup that was started and
 * could not be finished throws, so the launch does not go on without the card.
 */
internal object Ps2Card {
    const val SLOT = "card1"
    const val INI = "PCSX2-Android.ini"
    const val SETTINGS = "armsx2-settings.json"
    const val MEMCARDS = "memcards"
    const val DEFAULT_FILE = "mcd001.ps2"

    /** How many bytes of a blank card are read at once. The whole card is read only to back it up. */
    internal const val BLANK_CHUNK = 64 * 1024

    /**
     * Shown when slot 1 is a file card Foldcade will not replace. The user
     * switches slot 1 to a folder card in ARMSX2.
     */
    const val SETUP_STEPS =
        "Slot 1 needs a folder card to sync. In ARMSX2, open ☰ › Memory Cards and tap ＋. " +
            "Set Type to Folder, tap Create, then tap Slot 1 on the new card. " +
            "Saves on your old card stay there and don’t sync."

    fun find(slot: String, game: GameFileReader, folder: SaveFolderAccess, forLaunch: Boolean): SaveFolderSearch? {
        if (slot != SLOT) return null
        val serial = ps2Serial(game) ?: return null
        return when (val plan = plan(folder, serial)) {
            is Plan.Ready -> SaveFolderSearch(plan.parent, serial)
            is Plan.Make -> {
                if (!forLaunch) return null
                if (!folder.createFolder(plan.parent) || !folder.write(superblock(plan.parent), Ps2Superblock.formatted8Mb())) {
                    error("memory card could not be set up")
                }
                SaveFolderSearch(plan.parent, serial)
            }
            is Plan.Convert -> {
                if (!forLaunch) return null
                if (!folder.moveToBackup(plan.parent) || !folder.createFolder(plan.parent) ||
                    !folder.write(superblock(plan.parent), Ps2Superblock.formatted8Mb())
                ) {
                    error("memory card could not be set up")
                }
                SaveFolderSearch(plan.parent, serial)
            }
            Plan.NeedsSetup, Plan.Unavailable -> null
        }
    }

    fun state(folder: SaveFolderAccess): SaveSyncState? = when (plan(folder, serial = null)) {
        is Plan.Ready, is Plan.Make, is Plan.Convert -> SaveSyncState.Syncing
        Plan.NeedsSetup -> SaveSyncState.NeedsSetup(SETUP_STEPS)
        Plan.Unavailable -> null
    }

    private fun superblock(parent: String) = "$parent/${Ps2Superblock.FILE_NAME}"

    /**
     * What slot 1 is now. [Plan.Make] and [Plan.Convert] are a folder card the
     * next launch can create. [Plan.NeedsSetup] is a file card that stays.
     */
    private fun plan(folder: SaveFolderAccess, serial: String?): Plan {
        val root = folder.list("") ?: return Plan.Unavailable
        val slot = slot1(root, folder, serial) ?: return Plan.Unavailable
        if (!slot.enabled) return Plan.Unavailable
        val name = cardName(slot.filename) ?: return Plan.Unavailable
        val memcards = root.firstOrNull { it.isFolder && it.name.equals(MEMCARDS, ignoreCase = true) }?.name
        if (memcards == null) return Plan.Make("$MEMCARDS/$name")
        val parent = "$memcards/$name"
        val children = folder.list(parent)
        val listed = folder.list(memcards) ?: return Plan.Unavailable
        val card = listed.firstOrNull { it.name == name } ?: return Plan.Make(parent)
        if (card.isFolder) {
            if (children == null) return Plan.Unavailable
            val saves = children.any { it.isFolder }
            val block = superblock(children, folder, parent)
            return when {
                block == Super.Formatted -> Plan.Ready(parent)
                block == Super.Replaceable && !saves -> Plan.Make(parent)
                else -> Plan.Unavailable
            }
        }
        return when (fileCardHoldsSaves(folder, parent, card.size)) {
            false -> Plan.Convert(parent)
            true -> Plan.NeedsSetup
            null -> Plan.NeedsSetup
        }
    }

    /**
     * Slot 1 from the settings mirror when it can be read, and from the ini
     * otherwise. [serial] null reads the global card, which is what Settings shows.
     */
    private fun slot1(root: List<SaveFolderEntry>, folder: SaveFolderAccess, serial: String?): Slot1? {
        val mirror = root.firstOrNull { !it.isFolder && it.name == SETTINGS }
        if (mirror != null && mirror.size <= SETTINGS_LIMIT) {
            val text = folder.read(SETTINGS, SETTINGS_LIMIT)?.toString(Charsets.UTF_8)
            if (text != null) slotFromMirror(text, serial)?.let { return it }
        }
        val ini = root.firstOrNull { !it.isFolder && it.name == INI } ?: return Slot1(enabled = true, filename = DEFAULT_FILE)
        if (ini.size > INI_LIMIT) return null
        val text = folder.read(INI, INI_LIMIT) ?: return null
        return parseSlot1(text.toString(Charsets.UTF_8))
    }

    /** A card file name that stays one file inside `memcards`. */
    internal fun cardName(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == "." || trimmed == "..") return null
        if (trimmed.any { it == '/' || it == '\\' || it.code <= 31 }) return null
        return trimmed
    }
}

private sealed interface Plan {
    data class Ready(val parent: String) : Plan
    data class Make(val parent: String) : Plan
    data class Convert(val parent: String) : Plan
    data object NeedsSetup : Plan
    data object Unavailable : Plan
}

internal data class Slot1(val enabled: Boolean, val filename: String)

/** `Slot1_Enable` and `Slot1_Filename` in a PCSX2 ini. Null when the enable flag is not a boolean. */
internal fun parseSlot1(ini: String): Slot1? {
    var section = ""
    var enabled = true
    var filename = Ps2Card.DEFAULT_FILE
    for (raw in ini.split('\n')) {
        val line = raw.trim().trimEnd('\r')
        if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue
        if (line.startsWith("[") && line.endsWith("]")) {
            section = line.substring(1, line.length - 1).trim()
            continue
        }
        if (section != "MemoryCards") continue
        val split = line.indexOf('=')
        if (split <= 0) continue
        val key = line.substring(0, split).trim()
        val value = line.substring(split + 1).trim().removeSurrounding("\"")
        when (key) {
            "Slot1_Enable" -> enabled = when (value.lowercase()) {
                "true", "1" -> true
                "false", "0" -> false
                else -> return null
            }
            "Slot1_Filename" -> if (value.isNotEmpty()) filename = value
        }
    }
    return Slot1(enabled, filename)
}

private enum class Super { Formatted, Replaceable, Leave }

/**
 * [Super.Replaceable] is a missing or short superblock, or one of at most 8192
 * bytes that is entirely `0xFF` or entirely `0x00`. A launch may replace it
 * when the folder holds no saves. A block that cannot be read, or one that is
 * longer than 8192 and not a formatted 1.2 card, is left as it is. A formatted
 * block whose version is not 1.2 is left as it is.
 */
private fun superblock(children: List<SaveFolderEntry>, folder: SaveFolderAccess, parent: String): Super {
    val file = children.firstOrNull { !it.isFolder && it.name == Ps2Superblock.FILE_NAME } ?: return Super.Replaceable
    if (file.size > SUPER_READ) return Super.Leave
    val bytes = folder.read("$parent/${Ps2Superblock.FILE_NAME}", SUPER_READ) ?: return Super.Leave
    if (Ps2Superblock.isFormatted(bytes)) return Super.Formatted
    if (bytes.size < Ps2Superblock.SIZE || erased(bytes)) return Super.Replaceable
    return Super.Leave
}

/** An erased block ARMSX2 leaves when a folder card has not been formatted. Longer than 8192 stays. */
private fun erased(bytes: ByteArray): Boolean {
    if (bytes.isEmpty() || bytes.size > Ps2Superblock.SIZE) return false
    val mark = bytes[0]
    if (mark != 0.toByte() && mark != 0xFF.toByte()) return false
    return bytes.all { it == mark }
}

private const val INI_LIMIT = 256 * 1024
private const val SETTINGS_LIMIT = 2 * 1024 * 1024
private const val SUPER_READ = 64 * 1024
private const val CARD_RAW = 8_388_608L
private const val CARD_ECC = 8_650_752L
private const val PAGE = 512

/**
 * Whether the file card at [path] holds a save. False when it is blank, every
 * byte `0xFF`, or its root directory holds only `.` and `..`. Null when it
 * cannot be read as an 8 MB PS2 card, with or without ECC pages. Reads the
 * superblock, then the FAT pages and root clusters it needs. A whole-card read
 * happens only while checking a card whose superblock page is all `0xFF`.
 */
private fun fileCardHoldsSaves(folder: SaveFolderAccess, path: String, size: Long): Boolean? {
    val stride = when (size) {
        CARD_RAW -> PAGE
        CARD_ECC -> PAGE + 16
        else -> return null
    }
    val first = folder.read(path, 0, PAGE) ?: return null
    if (first.size < PAGE) return null
    if (first.all { it == 0xFF.toByte() }) return if (blankFile(folder, path, size)) false else null
    return directoryHoldsSaves(CardPages { page -> readCardPage(folder, path, stride, page) })
}

/**
 * Whether [card] holds a save. False when the only entries in use are `.` and
 * `..`. Null when it cannot be read as an 8 MB PS2 card. A card whose bytes are
 * all `0xFF` holds nothing.
 */
internal fun fileCardHoldsSaves(card: ByteArray?): Boolean? {
    if (card == null) return null
    val stride = when (card.size) {
        CARD_RAW.toInt() -> PAGE
        CARD_ECC.toInt() -> PAGE + 16
        else -> return null
    }
    if (card.isNotEmpty() && card.all { it == 0xFF.toByte() }) return false
    return directoryHoldsSaves(CardPages { page ->
        val at = page * stride
        if (page < 0 || at < 0 || at + PAGE > card.size) null else card.copyOfRange(at.toInt(), (at + PAGE).toInt())
    })
}

/** True when every byte of the file is `0xFF`. The superblock page is already all `0xFF`. */
private fun blankFile(folder: SaveFolderAccess, path: String, size: Long): Boolean {
    var offset = 0L
    while (offset < size) {
        val ask = minOf(Ps2Card.BLANK_CHUNK.toLong(), size - offset).toInt()
        val bytes = folder.read(path, offset, ask) ?: return false
        if (bytes.size != ask || bytes.any { it != 0xFF.toByte() }) return false
        offset += ask
    }
    return size > 0
}

private fun readCardPage(folder: SaveFolderAccess, path: String, stride: Int, page: Long): ByteArray? {
    if (page < 0) return null
    val at = page * stride
    if (at < 0) return null
    val bytes = folder.read(path, at, PAGE) ?: return null
    return bytes.takeIf { it.size == PAGE }
}

private fun interface CardPages {
    /** The 512 data bytes of [page], or null when they cannot be read. */
    fun page(page: Long): ByteArray?
}

private fun directoryHoldsSaves(pages: CardPages): Boolean? {
    val geometry = cardGeometry(pages) ?: return null
    val count = geometry.count
    if (count < 2 || count > ENTRY_CAP) return null
    var index = 0
    var relative = geometry.root
    val seen = HashSet<Int>()
    var walked = 0
    while (index < count && walked < CLUSTER_CAP) {
        if (!seen.add(relative)) return null
        val cluster = readCluster(pages, geometry, geometry.allocOffset + relative) ?: return null
        val perCluster = cluster.size / ENTRY
        if (perCluster <= 0) return null
        for (slot in 0 until perCluster) {
            if (index >= count) break
            val entry = cluster.copyOfRange(slot * ENTRY, (slot + 1) * ENTRY)
            if (entryInUse(entry) && !entryIsDot(entry)) return true
            index++
        }
        if (index >= count) return false
        val next = fatNext(pages, geometry, relative) ?: return null
        // The chain ended before the directory's own count was read, so it is not an empty card.
        if (next < 0) return null
        relative = next
        walked++
    }
    return if (index >= count) false else null
}

private class Geometry(
    val pagesPerCluster: Int,
    val allocOffset: Int,
    val root: Int,
    val count: Int,
    val indirect: IntArray,
)

private fun cardGeometry(pages: CardPages): Geometry? {
    val first = pages.page(0) ?: return null
    if (first.size < 0x160) return null
    val magic = "Sony PS2 Memory Card Format ".toByteArray(Charsets.US_ASCII)
    if (!first.copyOfRange(0, magic.size).contentEquals(magic)) return null
    val pagesPerCluster = u16(first, 0x2A)
    val clusters = u32i(first, 0x30)
    val alloc = u32i(first, 0x34)
    val root = u32i(first, 0x3C)
    if (pagesPerCluster != 1 && pagesPerCluster != 2) return null
    if (u16(first, 0x28) != PAGE) return null
    if (clusters <= 0 || alloc < 0 || root < 0 || alloc.toLong() + root >= clusters) return null
    val indirect = IntArray(32) { u32i(first, 0x50 + it * 4) }
    val rootCluster = readCluster(pages, pagesPerCluster, alloc + root) ?: return null
    if (rootCluster.size < ENTRY) return null
    val dot = rootCluster.copyOfRange(0, ENTRY)
    if (!entryIsDot(dot) || !dot.copyOfRange(0x40, 0x42).contentEquals(byteArrayOf('.'.code.toByte(), 0))) return null
    val count = u32i(dot, 4)
    return Geometry(pagesPerCluster, alloc, root, count, indirect)
}

private fun readCluster(pages: CardPages, geometry: Geometry, cluster: Int): ByteArray? =
    readCluster(pages, geometry.pagesPerCluster, cluster)

private fun readCluster(pages: CardPages, pagesPerCluster: Int, cluster: Int): ByteArray? {
    if (cluster < 0) return null
    val out = ByteArray(PAGE * pagesPerCluster)
    for (page in 0 until pagesPerCluster) {
        val data = pages.page(cluster.toLong() * pagesPerCluster + page) ?: return null
        data.copyInto(out, page * PAGE)
    }
    return out
}

/** The next relative cluster of a directory, or null when the chain cannot be followed. -1 is the end. */
private fun fatNext(pages: CardPages, geometry: Geometry, relative: Int): Int? {
    val per = (PAGE * geometry.pagesPerCluster) / 4
    if (per <= 0 || relative < 0) return null
    val indirectIndex = relative / per
    val fatOffset = relative % per
    val doubled = indirectIndex / per
    val indirectOffset = indirectIndex % per
    if (doubled !in geometry.indirect.indices) return null
    val indirectCluster = geometry.indirect[doubled]
    val indirect = readCluster(pages, geometry, indirectCluster) ?: return null
    val fatCluster = u32i(indirect, indirectOffset * 4)
    val fat = readCluster(pages, geometry, fatCluster) ?: return null
    val value = u32i(fat, fatOffset * 4)
    if (value == -1) return -1
    if (value and 0x80000000.toInt() == 0) return -1
    val next = value and 0x7FFFFFFF
    return if (next == relative) null else next
}

private fun entryInUse(entry: ByteArray): Boolean {
    val mode = u32i(entry, 0)
    return mode != -1 && mode and 0x8000 != 0
}

private fun entryIsDot(entry: ByteArray): Boolean {
    if (entry.size < 0x43 || entry[0x40] != '.'.code.toByte()) return false
    val second = entry[0x41]
    return second == 0.toByte() || (second == '.'.code.toByte() && entry[0x42] == 0.toByte())
}

private fun u16(bytes: ByteArray, at: Int): Int =
    (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

private fun u32i(bytes: ByteArray, at: Int): Int {
    if (at < 0 || at + 4 > bytes.size) return -1
    return (bytes[at].toInt() and 0xFF) or
        ((bytes[at + 1].toInt() and 0xFF) shl 8) or
        ((bytes[at + 2].toInt() and 0xFF) shl 16) or
        ((bytes[at + 3].toInt() and 0xFF) shl 24)
}

private const val ENTRY = 512
private const val ENTRY_CAP = 4096
private const val CLUSTER_CAP = 64
