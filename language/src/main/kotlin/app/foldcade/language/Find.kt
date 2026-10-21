package app.foldcade.language

import java.text.Normalizer
import java.util.Locale
import java.util.PriorityQueue
import kotlin.math.abs

/**
 * Find: every game, app and group on Home, by name, a few presses away. Select opens it
 * over any grid. This is its plain logic: what it lists, how a query ranks them, the
 * filters L2 and R2 step through, and where Show on Home takes a game.
 */

/** What a result is. A group is a folder, a collection or Recently played, on the root. */
enum class FindKind {
    Game,
    App,
    Group,
}

/** One thing Find can list. [platformId] is canonical. [hidden] is hidden from Home. */
data class FindEntry(
    val id: String,
    val title: String,
    val kind: FindKind,
    val platformId: String? = null,
    val platformName: String? = null,
    val hidden: Boolean = false,
    val lastPlayedMillis: Long = 0L,
    val mark: String? = null,
)

/** How many results Find shows at once. A strip longer than this is more than anyone scrolls. */
const val FIND_LIMIT: Int = 24

enum class FindFilterKind {
    All,
    Games,
    Apps,
    Collections,
    Hidden,
    Recent,
    Platform,
}

/** One filter chip. [platformId] is set for a platform's own chip. */
data class FindFilter(val kind: FindFilterKind, val label: String, val platformId: String? = null) {
    fun admits(entry: FindEntry): Boolean = when (kind) {
        FindFilterKind.All -> true
        FindFilterKind.Games -> entry.kind == FindKind.Game
        FindFilterKind.Apps -> entry.kind == FindKind.App
        FindFilterKind.Collections -> entry.kind == FindKind.Group
        FindFilterKind.Hidden -> entry.hidden
        FindFilterKind.Recent -> entry.kind != FindKind.Group && entry.lastPlayedMillis > 0L
        FindFilterKind.Platform -> entry.kind == FindKind.Game && entry.platformId == platformId
    }
}

/**
 * The chips L2 and R2 step through: All, then each kind that has something in it, then
 * every platform present, A to Z. A chip that would list nothing is left out, so every
 * press shows something.
 */
fun findFilters(entries: List<FindEntry>): List<FindFilter> {
    val kinds = listOf(
        FindFilter(FindFilterKind.Games, FindCopy.games),
        FindFilter(FindFilterKind.Apps, FindCopy.apps),
        FindFilter(FindFilterKind.Collections, FindCopy.collections),
        FindFilter(FindFilterKind.Hidden, FindCopy.hidden),
        FindFilter(FindFilterKind.Recent, FindCopy.recent),
    ).filter { filter -> entries.any(filter::admits) }
    val platforms = entries.filter { it.kind == FindKind.Game && it.platformId != null }
        .distinctBy { it.platformId }
        .map { FindFilter(FindFilterKind.Platform, it.platformName?.takeIf(String::isNotBlank) ?: it.platformId!!, it.platformId) }
        .sortedBy { it.label.lowercase(Locale.ROOT) }
    return listOf(FindFilter(FindFilterKind.All, FindCopy.all)) + kinds + platforms
}

/**
 * How well a title answers a query, best first. [Start]: the title begins with it.
 * [Words]: each word of the query begins a word of the title, as "ze" does Zelda.
 * [Initials]: the query runs along the words' first letters, as "oot" does Ocarina of
 * Time. [Contains]: it is somewhere inside. [Typo]: one letter off, swapped, missing or extra.
 */
enum class MatchTier {
    Start,
    Words,
    Initials,
    Contains,
    Typo,
}

/** A title's match: its [tier], and [at], how far into the title it was found. Lower is better for both. */
data class FindMatch(val tier: MatchTier, val at: Int)

/**
 * [text] as Find compares it: lower case, accents off, ligatures spelled out, apostrophes
 * dropped so "Assassin's" reads as one word, and anything else that is not a letter or a
 * digit a single space between words.
 */
