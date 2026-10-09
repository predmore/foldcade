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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.foldcade.language.Metrics
import app.foldcade.language.TypeRamp
import app.foldcade.language.builtInTheme

/**
 * Volume row for the L1 panel. Isolated so the theme-art pull request can rebase
 * the rest of the panel. Activate still steps the value. Touch drags the slider.
 */
@Composable
fun MusicVolumeRow(
    label: String,
    volume: Float,
    focused: Boolean,
    interactive: Boolean,
    onStep: () -> Unit,
    onVolume: (Float) -> Unit,
) {
    val theme = builtInTheme()
    val fraction = volume.coerceIn(0f, 1f)
    val track = theme.muted
    val fill = theme.onBackground
    Column(
        Modifier
            .fillMaxWidth()
            .musicFocus(focused)
            .padding(8.dp),
    ) {
        BasicText(
            text = label,
            modifier = Modifier
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
            style = TextStyle(
                color = if (focused) theme.onBackground else theme.muted,
                fontSize = TypeRamp.sideRow,
                fontFamily = theme.font,
            ),
        )
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(18.dp)
                .drawWithContent {
                    val bar = 8.dp.toPx()
                    val top = (size.height - bar) / 2f
                    drawRect(track, topLeft = Offset(0f, top), size = Size(size.width, bar))
                    drawRect(fill, topLeft = Offset(0f, top), size = Size(size.width * fraction, bar))
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

@Composable
private fun Modifier.musicFocus(focused: Boolean): Modifier {
    val color = builtInTheme().focus
    return drawWithContent {
        drawContent()
        if (!focused) return@drawWithContent
        val stroke = Metrics.focusStrokePx
        drawRect(
            color = color,
            topLeft = Offset(-stroke / 2f, -stroke / 2f),
            size = Size(size.width + stroke, size.height + stroke),
            style = Stroke(width = stroke),
        )
    }
}
