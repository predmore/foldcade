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
)

fun builtInTheme(): Theme = Theme(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
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
    val heroMeta: TextUnit = 18.sp
    val availability: TextUnit = 16.sp
    val gridLabel: TextUnit = 16.sp
    val dialogTitle: TextUnit = 20.sp
    val dialogBody: TextUnit = 16.sp
    val menuRow: TextUnit = 18.sp
    val sideRow: TextUnit = 18.sp
    val hint: TextUnit = 14.sp
}

/** Host-owned layout. Motion lives on [Motion]. */
object Metrics {
    const val columns = 4
    const val insetFraction = 0.04f
    const val gapFraction = 0.02f
    const val focusStrokePx = 3f
    const val heroInsetPx = 48f
    const val dialogInsetPx = 48f
    const val dialogBorderPx = 2f
    const val heroArtFraction = 0.62f

    /** Space between the hint line and the top of a focus stroke, in px. */
    const val chromeClearancePx = 28f
}

object Copy {
    const val launchOnTop = "Launch on top"
    const val launchOnBottom = "Launch on bottom"
    const val usesBothScreens = "Uses both screens"
    const val progressInGameNative = "Progress lives in GameNative."
    const val addShortcutInGameNative = "Add a game's shortcut in GameNative."
    const val library = "Library"
    const val theme = "Theme"
    const val background = "Background"
    const val motion = "Motion"
    const val ribbons = "Ribbons"
    const val embers = "Embers"
    const val motionStatic = "Static"
    const val motionOff = "Off"
    const val speedSlow = "Slow"
    const val speedSlower = "Slower"
    const val arrange = "Arrange"
    const val setAsHome = "Set as Home"
    const val androidSettings = "Android settings"
    const val defaultHomeApp = "Default home app"
    const val bluetooth = "Bluetooth"
    const val display = "Display"
    const val sound = "Sound"
    const val battery = "Battery"
    const val appInfo = "App info"
    const val builtIn = "Built-in"
    const val primaryPanel = "Primary panel"
    const val top = "Top"
    const val bottom = "Bottom"
    const val addFolder = "Add a folder"
    const val connectRomm = "Connect RomM"
    const val setUpRomm = "Set up RomM"
    const val signOut = "Sign out / forget credentials"
    const val clientApiToken = "Client API token"
    const val saveToken = "Save token"
    const val rommTokenHelp = "RomM uses a client API token or a device-code token. Foldcade stores the token and does not store a password."
    const val signInAgainTitle = "Sign in again"
    const val signInAgainBody = "Saved credentials could not be read."
    const val useAsHome = "Use as Home"
    const val notNow = "Not now"
    const val allowUsage = "Allow"
    const val usageAccessTitle = "Count time outside Foldcade?"
    const val usageAccessBody = "Usage access lets Foldcade see when a game is open, including from Recents or another launcher. Without it, play time only follows launches from here, so it stays approximate."
    const val approximatePlay = "Approximate"
    const val continueGrant = "Continue"
    const val ok = "OK"
    const val folderGrantTitle = "Choose a folder"
    const val folderGrantBody = "Foldcade lists games from a folder you choose."
    const val wifi = "Wi-Fi"
    const val cellular = "Cellular"
    const val offline = "Offline"
    const val homePromptTitle = "Use Foldcade as Home?"
    const val homePromptBody = "You can change this later in Android settings."
    const val unavailable = "Unavailable"
    const val noLibrary = "No library yet"
    const val libraryUnreachable = "Couldn’t reach the library"
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
    const val saveFolderBody = "This player keeps its own saves. Not now starts the game. You can choose a folder later from the left panel."
    const val playerSavesSet = "Set"
    const val playerSavesUnset = "Not set"
    const val missingFileBody = "This game has no file Foldcade can hand to the player."
    const val savesKeptTitle = "Both saves were kept"
    const val savesKeptBody = "This game used the copy from the server. The other copy was archived."
    const val savesKeptMore = "More than one game did this."
    const val offlineSaveTitle = "Played the save on this device"
    const val offlineSaveBody = "It will upload when the server is reachable."
    const val notOnDevice = "Not on this device"
    const val onDevice = "On this device"
    const val inFolder = "In this folder"
    const val androidGames = "Android Games"
    const val apps = "Apps"
    const val hiddenApps = "Hidden"
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
    const val moonlight = "Moonlight"
    const val moonlightImported = "Imported list"
    const val moonlightPinned = "Pinned shortcuts only"
    const val importMoonlightTitle = "Import Moonlight games"
    const val importMoonlightBody = "Checked games go to All Apps and the Moonlight folder."
    const val importMoonlight = "Import"
    const val editHome = "Edit home"
    const val allLibrary = "All library"
    const val addNewGames = "Add new games"
    const val addNewOn = "On"
    const val addNewOff = "Off"
    const val allGames = "All Games"
    const val allApps = "All Apps"
    const val sortTitle = "A to Z"
    const val sortRecent = "Recently played"
    const val sortSystem = "System"
    const val allSystems = "All systems"
    const val homeSlot = "Home"
    const val editHint = "A picks up. D-pad moves. A drops. B puts it back. Start or Back is done. X removes from home. It stays in All."
    const val allHint = "A launches. Y adds to home. X chooses a folder. L2 and R2 jump a letter."
    const val onHomeMark = "On home"
    const val newFolder = "Folder"
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

fun hintFor(place: HintPlace): HintActions? = when (place) {
    HintPlace.RootGrid -> hintLine(activateDoesSomething = true, backDoesSomething = false)
    HintPlace.InsidePlatform,
    HintPlace.Menu,
    HintPlace.Dialog,
    -> hintLine(activateDoesSomething = true, backDoesSomething = true)
    HintPlace.Connect -> hintLine(activateDoesSomething = false, backDoesSomething = true)
}