fun foldForFind(text: String): String {
    val spelled = buildString(text.length) {
        for (char in text) {
            when (char) {
                'ß' -> append("ss")
                'æ', 'Æ' -> append("ae")
                'œ', 'Œ' -> append("oe")
                'ø', 'Ø' -> append('o')
                'ł', 'Ł' -> append('l')
                'đ', 'Đ' -> append('d')
                '\'', '’', '‘', 'ʼ' -> Unit
                else -> append(char)
            }
        }
    }
    val bare = Normalizer.normalize(spelled, Normalizer.Form.NFD).replace(MARKS, "")
    return bare.lowercase(Locale.ROOT)
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .trim()
        .replace(SPACES, " ")
}

private val MARKS = Regex("\\p{M}+")
private val SPACES = Regex(" +")

/** The match of [title] for [query], or null when it does not answer it at all. A blank query matches nothing. */
fun matchTitle(title: String, query: String): FindMatch? = FoldedTitle(foldForFind(title)).match(FoldedQuery(foldForFind(query)))

/**
 * A title made ready to match once, when Find's list is built, rather than on every key:
 * folded, split into at most [TITLE_WORDS] words, run together, and its words' initials.
 */
internal class FoldedTitle(val text: String) {
    val words: List<String> = if (text.isEmpty()) emptyList() else text.split(' ').take(TITLE_WORDS)
    val compact: String = text.replace(" ", "")
    val initials: String = words.joinToString("") { word -> if (word.all(Char::isDigit)) word else word.take(1) }

    /** The best tier this title reaches for [query], or null. */
    fun match(query: FoldedQuery): FindMatch? {
        val q = query.text
        if (q.isEmpty() || text.isEmpty()) return null
        if (text.startsWith(q) || compact.startsWith(query.compact)) return FindMatch(MatchTier.Start, 0)
        wordsMatch(query.words, words)?.let { return FindMatch(MatchTier.Words, it) }
        if (query.compact.length >= 2 && query.words.size == 1) {
            val at = initials.indexOf(query.compact)
            if (at >= 0) return FindMatch(MatchTier.Initials, at)
        }
        if (query.compact.length >= 2) {
            val at = compact.indexOf(query.compact)
            if (at >= 0) return FindMatch(MatchTier.Contains, at)
        }
        if (query.compact.length >= TYPO_FLOOR) {
            typoMatch(query.words, words)?.let { return FindMatch(MatchTier.Typo, it) }
            if (prefixWithin(query.compact, compact)) return FindMatch(MatchTier.Typo, 0)
        }
        return null
    }
}

/** A query made ready once per key: folded, split into words, and run together. The keyboard keeps it short. */
internal class FoldedQuery(val text: String) {
    val words: List<String> = if (text.isEmpty()) emptyList() else text.split(' ')
    val compact: String = text.replace(" ", "")
}

/** A query this short is too easy to be one letter off from almost anything. */
private const val TYPO_FLOOR = 3

/** A title's words past this many are left out of word matching, which keeps it quick however long a name is. */
private const val TITLE_WORDS = 24

/**
 * Where the first query word landed when every query word begins a different title word, in
 * any order, at the earliest title word it can take. Null when they cannot all be placed. Each
 * placing is a bipartite matching, so it grows with the words' counts, never with their orders.
 */
private fun wordsMatch(asked: List<String>, words: List<String>): Int? {
    if (asked.isEmpty() || asked.size > words.size) return null
    val edges = Array(asked.size) { i -> words.indices.filter { words[it].startsWith(asked[i]) }.toIntArray() }
    return earliestFirst(edges, words.size)
}

/**
 * [wordsMatch] with one letter wrong in one word, of [TYPO_FLOOR] letters or more. Only tried
 * once the words cannot all be placed exactly, so at most one is missing; each one-letter-off
 * pairing for it is tried in turn.
 */
