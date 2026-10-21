package app.foldcade

import app.foldcade.language.Copy
import app.foldcade.language.FoldNotice
import app.foldcade.language.ThemeRollback

/** What R1 says after a pack on trial went back. One notice per archive. */
internal fun themeRollbackNotice(rollback: ThemeRollback, nowMillis: Long = System.currentTimeMillis()): FoldNotice {
    val (title, body) = if (rollback.restored) Copy.themeWentBackTitle to Copy.themeWentBackBody
        else Copy.themeTakenOutTitle to Copy.themeTakenOutBody
    val pack = listOfNotNull(rollback.name ?: rollback.file, rollback.release?.toString()).joinToString(" ")
    return FoldNotice(
        id = "foldcade:theme:${rollback.file}",
        title = title,
        body = "$pack · $body",
        postedMillis = nowMillis,
    )
}
