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
    val wallpaperTop: ImageBitmap? = null,
    val wallpaperBottom: ImageBitmap? = null,
    val icons: Map<String, List<app.foldcade.language.ThemeIconAsset>> = emptyMap(),
)

val LocalFoldTheme = staticCompositionLocalOf { FoldPaint(builtInTheme()) }

/** A small control's outline: a pill or a circle at Afterglow's `shape.round` of 0.5, square at 0. */
internal fun ThemeShape.control(): Shape = RoundedCornerShape(percent = (round * 100f).roundToInt())
