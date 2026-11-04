package app.foldcade

import app.foldcade.language.FoldNotice

/** Explicit X/Clear all stays quiet for this process; automatic cleanup is not a refusal. */
internal class FirmwareNoticeDismissals {
    private val dismissed = mutableSetOf<String>()

    fun allows(notice: FoldNotice): Boolean = notice.firmwarePlayerId == null || notice.id !in dismissed

    fun dismiss(notice: FoldNotice?, byUser: Boolean) {
        if (byUser && notice?.firmwarePlayerId != null) dismissed += notice.id
    }
}
