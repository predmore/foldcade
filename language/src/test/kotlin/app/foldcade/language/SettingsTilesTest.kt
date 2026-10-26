package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Your things are tiles, settings are rows: Emulators is a shelf of emulators, Library leads with its sources. */
class SettingsTilesTest {
    private val retroArchPlayer = PlayerSaveSetting(
        "retroarch.nes", "RetroArch", chosen = true, installed = true, packages = listOf("com.retroarch.aarch64"),
    )
    private val armsx2 = PlayerSaveSetting(
        "armsx2", "ARMSX2", chosen = true, saveSyncing = true, installed = true, packages = listOf("come.nanodata.armsx2"),
    )
    private val azahar = PlayerSaveSetting("azahar", "Azahar", chosen = false, installed = true, packages = listOf("io.github.lime3ds.android"))
    private val edenPlayer = PlayerSaveSetting("eden", "Eden", chosen = true, installed = true, packages = listOf("dev.eden.eden_emulator"))
    private val melonds = PlayerSaveSetting("melonds", "melonDS", chosen = true)
    private val romm = SignedInBackend("romm", "RomM")
    private val roms = LibraryFolder("content://roms", "roms")
    private val steam = LibraryFolder("content://steam", "Steam")
    private val gameWin = StreamHostSetting("8fe9e201", "game-win", paired = true)

    private val emulators = PickerModel(
        count = 4,
        rowsPerPage = 1,
        homeRoleHeld = true,
        signedIn = listOf(romm),
        playerSaves = listOf(retroArchPlayer, melonds, armsx2, azahar, edenPlayer),
        retroArch = RetroArchSetting(cores = 2, packageName = "com.retroarch.aarch64"),
        eden = EdenSetting(fromRomm = true, packageName = "dev.eden.eden_emulator"),
    )

    private val library = PickerModel(
        count = 4,
        rowsPerPage = 1,
        homeRoleHeld = true,
        signedIn = listOf(romm),
        folders = listOf(roms, steam),
        streamHosts = listOf(gameWin),
    )

    private fun tile(key: String) = Row.SettingsTile(SettingsInner.Emulator(key))

    private fun onCategory(model: PickerModel, category: SettingsCategory): PickerModel {
        val index = settingsCategories(model).indexOf(category)
        return model.copy(settings = SettingsPage(HostScreen.Top, category = index, onRows = true))
    }

    private fun PickerModel.press(vararg meanings: Meaning): PickerModel =
        meanings.fold(this) { model, meaning -> reduce(model, meaning, HostScreen.Top).first }

    @Test
    fun eachInstalledEmulatorIsOneTileHoldingOnlyItsOwnRows() {
        val shelf = emulatorShelf(emulators)
        // A to Z, and melonDS is not installed, so it has no tile.
        assertEquals(listOf("ARMSX2", "Azahar", "Eden", "RetroArch"), shelf.map { it.label })
        assertEquals(shelf.map { tile(it.key) }, settingsRows(SettingsCategory.Players, emulators))
        assertEquals(listOf(Row.PlayerSave("armsx2"), Row.SaveSync("armsx2")), shelf.single { it.key == "armsx2" }.rows)
        // RetroArch's cores and Eden's keys join the player that is that app, not a row of their own.
        assertEquals(listOf(Row.PlayerSave("retroarch.nes"), Row.RetroArchCores), shelf.single { it.key == "retroarch.nes" }.rows)
        assertEquals(listOf(Row.PlayerSave("eden"), Row.EdenFolder), shelf.single { it.key == "eden" }.rows)
        assertEquals("com.retroarch.aarch64", shelf.single { it.key == "retroarch.nes" }.packageName)
    }

