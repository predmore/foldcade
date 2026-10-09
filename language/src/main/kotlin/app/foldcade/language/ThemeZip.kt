package app.foldcade.language

import androidx.compose.ui.graphics.Color

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
)

fun parseThemeJson(text: String): ThemeFile? {
    val fields = parseFlatObject(text) ?: return null
    val name = fields["name"] as? String ?: return null
    if (name.isBlank()) return null
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

private fun parseRgb(text: String): Color? {
    if (text.length != 7 || text[0] != '#') return null
    val raw = text.substring(1).toLongOrNull(16) ?: return null
    return Color(0xFF000000 or raw)
}

internal fun parseFlatObject(text: String): Map<String, Any?>? {
    val reader = JsonReader(text)
    return reader.objectValue()
}

private class JsonReader(private val text: String) {
    private var index = 0

    fun objectValue(): Map<String, Any?>? {
        skip()
        if (!eat('{')) return null
        val fields = linkedMapOf<String, Any?>()
        skip()
        if (eat('}')) return fields
        while (index < text.length) {
            skip()
            val key = string() ?: return null
            skip()
            if (!eat(':')) return null
            skip()
            val value = value() ?: return null
            fields[key] = value
            skip()
            when {
                eat(',') -> continue
                eat('}') -> return fields
                else -> return null
            }
        }
        return null
    }

    private fun value(): Any? {
        skip()
        if (index >= text.length) return null
        return when (text[index]) {
            '"' -> string()
            '-', in '0'..'9' -> number()
            else -> literal()
        }
    }

    private fun string(): String? {
        if (!eat('"')) return null
        val out = StringBuilder()
        while (index < text.length) {
            val char = text[index++]
            when (char) {
                '"' -> return out.toString()
                '\\' -> {
                    if (index >= text.length) return null
                    when (val escaped = text[index++]) {
                        '"', '\\', '/' -> out.append(escaped)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (index + 4 > text.length) return null
                            val hex = text.substring(index, index + 4).toIntOrNull(16) ?: return null
                            index += 4
                            out.append(hex.toChar())
                        }
                        else -> return null
                    }
                }
                else -> out.append(char)
            }
        }
        return null
    }

    private fun number(): Double? {
        val start = index
        if (eat('-')) Unit
        if (index >= text.length || !text[index].isDigit()) return null
        while (index < text.length && text[index].isDigit()) index++
        if (eat('.')) {
            if (index >= text.length || !text[index].isDigit()) return null
            while (index < text.length && text[index].isDigit()) index++
        }
        return text.substring(start, index).toDoubleOrNull()
    }

    private fun literal(): Any? {
        return when {
            eatWord("true") -> true
            eatWord("false") -> false
            eatWord("null") -> null
            else -> null
        }
    }

    private fun eatWord(word: String): Boolean {
        if (!text.startsWith(word, index)) return false
        index += word.length
        return true
    }

    private fun eat(char: Char): Boolean {
        if (index >= text.length || text[index] != char) return false
        index++
        return true
    }

    private fun skip() {
        while (index < text.length && text[index].isWhitespace()) index++
    }
}
