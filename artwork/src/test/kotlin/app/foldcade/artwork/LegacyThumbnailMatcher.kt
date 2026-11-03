package app.foldcade.artwork

// Frozen pre-index matcher: ordering oracle and opt-in timing baseline.
internal fun legacyRankedThumbnails(names: List<String>, fileName: String?, title: String): List<String> {
    val stem = fileName?.let(::fileStem)
    val exact = stem?.let(::libretroSafe)?.takeIf { it in names }
    val key = matchKey(stem ?: title).ifEmpty { matchKey(title) }
    if (key.isEmpty()) return listOfNotNull(exact)
    val candidates = names.filter { matchKey(it) == key }
        .ifEmpty { if (stem != null) names.filter { matchKey(it) == matchKey(title) } else emptyList() }
    val wanted = regionsIn(stem.orEmpty()) + DEFAULT_REGIONS
    val ranked = candidates.sortedWith(
        compareBy<String>(
            { name -> regionsIn(name).minOfOrNull { region -> wanted.indexOf(region).takeIf { it >= 0 } ?: wanted.size } ?: wanted.size },
            { name -> if (UNOFFICIAL.containsMatchIn(name)) 1 else 0 },
            { name -> name.length },
        ),
    )
    return listOfNotNull(exact) + (ranked - setOfNotNull(exact))
}

/** The title alone, for comparing: tags cut, articles dropped, letters and digits only. */
private fun matchKey(name: String): String {
    val cut = name.indexOfAny(charArrayOf('(', '[')).let { if (it > 0) name.substring(0, it) else name }
    return cut.lowercase()
        .replace(Regex(""",\s*(the|a|an)\b"""), " ")
        .replace(Regex("""^(the|a|an)\s+"""), "")
        .replace(Regex("[^a-z0-9]+"), "")
}

/** The server writes these characters as `_` in an image name. */
private fun libretroSafe(name: String): String = name.replace(Regex("""[&*/:`<>?\\|"]"""), "_")

private fun fileStem(fileName: String): String {
    val dot = fileName.lastIndexOf('.')
    return if (dot > 0) fileName.substring(0, dot) else fileName
}

private val DEFAULT_REGIONS = listOf("USA", "World", "Europe", "Japan")

private val REGION_CODES = mapOf(
    "U" to "USA",
    "E" to "Europe",
    "J" to "Japan",
    "W" to "World",
    "UE" to "USA",
    "JU" to "USA",
)

private val UNOFFICIAL = Regex("""\((Rev [^)]*|Beta[^)]*|Demo[^)]*|Proto[^)]*|Sample|Kiosk[^)]*)\)""", RegexOption.IGNORE_CASE)

/** The regions a name's tags name: "(USA, Europe)" is both, "(U)" is USA. */
private fun regionsIn(name: String): List<String> =
    Regex("""\(([^)]*)\)""").findAll(name).flatMap { group ->
        group.groupValues[1].split(',').map { it.trim() }
    }.mapNotNull { tag ->
        REGION_CODES[tag] ?: tag.takeIf { it in setOf("USA", "World", "Europe", "Japan", "Korea", "Asia", "Australia") }
    }.toList()

