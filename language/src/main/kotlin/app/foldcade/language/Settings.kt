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
    /**
     * A page inside the category's card: one emulator, one library source, or Add. [row]
     * then counts that page's rows. Null shows the category's own tiles and rows.
     */
    val inner: SettingsInner? = null,
)

/** A page one step inside a Settings category. A tile on the category's shelf opens it, and B returns to that tile. */
sealed interface SettingsInner {
    /** One emulator's rows. [key] is its [EmulatorShelfEntry.key]. */
    data class Emulator(val key: String) : SettingsInner

    /** One folder the library reads, by its tree [uri]. */
    data class Folder(val uri: String) : SettingsInner

    /** One of Moonlight's computers, by its [StreamHostSetting.key]. */
    data class Host(val key: String) : SettingsInner

    /** A signed-in library other than RomM, which has its own connection actions. */
    data class Backend(val pluginId: String) : SettingsInner

    /** Ways to add a library: a folder, and a RomM server while none is saved. */
    data object Add : SettingsInner
}

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
    const val findSaves = "Find saves"
    const val accessAllowed = "Allowed"
    const val accessAsk = "Ask"
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

/** Categories with at least one row. Emulators appears once an emulator is installed whose saves, cores, or keys Foldcade can set up. */
fun settingsCategories(model: PickerModel): List<SettingsCategory> =
    SettingsCategory.entries.filter { settingsRows(it, model).isNotEmpty() }

/** Opens the normal Settings page at a category, keeping the grid to return to. */
fun PickerModel.openSettings(
    screen: HostScreen,
    category: SettingsCategory = SettingsCategory.Personalization,
    grid: GridFocus = focus,
): PickerModel = copy(
    panel = null,
    settings = SettingsPage(screen, category = settingsCategories(this).indexOf(category).coerceAtLeast(0), grid = grid),
)

/**
 * A category's tiles, then its rows. Your things are tiles (each emulator, each library
 * source) and settings are rows, so no category needs to scroll on the Thor's top screen.
 */
fun settingsRows(category: SettingsCategory, model: PickerModel): List<Row> = when (category) {
    SettingsCategory.Personalization -> buildList {
        // Left and right step the pack; A opens every pack, Add a theme, and what was found in each.
        add(Row.Theme)
        add(Row.IconTheme)
        add(Row.Background)
        add(Row.MotionSpeed)
        // Calibration can be corrected even after the one-time setup hint is settled.
        add(Row.ButtonLabels)
    }
    SettingsCategory.Sound -> listOf(Row.Music, Row.MusicTrack, Row.MusicVolume)
    SettingsCategory.Library -> buildList {
        addAll(librarySources(model))
        // Only once there is a folder or a server to read.
        if (model.signedIn.isNotEmpty() || model.folders.isNotEmpty()) add(Row.SyncLibrary)
        addAll(listOf(Row.Order, Row.Artwork, Row.ArtKey))
    }
    SettingsCategory.Players -> buildList {
        val shelf = emulatorShelf(model)
        addAll(shelf.map { Row.SettingsTile(SettingsInner.Emulator(it.key)) })
        if (shelf.isNotEmpty()) {
            add(Row.SaveSetup)
            add(Row.FindSaves)
        }
    }
    SettingsCategory.Screens -> buildList {
        add(Row.Primary)
        if (!model.homeRoleHeld) add(Row.SetAsHome)
        add(Row.DefaultHomeApp)
        // Android's permissions sit together: usage access for play time, then notification access.
        add(Row.UsageAccess)
        // Until Android grants it, so R1 can list notifications.
        if (model.askNoticeAccess) add(Row.NoticeAccess)
        add(Row.AndroidSettings)
    }
    SettingsCategory.About -> listOf(Row.Version, Row.Licenses)
}

/**
 * Settings › Library's tiles: RomM, any other signed-in library, each folder, each of
 * Moonlight's computers, then Add. Every source feeds the one home grid, so a tile only
 * opens what can be done to that source. None of them switches what the grid shows.
 */
