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
}
