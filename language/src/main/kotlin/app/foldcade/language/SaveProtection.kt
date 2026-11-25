package app.foldcade.language

/** What Foldcade knows about one game's saves on this device. Each fact can be unknown. */
data class SaveFacts(
    val keeper: SaveKeeper,
    val location: LocationFact,
    val spot: SpotFact,
    val offDevice: OffDeviceFact,
    /** Held bytes RomM has not accepted, since when. Null: nothing waiting. */
    val waitingSinceMillis: Long?,
    /** Open incidents for this game or its emulator, newest first. */
    val incidents: List<SaveIncident>,
    /** This device has play history for the game, so a device-only save has something at stake. */
    val playedHere: Boolean,
)

/** Who is responsible for the save. */
sealed interface SaveKeeper {
    /** A save adapter Foldcade drives. [name] is the emulator's display name. */
    data class Emulator(val name: String) : SaveKeeper
    /** The player keeps its own saves, possibly in a cloud: DroidDeck, GameNative. */
    data class Partner(val name: String, val cloud: String?) : SaveKeeper
    /** Streaming: saves stay on the computer. */
    data object HostComputer : SaveKeeper
    /** An Android app or game. */
    data object App : SaveKeeper
    /** An emulator that Foldcade can launch but cannot sync. */
    data class LaunchOnly(val name: String) : SaveKeeper
    /** No installed player for this game right now. */
    data object NoPlayer : SaveKeeper
}

sealed interface LocationFact {
    /** Not a folder Foldcade can watch (non-emulators, launch-only players). */
    data object NotApplicable : LocationFact
    /** Setup missing: today's SaveIssue values, plus a grant that is gone. */
    data class Missing(val issue: SaveIssue) : LocationFact
    /** A folder is bound and reachable. No live evidence for it yet. Unknown, not fine. */
    data class Bound(val root: FolderRef, val setAtMillis: Long? = null, val configUnavailable: Boolean = false,
        val cardOutside: Boolean = false, val configDetail: String? = null) : LocationFact
    /** Live evidence that the emulator uses [root], taken at [atMillis]. */
    data class Verified(val root: FolderRef, val evidence: Evidence, val atMillis: Long) : LocationFact
    /** Positive evidence that the emulator uses another folder, [live] when known. */
    data class Mismatch(val bound: FolderRef, val live: FolderRef?, val evidence: Evidence, val atMillis: Long) : LocationFact
}

/** [identity] compares folders (see §2.4). [label] is what the user reads, such as `saves/Azahar`. */
data class FolderRef(val identity: String, val label: String)

data class Evidence(val kind: EvidenceKind, val detail: String)

enum class EvidenceKind {
    /** A log written after the baseline names the folder. Azahar. */
    FreshLog,
    /** The game's save in this folder changed during a session Foldcade started. Every adapter. */
    SessionWrite,
    /** The folder is the root of the installed emulator's own DocumentsProvider. aPS3e, Dolphin, Eden. */
    OwnProvider,
    /** The emulator opened this save through Foldcade's hosted provider during the session. DuckStation. */
    HostedOpen,
    /** A readable config file names the folder. RetroArch, Eden, DuckStation, ARMSX2, Dolphin. */
    Config,
    /** Foldcade chose the folder in the launch itself, on a version that was tested. PPSSPP `--memstick`, later. */
    PinnedLaunch,
}

sealed interface SpotFact {
    /** Not resolved yet, as before the game file has been read. */
    data object Unchecked : SpotFact
    data object Resolved : SpotFact
    data class Unresolved(val reason: SpotIssue, val detail: String? = null) : SpotFact
}

/** Why a game's save has no place. Each one has its own sentence (§6.3). */
enum class SpotIssue { NoTitleId, UnsupportedImage, UnsupportedSaveFormat, NoProfile, CardNotFolder, ArchiveName, Unreadable, EdenLocation, EdenGrant, EdenProfile }

