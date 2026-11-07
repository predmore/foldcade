package app.foldcade.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import app.foldcade.language.Theme

/** The existing rounded-corner clearance shared by checks, hidden marks and status chips. */
internal const val CORNER_SAFE = 0.3f

internal class StatusChipSource(val alpha: () -> Float) {
    var size by mutableStateOf(IntSize.Zero)
}

/** Only the current tile's chip cuts the cursor. An old tile disposing cannot clear its successor. */
internal class StatusChipCutout {
    var source by mutableStateOf<StatusChipSource?>(null)
        private set

    fun show(next: StatusChipSource) { source = next }
    fun hide(old: StatusChipSource) { if (source === old) source = null }
}

internal val LocalStatusChipCutout = staticCompositionLocalOf<StatusChipCutout?> { null }

@Composable
internal fun StatusChipLayer(content: @Composable () -> Unit) {
    val cutout = remember { StatusChipCutout() }
    CompositionLocalProvider(LocalStatusChipCutout provides cutout, content = content)
}

/** Size changes only on layout; animation values are observed solely by the cursor's draw block. */
@Composable
internal fun Modifier.statusChipCutout(active: Boolean, alpha: () -> Float): Modifier {
    val cutout = LocalStatusChipCutout.current ?: return this
    val latest = rememberUpdatedState(alpha)
    val source = remember { StatusChipSource { latest.value() } }
    DisposableEffect(cutout, active, source) {
        if (active) cutout.show(source)
        onDispose { cutout.hide(source) }
    }
    return onSizeChanged { source.size = it }
}

/**
 * The same top-right seat as the status Row, in shared tile pixels. Cell's outer scaled Box
 * contains both the art and TileStatus. A landed ring uses that same focus scale, so its cut
 * scales with the chip. Inverse-scaling here would move the cut away from the chip.
 */
internal fun statusChipRect(tile: Size, chip: IntSize, chipHeight: Float, overlap: Float, corner: Float): Rect {
    val offset = chipHeight * overlap - corner * CORNER_SAFE
    return Rect(tile.width + offset - chip.width, -offset, tile.width + offset, -offset + chip.height)
}

internal fun DrawScope.withoutStatusChip(cutout: StatusChipCutout?, theme: Theme, corner: Float, path: Path, draw: DrawScope.() -> Unit) {
    val source = cutout?.source
    val chip = source?.size
    if (source == null || chip == null || chip.width <= 0 || chip.height <= 0 || source.alpha() <= 0f) {
        draw()
        return
    }
    val rect = statusChipRect(size, chip, theme.style.tile.statusChipPx, theme.style.tile.statusOverlap, corner)
    path.reset()
    path.addOutline(theme.style.shape.control().createOutline(rect.size, layoutDirection, this))
    path.translate(Offset(rect.left, rect.top))
    clipPath(path, ClipOp.Difference, block = draw)
}
