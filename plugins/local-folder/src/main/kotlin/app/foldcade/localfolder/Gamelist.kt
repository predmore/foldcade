// SPDX-License-Identifier: MIT

package app.foldcade.localfolder

import app.foldcade.api.plugin.ArtworkRole
import app.foldcade.api.plugin.GameDetails
import app.foldcade.api.plugin.normalizeGameDetails
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/** A game's flat fields from the public EmulationStation gamelist format. Paths are still relative. */
data class GamelistGame(
    val path: String,
    val name: String?,
    val details: GameDetails?,
    val images: List<GamelistImage>,
)

/** One referenced image. Lower [rank] wins; a title screen is a screenshot fallback. */
data class GamelistImage(val role: ArtworkRole, val path: String, val rank: Int = 0)

/** Maximum UTF-8 bytes the SAF reader may read for one gamelist. */
const val MAX_GAMELIST_BYTES: Int = 16 * 1024 * 1024

/**
 * Tolerant reader for flat `<game>` records, built from the public format docs.
 * No XML engine, DTD or entity declarations are evaluated. Comments, attributes,
 * CDATA and the five XML and numeric entities are supported. An incomplete game
 * is discarded when the next starts. Unknown fields and folder records are ignored.
 * [isCancelled] is checked while reading, including inside long text fields.
 */
fun parseGamelist(text: String, isCancelled: () -> Boolean = { false }): List<GamelistGame> {
    if (text.length > MAX_GAMELIST_BYTES) return emptyList()
    val games = ArrayList<GamelistGame>()
    var fields: MutableMap<String, String>? = null
    val stack = ArrayList<String>()
    var field: String? = null
    val value = StringBuilder()
    fun finishField() {
        field?.let { fields?.put(it, value.toString().trim()) }
        field = null
        value.setLength(0)
    }
    fun append(raw: String, decode: Boolean) {
        if (field == null || value.length >= MAX_FIELD_CHARS) return
        value.append((if (decode) decodeGamelistEntities(raw) else raw).take(MAX_FIELD_CHARS - value.length))
    }
    var index = 0
    while (index < text.length) {
        if (isCancelled()) throw CancellationException("Media index was cancelled")
        if (text[index] != '<') {
            val end = text.indexOf('<', index).takeIf { it >= 0 } ?: text.length
            append(text.substring(index, minOf(end, index + MAX_FIELD_CHARS)), decode = true)
            index = end
            continue
        }
        if (text.startsWith("<!--", index)) {
            index = text.indexOf("-->", index + 4).takeIf { it >= 0 }?.plus(3) ?: text.length
            continue
        }
        if (text.startsWith("<![CDATA[", index)) {
            val end = text.indexOf("]]>", index + 9).takeIf { it >= 0 } ?: text.length
            append(text.substring(index + 9, minOf(end, index + 9 + MAX_FIELD_CHARS)), decode = false)
            index = (end + 3).coerceAtMost(text.length)
            continue
        }
        // Skip declarations as a whole, including a DOCTYPE's internal subset.
        if (text.startsWith("<!", index) || text.startsWith("<?", index)) {
            index = declarationEnd(text, index + 2)
            continue
        }
        val end = tagEnd(text, index + 1)
        if (end < 0) {
            append("<", decode = false)
            index++
            continue
        }
        val tag = text.substring(index + 1, end).trim()
        index = end + 1
        val closing = tag.startsWith('/')
        val name = tag.removePrefix("/").takeWhile { it.isLetterOrDigit() || it == '_' }.lowercase(Locale.ROOT)
        if (name.isEmpty()) continue
        val selfClosing = tag.endsWith('/')
        if (name == "game" && !closing) {
            fields = LinkedHashMap()
            stack.clear()
            stack += name
            field = null
            value.setLength(0)
            if (selfClosing) fields = null
        } else if (fields != null) {
            if (closing) {
                val level = stack.indexOfLast { it == name }
                if (level < 0) continue
                if (name == field || name == "game") finishField()
                while (stack.size > level) stack.removeAt(stack.lastIndex)
                if (name == "game") {
                    fields?.toGame()?.let(games::add)
                    fields = null
                }
            } else {
                if (stack.size == 1 && name in GAMELIST_FIELDS) {
                    finishField()
                    field = name
                }
                if (!selfClosing) stack += name else if (field == name) finishField()
            }
        }
    }
    return games
}

private fun Map<String, String>.toGame(): GamelistGame? {
    val path = get("path")?.takeIf { it.isNotBlank() } ?: return null
    fun first(vararg keys: String): String? = keys.firstNotNullOfOrNull { get(it)?.takeIf(String::isNotBlank) }
    val images = ArrayList<GamelistImage>()
    fun image(key: String, role: ArtworkRole, rank: Int = 0) {
        first(key)?.let { images += GamelistImage(role, it, rank) }
    }
    image("boxart", ArtworkRole.Cover)
    image("image", ArtworkRole.Cover, 1)
    image("thumbnail", ArtworkRole.Cover, 2)
    image("screenshot", ArtworkRole.Screenshot)
    image("titleshot", ArtworkRole.Screenshot, 20)
    image("marquee", ArtworkRole.Logo)
    image("fanart", ArtworkRole.Background)
    return GamelistGame(path, first("name"), normalizeGameDetails(GameDetails(
        summary = get("desc"),
        releaseYear = get("releasedate")?.take(4)?.toIntOrNull(),
        genres = get("genre")?.split(Regex("\\s*,\\s*|\\s+/\\s+")).orEmpty(),
        developer = get("developer"), publisher = get("publisher"), players = get("players"),
    )), images)
}

private const val MAX_FIELD_CHARS = 16 * 1024
private val GAMELIST_FIELDS = setOf("path", "name", "desc", "releasedate", "developer", "publisher", "genre",
    "players", "image", "thumbnail", "marquee", "fanart", "titleshot", "screenshot", "boxart")

private fun tagEnd(text: String, start: Int): Int {
    var quote: Char? = null
    for (index in start until text.length) {
        val char = text[index]
        if (quote != null) { if (char == quote) quote = null }
        else if (char == '\'' || char == '"') quote = char
        else if (char == '>') return index
        else if (char == '<') return -1
    }
    return -1
}

private fun declarationEnd(text: String, start: Int): Int {
    var quote: Char? = null
    var brackets = 0
    for (index in start until text.length) {
        val char = text[index]
        if (quote != null) { if (char == quote) quote = null }
        else when (char) {
            '\'', '"' -> quote = char
            '[' -> brackets++
            ']' -> brackets--
            '>' -> if (brackets <= 0) return index + 1
        }
    }
    return text.length
}

private val XML_ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);")

/** Unknown and stray ampersands stay literal; declarations can never introduce new entities. */
internal fun decodeGamelistEntities(raw: String): String = XML_ENTITY.replace(raw) { match ->
    when (val entity = match.groupValues[1]) {
        "amp" -> "&"
        "lt" -> "<"
        "gt" -> ">"
        "quot" -> "\""
        "apos" -> "'"
        else -> {
            val code = if (entity.startsWith("#x")) entity.drop(2).toIntOrNull(16) else entity.drop(1).toIntOrNull()
            if (code != null && code in 1..0x10ffff && code !in 0xd800..0xdfff) String(Character.toChars(code))
            else match.value
        }
    }
}
