package app.foldcade.language

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The words on the surfaces that carry save sync: saves, RomM's sign-in, and Settings › Emulators. */
class SaveSurfacesTest {
    private val zone = ZoneOffset.UTC
    private val style = DateStyle()
    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 10, day, hour, minute).toInstant(zone).toEpochMilli()
    private val now = at(30, 12)
    private fun save(id: String, slot: String?, millis: Long, backup: Boolean = false) =
        SavePickerSave(id, slot, "azahar", null, millis, 1, false, null, backup = backup)

    @Test fun genericSlotsAreNamedByDayWithTheTimeOnlyWhenDaysTie() {
        val saves = listOf(
            save("a", "title", at(25, 20, 47)),
            save("b", "title", at(25, 9, 5)),
            save("c", "title", at(29, 8)),
            save("d", "title", at(30, 11)),
            save("e", "autosave", at(25, 7)),
            save("f", "title", at(24, 7), backup = true),
            save("g", null, at(12, 7)),
        )
        val names = saveNames(saves, ownSlots = listOf("title"), style = style, now = now, zone = zone)
        assertEquals("Oct 25, 8:47 PM", names["a"])
        assertEquals("Oct 25, 9:05 AM", names["b"])
        assertEquals(SavePickerCopy.yesterday, names["c"])
        assertEquals(SavePickerCopy.today, names["d"])
        // A slot someone named keeps its name; a Backup says it is one.
        assertEquals("autosave", names["e"])
        assertEquals(SavePickerCopy.backup, names["f"])
        assertEquals("Oct 12", names["g"])
        // A name with its time already says when; one with its day alone does not.
        assertTrue(saveNameSaysTime(names["a"]!!, at(25, 20, 47), style, zone))
        assertFalse(saveNameSaysTime(names["g"]!!, at(12, 7), style, zone))
    }

    @Test fun formatSlotsAreGenericEvenForAnotherEmulator() {
        for (slot in listOf("sav", "SRM", "savedata", "card1", "archive")) assertTrue(slot, genericSlot(slot, emptyList()))
        assertFalse(genericSlot("autosave", emptyList()))
        assertTrue(genericSlot("mine", listOf("mine")))
    }

    @Test fun aSlotToCopyIntoIsNamedAsItsNewestSaveOrEmpty() {
        val saves = listOf(save("a", "title", at(25, 20)), save("b", "title", at(26, 20)), save("c", "autosave", at(25, 7)))
        val names = saveNames(saves, listOf("title"), style, now, zone)
        assertEquals("Oct 26", destinationName(SaveDestination("title", "azahar", "Azahar"), saves, names, listOf("title")))
        assertEquals("autosave", destinationName(SaveDestination("autosave", "azahar", "Argosy"), saves, names, listOf("title")))
        assertEquals(SavePickerCopy.emptySlot, destinationName(SaveDestination("sav", "melonds", "melonDS"), saves, names, listOf("title")))
        val copy = SavePickerChoice.Copy(saves[2], SaveDestination("title", "azahar", "Azahar"))
        assertEquals("Copy to Oct 26?", SavePickerCopy.confirmationTitle(copy, "Azahar", "Oct 26"))
    }

    @Test fun foldcadeOnFoldcadeNamesTheDeviceOnce() {
        val foldcade = save("a", "title", 0).copy(origin = SavePickerOrigin("foldcade", "Foldcade", thisDevice = false), emulatorLabel = "Azahar")
        assertEquals("Foldcade · Azahar", SavePickerCopy.sourceDetail(foldcade))
        val thor = foldcade.copy(origin = SavePickerOrigin("argosy", "AYN Thor", thisDevice = false))
        assertEquals("Argosy on AYN Thor · Azahar", SavePickerCopy.sourceDetail(thor))
    }

    @Test fun duckStationsHowToReadsAsNumberedSteps() {
        assertEquals(listOf("Open it.", "Pick Foldcade."), numberedSteps("1. Open it.\n2. Pick Foldcade."))
        assertNull(numberedSteps("Open it. Pick Foldcade."))
        assertNull(numberedSteps("1. Open it.\n3. Skipped a number."))
        assertNull(numberedSteps("1. Only one."))
        assertNull(numberedSteps(null))
    }

    @Test fun rommsButtonSaysWhatItDoesNow() {
        val code = PairingCode("EUUJNSZV", "https://romm.example/pair/device?user_code=EUUJNSZV")
        assertEquals("EUUJ NSZV", code.spaced)
        assertEquals("romm.example/pair/device", code.shortUrl)
        assertEquals(Copy.connecting, rommConnectAction(busy = true, tokenTyped = true, originTyped = true, code = null, retry = false))
        assertEquals(Copy.connect, rommConnectAction(busy = false, tokenTyped = true, originTyped = true, code = code, retry = false))
        assertEquals(Copy.waitingForPhone, rommConnectAction(busy = false, tokenTyped = false, originTyped = false, code = null, retry = false))
        assertEquals(Copy.waitingForPhone, rommConnectAction(busy = false, tokenTyped = false, originTyped = true, code = code, retry = false))
        assertEquals(Copy.pairWithPhone, rommConnectAction(busy = false, tokenTyped = false, originTyped = true, code = null, retry = true))
        assertEquals(listOf(ConnectField.Origin, ConnectField.Token, ConnectField.Save), connectFields(ConnectKind.Romm))
    }

    private val signedOut = PickerModel(count = 1, rowsPerPage = 1, rommSaved = true, rommSignIn = true)

    @Test fun signedOutRommOffersSignInWhereSignOutWasAndWearsTheMark() {
        val connection = SidePanel(Side.Left, HostScreen.Top, PanelLevel.RommConnection, 0, GridFocus(), connection = SignedInBackend("romm", "RomM"))
        val rows = panelRows(connection, signedOut)
        assertEquals(listOf(Copy.signIn, Copy.remove), rows.map { rowText(it, signedOut).label })
        assertEquals(listOf(Copy.editRommConnection, Copy.signOut), rows.map { rowText(it, signedOut.copy(rommSignIn = false)).label })
        val tile = librarySources(signedOut).first()
        assertEquals(TileStatus.Attention, settingsTileStatus(tile, signedOut))
        assertNull(settingsTileStatus(tile, signedOut.copy(rommSignIn = false, signedIn = listOf(SignedInBackend("romm", "RomM")))))
        // Remove asks first too, and names what it does.
        val (asking, _) = reduce(signedOut.copy(panel = connection.copy(index = 1)), Meaning.Activate, HostScreen.Top)
        assertEquals(Copy.removeRommTitle, asking.dialog?.title)
        assertEquals(Copy.remove, dialogButtonLabel(DialogButton.SignOut, asking.dialog?.subject))
        // Sign in opens the form.
        val (signing, effect) = reduce(signedOut.copy(panel = connection), Meaning.Activate, HostScreen.Top)
        assertEquals(Effect.EditRommConnection, effect)
        assertTrue(signing.connectOpen)
    }

    @Test fun theSignInNoticeStandsAndOpensTheForm() {
        val notice = rommSignInNotice(5)
        assertFalse(notice.dismissable)
        assertEquals(Copy.rommSignInNoticeTitle, notice.title)
        val model = signedOut.copy(notices = listOf(notice))
        val panel = SidePanel(Side.Right, HostScreen.Top, PanelLevel.Root, rightRows(model.notices).indexOf(Row.Notice(notice.id)), GridFocus())
        val opened = model.copy(panel = panel)
        assertTrue(panelHints(opened)!!.any { it.label == Copy.signIn })
        assertFalse(panelHints(opened)!!.any { it.label == NoticeCopy.dismiss })
        val (form, effect) = reduce(opened, Meaning.Activate, HostScreen.Top)
        assertEquals(Effect.EditRommConnection, effect)
        assertTrue(form.connectOpen)
        assertNull(form.panel)
    }

    @Test fun aGapWaitingOnRommLeadsItsMenuWithSignIn() {
        val rows = actionRows(TileActions(signIn = true, move = true, remove = RemoveVerb.Remove))
        assertEquals(listOf(Row.SignInRomm, Row.MoveTile, Row.RemoveTile), rows)
        // Sign in is a row over the verbs, so Move stays a round tile under it.
        assertEquals(1..1, tileBlock(rows))
        // With Undo over it too, as right after removing another gap.
        val undoing = actionRows(TileActions(undo = true, signIn = true, move = true, remove = RemoveVerb.Remove))
        assertEquals(listOf(Row.Undo, Row.SignInRomm, Row.MoveTile, Row.RemoveTile), undoing)
        assertEquals(2..2, tileBlock(undoing))
        assertFalse(Row.SignInRomm in actionRows(TileActions(move = true, remove = RemoveVerb.Remove)))
    }

    @Test fun everyEmulatorRowThatMustBeActedOnReadsNotSyncing() {
        val duck = PlayerSaveSetting("duckstation", "DuckStation", false, hostedTitle = "Cards", installed = true)
        val model = PickerModel(count = 1, rowsPerPage = 1, playerSaves = listOf(duck))
        // DuckStation's own folder is for its BIOS; its memory cards are what sync.
        assertEquals(RowText(Copy.biosFolder, Copy.choose), rowText(Row.PlayerSave("duckstation"), model))
        assertEquals(RowControl.Value(Copy.notSyncing, attention = true), rowControl(Row.HostedSaves("duckstation"), model))
        assertEquals(RowText(Copy.saveFolder, Copy.notSyncing), rowText(Row.PlayerSave("azahar"), model.copy(playerSaves = listOf(PlayerSaveSetting("azahar", "Azahar", false, installed = true)))))
        // Set up saves says how many are left, and nothing once all sync.
        val left = saveSetupLeft(saveSetupTiles(model, SaveSetupPage()), SaveSetupPage())
        assertEquals(1, left)
        assertEquals(RowControl.Value(SaveSetupCopy.toGo(1), attention = true), rowControl(Row.SaveSetup, model))
        val synced = model.copy(playerSaves = listOf(duck.copy(hostedInUse = true)))
        assertEquals(RowControl.Opens, rowControl(Row.SaveSetup, synced))
    }

    @Test fun aPageOrDialogOnAScreenTakesItsKeys() {
        val model = PickerModel(count = 1, rowsPerPage = 1)
        assertFalse(model.pageOn(HostScreen.Bottom))
        // Backup moved onto the bottom, beside an app on the top.
        val backup = model.copy(settings = SettingsPage(HostScreen.Bottom), dialog = backupDialog(HostScreen.Bottom, undo = true))
        assertTrue(backup.pageOn(HostScreen.Bottom))
        assertFalse(backup.pageOn(HostScreen.Top))
        assertTrue(model.copy(connectOpen = true, connectScreen = HostScreen.Bottom).pageOn(HostScreen.Bottom))
        // A dialog alone takes the keys but is not a full page.
        val dialogOnly = model.copy(dialog = backupDialog(HostScreen.Bottom))
        assertTrue(dialogOnly.pageOn(HostScreen.Bottom))
        assertFalse(dialogOnly.fullPageOn(HostScreen.Bottom))
        assertTrue(backup.fullPageOn(HostScreen.Bottom))
    }

    @Test fun theBackupDialogClosesOnBackWithoutANotNow() {
        val dialog = backupDialog(HostScreen.Bottom, undo = true)
        assertFalse(DialogButton.NotNow in dialog.buttons)
        val (closed, effect) = reduce(PickerModel(count = 0, rowsPerPage = 1, dialog = dialog), Meaning.Back, HostScreen.Bottom)
        assertNull(closed.dialog)
        assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.Backup, null), effect)
    }
}
