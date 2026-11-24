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
    data class Bound(val root: FolderRef, val setAtMillis: Long? = null) : LocationFact
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
enum class SpotIssue { NoTitleId, UnsupportedImage, NoProfile, CardNotFolder, ArchiveName, Unreadable }

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

enum class UnconfirmedWhy { FolderNotChecked, GameNotChecked, RommUnreachable, PartnerCloud, HostComputer, AppKeepsSaves, NoPlayer }



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
)

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

sealed interface RepairState {
    data object NotStarted : RepairState
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
        UnconfirmedWhy.RommUnreachable -> "Can’t reach RomM" + (status.lastReceiptMillis?.let { " · saved there ${saveAge(it, now)}" } ?: "")
        UnconfirmedWhy.PartnerCloud -> (status.keeper as? SaveKeeper.Partner).let { "Saves in ${it?.cloud ?: it?.name ?: "the player"} · not confirmed" }
        UnconfirmedWhy.HostComputer -> "Saves on your computer"
        UnconfirmedWhy.AppKeepsSaves -> "This app keeps its own saves"
        UnconfirmedWhy.NoPlayer -> "No emulator for these saves"
    }
    SaveStatus.NothingYet -> "No saves yet"
    is SaveStatus.Protected -> "Saved to RomM" + (status.receiptMillis?.let { " · ${saveAge(it, now)}" } ?: "")
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
        health is SaveStatus.Waiting && activity != TileActivity.Syncing && activity !is TileActivity.Synced ||
        activity == null || activity == TileActivity.Paused || activity is TileActivity.OnHost
fun heroActivityLine(health: SaveStatus, activity: TileActivity): String? =
    if (saveHealthLeads(health, activity) && (activity == TileActivity.Syncing || activity is TileActivity.Synced)) null else activityLine(activity)

