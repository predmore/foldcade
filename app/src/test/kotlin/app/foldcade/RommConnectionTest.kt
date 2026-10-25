package app.foldcade

import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.CredentialLookup
import app.foldcade.api.plugin.CredentialStore
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.SignedInBackend
import app.foldcade.language.Effect
import app.foldcade.language.PanelLevel
import app.foldcade.language.Side
import app.foldcade.language.Row
import app.foldcade.language.SettingsCategory
import app.foldcade.language.settingsCategories
import app.foldcade.language.settingsRows
import app.foldcade.language.leftRows
import app.foldcade.language.HostScreen
import app.foldcade.plugins.romm.RommEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RommConnectionTest {
    @Test
    fun editingTheConnectionKeepsItsIdentityAndReturnsToItsActions() = runBlocking {
        val session = SessionStore(MemoryPrefs())
        val credentials = MemoryCredentialStore()
        saveRommConnection(session, credentials, "https://one.example", "rmm_one")
        val shell = ShellController(session, host(credentials))
        shell.setSignedIn(listOf(SignedInBackend("romm", "RomM")))
        // The sources live on the Settings page's Library rows.
        shell.previewIsland(Side.Left)
        shell.touchPanel(leftRows().indexOf(Row.Settings), HostScreen.Top)
        shell.touchPanel(leftRows().indexOf(Row.Settings), HostScreen.Top)
        shell.touchSettingsCategory(settingsCategories(shell.model).indexOf(SettingsCategory.Library), HostScreen.Top)
        val library = settingsRows(SettingsCategory.Library, shell.model)
        assertNull(shell.touchSettingsRow(library.indexOfFirst { it is Row.RommConnection && it.pluginId == "romm" }, HostScreen.Top))
        assertEquals(PanelLevel.RommConnection, shell.model.panel?.level)
        assertFalse(shell.model.connectOpen)

        assertEquals(Effect.EditRommConnection, shell.touchPanel(0, HostScreen.Top))
        assertTrue(shell.editRommConnection())
        assertEquals("https://one.example", shell.model.connectOrigin)
        shell.editOrigin("https://changed.example")
        saveRommConnection(session, credentials, shell.model.connectOrigin, "rmm_changed")
        shell.closeConnect()
        assertEquals(PanelLevel.RommConnection, shell.model.panel?.level)
        assertEquals("romm", shell.model.panel?.connection?.pluginId)
        assertEquals("https://changed.example", session.rommOrigin())
        assertEquals("rmm_changed", token(credentials, "access-token"))

        shell.touchPanel(1, HostScreen.Top)
        assertEquals(Effect.ForgetCredentials("romm"), shell.touchPanel(1, HostScreen.Top))
        removeRommServer(credentials, session)
        assertNull(session.rommOrigin())
        assertTrue(credentials.lookup("romm", "access-token") is CredentialLookup.Absent)
    }

    @Test
    fun theSingleServerKeepsItsOriginalCredentialAndRecoveryFlow() = runBlocking {
        val session = SessionStore(MemoryPrefs())
        val credentials = MemoryCredentialStore()
        saveRommConnection(session, credentials, "https://one.example", "rmm_one")
        val shell = ShellController(session, host(credentials))
        val recovery = readRommRecovery(credentials, session.rommOrigin())
        assertFalse(recovery.askToSignIn)
        assertEquals(listOf(SignedInBackend("romm", "RomM")), recovery.backends)
        shell.askToSignInAgain()
        assertNull(shell.model.dialog?.detail)
        shell.dismissDialog()
        shell.openRommRecovery()
        assertEquals("https://one.example", shell.model.connectOrigin)
        saveRommConnection(session, credentials, "https://one.example", "rmm_new")
        assertEquals("rmm_new", token(credentials, "access-token"))
    }

    @Test
    fun signOutForgetsOnlyTheAccessTokenAndAddress() = runBlocking {
        val session = SessionStore(MemoryPrefs())
        val credentials = MemoryCredentialStore()
        saveRommConnection(session, credentials, "https://one.example", "rmm_one")
        credentials.put("romm", "unrelated-key", Credential.ApiToken("leave"))
        removeRommServer(credentials, session)
        assertNull(session.rommOrigin())
        assertTrue(credentials.lookup("romm", "access-token") is CredentialLookup.Absent)
        assertEquals("leave", token(credentials, "unrelated-key"))
    }

    @Test
    fun addingWithoutAServerStartsWithAnEmptyAddress() {
        val session = SessionStore(MemoryPrefs())
        val shell = ShellController(session, host(MemoryCredentialStore()))
        shell.openAddRomm()
        assertEquals("", shell.model.connectOrigin)
        assertTrue(shell.model.connectOpen)
    }

    @Test
    fun anOpenConnectionFormKeepsTheTokenBeingEdited() {
        val session = SessionStore(MemoryPrefs()).apply { setRommOrigin("https://one.example") }
        val shell = ShellController(session, host(MemoryCredentialStore()))
        shell.openConnect("https://one.example")
        shell.editToken("being-edited")
        shell.askToSignInAgain()
        shell.showQueuedPrompt()
        assertTrue(shell.model.connectOpen)
        assertNull(shell.model.dialog)
        assertEquals("being-edited", shell.connectToken)
    }

    private fun host(credentials: CredentialStore): PluginHost {
        val host = PluginHost(Dispatchers.Unconfined, credentials)
        host.register(RommEntry())
        return host
    }

    private suspend fun token(credentials: CredentialStore, key: String): String {
        val present = credentials.lookup(RommCredentials.PLUGIN_ID, key) as CredentialLookup.Present
        return (present.credential as Credential.ApiToken).value
    }
}
