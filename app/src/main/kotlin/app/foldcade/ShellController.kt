package app.foldcade

import android.util.Log
import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameActivity
import app.foldcade.api.plugin.Platform
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.SyncOutcome
import app.foldcade.api.plugin.SyncResult
import app.foldcade.artwork.ArtQuery
import app.foldcade.localfolder.LocalFolderBackend
import app.foldcade.plugins.droiddeck.DroidDeckLibrary
import app.foldcade.plugins.droiddeck.droidDeckSteamAppId
import app.foldcade.plugins.gamenative.GameNativeLibrary
import app.foldcade.plugins.gamenative.steamAppId
import app.foldcade.plugins.moonlight.MoonlightLibrary
import app.foldcade.plugins.moonlight.isMoonlightRemoteKey
import app.foldcade.host.PluginHost
import app.foldcade.language.FoldNotice
import app.foldcade.language.brightnessPrompt
import app.foldcade.language.brightnessPosition
import app.foldcade.language.settingsCategories
import app.foldcade.language.AndroidShelf
import app.foldcade.language.AppActions
import app.foldcade.language.AppShelfState
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.ConnectField
import app.foldcade.language.SidePanel
import app.foldcade.language.ConnectKind
import app.foldcade.language.Chrome
import app.foldcade.language.Copy
import app.foldcade.language.DialogKind
import app.foldcade.language.DialogState
import app.foldcade.language.HomeGrid
import app.foldcade.language.LaunchableApp
import app.foldcade.language.appsOn
import app.foldcade.language.hiddenApps
import app.foldcade.language.Effect
import app.foldcade.language.EmptyGrid
import app.foldcade.language.GridFocus
import app.foldcade.language.actionRows
import app.foldcade.language.chordIndex
import app.foldcade.language.GridKind
import app.foldcade.language.HeroFolder
import app.foldcade.language.HeroItem
import app.foldcade.language.HeroSubject
import app.foldcade.language.HomeItem
import app.foldcade.language.HomeKind
import app.foldcade.language.HomePlatform
import app.foldcade.language.androidHomeId
import app.foldcade.language.bucketMark
import app.foldcade.language.homeGameId
import app.foldcade.language.Motion
import app.foldcade.language.SyncMark
import app.foldcade.language.TileActivity
import app.foldcade.language.resolvePlatform
import app.foldcade.language.kitMark
import app.foldcade.host.play.recentlyPlayedIndices
import app.foldcade.language.HomeMusicSetting
import app.foldcade.language.MusicTrack
import app.foldcade.language.offeredMusicTracks
import app.foldcade.language.selectedMusicTrack
import app.foldcade.language.HostScreen
import app.foldcade.language.LibraryFolder
import app.foldcade.language.Meaning
import app.foldcade.language.MotionSpeed
import app.foldcade.language.GridStrip
import app.foldcade.language.shownFirstColumn
import app.foldcade.language.PromptKey
import app.foldcade.language.ThorStyle
import app.foldcade.language.faceMapWithConfirm
import app.foldcade.language.offerButtonCalibration
import app.foldcade.language.resolveFaceMap
import app.foldcade.language.PickerModel
import app.foldcade.language.PlayerSaveSetting
import app.foldcade.language.Side
import app.foldcade.language.SignedInBackend
import app.foldcade.language.connectFields
import app.foldcade.language.displayOrder
import app.foldcade.language.focusAndActivateDialog
import app.foldcade.language.finishIslandRetire
import app.foldcade.language.replaceIsland
import app.foldcade.language.homePrompt
import app.foldcade.language.panelRows
import app.foldcade.language.previewDialogState
import app.foldcade.language.shouldOfferUsageAccess
import app.foldcade.language.usageAccessPrompt
import app.foldcade.language.reduce
import app.foldcade.language.signInAgainPrompt
import app.foldcade.romm.cleartextCredentialWarning
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.coroutines.cancellation.CancellationException

