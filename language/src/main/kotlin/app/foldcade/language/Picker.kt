package app.foldcade.language

enum class HostScreen {
    Top,
    Bottom,
}

enum class Chrome {
    LaunchTarget,
    StatusCluster,
}

data class GridFocus(
    val cellIndex: Int = 0,
    val lastColumn: Int = 0,
    val chrome: Chrome? = null,
)

enum class Side {
    Left,
    Right,
}

enum class PanelLevel {
    Root,
    Library,
}

data class SidePanel(
    val side: Side,
    val screen: HostScreen,
    val level: PanelLevel,
    val index: Int,
    val grid: GridFocus,
    /**
     * The open island is playing its leave morph. [pendingSide] expands only
     * after [finishIslandRetire]. The other island stays at idle size until then.
     */
    val retiring: Boolean = false,
    val pendingSide: Side? = null,
)

fun signInAgainPrompt(screen: HostScreen = HostScreen.Top): DialogState = DialogState(
    kind = DialogKind.ReLogin,
    title = Copy.signInAgainTitle,
    body = Copy.signInAgainBody,
    buttons = listOf(DialogButton.Ok),
    index = 0,
    safeIndex = 0,
    screen = screen,
)

fun connectHint(field: ConnectField): HintActions? = hintLine(
    activateDoesSomething = field == ConnectField.Save,
    backDoesSomething = true,
)

enum class DialogKind {
    Home,
    Folder,
    Ok,
    ReLogin,
    UsageAccess,
    MissingPlayer,
    ClosePlayer,
    SaveFolder,
}

enum class DialogButton {
    UseAsHome,
    NotNow,
    ContinueGrant,
    Allow,
    Ok,
    CloseIt,
}

data class DialogState(
    val kind: DialogKind,
    val title: String,
    val body: String,
    val buttons: List<DialogButton>,
    val index: Int,
    val safeIndex: Int,
    val screen: HostScreen,
    /** A second muted line. The missing-player dialog lists package names here. */
    val detail: String? = null,
)

fun usageAccessPrompt(screen: HostScreen = HostScreen.Bottom): DialogState = DialogState(
    kind = DialogKind.UsageAccess,
    title = Copy.usageAccessTitle,
    body = Copy.usageAccessBody,
    buttons = listOf(DialogButton.Allow, DialogButton.NotNow),
    index = 1,
    safeIndex = 1,
    screen = screen,
)

/** The automatic ask. L1 can open the same explainer later. It never replaces another dialog. */
fun shouldOfferUsageAccess(
    hasTrackedSession: Boolean,
    granted: Boolean,
    alreadyOffered: Boolean,
    dialogOpen: Boolean,
): Boolean = hasTrackedSession && !granted && !alreadyOffered && !dialogOpen

fun homePrompt(screen: HostScreen = HostScreen.Bottom): DialogState = DialogState(
    kind = DialogKind.Home,
    title = Copy.homePromptTitle,
    body = Copy.homePromptBody,
    buttons = listOf(DialogButton.UseAsHome, DialogButton.NotNow),
    index = 0,
    safeIndex = 1,
    screen = screen,
)

fun missingPlayerDialog(playerName: String, screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.MissingPlayer,
    title = "$playerName is not installed",
    body = Copy.missingPlayerBody,
    buttons = listOf(DialogButton.Ok),
    index = 0,
    safeIndex = 0,
    screen = screen,
)

fun closeBothPanelDialog(screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.ClosePlayer,
    title = Copy.closePlayerTitle,
    body = Copy.closePlayerBody,
    buttons = listOf(DialogButton.CloseIt, DialogButton.NotNow),
    index = 0,
    safeIndex = 1,
    screen = screen,
)

fun saveFolderDialog(screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.SaveFolder,
    title = Copy.saveFolderTitle,
    body = Copy.saveFolderBody,
    buttons = listOf(DialogButton.ContinueGrant, DialogButton.NotNow),
    index = 1,
    safeIndex = 1,
    screen = screen,
)

fun playerSavesLabel(name: String, chosen: Boolean): String =
    if (chosen) "$name saves  ${Copy.playerSavesSet}" else "$name saves  ${Copy.playerSavesUnset}"

fun playerNotice(title: String, body: String, screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.Ok,
    title = title,
    body = body,
    buttons = listOf(DialogButton.Ok),
    index = 0,
    safeIndex = 0,
    screen = screen,
)

fun noFileDialog(screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.Ok,
    title = Copy.notOnDevice,
    body = Copy.missingFileBody,
    buttons = listOf(DialogButton.Ok),
    index = 0,
    safeIndex = 0,
    screen = screen,
)

fun folderExplainer(screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.Folder,
    title = Copy.folderGrantTitle,
    body = Copy.folderGrantBody,
    buttons = listOf(DialogButton.ContinueGrant, DialogButton.NotNow),
    index = 0,
    safeIndex = 1,
    screen = screen,
)

/** Debug captures. Back still uses [DialogState.safeIndex]. Unknown names are ignored. */
fun previewDialogState(kind: String, index: Int, screen: HostScreen): DialogState? {
    val dialog = when (kind) {
        "home" -> homePrompt(screen)
        "folder" -> folderExplainer(screen)
        "relogin" -> signInAgainPrompt(screen)
        "save-folder" -> saveFolderDialog(screen)
        "close-player" -> closeBothPanelDialog(screen)
        "missing-player" -> missingPlayerDialog("Azahar", screen)
        else -> return null
    }
    val last = dialog.buttons.lastIndex.coerceAtLeast(0)
    return dialog.copy(index = index.coerceIn(0, last))
}

