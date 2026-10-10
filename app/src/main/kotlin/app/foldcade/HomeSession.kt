package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.language.AllSort
import app.foldcade.language.AllTab
import app.foldcade.language.Copy
import app.foldcade.language.Effect
import app.foldcade.language.GridFocus
import app.foldcade.language.HOME_ALL
import app.foldcade.language.FOLDER_ICONS
import app.foldcade.language.FolderChoice
import app.foldcade.language.TileActions
import app.foldcade.language.HomeBoard
import app.foldcade.language.HomeItem
import app.foldcade.language.HomeKeys
import app.foldcade.language.folderCountLine
import app.foldcade.language.HomeKind
import app.foldcade.language.HomePlatform
import app.foldcade.language.MARK_LIBRARY
import app.foldcade.language.Meaning
import app.foldcade.language.Metrics
import app.foldcade.language.addToHome
import app.foldcade.language.allItems
import app.foldcade.language.allLabels
import app.foldcade.language.closeAll
import app.foldcade.language.closeFolder
import app.foldcade.language.folderAround
import app.foldcade.language.cycleAllSort
import app.foldcade.language.cycleAllTab
import app.foldcade.language.cycleDestination
import app.foldcade.language.cycleSystemFilter
import app.foldcade.language.decodeHome
import app.foldcade.language.encodeHome
import app.foldcade.language.enterEdit
import app.foldcade.language.folderCount
import app.foldcade.language.jumpLetter
import app.foldcade.language.leaveEdit
import app.foldcade.language.mergeHome
import app.foldcade.language.moveHeld
import app.foldcade.language.moveToFolder
import app.foldcade.language.nextUserFolderId
import app.foldcade.language.onHome
import app.foldcade.language.openAll
import app.foldcade.language.openFolder
import app.foldcade.language.pickUp
import app.foldcade.language.removeFromHome
import app.foldcade.language.renameFolder
import app.foldcade.language.resolvePlatform
import app.foldcade.language.sectionLabel
import app.foldcade.language.setFolderIcon
import app.foldcade.language.setAddNewToHome
import app.foldcade.language.visibleSlots

/** One cell the grid can draw. A null [id] is an empty slot. */
data class HomeFace(
    val id: String?,
    val title: String,
    val shortText: String,
    val mark: String?,
    val empty: Boolean,
    val folder: Boolean,
    val pinned: Boolean,
    val onGrid: Boolean,
    val section: String?,
    val platformId: String?,
    val androidPackage: String?,
    val occupiesBoth: Boolean,
    /** The game is on its server and not on this device yet. The tile shows a cloud. */
    val onServer: Boolean = false,
)

data class AllChrome(
    val tab: String,
    val sort: String,
    val system: String,
    val destination: String,
    val focused: Int?,
)

/**
 * Layout for the bottom-screen home grid.
 * The catalog is every ROM and app. The board is only what the user placed.
 */
class HomeSession(raw: String?) {
    var board: HomeBoard = decodeHome(raw)
        private set

    private var shelf: List<HomeItem> = emptyList()
    private var apps: List<HomeItem> = emptyList()
    private var library: List<HomeItem> = emptyList()
    private var platforms: List<HomePlatform> = emptyList()
    private val games = HashMap<String, Game>()
    private val shelfById = HashMap<String, ShelfGame>()
    /** The folder whose name field is open. Null when closed. */
    var renamingId: String? = null
        private set
    var renameDraft: String = ""
        private set

    /** The folder whose icon is being chosen, and the focused choice in [FOLDER_ICONS]. Null when closed. */
    var iconPick: FolderIconPick? = null
        private set
    private var iconCell: Int = 0
    private var rootIndex: Int = 0

    fun rememberShelf(gamesOnShelf: List<ShelfGame>) {
        shelfById.clear()
        gamesOnShelf.forEach { game -> shelfById[game.id] = game }
    }

    fun remerge(
        shelfItems: List<HomeItem>,
        appItems: List<HomeItem>,
        libraryItems: List<HomeItem>,
        platformList: List<HomePlatform>,
        remembered: Map<String, Game> = emptyMap(),
        unsettled: (String) -> Boolean = { false },
    ): Boolean {
        shelf = shelfItems
        apps = appItems
        library = libraryItems
        platforms = platformList
        remembered.forEach { (id, game) -> games[id] = game }
        val next = mergeHome(board, catalog(), platforms, unsettled)
        val changed = next != board
        board = next
        return changed
    }

