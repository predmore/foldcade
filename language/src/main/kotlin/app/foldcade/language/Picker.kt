package app.foldcade.language

enum class HostScreen {
    Top,
    Bottom,
}

enum class Chrome {
    /** The R1 pill. */
    StatusCluster,

    /** The L1 pill. Up from the top row on the left half of the screen reaches it. */
    LeftIsland,
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

/** [depth] is how far in a level sits, so going in and coming back move opposite ways. [actsOnTile] marks menus whose header names the focused tile. */
enum class PanelLevel(val depth: Int, val actsOnTile: Boolean = false) {
    Root(0),

    /** One saved RomM server's edit and sign-out actions, opened from Settings. */
    RommConnection(1),

    /** Y's menu for the focused tile, in the left island. */
    Actions(1, actsOnTile = true),

    /** Add to: Home, then the collections the focused game can join, each checked while it is in it. */
    Collections(2, actsOnTile = true),

    /** L1's Edit home: the focused tile's actions, then the settings for the whole board. */
    EditHome(1, actsOnTile = true),
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
    /** The level the destination picker came from, so Back returns there. */
    val via: PanelLevel = PanelLevel.Actions,
    /** The saved RomM connection whose edit and sign-out actions are open. */
    val connection: SignedInBackend? = null,
    /** A connection opened from Settings returns to that page on Back. */
    val connectionSettings: SettingsPage? = null,
    /** An empty view opened Undo rather than a tile menu. Its rows do not depend on the covered grid. */
    val recoveryOnly: Boolean = false,
    /** The column of the tile the D-pad last left the tiles from, so up from the row under them returns there. */
    val tileColumn: Int? = null,
    /** L1's [tileColumn] while Edit home is open, so Back returns to the place tile it was left from. */
    val parentTileColumn: Int? = null,
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

enum class DialogKind {
    Home,
    Folder,
    Ok,
    ReLogin,
    UsageAccess,
    MissingPlayer,
    ClosePlayer,
    SaveFolder,

    /** Restart a game still open in its player. Not now leaves it paused. */
    RestartGame,

    /** Android's "Modify system settings" access, for the R1 brightness slider. */
    Brightness,

    /** Notification access, for R1's notices. */
    NoticeAccess,

    /** All of one Foldcade notice's text. */
    Notice,

    /** Settings › SteamGridDB with a key saved. Only Forget key forgets it. */
    ForgetArtKey,

    /** Offers to show Foldcade RetroArch's installed cores, once. Not now still starts the game. */
    RetroArchCores,

    /** Offers to add Switch keys and firmware from RomM. Not now still starts the game. */
    EdenFolder,

    /** Eden was given files. Continue opens its App info. Not now only closes. */
    EdenRestart,

    /** The picker was not left on Eden's root. Try again opens it again. Not now starts a held launch. */
    EdenWrongFolder,

    /** Something RetroArch needs before the game can start, such as a core. */
    RetroArchSetup,

    /** The code to type on a computer while Foldcade pairs with it. Not now stops. */
    PairHost,

    /** Stop listing a paired computer's games. Opens on Not now. */
    ForgetHost,

    /** How to point a player at the folder Foldcade hosts for its saves. Opens on Open. */
    HostedSaves,
}

enum class DialogButton {
    ChooseSaveFolder,
    PlayWithoutSyncing,
    SetUpSaves,
    UseAsHome,
    NotNow,
    ContinueGrant,
    Allow,
    Ok,
    CloseIt,
    ForgetKey,
    KeepKey,
    OpenRetroArch,
    Restart,
    ForgetHost,

    /** Opens the player [DialogState.subject] names. */
    OpenPlayer,
    TryAgain,
}

/** The words a dialog button shows. [subject] is [DialogState.subject], which names the player to open. */
fun dialogButtonLabel(button: DialogButton, subject: String? = null): String = when (button) {
    DialogButton.ChooseSaveFolder -> Copy.playerSavesUnset
    DialogButton.PlayWithoutSyncing -> Copy.playWithoutSyncing
    DialogButton.SetUpSaves -> Copy.setUp
    DialogButton.UseAsHome -> Copy.useAsHome
    DialogButton.NotNow -> Copy.notNow
    DialogButton.ContinueGrant -> Copy.continueGrant
    DialogButton.Allow -> Copy.allowUsage
    DialogButton.Ok -> Copy.ok
    DialogButton.CloseIt -> Copy.closeIt
    DialogButton.ForgetKey -> Copy.forgetKey
    DialogButton.KeepKey -> Copy.keepKey
    DialogButton.OpenRetroArch -> Copy.openRetroArch
    DialogButton.Restart -> Copy.restart
    DialogButton.ForgetHost -> Copy.forgetHost
    DialogButton.OpenPlayer -> openPlayerLabel(subject)
    DialogButton.TryAgain -> Copy.tryAgain
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
    /** What the dialog is about, such as the computer a pairing dialog names. */
    val subject: String? = null,
)

/** One of Moonlight's computers, for its Settings › Library switch. [key] is its lower-case UUID. */
data class StreamHostSetting(val key: String, val name: String, val paired: Boolean)

/** Foldcade shows [pin]; the user types it into Sunshine or Apollo on [name]. */
fun pairHostDialog(name: String, pin: String, key: String, screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.PairHost,
    title = "Enter $pin on $name",
    body = Copy.pairHostBody,
    buttons = listOf(DialogButton.NotNow),
    index = 0,
    safeIndex = 0,
    screen = screen,
    subject = key,
)

/** Forgetting opens on Not now, and Back keeps the pairing too. */
fun forgetHostDialog(name: String, key: String, screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.ForgetHost,
    title = "Forget $name?",
    body = Copy.forgetHostBody,
    buttons = listOf(DialogButton.ForgetHost, DialogButton.NotNow),
    index = 1,
    safeIndex = 1,
    screen = screen,
    subject = key,
)

/** Why a pairing with [name] did not finish. */
fun pairFailedDialog(name: String, outcome: PairFailure, screen: HostScreen): DialogState = playerNotice(
    title = when (outcome) {
        PairFailure.WrongPin -> Copy.pairWrongPinTitle
        PairFailure.Unreachable -> "$name didn’t answer"
        PairFailure.Refused -> "$name didn’t pair"
    },
    body = when (outcome) {
        PairFailure.WrongPin -> Copy.pairWrongPinBody
        PairFailure.Unreachable -> Copy.pairUnreachableBody
        PairFailure.Refused -> Copy.pairRefusedBody
    },
    screen = screen,
)

enum class PairFailure {
    WrongPin,
    Unreachable,
    Refused,
}

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

/** The other players that run a missing player's games, by name. Null when there are none. */
fun alsoRunsLine(names: List<String>): String? = when (names.size) {
    0 -> null
    1 -> "${names[0]} also runs these games."
    else -> "${names.dropLast(1).joinToString(", ")} and ${names.last()} also run these games."
}

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

/** Restarting loses what was not saved, so it opens on Not now, and Back keeps the game too. */
fun restartGameDialog(screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.RestartGame,
    title = Copy.restartTitle,
    body = Copy.restartBody,
    buttons = listOf(DialogButton.Restart, DialogButton.NotNow),
    index = 1,
    safeIndex = 1,
    screen = screen,
)

/** Forgetting the key opens on Keep key, and Back keeps it too. */
fun forgetArtKeyPrompt(screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.ForgetArtKey,
    title = Copy.forgetArtKeyTitle,
    body = Copy.forgetArtKeyBody,
    buttons = listOf(DialogButton.ForgetKey, DialogButton.KeepKey),
    index = 1,
    safeIndex = 1,
    screen = screen,
)

/**
 * The ask for a player's save folder. Choose folder opens the picker by default.
 * Play without syncing applies to this launch only. [playerName] names the player,
 * and [opensThere] says the picker opens at its folder. [wrong] is set after a
 * pick that was not the player's folder: what its folder holds.
 */
fun saveFolderDialog(
    screen: HostScreen,
    syncs: Boolean = false,
    wrong: Pair<String, Collection<String>>? = null,
    playerName: String? = null,
    opensThere: Boolean = false,
    needed: Boolean = false,
): DialogState = DialogState(
    kind = DialogKind.SaveFolder,
    title = playerName?.let(Copy::syncSavesTitle) ?: Copy.saveFolderTitle,
    body = wrong?.let { (player, markers) -> wrongSaveFolderBody(player, markers) }
        ?: listOfNotNull(
            when {
                needed -> Copy.saveFolderNeededBody
                syncs -> playerName?.let(Copy::syncSavesBody) ?: Copy.saveFolderSyncBody
                else -> Copy.saveFolderBody
            },
            Copy.saveFolderOpensThere.takeIf { opensThere },
        ).joinToString(" "),
    buttons = listOf(DialogButton.ChooseSaveFolder, if (needed) DialogButton.NotNow else DialogButton.PlayWithoutSyncing),
    index = 0,
    safeIndex = 1,
    screen = screen,
)

/**
 * How to point [playerName] at the folder Foldcade hosts,
 * once, in its own settings: [steps]. Before a launch, Set up opens the player
 * and Play without syncing starts this launch without remembering the choice.
 */
fun hostedSavesDialog(playerName: String, steps: String, beforeLaunch: Boolean, screen: HostScreen): DialogState =
    DialogState(
        kind = DialogKind.HostedSaves,
        title = Copy.syncSavesTitle(playerName),
        body = steps,
        buttons = if (beforeLaunch) listOf(DialogButton.SetUpSaves, DialogButton.PlayWithoutSyncing)
            else listOf(DialogButton.OpenPlayer, DialogButton.NotNow),
        index = 0,
        safeIndex = 1,
        screen = screen,
        subject = playerName,
    )

/**
 * How to point [playerName]'s memory card at a folder card, once, in its own
 * settings. Opens on Open, which opens the player. The row reads the same way
 * as a folder Foldcade hosts.
 */
fun saveSyncDialog(playerName: String, steps: String, screen: HostScreen, beforeLaunch: Boolean = false): DialogState =
    hostedSavesDialog(playerName, steps, beforeLaunch, screen)

/** What [playerName] needs once before it can start a game. Open opens the player; Not now closes. */
fun playerSetupDialog(playerName: String, steps: String, screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.HostedSaves,
    title = "${Copy.playerSetupTitle} $playerName",
    body = steps,
    buttons = listOf(DialogButton.OpenPlayer, DialogButton.NotNow),
    index = 0,
    safeIndex = 1,
    screen = screen,
    subject = playerName,
)

/** Opens on Continue: the picker is the only way Foldcade learns which cores RetroArch has. */
fun retroArchCoresDialog(screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.RetroArchCores,
    title = Copy.retroArchCoresTitle,
    body = Copy.retroArchCoresBody,
    buttons = listOf(DialogButton.ContinueGrant, DialogButton.NotNow),
    index = 0,
    safeIndex = 1,
    screen = screen,
)

/** Opens on Continue. Not now starts the game, and Settings can ask again. */
fun edenFolderDialog(screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.EdenFolder,
    title = Copy.edenFolderTitle,
    body = Copy.edenFolderBody,
    buttons = listOf(DialogButton.ContinueGrant, DialogButton.NotNow),
    index = 0,
    safeIndex = 1,
    screen = screen,
)

/**
 * Opens on Continue, which shows Eden's App info. Not now only closes.
 * Neither answer clears the pending restart.
 */
fun edenRestartDialog(screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.EdenRestart,
    title = Copy.edenRestartTitle,
    body = Copy.edenRestartBody,
    buttons = listOf(DialogButton.ContinueGrant, DialogButton.NotNow),
    index = 0,
    safeIndex = 1,
    screen = screen,
)

/** Opens on Try again, which returns to Eden's root. Not now starts a held launch. */
fun edenWrongFolderDialog(screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.EdenWrongFolder,
    title = Copy.edenWrongFolderTitle,
    body = Copy.edenWrongFolderBody,
    buttons = listOf(DialogButton.TryAgain, DialogButton.NotNow),
    index = 0,
    safeIndex = 1,
    screen = screen,
)

/** No core for [platformName] is installed. [coreName] is the one to install first. */
fun missingCoreDialog(platformName: String, coreName: String, screen: HostScreen): DialogState = retroArchSetupDialog(
    title = "${Copy.missingCoreTitle} $platformName",
    body = "${Copy.missingCoreBodyStart} $coreName${Copy.missingCoreBodyEnd}",
    screen = screen,
)

/** RetroArch has not been allowed to read the device's storage. */
fun retroArchStorageDialog(screen: HostScreen): DialogState =
    retroArchSetupDialog(Copy.retroArchStorageTitle, Copy.retroArchStorageBody, screen)

/** A Google Play RetroArch, which cannot read storage at all. */
fun retroArchStoreBuildDialog(screen: HostScreen): DialogState =
    retroArchSetupDialog(Copy.retroArchStorageTitle, Copy.retroArchStoreBuildBody, screen)

private fun retroArchSetupDialog(title: String, body: String, screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.RetroArchSetup,
    title = title,
    body = body,
    buttons = listOf(DialogButton.OpenRetroArch, DialogButton.Ok),
    index = 0,
    safeIndex = 1,
    screen = screen,
)

/** A player that opens games by path, and this game's file has no path it can read. */
fun noSharedPathDialog(playerName: String, screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.Ok,
    title = "$playerName ${Copy.noSharedPathTitle}",
    body = Copy.noSharedPathBody,
    buttons = listOf(DialogButton.Ok),
    index = 0,
    safeIndex = 0,
    screen = screen,
)

/** The label of [DialogButton.OpenPlayer] on a dialog about [playerName]. */
fun openPlayerLabel(playerName: String?): String = listOfNotNull(Copy.openPlayer, playerName).joinToString(" ")

/** Why a picked folder was not kept: it holds none of what [playerName]'s folder holds. */
fun wrongSaveFolderBody(playerName: String, markers: Collection<String>): String =
    "That folder doesn’t hold ${markers.sorted().joinToString(" or ")}, so it isn’t $playerName’s. " +
        "Choose the folder that holds them. If $playerName hasn’t been opened yet, open it once first."

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
        "forget-art-key" -> forgetArtKeyPrompt(screen)
        "retroarch-cores" -> retroArchCoresDialog(screen)
        "eden-folder" -> edenFolderDialog(screen)
        "eden-restart" -> edenRestartDialog(screen)
        "eden-wrong-folder" -> edenWrongFolderDialog(screen)
        "missing-core" -> missingCoreDialog("Game Boy Advance", "mGBA", screen)
        "retroarch-storage" -> retroArchStorageDialog(screen)
        else -> return null
    }
    val last = dialog.buttons.lastIndex.coerceAtLeast(0)
    return dialog.copy(index = index.coerceIn(0, last))
}

