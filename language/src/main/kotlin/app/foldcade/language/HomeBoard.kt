package app.foldcade.language

import java.util.Base64

/**
 * Curated home grid. Slots are positions. A null slot is empty.
 * Item ids are catalog keys. Nothing here deletes a game. The All tile stays on the grid.
 *
 * Three kinds of group sit on the root. A folder is Foldcade's own, one per platform or
 * library, and holds every game of its kind that is on Home. A collection is one the user
 * names, and a game can be in any number of them. Recently played fills itself. A game
 * tile on the root is a pin: a shortcut that leaves the game in its folder.
 */
const val HOME_ALL: String = "home.all"

/** The collection Favorite fills. It is on the root from the first launch until Edit home switches it off. */
const val HOME_FAVORITES: String = "home.favorites"

/** The games played last, newest first. Foldcade fills it, so nothing is filed in it by hand. */
const val HOME_RECENT: String = "home.recent"

/** How many games Recently played holds. */
const val RECENT_COUNT: Int = 12

const val HOME_ANDROID_GAMES: String = "home.android-games"
const val HOME_ANDROID_APPS: String = "home.android-apps"
const val HOME_GAMENATIVE: String = "home.gamenative"
const val HOME_MOONLIGHT: String = "home.moonlight"
const val HOME_DROIDDECK: String = "home.droiddeck"
const val HOME_EMULATORS: String = "home.emulators"

enum class HomeKind {
    Rom,
    AndroidGame,
    AndroidApp,
    GameNative,
    Moonlight,
    DroidDeck,

    /** An installed app a player plugin launches games in. It is filed apart from the games. */
    Emulator,
    Loose,
}

/** A platform the catalog can classify a ROM into. [id] is canonical. */
data class HomePlatform(
    val id: String,
    val name: String,
    val aliases: Set<String> = emptySet(),
    val mark: String = "",
)

data class HomeItem(
    val id: String,
    val title: String,
    val kind: HomeKind,
    val platformId: String? = null,
    val mark: String? = null,
    val lastPlayedMillis: Long = 0L,
    /** A setup/navigation tile, kept on Home but excluded from the All library tabs. */
    val placeholder: Boolean = false,
)

/**
 * A group on Home: a folder, a collection, or Recently played. A collection is saved with
 * [userMade] set, as the folders the user made before collections were, so those carry over.
 */
data class HomeFolder(
    val id: String,
    val name: String,
    val platformId: String? = null,
    val bucket: HomeKind? = null,
    val mark: String? = null,
    val userMade: Boolean = false,
    val namedByUser: Boolean = false,
    val slots: List<String?> = emptyList(),
    /** A collection's [CollectionTint] id. Null is the default, gold, which every collection saved before colours wears. */
    val tint: String? = null,
) {
    /** Foldcade's folder for one platform or library. A game sits in one folder at a time. */
    val system: Boolean get() = !userMade && (platformId != null || bucket != null)

    val recent: Boolean get() = id == HOME_RECENT

    /** A group the user names, Favorites included. It holds what they put in it, and only that. */
    val collection: Boolean get() = !system && !recent
}

/**
 * A tile in the hand. [slots], [folders] and [openFolderId] are the board the walk is
 * measured against, as it was when the tile was lifted on the board on screen.
 */
data class HomeHold(
    val index: Int,
    val slots: List<String?>,
    val folders: Map<String, HomeFolder>,
    val openFolderId: String?,
    /** Where the tile was picked up. Cancel puts it back here. */
    val origin: Int = index,
    /** Held over the Home target above an open group's grid. A drop there carries the tile out. */
    val overHome: Boolean = false,
    /** The board before the tile was carried out of a group. Cancel puts all of it back. Null when it was not. */
    val start: HomeSpot? = null,
)

/** Where a board stood: its root, its groups, and the open one. */
data class HomeSpot(
    val slots: List<String?>,
    val folders: Map<String, HomeFolder>,
    val openFolderId: String?,
)

/** What dropping the held tile does now, which the hint row names. */
enum class HoldDrop {
    /** It stays where it is. */
    Place,

    /** It goes into the collection or folder it is over. */
    Add,

    /** It is over a group it is already in: the tile goes, and the game stays in there. */
    PutBack,

    /** It is over the Home target: it leaves the open group and is carried on along the root. */
    TakeOut,
}
/** Slots remembered while a tile is hidden. A null group means the Home root. */
data class HiddenSlot(val group: String?, val index: Int)

data class HomeBoard(
    val slots: List<String?> = listOf(HOME_ALL),
    val folders: Map<String, HomeFolder> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    /** Folders the user explicitly took off Home; scans keep them off until Show. */
    val retired: Set<String> = emptySet(),
    val addNewToHome: Boolean = true,
    val openFolderId: String? = null,
    val allOpen: Boolean = false,
    val editing: Boolean = false,
    val hold: HomeHold? = null,
    /**
     * Emulators this board has filed: each moved once out of the Android folders, where an
     * older release, or the app before it had a player, put it. Later moves are the user's.
     */
    val filedEmulators: Set<String> = emptySet(),
    /**
     * Recently played joins the root on its own once a game is played after this time. A board
     * saved before collections starts it at the update, so the update itself adds no tile.
     */
    val recentAfter: Long = 0L,
    /**
     * Games the user took off Home by removing their last place, without hiding them. A merge
     * does not take them for new games, so a removal never places anything. Placing one again
     * takes it off this list.
     */
    val offHome: Set<String> = emptySet(),
    val hiddenSlots: Map<String, List<HiddenSlot>> = emptyMap(),
    /** Highest collection number ever issued; deleting a collection never lowers it. */
    val collectionSerial: Long = 0L,
)

/** What X does to a tile on Home, and what its row says. */
enum class RemoveVerb {
    /** Takes a pin off the root. The game stays in its folder or a collection. */
    Unpin,

    /** Hides the game everywhere on Home. All still lists it. */
    Hide,

    /** Takes a game out of the open collection, or a group off Home. */
    Remove,
}

fun homeGameId(backendId: String, remoteKey: String): String = "lib:$backendId:$remoteKey"

/** The remote key in a [homeGameId]. Null for any other id. */
fun homeGameKey(homeId: String): String? {
    val rest = homeId.removePrefix("lib:").takeIf { it != homeId } ?: return null
    val colon = rest.indexOf(':')
    if (colon <= 0 || colon == rest.lastIndex) return null
    return rest.substring(colon + 1)
}

fun androidHomeId(packageName: String): String = "android:$packageName"

fun platformFolderId(canonicalId: String): String = "home.platform.$canonicalId"

/** Platforms with their own drawn mark. The mark name is the canonical id. */
val PLATFORM_MARKS: Set<String> = setOf(
    "nintendo-3ds", "nintendo-ds", "game-boy", "game-boy-color", "game-boy-advance",
    "nes", "snes", "nintendo-64", "gamecube", "wii", "nintendo-switch",
    "playstation", "playstation-2", "psp",
    "genesis", "master-system", "game-gear", "saturn", "dreamcast",
)

const val MARK_CONSOLE: String = "console"
const val MARK_FOLDER: String = "folder"
const val MARK_LIBRARY: String = "library"

/** Recently played wears the clock, on a collection's folder card (see [collectionLook]). */
const val MARK_RECENT: String = MARK_CLOCK

/**
 * The glyphs a collection can wear, in the picker's order: the theme glyphs, the
 * libraries, then every platform. A collection is already a folder, so the folder is not one.
 */
val FOLDER_ICONS: List<String> =
    COLLECTION_GLYPHS +
        listOf(MARK_LIBRARY, "android-games", "android-apps", "pc", "moonlight", "droiddeck", "emulators", MARK_CONSOLE) +
        PLATFORM_MARKS

/** Gives a collection the glyph [mark], and the colour [tint] when one is given. A folder keeps its platform's. */
fun setFolderIcon(board: HomeBoard, folderId: String, mark: String, tint: CollectionTint? = null): HomeBoard {
    val folder = board.folders[folderId] ?: return board
    if (!folder.userMade || mark !in FOLDER_ICONS) return board
    val worn = folder.copy(mark = mark, tint = tint?.id ?: folder.tint)
    return board.copy(folders = board.folders + (folderId to worn))
}

/** Kit mark for a canonical platform id. Not a console logo. A platform without its own mark gets a controller. */
fun kitMark(canonicalId: String): String =
    canonicalId.takeIf { it in PLATFORM_MARKS } ?: MARK_CONSOLE

