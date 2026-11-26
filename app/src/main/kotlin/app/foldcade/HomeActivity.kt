package app.foldcade

import androidx.compose.ui.graphics.toArgb
import android.app.Activity
import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.ComponentName
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
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.focus.FocusRequester
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.lifecycleScope
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
import app.foldcade.language.settingsActivityScreen
import app.foldcade.language.MotionSpeed
import app.foldcade.language.AboutCopy
import app.foldcade.language.playScreen
import app.foldcade.language.SaveConflictAction
import app.foldcade.language.SaveConflictPrompt
import app.foldcade.language.SaveConflictSide
import app.foldcade.language.Copy
import app.foldcade.language.SignedInBackend
import app.foldcade.language.DialogButton
import app.foldcade.language.*
import app.foldcade.host.backup.*
import app.foldcade.language.PlayCopy
import app.foldcade.language.playHistoryDone
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
import app.foldcade.language.missingPlayerDialog
import app.foldcade.language.noFileDialog
import app.foldcade.language.noSharedPathDialog
import app.foldcade.language.playerNotice
import app.foldcade.language.PlayerPages
import app.foldcade.language.AndroidSetting
import app.foldcade.language.edenRestartDialog
import app.foldcade.language.blocksLaunch
import app.foldcade.language.isEdenGrant
import app.foldcade.language.isEdenSessionChange
import app.foldcade.language.canConfirmSaveLocation
import app.foldcade.language.hostedCardMismatch
import app.foldcade.language.armsX2OutsideCard
import app.foldcade.language.edenWrongFolderDialog
import app.foldcade.language.retroArchCoresDialog
import app.foldcade.language.retroArchStorageDialog
import app.foldcade.language.retroArchStoreBuildDialog
import app.foldcade.language.hostedSavesDialog
import app.foldcade.language.playerSetupDialog
import app.foldcade.language.saveFolderDialog
import app.foldcade.language.closeGameDialog
import app.foldcade.language.restartGameDialog
import app.foldcade.language.resumeGameDialog
import app.foldcade.language.PanelKeyActivity
import app.foldcade.language.PromptKey
import app.foldcade.language.interpretThorStyle
import app.foldcade.plugins.eden.EDEN_PACKAGES
import app.foldcade.plugins.armsx2.ArmsX2Player
import app.foldcade.plugins.aps3e.Aps3ePlayer
import app.foldcade.plugins.dolphin.DolphinPlayer
import app.foldcade.plugins.eden.EdenPlayer
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
        const val EXTRA_SHELF_PREVIEW = "foldcade.shelfPreview"
        const val EXTRA_SETTINGS_PREVIEW = "foldcade.settings"
        const val EXTRA_DIALOG_INDEX = "foldcade.dialogIndex"
        const val EXTRA_DIALOG_SCREEN = "foldcade.dialogScreen"
        const val EXTRA_SAVE_CONFLICT = "foldcade.saveConflict"
        const val STATE_FOLDER_PICKER = "foldcade.folderPicker"

        /** Set up saves was open, on this tile ("" for All set), so a Home Android recreates opens it again. */
        const val STATE_SAVE_SETUP = "foldcade.saveSetup"
    }

    private val displays by lazy { Displays(this) }
    private val foldcade by lazy { application as FoldcadeApp }
    private var resumed = false
    private var initialHomeRecallPending = false
    private var reopenedFromIntent = false
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
    private var folderGuide: app.foldcade.language.FolderGuide? = null
    /** The library folder Pick again stands in for: a different pick replaces it, so an empty source never lingers beside the new one. */
    private var folderReplacing: String? = null
    private var besideFixPickerId: String? = null
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
    private var pendingGameResume: (() -> Unit)? = null
    private var settingsPlayerId: String? = null
    private var libraryReturn: LibraryReturn? = null
    private var deviceCodeJob: Job? = null

    /** Settings › System › Backup › Play history › Export: where the file goes. */
    private val playExport = registerForActivityResult(ExportDocument()) { uri ->
        uri?.let(::exportPlayHistory)
    }

    /** Settings › System › Backup › Play history › Import: the file to add. */
    private val playImport = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importPlayHistory)
    }

    private val setupExport = registerForActivityResult(ExportDocument()) { uri ->
        if (uri != null) exportSetup(uri)
    }
    private val setupImport = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readSetup { contentResolver.openInputStream(uri) ?: throw java.io.IOException("No input") }
    }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        folderGuide = null
        foldcade.shell.showFolderGuide(null)
        val purpose = folderPurpose
        folderPurpose = FolderPurpose.Library
        val replacing = folderReplacing
        folderReplacing = null
        val besideFixId = besideFixPickerId
        besideFixPickerId = null
        if (uri == null) {
            when (purpose) {
                FolderPurpose.Firmware -> settingsPlayerId = null
                FolderPurpose.LaunchOffer -> skipSaveFolderAndLaunch(SaveFolderAnswer.PickerCancelled)
                FolderPurpose.SaveOffer -> {
                    settingsPlayerId = null
                    skipSaveFolderAndLaunch(SaveFolderAnswer.PickerCancelled)
                }
                FolderPurpose.Settings -> {
                    val playerId = settingsPlayerId
                    settingsPlayerId = null
                    foldcade.scope.launch(Dispatchers.IO) {
                        foldcade.awaitPlugins()
                        val player = playerId?.let(foldcade.plugins::player)
                        foldcade.saveProtection.incidents().filter {
                            it.playerKey == player?.saveFolderId() && it.repair == RepairState.Binding &&
                                if (besideFixId == null) !it.besideGame else it.id == besideFixId
                        }
                            .forEach { foldcade.saveProtection.put(it.copy(repair = app.foldcade.language.RepairState.Guided)) }
                        foldcade.refreshSaveProtection(player)
                    }
                }
                FolderPurpose.Library -> Unit
                FolderPurpose.RetroArchCores -> settleRetroArchCores()
                FolderPurpose.EdenFolder -> declineEdenFolder()
                FolderPurpose.EdenSaveOffer -> skipSaveFolderAndLaunch(SaveFolderAnswer.PickerCancelled)
            }
            return@registerForActivityResult
        }
        foldcade.scope.launch(Dispatchers.Main.immediate) {
            // A process that died behind the picker is still loading: the saved player id needs its player.
            foldcade.awaitPlugins()
            when (purpose) {
                FolderPurpose.RetroArchCores -> useRetroArchFolder(uri)
                FolderPurpose.EdenFolder -> useEdenFolder(uri)
                FolderPurpose.EdenSaveOffer -> useEdenFolder(uri, saveOffer = true)
                FolderPurpose.Library -> {
                    if (persistTreeGrant(write = true) { flags ->
                        contentResolver.takePersistableUriPermission(uri, flags)
                    }) {
                        // Pick again on a source with nothing in it: the new folder takes its place.
                        replacing?.takeIf { it != uri.toString() }?.let { old ->
                            // Named before the old folder leaves, so the shelf lands on this one even if the library already read it.
                            foldcade.shell.focusFolderAfterPick(uri.toString())
                            foldcade.forgetFolder(old)
                        }
                        foldcade.useFolder(uri)
                    } else {
                        foldcade.shell.present(playerNotice(Copy.folderGrantTitle, Copy.folderGrantFailed, hostScreen()))
                    }
                }
                FolderPurpose.LaunchOffer -> {
                    // Not the player's folder: ask again, and the game waits.
                    val player = playerForPendingLaunch()
                    val folder = pickedSaveFolder(uri, player, ask = true) ?: return@launch
                    if (foldcade.saveFlow.withLock { rememberSaveFolder(folder, player) }) {
                        pendingLaunch?.let { launchGame(it) }
                    } else {
                        cancelLaunches()
                    }
                }
                FolderPurpose.SaveOffer -> {
                    val player = settingsPlayerId?.let { foldcade.plugins.player(it) }
                    settingsPlayerId = null
                    val folder = pickedSaveFolder(uri, player, ask = pendingBackend != null) ?: return@launch
                    // A recreated screen lost the game. The folder is kept, and the next press starts it.
                    val pending = pendingBackend
                    pendingBackend = null
                    if (foldcade.saveFlow.withLock { rememberSaveFolder(folder, player) } && pending != null) {
                        continueBackendSaveAsk(pending, SaveFolderAnswer.Chosen)
                    } else {
                        cancelLaunches()
                    }
                }
                FolderPurpose.Firmware -> {
                    val player = settingsPlayerId?.let(foldcade.plugins::player)
                    settingsPlayerId = null
                    val docs = AndroidSaveDocuments(contentResolver)
                    val destination = withContext(Dispatchers.IO) {
                        pickedFirmwareFolder(uri.toString(), AndroidSaveFolderFinder(foldcade)::isDirectory) { root ->
                            if (docs.createFolder(root, "system")) docs.children(root, "")?.firstOrNull { it.isFolder && it.name == "system" }?.uri else null
                        }
                    }
                    if (destination == null) {
                        if (player != null) foldcade.shell.showDialog(app.foldcade.language.firmwareFolderDialog(
                            player.id, app.foldcade.language.FirmwareCopy.wrongFolder, hostScreen()))
                        return@launch
                    }
                    if (!persistTreeGrant(write = true) { flags -> contentResolver.takePersistableUriPermission(uri, flags) }) {
                        foldcade.shell.present(playerNotice(Copy.saveFolderTitle, Copy.folderGrantFailed, hostScreen()))
                    } else if (player != null) {
                        val previous = foldcade.store.playerFirmwareFolder("retroarch")
                        foldcade.store.setPlayerFirmwareFolder("retroarch", destination)
                        foldcade.dismissNotice("firmware-folder:${player.saveFolderId()}")
                        if (previous != null && previous != destination) foldcade.releaseUnusedFolderGrant(previous,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    }
                }
                FolderPurpose.Settings -> {
                    val player = settingsPlayerId?.let { foldcade.plugins.player(it) }
                    settingsPlayerId = null
                    if (besideFixId != null) {
                        val incident = foldcade.saveProtection.incident(besideFixId)?.takeIf { it.besideGame && it.playerKey == player?.saveFolderId() }
                        if (incident != null && player != null) {
                            val folder = pickedSaveFolder(uri, player, ask = false) ?: return@launch
                            confirmBesideSaveFolder(player, incident, folder)
                        }
                        return@launch
                    }
                    val incident = foldcade.saveProtection.incidents().firstOrNull {
                        it.playerKey == player?.saveFolderId() && it.repair == RepairState.Binding && !it.besideGame
                    }
                    if (player is EdenPlayer && incident?.isEdenGrant == true) {
                        allowEdenSaveFolder(player, incident, uri)
                        return@launch
                    }
                    if (incident != null && player != null) {
                        val folder = pickedSaveFolder(uri, player, ask = false) ?: return@launch
                        val scopedRetroArch = player is RetroArchPlayer && incident.gameIds.isNotEmpty()
                        val repairInPlace = scopedRetroArch || incident.armsX2OutsideCard || incident.dolphinCustomCard
                        val expected = if (repairInPlace) incident.bound?.identity else incident.live?.identity
                        val oldOwnProvider = incident.bound?.identity?.substringBefore(':') == DolphinPlayer.PROVIDER
                        val requiresDolphinProvider = player is DolphinPlayer && oldOwnProvider &&
                            (incident.kind == IncidentKind.LostAccess || incident.dolphinCustomCard)
                        if (!repairFolderMatches(expected, storageIdentity(folder.toString(), packageName)) ||
                            requiresDolphinProvider && providerSaveRootPackage(player, folder.toString()) == null) {
                            val copy = app.foldcade.language.SaveProtectionCopy
                            foldcade.shell.noteSaveFix(when {
                                requiresDolphinProvider -> copy.dolphinOwnFolder
                                repairInPlace -> copy.chooseThere(incident.bound?.label.orEmpty())
                                else -> copy.wrongPick(incident.playerName, incident.live?.label.orEmpty())
                            })
                            return@launch
                        }
                        if (!persistTreeGrant(write = true) { flags -> contentResolver.takePersistableUriPermission(Uri.parse(folderGrantUri(folder.toString())), flags) }) {
                            foldcade.shell.noteSaveFix(Copy.folderGrantFailed)
                            return@launch
                        }
                        if (player is DolphinPlayer && incident.dolphinCustomCard) {
                            try { confirmDolphinCard(player, incident, folder) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { foldcade.shell.noteSaveFix(Copy.saveNotPlacedBody) }
                            return@launch
                        }
                        try {
                            foldcade.saveFlow.withLock {
                                val knownGame = if (scopedRetroArch && incident.retroArchAlternate() != null) withContext(Dispatchers.Main.immediate) {
                                    incident.gameIds.firstNotNullOfOrNull(foldcade.shell::saveGame)
                                } else null
                                val preserved = withContext(Dispatchers.IO) {
                                    if (player is ArmsX2Player && incident.armsX2OutsideCard) {
                                        val config = player.cardConfig(foldcade.playerSaves.folderAccess(player, folder.toString(), allowWrite = false))
                                        if (!config.nativeReadable || config.directory == null) {
                                            val copy = app.foldcade.language.SaveProtectionCopy
                                            val detail = if (config.nativeReadable) copy.armsX2CardOutside else copy.armsX2ConfigUnavailableDetail
                                            withContext(Dispatchers.Main.immediate) { foldcade.shell.noteSaveFix(detail) }
                                            return@withContext null
                                        }
                                    }
                                    foldcade.saveProtection.put(incident.copy(recoveryRequired = true))
                                    val copies = foldcade.cachedSaveSpots(player).filterKeys(incident::appliesTo)
                                        .mapValues { (_, spots) -> spots.map { it.copy(folderUri = folder.toString()) } }.toMutableMap()
                                    val route = foldcade.hostedAsk?.pending?.route ?: foldcade.savePickerRequest?.route
                                    if (route != null && incident.appliesTo(route.saves)) {
                                        val target = foldcade.plugins.ensureLocal(route.play.backendId, route.play)
                                        copies[route.saves] = foldcade.playerSaves.probeSpots(player, route.saves, target, folder.toString())
                                    }
                                    if (player is RetroArchPlayer && incident.retroArchAlternate() != null) {
                                        val game = copies.keys.firstOrNull() ?: knownGame
                                        if (game != null) {
                                            val gamePlayer = foldcade.plugins.player(RetroArchPlayer.ID_PREFIX + game.platformId) as? RetroArchPlayer ?: player
                                            if (foldcade.saveProbes.recaptureAlternate(gamePlayer, game, incident)) foldcade.saveIncidentCopies.keep(game, gamePlayer)
                                        }
                                    }
                                    // Keep a durable copy before binding, then mark its hash once binding succeeds.
                                    copies.map { (game, spots) ->
                                        val observed = foldcade.saveIncidentCopies.stage(game, player, spots, incident, mark = false)
                                        foldcade.saveIncidentCopies.keep(game, player)
                                        Triple(game, spots, observed)
                                    }
                                } ?: return@withLock
                                // Fence every game before the new binding can become visible, including after process death.
                                if (!scopedRetroArch) withContext(Dispatchers.IO) { foldcade.saveProtection.rebound(player.saveFolderId()) }
                                if (rememberSaveFolder(folder, player)) withContext(Dispatchers.IO) {
                                    preserved.forEach { (game, spots, observed) ->
                                        foldcade.playerSaves.noteHandedOver(game.backendId, game, player, spots, observed)
                                        if (observed.slots.isNotEmpty()) foldcade.saveProtection.handled(player.saveFolderId(), game)
                                        foldcade.refreshSaveReceipt(game, player, spots)
                                    }
                                    foldcade.saveProtection.incidents().filter {
                                        it.playerKey == incident.playerKey && it.kind != app.foldcade.language.IncidentKind.MaybeNewSave &&
                                            (it.id == incident.id || incident.gameIds.isEmpty() && it.gameIds.isEmpty())
                                    }
                                        .forEach { foldcade.saveProtection.put(it.copy(repair = app.foldcade.language.RepairState.Done, recoveryRequired = false)) }
                                }
                            }
                            foldcade.refreshSaveProtection(player)
                            // The finished page reads offline or not as of now: offline it only closes.
                            foldcade.shell.updateSaveFix(incident.id) { it.copy(offline = !foldcade.hasValidatedNetwork()) }
                        } catch (cancelled: CancellationException) { throw cancelled
                        } catch (_: Exception) { foldcade.shell.noteSaveFix(Copy.saveNotPlacedBody) }
                    } else pickedSaveFolder(uri, player, ask = false)?.let { folder ->
                        foldcade.saveFlow.withLock { rememberSaveFolder(folder, player) }
                    }
                }
            }
            foldcade.refreshFoundSaveFolders()
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
        return foldcade.store.session.takesKeys(panel)
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
        foldcade.componentUpdates.playerInput()
        if (foldcade.shell.model.folderGuide != null) return
        // The companion's keys drive Themes or Licenses, keeping the parent Settings inactive.
        val inputPanel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop)
        val pageActivity = foldcade.settingsPageActivity
        if (inputPanel != null && foldcade.store.session.pageKeyTarget(inputPanel) != null &&
            pageActivity?.lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true
        ) {
            pageActivity.dispatchPanelMeaning(meaning, repeatCount)
            return
        }
        if (foldcade.shell.swallowsHomeReturnRepeat(repeatCount)) return
        if (meaning == app.foldcade.language.Meaning.Back && repeatCount > 0 && foldcade.shell.stoppedDownload != null) return
        if (commandCenterKey(meaning)) return
        if (app.foldcade.language.topMenuKeyBlocked(meaning, foldcade.shell.homeKeys(), foldcade.store.session.islandsOpen())) return
        if (app.foldcade.language.cancelsPendingLaunch(
                meaning,
                dialogOpen = foldcade.shell.model.dialog != null,
                conflictOpen = foldcade.shell.model.saveConflict != null,
            )
        ) {
            val stopped = foldcade.launchWork.pendingSubject?.takeIf { foldcade.shell.isDownloading(it) }
            cancelLaunches()
            if (meaning == app.foldcade.language.Meaning.Back && stopped != null) {
                val notice = foldcade.shell.noteDownloadStopped(stopped)
                foldcade.scope.launch(Dispatchers.Main.immediate) {
                    delay(app.foldcade.language.Motion.durationSyncMark.toLong())
                    foldcade.shell.clearDownloadStopped(notice)
                }
                // This B belongs to the transfer; it must not also leave the folder.
                return
            }
        }
        foldcade.music.duckForThemeSound(meaning)
        val panel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop) ?: return
        dispatch(foldcade.shell.onMeaning(meaning, inputScreen(panel), repeatCount, inputWindow))
    }

    /** The command center on this screen, or the grid opened from it, takes the key first. Set up saves covers it and keeps its keys. */
    private fun commandCenterKey(meaning: app.foldcade.language.Meaning): Boolean {
        if (foldcade.shell.model.saveSetup != null) return false
        val panel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop) ?: return false
        // A page or dialog that moved onto this screen covers the command center and keeps its keys
        // (see FoldcadeApp's browse for displaced pages): B must close a dialog that has no Not now.
        if (foldcade.shell.model.pageOn(panel.screen())) return false
        return foldcade.commandCenter.onKey(panel, meaning, foldcade.shell::atRest)
    }

    fun dispatch(effect: Effect?) {
        when (effect) {
            is Effect.Launch -> {
                // A second A/X keeps the original request, including its screen and bloom subject.
                val focused = foldcade.shell.focusedGame()?.id?.let(foldcade.shell::tileOf)
                if (focused != null && foldcade.launchWork.pending(focused)) return
                // Play on's screen, or in a one-screen layout whose other panel is powered off, the lit one.
                launchScreen = foldcade.shell.model.playScreen().panel().let { if (effect.elsewhere) it.flip() else it }
                launchSubject = foldcade.shell.focusedRunningApp()
                bloomPress = BloomPress(foldcade.shell.focusedGame()?.id, SystemClock.uptimeMillis())
                if (foldcade.shell.model.libraryGrid) launchLibraryGame(effect.index) else launchFocused()
                // A launch from Find has read its game; the grid is what to come back to.
                foldcade.shell.closeFindAfterLaunch()
            }
            is Effect.OpenPlatform -> foldcade.openPlatform(effect.index)
            Effect.LeavePlatform -> foldcade.leavePlatform()
            Effect.TryAgain -> foldcade.retryLibrary()
            Effect.AddFolder -> pickLibraryFolder()
            is Effect.PickFolderAgain -> {
                folderPurpose = FolderPurpose.Library
                folderReplacing = effect.uri
                launchFolderPicker(libraryFolderStart(effect.uri)?.let(Uri::parse)) // OpenDocumentTree supplies EXTRA_INITIAL_URI.
            }
            Effect.SyncLibrary -> foldcade.libraryResync.syncNow()
            is Effect.ChoosePlayerSave -> choosePlayerSave(effect.playerId)
            is Effect.SetUpHostedSaves -> (foldcade.plugins.player(effect.playerId) as? HostedSaveFolder)?.let { player ->
                askHostedSaves(player, pending = null)
            }
            is Effect.ShowSaveSync -> showSaveSync(effect.playerId)
            is Effect.ShowPlayerSetup -> showPlayerSetup(effect.playerId)
            is Effect.ChooseFirmwareFolder -> foldcade.plugins.player(effect.playerId)?.let(::openFirmwareFolder)
            is Effect.ShowThorSetup -> showThorSetup(effect.playerId)
            is Effect.OpenPlayer -> {
                cancelLaunches()
                openPlayerApp(effect.playerId)
            }
            Effect.ShowRetroArchCores -> showRetroArchCores(retry = null)
            Effect.ShowEdenFolder -> openEdenFolder(fromOffer = false)
            Effect.Undo, is Effect.ShowHidden, is Effect.DeleteShortcut -> Unit
            // The shell applies Y's tile actions and A on a text field itself and hands back null; these never arrive.
            is Effect.SelectTile, is Effect.Bulk, is Effect.ChooseArt, Effect.MoveTile, is Effect.ShowOnHome, is Effect.ToggleCollection, is Effect.NewCollection, is Effect.RecentOnHome,
            Effect.OpenFolders, is Effect.FolderOnHome,
            is Effect.MoveApp, is Effect.Hide,
            Effect.RenameFolder, Effect.ChooseFolderIcon, Effect.RemoveTile, Effect.Tidy, Effect.GoHome, Effect.EditField,
            is Effect.SortFolder, is Effect.SetGamePlayerChoice, is Effect.SetSystemPlayerChoice, is Effect.SetGameFan,
            -> Unit
            // The model already shows the new value; the plugin's reply replaces it.
            is Effect.SetFan -> foldcade.deviceControl.setFan(effect.tier)
            is Effect.SetStickLights -> foldcade.deviceControl.setLights(effect.mode)
            is Effect.SetBottomBrightness -> foldcade.deviceControl.setBottomBrightness(app.foldcade.language.bottomBrightnessLevel(effect.position))
            is Effect.CheckDevice -> foldcade.deviceControl.check()
            is Effect.RemoveDownload -> removeDownload(effect.gameId)
            is Effect.RestartGame -> askRestart(effect.gameId)
            is Effect.CloseGame -> askCloseGame(effect.gameId)
            is Effect.MoveGame -> moveGame(effect.gameId)
            is Effect.RemovePlaySession -> foldcade.plays.remove(effect.sessionId)
            // The shell lists the day's sessions, and a game's sources, itself.
            is Effect.OpenPlayDay, is Effect.OpenPlayGame -> Unit
            is Effect.MovePlayTime -> if (!foldcade.movePlay(effect.from, effect.to)) foldcade.shell.notePlayNotMoved(effect.from)
            is Effect.UndoPlayMove -> foldcade.undoPlayMove(effect.from, effect.to)
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
            Effect.UseFoundRomm -> foldcade.shell.model.connectFoundOrigin?.let {
                beginRommFromPhone(it)
            }
            is Effect.SetPluginGrant -> foldcade.setPluginGrant(effect.packageName, effect.scope, effect.on)
            is Effect.SetPluginRouting -> foldcade.setPluginRouting(effect.packageName, effect.on)
            is Effect.CheckPluginRelease -> foldcade.apkPluginCatalogs[effect.packageName]?.checkRelease(effect.onlyIfStale)
            is Effect.InstallPlugin -> {
                // An update ready notice is the player asking to install now. Without install access the
                // catalog could only say so on a page R1 isn't showing, so A goes to Android's switch at once
                // and the install carries on when Home returns with it allowed.
                val fromNotice = effect.staged && !packageManager.canRequestPackageInstalls()
                if (fromNotice) foldcade.installAfterAccess = effect.packageName
                if (fromNotice || foldcade.shell.model.pluginCatalog(effect.packageName).status == app.foldcade.language.PluginCatalogStatus.NeedsPermission &&
                    !packageManager.canRequestPackageInstalls()) {
                    val panel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop)
                    foldcade.store.handoffAndroidSetting(panel) {
                        try {
                            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                            true
                        } catch (_: ActivityNotFoundException) { false }
                    }
                } else foldcade.apkPluginCatalogs[effect.packageName]?.install(useStaged = effect.staged)
            }
            Effect.OpenArtKey -> foldcade.shell.openArtKey()
            Effect.SaveArtKey -> foldcade.saveArtKey()
            is Effect.ForgetCredentials -> forget(effect.pluginId)
            is Effect.ForgetFolder -> foldcade.forgetFolder(effect.uri)
            is Effect.PairStreamHost -> foldcade.pairMoonlightHost(effect.key, hostScreen())
            is Effect.DialogChoice -> onDialog(effect)
            Effect.RequestHome -> requestHome()
            is Effect.OpenAndroidSetting -> {
                // Wi-Fi can be a translucent Android panel: Home pauses without stopping, so
                // noteCovered alone cannot give its companion the command center.
                val panel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop)
                foldcade.store.handoffAndroidSetting(panel) { openAndroidSetting(effect.setting) }
            }
            Effect.OpenAllFilesAccess -> openAllFilesAccess()
            is Effect.OpenWhatsNew -> {
                foldcade.scope.launch {
                    val update = foldcade.componentUpdates.update(effect.packageName)
                    if (update != null) withContext(Dispatchers.Main.immediate) {
                        // From R1 no Settings page is open, and the key came from the grid's screen: a full page
                        // still opens where the layout puts pages, as Settings itself would.
                        val model = foldcade.shell.model
                        openSettingsPage(WhatsNewActivity::class.java, model.settings?.screen ?: model.layout.pageScreen) { putUpdate(it, update) }
                        foldcade.dismissNotice(app.foldcade.language.UpdateCopy.updatedPrefix + effect.packageName, byUser = true)
                    }
                }
            }
            Effect.OpenLicenses -> openSettingsPage(LicensesActivity::class.java)
            Effect.OpenThemes -> openSettingsPage(ThemesActivity::class.java)
            Effect.OpenBackup -> {
                val screen = foldcade.shell.model.backupDialogScreen(hostScreen())
                foldcade.scope.launch {
                    val action = foldcade.setupBackups.undoAction()
                    withContext(Dispatchers.Main.immediate) {
                        foldcade.shell.present(backupDialog(screen, undo = action != null, redo = action == RestoreAction.Redo))
                    }
                }
            }
            Effect.CopyDebugInfo -> copyDebugInfo()
            Effect.DismissButtonLabels -> Unit
            is Effect.OpenNotice -> openNotice(effect.id)
            is Effect.DismissNotice -> foldcade.dismissNotice(effect.id, byUser = true)
            Effect.ClearNotices -> foldcade.clearNotices()
            is Effect.OpenSaves -> openSaves(effect.gameId)
            is Effect.LoadSavePicker -> loadSavePicker(effect.target)
            is Effect.OpenSaveFix -> openSaveFix(effect.incidentId)
            is Effect.ChooseRepairSave -> chooseRepairSave(effect.incidentId)
            is Effect.RetrySaveRepair -> retrySaveRepair(effect.incidentId)
            is Effect.PickFixFolder -> pickFixFolder(effect.incidentId)
            is Effect.FinishSaveFix -> finishSaveFix(effect.incidentId, effect.openSaves)
            Effect.CancelSaveFix -> { foldcade.hostedAsk = null; cancelLaunches() }
            is Effect.ShowSaveCheck -> showSaveCheck(effect.playerId)
            is Effect.ConfirmSaveLocation -> foldcade.saveProtection.incidents().firstOrNull { it.id == effect.incidentId }?.let {
                foldcade.shell.showDialog(app.foldcade.language.saveFolderFineDialog(it, foldcade.shell.model.layout.gridScreen))
            }
            is Effect.FixSavePicker -> dispatch(foldcade.shell.savePickerFix(effect.target))
            is Effect.SyncSavePicker -> changeServerSave(effect.target, null)
            is Effect.UseSavePlayer -> useSavePlayer(effect.target, effect.choice)
            is Effect.PlayPickedSave -> changeServerSave(effect.target, effect.saveId)
            is Effect.CopyPickedSave -> changeServerSave(effect.target, effect.saveId, effect.destination)
            is Effect.ResolveSaveConflict -> resolveSaveConflict(effect)
            null -> Unit
        }
    }

    /** "Foldcade play history 2026-10-31.zip", in the device's own calendar. */
    private fun playHistoryFileName(): String = "Foldcade play history ${java.time.LocalDate.now()}.zip"

    /** Android's file picker, or a word when this device has none. */
    private fun startPicker(otherwise: String, backup: Boolean = false, launch: () -> Unit) {
        try {
            launch()
        } catch (missing: android.content.ActivityNotFoundException) {
            Log.w("FoldcadePlay", "No document picker", missing)
            foldcade.shell.present(if (backup) backupDone(otherwise, foldcade.shell.model.backupDialogScreen(hostScreen())) else playHistoryDone(otherwise, foldcade.shell.model.backupDialogScreen(hostScreen())))
        }
    }

    /** Writes the whole play history to [uri], off the main thread, then says how it went. */
    private fun exportPlayHistory(uri: Uri) {
        val screen = foldcade.shell.model.backupDialogScreen(hostScreen())
        foldcade.scope.launch {
            val message = try {
                val out = contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("no stream for $uri")
                out.use { foldcade.plays.export(it) }
                exportResult(uri)
            } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("FoldcadePlay", "Play history not exported", failure)
                PlayCopy.notSaved
            }
            withContext(Dispatchers.Main.immediate) { foldcade.shell.present(playHistoryDone(message, screen)) }
        }
    }

    /** Reads [uri] off the main thread and adds it to the play history on it, then says what it added. */
    private fun importPlayHistory(uri: Uri) {
        val screen = foldcade.shell.model.backupDialogScreen(hostScreen())
        foldcade.scope.launch {
            var refusal = PlayCopy.unreadable
            val read = try {
                val input = contentResolver.openInputStream(uri) ?: throw java.io.IOException("no stream for $uri")
                input.use { app.foldcade.host.play.PlayArchive.read(it) }.also { foldcade.plays.art.adopt(it.art) }
            } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                throw cancelled
            } catch (refused: app.foldcade.host.play.PlayArchiveException) {
                Log.w("FoldcadePlay", "Not a play history", refused)
                refusal = PlayCopy.notHistory
                null
            } catch (failure: Exception) {
                Log.w("FoldcadePlay", "Play history not imported", failure)
                null
            }
            withContext(Dispatchers.Main.immediate) {
                val message = read?.let { foldcade.plays.merge(it) }?.let { merged ->
                    when {
                        merged.sessions > 0 -> PlayCopy.imported(merged.sessions)
                        merged.changes > 0 -> PlayCopy.updated
                        else -> PlayCopy.nothingNew
                    }
                } ?: refusal
                foldcade.shell.present(playHistoryDone(message, screen))
            }
        }
    }

    private fun exportSetup(uri: Uri) {
        val screen = foldcade.shell.model.backupDialogScreen(hostScreen())
        foldcade.scope.launch {
            val message = try {
                foldcade.backupWork.withLock {
                    val work = foldcade.setupBackups.workspace("export")
                    try {
                        val snapshot = foldcade.setupSnapshot(work)
                        val output = contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("No output")
                        BackupCopy.saved(output.use { SetupArchive.write(it, snapshot) }.skippedThemes, exportResult(uri))
                    } finally { work.deleteRecursively() }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                Log.w("FoldcadeBackup", "Backup not saved", failure)
                BackupCopy.notSaved
            }
            withContext(Dispatchers.Main.immediate) { foldcade.shell.present(backupDone(message, screen)) }
        }
    }

    private fun readSetup(action: RestoreAction = RestoreAction.Restore, open: () -> java.io.InputStream) {
        val screen = foldcade.shell.model.backupDialogScreen(hostScreen())
        foldcade.scope.launch {
            var refusal = BackupCopy.unreadable
            val backup = try {
                foldcade.backupWork.withLock { open().use(foldcade.setupBackups::read) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: SetupArchiveException) {
                refusal = when (failure.reason) {
                    BackupRefusal.Foreign -> BackupCopy.foreign
                    BackupRefusal.Newer -> BackupCopy.newer
                    BackupRefusal.Unreadable -> BackupCopy.unreadable
                }
                null
            } catch (failure: Exception) {
                Log.w("FoldcadeBackup", "Backup not read", failure)
                null
            }
            withContext(Dispatchers.Main.immediate) {
                val previous = foldcade.pendingSetupRestore
                foldcade.scope.launch { foldcade.setupBackups.discard(previous) }
                foldcade.pendingSetupRestore = backup
                foldcade.pendingSetupAction = action
                val dialog = if (backup == null) backupDone(refusal, screen) else {
                    val available = foldcade.shell.catalogNow().filterIsInstance<HomeItem>().mapTo(mutableSetOf()) { it.id }
                    val model = foldcade.shell.model
                    val (date, detail) = backupPreview(backup, available, foldcade.store.rommOrigin(), model.themeIds.zip(model.themes).toMap())
                    restoreDialog(date, detail, screen, action)
                }
                foldcade.shell.present(dialog)
            }
        }
    }

    private fun restoreSetup(backup: SetupBackup, action: RestoreAction) {
        val screen = foldcade.shell.model.backupDialogScreen(hostScreen())
        foldcade.scope.launch {
            try {
                foldcade.backupWork.withLock {
                    val work = foldcade.setupBackups.workspace("before")
                    try {
                        foldcade.saveFlow.withLock { foldcade.setupBackups.stage(backup, foldcade.setupSnapshot(work, includeThemes = false), action) }
                    } finally { work.deleteRecursively(); foldcade.setupBackups.discard(backup) }
                }
                withContext(Dispatchers.Main.immediate) { foldcade.restart(this@FoldcadeHomeActivity) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                Log.w("FoldcadeBackup", "Restore not staged", failure)
                withContext(Dispatchers.Main.immediate) { foldcade.shell.present(backupDone(BackupCopy.restoreFailed, screen)) }
            }
        }
    }

    private fun showRestoredSetup() {
        if (foldcade.restoreNoticeShown || !foldcade.setupBackups.hasNotice()) return
        foldcade.restoreNoticeShown = true
        val screen = foldcade.shell.model.backupDialogScreen(hostScreen())
        foldcade.scope.launch {
            if (foldcade.setupBackups.failed.isFile || foldcade.setupBackups.failurePending) {
                withContext(Dispatchers.Main.immediate) {
                    snapshotFlow { foldcade.shell.model.dialog == null }.first { it }
                    foldcade.shell.present(backupDone(BackupCopy.restoreUnfinished, screen).copy(subject = "restore-failed"))
                }
                return@launch
            }
            foldcade.awaitPlugins()
            val todo = foldcade.setupBackups.result.readLines()
            val signedIn = readRommRecovery(foldcade.store.restoreCredentials(foldcade.credentials), foldcade.store.rommOrigin()).backends.isNotEmpty()
            val lines = buildList {
                if ("romm" in todo && !signedIn) add(BackupCopy.signIn)
                todo.firstOrNull { it.startsWith("folders:") }?.let { add(BackupCopy.folders(it.removePrefix("folders:"))) }
                todo.firstOrNull { it.startsWith("saves:") }?.let { row ->
                    val players = foldcade.plugins.playerIds().mapNotNull(foldcade.plugins::player)
                    val names = row.removePrefix("saves:").split(", ").mapNotNull { id -> players.firstOrNull { it.id == id || it.saveFolderId() == id }?.displayName }.distinct()
                    if (names.isNotEmpty()) add(BackupCopy.saves(names.joinToString(", ")))
                }
                if ("computers" in todo && storedComputersNeedPairing(foldcade.store, runCatching { GameStreamIdentity.existing()?.fingerprint }.getOrNull())) add(BackupCopy.pair)
                if ("cores" in todo) add(BackupCopy.cores)
                if ("eden" in todo) add(BackupCopy.eden)
            }
            // The before-copy offers Redo only once an Undo was what ran.
            val undone = foldcade.setupBackups.undoAction() == RestoreAction.Redo
            withContext(Dispatchers.Main.immediate) {
                snapshotFlow { foldcade.shell.model.dialog == null }.first { it }
                foldcade.shell.present(backupDone(lines.joinToString("\n"), screen, restored = true,
                    library = todo.any { it.startsWith("folders:") }, undone = undone).copy(subject = "restored"))
            }
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
            FolderPickerState(folderPurpose, pendingLaunch, settingsPlayerId, folderGuide, folderReplacing, besideFixPickerId).encode(),
        )
        foldcade.shell.model.saveSetup?.let { outState.putString(STATE_SAVE_SETUP, it.focus.orEmpty()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        decodeFolderPickerState(savedInstanceState?.getString(STATE_FOLDER_PICKER)).let { restored ->
            folderPurpose = restored.purpose
            pendingLaunch = restored.pendingLaunch
            settingsPlayerId = restored.settingsPlayerId
            folderGuide = restored.guide
            folderReplacing = restored.replacing
            besideFixPickerId = restored.besideFixId
        }
        foldcade.homeCreated(this)
        folderGuide?.let(foldcade.shell::showFolderGuide)
        // The process may have died behind Android's picker or an emulator. A live one still has the page.
        savedInstanceState?.getString(STATE_SAVE_SETUP)?.let { focus ->
            foldcade.shell.restoreSaveSetup(focus.ifEmpty { null }, hostScreen())
        }
        // Secondary Home may be created behind a running emulator. A Home intent
        // must count as leaving it paused only when this activity actually resumes.
        if (launchesCompanion) acceptHome(intent)
        else initialHomeRecallPending = savedInstanceState == null && isAndroidHomeRecall(intent.action, intent.categories ?: emptySet())
        acceptShelf(intent)
        acceptFolderLibrary(intent)
        val roleManager = getSystemService(RoleManager::class.java)
        val held = roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        foldcade.shell.setHomeRoleHeld(held)
        if (launchesCompanion) showRestoredSetup()
        foldcade.onSaveConflicts = { refreshSaveConflict() }
        foldcade.onNetworkBack = { foldcade.shell.model.savePicker?.takeIf { it.error }?.let { retrySavePicker(it.target) } }
        applyDialogPreview(intent)
        applySaveConflictPreview(intent)
        applyShelfPreview(intent)
        applySettingsPreview(intent)
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
        if (presentationRecoveryWorks(Build.VERSION.SDK_INT)) recoveryWindow = PanelRecoveryWindow(this, root, panel)
        hideSystemBars()
    }

    /** The chosen theme's background, for windows drawn before Compose is. */
    internal fun themeBackgroundArgb(): Int =
        foldcade.paintFor(foldcade.shell.model.themeIndex).theme.background.toArgb()

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) foldcade.componentUpdates.playerInput()
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        foldcade.componentUpdates.playerInput()
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        perfMark("resume ${javaClass.simpleName}")
        super.onResume()
        val reopened = reopenedFromIntent
        reopenedFromIntent = false
        if (initialHomeRecallPending) {
            initialHomeRecallPending = false
            acceptHome(intent)
        }
        // onPause marked this window unfocused. A pause that kept focus, as Home or a new
        // intent to this screen does, brings no focus change to mark it focused again,
        // and a hold to delete would wait for one.
        foldcade.shell.inputFocus(inputWindow, hasWindowFocus())
        if (restartReplacedPlayer()) return
        // A core installed in RetroArch counts for the order of players. Its launch reads them again anyway.
        foldcade.refreshRetroArchCoresOnReturn()
        // A player may have been pointed at the folder Foldcade hosts for its saves while Home was away.
        foldcade.refreshHostedFolders()
        foldcade.shell.setAllFilesGranted(AllFilesAccess(this).granted())
        foldcade.shell.setAllFilesPage(false)
        // Android's install-unknown-apps page may have been answered, or a plugin installed meanwhile.
        foldcade.apkPluginCatalogs.values.forEach { it.reassess() }
        // Back from Android's install switch, asked for by an update ready notice: allowed, that install goes on.
        foldcade.installAfterAccess?.let { packageName ->
            foldcade.installAfterAccess = null
            if (packageManager.canRequestPackageInstalls()) foldcade.apkPluginCatalogs[packageName]?.install(useStaged = true)
        }
        // Android's all-files page may have been answered, or a card put in: look for ROM folders again.
        foldcade.refreshRomRoots()
        foldcade.refreshEdenSetting()
        foldcade.shell.refreshPlayerSaves()
        val displayId = display?.displayId
        val pending = libraryReturn
        val leftPlayer = foldcade.externalPlay.packageName
        val returned = displayId != null && foldcade.externalPlay.endIfDisplayHome(foldcade.plays, displayId)
        // A companion resume (lid, Move) can have consumed the pause while the game was still open.
        foldcade.refreshSavesOnReturn(returnedFromGame = returned)
        val homeRecalled = foldcade.homeRecalledOn == displayId
        val leftRun = foldcade.runningGames.forPackage(leftPlayer)
        if (leftPlayer != null && forgetsRunOnReturn(
                returned = returned,
                homeRecalledOnThisDisplay = homeRecalled,
                holdsHomeRole = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME),
                replacesInPlayer = leftRun?.replacesInPlayer == true,
                reopenedFromIntent = reopened,
                hasActivityGrant = leftRun?.activityGrant != null,
            )
        ) {
            foldcade.runningGames.forget(leftPlayer)
        }
        forgetClosedGameActivities(leftRun.takeIf { returned })
        if (!homeRecalled) {
            val leftApp = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop)
                ?.let { foldcade.store.session.externalOn(it)?.id }
            if (leftApp?.startsWith("android:") == true) foldcade.runningGames.forgetCompanion(leftApp.removePrefix("android:"))
        }
        if (homeRecalled) foldcade.homeRecalledOn = null
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
        foldcade.resumeSaveRepairs()
        // Back in front where the game opened: its art leaves both screens, including one a
        // presentation covered, which never stopped.
        displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop)?.let { panel ->
            if (foldcade.launchBlooms.current?.gamePanels?.contains(panel) == true) foldcade.launchBlooms.retire()
        }
        // Foldcade in front on this panel means the game there has left. A launcher-icon
        // start is not a Home recall, so without this a Foldcade that is not the default
        // Home keeps the panel for the game and ignores every key.
        var reclaimedPanel = false
        displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop)?.let { panel ->
            reclaimedPanel = foldcade.store.session.surfaceOn(panel) == null
            val returnedId = foldcade.store.session.externalOn(panel)?.id
            // Browsing the grid beside the game: a game that quits by itself leaves the cursor where the user has it.
            val browsing = foldcade.store.session.browsing != null
            foldcade.store.update { it.foldcadeResumed(panel) }
            if (returned || (reclaimedPanel && foldcade.store.session.externalOn(panel) == null)) {
                if (browsing) foldcade.shell.forgetGameLaunch() else foldcade.shell.noteHomeReturned(returnedId)
            }
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
        foldcade.refreshGameNativeShelf()
        foldcade.refreshFolderQuietly()
        foldcade.refreshBrightness()
        foldcade.brightness.refreshOther()
        foldcade.refreshNoticeAccess()
        // No game left open ends any game's fan override, and the dark panel goes dark again.
        foldcade.deviceControl.onHomeResumed()
        reconcileUsage()
    }

    override fun onStop() {
        super.onStop()
        // Whatever covers this screen now, a game or not, its launch art has nowhere left to show.
        displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop)?.let(foldcade.launchBlooms::covered)
        // A Home created behind another app must not keep that old recall until the app quits.
        initialHomeRecallPending = false
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
        foldcade.noteHomePaused()
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
        if (!resumed) reopenedFromIntent = true
        initialHomeRecallPending = false
        setIntent(intent)
        acceptHome(intent)
        acceptShelf(intent)
        acceptFolderLibrary(intent)
        applyDialogPreview(intent)
        applySaveConflictPreview(intent)
        applyShelfPreview(intent)
        applySettingsPreview(intent)
        // Home relaunches this activity before a debug capture intent arrives.
        applyIslandPreview(intent, replace = true)
    }

    private fun applyDialogPreview(intent: Intent) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val kind = intent.getStringExtra(EXTRA_DIALOG) ?: return
        // Without an index the dialog opens where it would for real.
        val index = intent.getIntExtra(EXTRA_DIALOG_INDEX, -1)
        val screen = if (intent.getStringExtra(EXTRA_DIALOG_SCREEN) == "top") {
            HostScreen.Top
        } else {
            HostScreen.Bottom
        }
        foldcade.shell.showPreviewDialog(kind, index, screen)
    }

    /** Debug builds: `--ei foldcade.shelfPreview 16` fills Settings' shelves with stand-ins, 0 clears them. */
    private fun applyShelfPreview(intent: Intent) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        if (!intent.hasExtra(EXTRA_SHELF_PREVIEW)) return
        foldcade.shell.previewShelf(intent.getIntExtra(EXTRA_SHELF_PREVIEW, 0))
    }

    /**
     * Debug builds: `--es foldcade.settings plugins` opens Settings at a category, by its title or
     * name, and `plugins/<package>` at that plugin's page, so a device check needs no key path.
     */
    private fun applySettingsPreview(intent: Intent) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val target = intent.getStringExtra(EXTRA_SETTINGS_PREVIEW) ?: return
        foldcade.shell.previewSettings(target.substringBefore('/'), target.substringAfter('/', "").ifEmpty { null })?.let(::dispatch)
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
     * to its own level, with nothing to remember or restore. [dark], the screen a
     * one-screen layout leaves black, goes to the lowest backlight there is.
     * Where the other panel follows the default display's backlight (LineageOS),
     * the default display keeps its level: dimming it would dim the game beside it.
     */
    fun applyCommandCenterDim(dimmed: Boolean, dark: Boolean = false) {
        val leadsOtherPanel = display?.displayId == Display.DEFAULT_DISPLAY && foldcade.brightness.panelsShareBacklight
        val level = when {
            leadsOtherPanel -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            dark -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
            dimmed -> commandCenterDimLevel()
            else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
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
        val returnedId = foldcade.store.session.externalOn(panel)?.id
        if (surface == Surface.Picker || surface == Surface.Hero) foldcade.shell.goHome(fromRecall = true)
        foldcade.store.update { it.home(panel) }
        foldcade.shell.noteHomeReturned(returnedId)
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

    /** A visible early library must not allow a player to bypass startup save preparation. */
    private fun waitForSavePreparation(then: () -> Unit): Boolean {
        if (foldcade.savePreparationReady) return false
        val generation = beginLaunch()
        foldcade.launchWork.job = foldcade.scope.launch {
            foldcade.awaitPlugins()
            withContext(Dispatchers.Main.immediate) {
                if (!launchCurrent(generation)) return@withContext
                foldcade.launchWork.job = null
                then()
            }
        }
        return true
    }

    private fun launchGame(index: Int, saveSyncDeclinedFor: String? = null, foldersChecked: Boolean = false, coresChecked: Boolean = false) {
        val game = Shelf.games.getOrNull(index) ?: return
        if ((foldcade.shell.activity[game.id] as? app.foldcade.language.TileActivity.Downloading)?.core != null) return
        if (waitForSavePreparation {
            val currentIndex = Shelf.games.indexOfFirst { it.id == game.id }
            if (currentIndex >= 0) launchGame(currentIndex, saveSyncDeclinedFor, foldersChecked, coresChecked)
        }) return
        if (game.emptyShelfHint) return
        if (game.libraryId != null && game.remoteKey != null && resumeRunning(game.id, game.title, game.platformId) {
                val apiGame = shelfGame(game)
                val route = foldcade.shell.launchRoute(game.id, pressed = apiGame) ?: GameRoute(game.id, apiGame, apiGame)
                launchBackendGame(game.title, game.platformId, route, fresh = true)
            }) return
        if (!coresChecked && game.platformId?.let { foldcade.mayOpenRetroArch(game.id, it) } == true) {
            val generation = beginLaunch()
            foldcade.launchWork.job = foldcade.scope.launch {
                foldcade.readRetroArchCores()
                withContext(Dispatchers.Main.immediate) {
                    if (!launchCurrent(generation)) return@withContext
                    foldcade.launchWork.job = null
                    launchGame(index, saveSyncDeclinedFor, foldersChecked, coresChecked = true)
                }
            }
            return
        }
        val resolution = game.platformId?.let { foldcade.playerResolution(game.id, it) }
        val player = resolution?.player
        if (player == null) {
            if (game.platformId != null) {
                val generation = beginLaunch()
                pendingLaunch = index
                presentMissing(resolution?.skipped?.firstOrNull()?.label ?: "Player", generation)
                return
            }
            launchStandIn(game)
            return
        }
        // Only a player that would ask for its folder looks first, so other launches start at once.
        if (!foldersChecked && player is SaveFolderHolder && !player.hasSaveFolder()) {
            // A library game already preparing keeps going, as before.
            if (game.libraryId != null && game.remoteKey != null && foldcade.launchWork.active) return
            val generation = beginLaunch()
            foldcade.launchWork.job = foldcade.scope.launch {
                foldcade.findPlayerSaveFolders()
                withContext(Dispatchers.Main.immediate) {
                    if (!launchCurrent(generation)) return@withContext
                    foldcade.launchWork.job = null
                    launchGame(index, saveSyncDeclinedFor, foldersChecked = true, coresChecked = coresChecked)
                }
            }
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
                var notPlaced: SaveNotPlaced? = null
                if (installedEdenPackage(player, ::packageInstalled) != null &&
                    edenHeld(player, generation, libraryId, remoteKey)) return@launch
                val prepared = try {
                    val local = foldcade.plugins.ensureLocal(libraryId, apiGame)
                    withContext(Dispatchers.Main.immediate) { foldcade.shell.noteOnDevice(libraryId, remoteKey) }
                    val chosenPackage = saveProbePackage(player, apiGame, local, installedPackages(player.packageNames), foldcade.sharedPaths::target)
                    prepareSaves(GameRoute(game.id, apiGame, apiGame), player, local, saveSyncDeclinedFor, chosenPackage)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (fatal: VirtualMachineError) {
                    throw fatal
                } catch (failure: SaveNotPlaced) {
                    notPlaced = failure
                    null
                } catch (_: Exception) {
                    null
                }
                withContext(Dispatchers.Main.immediate) {
                    if (!launchCurrent(generation) || pendingLaunch != index) return@withContext
                    notPlaced?.let { failure ->
                        val pending = unresolvedSaveHold(failure) { PendingBackendLaunch(game.title, apiGame.platformId, GameRoute(game.id, apiGame, apiGame), player.id, launchScreen, false) }
                        showSaveNotPlaced(game.title, pending, protection = failure as? SaveProtectionStop, needsEdenUser = (failure as? UnresolvedSaveNotPlaced)?.needsEdenUser == true, incompatible = (failure as? UnresolvedSaveNotPlaced)?.incompatible == true)
                    }
                    if (prepared == null) return@withContext
                    finishLaunch(index, game, player, prepared.target, apiGame, generation, prepared.spots, baseline = prepared.baseline,
                        edenSession = prepared.edenSession, coresRead = true, edenChecked = true, saveSyncDeclinedFor = saveSyncDeclinedFor)
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
            coresRead = coresChecked,
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
        baseline: app.foldcade.language.ProbeSnapshot? = null,
        edenSession: EdenSaveSession? = null,
        retroArchChecked: Boolean = false,
        coresRead: Boolean = false,
        edenChecked: Boolean = false,
        saveSyncDeclinedFor: String? = null,
    ) {
        if (!launchCurrent(generation)) return
        if (player is RetroArchPlayer && !retroArchChecked) {
            foldcade.launchWork.job = foldcade.scope.launch {
                if (retroArchHeld(player, generation, game.id, readCores = !coresRead) {
                    Shelf.games.indexOfFirst { it.id == game.id }.takeIf { it >= 0 }?.let { launchGame(it, saveSyncDeclinedFor) }
                }) return@launch
                withContext(Dispatchers.Main.immediate) {
                    finishLaunch(
                        index,
                        game,
                        player,
                        target,
                        apiGame,
                        generation,
                        spots,
                        baseline = baseline,
                        edenSession = edenSession,
                        retroArchChecked = true,
                        coresRead = coresRead,
                        edenChecked = edenChecked,
                        saveSyncDeclinedFor = saveSyncDeclinedFor,
                    )
                }
            }
            return
        }
        if (!edenChecked && installedEdenPackage(player, ::packageInstalled) != null) {
            foldcade.launchWork.job = foldcade.scope.launch {
                if (edenHeld(player, generation, game.libraryId, game.remoteKey)) return@launch
                withContext(Dispatchers.Main.immediate) {
                    finishLaunch(
                        index,
                        game,
                        player,
                        target,
                        apiGame,
                        generation,
                        spots,
                        baseline = baseline,
                        edenSession = edenSession,
                        retroArchChecked = true,
                        coresRead = coresRead,
                        edenChecked = true,
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
                LaunchBlock.CloseFirst -> foldcade.shell.present(closeBothPanelDialog(hostScreen(), runningBothPanelTitle()))
                LaunchBlock.MissingPlayer -> presentMissing(game.platformId?.let { foldcade.playerResolution(game.id, it).skipped.firstOrNull()?.label } ?: decision.playerName, generation)
                LaunchBlock.NoLocalFile -> foldcade.shell.present(noFileDialog(hostScreen()))
                LaunchBlock.SaveFolder -> foldcade.shell.present(
                    if (needsItsFolder(player)) {
                        saveFolderDialog(
                            hostScreen(),
                            playerName = player.displayName,
                            opensThere = foldcade.saveFolderOpensThere(player),
                            needed = true,
                        )
                    } else {
                        saveFolderDialog(hostScreen())
                    },
                )
                LaunchBlock.NoSharedPath -> foldcade.shell.present(noSharedPathDialog(decision.playerName, hostScreen()))
            }
            is PlayerLaunch.Ready -> {
                foldcade.launchWork.job = foldcade.scope.launch {
                    if (target != null && foldcade.launchWork.prepareFiles(
                        generation, apiGame,
                        saves = { PreparedLaunch(target, spots, apiGame) },
                        firmware = { firmwareTarget, backendId -> prepareFirmwareFiles(player, apiGame, firmwareTarget, generation, backendId) },
                    ) == null) return@launch
                    val steps = playerSetupSteps(player)
                    whenLit({ if (steps == null) startPanels(decision.occupiesBothDisplays, primary = decision.startDisplay == StartDisplay.Primary) else emptySet() }) {
                        withContext(Dispatchers.Main.immediate) {
                            if (!launchCurrent(generation)) return@withContext
                            if (steps != null) {
                                showPlayerSetup(player.id, steps)
                                return@withContext
                            }
                            clearPendingLaunch()
                            startPlayer(game, decision, apiGame, player, generation, spots, saveSyncForLaunch(player, saveSyncDeclinedFor), baseline, target?.saveReadUri(), edenSession)
                        }
                    }
                }
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
     * Same top or bottom placement as any other single-screen launch. One of [BOTH_SCREEN_APPS]
     * opens on the top screen and takes both, as a 3DS game does.
     * [PluginHost.openInstalledApp] does not read a save folder, so a missing one does not block this.
     */
    /**
     * The panels a start is about to take: both for a both-screen app, the first display's for a
     * player that always starts there, else the screen it asked for, or the free one.
     */
    private fun startPanels(both: Boolean, primary: Boolean = false, requested: Panel = launchScreen): Set<Panel> = when {
        both -> Panel.entries.toSet()
        primary -> setOf(Panel.Top)
        else -> setOfNotNull(foldcade.store.session.singleScreenTarget(requested))
    }

    /**
     * A device plugin powers a one-screen layout's dark panel off. A start that takes it waits, a
     * bounded moment, for it to light, so the game never opens on a panel that is off. The panel
     * stays wanted until [start] returns. Without such a plugin, [start] runs at once.
     */
    private suspend fun <T> whenLit(panels: () -> Set<Panel>, start: suspend () -> T): T {
        val taking = withContext(Dispatchers.Main.immediate) { panels().map { it.screen() }.toSet() }
        if (!foldcade.deviceControl.mustLight(taking)) return start()
        // While it waits, a start that needs no panel lit supersedes it ([afterLit]).
        val job = currentCoroutineContext()[Job]
        withContext(Dispatchers.Main.immediate) { foldcade.launchWork.lighting = job }
        try {
            return foldcade.deviceControl.whenLit(taking, start)
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                if (foldcade.launchWork.lighting === job) foldcade.launchWork.lighting = null
            }
        }
    }

    /**
     * A start begun on the main thread: [start] at once, as without a device plugin, or once its
     * panel is lit. The wait is the launch's own job, so B, a cancel or a newer launch drops it.
     */
    private fun afterLit(panels: Set<Panel>, subject: String? = null, start: () -> Unit) {
        val taking = panels.map { it.screen() }.toSet()
        if (!foldcade.deviceControl.mustLight(taking)) {
            // An older start still waiting for its panel is superseded by this one, as a newer launch always is.
            // Only then: without a device plugin no start ever waits, and nothing else is cancelled.
            if (foldcade.launchWork.waitingToLight) cancelLaunches()
            return start()
        }
        val generation = beginLaunch(subject)
        val job = foldcade.scope.launch(Dispatchers.Main.immediate) {
            try {
                foldcade.deviceControl.whenLit(taking) {
                    if (launchCurrent(generation) && !isDestroyed && !isFinishing) start()
                }
            } finally {
                if (foldcade.launchWork.lighting === coroutineContext[Job]) foldcade.launchWork.lighting = null
            }
        }
        foldcade.launchWork.job = job
        foldcade.launchWork.lighting = job
    }

    private fun launchInstalled(game: ShelfGame) =
        afterLit(startPanels(game.occupiesBothDisplays), game.id) { launchInstalledNow(game) }

    private fun launchInstalledNow(game: ShelfGame) {
        val pkg = game.androidPackage ?: return
        val opened = foldcade.plugins.openInstalledApp(pkg)
        val launch = packageManager.getLaunchIntentForPackage(pkg) ?: return
        val external = ExternalApp(opened.sessionId, game.occupiesBothDisplays)
        val session = foldcade.store.session
        val panel = if (external.occupiesBothDisplays) Panel.Top else session.singleScreenTarget(launchScreen) ?: return
        val assignment = displays.assignment(session.defaultDisplayIsTop)
        val displayId = when (panel) {
            Panel.Top -> assignment.topDisplayId
            Panel.Bottom -> assignment.bottomDisplayId ?: return
        }
        foldcade.music.onExternalLaunch()
        // The player opened by its own icon is the player's to steer now, and its game may
        // close without Foldcade seeing it, so that game is no longer held as paused.
        val beside = if (external.occupiesBothDisplays) null else session.externalOn(panel.flip())?.id?.let(foldcade::runningOn)?.packageName
        val running = foldcade.shell.runningApp(game)
        foldcade.commandCenter.note(external.id, running)
        val still = bloomLaunch(running, displayId, external.occupiesBothDisplays)
        foldcade.shell.noteGameLaunched(external.id)
        foldcade.store.place(panel, external)
        val options = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }
        if (still) bloomStart(launch)
        try {
            startActivity(launch, options.toBundle())
        } catch (failure: RuntimeException) {
            foldcade.launchBlooms.cancel()
            foldcade.shell.forgetGameLaunch()
            throw failure
        }
        foldcade.runningGames.noteApp(GameCompanion(pkg, game.title, panel), beside,
            setOf(packageName, "app.foldcade"))
        foldcade.noteRepairAppOpened(pkg)
        foldcade.runningGames.forget(pkg)
    }

    /**
     * The screen the launch now starting asked for: Play on's for A, the other for X. A
     * newer launch replaces it, and an older one still running is dropped by its
     * generation check before it places anything.
     */
    private var launchScreen: Panel = Panel.Top

    /** The tile behind the launch now starting, for the command center beside it. */
    private var launchSubject: RunningApp? = null

    /** The tile focused at the last A or X, and when, so a start right after it grows from that tile. */
    private class BloomPress(val focusedId: String?, val at: Long)

    private var bloomPress: BloomPress? = null

    /**
     * [art] grows to fill both screens while the start to [displayId] goes in, see
     * [LaunchBlooms]. Called in the same moment as the start, so it never delays a launch,
     * and before the play is noted, while the grid and the hero still show the game.
     * Motion Off and Android's remove-animations open the game with no art. True when the
     * game should open with no slide, over the art or with Motion Off: see [bloomStart].
     */
    private fun bloomLaunch(art: RunningApp, displayId: Int, both: Boolean): Boolean {
        val press = bloomPress
        bloomPress = null
        // Motion Off asks for less movement, so its games open without Android's slide too.
        val still = foldcade.shell.model.motionSpeed == MotionSpeed.Off
        // An app with no art of its own opens as it always has.
        if (art.record == null && art.query == null) return still
        val motion = foldcade.paintFor(foldcade.shell.model.themeIndex).theme.motion
        val timing = motion.launchBloom(foldcade.shell.model.motionSpeed, motion.animatorScale(contentResolver)) ?: return still
        val assignment = displays.assignment(foldcade.store.session.defaultDisplayIsTop)
        val panels = if (both) Panel.entries.toSet() else setOfNotNull(assignment.panelFor(displayId))
        // A start that waited on a download or a question grows from the middle: the
        // cursor may be on another tile by now.
        val pressed = press != null &&
            SystemClock.uptimeMillis() - press.at <= BLOOM_PRESS_MS &&
            press.focusedId != null &&
            foldcade.shell.focusedGame()?.id == press.focusedId
        foldcade.launchBlooms.start(
            art = art,
            gamePanels = panels,
            timing = timing,
            pressed = pressed,
            finding = foldcade.shell.find != null,
            heroKey = foldcade.shell.focusedHero()?.key,
        )
        return true
    }

    private fun launchFocused() {
        val game = foldcade.shell.focusedGame() ?: return
        if (game.emptyShelfHint || foldcade.launchWork.pending(game.id)) return
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
            waitForCatalogGame(game)
            return
        }
        launchStandIn(game)
    }

    private fun waitForCatalogGame(game: ShelfGame) {
        val tileId = game.id
        if (foldcade.launchWork.pending(tileId)) return
        val generation = beginLaunch(tileId)
        val screen = launchScreen
        foldcade.shell.beginCatalogWait(tileId)
        foldcade.launchWork.job = foldcade.scope.launch(Dispatchers.Main.immediate) {
            try {
                val route = awaitCatalogGame(
                    resolve = { foldcade.shell.launchRoute(tileId) },
                    sync = foldcade::joinOrSyncRomm,
                )
                if (!launchCurrent(generation)) return@launch
                foldcade.shell.endCatalogWait(tileId)
                if (route != null) {
                    foldcade.launchWork.resolved(generation)
                    launchScreen = screen
                    launchBackendGame(game.title.takeUnless { it == tileId } ?: route.play.label, route.play.platformId, route)
                } else {
                    foldcade.shell.present(catalogGameUnavailableDialog(offline = !foldcade.hasValidatedNetwork(), screen = hostScreen()))
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    foldcade.shell.endCatalogWait(tileId)
                }
            }
        }
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
        if (foldcade.launchWork.pending(route.tileId)) return
        if (waitForSavePreparation {
            val preparedRoute = foldcade.shell.launchRoute(route.tileId, pressed = route.play) ?: route
            launchBackendGame(title, platformId, preparedRoute, fresh, saveSyncDeclinedFor)
        }) return
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
        val generation = beginLaunch(route.tileId)
        foldcade.suppressSaveConflict = true
        foldcade.launchWork.job = foldcade.scope.launch {
            try {
                runBackendLaunch(title, platformId, route, fetching, generation, fresh, saveSyncDeclinedFor)
            } finally {
                // Opened or not, a half-told download or sync is stale now. A sync's mark stays.
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    if (fetching) foldcade.shell.endDownload(libraryId, game.remoteKey)
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
        // Refresh before resolving: both the choice and its save path depend on core visibility.
        if (foldcade.mayOpenRetroArch(id, game.platformId)) foldcade.readRetroArchCores()
        val resolution = foldcade.playerResolution(id, game.platformId)
        val players = listOfNotNull(resolution.player)
        val installedPackage = { player: Player ->
            player.packageNames.firstOrNull { name ->
                runCatching { packageManager.getPackageInfo(name, 0) }.isSuccess
            }
        }
        // Say so before a download that could take minutes, not after it.
        val missing = missingPlayer(players, installedPackage)
            ?: resolution.skipped.firstOrNull()?.takeIf { players.isEmpty() }?.let { GameLaunch.Missing(it.label, emptyList()) }
        if (missing != null) {
            withContext(Dispatchers.Main.immediate) {
                if (fetching) foldcade.shell.endDownload(libraryId, game.remoteKey)
                // The saved choice is the one named missing, when there was one; the rest, best first, also run it.
                val name = resolution.skipped.firstOrNull()?.label ?: missing.playerName
                if (launchCurrent(generation)) showMissing(name,
                    foldcade.plugins.playersFor(game.platformId).sortedBy(::playerRank).map { it.displayName }.distinct() - name)
            }
            return
        }
        // RetroArch's core and storage, also before a download.
        val first = players.firstOrNull { installedPackage(it) != null }
        if (first is RetroArchPlayer) {
            val retry = { launchBackendGame(title, platformId, route) }
            if (retroArchHeld(first, generation, id, readCores = false, retry = retry)) {
                if (fetching) withContext(Dispatchers.Main.immediate) { foldcade.shell.endDownload(libraryId, game.remoteKey) }
                return
            }
        }
        if (first is SaveFolderHolder && !first.hasSaveFolder()) foldcade.findPlayerSaveFolders()
        // Eden's keys and firmware need its discovered folder, also before a download.
        if (first != null && installedEdenPackage(first, ::packageInstalled) != null) {
            try {
                if (edenHeld(first, generation, libraryId, game.remoteKey)) {
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
        if ((first is Aps3ePlayer || first is DolphinPlayer) &&
            !first.hasSaveFolder() && saveSyncForLaunch(first, saveSyncDeclinedFor)) {
            // A revoked provider grant is a Fix, even before the generic folder offer or ROM download.
            foldcade.refreshSaveProtection(first)
            val lost = foldcade.saveProtection.incidents().firstOrNull {
                it.playerKey == first.saveFolderId() && it.kind == IncidentKind.LostAccess && it.blocksLaunch
            }
            if (lost != null) {
                withContext(Dispatchers.Main.immediate) {
                    if (fetching) foldcade.shell.endDownload(libraryId, game.remoteKey)
                    if (launchCurrent(generation)) {
                        val pending = PendingBackendLaunch(title, platformId, route, first.id, launchScreen, fresh)
                        showSaveNotPlaced(title, pending, protection = SaveProtectionStop(lost))
                    }
                }
                return
            }
        }
        // Defer Dolphin's launch setup check until the disc is known. A revision can override a global raw card.
        val syncState = if (first is SaveFolderHolder && first !is DolphinPlayer && first.hasSaveFolder()) {
            val folder = foldcade.playerSaveFolder(first)
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
        var saveBaseline: app.foldcade.language.ProbeSnapshot? = null
        var edenSession: EdenSaveSession? = null
        val spots = if (plan is GameLaunch.Installed) {
            try {
                val prepared = foldcade.launchWork.prepareFiles(
                    generation, game,
                    saves = {
                        try {
                            prepareSaves(route, plan.player, local, saveSyncDeclinedFor,
                                saveProbePackage(plan.player, game, target, setOf(plan.packageName), foldcade.sharedPaths::target))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: SaveNotPlaced) {
                            throw failure
                        } catch (_: Exception) {
                            // The player keeps its own save. Firmware still runs before play.
                            PreparedLaunch(local, emptyList(), route.saves)
                        }
                    },
                    firmware = { firmwareTarget, backendId -> prepareFirmwareFiles(plan.player, game, firmwareTarget, generation, backendId) },
                ) ?: return
                owner = prepared.owner ?: owner
                saveBaseline = prepared.baseline
                edenSession = prepared.edenSession
                prepared.spots
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (fatal: VirtualMachineError) {
                throw fatal
            } catch (failure: SaveNotPlaced) {
                withContext(Dispatchers.Main.immediate) {
                    if (launchCurrent(generation)) {
                        val pending = unresolvedSaveHold(failure) { PendingBackendLaunch(title, platformId, route, plan.player.id, launchScreen, fresh) }
                        showSaveNotPlaced(title, pending, protection = failure as? SaveProtectionStop, needsEdenUser = (failure as? UnresolvedSaveNotPlaced)?.needsEdenUser == true, incompatible = (failure as? UnresolvedSaveNotPlaced)?.incompatible == true)
                    }
                }
                return
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
                plan.player.launchIntent(LaunchRequest(game, target, plan.packageName)).let { foldcade.startIntent(plan.player, it, fresh = fresh) }
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
        val taking = { when (plan) {
            is GameLaunch.Installed -> if (installedIntent != null) startPanels(plan.player.occupiesBothDisplays) else emptySet()
            is GameLaunch.Dummy -> startPanels(false)
            else -> emptySet()
        } }
        whenLit(taking) {
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
                        val startedAt = System.currentTimeMillis()
                        val activityGrant = markGameActivity(installedIntent)
                        val started = startExternal(
                            id,
                            title,
                            game.platformId,
                            plan.player.occupiesBothDisplays,
                            installedIntent,
                            noteStart = { playerIntent?.let { foldcade.noteStarted(plan.player, it) } == true },
                        )
                        if (started) {
                            foldcade.notePlayerChoiceLaunch(id, game.platformId, resolution)
                            val saves = LibraryReturn(
                                owner.backendId,
                                owner,
                                plan.player,
                                spots,
                                (target as? LaunchTarget.ContentUri)?.uri ?: (target as? LaunchTarget.SharedFile)?.uri,
                                saveBaseline, !saveSyncForLaunch(plan.player, saveSyncDeclinedFor),
                                preserveAsBackup = !saveSyncForLaunch(plan.player, saveSyncDeclinedFor) && foldcade.saveIncidentCopies.beginUnprotected(owner, plan.player),
                                sessionStartedAtMillis = startedAt,
                                edenSession = edenSession,
                            )
                            rememberReturn(saves)
                            noteRunning(id, title, game.platformId, plan.player, playerIntent, installedIntent, saves, activityGrant)
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
    }

    /** Records a successful start using the intent the player can resume. */
    private fun noteRunning(
        id: String,
        title: String,
        platformId: String?,
        player: Player,
        playerIntent: app.foldcade.api.plugin.PlayerIntent,
        intent: Intent,
        saves: LibraryReturn?,
        activityGrant: GameActivityGrant?,
    ) {
        val launchFlags = playerIntent.flags
        val started = foldcade.launchWork.retroArchCore?.installedIntent(playerIntent)
            ?.takeIf { it != playerIntent }?.toStartIntent(foldcade.rommFolderAuthority) ?: intent
        val pkg = started.component?.packageName ?: return
        if (pkg == packageName) return
        if (player !is ApkMoonlightPlayer && !resumable(packageManager.getLaunchIntentForPackage(pkg)?.component?.className, started.component?.className, launchFlags)) {
            foldcade.runningGames.noteHolding(pkg)
            return
        }
        // Where the game landed, which may not be where it was asked for when that screen was busy.
        val panel = landedPanel() ?: if (player.occupiesBothDisplays) Panel.Top else launchScreen
        val movesBetweenScreens = LaunchFlag.MovesBetweenScreens in launchFlags
        val sameStartResumes = movesBetweenScreens || LaunchFlag.SameStartResumes in launchFlags || player is ApkMoonlightPlayer
        val resumeStart = if (sameStartResumes) Intent(started).apply {
            flags = resumeStartFlags(flags, launchFlags)
        } else null
        foldcade.runningGames.note(RunningGame(id, title, platformId, pkg, started.component?.className, panel,
            player.occupiesBothDisplays, System.currentTimeMillis(), saves, resumeStart,
            movesBetweenScreens = movesBetweenScreens, saveFolderKey = player.saveFolderId(), player = player,
            replacesInPlayer = LaunchFlag.ReplaceRunning in launchFlags,
            activityGrant = activityGrant?.takeIf { it.heldByPlayer(this) == true }))
    }

    private fun forgetClosedGameActivities(returned: RunningGame?) {
        for (packageName in foldcade.runningGames.packages()) {
            val grant = foldcade.runningGames.forPackage(packageName)?.activityGrant ?: continue
            if (grant.heldByPlayer(this) == false) foldcade.runningGames.forget(packageName)
            else if (returned?.activityGrant == grant) lifecycleScope.launch {
                // Home can resume before Android removes the finishing activity and its grant.
                delay(RESUME_CHECK_MS)
                if (foldcade.runningGames.forPackage(packageName)?.activityGrant == grant && grant.heldByPlayer(this@FoldcadeHomeActivity) == false) {
                    foldcade.runningGames.forget(packageName)
                }
            }
        }
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
    private fun resumeRunning(tileId: String, title: String, platformId: String?, warned: Boolean = false, startAnew: () -> Unit): Boolean {
        val running = foldcade.runningOn(tileId) ?: return false
        if (running.packageName in foldcade.closingPlayers) return true
        // A chosen save must load even when the old game is still paused behind Home.
        if (running.saveFolderKey?.let(foldcade.store::saveAwaitsLoad) == true) return false
        // A player that takes its game's start again as a resume gets that, see LaunchFlag.SameStartResumes.
        val front = running.resumeStart?.let(::Intent)
            ?: packageManager.getLaunchIntentForPackage(running.packageName)?.apply { flags = RESUME_FLAGS } ?: run {
            foldcade.runningGames.forget(running.packageName)
            return false
        }
        cancelLaunches()
        val generation = beginLaunch()
        foldcade.launchWork.job = foldcade.scope.launch {
            foldcade.saveFlow.withLock {
                // A prior return can open an incident or clear a run while this press waits.
                val exited = try {
                    playerDiedAfterStart(running.startedAtMillis, foldcade.playerExitTimestamps(running.packageName))
                } catch (_: SecurityException) { false }
                whenLit({ startPanels(running.occupiesBoth, requested = running.panel) }) {
                    withContext(Dispatchers.Main.immediate) {
                        if (!launchCurrent(generation) || isDestroyed || isFinishing) return@withContext
                        val current = foldcade.runningOn(tileId)
                            ?.takeIf { it.packageName == running.packageName && it.startedAtMillis == running.startedAtMillis }
                        val grant = current?.activityGrant
                        val resume = gameActivityResume(exited, grant != null, grant?.heldByPlayer(this@FoldcadeHomeActivity), warned)
                        if (current == null || resume == GameActivityResume.Launch || current.saveFolderKey?.let(foldcade.store::saveAwaitsLoad) == true) {
                            if (current != null && resume == GameActivityResume.Launch) foldcade.runningGames.forget(current.packageName)
                            startAnew()
                            return@withContext
                        }
                        if (current.packageName in foldcade.closingPlayers) return@withContext
                        if (resume == GameActivityResume.Warn) {
                            if (foldcade.shell.present(resumeGameDialog(hostScreen(), current.player?.displayName))) {
                                pendingGameResume = {
                                    val run = foldcade.runningOn(tileId)
                                    if (run == null) startAnew()
                                    else if (run.startedAtMillis == current.startedAtMillis && run.packageName == current.packageName) {
                                        if (!resumeRunning(tileId, title, platformId, warned = true, startAnew = startAnew)) startAnew()
                                    }
                                }
                            }
                            return@withContext
                        }
                        if (resume == GameActivityResume.Resume) {
                            if (!sendResume(current, tileId, title, platformId, front)) startAnew()
                            return@withContext
                        }
                        // A press from the other screen brings the same visible game forward.
                        val session = foldcade.store.session
                        val shown = Panel.entries.firstOrNull { panel -> session.externalOn(panel)?.id.let { it == tileId || it == current.tileId } }
                        if (shown != null) {
                            val assignment = displays.assignment(session.defaultDisplayIsTop)
                            val displayId = if (shown == Panel.Top) assignment.topDisplayId else assignment.bottomDisplayId
                            if (displayId != null) {
                                val resumed = rememberResume(current, continuing = true) {
                                    foldcade.shell.noteGameLaunched(tileId)
                                    startOnDisplay(front, displayId, multipleTask = false)
                                    true
                                }
                                if (!resumed) startAnew()
                            }
                        } else if (!sendResume(current, tileId, title, platformId, front)) startAnew()
                    }
                }
            }
        }
        return true
    }

    /** Sends [front], which brings back [running], and notes the resumed return. */
    private suspend fun sendResume(running: RunningGame, tileId: String, title: String, platformId: String?, front: Intent, panel: Panel = running.panel): Boolean {
        foldcade.music.onExternalLaunch()
        launchScreen = panel
        launchSubject = foldcade.shell.focusedRunningApp()
        val sentAt = System.currentTimeMillis()
        val started = rememberResume(running, continuing = false) {
            startExternal(tileId, title, platformId ?: running.platformId, running.occupiesBoth, front, resume = true)
        }
        if (started && running.resumeStart == null) {
            checkLauncherResume(running, sentAt, front.component?.className)
        }
        return started
    }

    /** Caller holds saveFlow, so an older read-back cannot clear the resumed record. */
    private suspend fun rememberResume(running: RunningGame, continuing: Boolean, start: () -> Boolean): Boolean = withContext(NonCancellable) {
        // A same-start intent can boot a closed game, so measure before sending it.
        val startedAt = if (continuing) null else System.currentTimeMillis()
        val resumed = try {
            foldcade.saveIncidentCopies.resume(running, sessionStartedAtMillis = startedAt, start = start)
        } catch (_: ActivityNotFoundException) {
            foldcade.runningGames.forget(running.packageName)
            null
        } ?: return@withContext false
        val pending = resumed.saves
        if (pending != null) {
            if (continuing) {
                val assignment = displays.assignment(foldcade.store.session.defaultDisplayIsTop)
                assignment.allIds().mapNotNull(foldcade::homeOnDisplay).forEach { home ->
                    if (home.libraryReturn == running.saves) home.libraryReturn = pending
                }
            } else {
                libraryReturn = pending
            }
            withContext(Dispatchers.IO) { foldcade.store.setPendingSaveReturn(pending.storeKey, pending.encode()) }
        }
        foldcade.runningGames.note(resumed.copy(panel = landedPanel() ?: running.panel))
        true
    }

    /** The press already opened the player's task. Report the result without starting another launch. */
    private fun checkLauncherResume(running: RunningGame, sentAt: Long, frontDoor: String?) {
        val session = foldcade.externalPlay.sessionId ?: return
        foldcade.scope.launch {
            delay(RESUME_CHECK_MS)
            val resumed = UsageAccess(this@FoldcadeHomeActivity)
                .resumedSince(running.packageName, sentAt, System.currentTimeMillis())
            // Usage events name the target of an activity alias, including ARMSX2's game activity.
            val activity = running.activity
            val targetActivity = if (running.activityGrant != null && activity != null && resumed.isNotEmpty()) {
                try {
                    packageManager.getActivityInfo(ComponentName(running.packageName, activity), 0).targetActivity
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
            } else null
            withContext(Dispatchers.Main.immediate) {
                if (isDestroyed || isFinishing || foldcade.externalPlay.sessionId != session) return@withContext
                val current = foldcade.runningGames.forPackage(running.packageName) ?: return@withContext
                if (current.startedAtMillis != running.startedAtMillis) return@withContext
                val grant = current.activityGrant
                val held = grant?.heldByPlayer(this@FoldcadeHomeActivity)
                when (launcherResumeResult(grant != null, held, resumed, frontDoor, activity, targetActivity)) {
                    LauncherResumeResult.KeepRun -> Unit
                    LauncherResumeResult.Warn -> Toast.makeText(applicationContext, Copy.resumeGameWarning(current.player?.displayName), Toast.LENGTH_LONG).show()
                    LauncherResumeResult.ForgetRun -> {
                        foldcade.runningGames.forget(running.packageName)
                        Toast.makeText(applicationContext, Copy.resumeGameClosed, Toast.LENGTH_LONG).show()
                    }
                }
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
                opensThere = foldcade.saveFolderOpensThere(player),
                needed = needsItsFolder(player),
                inApp = SaveProtectionCopy.gbcPickInApp.takeIf { player?.saveFolderId() == "gbc-emu" },
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

    /** X, including Find's X and Y's Close, asks about the exact run shown now. */
    private fun askCloseGame(gameId: String?) {
        val id = gameId ?: return
        if (!foldcade.shell.isPaused(id) || !foldcade.canCloseGame(id)) return
        val running = foldcade.runningOn(id) ?: return
        val closable = running.copy(companions = running.companions.filter { canClearPlayerTask(it.packageName) })
        if (foldcade.shell.present(closeGameDialog(hostScreen(), closable.companions.map { it.title }))) {
            foldcade.closeGameAsk = closable
        }
    }

    private fun closeGame(asked: RunningGame) {
        val running = foldcade.runningGames.forPackage(asked.packageName) ?: return
        if (running.startedAtMillis != asked.startedAtMillis || !foldcade.shell.isPaused(running.tileId) || !foldcade.canCloseGame(running.tileId)) return
        if (!foldcade.closingPlayers.add(running.packageName)) return
        cancelLaunches()
        // This job belongs to the application, never to a launch that a newer press may cancel.
        foldcade.scope.launch {
            try {
                foldcade.saveFlow.withLock {
                    val closed = withContext(Dispatchers.Main.immediate) {
                        val current = foldcade.runningGames.forPackage(running.packageName)
                        if (current?.startedAtMillis != asked.startedAtMillis || !foldcade.shell.isPaused(running.tileId)) return@withContext false
                        val assignment = displays.assignment(foldcade.store.session.defaultDisplayIsTop)
                        val displayId = if (running.panel == Panel.Top) assignment.topDisplayId else assignment.bottomDisplayId
                        val companions = asked.companions.filter { named -> current.companions.any { it.packageName == named.packageName } }
                        if (displayId == null) return@withContext false
                        // Persist after revalidation and before touching the player.
                        running.saves?.let { foldcade.store.setPendingSaveReturn(it.storeKey, it.encode()) }
                        if (!clearBehindHome(running.packageName, displayId)) return@withContext false
                        for (app in companions) {
                            val appDisplay = if (app.panel == Panel.Top) assignment.topDisplayId else assignment.bottomDisplayId
                            if (appDisplay != null && clearBehindHome(app.packageName, appDisplay)) {
                                val appId = "android:${app.packageName}"
                                foldcade.store.update { it.clearIfCurrent(app.panel, appId) }
                            }
                        }
                        foldcade.store.noteRepairPlayerClosed(running.packageName)
                        foldcade.runningGames.noteHolding(running.packageName, closed = true)
                        true
                    }
                    if (closed) {
                        // Android sends destroy asynchronously. Keep new save handovers behind the shutdown flush.
                        delay(CLOSE_SETTLE_MS)
                        running.saves?.let {
                            Log.i("Foldcade", "game-close read-back ${running.packageName} after ${CLOSE_SETTLE_MS}ms")
                            val returned = reconcileLocked(it)
                            Log.i("Foldcade", "game-close read-back finished=$returned ${running.packageName}")
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("Foldcade", "Could not finish closing player", failure)
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) { foldcade.closingPlayers.remove(running.packageName) }
                foldcade.resumeSaveRepairs()
            }
        }
    }

    /** Task identity stays with its creator. A task first created by the player's menu on another screen can take this clear. */
    private fun clearBehindHome(playerPackage: String, displayId: Int): Boolean {
        if (!canClearPlayerTask(playerPackage)) return false
        val front = packageManager.getLaunchIntentForPackage(playerPackage) ?: return false
        val home = foldcade.homeOnDisplay(displayId) ?: return false
        front.flags = CLOSE_FLAGS
        val homeIntent = Intent(this, if (displayId == Display.DEFAULT_DISPLAY) PrimaryHomeActivity::class.java else CompanionHomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        return try {
            home.startActivities(arrayOf(front, homeIntent), ActivityOptions.makeBasic().apply { launchDisplayId = displayId }.toBundle())
            // Home may cover a different game now using this panel. Keep that game
            // paused, as a Home press does, instead of treating it as a player exit.
            if (foldcade.externalPlay.displayId == displayId) foldcade.homeRecalledOn = displayId
            Log.i("Foldcade", "game-close $playerPackage display=$displayId")
            true
        } catch (failure: RuntimeException) {
            Log.w("Foldcade", "Could not close player behind Home", failure)
            false
        }
    }

    /** A paused one-screen game resumes on the other free panel, using its existing task. */
    private fun moveGame(gameId: String?) {
        val id = gameId ?: return
        val running = foldcade.runningOn(id) ?: return
        if (!foldcade.shell.isPaused(id)) return
        val target = foldcade.moveGameTarget(id) ?: return
        val front = running.resumeStart?.let(::Intent) ?: return
        cancelLaunches()
        val generation = beginLaunch()
        foldcade.launchWork.job = foldcade.scope.launch {
            // SameStartResumes may boot a closed game, so finish its preceding save return first.
            foldcade.saveFlow.withLock {
                whenLit({ startPanels(running.occupiesBoth, requested = target) }) {
                    withContext(Dispatchers.Main.immediate) {
                        // A save choice may have committed while Move waited for the flow. The same
                        // run may have gained or lost a companion meanwhile, so it is matched by its start.
                        val current = foldcade.runningOn(id)
                            ?.takeIf { it.packageName == running.packageName && it.startedAtMillis == running.startedAtMillis }
                        if (current == null || !launchCurrent(generation) || isDestroyed || isFinishing || foldcade.moveGameTarget(id) != target) return@withContext
                        sendResume(current, id, current.title, current.platformId, front, panel = target)
                    }
                }
            }
        }
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

    private fun showSaveNotPlaced(title: String, pending: PendingBackendLaunch? = null, needsEdenUser: Boolean = false, incompatible: Boolean = false, protection: SaveProtectionStop? = null) {
        if (protection != null) {
            val style = foldcade.deviceStatus.status.value.dateStyle
            val dialog = foldcade.shell.showSaveProtection(protection.incident, pending != null, style = style) ?: return
            if (pending != null) {
                foldcade.hostedAsk = HostedAsk(pending.playerId, pending)
                foldcade.scope.launch {
                    val owner = pending.route.saves
                    val receipts = try { foldcade.plugins.library(owner.backendId)?.saveReceipts(owner).orEmpty() }
                        catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { emptyMap() }
                    val hasVersions = receipts.isNotEmpty() || try { foldcade.plugins.serverSaves(owner.backendId, owner).isNotEmpty() }
                        catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
                    withContext(Dispatchers.Main.immediate) {
                        foldcade.shell.updateSaveProtection(dialog, protection.incident, receipts.values.filterNotNull().minOrNull(), hasVersions, style)
                    }
                }
            }
            return
        }
        foldcade.shell.showDialog(app.foldcade.language.saveNotPlacedDialog(title, hostScreen(), pending != null, needsEdenUser, incompatible))
        if (pending != null) foldcade.hostedAsk = HostedAsk(pending.playerId, pending)
    }

    /** BIOS search and writes run only during a launch, off Main. */
    private suspend fun prepareFirmwareFiles(player: Player, game: Game, target: LaunchTarget, generation: Int, firmwareBackendId: String): Boolean {
        val libraries = foldcade.store.libraryFolders().map { it.uri }
        val interested = withContext(Dispatchers.IO) {
            firmwareRequired(player, game)
        }
        if (!interested) return true
        val progress = TileDownload(foldcade.shell, game.backendId, game.remoteKey)
        val launchContext = kotlinx.coroutines.currentCoroutineContext()
        val result = try {
            kotlinx.coroutines.runInterruptible(Dispatchers.IO) {
                foldcade.playerFirmware.prepare(player, game, target, foldcade.playerSaveFolder(player), libraries) {
                    launchContext.ensureActive()
                    progress.onStep(RomProgress.Receiving(0L, null))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            withContext(Dispatchers.Main.immediate) {
                if (launchCurrent(generation)) foldcade.postNotice(app.foldcade.language.FoldNotice(
                    id = "firmware-folder:${player.saveFolderId()}", title = app.foldcade.language.FirmwareCopy.folderNoticeTitle(player.displayName),
                    body = app.foldcade.language.FirmwareCopy.unreachable(player.displayName),
                    postedMillis = System.currentTimeMillis(), firmwarePlayerId = player.id))
            }
            return true
        } finally {
            withContext(NonCancellable) { progress.finish() }
        }
        return withContext(Dispatchers.Main.immediate) {
            if (!launchCurrent(generation)) return@withContext false
            if (result.blocksLaunch) foldcade.dismissNotice("firmware:${player.id}")
            firmwareWrittenNotice(result, player, foldcade.plugins.platform(game.platformId)?.displayName ?: game.platformId)
                ?.let { foldcade.postNotice(it) }
            if (!result.needsFolder) foldcade.dismissNotice("firmware-folder:${player.saveFolderId()}")
            when {
                result.needsFolder -> {
                    foldcade.postNotice(app.foldcade.language.FoldNotice(
                        id = "firmware-folder:${player.saveFolderId()}", title = app.foldcade.language.FirmwareCopy.folderNoticeTitle(player.displayName),
                        body = app.foldcade.language.FirmwareCopy.folder(
                            if (player is RetroArchPlayer) "RetroArch" else player.displayName, result.place),
                        postedMillis = System.currentTimeMillis(), firmwarePlayerId = player.id))
                    true
                }
                result.blocksLaunch -> {
                    val message = app.foldcade.language.FirmwareCopy.missing(
                        foldcade.plugins.platform(game.platformId)?.displayName ?: game.platformId,
                        result.missing, result.place,
                        rommConnected = canUseRommFirmware(firmwareBackendId, foldcade.store.rommOrigin() != null))
                    foldcade.shell.showDialog(app.foldcade.language.missingFirmwareDialog(message, hostScreen(),
                        firmwarePlayerId = (player as? RetroArchPlayer)?.id))
                    false
                }
                else -> true
            }
        }
    }

    /**
     * [route]'s saves into the player's folder, see [LaunchSaves.prepare]. One save flow
     * runs at a time, so a second launch or a return read-back waits rather than racing this one.
     */
    private suspend fun prepareSaves(route: GameRoute, player: Player, local: LaunchTarget, declinedFor: String?, resolvedPackage: String?): PreparedLaunch =
        foldcade.saveFlow.withLock {
            var launchReading: app.foldcade.language.ProbeSnapshot? = null
            LaunchSaves(
                foldcade.playerSaves,
                foldcade.plugins,
                protectionGate = { route, player, eden ->
                    val known = try { foldcade.plugins.saves(route.saves.backendId, route.saves).slots.isNotEmpty() }
                        catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
                    try { foldcade.saveProbes.gate(player, route.saves, local, known, resolvedPackage, eden) { launchReading = it } }
                    finally {
                        val uri = (local as? LaunchTarget.ContentUri)?.uri ?: (local as? LaunchTarget.SharedFile)?.uri
                        foldcade.associateSaveRepair(player, incidentGame(route.saves, player, uri, foldcade.store.rommOrigin(), launchReading))
                        try { foldcade.publishSaveGate(player, launchReading) }
                        catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { Unit }
                    }
                },
                baseline = { player, game, target, spots ->
                    try { foldcade.saveProbes.snapshot(player, game, target, resolvedPackage = resolvedPackage, edenSpots = spots, launchReading = launchReading) }
                    catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
                },
                preserveOutside = foldcade.saveIncidentCopies::preserveBeforeLaunch,
                firstLink = { route, owner, player, target, spots, outside ->
                    // The hero, R1 and Fix read the new two-saves incident at once.
                    foldcade.saveIncidentCopies.holdFirstLink(route, owner, player, target, spots, outside)?.also { foldcade.publishSaveGate(player, null) }
                },
                onHeadPlaced = foldcade.saveIncidentCopies::headPlaced,
                firmwareAvailable = { picked, game, target -> foldcade.playerFirmware.canPlace(picked, game, foldcade.playerSaveFolder(picked), target) },
                awaitLoad = { foldcade.store.setSaveAwaitsLoad(it, true) },
                onSpots = { game, picked, spots ->
                    val reachable = spots.isNotEmpty() && picked.saveDeclarations(game).all { declaration -> spots.any { it.slot == declaration.slot } }
                    val issue = if (reachable) null else foldcade.playerSaves.unresolvedSpot(picked, game, local)
                    withContext(Dispatchers.Main.immediate) { foldcade.shell.noteSaveReachable(game, picked.id, reachable, issue) }
                },
            )
                .prepare(route, player, local, syncing = saveSyncForLaunch(player, declinedFor)).also { prepared ->
                    foldcade.refreshSaveReceipt(prepared.owner ?: route.saves, player, prepared.spots)
                }
        }

    private fun showMissing(playerName: String, alsoRuns: List<String>) {
        foldcade.shell.showDialog(
            missingPlayerDialog(playerName, hostScreen(), canGet = PlayerPages.of(playerName) != null, alsoRuns = alsoRuns),
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
        val running = launchSubject?.takeIf { it.title == title } ?: RunningApp(title)
        foldcade.commandCenter.note(id, running)
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
        if (intent.action == ApkPluginProtocol.PLAY) {
            intent.putExtra(ApkPluginProtocol.EXTRA + "TITLE", title)
            intent.putExtra(ApkPluginProtocol.EXTRA + "TOP_DISPLAY", assignment.topDisplayId)
            intent.putExtra(ApkPluginProtocol.EXTRA + "BOTTOM_DISPLAY", assignment.bottomDisplayId ?: -1)
        }
        val playerPackage = intent.component?.packageName
        val replacing = !resume && foldcade.runningGames.mayHold(playerPackage)
        if (replacing && playerPackage != null && canClearPlayerTask(playerPackage)) intent.flags = replaceFlags(intent.flags)
        // Only a Home press after this start says the game was left open.
        foldcade.homeRecalledOn = null
        // Before the play is noted: noting it can reorder a Recent folder under the cursor.
        val still = bloomLaunch(running, displayId, occupiesBoth)
        foldcade.shell.noteGameLaunched(id)
        try {
            foldcade.externalPlay.open(foldcade.plays, id, title, displayId, playerPackage) {
                startOnDisplay(intent, displayId, multipleTask = !resume && !replacing, bloom = still)
                if (noteStart()) armRestart(intent, displayId)
            }
        } catch (failure: Exception) {
            foldcade.launchBlooms.cancel()
            foldcade.shell.forgetGameLaunch()
            throw failure
        }
        // The game it replaced is gone. This one is noted once the start is in.
        if (replacing && playerPackage != null) foldcade.runningGames.forget(playerPackage)
        foldcade.noteExternalPlayerOpened()
        foldcade.notePlayScreen()
        return true
    }

    private fun launchStandIn(game: ShelfGame) =
        afterLit(startPanels(game.occupiesBothDisplays), game.id) { launchStandInNow(game) }

    private fun launchStandInNow(game: ShelfGame) {
        foldcade.music.onExternalLaunch()
        val session = foldcade.store.session
        val external = ExternalApp(game.id, game.occupiesBothDisplays)
        val running = foldcade.shell.runningApp(game)
        foldcade.commandCenter.note(game.id, running)
        if (game.occupiesBothDisplays) {
            val displayId = displays.assignment(session.defaultDisplayIsTop).topDisplayId
            val still = bloomLaunch(running, displayId, both = true)
            foldcade.shell.noteGameLaunched(game.id)
            foldcade.store.update { it.launch(external, launchScreen) }
            beginPlay(game, displayId, still)
            return
        }
        val panel = session.singleScreenTarget(launchScreen) ?: return
        val assignment = displays.assignment(session.defaultDisplayIsTop)
        val displayId = when (panel) {
            Panel.Top -> assignment.topDisplayId
            Panel.Bottom -> assignment.bottomDisplayId ?: return
        }
        val still = bloomLaunch(running, displayId, both = false)
        foldcade.shell.noteGameLaunched(game.id)
        foldcade.store.place(panel, external)
        beginPlay(game, displayId, still)
    }

    /** Opens a play session, then starts the stand-in. That activity reports the rest of the session. */
    private fun beginPlay(game: ShelfGame, displayId: Int, bloom: Boolean = false) {
        val playId = foldcade.plays.start(game.id, game.title)
        try {
            startOnDisplay(StandInActivity::class.java, displayId, bloom) {
                putExtra(StandInActivity.EXTRA_ID, game.id)
                putExtra(StandInActivity.EXTRA_TITLE, game.title)
                putExtra(StandInActivity.EXTRA_PLAY, playId)
            }
        } catch (failure: RuntimeException) {
            foldcade.plays.signal(playId, PlaySignal.End)
            foldcade.launchBlooms.cancel()
            foldcade.shell.forgetGameLaunch()
            throw failure
        }
    }

    private suspend fun startPlayer(
        game: ShelfGame,
        ready: PlayerLaunch.Ready,
        apiGame: Game,
        player: Player,
        generation: Int,
        spots: List<SaveSpot>,
        syncing: Boolean,
        baseline: app.foldcade.language.ProbeSnapshot? = null,
        saveReadUri: String? = null,
        edenSession: EdenSaveSession? = null,
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
        val running = foldcade.shell.runningApp(game)
        foldcade.commandCenter.note(game.id, running)
        // Before the play is noted: noting it can reorder a Recent folder under the cursor.
        val still = bloomLaunch(running, displayId, ready.occupiesBothDisplays)
        foldcade.shell.noteGameLaunched(game.id)
        if (ready.occupiesBothDisplays) {
            foldcade.store.update { it.launch(external, launchScreen) }
        } else {
            foldcade.store.place(panel, external)
        }
        val gameNativePin = player.id == app.foldcade.plugins.gamenative.GameNativePlayer.ID &&
            ready.intent.extras.any { it.key == app.foldcade.plugins.gamenative.GameNativePlayer.EXTRA_SHORTCUT_ID }
        try {
            val intent = foldcade.startIntent(player, ready.intent).withApkPluginDisplays(assignment.topDisplayId, assignment.bottomDisplayId)
            val start = intent.toAndroidIntent(foldcade.rommFolderAuthority)
            val activityGrant = markGameActivity(start)
            val startedAt = System.currentTimeMillis()
            foldcade.externalPlay.open(foldcade.plays, game.id, game.title, displayId, intent.packageName) {
                val options = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }
                if (start.action == ApkPluginProtocol.PLAY) start.putExtra(ApkPluginProtocol.EXTRA + "TITLE", game.title)
                if (still) bloomStart(start)
                val shortcutId = intent.extras.filterIsInstance<app.foldcade.api.plugin.PlayerExtra.Text>()
                    .firstOrNull { it.key == app.foldcade.plugins.gamenative.GameNativePlayer.EXTRA_SHORTCUT_ID }?.value
                if (player.id == app.foldcade.plugins.gamenative.GameNativePlayer.ID && shortcutId != null) {
                    getSystemService(android.content.pm.LauncherApps::class.java)
                        .startShortcut(intent.packageName, shortcutId, null, options.toBundle(), android.os.Process.myUserHandle())
                } else {
                    foldcade.authorizeApkPluginStart(start)
                    startActivity(start, options.toBundle())
                    if (foldcade.noteStarted(player, intent)) armRestart(start, displayId)
                }
            }
            foldcade.noteExternalPlayerOpened()
            foldcade.notePlayScreen()
            if (game.libraryId != null) {
                val saves = LibraryReturn(game.libraryId, apiGame, player, spots, saveReadUri, baseline, !syncing,
                    preserveAsBackup = !syncing && foldcade.saveIncidentCopies.beginUnprotected(apiGame, player),
                    sessionStartedAtMillis = startedAt, edenSession = edenSession)
                rememberReturn(saves)
                noteRunning(game.id, game.title, game.platformId, player, intent, start, saves, activityGrant)
            }
            game.platformId?.let { platform -> foldcade.notePlayerChoiceLaunch(game.id, platform, foldcade.playerResolution(game.id, platform).copy(player = player)) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ActivityNotFoundException) {
            foldcade.launchBlooms.cancel()
            foldcade.shell.forgetGameLaunch()
            foldcade.store.update { it.home(Panel.Top).home(Panel.Bottom) }
            val name = playerChoiceLabel(player) ?: "Player"
            if (gameNativePin) foldcade.shell.present(app.foldcade.language.gameNativeShortcutPrompt(hostScreen()))
            else presentMissing(name, generation)
        } catch (failure: Exception) {
            foldcade.launchBlooms.cancel()
            foldcade.shell.forgetGameLaunch()
            foldcade.store.update { it.home(Panel.Top).home(Panel.Bottom) }
            if (gameNativePin) {
                val prompt = if (failure is SecurityException) {
                    app.foldcade.language.homePrompt(hostScreen(), listOf(app.foldcade.plugins.gamenative.GameNativePlayer.DISPLAY_NAME))
                } else app.foldcade.language.gameNativeShortcutPrompt(hostScreen())
                foldcade.shell.present(prompt)
            }
        }
    }

    /**
     * Opens a notice's app on the Play on screen, or the other when a game holds it,
     * the way an app from the grid opens. A notice that only runs a service or a
     * broadcast leaves both screens to Foldcade.
     */
    private fun openNotice(key: String) {
        val listener = NoticeListener.connected ?: return
        val session = foldcade.store.session
        val panel = session.singleScreenTarget(foldcade.shell.model.playScreen().panel()) ?: return
        val assignment = displays.assignment(session.defaultDisplayIsTop)
        val displayId = when (panel) {
            Panel.Top -> assignment.topDisplayId
            Panel.Bottom -> assignment.bottomDisplayId ?: return
        }
        val pkg = foldcade.shell.model.notices.firstOrNull { it.id == key }?.packageName
        afterLit(setOf(panel)) { openNoticeOn(listener, key, displayId, panel, pkg) }
    }

    private fun openNoticeOn(listener: NoticeListener, key: String, displayId: Int, panel: Panel, pkg: String?) {
        listener.open(key, displayId) {
            foldcade.music.onExternalLaunch()
            val id = "notice:${pkg ?: key}"
            foldcade.commandCenter.note(id, pkg?.let { RunningApp(title = "", packageName = it) })
            foldcade.store.place(panel, ExternalApp(id))
        }
    }

    /** The first of [intents] this device can open. */
    /**
     * Android's folder picker for a library folder. It opens (OpenDocumentTree's
     * EXTRA_INITIAL_URI) at the ROM folder Foldcade saw, so choosing it is Use this folder and
     * Allow. Otherwise it opens at the top of internal storage, where ROM folders live, rather
     * than wherever Android's picker was last left, which on the Thor is often an emulator's own files.
     */
    private fun pickLibraryFolder() {
        folderPurpose = FolderPurpose.Library
        folderReplacing = null
        val start = foldcade.shell.model.romFolderOffer?.start ?: foldcade.shell.model.folderStart ?: "primary:"
        launchFolderPicker(Uri.parse(storageFolderDocument(start)), opensThere = start != "primary:")
    }

    /**
     * Android's all-files page for Foldcade. Its switch takes a touch: with the D-pad, focus
     * starts on the app's header, or on nothing in touch mode, so Foldcade's other screen says
     * what to do until the player comes back.
     */
    private fun openAllFilesAccess() {
        if (startFirst(AllFilesAccess(this).settingsIntents())) foldcade.shell.setAllFilesPage(true)
    }

    /** Starts the first of [intents] Android can open. False when it has none of them. */
    private fun startFirst(intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                continue
            }
        }
        return false
    }

    private fun hostScreen(): HostScreen {
        val panel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop) ?: Panel.Bottom
        return inputScreen(panel)
    }

    /** The screen a key or a page from [panel] belongs to. The dark screen's are the grid's. */
    private fun inputScreen(panel: Panel): HostScreen {
        val session = foldcade.store.session
        return if (session.surfaceOn(panel) == Surface.Dark) session.gridPanel().screen() else panel.screen()
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
    private fun playerFor(game: ShelfGame): Player? = game.platformId?.let { foldcade.playerFor(game.id, it) }

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

    private fun beginLaunch(subject: String? = null): Int = foldcade.launchWork.begin(subject).also { ownedLaunch = it }

    /** Drops an in-flight launch so a late result cannot open a missing-player dialog. */
    private fun cancelLaunches() {
        foldcade.launchWork.cancel()
        clearPendingLaunch()
        pendingGameResume = null
        retroArchRetry = null
    }

    private fun launchCurrent(generation: Int): Boolean = foldcade.launchWork.current(generation)

    private fun presentMissing(name: String, generation: Int) {
        if (!launchCurrent(generation)) return
        foldcade.shell.present(missingPlayerDialog(name, hostScreen(), canGet = PlayerPages.of(name) != null))
    }

    /** The game already on both screens, by name, for the dialog that asks to close it. */
    private fun runningBothPanelTitle(): String? {
        val session = foldcade.store.session
        val app = listOfNotNull(session.topApp, session.bottomApp).firstOrNull { it.occupiesBothDisplays } ?: return null
        return foldcade.runningOn(app.id)?.title?.takeIf { it.isNotBlank() }
    }

    /** A missing player's own download page, in the browser. */
    private fun openPlayerPage(playerName: String?) {
        val page = playerName?.let(PlayerPages::of) ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: android.content.ActivityNotFoundException) {
            foldcade.shell.present(playerNotice(Copy.missingPlayerTitle(playerName), page, hostScreen()))
        }
    }

    private fun onDialog(effect: Effect.DialogChoice) {
        if (effect.kind == DialogKind.ThorSetup || effect.kind == DialogKind.RestoreThorSetup) {
            val playerId = effect.subject ?: return
            when (effect.button) {
                DialogButton.ApplyThorSetup -> changeThorSetup(playerId, restore = false)
                DialogButton.RestoreThorSetup -> if (effect.kind == DialogKind.ThorSetup)
                    foldcade.shell.showDialog(app.foldcade.language.restoreThorSetupDialog(playerId, hostScreen()))
                    else changeThorSetup(playerId, restore = true)
                DialogButton.ThorAppInfo -> foldcade.thorPresets.packageToClose()?.let { name ->
                    startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", name, null)))
                }
                DialogButton.ThorFileAccess -> startFirst(AllFilesAccess(this).settingsIntents())
                DialogButton.ChooseSaveFolder -> choosePlayerSave(playerId)
                else -> Unit
            }
            return
        }

        when (effect.kind) {
            DialogKind.SaveFolderFine -> if (effect.button == DialogButton.KeepWatchingSaveFolder) {
                val incident = foldcade.saveProtection.incidents().firstOrNull { it.id == effect.subject && canConfirmSaveLocation(it) }
                if (incident != null) foldcade.scope.launch {
                    val player = foldcade.plugins.playerIds().mapNotNull(foldcade.plugins::player).firstOrNull { it.saveFolderId() == incident.playerKey }
                    if (player != null) foldcade.saveProbes.markBound(player)
                    foldcade.saveProtection.close(incident.id)
                    foldcade.refreshSaveProtection()
                    withContext(Dispatchers.Main.immediate) { foldcade.shell.closeSaveFix() }
                }
            }
            DialogKind.SaveProtection -> when (effect.button) {
                DialogButton.SetUpSaves, DialogButton.FixSaves, DialogButton.ChooseSaves, DialogButton.PutSaveBack, DialogButton.Allow -> effect.subject?.let(::openSaveFix)
                DialogButton.PlayWithoutSyncing -> {
                    val pending = foldcade.hostedAsk?.pending
                    foldcade.hostedAsk = null
                    pending?.let { launchScreen = it.screen; launchBackendGame(it.title, it.platformId, it.route, it.fresh, saveSyncDeclinedFor = it.playerId) }
                }
                else -> { foldcade.hostedAsk = null; cancelLaunches() }
            }
            DialogKind.ThorSetup, DialogKind.RestoreThorSetup -> Unit
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
            DialogKind.FindGames -> {
                foldcade.store.setFindGamesOffered()
                if (effect.button == DialogButton.Allow) openAllFilesAccess()
            }
            DialogKind.AddGames -> {
                foldcade.store.setAddGamesAsked()
                when (effect.button) {
                    DialogButton.FindGames -> {
                        foldcade.store.setFindGamesOffered()
                        openAllFilesAccess()
                    }
                    // The dialog said what to choose, so the picker needs no explainer of its own.
                    DialogButton.AddFolder -> {
                        foldcade.shell.noteFolderExplained()
                        pickLibraryFolder()
                    }
                    DialogButton.SignInRomm -> {
                        foldcade.shell.openAddRomm()
                        beginRommDeviceCode()
                    }
                    else -> Unit
                }
            }
            // Continue on the explainer, or Add a folder on a pick that held no games.
            DialogKind.Folder -> if (effect.button == DialogButton.ContinueGrant || effect.button == DialogButton.AddFolder) {
                foldcade.shell.noteFolderExplained()
                pickLibraryFolder()
            }
            DialogKind.Ok -> {
                cancelLaunches()
                if (effect.button == DialogButton.Wifi) dispatch(Effect.OpenAndroidSetting(AndroidSetting.Wifi))
            }
            DialogKind.ReLogin -> if (effect.button == DialogButton.SignInRomm) {
                foldcade.shell.openRommRecovery()
                beginRommDeviceCode()
            }
            DialogKind.MissingPlayer -> {
                cancelLaunches()
                if (effect.button == DialogButton.GetPlayer) openPlayerPage(effect.subject)
            }
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
            DialogKind.RetroArchCores -> when (effect.button) {
                DialogButton.ContinueGrant -> showRetroArchCores(retroArchRetry)
                // Back stops the launch it held. The offer stays, so the next start asks again.
                DialogButton.Dismiss -> cancelLaunches()
                // Play anyway, or the first run's Not now: launches try the system's first core from now on.
                else -> settleRetroArchCores()
            }
            DialogKind.EdenRestart -> if (effect.button == DialogButton.ContinueGrant) {
                val pkg = edenRestartPackage ?: EDEN_PACKAGES.firstOrNull(::packageInstalled)
                edenRestartPackage = null
                if (pkg != null) openAndroidSetting(AndroidSetting.AppInfo, pkg)
            } else {
                // Not now only closes. The pending restart stays until Eden is force-stopped or the device restarts.
                edenRestartPackage = null
            }
            DialogKind.EdenWrongFolder -> when (effect.button) {
                DialogButton.TryAgain -> openEdenFolder(fromOffer = pendingBackend != null || pendingLaunch != null)
                // Back never starts a held launch.
                DialogButton.Dismiss -> cancelLaunches()
                else -> declineEdenFolder()
            }
            DialogKind.RetroArchSetup -> {
                if (effect.button == DialogButton.AllowFiles) openRetroArchPermissions() else {
                    cancelLaunches()
                    if (effect.button == DialogButton.OpenRetroArch) openRetroArch()
                }
            }
            DialogKind.RetroArchDownload -> {
                val retry = retroArchRetry
                if (effect.button == DialogButton.TryAgain) retryAfterRetroArchCores(retry) else {
                    cancelLaunches()
                    if (effect.button == DialogButton.OpenRetroArch) openRetroArch()
                }
            }
            DialogKind.PlayHistory -> when (effect.button) {
                DialogButton.ExportPlay -> startPicker(PlayCopy.notSaved) { playExport.launch(playHistoryFileName()) }
                DialogButton.ImportPlay -> startPicker(PlayCopy.unreadable) { playImport.launch(arrayOf("application/zip", "application/octet-stream")) }
                else -> Unit
            }
            DialogKind.PlayHistoryDone -> Unit
            DialogKind.Backup -> when (effect.button) {
                DialogButton.BackUp -> startPicker(BackupCopy.notSaved, backup = true) { setupExport.launch("Foldcade backup ${java.time.LocalDate.now()}.zip") }
                DialogButton.PickRestore -> startPicker(BackupCopy.unreadable, backup = true) { setupImport.launch(arrayOf("application/zip", "application/octet-stream")) }
                DialogButton.UndoRestore -> readSetup(RestoreAction.Undo) { foldcade.setupBackups.before.inputStream() }
                DialogButton.RedoRestore -> readSetup(RestoreAction.Redo) { foldcade.setupBackups.before.inputStream() }
                else -> Unit
            }
            DialogKind.RestoreBackup -> {
                val backup = foldcade.pendingSetupRestore
                val action = foldcade.pendingSetupAction
                foldcade.pendingSetupRestore = null
                foldcade.pendingSetupAction = RestoreAction.Restore
                if (effect.button in setOf(DialogButton.RestoreBackup, DialogButton.UndoRestore, DialogButton.RedoRestore) && backup != null) restoreSetup(backup, action)
                else foldcade.scope.launch { foldcade.setupBackups.discard(backup) }
            }
            // The shell answers it itself: Sign out forgets RomM, Not now only closes.
            DialogKind.RommSignOut -> Unit
            DialogKind.BackupDone -> when (effect.subject) {
                "restored" -> foldcade.scope.launch { foldcade.setupBackups.result.delete() }
                "restore-failed" -> foldcade.scope.launch { foldcade.setupBackups.acknowledgeFailure() }
            }
            DialogKind.EdenSaveHold -> {
                val ask = foldcade.hostedAsk
                foldcade.hostedAsk = null
                answerUnresolvedSaveHold(
                    effect.button,
                    cancel = ::cancelLaunches,
                    openPlayer = { ask?.playerId?.let(::openPlayerApp) },
                    playWithoutSyncing = {
                        ask?.pending?.let { pending ->
                            launchScreen = pending.screen
                            launchBackendGame(pending.title, pending.platformId, pending.route, pending.fresh, saveSyncDeclinedFor = pending.playerId)
                        }
                    },
                )
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
                    // Back stops the launch. Only Play without syncing starts it.
                    effect.button == DialogButton.Dismiss -> cancelLaunches()
                    pending != null -> {
                        launchScreen = pending.screen
                        launchBackendGame(pending.title, pending.platformId, pending.route, pending.fresh, saveSyncDeclinedFor = pending.playerId)
                    }
                }
            }
            DialogKind.CloseGame -> {
                val asked = foldcade.closeGameAsk
                foldcade.closeGameAsk = null
                if (effect.button == DialogButton.ContinueFix) effect.subject?.let(::closeGameForRepair)
                if (effect.button == DialogButton.CloseIt && asked != null) closeGame(asked)
            }
            DialogKind.RestartGame -> {
                val id = restartId
                restartId = null
                if (effect.button == DialogButton.Restart && id != null) restartGame(id)
            }
            DialogKind.ResumeGame -> {
                val resume = pendingGameResume
                pendingGameResume = null
                if (effect.button == DialogButton.ContinueGrant) resume?.invoke()
            }
            DialogKind.FirmwareFolder -> {
                if (effect.button == DialogButton.ChooseSaveFolder) {
                    foldcade.plugins.player(effect.subject ?: return)?.let(::openFirmwareFolder)
                }
            }
            // Back stops the launch the ask held. Only its own button plays without syncing.
            DialogKind.SaveFolder -> if (effect.button == DialogButton.Dismiss) {
                cancelLaunches()
            } else if (pendingBackend != null) {
                val pending = pendingBackend ?: return
                if (effect.button == DialogButton.ChooseSaveFolder) {
                    if (foldcade.plugins.player(pending.playerId) is EdenPlayer) {
                        openEdenFolder(fromOffer = true)
                    } else {
                        folderPurpose = FolderPurpose.SaveOffer
                        settingsPlayerId = pending.playerId
                        openSaveFolderPicker(foldcade.plugins.player(pending.playerId))
                    }
                } else {
                    skipSaveFolderAndLaunch()
                }
            } else if (effect.button == DialogButton.ChooseSaveFolder) {
                if (playerForPendingLaunch() is EdenPlayer) {
                    openEdenFolder(fromOffer = true)
                } else {
                    folderPurpose = FolderPurpose.LaunchOffer
                    openSaveFolderPicker(playerForPendingLaunch())
                }
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
                    var newServer = false
                    val saved = try {
                        val before = foldcade.store.rommOrigin()
                        newServer = before == null || normalizeSetupOrigin(before) != origin
                        // Its tiles name the old server's ROM ids until the new one lists its games.
                        if (newServer) {
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
                            if (foldcade.shell.setupValidationCurrent(ticket)) {
                                foldcade.shell.closeConnect()
                                // A new server is the moment to get every emulator's saves onto it.
                                // Not beside a running game: the page would take the controls from it.
                                if (newServer && Panel.entries.none { foldcade.store.session.externalOn(it) != null }) foldcade.shell.offerSaveSetup(hostScreen())
                            }
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
        foldcade.refreshSaveService(RommCredentials.PLUGIN_ID)
        foldcade.shell.setSignedIn(
            listOfNotNull(foldcade.store.rommOrigin()?.let { SignedInBackend(RommCredentials.PLUGIN_ID, "RomM") }) +
                foldcade.shell.model.signedIn.filter { it.pluginId != RommCredentials.PLUGIN_ID },
        )
    }

    /**
     * Device code is the main path once an origin is on the form. Paste stays in the token field.
     * The code follows the address on this form, including a phone's explicit choice.
     */
    internal fun beginRommFromPhone(origin: String) {
        foldcade.shell.chooseRommOrigin(origin)
        beginRommDeviceCode()
    }

    private fun beginRommDeviceCode() {
        deviceCodeJob?.cancel()
        val visit = foldcade.shell.beginDeviceCodeAttempt()
        deviceCodeJob = foldcade.scope.launch {
            var pairedRevision: Int? = null
            while (withContext(Dispatchers.Main.immediate) { foldcade.shell.deviceCodeVisitCurrent(visit) }) {
                val (revision, origin) = withContext(Dispatchers.Main.immediate) {
                    foldcade.shell.connectOriginRevision to normalizeSetupOrigin(foldcade.shell.model.connectOrigin)
                }
                if (origin == null || revision == pairedRevision) {
                    delay(200)
                    continue
                }
                delay(400)
                if (!withContext(Dispatchers.Main.immediate) { foldcade.shell.deviceCodeFormCurrent(visit, revision) }) continue
                pairedRevision = revision
                // Keep the existing token's server-switch policy; restored setups have no visible token.
                val mayConfigure = mayPairRommOrigin(foldcade.store.rommOrigin(), origin, foldcade.store.restoreCredentials(foldcade.credentials))
                if (!withContext(Dispatchers.Main.immediate) {
                    if (!foldcade.shell.deviceCodeFormCurrent(visit, revision)) false
                    else if (!mayConfigure) {
                        foldcade.shell.showSetupHint(app.foldcade.language.RommPhoneCopy.changeServerToken, retry = true)
                        false
                    } else true
                }) continue
                try {
                    // An attempt owns its own wiring. Only validateRommToken publishes an accepted origin.
                    val pairing = requireNotNull(app.foldcade.plugins.romm.RommPlugins.supply).pendingSignIn(origin)
                    val outcome = pollRommDeviceCode(pairing, visit, revision)
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

    private suspend fun pollRommDeviceCode(pairing: app.foldcade.api.plugin.LibraryBackend, visit: Int, originRevision: Int): DeviceCodePoll = pollDeviceCode(
        begin = { pairing.beginDeviceCode() },
        poll = { code ->
            val current = withContext(Dispatchers.Main.immediate) { foldcade.shell.deviceCodeFormCurrent(visit, originRevision) }
            if (current) pairing.pollDeviceCode(code) else DeviceCodePoll.Expired
        },
        onChallenge = { challenge ->
            withContext(Dispatchers.Main.immediate) {
                if (foldcade.shell.deviceCodeFormCurrent(visit, originRevision)) {
                    foldcade.shell.showDeviceCode(app.foldcade.language.PairingCode(challenge.userCode, challenge.verificationUrl))
                }
            }
        },
    )

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
                        if (pluginId == RommCredentials.PLUGIN_ID) {
                            foldcade.shell.reportRestoreSources(setOf("romm"))
                            foldcade.shell.dropLibrary(pluginId)
                        }
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

    private fun openSaveFix(id: String) {
        val incident = foldcade.saveProtection.incident(id) ?: return
        val player = foldcade.plugins.playerIds().mapNotNull(foldcade.plugins::player).firstOrNull { it.saveFolderId() == incident.playerKey }
        val hosted = player is app.foldcade.plugins.duckstation.DuckStationPlayer &&
            (incident.kind != app.foldcade.language.IncidentKind.MaybeNewSave || incident.hostedCardMismatch)
        val steps = when {
            incident.hostedCardMismatch -> app.foldcade.language.SaveProtectionCopy.hostedCardSteps
            incident.besideGame -> SaveProtectionCopy.besideLocationSteps(incident)
            else -> (player as? app.foldcade.api.plugin.SaveLocationProbe)?.locationSteps
                ?: (player as? app.foldcade.api.plugin.HostedSaveFolder)?.hostedFolderSteps
        }
        val route = foldcade.hostedAsk?.pending?.route ?: foldcade.savePickerRequest?.route
        val owner = incident.subject?.game() ?: route?.saves?.takeIf(incident::appliesTo)
            ?: incident.gameIds.singleOrNull()
                ?.takeIf { incident.hostedCardMismatch || incident.armsX2OutsideCard || incident.besideGame || incident.dolphinCustomCard }
                ?.let(foldcade.shell::saveGame)
        val page = foldcade.shell.openSaveFix(incident, steps ?: app.foldcade.language.SaveProtectionCopy.locationSteps(incident.playerKey), player?.id,
            owner = owner, offline = !foldcade.hasValidatedNetwork(), playerManagesFolder = hosted)
        foldcade.scope.launch { foldcade.refreshSaveFixDetails(incident) }
        if (!hosted && !incident.isEdenGrant && !incident.isEdenSessionChange && !incident.dolphinCustomCard) {
            if (page.hasRommDestination && incident.kind == app.foldcade.language.IncidentKind.MaybeNewSave && incident.repair is app.foldcade.language.RepairState.Choosing)
                chooseRepairSave(id)
            else retrySaveRepair(id, start = true)
        }
        if (owner == null || !page.hasRommDestination) return
        foldcade.scope.launch {
            val receipts = try { foldcade.plugins.library(owner.backendId)?.saveReceipts(owner).orEmpty() }
                catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { emptyMap() }
            val hasVersions = receipts.isNotEmpty() || try { foldcade.plugins.serverSaves(owner.backendId, owner).isNotEmpty() }
                catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
            val receipt = receipts.values.filterNotNull().minOrNull()
            // Only a copy Foldcade has seen on RomM is claimed.
            if (receipt == null && !hasVersions) return@launch
            withContext(Dispatchers.Main.immediate) {
                foldcade.shell.updateSaveFix(incident.id) { it.copy(safe = app.foldcade.language.SaveFixSafe(owner.label, receipt)) }
            }
        }
    }

    private fun closeGameForRepair(id: String) {
        val incident = foldcade.saveProtection.incident(id) ?: return
        if (incident.repair !is app.foldcade.language.RepairState.WaitingForClose) return
        val name = foldcade.repairClosePackage(incident)
        if (name == null) {
            retrySaveRepair(id, confirmedAtMillis = System.currentTimeMillis())
            return
        }
        val running = foldcade.runningGames.forPackage(name)
        if (running != null) {
            closeGame(running.copy(companions = emptyList()))
            return
        }
        // After process death there may be an Android task without a tile. The user confirmed
        // Close for this package. Keep the same shutdown settling and read-back as tile Close.
        val displayId = display?.displayId ?: return
        if (!foldcade.closingPlayers.add(name)) return
        foldcade.scope.launch {
            try {
                foldcade.saveFlow.withLock {
                    val closed = withContext(Dispatchers.Main.immediate) {
                        val trackedApp = foldcade.store.repairPlayerOpenedAt(name) != null
                        if (foldcade.launchWork.active || !(foldcade.runningGames.mayHold(name) || trackedApp) ||
                            foldcade.runningGames.forPackage(name) != null || !clearBehindHome(name, displayId)) return@withContext false
                        foldcade.store.noteRepairPlayerClosed(name)
                        foldcade.runningGames.noteHolding(name, closed = true)
                        true
                    }
                    if (closed) {
                        delay(CLOSE_SETTLE_MS)
                        for (stored in foldcade.store.pendingSaveReturns()) {
                            val pending = decodeLibraryReturn(stored, foldcade.plugins::player) ?: continue
                            if (name in pending.player.packageNames) reconcileLocked(pending)
                        }
                    }
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) { foldcade.closingPlayers.remove(name) }
                foldcade.resumeSaveRepairs()
            }
        }
    }

    private fun retrySaveRepair(id: String, start: Boolean = false, confirmedAtMillis: Long? = null) {
        foldcade.scope.launch {
            foldcade.advanceSaveRepair(id, start, confirmedAtMillis = confirmedAtMillis)?.let { foldcade.repairFinished(it) }
        }
    }

    private fun chooseRepairSave(id: String) {
        val incident = foldcade.saveProtection.incident(id) ?: return
        val subject = incident.subject ?: return
        val choosing = incident.repair as? app.foldcade.language.RepairState.Choosing ?: return
        foldcade.scope.launch {
            val prompt = try { foldcade.saveRepairPrompt(incident, subject, choosing) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            withContext(Dispatchers.Main.immediate) {
                if (foldcade.shell.model.saveFix?.incidentId != id || isDestroyed || isFinishing) return@withContext
                if (foldcade.saveProtection.incident(id)?.repair != choosing) return@withContext
                if (prompt == null) {
                    retrySaveRepair(id, start = true)
                    return@withContext
                }
                foldcade.shell.closeSaveFix()
                foldcade.shell.presentSavePicker(null)
                foldcade.shell.presentSaveConflict(prompt)
            }
        }
    }

    internal fun continueRepairedLaunch(incident: app.foldcade.language.SaveIncident) {
        val pending = foldcade.hostedAsk?.pending ?: return
        val subject = incident.subject ?: return
        if (subject.libraryId != pending.route.saves.backendId || subject.gameId != pending.route.saves.remoteKey) return
        foldcade.hostedAsk = null
        foldcade.shell.closeSaveFix()
        clearPendingLaunch()
        launchBackendGame(pending.title, pending.platformId, pending.route, fresh = true)
    }

    private fun pickFixFolder(id: String) {
        foldcade.scope.launch(Dispatchers.Main.immediate) {
            val incident = withContext(Dispatchers.IO) {
                foldcade.saveFlow.withLock {
                    val latest = foldcade.saveProtection.incident(id)?.takeIf {
                        it.repair in setOf(app.foldcade.language.RepairState.Guided, app.foldcade.language.RepairState.NotStarted, app.foldcade.language.RepairState.Binding)
                    } ?: return@withLock null
                    foldcade.saveProtection.put(latest.copy(repair = app.foldcade.language.RepairState.Binding))
                    latest
                }
            } ?: return@launch
            val player = foldcade.plugins.playerIds().mapNotNull(foldcade.plugins::player).firstOrNull { it.saveFolderId() == incident.playerKey } ?: return@launch
            settingsPlayerId = player.id
            folderPurpose = FolderPurpose.Settings
            besideFixPickerId = incident.id.takeIf { incident.besideGame }
            val preferred = if (incident.besideGame || incident.armsX2OutsideCard || incident.dolphinCustomCard || player is RetroArchPlayer && incident.gameIds.isNotEmpty()) incident.bound
                else app.foldcade.language.saveFixTarget(incident)
            val initial = preferred?.identity?.takeIf { incident.isEdenGrant || it.startsWith("primary:") }?.let(::storageFolderDocument)
                ?: foldcade.saveFolderStart(player)
            launchFolderPicker(initial?.let(Uri::parse))
        }
    }

    private suspend fun confirmBesideSaveFolder(player: Player, incident: SaveIncident, folder: Uri) {
        val bound = incident.bound ?: return
        if (!repairFolderMatches(bound.identity, storageIdentity(folder.toString(), packageName))) {
            foldcade.shell.noteSaveFix(SaveProtectionCopy.chooseThere(bound.label))
            return
        }
        try {
            val startRepair = foldcade.saveFlow.withLock {
                val picked = storageIdentity(folder.toString(), packageName)
                val watched = foldcade.playerSaveFolder(player)?.let { storageIdentity(it, packageName) }
                confirmBesideSaveFolder(foldcade.saveProtection, incident.id, picked, watched,
                    hasRomm = { subject ->
                        subject.origin != null && subject.origin == foldcade.store.rommOrigin() &&
                            app.foldcade.plugins.romm.supportsRommRepair(foldcade.plugins.library(subject.libraryId))
                    },
                    preserveLocal = { current, subject -> withContext(Dispatchers.IO) {
                        val gamePlayer = foldcade.plugins.player(subject.playerId)?.takeIf { it.saveFolderId() == player.saveFolderId() }
                            ?: throw SaveNotPlaced()
                        val game = subject.game()
                        val target = subject.targetUri?.let { LaunchTarget.ContentUri(it) }
                        val candidate = foldcade.saveProbes.besideCandidate(gamePlayer, game, target, bound)
                        val bytes = besideBytesForRepair(candidate, current)
                        if (candidate != null && bytes != null)
                            foldcade.saveIncidentCopies.stageBackup(game, gamePlayer, candidate.spot, bytes, current)
                        candidate?.source ?: BesideSaveSource.Missing
                    } },
                    remember = { rememberSaveFolder(folder, player) })
            }
            foldcade.refreshSaveProtection(player)
            if (startRepair) retrySaveRepair(incident.id)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { foldcade.shell.noteSaveFix(Copy.saveNotPlacedBody) }
    }

    private suspend fun confirmDolphinCard(player: DolphinPlayer, incident: SaveIncident, folder: Uri) {
        val done = foldcade.saveFlow.withLock {
            withContext(Dispatchers.IO) {
                val current = foldcade.saveProtection.incident(incident.id)?.takeIf { it.repair == RepairState.Binding } ?: return@withContext false
                val watched = foldcade.playerSaveFolder(player) ?: return@withContext false
                if (storageIdentity(watched, packageName) != storageIdentity(folder.toString(), packageName)) return@withContext false
                val subject = current.subject ?: return@withContext false
                val target = subject.targetUri?.let { LaunchTarget.ContentUri(it) } ?: return@withContext false
                val game = subject.game()
                if (foldcade.playerSaves.probeSpots(player, game, target, folder.toString()).isEmpty()) return@withContext false
                if (!foldcade.saveProbes.stageDolphinAlternate(player, game, target)) throw SaveNotPlaced()
                foldcade.saveIncidentCopies.keep(game, player)
                val latest = foldcade.saveProtection.incident(incident.id) ?: return@withContext false
                foldcade.saveProtection.put(latest.copy(repair = RepairState.Done, recoveryRequired = false))
                true
            }
        }
        if (done) foldcade.refreshSaveProtection(player)
        else foldcade.shell.noteSaveFix(SaveProtectionCopy.dolphinCustomCardSteps)
    }

    /** An external save or NAND grant supplements Eden's provider grant for keys and config. */
    private suspend fun allowEdenSaveFolder(player: EdenPlayer, incident: app.foldcade.language.SaveIncident, uri: Uri) {
        val expected = incident.live ?: return
        if (uri.authority != SharedPaths.EXTERNAL_STORAGE_AUTHORITY || !containsFolder(folderDocumentId(uri.toString()), expected.identity)) {
            foldcade.shell.noteSaveFix(app.foldcade.language.SaveProtectionCopy.wrongPick(player.displayName, expected.label))
            return
        }
        val route = foldcade.hostedAsk?.pending?.route ?: foldcade.savePickerRequest?.route
        val granted = try {
            foldcade.saveFlow.withLock {
                withContext(Dispatchers.IO) {
                    // The next save operation for every game checks its local bytes before a head can be placed.
                    foldcade.saveProtection.rebound(player.saveFolderId())
                    if (!persistTreeGrant(write = true) { flags -> contentResolver.takePersistableUriPermission(Uri.parse(folderGrantUri(uri.toString())), flags) })
                        return@withContext false
                    val data = foldcade.playerSaveFolder(player) ?: return@withContext false
                    when (val access = foldcade.playerSaves.edenSaveAccess(player, data)) {
                        is EdenSaveAccess.Ready -> {
                            foldcade.saveProtection.put(incident.copy(recoveryRequired = true))
                            if (route != null && route.play.availability != Availability.RemoteOnly) {
                                val target = foldcade.plugins.ensureLocal(route.play.backendId, route.play)
                                val spots = foldcade.playerSaves.spots(player, route.saves, target, eden = access)
                                foldcade.saveIncidentCopies.stage(route.saves, player, spots, incident, mark = true)
                                foldcade.saveIncidentCopies.keep(route.saves, player)
                            }
                            val current = foldcade.saveProtection.incidents().firstOrNull { it.id == incident.id } ?: incident
                            foldcade.saveProtection.put(current.copy(repair = app.foldcade.language.RepairState.Done, recoveryRequired = false))
                            true
                        }
                        is EdenSaveAccess.NeedsGrant -> {
                            // The save root and NAND may be on separate trees. Refresh opens the next grant step.
                            foldcade.saveProtection.put(incident.copy(repair = app.foldcade.language.RepairState.NotStarted))
                            true
                        }
                        EdenSaveAccess.Unknown -> false
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            foldcade.shell.noteSaveFix(Copy.saveNotPlacedBody)
            return
        }
        if (!granted) foldcade.shell.noteSaveFix(Copy.folderGrantFailed)
        foldcade.refreshSaveProtection(player)
        foldcade.shell.updateSaveFix(incident.id) { it.copy(offline = !foldcade.hasValidatedNetwork(), note = if (granted) null else it.note) }
    }

    private fun finishSaveFix(id: String, openSaves: Boolean = true) {
        val incident = foldcade.saveProtection.incidents().firstOrNull {
            it.id == id && (it.repair == app.foldcade.language.RepairState.Done || it.isEdenSessionChange)
        }
            ?: foldcade.shell.model.saveFix?.resolved?.takeIf { it.id == id } ?: return
        val tileId = incident.gameIds.singleOrNull()?.takeIf { incident.hostedCardMismatch || incident.armsX2OutsideCard || incident.besideGame }
            ?: foldcade.hostedAsk?.pending?.route?.tileId ?: foldcade.savePickerRequest?.route?.tileId
            ?: incident.subject?.let { homeGameId(it.libraryId, it.gameId) }
        foldcade.scope.launch {
            withContext(Dispatchers.IO) {
                // Keep offline-copy references and alternate paths for the next launch's recheck.
                foldcade.saveFlow.withLock {
                    if (incident.isEdenSessionChange) foldcade.saveProtection.close(incident.id)
                    foldcade.saveProtection.incidents().filter { it.playerKey == incident.playerKey && it.repair == app.foldcade.language.RepairState.Done &&
                        it.staged.isEmpty() && it.retroArchAlternate() == null }
                        .forEach { foldcade.saveProtection.close(it.id) }
                }
            }
            withContext(Dispatchers.Main.immediate) {
                foldcade.hostedAsk = null
                clearPendingLaunch()
                foldcade.shell.closeSaveFix()
            }
            foldcade.refreshSaveProtection()
            // Offline the page only closes: Saves would just say it can't reach RomM.
            if (openSaves) withContext(Dispatchers.Main.immediate) { tileId?.let(::openSaves) }
        }
    }

    private fun showSaveCheck(playerId: String) {
        val setting = foldcade.shell.model.playerSaves.firstOrNull { it.playerId == playerId } ?: return
        val copy = app.foldcade.language.SaveProtectionCopy
        val detail = when (val location = setting.location) {
            is app.foldcade.language.LocationFact.Verified -> copy.checkDetail(location, setting.label, style = foldcade.deviceStatus.status.value.dateStyle)
            is app.foldcade.language.LocationFact.Bound -> location.configDetail ?: when {
                location.cardOutside -> copy.armsX2CardOutside
                !location.configUnavailable -> copy.checkByPlaying
                playerId == ArmsX2Player.ID -> copy.armsX2ConfigUnavailableDetail
                setting.playerId == Aps3ePlayer.ID || setting.playerId == "dolphin.gamecube" -> copy.cantReadConfig
                else -> copy.configUnavailableDetail(setting.label)
            }
            else -> copy.checkByPlaying
        }
        foldcade.shell.showDialog(playerNotice("Check", detail, foldcade.shell.model.layout.menuScreen))
    }

    private fun choosePlayerSave(playerId: String) {
        clearPendingLaunch()
        if (foldcade.plugins.player(playerId) is EdenPlayer) {
            openEdenFolder(fromOffer = false)
            return
        }
        settingsPlayerId = playerId
        folderPurpose = FolderPurpose.Settings
        openSaveFolderPicker(foldcade.plugins.player(playerId))
    }

    /** Optional BIOS setup opens from R1 without holding or restarting a game launch. */
    private fun openFirmwareFolder(player: Player) {
        settingsPlayerId = player.id
        if (player is RetroArchPlayer) {
            folderPurpose = FolderPurpose.Firmware
            launchFolderPicker(Uri.parse(foldcade.store.playerFirmwareFolder("retroarch") ?: app.foldcade.plugins.retroarch.DEFAULT_SAVE_FOLDER))
        } else {
            folderPurpose = FolderPurpose.Settings
            openSaveFolderPicker(player)
        }
    }

    /**
     * One handoff for all SAF trees: the free screen guides the choice until a result arrives.
     * OpenDocumentTree supplies EXTRA_INITIAL_URI and returns null for cancellation:
     * https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.OpenDocumentTree
     */
    private fun launchFolderPicker(initialUri: Uri?, opensThere: Boolean = initialUri != null) {
        val playerId = when (folderPurpose) {
            FolderPurpose.EdenFolder, FolderPurpose.EdenSaveOffer -> foldcade.plugins.playerIds().firstOrNull { foldcade.plugins.player(it) is EdenPlayer }
            FolderPurpose.RetroArchCores -> foldcade.plugins.playerIds().firstOrNull { foldcade.plugins.player(it) is RetroArchPlayer }
            FolderPurpose.Library -> null
            else -> settingsPlayerId
        }
        val guide = app.foldcade.language.FolderGuide(hostScreen(), playerId, opensThere)
        folderGuide = guide
        foldcade.shell.showFolderGuide(guide)
        try {
            folderPicker.launch(initialUri)
        } catch (failure: RuntimeException) {
            besideFixPickerId = null
            folderGuide = null
            foldcade.shell.showFolderGuide(null)
            throw failure
        }
    }

    private fun openSettingsPage(page: Class<*>, on: HostScreen? = null, configure: (Intent) -> Unit = {}) {
        // A key received on the grid still opens this page on Settings' screen.
        val screen = on ?: foldcade.shell.model.settingsActivityScreen(hostScreen())
        val assignment = displays.assignment(foldcade.store.session.defaultDisplayIsTop)
        val displayId = if (screen == HostScreen.Top) assignment.topDisplayId else assignment.bottomDisplayId
        val host = displayId?.let(foldcade::homeOnDisplay) ?: this
        // Settings may be on the free screen while Android covers its previous native page.
        foldcade.settingsPageActivity?.finish()
        foldcade.noteSettingsPage(host)
        try {
            host.startActivity(Intent(host, page).also(configure))
        } catch (failure: RuntimeException) {
            displays.panelFor(host, foldcade.store.session.defaultDisplayIsTop)?.let { panel ->
                foldcade.store.update { it.foldcadeResumed(panel) }
            }
            throw failure
        }
    }

    /** Opens the save-folder picker at the start the last look found. */
    private fun openSaveFolderPicker(player: Player?) {
        settingsPlayerId = player?.id
        launchFolderPicker(foldcade.saveFolderStart(player)?.let(Uri::parse))
    }

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
            pending.intent.component?.packageName?.let(foldcade::noteRepairAppOpened)
            foldcade.externalPlay.notePlayerRestarted()
            foldcade.runningGames.forPackage(foldcade.externalPlay.packageName)?.let { running ->
                foldcade.runningGames.note(running.copy(startedAtMillis = foldcade.externalPlay.startedAtMillis ?: running.startedAtMillis))
            }
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** The launch to try again once the RetroArch cores offer is answered, and its generation. */
    private var retroArchRetry: Pair<Int, () -> Unit>? = null

    /**
     * The Eden package the restart dialog's Continue opens App info for.
     * Lost when this activity is recreated; Continue then uses the installed Eden.
     */
    private var edenRestartPackage: String? = null

    /**
     * What Eden needs before [player] starts. A grant is checked and filled in.
     * The save-folder flow owns the single offer. Returns true when Eden has
     * to restart before it can see files Foldcade added. This runs before save
     * placement, including the local-library launch path.
     */
    private suspend fun edenHeld(
        player: Player,
        generation: Int,
        libraryId: String?,
        remoteKey: String?,
    ): Boolean {
        val packageName = installedEdenPackage(player, ::packageInstalled) ?: return false
        // Before Eden's documents provider. Opening it starts Eden and clears the stopped state.
        if (edenRestartBlocks(packageName, generation)) return true
        withContext(Dispatchers.Main.immediate) { foldcade.refreshEdenSetting() }
        val holds = foldcade.edenFolderReader.granted(packageName) != null
        val gate = edenGate(holds)
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
                // A held external-storage grant need not start Eden's provider.
                // If Eden is still stopped, it has not cached the old files.
                return edenRestartBlocks(packageName, generation)
            }
            EdenGate.Ready -> return false
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
        if (!fromOffer) clearPendingLaunch()
        folderPurpose = if (fromOffer) FolderPurpose.EdenSaveOffer else FolderPurpose.EdenFolder
        launchFolderPicker(EdenFolders.root(packageName))
    }

    /**
     * Not now, or the Eden picker closed from a launch or from Settings.
     * The offer is answered. A held launch starts; Settings has none.
     */
    private fun declineEdenFolder() {
        if (pendingBackend != null || pendingLaunch != null) skipSaveFolderAndLaunch(SaveFolderAnswer.PickerCancelled)
    }

    private fun useEdenFolder(uri: Uri, saveOffer: Boolean = false, continueLaunch: Boolean = true): Boolean {
        // Derived again so a recreated activity still expects the package launches use.
        val expected = EDEN_PACKAGES.firstOrNull(::packageInstalled)
        val packageName = EdenFolders.packageOf(uri)
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val flags = read or write
        val root = packageName != null && packageName == expected && EdenFolders.isRoot(uri)
        val grant = Uri.parse(folderGrantUri(uri.toString()))
        val took = root && try {
            contentResolver.takePersistableUriPermission(grant, flags)
            true
        } catch (_: SecurityException) {
            false
        }
        val held = took && contentResolver.persistedUriPermissions.any { permission ->
            folderGrantUri(permission.uri.toString()) == grant.toString() && permission.isReadPermission && permission.isWritePermission
        }
        if (!held || packageName != expected) {
            // The held launch stays. Try again opens the picker; Play without syncing starts it, and Back cancels it.
            if (took) releaseUnlessUsed(uri.toString(), flags)
            foldcade.refreshEdenSetting()
            val held = pendingBackend != null || pendingLaunch != null
            foldcade.shell.present(edenWrongFolderDialog(hostScreen(), launchHeld = held, needed = held && needsItsFolder(pendingBackend?.playerId?.let(foldcade.plugins::player) ?: playerForPendingLaunch())))
            return false
        }
        val previous = foldcade.store.edenFolder(packageName)
        foldcade.store.setEdenFolder(packageName, uri.toString())
        if (previous != null && previous != uri.toString()) releaseUnlessUsed(previous, flags)
        foldcade.refreshEdenSetting()
        foldcade.plugins.playerIds().mapNotNull(foldcade.plugins::player).filterIsInstance<EdenPlayer>().firstOrNull()?.let {
            foldcade.saveProbes.markBound(it)
        }
        foldcade.refreshSaveSync()
        if (continueLaunch && saveOffer) skipSaveFolderAndLaunch(SaveFolderAnswer.Chosen)
        return true
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
        gameId: String,
        readCores: Boolean = true,
        retry: () -> Unit,
    ): Boolean = !foldcade.launchWork.prepare(generation) {
        !prepareRetroArch(player, generation, gameId, readCores, retry)
    }

    private suspend fun prepareRetroArch(
        player: RetroArchPlayer,
        generation: Int,
        gameId: String,
        readCores: Boolean,
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
        if (gate is RetroArchGate.MissingCore) {
            val owner = Any()
            try {
                withContext(Dispatchers.Main.immediate) {
                    if (launchCurrent(generation)) foldcade.shell.noteCoreDownload(gameId, owner, gate.core.libraryName, null)
                }
                val activity = packageManager.getActivityInfo(ComponentName(packageName, CORE_SIDELOAD_ACTIVITY), 0)
                if (!activity.exported || !activity.enabled || activity.permission != null) throw java.io.IOException("Core sideload unavailable")
                val info = packageManager.getApplicationInfo(packageName, 0)
                val abi = RetroArchAbi.fromLibraryDirectory(info.nativeLibraryDir)
                    ?: throw java.io.IOException("Unsupported RetroArch architecture")
                val prepared = foldcade.retroArchCoreDownload.prepare(packageName, gate.core, abi) { bytes, total ->
                    foldcade.scope.launch(Dispatchers.Main.immediate) {
                        if (launchCurrent(generation)) foldcade.shell.noteCoreDownload(
                            gameId, owner, gate.core.libraryName,
                            total?.takeIf { it > 0 }?.let { (bytes.toDouble() / it).toFloat().coerceIn(0f, 1f) },
                        )
                    }
                }
                return withContext(Dispatchers.Main.immediate) {
                    if (!launchCurrent(generation)) true else {
                        foldcade.launchWork.retroArchCore = prepared
                        false
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                withContext(Dispatchers.Main.immediate) {
                    if (launchCurrent(generation)) {
                        retroArchRetry = generation to retry
                        foldcade.shell.present(app.foldcade.language.coreDownloadFailedDialog(gate.core.libraryName, hostScreen()))
                    }
                }
                return true
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) { foldcade.shell.endCoreDownload(gameId, owner) }
            }
        }
        withContext(Dispatchers.Main.immediate) {
            if (!launchCurrent(generation)) return@withContext
            val screen = hostScreen()
            when (gate) {
                RetroArchGate.OfferCores -> {
                    retroArchRetry = generation to retry
                    foldcade.shell.present(retroArchCoresDialog(screen))
                }
                is RetroArchGate.MissingCore -> Unit // handled by the download above
                is RetroArchGate.Storage -> {
                    if (gate.storage == RetroArchStorage.NotAllowed) retroArchRetry = generation to retry
                    foldcade.shell.present(
                        if (gate.storage == RetroArchStorage.Never) retroArchStoreBuildDialog(screen) else retroArchStorageDialog(screen),
                    )
                }
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
        launchFolderPicker(packageName?.let(RetroArchCoreReader::dataFolder))
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
        releaseGrant(Uri.parse(folderGrantUri(uri)), flags)
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

    private val retroArchStorageSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val retry = retroArchRetry ?: return@registerForActivityResult
        val pkg = foldcade.retroArchPlayer()?.cores?.activePackage() ?: return@registerForActivityResult
        foldcade.scope.launch {
            val allowed = runCatching {
                retroArchStorage(packageManager.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)) == RetroArchStorage.Granted
            }.getOrDefault(false)
            withContext(Dispatchers.Main.immediate) {
                if (allowed) retryAfterRetroArchCores(retry) else cancelLaunches()
            }
        }
    }

    private fun openRetroArchPermissions() {
        val pkg = foldcade.retroArchPlayer()?.cores?.activePackage() ?: return
        // MANAGE_APP_PERMISSIONS is protected by GRANT_RUNTIME_PERMISSIONS on AOSP/Thor.
        // App info is the public deep link for this legacy READ_EXTERNAL_STORAGE build.
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        try {
            retroArchStorageSettings.launch(intent)
        } catch (_: ActivityNotFoundException) {
            cancelLaunches()
            openRetroArch()
        }
    }

    /** Opens [packageName] on the screen a single-screen app would take, for the user to set something up there. */
    private fun openAppForSetup(packageName: String) {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        val session = foldcade.store.session
        // The screen it was asked from. The dark screen's keys are the grid's, so it opens there.
        val here = displays.panelFor(this, session.defaultDisplayIsTop)?.let { inputScreen(it).panel() } ?: Panel.Top
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
        foldcade.noteRepairAppOpened(packageName)
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
        return playerFor(game)
    }

    /**
     * The picked folder or one unambiguous save folder inside it. With [ask],
     * a folder that does not fit brings the save-folder ask back and the launch
     * still waits; without, a notice says so.
     */
    private suspend fun pickedSaveFolder(uri: Uri, player: Player?, ask: Boolean): Uri? {
        if (player is EdenPlayer) return uri
        val narrowed = withContext(Dispatchers.IO) {
            val holder = player as? SaveFolderHolder ?: return@withContext uri
            if (player is Aps3ePlayer) {
                val pkg = player.packageNames.firstOrNull(::packageInstalled)
                return@withContext uri.takeIf { pkg != null && providerSaveRootPackage(player, it.toString()) == pkg }
            }
            if (player is DolphinPlayer && providerSaveRootPackage(player, uri.toString()) != null) return@withContext uri
            if (player is RetroArchPlayer && folderDocumentId(uri.toString()).substringAfter(':').substringAfterLast('/').equals("RetroArch", true)) {
                val docs = AndroidSaveDocuments(contentResolver)
                if (!docs.createFolder(uri.toString(), "saves")) return@withContext null
                return@withContext docs.children(uri.toString(), "")?.firstOrNull { it.isFolder && it.name == "saves" }?.uri?.let(Uri::parse)
            }
            AndroidSaveFolderFinder(foldcade).finder(player, emptyList()).narrow(holder, uri.toString())?.let(Uri::parse)
                // A folder the provider cannot list yet passes, as it always has.
                ?: uri.takeIf { AndroidSaveDocuments(contentResolver).childNames(it.toString()) == null }
        }
        if (narrowed != null) return narrowed
        val wrong = rejectedSaveFolderCopy(player)
        if (ask) {
            foldcade.shell.present(saveFolderDialog(
                hostScreen(), syncs = true, wrong = wrong,
                playerName = player?.displayName, needed = needsItsFolder(player),
            ))
        } else if (player == null || !foldcade.shell.noteSaveSetup(player.id, rejectedSaveFolderCopy(player, inSetup = true))) {
            // Set up saves says it in its own line, under the step, which stays.
            foldcade.shell.present(playerNotice(Copy.saveFolderTitle, wrong, hostScreen()))
        }
        return null
    }

    private fun rememberSaveFolder(uri: Uri, player: Player?): Boolean {
        if (player is EdenPlayer) {
            return useEdenFolder(uri, continueLaunch = false)
        }
        val identity = storageIdentity(uri.toString(), packageName)
        val beside = if (player?.probesContentSaves == true)
            foldcade.saveProtection.incidents().filter { it.playerKey == player.saveFolderId() && it.besideGame && it.blocksLaunch }
        else emptyList()
        beside.firstOrNull { it.live?.identity == identity }?.let { incident ->
            val body = incident.bound?.let { SaveProtectionCopy.chooseThere(it.label) } ?: Copy.saveFolderBody
            foldcade.shell.present(playerNotice(Copy.saveFolderTitle, body, hostScreen()))
            return false
        }
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (!persistTreeGrant(write = true) { flags -> contentResolver.takePersistableUriPermission(Uri.parse(folderGrantUri(uri.toString())), flags) }) {
            foldcade.shell.present(playerNotice(Copy.saveFolderTitle, Copy.folderGrantFailed, hostScreen()))
            return false
        }
        if (player is SaveFolderHolder) {
            val key = player.saveFolderId()
            val previous = foldcade.store.playerSaveFolder(key)
            val changed = beside.isNotEmpty() && previous?.let { storageIdentity(it, packageName) } != identity
            if (changed) foldcade.saveProtection.rebound(key)
            if (previous != null && (player is Aps3ePlayer || player is DolphinPlayer)) {
                // Another install can have the same document ID. Fence its working bytes before the binding changes.
                val old = FolderRef(storageIdentity(previous, packageName), folderPathLabel(previous))
                val pkg = providerSaveRootPackage(player, previous)?.let { name ->
                    try { packageManager.getPackageInfo(name, 0) } catch (_: PackageManager.NameNotFoundException) { null }
                }
                if (old.identity != identity ||
                    pkg != null && foldcade.saveProtection.reinstalled(key, old, pkg.packageName, pkg.firstInstallTime) ||
                    foldcade.saveProtection.incidents().any { it.playerKey == key && it.kind == IncidentKind.LostAccess && it.blocksLaunch }) {
                    foldcade.saveProtection.rebound(key)
                }
            }
            // Every player that shares the folder gets it, as at startup.
            for (id in foldcade.plugins.playerIds()) {
                val sharing = foldcade.plugins.player(id) ?: continue
                if (sharing is SaveFolderHolder && sharing.saveFolderId() == key) sharing.bindSaveFolder(uri.toString())
            }
            foldcade.store.setPlayerSaveFolder(key, uri.toString(), durable = changed)
            foldcade.saveProbes.markBound(player, picked = true)
            if (beside.isNotEmpty()) foldcade.saveProtection.retargetBesideRepairs(key, FolderRef(identity, folderPathLabel(uri.toString())))
            foldcade.dismissNotice("firmware-folder:$key")
            if (previous != null && previous != uri.toString() && foldcade.saveProtection.incidents().none { it.playerKey == key }) {
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

    private fun showThorSetup(playerId: String) {
        foldcade.scope.launch {
            val player = foldcade.plugins.player(playerId) ?: return@launch
            val setup = withContext(Dispatchers.IO) { foldcade.thorPresets.read(playerId, foldcade.playerSaveFolder(player)) } ?: return@launch
            foldcade.shell.setThorSetup(playerId, setup)
            foldcade.shell.showDialog(app.foldcade.language.thorSetupDialog(playerId, setup, hostScreen()))
        }
    }

    private fun changeThorSetup(playerId: String, restore: Boolean) {
        foldcade.scope.launch {
            val player = foldcade.plugins.player(playerId) ?: return@launch
            val setup = withContext(Dispatchers.IO) {
                if (restore) foldcade.thorPresets.restore(playerId, foldcade.playerSaveFolder(player))
                else foldcade.thorPresets.apply(playerId, foldcade.playerSaveFolder(player))
            } ?: return@launch
            foldcade.shell.setThorSetup(playerId, setup)
            // A successful settings action resolves the standing launch notice as well.
            if (setup.status in setOf(app.foldcade.language.ThorSetupStatus.Applied,
                    app.foldcade.language.ThorSetupStatus.Kept, app.foldcade.language.ThorSetupStatus.Restored)) {
                foldcade.dismissNotice(app.foldcade.language.ThorSetupCopy.noticePrefix + playerId)
            }
            foldcade.shell.showDialog(app.foldcade.language.thorSetupDialog(playerId, setup, hostScreen()))
        }
    }

    /**
     * What [player] needs once before it starts, read off the main thread from the
     * folder the user picked for it, or asked with none when none is picked.
     * Null for a player that keeps no folder.
     */
    private suspend fun playerSetupSteps(player: Player): String? {
        if (player !is SaveFolderHolder) return null
        val folder = foldcade.playerSaveFolder(player)
        foldcade.applyThorPreset(player)
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
                    // Closing a companion resumes its Home too. Close already owns this
                    // persisted return; do not queue a second read behind its save lock.
                    if (libraryReturn == null && foldcade.externalPlay.sessionId == null &&
                        lost.player.packageNames.none { it in foldcade.closingPlayers }) reconcile(lost)
                }
            }
        }
    }

    /**
     * Bind the whole screen and its later effects to the menu's original game. A game RomM doesn't
     * have opens on its own copy, with nothing to load: the page shows how its saves can get there.
     */
    private fun openSaves(gameId: String, message: String? = null, owner: Pair<String, String>? = null): Boolean {
        if (foldcade.launchWork.active) return false
        val route = foldcade.shell.savesMenuRoute(gameId) ?: return false
        @Suppress("NAME_SHADOWING") val owner = route.servers.firstOrNull { owner != null && it.backendId == owner.first && it.remoteKey == owner.second }
            ?: route.servers.firstOrNull { it == route.saves } ?: route.servers.firstOrNull() ?: route.saves
        val onRomm = route.servers.isNotEmpty()
        val player = foldcade.shell.savePlayer(route)
        val target = app.foldcade.language.SavePickerTarget(route.tileId, owner.backendId, owner.remoteKey, player?.id, ++foldcade.savePickerSerial)
        foldcade.savePickerRequest = SavePickerRequest(target, route, owner, player)
        val health = foldcade.shell.saveHealthOf(gameId)
        foldcade.shell.presentSavePicker(app.foldcade.language.SavePickerState(
            target, owner.label, player?.displayName ?: app.foldcade.language.SavePickerCopy.unknown,
            health, ownSlots = player?.saveDeclarations(owner).orEmpty().map { it.slot },
            deviceName = app.foldcade.language.SavePickerCopy.thisDeviceName(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty()),
            loading = onRomm, message = message, onRomm = onRomm,
        ).let { it.copy(route = foldcade.shell.savePickerRoute(target, foldcade.shell.savePickerHealth(target))).settled() })
        if (onRomm) loadSavePicker(target)
        return true
    }

    /**
     * The open Saves page again for [target]'s game and the RomM copy it named, now with the emulator
     * that plays it: a switch made while the old one held the game took effect when it closed.
     */
    internal fun reopenSaves(target: app.foldcade.language.SavePickerTarget) {
        if (isDestroyed || isFinishing || foldcade.shell.model.savePicker?.target != target) return
        openSaves(target.gameId, owner = target.libraryId to target.remoteKey)
    }

    /** The Saves page's route: switch the game to an emulator Foldcade syncs, then show its saves with it. */
    private fun useSavePlayer(target: app.foldcade.language.SavePickerTarget, choice: app.foldcade.language.PlayerChoice) {
        if (foldcade.shell.model.savePicker?.target != target) return
        // A running session is never interrupted: the choice applies once its emulator closes.
        val running = foldcade.shell.runningPlayer(target.gameId)?.takeIf { it.id != choice.playerId }
        foldcade.shell.useSavePlayer(target.gameId, choice)
        val message = running?.let { app.foldcade.language.SaveRouteCopy.afterClose(choice.label, it.displayName) }
            ?: app.foldcade.language.SaveRouteCopy.switched(choice.label)
        // A launch starting meanwhile owns the screen; this page just stops waiting and says the switch happened.
        if (!openSaves(target.gameId, message, owner = target.libraryId to target.remoteKey)) foldcade.shell.updateSavePicker(target) { it.copy(busy = false, message = message) }
    }

    private fun loadSavePicker(target: app.foldcade.language.SavePickerTarget) {
        val request = foldcade.savePickerRequest?.takeIf { it.target == target } ?: return
        // A game RomM doesn't have has no list to ask for.
        if (request.route.servers.isEmpty()) return
        foldcade.scope.launch {
            try {
                val listed = foldcade.plugins.serverSaves(request.owner.backendId, request.owner, request.player)
                val backups = foldcade.plugins.serverSaveBackups(request.owner.backendId, request.owner)
                val origins = foldcade.plugins.serverSaveOrigins(request.owner.backendId, request.owner)
                // RomM names an emulator by its id; players know it by its name.
                val names = foldcade.plugins.playerIds().mapNotNull { foldcade.plugins.player(it) }
                    .associate { it.id.substringBefore('.').lowercase() to it.displayName }
                val saves = listed.map {
                    // A source names the emulator only when it is an emulator's id outright.
                    val label = it.emulator?.substringBefore('.')?.lowercase()?.let(names::get)
                        ?: it.source?.takeIf { _ -> it.emulator == null }?.lowercase()?.let(names::get)
                    val origin = origins[it.id]?.let { detail -> app.foldcade.language.SavePickerOrigin(detail.client, detail.deviceName, detail.thisDevice) }
                    app.foldcade.language.SavePickerSave(it.id, it.slot, it.emulator, it.source, it.updatedAtMillis, it.sizeBytes, it.playing, it.screenshotUri, it.id in backups, origin, label,
                        folderLabel = it.source.takeIf { _ -> request.owner.backendId == RommCredentials.PLUGIN_ID && it.id in backups })
                }
                withContext(Dispatchers.Main.immediate) {
                    foldcade.shell.updateSavePicker(target) { previous ->
                        val id = previous.focused?.id
                        val index = if (previous.index == 0) 0 else (saves.indexOfFirst { it.id == id } + 1).coerceAtLeast(0)
                        previous.copy(saves = saves, index = index, loading = false, error = false,
                            health = foldcade.shell.savePickerHealth(target), ownSlots = request.player?.saveDeclarations(request.owner).orEmpty().map { it.slot }).settled()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // With no network at all, the page sends the player to Wi-Fi and reloads once it is back.
                val offline = !foldcade.hasValidatedNetwork()
                withContext(Dispatchers.Main.immediate) { foldcade.shell.updateSavePicker(target) { it.copy(loading = false, error = true, offline = offline) } }
            }
        }
    }

    /** The Saves page tries again by itself, as when the network it lacked comes back. */
    private fun retrySavePicker(target: app.foldcade.language.SavePickerTarget) {
        foldcade.shell.updateSavePicker(target) { it.copy(loading = true, error = false, offline = false, message = null) }
        loadSavePicker(target)
    }

    private fun changeServerSave(target: app.foldcade.language.SavePickerTarget, saveId: String?, destination: app.foldcade.language.SaveDestination? = null) {
        val request = foldcade.savePickerRequest?.takeIf { it.target == target } ?: return
        if (foldcade.shell.model.savePicker?.target != target) return
        val incident = request.player?.let { player -> foldcade.saveProtection.incidents().firstOrNull { it.gatesLaunch(player, request.owner) } }
        if (saveId != null && incident != null) {
            foldcade.shell.updateSavePicker(target) { it.copy(busy = false, confirmation = null) }
            openSaveFix(incident.id)
            return
        }
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
                    val eden = (installedPlayer as? EdenPlayer)?.let { player ->
                        withContext(Dispatchers.IO) {
                            val access = foldcade.playerSaves.edenSaveAccess(player, request.owner)
                            val probe = foldcade.saveProbes.snapshot(player)
                            if (probe != null) foldcade.saveProbes.check(player, probe, eden = access)
                            foldcade.publishSaveGate(player, probe)
                            if (needsPlayer) foldcade.saveProtection.incidents().firstOrNull { it.playerKey == player.saveFolderId() && it.blocksLaunch }
                                ?.let { throw SaveProtectionStop(it) }
                            access
                        }
                    }
                    ServerSaveFlow(foldcade.playerSaves, foldcade.plugins, foldcade.store::saveAwaitsLoad,
                        preserveOutside = foldcade.saveIncidentCopies::preserveBeforeLaunch,
                        protectionGate = { player, game, local ->
                            if (player is Aps3ePlayer || player is DolphinPlayer) withContext(Dispatchers.IO) {
                                var reading: ProbeSnapshot? = null
                                try { foldcade.saveProbes.gate(player, game, local) { reading = it } }
                                finally {
                                    val uri = (local as? LaunchTarget.ContentUri)?.uri ?: (local as? LaunchTarget.SharedFile)?.uri
                                    foldcade.associateSaveRepair(player, incidentGame(game, player, uri, foldcade.store.rommOrigin(), reading))
                                    foldcade.publishSaveGate(player, reading)
                                }
                            }
                        },
                        onHeadPlaced = { game, player ->
                            foldcade.saveIncidentCopies.headPlaced(game, player)
                            if (saveId != null && destination == null) {
                                // Playing a chosen save is the explicit choice a first-link stop waits for.
                                foldcade.saveIncidentCopies.firstLinkChosen(game, player)
                                val id = regressionIncidentId(game, player)
                                if (foldcade.saveProtection.incident(id)?.repair == app.foldcade.language.RepairState.Guided) {
                                    foldcade.saveProtection.close(id)
                                    foldcade.refreshSaveProtection(player, flowHeld = true)
                                }
                            }
                        },
                        awaitLoad = foldcade.store::setSaveAwaitsLoad)
                        .perform(request.copy(player = installedPlayer), saveId, destination, eden)
                }
                success = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (stop: SaveProtectionStop) {
                withContext(Dispatchers.Main.immediate) { openSaveFix(stop.incident.id) }
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
                    launchScreen = foldcade.runningOn(target.gameId)?.panel ?: foldcade.shell.model.playScreen().panel()
                    launchBackendGame(request.owner.label, request.route.play.platformId, request.route, fresh = true)
                } else if (success) loadSavePicker(target)
            }
        }
    }

    /** Shows the first conflict that is still waiting, unless a launch or a preview is up. */
    internal fun refreshSaveConflict() {
        if (foldcade.suppressSaveConflict) return
        if (foldcade.shell.model.saveConflict?.let { it.preview || it.repair != null } == true) return
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
                if (foldcade.shell.model.saveConflict?.let { it.preview || it.repair != null } == true) return@withContext
                foldcade.shell.presentSaveConflict(pending.firstOrNull()?.toPrompt())
            }
        }
    }

    private fun resolveSaveConflict(effect: Effect.ResolveSaveConflict) {
        if (effect.preview) return
        val repair = effect.repair
        if (repair != null) {
            resolveRepairChoice(effect, repair)
            return
        }
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

    private fun resolveRepairChoice(effect: Effect.ResolveSaveConflict, choice: app.foldcade.language.RepairState.Choosing) {
        if (effect.action == SaveConflictAction.DecideLater) {
            foldcade.hostedAsk = null
            clearPendingLaunch()
            return
        }
        val incident = foldcade.saveProtection.incident(effect.id) ?: return
        val subject = incident.subject ?: return
        val held = foldcade.hostedAsk?.pending
        // Keep Later available while the selected save is being checked and placed.
        if (effect.action == SaveConflictAction.KeepBoth) foldcade.hostedAsk = null
        foldcade.shell.openSaveFix(incident, null, subject.playerId, owner = subject.game())
        foldcade.scope.launch {
            val done = foldcade.advanceSaveRepair(incident.id, choice = choice to effect.action)
            withContext(Dispatchers.Main.immediate) {
                val mayLaunch = done != null && foldcade.shell.model.saveFix?.incidentId == incident.id && !isDestroyed && !isFinishing
                if (mayLaunch) foldcade.hostedAsk = null
                if (done != null) foldcade.repairFinished(done)
                if (!mayLaunch) return@withContext
                foldcade.hostedAsk = null
                foldcade.shell.closeSaveFix()
                clearPendingLaunch()
                if (effect.action == SaveConflictAction.KeepBoth) return@withContext
                val route = held?.route?.takeIf { it.saves.backendId == subject.libraryId && it.saves.remoteKey == subject.gameId }
                    ?: foldcade.shell.launchRoute(foldcade.shell.tileOf(homeGameId(subject.libraryId, subject.gameId))) ?: return@withContext
                launchScreen = foldcade.shell.model.playScreen().panel()
                launchBackendGame(subject.label, subject.platformId, route, fresh = true)
            }
        }
    }

    /** Reads back what the player wrote and gives it to the library. */
    private fun reconcile(pending: LibraryReturn) {
        val returned = pending.copy(sessionEndedAtMillis = pending.sessionEndedAtMillis ?: System.currentTimeMillis())
        pending.player.packageNames.firstNotNullOfOrNull { name -> foldcade.runningGames.forPackage(name)?.takeIf { it.saves == pending } }?.let {
            foldcade.runningGames.note(it.copy(saves = returned))
        }
        foldcade.scope.launch {
            foldcade.saveFlow.withLock { reconcileLocked(returned) }
        }
    }

    /** The caller holds saveFlow, including any shutdown settling time before this read. */
    private suspend fun reconcileLocked(pending: LibraryReturn): Boolean {
        return try {
            // Keep upload, lock and retry time out of the launch's minimum session duration.
            val endedAtMillis = pending.sessionEndedAtMillis ?: System.currentTimeMillis()
            foldcade.store.setPendingSaveReturn(pending.storeKey, pending.copy(sessionEndedAtMillis = endedAtMillis).encode())
            val saves = foldcade.playerSaves
            val eden = (pending.player as? EdenPlayer)?.let { player -> planEdenReturn(saves, pending, player) }
            if (eden?.backupOnly == true) {
                foldcade.store.setPendingSaveReturn(pending.storeKey, pending.copy(sessionEndedAtMillis = endedAtMillis, preserveAsBackup = true).encode())
                foldcade.saveIncidentCopies.preserveEdenReturn(pending, eden)
                foldcade.refreshSaveProtection(pending.player, flowHeld = true)
                foldcade.refreshSaveReceipt(pending.game, pending.player, emptyList())
                foldcade.store.setPendingSaveReturn(pending.storeKey, null)
                return true
            }
            val legacyBackup = foldcade.saveIncidentCopies.preserveUnsupportedRetroArchReturn(pending)
            // Read the card again, and do not set one up: the player has just been holding it.
            val spots = if (legacyBackup) emptyList() else eden?.spots ?: returnSpots(saves, pending)
            val reachable = spots.isNotEmpty() && pending.player.saveDeclarations(pending.game).all { declaration -> spots.any { it.slot == declaration.slot } }
            val probeTarget = pending.targetUri?.let { LaunchTarget.ContentUri(it) }
            val issue = if (reachable) null else probeTarget?.let { saves.unresolvedSpot(pending.player, pending.game, it) }
            withContext(Dispatchers.Main.immediate) { foldcade.shell.noteSaveReachable(pending.game, pending.player.id, reachable, issue) }
            val backupOnly = legacyBackup || pending.preserveAsBackup || foldcade.saveProtection.pendingBackup(pending.player.saveFolderId(), pending.game)
            // Judge and publish before reconcile can report Synced for the stale bound copy.
            // Optional probe failures still cannot block handing that copy back to the library.
            val key = pending.player.saveFolderId()
            var subject = incidentGame(pending.game, pending.player, pending.targetUri, foldcade.store.rommOrigin(), pending.baseline)
            var root = pending.baseline?.roots?.singleOrNull { it.bound }?.root
            var after: app.foldcade.language.ProbeSnapshot? = null
            var reinstalled = false
            fun hasOpenFolderIncident() = foldcade.saveProtection.incidents().any { it.playerKey == key && it.blocksLaunch && it.appliesTo(pending.game) &&
                it.kind in setOf(app.foldcade.language.IncidentKind.WrongFolder, app.foldcade.language.IncidentKind.FolderEmptied) }
            val openBeforeJudgement = hasOpenFolderIncident()
            try {
                val reading = foldcade.saveProbes.judgeReturn(pending.player, pending.game,
                    probeTarget, pending.baseline,
                    unprotected = backupOnly, startedByFoldcade = pending.startedByFoldcade,
                    sessionStartedAtMillis = pending.sessionStartedAtMillis, sessionEndedAtMillis = endedAtMillis, edenSpots = eden?.spots, spots = spots,
                    beforeJudgement = { snapshot ->
                        after = snapshot
                        root = snapshot.roots.singleOrNull { it.bound }?.root
                        reinstalled = root?.let { foldcade.saveProtection.reinstalled(key, it, snapshot.packageName, snapshot.installFirstMillis) } == true
                    })
                subject = incidentGame(pending.game, pending.player, pending.targetUri, foldcade.store.rommOrigin(), reading ?: pending.baseline)
                foldcade.associateSaveRepair(pending.player, subject)
                foldcade.publishSaveGate(pending.player, reading)
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { Unit }
            if (pending.player is RetroArchPlayer || pending.player is DolphinPlayer || pending.player.probesContentSaves) try {
                foldcade.saveProbes.captureReturn(pending.player, pending.game, probeTarget, pending.baseline, after, backupOnly)
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { return false }
            foldcade.saveProbes.stageDuckStationReturn(pending.player, pending.game, spots, pending.sessionStartedAtMillis, endedAtMillis,
                startedByFoldcade = pending.startedByFoldcade)
            val preserved = try {
                val providerPlayer = pending.player is Aps3ePlayer || pending.player is DolphinPlayer
                val lostProvider = providerPlayer &&
                    foldcade.saveProtection.incidents().any { it.playerKey == key && it.kind == IncidentKind.LostAccess && it.blocksLaunch }
                // A legacy Backup does not complete the fence for a compatible working slot.
                if (legacyBackup) true
                else if (providerPlayer && spots.isEmpty()) {
                    // Leave an unreadable launch-time spot in place and fence it before any later placement.
                    if (pending.spots.isNotEmpty()) foldcade.saveProtection.requireBackup(key, pending.game)
                    true
                }
                else foldcade.saveProbes.preserveArmsX2Return(pending, probeTarget, spots, after) ||
                    foldcade.saveIncidentCopies.preserveReturn(pending.game, pending.player, spots, pending.preserveAsBackup || lostProvider)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                // Keep both the return record and the durable per-game latch. A later launch must stage it before placement.
                return false
            }
            val regressionContext = returnRegressionContext(pending.baseline, after, pending.player is HostedSaveFolder,
                null, null, // Eden profile changes are handled by the frozen Backup-only return above.
                reinstalled, openBeforeJudgement || hasOpenFolderIncident())
            val (observed, returned) = when {
                preserved -> app.foldcade.api.plugin.ObservedSaves(emptyList()) to null
                subject.origin != null && spots.size == 1 &&
                    app.foldcade.plugins.romm.supportsRommRepair(foldcade.plugins.library(pending.libraryId)) ->
                    foldcade.saveRegression.capture(pending.libraryId, pending.game, pending.player, spots)
                else -> saves.changedSince(pending.libraryId, pending.game, pending.player, spots) to null
            }
            val quarantined = returned?.let {
                foldcade.saveRegression.quarantine(subject.copy(edenContext = eden?.context?.repairKey()), pending.player, it, root, regressionContext)
            } == true
            if (!preserved && !quarantined) {
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
            }
            // Optional evidence and live-copy capture cannot prevent the bound save reaching RomM.
            try {
                if (pending.player !is RetroArchPlayer && pending.player !is DolphinPlayer && !pending.player.probesContentSaves)
                    foldcade.saveProbes.captureReturn(pending.player, pending.game, probeTarget, pending.baseline, after)
                foldcade.saveIncidentCopies.keep(pending.game, pending.player)
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { Unit }
            foldcade.refreshSaveProtection(pending.player, flowHeld = true)
            foldcade.refreshSaveReceipt(pending.game, pending.player, spots)
            foldcade.store.setPendingSaveReturn(pending.storeKey, null)
            if (quarantined) foldcade.resumeSaveRepairs()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (fatal: VirtualMachineError) {
            throw fatal
        } catch (_: Exception) {
            // Keep the pending record so the next return or process retries it.
            false
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

/** How long after A or X a start still grows from the pressed tile. Later ones waited on something. */
private const val BLOOM_PRESS_MS = 1_500L

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
    val baseline: app.foldcade.language.ProbeSnapshot? = null,
    val unprotectedSession: Boolean = false,
    /** Decided after the successful start; later incident closure cannot authorize promotion. */
    val preserveAsBackup: Boolean = false,
    val startedByFoldcade: Boolean = true,
    val sessionStartedAtMillis: Long? = null,
    val sessionEndedAtMillis: Long? = null,
    val edenSession: EdenSaveSession? = null,
)

/**
 * The spots to read back. Eden is resolved against its frozen profile in [planEdenReturn].
 * A player whose spots at launch came from a search is looked up again.
 * The card is left as it is, and that look wins when it finds
 * any. The spots stored at launch remain when that look finds nothing, so a game
 * file that cannot be read just now does not drop the saves. Other players keep
 * launch-time spots, except for known unsupported RetroArch formats in old records.
 */
internal fun returnSpots(saves: PlayerSaves, pending: LibraryReturn): List<SaveSpot> {
    if (unsupportedRetroArchReturn(saves, pending)) return emptyList()
    // Slot A / VFS config can change during play. A cached launch spot still points to the old card.
    if (pending.player is DolphinPlayer || pending.player is Aps3ePlayer) {
        val uri = pending.targetUri ?: return emptyList()
        return saves.spots(pending.player, pending.game, LaunchTarget.ContentUri(uri), forLaunch = false)
    }
    val uri = pending.targetUri ?: return pending.spots
    if (!pending.unprotectedSession && pending.spots.none { it.search != null }) return pending.spots
    val fresh = try {
        saves.spots(pending.player, pending.game, LaunchTarget.ContentUri(uri), forLaunch = false)
    } catch (_: Exception) {
        return pending.spots
    }
    return fresh.ifEmpty { pending.spots }
}

internal fun unsupportedRetroArchReturn(saves: PlayerSaves, pending: LibraryReturn): Boolean {
    val player = pending.player as? RetroArchPlayer ?: return false
    return player.unsupportedSaveFormat(pending.targetUri?.let(saves::romDisplayName).orEmpty()) != null
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
    (player as? RetroArchPlayer)?.pinnedCore?.let { put("core", it) }
    targetUri?.let { put("target", it) }
    baseline?.let { put("baseline", it.toJson()) }
    put("unprotectedSession", unprotectedSession)
    put("preserveAsBackup", preserveAsBackup)
    put("startedByFoldcade", startedByFoldcade)
    put("sessionStartedAtMillis", sessionStartedAtMillis ?: JSONObject.NULL)
    put("sessionEndedAtMillis", sessionEndedAtMillis ?: JSONObject.NULL)
    edenSession?.let { put("edenSession", it.toJson()) }
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
        val pinned = if (resolved is RetroArchPlayer) json.optString("core").takeIf { it.isNotBlank() }?.let(resolved::withCore) ?: resolved else resolved
        decodeLibraryReturn(json, pinned)
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
        baseline = json.optJSONObject("baseline")?.let { runCatching { snapshotFromJson(it) }.getOrNull() },
        unprotectedSession = json.optBoolean("unprotectedSession"),
        // Old opt-outs conservatively preserve, since they did not persist the launch's incident decision.
        preserveAsBackup = json.optBoolean("preserveAsBackup", json.optBoolean("unprotectedSession")),
        startedByFoldcade = json.optBoolean("startedByFoldcade", false),
        sessionStartedAtMillis = json.nullableLong("sessionStartedAtMillis"),
        sessionEndedAtMillis = json.nullableLong("sessionEndedAtMillis"),
        edenSession = json.optJSONObject("edenSession")?.let(::edenSessionFromJson),
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

/** SharedFile.path launches the emulator; its uri remains the host's readable game handle. */
internal fun LaunchTarget.saveReadUri(): String? = when (this) {
    is LaunchTarget.ContentUri -> uri
    is LaunchTarget.SharedFile -> uri
    else -> null
}