class ShellController(
    private val store: SessionStore,
    private val plugins: PluginHost,
    private val onMusic: (HomeMusicSetting) -> Unit = {},
    private val onTheme: (Int) -> Unit = {},
    private val themeNames: List<String> = listOf(Copy.builtIn),
    private val themeMotions: List<BackgroundMotion> = listOf(BackgroundMotion.Off),
    private val themeIds: List<String> = themeNames,
    private val backgroundChoices: List<app.foldcade.language.BackgroundChoice>? = null,
    private val cue: (themeIndex: Int, slot: String) -> Boolean = { _, _ -> false },
    private val bareTick: () -> Unit = {},
    private val homeTracks: List<MusicTrack> = emptyList(),
    private val themeTracks: List<MusicTrack?> = emptyList(),
    private val lastPlayedMillis: (String) -> Long? = { null },
    /** A Moonlight game left Home. The app drops its pinned shortcut. */
    private val onMoonlightRemoved: (remoteKey: String) -> Unit = {},
    /** A DroidDeck game left Home. The app drops its pinned shortcut. */
    private val onDroidDeckRemoved: (remoteKey: String) -> Unit = {},
    /** The R1 slider moved to this position. The app stores it. */
    private val onBrightness: (position: Int) -> Unit = {},
) {
    private var shelfState: AppShelfState = store.appShelfState()
    private var installed: List<LaunchableApp> = emptyList()
    private var playerPackages: Set<String> = emptySet()
    private val home = HomeSession(store.homeBoardRaw())
    private var libraryItems: List<HomeItem> = emptyList()

    /** Libraries that have reported their games since launch. Until then their home tiles are kept. */
    private val settledLibraries = mutableSetOf<String>()
    private var moonlightSettled = false
    private var droidDeckSettled = false
    private var installedAppsSettled = false
    private val libraryGames = HashMap<String, Game>()
    private var extraPlatforms: List<HomePlatform> = emptyList()

    /** Platform names a library listed, by platform id. Used when nothing else names it. */
    private val listedPlatformNames = HashMap<String, String>()

    private var current by mutableStateOf(initial())

    /** Every change keeps the columns the grid showed, so the next focus scrolls only as far as it must. */
    var model: PickerModel
        get() = current
        private set(next) {
            current = next.copy(firstColumn = shownFirstColumn(current))
        }

    private var inputDeviceKey: String = "builtin"

    var connectToken by mutableStateOf("")
        private set

    private var setupValidation = 0
    var connectVisit: Int = 0
        private set
    var connectOriginRevision: Int = 0
        private set

    /** Shared by the two activities; the form always saves to the connection it opened for. */
    var connectRommId: String = RommCredentials.PLUGIN_ID
        private set
    private var reLoginRommId = RommCredentials.PLUGIN_ID

    /** Only the current form may save credentials or show the result of its check. */
    fun beginSetupValidation(): Int? {
        if (!model.connectOpen || model.connectBusy) return null
        setupValidation += 1
        // A token submitted on either display retires every outstanding challenge.
        if (model.connectKind == ConnectKind.Romm) connectVisit += 1
        model = model.copy(connectBusy = true, connectHint = null, connectRetry = model.connectKind == ConnectKind.Romm)
        return setupValidation
    }

    fun setupValidationCurrent(ticket: Int): Boolean = model.connectOpen && setupValidation == ticket

    fun finishSetupValidation(ticket: Int) {
        if (setupValidationCurrent(ticket)) model = model.copy(connectBusy = false)
    }

    private fun invalidateSetupValidation() {
        setupValidation += 1
        if (model.connectBusy) model = model.copy(connectBusy = false)
    }

    /** Counts A presses on a connect text field. Each one focuses that field and raises the keyboard. */
    var typingRequest by mutableStateOf(0)
        private set

    /** Home ids of server games downloading now. Their tiles pulse the cloud. */
    var downloading by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Home ids of games whose download is being deleted now. They do not launch until it is gone. */
    var removing by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * Libraries pointed at another server since their games were listed, with the
     * [deviceEdits] stamp of that change. Their copies are not theirs to delete.
     */
    private val relisting = HashMap<String, Long>()

    /** Counts on-device and server changes, so a scan that read before one does not undo it. */
    @Volatile private var deviceEdits = 0L
    private val deviceEdited = HashMap<String, Pair<Long, Availability>>()

    /** What libraries are doing with games, by home id. Tiles and the top screen show it. */
    var activity by mutableStateOf<Map<String, TileActivity>>(emptyMap())
        private set

    /** Cells for the current grid. Empty when the grid is an empty state. */
    var entries by mutableStateOf<List<GridEntry>>(emptyList())
        private set

    var activeLibraryId: String? = null
        private set

    private var platformEntries: List<GridEntry> = emptyList()
    private var platformFocus: GridFocus = GridFocus()
    private var platformOrder: List<Int> = emptyList()
    private var loggedHomeTile: String? = null

    fun onMeaning(meaning: Meaning, screen: HostScreen, repeatCount: Int = 0): Effect? {
            if (homeActive()) {
            val before = model
            val wasInFolder = home.board.openFolderId != null
            val wasAll = home.board.allOpen
            val wasHeld = home.board.hold != null
            val step = home.handle(meaning, model.focus.cellIndex)
            if (step != null && step.handled) {
                store.saveHomeBoard(home.encoded())
                showBoard(step.focus ?: model.focus, keepDialog = true)
                if (!wasInFolder && home.openedFolderHasGame()) Log.i("Foldcade", "library-ui games")
                if (!wasHeld && home.board.hold != null) Log.i("Foldcade", "home-ui lifted")
                if (home.board.allOpen && !wasAll) Log.i("Foldcade", "home-ui all-games")
                cueMeaning(meaning, before, model)
                return step.effect
            }
        }
        val before = model
        val current = if (model.libraryGrid) model else withShelf(model)
        val (next, effect) = reduce(current, meaning, screen, repeatCount)
        if (!next.connectOpen) connectToken = ""
        val packageName = focusedGame()?.androidPackage
        val panelLabel = next.panel?.let { panel ->
            val row = app.foldcade.language.panelRows(panel, next).getOrNull(panel.index) ?: return@let null
            app.foldcade.language.rowText(row, next).label
        }
        if (panelLabel != null && (meaning == Meaning.LeftPanel || meaning == Meaning.MoveDown)) {
            Log.i("Foldcade", "home-ui row $panelLabel")
        }
        when (effect) {
            Effect.PinApp -> {
                if (packageName != null) {
                    val favorite = !shelfState.record(packageName).favorite
                    shelfState = shelfState.pin(packageName, favorite)
                    store.saveAppShelfState(shelfState)
                }
                publish(focusPackage(next, packageName))
            }
            is Effect.AddAppTo -> {
                if (packageName != null && home.placeIn(androidHomeId(packageName), effect.folderId)) syncHome()
                publish(next.copy(shelfEpoch = next.shelfEpoch + 1))
            }
            Effect.MoveApp -> {
                if (packageName != null) {
                    val destination = if (current.homeGrid == HomeGrid.AndroidGames) {
                        AndroidShelf.Apps
                    } else {
                        AndroidShelf.Games
                    }
                    shelfState = shelfState.move(packageName, destination)
                    store.saveAppShelfState(shelfState)
                }
                publish(next)
            }
            Effect.HideApp -> {
                if (packageName != null) {
                    shelfState = shelfState.hide(packageName)
                    store.saveAppShelfState(shelfState)
                }
                publish(next)
            }
            Effect.ShowApp -> {
                if (packageName != null) {
                    shelfState = shelfState.show(packageName, packageName in playerPackages)
                    store.saveAppShelfState(shelfState)
                }
                publish(next)
            }
            else -> publish(next)
        }
        cueMeaning(meaning, before, model)
        if (effect == Effect.DismissButtonLabels) {
            store.saveButtonPrompt(inputDeviceKey, store.buttonPromptConfirm(inputDeviceKey), dismissed = true)
        }
        return when (effect) {
            Effect.PinApp, is Effect.AddAppTo, Effect.MoveApp, Effect.HideApp, Effect.ShowApp,
            -> null
            Effect.MoveTile -> {
                pickUpHome(model.focus.cellIndex)
                null
            }
            is Effect.MoveTileTo -> changeHome { home.moveTo(it, effect.folderId) }
            Effect.NewFolder -> changeHome { home.newFolder(it) }
            Effect.RenameFolder -> changeHome { home.startRename(it); it }
            Effect.ChooseFolderIcon -> changeHome { home.pickIcon(it); it }
            Effect.RemoveTile -> {
                // A folder has no library, so removing one never unpins what it held.
                val removed = home.face(model.focus.cellIndex)?.let(::homeShelf)
                val moonlight = removed?.takeIf { it.libraryId == MoonlightLibrary.ID }?.remoteKey
                val droidDeck = removed?.takeIf { it.libraryId == DroidDeckLibrary.ID }?.remoteKey
                changeHome { home.remove(it) }.also {
                    if (moonlight != null) onMoonlightRemoved(moonlight)
                    if (droidDeck != null) onDroidDeckRemoved(droidDeck)
                }
            }
            Effect.AddToHome -> changeHome { home.addFocused(it); it }
            Effect.OpenAll -> {
                openHomeAll()
                Log.i("Foldcade", "home-ui all-games")
                null
            }
            Effect.ToggleAddNew -> {
                applyHomeAddNew(next.addNewToHome)
                null
            }
            Effect.GoHome -> {
                goHome()
                null
            }
            Effect.EditField -> {
                typingRequest++
                null
            }
            else -> effect
        }
    }

    fun homeKeys(): app.foldcade.language.HomeKeys {
        if (model.dialog != null || model.panel != null || model.playActivity != null ||
            model.saveConflict != null || model.connectOpen
        ) {
            return app.foldcade.language.HomeKeys.Idle
        }
        if (model.libraryGrid || model.homeGrid != HomeGrid.StandIns) {
            return app.foldcade.language.HomeKeys.Idle
        }
        return home.keys()
    }

    fun homeFace(index: Int): HomeFace? = home.face(index)

    /** The text in the open folder name field. Null when no name field is open. */
    fun renameDraft(): String? = if (showingHome() && home.renamingId != null) home.renameDraft else null

    fun editRename(text: String) {
        home.editRename(text)
        showBoard(model.focus, keepDialog = true)
    }

    /** The open folder icon picker and that folder's name, for the top screen. Null when closed. */
    fun folderIconPick(): Pair<FolderIconPick, String>? {
        if (!showingHome()) return null
        val pick = home.iconPick ?: return null
        return pick to home.board.folders[pick.folderId]?.name.orEmpty()
    }

    /**
     * True when the focused tile is a game that A opens on the top screen and X
     * on the bottom: one that takes one screen, while both screens are Foldcade's.
     */
    fun focusedLaunchesOnOneScreen(): Boolean {
        if (!store.session.bothScreensFree() || model.focus.chrome != null) return false
        if (model.libraryGrid && model.gridKind != GridKind.Games) return false
        val face = if (showingHome() || home.board.allOpen) home.face(model.focus.cellIndex) else null
        if (face != null && (face.folder || face.pinned || face.empty)) return false
        val game = focusedGame() ?: return false
        return !game.emptyShelfHint && !game.occupiesBothDisplays
    }

    /** True when Y would open a menu for the focused tile. */
    fun hasOptions(): Boolean =
        model.focus.chrome == null && actionRows(model.tileActions, model.appActions).isNotEmpty()

    fun homeEditing(): Boolean =
        showingHome() && home.board.editing && model.dialog == null && model.panel == null

    fun homeAllOpen(): Boolean =
        showingHome() && home.board.allOpen && model.dialog == null && model.panel == null

    fun homeLiftedIndex(): Int? = home.board.hold?.index

    fun homeAnimToken(): String = home.animToken()

    fun catalogGame(id: String): Game? = home.game(id)

    /** Runs one Y menu action on the focused home tile. [change] returns the index to focus after. */
    private fun changeHome(change: (Int) -> Int): Effect? {
        if (!showingHome()) return null
        val index = change(model.focus.cellIndex)
        // A folder Foldcade made goes the moment its last game leaves it, not at the next scan.
        syncHome()
        store.saveHomeBoard(home.encoded())
        showBoard(GridFocus(cellIndex = index, lastColumn = GridStrip.column(index)), keepDialog = true)
        return null
    }

    /** L1's Home: the board's top level, from a folder, All, or a shelf. The last root tile keeps the focus. */
    fun goHome() {
        val index = home.showRoot()
        syncHome()
        showBoard(GridFocus(cellIndex = index, lastColumn = GridStrip.column(index)), keepDialog = true)
        Log.i("Foldcade", "library-ui home")
    }

    /** A game's name for the Play activity page, wherever it lives. Null for one Foldcade no longer knows. */
    fun playTitle(gameId: String): String? =
        home.catalog().firstOrNull { it.id == gameId }?.title
            ?: home.game(gameId)?.label
            ?: Shelf.games.firstOrNull { it.id == gameId }?.title
            ?: installed.firstOrNull { androidHomeId(it.packageName) == gameId }?.label

    fun openHomeAll() {
        home.showAll()
        store.saveHomeBoard(home.encoded())
        showBoard(GridFocus(), keepDialog = true)
    }

    fun applyHomeAddNew(enabled: Boolean) {
        home.applyAddNew(enabled)
        syncHome()
        publish(
            model.copy(
                addNewToHome = home.board.addNewToHome,
                shelfEpoch = model.shelfEpoch + 1,
            ),
        )
    }

    fun pickUpHome(index: Int) {
        if (model.libraryGrid || model.homeGrid != HomeGrid.StandIns) return
        if (model.dialog != null || model.panel != null || model.connectOpen) return
        home.startMove(index)
        if (home.board.hold != null) Log.i("Foldcade", "home-ui lifted")
        store.saveHomeBoard(home.encoded())
        showBoard(GridFocus(cellIndex = index, lastColumn = GridStrip.column(index)), keepDialog = true)
    }

    fun dragHome(meaning: Meaning) {
        if (home.board.hold == null) return
        home.drag(meaning)
        val index = home.board.hold?.index ?: return
        store.saveHomeBoard(home.encoded())
        showBoard(GridFocus(cellIndex = index, lastColumn = GridStrip.column(index)), keepDialog = true)
    }

    /**
     * Folds one backend's games into the curated board.
     * A later scan keeps placed tiles and only fills new ones when the setting is on.
     * Every library lands on the same board. An open panel or dialog stays open.
     */
    fun ingestLibrary(
        libraryId: String,
        entries: List<GridEntry>,
        platforms: List<Platform>,
        listedNames: Map<String, String> = emptyMap(),
        readAt: Long = deviceMark(),
    ) {
        listedPlatformNames.putAll(listedNames.filterValues { it.isNotBlank() })
        val prefix = "lib:$libraryId:"
        if ((relisting[libraryId] ?: 0L) <= readAt) relisting.remove(libraryId)
        // A download or delete after [readAt] is newer than what the scan saw on disk.
        deviceEdited.entries.removeAll { (id, edit) -> id.startsWith(prefix) && edit.first <= readAt }
        val items = ArrayList<HomeItem>()
        val remembered = HashMap<String, Game>()
        for (entry in entries) {
            val game = entry.game ?: continue
            val id = homeGameId(libraryId, game.remoteKey)
            val kind = when (libraryId) {
                "gamenative" -> HomeKind.GameNative
                "moonlight" -> HomeKind.Moonlight
                DroidDeckLibrary.ID -> HomeKind.DroidDeck
                else -> HomeKind.Rom
            }
            items += HomeItem(
                id = id,
                title = entry.title.ifBlank { game.label },
                kind = kind,
                platformId = game.platformId,
                mark = kitMark(game.platformId),
                lastPlayedMillis = lastPlayedMillis(id) ?: 0L,
            )
            remembered[id] = deviceEdited[id]?.let { (_, availability) -> game.copy(availability = availability) } ?: game
        }
        libraryItems = libraryItems.filterNot { it.id.startsWith(prefix) } + items
        settledLibraries += libraryId
        libraryGames.keys.filter { it.startsWith(prefix) }.toList().forEach { libraryGames.remove(it) }
        libraryGames.putAll(remembered)
        extraPlatforms = platforms.map { platform ->
            HomePlatform(
                id = platform.id,
                name = platform.displayName,
                aliases = platform.aliases,
                mark = kitMark(platform.id),
            )
        }
        noteShelfChanged()
    }

    /** A library that is no longer set up takes its tiles off the board. */
    fun dropLibrary(libraryId: String) {
        val prefix = "lib:$libraryId:"
        relisting.remove(libraryId)
        libraryItems = libraryItems.filterNot { it.id.startsWith(prefix) }
        settledLibraries += libraryId
        libraryGames.keys.removeAll { it.startsWith(prefix) }
        noteShelfChanged()
    }

    /** A server game was downloaded. Its tile drops the cloud. */
    fun noteOnDevice(libraryId: String, remoteKey: String) {
        noteAvailability(libraryId, remoteKey, from = Availability.RemoteOnly, to = Availability.Cached)
    }

    /** A server game's download was deleted. Its tile shows the cloud again. */
    fun noteOffDevice(libraryId: String, remoteKey: String) {
        noteAvailability(libraryId, remoteKey, from = Availability.Cached, to = Availability.RemoteOnly)
    }

    private fun noteAvailability(libraryId: String, remoteKey: String, from: Availability, to: Availability) {
        val id = homeGameId(libraryId, remoteKey)
        deviceEdits += 1
        deviceEdited[id] = deviceEdits to to
        val game = libraryGames[id] ?: return
        if (game.availability != from) return
        libraryGames[id] = game.copy(availability = to)
        noteShelfChanged()
    }

    /** Taken before a library scan reads the disk, and handed back to [ingestLibrary]. */
    fun deviceMark(): Long = deviceEdits

    /**
     * The focused home tile's game when its download can be deleted. Null when
     * it has no copy here, or while that copy is arriving, being checked, or deleted.
     */
    fun focusedDownload(): Game? {
        if (!showingHome()) return null
        val id = home.face(model.focus.cellIndex)?.id ?: return null
        if (busyOnDevice(id)) return null
        return home.game(id)?.takeIf { it.availability == Availability.Cached }
    }

    private fun busyOnDevice(id: String): Boolean =
        id in downloading || id in removing || libraryOf(id) in relisting ||
            activity[id].let { it is TileActivity.Downloading || it == TileActivity.Checking }

    /**
     * [libraryId]'s connection is moving to another server. Delete download waits,
     * and no list clears that until [settleServerChange].
     */
    fun noteServerChanged(libraryId: String) {
        relisting[libraryId] = Long.MAX_VALUE
    }

    /** The move is saved and published, or failed. A list read after this clears the hold. */
    fun settleServerChange(libraryId: String) {
        if (relisting[libraryId] != Long.MAX_VALUE) return
        deviceEdits += 1
        relisting[libraryId] = deviceEdits
    }

    /** False when this game's download is already being deleted. */
    fun beginRemove(libraryId: String, remoteKey: String): Boolean {
        val id = homeGameId(libraryId, remoteKey)
        if (id in removing) return false
        removing = removing + id
        return true
    }

    fun endRemove(libraryId: String, remoteKey: String) {
        removing = removing - homeGameId(libraryId, remoteKey)
    }

    /** False when this game is already downloading, so a second press does nothing. */
    fun beginDownload(libraryId: String, remoteKey: String): Boolean {
        val id = homeGameId(libraryId, remoteKey)
        if (id in downloading) return false
        downloading = downloading + id
        return true
    }

    fun endDownload(libraryId: String, remoteKey: String) {
        downloading = downloading - homeGameId(libraryId, remoteKey)
    }

    /**
     * Records what a library said it is doing with a game. A finished sync keeps
     * its mark for [Motion.durationSyncMark]; the time it ends is returned so the
     * caller can call [expireActivity] then. Anything else returns null.
     */
    fun noteActivity(libraryId: String, remoteKey: String, reported: GameActivity, nowMillis: Long): Long? {
        val id = homeGameId(libraryId, remoteKey)
        val next = when (reported) {
            is GameActivity.Downloading -> TileActivity.Downloading(
                reported.totalBytes?.takeIf { it > 0 }?.let { total ->
                    (reported.bytesSoFar.toDouble() / total).toFloat().coerceIn(0f, 1f)
                },
            )
            GameActivity.Checking -> TileActivity.Checking
            GameActivity.SyncingSaves -> TileActivity.Syncing
            is GameActivity.Synced -> TileActivity.Synced(syncMark(reported.result), nowMillis + Motion.durationSyncMark)
            else -> null
        }
        if (next == null) {
            if (id in activity) activity = activity - id
            return null
        }
        if (activity[id] != next) activity = activity + (id to next)
        return (next as? TileActivity.Synced)?.untilMillis
    }

    /** Drops a finished sync's mark when its time is up. A newer report for the game stays. */
    fun expireActivity(libraryId: String, remoteKey: String, untilMillis: Long) {
        val id = homeGameId(libraryId, remoteKey)
        val current = activity[id] as? TileActivity.Synced ?: return
        if (current.untilMillis == untilMillis) activity = activity - id
    }

    /** A launch is over, opened or not. Only a finished sync's mark outlives it. */
    fun settleActivity(libraryId: String, remoteKey: String) {
        val id = homeGameId(libraryId, remoteKey)
        val current = activity[id] ?: return
        if (current !is TileActivity.Synced) activity = activity - id
    }

    /** The activity of the game at [source] in the grid shown now. */
    fun activityAt(source: Int): TileActivity? {
        if (activity.isEmpty()) return null
        val id = when {
            model.libraryGrid -> entries.getOrNull(source)?.game?.let { homeGameId(it.backendId, it.remoteKey) }
            showingHome() -> home.face(source)?.id
            else -> null
        }
        return id?.let { activity[it] }
    }

    /** The activity of the focused game, for the top screen. */
    fun focusedActivity(): TileActivity? {
        if (activity.isEmpty()) return null
        val source = if (model.libraryGrid) {
            displayOrder(model).getOrElse(model.focus.cellIndex) { model.focus.cellIndex }
        } else {
            model.focus.cellIndex
        }
        return activityAt(source)
    }

    /** True once any library has a game on the board. */
    fun hasLibraryGames(): Boolean = libraryItems.isNotEmpty()

    /** The scanned library is on the home grid. Emulator waits for this line. */
    fun showCuratedHome() {
        syncHome()
        showBoard(GridFocus(), keepDialog = true)
        Log.i("Foldcade", "library-ui home")
    }

    fun setInstalledApps(apps: List<LaunchableApp>, players: Set<String>) {
        installed = apps.distinctBy { it.packageName }
        installedAppsSettled = true
        playerPackages = players
        syncHome()
        publish(model)
    }

    fun showHomeGrid(grid: HomeGrid) {
        publish(
            model.copy(
                homeGrid = grid,
                libraryGrid = false,
                panel = null,
                arranging = false,
                hold = null,
                order = emptyList(),
                focus = GridFocus(),
                connectOpen = false,
                dialog = null,
            ),
        )
    }

    fun setPromptHeld(key: PromptKey, down: Boolean) {
        val next = if (down) model.held + key else model.held - key
        if (next == model.held) return
        model = model.copy(held = next)
    }

    fun applyFacePrompt(style: ThorStyle?, deviceKey: String, firstSession: Boolean) {
        inputDeviceKey = deviceKey
        val confirm = store.buttonPromptConfirm(deviceKey)
        val map = resolveFaceMap(style, confirm)
        val offer = offerButtonCalibration(
            firstSession = firstSession,
            styleKnown = style != null,
            dismissed = store.buttonPromptDismissed(deviceKey),
            calibrated = confirm != null,
        )
        model = model.copy(
            faceMap = map,
            offerButtonLabels = offer,
            capturingConfirm = if (offer) model.capturingConfirm else false,
        )
    }

    fun calibrateConfirm(key: PromptKey) {
        if (!key.face) return
        store.saveButtonPrompt(inputDeviceKey, key, dismissed = false)
        model = model.copy(
            faceMap = faceMapWithConfirm(model.faceMap, key),
            capturingConfirm = false,
            offerButtonLabels = false,
        )
    }

    fun editOrigin(origin: String) {
        if (origin != model.connectOrigin) {
            invalidateSetupValidation()
            connectOriginRevision += 1
            // Keep retry available until a replacement challenge arrives; token checks retire polling.
            model = model.copy(connectHint = null)
        }
        model = model.copy(
            connectOrigin = origin,
            connectWarning = cleartextCredentialWarning(origin),
        )
    }

    fun editToken(value: String) {
        if (value != connectToken) invalidateSetupValidation()
        connectToken = value
    }

    fun consumeConnectToken(): String = connectToken.also { connectToken = "" }

    fun openAddRomm() {
        openConnect("", another = store.rommOrigin() != null)
    }

    fun editRommConnection(instanceId: String): Boolean {
        val origin = store.rommServerOrigin(instanceId)
        if (origin == null) {
            closeConnect()
            return false
        }
        openConnect(origin, instanceId = instanceId, returnPanel = model.connectReturnPanel)
        return true
    }

    fun openConnect(
        origin: String,
        another: Boolean = false,
        instanceId: String = RommCredentials.PLUGIN_ID,
        returnPanel: SidePanel? = null,
    ) {
        invalidateSetupValidation()
        connectVisit += 1
        connectRommId = instanceId
        val screen = model.connectScreen ?: HostScreen.Top
        val shown = if (another) "" else origin
        connectToken = ""
        model = model.copy(
            connectOpen = true,
            connectScreen = screen,
            connectReturnPanel = returnPanel,
            panel = null,
            connectOrigin = shown,
            connectIndex = 0,
            connectWarning = cleartextCredentialWarning(shown),
            connectHint = null,
            connectRetry = false,
            connectKind = ConnectKind.Romm,
            connectAnother = another,
        )
    }

    /** The connect form, asking for a SteamGridDB key. */
    fun openArtKey() {
        invalidateSetupValidation()
        connectVisit += 1
        val screen = model.connectScreen ?: HostScreen.Top
        connectToken = ""
        model = model.copy(
            connectOpen = true,
            connectScreen = screen,
            panel = null,
            connectIndex = 0,
            connectWarning = null,
            connectHint = null,
            connectRetry = false,
            connectKind = ConnectKind.ArtKey,
            connectReturnPanel = null,
        )
    }

    /** A line under the key field, such as a refused key. */
    fun showConnectNote(note: String) {
        model = model.copy(connectHint = note)
    }

    fun setArtKeySaved(saved: Boolean) {
        if (model.artKeySaved != saved) model = model.copy(artKeySaved = saved)
    }

    fun setLibrarySyncing(syncing: Boolean) {
        if (model.librarySyncing != syncing) model = model.copy(librarySyncing = syncing)
    }

    fun showSetupHint(hint: String?, retry: Boolean = false) {
        model = model.copy(connectHint = hint, connectRetry = retry)
    }

    /** A retry on either display supersedes every previous device-code attempt. */
    fun beginDeviceCodeAttempt(): Int {
        connectVisit += 1
        showSetupHint(null)
        return connectVisit
    }

    fun deviceCodeVisitCurrent(visit: Int): Boolean =
        model.connectOpen && model.connectKind == ConnectKind.Romm && connectVisit == visit

    /** Even an edit back to the same address supersedes the previous challenge. */
    fun deviceCodeFormCurrent(visit: Int, originRevision: Int): Boolean =
        deviceCodeVisitCurrent(visit) && connectOriginRevision == originRevision && !model.connectBusy

    fun setSignedIn(backends: List<SignedInBackend>) {
        model = model.copy(signedIn = backends)
    }

    fun setFolders(folders: List<LibraryFolder>) {
        if (model.folders != folders) model = model.copy(folders = folders)
    }

    /** Leaves the connect form, as after a server accepts the token. */
    fun closeConnect() {
        invalidateSetupValidation()
        connectToken = ""
        model = model.copy(
            connectOpen = false,
            connectScreen = null,
            connectHint = null,
            connectAnother = false,
            connectRetry = false,
            panel = model.connectReturnPanel,
            connectReturnPanel = null,
        )
        showQueuedPrompt()
    }

    fun openRommRecovery() {
        val origin = store.rommServerOrigin(reLoginRommId)
        // A removed additional server must not be recreated by an old prompt.
        if (origin == null && reLoginRommId != RommCredentials.PLUGIN_ID) return
        openConnect(origin.orEmpty(), instanceId = reLoginRommId)
    }

    fun askToSignInAgain(instanceId: String = RommCredentials.PLUGIN_ID) {
        if (model.dialog?.kind == DialogKind.ReLogin || model.reLoginPending) return
        if (model.connectOpen && !model.connectAnother && connectRommId == instanceId) return
        reLoginRommId = instanceId
        if (model.dialog != null || model.connectOpen) {
            model = model.copy(reLoginPending = true)
            return
        }
        invalidateSetupValidation()
        connectToken = ""
        model = model.copy(
            dialog = rommRecoveryPrompt(),
            connectOpen = false,
            connectScreen = null,
        )
    }

    private fun rommRecoveryPrompt() = signInAgainPrompt().copy(
        detail = if (reLoginRommId == RommCredentials.PLUGIN_ID) null
        else store.rommServerOrigin(reLoginRommId)?.let { rommServerLabel(reLoginRommId, it) },
    )

    fun present(dialog: DialogState) {
        if (model.dialog != null) return
        model = model.copy(dialog = dialog)
    }

    /** Drops a dialog without activating its button. Debug captures use this before opening a menu. */
    fun dismissDialog() {
        if (model.dialog == null) return
        model = model.copy(dialog = null)
    }

    /**
     * A debug capture shows this shoulder now, in place of whichever island was open.
     * A real L1 or R1 press still retires the open island before the other expands.
     */
    fun previewIsland(side: Side) {
        val shown = replaceIsland(model, side)
        if (shown.panel == model.panel && shown.focus == model.focus) return
        publish(shown)
    }

    /**
     * The open island finished its leave morph. The pending shoulder expands now.
     * Calling this before the close finishes, or twice, does nothing.
     */
    fun completeIslandRetire() {
        val finished = finishIslandRetire(model)
        if (finished.panel == model.panel && finished.focus == model.focus) return
        publish(finished)
    }

    /** Closes whichever top menu is open. A closed menu stays closed. */
    fun closePanel() {
        val side = model.panel?.side ?: return
        val meaning = if (side == Side.Left) Meaning.LeftPanel else Meaning.RightPanel
        onMeaning(meaning, HostScreen.Top)
    }

    fun showQueuedPrompt() {
        if (!model.reLoginPending || model.dialog != null || model.connectOpen) return
        model = model.copy(reLoginPending = false, dialog = rommRecoveryPrompt())
    }

    /** One tap focuses a text row. Save activates on that tap. */
    fun touchConnect(index: Int, screen: HostScreen): Effect? {
        val field = connectFields(model.connectKind, model.connectRetry).getOrNull(index) ?: return null
        if (field == ConnectField.Origin || field == ConnectField.Token) {
            if (model.connectIndex != index) model = model.copy(connectIndex = index)
            return null
        }
        val (next, effect) = reduce(model.copy(connectIndex = index), Meaning.Activate, screen)
        if (!next.connectOpen) connectToken = ""
        publish(next)
        return effect
    }

    fun setRowsPerPage(rows: Int) {
        val next = rows.coerceAtLeast(1)
        if (model.rowsPerPage == next) return
        model = model.copy(rowsPerPage = next)
    }

    fun setHomeRoleHeld(held: Boolean) {
        if (model.homeRoleHeld == held) return
        model = model.copy(homeRoleHeld = held)
    }

    fun noteFolderExplained() {
        store.setFolderGrantExplained()
        model = model.copy(folderGrantPending = false)
    }

    /** One tap focuses a tile. A second tap on it activates, and hands back what that opens. */
    fun touchCell(index: Int, screen: HostScreen): Effect? {
        if (model.dialog != null || model.panel != null || model.connectOpen) return null
        val current = model.focus
        val already = current.chrome == null && current.cellIndex == index
        if (!already) {
            publish(
                model.copy(
                    focus = GridFocus(
                        cellIndex = index,
                        lastColumn = GridStrip.column(index),
                        chrome = null,
                    ),
                ),
            )
            cue(model.themeIndex, "move")
            return null
        }
        return onMeaning(Meaning.Activate, screen)
    }

    fun touchChrome(chrome: Chrome, screen: HostScreen) {
        if (model.dialog != null || model.panel != null || model.connectOpen) return
        publish(model.copy(focus = model.focus.copy(chrome = chrome)))
        onMeaning(Meaning.Activate, screen)
    }

    /** One tap moves focus to that row. A second tap on it activates. */
    fun touchPanel(index: Int, screen: HostScreen): Effect? {
        val panel = model.panel ?: return null
        if (panel.screen != screen) return null
        val rows = panelRows(panel, model)
        if (index !in rows.indices) return null
        if (panel.index != index) {
            publish(model.copy(panel = panel.copy(index = index)))
            return null
        }
        return onMeaning(Meaning.Activate, screen)
    }

    /** A tap on a settings category shows its rows. A second tap steps into them. */
    fun touchSettingsCategory(index: Int, screen: HostScreen): Effect? {
        val page = model.settings ?: return null
        if (page.screen != screen) return null
        if (index !in settingsCategories(model).indices) return null
        if (page.category != index || page.onRows) {
            publish(model.copy(settings = page.copy(category = index, row = 0, onRows = false)))
            return null
        }
        return onMeaning(Meaning.MoveRight, screen)
    }

    /** One tap on a settings row focuses it and acts on it, the same as a menu row. */
    fun touchSettingsRow(index: Int, screen: HostScreen): Effect? {
        val page = model.settings ?: return null
        if (page.screen != screen) return null
        publish(model.copy(settings = page.copy(row = index, onRows = true)))
        return onMeaning(Meaning.Activate, screen)
    }

    /** A tap on the Play activity page's back pill. */
    fun closePlayActivity() {
        val page = model.playActivity ?: return
        publish(model.copy(playActivity = null, focus = page.grid))
    }

    fun closeSettings(): Effect? {
        val page = model.settings ?: return null
        publish(model.copy(settings = null, focus = page.grid))
        return null
    }

    /** One tap focuses the button and activates it. Keys still move, then Activate. */
    fun touchDialog(index: Int, screen: HostScreen): Effect? {
        val dialog = model.dialog ?: return null
        if (dialog.screen != screen) return null
        val (next, effect) = focusAndActivateDialog(model, index)
        publish(next)
        return effect
    }

    /**
     * Moonlight tiles stay on Home, hidden flags included, until the first live
     * shortcut read. Before it, a tile missing from the shelf is not yet known to be gone.
     */
    fun settleMoonlight() {
        moonlightSettled = true
    }

    fun settleGameNative() {
        settledLibraries += GameNativeLibrary.ID
    }

    /**
     * DroidDeck tiles stay on Home until a live shortcut read and a finished
     * folder scan have both reported. Before that, a missing tile is not yet known to be gone.
     */
    fun settleDroidDeck() {
        droidDeckSettled = true
    }

    /**
     * A game the user just added to the home screen in DroidDeck goes on Home in
     * the DroidDeck folder, even with Add new games off and even if it was removed before.
     */
    fun placeDroidDeckPin(shelfId: String) {
        if (!home.place(shelfId)) return
        store.saveHomeBoard(home.encoded())
        noteShelfChanged()
    }

    /**
     * A game the user just pinned in Moonlight goes on Home in the Moonlight
     * folder, even with Add new games off and even if it was removed before.
     */
    fun placeMoonlightPin(remoteKey: String) {
        if (!home.place(remoteKey)) return
        store.saveHomeBoard(home.encoded())
        noteShelfChanged()
    }

    /** The stored [level] and whether Foldcade may write it. */
    fun setBrightness(level: Int?, allowed: Boolean) {
        val position = level?.let(::brightnessPosition) ?: model.brightness
        if (position == model.brightness && allowed == model.brightnessAllowed) return
        model = model.copy(brightness = position, brightnessAllowed = allowed)
    }

    /** R1's notices, newest first. A notice that went away while focused leaves the focus on the next. */
    fun setNotices(notices: List<FoldNotice>) {
        if (notices == model.notices) return
        model = model.copy(notices = notices)
    }

    fun setNoticeAccess(ask: Boolean) {
        if (ask == model.askNoticeAccess) return
        model = model.copy(askNoticeAccess = ask)
    }

    fun setUsageGranted(granted: Boolean) {
        if (model.usageGranted == granted) return
        model = model.copy(usageGranted = granted)
    }

    /** Once, after a session exists. Never on a cold start, and never over another dialog. */
    fun maybeOfferUsageAccess(hasTrackedSession: Boolean, granted: Boolean, screen: HostScreen) {
        val blocked = model.dialog != null
        if (!shouldOfferUsageAccess(hasTrackedSession, granted, store.usagePromptOffered(), blocked)) {
            return
        }
        present(usageAccessPrompt(screen))
    }

    fun maybeAskHome(roleHeld: Boolean) {
        if (roleHeld) {
            store.setHomePromptSettled()
            return
        }
        if (store.homePromptSettled() || model.dialog != null) return
        publish(model.copy(dialog = homePrompt()))
    }

    /** Debuggable captures. Replaces whatever dialog is up. */
    fun showPreviewDialog(kind: String, index: Int, screen: HostScreen) {
        val dialog = previewDialogState(kind, index, screen) ?: return
        Log.i(
            "Foldcade",
            "preview-dialog kind=$kind index=${dialog.index} screen=${screen.name.lowercase()} title=${dialog.title}",
        )
        publish(model.copy(panel = null, connectOpen = false, dialog = dialog))
    }

    /** Puts [prompt] on the bottom screen. Null clears it. A debug preview is left in place. */
    fun presentSaveConflict(prompt: app.foldcade.language.SaveConflictPrompt?) {
        if (prompt == null && model.saveConflict?.preview == true) return
        if (prompt != null && !prompt.preview) {
            Log.i("Foldcade", "save-conflict ${prompt.gameLabel}")
        }
        publish(
            model.copy(
                saveConflict = prompt,
                panel = if (prompt != null) null else model.panel,
                playActivity = if (prompt != null) null else model.playActivity,
            ),
        )
    }

    /** Debuggable capture of the conflict screen. Choosing it does not touch a library. */
    fun showSaveConflictPreview() {
        val prompt = app.foldcade.language.previewSaveConflict()
        Log.i("Foldcade", "save-conflict ${prompt.gameLabel}")
        publish(
            model.copy(
                panel = null,
                connectOpen = false,
                dialog = null,
                settings = null,
                playActivity = null,
                saveConflict = prompt,
            ),
        )
    }

    /** A touch focuses that action and activates it, the same as the controller. */
    fun touchSaveConflict(focus: app.foldcade.language.SaveConflictFocus): Effect? {
        val prompt = model.saveConflict ?: return null
        model = model.copy(saveConflict = prompt.copy(focus = focus))
        return onMeaning(Meaning.Activate, HostScreen.Bottom)
    }

    fun focusedGame(): ShelfGame? = when {
        showingHome() -> home.face(model.focus.cellIndex)?.let(::homeShelf)
        else -> tileFromOrder(displaySource(model))
    }

    /** The game cell [index] shows, as [focusedGame] reads the focused cell. Null off the grid. */
    fun gameAt(index: Int): ShelfGame? = when {
        index !in 0 until model.count -> null
        showingHome() -> home.face(index)?.let(::homeShelf)
        else -> tileFromOrder(displayOrder(model).getOrElse(index) { index })
    }

    /**
     * A home tile as a game. A library game takes its screens from its platform's
     * players, as the library grid does, so a DS game reads as both screens.
     */
    private fun homeShelf(face: HomeFace): ShelfGame {
        val game = home.asShelf(face)
        val platformId = game.platformId
        if (game.libraryId == null || platformId == null) return game
        return game.copy(occupiesBothDisplays = platformOccupiesBoth(plugins, platformId))
    }

    /**
     * The id play history is kept under. A library game has one id, its home id,
     * whichever grid it was launched or focused from.
     */
    fun playId(id: String): String {
        val libraryId = activeLibraryId
        return if (model.libraryGrid && libraryId != null) homeGameId(libraryId, id) else id
    }

    /** A library game's platform, by the name its folder shows. Null off the library grid. */
    private fun libraryPlatformName(platformId: String?): String? {
        if (!model.libraryGrid || platformId == null) return null
        return platformEntries.firstOrNull { it.platformId == platformId }?.title
    }

    /** [game] as the command center beside it shows it: its name, icon or mark, and art. */
    fun runningApp(game: ShelfGame): RunningApp = RunningApp(
        title = game.title,
        packageName = game.androidPackage,
        mark = game.mark,
        record = artRecord(game),
        query = artQuery(game),
    )

    /** The focused tile as a [RunningApp], for the launch it is about to start. */
    fun focusedRunningApp(): RunningApp? = focusedGame()?.takeUnless { it.emptyShelfHint }?.let(::runningApp)

    /**
     * What the art sources need to look [game] up. Null for an app, which draws its
     * launcher icon, and for a tile with no game behind it, such as a folder.
     */
    fun artQuery(game: ShelfGame): ArtQuery? {
        if (game.androidPackage != null || game.emptyShelfHint || game.id.isEmpty()) return null
        val record = artRecord(game)
        val libraryId = record?.backendId ?: game.libraryId ?: return null
        val remoteKey = record?.remoteKey ?: game.remoteKey
        val platformId = (record?.platformId ?: game.platformId)?.let { id ->
            resolvePlatform(homePlatforms(), id)?.id ?: id
        }
        return ArtQuery(
            key = if (model.libraryGrid && record != null) homeGameId(libraryId, record.remoteKey) else game.id,
            title = game.title,
            platformId = platformId,
            fileName = remoteKey?.takeIf { libraryId == LocalFolderBackend.ID }?.let(LocalFolderBackend::fileNameOf),
            steamAppId = when (libraryId) {
                GameNativeLibrary.ID -> remoteKey?.let(::steamAppId)
                DroidDeckLibrary.ID -> remoteKey?.let(::droidDeckSteamAppId)
                else -> null
            },
        )
    }

    /** The library record behind [game], on the home grid or the library grid. */
    fun artRecord(game: ShelfGame): Game? =
        home.game(game.id) ?: entries.firstOrNull { it.id == game.id }?.game

    /** The library record behind the focused cell, for its metadata. Null for an app or a shelf tile. */
    fun focusedRecord(): Game? = when {
        showingHome() -> home.face(model.focus.cellIndex)?.id?.let(home::game)
        else -> focusedEntry()?.game
    }

    /**
     * What the idle top screen shows for the focused cell.
     * A platform folder is its name and game count, with no artwork.
     * An empty or loading grid has nothing to show, and nor does an empty slot,
     * so the background shows through instead of a blank name card.
     */
    fun focusedHero(): HeroSubject? {
        if (showingHome() && home.face(model.focus.cellIndex)?.empty == true) return null
        if (model.gridKind == GridKind.Platforms) {
            val entry = focusedEntry() ?: return null
            return HeroSubject.Folder(
                HeroFolder(
                    key = entry.id,
                    name = entry.title,
                    count = entry.shortText.toIntOrNull(),
                    mark = entry.platformId?.let(::kitMark),
                ),
            )
        }
        if (model.libraryGrid && model.gridKind != GridKind.Games) return null
        val game = focusedGame() ?: return null
        return HeroSubject.Item(
            HeroItem(
                key = game.id,
                title = game.title,
                detail = libraryPlatformName(game.platformId) ?: game.shortText,
                mark = game.mark,
                packageName = game.androidPackage,
                emptyShelfHint = game.emptyShelfHint,
                availability = game.availabilityLabel,
            ),
        )
    }

    fun tileFromOrder(source: Int): ShelfGame? = when {
        model.libraryGrid -> entries.getOrNull(source)?.asShelf()
        showingHome() -> home.face(source)?.let(::homeShelf)
        model.homeGrid == HomeGrid.StandIns -> Shelf.games.getOrNull(source)
        else -> listed(model.homeGrid).getOrNull(source)?.asTile(model.homeGrid)
    }

    fun refreshPlayerSaves() {
        model = model.copy(playerSaves = playerSaveSettings())
    }

    private fun libraryEntry(snapshot: PickerModel): GridEntry? {
        if (!snapshot.libraryGrid) return null
        val source = displayOrder(snapshot).getOrElse(snapshot.focus.cellIndex) { snapshot.focus.cellIndex }
        return entries.getOrNull(source)
    }

    private fun GridEntry.asShelf(): ShelfGame = ShelfGame(
        id = id,
        title = title,
        shortText = shortText,
        platformId = platformId,
        occupiesBothDisplays = occupiesBothDisplays,
        mark = platformId?.let(::kitMark),
        availabilityLabel = availabilityLabel,
    )

    /** The cell [index] shows, after the arrange order. */
    fun entryAt(index: Int): GridEntry? {
        if (!model.libraryGrid || index !in 0 until model.count) return null
        val source = displayOrder(model).getOrElse(index) { index }
        return entries.getOrNull(source)
    }

    /**
     * The focused platform or game. Empty states and action rows have none,
     * so the hero stays blank.
     */
    fun focusedEntry(): GridEntry? {
        if (!model.libraryGrid) return null
        if (model.gridKind != GridKind.Games && model.gridKind != GridKind.Platforms) return null
        return entryAt(model.focus.cellIndex)
    }

    fun showNoLibrary() {
        activeLibraryId = null
        platformEntries = emptyList()
        platformOrder = emptyList()
        show(
            cells = emptyList(),
            count = 2,
            kind = GridKind.NoLibrary,
            empty = EmptyGrid.None,
            atRoot = true,
            focus = GridFocus(),
            order = emptyList(),
        )
    }

    fun showLoading(insidePlatform: Boolean) {
        show(
            cells = emptyList(),
            count = 0,
            kind = if (insidePlatform) GridKind.Games else GridKind.Platforms,
            empty = EmptyGrid.Loading,
            atRoot = !insidePlatform,
            focus = GridFocus(chrome = Chrome.StatusCluster),
            order = emptyList(),
        )
    }

    fun showUnreachable(insidePlatform: Boolean) {
        show(
            cells = emptyList(),
            count = 1,
            kind = GridKind.Unreachable,
            empty = EmptyGrid.None,
            atRoot = !insidePlatform,
            focus = GridFocus(),
            order = emptyList(),
        )
    }

    fun showPlatforms(libraryId: String, cells: List<GridEntry>) {
        activeLibraryId = libraryId
        platformEntries = cells
        platformFocus = GridFocus()
        platformOrder = emptyList()
        val empty = cells.isEmpty()
        show(
            cells = cells,
            count = cells.size,
            kind = GridKind.Platforms,
            empty = if (empty) EmptyGrid.NoPlatforms else EmptyGrid.None,
            atRoot = true,
            focus = if (empty) GridFocus(chrome = Chrome.StatusCluster) else GridFocus(),
            order = emptyList(),
        )
    }

    fun showNoPlatforms(libraryId: String) {
        showPlatforms(libraryId, emptyList())
    }

    fun showGames(cells: List<GridEntry>) {
        val empty = cells.isEmpty()
        show(
            cells = cells,
            count = cells.size,
            kind = GridKind.Games,
            empty = if (empty) EmptyGrid.NoGames else EmptyGrid.None,
            atRoot = false,
            focus = GridFocus(),
            order = emptyList(),
        )
    }

    /** Saves the platform grid so Back can return to the same cell. */
    fun rememberPlatformPlace() {
        platformEntries = entries
        platformFocus = model.focus
        platformOrder = model.order
    }

    fun restorePlatforms() {
        entries = platformEntries
        show(
            cells = platformEntries,
            count = platformEntries.size,
            kind = GridKind.Platforms,
            empty = if (platformEntries.isEmpty()) EmptyGrid.NoPlatforms else EmptyGrid.None,
            atRoot = true,
            focus = platformFocus,
            order = platformOrder,
        )
    }

    fun showDialog(dialog: DialogState) {
        if (model.dialog != null) return
        val notify = dialog.kind == DialogKind.Ok
        publish(model.copy(dialog = dialog))
        if (notify) cue(model.themeIndex, "notify")
    }

    private fun show(
        cells: List<GridEntry>,
        count: Int,
        kind: GridKind,
        empty: EmptyGrid,
        atRoot: Boolean,
        focus: GridFocus,
        order: List<Int>,
    ) {
        entries = cells
        val source = if (order.size == count && count > 0) {
            order.getOrElse(focus.cellIndex) { focus.cellIndex }
        } else {
            focus.cellIndex
        }
        publish(
            model.copy(
                count = count,
                gridKind = kind,
                emptyGrid = empty,
                atLibraryRoot = atRoot,
                focus = focus,
                order = order,
                arranging = false,
                hold = null,
                libraryGrid = true,
                homeGrid = HomeGrid.StandIns,
                dialog = null,
                panel = null,
                connectOpen = false,
            ),
        )
        val phrase = when {
            kind == GridKind.NoLibrary -> "library-ui no-library"
            kind == GridKind.Platforms && count > 0 -> "library-ui platforms"
            kind == GridKind.Games && count > 0 -> "library-ui games"
            else -> null
        }
        if (phrase != null) Log.i("Foldcade", phrase)
    }

    private fun displaySource(snapshot: PickerModel): Int {
        val index = snapshot.focus.cellIndex
        return displayOrder(snapshot).getOrElse(index) { index }
    }

    private fun withShelf(next: PickerModel): PickerModel {
        val count = countFor(next.homeGrid)
        home.sort = next.sort
        home.lastPlayed = lastPlayedMillis
        val alphabetical = next.homeGrid != HomeGrid.StandIns ||
            (home.board.allOpen && next.sort == app.foldcade.language.LibrarySort.Listed)
        val labels = if (!alphabetical) emptyList() else if (next.homeGrid == HomeGrid.StandIns) {
            home.faces().map { it.title }
        } else listed(next.homeGrid).map { it.label }
        val lastOccupied = if (next.homeGrid == HomeGrid.StandIns && !home.board.allOpen) {
            app.foldcade.language.visibleSlots(home.board).indexOfLast { it != null }
        } else count - 1
        val counted = next.copy(count = count, playerSaves = playerSaveSettings(), recentFirst = recentOrder(),
            gridLabels = labels, lastOccupiedIndex = lastOccupied)
        val game = tileOn(counted)
        val onHome = showingHome(counted)
        val tile = if (onHome) home.actions(counted.focus.cellIndex) else null
        // A copy still arriving, or already going, is not one to delete.
        val busy = home.face(counted.focus.cellIndex)?.id?.let(::busyOnDevice) == true
        val offered = if (tile?.removeDownload == true && busy) tile.copy(removeDownload = false) else tile
        // A merge can close the open folder, when its last game left, so read where the board is now.
        val root = if (onHome) home.board.openFolderId == null && !home.board.allOpen else counted.atLibraryRoot
        return counted.copy(appActions = actionsFor(counted, game), tileActions = offered, atLibraryRoot = root)
    }

    private fun tileOn(snapshot: PickerModel): ShelfGame? {
        if (!snapshot.libraryGrid && snapshot.homeGrid == HomeGrid.StandIns) {
            return home.face(snapshot.focus.cellIndex)?.let(::homeShelf)
        }
        val source = displaySource(snapshot)
        return when (snapshot.homeGrid) {
            HomeGrid.StandIns -> Shelf.games.getOrNull(source)
            else -> listed(snapshot.homeGrid).getOrNull(source)?.asTile(snapshot.homeGrid)
        }
    }

    private fun playerSaveSettings(): List<PlayerSaveSetting> =
        plugins.playerIds().mapNotNull { id ->
            val player = plugins.player(id) ?: return@mapNotNull null
            if (player !is SaveFolderHolder) return@mapNotNull null
            PlayerSaveSetting(
                playerId = player.id,
                label = player.displayName,
                chosen = player.hasSaveFolder(),
            )
        }

    fun setMusicVolume(volume: Float) {
        publish(model.copy(music = model.music.withVolume(volume)))
    }

    /** A touch on the brightness slider. Without access it asks, as the keys do. */
    fun touchBrightness(position: Int) {
        if (!model.brightnessAllowed) {
            present(brightnessPrompt())
            return
        }
        publish(model.copy(brightness = position.coerceIn(0, 100)))
    }

    /**
     * The shelf gained or lost catalog games, or the empty-shelf hint changed.
     * [PickerModel.shelfEpoch] changes even when the count does not, so Compose
     * redraws the hint. Focus stays inside the new strip's reach, see [GridStrip.reach].
     */
    fun noteShelfChanged() {
        syncHome()
        val next = model.copy(shelfEpoch = model.shelfEpoch + 1)
        if (model.libraryGrid) {
            model = next
            return
        }
        val counted = withShelf(next)
        val cell = counted.focus.cellIndex.coerceIn(0, (GridStrip.reach(counted.count) - 1).coerceAtLeast(0))
        model = if (cell == counted.focus.cellIndex) {
            counted
        } else {
            counted.copy(
                focus = counted.focus.copy(
                    cellIndex = cell,
                    lastColumn = if (counted.count == 0) 0 else GridStrip.column(cell),
                ),
            )
        }
    }

    /** Play history changed. Recently played order follows the new last-played times. */
    fun notePlayChanged() {
        if (showingHome() && home.board.allOpen) {
            model = withShelf(model.copy(shelfEpoch = model.shelfEpoch + 1))
            return
        }
        val next = recentOrder()
        if (next == model.recentFirst) return
        model = model.copy(recentFirst = next)
    }

    private fun publish(next: PickerModel) {
        val connectClosed = model.connectOpen && !next.connectOpen
        if (model.connectOpen != next.connectOpen) invalidateSetupValidation()
        if (next.primaryIsTop != store.session.defaultDisplayIsTop) {
            store.update { it.withDefaultDisplayIsTop(next.primaryIsTop) }
        }
        if (next.sort != model.sort) store.setLibrarySort(next.sort)
        if (next.themeIndex != model.themeIndex) {
            store.setTheme(next.themeIds[next.themeIndex])
            onTheme(next.themeIndex)
        }
        if (next.iconThemeIndex != model.iconThemeIndex) {
            store.setIconTheme(next.iconThemeIndex?.let { themeIds.getOrNull(it) })
        }
        if (next.music != model.music) {
            store.setMusic(next.music.enabled, next.music.volume, next.music.trackId)
            onMusic(next.music)
        }
        if (next.backgroundPinned && (!model.backgroundPinned || next.backgroundMotion != model.backgroundMotion)) {
            store.setBackgroundMotion(next.backgroundMotion)
        }
        if (next.motionSpeed != model.motionSpeed) {
            store.setMotionSpeed(next.motionSpeed)
        }
        if (next.brightnessAllowed && next.brightness != model.brightness) onBrightness(next.brightness)
        if (next.artwork != model.artwork) store.setArtworkEnabled(next.artwork)
        val ready = if (!next.connectOpen) next.copy(connectBusy = false) else next
        model = if (ready.libraryGrid) {
            ready.copy(appActions = null, tileActions = null, playerSaves = playerSaveSettings())
        } else {
            withShelf(ready.copy(libraryGrid = false))
        }
        if (connectClosed) showQueuedPrompt()
        logHomeTile()
    }

    /**
     * The bottom labels ellipsize, and a presentation-display dump is often empty.
     * The capture follows this line to the Game Boy Advance folder.
     */
    private fun logHomeTile() {
        if (!showingHome() || home.board.allOpen || home.board.openFolderId != null) {
            loggedHomeTile = null
            return
        }
        if (model.dialog != null || model.panel != null || model.connectOpen) return
        val title = home.face(model.focus.cellIndex)?.title?.takeIf { it.isNotBlank() } ?: return
        if (title == loggedHomeTile) return
        loggedHomeTile = title
        Log.i("Foldcade", "home-ui tile $title")
    }

    private fun countFor(grid: HomeGrid): Int = when (grid) {
        HomeGrid.StandIns -> home.visibleCount()
        else -> listed(grid).size
    }

    private fun listed(grid: HomeGrid): List<LaunchableApp> = when (grid) {
        HomeGrid.StandIns -> emptyList()
        HomeGrid.AndroidGames -> appsOn(AndroidShelf.Games, installed, shelfState, playerPackages)
        HomeGrid.Apps -> appsOn(AndroidShelf.Apps, installed, shelfState, playerPackages)
        HomeGrid.HiddenApps -> hiddenApps(installed, shelfState, playerPackages)
    }.sortedWith(compareBy<LaunchableApp> { it.label.lowercase() }.thenBy { it.packageName })

    private fun actionsFor(snapshot: PickerModel, game: ShelfGame?): AppActions? {
        val packageName = game?.androidPackage ?: return null
        if (snapshot.homeGrid == HomeGrid.StandIns) return null
        val record = shelfState.record(packageName)
        return AppActions(
            favorite = record.favorite,
            onGamesShelf = snapshot.homeGrid == HomeGrid.AndroidGames,
            hiddenShelf = snapshot.homeGrid == HomeGrid.HiddenApps,
            destinations = home.placementDestinations(androidHomeId(packageName)),
        )
    }

    private fun focusPackage(next: PickerModel, packageName: String?): PickerModel {
        if (packageName == null || next.homeGrid == HomeGrid.StandIns) return next
        val source = listed(next.homeGrid).indexOfFirst { it.packageName == packageName }
        if (source < 0) return next
        val order = displayOrder(next.copy(count = listed(next.homeGrid).size))
        val display = order.indexOf(source).takeIf { it >= 0 } ?: source
        val column = GridStrip.column(display)
        val focus = next.focus.copy(cellIndex = display, lastColumn = column)
        val open = next.panel
        val panel = open?.copy(grid = open.grid.copy(cellIndex = display, lastColumn = column))
        return next.copy(focus = focus, panel = panel)
    }

    private fun LaunchableApp.asTile(grid: HomeGrid): ShelfGame {
        val opened = plugins.openInstalledApp(packageName)
        val meta = when (grid) {
            HomeGrid.AndroidGames -> Copy.androidGameMeta
            HomeGrid.HiddenApps -> Copy.hiddenApps
            else -> Copy.appMeta
        }
        return ShelfGame(
            id = opened.sessionId,
            title = label,
            shortText = meta,
            androidPackage = packageName,
            favorite = shelfState.record(packageName).favorite,
        )
    }

    private fun showingHome(snapshot: PickerModel = model): Boolean =
        !snapshot.libraryGrid && snapshot.homeGrid == HomeGrid.StandIns

    /** The home grid's top level with nothing open over it, where B has nothing left to close. */
    fun atRest(): Boolean =
        homeActive() && home.board.openFolderId == null && !home.board.allOpen && home.board.hold == null &&
            home.iconPick == null && renameDraft() == null

    private fun homeActive(): Boolean =
        showingHome() && model.dialog == null && model.panel == null && model.settings == null &&
            model.playActivity == null && model.saveConflict == null && !model.connectOpen

    private fun showBoard(focus: GridFocus, keepDialog: Boolean) {
        val count = home.visibleCount()
        // An empty slot past the last tile keeps the cursor, as it does on the strip.
        val cell = focus.cellIndex.coerceIn(0, (GridStrip.reach(count) - 1).coerceAtLeast(0))
        publish(
            model.copy(
                libraryGrid = false,
                homeGrid = HomeGrid.StandIns,
                panel = null,
                arranging = false,
                hold = null,
                order = emptyList(),
                count = count,
                focus = GridFocus(
                    cellIndex = cell,
                    lastColumn = if (count == 0) 0 else GridStrip.column(cell),
                ),
                addNewToHome = home.board.addNewToHome,
                shelfEpoch = model.shelfEpoch + 1,
                gridKind = GridKind.Games,
                emptyGrid = EmptyGrid.None,
                atLibraryRoot = home.board.openFolderId == null && !home.board.allOpen,
                dialog = if (keepDialog) model.dialog else null,
            ),
        )
    }

    /** The library a [homeGameId] belongs to, or null for a tile that is not a library game. */
    private fun libraryOf(id: String): String? =
        id.removePrefix("lib:").takeIf { it != id }?.substringBefore(':')

    private fun syncHome() {
        home.rememberShelf(Shelf.games)
        home.remerge(
            shelfItems(),
            appItems(),
            libraryItems,
            homePlatforms(),
            libraryGames,
            unsettled = { id ->
                libraryOf(id)?.let { it !in settledLibraries } == true ||
                    (!moonlightSettled && isMoonlightRemoteKey(id)) ||
                    (!droidDeckSettled && id.startsWith(Shelf.DROIDDECK_PREFIX)) ||
                    (!installedAppsSettled && id.startsWith("android:")) ||
                    (GameNativeLibrary.ID !in settledLibraries && id.startsWith("gamenative."))
            },
        )
        store.saveHomeBoard(home.encoded())
    }

    private fun shelfItems(): List<HomeItem> = Shelf.games.mapNotNull { game ->
        // The app itself is on Home, in Emulators, so its folder holds only games.
        if (game.id in Shelf.APP_TILES) return@mapNotNull null
        val kind = when {
            game.id == Shelf.PC_TILE || game.libraryId == "gamenative" -> HomeKind.GameNative
            game.id == "moonlight" || game.libraryId == "moonlight" -> HomeKind.Moonlight
            game.id == Shelf.DROIDDECK_TILE || game.libraryId == DroidDeckLibrary.ID -> HomeKind.DroidDeck
            !game.platformId.isNullOrBlank() && game.mark == null -> HomeKind.Rom
            else -> HomeKind.Loose
        }
        // The demo marks stay on the shelf. The home grid is system folders.
        if (kind == HomeKind.Loose) return@mapNotNull null
        HomeItem(
            id = game.id,
            title = game.title,
            kind = kind,
            platformId = game.platformId,
            mark = game.mark ?: game.platformId?.let { kitMark(it) },
            lastPlayedMillis = lastPlayedMillis(game.id) ?: 0L,
        )
    }

    /** Installed apps. One a player plugin launches games in is an emulator, filed apart from the games. */
    private fun appItems(): List<HomeItem> = installed.map { app ->
        val kind = when {
            app.packageName in playerPackages -> HomeKind.Emulator
            app.systemGame -> HomeKind.AndroidGame
            else -> HomeKind.AndroidApp
        }
        HomeItem(
            id = androidHomeId(app.packageName),
            title = app.label,
            kind = kind,
            mark = bucketMark(kind),
        )
    }

    private fun homePlatforms(): List<HomePlatform> {
        val known = listOf(
            HomePlatform("nintendo-3ds", "Nintendo 3DS", setOf("3ds", "n3ds", "new-nintendo-3ds")),
            HomePlatform("nintendo-ds", "Nintendo DS", setOf("nds")),
            HomePlatform("game-boy", "Game Boy", setOf("gb")),
            HomePlatform("game-boy-color", "Game Boy Color", setOf("gbc")),
            HomePlatform("game-boy-advance", "Game Boy Advance", setOf("gba")),
            HomePlatform("game-gear", "Game Gear", setOf("gg")),
            HomePlatform("psp", "PSP", setOf("psp")),
        )
        val byId = LinkedHashMap<String, HomePlatform>()
        // Lowest priority: a name the library listed, so a platform Foldcade does not
        // define (RomM's Sega CD) is named "Sega CD", not its id "segacd".
        listedPlatformNames.forEach { (id, name) -> byId[id] = HomePlatform(id, name, emptySet(), kitMark(id)) }
        known.forEach { byId[it.id] = it }
        extraPlatforms.forEach { byId[it.id] = it }
        plugins.platformDefinitions().forEach { platform ->
            byId[platform.id] = HomePlatform(
                id = platform.id,
                name = platform.displayName,
                aliases = platform.aliases,
                mark = kitMark(platform.id),
            )
        }
        return byId.values.toList()
    }

    private fun recentOrder(): List<Int> =
        recentlyPlayedIndices(Shelf.games.map { it.id }, lastPlayedMillis)

    private fun initial(): PickerModel {
        val names = themeNames.ifEmpty { listOf(Copy.builtIn) }
        val index = store.themeIndex(themeIds, names)
        val pinned = store.backgroundMotionPinned()
        val savedMotion = if (pinned) {
            store.backgroundMotion()
        } else {
            themeMotions.getOrElse(index) { BackgroundMotion.Off }
        }
        val choices = backgroundChoices ?: app.foldcade.language.PickerModel(count = 0, rowsPerPage = 1).backgroundChoices
        val motion = app.foldcade.language.resolveBackgroundChoice(savedMotion, choices, themeMotions.getOrElse(index) { BackgroundMotion.Off })
        val stored = HomeMusicSetting(store.musicEnabled(), store.musicVolume(), store.musicTrackId())
        val offered = offeredMusicTracks(homeTracks, themeTracks.getOrNull(index))
        val selected = selectedMusicTrack(offered, stored.trackId)
        // Playback can fall back while a track is unavailable without erasing the user's choice.
        syncHome()
        return PickerModel(
            count = home.visibleCount(),
            addNewToHome = home.board.addNewToHome,
            rowsPerPage = 2,
            primaryIsTop = store.session.defaultDisplayIsTop,
            folderGrantPending = store.folderGrantPending(),
            music = stored.copy(trackId = selected.id),
            trackTitle = selected.title,
            homeTracks = homeTracks,
            themeTracks = themeTracks,
            themes = names,
            themeIds = themeIds,
            themeIndex = index,
            iconThemeIndex = store.iconTheme()?.let { themeIds.indexOf(it).takeIf { index -> index >= 0 } },
            backgroundChoices = choices,
            themeMotions = themeMotions,
            backgroundMotion = motion,
            backgroundPinned = pinned,
            motionSpeed = store.motionSpeed(),
            artwork = store.artworkEnabled(),
            sort = store.librarySort(),
            recentFirst = recentOrder(),
        )
    }

    /** The move clip when the theme has one. Otherwise the short original tick. */
    private fun tickSlider(themeIndex: Int) {
        if (!cue(themeIndex, "move")) bareTick()
    }

    /** Volume and brightness steps, and a choice stepped with left or right. Each ticks while the row stays put. */
    private fun valueAdjusted(before: PickerModel, after: PickerModel): Boolean =
        before.music.volume != after.music.volume || before.music.trackId != after.music.trackId ||
            before.brightness != after.brightness || before.themeIndex != after.themeIndex ||
            before.iconThemeIndex != after.iconThemeIndex || before.backgroundMotion != after.backgroundMotion ||
            before.motionSpeed != after.motionSpeed || before.sort != after.sort ||
            before.primaryIsTop != after.primaryIsTop

    private fun cueMeaning(meaning: Meaning, before: PickerModel, after: PickerModel) {
        when (meaning) {
            Meaning.Activate -> cue(after.themeIndex, "activate")
            Meaning.Back -> cue(after.themeIndex, "back")
            // A chord runs a row, so it sounds as A on that row would.
            Meaning.Menu, Meaning.View, Meaning.Options, Meaning.ActivateBottom -> {
                val panel = before.panel
                if (panel != null && chordIndex(panelRows(panel, before), meaning) != null) {
                    cue(after.themeIndex, "activate")
                }
            }
            Meaning.MoveUp, Meaning.MoveDown, Meaning.MoveLeft, Meaning.MoveRight -> {
                val moved = before.focus != after.focus || before.panel?.index != after.panel?.index
                val slid = valueAdjusted(before, after)
                if (slid) tickSlider(after.themeIndex) else if (moved) cue(after.themeIndex, "move")
            }
            else -> Unit
        }
        if (before.dialog?.kind != DialogKind.Ok && after.dialog?.kind == DialogKind.Ok) {
            cue(after.themeIndex, "notify")
        }
        if (after.notices.size > before.notices.size) cue(after.themeIndex, "notify")
    }
}

/** How a finished sync reads on its tile. Unchanged with a note is a sync that could not run. */
internal fun syncMark(result: SyncResult): SyncMark = when (result.outcome) {
    SyncOutcome.KeptBoth -> SyncMark.KeptBoth
    SyncOutcome.Queued -> SyncMark.Later
    SyncOutcome.Unchanged -> if (result.note == null) SyncMark.Synced else SyncMark.NotSynced
    else -> SyncMark.Synced
}