    fun asShelf(face: HomeFace): ShelfGame {
        val shelfGame = face.id?.let { shelfById[it] }
        val game = face.id?.let { games[it] }
        return ShelfGame(
            id = face.id.orEmpty(),
            title = face.title,
            shortText = face.shortText,
            platformId = face.platformId ?: shelfGame?.platformId,
            occupiesBothDisplays = shelfGame?.occupiesBothDisplays == true,
            mark = face.mark ?: shelfGame?.mark,
            contentUri = shelfGame?.contentUri,
            androidPackage = face.androidPackage ?: shelfGame?.androidPackage,
            favorite = shelfGame?.favorite == true,
            libraryId = game?.backendId ?: shelfGame?.libraryId,
            remoteKey = game?.remoteKey ?: shelfGame?.remoteKey,
            emptyShelfHint = shelfGame?.emptyShelfHint == true,
            availabilityLabel = shelfGame?.availabilityLabel,
        )
    }

    fun openedFolderHasGame(): Boolean {
        val folderId = board.openFolderId ?: return false
        return board.folders[folderId]?.slots?.any { it != null } == true
    }

    fun focusChrome(index: Int) {
        board = board.copy(allChrome = index.coerceIn(0, 3))
    }

    fun game(id: String): Game? = games[id]

    fun catalog(): List<HomeItem> = (shelf + apps + library).distinctBy { it.id }

    fun visibleCount(): Int = faces().size

    fun face(index: Int): HomeFace? = faces().getOrNull(index)

    fun faces(): List<HomeFace> {
        if (board.allOpen) return allFaces()
        return visibleSlots(board).map { id -> faceFor(id, section = null) }
    }

    fun keys(): HomeKeys = when {
        iconPick != null -> HomeKeys.PickingIcon
        renamingId != null -> HomeKeys.Renaming
        board.hold != null -> HomeKeys.Moving
        board.allOpen -> HomeKeys.AllLibrary
        else -> HomeKeys.Idle
    }

    fun chrome(): AllChrome? {
        if (!board.allOpen) return null
        val system = board.allSystemId?.let { id ->
            platforms.firstOrNull { it.id == id }?.name
        } ?: Copy.allSystems
        val destination = board.destinationFolderId?.let { id ->
            board.folders[id]?.name
        } ?: Copy.homeSlot
        return AllChrome(
            tab = if (board.allTab == AllTab.Games) Copy.allGames else Copy.allApps,
            sort = when (board.allSort) {
                AllSort.Title -> Copy.sortTitle
                AllSort.RecentlyPlayed -> Copy.sortRecent
                AllSort.System -> Copy.sortSystem
            },
            system = system,
            destination = destination,
            focused = board.allChrome,
        )
    }

    fun animToken(): String = "${board.openFolderId}:${board.allOpen}"

    /** True when this meaning was applied. False lets the shared grid reducer move focus. */
    fun handle(meaning: Meaning, index: Int): HomeStep? {
        iconPick?.let { return handleIconPick(it, meaning) }
        if (renamingId != null) return handleRename(meaning)
        if (board.editing) return handleMove(meaning, index)
        if (board.allOpen) return handleAll(meaning, index)
        return when (meaning) {
            Meaning.Activate -> activateCell(index)
            Meaning.ActivateBottom -> activateCell(index, onBottom = true)
            Meaning.Back -> if (board.openFolderId != null) closeOpenFolder() else null
            else -> null
        }
    }