data class FoldNotice(
    val id: String,
    val title: String,
    val body: String,
)

data class Hold(
    val index: Int,
    val origin: Int,
    val orderBefore: List<Int>,
)

sealed interface Row {
    data object Library : Row
    data object Theme : Row
    data object Background : Row
    data object MotionSpeed : Row
    data object UsageAccess : Row
    data object MoonlightSource : Row
    data object Primary : Row
    data object Arrange : Row
    data object Order : Row
    data object Music : Row
    data object MusicTrack : Row
    data object MusicVolume : Row
    data object SetAsHome : Row
    data object AndroidSettings : Row
    data object DefaultHomeApp : Row
    data class Backend(val name: String) : Row
    data object AddFolder : Row
    data object Connect : Row
    data object LaunchTarget : Row
    data class Notice(val id: String) : Row
    data class SignOut(val pluginId: String, val label: String) : Row
    data class PlayerSave(val playerId: String) : Row
    data object AndroidGames : Row
    data object Apps : Row
    data object HiddenApps : Row
    data object PinApp : Row
    data object MoveApp : Row
    data object HideApp : Row
    data object ShowApp : Row
    data class QuickTile(val setting: QuickSetting) : Row
    data object ButtonLabels : Row
}

/** Two columns in the R1 cluster. Text rows above the tiles stay full width. */
const val quickTileColumns = 2

enum class QuickSetting {
    Wifi,
    Bluetooth,
    Display,
    Sound,
    Battery,
    AppInfo,
}

fun quickSettings(): List<QuickSetting> = listOf(
    QuickSetting.Wifi,
    QuickSetting.Bluetooth,
    QuickSetting.Display,
    QuickSetting.Sound,
    QuickSetting.Battery,
    QuickSetting.AppInfo,
)

enum class AndroidSetting {
    Settings,
    Home,
    Wifi,
    Bluetooth,
    Display,
    Sound,
    Battery,
    AppInfo,
}

fun QuickSetting.androidSetting(): AndroidSetting = when (this) {
    QuickSetting.Wifi -> AndroidSetting.Wifi
    QuickSetting.Bluetooth -> AndroidSetting.Bluetooth
    QuickSetting.Display -> AndroidSetting.Display
    QuickSetting.Sound -> AndroidSetting.Sound
    QuickSetting.Battery -> AndroidSetting.Battery
    QuickSetting.AppInfo -> AndroidSetting.AppInfo
}

data class PlayerSaveSetting(
    val playerId: String,
    val label: String,
    val chosen: Boolean,
)

fun leftRows(
    homeRoleHeld: Boolean,
    playerSaves: List<PlayerSaveSetting> = emptyList(),
    offerButtonLabels: Boolean = false,
): List<Row> = buildList {
    add(Row.Library)
    add(Row.Theme)
    add(Row.Primary)
    add(Row.Arrange)
    add(Row.Order)
    add(Row.Music)
    add(Row.MusicTrack)
    add(Row.MusicVolume)
    if (!homeRoleHeld) add(Row.SetAsHome)
    add(Row.Background)
    add(Row.MotionSpeed)
    add(Row.UsageAccess)
    add(Row.MoonlightSource)
    if (offerButtonLabels) add(Row.ButtonLabels)
    playerSaves.forEach { add(Row.PlayerSave(it.playerId)) }
    add(Row.AndroidGames)
    add(Row.Apps)
    add(Row.HiddenApps)
    add(Row.AndroidSettings)
    add(Row.DefaultHomeApp)
}

data class SignedInBackend(
    val pluginId: String,
    val label: String,
)

enum class ConnectField {
    Origin,
    Token,
    Save,
}

fun connectFields(): List<ConnectField> = listOf(ConnectField.Origin, ConnectField.Token, ConnectField.Save)

/**
 * Until RomM has a server, its library row is [Copy.setUpRomm] and opens the
 * connect form, so a second [Row.Connect] would only repeat it.
 */
fun libraryRows(backends: List<String>, signedIn: List<SignedInBackend> = emptyList()): List<Row> =
    backends.map { Row.Backend(it) } +
        signedIn.map { Row.SignOut(it.pluginId, it.label) } +
        listOf(Row.AddFolder) +
        listOfNotNull(Row.Connect.takeIf { Copy.setUpRomm !in backends })

fun rightRows(
    showLaunchTarget: Boolean,
    notices: List<FoldNotice>,
    actions: AppActions? = null,
): List<Row> = buildList {
    if (showLaunchTarget) add(Row.LaunchTarget)
    if (actions != null) {
        if (actions.hiddenShelf) {
            add(Row.ShowApp)
        } else {
            add(Row.PinApp)
            add(Row.MoveApp)
            add(Row.HideApp)
        }
    }
    notices.forEach { add(Row.Notice(it.id)) }
    quickSettings().forEach { add(Row.QuickTile(it)) }
}

fun panelRows(panel: SidePanel, model: PickerModel): List<Row> = when (panel.side) {
    Side.Left -> when (panel.level) {
        PanelLevel.Root -> leftRows(model.homeRoleHeld, model.playerSaves, model.offerButtonLabels)
        PanelLevel.Library -> libraryRows(model.backends, model.signedIn)
    }
    Side.Right -> rightRows(model.showLaunchTarget, model.notices, model.appActions)
}

