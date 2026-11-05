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
 * coil-core 3.3.0). Where it put the square is kept per address as a [TileCut], so the
 * launch bloom starts from the same picture and keeps its title as it widens.
 */
internal class TileCrop(private val uri: String) : Transformation() {
    override val cacheKey: String = "foldcade.tileCrop.2"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = input.width
        val height = input.height
        if (width == height || width <= 0 || height <= 0) return input
        val side = min(width, height)
        // A wide image keeps its middle; only a tall box has a title to look for.
        val share = if (height > width) {
            val detail = rowDetail(input)
            val start = titleSafeStart(detail, DETAIL_COLUMNS)
            TileCrops.record(uri, TileCut(detail.size, start))
            start.toFloat() / (detail.size - DETAIL_COLUMNS).coerceAtLeast(1)
        } else 0.5f
        val left = (width - side) / 2
        val top = ((height - side) * share).roundToInt().coerceIn(0, height - side)
        return Bitmap.createBitmap(input, left, top, side, side)
    }
}

internal object TileCrops {
    private val cuts = ConcurrentHashMap<String, TileCut>()

    fun record(uri: String, cut: TileCut) {
        cuts[uri] = cut
    }

    /** How [uri]'s tall box was cut for its tile. Null before its tile decoded, or for art that was not tall. */
    fun cut(uri: String?): TileCut? = uri?.let(cuts::get)
}

/**
 * A tall box's tile cut: the box is [rows] tall read [DETAIL_COLUMNS] wide, and its square of
 * [DETAIL_COLUMNS] rows [start]s at that row.
 */
internal class TileCut(val rows: Int, val start: Int) {
    /**
     * Where a crop [window] rows tall sits inside the tile's square: as a share of the whole box,
     * for the sharp cover, and of the square, for the tile's own copy. Both show the same rows.
     * It keeps to the end of the square the tile leaned to, where its title is: a square cut low
     * in the box kept a title at the bottom, and one cut high or centred, a title at the top
     * (checked against the 37 RomM covers on the Thor; the busiest band inside a narrow crop
     * found the art, not the title, on Mario Kart DS, Metroid and Gain Ground).
     */
    fun band(window: Float): Pair<Float, Float> {
        // In fractional rows, so the kept edge moves smoothly as the frame grows.
        val square = minOf(DETAIL_COLUMNS, rows - start).toFloat()
        val shown = window.coerceIn(1f, square)
        val low = start * 2 > rows - DETAIL_COLUMNS
        val inner = if (low) square - shown else 0f
        val box = (rows - shown).let { spare -> if (spare <= 0f) 0.5f else (start + inner) / spare }
        val own = (square - shown).let { spare -> if (spare <= 0f) 0.5f else inner / spare }
        return box to own
    }

    /** Rows of a box this wide that a crop [aspect] (height over width) shows. */
    fun rowsFor(aspect: Float): Float = DETAIL_COLUMNS * aspect
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
    return richestStart(detail, window).toFloat() / spare
}

/** The row the busiest run of [window] rows in [detail] starts at. A tie keeps the one nearest the middle. */
internal fun richestStart(detail: IntArray, window: Int): Int {
    val spare = detail.size - window
    if (spare <= 0) return 0
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
    return best
}

/**
 * Finish a detail band that the busiest square cuts through. In particular Metroid's
 * brightest Samus rows outweighed the last few rows of its title, leaving half of METROID
 * on the edge. Look only a small margin past the chosen edge for two quiet rows; without
 * a quiet seam nearby keep the original square, rather than chase detail across the box.
 */
internal fun titleSafeStart(detail: IntArray, window: Int): Int {
    val start = richestStart(detail, window)
    val spare = detail.size - window
    if (spare <= 0) return start
    // Check both edges: the illustration can pull the richest square upward even
    // when its bottom edge bisects a title (the Thor's tile-size Metroid decode).
    fun finishBand(rows: IntArray, cut: Int): Int? {
        val quiet = (cut until cut + window).sumOf { rows[it].toLong() } / window / 3
        val edge = cut + window
        if (rows[edge - 1] <= quiet) return null
        val before = (maxOf(cut + 1, edge - window / 3) until edge - 1).lastOrNull {
            rows[it - 1] <= quiet && rows[it] <= quiet
        } ?: return null
        for (end in edge + 2..minOf(rows.size, edge + window / 4)) {
            if (rows[end - 2] <= quiet && rows[end - 1] <= quiet) {
                // Only a short, isolated band, not a long run of illustration.
                return (end - window).takeIf { end - (before + 1) <= window / 3 }
            }
        }
        return null
    }
    val lower = finishBand(detail, start)
    val upper = finishBand(detail.reversedArray(), spare - start)?.let { spare - it }
    return listOfNotNull(lower, upper).minByOrNull { abs(it - start) } ?: start
}