sealed interface OffDeviceFact {
    /** RomM accepted the bytes the working copy holds now. [atMillis] null for ledgers older than this design. */
    data class OnRomm(val atMillis: Long?) : OffDeviceFact
    /** RomM has none of this game's saves, and none are held here. */
    data object NothingYet : OffDeviceFact
    /** Nowhere to send it: no RomM game, signed out, or a server too old for save sync. */
    data class NoDestination(val why: NoDestinationWhy) : OffDeviceFact
    /** The last receipt is known, but RomM can't be reached to confirm it is still the newest. */
    data class Unreachable(val lastReceiptMillis: Long?) : OffDeviceFact
    /** A partner service may hold it, and Foldcade has no receipt. */
    data class PartnerUnknown(val service: String) : OffDeviceFact
    /** The player does not say. */
    data object Unknown : OffDeviceFact
}

enum class NoDestinationWhy { NotOnRomm, SignedOut, ServerTooOld, PlayerCantSync }

enum class SaveIssue { NoFolder, HostedFolder, NeedsSetup, NoSlot, LostAccess }

sealed interface SaveStatus {
    data class Problem(val incident: SaveIncident) : SaveStatus
    data class NotSyncing(val issue: SaveIssue? = null, val spot: SpotIssue? = null, val destination: NoDestinationWhy? = null, val detail: String? = null) : SaveStatus
    data class Waiting(val sinceMillis: Long) : SaveStatus
    data class DeviceOnly(val why: NoDestinationWhy, val playedHere: Boolean = false, val emulator: String? = null) : SaveStatus
    data class Unconfirmed(val why: UnconfirmedWhy, val lastReceiptMillis: Long? = null, val keeper: SaveKeeper? = null) : SaveStatus
    data object NothingYet : SaveStatus
    data class Protected(val receiptMillis: Long?, val checkedMillis: Long) : SaveStatus
}

enum class UnconfirmedWhy { FolderNotChecked, GameNotChecked, RommCopyUnconfirmed, RommUnreachable, PartnerCloud, HostComputer, AppKeepsSaves, NoPlayer }



data class SaveIncident(
    val id: String,                  // "<kind>:<saveFolderId>[:<gameId>]"
    val kind: IncidentKind,
    val playerKey: String,           // saveFolderId(); one incident per emulator folder
    val playerName: String,
    val gameIds: List<String>,       // games known to be affected; empty = every game on the emulator
    val bound: FolderRef?,
    val live: FolderRef?,
    val evidence: Evidence,
    val openedAtMillis: Long,
    /** Copies already kept on RomM as Backups, by server id. */
    val kept: List<String> = emptyList(),
    /** Local copies staged in save-handover, waiting for RomM. */
    val staged: List<String> = emptyList(),
    val repair: RepairState = RepairState.NotStarted,
    /** A bypass session wrote suspect bytes. Only choosing a recovery folder clears this. */
    val recoveryRequired: Boolean = false,
    val subject: SaveIncidentGame? = null,
    val quarantinedHash: String? = null,
    val automaticDone: Boolean = false,
    val quarantineSourceUri: String? = null,
    val quarantineBackupId: String? = null,
    /** Exact alternate file named by the launch config, retained through guided repair. */
    val alternateSavePath: String? = null,
    val capturedAlternateHashes: List<String> = emptyList(),
    val besideGame: Boolean = false,
    val besideSource: BesideSaveSource? = null,
)

enum class BesideSaveSource { Progress, Blank, Missing }

/** The locally resolved game and player behind an incident, retained for repair after restart. */
data class SaveIncidentGame(
    val libraryId: String,
    val gameId: String,
    val platformId: String,
    val label: String,
    val playerId: String,
    val targetUri: String?,
    val origin: String?,
    val packageName: String?,
    val installFirstMillis: Long?,
    val edenContext: String? = null,
    val romFileName: String? = null,
)

val SaveIncident.saveLayoutChanged: Boolean get() =
    kind == IncidentKind.WrongFolder && live != null && live.identity == bound?.identity

val SaveIncident.armsX2OutsideCard: Boolean get() =
    kind == IncidentKind.WrongFolder && playerKey == "armsx2" && alternateSavePath != null

