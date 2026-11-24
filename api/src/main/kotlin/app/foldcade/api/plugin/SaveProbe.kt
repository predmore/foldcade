package app.foldcade.api.plugin

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

    /**
     * Optional relative signature for shallow all-files discovery of custom roots. The host
     * accepts only a fresh Proves signal from this file naming its own root. Minor 26.
     */
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
     * (one per line), or null when only Foldcade's picker is needed.
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

/** Whether saves can reach the backend. Open; added in API minor 26. */
sealed interface SaveServiceState {
    data object Ready : SaveServiceState
    data object SignedOut : SaveServiceState
    data class ServerTooOld(val version: String) : SaveServiceState
    data class Unreachable(val lastContactMillis: Long?) : SaveServiceState
    data object Unknown : SaveServiceState
}
