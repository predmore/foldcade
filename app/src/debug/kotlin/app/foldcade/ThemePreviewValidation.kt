package app.foldcade

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import app.foldcade.ui.ThemeVector
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Stock vectors compare against the Compose painters used by the original launcher. */
internal suspend fun validatePreviewIcons(context: Context, pack: PackagedTheme, references: Map<String, Painter>) = withContext(Dispatchers.Default) {
    val lines = mutableListOf<String>()
    for ((slot, icon) in pack.resources.icons) {
        try {
            val image = if (icon.file.endsWith(".xml")) ThemeVector.rasterize(icon.bytes, 256)
                else requireNotNull(BitmapFactory.decodeByteArray(icon.bytes, 0, icon.bytes.size)) { "Cannot decode image" }
            val painter = references[slot]
            if (painter != null) {
                val expected = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr,
                    androidx.compose.ui.graphics.Canvas(expected.asImageBitmap()), Size(image.width.toFloat(), image.height.toFloat())) {
                    with(painter) { draw(size) }
                }
                val actual = IntArray(image.width * image.height); val reference = IntArray(actual.size)
                image.getPixels(actual, 0, image.width, 0, 0, image.width, image.height)
                expected.getPixels(reference, 0, image.width, 0, 0, image.width, image.height)
                var difference = 0L
                val shifts = intArrayOf(0, 8, 16)
                for (index in actual.indices) {
                    val actualAlpha = actual[index] ushr 24; val expectedAlpha = reference[index] ushr 24
                    difference += abs(actualAlpha - expectedAlpha)
                    for (shift in shifts) difference += abs(
                        ((actual[index] ushr shift) and 255) * actualAlpha / 255 -
                            ((reference[index] ushr shift) and 255) * expectedAlpha / 255)
                }
                val mean = difference.toDouble() / (actual.size * 4)
                lines += "$slot: decoded, mean premultiplied RGBA difference %.4f/255".format(mean)
                expected.recycle()
            } else lines += "$slot: decoded ${image.width}×${image.height}"
            image.recycle()
        } catch (error: Exception) { lines += "$slot: ERROR ${icon.file}: ${error.message}" }
    }
    File(context.filesDir, "theme-preview-icons.txt").writeText(lines.joinToString("\n"))
    lines.forEach { Log.i("FoldcadeThemePreview", it) }
}