fun librarySources(model: PickerModel): List<Row> {
    // A saved address keeps its tile while its token needs recovery.
    val connections = if (model.rommSaved && model.signedIn.none { it.pluginId == "romm" }) {
        listOf(SignedInBackend("romm", "RomM")) + model.signedIn
    } else model.signedIn
    return connections.map {
        if (it.pluginId == "romm") Row.RommConnection(it.pluginId, it.label)
        else Row.SettingsTile(SettingsInner.Backend(it.pluginId))
    } + model.folders.map { Row.SettingsTile(SettingsInner.Folder(it.uri)) } +
        model.streamHosts.map { Row.SettingsTile(SettingsInner.Host(it.key)) } +
        Row.SettingsTile(SettingsInner.Add)
}

/** True for a row Settings draws as a tile on a category's shelf, in lines of [quickTileColumns]. */
fun Row.settingsTile(): Boolean = this is Row.SettingsTile || this is Row.RommConnection

/**
 * One emulator on Settings › Emulators: its tile, and the rows its page lists. [packageName]
 * is the installed package whose art the tile wears.
 */
data class EmulatorShelfEntry(
    val key: String,
    val label: String,
    val packageName: String?,
    val rows: List<Row>,
)

/** The keys of RetroArch's and Eden's entries when no player with a save folder stands for them. */
const val RETROARCH_SHELF_KEY = "retroarch"
const val EDEN_SHELF_KEY = "eden"

/**
 * One entry per emulator Foldcade knows is installed, A to Z. Each holds only its own rows:
 * its save folder, the folder Foldcade hosts, its memory card, and one-time setup, then
 * RetroArch's cores or Eden's keys for the player that is RetroArch or Eden. RetroArch or
 * Eden without a save-folder player still gets an entry for that row alone.
 */
fun emulatorShelf(model: PickerModel): List<EmulatorShelfEntry> {
    val players = model.playerSaves.filter { it.installed }
    val retroArch = model.retroArch
    // Eden's keys come from RomM, so its row waits for a RomM server.
    val eden = model.eden?.takeIf { _ -> model.signedIn.any { it.pluginId == "romm" } }
    val retroArchOwner = retroArch?.packageName?.let { name -> players.firstOrNull { name in it.packages } }
    val edenOwner = eden?.packageName?.let { name -> players.firstOrNull { name in it.packages } }
    val entries = players.map { setting ->
        EmulatorShelfEntry(
            key = setting.playerId,
            label = setting.label,
            packageName = setting.packages.firstOrNull(),
            rows = buildList {
                add(Row.PlayerSave(setting.playerId))
                if (setting.hostedTitle != null) add(Row.HostedSaves(setting.playerId))
                if (setting.saveSyncing || setting.saveSyncSteps != null) add(Row.SaveSync(setting.playerId))
                if (setting.setupSteps != null) add(Row.PlayerSetup(setting.playerId))
                if (setting === retroArchOwner) add(Row.RetroArchCores)
                if (setting === edenOwner) add(Row.EdenFolder)
            },
        )
    }
    val loose = listOfNotNull(
        retroArch?.takeIf { retroArchOwner == null }?.let {
            EmulatorShelfEntry(RETROARCH_SHELF_KEY, Copy.retroArch, it.packageName, listOf(Row.RetroArchCores))
        },
        eden?.takeIf { edenOwner == null }?.let {
            EmulatorShelfEntry(EDEN_SHELF_KEY, Copy.eden, it.packageName, listOfNotNull(Row.EdenFolder, Row.EdenUser.takeIf { _ -> it.fromRomm && it.setupSteps != null }))
        },
    )
    return (entries + loose).sortedBy { it.label.lowercase() }
}

