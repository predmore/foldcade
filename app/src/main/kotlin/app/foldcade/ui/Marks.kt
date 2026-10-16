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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.runtime.remember
import app.foldcade.language.CollectionLook
import app.foldcade.language.ThemeIconBlend
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Marks: every place and platform drawn as a lit object, for the grid and the hero. They carry their
 * own colour, so the host does not tint them. The accent paints the light behind the tile and its glow.
 * Each also ships as a flat one-ink symbol for menus and the collection picker ([symbolIcon]).
 * Not console logos. How they are drawn, and how to add one: docs/iconography.md.
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
    val drawable = MARKS[mark]?.mark
        ?: R.drawable.mark_console.takeIf { paint.icons["mark.$mark"].orEmpty().isNotEmpty() }
        ?: return null
    val icon = themeIcon("mark.$mark", drawable, pixels)
    return MarkGlyph(icon, icon.resolved?.asset?.accent ?: paint.theme.focus)
}

/** The flat symbol for a place, a platform, or a collection glyph. It is one ink, so it takes a tint. */
@Composable
internal fun symbolIcon(mark: String, pixels: Int = 256): ThemeIconPainter? {
    val drawable = MARKS[mark]?.symbol ?: GLYPHS[mark] ?: return null
    return themeIcon("symbol.$mark", drawable, pixels)
}

private class MarkArt(val mark: Int, val symbol: Int)

/** Places and platforms, by mark name. A platform's mark is named by its canonical id. */
private val MARKS: Map<String, MarkArt> = mapOf(
    "console" to MarkArt(R.drawable.mark_console, R.drawable.symbol_console),
    "home" to MarkArt(R.drawable.mark_home, R.drawable.symbol_home),
    "library" to MarkArt(R.drawable.mark_library, R.drawable.symbol_library),
    "android-apps" to MarkArt(R.drawable.mark_android_apps, R.drawable.symbol_android_apps),
    "android-games" to MarkArt(R.drawable.mark_android_games, R.drawable.symbol_android_games),
    "folder" to MarkArt(R.drawable.mark_folder, R.drawable.symbol_folder),
    "cloud" to MarkArt(R.drawable.mark_cloud, R.drawable.symbol_cloud),
    "moonlight" to MarkArt(R.drawable.mark_moonlight, R.drawable.symbol_moonlight),
    "pc" to MarkArt(R.drawable.mark_pc, R.drawable.symbol_pc),
    "droiddeck" to MarkArt(R.drawable.mark_droiddeck, R.drawable.symbol_droiddeck),
    "emulators" to MarkArt(R.drawable.mark_emulators, R.drawable.symbol_emulators),
    "nintendo-3ds" to MarkArt(R.drawable.mark_nintendo_3ds, R.drawable.symbol_nintendo_3ds),
    "nintendo-ds" to MarkArt(R.drawable.mark_nintendo_ds, R.drawable.symbol_nintendo_ds),
    "game-boy" to MarkArt(R.drawable.mark_game_boy, R.drawable.symbol_game_boy),
    "game-boy-color" to MarkArt(R.drawable.mark_game_boy_color, R.drawable.symbol_game_boy_color),
    "game-boy-advance" to MarkArt(R.drawable.mark_game_boy_advance, R.drawable.symbol_game_boy_advance),
    "nes" to MarkArt(R.drawable.mark_nes, R.drawable.symbol_nes),
    "snes" to MarkArt(R.drawable.mark_snes, R.drawable.symbol_snes),
    "nintendo-64" to MarkArt(R.drawable.mark_nintendo_64, R.drawable.symbol_nintendo_64),
    "gamecube" to MarkArt(R.drawable.mark_gamecube, R.drawable.symbol_gamecube),
    "wii" to MarkArt(R.drawable.mark_wii, R.drawable.symbol_wii),
    "nintendo-switch" to MarkArt(R.drawable.mark_nintendo_switch, R.drawable.symbol_nintendo_switch),
    "playstation" to MarkArt(R.drawable.mark_playstation, R.drawable.symbol_playstation),
    "playstation-2" to MarkArt(R.drawable.mark_playstation_2, R.drawable.symbol_playstation_2),
    "psp" to MarkArt(R.drawable.mark_psp, R.drawable.symbol_psp),
    "genesis" to MarkArt(R.drawable.mark_genesis, R.drawable.symbol_genesis),
    "master-system" to MarkArt(R.drawable.mark_master_system, R.drawable.symbol_master_system),
    "game-gear" to MarkArt(R.drawable.mark_game_gear, R.drawable.symbol_game_gear),
    "saturn" to MarkArt(R.drawable.mark_saturn, R.drawable.symbol_saturn),
    "dreamcast" to MarkArt(R.drawable.mark_dreamcast, R.drawable.symbol_dreamcast),
    "clamshell" to MarkArt(R.drawable.mark_clamshell, R.drawable.symbol_clamshell),
    "slim" to MarkArt(R.drawable.mark_slim, R.drawable.symbol_slim),
    "handheld" to MarkArt(R.drawable.mark_handheld, R.drawable.symbol_handheld),
    "cartridge" to MarkArt(R.drawable.mark_cartridge, R.drawable.symbol_cartridge),
    "disc" to MarkArt(R.drawable.mark_disc, R.drawable.symbol_disc),
)