fun backgroundLabel(motion: BackgroundMotion): String = when (motion) {
    BackgroundMotion.Ribbons -> Copy.ribbons
    BackgroundMotion.Embers -> Copy.embers
    BackgroundMotion.Static -> Copy.motionStatic
    BackgroundMotion.Off -> Copy.motionOff
}

fun speedLabel(speed: MotionSpeed): String = when (speed) {
    MotionSpeed.Slow -> Copy.speedSlow
    MotionSpeed.Slower -> Copy.speedSlower
    MotionSpeed.Off -> Copy.motionOff
}

/** Label on the left, current value on the right. A null value is a single action row. */
data class RowText(val label: String, val value: String? = null)

fun rowText(row: Row, model: PickerModel): RowText = when (row) {
    Row.Library -> RowText(Copy.library)
    Row.Theme -> RowText(Copy.theme, model.themes.getOrElse(model.themeIndex) { Copy.builtIn })
    Row.Background -> RowText(Copy.background, backgroundLabel(model.backgroundMotion))
    Row.MotionSpeed -> RowText(Copy.motion, speedLabel(model.motionSpeed))
    Row.UsageAccess -> RowText(
        "Play time",
        if (model.usageGranted) "All launchers" else Copy.approximatePlay,
    )
    Row.MoonlightSource -> RowText(Copy.moonlight, moonlightSourceLabel(model.moonlightSource))
    Row.Primary -> RowText(Copy.primaryPanel, if (model.primaryIsTop) Copy.top else Copy.bottom)
    Row.Arrange -> RowText(Copy.arrange)
    Row.Order -> RowText("Order", if (model.sort == LibrarySort.RecentlyPlayed) "Recently played" else "Library")
    Row.Music -> RowText(MusicCopy.row, if (model.music.enabled) MusicCopy.on else MusicCopy.off)
    Row.MusicTrack -> RowText(MusicCopy.track, model.trackTitle)
    Row.MusicVolume -> RowText(MusicCopy.volume, MusicCopy.volumeLabel(model.music.volume).substringAfter("  "))
    Row.SetAsHome -> RowText(Copy.setAsHome)
    Row.AndroidSettings -> RowText(Copy.androidSettings)
    Row.DefaultHomeApp -> RowText(Copy.defaultHomeApp)
    is Row.QuickTile -> RowText(
        when (row.setting) {
            QuickSetting.Wifi -> Copy.wifi
            QuickSetting.Bluetooth -> Copy.bluetooth
            QuickSetting.Display -> Copy.display
            QuickSetting.Sound -> Copy.sound
            QuickSetting.Battery -> Copy.battery
            QuickSetting.AppInfo -> Copy.appInfo
        },
    )
    is Row.Backend -> RowText(row.name)
    Row.AddFolder -> RowText(Copy.addFolder)
    Row.Connect -> RowText(Copy.connectRomm)
    is Row.SignOut -> RowText("${Copy.signOut} · ${row.label}")
    Row.LaunchTarget -> RowText(if (model.launchOnBottom) Copy.launchOnBottom else Copy.launchOnTop)
    is Row.Notice -> RowText(model.notices.firstOrNull { it.id == row.id }?.title ?: "")
    is Row.PlayerSave -> {
        val setting = model.playerSaves.firstOrNull { it.playerId == row.playerId }
        val chosen = if (setting?.chosen == true) Copy.playerSavesSet else Copy.playerSavesUnset
        RowText("${setting?.label ?: row.playerId} saves", chosen)
    }
    Row.AndroidGames -> RowText(Copy.androidGames)
    Row.Apps -> RowText(Copy.apps)
    Row.HiddenApps -> RowText(Copy.hiddenApps)
    Row.PinApp -> RowText(if (model.appActions?.favorite == true) Copy.unpin else Copy.pin)
    Row.MoveApp -> RowText(if (model.appActions?.onGamesShelf == true) Copy.moveToApps else Copy.moveToGames)
    Row.HideApp -> RowText(Copy.hideApp)
    Row.ShowApp -> RowText(Copy.showApp)
    Row.ButtonLabels -> RowText(
        Copy.buttonLabels,
        if (model.capturingConfirm) Copy.pressConfirm else null,
    )
}

fun rowLabel(row: Row, model: PickerModel): String {
    val text = rowText(row, model)
    val value = text.value
    return if (value.isNullOrEmpty()) text.label else "${text.label}  $value"
}

fun displayOrder(model: PickerModel): List<Int> {
    if (model.count <= 0) return emptyList()
    if (model.arranging && model.order.size == model.count) return model.order
    if (model.sort == LibrarySort.RecentlyPlayed && model.recentFirst.size == model.count) {
        return model.recentFirst
    }
    if (model.order.size == model.count) return model.order
    return List(model.count) { it }
}

