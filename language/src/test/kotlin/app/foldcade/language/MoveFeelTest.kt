package app.foldcade.language

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Carrying a tile: hovering a group, putting it in, taking it out of one, and Tidy. */
class MoveFeelTest {
    private val platforms = listOf(
        HomePlatform("nintendo-3ds", "Nintendo 3DS", setOf("3ds")),
        HomePlatform("nintendo-ds", "Nintendo DS", setOf("nds")),
    )
    private val threeDs = platformFolderId("nintendo-3ds")
    private val ds = platformFolderId("nintendo-ds")
    private val quests = "home.user.1"
    private val zelda = rom("lib:local:zelda", "3ds")
    private val mario = rom("lib:local:mario", "3ds")
    private val kirby = rom("lib:local:kirby", "nds")
    private val catalog = listOf(zelda, mario, kirby)

    /** All, a Zelda pin, Quests (Kirby), the 3DS folder, a Kirby pin, a gap, a Mario pin. */
    private val board = HomeBoard(
        slots = listOf(HOME_ALL, zelda.id, quests, threeDs, kirby.id, null, mario.id),
        folders = mapOf(
            quests to HomeFolder(quests, "Quests", userMade = true, slots = listOf(kirby.id)),
            threeDs to HomeFolder(threeDs, "Nintendo 3DS", platformId = "nintendo-3ds", slots = listOf(zelda.id, mario.id)),
            ds to HomeFolder(ds, "Nintendo DS", platformId = "nintendo-ds", slots = listOf(kirby.id)),
        ),
    )

    @Test
    fun aGameWalkedOntoACollectionHoversAndWalksOnPastIt() {
        val over = moveHeld(pickUp(board, 1), 2, catalog, platforms)
        // Nothing moves: the collection stays put, and the pin's slot waits for it.
        assertEquals(board.slots, over.slots)
        assertEquals(2, over.hold?.index)
        assertEquals(quests, heldOver(over))
        assertEquals(zelda.id, heldId(over))
        assertEquals(HoldDrop.Add, holdDrop(over))
        // On past it, the next tile swaps as usual and the collection is where it was.
        val past = moveHeld(over, 4, catalog, platforms)
        assertEquals(listOf(HOME_ALL, kirby.id, quests, threeDs, zelda.id, null, mario.id), past.slots)
        assertNull(heldOver(past))
        assertEquals(HoldDrop.Place, holdDrop(past))
        assertEquals(listOf(kirby.id), past.folders.getValue(quests).slots)
    }

    @Test
    fun droppingOnACollectionFilesThePinAndLeavesItsFolder() {
        val dropped = drop(moveHeld(pickUp(board, 1), 2, catalog, platforms), catalog, platforms)
        assertNull(dropped.hold)
        assertEquals(listOf(HOME_ALL, null, quests, threeDs, kirby.id, null, mario.id), dropped.slots)
        assertEquals(listOf(kirby.id, zelda.id), dropped.folders.getValue(quests).slots)
        assertEquals(listOf(zelda.id, mario.id), dropped.folders.getValue(threeDs).slots)
    }

    @Test
    fun aPinDroppedOnItsOwnFolderIsPutBack() {
        val over = moveHeld(pickUp(board, 1), 3, catalog, platforms)
        assertEquals(threeDs, heldOver(over))
        assertEquals(HoldDrop.PutBack, holdDrop(over))
        val dropped = drop(over, catalog, platforms)
        assertFalse(pinned(dropped, zelda.id))
        assertTrue(onHome(dropped, zelda.id))
        assertEquals(listOf(zelda.id, mario.id), dropped.folders.getValue(threeDs).slots)
    }