enum class IncidentKind {
    /** Positive evidence: the emulator uses another folder. Gates launch. */
    WrongFolder,
    /** Strong negative evidence (§4.2): no proof of the other folder. Does not gate; asks to confirm. */
    MaybeWrongFolder,
    /** A save Foldcade placed or read is gone, and the folder's markers are gone too. Gates launch. */
    FolderEmptied,
    /** Foldcade's grant, or the emulator's own grant on that folder, is gone. Gates launch. */
    LostAccess,
    /** A returned save looks like a fresh or reset game (§4.3). Kept as a Backup and not promoted. Asks. */
    MaybeNewSave,
}

val SaveIncident.isEdenGrant: Boolean get() = playerKey == "eden" && kind == IncidentKind.WrongFolder &&
    evidence.kind == EvidenceKind.Config && live != null

val SaveIncident.isEdenSessionChange: Boolean get() = playerKey == "eden" && kind == IncidentKind.MaybeNewSave &&
    evidence.kind == EvidenceKind.Config

val SaveIncident.hostedCardMismatch: Boolean get() =
    kind == IncidentKind.MaybeNewSave && evidence.kind == EvidenceKind.HostedOpen

val SaveIncident.hostedCardSilence: Boolean get() =
    kind == IncidentKind.MaybeWrongFolder && evidence.kind == EvidenceKind.HostedOpen

sealed interface RepairState {
    data object NotStarted : RepairState
    data object Keeping : RepairState
    data class WaitingForRomm(val resume: Placing? = null) : RepairState
    data class WaitingForClose(val resume: Placing? = null, val backupsKept: Boolean = false) : RepairState
    data object Rebinding : RepairState
    data class Choosing(val headId: String, val backupId: String, val headHash: String, val suspect: Boolean) : RepairState
    data class Placing(val saveId: String, val headHash: String, val liveHash: String?) : RepairState
    data object Guided : RepairState
    data object Binding : RepairState
    data object Done : RepairState
}

fun deriveSaveStatus(facts: SaveFacts): SaveStatus = with(facts) {
    val emulator = keeper is SaveKeeper.Emulator
    val destination = (offDevice as? OffDeviceFact.NoDestination)?.why
    val activeIncidents = incidents.filter { it.repair != RepairState.Done }
    when {
        activeIncidents.isNotEmpty() -> SaveStatus.Problem(activeIncidents.first())
        location is LocationFact.Mismatch -> SaveStatus.Problem(SaveIncident(
            "WrongFolder:${location.bound.identity}", IncidentKind.WrongFolder, location.bound.identity,
            (keeper as? SaveKeeper.Emulator)?.name.orEmpty(), emptyList(), location.bound, location.live,
            location.evidence, location.atMillis,
        ))
        emulator && destination in setOf(NoDestinationWhy.SignedOut, NoDestinationWhy.ServerTooOld) -> SaveStatus.NotSyncing(null, null, destination)
        emulator && (location is LocationFact.Missing || spot is SpotFact.Unresolved) -> SaveStatus.NotSyncing(
            (location as? LocationFact.Missing)?.issue, (spot as? SpotFact.Unresolved)?.reason, null, (spot as? SpotFact.Unresolved)?.detail,
        )
        waitingSinceMillis != null -> SaveStatus.Waiting(waitingSinceMillis)
        (emulator || keeper is SaveKeeper.LaunchOnly) && destination in setOf(NoDestinationWhy.NotOnRomm, NoDestinationWhy.PlayerCantSync) ->
            SaveStatus.DeviceOnly(destination!!, playedHere, (keeper as? SaveKeeper.LaunchOnly)?.name ?: (keeper as? SaveKeeper.Emulator)?.name)
        keeper !is SaveKeeper.Emulator -> SaveStatus.Unconfirmed(when (keeper) {
            is SaveKeeper.Partner -> UnconfirmedWhy.PartnerCloud
            SaveKeeper.HostComputer -> UnconfirmedWhy.HostComputer
            SaveKeeper.App -> UnconfirmedWhy.AppKeepsSaves
            SaveKeeper.NoPlayer -> UnconfirmedWhy.NoPlayer
            else -> UnconfirmedWhy.FolderNotChecked
        }, keeper = keeper.takeIf { it is SaveKeeper.Partner })
        location is LocationFact.Bound -> SaveStatus.Unconfirmed(UnconfirmedWhy.FolderNotChecked)
        spot is SpotFact.Unchecked -> SaveStatus.Unconfirmed(UnconfirmedWhy.GameNotChecked)
        offDevice is OffDeviceFact.Unreachable -> SaveStatus.Unconfirmed(UnconfirmedWhy.RommUnreachable, offDevice.lastReceiptMillis)
        location is LocationFact.Verified && spot == SpotFact.Resolved && offDevice == OffDeviceFact.NothingYet -> SaveStatus.NothingYet
        location is LocationFact.Verified && spot == SpotFact.Resolved && offDevice is OffDeviceFact.OnRomm -> SaveStatus.Protected(offDevice.atMillis, location.atMillis)
        location is LocationFact.Verified && spot == SpotFact.Resolved -> SaveStatus.Unconfirmed(UnconfirmedWhy.RommCopyUnconfirmed)
        else -> SaveStatus.Unconfirmed(UnconfirmedWhy.FolderNotChecked)
    }
}