private fun typoMatch(asked: List<String>, words: List<String>): Int? {
    if (asked.isEmpty() || asked.size > words.size) return null
    val exact = Array(asked.size) { i -> words.indices.filter { words[it].startsWith(asked[i]) }.toIntArray() }
    // One typo places at most one more word, so a matching short by two or more cannot be saved.
    if (matchedCount(exact, words.size) < asked.size - 1) return null
    for (i in asked.indices) {
        if (asked[i].length < TYPO_FLOOR) continue
        for (k in words.indices) {
            if (k in exact[i] || !prefixWithin(asked[i], words[k])) continue
            val edges = exact.copyOf()
            edges[i] = intArrayOf(k)
            earliestFirst(edges, words.size)?.let { return it }
        }
    }
    return null
}

/** The earliest title word the first query word can take with every other word still placed, or null. */
private fun earliestFirst(edges: Array<IntArray>, titleWords: Int): Int? {
    for (first in edges[0]) {
        val owner = IntArray(titleWords) { -1 }
        owner[first] = 0
        if ((1 until edges.size).all { place(it, edges, owner, BooleanArray(titleWords), pinned = 0) }) return first
    }
    return null
}

/** How many query words can take different title words at once. */
private fun matchedCount(edges: Array<IntArray>, titleWords: Int): Int {
    val owner = IntArray(titleWords) { -1 }
    return edges.indices.count { place(it, edges, owner, BooleanArray(titleWords), pinned = -1) }
}

/**
 * Finds query word [word] a title word of its own, moving others along to theirs if it must
 * (an augmenting path). Query word [pinned] keeps the title word it was given.
 */
private fun place(word: Int, edges: Array<IntArray>, owner: IntArray, seen: BooleanArray, pinned: Int): Boolean {
    for (title in edges[word]) {
        if (seen[title]) continue
        seen[title] = true
        val held = owner[title]
        // A free title word (-1) is never the pinned one, even when nothing is pinned (-1 too).
        if (held >= 0 && held == pinned) continue
        if (held < 0 || place(held, edges, owner, seen, pinned)) {
            owner[title] = word
            return true
        }
    }
    return false
}

/** True when some start of [text], as long as [query] give or take one letter, is [oneOff] from it. */
private fun prefixWithin(query: String, text: String): Boolean {
    for (length in (query.length - 1)..(query.length + 1)) {
        if (length <= 0 || length > text.length) continue
        if (oneOff(query, text, length)) return true
    }
    return false
}

/**
 * True when [a] and the first [length] letters of [b] are the same, or one letter apart: one
 * changed, added or dropped, or two side by side swapped. It walks both once and copies
 * nothing, since a search asks this of most titles on every key.
 */
internal fun oneOff(a: String, b: String, length: Int = b.length): Boolean {
    if (abs(a.length - length) > 1) return false
    var at = 0
    while (at < a.length && at < length && a[at] == b[at]) at++
    if (at == a.length || at == length) return true
    return when {
        a.length == length ->
            b.regionMatches(at + 1, a, at + 1, length - at - 1) ||
                (at + 1 < length && a[at] == b[at + 1] && a[at + 1] == b[at] && b.regionMatches(at + 2, a, at + 2, length - at - 2))
        a.length > length -> b.regionMatches(at, a, at + 1, length - at)
        else -> b.regionMatches(at + 1, a, at, a.length - at)
    }
}

/**
 * Find's list, made ready to search: each entry's title folded and split once, when the list
 * is built, so a key press only matches. The shell builds one when Home changes.
 */
class FindIndex(entries: List<FindEntry>) {
    internal val rows: List<Pair<FindEntry, FoldedTitle>> =
        entries.distinctBy { it.id }.map { it to FoldedTitle(foldForFind(it.title)) }
}

/** [findResults] over a list built for this search alone. */
fun findResults(entries: List<FindEntry>, query: String, filter: FindFilter, limit: Int = FIND_LIMIT): List<FindEntry> =
    findResults(FindIndex(entries), query, filter, limit)

/**
 * What Find shows for [query] under [filter], best first, at most [limit]. A match ranks
 * by [MatchTier], then by how early in the title it was found; a tie goes to what is on
 * Home over what is hidden, then to the game played last, then A to Z.
 *
 * With no query it is a list to browse: what was played, newest first, then everything
 * else A to Z, hidden things last.
 */
