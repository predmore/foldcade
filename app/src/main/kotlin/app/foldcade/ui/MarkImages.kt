package app.foldcade.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Hero-sized vectors are rasterized once off main, rather than on every crossfade. */
internal class MarkImages(private val context: Context) {
    private val gate = Mutex()
    private val images = object : LruCache<Int, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.allocationByteCount
    }

    fun cached(drawable: Int): ImageBitmap? = images.get(drawable)?.asImageBitmap()

    suspend fun load(drawable: Int): ImageBitmap = withContext(Dispatchers.Default) {
        gate.withLock {
            val found = images.get(drawable)
            if (found != null) return@withLock found.asImageBitmap()
            val vector = requireNotNull(context.getDrawable(drawable)).mutate()
            val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
            vector.setBounds(0, 0, bitmap.width, bitmap.height)
            vector.draw(Canvas(bitmap))
            bitmap.prepareToDraw()
            images.put(drawable, bitmap)
            bitmap.asImageBitmap()
        }
    }
}
