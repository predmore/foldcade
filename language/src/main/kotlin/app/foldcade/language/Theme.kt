package app.foldcade.language

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Built-in OLED theme. A missing theme zip keeps these values. */
data class Theme(
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val muted: Color,
    val focus: Color,
    val iconRadius: Float,
    val artScale: Float,
    val font: FontFamily,
    val style: ThemeStyle = ThemeStyle(),
    val motion: MotionStyle = MotionStyle(style),
    /** The hero's title, dialog titles, the clock and the save-conflict game. [font] unless the pack has a display face. */
    val displayFont: FontFamily = font,
)

/**
 * A face that failed to load wears the inherited one. Afterglow's own failure,
 * and a pack with nothing to inherit, uses [FontFamily.SansSerif].
 */
fun fontOrInherited(loaded: FontFamily?, inherited: FontFamily?): FontFamily =
    loaded ?: inherited ?: FontFamily.SansSerif

fun builtInTheme(): Theme = Theme(
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C20),
    onBackground = Color(0xFFF2F2F2),
    muted = Color(0xFF8A8A8A),
    focus = Color(0xFFFFFFFF),
    iconRadius = 0.12f,
    artScale = 0.92f,
    font = FontFamily.SansSerif,
)

fun cursorBrush(theme: Theme): Brush = SolidColor(theme.focus)

/** Host-owned type ramp, in sp, the same on both panels. */
object TypeRamp {
    val heroTitle: TextUnit = 28.sp
    val conflictGame: TextUnit = 40.sp
    val heroMeta: TextUnit = 18.sp
    val availability: TextUnit = 16.sp
    val gridLabel: TextUnit = 16.sp
    val dialogTitle: TextUnit = 20.sp
    val dialogBody: TextUnit = 16.sp
    val menuRow: TextUnit = 18.sp
    val sideRow: TextUnit = 18.sp
    val hint: TextUnit = 14.sp

    /** The command center's clock, the one large thing on that screen. */
    val clock: TextUnit = 64.sp
}

/** Host-owned layout. Motion lives on [Motion]. */
object Metrics {
    /** The grid shows four columns of three. More columns scroll in from the side, see [GridStrip]. */
    const val columns = 4
    const val rows = 3
    const val insetFraction = 0.04f
    const val gapFraction = 0.04f
    const val focusStrokePx = 3f
    const val heroInsetPx = 48f
    const val dialogInsetPx = 48f
    const val dialogBorderPx = 2f

    /** Corner of a filled card: an open island, a dialog, a settings group. */
    const val cardCornerDp = 28f
    const val heroArtFraction = 0.62f

    /** Space between the hint line and the top of a focus stroke, in px. */
    const val chromeClearancePx = 28f
}

object Copy {
    const val usesBothScreens = "Both screens"
    /** The command center's button: the grid on that screen, to open a second app there. */
    const val openAnApp = "Open an app"
    const val progressInGameNative = "Progress lives in GameNative."
    const val addShortcutInGameNative = "Add a game's shortcut in GameNative."
    const val progressInDroidDeck = "Progress lives in DroidDeck."
    const val steamInDroidDeck = "Steam on this device"
    const val addToHomeInDroidDeck = "Choose Add to home screen on a game in DroidDeck."
    const val theme = "Theme"
    const val icons = "Icons"
    const val matchTheme = "Match theme"
    const val background = "Background"
    const val motion = "Motion"
    const val ribbons = "Ribbons"
    const val embers = "Embers"
    const val motionStatic = "Static"
    const val motionOff = "Off"
    const val speedSlow = "Slow"
    const val speedSlower = "Slower"
    const val home = "Home"
    const val editHome = "Edit home"
    const val setAsHome = "Set as Home"
    const val androidSettings = "Android settings"
    const val defaultHomeApp = "Home app"
    const val bluetooth = "Bluetooth"
    const val battery = "Battery"
    const val builtIn = "Built-in"
    const val primaryPanel = "Main screen"
    const val top = "Top"
    const val bottom = "Bottom"
    const val addFolder = "Add a folder"
    const val syncGames = "Sync games"
    const val syncing = "Syncing"
    const val rommConnection = "RomM connection"
    const val editRommConnection = "Edit connection"
    const val addRommServer = "Add a RomM server"
    const val signOut = "Sign out"
    const val remove = "Remove"
    const val clientApiToken = "Client API token"
    const val saveToken = "Save token"
    const val steamGridDb = "SteamGridDB"
    const val addKey = "Add key"
    const val forgetKey = "Forget key"
    /** Settings › Library: the switch for one computer's Moonlight games, on its page. */
    const val hostGames = "Games"

