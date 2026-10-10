package app.foldcade

import android.app.Activity
import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.net.Uri
import android.provider.Settings
import android.view.InputDevice
import android.os.Bundle
import android.view.Display
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.foldcade.host.play.PlaySignal
import app.foldcade.api.isAndroidHomeRecall
import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.StartDisplay
import app.foldcade.language.Copy
import app.foldcade.language.DialogButton
import app.foldcade.language.DialogKind
import app.foldcade.language.DialogState
import app.foldcade.language.Effect
import app.foldcade.language.GridKind
import app.foldcade.language.HomeGrid
import app.foldcade.language.HomeKeys
import app.foldcade.language.HostScreen
import app.foldcade.language.closeBothPanelDialog
import app.foldcade.language.homeGameId
import app.foldcade.language.missingPlayerDialog
import app.foldcade.language.noFileDialog
import app.foldcade.language.playerNotice
import app.foldcade.language.saveFolderDialog
import app.foldcade.language.PanelKeyActivity
import app.foldcade.language.PromptKey
import app.foldcade.language.interpretThorStyle
import app.foldcade.language.SignedInBackend
import app.foldcade.language.alsoRunsLine
import app.foldcade.romm.RommClient
import app.foldcade.romm.RommSignInResult
import app.foldcade.romm.normalizeSetupOrigin
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.foldcade.ui.PanelHost

abstract class FoldcadeHomeActivity : PanelKeyActivity() {
    protected abstract val launchesCompanion: Boolean

    private companion object {
        const val EXTRA_DIALOG = "foldcade.dialog"
        const val EXTRA_DIALOG_INDEX = "foldcade.dialogIndex"
        const val EXTRA_DIALOG_SCREEN = "foldcade.dialogScreen"
    }

    private val displays by lazy { Displays(this) }
    private val foldcade by lazy { application as FoldcadeApp }
    private var resumed = false

    /** True while this panel is resumed and its display is fully on. */
    var shellVisible by mutableStateOf(false)
        private set

