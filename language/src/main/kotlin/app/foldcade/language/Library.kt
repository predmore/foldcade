package app.foldcade.language

import java.util.Locale

/**
 * All games: every game the user has, on every system, in one place, whatever Home shows.
 * L1's Games and the All games tile on Home open it. Its tabs, which L2 and R2 step and a
 * tap picks, are a few ways to look at the whole library, then each system. This is its
 * plain logic: what it lists, its tabs, each tab's order, and where its Search starts.
 *
 * Apps are not games, so they stay in Apps. An Android game is a game, so it is here.
 */

enum class LibraryTabKind {
    /** Every game, A to Z. */
    All,

    /** Games played on this device, last played first. */
    Recent,

    /** Games played on this device, most time played first. */
    MostPlayed,

    /** Games Home does not show, hidden or with no place left there, A to Z: the ones to add back. */
    NotOnHome,

    /** One system's games, A to Z. */
    System,
}

/** One tab. [systemId] is set for a system's own tab: a canonical platform id, or a library folder's id. */
data class LibraryTab(val kind: LibraryTabKind, val label: String, val systemId: String? = null)

/**
 * One game as All games lists it. [systemId] and [systemName] are its platform's for a ROM,
 * else its library folder's on Home (Android Games, Moonlight...). [onHome] is [onHome]'s.
 */
data class LibraryGame(
    val item: HomeItem,
    val systemId: String?,
    val systemName: String?,
    val onHome: Boolean,
    val lastPlayedMillis: Long = 0L,
    val playMillis: Long = 0L,
) {
    val id: String get() = item.id

    /** The title as tabs sort it, worked out once. */
    internal val sortTitle: String = item.title.lowercase(Locale.ROOT)
}

private val gameKinds = setOf(HomeKind.Rom, HomeKind.AndroidGame, HomeKind.GameNative, HomeKind.Moonlight, HomeKind.DroidDeck)

/** True for what All games lists: a game of any kind, never an app or a setup tile. */
fun listedInLibrary(item: HomeItem): Boolean = !item.placeholder && item.kind in gameKinds

/**
 * The games of [items] All games lists, once each, with their systems, where they are on
 * [board], and their play. [lastPlayed] and [playTime] read the play log, which is slow per
 * game, so play time is only read for games played at all.
 */
fun libraryGames(
    items: List<HomeItem>,
    board: HomeBoard,
    platforms: List<HomePlatform>,
    lastPlayed: (String) -> Long? = { null },
    playTime: (String) -> Long? = { null },
): List<LibraryGame> {
    val shown = onHomeIds(board)
    val resolved = HashMap<String, HomePlatform?>()
    val seen = HashSet<String>()
    val games = ArrayList<LibraryGame>()
    for (item in items) {
        if (!listedInLibrary(item) || !seen.add(item.id)) continue
        val (systemId, systemName) = systemOf(item) { raw -> resolved.getOrPut(raw) { resolvePlatform(platforms, raw) } }
        val last = lastPlayed(item.id) ?: item.lastPlayedMillis
        games += LibraryGame(
            item = item,
            systemId = systemId,
            systemName = systemName,
            onHome = item.id in shown,
            lastPlayedMillis = last,
            playMillis = if (last > 0L || item.playMillis > 0L) playTime(item.id) ?: item.playMillis else 0L,
        )
    }
    return games
}

/** A ROM's platform, or its raw platform id when none knows it; any other game's library folder. */
private inline fun systemOf(item: HomeItem, resolve: (String) -> HomePlatform?): Pair<String?, String?> {
    if (item.kind == HomeKind.Rom) {
        val raw = item.platformId?.takeIf { it.isNotBlank() } ?: return null to null
        val platform = resolve(raw)
        return (platform?.id ?: raw) to (platform?.name?.takeIf { it.isNotBlank() } ?: raw)
    }
    val folder = libraryFolderOf(item.kind) ?: return null to null
    return folder.id to folder.name
}

/**
 * The tabs over [games]: All first, then each way to look at them that lists something and is
 * not All again, then each system A to Z by its tab's name while there are two or more. Every press shows
 * something new.
 */
fun libraryTabs(games: List<LibraryGame>): List<LibraryTab> {
    val looks = listOf(
        LibraryTab(LibraryTabKind.Recent, LibraryCopy.recent),
        LibraryTab(LibraryTabKind.MostPlayed, LibraryCopy.mostPlayed),
        LibraryTab(LibraryTabKind.NotOnHome, LibraryCopy.notOnHome),
    ).filter { tab ->
        val listed = games.count { tab.admits(it) }
        listed > 0 && (listed < games.size || tab.kind != LibraryTabKind.NotOnHome)
    }
    val systems = games.mapNotNull { game -> game.systemId?.let { LibraryTab(LibraryTabKind.System, systemTabLabel(it, game.systemName ?: it), it) } }
        .distinctBy { it.systemId }
        .sortedWith(compareBy<LibraryTab> { it.label.lowercase(Locale.ROOT) }.thenBy { it.systemId })
        .takeIf { it.size >= 2 }
        .orEmpty()
    return listOf(LibraryTab(LibraryTabKind.All, LibraryCopy.all)) + looks + systems
}