val SaveStatus.needsAttention: Boolean get() = when (this) {
    is SaveStatus.Problem, is SaveStatus.NotSyncing, is SaveStatus.Waiting -> true
    is SaveStatus.DeviceOnly -> playedHere
    else -> false
}

/** Device-only has an attention line even before it earns a tile badge. */
val SaveStatus.attentionLine: Boolean get() = needsAttention || this is SaveStatus.DeviceOnly

fun saveStatusLine(status: SaveStatus, now: Long = System.currentTimeMillis()): String = when (status) {
    is SaveStatus.Problem -> SaveProtectionCopy.title(status.incident)
    is SaveStatus.NotSyncing -> when (status.destination) {
        NoDestinationWhy.ServerTooOld -> "Update RomM to 5.4 to sync saves"
        NoDestinationWhy.SignedOut -> Copy.rommSignInNoticeTitle
        else -> Copy.savesNotSyncing
    }
    is SaveStatus.Waiting -> Copy.savesWaiting
    is SaveStatus.DeviceOnly -> if (status.why == NoDestinationWhy.PlayerCantSync) "${status.emulator ?: "This emulator"} saves only on this device" else Copy.savesDeviceOnly
    is SaveStatus.Unconfirmed -> when (status.why) {
        UnconfirmedWhy.FolderNotChecked -> "Save folder not checked yet"
        UnconfirmedWhy.GameNotChecked -> "Save not checked yet"
        UnconfirmedWhy.RommCopyUnconfirmed -> "Save checked · No RomM copy confirmed"
        UnconfirmedWhy.RommUnreachable -> "Can’t reach RomM" + (status.lastReceiptMillis?.let { " · last saved ${saveAge(it, now)}" } ?: "")
        UnconfirmedWhy.PartnerCloud -> (status.keeper as? SaveKeeper.Partner).let { "Saves in ${it?.cloud ?: it?.name ?: "the player"} · not confirmed" }
        UnconfirmedWhy.HostComputer -> "Saves on your computer"
        UnconfirmedWhy.AppKeepsSaves -> "This app keeps its own saves"
        UnconfirmedWhy.NoPlayer -> "No emulator for these saves"
    }
    SaveStatus.NothingYet -> "No saves yet"
    is SaveStatus.Protected -> "Saved to RomM" + (status.receiptMillis?.let { " ${saveAge(it, now)}" } ?: "")
}

fun saveAge(at: Long, now: Long): String = when (val days = ((now - at).coerceAtLeast(0) / 86_400_000L)) {
    0L -> "today"
    1L -> "yesterday"
    else -> "$days days ago"
}

// Keep these surface helpers shared by the hero and save picker.
fun saveHealthLine(health: SaveStatus): String = saveStatusLine(health)
fun saveHealthLeads(health: SaveStatus, activity: TileActivity?): Boolean =
    health is SaveStatus.Problem || health is SaveStatus.NotSyncing ||
        activity is TileActivity.Synced && activity.mark == SyncMark.Synced &&
        (health == SaveStatus.NothingYet || health is SaveStatus.Unconfirmed) ||
        health is SaveStatus.Waiting && activity != TileActivity.Syncing && activity !is TileActivity.Synced ||
        activity == null || activity == TileActivity.Paused || activity is TileActivity.OnHost
