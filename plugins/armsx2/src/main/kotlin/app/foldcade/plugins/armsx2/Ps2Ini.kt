package app.foldcade.plugins.armsx2

/** PCSX2's quoted or plain values, in file order, from one ini section. */
internal fun iniValues(ini: String, wantedSection: String): Sequence<Pair<String, String>> = sequence {
    var section = ""
    for (raw in ini.splitToSequence('\n')) {
        val line = raw.trim().trimEnd('\r')
        if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue
        if (line.startsWith("[") && line.endsWith("]")) {
            section = line.substring(1, line.length - 1).trim()
            continue
        }
        if (section != wantedSection) continue
        val split = line.indexOf('=')
        if (split <= 0) continue
        yield(line.substring(0, split).trim() to line.substring(split + 1).trim().removeSurrounding("\""))
    }
}

internal const val INI_LIMIT = 256 * 1024
