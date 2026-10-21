package app.foldcade.language

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

enum class ArtKind { Tile, Hero, Logo }

data class ArtCandidate(val id: String, val url: String, val thumbnail: String)

data class ArtMatch(val id: Int, val name: String)

data class ArtChoices(
    val values: Map<String, Map<ArtKind, String>> = emptyMap(),
    val matches: Map<String, ArtMatch> = emptyMap(),
) {
    fun setMatch(id: String, match: ArtMatch): ArtChoices = copy(matches = matches + (id to match))
    fun get(id: String, kind: ArtKind): String? = values[id]?.get(kind)

    fun set(id: String, kind: ArtKind, url: String?): ArtChoices {
        val parts = values[id].orEmpty().toMutableMap()
        if (url == null) parts.remove(kind) else parts[kind] = url
        val next = values.toMutableMap()
        if (parts.isEmpty()) next.remove(id) else next[id] = parts
        return copy(values = next)
    }

    /**
     * Moves each copy's choices onto the one tile in [groups] it folds into: tile id to its
     * copies, first preferred. The tile keeps a choice of its own, else takes the first copy's,
     * kind by kind and for the match. The copies' choices go, so a choice cleared on the tile
     * stays cleared.
     */
    fun adopt(groups: Map<String, List<String>>): ArtChoices {
        var next = this
        for ((tile, copies) in groups) {
            val moved = copies.filter { it != tile && (it in values || it in matches) }
            if (moved.isEmpty()) continue
            val parts = values[tile].orEmpty().toMutableMap()
            for (copy in moved) values[copy].orEmpty().forEach { (kind, url) -> parts.putIfAbsent(kind, url) }
            val match = matches[tile] ?: moved.firstNotNullOfOrNull { matches[it] }
            next = next.copy(
                values = (next.values - moved.toSet()).let { if (parts.isEmpty()) it - tile else it + (tile to parts) },
                matches = (next.matches - moved.toSet()).let { if (match == null) it else it + (tile to match) },
            )
        }
        return next
    }

    fun encode(): String = buildJsonObject {
        (values.keys + matches.keys).forEach { id -> put(id, buildJsonObject {
            values[id].orEmpty().forEach { (kind, url) -> put(kind.name, url) }
            matches[id]?.let { match -> put("match", buildJsonObject {
                put("id", match.id)
                put("name", match.name)
            }) }
        }) }
    }.toString()

    companion object {
        fun decode(raw: String?): ArtChoices = try {
            val root = Json.parseToJsonElement(raw ?: "{}").jsonObject
            ArtChoices(root.mapValues { (_, value) ->
                (value as? JsonObject).orEmpty().mapNotNull { (name, value) ->
                    val kind = ArtKind.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
                    val url = (value as? JsonPrimitive)?.content ?: return@mapNotNull null
                    if (url.startsWith("https://")) kind to url else null
                }.toMap()
            }.filterValues { it.isNotEmpty() }, root.mapNotNull { (id, value) ->
                val match = (value as? JsonObject)?.get("match") as? JsonObject ?: return@mapNotNull null
                val game = (match["id"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 } ?: return@mapNotNull null
                val name = (match["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                id to ArtMatch(game, name)
            }.toMap())
        } catch (_: Exception) { ArtChoices() }
    }
}

data class ArtPickerState(
    val gameId: String,
    val kind: ArtKind = ArtKind.Tile,
    val matches: List<ArtMatch> = emptyList(),
    val match: ArtMatch? = null,
    val findingMatches: Boolean = true,
    val index: Int = 0,
    val candidates: List<ArtCandidate> = emptyList(),
    val preview: Boolean = true,
    val loading: Boolean = true,
    val error: Boolean = false,
) {
    val focused: ArtCandidate? get() = candidates.getOrNull(index)

    fun withoutQuery(): ArtPickerState = copy(findingMatches = false, loading = false, error = true,
        candidates = emptyList(), index = 0, preview = false)

    fun navigate(meaning: Meaning): ArtPickerState {
        if (meaning == Meaning.LetterBackward || meaning == Meaning.LetterForward) {
            if (matches.isEmpty()) return this
            val current = matches.indexOfFirst { it.id == match?.id }.coerceAtLeast(0)
            val delta = if (meaning == Meaning.LetterForward) 1 else -1
            val next = matches[(current + delta + matches.size) % matches.size]
            if (next == match) return this
            return copy(match = next, index = 0, candidates = emptyList(), preview = true, loading = true, error = false)
        }
        val tab = when (meaning) {
            Meaning.LeftPanel -> (kind.ordinal + 2) % 3
            Meaning.RightPanel -> (kind.ordinal + 1) % 3
            else -> kind.ordinal
        }
        if (tab != kind.ordinal) {
            val willLoad = findingMatches || match != null
            return copy(preview = true, kind = ArtKind.entries[tab], index = 0, candidates = emptyList(),
                loading = willLoad, error = if (willLoad) false else error)
        }
        val target = when (meaning) {
            Meaning.MoveUp -> if (index % 3 > 0) index - 1 else index
            Meaning.MoveDown -> if (index % 3 < 2) index + 1 else index
            Meaning.MoveLeft -> if (index >= 3) index - 3 else index
            Meaning.MoveRight -> index + 3
            else -> return this
        }
        return copy(preview = true, index = target.coerceIn(0, (candidates.size - 1).coerceAtLeast(0)))
    }
}

object ArtCopy {
    const val art = "Art"
    fun matched(name: String): String = "SteamGridDB · $name"
    val tabs = listOf("Tile", "Hero", "Logo")
    const val empty = "No art found"
    const val error = "Couldn’t load art"
    const val kind = "Kind"
    const val match = "Match"
    const val apply = "Apply"
    const val automatic = "Automatic"
    const val current = "✓"
}

fun artPickerHintGroups(faces: FaceMap): List<List<HintKey>> = listOf(
    listOf(HintKey(PromptKey.L1, ""), HintKey(PromptKey.R1, ArtCopy.kind)),
    listOf(HintKey(PromptKey.L2, ""), HintKey(PromptKey.R2, ArtCopy.match)),
    listOf(HintKey(promptKeyOf(faces.confirmKey) ?: PromptKey.FaceA, ArtCopy.apply)),
    listOf(HintKey(actionFaces(faces).bottomGlyph, ArtCopy.automatic)),
    listOf(HintKey(promptKeyOf(faces.backKey) ?: PromptKey.FaceB, Copy.back)),
)

fun artPickerHints(faces: FaceMap): List<HintKey> = artPickerHintGroups(faces).flatten()
