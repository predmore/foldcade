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

/** One or two words each. The page is for players, not a form, so nothing here explains itself twice. */
object SettingsCopy {
    const val title = "Settings"
    const val personalization = "Look"
    const val sound = "Sound"
    const val library = "Library"
    const val players = "Saves"
    const val screens = "System"
    const val about = "About"
}

fun settingsTitle(category: SettingsCategory): String = when (category) {
    SettingsCategory.Personalization -> SettingsCopy.personalization
    SettingsCategory.Sound -> SettingsCopy.sound
    SettingsCategory.Library -> SettingsCopy.library
    SettingsCategory.Players -> SettingsCopy.players
    SettingsCategory.Screens -> SettingsCopy.screens
    SettingsCategory.About -> SettingsCopy.about
}

/** Categories with at least one row. Players appears once a player has a save folder setting. */
fun settingsCategories(model: PickerModel): List<SettingsCategory> =
    SettingsCategory.entries.filter { settingsRows(it, model).isNotEmpty() }

fun settingsRows(category: SettingsCategory, model: PickerModel): List<Row> = when (category) {
    SettingsCategory.Personalization -> buildList {
        add(Row.Theme)
        add(Row.IconTheme)
        add(Row.Background)
        add(Row.MotionSpeed)
        if (model.offerButtonLabels) add(Row.ButtonLabels)
    }
    SettingsCategory.Sound -> listOf(Row.Music, Row.MusicTrack, Row.MusicVolume)
    SettingsCategory.Library -> buildList {
        // Only once there is a folder or a server to read.
        if (model.signedIn.isNotEmpty() || model.folders.isNotEmpty()) add(Row.SyncLibrary)
        addAll(libraryRows(model.signedIn, model.folders))
        addAll(listOf(Row.Order, Row.AddNewGames, Row.Artwork, Row.ArtKey, Row.UsageAccess))
    }
    SettingsCategory.Players -> model.playerSaves.map { Row.PlayerSave(it.playerId) }
    SettingsCategory.Screens -> buildList {
        add(Row.Primary)
        if (!model.homeRoleHeld) add(Row.SetAsHome)
        add(Row.DefaultHomeApp)
        // Until Android grants it, so R1 can list notifications.
        if (model.askNoticeAccess) add(Row.NoticeAccess)
        add(Row.AndroidSettings)
    }
    SettingsCategory.About -> listOf(Row.Licenses)
}

/**
 * How a row shows its value on the right. A [Toggle] is a switch that A flips. A [Check]
 * marks a choice that A puts in or takes out, as a game in a collection. A [Choice]
 * reads ‹ value ›, and left and right step it. [Opens] leads somewhere else. [Value] is
 * plain text. Sliders draw their own row, so they show [None] here.
 */
sealed interface RowControl {
    data class Toggle(val on: Boolean) : RowControl
    data class Check(val on: Boolean) : RowControl
    data class Choice(val value: String) : RowControl
    data object Opens : RowControl
    data class Value(val text: String) : RowControl
    data object None : RowControl
}

/** [value] is the row's [rowText] value, for a caller that already has it. */
fun rowControl(row: Row, model: PickerModel, value: String? = rowText(row, model).value): RowControl = when (row) {
    Row.Music -> RowControl.Toggle(model.music.enabled)
    Row.PinApp -> RowControl.Toggle(model.appActions?.favorite == true)
    Row.AddNewGames -> RowControl.Toggle(model.addNewToHome)
    Row.Artwork -> RowControl.Toggle(model.artwork)
    in choiceRows -> RowControl.Choice(value.orEmpty())
    Row.MusicVolume, Row.Brightness -> RowControl.None
    is Row.Collection -> RowControl.Check(model.tileActions?.collections?.any { it.folderId == row.folderId && it.member } == true)
    Row.EditHome, Row.AddToCollection, Row.AddAppTo, is Row.RommConnection,
    Row.AddFolder, Row.AddRomm, Row.SetAsHome, Row.DefaultHomeApp, Row.AndroidSettings, Row.Licenses,
    -> RowControl.Opens
    else -> value?.takeIf { it.isNotEmpty() }?.let(RowControl::Value) ?: RowControl.None
}
