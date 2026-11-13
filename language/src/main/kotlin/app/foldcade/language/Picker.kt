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

    /**
     * Not an island: Y's menu, which grows out of the focused tile on the grid's screen and sits
     * beside it. L1 and R1 never draw it, and pressing either swaps it for their own.
     */
    Tile,
}

/** [depth] is how far in a level sits, so going in and coming back move opposite ways. */
enum class PanelLevel(val depth: Int) {
    Root(0),

    /** One saved RomM server's edit and sign-out actions, opened from Settings. */
    RommConnection(1),

    /** Y's menu for the focused tile, beside it ([Side.Tile]). */
    Actions(1),

    /** Add to: Home, then the collections the focused game can join, each checked while it is in it. */
    Collections(2),

    /** L1's Edit home: the settings for the whole board. The focused tile's actions are Y's. */
    EditHome(1),
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

/** RomM's saved sign-in could not be read. Sign in opens its form, with the phone code; Not now only closes. */
fun signInAgainPrompt(screen: HostScreen = HostScreen.Top): DialogState = DialogState(
    kind = DialogKind.ReLogin,
    title = Copy.signInAgainTitle,
    body = Copy.signInAgainBody,
    buttons = listOf(DialogButton.SignInRomm, DialogButton.NotNow),
    index = 0,
    safeIndex = 1,
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
    FirmwareFolder,

    /** Restart a game still open in its player. Not now leaves it paused. */
    RestartGame,

    /** Close a paused game and the apps opened beside its current run. */
    CloseGame,

    /** Android's "Modify system settings" access, for the R1 brightness slider. */
    Brightness,

    /** Notification access, for R1's notices. */
    NoticeAccess,

    /** All of one Foldcade notice's text. */
    Notice,

    /** Settings › SteamGridDB with a key saved. Only Forget key forgets it. */
    ForgetArtKey,

    /**
     * Offers to show Foldcade RetroArch's installed cores, once. Before a launch, Play anyway
     * starts the game and Back cancels it; before any launch, Not now settles the offer.
     */
    RetroArchCores,

    /** Eden was given files. Continue opens its App info. Not now only closes. */
    EdenRestart,

    /** The picker was not left on Eden's root. Try again opens it again. Play without syncing starts a held launch; Back cancels it. */
    EdenWrongFolder,

    /** Something RetroArch needs before the game can start, such as a core. */
    RetroArchSetup,
    RetroArchDownload,

    /** The code to type on a computer while Foldcade pairs with it. Not now stops. */
    PairHost,

    /** Stop listing a paired computer's games. Opens on Not now. */
    ForgetHost,

    /** How to point a player at the folder Foldcade hosts for its saves. Opens on Open. */
    HostedSaves,

    /** Eden cannot place an incoming save. Not now cancels the held launch. */
    EdenSaveHold,

    /** Settings › System › Backup › Play history: export it to a file, or import one. Opens on Export. */
    PlayHistory,

    /** What an export or an import did. Ok only closes it. */
    PlayHistoryDone,
    Backup,
    RestoreBackup,
    BackupDone,

    /** Sign out of RomM, or remove a server that waits for a sign-in. Opens on Not now. */
    RommSignOut,
    ThorSetup,
    RestoreThorSetup,

    /** Once: ROM folders were seen, and Allow opens Android's all-files access. Opens on Allow. */
    FindGames,

    /**
     * Ways to bring games: Find games while ROM folders wait for access, a folder, and RomM.
     * Home's Add games tile opens it, and the first run asks it once when no ROM folder was seen.
     */
    AddGames,
}

enum class DialogButton {
    ApplyThorSetup, RestoreThorSetup, ThorAppInfo, ThorFileAccess,
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
    AllowFiles,
    Restart,
    ForgetHost,

    /** Opens the player [DialogState.subject] names. */
    OpenPlayer,
    TryAgain,
    ExportPlay,
    ImportPlay,
    PlayHistory,
    BackUp,
    PickRestore,
    UndoRestore,
    RedoRestore,
    RestoreBackup,
    Library,
    SignOut,

    /** Android's all-files access, for the ROM folders Foldcade saw. */
    FindGames,

    /** Android's folder picker, at the ROM folder Foldcade saw when it saw one. */
    AddFolder,

    /** RomM's sign-in form, with its phone code. */
    SignInRomm,

    /** Before a launch, the game starts without what the dialog offered, such as RetroArch's cores. */
    PlayAnyway,

    /** The missing player's own download page, which [DialogState.subject] names. */
    GetPlayer,

    /** Android's Wi-Fi panel, for a step that needs the internet while there is none. */
    Wifi,

