package app.foldcade.language

import java.text.Normalizer
import java.util.Locale

/**
 * One game that several libraries have, such as a ROM in a folder that a RomM server also
 * holds. Players name a game's saves after its file, so the same platform and the same file
 * name is the same game with the same save, and Home shows it as one tile.
 *
 * The tile's id comes from that key alone. Each source's own id is remembered as an alias of
 * it, and an alias is kept when a source goes away, so the tile keeps its places and its
 * history while a server is offline or removed.
 */
const val SAME_PREFIX: String = "same:"

/** A platform, folded to lowercase, and a file name in Unicode's composed form. */
data class SameKey(val platformId: String, val fileName: String)

/** The key two copies of one game share: the platform, and the file name spelled the same. Null without either. */
fun sameKey(platformId: String?, fileName: String?): SameKey? {
    val platform = platformId?.trim()?.lowercase(Locale.ROOT)?.ifEmpty { null } ?: return null
    val name = fileName?.takeIf { it.isNotBlank() } ?: return null
    // The name as written: a player looks for a save of exactly that name, so case and spaces count.
    return SameKey(platform, Normalizer.normalize(name, Normalizer.Form.NFC))
}

fun sameGameId(key: SameKey): String = "$SAME_PREFIX${key.platformId}:${key.fileName}"

/** The file name in a [sameGameId], as it was folded. Null for any other id. */
fun sameGameFileName(id: String): String? =
    id.removePrefix(SAME_PREFIX).takeIf { it != id }?.substringAfter(':', "")?.ifEmpty { null }

/**
 * What the fold knows of one source's game. [rank] orders copies: the lowest goes first, and
 * the first [named] one titles the tile. [named] is false for a title that is only the file name.
 */
data class SameSource(val key: SameKey?, val rank: Int, val named: Boolean)

/**
 * [items] with each game several libraries have folded into one tile. [members] lists each
 * tile's source ids, in rank order, for the ones listed now. [aliases] maps every source id
 * that is or was part of a tile to it.
 */
data class SameFold(
    val items: List<HomeItem>,
    val members: Map<String, List<String>>,
    val aliases: Map<String, String>,
)

/**
 * Folds games with the same key into one tile. Two or more copies make the tile and alias
 * each copy to it. A copy listed alone keeps the tile while its alias still names its key, so
 * the tile outlives a source that is offline. A copy listed with another key, such as a
 * renamed file, is its own game again and loses its alias. A copy with no key now keeps it.
 *
 * The tile takes the first named title by rank, the latest play, and the first copy's mark.
 * An item [sources] does not list passes through.
 */
fun foldSameGames(
    items: List<HomeItem>,
    sources: Map<String, SameSource>,
    aliases: Map<String, String>,
): SameFold {
    val keyed = items.filter { it.kind == HomeKind.Rom && sources[it.id]?.key != null }
        .groupBy { sources.getValue(it.id).key!! }
    val tileOf = HashMap<String, String>()
    val nextAliases = aliases.toMutableMap()
    for ((key, copies) in keyed) {
        val tile = sameGameId(key)
        if (copies.size > 1) {
            copies.forEach { tileOf[it.id] = tile; nextAliases[it.id] = tile }
        } else {
            val only = copies.single().id
            if (aliases[only] == tile) tileOf[only] = tile
        }
    }
    for (item in items) {
        if (item.id in tileOf) continue
        val alias = aliases[item.id] ?: continue
        if (item.kind == HomeKind.Rom && sources[item.id]?.key == null) {
            tileOf[item.id] = alias
        } else if (sources[item.id]?.key != null) {
            nextAliases.remove(item.id)
        }
    }
    val byTile = items.filter { it.id in tileOf }.groupBy { tileOf.getValue(it.id) }
    val members = byTile.mapValues { (_, copies) ->
        copies.sortedWith(compareBy({ sources[it.id]?.rank ?: Int.MAX_VALUE }, { it.id })).map { it.id }
    }
    val folded = ArrayList<HomeItem>()
    val emitted = HashSet<String>()
    for (item in items) {
        val tile = tileOf[item.id]
        if (tile == null) {
            folded += item
            continue
        }
        if (!emitted.add(tile)) continue
        val copies = members.getValue(tile).map { id -> byTile.getValue(tile).first { it.id == id } }
        val first = copies.first()
        val titled = copies.firstOrNull { sources[it.id]?.named == true } ?: first
        folded += first.copy(
            id = tile,
            title = titled.title,
            lastPlayedMillis = copies.maxOf { it.lastPlayedMillis },
            playMillis = copies.maxOf { it.playMillis },
        )
    }
    return SameFold(folded, members, nextAliases)
}

/**
 * Puts each copy's places, hiding, and removal from Home on the tile it folds into, so a
 * merge that no longer lists the copies loses nothing. A second place in one list becomes a
 * gap, as any repeated id does. The tile is hidden only when a copy was and no copy is placed:
 * a copy hidden as the duplicate does not hide the game. Running it again changes nothing.
 */
fun adoptSameGames(board: HomeBoard, aliases: Map<String, String>): HomeBoard {
    if (aliases.isEmpty()) return board
    val tile = { id: String -> aliases[id] ?: id }
    fun adopt(slots: List<String?>): List<String?> = firstOnly(slots.map { it?.let(tile) })
    val slots = adopt(board.slots)
    val folders = board.folders.mapValues { (_, folder) -> folder.copy(slots = adopt(folder.slots)) }
    val placed = HashSet<String>()
    slots.filterNotNullTo(placed)
    folders.values.filter { !it.recent }.forEach { it.slots.filterNotNullTo(placed) }
    val tiles = aliases.values.toSet()
    val shown = { id: String -> id !in tiles || id !in placed }
    val hidden = board.hidden.map(tile).filter(shown).toSet()
    // Each hidden copy remembers its own places. Show brings the tile back to all of them, once per group.
    val hiddenSlots = LinkedHashMap<String, List<HiddenSlot>>()
    for ((id, places) in board.hiddenSlots.entries.sortedBy { (id, _) -> if (id in tiles) 0 else 1 }) {
        val to = tile(id)
        if (to != id && to !in hidden) continue
        hiddenSlots[to] = (hiddenSlots[to].orEmpty() + places).distinctBy { it.group }
    }
    return board.copy(
        slots = slots,
        folders = folders,
        hidden = hidden,
        hiddenSlots = hiddenSlots.filterKeys { it !in tiles || it in hidden },
        offHome = board.offHome.map(tile).filter(shown).toSet(),
        filedEmulators = board.filedEmulators.map(tile).toSet(),
    )
}

/** Every source id behind [tileId], the tile included. */
fun sameGameIds(tileId: String, aliases: Map<String, String>): Set<String> =
    setOf(tileId) + aliases.filterValues { it == tileId }.keys

fun encodeSameGames(aliases: Map<String, String>): String =
    aliases.toSortedMap().entries.joinToString(SAME_RECORD) { (from, to) -> "$from$SAME_FIELD$to" }

fun decodeSameGames(raw: String?): Map<String, String> {
    if (raw.isNullOrEmpty()) return emptyMap()
    return raw.split(SAME_RECORD).mapNotNull { record ->
        val fields = record.split(SAME_FIELD)
        if (fields.size != 2 || fields.any { it.isEmpty() } || !fields[1].startsWith(SAME_PREFIX)) null
        else fields[0] to fields[1]
    }.toMap()
}

private const val SAME_FIELD = "\u001f"
private const val SAME_RECORD = "\u001e"