    @Test
    fun whatAGroupCannotTakeWalksPastByTradingPlaces() {
        // A DS game is not the 3DS folder's.
        val kirbyOnThreeDs = moveHeld(pickUp(board, 4), 3, catalog, platforms)
        assertEquals(listOf(HOME_ALL, zelda.id, quests, kirby.id, threeDs, null, mario.id), kirbyOnThreeDs.slots)
        assertNull(heldOver(kirbyOnThreeDs))
        // A gap, and a group, never hover.
        val gap = moveHeld(pickUp(board, 5), 2, catalog, platforms)
        assertEquals(listOf(HOME_ALL, zelda.id, null, threeDs, kirby.id, quests, mario.id), gap.slots)
        val group = moveHeld(pickUp(board, 2), 3, catalog, platforms)
        assertEquals(listOf(HOME_ALL, zelda.id, threeDs, quests, kirby.id, null, mario.id), group.slots)
        // Recently played takes nothing.
        val recent = board.copy(
            slots = board.slots + HOME_RECENT,
            folders = board.folders + (HOME_RECENT to HomeFolder(HOME_RECENT, Copy.recentlyPlayed, slots = listOf(mario.id))),
        )
        val onRecent = moveHeld(pickUp(recent, 6), 7, catalog, platforms)
        assertEquals(HOME_RECENT, onRecent.slots[6])
        assertEquals(mario.id, onRecent.slots[7])
    }

    @Test
    fun aGameCarriedOutOfItsFolderIsAPinAtTheFolderAndStaysInIt() {
        val lifted = moveHeld(pickUp(openFolder(board, threeDs), 1), 0, catalog, platforms)
        assertTrue(canTakeOut(lifted))
        val raised = raiseToHome(lifted)
        assertEquals(HoldDrop.TakeOut, holdDrop(raised))
        // Over the target, the folder is as it was at pickup.
        assertEquals(listOf(zelda.id, mario.id), raised.folders.getValue(threeDs).slots)
        val carried = drop(raised, catalog, platforms)
        val hold = carried.hold!!
        assertNull(carried.openFolderId)
        assertEquals(mario.id, heldId(carried))
        // Over the folder's slot, the folder aside in the nearest free one. The old Mario pin gave way.
        assertEquals(listOf(HOME_ALL, zelda.id, quests, mario.id, kirby.id, threeDs, null), carried.slots)
        assertEquals(3, hold.index)
        assertEquals(listOf(zelda.id, mario.id), carried.folders.getValue(threeDs).slots)
        // A step on brings the folder back to its slot, and the pin goes down where it is put.
        val walked = moveHeld(carried, 4, catalog, platforms)
        assertEquals(listOf(HOME_ALL, zelda.id, quests, threeDs, mario.id, kirby.id, null), walked.slots)
        val placed = drop(walked, catalog, platforms)
        assertNull(placed.hold)
        assertTrue(pinned(placed, mario.id))
        assertEquals(listOf(zelda.id, mario.id), placed.folders.getValue(threeDs).slots)
    }

    @Test
    fun aGameCarriedOutOfACollectionLeavesItAndCancelPutsEverythingBack() {
        val inQuests = openFolder(board, quests)
        val carried = drop(raiseToHome(pickUp(inQuests, 0)), catalog, platforms)
        assertNull(carried.openFolderId)
        assertEquals(listOf(null), carried.folders.getValue(quests).slots)
        assertEquals(listOf(HOME_ALL, zelda.id, kirby.id, threeDs, quests, null, mario.id), carried.slots)
        assertEquals(listOf(kirby.id), carried.folders.getValue(ds).slots)
        // Saved mid-carry, the board is the one before the tile was lifted.
        assertEquals(listOf(kirby.id), decodeHome(encodeHome(carried)).folders.getValue(quests).slots)
        val cancelled = cancelHold(moveHeld(carried, 5, catalog, platforms))
        assertEquals(quests, cancelled.openFolderId)
        assertEquals(board.slots, cancelled.slots)
        assertEquals(listOf(kirby.id), cancelled.folders.getValue(quests).slots)
        assertNull(cancelled.hold)
    }

