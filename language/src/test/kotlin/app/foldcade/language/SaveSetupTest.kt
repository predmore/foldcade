package app.foldcade.language

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveSetupTest {
    private val retroArch = PlayerSaveSetting("retroarch.nes", "RetroArch", chosen = false, installed = true,
        packages = listOf("com.retroarch.aarch64"), pick = FolderPick.OpensThere, findable = true)
    private val duck = PlayerSaveSetting("duckstation", "DuckStation", chosen = false, hostedTitle = "DuckStation memory cards",
        hostedSteps = "Point Memory Cards at Foldcade.", installed = true)
    private val arms = PlayerSaveSetting("armsx2", "ARMSX2", chosen = true, saveSyncSteps = "Make a folder card.", installed = true,
        pick = FolderPick.OpensThere)
    private val aps3e = PlayerSaveSetting("aps3e", "aPS3e", chosen = false, installed = true, pick = FolderPick.OwnProvider)
    private val melon = PlayerSaveSetting("melonds", "melonDS", chosen = false, installed = true, pick = FolderPick.OpensThere, findable = true)

    private val model = PickerModel(
        count = 4,
        rowsPerPage = 1,
        focus = GridFocus(cellIndex = 3, lastColumn = 1),
        playerSaves = listOf(retroArch, duck, arms, aps3e, melon),
        retroArch = RetroArchSetting(cores = null, packageName = "com.retroarch.aarch64"),
        eden = EdenSetting(fromRomm = false, packageName = "dev.eden.eden_emulator"),
        signedIn = listOf(SignedInBackend("romm", "RomM")),
    )

    private fun PickerModel.page() = saveSetup!!
    private fun PickerModel.tiles() = saveSetupTiles(this, page())
    private fun PickerModel.tile(key: String) = tiles().first { it.key == key }
    private fun PickerModel.press(meaning: Meaning, repeatCount: Int = 0) = reduce(this, meaning, HostScreen.Bottom, repeatCount)
    private fun PickerModel.on(key: String?) = copy(saveSetup = page().copy(focus = key))
    private fun PickerModel.settle(change: (PickerModel) -> PickerModel) = retainSaveSetup(this, change(this))
    private fun PickerModel.saves(id: String, change: (PlayerSaveSetting) -> PlayerSaveSetting) =
        copy(playerSaves = playerSaves.map { if (it.playerId == id) change(it) else it })

    @Test fun aFolderFoundWithoutAskingSaysWhereOnThePageAndInSettings() {
        val found = model.saves("melonds") { it.copy(chosen = true, foundIn = "saves/nds") }
        assertEquals("saves/nds", rowText(Row.PlayerSave("melonds"), found).value)
        assertEquals(Copy.playerSavesSet, rowText(Row.PlayerSave("melonds"), model.saves("melonds") { it.copy(chosen = true) }).value)
        val tile = found.openSaveSetup(HostScreen.Top).tile("melonds")
        assertEquals("saves/nds", tile.foundIn)
        assertTrue(tile.done)
        assertEquals("Found in saves/nds.", SaveSetupCopy.stepBody(tile))
        // The status chip already says Syncing, so the step has no title of its own.
        assertNull(SaveSetupCopy.stepTitle(tile.step))
        assertNull(SaveSetupCopy.stepBody(found.saves("melonds") { it.copy(foundIn = null) }.openSaveSetup(HostScreen.Top).tile("melonds")))
    }

    @Test fun edenWithoutAUserIsOneInEmulatorStep() {
        val ready = model.copy(eden = EdenSetting(fromRomm = true, packageName = "dev.eden.eden_emulator", setupSteps = Copy.edenUserSteps))
        val eden = ready.openSaveSetup(HostScreen.Top).tile(EDEN_SHELF_KEY)
        val step = eden.step as SetupStep.InApp
        assertEquals(InAppStep.User, step.kind)
        assertEquals(Copy.edenUserSteps, step.steps)
        assertEquals("Add a user in Eden", SaveSetupCopy.stepTitle(step))
        assertEquals("Make a user profile, then come back.", SaveSetupCopy.stepBody(eden))
        assertEquals(EDEN_SHELF_KEY, step.playerId)
        val settled = ready.copy(eden = ready.eden!!.copy(setupSteps = null)).openSaveSetup(HostScreen.Top).tile(EDEN_SHELF_KEY)
        assertTrue(settled.done)
        // Without Eden's folder, the folder comes first and no user step shows.
        val noFolder = model.copy(eden = EdenSetting(fromRomm = false, packageName = "dev.eden.eden_emulator", setupSteps = Copy.edenUserSteps))
        assertTrue(noFolder.openSaveSetup(HostScreen.Top).tile(EDEN_SHELF_KEY).steps.single() is SetupStep.EdenFolder)
    }

    @Test fun aPageBackAfterProcessDeathReturnsToItsTileOnceItLoads() {
        val empty = model.copy(playerSaves = emptyList(), retroArch = null, eden = null)
        val restored = empty.openSaveSetup(HostScreen.Top, target = "melonds")
        // Some emulators load first: the focus waits elsewhere, and the restored tile is still wanted.
        val partial = restored.settle { it.copy(playerSaves = listOf(retroArch)) }
        assertEquals(FIND_SAVES_TILE, partial.page().focus)
        assertEquals("melonds", partial.page().restoring)
        // The pick made behind the picker landed: its check lands, then the focus glides on.
        val loaded = partial.settle { model.copy(saveSetup = it.saveSetup).saves("melonds") { s -> s.copy(chosen = true) } }
        assertEquals("melonds", loaded.page().focus)
        assertEquals("melonds", loaded.page().celebrating)
        assertNull(loaded.page().restoring)
        // A move before then wins over the restore.
        val moved = partial.press(Meaning.MoveRight).first
        assertNull(moved.page().restoring)
    }

    @Test fun theStripIsFindSavesThenEveryEmulatorOnSettingsAToZ() {
        val opened = model.openSaveSetup(HostScreen.Top)
        assertEquals(
            listOf(FIND_SAVES_TILE, "aps3e", "armsx2", "duckstation", "eden", "melonds", "retroarch.nes"),
            opened.tiles().map { it.key },
        )
        assertEquals(emulatorShelf(model).map { it.key }, opened.tiles().drop(1).map { it.key })
        // Find saves leads, so the page opens on it.
        assertEquals(FIND_SAVES_TILE, opened.page().focus)
        assertNull(opened.panel)
        assertNull(opened.settings)
    }

    @Test fun eachEmulatorsStepsComeFromItsOwnSettingsRows() {
        val tiles = model.openSaveSetup(HostScreen.Top).tiles().associateBy { it.key }
        assertEquals(
            listOf(SetupStep.Folder("retroarch.nes", FolderPick.OpensThere, findable = true, done = false), SetupStep.Cores(done = false)),
            tiles.getValue("retroarch.nes").steps,
        )
        // Saves in the folder Foldcade hosts: nothing to pick, one thing to do in DuckStation.
        assertEquals(
            listOf(SetupStep.InApp("duckstation", InAppStep.HostedFolder, "Point Memory Cards at Foldcade.", done = false)),
            tiles.getValue("duckstation").steps,
        )
        val armsSteps = tiles.getValue("armsx2").steps
        assertEquals(2, armsSteps.size)
        assertTrue(armsSteps[0].done)
        assertEquals(SetupStep.InApp("armsx2", InAppStep.MemoryCard, "Make a folder card.", done = false), tiles.getValue("armsx2").step)
        assertEquals(listOf(SetupStep.Folder("aps3e", FolderPick.OwnProvider, findable = false, done = false)), tiles.getValue("aps3e").steps)
        assertEquals(listOf(SetupStep.EdenFolder(done = false)), tiles.getValue("eden").steps)
        assertEquals(SaveSetupCopy.asksOnce, SaveSetupCopy.stepTitle(tiles.getValue("aps3e").step))
        assertEquals(SaveSetupCopy.asksOnce, SaveSetupCopy.stepTitle(tiles.getValue("eden").step))
        assertEquals(SaveSetupCopy.opensThere, SaveSetupCopy.stepBody(tiles.getValue("melonds")))
        assertEquals("Point Memory Cards at Foldcade.", SaveSetupCopy.stepBody(tiles.getValue("duckstation")))
        assertEquals("Open DuckStation", SaveSetupCopy.action(tiles.getValue("duckstation")))
    }

    @Test fun aStepIsDoneExactlyWhenItsSettingsRowStopsNeedingYou() {
        val ready = model.copy(
            playerSaves = listOf(retroArch.copy(chosen = true), duck.copy(hostedInUse = true), arms.copy(saveSyncing = true, saveSyncSteps = null),
                aps3e.copy(chosen = true, setupSteps = "Install firmware in aPS3e."), melon.copy(chosen = true)),
            retroArch = RetroArchSetting(cores = 12, packageName = "com.retroarch.aarch64"),
            eden = EdenSetting(fromRomm = true, packageName = "dev.eden.eden_emulator"),
        )
        val tiles = ready.openSaveSetup(HostScreen.Top).tiles()
        for (tile in tiles) {
            val row = Row.SettingsTile(SettingsInner.Emulator(tile.key))
            val attention = settingsTileStatus(row, ready) == TileStatus.Attention
            assertEquals(tile.key, attention, !tile.done)
        }
        val aps = tiles.first { it.key == "aps3e" }
        assertEquals(SetupStep.InApp("aps3e", InAppStep.Setup, "Install firmware in aPS3e.", done = false), aps.step)
        assertEquals(SaveSetupCopy.finishSetup, SaveSetupCopy.stepTitle(aps.step))
    }

    @Test fun findSavesShowsOnlyWhileItCouldFindAFolderSomeoneNeedsAndStaysOnceAllowed() {
        assertTrue(model.openSaveSetup(HostScreen.Top).tiles().first().findSaves)
        // ARMSX2, DuckStation and aPS3e keep theirs where all-files access never reaches.
        val onlyAppData = model.copy(playerSaves = listOf(duck, arms, aps3e), retroArch = null)
        assertTrue(onlyAppData.openSaveSetup(HostScreen.Top).tiles().none { it.findSaves })
        assertTrue(model.copy(allFilesGranted = true).openSaveSetup(HostScreen.Top).tiles().none { it.findSaves })

        val opened = model.openSaveSetup(HostScreen.Top)
        assertTrue(opened.page().offeredFind)
        val allowed = opened.settle { it.copy(allFilesGranted = true) }
        val find = allowed.tiles().first()
        assertTrue(find.findSaves)
        assertTrue(find.done)
        assertEquals(SettingsCopy.accessAllowed, SaveSetupCopy.status(find, allowed.page()))
        // It was on Find saves, which just turned on: it celebrates, then the focus goes on.
        assertEquals(FIND_SAVES_TILE, allowed.page().celebrating)
        assertEquals("aps3e", allowed.advanceSaveSetup(FIND_SAVES_TILE).page().focus)
    }

    @Test fun findSavesAsksForAccessThenSaysWhatItFoundAndHoldsTheFocusWhileTheyLand() {
        val opened = model.openSaveSetup(HostScreen.Top)
        val ask = opened.tile(FIND_SAVES_TILE)
        assertEquals("Foldcade finds the folders", SaveSetupCopy.stepTitle(ask.step))
        assertEquals("Turn on Allow access, then come back.", SaveSetupCopy.stepBody(ask))
        val motion = MotionStyle()
        assertEquals(motion.durationSetupPause, saveSetupPause(ask, motion))

        val allowed = opened.settle { it.copy(allFilesGranted = true) }
        // While the find runs it says so, rather than that nothing was found.
        assertEquals(SaveSetupCopy.looking, SaveSetupCopy.stepBody(allowed.tile(FIND_SAVES_TILE), checking = true))
        assertEquals("Nothing new found.", SaveSetupCopy.stepBody(allowed.tile(FIND_SAVES_TILE)))
        assertEquals(motion.durationSetupPause, saveSetupPause(allowed.tile(FIND_SAVES_TILE), motion))

        val found = allowed.settle { m ->
            m.saves("melonds") { it.copy(chosen = true, foundIn = "saves/nds") }
                .saves("retroarch.nes") { it.copy(chosen = true, foundIn = "RetroArch/saves") }
                .copy(retroArch = RetroArchSetting(cores = 12, packageName = "com.retroarch.aarch64"))
        }
        val find = found.tile(FIND_SAVES_TILE)
        assertEquals(listOf("melonDS", "RetroArch"), find.found)
        // RetroArch's folder was found even while its cores are still to show.
        val coresLeft = found.copy(retroArch = RetroArchSetting(cores = null, packageName = "com.retroarch.aarch64"))
        assertEquals(listOf("melonDS", "RetroArch"), coresLeft.tile(FIND_SAVES_TILE).found)
        assertEquals("Found melonDS and RetroArch.", SaveSetupCopy.stepBody(find, checking = true))
        assertNull(SaveSetupCopy.stepTitle(find.step))
        // One stagger more for each, so the flips are seen before the focus moves.
        assertEquals(motion.durationSetupPause + 2 * motion.durationSetupStagger, saveSetupPause(find, motion))
        // An emulator's own check pauses the same as ever.
        assertEquals(motion.durationSetupPause, saveSetupPause(found.tile("melonds"), motion))
        assertEquals("Found melonDS.", SaveSetupCopy.found(listOf("melonDS")))
        assertEquals("Found aPS3e, melonDS and RetroArch.", SaveSetupCopy.found(listOf("aPS3e", "melonDS", "RetroArch")))
    }

    @Test fun confirmDoesTheStepWithTheHandlingSettingsAlreadyUses() {
        val opened = model.openSaveSetup(HostScreen.Top)
        val expected = mapOf(
            FIND_SAVES_TILE to Effect.OpenAllFilesAccess,
            "aps3e" to Effect.ChoosePlayerSave("aps3e"),
            "armsx2" to Effect.OpenPlayer("armsx2"),
            "duckstation" to Effect.OpenPlayer("duckstation"),
            "eden" to Effect.ShowEdenFolder,
            "melonds" to Effect.ChoosePlayerSave("melonds"),
            "retroarch.nes" to Effect.ChoosePlayerSave("retroarch.nes"),
        )
        for ((key, effect) in expected) {
            val (after, sent) = opened.on(key).press(Meaning.Activate)
            assertEquals(key, effect, sent)
            // The page stays: Android's picker or the emulator comes back to it.
            assertEquals(key, after.page().focus)
        }
        val cores = opened.settle { it.saves("retroarch.nes") { s -> s.copy(chosen = true) } }.on("retroarch.nes")
        assertEquals(Effect.ShowRetroArchCores, cores.press(Meaning.Activate).second)
    }

    @Test fun leftAndRightWalkTheStripAndStopAtItsEnds() {
        val opened = model.openSaveSetup(HostScreen.Top)
        val keys = opened.tiles().map { it.key }
        var at = opened
        for (key in keys.drop(1)) {
            at = at.press(Meaning.MoveRight).first
            assertEquals(key, at.page().focus)
        }
        assertEquals(keys.last(), at.press(Meaning.MoveRight).first.page().focus)
        // A held D-pad glides.
        assertEquals(keys[keys.size - 2], at.press(Meaning.MoveLeft, repeatCount = 3).first.page().focus)
        assertEquals(keys.first(), opened.press(Meaning.MoveLeft).first.page().focus)
        for (meaning in listOf(Meaning.MoveUp, Meaning.MoveDown, Meaning.LeftPanel, Meaning.RightPanel, Meaning.Menu, Meaning.Options)) {
            assertEquals(meaning.name, opened to null, opened.press(meaning))
        }
    }

    @Test fun skippingMarksItForThisRunAndGlidesToTheNextThatNeedsYou() {
        val opened = model.openSaveSetup(HostScreen.Top).on("melonds")
        val (skipped, effect) = opened.press(Meaning.ActivateBottom)
        assertNull(effect)
        assertEquals(setOf("melonds"), skipped.page().skipped)
        assertEquals("retroarch.nes", skipped.page().focus)
        assertEquals(SetupStatus.Skipped, setupStatus(skipped.tile("melonds"), skipped.page()))
        // It wraps round to the first that still needs you.
        assertEquals(FIND_SAVES_TILE, skipped.press(Meaning.Dismiss).first.page().focus)
        // A held X skips one.
        assertEquals(opened to null, opened.press(Meaning.ActivateBottom, repeatCount = 1))
        // Confirm on a skipped tile does its step after all.
        val (back, pick) = skipped.on("melonds").press(Meaning.Activate)
        assertEquals(Effect.ChoosePlayerSave("melonds"), pick)
        assertFalse("melonds" in back.page().skipped)
        // A fresh run asks again.
        assertTrue(skipped.press(Meaning.Back).first.openSaveSetup(HostScreen.Top).page().skipped.isEmpty())
    }

    @Test fun aSkippedFindSavesIsNotASkippedEmulator() {
        val one = PickerModel(count = 1, rowsPerPage = 1, playerSaves = listOf(melon))
        val opened = one.openSaveSetup(HostScreen.Top)
        assertEquals(FIND_SAVES_TILE, opened.page().focus)
        val passed = opened.press(Meaning.ActivateBottom).first
        assertEquals(0, saveSetupSkipped(passed.tiles(), passed.page()))
        assertEquals("1 to go", SaveSetupCopy.head(saveSetupLeft(passed.tiles(), passed.page()), 0))
        // melonDS's folder picked: everything that saves syncs, so All set, Find saves skipped or not.
        val picked = passed.settle { it.saves("melonds") { s -> s.copy(chosen = true) } }.on(null)
        assertTrue(saveSetupFinished(picked.tiles(), picked.page()))
        assertEquals("All set", SaveSetupCopy.endTitle(saveSetupSkipped(picked.tiles(), picked.page())))
    }

    @Test fun skippingNeverQuietsTheSettingsBadgeOrTheNotice() {
        val skipped = model.openSaveSetup(HostScreen.Top).on("melonds").press(Meaning.ActivateBottom).first
        assertEquals(TileStatus.Attention, settingsTileStatus(Row.SettingsTile(SettingsInner.Emulator("melonds")), skipped))
        assertEquals(model.playerSaves, skipped.playerSaves)
        assertEquals(model.notices, skipped.notices)
        assertTrue(saveSetupWanted(skipped))
    }

    @Test fun skippingTheLastOneEndsOnDoneForNowNotAllSetAndConfirmCloses() {
        val one = PickerModel(count = 1, rowsPerPage = 1, playerSaves = listOf(arms, duck.copy(hostedInUse = true)))
        val opened = one.openSaveSetup(HostScreen.Top)
        assertEquals("armsx2", opened.page().focus)
        assertEquals("1 to go", SaveSetupCopy.head(saveSetupLeft(opened.tiles(), opened.page()), saveSetupSkipped(opened.tiles(), opened.page())))
        val done = opened.press(Meaning.ActivateBottom).first
        assertNull(done.page().focus)
        assertTrue(saveSetupFinished(done.tiles(), done.page()))
        // A skipped emulator still does not sync, so the page never calls it All set.
        val skipped = saveSetupSkipped(done.tiles(), done.page())
        assertEquals(1, skipped)
        assertEquals("1 skipped", SaveSetupCopy.head(saveSetupLeft(done.tiles(), done.page()), skipped))
        assertEquals("Done for now", SaveSetupCopy.endTitle(skipped))
        assertEquals("1 still not syncing", SaveSetupCopy.endBody(skipped))
        assertEquals(listOf(HintKey(PromptKey.FaceA, SaveSetupCopy.done)), saveSetupActions(done, done.page()))
        assertNull(done.press(Meaning.Activate).first.saveSetup)
        // From All set, the D-pad still reaches the tiles.
        assertEquals("duckstation", done.press(Meaning.MoveLeft).first.page().focus)
        assertEquals("armsx2", done.press(Meaning.MoveRight).first.page().focus)
    }

    @Test fun nothingLeftOpensOnAllSetAndASyncingTileOpensItsSettingsPage() {
        val fine = PickerModel(count = 1, rowsPerPage = 1, playerSaves = listOf(melon.copy(chosen = true)), allFilesGranted = true)
        val opened = fine.openSaveSetup(HostScreen.Top)
        assertNull(opened.page().focus)
        assertEquals(0, saveSetupSkipped(opened.tiles(), opened.page()))
        assertEquals("All set", SaveSetupCopy.head(0, 0))
        assertEquals("All set", SaveSetupCopy.endTitle(0))
        assertNull(SaveSetupCopy.endBody(0))
        assertFalse(saveSetupWanted(fine))
        val (settings, effect) = opened.on("melonds").press(Meaning.Activate)
        assertNull(effect)
        assertNull(settings.saveSetup)
        assertEquals(SettingsInner.Emulator("melonds"), settings.settings!!.inner)
        assertTrue(settings.settings!!.onRows)
        assertEquals(SettingsCategory.Players, settingsCategories(settings)[settings.settings!!.category])
        assertEquals(SettingsCopy.title, SaveSetupCopy.action(opened.tile("melonds")))
    }

    @Test fun backClosesToTheGridOrToTheSettingsRowThatOpenedIt() {
        val closed = model.openSaveSetup(HostScreen.Top).press(Meaning.Back).first
        assertNull(closed.saveSetup)
        assertEquals(model.focus, closed.focus)

        var settings = model.openSettings(HostScreen.Top, SettingsCategory.Players)
        settings = settings.copy(settings = settings.settings!!.copy(onRows = true, row = settingsRows(SettingsCategory.Players, model).indexOf(Row.SaveSetup)))
        assertEquals(RowText(SaveSetupCopy.title), rowText(Row.SaveSetup, model))
        assertEquals(RowControl.Opens, rowControl(Row.SaveSetup, model))
        // The A held from the row before does not open it.
        assertEquals(settings.settings, settings.press(Meaning.Activate, repeatCount = 2).first.settings)
        val opened = settings.press(Meaning.Activate).first
        assertNull(opened.settings)
        assertEquals(settings.settings, opened.page().settings)
        val back = opened.press(Meaning.Back).first
        assertNull(back.saveSetup)
        assertEquals(settings.settings, back.settings)
    }

    @Test fun aHeldKeyFromBeforeActsOnceOnlyTheDPadRepeats() {
        val opened = model.openSaveSetup(HostScreen.Top)
        for (meaning in listOf(Meaning.Activate, Meaning.ActivateBottom, Meaning.Dismiss, Meaning.Back)) {
            assertEquals(meaning.name, opened to null, opened.press(meaning, repeatCount = 1))
        }
        // A dialog over the page, as a Try again, takes the keys first.
        val asked = opened.copy(dialog = edenWrongFolderDialog(HostScreen.Bottom))
        val (answered, choice) = asked.press(Meaning.Activate)
        assertEquals(Effect.DialogChoice(DialogButton.TryAgain, DialogKind.EdenWrongFolder), choice)
        assertEquals(opened.saveSetup, answered.saveSetup)
    }

    @Test fun theButtonsWearTheUsersOwnKeys() {
        val swapped = model.copy(faceMap = faceMapWithConfirm(FaceMap.standard(), PromptKey.FaceB)).openSaveSetup(HostScreen.Top).on("melonds")
        val actions = saveSetupActions(swapped, swapped.page())
        assertEquals(listOf(HintKey(PromptKey.FaceB, SaveSetupCopy.pick), HintKey(actionFaces(swapped.faceMap).bottomGlyph, SaveSetupCopy.skip)), actions)
        assertNotEquals(actions[0].key, actions[1].key)
        // Skip leaves once nothing is left to skip.
        val synced = swapped.settle { it.saves("melonds") { s -> s.copy(chosen = true) } }.on("melonds")
        assertEquals(listOf(HintKey(PromptKey.FaceB, SettingsCopy.title)), saveSetupActions(synced, synced.page()))
        // Calibrated so X confirms, skip moves off X with the bottom role.
        val xConfirms = model.copy(faceMap = faceMapWithConfirm(FaceMap.standard(), PromptKey.FaceX)).openSaveSetup(HostScreen.Top).on("melonds")
        val keys = saveSetupActions(xConfirms, xConfirms.page()).map { it.key }
        assertEquals(PromptKey.FaceX, keys[0])
        assertNotEquals(PromptKey.FaceX, keys[1])
        assertEquals(Meaning.ActivateBottom, meaningOf(keyOfPrompt(keys[1]), faces = xConfirms.faceMap))
    }

    @Test fun aStepDoneBehindAndroidsPickerChecksThenGlidesOnUnlessTheFocusMoved() {
        val opened = model.openSaveSetup(HostScreen.Top).on("melonds")
        val picked = opened.settle { it.saves("melonds") { s -> s.copy(chosen = true) } }
        assertEquals("melonds", picked.page().celebrating)
        assertEquals("melonds", picked.page().focus)
        val glided = picked.advanceSaveSetup("melonds")
        assertNull(glided.page().celebrating)
        assertEquals("retroarch.nes", glided.page().focus)
        // Moved away first: the focus stays where the user put it.
        val moved = picked.press(Meaning.MoveLeft).first
        assertNull(moved.page().celebrating)
        assertEquals(moved, moved.advanceSaveSetup("melonds"))
        // Confirm while the check lands goes on at once.
        assertEquals("retroarch.nes", picked.press(Meaning.Activate).first.page().focus)
        // A tile with a second step shows it, without a celebration.
        val folder = model.copy(playerSaves = listOf(arms.copy(chosen = false))).openSaveSetup(HostScreen.Top).on("armsx2")
        val next = folder.settle { it.saves("armsx2") { s -> s.copy(chosen = true) } }
        assertNull(next.page().celebrating)
        assertEquals(InAppStep.MemoryCard, (next.tile("armsx2").step as SetupStep.InApp).kind)
    }

    @Test fun theFocusWaitsWhileTheNewFolderIsStillBeingRead() {
        val opened = model.openSaveSetup(HostScreen.Top).on("melonds")
        val picked = opened.settle { it.saves("melonds") { s -> s.copy(chosen = true) }.copy(checkingSaves = true) }
        assertEquals("melonds", picked.page().celebrating)
        assertEquals(picked, picked.advanceSaveSetup("melonds"))
        assertEquals("melonds", picked.press(Meaning.Activate).first.page().focus)
        val read = picked.copy(checkingSaves = false)
        assertEquals("retroarch.nes", read.advanceSaveSetup("melonds").page().focus)
    }

    @Test fun aStepThatShowsUpOnlyAfterTheFolderCallsTheCelebrationOff() {
        val folder = model.copy(playerSaves = listOf(aps3e)).openSaveSetup(HostScreen.Top).on("aps3e")
        // The folder lands first; aPS3e's own setup is read a moment later, from that folder.
        val picked = folder.settle { it.saves("aps3e") { s -> s.copy(chosen = true) } }
        assertEquals("aps3e", picked.page().celebrating)
        val read = picked.settle { it.saves("aps3e") { s -> s.copy(setupSteps = "Install firmware in aPS3e.") } }
        assertNull(read.page().celebrating)
        assertEquals("aps3e", read.page().focus)
        // Read after the pause began: the focus still stays on it.
        val late = picked.copy(playerSaves = read.playerSaves).advanceSaveSetup("aps3e")
        assertEquals("aps3e", late.page().focus)
        assertNull(late.page().celebrating)
    }

    @Test fun anInAppStepDoneInTheEmulatorChecksOnReturn() {
        val opened = model.openSaveSetup(HostScreen.Top).on("duckstation")
        assertEquals(Effect.OpenPlayer("duckstation"), opened.press(Meaning.Activate).second)
        val back = opened.settle { it.saves("duckstation") { s -> s.copy(hostedInUse = true) } }
        assertTrue(back.tile("duckstation").done)
        assertEquals("duckstation", back.page().celebrating)
        // Not done there: the same step waits.
        val notYet = opened.settle { it.copy(shelfEpoch = it.shelfEpoch + 1) }
        assertEquals(opened.page(), notYet.page())
    }

    @Test fun allSetNeverStandsWhileSomethingNeedsYouAgain() {
        val fine = PickerModel(count = 1, rowsPerPage = 1, playerSaves = listOf(melon.copy(chosen = true)), allFilesGranted = true)
        val opened = fine.openSaveSetup(HostScreen.Top)
        assertNull(opened.page().focus)
        val lost = opened.settle { it.saves("melonds") { s -> s.copy(chosen = false) } }
        assertEquals("melonds", lost.page().focus)
    }

    @Test fun aTileThatLeavesHandsTheFocusOn() {
        val opened = model.openSaveSetup(HostScreen.Top).on("duckstation")
        val gone = opened.settle { it.copy(playerSaves = it.playerSaves.filter { s -> s.playerId != "duckstation" }) }
        assertEquals(FIND_SAVES_TILE, gone.page().focus)
        // While nothing has loaded yet, a page brought back keeps its tile.
        val restored = PickerModel(count = 0, rowsPerPage = 1).openSaveSetup(HostScreen.Top, target = "melonds")
        assertEquals("melonds", restored.page().focus)
        assertEquals("melonds", restored.settle { it.copy(shelfEpoch = 1) }.page().focus)
        assertEquals("melonds", restored.settle { it.copy(playerSaves = listOf(melon)) }.page().focus)
    }

    @Test fun aPickThatWasNotItsFolderSaysWhyUnderTheStepUntilTheStepChanges() {
        val opened = model.openSaveSetup(HostScreen.Top).on("melonds")
        val text = SaveSetupCopy.wrongFolder("Azahar", listOf("sdmc", "config"))
        assertEquals("Not Azahar’s folder. Look for one with config or sdmc.", text)
        assertEquals("Not Azahar’s folder. Look for one with config, nand or sdmc.", SaveSetupCopy.wrongFolder("Azahar", listOf("sdmc", "config", "nand")))
        assertEquals("That isn’t melonDS’s folder. Try another.", SaveSetupCopy.wrongFolder("melonDS", emptyList()))
        assertNull(opened.noteSaveSetup("azahar", text))
        val noted = opened.noteSaveSetup("melonds", text)!!
        assertEquals(SetupNote("melonds", text), noted.page().note)
        // Unrelated changes keep it; moving or the step finishing drops it.
        assertEquals(noted.page().note, noted.settle { it.copy(shelfEpoch = 1) }.page().note)
        assertNull(noted.press(Meaning.MoveRight).first.page().note)
        assertNull(noted.settle { it.saves("melonds") { s -> s.copy(chosen = true) } }.page().note)
        assertNull(noted.press(Meaning.Activate).first.page().note)
    }

    @Test fun aRommSignInWantsThePageOnlyWhileAnEmulatorNeedsAStep() {
        assertTrue(saveSetupWanted(model))
        val ready = model.copy(
            playerSaves = listOf(melon.copy(chosen = true)),
            retroArch = null,
            eden = null,
        )
        assertFalse(saveSetupWanted(ready))
        // Find saves alone is optional, never a reason to open the page.
        assertFalse(saveSetupWanted(ready.copy(playerSaves = listOf(melon.copy(chosen = true)))))
    }

    @Test fun keysThatOpenThePageStayKeys() {
        // The bottom button reaches the page as ActivateBottom, as on a grid.
        assertEquals(Meaning.ActivateBottom, meaningOf(KeyEvent.KEYCODE_BUTTON_X))
        assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_B, repeatCount = 1))
    }
}