/**
 * A system's name on its tab: what players call it, short, so more tabs fit across the
 * bottom screen (3DS, GBA, Genesis). The top screen still names the game's system in full.
 * A system with no short name keeps its own.
 */
fun systemTabLabel(systemId: String, name: String): String = SHORT_SYSTEMS[systemId.lowercase(Locale.ROOT)] ?: name

private val SHORT_SYSTEMS: Map<String, String> = mapOf(
    "game-boy" to "GB", "gb" to "GB",
    "game-boy-color" to "GBC", "gbc" to "GBC",
    "game-boy-advance" to "GBA", "gba" to "GBA",
    "nintendo-64" to "N64", "n64" to "N64",
    "nintendo-switch" to "Switch", "switch" to "Switch",
    "nintendo-ds" to "DS", "nds" to "DS",
    "nintendo-3ds" to "3DS", "3ds" to "3DS", "n3ds" to "3DS",
    "playstation" to "PS1", "psx" to "PS1", "ps1" to "PS1",
    "playstation-2" to "PS2", "ps2" to "PS2",
    "playstation-3" to "PS3", "ps3" to "PS3",
    "genesis" to "Genesis", "megadrive" to "Genesis",
    "master-system" to "Master System", "sms" to "Master System",
    "game-gear" to "Game Gear", "gg" to "Game Gear",
    "saturn" to "Saturn",
    "dreamcast" to "Dreamcast",
    "sega-cd" to "Sega CD", "segacd" to "Sega CD",
    "sega-32x" to "32X", "sega32" to "32X",
    "neo-geo-pocket" to "NGP",
    "neo-geo-pocket-color" to "NGPC",
    HOME_ANDROID_GAMES to "Android",
)

/** True when [game] belongs on this tab. */
fun LibraryTab.admits(game: LibraryGame): Boolean = when (kind) {
    LibraryTabKind.All -> true
    LibraryTabKind.Recent -> game.lastPlayedMillis > 0L
    LibraryTabKind.MostPlayed -> game.playMillis > 0L
    LibraryTabKind.NotOnHome -> !game.onHome
    LibraryTabKind.System -> game.systemId == systemId
}

/** The same tab in [tabs], by what it shows rather than where it sits, or -1 when it is gone. */
fun List<LibraryTab>.indexOfTab(tab: LibraryTab?): Int =
    if (tab == null) -1 else indexOfFirst { it.kind == tab.kind && it.systemId == tab.systemId }

/**
 * What [tab] lists, in its order. A to Z goes by title, then id, so two games of one name
 * keep a place. Recent and Most played put the latest or longest first and break ties A to Z.
 */
fun libraryRows(games: List<LibraryGame>, tab: LibraryTab): List<LibraryGame> {
    val byTitle = compareBy<LibraryGame> { it.sortTitle }.thenBy { it.id }
    val order = when (tab.kind) {
        LibraryTabKind.Recent -> compareByDescending<LibraryGame> { it.lastPlayedMillis }.then(byTitle)
        LibraryTabKind.MostPlayed -> compareByDescending<LibraryGame> { it.playMillis }.then(byTitle)
        LibraryTabKind.All, LibraryTabKind.NotOnHome, LibraryTabKind.System -> byTitle
    }
    return games.filter { tab.admits(it) }.sortedWith(order)
}

/**
 * The Find filter that Search from [tab] starts on: a system's own chip when Find has one,
 * else Games, so a search from All games looks through games on every system. All when Find
 * has neither.
 */
fun libraryFindFilter(tab: LibraryTab?, filters: List<FindFilter>): Int {
    val system = tab?.takeIf { it.kind == LibraryTabKind.System }?.systemId
    val platform = system?.let { id -> filters.indexOfFirst { it.kind == FindFilterKind.Platform && it.platformId == id } } ?: -1
    if (platform >= 0) return platform
    return filters.indexOfFirst { it.kind == FindFilterKind.Games }.coerceAtLeast(0)
}

/** The tab [by] steps from [current] in [tabs], round the ends, as Find's filters go. */
fun steppedTab(tabs: List<LibraryTab>, current: Int, by: Int): Int =
    if (tabs.isEmpty()) 0 else Math.floorMod(current.coerceIn(0, tabs.lastIndex) + by, tabs.size)

/** All games' words. */
object LibraryCopy {
    /** The tile on Home and the row in L1. */
    const val title = "All games"

    /** L1's tile, in one word. */
    const val tile = "Games"
    const val all = "All"
    const val recent = Copy.sortRecent
    const val mostPlayed = Copy.sortMostPlayed
    const val notOnHome = "Not on Home"
}
