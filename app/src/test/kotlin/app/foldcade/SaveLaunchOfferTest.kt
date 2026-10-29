package app.foldcade

import app.foldcade.api.plugin.*
import app.foldcade.language.*
import app.foldcade.plugins.armsx2.ArmsX2Player
import app.foldcade.plugins.duckstation.DuckStationPlayer
import app.foldcade.plugins.melonds.MelonDsPlayer
import org.junit.Assert.*
import org.junit.Test

class SaveLaunchOfferTest {
    @Test fun decliningOrCancellingThePickerAppliesToOneLaunchOfOnePlayer() {
        val player = MelonDsPlayer()
        assertEquals(SaveLaunchOffer.Folder, saveLaunchOffer(player, true))
        val prompt = saveFolderDialog(HostScreen.Top, syncs = true, playerName = player.displayName)
        assertEquals(DialogButton.ChooseSaveFolder, prompt.buttons[prompt.index])
        val model = PickerModel(count = 1, rowsPerPage = 1, dialog = prompt)
        assertEquals(Effect.DialogChoice(DialogButton.ChooseSaveFolder, DialogKind.SaveFolder), reduce(model, Meaning.Activate).second)
        assertEquals(Effect.DialogChoice(DialogButton.PlayWithoutSyncing, DialogKind.SaveFolder), reduce(model, Meaning.Back).second)
        // Both Play without syncing and Android's picker cancel retry with this same, transient player id.
        for (answer in listOf(SaveFolderAnswer.PlayWithoutSyncing, SaveFolderAnswer.PickerCancelled)) {
            val continuation = saveLaunchContinuation(player.id, answer, needsFolderToStart = false) as SaveLaunchContinuation.Launch
            assertEquals(player.id, continuation.declinedFor)
            assertNull(saveLaunchOffer(player, true, declinedFor = continuation.declinedFor))
            assertEquals(SaveLaunchOffer.Folder, saveLaunchOffer(player, true))
        }
        assertEquals(SaveLaunchOffer.Folder, saveLaunchOffer(player, true, declinedFor = "another-player"))
        assertNull(saveLaunchOffer(player, false))
        player.bindSaveFolder("content://saves")
        assertNull(saveLaunchOffer(player, true))
    }

    @Test fun edenAlwaysUsesTheSaveOfferAndSkippingIsNeverRemembered() {
        val player = app.foldcade.plugins.eden.EdenPlayer()
        assertEquals(SaveLaunchOffer.Folder, saveLaunchOffer(player, true))
        assertEquals(EdenGate.Ready, edenGate(false))
        for (answer in listOf(SaveFolderAnswer.PlayWithoutSyncing, SaveFolderAnswer.PickerCancelled)) {
            val continuation = saveLaunchContinuation(player.id, answer, player.setupSteps(null) != null) as SaveLaunchContinuation.Launch
            assertFalse(saveSyncForLaunch(player, continuation.declinedFor))
            assertTrue(saveSyncForLaunch(player, null))
            assertNull(saveLaunchOffer(player, true, continuation.declinedFor))
            assertEquals(SaveLaunchOffer.Folder, saveLaunchOffer(player, true))
        }
        player.bindSaveFolder("content://eden/tree/root")
        assertFalse(saveSyncForLaunch(player, player.id))
        assertTrue(saveSyncForLaunch(player, null))
        assertNull(saveLaunchOffer(player, true))
        assertEquals(EdenGate.Check, edenGate(true))
    }

