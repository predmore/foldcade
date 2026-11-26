package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** DroidDeck, GameNative and Moonlight tiles have Y › Saves too: it says who keeps their saves, and does nothing else (§7.1). */
class ShellPartnerSavesTest {
    private val hades = Shelf.DROIDDECK_PREFIX + "1145350"
    private val gog = "gamenative.GOG_42"

    @After fun clearShelf() {
        Shelf.droidDeckGames = emptyList()
        Shelf.droidDeckInstalled = false
        Shelf.moonlightGames = emptyList()
        Shelf.catalog = emptyList()
        Shelf.gameNativeInstalled = false
    }

    private fun shell(): ShellController {
        Shelf.droidDeckGames = listOf(ShelfGame(hades, "Hades II", Copy.progressInDroidDeck, "droiddeck", mark = "droiddeck",
            libraryId = "droiddeck", remoteKey = "1145350"))
        Shelf.catalog = listOf(ShelfGame(gog, "Gog Game", "", "pc", libraryId = "gamenative", remoteKey = "GOG_42", shortcut = false))
        // Moonlight's shelf, as a live computer's apps arrive.
        Shelf.moonlightGames = listOf(ShelfGame("moonlight.host:desktop", "Desktop", "", "moonlight", libraryId = "moonlight", remoteKey = "host:desktop"))
        val shell = ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.settleDroidDeck()
        shell.settleGameNative()
        shell.noteShelfChanged()
        shell.openHomeAll()
        return shell
    }

    @Test fun eachPartnerTileOffersSavesAndItsPageOnlyExplains() {
        val shell = shell()
        val expected = mapOf(
            "Hades II" to "Steam Cloud keeps these saves when it’s on for the game. Foldcade can’t check it yet.",
            "Gog Game" to "GameNative keeps these saves. Foldcade can’t check its cloud yet.",
            "Desktop" to "These saves stay on your computer, or with the game’s own cloud.",
        )
        for ((title, summary) in expected) {
            val index = (0 until shell.model.count).single { shell.homeFace(it)?.title == title }
            shell.touchCell(index, HostScreen.Bottom)
            val actions = shell.model.tileActions!!
            assertTrue(title, actions.saves)
            assertTrue(title, Row.Saves in actionRows(actions))
            val page = shell.partnerSavesPage(actions.game!!)!!
            assertEquals(title, summary, page.route?.summary)
            // Nothing to load, play, set up or press: A and Y do nothing here.
            assertTrue(page.routeInCard)
            assertFalse(page.loading)
            assertFalse(page.canPlay)
            assertFalse(page.statusActs)
            assertEquals(page to null, applySavePicker(page, Meaning.Activate))
            assertNull(applySavePicker(page, Meaning.Options).second)
            assertTrue(savePickerHints(page, FaceMap.standard()).isEmpty())
            // A refresh keeps it the partner's page.
            shell.presentSavePicker(page)
            shell.notePlayChanged()
            assertEquals(summary, shell.savePickerRoute(page.target, page.health)?.summary)
            shell.presentSavePicker(null)
        }
    }

    @Test fun anAndroidAppStillHasNoSavesRow() {
        val shell = shell()
        assertFalse(shell.partnerSaves("android:com.example.notes"))
        assertNull(shell.partnerSavesPage("android:com.example.notes"))
    }
}
