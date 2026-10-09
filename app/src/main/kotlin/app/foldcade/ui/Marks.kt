package app.foldcade.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import app.foldcade.R
import app.foldcade.language.MarkAccent

/**
 * Illustrated platform marks. They carry their own colour, so the host does not tint them.
 * The accent still paints the tile glow. Not console logos.
 */
data class MarkGlyph(
    val drawable: Int,
    val accent: Color,
)

fun markGlyph(mark: String): MarkGlyph? = when (mark) {
    "clamshell" -> MarkGlyph(R.drawable.mark_clamshell_lit, MarkAccent.clamshell)
    "slim" -> MarkGlyph(R.drawable.mark_slim_lit, MarkAccent.slim)
    "handheld" -> MarkGlyph(R.drawable.mark_handheld_lit, MarkAccent.handheld)
    "cartridge" -> MarkGlyph(R.drawable.mark_cartridge_lit, MarkAccent.cartridge)
    "disc" -> MarkGlyph(R.drawable.mark_disc_lit, MarkAccent.disc)
    "cloud" -> MarkGlyph(R.drawable.mark_cloud_lit, MarkAccent.cloud)
    else -> null
}

@Composable
fun MarkIcon(mark: String, scale: Float) {
    val glyph = markGlyph(mark) ?: return
    val painter = painterResource(glyph.drawable)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(scale),
        )
    }
}