    /**
     * Not a button: Back on a dialog whose [DialogState.safeIndex] is [BACK_CLOSES]. It only
     * closes, and a held launch is cancelled, never started.
     */
    Dismiss,
}

/** The words a dialog button shows. [subject] is [DialogState.subject], which names the player to open. */
fun dialogButtonLabel(button: DialogButton, subject: String? = null): String = when (button) {
    DialogButton.ApplyThorSetup -> ThorSetupCopy.apply
    DialogButton.RestoreThorSetup -> ThorSetupCopy.restore
    DialogButton.ThorAppInfo -> ThorSetupCopy.appInfo
    DialogButton.ThorFileAccess -> ThorSetupCopy.fileAccess
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
    DialogButton.AllowFiles -> Copy.allowFiles
    DialogButton.Restart -> Copy.restart
    DialogButton.ForgetHost -> Copy.forgetHost
    DialogButton.OpenPlayer -> openPlayerLabel(subject)
    DialogButton.TryAgain -> Copy.tryAgain
    DialogButton.ExportPlay -> PlayCopy.export
    DialogButton.ImportPlay -> PlayCopy.import
    DialogButton.PlayHistory -> PlayCopy.history
    DialogButton.BackUp -> BackupCopy.backUp
    DialogButton.PickRestore, DialogButton.RestoreBackup -> BackupCopy.restore
    DialogButton.UndoRestore -> BackupCopy.undo
    DialogButton.RedoRestore -> BackupCopy.redo
    DialogButton.Library -> SettingsCopy.library
    DialogButton.SignOut -> if (subject == ROMM_REMOVE) Copy.remove else Copy.signOut
    // The body says what it allows, as the one-time offer's Allow does.
    DialogButton.FindGames -> Copy.allowUsage
    DialogButton.AddFolder -> Copy.addFolder
    DialogButton.SignInRomm -> FoundFolderCopy.signInRomm
    DialogButton.PlayAnyway -> Copy.playAnyway
    DialogButton.GetPlayer -> listOfNotNull(Copy.getPlayer, subject).joinToString(" ")
    DialogButton.Wifi -> Copy.wifi
    DialogButton.Dismiss -> Copy.back
}

/**
 * What Back does on [dialog], named under it: the button it presses, or, when that is no button
 * or only OK, Cancel while it stops a held launch and Close otherwise.
 */
fun dialogBackLabel(dialog: DialogState): String {
    val button = dialog.buttons.getOrNull(dialog.safeIndex)
    return when {
        button == null || button == DialogButton.Ok -> if (dialog.holdsLaunch) Copy.cancelLaunch else Copy.close
        else -> dialogButtonLabel(button, dialog.subject)
    }
}

/** A dialog's body as a lead line and the numbered steps after it, either of which may be missing. */
data class DialogBody(val intro: String?, val steps: List<String>)

/**
 * Splits a body written one "1. …" a line, after any plain lines, into its steps, without their
 * numbers. A body with fewer than two steps stays one paragraph, as it is written.
 */
fun dialogBodyParts(body: String): DialogBody {
    val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val first = lines.indexOfFirst { it.startsWith("1.") }
    val steps = if (first < 0) null else numberedSteps(lines.drop(first).joinToString("\n"))
    if (steps == null) return DialogBody(body.takeIf { it.isNotBlank() }, emptyList())
    return DialogBody(lines.take(first).joinToString(" ").takeIf { it.isNotEmpty() }, steps)
}

/**
 * The button Back presses, when that button names what Back does: it wears Back's key on its
 * chip, and the card names no Back key of its own (review-B S11: "Not now … B Not now").
 * Null when Back only closes without pressing a button, which the card names as Close or Cancel.
 */
fun dialogBackButton(dialog: DialogState): Int? =
    dialog.safeIndex.takeIf { dialog.buttons.getOrNull(it) != null }

/**
 * The keys a dialog names under its card. A on a lit button needs no word, so only Back is
 * named, and only when no button wears its key ([dialogBackButton]).
 */
fun dialogHints(dialog: DialogState, faceMap: FaceMap): List<HintKey> =
    if (dialogBackButton(dialog) != null) emptyList()
    else listOf(HintKey(promptKeyOf(faceMap.backKey) ?: PromptKey.FaceB, dialogBackLabel(dialog)))

data class DialogState(
    val kind: DialogKind,
    val title: String,
    val body: String,
    val buttons: List<DialogButton>,
    val index: Int,
    /** The button Back presses. [BACK_CLOSES] when Back only closes and no button says so. */
    val safeIndex: Int,
    val screen: HostScreen,
    /** A second muted line. The missing-player dialog lists package names here. */
    val detail: String? = null,
    /** What the dialog is about, such as the computer a pairing dialog names. */
    val subject: String? = null,
    /** Where its opener put it, retained while an occupied screen displaces it. */
    val preferredScreen: HostScreen = screen,
    /**
     * A launch waits on this answer. Back must never start it: [safeIndex] is then [BACK_CLOSES]
     * or a button that stops it, and the explicit way to play is a button of its own.
     */
    val holdsLaunch: Boolean = false,
    /**
     * A code the player carries to another device, as Moonlight's pairing PIN. It stands
     * alone, large, under the title, with a note that Foldcade is waiting for it.
     */
    val code: String? = null,
)

/**
 * A [DialogState.safeIndex] for a dialog whose Back is no button on it: Back sends
 * [DialogButton.Dismiss], which closes it and cancels any launch it held.
 */
const val BACK_CLOSES = -1

/** One of Moonlight's computers, for its Settings › Library switch. [key] is its lower-case UUID. */
data class StreamHostSetting(val key: String, val name: String, val paired: Boolean)

/** Foldcade shows [pin]; the user types it into Sunshine or Apollo on [name]. */
fun pairHostDialog(name: String, pin: String, key: String, screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.PairHost,
    title = Copy.pairHostTitle(name),
    body = Copy.pairHostBody,
    code = pin,
    buttons = listOf(DialogButton.NotNow),
    index = 0,
    safeIndex = 0,
    screen = screen,
    subject = key,
)

/** [DialogState.subject] of the confirmation that removes a RomM server waiting for a sign-in. */
const val ROMM_REMOVE = "remove"

/**
 * Signing out of RomM stops its saves syncing, and its games leave Home, so it asks first,
 * on Not now. Signed out already, the same row removes the server.
 */
fun rommSignOutDialog(signedOut: Boolean, screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.RommSignOut,
    title = if (signedOut) Copy.removeRommTitle else Copy.signOutRommTitle,
    body = if (signedOut) Copy.removeRommBody else Copy.signOutRommBody,
    buttons = listOf(DialogButton.SignOut, DialogButton.NotNow),
    index = 1,
    safeIndex = 1,
    screen = screen,
    subject = if (signedOut) ROMM_REMOVE else null,
)

/** The RomM form, to sign in, from wherever RomM waits: R1's notice, a restored gap, its Y menu. */
fun PickerModel.openRommSignIn(grid: GridFocus): Pair<PickerModel, Effect?> = copy(
    panel = null,
    focus = grid,
    connectOpen = true,
    connectScreen = layout.pageScreen,
    connectIndex = 0,
    connectKind = ConnectKind.Romm,
    connectReturnPanel = null,
    connectReturnSettings = null,
) to Effect.EditRommConnection

/** R1's standing notice while RomM waits for a sign-in. It has no X: it goes once RomM is signed in. */
const val ROMM_SIGN_IN_NOTICE = "foldcade:romm-sign-in"

fun rommSignInNotice(postedMillis: Long): FoldNotice = FoldNotice(
    id = ROMM_SIGN_IN_NOTICE,
    title = Copy.rommSignInNoticeTitle,
    body = Copy.rommSignInNoticeBody,
    postedMillis = postedMillis,
    dismissable = false,
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

/** Settings › System › Backup › Play history. Neither way loses anything, so it opens on Export. */
fun playHistoryDialog(screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.PlayHistory,
    title = PlayCopy.history,
    body = PlayCopy.historyBody,
    buttons = listOf(DialogButton.ExportPlay, DialogButton.ImportPlay, DialogButton.NotNow),
    index = 0,
    safeIndex = 2,
    screen = screen,
)

/** What an export or an import did, in a line. */
fun playHistoryDone(message: String, screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.PlayHistoryDone,
    title = PlayCopy.history,
    body = message,
    buttons = listOf(DialogButton.Ok),
    index = 0,
    safeIndex = 0,
    screen = screen,
)

/** The automatic ask. L1 can open the same explainer later. It never replaces another dialog. */
fun shouldOfferUsageAccess(
    hasTrackedSession: Boolean,
    granted: Boolean,
    alreadyOffered: Boolean,
    dialogOpen: Boolean,
): Boolean = hasTrackedSession && !granted && !alreadyOffered && !dialogOpen

/** A cached GameNative pin can outlive its launcher permission or its original shortcut. */
fun gameNativeShortcutPrompt(screen: HostScreen): DialogState = playerNotice(
    "Add that shortcut again",
    "In GameNative, choose Add to home screen for this game again.",
    screen,
)

fun homePrompt(screen: HostScreen = HostScreen.Bottom, apps: List<String> = emptyList()): DialogState = DialogState(
    kind = DialogKind.Home,
    title = Copy.homePromptTitle,
    body = if (apps.isEmpty()) Copy.homePromptBody else FoundFolderCopy.homeBody(apps),
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

/**
 * No installed player runs this game. With [canGet], Get opens [playerName]'s own download
 * page and is focused; OK only closes. [alsoRuns] names the other players that would run it.
 */
fun missingPlayerDialog(playerName: String, screen: HostScreen, canGet: Boolean = false, alsoRuns: List<String> = emptyList()): DialogState = DialogState(
    kind = DialogKind.MissingPlayer,
    title = Copy.missingPlayerTitle(playerName),
    body = if (canGet) Copy.missingPlayerGetBody else Copy.missingPlayerBody,
    buttons = if (canGet) listOf(DialogButton.GetPlayer, DialogButton.NotNow) else listOf(DialogButton.Ok),
    index = 0,
    safeIndex = if (canGet) 1 else 0,
    screen = screen,
    detail = alsoRunsLine(alsoRuns),
    subject = playerName,
)

/** Closing the running game loses what it did not save, so this opens on Not now, as [closeGameDialog] does. */
fun closeBothPanelDialog(screen: HostScreen, running: String? = null): DialogState = DialogState(
    kind = DialogKind.ClosePlayer,
    title = running?.let(Copy::closeRunningTitle) ?: Copy.closePlayerTitle,
    body = Copy.closePlayerBody,
    buttons = listOf(DialogButton.CloseIt, DialogButton.NotNow),
    index = 1,
    safeIndex = 1,
    screen = screen,
    holdsLaunch = true,
)

/** Closing loses what was not saved, so it opens on Not now, and Back keeps the game too. */
fun closeGameDialog(screen: HostScreen, companions: List<String> = emptyList()): DialogState = DialogState(
    kind = DialogKind.CloseGame,
    title = Copy.closeGameTitle,
    body = listOf(Copy.closeGameBody, Copy.companionCloseLine(companions)).filter { it.isNotEmpty() }.joinToString(" "),
    buttons = listOf(DialogButton.CloseIt, DialogButton.NotNow),
    index = 1,
    safeIndex = 1,
    screen = screen,
)

/** Restarting loses what was not saved, so it opens on Not now. */
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
    // Back cancels the launch. Playing without syncing is its own button, never a reflex press.
    safeIndex = BACK_CLOSES,
    screen = screen,
    holdsLaunch = true,
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
        // Before a launch, Back cancels it rather than playing without syncing.
        safeIndex = if (beforeLaunch) BACK_CLOSES else 1,
        screen = screen,
        subject = playerName,
        holdsLaunch = beforeLaunch,
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

/**
 * Opens on Continue: the picker is the only way Foldcade learns which cores RetroArch has.
 * A launch waits on it. Play anyway settles the offer and starts it; Back cancels it.
 */
fun retroArchCoresDialog(screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.RetroArchCores,
    title = Copy.retroArchCoresTitle,
    body = Copy.retroArchCoresBody,
    buttons = listOf(DialogButton.ContinueGrant, DialogButton.PlayAnyway),
    index = 0,
    safeIndex = BACK_CLOSES,
    screen = screen,
    holdsLaunch = true,
)

/**
 * The same offer before any launch, once the library has games RetroArch plays ([systems]),
 * so the first game starts without a stop. Continue opens the picker; Not now, and Back, settle it.
 */
fun retroArchCoresFirstDialog(screen: HostScreen, systems: List<String>): DialogState =
    retroArchCoresDialog(screen).copy(
        body = FoundFolderCopy.retroArchCoresBody(systems),
        buttons = listOf(DialogButton.ContinueGrant, DialogButton.NotNow),
        safeIndex = 1,
        holdsLaunch = false,
    )

/** An unplaced save stops the launch; unresolved save destinations offer an explicit per-launch opt-out. */
fun saveNotPlacedDialog(title: String, screen: HostScreen, playAnyway: Boolean = false, needsEdenUser: Boolean = false, incompatible: Boolean = false): DialogState = DialogState(
    kind = if (playAnyway) DialogKind.EdenSaveHold else DialogKind.Ok,
    title = "${Copy.saveNotPlaced} $title",
    body = when {
        playAnyway && incompatible -> SavePickerCopy.incompatible
        needsEdenUser -> Copy.edenSaveNeedsUser
        playAnyway -> Copy.saveUnresolved
        else -> Copy.saveNotPlacedBody
    },
    buttons = when {
        playAnyway && incompatible -> listOf(DialogButton.PlayWithoutSyncing, DialogButton.NotNow)
        playAnyway -> listOf(DialogButton.SetUpSaves, DialogButton.PlayWithoutSyncing, DialogButton.NotNow)
        else -> listOf(DialogButton.Ok)
    },
    index = if (playAnyway && incompatible) 1 else 0,
    safeIndex = if (playAnyway && incompatible) 1 else if (playAnyway) 2 else 0,
    screen = screen,
    holdsLaunch = playAnyway,
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

/**
 * Opens on Try again, which returns to Eden's root. With a launch held ([launchHeld]), Back
 * cancels it and Play without syncing starts it, unless Eden needs the folder to start at
 * all ([needed]), when Not now cancels too. From Settings, Not now and Back only close.
 */
fun edenWrongFolderDialog(screen: HostScreen, launchHeld: Boolean = false, needed: Boolean = false): DialogState = DialogState(
    kind = DialogKind.EdenWrongFolder,
    title = Copy.edenWrongFolderTitle,
    body = Copy.edenWrongFolderBody,
    buttons = listOf(DialogButton.TryAgain, if (launchHeld && !needed) DialogButton.PlayWithoutSyncing else DialogButton.NotNow),
    index = 0,
    safeIndex = if (launchHeld) BACK_CLOSES else 1,
    screen = screen,
    holdsLaunch = launchHeld,
)

fun coreDownloadFailedDialog(coreName: String, screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.RetroArchDownload,
    title = Copy.coreDownloadFailed(coreName),
    body = Copy.coreDownloadFailedBody,
    buttons = listOf(DialogButton.TryAgain, DialogButton.OpenRetroArch, DialogButton.NotNow),
    index = 0,
    safeIndex = 2,
    screen = screen,
    holdsLaunch = true,
)

/** RetroArch has not been allowed to read the device's storage. */
fun retroArchStorageDialog(screen: HostScreen): DialogState =
    retroArchSetupDialog(Copy.retroArchStorageTitle, Copy.retroArchStorageBody, screen).copy(
        buttons = listOf(DialogButton.AllowFiles, DialogButton.NotNow),
    )

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
    holdsLaunch = true,
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

/**
 * A RomM game Foldcade waited on and couldn't find. With no internet, that is the cause, so it
 * says so and offers Wi-Fi, as Saves does offline (review-D 217); B closes. Online, RomM answered
 * without the game, and OK closes.
 */
fun catalogGameUnavailableDialog(offline: Boolean, screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.Ok,
    title = if (offline) Copy.noInternet else Copy.gameUnavailable,
    body = if (offline) Copy.noInternetGameBody else Copy.gameUnavailableBody,
    buttons = listOf(if (offline) DialogButton.Wifi else DialogButton.Ok),
    index = 0,
    safeIndex = if (offline) BACK_CLOSES else 0,
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

/**
 * A folder just picked that holds no games, said on the screen it was picked from, with the
 * way to pick the right one. It stays a source: games copied in later show up by themselves.
 */
fun emptyFolderDialog(name: String, screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.Folder,
    title = Copy.emptyFolderTitle(name),
    body = Copy.emptyFolderBody,
    buttons = listOf(DialogButton.AddFolder, DialogButton.Ok),
    index = 0,
    safeIndex = 1,
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

/**
 * Ways to bring games, the recommended one first. While ROM folders Foldcade saw wait for
 * access ([offer]) that is Find games; with none seen it is RomM, whose sign-in is a phone
 * code away. [ask] is the first run's one-time question, which has Not now; Home's Add games
 * tile opens it without one, and Back closes it.
 */
fun addGamesDialog(screen: HostScreen, offer: RomFolderOffer?, ask: Boolean = false): DialogState {
    val buttons = buildList {
        if (offer != null) add(DialogButton.FindGames)
        if (offer == null) add(DialogButton.SignInRomm)
        add(DialogButton.AddFolder)
        if (offer != null) add(DialogButton.SignInRomm)
        if (ask) add(DialogButton.NotNow)
    }
    return DialogState(
        kind = DialogKind.AddGames,
        title = if (ask) FoundFolderCopy.noRomsTitle else Copy.addGames,
        body = if (offer != null) FoundFolderCopy.offerBody(offer.places, offer.systems) else FoundFolderCopy.noRomsBody,
        buttons = buttons,
        index = 0,
        safeIndex = if (ask) buttons.lastIndex else BACK_CLOSES,
        screen = screen,
    )
}

/** The one-time offer to read the ROM folders Foldcade saw: [places] and the [systems] their folders name. */
fun findGamesPrompt(screen: HostScreen, places: List<String>, systems: List<String>): DialogState = DialogState(
    kind = DialogKind.FindGames,
    title = FoundFolderCopy.offerTitle,
    body = FoundFolderCopy.offerBody(places, systems),
    buttons = listOf(DialogButton.Allow, DialogButton.NotNow),
    index = 0,
    safeIndex = 1,
    screen = screen,
)

/** Debug captures. A negative [index] keeps the dialog's own focus. Back still uses [DialogState.safeIndex]. Unknown names are ignored. */
fun previewDialogState(kind: String, index: Int, screen: HostScreen): DialogState? {
    val dialog = when (kind) {
        "home" -> homePrompt(screen)
        "home-streams" -> homePrompt(screen, listOf("Moonlight", "DroidDeck"))
        "find-games" -> findGamesPrompt(screen, listOf("Emulation/roms"), listOf("Nintendo DS", "Sega Genesis"))
        "folder" -> folderExplainer(screen)
        "add-games" -> addGamesDialog(screen, RomFolderOffer(listOf("ROMs"), listOf("Nintendo DS", "Sega Genesis"), "primary:ROMs"))
        "no-roms" -> addGamesDialog(screen, null, ask = true)
        "retroarch-cores-first" -> retroArchCoresFirstDialog(screen, listOf("Sega Genesis", "Game Boy Advance"))
        "relogin" -> signInAgainPrompt(screen)
        "save-folder" -> saveFolderDialog(screen)
        "save-folder-azahar" -> saveFolderDialog(screen, syncs = true, playerName = "Azahar", opensThere = true)
        "close-player" -> closeBothPanelDialog(screen, "Bravely Second")
        "missing-player" -> missingPlayerDialog("Azahar", screen, canGet = true)
        "forget-art-key" -> forgetArtKeyPrompt(screen)
        "retroarch-cores" -> retroArchCoresDialog(screen)
        "eden-restart" -> edenRestartDialog(screen)
        "eden-wrong-folder" -> edenWrongFolderDialog(screen, launchHeld = true)
        "core-download-failed" -> coreDownloadFailedDialog("mGBA", screen)
        "catalog-offline" -> catalogGameUnavailableDialog(offline = true, screen = screen)
        "retroarch-storage" -> retroArchStorageDialog(screen)
        "missing-bios-romm" -> playerNotice(FirmwareCopy.title, FirmwareCopy.missing("Sega CD", listOf("bios_CD_U.bin"), "RetroArch/system", rommConnected = true), screen)
        "missing-bios" -> playerNotice(FirmwareCopy.title, FirmwareCopy.missing("Sega CD", listOf("bios_CD_U.bin"), "RetroArch/system"), screen)
        "firmware-folder" -> firmwareFolderDialog("retroarch.sega-cd", FirmwareCopy.folder("RetroArch", "RetroArch/system", listOf("bios_CD_U.bin")), screen)
        else -> return null
    }
    val last = dialog.buttons.lastIndex.coerceAtLeast(0)
    return if (index < 0) dialog else dialog.copy(index = index.coerceIn(0, last))
}

data class Hold(
    val index: Int,
    val origin: Int,
    val orderBefore: List<Int>,
)

sealed interface Row {
    data class PluginGrant(val packageName: String, val scope: String) : Row
    data class PluginRouting(val packageName: String) : Row
    data class PluginInstall(val packageName: String) : Row
    data object IconTheme : Row
    data object Theme : Row
    data object Background : Row
    data object MotionSpeed : Row
    data object UsageAccess : Row
    /** Opens Android's optional all-files access page. Its value follows Android. */
    data object FindSaves : Row

    /** Settings › Library's way to the same access, which finds ROM folders. Its value follows Android. */
    data object FindGames : Row

    /** Opens Set up saves, which walks every emulator's remaining step. */
    data object SaveSetup : Row
    /** Settings › System › Layout: which screen has the grid, which the hero, or the grid alone on top. */
    data object ScreenLayout : Row
    /** Settings › System › Play on: the screen A opens a game on. */
    data object PlayOn : Row
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

    /** The RomM connect form, from a place that waits for RomM: a restored gap's Y menu. */
    data object SignInRomm : Row

    /** Add a server, whether it is the first or an additional connection. */
    data object AddRomm : Row
    data class Notice(val id: String) : Row

    /** Shown in R1 until Foldcade may read notifications. A asks. */
    data object NoticeAccess : Row

    /** Under two or more notices X could remove. */
    data object ClearNotices : Row
    data class SignOut(val pluginId: String, val label: String) : Row
    data class ForgetFolder(val uri: String, val label: String) : Row
    data class PickFolderAgain(val uri: String) : Row
    data class PlayerSave(val playerId: String) : Row
    /** The folder Foldcade hosts for a player's saves, and whether the player has it. */
    data class HostedSaves(val playerId: String) : Row

    /** The player's memory card: Syncing, or Set up. */
    data class SaveSync(val playerId: String) : Row

    /** One-time setup in the player, such as firmware, before a game can start. */
    data class PlayerSetup(val playerId: String) : Row
    data class ThorSetup(val playerId: String) : Row

    /** Whether Foldcade can see RetroArch's cores. A shows them. */
    data object RetroArchCores : Row

    /** Whether Foldcade can add Eden's keys and firmware from RomM. A opens Eden's folder. */
    data object EdenFolder : Row

    /** Eden needs a step of its own before its saves sync, as a user while it has none. A opens Eden. */
    data object EdenUser : Row
    data object Apps : Row
    /** Edit home's way into the one Hidden place: hidden games and apps. */
    data object Hidden : Row

    /** Edit home's way into Folders: every folder Foldcade makes, on Home or off it. */
    data object Folders : Row

    /** The switch, in Y's menu in Folders, that puts the focused folder on Home or takes it off. */
    data object FolderOnHome : Row
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

    data object OpenWith : Row
    data object Systems : Row
    data class SystemPlayer(val platformId: String) : Row

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
    data object CloseGame : Row
    data object MoveGame : Row

    /** X on a session in Play activity's list: held, it takes the session out. Not a menu row. */
    data object RemoveSession : Row

    /** A game's play: its time, and other games' play to move onto it. Opens Play activity for the game. */
    data object PlayTime : Row

    /** A game's page: its cover, facts, and summary. Opens [GameAboutPage]. */
    data object About : Row

    /** A on another game in a game's Play activity: held, it moves that game's play here. Not a menu row. */
    data object MovePlay : Row

    /** Settings › System: export the play history to a file, or import one. */
    data object Backup : Row
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
private val placeRows: Set<Row> = setOf(Row.Home, Row.AllLibrary, Row.Apps)

/**
 * What makes a tile the user's own, in the order Y's menu offers them. The first
 * [quickTileColumns] of them lead the menu as round tiles; any past those are rows.
 */
val verbRows: List<Row> = listOf(
    Row.MoveTile, Row.AddToCollection, Row.ChooseArt, Row.Saves, Row.RestartGame, Row.CloseGame, Row.RenameFolder, Row.FolderIcon, Row.NewCollection,
)

/**
 * The rows [rows] draws as round tiles, side by side in lines of [quickTileColumns]: R1's
 * Android screens, L1's places, or the verbs that lead a tile's menu, under Undo or Sign in
 * when one is there. Empty when the page has none. Verbs lead only a tile's menu, so a verb further down
 * a page, such as Add to's New collection, stays a row.
 */
fun tileBlock(rows: List<Row>): IntRange {
    val start = rows.indexOfFirst { it is Row.QuickTile || it in placeRows }
    if (start >= 0) {
        val end = (start until rows.size).firstOrNull { rows[it] !is Row.QuickTile && rows[it] !in placeRows } ?: rows.size
        return start until end
    }
    // Undo, and a restored gap's Sign in, sit over the verbs as rows of their own.
    val lead = rows.takeWhile { it == Row.Undo || it == Row.SignInRomm }.size
    val verbs = rows.drop(lead).take(quickTileColumns).takeWhile { it in verbRows }.size
    return lead until lead + verbs
}

/** The word under a tile. A tile has less room than a row, so it says less. */
fun tileLabel(row: Row): String? = when (row) {
    Row.Home -> Copy.home
    Row.AllLibrary -> LibraryCopy.tile
    Row.Apps -> Copy.apps
    Row.MoveTile -> Copy.move
    Row.AddToCollection -> Copy.addTo
    Row.ChooseArt -> ArtCopy.art
    Row.Saves -> SavePickerCopy.title
    Row.RenameFolder -> Copy.rename
    Row.FolderIcon -> Copy.icon
    // Two lines under its tile, so it never reads as a bare "New".
    Row.NewCollection -> Copy.newCollection
    Row.RestartGame -> Copy.restart
    Row.CloseGame -> Copy.closeGame
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
    /** How Android's picker opens on the save folder. */
    val pick: FolderPick = FolderPick.Browse,
    /** True when all-files access could find the save folder without a pick. */
    val findable: Boolean = false,
    /** What the user does once, in the player, to give it the folder Foldcade hosts. */
    val hostedSteps: String? = null,
    /** Where Foldcade found the folder on its own, such as `saves/Azahar`, so it is never bound unseen. Null for a chosen one. */
    val foundIn: String? = null,
    val thorSetup: ThorSetupSetting? = null,
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
    /** What Eden needs once before its saves sync, as a user while it has none, or null. Read from the folder Foldcade holds. */
    val setupSteps: String? = null,
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
    Row.Apps,
    Row.EditHome,
    Row.Settings,
)

/**
 * What applies to the whole board, never to one tile, which is Y's: the open folder's
 * Sort, Tidy while it has gaps, the Recently played tile, whether new games join it,
 * which of Foldcade's folders are on it, and what is hidden from it.
 */
fun editHomeRows(model: PickerModel): List<Row> = buildList {
    if (model.tileActions?.boardSort != null) add(Row.SortFolder)
    if (model.tileActions?.tidyBoard == true) add(Row.Tidy)
    addAll(listOf(Row.RecentOnHome, Row.AddNewGames, Row.Folders, Row.Hidden))
}

/** The Sort a menu's Sort row steps: Edit home's is the open folder's, Y's the folder tile's. */
private fun PickerModel.sortChoice(): FolderSortChoice? =
    if (panel?.level == PanelLevel.EditHome) tileActions?.boardSort else tileActions?.sort

data class SignedInBackend(
    val pluginId: String,
    val label: String,
)

/**
 * One folder the library reads. [uri] is its document tree; [label] its own name. [artAndInfo]
 * marks a media tree that holds a frontend's art and facts rather than games. [path] is where it
 * is on the device, [games] how many games the last scan found in it, and [media] whether it
 * holds art and facts at all.
 */
data class LibraryFolder(
    val uri: String,
    val label: String,
    val artAndInfo: Boolean = false,
    val pickAgain: Boolean = false,
    val path: String? = null,
    val games: Int = 0,
    val media: Boolean = false,
    /** Foldcade found it by itself (`ROMs`, `Emulation/roms`). Remove keeps it away for good. */
    val found: Boolean = false,
    /** Read through, and it brought no games, art or facts: its page offers Pick again. */
    val empty: Boolean = false,
)

enum class ConnectField {
    FoundServer,
    Origin,
    Token,
    Save,
}

/** What the connect form saves: a RomM server and token, or a SteamGridDB key. */
enum class ConnectKind {
    Romm,
    ArtKey,
}

/**
 * The form's rows, top to bottom. RomM's one button pairs a phone while the token is empty and
 * connects once one is typed, so a new code needs no button of its own.
 */
fun connectFields(kind: ConnectKind = ConnectKind.Romm, foundServer: Boolean = false): List<ConnectField> = when (kind) {
    ConnectKind.Romm -> (if (foundServer) listOf(ConnectField.FoundServer) else emptyList()) + listOf(ConnectField.Origin, ConnectField.Token, ConnectField.Save)
    ConnectKind.ArtKey -> listOf(ConnectField.Token, ConnectField.Save)
}

/** RomM's device pairing: the [code] to approve, and the page at [url] that approves it. */
data class PairingCode(val code: String, val url: String) {
    /** "EUUJ NSZV": in fours, so it reads aloud and types on a phone without losing the place. */
    val spaced: String get() = code.chunked(4).joinToString(" ")

    /** The page without its scheme or query, short enough to type: "romm.example/pair/device". */
    val shortUrl: String get() = url.substringAfter("://").substringBefore('?').substringBefore('#').trimEnd('/')
}

/**
 * RomM's button says what A does now: Connect once a token is typed (or before there is a
 * server to pair with), Pair with phone for a new code, and nothing to press while a phone
 * can approve the code on screen.
 */
/**
 * What to fix in a typed RomM address Foldcade can't use, under the phone's square: no http(s)://
 * at all, a username or password in it, or else the address itself, as http:// off the home
 * network, whose warning sits under the field.
 */
fun rommAddressFix(origin: String): String {
    val text = origin.trim()
    val scheme = text.substringBefore("://", "").lowercase()
    return when {
        scheme != "http" && scheme != "https" -> Copy.rommAddressScheme
        // Before the http warning: a LAN http:// address with a password still warns about encryption.
        '@' in text.substringAfter("://").substringBefore('/') -> Copy.rommAddressCredentials
        else -> Copy.rommFixAddress
    }
}

/** The RomM form's title, which the page beside it wears too: editing, signing in again, or adding. */
fun rommConnectTitle(model: PickerModel, saved: Boolean): String = when {
    model.connectReturnPanel != null && !model.rommSignIn -> Copy.editRommConnection
    saved -> Copy.signInRomm
    else -> Copy.addRommServer
}

fun rommConnectAction(
    busy: Boolean,
    tokenTyped: Boolean,
    originTyped: Boolean,
    code: PairingCode?,
    retry: Boolean,
    /** RomM gave no code at this address ([Copy.rommCodeUnavailable]): the button asks it again. */
    unavailable: Boolean = false,
): String = when {
    busy -> Copy.connecting
    tokenTyped -> Copy.connect
    // The phone is the way in until a token is typed: the button names what it will do, and
    // the phone's side says it waits ([rommWaitsForPhone]).
    !originTyped -> Copy.connect
    code != null && !retry -> Copy.connect
    // Not a second way in by phone beside the address QR: it asks RomM again (review-A 210-F1′).
    unavailable -> Copy.tryAgain
    else -> Copy.pairWithPhone
}

/**
 * The muted line under a pairing note. Once RomM gave no code at the address, the address is
 * the likely fault, so the line names it, and the QR's job when the phone can send another.
 */
fun rommPairingNext(hint: String, phoneQr: Boolean): String = when {
    hint == Copy.rommCodeUnavailable -> if (phoneQr) RommPhoneCopy.checkAddress else Copy.rommCheckAddress
    phoneQr -> RommPhoneCopy.scan
    else -> Copy.rommPairAgain
}

/** True while the form waits on the phone, not the button: it reads quieter, and the phone's side says so. */
fun rommWaitsForPhone(busy: Boolean, tokenTyped: Boolean, originTyped: Boolean, code: PairingCode?, retry: Boolean): Boolean =
    !busy && !tokenTyped && (!originTyped || code != null && !retry)

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
    /** A restored gap that waits for RomM, signed out: Sign in leads its menu. */
    val signIn: Boolean = false,
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
    val close: Boolean = false,
    /** The other screen, when Foldcade owns it and this player can move there. */
    val moveGame: HostScreen? = null,
    val saves: Boolean = false,
    val openWith: PlayerChoiceRow? = null,
    /** The game's play time, for its Play time row. Null or 0 with nothing [playFound] leaves the row out: not a game, or never played. */
    val playTime: Long? = null,
    /** With no play of its own, the time a gone game of its name left: the row says it was found. */
    val playFound: Long? = null,
    /** The tile is a game a library lists, so it has an About page. */
    val about: Boolean = false,
    /** The game these rows act on. Null for a tile that is not a game, or one off the home grid. */
    val game: String? = null,
    /** The board on screen has gaps to close, so Edit home offers Tidy. */
    val tidyBoard: Boolean = false,
    /** A folder tile's own Sort, in Y's menu: that folder and its order now. Null leaves the row out. */
    val sort: FolderSortChoice? = null,
    /** The open folder's Sort, in Edit home: it orders the whole folder, not the tile. */
    val boardSort: FolderSortChoice? = null,
    /** In Folders, the focused folder's switch: on while its tile is on Home. Null leaves the row out. */
    val folderOnHome: FolderSwitch? = null,
)

/** One of Foldcade's folders, and whether its tile is on Home. */
data class FolderSwitch(val folderId: String, val on: Boolean)

/** A folder's Sort row: which folder it orders, and how it orders it now. */
data class FolderSortChoice(val folderId: String, val sort: FolderSort)

/**
 * Y's menu for the tile. Undo leads when there is one. Then come the tile's own verbs
 * ([verbRows]), which draw as round tiles, and under them the rest as rows. A game still open
 * in its player adds Restart and Close to the verbs, after the tile's own. Add to holds
 * every place a game can be put, Home first, and New collection at its end, so a game's
 * menu has one tile for all of them. Remove takes the tile off the spot it is in, and Hide
 * is its own row, after it. Sort, on a folder tile, orders that folder and comes last. What
 * acts on the whole board, Tidy and the open folder's Sort, is Edit home's, not Y's.
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
    if (tile.signIn) add(Row.SignInRomm)
    if (tile.move) add(Row.MoveTile)
    if (tile.collections != null) add(Row.AddToCollection)
    if (tile.art) add(Row.ChooseArt)
    if (tile.saves) add(Row.Saves)
    // A game still open in its player starts over or closes from the round tiles the tile's own
    // verbs leave, or from the first rows under them, so the menu beside the tile fits (UX-02).
    if (tile.restart) add(Row.RestartGame)
    if (tile.close) add(Row.CloseGame)
    if (tile.rename) add(Row.RenameFolder)
    if (tile.icon) add(Row.FolderIcon)
    if (tile.newCollection && tile.collections == null) add(Row.NewCollection)
    if (tile.openWith != null) add(Row.OpenWith)
    // A game never played says nothing with "0m", so the row waits for real time or time found to move.
    if (tile.playTime != null && (tile.playTime > 0L || tile.playFound != null)) add(Row.PlayTime)
    if (tile.show) add(Row.ShowHidden)
    if (tile.showOnHome != null) add(Row.ShowOnHome)
    // After Show on Home, so Find's Select keeps showing where a game lives.
    if (tile.about) add(Row.About)
    if (tile.folderOnHome != null) add(Row.FolderOnHome)
    if (tile.moveTo != null) add(Row.MoveApp)
    if (tile.select) add(Row.SelectTile)
    if (tile.remove != null) add(Row.RemoveTile)
    if (tile.moveGame != null) add(Row.MoveGame)
    if (tile.hide) add(Row.Hide)
    if (tile.removeDownload) add(Row.RemoveDownload)
    if (tile.deleteShortcut) add(Row.DeleteShortcut)
    if (tile.sort != null) add(Row.SortFolder)
}

/**
 * The hint row for the focused menu row when its keys are not just A and B:
 * the slider names the D-pad, and a notice names X. Null leaves the usual row.
 */
fun panelHints(model: PickerModel): List<HintKey>? {
    val panel = model.panel ?: return null
    if (model.dialog != null) return null
    val back = HintKey(promptKeyOf(model.faceMap.backKey) ?: PromptKey.FaceB, Copy.back)
    val row = panelRows(panel, model).getOrNull(panel.index) ?: return null
    val verb = listOf(HintKey(promptKeyOf(model.faceMap.confirmKey) ?: PromptKey.FaceA, menuVerb(row, model)), back)
    return when (row) {
        Row.RemoveDownload, Row.DeleteShortcut -> listOf(HintKey(model.deleteHold?.key ?: promptKeyOf(model.faceMap.confirmKey) ?: PromptKey.FaceA, ""), back)
        Row.Brightness -> if (model.brightnessAllowed) listOf(HintKey(PromptKey.DpadSides, BrightnessCopy.adjust), back)
            else listOf(HintKey(promptKeyOf(model.faceMap.confirmKey) ?: PromptKey.FaceA, BrightnessCopy.allow), back)
        is Row.Notice -> {
            val notice = model.notices.firstOrNull { it.id == row.id } ?: return null
            buildList {
                val roles = actionFaces(model.faceMap)
                if (notice.id.startsWith(ThorSetupCopy.noticePrefix)) {
                    add(HintKey(promptKeyOf(model.faceMap.confirmKey) ?: PromptKey.FaceA, NoticeCopy.open))
                } else if (notice.savePlayerId != null || notice.settingsCategory != null || notice.firmwarePlayerId != null) {
                    add(HintKey(promptKeyOf(model.faceMap.confirmKey) ?: PromptKey.FaceA, Copy.setUp))
                }
                if (notice.id == ROMM_SIGN_IN_NOTICE) add(HintKey(promptKeyOf(model.faceMap.confirmKey) ?: PromptKey.FaceA, Copy.signIn))
                if (notice.openable) add(HintKey(promptKeyOf(model.faceMap.confirmKey) ?: PromptKey.FaceA, NoticeCopy.open))
                if (notice.dismissable) add(HintKey(roles.bottomGlyph, NoticeCopy.dismiss))
                add(back)
            }
        }
        else -> verb
    }
}

/**
 * What A does to a menu row, for the hint row: a switch turns on or off, a check adds or takes
 * out, a row that leads somewhere or a place opens, and any other row is chosen.
 */
fun menuVerb(row: Row, model: PickerModel): String = when (val control = rowControl(row, model)) {
    is RowControl.Toggle -> if (control.on) Copy.hintTurnOff else Copy.hintTurnOn
    is RowControl.Check -> if (control.on) Copy.hintTakeOut else Copy.hintAdd
    RowControl.Opens -> Copy.hintOpen
    else -> when (row) {
        Row.PlayActivity, Row.Home, Row.AllLibrary, Row.Apps, Row.Settings, is Row.QuickTile -> Copy.hintOpen
        else -> Copy.hintChoose
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
    fun changed(was: FolderSortChoice?, now: FolderSortChoice?) =
        was != null && now != null && was.folderId == now.folderId && was.sort != now.sort
    // So does Find's folder once its switch puts it on Home: the switch gives way to Show on Home.
    val switched = (previous.tileActions?.folderOnHome != null) != (next.tileActions?.folderOnHome != null)
    val resorted = changed(previous.tileActions?.sort, next.tileActions?.sort) ||
        changed(previous.tileActions?.boardSort, next.tileActions?.boardSort) || switched
    val retained = (if (previous.panel?.side == panel.side && !resorted) previous.menuRows else emptyList())
        .map { it.copy(rows = validMenuRows(it.rows, next)) }
    val page = panel.page()
    // R1's rows are its notices, so they follow Android's list: a dismissed notice leaves, a new one arrives.
    val live = panel.side == Side.Right && panel.level == PanelLevel.Root
    val pages = if (!live && retained.any { it.page == page }) retained else
        retained.filterNot { it.page == page } + MenuRows(page, livePanelRows(panel, next))
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
        Side.Tile -> emptyList()
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

/** How the last read of every library ended. Only RomM can be out of reach; a folder read always answers. */
enum class LibrarySync {
    /** Nothing has finished since Foldcade started. */
    Unknown,
    Done,
    RommUnreachable,
}

/** Sync games' value: what the last sync did, or that one is running. Never the action itself, which is A. */
fun librarySyncValue(model: PickerModel): String? = when {
    model.librarySyncing -> Copy.syncing
    model.librarySync == LibrarySync.RommUnreachable -> Copy.rommUnreachable
    model.librarySync == LibrarySync.Done -> Copy.synced
    else -> null
}

/** Label on the left, current value on the right. A null value is a single action row. */
data class RowText(val label: String, val value: String? = null)

/**
 * A save row that isn't set up yet: amber Not syncing while a game on Home plays on [playerId],
 * so its saves are at stake, and a quiet Not set up while none does, as its tile and Set up
 * saves say (review-B S10).
 */
private fun unsetSaves(playerId: String, model: PickerModel): String =
    if (playerId in model.boardEmulators) Copy.notSyncing else SaveSetupCopy.notSetUp

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
    Row.FindSaves -> RowText(SettingsCopy.findSaves, if (model.allFilesGranted) SettingsCopy.accessAllowed else SettingsCopy.accessAsk)
    Row.FindGames -> RowText(FoundFolderCopy.autoFindGames, if (model.allFilesGranted) SettingsCopy.accessAllowed else SettingsCopy.accessAsk)
    // How many emulators still wait, in the attention colour, until every one syncs.
    Row.SaveSetup -> RowText(SaveSetupCopy.title, savesDue(model).size.takeIf { it > 0 }?.let(SaveSetupCopy::toGo))
    Row.ScreenLayout -> RowText(ScreenCopy.layout, layoutLabel(model.layout))
    Row.PlayOn -> RowText(ScreenCopy.playOn, screenLabel(model.playOn))
    Row.OpenWith -> RowText(PlayerChoiceCopy.openWith, model.tileActions?.openWith?.value())
    Row.Systems -> RowText(PlayerChoiceCopy.systems)
    is Row.SystemPlayer -> model.systemPlayers.firstOrNull { it.platformId == row.platformId }.let {
        RowText(it?.label.orEmpty(), it?.choice?.value())
    }
    Row.SortFolder -> RowText(Copy.sort, folderSortLabel(model.sortChoice()?.sort ?: FolderSort.Custom))
    Row.Music -> RowText(MusicCopy.row, if (model.music.enabled) MusicCopy.on else MusicCopy.off)
    Row.MusicTrack -> RowText(MusicCopy.track, model.trackTitle)
    Row.MusicVolume -> RowText(MusicCopy.volume, MusicCopy.volumeLabel(model.music.volume).substringAfter("  "))
    Row.SetAsHome -> RowText(Copy.setAsHome)
    Row.AndroidSettings -> RowText(Copy.androidSettings)
    Row.Licenses -> RowText(LicenseCopy.row)
    is Row.SettingsTile -> RowText(settingsTileLabel(row.page, model))
    Row.Version -> RowText(AboutCopy.copyInfo, AboutCopy.copied.takeIf { model.versionCopied })
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
    // Only Settings › Library › Add lists these, under its own "Add".
    Row.AddFolder -> RowText(Copy.addFolderRow)
    Row.SyncLibrary -> RowText(Copy.syncGames, librarySyncValue(model))
    is Row.RommConnection -> RowText(row.label)
    // Signed out, the connection's first row signs in, and its last removes the server.
    is Row.EditRomm -> RowText(if (model.rommSignIn) Copy.signIn else Copy.editRommConnection)
    Row.SignInRomm -> RowText(Copy.signIn)
    Row.AddRomm -> RowText(Copy.addRommRow)
    // Both live on a page that already names the connection or the folder.
    is Row.SignOut -> RowText(if (row.pluginId == "romm" && model.rommSignIn) Copy.remove else Copy.signOut)
    is Row.ForgetFolder -> RowText(Copy.remove)
    is Row.PickFolderAgain -> RowText(BackupCopy.pickAgain)
    is Row.Notice -> RowText(model.notices.firstOrNull { it.id == row.id }?.title ?: "")
    Row.NoticeAccess -> RowText(NoticeCopy.access, NoticeCopy.accessValue)
    Row.ClearNotices -> RowText(NoticeCopy.clearAll)
    is Row.PlayerSave -> {
        val setting = model.playerSaves.firstOrNull { it.playerId == row.playerId }
        val chosen = when {
            setting?.chosen == true -> setting.foundIn ?: Copy.playerSavesSet
            setting?.installed != true || setting.hostedTitle != null -> Copy.choose
            else -> unsetSaves(row.playerId, model)
        }
        // A player whose saves live in the folder Foldcade hosts (DuckStation) asks for its own
        // folder only for its BIOS, so the row says that, and its Choose is not a save waiting.
        RowText(if (setting?.hostedTitle != null) Copy.biosFolder else Copy.saveFolder, chosen)
    }
    is Row.HostedSaves -> {
        val setting = model.playerSaves.firstOrNull { it.playerId == row.playerId }
        val inUse = when {
            setting?.hostedInUse == true -> Copy.hostedSavesSet
            setting?.installed == true -> unsetSaves(row.playerId, model)
            else -> Copy.hostedSavesUnset
        }
        RowText(setting?.hostedTitle?.let { pageLabel(it, setting.label) } ?: row.playerId, inUse)
    }
    is Row.SaveSync -> {
        val setting = model.playerSaves.firstOrNull { it.playerId == row.playerId }
        val state = when {
            setting?.saveSyncing == true -> Copy.hostedSavesSet
            setting?.installed == true -> unsetSaves(row.playerId, model)
            else -> Copy.hostedSavesUnset
        }
        RowText(Copy.memoryCard, state)
    }
    is Row.ThorSetup -> RowText(ThorSetupCopy.title, model.playerSaves.firstOrNull { it.playerId == row.playerId }?.thorSetup?.status?.let(ThorSetupCopy::status).orEmpty())
    is Row.PlayerSetup -> {
        val setting = model.playerSaves.firstOrNull { it.playerId == row.playerId }
        RowText(Copy.setup, if (setting?.installed == true) unsetSaves(row.playerId, model) else Copy.hostedSavesUnset)
    }
    Row.RetroArchCores -> RowText(
        Copy.retroArchCores,
        model.retroArch?.cores?.let { if (it == 1) Copy.oneCore else "$it ${Copy.cores}" } ?: SaveSetupCopy.allow,
    )
    Row.EdenUser -> RowText(Copy.setup, Copy.notSyncing)
    Row.EdenFolder -> RowText(
        Copy.edenKeys,
        // Until Eden's folder is held its saves wait, so it reads as every row that must be acted on does.
        if (model.eden?.fromRomm == true) Copy.edenFromRomm else Copy.notSyncing,
    )
    Row.Apps -> RowText(Copy.apps)
    Row.Hidden -> RowText(Copy.hiddenAppsRow)
    Row.Folders -> RowText(Copy.folders)
    Row.FolderOnHome -> RowText(Copy.onHome, if (model.tileActions?.folderOnHome?.on == true) Copy.on else Copy.off)
    Row.AllLibrary -> RowText(LibraryCopy.title)
    Row.AddNewGames -> RowText(Copy.addNewGames, if (model.addNewToHome) Copy.on else Copy.off)
    Row.RecentOnHome -> RowText(Copy.recentlyPlayed, if (model.recentOnHome) Copy.on else Copy.off)
    Row.Artwork -> RowText(Copy.artwork, if (model.artwork) Copy.on else Copy.off)
    is Row.PluginGrant -> RowText(ApkPluginCopy.pairedComputers)
    is Row.PluginRouting -> RowText(ApkPluginCopy.useForMoonlight)
    is Row.PluginInstall -> ApkPluginCopy.action(model.pluginCatalog(row.packageName)).let { RowText(it.label, it.value) }
    Row.ArtKey -> RowText(Copy.steamGridDb, if (model.artKeySaved) Copy.keySaved else Copy.noKey)
    // Unpaired, the computer's one row pairs it; paired, it is the switch for its games.
    is Row.StreamHost -> model.streamHosts.firstOrNull { it.key == row.key }.let { host ->
        if (host?.paired == true) RowText(Copy.hostGames, Copy.on) else RowText(Copy.pairHost)
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
    Row.CloseGame -> RowText(Copy.closeGame)
    Row.MoveGame -> RowText(if (model.tileActions?.moveGame == HostScreen.Bottom) Copy.playOnBottom else Copy.playOnTop)
    Row.RemoveSession -> RowText(PlayCopy.remove)
    Row.PlayTime -> RowText(
        PlayCopy.playTime,
        model.tileActions?.playFound?.takeIf { model.tileActions.playTime == 0L }?.let(PlayCopy::found)
            ?: model.tileActions?.playTime?.let(::playDuration),
    )
    Row.MovePlay -> RowText(PlayCopy.moveHere)
    Row.About -> RowText(GameAboutCopy.about)
    Row.Backup -> RowText(BackupCopy.title)
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

/** One grid position, without allocating the identity order used outside rearranging. */
fun displaySource(model: PickerModel, index: Int): Int =
    if (model.order.size == model.count) model.order.getOrElse(index) { index } else index

fun displayOrder(model: PickerModel): List<Int> {
    if (model.count <= 0) return emptyList()
    if (model.arranging && model.order.size == model.count) return model.order
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
    /**
     * Launch the game at [index]: on the Play on screen for A, or on the other screen when
     * [elsewhere] (X). [PickerModel.playOn] says which screen that is.
     */
    data class Launch(val index: Int, val elsewhere: Boolean = false) : Effect
    data class OpenPlatform(val index: Int) : Effect
    data object LeavePlatform : Effect
    data object TryAgain : Effect
    data object AddFolder : Effect
    data object SyncLibrary : Effect
    data object EditRommConnection : Effect

    /** The connect form for a new server. */
    data object OpenAddRomm : Effect
    data class SetPluginGrant(val packageName: String, val scope: String, val on: Boolean) : Effect
    data class SetPluginRouting(val packageName: String, val on: Boolean) : Effect
    data class InstallPlugin(val packageName: String) : Effect
    /** Ask [packageName]'s repository for its latest release. [onlyIfStale] skips it when a check just ran. */
    data class CheckPluginRelease(val packageName: String, val onlyIfStale: Boolean = false) : Effect
    data object OpenArtKey : Effect
    data object SaveArtKey : Effect
    data object RetryRommDeviceCode : Effect
    data object UseFoundRomm : Effect
    data class DialogChoice(val button: DialogButton, val kind: DialogKind, val subject: String? = null) : Effect

    /** Pair with Moonlight's computer [key], so its games list themselves. */
    data class PairStreamHost(val key: String) : Effect
    data class ForgetCredentials(val pluginId: String) : Effect
    data class ForgetFolder(val uri: String) : Effect
    data class PickFolderAgain(val uri: String) : Effect
    data object SaveRommToken : Effect

    /** A on a text row of the connect form. The field takes focus and raises the keyboard. */
    data object EditField : Effect
    data object RequestHome : Effect
    data object OpenAllFilesAccess : Effect
    data class ChoosePlayerSave(val playerId: String) : Effect
    data class ChooseFirmwareFolder(val playerId: String) : Effect
    /** How to point the player at the folder Foldcade hosts for its saves. */
    data class SetUpHostedSaves(val playerId: String) : Effect

    /** How to switch the player's memory card so its saves can sync. */
    data class ShowSaveSync(val playerId: String) : Effect

    /** What the player needs once before it can start a game. */
    data class ShowPlayerSetup(val playerId: String) : Effect
    data class ShowThorSetup(val playerId: String) : Effect

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
    data object OpenBackup : Effect

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

    data class SetGamePlayerChoice(val gameId: String, val choice: PlayerChoice?) : Effect
    data class SetSystemPlayerChoice(val platformId: String, val choice: PlayerChoice?) : Effect

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
    data class CloseGame(val gameId: String?) : Effect
    data class MoveGame(val gameId: String?) : Effect

    /** A on a day of Play activity: list that day's sessions, of [game] alone on its page. The shell fills them in. */
    data class OpenPlayDay(val date: java.time.LocalDate, val game: String? = null) : Effect

    /** Take a play session out of the history, after its X was held. */
    data class RemovePlaySession(val sessionId: String) : Effect

    /** Play activity opened on one game: the shell lists the other played games its play can come from. */
    data class OpenPlayGame(val gameId: String) : Effect

    /** Every finished session of [from] counts for [to] from now on. The shell has it held first. */
    data class MovePlayTime(val from: String, val to: String) : Effect

    /** Puts back what the last [MovePlayTime] from [from] to [to] moved, while the page is still open. */
    data class UndoPlayMove(val from: String, val to: String) : Effect
    data object OpenAll : Effect
    data object ToggleAddNew : Effect

    /** Edit home's switch put Recently played on Home or took it off. */
    data class RecentOnHome(val on: Boolean) : Effect

    /** Show Folders in place of the board. */
    data object OpenFolders : Effect

    /** A folder's switch in Folders put Foldcade's folder [folderId] on Home or took it off. */
    data class FolderOnHome(val folderId: String, val on: Boolean) : Effect

    /** Open the notice's app on the top screen, or the bottom when a game holds the top. */
    data class OpenNotice(val id: String) : Effect
    data class DismissNotice(val id: String) : Effect

    /** Every notice X could remove is gone from the model; the app clears them too. */
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

/** The empty library's buttons, in order. Find games leads while ROM folders wait for access. */
enum class NoLibraryAction {
    FindGames,
    AddFolder,
    AddRomm,
}

fun noLibraryActions(model: PickerModel): List<NoLibraryAction> =
    listOfNotNull(NoLibraryAction.FindGames.takeIf { model.romFolderOffer != null }, NoLibraryAction.AddFolder, NoLibraryAction.AddRomm)

/**
 * The top screen over a library with no folder or server: the focused button, large, as Home
 * shows its focused tile. The lit folder for a folder, the RomM cloud for a server (review-A 324).
 */
fun noLibraryHero(model: PickerModel): HeroSubject? {
    if (!model.libraryGrid || model.gridKind != GridKind.NoLibrary || model.focus.chrome != null) return null
    val action = noLibraryActions(model).getOrNull(model.focus.cellIndex) ?: return null
    val (name, mark) = when (action) {
        NoLibraryAction.FindGames -> FoundFolderCopy.findGames to MARK_FOLDER
        NoLibraryAction.AddFolder -> Copy.addFolder to MARK_FOLDER
        NoLibraryAction.AddRomm -> Copy.addRommServer to MARK_CLOUD
    }
    return HeroSubject.Folder(HeroFolder(key = "no-library/${action.name}", name = name, count = null, mark = mark))
}

/**
 * Add a folder: Android's picker. It opens at the ROM folder Foldcade saw when there is one,
 * which says what to choose, so only a picker with nowhere obvious to start is explained first,
 * and that only once.
 */
fun addFolder(model: PickerModel, screen: HostScreen): Pair<PickerModel, Effect?> =
    if (model.folderGrantPending && model.romFolderOffer == null && model.folderStart == null) model.copy(dialog = folderExplainer(screen)) to null
    else model to Effect.AddFolder

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
    /** Android's tree picker owns [FolderGuide.screen]; the other screen shows its guide alone. */
    val folderGuide: FolderGuide? = null,
    val atLibraryRoot: Boolean = true,
    /** Which screen has the grid and which the hero. Settings › System › Layout. */
    val layout: ScreenLayout = ScreenLayout.Default,
    /** The screen A opens a one-screen game on. X opens it on the other. Settings › System › Play on. */
    val playOn: HostScreen = HostScreen.Top,
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
    val signedIn: List<SignedInBackend> = emptyList(),
    /** A saved address still has connection actions when its token needs recovery. */
    val rommSaved: Boolean = signedIn.any { it.pluginId == "romm" },
    /**
     * RomM has an address but no sign-in: after a restore, which leaves tokens out, or once the
     * server refused the token. Saves wait until a sign-in, so every place RomM shows says so.
     */
    val rommSignIn: Boolean = false,
    /** The folders the library reads. Each has a row to remove it. */
    val folders: List<LibraryFolder> = emptyList(),
    val connectOrigin: String = "",
    val connectFoundOrigin: String? = null,
    val connectSearching: Boolean = false,
    val connectIndex: Int = 0,
    val connectScreen: HostScreen? = null,
    /** Editing returns to this connection's actions when the form closes. */
    val connectReturnPanel: SidePanel? = null,
    /** The full-screen Settings row that opened this form. */
    val connectReturnSettings: SettingsPage? = null,
    val connectWarning: String? = null,
    val connectHint: String? = null,
    /** The pairing code on the form, while a phone can approve it. */
    val connectCode: PairingCode? = null,
    val connectBusy: Boolean = false,
    val connectRetry: Boolean = false,
    val reLoginPending: Boolean = false,
    val playerSaves: List<PlayerSaveSetting> = emptyList(),
    /**
     * The emulators Home's RomM games play on, by their Settings › Emulators key, whether or not
     * their saves sync. [savesDue] reads it, so R1, Settings and Set up saves count the same ones.
     */
    val boardEmulators: Set<String> = emptySet(),
    val systemPlayers: List<SystemPlayerChoice> = emptyList(),
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
    /** Android's current optional all-files access, shared by Settings and save setup. */
    val allFilesGranted: Boolean = false,
    /** ROM folders Foldcade saw and may not read yet. Find games leads the ways to add games while it is set. */
    val romFolderOffer: RomFolderOffer? = null,
    /** Android's all-files page is open beside Foldcade. The command center says to tap its switch. */
    val allFilesPage: Boolean = false,
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
    val apkPlugins: List<ApkPluginSetting> = emptyList(),
    /** Each [PluginRegistry] plugin's catalog state, by package. Read it with [pluginCatalog]. */
    val pluginCatalogs: Map<String, PluginCatalogSetting> = emptyMap(),
    val connectKind: ConnectKind = ConnectKind.Romm,
    /** Both screens' brightness as a slider position, 0 to 100. See [brightnessLevel]. */
    val brightness: Int = 50,
    /** Android lets Foldcade write the brightness setting. */
    val brightnessAllowed: Boolean = false,
    /** R1 shows the row that asks for notification access. */
    val askNoticeAccess: Boolean = false,
    /** The Play activity page. Null while it is closed. */
    val playActivity: PlayActivityPage? = null,
    /** A game's About page. Null while it is closed. */
    val gameAbout: GameAboutPage? = null,
    /** Two saves waiting for a choice on the bottom screen. Null while there is nothing to ask. */
    val saveConflict: SaveConflictPrompt? = null,
    val savePicker: SavePickerState? = null,
    /** Set up saves. Null while it is closed. */
    val saveSetup: SaveSetupPage? = null,
    /** True while Foldcade reads what each emulator's folder still needs, as just after a pick. Set up saves waits on it before moving on. */
    val checkingSaves: Boolean = false,
    /** Every library is being read again. */
    val librarySyncing: Boolean = false,
    /** How the last read of the libraries ended, for Sync games' value. */
    val librarySync: LibrarySync = LibrarySync.Unknown,
    /** The saved RomM server's address, without its scheme, for RomM's page. */
    val rommHost: String? = null,
    /**
     * A ROM folder Foldcade saw (`ROMs`, `roms`, `Emulation/roms`), as a storage document id.
     * Add a folder opens Android's picker there.
     */
    val folderStart: String? = null,
    /**
     * The tree Pick again chose for the folder whose page asked. Once that folder leaves, the shelf
     * focuses this one, even when the library already read it ([withFolders]).
     */
    val folderFocus: String? = null,
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
    if (model.folderGuide != null) return model to null
    val coerced = model.copy(focus = coerce(model.focus, model))
    val dialog = coerced.dialog
    if (dialog != null) {
        // Asks that open by themselves, or under a held press, take only a fresh one.
        if (dialog.kind in setOf(DialogKind.Backup, DialogKind.RestoreBackup, DialogKind.BackupDone, DialogKind.PlayHistory, DialogKind.RommSignOut,
                DialogKind.AddGames, DialogKind.FindGames, DialogKind.RetroArchCores, DialogKind.ThorSetup, DialogKind.RestoreThorSetup) && repeatCount > 0 &&
            meaning in setOf(Meaning.Activate, Meaning.ActivateBottom, Meaning.Back, Meaning.Dismiss)) return coerced to null
        if (dialog.kind == DialogKind.CloseGame && repeatCount > 0 &&
            (meaning == Meaning.Activate || meaning == Meaning.ActivateBottom)) return coerced to null
        if (meaning == Meaning.LeftPanel || meaning == Meaning.RightPanel) return coerced to null
        val (next, choice) = applyDialog(dialog, meaning)
        if (dialog.kind == DialogKind.Backup && choice == DialogButton.PlayHistory) {
            return coerced.copy(dialog = playHistoryDialog(dialog.screen)) to null
        }
        if (dialog.kind == DialogKind.RommSignOut) {
            return if (choice == DialogButton.SignOut) coerced.copy(dialog = null, panel = null, focus = coerced.panel?.grid ?: coerced.focus) to Effect.ForgetCredentials("romm")
            else coerced.copy(dialog = if (choice != null) null else next) to null
        }
        val chosen = choice?.let { Effect.DialogChoice(it, dialog.kind, dialog.subject) }
        if (dialog.kind == DialogKind.BackupDone && choice == DialogButton.Library) {
            return coerced.copy(dialog = null).openSettings(coerced.layout.pageScreen, SettingsCategory.Library) to chosen
        }
        val cleared = if (choice != null) null else next
        return coerced.copy(dialog = cleared) to chosen
    }
    coerced.saveSetup?.let { page ->
        if (meaning == Meaning.LeftPanel || meaning == Meaning.RightPanel) return coerced to null
        return applySaveSetup(coerced, page, meaning, repeatCount)
    }
    coerced.savePicker?.let {
        // A held A opens one confirmation. Its repeats would answer it, Cancel first, and open it again.
        if (meaning == Meaning.Activate && repeatCount > 0) return coerced to null
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
        val fields = connectFields(coerced.connectKind, coerced.connectFoundOrigin != null)
        val index = coerced.connectIndex.coerceIn(0, fields.lastIndex)
        val field = fields[index]
        return when (meaning) {
            Meaning.Back -> coerced.copy(
                connectOpen = false,
                connectScreen = null,
                connectBusy = false,
                connectCode = null,
                panel = coerced.connectReturnPanel,
                settings = coerced.connectReturnSettings,
                connectReturnPanel = null,
                connectReturnSettings = null,
            ) to null
            Meaning.MoveUp -> coerced.copy(connectIndex = (index - 1).coerceAtLeast(0)) to null
            Meaning.MoveDown -> coerced.copy(connectIndex = (index + 1).coerceAtMost(fields.lastIndex)) to null
            Meaning.Activate -> when (field) {
                ConnectField.FoundServer -> coerced to Effect.UseFoundRomm.takeIf { repeatCount == 0 }
                ConnectField.Save -> coerced to if (coerced.connectBusy) null else if (coerced.connectKind == ConnectKind.ArtKey) Effect.SaveArtKey else Effect.SaveRommToken
                ConnectField.Origin, ConnectField.Token -> coerced.copy(connectIndex = index) to Effect.EditField
            }
            Meaning.LeftPanel, Meaning.RightPanel ->
                presentPanel(
                    coerced.copy(connectOpen = false, connectScreen = null, connectBusy = false, connectReturnPanel = null, connectReturnSettings = null),
                    meaning,
                    screen,
                ) to null
            else -> coerced.copy(connectIndex = index) to null
        }
    }
    val settings = coerced.settings
    if (repeatCount > 0 && meaning in setOf(Meaning.Options, Meaning.Menu, Meaning.LeftPanel, Meaning.RightPanel)) return coerced to null
    if (settings != null) return applySettings(coerced, settings, meaning, repeatCount).let { (next, effect) -> retainSettingsShelf(coerced, next).versionCopySettled() to effect }
    coerced.gameAbout?.let { return applyGameAbout(coerced, it, meaning, repeatCount) }
    val activity = coerced.playActivity
    if (activity != null) return applyPlayActivity(coerced, activity, meaning, repeatCount)
    val panel = coerced.panel
    if (panel != null) {
        if (meaning == Meaning.LeftPanel || meaning == Meaning.RightPanel) {
            return presentPanel(coerced, meaning, screen) to null
        }
        // A held X or Delete removes one notice, not each one that moves up under the focus.
        if (repeatCount > 0 && (meaning == Meaning.ActivateBottom || meaning == Meaning.Dismiss)) return coerced to null
        // A held A flips a switch once. The menu stays open under it, so each repeat would flip it back.
        if (repeatCount > 0 && meaning == Meaning.Activate &&
            panelRows(panel, coerced).getOrNull(panel.index) == Row.OpenWith) return coerced to null
        if (repeatCount > 0 && meaning == Meaning.Activate &&
            panelRows(panel, coerced).getOrNull(panel.index)?.let { rowControl(it, coerced) is RowControl.Toggle } == true
        ) return coerced to null
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
    if (open == null) return model.copy(panel = freshPanel(side, model.focus, model.layout))
    // Y's menu is not an island: it shrinks back into its tile as the shoulder's island grows.
    if (open.side == Side.Tile) return model.copy(panel = freshPanel(side, open.grid, model.layout), focus = open.grid)
    if (open.retiring) {
        val pending = if (side == open.side) null else side
        return model.copy(panel = open.copy(pendingSide = pending))
    }
    if (open.side == side) return model.copy(panel = null, focus = open.grid)
    return model.copy(panel = open.copy(retiring = true, pendingSide = side))
}

/** An island's menu opens on the screen its island is on, [ScreenLayout.menuScreen]. */
private fun freshPanel(side: Side, grid: GridFocus, layout: ScreenLayout): SidePanel = SidePanel(
    side = side,
    screen = layout.menuScreen,
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
        open.screen == model.layout.menuScreen &&
        open.level == PanelLevel.Root
    ) {
        return model
    }
    return model.copy(panel = freshPanel(side, open?.grid ?: model.focus, model.layout))
}

/**
 * The retiring island has finished its leave morph.
 * The pending shoulder then expands. A close with no pending side stays closed.
 */
fun finishIslandRetire(model: PickerModel): PickerModel {
    val open = model.panel ?: return model
    if (!open.retiring) return model
    val next = open.pendingSide ?: return model.copy(panel = null, focus = open.grid)
    // The next island opens where the last one was, which may be a page's screen a layout change left.
    return model.copy(panel = freshPanel(next, open.grid, model.layout).copy(screen = open.screen))
}

private fun applyGrid(
    model: PickerModel,
    meaning: Meaning,
    screen: HostScreen,
    repeatCount: Int,
): Pair<PickerModel, Effect?> {
    // A held face button must not relaunch after a pending launch or dialog settles.
    if (repeatCount > 0 && (meaning == Meaning.Activate || meaning == Meaning.ActivateBottom)) return model to null
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
        Meaning.Menu -> if (model.arranging) model to null else model.openSettings(model.layout.pageScreen) to null
        Meaning.Activate -> activate(model, screen)
        Meaning.ActivateBottom -> activate(model, screen, elsewhere = true)
        Meaning.Options -> openActions(model) to null
        Meaning.Back -> backGrid(model)
        Meaning.LeftPanel, Meaning.RightPanel -> presentPanel(model, meaning, screen) to null
        else -> model to null
    }
}

/**
 * Y opens the focused tile's menu beside the tile, on the grid's screen ([tileMenu]). L1 and R1
 * keep their islands. With nothing to offer, it stays shut.
 */
private fun openActions(model: PickerModel): PickerModel {
    if (model.arranging) return model
    if (model.count <= 0) {
        if (model.tileActions?.undo != true) return model
    } else if (model.focus.chrome != null) return model
    if (actionRows(model.tileActions).isEmpty()) return model
    return model.copy(panel = tileMenu(model.focus, model.layout, recoveryOnly = model.count <= 0))
}

/**
 * Y's menu for the tile at [grid]: on the grid's screen, where the tile is, never the hero's. A game
 * holding that screen sends it to the one Foldcade still shows ([onShownScreens]), where the grid is then.
 */
fun tileMenu(grid: GridFocus, layout: ScreenLayout, recoveryOnly: Boolean = false): SidePanel = SidePanel(
    side = Side.Tile,
    screen = layout.gridScreen,
    level = PanelLevel.Actions,
    index = 0,
    grid = grid,
    recoveryOnly = recoveryOnly,
)

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

/**
 * How much of a dragged tile slides past an edge before the cursor steps off it,
 * along its row, to the next tile that is still whole on screen.
 */
const val DRAG_EDGE_GIVE = 0.25f

/**
 * A finger holds the strip at [held] columns, between whole columns. The cursor stays
 * on its tile until [DRAG_EDGE_GIVE] of that tile has slid past an edge. Then it steps
 * along its row to the outermost tile still on screen, so it always rests on a whole
 * tile and never waits at an edge while tiles slide past it. The strip comes to rest
 * only when the finger lets go, in [scrolledTo].
 */
fun draggedTo(model: PickerModel, held: Float): PickerModel {
    if (model.count <= 0 || reach(model) != GridStrip.reach(model.count)) return model
    val shown = model.columnsShown
    val lastColumn = GridStrip.reachColumns(model.count) - 1
    val low = kotlin.math.ceil(held - DRAG_EDGE_GIVE).toInt().coerceIn(0, lastColumn)
    val high = kotlin.math.floor(held + shown - 1 + DRAG_EDGE_GIVE).toInt().coerceIn(low, lastColumn)
    val focus = model.focus
    val column = GridStrip.column(focus.cellIndex)
    if (column in low..high) return model
    val next = column.coerceIn(low, high)
    return model.copy(
        // The nearest whole column, which keeps the new tile on screen.
        firstColumn = kotlin.math.round(held).toInt().coerceIn(0, GridStrip.lastFirst(model.count, shown)),
        focus = focus.copy(
            cellIndex = GridStrip.index(next, GridStrip.row(focus.cellIndex)),
            lastColumn = focus.lastColumn.coerceIn(low, high),
        ),
    )
}

/** A, or X when [elsewhere]. X launches a game on the screen Play on does not name, and does what A does anywhere else. */
private fun activate(model: PickerModel, screen: HostScreen, elsewhere: Boolean = false): Pair<PickerModel, Effect?> {
    return when (model.focus.chrome) {
        Chrome.StatusCluster -> presentPanel(model, Meaning.RightPanel, screen) to null
        Chrome.LeftIsland -> presentPanel(model, Meaning.LeftPanel, screen) to null
        null -> {
            if (model.arranging) return placeOrPick(model)
            if (model.count <= 0 || model.focus.cellIndex !in 0 until model.count) return model to null
            if (!model.libraryGrid) return model to Effect.Launch(model.focus.cellIndex, elsewhere)
            when (model.gridKind) {
                GridKind.Games -> model to Effect.Launch(model.focus.cellIndex, elsewhere)
                GridKind.Platforms -> model to Effect.OpenPlatform(model.focus.cellIndex)
                GridKind.Unreachable -> model to Effect.TryAgain
                GridKind.NoLibrary -> when (noLibraryActions(model).getOrNull(model.focus.cellIndex)) {
                    NoLibraryAction.FindGames -> model to Effect.OpenAllFilesAccess
                    NoLibraryAction.AddFolder -> addFolder(model, screen)
                    NoLibraryAction.AddRomm -> model.copy(
                        connectOpen = true,
                        connectScreen = model.layout.pageScreen,
                        connectIndex = 0,
                        connectKind = ConnectKind.Romm,
                        connectReturnPanel = null,
                    ) to Effect.OpenAddRomm
                    null -> model to null
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
            val opening = freshPanel(pending, current.grid, model.layout).copy(screen = current.screen)
            chordIndex(panelRows(opening, model), meaning) ?: return model to null
            return applyPanel(model.copy(panel = opening), opening, meaning, screen)
        }
        val chord = chordIndex(rows, meaning)
        if (chord != null) return activateRow(model, current.copy(index = chord), rows[chord], screen)
        // Y opened the tile's menu, so Y closes it again, unless a row there takes Y (Y then Y is Move).
        if (meaning == Meaning.Options && current.side == Side.Tile) return model.copy(panel = null, focus = current.grid) to null
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
            playerChoiceStep(model, rows[index], direction)?.let { return model.copy(panel = current) to it }
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
        // X, or Delete on a keyboard, removes the focused notice, as X removes in every menu.
        Meaning.ActivateBottom, Meaning.Dismiss -> when (val row = rows[index]) {
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
    Row.Theme, Row.IconTheme, Row.Background, Row.MotionSpeed, Row.MusicTrack, Row.ScreenLayout, Row.PlayOn, Row.SortFolder, Row.OpenWith,
)

/** The shell persists choices; effects always carry the menu's game, never the grid focus. */
private fun playerChoiceStep(model: PickerModel, row: Row, direction: Int): Effect? = when (row) {
    Row.OpenWith -> model.tileActions?.let { tile -> tile.game?.let { id ->
        tile.openWith?.let { Effect.SetGamePlayerChoice(id, it.step(direction)) }
    } }
    is Row.SystemPlayer -> model.systemPlayers.firstOrNull { it.platformId == row.platformId }?.let {
        Effect.SetSystemPlayerChoice(row.platformId, it.choice.step(direction))
    }
    else -> null
}

/** The folder Sort row's step, or null when the menu names no folder. */
private fun sortStep(model: PickerModel, direction: Int): Effect? =
    model.sortChoice()?.let { Effect.SortFolder(it.folderId, direction) }

/**
 * Left and right step a value and keep the row. Music volume moves by 5 points,
 * and so does brightness, or it asks for access first. A choice row (theme,
 * icons, background, motion, track, sort, layout, play on) cycles its choices either
 * way, as A cycles them forward, except Theme, whose A opens the Themes page. Other rows
 * ignore the direction, so the focus stays put.
 */
private fun stepRow(model: PickerModel, row: Row, direction: Int): PickerModel? {
    return when (row) {
        Row.MusicVolume -> model.copy(music = model.music.nudged(direction))
        Row.Brightness -> if (model.brightnessAllowed) {
            model.copy(brightness = steppedBrightness(model.brightness, direction))
        } else {
            model.copy(dialog = brightnessPrompt(model.panel?.screen ?: model.layout.menuScreen))
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
        Row.ScreenLayout -> {
            val layouts = ScreenLayout.entries
            model.copy(layout = layouts[Math.floorMod(layouts.indexOf(model.layout) + direction, layouts.size)])
        }
        Row.PlayOn -> model.copy(playOn = model.playOn.other())
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
    // The A that opened Settings cannot enter its rows or change their values while held.
    if (repeatCount > 0 && meaning == Meaning.Activate) return model.copy(settings = current) to null
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
    val inner = current.inner
    // Back on a page lands on the tile or row that opened it, including an empty Systems page.
    val shelf = current.copy(inner = null, row = inner?.let { settingsTileIndex(category, it, model) }?.coerceAtLeast(0) ?: rowIndex)
    val row = rows.getOrNull(rowIndex) ?: return when (meaning) {
        Meaning.Back, Meaning.MoveLeft -> model.copy(settings = shelf) to null
        Meaning.LeftPanel, Meaning.RightPanel, Meaning.Menu -> close
        else -> model.copy(settings = current) to null
    }
    return when (meaning) {
        Meaning.MoveUp, Meaning.MoveDown -> model.copy(
            capturingConfirm = false,
            settings = current.copy(row = settingsFocusIndex(rows, rowIndex, meaning, shownShelfFirst(current, model)) ?: rowIndex),
        ) to null
        Meaning.MoveLeft, Meaning.MoveRight -> {
            val direction = if (meaning == Meaning.MoveLeft) -1 else 1
            playerChoiceStep(model, row, direction)?.let { return model.copy(settings = current) to it }
            val nudged = stepRow(model, row, direction)
            when {
                nudged != null -> nudged.copy(capturingConfirm = false, settings = current) to null
                row.settingsTile() -> settingsFocusIndex(rows, rowIndex, meaning, shownShelfFirst(current, model))
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
            if (row == Row.Systems) return model.copy(settings = current.copy(inner = SettingsInner.Systems, row = 0)) to null
            // Add with one way in does it from its tile, which keeps the focus for the way back.
            settingsTileShortcut(row, model)?.let { only ->
                val standIn = SidePanel(Side.Left, current.screen, PanelLevel.Root, rowIndex, current.grid)
                val (next, effect) = activateRow(model.copy(panel = standIn), standIn, only, current.screen)
                return next.copy(panel = null, settings = current, focus = model.focus) to effect
            }
            if (row is Row.SettingsTile) return model.copy(settings = current.copy(inner = row.page, row = 0)) to pluginPageCheck(row.page, model)
            if (row is Row.RommConnection) return model.copy(settings = current.copy(inner = SettingsInner.Romm, row = 0)) to null
            // Back from Set up saves returns to this row.
            if (row == Row.SaveSetup) return model.openSaveSetup(current.screen, settings = current) to null
            // Rows act through the menu path. A stand-in panel tells "stays open" from "leaves".
            val standIn = SidePanel(Side.Left, current.screen, PanelLevel.Root, rowIndex, current.grid)
            val (next, effect) = activateRow(model.copy(panel = standIn), standIn, row, current.screen)
            // The folder goes; the shelf stays, its focus where the folder's tile was.
            if (row is Row.ForgetFolder) return next.copy(panel = null, settings = shelf, focus = model.focus) to effect
            val stays = next.panel != null
            next.copy(
                panel = null,
                settings = if (stays) current else null,
                connectReturnSettings = if (next.connectOpen) current else next.connectReturnSettings,
                connectReturnPanel = if (next.connectOpen) null else next.connectReturnPanel,
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
        Row.Settings -> model.openSettings(model.layout.pageScreen, grid = panel.grid) to null
        // The [choiceRows]. The list is spelled out so a new row cannot slip past this when.
        Row.IconTheme, Row.Background, Row.MotionSpeed, Row.MusicTrack, Row.ScreenLayout, Row.PlayOn ->
            stepRow(model, row, 1)!!.copy(panel = panel) to null
        // Left and right step the pack. A opens every pack, Add a theme, and what was found in each.
        Row.Theme -> model.copy(panel = panel) to Effect.OpenThemes
        // A choice row too, whose value is the board's: the shell steps it.
        Row.OpenWith, is Row.SystemPlayer -> model.copy(panel = panel) to playerChoiceStep(model, row, 1)
        Row.Systems -> model.copy(panel = panel) to null
        Row.SortFolder -> model.copy(panel = panel) to sortStep(model, 1)
        Row.UsageAccess -> model.copy(dialog = usageAccessPrompt(screen)) to null
        // Android adds Undo from the current before-copy; leave presentation to that effect.
        Row.Backup -> model to Effect.OpenBackup
        Row.FindSaves, Row.FindGames -> model to Effect.OpenAllFilesAccess
        Row.SaveSetup -> model.openSaveSetup(panel.screen, grid = panel.grid) to null
        Row.PlayActivity -> model.copy(
            panel = null,
            playActivity = PlayActivityPage(screen = model.layout.pageScreen, grid = panel.grid),
        ) to null
        Row.Home -> model.copy(panel = null, focus = panel.grid) to Effect.GoHome
        Row.EditHome -> model.copy(panel = panel.copy(level = PanelLevel.EditHome, index = 0, tileColumn = null, parentTileColumn = panel.tileColumn)) to null
        Row.Music -> model.copy(panel = panel, music = model.music.toggled()) to null
        Row.MusicVolume -> model.copy(panel = panel, music = model.music.stepped()) to null
        Row.SetAsHome -> model to Effect.RequestHome
        Row.AndroidSettings -> model.copy(panel = panel.takeUnless { it.side == Side.Right }, focus = panel.grid) to Effect.OpenAndroidSetting(AndroidSetting.Settings)
        Row.Licenses -> model.copy(panel = panel) to Effect.OpenLicenses
        // Settings opens its page itself, see [applySettings].
        is Row.SettingsTile -> model.copy(panel = panel) to null
        Row.Version -> model.copy(panel = panel, versionCopied = true) to Effect.CopyDebugInfo
        Row.DefaultHomeApp -> model.copy(panel = panel) to Effect.OpenAndroidSetting(AndroidSetting.Home)
        is Row.QuickTile -> model.copy(panel = null, focus = panel.grid) to Effect.OpenAndroidSetting(row.setting.androidSetting())
        // Left and right move the slider. A asks for access, and does nothing once it is granted.
        Row.Brightness -> if (model.brightnessAllowed) {
            model.copy(panel = panel) to null
        } else {
            model.copy(panel = panel, dialog = brightnessPrompt(panel.screen)) to null
        }
        Row.AddFolder -> addFolder(model, screen)
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
            connectScreen = model.layout.pageScreen,
            connectIndex = 0,
            connectKind = ConnectKind.Romm,
            connectReturnPanel = panel,
        ) to Effect.EditRommConnection
        Row.AddRomm -> model.copy(
            panel = null,
            focus = panel.grid,
            connectOpen = true,
            connectScreen = model.layout.pageScreen,
            connectOrigin = "",
            connectIndex = 0,
            connectKind = ConnectKind.Romm,
            connectHint = null,
            connectReturnPanel = null,
        ) to Effect.OpenAddRomm
        // RomM's asks first: saves stop syncing, and its games leave Home.
        is Row.SignOut -> if (row.pluginId == "romm") model.copy(panel = panel, dialog = rommSignOutDialog(model.rommSignIn, screen)) to null
            else model.copy(panel = null, focus = panel.grid) to Effect.ForgetCredentials(row.pluginId)
        Row.SignInRomm -> model.openRommSignIn(panel.grid)
        is Row.ForgetFolder -> model.copy(panel = null, focus = panel.grid) to Effect.ForgetFolder(row.uri)
        // Android's folder picker returns to the folder's page it was opened from, as PlayerSave's does.
        is Row.PickFolderAgain -> model.copy(panel = panel) to Effect.PickFolderAgain(row.uri)
        is Row.Notice -> openNotice(model, panel, row.id)
        Row.NoticeAccess -> model.copy(panel = panel, dialog = noticeAccessPrompt(screen)) to null
        Row.ClearNotices -> model.copy(
            panel = panel.copy(index = rightStart()),
            notices = model.notices.filter { !it.dismissable },
        ) to Effect.ClearNotices
        // Android's folder picker returns to the emulator's page it was opened from.
        is Row.PlayerSave -> model.copy(panel = panel) to Effect.ChoosePlayerSave(row.playerId)
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
        is Row.ThorSetup -> model.copy(panel = panel) to Effect.ShowThorSetup(row.playerId)
        is Row.PlayerSetup -> model.copy(panel = null, focus = panel.grid) to Effect.ShowPlayerSetup(row.playerId)
        Row.RetroArchCores -> model.copy(panel = panel) to Effect.ShowRetroArchCores
        Row.EdenFolder -> model.copy(panel = panel) to Effect.ShowEdenFolder
        Row.EdenUser -> model.copy(panel = null, focus = panel.grid) to Effect.OpenPlayer(EDEN_SHELF_KEY)
        Row.Apps -> model.copy(panel = null, focus = panel.grid) to Effect.OpenFolder(HOME_ANDROID_APPS)
        Row.Hidden -> openGrid(model, panel, HomeGrid.Hidden)
        Row.Folders -> model.copy(panel = null, focus = panel.grid, arranging = false, hold = null) to Effect.OpenFolders
        // The switch flips here so it moves at once. The shell's own reading replaces this one.
        Row.FolderOnHome -> model.tileActions?.folderOnHome?.let { switch ->
            model.copy(panel = panel, tileActions = model.tileActions.copy(folderOnHome = switch.copy(on = !switch.on))) to
                Effect.FolderOnHome(switch.folderId, !switch.on)
        } ?: (model.copy(panel = panel) to null)
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
                connectScreen = model.layout.pageScreen,
                connectIndex = 0,
                connectKind = ConnectKind.ArtKey,
            ) to Effect.OpenArtKey
        }
        // Pairing is asked on the computer, so A only starts it; forgetting asks here first.
        is Row.PluginGrant -> model.copy(panel = panel) to model.apkPlugins.firstOrNull { it.packageName == row.packageName }?.let {
            Effect.SetPluginGrant(row.packageName, row.scope, row.scope !in it.granted)
        }
        is Row.PluginRouting -> model.copy(panel = panel) to model.apkPlugins.firstOrNull { it.packageName == row.packageName }?.let {
            Effect.SetPluginRouting(row.packageName, !it.useForMoonlight)
        }
        // A release to take installs it; otherwise A asks again. Nothing while one is under way.
        is Row.PluginInstall -> model.copy(panel = panel) to model.pluginCatalog(row.packageName).let { catalog ->
            when {
                catalog.canInstall -> Effect.InstallPlugin(row.packageName)
                catalog.busy || catalog.arriving -> null
                else -> Effect.CheckPluginRelease(row.packageName)
            }
        }
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
        Row.PlayTime -> model.tileActions?.game?.let { game ->
            model.copy(panel = null, focus = panel.grid, playActivity = PlayActivityPage(screen = model.layout.pageScreen, grid = panel.grid, zoom = PlayZoom.Month, game = game)) to
                Effect.OpenPlayGame(game)
        } ?: (model.copy(panel = panel) to null)
        Row.About -> model.tileActions?.game?.let { game ->
            model.copy(panel = null, focus = panel.grid, gameAbout = GameAboutPage(screen = model.layout.pageScreen, game = game, grid = panel.grid)) to null
        } ?: (model.copy(panel = panel) to null)
        Row.RestartGame -> model.copy(panel = null, focus = panel.grid) to Effect.RestartGame(model.tileActions?.game)
        Row.CloseGame -> model.copy(panel = null, focus = panel.grid) to Effect.CloseGame(model.tileActions?.game)
        Row.MoveGame -> model.copy(panel = null, focus = panel.grid) to Effect.MoveGame(model.tileActions?.game)
        // Play activity's list removes a session itself; no menu lists this row.
        Row.RemoveSession, Row.MovePlay -> model to null
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

/** A. An app's notice opens that app and R1 closes. A Foldcade notice has no app, so A shows all of its text. */
private fun openNotice(model: PickerModel, panel: SidePanel, id: String): Pair<PickerModel, Effect?> {
    val notice = model.notices.firstOrNull { it.id == id } ?: return model.copy(panel = panel) to null
    if (notice.id == ROMM_SIGN_IN_NOTICE) return model.openRommSignIn(panel.grid)
    // The save-setup notices open Set up saves: the merged one at the first emulator that needs
    // you, one emulator's on its own tile.
    if (notice.settingsCategory == SettingsCategory.Players && notice.id.startsWith("foldcade:save-setup:")) return model.openSaveSetup(panel.screen, grid = panel.grid) to null
    // A system's fallback notice opens Systems on that system's row, where its choice is changed.
    if (notice.settingsCategory == SettingsCategory.Players && notice.id.startsWith(PLAYER_CHOICE_SYSTEM_NOTICE)) {
        val row = settingsInnerRows(SettingsInner.Systems, model).indexOf(Row.SystemPlayer(notice.id.removePrefix(PLAYER_CHOICE_SYSTEM_NOTICE)))
        val opened = model.openSettings(model.layout.pageScreen, SettingsCategory.Players, panel.grid)
        if (row >= 0 && SettingsCategory.Players in settingsCategories(model)) {
            return opened.copy(settings = opened.settings?.copy(inner = SettingsInner.Systems, onRows = true, row = row)) to null
        }
        return opened to null
    }
    notice.firmwarePlayerId?.let { playerId ->
        return model.copy(panel = null, focus = panel.grid) to Effect.ChooseFirmwareFolder(playerId)
    }
    notice.settingsCategory?.let { category ->
        return model.openSettings(model.layout.pageScreen, category, panel.grid) to null
    }
    if (notice.id.startsWith(ThorSetupCopy.noticePrefix)) {
        val playerId = notice.id.removePrefix(ThorSetupCopy.noticePrefix)
        val inner = SettingsInner.Emulator(playerId)
        val row = settingsInnerRows(inner, model).indexOf(Row.ThorSetup(playerId))
        val opened = model.openSettings(model.layout.pageScreen, SettingsCategory.Players, panel.grid)
        return if (row < 0) opened to null else
            opened.copy(settings = opened.settings?.copy(inner = inner, onRows = true, row = row)) to null
    }
    notice.savePlayerId?.let { playerId ->
        if (saveSetupTiles(model, SaveSetupPage()).any { it.key == playerId }) {
            return model.openSaveSetup(panel.screen, target = playerId, grid = panel.grid) to null
        }
        // Not on the strip, as before its emulator has loaded: the fix itself, as before.
        val setting = model.playerSaves.firstOrNull { it.playerId == playerId }
            ?: return model.copy(panel = null, focus = panel.grid) to Effect.ChoosePlayerSave(playerId)
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
        return model.copy(panel = null, focus = panel.grid) to Effect.OpenNotice(id)
    }
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
        Meaning.Back -> dialog to if (dialog.safeIndex == BACK_CLOSES) DialogButton.Dismiss else dialog.buttons[dialog.safeIndex.coerceIn(0, last)]
        else -> dialog.copy(index = index) to null
    }
}

/** A held direction stops before the empty tail; a fresh press can explore it. */
fun softStop(before: GridFocus, after: GridFocus, lastOccupiedIndex: Int, repeatCount: Int): GridFocus =
    if (repeatCount > 0 && after.chrome == null && after.cellIndex > lastOccupiedIndex &&
        after.cellIndex > before.cellIndex) before else after
