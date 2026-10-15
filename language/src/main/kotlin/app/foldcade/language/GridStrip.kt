package app.foldcade.language

/**
 * The grid is one strip, as on the 3DS: tiles fill a column top to bottom, the
 * columns run left to right, and as many of them are on screen as the panel
 * fits, at least [Metrics.columns]. The strip scrolls sideways a column at a
 * time to keep the cursor on screen. It never pages. The cursor can rest on any
 * slot, empty ones too, and walk [trailing] empty columns past the last tile.
 *
 * Rows are the same on every panel, so a tile keeps its row and column wherever
 * the grid is drawn. Only how many columns are on screen changes.
 */
object GridStrip {
    fun column(index: Int): Int = index / Metrics.rows

    fun row(index: Int): Int = index % Metrics.rows

    fun index(column: Int, row: Int): Int = column * Metrics.rows + row

    /** Columns holding at least one of [count] tiles. */
    fun columns(count: Int): Int = (count + Metrics.rows - 1) / Metrics.rows

    /** Empty columns past the last tile's column. Enough to scroll the last tiles to the left edge of the bottom screen. */
    const val trailing = Metrics.columns - 1

    /** Columns the cursor can reach along [count] tiles: theirs, then [trailing] empty ones. None without tiles. */
    fun reachColumns(count: Int): Int = if (count <= 0) 0 else columns(count) + trailing

    /** Slots the cursor can reach along [count] tiles, empty ones included, see [reachColumns]. */
    fun reach(count: Int): Int = index(reachColumns(count), 0)

    /**
     * The furthest the strip scrolls along [count] tiles: the first column of the
     * last screen the cursor can reach, with [shown] columns on screen.
     */
    fun lastFirst(count: Int, shown: Int): Int = (reachColumns(count) - shown).coerceAtLeast(0)

    /**
     * The first column on screen once the cursor is in [column], when [first]
     * was on screen before and [shown] columns fit. It moves only as far as it
     * must, and never past the last screen the cursor can reach.
     */
    fun follow(first: Int, column: Int, count: Int, shown: Int): Int =
        first.coerceIn(column - shown + 1, column).coerceIn(0, lastFirst(count, shown))
}

/** The first column the grid shows for [model]'s focus, with [shown] columns on screen. */
fun shownFirstColumn(model: PickerModel, shown: Int = model.columnsShown): Int =
    GridStrip.follow(model.firstColumn, GridStrip.column(model.focus.cellIndex), model.count, shown)
