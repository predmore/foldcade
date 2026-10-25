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
        for (answer in listOf(SaveFolderAnswer.PlayWithoutSyncing, SaveFolderAnswer.PickerCancelled)) {
            assertEquals(SaveLaunchContinuation.Cancel, saveLaunchContinuation("aps3e", answer, needsFolderToStart = true))
        }
        assertEquals(SaveLaunchContinuation.Launch(null), saveLaunchContinuation("aps3e", SaveFolderAnswer.Chosen, true))
    }
}
