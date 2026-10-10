package app.foldcade.language

/** The settings page on [screen] with [row] focused, as if reached from the L1 Settings row. */
fun PickerModel.onSetting(row: Row, screen: HostScreen = HostScreen.Top): PickerModel {
    val categories = settingsCategories(this)
    val category = categories.indexOfFirst { row in settingsRows(it, this) }
    require(category >= 0) { "$row is not on the settings page" }
    val index = settingsRows(categories[category], this).indexOf(row)
    return copy(panel = null, settings = SettingsPage(screen, category, index, onRows = true, grid = focus))
}
