package app.foldcade.host.backup

import app.foldcade.host.play.PlayArchive
import app.foldcade.host.play.PlayExport
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.*

const val SETUP_FORMAT = 1
data class BackupFolder(val uri: String, val name: String)
data class BackupSummary(val games: Int = 0, val collections: Int = 0, val hidden: Int = 0, val theme: String = "Afterglow")
data class BackupManifest(
    val createdMillis: Long,
    val versionName: String,
    val versionCode: Long,
    val device: String,
    val summary: BackupSummary,
    val folders: List<BackupFolder> = emptyList(),
    /** Names only, never save URIs, marks or pairing material. */
    val saveFolders: List<String> = emptyList(),
    val computers: Boolean = false,
    val retroArchCores: Boolean = false,
    val edenFolder: Boolean = false,
)
data class SetupBackup(
    val manifest: BackupManifest,
    val settings: Map<String, Any>,
    val home: String,
    val sameGames: String,
    val artChoices: String,
    val playHistory: ByteArray,
    val themes: Map<String, ByteArray> = emptyMap(),
) {
    fun preferences(): Map<String, Any> = settings + mapOf("home_board" to home, "same_games" to sameGames)
    fun play(): PlayExport = PlayArchive.read(playHistory.inputStream())
}
enum class BackupRefusal { Foreign, Newer, Unreadable }
class SetupArchiveException(val reason: BackupRefusal) : IOException(reason.name)

/** A versioned, allowlisted transport. No archive path is ever resolved against the filesystem. */
object SetupArchive {
    private const val MARKER = "foldcade-backup.json"
    private const val MIB = 1024L * 1024
    const val MAX_THEMES_BYTES = 128L * MIB
    private val limits = mapOf(MARKER to MIB, "settings.json" to 4 * MIB, "home.txt" to 8 * MIB,
        "same-games.txt" to 8 * MIB, "art-choices.json" to 8 * MIB, "play-history.zip" to 112 * MIB)
    private val themeName = Regex("themes/[A-Za-z0-9._-]{1,251}\\.zip")

