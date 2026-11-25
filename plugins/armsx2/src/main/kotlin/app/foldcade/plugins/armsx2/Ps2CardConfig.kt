package app.foldcade.plugins.armsx2

import app.foldcade.api.plugin.LiveSignal
import app.foldcade.api.plugin.SaveFolderAccess
import app.foldcade.api.plugin.SignalStrength
import java.nio.file.Paths
import java.util.Locale

/** The readable files describe a card selection. The running emulator's private settings can differ. */
class ArmsX2CardConfig internal constructor(
    val nativeReadable: Boolean,
    val directory: String?,
    val outsideDirectoryId: String?,
    val cardName: String?,
    val signals: List<LiveSignal>,
)

internal fun readPs2CardConfig(folder: SaveFolderAccess, serial: String?): ArmsX2CardConfig {
    val children = folder.list("") ?: return ArmsX2CardConfig(false, null, null, null, emptyList())
    val iniEntry = children.firstOrNull { it.name == Ps2Card.INI }
    val ini = if (iniEntry?.isFolder == false) folder.read(Ps2Card.INI, INI_LIMIT)?.decodeToString() else null
    val mirror = folder.read(Ps2Card.SETTINGS, SETTINGS_LIMIT)?.decodeToString()
    val mirroredSlot = mirror?.let { slotFromMirror(it, serial) }
    val slot = mirroredSlot ?: ini?.let(::parseSlot1)
        ?: Slot1(enabled = true, filename = Ps2Card.DEFAULT_FILE).takeIf { iniEntry == null }
    val cardName = slot?.takeIf { it.enabled }?.filename?.let(Ps2Card::cardName)
    val directory = cardDirectory(ini.orEmpty(), folder.storageDocumentId)
    // A present but unreadable INI could redirect writes. Keep the legacy default only when it is absent.
    val inside = if (iniEntry == null || ini != null) directory.relative else null
    val relative = if (inside == Ps2Card.MEMCARDS)
        children.firstOrNull { it.isFolder && it.name.equals(inside, ignoreCase = true) }?.name ?: inside
    else inside
    val signals = buildList {
        if (ini != null) add(LiveSignal(Ps2Card.INI, directory.outsideDocumentId, folder.modifiedAtMillis(Ps2Card.INI), SignalStrength.Supports))
        if (mirroredSlot != null) add(LiveSignal(Ps2Card.SETTINGS, null, folder.modifiedAtMillis(Ps2Card.SETTINGS), SignalStrength.Supports))
    }
    return ArmsX2CardConfig(ini != null && directory.valid, relative, directory.outsideDocumentId, cardName, signals)
}

internal data class CardDirectory(val relative: String?, val outsideDocumentId: String? = null, val valid: Boolean = true)

/** Native PCSX2 combines a relative MemoryCards path with DataRoot. Only a contained path can be written. */
internal fun cardDirectory(ini: String, rootId: String?): CardDirectory {
    val value = iniValues(ini, "Folders").lastOrNull { it.first == "MemoryCards" }?.second ?: Ps2Card.MEMCARDS
    if (value.any { it == '\\' || it.code < 32 } || "://" in value) return CardDirectory(null, valid = false)
    val path = Paths.get(value)
    if (!path.isAbsolute) {
        val relative = path.normalize().toString()
        if (relative != ".." && !relative.startsWith("../")) return CardDirectory(relative)
    }
    val root = rootId?.let(::sharedStoragePath)
    val absolute = if (path.isAbsolute) path.normalize().toString() else root?.let {
        Paths.get(it).resolve(path).normalize().toString()
    } ?: return CardDirectory(null)
    val documentId = sharedStorageId(absolute) ?: return CardDirectory(null)
    val rootPath = root?.let(Paths::get)
    val absolutePath = sharedStoragePath(documentId)?.let(Paths::get)
    if (rootPath != null && absolutePath != null && absolutePath.startsWith(rootPath)) {
        return CardDirectory(rootPath.relativize(absolutePath).toString())
    }
    return CardDirectory(null, outsideDocumentId = documentId)
}

private fun sharedStoragePath(id: String): String? {
    if (':' !in id) return null
    val volume = id.substringBefore(':')
    val base = when {
        volume.equals("primary", ignoreCase = true) -> "/storage/emulated/0"
        VOLUME_ID.matches(volume) -> "/storage/${volume.uppercase(Locale.ROOT)}"
        else -> return null
    }
    return Paths.get(base, id.substringAfter(':')).normalize().toString()
}

private fun sharedStorageId(path: String): String? {
    for (base in listOf("/storage/emulated/0", "/sdcard", "/mnt/sdcard", "/storage/self/primary")) {
        if (path == base || path.startsWith("$base/")) return "primary:${path.removePrefix(base).trimStart('/')}"
    }
    val tail = path.takeIf { it.startsWith("/storage/") }?.removePrefix("/storage/") ?: return null
    val volume = tail.substringBefore('/')
    if (!VOLUME_ID.matches(volume)) return null
    return "${volume.lowercase(Locale.ROOT)}:${tail.substringAfter('/', "")}"
}

private val VOLUME_ID = Regex("[a-fA-F0-9]{4}-[a-fA-F0-9]{4}")
private const val SETTINGS_LIMIT = 2 * 1024 * 1024
