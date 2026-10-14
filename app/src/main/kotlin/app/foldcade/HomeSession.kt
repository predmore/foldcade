package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.language.HomeSelection
import app.foldcade.language.BulkAction
import app.foldcade.language.bulkCollectionChoices
import app.foldcade.language.bulkCollection
import app.foldcade.language.bulkNewCollection
import app.foldcade.language.bulkHide
import app.foldcade.language.LibrarySort
import app.foldcade.language.Copy
import app.foldcade.language.LaunchableApp
import app.foldcade.language.AppShelfState
import app.foldcade.language.HomeKind
import app.foldcade.language.hideGame
import app.foldcade.language.showHidden
import app.foldcade.language.hiddenItems
import app.foldcade.language.deleteShortcut
import app.foldcade.language.Effect
import app.foldcade.language.GridFocus
import app.foldcade.language.GridStrip
import app.foldcade.language.HOME_ALL
import app.foldcade.language.HOME_FAVORITES
import app.foldcade.language.IconPick
import app.foldcade.language.collectionLook
import app.foldcade.language.FolderChoice
import app.foldcade.language.FindEntry
import app.foldcade.language.FindKind
import app.foldcade.language.HomeLanding
import app.foldcade.language.homeLanding
import app.foldcade.language.resolvePlatform
import app.foldcade.language.RemoveVerb
import app.foldcade.language.TileActions
import app.foldcade.language.HomeBoard
import app.foldcade.language.HomeItem
import app.foldcade.language.HomeHold
import app.foldcade.language.HomeKeys
import app.foldcade.language.KeyFocus
import app.foldcade.language.TypingCue
import app.foldcade.language.TypingStep
import app.foldcade.language.quickNames
import app.foldcade.language.folderCountLine
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
import app.foldcade.language.encodeHome
import app.foldcade.language.enterEdit
import app.foldcade.language.favorite
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
import app.foldcade.language.pinItemToHome
import app.foldcade.language.pinToHome
import app.foldcade.language.pinned
import app.foldcade.language.removeFromHome
import app.foldcade.language.removeVerb
import app.foldcade.language.renameFolder
import app.foldcade.language.setFolderIcon
import app.foldcade.language.setAddNewToHome
import app.foldcade.language.toggleCollection
import app.foldcade.language.toggleFavorite
import app.foldcade.language.heldId
import app.foldcade.language.heldOver
import app.foldcade.language.holdDrop
import app.foldcade.language.raiseToHome
import app.foldcade.language.tidy
import app.foldcade.language.visibleSlots

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
class HomeSession(raw: String?, nowMillis: Long = System.currentTimeMillis()) {
    /**
     * The board on screen. While a tile is held, the walk is measured against the board as it
     * was at pickup, so a library's report then waits in [pendingMerge]: merged into that walk,
     * it would be undone by the next step. Selection also keeps its view steady until it ends.
     * Putting a tile down, cancelling, or finishing selection merges the latest report.
     */
    var board: HomeBoard = decodeHome(raw, nowMillis).let { it.copy(collectionSerial = app.foldcade.language.collectionHighWater(it)) }
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

    /** Undo reverses the batch before deferred catalog arrivals are merged into it. */
    var bulkAfter: HomeBoard? = null
        private set

    fun bulk(action: BulkAction, ids: List<String>, collectionId: String?, index: Int): Int {
        if (selection == null || ids.isEmpty()) return index
        val name = "${Copy.collection} ${board.folders.values.count { it.collection && it.id != HOME_FAVORITES } + 1}"
        val made = nextUserFolderId(board)
        board = when (action) {
            BulkAction.Collection -> collectionId?.let { bulkCollection(board, it, ids) } ?: board
            BulkAction.NewCollection -> bulkNewCollection(board, made, name, ids)
            BulkAction.Hide -> bulkHide(board, ids)
            BulkAction.Favorite -> ids.fold(board) { next, id -> if (favorite(next, id)) next else toggleFavorite(next, id) }
            BulkAction.Pin -> ids.filter { !pinned(board, it) }.fold(board) { next, id ->
                catalog().firstOrNull { it.id == id }?.let { pinItemToHome(next, it, platforms) } ?: pinToHome(next, id)
            }
        }
        bulkAfter = board
        finishSelection()
        if (action == BulkAction.NewCollection && made in board.folders) openNaming(made, index, fresh = true)
        return index
    }

