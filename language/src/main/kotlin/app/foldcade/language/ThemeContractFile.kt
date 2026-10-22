package app.foldcade.language

import kotlinx.serialization.json.*

/**
 * `themes/contract.json`: everything a pack can name, written from the engine and from Afterglow.
 * One entry per line, so a change to the contract reads as a change in review. Afterglow's value
 * sits beside each token: changing one changes every pack that inherits it.
 */
fun themeContractJson(afterglow: ThemeResources): String {
    val style = afterglow.style
    val tokens = style.specs.mapValues { (token, spec) -> tokenEntry(spec, style.values[token]) }
    val sections = linkedMapOf<String, String>(
        "about" to quoted("Generated from Foldcade's theme engine and Afterglow. See docs/themes.md. Never edit by hand."),
        "host" to "$THEME_HOST_LEVEL",
        "manifest" to block(ThemeContract.manifestFields.mapValues { JsonPrimitive(it.value) }),
        "fonts" to block(ThemeContract.fontFaces.mapValues { JsonPrimitive(it.value) }),
        "tokens" to block(tokens),
        "renamedTokens" to block(ThemeContract.renamedTokens.mapValues { JsonPrimitive(it.value) }),
        "retiredTokens" to list(ThemeContract.retiredTokens.sorted()),
        "slots" to list(contractSlots(afterglow)),
        "closedSlotFamilies" to list(ThemeContract.closedSlotFamilies.sorted()),
        "openSlotFamilies" to list(ThemeContract.openSlotFamilies.sorted()),
        "renamedSlots" to block(ThemeContract.renamedSlots.mapValues { JsonPrimitive(it.value) }),
        "retiredSlots" to list(ThemeContract.retiredSlots.sorted()),
        "sceneInputs" to block(ThemeContract.sceneInputs.mapValues { JsonPrimitive(it.value) }),
        "sounds" to block(ThemeContract.soundCues.mapValues { it.value?.let(::JsonPrimitive) ?: JsonNull }),
    )
    return "{\n" + sections.entries.joinToString(",\n") { (key, value) -> "  ${quoted(key)}: $value" } + "\n}\n"
}

/**
 * The slots Foldcade promises to keep reading: every one Afterglow fills, but an installed app's.
 * Apps are named by their package, which Foldcade does not choose.
 */
fun contractSlots(afterglow: ThemeResources): List<String> = afterglow.icons.keys.filter { !it.startsWith("app.") }.sorted()

/**
 * Names in an earlier [contract] that the engine and Afterglow no longer have, and that no
 * rename or retirement accounts for. A pack in the wild may still use each one. A rename or a
 * retirement is a promise too: an old name stays working, or stays known to do nothing, for good.
 * Scene inputs, sound cues, font faces and manifest fields have no renames, so none may leave.
 */
fun vanishedFromContract(contract: JsonObject, afterglow: ThemeResources): List<String> {
    fun names(section: String): List<String> = when (val value = contract[section]) {
        is JsonObject -> value.keys.toList()
        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        else -> emptyList()
    }
    val liveTokens = afterglow.style.specs.keys
    val liveSlots = contractSlots(afterglow).toSet()
    val tokenKnown = { name: String -> name in liveTokens || name in ThemeContract.renamedTokens || name in ThemeContract.retiredTokens }
    val slotKnown = { name: String -> name in liveSlots || name in ThemeContract.renamedSlots || name in ThemeContract.retiredSlots }
    return (names("tokens") + names("renamedTokens") + names("retiredTokens")).distinct().filterNot(tokenKnown).map { "token $it" } +
        (names("slots") + names("renamedSlots") + names("retiredSlots")).distinct().filterNot(slotKnown).map { "slot $it" } +
        names("sceneInputs").filter { it !in ThemeContract.sceneInputs }.map { "scene input $it" } +
        names("sounds").filter { it !in ThemeContract.soundCues }.map { "sound $it" } +
        names("fonts").filter { it !in ThemeContract.fontFaces }.map { "font $it" } +
        names("manifest").filter { it !in ThemeContract.manifestFields }.map { "manifest field $it" }
}