    /** Settings › Library's last tile, and the page it opens. */
    const val add = "Add"
    const val pairHostBody = "In Sunshine or Apollo on that computer, open PIN and type this code. Its games then appear here."
    const val forgetHost = "Forget"
    const val forgetHostBody = "Its games leave Home, except ones you pinned in Moonlight. Remove Foldcade from the computer's client list too."
    const val pairWrongPinTitle = "That code didn’t match"
    const val pairWrongPinBody = "Turn the switch on again for a new code."
    const val pairUnreachableBody = "Check that it’s on and on this network, then try again."
    const val pairRefusedBody = "It may be pairing another device. Try again in a few minutes."
    const val keepKey = "Keep key"
    const val forgetArtKeyTitle = "Forget the SteamGridDB key?"
    const val forgetArtKeyBody = "Foldcade stops finding art on SteamGridDB until you add a key again."
    const val apiKey = "API key"
    const val saveKey = "Save key"
    const val artKeyHelp = "A free key from steamgriddb.com, under Preferences › API. It finds art for Moonlight, PC and DroidDeck games, and anything the other sources miss. Foldcade keeps the key on this device."
    const val artKeyRefused = "SteamGridDB didn’t accept this key."
    const val artKeyUnreachable = "Couldn’t reach SteamGridDB. Try again in a moment."
    const val rommTokenHelp = "RomM uses a client API token or a device-code token. Foldcade stores the token and does not store a password."
    const val rommDeviceCode = "Approve this code on your phone, or paste a token instead."
    const val rommCodeExpired = "This code expired. Get a new code or paste a token."
    const val rommCodeDenied = "Approval was declined. Get a new code or paste a token."
    const val rommCodeUnavailable = "Couldn’t get approval from RomM. Get a new code or paste a token."
    const val rommNewCode = "Get a new code"
    const val signInAgainTitle = "Sign in again"
    const val signInAgainBody = "Saved credentials could not be read."
    const val useAsHome = "Use as Home"
    const val notNow = "Not now"
    const val allowUsage = "Allow"
    const val usageAccessTitle = "Count time outside Foldcade?"
    const val usageAccessBody = "Usage access lets Foldcade see when a game is open, including from Recents or another launcher. Without it, play time only follows launches from here, so it stays approximate."
    const val approximatePlay = "Approximate"
    const val playTime = "Play time"
    const val playTimeEverywhere = "Everywhere"
    const val playTimeHere = "Foldcade only"
    const val sort = "Sort"