fun bucketMark(kind: HomeKind): String = when (kind) {
    HomeKind.AndroidGame -> "android-games"
    HomeKind.AndroidApp -> "android-apps"
    HomeKind.GameNative -> "pc"
    HomeKind.Moonlight -> "moonlight"
    HomeKind.DroidDeck -> "droiddeck"
    HomeKind.Emulator -> "emulators"
    else -> MARK_FOLDER
}

fun resolvePlatform(platforms: List<HomePlatform>, idOrAlias: String?): HomePlatform? {
    if (idOrAlias.isNullOrBlank()) return null
    return platforms.firstOrNull { platform ->
        platform.id.equals(idOrAlias, ignoreCase = true) ||
            platform.aliases.any { alias -> alias.equals(idOrAlias, ignoreCase = true) }
    }
}

/** The open group's slots, or the root's. An id listed twice shows once, at its first place. */
fun visibleSlots(board: HomeBoard): List<String?> {
    val folderId = board.openFolderId
    if (folderId == null) return firstOnly(board.slots)
    return firstOnly(board.folders[folderId]?.slots ?: emptyList())
}

/**
 * The collection open on screen while it holds no game, so the grid has nothing to show.
 * Null for a folder, Recently played, All, the root, or while a tile is in the hand.
 */
fun emptyCollection(board: HomeBoard): HomeFolder? {
    if (board.allOpen || board.hold != null) return null
    val folder = board.openFolderId?.let { board.folders[it] } ?: return null
    return folder.takeIf { it.collection && visibleSlots(board).all { slot -> slot == null } }
}

/**
 * Reachable from Home: pinned on the root, or in a folder, a collection, or Recently played
 * there. All's dot shows this. A folder taken off Home no longer counts.
 */
fun onHome(board: HomeBoard, id: String): Boolean = reachable(board, id, viaRecent = true)

/**
 * [onHome], with or without Recently played. Placing a game asks without it: Recently played
 * only reflects play, so a game seen only there is still put in its folder.
 */
private fun reachable(board: HomeBoard, id: String, viaRecent: Boolean): Boolean {
    if (id == HOME_ALL) return true
    if (id in board.hidden) return false
    if (board.slots.contains(id)) return true
    return board.slots.any { slot -> board.folders[slot]?.takeIf { viaRecent || !it.recent }?.slots?.contains(id) == true }
}

/** The game is a pin: its own tile on the root. */
fun pinned(board: HomeBoard, id: String): Boolean = id in board.slots

fun favorite(board: HomeBoard, id: String): Boolean =
    board.folders[HOME_FAVORITES]?.slots?.contains(id) == true

/** Collections on the root, in the root's order. */
fun collectionsOnHome(board: HomeBoard): List<HomeFolder> =
    board.slots.mapNotNull { slot -> board.folders[slot]?.takeIf { it.collection } }.distinctBy { it.id }

/**
 * What Add to lists for the game [id]: Favorites first, even before it exists, since
 * choosing it makes it, then every collection on the root. Each says whether [id] is in it.
 */
fun collectionChoices(board: HomeBoard, id: String): List<CollectionChoice> {
    val favorites = board.folders[HOME_FAVORITES]
    val first = CollectionChoice(HOME_FAVORITES, favorites?.name ?: Copy.favorites, favorites?.slots?.contains(id) == true)
    return listOf(first) + collectionsOnHome(board).filter { it.id != HOME_FAVORITES }
        .map { CollectionChoice(it.id, it.name, id in it.slots) }
}

fun folderCount(board: HomeBoard, folderId: String): Int =
    board.folders[folderId]?.slots?.count { it != null } ?: 0

/**
 * Merges [items] into [board]. Placed tiles stay put. A missing catalog id leaves an empty slot,
 * wherever it was. New items go into their folder when [HomeBoard.addNewToHome] is on,
 * otherwise they stay off the grid. Hidden tiles stay hidden. Games are not deleted.
 *
 * [unsettled] is true for an id whose source has not reported yet, such as a library still
 * scanning after launch. Its slots are kept rather than read as a game that went away.
 *
 * No games, no folder. A folder Foldcade made, for a platform or a library, appears with its
 * first game and goes away once none is left in it. Emulators have a folder of their own.
 * A folder holds every game of its kind on Home, so a pin or a collection's game is in its
 * folder too ([completeFolders]). Recently played follows the items' play times.
 */
fun mergeHome(
    board: HomeBoard,
    items: List<HomeItem>,
    platforms: List<HomePlatform>,
    unsettled: (String) -> Boolean = { false },
): HomeBoard {
    val catalog = items.distinctBy { it.id }
    val ids = catalog.map { it.id }.toSet()
    val keep = { id: String -> id in ids || unsettled(id) }
    val hidden = board.hidden.filter(keep).toSet()
    var next = board.copy(
        slots = tidySlots(board.slots, keep, board.folders, hidden),
        folders = board.folders.mapValues { (_, folder) ->
            folder.copy(slots = tidySlots(folder.slots, keep, board.folders, hidden))
        },
        hidden = hidden,
        offHome = board.offHome.filter(keep).toSet(),
        filedEmulators = board.filedEmulators.filter(keep).toSet(),
    )
    next = fileEmulators(next, catalog, platforms)
    val known = placedIds(next)
    for (item in catalog) {
        if (item.id in next.hidden || item.id in next.offHome || item.id in known) continue
        if (!next.addNewToHome) continue
        next = placeNew(next, item, platforms, reviveRetired = false)
        known += item.id
    }
    next = completeFolders(next, catalog, platforms)
    next = dropEmptySystemFolders(next, waiting = unfiledIds(next).any { it !in ids && unsettled(it) })
    next = next.copy(offHome = next.offHome - placedIds(next))
    next = refreshRecent(next, catalog, unsettled)
    next = refreshFolderNames(next, platforms)
    if (HOME_ALL !in next.slots) {
        next = next.copy(slots = listOf(HOME_ALL) + next.slots)
    }
    return next
}

/**
 * Lifts the tile at [index], or the empty slot there. A lifted gap walks the way a tile
 * does, so carrying one onto a tile brings that tile back to where the gap was.
 * Recently played keeps its own order, so nothing in it lifts.
 */
fun pickUp(board: HomeBoard, index: Int): HomeBoard {
    if (index < 0) return board
    // A second lift, a long press on another tile, puts the first one down where it is.
    if (board.hold != null) return board.copy(hold = null)
    if (board.openFolderId?.let { board.folders[it] }?.recent == true) return board
    return board.copy(
        hold = HomeHold(
            index = index,
            slots = board.slots,
            folders = board.folders,
            openFolderId = board.openFolderId,
        ),
        editing = true,
    )
}

/**
 * Walks the held tile to [toIndex]. It trades places with the tile there, and every tile it
 * passed on the way is back where it was at pickup, so a long move disturbs one other tile.
 *
 * Onto a group that takes it, a game hovers instead: the group stays put, and every tile is
 * where it was at pickup, so walking on passes over the group. Dropping there files the game
 * in it ([drop]). Onto a group that does not take it, it trades places. A held gap or group
 * trades places too, and never hovers.
 */
fun moveHeld(board: HomeBoard, toIndex: Int, catalog: List<HomeItem>, platforms: List<HomePlatform>): HomeBoard {
    val hold = board.hold ?: return board
    if (toIndex < 0) return board
    val before = holdBase(hold)
    val moving = before.getOrNull(hold.origin)
    val slots = before.toMutableList()
    while (slots.size <= maxOf(toIndex, hold.origin)) slots += null
    val target = slots[toIndex]
    val walked = hold.copy(index = toIndex, overHome = false)
    if (hoversOver(board, moving, target, catalog, platforms)) {
        return writeVisible(board, before).copy(hold = walked)
    }
    slots[hold.origin] = target
    slots[toIndex] = moving
    // A gap walked past the last tile leaves no empty slots behind it.
    while (slots.size > before.size && slots.last() == null) slots.removeAt(slots.lastIndex)
    return writeVisible(board, slots).copy(hold = walked)
}

/** The held game would go into [target], a group that takes it. */
private fun hoversOver(
    board: HomeBoard,
    moving: String?,
    target: String?,
    catalog: List<HomeItem>,
    platforms: List<HomePlatform>,
): Boolean {
    if (moving == null || target == null || target == moving || !isGame(board, moving)) return false
    val group = board.folders[target] ?: return false
    return folderTakes(group, catalog.firstOrNull { it.id == moving }, platforms)
}