/** The rows [inner] lists, or none once what it was about has gone. */
fun settingsInnerRows(inner: SettingsInner, model: PickerModel): List<Row> = when (inner) {
    is SettingsInner.Emulator -> emulatorShelf(model).firstOrNull { it.key == inner.key }?.rows.orEmpty()
    is SettingsInner.Folder -> model.folders.filter { it.uri == inner.uri }.take(1).map { Row.ForgetFolder(it.uri, it.label) }
    is SettingsInner.Host -> model.streamHosts.filter { it.key == inner.key }.take(1).map { Row.StreamHost(it.key) }
    is SettingsInner.Backend -> model.signedIn.filter { it.pluginId == inner.pluginId }.take(1).map { Row.SignOut(it.pluginId, it.label) }
    // One RomM server at a time: once one is saved, its own tile edits it or signs out.
    SettingsInner.Add -> listOfNotNull(Row.AddFolder, Row.AddRomm.takeIf { !model.rommSaved && model.signedIn.none { it.pluginId == "romm" } })
}

/** What an inner page, and the tile that opens it, is called. */
fun settingsInnerTitle(inner: SettingsInner, model: PickerModel): String = when (inner) {
    is SettingsInner.Emulator -> emulatorShelf(model).firstOrNull { it.key == inner.key }?.label ?: inner.key
    is SettingsInner.Folder -> model.folders.firstOrNull { it.uri == inner.uri }?.label.orEmpty()
    is SettingsInner.Host -> model.streamHosts.firstOrNull { it.key == inner.key }?.name.orEmpty()
    is SettingsInner.Backend -> model.signedIn.firstOrNull { it.pluginId == inner.pluginId }?.label.orEmpty()
    SettingsInner.Add -> Copy.add
}

/**
 * The page as it can be shown now, and the rows its card lists: the inner page's, or the
 * category's. An inner page whose subject has gone, as a folder just removed, gives way
 * to the category's shelf.
 */
fun settingsShown(page: SettingsPage, model: PickerModel): Pair<SettingsPage, List<Row>> {
    val categories = settingsCategories(model)
    if (categories.isEmpty()) return page.copy(inner = null) to emptyList()
    val category = categories[page.category.coerceIn(0, categories.lastIndex)]
    page.inner?.let { inner ->
        val rows = settingsInnerRows(inner, model)
        if (rows.isNotEmpty()) return page to rows
    }
    return page.copy(inner = null) to settingsRows(category, model)
}

/** A folder just added from Add shows itself: the card returns to the shelf with the new folder's tile focused. */
fun PickerModel.withFolders(folders: List<LibraryFolder>): PickerModel {
    val next = copy(folders = folders)
    val page = settings?.takeIf { it.inner == SettingsInner.Add } ?: return next
    val added = folders.firstOrNull { new -> this.folders.none { it.uri == new.uri } } ?: return next
    val category = settingsCategories(next).getOrNull(page.category) ?: return next
    val index = settingsTileIndex(category, SettingsInner.Folder(added.uri), next).takeIf { it >= 0 } ?: return next
    return next.copy(settings = page.copy(inner = null, row = index))
}

/** The tile on [category]'s shelf that opens [inner], or -1. */
fun settingsTileIndex(category: SettingsCategory, inner: SettingsInner, model: PickerModel): Int =
    settingsRows(category, model).indexOfFirst { it is Row.SettingsTile && it.page == inner }

/** How a tile's corner badge reads. */
enum class TileStatus {
    Fine,
    Attention,
}

/**
 * An emulator tile wears a check while all its rows are fine, and the not-syncing mark
 * while any says Not syncing or waits to be set up. Other tiles wear none.
 */
fun settingsTileStatus(row: Row, model: PickerModel): TileStatus? {
    val inner = (row as? Row.SettingsTile)?.page as? SettingsInner.Emulator ?: return null
    val needsYou = settingsInnerRows(inner, model).any { rowNeedsYou(it, model) }
    return if (needsYou) TileStatus.Attention else TileStatus.Fine
}

/** True while an emulator's row says Not syncing or waits to be set up. Set up saves reads its steps' progress from this too. */
fun rowNeedsYou(row: Row, model: PickerModel): Boolean = when (row) {
    Row.RetroArchCores -> model.retroArch?.cores == null
    Row.EdenFolder -> model.eden?.fromRomm != true
    else -> (rowControl(row, model) as? RowControl.Value)?.attention == true
}