    @Test
    fun onlyAGameInAGroupRisesToTheHomeTarget() {
        assertFalse(canTakeOut(pickUp(board, 1)))
        assertEquals(pickUp(board, 1), raiseToHome(pickUp(board, 1)))
        val gapInFolder = pickUp(openFolder(board.copy(folders = board.folders + (threeDs to board.folders.getValue(threeDs).copy(slots = listOf(null, zelda.id)))), threeDs), 0)
        assertFalse(canTakeOut(gapInFolder))
        // Down from the target comes back to the slot it rose from.
        val raised = raiseToHome(moveHeld(pickUp(openFolder(board, threeDs), 1), 0, catalog, platforms))
        val back = moveHeld(raised, raised.hold!!.index, catalog, platforms)
        assertFalse(back.hold!!.overHome)
        assertEquals(listOf(mario.id, zelda.id), back.folders.getValue(threeDs).slots)
    }

    @Test
    fun tidyClosesGapsInReadingOrder() {
        val gappy = board.copy(slots = listOf(HOME_ALL, null, zelda.id, null, null, quests, null, mario.id, null))
        assertTrue(canTidy(gappy))
        val tidied = tidy(gappy)
        assertEquals(listOf(HOME_ALL, zelda.id, quests, mario.id), tidied.slots)
        assertFalse(canTidy(tidied))
        assertEquals(tidied, tidy(tidied))
        // In an open folder only its own slots close up.
        val folder = openFolder(board.copy(folders = board.folders + (threeDs to board.folders.getValue(threeDs).copy(slots = listOf(null, zelda.id, null, mario.id)))), threeDs)
        val inFolder = tidy(folder)
        assertEquals(listOf(zelda.id, mario.id), inFolder.folders.getValue(threeDs).slots)
        assertEquals(board.slots, inFolder.slots)
        // Gaps after the last tile are not gaps to close; nor is anything while a tile is held, in All, or in Recently played.
        assertFalse(canTidy(board.copy(slots = listOf(HOME_ALL, zelda.id, null, null))))
        assertFalse(canTidy(pickUp(gappy, 2)))
        assertFalse(canTidy(openAll(gappy)))
        val recent = gappy.copy(folders = gappy.folders + (HOME_RECENT to HomeFolder(HOME_RECENT, Copy.recentlyPlayed, slots = listOf(null, mario.id))))
        assertFalse(canTidy(openFolder(recent, HOME_RECENT)))
    }

