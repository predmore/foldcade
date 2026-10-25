package app.foldcade

import app.foldcade.api.plugin.PluginException
import app.foldcade.host.PluginCallException
import app.foldcade.language.SavePickerCopy
import app.foldcade.romm.*
import org.junit.Assert.*
import org.junit.Test

class ServerSaveFailureTest {
    @Test fun onlyNetworkAndAuthenticationFailuresAskForAConnection() {
        for (error in listOf(RommUnavailable("offline"), RommUnauthenticated(), RommUnauthorized("expired"), PluginException.NotAuthenticated("Sign in"))) {
            assertEquals(SavePickerCopy.unavailable, serverSaveFailure(PluginCallException("romm", error)))
        }
        for (error in listOf(RommSlotMoved("changed"), RommResponseException("hash mismatch"), PluginException.Unavailable("conflict"), PluginException.SaveChoiceRefused("refused"))) {
            assertEquals(SavePickerCopy.failed, serverSaveFailure(PluginCallException("romm", error)))
        }
        assertEquals(SavePickerCopy.downloadFirst, serverSaveFailure(SaveNeedsDownload()))
        assertEquals(SavePickerCopy.unreadable, serverSaveFailure(SaveNotPlaced()))
        assertFalse(SavePickerCopy.failed.contains("Connect"))
    }
}
