package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.HostScreen
import app.foldcade.language.Meaning
import app.foldcade.language.MoonlightDiscoveredApp
import app.foldcade.language.MoonlightPlacement
import app.foldcade.language.MoonlightSource
import app.foldcade.language.MoonlightStoredApp
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonlightImportStoreTest {
    @Test
    fun sourceImportAndPlacementsSurviveAReload() {
        val prefs = MemoryPrefs()
        val store = SessionStore(prefs)
        assertEquals(MoonlightSource.PinnedShortcuts, store.moonlightSource())
        assertFalse(store.moonlightImportConfirmed())
        assertFalse(store.moonlightImportSettled())
        assertTrue(store.moonlightPlacements().isEmpty())

        val host = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6"
        store.setMoonlightSource(MoonlightSource.ImportedList)
        store.setMoonlightImport(listOf(MoonlightStoredApp(host, "123", "Celeste")))
        store.setMoonlightImportSettled()
        store.setMoonlightPlacements(
            listOf(MoonlightPlacement("$host|123", allApps = true, moonlightFolder = true)),
        )

        val again = SessionStore(prefs)
        assertEquals(MoonlightSource.ImportedList, again.moonlightSource())
        assertTrue(again.moonlightImportConfirmed())
        assertTrue(again.moonlightImportSettled())
        assertEquals("Celeste", again.moonlightImportedApps().single().label)
        val placed = again.moonlightPlacements().single()
        assertEquals("$host|123", placed.remoteKey)
        assertTrue(placed.allApps)
        assertTrue(placed.moonlightFolder)

        again.setMoonlightSource(MoonlightSource.PinnedShortcuts)
        val switched = SessionStore(prefs)
        assertEquals(MoonlightSource.PinnedShortcuts, switched.moonlightSource())
        assertEquals("Celeste", switched.moonlightImportedApps().single().label)
        assertEquals(placed, switched.moonlightPlacements().single())
    }

    @Test
    fun aNewPinJoinsAConfirmedImportOnce() {
        val store = SessionStore(MemoryPrefs())
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        val host = "F81D4FAE-7DEC-11D0-A765-00A0C91E6BF6"
        val pin = MoonlightDiscoveredApp(hostUuid = host, hostName = "Gaming PC", appId = "7", label = "Hades")

        // Without an import, Pinned shortcuts shows the pin and the first-run sheet still offers it.
        shell.addMoonlightPin(pin)
        assertFalse(store.moonlightImportConfirmed())
        assertTrue(store.moonlightPlacements().isEmpty())

        store.setMoonlightImport(listOf(MoonlightStoredApp(host.lowercase(), "123", "Celeste")))
        shell.addMoonlightPin(pin)
        shell.addMoonlightPin(pin)
        assertEquals(listOf("Celeste", "Hades"), store.moonlightImportedApps().map { it.label })
        val placed = store.moonlightPlacements().single()
        assertEquals("${host.lowercase()}|7", placed.remoteKey)
        assertEquals(store.moonlightPlacements(), shell.model.moonlightPlacements)
    }

    @Test
    fun theSheetTakesActivateBeforeTheHomeBoard() {
        val store = SessionStore(MemoryPrefs())
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        val host = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6"
        shell.presentMoonlightSheet(
            listOf(MoonlightDiscoveredApp(hostUuid = host, hostName = "Gaming PC", appId = "7", label = "Hades")),
            HostScreen.Bottom,
        )
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertNull(shell.model.moonlightSheet)
        assertEquals(listOf("Hades"), store.moonlightImportedApps().map { it.label })
    }
}
