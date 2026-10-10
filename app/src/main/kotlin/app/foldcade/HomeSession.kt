package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.language.AllSort
import app.foldcade.language.AllTab
import app.foldcade.language.Copy
import app.foldcade.language.Effect
import app.foldcade.language.GridFocus
import app.foldcade.language.HOME_ALL
import app.foldcade.language.HomeBoard
import app.foldcade.language.HomeItem
import app.foldcade.language.HomeKeys
import app.foldcade.language.folderCountLine
import app.foldcade.language.HomeKind
import app.foldcade.language.HomePlatform
import app.foldcade.language.Meaning
import app.foldcade.language.Metrics
import app.foldcade.language.addToHome
import app.foldcade.language.allItems
import app.foldcade.language.allLabels
import app.foldcade.language.cancelHold
import app.foldcade.language.closeAll
import app.foldcade.language.closeFolder
import app.foldcade.language.createFolder
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
import app.foldcade.language.nextUserFolderId
import app.foldcade.language.onHome
import app.foldcade.language.openAll
import app.foldcade.language.openFolder
import app.foldcade.language.pickUp
import app.foldcade.language.removeFromHome
import app.foldcade.language.renameFolder
import app.foldcade.language.resolvePlatform
import app.foldcade.language.sectionLabel
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
    var renaming: Boolean = false
        private set
    var renameDraft: String = ""
        private set
    private var renameIndex: Int = 0
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
        board.editing -> HomeKeys.Editing
        board.allOpen -> HomeKeys.AllLibrary
        else -> HomeKeys.Idle
    }

    /** Edit and All name their keys in the hint row. Only renaming has a line of its own. */
    fun hint(): String? = if (renaming) Copy.newFolder else null

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
        if (renaming) return handleRename(meaning)
        if (board.editing) return handleEdit(meaning, index)
        if (board.allOpen) return handleAll(meaning, index)
        return when (meaning) {
            Meaning.Activate -> activateCell(index)
            Meaning.Back -> if (board.openFolderId != null) closeOpenFolder() else null
            else -> null
        }
    }

    fun enterEditing() {
        renaming = false
        board = enterEdit(board)
    }

    fun showAll() {
        renaming = false
        rootIndex = 0
        board = openAll(board)
    }

    fun applyAddNew(enabled: Boolean) {
        board = setAddNewToHome(board, enabled)
    }

    fun pickUpFocused(index: Int) {
        if (!board.editing) board = enterEdit(board)
        board = if (board.hold == null) pickUp(board, index) else board
    }

    fun dropHeld() {
        val index = board.hold?.index ?: return
        board = app.foldcade.language.drop(board, index)
    }

    fun drag(meaning: Meaning) {
        val hold = board.hold ?: return
        val to = neighbor(hold.index, meaning) ?: return
        board = moveHeld(board, to)
    }

    fun encoded(): String = encodeHome(board)

    private fun handleRename(meaning: Meaning): HomeStep {
        return when (meaning) {
            Meaning.Activate -> {
                val folderId = visibleSlots(board).getOrNull(renameIndex)
                if (folderId != null && board.folders[folderId] != null) {
                    board = renameFolder(board, folderId, renameDraft)
                }
                renaming = false
                step(null, renameIndex)
            }
            Meaning.Back, Meaning.CancelHold, Meaning.LeaveEdit -> {
                renaming = false
                step(null, focusIndex())
            }
            else -> step(null, focusIndex())
        }
    }

    private fun handleEdit(meaning: Meaning, index: Int): HomeStep {
        val cursor = board.hold?.index ?: index
        return when (meaning) {
            Meaning.MoveLeft, Meaning.MoveRight, Meaning.MoveUp, Meaning.MoveDown -> {
                val hold = board.hold
                if (hold == null) return HomeStep(handled = false, effect = null, focus = null)
                val to = neighbor(hold.index, meaning) ?: return step(null, hold.index)
                board = moveHeld(board, to)
                step(null, board.hold?.index ?: to)
            }
            Meaning.Activate -> {
                if (board.hold == null) {
                    val id = visibleSlots(board).getOrNull(index)
                    if (id == null) return step(null, index)
                    board = pickUp(board, index)
                    step(null, index)
                } else {
                    board = app.foldcade.language.drop(board, cursor)
                    step(null, cursor)
                }
            }
            Meaning.CancelHold, Meaning.Back -> {
                if (board.hold != null) {
                    val origin = board.hold?.index
                    board = cancelHold(board)
                    step(null, origin ?: index)
                } else if (board.openFolderId != null) {
                    closeOpenFolder()
                } else {
                    step(null, index)
                }
            }
            Meaning.LeaveEdit -> {
                board = leaveEdit(board)
                step(null, index)
            }
            Meaning.RemoveFromHome -> {
                board = removeFromHome(board, cursor)
                step(null, cursor.coerceAtMost((visibleCount() - 1).coerceAtLeast(0)))
            }
            Meaning.MakeFolder -> makeOrRename(cursor)
            Meaning.PageTowardStart, Meaning.PageTowardEnd ->
                if (board.hold == null) {
                    HomeStep(handled = false, effect = null, focus = null)
                } else {
                    step(null, cursor)
                }
            Meaning.LeftPanel, Meaning.RightPanel -> HomeStep(handled = false, effect = null, focus = null)
            else -> step(null, cursor)
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
                Meaning.LeaveEdit -> step(null, index)
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
            Meaning.Back -> {
                board = closeAll(board)
                step(null, rootIndex)
            }
            Meaning.AddToHome -> {
                val item = allRows().getOrNull(index) ?: return step(null, index)
                board = addToHome(board, item.id, board.destinationFolderId)
                step(null, index)
            }
            Meaning.CycleDestination -> {
                board = cycleDestination(board)
                step(null, index)
            }
            Meaning.LetterForward, Meaning.LetterBackward -> {
                val labels = allLabels(allRows(), platforms, board.allSort)
                val next = jumpLetter(labels, index, meaning == Meaning.LetterForward)
                step(null, next)
            }
            Meaning.LeftPanel, Meaning.RightPanel -> HomeStep(handled = false, effect = null, focus = null)
            Meaning.MoveDown, Meaning.MoveLeft, Meaning.MoveRight, Meaning.PageTowardStart, Meaning.PageTowardEnd ->
                HomeStep(handled = false, effect = null, focus = null)
            else -> step(null, index)
        }
    }

    private fun activateCell(index: Int): HomeStep {
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
        return step(Effect.Launch(index), index)
    }

    private fun closeOpenFolder(): HomeStep {
        val restore = rootIndex
        board = closeFolder(board)
        return step(null, restore)
    }

    private fun makeOrRename(index: Int): HomeStep {
        val id = visibleSlots(board).getOrNull(index)
        if (id != null && board.folders[id] != null && board.hold == null) {
            renaming = true
            renameIndex = index
            renameDraft = board.folders.getValue(id).name
            return step(null, index)
        }
        val folderId = nextUserFolderId(board)
        val count = board.folders.values.count { it.userMade } + 1
        board = createFolder(board, folderId, "${Copy.newFolder} $count")
        val placed = board.slots.indexOf(folderId).coerceAtLeast(0)
        return step(null, placed)
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
                mark = "desk",
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

data class HomeStep(
    val handled: Boolean,
    val effect: Effect?,
    val focus: GridFocus?,
)
