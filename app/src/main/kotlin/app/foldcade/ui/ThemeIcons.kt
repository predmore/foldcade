package app.foldcade.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import app.foldcade.language.ThemeIconAsset
import app.foldcade.language.ResolvedThemeIcon
import app.foldcade.language.resolveThemeIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Vectors are rasterized off main and reused, including during hero transitions. */
internal object ThemeIconImages {
    private val images = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private fun key(icon: ThemeIconAsset, size: Int) = "${icon.cacheKey}/$size"
    fun cached(icon: ThemeIconAsset, size: Int): Bitmap? = images.get(key(icon, size))
    fun cached(candidates: List<ThemeIconAsset>, size: Int) = resolveThemeIcon(candidates) { cached(it, size) }
    suspend fun load(candidates: List<ThemeIconAsset>, size: Int): ResolvedThemeIcon<Bitmap>? = withContext(Dispatchers.Default) {
        resolveThemeIcon(candidates) { load(it, size) }
    }
    private fun load(icon: ThemeIconAsset, size: Int): Bitmap? {
        cached(icon, size)?.let { return it }
        return try {
            val bitmap = if (icon.file.endsWith(".xml")) {
                ThemeVector.rasterize(icon.bytes, size)
            } else BitmapFactory.decodeByteArray(icon.bytes, 0, icon.bytes.size)
            bitmap?.also { it.prepareToDraw(); images.put(key(icon, size), it) }
        } catch (error: Exception) {
            android.util.Log.w("FoldcadeTheme", "${icon.owner}/${icon.file}: ${error.message}")
            null
        }
    }
}

internal data class ThemeIconPainter(
    val painter: Painter,
    val resolved: ResolvedThemeIcon<Bitmap>?,
) {
    fun tint(color: Color): ColorFilter? = if (resolved?.asset?.tint != false) ColorFilter.tint(color) else null

    /**
     * [color] only for an icon its pack marked `"tint": true`. For slots whose art may carry state
     * colours, such as status icons, so the built-in drawables keep theirs.
     */
    fun requestedTint(color: Color): ColorFilter? = if (resolved?.asset?.tint == true) ColorFilter.tint(color) else null
}

/** Semantic keys are stable across packs; drawable ids are only the emergency fallback. */
@Composable
internal fun themeIcon(slot: String, fallback: Int, pixels: Int = 256): ThemeIconPainter {
    val candidates = LocalFoldTheme.current.icons[slot].orEmpty()
    var resolved by remember(candidates, pixels) { mutableStateOf(ThemeIconImages.cached(candidates, pixels)) }
    LaunchedEffect(candidates, pixels) { resolved = ThemeIconImages.load(candidates, pixels) }
    val painter = resolved?.image?.let { remember(it) { BitmapPainter(it.asImageBitmap()) } } ?: painterResource(fallback)
    return ThemeIconPainter(painter, resolved)
}

/** Afterglow's file prefix for each slot family, and the separator its slot names use for `_`. */
private val SLOT_FAMILIES = listOf(
    Triple("ic_btn_", "button.", '.'),
    Triple("ic_status_", "status.", '.'),
    Triple("ic_badge_", "badge.", '.'),
    Triple("ic_control_", "control.", '.'),
    Triple("mark_", "mark.", '-'),
    Triple("symbol_", "symbol.", '-'),
)

/** Converts the old art-kit resource names to stable public slots. */
internal fun iconSlot(resource: String): String {
    val (file, slot, separator) = SLOT_FAMILIES.firstOrNull { resource.startsWith(it.first) } ?: return resource
    return slot + resource.removePrefix(file).replace('_', separator)
}

/** The emergency drawable's name for [slot]: Afterglow's file, compiled into the app. */
internal fun slotResource(slot: String): String? {
    val (file, prefix, separator) = SLOT_FAMILIES.firstOrNull { slot.startsWith(it.second) } ?: return null
    return file + slot.removePrefix(prefix).replace(separator, '_')
}

/**
 * The icon in [slot] from the theme, else Afterglow's compiled copy. Null when neither has it,
 * so a pack can add slots the app has no drawable for, such as a new platform's mark.
 */
@Composable
internal fun themeIconOrNull(slot: String, pixels: Int = 256): ThemeIconPainter? {
    val context = LocalContext.current
    val fallback = remember(slot) {
        slotResource(slot)?.let { context.resources.getIdentifier(it, "drawable", context.packageName) } ?: 0
    }
    val candidates = LocalFoldTheme.current.icons[slot].orEmpty()
    if (candidates.isEmpty() && fallback == 0) return null
    var resolved by remember(candidates, pixels) { mutableStateOf(ThemeIconImages.cached(candidates, pixels)) }
    var loaded by remember(candidates, pixels) { mutableStateOf(resolved != null) }
    LaunchedEffect(candidates, pixels) {
        resolved = ThemeIconImages.load(candidates, pixels)
        loaded = true
    }
    val image = resolved?.image
    val painter = when {
        image != null -> remember(image) { BitmapPainter(image.asImageBitmap()) }
        fallback != 0 -> painterResource(fallback)
        // Every candidate failed to decode, so the caller can fall back, as a missing slot does.
        loaded -> return null
        else -> EmptyPainter
    }
    return ThemeIconPainter(painter, resolved)
}

/** Draws nothing, while a pack-only icon decodes. */
private object EmptyPainter : Painter() {
    override val intrinsicSize: Size get() = Size.Unspecified
    override fun DrawScope.onDraw() = Unit
}

@Composable
internal fun themeIcon(drawable: Int, pixels: Int = 128): ThemeIconPainter {
    val resources = LocalResources.current
    val slot = remember(drawable) { iconSlot(resources.getResourceEntryName(drawable)) }
    return themeIcon(slot, drawable, pixels)
}

@Composable
internal fun themeIconPainter(drawable: Int, pixels: Int = 128): Painter = themeIcon(drawable, pixels).painter