sealed interface Effect {
    data class Launch(val index: Int) : Effect
    data class OpenPlatform(val index: Int) : Effect
    data object LeavePlatform : Effect
    data object TryAgain : Effect
    data object CycleLaunchTarget : Effect
    data object AddFolder : Effect
    data object OpenConnect : Effect
    data class DialogChoice(val button: DialogButton, val kind: DialogKind) : Effect
    data class ActivateBackend(val name: String) : Effect
    data class ForgetCredentials(val pluginId: String) : Effect
    data object SaveRommToken : Effect
    data object RequestHome : Effect
    data class ChoosePlayerSave(val playerId: String) : Effect
    data object PinApp : Effect
    data object MoveApp : Effect
    data object HideApp : Effect
    data object ShowApp : Effect
    data class OpenAndroidSetting(val setting: AndroidSetting) : Effect
    data object DismissButtonLabels : Effect
    data class ConfirmMoonlightImport(val checked: List<MoonlightSheetApp>) : Effect
    data object SkipMoonlightImport : Effect
    data object ReviewMoonlightImport : Effect
}

/** What a grid cell is. Existing callers stay on [Games], which launches. */
enum class GridKind {
    Games,
    Platforms,
    NoLibrary,
    Unreachable,
}

/** Why a grid with no cells is showing. [None] means the grid has cells, or the kind already names the state. */
enum class EmptyGrid {
    None,
    Loading,
    NoPlatforms,
    NoGames,
}

data class PickerModel(
    val count: Int,
    val rowsPerPage: Int,
    val showLaunchTarget: Boolean,
    val focus: GridFocus = GridFocus(),
    val panel: SidePanel? = null,
    val dialog: DialogState? = null,
    val connectOpen: Boolean = false,
    val atLibraryRoot: Boolean = true,
    val primaryIsTop: Boolean = true,
    val backends: List<String> = emptyList(),
    val themes: List<String> = listOf(Copy.builtIn),
    val themeIndex: Int = 0,
    val themeMotions: List<BackgroundMotion> = listOf(BackgroundMotion.Off),
    val backgroundMotion: BackgroundMotion = BackgroundMotion.Off,
    val backgroundPinned: Boolean = false,
    val motionSpeed: MotionSpeed = MotionSpeed.Slow,
    val homeRoleHeld: Boolean = false,
    val folderGrantPending: Boolean = true,
    val notices: List<FoldNotice> = emptyList(),
    val launchOnBottom: Boolean = false,
    val music: HomeMusicSetting = HomeMusicSetting(),
    val trackTitle: String = DEFAULT_TRACK_TITLE,
    val homeTracks: List<MusicTrack> = emptyList(),
    /** Parallel to [themes]. Null when that theme does not offer a track. */
    val themeTracks: List<MusicTrack?> = emptyList(),
    val arranging: Boolean = false,
    val hold: Hold? = null,
    val order: List<Int> = emptyList(),
    val sort: LibrarySort = LibrarySort.Listed,
    val recentFirst: List<Int> = emptyList(),
    val signedIn: List<SignedInBackend> = emptyList(),
    val connectOrigin: String = "",
    val connectIndex: Int = 0,
    val connectScreen: HostScreen? = null,
    val connectWarning: String? = null,
    val connectHint: String? = null,
    val reLoginPending: Boolean = false,
    val unavailable: Boolean = false,
    val playerSaves: List<PlayerSaveSetting> = emptyList(),
    val homeGrid: HomeGrid = HomeGrid.StandIns,
    val appActions: AppActions? = null,
    /**
     * Bumped when the shelf's text changes without the count changing.
     * Compose treats an equal model as unchanged.
     */
    val shelfEpoch: Int = 0,
    val faceMap: FaceMap = FaceMap.standard(),
    val thorGlyphs: Boolean = true,
    val offerButtonLabels: Boolean = false,
    val capturingConfirm: Boolean = false,
    val held: Set<PromptKey> = emptySet(),
    val libraryGrid: Boolean = false,
    val gridKind: GridKind = GridKind.Games,
    val emptyGrid: EmptyGrid = EmptyGrid.None,
    val usageGranted: Boolean = false,
    val moonlightSource: MoonlightSource = MoonlightSource.PinnedShortcuts,
    val moonlightPlacements: List<MoonlightPlacement> = emptyList(),
    val moonlightImportConfirmed: Boolean = false,
    val moonlightSheet: MoonlightImportSheet? = null,
)

fun reduce(
    model: PickerModel,
    meaning: Meaning,
    screen: HostScreen = HostScreen.Bottom,
): Pair<PickerModel, Effect?> {
    val coerced = model.copy(focus = coerce(model.focus, model))
    val sheet = coerced.moonlightSheet
    if (sheet != null) {
        if (meaning == Meaning.LeftPanel || meaning == Meaning.RightPanel) return coerced to null
        val (nextSheet, result) = applyMoonlightSheet(sheet, meaning)
        return when (result) {
            is MoonlightSheetResult.Import -> coerced.copy(
                moonlightSheet = null,
                moonlightSource = MoonlightSource.ImportedList,
                moonlightImportConfirmed = true,
                moonlightPlacements = placeMoonlightImport(coerced.moonlightPlacements, result.checked),
            ) to Effect.ConfirmMoonlightImport(result.checked)
            MoonlightSheetResult.NotNow -> coerced.copy(moonlightSheet = null) to Effect.SkipMoonlightImport
            null -> coerced.copy(moonlightSheet = nextSheet) to null
        }
    }
    val dialog = coerced.dialog
    if (dialog != null) {
        if (meaning == Meaning.LeftPanel || meaning == Meaning.RightPanel) return coerced to null
        val (next, choice) = applyDialog(dialog, meaning)
        val chosen = choice?.let { Effect.DialogChoice(it, dialog.kind) }
        val cleared = if (choice != null) null else next
        return coerced.copy(dialog = cleared) to chosen
    }
    if (coerced.connectOpen) {
        val fields = connectFields()
        val index = coerced.connectIndex.coerceIn(0, fields.lastIndex)
        val field = fields[index]
        return when (meaning) {
            Meaning.Back -> coerced.copy(
                connectOpen = false,
                connectScreen = null,
            ) to null
            Meaning.MoveUp -> coerced.copy(connectIndex = (index - 1).coerceAtLeast(0)) to null
            Meaning.MoveDown -> coerced.copy(connectIndex = (index + 1).coerceAtMost(fields.lastIndex)) to null
            Meaning.Activate -> when (field) {
                ConnectField.Save -> coerced to Effect.SaveRommToken
                ConnectField.Origin, ConnectField.Token -> coerced.copy(connectIndex = index) to null
            }
            Meaning.LeftPanel, Meaning.RightPanel ->
                presentPanel(
                    coerced.copy(connectOpen = false, connectScreen = null),
                    meaning,
                    screen,
                ) to null
            else -> coerced.copy(connectIndex = index) to null
        }
    }
    val panel = coerced.panel
    if (panel != null) {
        if (meaning == Meaning.LeftPanel || meaning == Meaning.RightPanel) {
            return presentPanel(coerced, meaning, screen) to null
        }
        return applyPanel(coerced, panel, meaning, screen)
    }
    return applyGrid(coerced, meaning, screen)
}