data class Hold(
    val index: Int,
    val origin: Int,
    val orderBefore: List<Int>,
)

sealed interface Row {
    data object IconTheme : Row
    data object Theme : Row
    data object Background : Row
    data object MotionSpeed : Row
    data object UsageAccess : Row
    data object Primary : Row
    data object Order : Row
    data object Music : Row
    data object MusicTrack : Row
    data object MusicVolume : Row
    data object SetAsHome : Row
    data object AndroidSettings : Row
    data object DefaultHomeApp : Row
    data object AddFolder : Row

    /** Reads every library again now. The same sync runs on its own while Home is in front. */
    data object SyncLibrary : Row
    data class RommConnection(val pluginId: String, val label: String) : Row
    data class EditRomm(val pluginId: String) : Row

    /** Add a server, whether it is the first or an additional connection. */
    data object AddRomm : Row
    data class Notice(val id: String) : Row

    /** Shown in R1 until Foldcade may read notifications. A asks. */
    data object NoticeAccess : Row

    /** Under two or more notices Y could remove. */
    data object ClearNotices : Row
    data class SignOut(val pluginId: String, val label: String) : Row
    data class ForgetFolder(val uri: String, val label: String) : Row
    data class PlayerSave(val playerId: String) : Row
    /** The folder Foldcade hosts for a player's saves, and whether the player has it. */
    data class HostedSaves(val playerId: String) : Row

    /** The player's memory card: Syncing, or Set up. */
    data class SaveSync(val playerId: String) : Row

    /** One-time setup in the player, such as firmware, before a game can start. */
    data class PlayerSetup(val playerId: String) : Row

    /** Whether Foldcade can see RetroArch's cores. A shows them. */
    data object RetroArchCores : Row

    /** Whether Foldcade can add Eden's keys and firmware from RomM. A opens Eden's folder. */
    data object EdenFolder : Row
    data object AndroidGames : Row
    data object Apps : Row
    /** Edit home's way into the one Hidden place: hidden games and apps, and groups taken off Home. */
    data object Hidden : Row
    data object AllLibrary : Row
    data object AddNewGames : Row

    /** Edit home's switch for the Recently played tile. */
    data object RecentOnHome : Row
    data object Artwork : Row
    data object ArtKey : Row

    /** A switch for the games of one of Moonlight's computers, [StreamHostSetting.key]. On pairs; off forgets. */
    data class StreamHost(val key: String) : Row
    /** An installed app trades Android Games for Android Apps, or back. */
    data object MoveApp : Row

    /** Hides the game everywhere on Home. All and Find still list it, and Hidden shows it again. */
    data object Hide : Row
    data object ChooseArt : Row
    data object MoveTile : Row

    /** Closes the gaps on the board on screen. */
    data object Tidy : Row

    /** ‹ Custom › and the orders a folder can show instead ([TileActions.sort]). */
    data object SortFolder : Row

    /** Closes Find or All and lands on the tile where the game, or the group, lives on Home. */
    data object ShowOnHome : Row

    /** Opens [PanelLevel.Collections]. */
    data object AddToCollection : Row

    /** One choice in Add to, Home or a collection. A toggles the focused game in it and the menu stays open. */
    data class Collection(val folderId: String, val label: String) : Row
    data object SelectTile : Row
    data object NewCollection : Row
    data object RenameFolder : Row
    data object FolderIcon : Row
    data object RemoveTile : Row
    data object Undo : Row
    data object ShowHidden : Row
    data object DeleteShortcut : Row
    data object RemoveDownload : Row

    /** A game still open in its player starts over. */
    data object Saves : Row
    data object RestartGame : Row

    /** X on a session in Play activity's list: held, it takes the session out. Not a menu row. */
    data object RemoveSession : Row
    data class QuickTile(val setting: QuickSetting) : Row

    /** The R1 slider for both screens' brightness. */
    data object Brightness : Row
    data object ButtonLabels : Row
    data object Licenses : Row

    /** A tile on a Settings shelf: an emulator, a library source, or Add. A opens [page] inside the card. */
    data class SettingsTile(val page: SettingsInner) : Row

    /** About's version. A copies one line of debug info for a bug report. */
    data object Version : Row
    data object Settings : Row

    /** L1's first row: the past week's play as bars. A opens the Play activity page. */
    data object PlayActivity : Row

    /** Back to the home board's top level, from a folder, All, or a shelf. */
    data object Home : Row

    /** Opens [PanelLevel.EditHome]. */
    data object EditHome : Row
}

/** The R1 quick settings and the L1 places are rows of round buttons. Text rows around them stay full width. */
const val quickTileColumns = 4

/** L1's places to go. They sit side by side as round tiles, as R1's Android screens do. */
private val placeRows: Set<Row> = setOf(Row.Home, Row.AllLibrary, Row.AndroidGames, Row.Apps)

/**
 * What makes a tile the user's own, in the order Y's menu offers them. The first
 * [quickTileColumns] of them lead the menu as round tiles; any past those are rows.
 */
val verbRows: List<Row> = listOf(
    Row.MoveTile, Row.AddToCollection, Row.ChooseArt, Row.Saves, Row.RenameFolder, Row.FolderIcon, Row.NewCollection,
)

/**
 * The rows [rows] draws as round tiles, side by side in lines of [quickTileColumns]: R1's
 * Android screens, L1's places, or the verbs that lead a tile's menu, under Undo when it is
 * there. Empty when the page has none. Verbs lead only a tile's menu, so a verb further down
 * a page, such as Add to's New collection, stays a row.
 */
fun tileBlock(rows: List<Row>): IntRange {
    val start = rows.indexOfFirst { it is Row.QuickTile || it in placeRows }
    if (start >= 0) {
        val end = (start until rows.size).firstOrNull { rows[it] !is Row.QuickTile && rows[it] !in placeRows } ?: rows.size
        return start until end
    }
    val lead = if (rows.firstOrNull() == Row.Undo) 1 else 0
    val verbs = rows.drop(lead).take(quickTileColumns).takeWhile { it in verbRows }.size
    return lead until lead + verbs
}

/** The word under a tile. A tile has less room than a row, so it says less. */
fun tileLabel(row: Row): String? = when (row) {
    Row.Home -> Copy.home
    Row.AllLibrary -> Copy.tileAll
    Row.AndroidGames -> Copy.tileGames
    Row.Apps -> Copy.apps
    Row.MoveTile -> Copy.move
    Row.AddToCollection -> Copy.addTo
    Row.ChooseArt -> ArtCopy.art
    Row.Saves -> SavePickerCopy.title
    Row.RenameFolder -> Copy.rename
    Row.FolderIcon -> Copy.icon
    Row.NewCollection -> Copy.tileNew
    is Row.QuickTile -> when (row.setting) {
        QuickSetting.Wifi -> Copy.wifi
        QuickSetting.Bluetooth -> Copy.bluetooth
        QuickSetting.Battery -> Copy.battery
        QuickSetting.Settings -> SettingsCopy.title
    }
    else -> null
}

/**
 * The Android screens R1 opens. Only what a Thor owner reaches for: the slider
 * replaces Display, the hardware buttons set the volume, and Foldcade's own
 * settings are on L1.
 */
enum class QuickSetting {
    Wifi,
    Bluetooth,
    Battery,

    /** Android's Settings app. */
    Settings,
}

fun quickSettings(): List<QuickSetting> = listOf(
    QuickSetting.Wifi,
    QuickSetting.Bluetooth,
    QuickSetting.Battery,
    QuickSetting.Settings,
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
    QuickSetting.Battery -> AndroidSetting.Battery
    QuickSetting.Settings -> AndroidSetting.Settings
}

data class PlayerSaveSetting(
    val playerId: String,
    val label: String,
    val chosen: Boolean,
    /** What the folder Foldcade hosts for the player holds, or null when it hosts none. */
    val hostedTitle: String? = null,
    /** True when the player has been given the folder Foldcade hosts. */
    val hostedInUse: Boolean = false,
    /** True when the player's memory card syncs. False when [saveSyncSteps] says how to start. */
    val saveSyncing: Boolean = false,
    /** What the user does once, in the player, when the memory card is not syncing. */
    val saveSyncSteps: String? = null,
    /** What the user does once in the player before it can start a game, or null when it can. */
    val setupSteps: String? = null,
    /** False until the host knows one of this player's packages is installed. */
    val installed: Boolean = false,
    /** The player's installed packages. The first one's art is its Settings tile. */
    val packages: List<String> = emptyList(),
)

/**
 * RetroArch, when it is installed. [cores] is how many cores Foldcade can see,
 * or null when it cannot see them.
 */
data class RetroArchSetting(
    val cores: Int?,
    /** The RetroArch whose cores those are, which ties the row to that player's tile. */
    val packageName: String? = null,
)

/** Eden, when it is installed. [fromRomm] is true while Foldcade holds Eden's folder. */
data class EdenSetting(
    val fromRomm: Boolean,
    /** The installed Eden, which ties the row to that player's tile. */
    val packageName: String? = null,
)

/**
 * L1 is Foldcade's own drawer: the past week's play, where to go, editing home,
 * and Foldcade's settings. Device controls are on R1. Every other setting,
 * the library sources included, lives on the Settings page, so this stays
 * short enough to fit the island.
 */
fun leftRows(): List<Row> = listOf(
    Row.PlayActivity,
    Row.Home,
    Row.AllLibrary,
    Row.AndroidGames,
    Row.Apps,
    Row.EditHome,
    Row.Settings,
)

/**
 * The focused tile's actions, the same ones Y offers, then what applies to the
 * whole board: Tidy while it has gaps, the Recently played tile, whether new
 * games join it, and what is hidden from it.
 */
