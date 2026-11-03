package app.foldcade.language

/**
 * How Y's menu beside its tile is sized, in dp. The card ([TileMenuSize.widthDp] wide) sits
 * [TileMenuSize.marginDp] from the screen's top and stops above the grid's hint row, so the
 * menu's keys stay readable. The round tiles and rows inside are the island's: tiles of
 * [TileMenuSize.tileDp] with their word under them, rows at least [TileMenuSize.rowMinDp] tall.
 *
 * [tileMenuFit] adds those up for a menu, so a test can hold the longest real menus to the
 * Thor's screens at 1.0x and 1.3x font. The card measures its rows itself; a menu that does not
 * fit scrolls, as an island's does.
 */
object TileMenuSize {
    /** The card's width, or the screen's less its margins when that is narrower. */
    const val widthDp = 340f

    /** Between the card and the screen's edges, and between the card and its tile. */
    const val marginDp = 8f
    const val besideDp = 12f

    /** The hint row the card stops above: its glyph, or its words' line when that is taller, and the gap over it. */
    const val hintLineDp = 24f
    const val hintGapDp = 6f

    /** Inside the card: the focus ring's reach on Afterglow, then a little more. */
    const val ringReachDp = 11.8f
    const val padDp = 4f

    const val tileDp = 56f
    const val tileLabelGapDp = 8f

    /** One line of a tile's word, 14sp at 1.0x font. */
    const val tileLabelLineDp = 19.5f

    /** The tiles' block pads 12px above and below on the Thor (369 dpi), and 16px between lines. */
    const val tileBlockPadDp = 5.2f
    const val tileLineGapDp = 7f

    /** Rows are 52dp, and give up to 40dp each before the card scrolls. */
    const val rowMinDp = 40f

    /** 6px between rows and blocks on the Thor. */
    const val rowGapDp = 2.6f
}

/** What a menu needs in the card and what the screen leaves it, in dp. */
data class TileMenuFit(val neededDp: Float, val roomDp: Float) {
    val fits: Boolean get() = neededDp <= roomDp
}

/**
 * [rows] in the card on a screen [screenHeightDp] tall whose grid insets [insetDp] at its
 * edges, at [fontScale]. The tiles' words scale with the font; the tiles and the rows' floor do not.
 */
fun tileMenuFit(rows: List<Row>, screenHeightDp: Float, insetDp: Float, fontScale: Float = 1f): TileMenuFit {
    val size = TileMenuSize
    val block = tileBlock(rows)
    val tiles = rows.slice(block)
    val lines = tiles.chunked(quickTileColumns)
    val tilesDp = if (lines.isEmpty()) 0f else lines.sumOf { line ->
        val words = line.maxOf { tileLabelLines(it) }
        (size.tileDp + size.tileLabelGapDp + size.tileLabelLineDp * fontScale * words).toDouble()
    }.toFloat() + size.tileLineGapDp * (lines.size - 1) + size.tileBlockPadDp * 2
    val rowCount = rows.size - tiles.size
    val blocks = rowCount + if (tiles.isEmpty()) 0 else 1
    val needed = (size.ringReachDp + size.padDp) * 2 + tilesDp + rowCount * size.rowMinDp +
        size.rowGapDp * (blocks - 1).coerceAtLeast(0)
    val room = screenHeightDp - size.marginDp - (insetDp + maxOf(size.hintLineDp, size.tileLabelLineDp * fontScale) + size.hintGapDp)
    return TileMenuFit(needed, room)
}

/** Lines a tile's word takes: New collection takes two, every other word one. */
fun tileLabelLines(row: Row): Int = if (row == Row.NewCollection) 2 else 1
