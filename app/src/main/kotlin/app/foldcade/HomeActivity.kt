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
import android.util.Log
import android.provider.Settings
import android.view.InputDevice
import android.os.Bundle
import android.view.Display
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.focus.FocusRequester
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import app.foldcade.host.play.PlaySignal
import app.foldcade.api.isAndroidHomeRecall
import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.DeviceCodePoll
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveConflict
import app.foldcade.api.plugin.SaveConflictChoice
import app.foldcade.api.plugin.SaveCopy
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.StartDisplay
import app.foldcade.artwork.SteamGridDb
import app.foldcade.language.SaveConflictAction
import app.foldcade.language.SaveConflictPrompt
import app.foldcade.language.SaveConflictSide
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
import app.foldcade.language.alsoRunsLine
import app.foldcade.plugins.romm.ROMM_ORIGIN_KEY
import app.foldcade.plugins.romm.isRommInstance
import app.foldcade.plugins.romm.rommOriginAlreadyConfigured
import app.foldcade.romm.RommClient
import app.foldcade.romm.RommSignInResult
import app.foldcade.romm.normalizeSetupOrigin
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import app.foldcade.ui.PanelHost

abstract class FoldcadeHomeActivity : PanelKeyActivity() {
    protected abstract val launchesCompanion: Boolean

    private companion object {
        const val EXTRA_DIALOG = "foldcade.dialog"
        const val EXTRA_DIALOG_INDEX = "foldcade.dialogIndex"
        const val EXTRA_DIALOG_SCREEN = "foldcade.dialogScreen"
        const val EXTRA_SAVE_CONFLICT = "foldcade.saveConflict"
    }

    private val displays by lazy { Displays(this) }
    private val foldcade by lazy { application as FoldcadeApp }
    private var resumed = false
    private var recoveryWindow: PanelRecoveryWindow? = null
    internal val panelFocus = FocusRequester()

    /** True while this panel is resumed and its display is fully on. */
    var shellVisible by mutableStateOf(false)
        private set

    /** Windows the panel has been attached to. [PanelRecoveryWindow] moves it at each wake and return. */
    var panelWindows by mutableIntStateOf(0)
        private set