    private fun bulkActions(): TileActions {
        val ids = selectedIds()
        if (ids.isEmpty()) return TileActions()
        return TileActions(bulk = ids, collections = bulkCollectionChoices(board, ids), newCollection = true,
            favorite = false.takeIf { ids.any { !favorite(board, it) } },
            pin = ids.any { !pinned(board, it) }, remove = RemoveVerb.Hide.takeIf { ids.any { it !in board.hidden } })
    }

    var sort: LibrarySort = LibrarySort.Listed
    var lastPlayed: (String) -> Long? = { null }

    private var shelf: List<HomeItem> = emptyList()
    private var apps: List<HomeItem> = emptyList()
    private var library: List<HomeItem> = emptyList()
    private var platforms: List<HomePlatform> = emptyList()
    private val games = HashMap<String, Game>()
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

    /** The collection whose icon is being chosen, and what is focused. Null when closed. */
    var iconPick: IconPick? = null
        private set

    /** The collection that last took a new look, and when, so its tile can settle into it. */
    var settled: Pair<String, Long>? = null
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
        hidden: Set<String> = emptySet(),
    ) {
        val report = PendingMerge(shelfItems, appItems, libraryItems, platformList, remembered, unsettled, hidden)
        // A later report replaces one still waiting; the games it remembers add up.
        if (board.hold != null || selection != null) {
            pendingMerge = report.copy(remembered = pendingMerge?.remembered.orEmpty() + remembered)
            return
        }
        merge(report)
    }

    private fun merge(report: PendingMerge) {
        shelf = report.shelf
        apps = report.apps
        library = report.library
        platforms = report.platforms
        report.remembered.forEach { (id, game) -> games[id] = game }
        val reconciled = report.hidden.fold(board) { current, id -> hideGame(current, id) }
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

    fun catalog(): List<HomeItem> = (shelf + apps + library).distinctBy { it.id }

    fun visibleCount(): Int = faces().size

    fun face(index: Int): HomeFace? = faces().getOrNull(index)

    fun faces(): List<HomeFace> {
        if (board.allOpen) return allFaces()
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

    fun animToken(): String = "${board.openFolderId}:${board.allOpen}"

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
        return when (meaning) {
            Meaning.Activate -> activateCell(index)
            Meaning.ActivateBottom -> activateCell(index, onBottom = true)
            Meaning.Back -> if (board.openFolderId != null) closeOpenFolder() else null
            else -> null
        }
    }

    /**
     * What Y offers for the tile at [index]. A game can always join collections and be a
     * favorite; where it sits decides the rest (see [removeVerb]). In All, a game not pinned
     * can be, which brings a hidden one back. A group can move, be renamed and come off Home,
     * and a collection takes an icon. An empty slot moves, and on the root makes a collection.
     * A downloaded server game can be deleted from anywhere. While Add to has taken the game
     * out of the collection on screen, the menu keeps acting on that game. Null while a tile
     * moves or a picker or name field is open.
     */
    fun actions(index: Int): TileActions? {
        if (iconPick != null || renamingId != null || board.editing) return null
        if (selection != null) return bulkActions()
        val held = menuGame
        if (held != null && slotGame(index) != held) return gameActions(held, move = false, pin = false, remove = null).copy(kept = true)
        if (board.allOpen) {
            val item = allRows().getOrNull(index) ?: return null
            return gameActions(item.id, move = false, pin = !pinned(board, item.id), remove = null)
                .copy(select = true, showOnHome = item.id.takeIf { homeLanding(board, it) != null })
        }
        if (index < 0) return null
        val open = board.openFolderId?.let { board.folders[it] }
        val id = visibleSlots(board).getOrNull(index)
        if (open?.recent == true) {
            return id?.let { gameActions(it, move = false, pin = !pinned(board, it), remove = null).copy(select = true) }
        }
        val gaps = canTidy(board)
        // An empty slot, or one past the last tile, moves as a tile does, makes a collection on
        // the root, and closes the board's gaps.
        if (id == null) return TileActions(move = true, newCollection = open == null, tidy = gaps, tidyBoard = gaps)
        if (id == HOME_ALL) return TileActions(move = true, newCollection = true, tidyBoard = gaps)
        val folder = board.folders[id]
        if (folder != null) {
            return TileActions(move = true, rename = true, icon = folder.userMade, remove = removeVerb(board, index), tidyBoard = gaps)
        }
        return gameActions(id, move = true, pin = open?.system == true && !pinned(board, id), remove = removeVerb(board, index), tidyBoard = gaps).copy(select = true)
    }

    private fun gameActions(id: String, move: Boolean, pin: Boolean, remove: RemoveVerb?, tidyBoard: Boolean = false): TileActions = TileActions(
        tidyBoard = tidyBoard,
        move = move,
        collections = collectionChoices(board, id),
        favorite = favorite(board, id),
        pin = pin,
        remove = remove,
        removeDownload = downloaded(id),
        deleteShortcut = shelfById[id]?.libraryId in setOf("moonlight", "droiddeck") && shelfById[id]?.remoteKey != null,
        game = id,
    )

    /**
     * What Y offers for [id] among Find's results. A game or app is offered where it lives on
     * Home, then what it is offered in All. Hidden items offer Show; visible games and apps
     * offer Hide, and a pin when not pinned. A retired group offers Show too.
     */
    fun findActions(id: String, hidden: Boolean = id in board.hidden || id in board.retired): TileActions {
        if (id in board.retired) return TileActions(show = true, game = id)
        val shown = id.takeIf { homeLanding(board, it) != null }
        if (board.folders[id] != null) return TileActions(showOnHome = shown)
        return gameActions(id, move = false, pin = !hidden && !pinned(board, id),
            remove = RemoveVerb.Hide.takeUnless { hidden }).copy(showOnHome = shown.takeUnless { hidden }, show = hidden)
    }

    /**
     * Everything Find can list: each game and app the catalog knows, hidden ones marked, and
     * the groups on the root by their names. [shelfHidden] says which apps their shelf hides.
     */
    fun findEntries(shelfHidden: (String) -> Boolean = { false }): List<FindEntry> {
        val items = catalog().filter { !it.placeholder }.map { item ->
            val platform = if (item.kind == HomeKind.Rom) resolvePlatform(platforms, item.platformId) else null
            FindEntry(
                id = item.id,
                title = item.title,
                kind = if (item.kind == HomeKind.AndroidApp || item.kind == HomeKind.Emulator) FindKind.App else FindKind.Game,
                platformId = platform?.id ?: item.platformId?.takeIf { item.kind == HomeKind.Rom && it.isNotBlank() },
                platformName = platform?.name,
                hidden = item.id in board.hidden || shelfHidden(item.id),
                lastPlayedMillis = lastPlayed(item.id) ?: item.lastPlayedMillis,
                mark = item.mark,
            )
        }
        val groups = board.slots.mapNotNull { slot -> slot?.let { board.folders[it] } }.distinctBy { it.id }
            // A collection wears its look, a coloured card, and Recently played its clock, as on Home.
            .map { FindEntry(id = it.id, title = it.name, kind = FindKind.Group, mark = collectionLook(it)?.mark ?: it.mark) }
        return items + groups
    }

    /**
     * Leaves whatever is open for the place [id] has on Home (see [homeLanding]): the root, or
     * the group that holds it, opened. Returns the tile to focus there, or null when [id] is
     * nowhere on Home and nothing changed.
     */
    fun reveal(id: String): Int? {
        val spot = homeLanding(board, id) ?: return null
        finishSelection()
        closeTyping()
        iconPick = null
        board = closeAll(closeFolder(leaveEdit(board)))
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

    /** What X does to the tile at [index]. Null in All, or where X does nothing. */
    fun removeVerb(index: Int): RemoveVerb? = if (board.allOpen) null else removeVerb(board, index)

    /** A server game with a copy on this device. */
    private fun downloaded(id: String): Boolean = games[id]?.availability == Availability.Cached

    /**
     * The game a menu effect acts on: the one it names ([gameId], see [TileActions.game]),
     * else the one Add to took out of the collection on screen, else the one at [index].
     */
    private fun gameAt(index: Int, gameId: String? = null): String? =
        gameId?.takeIf { it != HOME_ALL && board.folders[it] == null } ?: menuGame ?: slotGame(index)

    /** The face of game [id], wherever it is, as a menu that kept its game shows it. */
    fun faceOf(id: String): HomeFace = faceFor(id)

    fun restore(snapshot: HomeBoard) { board = snapshot; menuGame = null }
    fun ensureAppFolder(id: String) {
        if (id in board.folders) return
        board = app.foldcade.language.ensureAppFolder(board, id)
    }
    fun hide(id: String) { board = hideGame(board, id) }
    fun show(id: String) { board = showHidden(board, id, catalog(), platforms) }
    fun deleteShortcut(id: String) { board = deleteShortcut(board, id) }
    fun shelfActions(id: String): TileActions = gameActions(id, false, !pinned(board, id), RemoveVerb.Hide)
    fun hiddenItems(apps: List<LaunchableApp>, state: AppShelfState, players: Set<String>): List<HomeItem> =
        hiddenItems(board, catalog(), apps, state, players, platforms)

    /** The game at [index] in All or on the board. Null for a group, the All tile, or an empty slot. */
    private fun slotGame(index: Int): String? {
        val id = if (board.allOpen) allRows().getOrNull(index)?.id else visibleSlots(board).getOrNull(index)
        return id?.takeIf { it != HOME_ALL && board.folders[it] == null }
    }

    /**
     * Ends a menu's hold on its game, so the next menu reads the focused slot again. True
     * when there was one, and the menu's rows need reading again.
     */
    fun keepMenu(id: String?) {
        menuGame = id?.takeIf { it != HOME_ALL && it !in board.folders }
    }

    fun releaseMenu(): Boolean {
        if (menuGame == null) return false
        menuGame = null
        return true
    }

    /** Move: lifts the tile or empty slot at [index]. The D-pad walks it, A drops it, B puts it back. */
    fun startMove(index: Int) {
        if (index < 0) return
        board = pickUp(enterEdit(board), index)
        if (board.hold == null) board = leaveEdit(board)
    }

    /**
     * Puts the game at [index] in collection [folderId], or takes it out. Favorites is made
     * when missing. The open menu keeps this game even when it leaves the slot on screen.
     */
    fun toggleCollection(index: Int, folderId: String, gameId: String? = null) {
        val id = gameAt(index, gameId) ?: return
        menuGame = id
        board = toggleCollection(board, folderId, id)
    }

    fun toggleFavorite(index: Int, gameId: String? = null) {
        val id = gameAt(index, gameId) ?: return
        board = toggleFavorite(board, id)
    }

    /**
     * A pin for the game at [index] in the first free root slot. The focus stays on the game.
     * A game nowhere on the board is filed in its folder too.
     */
    fun pin(index: Int, gameId: String? = null) {
        val id = gameAt(index, gameId) ?: return
        val item = catalog().firstOrNull { it.id == id }
        board = if (item != null) pinItemToHome(board, item, platforms) else pinToHome(board, id)
    }

    /**
     * A new collection, then its name field. On a game it holds that game, which stays where it
     * is, and so does the focus. On an empty root slot the collection takes that slot, and on the
     * All tile the first free one; the focus goes to it. Returns where focus goes.
     */
    fun newCollection(index: Int, gameId: String? = null): Int {
        val collectionId = nextUserFolderId(board)
        val count = board.folders.values.count { it.collection && it.id != HOME_FAVORITES } + 1
        val name = "${Copy.collection} $count"
        val game = gameAt(index, gameId)
        val atRoot = !board.allOpen && board.openFolderId == null
        if (game == null && !atRoot) return index
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

    /** Where an app from a shelf can go (see [app.foldcade.language.placementChoices]). */
    fun placementDestinations(id: String): List<FolderChoice> =
        app.foldcade.language.placementChoices(board, catalog().firstOrNull { it.id == id }, platforms)

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
        finishSelection()
        closeTyping()
        iconPick = null
        board = closeAll(closeFolder(cancelHold(board)))
        return rootIndex
    }

    /** A shelf opened from a nested view keeps that view's original root opener. */
    fun rememberRootFocus(index: Int) {
        if (board.openFolderId == null && !board.allOpen) rootIndex = index
    }

    fun showAll() {
        finishSelection()
        closeTyping()
        board = openAll(board)
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
                shortText = "",
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
                shortText = folderCountLine(count, apps = folder.bucket == HomeKind.AndroidApp),
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
            onServer = game?.availability == Availability.RemoteOnly,
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
