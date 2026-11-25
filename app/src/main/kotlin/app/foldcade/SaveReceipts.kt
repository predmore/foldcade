package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.Player
import app.foldcade.language.OffDeviceFact
import app.foldcade.plugins.eden.EdenPlayer
import app.foldcade.plugins.aps3e.Aps3ePlayer
import app.foldcade.plugins.dolphin.DolphinPlayer

/** No launch or setup: only games with held saves need their readable local ROM resolved. */
internal suspend fun resolveReceiptSpots(saves: PlayerSaves, backend: LibraryBackend, game: Game, player: Player,
    cached: List<SaveSpot>? = null,
    local: suspend () -> LaunchTarget?): List<SaveSpot>? {
    if (backend.saves(game).slots.isEmpty()) {
        val known = cached ?: return null
        // A profile, card or VFS mapping can change while the grant stays the same.
        if (player is EdenPlayer || player is Aps3ePlayer || player is DolphinPlayer)
            return local()?.let { saves.spots(player, game, it) }
        val declarations = player.saveDeclarations(game)
        return known.takeIf { spots -> spots.all { spot -> declarations.any { declaration ->
            declaration.slot == spot.slot && declaration.locationUri?.let { storageIdentity(it) } == storageIdentity(spot.folderUri)
        } } }
    }
    val target = local() ?: return null
    return saves.spots(player, game, target, forLaunch = false)
}

/** Re-read working bytes and compare with the backend's held hashes and receipts. */
internal suspend fun readSaveReceipt(saves: PlayerSaves, backend: LibraryBackend, game: Game, spots: List<SaveSpot>): OffDeviceFact {
    val receipts = backend.saveReceipts(game)
    val held = backend.saves(game).slots.associate { it.slot to it.contentHash }
    val working = spots.mapNotNull { spot -> saves.readSpot(spot, strict = true)?.first?.let { spot.slot to md5Hex(it) } }.toMap()
    return when {
        spots.isEmpty() -> OffDeviceFact.Unknown
        working.isEmpty() && held.isEmpty() -> OffDeviceFact.NothingYet
        working.isNotEmpty() && working.all { (slot, hash) -> held[slot] == hash && receipts.containsKey(slot) } -> {
            val times = working.keys.map { receipts[it] }
            OffDeviceFact.OnRomm(if (times.any { it == null }) null else times.filterNotNull().minOrNull())
        }
        else -> OffDeviceFact.Unknown
    }
}