/** A JSON Schema for `theme.json`, for editors to complete and check a manifest as it is written. */
fun themeSchemaJson(afterglow: ThemeResources): String {
    val style = afterglow.style
    val color = buildJsonObject { put("type", "string"); put("pattern", "^#[0-9A-Fa-f]{6}$") }
    val groups = style.specs.entries.groupBy { it.key.substringBefore('.') }.mapValues { (_, entries) ->
        buildJsonObject {
            put("type", "object")
            put("additionalProperties", false)
            put("properties", buildJsonObject {
                entries.forEach { (token, spec) -> put(token.substringAfter('.'), tokenSchema(spec, style.values[token], color)) }
                ThemeContract.renamedTokens.filterValues { it.substringBefore('.') == entries.first().key.substringBefore('.') }.forEach { (old, now) ->
                    if (old.substringBefore('.') == now.substringBefore('.')) put(old.substringAfter('.'), buildJsonObject {
                        put("deprecated", true); put("description", "Renamed to $now")
                    })
                }
            })
        }
    }
    val closed = contractSlots(afterglow).filter { it.substringBefore('.') in ThemeContract.closedSlotFamilies }
    val schema = buildJsonObject {
        put("\$schema", "https://json-schema.org/draft/2020-12/schema")
        put("title", "Foldcade theme manifest")
        put("description", "Generated from Foldcade's theme engine. See docs/themes.md.")
        put("type", "object")
        put("required", buildJsonArray { add("name") })
        put("properties", buildJsonObject {
            put("version", buildJsonObject { put("enum", buildJsonArray { add(1); add(2) }) })
            put("id", buildJsonObject { put("type", "string"); put("pattern", "^[A-Za-z0-9._-]+$") })
            put("name", buildJsonObject { put("type", "string"); put("minLength", 1) })
            put("release", buildJsonObject { put("type", "string"); put("pattern", "^\\d{1,6}(\\.\\d{1,6}){0,2}$") })
            put("host", buildJsonObject { put("type", "integer"); put("minimum", 1); put("description", "The theme format level the pack needs. This Foldcade reads $THEME_HOST_LEVEL.") })
            put("source", buildJsonObject {
                put("type", "object"); put("additionalProperties", false); put("required", buildJsonArray { add("github") })
                put("properties", buildJsonObject { put("github", buildJsonObject { put("type", "string"); put("pattern", "^[A-Za-z0-9-]+/[A-Za-z0-9._-]+$") }) })
            })
            put("extends", buildJsonObject { put("type", "string"); put("pattern", "^[A-Za-z0-9._-]+$"); put("description", "Another pack's id, inherited before Afterglow") })
            listOf("author", "description", "license", "preview", "backgroundMotion", "backgroundMusic").forEach { put(it, buildJsonObject { put("type", "string") }) }
            listOf("background", "surface", "onBackground", "muted", "focus").forEach { put(it, color) }
            put("iconRadius", buildJsonObject { put("type", "number"); put("minimum", 0); put("maximum", 0.5) })
            put("artScale", buildJsonObject { put("type", "number"); put("minimum", 0.7); put("maximum", 1) })
            put("scenes", buildJsonObject { put("type", "array"); put("items", buildJsonObject { put("type", "string") }) })
            put("fonts", buildJsonObject {
                put("type", "object"); put("additionalProperties", false)
                put("properties", buildJsonObject { ThemeContract.fontFaces.forEach { (face, about) -> put(face, buildJsonObject { put("type", "string"); put("description", about) }) } })
            })
            put("icons", buildJsonObject {
                put("type", "object")
                put("propertyNames", buildJsonObject {
                    put("anyOf", buildJsonArray {
                        add(buildJsonObject { put("enum", buildJsonArray { (closed + ThemeContract.renamedSlots.keys).sorted().forEach { add(it) } }) })
                        add(buildJsonObject { put("pattern", "^(${ThemeContract.openSlotFamilies.sorted().joinToString("|")})\\.") })
                    })
                })
                put("additionalProperties", buildJsonObject {
                    put("type", "object"); put("required", buildJsonArray { add("file") }); put("additionalProperties", false)
                    put("properties", buildJsonObject {
                        put("file", buildJsonObject { put("type", "string") })
                        put("accent", color)
                        put("tint", buildJsonObject { put("type", "boolean") })
                    })
                })
            })
            put("style", buildJsonObject {
                put("type", "object"); put("additionalProperties", false)
                put("properties", JsonObject(groups))
            })
        })
    }
    return prettyJson.encodeToString(JsonElement.serializer(), schema) + "\n"
}

private val prettyJson = Json { prettyPrint = true }

private fun tokenEntry(spec: TokenSpec, value: Any?): JsonObject = buildJsonObject {
    put("kind", spec.kind.wire)
    spec.unit?.let { put("unit", it) }
    if (spec.role != TokenRole.None) put("role", spec.role.wire)
    if (spec.nullable) put("nullable", true)
    spec.domain?.let { domain ->
        if (domain.min.isFinite()) put(if (domain.minOpen) "above" else "min", domain.min)
        if (domain.max.isFinite()) put(if (domain.maxOpen) "below" else "max", domain.max)
        if (domain.whole) put("whole", true)
    }
    put("afterglow", jsonValue(value))
}

private fun tokenSchema(spec: TokenSpec, value: Any?, color: JsonObject): JsonObject = when (spec.kind) {
    TokenKind.Color -> if (spec.nullable) buildJsonObject {
        put("anyOf", buildJsonArray { add(color); add(buildJsonObject { put("type", "null") }) })
        put("default", jsonValue(value))
        put("description", "null derives it from the pack's own colours")
    } else JsonObject(color + ("default" to jsonValue(value)))
    TokenKind.Curve -> buildJsonObject {
        put("type", "array"); put("minItems", 4); put("maxItems", 4); put("items", buildJsonObject { put("type", "number") })
        put("default", jsonValue(value))
    }
    else -> buildJsonObject {
        put("type", if (spec.domain?.whole == true) "integer" else "number")
        spec.domain?.let { domain ->
            if (domain.min.isFinite()) put(if (domain.minOpen) "exclusiveMinimum" else "minimum", domain.min)
            if (domain.max.isFinite()) put(if (domain.maxOpen) "exclusiveMaximum" else "maximum", domain.max)
        }
        put("default", jsonValue(value))
        listOfNotNull(spec.unit, spec.role.takeIf { it != TokenRole.None }?.let { "follows base.${it.scale}" })
            .takeIf { it.isNotEmpty() }?.let { put("description", it.joinToString("; ")) }
    }
}

private fun jsonValue(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is androidx.compose.ui.graphics.Color -> JsonPrimitive(themeColorText(value))
    is Number -> JsonPrimitive(value)
    is List<*> -> JsonArray(value.map { JsonPrimitive(it as Number) })
    else -> JsonPrimitive(value.toString())
}

private fun quoted(text: String): String = JsonPrimitive(text).toString()

private fun block(entries: Map<String, JsonElement>): String =
    if (entries.isEmpty()) "{}"
    else "{\n" + entries.entries.joinToString(",\n") { (key, value) -> "    ${quoted(key)}: $value" } + "\n  }"

private fun list(items: List<String>): String =
    if (items.isEmpty()) "[]"
    else "[\n" + items.joinToString(",\n") { "    ${quoted(it)}" } + "\n  ]"
