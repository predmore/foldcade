package app.foldcade.plugins.retroarch

import app.foldcade.api.plugin.SignalStrength

sealed interface RetroArchConfigFile {
    data class Read(val text: String) : RetroArchConfigFile
    data object Missing : RetroArchConfigFile
    data object Unreadable : RetroArchConfigFile
}

data class RetroArchSavePath(val path: String?, val strength: SignalStrength)

/** Resolve the frontend's single .srm path for the supplied launch. No files are created. */
fun retroArchSavePath(
    configFile: String,
    romPath: String,
    coreName: String,
    read: (String) -> RetroArchConfigFile,
    isDirectory: (String) -> Boolean,
): RetroArchSavePath {
    val uncertain = RetroArchSavePath(null, SignalStrength.Supports)
    val config = absoluteRetroArchPath(configFile) ?: return uncertain
    val rom = absoluteRetroArchPath(romPath) ?: return uncertain
    val contentDir = rom.substringBeforeLast('/')
    val contentName = contentDir.substringAfterLast('/')
    val gameName = saveBaseName(rom.substringAfterLast('/'))
    if (listOf(coreName, gameName, contentName).any { !configPathPart(it) }) return uncertain

    val global = (read(config) as? RetroArchConfigFile.Read)?.let { parseSaveConfig(it.text) } ?: return uncertain
    val effective = global.toMutableMap()
    if (global["auto_overrides_enable"].asConfigBool(default = true) ?: return uncertain) {
        // RetroArch chooses the override directory and whether to autoload from the global config.
        // With the key omitted, Android's default depends on the storage access of that build.
        val setting = global["rgui_config_directory"] ?: return uncertain
        val overrides = when (setting) {
            "default" -> config.substringBeforeLast('/')
            else -> absoluteRetroArchPath(setting) ?: return uncertain
        }
        for (name in listOf(coreName, contentName, gameName).distinct()) {
            when (val file = read("$overrides/$coreName/$name.cfg")) {
                is RetroArchConfigFile.Read -> effective.putAll(parseSaveConfig(file.text) ?: return uncertain)
                RetroArchConfigFile.Missing -> Unit
                RetroArchConfigFile.Unreadable -> return uncertain
            }
        }
    }

    val beside = effective["savefiles_in_content_dir"].asConfigBool(default = false) ?: return uncertain
    val sortContent = effective["sort_savefiles_by_content_enable"].asConfigBool(default = false) ?: return uncertain
    val sortCore = effective["sort_savefiles_enable"].asConfigBool(default = true) ?: return uncertain
    val base = if (beside) contentDir else effective["savefile_directory"]?.let(::absoluteRetroArchPath) ?: return uncertain
    val sorted = buildString {
        append(base)
        if (sortContent) append('/').append(contentName)
        if (sortCore) append('/').append(coreName)
    }
    val path = absoluteRetroArchPath("$sorted/$gameName.srm") ?: return uncertain
    // The frontend ignores an invalid configured directory, or falls back if it can't create a sorted one.
    val proven = isDirectory(base) && (base == sorted || isDirectory(sorted))
    return RetroArchSavePath(path, if (proven) SignalStrength.Proves else SignalStrength.Supports)
}

/** Only ordinary absolute paths whose segments can be compared across Android storage aliases. */
fun absoluteRetroArchPath(path: String): String? {
    if (!path.startsWith('/') || path.length >= 1024) return null
    val parts = path.split('/').filter(String::isNotEmpty)
    if (parts.isEmpty() || parts.any { !configPathPart(it) }) return null
    val normalized = parts.joinToString("/", prefix = "/")
    val aliases = mapOf("/sdcard" to SHARED_STORAGE, "/mnt/sdcard" to SHARED_STORAGE,
        "/storage/self/primary" to SHARED_STORAGE, LEGACY_DATA to INTERNAL_DATA)
    val alias = aliases.entries.firstOrNull { normalized == it.key || normalized.startsWith("${it.key}/") }
    return if (alias == null) normalized else alias.value + normalized.removePrefix(alias.key)
}

internal fun saveBaseName(fileName: String): String = fileName.substringBeforeLast('.').ifEmpty { fileName }

private fun configPathPart(part: String) = part.isNotEmpty() && part != "." && part != ".." &&
    part.none { it.isISOControl() || it == '\\' || it == '|' || it == '"' || it == '#' }

private val saveSettings = setOf("auto_overrides_enable", "rgui_config_directory", "savefile_directory",
    "savefiles_in_content_dir", "sort_savefiles_by_content_enable", "sort_savefiles_enable")
private val configValue = Regex("""^\s*=\s*(?:"([^"\r\n]*)"|([^\s#"]+))\s*(?:#.*)?$""")

/** A small subset of RetroArch config syntax. Ambiguous settings can't establish a mismatch. */
private fun parseSaveConfig(text: String): Map<String, String>? {
    if ('\u0000' in text || text.startsWith('\uFEFF')) return null
    val settings = mutableMapOf<String, String>()
    for (line in text.lineSequence()) {
        val trimmed = line.trimStart(' ', '\t', '\r')
        if (trimmed.startsWith("#include") || trimmed.startsWith("#reference")) return null
        val key = trimmed.takeWhile { !it.isWhitespace() && it != '=' }
        if (key !in saveSettings) continue
        val rest = trimmed.removePrefix(key)
        if (rest.firstOrNull() !in listOf(' ', '\t')) return null
        val parsed = configValue.matchEntire(rest) ?: return null
        val value = parsed.groups[1]?.value ?: parsed.groupValues[2]
        val previous = settings.put(key, value)
        if (previous != null && previous != value) return null
    }
    return settings
}

private fun String?.asConfigBool(default: Boolean): Boolean? = when (this) {
    null -> default
    "true", "1" -> true
    "false", "0" -> false
    else -> null
}