private fun coerce(focus: GridFocus, model: PickerModel): GridFocus {
    var next = focus
    if (next.chrome == Chrome.LaunchTarget && !model.showLaunchTarget) {
        next = next.copy(chrome = Chrome.StatusCluster)
    }
    if (model.count <= 0) {
        return next.copy(chrome = next.chrome ?: Chrome.StatusCluster, cellIndex = 0, lastColumn = 0)
    }
    if (next.chrome == null && next.cellIndex !in 0 until model.count) {
        val index = (model.count - 1).coerceAtLeast(0)
        next = next.copy(cellIndex = index, lastColumn = index % Metrics.columns)
    }
    return next
}

@Suppress("UNUSED_PARAMETER")
private fun presentPanel(model: PickerModel, meaning: Meaning, screen: HostScreen): PickerModel {
    val side = if (meaning == Meaning.LeftPanel) Side.Left else Side.Right
    val open = model.panel
    if (open == null) return model.copy(panel = freshPanel(side, model.focus))
    if (open.retiring) {
        val pending = if (side == open.side) null else side
        return model.copy(panel = open.copy(pendingSide = pending))
    }
    if (open.side == side) return model.copy(panel = null, focus = open.grid)
    return model.copy(panel = open.copy(retiring = true, pendingSide = side))
}

private fun freshPanel(side: Side, grid: GridFocus): SidePanel = SidePanel(
    side = side,
    screen = HostScreen.Top,
    level = PanelLevel.Root,
    index = 0,
    grid = grid,
)

/**
 * Debug captures show [side] now.
 * The live shoulder path still retires the open island before the other expands.
 */
fun replaceIsland(model: PickerModel, side: Side): PickerModel {
    val open = model.panel
    if (open != null &&
        open.side == side &&
        !open.retiring &&
        open.pendingSide == null &&
        open.screen == HostScreen.Top &&
        open.level == PanelLevel.Root
    ) {
        return model
    }
    return model.copy(panel = freshPanel(side, open?.grid ?: model.focus))
}

/**
 * The retiring island has finished its leave morph.
 * The pending shoulder then expands. A close with no pending side stays closed.
 */
fun finishIslandRetire(model: PickerModel): PickerModel {
    val open = model.panel ?: return model
    if (!open.retiring) return model
    val next = open.pendingSide ?: return model.copy(panel = null, focus = open.grid)
    return model.copy(panel = freshPanel(next, open.grid))
}

private fun applyGrid(
    model: PickerModel,
    meaning: Meaning,
    screen: HostScreen,
): Pair<PickerModel, Effect?> {
    val hold = model.hold
    if (model.arranging && hold != null &&
        (meaning == Meaning.MoveUp || meaning == Meaning.MoveDown ||
            meaning == Meaning.MoveLeft || meaning == Meaning.MoveRight)
    ) {
        return moveHeld(model, hold, meaning) to null
    }
    return when (meaning) {
        Meaning.MoveUp, Meaning.MoveDown, Meaning.MoveLeft, Meaning.MoveRight ->
            model.copy(focus = move(model.focus, meaning, model)) to null
        Meaning.PageTowardStart, Meaning.PageTowardEnd ->
            model.copy(focus = page(model.focus, meaning == Meaning.PageTowardEnd, model)) to null
        Meaning.Activate -> activate(model, screen)
        Meaning.Back -> backGrid(model)
        Meaning.LeftPanel, Meaning.RightPanel -> presentPanel(model, meaning, screen) to null
    }
}

private fun backGrid(model: PickerModel): Pair<PickerModel, Effect?> {
    val hold = model.hold
    if (hold != null) {
        return model.copy(
            hold = null,
            order = hold.orderBefore,
            focus = model.focus.copy(cellIndex = hold.origin, lastColumn = hold.origin % Metrics.columns, chrome = null),
        ) to null
    }
    if (model.arranging) return model.copy(arranging = false) to null
    if (!model.atLibraryRoot) return model to Effect.LeavePlatform
    return model to null
}

