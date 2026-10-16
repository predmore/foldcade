package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class DeleteHoldLifecycleTest {
    private var time = 0L
    private val deleted = mutableListOf<String>()
    private val key = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6|7"
    @After fun clean() { Shelf.moonlightGames = emptyList() }

    @Test fun pressingAnotherFaceKeyCancelsTheHold() {
        val shell = heldShortcut()
        shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom)
        time += 1000L
        shell.tickRecovery()
        assertTrue(deleted.isEmpty())
        assertNull(shell.model.deleteHold)
    }

    @Test fun activityFocusAndPauseNotifyTheHoldOwner() {
        val source = File("src/main/kotlin/app/foldcade/HomeActivity.kt").readText()
        val focus = source.substringAfter("override fun onWindowFocusChanged").substringBefore("/**")
        val pause = source.substringAfter("override fun onPause").substringBefore("override fun onWindowFocusChanged")
        assertTrue("Window focus must reach the hold owner", focus.contains("shell.inputFocus"))
        assertTrue("Pause must cancel the owning hold", pause.contains("shell.inputFocus"))
    }

    @Test fun resumeGivesBackTheFocusAPauseTookWhenTheWindowKeptIt() {
        // Home, or a new intent to the screen in front, pauses and resumes it without a
        // focus change. Marked unfocused by the pause, every hold to delete was refused.
        val source = File("src/main/kotlin/app/foldcade/HomeActivity.kt").readText()
        val resume = source.substringAfter("override fun onResume").substringBefore("override fun onStop")
        assertTrue(resume.contains("shell.inputFocus(inputWindow, hasWindowFocus())"))
        val shell = heldShortcut()
        shell.inputFocus(HostScreen.Bottom.name, false)
        assertNull(shell.model.deleteHold)
        shell.inputFocus(HostScreen.Bottom.name, true)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertNotNull(shell.model.deleteHold)
        time += 1000L
        shell.tickRecovery()
        assertEquals(listOf(key), deleted)
    }

    @Test fun losingTheInputWindowCancelsWhileTheOtherScreenRemainsVisible() {
        val shell = heldShortcut()
        assertEquals(HostScreen.Bottom.name, shell.model.deleteHold?.inputWindow)
        shell.inputFocus(HostScreen.Bottom.name, false)
        time += 1000L
        shell.tickRecovery()
        assertTrue(deleted.isEmpty())
        assertNull(shell.model.deleteHold)
    }

    @Test fun losingTheOtherWindowDoesNotCancelTheOwnersHold() {
        val shell = heldShortcut()
        shell.inputFocus(HostScreen.Top.name, false)
        time += 1000L
        shell.tickRecovery()
        assertEquals(listOf(key), deleted)
    }

    @Test fun closingTheMenuCancelsImmediately() {
        val shell = heldShortcut()
        shell.showHomeGrid(HomeGrid.Apps)
        assertNull(shell.model.deleteHold)
        time += 1000L
        shell.tickRecovery()
        assertTrue(deleted.isEmpty())
    }

    @Test fun anUnmappedKeyAlsoCancels() {
        val shell = heldShortcut()
        assertTrue(shell.inputKey(null, HostScreen.Bottom.name))
        time += 1000L
        shell.tickRecovery()
        assertTrue(deleted.isEmpty())
    }

    private fun heldShortcut(): ShellController {
        Shelf.moonlightGames = listOf(ShelfGame(key, "Hades", "Moonlight", platformId = "moonlight", mark = "moonlight", libraryId = "moonlight", remoteKey = key))
        val shell = ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()), nowMillis = { time }, onMoonlightRemoved = { deleted += it })
        shell.settleMoonlight()
        shell.noteShelfChanged()
        val folder = List(shell.model.count) { shell.homeFace(it)?.title }.indexOf("Moonlight")
        repeat(GridStrip.column(folder)) { shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom) }
        repeat(GridStrip.row(folder)) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        val target = panelRows(shell.model.panel!!, shell.model).indexOf(Row.DeleteShortcut)
        repeat(target) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertNotNull(shell.model.deleteHold)
        return shell
    }
}
