package app.foldcade

import app.foldcade.api.plugin.Player
import app.foldcade.language.FirmwareCopy
import app.foldcade.language.FoldNotice

/** Report only a new write; staged firmware may still need the emulator's own import. */
internal fun firmwareWrittenNotice(result: FirmwareResult, player: Player, platform: String): FoldNotice? {
    if (result.written.isEmpty() || result.blocksLaunch) return null
    return FoldNotice(
        id = "firmware:${player.id}",
        title = if (result.requiresImport) FirmwareCopy.importPs2Title else FirmwareCopy.ready(platform),
        body = if (result.requiresImport) FirmwareCopy.importPs2(result.place) else FirmwareCopy.readyBody,
        postedMillis = System.currentTimeMillis(), packageName = player.packageNames.firstOrNull(),
    )
}

/** Explicit X/Clear all stays quiet for this process; automatic cleanup is not a refusal. */
internal class FirmwareNoticeDismissals {
    private val dismissed = mutableSetOf<String>()

    fun allows(notice: FoldNotice): Boolean = notice.firmwarePlayerId == null || notice.id !in dismissed

    fun dismiss(notice: FoldNotice?, byUser: Boolean) {
        if (byUser && notice?.firmwarePlayerId != null) dismissed += notice.id
    }
}