    /** Settings › Library: the order All lists games in. Each folder's own Sort is on its Y menu. */
    const val sortAll = "Sort All"
    const val sortRecent = "Recent"
    const val sortListed = "A–Z"
    const val sortCustom = "Custom"
    const val sortMostPlayed = "Most played"
    const val continueGrant = "Continue"
    const val ok = "OK"
    const val folderGrantTitle = "Choose a folder"
    const val folderGrantBody = "Foldcade lists games from a folder you choose."
    const val folderGrantFailed = "That folder couldn’t be kept. Choose it again."
    const val wifi = "Wi-Fi"
    const val cellular = "Cellular"
    const val offline = "Offline"
    const val homePromptTitle = "Use Foldcade as Home?"
    const val homePromptBody = "You can change this later in Android settings."
    const val noLibrary = "No library yet"
    const val libraryUnreachable = "Couldn’t reach the library"
    const val downloadFailed = "Couldn’t download"
    const val downloadFailedBody = "Check that your RomM server is reachable, then try again."
    const val deleteFailed = "Couldn’t delete"
    const val deleteFailedBody = "The download is still on this device. Try again."
    const val saveNotPlaced = "Couldn’t load the save for"
    const val saveNotPlacedBody = "The game didn’t start, so it won’t play from an old save. Check the player’s save folder, then try again."
    const val edenSaveNeedsUser = "Open Eden once so it sets up its user, then come back and start the game again. Your RomM save hasn’t been loaded."
    const val edenSaveUnresolved = "Foldcade couldn’t find where this game’s Eden save goes on this device, or couldn’t check RomM. The game didn’t start. Your RomM save is safe."
    const val tryAgain = "Try again"
    const val noPlatforms = "This library has no platforms"
    const val noGames = "No games"
    const val loading = "Loading"
    const val buttonLabels = "Button labels"
    const val pressConfirm = "Press Confirm"
    const val setUp = "Set up"
    const val buttonCalibrationHelp = "Press the face button you want for Confirm. Start cancels."
    const val confirm = "Confirm"
    const val back = "Back"
    const val missingPlayerBody = "Foldcade can’t start this game without it."
    const val closePlayerTitle = "Close the other game?"
    const val closePlayerBody = "This game uses both screens. The one already running has to close first."
    const val closeIt = "Close it"
    const val saveFolderTitle = "Save folder"
    const val saveFolderBody = "Choose where this player keeps its saves."
    /** The same ask before a game whose saves sync with its server. */
    const val saveFolderSyncBody = "Choose where this player keeps its saves to keep them on RomM."
    const val playWithoutSyncing = "Play without syncing"
    const val savesNotSyncing = "Saves not syncing"
    const val savesWaiting = "Saves waiting to upload"
    const val savesDeviceOnly = "Saves stay on this device"
    const val notSyncing = "Not syncing"
    fun savesNotSyncingTitle(player: String) = "$player saves aren’t syncing"
    const val savesFoundTitle = "Saves found"

