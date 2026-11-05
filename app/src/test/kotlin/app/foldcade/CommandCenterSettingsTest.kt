package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class CommandCenterSettingsTest {
    @Test fun anActivityBackedSettingsPageKeepsItsParentOnTheSameScreen() {
        val store = SessionStore(MemoryPrefs())
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        store.afterSessionChanged = shell::noteSessionChanged
        shell.onMeaning(Meaning.Menu, HostScreen.Top)
        val settings = shell.model.settings
        store.place(Panel.Top, ExternalApp("licenses", foldcadePage = true))
        assertEquals(settings, shell.model.settings)
        assertEquals(Surface.CommandCenter, store.session.surfaceOn(Panel.Bottom))
        assertFalse(commandCenterDimmed(store.session, Panel.Bottom, awake = null))
        store.update { it.foldcadeResumed(Panel.Top) }
        assertEquals(settings, shell.model.settings)
    }
    @Test fun startBesideATopGameOpensSettingsBelowAndStartClosesIt() {
        val store = SessionStore(MemoryPrefs())
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        store.afterSessionChanged = shell::noteSessionChanged
        store.place(Panel.Top, ExternalApp("game"))
        val center = CommandCenter(store, CoroutineScope(Dispatchers.Unconfined), { null }, {}, shell::holdsCommandCenter)
        assertFalse(center.onKey(Panel.Bottom, Meaning.Menu) { true })
        assertFalse(topMenuKeyBlocked(Meaning.Menu, HomeKeys.Idle, store.session.islandsOpen()))
        shell.onMeaning(Meaning.Menu, HostScreen.Bottom)
        assertEquals(HostScreen.Bottom, shell.model.settings?.screen)
        assertTrue(shell.holdsCommandCenter())
        assertFalse(center.onKey(Panel.Bottom, Meaning.Menu) { false })
        shell.onMeaning(Meaning.Menu, HostScreen.Bottom)
        assertNull(shell.model.settings)
        assertEquals("game", store.session.topApp?.id)
    }

    @Test fun sessionChangesReturnSettingsToThePreferredScreenWithoutAnotherKey() {
        val store = SessionStore(MemoryPrefs())
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        store.afterSessionChanged = shell::noteSessionChanged
        shell.onMeaning(Meaning.Menu, HostScreen.Top)
        store.place(Panel.Top, ExternalApp("licenses"))
        assertEquals(HostScreen.Bottom, shell.model.settings?.screen)
        store.update { it.home(Panel.Top) }
        assertEquals(HostScreen.Top, shell.model.settings?.screen)
    }
}