    fun write(output: OutputStream, backup: SetupBackup) {
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, bytes: ByteArray) {
                val limit = limits[name] ?: MAX_THEMES_BYTES
                require(bytes.size <= limit) { "$name is too large" }
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            entry(MARKER, manifestJson(backup.manifest).toString().encodeToByteArray())
            entry("settings.json", settingsJson(BackupSettings.portable(backup.settings) - setOf("home_board", "same_games")).toString().encodeToByteArray())
            entry("home.txt", backup.home.encodeToByteArray())
            entry("same-games.txt", backup.sameGames.encodeToByteArray())
            entry("art-choices.json", backup.artChoices.encodeToByteArray())
            entry("play-history.zip", backup.playHistory)
            require(backup.themes.values.sumOf { it.size.toLong() } <= MAX_THEMES_BYTES) { "themes are too large" }
            backup.themes.toSortedMap().forEach { (name, bytes) ->
                require(themeName.matches("themes/$name")) { "invalid theme name" }
                entry("themes/$name", bytes)
            }
        }
    }

    fun read(input: InputStream): SetupBackup {
        try {
            val entries = LinkedHashMap<String, ByteArray>()
            val seen = HashSet<String>()
            val budget = InflateBudget(288 * MIB)
            var themes = 0L
            ZipInputStream(input).use { zip ->
                var count = 0
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (++count > 4096 || !seen.add(name) || name.startsWith('/') || '\\' in name ||
                        name.split('/').any { it == "." || it == ".." }) throw SetupArchiveException(BackupRefusal.Unreadable)
                    val limit = limits[name] ?: if (themeName.matches(name)) minOf(MAX_THEMES_BYTES - themes, MAX_THEMES_BYTES) else null
                    if (limit != null && !entry.isDirectory) {
                        val bytes = zip.readBounded(limit, budget)
                        entries[name] = bytes
                        if (name.startsWith("themes/")) themes += bytes.size
                    }
                    zip.drainBounded(budget)
                    zip.closeEntry()
                }
            }
            val marker = entries[MARKER] ?: throw SetupArchiveException(BackupRefusal.Foreign)
            val root = Json.parseToJsonElement(marker.decodeToString()).jsonObject
            val format = root.getValue("format").jsonPrimitive.int
            if (format > SETUP_FORMAT) throw SetupArchiveException(BackupRefusal.Newer)
            val migrated = migrate(format, root)
            fun section(name: String): ByteArray = entries[name] ?: throw SetupArchiveException(BackupRefusal.Unreadable)
            val play = section("play-history.zip")
            PlayArchive.read(play.inputStream()) // Validate the nested transport before asking to replace anything.
            val art = section("art-choices.json").decodeToString()
            Json.parseToJsonElement(art).jsonObject
            val home = section("home.txt").decodeToString()
            require(home.lineSequence().firstOrNull() in setOf("v1", "v2")) { "unreadable Home" }
            return SetupBackup(readManifest(migrated), readSettings(section("settings.json").decodeToString()),
                home, section("same-games.txt").decodeToString(), art, play,
                entries.filterKeys { themeName.matches(it) }.mapKeys { it.key.removePrefix("themes/") })
        } catch (refused: SetupArchiveException) { throw refused }
        catch (_: Exception) { throw SetupArchiveException(BackupRefusal.Unreadable) }
    }

    /** Add old-to-current migrations here when the format grows. Never reinterpret a newer file. */
    private fun migrate(format: Int, root: JsonObject): JsonObject = when (format) {
        1 -> root
        else -> throw SetupArchiveException(BackupRefusal.Foreign)
    }

    internal fun settingsJson(settings: Map<String, Any>): JsonObject = buildJsonObject {
        settings.toSortedMap().forEach { (key, value) ->
            val type = PreferenceType.entries.firstOrNull { it.accepts(value) } ?: return@forEach
            put(key, buildJsonObject {
                put("type", type.wire)
                put("value", when (value) {
                    is String -> JsonPrimitive(value)
                    is Boolean -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value)
                    is Set<*> -> JsonArray(value.filterIsInstance<String>().sorted().map(::JsonPrimitive))
                    else -> error("unsupported preference")
                })
            })
        }
    }

    internal fun readSettings(raw: String): Map<String, Any> = Json.parseToJsonElement(raw).jsonObject.mapNotNull { (key, entry) ->
        val rule = BackupSettings.classify(key)?.takeIf { it.disposition == BackupDisposition.BackedUp } ?: return@mapNotNull null
        val row = entry.jsonObject
        val type = PreferenceType.entries.firstOrNull { it.wire == row["type"]?.jsonPrimitive?.content }
        if (type != rule.type) throw SetupArchiveException(BackupRefusal.Unreadable)
        key to readPreference(entry)
    }.toMap().let(BackupSettings::portable)

    internal fun readPreference(entry: JsonElement): Any {
        val row = entry.jsonObject
        val type = PreferenceType.entries.firstOrNull { it.wire == row["type"]?.jsonPrimitive?.content }
        val value = row.getValue("value")
        return when (type) {
            PreferenceType.StringValue -> value.jsonPrimitive.let { require(it.isString); it.content }
            PreferenceType.BooleanValue -> value.jsonPrimitive.boolean
            PreferenceType.IntValue -> value.jsonPrimitive.int
            PreferenceType.LongValue -> value.jsonPrimitive.long
            PreferenceType.FloatValue -> value.jsonPrimitive.float.also { require(it.isFinite()) }
            PreferenceType.StringSet -> value.jsonArray.map { it.jsonPrimitive.let { text -> require(text.isString); text.content } }.toSet()
            null -> throw SetupArchiveException(BackupRefusal.Unreadable)
        }
    }

    private fun manifestJson(manifest: BackupManifest): JsonObject = buildJsonObject {
        put("format", SETUP_FORMAT)
        put("createdMillis", manifest.createdMillis)
        put("versionName", manifest.versionName)
        put("versionCode", manifest.versionCode)
        put("deviceModel", manifest.device)
        put("summary", buildJsonObject {
            put("games", manifest.summary.games); put("collections", manifest.summary.collections)
            put("hidden", manifest.summary.hidden); put("theme", manifest.summary.theme)
        })
        put("folders", JsonArray(manifest.folders.map { buildJsonObject { put("uri", it.uri); put("name", it.name) } }))
        put("saveFolders", JsonArray(manifest.saveFolders.map(::JsonPrimitive)))
        put("computers", manifest.computers)
        put("retroArchCores", manifest.retroArchCores)
        put("edenFolder", manifest.edenFolder)
    }

    private fun readManifest(root: JsonObject): BackupManifest {
        val summary = root.getValue("summary").jsonObject
        fun count(name: String): Int = summary.getValue(name).jsonPrimitive.int.also { require(it >= 0) }
        return BackupManifest(
            root.getValue("createdMillis").jsonPrimitive.long.also { require(it >= 0) },
            root.getValue("versionName").jsonPrimitive.content, root.getValue("versionCode").jsonPrimitive.long,
            root.getValue("deviceModel").jsonPrimitive.content,
            BackupSummary(count("games"), count("collections"), count("hidden"), summary.getValue("theme").jsonPrimitive.content),
            root["folders"]?.jsonArray.orEmpty().map { it.jsonObject.let { row -> BackupFolder(row.getValue("uri").jsonPrimitive.content, row.getValue("name").jsonPrimitive.content) } },
            root["saveFolders"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
            root["computers"]?.jsonPrimitive?.boolean ?: false,
            root["retroArchCores"]?.jsonPrimitive?.boolean ?: false,
            root["edenFolder"]?.jsonPrimitive?.boolean ?: false,
        )
    }
}
