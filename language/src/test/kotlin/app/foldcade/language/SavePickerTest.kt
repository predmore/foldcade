package app.foldcade.language

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class SavePickerTest {
    private val target = SavePickerTarget("original-game", "romm", "3", "melonds")
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

    @Test fun onlyOwnBackupMetadataChangesTheSlotLabel() {
        val archive = save.copy(slot = null)
        assertEquals("Steam Deck · No slot", SavePickerCopy.row(archive))
        assertEquals("Steam Deck · Backup", SavePickerCopy.row(archive.copy(backup = true)))
        assertEquals("Steam Deck · autosave", SavePickerCopy.row(save))
    }

    @Test fun rowsNameTheOriginAppExceptForFoldcadeDevicesAndKeepFullDetailForConfirmation() {
        val sources = listOf(
            Triple(SavePickerOrigin("foldcade", "AYN AYN Thor", true), "This device", "Foldcade on AYN AYN Thor · melonds"),
            Triple(SavePickerOrigin("foldcade", "Steam Deck", false), "Steam Deck", "Foldcade on Steam Deck · melonds"),
            Triple(SavePickerOrigin("argosy", "AYN AYN Thor", false), "Argosy", "Argosy on AYN AYN Thor · melonds"),
            Triple(SavePickerOrigin("argosy-launcher", "Thor", false), "Argosy", "Argosy on Thor · melonds"),
            Triple(SavePickerOrigin("grout", null, false), "Grout", "Grout · melonds"),
            Triple(SavePickerOrigin("web", "Browser", false), "RomM web", "RomM web on Browser · melonds"),
            Triple(SavePickerOrigin("rom-ingest", "Server", false), "Rom Ingest", "Rom Ingest on Server · melonds"),
        )
        for ((origin, short, detail) in sources) {
            val from = save.copy(origin = origin)
            assertEquals("$short · autosave", SavePickerCopy.row(from))
            assertEquals(detail, SavePickerCopy.sourceDetail(from))
            assertTrue(SavePickerCopy.confirmationBody(SavePickerChoice.Play(from), "melonds", "melonDS", DateStyle()).startsWith("From $detail, "))
            val choosing = state.copy(saves = listOf(from.copy(slot = "manual")), copying = save)
            assertEquals(short, choosing.destinations().first().source)
        }
    }

    @Test fun unknownOriginsKeepDeviceThenEmulatorThenUnknownFallbacks() {
        assertEquals("Steam Deck", SavePickerCopy.source(save))
        assertEquals("melonds", SavePickerCopy.source(save.copy(source = null)))
        assertEquals("Unknown", SavePickerCopy.source(save.copy(source = "", emulator = null)))
        assertEquals("Steam Deck", SavePickerCopy.source(save.copy(origin = SavePickerOrigin(null, null, false))))
        assertEquals("Steam Deck · melonds", SavePickerCopy.sourceDetail(save))
        assertEquals("melonds", SavePickerCopy.sourceDetail(save.copy(source = null)))
    }

    @Test fun playWarnsThatAPausedGameRestartsAndLosesUnsavedProgress() {
        val body = SavePickerCopy.confirmationBody(SavePickerChoice.Play(save), "melonds", "melonDS", DateStyle(), restartsOpenGame = true)
        assertTrue(body.contains("The open game restarts."))
        assertTrue(body.contains(SavePickerCopy.restartOpen))
        assertFalse(SavePickerCopy.confirmationBody(SavePickerChoice.Play(save), "melonds", "melonDS", DateStyle()).contains(SavePickerCopy.restartOpen))
    }

    @Test fun copyWarnsAboutRestartOnlyForAnOpenGameAndItsOwnSlot() {
        for (open in listOf(false, true)) {
            for (slot in listOf("sav", "autosave")) {
                val page = state.copy(restartsOpenGame = open, ownSlots = listOf("sav"))
                val choice = SavePickerChoice.Copy(save, SaveDestination(slot, "melonds", "Device"))
                val body = SavePickerCopy.confirmationBody(choice, page.target.playerId, page.playerName, DateStyle(), page.restartsOpenGame, page.ownSlots)
                assertTrue(body.startsWith("The save there now stays on RomM."))
                assertEquals("open=$open slot=$slot", open && slot in page.ownSlots, body.contains(SavePickerCopy.restartOpen))
            }
        }
    }

    @Test fun emulatorFamiliesWarnOnlyWhenDifferentAndDatesAreLocal() {
        assertFalse(emulatorMismatch("retroarch.nes", "retroarch.snes"))
        assertFalse(emulatorMismatch("azahar", "azahar"))
        assertTrue(emulatorMismatch("azahar", "melonds"))
        val now = Instant.parse("2026-10-25T10:00:00Z").toEpochMilli()
        assertEquals("2h ago", relativeSaveWhen(now - 7_200_000, now, DateStyle(), ZoneId.of("UTC")))
        assertEquals("Oct 3", relativeSaveWhen(Instant.parse("2026-10-03T10:00:00Z").toEpochMilli(), now, DateStyle(), ZoneId.of("UTC")))
        val warning = SavePickerCopy.confirmationBody(SavePickerChoice.Play(save.copy(emulator = "azahar")), "melonds", "melonDS", DateStyle())
        assertTrue(warning.contains("Your current save stays on RomM."))
        assertTrue(warning.contains("Made by azahar. It may not load in melonDS."))
    }

    @Test fun rowTimesAreShortTodayThenYesterdayThenTheDateWithAYearOnlyWhenItDiffers() {
        val now = Instant.parse("2026-10-25T10:00:00Z").toEpochMilli()
        val zone = ZoneId.of("UTC")
        assertEquals("Just now", relativeSaveWhen(now - 30_000, now, DateStyle(), zone))
        assertEquals("2m ago", relativeSaveWhen(now - 120_000, now, DateStyle(), zone))
        assertEquals("3h ago", relativeSaveWhen(now - 10_800_000, now, DateStyle(), zone))
        // A clock a little ahead of the server is still now, not the future.
        assertEquals("Just now", relativeSaveWhen(now + 5_000, now, DateStyle(), zone))
        val yesterday = Instant.parse("2026-10-24T23:59:00Z").toEpochMilli()
        assertEquals("Yesterday", relativeSaveWhen(yesterday, now, DateStyle(), zone))
        // It is still yesterday on the device, even though the UTC date has changed.
        assertEquals("10h ago", relativeSaveWhen(yesterday, now - 60_000, DateStyle(), ZoneId.of("Pacific/Honolulu")))
        assertEquals("Oct 23", relativeSaveWhen(Instant.parse("2026-10-23T08:00:00Z").toEpochMilli(), now, DateStyle(), zone))
        val old = Instant.parse("2025-09-30T22:25:00Z").toEpochMilli()
        assertEquals("Sep 30, 2025", relativeSaveWhen(old, now, DateStyle(), zone))
        val french = DateStyle(Locale.FRANCE, monthDayPattern = "d MMM", monthDayYearPattern = "d MMM y", is24Hour = true)
        assertEquals("30 sept. 2025", relativeSaveWhen(old, now, french, zone))
    }

    @Test fun theStatusChipHoldsFocusOnlyWhileItActsAndThePageOtherwiseStartsOnTheNewestSave() {
        val older = save.copy(id = "18", updatedAtMillis = 0)
        val opened = state.copy(index = 0, saves = listOf(save, older))
        // Syncing fine: the chip is only read, so focus starts on the newest save and stops there going up.
        assertEquals(1, opened.firstIndex)
        assertEquals(1, opened.settled().index)
        assertEquals(1, applySavePicker(opened.settled(), Meaning.MoveUp).first!!.index)
        assertEquals(1, opened.copy(health = SaveHealth.DeviceOnly).settled().index)
        // Not syncing or waiting: the chip is where setup starts, and it is reachable from the rows.
        for (health in listOf(SaveHealth.NotSyncing(SaveIssue.NoFolder), SaveHealth.Waiting)) {
            val needs = opened.copy(health = health)
            assertEquals(0, needs.settled().index)
            assertEquals(0, applySavePicker(needs.copy(index = 1), Meaning.MoveUp).first!!.index)
        }
        // A fixed setup moves focus off the chip that no longer acts; no saves keep it on the chip.
        assertEquals(1, opened.copy(index = 0).settled().index)
        assertEquals(0, opened.copy(saves = emptyList(), index = 2).settled().index)
        assertEquals(2, opened.copy(index = 5).settled().index)
    }

    @Test fun hintsNameOnlyTheKeysThatActOnWhatHasFocus() {
        val faces = FaceMap.standard()
        val a = promptKeyOf(faces.confirmKey)!!
        val y = actionFaces(faces).optionsGlyph
        assertEquals(listOf(HintKey(a, "Play"), HintKey(y, "Copy")), savePickerHints(state, faces))
        assertEquals(emptyList<HintKey>(), savePickerHints(state.copy(index = 0), faces))
        assertEquals(listOf(HintKey(a, "Set up")), savePickerHints(state.copy(index = 0, health = SaveHealth.NotSyncing(SaveIssue.NeedsSetup)), faces))
        assertEquals(listOf(HintKey(a, "Sync now")), savePickerHints(state.copy(index = 0, health = SaveHealth.Waiting), faces))
        assertEquals(listOf(HintKey(a, "Try again")), savePickerHints(state.copy(error = true), faces))
        for (quiet in listOf(state.copy(loading = true), state.copy(busy = true), state.copy(confirmation = SavePickerChoice.Play(save)))) {
            assertEquals(emptyList<HintKey>(), savePickerHints(quiet, faces))
        }
        val choosing = state.copy(copying = save)
        assertEquals(listOf(HintKey(a, "Copy")), savePickerHints(choosing, faces))
        assertEquals(emptyList<HintKey>(), savePickerHints(choosing.copy(ownSlots = listOf("autosave")), faces))
    }

    @Test fun theTopScreenCountsTheSaves() {
        assertEquals("No saves on RomM yet", SavePickerCopy.count(0))
        assertEquals("1 save on RomM", SavePickerCopy.count(1))
        assertEquals("3 saves on RomM", SavePickerCopy.count(3))
    }
}
