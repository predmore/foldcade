package app.foldcade

import app.foldcade.language.MoonlightPlacement
import app.foldcade.language.MoonlightSource
import app.foldcade.language.MoonlightStoredApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
