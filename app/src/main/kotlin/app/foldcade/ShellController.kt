package app.foldcade

import app.foldcade.api.plugin.RommCredentials
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.host.PluginHost
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.ConnectField
import app.foldcade.language.Chrome
import app.foldcade.language.Copy
import app.foldcade.language.DialogKind
import app.foldcade.language.DialogState
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
    private val cue: (themeIndex: Int, slot: String) -> Unit = { _, _ -> },
    private val lastPlayedMillis: (String) -> Long? = { null },
) {
    var model by mutableStateOf(initial())
        private set

    var connectToken by mutableStateOf("")
        private set

    fun onMeaning(meaning: Meaning, screen: HostScreen): Effect? {
        val before = model
        val (next, effect) = reduce(prepared(), meaning, screen)
        if (!next.connectOpen) connectToken = ""
        publish(next)
        if (next.panel?.level == PanelLevel.Library) {
            refreshLibraries()
        }
        cueMeaning(meaning, before, model)
        return effect
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

    fun focusedGame(): ShelfGame? {
        val index = model.focus.cellIndex
        val source = displayOrder(model).getOrElse(index) { index }
        return Shelf.games.getOrNull(source)
    }

    fun refreshPlayerSaves() {
        model = model.copy(playerSaves = playerSaveSettings())
    }

    private fun prepared(): PickerModel {
        val game = focusedGame()
        val visible = store.session.launchTargetControlVisible(game?.occupiesBothDisplays == true)
        return model.copy(
            count = Shelf.games.size,
            showLaunchTarget = visible,
            playerSaves = playerSaveSettings(),
        )
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
        model = next.copy(count = Shelf.games.size, recentFirst = recentOrder())
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

    private fun cueMeaning(meaning: Meaning, before: PickerModel, after: PickerModel) {
        when (meaning) {
            Meaning.Activate -> cue(after.themeIndex, "activate")
            Meaning.Back -> cue(after.themeIndex, "back")
            Meaning.MoveUp, Meaning.MoveDown, Meaning.MoveLeft, Meaning.MoveRight -> {
                val moved = before.focus != after.focus || before.panel?.index != after.panel?.index
                if (moved) cue(after.themeIndex, "move")
            }
            else -> Unit
        }
        if (before.dialog?.kind != DialogKind.Ok && after.dialog?.kind == DialogKind.Ok) {
            cue(after.themeIndex, "notify")
        }
        if (after.notices.size > before.notices.size) cue(after.themeIndex, "notify")
    }
}