fun findResults(index: FindIndex, query: String, filter: FindFilter, limit: Int = FIND_LIMIT): List<FindEntry> {
    val shown = index.rows.filter { filter.admits(it.first) }
    val asked = FoldedQuery(foldForFind(query))
    if (asked.text.isEmpty()) return browse(shown, limit)
    return shown.mapNotNull { (entry, title) -> title.match(asked)?.let { Triple(entry, title, it) } }
        .best(
            limit,
            compareBy<Triple<FindEntry, FoldedTitle, FindMatch>> { it.third.tier }
                .thenBy { it.third.at }
                .thenBy { it.first.hidden }
                .thenByDescending { it.first.lastPlayedMillis }
                .thenBy { it.second.text }
                .thenBy { it.first.id },
        )
        .map { it.first }
}

/** Played first, newest first; then the rest A to Z, with what is hidden after what is on Home. */
private fun browse(rows: List<Pair<FindEntry, FoldedTitle>>, limit: Int): List<FindEntry> {
    val (played, rest) = rows.partition { it.first.kind != FindKind.Group && it.first.lastPlayedMillis > 0L }
    val first = played.best(limit, compareByDescending<Pair<FindEntry, FoldedTitle>> { it.first.lastPlayedMillis }.thenBy { it.first.id })
    val then = rest.best(limit - first.size, compareBy<Pair<FindEntry, FoldedTitle>> { it.first.hidden }.thenBy { it.second.text }.thenBy { it.first.id })
    return (first + then).map { it.first }
}

/**
 * The first [limit] of this list in [order]. Only those few are kept in order as it goes, so a
 * query that matches most of a big library does not sort all of it on every key.
 */
private fun <T> List<T>.best(limit: Int, order: Comparator<T>): List<T> {
    if (limit <= 0) return emptyList()
    if (size <= limit) return sortedWith(order)
    val kept = PriorityQueue(limit + 1, order.reversed())
    for (item in this) {
        kept.add(item)
        if (kept.size > limit) kept.poll()
    }
    return kept.sortedWith(order)
}

/**
 * Where Show on Home lands. [Root] is a tile on the root: a pin, or a group itself.
 * [Inside] is a tile in the group at [rootIndex], at [index] once that group is open.
 */
sealed interface HomeLanding {
    data class Root(val index: Int) : HomeLanding

    data class Inside(val groupId: String, val rootIndex: Int, val index: Int) : HomeLanding
}

/**
 * The most visible place [id] has on Home: a group's own tile, or a game's pin on the root,
 * else its folder, else the first collection on the root that holds it, else Recently
 * played. Null when it is nowhere on Home, hidden or taken off, so Pin to Home is offered
 * instead. A group taken off Home has no place either.
 */
fun homeLanding(board: HomeBoard, id: String): HomeLanding? {
    if (id == HOME_ALL) return board.slots.indexOf(HOME_ALL).takeIf { it >= 0 }?.let { HomeLanding.Root(it) }
    val root = board.slots.indexOf(id)
    if (board.folders[id] != null) return root.takeIf { it >= 0 }?.let { HomeLanding.Root(it) }
    if (id in board.hidden) return null
    if (root >= 0) return HomeLanding.Root(root)
    val groups = board.slots.withIndex().mapNotNull { (index, slot) -> slot?.let { board.folders[it] }?.let { index to it } }
        .distinctBy { it.second.id }
    val holding = groups.filter { (_, group) -> id in group.slots }
    val (at, group) = holding.firstOrNull { it.second.system }
        ?: holding.firstOrNull { it.second.collection }
        ?: holding.firstOrNull { it.second.recent }
        ?: return null
    return HomeLanding.Inside(group.id, at, groupSlots(board, group.id).indexOf(id))
}

/**
 * The line under a result's name on the top screen: a game's platform, a group's count, then
 * where it lives on Home (pinned, or in which folder or collection), or that it is not on
 * Home, and whether it is hidden. A game in its platform's folder is named by that place alone.
 */
