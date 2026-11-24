package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.Player
import app.foldcade.language.OffDeviceFact

/** No launch or setup: only games with held saves need their readable local ROM resolved. */
internal suspend fun resolveReceiptSpots(saves: PlayerSaves, backend: LibraryBackend, game: Game, player: Player,
    local: suspend () -> LaunchTarget?): List<SaveSpot>? {
    if (backend.saves(game).slots.isEmpty()) return null
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
