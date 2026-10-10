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
 * Illustrated platform and library marks. They carry their own colour, so the host does not tint them.
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
    else -> markDrawable(mark)?.let { drawable -> MarkGlyph(drawable, MarkAccent.byMark.getValue(mark)) }
}

/** Platform and library marks. A platform's mark is named by its canonical id. */
private fun markDrawable(mark: String): Int? = when (mark) {
    "nintendo-3ds" -> R.drawable.mark_nintendo_3ds
    "nintendo-ds" -> R.drawable.mark_nintendo_ds
    "game-boy" -> R.drawable.mark_game_boy
    "game-boy-color" -> R.drawable.mark_game_boy_color
    "game-boy-advance" -> R.drawable.mark_game_boy_advance
    "nes" -> R.drawable.mark_nes
    "snes" -> R.drawable.mark_snes
    "nintendo-64" -> R.drawable.mark_nintendo_64
    "gamecube" -> R.drawable.mark_gamecube
    "wii" -> R.drawable.mark_wii
    "nintendo-switch" -> R.drawable.mark_nintendo_switch
    "playstation" -> R.drawable.mark_playstation
    "playstation-2" -> R.drawable.mark_playstation_2
    "psp" -> R.drawable.mark_psp
    "genesis" -> R.drawable.mark_genesis
    "master-system" -> R.drawable.mark_master_system
    "game-gear" -> R.drawable.mark_game_gear
    "saturn" -> R.drawable.mark_saturn
    "dreamcast" -> R.drawable.mark_dreamcast
    "android-games" -> R.drawable.mark_android_games
    "android-apps" -> R.drawable.mark_android_apps
    "pc" -> R.drawable.mark_pc
    "moonlight" -> R.drawable.mark_moonlight
    "folder" -> R.drawable.mark_folder
    "library" -> R.drawable.mark_library
    "console" -> R.drawable.mark_console
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