fun findFacts(board: HomeBoard, entry: FindEntry): List<String> {
    val group = board.folders[entry.id]?.takeIf { entry.kind == FindKind.Group }
    val spot = homeLanding(board, entry.id)
    val place = when (spot) {
        is HomeLanding.Root -> FindCopy.pinned.takeIf { group == null }
        is HomeLanding.Inside -> board.folders[spot.groupId]?.name?.let(FindCopy::inPlace)
        null -> FindCopy.notOnHome.takeIf { !entry.hidden }
    }
    // In its own platform's folder, "In Sega Genesis" already says the platform.
    val inOwnFolder = spot is HomeLanding.Inside && board.folders[spot.groupId]?.platformId == entry.platformId
    return listOfNotNull(
        entry.platformName?.takeIf { entry.kind == FindKind.Game && it.isNotBlank() && !inOwnFolder },
        group?.let { folderCountLine(folderCount(board, it.id), apps = it.holdsApps) },
        place,
        FindCopy.hidden.takeIf { entry.hidden },
    )
}

/**
 * Find's keys while its keyboard has them: A types the key under the ring, and B clears what
 * was typed, then closes Find once nothing is. The results' keys show only once they have the D-pad.
 */
fun findKeyboardHints(faceMap: FaceMap, typed: Boolean): List<HintKey> {
    val confirm = promptKeyOf(faceMap.confirmKey) ?: PromptKey.FaceA
    val back = promptKeyOf(faceMap.backKey) ?: PromptKey.FaceB
    return listOf(
        HintKey(confirm, FindCopy.type),
        HintKey(typingFaces(faceMap).eraseGlyph, KeyboardCopy.erase),
        HintKey(typingFaces(faceMap).spaceGlyph, KeyboardCopy.space),
        HintKey(PromptKey.L2, ""),
        HintKey(PromptKey.R2, FindCopy.filter),
        HintKey(PromptKey.Start, FindCopy.results),
        HintKey(back, if (typed) FindCopy.clear else FindCopy.close),
    )
}

/** Find's keys on its results: open on either screen, the game's menu, the filters, and back to the keys. */
fun findResultHints(faceMap: FaceMap, screens: Boolean, options: Boolean): List<HintKey> {
    val confirm = promptKeyOf(faceMap.confirmKey) ?: PromptKey.FaceA
    val back = promptKeyOf(faceMap.backKey) ?: PromptKey.FaceB
    return buildList {
        if (screens) {
            add(HintKey(confirm, Copy.hintTop))
            add(HintKey(PromptKey.FaceX, Copy.hintBottom))
        } else {
            add(HintKey(confirm, FindCopy.open))
        }
        if (options) add(HintKey(PromptKey.FaceY, Copy.hintOptions))
        add(HintKey(PromptKey.L2, ""))
        add(HintKey(PromptKey.R2, FindCopy.filter))
        add(HintKey(back, Copy.back))
    }
}

/**
 * The root grid's hints with Select's Find added, so the button can be found, while the row
 * still has room for it on one line. Anywhere else they stay as they are.
 */
fun withFindHint(keys: List<HintKey>, atRoot: Boolean): List<HintKey> =
    if (atRoot && keys.size <= FIND_HINT_ROOM) keys + HintKey(PromptKey.Select, FindCopy.find) else keys

/** How many hints may already be in the row for Select's Find to join it. */
private const val FIND_HINT_ROOM = 3

/** Find's words. */
object FindCopy {
    const val find = "Find"
    const val all = "All"
    const val games = "Games"
    const val apps = "Apps"
    const val collections = "Collections"
    const val hidden = "Hidden"
    const val recent = "Recent"
    const val filter = "Filter"
    const val type = "Type"
    const val results = "Results"
    const val clear = "Clear"
    const val close = "Close"
    const val open = "Open"
    const val noMatches = "No matches"
    const val showOnHome = "Show on Home"
    const val pinned = "Pinned"
    const val notOnHome = "Not on Home"

    /** Where a result lives on Home, for the line under its name: "In Nintendo 3DS". */
    fun inPlace(name: String): String = "In $name"
}