fun heroActivityLine(health: SaveStatus, activity: TileActivity): String? =
    if (saveHealthLeads(health, activity) && (activity == TileActivity.Syncing || activity is TileActivity.Synced)) null else activityLine(activity)

object SaveProtectionCopy {
    const val mayBeNewGame = "May be a new game"
    const val chooseProgress = "Which save do you want to play?"
    const val bothKept = "Both are kept on RomM."
    const val keepingCopies = "Keeping your saves on RomM."
    const val waitForRomm = "Waiting for RomM. Foldcade will try again when it can connect."
    const val placingSave = "Putting your save back."
    const val stepRestore = "Put your save back"
    const val tryAgain = "Try again"
    const val confirmClose = "Confirm close"
    const val alreadyQuit = "Already quit?"
    const val openSaves = "Open Saves"
    const val chooseInSaves = "Open Y › Saves to choose which progress to keep."
    const val thisThor = "This Thor"
    const val romm = "RomM"
    const val twoSaves = "Two saves"
    fun waitForClose(game: String?, player: String) =
        "Already quit ${game ?: "the game"} in $player? Confirm to continue. If it’s still open, save and quit first."
    fun quitInEmulator(game: String?, player: String) = "Quit ${game ?: "the game"} in $player, then come back."
    fun confirmCloseBody(game: String?, player: String) =
        "Only continue if you’ve quit ${game ?: "the game"} in $player. Foldcade can’t tell if it’s still open."
    fun usingFolder(folder: String) = "Switching Foldcade to $folder."
    fun repairDone(folder: String) = "Your RomM save is back in $folder."
    fun besideRepairDone(folder: String) = "${repairDone(folder)} The save from beside the ROM is in Y › Saves under Backups."
    fun fixed(game: String) = "Fixed: $game"
    fun fromFolder(folder: String) = "From the folder $folder"
    const val fix = "Fix it"
    const val playAnyway = "Play anyway"
    const val folderSet = "Folder set"
    const val notChecked = "Not checked yet"
    const val checkByPlaying = "Play a game and save in it to check."
    const val armsX2ConfigUnavailableDetail = "Foldcade can’t read PCSX2-Android.ini in this folder. Open ARMSX2 once, or choose the shared data folder ARMSX2 uses and allow access."
    const val armsX2CardOutside = "ARMSX2’s MemoryCards folder is outside its data folder, so Foldcade can’t sync this card. " +
        "With ARMSX2 closed, open PCSX2-Android.ini and, under [Folders], set MemoryCards = memcards."
    const val armsX2OutsideCardSteps = "1. Close ARMSX2.\n" +
        "2. In PCSX2-Android.ini in the ARMSX2 data folder, under [Folders], set MemoryCards = memcards. Don’t delete the outside card. Other games’ saves may be on it.\n" +
        "3. Choose the ARMSX2 data folder again."
    fun configUnavailableDetail(name: String) = "Foldcade can’t read $name’s save settings. It can still check for save writes during play."
    const val gbcPickInApp = "In GBC.emu, open Options › File Paths › Saves › Select Folder. Pick your save folder there, then choose the same folder in Foldcade."
    const val savedBesideRom = "Saved beside the ROM"
    const val besideRomFolder = "ROM folder"
    const val besideNoHead = "Folder set. The save from beside the ROM is in Y › Saves under Backups."
    fun besideDoneLocal(folder: String) = "Folder set. Your save beside the ROM is still there. Move it into $folder to keep playing it."
    /** The Fix page once the folder is set and a save can be chosen now. */
    const val guidedDone = "Folder set. Choose your RomM save in Y › Saves."
    /** The same, offline: Saves would only say it can't reach RomM, so the page just closes. */
    const val guidedDoneOffline = "Folder set. Join Wi-Fi, then choose your save in Y › Saves."
    const val guidedDoneLocal = "Folder set. A copy of your save is kept on this device."
    const val guidedDoneRommWaiting = "Folder set. Your copies are waiting for RomM."
    const val guidedDoneCopiesUnchecked = "Folder set. Foldcade couldn’t check your copies."
    const val guidedDoneWithoutLocalCopy = "Folder set. No copy is kept on this device yet."
    /** Copies kept on RomM before its destination went away are still there. */
    const val guidedDoneKeptOnRomm = "Folder set. Your copies are kept on RomM."
    const val chooseFolder = "Choose folder"
    const val chooseSave = "Choose save"
    const val thenChoose = "Then choose the same folder here."
    const val hostedCardSteps = "In DuckStation, check Memory Card 1 for this game. Keep a copy of your card before changing its type."
    const val noHostedCard = "DuckStation didn’t open a memory card from Foldcade’s folder."
    const val canReadConfig = "Readable"
    const val cantReadConfig = "Can’t read config"
    const val dolphinCustomCardDetail = "Dolphin’s config sends this game’s memory card to its own GCI folder."
    const val dolphinCustomCardSteps = "1. Keep a copy of the game’s .gci files from that folder.\n2. Remove the GCIFolderAPath lines from Config/Dolphin.ini and the game’s GameSettings file, then choose Fix again."
    const val aps3eVfsRemapped = "aPS3e’s VFS moves dev_hdd0, so Foldcade can’t find its saves."
    const val aps3eVfsUnsupported = "Foldcade can’t confirm aPS3e’s dev_hdd0 mapping."
    const val aps3eOwnFolder = "Choose aPS3e’s own folder."
    const val later = "Later"
    const val itsFine = "It’s fine"
    const val checkWhere = "Check where they save before you play."
    fun emulatorsNeedFix(count: Int) = "$count emulators need a fix"
    fun wrongPick(name: String, folder: String) = "That isn’t the folder $name uses. Choose $folder."
    const val incidentEmpty = "No saves on RomM yet. Your saves on this device are kept."
    fun fixTitle(name: String) = "Fix $name’s folder"
    /** The Fix page's one step when Foldcade knows the folder: the picker opens there. */
    fun chooseThere(folder: String) = "Choose $folder, then tap Use this folder › Allow."
    /** Without the live folder, the emulator's own steps say where it saves. */
    fun findFolder(name: String) = "Find the folder $name saves in, then choose it here."
    fun fixBeforeChoosing(incident: SaveIncident) = "${title(incident)}. Fix it, then choose a save."
    const val foldersSet = "Folders set. Each is checked the first time a game saves."

