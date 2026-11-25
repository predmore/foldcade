package app.foldcade.plugins.duckstation

import app.foldcade.api.plugin.SaveFolderAccess
import java.nio.charset.CharacterCodingException

/** Card settings for one possible game. Only the emulator can identify every active game INI. */
data class DuckStationCardSettings(
    val directory: String?,
    val card1Type: String,
    val card1Path: String?,
    val card2Path: String?,
)

/**
 * Read the global settings and the alternatives after applying each game's overrides.
 * A caller without DuckStation's disc-set/serial lookup can use values that agree across
 * the alternatives. An unreadable or oversized set of overrides gives no config evidence.
 */
fun readDuckStationCardSettings(data: SaveFolderAccess): List<DuckStationCardSettings>? {
    val globalIni = data.read("settings.ini", MAX_INI_BYTES)?.let(::memoryCardIni) ?: return null
    val global = effectiveCardSettings(globalIni, emptyMap())
    val root = data.list("") ?: return null
    if (root.none { it.name == "gamesettings" }) return listOf(global)
    val files = data.list("gamesettings")?.filter { !it.isFolder && it.name.endsWith(".ini", ignoreCase = true) }
        ?: return null
    if (files.size > MAX_GAME_INIS) return null
    return buildSet {
        add(global)
        for (file in files) {
            val override = data.read("gamesettings/${file.name}", MAX_INI_BYTES)?.let(::memoryCardIni) ?: return null
            add(effectiveCardSettings(globalIni, override))
        }
    }.toList()
}

private fun effectiveCardSettings(global: Map<String, String>, override: Map<String, String>): DuckStationCardSettings {
    fun setting(key: String) = override[key] ?: global[key]
    return DuckStationCardSettings(
        directory = setting("directory")?.ifEmpty { DuckStationPlayer.MEMCARDS },
        card1Type = override["card1type"].cardType() ?: global["card1type"].cardType() ?: "PerGameTitle",
        card1Path = setting("card1path")?.takeIf(String::isNotEmpty),
        card2Path = setting("card2path")?.takeIf(String::isNotEmpty),
    )
}

private fun String?.cardType(): String? = when (this) {
    "None", "NonPersistent", "Shared", "PerGame", "PerGameTitle", "PerGameFileTitle" -> this
    else -> null
}

private fun memoryCardIni(bytes: ByteArray): Map<String, String>? {
    val ini = try { bytes.decodeToString(throwOnInvalidSequence = true).removePrefix("\uFEFF") }
        catch (_: CharacterCodingException) { return null }
    if ('\u0000' in ini) return null
    val values = mutableMapOf<String, String>()
    var inSection = false
    for (line in ini.lineSequence().map(String::trim)) {
        if (line.isEmpty() || line.startsWith(';') || line.startsWith('#')) continue
        if (line.startsWith('[')) {
            if (!line.endsWith(']')) return null
            inSection = line.substring(1, line.lastIndex).trim().equals("MemoryCards", ignoreCase = true)
            continue
        }
        if (!inSection) continue
        val split = line.indexOf('=')
        if (split <= 0) return null
        val key = line.substring(0, split).trim().lowercase()
        values[key] = line.substring(split + 1).trim().removeSurrounding("\"")
    }
    return values
}

private const val MAX_INI_BYTES = 128 * 1024
private const val MAX_GAME_INIS = 64
