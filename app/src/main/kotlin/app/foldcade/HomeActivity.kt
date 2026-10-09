package app.foldcade

import android.app.Activity
import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import app.foldcade.api.ExternalApp
import app.foldcade.api.Panel
import app.foldcade.api.isAndroidHomeRecall
import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.RommCredentials
import app.foldcade.language.DialogButton
import app.foldcade.language.DialogKind
import app.foldcade.language.Effect
import app.foldcade.language.HostScreen
import app.foldcade.language.PanelKeyActivity
import app.foldcade.language.SignedInBackend
import app.foldcade.romm.RommClient
import app.foldcade.romm.normalizeSetupOrigin
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import app.foldcade.ui.PanelHost

abstract class FoldcadeHomeActivity : PanelKeyActivity() {
    protected abstract val launchesCompanion: Boolean

    private val displays by lazy { Displays(this) }
    private val foldcade by lazy { application as FoldcadeApp }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        foldcade.store.setFolderTree(uri.toString())
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
        val panel = displays.panelFor(this, foldcade.store.session.defaultDisplayIsTop) ?: return
        val screen = if (panel == Panel.Top) HostScreen.Top else HostScreen.Bottom
        dispatch(foldcade.shell.onMeaning(meaning, screen))
    }

    fun dispatch(effect: Effect?) {
        when (effect) {
            is Effect.Launch -> launchGame(effect.index)
            Effect.CycleLaunchTarget -> cycleLaunchTarget()
            Effect.AddFolder -> folderPicker.launch(null)
            Effect.OpenConnect -> foldcade.shell.openConnect(foldcade.store.rommOrigin().orEmpty())
            Effect.SaveRommToken -> saveRommToken()
            is Effect.ForgetCredentials -> forget(effect.pluginId)
            is Effect.DialogChoice -> onDialog(effect)
            is Effect.ActivateBackend -> Unit
            Effect.RequestHome -> requestHome()
            null -> Unit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptHome(intent)
        val roleManager = getSystemService(RoleManager::class.java)
        val held = roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        foldcade.shell.setHomeRoleHeld(held)
        foldcade.shell.maybeAskHome(held)
        if (launchesCompanion) {
            val manager = getSystemService(DisplayManager::class.java)
            manager.registerDisplayListener(displayListener, null)
            launchCompanionIfNeeded()
        }
        setContent { PanelHost(activity = this, displays = displays) }
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptHome(intent)
    }

    override fun onDestroy() {
        if (launchesCompanion) {
            getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        }
        super.onDestroy()
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
        val session = foldcade.store.session
        val external = ExternalApp(game.id, game.occupiesBothDisplays)
        if (game.occupiesBothDisplays) {
            foldcade.store.update { it.launch(external, game.platformId) }
            startOnDisplay(StandInActivity::class.java, displays.assignment(session.defaultDisplayIsTop).topDisplayId) {
                putExtra(StandInActivity.EXTRA_ID, game.id)
                putExtra(StandInActivity.EXTRA_TITLE, game.title)
            }
            return
        }
        val panel = session.singleScreenTarget(game.id, game.platformId) ?: return
        val assignment = displays.assignment(session.defaultDisplayIsTop)
        val displayId = when (panel) {
            Panel.Top -> assignment.topDisplayId
            Panel.Bottom -> assignment.bottomDisplayId ?: return
        }
        foldcade.store.place(panel, external)
        startOnDisplay(StandInActivity::class.java, displayId) {
            putExtra(StandInActivity.EXTRA_ID, game.id)
            putExtra(StandInActivity.EXTRA_TITLE, game.title)
        }
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
                folderPicker.launch(null)
            }
            DialogKind.Ok -> Unit
            DialogKind.ReLogin -> foldcade.shell.openConnect(foldcade.store.rommOrigin().orEmpty())
        }
        foldcade.shell.showQueuedPrompt()
    }

    private fun saveRommToken() {
        val origin = normalizeSetupOrigin(foldcade.shell.model.connectOrigin)
        if (origin == null || foldcade.shell.connectToken.isBlank()) return
        val token = foldcade.shell.consumeConnectToken().trim()
        if (token.isEmpty()) return
        foldcade.scope.launch {
            val hint = try {
                RommClient(
                    origin = origin,
                    accessToken = { null },
                    connectTimeout = Duration.ofSeconds(4),
                    readTimeout = Duration.ofSeconds(4),
                ).use { it.redirectHint() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            foldcade.shell.showSetupHint(hint)
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
                    foldcade.shell.askToSignInAgain()
                    false
                }
                if (saved) {
                    foldcade.store.setRommOrigin(origin)
                    foldcade.shell.setSignedIn(listOf(SignedInBackend(RommCredentials.PLUGIN_ID, "RomM")))
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
                    foldcade.shell.askToSignInAgain()
                    false
                }
                if (forgotten) {
                    if (pluginId == RommCredentials.PLUGIN_ID) foldcade.store.clearRommOrigin()
                    foldcade.shell.setSignedIn(foldcade.shell.model.signedIn.filter { it.pluginId != pluginId })
                }
            }
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
