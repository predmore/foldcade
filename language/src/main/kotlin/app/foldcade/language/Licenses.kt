package app.foldcade.language

/** Left-panel copy for the Foldcade and MuseScore license notices. */
object LicenseCopy {
    const val row = "Licenses"
    const val title = "Licenses"
    const val scroll = "Scroll"
}

/** What the companion names beside Licenses: the D-pad pages through the notices and B goes back. A does nothing there. */
fun licensesHints(faces: FaceMap): List<HintKey> =
    listOf(HintKey(PromptKey.DpadUpDown, LicenseCopy.scroll), HintKey(promptKeyOf(faces.backKey) ?: PromptKey.FaceB, Copy.back))

/**
 * One block of a notice as the page shows it: a heading, or a paragraph the page wraps itself.
 * [level] is a heading's depth, its count of `#`; an underlined heading is 1. Release notes use
 * 2 for each version and 3 for the sections inside one, so a version reads apart from its parts.
 */
data class NoticeBlock(val text: String, val heading: Boolean = false, val level: Int = if (heading) 1 else 0)

/**
 * A plain-text or Markdown notice as blocks the page can wrap to its own width. The files are
 * hard-wrapped near 70 columns, which left a ragged column with a quarter of the card empty, so
 * a line that ran long joins the next. Short lines, as a list of names, stay one per line.
 * `# Heading` and a heading underlined with `---` become headings, and `**` marks drop.
 */
fun noticeBlocks(text: String): List<NoticeBlock> {
    val blocks = mutableListOf<NoticeBlock>()
    val lines = mutableListOf<String>()
    fun flush() {
        if (lines.isNotEmpty()) blocks += NoticeBlock(lines.joinToString("\n"))
        lines.clear()
    }
    for (raw in text.replace("\r\n", "\n").lines()) {
        val line = raw.trimEnd().replace("**", "")
        when {
            line.isBlank() -> flush()
            RULE.matches(line) -> {
                // An underline: what came just before it is a heading.
                val title = lines.removeLastOrNull()
                flush()
                if (title != null) blocks += NoticeBlock(title.trim(), heading = true)
            }
            line.startsWith("#") -> {
                flush()
                blocks += NoticeBlock(line.trimStart('#').trim(), heading = true, level = line.takeWhile { it == '#' }.length)
            }
            lines.isNotEmpty() && lines.last().length >= WRAPPED_AT && !LIST_ITEM.containsMatchIn(line) ->
                lines[lines.lastIndex] = lines.last() + " " + line.trim()
            else -> lines += line.trim()
        }
    }
    flush()
    return blocks
}

/** A line at least this long was wrapped by its author, so the next line continues it. */
private const val WRAPPED_AT = 56
private val RULE = Regex("^\\s*([-=*_])\\1{2,}\\s*$")
private val LIST_ITEM = Regex("^\\s*([-*•]|\\d+[.)])\\s")