    @Test
    fun yDropsAHeldTileOncePerPress() {
        assertEquals(Meaning.Activate, meaningOf(KeyEvent.KEYCODE_BUTTON_Y, homeKeys = HomeKeys.Moving))
        assertEquals(Meaning.Activate, meaningOf(KeyEvent.KEYCODE_BUTTON_A, homeKeys = HomeKeys.Moving))
        assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_Y, repeatCount = 1, homeKeys = HomeKeys.Moving))
        assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_A, repeatCount = 1, homeKeys = HomeKeys.Moving))
        assertEquals(Meaning.CancelHold, meaningOf(KeyEvent.KEYCODE_BUTTON_B, homeKeys = HomeKeys.Moving))
        // Held, Y opens its menu once and no more.
        assertEquals(Meaning.Options, meaningOf(KeyEvent.KEYCODE_BUTTON_Y))
        assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_Y, repeatCount = 3))
    }

    @Test
    fun yDropsOnlyWhenItIsNotTheCalibratedBack() {
        // Confirm calibrated to X makes Y the back key: Y then cancels, as the hint says.
        val xConfirms = FaceMap.standard().copy(confirmKey = KeyEvent.KEYCODE_BUTTON_X, backKey = KeyEvent.KEYCODE_BUTTON_Y)
        assertEquals(Meaning.CancelHold, meaningOf(KeyEvent.KEYCODE_BUTTON_Y, faces = xConfirms, homeKeys = HomeKeys.Moving))
        assertEquals(Meaning.Activate, meaningOf(KeyEvent.KEYCODE_BUTTON_X, faces = xConfirms, homeKeys = HomeKeys.Moving))
        assertEquals(Copy.hintCancel, gridHints(null, HomeKeys.Moving, xConfirms).first { it.key == PromptKey.FaceY }.label)
        // Confirm on Y: Y drops, as confirm.
        val yConfirms = FaceMap.standard().copy(confirmKey = KeyEvent.KEYCODE_BUTTON_Y, backKey = KeyEvent.KEYCODE_BUTTON_B)
        assertEquals(Meaning.Activate, meaningOf(KeyEvent.KEYCODE_BUTTON_Y, faces = yConfirms, homeKeys = HomeKeys.Moving))
        // The standard map: Y drops silently beside A.
        assertEquals(Meaning.Activate, meaningOf(KeyEvent.KEYCODE_BUTTON_Y, homeKeys = HomeKeys.Moving))
        assertEquals(Meaning.CancelHold, meaningOf(KeyEvent.KEYCODE_BUTTON_B, homeKeys = HomeKeys.Moving))
    }

    @Test
    fun theHintNamesWhatTheDropDoes() {
        fun drop(kind: HoldDrop) = gridHints(null, HomeKeys.Moving, FaceMap.standard(), drop = kind).map { it.label }
        assertEquals(listOf(Copy.hintDrop, Copy.hintCancel), drop(HoldDrop.Place))
        assertEquals(listOf(Copy.hintAdd, Copy.hintCancel), drop(HoldDrop.Add))
        assertEquals(listOf(Copy.hintPutBack, Copy.hintCancel), drop(HoldDrop.PutBack))
        assertEquals(listOf(Copy.hintTakeOut, Copy.hintCancel), drop(HoldDrop.TakeOut))
    }

    @Test
    fun tidyIsOfferedOnAnEmptySlotAndInEditHome() {
        val slot = TileActions(move = true, newCollection = true, tidy = true, tidyBoard = true)
        assertEquals(listOf(Row.MoveTile, Row.NewCollection, Row.Tidy), actionRows(slot, null))
        val game = TileActions(move = true, collections = emptyList(), favorite = false, tidyBoard = true)
        assertFalse(Row.Tidy in actionRows(game, null))
        val model = PickerModel(count = 4, rowsPerPage = 3, tileActions = game)
        assertEquals(listOf(Row.MoveTile, Row.AddToCollection, Row.Favorite, Row.Tidy, Row.AddNewGames, Row.HiddenApps), editHomeRows(model))
        assertEquals(1, editHomeRows(model.copy(tileActions = slot)).count { it == Row.Tidy })
        assertFalse(Row.Tidy in editHomeRows(model.copy(tileActions = game.copy(tidyBoard = false))))
        // A runs it, and it has no chord: Y stays Move.
        val menu = reduce(model.copy(tileActions = slot), Meaning.Options).first
        val onTidy = menu.copy(panel = menu.panel!!.copy(index = 2))
        assertEquals(Effect.Tidy, reduce(onTidy, Meaning.Activate).second)
        assertEquals(Effect.MoveTile, reduce(onTidy, Meaning.Options).second)
    }

    @Test
    fun motionOffHoldsAHeldTileStill() {
        assertNull(Motion.bob(MotionSpeed.Off, 1f))
        assertNull(Motion.squashBack(MotionSpeed.Off, 1f))
        assertNull(Motion.bob(MotionSpeed.Slow, 0f))
        assertNotNull(Motion.bob(MotionSpeed.Slow, 1f))
        assertNotNull(Motion.squashBack(MotionSpeed.Slower, 1f))
    }

    private fun rom(id: String, platform: String): HomeItem =
        HomeItem(id = id, title = id.substringAfterLast(':'), kind = HomeKind.Rom, platformId = platform)
}
