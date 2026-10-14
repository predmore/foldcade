package app.foldcade.language

/**
 * A menu's shortcuts. The key that opens a menu, then one more key, runs a row
 * without walking to it: L1 then Start is Settings, Y then Y is Move. The second
 * key means the same thing in every menu: Start is settings, Select is a view
 * (Play activity, or Show on Home for a game Find or All found),
 * Y moves or edits, and X takes away. A confirms the focused row and B goes back,
 * so neither is ever a chord. Each row shows its key on its right.
 *
 * A row lives in one menu, so its chord is its own. R1's notices keep Y and X for
 * themselves, and no row in R1 takes either.
 */
private fun Row.chord(): Meaning? = when (this) {
    Row.Settings, Row.QuickTile(QuickSetting.Settings) -> Meaning.Menu
    Row.PlayActivity, Row.ShowOnHome -> Meaning.View
    Row.EditHome, Row.MoveTile, Row.ShowHidden, Row.ShowApp -> Meaning.Options
    Row.RemoveTile, Row.HideApp, Row.RemoveDownload, Row.DeleteShortcut -> Meaning.ActivateBottom
    else -> null
}

/** Each row's chord, or null. When two rows could take a key, the first one has it. */
fun chords(rows: List<Row>): List<Meaning?> {
    val taken = mutableSetOf<Meaning>()
    return rows.map { row -> row.chord()?.takeIf { taken.add(it) } }
}

/** The row [meaning] runs among [rows], or null when that key is not a chord there. */
fun chordIndex(rows: List<Row>, meaning: Meaning): Int? =
    chords(rows).indexOf(meaning).takeIf { it >= 0 }

/** The glyph for a chord's key. */
fun chordKey(meaning: Meaning): PromptKey? = when (meaning) {
    Meaning.Menu -> PromptKey.Start
    Meaning.View -> PromptKey.Select
    Meaning.Options -> PromptKey.FaceY
    Meaning.ActivateBottom -> PromptKey.FaceX
    else -> null
}

/**
 * The key each row shows on its right, or null. Only the key to press now: the menu
 * is already open, so the key that opened it would only read as a second step.
 */
fun rowKeys(rows: List<Row>): List<PromptKey?> = chords(rows).map { it?.let(::chordKey) }