fun editHomeRows(model: PickerModel): List<Row> {
    val tile = actionRows(model.tileActions)
    val tidy = if (model.tileActions?.tidyBoard == true && Row.Tidy !in tile) listOf(Row.Tidy) else emptyList()
    return tile + tidy + listOf(Row.RecentOnHome, Row.AddNewGames, Row.Hidden)
}

data class SignedInBackend(
    val pluginId: String,
    val label: String,
)

/** One folder the library reads. [uri] is its document tree; [label] its own name. */
data class LibraryFolder(
    val uri: String,
    val label: String,
)

enum class ConnectField {
    Origin,
    Token,
    Save,
    Retry,
}

/** What the connect form saves: a RomM server and token, or a SteamGridDB key. */
enum class ConnectKind {
    Romm,
    ArtKey,
}

fun connectFields(kind: ConnectKind = ConnectKind.Romm, retry: Boolean = false): List<ConnectField> = when (kind) {
    ConnectKind.Romm -> listOf(ConnectField.Origin, ConnectField.Token, ConnectField.Save) +
        if (retry) listOf(ConnectField.Retry) else emptyList()
    ConnectKind.ArtKey -> listOf(ConnectField.Token, ConnectField.Save)
}

/**
 * R1: Android's screens on top, then brightness, then the notices, newest first.
 * R1 opens on brightness ([rightStart]), so R1 then left or right changes it.
 * Notification access is asked from Settings, not here.
 */
fun rightRows(notices: List<FoldNotice>): List<Row> = buildList {
    quickSettings().forEach { add(Row.QuickTile(it)) }
    add(Row.Brightness)
    notices.forEach { add(Row.Notice(it.id)) }
    if (notices.count { it.dismissable } >= 2) add(Row.ClearNotices)
}

/** Where R1 opens: the brightness slider, under the tiles. */
fun rightStart(): Int = quickSettings().size

/** A choice Add to lists, Home ([HOME_ROOT]) or a collection, and whether the focused game is in it. */
data class CollectionChoice(val folderId: String, val name: String, val member: Boolean, val some: Boolean = false)

/**
 * What Y offers for the focused tile on the home board or in All.
 * The menu lists only what applies, so an empty one does not open.
 */
data class TileActions(
    val select: Boolean = false,
    val bulk: List<String>? = null,
    /** The game or group Show on Home goes to. Null leaves the row out: it is nowhere on Home. */
    val showOnHome: String? = null,
    /** The menu still targets a game that left the focused slot. Slot actions no longer apply. */
    val kept: Boolean = false,
    val undo: Boolean = false,
    val show: Boolean = false,
    val deleteShortcut: Boolean = false,
    val art: Boolean = false,
    /** Pick the tile up and walk it with the D-pad. */
    val move: Boolean = false,
    /** Add to: Home, then every collection on Home. Null leaves the row out. */
    val collections: List<CollectionChoice>? = null,
    /** A collection, on a tile with no game for Add to: an empty slot, or the All tile. */
    val newCollection: Boolean = false,
    val rename: Boolean = false,
    /** A collection can wear an icon the user chooses. */
    val icon: Boolean = false,
    /** What Remove does to the tile. Null leaves the row out. */
    val remove: RemoveVerb? = null,
    /** The game can be hidden from Home. */
    val hide: Boolean = false,
    /** The Android folder an installed app would move to, Games or Apps. Null leaves the row out. */
    val moveTo: String? = null,
    /** A server game downloaded to this device can be deleted again. */
    val removeDownload: Boolean = false,
    /** The game is still open in its player, so it can start over. */
    val restart: Boolean = false,
    val saves: Boolean = false,
    /** The game these rows act on. Null for a tile that is not a game, or one off the home grid. */
    val game: String? = null,
    /** Tidy, on an empty slot of a board with gaps to close. */
    val tidy: Boolean = false,
    /** The board on screen has gaps to close, so Edit home offers Tidy whatever the tile. */
    val tidyBoard: Boolean = false,
    /** The folder Sort orders, the open one or the folder tile, and its order now. Null leaves the row out. */
    val sort: FolderSortChoice? = null,
)

/** A folder's Sort row: which folder it orders, and how it orders it now. */
data class FolderSortChoice(val folderId: String, val sort: FolderSort)

/**
 * Y's menu for the tile. Undo leads when there is one. Then come the tile's own verbs
 * ([verbRows]), which draw as round tiles, and under them the rest as rows. Add to holds
 * every place a game can be put, Home first, and New collection at its end, so a game's
 * menu has one tile for all of them. Remove takes the tile off the spot it is in, and Hide
 * is its own row, after it. Sort, for the whole folder rather than the tile, comes last.
 */
fun actionRows(tile: TileActions?): List<Row> = buildList {
    if (tile == null) return@buildList
    if (tile.bulk != null) {
        if (tile.collections != null) add(Row.AddToCollection)
        if (tile.newCollection && tile.collections == null) add(Row.NewCollection)
        if (tile.remove != null) add(Row.RemoveTile)
        if (tile.hide) add(Row.Hide)
        return@buildList
    }
    if (tile.undo) add(Row.Undo)
    if (tile.move) add(Row.MoveTile)
    if (tile.collections != null) add(Row.AddToCollection)
    if (tile.art) add(Row.ChooseArt)
    if (tile.saves) add(Row.Saves)
    if (tile.rename) add(Row.RenameFolder)
    if (tile.icon) add(Row.FolderIcon)
    if (tile.newCollection && tile.collections == null) add(Row.NewCollection)
    if (tile.show) add(Row.ShowHidden)
    if (tile.showOnHome != null) add(Row.ShowOnHome)
    if (tile.moveTo != null) add(Row.MoveApp)
    if (tile.select) add(Row.SelectTile)
    if (tile.tidy) add(Row.Tidy)
    if (tile.remove != null) add(Row.RemoveTile)
    if (tile.restart) add(Row.RestartGame)
    if (tile.hide) add(Row.Hide)
    if (tile.removeDownload) add(Row.RemoveDownload)
    if (tile.deleteShortcut) add(Row.DeleteShortcut)
    if (tile.sort != null) add(Row.SortFolder)
}

/**
 * The hint row for the focused menu row when its keys are not just A and B:
 * the slider names the D-pad, and a notice names Y. Null leaves the usual row.
 */
fun panelHints(model: PickerModel): List<HintKey>? {
    val panel = model.panel ?: return null
    if (model.dialog != null) return null
    val back = HintKey(promptKeyOf(model.faceMap.backKey) ?: PromptKey.FaceB, Copy.back)
    return when (val row = panelRows(panel, model).getOrNull(panel.index)) {
        Row.RemoveDownload, Row.DeleteShortcut -> listOf(HintKey(model.deleteHold?.key ?: promptKeyOf(model.faceMap.confirmKey) ?: PromptKey.FaceA, ""), back)
        Row.Brightness -> if (model.brightnessAllowed) listOf(HintKey(PromptKey.DpadSides, BrightnessCopy.adjust), back) else null
        is Row.Notice -> {
            val notice = model.notices.firstOrNull { it.id == row.id } ?: return null
            buildList {
                val roles = actionFaces(model.faceMap)
                if (notice.savePlayerId != null || notice.settingsCategory != null) {
                    add(HintKey(promptKeyOf(model.faceMap.confirmKey) ?: PromptKey.FaceA, Copy.setUp))
                }
                if (notice.openable) {
                    add(HintKey(promptKeyOf(model.faceMap.confirmKey) ?: PromptKey.FaceA, Copy.hintTop))
                    add(HintKey(roles.bottomGlyph, Copy.hintBottom))
                }
                if (notice.dismissable) add(HintKey(roles.optionsGlyph, NoticeCopy.dismiss))
                add(back)
            }
        }
        else -> null
    }
}

/** Add to's rows: each collection, then one that makes a new collection holding the game. */
fun collectionRows(tile: TileActions?): List<Row> {
    val collections = tile?.collections ?: return emptyList()
    return collections.map { Row.Collection(it.folderId, it.name) } + Row.NewCollection
}

/** Catalog reports keep rows stable; removing a game from its slot discards that slot's actions. */
data class MenuRows(val page: SidePanel, val rows: List<Row>)

private fun SidePanel.page() = copy(index = 0, retiring = false, pendingSide = null, tileColumn = null, parentTileColumn = null)

fun retainMenuRows(previous: PickerModel, next: PickerModel): PickerModel {
    val panel = next.panel ?: return next.copy(menuRows = emptyList())
    // Sorting the folder changes what its menu offers: Move and Tidy mean something only in Custom.
    val was = previous.tileActions?.sort
    val now = next.tileActions?.sort
    val resorted = was != null && now != null && was.folderId == now.folderId && was.sort != now.sort
    val retained = (if (previous.panel?.side == panel.side && !resorted) previous.menuRows else emptyList())
        .map { it.copy(rows = validMenuRows(it.rows, next)) }
    val page = panel.page()
    val pages = if (retained.any { it.page == page }) retained else
        retained + MenuRows(page, livePanelRows(panel, next))
    val stable = next.copy(menuRows = pages)
    val oldPanel = previous.panel
    if (oldPanel != null && oldPanel.page() == page) {
        val oldRows = panelRows(oldPanel, previous)
        val rows = panelRows(panel, stable)
        if (oldRows != rows) {
            val selected = oldRows.getOrNull(oldPanel.index)
            val index = rows.indexOf(selected).takeIf { it >= 0 }
                ?: panel.index.coerceIn(0, rows.lastIndex.coerceAtLeast(0))
            return stable.copy(panel = panel.copy(index = index))
        }
    }
    return stable
}

private fun validMenuRows(rows: List<Row>, model: PickerModel): List<Row> = rows.filterNot {
    (it == Row.Undo && model.tileActions?.undo != true) ||
        (model.tileActions?.kept == true && (it == Row.SelectTile || it == Row.MoveTile || it == Row.RemoveTile))
}

fun panelRows(panel: SidePanel, model: PickerModel): List<Row> = validMenuRows(
    model.menuRows.firstOrNull { it.page == panel.page() }?.rows ?: livePanelRows(panel, model), model)

private fun livePanelRows(panel: SidePanel, model: PickerModel): List<Row> = when (panel.level) {
    PanelLevel.Actions -> if (panel.recoveryOnly) {
        if (model.tileActions?.undo == true) listOf(Row.Undo) else emptyList()
    } else actionRows(model.tileActions)
    PanelLevel.Collections -> collectionRows(model.tileActions)
    PanelLevel.RommConnection -> panel.connection?.let {
        listOf(Row.EditRomm(it.pluginId), Row.SignOut(it.pluginId, it.label))
    }.orEmpty()
    PanelLevel.EditHome -> editHomeRows(model)
    PanelLevel.Root -> when (panel.side) {
        Side.Left -> leftRows()
        Side.Right -> rightRows(model.notices)
    }
}

fun backgroundLabel(motion: BackgroundMotion): String = when (motion) {
    BackgroundMotion.Ribbons -> Copy.ribbons
    BackgroundMotion.Embers -> Copy.embers
    BackgroundMotion.Static -> Copy.motionStatic
    BackgroundMotion.Off -> Copy.motionOff
    else -> motion.wire
}

fun speedLabel(speed: MotionSpeed): String = when (speed) {
    MotionSpeed.Slow -> Copy.speedSlow
    MotionSpeed.Slower -> Copy.speedSlower
    MotionSpeed.Off -> Copy.motionOff
}

/** Label on the left, current value on the right. A null value is a single action row. */
data class RowText(val label: String, val value: String? = null)

