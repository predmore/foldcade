package app.foldcade

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameAchievements
import app.foldcade.artwork.ArtQuery
import app.foldcade.language.Meaning
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What a command center shows for the app beside it. Noted when Foldcade opens that app. */
data class RunningApp(
    val title: String,
    val packageName: String? = null,
    val mark: String? = null,
    val record: Game? = null,
    val query: ArtQuery? = null,
)

/** The command center's two buttons. Achievements is there only when the game has some. */
enum class CenterFocus {
    OpenApp,
    Achievements,
}

/**
 * The screen beside a single-screen app, [Surface.CommandCenter]. It rests dim.
 * A touch or a key wakes it to the screen's own brightness. After [IDLE_MS]
 * with nothing pressed it dims again, and a grid or list opened from it gives
 * way to the command center. An app opened there keeps the screen bright: its
 * window asks for no brightness of its own.
 *
 * A library game's achievements come from [load] when the game is noted, and
 * again on a wake once they are [ACHIEVEMENTS_STALE_MS] old.
 *
 * [busy] holds the timer while a dialog or menu is up.
 */
class CommandCenter(
    private val store: SessionStore,
    private val scope: CoroutineScope,
    private val load: suspend (Game) -> GameAchievements?,
    private val busy: () -> Boolean,
) {
    /** A wake belongs to the app beside it. A new app there starts dim again. */
    private data class Wake(val panel: Panel, val beside: ExternalApp?)

    private class Loaded(val achievements: GameAchievements?, val atMillis: Long)

    private var wake by mutableStateOf<Wake?>(null)
    private val running = mutableStateMapOf<String, RunningApp>()
    private val loaded = mutableStateMapOf<String, Loaded>()
    private val loading = mutableSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private val rest = Runnable { settle() }

    /** The focused button. Keys move it; touch presses either. */
    var focus by mutableStateOf(CenterFocus.OpenApp)
        private set

    /** The panel showing the achievement list in place of the buttons. */
    var listOn by mutableStateOf<Panel?>(null)
        private set

    /** The list row the keys are on. */
    var listIndex by mutableStateOf(0)
        private set

    /** Remembers what [id] is, for the command center beside it. Apps no screen holds are forgotten. */
    fun note(id: String, app: RunningApp?) {
        val session = store.session
        val held = setOfNotNull(session.topApp?.id, session.bottomApp?.id, id)
        running.keys.retainAll(held)
        loaded.keys.retainAll(held)
        if (app != null) running[id] = app else running.remove(id)
        loaded.remove(id)
        focus = CenterFocus.OpenApp
        listOn = null
        refresh(id)
    }

    /** What the app beside [panel] is. Null for one Foldcade did not open. */
    fun beside(panel: Panel): RunningApp? = besideId(panel)?.let(running::get)

    /** The achievements of the game beside [panel]. Null while loading, and for a game with none. */
    fun achievements(panel: Panel): GameAchievements? =
        besideId(panel)?.let(loaded::get)?.achievements?.takeIf { it.achievements.isNotEmpty() }

    /** True while [panel]'s command center rests at its dim level. */
    fun dimmed(panel: Panel): Boolean = commandCenterDimmed(store.session, panel, wake?.takeIf { it.matches() }?.panel)

    /**
     * A touch or a key on [panel]. Only a command center, or the grid opened
     * from one, wakes and restarts the timer.
     */
    fun touch(panel: Panel) {
        val session = store.session
        if (session.surfaceOn(panel) != Surface.CommandCenter && session.browsing != panel) return
        val waking = wake?.takeIf { it.matches() }?.panel != panel
        wake = Wake(panel, session.externalOn(panel.flip()))
        handler.removeCallbacks(rest)
        handler.postDelayed(rest, IDLE_MS)
        if (waking) besideId(panel)?.let(::refresh)
    }

    /** The command center's button: the grid on [panel], awake. */
    fun browse(panel: Panel) {
        listOn = null
        store.update { it.browse(panel) }
        touch(panel)
    }

    /** The other button: the list of the game's achievements on [panel]. */
    fun openList(panel: Panel) {
        if (achievements(panel) == null) return
        focus = CenterFocus.Achievements
        listIndex = 0
        listOn = panel
        touch(panel)
    }

    fun closeList() {
        listOn = null
    }

    /** Back on a grid opened from the command center. */
    fun leaveGrid(panel: Panel) {
        if (store.session.browsing != panel) return
        store.update { it.stopBrowsing() }
        wake = null
        handler.removeCallbacks(rest)
    }

    /**
     * One key on [panel]. Keys on the command center wake it and never reach the
     * grid behind it: a direction moves between its buttons or down the list, A
     * presses, and B closes the list. On a grid opened from it every key keeps
     * the screen awake, and B where the grid has nothing left to close
     * ([gridAtRest]) goes back. True when the key was handled here.
     */
    fun onKey(panel: Panel, meaning: Meaning, gridAtRest: () -> Boolean): Boolean {
        val session = store.session
        if (session.surfaceOn(panel) == Surface.CommandCenter) {
            touch(panel)
            if (listOn == panel) {
                val last = (achievements(panel)?.achievements?.lastIndex ?: 0).coerceAtLeast(0)
                when (meaning) {
                    Meaning.MoveUp -> listIndex = (listIndex - 1).coerceAtLeast(0)
                    Meaning.MoveDown -> listIndex = (listIndex + 1).coerceAtMost(last)
                    Meaning.Back, Meaning.Activate -> closeList()
                    else -> Unit
                }
                return true
            }
            val two = achievements(panel) != null
            when (meaning) {
                Meaning.MoveLeft, Meaning.MoveUp -> focus = CenterFocus.OpenApp
                Meaning.MoveRight, Meaning.MoveDown -> if (two) focus = CenterFocus.Achievements
                Meaning.Activate -> if (focus == CenterFocus.Achievements && two) openList(panel) else browse(panel)
                else -> Unit
            }
            return true
        }
        if (session.browsing != panel) return false
        touch(panel)
        if (meaning == Meaning.Back && gridAtRest()) {
            leaveGrid(panel)
            return true
        }
        return false
    }

    private fun besideId(panel: Panel): String? = store.session.externalOn(panel.flip())?.id

    /** Loads [id]'s achievements when it is a library game and they are missing or stale. */
    private fun refresh(id: String) {
        val record = running[id]?.record ?: return
        val held = loaded[id]
        val now = System.currentTimeMillis()
        if (held != null && now - held.atMillis < ACHIEVEMENTS_STALE_MS) return
        if (!loading.add(id)) return
        scope.launch {
            val found = try {
                load(record)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Offline, signed out, or no achievements service: the command center just has none.
                null
            }
            handler.post {
                loading.remove(id)
                if (running[id]?.record == record) loaded[id] = Loaded(found ?: held?.achievements, now)
            }
        }
    }

    private fun settle() {
        val current = wake ?: return
        if (current.matches() && busy()) {
            handler.postDelayed(rest, IDLE_MS)
            return
        }
        wake = null
        listOn = null
        focus = CenterFocus.OpenApp
        store.update { it.stopBrowsing() }
    }

    private fun Wake.matches(): Boolean = store.session.externalOn(panel.flip()) == beside

    companion object {
        const val IDLE_MS = 15_000L

        /** A woken command center asks again once its achievements are this old. */
        const val ACHIEVEMENTS_STALE_MS = 5L * 60L * 1000L

        /**
         * The dim level, as a window brightness. On the Thor a window's level moves
         * only its own panel's backlight. A screen the user keeps darker than this
         * stays at its own level.
         */
        const val DIM_BRIGHTNESS = 0.02f
    }
}

/** Dim while [panel] shows its command center and the user has not woken it. */
fun commandCenterDimmed(session: Session, panel: Panel, awake: Panel?): Boolean =
    session.surfaceOn(panel) == Surface.CommandCenter && awake != panel
