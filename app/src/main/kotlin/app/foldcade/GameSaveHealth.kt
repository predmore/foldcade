package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameActivity
import app.foldcade.api.plugin.HostedSaveFolder
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.SaveSyncState
import app.foldcade.language.SaveStatus
import app.foldcade.language.SaveIssue
import app.foldcade.language.*
import app.foldcade.plugins.emulators.EmulatorPlayer
import app.foldcade.plugins.retroarch.RetroArchPlayer
import kotlin.coroutines.cancellation.CancellationException

/** Player-level setup, independent of the file format or a particular game's slots. */
internal fun playerSaveIssue(player: Player?, sync: SaveSyncState?, setup: String?): SaveIssue? = when {
    player is HostedSaveFolder && !player.hostedFolderInUse() -> SaveIssue.HostedFolder
    player is SaveFolderHolder && player !is HostedSaveFolder && !player.hasSaveFolder() -> SaveIssue.NoFolder
    sync is SaveSyncState.NeedsSetup || setup != null -> SaveIssue.NeedsSetup
    else -> null
}

internal data class GameSaveSupport(val declarations: List<SaveDeclaration>, val supportsSync: Boolean)

/** Pure plugin metadata, cached by the shell per player and game. */
internal fun readGameSaveSupport(game: Game, player: Player?): GameSaveSupport = try {
    GameSaveSupport(player?.saveDeclarations(game).orEmpty(), player?.supportsSaveSync(game) == true)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    GameSaveSupport(emptyList(), player is SaveFolderHolder || player is HostedSaveFolder)
}

/**
 * What a game file's type alone says about its save, before Foldcade reads it: an Azahar `.zcci`
 * names no title, and Dolphin syncs only ISO and GCM. Null when only the file's contents can tell.
 */
internal fun fileSpotIssue(player: Player, fileName: String?): SpotFact.Unresolved? {
    val extension = fileName?.substringAfterLast('.', "")?.lowercase(java.util.Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        player is app.foldcade.plugins.azahar.AzaharPlayer && extension == "zcci" -> SpotFact.Unresolved(SpotIssue.NoTitleId, SaveRouteCopy.zcci)
        player is app.foldcade.plugins.dolphin.DolphinPlayer && extension !in setOf("iso", "gcm") -> SpotFact.Unresolved(SpotIssue.UnsupportedImage,
            SaveProtectionCopy.unsupportedFormat(extension.uppercase(java.util.Locale.ROOT), "ISO and GCM"))
        else -> null
    }
}

/** An emulator, synced or not, as opposed to a partner, a stream or an app that keeps its own saves. */
internal fun isSaveEmulator(player: Player, supportsSync: Boolean): Boolean =
    player.needsLocalFile || player is EmulatorPlayer || supportsSync

/** Reads only player declarations and the host's cached checks; never reads a card on a frame. */
internal fun gameSaveHealth(
    game: Game,
    player: Player?,
    onServer: Boolean,
    waiting: Boolean,
    sync: SaveSyncState? = null,
    setup: String? = null,
    reachable: Boolean? = null,
    saves: GameSaveSupport = readGameSaveSupport(game, player),
    location: LocationFact? = null,
    offDevice: OffDeviceFact? = null,
    incidents: List<SaveIncident> = emptyList(),
    playedHere: Boolean = false,
    waitingSinceMillis: Long? = null,
    spotIssue: SpotIssue? = null,
    spotDetail: String? = null,
    /** The game file's name as the catalog recorded it, for what its type alone says before any launch. */
    fileName: String? = null,
): SaveStatus {
    val (declarations, supportsSync) = saves
    val emulator = player != null && isSaveEmulator(player, supportsSync)
    val keeper = when {
        player == null -> SaveKeeper.NoPlayer
        emulator && supportsSync -> SaveKeeper.Emulator(player.displayName)
        emulator -> SaveKeeper.LaunchOnly(player.displayName)
        player.id.startsWith("droiddeck") -> SaveKeeper.Partner("DroidDeck", "Steam Cloud")
        player.id.startsWith("gamenative") -> SaveKeeper.Partner("GameNative", null)
        // A stream's adapter can come from an APK plugin with its own id: the game's backend says what it is.
        player.id.contains("moonlight") || game.backendId == "moonlight" || game.platformId == "moonlight" -> SaveKeeper.HostComputer
        else -> SaveKeeper.App
    }
    val issue = playerSaveIssue(player, sync, setup) ?: SaveIssue.NoSlot.takeIf {
        supportsSync && (declarations.isEmpty() || declarations.any { it.locationUri == null } || reachable == false)
    }
    val bound = declarations.firstOrNull()?.locationUri?.let { FolderRef(storageIdentity(it), runCatching { folderPathLabel(it) }.getOrDefault(it)) }
    val location = if (issue != null) LocationFact.Missing(issue) else location ?: when {
        bound != null -> LocationFact.Bound(bound)
        else -> LocationFact.NotApplicable
    }
    val coreIssue = (player as? RetroArchPlayer)?.unsupportedSaveFormat("")
    val fileIssue = player?.let { fileSpotIssue(it, fileName) }
    val spot = when {
        coreIssue != null -> SpotFact.Unresolved(SpotIssue.UnsupportedSaveFormat, coreIssue)
        reachable == false -> SpotFact.Unresolved(spotIssue ?: fileIssue?.reason ?: SpotIssue.NoTitleId, spotDetail ?: fileIssue?.detail)
        // Known before the first launch: the file's type alone means its save has no place.
        reachable == null && fileIssue != null -> fileIssue
        reachable == true -> SpotFact.Resolved
        else -> SpotFact.Unchecked
    }
    // Where a save can go is structural first: a receipt read from a local library, or a signed-out
    // server, can't make a launch-only emulator sync or give a folder game a RomM copy (§7.1).
    val destination = when {
        !supportsSync && emulator -> OffDeviceFact.NoDestination(NoDestinationWhy.PlayerCantSync)
        !onServer && emulator -> OffDeviceFact.NoDestination(NoDestinationWhy.NotOnRomm)
        else -> offDevice ?: OffDeviceFact.Unknown
    }
    return deriveSaveStatus(SaveFacts(keeper, location, spot, destination,
        if (waiting) waitingSinceMillis ?: 0 else null, incidents, playedHere))
}

/** Read the ledger after work settles, rather than competing with an active sync's ROM lock. */
internal fun GameActivity.refreshesSaveHealth(): Boolean = this is GameActivity.Synced || this == GameActivity.Idle