fun rowText(row: Row, model: PickerModel): RowText = when (row) {
    Row.PlayActivity -> RowText(PlayCopy.activity)
    Row.Home -> RowText(Copy.home)
    Row.EditHome -> RowText(Copy.editHome)
    Row.Settings -> RowText(SettingsCopy.title)
    Row.IconTheme -> RowText(Copy.icons, model.iconThemeIndex?.let { model.themes.getOrNull(it) } ?: Copy.matchTheme)
    Row.Theme -> RowText(Copy.theme, model.themes.getOrElse(model.themeIndex) { Copy.builtIn })
    Row.Background -> RowText(Copy.background, model.backgroundChoices.firstOrNull { it.motion == model.backgroundMotion }?.name ?: backgroundLabel(model.backgroundMotion))
    Row.MotionSpeed -> RowText(Copy.motion, speedLabel(model.motionSpeed))
    Row.UsageAccess -> RowText(Copy.playTime, if (model.usageGranted) Copy.playTimeEverywhere else Copy.playTimeHere)
    Row.Primary -> RowText(Copy.primaryPanel, if (model.primaryIsTop) Copy.top else Copy.bottom)
    Row.Order -> RowText(Copy.sortAll, if (model.sort == LibrarySort.RecentlyPlayed) Copy.sortRecent else Copy.sortListed)
    Row.SortFolder -> RowText(Copy.sort, folderSortLabel(model.tileActions?.sort?.sort ?: FolderSort.Custom))
    Row.Music -> RowText(MusicCopy.row, if (model.music.enabled) MusicCopy.on else MusicCopy.off)
    Row.MusicTrack -> RowText(MusicCopy.track, model.trackTitle)
    Row.MusicVolume -> RowText(MusicCopy.volume, MusicCopy.volumeLabel(model.music.volume).substringAfter("  "))
    Row.SetAsHome -> RowText(Copy.setAsHome)
    Row.AndroidSettings -> RowText(Copy.androidSettings)
    Row.Licenses -> RowText(LicenseCopy.row)
    is Row.SettingsTile -> RowText(settingsInnerTitle(row.page, model))
    Row.Version -> RowText(AboutCopy.version, if (model.versionCopied) AboutCopy.copied else model.appVersion)
    Row.DefaultHomeApp -> RowText(Copy.defaultHomeApp)
    is Row.QuickTile -> RowText(
        when (row.setting) {
            QuickSetting.Wifi -> Copy.wifi
            QuickSetting.Bluetooth -> Copy.bluetooth
            QuickSetting.Battery -> Copy.battery
            QuickSetting.Settings -> Copy.androidSettings
        },
    )
    Row.Brightness -> RowText(
        BrightnessCopy.row,
        if (model.brightnessAllowed) BrightnessCopy.percent(model.brightness) else BrightnessCopy.allow,
    )
    Row.AddFolder -> RowText(Copy.addFolder)
    Row.SyncLibrary -> RowText(Copy.syncGames, if (model.librarySyncing) Copy.syncing else null)
    is Row.RommConnection -> RowText(row.label)
    is Row.EditRomm -> RowText(Copy.editRommConnection)
    Row.AddRomm -> RowText(Copy.addRommServer)
    // Both live on a page that already names the connection or the folder.
    is Row.SignOut -> RowText(Copy.signOut)
    is Row.ForgetFolder -> RowText(Copy.remove)
    is Row.Notice -> RowText(model.notices.firstOrNull { it.id == row.id }?.title ?: "")
    Row.NoticeAccess -> RowText(NoticeCopy.access, NoticeCopy.accessValue)
    Row.ClearNotices -> RowText(NoticeCopy.clearAll)
    is Row.PlayerSave -> {
        val setting = model.playerSaves.firstOrNull { it.playerId == row.playerId }
        val chosen = when {
            setting?.chosen == true -> Copy.playerSavesSet
            setting?.installed != true || setting.hostedTitle != null -> Copy.choose
            else -> Copy.notSyncing
        }
        RowText(Copy.saveFolder, chosen)
    }
    is Row.HostedSaves -> {
        val setting = model.playerSaves.firstOrNull { it.playerId == row.playerId }
        val inUse = when {
            setting?.hostedInUse == true -> Copy.hostedSavesSet
            setting?.installed == true -> Copy.notSyncing
            else -> Copy.hostedSavesUnset
        }
        RowText(setting?.hostedTitle?.let { pageLabel(it, setting.label) } ?: row.playerId, inUse)
    }
    is Row.SaveSync -> {
        val setting = model.playerSaves.firstOrNull { it.playerId == row.playerId }
        val state = when {
            setting?.saveSyncing == true -> Copy.hostedSavesSet
            setting?.installed == true -> Copy.notSyncing
            else -> Copy.hostedSavesUnset
        }
        RowText(Copy.memoryCard, state)
    }
    is Row.PlayerSetup -> {
        val setting = model.playerSaves.firstOrNull { it.playerId == row.playerId }
        RowText(Copy.setup, if (setting?.installed == true) Copy.notSyncing else Copy.hostedSavesUnset)
    }
    Row.RetroArchCores -> RowText(
        Copy.retroArchCores,
        model.retroArch?.cores?.let { if (it == 1) Copy.oneCore else "$it ${Copy.cores}" } ?: Copy.showCores,
    )
    Row.EdenFolder -> RowText(
        Copy.edenKeys,
        if (model.eden?.fromRomm == true) Copy.edenFromRomm else Copy.setUp,
    )
    Row.AndroidGames -> RowText(Copy.androidGames)
    Row.Apps -> RowText(Copy.apps)
    Row.Hidden -> RowText(Copy.hiddenAppsRow)
    Row.AllLibrary -> RowText(Copy.allLibrary)
    Row.AddNewGames -> RowText(Copy.addNewGames, if (model.addNewToHome) Copy.on else Copy.off)
    Row.RecentOnHome -> RowText(Copy.recentlyPlayed, if (model.recentOnHome) Copy.on else Copy.off)
    Row.Artwork -> RowText(Copy.artwork, if (model.artwork) Copy.on else Copy.off)
    Row.ArtKey -> RowText(Copy.steamGridDb, if (model.artKeySaved) Copy.forgetKey else Copy.addKey)
    is Row.StreamHost -> model.streamHosts.firstOrNull { it.key == row.key }.let { host ->
        RowText(Copy.hostGames, if (host?.paired == true) Copy.on else Copy.off)
    }
    Row.MoveApp -> RowText(if (model.tileActions?.moveTo == HOME_ANDROID_APPS) Copy.moveToApps else Copy.moveToGames)
    Row.Hide -> RowText(Copy.hideApp)
    Row.ChooseArt -> RowText(ArtCopy.art)
    Row.MoveTile -> RowText(Copy.move)
    Row.Tidy -> RowText(Copy.tidy)
    Row.ShowOnHome -> RowText(FindCopy.showOnHome)
    Row.AddToCollection -> RowText(Copy.addTo)
    is Row.Collection -> RowText(row.label)
    Row.SelectTile -> RowText(SelectionCopy.select)
    Row.NewCollection -> RowText(Copy.newCollection)
    Row.RenameFolder -> RowText(Copy.rename)
    Row.FolderIcon -> RowText(Copy.icon)
    Row.RemoveTile -> RowText(
        when (model.tileActions?.remove) {
            RemoveVerb.Unpin -> Copy.unpin
            RemoveVerb.Remove, null -> Copy.remove
        },
    )
    Row.Undo -> RowText(Copy.undo)
    Row.ShowHidden -> RowText(Copy.showApp)
    Row.DeleteShortcut -> RowText(Copy.deleteShortcut)
    Row.RemoveDownload -> RowText(Copy.deleteDownload)
    Row.Saves -> RowText(SavePickerCopy.title)
    Row.RestartGame -> RowText(Copy.restart)
    Row.RemoveSession -> RowText(PlayCopy.remove)
    Row.ButtonLabels -> RowText(
        Copy.buttonLabels,
        when {
            model.capturingConfirm -> Copy.pressConfirm
            model.offerButtonLabels -> Copy.setUp
            else -> "${Copy.confirm}: ${letterOfKey(model.faceMap.confirmKey)?.name.orEmpty()}"
        },
    )
}

/** A row on a page headed by [name] drops the name it starts with: "DuckStation memory cards" reads "Memory cards". */
private fun pageLabel(title: String, name: String): String =
    title.removePrefix("$name ").takeIf { it != title }?.replaceFirstChar { it.uppercaseChar() } ?: title

fun rowLabel(row: Row, model: PickerModel): String {
    val text = rowText(row, model)
    val value = text.value
    return if (value.isNullOrEmpty()) text.label else "${text.label}  $value"
}

fun displayOrder(model: PickerModel): List<Int> {
    if (model.count <= 0) return emptyList()
    if (model.arranging && model.order.size == model.count) return model.order
    if (model.homeGrid == HomeGrid.StandIns && model.sort == LibrarySort.RecentlyPlayed && model.recentFirst.size == model.count) {
        return model.recentFirst
    }
    if (model.order.size == model.count) return model.order
    return List(model.count) { it }
}

sealed interface Effect {
    data class OpenSaves(val gameId: String) : Effect
    data class LoadSavePicker(val target: SavePickerTarget) : Effect
    data class FixSavePicker(val target: SavePickerTarget) : Effect
    data class SyncSavePicker(val target: SavePickerTarget) : Effect
    data class PlayPickedSave(val target: SavePickerTarget, val saveId: String) : Effect
    data class CopyPickedSave(val target: SavePickerTarget, val saveId: String, val destination: SaveDestination) : Effect
    /** Launch the game at [index], on the bottom screen when [onBottom] (X), else the top (A). */
    data class Launch(val index: Int, val onBottom: Boolean = false) : Effect
    data class OpenPlatform(val index: Int) : Effect
    data object LeavePlatform : Effect
    data object TryAgain : Effect
    data object AddFolder : Effect
    data object SyncLibrary : Effect
    data object EditRommConnection : Effect

    /** The connect form for a new server. */
    data object OpenAddRomm : Effect
    data object OpenArtKey : Effect
    data object SaveArtKey : Effect
    data object RetryRommDeviceCode : Effect
    data class DialogChoice(val button: DialogButton, val kind: DialogKind, val subject: String? = null) : Effect

    /** Pair with Moonlight's computer [key], so its games list themselves. */
    data class PairStreamHost(val key: String) : Effect
    data class ForgetCredentials(val pluginId: String) : Effect
    data class ForgetFolder(val uri: String) : Effect
    data object SaveRommToken : Effect

    /** A on a text row of the connect form. The field takes focus and raises the keyboard. */
    data object EditField : Effect
    data object RequestHome : Effect
    data class ChoosePlayerSave(val playerId: String) : Effect
    /** How to point the player at the folder Foldcade hosts for its saves. */
    data class SetUpHostedSaves(val playerId: String) : Effect

    /** How to switch the player's memory card so its saves can sync. */
    data class ShowSaveSync(val playerId: String) : Effect

    /** What the player needs once before it can start a game. */
    data class ShowPlayerSetup(val playerId: String) : Effect

    /** Opens the player, as Open on the memory-card dialog does. */
    data class OpenPlayer(val playerId: String) : Effect

    /** Settings › RetroArch cores: the picker that lets Foldcade see them. */
    data object ShowRetroArchCores : Effect

    /** Settings › Eden keys and firmware: the picker for Eden's folder. */
    data object ShowEdenFolder : Effect
    /** Move the installed app [gameId] to the Android folder [folderId]. */
    data class MoveApp(val gameId: String?, val folderId: String) : Effect

    /** Hide [gameId] from Home, or the focused game when null. */
    data class Hide(val gameId: String? = null) : Effect

    /** Go to the board's folder [folderId] and open it, as L1's Games and Apps do. */
    data class OpenFolder(val folderId: String) : Effect
    data class OpenAndroidSetting(val setting: AndroidSetting) : Effect
    data object OpenLicenses : Effect
    data object OpenThemes : Effect

    /** Copy Foldcade's version and the device's to the clipboard, for a bug report. */
    data object CopyDebugInfo : Effect
    data object DismissButtonLabels : Effect
    /** Choose art for [gameId], or the focused game when null. */
    data class ChooseArt(val gameId: String? = null) : Effect
    /** Start checking games in the current view, with this game already checked. */
    data class SelectTile(val gameId: String) : Effect
    /** The checked ids in view order, retained by the menu rather than read from grid focus. */
    data class Bulk(val action: BulkAction, val ids: List<String>, val collectionId: String? = null) : Effect
    /** Pick up the focused tile to move it. */
    data object MoveTile : Effect

    /** Close the gaps on the board on screen. */
    data object Tidy : Effect

