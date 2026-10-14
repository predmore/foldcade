package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.language.LibrarySort
import app.foldcade.language.Copy
import app.foldcade.language.Effect
import app.foldcade.language.GridFocus
import app.foldcade.language.GridStrip
import app.foldcade.language.HOME_ALL
import app.foldcade.language.HOME_FAVORITES
import app.foldcade.language.FOLDER_ICONS
import app.foldcade.language.FolderChoice
import app.foldcade.language.RemoveVerb
import app.foldcade.language.TileActions
import app.foldcade.language.HomeBoard
import app.foldcade.language.HomeItem
import app.foldcade.language.HomeKeys
import app.foldcade.language.KeyFocus
import app.foldcade.language.TypingCue
import app.foldcade.language.TypingStep
import app.foldcade.language.quickNames
import app.foldcade.language.folderCountLine
import app.foldcade.language.HomeKind
import app.foldcade.language.HomePlatform
import app.foldcade.language.MARK_LIBRARY
import app.foldcade.language.Meaning
import app.foldcade.language.Metrics
import app.foldcade.language.addItemToHome
import app.foldcade.language.allItems
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
    var board: HomeBoard = decodeHome(raw, nowMillis)
        private set

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
    ) {
        shelf = shelfItems
        apps = appItems
        library = libraryItems
        platforms = platformList
        remembered.forEach { (id, game) -> games[id] = game }
        board = mergeHome(board, catalog(), platforms, unsettled)
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
        board.allOpen -> HomeKeys.AllLibrary
        else -> HomeKeys.Idle
    }

    fun animToken(): String = "${board.openFolderId}:${board.allOpen}"

    /** True when this meaning was applied. False lets the shared grid reducer move focus. */
    fun handle(meaning: Meaning, index: Int): HomeStep? {
        iconPick?.let { return handleIconPick(it, meaning) }
        typed { it.handle(meaning) }?.let { return it }
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
        val held = menuGame
        if (held != null && slotGame(index) != held) return gameActions(held, move = false, pin = false, remove = null)
        if (board.allOpen) {
            val item = allRows().getOrNull(index) ?: return null
            return gameActions(item.id, move = false, pin = !pinned(board, item.id), remove = null)
        }
        if (index < 0) return null
        val open = board.openFolderId?.let { board.folders[it] }
        val id = visibleSlots(board).getOrNull(index)
        if (open?.recent == true) {
            return id?.let { gameActions(it, move = false, pin = !pinned(board, it), remove = null) }
        }
        // An empty slot, or one past the last tile, moves as a tile does, and makes a collection on the root.
        if (id == null) return TileActions(move = true, newCollection = open == null)
        if (id == HOME_ALL) return TileActions(move = true, newCollection = true)
        val folder = board.folders[id]
        if (folder != null) {
            return TileActions(move = true, rename = true, icon = folder.userMade, remove = removeVerb(board, index))
        }
        return gameActions(id, move = true, pin = open?.system == true && !pinned(board, id), remove = removeVerb(board, index))
    }

    private fun gameActions(id: String, move: Boolean, pin: Boolean, remove: RemoveVerb?): TileActions = TileActions(
        move = move,
        collections = collectionChoices(board, id),
        favorite = favorite(board, id),
        pin = pin,
        remove = remove,
        removeDownload = downloaded(id),
        game = id,
    )

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

    /** The game at [index] in All or on the board. Null for a group, the All tile, or an empty slot. */
    private fun slotGame(index: Int): String? {
        val id = if (board.allOpen) allRows().getOrNull(index)?.id else visibleSlots(board).getOrNull(index)
        return id?.takeIf { it != HOME_ALL && board.folders[it] == null }
    }

    /**
     * Ends a menu's hold on its game, so the next menu reads the focused slot again. True
     * when there was one, and the menu's rows need reading again.
     */
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
        val folder = board.folders[id]?.takeIf { it.userMade } ?: return
        iconPick = FolderIconPick(id, FOLDER_ICONS.indexOf(folder.mark).coerceAtLeast(0))
        iconCell = index
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

    /** Leaves any folder, All, a lifted tile, or a name field. Returns the root tile to focus. */
    fun showRoot(): Int {
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
        closeTyping()
        board = openAll(board)
    }

    fun applyAddNew(enabled: Boolean) {
        board = setAddNewToHome(board, enabled)
    }

    fun drag(meaning: Meaning) {
        val hold = board.hold ?: return
        val to = neighbor(hold.index, meaning) ?: return
        board = moveHeld(board, to, catalog(), platforms)
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

    /** Every key goes to the open keyboard. Focus stays where naming began, saved or not. */
    private fun typed(press: (TypingSession) -> TypingStep): HomeStep? {
        val session = typing ?: return null
        val cue = press(session).cue
        return step(null, renameCell).copy(cue = cue)
    }

    /**
     * A tile or gap lifted with Move. The D-pad walks it, and onto a folder a game drops in.
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
                board = moveHeld(board, to, catalog(), platforms)
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
                mark = folder.mark,
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
        val lifted = visibleSlots(cancelHold(board)).indexOfLast { it != null } + 1
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

/** The icon picker for one folder the user made. [index] is the focused icon in [FOLDER_ICONS]. */
data class FolderIconPick(val folderId: String, val index: Int) {
    companion object {
        /** The picker lays the icons out in rows of this many. */
        const val COLUMNS: Int = 7
    }
}

/** [cue] is the open keyboard's sound for this press. Null leaves the sound to the meaning. */
data class HomeStep(
    val handled: Boolean,
    val effect: Effect?,
    val focus: GridFocus?,
    val cue: TypingCue? = null,
)