    // The Fix page's top screen.
    const val safeHeadline = "Your save is safe on RomM."
    const val safeHeadlineVersions = "Your saves on RomM are safe."
    fun nowSavesIn(name: String) = "$name now saves in"
    const val wasWatching = "Foldcade was watching"
    fun nowWatches(folder: String) = "Foldcade now watches $folder."
    const val stepKeep = "Keep every copy on RomM"
    const val stepKeepRomm = "Keep copies on RomM"
    fun stepKept(count: Int) = if (count == 1) "1 kept" else "$count kept"
    const val stepWaiting = "Waiting for RomM"
    const val stepKeepLocal = "Keep a copy on this device"
    const val stepNotKeptYet = "Not kept yet"
    const val stepCopiesUnchecked = "Copies need checking"
    fun stepFolder(name: String, known: Boolean) = if (known) "Use the folder $name uses" else "Choose $name’s folder"
    const val stepChoose = "Choose which save to play"

    fun unchangedLog(name: String) = "$name didn’t use this folder last time you played."
    const val edenUserChanged = "Eden’s user changed during play."
    const val edenLocationChanged = "Eden’s save location changed during play."
    fun incidentDetail(incident: SaveIncident): String = with(incident) {
        if (kind == IncidentKind.WrongFolder && live == null && bound != null && evidence.kind == EvidenceKind.FreshLog)
            unchangedLog(playerName) else evidence.detail
    }
    /** Settings › Emulators' Check row, opened: the evidence in words, with one date format. */
    fun checkDetail(location: LocationFact.Verified, name: String, now: Long = System.currentTimeMillis(), style: DateStyle = DateStyle()): String {
        val whenChecked = saveWhen(location.atMillis, now, style, inSentence = true)
        return when (location.evidence.kind) {
            EvidenceKind.OwnProvider -> "$name’s own folder."
            EvidenceKind.SessionWrite -> "$name saved here $whenChecked."
            else -> "$name used this folder $whenChecked."
        }
    }
    fun locationSteps(key: String): String? = when (key.substringBefore('.')) {
        "azahar" -> "1. In Azahar, choose Select User Folder.\n2. Choose the folder that should keep your saves, then Use this folder › Allow."
        "melonds" -> "1. Settings › Save Files › Save next to ROM file: Off.\n2. Save file directory › choose your saves folder."
        "armsx2" -> "1. ☰ › Setup / Change Folders › App Data Folder.\n2. Choose the folder that holds bios and memcards."
        "ppsspp" -> "1. Settings › System › Memory Stick Folder.\n2. Create or Choose a PSP folder › choose your PSP folder."
        "retroarch" -> "1. In Settings › Directory › Save Files, choose the folder Foldcade watches.\n2. In Settings › Saving, turn on sorting by core. Turn off sorting by content directory and writing saves to content directory.\n3. Check Quick Menu › Overrides for the game, its content directory and its core. Save your configuration."
        "gba-emu", "gbc-emu", "nes-emu", "snes9x-ex", "md-emu" -> "1. Options › File Paths › Saves › Select Folder.\n2. Options › System › Launch Mode › Main Slot (No State)."
        "dolphin" -> "1. Settings › Config › GameCube › GameCube Slot A Device › GCI Folder.\n2. Check the game’s Game Settings for its own card."
        "eden" -> "1. Settings › Advanced settings › Custom Paths › Save Data Directory.\n2. Settings › Profile Manager: check the user you play as."
        else -> null
    }
    fun besideLocationSteps(incident: SaveIncident): String? {
        val folder = incident.bound?.label ?: return locationSteps(incident.playerKey)
        return if (incident.playerKey == "melonds")
            "1. Settings › Save Files › Save next to ROM file: Off.\n2. Save file directory › choose $folder."
        else "1. Options › File Paths › Saves › Select Folder. Choose $folder to switch away from Same As Content. If this game has its own save folder, change that too.\n2. Options › System › Launch Mode › Main Slot (No State)."
    }
    fun title(incident: SaveIncident): String = with(incident) { when (kind) {
        IncidentKind.WrongFolder -> when {
            isEdenGrant -> "Eden needs folder access"
            saveLayoutChanged -> "$playerName’s save layout changed"
            else -> "$playerName is saving somewhere else"
        }
        IncidentKind.MaybeWrongFolder -> "$playerName may be saving somewhere else"
        IncidentKind.FolderEmptied -> "$playerName’s save folder is empty"
        IncidentKind.LostAccess -> "Foldcade can’t see $playerName’s saves"
        IncidentKind.MaybeNewSave -> when {
            isEdenSessionChange -> evidence.detail.trimEnd('.')
            hostedCardMismatch -> "$playerName used a different memory card"
            else -> subject?.let { "Two saves for ${it.label}" } ?: "Two saves · choose one"
        }
    } }
    fun safeOnRomm(receipt: Long?, hasVersions: Boolean, now: Long = System.currentTimeMillis(), style: DateStyle = DateStyle()): String = when {
        receipt != null -> "Your save on RomM is safe, from ${saveWhen(receipt, now, style, inSentence = true)}."
        hasVersions -> safeHeadlineVersions
        else -> ""
    }
    /** R1 has one line: lead with the problem and keep the action visible. */
    fun noticeBody(incident: SaveIncident): String = with(incident) { when (kind) {
        IncidentKind.WrongFolder -> when {
            isEdenGrant -> "Allow ${live?.label} to sync Eden saves."
            dolphinCustomCard -> "Slot A uses a custom GCI folder. Fix it before you play."
            saveLayoutChanged -> "Save layout changed. Fix it before you play."
            besideGame -> "Saved beside the ROM. Fix it before you play."
            live != null -> "Saving in ${live.label}. Fix it before you play."
            else -> "Not saving in ${bound?.label ?: "this folder"}. Fix it before you play."
        }
        IncidentKind.FolderEmptied -> "${bound?.label ?: "The save folder"} is empty. Fix it before you play."
        IncidentKind.LostAccess -> "Can’t see ${bound?.label ?: "the save folder"}. Fix it before you play."
        IncidentKind.MaybeWrongFolder -> "May be saving elsewhere. Check before you play."
        IncidentKind.MaybeNewSave -> when {
            isEdenSessionChange -> "Check Eden’s user, then choose a save."
            hostedCardMismatch -> cardNotice(live?.label)
            repair is RepairState.WaitingForRomm -> "Keeping both saves. Waiting for RomM."
            else -> "Two saves. Choose which to play."
        }
    } }

