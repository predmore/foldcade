// SPDX-License-Identifier: MIT

package app.foldcade.api.plugin

/**
 * One copy of a save the player can choose.
 * [source] is the device or program that wrote it.
 * [updatedAtMillis] is when it was last written, epoch milliseconds.
 * [sizeBytes] is the length of that copy.
 */
data class SaveCopy(
    val source: String,
    val updatedAtMillis: Long,
    val sizeBytes: Long,
)

/**
 * Two copies of one save, both still on disk.
 * The shell shows this on the bottom screen. It does not read a backend's own fields.
 * [id] is the id [LibraryBackend.resolveSaveConflict] takes.
 */
data class SaveConflict(
    val id: String,
    val libraryId: String,
    val gameLabel: String,
    val local: SaveCopy,
    val remote: SaveCopy,
)

/**
 * What the player chose.
 * Every value keeps both copies. None of them deletes a save.
 * [DecideLater] asks again the next time Foldcade is in front after a launch.
 */
enum class SaveConflictChoice {
    /** The game plays the copy from this device. The other copy stays. */
    KeepLocal,

    /** The game plays the copy from the other device. The other copy stays. */
    KeepRemote,

    /** Leave both copies as they are and do not ask again until the bytes change. */
    KeepBoth,

    /** Leave both copies and ask again on the next launch. */
    DecideLater,
}
