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
        val state = ArtPickerState("game", index = 9, candidates = candidates, loading = false, error = true)
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
}