    @Test
    fun retroArchAndEdenWithoutASaveFolderStillGetATile() {
        val loose = emulators.copy(playerSaves = listOf(azahar))
        val shelf = emulatorShelf(loose)
        assertEquals(listOf("azahar", EDEN_SHELF_KEY, RETROARCH_SHELF_KEY), shelf.map { it.key })
        assertEquals(listOf(Row.EdenFolder), shelf.single { it.key == EDEN_SHELF_KEY }.rows)
        assertEquals(listOf(Row.RetroArchCores), shelf.single { it.key == RETROARCH_SHELF_KEY }.rows)
        // Eden's keys come from RomM: signed out, Eden has nothing to set here.
        assertFalse(emulatorShelf(loose.copy(signedIn = emptyList())).any { it.key == EDEN_SHELF_KEY })
    }

    @Test
    fun aTileWearsTheCheckOnlyWhileEveryRowIsFine() {
        assertEquals(TileStatus.Fine, settingsTileStatus(tile("armsx2"), emulators))
        assertEquals(TileStatus.Attention, settingsTileStatus(tile("azahar"), emulators))
        val cardOff = emulators.copy(playerSaves = listOf(armsx2.copy(saveSyncing = false, saveSyncSteps = "Use a folder card")))
        assertEquals(TileStatus.Attention, settingsTileStatus(tile("armsx2"), cardOff))
        val noCores = emulators.copy(retroArch = RetroArchSetting(cores = null, packageName = "com.retroarch.aarch64"))
        assertEquals(TileStatus.Attention, settingsTileStatus(tile("retroarch.nes"), noCores))
        val noKeys = emulators.copy(eden = EdenSetting(fromRomm = false, packageName = "dev.eden.eden_emulator"))
        assertEquals(TileStatus.Attention, settingsTileStatus(tile("eden"), noKeys))
        // Sources have no badge.
        assertNull(settingsTileStatus(Row.SettingsTile(SettingsInner.Add), library))
    }

    @Test
    fun anEmulatorsPageListsShortValuesUnderItsName() {
        assertEquals("ARMSX2", settingsInnerTitle(SettingsInner.Emulator("armsx2"), emulators))
        assertEquals(RowText(Copy.saveFolder, Copy.playerSavesSet), rowText(Row.PlayerSave("armsx2"), emulators))
        assertEquals(RowText(Copy.memoryCard, Copy.hostedSavesSet), rowText(Row.SaveSync("armsx2"), emulators))
        assertEquals(RowControl.Value(Copy.notSyncing, attention = true), rowControl(Row.PlayerSave("azahar"), emulators))
        assertEquals(RowText(Copy.retroArchCores, "2 ${Copy.cores}"), rowText(Row.RetroArchCores, emulators))
        val duck = PlayerSaveSetting("duckstation", "DuckStation", chosen = false, hostedTitle = "DuckStation memory cards", hostedInUse = true, installed = true)
        val hosted = emulators.copy(playerSaves = listOf(duck))
        assertEquals(RowText("Memory cards", Copy.hostedSavesSet), rowText(Row.HostedSaves("duckstation"), hosted))
    }

    @Test
    fun aOnATileOpensItsPageAndBackReturnsToThatTile() {
        val shelf = onCategory(emulators, SettingsCategory.Players).press(Meaning.MoveRight, Meaning.MoveRight)
        assertEquals(tile("eden"), settingsFocus(shelf))
        val (page, effect) = reduce(shelf, Meaning.Activate, HostScreen.Top)
        assertNull(effect)
        assertEquals(SettingsInner.Emulator("eden"), page.settings!!.inner)
        assertEquals(0, page.settings!!.row)
        assertEquals(Row.PlayerSave("eden"), settingsFocus(page))
        assertEquals(Row.EdenFolder, settingsFocus(page.press(Meaning.MoveDown)))

        val back = page.press(Meaning.MoveDown, Meaning.Back)
        assertNull(back.settings!!.inner)
        assertTrue(back.settings!!.onRows)
        assertEquals(tile("eden"), settingsFocus(back))
        // Left on a row with nothing to step goes back to the tile too.
        assertEquals(shelf.settings, page.press(Meaning.MoveLeft).settings)
        // Then Back reaches the categories, then closes.
        assertFalse(back.press(Meaning.Back).settings!!.onRows)
        assertNull(back.press(Meaning.Back, Meaning.Back).settings)
        // The drawers and Start close it from a page as well.
        assertNull(page.press(Meaning.Menu).settings)
    }