    /** Step folder [folderId]'s sort forward, or back for a negative [direction]. The menu stays open. */
    data class SortFolder(val folderId: String, val direction: Int) : Effect

    /** Go to where [id], a game or a group, lives on Home, and light it up there. */
    data class ShowOnHome(val id: String?) : Effect

    /*
     * The tile menu's effects name the game they act on ([TileActions.game]), so a menu that
     * kept its game after the slot on screen emptied still acts on that game. Null means the
     * focused tile.
     */

    /** Put the game in collection [folderId], or take it out; [HOME_ROOT] pins it. The menu stays open. */
    data class ToggleCollection(val folderId: String, val gameId: String? = null) : Effect

    /** A new collection, holding the game when there is one, then its name field. */
    data class NewCollection(val gameId: String? = null) : Effect
    data object RenameFolder : Effect
    data object ChooseFolderIcon : Effect
    data object RemoveTile : Effect

    /** Delete the server game's downloaded copy. Its saves stay. */
    data object Undo : Effect
    data class ShowHidden(val id: String?) : Effect
    data class DeleteShortcut(val gameId: String?) : Effect
    data class RemoveDownload(val gameId: String? = null) : Effect

    /** Ask, then start the game open in its player over. */
    data class RestartGame(val gameId: String?) : Effect

    /** A on a day of Play activity: list that day's sessions. The shell fills them in. */
    data class OpenPlayDay(val weeksBack: Int, val day: Int) : Effect

    /** Take a play session out of the history, after its X was held. */
    data class RemovePlaySession(val sessionId: String) : Effect
    data object OpenAll : Effect
    data object ToggleAddNew : Effect

    /** Edit home's switch put Recently played on Home or took it off. */
    data class RecentOnHome(val on: Boolean) : Effect

    /** Open the notice's app on the top screen, or the bottom when [onBottom]. */
    data class OpenNotice(val id: String, val onBottom: Boolean) : Effect
    data class DismissNotice(val id: String) : Effect

    /** Every notice Y could remove is gone from the model; the app clears them too. */
    data object ClearNotices : Effect

    /** Show the home board's top level. */
    data object GoHome : Effect

    /**
     * The player chose what to do with a save conflict.
     * [preview] does not touch a library. The copies themselves are not dropped.
     */
    data class ResolveSaveConflict(
        val libraryId: String,
        val id: String,
        val action: SaveConflictAction,
        val preview: Boolean = false,
    ) : Effect
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
    val focus: GridFocus = GridFocus(),
    /** The first column the grid showed. The next focus scrolls from here, see [shownFirstColumn]. */
    val firstColumn: Int = 0,
    /** Columns on screen. The panel showing the grid sets it from its width, see [GridStrip]. */
    val columnsShown: Int = Metrics.columns,
    val panel: SidePanel? = null,
    val menuRows: List<MenuRows> = emptyList(),
    /** The full-screen settings page. Null while it is closed. */
    val settings: SettingsPage? = null,
    val dialog: DialogState? = null,
    val connectOpen: Boolean = false,
    val atLibraryRoot: Boolean = true,
    val primaryIsTop: Boolean = true,
    /**
     * Up from the top row can reach L1's and R1's pills. They are on the top screen, so an
     * app there covers them, and a selection keeps their drawers shut. Up then stays on the grid.
     */
    val pills: Boolean = true,
    val themes: List<String> = listOf(Copy.builtIn),
    val themeIds: List<String> = themes,
    val themeIndex: Int = 0,
    val iconThemeIndex: Int? = null,
    val backgroundChoices: List<BackgroundChoice> = listOf(
        BackgroundChoice(BackgroundMotion.Ribbons, Copy.ribbons),
        BackgroundChoice(BackgroundMotion.Embers, Copy.embers),
        BackgroundChoice(BackgroundMotion.Static, Copy.motionStatic),
        BackgroundChoice(BackgroundMotion.Off, Copy.motionOff),
    ),
    val themeMotions: List<BackgroundMotion> = listOf(BackgroundMotion.Off),
    val backgroundMotion: BackgroundMotion = BackgroundMotion.Off,
    val backgroundPinned: Boolean = false,
    val motionSpeed: MotionSpeed = MotionSpeed.Slow,
    val homeRoleHeld: Boolean = false,
    val folderGrantPending: Boolean = true,
    val notices: List<FoldNotice> = emptyList(),
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
    /** A saved address still has connection actions when its token needs recovery. */
    val rommSaved: Boolean = signedIn.any { it.pluginId == "romm" },
    /** The folders the library reads. Each has a row to remove it. */
    val folders: List<LibraryFolder> = emptyList(),
    val connectOrigin: String = "",
    val connectIndex: Int = 0,
    val connectScreen: HostScreen? = null,
    /** Editing returns to this connection's actions when the form closes. */
    val connectReturnPanel: SidePanel? = null,
    val connectWarning: String? = null,
    val connectHint: String? = null,
    val connectBusy: Boolean = false,
    val connectRetry: Boolean = false,
    val reLoginPending: Boolean = false,
    val playerSaves: List<PlayerSaveSetting> = emptyList(),
    /** Null while RetroArch is not installed. */
    val retroArch: RetroArchSetting? = null,
    /** Null while Eden is not installed. */
    val eden: EdenSetting? = null,
    val homeGrid: HomeGrid = HomeGrid.StandIns,
    /** What Y offers for the focused home or All tile. Null elsewhere. */
    val tileActions: TileActions? = null,
    /**
     * Bumped when the shelf's text changes without the count changing.
     * Compose treats an equal model as unchanged.
     */
    val shelfEpoch: Int = 0,
    val faceMap: FaceMap = FaceMap.standard(),
    val thorGlyphs: Boolean = true,
    val offerButtonLabels: Boolean = false,
    val capturingConfirm: Boolean = false,
    val undoVerb: String? = null,
    val deleteHold: DeleteHold? = null,
    val deleteProgress: Float = 0f,
    val deleteShake: Int = 0,
    val rewind: Int = 0,
    val held: Set<PromptKey> = emptySet(),
    val libraryGrid: Boolean = false,
    val gridKind: GridKind = GridKind.Games,
    val emptyGrid: EmptyGrid = EmptyGrid.None,
    val usageGranted: Boolean = false,
    /** Mirrors the home board. Off keeps new scans out of the grid. */
    val addNewToHome: Boolean = true,
    /** Mirrors the home board: the Recently played tile is on the root. */
    val recentOnHome: Boolean = false,
    /** Download game art from public sources. Off sends nothing; RomM's own covers still show. */
    val artwork: Boolean = true,
    /** A SteamGridDB key is saved. */
    val artKeySaved: Boolean = false,
    /** Moonlight's computers, each with a switch in Settings › Library. */
    val streamHosts: List<StreamHostSetting> = emptyList(),
    val connectKind: ConnectKind = ConnectKind.Romm,
    /** Both screens' brightness as a slider position, 0 to 100. See [brightnessLevel]. */
    val brightness: Int = 50,
    /** Android lets Foldcade write the brightness setting. */
    val brightnessAllowed: Boolean = false,
    /** R1 shows the row that asks for notification access. */
    val askNoticeAccess: Boolean = false,
    /** The Play activity page. Null while it is closed. */
    val playActivity: PlayActivityPage? = null,
    /** Two saves waiting for a choice on the bottom screen. Null while there is nothing to ask. */
    val saveConflict: SaveConflictPrompt? = null,
    val savePicker: SavePickerState? = null,
    /** Every library is being read again. */
    val librarySyncing: Boolean = false,
    /** Alphabetical labels in display order. Empty when triggers jump screens instead. */
    val gridLabels: List<String> = emptyList(),
    /** Home can retain empty slots after its last actual tile. */
    val lastOccupiedIndex: Int = count - 1,
    /** Trigger jumps on Home and folders stop at the last occupied column. Swipes keep the full reach. */
    val screenJumpLastColumn: Int? = null,
    val letterJump: String? = null,
    val letterJumpEpoch: Int = 0,
    /** "0.1.2 (230)", from the installed package. Settings › About shows it. */
    val appVersion: String = "",
    /** A on Version copied the debug line. The row says so until the focus leaves it. */
    val versionCopied: Boolean = false,
)

fun reduce(
    model: PickerModel,
    meaning: Meaning,
    screen: HostScreen = HostScreen.Bottom,
    repeatCount: Int = 0,
): Pair<PickerModel, Effect?> {
    val coerced = model.copy(focus = coerce(model.focus, model))
    val dialog = coerced.dialog
    if (dialog != null) {
        if (meaning == Meaning.LeftPanel || meaning == Meaning.RightPanel) return coerced to null
        val (next, choice) = applyDialog(dialog, meaning)
        val chosen = choice?.let { Effect.DialogChoice(it, dialog.kind, dialog.subject) }
        val cleared = if (choice != null) null else next
        return coerced.copy(dialog = cleared) to chosen
    }
    coerced.savePicker?.let {
        val (next, effect) = applySavePicker(it, meaning)
        return coerced.copy(savePicker = next) to effect
    }
    val conflict = coerced.saveConflict
    if (conflict != null) {
        if (meaning == Meaning.LeftPanel || meaning == Meaning.RightPanel) return coerced to null
        val (next, action) = applySaveConflict(conflict, meaning)
        val effect = action?.let {
            Effect.ResolveSaveConflict(conflict.libraryId, conflict.id, it, conflict.preview)
        }
        return coerced.copy(saveConflict = if (action != null) null else next) to effect
    }
    if (coerced.connectOpen) {
        val fields = connectFields(coerced.connectKind, coerced.connectRetry)
        val index = coerced.connectIndex.coerceIn(0, fields.lastIndex)
        val field = fields[index]
        return when (meaning) {
            Meaning.Back -> coerced.copy(
                connectOpen = false,
                connectScreen = null,
                connectBusy = false,
                panel = coerced.connectReturnPanel,
                connectReturnPanel = null,
            ) to null
            Meaning.MoveUp -> coerced.copy(connectIndex = (index - 1).coerceAtLeast(0)) to null
            Meaning.MoveDown -> coerced.copy(connectIndex = (index + 1).coerceAtMost(fields.lastIndex)) to null
            Meaning.Activate -> when (field) {
                ConnectField.Save -> coerced to if (coerced.connectBusy) null else if (coerced.connectKind == ConnectKind.ArtKey) Effect.SaveArtKey else Effect.SaveRommToken
                ConnectField.Retry -> coerced to if (coerced.connectBusy) null else Effect.RetryRommDeviceCode
                ConnectField.Origin, ConnectField.Token -> coerced.copy(connectIndex = index) to Effect.EditField
            }
            Meaning.LeftPanel, Meaning.RightPanel ->
                presentPanel(
                    coerced.copy(connectOpen = false, connectScreen = null, connectBusy = false, connectReturnPanel = null),
                    meaning,
                    screen,
                ) to null
            else -> coerced.copy(connectIndex = index) to null
        }
    }
    val settings = coerced.settings
    if (settings != null) return applySettings(coerced, settings, meaning, repeatCount).let { (next, effect) -> next.versionCopySettled() to effect }
    val activity = coerced.playActivity
    if (activity != null) return applyPlayActivity(coerced, activity, meaning)
    val panel = coerced.panel
    if (panel != null) {
        if (meaning == Meaning.LeftPanel || meaning == Meaning.RightPanel) {
            return presentPanel(coerced, meaning, screen) to null
        }
        return applyPanel(coerced, panel, meaning, screen)
    }
    return applyGrid(coerced, meaning, screen, repeatCount)
}

