package app.foldcade

import android.app.Activity
import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.foldcade.api.ExternalApp
import app.foldcade.api.Panel
import app.foldcade.host.play.PlaySignal
import app.foldcade.api.isAndroidHomeRecall
import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.RommCredentials
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.StartDisplay
import app.foldcade.language.DialogButton
import app.foldcade.language.DialogKind
import app.foldcade.language.Effect
import app.foldcade.language.HomeGrid
import app.foldcade.language.HostScreen
import app.foldcade.language.closeBothPanelDialog
import app.foldcade.language.missingPlayerDialog
import app.foldcade.language.noFileDialog
import app.foldcade.language.saveFolderDialog
import app.foldcade.language.PanelKeyActivity
import app.foldcade.language.SignedInBackend
import app.foldcade.romm.RommClient
import app.foldcade.romm.RommSignInResult
import app.foldcade.romm.normalizeSetupOrigin
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.foldcade.ui.PanelHost

abstract class FoldcadeHomeActivity : PanelKeyActivity() {
    protected abstract val launchesCompanion: Boolean

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
    private var settingsPlayerId: String? = null

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
                foldcade.store.setFolderTree(uri.toString())
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

    override fun pickerIsFocused(): Boolean {
        val panel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop) ?: return false
        return foldcade.store.session.pickerHandlesKeys(panel)
    }

    override fun foldcadeSurfaceFocused(): Boolean {
        val panel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop) ?: return false
        return foldcade.store.session.surfaceOn(panel) != null
    }

    override fun onMeaning(meaning: app.foldcade.language.Meaning) {
        foldcade.music.duckForThemeSound(meaning)
        val panel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop) ?: return
        val screen = if (panel == Panel.Top) HostScreen.Top else HostScreen.Bottom
        dispatch(foldcade.shell.onMeaning(meaning, screen))
    }

    fun dispatch(effect: Effect?) {
        when (effect) {
            is Effect.Launch -> launchFocused()
            Effect.CycleLaunchTarget -> cycleLaunchTarget()
            Effect.AddFolder -> {
                folderPurpose = FolderPurpose.Library
                folderPicker.launch(null)
            }
            is Effect.ChoosePlayerSave -> choosePlayerSave(effect.playerId)
            Effect.PinApp, Effect.MoveApp, Effect.HideApp, Effect.ShowApp -> Unit
            Effect.OpenConnect -> foldcade.shell.openConnect(foldcade.store.rommOrigin().orEmpty())
            Effect.SaveRommToken -> saveRommToken()
            is Effect.ForgetCredentials -> forget(effect.pluginId)
            is Effect.DialogChoice -> onDialog(effect)
            is Effect.ActivateBackend -> Unit
            Effect.RequestHome -> requestHome()
            is Effect.OpenAndroidSetting -> openAndroidSetting(effect.setting)
            null -> Unit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptHome(intent)
        acceptShelf(intent)
        val roleManager = getSystemService(RoleManager::class.java)
        val held = roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        foldcade.shell.setHomeRoleHeld(held)
        foldcade.shell.maybeAskHome(held)
        if (launchesCompanion) {
            val manager = getSystemService(DisplayManager::class.java)
            manager.registerDisplayListener(displayListener, null)
            launchCompanionIfNeeded()
        }
        getSystemService(DisplayManager::class.java).registerDisplayListener(backdropDisplayListener, null)
        setContent { PanelHost(activity = this, displays = displays) }
        hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        display?.displayId?.let { foldcade.externalPlay.endIfDisplayHome(foldcade.plays, it) }
        resumed = true
        refreshShellVisible()
        foldcade.reloadInstalledApps()
        foldcade.music.onHomeResume()
    }

    override fun onPause() {
        resumed = false
        refreshShellVisible()
        foldcade.music.onHomePause()
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
    }

    override fun onDestroy() {
        val manager = getSystemService(DisplayManager::class.java)
        manager.unregisterDisplayListener(backdropDisplayListener)
        if (launchesCompanion) manager.unregisterDisplayListener(displayListener)
        super.onDestroy()
    }

    private fun refreshShellVisible() {
        val state = display?.state ?: Display.STATE_ON
        shellVisible = resumed && state == Display.STATE_ON
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
        val player = game.platformId?.let { foldcade.plugins.playersFor(it).firstOrNull() }
        if (player == null) {
            if (game.platformId != null) {
                pendingLaunch = index
                foldcade.shell.present(missingPlayerDialog("Player", hostScreen()))
                return
            }
            launchStandIn(game)
            return
        }
        val continuing = pendingLaunch == index && closeConfirmed
        val decision = planPlayerLaunch(
            player = player,
            game = shelfGame(game),
            target = game.contentUri?.let { LaunchTarget.ContentUri(it) },
            installedPackages = installedPackages(player.packageNames),
            anotherBothPanelRunning = anotherBothPanelRunning(foldcade.store.session, game.id),
            closeConfirmed = continuing,
            saveFolderSettled = foldcade.store.saveFolderPromptSkipped(player.id),
            componentResolves = { intent ->
                packageManager.resolveActivity(intent.toAndroidIntent(), 0) != null
            },
        )
        pendingLaunch = index
        when (decision) {
            is PlayerLaunch.Blocked -> when (decision.block) {
                LaunchBlock.CloseFirst -> foldcade.shell.present(closeBothPanelDialog(hostScreen()))
                LaunchBlock.MissingPlayer ->
                    foldcade.shell.present(missingPlayerDialog(decision.playerName, hostScreen()))
                LaunchBlock.NoLocalFile -> foldcade.shell.present(noFileDialog(hostScreen()))
                LaunchBlock.SaveFolder -> foldcade.shell.present(saveFolderDialog(hostScreen()))
            }
            is PlayerLaunch.Ready -> {
                clearPendingLaunch()
                startPlayer(game, decision)
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
        if (game.androidPackage != null) {
            launchInstalled(game)
            return
        }
        val index = Shelf.games.indexOfFirst { it.id == game.id }
        if (index >= 0) launchGame(index) else launchStandIn(game)
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

    private fun startPlayer(game: ShelfGame, ready: PlayerLaunch.Ready) {
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ActivityNotFoundException) {
            foldcade.store.update { it.home(Panel.Top).home(Panel.Bottom) }
            val name = game.platformId
                ?.let { foldcade.plugins.playersFor(it).firstOrNull()?.displayName }
                ?: "Player"
            foldcade.shell.present(missingPlayerDialog(name, hostScreen()))
        } catch (_: Exception) {
            foldcade.store.update { it.home(Panel.Top).home(Panel.Bottom) }
        }
    }

    private fun installedPackages(packageNames: List<String>): Set<String> =
        packageNames.filter { name ->
            try {
                packageManager.getPackageInfo(name, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }.toSet()

    private fun hostScreen(): HostScreen {
        val panel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop)
        return if (panel == Panel.Top) HostScreen.Top else HostScreen.Bottom
    }

    private fun clearPendingLaunch() {
        pendingLaunch = null
        closeConfirmed = false
    }

    private fun cycleLaunchTarget() {
        val game = foldcade.shell.focusedGame() ?: return
        foldcade.store.update { it.cycleStoredScreen(game.id, game.platformId, onPlatform = false) }
    }

    private fun onDialog(effect: Effect.DialogChoice) {
        when (effect.kind) {
            DialogKind.Home -> {
                foldcade.store.setHomePromptSettled()
                if (effect.button == DialogButton.UseAsHome) requestHome()
            }
            DialogKind.Folder -> if (effect.button == DialogButton.ContinueGrant) {
                foldcade.shell.noteFolderExplained()
                folderPurpose = FolderPurpose.Library
                folderPicker.launch(null)
            }
            DialogKind.Ok -> clearPendingLaunch()
            DialogKind.ReLogin -> foldcade.shell.openConnect(foldcade.store.rommOrigin().orEmpty())
            DialogKind.MissingPlayer -> clearPendingLaunch()
            DialogKind.ClosePlayer -> if (effect.button == DialogButton.CloseIt) {
                closeConfirmed = true
                pendingLaunch?.let { launchGame(it) }
            } else {
                clearPendingLaunch()
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
        return game.platformId?.let { foldcade.plugins.playersFor(it).firstOrNull() }
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
}

private enum class FolderPurpose {
    Library,
    LaunchOffer,
    Settings,
}

private fun shelfGame(game: ShelfGame): Game = Game(
    backendId = "shelf",
    remoteKey = game.contentUri ?: game.id,
    platformId = game.platformId.orEmpty(),
    availability = if (game.contentUri == null) Availability.RemoteOnly else Availability.LocalOnly,
    label = game.title,
)

internal const val EXTRA_ANDROID_SHELF = "app.foldcade.extra.SHELF"

class PrimaryHomeActivity : FoldcadeHomeActivity() {
    override val launchesCompanion: Boolean = true
}

class CompanionHomeActivity : FoldcadeHomeActivity() {
    override val launchesCompanion: Boolean = false
}

internal fun Activity.hideSystemBars() {
    window.insetsController?.apply {
        hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