    private fun cardNotice(name: String?): String {
        val action = " Check before you play."
        val prefix = "Using "
        val card = name?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }
            ?: return "Different memory card.$action"
        val budget = 50 - prefix.length - action.length - 1
        val short = if (card.length <= budget) "$card." else {
            val end = if (card[budget - 1].isHighSurrogate()) budget - 1 else budget
            card.take(end).trimEnd() + "…"
        }
        return "$prefix$short$action"
    }
    /**
     * What happened, then what RomM holds, then what to do. [stop] is the launch gate's: only it
     * says what playing now costs. The other detail surfaces end with "Fix it before you play."
     */
    fun body(
        incident: SaveIncident,
        receipt: Long? = null,
        hasVersions: Boolean = false,
        stop: Boolean = false,
        now: Long = System.currentTimeMillis(),
        style: DateStyle = DateStyle(),
    ): String = with(incident) {
        val safe = safeOnRomm(receipt, hasVersions, now, style)
        listOf(when (kind) {
            // The folder and the emulator often share a name ("Azahar"), so the folder is called one.
            IncidentKind.WrongFolder -> when {
                isEdenGrant -> "Allow ${live?.label} so Foldcade can sync Eden’s saves."
                dolphinCustomCard -> dolphinCustomCardDetail
                saveLayoutChanged -> "$playerName uses a different save location inside ${live?.label ?: "the chosen folder"}."
                besideGame -> "$playerName saved beside a ROM."
                live != null -> "$playerName now saves in ${live.label} instead of the ${bound?.label ?: "chosen"} folder."
                else -> "$playerName didn’t use the folder ${bound?.label ?: "set in Foldcade"} last time you played."
            }
            IncidentKind.FolderEmptied -> "Its saves in ${bound?.label} are gone."
            IncidentKind.LostAccess -> "Allow ${bound?.label ?: "its folder"} again so saves reach RomM."
            IncidentKind.MaybeWrongFolder -> if (hostedCardSilence)
                "$noHostedCard In DuckStation, open App Settings › Folders › Memory Cards and choose Foldcade."
            else "Check where it saves."
            IncidentKind.MaybeNewSave -> when {
                isEdenSessionChange -> "${evidence.detail} Check Eden’s user before choosing a save."
                hostedCardMismatch -> evidence.detail
                else -> "Choose which save to play."
            }
        }, safe, when (kind) {
            IncidentKind.WrongFolder -> if (stop) "Fix it first, or this session’s progress isn’t protected." else "Fix it before you play."
            IncidentKind.FolderEmptied -> "Put it back before you play."
            else -> ""
        }).filter { it.isNotEmpty() }.joinToString(" ")
    }
    fun unsupportedFormat(format: String, supported: String) = "$format doesn’t sync yet. $supported do."
    fun spot(issue: SpotIssue, azahar: Boolean = false): String = when (issue) {
        SpotIssue.NoTitleId -> if (azahar) "This game file doesn’t say which 3DS game it is. A .cci syncs; a .zcci doesn’t." else "Foldcade can’t read this disc’s game ID."
        SpotIssue.UnsupportedImage -> "This file type doesn’t sync yet."
        SpotIssue.UnsupportedSaveFormat -> "This core’s save format doesn’t sync yet."
        SpotIssue.NoProfile -> Copy.edenSaveNeedsUser
        SpotIssue.EdenLocation -> "Foldcade couldn’t check where Eden keeps this save."
        SpotIssue.EdenGrant -> "Allow access to Eden’s folder in Foldcade."
        SpotIssue.EdenProfile -> "Foldcade couldn’t read Eden’s selected user. Check Eden’s Profile Manager."
        SpotIssue.CardNotFolder -> "Choose a folder memory card in the emulator."
        SpotIssue.ArchiveName -> SavePickerCopy.incompatible
        SpotIssue.Unreadable -> "Foldcade couldn’t read the game file. Try again once it’s downloaded."
    }
}