/**
 * Where the D-pad goes in a card that leads with tiles. The tiles sit in lines of
 * [quickTileColumns]; the rows under them are one column. Down from a tile with nothing
 * under it lands on the next line's last tile. Up from the first row lands on the last
 * line's first tile. Null means the move leaves the card: left from a line's first tile.
 */
fun settingsFocusIndex(rows: List<Row>, index: Int, meaning: Meaning): Int? {
    if (rows.isEmpty()) return 0
    val current = index.coerceIn(0, rows.lastIndex)
    val tiles = rows.takeWhile { it.settingsTile() }.size
    val columns = quickTileColumns
    val lastLine = (tiles - 1).coerceAtLeast(0) / columns
    if (current >= tiles) {
        return when (meaning) {
            Meaning.MoveUp -> if (current == tiles && tiles > 0) lastLine * columns else (current - 1).coerceAtLeast(0)
            Meaning.MoveDown -> (current + 1).coerceAtMost(rows.lastIndex)
            else -> current
        }
    }
    val column = current % columns
    val line = current / columns
    return when (meaning) {
        Meaning.MoveLeft -> if (column == 0) null else current - 1
        Meaning.MoveRight -> if (column == columns - 1 || current + 1 >= tiles) current else current + 1
        Meaning.MoveUp -> if (line == 0) current else current - columns
        Meaning.MoveDown -> when {
            line < lastLine -> (current + columns).coerceAtMost(tiles - 1)
            tiles < rows.size -> tiles
            else -> current
        }
        else -> current
    }
}

/** The row the D-pad is on in the settings page, or null while the categories have it or the page is shut. */
fun settingsFocus(model: PickerModel): Row? {
    val page = model.settings?.takeIf { it.onRows } ?: return null
    return settingsShown(page, model).second.getOrNull(page.row)
}

/**
 * The page keeps its category when the list of categories changes under it, as when the
 * last emulator is uninstalled and Emulators leaves. A category that left drops the focus
 * back to the categories, at the same place.
 */
fun retainSettingsCategory(previous: PickerModel, next: PickerModel): PickerModel {
    val was = previous.settings ?: return next
    val page = next.settings ?: return next
    if (page.category != was.category) return next
    val held = settingsCategories(previous).getOrNull(was.category) ?: return next
    val now = settingsCategories(next)
    if (now.getOrNull(page.category) == held) return next
    val index = now.indexOf(held)
    val settled = if (index >= 0) page.copy(category = index)
        else page.copy(category = page.category.coerceIn(0, now.lastIndex.coerceAtLeast(0)), onRows = false, row = 0, inner = null)
    return next.copy(settings = settled)
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
    // Android owns the switch: A opens its all-files page, and the switch shows what it says.
    Row.FindSaves -> RowControl.Toggle(model.allFilesGranted)
    Row.RecentOnHome -> RowControl.Toggle(model.recentOnHome)
    Row.FolderOnHome -> RowControl.Toggle(model.tileActions?.folderOnHome?.on == true)
    Row.Artwork -> RowControl.Toggle(model.artwork)
    is Row.StreamHost -> RowControl.Toggle(model.streamHosts.any { it.key == row.key && it.paired })
    in choiceRows -> RowControl.Choice(value.orEmpty())
    Row.MusicVolume, Row.Brightness -> RowControl.None
    is Row.Collection -> model.tileActions?.collections?.firstOrNull { it.folderId == row.folderId }.let { RowControl.Check(it?.member == true, it?.some == true) }
    Row.EditHome, Row.AddToCollection, is Row.RommConnection,
    Row.AddFolder, Row.AddRomm, Row.SetAsHome, Row.DefaultHomeApp, Row.AndroidSettings, Row.Licenses, is Row.SettingsTile,
    Row.SaveSetup,
    -> RowControl.Opens
    else -> value?.takeIf { it.isNotEmpty() }?.let {
        RowControl.Value(it, attention = it == Copy.notSyncing &&
            (row is Row.PlayerSave || row is Row.HostedSaves || row is Row.SaveSync || row is Row.PlayerSetup || row == Row.EdenUser))
    } ?: RowControl.None
}
