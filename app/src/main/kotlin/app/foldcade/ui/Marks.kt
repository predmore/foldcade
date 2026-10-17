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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.runtime.remember
import app.foldcade.language.CollectionLook
import app.foldcade.language.collectionGlyphSlot
import app.foldcade.language.screenedInset
import app.foldcade.language.ThemeIconBlend
import app.foldcade.language.MARK_CONSOLE
import app.foldcade.language.Theme
import app.foldcade.language.ThemeStyle
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
    /** Legacy Screen PNGs fill [artScale] of the hero like a cover; drawn marks use the theme's hero scale. */
    fun heroScale(theme: Theme): Float =
        if (icon.resolved?.asset?.blend == ThemeIconBlend.Screen) theme.artScale else theme.style.hero.markScale
}

/**
 * The object for a place or platform, from its `mark.` slot. Any slot a pack fills works, so a pack
 * can draw a platform Afterglow has not. A mark nobody drew, such as a platform without art, wears
 * the controller.
 */
@Composable
internal fun markGlyph(mark: String, pixels: Int = 256): MarkGlyph? {
    CollectionLook.parse(mark)?.let { return collectionGlyph(it, pixels) }
    val paint = LocalFoldTheme.current
    val icon = themeIconOrNull("mark.$mark", pixels) ?: themeIconOrNull("mark.$MARK_CONSOLE", pixels) ?: return null
    return MarkGlyph(icon, icon.resolved?.asset?.accent ?: paint.theme.focus)
}

/** The flat symbol for a place, a platform, or a collection glyph. It is one ink, so it takes a tint. */
@Composable
internal fun symbolIcon(mark: String, pixels: Int = 256): ThemeIconPainter? = themeIconOrNull("symbol.$mark", pixels)

/**
 * A collection: the folder in its colour, its glyph pressed into the front, then the parts that keep
 * their own colour. One body serves every colour, multiplied by the tint, so a pack overrides the shape once.
 * The tint also paints the tile's light and glow. A pack from before symbols keeps its glyph's mark,
 * drawn on the front as it is (see [collectionGlyphSlot]). Colours, where the glyph sits, and how deep
 * it is pressed are the theme's `style.collections`.
 */
@Composable
private fun collectionGlyph(look: CollectionLook, pixels: Int): MarkGlyph {
    val paint = LocalFoldTheme.current
    val style = paint.theme.style
    val body = themeIcon("mark.collection-body", R.drawable.mark_collection_body, pixels).painter
    val face = themeIcon("mark.collection-face", R.drawable.mark_collection_face, pixels).painter
    val pressed = remember(paint.icons, look.glyph) { collectionGlyphSlot(look.glyph) { paint.icons[it].orEmpty() }.startsWith("symbol.") }
    val glyph = if (pressed) symbolIcon(look.glyph, pixels) else markGlyph(look.glyph, pixels)?.icon
    val screened = screenedInset(glyph?.resolved)?.let { remember(it) { it.asImageBitmap() } }
    val tint = style.collections.color(look.tint)
    val painter = remember(body, face, glyph?.painter, pressed, screened, tint, style) {
        CollectionPainter(body, face, glyph?.painter, pressed, screened, tint, style)
    }
    return MarkGlyph(ThemeIconPainter(painter, null), tint)
}

private class CollectionPainter(
    private val body: Painter,
    private val face: Painter,
    private val glyph: Painter?,
    /** A symbol, pressed in. Otherwise a pack's own mark, drawn as it is. */
    private val pressed: Boolean,
    /** A legacy pack's black-backed glyph, screened so its black disappears into the folder. */
    private val screened: ImageBitmap?,
    tint: Color,
    style: ThemeStyle,
) : Painter() {
    private val folder = style.collections
    private val tinted = ColorFilter.tint(tint, BlendMode.Modulate)
    private val deep = ColorFilter.tint(lerp(tint, style.colors.shade, folder.pressedDepth))
    private val lip = ColorFilter.tint(style.colors.shine)

    override val intrinsicSize: Size get() = body.intrinsicSize

    override fun DrawScope.onDraw() {
        with(body) { draw(size, colorFilter = tinted) }
        glyph?.let { glyph ->
            // Where the glyph sits, in the art's 64-unit grid (tools/art/marks.py: face_glyph).
            val unit = min(size.width, size.height) / ART_UNITS
            val box = Size(folder.glyphSize * unit, folder.glyphSize * unit)
            val left = folder.glyphX * unit - box.width / 2f
            val top = folder.glyphY * unit - box.height / 2f
            when {
                // Pressed in: a faint light lip on its lower edge, under the shape in a deep shade of the tint.
                pressed -> {
                    translate(left, top + box.height * folder.lipDrop) { with(glyph) { draw(box, alpha = folder.lipAlpha, colorFilter = lip) } }
                    translate(left, top) { with(glyph) { draw(box, colorFilter = deep) } }
                }
                screened != null -> drawImage(
                    screened,
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(box.width.roundToInt().coerceAtLeast(1), box.height.roundToInt().coerceAtLeast(1)),
                    blendMode = BlendMode.Screen,
                )
                else -> translate(left, top) { with(glyph) { draw(box) } }
            }
        }
        with(face) { draw(size) }
    }
}

/** The folder art's grid. The glyph's place on it is the theme's. */
private const val ART_UNITS = 64f

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