    @Test
    fun aPageRowKeepsItsActionAndDialog() {
        val onCores = emulators.onSetting(Row.RetroArchCores)
        assertEquals(SettingsInner.Emulator("retroarch.nes"), onCores.settings!!.inner)
        assertEquals(Effect.ShowRetroArchCores, reduce(onCores, Meaning.Activate).second)
        val onAzahar = emulators.onSetting(Row.PlayerSave("azahar"))
        val (closed, choose) = reduce(onAzahar, Meaning.Activate)
        assertEquals(Effect.ChoosePlayerSave("azahar"), choose)
        assertNull(closed.settings)
    }

    @Test
    fun tilesMoveInTwoDirectionsAndTheRowsUnderThemInOne() {
        val rows = settingsRows(SettingsCategory.Library, library)
        // RomM, two folders, the computer, and Add; then Sync games, Sort All, Game art, SteamGridDB.
        assertEquals(
            listOf(
                Row.RommConnection("romm", "RomM"),
                Row.SettingsTile(SettingsInner.Folder(roms.uri)),
                Row.SettingsTile(SettingsInner.Folder(steam.uri)),
                Row.SettingsTile(SettingsInner.Host(gameWin.key)),
                Row.SettingsTile(SettingsInner.Add),
                Row.SyncLibrary,
                Row.Order,
                Row.Artwork,
                Row.ArtKey,
            ),
            rows,
        )
        assertEquals(1, settingsFocusIndex(rows, 0, Meaning.MoveRight))
        assertEquals(3, settingsFocusIndex(rows, 3, Meaning.MoveRight))
        assertNull(settingsFocusIndex(rows, 0, Meaning.MoveLeft))
        assertEquals(2, settingsFocusIndex(rows, 3, Meaning.MoveLeft))
        // Down from any tile of the first line lands on Add, the only tile below.
        assertEquals(4, settingsFocusIndex(rows, 0, Meaning.MoveDown))
        assertEquals(4, settingsFocusIndex(rows, 3, Meaning.MoveDown))
        assertEquals(0, settingsFocusIndex(rows, 4, Meaning.MoveUp))
        assertEquals(4, settingsFocusIndex(rows, 4, Meaning.MoveRight))
        // From the last line to the rows, and Up from the first row back to that line.
        assertEquals(5, settingsFocusIndex(rows, 4, Meaning.MoveDown))
        assertEquals(4, settingsFocusIndex(rows, 5, Meaning.MoveUp))
        assertEquals(6, settingsFocusIndex(rows, 5, Meaning.MoveDown))
        assertEquals(8, settingsFocusIndex(rows, 8, Meaning.MoveDown))

        val model = onCategory(library, SettingsCategory.Library)
        assertEquals(Row.SyncLibrary, settingsFocus(model.press(Meaning.MoveDown, Meaning.MoveDown)))
        val categories = model.press(Meaning.MoveLeft).settings!!
        assertFalse(categories.onRows)
        // Left and right on a choice row still step it.
        val onSort = model.press(Meaning.MoveDown, Meaning.MoveDown, Meaning.MoveDown)
        assertEquals(Row.Order, settingsFocus(onSort))
        assertEquals(LibrarySort.RecentlyPlayed, onSort.press(Meaning.MoveLeft).sort)
    }