private fun isGame(board: HomeBoard, id: String): Boolean = id != HOME_ALL && board.folders[id] == null

/** The slots the held tile walks against: the open group's, or the root's, as they were at pickup. */
private fun holdBase(hold: HomeHold): List<String?> =
    hold.openFolderId?.let { hold.folders[it]?.slots } ?: hold.slots

/** The game, group or All tile in the hand. Null for a held gap, or nothing held. */
fun heldId(board: HomeBoard): String? {
    val hold = board.hold ?: return null
    return holdBase(hold).getOrNull(hold.origin)
}

/**
 * The group the held game is over, which a drop files it in. Null while it is not over one.
 * Over a group, the tile there is the group and not the game, as [moveHeld] leaves it.
 */
fun heldOver(board: HomeBoard): String? {
    val hold = board.hold ?: return null
    if (hold.overHome) return null
    val id = heldId(board) ?: return null
    val over = visibleSlots(board).getOrNull(hold.index) ?: return null
    return over.takeIf { it != id && board.folders[it] != null }
}

/** What a drop does now. Null while nothing is held. */
fun holdDrop(board: HomeBoard): HoldDrop? {
    val hold = board.hold ?: return null
    if (hold.overHome) return HoldDrop.TakeOut
    val over = heldOver(board) ?: return HoldDrop.Place
    val id = heldId(board)
    return if (board.folders[over]?.slots?.contains(id) == true) HoldDrop.PutBack else HoldDrop.Add
}

/**
 * Whether the held tile can rise to the Home target: it is a game, in a folder or a
 * collection. Recently played keeps its own order, so nothing lifts there to begin with.
 */
fun canTakeOut(board: HomeBoard): Boolean {
    val hold = board.hold ?: return false
    val open = hold.openFolderId?.let { board.folders[it] } ?: return false
    val id = heldId(board) ?: return false
    return !open.recent && isGame(board, id)
}

/**
 * Holds the tile over the Home target above the open group's grid. Every tile there is back
 * where it was at pickup, as over a group. [HomeHold.index] keeps the slot it rose from, so
 * Down comes back to it.
 */
fun raiseToHome(board: HomeBoard): HomeBoard {
    val hold = board.hold ?: return board
    if (!canTakeOut(board)) return board
    return writeVisible(board, holdBase(hold)).copy(hold = hold.copy(overHome = true))
}

/**
 * Carries the held game out of the open group onto the root, still in the hand. From a folder
 * it stays filed there, and from a collection it leaves it; either group is as it was at
 * pickup otherwise. On the root it is a pin over the group's slot, and the group stands aside
 * in the nearest free slot, as the tile under a held one does. An older pin of the game gives
 * its slot up. Cancel puts back the board as it was before any of this.
 */
fun carryOut(board: HomeBoard): HomeBoard {
    val hold = board.hold ?: return board
    if (!hold.overHome || !canTakeOut(board)) return board
    val openId = hold.openFolderId ?: return board
    val group = board.folders[openId] ?: return board
    val before = holdBase(hold)
    val id = before[hold.origin] ?: return board
    val left = if (group.collection) before.mapIndexed { index, value -> if (index == hold.origin) null else value } else before
    val folders = board.folders + (openId to group.copy(slots = left))
    val root = board.slots.map { if (it == id) null else it }.toMutableList()
    val at = root.indexOf(openId).takeIf { it >= 0 }
        ?: root.indexOfFirst { it == null }.takeIf { it >= 0 }
        ?: root.size
    val aside = if (root.getOrNull(at) == null) at else freeSlot(root, at)
    while (root.size <= maxOf(at, aside)) root += null
    // The walk is measured from a root with the pin in the free slot, so a step away
    // brings the group back to its own slot and leaves the free one to the tile passed.
    val base = root.toMutableList().also { it[aside] = id }
    val carried = base.toMutableList().also {
        it[aside] = base[at]
        it[at] = id
    }
    val start = hold.start ?: HomeSpot(hold.slots, hold.folders, hold.openFolderId)
    return board.copy(
        slots = carried,
        folders = folders,
        openFolderId = null,
        offHome = board.offHome - id,
        hold = HomeHold(index = at, slots = base, folders = folders, openFolderId = null, origin = aside, start = start),
    )
}

/** The empty slot nearest [at], after it on a tie. Past the last slot is always free. */
private fun freeSlot(slots: List<String?>, at: Int): Int {
    for (step in 1..slots.size + 1) {
        val after = at + step
        if (after >= slots.size || slots[after] == null) return after
        val before = at - step
        if (before >= 0 && slots[before] == null) return before
    }
    return slots.size
}

/**
 * Whether [item] may go in [folder]. A collection takes anything, and Recently played
 * nothing. A platform's folder takes only that platform's games, and a library's folder
 * only that library's. The installed Android folders trade among themselves, so an app
 * filed as the wrong kind can be moved where it belongs. An item the catalog does not
 * know goes only in a collection.
 */
fun folderTakes(folder: HomeFolder, item: HomeItem?, platforms: List<HomePlatform>): Boolean {
    if (folder.recent) return false
    if (folder.collection) return true
    val platformId = folder.platformId
    val bucket = folder.bucket
    if (item == null) return false
    if (platformId != null) return item.kind == HomeKind.Rom && romPlatformId(item, platforms) == platformId
    return item.kind == bucket || (bucket in INSTALLED_KINDS && item.kind in INSTALLED_KINDS)
}

private val INSTALLED_KINDS: Set<HomeKind> = setOf(HomeKind.AndroidGame, HomeKind.AndroidApp, HomeKind.Emulator)

/**
 * Puts the held tile down where it is. Over a group, the game goes into it: a pin leaves the
 * root for that collection, or for its folder, and every other tile is where it was at
 * pickup. Over the Home target nothing is put down yet: the tile is carried out ([carryOut]).
 */
fun drop(board: HomeBoard, catalog: List<HomeItem>, platforms: List<HomePlatform>): HomeBoard {
    val hold = board.hold ?: return board
    if (hold.overHome) return carryOut(board)
    val over = heldOver(board) ?: return board.copy(hold = null)
    val id = heldId(board) ?: return board.copy(hold = null)
    val slots = holdBase(hold).mapIndexed { index, value -> if (index == hold.origin) null else value }
    return fileGame(writeVisible(board, slots), id, over, catalog, platforms).copy(hold = null)
}

/** Puts everything back as it was at pickup, the group a tile was carried out of included. */
fun cancelHold(board: HomeBoard): HomeBoard {
    val hold = board.hold ?: return board
    val spot = hold.start ?: HomeSpot(hold.slots, hold.folders, hold.openFolderId)
    return board.copy(
        slots = spot.slots,
        folders = spot.folders,
        openFolderId = spot.openFolderId,
        hold = null,
    )
}

/**
 * Closes the gaps on the board on screen, the root or the open group: its tiles keep their
 * reading order, column by column and top to bottom, and close up from the first slot.
 */
fun tidy(board: HomeBoard): HomeBoard {
    if (!canTidy(board)) return board
    val packed = visibleSlots(board).filterNotNull()
    return writeVisible(board, packed)
}

/** The board on screen has a gap before its last tile, and is one the player arranges. */
fun canTidy(board: HomeBoard): Boolean {
    if (board.hold != null || board.allOpen) return false
    if (board.openFolderId?.let { board.folders[it] }?.recent == true) return false
    val slots = visibleSlots(board)
    val last = slots.indexOfLast { it != null }
    return (0 until last).any { slots[it] == null }
}

/**
 * What X does to the tile at [index], or null when it does nothing. On the root a group
 * comes off Home, and a pin comes off the root while the game is still filed in a folder,
 * one taken off Home included, or a collection: those places keep it, so a folder that comes
 * back has it. A pin that is the game's only place hides it instead. In a
 * collection X takes the game out of it, and in a folder it hides the game. Recently
 * played and the All tile keep what they show.
 */
fun removeVerb(board: HomeBoard, index: Int): RemoveVerb? {
    val id = visibleSlots(board).getOrNull(index) ?: return null
    if (id == HOME_ALL) return null
    val openId = board.openFolderId
    if (openId == null) {
        if (board.folders[id] != null) return RemoveVerb.Remove
        val elsewhere = board.folders.values.any { !it.recent && id in it.slots }
        return if (elsewhere) RemoveVerb.Unpin else RemoveVerb.Hide
    }
    val open = board.folders[openId] ?: return null
    return when {
        open.recent -> null
        open.collection -> RemoveVerb.Remove
        else -> RemoveVerb.Hide
    }
}