private fun coerce(focus: GridFocus, model: PickerModel): GridFocus {
    var next = focus
    // A pill the cursor was on before an app covered it gives the cursor back to the grid.
    if (!model.pills && next.chrome != null) next = next.copy(chrome = null)
    if (model.count <= 0) {
        // An empty grid has nothing but R1 to rest on, when the pills are there to reach.
        val chrome = if (model.pills) next.chrome ?: Chrome.StatusCluster else null
        return next.copy(chrome = chrome, cellIndex = 0, lastColumn = 0)
    }
    if (next.chrome == null && next.cellIndex !in 0 until reach(model)) {
        val index = (model.count - 1).coerceAtLeast(0)
        next = next.copy(cellIndex = index, lastColumn = GridStrip.column(index))
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
    index = if (side == Side.Right) rightStart() else 0,
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
    repeatCount: Int,
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
            model.copy(focus = softStop(model.focus, move(model.focus, meaning, model), model.lastOccupiedIndex, repeatCount)) to null
        Meaning.PageTowardStart, Meaning.PageTowardEnd ->
            model.copy(focus = page(model.focus, meaning == Meaning.PageTowardEnd, model)) to null
        Meaning.LetterBackward, Meaning.LetterForward -> {
            val forward = meaning == Meaning.LetterForward
            if (model.gridLabels.isNotEmpty() && model.focus.chrome == null) {
                val index = jumpLetter(model.gridLabels, model.focus.cellIndex, forward)
                model.copy(
                    focus = GridFocus(index, GridStrip.column(index)),
                    letterJump = letterGroup(model.gridLabels[index]),
                    letterJumpEpoch = model.letterJumpEpoch + 1,
                ) to null
            } else model.copy(focus = page(model.focus, forward, model, model.screenJumpLastColumn)) to null
        }
        Meaning.Menu -> if (model.arranging) model to null else model.openSettings(HostScreen.Top) to null
        Meaning.Activate -> activate(model, screen)
        Meaning.ActivateBottom -> activate(model, screen, onBottom = true)
        Meaning.Options -> openActions(model) to null
        Meaning.Back -> backGrid(model)
        Meaning.LeftPanel, Meaning.RightPanel -> presentPanel(model, meaning, screen) to null
        else -> model to null
    }
}

/**
 * Y opens the focused tile's menu in the left island, with Foldcade's other
 * options. R1 is the device's. With nothing to offer, it stays shut.
 */
private fun openActions(model: PickerModel): PickerModel {
    if (model.arranging) return model
    if (model.count <= 0) {
        if (model.tileActions?.undo != true) return model
    } else if (model.focus.chrome != null) return model
    if (actionRows(model.tileActions).isEmpty()) return model
    return model.copy(panel = freshPanel(Side.Left, model.focus).copy(
        level = PanelLevel.Actions, recoveryOnly = model.count <= 0))
}

private fun backGrid(model: PickerModel): Pair<PickerModel, Effect?> {
    val hold = model.hold
    if (hold != null) {
        return model.copy(
            hold = null,
            order = hold.orderBefore,
            focus = model.focus.copy(cellIndex = hold.origin, lastColumn = GridStrip.column(hold.origin), chrome = null),
        ) to null
    }
    if (model.arranging) return model.copy(arranging = false) to null
    if (model.homeGrid != HomeGrid.StandIns) return model to Effect.GoHome
    if (!model.atLibraryRoot) return model to Effect.LeavePlatform
    return model to null
}

private fun moveHeld(model: PickerModel, hold: Hold, meaning: Meaning): PickerModel {
    val focus = GridFocus(cellIndex = hold.index, lastColumn = GridStrip.column(hold.index))
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
            Meaning.MoveDown -> downFromChrome(focus, model)
            Meaning.MoveLeft -> focus.copy(chrome = Chrome.LeftIsland)
            Meaning.MoveRight -> focus.copy(chrome = Chrome.StatusCluster)
            else -> focus
        }
    }
    if (model.count <= 0) return if (model.pills) focus.copy(chrome = Chrome.StatusCluster) else focus
    if (model.libraryGrid && model.gridKind == GridKind.NoLibrary) return alongButtons(focus, meaning, model)
    val column = GridStrip.column(focus.cellIndex)
    val row = GridStrip.row(focus.cellIndex)
    return when (meaning) {
        Meaning.MoveLeft -> across(focus, column - 1, row, model)
        Meaning.MoveRight -> across(focus, column + 1, row, model)
        Meaning.MoveDown -> if (row == Metrics.rows - 1) focus else land(focus, focus.cellIndex + 1, model)
        Meaning.MoveUp -> if (row == 0) upToChrome(focus, column, model) else land(focus, focus.cellIndex - 1, model)
        else -> focus
    }
}

/**
 * Slots the cursor can rest on. The empty library has only its buttons, and a
 * held tile only reorders the tiles there are, so it stops at the last one.
 */
private fun reach(model: PickerModel): Int =
    if (model.hold != null || model.libraryGrid && model.gridKind == GridKind.NoLibrary) model.count else GridStrip.reach(model.count)

private fun reachColumns(model: PickerModel): Int = GridStrip.columns(reach(model))

/** The empty library's buttons sit side by side under its title, not down a column. */
private fun alongButtons(focus: GridFocus, meaning: Meaning, model: PickerModel): GridFocus = when (meaning) {
    Meaning.MoveLeft -> land(focus, focus.cellIndex - 1, model)
    Meaning.MoveRight -> land(focus, focus.cellIndex + 1, model)
    Meaning.MoveUp -> upToChrome(focus, 0, model)
    else -> focus
}

/** Into [column] at [row], a tile or an empty slot. Past the strip's reach stays put. */
private fun across(focus: GridFocus, column: Int, row: Int, model: PickerModel): GridFocus {
    if (column !in 0 until reachColumns(model)) return focus
    return land(focus, GridStrip.index(column, row), model)
}

private fun land(focus: GridFocus, index: Int, model: PickerModel): GridFocus {
    if (index !in 0 until reach(model)) return focus
    return focus.copy(cellIndex = index, lastColumn = GridStrip.column(index), chrome = null)
}

/**
 * Up from the top row reaches the pill on that side of the screen: L1 from the left half, R1 from the right.
 * With the pills out of reach ([PickerModel.pills]) it stays.
 */
private fun upToChrome(focus: GridFocus, column: Int, model: PickerModel): GridFocus {
    if (!model.pills) return focus
    val onScreen = column - shownFirstColumn(model)
    val chrome = if (onScreen < model.columnsShown / 2) Chrome.LeftIsland else Chrome.StatusCluster
    return focus.copy(chrome = chrome, lastColumn = column)
}

private fun downFromChrome(focus: GridFocus, model: PickerModel): GridFocus {
    if (model.count <= 0) return focus
    val column = focus.lastColumn.coerceIn(0, reachColumns(model) - 1)
    return focus.copy(cellIndex = GridStrip.index(column, 0), lastColumn = column, chrome = null)
}

/** A swipe moves the cursor a screen of columns along the strip, on the same row. */
private fun page(focus: GridFocus, towardEnd: Boolean, model: PickerModel, lastColumn: Int? = null): GridFocus {
    if (model.count <= 0 || focus.chrome != null) return focus
    val index = focus.cellIndex
    val column = GridStrip.column(index)
    val step = if (towardEnd) model.columnsShown else -model.columnsShown
    val last = (lastColumn ?: (reachColumns(model) - 1)).coerceIn(0, reachColumns(model) - 1)
    if (towardEnd && column >= last) return focus
    val target = (column + step).coerceIn(0, last)
    if (target == column) return focus
    return across(focus, target, GridStrip.row(index), model)
}

/**
 * A finger let the strip go at [first]. The strip rests there, as far along as it can go.
 * The cursor keeps its tile while that tile is on screen. Otherwise it comes in along
 * its row to the nearer edge, where the tiles slid out from under it.
 */
fun scrolledTo(model: PickerModel, first: Int): PickerModel {
    // A cursor that walks only the tiles, as with a tile in the hand, keeps the strip where its moves put it.
    if (model.count <= 0 || reach(model) != GridStrip.reach(model.count)) return model
    val shown = model.columnsShown
    val resting = first.coerceIn(0, GridStrip.lastFirst(model.count, shown))
    val onScreen = resting until resting + shown
    val focus = model.focus
    val column = GridStrip.column(focus.cellIndex).coerceIn(onScreen)
    return model.copy(
        firstColumn = resting,
        // From a pill, down lands on the column under it, so that comes along too.
        focus = focus.copy(
            cellIndex = GridStrip.index(column, GridStrip.row(focus.cellIndex)),
            lastColumn = focus.lastColumn.coerceIn(onScreen),
        ),
    )
}

/** A, or X when [onBottom]. X launches a game on the bottom screen and does what A does anywhere else. */
private fun activate(model: PickerModel, screen: HostScreen, onBottom: Boolean = false): Pair<PickerModel, Effect?> {
    return when (model.focus.chrome) {
        Chrome.StatusCluster -> presentPanel(model, Meaning.RightPanel, screen) to null
        Chrome.LeftIsland -> presentPanel(model, Meaning.LeftPanel, screen) to null
        null -> {
            if (model.arranging) return placeOrPick(model)
            if (model.count <= 0 || model.focus.cellIndex !in 0 until model.count) return model to null
            if (!model.libraryGrid) return model to Effect.Launch(model.focus.cellIndex, onBottom)
            when (model.gridKind) {
                GridKind.Games -> model to Effect.Launch(model.focus.cellIndex, onBottom)
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
                        connectKind = ConnectKind.Romm,
                        connectReturnPanel = null,
                    ) to Effect.OpenAddRomm
                }
            }
        }
    }
}

private fun placeOrPick(model: PickerModel): Pair<PickerModel, Effect?> {
    val hold = model.hold
    if (hold != null) return model.copy(hold = null) to null
    val index = model.focus.cellIndex
    if (index !in 0 until model.count) return model to null
    val order = displayOrder(model)
    return model.copy(hold = Hold(index = index, origin = index, orderBefore = order), order = order) to null
}

/**
 * Text rows are one column. Tiles ([tileBlock]) are [quickTileColumns] wide and sit
 * together as one block: R1's Android screens at its top, L1's places under the
 * play strip, a tile's verbs at the top of its menu. Up from the row under the block
 * lands on the tile of its last line in [column], the one focus came down from, or
 * the nearest one, or the first without one. A direction with no neighbor stays put.
 */
