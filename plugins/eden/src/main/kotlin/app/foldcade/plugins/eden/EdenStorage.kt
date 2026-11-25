package app.foldcade.plugins.eden

import app.foldcade.api.plugin.GameFileReader
import app.foldcade.api.plugin.SaveFolderAccess
import app.foldcade.api.plugin.SaveFolderPart
import java.util.Locale

private const val CONFIG = "config/config.ini"
private const val PROFILES = "system/save/8000000000000010/su/avators/profiles.dat"
private const val PROFILES_SIZE = 0x650

data class EdenStorageConfig(val nandDirectory: String?, val saveDirectory: String?, val currentUser: Int?)

/** Read Eden's global settings. An unknown config cannot establish a default save location. */
fun readEdenStorageConfig(folder: SaveFolderAccess): EdenStorageConfig? {
    val text = folder.read(CONFIG, 1024 * 1024)?.let {
        runCatching { it.decodeToString(throwOnInvalidSequence = true) }.getOrNull()
    } ?: return null
    if ('\u0000' in text) return null
    var section: String? = null
    var recognizedSection = false
    val settings = mutableMapOf<String, String>()
    for (raw in text.removePrefix("\uFEFF").lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith(';') || line.startsWith('#')) continue
        if (line.startsWith('[')) {
            if (!line.endsWith(']')) return null
            section = line.substring(1, line.lastIndex).replace("%20", " ").lowercase(Locale.ROOT)
            if (section == "data storage" || section == "system") recognizedSection = true
            continue
        }
        val separator = line.indexOf('=')
        if (separator < 1) return null
        val key = line.substring(0, separator).trim().lowercase(Locale.ROOT)
        val setting = when (section) {
            "data storage" -> key.takeIf { it == "nand_directory" || it == "save_directory" }
            "system" -> key.takeIf { it == "current_user" || it == "current_user\\default" }
            else -> null
        } ?: continue
        val value = line.substring(separator + 1).trim().replace("\"", "")
        if (settings.put(setting, value) != null) return null
    }
    if (!recognizedSection) return null
    val currentUser = when (settings["current_user\\default"]?.lowercase(Locale.ROOT)) {
        null, "true", "yes", "on", "1" -> 0
        "false", "no", "off", "0" -> settings["current_user"]?.toIntOrNull()?.takeIf { it in 0..7 }
        else -> null
    }
    return EdenStorageConfig(settings["nand_directory"]?.takeIf { it.isNotEmpty() },
        settings["save_directory"]?.takeIf { it.isNotEmpty() }, currentUser)
}

sealed interface EdenProfile {
    data object Missing : EdenProfile
    data object Unreadable : EdenProfile
    /** Lowercase hex in the order stored in profiles.dat, also used by the account layout. */
    data class User(val uuid: String) : EdenProfile
}

/** The profile database is under NAND even when SaveDir points somewhere else. */
fun readEdenProfile(nand: SaveFolderAccess, prefix: String, index: Int?): EdenProfile {
    if (index == null) return EdenProfile.Unreadable
    val path = below(prefix, PROFILES)
    val entry = nand.list(path.substringBeforeLast('/'))?.firstOrNull { it.name == "profiles.dat" }
        ?: return EdenProfile.Missing
    if (entry.isFolder) return EdenProfile.Unreadable
    val profiles = nand.read(path, PROFILES_SIZE) ?: return EdenProfile.Unreadable
    if (profiles.size != PROFILES_SIZE) return EdenProfile.Unreadable
    // Eden loads valid records in their stored order and skips unused entries.
    val users = (0..7).mapNotNull { entryIndex ->
        val start = 0x10 + entryIndex * 0xC8
        profiles.copyOfRange(start, start + 16).takeIf { uuid -> uuid.any { it != 0.toByte() } }?.toHexString()
    }.distinct()
    return users.getOrNull(index)?.let(EdenProfile::User) ?: EdenProfile.Unreadable
}

/** Match SaveDataFactory::GetFullPath. Both layouts use the same title folder in RomM's ZIP. */
fun edenAccountSave(game: GameFileReader, data: SaveFolderAccess, save: SaveFolderAccess, prefix: String, profile: EdenProfile.User): SaveFolderPart? {
    val title = switchTitleId(game, data) ?: return null
    val account = below(prefix, "user/save/account/${profile.uuid}/$title")
    if (save.list(account)?.any { it.name == "0" && it.isFolder } == true) return SaveFolderPart(title, "$account/0")
    val legacyUuid = profile.uuid.chunked(2).asReversed().joinToString("").uppercase(Locale.ROOT)
    return SaveFolderPart(title, below(prefix, "user/save/0000000000000000/$legacyUuid/$title"))
}

private fun below(prefix: String, path: String): String = if (prefix.isEmpty()) path else "$prefix/$path"
