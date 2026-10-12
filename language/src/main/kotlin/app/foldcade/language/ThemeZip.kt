package app.foldcade.language

import androidx.compose.ui.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.*

/**
 * Paint read from a theme zip. A missing or rejected field keeps the built-in value.
 * A document that is not a theme object, or that has no name, is rejected.
 */
data class ThemeFile(
    val name: String,
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val muted: Color,
    val focus: Color,
    val iconRadius: Float,
    val artScale: Float,
    val backgroundMotion: BackgroundMotion,
    val id: String = name,
    val version: Int = 1,
    val icons: Map<String, ThemeIconFile> = emptyMap(),
    val sceneFiles: List<String> = emptyList(),
    val style: JsonObject = JsonObject(emptyMap()),
)

data class ThemeIconFile(val file: String, val accent: Color? = null, val tint: Boolean = false)

fun parseThemeJson(text: String): ThemeFile? = try { parseThemeJsonFields(text) } catch (_: Exception) { null }

private fun parseThemeJsonFields(text: String): ThemeFile? {
    val fields = parseFlatObject(text) ?: return null
    val name = fields["name"] as? String ?: return null
    if (name.isBlank()) return null
    val root = try { Json.parseToJsonElement(text).jsonObject } catch (_: Exception) { return null }
    val version = root["version"]?.jsonPrimitive?.intOrNull ?: 1
    if (version !in 1..2) return null
    val id = root["id"]?.jsonPrimitive?.contentOrNull ?: name
    if (version == 2 && (!id.matches(Regex("[A-Za-z0-9._-]+")) || id == "builtin")) return null
    val fallback = builtInTheme()
    val background = colorField(fields, "background", fallback.background)
    val surface = colorField(fields, "surface", fallback.surface)
    val onBackground = colorField(fields, "onBackground", fallback.onBackground)
    val muted = colorField(fields, "muted", fallback.muted)
    var focus = colorField(fields, "focus", fallback.focus)
    if (focus == background) focus = fallback.focus
    return ThemeFile(
        name = name,
        background = background,
        surface = surface,
        onBackground = onBackground,
        muted = muted,
        focus = focus,
        iconRadius = numberField(fields, "iconRadius", fallback.iconRadius, 0f, 0.5f),
        artScale = numberField(fields, "artScale", fallback.artScale, 0.7f, 1f),
        backgroundMotion = motionField(fields, "backgroundMotion"),
        id = id,
        version = version,
        icons = (root["icons"] as? JsonObject)?.mapNotNull { (slot, value) ->
            val icon = value as? JsonObject ?: return@mapNotNull null
            val path = (icon["file"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            if (themeZipEntryName(path) != path) return@mapNotNull null
            slot to ThemeIconFile(path, (icon["accent"] as? JsonPrimitive)?.contentOrNull?.let(::parseRgb),
                (icon["tint"] as? JsonPrimitive)?.booleanOrNull ?: false)
        }?.toMap().orEmpty(),
        sceneFiles = (root["scenes"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
        style = root["style"] as? JsonObject ?: JsonObject(emptyMap()),
    )
}

/** Bytes from a theme zip whose `theme.json` parsed. A bad zip is null. */
data class ThemeZip(
    val file: ThemeFile,
    val entries: Map<String, ByteArray>,
)

/** Where a wallpaper sits when letterboxed inside a panel. */
data class LetterboxRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

private const val MAX_THEME_ENTRIES = 4096
private const val MAX_THEME_ENTRY_BYTES = 8 * 1024 * 1024
private val APPLICATION_CODE_EXTENSIONS = setOf("java", "kt", "kts", "class", "dex", "jar", "so", "apk", "aar", "dll", "dylib", "wasm", "js", "mjs", "cjs", "py")

/**
 * Reads one theme zip. A stream that is not a zip, a zip with no named theme,
 * or a `theme.json` the host cannot parse returns null. An oversized entry is
 * skipped. A name that leaves the zip is skipped. Neither one throws.
 */
fun readThemeZip(input: InputStream): ThemeZip? {
    val entries = try {
        readCappedEntries(input)
    } catch (_: Exception) {
        return null
    }
    val json = entries["theme.json"]?.decodeToString() ?: return null
    val file = parseThemeJson(json) ?: return null
    return ThemeZip(file, entries)
}

/** `wallpaper-top.png` or `.webp`, and the same pair for the bottom panel. Png wins when both exist. */
fun wallpaperZipName(names: Set<String>, top: Boolean): String? {
    val stem = if (top) "wallpaper-top" else "wallpaper-bottom"
    return listOf("$stem.png", "$stem.webp").firstOrNull { it in names }
}

/** `font.ttf`, or `font.otf` when the zip has no ttf. */
fun fontZipName(names: Set<String>): String? =
    listOf("font.ttf", "font.otf").firstOrNull { it in names }

fun soundZipName(slot: String): String = "sounds/$slot.ogg"

/**
 * Fits [imageWidth]×[imageHeight] inside the panel, centered, without cropping.
 * A zero side returns an empty rect.
 */
fun letterbox(
    imageWidth: Float,
    imageHeight: Float,
    panelWidth: Float,
    panelHeight: Float,
): LetterboxRect {
    if (imageWidth <= 0f || imageHeight <= 0f || panelWidth <= 0f || panelHeight <= 0f) {
        return LetterboxRect(0f, 0f, 0f, 0f)
    }
    val scale = minOf(panelWidth / imageWidth, panelHeight / imageHeight)
    val width = imageWidth * scale
    val height = imageHeight * scale
    return LetterboxRect(
        left = (panelWidth - width) / 2f,
        top = (panelHeight - height) / 2f,
        width = width,
        height = height,
    )
}

/** Square cell for a panel width. The host does not stretch cells to fill the panel. */
fun panelCellPx(panelWidthPx: Float, columns: Int = Metrics.columns): Float {
    val inset = panelWidthPx * Metrics.insetFraction
    val gap = panelWidthPx * Metrics.gapFraction
    val inner = panelWidthPx - inset * 2f
    return (inner - gap * (columns - 1)) / columns
}

/** How far a focused cell's scaled stroke extends past the layout slot, per side. */
fun focusOutsetPx(cellPx: Float): Float =
    cellPx * ((Motion.scaleFocus - 1f) / 2f) + Metrics.focusStrokePx * Motion.scaleFocus

/**
 * True when one focused cell's stroke fits in the gap and does not cover the neighbor.
 * The bottom panel is 1240px wide. Four columns still clear that gap.
 */
fun focusClearsNeighborGap(panelWidthPx: Float, columns: Int = Metrics.columns): Boolean {
    val cell = panelCellPx(panelWidthPx, columns)
    if (cell <= 0f) return false
    return focusOutsetPx(cell) <= panelWidthPx * Metrics.gapFraction
}

private fun colorField(fields: Map<String, Any?>, key: String, fallback: Color): Color {
    val raw = fields[key] as? String ?: return fallback
    return parseRgb(raw) ?: fallback
}

private fun numberField(
    fields: Map<String, Any?>,
    key: String,
    fallback: Float,
    min: Float,
    max: Float,
): Float {
    val number = when (val raw = fields[key]) {
        is Double -> raw.toFloat()
        is String -> raw.toFloatOrNull() ?: return fallback
        else -> return fallback
    }
    return number.coerceIn(min, max)
}

private fun motionField(fields: Map<String, Any?>, key: String): BackgroundMotion {
    val raw = fields[key] as? String ?: return BackgroundMotion.Off
    return BackgroundMotion.fromWire(raw)
        ?: raw.takeIf { it.matches(Regex("[A-Za-z0-9._-]+")) }?.let(::BackgroundMotion)
        ?: BackgroundMotion.Off
}

private fun parseRgb(text: String): Color? {
    if (text.length != 7 || text[0] != '#') return null
    val raw = text.substring(1).toLongOrNull(16) ?: return null
    return Color(0xFF000000 or raw)
}

internal fun parseFlatObject(text: String): Map<String, Any?>? = try {
    Json.parseToJsonElement(text).jsonObject.mapValues { (_, element) ->
        if (element is JsonPrimitive) {
            when {
                element is JsonNull -> null
                element.isString -> element.content
                element.booleanOrNull != null -> element.boolean
                else -> element.doubleOrNull
            }
        } else element
    }
} catch (_: Exception) { null }

private fun readCappedEntries(input: InputStream): Map<String, ByteArray> {
    val entries = linkedMapOf<String, ByteArray>()
    ZipInputStream(input).use { zip ->
        var count = 0
        while (true) {
            val entry = zip.nextEntry ?: break
            count++
            require(count <= MAX_THEME_ENTRIES) { "Too many theme archive entries" }
            require(entry.name.substringAfterLast('.').lowercase() !in APPLICATION_CODE_EXTENSIONS) { "Themes cannot contain application code" }
            try {
                if (entry.isDirectory) continue
                val name = themeZipEntryName(entry.name) ?: continue
                val known = entry.size
                if (known > MAX_THEME_ENTRY_BYTES) continue
                val bytes = readAtMost(zip, MAX_THEME_ENTRY_BYTES) ?: continue
                entries[name] = bytes
            } finally {
                zip.closeEntry()
            }
        }
    }
    return entries
}

/** Zip path relative to the archive root. `..` and an absolute path are rejected. */
internal fun themeZipEntryName(raw: String): String? {
    val slash = raw.replace('\\', '/')
    var name = slash
    while (name.startsWith("/")) name = name.removePrefix("/")
    if (name.startsWith("./")) name = name.removePrefix("./")
    if (name.isEmpty() || name.contains('\u0000')) return null
    val parts = name.split('/')
    if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
    return name
}

private fun readAtMost(input: InputStream, max: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > max) return null
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
