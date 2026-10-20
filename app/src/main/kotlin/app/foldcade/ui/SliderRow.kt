package app.foldcade.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** The finger target. The drawn track stays the theme's height, centred in this box. */
internal const val SLIDER_TOUCH_TARGET_DP = 48

/**
 * A label, its value, and a slider under them: music volume in Settings, and
 * brightness in R1. Left and right step it from the keys. A tap on the label
 * runs [onStep]. Touch drags the slider and reports a fraction from 0 to 1.
 * Without [showLabel] it is the bare slider, and [label] is only for touch readers.
 */
@Composable
fun SliderRow(
    label: String,
    value: String,
    fraction: Float,
    focused: Boolean,
    interactive: Boolean,
    onStep: () -> Unit,
    onFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    scale: Float = 1f,
    labelStyle: TextStyle? = null,
) {
    val theme = LocalFoldTheme.current.theme
    val fraction = fraction.coerceIn(0f, 1f)
    val accent = theme.focus
    val controls = theme.style.controls
    val round = theme.style.shape.round
    val lit by eased(if (focused) 1f else 0f, theme.motion.durationFocus, scale)
    // The same margins as a menu row, so the words line up down the list.
    Column(modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)) {
        if (showLabel) Row(
            Modifier
                .fillMaxWidth()
                .focusProperties { canFocus = false }
                .then(
                    if (interactive) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onStep,
                        )
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = label,
                modifier = Modifier.weight(1f).graphicsLayer { alpha = theme.style.menu.labelRestAlpha.let { it + (1f - it) * lit } },
                style = (labelStyle ?: TextStyle(fontSize = theme.style.type.sideRow, fontFamily = theme.font))
                    .copy(color = theme.onBackground),
            )
            BasicText(
                text = value,
                style = TextStyle(
                    color = if (focused) theme.focus else theme.onBackground,
                    fontSize = LocalFoldTheme.current.theme.style.type.sideRow,
                    fontFamily = theme.font,
                    textAlign = TextAlign.End,
                ),
            )
        }
        Box(
            Modifier
                .padding(top = if (showLabel) 8.dp else 0.dp)
                .fillMaxWidth()
                .then(if (showLabel) Modifier else Modifier.semantics { contentDescription = "$label $value" })
                .height(SLIDER_TOUCH_TARGET_DP.dp)
                .graphicsLayer { clip = false }
                .drawWithContent {
                    val trackHeight = controls.sliderTrackDp.dp.toPx()
                    val trackTop = (size.height - trackHeight) / 2f
                    val trackRadius = CornerRadius(trackHeight * round, trackHeight * round)
                    drawRoundRect(
                        color = accent.copy(alpha = controls.sliderTrackAlpha),
                        topLeft = Offset(0f, trackTop),
                        size = Size(size.width, trackHeight),
                        cornerRadius = trackRadius,
                    )
                    val fillWidth = size.width * fraction
                    if (fillWidth > 1f) {
                        val cap = min(trackHeight * round, fillWidth / 2f)
                        drawRoundRect(
                            color = accent,
                            topLeft = Offset(0f, trackTop),
                            size = Size(fillWidth, trackHeight),
                            cornerRadius = CornerRadius(cap, cap),
                        )
                    }
                    val thumbRadius = controls.sliderKnobDp.dp.toPx()
                    val center = Offset(
                        x = (size.width * fraction).coerceIn(thumbRadius, (size.width - thumbRadius).coerceAtLeast(thumbRadius)),
                        y = size.height / 2f,
                    )
                    val glow = thumbRadius * controls.sliderGlow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to accent.copy(alpha = 0.9f),
                                0.42f to accent.copy(alpha = 0.34f),
                                1f to Color.Transparent,
                            ),
                            center = center,
                            radius = glow,
                        ),
                        radius = glow,
                        center = center,
                    )
                    drawCircle(color = accent, radius = thumbRadius * 0.62f, center = center)
                }
                .then(
                    if (interactive) {
                        Modifier.pointerInput(onFraction) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                fun apply(x: Float) {
                                    val width = size.width.coerceAtLeast(1)
                                    onFraction((x / width).coerceIn(0f, 1f))
                                }
                                apply(down.position.x)
                                drag(down.id) { change ->
                                    if (change.positionChange() != Offset.Zero) change.consume()
                                    apply(change.position.x)
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        )
    }
}
