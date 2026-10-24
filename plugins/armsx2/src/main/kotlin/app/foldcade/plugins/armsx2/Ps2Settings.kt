package app.foldcade.plugins.armsx2

/**
 * ARMSX2's settings mirror, `armsx2-settings.json`, written at the data folder's
 * top on every settings save. `global` is the shared settings object.
 * `games` maps a serial, such as `SLUS-21065`, to the fields that game overrides.
 * A present `memoryCardSlot1Filename` or `memoryCardSlot1Enabled` replaces the
 * global one. An empty filename keeps the global name. Missing keys keep the
 * defaults, slot 1 on and `mcd001.ps2`.
 */
internal fun slotFromMirror(text: String, serial: String?): Slot1? {
    val root = parseJson(text) as? Json.Obj ?: return null
    val global = root.fields["global"] as? Json.Obj ?: return null
    val base = slotFields(global) ?: return null
    if (serial == null) return base
    val games = root.fields["games"] ?: return base
    val table = games as? Json.Obj ?: return null
    val game = table.fields[serial] ?: return base
    val overrides = game as? Json.Obj ?: return null
    return applyCardOverride(base, overrides)
}

private fun slotFields(obj: Json.Obj): Slot1? {
    val enabled = when (val value = obj.fields["memoryCardSlot1Enabled"]) {
        null -> true
        is Json.Bool -> value.value
        else -> return null
    }
    val filename = when (val value = obj.fields["memoryCardSlot1Filename"]) {
        null -> Ps2Card.DEFAULT_FILE
        is Json.Str -> value.value.ifEmpty { Ps2Card.DEFAULT_FILE }
        else -> return null
    }
    return Slot1(enabled, filename)
}

/** Fields this game sets replace [base]. A filename that is present and empty keeps [base]. */
private fun applyCardOverride(base: Slot1, game: Json.Obj): Slot1? {
    var enabled = base.enabled
    var filename = base.filename
    if ("memoryCardSlot1Enabled" in game.fields) {
        enabled = (game.fields.getValue("memoryCardSlot1Enabled") as? Json.Bool)?.value ?: return null
    }
    if ("memoryCardSlot1Filename" in game.fields) {
        val name = (game.fields.getValue("memoryCardSlot1Filename") as? Json.Str)?.value ?: return null
        if (name.isNotEmpty()) filename = name
    }
    return Slot1(enabled, filename)
}

private sealed interface Json {
    data class Obj(val fields: Map<String, Json>) : Json
    data class Str(val value: String) : Json
    data class Bool(val value: Boolean) : Json

    /** A number, null, or array. Card fields are never these. */
    data object Skip : Json
}

/** The JSON value [text] holds, or null when it is not one JSON value. */
private fun parseJson(text: String): Json? = try {
    val parser = JsonParser(text)
    val value = parser.value()
    parser.skip()
    if (parser.done) value else null
} catch (_: ParseFail) {
    null
}

private class ParseFail : Exception()

private class JsonParser(private val text: String) {
    private var at = 0
    private var depth = 0
    val done: Boolean get() = at == text.length

    fun value(): Json {
        skip()
        if (depth > DEPTH) fail()
        return when (peek()) {
            '{' -> obj()
            '[' -> {
                skipArray()
                Json.Skip
            }
            '"' -> Json.Str(string())
            't' -> literal("true").let { Json.Bool(true) }
            'f' -> literal("false").let { Json.Bool(false) }
            'n' -> literal("null").let { Json.Skip }
            else -> number().let { Json.Skip }
        }
    }

    fun skip() {
        while (at < text.length && text[at].isWhitespace()) at++
    }

    private fun obj(): Json.Obj {
        expect('{')
        depth++
        val fields = linkedMapOf<String, Json>()
        skip()
        if (peek() == '}') {
            at++
            depth--
            return Json.Obj(fields)
        }
        while (true) {
            skip()
            val key = string()
            skip()
            expect(':')
            fields[key] = value()
            skip()
            when (peek()) {
                ',' -> at++
                '}' -> {
                    at++
                    depth--
                    return Json.Obj(fields)
                }
                else -> fail()
            }
        }
    }

    /** An array is skipped. Its contents are not card fields. */
    private fun skipArray() {
        expect('[')
        depth++
        skip()
        if (peek() == ']') {
            at++
            depth--
            return
        }
        while (true) {
            value()
            skip()
            when (peek()) {
                ',' -> at++
                ']' -> {
                    at++
                    depth--
                    return
                }
                else -> fail()
            }
        }
    }

    private fun string(): String {
        expect('"')
        val out = StringBuilder()
        while (at < text.length) {
            val char = text[at++]
            when {
                char == '"' -> return out.toString()
                char == '\\' -> out.append(escaped())
                char.code < 0x20 -> fail()
                else -> out.append(char)
            }
        }
        fail()
    }

    private fun escaped(): Char {
        if (at >= text.length) fail()
        return when (val char = text[at++]) {
            '"', '\\', '/' -> char
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                if (at + 4 > text.length) fail()
                val hex = text.substring(at, at + 4)
                at += 4
                val code = hex.toIntOrNull(16) ?: fail()
                code.toChar()
            }
            else -> fail()
        }
    }

    private fun number() {
        if (peek() == '-') at++
        if (at >= text.length) fail()
        if (text[at] == '0') {
            at++
        } else if (text[at] in '1'..'9') {
            while (at < text.length && text[at] in '0'..'9') at++
        } else {
            fail()
        }
        if (at < text.length && text[at] == '.') {
            at++
            var digits = 0
            while (at < text.length && text[at] in '0'..'9') {
                at++
                digits++
            }
            if (digits == 0) fail()
        }
        if (at < text.length && (text[at] == 'e' || text[at] == 'E')) {
            at++
            if (at < text.length && (text[at] == '+' || text[at] == '-')) at++
            var digits = 0
            while (at < text.length && text[at] in '0'..'9') {
                at++
                digits++
            }
            if (digits == 0) fail()
        }
    }

    private fun literal(word: String) {
        if (!text.startsWith(word, at)) fail()
        at += word.length
    }

    private fun expect(char: Char) {
        if (peek() != char) fail()
        at++
    }

    private fun peek(): Char = if (at < text.length) text[at] else fail()
}

private fun fail(): Nothing = throw ParseFail()

private const val DEPTH = 64
