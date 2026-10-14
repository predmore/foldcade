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
)

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

/** Ends of the focus ring's gradient: green at the top left, blue at the bottom right. */
object FocusRingColors {
    val start: Color = Color(0xFF52B788)
    val end: Color = Color(0xFF3E8EC0)
}

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
    const val apiKey = "API key"
    const val saveKey = "Save key"
    const val artKeyHelp = "A free key from steamgriddb.com, under Preferences › API. It finds art for Moonlight, PC and DroidDeck games, and anything the other sources miss. Foldcade keeps the key on this device."
    const val artKeyRefused = "SteamGridDB didn’t accept this key."
    const val artKeyUnreachable = "Couldn’t reach SteamGridDB. Try again in a moment."
    const val rommTokenHelp = "RomM uses a client API token or a device-code token. Foldcade stores the token and does not store a password."
    const val rommSameServer = "That server is already connected."
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
    const val sortRecent = "Recent"
    const val sortListed = "Library"
    const val continueGrant = "Continue"
    const val ok = "OK"
    const val folderGrantTitle = "Choose a folder"
    const val folderGrantBody = "Foldcade lists games from a folder you choose."
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
    const val tryAgain = "Try again"
    const val noPlatforms = "This library has no platforms"
    const val noGames = "No games"
    const val loading = "Loading"
    const val buttonLabels = "Button labels"
    const val pressConfirm = "Press Confirm"
    const val confirm = "Confirm"
    const val back = "Back"
    const val missingPlayerBody = "Foldcade can’t start this game without it."
    const val closePlayerTitle = "Close the other game?"
    const val closePlayerBody = "This game uses both screens. The one already running has to close first."
    const val closeIt = "Close it"
    const val saveFolderTitle = "Save folder"
    const val saveFolderBody = "This player keeps its own saves. Not now starts the game. You can choose one later in Settings."
    const val playerSavesSet = "Folder set"
    const val playerSavesUnset = "Choose folder"
    const val missingFileBody = "This game has no file Foldcade can hand to the player."
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
    const val downloading = "Downloading"
    const val checkingDownload = "Checking"
    const val syncingSaves = "Syncing saves"
    const val savesSynced = "Saves synced"
    const val savesKeptBoth = "Kept both saves"
    const val savesSyncLater = "Saves will sync later"
    const val savesNotSynced = "Saves didn’t sync"
    const val notOnDevice = "Not on this device"
    const val onDevice = "On this device"
    const val inFolder = "In this folder"
    const val androidGames = "Android Games"
    const val apps = "Apps"
    const val hiddenApps = "Hidden"
    /** The Edit home row that opens the Hidden grid. */
    const val hiddenAppsRow = "Hidden apps"
    const val noApps = "No apps"
    const val noHiddenApps = "No hidden apps"
    const val pin = "Pin"
    const val unpin = "Unpin"
    const val moveToGames = "Move to Android Games"
    const val moveToApps = "Move to Apps"
    const val hideApp = "Hide"
    const val showApp = "Show"
    const val androidGameMeta = "Android game"
    const val appMeta = "App"
    const val allLibrary = "All library"
    /** The L1 tiles' words: All library and Android Games, said in one word each. */
    const val tileAll = "All"
    const val tileGames = "Games"
    const val addNewGames = "New games on Home"
    /** A switch's value, for readers and labels: New games on Home, Game art. */
    const val on = "On"
    const val off = "Off"
    const val artwork = "Game art"
    const val homeSlot = "Home"
    const val move = "Move"
    /** Closes the gaps on the board on screen. */
    const val tidy = "Tidy"
    const val rename = "Rename"
    const val icon = "Icon"
    const val favorite = "Favorite"
    const val unfavorite = "Unfavorite"
    const val pinToHome = "Pin to Home"
    const val newCollection = "New collection"
    /** A new collection's name, before a number: Collection 1. */
    const val collection = "Collection"
    const val favorites = "Favorites"
    const val recentlyPlayed = "Recently played"
    const val deleteDownload = "Delete download"
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
): List<HintKey> {
    val confirm = promptKeyOf(faceMap.confirmKey) ?: PromptKey.FaceA
    val back = promptKeyOf(faceMap.backKey) ?: PromptKey.FaceB
    // A one-screen game opens on the top screen with A and on the bottom with X.
    val launch = if (screens) listOf(HintKey(confirm, Copy.hintTop), HintKey(PromptKey.FaceX, Copy.hintBottom)) else emptyList()
    // Y names its menu only when the focused tile has one.
    val menu = if (options) listOf(HintKey(PromptKey.FaceY, Copy.hintOptions)) else emptyList()
    return when (homeKeys) {
        // Y drops too, unnamed: the thumb that lifted the tile is already on it.
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
