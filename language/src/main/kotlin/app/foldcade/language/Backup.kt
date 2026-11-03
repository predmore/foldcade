package app.foldcade.language

import kotlinx.serialization.json.*

object BackupCopy {
    const val title = "Backup"
    const val body = "Home, collections, hidden games, settings, themes and play time, in one file. Not saves or sign-ins."
    const val backUp = "Back up"
    const val restore = "Restore"
    const val undo = "Undo restore"
    const val redo = "Redo restore"
    const val undoConfirm = "Undo restore?"
    const val redoConfirm = "Redo restore?"
    const val saved = "Backed up."
    fun saved(skippedThemes: Int, result: String = saved): String = when (skippedThemes) {
        0 -> result
        1 -> "$result 1 theme was too large to include."
        else -> "$result $skippedThemes themes were too large to include."
    }
    const val notSaved = PlayCopy.notSaved
    const val unreadable = PlayCopy.unreadable
    const val foreign = "That file isn't a Foldcade backup."
    const val newer = "That backup is from a newer Foldcade."
    const val restoreFailed = "The backup couldn't be restored."
    const val restoreUnfinished = "The restore didn't finish. Your setup is as it was."
    const val confirm = "Restore this backup?"
    const val restored = "Restored"
    const val restoredDone = "All set."
    /** After Undo restore: the setup from before is back, so the title says that rather than Restored. */
    const val undone = "Restore undone"
    const val undoneDone = "Your setup is back as it was."
    const val pickAgain = "Pick again"
    fun replacing(date: String): String = "Replaces your Home, collections, hidden games, settings and theme with the ones in this backup, from $date. Play time from both is kept."
    fun undoing(date: String): String = "Puts back the setup you had before, from $date. Play time from both is kept."
    fun redoing(date: String): String = "Puts back the restored setup, from $date. Play time from both is kept."
    /**
     * What the backup holds, one line, then what is not on this device yet, in a sentence:
     * "43 games on Home · 2 collections · Theme: Afterglow", then "38 games aren't here yet.
     * They come back once their library reconnects." Counts of nothing are left out.
     */
    fun preview(games: Int, collections: Int, hidden: Int, theme: String, missingGames: Int, missingApps: Int): String {
        val home = listOfNotNull(
            "${count(games, "game")} on Home",
            count(collections, "collection").takeIf { collections > 0 },
            "$hidden hidden".takeIf { hidden > 0 },
            "Theme: $theme",
        ).joinToString(" · ")
        val missing = listOfNotNull(
            count(missingGames, "game").takeIf { missingGames > 0 },
            count(missingApps, "app").takeIf { missingApps > 0 },
        )
        if (missing.isEmpty()) return home
        val one = missingGames + missingApps == 1
        val back = when {
            missingApps == 0 -> if (one) "It comes back once its library reconnects." else "They come back once their library reconnects."
            missingGames == 0 -> if (one) "It comes back once it’s installed." else "They come back once they’re installed."
            else -> "They come back once their library reconnects, or once they’re installed."
        }
        return "$home\n${missing.joinToString(" and ")} ${if (one) "isn’t" else "aren’t"} here yet. $back"
    }
    private fun count(number: Int, noun: String): String = "$number $noun${if (number == 1) "" else "s"}"
    const val signIn = "Sign in to RomM again."
    const val pair = "Pair your computers again."
    const val cores = "Pick RetroArch's cores folder again."
    const val eden = "Pick Eden's folder again."
    fun folders(names: String): String = "Pick these folders again in Settings › Library: $names."
    fun saves(names: String): String = "Pick save folders again: $names."
    fun switchedRomm(host: String): String = "RomM changes to $host. Sign in after the restore."
    fun connectRomm(host: String): String = "After the restore, sign in to RomM at $host."
    fun waiting(source: String): String = "Waiting for $source"
}

enum class RestoreAction { Restore, Undo, Redo }

/** Missing sources name the recovery step, without inventing a game tile. */
fun restoreWaitingTitle(sources: Set<String>, folders: Map<String, String>, computers: Map<String, String>): String? {
    val name = sources.sorted().firstNotNullOfOrNull { source -> when {
        source.startsWith("folder:") -> folders[source.removePrefix("folder:")]
        source == "romm" -> "RomM"
        source.startsWith("moonlight:") -> computers.entries.firstOrNull { it.key.equals(source.removePrefix("moonlight:"), ignoreCase = true) }?.value
        source == "gamenative" -> "GameNative"
        source == "droiddeck" -> "DroidDeck"
        else -> null
    } }?.takeIf { it.isNotBlank() }
    return name?.let(BackupCopy::waiting)
}

/** A file flow opened from Settings stays with its page, whichever panel delivered the key. */
fun PickerModel.backupDialogScreen(from: HostScreen): HostScreen = settings?.screen ?: from

/**
 * Back up, Restore, Undo or Redo, and Play history. B closes it, so it has no Not now: five
 * buttons did not fit the bottom screen's narrower Settings on one line.
 */
fun backupDialog(screen: HostScreen, undo: Boolean = false, redo: Boolean = false): DialogState {
    val buttons = listOf(DialogButton.BackUp, DialogButton.PickRestore) +
        (if (undo) listOf(if (redo) DialogButton.RedoRestore else DialogButton.UndoRestore) else emptyList()) + listOf(DialogButton.PlayHistory)
    return DialogState(DialogKind.Backup, BackupCopy.title, BackupCopy.body, buttons, 0, BACK_CLOSES, screen)
}

/** [undone]: the restore just finished was Undo restore, so it names that rather than Restored. */
fun backupDone(message: String, screen: HostScreen, restored: Boolean = false, library: Boolean = false, undone: Boolean = false): DialogState = DialogState(
    DialogKind.BackupDone, if (restored) (if (undone) BackupCopy.undone else BackupCopy.restored) else BackupCopy.title,
    if (restored && message.isBlank()) (if (undone) BackupCopy.undoneDone else BackupCopy.restoredDone) else message,
    if (library) listOf(DialogButton.Library, DialogButton.Ok) else listOf(DialogButton.Ok), 0, if (library) 1 else 0, screen,
)

fun restoreDialog(date: String, detail: String, screen: HostScreen, action: RestoreAction = RestoreAction.Restore): DialogState {
    val (title, body, button) = when (action) {
        RestoreAction.Restore -> Triple(BackupCopy.confirm, BackupCopy.replacing(date), DialogButton.RestoreBackup)
        RestoreAction.Undo -> Triple(BackupCopy.undoConfirm, BackupCopy.undoing(date), DialogButton.UndoRestore)
        RestoreAction.Redo -> Triple(BackupCopy.redoConfirm, BackupCopy.redoing(date), DialogButton.RedoRestore)
    }
    return DialogState(DialogKind.RestoreBackup, title, body, listOf(button, DialogButton.NotNow), 1, 1, screen, detail = detail)
}

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
