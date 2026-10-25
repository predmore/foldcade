package app.foldcade.language

/** The protection of a game's saves, independent of short-lived library activity. */
sealed interface SaveHealth {
    data object Fine : SaveHealth
    data class NotSyncing(val reason: SaveIssue) : SaveHealth
    data object Waiting : SaveHealth
    data object DeviceOnly : SaveHealth
}

enum class SaveIssue { NoFolder, HostedFolder, NeedsSetup, NoSlot }

/** Setup takes precedence: uploading held bytes cannot protect a player's unreachable saves. */
fun deriveSaveHealth(emulator: Boolean, onServer: Boolean, supportsSync: Boolean, issue: SaveIssue?, waiting: Boolean): SaveHealth = when {
    !emulator -> SaveHealth.Fine
    !onServer || !supportsSync -> SaveHealth.DeviceOnly
    issue != null -> SaveHealth.NotSyncing(issue)
    waiting -> SaveHealth.Waiting
    else -> SaveHealth.Fine
}

val SaveHealth.needsAttention: Boolean get() = this is SaveHealth.NotSyncing || this == SaveHealth.Waiting

fun saveHealthLine(health: SaveHealth): String? = when (health) {
    SaveHealth.Fine -> null
    is SaveHealth.NotSyncing -> Copy.savesNotSyncing
    SaveHealth.Waiting -> Copy.savesWaiting
    SaveHealth.DeviceOnly -> Copy.savesDeviceOnly
}

/** A paused game is standing still; only work and its brief result cover save health. */
fun saveHealthLeads(health: SaveHealth, activity: TileActivity?): Boolean = saveHealthLine(health) != null &&
    (activity == null || activity == TileActivity.Paused || activity is TileActivity.OnHost)