    @Test fun hostedSavesOfferSetupOrPlayAndAskAgainOnTheNextLaunch() {
        val player = DuckStationPlayer().apply { bindSaveFolder("content://data") }
        val chosen = saveLaunchContinuation(player.id, SaveFolderAnswer.Chosen, false) as SaveLaunchContinuation.Launch
        assertNull(chosen.declinedFor)
        assertTrue(saveLaunchOffer(player, true, declinedFor = chosen.declinedFor) is SaveLaunchOffer.Setup)
        val offer = saveLaunchOffer(player, true) as SaveLaunchOffer.Setup
        assertEquals(player.hostedFolderSteps, offer.steps)
        val prompt = hostedSavesDialog(player.displayName, offer.steps, true, HostScreen.Bottom)
        val model = PickerModel(count = 1, rowsPerPage = 1, dialog = prompt)
        assertEquals(DialogButton.SetUpSaves, prompt.buttons[prompt.index])
        assertEquals(Effect.DialogChoice(DialogButton.SetUpSaves, DialogKind.HostedSaves, subject = player.displayName), reduce(model, Meaning.Activate).second)
        assertEquals(Effect.DialogChoice(DialogButton.PlayWithoutSyncing, DialogKind.HostedSaves, subject = player.displayName), reduce(model, Meaning.Back).second)
        assertNull(saveLaunchOffer(player, true, declinedFor = player.id))
        assertTrue(saveLaunchOffer(player, true) is SaveLaunchOffer.Setup)
        player.bindHostedFolder("content://hosted", true)
        assertNull(saveLaunchOffer(player, true))
    }

    @Test fun backOrNotNowOnTheEdenSaveHoldCancelsWithoutStartingAnything() {
        val prompt = saveNotPlacedDialog("Fantasy Life", HostScreen.Bottom, playAnyway = true)
        val model = PickerModel(count = 1, rowsPerPage = 1, dialog = prompt)
        val back = reduce(model, Meaning.Back).second as Effect.DialogChoice
        val notNow = reduce(model.copy(dialog = prompt.copy(index = 2)), Meaning.Activate).second as Effect.DialogChoice
        for (choice in listOf(back, notNow)) {
            assertEquals(DialogKind.EdenSaveHold, choice.kind)
            val work = LaunchWork()
            val generation = work.begin()
            val actions = mutableListOf<String>()
            answerEdenSaveHold(
                choice.button,
                cancel = { work.cancel(); actions += "cancel" },
                openPlayer = { actions += "open" },
                playWithoutSyncing = { actions += "play" },
            )
            assertEquals(listOf("cancel"), actions)
            assertFalse(work.current(generation))
        }
    }

    @Test fun settingUpAHeldEdenSaveCancelsBeforeOpeningThePlayer() {
        val actions = mutableListOf<String>()
        answerEdenSaveHold(
            DialogButton.SetUpSaves,
            cancel = { actions += "cancel" },
            openPlayer = { actions += "open" },
            playWithoutSyncing = { actions += "play" },
        )
        assertEquals(listOf("cancel", "open"), actions)
    }

    @Test fun onlyExplicitlyPlayingAHeldEdenSaveBypassesSyncForThisLaunch() {
        val player = app.foldcade.plugins.eden.EdenPlayer().apply { bindSaveFolder("content://eden") }
        val prompt = saveNotPlacedDialog("Fantasy Life", HostScreen.Bottom, playAnyway = true)
        val model = PickerModel(count = 1, rowsPerPage = 1, dialog = prompt.copy(index = 1))
        val choice = reduce(model, Meaning.Activate).second as Effect.DialogChoice
        val actions = mutableListOf<String>()
        var declinedFor: String? = null
        answerEdenSaveHold(
            choice.button,
            cancel = { actions += "cancel" },
            openPlayer = { actions += "open" },
            playWithoutSyncing = { actions += "play"; declinedFor = player.id },
        )
        assertEquals(listOf("play"), actions)
        assertFalse(saveSyncForLaunch(player, declinedFor))
        assertTrue(saveSyncForLaunch(player, null))
    }

    @Test fun hostedCardsInUseNeverAskForAnOptionalDataFolder() {
        val player = DuckStationPlayer().apply { bindHostedFolder("content://hosted", true) }
        assertFalse(player.hasSaveFolder())
        repeat(2) { assertNull(saveLaunchOffer(player, true)) }
        player.bindSaveFolder("content://data")
        assertNull(saveLaunchOffer(player, true))
    }