    /**
     * What Y offers for the tile at [index]. In All, adding an item that is not
     * placed yet. On the board, moving, folders, and removing. Null while a
     * tile moves, a picker or name field is open, or All's chrome has focus.
     */
    fun actions(index: Int): TileActions? {
        if (iconPick != null || renamingId != null || board.editing) return null
        if (board.allOpen) {
            if (board.allChrome != null) return null
            val item = allRows().getOrNull(index) ?: return null
            if (onHome(board, item.id)) return null
            val into = board.destinationFolderId?.let { board.folders[it]?.name }
            return TileActions(addToHome = if (into != null) "${Copy.addTo} $into" else Copy.addToHome)
        }
        val slots = visibleSlots(board)
        if (index !in slots.indices) return null
        val atRoot = board.openFolderId == null
        val id = slots[index] ?: return if (atRoot) TileActions(newFolder = true) else null
        if (id == HOME_ALL) return TileActions(move = true, newFolder = true)
        val folder = board.folders[id]
        if (folder != null) {
            return TileActions(move = true, rename = true, icon = folder.userMade, remove = true, removeIsFolder = true)
        }
        val here = board.openFolderId
        val destinations = buildList {
            if (here != null) add(FolderChoice(null, Copy.homeSlot))
            board.slots.mapNotNull { slot -> board.folders[slot] }
                .filter { it.id != here }
                .forEach { add(FolderChoice(it.id, it.name)) }
        }
        return TileActions(move = true, destinations = destinations, newFolder = true, remove = true)
    }

    /** Move: lifts the tile at [index]. The D-pad walks it, A drops it, B puts it back. */
    fun startMove(index: Int) {
        if (visibleSlots(board).getOrNull(index) == null) return
        board = pickUp(enterEdit(board), index)
    }

    /** Sends the game at [index] to [folderId], or the root when null. Returns where focus goes. */
    fun moveTo(index: Int, folderId: String?): Int {
        val id = visibleSlots(board).getOrNull(index) ?: return index
        board = moveToFolder(board, id, folderId)
        return index
    }

    /**
     * A new folder around the game at [index], then its name field. Made from
     * inside a folder, it lands on the root, so the open folder closes onto it.
     */
    fun newFolder(index: Int): Int {
        val folderId = nextUserFolderId(board)
        val count = board.folders.values.count { it.userMade } + 1
        board = folderAround(board, index, folderId, "${Copy.newFolder} $count")
        if (folderId !in board.folders) return index
        if (board.openFolderId != null) board = closeFolder(board)
        val placed = board.slots.indexOf(folderId).coerceAtLeast(0)
        startRename(placed)
        return placed
    }

    /** Opens the name field for the folder at [index]. */
    fun startRename(index: Int) {
        val id = visibleSlots(board).getOrNull(index) ?: return
        val folder = board.folders[id] ?: return
        renamingId = id
        renameDraft = folder.name
    }

    fun editRename(text: String) {
        if (renamingId != null) renameDraft = text.replace('\n', ' ')
    }

    /** Opens the icon picker for the folder the user made at [index]. */
    fun pickIcon(index: Int) {
        val id = visibleSlots(board).getOrNull(index) ?: return
        val folder = board.folders[id]?.takeIf { it.userMade } ?: return
        iconPick = FolderIconPick(id, FOLDER_ICONS.indexOf(folder.mark).coerceAtLeast(0))
        iconCell = index
    }

    /** Hides the tile at [index], or spills the folder there. Returns where focus goes. */
    fun remove(index: Int): Int {
        board = removeFromHome(board, index)
        return index.coerceAtMost((visibleCount() - 1).coerceAtLeast(0))
    }

    /** Places the All item at [index] on Home, in the destination folder when one is chosen. */
    fun addFocused(index: Int) {
        val item = allRows().getOrNull(index) ?: return
        board = addToHome(board, item.id, board.destinationFolderId)
    }

    fun showAll() {
        renamingId = null
        rootIndex = 0
        board = openAll(board)
    }

    fun applyAddNew(enabled: Boolean) {
        board = setAddNewToHome(board, enabled)
    }

    fun drag(meaning: Meaning) {
        val hold = board.hold ?: return
        val to = neighbor(hold.index, meaning) ?: return
        board = moveHeld(board, to)
        if (board.hold == null) board = leaveEdit(board)
    }

    fun encoded(): String = encodeHome(board)

    /** The D-pad walks the icons, A wears the focused one, B leaves the folder as it was. */
    private fun handleIconPick(pick: FolderIconPick, meaning: Meaning): HomeStep {
        val last = FOLDER_ICONS.lastIndex
        val columns = FolderIconPick.COLUMNS
        val next = when (meaning) {
            Meaning.MoveLeft -> pick.index - 1
            Meaning.MoveRight -> pick.index + 1
            Meaning.MoveUp -> pick.index - columns
            Meaning.MoveDown -> pick.index + columns
            else -> null
        }
        when {
            next != null -> iconPick = pick.copy(index = next.coerceIn(0, last))
            meaning == Meaning.Activate -> {
                board = setFolderIcon(board, pick.folderId, FOLDER_ICONS[pick.index])
                iconPick = null
            }
            meaning == Meaning.Back || meaning == Meaning.CancelHold -> iconPick = null
        }
        return step(null, iconCell)
    }