private fun moveHeld(model: PickerModel, hold: Hold, meaning: Meaning): PickerModel {
    val focus = GridFocus(cellIndex = hold.index, lastColumn = hold.index % Metrics.columns)
    val moved = move(focus, meaning, model)
    if (moved.chrome != null || moved.cellIndex == hold.index) return model
    val order = displayOrder(model).toMutableList()
    val item = order.removeAt(hold.index)
    order.add(moved.cellIndex, item)
    return model.copy(
        hold = hold.copy(index = moved.cellIndex),
        order = order,
        focus = moved,
    )
}

private fun move(focus: GridFocus, meaning: Meaning, model: PickerModel): GridFocus {
    val chrome = focus.chrome
    if (chrome != null) {
        return when (meaning) {
            Meaning.MoveRight ->
                if (chrome == Chrome.LaunchTarget) focus.copy(chrome = Chrome.StatusCluster) else focus
            Meaning.MoveLeft ->
                if (chrome == Chrome.StatusCluster && model.showLaunchTarget) {
                    focus.copy(chrome = Chrome.LaunchTarget)
                } else {
                    focus
                }
            Meaning.MoveDown -> downFromChrome(focus, model)
            Meaning.MoveUp -> focus
            else -> focus
        }
    }
    if (model.count <= 0) return focus.copy(chrome = Chrome.StatusCluster)
    val columns = Metrics.columns
    val column = focus.cellIndex % columns
    val row = focus.cellIndex / columns
    return when (meaning) {
        Meaning.MoveLeft -> step(focus, column - 1, row, model)
        Meaning.MoveRight -> step(focus, column + 1, row, model)
        Meaning.MoveDown -> step(focus, column, row + 1, model)
        Meaning.MoveUp -> if (row == 0) upToChrome(focus, column, model) else step(focus, column, row - 1, model)
        else -> focus
    }
}

private fun step(focus: GridFocus, column: Int, row: Int, model: PickerModel): GridFocus {
    if (column !in 0 until Metrics.columns || row < 0) return focus
    val index = row * Metrics.columns + column
    if (index !in 0 until model.count) return focus
    return focus.copy(cellIndex = index, lastColumn = column, chrome = null)
}

private fun upToChrome(focus: GridFocus, column: Int, model: PickerModel): GridFocus {
    val target = if (!model.showLaunchTarget || column >= 2) Chrome.StatusCluster else Chrome.LaunchTarget
    return focus.copy(chrome = target, lastColumn = column)
}

private fun downFromChrome(focus: GridFocus, model: PickerModel): GridFocus {
    if (model.count <= 0) return focus
    val rowCount = minOf(Metrics.columns, model.count)
    val column = focus.lastColumn.coerceIn(0, rowCount - 1)
    return focus.copy(cellIndex = column, lastColumn = column, chrome = null)
}

private fun page(focus: GridFocus, towardEnd: Boolean, model: PickerModel): GridFocus {
    if (model.count <= 0 || focus.chrome != null) return focus
    val pageSize = Metrics.columns * model.rowsPerPage.coerceAtLeast(1)
    val index = focus.cellIndex.coerceIn(0, model.count - 1)
    val pageIndex = index / pageSize
    val slot = index % pageSize
    val nextPage = pageIndex + if (towardEnd) 1 else -1
    if (nextPage < 0) return focus
    val start = nextPage * pageSize
    if (start >= model.count) return focus
    val landed = minOf(start + slot, model.count - 1)
    return focus.copy(cellIndex = landed, lastColumn = landed % Metrics.columns, chrome = null)
}

private fun activate(model: PickerModel, screen: HostScreen): Pair<PickerModel, Effect?> {
    return when (model.focus.chrome) {
        Chrome.StatusCluster -> presentPanel(model, Meaning.RightPanel, screen) to null
        Chrome.LaunchTarget -> model.copy(launchOnBottom = !model.launchOnBottom) to Effect.CycleLaunchTarget
        null -> {
            if (model.arranging) return placeOrPick(model)
            if (model.count <= 0 || model.focus.cellIndex !in 0 until model.count) return model to null
            if (!model.libraryGrid) return model to Effect.Launch(model.focus.cellIndex)
            when (model.gridKind) {
                GridKind.Games -> model to Effect.Launch(model.focus.cellIndex)
                GridKind.Platforms -> model to Effect.OpenPlatform(model.focus.cellIndex)
                GridKind.Unreachable -> model to Effect.TryAgain
                GridKind.NoLibrary -> if (model.focus.cellIndex == 0) {
                    if (model.folderGrantPending) {
                        model.copy(dialog = folderExplainer(screen)) to null
                    } else {
                        model to Effect.AddFolder
                    }
                } else {
                    model.copy(
                        connectOpen = true,
                        connectScreen = screen,
                        connectIndex = 0,
                    ) to Effect.OpenConnect
                }
            }
        }
    }
}

private fun placeOrPick(model: PickerModel): Pair<PickerModel, Effect?> {
    val hold = model.hold
    if (hold != null) return model.copy(hold = null) to null
    val index = model.focus.cellIndex
    val order = displayOrder(model)
    return model.copy(hold = Hold(index = index, origin = index, orderBefore = order), order = order) to null
}

/**
 * Text rows are one column. Quick tiles are [quickTileColumns] wide and sit
 * together at the end of the list. A direction with no neighbor stays put.
 */