    private val backdropDisplayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refreshShellVisible()
        override fun onDisplayRemoved(displayId: Int) = refreshShellVisible()
        override fun onDisplayChanged(displayId: Int) = refreshShellVisible()
    }

    private var folderPurpose = FolderPurpose.Library
    private var pendingLaunch: Int? = null
    private var closeConfirmed = false
    private var settingsPlayerId: String? = null
    private var libraryReturn: LibraryReturn? = null
    private var deviceCodeJob: Job? = null

    /** A second server minted for device-code before the token is saved. Not in the session until then. */
    private var pendingRommId: String? = null

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

    override fun rowsTakeVerticalKeys(): Boolean = foldcade.shell.model.connectOpen

    override fun onMeaning(meaning: app.foldcade.language.Meaning) = onMeaningRepeated(meaning, 0)

    override fun onMeaningRepeated(meaning: app.foldcade.language.Meaning, repeatCount: Int) {
        if (commandCenterKey(meaning)) return
        if (app.foldcade.language.opensTopMenu(meaning) && !foldcade.store.session.islandsOpen()) return
        if (app.foldcade.language.cancelsPendingLaunch(
                meaning,
                dialogOpen = foldcade.shell.model.dialog != null,
                conflictOpen = foldcade.shell.model.saveConflict != null,
            )
        ) {
            cancelLaunches()
        }
        foldcade.music.duckForThemeSound(meaning)
        val panel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop) ?: return
        val screen = if (panel == Panel.Top) HostScreen.Top else HostScreen.Bottom
        dispatch(foldcade.shell.onMeaning(meaning, screen, repeatCount))
    }

    /** The command center on this screen, or the grid opened from it, takes the key first. */
    private fun commandCenterKey(meaning: app.foldcade.language.Meaning): Boolean {
        val panel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop) ?: return false
        return foldcade.commandCenter.onKey(panel, meaning, foldcade.shell::atRest)
    }

    fun dispatch(effect: Effect?) {
        when (effect) {
            is Effect.Launch -> {
                launchScreen = if (effect.onBottom) Panel.Bottom else Panel.Top
                launchSubject = foldcade.shell.focusedRunningApp()
                if (foldcade.shell.model.libraryGrid) launchLibraryGame(effect.index) else launchFocused()
            }
            is Effect.OpenPlatform -> foldcade.openPlatform(effect.index)
            Effect.LeavePlatform -> foldcade.leavePlatform()
            Effect.TryAgain -> foldcade.retryLibrary()
            Effect.AddFolder -> {
                folderPurpose = FolderPurpose.Library
                folderPicker.launch(null)
            }
            Effect.SyncLibrary -> foldcade.libraryResync.syncNow()
            is Effect.ChoosePlayerSave -> choosePlayerSave(effect.playerId)
            Effect.PinApp, is Effect.AddAppTo, Effect.MoveApp, Effect.HideApp, Effect.ShowApp -> Unit
            // The shell applies Y's tile actions and A on a text field itself and hands back null; these never arrive.
            Effect.ChooseArt, Effect.MoveTile, is Effect.MoveTileTo, Effect.NewFolder, Effect.RenameFolder,
            Effect.ChooseFolderIcon, Effect.RemoveTile, Effect.AddToHome, Effect.GoHome, Effect.EditField,
            -> Unit
            Effect.RemoveDownload -> removeDownload()
            Effect.OpenAll -> foldcade.shell.openHomeAll()
            Effect.ToggleAddNew -> foldcade.shell.applyHomeAddNew(foldcade.shell.model.addNewToHome)
            is Effect.EditRommConnection -> {
                if (foldcade.shell.editRommConnection(effect.pluginId)) beginRommDeviceCode()
            }
            Effect.OpenAddRomm -> {
                foldcade.shell.openAddRomm()
                beginRommDeviceCode()
            }
            Effect.SaveRommToken -> saveRommToken()
            Effect.RetryRommDeviceCode -> beginRommDeviceCode()
            Effect.OpenArtKey -> foldcade.shell.openArtKey()
            Effect.SaveArtKey -> saveArtKey()
            Effect.ForgetArtKey -> forgetArtKey()
            is Effect.ForgetCredentials -> forget(effect.pluginId)
            is Effect.ForgetFolder -> foldcade.forgetFolder(effect.uri)
            is Effect.DialogChoice -> onDialog(effect)
            Effect.RequestHome -> requestHome()
            is Effect.OpenAndroidSetting -> openAndroidSetting(effect.setting)
            Effect.OpenLicenses -> startActivity(Intent(this, LicensesActivity::class.java))
            Effect.DismissButtonLabels -> Unit
            is Effect.OpenNotice -> openNotice(effect.id, if (effect.onBottom) Panel.Bottom else Panel.Top)
            is Effect.DismissNotice -> foldcade.dismissNotice(effect.id)
            Effect.ClearNotices -> foldcade.clearNotices()
            is Effect.ResolveSaveConflict -> resolveSaveConflict(effect)
            null -> Unit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        foldcade.homeCreated(this)
        // Secondary Home may be created behind a running emulator. Resume reconciles its panel.
        if (launchesCompanion) acceptHome(intent)
        acceptShelf(intent)
        acceptFolderLibrary(intent)
        val roleManager = getSystemService(RoleManager::class.java)
        val held = roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        foldcade.shell.setHomeRoleHeld(held)
        foldcade.shell.maybeAskHome(held)
        foldcade.onSaveConflicts = { refreshSaveConflict() }
        applyDialogPreview(intent)
        applySaveConflictPreview(intent)
        applyIslandPreview(intent)
        if (launchesCompanion) {
            val manager = getSystemService(DisplayManager::class.java)
            manager.registerDisplayListener(displayListener, null)
            launchCompanionIfNeeded()
        }
        getSystemService(DisplayManager::class.java).registerDisplayListener(backdropDisplayListener, null)
        getSystemService(InputManager::class.java).registerInputDeviceListener(inputDevices, null)
        refreshFacePrompt()
        val root = FrameLayout(this)
        val panel = ComposeView(this).apply {
            id = android.R.id.content
            // Both containers belong to this activity; moving windows must preserve panel state.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycle))
            setContent { PanelHost(activity = this@FoldcadeHomeActivity, displays = displays) }
            addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(view: View) {
                        panelWindows++
                    }

                    override fun onViewDetachedFromWindow(view: View) = Unit
                },
            )
        }
        root.addView(panel, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        setContentView(root)
        recoveryWindow = PanelRecoveryWindow(this, root, panel)
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
        val returned = ending && foldcade.externalPlay.sessionId == null
        if (returned) foldcade.suppressSaveConflict = false
        if (returned && pending != null) {
            libraryReturn = null
            reconcile(pending)
            foldcade.scope.launch {
                try {
                    foldcade.plugins.reopenDeferredSaveConflicts()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Unit
                }
                withContext(Dispatchers.Main.immediate) { refreshSaveConflict() }
            }
        } else {
            resumeLostReturn()
            if (!foldcade.suppressSaveConflict) refreshSaveConflict()
        }
        // Foldcade in front on this panel means the game there has left. A launcher-icon
        // start is not a Home recall, so without this a Foldcade that is not the default
        // Home keeps the panel for the game and ignores every key.
        var reclaimedPanel = false
        displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop)?.let { panel ->
            reclaimedPanel = foldcade.store.session.surfaceOn(panel) == null
            foldcade.store.update { it.foldcadeResumed(panel) }
        }
        resumed = true
        // Primary Home coming forward reclaims the launcher, including a fresh icon start.
        // A secondary Home can resume behind an emulator and cannot arm recovery by itself.
        if (launchesCompanion) foldcade.requestPanelRecovery(reclaim = reclaimedPanel)
        refreshShellVisible()
        if (launchesCompanion) launchCompanionIfNeeded()
        foldcade.reloadInstalledApps()
        refreshFacePrompt()
        displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop)?.let(foldcade.music::onHomeResume)
        foldcade.refreshMoonlightShelf()
        foldcade.refreshDroidDeckShelf()
        foldcade.refreshFolderQuietly()
        foldcade.refreshBrightness()
        foldcade.brightness.refreshOther()
        foldcade.refreshNoticeAccess()
        reconcileUsage()
    }

    override fun onStop() {
        super.onStop()
        noteCovered()
    }

    /**
     * Another app now covers this screen while Foldcade thought it was showing.
     * It might come from Recents, another launcher, or a notification. The session
     * marks this screen taken, so the other screen shows the grid instead of a hero
     * for a grid nobody can see. Foldcade's own launches mark their screen first,
     * and coming back to the front clears it ([Session.foldcadeResumed]).
     */
    private fun noteCovered() {
        if (isChangingConfigurations || isFinishing) return
        // Sleep stops the activity too. That is not another app.
        if (display?.state != Display.STATE_ON) return
        val panel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop) ?: return
        if (foldcade.store.session.surfaceOn(panel) == null) return
        foldcade.store.place(panel, ExternalApp(OTHER_APP_ID))
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

    /** True while this screen is Android's focused display. Each screen's window has focus of its own. */
    private var topResumed by mutableStateOf(false)

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        topResumed = isTopResumedActivity
    }

    /**
     * Android raises the keyboard only on the focused display. The keys usually come from
     * the other screen, so this one starts itself again there, which moves focus to it.
     * Android pauses and resumes it around the new intent; resuming only refreshes state.
     * This returns once it has focus.
     */
    suspend fun takeKeyboardFocus() {
        val displayId = display?.displayId ?: return
        if (!topResumed) {
            val options = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }
            startActivity(Intent(this, this::class.java), options.toBundle())
        }
        withTimeoutOrNull(KEYBOARD_FOCUS_WAIT_MS) { snapshotFlow { topResumed }.first { it } }
        recoveryWindow?.takeKeyboardFocus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptHome(intent)
        acceptShelf(intent)
        acceptFolderLibrary(intent)
        applyDialogPreview(intent)
        applySaveConflictPreview(intent)
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

    private fun applySaveConflictPreview(intent: Intent) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        if (intent.getStringExtra(EXTRA_SAVE_CONFLICT) == null) return
        foldcade.shell.showSaveConflictPreview()
    }

    override fun onDestroy() {
        recoveryWindow?.close()
        foldcade.homeDestroyed(this)
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

    /**
     * The command center's dim, as this window's own brightness. On the Thor that
     * moves this panel's backlight and no other. Not dimmed hands the panel back
     * to its own level, with nothing to remember or restore.
     */
    fun applyCommandCenterDim(dimmed: Boolean) {
        val level = if (dimmed) commandCenterDimLevel() else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        val attributes = window.attributes
        if (attributes.screenBrightness == level) return
        attributes.screenBrightness = level
        window.attributes = attributes
        recoveryWindow?.brightness(level)
    }

    /** A screen the user keeps darker than the dim stays at its own level. */
    private fun commandCenterDimLevel(): Float {
        val dim = CommandCenter.DIM_BRIGHTNESS
        val id = display?.displayId ?: return dim
        return minOf(dim, foldcade.brightness.fraction(id) ?: return dim)
    }

    private fun refreshShellVisible() {
        val state = display?.state ?: Display.STATE_ON
        shellVisible = resumed && state == Display.STATE_ON
        foldcade.noteHomeShown()
        refreshRecoveryWindow()
    }

    internal fun refreshRecoveryWindow(reclaim: Boolean = false) {
        val panel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop)
        val ownsPanel = panel != null && canRecoverPanel(foldcade.store.session, panel)
        recoveryWindow?.update(
            wanted = foldcade.panelRecoveryRequested && shellVisible && ownsPanel && !isFinishing && !isDestroyed &&
                display?.displayId != Display.DEFAULT_DISPLAY,
            reclaim = reclaim,
        )
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
        val surface = foldcade.store.session.surfaceOn(panel)
        if (surface == Surface.Picker || surface == Surface.Hero) foldcade.shell.goHome()
        foldcade.store.update { it.home(panel) }
        if (foldcade.panelRecoveryRequested) foldcade.requestPanelRecovery()
    }

    /**
     * Starts the other screen's Foldcade when it does not exist: at first launch, and
     * again if Android destroyed it. One that exists behind another app is left there;
     * [noteCovered] hands the grid to this screen instead.
     */
    private fun launchCompanionIfNeeded() {
        val assignment = displays.assignment(foldcade.store.session.defaultDisplayIsTop)
        val other = assignment.allIds().firstOrNull { it != Display.DEFAULT_DISPLAY } ?: return
        if (foldcade.companionAlive(other) || foldcade.companionStarting) return
        foldcade.companionStarting = true
        val intent = Intent(this, CompanionHomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
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
            if (foldcade.launchWork.active) return
            val generation = beginLaunch()
            pendingLaunch = index
            val apiGame = shelfGame(game)
            foldcade.launchWork.job = foldcade.scope.launch {
                var notPlaced = false
                val prepared = try {
                    val local = foldcade.plugins.ensureLocal(libraryId, apiGame)
                    withContext(Dispatchers.Main.immediate) { foldcade.shell.noteOnDevice(libraryId, remoteKey) }
                    prepareSaves(libraryId, apiGame, player, local)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (fatal: VirtualMachineError) {
                    throw fatal
                } catch (_: SaveNotPlaced) {
                    notPlaced = true
                    null
                } catch (_: Exception) {
                    null
                }
                withContext(Dispatchers.Main.immediate) {
                    if (!launchCurrent(generation) || pendingLaunch != index) return@withContext
                    if (notPlaced) showSaveNotPlaced(game.title)
                    if (prepared == null) return@withContext
                    finishLaunch(index, game, player, prepared.target, apiGame, generation, prepared.spots)
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
        spots: List<SaveSpot> = emptyList(),
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
                startPlayer(game, decision, apiGame, player, generation, spots)
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
        val panel = session.singleScreenTarget(launchScreen) ?: return
        val assignment = displays.assignment(session.defaultDisplayIsTop)
        val displayId = when (panel) {
            Panel.Top -> assignment.topDisplayId
            Panel.Bottom -> assignment.bottomDisplayId ?: return
        }
        foldcade.music.onExternalLaunch()
        foldcade.commandCenter.note(external.id, foldcade.shell.runningApp(game))
        foldcade.store.place(panel, external)
        val options = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }
        startActivity(launch, options.toBundle())
    }

    /**
     * The screen the launch now starting asked for: top for A, bottom for X. A
     * newer launch replaces it, and an older one still running is dropped by its
     * generation check before it places anything.
     */
    private var launchScreen: Panel = Panel.Top

    /** The tile behind the launch now starting, for the command center beside it. */
    private var launchSubject: RunningApp? = null

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
        if (game.id.startsWith("lib:")) {
            foldcade.shell.present(DialogState(
                kind = DialogKind.Ok,
                title = "Game unavailable",
                body = "This game's library details are not available yet. Reconnect the library and try again.",
                buttons = listOf(DialogButton.Ok),
                index = 0,
                safeIndex = 0,
                screen = hostScreen(),
            ))
            foldcade.syncRomm()
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
        // Its download is being deleted. A press once that is done downloads it again.
        if (id in foldcade.shell.removing) return
        // A server game downloads first. Its tile pulses until then, and a second press waits.
        val fetching = game.availability == Availability.RemoteOnly
        if (fetching && !foldcade.shell.beginDownload(libraryId, game.remoteKey)) return
        val generation = beginLaunch()
        foldcade.suppressSaveConflict = true
        foldcade.launchWork.job = foldcade.scope.launch {
            try {
                runBackendLaunch(title, platformId, libraryId, game, id, fetching, generation)
            } finally {
                // Opened or not, a half-told download or sync is stale now. A sync's mark stays.
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    foldcade.shell.settleActivity(libraryId, game.remoteKey)
                    // A game that started shows the conflict when Foldcade is in front again.
                    if (foldcade.externalPlay.sessionId == null && libraryReturn == null) {
                        foldcade.suppressSaveConflict = false
                        refreshSaveConflict()
                    }
                }
            }
        }
    }

    /** Download, saves, then the player, for [launchBackendGame]. Each step that shows anything checks [generation]. */
    private suspend fun runBackendLaunch(
        title: String,
        platformId: String?,
        libraryId: String,
        game: Game,
        id: String,
        fetching: Boolean,
        generation: Int,
    ) {
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
            return
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
            return
        } finally {
            if (fetching) {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    foldcade.shell.endDownload(libraryId, game.remoteKey)
                }
            }
        }
        val plan = planLaunch(players, target, installedPackage)
        // Saves go into the player's folder before it starts, and come back when it returns.
        val spots = if (plan is GameLaunch.Installed) {
            try {
                prepareSaves(libraryId, game, plan.player, target).spots
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (fatal: VirtualMachineError) {
                throw fatal
            } catch (_: SaveNotPlaced) {
                withContext(Dispatchers.Main.immediate) {
                    if (launchCurrent(generation)) showSaveNotPlaced(title)
                }
                return
            } catch (_: Exception) {
                // The player keeps its own save. The next launch hands any change to the library.
                emptyList()
            }
        } else {
            emptyList()
        }
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
                    val started = startExternal(
                        id,
                        title,
                        game.platformId,
                        plan.player.occupiesBothDisplays,
                        installedIntent,
                    )
                    if (started) rememberReturn(LibraryReturn(libraryId, game, plan.player, spots))
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

    /**
     * Y's Delete download. Not while a launch is under way or the game holds
     * either screen, since either may be reading that copy. The game does not
     * launch until the copy is gone.
     */
    private fun removeDownload() {
        if (foldcade.launchWork.active) return
        val game = foldcade.shell.focusedDownload() ?: return
        val libraryId = game.backendId
        if (foldcade.store.session.runs(homeGameId(libraryId, game.remoteKey))) return
        if (!foldcade.shell.beginRemove(libraryId, game.remoteKey)) return
        foldcade.scope.launch {
            var removed = false
            try {
                foldcade.plugins.removeLocal(libraryId, game)
                removed = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (fatal: VirtualMachineError) {
                throw fatal
            } catch (_: Exception) {
                Unit
            } finally {
                // In one step, so a press in between never launches a copy that is gone.
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    if (removed) foldcade.shell.noteOffDevice(libraryId, game.remoteKey)
                    foldcade.shell.endRemove(libraryId, game.remoteKey)
                }
            }
            if (!removed) withContext(Dispatchers.Main.immediate) { showDeleteFailed(game.label) }
        }
    }

    private fun showDeleteFailed(title: String) {
        foldcade.shell.showDialog(
            DialogState(
                kind = DialogKind.Ok,
                title = "${Copy.deleteFailed} $title",
                body = Copy.deleteFailedBody,
                buttons = listOf(DialogButton.Ok),
                index = 0,
                safeIndex = 0,
                screen = hostScreen(),
            ),
        )
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

    private fun showSaveNotPlaced(title: String) {
        foldcade.shell.showDialog(
            DialogState(
                kind = DialogKind.Ok,
                title = "${Copy.saveNotPlaced} $title",
                body = Copy.saveNotPlacedBody,
                buttons = listOf(DialogButton.Ok),
                index = 0,
                safeIndex = 0,
                screen = hostScreen(),
            ),
        )
    }

    /**
     * Hands the library any save the player changed outside Foldcade, lets the
     * library sync, then writes its saves into the player's folder.
     * Throws [SaveNotPlaced] when the player has a save folder and any step
     * fails: the game must not start from a save the library does not know.
     * One save flow runs at a time, so a second launch or a return read-back
     * waits rather than racing this one.
     */
    private suspend fun prepareSaves(
        libraryId: String,
        game: Game,
        player: Player,
        local: LaunchTarget,
    ): PreparedLaunch = foldcade.saveFlow.withLock {
        val saves = foldcade.playerSaves
        val spots = saves.spots(player, game, local)
        try {
            val outside = saves.changedSince(libraryId, game, player, spots)
            if (outside.slots.isNotEmpty()) {
                foldcade.plugins.reconcile(libraryId, game, player, outside)
                saves.noteHandedOver(libraryId, game, player, spots, outside)
            }
            val placement = foldcade.plugins.prepareLaunch(libraryId, game, player)
            if (!saves.place(libraryId, game, player, spots, placement.savesToPlace)) throw SaveNotPlaced()
            PreparedLaunch(placement.target, spots)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // With a save folder in play, any failure here would start the game from a save the library does not know.
            if (spots.isNotEmpty() && failure !is SaveNotPlaced) throw SaveNotPlaced() else throw failure
        }
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

    /** True when the app was asked to start. */
    private fun startExternal(
        id: String,
        title: String,
        platformId: String?,
        occupiesBoth: Boolean,
        intent: Intent,
    ): Boolean {
        val session = foldcade.store.session
        val external = ExternalApp(id, occupiesBoth)
        foldcade.commandCenter.note(id, launchSubject?.takeIf { it.title == title } ?: RunningApp(title))
        val assignment = displays.assignment(session.defaultDisplayIsTop)
        val displayId = if (occupiesBoth) {
            foldcade.store.update { it.launch(external, launchScreen) }
            assignment.topDisplayId
        } else {
            val panel = session.singleScreenTarget(launchScreen) ?: return false
            foldcade.store.place(panel, external)
            when (panel) {
                Panel.Top -> assignment.topDisplayId
                Panel.Bottom -> assignment.bottomDisplayId ?: return false
            }
        }
        foldcade.externalPlay.open(foldcade.plays, id, displayId) {
            startOnDisplay(intent, displayId)
        }
        return true
    }

    private fun launchStandIn(game: ShelfGame) {
        foldcade.music.onExternalLaunch()
        val session = foldcade.store.session
        val external = ExternalApp(game.id, game.occupiesBothDisplays)
        foldcade.commandCenter.note(game.id, foldcade.shell.runningApp(game))
        if (game.occupiesBothDisplays) {
            foldcade.store.update { it.launch(external, launchScreen) }
            beginPlay(game, displays.assignment(session.defaultDisplayIsTop).topDisplayId)
            return
        }
        val panel = session.singleScreenTarget(launchScreen) ?: return
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
        spots: List<SaveSpot>,
    ) {
        foldcade.music.onExternalLaunch()
        val assignment = displays.assignment(foldcade.store.session.defaultDisplayIsTop)
        val panel = when (ready.startDisplay) {
            StartDisplay.Primary -> Panel.Top
            StartDisplay.PickerChoice ->
                foldcade.store.session.singleScreenTarget(launchScreen) ?: return
            else -> Panel.Top
        }
        val displayId = when (panel) {
            Panel.Top -> assignment.topDisplayId
            Panel.Bottom -> assignment.bottomDisplayId ?: return
        }
        val external = ExternalApp(game.id, ready.occupiesBothDisplays)
        foldcade.commandCenter.note(game.id, foldcade.shell.runningApp(game))
        if (ready.occupiesBothDisplays) {
            foldcade.store.update { it.launch(external, launchScreen) }
        } else {
            foldcade.store.place(panel, external)
        }
        try {
            foldcade.externalPlay.open(foldcade.plays, game.id, displayId) {
                val options = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }
                startActivity(ready.intent.toAndroidIntent(), options.toBundle())
            }
            if (game.libraryId != null) {
                rememberReturn(LibraryReturn(game.libraryId, apiGame, player, spots))
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

    /**
     * Opens a notice's app on [requested], or the other screen when a game holds it,
     * the way an app from the grid opens. A notice that only runs a service or a
     * broadcast leaves both screens to Foldcade.
     */
    private fun openNotice(key: String, requested: Panel) {
        val listener = NoticeListener.connected ?: return
        val session = foldcade.store.session
        val panel = session.singleScreenTarget(requested) ?: return
        val assignment = displays.assignment(session.defaultDisplayIsTop)
        val displayId = when (panel) {
            Panel.Top -> assignment.topDisplayId
            Panel.Bottom -> assignment.bottomDisplayId ?: return
        }
        val pkg = foldcade.shell.model.notices.firstOrNull { it.id == key }?.packageName
        listener.open(key, displayId) {
            foldcade.music.onExternalLaunch()
            val id = "notice:${pkg ?: key}"
            foldcade.commandCenter.note(id, pkg?.let { RunningApp(title = "", packageName = it) })
            foldcade.store.place(panel, ExternalApp(id))
        }
    }

    /** The first of [intents] this device can open. */
    private fun startFirst(intents: List<Intent>) {
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                continue
            }
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

    private fun beginLaunch(): Int = foldcade.launchWork.begin()

    /** Drops an in-flight launch so a late result cannot open a missing-player dialog. */
    private fun cancelLaunches() {
        foldcade.launchWork.cancel()
        clearPendingLaunch()
    }

    private fun launchCurrent(generation: Int): Boolean = foldcade.launchWork.current(generation)

    private fun presentMissing(name: String, generation: Int) {
        if (!launchCurrent(generation)) return
        foldcade.shell.present(missingPlayerDialog(name, hostScreen()))
    }

    private fun onDialog(effect: Effect.DialogChoice) {
        when (effect.kind) {
            DialogKind.Brightness -> if (effect.button == DialogButton.Allow) {
                startActivity(foldcade.brightness.accessIntent())
            }
            DialogKind.NoticeAccess -> if (effect.button == DialogButton.Allow) {
                startFirst(NoticeListener.accessIntents(this))
            }
            DialogKind.Notice -> Unit
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
            DialogKind.ReLogin -> {
                foldcade.shell.openRommRecovery()
                beginRommDeviceCode()
            }
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
        val origin = normalizeSetupOrigin(foldcade.shell.model.connectOrigin) ?: return
        val token = foldcade.shell.connectToken.trim()
        if (token.isEmpty()) return
        deviceCodeJob?.cancel()
        if (foldcade.shell.model.connectAnother) saveAnotherRomm(origin, token) else storeRommToken(origin, token)
    }

    private fun storeRommToken(origin: String, token: String) = validateRommToken(origin, token, another = false)

    private fun saveAnotherRomm(origin: String, token: String) = validateRommToken(origin, token, another = true)

    /** Check once, then store only if this is still the form that submitted the token. */
    private fun validateRommToken(origin: String, token: String, another: Boolean) {
        val instanceId = foldcade.shell.connectRommId
        val taken = foldcade.store.rommServers()
            .filter { another || it.first != instanceId }.map { it.second }
        if (rommOriginAlreadyConfigured(taken, origin)) {
            foldcade.shell.showSetupHint(Copy.rommSameServer)
            return
        }
        val ticket = foldcade.shell.beginSetupValidation() ?: return
        foldcade.scope.launch {
            try {
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
                    RommSignInResult.StayOnForm("Could not check this RomM server. Check the address and try again.")
                }
                val current = withContext(Dispatchers.Main.immediate) {
                    if (!foldcade.shell.setupValidationCurrent(ticket)) false else {
                        foldcade.shell.showSetupHint(result.hint, retry = true)
                        true
                    }
                }
                if (!current || result !is RommSignInResult.Accepted) return@launch
                foldcade.editCredentials {
                    if (!withContext(Dispatchers.Main.immediate) { foldcade.shell.setupValidationCurrent(ticket) }) return@editCredentials
                    val saved = try {
                        if (another) {
                            val added = addRommServer(
                                host = foldcade.plugins,
                                session = foldcade.store,
                                credentials = foldcade.credentials,
                                origin = origin,
                                token = token,
                                defaultOrigin = foldcade.store.rommOrigin(),
                                extras = foldcade.store.rommInstances(),
                                pendingInstanceId = pendingRommId,
                            )
                            if (added is RommServerAdd.Added) pendingRommId = null
                            added is RommServerAdd.Added
                        } else {
                            if (instanceId != RommCredentials.PLUGIN_ID && foldcade.store.rommServerOrigin(instanceId) == null) {
                                return@editCredentials
                            }
                            val before = foldcade.store.rommServerOrigin(instanceId)
                            // Its tiles name the old server's ROM ids until the new one lists its games.
                            if (before == null || !rommOriginAlreadyConfigured(listOf(before), origin)) {
                                withContext(Dispatchers.Main.immediate) { foldcade.shell.noteServerChanged(instanceId) }
                            }
                            saveRommConnection(foldcade.store, foldcade.credentials, instanceId, origin, token)
                            true
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        false
                    }
                    if (saved) {
                        foldcade.publishRomm()
                        foldcade.art.forgetMisses()
                    }
                    withContext(Dispatchers.Main.immediate) {
                        // Before the refresh, so only a list from the server now saved clears the hold.
                        foldcade.shell.settleServerChange(instanceId)
                        if (saved) {
                            refreshRommSignedIn()
                            if (foldcade.shell.setupValidationCurrent(ticket)) foldcade.shell.closeConnect()
                            foldcade.syncRomm()
                        } else if (foldcade.shell.setupValidationCurrent(ticket)) {
                            foldcade.shell.showSetupHint("Could not save the RomM credentials. Try again.", retry = true)
                        }
                    }
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    foldcade.shell.settleServerChange(instanceId)
                    foldcade.shell.finishSetupValidation(ticket)
                }
            }
        }
    }

    private fun refreshRommSignedIn() {
        foldcade.shell.setSignedIn(
            mergeSignedIn(
                foldcade.shell.model.signedIn,
                rommSignedInBackends(foldcade.store.rommOrigin(), foldcade.store.rommInstances()),
            ),
        )
    }

    /** The instance device-code is using, minted once and reused if the origin changes. */
    private fun pendingRommInstance(): String {
        val existing = pendingRommId
        if (existing != null && foldcade.plugins.library(existing) != null) return existing
        val created = foldcade.plugins.addInstance(RommCredentials.PLUGIN_ID)
        pendingRommId = created.instanceId
        return created.instanceId
    }

    /**
     * Device code is the main path once an origin is on the form. Paste stays in the token field.
     * The code is for the saved server when one exists, and for the typed origin otherwise.
     */
    private fun beginRommDeviceCode() {
        deviceCodeJob?.cancel()
        val instanceId = foldcade.shell.connectRommId
        val another = foldcade.shell.model.connectAnother
        val visit = foldcade.shell.beginDeviceCodeAttempt()
        deviceCodeJob = foldcade.scope.launch {
            var pairedRevision: Int? = null
            var refusedRevision: Int? = null
            while (withContext(Dispatchers.Main.immediate) { foldcade.shell.deviceCodeVisitCurrent(visit) }) {
                val (revision, origin) = withContext(Dispatchers.Main.immediate) {
                    foldcade.shell.connectOriginRevision to normalizeSetupOrigin(foldcade.shell.model.connectOrigin)
                }
                val saved = if (another) null else foldcade.store.rommServerOrigin(instanceId)?.let(::normalizeSetupOrigin)
                if (origin == null || revision == pairedRevision || (saved != null && saved != origin)) {
                    delay(200)
                    continue
                }
                val taken = foldcade.store.rommServers()
                    .filter { another || it.first != instanceId }.map { it.second }
                if (rommOriginAlreadyConfigured(taken, origin)) {
                    if (revision != refusedRevision) {
                        refusedRevision = revision
                        withContext(Dispatchers.Main.immediate) {
                            if (foldcade.shell.deviceCodeFormCurrent(visit, revision)) foldcade.shell.showSetupHint(Copy.rommSameServer)
                        }
                    }
                    delay(200)
                    continue
                }
                refusedRevision = null
                delay(400)
                if (!withContext(Dispatchers.Main.immediate) { foldcade.shell.deviceCodeFormCurrent(visit, revision) }) continue
                val id = if (another) pendingRommInstance() else instanceId
                pairedRevision = revision
                try {
                    foldcade.plugins.configure(id, mapOf(ROMM_ORIGIN_KEY to origin), remember = saved != null)
                    val outcome = pollRommDeviceCode(id, visit, revision)
                    withContext(Dispatchers.Main.immediate) {
                        if (foldcade.shell.deviceCodeFormCurrent(visit, revision)) {
                            when (outcome) {
                                is DeviceCodePoll.Approved -> validateRommToken(origin, outcome.token, another)
                                DeviceCodePoll.Expired -> foldcade.shell.showSetupHint(Copy.rommCodeExpired, retry = true)
                                DeviceCodePoll.Denied -> foldcade.shell.showSetupHint(Copy.rommCodeDenied, retry = true)
                                else -> Unit
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    withContext(Dispatchers.Main.immediate) {
                        if (foldcade.shell.deviceCodeFormCurrent(visit, revision)) {
                            foldcade.shell.showSetupHint(Copy.rommCodeUnavailable, retry = true)
                        }
                    }
                }
            }
        }
    }

    private suspend fun pollRommDeviceCode(libraryId: String, visit: Int, originRevision: Int): DeviceCodePoll = pollDeviceCode(
        begin = { foldcade.plugins.beginDeviceCode(libraryId) },
        poll = { code ->
            val current = withContext(Dispatchers.Main.immediate) { foldcade.shell.deviceCodeFormCurrent(visit, originRevision) }
            if (current) foldcade.plugins.pollDeviceCode(libraryId, code) else DeviceCodePoll.Expired
        },
        onChallenge = { challenge ->
            withContext(Dispatchers.Main.immediate) {
                if (foldcade.shell.deviceCodeFormCurrent(visit, originRevision)) {
                    foldcade.shell.showSetupHint(
                        "${Copy.rommDeviceCode}\n${challenge.userCode}\n${challenge.verificationUrl}",
                    )
                }
            }
        },
    )

    /**
     * Checks the typed SteamGridDB key with one search, then keeps it in the
     * credential store. A refused or unreachable key leaves the form open with a note.
     */
    private fun saveArtKey() {
        val key = foldcade.shell.connectToken.trim()
        if (key.isEmpty()) return
        val ticket = foldcade.shell.beginSetupValidation() ?: return
        foldcade.scope.launch {
            try {
                val accepted = foldcade.checkSteamGridDbKey(key)
                if (accepted != true) {
                    withContext(Dispatchers.Main.immediate) {
                        if (foldcade.shell.setupValidationCurrent(ticket)) {
                            foldcade.shell.showConnectNote(if (accepted == false) Copy.artKeyRefused else Copy.artKeyUnreachable)
                        }
                    }
                    return@launch
                }
                foldcade.editCredentials {
                    if (!withContext(Dispatchers.Main.immediate) { foldcade.shell.setupValidationCurrent(ticket) }) return@editCredentials
                    val saved = try {
                        foldcade.credentials.put(
                            SteamGridDb.CREDENTIAL_ID,
                            SteamGridDb.CREDENTIAL_KEY,
                            Credential.ApiToken(key),
                        )
                        true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        false
                    }
                    if (saved) {
                        foldcade.steamGridDbKey = key
                        foldcade.art.forgetMisses()
                    }
                    withContext(Dispatchers.Main.immediate) {
                        if (saved) foldcade.shell.setArtKeySaved(true)
                        if (foldcade.shell.setupValidationCurrent(ticket)) {
                            if (saved) foldcade.shell.closeConnect() else foldcade.shell.showConnectNote(Copy.artKeyUnreachable)
                        }
                    }
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    foldcade.shell.finishSetupValidation(ticket)
                }
            }
        }
    }

    private fun forgetArtKey() {
        foldcade.scope.launch {
            foldcade.editCredentials {
                val forgotten = try {
                    foldcade.credentials.forgetPlugin(SteamGridDb.CREDENTIAL_ID)
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                if (forgotten) foldcade.steamGridDbKey = null
                withContext(Dispatchers.Main.immediate) {
                    if (forgotten) foldcade.shell.setArtKeySaved(false)
                }
            }
        }
    }

    private fun forget(pluginId: String) {
        foldcade.scope.launch {
            foldcade.editCredentials {
                val forgotten = try {
                    if (isRommInstance(pluginId)) {
                        removeRommServer(foldcade.credentials, foldcade.store, pluginId)
                    } else {
                        foldcade.credentials.forgetPlugin(pluginId)
                    }
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                if (forgotten) {
                    foldcade.publishRomm()
                    if (isRommInstance(pluginId) && foldcade.plugins.library(pluginId) != null) {
                        try {
                            foldcade.plugins.configure(pluginId, mapOf(ROMM_ORIGIN_KEY to ""))
                            foldcade.plugins.disconnect(pluginId)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            Log.w("Foldcade", "Could not close RomM connection $pluginId", failure)
                        }
                    }
                    foldcade.syncRomm()
                }
                withContext(Dispatchers.Main.immediate) {
                    if (forgotten) {
                        if (isRommInstance(pluginId)) foldcade.shell.dropLibrary(pluginId)
                        foldcade.shell.setSignedIn(
                            foldcade.shell.model.signedIn.filter { it.pluginId != pluginId },
                        )
                    } else {
                        foldcade.shell.askToSignInAgain(if (isRommInstance(pluginId)) pluginId else RommCredentials.PLUGIN_ID)
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
            val previous = foldcade.store.playerSaveFolder(player.id)
            player.bindSaveFolder(uri.toString())
            foldcade.store.setPlayerSaveFolder(player.id, uri.toString())
            if (previous != null && previous != uri.toString()) {
                foldcade.releaseUnusedFolderGrant(previous, read or write)
            }
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

    /** Kept on disk too, so a process that dies while the game runs still reads its saves back. */
    private fun rememberReturn(pending: LibraryReturn) {
        libraryReturn = pending
        foldcade.scope.launch { foldcade.store.setPendingSaveReturn(pending.storeKey, pending.encode()) }
    }

    /**
     * Read-backs an earlier process never finished. Runs only when no game is
     * open, and only once the plugins are loaded: before that no player can be
     * found, and the record would be dropped.
     */
    private fun resumeLostReturn() {
        if (libraryReturn != null || foldcade.externalPlay.sessionId != null) return
        foldcade.scope.launch {
            foldcade.awaitPlugins()
            for (stored in foldcade.store.pendingSaveReturns()) {
                val lost = decodeLibraryReturn(stored) { id -> foldcade.plugins.player(id) }
                if (lost == null) {
                    // The player is gone or the record is unreadable. Its next launch still hands the save over.
                    storedReturnKey(stored)?.let { foldcade.store.setPendingSaveReturn(it, null) }
                    continue
                }
                withContext(Dispatchers.Main.immediate) {
                    if (libraryReturn == null && foldcade.externalPlay.sessionId == null) reconcile(lost)
                }
            }
        }
    }

    /** Shows the first conflict that is still waiting, unless a launch or a preview is up. */
    private fun refreshSaveConflict() {
        if (foldcade.suppressSaveConflict) return
        if (foldcade.shell.model.saveConflict?.preview == true) return
        foldcade.scope.launch {
            foldcade.awaitPlugins()
            if (foldcade.suppressSaveConflict) return@launch
            val pending = try {
                foldcade.plugins.pendingSaveConflicts()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@launch
            }
            withContext(Dispatchers.Main.immediate) {
                if (foldcade.suppressSaveConflict) return@withContext
                if (foldcade.shell.model.saveConflict?.preview == true) return@withContext
                foldcade.shell.presentSaveConflict(pending.firstOrNull()?.toPrompt())
            }
        }
    }

    private fun resolveSaveConflict(effect: Effect.ResolveSaveConflict) {
        if (effect.preview) return
        if (effect.action == SaveConflictAction.DecideLater) {
            foldcade.scope.launch {
                try {
                    foldcade.plugins.resolveSaveConflict(
                        effect.libraryId,
                        effect.id,
                        SaveConflictChoice.DecideLater,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    withContext(Dispatchers.Main.immediate) { refreshSaveConflict() }
                }
            }
            return
        }
        foldcade.scope.launch {
            try {
                foldcade.plugins.resolveSaveConflict(effect.libraryId, effect.id, effect.action.toChoice())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Unit
            }
            withContext(Dispatchers.Main.immediate) { refreshSaveConflict() }
        }
    }

    /** Reads back what the player wrote and gives it to the library. */
    private fun reconcile(pending: LibraryReturn) {
        foldcade.scope.launch {
            try {
                foldcade.saveFlow.withLock {
                    val saves = foldcade.playerSaves
                    val observed = saves.changedSince(pending.libraryId, pending.game, pending.player, pending.spots)
                    val result = foldcade.plugins.reconcile(
                        pending.libraryId,
                        pending.game,
                        pending.player,
                        observed,
                    )
                    saveNotice(pending.game, result.outcome)?.let { notice ->
                        withContext(Dispatchers.Main.immediate) { foldcade.postNotice(notice) }
                    }
                    saves.noteHandedOver(pending.libraryId, pending.game, pending.player, pending.spots, observed)
                    foldcade.store.setPendingSaveReturn(pending.storeKey, null)
                }
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

/** The session's id for an app Foldcade did not open. */
internal const val OTHER_APP_ID = "android:other"

/** How long A on a text field waits for its screen to take focus before raising the keyboard anyway. */
private const val KEYBOARD_FOCUS_WAIT_MS = 1_000L

internal const val EXTRA_ANDROID_SHELF = "app.foldcade.extra.SHELF"
internal const val EXTRA_FOLDER_LIBRARY = "app.foldcade.extra.FOLDER_LIBRARY"

private fun SaveConflict.toPrompt(): SaveConflictPrompt = SaveConflictPrompt(
    id = id,
    libraryId = libraryId,
    gameLabel = gameLabel,
    local = local.toSide(),
    remote = remote.toSide(),
)

private fun SaveCopy.toSide(): SaveConflictSide = SaveConflictSide(source, updatedAtMillis, sizeBytes)

private fun SaveConflictAction.toChoice(): SaveConflictChoice = when (this) {
    SaveConflictAction.KeepLocal -> SaveConflictChoice.KeepLocal
    SaveConflictAction.KeepRemote -> SaveConflictChoice.KeepRemote
    SaveConflictAction.KeepBoth -> SaveConflictChoice.KeepBoth
    SaveConflictAction.DecideLater -> SaveConflictChoice.DecideLater
}

private data class LibraryReturn(
    val libraryId: String,
    val game: Game,
    val player: Player,
    val spots: List<SaveSpot> = emptyList(),
)

/** One stored read-back per game. */
private val LibraryReturn.storeKey: String get() = "$libraryId\u0000${game.remoteKey}"

private fun storedReturnKey(text: String): String? = try {
    val json = JSONObject(text)
    "${json.getString("library")}\u0000${json.getString("key")}"
} catch (_: Exception) {
    null
}

private fun LibraryReturn.encode(): String = JSONObject().apply {
    put("library", libraryId)
    put("backend", game.backendId)
    put("key", game.remoteKey)
    put("platform", game.platformId)
    put("availability", game.availability.name)
    put("label", game.label)
    put("player", player.id)
    put(
        "spots",
        JSONArray(
            spots.map { spot ->
                JSONObject().put("slot", spot.slot).put("folder", spot.folderUri).put("file", spot.fileName)
            },
        ),
    )
}.toString()

private fun decodeLibraryReturn(text: String, player: (String) -> Player?): LibraryReturn? {
    val json = try {
        JSONObject(text)
    } catch (_: Exception) {
        return null
    }
    val resolved = player(json.optString("player")) ?: return null
    return try {
        decodeLibraryReturn(json, resolved)
    } catch (_: Exception) {
        null
    }
}

private fun decodeLibraryReturn(json: JSONObject, player: Player): LibraryReturn {
    val spots = json.getJSONArray("spots")
    return LibraryReturn(
        libraryId = json.getString("library"),
        game = Game(
            backendId = json.getString("backend"),
            remoteKey = json.getString("key"),
            platformId = json.getString("platform"),
            availability = Availability.valueOf(json.getString("availability")),
            label = json.getString("label"),
        ),
        player = player,
        spots = (0 until spots.length()).map { index ->
            val spot = spots.getJSONObject(index)
            SaveSpot(spot.getString("slot"), spot.getString("folder"), spot.getString("file"))
        },
    )
}

private class PreparedLaunch(val target: LaunchTarget, val spots: List<SaveSpot>)

/** A library save could not be written into the player's folder and checked. */
private class SaveNotPlaced : Exception("save could not be placed")

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
