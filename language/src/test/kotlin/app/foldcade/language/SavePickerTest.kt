package app.foldcade.language

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SavePickerTest {
    private val target = SavePickerTarget("original-game", "romm#one", "3", "melonds")
    private val save = SavePickerSave("17", "autosave", "melonds", "Steam Deck", 1_000, 32_768, false, null)
    private val state = SavePickerState(target, "Mario", "melonDS", SaveHealth.Fine, listOf("sav"), listOf(save), index = 1, loading = false)

    @Test fun menuOffersSavesNearRestartAndKeepsTheOriginalTarget() {
        val tile = TileActions(saves = true, restart = true, removeDownload = true, game = "original-game")
        assertEquals(listOf(Row.Saves, Row.RestartGame, Row.RemoveDownload), actionRows(tile))
        val model = PickerModel(20, 3, tileActions = tile, focus = GridFocus(cellIndex = 9),
            panel = SidePanel(Side.Left, HostScreen.Bottom, PanelLevel.Actions, 0, GridFocus()))
        assertEquals(Effect.OpenSaves("original-game"), reduce(model, Meaning.Activate).second)
        assertFalse(actionRows(TileActions()).contains(Row.Saves))
    }

    @Test fun confirmationStartsOnCancelAndBLeavesEverySaveAlone() {
        val (confirm, effect) = applySavePicker(state, Meaning.Activate)
        assertNull(effect)
        assertEquals(SavePickerChoice.Play(save), confirm!!.confirmation)
        assertEquals(1, confirm.confirmIndex)
        assertNull(applySavePicker(confirm, Meaning.Activate).second)
        assertNull(applySavePicker(confirm, Meaning.Back).first!!.confirmation)
        assertNull(applySavePicker(state, Meaning.Back).first)
    }

    @Test fun confirmedActionsRetainSaveAndGameIdsEvenIfTheListChanges() {
        val confirm = applySavePicker(state, Meaning.Activate).first!!
        val moved = confirm.copy(saves = listOf(save.copy(id = "new-save")), index = 0, confirmIndex = 0)
        val (busy, effect) = applySavePicker(moved, Meaning.Activate)
        assertEquals(Effect.PlayPickedSave(target, "17"), effect)
        assertTrue(busy!!.busy)
        assertEquals(busy to null, applySavePicker(busy, Meaning.Back))
    }

    @Test fun copyListsOtherSlotsNewestSourcesAndThePlayersMissingSlot() {
        val older = save.copy(id = "18", slot = "manual", source = "Old device", updatedAtMillis = 0)
        val newer = older.copy(id = "19", source = "New device", updatedAtMillis = 2_000)
        val choosing = applySavePicker(state.copy(saves = listOf(save, older, newer)), Meaning.Options).first!!
        assertEquals(listOf(SaveDestination("manual", "melonds", "New device"), SaveDestination("sav", "melonds", "melonDS")), choosing.destinations())
        val confirm = applySavePicker(choosing, Meaning.Activate).first!!.copy(confirmIndex = 0)
        assertEquals(Effect.CopyPickedSave(target, "17", SaveDestination("manual", "melonds", "New device")), applySavePicker(confirm, Meaning.Activate).second)
    }

    @Test fun headerReusesSetupAndSyncAndUnconfiguredSaveOffersSetup() {
        val setup = state.copy(health = SaveHealth.NotSyncing(SaveIssue.NoFolder), index = 0)
        assertEquals(Effect.FixSavePicker(target), applySavePicker(setup, Meaning.Activate).second)
        val confirm = applySavePicker(setup.copy(index = 1), Meaning.Activate).first!!
        assertEquals(SavePickerChoice.Setup, confirm.confirmation)
        assertEquals(Effect.FixSavePicker(target), applySavePicker(confirm.copy(confirmIndex = 0), Meaning.Activate).second)
        assertEquals(Effect.SyncSavePicker(target), applySavePicker(state.copy(health = SaveHealth.Waiting, index = 0), Meaning.Activate).second)
    }

    @Test fun loadingAndErrorsCannotLeakKeysToTheCoveredGrid() {
        for (meaning in listOf(Meaning.LeftPanel, Meaning.RightPanel, Meaning.Options, Meaning.ActivateBottom, Meaning.Menu, Meaning.View)) {
            val model = PickerModel(20, 3, savePicker = state)
            val (next, effect) = reduce(model, meaning)
            assertEquals(model.focus, next.focus)
            assertNull(next.panel)
            assertNull(effect)
        }
        val error = state.copy(error = true)
        assertEquals(Effect.LoadSavePicker(target), applySavePicker(error, Meaning.Activate).second)
        assertNull(applySavePicker(state.copy(loading = true), Meaning.Activate).second)
    }

    @Test fun anUninstalledPlayerCanSyncHeldProgressButCannotConfirmPlay() {
        val missing = state.copy(canPlay = false)
        val tapped = applySavePicker(missing, Meaning.Activate)
        assertNull(tapped.second)
        assertNull(tapped.first!!.confirmation)
        assertEquals(SavePickerCopy.installFirst, tapped.first!!.message)
        val confirm = state.copy(canPlay = false, confirmation = SavePickerChoice.Play(save), confirmIndex = 0)
        assertNull(applySavePicker(confirm, Meaning.Activate).second)
        assertEquals(Effect.SyncSavePicker(target), applySavePicker(missing.copy(index = 0, health = SaveHealth.Waiting), Meaning.Activate).second)
    }

    @Test fun emulatorFamiliesWarnOnlyWhenDifferentAndDatesAreLocal() {
        assertFalse(emulatorMismatch("retroarch.nes", "retroarch.snes"))
        assertFalse(emulatorMismatch("azahar", "azahar"))
        assertTrue(emulatorMismatch("azahar", "melonds"))
        val now = Instant.parse("2026-10-25T10:00:00Z").toEpochMilli()
        assertEquals("2 h ago", relativeSaveWhen(now - 7_200_000, now, DateStyle()))
        assertEquals("Oct 3", relativeSaveWhen(Instant.parse("2026-10-03T10:00:00Z").toEpochMilli(), now, DateStyle(), ZoneId.of("UTC")))
        val warning = SavePickerCopy.confirmationBody(SavePickerChoice.Play(save.copy(emulator = "azahar")), "melonds", "melonDS", DateStyle())
        assertTrue(warning.contains("Your current save stays on RomM."))
        assertTrue(warning.contains("Made by azahar. It may not load in melonDS."))
    }
}
