package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings › Library reads states on its right, adds a folder in one press, and holds a folder's Remove. */
class LibrarySettingsTest {
    private val romm = SignedInBackend("romm", "RomM")
    private val roms = LibraryFolder("content://roms", "roms", games = 12)
    private val empty = LibraryFolder("content://empty", "empty", path = "Download/empty")
    private val library = PickerModel(count = 4, rowsPerPage = 1, homeRoleHeld = true, signedIn = listOf(romm), folders = listOf(roms))
    private val addTile = Row.SettingsTile(SettingsInner.Add)

    private fun onAddTile(model: PickerModel): PickerModel = model.onSetting(addTile)

    @Test
    fun withRommSavedTheAddTileOpensThePickerItself() {
        assertEquals(listOf(Row.AddFolder), settingsInnerRows(SettingsInner.Add, library))
        val page = onAddTile(library.copy(folderGrantPending = false))
        val (next, effect) = reduce(page, Meaning.Activate, HostScreen.Top)
        assertEquals(Effect.AddFolder, effect)
        // No one-row page in between: the card stays on its shelf, Add still focused for the way back.
        assertEquals(page.settings, next.settings)
        assertEquals(addTile, settingsFocus(next))
    }

    @Test
    fun aKnownRomFolderNeedsNoExplainerEvenTheFirstTime() {
        val first = onAddTile(library.copy(folderGrantPending = true, folderStart = "primary:ROMs"))
        val (next, effect) = reduce(first, Meaning.Activate, HostScreen.Top)
        assertEquals(Effect.AddFolder, effect)
        assertNull(next.dialog)
        // With nowhere to start, the one-time explainer says what to pick, over the shelf.
        val (explained, none) = reduce(onAddTile(library.copy(folderGrantPending = true)), Meaning.Activate, HostScreen.Top)
        assertNull(none)
        assertEquals(DialogKind.Folder, explained.dialog?.kind)
        assertEquals(Copy.folderGrantBody, explained.dialog?.body)
        assertTrue(explained.dialog!!.body.contains("Use this folder"))
    }

    @Test
    fun withoutRommAddIsAPageWhoseRowsDontRepeatAdd() {
        val bare = library.copy(signedIn = emptyList(), rommSaved = false)
        assertEquals(listOf(Row.AddFolder, Row.AddRomm), settingsInnerRows(SettingsInner.Add, bare))
        assertNull(settingsTileShortcut(addTile, bare))
        assertEquals(Copy.add, settingsInnerTitle(SettingsInner.Add, bare))
        assertEquals("Folder", rowText(Row.AddFolder, bare).label)
        assertEquals("RomM server", rowText(Row.AddRomm, bare).label)
        val (opened, effect) = reduce(onAddTile(bare), Meaning.Activate, HostScreen.Top)
        assertNull(effect)
        assertEquals(SettingsInner.Add, opened.settings?.inner)
    }

    @Test
    fun aFolderAddedFromTheAddTileTakesTheFocus() {
        val page = onAddTile(library)
        val added = page.withFolders(listOf(roms, empty))
        assertEquals(Row.SettingsTile(SettingsInner.Folder(empty.uri)), settingsFocus(added))
    }

    @Test
    fun aNewServerLandsOnItsTileNotOnAdd() {
        val bare = library.copy(signedIn = emptyList(), rommSaved = false)
        val add = bare.onSetting(Row.AddRomm).settings!!
        assertEquals(SettingsInner.Add, add.inner)
        val landed = add.afterRommAdded(library)
        assertNull(landed.inner)
        assertTrue(landed.onRows)
        assertEquals(Row.RommConnection("romm", "RomM"), settingsShown(landed, library).second[landed.row])
        // Any other page is left as it was.
        val shelf = library.onSetting(Row.SyncLibrary).settings!!
        assertEquals(shelf, shelf.afterRommAdded(library))
    }

    @Test
    fun syncGamesSaysHowTheLastSyncEnded() {
        assertEquals(RowControl.None, rowControl(Row.SyncLibrary, library))
        assertEquals(RowControl.Value(Copy.syncing), rowControl(Row.SyncLibrary, library.copy(librarySyncing = true)))
        assertEquals(RowControl.Value(Copy.synced), rowControl(Row.SyncLibrary, library.copy(librarySync = LibrarySync.Done)))
        val offline = library.copy(librarySync = LibrarySync.RommUnreachable)
        assertEquals(RowControl.Value(Copy.rommUnreachable, attention = true), rowControl(Row.SyncLibrary, offline))
        // Syncing again reads Syncing, not the last failure.
        assertEquals(RowControl.Value(Copy.syncing), rowControl(Row.SyncLibrary, offline.copy(librarySyncing = true)))
        // RomM's tile wears the not-syncing mark while it can't be reached.
        assertNull(settingsTileStatus(Row.RommConnection("romm", "RomM"), library))
        assertEquals(TileStatus.Attention, settingsTileStatus(Row.RommConnection("romm", "RomM"), offline))
    }

    @Test
    fun theRightSideIsAlwaysAState() {
        assertEquals(RowText(Copy.steamGridDb, Copy.noKey), rowText(Row.ArtKey, library))
        assertEquals(RowText(Copy.steamGridDb, Copy.keySaved), rowText(Row.ArtKey, library.copy(artKeySaved = true)))
        assertEquals(FoundFolderCopy.autoFindGames, rowText(Row.FindGames, library).label)
    }

    @Test
    fun aFolderPageSaysWhereItIsAndWhatItHolds() {
        val model = library.copy(folders = listOf(roms, empty))
        assertEquals("12 games", settingsInnerDetail(SettingsInner.Folder(roms.uri), model))
        assertEquals("Download/empty · 0 games", settingsInnerDetail(SettingsInner.Folder(empty.uri), model))
    }

    @Test
    fun aFolderThatBringsNothingSaysSoWithTheWayToPickAgain() {
        val dialog = emptyFolderDialog("empty", HostScreen.Top)
        assertEquals("No games in empty", dialog.title)
        assertEquals(listOf(DialogButton.AddFolder, DialogButton.Ok), dialog.buttons)
        // Back is OK: the folder stays, and games copied in later show up.
        assertEquals(DialogButton.Ok, dialog.buttons[dialog.safeIndex])
    }

    @Test
    fun removingAFolderIsHeld() {
        assertTrue(Row.ForgetFolder(roms.uri, roms.label).destructive())
        assertFalse(Row.SyncLibrary.destructive())
    }

    @Test
    fun noticesWrapToThePageButKeepTheirLists() {
        val text = """
            # MuseScore_General.sf2
            ---

            Permission is hereby granted, free of charge, to any person
            obtaining a copy of this software and associated documentation
            files (the "Software").

            Suren M. Seron
            Scott Hanan

            COPYING
            ---
            The above copyright notice and **this permission** notice shall be
            included in all copies.
        """.trimIndent()
        assertEquals(
            listOf(
                NoticeBlock("MuseScore_General.sf2", heading = true),
                NoticeBlock("Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\")."),
                NoticeBlock("Suren M. Seron\nScott Hanan"),
                NoticeBlock("COPYING", heading = true),
                NoticeBlock("The above copyright notice and this permission notice shall be included in all copies."),
            ),
            noticeBlocks(text),
        )
    }
}