fun panelFocusIndex(rows: List<Row>, index: Int, meaning: Meaning): Int {
    if (rows.isEmpty()) return 0
    val current = index.coerceIn(0, rows.lastIndex)
    val tileStart = rows.indexOfFirst { it is Row.QuickTile }
    val tilesAreSuffix = tileStart >= 0 && rows.drop(tileStart).all { it is Row.QuickTile }
    if (!tilesAreSuffix) {
        return when (meaning) {
            Meaning.MoveUp -> (current - 1).coerceAtLeast(0)
            Meaning.MoveDown -> (current + 1).coerceAtMost(rows.lastIndex)
            else -> current
        }
    }
    if (current < tileStart) {
        return when (meaning) {
            Meaning.MoveUp -> (current - 1).coerceAtLeast(0)
            Meaning.MoveDown -> if (current == tileStart - 1) tileStart else current + 1
            else -> current
        }
    }
    val tileIndex = current - tileStart
    val column = tileIndex % quickTileColumns
    val row = tileIndex / quickTileColumns
    val tileCount = rows.size - tileStart
    return when (meaning) {
        Meaning.MoveLeft -> if (column == 0) current else current - 1
        Meaning.MoveRight -> {
            val next = tileIndex + 1
            if (next >= tileCount || next / quickTileColumns != row) current else tileStart + next
        }
        Meaning.MoveUp -> if (row == 0) {
            if (tileStart == 0) current else tileStart - 1
        } else {
            tileStart + (row - 1) * quickTileColumns + column
        }
        Meaning.MoveDown -> {
            val below = (row + 1) * quickTileColumns + column
            if (below < tileCount) tileStart + below else current
        }
        else -> current
    }
}

private fun applyPanel(
    model: PickerModel,
    panel: SidePanel,
    meaning: Meaning,
    screen: HostScreen,
): Pair<PickerModel, Effect?> {
    val rows = panelRows(panel, model)
    if (rows.isEmpty()) {
        return if (meaning == Meaning.Back) model.copy(panel = null, focus = panel.grid) to null else model to null
    }
    val index = panel.index.coerceIn(0, rows.lastIndex)
    val current = panel.copy(index = index)
    return when (meaning) {
        Meaning.MoveUp, Meaning.MoveDown ->
            model.copy(panel = current.copy(index = panelFocusIndex(rows, index, meaning))) to null
        Meaning.MoveLeft, Meaning.MoveRight -> {
            val direction = if (meaning == Meaning.MoveLeft) -1 else 1
            val nudged = adjustRow(model, rows[index], direction)
            if (nudged != null) {
                nudged.copy(panel = current) to null
            } else {
                model.copy(panel = current.copy(index = panelFocusIndex(rows, index, meaning))) to null
            }
        }
        Meaning.PageTowardStart, Meaning.PageTowardEnd ->
            model.copy(panel = current) to null
        Meaning.Back -> {
            val cleared = if (model.capturingConfirm) model.copy(capturingConfirm = false) else model
            backPanel(cleared, current)
        }
        Meaning.Activate -> activateRow(model, current, rows[index], screen)
        Meaning.LeftPanel, Meaning.RightPanel -> presentPanel(model, meaning, screen) to null
    }
}

/**
 * Left and right step a value and keep the row. Music volume moves by 5 points.
 * The track row cycles the offered tracks. Other rows ignore the direction
 * so the panel focus stays put.
 */
private fun adjustRow(model: PickerModel, row: Row, direction: Int): PickerModel? {
    return when (row) {
        Row.MusicVolume -> model.copy(music = model.music.nudged(direction))
        Row.MusicTrack -> model.withTrack(cycledMusicTrack(offeredTracks(model), model.music.trackId, direction))
        else -> null
    }
}

private fun offeredTracks(model: PickerModel): List<MusicTrack> =
    offeredMusicTracks(model.homeTracks, model.themeTracks.getOrNull(model.themeIndex))

private fun PickerModel.withTrack(track: MusicTrack): PickerModel =
    copy(music = music.copy(trackId = track.id), trackTitle = track.title)

private fun backPanel(model: PickerModel, panel: SidePanel): Pair<PickerModel, Effect?> {
    return if (panel.level == PanelLevel.Library) {
        model.copy(panel = panel.copy(level = PanelLevel.Root, index = 0)) to null
    } else {
        model.copy(panel = null, focus = panel.grid) to null
    }
}

