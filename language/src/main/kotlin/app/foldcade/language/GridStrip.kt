package app.foldcade.language

/**
 * The grid is one strip, as on the 3DS: tiles fill a column top to bottom, the
 * columns run left to right, and [Metrics.columns] of them are on screen. The
 * strip scrolls sideways a column at a time to keep the cursor on screen. It
 * never pages.
 */
object GridStrip {
    fun column(index: Int): Int = index / Metrics.rows

    fun row(index: Int): Int = index % Metrics.rows

    fun index(column: Int, row: Int): Int = column * Metrics.rows + row

    /** Columns holding at least one of [count] tiles. */
    fun columns(count: Int): Int = (count + Metrics.rows - 1) / Metrics.rows

    /**
     * The first column on screen once the cursor is in [column], when [first]
     * was on screen before. It moves only as far as it must, and never past
     * the last full screen of the strip.
     */
    fun follow(first: Int, column: Int, count: Int): Int {
        val last = (columns(count) - Metrics.columns).coerceAtLeast(0)
        return first.coerceIn(column - Metrics.columns + 1, column).coerceIn(0, last)
    }
}

/** The first column the grid shows for [model]'s focus. */
fun shownFirstColumn(model: PickerModel): Int =
    GridStrip.follow(model.firstColumn, GridStrip.column(model.focus.cellIndex), model.count)