    /** A saves the name, B keeps the old one. The keyboard types into [renameDraft]. */
    private fun handleRename(meaning: Meaning): HomeStep {
        val id = renamingId ?: return step(null, focusIndex())
        when (meaning) {
            Meaning.Activate -> {
                board = renameFolder(board, id, renameDraft)
                renamingId = null
            }
            Meaning.Back, Meaning.CancelHold -> renamingId = null
            else -> Unit
        }
        val at = visibleSlots(board).indexOf(id)
        return step(null, if (at >= 0) at else focusIndex())
    }

    /**
     * A tile lifted with Move. The D-pad walks it, and onto a folder drops it in.
     * A drops it, B puts it back. Either way the board is back to plain browsing.
     */
    private fun handleMove(meaning: Meaning, index: Int): HomeStep {
        val hold = board.hold
        if (hold == null) {
            board = leaveEdit(board)
            return HomeStep(handled = false, effect = null, focus = null)
        }
        return when (meaning) {
            Meaning.MoveLeft, Meaning.MoveRight, Meaning.MoveUp, Meaning.MoveDown -> {
                val to = neighbor(hold.index, meaning) ?: return step(null, hold.index)
                board = moveHeld(board, to)
                val at = board.hold?.index ?: to
                if (board.hold == null) board = leaveEdit(board)
                step(null, at)
            }
            Meaning.Activate -> {
                board = leaveEdit(app.foldcade.language.drop(board, hold.index))
                step(null, hold.index)
            }
            Meaning.CancelHold, Meaning.Back -> {
                board = leaveEdit(board)
                step(null, hold.origin)
            }
            else -> step(null, hold.index)
        }
    }

    private fun handleAll(meaning: Meaning, index: Int): HomeStep {
        val chrome = board.allChrome
        if (chrome != null) {
            return when (meaning) {
                Meaning.MoveLeft -> {
                    board = board.copy(allChrome = (chrome - 1).coerceAtLeast(0))
                    step(null, index)
                }
                Meaning.MoveRight -> {
                    board = board.copy(allChrome = (chrome + 1).coerceAtMost(3))
                    step(null, index)
                }
                Meaning.MoveDown, Meaning.Back -> {
                    board = board.copy(allChrome = null)
                    step(null, index)
                }
                Meaning.Activate -> {
                    board = cycleChrome(chrome)
                    step(null, 0)
                }
                else -> step(null, index)
            }
        }
        return when (meaning) {
            Meaning.MoveUp -> {
                if (index < Metrics.columns) {
                    board = board.copy(allChrome = 0)
                    return step(null, index)
                }
                HomeStep(handled = false, effect = null, focus = null)
            }
            Meaning.Activate -> activateCell(index)
            Meaning.ActivateBottom -> activateCell(index, onBottom = true)
            Meaning.Back -> {
                board = closeAll(board)
                step(null, rootIndex)
            }
            Meaning.LetterForward, Meaning.LetterBackward -> {
                val labels = allLabels(allRows(), platforms, board.allSort)
                val next = jumpLetter(labels, index, meaning == Meaning.LetterForward)
                step(null, next)
            }
            Meaning.LeftPanel, Meaning.RightPanel, Meaning.Options -> HomeStep(handled = false, effect = null, focus = null)
            Meaning.MoveDown, Meaning.MoveLeft, Meaning.MoveRight, Meaning.PageTowardStart, Meaning.PageTowardEnd ->
                HomeStep(handled = false, effect = null, focus = null)
            else -> step(null, index)
        }
    }

    /** A opens a folder or launches on the top screen. X opens a folder too, or launches on the bottom. */
    private fun activateCell(index: Int, onBottom: Boolean = false): HomeStep {
        val face = face(index) ?: return step(null, index)
        val id = face.id ?: return step(null, index)
        if (face.pinned) {
            rootIndex = index
            board = openAll(board)
            return step(null, 0)
        }
        if (face.folder) {
            rootIndex = index
            board = openFolder(board, id)
            return step(null, 0)
        }
        return step(Effect.Launch(index, onBottom), index)
    }

