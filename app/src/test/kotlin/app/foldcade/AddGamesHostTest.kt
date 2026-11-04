package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

/** Home's way in for games while nothing brings any, and the first run's one-time ask. */
class AddGamesHostTest {
    private val quest = androidHomeId("quest")
    private val initial = HomeBoard(slots = listOf(HOME_ALL, quest))
    private val apps = listOf(LaunchableApp("quest", "Quest", true))
    private val store = SessionStore(MemoryPrefs()).also { it.saveHomeBoard(encodeHome(initial)) }
    private fun shell() = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())).also {
        it.setInstalledApps(apps, emptySet())
        it.showCuratedHome()
    }
    private val offer = RomFolderOffer(listOf("ROMs"), listOf("Sega Genesis"), "primary:ROMs")

    @Test fun homeEndsInAddGamesWhichOffersTheWaysIn() {
        val shell = shell()
        val slot = shell.model.count
        assertEquals(slot, shell.addGamesSlot())
        walkTo(shell, slot)
        // The top screen names it, where an empty slot shows nothing.
        val hero = shell.focusedHero() as HeroSubject.Item
        assertEquals(Copy.addGames, hero.item.title)
        assertEquals(FoundFolderCopy.addGamesLine(null), hero.item.detail)
        assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        val dialog = shell.model.dialog!!
        assertEquals(DialogKind.AddGames, dialog.kind)
        assertEquals(listOf(DialogButton.SignInRomm, DialogButton.AddFolder), dialog.buttons)
        // A held A does not press its first button too.
        assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Bottom, repeatCount = 1))
        assertEquals(DialogKind.AddGames, shell.model.dialog?.kind)
        assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.AddGames, null), shell.onMeaning(Meaning.Back, HostScreen.Bottom))
        assertNull(shell.model.dialog)
        // X does the same, and with folders waiting for access Find games leads.
        shell.setRomFolderOffer(offer)
        assertNull(shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom))
        assertEquals(DialogButton.FindGames, shell.model.dialog?.buttons?.first())
    }

    @Test fun yHasNothingToDoOnTheTile() {
        val shell = shell()
        walkTo(shell, shell.model.count)
        assertTrue(shell.onAddGamesTile())
        assertFalse(shell.hasOptions())
        assertNull(shell.onMeaning(Meaning.Options, HostScreen.Bottom))
        assertNull(shell.model.panel)
        assertNull(shell.model.dialog)
    }

    @Test fun romsOnTheBoardTakeTheTileAway() {
        val shell = shell()
        shell.ingestLibrary("local-folder", listOf(GridEntry(
            id = "drift", title = "Drift", shortText = "nintendo-ds", platformId = "nintendo-ds",
            availabilityLabel = null, occupiesBothDisplays = false,
            game = app.foldcade.api.plugin.Game(backendId = "local-folder", remoteKey = "drift", platformId = "nintendo-ds",
                availability = app.foldcade.api.plugin.Availability.LocalOnly, label = "Drift"),
        )), emptyList())
        assertNull(shell.addGamesSlot())
    }

    @Test fun aSourceTakesTheTileAway() {
        val shell = shell()
        store.addFolderTree("content://com.android.externalstorage.documents/tree/primary%3AGames")
        assertNull(shell.addGamesSlot())
        val romm = shell()
        store.setRommOrigin("https://romm.example")
        assertNull(romm.addGamesSlot())
    }

    @Test fun anEmptySlotPastAddGamesIsStillEmpty() {
        val shell = shell()
        walkTo(shell, shell.model.count + 1)
        assertNull(shell.focusedHero())
        assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        assertNull(shell.model.dialog)
    }

    @Test fun theFirstRunAsksOnceWhenNothingWasFound() {
        val shell = shell()
        assertTrue(shell.maybeAskAddGames())
        assertEquals(FoundFolderCopy.noRomsTitle, shell.model.dialog?.title)
        assertEquals(DialogButton.NotNow, shell.model.dialog?.buttons?.last())
        // Never over another dialog; once answered it never comes back.
        assertFalse(shell.maybeAskAddGames())
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        store.setAddGamesAsked()
        assertFalse(shell.maybeAskAddGames())
    }

    @Test fun theFirstRunAskWaitsForFindGamesAndSkipsAPlayerWithASource() {
        val waiting = shell()
        waiting.setRomFolderOffer(offer)
        assertFalse(waiting.maybeAskAddGames())
        assertFalse(store.addGamesAsked())
        store.setRommOrigin("https://romm.example")
        val signedUp = shell()
        assertFalse(signedUp.maybeAskAddGames())
        // A player who already has games never sees it, even after removing their source.
        assertTrue(store.addGamesAsked())
    }

    @Test fun theEmptyLibraryFollowsTheOffer() {
        val shell = shell()
        shell.showNoLibrary()
        assertEquals(2, shell.model.count)
        shell.setRomFolderOffer(offer)
        assertEquals(3, shell.model.count)
        assertEquals(0, shell.model.focus.cellIndex)
        assertEquals(Effect.OpenAllFilesAccess, shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        shell.setRomFolderOffer(null)
        assertEquals(2, shell.model.count)
    }

    @Test fun theCoresAskNeedsRetroArchAndItsGames() {
        val shell = shell()
        // No RetroArch, or one whose cores Foldcade already sees: nothing to ask.
        assertFalse(shell.maybeOfferRetroArchCores())
        shell.setRetroArch(RetroArchSetting(cores = 3, packageName = "com.retroarch.aarch64"))
        assertFalse(shell.maybeOfferRetroArchCores())
        // Cores unseen, but no game RetroArch plays yet.
        shell.setRetroArch(RetroArchSetting(cores = null, packageName = "com.retroarch.aarch64"))
        assertFalse(shell.maybeOfferRetroArchCores())
        assertNull(shell.model.dialog)
    }

    private fun walkTo(shell: ShellController, target: Int) {
        val columns = GridStrip.column(target) - GridStrip.column(shell.model.focus.cellIndex)
        repeat(kotlin.math.abs(columns)) { shell.onMeaning(if (columns > 0) Meaning.MoveRight else Meaning.MoveLeft, HostScreen.Bottom) }
        val rows = GridStrip.row(target) - GridStrip.row(shell.model.focus.cellIndex)
        repeat(kotlin.math.abs(rows)) { shell.onMeaning(if (rows > 0) Meaning.MoveDown else Meaning.MoveUp, HostScreen.Bottom) }
        assertEquals(target, shell.model.focus.cellIndex)
    }
}
