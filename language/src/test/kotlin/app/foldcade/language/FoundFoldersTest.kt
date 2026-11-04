package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The found ROM folders' offer, their R1 notice, Settings › Library's Find games, and the Home ask. */
class FoundFoldersTest {
    @Test
    fun theOfferNamesWhatWasSeen() {
        assertEquals(
            "ROMs has Nintendo DS and Sega Genesis games. Allow file access once and they’re on Home.",
            FoundFolderCopy.offerBody(listOf("ROMs"), listOf("Nintendo DS", "Sega Genesis")),
        )
        assertEquals(
            "Emulation/roms and your SD card have games for 4 systems. Allow file access once and they’re on Home.",
            FoundFolderCopy.offerBody(listOf("Emulation/roms", FoundFolderCopy.yourCard), listOf("Game Boy", "PSP", "Sega Genesis", "SNES")),
        )
        val prompt = findGamesPrompt(HostScreen.Bottom, listOf("ROMs"), listOf("PSP"))
        assertEquals(DialogKind.FindGames, prompt.kind)
        assertEquals(listOf(DialogButton.Allow, DialogButton.NotNow), prompt.buttons)
        assertEquals(0, prompt.index)
        // B is Not now.
        val (closed, effect) = reduce(PickerModel(count = 1, rowsPerPage = 1, dialog = prompt), Meaning.Back)
        assertNull(closed.dialog)
        assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.FindGames, null), effect)
    }

    @Test
    fun theNoticeCountsGamesAndNamesTheFolder() {
        assertEquals("Found 214 games in ROMs", FoundFolderCopy.foundTitle(214, listOf("ROMs")))
        assertEquals("Found 1 game in SD card · roms", FoundFolderCopy.foundTitle(1, listOf("SD card · roms")))
        assertEquals("Found 230 games in 2 folders", FoundFolderCopy.foundTitle(230, listOf("ROMs", "SD card · roms")))
        assertEquals("ROMs, SD card · roms", FoundFolderCopy.foundBody(listOf("ROMs", "SD card · roms")))
    }

    @Test
    fun findGamesFollowsAndroidsAccess() {
        val model = PickerModel(count = 1, rowsPerPage = 1)
        assertEquals(true, Row.FindGames in settingsRows(SettingsCategory.Library, model))
        assertEquals(RowControl.Toggle(false), rowControl(Row.FindGames, model))
        assertEquals(SettingsCopy.accessAsk, rowText(Row.FindGames, model).value)
        val granted = model.copy(allFilesGranted = true)
        assertEquals(RowControl.Toggle(true), rowControl(Row.FindGames, granted))
        assertEquals(SettingsCopy.accessAllowed, rowText(Row.FindGames, granted).value)
        val onRow = granted.onSetting(Row.FindGames)
        assertEquals(Effect.OpenAllFilesAccess, reduce(onRow, Meaning.Activate).second)
    }

    @Test
    fun aFoundFolderIsATileWhosePageRemovesIt() {
        val found = LibraryFolder("content://app.foldcade.library/tree/primary%3AROMs", "ROMs", path = "ROMs", games = 214, found = true)
        val model = PickerModel(count = 1, rowsPerPage = 1, folders = listOf(found))
        val tile = Row.SettingsTile(SettingsInner.Folder(found.uri))
        assertEquals(true, tile in settingsRows(SettingsCategory.Library, model))
        assertEquals("ROMs", settingsTileLabel(SettingsInner.Folder(found.uri), model))
        assertEquals(listOf(Row.ForgetFolder(found.uri, "ROMs")), settingsInnerRows(SettingsInner.Folder(found.uri), model))
        assertEquals("214 games", libraryFolderDetail(found))
        // A card's page is named for the card, so its line says only the folder.
        assertEquals("roms · 1 game", libraryFolderDetail(found.copy(label = "SD card", path = "roms", games = 1)))
    }

    @Test
    fun theHomeAskNamesTheAppsThatNeedIt() {
        assertEquals(Copy.homePromptBody, homePrompt(HostScreen.Bottom).body)
        assertEquals("Moonlight can add games here once Foldcade is Home.", homePrompt(HostScreen.Bottom, listOf("Moonlight")).body)
        assertEquals(
            "Moonlight and DroidDeck can add games here once Foldcade is Home.",
            homePrompt(HostScreen.Bottom, listOf("Moonlight", "DroidDeck")).body,
        )
    }

    private val offer = RomFolderOffer(listOf("ROMs"), listOf("Nintendo DS"), "primary:ROMs")

    @Test
    fun theEmptyLibraryLeadsWithFindGamesWhileFoldersWait() {
        val model = PickerModel(count = 3, rowsPerPage = 1, gridKind = GridKind.NoLibrary, libraryGrid = true, romFolderOffer = offer)
        assertEquals(listOf(NoLibraryAction.FindGames, NoLibraryAction.AddFolder, NoLibraryAction.AddRomm), noLibraryActions(model))
        assertEquals(Effect.OpenAllFilesAccess, reduce(model, Meaning.Activate).second)
        // Add a folder opens the picker at the folder seen, so it needs no explainer even the first time.
        val onFolder = reduce(model, Meaning.MoveRight).first
        assertEquals(1, onFolder.focus.cellIndex)
        val (added, effect) = reduce(onFolder.copy(folderGrantPending = true), Meaning.Activate)
        assertEquals(Effect.AddFolder, effect)
        assertNull(added.dialog)
        val onRomm = reduce(onFolder, Meaning.MoveRight).first
        assertEquals(Effect.OpenAddRomm, reduce(onRomm, Meaning.Activate).second)
        // Without anything seen, the buttons are as before.
        val none = model.copy(count = 2, romFolderOffer = null)
        assertEquals(listOf(NoLibraryAction.AddFolder, NoLibraryAction.AddRomm), noLibraryActions(none))
        assertEquals(DialogKind.Folder, reduce(none.copy(folderGrantPending = true), Meaning.Activate).first.dialog?.kind)
    }

    @Test
    fun addGamesPutsTheWayThatFitsFirst() {
        val seen = addGamesDialog(HostScreen.Bottom, offer)
        assertEquals(DialogKind.AddGames, seen.kind)
        assertEquals(listOf(DialogButton.FindGames, DialogButton.AddFolder, DialogButton.SignInRomm), seen.buttons)
        assertEquals(Copy.addGames, seen.title)
        assertEquals(FoundFolderCopy.offerBody(offer.places, offer.systems), seen.body)
        // From the tile, Back only closes.
        val (closed, effect) = reduce(PickerModel(count = 1, rowsPerPage = 1, dialog = seen), Meaning.Back)
        assertNull(closed.dialog)
        assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.AddGames, null), effect)
        // Nothing seen: RomM is a phone code away, then a folder. The first run's ask has Not now.
        val asked = addGamesDialog(HostScreen.Bottom, null, ask = true)
        assertEquals(listOf(DialogButton.SignInRomm, DialogButton.AddFolder, DialogButton.NotNow), asked.buttons)
        assertEquals(FoundFolderCopy.noRomsTitle, asked.title)
        assertEquals(2, asked.safeIndex)
        assertEquals(FoundFolderCopy.signInRomm, dialogButtonLabel(DialogButton.SignInRomm))
        assertEquals(FoundFolderCopy.findGames, dialogButtonLabel(DialogButton.FindGames))
        assertEquals("Find games in ROMs, or add a folder", FoundFolderCopy.addGamesLine(offer))
        assertEquals("Sign in to RomM, or add a folder", FoundFolderCopy.addGamesLine(null))
    }

    @Test
    fun retroArchsCoresAskNamesItsSystems() {
        val dialog = retroArchCoresFirstDialog(HostScreen.Bottom, listOf("Sega Genesis"))
        assertEquals(DialogKind.RetroArchCores, dialog.kind)
        assertEquals(listOf(DialogButton.ContinueGrant, DialogButton.NotNow), dialog.buttons)
        assertEquals(
            "RetroArch plays your Sega Genesis games. Let Foldcade see its cores and it gets any that are missing. Next, tap Use this folder, then Allow.",
            dialog.body,
        )
        assertEquals(true, FoundFolderCopy.retroArchCoresBody(listOf("A", "B", "C")).startsWith("RetroArch plays your games."))
    }
}
