package app.foldcade.language

/** Folder saves use the count of actual files, independent of ZIP compression or directory entries. */
data class SaveShape(val bytes: Long, val files: Int, val folder: Boolean = files > 1)

enum class RegressionContext { FreshRoot, Reinstalled, NewProfile, OpenIncident }

fun suspectRegression(lastSynced: SaveShape?, observed: SaveShape, context: Set<RegressionContext> = emptySet()): String? {
    if (lastSynced == null || observed.files < 1 || lastSynced.folder != observed.folder) return null
    return when {
        context.isNotEmpty() -> SaveProtectionCopy.mayBeNewGame
        lastSynced.folder && observed.files <= lastSynced.files / 2 -> SaveProtectionCopy.mayBeNewGame
        !lastSynced.folder && lastSynced.bytes > 1024 && observed.bytes <= lastSynced.bytes / 4 -> SaveProtectionCopy.mayBeNewGame
        else -> null
    }
}
