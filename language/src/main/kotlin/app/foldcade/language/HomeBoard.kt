package app.foldcade.language

import java.util.Base64

/**
 * Curated home grid. Slots are positions. A null slot is empty.
 * Item ids are catalog keys. Removing a tile hides it here and does not delete the game.
 * The All tile stays on the grid.
 */
const val HOME_ALL: String = "home.all"

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

data class HomeFolder(
    val id: String,
    val name: String,
    val platformId: String? = null,
    val bucket: HomeKind? = null,
    val mark: String? = null,
    val userMade: Boolean = false,
    val namedByUser: Boolean = false,
    val slots: List<String?> = emptyList(),
)

data class HomeHold(
    val index: Int,
    val slots: List<String?>,
    val folders: Map<String, HomeFolder>,
    val openFolderId: String?,
    /** Where the tile was picked up. Cancel puts it back here. */
    val origin: Int = index,
)

data class HomeBoard(
    val slots: List<String?> = listOf(HOME_ALL),
    val folders: Map<String, HomeFolder> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    /** System folders the user deleted. A later new game may recreate one. */
    val retired: Set<String> = emptySet(),
    val addNewToHome: Boolean = true,
    val openFolderId: String? = null,
    val allOpen: Boolean = false,
    val editing: Boolean = false,
    val hold: HomeHold? = null,
    /** Emulators an older release filed with the Android apps have moved to Emulators. */
    val emulatorsFiled: Boolean = false,
)

fun homeGameId(backendId: String, remoteKey: String): String = "lib:$backendId:$remoteKey"

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

/**
 * The icons a folder the user made can wear, in the picker's order: the
 * folder, the libraries, then every platform.
 */
val FOLDER_ICONS: List<String> =
    listOf(MARK_FOLDER, MARK_LIBRARY, "android-games", "android-apps", "pc", "moonlight", "droiddeck", "emulators", MARK_CONSOLE) +
        PLATFORM_MARKS