    /** Each emulator Foldcade just found a folder for, with where: `Azahar · saves/Azahar`. */
    fun savesFoundBody(found: List<Pair<String, String>>) = found.joinToString(", ") { (name, path) -> "$name · $path" }
    fun savesNotSyncingEmulatorsBody(count: Int) = "$count emulators need setup."
    const val savesNotSyncingBody = "Set up to keep them on RomM."
    fun syncSavesTitle(player: String) = "Sync $player saves?"
    fun syncSavesBody(player: String) = "Foldcade keeps $player saves on RomM. Choose $player’s save folder to start."
    /** The same ask for a player that cannot start a game without its folder. Not now only closes. */
    const val saveFolderNeededBody = "This player needs its folder to start games and sync saves."
    /** The ask for a named player's folder, when the picker opens at it. */
    const val saveFolderOpensThere = "It opens there: tap Use this folder, then Allow."
    /** Settings › an emulator's save folder row, and its values. Its page already names the emulator. */
    const val saveFolder = "Save folder"
    const val playerSavesSet = "Set"
    const val choose = "Choose"
    const val playerSavesUnset = "Choose folder"
    const val missingFileBody = "This game has no file Foldcade can hand to the player."
    const val noSharedPathTitle = "can’t open this file"
    const val noSharedPathBody = "It opens games from this device’s storage. Add the game from a folder there, or download it again."
    const val retroArchCoresTitle = "Show RetroArch’s cores"
    const val retroArchCoresBody = "Foldcade starts each game with a core you installed. On the next screen, choose Use this folder. Not now starts the game anyway."
    const val missingCoreTitle = "RetroArch has no core for"
    const val missingCoreBodyStart = "In RetroArch, open Online Updater › Core Downloader and install"
    const val missingCoreBodyEnd = ". Then start the game again."
    const val retroArchStorageTitle = "RetroArch can’t read games yet"
    const val retroArchStorageBody = "Open RetroArch once and allow access to files. Then start the game again."
    const val retroArchStoreBuildBody = "RetroArch from Google Play can’t open games from other apps. Install it from retroarch.com or F-Droid."
    const val openRetroArch = "Open RetroArch"
    /** Opens the player named on a dialog, such as Open DuckStation. */
    const val openPlayer = "Open"
    const val hostedSavesSet = "Syncing"
    const val hostedSavesUnset = "Set up"
    /** The row under a player's folder, on its page. */
    const val memoryCard = "Memory card"
    /** The row under a player's folder for one-time setup, on its page. */
    const val setup = "Setup"
    /** The dialog for that setup: "Set up aPS3e". */
    const val playerSetupTitle = "Set up"
    /** A player's memory cards or other saves, synced through a folder Foldcade hosts. */
    const val hostedSavesTitle = "Sync"
    const val retroArchWrongFolder = "That folder has no cores. Choose RetroArch’s top folder, the one that holds cores, then Use this folder."
    /** On RetroArch's page. */
    const val retroArchCores = "Cores"
    const val edenWrongFolderTitle = "That isn’t Eden’s folder"
    const val edenWrongFolderBody = "Choose Use this folder where the picker opens, without opening a folder inside it."
    const val edenRestartTitle = "Restart Eden once"
    const val edenRestartBody = "Foldcade added Switch files to Eden. Eden reads them only when it starts. On the next screen, choose Force stop, then OK. Press B to come back and start the game again."
    /** On Eden's page. */
    const val edenKeys = "Saves, keys & firmware"
    /** The tiles of RetroArch and Eden when no player with a save folder names them. */
    const val retroArch = "RetroArch"
    const val eden = "Eden"
    const val edenUserSteps = "Make a user profile, then come back."
    const val edenFromRomm = "From RomM"
    const val showCores = "Show"
    const val cores = "cores"
    const val oneCore = "1 core"
    const val savesKeptTitle = "Both saves were kept"
    const val savesKeptBody = "This game used the copy from the server. The other copy was archived."
    const val saveConflictTitle = "Two saves"
    const val saveConflictBody = "Both copies are kept. Choose which one this game plays."
    const val saveConflictTop = "Choose which save to play on the bottom screen."
    const val keepThisOne = "Keep this one"
    const val keepBoth = "Keep both"
    const val decideLater = "Decide later"
    const val savesKeptMore = "More than one game did this."
    const val offlineSaveTitle = "Played the save on this device"
    const val offlineSaveBody = "It will upload when the server is reachable."
    const val themeWentBackTitle = "A theme update was undone"
    const val themeWentBackBody = "Foldcade stopped twice while it was in use, so the version before it is back."
    const val themeTakenOutTitle = "A theme was taken out"
    const val themeTakenOutBody = "Foldcade stopped twice while it was in use, so it was removed."
    const val downloading = "Downloading"
    const val gettingFirmware = "Getting firmware"
    const val checkingDownload = "Checking"
    const val syncingSaves = "Syncing saves"
    const val savesSynced = "Saves synced"
    const val savesKeptBoth = "Kept both saves"
    const val savesSyncLater = "Saves will sync later"
    const val savesNotSynced = "Saves didn’t sync"
    /** The game is still open in its player, behind Home. A brings it back. */
    const val paused = "Paused"
    const val notOnDevice = "Not on this device"
    const val onDevice = "On this device"
    const val inFolder = "In this folder"
    const val androidGames = "Android Games"
    const val apps = "Apps"
    const val hiddenApps = "Hidden"
    /** The Edit home row that opens the Hidden grid. */
    const val hiddenAppsRow = "Hidden"
    const val noHiddenApps = "Nothing hidden"
    /** Edit home's row, and the view it opens: every folder Foldcade makes, on Home or off it. */
    const val folders = "Folders"
    const val noFolders = "No folders yet"
    /** A Foldcade folder's switch, in its Y menu: its tile is on Home. */
    const val onHome = "On Home"
    /** What Undo says once a folder came off Home. It keeps its games, and Folders brings it back. */
    const val offHome = "Off Home"
    const val pin = "Pin"
    const val unpin = "Unpin"
    const val moveToGames = "Move to Android Games"
    const val moveToApps = "Move to Apps"
    const val hideApp = "Hide"
    const val showApp = "Show"
    const val allLibrary = "All library"
    /** The L1 tiles' words: All library and Android Games, said in one word each. */
    const val tileAll = "All"
    const val tileGames = "Games"
    const val addNewGames = "New games on Home"
    /** A switch's value, for readers and labels: New games on Home, Game art. */
    const val on = "On"
    const val off = "Off"
    const val artwork = "Game art"
    const val move = "Move"
    /** Closes the gaps on the board on screen. */
    const val tidy = "Tidy"
    const val tidied = "Tidied"
    const val rename = "Rename"
    const val icon = "Icon"
    const val newCollection = "New collection"
    /** New collection's word under its tile in Y's menu. */
    const val tileNew = "New"
    /** A new collection's name, before a number: Collection 1. */
    const val collection = "Collection"
    const val recentlyPlayed = "Recently played"
    /** An open collection with nothing in it, and the way to fill it. */
    const val noGamesYet = "No games yet"
    const val addGamesHowBefore = "Add games opens All. Press"
    const val addGamesHowAfter = "on a game, then Add to."
    const val addGames = "Add games"
    const val undo = "Undo"
    const val hidden = "Hidden"
    const val unpinned = "Unpinned"
    const val removed = "Removed"
    const val hold = "Hold"
    const val deleteShortcut = "Delete shortcut"
    const val deleteDownload = "Delete download"
    const val restart = "Restart"
    const val restartTitle = "Restart the game?"
    const val restartBody = "It closes where you left it. Anything not saved is lost."
    const val addTo = "Add to"
    const val emptySlot = "Empty slot"
    const val letterJump = "Letter"
    const val screenJump = "Screen"
    const val hintDrop = "Drop"
    /** A held game over a group that does not have it yet. */
    const val hintAdd = "Add"
    /** A held pin over a group the game is already in. */
    const val hintPutBack = "Put back"
    /** A held game over the Home target above a group's grid. */
    const val hintTakeOut = "Take out"
    const val hintCancel = "Cancel"
    const val hintChoose = "Choose"
    const val hintColor = "Color"
    const val hintOptions = "Options"
    const val collectionIcon = "Collection icon"
    const val hintTop = "Top"
    const val hintPlay = "Play"
    const val hintResume = "Resume"
    const val hintBottom = "Bottom"
    const val onHomeMark = "On home"
    const val stillInAll = "Still in All"
}

