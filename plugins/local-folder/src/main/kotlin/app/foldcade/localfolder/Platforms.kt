package app.foldcade.localfolder

import java.util.Locale

/**
 * A platform a local folder can classify a game into.
 * [id] is stable. [name] is the label shown with the file name.
 */
data class FolderPlatform(
    val id: String,
    val name: String,
)

/**
 * Foldcade's folder and extension map.
 * A unique extension classifies a file on its own.
 * zip, 7z, iso, and other shared suffixes need a platform folder.
 * Display metadata is not part of this map.
 */
internal object PlatformCatalog {
    val specs: List<PlatformSpec> = listOf(
        spec(
            id = "game-boy",
            name = "Game Boy",
            folders = setOf("gb", "game boy", "gameboy"),
            extensions = setOf("gb"),
            aliases = setOf("gb"),
        ),
        spec(
            id = "game-boy-color",
            name = "Game Boy Color",
            folders = setOf("gbc", "game boy color", "gameboy color"),
            extensions = setOf("gbc"),
            aliases = setOf("gbc"),
        ),
        spec(
            id = "game-boy-advance",
            name = "Game Boy Advance",
            folders = setOf("gba", "game boy advance", "gameboy advance"),
            extensions = setOf("gba"),
            aliases = setOf("gba"),
        ),
        spec(
            id = "nes",
            name = "Nintendo Entertainment System",
            folders = setOf("nes", "famicom", "nintendo entertainment system"),
            extensions = setOf("nes", "fds"),
            aliases = setOf("famicom"),
        ),
        spec(
            id = "snes",
            name = "Super Nintendo",
            folders = setOf(
                "snes",
                "super nintendo",
                "super nintendo entertainment system",
                "super famicom",
            ),
            extensions = setOf("sfc", "smc"),
            aliases = setOf("sfam"),
        ),
        spec(
            id = "nintendo-64",
            name = "Nintendo 64",
            folders = setOf("n64", "nintendo 64"),
            extensions = setOf("n64", "z64", "v64"),
            aliases = setOf("n64"),
        ),
        spec(
            id = "gamecube",
            name = "GameCube",
            folders = setOf("gamecube", "game cube", "gc", "ngc"),
            extensions = setOf("gcm"),
            folderOnlyExtensions = setOf("iso", "rvz"),
            aliases = setOf("ngc"),
        ),
        spec(
            id = "wii",
            name = "Wii",
            folders = setOf("wii", "nintendo wii"),
            extensions = setOf("wbfs"),
            folderOnlyExtensions = setOf("iso", "rvz"),
        ),
        spec(
            id = "nintendo-switch",
            name = "Nintendo Switch",
            folders = setOf("switch", "nintendo switch", "nsw"),
            extensions = setOf("nsp", "xci"),
            // RomM's slug is switch.
            aliases = setOf("switch"),
        ),
        spec(
            id = "nintendo-ds",
            name = "Nintendo DS",
            folders = setOf("ds", "nds", "nintendo ds", "dsi", "nintendo dsi"),
            extensions = setOf("nds", "dsi"),
            // RomM's slug is nds. The scan still stores nintendo-ds.
            aliases = setOf("nds"),
        ),
        spec(
            id = "nintendo-3ds",
            name = "Nintendo 3DS",
            folders = setOf("3ds", "n3ds", "nintendo 3ds", "new 3ds", "new nintendo 3ds"),
            // .3ds is best-effort. Decrypted .cci and .zcci are the formats to prefer.
            extensions = setOf("cci", "zcci", "3ds"),
            // RomM's slug is 3ds. new-nintendo-3ds is the same platform.
            // n3ds stays as a folder-style alias.
            aliases = setOf("3ds", "n3ds", "new-nintendo-3ds"),
        ),
        spec(
            id = "playstation",
            name = "PlayStation",
            folders = setOf("ps1", "psx", "playstation"),
            // .m3u is the multi-disc game. The scan hides the files that playlist names.
            folderOnlyExtensions = setOf("cue", "chd", "pbp", "img", "iso", "m3u"),
            aliases = setOf("psx"),
        ),
        spec(
            id = "playstation-2",
            name = "PlayStation 2",
            folders = setOf("ps2", "playstation 2"),
            folderOnlyExtensions = setOf("iso", "chd", "m3u"),
            aliases = setOf("ps2"),
        ),
        spec(
            id = "psp",
            name = "PlayStation Portable",
            folders = setOf("psp", "playstation portable"),
            extensions = setOf("cso"),
            folderOnlyExtensions = setOf("iso", "pbp"),
        ),
        spec(
            id = "genesis",
            name = "Genesis",
            folders = setOf("genesis", "mega drive", "megadrive", "md"),
            extensions = setOf("gen", "smd"),
            // .md is also markdown, so a bare file is not a Genesis game.
            folderOnlyExtensions = setOf("md"),
        ),
        spec(
            id = "master-system",
            name = "Master System",
            folders = setOf("master system", "sega master system", "sms"),
            extensions = setOf("sms"),
            aliases = setOf("sms"),
        ),
        spec(
            id = "game-gear",
            name = "Game Gear",
            folders = setOf("game gear", "gamegear", "gg"),
            extensions = setOf("gg"),
            aliases = setOf("gamegear"),
        ),
        spec(
            id = "saturn",
            name = "Saturn",
            folders = setOf("saturn", "sega saturn"),
            folderOnlyExtensions = setOf("cue", "chd", "iso", "ccd", "mds", "m3u"),
        ),
        spec(
            id = "dreamcast",
            name = "Dreamcast",
            folders = setOf("dreamcast", "sega dreamcast", "dc"),
            extensions = setOf("gdi", "cdi"),
            folderOnlyExtensions = setOf("chd", "cue", "iso", "m3u"),
            // RomM's slug is dc.
            aliases = setOf("dc"),
        ),
    )

