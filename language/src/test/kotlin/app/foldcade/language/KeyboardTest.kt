package app.foldcade.language

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardTest {
    private fun at(char: Char): KeyFocus = KeyboardLayout.find(KeyCap.Glyph(char))

    private fun capAt(focus: KeyFocus): KeyCap? = KeyboardLayout.key(focus)?.cap

    private fun Typing.run(vararg meanings: Meaning): Typing = meanings.fold(this) { typing, meaning -> typing.on(meaning).typing }

    /** Walks the cursor to [char] by tapping it, then presses it, as A on that key would. */
    private fun Typing.typeKeys(text: String): Typing = text.fold(this) { typing, char ->
        val key = if (char == ' ') KeyboardLayout.find(KeyCap.Space) else at(char.lowercaseChar())
        typing.press(key).typing
    }

    @Test
    fun everyRowFitsTheBoardAndNoKeysOverlap() {
        KeyboardLayout.rows.forEach { row ->
            assertTrue(row.first().x >= 0f)
            assertTrue(row.last().end <= KeyboardLayout.WIDTH)
            row.zipWithNext().forEach { (left, right) -> assertTrue(left.end <= right.x) }
        }
        // Number row, three letter rows, then Space and Done.
        assertEquals("1234567890-&", KeyboardLayout.rows[0].map { (it.cap as KeyCap.Glyph).lower }.joinToString(""))
        assertEquals(listOf(KeyCap.Space, KeyCap.Done), KeyboardLayout.rows.last().map { it.cap })
        assertEquals(KeyCap.Shift, KeyboardLayout.rows[3].first().cap)
        assertEquals(KeyCap.Erase, KeyboardLayout.rows[3].last().cap)
    }

    @Test
    fun leftAndRightStopAtTheEndsOfARow() {
        val q = at('q')
        assertNull(moveKey(q, Meaning.MoveLeft, chips = 0))
        assertEquals(at('w'), moveKey(q, Meaning.MoveRight, chips = 0))
        val last = KeyFocus(1, KeyboardLayout.rows[1].lastIndex, KeyboardLayout.rows[1].last().center)
        assertNull(moveKey(last, Meaning.MoveRight, chips = 0))
        assertNull(moveKey(KeyboardLayout.find(KeyCap.Done), Meaning.MoveDown, chips = 0))
    }

    @Test
    fun upAndDownKeepTheColumnAcrossShorterRows() {
        // P sits over the colon, the full stop, then Done; coming back up lands on P again.
        var focus = at('p')
        val down = List(3) { focus = moveKey(focus, Meaning.MoveDown, chips = 0)!!; capAt(focus) }
        assertEquals(listOf(KeyCap.Glyph(':'), KeyCap.Glyph('.'), KeyCap.Done), down)
        repeat(3) { focus = moveKey(focus, Meaning.MoveUp, chips = 0)!! }
        assertEquals(KeyCap.Glyph('p'), capAt(focus))
        // B drops onto the wide Space bar and goes back up to B, not to Space's middle.
        val space = moveKey(at('b'), Meaning.MoveDown, chips = 0)!!
        assertEquals(KeyCap.Space, capAt(space))
        assertEquals(KeyCap.Glyph('b'), capAt(moveKey(space, Meaning.MoveUp, chips = 0)!!))
        // A row that starts half a key in still has a key under its left edge.
        assertEquals(KeyCap.Glyph('a'), capAt(moveKey(at('q'), Meaning.MoveDown, chips = 0)!!))
    }

    @Test
    fun movingAlongARowTakesThatKeysColumn() {
        // From Space, right to Done and up: the column is Done's now, under Erase.
        val done = moveKey(KeyboardLayout.find(KeyCap.Space), Meaning.MoveRight, chips = 0)!!
        assertEquals(KeyCap.Done, capAt(done))
        assertEquals(KeyCap.Erase, capAt(moveKey(done, Meaning.MoveUp, chips = 0)!!))
    }

    @Test
    fun upFromTheNumbersReachesTheChipsAndDownComesBack() {
        val five = at('5')
        assertNull(moveKey(five, Meaning.MoveUp, chips = 0))
        val chip = moveKey(five, Meaning.MoveUp, chips = 4, lastChip = 2)!!
        assertEquals(KeyFocus(CHIP_ROW, 2, five.x), chip)
        assertEquals(KeyFocus(CHIP_ROW, 3, five.x), moveKey(chip, Meaning.MoveRight, chips = 4))
        assertNull(moveKey(chip.copy(index = 3), Meaning.MoveRight, chips = 4))
        assertNull(moveKey(chip, Meaning.MoveUp, chips = 4))
        assertEquals(five, moveKey(chip, Meaning.MoveDown, chips = 4))
    }

    @Test
    fun theOldNameStartsSelectedSoTheFirstKeyReplacesIt() {
        val typing = openTyping("Folder 1")
        assertEquals(TextDraft("Folder 1", caret = 8, anchor = 0), typing.draft)
        // The selection starts at a word, so the first letter is a capital.
        assertEquals(ShiftState.Word, typing.shift)
        assertEquals("R", typing.typeKeys("r").draft.text)
        assertEquals("", typing.run(Meaning.Erase).draft.text)
    }

    @Test
    fun lOneAndROneCollapseTheSelectionToThatEnd() {
        val typing = openTyping("Folder 1")
        assertEquals(TextDraft("Folder 1", 0), typing.run(Meaning.CaretBack).draft)
        assertEquals(TextDraft("Folder 1", 8), typing.run(Meaning.CaretForward).draft)
        val moved = typing.run(Meaning.CaretForward, Meaning.CaretBack, Meaning.CaretBack)
        assertEquals(6, moved.draft.caret)
        assertEquals(TextDraft("Folde 1", 5), moved.run(Meaning.Erase).draft)
        assertEquals(TypingCue.None, typing.run(Meaning.CaretBack).on(Meaning.CaretBack).cue)
    }

    @Test
    fun typingCapitalisesEachWordUnlessShiftSaysOtherwise() {
        val typed = openTyping("").typeKeys("must play")
        assertEquals("Must Play", typed.draft.text)
        // L2 at a word's start makes caps lock; again turns Shift off.
        val locked = openTyping("").run(Meaning.Shift)
        assertEquals(ShiftState.Locked, locked.shift)
        assertEquals("RPG", locked.typeKeys("rpg").draft.text)
        val lower = locked.run(Meaning.Shift)
        assertEquals(ShiftState.Off, lower.shift)
        assertEquals("ios", lower.typeKeys("ios").draft.text)
        // Mid-word, L2 is one capital and then it lets go.
        val mid = openTyping("").typeKeys("mc").run(Meaning.Shift)
        assertEquals(ShiftState.Once, mid.shift)
        assertEquals("McDo", mid.typeKeys("do").draft.text)
    }

    @Test
    fun shiftStepsThroughOnceCapsAndOff() {
        assertEquals(ShiftState.Once, ShiftState.Off.next())
        assertEquals(ShiftState.Locked, ShiftState.Once.next())
        assertEquals(ShiftState.Locked, ShiftState.Word.next())
        assertEquals(ShiftState.Off, ShiftState.Locked.next())
        val draft = TextDraft("ab", 2)
        assertEquals(ShiftState.Off, settleShift(ShiftState.Once, draft, spent = true))
        assertEquals(ShiftState.Once, settleShift(ShiftState.Once, draft))
        assertEquals(ShiftState.Off, settleShift(ShiftState.Word, draft))
        assertEquals(ShiftState.Locked, settleShift(ShiftState.Locked, draft, spent = true))
        assertEquals(ShiftState.Word, settleShift(ShiftState.Off, TextDraft("ab ", 3)))
    }

    @Test
    fun yTypesASpaceAndXErasesOneCharacter() {
        val typing = openTyping("Co").run(Meaning.CaretForward, Meaning.Space)
        assertEquals("Co ", typing.draft.text)
        assertEquals("Co", typing.run(Meaning.Erase).draft.text)
        val empty = openTyping("").on(Meaning.Erase)
        assertEquals(TypingCue.None, empty.cue)
    }

    @Test
    fun theFieldStopsAtItsLimitAndShakes() {
        val full = openTyping("x".repeat(NAME_LIMIT)).run(Meaning.CaretForward)
        val refused = full.on(Meaning.Space)
        assertEquals(NAME_LIMIT, refused.typing.draft.text.length)
        assertEquals(1, refused.typing.refusals)
        assertEquals(TypingCue.None, refused.cue)
        // A long old name is cut to the limit, and a selection makes room.
        assertEquals(NAME_LIMIT, openTyping("y".repeat(40)).draft.text.length)
        assertEquals(TextDraft("abz", 3), TextDraft("abc", 3, 2).insert("zz", limit = 3))
    }

    @Test
    fun doneSavesTrimmedTextAndAnEmptyFieldPutsTheOldNameBack() {
        val saved = openTyping("Folder 1").typeKeys("  rpg ").on(Meaning.Done)
        assertEquals("Rpg", saved.saved)
        assertEquals(TypingCue.Done, saved.cue)
        val blank = openTyping("Folder 1").run(Meaning.Erase).on(Meaning.Done)
        assertNull(blank.saved)
        assertEquals(TextDraft.selectAll("Folder 1"), blank.typing.draft)
        assertEquals(1, blank.typing.refusals)
        val spaces = openTyping("Folder 1").typeKeys("   ").on(Meaning.Done)
        assertNull(spaces.saved)
    }

    @Test
    fun bCancelsAndEdgesAreReported() {
        val typing = openTyping("Folder 1")
        assertTrue(typing.on(Meaning.Back).cancelled)
        assertEquals(TypingCue.Cancel, typing.on(Meaning.Back).cue)
        val edge = openTyping("", chips = emptyList()).copy(focus = at('5')).on(Meaning.MoveUp)
        assertEquals(Meaning.MoveUp, edge.edge)
        assertEquals(TypingCue.None, edge.cue)
    }

    @Test
    fun aOnAChipReplacesTheTextAndKeepsTheKeyboardOpen() {
        val typing = openTyping("Folder 1", chips = listOf("Playing", "Backlog"), onChips = true)
        assertTrue(typing.focus.onChips)
        val picked = typing.run(Meaning.MoveRight).on(Meaning.Activate)
        assertEquals("Backlog", picked.typing.draft.text)
        assertFalse(picked.typing.draft.selecting)
        assertNull(picked.saved)
        assertEquals("Backlog", picked.typing.on(Meaning.Done).saved)
        // Down and back up returns to the chip last chosen.
        val back = picked.typing.run(Meaning.MoveDown, Meaning.MoveUp)
        assertEquals(KeyFocus(CHIP_ROW, 1, picked.typing.focus.x), back.focus)
    }

    @Test
    fun buttonsAndTapsFlashTheKeyThatActed() {
        val typing = openTyping("Folder 1")
        val erased = typing.on(Meaning.Erase).typing
        assertEquals(1, erased.pressed)
        assertEquals(KeyboardLayout.find(KeyCap.Erase), erased.pressedKey)
        assertEquals(KeyboardLayout.find(KeyCap.Done), erased.on(Meaning.Done).typing.pressedKey)
        val tapped = typing.press(at('k'))
        assertEquals(at('k'), tapped.typing.focus)
        assertEquals(at('k'), tapped.typing.pressedKey)
        assertEquals(TypingCue.None, typing.press(KeyFocus(9, 0, 0f)).cue)
    }

    @Test
    fun aDoesNotStepInsideASurrogatePair() {
        val star = "A🌟B"
        val draft = TextDraft(star, star.length)
        assertEquals(3, draft.back().caret)
        assertEquals(1, draft.back().back().caret)
        assertEquals("AB", TextDraft(star, 3).erase().text)
        assertEquals(3, TextDraft(star, 1).forward().caret)
    }

    @Test
    fun quickNamesPutContextFirstAndSkipNamesInUse() {
        val names = quickNames(listOf("Nintendo DS", "playing"), taken = listOf("Backlog"))
        assertEquals("Nintendo DS", names[0])
        assertEquals("playing", names[1])
        assertFalse(names.any { it.equals("Backlog", ignoreCase = true) })
        assertEquals(1, names.count { it.equals("Playing", ignoreCase = true) })
        assertTrue(names.all { it.split(' ').size <= 2 })
        // Favorites is not built in, so it is the first idea for a collection, until one has the name.
        assertEquals("Favorites", quickNames(emptyList()).first())
        assertFalse("Favorites" in quickNames(emptyList(), taken = listOf("favorites")))
        assertFalse(quickNames(listOf("z".repeat(NAME_LIMIT + 1))).any { it.length > NAME_LIMIT })
    }

    @Test
    fun theKeyboardTakesEveryKeyWhileItIsOpen() {
        val typing = HomeKeys.Typing
        assertEquals(Meaning.Erase, meaningOf(KeyEvent.KEYCODE_BUTTON_X, homeKeys = typing))
        assertEquals(Meaning.Space, meaningOf(KeyEvent.KEYCODE_BUTTON_Y, homeKeys = typing))
        assertEquals(Meaning.Done, meaningOf(KeyEvent.KEYCODE_BUTTON_START, homeKeys = typing))
        assertEquals(Meaning.CaretBack, meaningOf(KeyEvent.KEYCODE_BUTTON_L1, homeKeys = typing))
        assertEquals(Meaning.CaretForward, meaningOf(KeyEvent.KEYCODE_BUTTON_R1, homeKeys = typing))
        assertEquals(Meaning.Shift, meaningOf(KeyEvent.KEYCODE_BUTTON_L2, homeKeys = typing))
        assertEquals(Meaning.Activate, meaningOf(KeyEvent.KEYCODE_BUTTON_A, homeKeys = typing))
        assertEquals(Meaning.Back, meaningOf(KeyEvent.KEYCODE_BUTTON_B, homeKeys = typing))
        assertEquals(Meaning.Done, meaningOf(KeyEvent.KEYCODE_ENTER, homeKeys = typing))
        // Nothing opens a menu or a view.
        assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_SELECT, homeKeys = typing))
        assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_R2, homeKeys = typing))
        // Held: the D-pad, X and the shoulders repeat; A, Start and L2 act once.
        assertEquals(Meaning.MoveRight, meaningOf(KeyEvent.KEYCODE_DPAD_RIGHT, repeatCount = 3, homeKeys = typing))
        assertEquals(Meaning.Erase, meaningOf(KeyEvent.KEYCODE_BUTTON_X, repeatCount = 3, homeKeys = typing))
        assertEquals(Meaning.CaretBack, meaningOf(KeyEvent.KEYCODE_BUTTON_L1, repeatCount = 3, homeKeys = typing))
        assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_A, repeatCount = 1, homeKeys = typing))
        assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_START, repeatCount = 1, homeKeys = typing))
        assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_L2, repeatCount = 1, homeKeys = typing))
    }

    @Test
    fun hostsCanSetTheTextOutright() {
        val typing = openTyping("Folder 1").withText("Zelda")
        assertEquals(TextDraft("Zelda", 5), typing.draft)
        assertEquals("Folder 1", typing.original)
        assertEquals(ShiftState.Off, typing.shift)
    }

    @Test
    fun aLongOldNameIsCutWholeAndNeverLeavesHalfAnEmoji() {
        // 31 letters and a star are 32 characters, though the star takes two UTF-16 units.
        val star = "x".repeat(31) + "🌟"
        assertEquals(star, openTyping(star).draft.text)
        val over = openTyping("x".repeat(32) + "🌟")
        assertEquals("x".repeat(32), over.draft.text)
        val saved = openTyping("x".repeat(31) + "🌟🌟").on(Meaning.Done).saved!!
        assertEquals("x".repeat(31) + "🌟", saved)
        // An unpaired half of a surrogate pair reads as its own code point in that range.
        assertTrue(saved.codePoints().noneMatch { it in 0xD800..0xDFFF })
        // The limit counts characters, so a full field with an emoji in it still refuses one more.
        assertEquals(1, openTyping(star).run(Meaning.CaretForward).on(Meaning.Space).typing.refusals)
    }

    @Test
    fun eraseAndTheCaretStepOverAWholeEmoji() {
        val thumb = "A👍🏽B"
        val draft = TextDraft(thumb, thumb.length)
        assertEquals(thumb.length - 1, draft.back().caret)
        assertEquals(1, draft.back().back().caret)
        assertEquals("AB", TextDraft(thumb, thumb.length - 1).erase().text)
        assertEquals(thumb.length - 1, TextDraft(thumb, 1).forward().caret)
        val flag = "🇯🇵"
        assertEquals("", TextDraft(flag).erase().text)
    }

    @Test
    fun oneCapitalWaitsForALetter() {
        // m, L2, 1, c: the digit leaves Shift for the c.
        val typed = openTyping("").typeKeys("m").run(Meaning.Shift).typeKeys("1c")
        assertEquals("M1C", typed.draft.text)
        // A word that starts with a digit has started: its letters are not capitals.
        assertEquals("1c", openTyping("").typeKeys("1c").draft.text)
        // Moving the caret does not use it up either.
        val moved = openTyping("").typeKeys("ab").run(Meaning.Shift, Meaning.CaretBack, Meaning.CaretForward).typeKeys("c")
        assertEquals("AbC", moved.draft.text)
        // A letter does, and only the one.
        assertEquals("AbCd", moved.typeKeys("d").draft.text)
        // A word's own capital is about where the caret is: moving to the end of a word drops it.
        assertEquals("Folder 1x", openTyping("Folder 1").run(Meaning.CaretForward).typeKeys("x").draft.text)
        assertEquals("XFolder 1", openTyping("Folder 1").run(Meaning.CaretBack).typeKeys("x").draft.text)
    }

    @Test
    fun wordsThatStartWithADigitTypeAsTheyRead() {
        assertEquals("2nd", openTyping("").typeKeys("2nd").draft.text)
        assertEquals("1st Run", openTyping("").typeKeys("1st run").draft.text)
        assertEquals("007", openTyping("").typeKeys("007").draft.text)
        // 3, caps lock for D and S, caps off, a space, then L2 twice at the next word to keep it lower case.
        val faves = openTyping("").typeKeys("3")
            .run(Meaning.Shift, Meaning.Shift).typeKeys("ds")
            .run(Meaning.Shift).typeKeys(" ")
            .run(Meaning.Shift, Meaning.Shift).typeKeys("faves")
        assertEquals("3DS faves", faves.draft.text)
        // Left alone, the second word takes its own capital.
        assertEquals("3DS Faves", openTyping("").typeKeys("3").run(Meaning.Shift, Meaning.Shift).typeKeys("ds")
            .run(Meaning.Shift).typeKeys(" faves").draft.text)
        // A mark first spends the word's capital too.
        assertEquals("'90s", openTyping("").typeKeys("'90s").draft.text)
    }

    @Test
    fun theLimitCountsWhatAReaderSeesAsOneCharacter() {
        val thumb = "👍🏽"
        val flag = "🇯🇵"
        val family = "👨‍👩‍👧"
        for (emoji in listOf(thumb, flag, family)) {
            assertEquals(1, nameLength(emoji))
            // 31 letters and the emoji are 32 characters: the whole name opens and saves.
            val name = "x".repeat(31) + emoji
            assertEquals(name, openTyping(name).draft.text)
            assertEquals(name, openTyping(name).on(Meaning.Done).saved)
            // One more character over, and the emoji is left off whole.
            assertEquals("x".repeat(32), openTyping("x".repeat(32) + emoji).draft.text)
            // At the limit, typing is refused; erase takes the emoji whole.
            val full = openTyping(name).run(Meaning.CaretForward)
            assertEquals(1, full.on(Meaning.Space).typing.refusals)
            assertEquals("x".repeat(31), full.run(Meaning.Erase).draft.text)
        }
        assertEquals("ab" + family, clipName("ab" + family + "cd", 3))
    }

    @Test
    fun typingFacesFollowTheCalibratedConfirmAndBack() {
        val standard = typingFaces(FaceMap.standard())
        assertEquals(TypingFaces(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y), standard)
        // Confirm on B, back on A: X and Y are free, so they keep Erase and Space.
        val swapped = typingFaces(faceMapWithConfirm(FaceMap.standard(), PromptKey.FaceB))
        assertEquals(TypingFaces(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y), swapped)
        // Confirm on X, back on Y: Erase and Space move to A and B.
        val onX = faceMapWithConfirm(FaceMap.standard(), PromptKey.FaceX)
        assertEquals(TypingFaces(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B), typingFaces(onX))
        val typing = HomeKeys.Typing
        assertEquals(Meaning.Activate, meaningOf(KeyEvent.KEYCODE_BUTTON_X, faces = onX, homeKeys = typing))
        assertEquals(Meaning.Back, meaningOf(KeyEvent.KEYCODE_BUTTON_Y, faces = onX, homeKeys = typing))
        assertEquals(Meaning.Erase, meaningOf(KeyEvent.KEYCODE_BUTTON_A, faces = onX, homeKeys = typing))
        assertEquals(Meaning.Space, meaningOf(KeyEvent.KEYCODE_BUTTON_B, faces = onX, homeKeys = typing))
        // Repeats follow the job: the erase button repeats, the typing one does not.
        assertEquals(Meaning.Erase, meaningOf(KeyEvent.KEYCODE_BUTTON_A, repeatCount = 2, faces = onX, homeKeys = typing))
        assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_X, repeatCount = 2, faces = onX, homeKeys = typing))
        // Confirm on Y, back on X: the same, the other way round.
        val onY = faceMapWithConfirm(FaceMap.standard(), PromptKey.FaceY)
        assertEquals(TypingFaces(KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B), typingFaces(onY))
        // The hint bar names the buttons that really erase and type a space.
        val hints = gridHints(null, typing, onX).map { it.key }
        assertEquals(listOf(PromptKey.FaceA, PromptKey.FaceB), hints.take(2))
        assertEquals(PromptKey.FaceY, hints.last())
    }
}
