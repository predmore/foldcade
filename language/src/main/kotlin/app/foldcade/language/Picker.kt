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

fun connectHint(field: ConnectField): String? = hintLine(
    activateDoesSomething = field == ConnectField.Save,
    backDoesSomething = true,
)

enum class DialogKind {
    Home,
    Folder,
    Ok,
    ReLogin,
}

enum class DialogButton {
    UseAsHome,
    NotNow,
    ContinueGrant,
    Ok,
}

data class DialogState(
    val kind: DialogKind,
    val title: String,
    val body: String,
    val buttons: List<DialogButton>,
    val index: Int,
    val safeIndex: Int,
    val screen: HostScreen,
)

fun homePrompt(screen: HostScreen = HostScreen.Bottom): DialogState = DialogState(
    kind = DialogKind.Home,
    title = Copy.homePromptTitle,
    body = Copy.homePromptBody,
    buttons = listOf(DialogButton.UseAsHome, DialogButton.NotNow),
    index = 1,
    safeIndex = 1,
    screen = screen,
)

fun folderExplainer(screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.Folder,
    title = Copy.folderGrantTitle,
    body = Copy.folderGrantBody,
    buttons = listOf(DialogButton.ContinueGrant, DialogButton.NotNow),
    index = 1,
    safeIndex = 1,
    screen = screen,
)

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
    data object Primary : Row
    data object Arrange : Row
    data object Music : Row
    data object MusicVolume : Row
    data object SetAsHome : Row
    data class Backend(val name: String) : Row
    data object AddFolder : Row
    data object Connect : Row
    data object LaunchTarget : Row
    data class Notice(val id: String) : Row
    data class SignOut(val pluginId: String, val label: String) : Row
}

fun leftRows(homeRoleHeld: Boolean): List<Row> = buildList {
    add(Row.Library)
    add(Row.Theme)
    add(Row.Primary)
    add(Row.Arrange)
    add(Row.Music)
    add(Row.MusicVolume)
    if (!homeRoleHeld) add(Row.SetAsHome)
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

fun libraryRows(backends: List<String>, signedIn: List<SignedInBackend> = emptyList()): List<Row> =
    backends.map { Row.Backend(it) } +
        signedIn.map { Row.SignOut(it.pluginId, it.label) } +
        listOf(Row.AddFolder, Row.Connect)

fun rightRows(showLaunchTarget: Boolean, notices: List<FoldNotice>): List<Row> = buildList {
    if (showLaunchTarget) add(Row.LaunchTarget)
    notices.forEach { add(Row.Notice(it.id)) }
}

fun panelRows(panel: SidePanel, model: PickerModel): List<Row> = when (panel.side) {
    Side.Left -> when (panel.level) {
        PanelLevel.Root -> leftRows(model.homeRoleHeld)
        PanelLevel.Library -> libraryRows(model.backends, model.signedIn)
    }
    Side.Right -> rightRows(model.showLaunchTarget, model.notices)
}

fun rowLabel(row: Row, model: PickerModel): String = when (row) {
    Row.Library -> Copy.library
    Row.Theme -> "${Copy.theme}  ${model.themes.getOrElse(model.themeIndex) { Copy.builtIn }}"
    Row.Primary -> "${Copy.primaryPanel}  ${if (model.primaryIsTop) Copy.top else Copy.bottom}"
    Row.Arrange -> Copy.arrange
    Row.Music -> MusicCopy.musicLabel(model.music.enabled)
    Row.MusicVolume -> MusicCopy.volumeLabel(model.music.volume)
    Row.SetAsHome -> Copy.setAsHome
    is Row.Backend -> row.name
    Row.AddFolder -> Copy.addFolder
    Row.Connect -> Copy.connectRomm
    is Row.SignOut -> "${Copy.signOut} · ${row.label}"
    Row.LaunchTarget -> if (model.launchOnBottom) Copy.launchOnBottom else Copy.launchOnTop
    is Row.Notice -> model.notices.firstOrNull { it.id == row.id }?.title ?: ""
}

fun displayOrder(model: PickerModel): List<Int> =
    if (model.order.size == model.count && model.count > 0) model.order else List(model.count) { it }

sealed interface Effect {
    data class Launch(val index: Int) : Effect
    data object CycleLaunchTarget : Effect
    data object AddFolder : Effect
    data object OpenConnect : Effect
    data class DialogChoice(val button: DialogButton, val kind: DialogKind) : Effect
    data class ActivateBackend(val name: String) : Effect
    data class ForgetCredentials(val pluginId: String) : Effect
    data object SaveRommToken : Effect
    data object RequestHome : Effect
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
    val homeRoleHeld: Boolean = false,
    val folderGrantPending: Boolean = true,
    val notices: List<FoldNotice> = emptyList(),
    val launchOnBottom: Boolean = false,
    val music: HomeMusicSetting = HomeMusicSetting(),
    val arranging: Boolean = false,
    val hold: Hold? = null,
    val order: List<Int> = emptyList(),
    val signedIn: List<SignedInBackend> = emptyList(),
    val connectOrigin: String = "",
    val connectIndex: Int = 0,
    val connectScreen: HostScreen? = null,
    val connectWarning: String? = null,
    val connectHint: String? = null,
    val reLoginPending: Boolean = false,
    val unavailable: Boolean = false,
)

fun reduce(
    model: PickerModel,
    meaning: Meaning,
    screen: HostScreen = HostScreen.Bottom,
): Pair<PickerModel, Effect?> {
    val coerced = model.copy(focus = coerce(model.focus, model))
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

private fun presentPanel(model: PickerModel, meaning: Meaning, screen: HostScreen): PickerModel {
    val side = if (meaning == Meaning.LeftPanel) Side.Left else Side.Right
    val open = model.panel
    if (open != null && open.side == side) {
        return model.copy(panel = null, focus = open.grid)
    }
    val grid = open?.grid ?: model.focus
    return model.copy(
        panel = SidePanel(
            side = side,
            screen = screen,
            level = PanelLevel.Root,
            index = 0,
            grid = grid,
        ),
    )
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
            model to Effect.Launch(model.focus.cellIndex)
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
        Meaning.MoveUp -> model.copy(panel = current.copy(index = (index - 1).coerceAtLeast(0))) to null
        Meaning.MoveDown -> model.copy(panel = current.copy(index = (index + 1).coerceAtMost(rows.lastIndex))) to null
        Meaning.MoveLeft, Meaning.MoveRight, Meaning.PageTowardStart, Meaning.PageTowardEnd ->
            model.copy(panel = current) to null
        Meaning.Back -> backPanel(model, current)
        Meaning.Activate -> activateRow(model, current, rows[index], screen)
        Meaning.LeftPanel, Meaning.RightPanel -> presentPanel(model, meaning, screen) to null
    }
}

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
            model.copy(panel = panel, themeIndex = (model.themeIndex + 1) % count) to null
        }
        Row.Primary -> model.copy(panel = panel, primaryIsTop = !model.primaryIsTop) to null
        Row.Arrange -> model.copy(
            panel = null,
            focus = panel.grid,
            arranging = true,
            hold = null,
            order = displayOrder(model),
        ) to null
        Row.Music -> model.copy(panel = panel, music = model.music.toggled()) to null
        Row.MusicVolume -> model.copy(panel = panel, music = model.music.stepped()) to null
        Row.SetAsHome -> model to Effect.RequestHome
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
    }
}

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
