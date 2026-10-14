package app.foldcade.language

import kotlin.math.roundToInt

/** Row padding contracts before a menu needs to scroll. Heights are whole pixels. */
data class MenuFit(val rowHeights: List<Int>, val scrolls: Boolean)

fun menuFit(rows: Int, availableHeight: Int, density: Float, gap: Int = 0): MenuFit {
    if (rows <= 0) return MenuFit(emptyList(), false)
    val minimum = (40f * density).roundToInt()
    val normal = (52f * density).roundToInt()
    val room = (availableHeight - gap * (rows - 1)).coerceAtLeast(0)
    val height = (room / rows).coerceIn(minimum, normal)
    // Share the remaining pixels, so rounding each row cannot overrun a fitted menu.
    val remainder = if (height < normal && room >= rows * minimum) room % rows else 0
    return MenuFit(List(rows) { height + if (it < remainder) 1 else 0 }, rows * minimum > room)
}

/** Keep the focus and a neighboring row inside the viewport, including the glow. */
fun menuScrollTarget(top: Float, bottom: Float, contextTop: Float, contextBottom: Float,
    viewport: Float, current: Float, maximum: Float, reach: Float): Float {
    val (start, end) = when {
        contextBottom - contextTop + reach * 2 <= viewport -> contextTop to contextBottom
        contextBottom > bottom && contextBottom - top + reach * 2 <= viewport -> top to contextBottom
        contextTop < top && bottom - contextTop + reach * 2 <= viewport -> contextTop to bottom
        else -> top to bottom
    }
    return when {
        start - reach < current -> start - reach
        end + reach > current + viewport -> end + reach - viewport
        else -> current
    }.coerceIn(0f, maximum.coerceAtLeast(0f))
}