    @Test fun missingHostedCardsAreTheFirstSaveOfferWithOrWithoutADataFolder() {
        val player = DuckStationPlayer()
        val setup = SaveLaunchOffer.Setup(player.hostedFolderSteps)
        assertEquals(setup, saveLaunchOffer(player, true))
        assertNull(saveLaunchOffer(player, true, declinedFor = player.id))
        assertEquals(setup, saveLaunchOffer(player, true))
        player.bindSaveFolder("content://data")
        assertEquals(setup, saveLaunchOffer(player, true))
        player.bindHostedFolder("content://hosted", true)
        assertNull(saveLaunchOffer(player, true))
    }

    @Test fun armsx2NeedsSetupUsesTheSameLaunchFlowAndNeverReplacesTheCard() {
        val player = ArmsX2Player().apply { bindSaveFolder("content://data") }
        val state = SaveSyncState.NeedsSetup("Keep the old card. Pick a folder card.")
        assertEquals(SaveLaunchOffer.Setup(state.steps), saveLaunchOffer(player, true, sync = state))
        val prompt = saveSyncDialog(player.displayName, state.steps, HostScreen.Top, beforeLaunch = true)
        assertEquals(listOf(DialogButton.SetUpSaves, DialogButton.PlayWithoutSyncing), prompt.buttons)
        assertEquals(state.steps, prompt.body)
        assertEquals(0, prompt.index)
        assertNull(saveLaunchOffer(player, true, declinedFor = player.id, sync = state))
        assertEquals(SaveLaunchOffer.Setup(state.steps), saveLaunchOffer(player, true, sync = state))
        assertNull(saveLaunchOffer(player, true, sync = SaveSyncState.Syncing))
    }

    @Test fun retiredSkipPreferencesAreRemovedWithoutTouchingFoldersMarksOrOtherChoices() {
        val prefs = MemoryPrefs()
        prefs.edit().putBoolean("player_save_folder_skipped:retroarch", true)
            .putBoolean("player_save_folder_skipped:armsx2", false)
            .putBoolean("player_hosted_saves_skipped:duckstation", true)
            .putString("player_save_folder:retroarch", "content://saves")
            .putString("unrelated", "keep").apply()
        SessionStore(prefs)
        assertEquals(setOf("player_save_folder:retroarch", "unrelated"), prefs.all.keys)
        val again = SessionStore(prefs)
        assertEquals("content://saves", again.playerSaveFolder("retroarch"))
        assertEquals("keep", prefs.getString("unrelated", null))
        assertEquals(SaveLaunchOffer.Folder, saveLaunchOffer(MelonDsPlayer(), true))
    }

    @Test fun aPlayerThatRequiresItsFolderStillCannotStartUntilOneIsChosen() {
        val prompt = saveFolderDialog(HostScreen.Top, playerName = "aPS3e", needed = true)
        assertEquals(DialogButton.NotNow, prompt.buttons[prompt.safeIndex])
        val model = PickerModel(count = 1, rowsPerPage = 1, dialog = prompt)
        assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.SaveFolder), reduce(model, Meaning.Back).second)
        assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.SaveFolder), reduce(model, Meaning.MoveRight).first.let {
            reduce(it, Meaning.Activate).second
        })
        for (answer in listOf(SaveFolderAnswer.PlayWithoutSyncing, SaveFolderAnswer.PickerCancelled)) {
            assertEquals(SaveLaunchContinuation.Cancel, saveLaunchContinuation("aps3e", answer, needsFolderToStart = true))
        }
        assertEquals(SaveLaunchContinuation.Launch(null), saveLaunchContinuation("aps3e", SaveFolderAnswer.Chosen, true))
    }
}