    val byId: Map<String, PlatformSpec>
    private val byFolder: Map<String, FolderPlatform>
    private val byExtension: Map<String, FolderPlatform>

    init {
        val ids = LinkedHashMap<String, PlatformSpec>()
        val names = LinkedHashSet<String>()
        val folders = LinkedHashMap<String, FolderPlatform>()
        val extensions = LinkedHashMap<String, FolderPlatform>()
        val unique = HashSet<String>()
        for (spec in specs) {
            require(ids.put(spec.platform.id, spec) == null) {
                "Duplicate platform id ${spec.platform.id}"
            }
            require(names.add(spec.platform.name)) {
                "Duplicate platform name ${spec.platform.name}"
            }
            for (folder in spec.folders) {
                for (key in folderKeys(folder)) {
                    val prior = folders.put(key, spec.platform)
                    require(prior == null || prior == spec.platform) {
                        "Folder '$key' is claimed by ${prior?.id} and ${spec.platform.id}"
                    }
                }
            }
            for (ext in spec.extensions) {
                require(ext == normalizeExtension(ext)) { "Extension '$ext' is not normalized" }
                require(ext !in SHARED_ARCHIVES) { "Extension '$ext' is a shared archive" }
                require(ext !in NEVER_EXTENSIONS) { "Extension '$ext' cannot be a game" }
                require(extensions.put(ext, spec.platform) == null) {
                    "Extension '$ext' is claimed twice"
                }
                unique += ext
            }
            for (ext in spec.folderOnlyExtensions) {
                require(ext == normalizeExtension(ext)) { "Extension '$ext' is not normalized" }
                require(ext !in SHARED_ARCHIVES) { "Extension '$ext' is a shared archive" }
                require(ext !in NEVER_EXTENSIONS) { "Extension '$ext' cannot be a game" }
                require(ext !in spec.extensions) { "Extension '$ext' is both unique and shared" }
            }
        }
        for (spec in specs) {
            for (ext in spec.folderOnlyExtensions) {
                require(ext !in unique) { "Extension '$ext' is unique and also folder-only" }
            }
        }
        val claimed = LinkedHashMap<String, String>()
        for (spec in specs) {
            claimed[spec.platform.id.lowercase(Locale.ROOT)] = spec.platform.id
        }
        for (spec in specs) {
            for (alias in spec.aliases) {
                require(alias.isNotEmpty() && alias == alias.lowercase(Locale.ROOT)) {
                    "Alias '$alias' is not a lowercase id"
                }
                val prior = claimed.put(alias, spec.platform.id)
                require(prior == null) {
                    "Alias '$alias' on ${spec.platform.id} collides with $prior"
                }
            }
        }
        byId = ids
        byFolder = folders
        byExtension = extensions
    }

    fun platformForFolderKey(key: String): FolderPlatform? = byFolder[key]

    fun platformForUniqueExtension(extension: String): FolderPlatform? = byExtension[extension]
}

internal data class PlatformSpec(
    val platform: FolderPlatform,
    val folders: Set<String>,
    val extensions: Set<String>,
    val folderOnlyExtensions: Set<String>,
    val aliases: Set<String>,
)