/** Which face actions the hint row should show. Glyphs come from [FaceMap], not from these flags. */
data class HintActions(val confirm: Boolean, val back: Boolean)

fun hintLine(activateDoesSomething: Boolean, backDoesSomething: Boolean): HintActions? {
    if (!activateDoesSomething && !backDoesSomething) return null
    return HintActions(confirm = activateDoesSomething, back = backDoesSomething)
}

enum class HintPlace {
    RootGrid,
    InsidePlatform,
    Menu,
    Dialog,
    Connect,
}

fun monogram(title: String): String {
    val char = title.firstOrNull { it.isLetterOrDigit() } ?: return ""
    return char.uppercaseChar().toString()
}

/**
 * Moving and choosing on a grid need no prompt, so the root grid has none and
 * a grid inside a folder shows Back only.
 */
fun hintFor(place: HintPlace): HintActions? = when (place) {
    HintPlace.RootGrid -> null
    HintPlace.InsidePlatform -> hintLine(activateDoesSomething = false, backDoesSomething = true)
    // A on a connect field raises the keyboard, and on Save it saves.
    HintPlace.Menu,
    HintPlace.Dialog,
    HintPlace.Connect,
    -> hintLine(activateDoesSomething = true, backDoesSomething = true)
}

/**
 * What A does to the focused game when it is not a choice of screens: a game
 * that takes both screens plays, and one still open in its player resumes.
 */
enum class PlayKey {
    None,
    Play,
    Resume,
}

/** One glyph and one word in the hint row. */
data class HintKey(val key: PromptKey, val label: String)