/** Gives a folder the user made the icon [mark]. A system folder keeps its platform's. */
fun setFolderIcon(board: HomeBoard, folderId: String, mark: String): HomeBoard {
    val folder = board.folders[folderId] ?: return board
    if (!folder.userMade || mark !in FOLDER_ICONS) return board
    return board.copy(folders = board.folders + (folderId to folder.copy(mark = mark)))
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

fun visibleSlots(board: HomeBoard): List<String?> {
    val folderId = board.openFolderId
    if (folderId == null) return board.slots
    return board.folders[folderId]?.slots ?: emptyList()
}

fun onHome(board: HomeBoard, id: String): Boolean {
    if (id == HOME_ALL) return true
    if (id in board.hidden) return false
    if (board.slots.contains(id)) return true
    return board.folders.values.any { folder -> folder.slots.contains(id) }
}

fun folderCount(board: HomeBoard, folderId: String): Int =
    board.folders[folderId]?.slots?.count { it != null } ?: 0

/**
 * Merges [items] into [board]. Placed tiles stay put. A missing catalog id leaves an empty slot.
 * New items go into their system folder when [HomeBoard.addNewToHome] is on, otherwise they stay off the grid.
 * Hidden tiles stay hidden. Games are not deleted.
 *
 * [unsettled] is true for an id whose source has not reported yet, such as a library still
 * scanning after launch. Its slot is kept rather than read as a game that went away.
 *
 * No games, no folder. A folder Foldcade made, for a platform or a library, appears with its
 * first game and goes away once none is left in it. Emulators have a folder of their own.
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
    var next = board.copy(
        slots = stripUnknown(board.slots, keep, board.folders),
        folders = board.folders.mapValues { (_, folder) ->
            folder.copy(slots = stripUnknown(folder.slots, keep, board.folders))
        },
        hidden = board.hidden.filter(keep).toSet(),
    )
    next = fileEmulators(next, catalog, platforms)
    for (item in catalog) {
        if (item.id in next.hidden) continue
        if (onHome(next, item.id)) continue
        if (!next.addNewToHome) continue
        next = placeNew(next, item, platforms)
    }
    next = dropEmptySystemFolders(next)
    next = refreshFolderNames(next, platforms)
    if (HOME_ALL !in next.slots) {
        next = next.copy(slots = listOf(HOME_ALL) + next.slots)
    }
    return next
}

/**
 * Lifts the tile at [index], or the empty slot there. A lifted gap walks the way a tile
 * does, so carrying one onto a tile brings that tile back to where the gap was.
 */
fun pickUp(board: HomeBoard, index: Int): HomeBoard {
    if (index < 0) return board
    if (board.hold != null) return drop(board, index)
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
 * Onto a folder that takes it, a game drops in. Onto one that does not, it trades places.
 * A held gap trades places too, and never drops into a folder.
 */
fun moveHeld(board: HomeBoard, toIndex: Int, catalog: List<HomeItem>, platforms: List<HomePlatform>): HomeBoard {
    val hold = board.hold ?: return board
    if (toIndex < 0) return board
    val before = hold.openFolderId?.let { hold.folders[it]?.slots } ?: hold.slots
    val moving = before.getOrNull(hold.origin)
    val slots = before.toMutableList()
    while (slots.size <= maxOf(toIndex, hold.origin)) slots += null
    val target = slots[toIndex]
    val targetFolder = target?.let { board.folders[it] }
    if (moving != null && targetFolder != null && moving != HOME_ALL && board.folders[moving] == null &&
        folderTakes(targetFolder, catalog.firstOrNull { it.id == moving }, platforms)
    ) {
        slots[hold.origin] = null
        return dropInto(writeVisible(board, slots), targetFolder.id, moving).copy(hold = null)
    }
    slots[hold.origin] = target
    slots[toIndex] = moving
    // A gap walked past the last tile leaves no empty slots behind it.
    while (slots.size > before.size && slots.last() == null) slots.removeAt(slots.lastIndex)
    return writeVisible(board, slots).copy(hold = hold.copy(index = toIndex))
}

/**
 * Whether [item] may go in [folder]. A folder the user made takes anything. A platform's
 * folder takes only that platform's games, and a library's folder only that library's.
 * The installed Android folders trade among themselves, so an app filed as the wrong
 * kind can be moved where it belongs. An item the catalog does not know goes only in a
 * folder the user made.
 */
fun folderTakes(folder: HomeFolder, item: HomeItem?, platforms: List<HomePlatform>): Boolean {
    val platformId = folder.platformId
    val bucket = folder.bucket
    if (folder.userMade || (platformId == null && bucket == null)) return true
    if (item == null) return false
    if (platformId != null) return item.kind == HomeKind.Rom && romPlatformId(item, platforms) == platformId
    return item.kind == bucket || (bucket in INSTALLED_KINDS && item.kind in INSTALLED_KINDS)
}

private val INSTALLED_KINDS: Set<HomeKind> = setOf(HomeKind.AndroidGame, HomeKind.AndroidApp, HomeKind.Emulator)

fun drop(board: HomeBoard, index: Int): HomeBoard {
    if (board.hold == null) return board
    return board.copy(hold = null)
}

fun cancelHold(board: HomeBoard): HomeBoard {
    val hold = board.hold ?: return board
    return board.copy(
        slots = hold.slots,
        folders = hold.folders,
        openFolderId = hold.openFolderId,
        hold = null,
    )
}

/** Hides a game or app tile. The All tile stays. A folder spills its contents instead. */
fun removeFromHome(board: HomeBoard, index: Int): HomeBoard {
    val slots = visibleSlots(board)
    val id = slots.getOrNull(index) ?: return board
    if (id == HOME_ALL) return board
    if (board.folders[id] != null) return deleteFolder(board, id)
    val cleared = slots.mapIndexed { slot, value -> if (slot == index) null else value }
    return writeVisible(board.copy(hidden = board.hidden + id, hold = null), cleared)
}

fun deleteFolder(board: HomeBoard, folderId: String): HomeBoard {
    if (folderId == HOME_ALL) return board
    val folder = board.folders[folderId] ?: return board
    val children = folder.slots.filterNotNull()
    var slots = board.slots.map { if (it == folderId) null else it }
    var folders = board.folders - folderId
    for (child in children) {
        if (child == HOME_ALL) continue
        val hole = slots.indexOfFirst { it == null }
        slots = if (hole >= 0) {
            slots.mapIndexed { index, value -> if (index == hole) child else value }
        } else {
            slots + child
        }
    }
    val open = if (board.openFolderId == folderId) null else board.openFolderId
    return board.copy(
        slots = slots,
        folders = folders,
        retired = board.retired + folderId,
        openFolderId = open,
        hold = null,
    )
}

fun createFolder(board: HomeBoard, id: String, name: String): HomeBoard {
    if (id in board.folders || id == HOME_ALL) return board
    val folder = HomeFolder(id = id, name = name, userMade = true, mark = MARK_FOLDER)
    val slots = board.slots
    val hole = slots.indexOfFirst { it == null }
    val placed = if (hole >= 0) {
        slots.mapIndexed { index, value -> if (index == hole) id else value }
    } else {
        slots + id
    }
    return board.copy(slots = placed, folders = board.folders + (id to folder))
}

/**
 * A new folder the user made, holding the game at [index] and standing in its place.
 * Folders do not nest, so inside a folder the new one goes on the root. With no game
 * at [index], the folder is empty and takes the first free root slot.
 */
fun folderAround(board: HomeBoard, index: Int, folderId: String, name: String): HomeBoard {
    val id = visibleSlots(board).getOrNull(index)
    val game = id?.takeIf { it != HOME_ALL && board.folders[it] == null } ?: return createFolder(board, folderId, name)
    if (folderId in board.folders || folderId == HOME_ALL) return board
    val folder = HomeFolder(id = folderId, name = name, userMade = true, mark = MARK_FOLDER, slots = listOf(game))
    val made = board.copy(folders = board.folders + (folderId to folder), hold = null)
    if (board.openFolderId == null) return made.copy(slots = board.slots.map { if (it == game) folderId else it })
    val left = writeVisible(made, visibleSlots(board).map { if (it == game) null else it })
    return placeOnRoot(left, folderId)
}

/**
 * Moves the game [id] into [folderId], or onto the first free root slot when [folderId]
 * is null. Its old slot is left empty. Folders and the All tile do not move this way,
 * and a game does not go in a folder that does not take it.
 */
fun moveToFolder(
    board: HomeBoard,
    id: String,
    folderId: String?,
    catalog: List<HomeItem>,
    platforms: List<HomePlatform>,
): HomeBoard {
    if (id == HOME_ALL || board.folders[id] != null) return board
    if (folderId != null) {
        val folder = board.folders[folderId] ?: return board
        if (!folderTakes(folder, catalog.firstOrNull { it.id == id }, platforms)) return board
    }
    val lifted = board.copy(
        slots = board.slots.map { if (it == id) null else it },
        folders = board.folders.mapValues { (_, folder) ->
            folder.copy(slots = folder.slots.map { if (it == id) null else it })
        },
        hold = null,
    )
    return if (folderId != null) dropInto(lifted, folderId, id) else placeOnRoot(lifted, id)
}

fun renameFolder(board: HomeBoard, folderId: String, name: String): HomeBoard {
    val folder = board.folders[folderId] ?: return board
    val cleaned = name.replace('\t', ' ').replace('\n', ' ').trim()
    if (cleaned.isEmpty()) return board
    val renamed = folder.copy(name = cleaned, namedByUser = true)
    return board.copy(folders = board.folders + (folderId to renamed))
}

fun nextUserFolderId(board: HomeBoard): String {
    val taken = board.folders.keys.mapNotNull { id ->
        id.removePrefix("home.user.").toIntOrNull()
    }.maxOrNull() ?: 0
    return "home.user.${taken + 1}"
}

/**
 * Places [id] in [folderId], or the first free root slot when [folderId] is null.
 * Already-placed ids stay. Hidden ids come back.
 */
fun addToHome(board: HomeBoard, id: String, folderId: String? = null): HomeBoard {
    if (id == HOME_ALL || id.isBlank()) return board
    val shown = board.copy(hidden = board.hidden - id)
    if (onHome(shown, id)) return shown
    val folder = folderId?.let { shown.folders[it] }
    return if (folder != null) dropInto(shown, folder.id, id) else placeOnRoot(shown, id)
}

/**
 * The folder [item] is filed in, while the board still has it: its platform's folder
 * for a ROM, or its library's folder for an app or stream. Null when that folder is
 * gone or the item has none, so a game brought back from All lands where it came from.
 */
fun homeFolderFor(board: HomeBoard, item: HomeItem, platforms: List<HomePlatform>): String? {
    val folderId = when (item.kind) {
        HomeKind.Rom -> romPlatformId(item, platforms)?.let(::platformFolderId)
        else -> bucketFolderId(item.kind)
    }
    return folderId?.takeIf { it in board.folders }
}

/** The canonical platform of a ROM, or its raw platform id when no platform knows it. */
private fun romPlatformId(item: HomeItem, platforms: List<HomePlatform>): String? =
    resolvePlatform(platforms, item.platformId)?.id ?: item.platformId?.takeIf { it.isNotBlank() }

/**
 * Puts [item] back on Home, unhidden, where a new one would go. A system folder that
 * went away with its last game comes back with this one.
 */
fun addItemToHome(board: HomeBoard, item: HomeItem, platforms: List<HomePlatform>): HomeBoard {
    if (item.id == HOME_ALL || item.id.isBlank()) return board
    val shown = board.copy(hidden = board.hidden - item.id)
    if (onHome(shown, item.id)) return shown
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

/** The All library: every game, A to Z. */
fun allItems(items: List<HomeItem>): List<HomeItem> = items.distinctBy { it.id }
    .filter { !it.placeholder && it.kind in gameKinds }
    .sortedWith(compareBy({ it.title.lowercase() }, { it.id }))

/** Next letter group. Forward lands on its first row. Backward lands on the previous group's first row. Stays put at the ends. Does not wrap. */
fun jumpLetter(labels: List<String>, index: Int, forward: Boolean): Int {
    if (labels.isEmpty()) return 0
    val current = index.coerceIn(0, labels.lastIndex)
    val letter = labels[current].firstOrNull()?.uppercaseChar()
    if (forward) {
        for (step in (current + 1)..labels.lastIndex) {
            if (labels[step].firstOrNull()?.uppercaseChar() != letter) return step
        }
        return current
    }
    val groupStart = (current downTo 0).first { step ->
        step == 0 || labels[step - 1].firstOrNull()?.uppercaseChar() != letter
    }
    if (groupStart == 0) return current
    val previous = labels[groupStart - 1].firstOrNull()?.uppercaseChar()
    return (groupStart - 1 downTo 0).first { step ->
        step == 0 || labels[step - 1].firstOrNull()?.uppercaseChar() != previous
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
        homeRow("emulatorsFiled", listOf(if (saved.emulatorsFiled) "1" else "0"))
        for (folder in saved.folders.values.sortedBy { it.id }) {
            homeRow(
                "folder",
                listOf(
                    folder.id, folder.name, folder.platformId.orEmpty(), folder.bucket?.name.orEmpty(),
                    if (folder.userMade) "1" else "0", if (folder.namedByUser) "1" else "0", folder.mark.orEmpty(),
                ) + folder.slots.map { it.orEmpty() },
            )
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

fun decodeHome(raw: String?): HomeBoard {
    if (raw.isNullOrBlank()) return HomeBoard()
    val encoded = raw.lineSequence().firstOrNull() == "v2"
    val decoder = Base64.getUrlDecoder()
    var board = HomeBoard(slots = emptyList())
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
            "emulatorsFiled" -> board = board.copy(emulatorsFiled = bits.getOrNull(1) == "1")
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
    if (board.slots.isEmpty()) board = board.copy(slots = listOf(HOME_ALL))
    if (HOME_ALL !in board.slots) board = board.copy(slots = listOf(HOME_ALL) + board.slots)
    return refreshFolderNames(board, emptyList())
}

private fun stripUnknown(
    slots: List<String?>,
    keep: (String) -> Boolean,
    folders: Map<String, HomeFolder>,
): List<String?> = slots.map { id ->
    when {
        id == null -> null
        id == HOME_ALL -> id
        id in folders -> id
        keep(id) -> id
        else -> null
    }
}

/**
 * Once, an emulator still in the Android Games or Android Apps folder, where releases
 * before the Emulators folder put it, moves to Emulators. Later moves are the user's.
 * Waits until the installed apps have reported, which is when an emulator is in [catalog].
 */
private fun fileEmulators(board: HomeBoard, catalog: List<HomeItem>, platforms: List<HomePlatform>): HomeBoard {
    if (board.emulatorsFiled || board.hold != null) return board
    val emulators = catalog.filter { it.kind == HomeKind.Emulator }
    if (emulators.isEmpty()) return board
    var next = board
    for (item in emulators) {
        val from = listOf(HOME_ANDROID_GAMES, HOME_ANDROID_APPS).mapNotNull { next.folders[it] }
            .firstOrNull { item.id in it.slots } ?: continue
        val left = from.copy(slots = from.slots.map { if (it == item.id) null else it })
        next = placeNew(next.copy(folders = next.folders + (from.id to left)), item, platforms)
    }
    return next.copy(emulatorsFiled = true)
}

/**
 * A folder Foldcade made, for a platform or a library, with nothing left in its slots,
 * leaves the grid. Its root slot stays empty like any other removed tile, so the tiles
 * after it keep their places. Empty slots left at the end of the grid are dropped.
 * A folder the user made, or any folder while a tile is held, stays. A game whose
 * source has not reported yet keeps its slot, so it keeps its folder too.
 */
private fun dropEmptySystemFolders(board: HomeBoard): HomeBoard {
    if (board.hold != null) return board
    val gone = board.folders.values.filter { folder ->
        !folder.userMade && (folder.platformId != null || folder.bucket != null) && folder.slots.all { it == null }
    }.map { it.id }.toSet()
    if (gone.isEmpty()) return board
    val slots = board.slots.map { if (it in gone) null else it }.dropLastWhile { it == null }
    return board.copy(
        slots = slots,
        folders = board.folders - gone,
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

private fun placeNew(board: HomeBoard, item: HomeItem, platforms: List<HomePlatform>): HomeBoard {
    return when (item.kind) {
        HomeKind.Rom -> {
            val platform = resolvePlatform(platforms, item.platformId)
                ?: item.platformId?.takeIf { it.isNotBlank() }?.let { raw ->
                    HomePlatform(id = raw, name = raw, mark = kitMark(raw))
                }
            if (platform == null) placeOnRoot(board, item.id) else placeInPlatform(board, item.id, platform)
        }
        else -> bucketFolderId(item.kind)?.let { placeInBucket(board, item.id, it) } ?: placeOnRoot(board, item.id)
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

private fun placeInPlatform(board: HomeBoard, itemId: String, platform: HomePlatform): HomeBoard {
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
        retired = board.retired - folderId,
    )
    if (existing == null && folderId !in next.slots) {
        next = placeOnRoot(next, folderId)
    }
    return dropInto(next, folderId, itemId)
}

private fun placeInBucket(board: HomeBoard, itemId: String, folderId: String): HomeBoard {
    if (board.folders[folderId] == null) {
        val spec = specialFolders().first { it.id == folderId }
        var next = board.copy(
            folders = board.folders + (folderId to spec),
            retired = board.retired - folderId,
        )
        if (folderId !in next.slots) next = placeOnRoot(next, folderId)
        return dropInto(next, folderId, itemId)
    }
    return dropInto(board.copy(retired = board.retired - folderId), folderId, itemId)
}

private fun placeOnRoot(board: HomeBoard, id: String): HomeBoard {
    if (board.slots.contains(id)) return board
    val hole = board.slots.indexOfFirst { it == null }
    val slots = if (hole >= 0) {
        board.slots.mapIndexed { index, value -> if (index == hole) id else value }
    } else {
        board.slots + id
    }
    return board.copy(slots = slots)
}

private fun dropInto(board: HomeBoard, folderId: String, id: String): HomeBoard {
    val folder = board.folders[folderId] ?: return placeOnRoot(board, id)
    if (folder.slots.contains(id)) return board
    val hole = folder.slots.indexOfFirst { it == null }
    val slots = if (hole >= 0) {
        folder.slots.mapIndexed { index, value -> if (index == hole) id else value }
    } else {
        folder.slots + id
    }
    return board.copy(folders = board.folders + (folderId to folder.copy(slots = slots)))
}

/**
 * Names system folders after their platform and redraws every folder's mark.
 * A saved board keeps the marks of the release that wrote it, so marks are not trusted.
 */
private fun refreshFolderNames(board: HomeBoard, platforms: List<HomePlatform>): HomeBoard {
    val folders = board.folders.mapValues { (_, folder) ->
        val platformId = folder.platformId
        val platform = platformId?.let { id -> platforms.firstOrNull { it.id == id } }
        val mark = when {
            platformId != null -> platform?.mark?.ifBlank { null } ?: kitMark(platformId)
            folder.bucket != null -> bucketMark(folder.bucket)
            // A folder the user made keeps the icon they chose.
            folder.userMade -> folder.mark?.takeIf { it in FOLDER_ICONS } ?: MARK_FOLDER
            else -> MARK_FOLDER
        }
        val name = if (platform == null || folder.namedByUser) folder.name else platform.name
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