    @Test
    fun librarysRowsAreItsSettingsAndPlayTimeLivesInSystem() {
        val rows = settingsRows(SettingsCategory.Library, library)
        assertFalse(Row.AddNewGames in rows)
        assertFalse(Row.UsageAccess in rows)
        assertTrue(Row.UsageAccess in settingsRows(SettingsCategory.Screens, library))
        // New games on Home stays in Edit home.
        assertTrue(Row.AddNewGames in editHomeRows(library))
        // A source tile is named for its source.
        assertEquals("RomM", rowText(rows[0], library).label)
        assertEquals("Steam", rowText(rows[2], library).label)
        assertEquals("game-win", rowText(rows[3], library).label)
        assertEquals(Copy.add, rowText(rows[4], library).label)
    }

    @Test
    fun aFolderPageRemovesItAndLandsBackOnTheShelf() {
        val page = library.onSetting(Row.ForgetFolder(roms.uri, roms.label))
        assertEquals(SettingsInner.Folder(roms.uri), page.settings!!.inner)
        assertEquals(Copy.remove, rowText(Row.ForgetFolder(roms.uri, roms.label), page).label)
        // A held A that opened the page does not go on to remove the folder.
        assertNull(reduce(page, Meaning.Activate, HostScreen.Top, repeatCount = 1).second)
        val (removed, effect) = reduce(page, Meaning.Activate, HostScreen.Top)
        assertEquals(Effect.ForgetFolder(roms.uri), effect)
        assertNull(removed.settings!!.inner)
        assertEquals(1, removed.settings!!.row)
        assertNull(removed.panel)
        // Once the folder is gone, the focus rests on the tile that took its place.
        val gone = removed.copy(folders = listOf(steam))
        assertEquals(Row.SettingsTile(SettingsInner.Folder(steam.uri)), settingsFocus(gone))
        // A page left open on a folder that went away shows the shelf instead.
        val stale = page.copy(folders = listOf(steam))
        assertEquals(Row.SettingsTile(SettingsInner.Folder(steam.uri)), settingsFocus(stale.copy(settings = stale.settings!!.copy(row = 1))))
        assertNull(settingsShown(stale.settings!!, stale).first.inner)
    }

    @Test
    fun aComputersPageIsItsSwitch() {
        val page = library.onSetting(Row.StreamHost(gameWin.key))
        assertEquals(SettingsInner.Host(gameWin.key), page.settings!!.inner)
        val (asking, effect) = reduce(page, Meaning.Activate, HostScreen.Top)
        assertNull(effect)
        assertEquals(DialogKind.ForgetHost, asking.dialog?.kind)
        assertEquals(page.settings, asking.settings)
        val unpaired = library.copy(streamHosts = listOf(gameWin.copy(paired = false))).onSetting(Row.StreamHost(gameWin.key))
        val (pairing, pair) = reduce(unpaired, Meaning.Activate, HostScreen.Top)
        assertEquals(Effect.PairStreamHost(gameWin.key), pair)
        assertEquals(unpaired.settings, pairing.settings)
    }

    @Test
    fun addOffersRommOnlyWhileNoServerIsSaved() {
        assertEquals(listOf(Row.AddFolder), settingsInnerRows(SettingsInner.Add, library))
        val bare = library.copy(signedIn = emptyList(), rommSaved = false)
        assertEquals(listOf(Row.AddFolder, Row.AddRomm), settingsInnerRows(SettingsInner.Add, bare))
        val page = bare.onSetting(Row.AddFolder)
        assertEquals(SettingsInner.Add, page.settings!!.inner)
        // Adding a folder explains first, over the page, and the page stays.
        val (explained, effect) = reduce(page, Meaning.Activate, HostScreen.Top)
        assertNull(effect)
        assertEquals(DialogKind.Folder, explained.dialog?.kind)
        assertEquals(page.settings, explained.settings)
    }

