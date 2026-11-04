package app.foldcade

import app.foldcade.api.plugin.Player
import app.foldcade.language.PlayerChoiceCopy
import app.foldcade.plugins.retroarch.RetroArchPlayer

typealias PlayerChoice = app.foldcade.language.PlayerChoice

enum class PlayerChoiceSource { Game, System, Automatic }

data class PlayerResolution(
    val player: Player?,
    val source: PlayerChoiceSource,
    val skipped: List<PlayerChoice> = emptyList(),
)

/** One decision for launch, save placement/read-back, and the shell's screen hints. */
fun resolvePlayer(
    players: List<Player>,
    gameChoice: PlayerChoice? = null,
    systemChoice: PlayerChoice? = null,
    installed: (Player) -> Boolean,
    runnable: (Player) -> Boolean = { true },
    rank: (Player) -> Int = { 0 },
    coreFiles: (Player) -> Set<String>? = { null },
): PlayerResolution {
    val skipped = ArrayList<PlayerChoice>()
    for ((choice, source) in listOf(gameChoice to PlayerChoiceSource.Game, systemChoice to PlayerChoiceSource.System)) {
        if (choice == null) continue
        val wantedCore = choice.core
        val player = players.firstOrNull { it.id == choice.playerId }
        val core = (player as? RetroArchPlayer)?.system?.cores?.firstOrNull { it.file == wantedCore }
        if (player == null || !installed(player) ||
            (wantedCore != null && core == null)) {
            skipped += choice
            continue
        }
        val chosen = if (wantedCore != null) (player as RetroArchPlayer).withCore(wantedCore) else player
        return PlayerResolution(pinCurrentCore(chosen, coreFiles), source, skipped)
    }
    val ranked = rankPlayers(players, installed, runnable, rank)
    return PlayerResolution(pinCurrentCore(ranked.firstOrNull(installed) ?: ranked.firstOrNull(), coreFiles), PlayerChoiceSource.Automatic, skipped)
}

/** Installed players, with visible RetroArch cores expanded in the system's core order. */
fun playerOptions(
    players: List<Player>,
    installed: (Player) -> Boolean,
    runnable: (Player) -> Boolean = { true },
    rank: (Player) -> Int = { 0 },
    coreFiles: (Player) -> Set<String>? = { null },
): List<PlayerChoice> = rankPlayers(players, installed, runnable, rank).filter(installed).flatMap { player ->
    if (player is RetroArchPlayer) {
        val files = coreFiles(player)
        if (files == null) listOf(PlayerChoice(player.id, label = player.displayName))
        else player.system.cores.filter { it.fileName in files }.map { PlayerChoice(player.id, it.file, PlayerChoiceCopy.core(it.libraryName)) }
    } else listOf(PlayerChoice(player.id, label = player.displayName))
}

/** Freeze Automatic's core too: a later core scan must not change a running game's save folder. */
private fun pinCurrentCore(player: Player?, coreFiles: (Player) -> Set<String>?): Player? {
    if (player !is RetroArchPlayer || player.pinnedCore != null) return player
    val files = coreFiles(player)
    val core = player.system.cores.firstOrNull { files?.contains(it.fileName) == true } ?: player.system.cores.first()
    return player.withCore(core.file)
}

internal fun playerChoiceLabel(player: Player?): String? = when (player) {
    is RetroArchPlayer -> player.pinnedCore?.let { file -> player.system.cores.firstOrNull { it.file == file } }
        ?.let { PlayerChoiceCopy.core(it.libraryName) } ?: player.displayName
    else -> player?.displayName
}
