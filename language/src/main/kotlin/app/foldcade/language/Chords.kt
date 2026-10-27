package app.foldcade.language

/**
 * A menu's shortcuts. The key that opens a menu, then one more key, runs a row
 * without walking to it: L1 then Start is Settings, Y then Y is Move. The second
 * key means the same thing in every menu: Start is settings, Select is a view
 * (Play activity, or Show on Home for a game Find or All found),
 * The options button moves or edits, and the bottom button takes away. Those stay on
 * Y and X until calibration takes them. A confirms the focused row and B goes back,
 * so neither is ever a chord. Each row shows its key on its right.
 *
 * A row lives in one menu, so its chord is its own. R1's notices keep X for
 * themselves, and no row in R1 takes it.
 */
private fun Row.chord(): Meaning? = when (this) {
    Row.Settings, Row.QuickTile(QuickSetting.Settings) -> Meaning.Menu
    Row.PlayActivity, Row.ShowOnHome -> Meaning.View
    Row.EditHome, Row.MoveTile, Row.ShowHidden -> Meaning.Options
    // Hide has no chord: X takes a tile off its spot, and never hides a game.
    Row.RemoveTile, Row.RemoveDownload, Row.DeleteShortcut -> Meaning.ActivateBottom
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

/** The glyph for a chord's key. Bottom and options follow [actionFaces] after calibration. */
fun chordKey(meaning: Meaning, faces: FaceMap = FaceMap.standard()): PromptKey? = when (meaning) {
    Meaning.Menu -> PromptKey.Start
    Meaning.View -> PromptKey.Select
    Meaning.Options -> actionFaces(faces).optionsGlyph
    Meaning.ActivateBottom -> actionFaces(faces).bottomGlyph
    else -> null
}

/**
 * The key each row shows on its right, or null. Only the key to press now: the menu
 * is already open, so the key that opened it would only read as a second step.
 */
fun rowKeys(rows: List<Row>, faces: FaceMap = FaceMap.standard()): List<PromptKey?> =
    chords(rows).map { it?.let { meaning -> chordKey(meaning, faces) } }
