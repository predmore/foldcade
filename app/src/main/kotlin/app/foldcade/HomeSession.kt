package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.language.HomeSelection
import app.foldcade.language.adoptSameGames
import app.foldcade.language.BulkAction
import app.foldcade.language.bulkCollectionChoices
import app.foldcade.language.bulkCollection
import app.foldcade.language.bulkNewCollection
import app.foldcade.language.bulkHide
import app.foldcade.language.LibrarySort
import app.foldcade.language.Copy
import app.foldcade.language.HomeKind
import app.foldcade.language.hideGame
import app.foldcade.language.showHidden
import app.foldcade.language.hiddenItems
import app.foldcade.language.deleteShortcut
import app.foldcade.language.Effect
import app.foldcade.language.GridFocus
import app.foldcade.language.GridStrip
import app.foldcade.language.HOME_ALL
import app.foldcade.language.IconPick
import app.foldcade.language.collectionLook
import app.foldcade.language.FindEntry
import app.foldcade.language.FindKind
import app.foldcade.language.HomeLanding
import app.foldcade.language.homeLanding
import app.foldcade.language.resolvePlatform
import app.foldcade.language.TileActions
import app.foldcade.language.FolderSort
import app.foldcade.language.FolderSortChoice
import app.foldcade.language.setFolderSort
import app.foldcade.language.stepped
import app.foldcade.language.HomeBoard
import app.foldcade.language.HomeFolder
import app.foldcade.language.HomeItem
import app.foldcade.language.HomeHold
import app.foldcade.language.HomeKeys
import app.foldcade.language.KeyFocus
import app.foldcade.language.TypingCue
import app.foldcade.language.TypingStep
import app.foldcade.language.quickNames
import app.foldcade.language.folderCountLine
import app.foldcade.language.allCount
import app.foldcade.language.isApp
import app.foldcade.language.HomePlatform
import app.foldcade.language.MARK_LIBRARY
import app.foldcade.language.Meaning
import app.foldcade.language.Metrics
import app.foldcade.language.addItemToHome
import app.foldcade.language.allItems
import app.foldcade.language.HoldDrop
import app.foldcade.language.canTakeOut
import app.foldcade.language.canTidy
import app.foldcade.language.cancelHold
import app.foldcade.language.closeAll
import app.foldcade.language.closeFolder
import app.foldcade.language.collectionChoices
import app.foldcade.language.decodeHome
import app.foldcade.language.setRecentOnHome
import app.foldcade.language.otherAppFolder
import app.foldcade.language.RemoveVerb
import app.foldcade.language.emptyCollection
import app.foldcade.language.encodeHome
import app.foldcade.language.enterEdit
import app.foldcade.language.fileGame
import app.foldcade.language.folderTakes
import app.foldcade.language.folderCount
import app.foldcade.language.leaveEdit
import app.foldcade.language.mergeHome
import app.foldcade.language.moveHeld
import app.foldcade.language.newCollection
import app.foldcade.language.nextUserFolderId
import app.foldcade.language.onHome
import app.foldcade.language.openAll
import app.foldcade.language.openFolder
import app.foldcade.language.pickUp
import app.foldcade.language.removeFromHome
import app.foldcade.language.removeVerb
import app.foldcade.language.renameFolder
import app.foldcade.language.setFolderIcon
import app.foldcade.language.setAddNewToHome
import app.foldcade.language.toggleCollection
import app.foldcade.language.heldId
import app.foldcade.language.heldOver
import app.foldcade.language.holdDrop
import app.foldcade.language.raiseToHome
import app.foldcade.language.tidy
import app.foldcade.language.visibleSlots
import app.foldcade.language.FolderEntry
import app.foldcade.language.FolderSwitch
import app.foldcade.language.atRoot
import app.foldcade.language.closeFolders
import app.foldcade.language.foldcadeFolders
import app.foldcade.language.groupOnHome
import app.foldcade.language.openAnyFolder
import app.foldcade.language.openFolders
import app.foldcade.language.setFolderOnHome

/** One cell the grid can draw. A null [id] is an empty slot. */
data class HomeFace(
    val id: String?,
    val title: String,
    val shortText: String,
    val mark: String?,
    val empty: Boolean,
    /** A folder, a collection, or Recently played: A opens it. */
    val folder: Boolean,
    /** The All tile, which A opens as the whole library. */
    val allTile: Boolean,
    val onGrid: Boolean,
    val platformId: String?,
    val androidPackage: String?,
    val occupiesBoth: Boolean,
    /** The game is on its server and not on this device yet. The tile shows a cloud. */
    val onServer: Boolean = false,
)

/**
 * Layout for the bottom-screen home grid.
 * The catalog is every ROM and app. The board is only what the user placed.
 */
