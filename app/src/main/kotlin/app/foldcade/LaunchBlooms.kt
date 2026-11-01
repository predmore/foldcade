package app.foldcade

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import app.foldcade.language.LaunchBloom

/** What a screen showed of a launching game, so its art grows from there. */
internal enum class BloomFrom {
    /** The game's tile on the grid, or in Find. */
    Tile,

    /** The hero's box art on the top screen. */
    Cover,

    /** The hero's wide art, already filling the screen behind the game's logo. */
    Scene,
}

/** Where a screen's art sat when the game started, in that panel's root pixels. */
internal data class BloomAnchor(val from: BloomFrom, val rect: Rect, val cornerPx: Float)

/**
 * A place that draws a game's art, registered while it is on screen. [find] marks Find's
 * tiles, which are under the cursor only while Find is open. [key] is the hero subject's key.
 */
internal class BloomSource(var from: BloomFrom, var cornerPx: Float, var key: String?, val find: Boolean = false) {
    var coordinates: LayoutCoordinates? = null

    fun anchor(): BloomAnchor? {
        val placed = coordinates?.takeIf { it.isAttached } ?: return null
        val rect = placed.boundsInRoot()
        if (rect.width <= 0f || rect.height <= 0f) return null
        return BloomAnchor(from, rect, cornerPx)
    }
}

/**
 * One launch's bloom. [gamePanels] are the screens the game takes; the others give way
 * once the art has grown and held. Started at [startedAt], uptime millis.
 */
internal class ActiveBloom(
    val serial: Int,
    val art: RunningApp,
    val gamePanels: Set<Panel>,
    val anchors: Map<Panel, BloomAnchor>,
    val timing: LaunchBloom,
    val startedAt: Long,
) {
    /** Screens the game, or another app, now covers. Their art is gone at once. */
    val covered = mutableStateSetOf<Panel>()

    /** Screens whose art has given way. */
    val done = mutableStateSetOf<Panel>()

    /** Screens the grown art covers edge to edge, so their backdrop can rest. */
    val filled = mutableStateSetOf<Panel>()

    /** Screens whose art has finished growing, at least once. */
    val grown = mutableStateSetOf<Panel>()

    /** When the start failed, in millis from [startedAt]. Every screen gives way from there. */
    var cancelledAt by mutableLongStateOf(Long.MAX_VALUE)

    fun elapsed(now: Long = SystemClock.uptimeMillis()): Long = now - startedAt

    /** When [panel]'s art starts to give way, in millis from [startedAt]. */
    fun leavesAt(panel: Panel): Long = minOf(timing.leavesAt(panel in gamePanels), cancelledAt)
}

/**
 * A launching game's art growing to fill both screens, see [LaunchBloom]. The shell starts
 * the game in the same moment, so nothing here holds a launch back.
 */
internal class LaunchBlooms {
    var current by mutableStateOf<ActiveBloom?>(null)
        private set

    private var serial = 0
    private val sources = HashMap<Panel, MutableList<BloomSource>>()

    fun register(panel: Panel, source: BloomSource) {
        sources.getOrPut(panel) { mutableListOf() }.add(source)
    }

    fun unregister(panel: Panel, source: BloomSource) {
        sources[panel]?.remove(source)
    }

    /**
     * Starts [art]'s bloom, from where each screen showed it when [pressed]: the tile
     * pressed is still the focused one. Otherwise it grows from the middle.
     */
    fun start(
        art: RunningApp,
        gamePanels: Set<Panel>,
        timing: LaunchBloom,
        pressed: Boolean,
        finding: Boolean,
        heroKey: String?,
    ) {
        val anchors = if (!pressed) emptyMap() else Panel.entries.mapNotNull { panel ->
            val shown = sources[panel].orEmpty().lastOrNull { source ->
                when (source.from) {
                    BloomFrom.Tile -> source.find == finding
                    BloomFrom.Cover, BloomFrom.Scene -> heroKey != null && source.key == heroKey
                } && source.anchor() != null
            }
            shown?.anchor()?.let { panel to it }
        }.toMap()
        current = ActiveBloom(++serial, art, gamePanels, anchors, timing, SystemClock.uptimeMillis())
    }

    /** The start did not go in. The art gives way from where it is. */
    fun cancel() {
        val bloom = current ?: return
        bloom.cancelledAt = minOf(bloom.cancelledAt, bloom.elapsed())
    }

    /** Another window now covers [panel]. Coming back finds no art over Home. */
    fun covered(panel: Panel) {
        val bloom = current ?: return
        bloom.covered.add(panel)
        bloom.filled.remove(panel)
        settle(bloom)
    }

    /** Home is back on a screen the game took: the game has left, so no screen keeps its art. */
    fun retire() {
        current = null
    }

    fun finished(bloom: ActiveBloom, panel: Panel) {
        bloom.done.add(panel)
        bloom.filled.remove(panel)
        settle(bloom)
    }

    /**
     * True while [panel]'s art is still growing over it. The screen keeps drawing what it
     * showed until then, so the frames the art grows in are not also spent building the
     * screen's next page; that page changes in, unseen, under the grown art.
     */
    fun holdsView(panel: Panel): Boolean {
        val bloom = current ?: return false
        return panel !in bloom.grown && panel !in bloom.covered && panel !in bloom.done
    }

    /** True while [panel]'s art covers it edge to edge. */
    fun fills(panel: Panel): Boolean = current?.filled?.contains(panel) == true

    private fun settle(bloom: ActiveBloom) {
        if (current !== bloom) return
        if (Panel.entries.all { it in bloom.covered || it in bloom.done }) current = null
    }
}
