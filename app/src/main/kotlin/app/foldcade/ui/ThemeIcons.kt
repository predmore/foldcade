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

/** Converts the old art-kit resource names to stable public slots. */
internal fun iconSlot(resource: String): String = when {
    resource.startsWith("ic_btn_") -> "button." + resource.removePrefix("ic_btn_").replace('_', '.')
    resource.startsWith("ic_status_") -> "status." + resource.removePrefix("ic_status_").replace('_', '.')
    resource.startsWith("ic_badge_") -> "badge." + resource.removePrefix("ic_badge_").replace('_', '.')
    resource.startsWith("mark_") -> "mark." + resource.removePrefix("mark_").replace('_', '-')
    resource.startsWith("symbol_") -> "symbol." + resource.removePrefix("symbol_").replace('_', '-')
    else -> resource
}

@Composable
internal fun themeIcon(drawable: Int, pixels: Int = 128): ThemeIconPainter {
    val resources = LocalResources.current
    val slot = remember(drawable) { iconSlot(resources.getResourceEntryName(drawable)) }
    return themeIcon(slot, drawable, pixels)
}

@Composable
internal fun themeIconPainter(drawable: Int, pixels: Int = 128): Painter = themeIcon(drawable, pixels).painter
