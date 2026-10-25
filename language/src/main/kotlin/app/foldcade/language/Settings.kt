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
    const val players = "Emulators"
    const val screens = "System"
    const val about = "About"
}

/** About's version row, and the one line it copies for a bug report. */
object AboutCopy {
    const val version = "Version"
    const val copied = "Copied"

    /** "0.1.2 (230)": the name players see, and the build that tells two of them apart. */
    fun version(name: String, code: Long): String = "$name ($code)"

    /** The maker is left out when the model already starts with it, as "AYN Thor" might. */
    fun device(manufacturer: String, model: String): String =
        if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model".trim()

    fun debugLine(version: String, android: String, device: String, build: String): String =
        listOf("Foldcade $version", "Android $android", device, build).filter { it.isNotBlank() }.joinToString(" · ")
}

fun settingsTitle(category: SettingsCategory): String = when (category) {
    SettingsCategory.Personalization -> SettingsCopy.personalization
    SettingsCategory.Sound -> SettingsCopy.sound
    SettingsCategory.Library -> SettingsCopy.library
    SettingsCategory.Players -> SettingsCopy.players
    SettingsCategory.Screens -> SettingsCopy.screens
    SettingsCategory.About -> SettingsCopy.about
}

/** Categories with at least one row. Players appears once a player has a save folder setting, RetroArch is installed, or Eden is installed and a RomM server is signed in. */
fun settingsCategories(model: PickerModel): List<SettingsCategory> =
    SettingsCategory.entries.filter { settingsRows(it, model).isNotEmpty() }

fun settingsRows(category: SettingsCategory, model: PickerModel): List<Row> = when (category) {
    SettingsCategory.Personalization -> buildList {
        add(Row.Theme)
        add(Row.IconTheme)
        add(Row.Background)
        add(Row.Themes)
        add(Row.MotionSpeed)
        // Calibration can be corrected even after the one-time setup hint is settled.
        add(Row.ButtonLabels)
    }
    SettingsCategory.Sound -> listOf(Row.Music, Row.MusicTrack, Row.MusicVolume)
    SettingsCategory.Library -> buildList {
        // Only once there is a folder or a server to read.
        if (model.signedIn.isNotEmpty() || model.folders.isNotEmpty()) add(Row.SyncLibrary)
        addAll(libraryRows(model.signedIn, model.folders))
        addAll(model.streamHosts.map { Row.StreamHost(it.key) })
        addAll(listOf(Row.Order, Row.AddNewGames, Row.Artwork, Row.ArtKey, Row.UsageAccess))
    }
    SettingsCategory.Players -> buildList {
        if (model.retroArch != null) add(Row.RetroArchCores)
        if (model.eden != null && model.signedIn.any { it.pluginId == "romm" || it.pluginId.startsWith("romm#") }) {
            add(Row.EdenFolder)
        }
        model.playerSaves.forEach { setting ->
            add(Row.PlayerSave(setting.playerId))
            if (setting.hostedTitle != null) add(Row.HostedSaves(setting.playerId))
            if (setting.saveSyncing || setting.saveSyncSteps != null) add(Row.SaveSync(setting.playerId))
            if (setting.setupSteps != null) add(Row.PlayerSetup(setting.playerId))
        }
    }
    SettingsCategory.Screens -> buildList {
        add(Row.Primary)
        if (!model.homeRoleHeld) add(Row.SetAsHome)
        add(Row.DefaultHomeApp)
        // Until Android grants it, so R1 can list notifications.
        if (model.askNoticeAccess) add(Row.NoticeAccess)
        add(Row.AndroidSettings)
    }
    SettingsCategory.About -> listOf(Row.Version, Row.Licenses)
}

/** The row the D-pad is on in the settings page, or null while the categories have it or the page is shut. */
fun settingsFocus(model: PickerModel): Row? {
    val page = model.settings?.takeIf { it.onRows } ?: return null
    val category = settingsCategories(model).getOrNull(page.category) ?: return null
    return settingsRows(category, model).getOrNull(page.row)
}

/** Version reads Copied only until the focus leaves it. */
fun PickerModel.versionCopySettled(): PickerModel =
    if (versionCopied && settingsFocus(this) != Row.Version) copy(versionCopied = false) else this

/**
 * How a row shows its value on the right. A [Toggle] is a switch that A flips. A [Check]
 * marks a choice that A puts in or takes out, as a game in a collection. A [Choice]
 * reads ‹ value ›, and left and right step it. [Opens] leads somewhere else. [Value] is
 * plain text. Sliders draw their own row, so they show [None] here.
 */
sealed interface RowControl {
    data class Toggle(val on: Boolean) : RowControl
    data class Check(val on: Boolean, val some: Boolean = false) : RowControl
    data class Choice(val value: String) : RowControl
    data object Opens : RowControl
    data class Value(val text: String, val attention: Boolean = false) : RowControl
    data object None : RowControl
}

/** [value] is the row's [rowText] value, for a caller that already has it. */
fun rowControl(row: Row, model: PickerModel, value: String? = rowText(row, model).value): RowControl = when (row) {
    Row.Music -> RowControl.Toggle(model.music.enabled)
    Row.AddNewGames -> RowControl.Toggle(model.addNewToHome)
    Row.RecentOnHome -> RowControl.Toggle(model.recentOnHome)
    Row.Artwork -> RowControl.Toggle(model.artwork)
    is Row.StreamHost -> RowControl.Toggle(model.streamHosts.any { it.key == row.key && it.paired })
    in choiceRows -> RowControl.Choice(value.orEmpty())
    Row.MusicVolume, Row.Brightness -> RowControl.None
    is Row.Collection -> model.tileActions?.collections?.firstOrNull { it.folderId == row.folderId }.let { RowControl.Check(it?.member == true, it?.some == true) }
    Row.EditHome, Row.AddToCollection, is Row.RommConnection,
    Row.AddFolder, Row.AddRomm, Row.SetAsHome, Row.DefaultHomeApp, Row.AndroidSettings, Row.Licenses, Row.Themes,
    -> RowControl.Opens
    else -> value?.takeIf { it.isNotEmpty() }?.let {
        RowControl.Value(it, attention = it == Copy.notSyncing &&
            (row is Row.PlayerSave || row is Row.HostedSaves || row is Row.SaveSync || row is Row.PlayerSetup))
    } ?: RowControl.None
}
