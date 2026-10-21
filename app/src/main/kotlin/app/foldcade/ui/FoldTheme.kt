package app.foldcade.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ImageBitmap
import app.foldcade.language.Theme
import app.foldcade.language.ThemeShape
import app.foldcade.language.builtInTheme
import kotlin.math.roundToInt

/** Paint plus the images a loaded theme zip contributed. */
data class FoldPaint(
    val theme: Theme,
    val wallpapers: ThemeWallpapers = ThemeWallpapers.None,
    val icons: Map<String, List<app.foldcade.language.ThemeIconAsset>> = emptyMap(),
) {
    val wallpaperTop: ImageBitmap? get() = wallpapers.top
    val wallpaperBottom: ImageBitmap? get() = wallpapers.bottom
}

/** A pack's two wallpapers, decoded the first time a panel shows one. */
class ThemeWallpapers(private val decode: (top: Boolean) -> ImageBitmap?) {
    val top: ImageBitmap? by lazy { decode(true) }
    val bottom: ImageBitmap? by lazy { decode(false) }

    companion object {
        val None = ThemeWallpapers { null }
    }
}

val LocalFoldTheme = staticCompositionLocalOf { FoldPaint(builtInTheme()) }

/** A small control's outline: a pill or a circle at Afterglow's `shape.round` of 0.5, square at 0. */
internal fun ThemeShape.control(): Shape = RoundedCornerShape(percent = (round * 100f).roundToInt())
