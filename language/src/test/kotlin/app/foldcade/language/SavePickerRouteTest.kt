package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

/** Y › Saves for a game whose saves stay on this device (design §7.1): what A does and what the keys say. */
class SavePickerRouteTest {
    private val gbaEmu = PlayerChoice("gba-emu", label = "GBA.emu")
    private val target = SavePickerTarget("local-folder:puzzle", "local-folder", "puzzle", "my-boy.game-boy-advance")
    private fun facts(status: SaveStatus, onRomm: Boolean, player: String = "My Boy!", syncs: Boolean = false, supported: PlayerChoice? = gbaEmu) =
        SaveRouteFacts(status, onRomm, true, player, syncs, supported, fileName = "Puzzle.gba", system = "Game Boy Advance")
    private fun page(status: SaveStatus, onRomm: Boolean, saves: List<SavePickerSave> = emptyList(), route: SaveRoute? = saveRoute(facts(status, onRomm)),
        player: String = "My Boy!") =
        SavePickerState(target, "Puzzle", player, status, saves = saves, loading = false, onRomm = onRomm, route = route).settled()
    private val launchOnly = SaveStatus.DeviceOnly(NoDestinationWhy.PlayerCantSync, playedHere = true, emulator = "My Boy!")
    private val save = SavePickerSave("17", "sav", "gba-emu", "Steam Deck", 1_000, 32_768, false, null)

    @Test fun aFolderGameShowsItsRouteInTheCardWithNothingToLoad() {
        val state = page(SaveStatus.DeviceOnly(NoDestinationWhy.NotOnRomm, playedHere = true, emulator = "GBA.emu"), onRomm = false,
            route = saveRoute(facts(SaveStatus.DeviceOnly(NoDestinationWhy.NotOnRomm, true, "GBA.emu"), false, player = "GBA.emu", syncs = true)), player = "GBA.emu")
        assertTrue(state.routeInCard)
        assertFalse(state.setupInCard)
        // Foldcade can't upload a game, so nothing takes focus and A does nothing.
        assertFalse(state.statusActs)
        assertEquals(state to null, applySavePicker(state, Meaning.Activate))
        assertTrue(savePickerHints(state, FaceMap.standard()).isEmpty())
        assertEquals("RomM doesn’t have this game, so its saves stay on this device.", SavePickerCopy.explanation(state.health))
    }

    @Test fun aFolderGameWhoseEmulatorNeedsSetupOpensSetupFromTheCard() {
        val status = SaveStatus.NotSyncing(SaveIssue.NoFolder)
        val state = page(status, onRomm = false, route = saveRoute(facts(status, false, player = "GBA.emu", syncs = true)), player = "GBA.emu")
        // The route card, not the RomM-only Set up saves card: it also says the game needs a RomM copy.
        assertTrue(state.routeInCard)
        assertFalse(state.setupInCard)
        assertTrue(state.statusActs)
        assertEquals(Effect.FixSavePicker(target), applySavePicker(state, Meaning.Activate).second)
        assertTrue(savePickerHints(state, FaceMap.standard()).isEmpty())
    }

    @Test fun aPlayedLaunchOnlyGameAsksBeforeSwitchingAndOpensOnCancel() {
        val state = page(launchOnly, onRomm = false)
        assertTrue(state.statusActs)
        val asked = applySavePicker(state, Meaning.Activate).first!!
        val choice = SavePickerChoice.UsePlayer(gbaEmu, "My Boy!", playedHere = true)
        assertEquals(choice, asked.confirmation)
        // Progress made in My Boy! won't follow, so the question opens on Cancel.
        assertEquals(1, asked.confirmIndex)
        assertEquals("Play with GBA.emu?", SavePickerCopy.confirmationTitle(choice, "My Boy!"))
        assertEquals("Foldcade syncs GBA.emu’s saves. My Boy! keeps the saves made in it. Foldcade can’t move them. Change it any time in Y › Open with.",
            SavePickerCopy.confirmationBody(choice, target.playerId, "My Boy!", DateStyle()))
        assertNull(applySavePicker(asked, Meaning.Activate).second)
        val confirmed = applySavePicker(asked.copy(confirmIndex = 0), Meaning.Activate)
        assertEquals(Effect.UseSavePlayer(target, gbaEmu), confirmed.second)
        assertTrue(confirmed.first!!.busy)
        assertNull(confirmed.first!!.confirmation)
    }