    private val backdropDisplayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refreshShellVisible()
        override fun onDisplayRemoved(displayId: Int) = refreshShellVisible()
        override fun onDisplayChanged(displayId: Int) = refreshShellVisible()
    }

    private var folderPurpose = FolderPurpose.Library
    private var pendingLaunch: Int? = null
    private var closeConfirmed = false
    /** A dismissed or replaced launch. An older attempt must not present a dialog later. */
    private var launchGeneration = 0
    private var settingsPlayerId: String? = null
    private var libraryLaunching = false
    private var libraryReturn: LibraryReturn? = null

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val purpose = folderPurpose
        folderPurpose = FolderPurpose.Library
        if (uri == null) {
            when (purpose) {
                FolderPurpose.LaunchOffer -> skipSaveFolderAndLaunch()
                FolderPurpose.Settings -> settingsPlayerId = null
                FolderPurpose.Library -> Unit
            }
            return@registerForActivityResult
        }
        when (purpose) {
            FolderPurpose.Library -> {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                foldcade.useFolder(uri)
            }
            FolderPurpose.LaunchOffer -> {
                rememberSaveFolder(uri, playerForPendingLaunch())
                pendingLaunch?.let { launchGame(it) }
            }
            FolderPurpose.Settings -> {
                val player = settingsPlayerId?.let { foldcade.plugins.player(it) }
                settingsPlayerId = null
                rememberSaveFolder(uri, player)
            }
        }
    }

    private val homeRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val held = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
        foldcade.shell.setHomeRoleHeld(held)
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            launchCompanionIfNeeded()
        }

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) = Unit
    }

    // The hero has no controls of its own. Android gives its display input focus after a
    // launcher-icon start or Home on that screen, so its keys drive the picker too.
    override fun pickerIsFocused(): Boolean = foldcadeSurfaceFocused()

    override fun foldcadeSurfaceFocused(): Boolean {
        val panel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop) ?: return false
        return foldcade.store.session.surfaceOn(panel) != null
    }

    override fun faceMap(): app.foldcade.language.FaceMap = foldcade.shell.model.faceMap

    override fun onPromptHeld(key: PromptKey, held: Boolean) {
        foldcade.shell.setPromptHeld(key, held)
    }

    override fun capturingConfirm(): Boolean = foldcade.shell.model.capturingConfirm

    override fun onCalibrateConfirm(key: PromptKey) {
        foldcade.shell.calibrateConfirm(key)
    }

    override fun homeKeys(): HomeKeys = foldcade.shell.homeKeys()

    override fun onMeaning(meaning: app.foldcade.language.Meaning) {
        val openingMenu = meaning == app.foldcade.language.Meaning.LeftPanel ||
            meaning == app.foldcade.language.Meaning.RightPanel
        if (foldcade.shell.model.dialog == null &&
            (openingMenu || meaning == app.foldcade.language.Meaning.Back)
        ) {
            cancelLaunches()
        }
        foldcade.music.duckForThemeSound(meaning)
        val panel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop) ?: return
        val screen = if (panel == Panel.Top) HostScreen.Top else HostScreen.Bottom
        dispatch(foldcade.shell.onMeaning(meaning, screen))
    }

    fun dispatch(effect: Effect?) {
        when (effect) {
            is Effect.Launch -> if (foldcade.shell.model.libraryGrid) {
                launchLibraryGame(effect.index)
            } else {
                launchFocused()
            }
            is Effect.OpenPlatform -> foldcade.openPlatform(effect.index)
            Effect.LeavePlatform -> foldcade.leavePlatform()
            Effect.TryAgain -> foldcade.retryLibrary()
            Effect.CycleLaunchTarget -> cycleLaunchTarget()
            Effect.AddFolder -> {
                folderPurpose = FolderPurpose.Library
                folderPicker.launch(null)
            }
            is Effect.ChoosePlayerSave -> choosePlayerSave(effect.playerId)
            Effect.PinApp, Effect.MoveApp, Effect.HideApp, Effect.ShowApp -> Unit
            Effect.EditHome -> foldcade.shell.enterHomeEdit()
            Effect.OpenAll -> foldcade.shell.openHomeAll()
            Effect.ToggleAddNew -> foldcade.shell.applyHomeAddNew(foldcade.shell.model.addNewToHome)
            Effect.OpenConnect -> foldcade.shell.openConnect(foldcade.store.rommOrigin().orEmpty())
            Effect.SaveRommToken -> saveRommToken()
            is Effect.ForgetCredentials -> forget(effect.pluginId)
            is Effect.ForgetFolder -> foldcade.forgetFolder(effect.uri)
            is Effect.DialogChoice -> onDialog(effect)
            Effect.RequestHome -> requestHome()
            is Effect.OpenAndroidSetting -> openAndroidSetting(effect.setting)
            Effect.OpenLicenses -> startActivity(Intent(this, LicensesActivity::class.java))
            Effect.DismissButtonLabels -> Unit
            is Effect.ConfirmMoonlightImport -> Unit
            Effect.SkipMoonlightImport -> Unit
            Effect.ReviewMoonlightImport -> Unit
            null -> Unit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptHome(intent)
        acceptShelf(intent)
        acceptFolderLibrary(intent)
        val roleManager = getSystemService(RoleManager::class.java)
        val held = roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        foldcade.shell.setHomeRoleHeld(held)
        foldcade.shell.maybeAskHome(held)
        applyDialogPreview(intent)
        applyIslandPreview(intent)
        if (launchesCompanion) {
            val manager = getSystemService(DisplayManager::class.java)
            manager.registerDisplayListener(displayListener, null)
            launchCompanionIfNeeded()
        }
        getSystemService(DisplayManager::class.java).registerDisplayListener(backdropDisplayListener, null)
        getSystemService(InputManager::class.java).registerInputDeviceListener(inputDevices, null)
        refreshFacePrompt()
        setContent { PanelHost(activity = this, displays = displays) }
        hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        val displayId = display?.displayId
        val ending = displayId != null &&
            foldcade.externalPlay.sessionId != null &&
            foldcade.externalPlay.displayId == displayId
        val pending = libraryReturn
        if (displayId != null) foldcade.externalPlay.endIfDisplayHome(foldcade.plays, displayId)
        if (ending && foldcade.externalPlay.sessionId == null && pending != null) {
            libraryReturn = null
            reconcile(pending)
        }
        // Foldcade in front on this panel means the game there has left. A launcher-icon
        // start is not a Home recall, so without this a Foldcade that is not the default
        // Home keeps the panel for the game and ignores every key.
        displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop)?.let { panel ->
            foldcade.store.update { it.foldcadeResumed(panel) }
        }
        resumed = true
        refreshShellVisible()
        foldcade.reloadInstalledApps()
        refreshFacePrompt()
        displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop)?.let(foldcade.music::onHomeResume)
        foldcade.refreshMoonlightShelf()
        reconcileUsage()
    }

    override fun onPause() {
        resumed = false
        refreshShellVisible()
        displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop)?.let(foldcade.music::onHomePause)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptHome(intent)
        acceptShelf(intent)
        acceptFolderLibrary(intent)
        applyDialogPreview(intent)
        // Home relaunches this activity before a debug capture intent arrives.
        applyIslandPreview(intent, replace = true)
    }

    private fun applyDialogPreview(intent: Intent) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val kind = intent.getStringExtra(EXTRA_DIALOG) ?: return
        val index = intent.getIntExtra(EXTRA_DIALOG_INDEX, 0)
        val screen = if (intent.getStringExtra(EXTRA_DIALOG_SCREEN) == "top") {
            HostScreen.Top
        } else {
            HostScreen.Bottom
        }
        foldcade.shell.showPreviewDialog(kind, index, screen)
    }

    override fun onDestroy() {
        val manager = getSystemService(DisplayManager::class.java)
        manager.unregisterDisplayListener(backdropDisplayListener)
        if (launchesCompanion) manager.unregisterDisplayListener(displayListener)
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(inputDevices)
        super.onDestroy()
    }

    private val inputDevices = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refreshFacePrompt()
        override fun onInputDeviceRemoved(deviceId: Int) = refreshFacePrompt()
        override fun onInputDeviceChanged(deviceId: Int) = refreshFacePrompt()
    }

    private fun refreshFacePrompt() {
        foldcade.shell.applyFacePrompt(
            style = interpretThorStyle(readThorControllerStyle()),
            deviceKey = inputDeviceKey(this),
            firstSession = foldcade.buttonPromptFirstSession,
        )
    }

    /** Readable Thor style, or null when the setting is missing, blank, or blocked. */
    private fun readThorControllerStyle(): String? {
        val resolver = contentResolver
        val keys = listOf(
            "controller_style",
            "thor_controller_style",
            "gamepad_style",
            "ayn_controller_style",
        )
        for (key in keys) {
            val value = readSystemOrGlobal(resolver, key) ?: continue
            if (value.isNotBlank()) return value
        }
        return null
    }

    private fun refreshShellVisible() {
        val state = display?.state ?: Display.STATE_ON
        shellVisible = resumed && state == Display.STATE_ON
    }

    private fun applyIslandPreview(intent: Intent, replace: Boolean = false) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val raw = intent.getStringExtra("foldcade.island")
        if (raw == "closed") {
            foldcade.islandHold = null
            cancelLaunches()
            foldcade.shell.dismissDialog()
            foldcade.shell.closePanel()
            return
        }
        val hold = parseIslandHold(raw) ?: return
        if (!replace && foldcade.islandHold != null) return
        // A leftover dialog swallows shoulders and leaves the bottom screen sharp.
        // Show this shoulder directly. onMeaning would retire the other island and
        // leave that panel on screen under the new hold.
        cancelLaunches()
        foldcade.shell.dismissDialog()
        foldcade.shell.previewIsland(hold.side)
        foldcade.islandHold = hold
    }

    private fun acceptHome(intent: Intent) {
        if (!isAndroidHomeRecall(intent.action, intent.categories ?: emptySet())) return
        val panel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop) ?: return
        foldcade.store.update { it.home(panel) }
    }

    private fun launchCompanionIfNeeded() {
        if (foldcade.companionLaunched) return
        val assignment = displays.assignment(foldcade.store.session.defaultDisplayIsTop)
        val other = assignment.allIds().firstOrNull { it != Display.DEFAULT_DISPLAY } ?: return
        foldcade.companionLaunched = true
        val intent = Intent(this, CompanionHomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().apply { launchDisplayId = other }
        startActivity(intent, options.toBundle())
    }

    private fun launchGame(index: Int) {
        val game = Shelf.games.getOrNull(index) ?: return
        if (game.emptyShelfHint) return
        val player = game.platformId?.let(::playerFor)
        if (player == null) {
            if (game.platformId != null) {
                val generation = beginLaunch()
                pendingLaunch = index
                presentMissing("Player", generation)
                return
            }
            launchStandIn(game)
            return
        }
        val libraryId = game.libraryId
        val remoteKey = game.remoteKey
        if (libraryId != null && remoteKey != null) {
            if (libraryLaunching) return
            val generation = beginLaunch()
            libraryLaunching = true
            pendingLaunch = index
            val apiGame = shelfGame(game)
            foldcade.scope.launch {
                val target = try {
                    foldcade.plugins.ensureLocal(libraryId, apiGame)
                    withContext(Dispatchers.Main.immediate) { foldcade.shell.noteOnDevice(libraryId, remoteKey) }
                    foldcade.plugins.prepareLaunch(libraryId, apiGame, player).target
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (fatal: VirtualMachineError) {
                    throw fatal
                } catch (_: Exception) {
                    null
                }
                withContext(Dispatchers.Main.immediate) {
                    libraryLaunching = false
                    if (!launchCurrent(generation) || pendingLaunch != index) return@withContext
                    if (target == null) return@withContext
                    finishLaunch(index, game, player, target, apiGame, generation)
                }
            }
            return
        }
        val generation = beginLaunch()
        finishLaunch(
            index,
            game,
            player,
            game.contentUri?.let { LaunchTarget.ContentUri(it) },
            shelfGame(game),
            generation,
        )
    }

    private fun finishLaunch(
        index: Int,
        game: ShelfGame,
        player: Player,
        target: LaunchTarget?,
        apiGame: Game,
        generation: Int,
    ) {
        if (!launchCurrent(generation)) return
        val continuing = pendingLaunch == index && closeConfirmed
        val decision = try {
            planPlayerLaunch(
                player = player,
                game = apiGame,
                target = target,
                installedPackages = installedPackages(player.packageNames),
                anotherBothPanelRunning = anotherBothPanelRunning(foldcade.store.session, game.id),
                closeConfirmed = continuing,
                saveFolderSettled = foldcade.store.saveFolderPromptSkipped(player.id),
                componentResolves = { intent ->
                    packageManager.resolveActivity(intent.toAndroidIntent(), 0) != null
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: PluginException) {
            pendingLaunch = index
            foldcade.shell.present(
                playerNotice(player.displayName, error.message ?: Copy.missingPlayerBody, hostScreen()),
            )
            return
        }
        if (!launchCurrent(generation)) return
        pendingLaunch = index
        when (decision) {
            is PlayerLaunch.Blocked -> if (launchCurrent(generation)) when (decision.block) {
                LaunchBlock.CloseFirst -> foldcade.shell.present(closeBothPanelDialog(hostScreen()))
                LaunchBlock.MissingPlayer -> presentMissing(decision.playerName, generation)
                LaunchBlock.NoLocalFile -> foldcade.shell.present(noFileDialog(hostScreen()))
                LaunchBlock.SaveFolder -> foldcade.shell.present(saveFolderDialog(hostScreen()))
            }
            is PlayerLaunch.Ready -> {
                clearPendingLaunch()
                startPlayer(game, decision, apiGame, player, generation)
            }
        }
    }

    private fun acceptShelf(intent: Intent) {
        val grid = when (intent.getStringExtra(EXTRA_ANDROID_SHELF)) {
            "games" -> HomeGrid.AndroidGames
            "apps" -> HomeGrid.Apps
            else -> return
        }
        foldcade.shell.showHomeGrid(grid)
    }

    /** Emulator hook, same kind as the Android shelf extra. A saved folder still loads on its own. */
    private fun acceptFolderLibrary(intent: Intent) {
        if (!intent.hasExtra(EXTRA_FOLDER_LIBRARY)) return
        foldcade.reloadFolder()
    }

    /**
     * Same top or bottom placement as any other single-screen launch.
     * [PluginHost.openInstalledApp] does not read a save folder, so a missing one does not block this.
     */
    private fun launchInstalled(game: ShelfGame) {
        val pkg = game.androidPackage ?: return
        val opened = foldcade.plugins.openInstalledApp(pkg)
        val launch = packageManager.getLaunchIntentForPackage(pkg) ?: return
        val external = ExternalApp(opened.sessionId, occupiesBothDisplays = false)
        val session = foldcade.store.session
        val panel = session.singleScreenTarget(external.id, game.platformId) ?: return
        val assignment = displays.assignment(session.defaultDisplayIsTop)
        val displayId = when (panel) {
            Panel.Top -> assignment.topDisplayId
            Panel.Bottom -> assignment.bottomDisplayId ?: return
        }
        foldcade.music.onExternalLaunch()
        foldcade.store.place(panel, external)
        val options = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }
        startActivity(launch, options.toBundle())
    }

    private fun launchFocused() {
        val game = foldcade.shell.focusedGame() ?: return
        if (game.emptyShelfHint) return
        if (game.androidPackage != null) {
            launchInstalled(game)
            return
        }
        val index = Shelf.games.indexOfFirst { it.id == game.id }
        if (index >= 0) {
            launchGame(index)
            return
        }
        val apiGame = foldcade.shell.catalogGame(game.id)
        if (apiGame != null) {
            launchBackendGame(
                title = game.title,
                platformId = apiGame.platformId,
                libraryId = apiGame.backendId,
                game = apiGame,
            )
            return
        }
        launchStandIn(game)
    }

    private fun launchLibraryGame(index: Int) {
        val entry = foldcade.shell.entryAt(index) ?: return
        val game = entry.game ?: return
        val libraryId = foldcade.shell.activeLibraryId ?: return
        launchBackendGame(
            title = entry.title,
            platformId = entry.platformId,
            libraryId = libraryId,
            game = game,
        )
    }

    private fun launchBackendGame(
        title: String,
        platformId: String?,
        libraryId: String,
        game: Game,
    ) {
        // Play history and the per-game screen choice use the home id from every grid.
        val id = homeGameId(libraryId, game.remoteKey)
        // A server game downloads first. Its tile pulses until then, and a second press waits.
        val fetching = game.availability == Availability.RemoteOnly
        if (fetching && !foldcade.shell.beginDownload(libraryId, game.remoteKey)) return
        foldcade.music.onExternalLaunch()
        val generation = beginLaunch()
        foldcade.scope.launch {
            val players = foldcade.plugins.playersFor(game.platformId)
            val installedPackage = { player: Player ->
                player.packageNames.firstOrNull { name ->
                    runCatching { packageManager.getPackageInfo(name, 0) }.isSuccess
                }
            }
            // Say so before a download that could take minutes, not after it.
            val missing = missingPlayer(players, installedPackage)
            if (missing != null) {
                withContext(Dispatchers.Main.immediate) {
                    if (fetching) foldcade.shell.endDownload(libraryId, game.remoteKey)
                    if (launchCurrent(generation)) showMissing(missing.playerName, missing.alsoRuns)
                }
                return@launch
            }
            val target = try {
                foldcade.plugins.ensureLocal(libraryId, game).also {
                    withContext(Dispatchers.Main.immediate) { foldcade.shell.noteOnDevice(libraryId, game.remoteKey) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (fatal: VirtualMachineError) {
                throw fatal
            } catch (_: Exception) {
                withContext(Dispatchers.Main.immediate) {
                    if (launchCurrent(generation)) showDownloadFailed(title)
                }
                return@launch
            } finally {
                if (fetching) {
                    withContext(NonCancellable + Dispatchers.Main.immediate) {
                        foldcade.shell.endDownload(libraryId, game.remoteKey)
                    }
                }
            }
            val plan = planLaunch(players, target, installedPackage)
            val installedIntent = if (plan is GameLaunch.Installed) {
                try {
                    foldcade.plugins.launchIntent(
                        plan.player.id,
                        LaunchRequest(game, target, plan.packageName),
                    ).toStartIntent()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (fatal: VirtualMachineError) {
                    throw fatal
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
            withContext(Dispatchers.Main.immediate) {
                if (!launchCurrent(generation)) return@withContext
                when (plan) {
                    is GameLaunch.Dummy -> openContent(
                        id,
                        title,
                        platformId,
                        plan.uri,
                        occupiesBoth = false,
                    )
                    is GameLaunch.Installed -> if (installedIntent != null) {
                        startExternal(
                            id,
                            title,
                            game.platformId,
                            plan.player.occupiesBothDisplays,
                            installedIntent,
                        )
                    } else if (launchCurrent(generation)) {
                        showMissing(plan.player.displayName, emptyList())
                    }
                    is GameLaunch.Missing -> if (launchCurrent(generation)) {
                        showMissing(plan.playerName, plan.alsoRuns)
                    }
                    GameLaunch.NotAFile -> foldcade.retryLibrary()
                }
            }
        }
    }

    private fun showDownloadFailed(title: String) {
        foldcade.shell.showDialog(
            DialogState(
                kind = DialogKind.Ok,
                title = "${Copy.downloadFailed} $title",
                body = Copy.downloadFailedBody,
                buttons = listOf(DialogButton.Ok),
                index = 0,
                safeIndex = 0,
                screen = hostScreen(),
            ),
        )
    }

    private fun showMissing(playerName: String, alsoRuns: List<String>) {
        foldcade.shell.showDialog(
            DialogState(
                kind = DialogKind.Ok,
                title = "$playerName is not installed",
                body = Copy.missingPlayerBody,
                buttons = listOf(DialogButton.Ok),
                index = 0,
                safeIndex = 0,
                screen = hostScreen(),
                detail = alsoRunsLine(alsoRuns),
            ),
        )
    }

    private fun openContent(
        id: String,
        title: String,
        platformId: String?,
        uri: String,
        occupiesBoth: Boolean,
    ) {
        val intent = Intent(this, StandInActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(StandInActivity.EXTRA_ID, id)
            putExtra(StandInActivity.EXTRA_TITLE, title)
        }
        startExternal(id, title, platformId, occupiesBoth, intent)
    }

    private fun startExternal(
        id: String,
        title: String,
        platformId: String?,
        occupiesBoth: Boolean,
        intent: Intent,
    ) {
        val session = foldcade.store.session
        val external = ExternalApp(id, occupiesBoth)
        val assignment = displays.assignment(session.defaultDisplayIsTop)
        val displayId = if (occupiesBoth) {
            foldcade.store.update { it.launch(external, platformId) }
            assignment.topDisplayId
        } else {
            val panel = session.singleScreenTarget(id, platformId) ?: return
            foldcade.store.place(panel, external)
            when (panel) {
                Panel.Top -> assignment.topDisplayId
                Panel.Bottom -> assignment.bottomDisplayId ?: return
            }
        }
        foldcade.externalPlay.open(foldcade.plays, id, displayId) {
            startOnDisplay(intent, displayId)
        }
    }

    private fun launchStandIn(game: ShelfGame) {
        foldcade.music.onExternalLaunch()
        val session = foldcade.store.session
        val external = ExternalApp(game.id, game.occupiesBothDisplays)
        if (game.occupiesBothDisplays) {
            foldcade.store.update { it.launch(external, game.platformId) }
            beginPlay(game, displays.assignment(session.defaultDisplayIsTop).topDisplayId)
            return
        }
        val panel = session.singleScreenTarget(game.id, game.platformId) ?: return
        val assignment = displays.assignment(session.defaultDisplayIsTop)
        val displayId = when (panel) {
            Panel.Top -> assignment.topDisplayId
            Panel.Bottom -> assignment.bottomDisplayId ?: return
        }
        foldcade.store.place(panel, external)
        beginPlay(game, displayId)
    }

    /** Opens a play session, then starts the stand-in. That activity reports the rest of the session. */
    private fun beginPlay(game: ShelfGame, displayId: Int) {
        val playId = foldcade.plays.start(game.id)
        try {
            startOnDisplay(StandInActivity::class.java, displayId) {
                putExtra(StandInActivity.EXTRA_ID, game.id)
                putExtra(StandInActivity.EXTRA_TITLE, game.title)
                putExtra(StandInActivity.EXTRA_PLAY, playId)
            }
        } catch (failure: RuntimeException) {
            foldcade.plays.signal(playId, PlaySignal.End)
            throw failure
        }
    }

    private fun startPlayer(
        game: ShelfGame,
        ready: PlayerLaunch.Ready,
        apiGame: Game,
        player: Player,
        generation: Int,
    ) {
        foldcade.music.onExternalLaunch()
        val assignment = displays.assignment(foldcade.store.session.defaultDisplayIsTop)
        val panel = when (ready.startDisplay) {
            StartDisplay.Primary -> Panel.Top
            StartDisplay.PickerChoice ->
                foldcade.store.session.singleScreenTarget(game.id, game.platformId) ?: return
            else -> Panel.Top
        }
        val displayId = when (panel) {
            Panel.Top -> assignment.topDisplayId
            Panel.Bottom -> assignment.bottomDisplayId ?: return
        }
        val external = ExternalApp(game.id, ready.occupiesBothDisplays)
        if (ready.occupiesBothDisplays) {
            foldcade.store.update { it.launch(external, game.platformId) }
        } else {
            foldcade.store.place(panel, external)
        }
        try {
            foldcade.externalPlay.open(foldcade.plays, game.id, displayId) {
                val options = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }
                startActivity(ready.intent.toAndroidIntent(), options.toBundle())
            }
            if (game.libraryId != null) {
                libraryReturn = LibraryReturn(game.libraryId, apiGame, player)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ActivityNotFoundException) {
            foldcade.store.update { it.home(Panel.Top).home(Panel.Bottom) }
            val name = game.platformId
                ?.let { playerFor(it)?.displayName }
                ?: "Player"
            presentMissing(name, generation)
        } catch (_: Exception) {
            foldcade.store.update { it.home(Panel.Top).home(Panel.Bottom) }
        }
    }

    private fun hostScreen(): HostScreen {
        val panel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop)
        return if (panel == Panel.Top) HostScreen.Top else HostScreen.Bottom
    }

    private fun installedPackages(packageNames: List<String>): Set<String> =
        packageNames.filter(::packageInstalled).toSet()

    private fun packageInstalled(name: String): Boolean =
        try {
            packageManager.getPackageInfo(name, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    /** Several emulators can serve one platform. See [preferredPlayer]. */
    private fun playerFor(platformId: String): Player? =
        preferredPlayer(foldcade.plugins.playersFor(platformId), ::packageInstalled)

    private fun reconcileUsage() {
        val now = System.currentTimeMillis()
        val access = UsageAccess(this)
        val granted = access.granted()
        val samples = if (granted) {
            access.samples(now - UsageAccess.LOOKBACK_MS, now)
        } else {
            emptyList()
        }
        foldcade.plays.applyUsage(granted, samples, now)
        foldcade.shell.setUsageGranted(granted)
        foldcade.shell.maybeOfferUsageAccess(foldcade.plays.hasTrackedSession(), granted, hostScreen())
    }

    private fun clearPendingLaunch() {
        pendingLaunch = null
        closeConfirmed = false
    }

    private fun beginLaunch(): Int {
        launchGeneration += 1
        return launchGeneration
    }

    /** Drops an in-flight launch so a late result cannot open a missing-player dialog. */
    private fun cancelLaunches() {
        launchGeneration += 1
        libraryLaunching = false
        clearPendingLaunch()
    }

    private fun launchCurrent(generation: Int): Boolean = generation == launchGeneration

    private fun presentMissing(name: String, generation: Int) {
        if (!launchCurrent(generation)) return
        foldcade.shell.present(missingPlayerDialog(name, hostScreen()))
    }

    private fun cycleLaunchTarget() {
        val game = foldcade.shell.focusedGame() ?: return
        val onPlatform = foldcade.shell.model.libraryGrid &&
            foldcade.shell.model.gridKind == GridKind.Platforms
        foldcade.store.update { it.cycleStoredScreen(game.id, game.platformId, onPlatform) }
    }

    private fun onDialog(effect: Effect.DialogChoice) {
        when (effect.kind) {
            DialogKind.UsageAccess -> {
                foldcade.store.setUsagePromptOffered()
                if (effect.button == DialogButton.Allow) {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
            DialogKind.Home -> {
                foldcade.store.setHomePromptSettled()
                if (effect.button == DialogButton.UseAsHome) requestHome()
            }
            DialogKind.Folder -> if (effect.button == DialogButton.ContinueGrant) {
                foldcade.shell.noteFolderExplained()
                folderPurpose = FolderPurpose.Library
                folderPicker.launch(null)
            }
            DialogKind.Ok -> cancelLaunches()
            DialogKind.ReLogin -> foldcade.shell.openConnect(foldcade.store.rommOrigin().orEmpty())
            DialogKind.MissingPlayer -> cancelLaunches()
            DialogKind.ClosePlayer -> if (effect.button == DialogButton.CloseIt) {
                closeConfirmed = true
                pendingLaunch?.let { launchGame(it) }
            } else {
                cancelLaunches()
            }
            DialogKind.SaveFolder -> if (effect.button == DialogButton.ContinueGrant) {
                folderPurpose = FolderPurpose.LaunchOffer
                folderPicker.launch(null)
            } else {
                skipSaveFolderAndLaunch()
            }
        }
        foldcade.shell.showQueuedPrompt()
    }

    private fun saveRommToken() {
        val origin = normalizeSetupOrigin(foldcade.shell.model.connectOrigin)
        if (origin == null || foldcade.shell.connectToken.isBlank()) return
        val token = foldcade.shell.connectToken.trim()
        if (token.isEmpty()) return
        foldcade.scope.launch {
            val result = try {
                RommClient(
                    origin = origin,
                    accessToken = { token },
                    connectTimeout = Duration.ofSeconds(4),
                    readTimeout = Duration.ofSeconds(4),
                ).use { it.confirmSignIn() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                RommSignInResult.StayOnForm(null)
            }
            if (result is RommSignInResult.Accepted) {
                foldcade.editCredentials {
                    val saved = try {
                        foldcade.credentials.put(
                            RommCredentials.PLUGIN_ID,
                            RommCredentials.ACCESS_TOKEN,
                            Credential.ApiToken(token),
                        )
                        true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        false
                    }
                    if (saved) {
                        foldcade.store.setRommOrigin(origin)
                        foldcade.publishRomm()
                    }
                    withContext(Dispatchers.Main.immediate) {
                        foldcade.shell.showSetupHint(result.hint)
                        if (saved) {
                            foldcade.shell.setSignedIn(listOf(SignedInBackend(RommCredentials.PLUGIN_ID, "RomM")))
                            if (foldcade.shell.connectToken.trim() == token) foldcade.shell.consumeConnectToken()
                            // The form used to stay up with an empty token field, so a save
                            // that worked looked like one that did nothing.
                            foldcade.shell.closeConnect()
                            foldcade.syncRomm()
                        } else {
                            foldcade.shell.askToSignInAgain()
                        }
                    }
                }
            } else {
                withContext(Dispatchers.Main.immediate) {
                    foldcade.shell.showSetupHint(result.hint)
                }
            }
        }
    }

    private fun forget(pluginId: String) {
        foldcade.scope.launch {
            foldcade.editCredentials {
                val forgotten = try {
                    foldcade.credentials.forgetPlugin(pluginId)
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                if (forgotten) {
                    if (pluginId == RommCredentials.PLUGIN_ID) foldcade.store.clearRommOrigin()
                    foldcade.publishRomm()
                    foldcade.syncRomm()
                }
                withContext(Dispatchers.Main.immediate) {
                    if (forgotten) {
                        foldcade.shell.setSignedIn(
                            foldcade.shell.model.signedIn.filter { it.pluginId != pluginId },
                        )
                    } else {
                        foldcade.shell.askToSignInAgain()
                    }
                }
            }
        }
    }

    private fun choosePlayerSave(playerId: String) {
        clearPendingLaunch()
        settingsPlayerId = playerId
        folderPurpose = FolderPurpose.Settings
        folderPicker.launch(null)
    }

    private fun skipSaveFolderAndLaunch() {
        val index = pendingLaunch ?: return
        val player = playerForPendingLaunch() ?: return
        foldcade.store.setSaveFolderPromptSkipped(player.id)
        launchGame(index)
    }

    private fun playerForPendingLaunch(): Player? {
        val index = pendingLaunch ?: return null
        val game = Shelf.games.getOrNull(index) ?: return null
        return game.platformId?.let(::playerFor)
    }

    private fun rememberSaveFolder(uri: Uri, player: Player?) {
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, read or write)
        } catch (_: SecurityException) {
            contentResolver.takePersistableUriPermission(uri, read)
        }
        if (player is SaveFolderHolder) {
            player.bindSaveFolder(uri.toString())
            foldcade.store.setPlayerSaveFolder(player.id, uri.toString())
            foldcade.shell.refreshPlayerSaves()
        }
    }

    private fun requestHome() {
        val roleManager = getSystemService(RoleManager::class.java)
        foldcade.shell.setHomeRoleHeld(roleManager.isRoleHeld(RoleManager.ROLE_HOME))
        if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            homeRole.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }
    }

    private fun reconcile(pending: LibraryReturn) {
        foldcade.scope.launch {
            try {
                foldcade.plugins.reconcile(
                    pending.libraryId,
                    pending.game,
                    pending.player,
                    ObservedSaves(emptyList()),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (fatal: VirtualMachineError) {
                throw fatal
            } catch (_: Exception) {
                Unit
            }
        }
    }
}

private enum class FolderPurpose {
    Library,
    LaunchOffer,
    Settings,
}

private fun shelfGame(game: ShelfGame): Game = Game(
    backendId = game.libraryId ?: "shelf",
    remoteKey = game.remoteKey ?: game.contentUri ?: game.id,
    platformId = game.platformId.orEmpty(),
    availability = if (game.libraryId != null || game.contentUri != null) {
        Availability.LocalOnly
    } else {
        Availability.RemoteOnly
    },
    label = game.title,
)

internal const val EXTRA_ANDROID_SHELF = "app.foldcade.extra.SHELF"
internal const val EXTRA_FOLDER_LIBRARY = "app.foldcade.extra.FOLDER_LIBRARY"

private data class LibraryReturn(
    val libraryId: String,
    val game: Game,
    val player: Player,
)

class PrimaryHomeActivity : FoldcadeHomeActivity() {
    override val launchesCompanion: Boolean = true
}

class CompanionHomeActivity : FoldcadeHomeActivity() {
    override val launchesCompanion: Boolean = false
}

internal fun inputDeviceKey(activity: Activity): String {
    val descriptors = InputDevice.getDeviceIds().asIterable().mapNotNull { id ->
        val device = InputDevice.getDevice(id) ?: return@mapNotNull null
        val pad = device.sources and (
            InputDevice.SOURCE_GAMEPAD or
                InputDevice.SOURCE_JOYSTICK or
                InputDevice.SOURCE_DPAD
            )
        if (pad == 0) return@mapNotNull null
        device.descriptor.takeIf { it.isNotBlank() }
    }.sorted()
    return if (descriptors.isEmpty()) "builtin" else descriptors.joinToString(",")
}

private fun readSystemOrGlobal(resolver: android.content.ContentResolver, key: String): String? =
    try {
        Settings.System.getString(resolver, key)?.takeIf { it.isNotBlank() }
            ?: Settings.Global.getString(resolver, key)
    } catch (_: SecurityException) {
        null
    }

internal fun Activity.hideSystemBars() {
    window.insetsController?.apply {
        hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