/** Collection glyphs: they have no object of their own, since the folder is the object. */
private val GLYPHS: Map<String, Int> = mapOf(
    "star" to R.drawable.symbol_star,
    "heart" to R.drawable.symbol_heart,
    "trophy" to R.drawable.symbol_trophy,
    "crown" to R.drawable.symbol_crown,
    "gem" to R.drawable.symbol_gem,
    "sword" to R.drawable.symbol_sword,
    "shield" to R.drawable.symbol_shield,
    "skull" to R.drawable.symbol_skull,
    "flame" to R.drawable.symbol_flame,
    "rocket" to R.drawable.symbol_rocket,
    "racing-flag" to R.drawable.symbol_racing_flag,
    "puzzle" to R.drawable.symbol_puzzle,
    "ghost" to R.drawable.symbol_ghost,
    "mushroom" to R.drawable.symbol_mushroom,
    "music" to R.drawable.symbol_music,
    "two-players" to R.drawable.symbol_two_players,
    "hourglass" to R.drawable.symbol_hourglass,
    "play-badge" to R.drawable.symbol_play_badge,
    "check-badge" to R.drawable.symbol_check_badge,
    "clock" to R.drawable.symbol_clock,
    "leaf" to R.drawable.symbol_leaf,
    "paw" to R.drawable.symbol_paw,
)

/**
 * A collection: the folder in its colour, its glyph pressed into the front, then the parts that keep
 * their own colour. One body serves every colour, multiplied by the tint, so a pack overrides the shape once.
 * The tint also paints the tile's light and glow.
 */
@Composable
private fun collectionGlyph(look: CollectionLook, pixels: Int): MarkGlyph {
    val body = themeIcon("mark.collection-body", R.drawable.mark_collection_body, pixels).painter
    val face = themeIcon("mark.collection-face", R.drawable.mark_collection_face, pixels).painter
    val glyph = symbolIcon(look.glyph, pixels)?.painter
    val painter = remember(body, face, glyph, look.tint) { CollectionPainter(body, face, glyph, look.tint.color) }
    return MarkGlyph(ThemeIconPainter(painter, null), look.tint.color)
}

private class CollectionPainter(
    private val body: Painter,
    private val face: Painter,
    private val glyph: Painter?,
    tint: Color,
) : Painter() {
    private val tinted = ColorFilter.tint(tint, BlendMode.Modulate)
    private val pressed = ColorFilter.tint(lerp(tint, Color.Black, PRESSED_DEPTH))
    private val lip = ColorFilter.tint(Color.White)

    override val intrinsicSize: Size get() = body.intrinsicSize

    override fun DrawScope.onDraw() {
        with(body) { draw(size, colorFilter = tinted) }
        glyph?.let { glyph ->
            // Where the glyph sits, in the art's 64-unit grid (tools/art/marks.py: face_glyph).
            val unit = min(size.width, size.height) / ART_UNITS
            val box = Size(GLYPH_SIZE * unit, GLYPH_SIZE * unit)
            val left = GLYPH_X * unit - box.width / 2f
            val top = GLYPH_Y * unit - box.height / 2f
            // Pressed in: a faint light lip on its lower edge, under the shape in a deep shade of the tint.
            translate(left, top + box.height * LIP_DROP) { with(glyph) { draw(box, alpha = LIP_ALPHA, colorFilter = lip) } }
            translate(left, top) { with(glyph) { draw(box, colorFilter = pressed) } }
        }
        with(face) { draw(size) }
    }
}

private const val ART_UNITS = 64f
private const val GLYPH_X = 32f
private const val GLYPH_Y = 39.48f
private const val GLYPH_SIZE = 29.3f
private const val PRESSED_DEPTH = 0.62f
private const val LIP_DROP = 0.025f
private const val LIP_ALPHA = 0.32f

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

/** A flat symbol in [tint], filling [scale] of its box. */
@Composable
internal fun SymbolIcon(mark: String, scale: Float, tint: Color, alpha: () -> Float = { 1f }) {
    val icon = symbolIcon(mark) ?: return
    val filter = remember(icon, tint) { icon.tint(tint) }
    Canvas(Modifier.fillMaxSize(scale)) { with(icon.painter) { draw(size, alpha = alpha(), colorFilter = filter) } }
}
