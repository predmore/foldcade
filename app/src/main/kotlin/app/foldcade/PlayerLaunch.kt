package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.StartDisplay
import java.security.MessageDigest

/**
 * What the shell should do before it starts a player.
 * The player builds the intent. This type does not name a package or a path.
 */
sealed interface PlayerLaunch {
    data class Blocked(val block: LaunchBlock, val playerName: String) : PlayerLaunch

    data class Ready(
        val intent: PlayerIntent,
        val occupiesBothDisplays: Boolean,
        val startDisplay: StartDisplay,
    ) : PlayerLaunch
}

enum class LaunchBlock {
    CloseFirst,
    MissingPlayer,
    NoLocalFile,
    SaveFolder,

    /** The player opens games by path, and this game's file has none another app can read. */
    NoSharedPath,
}

/**
 * [players] in the order a launch tries them: by [rank], lowest first, each
 * rank in registration order. A player that is installed and [runnable] comes
 * before one that is installed and is not, so RetroArch without a core for
 * the platform gives way to a standalone emulator.
 */
fun rankPlayers(
    players: List<Player>,
    installed: (Player) -> Boolean,
    runnable: (Player) -> Boolean = { true },
    rank: (Player) -> Int = { 0 },
): List<Player> {
    val ordered = players.sortedBy(rank)
    val ready = ordered.filter { installed(it) && runnable(it) }
    return ready + ordered.filterNot { it in ready }
}

/**
 * The player that opens a game whose platform has [players]: the first
 * installed one in [rankPlayers] order. With none installed, the first ranked
 * player is returned so the missing-player dialog can name it. Null only when [players] is empty.
 */
fun preferredPlayer(
    players: List<Player>,
    runnable: (Player) -> Boolean,
    rank: (Player) -> Int,
    installed: (String) -> Boolean,
): Player? {
    val isInstalled = { player: Player -> player.packageNames.any(installed) }
    val ranked = rankPlayers(players, isInstalled, runnable, rank)
    return ranked.firstOrNull(isInstalled) ?: ranked.firstOrNull()
}

/** [preferredPlayer] in registration order, every installed player runnable. */
fun preferredPlayer(players: List<Player>, installed: (String) -> Boolean): Player? =
    preferredPlayer(players, runnable = { true }, rank = { 0 }, installed = installed)

/** The key a save folder is stored, asked for, and listed under. Players can share one. */
fun Player.saveFolderId(): String = (this as? SaveFolderHolder)?.saveFolderKey ?: id

/** True when a different both-panel player already owns the screens. */
fun anotherBothPanelRunning(session: Session, gameId: String): Boolean {
    val running = listOfNotNull(session.topApp, session.bottomApp)
        .firstOrNull { it.occupiesBothDisplays }
        ?: return false
    return running.id != gameId
}

/**
 * Decides the next step for [player].
 * A both-panel game asks to close a different both-panel game first.
 * A missing install is reported before a missing file.
 * A save folder is offered once, and only when [saveFolderSettled] is false.
 * Skipping that offer still returns [PlayerLaunch.Ready]. A missing folder
 * never blocks play. Save sync is the caller that reads [SaveFolderHolder].
 * [componentResolves] is the shell's `resolveActivity` check. A null result
 * is the missing-player state, not a crash.
 * A player that opens games by path gets [sharedFile]'s answer for a content
 * URI, and is blocked when there is none.
 */
fun planPlayerLaunch(
    player: Player,
    game: Game,
    target: LaunchTarget?,
    installedPackages: Set<String>,
    anotherBothPanelRunning: Boolean,
    closeConfirmed: Boolean,
    saveFolderSettled: Boolean = false,
    componentResolves: (PlayerIntent) -> Boolean = { true },
    sharedFile: (String) -> LaunchTarget.SharedFile? = { null },
): PlayerLaunch {
    if (player.occupiesBothDisplays && anotherBothPanelRunning && !closeConfirmed) {
        return PlayerLaunch.Blocked(LaunchBlock.CloseFirst, player.displayName)
    }
    val resolved = player.packageNames.firstOrNull { it in installedPackages }
        ?: return PlayerLaunch.Blocked(LaunchBlock.MissingPlayer, player.displayName)
    if (player.needsLocalFile && target !is LaunchTarget.ContentUri && target !is LaunchTarget.SharedFile) {
        return PlayerLaunch.Blocked(LaunchBlock.NoLocalFile, player.displayName)
    }
    val opened = if (player.opensByPath && target is LaunchTarget.ContentUri) {
        sharedFile(target.uri) ?: return PlayerLaunch.Blocked(LaunchBlock.NoSharedPath, player.displayName)
    } else {
        target
    }
    if (player is SaveFolderHolder && !player.hasSaveFolder() && !saveFolderSettled) {
        return PlayerLaunch.Blocked(LaunchBlock.SaveFolder, player.displayName)
    }
    val handoff = opened ?: LaunchTarget.AppRef(emptyMap())
    val intent = player.launchIntent(LaunchRequest(game, handoff, resolved))
    if (!componentResolves(intent)) {
        return PlayerLaunch.Blocked(LaunchBlock.MissingPlayer, player.displayName)
    }
    return PlayerLaunch.Ready(
        intent = intent,
        occupiesBothDisplays = player.occupiesBothDisplays,
        startDisplay = player.startDisplay,
    )
}

/** A key for [intent], kept to tell whether the next start is the same one. */
internal fun startKey(intent: PlayerIntent): String =
    MessageDigest.getInstance("SHA-256").digest(intent.toString().toByteArray())
        .joinToString("") { "%02x".format(it) }

/**
 * [intent] or [other], two spellings of the same game for a player that tells
 * games apart by their strings, given the key of the one [last] started.
 * The same spelling resumes the game, so it is kept, unless a save is
 * [fresh]: then the other one makes the player quit and start anew. Any
 * other game was running, or none, so [intent] starts it.
 */
internal fun chooseStart(intent: PlayerIntent, other: PlayerIntent, last: String?, fresh: Boolean): PlayerIntent =
    when (last) {
        startKey(intent) -> if (fresh) other else intent
        startKey(other) -> if (fresh) intent else other
        else -> intent
    }
