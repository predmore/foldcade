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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.runtime.remember
import app.foldcade.language.CollectionLook
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
    CollectionLook.parse(mark)?.let { return collectionGlyph(it, pixels) }
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
    "star" -> R.drawable.mark_star
    "heart" -> R.drawable.mark_heart
    "trophy" -> R.drawable.mark_trophy
    "crown" -> R.drawable.mark_crown
    "gem" -> R.drawable.mark_gem
    "sword" -> R.drawable.mark_sword
    "shield" -> R.drawable.mark_shield
    "skull" -> R.drawable.mark_skull
    "flame" -> R.drawable.mark_flame
    "rocket" -> R.drawable.mark_rocket
    "racing-flag" -> R.drawable.mark_racing_flag
    "puzzle" -> R.drawable.mark_puzzle
    "ghost" -> R.drawable.mark_ghost
    "mushroom" -> R.drawable.mark_mushroom
    "music" -> R.drawable.mark_music
    "two-players" -> R.drawable.mark_two_players
    "hourglass" -> R.drawable.mark_hourglass
    "play-badge" -> R.drawable.mark_play_badge
    "check-badge" -> R.drawable.mark_check_badge
    "clock" -> R.drawable.mark_clock
    "leaf" -> R.drawable.mark_leaf
    "paw" -> R.drawable.mark_paw
    else -> null
}

/**
 * A collection: the folder card in its colour, its gloss, then the glyph in the well on its face.
 * One body serves every colour, multiplied by the tint, so a pack overrides the shape once.
 * The tint also paints the tile's glow and edge.
 */
@Composable
private fun collectionGlyph(look: CollectionLook, pixels: Int): MarkGlyph {
    val body = themeIcon("mark.collection-body", R.drawable.mark_collection_body, pixels).painter
    val face = themeIcon("mark.collection-face", R.drawable.mark_collection_face, pixels).painter
    val inset = markGlyph(look.glyph, pixels)?.icon?.painter
    val painter = remember(body, face, inset, look.tint) { CollectionPainter(body, face, inset, look.tint.color) }
    return MarkGlyph(ThemeIconPainter(painter, null), look.tint.color)
}

private class CollectionPainter(
    private val body: Painter,
    private val face: Painter,
    private val glyph: Painter?,
    private val tint: Color,
) : Painter() {
    override val intrinsicSize: Size get() = body.intrinsicSize

    override fun DrawScope.onDraw() {
        with(body) { draw(size, colorFilter = ColorFilter.tint(tint, BlendMode.Modulate)) }
        with(face) { draw(size) }
        val glyph = glyph ?: return
        // The well, in the art's 64-unit grid (tools/art/collection_art.py: WELL, GLYPH_BOX).
        val unit = min(size.width, size.height) / ART_UNITS
        val well = RoundRect(
            left = WELL_X * unit, top = WELL_Y * unit, right = (WELL_X + WELL_W) * unit, bottom = (WELL_Y + WELL_H) * unit,
            cornerRadius = CornerRadius(WELL_R * unit),
        )
        val box = GLYPH_BOX * unit
        clipPath(Path().apply { addRoundRect(well) }) {
            translate((WELL_X + WELL_W / 2f) * unit - box / 2f, (WELL_Y + WELL_H / 2f) * unit - box / 2f) {
                with(glyph) { draw(Size(box, box)) }
            }
        }
    }
}

private const val ART_UNITS = 64f
private const val WELL_X = 11.2f
private const val WELL_Y = 25f
private const val WELL_W = 41.6f
private const val WELL_H = 29.2f
private const val WELL_R = 7.4f
private const val GLYPH_BOX = 36f

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
