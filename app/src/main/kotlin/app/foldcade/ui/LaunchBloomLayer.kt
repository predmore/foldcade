package app.foldcade.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onPlaced
import app.foldcade.ActiveBloom
import app.foldcade.BloomFrom
import app.foldcade.BloomSource
import app.foldcade.FoldcadeApp
import app.foldcade.Panel
import app.foldcade.language.HostScreen
import kotlin.math.max
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Marks this layout as where [screen] shows a game's art, so a launch grows the art from
 * here. Only one place per screen is under the cursor at a time; [BloomSource.find] and
 * [BloomSource.key] tell the shell which.
 */
@Composable
internal fun Modifier.bloomSource(
    app: FoldcadeApp,
    screen: HostScreen,
    from: BloomFrom,
    cornerPx: Float,
    key: String? = null,
    find: Boolean = false,
): Modifier {
    val panel = if (screen == HostScreen.Top) Panel.Top else Panel.Bottom
    val source = remember(find) { BloomSource(from, cornerPx, key, find) }
    source.from = from
    source.cornerPx = cornerPx
    source.key = key
    DisposableEffect(panel, source) {
        app.launchBlooms.register(panel, source)
        onDispose {
            app.launchBlooms.unregister(panel, source)
            source.coordinates = null
        }
    }
    return this.onPlaced { source.coordinates = it }
}

/**
 * The launching game's art on [panel], growing from where this screen showed it to the
 * whole screen, then waiting there for the game. It draws over everything and takes no
 * touches. Nothing is in the tree once it has given way.
 */
@Composable
internal fun LaunchBloomLayer(app: FoldcadeApp, panel: Panel) {
    val bloom = app.launchBlooms.current ?: return
    if (panel in bloom.covered || panel in bloom.done) return
    key(bloom.serial) { BloomArt(app, bloom, panel) }
}

@Composable
private fun BloomArt(app: FoldcadeApp, bloom: ActiveBloom, panel: Panel) {
    val motion = LocalFoldTheme.current.theme.motion
    val anchor = bloom.anchors[panel]
    val record = bloom.art.record
    val query = bloom.art.query
    val art = rememberArt(app, record, query)
    val scene = rememberScene(app, record, query)
    // What grows is what this screen already showed, so its first frame is the same picture.
    val (uri, px) = when (anchor?.from) {
        BloomFrom.Tile -> (art?.square ?: art?.cover) to BLOOM_COVER_PX
        BloomFrom.Cover -> art?.cover to BLOOM_COVER_PX
        BloomFrom.Scene, null -> (scene?.background ?: art?.cover) to (if (scene?.background != null) BLOOM_WIDE_PX else BLOOM_COVER_PX)
    }
    // The tile's own size is in memory already. The sharper one replaces it once decoded.
    val small = if (anchor?.from == BloomFrom.Tile) uri?.let { rememberCoverPainter(app, it, TILE_PX) } else null
    val sharp = uri?.let { rememberCoverPainter(app, it, px) }
    val painter = sharp?.painter ?: small?.painter
    var elapsed by remember { mutableLongStateOf(bloom.elapsed()) }
    val missing = painter == null
    LaunchedEffect(bloom, panel, missing) {
        val timing = bloom.timing
        // Past the push the art holds still, so frames stop until it is time to give way.
        val moving = maxOf(timing.growMillis, timing.pushMillis).toLong()
        while (true) {
            withFrameMillis { }
            elapsed = bloom.elapsed(SystemClock.uptimeMillis())
            val leaveFrom = bloom.leavesAt(panel)
            // Art that never arrived while it would have grown, or art that has given way: this screen is done.
            if ((missing && elapsed >= timing.growMillis) || timing.done(elapsed, leaveFrom)) {
                bloom.filled.remove(panel)
                app.launchBlooms.finished(bloom, panel)
                return@LaunchedEffect
            }
            if (missing) continue
            val whole = elapsed >= timing.growMillis && elapsed < leaveFrom
            if (whole) bloom.filled.add(panel) else bloom.filled.remove(panel)
            if (elapsed in moving until leaveFrom) {
                // A failed start brings the leave forward, so this waits on that too.
                withTimeoutOrNull(leaveFrom - elapsed) {
                    snapshotFlow { bloom.leavesAt(panel) }.first { it < leaveFrom }
                }
            }
        }
    }
    if (painter == null) return
    val path = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        val timing = bloom.timing
        val presence = timing.presence(elapsed, bloom.leavesAt(panel))
        if (presence <= 0f) return@Canvas
        val full = Rect(Offset.Zero, size)
        val grow = timing.grow(elapsed)
        val (rect, corner, alpha, scale) = when (anchor?.from) {
            BloomFrom.Tile, BloomFrom.Cover -> Frame(lerp(anchor.rect, full, grow), anchor.cornerPx * (1f - grow), 1f, 1f)
            // Wide art already fills the screen behind the logo. It comes forward over the logo and the name.
            BloomFrom.Scene -> Frame(full, 0f, timing.arrive(elapsed), motion.sceneOverscan)
            // Art this screen did not show grows out of its middle as it fades in.
            null -> Frame(lerp(middle(full, painter.intrinsicSize), full, grow), 0f, timing.arrive(elapsed), 1f)
        }
        val push = scale * timing.pushScale(elapsed)
        withTransform({ scale(push, push, full.center) }) {
            drawCropped(painter, rect, corner, alpha * presence, path)
        }
    }
}

