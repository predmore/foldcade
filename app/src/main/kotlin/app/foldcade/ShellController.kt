package app.foldcade

import android.util.Log
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.Platform
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.host.PluginHost
import app.foldcade.language.settingsCategories
import app.foldcade.language.AndroidShelf
import app.foldcade.language.AppActions
import app.foldcade.language.AppShelfState
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.ConnectField
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
import app.foldcade.language.kitMark
import app.foldcade.host.play.recentlyPlayedIndices
import app.foldcade.language.HomeMusicSetting
import app.foldcade.language.MusicTrack
import app.foldcade.language.offeredMusicTracks
import app.foldcade.language.selectedMusicTrack
import app.foldcade.language.HostScreen
import app.foldcade.language.Meaning
import app.foldcade.language.MotionSpeed
import app.foldcade.language.Metrics
import app.foldcade.language.MoonlightDiscoveredApp
import app.foldcade.language.MoonlightSource
import app.foldcade.language.MoonlightStoredApp
import app.foldcade.language.PanelLevel
import app.foldcade.language.moonlightImportSheet
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
    private val cue: (themeIndex: Int, slot: String) -> Boolean = { _, _ -> false },
    private val bareTick: () -> Unit = {},
    private val homeTracks: List<MusicTrack> = emptyList(),
    private val themeTracks: List<MusicTrack?> = emptyList(),
    private val lastPlayedMillis: (String) -> Long? = { null },
    private val onMoonlightCatalog: () -> Unit = {},
    private val onReviewMoonlight: (HostScreen) -> Unit = {},
) {
    private var shelfState: AppShelfState = store.appShelfState()
    private var installed: List<LaunchableApp> = emptyList()
    private var playerPackages: Set<String> = emptySet()
    private val home = HomeSession(store.homeBoardRaw())
    private var libraryItems: List<HomeItem> = emptyList()

    /** Libraries that have reported their games since launch. Until then their home tiles are kept. */
    private val settledLibraries = mutableSetOf<String>()
    private val libraryGames = HashMap<String, Game>()
    private var extraPlatforms: List<HomePlatform> = emptyList()

    /** Platform names a library listed, by platform id. Used when nothing else names it. */
    private val listedPlatformNames = HashMap<String, String>()

    var model by mutableStateOf(initial())
        private set

    private var inputDeviceKey: String = "builtin"

    var connectToken by mutableStateOf("")
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

    fun onMeaning(meaning: Meaning, screen: HostScreen): Effect? {
            if (homeActive()) {
            val before = model
            val wasInFolder = home.board.openFolderId != null
            val wasAll = home.board.allOpen
            val wasTab = home.board.allTab
            val wasHeld = home.board.hold != null
            val step = home.handle(meaning, model.focus.cellIndex)
            if (step != null && step.handled) {
                store.saveHomeBoard(home.encoded())
                showBoard(step.focus ?: model.focus, keepDialog = true)
                if (!wasInFolder && home.openedFolderHasGame()) Log.i("Foldcade", "library-ui games")
                if (!wasHeld && home.board.hold != null) Log.i("Foldcade", "home-ui lifted")
                if (home.board.allOpen && (!wasAll || wasTab != home.board.allTab)) {
                    val tab = if (home.board.allTab == app.foldcade.language.AllTab.Games) "games" else "apps"
                    Log.i("Foldcade", "home-ui all-$tab")
                }
                cueMeaning(meaning, before, model)
                return step.effect
            }
        }
        val before = model
        val current = if (model.libraryGrid) model else withShelf(model)
        val (next, effect) = reduce(current, meaning, screen)
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
            is Effect.ConfirmMoonlightImport -> {
                val sourceChanged = next.moonlightSource != model.moonlightSource
                store.setMoonlightImport(
                    effect.checked.map { app ->
                        MoonlightStoredApp(hostUuid = app.hostUuid, appId = app.appId, label = app.label)
                    },
                )
                store.setMoonlightImportSettled()
                publish(next)
                if (!sourceChanged) onMoonlightCatalog()
            }
            Effect.SkipMoonlightImport -> {
                store.setMoonlightImportSettled()
                publish(next)
            }
            Effect.ReviewMoonlightImport -> {
                val screenForSheet = next.panel?.screen ?: screen
                publish(next)
                onReviewMoonlight(screenForSheet)
            }
            Effect.PinApp -> {
                if (packageName != null) {
                    val favorite = !shelfState.record(packageName).favorite
                    shelfState = shelfState.pin(packageName, favorite)
                    store.saveAppShelfState(shelfState)
                }
                publish(focusPackage(next, packageName))
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
            else -> {
                publish(next)
                if (next.panel?.level == PanelLevel.Library) {
                    refreshLibraries()
                }
            }
        }
        cueMeaning(meaning, before, model)
        if (effect == Effect.DismissButtonLabels) {
            store.saveButtonPrompt(inputDeviceKey, store.buttonPromptConfirm(inputDeviceKey), dismissed = true)
        }
        return when (effect) {
            Effect.PinApp, Effect.MoveApp, Effect.HideApp, Effect.ShowApp,
            Effect.SkipMoonlightImport, Effect.ReviewMoonlightImport,
            -> null
            is Effect.ConfirmMoonlightImport -> null
            Effect.EditHome -> {
                enterHomeEdit()
                Log.i("Foldcade", "home-ui editing")
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
            else -> effect
        }
    }

    fun homeKeys(): app.foldcade.language.HomeKeys {
        if (model.dialog != null || model.panel != null || model.connectOpen) {
            return app.foldcade.language.HomeKeys.Idle
        }
        if (model.libraryGrid || model.homeGrid != HomeGrid.StandIns) {
            return app.foldcade.language.HomeKeys.Idle
        }
        return home.keys()
    }

    fun homeFace(index: Int): HomeFace? = home.face(index)

    fun homeHint(): String? = if (showingHome() && model.dialog == null && model.panel == null) home.hint() else null

    fun homeChrome(): AllChrome? = if (showingHome() && model.dialog == null && model.panel == null && !model.connectOpen) {
        home.chrome()
    } else {
        null
    }

    fun homeEditing(): Boolean =
        showingHome() && home.board.editing && model.dialog == null && model.panel == null

    fun homeAllOpen(): Boolean =
        showingHome() && home.board.allOpen && model.dialog == null && model.panel == null

    fun homeLiftedIndex(): Int? = home.board.hold?.index

    fun homeAnimToken(): String = home.animToken()

    fun catalogGame(id: String): Game? = home.game(id)

    fun enterHomeEdit() {
        home.enterEditing()
        store.saveHomeBoard(home.encoded())
        showBoard(model.focus, keepDialog = true)
    }

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
        home.pickUpFocused(index)
        store.saveHomeBoard(home.encoded())
        showBoard(GridFocus(cellIndex = index, lastColumn = index % Metrics.columns), keepDialog = true)
    }

    fun dragHome(meaning: Meaning) {
        if (home.board.hold == null) return
        home.drag(meaning)
        val index = home.board.hold?.index ?: return
        store.saveHomeBoard(home.encoded())
        showBoard(GridFocus(cellIndex = index, lastColumn = index % Metrics.columns), keepDialog = true)
    }

    fun touchAllChrome(index: Int, screen: HostScreen): Effect? {
        if (!home.board.allOpen || model.dialog != null || model.panel != null) return null
        if (home.board.allChrome != index) {
            home.focusChrome(index)
            store.saveHomeBoard(home.encoded())
            showBoard(model.focus, keepDialog = true)
            return null
        }
        return onMeaning(Meaning.Activate, screen)
    }

    /**
     * Folds one backend's games into the curated board.
     * A later scan keeps placed tiles and only fills new ones when the setting is on.
     */
    fun ingestLibrary(
        libraryId: String,
        entries: List<GridEntry>,
        platforms: List<Platform>,
        listedNames: Map<String, String> = emptyMap(),
    ) {
        listedPlatformNames.putAll(listedNames.filterValues { it.isNotBlank() })
        val prefix = "lib:$libraryId:"
        val items = ArrayList<HomeItem>()
        val remembered = HashMap<String, Game>()
        for (entry in entries) {
            val game = entry.game ?: continue
            val id = homeGameId(libraryId, game.remoteKey)
            val kind = when (libraryId) {
                "gamenative" -> HomeKind.GameNative
                "moonlight" -> HomeKind.Moonlight
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
            remembered[id] = game
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
        syncHome()
        if (!model.libraryGrid && model.homeGrid == HomeGrid.StandIns) {
            showBoard(model.focus, keepDialog = true)
        }
    }

    /** The scanned library is on the home grid. Emulator waits for this line. */
    fun showCuratedHome() {
        syncHome()
        showBoard(GridFocus(), keepDialog = true)
        Log.i("Foldcade", "library-ui home")
    }

    fun setInstalledApps(apps: List<LaunchableApp>, players: Set<String>) {
        installed = apps.distinctBy { it.packageName }
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
        model = model.copy(
            connectOrigin = origin,
            connectWarning = cleartextCredentialWarning(origin),
        )
    }

    fun editToken(value: String) {
        connectToken = value
    }

    fun consumeConnectToken(): String = connectToken.also { connectToken = "" }

    fun openConnect(origin: String) {
        val screen = model.connectScreen ?: HostScreen.Top
        connectToken = ""
        model = model.copy(
            connectOpen = true,
            connectScreen = screen,
            panel = null,
            connectOrigin = origin,
            connectIndex = 0,
            connectWarning = cleartextCredentialWarning(origin),
            connectHint = null,
        )
    }

    fun showSetupHint(hint: String?) {
        model = model.copy(connectHint = hint)
    }

    fun setSignedIn(backends: List<SignedInBackend>) {
        model = model.copy(signedIn = backends)
    }

    /** Leaves the connect form, as after a server accepts the token. */
    fun closeConnect() {
        connectToken = ""
        model = model.copy(connectOpen = false, connectScreen = null, connectHint = null)
    }

    fun askToSignInAgain() {
        if (model.dialog != null || model.moonlightSheet != null) {
            model = model.copy(reLoginPending = true)
            return
        }
        connectToken = ""
        model = model.copy(
            dialog = signInAgainPrompt(),
            connectOpen = false,
            connectScreen = null,
        )
    }

    fun present(dialog: DialogState) {
        if (model.dialog != null || model.moonlightSheet != null) return
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
        if (!model.reLoginPending || model.dialog != null) return
        model = model.copy(reLoginPending = false, dialog = signInAgainPrompt())
    }

    /** One tap focuses a text row. Save activates on that tap. */
    fun touchConnect(index: Int, screen: HostScreen): Effect? {
        val field = connectFields().getOrNull(index) ?: return null
        if (field != ConnectField.Save) {
            if (model.connectIndex != index) model = model.copy(connectIndex = index)
            return null
        }
        val (next, effect) = reduce(model.copy(connectIndex = index), Meaning.Activate, screen)
        if (!next.connectOpen) connectToken = ""
        publish(next)
        if (next.panel?.level == PanelLevel.Library) {
            refreshLibraries()
        }
        return effect
    }

    /**
     * Library names come from [PluginHost.libraryLabel].
     * The shell does not call a plugin object itself.
     * RomM is [Copy.setUpRomm] until a server origin is saved.
     * A plugin failure becomes the unavailable state.
     * [CancellationException] and [VirtualMachineError] still propagate.
     */
    fun refreshLibraries() {
        try {
            model = model.copy(backends = libraryNames(), unavailable = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (fatal: VirtualMachineError) {
            throw fatal
        } catch (_: Throwable) {
            model = model.copy(unavailable = true)
        }
    }

    private fun libraryNames(): List<String> {
        val rommReady = !store.rommOrigin().isNullOrBlank()
        return plugins.libraryIds().map { id ->
            if (id == RommCredentials.PLUGIN_ID && !rommReady) {
                Copy.setUpRomm
            } else {
                plugins.libraryLabel(id)
            }
        }
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

    fun touchCell(index: Int, screen: HostScreen) {
        if (model.dialog != null || model.moonlightSheet != null || model.panel != null || model.connectOpen) return
        val current = model.focus
        val already = current.chrome == null && current.cellIndex == index
        if (!already) {
            publish(
                model.copy(
                    focus = GridFocus(
                        cellIndex = index,
                        lastColumn = index % Metrics.columns,
                        chrome = null,
                    ),
                ),
            )
            cue(model.themeIndex, "move")
            return
        }
        onMeaning(Meaning.Activate, screen)
    }

    fun touchChrome(chrome: Chrome, screen: HostScreen) {
        if (model.dialog != null || model.moonlightSheet != null || model.panel != null || model.connectOpen) return
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

    /** One tap focuses that sheet row and activates it. Keys still move, then Activate. */
    fun touchMoonlight(index: Int, screen: HostScreen): Effect? {
        val sheet = model.moonlightSheet ?: return null
        if (sheet.screen != screen) return null
        val last = sheet.apps.size + 1
        if (index !in 0..last) return null
        model = model.copy(moonlightSheet = sheet.copy(index = index))
        return onMeaning(Meaning.Activate, screen)
    }

    fun maybeOfferMoonlightImport(apps: List<MoonlightDiscoveredApp>) {
        if (store.moonlightImportSettled() || apps.isEmpty()) return
        if (model.dialog != null || model.moonlightSheet != null || model.panel != null || model.connectOpen) return
        presentMoonlightSheet(apps, HostScreen.Bottom)
    }

    fun presentMoonlightSheet(apps: List<MoonlightDiscoveredApp>, screen: HostScreen) {
        if (model.dialog != null || model.moonlightSheet != null) return
        val sheet = moonlightImportSheet(apps, screen) ?: return
        model = model.copy(moonlightSheet = sheet)
    }

    fun selectImportedList() {
        if (model.moonlightSource == MoonlightSource.ImportedList) return
        publish(model.copy(moonlightSource = MoonlightSource.ImportedList))
    }

    fun setUsageGranted(granted: Boolean) {
        if (model.usageGranted == granted) return
        model = model.copy(usageGranted = granted)
    }

    /** Once, after a session exists. Never on a cold start, and never over another dialog. */
    fun maybeOfferUsageAccess(hasTrackedSession: Boolean, granted: Boolean, screen: HostScreen) {
        val blocked = model.dialog != null || model.moonlightSheet != null
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
        if (store.homePromptSettled() || model.dialog != null || model.moonlightSheet != null) return
        publish(model.copy(dialog = homePrompt()))
    }

    /** Debuggable captures. Replaces whatever dialog is up. */
    fun showPreviewDialog(kind: String, index: Int, screen: HostScreen) {
        val dialog = previewDialogState(kind, index, screen) ?: return
        Log.i(
            "Foldcade",
            "preview-dialog kind=$kind index=${dialog.index} screen=${screen.name.lowercase()} title=${dialog.title}",
        )
        publish(model.copy(panel = null, connectOpen = false, dialog = dialog, moonlightSheet = null))
    }

    fun focusedGame(): ShelfGame? = when {
        showingHome() -> home.face(model.focus.cellIndex)?.let { home.asShelf(it) }
        else -> tileFromOrder(displaySource(model))
    }

    /**
     * What the idle top screen shows for the focused cell.
     * A platform folder is its name and game count, with no artwork.
     * An empty or loading grid has nothing to show.
     */
    fun focusedHero(): HeroSubject? {
        if (model.gridKind == GridKind.Platforms) {
            val entry = focusedEntry() ?: return null
            return HeroSubject.Folder(
                HeroFolder(
                    key = entry.id,
                    name = entry.title,
                    count = entry.shortText.toIntOrNull(),
                ),
            )
        }
        if (model.libraryGrid && model.gridKind != GridKind.Games) return null
        val game = focusedGame() ?: return null
        return HeroSubject.Item(
            HeroItem(
                key = game.id,
                title = game.title,
                detail = game.shortText,
                mark = game.mark,
                packageName = game.androidPackage,
                emptyShelfHint = game.emptyShelfHint,
                availability = game.availabilityLabel,
            ),
        )
    }

    fun tileFromOrder(source: Int): ShelfGame? = when {
        model.libraryGrid -> entries.getOrNull(source)?.asShelf()
        showingHome() -> home.face(source)?.let { home.asShelf(it) }
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
        if (model.dialog != null || model.moonlightSheet != null) return
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
        val entry = cells.getOrNull(source)
        val onGrid = kind == GridKind.Games || kind == GridKind.Platforms
        val visible = onGrid &&
            count > 0 &&
            focus.cellIndex in 0 until count &&
            store.session.launchTargetControlVisible(entry?.occupiesBothDisplays == true)
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
                showLaunchTarget = visible,
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
        val counted = next.copy(count = count, playerSaves = playerSaveSettings(), recentFirst = recentOrder())
        val game = tileOn(counted)
        val visible = game != null &&
            !game.emptyShelfHint &&
            store.session.launchTargetControlVisible(game.occupiesBothDisplays)
        return counted.copy(showLaunchTarget = visible, appActions = actionsFor(counted, game))
    }

    private fun tileOn(snapshot: PickerModel): ShelfGame? {
        if (!snapshot.libraryGrid && snapshot.homeGrid == HomeGrid.StandIns) {
            return home.face(snapshot.focus.cellIndex)?.let { home.asShelf(it) }
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

    /**
     * The shelf gained or lost catalog games, or the empty-shelf hint changed.
     * [PickerModel.shelfEpoch] changes even when the count does not, so Compose
     * redraws the hint. Focus stays inside the new count.
     */
    fun noteShelfChanged() {
        syncHome()
        val next = model.copy(shelfEpoch = model.shelfEpoch + 1)
        if (model.libraryGrid) {
            model = next
            return
        }
        val counted = withShelf(next)
        val last = (counted.count - 1).coerceAtLeast(0)
        val cell = counted.focus.cellIndex.coerceIn(0, last)
        model = if (cell == counted.focus.cellIndex) {
            counted
        } else {
            counted.copy(
                focus = counted.focus.copy(
                    cellIndex = cell,
                    lastColumn = if (counted.count == 0) 0 else cell % Metrics.columns,
                ),
            )
        }
    }

    /** Play history changed. Recently played order follows the new last-played times. */
    fun notePlayChanged() {
        val next = recentOrder()
        if (next == model.recentFirst) return
        model = model.copy(recentFirst = next)
    }

    private fun publish(next: PickerModel) {
        if (next.primaryIsTop != store.session.defaultDisplayIsTop) {
            store.update { it.withDefaultDisplayIsTop(next.primaryIsTop) }
        }
        if (next.sort != model.sort) store.setLibrarySort(next.sort)
        if (next.themeIndex != model.themeIndex) onTheme(next.themeIndex)
        if (next.music != model.music) {
            store.setMusic(next.music.enabled, next.music.volume, next.music.trackId)
            onMusic(next.music)
        }
        if (next.backgroundPinned && next.backgroundMotion != model.backgroundMotion) {
            store.setBackgroundMotion(next.backgroundMotion)
        }
        if (next.motionSpeed != model.motionSpeed) {
            store.setMotionSpeed(next.motionSpeed)
        }
        val moonlightSourceChanged = next.moonlightSource != model.moonlightSource
        if (next.moonlightPlacements != model.moonlightPlacements) {
            store.setMoonlightPlacements(next.moonlightPlacements)
        }
        if (moonlightSourceChanged) store.setMoonlightSource(next.moonlightSource)
        model = if (next.libraryGrid) {
            val entry = libraryEntry(next)
            val onGrid = next.gridKind == GridKind.Games || next.gridKind == GridKind.Platforms
            val visible = onGrid &&
                next.count > 0 &&
                store.session.launchTargetControlVisible(entry?.occupiesBothDisplays == true)
            next.copy(showLaunchTarget = visible, appActions = null, playerSaves = playerSaveSettings())
        } else {
            withShelf(next.copy(libraryGrid = false))
        }
        if (moonlightSourceChanged) onMoonlightCatalog()
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
    }

    private fun actionsFor(snapshot: PickerModel, game: ShelfGame?): AppActions? {
        val packageName = game?.androidPackage ?: return null
        if (snapshot.homeGrid == HomeGrid.StandIns) return null
        val record = shelfState.record(packageName)
        return AppActions(
            favorite = record.favorite,
            onGamesShelf = snapshot.homeGrid == HomeGrid.AndroidGames,
            hiddenShelf = snapshot.homeGrid == HomeGrid.HiddenApps,
        )
    }

    private fun focusPackage(next: PickerModel, packageName: String?): PickerModel {
        if (packageName == null || next.homeGrid == HomeGrid.StandIns) return next
        val source = listed(next.homeGrid).indexOfFirst { it.packageName == packageName }
        if (source < 0) return next
        val order = displayOrder(next.copy(count = listed(next.homeGrid).size))
        val display = order.indexOf(source).takeIf { it >= 0 } ?: source
        val column = display % Metrics.columns
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

    private fun showingHome(): Boolean =
        !model.libraryGrid && model.homeGrid == HomeGrid.StandIns

    private fun homeActive(): Boolean =
        showingHome() && model.dialog == null && model.panel == null && model.settings == null && !model.connectOpen

    private fun showBoard(focus: GridFocus, keepDialog: Boolean) {
        val count = home.visibleCount()
        val cell = focus.cellIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
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
                    lastColumn = if (count == 0) 0 else cell % Metrics.columns,
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
        val changed = home.remerge(
            shelfItems(),
            appItems(),
            libraryItems,
            homePlatforms(),
            libraryGames,
            unsettled = { id -> libraryOf(id)?.let { it !in settledLibraries } == true },
        )
        if (changed) store.saveHomeBoard(home.encoded())
    }

    private fun shelfItems(): List<HomeItem> = Shelf.games.mapNotNull { game ->
        val kind = when {
            game.id == Shelf.PC_TILE || game.libraryId == "gamenative" -> HomeKind.GameNative
            game.id == "moonlight" || game.libraryId == "moonlight" -> HomeKind.Moonlight
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

    private fun appItems(): List<HomeItem> = installed.map { app ->
        val kind = if (app.systemGame) HomeKind.AndroidGame else HomeKind.AndroidApp
        HomeItem(
            id = androidHomeId(app.packageName),
            title = app.label,
            kind = kind,
            mark = bucketMark(kind),
        )
    }

    private fun homePlatforms(): List<HomePlatform> {
        val known = listOf(
            HomePlatform("nintendo-3ds", "Nintendo 3DS", setOf("3ds", "n3ds", "new-nintendo-3ds"), "dual"),
            HomePlatform("nintendo-ds", "Nintendo DS", setOf("nds"), "dual"),
            HomePlatform("game-boy", "Game Boy", setOf("gb"), "pocket"),
            HomePlatform("game-boy-color", "Game Boy Color", setOf("gbc"), "pocket"),
            HomePlatform("game-boy-advance", "Game Boy Advance", setOf("gba"), "pocket"),
            HomePlatform("game-gear", "Game Gear", setOf("gg"), "pocket"),
            HomePlatform("psp", "PSP", setOf("psp"), "pocket"),
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
        val index = if (names.size > 1) 1 else 0
        val pinned = store.backgroundMotionPinned()
        val motion = if (pinned) {
            store.backgroundMotion()
        } else {
            themeMotions.getOrElse(index) { BackgroundMotion.Off }
        }
        val stored = HomeMusicSetting(store.musicEnabled(), store.musicVolume(), store.musicTrackId())
        val offered = offeredMusicTracks(homeTracks, themeTracks.getOrNull(index))
        val selected = selectedMusicTrack(offered, stored.trackId)
        if (selected.id != stored.trackId) {
            store.setMusic(stored.enabled, stored.volume, selected.id)
        }
        syncHome()
        return PickerModel(
            count = home.visibleCount(),
            addNewToHome = home.board.addNewToHome,
            rowsPerPage = 2,
            showLaunchTarget = true,
            primaryIsTop = store.session.defaultDisplayIsTop,
            folderGrantPending = store.folderGrantPending(),
            music = stored.copy(trackId = selected.id),
            trackTitle = selected.title,
            homeTracks = homeTracks,
            themeTracks = themeTracks,
            themes = names,
            themeIndex = index,
            themeMotions = themeMotions,
            backgroundMotion = motion,
            backgroundPinned = pinned,
            motionSpeed = store.motionSpeed(),
            sort = store.librarySort(),
            recentFirst = recentOrder(),
            moonlightSource = store.moonlightSource(),
            moonlightPlacements = store.moonlightPlacements(),
            moonlightImportConfirmed = store.moonlightImportConfirmed(),
        )
    }

    /** The move clip when the theme has one. Otherwise the short original tick. */
    private fun tickSlider(themeIndex: Int) {
        if (!cue(themeIndex, "move")) bareTick()
    }

    /** Volume steps and track changes. Either one ticks while the row stays put. */
    private fun valueAdjusted(before: PickerModel, after: PickerModel): Boolean =
        before.music.volume != after.music.volume || before.music.trackId != after.music.trackId

    private fun cueMeaning(meaning: Meaning, before: PickerModel, after: PickerModel) {
        when (meaning) {
            Meaning.Activate -> cue(after.themeIndex, "activate")
            Meaning.Back -> cue(after.themeIndex, "back")
            Meaning.MoveUp, Meaning.MoveDown, Meaning.MoveLeft, Meaning.MoveRight -> {
                val moved = before.focus != after.focus ||
                    before.panel?.index != after.panel?.index ||
                    before.moonlightSheet?.index != after.moonlightSheet?.index
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