    @Test
    fun aFolderAddedFromAddShowsItsNewTile() {
        val page = library.copy(folders = listOf(roms)).onSetting(Row.AddFolder)
        val added = page.withFolders(listOf(roms, steam))
        assertNull(added.settings!!.inner)
        assertEquals(Row.SettingsTile(SettingsInner.Folder(steam.uri)), settingsFocus(added))
        // Elsewhere the page stays where it is.
        val shelf = onCategory(library.copy(folders = listOf(roms)), SettingsCategory.Library)
        assertEquals(shelf.settings, shelf.withFolders(listOf(roms, steam)).settings)
        assertEquals(page.settings, page.withFolders(listOf(roms)).settings)
    }

    @Test
    fun rommsTileOpensItsConnectionAndBackReturnsToIt() {
        val onRomm = onCategory(library, SettingsCategory.Library)
        assertEquals(Row.RommConnection("romm", "RomM"), settingsFocus(onRomm))
        val (opened, effect) = reduce(onRomm, Meaning.Activate, HostScreen.Top)
        assertNull(effect)
        assertEquals(PanelLevel.RommConnection, opened.panel?.level)
        assertEquals(onRomm.settings, reduce(opened, Meaning.Back, HostScreen.Top).first.settings)
    }

    @Test
    fun themeStepsItsPackAndAOpensThemes() {
        val model = PickerModel(count = 1, rowsPerPage = 1, themes = listOf(Copy.builtIn, "Afterglow")).onSetting(Row.Theme)
        assertEquals(1, model.press(Meaning.MoveRight).themeIndex)
        assertEquals(1, model.press(Meaning.MoveLeft).themeIndex)
        val (opened, effect) = reduce(model, Meaning.Activate, HostScreen.Top)
        assertEquals(Effect.OpenThemes, effect)
        assertEquals(0, opened.themeIndex)
        assertEquals(model.settings, opened.settings)
        assertEquals(RowControl.Choice("Built-in"), rowControl(Row.Theme, model))
        assertEquals(listOf(Row.Theme, Row.IconTheme, Row.Background, Row.MotionSpeed, Row.ButtonLabels), settingsRows(SettingsCategory.Personalization, model))
    }

    @Test
    fun anotherCategoryNeverKeepsAPage() {
        val page = emulators.onSetting(Row.RetroArchCores)
        val categories = page.press(Meaning.Back, Meaning.Back)
        assertFalse(categories.settings!!.onRows)
        val moved = categories.press(Meaning.MoveUp)
        assertNull(moved.settings!!.inner)
        assertEquals(0, moved.settings!!.row)
        val back = moved.press(Meaning.MoveDown, Meaning.MoveRight)
        assertNull(back.settings!!.inner)
        assertEquals(tile("armsx2"), settingsFocus(back))
    }

    @Test
    fun aHeldAThatOpenedAComputersPageDoesNotPairIt() {
        val unpaired = library.copy(streamHosts = listOf(gameWin.copy(paired = false)))
        val page = unpaired.onSetting(Row.StreamHost(gameWin.key))
        assertNull(reduce(page, Meaning.Activate, HostScreen.Top, repeatCount = 1).second)
        assertEquals(Effect.PairStreamHost(gameWin.key), reduce(page, Meaning.Activate, HostScreen.Top).second)
    }

    @Test
    fun theCategoryStaysPutWhenEmulatorsLeavesUnderIt() {
        val one = emulators.copy(playerSaves = listOf(azahar), retroArch = null, eden = null, askNoticeAccess = false)
        val before = one.onSetting(Row.UsageAccess)
        val gone = before.copy(playerSaves = emptyList())
        assertFalse(SettingsCategory.Players in settingsCategories(gone))
        val kept = retainSettingsCategory(before, gone)
        assertEquals(SettingsCategory.Screens, settingsCategories(kept)[kept.settings!!.category])
        assertEquals(Row.UsageAccess, settingsFocus(kept))
        // The page's own category leaving drops the focus to the categories.
        val onEmulators = onCategory(one, SettingsCategory.Players)
        val left = retainSettingsCategory(onEmulators, onEmulators.copy(playerSaves = emptyList()))
        assertFalse(left.settings!!.onRows)
    }
}