/** Does what [removeVerb] says for the tile at [index]. */
fun removeFromHome(board: HomeBoard, index: Int): HomeBoard {
    val id = visibleSlots(board).getOrNull(index) ?: return board
    return when (removeVerb(board, index)) {
        null -> board
        RemoveVerb.Unpin -> keepOff(board, board.copy(slots = board.slots.mapIndexed { slot, value -> if (slot == index) null else value }, hold = null))
        RemoveVerb.Hide -> hideGame(board, id)
        RemoveVerb.Remove -> {
            val openId = board.openFolderId
            if (openId == null) removeFolder(board, id) else removeFromCollection(board, openId, id)
        }
    }
}

/**
 * [board] ready to take [id] somewhere: not hidden, and not off Home. Every way a game is
 * added (a collection, Favorites, a pin, a folder) goes through this, so none of them leaves
 * it hidden for the next merge to strip.
 */
private fun admit(board: HomeBoard, id: String): HomeBoard =
    if (id !in board.hidden && id !in board.offHome) board else restoreHiddenSlots(board.copy(hidden = board.hidden - id, offHome = board.offHome - id), id)

/** [after] a removal from [before], with every game it left nowhere on [HomeBoard.offHome]. */
private fun keepOff(before: HomeBoard, after: HomeBoard): HomeBoard {
    val left = placedIds(before) - placedIds(after) - after.hidden - after.folders.keys - HOME_ALL
    return if (left.isEmpty()) after else after.copy(offHome = after.offHome + left)
}

/**
 * Hides [id] from every place on Home: pins, folders and collections. All still lists it,
 * and Show restores its previous slots, including collection memberships and pins.
 */
fun hideGame(board: HomeBoard, id: String): HomeBoard {
    if (id == HOME_ALL || board.folders[id] != null) return board
    if (id in board.hidden) return board
    val remembered = buildList {
        board.slots.forEachIndexed { index, value -> if (value == id) add(HiddenSlot(null, index)) }
        board.folders.values.forEach { folder ->
            folder.slots.forEachIndexed { index, value -> if (value == id) add(HiddenSlot(folder.id, index)) }
        }
    }
    return board.copy(
        hiddenSlots = if (remembered.isEmpty()) board.hiddenSlots - id else board.hiddenSlots + (id to remembered),
        slots = board.slots.map { if (it == id) null else it },
        folders = board.folders.mapValues { (_, folder) -> folder.copy(slots = folder.slots.map { if (it == id) null else it }) },
        hidden = board.hidden + id,
        hold = null,
    )
}

/**
 * Takes the group [folderId] off Home. Its slot is left empty. A folder is retired with its
 * games still filed in it, so it comes back whole; they stay in All, collections and pins
 * meanwhile. A collection is deleted, and the games in it stay everywhere else. Favorites
 * and Recently played are retired, Favorites with its games, and come back from Edit home.
 */
fun removeFolder(board: HomeBoard, folderId: String): HomeBoard {
    if (folderId == HOME_ALL) return board
    val folder = board.folders[folderId] ?: return board
    val gone = board.copy(
        hiddenSlots = board.hiddenSlots + (folderId to listOf(HiddenSlot(null, board.slots.indexOf(folderId))).filter { it.index >= 0 }),
        slots = board.slots.map { if (it == folderId) null else it },
        openFolderId = board.openFolderId?.takeIf { it != folderId },
        hold = null,
    )
    return when {
        folder.system -> gone.copy(retired = board.retired + folderId)
        folder.recent || folderId == HOME_FAVORITES -> gone.copy(retired = board.retired + folderId)
        else -> keepOff(board, gone.copy(
            folders = board.folders - folderId,
            collectionSerial = collectionHighWater(board),
            hiddenSlots = gone.hiddenSlots.filterKeys { it != folderId }
                .mapValues { (_, places) -> places.filter { it.group != folderId } }.filterValues { it.isNotEmpty() },
        ))
    }
}

/**
 * A new collection named [name]. It takes root slot [at] when that is empty, else the first
 * free one. With [member] it starts holding that game, which stays everywhere it already is.
 */
fun newCollection(board: HomeBoard, collectionId: String, name: String, at: Int? = null, member: String? = null): HomeBoard {
    if (collectionId in board.folders || collectionId == HOME_ALL) return board
    val serial = collectionNumber(collectionId)
    if (serial != null && serial <= board.collectionSerial) return board
    val game = member?.takeIf { it != HOME_ALL && board.folders[it] == null }
    val folder = HomeFolder(id = collectionId, name = name, userMade = true, mark = MARK_FOLDER, slots = listOfNotNull(game))
    val admitted = game?.let { admit(board, it) } ?: board
    val made = admitted.copy(folders = admitted.folders + (collectionId to folder), hold = null,
        collectionSerial = maxOf(collectionHighWater(admitted), serial ?: 0L))
    val target = at?.takeIf { it >= 0 && made.slots.getOrNull(it) == null } ?: return placeOnRoot(made, collectionId)
    val slots = made.slots.toMutableList()
    while (slots.size <= target) slots += null
    slots[target] = collectionId
    return made.copy(slots = slots)
}

/** Puts the game [id] in the collection [collectionId]. It stays everywhere else it is. */
fun addToCollection(board: HomeBoard, collectionId: String, id: String): HomeBoard {
    if (id == HOME_ALL || board.folders[id] != null) return board
    if (board.folders[collectionId]?.collection != true) return board
    return dropInto(admit(board, id), collectionId, id)
}

/** Takes the game [id] out of [collectionId]. Its slot there is left empty. */
fun removeFromCollection(board: HomeBoard, collectionId: String, id: String): HomeBoard {
    val folder = board.folders[collectionId]?.takeIf { it.collection } ?: return board
    if (id !in folder.slots) return board
    val left = folder.copy(slots = folder.slots.map { if (it == id) null else it })
    return keepOff(board, board.copy(folders = board.folders + (collectionId to left), hold = null))
}

/** Adds [id] to [collectionId], or takes it out when it is already there. Favorites is made when it is missing. */
fun toggleCollection(board: HomeBoard, collectionId: String, id: String): HomeBoard {
    if (collectionId == HOME_FAVORITES) return toggleFavorite(board, id)
    val folder = board.folders[collectionId] ?: return board
    return if (id in folder.slots) removeFromCollection(board, collectionId, id) else addToCollection(board, collectionId, id)
}

/**
 * Favorites [id], or unfavorites it. Favorites is made when missing, on the root unless Edit
 * home switched it off. Switched off, it still keeps what is favorited, out of sight.
 */
fun toggleFavorite(board: HomeBoard, id: String): HomeBoard {
    if (id == HOME_ALL || id.isBlank() || board.folders[id] != null) return board
    val existing = board.folders[HOME_FAVORITES]
    if (existing != null && id in existing.slots) return removeFromCollection(board, HOME_FAVORITES, id)
    val made = when {
        existing != null -> board
        HOME_FAVORITES in board.retired -> board.copy(folders = board.folders + (HOME_FAVORITES to favoritesFolder()))
        else -> withFavorites(board)
    }
    return dropInto(admit(made, id), HOME_FAVORITES, id)
}

private fun favoritesFolder(): HomeFolder = HomeFolder(HOME_FAVORITES, Copy.favorites, userMade = true, mark = MARK_FOLDER)

/**
 * Favorites on the root, in the first free slot, empty until a game is favorited. A board
 * loads through this, so Favorites is there from the first launch. Switched off, it stays off.
 */
fun withFavorites(board: HomeBoard): HomeBoard {
    if (HOME_FAVORITES in board.retired || HOME_FAVORITES in board.slots) return board
    val folder = board.folders[HOME_FAVORITES] ?: favoritesFolder()
    return placeOnRoot(board.copy(folders = board.folders + (HOME_FAVORITES to folder)), HOME_FAVORITES)
}

/** The groups Edit home switches on and off. */
val SWITCHED_GROUPS: Set<String> = setOf(HOME_FAVORITES, HOME_RECENT)

/** The group [id] has its tile on the root. */
fun groupOnHome(board: HomeBoard, id: String): Boolean = id in board.slots

/**
 * Edit home's switch for Favorites or Recently played. Off takes the tile off Home as X does;
 * Favorites keeps its games. On puts it back in its old slot when that is free, else the
 * first free one, and makes it when there was none yet.
 */