class HomeSession(
    raw: String?,
    nowMillis: Long = System.currentTimeMillis(),
    /** Each copy's id to the one tile it folds into ([foldSameGames]), as last saved. */
    sameGames: Map<String, String> = emptyMap(),
) {
    /** Each copy's id to its tile, as the last merge folded them. */
    private var sameGames: Map<String, String> = sameGames

    /** The tile [id] is shown as now. A menu or a selection made before a copy folded names the copy. */
    private fun tileOf(id: String): String = sameGames[id] ?: id

    /**
     * The board on screen. While a tile is held, the walk is measured against the board as it
     * was at pickup, so a library's report then waits in [pendingMerge]: merged into that walk,
     * it would be undone by the next step. Selection also keeps its view steady until it ends.
     * Putting a tile down, cancelling, or finishing selection merges the latest report.
     */
    var board: HomeBoard = decodeHome(raw, nowMillis)
        .let { it.copy(collectionSerial = app.foldcade.language.collectionHighWater(it)) }
        .let { adoptSameGames(it, sameGames) }
        private set(value) {
            field = value
            selection = selection?.inView(animToken())
            val waiting = pendingMerge ?: return
            if (value.hold != null || selection != null) return
            pendingMerge = null
            merge(waiting)
        }

    /** The last report that came while a tile was held or games were checked. Null when none waits. */
    private var pendingMerge: PendingMerge? = null

    var selection: HomeSelection? = null
        private set

    fun selectedIds(): List<String> = selection?.ordered(faces().map { it.id }).orEmpty()

    fun startSelection(id: String) {
        if (faces().none { it.id == id && !it.folder && !it.allTile && !it.empty }) return
        selection = HomeSelection(animToken(), setOf(id))
    }

    fun finishSelection() {
        selection = null
        board = board
    }

    /** Undo records only the edit, after the deferred report and before folder cleanup. */
    var bulkBefore: HomeBoard? = null
        private set
    var bulkAfter: HomeBoard? = null
        private set

    fun bulk(action: BulkAction, selected: List<String>, collectionId: String?, index: Int): Int {
        if (selection == null || selected.isEmpty()) return index
        finishSelection()
        // The report that waited on the selection may have folded a checked copy into its tile.
        val ids = selected.map(::tileOf).distinct()
        bulkBefore = board
        val name = "${Copy.collection} ${board.folders.values.count { it.collection } + 1}"
        val made = nextUserFolderId(board)
        val items = catalog().associateBy { it.id }
        board = when (action) {
            BulkAction.Collection -> collectionId?.let { bulkCollection(board, it, ids, items::get, platforms) } ?: board
            BulkAction.NewCollection -> bulkNewCollection(board, made, name, ids)
            BulkAction.Hide -> bulkHide(board, ids)
        }
        bulkAfter = board
        if (action == BulkAction.NewCollection && made in board.folders) openNaming(made, index, fresh = true)
        return index
    }

    private fun bulkActions(): TileActions {
        val ids = selectedIds()
        if (ids.isEmpty()) return TileActions()
        return TileActions(bulk = ids, collections = bulkCollectionChoices(board, ids), newCollection = true,
            hide = ids.any { it !in board.hidden })
    }

    var sort: LibrarySort = LibrarySort.Listed
    var lastPlayed: (String) -> Long? = { null }

    private var shelf: List<HomeItem> = emptyList()
    private var apps: List<HomeItem> = emptyList()
    private var library: List<HomeItem> = emptyList()
    private var platforms: List<HomePlatform> = emptyList()

    /** The games All lists, counted once per merge rather than on every face. */
    private var allGames: Int = 0
    private val games = HashMap<String, Game>()
    private val copies = HashMap<String, List<Game>>()
    private val shelfById = HashMap<String, ShelfGame>()
    /** The folder or collection whose name is being typed, and the keyboard typing it. Null when closed. */
    var renamingId: String? = null
        private set
    var typing: TypingSession? = null
        private set

    /** The cell focus goes back to when the name field closes. */
    private var renameCell: Int = 0

    /** The game an open menu keeps acting on after Add to took it out of the slot on screen. */
    private var menuGame: String? = null

    /** The folder an open menu in Folders keeps switching, wherever it moves there. */
    private var menuFolder: String? = null

    /** The collection whose icon is being chosen, and what is focused. Null when closed. */
    var iconPick: IconPick? = null
        private set

    /** The collection that last took a new look, and when, so its tile can settle into it. */
    var settled: Pair<String, Long>? = null
        private set
    private var iconCell: Int = 0
    private var rootIndex: Int = 0

    /** The empty collection Add games left for All. Back from All opens it again. */
    private var addingTo: String? = null

    /** The folder opened from Folders. Back from it opens Folders again, on that folder. */
    private var fromFolders: String? = null

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
        hidden: Set<String> = emptySet(),
        sameGames: Map<String, String> = emptyMap(),
        copies: Map<String, List<Game>> = emptyMap(),
    ) {
        val report = PendingMerge(shelfItems, appItems, libraryItems, platformList, remembered, unsettled, hidden, sameGames, copies)
        // A later report replaces one still waiting; the games it remembers add up.
        if (board.hold != null || selection != null) {
            pendingMerge = report.copy(remembered = pendingMerge?.remembered.orEmpty() + remembered)
            return
        }
        merge(report)
    }

    /** Library reports merged so far, so a reading of the catalog knows it is still current. */
    private var merges = 0

    private fun merge(report: PendingMerge) {
        merges++
        shelf = report.shelf
        apps = report.apps
        library = report.library
        allGames = allCount(catalog())
        platforms = report.platforms
        report.remembered.forEach { (id, game) -> games[id] = game }
        report.copies.forEach { (id, listed) -> if (listed.isNotEmpty()) copies[id] = listed }
        sameGames = report.sameGames
        // A menu open on a copy reads and acts on the tile it folded into.
        menuGame = menuGame?.let(::tileOf)
        // Copies folded since the board was saved bring their places onto their tile first.
        val adopted = adoptSameGames(board, report.sameGames)
        val reconciled = report.hidden.fold(adopted) { current, id -> hideGame(current, id) }
        board = mergeHome(reconciled, catalog(), platforms, report.unsettled)
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

    fun game(id: String): Game? = games[id]

    /**
     * The library games behind the tile [id]: each copy of a game several libraries have, in
     * rank order, or the one game of any other tile. Kept while a copy's library is still loading.
     */
    fun copies(id: String): List<Game> = copies[id] ?: listOfNotNull(games[id])

    fun catalog(): List<HomeItem> = (shelf + apps + library).distinctBy { it.id }

    fun visibleCount(): Int = faces().size

    fun face(index: Int): HomeFace? = faces().getOrNull(index)

    fun faces(): List<HomeFace> {
        if (board.allOpen) return allFaces()
        if (board.foldersOpen) return folderFaces()
        return visibleSlots(board).map { id -> faceFor(id) }
    }

    fun keys(): HomeKeys = when {
        iconPick != null -> HomeKeys.PickingIcon
        typing != null -> HomeKeys.Typing
        board.hold != null -> HomeKeys.Moving
        selection != null -> HomeKeys.Selecting
        board.allOpen -> HomeKeys.AllLibrary
        else -> HomeKeys.Idle
    }

    fun animToken(): String = "${board.openFolderId}:${board.allOpen}:${board.foldersOpen}"

    /** True when this meaning was applied. False lets the shared grid reducer move focus. */
    fun handle(meaning: Meaning, index: Int, repeatCount: Int = 0): HomeStep? {
        iconPick?.let { return handleIconPick(it, meaning) }
        typed { it.handle(meaning) }?.let { return it }
        if (board.editing) return handleMove(meaning, index, repeatCount)
        // A held A or X opens or launches once. Still down after a drop, it does nothing more.
        if (repeatCount > 0 && (meaning == Meaning.Activate || meaning == Meaning.ActivateBottom)) return step(null, index)
        selection?.let { checks ->
            when (meaning) {
                Meaning.Activate -> {
                    if (repeatCount == 0) selection = checks.toggle(slotGame(index), index)
                    return step(null, index)
                }
                Meaning.Back -> { finishSelection(); return step(null, index) }
                Meaning.ActivateBottom, Meaning.View, Meaning.Menu, Meaning.LeftPanel, Meaning.RightPanel -> return step(null, index)
                else -> Unit
            }
            return null
        }
        if (board.allOpen) return handleAll(meaning, index)
        if (board.foldersOpen) return handleFolders(meaning, index)
        return when (meaning) {
            Meaning.Activate, Meaning.ActivateBottom -> emptyCollection()?.let { addGames(it.id) }
                ?: activateCell(index, onBottom = meaning == Meaning.ActivateBottom)
            // With no tile to land on, Down from L1 or R1 comes back to Add games.
            Meaning.MoveDown -> emptyCollection()?.let { step(null, 0) }
            Meaning.Back -> if (board.openFolderId != null) closeOpenFolder() else null
            else -> null
        }
    }

    /**
     * The collection on screen while it holds no game and nothing else is open over it.
     * A or X there is Add games ([addGames]).
     */
    fun emptyCollection(): HomeFolder? =
        if (iconPick != null || typing != null || selection != null || board.editing) null else emptyCollection(board)

    /** Opens All to fill collection [id] from, with Y › Add to. Back from All comes back to it. */
    private fun addGames(id: String): HomeStep {
        board = openAll(board)
        addingTo = id
        return step(null, 0)
    }

    /**
     * What Y offers for the tile at [index]. A game can always be added to Home and to
     * collections, and hidden; where it sits decides what Remove does (see [removeVerb]).
     * A group can move, be renamed and come off Home, and a collection takes an icon. An
     * empty slot moves, and on the root makes a collection.
     * A downloaded server game can be deleted from anywhere. While Add to has taken the game
     * out of the collection on screen, the menu keeps acting on that game. Null while a tile
     * moves or a picker or name field is open.
     * Inside a folder or collection, Edit home carries its Sort ([TileActions.boardSort]); a
     * folder tile's own Sort ends its Y menu. While a folder is sorted, nothing in it moves.
     */
    fun actions(index: Int): TileActions? {
        if (iconPick != null || renamingId != null || board.editing) return null
        if (selection != null) return bulkActions()
        val open = board.openFolderId?.let { board.folders[it] }?.takeUnless { board.allOpen }
        val sort = open?.let(::sortChoice)
        val held = menuGame
        if (held != null && slotGame(index) != held) return gameActions(held, move = false, remove = null).copy(kept = true, boardSort = sort)
        if (board.allOpen) {
            val item = allRows().getOrNull(index) ?: return null
            return gameActions(item.id, move = false, remove = null)
                .copy(select = true, showOnHome = item.id.takeIf { homeLanding(board, it) != null })
        }
        // In Folders a folder has one thing to say: whether its tile is on Home. An open menu
        // keeps the folder it opened on, however a library report moves Folders under it.
        if (board.foldersOpen) {
            val rows = folderRows()
            val entry = menuFolder?.let { id -> rows.firstOrNull { it.folder.id == id } } ?: rows.getOrNull(index) ?: return null
            return TileActions(folderOnHome = FolderSwitch(entry.folder.id, entry.onHome))
        }
        if (index < 0) return null
        val id = visibleSlots(board).getOrNull(index)
        if (open?.recent == true) {
            return id?.let { gameActions(it, move = false, remove = null).copy(select = true) }
        }
        val gaps = canTidy(board)
        val moves = open?.sorted != true
        // An empty slot, or one past the last tile, moves as a tile does, makes a collection on
        // the root, and closes the board's gaps.
        if (id == null) return TileActions(move = moves, newCollection = open == null, tidyBoard = gaps, boardSort = sort)
        if (id == HOME_ALL) return TileActions(move = true, newCollection = true, tidyBoard = gaps)
        val folder = board.folders[id]
        if (folder != null) {
            return TileActions(move = true, rename = true, icon = folder.userMade, remove = removeVerb(board, index), tidyBoard = gaps,
                sort = sortChoice(folder))
        }
        return gameActions(id, move = moves, remove = removeVerb(board, index), tidyBoard = gaps).copy(select = true, boardSort = sort)
    }

    /** The games in a folder sorted by Most played: the only ones whose play time a merge needs. */
    fun rankedByPlay(): Set<String> = board.folders.values
        .filter { it.sorted && it.sort == FolderSort.MostPlayed }
        .flatMapTo(HashSet()) { it.slots.filterNotNull() }

    /** The Sort row for [folder]. Recently played orders itself, so it has none. */
    private fun sortChoice(folder: HomeFolder): FolderSortChoice? =
        if (folder.recent) null else FolderSortChoice(folder.id, folder.sort)

    /**
     * Steps folder [folderId]'s sort by [direction]. The game the menu is on keeps the focus
     * wherever it lands; an empty slot stays empty, past the last game when sorting closed the
     * gap it was. [reread] runs once the sort is set, to read the play a new order needs
     * ([rankedByPlay]). Returns where focus goes.
     */
    fun stepSort(folderId: String, direction: Int, index: Int, reread: () -> Unit = {}): Int {
        val folder = board.folders[folderId] ?: return index
        val focused = menuGame ?: visibleSlots(board).getOrNull(index)
        val next = setFolderSort(board, folderId, folder.sort.stepped(direction), catalog())
        if (next == board) return index
        board = next
        reread()
        if (board.openFolderId != folderId) return index
        val slots = visibleSlots(board)
        val last = slots.indexOfLast { it != null }
        val at = when {
            focused != null -> slots.indexOf(focused).takeIf { it >= 0 } ?: index
            slots.getOrNull(index) == null -> index
            else -> last + 1
        }
        return at.coerceAtMost(GridStrip.reach(last + 1) - 1).coerceAtLeast(0)
    }

    private fun gameActions(id: String, move: Boolean, remove: RemoveVerb?, tidyBoard: Boolean = false): TileActions = TileActions(
        tidyBoard = tidyBoard,
        move = move,
        collections = collectionChoices(board, id),
        remove = remove,
        hide = id !in board.hidden,
        moveTo = otherAppFolder(board, id),
        removeDownload = downloaded(id),
        deleteShortcut = shelfById[id]?.let { it.libraryId in setOf("moonlight", "droiddeck") && it.remoteKey != null && it.shortcut } == true,
        game = id,
    )

    /**
     * What Y offers for [id] among Find's results. A game or app is offered where it lives on
     * Home, then what it is offered in All. Hidden items offer Show, and visible ones Hide.
     * One of Foldcade's folders offers its On Home switch, and a retired group Show.
     */
    fun findActions(id: String, hidden: Boolean = id in board.hidden || id in board.retired): TileActions {
        // One of Foldcade's folders has the switch Folders gives it, and on Home, the way to its tile.
        folderRows().firstOrNull { it.folder.id == id }?.let { entry ->
            return TileActions(folderOnHome = FolderSwitch(id, entry.onHome), showOnHome = id.takeIf { homeLanding(board, it) != null })
        }
        if (id in board.retired) return TileActions(show = true, game = id)
        val shown = id.takeIf { homeLanding(board, it) != null }
        if (board.folders[id] != null) return TileActions(showOnHome = shown)
        return gameActions(id, move = false, remove = null)
            .copy(showOnHome = shown.takeUnless { hidden }, show = hidden, hide = !hidden)
    }

    /**
     * Everything Find can list: each game and app the catalog knows, hidden ones marked, the
     * groups on the root by their names, and Foldcade's folders off Home, which A opens there.
     */
    fun findEntries(): List<FindEntry> {
        val items = catalog().filter { !it.placeholder }.map { item ->
            val platform = if (item.kind == HomeKind.Rom) resolvePlatform(platforms, item.platformId) else null
            FindEntry(
                id = item.id,
                title = item.title,
                kind = if (item.kind.isApp) FindKind.App else FindKind.Game,
                platformId = platform?.id ?: item.platformId?.takeIf { item.kind == HomeKind.Rom && it.isNotBlank() },
                platformName = platform?.name,
                hidden = item.id in board.hidden,
                lastPlayedMillis = lastPlayed(item.id) ?: item.lastPlayedMillis,
                mark = item.mark,
            )
        }
        val groups = board.slots.mapNotNull { slot -> slot?.let { board.folders[it] } }.distinctBy { it.id }
            // A collection wears its look, a coloured card, and Recently played its clock, as on Home.
            .map { FindEntry(id = it.id, title = it.name, kind = FindKind.Group, mark = collectionLook(it)?.mark ?: it.mark) }
        val offHome = folderRows().filter { !it.onHome }
            .map { FindEntry(id = it.folder.id, title = it.folder.name, kind = FindKind.Group, mark = it.folder.mark) }
        return items + groups + offHome
    }

    /** Foldcade's folder [id] while it is off Home, or null for anything else. */
    private fun offHomeFolder(id: String): FolderEntry? = folderRows().firstOrNull { it.folder.id == id && !it.onHome }

    /**
     * Leaves whatever is open for the place [id] has on Home (see [homeLanding]): the root, or
     * the group that holds it, opened. Returns the tile to focus there, or null when [id] is
     * nowhere on Home and nothing changed.
     */
    fun reveal(id: String): Int? {
        val spot = homeLanding(board, id) ?: return null
        addingTo = null
        fromFolders = null
        finishSelection()
        closeTyping()
        iconPick = null
        board = closeFolders(closeAll(closeFolder(leaveEdit(board))))
        return when (spot) {
            is HomeLanding.Root -> spot.index.also { rootIndex = it }
            is HomeLanding.Inside -> {
                rootIndex = spot.rootIndex
                board = openFolder(board, spot.groupId)
                spot.index
            }
        }
    }

    /** Opens the group [id] from wherever the board is, as A on its root tile would. Null when it is not on the root. */
    fun openGroup(id: String): Int? {
        if (board.folders[id] == null) return null
        val at = reveal(id) ?: return null
        rootIndex = at
        board = openFolder(board, id)
        return 0
    }

    /**
     * Opens Foldcade's folder [id] off Home, as Find's A does, so its games can be browsed where
     * it is. Back from it goes to the root. Null when [id] is not one of them.
     */
    fun openOffHome(id: String): Int? {
        if (offHomeFolder(id) == null) return null
        showRoot()
        val opened = openAnyFolder(board, id, catalog(), platforms)
        if (opened.openFolderId != id) return null
        board = opened
        return 0
    }

    /** What X does to the tile at [index]. Null in All and Folders, or where X does nothing. */
    fun removeVerb(index: Int): RemoveVerb? = if (board.allOpen || board.foldersOpen) null else removeVerb(board, index)

    /** A server game with a copy on this device. */
    private fun downloaded(id: String): Boolean = copies(id).any { it.availability == Availability.Cached }

    /**
     * The game a menu effect acts on: the one it names ([gameId], see [TileActions.game]),
     * else the one Add to took out of the collection on screen, else the one at [index].
     */
    private fun gameAt(index: Int, gameId: String? = null): String? =
        (gameId?.takeIf { it != HOME_ALL && board.folders[it] == null } ?: menuGame ?: slotGame(index))?.let(::tileOf)

    /** The face of game [id], wherever it is, as a menu that kept its game shows it. */
    fun faceOf(id: String): HomeFace = faceFor(id)

    fun restore(snapshot: HomeBoard) { board = snapshot; menuGame = null; addingTo = null }
    fun ensureAppFolder(id: String) {
        if (id in board.folders) return
        board = app.foldcade.language.ensureAppFolder(board, id)
    }
    fun hide(id: String) { board = hideGame(board, id) }
    fun show(id: String) { board = showHidden(board, id, catalog(), platforms) }

    /** Edit home's switch for Recently played. Returns where focus goes: it stays. */
    fun setRecentOnHome(on: Boolean, index: Int): Int {
        board = setRecentOnHome(board, on, catalog(), platforms)
        return index
    }
    /**
     * Folders' switch for Foldcade's folder [folderId]. The folder stays where it is in Folders,
     * and so does the focus. Returns where focus goes.
     */
    fun setFolderOnHome(folderId: String, on: Boolean, index: Int): Int {
        board = setFolderOnHome(board, folderId, on, catalog(), platforms)
        return index
    }

    /** Whether Foldcade's folder [folderId] has its tile on Home. */
    fun folderOnHome(folderId: String): Boolean = groupOnHome(board, folderId)

    fun deleteShortcut(id: String) { board = deleteShortcut(board, id) }
    fun hiddenItems(): List<HomeItem> = hiddenItems(board, catalog(), platforms)

    /** The game at [index] in All or on the board. Null for a group, the All tile, or an empty slot. */
    private fun slotGame(index: Int): String? {
        if (board.foldersOpen) return null
        val id = if (board.allOpen) allRows().getOrNull(index)?.id else visibleSlots(board).getOrNull(index)
        return id?.takeIf { it != HOME_ALL && board.folders[it] == null }
    }

    /**
     * Ends a menu's hold on its game, so the next menu reads the focused slot again. True
     * when there was one, and the menu's rows need reading again.
     */
    fun keepMenu(id: String?, folder: String? = null) {
        menuGame = id?.takeIf { it != HOME_ALL && it !in board.folders }
        menuFolder = folder
    }

    fun releaseMenu(): Boolean {
        if (menuGame == null && menuFolder == null) return false
        menuGame = null
        menuFolder = null
        return true
    }

    /** Move: lifts the tile or empty slot at [index]. The D-pad walks it, A drops it, B puts it back. */
    fun startMove(index: Int) {
        if (index < 0 || board.foldersOpen) return
        board = pickUp(enterEdit(board), index)
        if (board.hold == null) board = leaveEdit(board)
    }

    /**
     * Puts the game at [index] in collection [folderId], or takes it out; Home ([app.foldcade.language.HOME_ROOT])
     * pins or unpins it, and a pin of a game nowhere on the board files it in its folder too.
     * The open menu keeps this game even when it leaves the slot on screen.
     */
    fun toggleCollection(index: Int, folderId: String, gameId: String? = null) {
        val id = gameAt(index, gameId) ?: return
        menuGame = id
        board = toggleCollection(board, folderId, id, catalog().firstOrNull { it.id == id }, platforms)
    }

    /**
     * A new collection, then its name field. On a game it holds that game, which stays where it
     * is, and so does the focus. On an empty root slot the collection takes that slot, and on the
     * All tile the first free one; the focus goes to it. Returns where focus goes.
     */
    fun newCollection(index: Int, gameId: String? = null): Int {
        val collectionId = nextUserFolderId(board)
        val count = board.folders.values.count { it.collection } + 1
        val name = "${Copy.collection} $count"
        val game = gameAt(index, gameId)
        if (game == null && !board.atRoot) return index
        board = newCollection(board, collectionId, name, at = index.takeIf { game == null }, member = game)
        if (collectionId !in board.folders) return index
        val focus = if (game == null) board.slots.indexOf(collectionId).coerceAtLeast(0) else index
        openNaming(collectionId, focus, fresh = true)
        return focus
    }

    /** Opens the keyboard on the name of the folder or collection at [index], all of it selected. */
    fun startRename(index: Int) {
        val id = visibleSlots(board).getOrNull(index) ?: return
        if (id !in board.folders) return
        openNaming(id, index, fresh = false)
    }

    /**
     * The keyboard over [id]'s name; focus goes back to [cell] when it closes. Quick names
     * offer the platform its games share, then ideas for a collection, leaving out names
     * the user already gave another collection. A [fresh] one starts the cursor on them.
     */
    private fun openNaming(id: String, cell: Int, fresh: Boolean) {
        val folder = board.folders[id] ?: return
        val shared = folder.slots.filterNotNull()
            .map { slot -> platformName(catalog().firstOrNull { it.id == slot }?.platformId ?: games[slot]?.platformId) }
            .distinct()
            .singleOrNull()
            ?.takeIf { it.isNotEmpty() }
        val taken = board.folders.values.filter { it.collection && it.id != id }.map { it.name }
        renamingId = id
        renameCell = cell
        typing = TypingSession(
            text = folder.name,
            chips = quickNames(listOfNotNull(shared), taken),
            startOnChips = fresh,
            onDone = { name ->
                board = renameFolder(board, id, name)
                closeTyping()
            },
            onCancel = ::closeTyping,
        )
    }

    /** A tap on the open keyboard. Null when none is open. */
    fun tapTyping(at: KeyFocus): HomeStep? = typed { it.press(at) }

    /** Characters from a hardware keyboard, into the open keyboard's name. Null when none is open. */
    fun typeText(text: String): HomeStep? = typed { it.typeText(text) }

    private fun closeTyping() {
        renamingId = null
        typing = null
    }

    /** Opens the icon picker for the collection at [index]. */
    fun pickIcon(index: Int) {
        val id = visibleSlots(board).getOrNull(index) ?: return
        val look = board.folders[id]?.takeIf { it.userMade }?.let(::collectionLook) ?: return
        iconPick = IconPick.open(id, look)
        iconCell = index
    }

    /**
     * Closes the gaps on the board on screen. The focus keeps its tile, which may have moved
     * up the strip, or stays in its slot when that was empty. Returns where focus goes.
     */
    fun tidyBoard(index: Int): Int {
        val focused = visibleSlots(board).getOrNull(index)
        board = tidy(board)
        val slots = visibleSlots(board)
        val at = focused?.let { slots.indexOf(it) }?.takeIf { it >= 0 } ?: index
        return at.coerceAtMost(GridStrip.reach(slots.indexOfLast { it != null } + 1) - 1).coerceAtLeast(0)
    }

    /** Does what X says for the tile at [index] (see [removeVerb]). Returns where focus goes. */
    fun remove(index: Int): Int {
        if (board.foldersOpen) return index
        board = removeFromHome(board, index)
        return index.coerceAtMost((visibleCount() - 1).coerceAtLeast(0))
    }

    /**
     * Places catalog item [id] on Home in its own folder, the way Add to Home in
     * All does. The folder comes back if it went away. A hidden id comes back.
     * False when nothing changed.
     */
    fun place(id: String): Boolean {
        val item = catalog().firstOrNull { it.id == id } ?: return false
        val next = addItemToHome(board, item, platforms)
        if (next == board) return false
        board = next
        return true
    }

    /**
     * Places a catalog item at the chosen destination: a pin on Home, a collection, or a folder,
     * which it then sits in instead of its old one. A hidden item comes back.
     */
    fun placeIn(id: String, folderId: String?): Boolean {
        if (catalog().none { it.id == id }) return false
        if (folderId != null && folderId !in board.folders) return false
        val next = fileGame(board, id, folderId, catalog(), platforms)
        if (next == board) return false
        board = next
        return true
    }

    /**
     * Leaves any folder, All, a lifted tile, a name field, or the icon editor, whose collection
     * keeps the look it had: nothing it showed was saved. Returns the root tile to focus.
     */
    fun showRoot(): Int {
        addingTo = null
        fromFolders = null
        finishSelection()
        closeTyping()
        iconPick = null
        board = closeFolders(closeAll(closeFolder(cancelHold(board))))
        return rootIndex
    }

    /**
     * Opens the board's folder [id], as L1's Games and Apps do: from wherever the board is, as
     * A on its root tile would. One taken off Home opens where it is, and an Android folder the
     * board has not made yet is made. Returns where focus goes.
     */
    fun openShelf(id: String): Int {
        openGroup(id)?.let { return it }
        ensureAppFolder(id)
        if (id !in board.folders) return showRoot()
        if (id in board.slots) return openGroup(id) ?: showRoot()
        showRoot()
        board = openFolder(board, id)
        return 0
    }

    /** A shelf opened from a nested view keeps that view's original root opener. */
    fun rememberRootFocus(index: Int) {
        if (board.atRoot) rootIndex = index
    }

    fun showAll() {
        addingTo = null
        fromFolders = null
        finishSelection()
        closeTyping()
        board = openAll(board)
    }

    /**
     * Shows Folders in place of the board, from wherever it is, on its first folder. The board's
     * own focus is kept for Back. Returns where focus goes.
     */
    fun showFolders(): Int {
        showRoot()
        board = openFolders(board)
        return 0
    }

    fun applyAddNew(enabled: Boolean) {
        board = setAddNewToHome(board, enabled)
    }

    /** A touch drag walks the held tile, as the D-pad does. Letting go drops it, as A does. */
    fun drag(meaning: Meaning) {
        val hold = board.hold ?: return
        if (hold.overHome) return
        val to = neighbor(hold.index, meaning) ?: return
        board = moveHeld(board, to, catalog(), platforms)
    }

    fun encoded(): String = encodeHome(board)

    /**
     * The D-pad walks the colours and glyphs, and the tile wears each as it goes (see [IconPick]).
     * A keeps the look, B puts the old one back. Nothing is saved until A.
     */
    private fun handleIconPick(pick: IconPick, meaning: Meaning): HomeStep {
        when (meaning) {
            Meaning.Activate -> {
                board = setFolderIcon(board, pick.folderId, pick.look.glyph, pick.look.tint)
                settled = pick.folderId to System.currentTimeMillis()
                iconPick = null
            }
            Meaning.Back, Meaning.CancelHold -> iconPick = null
            else -> iconPick = pick.step(meaning)
        }
        return step(null, iconCell)
    }

    /** Every key goes to the open keyboard. Focus stays where naming began, saved or not. */
    private fun typed(press: (TypingSession) -> TypingStep): HomeStep? {
        val session = typing ?: return null
        val cue = press(session).cue
        return step(null, renameCell).copy(cue = cue)
    }

    /**
     * A tile or gap lifted with Move. The D-pad walks it, and passes over a group that would
     * take it; A or Y drops it, into that group when it is over one. Inside a group, Up from
     * the top row raises a game to the Home target, and a drop there carries it out onto the
     * root, still held. B puts everything back. A held direction stops at the last tile's
     * column, so it never races off into the empty strip; a fresh press goes on.
     */
    private fun handleMove(meaning: Meaning, index: Int, repeatCount: Int): HomeStep {
        val hold = board.hold
        if (hold == null) {
            board = leaveEdit(board)
            return HomeStep(handled = false, effect = null, focus = null)
        }
        return when (meaning) {
            Meaning.MoveLeft, Meaning.MoveRight, Meaning.MoveUp, Meaning.MoveDown -> {
                if (hold.overHome) {
                    // Down comes back to the slot it rose from. Nothing else leads off the target.
                    if (meaning == Meaning.MoveDown) board = moveHeld(board, hold.index, catalog(), platforms)
                    return step(null, hold.index)
                }
                if (meaning == Meaning.MoveUp && GridStrip.row(hold.index) == 0) {
                    if (repeatCount == 0) board = raiseToHome(board)
                    return step(null, hold.index)
                }
                // Held, the tile steps on every other repeat, so each swap can be seen.
                if (repeatCount % CARRY_REPEAT != 0) return step(null, hold.index)
                val to = neighbor(hold.index, meaning) ?: return step(null, hold.index)
                if (repeatCount > 0 && GridStrip.column(to) > GridStrip.column(lastTile())) return step(null, hold.index)
                board = moveHeld(board, to, catalog(), platforms)
                step(null, board.hold?.index ?: to)
            }
            Meaning.Activate -> {
                board = app.foldcade.language.drop(board, catalog(), platforms)
                val carried = board.hold
                if (carried != null) {
                    // Carried out of a group: the walk goes on along the root, from the group's slot.
                    rootIndex = carried.index
                    return step(null, carried.index)
                }
                board = leaveEdit(board)
                step(null, hold.index)
            }
            Meaning.CancelHold, Meaning.Back -> {
                val origin = heldStart(hold)
                board = leaveEdit(board)
                step(null, origin)
            }
            else -> step(null, hold.index)
        }
    }

    /** Where the held tile was lifted. For one carried out of a group, that is in the group cancel reopens. */
    private fun heldStart(hold: HomeHold): Int {
        val start = hold.start ?: return hold.origin
        val id = heldId(board) ?: return hold.origin
        val slots = start.openFolderId?.let { start.folders[it]?.slots } ?: start.slots
        return slots.indexOf(id).coerceAtLeast(0)
    }

    /** The slots the held tile walks, as they were at pickup. */
    private fun heldBoard(): List<String?> {
        val hold = board.hold ?: return emptyList()
        return hold.openFolderId?.let { hold.folders[it]?.slots } ?: hold.slots
    }

    /** The last tile on [heldBoard]. */
    private fun lastTile(): Int = heldBoard().indexOfLast { it != null }.coerceAtLeast(0)

    /** What a drop does now, for the hint row. Null while nothing is held. */
    fun holdDrop(): HoldDrop? = holdDrop(board)

    /**
     * The carried tile, for the grid to draw in the hand: the slot its own tile sits in, where
     * it is held now, and the group it is over. Null while nothing, or only a gap, is held.
     */
    fun carry(): HomeCarry? {
        val hold = board.hold ?: return null
        val id = heldId(board) ?: return null
        val slot = visibleSlots(board).indexOf(id).takeIf { it >= 0 } ?: return null
        return HomeCarry(
            id = id,
            slot = slot,
            at = hold.index,
            over = heldOver(board)?.let { visibleSlots(board).indexOf(it) }?.takeIf { it >= 0 },
            overHome = hold.overHome,
            canTakeOut = canTakeOut(board),
        )
    }

    private fun handleAll(meaning: Meaning, index: Int): HomeStep {
        return when (meaning) {
            Meaning.Activate -> activateCell(index)
            Meaning.ActivateBottom -> activateCell(index, onBottom = true)
            Meaning.Back -> {
                val back = addingTo?.takeIf { it in board.folders }
                addingTo = null
                if (back != null) {
                    board = openFolder(board, back)
                    return step(null, 0)
                }
                board = closeAll(board)
                step(null, rootIndex)
            }
            Meaning.LetterForward, Meaning.LetterBackward,
            Meaning.Menu, Meaning.LeftPanel, Meaning.RightPanel, Meaning.Options -> HomeStep(handled = false, effect = null, focus = null)
            Meaning.MoveUp, Meaning.MoveDown, Meaning.MoveLeft, Meaning.MoveRight,
            Meaning.PageTowardStart, Meaning.PageTowardEnd,
            ->
                HomeStep(handled = false, effect = null, focus = null)
            else -> step(null, index)
        }
    }

    /** A opens a folder or launches on the top screen. X opens a folder too, or launches on the bottom. */
    private fun activateCell(index: Int, onBottom: Boolean = false): HomeStep {
        val face = face(index) ?: return step(null, index)
        val id = face.id ?: return step(null, index)
        if (face.allTile) {
            rootIndex = index
            addingTo = null
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
        val back = fromFolders
        fromFolders = null
        if (back != null) {
            board = openFolders(closeFolder(board))
            return step(null, folderRows().indexOfFirst { it.folder.id == back }.coerceAtLeast(0))
        }
        val restore = rootIndex
        board = closeFolder(board)
        return step(null, restore)
    }

    /**
     * Folders: A or X opens the folder, on Home or off it, and Back from it comes back here.
     * B goes back to the board. The D-pad and triggers are the grid's, as in All.
     */
    private fun handleFolders(meaning: Meaning, index: Int): HomeStep = when (meaning) {
        Meaning.Activate, Meaning.ActivateBottom -> {
            val entry = folderRows().getOrNull(index)
            val opened = entry?.let { openAnyFolder(board, it.folder.id, catalog(), platforms) }
            if (opened != null && opened.openFolderId == entry.folder.id) {
                board = opened
                fromFolders = entry.folder.id
                step(null, 0)
            } else step(null, index)
        }
        Meaning.Back -> {
            board = closeFolders(board)
            step(null, rootIndex)
        }
        Meaning.LetterForward, Meaning.LetterBackward,
        Meaning.Menu, Meaning.LeftPanel, Meaning.RightPanel, Meaning.Options,
        Meaning.MoveUp, Meaning.MoveDown, Meaning.MoveLeft, Meaning.MoveRight,
        Meaning.PageTowardStart, Meaning.PageTowardEnd,
        -> HomeStep(handled = false, effect = null, focus = null)
        else -> step(null, index)
    }

    /** What Folders lists, as the board and catalog stand now. Every face and menu asks, so one reading serves a change. */
    fun folderRows(): List<FolderEntry> {
        folderCache?.let { (from, merged, rows) -> if (from === board && merged == merges) return rows }
        return foldcadeFolders(board, catalog(), platforms).also { folderCache = Triple(board, merges, it) }
    }

    private var folderCache: Triple<HomeBoard, Int, List<FolderEntry>>? = null

    /** Folders' tiles. A folder whose tile is on Home wears All's on-Home dot; one off Home is dimmed. */
    private fun folderFaces(): List<HomeFace> = folderRows().map { entry ->
        val folder = entry.folder
        HomeFace(
            id = folder.id,
            title = folder.name,
            shortText = folderCountLine(entry.count, apps = folder.holdsApps),
            mark = folder.mark,
            empty = false,
            folder = true,
            allTile = false,
            onGrid = entry.onHome,
            platformId = folder.platformId,
            androidPackage = null,
            occupiesBoth = false,
        )
    }

    /** Where game [id] sits in All, or -1 when All does not list it. */
    fun allIndexOf(id: String): Int = allRows().indexOfFirst { it.id == id }

    private fun allRows(): List<HomeItem> =
        allItems(catalog().map { it.copy(lastPlayedMillis = lastPlayed(it.id) ?: it.lastPlayedMillis) }, sort)

    private fun allFaces(): List<HomeFace> =
        allRows().map { item -> faceFor(item.id).copy(onGrid = onHome(board, item.id)) }

    private fun faceFor(id: String?): HomeFace {
        if (id == null) {
            return HomeFace(
                id = null,
                title = "",
                shortText = "",
                mark = null,
                empty = true,
                folder = false,
                allTile = false,
                onGrid = false,
                platformId = null,
                androidPackage = null,
                occupiesBoth = false,
            )
        }
        if (id == HOME_ALL) {
            return HomeFace(
                id = id,
                title = Copy.allLibrary,
                shortText = folderCountLine(allGames),
                mark = MARK_LIBRARY,
                empty = false,
                folder = false,
                allTile = true,
                onGrid = true,
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
                shortText = folderCountLine(count, apps = folder.holdsApps),
                // A collection wears its folder card; the one being edited shows the focused choice.
                mark = (iconPick?.takeIf { it.folderId == id }?.look ?: collectionLook(folder))?.mark ?: folder.mark,
                empty = false,
                folder = true,
                allTile = false,
                onGrid = true,
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
            allTile = false,
            onGrid = true,
            platformId = item?.platformId ?: game?.platformId,
            androidPackage = id.removePrefix("android:").takeIf { id.startsWith("android:") },
            occupiesBoth = false,
            onServer = copies(id).let { it.isNotEmpty() && it.all { copy -> copy.availability == Availability.RemoteOnly } },
        )
    }

    /** The platform's display name. An id with no listed platform is not shown. */
    private fun platformName(id: String?): String {
        if (id == null) return ""
        return platforms.firstOrNull { it.id == id || id in it.aliases }?.name.orEmpty()
    }

    /**
     * Where a lifted tile goes. It can walk past the last tile, into empty slots along the
     * strip, as far as the cursor reaches past the tiles there were when it was lifted.
     */
    private fun neighbor(index: Int, meaning: Meaning): Int? {
        val rows = Metrics.rows
        val column = GridStrip.column(index)
        val row = GridStrip.row(index)
        val target = when (meaning) {
            Meaning.MoveLeft -> if (column == 0) null else index - rows
            Meaning.MoveRight -> index + rows
            Meaning.MoveUp -> if (row == 0) null else index - 1
            Meaning.MoveDown -> if (row == rows - 1) null else index + 1
            else -> null
        } ?: return null
        val lifted = heldBoard().indexOfLast { it != null } + 1
        return target.takeIf { it < GridStrip.reach(lifted) }
    }

    private fun focusIndex(): Int = board.hold?.index ?: rootIndex

    private fun step(effect: Effect?, index: Int): HomeStep =
        HomeStep(
            handled = true,
            effect = effect,
            focus = GridFocus(cellIndex = index.coerceAtLeast(0), lastColumn = GridStrip.column(index.coerceAtLeast(0))),
        )
}

/**
 * A tile in the hand, as the grid draws it. [slot] is where its own tile sits, which a drop
 * would leave, and [at] where it is held: the same slot, or a group it hovers over ([over]).
 * [overHome] holds it over the Home target, which [canTakeOut] says the group on screen has.
 */
data class HomeCarry(
    val id: String,
    val slot: Int,
    val at: Int,
    val over: Int?,
    val overHome: Boolean,
    val canTakeOut: Boolean,
)

/** One library report to [HomeSession.remerge], kept while a tile is held. */
private data class PendingMerge(
    val shelf: List<HomeItem>,
    val apps: List<HomeItem>,
    val library: List<HomeItem>,
    val platforms: List<HomePlatform>,
    val remembered: Map<String, Game>,
    val unsettled: (String) -> Boolean,
    val hidden: Set<String>,
    val sameGames: Map<String, String>,
    val copies: Map<String, List<Game>>,
)

/** A held D-pad walks a carried tile one slot per this many key repeats. */
private const val CARRY_REPEAT = 2

/** [cue] is the open keyboard's sound for this press. Null leaves the sound to the meaning. */
data class HomeStep(
    val handled: Boolean,
    val effect: Effect?,
    val focus: GridFocus?,
    val cue: TypingCue? = null,
)
