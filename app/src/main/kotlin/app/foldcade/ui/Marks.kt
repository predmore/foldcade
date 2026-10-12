package app.foldcade.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.foldcade.R
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.foldcade.language.ThemeIconBlend
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Illustrated platform and library marks. They carry their own colour, so the host does not tint them.
 * The accent still paints the tile glow. Not console logos.
 */
internal data class MarkGlyph(
    val icon: ThemeIconPainter,
    val accent: Color,
) {
    fun heroScale(artScale: Float): Float = if (icon.resolved?.asset?.blend == ThemeIconBlend.Screen) artScale else 0.86f
}

@Composable
internal fun markGlyph(mark: String, pixels: Int = 256): MarkGlyph? {
    val paint = LocalFoldTheme.current
    val drawable = when (mark) {
        "clamshell" -> R.drawable.mark_clamshell_lit
        "slim" -> R.drawable.mark_slim_lit
        "handheld" -> R.drawable.mark_handheld_lit
        "cartridge" -> R.drawable.mark_cartridge_lit
        "disc" -> R.drawable.mark_disc_lit
        "cloud" -> R.drawable.mark_cloud_lit
        else -> markDrawable(mark) ?: R.drawable.mark_console.takeIf { paint.icons["mark.$mark"].orEmpty().isNotEmpty() }
    } ?: return null
    val icon = themeIcon("mark.$mark", drawable, pixels)
    return MarkGlyph(icon, icon.resolved?.asset?.accent ?: paint.theme.focus)
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
    "droiddeck" -> R.drawable.mark_droiddeck
    "emulators" -> R.drawable.mark_emulators
    "folder" -> R.drawable.mark_folder
    "library" -> R.drawable.mark_library
    "console" -> R.drawable.mark_console
    else -> null
}

@Composable
internal fun MarkIcon(mark: String, scale: Float) {
    val glyph = markGlyph(mark) ?: return
    MarkIcon(glyph, scale)
}

/** Legacy PNGs retain Screen blending, through the same resolution path as modern icons. */
@Composable
internal fun MarkIcon(glyph: MarkGlyph, scale: Float, alpha: () -> Float = { 1f }) {
    val icon = glyph.icon
    val image = icon.resolved?.image?.let { androidx.compose.runtime.remember(it) { it.asImageBitmap() } }
    Canvas(Modifier.fillMaxSize(scale)) {
        val intrinsic = icon.painter.intrinsicSize
        val target = if (intrinsic.width > 0f && intrinsic.height > 0f) {
            val fit = min(size.width / intrinsic.width, size.height / intrinsic.height)
            Size(intrinsic.width * fit, intrinsic.height * fit)
        } else size
        val x = (size.width - target.width) / 2f
        val y = (size.height - target.height) / 2f
        if (icon.resolved?.asset?.blend == ThemeIconBlend.Screen && image != null) {
            drawImage(image, dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
                dstSize = IntSize(target.width.roundToInt().coerceAtLeast(1), target.height.roundToInt().coerceAtLeast(1)),
                alpha = alpha(), blendMode = BlendMode.Screen)
        } else translate(x, y) { with(icon.painter) { draw(target, alpha = alpha()) } }
    }
}