private fun activateRow(
    model: PickerModel,
    panel: SidePanel,
    row: Row,
    screen: HostScreen,
): Pair<PickerModel, Effect?> {
    return when (row) {
        Row.Library -> model.copy(panel = panel.copy(level = PanelLevel.Library, index = 0)) to null
        Row.Theme -> {
            val count = model.themes.size.coerceAtLeast(1)
            val nextIndex = (model.themeIndex + 1) % count
            val motion = if (model.backgroundPinned) {
                model.backgroundMotion
            } else {
                model.themeMotions.getOrElse(nextIndex) { BackgroundMotion.Off }
            }
            val stepped = model.copy(panel = panel, themeIndex = nextIndex, backgroundMotion = motion)
            val tracks = offeredMusicTracks(stepped.homeTracks, stepped.themeTracks.getOrNull(nextIndex))
            val kept = tracks.firstOrNull { it.id == model.music.trackId }
            val track = kept ?: selectedMusicTrack(tracks, HomeMusicSetting.DEFAULT_TRACK_ID)
            stepped.withTrack(track) to null
        }
        Row.Background -> model.copy(
            panel = panel,
            backgroundMotion = model.backgroundMotion.next(),
            backgroundPinned = true,
        ) to null
        Row.MotionSpeed -> model.copy(panel = panel, motionSpeed = model.motionSpeed.next()) to null
        Row.UsageAccess -> model.copy(dialog = usageAccessPrompt(screen)) to null
        Row.MoonlightSource -> {
            val nextSource = cycleMoonlightSource(model.moonlightSource)
            if (nextSource == MoonlightSource.ImportedList && !model.moonlightImportConfirmed) {
                model to Effect.ReviewMoonlightImport
            } else {
                model.copy(panel = panel, moonlightSource = nextSource) to null
            }
        }
        Row.Primary -> model.copy(panel = panel, primaryIsTop = !model.primaryIsTop) to null
        Row.Arrange -> model.copy(
            panel = null,
            focus = panel.grid,
            arranging = true,
            hold = null,
            order = displayOrder(model),
        ) to null
        Row.Order -> model.copy(panel = panel, sort = model.sort.toggled()) to null
        Row.Music -> model.copy(panel = panel, music = model.music.toggled()) to null
        Row.MusicTrack ->
            model.copy(panel = panel).withTrack(cycledMusicTrack(offeredTracks(model), model.music.trackId, 1)) to null
        Row.MusicVolume -> model.copy(panel = panel, music = model.music.stepped()) to null
        Row.SetAsHome -> model to Effect.RequestHome
        Row.AndroidSettings -> model.copy(panel = panel) to Effect.OpenAndroidSetting(AndroidSetting.Settings)
        Row.DefaultHomeApp -> model.copy(panel = panel) to Effect.OpenAndroidSetting(AndroidSetting.Home)
        is Row.QuickTile -> model.copy(panel = panel) to Effect.OpenAndroidSetting(row.setting.androidSetting())
        is Row.Backend -> model.copy(panel = null, focus = panel.grid) to Effect.ActivateBackend(row.name)
        Row.AddFolder ->
            if (model.folderGrantPending) {
                model.copy(dialog = folderExplainer(screen)) to null
            } else {
                model to Effect.AddFolder
            }
        Row.Connect -> model.copy(
            panel = null,
            focus = panel.grid,
            connectOpen = true,
            connectScreen = screen,
            connectIndex = 0,
        ) to Effect.OpenConnect
        is Row.SignOut -> model.copy(panel = null, focus = panel.grid) to Effect.ForgetCredentials(row.pluginId)
        Row.LaunchTarget -> model.copy(panel = panel, launchOnBottom = !model.launchOnBottom) to Effect.CycleLaunchTarget
        is Row.Notice -> model.copy(panel = panel, notices = model.notices.filter { it.id != row.id }) to null
        is Row.PlayerSave -> model.copy(panel = null, focus = panel.grid) to Effect.ChoosePlayerSave(row.playerId)
        Row.AndroidGames -> openGrid(model, panel, HomeGrid.AndroidGames)
        Row.Apps -> openGrid(model, panel, HomeGrid.Apps)
        Row.HiddenApps -> openGrid(model, panel, HomeGrid.HiddenApps)
        Row.PinApp -> model to Effect.PinApp
        Row.MoveApp -> model.copy(panel = null, focus = panel.grid) to Effect.MoveApp
        Row.HideApp -> model.copy(panel = null, focus = panel.grid) to Effect.HideApp
        Row.ShowApp -> model.copy(panel = null, focus = panel.grid) to Effect.ShowApp
        Row.ButtonLabels -> if (model.capturingConfirm) {
            model.copy(capturingConfirm = false, offerButtonLabels = false) to Effect.DismissButtonLabels
        } else {
            model.copy(capturingConfirm = true) to null
        }
    }
}

private fun openGrid(
    model: PickerModel,
    panel: SidePanel,
    grid: HomeGrid,
): Pair<PickerModel, Effect?> = model.copy(
    panel = null,
    focus = GridFocus(),
    arranging = false,
    hold = null,
    order = emptyList(),
    homeGrid = grid,
    libraryGrid = false,
) to null

/** A tap on a dialog button focuses that button and activates it. */
fun focusAndActivateDialog(model: PickerModel, index: Int): Pair<PickerModel, Effect?> {
    val dialog = model.dialog ?: return model to null
    if (index !in dialog.buttons.indices) return model to null
    return reduce(model.copy(dialog = dialog.copy(index = index)), Meaning.Activate, dialog.screen)
}

private fun applyDialog(dialog: DialogState, meaning: Meaning): Pair<DialogState, DialogButton?> {
    val last = dialog.buttons.lastIndex.coerceAtLeast(0)
    val index = dialog.index.coerceIn(0, last)
    return when (meaning) {
        Meaning.MoveLeft -> dialog.copy(index = (index - 1).coerceAtLeast(0)) to null
        Meaning.MoveRight -> dialog.copy(index = (index + 1).coerceAtMost(last)) to null
        Meaning.MoveUp, Meaning.MoveDown, Meaning.PageTowardStart, Meaning.PageTowardEnd,
        Meaning.LeftPanel, Meaning.RightPanel,
        -> dialog.copy(index = index) to null
        Meaning.Activate -> dialog to dialog.buttons[index]
        Meaning.Back -> dialog to dialog.buttons[dialog.safeIndex.coerceIn(0, last)]
    }
}
