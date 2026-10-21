package app.foldcade.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.foldcade.language.Theme

/**
 * The focus ring: a green-to-blue stroke just outside the focused shape, with a soft glow.
 * Grid tiles and menu rows share it so focus reads the same everywhere. It hugs the
 * shape so a tile in the first row is not cut by the grid's clip.
 */
internal class FocusRing(private val theme: Theme) {
    private val design = theme.style
    val strokePx get() = design.focusRing.stroke
    val gapPx get() = design.focusRing.gap
    private val glowSteps get() = design.focusRing.glowSteps
    private val glowSpreadPx get() = design.focusRing.glowSpread

    /** The gradient's ends: green at the top left, blue at the bottom right, or the theme's focus color. */
    val start: Color get() = design.focusRing.start ?: theme.focus
    val end: Color get() = design.focusRing.end ?: theme.focus

    /** The gliding highlights' ends: `colors.highlight` when the pack sets one, else the ring's. */
    val highlightStart: Color get() = design.colors.highlight ?: start
    val highlightEnd: Color get() = design.colors.highlight ?: end

    /** What a switch, a check or a quick tile that is on fills with: `colors.on`, else the ring's. */
    val onStart: Color get() = design.colors.on ?: start
    val onEnd: Color get() = design.colors.on ?: end

    fun brush(size: Size, origin: Offset = Offset.Zero): Brush = Brush.linearGradient(
        colors = listOf(start, end),
        start = origin,
        end = Offset(origin.x + size.width, origin.y + size.height),
    )

    /**
     * Draws around [bounds], this draw scope's own by default, [gapPx] outside them.
     * [pronounced] uses the same green-to-blue stroke, heavier, for a choice that
     * has to read from across the bottom screen. [alpha] fades the whole ring.
     */
    fun DrawScope.drawFocusRing(
        cornerPx: Float,
        pronounced: Boolean = false,
        alpha: Float = 1f,
        bounds: Rect = Rect(Offset.Zero, size),
    ) {
        val stroke = if (pronounced) design.focusRing.pronouncedStroke else strokePx
        val glowSpread = if (pronounced) design.focusRing.pronouncedSpread else glowSpreadPx
        val glowAlpha = if (pronounced) design.focusRing.pronouncedAlpha else design.focusRing.glowAlpha
        val outset = gapPx + stroke / 2f
        val ringSize = Size(bounds.width + outset * 2f, bounds.height + outset * 2f)
        val topLeft = Offset(bounds.left - outset, bounds.top - outset)
        val radius = CornerRadius(cornerPx + outset, cornerPx + outset)
        val brush = brush(ringSize, topLeft)
        for (step in glowSteps downTo 1) {
            val spread = glowSpread * step / glowSteps
            drawRoundRect(
                brush = brush,
                topLeft = topLeft,
                size = ringSize,
                cornerRadius = radius,
                style = Stroke(width = stroke + spread * 2f),
                alpha = glowAlpha * alpha,
            )
        }
        drawRoundRect(
            brush = brush,
            topLeft = topLeft,
            size = ringSize,
            cornerRadius = radius,
            style = Stroke(width = stroke),
            alpha = alpha,
        )
    }
}
