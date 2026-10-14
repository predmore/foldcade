package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.HOME_ALL
import app.foldcade.language.HomeBoard
import app.foldcade.language.HomeFolder
import app.foldcade.language.encodeHome
import app.foldcade.language.savedBeforeCollections
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The first load of a board from before collections keeps a copy of it, once. */
class HomeBoardKeptTest {
    private val folder = HomeFolder("home.user.1", "Folder 1", userMade = true, mark = "console", slots = listOf("lib:local:a"))
    private val oldRaw = encodeHome(HomeBoard(slots = listOf(HOME_ALL, null, folder.id), folders = mapOf(folder.id to folder)))
        .lines().filter { !it.startsWith("recentAfter") }.joinToString("\n")

    @Test
    fun aBoardFromBeforeCollectionsIsKeptVerbatimOnceAndNeverReplaced() {
        assertTrue(savedBeforeCollections(oldRaw))
        val prefs = MemoryPrefs()
        val store = SessionStore(prefs)
        store.saveHomeBoard(oldRaw)
        shell(prefs)
        assertEquals(oldRaw, store.homeBoardBeforeCollections())
        // This release saved its own board since, so later loads have nothing to keep.
        val rewritten = store.homeBoardRaw()
        assertFalse(savedBeforeCollections(rewritten))
        shell(prefs).noteShelfChanged()
        assertEquals(oldRaw, SessionStore(prefs).homeBoardBeforeCollections())
        // Even an old board saved again later does not replace the first copy.
        store.saveHomeBoard(oldRaw.replace("Folder 1", "Other"))
        shell(prefs)
        assertEquals(oldRaw, SessionStore(prefs).homeBoardBeforeCollections())
    }

    @Test
    fun aFreshHomeOrAHomeFromThisReleaseKeepsNothing() {
        val fresh = MemoryPrefs()
        shell(fresh)
        assertNull(SessionStore(fresh).homeBoardBeforeCollections())
        val current = MemoryPrefs()
        SessionStore(current).saveHomeBoard(encodeHome(HomeBoard()))
        shell(current)
        assertNull(SessionStore(current).homeBoardBeforeCollections())
    }

    private fun shell(prefs: MemoryPrefs) =
        ShellController(SessionStore(prefs), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
}