fun setGroupOnHome(board: HomeBoard, id: String, on: Boolean, catalog: List<HomeItem>, platforms: List<HomePlatform>): HomeBoard {
    if (id !in SWITCHED_GROUPS || board.hold != null) return board
    // Off holds even with no tile yet, so neither the first play nor a load puts one there.
    if (!on) return if (groupOnHome(board, id) && id in board.folders) removeFolder(board, id) else board.copy(retired = board.retired + id)
    if (groupOnHome(board, id)) return board
    var next = if (id in board.retired) showHidden(board, id, catalog, platforms) else board
    if (!groupOnHome(next, id)) {
        val folder = next.folders[id] ?: if (id == HOME_RECENT) HomeFolder(id, Copy.recentlyPlayed, mark = MARK_RECENT) else favoritesFolder()
        next = placeOnRoot(next.copy(folders = next.folders + (id to folder), retired = next.retired - id), id)
    }
    return if (id == HOME_RECENT) refreshRecent(next, catalog) { false } else next
}

/** A pin for [id] in the first free root slot. The game stays in its folder. A hidden one comes back. */
fun pinToHome(board: HomeBoard, id: String): HomeBoard {
    if (id == HOME_ALL || id.isBlank() || board.folders[id] != null) return board
    return placeOnRoot(admit(board, id).copy(hold = null), id)
}

/**
 * Pins [item], and files it in its folder too when it was nowhere on the board, so a game
 * brought back from All has both. Its folder is made when the board has none, as a new game's
 * would be, but one the user took off Home stays off.
 */
fun pinItemToHome(board: HomeBoard, item: HomeItem, platforms: List<HomePlatform>): HomeBoard {
    val unplaced = item.id !in placedIds(board)
    val pinned = pinToHome(board, item.id)
    if (!unplaced || pinned == board) return pinned
    val folderId = systemFolderId(item, platforms) ?: return pinned
    val folder = pinned.folders[folderId]
    return when {
        folder != null && item.id in folder.slots -> pinned
        folder != null -> pinned.copy(folders = pinned.folders + (folderId to folder.copy(slots = atEnd(folder.slots, item.id))))
        folderId in pinned.retired -> pinned
        else -> placeNew(pinned, item, platforms)
    }
}

/**
 * Files the game [id] in [folderId], unhidden. A collection keeps it everywhere else too.
 * A folder must take it, and the game leaves any other folder, since it sits in one.
 * A null [folderId] pins it on the root.
 */
fun fileGame(
    board: HomeBoard,
    id: String,
    folderId: String?,
    catalog: List<HomeItem>,
    platforms: List<HomePlatform>,
): HomeBoard {
    if (id == HOME_ALL || board.folders[id] != null) return board
    if (folderId == null) return pinToHome(board, id)
    val folder = board.folders[folderId] ?: return board
    if (!folderTakes(folder, catalog.firstOrNull { it.id == id }, platforms)) return board
    val shown = admit(board, id).copy(hold = null)
    if (folder.collection) return addToCollection(shown, folderId, id)
    val moved = shown.copy(
        folders = shown.folders.mapValues { (key, other) ->
            if (key == folderId || !other.system) other else other.copy(slots = other.slots.map { if (it == id) null else it })
        },
    )
    return dropInto(moved, folderId, id)
}

fun renameFolder(board: HomeBoard, folderId: String, name: String): HomeBoard {
    val folder = board.folders[folderId] ?: return board
    val cleaned = name.replace('\t', ' ').replace('\n', ' ').trim()
    if (cleaned.isEmpty()) return board
    val renamed = folder.copy(name = cleaned, namedByUser = true)
    return board.copy(folders = board.folders + (folderId to renamed))
}

private fun collectionNumber(id: String): Long? = id.removePrefix("home.user.").takeIf { it != id }?.toLongOrNull()

/** Older saves have no counter; surviving ids and hidden references establish its lower bound. */
fun collectionHighWater(board: HomeBoard): Long = maxOf(board.collectionSerial,
    (board.folders.keys + board.hiddenSlots.keys + board.hiddenSlots.values.flatten().mapNotNull { it.group })
        .mapNotNull(::collectionNumber).maxOrNull() ?: 0L)

fun nextUserFolderId(board: HomeBoard): String = "home.user.${collectionHighWater(board) + 1L}"

/**
 * The folder [item] is filed in, while it is on Home: its platform's folder for a ROM,
 * or its library's folder for an app or stream. Null when that folder is gone or off
 * Home, or the item has none.
 */
fun homeFolderFor(board: HomeBoard, item: HomeItem, platforms: List<HomePlatform>): String? =
    systemFolderId(item, platforms)?.takeIf { it in board.folders && it !in board.retired }

/** The id of the folder [item] belongs in, whether or not the board has it. */
private fun systemFolderId(item: HomeItem, platforms: List<HomePlatform>): String? = when (item.kind) {
    HomeKind.Rom -> romPlatformId(item, platforms)?.let(::platformFolderId)
    else -> bucketFolderId(item.kind)
}

/** The canonical platform of a ROM, or its raw platform id when no platform knows it. */
private fun romPlatformId(item: HomeItem, platforms: List<HomePlatform>): String? =
    resolvePlatform(platforms, item.platformId)?.id ?: item.platformId?.takeIf { it.isNotBlank() }

/**
 * Puts [item] back on Home, unhidden, where a new one would go. A folder that went away,
 * or was taken off Home, comes back with it.
 */
fun addItemToHome(board: HomeBoard, item: HomeItem, platforms: List<HomePlatform>): HomeBoard {
    if (item.id == HOME_ALL || item.id.isBlank()) return board
    val shown = admit(board, item.id)
    if (reachable(shown, item.id, viaRecent = false)) return shown
    return placeNew(shown, item, platforms)
}

fun setAddNewToHome(board: HomeBoard, enabled: Boolean): HomeBoard =
    board.copy(addNewToHome = enabled)

fun openFolder(board: HomeBoard, folderId: String): HomeBoard {
    if (board.folders[folderId] == null) return board
    return board.copy(openFolderId = folderId, allOpen = false, hold = null)
}

fun closeFolder(board: HomeBoard): HomeBoard = board.copy(openFolderId = null, hold = null)

fun openAll(board: HomeBoard): HomeBoard =
    board.copy(allOpen = true, openFolderId = null, hold = null, editing = false)

fun closeAll(board: HomeBoard): HomeBoard = board.copy(allOpen = false)

fun enterEdit(board: HomeBoard): HomeBoard =
    board.copy(editing = true, allOpen = false, hold = null)

fun leaveEdit(board: HomeBoard): HomeBoard = cancelHold(board).copy(editing = false)

private val gameKinds = setOf(HomeKind.Rom, HomeKind.AndroidGame, HomeKind.GameNative, HomeKind.Moonlight, HomeKind.DroidDeck)

/** All lists every game alphabetically, or played games newest first with unplayed games A to Z. */
fun allItems(items: List<HomeItem>, sort: LibrarySort = LibrarySort.Listed): List<HomeItem> = items.distinctBy { it.id }
    .filter { !it.placeholder && it.kind in gameKinds }
    .sortedWith(compareBy<HomeItem> { if (sort == LibrarySort.RecentlyPlayed) -it.lastPlayedMillis else 0L }
        .thenBy { it.title.lowercase() }.thenBy { it.id })

/** Next letter group. Forward lands on its first row. Backward lands on the previous group's first row. Stays put at the ends. Does not wrap. */
fun jumpLetter(labels: List<String>, index: Int, forward: Boolean): Int {
    if (labels.isEmpty()) return 0
    val current = index.coerceIn(0, labels.lastIndex)
    val letter = letterGroup(labels[current])
    if (forward) {
        for (step in (current + 1)..labels.lastIndex) {
            if (letterGroup(labels[step]) != letter) return step
        }
        return current
    }
    val groupStart = (current downTo 0).first { step ->
        step == 0 || letterGroup(labels[step - 1]) != letter
    }
    if (groupStart == 0) return current
    val previous = letterGroup(labels[groupStart - 1])
    return (groupStart - 1 downTo 0).first { step ->
        step == 0 || letterGroup(labels[step - 1]) != previous
    }
}

