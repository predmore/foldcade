package app.foldcade

import app.foldcade.api.plugin.PluginException
import app.foldcade.language.SavePickerCopy
import app.foldcade.romm.RommUnauthenticated
import app.foldcade.romm.RommUnauthorized
import app.foldcade.romm.RommUnavailable

/** Distinguish network/authentication failures from a choice the server or save checks refused. */
internal fun serverSaveFailure(error: Throwable): String {
    val seen = HashSet<Throwable>()
    var cause: Throwable? = error
    while (cause != null && seen.add(cause)) {
        when (cause) {
            is SaveNeedsDownload -> return SavePickerCopy.downloadFirst
            is SaveNotPlaced -> return SavePickerCopy.unreadable
            is RommUnavailable, is RommUnauthenticated, is RommUnauthorized, is PluginException.NotAuthenticated -> return SavePickerCopy.unavailable
        }
        cause = cause.cause
    }
    return SavePickerCopy.failed
}
