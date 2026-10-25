package app.foldcade.language

/**
 * One notice in R1. Android's notifications arrive here once the user grants
 * notification access; Foldcade adds its own, such as a save that waits to upload.
 */
data class FoldNotice(
    val id: String,
    val title: String,
    val body: String,
    /** The app that posted it. Its icon draws beside the notice. */
    val packageName: String? = null,
    val postedMillis: Long = 0L,
    /** A tells the app to open it. Foldcade's own notices only have their text. */
    val openable: Boolean = false,
    /** Y removes it. Android marks some notifications as not clearable. */
    val dismissable: Boolean = true,
    /** A standing save notice opens this player's Settings fix. */
    val savePlayerId: String? = null,
)

object NoticeCopy {
    const val access = "Notifications"
    const val accessValue = "Allow"
    const val accessTitle = "Show notifications in R1"
    const val accessBody =
        "Android asks once. Turn on notification access for Foldcade, then come back. They stay on this device."
    const val clearAll = "Clear all"
    const val dismiss = "Dismiss"
}

/** How long ago, in the fewest characters: now, 5m, 3h, 2d. */
fun noticeAge(postedMillis: Long, nowMillis: Long): String {
    val minutes = ((nowMillis - postedMillis).coerceAtLeast(0L)) / 60_000L
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> "${minutes / (24 * 60)}d"
    }
}

/** Newest first. Two from the same moment keep a stable order. */
fun orderedNotices(notices: List<FoldNotice>): List<FoldNotice> =
    notices.sortedWith(compareByDescending<FoldNotice> { it.postedMillis }.thenBy { it.id })

/** Asked from the Settings row while Foldcade cannot read notifications. */
fun noticeAccessPrompt(screen: HostScreen = HostScreen.Top): DialogState = DialogState(
    kind = DialogKind.NoticeAccess,
    title = NoticeCopy.accessTitle,
    body = NoticeCopy.accessBody,
    buttons = listOf(DialogButton.Allow, DialogButton.NotNow),
    index = 0,
    safeIndex = 1,
    screen = screen,
)

/** A Foldcade notice has no app to open, so A shows all of its text. */
fun noticeReader(notice: FoldNotice, screen: HostScreen = HostScreen.Top): DialogState = DialogState(
    kind = DialogKind.Notice,
    title = notice.title,
    body = notice.body,
    buttons = listOf(DialogButton.Ok),
    index = 0,
    safeIndex = 0,
    screen = screen,
)