/** Archives need a platform folder. melonDS opens zip and 7z; so does every other folder here. */
internal val SHARED_ARCHIVES: Set<String> = setOf("zip", "7z")

/**
 * Sidecars and disc tracks. A .bin next to a .cue is the track, not a second game.
 * .md is absent on purpose: inside a Genesis folder it is a ROM extension.
 */
internal val NEVER_EXTENSIONS: Set<String> = setOf(
    "sav",
    "srm",
    "dsv",
    "state",
    "png",
    "jpg",
    "jpeg",
    "gif",
    "webp",
    "bmp",
    "txt",
    "nfo",
    "xml",
    "json",
    "html",
    "pdf",
    "mp3",
    "ogg",
    "wav",
    "mp4",
    "url",
    "cfg",
    "ini",
    "toml",
    "m3u8",
    "bin",
    "exe",
    "apk",
)

internal val DOC_STEMS: Set<String> = setOf(
    "readme",
    "read me",
    "license",
    "licence",
    "copying",
    "changelog",
    "notice",
    "authors",
)

internal fun folderKeys(alias: String): Set<String> {
    val normalized = normalizeFolderName(alias)
    if (normalized.isEmpty()) return emptySet()
    val compact = normalized.replace(" ", "")
    return if (compact == normalized) setOf(normalized) else setOf(normalized, compact)
}

internal fun normalizeFolderName(raw: String): String {
    val withoutNotes = raw.lowercase(Locale.ROOT).replace(Regex("\\([^)]*\\)"), " ")
    return withoutNotes
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex(" +"), " ")
}

internal fun normalizeExtension(raw: String): String =
    raw.trim().removePrefix(".").lowercase(Locale.ROOT)

internal fun fileExtension(displayName: String): String? {
    val name = displayName.trim()
    if (name.startsWith('.')) return null
    val dot = name.lastIndexOf('.')
    if (dot <= 0 || dot == name.lastIndex) return null
    return name.substring(dot + 1).lowercase(Locale.ROOT)
}

internal fun titleFromFileName(displayName: String): String {
    val name = displayName.trim()
    val dot = name.lastIndexOf('.')
    if (dot <= 0) return name
    return name.substring(0, dot)
}

/** Folder title, ignoring a trailing "roms". "3ds" does not match "nds". */
fun platformFromFolderName(folderName: String): FolderPlatform? {
    val normalized = normalizeFolderName(folderName)
    if (normalized.isEmpty()) return null
    val stripped = when {
        normalized.endsWith(" roms") -> normalized.removeSuffix(" roms").trim()
        normalized.endsWith(" rom") -> normalized.removeSuffix(" rom").trim()
        else -> normalized
    }
    val keys = LinkedHashSet<String>()
    for (value in listOf(normalized, stripped)) {
        if (value.isEmpty()) continue
        keys += value
        keys += value.replace(" ", "")
    }
    return keys.firstNotNullOfOrNull { PlatformCatalog.platformForFolderKey(it) }
}

/**
 * Platform for an extension that belongs to one system.
 * zip, 7z, iso, chd, and other shared suffixes return null.
 */
fun platformFromExtension(extension: String): FolderPlatform? {
    val ext = normalizeExtension(extension)
    if (ext.isEmpty() || ext in NEVER_EXTENSIONS || ext in SHARED_ARCHIVES) return null
    return PlatformCatalog.platformForUniqueExtension(ext)
}

internal fun resolvePlatform(extension: String, folder: FolderPlatform?): FolderPlatform? {
    val ext = normalizeExtension(extension)
    if (ext.isEmpty() || ext in NEVER_EXTENSIONS) return null
    val spec = folder?.let { PlatformCatalog.byId[it.id] }
    if (spec != null && spec.accepts(ext)) return spec.platform
    return PlatformCatalog.platformForUniqueExtension(ext)
}

private fun PlatformSpec.accepts(extension: String): Boolean =
    extension in extensions ||
        extension in folderOnlyExtensions ||
        extension in SHARED_ARCHIVES

private fun spec(
    id: String,
    name: String,
    folders: Set<String>,
    extensions: Set<String> = emptySet(),
    folderOnlyExtensions: Set<String> = emptySet(),
    aliases: Set<String> = emptySet(),
): PlatformSpec = PlatformSpec(
    platform = FolderPlatform(id, name),
    folders = folders,
    extensions = extensions,
    folderOnlyExtensions = folderOnlyExtensions,
    aliases = aliases,
)
