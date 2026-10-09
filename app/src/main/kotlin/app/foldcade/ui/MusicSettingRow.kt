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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.foldcade.language.TypeRamp
import kotlin.math.min

/**
 * Volume row for the L1 panel. Isolated so the theme-art pull request can rebase
 * the rest of the panel. Activate still steps the value. Touch drags the slider.
 */
@Composable
fun MusicVolumeRow(
    label: String,
    value: String,
    volume: Float,
    focused: Boolean,
    interactive: Boolean,
    onStep: () -> Unit,
    onVolume: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalFoldTheme.current.theme
    val fraction = volume.coerceIn(0f, 1f)
    val accent = theme.focus
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
        Row(
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
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    color = if (focused) theme.onBackground else theme.muted,
                    fontSize = TypeRamp.sideRow,
                    fontFamily = theme.font,
                ),
            )
            BasicText(
                text = value,
                style = TextStyle(
                    color = if (focused) theme.focus else theme.onBackground,
                    fontSize = TypeRamp.sideRow,
                    fontFamily = theme.font,
                    textAlign = TextAlign.End,
                ),
            )
        }
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(36.dp)
                .graphicsLayer { clip = false }
                .drawWithContent {
                    val trackHeight = 6.dp.toPx()
                    val trackTop = (size.height - trackHeight) / 2f
                    val trackRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                    drawRoundRect(
                        color = accent.copy(alpha = 0.28f),
                        topLeft = Offset(0f, trackTop),
                        size = Size(size.width, trackHeight),
                        cornerRadius = trackRadius,
                    )
                    val fillWidth = size.width * fraction
                    if (fillWidth > 1f) {
                        val cap = min(trackHeight / 2f, fillWidth / 2f)
                        drawRoundRect(
                            color = accent,
                            topLeft = Offset(0f, trackTop),
                            size = Size(fillWidth, trackHeight),
                            cornerRadius = CornerRadius(cap, cap),
                        )
                    }
                    val thumbRadius = 7.dp.toPx()
                    val center = Offset(
                        x = (size.width * fraction).coerceIn(thumbRadius, (size.width - thumbRadius).coerceAtLeast(thumbRadius)),
                        y = size.height / 2f,
                    )
                    val glow = thumbRadius * 2.2f
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
                        Modifier.pointerInput(onVolume) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                fun apply(x: Float) {
                                    val width = size.width.coerceAtLeast(1)
                                    onVolume((x / width).coerceIn(0f, 1f))
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