/** A versioned snapshot of saved choices. A lifted tile is saved at its last confirmed position. */
fun encodeHome(board: HomeBoard): String {
    val saved = cancelHold(board)
    return buildString {
        append("v2\n")
        homeRow("addNew", listOf(if (saved.addNewToHome) "1" else "0"))
        homeRow("hidden", saved.hidden.sorted())
        homeRow("retired", saved.retired.sorted())
        homeRow("slots", saved.slots.map { it.orEmpty() })
        homeRow("filedEmulators", saved.filedEmulators.sorted())
        homeRow("recentAfter", listOf(saved.recentAfter.toString()))
        homeRow("offHome", saved.offHome.sorted())
        if (saved.collectionSerial > 0L) homeRow("collectionSerial", listOf(saved.collectionSerial.toString()))
        saved.hiddenSlots.toSortedMap().forEach { (id, places) ->
            places.forEach { homeRow("hiddenSlot", listOf(id, it.group.orEmpty(), it.index.toString())) }
        }
        for (folder in saved.folders.values.sortedBy { it.id }) {
            homeRow(
                "folder",
                listOf(
                    folder.id, folder.name, folder.platformId.orEmpty(), folder.bucket?.name.orEmpty(),
                    if (folder.userMade) "1" else "0", if (folder.namedByUser) "1" else "0", folder.mark.orEmpty(),
                ) + folder.slots.map { it.orEmpty() },
            )
        }
        // A row of its own, so a release before colours reads the folder rows it knows and skips these.
        for (folder in saved.folders.values.sortedBy { it.id }) {
            folder.tint?.let { homeRow("tint", listOf(folder.id, it)) }
        }
    }
}

/** Each field is Base64url, so opaque ids and names cannot split a record. */
private fun StringBuilder.homeRow(kind: String, fields: List<String>) {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    append(kind)
    if (fields.isNotEmpty()) append('\t')
    append(fields.joinToString("\t") { encoder.encodeToString(it.toByteArray(Charsets.UTF_8)) })
    append('\n')
}

/** A board saved by a release before collections. Its first load keeps a copy of it as it was. */
fun savedBeforeCollections(raw: String?): Boolean =
    !raw.isNullOrBlank() && raw.lineSequence().none { it == "recentAfter" || it.startsWith("recentAfter\t") }

/**
 * Reads a saved board. Folders the user made come back as collections. A board saved before
 * collections has no Recently played start, so it takes [nowMillis]: see [HomeBoard.recentAfter].
 */
fun decodeHome(raw: String?, nowMillis: Long = 0L): HomeBoard {
    if (raw.isNullOrBlank()) return HomeBoard()
    val encoded = raw.lineSequence().firstOrNull() == "v2"
    val decoder = Base64.getUrlDecoder()
    var board = HomeBoard(slots = emptyList(), recentAfter = nowMillis)
    val tints = HashMap<String, String>()
    for (line in raw.lineSequence()) {
        if (line.isBlank()) continue
        val fields = line.split('\t')
        val bits = if (encoded) {
            try {
                listOf(fields.first()) + fields.drop(1).map {
                    String(decoder.decode(it), Charsets.UTF_8)
                }
            } catch (_: IllegalArgumentException) {
                continue
            }
        } else fields
        when (bits.firstOrNull()) {
            "v1", "v2" -> Unit
            "addNew" -> board = board.copy(addNewToHome = bits.getOrNull(1) != "0")
            "hidden" -> board = board.copy(hidden = bits.drop(1).filter { it.isNotEmpty() }.toSet())
            "retired" -> board = board.copy(retired = bits.drop(1).filter { it.isNotEmpty() }.toSet())
            "slots" -> board = board.copy(slots = decodeSlots(bits.drop(1), encoded))
            // An older release's emulatorsFiled is not read: it covered only the emulators of its day.
            "filedEmulators" -> board = board.copy(filedEmulators = bits.drop(1).filter { it.isNotEmpty() }.toSet())
            "recentAfter" -> bits.getOrNull(1)?.toLongOrNull()?.let { board = board.copy(recentAfter = it) }
            "offHome" -> board = board.copy(offHome = bits.drop(1).filter { it.isNotEmpty() }.toSet())
            "tint" -> if (bits.size >= 3 && bits[2].isNotEmpty()) tints[bits[1]] = bits[2]
            "collectionSerial" -> bits.getOrNull(1)?.toLongOrNull()?.takeIf { it >= 0L }?.let { board = board.copy(collectionSerial = it) }
            "hiddenSlot" -> {
                val id = bits.getOrNull(1) ?: continue
                val index = bits.getOrNull(3)?.toIntOrNull()?.takeIf { it >= 0 } ?: continue
                val place = HiddenSlot(bits.getOrNull(2)?.ifEmpty { null }, index)
                board = board.copy(hiddenSlots = board.hiddenSlots + (id to (board.hiddenSlots[id].orEmpty() + place)))
            }
            "folder" -> {
                if (bits.size < 8) continue
                val bucket = bits[4].takeIf { it.isNotEmpty() }?.let { runCatching { HomeKind.valueOf(it) }.getOrNull() }
                val folder = HomeFolder(
                    id = bits[1],
                    name = bits[2],
                    platformId = bits[3].takeIf { it.isNotEmpty() },
                    bucket = bucket,
                    userMade = bits[5] == "1",
                    namedByUser = bits[6] == "1",
                    mark = bits[7].takeIf { it.isNotEmpty() },
                    slots = decodeSlots(bits.drop(8), encoded),
                )
                if (folder.id.isNotEmpty()) {
                    board = board.copy(folders = board.folders + (folder.id to folder))
                }
            }
        }
    }
    board = board.copy(folders = board.folders.mapValues { (id, folder) -> tints[id]?.let { folder.copy(tint = it) } ?: folder })
    if (board.slots.isEmpty()) board = board.copy(slots = listOf(HOME_ALL))
    if (HOME_ALL !in board.slots) board = board.copy(slots = listOf(HOME_ALL) + board.slots)
    return refreshFolderNames(board, emptyList())
}

/**
 * One list's slots after a merge. A game no source has, or one hidden, leaves an empty
 * slot, and a second copy of an id does too.
 */
private fun tidySlots(
    slots: List<String?>,
    keep: (String) -> Boolean,
    folders: Map<String, HomeFolder>,
    hidden: Set<String>,
): List<String?> = firstOnly(
    slots.map { id ->
        when {
            id == null -> null
            id == HOME_ALL -> id
            id in folders -> id
            id in hidden -> null
            keep(id) -> id
            else -> null
        }
    },
)

private fun firstOnly(slots: List<String?>): List<String?> {
    val seen = HashSet<String>()
    return slots.map { id -> id?.takeIf { seen.add(it) } }
}

/**
 * Every game somewhere on the board, a folder taken off Home included, so a merge does not
 * take it for a new one and bring that folder back. Recently played only reflects play.
 */
private fun placedIds(board: HomeBoard): MutableSet<String> {
    val ids = HashSet<String>()
    board.slots.filterNotNullTo(ids)
    board.folders.values.filter { !it.recent }.forEach { it.slots.filterNotNullTo(ids) }
    return ids
}

/**
 * A folder holds every game of its kind that is on Home. A game pinned on the root, or only
 * in collections, joins the end of its folder while the board has that folder, one taken off
 * Home included. A game already in any folder stays where it is, as an app the user moved
 * among the Android folders does. No folder is made and no tile is placed for this, and
 * nothing in a folder moves. A game whose source has not reported yet waits.
 */
private fun completeFolders(board: HomeBoard, catalog: List<HomeItem>, platforms: List<HomePlatform>): HomeBoard {
    if (board.hold != null) return board
    val unfiled = unfiledIds(board)
    if (unfiled.isEmpty()) return board
    val byId = catalog.associateBy { it.id }
    val filed = HashSet<String>()
    var next = board
    for (id in unfiled) {
        if (id in filed || id in board.hidden) continue
        val item = byId[id] ?: continue
        val folderId = systemFolderId(item, platforms) ?: continue
        val folder = next.folders[folderId]?.takeIf { it.system } ?: continue
        next = next.copy(folders = next.folders + (folderId to folder.copy(slots = atEnd(folder.slots, id))))
        filed += id
    }
    return next
}

/**
 * Games pinned on the root or in collections that no folder holds, in the root's order, then
 * collections off the root.
 */
private fun unfiledIds(board: HomeBoard): List<String> {
    val filed = HashSet<String>()
    board.folders.values.filter { it.system }.forEach { it.slots.filterNotNullTo(filed) }
    return buildList {
        for (slot in board.slots) {
            val folder = slot?.let { board.folders[it] }
            when {
                slot == null || slot == HOME_ALL -> Unit
                folder == null -> add(slot)
                folder.collection -> addAll(folder.slots.filterNotNull())
            }
        }
        board.folders.values.filter { it.collection && it.id !in board.slots }.sortedBy { it.id }
            .forEach { addAll(it.slots.filterNotNull()) }
    }.distinct().filter { it !in filed }
}

