package app.foldcade.ui

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A tile's square out of a tall box. A centred crop cut the title off covers that print
 * it at the bottom ("BRAVEL", "GOLDE") and at the top ("ANTASY LIF") alike (review-B 60-F2),
 * so the square goes where the box has the most detail: a title's hard-edged letters are the
 * busiest band on nearly every cover, and a plain sky or gradient the quietest.
 *
 * Runs on Coil's decode thread, once per decode (`coil3.transform.Transformation`,
 * coil-core 3.3.0). Where it put the square is kept per address for [TileCrops.share], so
 * the launch bloom starts from the same picture the tile showed.
 */
internal class TileCrop(private val uri: String) : Transformation() {
    override val cacheKey: String = "foldcade.tileCrop.1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = input.width
        val height = input.height
        if (width == height || width <= 0 || height <= 0) return input
        val side = min(width, height)
        // A wide image keeps its middle; only a tall box has a title to look for.
        val share = if (height > width) richestShare(rowDetail(input)) else 0.5f
        TileCrops.record(uri, share)
        val left = (width - side) / 2
        val top = ((height - side) * share).roundToInt().coerceIn(0, height - side)
        return Bitmap.createBitmap(input, left, top, side, side)
    }
}

internal object TileCrops {
    private val shares = ConcurrentHashMap<String, Float>()

    fun record(uri: String, share: Float) {
        shares[uri] = share
    }

    /** Where [uri]'s tile square sat in the box, from its top: 0 top, 0.5 centred, 1 bottom. */
    fun share(uri: String?): Float = uri?.let(shares::get) ?: 0.5f
}

/** How many columns the box is read at. Enough for letters to show as edges, cheap on a 384 px tile. */
private const val DETAIL_COLUMNS = 48

/**
 * Each row's detail in [bitmap] read [DETAIL_COLUMNS] wide: the summed brightness steps to its
 * left and above. Rows are as tall as columns are wide, so a square is [DETAIL_COLUMNS] rows.
 */
private fun rowDetail(bitmap: Bitmap): IntArray {
    val rows = (bitmap.height.toFloat() * DETAIL_COLUMNS / bitmap.width).roundToInt().coerceAtLeast(DETAIL_COLUMNS)
    val small = Bitmap.createScaledBitmap(bitmap, DETAIL_COLUMNS, rows, true)
    val pixels = IntArray(DETAIL_COLUMNS * rows)
    small.getPixels(pixels, 0, DETAIL_COLUMNS, 0, 0, DETAIL_COLUMNS, rows)
    if (small !== bitmap) small.recycle()
    val light = IntArray(pixels.size) { i ->
        val c = pixels[i]
        (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
    }
    return IntArray(rows) { y ->
        var sum = 0
        for (x in 0 until DETAIL_COLUMNS) {
            val at = y * DETAIL_COLUMNS + x
            if (x > 0) sum += abs(light[at] - light[at - 1])
            if (y > 0) sum += abs(light[at] - light[at - DETAIL_COLUMNS])
        }
        sum
    }
}

/**
 * Where the square window of [DETAIL_COLUMNS] rows holding the most [detail] starts, as a
 * share of the rows it could start at: 0 top, 1 bottom. A tie keeps the one nearest the middle.
 */
internal fun richestShare(detail: IntArray, window: Int = DETAIL_COLUMNS): Float {
    val spare = detail.size - window
    if (spare <= 0) return 0.5f
    var sum = 0L
    for (y in 0 until window) sum += detail[y]
    var best = 0
    var bestSum = sum
    for (start in 1..spare) {
        sum += detail[start + window - 1] - detail[start - 1]
        val nearer = abs(start * 2 - spare) < abs(best * 2 - spare)
        if (sum > bestSum || (sum == bestSum && nearer)) {
            best = start
            bestSum = sum
        }
    }
    return best.toFloat() / spare
}
