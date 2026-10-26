package app.foldcade.language

/**
 * The settings page on [screen] with [row] focused, as if reached from the L1 Settings row.
 * A row that lives on a tile's page, as an emulator's save folder does, is focused on that page.
 */
fun PickerModel.onSetting(row: Row, screen: HostScreen = HostScreen.Top): PickerModel {
    val categories = settingsCategories(this)
    categories.forEachIndexed { category, it ->
        val rows = settingsRows(it, this)
        val index = rows.indexOf(row)
        if (index >= 0) return copy(panel = null, settings = SettingsPage(screen, category, index, onRows = true, grid = focus))
        for (tile in rows.filterIsInstance<Row.SettingsTile>()) {
            val inner = settingsInnerRows(tile.page, this).indexOf(row)
            if (inner >= 0) {
                return copy(panel = null, settings = SettingsPage(screen, category, inner, onRows = true, grid = focus, inner = tile.page))
            }
        }
    }
    throw IllegalArgumentException("$row is not on the settings page")
}