/** [id] after the last tile in [slots], in the empty slot there if there is one. */
private fun atEnd(slots: List<String?>, id: String): List<String?> {
    val after = slots.indexOfLast { it != null } + 1
    return if (after < slots.size) slots.mapIndexed { index, value -> if (index == after) id else value } else slots + id
}

/**
 * Recently played lists the games played last, newest first, leaving out hidden ones and
 * apps. A game whose source has not reported yet keeps its place. The collection joins the
 * root, in the first free slot, once a game is played after [HomeBoard.recentAfter], unless
 * the user took it off Home.
 */
private fun refreshRecent(board: HomeBoard, catalog: List<HomeItem>, unsettled: (String) -> Boolean): HomeBoard {
    if (board.hold != null) return board
    val played = catalog.filter {
        it.lastPlayedMillis > 0L && !it.placeholder && it.kind in gameKinds && it.id !in board.hidden
    }.sortedWith(compareByDescending<HomeItem> { it.lastPlayedMillis }.thenBy { it.id })
    val existing = board.folders[HOME_RECENT]
    if (existing == null) {
        if (HOME_RECENT in board.retired || played.none { it.lastPlayedMillis > board.recentAfter }) return board
        val folder = HomeFolder(HOME_RECENT, Copy.recentlyPlayed, mark = MARK_RECENT, slots = played.take(RECENT_COUNT).map { it.id })
        return placeOnRoot(board.copy(folders = board.folders + (HOME_RECENT to folder)), HOME_RECENT)
    }
    val listed = catalog.map { it.id }.toSet()
    val order = played.map { it.id }.toMutableList()
    existing.slots.forEachIndexed { index, id ->
        if (id != null && id !in listed && id !in board.hidden && unsettled(id)) order.add(index.coerceAtMost(order.size), id)
    }
    val slots: List<String?> = order.distinct().take(RECENT_COUNT)
    if (slots == existing.slots) return board
    return board.copy(folders = board.folders + (HOME_RECENT to existing.copy(slots = slots)))
}

/**
 * Once per emulator, one in the Android Games or Android Apps folder moves to Emulators.
 * Releases before the Emulators folder put emulators there, and so does a merge before
 * the app's player loaded: RetroArch says it is a game. Later moves are the user's.
 * Waits until the installed apps have reported, which is when an emulator is in [catalog].
 */
private fun fileEmulators(board: HomeBoard, catalog: List<HomeItem>, platforms: List<HomePlatform>): HomeBoard {
    if (board.hold != null) return board
    val emulators = catalog.filter { it.kind == HomeKind.Emulator && it.id !in board.filedEmulators }
    if (emulators.isEmpty()) return board
    var next = board
    for (item in emulators) {
        val from = listOf(HOME_ANDROID_GAMES, HOME_ANDROID_APPS).mapNotNull { next.folders[it] }
            .firstOrNull { item.id in it.slots } ?: continue
        val left = from.copy(slots = from.slots.map { if (it == item.id) null else it })
        next = placeNew(next.copy(folders = next.folders + (from.id to left)), item, platforms)
    }
    return next.copy(filedEmulators = next.filedEmulators + emulators.map { it.id })
}

/**
 * A folder Foldcade made, for a platform or a library, with nothing left in its slots,
 * leaves the grid. Its root slot stays empty like any other removed tile, so the tiles
 * after it keep their places. Empty slots left at the end of the grid are dropped.
 * A folder the user made, or any folder while a tile is held, stays. A game whose
 * source has not reported yet keeps its slot, so it keeps its folder too.
 */
private fun dropEmptySystemFolders(board: HomeBoard, waiting: Boolean): HomeBoard {
    // A game on Home but in no folder, whose source has not reported, may belong in an empty
    // folder. Until it is known, every folder stays, its name and slot with it.
    if (board.hold != null || waiting) return board
    val gone = board.folders.values.filter { folder ->
        !folder.userMade && (folder.platformId != null || folder.bucket != null) && folder.id !in board.retired && folder.slots.all { it == null }
    }.map { it.id }.toSet()
    if (gone.isEmpty()) return board
    val recoverable = board.hiddenSlots.values.flatten().mapNotNull { it.group }.toSet()
    val remembered = gone.filter { it in recoverable && it in board.slots }.associateWith { listOf(HiddenSlot(null, board.slots.indexOf(it))) }
    val slots = board.slots.map { if (it in gone) null else it }.dropLastWhile { it == null }
    return board.copy(
        hiddenSlots = board.hiddenSlots + remembered,
        slots = slots,
        folders = board.folders - (gone - recoverable),
        openFolderId = board.openFolderId?.takeIf { it !in gone },
    )
}

private fun specialFolders(): List<HomeFolder> = listOf(
    HomeFolder(HOME_ANDROID_GAMES, "Android Games", bucket = HomeKind.AndroidGame, mark = bucketMark(HomeKind.AndroidGame)),
    HomeFolder(HOME_ANDROID_APPS, "Android Apps", bucket = HomeKind.AndroidApp, mark = bucketMark(HomeKind.AndroidApp)),
    HomeFolder(HOME_GAMENATIVE, "GameNative", bucket = HomeKind.GameNative, mark = bucketMark(HomeKind.GameNative)),
    HomeFolder(HOME_MOONLIGHT, "Moonlight", bucket = HomeKind.Moonlight, mark = bucketMark(HomeKind.Moonlight)),
    HomeFolder(HOME_DROIDDECK, "DroidDeck", bucket = HomeKind.DroidDeck, mark = bucketMark(HomeKind.DroidDeck)),
    HomeFolder(HOME_EMULATORS, "Emulators", bucket = HomeKind.Emulator, mark = bucketMark(HomeKind.Emulator)),
)

private fun placeNew(board: HomeBoard, item: HomeItem, platforms: List<HomePlatform>, reviveRetired: Boolean = true): HomeBoard {
    return when (item.kind) {
        HomeKind.Rom -> {
            val platform = resolvePlatform(platforms, item.platformId)
                ?: item.platformId?.takeIf { it.isNotBlank() }?.let { raw ->
                    HomePlatform(id = raw, name = raw, mark = kitMark(raw))
                }
            if (platform == null) placeOnRoot(board, item.id) else placeInPlatform(board, item.id, platform, reviveRetired)
        }
        else -> bucketFolderId(item.kind)?.let { placeInBucket(board, item.id, it, reviveRetired) } ?: placeOnRoot(board, item.id)
    }
}

/** The library folder for [kind], or null for a ROM, whose folder is its platform's, and a loose tile. */
private fun bucketFolderId(kind: HomeKind): String? = when (kind) {
    HomeKind.AndroidGame -> HOME_ANDROID_GAMES
    HomeKind.AndroidApp -> HOME_ANDROID_APPS
    HomeKind.GameNative -> HOME_GAMENATIVE
    HomeKind.Moonlight -> HOME_MOONLIGHT
    HomeKind.DroidDeck -> HOME_DROIDDECK
    HomeKind.Emulator -> HOME_EMULATORS
    HomeKind.Rom, HomeKind.Loose -> null
}

private fun placeInPlatform(board: HomeBoard, itemId: String, platform: HomePlatform, reviveRetired: Boolean): HomeBoard {
    val folderId = platformFolderId(platform.id)
    val existing = board.folders[folderId]
    val folder = existing ?: HomeFolder(
        id = folderId,
        name = platform.name,
        platformId = platform.id,
        mark = platform.mark.ifBlank { kitMark(platform.id) },
    )
    var next = board.copy(
        folders = board.folders + (folderId to folder),
        retired = if (reviveRetired) board.retired - folderId else board.retired,
    )
    // An automatically emptied folder returns with a new game; an explicit removal waits for Show.
    if (folderId !in next.slots && (folderId !in board.retired || reviveRetired)) {
        next = placeOnRoot(next, folderId).copy(hiddenSlots = next.hiddenSlots - folderId)
    }
    return dropInto(next, folderId, itemId)
}

private fun placeInBucket(board: HomeBoard, itemId: String, folderId: String, reviveRetired: Boolean): HomeBoard {
    val folder = board.folders[folderId] ?: specialFolders().first { it.id == folderId }
    var next = board.copy(
        folders = board.folders + (folderId to folder),
        retired = if (reviveRetired) board.retired - folderId else board.retired,
    )
    if (folderId !in next.slots && (folderId !in board.retired || reviveRetired)) {
        next = placeOnRoot(next, folderId).copy(hiddenSlots = next.hiddenSlots - folderId)
    }
    return dropInto(next, folderId, itemId)
}

