package app.foldcade

import androidx.compose.ui.graphics.toArgb
import android.app.Activity
import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import app.foldcade.api.plugin.GameActivity
import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveConflict
import app.foldcade.api.plugin.SaveConflictChoice
import app.foldcade.api.plugin.SaveCopy
import app.foldcade.api.plugin.HostedSaveFolder
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.SaveFolderPart
import app.foldcade.api.plugin.SaveFolderSearch
import app.foldcade.api.plugin.StartDisplay
import app.foldcade.artwork.SteamGridDb
import app.foldcade.language.AboutCopy
import app.foldcade.language.SaveConflictAction
import app.foldcade.language.SaveConflictPrompt
import app.foldcade.language.SaveConflictSide
import app.foldcade.language.Copy
import app.foldcade.language.SignedInBackend
import app.foldcade.language.DialogButton
import app.foldcade.language.DialogKind
import app.foldcade.language.DialogState
import app.foldcade.language.Effect
import app.foldcade.language.GridKind
import app.foldcade.language.HOME_ANDROID_APPS
import app.foldcade.language.HOME_ANDROID_GAMES
import app.foldcade.language.HomeKeys
import app.foldcade.language.HostScreen
import app.foldcade.language.closeBothPanelDialog
import app.foldcade.language.SAME_PREFIX
import app.foldcade.language.homeGameId
import app.foldcade.language.missingCoreDialog
import app.foldcade.language.missingPlayerDialog
import app.foldcade.language.noFileDialog
import app.foldcade.language.noSharedPathDialog
import app.foldcade.language.playerNotice
import app.foldcade.language.AndroidSetting
import app.foldcade.language.edenFolderDialog
import app.foldcade.language.edenRestartDialog
import app.foldcade.language.edenWrongFolderDialog
import app.foldcade.language.retroArchCoresDialog
import app.foldcade.language.retroArchStorageDialog
import app.foldcade.language.retroArchStoreBuildDialog
import app.foldcade.language.hostedSavesDialog
import app.foldcade.language.playerSetupDialog
import app.foldcade.language.saveFolderDialog
import app.foldcade.language.wrongSaveFolderBody
import app.foldcade.language.restartGameDialog
import app.foldcade.language.PanelKeyActivity
import app.foldcade.language.PromptKey
import app.foldcade.language.interpretThorStyle
import app.foldcade.language.alsoRunsLine
import app.foldcade.plugins.emulators.EDEN_PACKAGES
import app.foldcade.plugins.retroarch.RetroArchPlayer
import app.foldcade.plugins.romm.ROMM_ORIGIN_KEY
import app.foldcade.romm.RomProgress
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
import kotlinx.coroutines.CompletableDeferred
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
        const val STATE_FOLDER_PICKER = "foldcade.folderPicker"
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

    /** A library game waiting on the save-folder ask, see [askSaveFolder]. Kept by the app, so a recreated Home still has it. */
    private var pendingBackend: PendingBackendLaunch?
        get() = foldcade.pendingSaveAsk
        set(value) {
            foldcade.pendingSaveAsk = value
        }

    /** The tile Restart asked about, see [FoldcadeApp.restartAsk]. */
    private var restartId: String?
        get() = foldcade.restartAsk
        set(value) {
            foldcade.restartAsk = value
        }
    private var closeConfirmed = false
    private var settingsPlayerId: String? = null
    private var libraryReturn: LibraryReturn? = null
    private var deviceCodeJob: Job? = null

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val purpose = folderPurpose
        folderPurpose = FolderPurpose.Library
        if (uri == null) {
            when (purpose) {
                FolderPurpose.LaunchOffer -> skipSaveFolderAndLaunch(SaveFolderAnswer.PickerCancelled)
                FolderPurpose.SaveOffer -> {
                    settingsPlayerId = null
                    skipSaveFolderAndLaunch(SaveFolderAnswer.PickerCancelled)
                }
                FolderPurpose.Settings -> settingsPlayerId = null
                FolderPurpose.Library -> Unit
                FolderPurpose.RetroArchCores -> settleRetroArchCores()
                FolderPurpose.EdenFolder -> declineEdenFolder()
            }
            return@registerForActivityResult
        }
        when (purpose) {
            FolderPurpose.RetroArchCores -> useRetroArchFolder(uri)
            FolderPurpose.EdenFolder -> useEdenFolder(uri)
            FolderPurpose.Library -> {
                if (persistTreeGrant(write = false) { flags ->
                    contentResolver.takePersistableUriPermission(uri, flags)
                }) {
                    foldcade.useFolder(uri)
                } else {
                    foldcade.shell.present(playerNotice(Copy.folderGrantTitle, Copy.folderGrantFailed, hostScreen()))
                }
            }
            FolderPurpose.LaunchOffer -> {
                // Not the player's folder: ask again, and the game waits.
                if (!pickedSaveFolderFits(uri, playerForPendingLaunch(), ask = true)) return@registerForActivityResult
                if (rememberSaveFolder(uri, playerForPendingLaunch())) {
                    pendingLaunch?.let { launchGame(it) }
                } else {
                    cancelLaunches()
                }
            }
            FolderPurpose.SaveOffer -> {
                val player = settingsPlayerId?.let { foldcade.plugins.player(it) }
                settingsPlayerId = null
                if (!pickedSaveFolderFits(uri, player, ask = pendingBackend != null)) return@registerForActivityResult
                // A recreated screen lost the game. The folder is kept, and the next press starts it.
                val pending = pendingBackend
                pendingBackend = null
                if (rememberSaveFolder(uri, player) && pending != null) {
                    continueBackendSaveAsk(pending, SaveFolderAnswer.Chosen)
                } else {
                    cancelLaunches()
                }
            }
            FolderPurpose.Settings -> {
                val player = settingsPlayerId?.let { foldcade.plugins.player(it) }
                settingsPlayerId = null
                if (pickedSaveFolderFits(uri, player, ask = false)) rememberSaveFolder(uri, player)
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

        override fun onDisplayRemoved(displayId: Int) {
            foldcade.companionLaunch.displayRemoved(displayId, foldcade.companionAlive(displayId))
        }

        override fun onDisplayChanged(displayId: Int) = Unit
    }

    // The hero has no controls of its own. Android gives its display input focus after a
    // launcher-icon start or Home on that screen, so its keys drive the picker too.
    override fun pickerIsFocused(): Boolean = foldcadeSurfaceFocused()

    override fun foldcadeSurfaceFocused(): Boolean {
        val panel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop) ?: return false
        return foldcade.store.session.surfaceOn(panel) != null
    }

    internal val inputWindow: String = java.util.UUID.randomUUID().toString()

    override fun onInputKey(meaning: app.foldcade.language.Meaning?, key: Int): Boolean = foldcade.shell.inputKey(meaning, inputWindow, key)

    override fun onInputCanceled() { foldcade.shell.cancelDeleteHold(inputWindow) }

    override fun onTypedText(text: String) {
        // The command center on this screen covers the field; its keys are its own.
        val panel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop) ?: return
        if (foldcade.store.session.surfaceOn(panel) == Surface.CommandCenter) return
        dispatch(foldcade.shell.typeText(text))
    }

    override fun faceMap(): app.foldcade.language.FaceMap = foldcade.shell.model.faceMap

    override fun onPromptHeld(key: PromptKey, held: Boolean) {
        dispatch(foldcade.shell.setPromptHeld(key, held, inputWindow))
    }

    override fun capturingHold(): Boolean = foldcade.shell.model.deleteHold?.inputWindow == inputWindow

    override fun capturingConfirm(): Boolean = foldcade.shell.model.capturingConfirm

    override fun onMeaningReleased(meaning: app.foldcade.language.Meaning, canceled: Boolean) {
        dispatch(foldcade.shell.releaseMeaning(meaning, inputWindow, canceled))
    }

    override fun onCalibrateConfirm(key: PromptKey) {
        foldcade.shell.calibrateConfirm(key)
    }

    override fun homeKeys(): HomeKeys = foldcade.shell.homeKeys()

    override fun rowsTakeVerticalKeys(): Boolean = foldcade.shell.model.connectOpen || foldcade.shell.model.savePicker != null

    override fun onMeaning(meaning: app.foldcade.language.Meaning) = onMeaningRepeated(meaning, 0)

    override fun onMeaningRepeated(meaning: app.foldcade.language.Meaning, repeatCount: Int) {
        if (commandCenterKey(meaning)) return
        if (app.foldcade.language.topMenuKeyBlocked(meaning, foldcade.shell.homeKeys(), foldcade.store.session.islandsOpen())) return
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
        dispatch(foldcade.shell.onMeaning(meaning, screen, repeatCount, inputWindow))
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
                // A launch from Find has read its game; the grid is what to come back to.
                foldcade.shell.closeFindAfterLaunch()
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
            is Effect.SetUpHostedSaves -> (foldcade.plugins.player(effect.playerId) as? HostedSaveFolder)?.let { player ->
                askHostedSaves(player, pending = null)
            }
            is Effect.ShowSaveSync -> showSaveSync(effect.playerId)
            is Effect.ShowPlayerSetup -> showPlayerSetup(effect.playerId)
            is Effect.OpenPlayer -> {
                cancelLaunches()
                openPlayerApp(effect.playerId)
            }
            Effect.ShowRetroArchCores -> showRetroArchCores(retry = null)
            Effect.ShowEdenFolder -> openEdenFolder(fromOffer = false)
            Effect.Undo, is Effect.ShowHidden, is Effect.DeleteShortcut -> Unit
            // The shell applies Y's tile actions and A on a text field itself and hands back null; these never arrive.
            is Effect.SelectTile, is Effect.Bulk, is Effect.ChooseArt, Effect.MoveTile, is Effect.ShowOnHome, is Effect.ToggleCollection, is Effect.NewCollection, is Effect.RecentOnHome,
            is Effect.MoveApp, is Effect.Hide,
            Effect.RenameFolder, Effect.ChooseFolderIcon, Effect.RemoveTile, Effect.Tidy, Effect.GoHome, Effect.EditField,
            is Effect.SortFolder,
            -> Unit
            is Effect.RemoveDownload -> removeDownload(effect.gameId)
            is Effect.RestartGame -> askRestart(effect.gameId)
            is Effect.RemovePlaySession -> foldcade.plays.remove(effect.sessionId)
            // The shell lists the day's sessions itself.
            is Effect.OpenPlayDay -> Unit
            Effect.OpenAll -> foldcade.shell.openHomeAll()
            is Effect.OpenFolder -> foldcade.shell.openHomeFolder(effect.folderId)
            Effect.ToggleAddNew -> foldcade.shell.applyHomeAddNew(foldcade.shell.model.addNewToHome)
            Effect.EditRommConnection -> {
                if (foldcade.shell.editRommConnection()) beginRommDeviceCode()
            }
            Effect.OpenAddRomm -> {
                foldcade.shell.openAddRomm()
                beginRommDeviceCode()
            }
            Effect.SaveRommToken -> saveRommToken()
            Effect.RetryRommDeviceCode -> beginRommDeviceCode()
            Effect.OpenArtKey -> foldcade.shell.openArtKey()
            Effect.SaveArtKey -> saveArtKey()
            is Effect.ForgetCredentials -> forget(effect.pluginId)
            is Effect.ForgetFolder -> foldcade.forgetFolder(effect.uri)
            is Effect.PairStreamHost -> foldcade.pairMoonlightHost(effect.key, hostScreen())
            is Effect.DialogChoice -> onDialog(effect)
            Effect.RequestHome -> requestHome()
            is Effect.OpenAndroidSetting -> openAndroidSetting(effect.setting)
            Effect.OpenLicenses -> startActivity(Intent(this, LicensesActivity::class.java))
            Effect.OpenThemes -> startActivity(Intent(this, ThemesActivity::class.java))
            Effect.CopyDebugInfo -> copyDebugInfo()
            Effect.DismissButtonLabels -> Unit
            is Effect.OpenNotice -> openNotice(effect.id, if (effect.onBottom) Panel.Bottom else Panel.Top)
            is Effect.DismissNotice -> foldcade.dismissNotice(effect.id)
            Effect.ClearNotices -> foldcade.clearNotices()
            is Effect.OpenSaves -> openSaves(effect.gameId)
            is Effect.LoadSavePicker -> loadSavePicker(effect.target)
            is Effect.FixSavePicker -> dispatch(foldcade.shell.savePickerFix(effect.target))
            is Effect.SyncSavePicker -> changeServerSave(effect.target, null)
            is Effect.PlayPickedSave -> changeServerSave(effect.target, effect.saveId)
            is Effect.CopyPickedSave -> changeServerSave(effect.target, effect.saveId, effect.destination)
            is Effect.ResolveSaveConflict -> resolveSaveConflict(effect)
            null -> Unit
        }
    }

    /** One line a player can paste into a bug report: Foldcade's build, Android's, and the device's. */
    private fun copyDebugInfo() {
        val line = AboutCopy.debugLine(
            version = foldcade.shell.model.appVersion,
            android = Build.VERSION.RELEASE,
            device = AboutCopy.device(Build.MANUFACTURER, Build.MODEL),
            build = Build.DISPLAY,
        )
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(AboutCopy.version, line))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(
            STATE_FOLDER_PICKER,
            FolderPickerState(folderPurpose, pendingLaunch, settingsPlayerId).encode(),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        decodeFolderPickerState(savedInstanceState?.getString(STATE_FOLDER_PICKER)).let { restored ->
            folderPurpose = restored.purpose
            pendingLaunch = restored.pendingLaunch
            settingsPlayerId = restored.settingsPlayerId
        }
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
        // What shows before the first frame and while the window moves is the theme's, not black.
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(themeBackgroundArgb()))
        recoveryWindow = PanelRecoveryWindow(this, root, panel)
        hideSystemBars()
    }

    /** The chosen theme's background, for windows drawn before Compose is. */
    internal fun themeBackgroundArgb(): Int =
        foldcade.paintFor(foldcade.shell.model.themeIndex).theme.background.toArgb()

    override fun onResume() {
        super.onResume()
        // onPause marked this window unfocused. A pause that kept focus, as Home or a new
        // intent to this screen does, brings no focus change to mark it focused again,
        // and a hold to delete would wait for one.
        foldcade.shell.inputFocus(inputWindow, hasWindowFocus())
        if (restartReplacedPlayer()) return
        // A core installed in RetroArch counts for the order of players. Its launch reads them again anyway.
        foldcade.refreshRetroArchCoresOnReturn()
        // A player may have been pointed at the folder Foldcade hosts for its saves while Home was away.
        foldcade.refreshHostedFolders()
        foldcade.shell.refreshPlayerSaves()
        foldcade.refreshSaveSync()
        // Usage access may have changed while Home was away, and without it no game reads Paused.
        foldcade.pruneRunningGames()
        val displayId = display?.displayId
        val ending = displayId != null &&
            foldcade.externalPlay.sessionId != null &&
            foldcade.externalPlay.displayId == displayId
        val pending = libraryReturn
        val leftPlayer = foldcade.externalPlay.packageName.takeIf { ending }
        if (displayId != null) foldcade.externalPlay.endIfDisplayHome(foldcade.plays, displayId)
        val returned = ending && foldcade.externalPlay.sessionId == null
        // Back here without the Home button: the player closed the game itself, so it is not paused.
        if (returned && leftPlayer != null && foldcade.homeRecalledOn != displayId) foldcade.runningGames.forget(leftPlayer)
        if (foldcade.homeRecalledOn == displayId) foldcade.homeRecalledOn = null
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
        foldcade.shell.inputFocus(inputWindow, false)
        resumed = false
        refreshShellVisible()
        displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop)?.let(foldcade.music::onHomePause)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        foldcade.shell.inputFocus(inputWindow, hasFocus)
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
        usageReconcile.cancel()
        // A download still running on this instance would finish on a dead
        // activity and, if the start threw, send both screens home.
        cancelLaunchIfOwned(foldcade.launchWork, ownedLaunch)
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
        // The player was left with Home, so the game it holds is still open there.
        foldcade.homeRecalledOn = display?.displayId
        // The user left the game. Only a player quitting by itself is started again.
        foldcade.pendingRestart = null
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
        if (foldcade.companionAlive(other)) return
        if (!foldcade.companionLaunch.begin(other)) return
        val intent = Intent(this, CompanionHomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val options = ActivityOptions.makeBasic().apply { launchDisplayId = other }
        try {
            startActivity(intent, options.toBundle())
        } catch (failure: RuntimeException) {
            foldcade.companionLaunch.failed()
            Log.w("Foldcade", "Could not open the other screen", failure)
        }
    }

    private fun launchGame(index: Int, saveSyncDeclinedFor: String? = null) {
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
                // The core decides where RetroArch keeps the save, so it is fixed before saves are placed.
                if (player is RetroArchPlayer) foldcade.readRetroArchCores()
                val prepared = try {
                    val local = foldcade.plugins.ensureLocal(libraryId, apiGame)
                    withContext(Dispatchers.Main.immediate) { foldcade.shell.noteOnDevice(libraryId, remoteKey) }
                    prepareSaves(GameRoute(game.id, apiGame, apiGame), player, local)
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
                    finishLaunch(index, game, player, prepared.target, apiGame, generation, prepared.spots, coresRead = true, saveSyncDeclinedFor = saveSyncDeclinedFor)
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
            saveSyncDeclinedFor = saveSyncDeclinedFor,
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
        retroArchChecked: Boolean = false,
        coresRead: Boolean = false,
        edenChecked: Boolean = false,
        setupChecked: Boolean = false,
        saveSyncDeclinedFor: String? = null,
    ) {
        if (!launchCurrent(generation)) return
        // A player that still needs its own setup, such as firmware, does not start. Not now only closes.
        // A folder not yet asked for is asked for first, below, and this runs again once it is settled.
        val folderSettled = player is SaveFolderHolder &&
            (player.hasSaveFolder() || saveSyncDeclinedFor == player.id)
        if (folderSettled && !setupChecked) {
            foldcade.launchWork.job = foldcade.scope.launch {
                val steps = playerSetupSteps(player)
                withContext(Dispatchers.Main.immediate) {
                    if (!launchCurrent(generation)) return@withContext
                    if (steps != null) {
                        showPlayerSetup(player.id, steps)
                        return@withContext
                    }
                    finishLaunch(
                        index,
                        game,
                        player,
                        target,
                        apiGame,
                        generation,
                        spots,
                        retroArchChecked = retroArchChecked,
                        coresRead = coresRead,
                        edenChecked = edenChecked,
                        setupChecked = true,
                        saveSyncDeclinedFor = saveSyncDeclinedFor,
                    )
                }
            }
            return
        }
        if (player is RetroArchPlayer && !retroArchChecked) {
            foldcade.launchWork.job = foldcade.scope.launch {
                if (retroArchHeld(player, generation, readCores = !coresRead) { launchGame(index, saveSyncDeclinedFor) }) return@launch
                withContext(Dispatchers.Main.immediate) {
                    finishLaunch(
                        index,
                        game,
                        player,
                        target,
                        apiGame,
                        generation,
                        spots,
                        retroArchChecked = true,
                        coresRead = coresRead,
                        edenChecked = edenChecked,
                        setupChecked = setupChecked,
                        saveSyncDeclinedFor = saveSyncDeclinedFor,
                    )
                }
            }
            return
        }
        if (!edenChecked && installedEdenPackage(player, ::packageInstalled) != null) {
            foldcade.launchWork.job = foldcade.scope.launch {
                if (edenHeld(player, generation, { launchGame(index, saveSyncDeclinedFor) }, game.libraryId, game.remoteKey)) return@launch
                withContext(Dispatchers.Main.immediate) {
                    finishLaunch(
                        index,
                        game,
                        player,
                        target,
                        apiGame,
                        generation,
                        spots,
                        retroArchChecked = true,
                        coresRead = coresRead,
                        edenChecked = true,
                        setupChecked = setupChecked,
                        saveSyncDeclinedFor = saveSyncDeclinedFor,
                    )
                }
            }
            return
        }
        val continuing = pendingLaunch == index && closeConfirmed
        val decision = try {
            planPlayerLaunch(
                player = player,
                game = apiGame,
                target = target,
                installedPackages = installedPackages(player.packageNames),
                anotherBothPanelRunning = anotherBothPanelRunning(foldcade.store.session, game.id),
                closeConfirmed = continuing,
                saveFolderSettled = saveSyncDeclinedFor == player.id,
                componentResolves = { intent ->
                    packageManager.resolveActivity(intent.toAndroidIntent(), 0) != null
                },
                sharedFile = foldcade.sharedPaths::target,
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
                LaunchBlock.SaveFolder -> foldcade.shell.present(
                    if (needsItsFolder(player)) {
                        saveFolderDialog(
                            hostScreen(),
                            playerName = player.displayName,
                            opensThere = (player as? SaveFolderHolder)?.suggestedSaveFolder != null,
                            needed = true,
                        )
                    } else {
                        saveFolderDialog(hostScreen())
                    },
                )
                LaunchBlock.NoSharedPath -> foldcade.shell.present(noSharedPathDialog(decision.playerName, hostScreen()))
            }
            is PlayerLaunch.Ready -> {
                clearPendingLaunch()
                startPlayer(game, decision, apiGame, player, generation, spots)
            }
        }
    }

    private fun acceptShelf(intent: Intent) {
        val folder = when (intent.getStringExtra(EXTRA_ANDROID_SHELF)) {
            "games" -> HOME_ANDROID_GAMES
            "apps" -> HOME_ANDROID_APPS
            else -> return
        }
        foldcade.shell.openHomeFolder(folder)
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
        // The player opened by its own icon is the player's to steer now, and its game may
        // close without Foldcade seeing it, so that game is no longer held as paused.
        foldcade.runningGames.forget(pkg)
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
        val route = foldcade.shell.launchRoute(game.id)
        if (route != null) {
            launchBackendGame(title = game.title, platformId = route.play.platformId, route = route)
            return
        }
        if (game.id.startsWith("lib:") || game.id.startsWith(SAME_PREFIX)) {
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
        // The copy pressed plays. Its tile keeps the play, and saves go where the tile's do.
        val tileId = foldcade.shell.tileOf(homeGameId(libraryId, game.remoteKey))
        val route = foldcade.shell.launchRoute(tileId, pressed = game) ?: return
        launchBackendGame(title = entry.title, platformId = entry.platformId, route = route)
    }

    private fun launchBackendGame(
        title: String,
        platformId: String?,
        route: GameRoute,
        fresh: Boolean = false,
        saveSyncDeclinedFor: String? = null,
    ) {
        foldcade.noteSaveLaunch(route)
        // A game still open in its player comes back where it was, with nothing downloaded or placed.
        if (!fresh && resumeRunning(route.tileId, title, platformId) { launchBackendGame(title, platformId, route, fresh = true) }) return
        val game = route.play
        val libraryId = game.backendId
        // Play history uses the tile's id from every grid.
        val id = route.tileId
        // Its download is being deleted. A press once that is done downloads it again.
        if (homeGameId(libraryId, game.remoteKey) in foldcade.shell.removing) return
        // A server game downloads first. Its tile pulses until then, and a second press waits.
        val fetching = game.availability == Availability.RemoteOnly
        if (fetching && !foldcade.shell.beginDownload(libraryId, game.remoteKey)) return
        // A game on the device pulses while Eden's firmware downloads for it. A second press waits for that too.
        if (!fetching && homeGameId(libraryId, game.remoteKey) in foldcade.shell.downloading) return
        val generation = beginLaunch()
        foldcade.suppressSaveConflict = true
        foldcade.launchWork.job = foldcade.scope.launch {
            try {
                runBackendLaunch(title, platformId, route, fetching, generation, fresh, saveSyncDeclinedFor)
            } finally {
                // Opened or not, a half-told download or sync is stale now. A sync's mark stays.
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    foldcade.shell.settleActivity(libraryId, game.remoteKey)
                    foldcade.shell.settleActivity(route.saves.backendId, route.saves.remoteKey)
                    // A game that started shows the conflict when Foldcade is in front again.
                    if (foldcade.externalPlay.sessionId == null && libraryReturn == null) {
                        foldcade.suppressSaveConflict = false
                        // The screen that started the download may already be gone.
                        if (!isDestroyed && !isFinishing) refreshSaveConflict()
                        else foldcade.refreshSaveConflictOnSurvivor()
                    }
                }
            }
        }
    }

    /** Download, saves, then the player, for [launchBackendGame]. Each step that shows anything checks [generation]. */
    private suspend fun runBackendLaunch(
        title: String,
        platformId: String?,
        route: GameRoute,
        fetching: Boolean,
        generation: Int,
        fresh: Boolean,
        saveSyncDeclinedFor: String?,
    ) {
        val game = route.play
        val libraryId = game.backendId
        val id = route.tileId
        val players = foldcade.rankedPlayers(game.platformId)
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
        // RetroArch's core and storage, also before a download.
        val first = players.firstOrNull { installedPackage(it) != null }
        if (first is RetroArchPlayer) {
            val retry = { launchBackendGame(title, platformId, route) }
            if (retroArchHeld(first, generation, retry = retry)) {
                if (fetching) withContext(Dispatchers.Main.immediate) { foldcade.shell.endDownload(libraryId, game.remoteKey) }
                return
            }
        }
        // Eden's keys and firmware, also before a download. The same player the RetroArch check uses.
        if (first != null && installedEdenPackage(first, ::packageInstalled) != null) {
            val retry = { launchBackendGame(title, platformId, route, fresh) }
            try {
                if (edenHeld(first, generation, retry, libraryId, game.remoteKey)) {
                    if (fetching) withContext(Dispatchers.Main.immediate) { foldcade.shell.endDownload(libraryId, game.remoteKey) }
                    return
                }
            } catch (cancelled: CancellationException) {
                // ensureLocal's finally never runs, so a RemoteOnly pulse would stay on.
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    if (fetching) foldcade.shell.endDownload(libraryId, game.remoteKey)
                }
                throw cancelled
            }
        }
        if (first is HostedSaveFolder) foldcade.refreshHostedFolders()
        val syncState = if (first is SaveFolderHolder && first.hasSaveFolder()) {
            val folder = foldcade.store.playerSaveFolder(first.saveFolderId())
            if (folder != null) withContext(Dispatchers.IO) { foldcade.playerSaves.saveSync(first, folder) } else null
        } else null
        val offer = first?.let { saveLaunchOffer(it, route.servers.isNotEmpty(), saveSyncDeclinedFor, syncState) }
        if (offer != null) {
            withContext(Dispatchers.Main.immediate) {
                if (fetching) foldcade.shell.endDownload(libraryId, game.remoteKey)
                if (!launchCurrent(generation)) return@withContext
                val pending = PendingBackendLaunch(title, platformId, route, first.id, launchScreen, fresh)
                when (offer) {
                    SaveLaunchOffer.Folder -> askSaveFolder(pending)
                    is SaveLaunchOffer.Setup -> askSaveSetup(first, offer.steps, pending)
                }
            }
            return
        }
        val local = try {
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
        val plan = planLaunch(players, local, installedPackage)
        // A player that opens games by path gets the file's path in shared storage.
        val opensByPath = plan is GameLaunch.Installed && plan.player.opensByPath
        val target = if (opensByPath && local is LaunchTarget.ContentUri) {
            foldcade.sharedPaths.target(local.uri) ?: run {
                withContext(Dispatchers.Main.immediate) {
                    if (launchCurrent(generation)) {
                        foldcade.shell.showDialog(noSharedPathDialog((plan as GameLaunch.Installed).player.displayName, hostScreen()))
                    }
                }
                return
            }
        } else {
            local
        }
        // Saves go into the player's folder before it starts, and come back when it returns.
        var owner = route.saves
        val spots = if (plan is GameLaunch.Installed) {
            try {
                prepareSaves(route, plan.player, target).also { owner = it.owner ?: owner }.spots
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
        // A player that still needs its own setup, such as firmware, does not start.
        if (plan is GameLaunch.Installed) {
            val steps = playerSetupSteps(plan.player)
            if (steps != null) {
                withContext(Dispatchers.Main.immediate) {
                    if (launchCurrent(generation)) showPlayerSetup(plan.player.id, steps)
                }
                return
            }
        }
        val playerIntent = if (plan is GameLaunch.Installed) {
            try {
                foldcade.plugins.launchIntent(
                    plan.player.id,
                    LaunchRequest(game, target, plan.packageName),
                ).let { foldcade.startIntent(plan.player, it) }
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
        val installedIntent = playerIntent?.toStartIntent(foldcade.rommFolderAuthority)
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
                        noteStart = { playerIntent?.let { foldcade.noteStarted(plan.player, it) } == true },
                    )
                    if (started) {
                        val saves = LibraryReturn(
                            owner.backendId,
                            owner,
                            plan.player,
                            spots,
                            (target as? LaunchTarget.ContentUri)?.uri,
                        )
                        rememberReturn(saves)
                        val sameStart = playerIntent?.let { LaunchFlag.SameStartResumes in it.flags } == true
                        noteRunning(id, title, game.platformId, plan.player, installedIntent, saves, sameStart)
                    }
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
     * Notes a game just started in [player], so A brings it back after Home
     * instead of starting it again. RetroArch is left out: it keeps one game
     * and switches by itself (see [FoldcadeApp.startIntent]).
     */
    private fun noteRunning(
        id: String,
        title: String,
        platformId: String?,
        player: Player,
        intent: Intent,
        saves: LibraryReturn?,
        sameStartResumes: Boolean = false,
    ) {
        val pkg = intent.component?.packageName ?: return
        if (pkg == packageName || player is RetroArchPlayer) return
        if (!resumable(packageManager.getLaunchIntentForPackage(pkg)?.component?.className, intent.component?.className)) {
            foldcade.runningGames.noteHolding(pkg)
            return
        }
        // Where the game landed, which may not be where it was asked for when that screen was busy.
        val panel = landedPanel() ?: if (player.occupiesBothDisplays) Panel.Top else launchScreen
        val resumeStart = if (sameStartResumes) Intent(intent).apply { flags = resumeStartFlags(flags) } else null
        foldcade.runningGames.note(RunningGame(id, title, platformId, pkg, intent.component?.className, panel,
            player.occupiesBothDisplays, System.currentTimeMillis(), saves, resumeStart))
    }

    /** The screen the game just started is on. */
    private fun landedPanel(): Panel? = foldcade.externalPlay.displayId
        ?.let { displays.assignment(foldcade.store.session.defaultDisplayIsTop).panelFor(it) }

    /**
     * Brings back the game on tile [tileId] if it is still open in its player.
     * True when that start was sent, or is waiting on the read-back of the game's
     * last session, see [LaunchFlag.SameStartResumes]; if that start then cannot be
     * sent, [startAnew] launches the game as a new press would. Its player may have
     * closed since without Android saying so; the same start then opens the game anew.
     */
    private fun resumeRunning(tileId: String, title: String, platformId: String?, startAnew: () -> Unit): Boolean {
        foldcade.pruneRunningGames()
        val running = foldcade.runningOn(tileId) ?: return false
        // A chosen save must load even when the old game is still paused behind Home.
        val savePlayer = platformId?.let(foldcade::playerFor)
        if (savePlayer != null && foldcade.store.saveAwaitsLoad(savePlayer.saveFolderId())) return false
        // A player that takes its game's start again as a resume gets that, see LaunchFlag.SameStartResumes.
        val front = running.resumeStart?.let(::Intent)
            ?: packageManager.getLaunchIntentForPackage(running.packageName)?.apply { flags = RESUME_FLAGS } ?: run {
            foldcade.runningGames.forget(running.packageName)
            return false
        }
        // Already on a screen, so this press came from the grid on the other one. The game
        // comes forward where it is, untouched, rather than being started over.
        val session = foldcade.store.session
        val shown = Panel.entries.firstOrNull { panel -> session.externalOn(panel)?.id.let { it == tileId || it == running.tileId } }
        if (shown != null) {
            val assignment = displays.assignment(session.defaultDisplayIsTop)
            val displayId = if (shown == Panel.Top) assignment.topDisplayId else assignment.bottomDisplayId
            if (displayId != null) {
                try {
                    startOnDisplay(front, displayId, multipleTask = false)
                } catch (_: ActivityNotFoundException) {
                    Unit
                }
            }
            return true
        }
        // Behind Home, a player that may have closed unseen would open its own menu, not the game.
        if (!foldcade.closesVisible) return false
        cancelLaunches()
        if (running.resumeStart != null) {
            // The same start boots the game again when it had closed, and it writes the saves the read-back
            // of its last session may still be reading. A read-back already holding the save flow finishes
            // first, and only then is this session's own noted, so that read-back cannot drop it.
            val generation = beginLaunch()
            foldcade.launchWork.job = foldcade.scope.launch {
                foldcade.saveFlow.withLock { }
                withContext(Dispatchers.Main.immediate) {
                    if (!launchCurrent(generation) || isDestroyed || isFinishing) return@withContext
                    if (!sendResume(running, tileId, title, platformId, front)) startAnew()
                }
            }
            return true
        }
        return sendResume(running, tileId, title, platformId, front)
    }

    /** Sends [front], which brings back [running], and notes the session it starts. True when it was sent. */
    private fun sendResume(running: RunningGame, tileId: String, title: String, platformId: String?, front: Intent): Boolean {
        foldcade.music.onExternalLaunch()
        launchScreen = running.panel
        launchSubject = foldcade.shell.focusedRunningApp()
        val sentAt = System.currentTimeMillis()
        val started = try {
            startExternal(tileId, title, platformId ?: running.platformId, running.occupiesBoth, front, resume = true)
        } catch (_: ActivityNotFoundException) {
            foldcade.runningGames.forget(running.packageName)
            return false
        }
        if (!started) return false
        running.saves?.let(::rememberReturn)
        // A busy screen sends it to the other one; the next resume looks for it there.
        landedPanel()?.takeIf { it != running.panel }?.let { foldcade.runningGames.note(running.copy(panel = it)) }
        // The same start opens the game anew by itself when it had closed, so there is nothing to look for.
        if (running.resumeStart == null) checkResume(running, sentAt, front.component?.className)
        return true
    }

    /**
     * A moment after a resume, looks at what came to the front. If the player's own
     * menu ([frontDoor]) did, its task was gone, so the game starts afresh in its
     * place, as A on a game not open would have. Anything else leaves it be.
     */
    private fun checkResume(running: RunningGame, sentAt: Long, frontDoor: String?) {
        foldcade.scope.launch {
            delay(RESUME_CHECK_MS)
            val resumed = withContext(Dispatchers.IO) {
                UsageAccess(this@FoldcadeHomeActivity).resumedSince(running.packageName, sentAt, System.currentTimeMillis())
            }
            if (resumeFound(resumed, frontDoor, running.activity) != false) return@launch
            withContext(Dispatchers.Main.immediate) {
                // Only the same start of the same game, with its player still in front from this resume.
                val now = foldcade.runningOn(running.tileId) ?: return@withContext
                if (now.packageName != running.packageName || now.startedAtMillis != running.startedAtMillis) return@withContext
                if (foldcade.externalPlay.sessionId == null || foldcade.externalPlay.packageName != running.packageName) return@withContext
                if (isDestroyed || isFinishing) return@withContext
                val route = foldcade.shell.launchRoute(running.tileId) ?: return@withContext
                launchScreen = now.panel
                launchSubject = null
                launchBackendGame(title = running.title, platformId = running.platformId, route = route, fresh = true)
            }
        }
    }

    /**
     * Holds [pending] and asks for its player's save folder. Choosing opens the picker;
     * declining starts only this launch, or closes when the player requires its folder.
     */
    private fun askSaveFolder(pending: PendingBackendLaunch) {
        pendingBackend = pending
        val player = foldcade.plugins.player(pending.playerId)
        foldcade.shell.present(
            saveFolderDialog(
                hostScreen(),
                syncs = true,
                playerName = player?.displayName,
                opensThere = (player as? SaveFolderHolder)?.suggestedSaveFolder != null,
                needed = needsItsFolder(player),
            ),
        )
    }

    /**
     * True when [player] cannot start a game until it has its folder: it gives setup
     * steps with none picked, see [Player.setupSteps]. Its folder ask is not skipped
     * for good, so the next start asks again.
     */
    private fun needsItsFolder(player: Player?): Boolean = player is SaveFolderHolder && try {
        player.setupSteps(null) != null
    } catch (_: Exception) {
        false
    }

    /**
     * How to give [player] the folder Foldcade hosts for its saves. Before a launch,
     * [pending] waits on it: Play without syncing starts only this launch, and Set up
     * opens the player. The next launch asks again while setup is incomplete.
     * False when another dialog is up, so it could not ask.
     */
    private fun askHostedSaves(player: HostedSaveFolder, pending: PendingBackendLaunch?): Boolean {
        val named = player as? Player ?: return false
        val shown = foldcade.shell.present(
            hostedSavesDialog(named.displayName, player.hostedFolderSteps, pending != null, hostScreen()),
        )
        if (shown) foldcade.hostedAsk = HostedAsk(named.id, pending)
        return shown
    }

    /** The same setup ask for a memory card as for a hosted save folder. */
    private fun askSaveSetup(player: Player, steps: String, pending: PendingBackendLaunch) {
        if (foldcade.shell.present(app.foldcade.language.saveSyncDialog(player.displayName, steps, hostScreen(), beforeLaunch = true))) {
            foldcade.hostedAsk = HostedAsk(player.id, pending)
        }
    }

    /** Opens the player [playerId] names on a screen of its own, as Home opens any app. */
    private fun openPlayerApp(playerId: String) {
        val packageName = foldcade.plugins.player(playerId)?.packageNames?.firstOrNull { name ->
            packageManager.getLaunchIntentForPackage(name) != null
        } ?: return
        openAppForSetup(packageName)
    }

    /** Y's Restart asks first: what the game has not saved is lost. */
    private fun askRestart(gameId: String?) {
        val id = gameId?.let { foldcade.shell.tileOf(it) } ?: return
        if (foldcade.runningOn(id) == null) return
        restartId = id
        foldcade.shell.present(restartGameDialog(hostScreen()))
    }

    /** Starts the game on tile [id] over: a fresh start that replaces the task it is open in. */
    private fun restartGame(id: String) {
        val running = foldcade.runningOn(id) ?: return
        val route = foldcade.shell.launchRoute(id) ?: return
        launchScreen = running.panel
        launchSubject = null
        // Still noted, so the fresh start replaces the task the game is open in.
        launchBackendGame(title = running.title, platformId = running.platformId, route = route, fresh = true)
    }

    /**
     * Y's Delete download. Not while a launch is under way or the game holds
     * either screen, since either may be reading that copy. The game does not
     * launch until the copy is gone.
     */
    private fun removeDownload(gameId: String?) {
        if (foldcade.launchWork.active) return
        val game = foldcade.shell.downloadFor(gameId) ?: return
        val libraryId = game.backendId
        val homeId = homeGameId(libraryId, game.remoteKey)
        if (foldcade.store.session.runs(homeId) || foldcade.store.session.runs(foldcade.shell.tileOf(homeId))) return
        // A game still open in its player behind Home is reading that copy too.
        if (foldcade.shell.isRunning(homeId)) return
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
     * [route]'s saves into the player's folder, see [LaunchSaves.prepare]. One save flow
     * runs at a time, so a second launch or a return read-back waits rather than racing this one.
     */
    private suspend fun prepareSaves(route: GameRoute, player: Player, local: LaunchTarget): PreparedLaunch =
        foldcade.saveFlow.withLock {
            LaunchSaves(
                foldcade.playerSaves,
                foldcade.plugins,
                awaitLoad = { foldcade.store.setSaveAwaitsLoad(it, true) },
                pickedFolder = { picked -> if (picked is SaveFolderHolder) foldcade.store.playerSaveFolder(picked.saveFolderId()) else null },
                onSpots = { game, picked, spots ->
                    withContext(Dispatchers.Main.immediate) { foldcade.shell.noteSaveReachable(game, picked.id, spots.isNotEmpty()) }
                },
            )
                .prepare(route, player, local)
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

    /**
     * True when the app was asked to start. [resume] brings back the task a game is
     * still open in. A fresh start in a player that still holds a game Foldcade
     * started replaces that game's task, so the player never runs two at once.
     */
    private fun startExternal(
        id: String,
        title: String,
        platformId: String?,
        occupiesBoth: Boolean,
        intent: Intent,
        noteStart: () -> Boolean = { false },
        resume: Boolean = false,
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
        val playerPackage = intent.component?.packageName
        val replacing = !resume && foldcade.runningGames.mayHold(playerPackage)
        if (replacing) intent.flags = replaceFlags(intent.flags)
        // Only a Home press after this start says the game was left open.
        foldcade.homeRecalledOn = null
        foldcade.externalPlay.open(foldcade.plays, id, title, displayId, playerPackage) {
            startOnDisplay(intent, displayId, multipleTask = !resume && !replacing)
            if (noteStart()) armRestart(intent, displayId)
        }
        // The game it replaced is gone. This one is noted once the start is in.
        if (replacing && playerPackage != null) foldcade.runningGames.forget(playerPackage)
        foldcade.noteExternalPlayerOpened()
        foldcade.notePlayScreen()
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
        val playId = foldcade.plays.start(game.id, game.title)
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
            val intent = foldcade.startIntent(player, ready.intent)
            foldcade.externalPlay.open(foldcade.plays, game.id, game.title, displayId, intent.packageName) {
                val options = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }
                val start = intent.toAndroidIntent(foldcade.rommFolderAuthority)
                startActivity(start, options.toBundle())
                if (foldcade.noteStarted(player, intent)) armRestart(start, displayId)
            }
            foldcade.noteExternalPlayerOpened()
            foldcade.notePlayScreen()
            if (game.libraryId != null) {
                rememberReturn(LibraryReturn(game.libraryId, apiGame, player, spots, ready.intent.dataUri))
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

    /** Several emulators can serve one platform. See [FoldcadeApp.playerFor]. */
    private fun playerFor(platformId: String): Player? = foldcade.playerFor(platformId)

    private val usageReconcile = UsageReconcile()

    /**
     * A week of usage events is too much for the frame that is resuming.
     * The query runs on the app scope, and the result is applied only if this
     * screen is still in front.
     */
    private fun reconcileUsage() {
        val startedAt = System.currentTimeMillis()
        usageReconcile.request(
            scope = foldcade.scope,
            deliver = Dispatchers.Main.immediate,
            alive = { resumed && !isDestroyed },
            read = {
                val access = UsageAccess(this@FoldcadeHomeActivity)
                val granted = access.granted()
                val samples = if (granted) {
                    access.samples(startedAt - UsageAccess.LOOKBACK_MS, startedAt)
                } else {
                    emptyList()
                }
                UsageSnapshot(granted, samples)
            },
            publish = { snapshot ->
                val now = System.currentTimeMillis()
                foldcade.plays.applyUsage(snapshot.granted, snapshot.samples, now)
                foldcade.shell.setUsageGranted(snapshot.granted)
                foldcade.shell.maybeOfferUsageAccess(
                    foldcade.plays.hasTrackedSession(),
                    snapshot.granted,
                    hostScreen(),
                )
            },
        )
    }

    private fun clearPendingLaunch() {
        pendingLaunch = null
        pendingBackend = null
        closeConfirmed = false
    }

    /** The generation this activity started, so its destruction does not cancel a later one. */
    private var ownedLaunch: Int? = null

    private fun beginLaunch(): Int = foldcade.launchWork.begin().also { ownedLaunch = it }

    /** Drops an in-flight launch so a late result cannot open a missing-player dialog. */
    private fun cancelLaunches() {
        foldcade.launchWork.cancel()
        clearPendingLaunch()
        retroArchRetry = null
        edenRetry = null
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
            DialogKind.ForgetArtKey -> if (effect.button == DialogButton.ForgetKey) forgetArtKey()
            // Its only button is Not now, which stops waiting for the code.
            DialogKind.PairHost -> foldcade.cancelMoonlightPairing()
            DialogKind.ForgetHost -> if (effect.button == DialogButton.ForgetHost) effect.subject?.let(foldcade::forgetMoonlightHost)
            DialogKind.RetroArchCores -> if (effect.button == DialogButton.ContinueGrant) {
                showRetroArchCores(retroArchRetry)
            } else {
                settleRetroArchCores()
            }
            DialogKind.EdenFolder -> if (effect.button == DialogButton.ContinueGrant) {
                openEdenFolder(fromOffer = true)
            } else {
                declineEdenFolder()
            }
            DialogKind.EdenRestart -> if (effect.button == DialogButton.ContinueGrant) {
                val pkg = edenRestartPackage ?: EDEN_PACKAGES.firstOrNull(::packageInstalled)
                edenRestartPackage = null
                if (pkg != null) openAndroidSetting(AndroidSetting.AppInfo, pkg)
            } else {
                // Not now only closes. The pending restart stays until Eden is force-stopped or the device restarts.
                edenRestartPackage = null
            }
            DialogKind.EdenWrongFolder -> if (effect.button == DialogButton.TryAgain) {
                openEdenFolder(fromOffer = true)
            } else {
                declineEdenFolder()
            }
            DialogKind.RetroArchSetup -> {
                retroArchRetry = null
                cancelLaunches()
                if (effect.button == DialogButton.OpenRetroArch) openRetroArch()
            }
            DialogKind.HostedSaves -> {
                val ask = foldcade.hostedAsk
                foldcade.hostedAsk = null
                val pending = ask?.pending
                val playerId = ask?.playerId
                when {
                    effect.button == DialogButton.OpenPlayer || effect.button == DialogButton.SetUpSaves -> {
                        cancelLaunches()
                        playerId?.let(::openPlayerApp)
                    }
                    pending != null -> {
                        launchScreen = pending.screen
                        launchBackendGame(pending.title, pending.platformId, pending.route, pending.fresh, saveSyncDeclinedFor = pending.playerId)
                    }
                }
            }
            DialogKind.RestartGame -> {
                val id = restartId
                restartId = null
                if (effect.button == DialogButton.Restart && id != null) restartGame(id)
            }
            DialogKind.SaveFolder -> if (pendingBackend != null) {
                val pending = pendingBackend ?: return
                if (effect.button == DialogButton.ChooseSaveFolder) {
                    folderPurpose = FolderPurpose.SaveOffer
                    settingsPlayerId = pending.playerId
                    folderPicker.launch(suggestedSaveFolder(foldcade.plugins.player(pending.playerId)))
                } else {
                    skipSaveFolderAndLaunch()
                }
            } else if (effect.button == DialogButton.ChooseSaveFolder) {
                folderPurpose = FolderPurpose.LaunchOffer
                folderPicker.launch(suggestedSaveFolder(playerForPendingLaunch()))
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
        validateRommToken(origin, token)
    }

    /** Check once, then store only if this is still the form that submitted the token. */
    private fun validateRommToken(origin: String, token: String) {
        val instanceId = RommCredentials.PLUGIN_ID
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
                        val before = foldcade.store.rommOrigin()
                        // Its tiles name the old server's ROM ids until the new one lists its games.
                        if (before == null || normalizeSetupOrigin(before) != origin) {
                            withContext(Dispatchers.Main.immediate) { foldcade.shell.noteServerChanged(instanceId) }
                        }
                        saveRommConnection(foldcade.store, foldcade.credentials, origin, token)
                        true
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
            listOfNotNull(foldcade.store.rommOrigin()?.let { SignedInBackend(RommCredentials.PLUGIN_ID, "RomM") }) +
                foldcade.shell.model.signedIn.filter { it.pluginId != RommCredentials.PLUGIN_ID },
        )
    }

    /**
     * Device code is the main path once an origin is on the form. Paste stays in the token field.
     * The code is for the saved server when one exists, and for the typed origin otherwise.
     */
    private fun beginRommDeviceCode() {
        deviceCodeJob?.cancel()
        val instanceId = RommCredentials.PLUGIN_ID
        val visit = foldcade.shell.beginDeviceCodeAttempt()
        deviceCodeJob = foldcade.scope.launch {
            var pairedRevision: Int? = null
            while (withContext(Dispatchers.Main.immediate) { foldcade.shell.deviceCodeVisitCurrent(visit) }) {
                val (revision, origin) = withContext(Dispatchers.Main.immediate) {
                    foldcade.shell.connectOriginRevision to normalizeSetupOrigin(foldcade.shell.model.connectOrigin)
                }
                val saved = foldcade.store.rommOrigin()?.let(::normalizeSetupOrigin)
                if (origin == null || revision == pairedRevision || (saved != null && saved != origin)) {
                    delay(200)
                    continue
                }
                delay(400)
                if (!withContext(Dispatchers.Main.immediate) { foldcade.shell.deviceCodeFormCurrent(visit, revision) }) continue
                pairedRevision = revision
                try {
                    foldcade.plugins.configure(instanceId, mapOf(ROMM_ORIGIN_KEY to origin))
                    val outcome = pollRommDeviceCode(instanceId, visit, revision)
                    withContext(Dispatchers.Main.immediate) {
                        if (foldcade.shell.deviceCodeFormCurrent(visit, revision)) {
                            when (outcome) {
                                is DeviceCodePoll.Approved -> validateRommToken(origin, outcome.token)
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
                    if (pluginId == RommCredentials.PLUGIN_ID) {
                        removeRommServer(foldcade.credentials, foldcade.store)
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
                    if (pluginId == RommCredentials.PLUGIN_ID && foldcade.plugins.library(pluginId) != null) {
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
                        if (pluginId == RommCredentials.PLUGIN_ID) foldcade.shell.dropLibrary(pluginId)
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
        folderPicker.launch(suggestedSaveFolder(foldcade.plugins.player(playerId)))
    }

    /** Where the save-folder picker opens: the player's own save directory, when it names one. */
    private fun suggestedSaveFolder(player: Player?): Uri? =
        (player as? SaveFolderHolder)?.suggestedSaveFolder?.let(Uri::parse)

    /**
     * A player handed a different game may quit instead of opening it, as
     * RetroArch 1.22 does, and Foldcade comes back. For a moment after such a
     * start, coming back starts the same intent again, which opens the game.
     */
    private fun armRestart(intent: Intent, displayId: Int) {
        foldcade.pendingRestart = PendingRestart(intent, displayId, SystemClock.uptimeMillis() + RESTART_WINDOW_MS)
    }

    /** True when this return was the player quitting to switch games, and the game started again. */
    private fun restartReplacedPlayer(): Boolean {
        val pending = foldcade.pendingRestart ?: return false
        foldcade.pendingRestart = null
        if (SystemClock.uptimeMillis() > pending.until) return false
        return try {
            startOnDisplay(pending.intent, pending.displayId)
            foldcade.externalPlay.notePlayerRestarted()
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** The launch to try again once the RetroArch cores offer is answered, and its generation. */
    private var retroArchRetry: Pair<Int, () -> Unit>? = null

    /** The launch to try again once the Eden folder offer is answered, and its generation. */
    private var edenRetry: Pair<Int, () -> Unit>? = null

    /**
     * The Eden package the restart dialog's Continue opens App info for.
     * Lost when this activity is recreated; Continue then uses the installed Eden.
     */
    private var edenRestartPackage: String? = null

    /**
     * What Eden needs before [player] starts. A grant is checked and filled in.
     * Without one, RomM is asked once, and only then. A RomM error starts the
     * game and does not count as the user declining. Returns true when the
     * launch stops: for the folder offer, or because Eden has to be started
     * again before it can see files Foldcade added.
     */
    private suspend fun edenHeld(
        player: Player,
        generation: Int,
        retry: () -> Unit,
        libraryId: String?,
        remoteKey: String?,
    ): Boolean {
        val packageName = installedEdenPackage(player, ::packageInstalled) ?: return false
        // Before Eden's documents provider. Opening it starts Eden and clears the stopped state.
        if (edenRestartBlocks(packageName, generation)) return true
        val holds = foldcade.edenFolderReader.granted(packageName) != null
        val gate = edenGate(holds, foldcade.store.edenFolderOffered()) {
            rommHasSwitchSetup(foldcade.rommFirmware.listSwitch())
        }
        when (gate) {
            EdenGate.Check -> {
                val progress = if (libraryId != null && remoteKey != null) {
                    TileDownload(foldcade.shell, libraryId, remoteKey)
                } else {
                    null
                }
                try {
                    foldcade.edenInstaller.ensure(
                        packageName,
                        onProgress = progress?.let { tile -> tile::onStep },
                        onAdded = { foldcade.noteEdenRestartPending(packageName) },
                    )
                } finally {
                    withContext(NonCancellable) { progress?.finish() }
                }
                // A launch replaced during the install still leaves the restart pending.
                if (foldcade.edenRestartPending()?.packageName != packageName) return false
                withContext(Dispatchers.Main.immediate) { presentEdenRestart(packageName, generation) }
                return true
            }
            EdenGate.Offer -> {
                withContext(Dispatchers.Main.immediate) {
                    if (!launchCurrent(generation)) return@withContext
                    edenRetry = generation to retry
                    foldcade.shell.present(edenFolderDialog(hostScreen()))
                }
                return true
            }
            is EdenGate.Ready -> {
                if (gate.markOffered) {
                    withContext(Dispatchers.Main.immediate) { foldcade.store.setEdenFolderOffered() }
                }
                return false
            }
        }
    }

    /**
     * True when [packageName] still has to restart, so this launch must not open Eden.
     * A reboot or a force stop clears the pending restart and the launch continues.
     * Showing the prompt, dismissing it, or finding another dialog already open leaves it pending.
     */
    private suspend fun edenRestartBlocks(packageName: String, generation: Int): Boolean {
        val pending = foldcade.edenRestartPending() ?: return false
        if (pending.packageName != packageName) return false
        val currentBoot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        val stopped = try {
            (packageManager.getApplicationInfo(packageName, 0).flags and ApplicationInfo.FLAG_STOPPED) != 0
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        if (!edenRestartStillNeeded(pending.bootCount, currentBoot, stopped)) {
            foldcade.clearEdenRestartPending()
            return false
        }
        withContext(Dispatchers.Main.immediate) { presentEdenRestart(packageName, generation) }
        return true
    }

    /**
     * Asks for Eden to be started again, for files added to [packageName].
     * False when [generation] is no longer the current launch. Another dialog
     * already open still holds this launch. The pending restart stays either way.
     */
    private fun presentEdenRestart(packageName: String, generation: Int): Boolean {
        if (!launchCurrent(generation)) return false
        if (!foldcade.shell.present(edenRestartDialog(hostScreen()))) return true
        edenRestartPackage = packageName
        return true
    }

    /** Android's picker, opened at the Eden package a launch uses. [fromOffer] is a launch, not Settings. */
    private fun openEdenFolder(fromOffer: Boolean) {
        val packageName = EDEN_PACKAGES.firstOrNull(::packageInstalled) ?: return
        if (!fromOffer) edenRetry = null
        folderPurpose = FolderPurpose.EdenFolder
        folderPicker.launch(EdenFolders.root(packageName))
    }

    /**
     * Not now, or the Eden picker closed from a launch or from Settings.
     * The offer is answered. A held launch starts; Settings has none.
     */
    private fun declineEdenFolder() {
        foldcade.store.setEdenFolderOffered()
        val retry = edenRetry
        edenRetry = null
        retryAfterEden(retry)
    }

    /** Starts the launch the Eden offer held, unless a newer launch or a cancel replaced it. */
    private fun retryAfterEden(retry: Pair<Int, () -> Unit>?) {
        edenRetry = null
        val (generation, launch) = retry ?: return
        if (launchCurrent(generation)) launch()
    }

    private fun useEdenFolder(uri: Uri) {
        // Derived again so a recreated activity still expects the package launches use.
        val expected = EDEN_PACKAGES.firstOrNull(::packageInstalled)
        val packageName = EdenFolders.packageOf(uri)
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val flags = read or write
        val root = packageName != null && packageName == expected && EdenFolders.isRoot(uri)
        val took = root && try {
            contentResolver.takePersistableUriPermission(uri, flags)
            true
        } catch (_: SecurityException) {
            false
        }
        val held = took && contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission && permission.isWritePermission
        }
        if (!held || packageName != expected) {
            // The held launch stays. Try again opens the picker; Not now starts it.
            if (took) releaseUnlessUsed(uri.toString(), flags)
            foldcade.refreshEdenSetting()
            foldcade.shell.present(edenWrongFolderDialog(hostScreen()))
            return
        }
        val retry = edenRetry
        edenRetry = null
        val previous = foldcade.store.edenFolder(packageName)
        foldcade.store.setEdenFolder(packageName, uri.toString())
        if (previous != null && previous != uri.toString()) releaseUnlessUsed(previous, flags)
        foldcade.store.setEdenFolderOffered()
        foldcade.refreshEdenSetting()
        retryAfterEden(retry)
    }

    /**
     * What RetroArch needs before [player] starts: storage access, and a core
     * for the system. Reads the cores again first, unless this launch already
     * did before placing saves, so a core installed a moment ago counts. Shows
     * what is missing and returns true when the launch stops. [retry] starts
     * the launch again after the cores offer.
     */
    private suspend fun retroArchHeld(
        player: RetroArchPlayer,
        generation: Int,
        readCores: Boolean = true,
        retry: () -> Unit,
    ): Boolean {
        val packageName = player.packageNames.firstOrNull(::packageInstalled) ?: return false
        if (readCores) foldcade.readRetroArchCores()
        val storage = try {
            retroArchStorage(packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS))
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
        val gate = retroArchGate(player, packageName, storage, foldcade.store.retroArchCoresOffered())
        if (gate == RetroArchGate.Ready) return false
        withContext(Dispatchers.Main.immediate) {
            if (!launchCurrent(generation)) return@withContext
            val screen = hostScreen()
            when (gate) {
                RetroArchGate.OfferCores -> {
                    retroArchRetry = generation to retry
                    foldcade.shell.present(retroArchCoresDialog(screen))
                }
                is RetroArchGate.MissingCore -> {
                    val platform = foldcade.plugins.platformDefinitions()
                        .firstOrNull { it.id == player.platformId }?.displayName ?: player.platformId
                    foldcade.shell.present(missingCoreDialog(platform, gate.core.libraryName, screen))
                }
                is RetroArchGate.Storage -> foldcade.shell.present(
                    if (gate.storage == RetroArchStorage.Never) retroArchStoreBuildDialog(screen) else retroArchStorageDialog(screen),
                )
                RetroArchGate.Ready -> Unit
            }
        }
        return true
    }

    /** Android's picker, opened at RetroArch's data folder. Choosing it lets Foldcade list the cores. */
    private fun showRetroArchCores(retry: Pair<Int, () -> Unit>?) {
        val packageName = foldcade.retroArchPlayer()?.cores?.activePackage()
        retroArchRetry = retry
        folderPurpose = FolderPurpose.RetroArchCores
        folderPicker.launch(packageName?.let(RetroArchCoreReader::dataFolder))
    }

    /** The offer was answered without a folder. Launches try the system's first core from now on. */
    private fun settleRetroArchCores() {
        foldcade.store.setRetroArchCoresOffered()
        retryAfterRetroArchCores(retroArchRetry)
    }

    /** Starts the launch the cores offer held, unless a newer launch or a cancel replaced it. */
    private fun retryAfterRetroArchCores(retry: Pair<Int, () -> Unit>?) {
        retroArchRetry = null
        val (generation, launch) = retry ?: return
        if (launchCurrent(generation)) launch()
    }

    private fun useRetroArchFolder(uri: Uri) {
        val retry = retroArchRetry
        retroArchRetry = null
        val packageName = RetroArchCoreReader.packageOf(uri)
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val granted = packageName != null && try {
            contentResolver.takePersistableUriPermission(uri, read)
            true
        } catch (_: SecurityException) {
            false
        }
        foldcade.scope.launch {
            // A folder without cores, such as one inside RetroArch's, would only ever read as none installed.
            val holdsCores = granted && foldcade.retroArchCoreReader.holdsCores(uri)
            withContext(Dispatchers.Main.immediate) {
                if (!holdsCores || packageName == null) {
                    if (granted) releaseUnlessUsed(uri.toString(), read)
                    cancelLaunches()
                    foldcade.shell.present(playerNotice(Copy.retroArchCoresTitle, Copy.retroArchWrongFolder, hostScreen()))
                    return@withContext
                }
                // The folder just chosen is the one read from now on. A grant a library or save folder uses stays.
                val previous = foldcade.store.retroArchCoresFolder(packageName)
                foldcade.store.setRetroArchCoresFolder(packageName, uri.toString())
                if (previous != null && previous != uri.toString()) releaseUnlessUsed(previous, read)
                foldcade.store.setRetroArchCoresOffered()
            }
            if (!holdsCores || packageName == null) return@launch
            foldcade.readRetroArchCores()
            withContext(Dispatchers.Main.immediate) { retryAfterRetroArchCores(retry) }
        }
    }

    private fun releaseUnlessUsed(uri: String, flags: Int) {
        if (foldcade.store.folderTreeInUse(uri)) return
        releaseGrant(Uri.parse(uri), flags)
    }

    private fun releaseGrant(uri: Uri, flags: Int) {
        try {
            contentResolver.releasePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            Unit
        }
    }

    /** RetroArch's own menu, on this screen, so the user can install a core or allow storage. */
    private fun openRetroArch() {
        val packageName = foldcade.retroArchPlayer()?.cores?.activePackage() ?: return
        openAppForSetup(packageName)
    }

    /** Opens [packageName] on the screen a single-screen app would take, for the user to set something up there. */
    private fun openAppForSetup(packageName: String) {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        val session = foldcade.store.session
        val here = displays.panelFor(this, session.defaultDisplayIsTop) ?: Panel.Top
        val panel = session.singleScreenTarget(here) ?: return
        val assignment = displays.assignment(session.defaultDisplayIsTop)
        val displayId = when (panel) {
            Panel.Top -> assignment.topDisplayId
            Panel.Bottom -> assignment.bottomDisplayId ?: return
        }
        val opened = foldcade.plugins.openInstalledApp(packageName)
        foldcade.music.onExternalLaunch()
        foldcade.store.place(panel, ExternalApp(opened.sessionId, occupiesBothDisplays = false))
        val options = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }
        startActivity(launch, options.toBundle())
    }

    private fun continueBackendSaveAsk(pending: PendingBackendLaunch, answer: SaveFolderAnswer) {
        when (val continuation = saveLaunchContinuation(pending.playerId, answer, needsItsFolder(foldcade.plugins.player(pending.playerId)))) {
            SaveLaunchContinuation.Cancel -> cancelLaunches()
            is SaveLaunchContinuation.Launch -> {
                launchScreen = pending.screen
                launchBackendGame(pending.title, pending.platformId, pending.route, pending.fresh, saveSyncDeclinedFor = continuation.declinedFor)
            }
        }
    }

    private fun skipSaveFolderAndLaunch(answer: SaveFolderAnswer = SaveFolderAnswer.PlayWithoutSyncing) {
        pendingBackend?.let { pending ->
            pendingBackend = null
            continueBackendSaveAsk(pending, answer)
            return
        }
        val index = pendingLaunch ?: return
        val player = playerForPendingLaunch() ?: return
        when (val continuation = saveLaunchContinuation(player.id, answer, needsItsFolder(player))) {
            SaveLaunchContinuation.Cancel -> cancelLaunches()
            is SaveLaunchContinuation.Launch -> launchGame(index, saveSyncDeclinedFor = continuation.declinedFor)
        }
    }

    private fun playerForPendingLaunch(): Player? {
        val index = pendingLaunch ?: return null
        val game = Shelf.games.getOrNull(index) ?: return null
        return game.platformId?.let(::playerFor)
    }

    /**
     * True when [uri] can be [player]'s save folder. A folder that holds none of what
     * its folder holds is not kept: with [ask], the save-folder ask comes back saying
     * so, and the launch it holds still waits; without, a notice says so.
     */
    private fun pickedSaveFolderFits(uri: Uri, player: Player?, ask: Boolean): Boolean {
        if (foldcade.playerSaves.looksLikeSaveFolder(player, uri.toString())) return true
        val markers = (player as? SaveFolderHolder)?.saveFolderMarkers.orEmpty()
        val name = player?.displayName.orEmpty()
        if (ask) {
            foldcade.shell.present(saveFolderDialog(
                hostScreen(), syncs = true, wrong = name to markers,
                playerName = player?.displayName, needed = needsItsFolder(player),
            ))
        } else {
            foldcade.shell.present(playerNotice(Copy.saveFolderTitle, wrongSaveFolderBody(name, markers), hostScreen()))
        }
        return false
    }

    private fun rememberSaveFolder(uri: Uri, player: Player?): Boolean {
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (!persistTreeGrant(write = true) { flags -> contentResolver.takePersistableUriPermission(uri, flags) }) {
            foldcade.shell.present(playerNotice(Copy.saveFolderTitle, Copy.folderGrantFailed, hostScreen()))
            return false
        }
        if (player is SaveFolderHolder) {
            val key = player.saveFolderId()
            val previous = foldcade.store.playerSaveFolder(key)
            // Every player that shares the folder gets it, as at startup.
            for (id in foldcade.plugins.playerIds()) {
                val sharing = foldcade.plugins.player(id) ?: continue
                if (sharing is SaveFolderHolder && sharing.saveFolderId() == key) sharing.bindSaveFolder(uri.toString())
            }
            foldcade.store.setPlayerSaveFolder(key, uri.toString())
            if (previous != null && previous != uri.toString()) {
                foldcade.releaseUnusedFolderGrant(previous, read or write)
            }
            foldcade.shell.refreshPlayerSaves()
            foldcade.refreshSaveSync()
        }
        return true
    }

    /** The steps for a memory card that is not syncing, and Open for the player that keeps it. */
    private fun showSaveSync(playerId: String) {
        val player = foldcade.plugins.player(playerId) ?: return
        val steps = foldcade.shell.model.playerSaves.firstOrNull { it.playerId == playerId }?.saveSyncSteps ?: return
        val shown = foldcade.shell.present(app.foldcade.language.saveSyncDialog(player.displayName, steps, hostScreen()))
        if (shown) foldcade.hostedAsk = HostedAsk(player.id, null)
    }

    /**
     * What [player] needs once before it starts, read off the main thread from the
     * folder the user picked for it, or asked with none when none is picked.
     * Null for a player that keeps no folder.
     */
    private suspend fun playerSetupSteps(player: Player): String? {
        if (player !is SaveFolderHolder) return null
        val folder = foldcade.store.playerSaveFolder(player.saveFolderId())
        return withContext(Dispatchers.IO) { foldcade.playerSaves.setupSteps(player, folder) }
    }

    /**
     * What [playerId] needs once before a game can start. [steps] is used when the
     * launch just read them; Settings uses the row's copy. Open starts the player.
     * Not now only closes.
     */
    private fun showPlayerSetup(playerId: String, steps: String? = null) {
        val player = foldcade.plugins.player(playerId) ?: return
        val body = steps ?: foldcade.shell.model.playerSaves.firstOrNull { it.playerId == playerId }?.setupSteps ?: return
        val shown = foldcade.shell.present(playerSetupDialog(player.displayName, body, hostScreen()))
        if (shown) foldcade.hostedAsk = HostedAsk(player.id, null)
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

    /** Bind the whole screen and its later effects to the menu's original game. */
    private fun openSaves(gameId: String) {
        if (foldcade.launchWork.active) return
        val route = foldcade.shell.savesMenuRoute(gameId) ?: return
        val owner = route.servers.firstOrNull { it == route.saves } ?: route.servers.firstOrNull() ?: return
        val player = foldcade.shell.savePlayer(route.play.platformId)
        val target = app.foldcade.language.SavePickerTarget(route.tileId, owner.backendId, owner.remoteKey, player?.id, ++foldcade.savePickerSerial)
        foldcade.savePickerRequest = SavePickerRequest(target, route, owner, player)
        foldcade.shell.presentSavePicker(app.foldcade.language.SavePickerState(
            target, owner.label, player?.displayName ?: app.foldcade.language.SavePickerCopy.unknown,
            foldcade.shell.saveHealthOf(gameId), ownSlots = player?.saveDeclarations(owner).orEmpty().map { it.slot },
        ))
        loadSavePicker(target)
    }

    private fun loadSavePicker(target: app.foldcade.language.SavePickerTarget) {
        val request = foldcade.savePickerRequest?.takeIf { it.target == target } ?: return
        foldcade.scope.launch {
            try {
                val listed = foldcade.plugins.serverSaves(request.owner.backendId, request.owner, request.player)
                val backups = foldcade.plugins.serverSaveBackups(request.owner.backendId, request.owner)
                val origins = foldcade.plugins.serverSaveOrigins(request.owner.backendId, request.owner)
                val saves = listed.map {
                    val origin = origins[it.id]?.let { detail -> app.foldcade.language.SavePickerOrigin(detail.client, detail.deviceName, detail.thisDevice) }
                    app.foldcade.language.SavePickerSave(it.id, it.slot, it.emulator, it.source, it.updatedAtMillis, it.sizeBytes, it.playing, it.screenshotUri, it.id in backups, origin)
                }
                withContext(Dispatchers.Main.immediate) {
                    foldcade.shell.updateSavePicker(target) { previous ->
                        val id = previous.focused?.id
                        val index = if (previous.index == 0) 0 else (saves.indexOfFirst { it.id == id } + 1).coerceAtLeast(0)
                        previous.copy(saves = saves, index = index, loading = false, error = false,
                            health = foldcade.shell.savePickerHealth(target), ownSlots = request.player?.saveDeclarations(request.owner).orEmpty().map { it.slot })
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                withContext(Dispatchers.Main.immediate) { foldcade.shell.updateSavePicker(target) { it.copy(loading = false, error = true) } }
            }
        }
    }

    private fun changeServerSave(target: app.foldcade.language.SavePickerTarget, saveId: String?, destination: app.foldcade.language.SaveDestination? = null) {
        val request = foldcade.savePickerRequest?.takeIf { it.target == target } ?: return
        if (foldcade.shell.model.savePicker?.target != target) return
        // An emulator on either display can still be writing its save; ask for Home first.
        if (foldcade.launchWork.active || Panel.entries.any { foldcade.store.session.externalOn(it) != null }) {
            foldcade.shell.updateSavePicker(target) { it.copy(busy = false, message = app.foldcade.language.SavePickerCopy.returnHome) }
            return
        }
        val installedPlayer = request.player?.takeIf { it.packageNames.any(::packageInstalled) }
        val needsPlayer = saveId != null && (destination == null || request.player?.saveDeclarations(request.owner).orEmpty().any { it.slot == destination.slot })
        if (needsPlayer && installedPlayer == null) {
            foldcade.shell.updateSavePicker(target) { it.copy(busy = false, message = app.foldcade.language.SavePickerCopy.installFirst) }
            return
        }
        foldcade.scope.launch {
            var success = false
            var failure = app.foldcade.language.SavePickerCopy.failed
            try {
                foldcade.saveFlow.withLock {
                    ServerSaveFlow(foldcade.playerSaves, foldcade.plugins, foldcade.store::saveAwaitsLoad, foldcade.store::setSaveAwaitsLoad)
                        .perform(request.copy(player = installedPlayer), saveId, destination)
                }
                success = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failure = serverSaveFailure(error)
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    foldcade.shell.updateSavePicker(target) { it.copy(busy = false, copying = null,
                        message = if (success) { if (saveId == null) app.foldcade.language.SavePickerCopy.synced else app.foldcade.language.SavePickerCopy.copied } else failure) }
                }
            }
            withContext(Dispatchers.Main.immediate) {
                if (success && saveId != null && destination == null && !isDestroyed && !isFinishing && foldcade.shell.model.savePicker?.target == target) {
                    foldcade.shell.presentSavePicker(null)
                    launchScreen = foldcade.runningOn(target.gameId)?.panel ?: Panel.Top
                    launchBackendGame(request.owner.label, request.route.play.platformId, request.route, fresh = true)
                } else if (success) loadSavePicker(target)
            }
        }
    }

    /** Shows the first conflict that is still waiting, unless a launch or a preview is up. */
    internal fun refreshSaveConflict() {
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
                    // Read the card again, and do not set one up: the player has just been holding it.
                    val spots = returnSpots(saves, pending)
                    withContext(Dispatchers.Main.immediate) { foldcade.shell.noteSaveReachable(pending.game, pending.player.id, spots.isNotEmpty()) }
                    val observed = saves.changedSince(pending.libraryId, pending.game, pending.player, spots)
                    // The library may finish its sync before reconcile returns. The notice waits for either.
                    withContext(Dispatchers.Main.immediate) { foldcade.expectSaveReturn(pending.libraryId, pending.game) }
                    var announced = true
                    try {
                        val result = foldcade.plugins.reconcile(
                            pending.libraryId,
                            pending.game,
                            playerForSaveSpots(pending.player, pending.game, spots),
                            observed,
                        )
                        withContext(Dispatchers.Main.immediate) {
                            announced = false
                            foldcade.noteSaveReturn(pending.libraryId, pending.game, result.outcome)
                        }
                    } finally {
                        // Taken back before the save flow is free, so the next hand-back's own announcement stays.
                        if (announced) {
                            withContext(NonCancellable + Dispatchers.Main.immediate) {
                                foldcade.dropSaveReturn(pending.libraryId, pending.game)
                            }
                        }
                    }
                    saves.noteHandedOver(pending.libraryId, pending.game, pending.player, spots, observed)
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

/** The player [askHostedSaves] asked about, and the library launch [pending] on the answer, if any. */
internal class HostedAsk(val playerId: String, val pending: PendingBackendLaunch?)

/**
 * Firmware download progress on one game's tile, the same pulse and fraction a game download uses.
 * A game that is already downloading keeps that begin and end. This only adds the fraction,
 * and clears a pulse it started itself so the tile does not stay on Downloading.
 */
private class TileDownload(
    private val shell: ShellController,
    private val libraryId: String,
    private val remoteKey: String,
) {
    private val main = Handler(Looper.getMainLooper())
    private var owned = false
    /** A report has been posted. That post is what claims the tile. */
    @Volatile private var claimed = false
    private var shownAt = 0L
    private var shown = -1L

    fun onStep(step: RomProgress) {
        val reported = when (step) {
            is RomProgress.Receiving -> {
                val total = step.total
                val percent = if (total != null && total > 0L) step.bytes * 100 / total else -1L
                val now = System.nanoTime()
                val last = total != null && step.bytes >= total
                // The first report claims the tile. The gap must not drop it.
                if (claimed && !last && (percent == shown || now - shownAt < PROGRESS_GAP_NS)) return
                shownAt = now
                shown = percent
                GameActivity.Downloading(step.bytes, total)
            }
            RomProgress.Checking -> GameActivity.Checking
        }
        claimed = true
        main.post {
            if (!owned && shell.beginDownload(libraryId, remoteKey)) owned = true
            shell.noteActivity(libraryId, remoteKey, reported, System.currentTimeMillis(), firmware = true)
        }
    }

    /** Waits until the queued progress has been applied, then drops a pulse this download started. */
    suspend fun finish() {
        val done = CompletableDeferred<Unit>()
        main.post {
            try {
                if (owned) {
                    shell.endDownload(libraryId, remoteKey)
                    shell.settleActivity(libraryId, remoteKey)
                    owned = false
                }
            } finally {
                done.complete(Unit)
            }
        }
        done.await()
    }

    private companion object {
        /** Same gap the RomM library uses between download reports. */
        const val PROGRESS_GAP_NS = 100_000_000L
    }
}

/** A library launch held while its player's save folder is asked for. */
internal class PendingBackendLaunch(
    val title: String,
    val platformId: String?,
    val route: GameRoute,
    val playerId: String,
    /** The screen A or X asked for, which a recreated Home would not know. */
    val screen: Panel,
    /** Restart asked for it, so the answer starts the game over rather than bringing it back. */
    val fresh: Boolean = false,
)

/** See [FoldcadeHomeActivity.armRestart]. */
class PendingRestart(val intent: Intent, val displayId: Int, val until: Long)

/** How long after a start a return counts as the player quitting to switch games. */
private const val RESTART_WINDOW_MS = 3_000L

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

internal data class LibraryReturn(
    val libraryId: String,
    val game: Game,
    val player: Player,
    val spots: List<SaveSpot> = emptyList(),
    /** The game file, so a return can find the card again without setting one up. */
    val targetUri: String? = null,
)

/**
 * The spots to read back. A player whose spots at launch came from a search is
 * looked up again, with the card left as it is, and that look wins when it finds
 * any. The spots stored at launch remain when that look finds nothing, so a game
 * file that cannot be read just now does not drop the saves. Every other player
 * keeps the spots stored at launch.
 */
internal fun returnSpots(saves: PlayerSaves, pending: LibraryReturn): List<SaveSpot> {
    val uri = pending.targetUri ?: return pending.spots
    if (pending.spots.none { it.search != null }) return pending.spots
    val fresh = try {
        saves.spots(pending.player, pending.game, LaunchTarget.ContentUri(uri), forLaunch = false)
    } catch (_: Exception) {
        return pending.spots
    }
    return fresh.ifEmpty { pending.spots }
}

/** One stored read-back per game. */
private val LibraryReturn.storeKey: String get() = "$libraryId\u0000${game.remoteKey}"

private fun storedReturnKey(text: String): String? = try {
    val json = JSONObject(text)
    "${json.getString("library")}\u0000${json.getString("key")}"
} catch (_: Exception) {
    null
}

internal fun LibraryReturn.encode(): String = JSONObject().apply {
    put("library", libraryId)
    put("backend", game.backendId)
    put("key", game.remoteKey)
    put("platform", game.platformId)
    put("availability", game.availability.name)
    put("label", game.label)
    put("player", player.id)
    targetUri?.let { put("target", it) }
    put(
        "spots",
        JSONArray(
            spots.map { spot ->
                JSONObject().apply {
                    put("slot", spot.slot)
                    put("folder", spot.folderUri)
                    put("file", spot.fileName)
                    if (spot.folders.isNotEmpty()) {
                        put("parts", JSONArray(spot.folders.map { part ->
                            JSONObject().apply {
                                put("name", part.name)
                                put("path", part.path)
                            }
                        }))
                    }
                    spot.search?.let { search ->
                        put("search", JSONObject().apply {
                            put("parent", search.parent)
                            put("contains", search.nameContains)
                        })
                    }
                }
            },
        ),
    )
}.toString()

internal fun decodeLibraryReturn(text: String, player: (String) -> Player?): LibraryReturn? {
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
            val parts = spot.optJSONArray("parts")
            val folders = (0 until (parts?.length() ?: 0)).map { part ->
                val folder = parts!!.getJSONObject(part)
                SaveFolderPart(folder.getString("name"), folder.getString("path"))
            }
            val search = spot.optJSONObject("search")?.let {
                SaveFolderSearch(it.getString("parent"), it.getString("contains"))
            }
            SaveSpot(spot.getString("slot"), spot.getString("folder"), spot.getString("file"), folders, search)
        },
        targetUri = json.optString("target", "").takeIf { it.isNotEmpty() },
    )
}


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
