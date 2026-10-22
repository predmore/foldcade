package app.foldcade.gamestream

/**
 * A GameStream reply: one `<root status_code="…">` holding plain elements,
 * some of them in repeated groups such as `<App>`. That is all a host sends,
 * so this reads only that shape, with no DTD and no entity beyond the five
 * XML predefines.
 */
internal class FlatXml(
    val status: Int,
    private val body: String,
) {
    /** The text of the first `<name>` anywhere under the root. */
    fun first(name: String): String? = elements(body, name).firstOrNull()

    /** The inner XML of each `<name>` group, read the same way. */
    fun groups(name: String): List<FlatXml> = elements(body, name, raw = true).map { FlatXml(status, it) }
}

/** Null when [xml] has no `<root>` with a numeric `status_code`. */
internal fun readFlatXml(xml: String): FlatXml? {
    if (xml.contains("<!DOCTYPE", ignoreCase = true) || xml.contains("<!ENTITY", ignoreCase = true)) return null
    val open = ROOT_OPEN.find(xml) ?: return null
    val status = STATUS.find(open.value)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    val close = xml.lastIndexOf("</root>")
    val body = if (close > open.range.last) xml.substring(open.range.last + 1, close) else ""
    return FlatXml(status, body)
}

private fun elements(body: String, name: String, raw: Boolean = false): List<String> {
    val pattern = Regex("<${Regex.escape(name)}(?:\\s[^>]*)?>(.*?)</${Regex.escape(name)}>", RegexOption.DOT_MATCHES_ALL)
    return pattern.findAll(body).map { match ->
        val inner = match.groupValues[1]
        if (raw) inner else unescape(inner)
    }.toList()
}

private fun unescape(text: String): String = text
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&amp;", "&")

private val ROOT_OPEN = Regex("<root(?:\\s[^>]*)?>")
private val STATUS = Regex("status_code\\s*=\\s*\"(-?\\d+)\"")
