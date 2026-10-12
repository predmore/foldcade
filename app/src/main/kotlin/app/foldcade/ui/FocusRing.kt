package app.foldcade.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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

    fun brush(size: Size): Brush = Brush.linearGradient(
        colors = listOf(design.focusRing.start ?: theme.focus, design.focusRing.end ?: theme.focus),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )

    /**
     * Draws around this draw scope's bounds, [gapPx] outside them.
     * [pronounced] uses the same green-to-blue stroke, heavier, for a choice that
     * has to read from across the bottom screen.
     */
    fun DrawScope.drawFocusRing(cornerPx: Float, pronounced: Boolean = false) {
        val stroke = if (pronounced) design.focusRing.pronouncedStroke else strokePx
        val glowSpread = if (pronounced) design.focusRing.pronouncedSpread else glowSpreadPx
        val glowAlpha = if (pronounced) design.focusRing.pronouncedAlpha else design.focusRing.glowAlpha
        val outset = gapPx + stroke / 2f
        val ringSize = Size(size.width + outset * 2f, size.height + outset * 2f)
        val topLeft = Offset(-outset, -outset)
        val radius = CornerRadius(cornerPx + outset, cornerPx + outset)
        val brush = brush(ringSize)
        for (step in glowSteps downTo 1) {
            val spread = glowSpread * step / glowSteps
            drawRoundRect(
                brush = brush,
                topLeft = topLeft,
                size = ringSize,
                cornerRadius = radius,
                style = Stroke(width = stroke + spread * 2f),
                alpha = glowAlpha,
            )
        }
        drawRoundRect(
            brush = brush,
            topLeft = topLeft,
            size = ringSize,
            cornerRadius = radius,
            style = Stroke(width = stroke),
        )
    }
}