private fun placeOnRoot(board: HomeBoard, id: String): HomeBoard {
    if (board.slots.contains(id)) return board.copy(offHome = board.offHome - id)
    val hole = board.slots.indexOfFirst { it == null }
    val slots = if (hole >= 0) {
        board.slots.mapIndexed { index, value -> if (index == hole) id else value }
    } else {
        board.slots + id
    }
    return board.copy(slots = slots, offHome = board.offHome - id)
}

private fun dropInto(board: HomeBoard, folderId: String, id: String): HomeBoard {
    val folder = board.folders[folderId] ?: return placeOnRoot(board, id)
    if (folder.slots.contains(id)) return board.copy(offHome = board.offHome - id)
    val hole = folder.slots.indexOfFirst { it == null }
    val slots = if (hole >= 0) {
        folder.slots.mapIndexed { index, value -> if (index == hole) id else value }
    } else {
        folder.slots + id
    }
    return board.copy(folders = board.folders + (folderId to folder.copy(slots = slots)), offHome = board.offHome - id)
}

/**
 * Names system folders after their platform and redraws every folder's mark.
 * A saved board keeps the marks of the release that wrote it, so marks are not trusted.
 * Favorites and Recently played keep Foldcade's names until the user renames them.
 */
private fun refreshFolderNames(board: HomeBoard, platforms: List<HomePlatform>): HomeBoard {
    val folders = board.folders.mapValues { (_, folder) ->
        val platformId = folder.platformId
        val platform = platformId?.let { id -> platforms.firstOrNull { it.id == id } }
        val mark = when {
            platformId != null -> platform?.mark?.ifBlank { null } ?: kitMark(platformId)
            folder.bucket != null -> bucketMark(folder.bucket)
            folder.recent -> MARK_RECENT
            // A collection keeps the icon the user chose.
            folder.userMade -> folder.mark?.takeIf { it in FOLDER_ICONS } ?: MARK_FOLDER
            else -> MARK_FOLDER
        }
        val name = when {
            folder.namedByUser -> folder.name
            folder.recent -> Copy.recentlyPlayed
            folder.id == HOME_FAVORITES -> Copy.favorites
            platform != null -> platform.name
            else -> folder.name
        }
        folder.copy(name = name, mark = mark)
    }
    return board.copy(folders = folders)
}

private fun writeVisible(board: HomeBoard, slots: List<String?>): HomeBoard {
    val folderId = board.openFolderId
    if (folderId == null) return board.copy(slots = slots)
    val folder = board.folders[folderId] ?: return board
    return board.copy(folders = board.folders + (folderId to folder.copy(slots = slots)))
}

private fun decodeSlots(bits: List<String>, encoded: Boolean): List<String?> =
    if (encoded) bits.map { it.ifEmpty { null } }
    else bits.filter { it.isNotEmpty() }.map { if (it == "-") null else it }

fun letterGroup(title: String): String =
    title.firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"

/**
 * Where an app from a shelf can go: a pin on Home first, then the folders that take it and
 * the collections. Places it already is are left out, so every choice does something.
 */
fun placementChoices(board: HomeBoard, item: HomeItem?, platforms: List<HomePlatform>): List<FolderChoice> {
    val id = item?.id
    val home = if (id == null || !pinned(board, id)) listOf(FolderChoice(null, Copy.homeSlot)) else emptyList()
    return home + board.slots.mapNotNull { board.folders[it] }.distinctBy { it.id }
        .filter { (id == null || id !in it.slots) && folderTakes(it, item, platforms) }
        .map { FolderChoice(it.id, it.name) }
}

/** Hidden games and retired groups, alphabetically, without duplicate Android entries. */
fun hiddenItems(board: HomeBoard, catalog: List<HomeItem>, apps: List<LaunchableApp>, state: AppShelfState, players: Set<String>, platforms: List<HomePlatform> = emptyList()): List<HomeItem> {
    val games = catalog.filter { it.id in board.hidden }
    val installed = hiddenApps(apps, state, players).map { HomeItem(androidHomeId(it.packageName), it.label, HomeKind.AndroidApp) }
    val groups = board.retired.mapNotNull { id ->
        retiredFolder(board, id, catalog, platforms)?.let { HomeItem(id, it.name, HomeKind.Loose, mark = it.mark) }
    }
    return (games + installed + groups).distinctBy { it.id }.sortedWith(compareBy<HomeItem> { it.title.lowercase() }.thenBy { it.id })
}

private fun restoredSlot(slots: List<String?>, id: String, previous: Int, root: Boolean): List<String?> {
    if (id in slots) return slots
    val at = if (previous >= 0 && slots.getOrNull(previous) == null) previous
        else if (root) slots.indexOfFirst { it == null }.takeIf { it >= 0 } ?: slots.size
        else slots.indexOfLast { it != null } + 1
    val next = slots.toMutableList()
    while (next.size <= at) next += null
    next[at] = id
    return next
}

private fun restoreHiddenSlots(board: HomeBoard, id: String): HomeBoard {
    var next = board
    for (place in board.hiddenSlots[id].orEmpty()) {
        val group = place.group
        if (group == null) next = next.copy(slots = restoredSlot(next.slots, id, place.index, root = true))
        else next.folders[group]?.let { folder ->
            next = next.copy(folders = next.folders + (group to folder.copy(slots = restoredSlot(folder.slots, id, place.index, root = false))))
            if (folder.system && group !in next.retired && group !in next.slots) {
                next = next.copy(slots = restoredSlot(next.slots, group, next.hiddenSlots[group]?.firstOrNull()?.index ?: -1, root = true), hiddenSlots = next.hiddenSlots - group)
            }
        }
    }
    return next.copy(hiddenSlots = next.hiddenSlots - id)
}

private fun retiredFolder(board: HomeBoard, id: String, catalog: List<HomeItem>, platforms: List<HomePlatform>): HomeFolder? {
    board.folders[id]?.let { return it }
    if (id == HOME_RECENT) return HomeFolder(id, Copy.recentlyPlayed, mark = MARK_RECENT)
    specialFolders().firstOrNull { it.id == id }?.let { return it }
    val platform = platforms.firstOrNull { platformFolderId(it.id) == id }
    if (platform != null) return HomeFolder(id, platform.name, platformId = platform.id, mark = kitMark(platform.id))
    val item = catalog.firstOrNull { systemFolderId(it, platforms) == id }
    val platformId = item?.let { romPlatformId(it, platforms) }
        ?: id.removePrefix("home.platform.").takeIf { it != id && it.isNotBlank() } ?: return null
    return HomeFolder(id, platformId, platformId = platformId, mark = kitMark(platformId))
}

/** Show restores a game's old places, or a retired folder's old root slot when free. */
fun showHidden(board: HomeBoard, id: String, catalog: List<HomeItem>, platforms: List<HomePlatform>): HomeBoard {
    if (id in board.retired) {
        val restored = retiredFolder(board, id, catalog, platforms) ?: return board
        val folder = if (id in board.folders || restored.recent) restored else restored.copy(
            slots = catalog.filter { it.id !in board.hidden && systemFolderId(it, platforms) == id }.map { it.id },
        )
        val previous = board.hiddenSlots[id]?.firstOrNull()?.index ?: -1
        val next = board.copy(folders = board.folders + (id to folder), retired = board.retired - id,
            slots = restoredSlot(board.slots, id, previous, root = true), hiddenSlots = board.hiddenSlots - id)
        return next
    }
    val item = catalog.firstOrNull { it.id == id } ?: return board
    val shown = admit(board, id)
    return if (placedIds(shown).contains(id)) shown else addItemToHome(shown, item, platforms)
}

/** A shortcut deleted from Android cannot be recovered through Hidden or a stale scan. */
fun deleteShortcut(board: HomeBoard, id: String): HomeBoard {
    val gone = hideGame(board, id)
    return gone.copy(hidden = gone.hidden - id, hiddenSlots = gone.hiddenSlots - id, offHome = gone.offHome + id)
}

/** Classification can move an app into a folder that has never been created. */
fun ensureAppFolder(board: HomeBoard, id: String): HomeBoard {
    if (id in board.folders) return board
    val spec = specialFolders().firstOrNull { it.id == id && it.bucket in INSTALLED_KINDS } ?: return board
    return placeOnRoot(board.copy(folders = board.folders + (id to spec), retired = board.retired - id), id)
}
