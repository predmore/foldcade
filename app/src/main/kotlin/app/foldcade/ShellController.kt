package app.foldcade

import app.foldcade.api.plugin.RommCredentials
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.host.PluginHost
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
import app.foldcade.language.GridFocus
import app.foldcade.language.DEFAULT_TRACK_TITLE
import app.foldcade.host.play.recentlyPlayedIndices
import app.foldcade.language.HomeMusicSetting
import app.foldcade.language.HostScreen
import app.foldcade.language.Meaning
import app.foldcade.language.MotionSpeed
import app.foldcade.language.Metrics
import app.foldcade.language.PanelLevel
import app.foldcade.language.PickerModel
import app.foldcade.language.PlayerSaveSetting
import app.foldcade.language.SignedInBackend
import app.foldcade.language.connectFields
import app.foldcade.language.displayOrder
import app.foldcade.language.focusAndActivateDialog
import app.foldcade.language.homePrompt
import app.foldcade.language.panelRows
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
    private val trackTitle: String = DEFAULT_TRACK_TITLE,
    private val themeNames: List<String> = listOf(Copy.builtIn),
    private val themeMotions: List<BackgroundMotion> = listOf(BackgroundMotion.Off),
    private val cue: (themeIndex: Int, slot: String) -> Boolean = { _, _ -> false },
    private val bareTick: () -> Unit = {},
    private val lastPlayedMillis: (String) -> Long? = { null },
) {
    private var shelfState: AppShelfState = store.appShelfState()
    private var installed: List<LaunchableApp> = emptyList()
    private var playerPackages: Set<String> = emptySet()

    var model by mutableStateOf(initial())
        private set

    var connectToken by mutableStateOf("")
        private set

    fun onMeaning(meaning: Meaning, screen: HostScreen): Effect? {
        val before = model
        val current = withShelf(model)
        val (next, effect) = reduce(current, meaning, screen)
        if (!next.connectOpen) connectToken = ""
        val packageName = focusedGame()?.androidPackage
        when (effect) {
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
        return when (effect) {
            Effect.PinApp, Effect.MoveApp, Effect.HideApp, Effect.ShowApp -> null
            else -> effect
        }
    }

    fun setInstalledApps(apps: List<LaunchableApp>, players: Set<String>) {
        installed = apps.distinctBy { it.packageName }
        playerPackages = players
        publish(model)
    }

    fun showHomeGrid(grid: HomeGrid) {
        publish(
            model.copy(
                homeGrid = grid,
                panel = null,
                arranging = false,
                hold = null,
                order = emptyList(),
                focus = GridFocus(),
                connectOpen = false,
            ),
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

    fun askToSignInAgain() {
        if (model.dialog != null) {
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
        if (model.dialog != null) return
        model = model.copy(dialog = dialog)
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
        if (model.dialog != null || model.panel != null || model.connectOpen) return
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

    /** One tap focuses the button and activates it. Keys still move, then Activate. */
    fun touchDialog(index: Int, screen: HostScreen): Effect? {
        val dialog = model.dialog ?: return null
        if (dialog.screen != screen) return null
        val (next, effect) = focusAndActivateDialog(model, index)
        publish(next)
        return effect
    }

    fun maybeAskHome(roleHeld: Boolean) {
        if (roleHeld) {
            store.setHomePromptSettled()
            return
        }
        if (store.homePromptSettled() || model.dialog != null) return
        publish(model.copy(dialog = homePrompt()))
    }

    fun focusedGame(): ShelfGame? = tileFromOrder(displaySource(model))

    fun tileFromOrder(source: Int): ShelfGame? = when (model.homeGrid) {
        HomeGrid.StandIns -> Shelf.games.getOrNull(source)
        else -> listed(model.homeGrid).getOrNull(source)?.asTile(model.homeGrid)
    }

    fun refreshPlayerSaves() {
        model = model.copy(playerSaves = playerSaveSettings())
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
     * redraws the hint.
     */
    fun noteShelfChanged() {
        model = withShelf(model.copy(shelfEpoch = model.shelfEpoch + 1))
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
        model = withShelf(next)
    }

    private fun countFor(grid: HomeGrid): Int = when (grid) {
        HomeGrid.StandIns -> Shelf.games.size
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
        return PickerModel(
            count = Shelf.games.size,
            rowsPerPage = 2,
            showLaunchTarget = true,
            primaryIsTop = store.session.defaultDisplayIsTop,
            folderGrantPending = store.folderGrantPending(),
            music = HomeMusicSetting(store.musicEnabled(), store.musicVolume(), store.musicTrackId()),
            trackTitle = trackTitle,
            themes = names,
            themeIndex = index,
            themeMotions = themeMotions,
            backgroundMotion = motion,
            backgroundPinned = pinned,
            motionSpeed = store.motionSpeed(),
            sort = store.librarySort(),
            recentFirst = recentOrder(),
        )
    }

    /** The move clip when the theme has one. Otherwise the short original tick. */
    private fun tickSlider(themeIndex: Int) {
        if (!cue(themeIndex, "move")) bareTick()
    }

    /** Slider rows. Music volume is the only one; another slider joins this check. */
    private fun sliderMoved(before: PickerModel, after: PickerModel): Boolean =
        before.music.volume != after.music.volume

    private fun cueMeaning(meaning: Meaning, before: PickerModel, after: PickerModel) {
        when (meaning) {
            Meaning.Activate -> cue(after.themeIndex, "activate")
            Meaning.Back -> cue(after.themeIndex, "back")
            Meaning.MoveUp, Meaning.MoveDown, Meaning.MoveLeft, Meaning.MoveRight -> {
                val moved = before.focus != after.focus || before.panel?.index != after.panel?.index
                val slid = sliderMoved(before, after)
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
