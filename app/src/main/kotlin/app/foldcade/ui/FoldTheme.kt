package app.foldcade.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import app.foldcade.language.Theme
import app.foldcade.language.builtInTheme

/** Paint plus the images a loaded theme zip contributed. */
data class FoldPaint(
    val theme: Theme,
    val wallpaperTop: ImageBitmap? = null,
    val wallpaperBottom: ImageBitmap? = null,
    val marks: Map<String, ImageBitmap> = emptyMap(),
)

val LocalFoldTheme = staticCompositionLocalOf { FoldPaint(builtInTheme()) }
