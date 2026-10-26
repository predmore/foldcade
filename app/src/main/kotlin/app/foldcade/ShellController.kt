package app.foldcade

import app.foldcade.language.HomeUndo
import app.foldcade.language.SAME_PREFIX
import app.foldcade.language.SameSource
import app.foldcade.language.foldSameGames
import app.foldcade.language.sameGameIds
import app.foldcade.language.sameKey
import app.foldcade.language.DeleteHold
import app.foldcade.language.Row
import app.foldcade.language.promptKeyOf
import app.foldcade.language.HOME_ANDROID_GAMES
import app.foldcade.language.HOME_ANDROID_APPS
import app.foldcade.language.HOME_RECENT
import app.foldcade.language.HOME_ROOT
import app.foldcade.language.androidFolderOf
import app.foldcade.language.groupOnHome
import app.foldcade.language.ArtMatch
import app.foldcade.language.ArtKind
import app.foldcade.language.ArtPickerState
import app.foldcade.language.IconPick
import app.foldcade.language.collectionLook
import app.foldcade.language.BulkAction
import app.foldcade.language.HomeSelection
import app.foldcade.language.TileActions
import android.util.Log
import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameActivity
import app.foldcade.api.plugin.Platform
import app.foldcade.api.plugin.HostedSaveFolder
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.SaveSyncState
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.language.SaveHealth
import app.foldcade.api.plugin.SyncOutcome
import app.foldcade.api.plugin.SyncResult
import app.foldcade.artwork.ArtQuery
import app.foldcade.localfolder.LocalFolderBackend
import app.foldcade.plugins.droiddeck.DroidDeckLibrary
import app.foldcade.plugins.droiddeck.droidDeckSteamAppId
import app.foldcade.plugins.gamenative.GameNativeLibrary
import app.foldcade.plugins.gamenative.steamAppId
import app.foldcade.plugins.moonlight.MoonlightLibrary
import app.foldcade.plugins.moonlight.MoonlightPlatform
import app.foldcade.plugins.moonlight.isMoonlightRemoteKey
import app.foldcade.host.PluginHost
import app.foldcade.language.FoldNotice
import app.foldcade.language.FindEntry
import app.foldcade.language.FindKind
import app.foldcade.language.downloadFacts
import app.foldcade.language.StreamHostSetting
import app.foldcade.language.streamHostLine
import app.foldcade.language.HintKey
import app.foldcade.language.HomeKeys
import app.foldcade.language.PanelLevel
import app.foldcade.language.findKeyboardHints
import app.foldcade.language.findResultHints
import app.foldcade.language.KeyFocus
import app.foldcade.language.brightnessPrompt
import app.foldcade.language.brightnessPosition
import app.foldcade.language.settingsCategories
import app.foldcade.language.withFolders
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.ConnectField
import app.foldcade.language.SidePanel
import app.foldcade.language.ConnectKind
import app.foldcade.language.Chrome
import app.foldcade.language.Copy
import app.foldcade.language.DialogKind
import app.foldcade.language.DialogState
import app.foldcade.language.HomeGrid
import app.foldcade.language.HoldDrop
import app.foldcade.language.LaunchableApp
import app.foldcade.language.Effect
import app.foldcade.language.EmptyGrid
import app.foldcade.language.GridFocus
import app.foldcade.language.actionRows
import app.foldcade.language.chordIndex
import app.foldcade.language.GridKind
import app.foldcade.language.HeroFolder
import app.foldcade.language.folderCount
import app.foldcade.language.HeroItem
import app.foldcade.language.HeroSubject
import app.foldcade.language.HomeFolder
import app.foldcade.language.HomeItem
import app.foldcade.language.HomeKind
import app.foldcade.language.isApp
import app.foldcade.language.HomePlatform
import app.foldcade.language.androidHomeId
import app.foldcade.language.bucketMark
import app.foldcade.language.homeGameId
import app.foldcade.language.homeGameKey
import app.foldcade.language.Motion
import app.foldcade.language.SyncMark
import app.foldcade.language.TileActivity
import app.foldcade.language.PlayKey
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
import app.foldcade.language.scrolledTo
import app.foldcade.language.shownFirstColumn
import app.foldcade.language.PromptKey
import app.foldcade.language.RemoveVerb
import app.foldcade.language.ThorStyle
import app.foldcade.language.actionFaces
import app.foldcade.language.faceMapWithConfirm
import app.foldcade.language.offerButtonCalibration
import app.foldcade.language.resolveFaceMap
import app.foldcade.language.PickerModel
import app.foldcade.language.PlayerSaveSetting
import app.foldcade.language.EdenSetting
import app.foldcade.language.RetroArchSetting
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
    /** A game's total play time, which a folder sorted by Most played orders by. */
    private val playMillis: (String) -> Long? = { null },
    /** Delete shortcut was confirmed. The app drops its pinned shortcut. */
    private val onMoonlightRemoved: (remoteKey: String) -> Unit = {},
    /** Delete shortcut was confirmed. The app drops its pinned shortcut. */
    private val onDroidDeckRemoved: (remoteKey: String) -> Unit = {},
    /** The R1 slider moved to this position. The app stores it. */
    private val onBrightness: (position: Int) -> Unit = {},
    private val onArtApply: (String, ArtKind, String?) -> Unit = { _, _, _ -> },
    private val savedArtMatch: (String) -> ArtMatch? = { null },
    private val onArtMatch: (String, ArtMatch) -> Unit = { _, _ -> },
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    /** Copies were folded into new tiles: tile id to its copies, first preferred. The app moves their art. */
    private val onSameGames: (Map<String, List<String>>) -> Unit = {},
    /** "0.1.2 (230)", read from the installed package. Settings › About shows it. */
    private val appVersion: String = "",
    private val onSaveHealthChanged: (Map<String, String>) -> Unit = {},
    private val onSavePickerClosed: () -> Unit = {},
) {
    var artPicker by mutableStateOf<ArtPickerState?>(null)
        private set

    /** Find, open over the grid, which stays as it was under it. Null while it is closed. */
    var find by mutableStateOf<FindSession?>(null)
        private set

    /** Counts Show on Home landings. The grid's ring bursts on the focused tile at each new one. */
    var revealPulse by mutableStateOf(0)
        private set

    /** The last landing a ring burst for. A ring drawn later, as after a game, does not burst again. */
    private var revealBurst = 0

    /**
     * True once for each Show on Home landing: for the first grid ring to see it, which is the
     * new one drawn as Find closes, when it was Find that went there.
     */
    fun takeRevealBurst(): Boolean {
        if (revealPulse == revealBurst) return false
        revealBurst = revealPulse
        return true
    }
    private var keptArtTarget: Pair<String, GridFocus>? = null

    private fun closeArtAfterFocusChange() {
        val state = artPicker ?: return
        val focused = focusedGame()
        val retained = keptArtTarget?.takeIf { it.second == model.focus && focused?.id.isNullOrEmpty() }?.first
        val game = retained?.let { homeShelf(home.faceOf(it)) } ?: focused
        if (model.focus.chrome != null || game?.let(::artQuery)?.key != state.gameId) artPicker = null
    }

    fun artCandidates(state: ArtPickerState, loadingResult: Boolean = true) {
        closeArtAfterFocusChange()
        val current = artPicker ?: return
        if (current.gameId == state.gameId && current.kind == state.kind && current.match?.id == state.match?.id) {
            artPicker = if (loadingResult) state.copy(preview = current.preview) else state
        }
    }

    fun artMatches(gameId: String, matches: List<ArtMatch>, error: Boolean = false) {
        closeArtAfterFocusChange()
        val state = artPicker?.takeIf { it.gameId == gameId } ?: return
        val choices = (listOfNotNull(state.match) + matches).distinctBy { it.id }.take(8)
        artPicker = state.copy(matches = choices, match = state.match ?: choices.firstOrNull(), findingMatches = false,
            loading = choices.isNotEmpty(), error = error)
    }

    fun artInput(meaning: Meaning) {
        closeArtAfterFocusChange()
        val state = artPicker ?: return
        when (meaning) {
            Meaning.Back, Meaning.CancelHold -> artPicker = null
            Meaning.Activate -> state.focused?.let {
                state.match?.takeIf { it != savedArtMatch(state.gameId) }?.let { onArtMatch(state.gameId, it) }
                onArtApply(state.gameId, state.kind, it.url)
            }
            Meaning.ActivateBottom -> {
                state.match?.takeIf { it != savedArtMatch(state.gameId) }?.let { onArtMatch(state.gameId, it) }
                onArtApply(state.gameId, state.kind, null)
                artPicker = state.copy(preview = false)
            }
            else -> {
                val next = state.navigate(meaning)
                next.match?.takeIf { it != state.match }?.let { onArtMatch(state.gameId, it) }
                artPicker = next
            }
        }
    }

    private var installed: List<LaunchableApp> = emptyList()
    /**
     * The emulators on the device, so the hero can tell which one a game opens in without
     * asking Android while it draws. State, so a hero already drawn follows an install.
     */
    private var installedPlayers by mutableStateOf<Set<String>>(emptySet())
    private var playerPackages: Set<String> = emptySet()
    /** Each copy's id to the tile a game several libraries have is shown as. */
    private var sameAliases: Map<String, String> = store.sameGames()

    /** What [foldSameGames] knows of each library game, by its own home id. */
    private val sameSources = HashMap<String, SameSource>()

    /** Each folded tile's copies listed now, first preferred. */
    private var sameCopies: Map<String, List<String>> = emptyMap()

    // A board from before collections is kept as it was before this release first rewrites it.
    private val home = store.run {
        keepHomeBoardBeforeCollections()
        HomeSession(homeBoardRaw(), sameGames = sameAliases).also { session ->
            // Apps a release with Android shelves hid there are hidden on Home, the one Hidden place.
            val hidden = takeShelfHiddenApps()
            hidden.forEach { session.hide(androidHomeId(it)) }
            if (hidden.isNotEmpty()) saveHomeBoard(session.encoded())
        }
    }
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

    private var committedHome: String? = store.homeBoardRaw()
    private var saveHealthReady = false
    private var current by mutableStateOf(initial())

    /**
     * Every change keeps the columns the grid showed, so the next focus scrolls only as far as it must.
     * A change that scrolls the strip itself, as a swipe does, keeps where it put it.
     */
    var model: PickerModel
        get() = current
        private set(next) {
            val changedView = next.homeGrid != current.homeGrid || next.libraryGrid != current.libraryGrid ||
                next.gridKind != current.gridKind || next.primaryIsTop != current.primaryIsTop ||
                (next.settings != null && current.settings == null) ||
                (next.playActivity != null && current.playActivity == null) ||
                (next.connectOpen && !current.connectOpen)
            val prepared = if (home.selection != null && changedView) {
                home.finishSelection()
                commitHome(catalog = true)
                if (next.libraryGrid) next else withShelf(next)
            } else next
            // A session's hold lives on Play activity's list, which has no menu: that list changing cancels it.
            val holdMoved = if (current.deleteHold?.row == Row.RemoveSession) next.playActivity != current.playActivity
                else next.panel == null || next.panel != current.panel
            val canceled = current.deleteHold != null && (holdMoved || next.dialog != null || next.settings != null || next.connectOpen)
            val stable = app.foldcade.language.retainSettingsCategory(current, app.foldcade.language.retainMenuRows(current, prepared))
            undo = when {
                stable.panel == null -> undo?.resume(nowMillis())
                stable.panel?.let { Row.Undo in panelRows(it, stable) } == true -> undo?.pause(nowMillis())
                else -> undo
            }
            current = stable.copy(firstColumn = shownFirstColumn(stable),
                // A replacement surface must not leave a return target for a later form.
                connectReturnSettings = stable.connectReturnSettings.takeIf { stable.connectOpen },
                deleteHold = if (canceled) null else next.deleteHold,
                deleteProgress = if (canceled) 0f else next.deleteProgress,
                deleteShake = next.deleteShake + if (canceled) 1 else 0)
            if (canceled) cue(next.themeIndex, "back")
            closeArtAfterFocusChange()
        }

    private var inputDeviceKey: String = "builtin"

    var connectToken by mutableStateOf("")
        private set

    private var setupValidation = 0
    var connectVisit: Int = 0
        private set
    var connectOriginRevision: Int = 0
        private set

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

    private var saveHealthEpoch by mutableStateOf(0)
    private var unsyncedSaves by mutableStateOf<Map<String, Set<String>>>(emptyMap())
    private data class SaveReachability(val locations: List<SaveDeclaration>, val reachable: Boolean)
    private val reachableSaves = mutableMapOf<Pair<String, String>, SaveReachability>()
    private val saveSupport = mutableMapOf<Pair<String, Game>, GameSaveSupport>()
    private var healthPlayerSettings: List<PlayerSaveSetting>? = null
    private var healthPlayerFolders: List<String?> = emptyList()

    internal fun setUnsyncedSaves(libraryId: String, keys: Set<String>) {
        if (unsyncedSaves[libraryId] == keys) return
        unsyncedSaves = unsyncedSaves + (libraryId to keys)
        refreshSavePickerHealth()
    }

    internal fun noteSaveReachable(game: Game, playerId: String, reachable: Boolean) {
        val player = plugins.player(playerId) ?: return
        reachableSaves[homeGameId(game.backendId, game.remoteKey) to playerId] = SaveReachability(player.saveDeclarations(game), reachable)
        saveSupport.remove(playerId to game)
        saveHealthEpoch += 1
        refreshSavePickerHealth()
    }

    internal fun saveGame(libraryId: String, remoteKey: String): Game? = libraryGames[homeGameId(libraryId, remoteKey)]

    /** The Settings row owns a shared folder's fix, such as RetroArch across several systems. */
    internal fun saveNoticePlayerId(playerId: String): String {
        val player = plugins.player(playerId) ?: return playerId
        if (player !is SaveFolderHolder) return playerId
        val key = player.saveFolderId()
        return plugins.playerIds().firstOrNull { id ->
            val candidate = plugins.player(id)
            candidate is SaveFolderHolder && candidate.saveFolderId() == key
        } ?: playerId
    }

    internal fun savePlayer(platformId: String) = preferredPlayer(
        plugins.playersFor(platformId),
        runnable = { runnableWith(it, it.packageNames.firstOrNull(installedPlayers::contains)) },
        rank = ::playerRank,
        installed = installedPlayers::contains,
    )?.takeIf { it.packageNames.any(installedPlayers::contains) }

    internal fun saveHealthOf(id: String, pressed: Game? = null): SaveHealth {
        saveHealthEpoch // Folder checks, player installs, and catalog changes invalidate this read.
        val homeId = pressed?.let { homeGameId(it.backendId, it.remoteKey) } ?: id
        val route = launchRoute(tileOf(homeId), pressed) ?: return SaveHealth.Fine
        val waiting = route.servers.any { it.remoteKey in unsyncedSaves[it.backendId].orEmpty() }
        // A save still waiting for the server says so even after its emulator is gone.
        val player = savePlayer(route.play.platformId) ?: return if (waiting) SaveHealth.Waiting else SaveHealth.Fine
        val saves = saveSupport.getOrPut(player.id to route.saves) { readGameSaveSupport(route.saves, player) }
        val reachability = reachableSaves[homeGameId(route.saves.backendId, route.saves.remoteKey) to player.id]
        return gameSaveHealth(
            route.saves, player, route.servers.isNotEmpty(),
            waiting = waiting,
            sync = saveSync[player.id],
            setup = setupSteps[player.id],
            reachable = reachability?.takeIf { it.locations == saves.declarations }?.reachable,
            saves = saves,
        )
    }

    fun saveHealthAt(source: Int, game: ShelfGame?): SaveHealth {
        if (game == null) return SaveHealth.Fine
        val pressed = if (model.libraryGrid) entries.getOrNull(source)?.game else null
        return saveHealthOf(pressed?.let { homeGameId(it.backendId, it.remoteKey) } ?: game.id, pressed)
    }

    private fun refreshSaveHealth() {
        if (!saveHealthReady) return
        saveSupport.clear()
        saveHealthEpoch += 1
        val onHome = app.foldcade.language.onHomeIds(home.board)
        val platforms = HashSet<String>()
        for ((id, game) in libraryGames) {
            if (game.availability != Availability.LocalOnly && tileOf(id) in onHome) platforms.add(game.platformId)
        }
        val players = linkedMapOf<String, String>()
        for (platformId in platforms) {
            val player = savePlayer(platformId) ?: continue
            if (playerSaveIssue(player, saveSync[player.id], setupSteps[player.id]) == null) continue
            players[saveNoticePlayerId(player.id)] = player.displayName
        }
        refreshSavePickerHealth()
        onSaveHealthChanged(players)
    }

    /** Tiles whose game is still open in its player, see [RunningGames]. */
    var running by mutableStateOf<Set<String>>(emptySet())

    /** Moonlight's computers by host key, see [MoonlightHosts]. */
    internal var moonlightHosts by mutableStateOf<Map<String, MoonlightHost>>(emptyMap())
        private set

    /** Moonlight tiles a computer is streaming now, with its name. */
    private var streaming by mutableStateOf<Map<String, String>>(emptyMap())

    /** Computers Foldcade paired with itself, by host key. */
    private var pairedHosts: Set<String> = emptySet()

    internal fun setMoonlightHosts(hosts: Map<String, MoonlightHost>) {
        if (hosts == moonlightHosts) return
        moonlightHosts = hosts
        streaming = streamingTiles(hosts)
        publishStreamHosts()
    }

    fun setPairedHosts(keys: Set<String>) {
        if (keys == pairedHosts) return
        pairedHosts = keys
        publishStreamHosts()
    }

    /** Settings › Library's switch for each computer, by name. */
    private fun publishStreamHosts() {
        val rows = moonlightHosts.map { (key, host) -> StreamHostSetting(key, host.name, key in pairedHosts) }
            .sortedBy { it.name.lowercase() }
        if (rows != model.streamHosts) model = model.copy(streamHosts = rows)
    }

    /** False while Foldcade can't tell whether a player closed, see [FoldcadeApp.closesVisible]. Nothing reads Paused then. */
    var resumable by mutableStateOf(false)

    /** Cells for the current grid. Empty when the grid is an empty state. */
    var entries by mutableStateOf<List<GridEntry>>(emptyList())
        private set

    var activeLibraryId: String? = null
        private set

    private var librarySavesMenuGame: Game? = null
    private var platformEntries: List<GridEntry> = emptyList()
    private var platformFocus: GridFocus = GridFocus()
    private var platformOrder: List<Int> = emptyList()
    private var loggedHomeTile: String? = null

    private var undo: HomeUndo? = null
    private var undoFocus = GridFocus()
    private var undoGrid = HomeGrid.StandIns
    private var completingDelete = false
    private val inputWindows = mutableMapOf<String, Boolean>()
    private val screenWindows = mutableMapOf<HostScreen, String>()
    private val inputKeys = mutableMapOf<String, Int>()
    private val heldActions = app.foldcade.language.HeldKeyGuard()

    fun registerInputWindow(screen: HostScreen, window: String) { screenWindows[screen] = window }
    fun inputWindow(screen: HostScreen): String = screenWindows[screen] ?: screen.name

    fun inputFocus(window: String, focused: Boolean) {
        inputWindows[window] = focused
        if (!focused) cancelDeleteHold(window)
    }

    /** A second key belongs to a fresh action, so it first cancels and consumes this hold. */
    fun inputKey(meaning: Meaning?, window: String, key: Int? = null): Boolean {
        if (key != null) inputKeys[window] = key
        val hold = model.deleteHold ?: return false
        if (hold.meaning == meaning && hold.inputWindow == window && (hold.inputKey == null || hold.inputKey == key)) return false
        cancelDelete()
        return true
    }

    private fun activeUndo(): HomeUndo? = undo?.takeIf { it.available(nowMillis()) }

    fun tickRecovery(): Effect? {
        if (undo != null && activeUndo() == null) { undo = null; publish(model.copy(undoVerb = null)) }
        val hold = model.deleteHold ?: return null
        if (inputWindows[hold.inputWindow] == false) { cancelDelete(); return null }
        val session = hold.row == Row.RemoveSession
        if (session) {
            val page = model.playActivity
            if (page?.sessions?.getOrNull(page.session) != hold.gameId) { cancelDelete(); return null }
        } else {
            val selected = model.panel?.let { panelRows(it, model).getOrNull(it.index) }
            if (selected != hold.row || model.tileActions?.game != hold.gameId) { cancelDelete(); return null }
        }
        if (hold.complete(nowMillis())) {
            model = model.copy(deleteHold = null, deleteProgress = 0f)
            completingDelete = true
            cue(model.themeIndex, "notify")
            // A menu row runs on A; a session's list has no rows, so the key held is pressed again.
            val press = if (session) hold.meaning else Meaning.Activate
            return try { onMeaning(press, HostScreen.Top, inputWindow = hold.inputWindow) } finally { completingDelete = false }
        }
        model = model.copy(deleteProgress = hold.progress(nowMillis()))
        return null
    }

    fun cancelDeleteHold(window: String? = null) {
        if (window == null || model.deleteHold?.inputWindow == window) cancelDelete()
    }

    private fun cancelDelete() {
        if (model.deleteHold == null) return
        model = model.copy(deleteHold = null, deleteProgress = 0f, deleteShake = model.deleteShake + 1)
        cue(model.themeIndex, "back")
    }

    fun onMeaning(meaning: Meaning, screen: HostScreen, repeatCount: Int = 0, inputWindow: String = inputWindow(screen)): Effect? {
        if (!completingDelete && heldActions.swallows(meaning.ordinal, repeatCount, capturing = model.deleteHold?.meaning == meaning)) return null
        if (!completingDelete && model.deleteHold != null) {
            inputKey(meaning, inputWindow, inputKeys[inputWindow])
            return null
        }
        val panel = model.panel
        val rows = panel?.let { panelRows(it, model) }.orEmpty()
        val at = if (meaning == Meaning.Activate) panel?.index?.coerceIn(0, (rows.size - 1).coerceAtLeast(0)) else chordIndex(rows, meaning)
        val row = at?.let { rows.getOrNull(it) }
        if (find == null && model.homeGrid == HomeGrid.Hidden && model.panel == null && model.playActivity == null && model.savePicker == null && (meaning == Meaning.Activate || meaning == Meaning.ActivateBottom)) return null
        val before = home.board
        val beforeFocus = model.focus
        val beforeGrid = model.homeGrid
        val beforeSaved = home.encoded()
        val target = model.tileActions?.game
        val bulkIds = model.tileActions?.bulk
        val removeVerb = model.tileActions?.remove
        val result = applyMeaning(meaning, screen, repeatCount, inputWindow)
        if (row != Row.Undo && (home.encoded() != beforeSaved || row == Row.MoveTile)) {
            // Taking a game out of Add to's choice: a collection, or Home, where it was a pin.
            val wasIn = { id: String?, choice: String ->
                id != null && if (choice == HOME_ROOT) id in before.slots else id in before.folders[choice]?.slots.orEmpty()
            }
            val unchecked = row is Row.Collection && (bulkIds?.all { wasIn(it, row.folderId) } ?: wasIn(target, row.folderId))
            val reversible = row == Row.RemoveTile || row == Row.Hide || row == Row.Tidy || unchecked
            if (reversible) {
                undo = HomeUndo(if (bulkIds != null) home.bulkBefore ?: before else before,
                when { row is Row.Collection -> if (row.folderId == HOME_ROOT) Copy.unpinned else Copy.removed
                    row == Row.Tidy -> Copy.tidied
                    row == Row.Hide -> Copy.hidden
                    removeVerb == RemoveVerb.Unpin -> Copy.unpinned
                    else -> Copy.removed }, nowMillis(), after = if (bulkIds != null) home.bulkAfter ?: home.board else home.board).withFolderCleanup(home.board)
                undoFocus = beforeFocus
                undoGrid = beforeGrid
            }
            publish(model.copy(undoVerb = activeUndo()?.verb))
        }
        return result
    }

    private fun applyMeaning(meaning: Meaning, screen: HostScreen, repeatCount: Int, inputWindow: String): Effect? {
        if (model.savePicker != null) {
            val (next, effect) = reduce(model, meaning, screen, repeatCount)
            publish(next)
            return effect
        }
        if (artPicker != null) {
            artInput(meaning)
            return null
        }
        if (model.panel == null && home.releaseMenu()) publish(model)
        find?.takeIf { findActive() }?.let { return findOutcome(it.handle(meaning)) }
        if (meaning == Meaning.View && canOpenFind()) {
            openFind()
            return null
        }
        syncPills()
        if (homeActive() && !opensPill(meaning)) {
            val before = model
            val wasInFolder = home.board.openFolderId != null
            val wasAll = home.board.allOpen
            val wasSelecting = home.selection != null
            val wasHeld = home.board.hold != null
            val wasInGroup = home.board.hold?.openFolderId != null
            val step = home.handle(meaning, model.focus.cellIndex, repeatCount)
            if (step != null && step.handled) {
                commitHome(catalog = wasSelecting && meaning == Meaning.Back)
                showBoard(step.focus ?: model.focus, keepDialog = true)
                if (!wasInFolder && home.openedFolderHasGame()) Log.i("Foldcade", "library-ui games")
                if (!wasHeld && home.board.hold != null) Log.i("Foldcade", "home-ui lifted")
                if (wasInGroup && home.board.hold?.openFolderId == null && home.board.hold != null) Log.i("Foldcade", "home-ui carried out")
                if (home.board.allOpen && !wasAll) Log.i("Foldcade", "home-ui all-games")
                val typed = step.cue
                if (typed != null) typed.slot?.let { cue(model.themeIndex, it) } else cueMeaning(meaning, before, model)
                return step.effect
            }
        }
        val before = model
        if (model.libraryGrid && model.panel == null && meaning == Meaning.Options) librarySavesMenuGame = libraryEntry(model)?.game
        val current = if (model.libraryGrid) model else withShelf(model)
        val (next, effect) = reduce(current, meaning, screen, repeatCount)
        if (current.panel == null && next.panel?.level == PanelLevel.Actions && showingHome() && find == null) {
            home.keepMenu(current.tileActions?.game)
        }
        // L1 or R1 opening over Find's menu leaves Find: those drawers go places of their own.
        if (find != null && next.panel?.let { it.level == PanelLevel.Root || it.pendingSide != null } == true) find = null
        // Rows may change while a key is arriving (for example, Undo just expired). Guard
        // the reducer's actual effect, rather than trusting the row last drawn on screen.
        if (!completingDelete && (effect is Effect.RemoveDownload || effect is Effect.DeleteShortcut)) {
            val row = if (effect is Effect.RemoveDownload) Row.RemoveDownload else Row.DeleteShortcut
            val game = if (effect is Effect.RemoveDownload) effect.gameId else (effect as Effect.DeleteShortcut).gameId
            val panel = current.panel ?: return null
            val index = panelRows(panel, current).indexOf(row)
            if (index >= 0 && repeatCount == 0 && inputWindows[inputWindow] != false) {
                val key = if (meaning == Meaning.ActivateBottom) actionFaces(current.faceMap).bottomGlyph else promptKeyOf(current.faceMap.confirmKey) ?: PromptKey.FaceA
                heldActions.capture(meaning.ordinal)
                model = current.copy(panel = panel.copy(index = index),
                    deleteHold = DeleteHold(row, key, nowMillis(), game, meaning, inputWindow, inputKeys[inputWindow]), deleteProgress = 0f)
            }
            return null
        }
        // Removing a play session is held first, as deleting a download is.
        if (!completingDelete && effect is Effect.RemovePlaySession) {
            if (current.playActivity?.sessions != null && repeatCount == 0 && inputWindows[inputWindow] != false) {
                heldActions.capture(meaning.ordinal)
                val key = actionFaces(current.faceMap).bottomGlyph
                model = current.copy(deleteHold = DeleteHold(Row.RemoveSession, key, nowMillis(), effect.sessionId, meaning, inputWindow, inputKeys[inputWindow]), deleteProgress = 0f)
            }
            return null
        }
        if (!next.connectOpen) connectToken = ""
        val targetGame = actionGame(current)
        val packageName = targetGame?.androidPackage
        val panelLabel = next.panel?.let { panel ->
            val row = app.foldcade.language.panelRows(panel, next).getOrNull(panel.index) ?: return@let null
            app.foldcade.language.rowText(row, next).label
        }
        if (panelLabel != null && (meaning == Meaning.LeftPanel || meaning == Meaning.MoveDown)) {
            Log.i("Foldcade", "home-ui row $panelLabel")
        }
        when (effect) {
            // Add to stays open, so the next choice is one press away, even when the game
            // leaves the collection on screen: the menu keeps acting on that game.
            is Effect.ToggleCollection -> {
                if (!model.libraryGrid) {
                    home.toggleCollection(next.focus.cellIndex, effect.folderId, effect.gameId)
                    commitHome()
                    syncHome()
                }
                publish(next.copy(notices = model.notices))
            }
            else -> publish(next)
        }
        cueMeaning(meaning, before, model, effect)
        if (effect == Effect.DismissButtonLabels) {
            store.saveButtonPrompt(inputDeviceKey, store.buttonPromptConfirm(inputDeviceKey), dismissed = true)
        }
        val handled = when (effect) {
            is Effect.ToggleCollection -> null
            is Effect.MoveApp -> (effect.gameId ?: packageName?.let(::androidHomeId))?.let { id ->
                changeTile {
                    home.ensureAppFolder(effect.folderId)
                    home.placeIn(id, effect.folderId)
                }
            }
            is Effect.Hide -> (effect.gameId ?: current.tileActions?.game)?.let { id -> changeTile { home.hide(id) } }
            is Effect.OpenFolder -> {
                openHomeFolder(effect.folderId)
                null
            }
            is Effect.ChooseArt -> {
                if (artPickerAvailable()) {
                    // The picker previews on the game's own tile and hero, so from Find it goes there first.
                    if (find != null) effect.gameId?.let(::bringForArt)
                    val game = actionGame(current, effect.gameId ?: current.tileActions?.game)
                    game?.let(::artQuery)?.let {
                        keptArtTarget = if (current.tileActions?.kept == true) game.id to current.focus else null
                        artPicker = ArtPickerState(it.key, match = savedArtMatch(it.key))
                    }
                }
                null
            }
            is Effect.SelectTile -> {
                home.startSelection(effect.gameId)
                showBoard(model.focus, keepDialog = true)
                null
            }
            is Effect.Bulk -> {
                val focus = model.focus.cellIndex
                home.bulk(effect.action, effect.ids, effect.collectionId, focus)
                commitHome()
                syncHome()
                showBoard(model.focus, keepDialog = true)
                null
            }
            Effect.MoveTile -> {
                if (current.tileActions?.move == true) pickUpHome(model.focus.cellIndex)
                null
            }
            Effect.Tidy -> changeHome { home.tidyBoard(it) }
            is Effect.SortFolder -> {
                sortFolder(effect)
                null
            }
            is Effect.NewCollection -> {
                // Naming takes the keyboard Find has, so Find gives way to it, on Home.
                if (find != null) {
                    find = null
                    if (!showingHome()) home.showRoot().let { showBoard(GridFocus(cellIndex = it, lastColumn = GridStrip.column(it)), keepDialog = true) }
                }
                changeHome { home.newCollection(it, effect.gameId) }
            }
            is Effect.ShowOnHome -> {
                showOnHome(effect.id)
                null
            }
            Effect.RenameFolder -> changeHome { home.startRename(it); it }
            Effect.ChooseFolderIcon -> changeHome { home.pickIcon(it); it }
            // Remove takes a tile off the spot it is in on Home. Find and All have no spots, so none there.
            Effect.RemoveTile -> if (showingHome() && find == null) changeHome { home.remove(it) } else null
            is Effect.ShowHidden -> {
                effect.id?.let { id ->
                    val wasFolder = id in home.board.retired
                    home.show(id)
                    commitHome()
                    if (!wasFolder) syncHome() else { commitHome(); find?.refresh(findEntries()) }
                    publish(model.copy(shelfEpoch = model.shelfEpoch + 1))
                }
                null
            }
            Effect.Undo -> {
                activeUndo()?.let { saved ->
                    undo = null
                    home.restore(saved.adopt(sameAliases).restore(home.board))
                    syncHome()
                    model = model.copy(undoVerb = null, rewind = model.rewind + 1, panel = null)
                    if (undoGrid == HomeGrid.StandIns) showBoard(undoFocus, keepDialog = false)
                    else publish(model.copy(homeGrid = undoGrid, focus = undoFocus))
                    cue(model.themeIndex, "back")
                }
                null
            }
            is Effect.DeleteShortcut -> {
                effect.gameId?.let { id ->
                    val removed = homeShelf(home.faceOf(id))
                    home.deleteShortcut(id)
                    commitHome()
                    removed.remoteKey?.let { key ->
                        when (removed.libraryId) {
                            MoonlightLibrary.ID -> onMoonlightRemoved(key)
                            DroidDeckLibrary.ID -> onDroidDeckRemoved(key)
                        }
                    }
                    showBoard(model.focus, keepDialog = false)
                    cue(model.themeIndex, "notify")
                }
                null
            }
            Effect.OpenAll -> {
                openHomeAll()
                Log.i("Foldcade", "home-ui all-games")
                null
            }
            Effect.ToggleAddNew -> {
                applyHomeAddNew(next.addNewToHome)
                null
            }
            is Effect.RecentOnHome -> changeHome { home.setRecentOnHome(effect.on, it) }
            Effect.GoHome -> {
                goHome()
                null
            }
            Effect.EditField -> {
                typingRequest++
                null
            }
            is Effect.OpenPlayDay -> {
                openPlayDay(effect.weeksBack, effect.day)
                null
            }
            else -> effect
        }
        // Once the menu has closed and acted, the grid's focused slot leads again.
        if (model.panel == null && home.releaseMenu()) publish(model)
        return handled
    }

    fun homeKeys(): app.foldcade.language.HomeKeys {
        if (artPicker != null) return app.foldcade.language.HomeKeys.ChoosingArt
        if (model.panel != null && home.selection != null) return HomeKeys.Selecting
        if (model.dialog != null || model.panel != null || model.playActivity != null ||
            model.saveConflict != null || model.savePicker != null || model.connectOpen
        ) {
            return app.foldcade.language.HomeKeys.Idle
        }
        // On its results Find takes the grid's keys: A, X and Y mean what they do on a tile. Its
        // own state still guards a held key, since A on a folder closes Find under it.
        find?.takeIf { findActive() }?.let { return if (it.onResults) HomeKeys.FindResults else HomeKeys.Finding }
        if (model.libraryGrid || model.homeGrid != HomeGrid.StandIns) {
            return app.foldcade.language.HomeKeys.Idle
        }
        return home.keys()
    }

    fun homeSelection(): HomeSelection? = if (showingHome()) home.selection else null

    fun homeFace(index: Int): HomeFace? = home.face(index)

    /**
     * True while a grid opened from a command center must stay: a dialog or menu is up, a
     * name is being typed, a collection's icon is being chosen, or Find is open. Its idle timer would
     * otherwise drop the grid mid-word or mid-choice and leave the keyboard or editor open
     * with nothing to act on.
     */
    fun holdsCommandCenter(): Boolean =
        model.savePicker != null || model.dialog != null || model.panel != null || model.connectOpen || typing() != null || home.iconPick != null || home.selection != null || find != null

    /**
     * The collection or folder being named, as the top screen shows it over the name field.
     * Everything the keyboard shows comes from what it names, never from the grid's focus:
     * naming can begin with the focus on a game, or on a slot Add to just emptied.
     */
    fun typingHero(): HeroSubject? {
        if (!showingHome()) return null
        val id = home.renamingId ?: return null
        return home.board.folders[id]?.let(::folderHero)
    }

    /**
     * The collection open on Home while it holds no game, so its grid shows how to fill it
     * instead of tiles. Null anywhere else, and while Find is open over it.
     */
    fun emptyCollection(): HomeFolder? = if (showingHome() && find == null) home.emptyCollection() else null

    /** An empty collection keeps its name and look on the top screen, where an empty slot shows nothing. */
    fun emptyCollectionHero(): HeroSubject? = emptyCollection()?.let(::folderHero)

    private fun folderHero(folder: HomeFolder): HeroSubject {
        val mark = collectionLook(folder)?.mark ?: folder.mark
        return HeroSubject.Folder(
            HeroFolder(key = folder.id, name = folder.name, count = folderCount(home.board, folder.id), mark = mark, apps = folder.holdsApps),
        )
    }

    /** A tap on an empty collection's Add games does what A does there, in one tap: it is the only thing to press. */
    fun touchAddGames(screen: HostScreen): Effect? {
        if (!homeActive() || artPicker != null || emptyCollection() == null) return null
        // The cursor may be on a pill. The tap names Add games, so it takes the cursor back first.
        if (model.focus.chrome != null) publish(model.copy(focus = model.focus.copy(chrome = null)))
        return onMeaning(Meaning.Activate, screen)
    }

    /** The open controller keyboard on Home. Null when none is open. */
    fun typing(): TypingSession? = if (showingHome()) home.typing else null

    /** The text being typed for a folder's name. Null when no keyboard is open. */
    fun renameDraft(): String? = typing()?.text

    /** A tap on the open keyboard: the cursor goes to that key or chip and presses it. */
    fun tapTyping(at: KeyFocus) {
        if (!homeActive()) return
        val step = home.tapTyping(at) ?: return
        commitHome()
        showBoard(step.focus ?: model.focus, keepDialog = true)
        step.cue?.slot?.let { cue(model.themeIndex, it) }
    }

    /** Characters a hardware keyboard typed: onto Find's query, or the name on the open keyboard. */
    fun typeText(text: String): Effect? {
        find?.takeIf { findActive() }?.let { return findOutcome(it.typeText(text)) }
        if (!homeActive()) return null
        val step = home.typeText(text) ?: return null
        commitHome()
        showBoard(step.focus ?: model.focus, keepDialog = true)
        step.cue?.slot?.let { cue(model.themeIndex, it) }
        return null
    }

    /**
     * Select opens Find over any grid of Home, All or a shelf, unless something else has
     * the keys: a menu, a page, a lifted tile, the keyboard, or a picker.
     */
    private fun canOpenFind(): Boolean =
        find == null && artPicker == null && !model.libraryGrid && model.dialog == null && model.panel == null &&
            model.settings == null && model.playActivity == null && model.saveConflict == null && model.savePicker == null && !model.connectOpen &&
            (!showingHome() || home.keys().let { it == HomeKeys.Idle || it == HomeKeys.AllLibrary })

    /** Opens Find on everything Home knows, the most recently played first. */
    fun openFind() {
        if (!canOpenFind()) return
        find = FindSession(findEntries())
        Log.i("Foldcade", "home-ui find")
        cue(model.themeIndex, "open")
        publish(model)
    }

    /** Closes Find. The grid under it still has its focus. */
    fun closeFind() {
        if (find == null) return
        find = null
        publish(model)
    }

    /** True while Find has the keys: open, with no menu, page or dialog over it. */
    private fun findActive(): Boolean =
        find != null && artPicker == null && model.dialog == null && model.panel == null && model.settings == null &&
            model.playActivity == null && model.saveConflict == null && model.savePicker == null && !model.connectOpen

    /** Everything Find lists: the catalog and the groups on Home, then what only Hidden lists. */
    private fun findEntries(): List<FindEntry> {
        val hidden = hiddenRows()
        val hiddenIds = hidden.map { it.id }.toSet()
        val entries = home.findEntries().map { it.copy(hidden = it.id in hiddenIds) }
        val listed = entries.map { it.id }.toSet()
        return entries + hidden.filter { it.id !in listed }.map {
            FindEntry(it.id, it.title, if (it.id in home.board.retired) FindKind.Group
                else if (it.kind.isApp) FindKind.App else FindKind.Game,
                hidden = true, mark = it.mark)
        }
    }

    /** Does what a key in Find asked. A launch is the activity's to run, and Find closes once it has. */
    private fun findOutcome(outcome: FindOutcome): Effect? {
        when (outcome) {
            is FindOutcome.Stay -> {
                outcome.cue.slot?.let { cue(model.themeIndex, it) }
                publish(model)
            }
            FindOutcome.Close -> {
                closeFind()
                cue(model.themeIndex, "close")
            }
            is FindOutcome.Launch -> {
                cue(model.themeIndex, "launch")
                return Effect.Launch(model.focus.cellIndex, outcome.onBottom)
            }
            is FindOutcome.OpenGroup -> {
                find = null
                val at = home.openGroup(outcome.id)
                commitHome()
                showBoard(at?.let { GridFocus(cellIndex = it, lastColumn = GridStrip.column(it)) } ?: model.focus, keepDialog = true)
                cue(model.themeIndex, "activate")
            }
            // The tile menu, for the focused result, in the left island as Y opens it on a grid.
            // The menu holds its result from here, and opens in the same step: a model published
            // without it in between would read as the menu closing.
            FindOutcome.Options -> {
                val session = find ?: return null
                session.holdMenu()
                val menu = model.copy(panel = SidePanel(Side.Left, HostScreen.Top, PanelLevel.Actions, 0, model.focus))
                if (actionRows(withShelf(menu).tileActions).isNotEmpty()) publish(menu) else session.releaseMenu()
            }
        }
        return null
    }

    /** The line under a Find result's name: its platform or count, where it lives, and whether it is hidden. */
    fun findFacts(entry: FindEntry): List<String> =
        app.foldcade.language.findFacts(home.board, entry) + listOfNotNull(entry.id.takeIf { entry.kind != FindKind.Group }?.let(::downloadLine))

    /** A Find result as a game, for its tile's art and icon. */
    fun findGame(id: String): ShelfGame = homeShelf(home.faceOf(id))

    /**
     * A launch from Find has started. Find closes, so the grid is there to come back to. A game
     * that downloads first lands the ring on its place on Home, as Show on Home does but quietly,
     * so its tile and the top screen show the download.
     */
    fun closeFindAfterLaunch() {
        val id = find?.subject?.id
        if (isDownloading(id) && showOnHome(id, burst = false)) return
        closeFind()
    }

    /** A tap on Find's keyboard. */
    fun tapFind(at: KeyFocus): Effect? = find?.takeIf { findActive() }?.let { findOutcome(it.tap(at)) }

    /** A tap on one of Find's results: the first focuses it, a second opens it. */
    fun tapFindResult(at: Int): Effect? = find?.takeIf { findActive() }?.let { findOutcome(it.tapResult(at)) }

    /** Find's keys for the hint row. Null while Find is closed or covered. */
    fun findHints(): List<HintKey>? {
        val session = find?.takeIf { findActive() } ?: return null
        return if (session.onResults) {
            findResultHints(model.faceMap, screens = focusedLaunchesOnOneScreen(), options = hasOptions())
        } else {
            findKeyboardHints(model.faceMap, typed = session.query.isNotEmpty())
        }
    }

    /**
     * Show on Home: leaves Find, All, or whatever is open for where [id] lives on Home (see
     * [app.foldcade.language.homeLanding]), opening its folder or collection when it is in one,
     * and lands the ring there with a burst.
     */
    fun showOnHome(id: String?): Boolean = showOnHome(id, burst = true)

    /** [showOnHome], with the ring's burst and sound only when [burst]. False when [id] is nowhere on Home. */
    private fun showOnHome(id: String?, burst: Boolean): Boolean {
        if (id == null) return false
        artPicker = null
        find = null
        val index = home.reveal(id)
        if (index == null) {
            publish(model)
            return false
        }
        commitHome()
        showBoard(GridFocus(cellIndex = index, lastColumn = GridStrip.column(index)), keepDialog = true)
        if (burst) {
            revealPulse++
            cue(model.themeIndex, "notify")
        }
        Log.i("Foldcade", "home-ui shown ${home.face(index)?.title.orEmpty()}")
        return true
    }

    /**
     * Art chosen from Find's menu: Find closes onto the game's place on Home, as Show on Home
     * lands there, or onto the game in All when it is nowhere on Home. The picker then opens on it.
     */
    private fun bringForArt(id: String) {
        if (showOnHome(id, burst = false)) return
        openHomeAll()
        val at = home.allIndexOf(id).takeIf { it >= 0 } ?: return
        showBoard(GridFocus(cellIndex = at, lastColumn = GridStrip.column(at)), keepDialog = true)
    }

    /**
     * [change] to the game a menu acts on, from Home, All or Find. On the board the focus keeps
     * its slot, or the last tile when the game left the end of the grid.
     */
    private fun changeTile(change: () -> Unit): Effect? =
        if (showingHome() && find == null) changeHome { change(); it.coerceAtMost((home.visibleCount() - 1).coerceAtLeast(0)) }
        else changeFound { change() }

    /** Runs one Y menu action Find opened on its focused result. Find stays, over the same grid. */
    private fun changeFound(change: (Int) -> Unit): Effect? {
        change(model.focus.cellIndex)
        commitHome()
        syncHome()
        publish(model.copy(shelfEpoch = model.shelfEpoch + 1))
        return null
    }

    /** The open collection icon picker and that collection's name, for the top screen. Null when closed. */
    fun folderIconPick(): Pair<IconPick, String>? {
        if (!showingHome()) return null
        val pick = home.iconPick ?: return null
        return pick to home.board.folders[pick.folderId]?.name.orEmpty()
    }

    /** When the home tile [id] last took a new look, or 0. Its tile settles into it. */
    fun settledAt(id: String?): Long = home.settled?.takeIf { it.first == id }?.second ?: 0L

    /**
     * True when the focused tile is a game that A opens on the top screen and X
     * on the bottom: one that takes one screen, while both screens are Foldcade's.
     */
    fun focusedLaunchesOnOneScreen(): Boolean {
        if (!store.session.bothScreensFree()) return false
        val game = focusedPlayable() ?: return false
        return !game.occupiesBothDisplays && !isPaused(focusedTileId(game))
    }

    /** What A says for the focused game when it is not a choice of screens. */
    fun focusedPlayKey(): PlayKey {
        val game = focusedPlayable() ?: return PlayKey.None
        return when {
            isPaused(focusedTileId(game)) -> PlayKey.Resume
            game.occupiesBothDisplays -> PlayKey.Play
            else -> PlayKey.None
        }
    }

    /** The focused tile when it is a game A would start. */
    private fun focusedPlayable(): ShelfGame? {
        if (model.focus.chrome != null) return null
        if (model.libraryGrid && model.gridKind != GridKind.Games) return null
        val found = find?.subject
        if (found != null && found.kind == FindKind.Group) return null
        val face = if (find == null && (showingHome() || home.board.allOpen)) home.face(model.focus.cellIndex) else null
        if (face != null && (face.folder || face.allTile || face.empty)) return null
        return focusedGame()?.takeUnless { it.emptyShelfHint }
    }

    /** The tile a focused game plays under. A library grid names a copy, which may be folded into another tile. */
    private fun focusedTileId(game: ShelfGame): String = tileOf(game.id)

    /** True when Y would open a menu for the focused tile. A grid with no tiles opens one only for Undo. */
    fun hasOptions(): Boolean =
        (model.focus.chrome == null || find != null) && actionRows(model.tileActions).isNotEmpty() &&
            (find != null || model.count > 0 || model.tileActions?.undo == true)

    fun homeEditing(): Boolean =
        showingHome() && home.board.editing && model.dialog == null && model.panel == null

    fun homeAllOpen(): Boolean =
        showingHome() && home.board.allOpen && model.dialog == null && model.panel == null

    fun homeLiftedIndex(): Int? = home.board.hold?.index

    /** The tile in the hand on Home, for the grid to draw lifted. Null while none is. */
    fun homeCarry(): HomeCarry? = if (homeEditing()) home.carry() else null

    /**
     * The ids in the Home board's slots on screen, a folder's when one is open. Empty in All
     * or off Home. The grid reads it to glide a tile that changed slots.
     */
    fun homeSlots(): List<String?> =
        if (showingHome() && !home.board.allOpen) app.foldcade.language.visibleSlots(home.board) else emptyList()

    /** The face of Home game or group [id] wherever it is, for a tile drawn in the hand. */
    fun homeFaceOf(id: String): HomeFace = home.faceOf(id)

    /** The tile [homeFaceOf] draws, with what its art is found by. */
    fun homeTileOf(id: String): ShelfGame = homeShelf(home.faceOf(id))

    /** What a drop does now on Home, which A's hint names. */
    fun homeHoldDrop(): HoldDrop = home.holdDrop() ?: HoldDrop.Place

    fun homeAnimToken(): String = home.animToken()

    /** The tile [id]'s game is only on a server: no copy of it is on this device. */
    fun onServer(id: String): Boolean =
        home.copies(id).let { copies -> copies.isNotEmpty() && copies.all { it.availability == Availability.RemoteOnly } }

    /** A copy of the tile [id]'s game is downloading. The tile pulses its cloud. */
    fun isDownloading(id: String?): Boolean = id != null && downloading.isNotEmpty() && tileIds(id).any { it in downloading }

    /**
     * Steps a folder's Sort from its menu, which stays open on that row. The grid reorders
     * behind it, and the focus, the menu's own included, follows the game it was on.
     */
    private fun sortFolder(effect: Effect.SortFolder) {
        if (!showingHome() || find != null) return
        // Saved, as any edit is, then read again at once, so Most played orders by play time
        // the catalog had not read yet.
        val at = home.stepSort(effect.folderId, effect.direction, model.focus.cellIndex) {
            commitHome()
            syncHome()
        }
        val grid = GridFocus(cellIndex = at, lastColumn = GridStrip.column(at))
        publish(model.copy(focus = grid, panel = model.panel?.copy(grid = grid), shelfEpoch = model.shelfEpoch + 1))
        // Move and Tidy come and go with Custom, so the rows were read again. Sort keeps the highlight.
        val open = model.panel ?: return
        val row = panelRows(open, model).indexOf(Row.SortFolder)
        if (row >= 0 && row != open.index) model = model.copy(panel = open.copy(index = row))
    }

    /** Runs one Y menu action on the focused home tile. [change] returns the index to focus after. */
    private fun changeHome(change: (Int) -> Int): Effect? {
        if (model.libraryGrid) return null
        val onHome = showingHome()
        val index = change(model.focus.cellIndex)
        commitHome()
        // A folder Foldcade made goes the moment its last game leaves it, not at the next scan.
        syncHome()
        commitHome()
        if (home.renamingId != null || onHome) showBoard(GridFocus(cellIndex = index, lastColumn = GridStrip.column(index)), keepDialog = true)
        else publish(model.copy(shelfEpoch = model.shelfEpoch + 1))
        return null
    }

    /** L1's Home: the board's top level, from a folder, All, or a shelf. The last root tile keeps the focus. */
    fun goHome() {
        artPicker = null
        find = null
        val rootFocus = model.focus.takeIf {
            showingHome() && home.board.openFolderId == null && !home.board.allOpen
        }
        val index = home.showRoot()
        syncHome()
        showBoard(rootFocus ?: GridFocus(cellIndex = index, lastColumn = GridStrip.column(index)), keepDialog = true)
        Log.i("Foldcade", "library-ui home")
    }

    /**
     * A game's name for the Play activity page, wherever it lives. Null for one
     * Foldcade no longer knows. History from before the home id kept a library
     * game under its bare remote key, so a key one library game still has names it.
     */
    fun playTitle(gameId: String): String? =
        home.catalog().firstOrNull { it.id == gameId }?.title
            ?: home.game(gameId)?.label
            ?: Shelf.games.firstOrNull { it.id == gameId }?.title
            ?: installed.firstOrNull { androidHomeId(it.packageName) == gameId }?.label
            ?: home.catalog().singleOrNull { homeGameKey(it.id) == gameId }?.title

    /**
     * L1's Games and Apps: the board's Android folder [folderId], opened from wherever the grid
     * is. Back from it lands on its tile, as from any folder opened from its tile.
     */
    fun openHomeFolder(folderId: String) {
        artPicker = null
        find = null
        val index = home.openShelf(folderId)
        commitHome()
        showBoard(GridFocus(cellIndex = index, lastColumn = GridStrip.column(index)), keepDialog = true)
    }

    fun openHomeAll() {
        artPicker = null
        find = null
        if (showingHome()) home.rememberRootFocus(model.focus.cellIndex)
        home.showAll()
        commitHome()
        showBoard(GridFocus(), keepDialog = true)
    }

    fun applyHomeAddNew(enabled: Boolean) {
        home.applyAddNew(enabled)
        commitHome()
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
        commitHome()
        showBoard(GridFocus(cellIndex = index, lastColumn = GridStrip.column(index)), keepDialog = true)
    }

    fun dragHome(meaning: Meaning) {
        if (home.board.hold == null) return
        home.drag(meaning)
        val index = home.board.hold?.index ?: return
        commitHome()
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
        sameSources.keys.removeAll { it.startsWith(prefix) }
        for (entry in entries) {
            val game = entry.game ?: continue
            val id = homeGameId(libraryId, game.remoteKey)
            val kind = when (libraryId) {
                "gamenative" -> HomeKind.GameNative
                "moonlight" -> HomeKind.Moonlight
                DroidDeckLibrary.ID -> HomeKind.DroidDeck
                else -> HomeKind.Rom
            }
            val title = entry.title.ifBlank { game.label }
            items += HomeItem(
                id = id,
                title = title,
                kind = kind,
                platformId = game.platformId,
                mark = kitMark(game.platformId),
                lastPlayedMillis = lastPlayedMillis(id) ?: 0L,
            )
            if (kind == HomeKind.Rom) {
                val fileName = entry.fileName
                sameSources[id] = SameSource(
                    key = sameKey(game.platformId, fileName),
                    rank = libraryRank(libraryId),
                    // A title that is only the file name, as a server without a match gives, does not name the tile.
                    named = fileName == null || (title != fileName && title != fileName.substringBeforeLast('.')),
                )
            }
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
        sameSources.keys.removeAll { it.startsWith(prefix) }
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
    /**
     * The game a menu's Delete download named, when its download can be deleted: [gameId], or
     * the focused tile's game when null. The menu may have kept a game that left the slot on screen.
     */
    fun downloadFor(gameId: String?): Game? {
        if (gameId == null) return focusedDownload()
        if ((!showingHome() && find == null) || busyOnDevice(gameId)) return null
        return downloadOf(gameId)
    }

    fun focusedDownload(): Game? {
        if (!showingHome()) return null
        val id = focusedHomeFace()?.id ?: return null
        if (busyOnDevice(id)) return null
        return downloadOf(id)
    }

    /** The downloaded copy behind the tile [id]. Deleting it leaves a copy in a folder on the tile. */
    private fun downloadOf(id: String): Game? =
        home.copies(id).firstOrNull { it.availability == Availability.Cached }

    private fun busyOnDevice(id: String): Boolean = isRunning(id) || tileIds(id).any { one ->
        one in downloading || one in removing || libraryOf(one) in relisting ||
            activity[one].let { it is TileActivity.Downloading || it == TileActivity.Checking }
    }

    /** True while the game on [id]'s tile is still open in its player, in front or behind Home. */
    fun isRunning(id: String?): Boolean =
        id != null && running.isNotEmpty() && (tileOf(id) in running || tileIds(id).any { it in running })

    /** Still open in its player and on neither screen now: it waits behind Home, where A brings it back. */
    fun isPaused(id: String?): Boolean {
        if (id == null || !resumable || !isRunning(id)) return false
        // On a screen under any id the tile has had, as a game started before a merge is.
        return (tileIds(tileOf(id)) + id).none(store.session::runs)
    }

    /** The tile [id] and the home id of each copy folded into it. */
    private fun tileIds(id: String): List<String> =
        if (id.startsWith(SAME_PREFIX)) listOf(id) + sameAliases.filterValues { it == id }.keys else listOf(id)

    /** What a library is doing with the tile [id]'s game, or with any copy of it. */
    private fun activityOf(id: String): TileActivity? =
        tileIds(id).firstNotNullOfOrNull { activity[it] } ?: TileActivity.Paused.takeIf { isPaused(id) }
            ?: streaming[id]?.let(TileActivity::OnHost)

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
     * [firmware] is Eden's firmware zip: the same fraction and pulse, with its own words.
     */
    fun noteActivity(
        libraryId: String,
        remoteKey: String,
        reported: GameActivity,
        nowMillis: Long,
        firmware: Boolean = false,
    ): Long? {
        val id = homeGameId(libraryId, remoteKey)
        val next = when (reported) {
            is GameActivity.Downloading -> TileActivity.Downloading(
                reported.totalBytes?.takeIf { it > 0 }?.let { total ->
                    (reported.bytesSoFar.toDouble() / total).toFloat().coerceIn(0f, 1f)
                },
                firmware = firmware,
            )
            is GameActivity.GettingFirmware -> TileActivity.Downloading(
                reported.totalBytes?.takeIf { it > 0 }?.let { total ->
                    (reported.bytesSoFar.toDouble() / total).toFloat().coerceIn(0f, 1f)
                },
                firmware = true,
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
        if (activity.isEmpty() && running.isEmpty() && streaming.isEmpty()) return null
        val id = when {
            model.libraryGrid -> entries.getOrNull(source)?.game?.let { homeGameId(it.backendId, it.remoteKey) }
            showingHome() -> home.face(source)?.id
            else -> null
        }
        return id?.let(::activityOf)
    }

    /** The activity of the focused game, for the top screen. */
    fun focusedActivity(): TileActivity? {
        if (activity.isEmpty() && running.isEmpty() && streaming.isEmpty()) return null
        if (!model.libraryGrid) homeCarry()?.let { return activityOf(it.id) }
        val source = if (model.libraryGrid) {
            displayOrder(model).getOrElse(model.focus.cellIndex) { model.focus.cellIndex }
        } else {
            model.focus.cellIndex
        }
        return activityAt(source)
    }

    /** Target-specific activity, including Find and the tile in hand. */
    fun activityFor(id: String): TileActivity? = activityOf(tileOf(id))

    /** True once any library has a game on the board. */
    fun hasLibraryGames(): Boolean = libraryItems.isNotEmpty()

    /** The scanned library is on the home grid. Emulator waits for this line. */
    fun showCuratedHome() {
        artPicker = null
        find = null
        syncHome()
        showBoard(GridFocus(), keepDialog = true)
        Log.i("Foldcade", "library-ui home")
    }

    /** [installedPlayers] are the emulator packages Android has, launcher entry or not. */
    fun setInstalledApps(
        apps: List<LaunchableApp>,
        players: Set<String>,
        installedPlayers: Set<String> = apps.mapTo(HashSet()) { it.packageName },
    ) {
        installed = apps.distinctBy { it.packageName }
        this.installedPlayers = installedPlayers
        installedAppsSettled = true
        playerPackages = players
        syncHome()
        publish(model)
    }

    fun showHomeGrid(grid: HomeGrid) {
        artPicker = null
        find = null
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

    fun releaseMeaning(meaning: Meaning, window: String? = null, canceled: Boolean = false): Effect? {
        heldActions.up(meaning.ordinal, canceled)
        if (canceled) { cancelDeleteHold(window); return null }
        return if (model.deleteHold?.meaning == meaning && (window == null || model.deleteHold?.inputWindow == window)) releaseDeleteHold() else null
    }

    private fun releaseDeleteHold(): Effect? {
        val hold = model.deleteHold ?: return null
        if (hold.release(nowMillis())) return tickRecovery()
        cancelDelete()
        return null
    }

    fun setPromptHeld(key: PromptKey, down: Boolean, window: String? = null): Effect? {
        val effect = if (!down && model.deleteHold?.key == key && (window == null || model.deleteHold?.inputWindow == window)) releaseDeleteHold() else null
        val next = if (down) model.held + key else model.held - key
        if (next != model.held) model = model.copy(held = next)
        return effect
    }

    fun applyFacePrompt(style: ThorStyle?, deviceKey: String, firstSession: Boolean) {
        val confirm = store.buttonPromptConfirm(deviceKey)
        val map = resolveFaceMap(style, confirm)
        val keepCapture = model.capturingConfirm && inputDeviceKey == deviceKey && model.faceMap == map
        inputDeviceKey = deviceKey
        val offer = offerButtonCalibration(
            firstSession = firstSession,
            styleKnown = style != null,
            dismissed = store.buttonPromptDismissed(deviceKey),
            calibrated = confirm != null,
        )
        model = model.copy(
            faceMap = map,
            offerButtonLabels = offer,
            capturingConfirm = keepCapture,
        )
    }

    fun calibrateConfirm(key: PromptKey) {
        if (!model.capturingConfirm || !key.face) return
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
        openConnect(store.rommOrigin().orEmpty())
    }

    fun editRommConnection(): Boolean {
        val origin = store.rommOrigin()
        if (origin == null) {
            closeConnect()
            return false
        }
        openConnect(origin, returnPanel = model.connectReturnPanel)
        return true
    }

    fun openConnect(
        origin: String,
        returnPanel: SidePanel? = null,
    ) {
        invalidateSetupValidation()
        connectVisit += 1
        val screen = model.connectScreen ?: HostScreen.Top
        connectToken = ""
        model = model.copy(
            connectOpen = true,
            connectScreen = screen,
            connectReturnPanel = returnPanel,
            panel = null,
            connectOrigin = origin,
            connectIndex = 0,
            connectWarning = cleartextCredentialWarning(origin),
            connectHint = null,
            connectRetry = false,
            connectKind = ConnectKind.Romm,
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
        model = model.copy(signedIn = backends, rommSaved = store.rommOrigin() != null || backends.any { it.pluginId == RommCredentials.PLUGIN_ID })
    }

    fun setFolders(folders: List<LibraryFolder>) {
        if (model.folders != folders) model = model.withFolders(folders)
    }

    /** Leaves the connect form, as after a server accepts the token. */
    fun closeConnect() {
        invalidateSetupValidation()
        connectToken = ""
        model = model.copy(
            connectOpen = false,
            connectScreen = null,
            connectHint = null,
            connectRetry = false,
            panel = model.connectReturnPanel,
            settings = model.connectReturnSettings,
            connectReturnPanel = null,
            connectReturnSettings = null,
        )
        showQueuedPrompt()
    }

    fun openRommRecovery() {
        openConnect(store.rommOrigin().orEmpty())
    }

    fun askToSignInAgain() {
        if (model.dialog?.kind == DialogKind.ReLogin || model.reLoginPending) return
        if (model.connectOpen && model.connectKind == ConnectKind.Romm) return
        if (model.dialog != null || model.connectOpen) {
            model = model.copy(reLoginPending = true)
            return
        }
        invalidateSetupValidation()
        connectToken = ""
        model = model.copy(
            dialog = signInAgainPrompt(),
            connectOpen = false,
            connectScreen = null,
        )
    }

    /** Shows [dialog] unless another is open. True when it is shown. */
    fun present(dialog: DialogState): Boolean {
        if (model.dialog != null) return false
        model = model.copy(dialog = dialog)
        return true
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
        if (finished.panel == null) home.releaseMenu()
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
        model = model.copy(reLoginPending = false, dialog = signInAgainPrompt())
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

    /** How many columns the panel showing the grid fits, see [app.foldcade.language.GridStrip]. */
    fun setColumnsShown(columns: Int) {
        if (model.columnsShown == columns) return
        model = model.copy(columnsShown = columns)
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
    /** Whether the grid itself takes touches: nothing covers it. */
    private fun gridTouchable(): Boolean =
        find == null && artPicker == null && model.dialog == null && model.panel == null && model.savePicker == null && !model.connectOpen

    fun touchCell(index: Int, screen: HostScreen): Effect? {
        if (!gridTouchable()) return null
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

    /** Whether a finger can scroll the grid's strip now. */
    fun scrollsByTouch(): Boolean = gridTouchable() && !homeEditing() && model.hold == null

    /** A finger let the strip go at [first]. It rests there, and the cursor comes onto the screen if it was left behind. */
    fun touchScroll(first: Int) {
        if (!scrollsByTouch()) return
        val before = model.focus
        val next = scrolledTo(model, first)
        if (next == model) return
        publish(next)
        if (next.focus != before) cue(model.themeIndex, "move")
    }

    fun touchChrome(chrome: Chrome, screen: HostScreen) {
        if (find != null || model.dialog != null || model.panel != null || model.connectOpen) return
        publish(model.copy(focus = model.focus.copy(chrome = chrome)))
        onMeaning(Meaning.Activate, screen)
    }

    /** One tap moves focus to that row. A second tap on it activates. */
    fun touchPanel(index: Int, screen: HostScreen): Effect? {
        if (modalOpen()) return null
        val panel = model.panel ?: return null
        if (panel.screen != screen) return null
        val rows = panelRows(panel, model)
        if (index !in rows.indices) return null
        if (panel.index != index) {
            publish(model.copy(panel = panel.copy(index = index)))
            return null
        }
        return onMeaning(Meaning.Activate, screen, inputWindow = inputWindow(screen))
    }

    /** A tap on a settings category shows its rows. A second tap steps into them. */
    fun touchSettingsCategory(index: Int, screen: HostScreen): Effect? {
        if (modalOpen()) return null
        val page = model.settings ?: return null
        if (page.screen != screen) return null
        if (index !in settingsCategories(model).indices) return null
        if (page.category != index || page.onRows) {
            publish(model.copy(capturingConfirm = false, versionCopied = false, settings = page.copy(category = index, row = 0, onRows = false, inner = null)))
            return null
        }
        return onMeaning(Meaning.MoveRight, screen)
    }

    /** One tap on a settings row focuses it and acts on it, the same as a menu row. */
    fun touchSettingsRow(index: Int, screen: HostScreen): Effect? {
        if (modalOpen()) return null
        val page = model.settings ?: return null
        if (page.screen != screen) return null
        publish(model.copy(
            capturingConfirm = model.capturingConfirm && page.onRows && page.row == index,
            settings = page.copy(row = index, onRows = true),
        ))
        return onMeaning(Meaning.Activate, screen)
    }

    /**
     * The session ids Play activity lists for a day of a week, first started first.
     * The app reads them from its play log; see [app.foldcade.language.playDaySessions].
     */
    var playDaySessions: (weeksBack: Int, day: Int) -> List<String> = { _, _ -> emptyList() }

    /** A on a day of Play activity lists its sessions. A day without any stays as it is. */
    private fun openPlayDay(weeksBack: Int, day: Int) {
        val page = model.playActivity ?: return
        val ids = playDaySessions(weeksBack, day).takeIf { it.isNotEmpty() } ?: return
        publish(model.copy(playActivity = page.copy(sessions = ids, session = 0)))
    }

    /** A tap on the Play activity page's back pill. */
    fun closePlayActivity() {
        if (modalOpen()) return
        val page = model.playActivity ?: return
        publish(model.copy(playActivity = null, focus = page.grid))
    }

    fun closeSettings(): Effect? {
        if (modalOpen()) return null
        val page = model.settings ?: return null
        publish(model.copy(settings = null, focus = page.grid, capturingConfirm = false, versionCopied = false))
        return null
    }

    /** A tap on the page's back pill. A page inside a category returns to its tile, as B does; otherwise the page closes. */
    fun backSettings(screen: HostScreen): Effect? {
        val page = model.settings ?: return null
        if (page.inner == null || modalOpen()) return closeSettings()
        return onMeaning(Meaning.Back, screen)
    }

    /**
     * A dialog or save choice takes every key, so a touch on the page under it does nothing.
     * A tap on a Settings row once went on to activate the dialog's focused button.
     */
    private fun modalOpen(): Boolean = model.dialog != null || model.saveConflict != null || model.savePicker != null

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
        commitHome()
        noteShelfChanged()
    }

    /**
     * A game the user just pinned in Moonlight goes on Home in the Moonlight
     * folder, even with Add new games off and even if it was removed before.
     */
    fun placeMoonlightPin(remoteKey: String) {
        if (!home.place(remoteKey)) return
        commitHome()
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

    /** The exact server copy the menu opened on, including a library-grid press. */
    internal fun savesMenuRoute(gameId: String): GameRoute? {
        val pressed = librarySavesMenuGame?.takeIf { homeGameId(it.backendId, it.remoteKey) == gameId }
        return launchRoute(tileOf(gameId), pressed)
    }

    /** The picker's original game's hero art, even if the covered grid has moved. */
    internal fun savePickerArt(target: app.foldcade.language.SavePickerTarget): RunningApp? {
        val tile = homeTileOf(target.gameId)
        if (artRecord(tile) != null) return runningApp(tile)
        // A library-grid game may not have entered the Home catalog yet.
        val record = savesMenuRoute(target.gameId)?.play ?: return null
        val game = ShelfGame(target.gameId, record.label, "", platformId = record.platformId,
            libraryId = record.backendId, remoteKey = record.remoteKey)
        return runningApp(game).copy(record = record)
    }

    internal fun savePickerHealth(target: app.foldcade.language.SavePickerTarget): SaveHealth {
        val route = savesMenuRoute(target.gameId) ?: return model.savePicker?.health ?: SaveHealth.Fine
        val owner = route.servers.firstOrNull { it.backendId == target.libraryId && it.remoteKey == target.remoteKey } ?: route.saves
        val waiting = route.servers.any { it.remoteKey in unsyncedSaves[it.backendId].orEmpty() }
        val player = target.playerId?.let(plugins::player)?.takeIf { it.packageNames.any(installedPlayers::contains) }
            ?: return if (waiting) SaveHealth.Waiting else SaveHealth.Fine
        val saves = saveSupport.getOrPut(player.id to owner) { readGameSaveSupport(owner, player) }
        val reachability = reachableSaves[homeGameId(owner.backendId, owner.remoteKey) to player.id]
        return gameSaveHealth(owner, player, true, waiting,
            saveSync[player.id], setupSteps[player.id],
            reachable = reachability?.takeIf { it.locations == saves.declarations }?.reachable, saves = saves)
    }

    private fun refreshSavePickerHealth() {
        val page = model.savePicker ?: return
        val owner = savesMenuRoute(page.target.gameId)?.servers?.firstOrNull {
            it.backendId == page.target.libraryId && it.remoteKey == page.target.remoteKey
        }
        val player = page.target.playerId?.let(plugins::player)?.takeIf { it.packageNames.any(installedPlayers::contains) }
        val slots = if (owner != null && player != null)
            saveSupport.getOrPut(player.id to owner) { readGameSaveSupport(owner, player) }.declarations.map { it.slot }
        else emptyList()
        model = model.copy(savePicker = page.copy(health = savePickerHealth(page.target), ownSlots = slots, canPlay = player != null, restartsOpenGame = isRunning(page.target.gameId)))
    }

    fun presentSavePicker(state: app.foldcade.language.SavePickerState?) {
        artPicker = null
        find = null
        publish(model.copy(savePicker = state, saveConflict = null, panel = null, playActivity = null, settings = null, connectOpen = false))
    }

    /** Async results are accepted only by the screen that requested them. */
    fun updateSavePicker(target: app.foldcade.language.SavePickerTarget, update: (app.foldcade.language.SavePickerState) -> app.foldcade.language.SavePickerState) {
        val state = model.savePicker?.takeIf { it.target == target } ?: return
        publish(model.copy(savePicker = update(state)))
    }

    fun touchSavePickerHeader(target: app.foldcade.language.SavePickerTarget): Effect? {
        val state = model.savePicker?.takeIf { it.target == target } ?: return null
        if (state.busy || state.confirmation != null || state.copying != null) return null
        model = model.copy(savePicker = state.copy(index = 0))
        return onMeaning(Meaning.Activate, HostScreen.Bottom)
    }

    fun touchSavePickerSave(target: app.foldcade.language.SavePickerTarget, saveId: String): Effect? {
        val state = model.savePicker?.takeIf { it.target == target } ?: return null
        if (state.busy || state.confirmation != null || state.copying != null) return null
        val index = state.saves.indexOfFirst { it.id == saveId }.takeIf { it >= 0 } ?: return null
        model = model.copy(savePicker = state.copy(index = index + 1))
        return onMeaning(Meaning.Activate, HostScreen.Bottom)
    }

    fun touchSavePickerDestination(target: app.foldcade.language.SavePickerTarget, destination: app.foldcade.language.SaveDestination): Effect? {
        val state = model.savePicker?.takeIf { it.target == target } ?: return null
        if (state.busy || state.confirmation != null || state.copying == null) return null
        val index = state.destinations().indexOf(destination).takeIf { it >= 0 } ?: return null
        model = model.copy(savePicker = state.copy(destinationIndex = index))
        return onMeaning(Meaning.Activate, HostScreen.Bottom)
    }

    fun touchSavePickerConfirm(target: app.foldcade.language.SavePickerTarget, choice: app.foldcade.language.SavePickerChoice, index: Int): Effect? {
        val state = model.savePicker?.takeIf { it.target == target } ?: return null
        if (state.confirmation != choice || state.busy) return null
        model = model.copy(savePicker = state.copy(confirmIndex = index))
        return onMeaning(Meaning.Activate, HostScreen.Bottom)
    }

    fun savePickerFix(target: app.foldcade.language.SavePickerTarget): Effect? {
        if (model.savePicker?.target != target) return null
        val player = target.playerId?.let(plugins::player)?.takeIf { it.packageNames.any(installedPlayers::contains) } ?: return null
        return when {
            player is app.foldcade.api.plugin.HostedSaveFolder && !player.hostedFolderInUse() -> Effect.SetUpHostedSaves(player.id)
            player is SaveFolderHolder && !player.hasSaveFolder() -> Effect.ChoosePlayerSave(player.id)
            saveSync[player.id] is SaveSyncState.NeedsSetup -> Effect.ShowSaveSync(player.id)
            setupSteps[player.id] != null -> Effect.ShowPlayerSetup(player.id)
            player is SaveFolderHolder -> Effect.ChoosePlayerSave(player.id)
            else -> null
        }
    }

    /** Puts a conflict on the bottom screen unless the save chooser owns it. */
    fun presentSaveConflict(prompt: app.foldcade.language.SaveConflictPrompt?) {
        if (model.savePicker != null) return
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

    /** A touch on Decide later, the same as B. Both copies stay and the prompt returns next launch. */
    fun touchSaveConflictLater(): Effect? {
        if (model.saveConflict == null) return null
        return onMeaning(Meaning.Back, HostScreen.Bottom)
    }

    fun focusedGame(): ShelfGame? = when {
        find != null -> foundGame()
        showingHome() -> focusedHomeFace()?.let(::homeShelf)
        else -> tileFromOrder(displaySource(model))
    }

    /**
     * The Home tile the top screen is about: the one in the hand while a tile is held, which
     * may be over a group or the Home target, away from the slot under the cursor, else the
     * focused one. Every hero, record and art lookup reads this, so they agree on one game.
     */
    private fun focusedHomeFace(): HomeFace? =
        homeCarry()?.let { home.faceOf(it.id) } ?: home.face(model.focus.cellIndex)

    /** The game cell [index] shows, as [focusedGame] reads the focused cell. Null off the grid. */
    fun gameAt(index: Int): ShelfGame? = when {
        index !in 0 until model.count -> null
        showingHome() -> home.face(index)?.let(::homeShelf)
        else -> tileFromOrder(displayOrder(model).getOrElse(index) { index })
    }

    /** Find's subject as a game: the focused result, or the best one on the keys. Null for a group. */
    private fun foundGame(): ShelfGame? {
        val entry = find?.subject?.takeIf { it.kind != FindKind.Group } ?: return null
        return homeShelf(home.faceOf(entry.id))
    }

    /**
     * A home tile as a game. A library game takes its screens from its platform's
     * players, as the library grid does, so a DS game reads as both screens.
     */
    private fun homeShelf(face: HomeFace): ShelfGame {
        val game = home.asShelf(face)
        val platformId = game.platformId
        if (game.libraryId == null || platformId == null) return game
        return game.copy(occupiesBothDisplays = platformOccupiesBoth(plugins, platformId, installedPlayers::contains))
    }

    /**
     * The id play history is kept under. A library game has one id, its home id,
     * whichever grid it was launched or focused from.
     */
    fun playId(id: String): String {
        val libraryId = activeLibraryId
        return tileOf(if (model.libraryGrid && libraryId != null) homeGameId(libraryId, id) else id)
    }

    /** The tile a library game's [homeId] is shown as: the one its copies fold into, or its own. */
    fun tileOf(homeId: String): String = sameAliases[homeId] ?: homeId

    /** Every id [tileId]'s play is kept under: the tile's, and each copy's from before it folded. */
    fun historyIds(tileId: String): Set<String> = sameGameIds(tileOf(tileId), sameAliases)

    /** Each folded tile and its copies, first preferred, the ones not listed now after. */
    fun sameGroups(): Map<String, List<String>> = sameAliases.entries.groupBy({ it.value }, { it.key })
        .mapValues { (tile, ids) -> sameCopies[tile].orEmpty().let { listed -> listed + ids.filter { it !in listed }.sorted() } }

    /**
     * How a Home tile starts: which copy plays, and which keeps its saves. [pressed] is the copy
     * a library grid named, which plays. Null when the tile has no library game.
     */
    internal fun launchRoute(tileId: String, pressed: Game? = null): GameRoute? {
        val copies = home.copies(tileId).ifEmpty { listOfNotNull(pressed) }
        if (copies.isEmpty()) return null
        return routeSameGame(tileId, copies, pressed, ::libraryRank)
    }

    /** RomM keeps a folded game's saves and gives its tile a name and art. */
    private fun libraryRank(libraryId: String): Int =
        if (libraryId == RommCredentials.PLUGIN_ID) 0 else Int.MAX_VALUE / 2

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
     * What the art sources need to look [game] up. Non-game apps keep their launcher
     * icons, and folders have no game to look up.
     */
    fun artQuery(game: ShelfGame): ArtQuery? {
        if (game.emptyShelfHint || game.id.isEmpty()) return null
        game.androidPackage?.let { packageName ->
            val isGame = appKind(installed.firstOrNull { it.packageName == packageName } ?: return null) == HomeKind.AndroidGame
            if (!isGame) return null
            return matchedArtQuery(ArtQuery(key = game.id, title = game.title))
        }
        val record = artRecord(game)
        val libraryId = record?.backendId ?: game.libraryId ?: return null
        val remoteKey = record?.remoteKey ?: game.remoteKey
        val platformId = (record?.platformId ?: game.platformId)?.let { id ->
            resolvePlatform(homePlatforms(), id)?.id ?: id
        }
        return matchedArtQuery(ArtQuery(
            key = if (model.libraryGrid && record != null) tileOf(homeGameId(libraryId, record.remoteKey)) else game.id,
            title = game.title,
            platformId = platformId,
            fileName = remoteKey?.takeIf { libraryId == LocalFolderBackend.ID }?.let(LocalFolderBackend::fileNameOf),
            steamAppId = when (libraryId) {
                GameNativeLibrary.ID -> remoteKey?.let(::steamAppId)
                DroidDeckLibrary.ID -> remoteKey?.let(::droidDeckSteamAppId)
                else -> null
            },
        ))
    }

    private fun matchedArtQuery(query: ArtQuery): ArtQuery =
        query.copy(steamGridDbGameId = savedArtMatch(query.key)?.id)

    /** The library record behind [game], on the home grid or the library grid. */
    fun artRecord(game: ShelfGame): Game? =
        home.game(game.id) ?: entries.firstOrNull { it.id == game.id }?.game ?: streamRecord(game)

    /** A Moonlight tile as its library lists it, so Moonlight's own poster can answer for it. */
    private fun streamRecord(game: ShelfGame): Game? {
        if (game.libraryId != MoonlightLibrary.ID) return null
        val key = game.remoteKey ?: return null
        return Game(
            backendId = MoonlightLibrary.ID,
            remoteKey = key,
            platformId = game.platformId ?: MoonlightPlatform.ID,
            availability = Availability.LocalOnly,
            label = game.title,
        )
    }

    /**
     * The library record behind the focused cell, for its metadata and art. Null for an app
     * or a shelf tile, except a Moonlight game's, whose poster comes from its library.
     */
    fun focusedRecord(): Game? = when {
        find != null -> find?.subject?.id?.let(home::game)
        showingHome() -> focusedHomeFace()?.id?.let(home::game)
        else -> focusedEntry()?.game
    } ?: focusedGame()?.let(::streamRecord)

    /**
     * What the idle top screen shows for the focused cell.
     * A platform folder is its name and game count, with no artwork.
     * An empty or loading grid has nothing to show, and nor does an empty slot,
     * so the background shows through instead of a blank name card.
     */
    fun focusedHero(): HeroSubject? {
        find?.let { return foundHero(it) }
        // A tile in the hand stays on top while it walks, over a group too.
        val carried = homeCarry()?.let { home.faceOf(it.id) }?.let(::homeShelf)
        if (carried != null) return heroItem(carried)
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
        return heroItem(game)
    }

    private fun heroItem(game: ShelfGame): HeroSubject = HeroSubject.Item(
        HeroItem(
            key = game.id,
            title = game.title,
            detail = libraryPlatformName(game.platformId) ?: game.shortText,
            mark = game.mark,
            packageName = game.androidPackage,
            emptyShelfHint = game.emptyShelfHint,
            availability = game.availabilityLabel ?: streamLine(game) ?: downloadLine(game.id),
        ),
    )

    /**
     * What playing the tile [id]'s game would download, as [downloadFacts] says it, while no
     * copy is on the device ([onServer]). Null once one is, and while one is downloading: the
     * live activity line says that. The first copy is the one a launch fetches.
     */
    private fun downloadLine(id: String): String? {
        if (id.isEmpty() || !onServer(id) || isDownloading(id)) return null
        val copy = home.copies(id).firstOrNull() ?: return null
        return downloadFacts(plugins.library(copy.backendId)?.displayName, copy.sizeBytes)
    }

    /** Find's subject as the top screen shows it. A group shows as its folder does. */
    private fun foundHero(session: FindSession): HeroSubject? {
        val entry = session.subject ?: return null
        // A group's mark is already its look, as its result tile shows it.
        if (entry.kind == FindKind.Group) {
            val apps = home.board.folders[entry.id]?.holdsApps == true
            return HeroSubject.Folder(HeroFolder(key = entry.id, name = entry.title, count = folderCount(home.board, entry.id), mark = entry.mark, apps = apps))
        }
        val game = foundGame() ?: return null
        return HeroSubject.Item(
            HeroItem(
                key = game.id,
                title = game.title,
                detail = game.shortText,
                mark = game.mark,
                packageName = game.androidPackage,
                availability = game.availabilityLabel ?: streamLine(game) ?: downloadLine(game.id),
            ),
        )
    }

    /** A Moonlight game's computer, and whether it answers. Null for any other game. */
    private fun streamLine(game: ShelfGame): String? {
        if (game.libraryId != MoonlightLibrary.ID) return null
        val host = game.remoteKey?.substringBefore('|')?.let(moonlightHosts::get) ?: return null
        return streamHostLine(host.name, host.online)
    }

    fun tileFromOrder(source: Int): ShelfGame? = when {
        model.libraryGrid -> entries.getOrNull(source)?.asShelf()
        showingHome() -> home.face(source)?.let(::homeShelf)
        model.homeGrid == HomeGrid.StandIns -> Shelf.games.getOrNull(source)
        else -> hiddenTile(source)
    }

    fun refreshPlayerSaves() {
        val settings = playerSaveSettings()
        val folders = settings.map { store.playerSaveFolder(plugins.player(it.playerId)?.saveFolderId() ?: it.playerId) }
        if (settings == healthPlayerSettings && folders == healthPlayerFolders) return
        healthPlayerSettings = settings
        healthPlayerFolders = folders
        model = model.copy(playerSaves = settings)
        refreshSaveHealth()
    }

    /** Null while RetroArch is not installed. */
    fun setRetroArch(setting: RetroArchSetting?) {
        if (model.retroArch == setting) return
        model = model.copy(retroArch = setting)
        refreshSaveHealth()
    }

    /** Null while Eden is not installed. */
    fun setEden(setting: EdenSetting?) {
        if (model.eden == setting) return
        model = model.copy(eden = setting)
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
        artPicker = null
        find = null
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

    private fun artPickerAvailable(): Boolean = store.session.surfaceOn(Panel.Bottom) != null

    private fun withShelf(next: PickerModel): PickerModel {
        val count = countFor(next.homeGrid)
        home.sort = next.sort
        home.lastPlayed = lastPlayedMillis
        val alphabetical = next.homeGrid != HomeGrid.StandIns ||
            (home.board.allOpen && next.sort == app.foldcade.language.LibrarySort.Listed)
        val labels = if (!alphabetical) emptyList() else if (next.homeGrid == HomeGrid.StandIns) {
            home.faces().map { it.title }
        } else hiddenRows().map { it.title }
        val lastOccupied = if (next.homeGrid == HomeGrid.StandIns && !home.board.allOpen) {
            app.foldcade.language.visibleSlots(home.board).indexOfLast { it != null }
        } else count - 1
        val counted = next.copy(count = count, playerSaves = playerSaveSettings(), recentFirst = recentOrder(),
            gridLabels = labels, lastOccupiedIndex = lastOccupied,
            screenJumpLastColumn = if (showingHome(next) && !home.board.allOpen) GridStrip.column(lastOccupied.coerceAtLeast(0)) else null)
        val game = tileOn(counted)
        val onHome = showingHome(counted)
        // On Find's results, Y's menu is the focused result's, wherever the grid under it is. An
        // open menu keeps the result it opened on, however the results move under it.
        if (counted.panel == null) find?.releaseMenu()
        val found = find?.let { it.menuId ?: it.subject?.id?.takeIf { _ -> it.onResults } }?.let { id ->
            home.findActions(id, hidden = hiddenRows().any { it.id == id })
        }
        val tile = found ?: when {
            counted.homeGrid == HomeGrid.Hidden -> game?.let { TileActions(show = true, game = it.id) }
            onHome -> home.actions(counted.focus.cellIndex)
            else -> null
        }
        val targetGame = actionGame(counted, tile?.game)
        // A copy still arriving, or already going, is not one to delete.
        val busy = (tile?.game ?: home.face(counted.focus.cellIndex)?.id)?.let(::busyOnDevice) == true
        val offered = (if (tile?.removeDownload == true && busy) tile.copy(removeDownload = false) else tile)
            // A game still open in its player can start over. Find's menu acts on a result that may not be a tile.
            ?.let { if (found == null && isPaused(it.game)) it.copy(restart = true) else it }
        // A merge can close the open folder, when its last game left, so read where the board is now.
        val root = if (onHome) home.board.openFolderId == null && !home.board.allOpen else counted.atLibraryRoot
        val withArt = if (tile?.bulk == null && home.selection == null && counted.artKeySaved && artPickerAvailable() && targetGame?.let(::artQuery) != null) {
            (offered ?: TileActions()).copy(art = true)
        } else offered
        val withSaves = if (tile?.bulk == null && home.selection == null && targetGame != null &&
            launchRoute(targetGame.id)?.servers?.isNotEmpty() == true) (withArt ?: TileActions()).copy(saves = true, game = tile?.game ?: targetGame.id) else withArt
        return counted.copy(tileActions = (withSaves ?: TileActions()).copy(undo = home.selection == null && activeUndo() != null), undoVerb = activeUndo()?.verb, atLibraryRoot = root)
    }

    /** A menu acts on its named game even after Add to leaves the focused slot empty. */
    private fun actionGame(snapshot: PickerModel, gameId: String? = snapshot.tileActions?.game): ShelfGame? = when {
        snapshot.libraryGrid -> libraryEntry(snapshot)?.asShelf()
        gameId != null -> homeShelf(home.faceOf(gameId))
        else -> tileOn(snapshot)
    }

    private fun tileOn(snapshot: PickerModel): ShelfGame? {
        if (!snapshot.libraryGrid && snapshot.homeGrid == HomeGrid.StandIns) {
            return home.face(snapshot.focus.cellIndex)?.let(::homeShelf)
        }
        val source = displaySource(snapshot)
        return when (snapshot.homeGrid) {
            HomeGrid.StandIns -> Shelf.games.getOrNull(source)
            HomeGrid.Hidden -> hiddenTile(source)
        }
    }

    /** The last read of each player's [SaveSyncState]. Reading a card does not belong on this path. */
    private var saveSync: Map<String, SaveSyncState> = emptyMap()

    /** The last read of each player's one-time setup, or absent when it can start. */
    private var setupSteps: Map<String, String> = emptyMap()

    /** Replaces the cached card state and redraws the emulator rows. */
    fun setSaveSync(states: Map<String, SaveSyncState>, setup: Map<String, String> = setupSteps) {
        if (states == saveSync && setup == setupSteps) return
        val changed = (states.keys + saveSync.keys + setup.keys + setupSteps.keys).filter {
            states[it] != saveSync[it] || setup[it] != setupSteps[it]
        }.toSet()
        reachableSaves.keys.removeAll { it.second in changed }
        saveSync = states
        setupSteps = setup
        saveHealthEpoch += 1
        refreshPlayerSaves()
    }

    /** One row per save folder. Players that share a folder, as RetroArch's systems do, share a row. */
    private fun playerSaveSettings(): List<PlayerSaveSetting> =
        plugins.playerIds().mapNotNull { id ->
            val player = plugins.player(id) ?: return@mapNotNull null
            if (player !is SaveFolderHolder) return@mapNotNull null
            val sync = saveSync[player.id]
            player.saveFolderId() to PlayerSaveSetting(
                playerId = player.id,
                label = player.displayName,
                chosen = player.hasSaveFolder(),
                hostedTitle = (player as? HostedSaveFolder)?.hostedFolderTitle,
                hostedInUse = (player as? HostedSaveFolder)?.hostedFolderInUse() == true,
                saveSyncing = sync is SaveSyncState.Syncing,
                saveSyncSteps = (sync as? SaveSyncState.NeedsSetup)?.steps,
                setupSteps = setupSteps[player.id],
                installed = player.packageNames.any(installedPlayers::contains),
                packages = player.packageNames.filter(installedPlayers::contains),
            )
        }.distinctBy { it.first }.map { it.second }

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

    /**
     * Play history changed. The Recent sort and the Recently played collection follow the new
     * last-played times.
     */
    fun notePlayChanged() {
        // All's Recent sort reorders its rows, which the board does not see.
        if (showingHome() && home.board.allOpen) {
            syncHome()
            model = withShelf(model.copy(shelfEpoch = model.shelfEpoch + 1))
            return
        }
        val before = home.board
        syncHome()
        val next = recentOrder()
        if (home.board == before && next == model.recentFirst) return
        noteShelfChanged()
        if (model.recentFirst != next) model = model.copy(recentFirst = next)
    }

    private fun publish(next: PickerModel) {
        if (showingHome() && next.homeGrid != HomeGrid.StandIns) {
            home.rememberRootFocus(model.panel?.grid?.cellIndex ?: model.focus.cellIndex)
        }
        val savePickerClosed = model.savePicker != null && next.savePicker == null
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
            val game = if (ready.panel != null) librarySavesMenuGame ?: libraryEntry(ready)?.game else libraryEntry(ready)?.game
            val id = game?.let { homeGameId(it.backendId, it.remoteKey) }
            val canChoose = game != null && id != null && launchRoute(tileOf(id), game)?.servers?.isNotEmpty() == true
            ready.copy(tileActions = if (canChoose) TileActions(saves = true, game = id) else null, playerSaves = playerSaveSettings())
        } else {
            withShelf(ready.copy(libraryGrid = false))
        }
        refreshSavePickerHealth()
        if (connectClosed) showQueuedPrompt()
        if (savePickerClosed) onSavePickerClosed()
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
        HomeGrid.Hidden -> hiddenRows().size
    }

    private fun hiddenRows(): List<HomeItem> = home.hiddenItems()

    private fun hiddenTile(index: Int): ShelfGame? = hiddenRows().getOrNull(index)?.let { item ->
        homeShelf(home.faceOf(item.id)).copy(id = item.id, title = item.title, mark = item.mark ?: home.faceOf(item.id).mark)
    }

    private fun showingHome(snapshot: PickerModel = model): Boolean =
        !snapshot.libraryGrid && snapshot.homeGrid == HomeGrid.StandIns

    /** The home grid's top level with nothing open over it, where B has nothing left to close. */
    fun atRest(): Boolean =
        find == null && homeActive() && home.board.openFolderId == null && !home.board.allOpen && home.board.hold == null &&
            home.iconPick == null && home.selection == null && renameDraft() == null

    private fun homeActive(): Boolean =
        showingHome() && model.dialog == null && model.panel == null && model.settings == null &&
            model.playActivity == null && model.saveConflict == null && model.savePicker == null && !model.connectOpen

    /**
     * A or X with the cursor on L1's or R1's pill opens that drawer, as the grid reducer
     * does. Home would act on the tile the cursor left. Typing, a held tile, an icon pick
     * and a selection keep their own keys.
     */
    private fun opensPill(meaning: Meaning): Boolean =
        model.focus.chrome != null && (meaning == Meaning.Activate || meaning == Meaning.ActivateBottom) &&
            home.keys().let { it == HomeKeys.Idle || it == HomeKeys.AllLibrary }

    /**
     * The pills are drawn on the top screen, so an app there leaves the cursor on the grid.
     * A selection keeps L1 and R1 shut, so their pills are out of reach then too.
     */
    private fun syncPills() {
        val shown = store.session.islandsOpen() && home.keys() != HomeKeys.Selecting
        if (model.pills == shown) return
        model = model.copy(pills = shown, focus = if (shown) model.focus else model.focus.copy(chrome = null))
    }

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
                recentOnHome = groupOnHome(home.board, HOME_RECENT),
                shelfEpoch = model.shelfEpoch + 1,
                gridKind = GridKind.Games,
                emptyGrid = EmptyGrid.None,
                atLibraryRoot = home.board.openFolderId == null && !home.board.allOpen,
                dialog = if (keepDialog) model.dialog else null,
            ),
        )
    }

    /**
     * [libraryId] has not listed its games since launch and may still. A RomM server no longer
     * saved never will, so a copy a folded tile remembers from it does not hold the tile.
     */
    private fun awaitingLibrary(libraryId: String): Boolean =
        libraryId !in settledLibraries &&
            (libraryId != RommCredentials.PLUGIN_ID || store.rommOrigin() != null)

    /** The library a [homeGameId] belongs to, or null for a tile that is not a library game. */
    private fun libraryOf(id: String): String? =
        id.removePrefix("lib:").takeIf { it != id }?.substringBefore(':')

    private fun commitHome(catalog: Boolean = false) {
        val saved = home.encoded()
        val changed = saved != committedHome
        if (!catalog && (saved != committedHome || home.board.editing)) undo = null
        committedHome = saved
        store.saveHomeBoard(saved)
        if (!catalog && changed) refreshSaveHealth()
    }

    private fun syncHome() {
        home.rememberShelf(Shelf.games)
        val folded = foldSameGames(libraryItems, sameSources, sameAliases)
        sameCopies = folded.members
        if (folded.aliases != sameAliases) {
            val joined = folded.aliases.filter { (id, tile) -> sameAliases[id] != tile }.values.toSet()
            sameAliases = folded.aliases
            store.saveSameGames(sameAliases)
            onSameGames(sameGroups().filterKeys { it in joined })
        }
        val copies = folded.members.mapValues { (_, ids) -> ids.mapNotNull(libraryGames::get) }
        // Play time is read through every play, so only for the games a folder orders by it.
        val ranked = home.rankedByPlay()
        val timed = { item: HomeItem -> if (item.id in ranked) item.copy(playMillis = playMillis(item.id) ?: 0L) else item }
        home.remerge(
            shelfItems().map(timed),
            appItems().map(timed),
            // Read now, since a game played since its library listed it moves up Recently played.
            folded.items.map { item -> timed(item.copy(lastPlayedMillis = lastPlayedMillis(item.id) ?: 0L)) },
            homePlatforms(),
            // A tile's art and details come from its first copy: a server's, which knows the game.
            libraryGames + copies.mapNotNull { (tile, games) -> games.firstOrNull()?.let { tile to it } },
            unsettled = { id ->
                tileIds(id).any { one -> libraryOf(one)?.let(::awaitingLibrary) == true } ||
                    (!moonlightSettled && isMoonlightRemoteKey(id)) ||
                    (!droidDeckSettled && id.startsWith(Shelf.DROIDDECK_PREFIX)) ||
                    (!installedAppsSettled && id.startsWith("android:")) ||
                    (GameNativeLibrary.ID !in settledLibraries && id.startsWith("gamenative."))
            },
            sameGames = sameAliases,
            copies = copies,
        )
        commitHome(catalog = true)
        find?.refresh(findEntries())
        refreshSaveHealth()
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
        val kind = appKind(app)
        HomeItem(
            id = androidHomeId(app.packageName),
            title = app.label,
            kind = kind,
            mark = bucketMark(kind),
            lastPlayedMillis = lastPlayedMillis(androidHomeId(app.packageName)) ?: 0L,
        )
    }

    /**
     * What an installed app is: an emulator when a player launches games in it, else a game or
     * an app. The Android folder the user moved it to on Home decides that, and still does while
     * it is hidden, then the system's game flag, so All, Find and art follow a move between
     * Android Games and Android Apps.
     */
    private fun appKind(app: LaunchableApp): HomeKind {
        return when {
            app.packageName in playerPackages -> HomeKind.Emulator
            else -> when (androidFolderOf(home.board, androidHomeId(app.packageName))) {
                HOME_ANDROID_GAMES -> HomeKind.AndroidGame
                HOME_ANDROID_APPS -> HomeKind.AndroidApp
                else -> if (app.systemGame) HomeKind.AndroidGame else HomeKind.AndroidApp
            }
        }
    }

    private fun homePlatforms(): List<HomePlatform> {
        val known = listOf(
            HomePlatform("nintendo-3ds", "Nintendo 3DS", setOf("3ds", "n3ds", "new-nintendo-3ds")),
            HomePlatform("nintendo-ds", "Nintendo DS", setOf("nds")),
            HomePlatform("game-boy", "Game Boy", setOf("gb")),
            HomePlatform("game-boy-color", "Game Boy Color", setOf("gbc")),
            HomePlatform("game-boy-advance", "Game Boy Advance", setOf("gba")),
            HomePlatform("game-gear", "Sega Game Gear", setOf("gg")),
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
            rommSaved = store.rommOrigin() != null,
            count = home.visibleCount(),
            addNewToHome = home.board.addNewToHome,
            recentOnHome = groupOnHome(home.board, HOME_RECENT),
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
            appVersion = appVersion,
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

    /**
     * The sound of a key that did something. Each cue past the first four falls back to the one
     * that played here before it existed (see [ThemeContract.soundCues]), so a pack that only
     * ships `activate` sounds as it always did.
     */
    private fun cueMeaning(meaning: Meaning, before: PickerModel, after: PickerModel, effect: Effect? = null) {
        when (meaning) {
            Meaning.Activate -> cue(after.themeIndex, activateCue(before, after, effect))
            Meaning.Back -> cue(after.themeIndex, if (closed(before, after)) "close" else "back")
            // A chord runs a row, so it sounds as A on that row would.
            Meaning.Menu, Meaning.View, Meaning.Options, Meaning.ActivateBottom -> {
                val panel = before.panel
                if (panel != null && chordIndex(panelRows(panel, before), meaning) != null) {
                    cue(after.themeIndex, activateCue(before, after, effect))
                }
            }
            Meaning.MoveUp, Meaning.MoveDown, Meaning.MoveLeft, Meaning.MoveRight -> {
                val moved = before.focus != after.focus || before.panel?.index != after.panel?.index ||
                    before.playActivity != after.playActivity
                val slid = valueAdjusted(before, after)
                // A move that changed nothing at all ran into an edge. Silent unless the pack ships `edge`.
                if (slid) tickSlider(after.themeIndex) else if (moved) cue(after.themeIndex, "move")
                else if (before == after) cue(after.themeIndex, "edge")
            }
            else -> Unit
        }
        if (before.dialog?.kind != DialogKind.Ok && after.dialog?.kind == DialogKind.Ok) {
            cue(after.themeIndex, "notify")
        }
        if (after.notices.size > before.notices.size) cue(after.themeIndex, "notify")
    }

    private fun activateCue(before: PickerModel, after: PickerModel, effect: Effect?): String = when {
        effect is Effect.Launch -> "launch"
        before.music.enabled != after.music.enabled || before.addNewToHome != after.addNewToHome ||
            before.recentOnHome != after.recentOnHome || before.artwork != after.artwork -> "toggle"
        opened(before, after) -> "open"
        else -> "activate"
    }

    /** A menu, a dialog, a page or the connection form came up. */
    private fun opened(before: PickerModel, after: PickerModel): Boolean =
        (before.panel == null && after.panel != null) || (before.dialog == null && after.dialog != null) ||
            (before.settings == null && after.settings != null) || (before.playActivity == null && after.playActivity != null) ||
            (!before.connectOpen && after.connectOpen)

    /** One of them went away. */
    private fun closed(before: PickerModel, after: PickerModel): Boolean = opened(after, before)

    // initial() merges the board before the later state fields and the app's shell binding exist.
    init { saveHealthReady = true }
}

/** How a finished sync reads on its tile. Unchanged with a note is a sync that could not run. */
internal fun syncMark(result: SyncResult): SyncMark = when (result.outcome) {
    SyncOutcome.KeptBoth -> SyncMark.KeptBoth
    SyncOutcome.Queued -> SyncMark.Later
    SyncOutcome.Unchanged -> if (result.note == null) SyncMark.Synced else SyncMark.NotSynced
    else -> SyncMark.Synced
}
