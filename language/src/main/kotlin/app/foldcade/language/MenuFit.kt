package app.foldcade.language

/** Row padding contracts before a menu needs to scroll. Heights are in dp. */
data class MenuFit(val rowHeight: Float, val scrolls: Boolean)

fun menuFit(rows: Int, availableHeight: Float, gap: Float = 0f): MenuFit {
    if (rows <= 0) return MenuFit(52f, false)
    val room = (availableHeight - gap * (rows - 1)).coerceAtLeast(0f)
    val height = (room / rows).coerceIn(40f, 52f)
    return MenuFit(height, rows * height > room + 0.01f)
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
