package app.foldcade.language

/**
 * What a library is doing with one game, as its tile and the top screen show it.
 * None of it asks anything of the player: the game opens when it is ready.
 */
sealed interface TileActivity {
    /** The game is arriving. [fraction] is 0..1, or null while the size is not known. */
    data class Downloading(val fraction: Float?) : TileActivity

    /** Every byte is here and is being checked. */
    data object Checking : TileActivity

    /** The game's saves are syncing. */
    data object Syncing : TileActivity

    /** A sync ended. Its mark shows until [untilMillis], then the tile is quiet again. */
    data class Synced(val mark: SyncMark, val untilMillis: Long) : TileActivity

    /** The game is still open in its player, behind Home. A brings it back where it was. */
    data object Paused : TileActivity
}

/** How a finished sync reads. Only [Synced] and [KeptBoth] earn the check. */
enum class SyncMark {
    Synced,
    KeptBoth,
    Later,
    NotSynced,
}

/** True when the sync worked, so the tile shows its check. */
val SyncMark.worked: Boolean get() = this == SyncMark.Synced || this == SyncMark.KeptBoth

/** The top screen's words for [activity], first on the line under the game's name. */
fun activityLine(activity: TileActivity): String = when (activity) {
    is TileActivity.Downloading -> activity.fraction
        ?.let { "${Copy.downloading} ${(it.coerceIn(0f, 1f) * 100).toInt()}%" }
        ?: Copy.downloading
    TileActivity.Checking -> Copy.checkingDownload
    TileActivity.Syncing -> Copy.syncingSaves
    TileActivity.Paused -> Copy.paused
    is TileActivity.Synced -> when (activity.mark) {
        SyncMark.Synced -> Copy.savesSynced
        SyncMark.KeptBoth -> Copy.savesKeptBoth
        SyncMark.Later -> Copy.savesSyncLater
        SyncMark.NotSynced -> Copy.savesNotSynced
    }
}
