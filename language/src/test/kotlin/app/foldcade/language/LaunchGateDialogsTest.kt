package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B is back. On a dialog a launch waits on, Back closes it and the launch stops: it never
 * plays without syncing, plays anyway, or closes the running game (review-D S1). Playing
 * past the ask is always a button of its own, and the hint under the dialog says Cancel.
 */
class LaunchGateDialogsTest {
    private val screen = HostScreen.Bottom

    /** Every dialog that holds a launch, in every shape it takes. */
    private val gates = listOf(
        saveFolderDialog(screen),
        saveFolderDialog(screen, syncs = true, playerName = "Azahar", opensThere = true),
        saveFolderDialog(screen, syncs = true, playerName = "aPS3e", needed = true),
        saveFolderDialog(screen, syncs = true, wrong = "melonDS" to setOf("sav"), playerName = "melonDS"),
        hostedSavesDialog("DuckStation", "1. Open DuckStation.\n2. Pick Foldcade.", beforeLaunch = true, screen = screen),
        saveSyncDialog("ARMSX2", "Open ARMSX2.", screen, beforeLaunch = true),
        retroArchCoresDialog(screen),
        edenWrongFolderDialog(screen, launchHeld = true),
        edenWrongFolderDialog(screen, launchHeld = true, needed = true),
        closeBothPanelDialog(screen, "Bravely Second"),
        saveNotPlacedDialog("Tetris", screen, playAnyway = true),
        saveNotPlacedDialog("Tetris", screen, playAnyway = true, incompatible = true),
        coreDownloadFailedDialog("mGBA", screen),
        retroArchStorageDialog(screen),
    )

    /** What would carry a held launch on, or start something in its place. */
    private val continues = setOf(
        DialogButton.PlayWithoutSyncing,
        DialogButton.PlayAnyway,
        DialogButton.CloseIt,
        DialogButton.TryAgain,
        DialogButton.ContinueGrant,
        DialogButton.ChooseSaveFolder,
        DialogButton.SetUpSaves,
        DialogButton.AllowFiles,
        DialogButton.OpenRetroArch,
    )

    @Test fun backOnALaunchGateNeverContinuesTheLaunch() {
        for (dialog in gates) {
            assertTrue("${dialog.title} holds a launch", dialog.holdsLaunch)
            val (closed, effect) = reduce(PickerModel(count = 1, rowsPerPage = 1, dialog = dialog), Meaning.Back, screen)
            assertNull("${dialog.title}: Back closes it", closed.dialog)
            val button = (effect as Effect.DialogChoice).button
            assertFalse("${dialog.title}: Back pressed $button", button in continues)
            // The way on, when there is one, is a button the player presses on purpose.
            assertTrue(dialog.buttons.none { it == DialogButton.Dismiss })
        }
    }

    @Test fun theHintUnderALaunchGateSaysWhatBackDoes() {
        for (dialog in gates) {
            val hints = dialogHints(dialog, FaceMap.standard())
            val back = dialogBackButton(dialog)
            if (back != null) {
                // The button Back presses wears its key, so nothing names it twice (review-B S11).
                assertTrue("${dialog.title}: $hints", hints.isEmpty())
                assertEquals(Copy.notNow, dialogButtonLabel(dialog.buttons[back], dialog.subject))
                continue
            }
            val hint = hints.single()
            assertEquals(PromptKey.FaceB, hint.key)
            assertEquals("${dialog.title}: ${hint.label}", Copy.cancelLaunch, hint.label)
        }
        assertEquals(Copy.cancelLaunch, dialogBackLabel(saveFolderDialog(screen)))
        assertEquals(Copy.notNow, dialogBackLabel(closeBothPanelDialog(screen)))
        // A dialog that only tells says Close, and one with Not now names it.
        assertEquals(Copy.close, dialogBackLabel(noFileDialog(screen)))
        assertEquals(Copy.notNow, dialogBackLabel(homePrompt(screen)))
        assertEquals(Copy.close, dialogBackLabel(backupDialog(screen, undo = false)))
        // Back's own key sits on the button it presses, including an OK-only notice.
        assertEquals(homePrompt(screen).buttons.indexOf(DialogButton.NotNow), dialogBackButton(homePrompt(screen)))
        assertNull(dialogBackButton(backupDialog(screen, undo = false)))
        for (notice in listOf(noFileDialog(screen), playerNotice("BIOS needed", "Body", screen))) {
            assertEquals(listOf(DialogButton.Ok), notice.buttons)
            assertEquals(0, dialogBackButton(notice))
            assertTrue(dialogHints(notice, FaceMap.standard()).isEmpty())
            val (_, effect) = reduce(PickerModel(count = 1, rowsPerPage = 1, dialog = notice), Meaning.Back, screen)
            assertEquals(DialogButton.Ok, (effect as Effect.DialogChoice).button)
        }
    }

