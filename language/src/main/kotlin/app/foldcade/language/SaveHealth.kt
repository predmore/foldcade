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

/** A save warning stays first even while a transfer is running or reporting its result. */
fun saveHealthLeads(health: SaveHealth, activity: TileActivity?): Boolean = saveHealthLine(health) != null &&
    (health is SaveHealth.NotSyncing ||
        health == SaveHealth.Waiting && activity != TileActivity.Syncing && activity !is TileActivity.Synced ||
        activity == null || activity == TileActivity.Paused || activity is TileActivity.OnHost)

/** A standing setup fault already explains a sync result; a transfer can still show its progress beside it. */
fun heroActivityLine(health: SaveHealth, activity: TileActivity): String? =
    if (saveHealthLeads(health, activity) && (activity == TileActivity.Syncing || activity is TileActivity.Synced)) null
    else activityLine(activity)