object SaveProtectionCopy {
    const val fix = "Fix it"
    const val playAnyway = "Play anyway"
    const val folderSet = "Folder set"
    const val notChecked = "Not checked yet"
    const val checkByPlaying = "Play a game and save in it to check."
    const val guidedDone = "Folder set. Choose your RomM save in Y › Saves."
    const val keptHere = "Your saves on this device are kept."
    const val chooseFolder = "Choose folder"
    const val thenChoose = "Then choose the same folder here."
    const val later = "Later"
    const val itsFine = "It’s fine"
    const val checkWhere = "Check where they save before you play."
    fun emulatorsNeedFix(count: Int) = "$count emulators need a fix"
    fun wrongPick(name: String, folder: String) = "That isn’t the folder $name uses. Choose $folder."
    const val autoFind = "Auto-find saves"
    const val incidentEmpty = "No saves on RomM yet. Your saves on this device are kept."
    fun fixTitle(name: String) = "Fix $name’s folder"
    fun bindingTitle(name: String, known: Boolean) = if (known) "Use the folder $name uses" else "Choose $name’s folder"
    fun liveFolder(name: String, folder: String) = "$name now saves in   $folder"
    fun watchedFolder(folder: String) = "Foldcade was watching   $folder"
    fun fixBeforeChoosing(incident: SaveIncident) = "${title(incident)}. Fix it, then choose a save."
    const val foldersSet = "Folders set. Each is checked the first time a game saves."
    fun checkDetail(location: LocationFact.Verified, name: String, now: Long = System.currentTimeMillis()): String {
        val whenChecked = "${saveAge(location.atMillis, now)} ${java.time.Instant.ofEpochMilli(location.atMillis).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}"
        return when (location.evidence.kind) {
            EvidenceKind.FreshLog -> "$name’s log in ${location.root.label}, $whenChecked."
            EvidenceKind.SessionWrite -> "${location.evidence.detail}, $whenChecked."
            EvidenceKind.OwnProvider -> "$name’s own folder."
            else -> "${location.evidence.detail}, $whenChecked."
        }
    }
    fun locationSteps(key: String): String? = when (key.substringBefore('.')) {
        "azahar" -> "1. In Azahar, choose Select User Folder.\n2. Choose the folder that should keep your saves, then Use this folder › Allow."
        "melonds" -> "1. Settings › Save Files › Save next to ROM file: Off.\n2. Save file directory › choose your saves folder."
        "armsx2" -> "1. ☰ › Setup / Change Folders › App Data Folder.\n2. Choose the folder that holds bios and memcards."
        "ppsspp" -> "1. Settings › System › Memory Stick Folder.\n2. Create or Choose a PSP folder › choose your PSP folder."
        "retroarch" -> "1. Settings › Directory › Save Files › choose RetroArch/saves.\n2. Settings › Saving: Sort Saves into Folders by Core On; by Content Directory Off; Write Saves to Content Directory Off."
        "gba-emu", "gbc-emu", "nes-emu", "snes9x-ex", "md-emu" -> "1. Options › File Paths › Saves › Select Folder.\n2. Options › System › Launch Mode › Main Slot (No State)."
        "dolphin" -> "1. Settings › Config › GameCube › GameCube Slot A Device › GCI Folder.\n2. Check the game’s Game Settings for its own card."
        "eden" -> "1. Settings › Advanced settings › Custom Paths › Save Data Directory.\n2. Settings › Profile Manager: check the user you play as."
        else -> null
    }
    fun title(incident: SaveIncident): String = with(incident) { when (kind) {
        IncidentKind.WrongFolder -> "$playerName is saving somewhere else"
        IncidentKind.MaybeWrongFolder -> "$playerName may be saving somewhere else"
        IncidentKind.FolderEmptied -> "$playerName’s save folder is empty"
        IncidentKind.LostAccess -> "Foldcade can’t see $playerName’s saves"
        IncidentKind.MaybeNewSave -> "Two saves · choose one"
    } }
    fun safeOnRomm(receipt: Long?, hasVersions: Boolean): String = when {
        receipt != null -> "Your save on RomM is safe, from ${saveWhen(receipt)}."
        hasVersions -> "Your saves on RomM are safe."
        else -> ""
    }
    fun saveWhen(at: Long): String = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm"))
    fun body(incident: SaveIncident, receipt: Long? = null, hasVersions: Boolean = false): String = with(incident) {
        val safe = safeOnRomm(receipt, hasVersions)
        listOf(when (kind) {
            IncidentKind.WrongFolder -> if (live != null) "$playerName now uses the folder ${live.label}, not ${bound?.label}." else "$playerName stopped using ${bound?.label}."
            IncidentKind.FolderEmptied -> "Its saves in ${bound?.label} are gone."
            IncidentKind.LostAccess -> "Allow ${bound?.label ?: "its folder"} again so saves reach RomM."
            IncidentKind.MaybeWrongFolder -> "Check where it saves."
            IncidentKind.MaybeNewSave -> "Choose which save to play."
        }, safe, when (kind) {
            IncidentKind.WrongFolder -> "Fix it first, or this session’s progress isn’t protected."
            IncidentKind.FolderEmptied -> "Put it back before you play."
            else -> ""
        }).filter { it.isNotEmpty() }.joinToString(" ")
    }
    fun unsupportedFormat(format: String, supported: String) = "$format doesn’t sync yet. $supported do."
    fun spot(issue: SpotIssue, azahar: Boolean = false): String = when (issue) {
        SpotIssue.NoTitleId -> if (azahar) "This game file doesn’t say which 3DS game it is. A .cci syncs; a .zcci doesn’t." else "Foldcade can’t read this disc’s game ID."
        SpotIssue.UnsupportedImage -> "This file type doesn’t sync yet."
        SpotIssue.NoProfile -> Copy.edenSaveNeedsUser
        SpotIssue.CardNotFolder -> "Choose a folder memory card in the emulator."
        SpotIssue.ArchiveName -> SavePickerCopy.incompatible
        SpotIssue.Unreadable -> "Foldcade couldn’t read the game file. Try again once it’s downloaded."
    }
}