private data class Frame(val rect: Rect, val corner: Float, val alpha: Float, val scale: Float)

private fun lerp(from: Rect, to: Rect, t: Float): Rect = Rect(
    from.left + (to.left - from.left) * t,
    from.top + (to.top - from.top) * t,
    from.right + (to.right - from.right) * t,
    from.bottom + (to.bottom - from.bottom) * t,
)

/** The art at its own shape, centred, [MIDDLE_SHARE] of the screen's shorter side. */
private fun middle(full: Rect, intrinsic: Size): Rect {
    val ratio = if (intrinsic.isSpecified && intrinsic.height > 0f) intrinsic.width / intrinsic.height else 1f
    val side = full.minDimension * MIDDLE_SHARE
    val width = if (ratio >= 1f) side * ratio else side
    val height = if (ratio >= 1f) side else side / ratio
    return Rect(full.center.x - width / 2f, full.center.y - height / 2f, full.center.x + width / 2f, full.center.y + height / 2f)
}

/** [painter] filling [rect] as ContentScale.Crop would, inside corners of [corner]. */
private fun DrawScope.drawCropped(painter: Painter, rect: Rect, corner: Float, alpha: Float, path: Path) {
    val intrinsic = painter.intrinsicSize
    val known = intrinsic.isSpecified && intrinsic.width > 0f && intrinsic.height > 0f
    val width = if (known) intrinsic.width else rect.width
    val height = if (known) intrinsic.height else rect.height
    val fit = max(rect.width / width, rect.height / height)
    val drawn = Size(width * fit, height * fit)
    val left = rect.left + (rect.width - drawn.width) / 2f
    val top = rect.top + (rect.height - drawn.height) / 2f
    val paint: DrawScope.() -> Unit = { translate(left, top) { with(painter) { draw(drawn, alpha) } } }
    if (corner > 0.5f) {
        path.reset()
        path.addRoundRect(RoundRect(rect, CornerRadius(corner, corner)))
        clipPath(path, block = paint)
    } else {
        clipRect(rect.left, rect.top, rect.right, rect.bottom, block = paint)
    }
}

private const val MIDDLE_SHARE = 0.6f

/** The sizes the hero decodes its box and wide art at, so the bloom finds them already in memory. */
private const val BLOOM_COVER_PX = 1024
private const val BLOOM_WIDE_PX = 1920