/**
 * The keys a grid lists under it. Editing home and the All library use keys
 * that are not obvious, so those modes name them. Anything else follows [actions].
 */
fun gridHints(
    actions: HintActions?,
    homeKeys: HomeKeys,
    faceMap: FaceMap,
    screens: Boolean = false,
    options: Boolean = false,
    drop: HoldDrop = HoldDrop.Place,
    play: PlayKey = PlayKey.None,
): List<HintKey> {
    val confirm = promptKeyOf(faceMap.confirmKey) ?: PromptKey.FaceA
    val back = promptKeyOf(faceMap.backKey) ?: PromptKey.FaceB
    val roles = actionFaces(faceMap)
    // A one-screen game opens on the top screen with confirm and on the bottom with the free face button, X unless calibration took it.
    // A game that takes both screens says Play, so confirm is named on every game; one still open says Resume.
    val launch = when {
        play == PlayKey.Resume -> listOf(HintKey(confirm, Copy.hintResume))
        screens -> listOf(HintKey(confirm, Copy.hintTop), HintKey(roles.bottomGlyph, Copy.hintBottom))
        play == PlayKey.Play -> listOf(HintKey(confirm, Copy.hintPlay))
        else -> emptyList()
    }
    // The options button names its menu only when the focused tile has one.
    val menu = if (options) listOf(HintKey(roles.optionsGlyph, Copy.hintOptions)) else emptyList()
    return when (homeKeys) {
        // The options button drops too, unnamed: the thumb that lifted the tile is already on it.
        HomeKeys.Selecting -> listOf(HintKey(confirm, SelectionCopy.check), HintKey(roles.optionsGlyph, SelectionCopy.actions), HintKey(back, SelectionCopy.done))
        HomeKeys.Moving -> listOf(
            HintKey(confirm, dropLabel(drop)),
            HintKey(back, Copy.hintCancel),
        )
        // B puts the old look back, so it reads as Cancel.
        HomeKeys.PickingIcon -> listOf(
            HintKey(confirm, Copy.hintChoose),
            HintKey(PromptKey.L1, ""),
            HintKey(PromptKey.R1, Copy.hintColor),
            HintKey(back, Copy.hintCancel),
        )
        // A types the key under the ring, which needs no word.
        HomeKeys.Typing -> listOf(
            HintKey(typingFaces(faceMap).eraseGlyph, KeyboardCopy.erase),
            HintKey(typingFaces(faceMap).spaceGlyph, KeyboardCopy.space),
            HintKey(PromptKey.L1, ""),
            HintKey(PromptKey.R1, KeyboardCopy.caret),
            HintKey(PromptKey.L2, KeyboardCopy.shift),
            HintKey(PromptKey.Start, KeyboardCopy.done),
            HintKey(back, Copy.hintCancel),
        )
        // The shell names Find's own keys, which depend on what has been typed.
        HomeKeys.Finding -> findKeyboardHints(faceMap, typed = false)
        HomeKeys.FindResults -> findResultHints(faceMap, screens, options)
        HomeKeys.ChoosingArt -> artPickerHints(faceMap)
        HomeKeys.AllLibrary -> launch + menu + HintKey(back, Copy.back)
        HomeKeys.Idle -> buildList {
            if (launch.isNotEmpty()) {
                addAll(launch)
            } else if (actions?.confirm == true) {
                add(HintKey(confirm, Copy.confirm))
            }
            addAll(menu)
            if (actions?.back == true) add(HintKey(back, Copy.back))
        }
    }
}

/** What A says while a tile is held. */
fun dropLabel(drop: HoldDrop): String = when (drop) {
    HoldDrop.Place -> Copy.hintDrop
    HoldDrop.Add -> Copy.hintAdd
    HoldDrop.PutBack -> Copy.hintPutBack
    HoldDrop.TakeOut -> Copy.hintTakeOut
}

fun triggerHints(letters: Boolean): List<HintKey> = listOf(
    HintKey(PromptKey.L2, ""),
    HintKey(PromptKey.R2, if (letters) Copy.letterJump else Copy.screenJump),
)
