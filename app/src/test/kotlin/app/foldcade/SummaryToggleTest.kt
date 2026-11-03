package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

/** R3 opens the hero's summary for the focused game; B, R3 again, or moving on fold it. */
class SummaryToggleTest {
    private fun shell(): ShellController {
        val store = SessionStore(MemoryPrefs()).also {
            it.saveHomeBoard(encodeHome(HomeBoard(slots = listOf(androidHomeId("alpha"), androidHomeId("beta")))))
        }
        return ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())).also {
            it.setInstalledApps(listOf(LaunchableApp("alpha", "Alpha", true), LaunchableApp("beta", "Beta", true)), emptySet())
        }
    }

    @Test fun rThreeOpensOnlyWhatTheHeroOffersAndBFoldsItFirst() {
        val shell = shell()
        val hero = Any()
        val key = shell.focusedHero()!!.key
        // Nothing offered: R3 does nothing, and the hint row has no Info.
        assertNull(shell.onMeaning(Meaning.Info, HostScreen.Bottom))
        assertFalse(shell.offersInfo())
        assertNull(shell.summaryOpenFor)
        shell.offerSummary(hero, key)
        assertTrue(shell.offersInfo())
        shell.onMeaning(Meaning.Info, HostScreen.Bottom)
        assertTrue(shell.summaryShown())
        // B folds it and does nothing else.
        val focus = shell.model.focus
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertFalse(shell.summaryShown())
        assertEquals(focus, shell.model.focus)
        // R3 again opens it, R3 once more folds it.
        shell.onMeaning(Meaning.Info, HostScreen.Bottom)
        assertTrue(shell.summaryShown())
        shell.onMeaning(Meaning.Info, HostScreen.Bottom)
        assertFalse(shell.summaryShown())
    }

    @Test fun movingOnFoldsItSoEachGameStartsClosed() {
        val shell = shell()
        val hero = Any()
        shell.offerSummary(hero, shell.focusedHero()!!.key)
        shell.onMeaning(Meaning.Info, HostScreen.Bottom)
        assertNotNull(shell.summaryOpenFor)
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        assertNull(shell.summaryOpenFor)
        shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom)
        shell.offerSummary(hero, shell.focusedHero()!!.key)
        assertFalse(shell.summaryShown())
    }

    @Test fun aMenuOverTheGridHidesInfoAndBClosesTheMenuNotTheSummary() {
        val shell = shell()
        shell.offerSummary(Any(), shell.focusedHero()!!.key)
        shell.onMeaning(Meaning.Info, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertEquals(Side.Tile, shell.model.panel?.side)
        assertFalse(shell.offersInfo())
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertNull(shell.model.panel)
    }

    @Test fun aHeroLeavingTakesBackOnlyItsOwnOffer() {
        val shell = shell()
        val key = shell.focusedHero()!!.key
        val outgoing = Any()
        val incoming = Any()
        shell.offerSummary(outgoing, key)
        shell.offerSummary(incoming, key)
        // The outgoing hero is drawn once more during the crossfade, then leaves.
        shell.offerSummary(outgoing, key)
        shell.withdrawSummary(outgoing)
        assertEquals(key, shell.summaryOffered)
        shell.withdrawSummary(incoming)
        assertNull(shell.summaryOffered)
    }
}
