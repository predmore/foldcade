package app.foldcade

import app.foldcade.api.Session
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.StartDisplay

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
}

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
): PlayerLaunch {
    if (player.occupiesBothDisplays && anotherBothPanelRunning && !closeConfirmed) {
        return PlayerLaunch.Blocked(LaunchBlock.CloseFirst, player.displayName)
    }
    val resolved = player.packageNames.firstOrNull { it in installedPackages }
        ?: return PlayerLaunch.Blocked(LaunchBlock.MissingPlayer, player.displayName)
    if (player.needsLocalFile && target !is LaunchTarget.ContentUri) {
        return PlayerLaunch.Blocked(LaunchBlock.NoLocalFile, player.displayName)
    }
    if (player is SaveFolderHolder && !player.hasSaveFolder() && !saveFolderSettled) {
        return PlayerLaunch.Blocked(LaunchBlock.SaveFolder, player.displayName)
    }
    val handoff = target ?: LaunchTarget.AppRef(emptyMap())
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
