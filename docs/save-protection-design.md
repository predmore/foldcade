# Save protection design

Design, 24 November 2026. Input: [save-safety-audit.md](save-safety-audit.md), plus the current code. This document changes nothing yet. Where the audit and the code disagree, the code wins, and the disagreement is noted in [§9](#9-where-the-audit-and-the-code-differ).

## The promise

A game shows **Saved to RomM** only when Foldcade can show three things:

1. **The emulator really uses the folder Foldcade watches.** There is live evidence, and Foldcade records when it got it.
2. **The game's save resolves in that folder.**
3. **RomM has the bytes that are there now.** The ledger agrees with the working copy, with a time.

Anything less shows what is missing, in words. Unknown is a state of its own, and it never draws a check. A problem the user can lose progress to (a wrong folder, a save that can't be placed, a save that may have been reset) stops the ordinary launch. It says the RomM copy is safe, and it fixes the problem or walks the user through fixing it.

The incident would play out like this. Azahar is re-pointed at an empty `/sdcard/Azahar`, and its fresh log in that folder names it. Foldcade reads that log on Home resume. Azahar's tile in Settings turns amber and reads **Wrong folder**. R1 says **Azahar is saving somewhere else**. Sonic Boom's tile gets the amber badge. Pressing A shows the gate instead of booting a new game. **Fix it** keeps both folders' copies on RomM and binds `/sdcard/Azahar`. It then puts the RomM save there, checks it, and plays.

---

## 1. Protection model

### 1.1 Facts

All of these are pure and live in `language/src/main/kotlin/app/foldcade/language/SaveProtection.kt`. That file replaces `SaveHealth.kt`. `language` has no dependency on `api`, so these types use plain strings and times. `app` maps API results onto them.

```kotlin
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
    data class Bound(val root: FolderRef) : LocationFact
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
    data class Unresolved(val reason: SpotIssue) : SpotFact
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
```

`SaveIssue` gains `LostAccess`. That covers Foldcade's grant gone, or a player that picks its folder in its own picker no longer holding it.

### 1.2 What users see

```kotlin
sealed interface SaveStatus {
    data class Problem(val incident: SaveIncident) : SaveStatus
    data class NotSyncing(val issue: SaveIssue?, val spot: SpotIssue?, val destination: NoDestinationWhy?) : SaveStatus
    data class Waiting(val sinceMillis: Long) : SaveStatus
    data class DeviceOnly(val why: NoDestinationWhy) : SaveStatus
    data class Unconfirmed(val why: UnconfirmedWhy, val lastReceiptMillis: Long?) : SaveStatus
    data object NothingYet : SaveStatus
    data class Protected(val receiptMillis: Long?, val checkedMillis: Long) : SaveStatus
}

enum class UnconfirmedWhy { FolderNotChecked, GameNotChecked, RommCopyUnconfirmed, RommUnreachable, PartnerCloud, HostComputer, AppKeepsSaves, NoPlayer }

fun deriveSaveStatus(facts: SaveFacts): SaveStatus
```

`deriveSaveStatus` checks these rows in order and stops at the first one that applies:

| # | When | Status | Tone |
|---|---|---|---|
| 1 | `incidents` not empty | `Problem(incidents.first())` | Attention |
| 2 | `location is Mismatch`. This can't happen without an incident, but stay safe. | `Problem` (synthesized `WrongFolder`) | Attention |
| 3 | Emulator, and `offDevice is NoDestination(SignedOut \| ServerTooOld)` | `NotSyncing(destination)` | Attention |
| 4 | Emulator, and `location is Missing`, or `spot is Unresolved` | `NotSyncing(issue, spot)` | Attention |
| 5 | `waitingSinceMillis != null` (any keeper; a save waiting for an uninstalled emulator still says so) | `Waiting` | Attention |
| 6 | Emulator or launch-only, and `offDevice is NoDestination(NotOnRomm \| PlayerCantSync)` | `DeviceOnly` | Attention line. Badge only if `playedHere` (§6.1). |
| 7 | Partner, host, app or no player | `Unconfirmed(PartnerCloud \| HostComputer \| AppKeepsSaves \| NoPlayer)` | Quiet |
| 8 | Emulator, and `location is Bound` | `Unconfirmed(FolderNotChecked)` | Quiet |
| 9 | Emulator, and `spot is Unchecked` | `Unconfirmed(GameNotChecked)` | Quiet |
| 10 | Emulator, and `offDevice is Unreachable` | `Unconfirmed(RommUnreachable, last)` | Quiet |
| 11 | Emulator, `location is Verified`, `spot is Resolved`, `offDevice is NothingYet` | `NothingYet` | Quiet |
| 12 | Emulator, `location is Verified`, `spot is Resolved`, `offDevice is OnRomm` | `Protected` | Clear |
| 13 | Emulator, `location is Verified`, `spot is Resolved`, and no matching RomM receipt | `Unconfirmed(RommCopyUnconfirmed)` | Quiet |
| 14 | Otherwise | `Unconfirmed(FolderNotChecked)` | Quiet |

The rule is that `Protected` is the only status built from proof, and only rows 11 and 12 need `Verified`. Unknown on its own always falls to `Unconfirmed`, which has its own words and never draws a check. `SaveProtectionTest` covers the whole table, including a test that every `LocationFact` × `OffDeviceFact` pair without `Verified` and `OnRomm` gives something other than `Protected`.

Tones:

- **Attention**: the amber not-syncing colour and line. The tile badge follows §6.1. These count in R1 and Settings.
- **Quiet**: the secondary text colour. No badge and no count.
- **Clear**: the secondary text colour. This is the only tone that may draw a check.

### 1.3 What a verification is worth

A location check belongs to the save folder (`saveFolderId()`), not to each game. Every game on that emulator shares it.

A `Verified` check stays valid while all of these hold:

- The binding is the same: its `FolderRef.identity` hasn't changed.
- The emulator's install stamp (`PackageInfo.firstInstallTime`, plus the resolved package name) is the same. A reinstall drops the check to `Bound`. An update keeps it, and probing goes on as usual.
- No later probe has found a positive mismatch.

There is no timer. Verification fails because something changes, not because time passes, and the probes in §2 and §4 exist to catch those changes. The hero line shows the age of the check, so an old one is visible as old.

### 1.4 Incidents

```kotlin
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
```

**Storage.** `app/src/main/kotlin/app/foldcade/SaveProtectionStore.kt` keeps two files, both written with the existing durable-file writer:

- `files/save-protection/locations.json`: one record per `saveFolderId()`. It holds `{identity, label, evidenceKind, detail, atMillis, installFirstMillis, packageName}`.
- `files/save-protection/incidents.json`: the open incidents.

Both are device-owned, the same class as save marks. They never go into setup backup or Android backup. Add both to `docs/persistence.md` under "Files, secrets, and transfer".

**When an incident closes:**

- A repair finishes and is verified (§5).
- Or a later probe gives `Verified` for the bound folder. For example, the user re-pointed the emulator back, or picked the live folder in Settings.
- Or, for the `Maybe*` kinds only, the user chooses **It's fine** (§6). This closes the incident but leaves the location `Bound`, which reads **Folder set**. A user's word is not evidence, so it can't produce **Checked** or **Saved to RomM**. The next session write verifies it.

A closed incident is deleted. The Backups it made stay on RomM and show in **Y › Saves**. A regression incident is scoped to the game and library. Choosing a copy or **Leave as is** resolves it.

---

## 2. Per-emulator evidence interface

### 2.1 Principle

Plugins **report what they can read**. The host **judges**, using pure code in `language`. A plugin does not write its own verdict logic. This keeps every adapter small, and it makes "is this fresh, does this contradict that" one tested function instead of eleven.

Two kinds of evidence need no plugin code. Every `SaveFolderHolder` gets both from the first PR:

- **Session write.** The game's resolved save in the bound folder changed between the post-placement baseline and the read-back. That proves the emulator wrote there, at that time.
- **Candidate diff.** The same relative save is resolved under each *other* root Foldcade can already read. These roots are the player's `saveFolderPlaces` plus the probe's extra places. If the save changed there and not in the bound folder, that is a positive mismatch.

### 2.2 API, minor 26

New file `api/src/main/kotlin/app/foldcade/api/plugin/SaveProbe.kt`. Bump `PLUGIN_API_MINOR` to 26 and add `SignalStrength` and `SaveServiceState` to the open-types list in `ApiVersion.kt`.

```kotlin
/**
 * A save-folder player that can say which folder it is really using. Read only:
 * nothing here writes, creates, or sets up. Players that don't implement this
 * still get session-write and candidate-diff evidence from the host. Added in API minor 26.
 */
interface SaveLocationProbe {
    /**
     * Folders below primary shared storage this player may be using instead of the
     * bound one, besides [SaveFolderHolder.saveFolderPlaces], such as `PSP` beside
     * `saves/PSP`. The host reads only those it can already read: a held tree grant,
     * or all-files access. It never asks for a grant to probe.
     */
    val probePlaces: List<String> get() = emptyList()

    /** Optional relative signature for shallow all-files discovery of custom roots. Minor 26. */
    val signatureFile: String? get() = null

    /**
     * The save's name beside the game file, for a player that can be set to save
     * next to the content (melonDS, RetroArch, EX). The host checks it only when the
     * game file sits in a folder Foldcade can read, such as its own RomM downloads.
     */
    fun saveBesideGame(slot: String, romFileName: String): String? = null

    /** What [root] says about the folder the player last used. Called before launch, after return, and on Home resume. */
    fun liveSignals(root: SaveFolderAccess): List<LiveSignal> = emptyList()

    /**
     * How to point the player at a folder in its own settings, as numbered lines
     * ([numberedSteps]), or null when only Foldcade's picker is needed.
     */
    val locationSteps: String? get() = null
}

/**
 * One readable sign of the player's live folder. [source] is a path below the root, such as
 * `log/azahar_log.txt`. [namesUri] is the folder it names, as written, or null when it only
 * shows activity. [perStart] is true when the player rewrites [source] on every game start,
 * so a start that left it alone is evidence too (§4.2).
 */
data class LiveSignal(
    val source: String,
    val namesUri: String?,
    val modifiedAtMillis: Long?,
    val strength: SignalStrength,
    val perStart: Boolean = false,
)

/** Open. [Proves] can open an incident; [Supports] can only be shown beside one. */
enum class SignalStrength { Proves, Supports }
```

Additive members with default bodies, all minor 26:

```kotlin
interface SaveFolderAccess {
    /** Last-modified time of [path], epoch milliseconds, or null when the provider does not say. Minor 26. */
    fun modifiedAtMillis(path: String): Long? = null
}

interface LibraryBackend {
    /** When the server last accepted the bytes now held for [game], per slot. Empty: none known. No network. Minor 26. */
    suspend fun saveReceipts(game: Game): Map<String, Long?> = emptyMap()

    /** Whether saves can reach this backend now. Minor 26. */
    suspend fun saveServiceState(): SaveServiceState = SaveServiceState.Unknown

    /**
     * Upload [observed] as kept-both Backups (unslotted archives), never as a slot head,
     * and verify each on the server. Returns the server ids. Throws Unavailable when it
     * can't; the host then keeps the bytes staged. Minor 26.
     */
    suspend fun keepAsBackups(game: Game, player: Player, observed: ObservedSaves): List<String> =
        throw PluginException.Unavailable("Backups are not supported")
}

/** Open. */
sealed interface SaveServiceState {
    data object Ready : SaveServiceState
    data object SignedOut : SaveServiceState
    data class ServerTooOld(val version: String) : SaveServiceState
    data class Unreachable(val lastContactMillis: Long?) : SaveServiceState
    data object Unknown : SaveServiceState
}
```

Backward compatibility:

- Older plugins don't implement `SaveLocationProbe`, and they still load.
- The defaults mean "nothing to add".
- The `keepAsBackups` default throws, so a backend without it never promotes a suspect save. The host keeps the bytes staged and the incident stays open.

Nothing changes a frozen data class's constructor.

### 2.3 Readings and judging, in `language`

New file `language/src/main/kotlin/app/foldcade/language/SaveEvidence.kt`:

```kotlin
data class SignalReading(val source: String, val names: String?, val modifiedAtMillis: Long?, val proves: Boolean, val perStart: Boolean)

/** One root at one moment. [spotHash] is the game's save there; null = absent, [spotReadable] false = couldn't read. */
data class RootReading(
    val root: FolderRef,
    val bound: Boolean,
    val signals: List<SignalReading>,
    val spotHash: String?,
    val spotReadable: Boolean,
    val markersPresent: Boolean?,
    /** Whether the emulator holds a grant on this tree; null when its picker isn't how it gets the folder. */
    val emulatorHolds: Boolean?,
)

data class ProbeSnapshot(val atMillis: Long, val installFirstMillis: Long?, val packageName: String, val roots: List<RootReading>)

sealed interface RootVerdict {
    data class Uses(val root: FolderRef, val evidence: Evidence, val atMillis: Long) : RootVerdict
    data class UsesOther(val live: FolderRef?, val evidence: Evidence, val atMillis: Long) : RootVerdict
    data class Lost(val kind: IncidentKind, val evidence: Evidence) : RootVerdict   // LostAccess, FolderEmptied
    data object NoNews : RootVerdict
}

/** Before a launch, and on Home resume: no session, only what the roots say now. */
fun judgeRoots(now: ProbeSnapshot, last: LocationFact): RootVerdict

/** After a session Foldcade started. [sessionMillis] is wall time from start to return. */
fun judgeSession(before: ProbeSnapshot, after: ProbeSnapshot, sessionMillis: Long): SessionVerdict

sealed interface SessionVerdict {
    data class Wrote(val root: FolderRef) : SessionVerdict                       // → Verified(SessionWrite)
    data class WroteElsewhere(val live: FolderRef) : SessionVerdict              // → WrongFolder incident
    data class Signalled(val verdict: RootVerdict) : SessionVerdict              // fresh log etc.
    data class SilentStart(val source: String) : SessionVerdict                  // → WrongFolder (approved perStart, launched session only)
    data object NoNews : SessionVerdict
}
```

`judgeRoots` rules. The first that applies wins:

1. **The bound root became unreachable.** A missing Foldcade grant gives `Lost(LostAccess)`. For a `saveFolderPickedInPlayer` player, `emulatorHolds == false` on the bound root means the emulator gave the folder up. If it holds exactly one other candidate, that gives `UsesOther(candidate, Evidence(Config, "Azahar holds Azahar, not saves/Azahar"))`. Otherwise it gives `Lost(LostAccess)`.
2. **The newest proving signal names a different root.** Take the `Proves` signal with the greatest `modifiedAtMillis` across all roots. If it names a root other than the bound one, the verdict is `UsesOther`. Ties, or unknown mtimes, give no verdict.
3. **The newest proving signal names the bound root** and is newer than the last check: `Uses(bound, FreshLog)`.
4. **The folder emptied.** The bound root's markers were present before and are missing now, and a held or server save exists for a game on this emulator: `Lost(FolderEmptied)`.
5. Otherwise `NoNews`.

`judgeSession` rules, in order:

1. **The bound save changed:** `Wrote(bound)`. The baseline is taken after Foldcade's own placement, so Foldcade's write never counts. This positive write takes precedence over log evidence, including a silent-start alarm.
2. **The bound save is unchanged, and candidates' saves changed:** `WroteElsewhere(candidate)`, or an unknown live root when several changed. A save that appears where none was counts as a change.
3. **A proving signal moved past the baseline and names a different root:** `Signalled(UsesOther(…))`. This includes a newly discovered custom root with no earlier game hash.
4. **An approved `perStart` signal in the bound root has exactly the same known mtime before and after a Foldcade-launched session of at least 20 s, and remains readable and eligible:** `SilentStart` → `WrongFolder`. Resumes, capped logs and moved clocks are excluded (§4.2).
5. **Another proving signal moved past the baseline:** `Signalled(judgeRoots(after, …))`.
6. Otherwise `NoNews`. An unchanged short session lands here and alarms nobody.

These are plain JVM tests in `language`: `SaveEvidenceTest` covers each rule, the stale-log case from the audit, restored mtimes, two-flavor ambiguity, and all-files alias identity.

### 2.4 The host side, in `app`

- **`SaveProbes.kt`** builds a `ProbeSnapshot`. It runs on IO and reads only:
  - Read-only `SaveFolderAccess` for the bound root, plus `modifiedAtMillis`, which comes from `DocumentsContract.Document.COLUMN_LAST_MODIFIED`.
  - Candidate roots. These are `saveFolderPlaces + probePlaces` as `primary:<place>`, known live roots from open incidents, and fresh signature roots discovered on return (§4.2), resolved through `SaveFolderFinder.reachable`. That covers held SAF grants and the all-files `SharedStorageProvider` alias, read only. A root Foldcade can't read is left out, not counted as absent.
  - The beside-game file, when the game sits in `Android/media/app.foldcade/romm/…` or a local library tree Foldcade holds.
  - `AndroidSaveFolderFinder.playerHolds` for each root, when `saveFolderPickedInPlayer`. Today that check runs only for *found* folders (`FoldcadeApp.kt:1685` keeps a chosen folder on Foldcade's grant alone). Here it runs for chosen ones too.
  - The game's spot is resolved with the existing `PlayerSaves.resolveSpots` against each root. It is hashed with `readSpot`. Bounds: at most 4 candidate roots, the 256 MiB zip limit as today, and the first 32 KiB of any log. Azahar also reads one byte near its 100 MiB logging limit to disable negative evidence for capped logs.
- **Identity.** `storageIdentity(uri)` maps an external-storage tree or document, and Foldcade's all-files alias, to `primary:<path>`. It lower-cases the volume. An emulator's own provider keeps `authority + documentId`. Every comparison uses identity, never the raw URI. The audit shows PPSSPP's two URIs for one `/sdcard/PSP`.
- **When probes run:**
  - Inside `LaunchSaves.prepare`, before `changedSince`. This is the gate (§3).
  - Right after placement, as the session baseline. The baseline is stored in the pending return (`LibraryReturn` gains `baseline: ProbeSnapshot?`). It is committed with the pending return, as today.
  - In `reconcileLocked`, judge and publish the return before reconciliation can announce a sync. Live-copy staging and uploads run separately after the normal handoff; either optional step can fail without blocking the bound save.
  - On startup and Home resume, inside `refreshPlayerSaves`. Only `judgeRoots` runs there, once per emulator folder, with no spot hashing. This is what makes Settings, the badge and R1 correct before anyone presses A.

### 2.5 Each emulator

"Today" means stock apps and grants Foldcade already holds or can ask for.

| Emulator | `probePlaces` / beside-game | `liveSignals` | Best stock evidence today | Gaps left / later |
|---|---|---|---|---|
| **Azahar** | `saveFolderPlaces` (`saves/Azahar`, `Azahar`), plus custom roots discovered after a session with all-files access: root depth ≤ 2 under primary storage, signature `log/azahar_log.txt`; none beside | The signature's `User directory path: <uri>` must name its own root and its mtime must be newer than the session baseline. It reports `Proves`, `namesUri` and the enabled `perStart` flag. `log/azahar_log.old.txt` is ignored. | **FreshLog**, session write, and launch-only negative evidence after 20 s (§4.2). Azahar retains its old grant, so the grant verdict stays off. | Title ID only from NCSD `.cci`. `.zcci` stays `NoTitleId`. Upstream `liveSaveLocation` query (§8.4). |
| **ARMSX2** | `ARMSX2` and a readable outside `MemoryCards` directory. Stock Android 11+ restricts access to another app’s `Android/data`. | At the top of the data root, `PCSX2-Android.ini` provides `[Folders] MemoryCards` and `armsx2-settings.json` records global and per-game slot 1 selection and mtime. Both are `Supports`. | A session write to the same watched card verifies the location. A write to a readable outside card with an unchanged watched copy confirms a mismatch for that game. | Live private preferences need an upstream query. |
| **aPS3e** | none | The installed build's provider exposes the absolute data-root document ID. A readable stock VFS mapping allows resolution under the default `dev_hdd0`. | **OwnProvider** at bind, plus a session write to matched `savedata`. A changed install stamp requires a regrant. | Remapped or unreadable VFS, other accounts, and title aliases outside the matched ID remain unresolved. |
| **Dolphin** | `dolphin-emu` (legacy) | `Config/Dolphin.ini` and the disc's applicable `GameSettings` INIs, in priority order through its exact revision. A resolved alternate primary-storage GCI path from the installed provider gives **Config** evidence for that game. | **OwnProvider** at bind, or a matching GCI session write. A legacy write with a stable, unchanged watched card gives `WroteElsewhere`. | Slot B, raw cards, RVZ, Wii, and custom GCI paths as sync destinations. |
| **DuckStation** | Hosted card 1. The optional data grant reaches the installed player's configuration when Android allows it. | The host records successful opens, caller UID and package, mode, descriptor detach or close, and temporary writes published by rename. The plugin reads `settings.ini` and `gamesettings/*.ini` for `[MemoryCards]` Directory, Card1Type and card paths. | **HostedOpen** checks the expected card after a read-only open or successful publication. A different read-only card gives a game-specific **MaybeNewSave**. **Config** can prove **WrongFolder**. Silence during a confirmed launch can give a dismissible **MaybeWrongFolder** (§4.2). | Android may block the optional data grant. Unobserved gameplay and ambiguous or shared cards limit what the host can infer. |
| **Eden** | The selected SaveDir and NAND through existing writable storage grants | `config/config.ini` `[Data Storage]` (also `[Data%20Storage]`) and `[UI]` `Paths\…` keys. An empty `save_directory` uses `nand_directory`. A configured path without a grant opens Fix for that folder. `[System] current_user` and NAND `profiles.dat` identify the account (§4.1). | OwnProvider for the default NAND through Eden's provider, Config for resolved custom paths, and SessionWrite for the frozen account. | Prefer `user/save/account/<raw UUID>/<TITLEID>/0` when present. Otherwise use the legacy layout. A missing, corrupt, or conflicting config clears the check and leaves the destination unresolved. The first resolved context after updating fences existing bytes for Backup preservation. |
| **EX ×5** | `EmuEx`, beside-game `<content>.sav/.srm/.brm/.rtc` via `saveBesideGame` | none readable: config is private and binary | Session write. Beside-game diff catches **Same As Content**. Candidate diff catches a custom folder Foldcade also holds. | App Folder (private) and Launch Mode stay a standing setup step. Upstream common bridge. |
| **melonDS** | `saves/nds` and others in `saveFolderPlaces`; beside-game `<rom>.sav` | none readable | Session write. **Beside-game diff catches "Save next to ROM file"** for RomM downloads (Foldcade owns `Android/media/app.foldcade/romm`). | Private-fallback `saves` is unreadable. Upstream `save_uri` extra. |
| **PPSSPP** | Existing `saveFolderPlaces` and held access | Private Memory Stick selection. A copy of `SYSTEM/ppsspp.ini` gives no location proof. | Session write, plus candidate diff | **PinnedLaunch** disabled for every package and version (PR 9) |
| **RetroArch** | Exact `.srm` beside the ROM, under the default core-sorted root, or at the confirmed alternate path. Readable granted local folders and Foldcade's RomM media qualify. | The launched `CONFIGFILE` and applicable core, content-directory, and game overrides determine `savefile_directory`, `sort_savefiles_enable`, `sort_savefiles_by_content_enable`, and `savefiles_in_content_dir`. A proven different path gives `Config` / `UsesOther` for the affected game. | Session write, beside-ROM diff, and a complete config mismatch. A config match alone does not verify game bytes. | Private config, unsupported syntax, archive-member names, and unresolved directory fallbacks leave path checks inconclusive. Unsupported core-specific formats leave the spot unresolved. Save states and companions stay outside the `.srm` slot. |

(*) Each `probePlaces` entry is checked only when Foldcade can already read it, so a guessed place costs nothing when it doesn't exist.

**Two Azahar flavors.** If both `org.azahar_emu.azahar` and `io.github.lime3ds.android` are installed, a candidate log may belong to the other flavor. The probe then lowers candidate signals to `Supports`. The exception is a log whose `Azahar Version:` line matches the resolved package's `versionName`. The same rule applies wherever `packageNames.size > 1` and both are installed.

---

## 3. Launch gate

### 3.1 When the ordinary launch stops

`LaunchSaves.prepare` gains a first step, `protectionGate(route, player)`. It throws `SaveProtectionStop(incident)`, a subclass of `UnresolvedSaveNotPlaced`, so the existing "explicit per-launch opt-out" path carries it. Stop when:

1. An **open incident** of kind `WrongFolder`, `FolderEmptied` or `LostAccess` exists for this emulator folder.
2. `judgeRoots` on a fresh snapshot gives `UsesOther` or `Lost`. This opens the incident, then stops.
3. **A known save can't be placed, for any adapter.** Today only Eden, PPSSPP, Dolphin and EX are guarded (`LaunchSaves.kt:52`). Azahar, melonDS, ARMSX2, aPS3e, RetroArch and DuckStation can resolve no spot and still launch. The check is the same: held or server saves exist for the slot and `spots` is empty. It becomes unconditional. The existing Eden-only remote-check failure rule stays.
4. **A save is missing where it was proven.** The location is `Verified`, a held save exists, and the bound root's markers are gone. That is rule 4 of `judgeRoots`, so it arrives as case 2.

Do **not** stop when:

- The location is only `Bound` (unchecked).
- RomM is unreachable. The hero says when RomM was last reached; the launch goes on.
- The last session changed nothing.
- A `Maybe*` incident is open. Those show a line in the dialog-free hero, not a gate.

### 3.2 What the user sees

The dialog goes on the controls screen, with `holdsLaunch = true`. B cancels. Its kind is `DialogKind.SaveProtection`.

```
TOP (1920×1080) — the game's hero stays; the hero line turns amber
┌────────────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                            │
│   [ Sonic Boom art ]                                                                       │
│                                                                                            │
│   Sonic Boom: Fire & Ice                                                            │
│   ▲ Azahar is saving somewhere else                                       (amber)          │
│                                                                                            │
└────────────────────────────────────────────────────────────────────────────────────────────┘

BOTTOM (1240×1080) — controls
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│   Azahar is saving somewhere else                            │
│                                                              │
│   Azahar now saves in AzaharQA instead of the Azahar         │
│   folder. Your save on RomM is safe, from today, 13:37.      │
│   Fix it first, or this session's progress                   │
│   isn't protected.                                           │
│                                                              │
│   ┌──────────────┐  ┌────────────────┐                       │
│   │   Fix it     │  │  Play anyway   │       Ⓑ Cancel        │
│   └──────────────┘  └────────────────┘                       │
└──────────────────────────────────────────────────────────────┘
```

Copy, as `SaveProtectionCopy`:

| Stop | Title | Body | Buttons |
|---|---|---|---|
| WrongFolder, live root known | `"$emulator is saving somewhere else"` | `"$emulator now saves in ${live.label} instead of the ${bound.label} folder. ${safeOnRomm(receipt)} Fix it first, or this session’s progress isn’t protected."` The folder and the emulator often share a name, so the folder is called one. | **Fix it** · **Play anyway** |
| WrongFolder, live root unknown | same | `"$emulator didn’t use the folder ${bound.label} last time you played. ${safeOnRomm(receipt)} Fix it first, or this session’s progress isn’t protected."` | **Fix it** · **Play anyway** |
| FolderEmptied | `"$emulator’s save folder is empty"` | `"Its saves in ${bound.label} are gone. ${safeOnRomm(receipt)} Put it back before you play."` | **Put it back** · **Play anyway** |
| LostAccess | `"Foldcade can’t see $emulator’s saves"` | `"Allow ${bound.label} again so saves reach RomM. ${safeOnRomm(receipt)}"` | **Allow** · **Play anyway** |
| Known save can't be placed (all adapters) | the existing `"${Copy.saveNotPlaced} $title"` | the existing `Copy.saveUnresolved`, or the precise `SpotIssue` sentence (§6.3) | **Set up saves** · **Play anyway** · **Not now** (as today, with Play without syncing renamed) |

Helpers:

- `safeOnRomm(ms)` gives `"Your save on RomM is safe, from ${saveWhen(ms)}."`, with the one save date format below, lowered mid-sentence: `"…from today, 13:37."`
- `saveWhen` is the one date format on every save surface (Y › Saves list and detail, the Play confirmation, the stop's RomM line, Backups, the Fix page and the Check detail): `Today, 13:37`, `Yesterday, 13:37`, otherwise `Nov 24, 13:37`, with the year only when it isn't this year. The clock follows the device's 12/24-hour setting.
- Only the stop ends with "Fix it first, or this session’s progress isn’t protected." R1 and every other surface end the same body with "Fix it before you play."
- When no receipt is known but RomM has versions, it gives `"Your saves on RomM are safe."`.
- When RomM has nothing for the game, the sentence is left out. The dialog never claims a copy it hasn't seen. A receipt lookup updates only the same still-open stop, preserving its focus and launch hold; it cannot reopen a cancelled stop or update a later stop for the same folder incident.

**Play anyway.** The button reads **Play anyway** everywhere the opt-out is about protection. Each body above ends by saying what it costs, so the button can stay short.

- It starts this launch only, with `syncing = false` (existing).
- It records `unprotectedSession = true` in the pending return.
- On return, if the live root is readable, its copy is captured and kept as a Backup (§4.1).
- The incident stays open.

**Fix it** opens the Fix saves page (§5, §6.4) on this incident, with the launch held. A verified fix goes on to start the game.

**False alarms.** Only rules 1–4 above stop a launch. Each rests on a positive fact: a fresh signal naming another root, a grant that is gone, markers that are gone, or a save that exists and can't be placed. "Nothing changed" never stops one.

---

## 4. After a session

### 4.1 Read-back

`reconcileLocked` resolves spots and publishes its session verdict before a sync result can appear. It stages confirmed RetroArch alternate saves and handles a latched Backup-only return before regression inspection or slot reconciliation. The normal bound-copy handoff remains `changedSince` → `reconcile` → `noteHandedOver`. Other optional live-copy capture runs after that handoff. The evidence steps are:

1. Take an `after` snapshot, then run `judgeSession(before, after, sessionMillis)`.
2. `Wrote(bound)` stores `Verified(SessionWrite, "${game.label} saved")`, and any `Maybe*` incident for this folder closes.
3. `WroteElsewhere(live)` or `Signalled(UsesOther)` opens a `WrongFolder` incident for this game. Then:
   - After the normal bound-copy handoff, capture the live root's copy: `readSpot` against the live root goes into `save-handover`.
   - Call `keepAsBackups`, then record the ids in `incident.kept`. If RomM is unreachable, they go in `incident.staged`.
   - The bound root’s bytes still go through the normal handoff. If a changed return has a verified prior save, the open folder incident can quarantine the changed copy first (§4.3).
4. `SilentStart` opens `WrongFolder`, only for approved signals marked `perStart` on an actual Foldcade launch (§4.2).
5. **The profile is frozen (Eden).** The pending return stores the profile UUID and effective save and NAND paths selected at prepare. A different UUID opens `MaybeNewSave` and the return stages only the original account's save as an unslotted Backup. A detected account or location change fences the newly selected folder before later save operations. A verified first run with an absent profile database can discover Eden's first account on return when the paths match.
6. **Unprotected sessions.** With an open `WrongFolder` and a readable live root, the live copy is captured and kept as a Backup on return. Eden uses its frozen account for return capture. When Eden's configured folder becomes accessible later, the next save operation keeps its local bytes as a Backup before placing a head.

### 4.2 Negative evidence (`perStart`)

**Decision, 24 November 2026, after Thor QA run 2 (`b37fa512`): enable Azahar's per-start evidence only for game launches sent by Foldcade. Keep the emulator-grant rule off.** The report conservatively recommended leaving both rules disabled; the product decision uses its measured cold/warm behaviour and explicitly excludes resume.

The bound root's readable `log/azahar_log.txt` must report `perStart = true` both before launch and on return. If the bound save did not change, the actual launched session lasted at least 20 s, and the log's known mtime is exactly unchanged and no later than the pre-launch baseline, open **WrongFolder**. The live root can be unknown. This gates the next ordinary launch and leads to guided Fix. A positive bound-save write wins first. A short session, a resume, an unknown or moved mtime, a capped log or an unreadable log gives no negative verdict. The host persists whether the return came from a fresh Foldcade launch, plus its actual start and observed return times, so time spent preparing files, waiting for the save flow or uploading does not count. The observed return time survives a retry, and a resume cannot reuse an earlier launch's negative evidence.

Azahar's [upstream FileBackend](https://github.com/azahar-emu/azahar/blob/a5c3d4f8eb34493f37479158128de134c3e700a9/src/common/logging/backend.cpp#L158) stops ordinary writes after 100 MiB until the process restarts. A one-byte read at offset `100 MiB - 1` disables `perStart` when the file has reached that boundary. The log can still give fresh positive evidence; a stationary capped log cannot open a launch alarm. No full-log read or allocation is needed.

QA measured these local times and file sizes:

| Path | Before mtime / bytes | After mtime / bytes | Result |
|---|---|---|---|
| Cold launch | 10:41:31.079 / 20,075 | 13:14:27.923 / 20,260 | New backend and directory line. |
| Warm launch, same PID after Close Game | 13:18:53.547 / 23,215 | 13:21:08.875 / 41,829 | Appended; directory line still names the bound root. |
| Controlled Home → A resume | 13:37:57.475 / 19,635 | 13:39:02.695 / 20,714 | Buffered/ordinary traffic, no new directory line or RunCitra start; excluded from this rule. |
| Cold start in custom `AzaharQA` | No log | 13:28:32.115 / 2,693 | New log names `primary:AzaharQA`. |

The resume also showed a Loading overlay for at least 51 s and “Missing game data in intent extras”. That is a separate resume issue, not a reason to treat a resume as a new launch. Azahar retained both the old and new tree grants after switching, so grant presence cannot identify its live root. `SAVE_NEGATIVE_EVIDENCE_ENABLED` stays false for the generic/grant rule; the Azahar signal's `perStart` is the explicit per-probe opt-in.

With all-files access, session return searches primary storage for the optional probe `signatureFile` only when the bound signature did not advance past baseline or did not prove that it names the bound folder. A fresh bound log naming that folder skips the walk. Only roots at depths 0–2 are considered, with an 8,192-entry budget and at most four discovered roots. The signature is read within the probe's bound (Azahar: first 32 KiB); its signal must prove and name that same root, with mtime newer than the session baseline. Only directories, checked without following links, reach canonical path validation; ordinary files are skipped. Links and Android app-data roots are excluded. A discovered live root names the incident and opens Fix's picker there. Without that access, the negative incident still opens with an unknown live root and the user chooses the folder Azahar uses.

A readable live game's copy is staged and kept through the independent Backup queue, never reconciled as a slot head. Identical bytes already present in an unslotted copy reuse that verified id instead of creating another Backup. Slot versions can be pruned, so a slot match must still be archived outside slot retention. The return publishes its incident before Backup uploads, so the hero cannot retain **Saved to RomM** while the copy is being kept.

Thor QA observed four DuckStation 12303 boots with a read-only open of an existing card. A fresh Foldcade launch can raise **MaybeWrongFolder** when the expected card already existed at launch, the full hosted journal covers the session, and DuckStation had zero hosted opens. Foldcade requires the selected RomM ROM's open from the same caller and at least 20 seconds in DuckStation's emulation activity after that open, observed through Usage Access. The check skips resumes, missing game evidence, incomplete journals, and readable settings in which card 1 can be disabled or nonpersistent. A first boot may create a card in memory without a file open. [DuckStation card load](https://github.com/stenzek/duckstation/blob/0403d3c45a33c7a7e2b3d5c27db364e6c2e08da0/src/core/memory_card.cpp)

The notice gives DuckStation's Memory Cards steps and **It’s fine** dismisses it with the folder still unchecked. Because a disabled card can look the same when the config is unreadable, this notice does not gate play.

### 4.3 Regression quarantine (PR 2)

`suspectRegression` in `language` compares a returned save with the device’s last verified RomM copy. The host reads that baseline from the locally held slot only when its hash still matches the synced ledger. This works offline. An identical copy, including a ZIP with the same member contents, needs no quarantine.

- For an established save, new markers in the same root during play, a changed install stamp or Eden profile, or an open wrong-folder or empty-folder incident are reasons to ask. Hosted saves such as DuckStation's cards do not use the marker rule. A first save with no verified baseline goes through ordinary sync.
- A save declared as folders is suspect if half its file members or more disappeared. Directory entries and compression are irrelevant. A single-file save above 1 KiB is suspect if it shrank to a quarter of its previous size or less.
- The return inspects the bytes already captured by `changedSince`. Ordinary RomM reconciliation reads the same staged snapshot, including for single files. A failed inspection without other evidence leaves ordinary sync available. Context alone can quarantine an established save even when its shape is unavailable. A suspect copy is staged in the Backup queue before the handover mark advances and the return ends. It opens a game-specific `MaybeNewSave`. If RomM is offline, the choice waits for verified copies. Later legitimate progress is kept as a Backup and syncs normally. The choice then offers the new head alongside the original suspect copy. Resuming a running game with a blocking incident latches a Backup-only return and preserves any existing two-saves choice.
- **Choose save** opens the existing two-saves card. The player can keep the head, play the Backup, or come back later. The incident never stops an ordinary launch. The structural rules are suspicion only, so rare legitimate changes can bring up a choice.

### 4.4 Across restarts

- Incidents and location checks live in `files/save-protection/*` (§1.4).
- The session baseline lives in the pending return, so a process death between launch and return still judges the session on the next return.
- Staged copies live in `save-handover`. `incident.staged` names them. Each sidecar records the game’s library and the RomM origin when known. The next connection retries RomM copies as Backups. Local-folder copies stay on this device.

---

## 5. Auto-repair, RomM-first

**Fix it** starts automatic repair when Foldcade knows the game, has a proven live root, and can read and write both roots using held grants or all-files access. The original and live bindings, local game URI, RomM origin, and each repair phase survive restart. Repair shares the save-flow lock with launches, read-backs, and save choices.

1. **Wait for the game to close.** Home can leave the emulator holding a paused game. Foldcade waits on its launch records, including emulator launches from Apps or **Open emulator** and holds from an earlier process. A reboot releases holds recorded before startup. On Android 15 or later, Foldcade can proceed after receiving Android’s package restart broadcast if the package is still marked stopped. It checks the stopped state again before binding and placing a save. A native quit can leave a launch record behind, and usage events can report a backgrounded activity that still holds the game. **Confirm close** opens **Already quit?**, focused on **Not now**. On **Continue**, Foldcade rechecks the hold. If it can clear the task, it uses the usual Close path and waits for shutdown and save read-back. When Foldcade knows the game is still running and cannot close it, the waiting card sends the player back to quit in the emulator. An explicit confirmation can release an unobservable hold and survives a restart.
2. **Keep every distinct game copy.** Read its save from the bound root, live root, and a reachable beside-game file if the adapter declares one. Stage them in the existing Backup queue. Equal contents use one unslotted Backup, even when the same save is a slot version. Verify the unslotted IDs and RomM hashes. A new upload must also match its byte size. An equivalent existing ZIP can have a different container size. While RomM is unreachable, the repair stays at **Waiting for RomM** with the copies durably staged.
3. **Bind to the proven root.** Check the working files with two reads two seconds apart. A change sends repair back to waiting for the game to close. Write the rebound fence before persisting the new binding, then bind every player sharing that folder. If access or the game’s save path cannot be resolved, offer the guided Fix. The picker checks the proven storage identity when one is known.
4. **Choose the head.** Use RomM’s current slot head when the live file is absent, equivalent to the head, unchanged from the last verified sync, or structurally suspect. A different live history opens the two-saves card. All local copies are already unslotted Backups. The current RomM head is focused first.
5. **Place and verify.** The save-selection route checks the server version, format, working copies, and emulator hold again before committing. Restoring the current head creates no new slot version. Choosing a different history keeps the previous head as an unslotted Backup first. Once the live Backup verifies, placement records the raw hash of the captured local spot so the usual overwrite guard allows that exact copy to be replaced. Placement sets the fresh-load marker and checks the bytes written using the ordinary save read-back and rollback. An interrupted placement retains the chosen ID. A changed head or new local bytes cause repair to read the histories again before offering a choice.
6. **Done.** Mark the proven live root verified and close the incident. R1 posts **Fixed: $game** and **Your RomM save is back in $folder.** A still-held launch of the repaired game can continue. The Done page can also open **Y › Saves**. The guided path keeps PR 1’s folder-only recovery and opens **Y › Saves** after binding.

The rebound fence protects other games in the folder as they are opened. Repair leaves both folders in place and never changes the emulator’s settings. An upload still in progress keeps repair waiting for RomM. A missing repair destination, a missing head, or an unresolved sync conflict goes to guided recovery.

For a quarantined Eden save, automatic placement resolves the configured SaveDir and the captured account again before choosing a destination, committing the selected head, and writing the save. It observes that Eden context before marking the game handled. Missing access or a changed account, SaveDir or NAND sends the repair to the guided Fix. Account and folder changes detected on return keep the frozen session's progress as a Backup and use the same guided path.

---

## 6. Guided repair and recovery: every surface

The voice follows `SaveSetupCopy`: one or two words for a control, one short sentence for a step, the emulator's own menu path in `›` form, and no exclamation marks. New strings live in `object SaveProtectionCopy` in `SaveProtection.kt`.

### 6.1 Game tile and hero line

**Tile badge.** The existing amber badge, `needsAttention`, appears for `Problem`, `NotSyncing`, `Waiting`, and `DeviceOnly` when `playedHere`. Nothing quiet or clear gets a badge.

**Hero line**, from `saveStatusLine(status, now)`. It is never null:

| Status | Line | Tone |
|---|---|---|
| Problem WrongFolder | `"$emulator is saving somewhere else"` | amber |
| Problem MaybeWrongFolder | `"$emulator may be saving somewhere else"` | amber |
| Problem FolderEmptied | `"$emulator’s save folder is empty"` | amber |
| Problem LostAccess | `"Foldcade can’t see $emulator’s saves"` | amber |
| Problem MaybeNewSave | `"Two saves · choose one"` | amber |
| NotSyncing | `Copy.savesNotSyncing`, "Saves not syncing" (existing) | amber |
| NotSyncing ServerTooOld | `"Update RomM to 5.4 to sync saves"` | amber |
| NotSyncing SignedOut | `Copy.rommSignInNoticeTitle`, "RomM: sign in to sync saves" | amber |
| Waiting | `Copy.savesWaiting`, "Saves waiting to upload" | amber |
| DeviceOnly NotOnRomm | `"Saves only on this device"` | amber |
| DeviceOnly PlayerCantSync | `"$emulator saves only on this device"` | amber |
| Unconfirmed FolderNotChecked | `"Save folder not checked yet"` | quiet |
| Unconfirmed GameNotChecked | `"Save not checked yet"` | quiet |
| Unconfirmed RommCopyUnconfirmed | `"Save checked · No RomM copy confirmed"` | quiet |
| Unconfirmed RommUnreachable | `"Can’t reach RomM · last saved ${age}"`, or `"Can’t reach RomM"` | quiet |
| Unconfirmed PartnerCloud | `"Saves in ${cloud ?: partner} · not confirmed"`, e.g. "Saves in Steam Cloud · not confirmed" | quiet |
| Unconfirmed HostComputer | `"Saves on your computer"` | quiet |
| Unconfirmed AppKeepsSaves | `"This app keeps its own saves"` | quiet |
| Unconfirmed NoPlayer | `"No emulator for these saves"` | quiet |
| NothingYet | `"No saves yet"` | quiet |
| Protected | `"Saved to RomM ${age}"`: `Saved to RomM today`, `yesterday`, `3 days ago`; plain `"Saved to RomM"` with no time | quiet |

`saveHealthLeads`/`heroActivityLine` keep their rules, with "has an attention line" in place of "is a warning". Downloads and sync progress still show beside a standing fault. `Copy.savesDeviceOnly` changes from "Saves stay on this device" to "Saves only on this device". The new wording says it is a risk, not a feature.

### 6.2 Settings › Emulators and Set up saves

- **The tile check** (the check beside set rows, and the `Syncing` chip on Set up saves) appears only while the folder's location is `Verified`. On Set up saves, a bound but unverified folder reads **Folder set**, with no check and no amber. **Folder set** is Set up saves' word only.
- **The emulator page's Save folder row** names the bound folder, checked or not (e.g. `MD.emu`); only the check beside it says it was proven. During an incident it names the bound folder (`Azahar`) in amber and opens the Fix saves page. Hosted folders and memory cards read **Syncing** only once checked, **Set** before.
- **The emulator page** gets one new row under Save folder, `Row.SaveCheck(playerId)`, shown only once a folder is bound. Label **Check**. Values:
  - `"Checked ${relative}"`, e.g. "Checked today"
  - `"Not checked yet"`
  - during an incident, in amber, what the evidence found, said once: `"Saving in ${live.label}"`, or `"Not saving here"` when the live folder is unknown. `"May save elsewhere"`, `"Folder empty"`, `"No access"`, `"Two saves"` for the other kinds.
  
  A on the row shows its detail line on the top screen. That line is the evidence, in words, with the one date format:
  - Positive log evidence: `"Azahar used this folder today, 13:27."` (Config, hosted and pinned evidence read the same way.)
  - A session write: `"Azahar saved here today, 13:27."`
  - `"Azahar’s own folder."`
  - `"Play a game and save in it to check."`
  - With an incident, the row opens the Fix saves page instead. A silent-start incident's evidence reads `"$emulator didn’t use this folder last time you played."`.
- **Set up saves.** An incident makes its emulator's tile need you again. The step is `SetupStep.Fix(incidentId)`, the title is `"Fix $emulator’s folder"`, and the body is `"Your save is safe on RomM."`. `savesDue` counts incidents for the **3 to go** chip and Set up saves. R1's "Saves not syncing · N emulators need setup" notice (`savesDueNotice`) leaves them out, because each incident has its own R1 notice. `SaveSetupCopy.endTitle` can't say **All set** while an incident is open.
  - `SetupStatus.Syncing` gains a sibling, `FolderSet`, label **Folder set**. **All set** requires that every due emulator is `Syncing`, meaning verified. Until then the end reads `"Folders set. Each is checked the first time a game saves."`.

### 6.3 Why a game's save has no place

The `SpotIssue` sentences, shown on Y › Saves and in the gate:

| Issue | Sentence |
|---|---|
| NoTitleId | `"This game file doesn’t say which 3DS game it is. A .cci syncs; a .zcci doesn’t."` (Azahar) / `"Foldcade can’t read this disc’s game ID."` (others) |
| UnsupportedImage | `"This file type doesn’t sync yet."` + format, e.g. `"RVZ doesn’t sync yet. ISO and GCM do."` |
| NoProfile | `Copy.edenSaveNeedsUser` (existing) |
| CardNotFolder | the existing card steps (ARMSX2/Dolphin) |
| ArchiveName | `SavePickerCopy.incompatible` (existing) |
| Unreadable | `"Foldcade couldn’t read the game file. Try again once it’s downloaded."` |

### 6.4 The Fix saves page

`SaveFixPage` in `language`, a sibling of `SaveSetupPage`, opened by **Fix it**, the R1 notice, the Settings row, or Y › Saves. Nothing about it is stored: each step is read from `incident.repair` and the model, as Set up saves does.

```
TOP (1920×1080)
┌────────────────────────────────────────────────────────────────────────────────────────────┐
│  Fix Azahar’s folder                                                                       │
│  Azahar is saving somewhere else                                                  (amber)  │
│                                                                                            │
│  Your save is safe on RomM.                     (only when a RomM copy is known)           │
│  Picross 3D: Round 2 · Today, 13:37                                                        │
│                                                                                            │
│  ✓  Keep every copy on RomM        2 kept                                                  │
│  ▸  Use the folder Azahar uses                                                             │
│  ○  Choose which save to play                                                              │
│                                                                                            │
│  Azahar now saves in       AzaharQA                                                        │
│  Foldcade was watching     Azahar                                                          │
└────────────────────────────────────────────────────────────────────────────────────────────┘

BOTTOM (1240×1080) — one card in the dialog look: the step to do now and its pills
┌──────────────────────────────────────────────────────────────┐
│  Fix Azahar’s folder                                         │
│  Choose AzaharQA, then tap Use this folder › Allow.          │
│                                                              │
│  ( Choose folder )                            Ⓑ Later        │
└──────────────────────────────────────────────────────────────┘
```

The bottom card is `DialogFrame`, the stop dialog's look: pills with the focus ring, then the key hints at the right. B is always named **Later**. No file paths appear on either screen.

Bottom card, by state (`saveFixActions`, `saveFixCard`):

| State | Body | Buttons |
|---|---|---|
| Keeping | **Keeping your saves on RomM.** | |
| Waiting for RomM | **Waiting for RomM. Foldcade will try again when it can connect.** | **Try again** |
| Waiting for Close | **Already quit $game in $emulator? Confirm to continue. If it’s still open, save and quit first.** | **Confirm close** · **Open $emulator** |
| Waiting for a game Foldcade cannot close | **Quit $game in $emulator, then come back.** | **Open $emulator** |
| Switching | **Switching Foldcade to $folder.** | |
| Choosing | **Both are kept on RomM.** | **Choose save** |
| Reset choice needs guided recovery | **Open Y › Saves to choose which progress to keep.** | **Open Saves** |
| Placing | **Putting your save back.** | |
| Automatically fixed | **Your RomM save is back in $folder.** | **Choose save** or **Done** |
| Folder known: `live`, or `bound` for LostAccess (`saveFixTarget`) | `"Choose ${folder}, then tap Use this folder › Allow."` | **Choose folder**, which opens the picker in that folder |
| Folder unknown | the emulator's `locationSteps` (§6.5) as numbered steps, then `"Then choose the same folder here."` (`"Find the folder $emulator saves in, then choose it here."` without steps) | **Open $emulator** · **Choose folder** |
| Done, online, with a game | `"Folder set. Choose your RomM save in Y › Saves."` | **Choose save**, which opens that game's Y › Saves |
| Done, offline | `"Folder set. Join Wi-Fi, then choose your save in Y › Saves."` | **Done**, which only closes: Saves would be a dead end |
| Done, no game (opened from Settings or R1) | `"Folder set. Choose your RomM save in Y › Saves."` | **Done** |

The in-emulator folder steps show only in guided Fix when the live folder is unknown. Automatic Fix shows **Put your save back** as the final checklist item.

Top screen:

- Title `"Fix $emulator’s folder"`, then the incident's title in amber. Once done, the amber line is replaced by `"Foldcade now watches ${folder}."` in the normal colour.
- `"Your save is safe on RomM."` and `"$game · ${saveWhen}"`, only once the host has read a receipt for the held launch's or open Saves page's game. With versions but no receipt: `"Your saves on RomM are safe."` and the game. Nothing otherwise.
- The checklist (`saveFixSteps`):

| Step | Title | Value |
|---|---|---|
| Keep on RomM | `"Keep every copy on RomM"` or `"Keep copies on RomM"` when the page also shows other copies | ✓ `"$n kept"` for verified Backups; ▸ `"Waiting for RomM"` for queued RomM copies |
| Keep on this device | `"Keep a copy on this device"` | ✓ `"$n kept"` for staged local-folder copies |
| Folder | `"Use the folder $emulator uses"` (known) / `"Choose $emulator’s folder"` (unknown) | ▸ until bound, then ✓ |
| Choose | `"Choose which save to play"` | ○ until bound, then ▸ |

- Two real columns: `"$emulator now saves in"` | `live.label`, and `"Foldcade was watching"` | `bound.label`. With no live folder, the evidence in words follows, e.g. `"Azahar didn’t use this folder last time you played."`
- When Settings opens an emulator-wide incident, the checklist reads each staged copy’s sidecar before showing copy status. Local-folder copies count as kept here even when RomM is set up. Copies whose destination cannot be read show **Copies need checking**.

Controls: B is **Later**. It closes the page, and the incident and gate stay. The bottom face button does nothing here, because there is no skip: a fix either happens or waits. For **MaybeWrongFolder**, Y opens **It's fine**, with the confirmation `"Keep watching ${bound.label}?"` / `"Only if $emulator saves there. Foldcade checks again next time."`.

### 6.5 The in-emulator steps

These are written as `locationSteps`, one numbered line each, in the voice of `DuckStationEntry.kt:102`. The menu paths are the audit's verified ones. Where a surrounding menu varies by version, only the verified label is named.

- **Azahar:** `1. In Azahar, choose Select User Folder.` / `2. Choose the folder that should keep your saves, then Use this folder › Allow.` (AZ `strings.xml:106`.)
- **melonDS:** `1. Settings › Save Files › Save next to ROM file: Off.` / `2. Save file directory › choose your saves folder.`
- **ARMSX2:** `1. ☰ › Setup / Change Folders › App Data Folder.` / `2. Choose the folder that holds bios and memcards.`
- **PPSSPP:** `1. Settings › System › Memory Stick Folder.` / `2. Create or Choose a PSP folder › choose your PSP folder.`
- **RetroArch:** `1. Settings › Directory › Save Files › choose RetroArch/saves.` / `2. Settings › Saving: Sort Saves into Folders by Core On; by Content Directory Off; Write Saves to Content Directory Off.`
- **EX family:** `1. Options › File Paths › Saves › Select Folder.` / `2. Options › System › Launch Mode › Main Slot (No State).`
- **Dolphin:** `1. Settings › Config › GameCube › GameCube Slot A Device › GCI Folder.` / `2. Check the game’s Game Settings for its own card.` (= `Copy.dolphinFolderCard` sense)
- **Eden:** `1. Settings › Advanced settings › Custom Paths › Save Data Directory.` / `2. Settings › Profile Manager: check the user you play as.`
- **DuckStation:** the existing `hostedFolderSteps`.
- **aPS3e:** none. Its root is fixed, so the Foldcade picker alone fixes it.

### 6.6 Two saves, and Y › Saves

- **The two-saves card** (the existing `SaveConflictPrompt`) gets a variant for `MaybeNewSave` and repair `Choosing`:
  - Title `"Which save do you want to play?"`. Body `"Both are kept on RomM."`
  - Each copy shows its existing source and time, plus a `"Where"` line, e.g. `"From the folder Azahar"`, `"From RomM, 22 Nov"`.
  - The suspect copy carries **May be a new game**. The card identifies **This Thor** and **RomM**, and shows **From the folder $folder** with the working copy when its folder is known.
  - Focus starts on the RomM head. A chooses and plays the focused copy once placement verifies. For a suspected reset, **Leave as is** keeps both and leaves RomM’s head where it was. Wrong-folder repair offers the two saves and B to decide later.
- **Y › Saves:**
  - **The chip** shows the status line (§6.1).
  - **With an incident**, a banner row leads the list: `"Your save is safe on RomM"` / `"$emulator is saving somewhere else. Fix it, then choose a save."` · A opens the Fix saves page.
  - **Playing a save** while a `WrongFolder` is open runs Fix first. The confirmation adds `"Foldcade fixes $emulator’s folder first."`.
  - **Backups** made by `keepAsBackups` are named by their time like every dated row (`Today, 14:24`), with a **Backup** chip where **Playing** sits. Their row shows the folder's name beside a folder mark. The detail's headline is the time; under it go the **Backup** chip and `"From the folder AzaharQA"`. No surface says "No slot". Playing a Backup confirms `"From the folder AzaharQA, today, 14:24."`; a save from this device, `"From Azahar on this Thor, today, 13:37."`; another device keeps its name. Archives already carry a unique `foldcade-kept-<uuid>` name. The folder label goes in a sidecar map in the RomM plugin, keyed by server id. This is RomM-plugin state, not a constructor or method change: the existing `ServerSave.source` carries the folder label for library-owned Backups, while `serverSaveOrigins` keeps device provenance separate. Only a verified unslotted copy satisfies preservation and drains the queue; a matching head or older slot version still needs an unslotted Backup. ZIP equality uses the member-aware server hash rather than compressed byte size, and reusing a Backup keeps its first recorded folder label.
  - **With nothing on RomM** and an incident, the list's place shows `"No saves on RomM yet. Your saves on this device are kept."` and **Fix it**.

### 6.7 R1 notices

New class `SaveIncidentNotices` in `SaveNotices.kt`. It shows one notice per incident:

| Incident | Title | Body | A |
|---|---|---|---|
| WrongFolder, live root known | `"$emulator is saving somewhere else"` | `"Saving in ${live.label}. Fix it before you play."` | Fix saves page |
| WrongFolder, live root unknown | same | `"Not saving in ${bound.label}. Fix it before you play."` | Fix saves page |
| FolderEmptied | `"$emulator’s save folder is empty"` | `"${bound.label} is empty. Fix it before you play."` | Fix saves page |
| LostAccess | `"Foldcade can’t see $emulator’s saves"` | `"Can’t see ${bound.label}. Fix it before you play."` | picker |
| MaybeWrongFolder | `"$emulator may be saving somewhere else"` | `"May be saving elsewhere. Check before you play."` | Fix saves page |
| DuckStation card mismatch | `"DuckStation used a different memory card"` | `"Using $cardName. Check before you play."` with a shortened name when needed | Memory card detail |
| MaybeNewSave | `"Two saves for $game"` | `"Two saves. Choose which to play."` or `"Keeping both saves. Waiting for RomM."` | two-saves card or waiting step |
| Repair Done | — | No incident notice | — |

R1 uses its own short `noticeBody` helper: the problem first, then the action, about 50 characters with ordinary folder names so both fit its one-line renderer. The stop dialog, hero detail and Fix page keep the longer copy. A folder-wide notice makes no receipt or server-version claim, because it does not know every game has a RomM copy. Done incidents do not keep an R1 alarm.

Dismiss works "for now", as `SaveSetupNotices` does. The notice comes back when any game on that emulator is launched, and at every cold start, until the incident closes. If several incidents are open, they merge into `"$n emulators need a fix"`, using the merged-notice pattern, with `settingsCategory = Players`.

---

## 7. Device-only saves and players that keep their own

| Case | Status / line | Badge | Route off the device |
|---|---|---|---|
| Local-folder game, no RomM match | DeviceOnly NotOnRomm · "Saves only on this device" | if played | Y › Saves shows `"Add this game to RomM to keep its saves there. Once RomM has the same file name for this system, Foldcade syncs it."` (`SameGameRoute` already folds and syncs on the next launch, `romm.md:136`). There is no save-only upload to a missing RomM record (audit). |
| Local-folder game also on RomM | the normal model: the save owner is RomM | — | Already supported. The hero says "Saved to RomM", as for any RomM game. |
| Launch-only emulator (My Boy!, Flycast, …) | DeviceOnly PlayerCantSync · "Flycast saves only on this device" | if played | Y › Saves shows `"Play with $supported to sync these saves."` when a supported player exists for the platform, using the Open with choice. Otherwise `"Foldcade can’t sync $emulator yet."` |
| Supported emulator, unsupported format/identity | NotSyncing with `SpotIssue` (§6.3) | yes | The exact reason, plus the format that does work |
| Missing/revoked folder or grant | NotSyncing / LostAccess incident | yes | Set up saves / Fix saves |
| Play without syncing (setup missing) | It stays NotSyncing. The hero adds nothing new. | yes | On return, if spots are readable, the bytes are captured and queued as today (`romm.md:110` already allows read-back). The opt-out is never recorded as a sync. |
| Signed out | NotSyncing SignedOut | yes | the existing sign-in notice |
| Server < 5.4 | NotSyncing ServerTooOld, from `saveServiceState()` (the plugin already rejects at `RommClient.kt:574`) | yes | `"Update RomM to 5.4 to sync saves"`. This is shown even before any save exists. |
| RomM offline | Unconfirmed RommUnreachable · "Can’t reach RomM · saved there 3 days ago" | no (Waiting badges once bytes are held) | Automatic retry, as today |
| DroidDeck | Unconfirmed PartnerCloud · "Saves in Steam Cloud · not confirmed" | no | Y › Saves for non-RomM games shows `"Steam keeps these saves when Steam Cloud is on for the game. Foldcade can’t check it yet."` |
| GameNative | "Saves in GameNative · not confirmed" | no | `"GameNative keeps these saves. Foldcade can’t check its cloud yet."` |
| Android app | "This app keeps its own saves" | no | — |
| Moonlight | "Saves on your computer" | no | — |
| No installed player | Unconfirmed NoPlayer, or Waiting if bytes are held | Waiting only | Pending uploads keep going without the player (`syncServerSaves` already allows it) |

`ShellController.saveHealthOf` no longer returns `Fine` for a missing route or player (`ShellController.kt:772–774`). It returns `Unconfirmed(NoPlayer)`, or `Waiting`. `gameSaveHealth` builds `SaveFacts` instead of calling `deriveSaveHealth`.

Partner receipts are deferred (§8.3). When one exists later, `OffDeviceFact.PartnerUnknown` gets a receipt sibling, and nothing else changes.

---

## 8. Delivery plan

All PRs:

- Run `./gradlew testDebugUnitTest`.
- Leave `versionName` alone unless the orchestrator wants a stable tag.
- Update `docs/integrations/romm.md` "Save sync" and `docs/persistence.md` as they change behaviour.

### 8.1 Order

```
PR1 ──┬── PR2 (repair + quarantine)
      ├── PR3 DuckStation ─┐
      ├── PR4 Eden         │
      ├── PR5 RetroArch    ├── parallel once PR1 lands
      ├── PR6 melonDS + EX │
      ├── PR7 ARMSX2       │
      ├── PR8 aPS3e+Dolphin┘
      ├── PR9 PPSSPP pin (after PR2: needs Fix to rebind)
      └── PR10 device-only routes
PR11 silent-session caution: only after QA data from PR1–PR6
```

### 8.2 PRs

**PR 1: Truthful save status, Azahar folder check, and the launch gate.** This is the largest safety gain, and it stands on its own.

- **Scope:**
  - Facts, status and copy (§1, §6.1–6.3).
  - API minor 26, including `SaveLocationProbe`, `modifiedAtMillis`, `saveReceipts` and `saveServiceState`.
  - Generic session-write and candidate-diff evidence for every `SaveFolderHolder`.
  - The `playerHolds` recheck for chosen folders.
  - The Azahar probe.
  - The gate (§3), including "known save can't be placed" for every adapter.
  - Incidents, stored and in R1.
  - Live-copy capture into `save-handover` plus `incident.staged`.
  - A guided-only Fix page: the Binding (known/guided) and Done steps, plus Y › Saves recovery. Auto-repair comes in PR 2.
  - The Settings Check row, the **Folder set** state, and non-emulator lines.
  - Ledger `synced_at`.
- **Files:**
  - `language/.../SaveProtection.kt` (new; replaces `SaveHealth.kt`), `SaveEvidence.kt` (new), `SaveSetup.kt`, `Settings.kt`, `Picker.kt` (dialog, row, effects), `Theme.kt` (`Copy.savesDeviceOnly`), `SavePicker.kt` (chip/banner)
  - `api/.../SaveProbe.kt` (new), `Player.kt` (`SaveFolderAccess.modifiedAtMillis`), `Library.kt`, `ApiVersion.kt`
  - `app/.../SaveProtectionStore.kt`, `SaveProbes.kt`, `SaveIncidentNotices` in `SaveNotices.kt`; `GameSaveHealth.kt`, `LaunchSaves.kt`, `HomeActivity.kt` (`reconcileLocked`, `LibraryReturn.baseline`), `ShellController.kt` (`saveHealthOf`), `FoldcadeApp.kt` (resume probe), `AndroidSaveFolderFinder.kt` (`modifiedAtMillis` via `COLUMN_LAST_MODIFIED`), `PlayerSaves.kt` (`folderAccess` passes mtime)
  - `plugins/azahar/.../AzaharEntry.kt` (probe + steps)
  - `romm/.../SaveLedger.kt` (`synced_at`), `plugins/romm/.../RommLibrary.kt` (`saveReceipts`, `saveServiceState`)
- **Tests:**
  - `SaveProtectionTest`: the full table, plus "no Protected without Verified + OnRomm".
  - `SaveEvidenceTest`: every `judgeRoots`/`judgeSession` rule; the stale old-root log (audit table row 1); restored mtimes; two-flavor downgrade; all-files alias identity; a short unchanged session gives `NoNews`.
  - `AzaharProbeTest` (in `plugins/azahar`): log parsing from a fixture copied from the Thor's `azahar_log.txt`, read-only. Covers a URI with `%2F`/`%3A`, a missing line, `.old.txt` ignored, and a missing file.
  - `LaunchSavesTest`: each stop, and every adapter unguarded before now.
  - `SaveProtectionStoreTest`: the JSON round trip, a corrupt file moved aside (the `SaveLedger` pattern), and an install-stamp change dropping a check.
  - `SaveLedgerTest`: `synced_at` written, old ledger read with a null time.
  - Update `GameSaveHealthTest`/`ShellSaveHealthTest` for non-emulator `Unconfirmed`.
  - No Robolectric, except one `AndroidSaveFolderFinder` mtime test if the cursor column needs it.
- **Thor QA** (Sol). Screenshots go outside the repo.
  1. Reproduce the incident on a throwaway game. Bind `saves/Azahar`. In Azahar, choose Select User Folder → a new empty `/sdcard/AzaharQA`. Back on Home, expect the R1 notice, the amber badge on the game, Settings **Wrong folder**, and the Check row detail naming both logs. Do it with all-files access on, and again with only the `saves/Azahar` grant. With only that grant, expect the grant-check path or **Folder set**. Record which.
  2. A → the gate. **Play anyway** → play and save → return. Expect the incident open, and the live copy staged or kept.
  3. **Fix it** (guided) → the picker on `/sdcard/AzaharQA` → Done. Then Y › Saves → play the RomM version → the in-game save is correct.
  4. Measure Azahar log mtime movement on a cold launch, a warm launch, and Home→resume. Find out whether Azahar releases its old tree grant after Select User Folder. Both answers set `perStart` and the rule 1 strength.
  5. No false alarms: open and close a game with no save; resume; change Wi-Fi off and on; reboot. Expect no gate and no notice.
  6. For every installed emulator, record the Settings value after one save in a game. Expect **Checked today**, and nowhere a check that didn't come from a save.
- **False-alarm risk:**
  - Low for the gate: positive facts only.
  - Medium for rule 1, the emulator's grant: if QA shows Azahar keeps old grants, `emulatorHolds == true` on both gives no verdict and costs nothing.
  - Users who are set up today will see their checks turn into **Folder set** until each emulator saves once. That is intended, and the release notes say so.

**PR 2: RomM-first auto-repair and regression quarantine.**

- **Scope:**
  - `SaveRepair.kt` steps 1–6 (§5).
  - The full Fix page.
  - Reuse PR 1’s `keepAsBackups`, verification, and folder-label sidecar. Verify the head again at placement.
  - `suspectRegression` and the `MaybeNewSave` path.
  - The two-saves variant.
  - Resuming a repair after a restart.
- **Files:** `app/.../SaveRepair.kt`, `ServerSaveFlow.kt`, `HomeActivity.kt`, `language/.../SaveProtection.kt` (`SaveFixPage`, `suspectRegression`), `SaveConflictPrompt.kt`, `plugins/romm/.../RommLibrary.kt`, `romm/.../RommClient.kt` (reuse `archive()`).
- **Tests:**
  - `SaveRepairTest`, with fakes: each step's persistence and resume, offline → `WaitingForRomm`, running game → `WaitingForClose`, wrong pick, an equal copy not re-kept, choosing when two copies carry unsynced progress.
  - `SuspectRegressionTest`.
  - The RomM `keepAsBackups` test against the existing fake server: it is never a slot head, and the ids are verified.
- **Thor QA:** repeat PR 1 step 1, then **Fix it** with an all-files or held grant. Expect no picker, and the save back. Also: offline during step 1, then reconnect; kill Foldcade mid-placement, then relaunch; a fresh-save quarantine after clearing Azahar data.
- **False-alarm risk:** medium for structural regression. The cost of a false one is a choice, never a block.

**PR 3: DuckStation hosted-open evidence.**

- `HostedSavesProvider` keeps a bounded in-memory log. Judgement uses the selected package, UID, hosted root and the session's start and return times. Sending the same start to resume DuckStation begins a new card-open window because Android may boot the game again after it closes. Android reports native descriptor detach as a distinct listener status. The native file can still be in use. [Android descriptor lifecycle](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/core/java/android/os/ParcelFileDescriptor.java#L1091)
- A read-only open verifies the card path whatever the close status. A successful rename into the expected card can also verify the path. An open for writing alone cannot show completed bytes. Foldcade queues one clearly identified per-game card as an unslotted Backup. A resumed game can keep using that alternate card without reopening it, so Foldcade also preserves changed bytes from the open incident on resume. The incident records which hashes have been queued or kept. Changes to the expected card while the mismatch remains are also Backup-only. Mixed eligible opens leave the card association unresolved.
- With the data grant to DuckStation's own data folder, parse global settings and layer each per-game file over them. A folder verdict needs agreement across the possible outcomes because Foldcade cannot reliably pick a serial or disc-set override from a ROM filename. An explicitly configured directory elsewhere, newer than the latest hosted proof, gives `WrongFolder` even if the hosted grant remains. Return to **Foldcade** in DuckStation's folder picker. Only newer matching configuration clears this stop. A later matching open checks the game again.
- When Android blocks the data grant, Settings reports **Can’t read config** separately from the hosted check. See §4.2 for the non-gating silent-session rule.
- Tests cover open-log attribution, detach, atomic publication, silent starts, INI precedence, R1 card-name copy and Backup-only preservation.
- QA:
  - Make verified backups before changing the Thor's only RomM PS1 game's cards or settings. Prefer a disposable QA copy.
  - Launch with a populated matching hosted card and check **HostedOpen**. Verify that a first save published by rename also checks the card. Check the Android build's boot open mode. A read-write open of a different file leaves the check inconclusive.
  - With the data grant and hosted grant retained, point Memory Cards at a scratch folder. Confirm the **WrongFolder** stop, then return to the hosted folder and check again.
  - Use a disposable title whose file and database titles differ to exercise **File Title** versus **Title**. Confirm the notice shows the real card, the Backup verifies after reconnect and RomM's slot head stays the same.
  - Without the data grant, launch a disposable RomM game with an existing expected card after moving Memory Cards away. With Usage Access, check the dismissible warning after 20 seconds of gameplay. Repeat a quick return, Home → resume, a menu visit, and a slot-2-only open. Use two disposable games to check that game A's delayed write cannot create a Backup for game B.

**PR 4: Eden.**

- Resolve SaveDir and NAND from Eden's config. Keep the data grant for keys, firmware and configuration, and supplement it with a storage grant when a custom folder needs one.
- Resolve both account layouts in upstream order. Keep `<TITLEID>/…` as the archive layout and use `SwitchTitle` for the game ID.
- Persist the selected profile and paths in pending returns. Keep suspect bytes in the verified RomM Backup queue and fence newly selected account folders before placement.
- Test default and custom directories, both layouts, unreadable settings, and a switched profile across restart. Back up the Thor's three Switch titles and their RomM versions before device QA.

**PR 5: RetroArch config.**

- Read the selected package's launched `CONFIGFILE` and the override directory from the base config. Apply the global, core, content-directory, and game layers in that order. Apply the beside-content flag before the content-directory and core sort flags. Use held grants for config reads, without asking for new access.
- Judge a proven alternate file path at launch and reuse the result for the post-placement baseline. Inaccessible overrides, unsupported syntax, and unresolved directory fallbacks produce `Supports` only. Compare files even when the base folder is the same. Settings explains when no held grant covers the launched config.
- Use the persisted after-placement baseline to detect a changed file beside the ROM. Scope the incident to the affected game. Keep new alternate bytes through the existing verified, unslotted Backups queue and re-read the last confirmed alternate file before automatically closing a config incident. Guided Fix restores the expected layout. Unsupported formats have an unresolved spot with a reason, and launch-time spots restored from older builds go to Backups.
- Tests cover config precedence and flags, unreachable config, and readable beside-content baselines. Thor QA starts by backing up the existing RomM GBA game, then checks the normal layout, content-directory saves, a game override, and preservation without moving the RomM head.

**PR 6: melonDS + EX beside-game and candidate places.**

- The probes use `saveBesideGame` and `probePlaces`. melonDS uses the game's `.sav`, with `saves/melonDS` as a candidate alongside `saves/nds`. EX uses its supported battery and clock bundle in the content folder and checks shared `EmuEx` alongside the existing places. A candidate must be readable before and after the session. An empty melonDS file created at boot contributes no write evidence.
- API minor 28 gives `SaveLocationProbe.saveConfigPrivate` a default of `false`. Stock melonDS private preferences and EX private binary config yield **Can’t read config** until there is bound-folder session evidence. GBC.emu setup asks for the in-app selection first when Foldcade has no binding. The EX launch-mode step stays in setup.
- A beside-game write records an incident and captures progress for the game that wrote it. In melonDS, **Save next to ROM file** and the save directory apply across the emulator, so that incident gates every melonDS game and shows a mismatch in Settings and on each game. Any affected game opens the original game’s Fix. EX beside-ROM incidents gate only the game that wrote the save. A write to a shared candidate save folder opens an emulator-wide incident in either family. The beside-game Fix accepts only the watched folder. After the user changes the emulator setting and confirms that folder, repair verifies the unslotted Backup and restores the current RomM head there. The folder stays unchecked until a later session write. The finished Fix opens Y › Saves so the user can find the beside-ROM Backup. A fresh all-zero or all-`FF` GBA/GBC battery still proves a write, and its initialized contents are skipped by beside-ROM capture. A local-only game retains a durable device copy.
- QA uses a writable local library with a matching RomM game to exercise the complete repair, and a second local-only title for device retention. Also test a RomM download directly. Foldcade’s download provider is read-only, so if the emulator cannot produce a sibling file through its own storage access, the evidence stays inconclusive. An unscanned melonDS launch can use its private fallback.
- Settings’ **Save folder** opens the picker during a beside-ROM incident and retargets the guided Fix after a successful pick. Other incidents keep their existing Fix action. A zero-byte melonDS startup file gives no write evidence. For GBA.emu and GBC.emu, watched-folder verification uses changes to nonblank battery data. Blank batteries can still show that a session wrote beside the ROM.
- Risk: low.

**PR 7: ARMSX2 config.**

- Read the top-level `PCSX2-Android.ini` and `armsx2-settings.json` through Foldcade’s existing shared-storage access. The INI selects the memory-card directory. The mirror supplies the per-game slot 1 override and its modification time. Both are `Supports`. If the INI is unreadable, Settings shows **Can’t read config**.
- Use an in-tree `MemoryCards` path for placement. Treat an outside path as a read-only candidate. A game write there with a readable, unchanged watched copy opens a game-scoped `WrongFolder` incident. A confirmed move to another ARMSX2 data root applies to every game. An unchanged mirror and a session without a write leave the folder unconfirmed. A known save with no matching spot still uses the existing launch gate.
- Keep suspect folder-card bytes as verified, unslotted RomM Backups, or durably on the device for a local-only game. Guide the user back to a card folder inside the data root. Raw cards are left with the emulator.
- API minor 27 adds a default `SaveFolderAccess.storageDocumentId` so the plugin can compare an absolute path with a held shared-storage tree. Older plugins retain the same defaults.
- Thor QA uses `com.armsx2`, a disposable slot 1 folder card and a serial-bearing test disc. Check default, in-tree and outside paths, the per-game override, a run without saving, and the same outside path without a readable grant. The `xyz.aethersx2.android` and `xyz.aethersx2.tturnip` routes are NetherSX2 launch-only players.

**PR 8: aPS3e + Dolphin, OwnProvider and config.**

- Match the bound document ID and provider owner to the installed package. Persist **OwnProvider** with its install stamp. A lost grant or a different install of that package opens **Lost access**. Guided Fix regrants the root and fences local bytes before the existing restore or firmware flow. The aPS3e Premium hint follows the resolved package.
- aPS3e resolves its stock VFS path and matching `savedata` under account `00000001`. It reports a remapped path, unsupported configuration, an unreadable configuration, or a binding outside its own provider distinctly. Dolphin reads global and applicable game/revision INIs. Only the official provider's config can prove an alternate GCI destination. Conflicts, paths with unresolved aliases, and read failures leave the destination unresolved. Settings uses **Can’t read config** for an unreadable check.
- A confirmed custom Dolphin GCI destination gates the affected GameCube game. A session write in the legacy root can prove an emulator-wide folder mismatch only after a readable baseline and an unchanged watched copy. Fix tells the user to preserve the files and remove the custom path from the INIs. A verified default spot clears the game-scoped stop after the alternate has been handled, unless a bypass requires guided recovery. Readable matching alternate bytes go through the verified unslotted RomM Backup path. A local-only copy is kept on the device.
- The plugin API needs no new member. EmuCoreX and Flycast retain their launch-only integrations. Play! has no Foldcade save adapter. Wii remains launch-only in Dolphin.
- Thor QA uses dedicated disposable titles or fixtures and a test RomM library. Check official provider binding and a normal save, an unreadable INI, an exact per-game custom path, and a legacy candidate write. Confirm the latter two preserve matching GCI files while the RomM head stays in **Playing**. Check local-only copies offline. Reinstall only on an isolated installation with disposable data. For an existing Thor install, exercise regrant and firmware on a spare device or stock Android test instance, and use the JVM install-stamp fixture for the stale-grant case.

**PR 9: PPSSPP `--memstick` pin.**

API minor 29 adds the JVM default `SaveLocationProbe.pinsSaveFolder(packageName: String, versionCode: Long): Boolean = false`. PPSSPP returns false for every Free and Gold build. The v1.20.4 Android activity uses the game data URI before it looks at `Args`, and its native option parser lacks `--memstick`. The newer revision from the audit accepts the data URI and extra together. Foldcade ships with the extra and **PinnedLaunch** evidence disabled. The plugin reports its private Memory Stick setting, so Foldcade looks for a save write to verify the binding. A positive mismatch keeps the existing save gate and **Fix it** flow. [v1.20.4 Android intent handling](https://github.com/hrydgard/ppsspp/blob/v1.20.4/android/src/org/ppsspp/ppsspp/PpssppActivity.java#L1836-L1858), [v1.20.4 native options](https://github.com/hrydgard/ppsspp/blob/v1.20.4/UI/NativeApp.cpp#L526-L609), [audit revision intent handling](https://github.com/hrydgard/ppsspp/blob/3bc60270eae3069f13dfbfdb7292a7e1375de6f6/android/src/org/ppsspp/ppsspp/PpssppActivity.java#L2112-L2136)

| Risk | Source finding and decision |
|---|---|
| Quoting | The newer JNI passes the combined command line through `SplitCommandLine`. A candidate `org.ppsspp.ppsspp.Args` value of `--memstick="/sdcard/Foldcade QA/PSP"` reaches the native parser as one argument. Escape literal quotes for that tokenizer. [Android JNI](https://github.com/hrydgard/ppsspp/blob/3bc60270eae3069f13dfbfdb7292a7e1375de6f6/android/jni/app-android.cpp#L754-L778), [native tokenizer](https://github.com/hrydgard/ppsspp/blob/3bc60270eae3069f13dfbfdb7292a7e1375de6f6/Core/CmdLine.cpp#L767-L824) |
| PSP root | `GetSysDirectory` uses the supplied directory directly if its last component is `PSP` (case-insensitive). Otherwise it appends `PSP`. A candidate pin would name the bound PSP directory itself, with its `SAVEDATA` and `SYSTEM` children. [PSP path resolution](https://github.com/hrydgard/ppsspp/blob/3bc60270eae3069f13dfbfdb7292a7e1375de6f6/Core/Util/PathUtil.cpp#L94-L117) |
| Settings | The override sets the Memory Stick and creates its directories before selecting `SYSTEM` and loading config from it. A different root can load defaults or different `ppsspp.ini`, `controls.ini`, and per-game settings. PPSSPP’s private `memstick_dir.txt` holds the active choice. A readable INI in the target alone cannot establish equivalent settings. Even explicit global INI options leave per-game overrides rooted in the new search path. Keep pinning off until the active configuration and its persistence are proven. [Startup order](https://github.com/hrydgard/ppsspp/blob/3bc60270eae3069f13dfbfdb7292a7e1375de6f6/UI/NativeApp.cpp#L663-L681), [config load](https://github.com/hrydgard/ppsspp/blob/3bc60270eae3069f13dfbfdb7292a7e1375de6f6/UI/NativeApp.cpp#L734-L759), [global INIs](https://github.com/hrydgard/ppsspp/blob/3bc60270eae3069f13dfbfdb7292a7e1375de6f6/Core/Config.cpp#L1728-L1750), [per-game INI](https://github.com/hrydgard/ppsspp/blob/3bc60270eae3069f13dfbfdb7292a7e1375de6f6/Core/Config.cpp#L2440-L2448) |
| Storage access | PPSSPP opens `content://` files through its Android document helpers and a native path through ordinary filesystem I/O. Foldcade’s writable tree grant establishes Foldcade’s access. PPSSPP’s tree grant covers that URI, and passing a path in a string extra confers no filesystem permission. Require a protected launch, a writable Foldcade grant on the bound primary PSP root, and proof that PPSSPP itself can read and write that same raw path under scoped storage. A URI-based pin would need its own permission contract. [File opens](https://github.com/hrydgard/ppsspp/blob/v1.20.4/Common/File/FileUtil.cpp#L142-L238), [Android 11 storage](https://developer.android.com/about/versions/11/privacy/storage) |
| Warm process | The newer runtime shortcut handler parses the game filename and ignores startup options. The Android activity tracks native initialization in a static field and clears it on the shutdown path. Task clearing alone cannot certify a new native initialization. Keep PPSSPP’s existing launch flags and require a proven cold, non-resume start before considering a future pin. An open game must be allowed to save and exit normally. [Warm shortcut handler](https://github.com/hrydgard/ppsspp/blob/3bc60270eae3069f13dfbfdb7292a7e1375de6f6/android/jni/app-android.cpp#L1486-L1519), [activity shutdown](https://github.com/hrydgard/ppsspp/blob/v1.20.4/android/src/org/ppsspp/ppsspp/PpssppActivity.java#L995-L1037) |

The JVM tests check the API default, refusal for Free and Gold across version codes, and the shipped game-URI-only launch. A test-only fixture covers the candidate argument format and prerequisites. A future allowlist needs verification on an exact installed package and version, plus runtime evidence for the storage, settings, and cold-start conditions before any attempt can count as **PinnedLaunch**.

**Thor QA for the installed Free package.**

1. Reserve the Thor before inspecting it. Record the exact `versionName`, `versionCode`, Android SDK, and user ID with `pm dump org.ppsspp.ppsspp` and read-only package inspection. When PPSSPP is quiescent, read its private `files/memstick_dir.txt` with `su -c` to identify the current user’s root. Locally snapshot the selection file and byte contents or SHA-256 manifests of the active `SYSTEM` files, including `ppsspp.ini`, `controls.ini`, and any per-game INIs. Include a manifest of the original `SAVEDATA` tree. Keep those snapshots private.
2. Use an isolated Thor Android user with the same Free APK and disposable Foldcade data, or a spare Thor with the same APK. Use a PSP homebrew fixture with a unique readable `DISC_ID` and a deterministic in-game save operation. Configure the test PPSSPP to use a scratch PSP root and grant Foldcade that same root. Keep the production Android user’s Memory Stick selection and saves outside the test. If isolation is unavailable, stop at read-only checks.
3. Use a test build to inspect Foldcade’s outgoing intent. On the first launch from a cold process, after **Exit to menu** with PPSSPP still running, and on **Home → resume**, verify the game opens or resumes through the ordinary route. Check that a fresh launch gives PPSSPP the game URI and its read grant, the `Args` extra is absent, and no **PinnedLaunch** evidence appears. Skip saving once and expect the folder to stay unchecked. Then save in the homebrew and verify the new files are under the watched `PSP/SAVEDATA` and the folder check comes from that write.
4. In the isolated profile, make the default `PSP` location a readable candidate and keep Foldcade bound to a separate scratch `PSP` tree. Write a new homebrew save through PPSSPP to the candidate. Verify **Wrong folder** on return and the next launch gate. Use **Fix it** to confirm the candidate binding and preserved copies. If checking RomM, use a disposable game record on a test server and verify the unslotted Backup and slot head independently.
5. Exit PPSSPP normally in the test profile. Repeat the original user’s snapshots and compare them byte-for-byte or by SHA-256, including path sets and absent files. Any difference is a QA failure. On v1.20.4 the expected result is the disabled route. Reserve a positive pin trial with a spaced path for a supporting APK in an isolated profile, and confirm real read/write behavior as PPSSPP’s app UID and the effective INIs on cold start before testing warm behavior.

**PR 10: Device-only routes.**

- Y › Saves routes for local-only and launch-only games (§7).
- The "Play with $supported" suggestion.
- Partner copy.

**PR 11 (later, needs data): silent-session caution.**

- If three Foldcade sessions on one emulator, each 15 minutes or longer, over two or more days, all end with `NoNews`, open `MaybeWrongFolder("No save seen in your last 3 sessions")`.
- This applies only to emulators with no `perStart` signal.
- Ship only if PR 1–6 QA logs show real sessions rarely meet the bar without a write.

### 8.3 Deferred, and why

| Audit row | Deferred because |
|---|---|
| 14: states, slot 2, raw cards, Wii/DSi NAND, PS3 aliases, Switch device/BCAT | Each needs format work or a new save unit. The model shows them honestly meanwhile: the status is about the battery/account slot only, and `romm.md` already says states don't sync. Revisit after PR 2. |
| 15: partner cloud receipts | Needs DroidDeck/GameNative/host APIs, which don't exist. The `Unconfirmed` copy is truthful in the meantime. |
| 16: firmware/key gaps | Separate from save location. Eden's `title.keys` gap is noted in `romm.md`. |
| 17: maintained emulator builds | The last resort. Upstream patches (§8.4) come first. No current emulator needs one to reach the PR 1–9 level. |
| 5 (part): linking local saves to an existing RomM record without a matching file name | Needs RomM support to be checked first (audit). PR 10 covers the same-name route. |
| 12: common live-path/no-state bridge for melonDS/EX | An upstream ask (§8.4). EX Launch Mode stays a standing setup step. |

### 8.4 Upstream patches worth drafting

These are drafted, not sent; the user approves before anything goes out.

1. **Azahar:** a read-only `liveSaveLocation` call on a consent-scoped provider. It returns `DirectoryInitialization.userDirectory`, the title's resolved save paths, and a process generation, plus a flush receipt at emulation shutdown. This makes the gate exact with no all-files access.
2. **melonDS Android:** a `save_uri` launch extra resolved through `SramProvider`, plus a provider query for the effective save directory and `use_rom_dir`.
3. **EX framework (Rakashazi):** one bridge for all five apps: the effective save dir, batteries and clock paths, launch mode, and a session extra for save URI plus no-state boot. Snes9x EX+ would use the author's official build, since the bridge goes upstream.
4. **Eden:** a provider query for the effective SaveDir, current profile and layout.
5. **ARMSX2:** a provider query for `currentInitDataRoot()` and the effective slot 1 path at boot, including the serial-named card selected by the private [`memcard.perGame` preference](https://github.com/ARMSX2/ARMSX2/blob/25e7084fdefcf1e9069481a583c38c103b03e8f1/platforms/android/app/src/main/java/com/armsx2/runtime/MainActivityRuntime.kt#L934-L950).
6. **DuckStation:** an *ask*, not a patch, given CC BY-NC-ND. Request an effective-card-path query.

---

## 9. Where the audit and the code differ

- **Azahar log movement.** The audit (`Azahar › Readable signals`) calls absent log movement "inconclusive for a reused process", because initialization is guarded by `directoryState`. But upstream `RunCitra` logs `Azahar starting...` on every game start, and Foldcade always starts Azahar with `ClearTop | ClearTask` (`AzaharEntry.kt:135`). So each Foldcade launch should move the live log. QA run 2 measured cold and warm log movement; the resulting launch-only decision and resume exclusion are in §4.2.
- **The scope of the launch guard.** The audit lists the Eden/PPSSPP/Dolphin/EX guard. The code confirms it (`LaunchSaves.kt:52`), and also shows that Azahar with an unresolvable title and a server save launches without a stop: no spots, so `place` returns true. The design makes the guard universal (§3.1, case 3).
- **The receipt time.** The audit asks for "verified server receipt/head age". `SaveLedger` has no time at all (`synced_hash`, `archived_hash`, `emulator`), so PR 1 adds `synced_at`. Old records show "Saved to RomM" with no age, because the hash agreement is still evidence.
- **"Missing selected player becomes Fine".** Confirmed at `ShellController.kt:773–774`. Non-emulators returning `Fine` is confirmed at `SaveHealth.kt:15`.
- Everything else this design builds on was checked against the code and agrees: `SaveFolderFinder`'s marker rule and grant use, `FoldcadeApp.kt:1685` keeping chosen folders on their grant, `RommLibrary.reconcile` keeping bytes durably before upload, the archive path in `RommClient`, and the 20-per-player backup cap.

## 10. Open questions, with recommendations

1. **Badge device-only games?**
   - The recommendation: badge only when the game has play history on this device (`playedHere`), and show the amber line always.
   - A local library of 300 never-played games shouldn't turn amber. A played one has a save at risk.
2. **Unverified folders lose their check on upgrade.**
   - The recommendation: accept this, and say so in the release notes: "Each emulator gets its check back the first time a game saves." The alternative keeps today's false greens.
3. **How to confirm the live folder when Foldcade can't read it.**
   - The recommendation: open the picker at the suspected folder. One grant both proves the folder and rebinds it.
   - Auto-find saves (all-files access) stays on Set up saves and Settings, not the Fix page: with the live folder known, the picker in that folder is the whole fix, and a second way only adds a choice (UX review after Thor QA run 3).
4. **What "Play anyway" captures.**
   - The recommendation: capture the live copy as a Backup on every return while a `WrongFolder` is open, so playing anyway is never device-only.
5. **`perStart` and the emulator-grant rule.**
   - Decided from Thor QA run 2: enable Azahar's per-start check for Foldcade-launched cold/warm sessions of at least 20 s; exclude resumes and unreadable logs. Keep the emulator-grant rule off because Azahar retains the old grant. The measurements and custom-root discovery bounds are in §4.2.
6. **History of resolved incidents.**
   - The recommendation: none. Backups on RomM, and their folder labels in Y › Saves, are the history the user needs.
7. **A stable version bump** for PR 1, since it changes user-visible status everywhere.
   - The recommendation: yes, a minor `versionName` bump when PR 1 and PR 2 both land, so the release notes explain the new states once.

**Decided, 24 November 2026:** all seven recommendations above are accepted as written. Question 5 was refined after QA run 2 as recorded above: Azahar launch-only per-start evidence is enabled; generic and emulator-grant evidence remain off.
