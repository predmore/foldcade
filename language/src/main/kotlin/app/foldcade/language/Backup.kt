package app.foldcade.language

import kotlinx.serialization.json.*

object BackupCopy {
    const val title = "Backup"
    const val body = "Home, collections, hidden games, settings, themes and play time, in one file. Sign-ins stay on this device."
    const val backUp = "Back up"
    const val restore = "Restore"
    const val undo = "Undo restore"
    const val saved = "Backed up."
    fun saved(skippedThemes: Int): String = when (skippedThemes) {
        0 -> saved
        1 -> "$saved 1 theme was too large to include."
        else -> "$saved $skippedThemes themes were too large to include."
    }
    const val notSaved = PlayCopy.notSaved
    const val unreadable = PlayCopy.unreadable
    const val foreign = "That file isn't a Foldcade backup."
    const val newer = "That backup is from a newer Foldcade."
    const val restoreFailed = "The backup couldn't be restored."
    const val restoreUnfinished = "The restore didn't finish. Your setup is as it was."
    const val confirm = "Restore this backup?"
    const val restored = "Restored"
    fun replacing(date: String): String = "Replaces Home, collections, hidden games, settings and theme with the backup's from $date. Play time is added to, never replaced."
    fun preview(games: Int, collections: Int, hidden: Int, theme: String, missingGames: Int, missingApps: Int): String =
        "Home: ${count(games, "game")}, ${count(collections, "collection")}, $hidden hidden · Theme: $theme · Not here yet: ${count(missingGames, "game")}, ${count(missingApps, "app")}"
    private fun count(number: Int, noun: String): String = "$number $noun${if (number == 1) "" else "s"}"
    const val signIn = "Sign in to RomM again."
    const val pair = "Pair your computers again."
    const val cores = "Pick RetroArch's cores folder again."
    const val eden = "Pick Eden's folder again."
    fun folders(names: String): String = "Pick these folders again: $names."
    fun saves(names: String): String = "Pick save folders again: $names."
    fun switchedRomm(host: String): String = "RomM: switches to $host; sign in again"
}

fun backupDialog(screen: HostScreen, undo: Boolean = false): DialogState {
    val buttons = listOf(DialogButton.BackUp, DialogButton.PickRestore) +
        (if (undo) listOf(DialogButton.UndoRestore) else emptyList()) + DialogButton.NotNow
    return DialogState(DialogKind.Backup, BackupCopy.title, BackupCopy.body, buttons, 0, buttons.lastIndex, screen)
}

fun backupDone(message: String, screen: HostScreen, restored: Boolean = false): DialogState = DialogState(
    DialogKind.BackupDone, if (restored) BackupCopy.restored else BackupCopy.title,
    message, listOf(DialogButton.Ok), 0, 0, screen,
)

fun restoreDialog(date: String, detail: String, screen: HostScreen): DialogState = DialogState(
    DialogKind.RestoreBackup, BackupCopy.confirm, BackupCopy.replacing(date),
    listOf(DialogButton.RestoreBackup, DialogButton.NotNow), 1, 1, screen, detail = detail,
)

/** Every saved game, including games hidden or taken off Home and collection-only memberships. */
fun backupHomeIds(board: HomeBoard): Set<String> = (
    board.slots.filterNotNull() + board.folders.values.flatMap { it.slots.filterNotNull() } +
        board.hidden + board.offHome + board.hiddenSlots.keys
    ).filter { it != HOME_ALL && it !in board.folders }.toSet()

data class BackupHomeSummary(val games: Int, val collections: Int, val hidden: Int, val missingGames: Int, val missingApps: Int)
fun backupHomeSummary(board: HomeBoard, available: Set<String>): BackupHomeSummary {
    val ids = backupHomeIds(board)
    return BackupHomeSummary(ids.count { !it.startsWith("android:") }, board.folders.values.count { it.collection },
        board.hidden.size, ids.count { !it.startsWith("android:") && it !in available },
        ids.count { it.startsWith("android:") && it !in available })
}

/**
 * A restored id waits for each source behind it to report once. No stand-in game is added
 * to the catalog: held places draw as gaps. Android installs have no restore hold.
 */
data class RestoreHolds(val sources: Map<String, Set<String>> = emptyMap()) {
    fun awaiting(id: String): Boolean = sources[id]?.isNotEmpty() == true
    fun reported(sources: Set<String>): RestoreHolds = RestoreHolds(this.sources.mapValues { it.value - sources }.filterValues { it.isNotEmpty() })
    fun without(id: String): RestoreHolds = copy(sources = sources - id)
    fun encode(): String = buildJsonObject {
        sources.toSortedMap().forEach { (id, from) -> put(id, JsonArray(from.sorted().map(::JsonPrimitive))) }
    }.toString()

    companion object {
        fun decode(raw: String?): RestoreHolds = try {
            RestoreHolds(Json.parseToJsonElement(raw ?: "{}").jsonObject.mapValues { (_, sources) ->
                sources.jsonArray.map { it.jsonPrimitive.content }.toSet()
            })
        } catch (_: Exception) { RestoreHolds() }

        fun forBoard(board: HomeBoard, aliases: Map<String, String>, folders: List<String>): RestoreHolds = RestoreHolds(
            backupHomeIds(board).mapNotNull { id ->
                val sources = sameGameIds(id, aliases).mapNotNull { sourceOf(it, folders) }.toSet()
                if (sources.isEmpty()) null else id to sources
            }.toMap(),
        )

        private fun sourceOf(id: String, folders: List<String>): String? = when {
            id.startsWith("android:") || id.startsWith(SAME_PREFIX) -> null
            id.startsWith("lib:local-folder:") -> {
                val uri = homeGameKey(id).orEmpty()
                folders.firstOrNull { uri == it || uri.startsWith("$it/document/") }?.let { "folder:$it" }
                    ?: uri.substringBefore("/document/", "").takeIf { it.startsWith("content://") }?.let { "folder:$it" }
                    ?: "local-folder"
            }
            id.startsWith("lib:moonlight:") -> "moonlight:" + homeGameKey(id).orEmpty().substringBefore('|')
            id.startsWith("lib:") -> id.removePrefix("lib:").substringBefore(':')
            id.startsWith("moonlight.") || id.startsWith("moonlight:") ||
                Regex("[0-9a-f-]{36}\\|[0-9]+").matches(id) -> "moonlight:" + id.substringBefore('|')
            id.startsWith("droiddeck.") -> "droiddeck"
            id.startsWith("gamenative.") -> "gamenative"
            else -> null
        }
    }
}
