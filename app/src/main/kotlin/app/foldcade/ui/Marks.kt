package app.foldcade.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import app.foldcade.R
import app.foldcade.language.MarkAccent

/**
 * Original single-weight line marks. The host tints them and paints the soft glow.
 * Not console logos.
 */
data class MarkGlyph(
    val drawable: Int,
    val accent: Color,
)

fun markGlyph(mark: String): MarkGlyph? = when (mark) {
    "dual" -> MarkGlyph(R.drawable.mark_dual, MarkAccent.dual)
    "pocket" -> MarkGlyph(R.drawable.mark_pocket, MarkAccent.pocket)
    "desk" -> MarkGlyph(R.drawable.mark_desk, MarkAccent.desk)
    "beam" -> MarkGlyph(R.drawable.mark_beam, MarkAccent.beam)
    else -> null
}

@Composable
fun MarkIcon(mark: String, scale: Float) {
    val glyph = markGlyph(mark) ?: return
    val painter = painterResource(glyph.drawable)
    val tint = ColorFilter.tint(glyph.accent)
    val size = 0.46f * scale
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(size).graphicsLayer { scaleX = 1.22f; scaleY = 1.22f; alpha = 0.20f },
            colorFilter = tint,
        )
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(size).graphicsLayer { scaleX = 1.08f; scaleY = 1.08f; alpha = 0.34f },
            colorFilter = tint,
        )
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(size),
            colorFilter = tint,
        )
    }
}