    private fun closeOpenFolder(): HomeStep {
        val restore = rootIndex
        board = closeFolder(board)
        return step(null, restore)
    }

    private fun cycleChrome(index: Int): HomeBoard = when (index) {
        0 -> cycleAllTab(board)
        1 -> cycleAllSort(board)
        2 -> cycleSystemFilter(board, platforms, catalog())
        else -> cycleDestination(board)
    }

    private fun allRows(): List<HomeItem> =
        allItems(catalog(), platforms, board.allTab, board.allSort, board.allSystemId)

    private fun allFaces(): List<HomeFace> {
        val rows = allRows()
        var previous: String? = null
        return rows.map { item ->
            val section = sectionLabel(item, platforms, board.allSort)
            val header = section?.takeIf { it != previous }
            previous = section ?: previous
            faceFor(item.id, header).copy(onGrid = onHome(board, item.id))
        }
    }

    private fun faceFor(id: String?, section: String?): HomeFace {
        if (id == null) {
            return HomeFace(
                id = null,
                title = "",
                shortText = "",
                mark = null,
                empty = true,
                folder = false,
                pinned = false,
                onGrid = false,
                section = section,
                platformId = null,
                androidPackage = null,
                occupiesBoth = false,
            )
        }
        if (id == HOME_ALL) {
            return HomeFace(
                id = id,
                title = Copy.allLibrary,
                shortText = "",
                mark = MARK_LIBRARY,
                empty = false,
                folder = false,
                pinned = true,
                onGrid = true,
                section = section,
                platformId = null,
                androidPackage = null,
                occupiesBoth = false,
            )
        }
        val folder = board.folders[id]
        if (folder != null) {
            val count = folderCount(board, id)
            return HomeFace(
                id = id,
                title = folder.name,
                shortText = folderCountLine(count, apps = folder.bucket == HomeKind.AndroidApp),
                mark = folder.mark,
                empty = false,
                folder = true,
                pinned = false,
                onGrid = true,
                section = section,
                platformId = folder.platformId,
                androidPackage = null,
                occupiesBoth = false,
            )
        }
        val item = catalog().firstOrNull { it.id == id }
        val game = games[id]
        return HomeFace(
            id = id,
            title = item?.title ?: id,
            shortText = platformName(item?.platformId ?: game?.platformId),
            mark = item?.mark,
            empty = false,
            folder = false,
            pinned = false,
            onGrid = true,
            section = section,
            platformId = item?.platformId ?: game?.platformId,
            androidPackage = id.removePrefix("android:").takeIf { id.startsWith("android:") },
            occupiesBoth = false,
            onServer = game?.availability == Availability.RemoteOnly,
        )
    }

    /** The platform's display name. An id with no listed platform is not shown. */
    private fun platformName(id: String?): String {
        if (id == null) return ""
        return platforms.firstOrNull { it.id == id || id in it.aliases }?.name.orEmpty()
    }

    private fun neighbor(index: Int, meaning: Meaning): Int? {
        val columns = Metrics.columns
        val count = visibleSlots(board).size.coerceAtLeast(index + 1)
        val column = index % columns
        val row = index / columns
        val target = when (meaning) {
            Meaning.MoveLeft -> if (column == 0) null else index - 1
            Meaning.MoveRight -> index + 1
            Meaning.MoveUp -> if (row == 0) null else index - columns
            Meaning.MoveDown -> index + columns
            else -> null
        } ?: return null
        if (target < 0) return null
        if (target >= count && meaning != Meaning.MoveRight && meaning != Meaning.MoveDown) return null
        return target
    }

    private fun focusIndex(): Int = board.hold?.index ?: rootIndex

    private fun step(effect: Effect?, index: Int): HomeStep =
        HomeStep(
            handled = true,
            effect = effect,
            focus = GridFocus(cellIndex = index.coerceAtLeast(0), lastColumn = index.coerceAtLeast(0) % Metrics.columns),
        )
}

/** The icon picker for one folder the user made. [index] is the focused icon in [FOLDER_ICONS]. */
data class FolderIconPick(val folderId: String, val index: Int) {
    companion object {
        /** The picker lays the icons out in rows of this many. */
        const val COLUMNS: Int = 7
    }
}

data class HomeStep(
    val handled: Boolean,
    val effect: Effect?,
    val focus: GridFocus?,
)
