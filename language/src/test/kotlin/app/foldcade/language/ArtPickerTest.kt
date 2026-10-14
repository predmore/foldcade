package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class ArtPickerTest {
    private val candidates = (0..13).map { ArtCandidate("$it", "https://cdn/$it", "https://cdn/thumb/$it") }

    @Test fun choicesRoundTripWithoutChangingOtherGamesOrKinds() {
        val choices = ArtChoices().set("game\n\"one", ArtKind.Tile, "https://cdn/tile")
            .set("game\n\"one", ArtKind.Hero, "https://cdn/hero")
            .set("two", ArtKind.Logo, "https://cdn/logo")
        assertEquals(choices, ArtChoices.decode(choices.encode()))
        val reset = ArtChoices.decode(choices.encode()).set("game\n\"one", ArtKind.Tile, null)
        assertNull(reset.get("game\n\"one", ArtKind.Tile))
        assertEquals("https://cdn/hero", reset.get("game\n\"one", ArtKind.Hero))
        assertEquals("https://cdn/logo", reset.get("two", ArtKind.Logo))
        assertFalse(reset.set("two", ArtKind.Logo, null).values.containsKey("two"))
    }

    @Test fun oldInstallsAndUnknownFutureKindsDecodeSafely() {
        assertEquals(ArtChoices(), ArtChoices.decode(null))
        assertEquals(ArtChoices(), ArtChoices.decode("broken"))
        val choices = ArtChoices.decode("""{"game":{"Tile":"https://cdn/tile","Future":"https://cdn/future","Logo":"http://unsafe"},"old":false}""")
        assertEquals(mapOf("game" to mapOf(ArtKind.Tile to "https://cdn/tile")), choices.values)
    }

    @Test fun navigationRunsDownThreeRowsThenSidewaysAndStaysWithinCandidates() {
        var state = ArtPickerState("game", candidates = candidates, loading = false)
        assertEquals(0, state.navigate(Meaning.MoveLeft).index)
        assertEquals(0, state.navigate(Meaning.MoveUp).index)
        state = state.navigate(Meaning.MoveDown).navigate(Meaning.MoveDown)
        assertEquals(2, state.index)
        assertEquals(2, state.navigate(Meaning.MoveDown).index)
        assertEquals(5, state.navigate(Meaning.MoveRight).index)
        assertEquals(13, state.copy(index = 11).navigate(Meaning.MoveRight).index)
        assertEquals(0, ArtPickerState("game").navigate(Meaning.MoveRight).index)
    }

    @Test fun tabsWrapAndDiscardThePreviousCandidatesAndError() {
        val state = ArtPickerState("game", match = ArtMatch(1, "Game"), findingMatches = false,
            index = 9, candidates = candidates, loading = false, error = true)
        val logo = state.navigate(Meaning.LeftPanel)
        assertEquals(ArtKind.Logo, logo.kind)
        assertEquals(0, logo.index)
        assertTrue(logo.loading)
        assertFalse(logo.error)
        assertTrue(logo.candidates.isEmpty())
        assertEquals(ArtKind.Tile, logo.navigate(Meaning.RightPanel).kind)
        assertTrue(state.copy(preview = false).navigate(Meaning.MoveRight).preview)
    }

    @Test fun artIsOfferedAfterFilingAndBeforeRemovalWithoutTakingAChord() {
        val rows = actionRows(TileActions(move = true, art = true, destinations = listOf(FolderChoice("c", "Collection")), remove = true), null)
        assertEquals(listOf(Row.MoveTile, Row.MoveTileTo, Row.ChooseArt, Row.RemoveTile), rows)
        assertNull(chords(rows)[2])
        assertFalse(actionRows(TileActions(move = true), null).contains(Row.ChooseArt))
    }

    @Test fun leftAtTheFirstColumnKeepsEveryRow() {
        for (row in 0..2) {
            val state = ArtPickerState("game", candidates = candidates, index = row, loading = false)
            assertEquals(row, state.navigate(Meaning.MoveLeft).index)
            assertEquals(row, state.copy(index = row + 3).navigate(Meaning.MoveLeft).index)
        }
    }

    @Test fun pickerKeysKeepHeldVerticalMovementAndUseTriggersOnlyOncePerPress() {
        val context = HomeKeys.ChoosingArt
        assertEquals(Meaning.MoveUp, meaningOf(android.view.KeyEvent.KEYCODE_DPAD_UP, repeatCount = 4, homeKeys = context))
        assertEquals(Meaning.MoveDown, meaningOf(android.view.KeyEvent.KEYCODE_DPAD_DOWN, repeatCount = 4, homeKeys = context))
        assertEquals(Meaning.ActivateBottom, meaningOf(android.view.KeyEvent.KEYCODE_BUTTON_X, homeKeys = context))
        assertEquals(Meaning.LetterBackward, meaningOf(android.view.KeyEvent.KEYCODE_BUTTON_L2, homeKeys = context))
        assertEquals(Meaning.LetterForward, meaningOf(android.view.KeyEvent.KEYCODE_BUTTON_R2, homeKeys = context))
        assertNull(meaningOf(android.view.KeyEvent.KEYCODE_BUTTON_R2, repeatCount = 1, homeKeys = context))
        assertEquals(Meaning.MoveDown, meaningOf(android.view.KeyEvent.KEYCODE_DPAD_DOWN, repeatCount = 4, homeKeys = HomeKeys.AllLibrary))
    }

    @Test fun matchesCycleWithWrapAndDiscardOnlyTheCandidateSelection() {
        val matches = listOf(ArtMatch(1, "One"), ArtMatch(2, "Two"), ArtMatch(3, "Three"))
        val state = ArtPickerState("game", kind = ArtKind.Logo, matches = matches, match = matches[0],
            findingMatches = false, candidates = candidates, index = 5, loading = false, error = true)
        val next = state.navigate(Meaning.LetterBackward)
        assertEquals(matches[2], next.match)
        assertEquals(ArtKind.Logo, next.kind)
        assertEquals(matches, next.matches)
        assertTrue(next.loading)
        assertFalse(next.error)
        assertTrue(next.candidates.isEmpty())
        assertEquals(0, next.index)
        assertEquals(matches[0], next.navigate(Meaning.LetterForward).match)
        assertEquals(state, state.copy(matches = listOf(matches[0])).navigate(Meaning.LetterForward).copy(matches = matches))
    }

    @Test fun chosenMatchesRoundTripAlongsideOldOverridesAndSurviveReset() {
        val original = ArtChoices.decode("""{"game":{"Tile":"https://cdn/tile"}}""")
        val choices = original.setMatch("game", ArtMatch(7, "QuackShot"))
            .setMatch("match-only", ArtMatch(8, "Other"))
        assertEquals(choices, ArtChoices.decode(choices.encode()))
        val reset = choices.set("game", ArtKind.Tile, null)
        assertEquals(ArtMatch(7, "QuackShot"), ArtChoices.decode(reset.encode()).matches["game"])
        assertTrue(reset.values.isEmpty())
        assertEquals(mapOf("game" to mapOf(ArtKind.Tile to "https://cdn/tile")), original.values)
        assertTrue(original.matches.isEmpty())
    }

    @Test fun hintsPairBothKindsAndMatchesWithOneLabelEach() {
        val groups = artPickerHintGroups(FaceMap.standard())
        assertEquals(5, groups.size)
        assertEquals(listOf(HintKey(PromptKey.L1, ""), HintKey(PromptKey.R1, ArtCopy.kind)), groups[0])
        assertEquals(listOf(HintKey(PromptKey.L2, ""), HintKey(PromptKey.R2, ArtCopy.match)), groups[1])
        assertEquals(1, artPickerHints(FaceMap.standard()).count { it.label == ArtCopy.kind })
        assertEquals(1, artPickerHints(FaceMap.standard()).count { it.label == ArtCopy.match })
    }

    @Test fun changingKindsAfterAnEmptyOrFailedMatchSearchKeepsItsSettledState() {
        for (failed in listOf(false, true)) {
            for (direction in listOf(Meaning.LeftPanel, Meaning.RightPanel)) {
                var state = ArtPickerState("game", findingMatches = false, loading = false, error = failed)
                repeat(3) {
                    val previous = state.kind
                    state = state.navigate(direction)
                    assertNotEquals(previous, state.kind)
                    assertFalse("No request can load candidates without a match", state.loading)
                    assertEquals(failed, state.error)
                    assertNull(state.match)
                    assertTrue(state.candidates.isEmpty())
                }
                assertEquals(ArtKind.Tile, state.kind)
            }
        }
    }

    @Test fun changingKindsWhileTheMatchSearchIsRunningKeepsLoadingUntilItFinishes() {
        val state = ArtPickerState("game").navigate(Meaning.RightPanel)
        assertEquals(ArtKind.Hero, state.kind)
        assertTrue(state.findingMatches)
        assertTrue(state.loading)
        assertFalse(state.error)
    }

}
