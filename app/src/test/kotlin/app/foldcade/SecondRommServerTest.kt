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
import app.foldcade.language.DialogKind
import app.foldcade.language.homePrompt
import app.foldcade.language.HostScreen
import app.foldcade.language.Meaning
import app.foldcade.plugins.romm.RommEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondRommServerTest {
    @Test
    fun editingTheSecondConnectionKeepsTheFirstAndReturnsToItsActions() = runBlocking {
        val session = SessionStore(MemoryPrefs())
        val credentials = MemoryCredentialStore()
        saveRommConnection(session, credentials, "romm", "https://one.example", "rmm_one")
        saveRommConnection(session, credentials, "romm#2", "https://two.example", "rmm_two")
        val shell = ShellController(session, host(credentials))
        shell.setSignedIn(rommSignedInBackends(session.rommOrigin(), session.rommInstances()))
        // The sources live on the Settings page's Library rows.
        shell.previewIsland(Side.Left)
        shell.touchPanel(leftRows().indexOf(Row.Settings), HostScreen.Top)
        shell.touchPanel(leftRows().indexOf(Row.Settings), HostScreen.Top)
        shell.touchSettingsCategory(settingsCategories(shell.model).indexOf(SettingsCategory.Library), HostScreen.Top)
        val library = settingsRows(SettingsCategory.Library, shell.model)
        assertNull(shell.touchSettingsRow(library.indexOfFirst { it is Row.RommConnection && it.pluginId == "romm#2" }, HostScreen.Top))
        assertEquals(PanelLevel.RommConnection, shell.model.panel?.level)
        assertFalse(shell.model.connectOpen)

        assertEquals(Effect.EditRommConnection("romm#2"), shell.touchPanel(0, HostScreen.Top))
        assertTrue(shell.editRommConnection("romm#2"))
        assertEquals("https://two.example", shell.model.connectOrigin)
        assertEquals("romm#2", shell.connectRommId)
        shell.editOrigin("https://changed.example")
        saveRommConnection(session, credentials, shell.connectRommId, shell.model.connectOrigin, "rmm_changed")
        shell.closeConnect()
        assertEquals(PanelLevel.RommConnection, shell.model.panel?.level)
        assertEquals("romm#2", shell.model.panel?.connection?.pluginId)
        assertEquals("https://one.example", session.rommOrigin())
        assertEquals("rmm_one", token(credentials, "access-token"))
        assertEquals(listOf("romm#2" to "https://changed.example"), session.rommInstances())

        shell.touchPanel(1, HostScreen.Top)
        assertEquals(Effect.ForgetCredentials("romm#2"), shell.touchPanel(1, HostScreen.Top))
        removeRommServer(credentials, session, "romm#2")
        assertEquals(listOf("romm" to "https://one.example"), session.rommServers())
        assertEquals("rmm_one", token(credentials, "access-token"))
    }

    @Test
    fun theAddActionUsesTheFirstVacantDefaultSlotAndKeepsOtherConnections() {
        val session = SessionStore(MemoryPrefs())
        val shell = ShellController(session, host(MemoryCredentialStore()))
        shell.openAddRomm()
        assertFalse(shell.model.connectAnother)
        assertEquals("", shell.model.connectOrigin)
        session.setRommOrigin("https://one.example")
        shell.openAddRomm()
        assertTrue(shell.model.connectAnother)
        assertEquals("", shell.model.connectOrigin)
        session.setRommInstance("romm#2", "https://two.example")
        session.clearRommOrigin()
        shell.openAddRomm()
        assertFalse(shell.model.connectAnother)
        assertEquals(listOf("romm#2" to "https://two.example"), session.rommServers())
    }

    @Test
    fun recoveryRepairsTheSecondServerWithoutChangingTheDefault() = runBlocking {
        val credentials = MemoryCredentialStore()
        val session = SessionStore(MemoryPrefs())
        session.setRommOrigin("https://one.example")
        session.setRommInstance("romm#2", "https://two.example")
        credentials.put("romm", "access-token", Credential.ApiToken("rmm_one"))
        credentials.put("romm", "access-token@romm#2", Credential.ApiToken("rmm_expired"))
        val shell = ShellController(session, host(credentials))

        shell.askToSignInAgain("romm#2")
        assertEquals(DialogKind.ReLogin, shell.model.dialog?.kind)
        assertEquals("two.example", shell.model.dialog?.detail)
        // A second failure must not replace the target of the prompt already shown.
        shell.askToSignInAgain("romm")
        shell.dismissDialog()
        shell.openRommRecovery()
        assertEquals("https://two.example", shell.model.connectOrigin)
        assertEquals("romm#2", shell.connectRommId)
        assertFalse(shell.model.connectAnother)

        saveRommConnection(session, credentials, shell.connectRommId, shell.model.connectOrigin, "rmm_repaired")
        assertEquals("rmm_one", token(credentials, "access-token"))
        assertEquals("rmm_repaired", token(credentials, "access-token@romm#2"))
        assertEquals(listOf("romm" to "https://one.example", "romm#2" to "https://two.example"), session.rommServers())
    }

    @Test
    fun unreadableCredentialsLeaveTheOtherConnectionSignedIn() = runBlocking {
        val memory = MemoryCredentialStore()
        memory.put("romm", "access-token", Credential.ApiToken("rmm_one"))
        memory.put("romm", "access-token@romm#2", Credential.ApiToken("rmm_two"))
        val servers = listOf("romm" to "https://one.example", "romm#2" to "https://two.example")
        for ((failedId, _) in servers) {
            val credentials = object : CredentialStore by memory {
                override suspend fun lookup(pluginId: String, key: String): CredentialLookup =
                    if (key == app.foldcade.plugins.romm.rommCredentialKey(failedId)) CredentialLookup.Unreadable
                    else memory.lookup(pluginId, key)
            }
            val recovery = readRommRecovery(credentials, servers)
            assertTrue(recovery.askToSignIn)
            assertEquals(failedId, recovery.rommInstanceId)
            assertEquals(servers.filter { it.first != failedId }.map { it.first }, recovery.backends.map { it.pluginId })
        }
    }

    @Test
    fun recoveryWaitsForAnOpenFormAndKeepsItsConnection() {
        val session = SessionStore(MemoryPrefs())
        session.setRommOrigin("https://one.example")
        session.setRommInstance("romm#2", "https://two.example")
        val shell = ShellController(session, host(MemoryCredentialStore()))
        shell.openConnect("https://one.example")
        shell.editToken("being-edited")
        shell.askToSignInAgain("romm#2")
        shell.showQueuedPrompt()
        assertTrue(shell.model.connectOpen)
        assertNull(shell.model.dialog)
        assertEquals("romm", shell.connectRommId)
        assertEquals("being-edited", shell.connectToken)
        shell.onMeaning(Meaning.Back, HostScreen.Top)
        assertEquals(DialogKind.ReLogin, shell.model.dialog?.kind)
        shell.dismissDialog()
        shell.openRommRecovery()
        assertEquals("romm#2", shell.connectRommId)
    }

    @Test
    fun queuedRecoveryDoesNotRecreateARemovedServer() {
        val session = SessionStore(MemoryPrefs())
        session.setRommInstance("romm#2", "https://two.example")
        val shell = ShellController(session, host(MemoryCredentialStore()))
        shell.present(homePrompt())
        shell.askToSignInAgain("romm#2")
        session.removeRommInstance("romm#2")
        shell.dismissDialog()
        shell.showQueuedPrompt()
        shell.dismissDialog()
        shell.openRommRecovery()
        assertFalse(shell.model.connectOpen)
        assertTrue(session.rommServers().isEmpty())
    }

    @Test
    fun theSingleServerKeepsItsOriginalCredentialAndRecoveryFlow() = runBlocking {
        val session = SessionStore(MemoryPrefs())
        val credentials = MemoryCredentialStore()
        saveRommConnection(session, credentials, "romm", "https://one.example", "rmm_one")
        val shell = ShellController(session, host(credentials))
        val recovery = readRommRecovery(credentials, session.rommServers())
        assertFalse(recovery.askToSignIn)
        assertEquals(listOf(SignedInBackend("romm", "RomM")), recovery.backends)
        shell.askToSignInAgain()
        assertNull(shell.model.dialog?.detail)
        shell.dismissDialog()
        shell.openRommRecovery()
        assertEquals("https://one.example", shell.model.connectOrigin)
        assertEquals("romm", shell.connectRommId)
        saveRommConnection(session, credentials, shell.connectRommId, "https://one.example", "rmm_new")
        assertEquals("rmm_new", token(credentials, "access-token"))
        assertTrue(session.rommInstances().isEmpty())
    }

    @Test
    fun addingASecondServerMintsTheInstanceAndStoresTheOrigin() = runBlocking {
        val credentials = MemoryCredentialStore()
        val host = host(credentials)
        val session = SessionStore(MemoryPrefs())
        session.setRommOrigin("https://one.example")
        credentials.put(RommCredentials.PLUGIN_ID, "access-token", Credential.ApiToken("rmm_one"))

        val added = addRommServer(
            host = host,
            session = session,
            credentials = credentials,
            origin = "https://two.example",
            token = "rmm_two",
            defaultOrigin = session.rommOrigin(),
            extras = session.rommInstances(),
            pendingInstanceId = null,
        )

        assertEquals(RommServerAdd.Added("romm#2"), added)
        assertEquals("romm#2", host.library("romm#2")?.instanceId)
        assertEquals("romm", host.library("romm#2")?.id)
        assertEquals(listOf("romm#2" to "https://two.example"), session.rommInstances())
        assertEquals("https://one.example", session.rommOrigin())
        assertEquals("rmm_two", token(credentials, "access-token@romm#2"))
        assertEquals("rmm_one", token(credentials, "access-token"))
        assertEquals(
            listOf(SignedInBackend("romm", "RomM"), SignedInBackend("romm#2", "two.example")),
            rommSignedInBackends(session.rommOrigin(), session.rommInstances()),
        )
    }

    @Test
    fun theSameCanonicalOriginIsRefusedBeforeASecondInstance() = runBlocking {
        val credentials = MemoryCredentialStore()
        val host = host(credentials)
        val session = SessionStore(MemoryPrefs())
        session.setRommOrigin("https://one.example/")

        val againstDefault = addRommServer(
            host, session, credentials,
            origin = "https://one.example/api",
            token = "rmm_two",
            defaultOrigin = session.rommOrigin(),
            extras = session.rommInstances(),
            pendingInstanceId = null,
        )
        assertEquals(RommServerAdd.SameServer, againstDefault)
        assertNull(host.library("romm#2"))
        assertTrue(session.rommInstances().isEmpty())
        assertTrue(credentials.keys(RommCredentials.PLUGIN_ID).isEmpty())

        session.setRommInstance("romm#2", "https://two.example")
        val againstExtra = addRommServer(
            host, session, credentials,
            origin = "https://two.example/api",
            token = "rmm_three",
            defaultOrigin = "https://one.example",
            extras = session.rommInstances(),
            pendingInstanceId = null,
        )
        assertEquals(RommServerAdd.SameServer, againstExtra)
        assertNull(host.library("romm#2"))
        assertTrue(credentials.keys(RommCredentials.PLUGIN_ID).isEmpty())
    }

    @Test
    fun removingTheSecondServerForgetsThatKeyOnly() = runBlocking {
        val delegate = MemoryCredentialStore()
        val credentials = object : CredentialStore by delegate {
            var forgotPlugin = false
            override suspend fun forgetPlugin(pluginId: String) {
                forgotPlugin = true
                delegate.forgetPlugin(pluginId)
            }
        }
        val host = host(credentials)
        val session = SessionStore(MemoryPrefs())
        session.setRommOrigin("https://one.example")
        credentials.put(RommCredentials.PLUGIN_ID, "access-token", Credential.ApiToken("rmm_one"))
        val added = addRommServer(
            host, session, credentials,
            origin = "https://two.example",
            token = "rmm_two",
            defaultOrigin = session.rommOrigin(),
            extras = emptyList(),
            pendingInstanceId = null,
        )
        assertEquals(RommServerAdd.Added("romm#2"), added)

        removeRommServer(credentials, session, "romm#2")

        assertFalse(credentials.forgotPlugin)
        assertEquals("rmm_one", token(credentials, "access-token"))
        assertTrue(credentials.lookup(RommCredentials.PLUGIN_ID, "access-token@romm#2") is CredentialLookup.Absent)
        assertTrue(session.rommInstances().isEmpty())
        assertEquals("https://one.example", session.rommOrigin())
        assertEquals(
            listOf(SignedInBackend("romm", "RomM")),
            mergeSignedIn(
                listOf(SignedInBackend("romm", "RomM"), SignedInBackend("romm#2", "two.example")),
                rommSignedInBackends(session.rommOrigin(), session.rommInstances()),
            ),
        )
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