fun panelFocusIndex(rows: List<Row>, index: Int, meaning: Meaning, column: Int? = null): Int {
    if (rows.isEmpty()) return 0
    val current = index.coerceIn(0, rows.lastIndex)
    val block = tileBlock(rows)
    val linear = when (meaning) {
        Meaning.MoveUp -> (current - 1).coerceAtLeast(0)
        Meaning.MoveDown -> (current + 1).coerceAtMost(rows.lastIndex)
        else -> current
    }
    if (block.isEmpty()) return linear
    val tileStart = block.first
    val tileEnd = block.last + 1
    val tileCount = tileEnd - tileStart
    val lastLine = (tileCount - 1) / quickTileColumns
    if (current !in block) {
        if (meaning != Meaning.MoveUp || current != tileEnd) return linear
        val lastLineStart = lastLine * quickTileColumns
        return tileStart + lastLineStart + (column ?: 0).coerceIn(0, tileCount - 1 - lastLineStart)
    }
    val tileIndex = current - tileStart
    val column = tileIndex % quickTileColumns
    val line = tileIndex / quickTileColumns
    return when (meaning) {
        Meaning.MoveLeft -> if (column == 0) current else current - 1
        Meaning.MoveRight -> {
            val next = tileIndex + 1
            if (next >= tileCount || next / quickTileColumns != line) current else tileStart + next
        }
        Meaning.MoveUp -> if (line == 0) {
            if (tileStart == 0) current else tileStart - 1
        } else {
            tileStart + (line - 1) * quickTileColumns + column
        }
        Meaning.MoveDown -> {
            val below = (line + 1) * quickTileColumns + column
            when {
                below < tileCount -> tileStart + below
                line == lastLine && tileEnd < rows.size -> tileEnd
                else -> current
            }
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
    if (chordKey(meaning) != null) {
        // A chord pressed while the other island is still folding away belongs to the one
        // opening. The island leaving never takes it, even when the opening one has no such row.
        if (current.retiring) {
            val pending = current.pendingSide ?: return model to null
            val opening = freshPanel(pending, current.grid)
            chordIndex(panelRows(opening, model), meaning) ?: return model to null
            return applyPanel(model.copy(panel = opening), opening, meaning, screen)
        }
        val chord = chordIndex(rows, meaning)
        if (chord != null) return activateRow(model, current.copy(index = chord), rows[chord], screen)
    }
    return when (meaning) {
        Meaning.MoveUp, Meaning.MoveDown -> {
            val next = panelFocusIndex(rows, index, meaning, current.tileColumn)
            val block = tileBlock(rows)
            // Leaving the tiles remembers which one, so up from the row under them comes back to it.
            val column = if (index in block && next !in block) (index - block.first) % quickTileColumns else current.tileColumn
            model.copy(panel = current.copy(index = next, tileColumn = column)) to null
        }
        Meaning.MoveLeft, Meaning.MoveRight -> {
            val direction = if (meaning == Meaning.MoveLeft) -1 else 1
            // A folder's order is the board's, which the shell changes; the menu stays on the row.
            if (rows[index] == Row.SortFolder) return model.copy(panel = current) to sortStep(model, direction)
            val nudged = stepRow(model, rows[index], direction)
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
        // X opens a notice on the bottom screen, as it opens a game there.
        Meaning.ActivateBottom -> when (val row = rows[index]) {
            is Row.Notice -> openNotice(model, current, row.id, onBottom = true, screen = screen)
            else -> model.copy(panel = current) to null
        }
        // Y, or Delete on a keyboard, removes the focused notice.
        Meaning.Options, Meaning.Dismiss -> when (val row = rows[index]) {
            is Row.Notice -> dismissNotice(model, current, row.id)
            else -> model.copy(panel = current) to null
        }
        else -> model.copy(panel = current) to null
    }
}

/**
 * Rows that offer a few choices: left and right step through them, and A steps forward.
 * The Settings page draws them as ‹ value ›.
 */
internal val choiceRows: Set<Row> = setOf(
    Row.Theme, Row.IconTheme, Row.Background, Row.MotionSpeed, Row.MusicTrack, Row.Order, Row.Primary, Row.SortFolder,
)

/** The folder Sort row's step, or null when the menu names no folder. */
private fun sortStep(model: PickerModel, direction: Int): Effect? =
    model.tileActions?.sort?.let { Effect.SortFolder(it.folderId, direction) }

/**
 * Left and right step a value and keep the row. Music volume moves by 5 points,
 * and so does brightness, or it asks for access first. A choice row (theme,
 * icons, background, motion, track, sort, main screen) cycles its choices either
 * way, as A cycles them forward, except Theme, whose A opens the Themes page. Other rows
 * ignore the direction, so the focus stays put.
 */
private fun stepRow(model: PickerModel, row: Row, direction: Int): PickerModel? {
    return when (row) {
        Row.MusicVolume -> model.copy(music = model.music.nudged(direction))
        Row.Brightness -> if (model.brightnessAllowed) {
            model.copy(brightness = steppedBrightness(model.brightness, direction))
        } else {
            model.copy(dialog = brightnessPrompt())
        }
        Row.MusicTrack -> model.withTrack(cycledMusicTrack(offeredTracks(model), model.music.trackId, direction))
        Row.Theme -> stepTheme(model, direction)
        Row.IconTheme -> {
            // Match theme sits before the first pack, so the choices loop through it.
            val slots = model.themes.size + 1
            val now = model.iconThemeIndex?.plus(1) ?: 0
            val next = Math.floorMod(now + direction, slots)
            model.copy(iconThemeIndex = if (next == 0) null else next - 1)
        }
        Row.Background -> {
            val choices = model.backgroundChoices
            val at = choices.indexOfFirst { it.motion == model.backgroundMotion }
            val motion = when {
                choices.isEmpty() -> BackgroundMotion.Off
                // A motion that is not among the choices steps to the first or the last.
                at < 0 -> (if (direction > 0) choices.first() else choices.last()).motion
                else -> choices[Math.floorMod(at + direction, choices.size)].motion
            }
            model.copy(backgroundMotion = motion, backgroundPinned = true)
        }
        Row.MotionSpeed -> {
            val speeds = MotionSpeed.entries
            model.copy(motionSpeed = speeds[Math.floorMod(speeds.indexOf(model.motionSpeed) + direction, speeds.size)])
        }
        Row.Order -> model.copy(sort = model.sort.toggled())
        Row.Primary -> model.copy(primaryIsTop = !model.primaryIsTop)
        else -> null
    }
}

/** The next or previous theme. It brings that theme's background and track unless the user chose their own. */
private fun stepTheme(model: PickerModel, direction: Int): PickerModel {
    val count = model.themes.size.coerceAtLeast(1)
    val nextIndex = Math.floorMod(model.themeIndex + direction, count)
    val motion = if (model.backgroundPinned) {
        model.backgroundMotion
    } else {
        model.themeMotions.getOrElse(nextIndex) { BackgroundMotion.Off }
    }
    val stepped = model.copy(themeIndex = nextIndex, backgroundMotion = motion)
    val tracks = offeredMusicTracks(stepped.homeTracks, stepped.themeTracks.getOrNull(nextIndex))
    val kept = tracks.firstOrNull { it.id == model.music.trackId }
    val track = kept ?: selectedMusicTrack(tracks, HomeMusicSetting.DEFAULT_TRACK_ID)
    return stepped.withTrack(track)
}

/**
 * Up and down move within the focused column. Right, or Activate, steps from a category into
 * its card. A card's tiles move in two directions, its rows in one. Left on a row steps a
 * value when the row has one, otherwise back to the categories, as Left from a line's first
 * tile does. A on a tile opens its page in the card, and Back or Left there returns to that
 * tile. Back steps out the same way, then closes the page. L1, R1, and Start close it too,
 * so Start undoes L1 then Start. A row acts exactly as it does in the menu. A row that would
 * close the menu closes the page.
 */
private fun applySettings(
    model: PickerModel,
    page: SettingsPage,
    meaning: Meaning,
    repeatCount: Int,
): Pair<PickerModel, Effect?> {
    val categories = settingsCategories(model)
    if (categories.isEmpty()) return model.copy(settings = null, focus = page.grid) to null
    val categoryIndex = page.category.coerceIn(0, categories.lastIndex)
    val category = categories[categoryIndex]
    val (shown, rows) = settingsShown(page.copy(category = categoryIndex), model)
    val rowIndex = shown.row.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
    val current = shown.copy(row = rowIndex)
    val close = model.copy(settings = null, focus = page.grid, capturingConfirm = false) to null
    if (!current.onRows) {
        return when (meaning) {
            Meaning.MoveUp -> model.copy(settings = current.copy(category = (categoryIndex - 1).coerceAtLeast(0), row = 0, inner = null)) to null
            Meaning.MoveDown -> model.copy(
                settings = current.copy(category = (categoryIndex + 1).coerceAtMost(categories.lastIndex), row = 0, inner = null),
            ) to null
            Meaning.MoveRight, Meaning.Activate ->
                model.copy(settings = current.copy(onRows = rows.isNotEmpty(), row = 0, inner = null)) to null
            Meaning.Back, Meaning.LeftPanel, Meaning.RightPanel, Meaning.Menu -> close
            else -> model.copy(settings = current) to null
        }
    }
    val row = rows.getOrNull(rowIndex) ?: return model.copy(settings = current.copy(onRows = false, inner = null)) to null
    val inner = current.inner
    // Back on a page lands on the tile that opened it.
    val shelf = current.copy(inner = null, row = inner?.let { settingsTileIndex(category, it, model) }?.coerceAtLeast(0) ?: rowIndex)
    return when (meaning) {
        Meaning.MoveUp, Meaning.MoveDown -> model.copy(
            capturingConfirm = false,
            settings = current.copy(row = settingsFocusIndex(rows, rowIndex, meaning) ?: rowIndex),
        ) to null
        Meaning.MoveLeft, Meaning.MoveRight -> {
            val direction = if (meaning == Meaning.MoveLeft) -1 else 1
            val nudged = stepRow(model, row, direction)
            when {
                nudged != null -> nudged.copy(capturingConfirm = false, settings = current) to null
                row.settingsTile() -> settingsFocusIndex(rows, rowIndex, meaning)
                    ?.let { model.copy(settings = current.copy(row = it)) to null }
                    ?: (model.copy(settings = current.copy(onRows = false)) to null)
                meaning == Meaning.MoveLeft && inner != null -> model.copy(capturingConfirm = false, settings = shelf) to null
                meaning == Meaning.MoveLeft -> model.copy(capturingConfirm = false, settings = current.copy(onRows = false)) to null
                else -> model.copy(capturingConfirm = false, settings = current) to null
            }
        }
        Meaning.Back -> when {
            model.capturingConfirm -> model.copy(capturingConfirm = false, settings = current) to null
            inner != null -> model.copy(settings = shelf) to null
            else -> model.copy(settings = current.copy(onRows = false)) to null
        }
        Meaning.LeftPanel, Meaning.RightPanel, Meaning.Menu -> close
        Meaning.Activate -> {
            if (row is Row.SettingsTile) return model.copy(settings = current.copy(inner = row.page, row = 0)) to null
            // A held A that opened a page does not go on to act in it: a folder's Remove, a computer's pairing.
            if (inner != null && repeatCount > 0) return model.copy(settings = current) to null
            // Rows act through the menu path. A stand-in panel tells "stays open" from "leaves".
            val standIn = SidePanel(Side.Left, current.screen, PanelLevel.Root, rowIndex, current.grid)
            val (next, effect) = activateRow(model.copy(panel = standIn), standIn, row, current.screen)
            if (next.panel?.level == PanelLevel.RommConnection) {
                return next.copy(
                    panel = next.panel.copy(connectionSettings = current),
                    settings = null,
                ) to effect
            }
            // The folder goes; the shelf stays, its focus where the folder's tile was.
            if (row is Row.ForgetFolder) return next.copy(panel = null, settings = shelf, focus = model.focus) to effect
            val stays = next.panel != null
            next.copy(
                panel = null,
                settings = if (stays) current else null,
                focus = if (stays) model.focus else next.focus,
            ) to effect
        }
        else -> model.copy(settings = current) to null
    }
}

private fun offeredTracks(model: PickerModel): List<MusicTrack> =
    offeredMusicTracks(model.homeTracks, model.themeTracks.getOrNull(model.themeIndex))

private fun PickerModel.withTrack(track: MusicTrack): PickerModel =
    copy(music = music.copy(trackId = track.id), trackTitle = track.title)

private fun backPanel(model: PickerModel, panel: SidePanel): Pair<PickerModel, Effect?> {
    return when (panel.level) {
        PanelLevel.RommConnection -> {
            val settings = panel.connectionSettings
            if (settings != null) model.copy(panel = null, settings = settings) to null
            else model.copy(panel = null, focus = panel.grid) to null
        }
        PanelLevel.EditHome -> model.copy(panel = panel.copy(level = PanelLevel.Root, index = leftRows().indexOf(Row.EditHome),
            tileColumn = panel.parentTileColumn, parentTileColumn = null)) to null
        PanelLevel.Collections -> {
            val parent = panel.copy(level = panel.via)
            val row = panelRows(parent, model).indexOf(Row.AddToCollection).coerceAtLeast(0)
            model.copy(panel = parent.copy(index = row)) to null
        }
        PanelLevel.Root, PanelLevel.Actions -> model.copy(panel = null, focus = panel.grid) to null
    }
}

private fun activateRow(
    model: PickerModel,
    panel: SidePanel,
    row: Row,
    screen: HostScreen,
): Pair<PickerModel, Effect?> {
    return when (row) {
        Row.Settings -> model.openSettings(panel.screen, grid = panel.grid) to null
        // The [choiceRows]. The list is spelled out so a new row cannot slip past this when.
        Row.IconTheme, Row.Background, Row.MotionSpeed, Row.MusicTrack, Row.Order, Row.Primary ->
            stepRow(model, row, 1)!!.copy(panel = panel) to null
        // Left and right step the pack. A opens every pack, Add a theme, and what was found in each.
        Row.Theme -> model.copy(panel = panel) to Effect.OpenThemes
        // A choice row too, whose value is the board's: the shell steps it.
        Row.SortFolder -> model.copy(panel = panel) to sortStep(model, 1)
        Row.UsageAccess -> model.copy(dialog = usageAccessPrompt(screen)) to null
        Row.PlayActivity -> model.copy(
            panel = null,
            playActivity = PlayActivityPage(screen = panel.screen, grid = panel.grid),
        ) to null
        Row.Home -> model.copy(panel = null, focus = panel.grid) to Effect.GoHome
        Row.EditHome -> model.copy(panel = panel.copy(level = PanelLevel.EditHome, index = 0, tileColumn = null, parentTileColumn = panel.tileColumn)) to null
        Row.Music -> model.copy(panel = panel, music = model.music.toggled()) to null
        Row.MusicVolume -> model.copy(panel = panel, music = model.music.stepped()) to null
        Row.SetAsHome -> model to Effect.RequestHome
        Row.AndroidSettings -> model.copy(panel = panel) to Effect.OpenAndroidSetting(AndroidSetting.Settings)
        Row.Licenses -> model.copy(panel = panel) to Effect.OpenLicenses
        // Settings opens its page itself, see [applySettings].
        is Row.SettingsTile -> model.copy(panel = panel) to null
        Row.Version -> model.copy(panel = panel, versionCopied = true) to Effect.CopyDebugInfo
        Row.DefaultHomeApp -> model.copy(panel = panel) to Effect.OpenAndroidSetting(AndroidSetting.Home)
        is Row.QuickTile -> model.copy(panel = panel) to Effect.OpenAndroidSetting(row.setting.androidSetting())
        // Left and right move the slider. A asks for access, and does nothing once it is granted.
        Row.Brightness -> if (model.brightnessAllowed) {
            model.copy(panel = panel) to null
        } else {
            model.copy(panel = panel, dialog = brightnessPrompt()) to null
        }
        Row.AddFolder ->
            if (model.folderGrantPending) {
                model.copy(dialog = folderExplainer(screen)) to null
            } else {
                model to Effect.AddFolder
            }
        // The row stays in view and reads Syncing until every library has answered.
        Row.SyncLibrary -> model.copy(panel = panel) to Effect.SyncLibrary
        is Row.RommConnection -> model.copy(
            panel = panel.copy(
                level = PanelLevel.RommConnection,
                index = 0,
                connection = SignedInBackend(row.pluginId, row.label),
            ),
        ) to null
        is Row.EditRomm -> model.copy(
            panel = null,
            focus = panel.grid,
            connectOpen = true,
            connectScreen = screen,
            connectIndex = 0,
            connectKind = ConnectKind.Romm,
            connectReturnPanel = panel,
        ) to Effect.EditRommConnection
        Row.AddRomm -> model.copy(
            panel = null,
            focus = panel.grid,
            connectOpen = true,
            connectScreen = screen,
            connectOrigin = "",
            connectIndex = 0,
            connectKind = ConnectKind.Romm,
            connectHint = null,
            connectReturnPanel = null,
        ) to Effect.OpenAddRomm
        is Row.SignOut -> model.copy(panel = null, focus = panel.grid) to Effect.ForgetCredentials(row.pluginId)
        is Row.ForgetFolder -> model.copy(panel = null, focus = panel.grid) to Effect.ForgetFolder(row.uri)
        is Row.Notice -> openNotice(model, panel, row.id, onBottom = false, screen = screen)
        Row.NoticeAccess -> model.copy(panel = panel, dialog = noticeAccessPrompt(screen)) to null
        Row.ClearNotices -> model.copy(
            panel = panel.copy(index = rightStart()),
            notices = model.notices.filter { !it.dismissable },
        ) to Effect.ClearNotices
        is Row.PlayerSave -> model.copy(panel = null, focus = panel.grid) to Effect.ChoosePlayerSave(row.playerId)
        is Row.HostedSaves -> model.copy(panel = null, focus = panel.grid) to Effect.SetUpHostedSaves(row.playerId)
        is Row.SaveSync -> {
            val setting = model.playerSaves.firstOrNull { it.playerId == row.playerId }
            when {
                // Not syncing yet: the steps, and Open on that dialog starts the player.
                setting?.saveSyncSteps != null && !setting.saveSyncing ->
                    model.copy(panel = null, focus = panel.grid) to Effect.ShowSaveSync(row.playerId)
                // Already syncing: A opens the player directly, as that dialog's Open does.
                setting?.saveSyncing == true ->
                    model.copy(panel = null, focus = panel.grid) to Effect.OpenPlayer(row.playerId)
                else -> model.copy(panel = panel) to null
            }
        }
        is Row.PlayerSetup -> model.copy(panel = null, focus = panel.grid) to Effect.ShowPlayerSetup(row.playerId)
        Row.RetroArchCores -> model.copy(panel = null, focus = panel.grid) to Effect.ShowRetroArchCores
        Row.EdenFolder -> model.copy(panel = null, focus = panel.grid) to Effect.ShowEdenFolder
        Row.AndroidGames -> model.copy(panel = null, focus = panel.grid) to Effect.OpenFolder(HOME_ANDROID_GAMES)
        Row.Apps -> model.copy(panel = null, focus = panel.grid) to Effect.OpenFolder(HOME_ANDROID_APPS)
        Row.Hidden -> openGrid(model, panel, HomeGrid.Hidden)
        Row.AllLibrary -> model.copy(panel = null, focus = panel.grid, arranging = false, hold = null) to Effect.OpenAll
        Row.AddNewGames -> model.copy(panel = panel, addNewToHome = !model.addNewToHome) to Effect.ToggleAddNew
        Row.RecentOnHome -> model.copy(panel = panel, recentOnHome = !model.recentOnHome) to Effect.RecentOnHome(!model.recentOnHome)
        Row.Artwork -> model.copy(panel = panel, artwork = !model.artwork) to null
        // A saved key is a credential: A only asks, and the dialog's Forget key forgets it.
        Row.ArtKey -> if (model.artKeySaved) {
            model.copy(panel = panel, dialog = forgetArtKeyPrompt(screen)) to null
        } else {
            model.copy(
                panel = null,
                focus = panel.grid,
                connectOpen = true,
                connectScreen = screen,
                connectIndex = 0,
                connectKind = ConnectKind.ArtKey,
            ) to Effect.OpenArtKey
        }
        // Pairing is asked on the computer, so A only starts it; forgetting asks here first.
        is Row.StreamHost -> model.streamHosts.firstOrNull { it.key == row.key }?.let { host ->
            if (host.paired) model.copy(panel = panel, dialog = forgetHostDialog(host.name, host.key, screen)) to null
            else model.copy(panel = panel) to Effect.PairStreamHost(host.key)
        } ?: (model.copy(panel = panel) to null)
        Row.MoveApp -> model.copy(panel = null, focus = panel.grid) to model.tileActions?.moveTo?.let { Effect.MoveApp(model.tileActions.game, it) }
        Row.Hide -> model.copy(panel = null, focus = panel.grid) to (model.tileActions?.bulk?.let { Effect.Bulk(BulkAction.Hide, it) } ?: Effect.Hide(model.tileActions?.game))
        Row.AddToCollection -> model.copy(panel = panel.copy(level = PanelLevel.Collections, index = 0, via = panel.level)) to null
        // The check flips here so it moves at once. The shell's own list replaces this one.
        is Row.Collection -> if (model.tileActions?.bulk != null) model.copy(panel = null, focus = panel.grid) to
            Effect.Bulk(BulkAction.Collection, model.tileActions.bulk, row.folderId) else model.copy(panel = panel, tileActions = model.tileActions?.toggled(row.folderId)) to
            Effect.ToggleCollection(row.folderId, model.tileActions?.game)
        Row.SelectTile -> model.copy(panel = null, focus = panel.grid) to model.tileActions?.game?.let(Effect::SelectTile)
        Row.ChooseArt -> model.copy(panel = null, focus = panel.grid) to Effect.ChooseArt(model.tileActions?.game)
        Row.MoveTile -> model.copy(panel = null, focus = panel.grid) to Effect.MoveTile
        Row.Tidy -> model.copy(panel = null, focus = panel.grid) to Effect.Tidy
        Row.ShowOnHome -> model.copy(panel = null, focus = panel.grid) to Effect.ShowOnHome(model.tileActions?.showOnHome)
        Row.NewCollection -> model.copy(panel = null, focus = panel.grid) to (model.tileActions?.bulk?.let { Effect.Bulk(BulkAction.NewCollection, it) } ?: Effect.NewCollection(model.tileActions?.game))
        Row.RenameFolder -> model.copy(panel = null, focus = panel.grid) to Effect.RenameFolder
        Row.FolderIcon -> model.copy(panel = null, focus = panel.grid) to Effect.ChooseFolderIcon
        Row.RemoveTile -> model.copy(panel = null, focus = panel.grid) to Effect.RemoveTile
        Row.Undo -> model.copy(panel = null, focus = panel.grid) to Effect.Undo
        Row.ShowHidden -> model.copy(panel = null, focus = panel.grid) to Effect.ShowHidden(model.tileActions?.game)
        Row.DeleteShortcut -> model.copy(panel = null, focus = panel.grid) to Effect.DeleteShortcut(model.tileActions?.game)
        Row.RemoveDownload -> model.copy(panel = null, focus = panel.grid) to Effect.RemoveDownload(model.tileActions?.game)
        Row.Saves -> model.copy(panel = null, focus = panel.grid) to model.tileActions?.game?.let(Effect::OpenSaves)
        Row.RestartGame -> model.copy(panel = null, focus = panel.grid) to Effect.RestartGame(model.tileActions?.game)
        // Play activity's list removes a session itself; no menu lists this row.
        Row.RemoveSession -> model to null
        Row.ButtonLabels -> if (model.capturingConfirm) {
            model.copy(capturingConfirm = false, offerButtonLabels = false) to Effect.DismissButtonLabels
        } else {
            model.copy(capturingConfirm = true) to null
        }
    }
}

private fun TileActions.toggled(folderId: String): TileActions = copy(
    collections = collections?.map { if (it.folderId == folderId) it.copy(member = !it.member) else it },
)

/**
 * A, or X for the bottom screen. An app's notice opens that app and R1 closes.
 * A Foldcade notice has no app, so A shows all of its text and X does nothing.
 */
private fun openNotice(
    model: PickerModel,
    panel: SidePanel,
    id: String,
    onBottom: Boolean,
    screen: HostScreen,
): Pair<PickerModel, Effect?> {
    val notice = model.notices.firstOrNull { it.id == id } ?: return model.copy(panel = panel) to null
    notice.settingsCategory?.let { category ->
        return model.openSettings(panel.screen, category, panel.grid) to null
    }
    notice.savePlayerId?.let { playerId ->
        val setting = model.playerSaves.firstOrNull { it.playerId == playerId }
            ?: return model.copy(panel = panel) to null
        val fix = when {
            setting.hostedTitle != null && !setting.hostedInUse -> Effect.SetUpHostedSaves(playerId)
            !setting.chosen -> Effect.ChoosePlayerSave(playerId)
            setting.saveSyncSteps != null -> Effect.ShowSaveSync(playerId)
            setting.setupSteps != null -> Effect.ShowPlayerSetup(playerId)
            else -> Effect.ChoosePlayerSave(playerId)
        }
        return model.copy(panel = null, focus = panel.grid) to fix
    }
    if (notice.openable) {
        return model.copy(panel = null, focus = panel.grid) to Effect.OpenNotice(id, onBottom)
    }
    if (onBottom) return model.copy(panel = panel) to null
    return model.copy(panel = panel, dialog = noticeReader(notice, panel.screen)) to null
}

/** The focus stays in place, so the next notice moves up under it. */
private fun dismissNotice(model: PickerModel, panel: SidePanel, id: String): Pair<PickerModel, Effect?> {
    val notice = model.notices.firstOrNull { it.id == id }
    if (notice == null || !notice.dismissable) return model.copy(panel = panel) to null
    return model.copy(panel = panel, notices = model.notices.filter { it.id != id }) to Effect.DismissNotice(id)
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
        else -> dialog.copy(index = index) to null
    }
}

/** A held direction stops before the empty tail; a fresh press can explore it. */
fun softStop(before: GridFocus, after: GridFocus, lastOccupiedIndex: Int, repeatCount: Int): GridFocus =
    if (repeatCount > 0 && after.chrome == null && after.cellIndex > lastOccupiedIndex &&
        after.cellIndex > before.cellIndex) before else after
