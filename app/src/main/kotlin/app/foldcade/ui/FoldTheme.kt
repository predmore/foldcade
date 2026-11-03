package app.foldcade.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ImageBitmap
import app.foldcade.language.Theme
import app.foldcade.language.ThemeShape
import app.foldcade.language.builtInTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Paint plus the images a loaded theme zip contributed. */
data class FoldPaint(
    val theme: Theme,
    val wallpapers: ThemeWallpapers = ThemeWallpapers.None,
    val icons: Map<String, List<app.foldcade.language.ThemeIconAsset>> = emptyMap(),
) {
    val wallpaperTop: ImageBitmap? get() = wallpapers.top
    val wallpaperBottom: ImageBitmap? get() = wallpapers.bottom
}

/** A pack's two wallpapers, prepared off Main before its paint reaches either panel. */
class ThemeWallpapers(private val decode: (top: Boolean) -> ImageBitmap?) {
    private data class Images(val top: ImageBitmap?, val bottom: ImageBitmap?)
    @Volatile private var images: Images? = null
    val prepared: Boolean get() = images != null
    val top: ImageBitmap? get() = images?.top
    val bottom: ImageBitmap? get() = images?.bottom

    /** Both panels share one decode, including remembered misses. Call only off Main. */
    @Synchronized fun prepare() {
        if (images == null) images = Images(decode(true), decode(false))
    }

    companion object {
        val None = ThemeWallpapers { null }.apply { prepare() }
    }
}

/** Keep the previous complete paint while a newly selected pack's images are decoded. */
@Composable
internal fun preparedPaint(requested: FoldPaint): FoldPaint {
    var shown by remember { mutableStateOf(if (requested.wallpapers.prepared) requested else FoldPaint(builtInTheme())) }
    LaunchedEffect(requested) {
        withContext(Dispatchers.IO) { requested.wallpapers.prepare() }
        shown = requested
    }
    return if (requested.wallpapers.prepared) requested else shown
}

val LocalFoldTheme = staticCompositionLocalOf { FoldPaint(builtInTheme()) }

/** A small control's outline: a pill or a circle at Afterglow's `shape.round` of 0.5, square at 0. */
internal fun ThemeShape.control(): Shape = RoundedCornerShape(percent = (round * 100f).roundToInt())