    @Test fun aNeverPlayedLaunchOnlyGameOpensTheQuestionOnTheSwitch() {
        val state = page(launchOnly.copy(playedHere = false), onRomm = false)
        val asked = applySavePicker(state, Meaning.Activate).first!!
        assertEquals(0, asked.confirmIndex)
        assertEquals("Foldcade syncs GBA.emu’s saves. Change it any time in Y › Open with.",
            SavePickerCopy.confirmationBody(asked.confirmation!!, target.playerId, "My Boy!", DateStyle()))
    }

    @Test fun aRommGameOnALaunchOnlyEmulatorKeepsItsListAndPutsTheSwitchOnTheChip() {
        val state = page(launchOnly, onRomm = true, saves = listOf(save))
        assertFalse(state.routeInCard)
        // The chip is where the page starts, and A on it asks to switch.
        assertEquals(0, state.index)
        assertEquals(listOf(HintKey(PromptKey.FaceA, "Use GBA.emu")), savePickerHints(state, FaceMap.standard()))
        assertTrue(applySavePicker(state, Meaning.Activate).first!!.confirmation is SavePickerChoice.UsePlayer)
        // A RomM save can't load in My Boy!, and the message says what can.
        val onSave = applySavePicker(state.copy(index = 1), Meaning.Activate).first!!
        assertEquals("My Boy! can’t load RomM saves. Play with GBA.emu first.", onSave.message)
        assertNull(onSave.confirmation)
        assertEquals("Foldcade can’t sync My Boy!, so its saves stay on this device.", SavePickerCopy.explanation(state.health))
    }

    @Test fun withNothingToSwitchToTheChipIsOnlyRead() {
        val flycast = SaveStatus.DeviceOnly(NoDestinationWhy.PlayerCantSync, playedHere = true, emulator = "Flycast")
        val state = page(flycast, onRomm = true, saves = listOf(save), route = saveRoute(facts(flycast, true, player = "Flycast", supported = null)), player = "Flycast")
        assertFalse(state.statusActs)
        assertEquals(1, state.index)
        assertEquals("Flycast can’t load RomM saves through Foldcade.", applySavePicker(state, Meaning.Activate).first!!.message)
    }

    @Test fun aPartnerGameOnlyExplains() {
        val status = SaveStatus.Unconfirmed(UnconfirmedWhy.PartnerCloud, keeper = SaveKeeper.Partner("DroidDeck", "Steam Cloud"))
        val state = page(status, onRomm = false, route = saveRoute(facts(status, false, player = "DroidDeck")), player = "DroidDeck")
        assertTrue(state.routeInCard)
        assertFalse(state.statusActs)
        assertEquals(state to null, applySavePicker(state, Meaning.Activate))
        assertEquals("Steam Cloud keeps these saves when it’s on for the game. Foldcade can’t check it yet.", state.route!!.summary)
    }

    @Test fun anIncidentOnAFolderGameStillOpensFixFirst() {
        val incident = SaveIncident("WrongFolder:gba", IncidentKind.WrongFolder, "gba", "GBA.emu", emptyList(), null, null,
            Evidence(EvidenceKind.SessionWrite, ""), 0)
        val state = page(SaveStatus.Problem(incident), onRomm = false, route = null, player = "GBA.emu")
        assertFalse(state.routeInCard)
        assertTrue(state.setupInCard)
        assertEquals(Effect.FixSavePicker(target), applySavePicker(state, Meaning.Activate).second)
    }

    @Test fun aFirstLinkStopStillLetsTheSavesPagePlayAChosenSave() {
        val incident = SaveIncident("MaybeNewSave:melonds:romm:12:firstLink:x", IncidentKind.MaybeNewSave, "melonds", "melonDS", listOf("12"), null, null,
            Evidence(EvidenceKind.SessionWrite, SaveProtectionCopy.firstLinkDetail), 0, firstLink = true)
        val state = page(SaveStatus.Problem(incident), onRomm = true, saves = listOf(save), route = null, player = "melonDS").copy(index = 1)
        val asked = applySavePicker(state, Meaning.Activate).first!!
        // A asks Play this save?, opening on Cancel, rather than sending the player to set up.
        assertEquals(SavePickerChoice.Play(save), asked.confirmation)
        assertEquals(1, asked.confirmIndex)
        assertEquals("Play this save?", SavePickerCopy.confirmationTitle(asked.confirmation!!, "melonDS"))
        assertEquals(Effect.PlayPickedSave(target, save.id), applySavePicker(asked.copy(confirmIndex = 0), Meaning.Activate).second)
        // A folder or access problem keeps its gate.
        val wrongFolder = incident.copy(kind = IncidentKind.WrongFolder, firstLink = false)
        assertEquals(SavePickerChoice.Setup, applySavePicker(state.copy(health = SaveStatus.Problem(wrongFolder)), Meaning.Activate).first!!.confirmation)
    }
}
