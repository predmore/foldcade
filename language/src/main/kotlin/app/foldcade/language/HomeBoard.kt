package app.foldcade.language

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

enum class HomeKind {
    Rom,
    AndroidGame,
    AndroidApp,
    GameNative,
    Moonlight,
    Loose,
}

enum class AllTab {
    Games,
    Apps,
}

enum class AllSort {
    Title,
    RecentlyPlayed,
    System,
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
    val allTab: AllTab = AllTab.Games,
    val allSort: AllSort = AllSort.Title,
    val allSystemId: String? = null,
    val destinationFolderId: String? = null,
    /** 0 tab, 1 sort, 2 system, 3 destination. Null is the grid. */
    val allChrome: Int? = null,
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

/** Kit mark for a canonical platform id. Not a console logo. A platform without its own mark gets a controller. */
fun kitMark(canonicalId: String): String =
    canonicalId.takeIf { it in PLATFORM_MARKS } ?: MARK_CONSOLE

fun bucketMark(kind: HomeKind): String = when (kind) {
    HomeKind.AndroidGame -> "android-games"
    HomeKind.AndroidApp -> "android-apps"
    HomeKind.GameNative -> "pc"
    HomeKind.Moonlight -> "moonlight"
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
 * A platform folder Foldcade made goes away once no game for that platform is left,
 * and comes back with the next game for it.
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
    for (item in catalog) {
        if (item.id in next.hidden) continue
        if (onHome(next, item.id)) continue
        if (!next.addNewToHome) continue
        next = placeNew(next, item, platforms)
    }
    next = ensureSpecialFolders(next)
    next = dropGonePlatformFolders(next, catalog, platforms)
    next = refreshFolderNames(next, platforms)
    if (HOME_ALL !in next.slots) {
        next = next.copy(slots = listOf(HOME_ALL) + next.slots)
    }
    return next
}

fun pickUp(board: HomeBoard, index: Int): HomeBoard {
    val id = visibleSlots(board).getOrNull(index) ?: return board
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

fun moveHeld(board: HomeBoard, toIndex: Int): HomeBoard {
    val hold = board.hold ?: return board
    val slots = visibleSlots(board).toMutableList()
    val from = hold.index
    if (from !in slots.indices) return board
    if (toIndex < 0) return board
    while (slots.size <= toIndex) slots += null
    if (from == toIndex) return writeVisible(board, slots)
    val moving = slots[from]
    val target = slots[toIndex]
    if (moving == null) return board
    val targetFolder = target?.let { board.folders[it] }
    if (targetFolder != null && moving != HOME_ALL && board.folders[moving] == null) {
        slots[from] = null
        val written = writeVisible(board, slots)
        val filled = dropInto(written, targetFolder.id, moving)
        return filled.copy(hold = null)
    }
    slots[from] = target
    slots[toIndex] = moving
    return writeVisible(board, slots).copy(hold = hold.copy(index = toIndex))
}

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

fun setAddNewToHome(board: HomeBoard, enabled: Boolean): HomeBoard =
    board.copy(addNewToHome = enabled)

fun openFolder(board: HomeBoard, folderId: String): HomeBoard {
    if (board.folders[folderId] == null) return board
    return board.copy(openFolderId = folderId, allOpen = false, hold = null, allChrome = null)
}

fun closeFolder(board: HomeBoard): HomeBoard = board.copy(openFolderId = null, hold = null)

fun openAll(board: HomeBoard): HomeBoard =
    board.copy(allOpen = true, openFolderId = null, hold = null, editing = false, allChrome = null)

fun closeAll(board: HomeBoard): HomeBoard = board.copy(allOpen = false, allChrome = null)

fun enterEdit(board: HomeBoard): HomeBoard =
    board.copy(editing = true, allOpen = false, hold = null, allChrome = null)

fun leaveEdit(board: HomeBoard): HomeBoard = cancelHold(board).copy(editing = false)

enum class AllChromeControl {
    Tab,
    Sort,
    System,
    Destination,
}

fun allItems(
    items: List<HomeItem>,
    platforms: List<HomePlatform>,
    tab: AllTab,
    sort: AllSort,
    systemId: String?,
): List<HomeItem> {
    val filtered = items.distinctBy { it.id }.filter { item ->
        val tabOk = when (tab) {
            AllTab.Games -> item.kind == HomeKind.Rom
            AllTab.Apps -> item.kind == HomeKind.AndroidGame ||
                item.kind == HomeKind.AndroidApp ||
                item.kind == HomeKind.GameNative ||
                item.kind == HomeKind.Moonlight
        }
        if (!tabOk) return@filter false
        if (systemId == null || tab != AllTab.Games) return@filter true
        val platform = resolvePlatform(platforms, item.platformId)
        platform?.id == systemId
    }
    return when (sort) {
        AllSort.Title -> filtered.sortedWith(compareBy({ it.title.lowercase() }, { it.id }))
        AllSort.RecentlyPlayed -> filtered.sortedWith(
            compareByDescending<HomeItem> { it.lastPlayedMillis }.thenBy { it.title.lowercase() }.thenBy { it.id },
        )
        AllSort.System -> filtered.sortedWith(
            compareBy({ systemName(platforms, it) }, { it.title.lowercase() }, { it.id }),
        )
    }
}

fun sectionLabel(item: HomeItem, platforms: List<HomePlatform>, sort: AllSort): String? =
    if (sort == AllSort.System) systemName(platforms, item) else null

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

fun allLabels(
    items: List<HomeItem>,
    platforms: List<HomePlatform>,
    sort: AllSort,
): List<String> = items.map { item ->
    if (sort == AllSort.System) systemName(platforms, item) else item.title
}

fun cycleAllTab(board: HomeBoard): HomeBoard {
    val tab = if (board.allTab == AllTab.Games) AllTab.Apps else AllTab.Games
    return board.copy(allTab = tab, allSystemId = null)
}

fun cycleAllSort(board: HomeBoard): HomeBoard {
    val sort = when (board.allSort) {
        AllSort.Title -> AllSort.RecentlyPlayed
        AllSort.RecentlyPlayed -> AllSort.System
        AllSort.System -> AllSort.Title
    }
    return board.copy(allSort = sort)
}

fun cycleSystemFilter(board: HomeBoard, platforms: List<HomePlatform>, items: List<HomeItem>): HomeBoard {
    val systems = items.mapNotNull { resolvePlatform(platforms, it.platformId)?.id }.distinct()
    if (systems.isEmpty()) return board.copy(allSystemId = null)
    val current = board.allSystemId
    val next = if (current == null) {
        systems.first()
    } else {
        val index = systems.indexOf(current)
        if (index < 0 || index == systems.lastIndex) null else systems[index + 1]
    }
    return board.copy(allSystemId = next)
}

fun cycleDestination(board: HomeBoard): HomeBoard {
    val ids = listOf<String?>(null) + board.folders.keys.filter { it != HOME_ALL }
    if (ids.size <= 1) return board.copy(destinationFolderId = null)
    val index = ids.indexOf(board.destinationFolderId).let { if (it < 0) 0 else it }
    val next = ids[(index + 1) % ids.size]
    return board.copy(destinationFolderId = next)
}

fun encodeHome(board: HomeBoard): String = buildString {
    append("v1\n")
    append("addNew\t").append(if (board.addNewToHome) "1" else "0").append('\n')
    append("hidden\t").append(board.hidden.joinToString("\t")).append('\n')
    append("retired\t").append(board.retired.joinToString("\t")).append('\n')
    append("slots\t").append(encodeSlots(board.slots)).append('\n')
    for (folder in board.folders.values.sortedBy { it.id }) {
        append("folder\t")
        append(folder.id).append('\t')
        append(folder.name).append('\t')
        append(folder.platformId.orEmpty()).append('\t')
        append(folder.bucket?.name.orEmpty()).append('\t')
        append(if (folder.userMade) "1" else "0").append('\t')
        append(if (folder.namedByUser) "1" else "0").append('\t')
        append(folder.mark.orEmpty()).append('\t')
        append(encodeSlots(folder.slots))
        append('\n')
    }
}

fun decodeHome(raw: String?): HomeBoard {
    if (raw.isNullOrBlank()) return HomeBoard()
    var board = HomeBoard(slots = emptyList())
    for (line in raw.lineSequence()) {
        if (line.isBlank()) continue
        val bits = line.split('\t')
        when (bits.firstOrNull()) {
            "v1" -> Unit
            "addNew" -> board = board.copy(addNewToHome = bits.getOrNull(1) != "0")
            "hidden" -> board = board.copy(hidden = bits.drop(1).filter { it.isNotEmpty() }.toSet())
            "retired" -> board = board.copy(retired = bits.drop(1).filter { it.isNotEmpty() }.toSet())
            "slots" -> board = board.copy(slots = decodeSlots(bits.drop(1)))
            "folder" -> {
                if (bits.size < 9) continue
                val bucket = bits[4].takeIf { it.isNotEmpty() }?.let { runCatching { HomeKind.valueOf(it) }.getOrNull() }
                val folder = HomeFolder(
                    id = bits[1],
                    name = bits[2],
                    platformId = bits[3].takeIf { it.isNotEmpty() },
                    bucket = bucket,
                    userMade = bits[5] == "1",
                    namedByUser = bits[6] == "1",
                    mark = bits[7].takeIf { it.isNotEmpty() },
                    slots = decodeSlots(bits.drop(8)),
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
 * A platform folder Foldcade made, with no game for that platform left in the catalog and
 * nothing still in its slots, leaves the grid. Its root slot stays empty like any other
 * removed tile, so the tiles after it keep their places. Empty slots left at the end of the
 * grid are dropped. A folder the user made, or one moved while a tile is held, stays.
 */
private fun dropGonePlatformFolders(
    board: HomeBoard,
    catalog: List<HomeItem>,
    platforms: List<HomePlatform>,
): HomeBoard {
    if (board.hold != null) return board
    val present = catalog.mapNotNull { item ->
        if (item.kind != HomeKind.Rom) return@mapNotNull null
        resolvePlatform(platforms, item.platformId)?.id ?: item.platformId?.takeIf { it.isNotBlank() }
    }.toSet()
    val gone = board.folders.values.filter { folder ->
        val platformId = folder.platformId ?: return@filter false
        !folder.userMade && platformId !in present && folder.slots.all { it == null }
    }.map { it.id }.toSet()
    if (gone.isEmpty()) return board
    val slots = board.slots.map { if (it in gone) null else it }.dropLastWhile { it == null }
    return board.copy(
        slots = slots,
        folders = board.folders - gone,
        openFolderId = board.openFolderId?.takeIf { it !in gone },
        destinationFolderId = board.destinationFolderId?.takeIf { it !in gone },
    )
}

private fun ensureSpecialFolders(board: HomeBoard): HomeBoard {
    var next = board
    for (spec in specialFolders()) {
        if (spec.id in next.folders || spec.id in next.retired) continue
        next = next.copy(
            folders = next.folders + (spec.id to spec),
            slots = next.slots + spec.id,
        )
    }
    return next
}

private fun specialFolders(): List<HomeFolder> = listOf(
    HomeFolder(HOME_ANDROID_GAMES, "Android Games", bucket = HomeKind.AndroidGame, mark = bucketMark(HomeKind.AndroidGame)),
    HomeFolder(HOME_ANDROID_APPS, "Android Apps", bucket = HomeKind.AndroidApp, mark = bucketMark(HomeKind.AndroidApp)),
    HomeFolder(HOME_GAMENATIVE, "GameNative", bucket = HomeKind.GameNative, mark = bucketMark(HomeKind.GameNative)),
    HomeFolder(HOME_MOONLIGHT, "Moonlight", bucket = HomeKind.Moonlight, mark = bucketMark(HomeKind.Moonlight)),
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
        HomeKind.AndroidGame -> placeInBucket(board, item.id, HOME_ANDROID_GAMES)
        HomeKind.AndroidApp -> placeInBucket(board, item.id, HOME_ANDROID_APPS)
        HomeKind.GameNative -> placeInBucket(board, item.id, HOME_GAMENATIVE)
        HomeKind.Moonlight -> placeInBucket(board, item.id, HOME_MOONLIGHT)
        HomeKind.Loose -> placeOnRoot(board, item.id)
    }
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

private fun systemName(platforms: List<HomePlatform>, item: HomeItem): String =
    resolvePlatform(platforms, item.platformId)?.name ?: item.platformId ?: ""

private fun encodeSlots(slots: List<String?>): String =
    slots.joinToString("\t") { it ?: "-" }

private fun decodeSlots(bits: List<String>): List<String?> =
    bits.filter { it.isNotEmpty() }.map { if (it == "-") null else it }
