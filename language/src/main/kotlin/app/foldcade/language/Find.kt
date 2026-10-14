package app.foldcade.language

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

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
fun matchTitle(title: String, query: String): FindMatch? = matchFolded(foldForFind(title), foldForFind(query))

/** [matchTitle] for a title and query already through [foldForFind]. */
private fun matchFolded(t: String, q: String): FindMatch? {
    if (q.isEmpty() || t.isEmpty()) return null
    val words = t.split(' ')
    val asked = q.split(' ')
    val compact = t.replace(" ", "")
    val compactQuery = q.replace(" ", "")
    if (t.startsWith(q) || compact.startsWith(compactQuery)) return FindMatch(MatchTier.Start, 0)
    wordsMatch(asked, words, typos = 0)?.let { return FindMatch(MatchTier.Words, it) }
    if (compactQuery.length >= 2 && ' ' !in q) {
        val initials = words.joinToString("") { word -> if (word.all(Char::isDigit)) word else word.take(1) }
        val at = initials.indexOf(compactQuery)
        if (at >= 0) return FindMatch(MatchTier.Initials, at)
    }
    if (compactQuery.length >= 2) {
        val at = compact.indexOf(compactQuery)
        if (at >= 0) return FindMatch(MatchTier.Contains, at)
    }
    if (compactQuery.length >= TYPO_FLOOR) {
        wordsMatch(asked, words, typos = 1)?.let { return FindMatch(MatchTier.Typo, it) }
        if (prefixWithin(compactQuery, compact, 1)) return FindMatch(MatchTier.Typo, 0)
    }
    return null
}

/** A query this short is too easy to be one letter off from almost anything. */
private const val TYPO_FLOOR = 3

/**
 * Where the first query word landed when every query word begins a different title word,
 * in any order, with at most [typos] letters wrong between them all. Null when they do not.
 * A word under [TYPO_FLOOR] letters must match exactly.
 */
private fun wordsMatch(asked: List<String>, words: List<String>, typos: Int): Int? {
    var best: Int? = null
    fun place(at: Int, used: Set<Int>, spent: Int, first: Int?) {
        if (best == 0) return
        if (at == asked.size) {
            val landed = first ?: 0
            if (best == null || landed < best!!) best = landed
            return
        }
        val word = asked[at]
        for (index in words.indices) {
            if (index in used) continue
            val cost = when {
                words[index].startsWith(word) -> 0
                spent < typos && word.length >= TYPO_FLOOR && prefixWithin(word, words[index], 1) -> 1
                else -> continue
            }
            place(at + 1, used + index, spent + cost, first ?: index)
        }
    }
    place(0, emptySet(), 0, null)
    return best
}

/** True when some start of [text], as long as [query] give or take one, is within [limit] edits of it. */
private fun prefixWithin(query: String, text: String, limit: Int): Boolean {
    for (length in (query.length - limit)..(query.length + limit)) {
        if (length <= 0 || length > text.length) continue
        if (editDistance(query, text.substring(0, length), limit) <= limit) return true
    }
    return false
}

/**
 * Letters changed, added, dropped, or two side by side swapped, to turn [a] into [b].
 * Counting stops past [limit]; anything further is reported as one more than it.
 */
internal fun editDistance(a: String, b: String, limit: Int): Int {
    if (abs(a.length - b.length) > limit) return limit + 1
    val rows = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) rows[i][0] = i
    for (j in 0..b.length) rows[0][j] = j
    for (i in 1..a.length) {
        var lowest = Int.MAX_VALUE
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var value = min(min(rows[i - 1][j] + 1, rows[i][j - 1] + 1), rows[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) value = min(value, rows[i - 2][j - 2] + 1)
            rows[i][j] = value
            lowest = min(lowest, value)
        }
        if (lowest > limit) return limit + 1
    }
    return rows[a.length][b.length]
}

/**
 * What Find shows for [query] under [filter], best first, at most [limit]. A match ranks
 * by [MatchTier], then by how early in the title it was found; a tie goes to what is on
 * Home over what is hidden, then to the game played last, then A to Z.
 *
 * With no query it is a list to browse: what was played, newest first, then everything
 * else A to Z, hidden things last.
 */
fun findResults(entries: List<FindEntry>, query: String, filter: FindFilter, limit: Int = FIND_LIMIT): List<FindEntry> {
    val shown = entries.filter(filter::admits).distinctBy { it.id }
    // Each title is folded once, not once per comparison.
    val folded = shown.associate { it.id to foldForFind(it.title) }
    val q = foldForFind(query)
    if (q.isEmpty()) return browse(shown, folded).take(limit)
    return shown.mapNotNull { entry -> matchFolded(folded.getValue(entry.id), q)?.let { entry to it } }
        .sortedWith(
            compareBy<Pair<FindEntry, FindMatch>> { it.second.tier }
                .thenBy { it.second.at }
                .thenBy { it.first.hidden }
                .thenByDescending { it.first.lastPlayedMillis }
                .thenBy { folded.getValue(it.first.id) }
                .thenBy { it.first.id },
        )
        .take(limit)
        .map { it.first }
}

/** Played first, newest first; then the rest A to Z, with what is hidden after what is on Home. */
private fun browse(entries: List<FindEntry>, folded: Map<String, String>): List<FindEntry> {
    val (played, rest) = entries.partition { it.kind != FindKind.Group && it.lastPlayedMillis > 0L }
    return played.sortedWith(compareByDescending<FindEntry> { it.lastPlayedMillis }.thenBy { it.id }) +
        rest.sortedWith(compareBy<FindEntry> { it.hidden }.thenBy { folded.getValue(it.id) }.thenBy { it.id })
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
    return HomeLanding.Inside(group.id, at, group.slots.indexOf(id))
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
    // In its own platform's folder, "In Genesis" already says the platform.
    val inOwnFolder = spot is HomeLanding.Inside && board.folders[spot.groupId]?.platformId == entry.platformId
    return listOfNotNull(
        entry.platformName?.takeIf { entry.kind == FindKind.Game && it.isNotBlank() && !inOwnFolder },
        group?.let { folderCountLine(folderCount(board, it.id), apps = it.bucket == HomeKind.AndroidApp) },
        place,
        FindCopy.hidden.takeIf { entry.hidden },
    )
}

/**
 * Find's keys while its keyboard has them. A types the key under the ring, which needs no
 * word. B clears what was typed, and closes Find once nothing is.
 */
fun findKeyboardHints(faceMap: FaceMap, typed: Boolean): List<HintKey> {
    val back = promptKeyOf(faceMap.backKey) ?: PromptKey.FaceB
    return listOf(
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
