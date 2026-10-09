package app.foldcade.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import app.foldcade.R
import app.foldcade.language.MarkAccent

/**
 * Original single-weight line marks. The host tints them and paints the glow.
 * Not console logos.
 */
data class MarkGlyph(
    val drawable: Int,
    val accent: Color,
)

fun markGlyph(mark: String): MarkGlyph? = when (mark) {
    "clamshell" -> MarkGlyph(R.drawable.mark_clamshell, MarkAccent.clamshell)
    "slim" -> MarkGlyph(R.drawable.mark_slim, MarkAccent.slim)
    "handheld" -> MarkGlyph(R.drawable.mark_handheld, MarkAccent.handheld)
    "cartridge" -> MarkGlyph(R.drawable.mark_cartridge, MarkAccent.cartridge)
    "disc" -> MarkGlyph(R.drawable.mark_disc, MarkAccent.disc)
    "cloud" -> MarkGlyph(R.drawable.mark_cloud, MarkAccent.cloud)
    else -> null
}

@Composable
fun MarkIcon(mark: String, scale: Float) {
    val glyph = markGlyph(mark) ?: return
    val painter = painterResource(glyph.drawable)
    val tint = ColorFilter.tint(glyph.accent)
    val icon = 0.52f * scale
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(icon),
            colorFilter = tint,
        )
    }
}
