package app.foldcade.language

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Which of the three actions has the controller. [KeepBoth] is the one that starts focused. */
enum class SaveConflictFocus {
    Local,
    KeepBoth,
    Remote,
}

/**
 * What the player asked the shell to do.
 * Each one keeps both copies. [DecideLater] asks again on the next launch.
 */
enum class SaveConflictAction {
    KeepLocal,
    KeepRemote,
    KeepBoth,
    DecideLater,
}

/** One copy as the bottom screen shows it. */
data class SaveConflictSide(
    val source: String,
    val updatedAtMillis: Long,
    val sizeBytes: Long,
)

/**
 * The bottom-screen choice between two saves.
 * [preview] is a debug capture: choosing does not touch a library.
 */
data class SaveConflictPrompt(
    val id: String,
    val libraryId: String,
    val gameLabel: String,
    val local: SaveConflictSide,
    val remote: SaveConflictSide,
    val focus: SaveConflictFocus = SaveConflictFocus.KeepBoth,
    val preview: Boolean = false,
)

/**
 * Left and right move between the two copies, and down returns to Keep both.
 * A activates the focused action. B decides later.
 * The prompt stays until an action is chosen, and choosing does not drop either copy from it.
 */
fun applySaveConflict(
    prompt: SaveConflictPrompt,
    meaning: Meaning,
): Pair<SaveConflictPrompt, SaveConflictAction?> {
    val focus = prompt.focus
    return when (meaning) {
        Meaning.MoveLeft -> prompt.copy(
            focus = when (focus) {
                SaveConflictFocus.Remote -> SaveConflictFocus.Local
                SaveConflictFocus.KeepBoth -> SaveConflictFocus.Local
                SaveConflictFocus.Local -> SaveConflictFocus.Local
            },
        ) to null
        Meaning.MoveRight -> prompt.copy(
            focus = when (focus) {
                SaveConflictFocus.Local -> SaveConflictFocus.Remote
                SaveConflictFocus.KeepBoth -> SaveConflictFocus.Remote
                SaveConflictFocus.Remote -> SaveConflictFocus.Remote
            },
        ) to null
        Meaning.MoveUp -> prompt.copy(
            focus = when (focus) {
                SaveConflictFocus.KeepBoth -> SaveConflictFocus.Local
                else -> focus
            },
        ) to null
        Meaning.MoveDown -> prompt.copy(
            focus = when (focus) {
                SaveConflictFocus.KeepBoth -> SaveConflictFocus.KeepBoth
                else -> SaveConflictFocus.KeepBoth
            },
        ) to null
        Meaning.Activate -> prompt to when (focus) {
            SaveConflictFocus.Local -> SaveConflictAction.KeepLocal
            SaveConflictFocus.Remote -> SaveConflictAction.KeepRemote
            SaveConflictFocus.KeepBoth -> SaveConflictAction.KeepBoth
        }
        Meaning.Back -> prompt to SaveConflictAction.DecideLater
        else -> prompt to null
    }
}

/**
 * Spacing for the bottom-screen choice, in dp. The two cards always share one [cardWidth].
 * A short panel, such as Thor's 1240 × 1080 bottom display at about 412 dp, is [compact],
 * so both cards, Keep both and Decide later fit without scrolling.
 */
data class SaveConflictFit(val edge: Float, val gap: Float, val cardWidth: Float, val compact: Boolean)

fun saveConflictFit(width: Float, height: Float): SaveConflictFit {
    val compact = height < 520f
    val edge = if (compact) 16f else 28f
    val gap = if (compact) 14f else 28f
    val row = (width - edge * 2f).coerceIn(0f, 600f)
    return SaveConflictFit(edge, gap, ((row - gap) / 2f).coerceAtLeast(0f), compact)
}

/** The two copies a debug capture shows, so the bottom screen can be opened without a server. */
fun previewSaveConflict(): SaveConflictPrompt = SaveConflictPrompt(
    id = "preview",
    libraryId = "romm",
    gameLabel = "Bravely Second",
    local = SaveConflictSide(
        source = "This device · Azahar",
        updatedAtMillis = Instant.parse("2026-04-18T09:12:00Z").toEpochMilli(),
        sizeBytes = 32_768,
    ),
    remote = SaveConflictSide(
        source = "Steam Deck · Azahar",
        updatedAtMillis = Instant.parse("2026-04-18T10:40:00Z").toEpochMilli(),
        sizeBytes = 31_200,
    ),
    preview = true,
)

/** When a save was written, in the device's date order and clock. */
fun formatSaveWhen(millis: Long, style: DateStyle, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(millis).atZone(zone)
    return "${style.monthDayYear(at)}, ${style.time(at)}"
}

fun formatSaveSize(bytes: Long): String {
    val size = bytes.coerceAtLeast(0L)
    if (size < 1024L) return "$size B"
    val kb = size / 1024.0
    if (kb < 1024.0) {
        return if (size % 1024L == 0L) "${size / 1024L} KB" else String.format(Locale.US, "%.1f KB", kb)
    }
    return String.format(Locale.US, "%.1f MB", kb / 1024.0)
}
