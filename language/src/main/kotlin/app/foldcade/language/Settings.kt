package app.foldcade.language

/**
 * The full-screen settings page. Categories on the left, that category's rows on the
 * right, the way Cocoon lays out its settings. The L1 menu opens it and stays short.
 */
data class SettingsPage(
    val screen: HostScreen,
    val category: Int = 0,
    val row: Int = 0,
    /** True while the D-pad moves through the rows. False while it moves through the categories. */
    val onRows: Boolean = false,
    /** Grid focus to restore when the page closes. */
    val grid: GridFocus = GridFocus(),
)

enum class SettingsCategory {
    Personalization,
    Sound,
    Library,
    Players,
    Screens,
    About,
}

object SettingsCopy {
    const val title = "Settings"
    const val personalization = "Personalization"
    const val personalizationDetail = "Theme, background, and motion"
    const val sound = "Sound"
    const val soundDetail = "Home music"
    const val library = "Library & data"
    const val libraryDetail = "Sources, order, and play time"
    const val players = "Players"
    const val playersDetail = "Save folders"
    const val screens = "Screens & system"
    const val screensDetail = "Panels, Home, and Android"
    const val about = "About"
    const val aboutDetail = "Licenses"
}

fun settingsTitle(category: SettingsCategory): String = when (category) {
    SettingsCategory.Personalization -> SettingsCopy.personalization
    SettingsCategory.Sound -> SettingsCopy.sound
    SettingsCategory.Library -> SettingsCopy.library
    SettingsCategory.Players -> SettingsCopy.players
    SettingsCategory.Screens -> SettingsCopy.screens
    SettingsCategory.About -> SettingsCopy.about
}

fun settingsDetail(category: SettingsCategory): String = when (category) {
    SettingsCategory.Personalization -> SettingsCopy.personalizationDetail
    SettingsCategory.Sound -> SettingsCopy.soundDetail
    SettingsCategory.Library -> SettingsCopy.libraryDetail
    SettingsCategory.Players -> SettingsCopy.playersDetail
    SettingsCategory.Screens -> SettingsCopy.screensDetail
    SettingsCategory.About -> SettingsCopy.aboutDetail
}

/** Categories with at least one row. Players appears once a player has a save folder setting. */
fun settingsCategories(model: PickerModel): List<SettingsCategory> =
    SettingsCategory.entries.filter { settingsRows(it, model).isNotEmpty() }

fun settingsRows(category: SettingsCategory, model: PickerModel): List<Row> = when (category) {
    SettingsCategory.Personalization -> buildList {
        add(Row.Theme)
        add(Row.Background)
        add(Row.MotionSpeed)
        if (model.offerButtonLabels) add(Row.ButtonLabels)
    }
    SettingsCategory.Sound -> listOf(Row.Music, Row.MusicTrack, Row.MusicVolume)
    SettingsCategory.Library -> libraryRows(model.signedIn, model.folders) +
        listOf(Row.Order, Row.AddNewGames, Row.Artwork, Row.UsageAccess, Row.MoonlightSource)
    SettingsCategory.Players -> model.playerSaves.map { Row.PlayerSave(it.playerId) }
    SettingsCategory.Screens -> buildList {
        add(Row.Primary)
        if (!model.homeRoleHeld) add(Row.SetAsHome)
        add(Row.DefaultHomeApp)
        add(Row.AndroidSettings)
    }
    SettingsCategory.About -> listOf(Row.Licenses)
}