    @Test fun theSaveFolderAskOpensOnChooseAndPlaysOnlyFromItsOwnButton() {
        val ask = saveFolderDialog(screen, syncs = true, playerName = "Azahar", opensThere = true)
        val model = PickerModel(count = 1, rowsPerPage = 1, dialog = ask)
        assertEquals(DialogButton.ChooseSaveFolder, ask.buttons[ask.index])
        assertEquals(Effect.DialogChoice(DialogButton.Dismiss, DialogKind.SaveFolder), reduce(model, Meaning.Back).second)
        val right = reduce(model, Meaning.MoveRight).first
        assertEquals(Effect.DialogChoice(DialogButton.PlayWithoutSyncing, DialogKind.SaveFolder), reduce(right, Meaning.Activate).second)
    }

    @Test fun theCoresAskPlaysAnywayOnlyOnPurpose() {
        val held = retroArchCoresDialog(screen)
        assertEquals(listOf(DialogButton.ContinueGrant, DialogButton.PlayAnyway), held.buttons)
        assertEquals("Play anyway", dialogButtonLabel(DialogButton.PlayAnyway))
        assertFalse(held.body.contains("Not now"))
        // Before any launch nothing is held, so Not now settles the offer and Back presses it.
        val first = retroArchCoresFirstDialog(screen, listOf("Sega Genesis"))
        assertFalse(first.holdsLaunch)
        assertEquals(DialogButton.NotNow, first.buttons[first.safeIndex])
    }

    @Test fun closingTheRunningGameOpensOnNotNow() {
        val close = closeBothPanelDialog(screen, "Bravely Second")
        assertEquals(DialogButton.NotNow, close.buttons[close.index])
        assertEquals("Close Bravely Second?", close.title)
        assertEquals(Copy.closePlayerTitle, closeBothPanelDialog(screen).title)
    }

    @Test fun aMissingPlayerOffersItsPageWhenThereIsOne() {
        val get = missingPlayerDialog("Azahar", screen, canGet = PlayerPages.of("Azahar") != null)
        assertEquals(listOf(DialogButton.GetPlayer, DialogButton.NotNow), get.buttons)
        assertEquals("Get Azahar", dialogButtonLabel(get.buttons[get.index], get.subject))
        assertEquals(DialogButton.NotNow, get.buttons[get.safeIndex])
        assertEquals("Azahar", get.subject)
        assertTrue(PlayerPages.of("Azahar")!!.startsWith("https://github.com/azahar-emu/azahar"))
        val unknown = missingPlayerDialog("EmuCoreX", screen, canGet = PlayerPages.of("EmuCoreX") != null)
        assertEquals(listOf(DialogButton.Ok), unknown.buttons)
        assertEquals("melonDS also runs these games.", missingPlayerDialog("Azahar", screen, alsoRuns = listOf("melonDS")).detail)
    }

    @Test fun reLoginOffersSignInAndNotNow() {
        val dialog = signInAgainPrompt(screen)
        assertEquals(listOf(DialogButton.SignInRomm, DialogButton.NotNow), dialog.buttons)
        assertEquals(DialogButton.NotNow, dialog.buttons[dialog.safeIndex])
        assertFalse(dialog.body.contains("credentials"))
    }

    @Test fun aBodyOfNumberedStepsSplitsIntoALeadAndItsSteps() {
        assertEquals(DialogBody("Plain words.", emptyList()), dialogBodyParts("Plain words."))
        assertEquals(DialogBody(null, listOf("One.", "Two.")), dialogBodyParts("1. One.\n2. Two."))
        assertEquals(DialogBody("Lead.", listOf("One.", "Two.")), dialogBodyParts("Lead.\n1. One.\n2. Two."))
        // One step alone, or steps out of order, read as written.
        assertEquals(DialogBody("Lead.\n1. One.", emptyList()), dialogBodyParts("Lead.\n1. One."))
        assertEquals(DialogBody("1. One.\n3. Two.", emptyList()), dialogBodyParts("1. One.\n3. Two."))
    }

    @Test fun theDebugCaptureNamesOnlyDialogsTheAppStillShows() {
        assertNull(previewDialogState("missing-core", 0, screen))
        assertEquals(DialogKind.RetroArchDownload, previewDialogState("core-download-failed", 0, screen)?.kind)
    }
}
