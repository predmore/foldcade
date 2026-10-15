package app.foldcade.language

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveConflictPromptTest {
    private val prompt = SaveConflictPrompt(
        id = "1234:autosave",
        libraryId = "romm",
        gameLabel = "Bravely Second",
        local = SaveConflictSide(
            "This device · Azahar",
            Instant.parse("2026-04-18T09:12:00Z").toEpochMilli(),
            32_768,
        ),
        remote = SaveConflictSide(
            "Steam Deck · Azahar",
            Instant.parse("2026-04-18T10:40:00Z").toEpochMilli(),
            31_200,
        ),
    )

    @Test
    fun keepBothStartsFocusedAndChoosingItKeepsThePromptData() {
        assertEquals(SaveConflictFocus.KeepBoth, prompt.focus)
        val model = PickerModel(count = 1, rowsPerPage = 1, saveConflict = prompt)
        val (next, effect) = reduce(model, Meaning.Activate)
        assertEquals(
            Effect.ResolveSaveConflict("romm", "1234:autosave", SaveConflictAction.KeepBoth),
            effect,
        )
        assertNull(next.saveConflict)
        assertEquals(prompt.local, prompt.local)
        assertEquals(prompt.remote, prompt.remote)
    }

    @Test
    fun keepThisOneOnTheLocalCopy() {
        val model = PickerModel(count = 1, rowsPerPage = 1, saveConflict = prompt)
        val focused = reduce(model, Meaning.MoveLeft).first
        assertEquals(SaveConflictFocus.Local, focused.saveConflict?.focus)
        val (next, effect) = reduce(focused, Meaning.Activate)
        assertEquals(SaveConflictAction.KeepLocal, (effect as Effect.ResolveSaveConflict).action)
        assertEquals(prompt.local.source, focused.saveConflict?.local?.source)
        assertEquals(prompt.remote.source, focused.saveConflict?.remote?.source)
        assertNull(next.saveConflict)
    }

    @Test
    fun keepThisOneOnTheRemoteCopy() {
        val model = PickerModel(count = 1, rowsPerPage = 1, saveConflict = prompt)
        val focused = reduce(model, Meaning.MoveRight).first
        assertEquals(SaveConflictFocus.Remote, focused.saveConflict?.focus)
        val (next, effect) = reduce(focused, Meaning.Activate)
        assertEquals(SaveConflictAction.KeepRemote, (effect as Effect.ResolveSaveConflict).action)
        assertEquals(prompt.local.sizeBytes, focused.saveConflict?.local?.sizeBytes)
        assertEquals(prompt.remote.sizeBytes, focused.saveConflict?.remote?.sizeBytes)
        assertNull(next.saveConflict)
    }

    @Test
    fun backDecidesLaterAndKeepsBothCopiesOnThePrompt() {
        val model = PickerModel(count = 1, rowsPerPage = 1, saveConflict = prompt)
        val (next, effect) = reduce(model, Meaning.Back)
        val choice = effect as Effect.ResolveSaveConflict
        assertEquals(SaveConflictAction.DecideLater, choice.action)
        assertEquals(prompt.id, choice.id)
        assertNull(next.saveConflict)
        val held = applySaveConflict(prompt, Meaning.Back).first
        assertEquals(prompt.local, held.local)
        assertEquals(prompt.remote, held.remote)
    }

    @Test
    fun downFromEitherCopyReturnsToKeepBoth() {
        val local = prompt.copy(focus = SaveConflictFocus.Local)
        assertEquals(SaveConflictFocus.KeepBoth, applySaveConflict(local, Meaning.MoveDown).first.focus)
        val remote = prompt.copy(focus = SaveConflictFocus.Remote)
        assertEquals(SaveConflictFocus.KeepBoth, applySaveConflict(remote, Meaning.MoveLeft).first.focus)
    }

    @Test
    fun aStampAndASizeReadAsAPersonWould() {
        val whenLocal = formatSaveWhen(prompt.local.updatedAtMillis, ZoneOffset.UTC)
        assertEquals("18 Apr 2026, 09:12", whenLocal)
        assertEquals("32 KB", formatSaveSize(prompt.local.sizeBytes))
        assertEquals("30.5 KB", formatSaveSize(prompt.remote.sizeBytes))
    }

    @Test
    fun thorBottomCardsShareOneWidthAndUseTheTightSpacing() {
        // 1240 × 1080 px at 419 dpi is about 473 × 412 dp.
        val fit = saveConflictFit(1240f / 2.61875f, 1080f / 2.61875f)
        assertTrue(fit.compact)
        assertEquals(1240f / 2.61875f, fit.edge * 2 + fit.cardWidth * 2 + fit.gap, 0.01f)
        assertTrue("each card keeps room for a source and a stamp", fit.cardWidth >= 200f)
    }

    @Test
    fun aWideShortPanelCapsTheCardsAndStaysCompact() {
        val fit = saveConflictFit(1920f / 2.61875f, 1080f / 2.61875f)
        assertTrue(fit.compact)
        assertEquals((600f - fit.gap) / 2f, fit.cardWidth, 0.01f)
    }

    @Test
    fun aTallPanelKeepsTheRoomySpacing() {
        val fit = saveConflictFit(560f, 600f)
        assertFalse(fit.compact)
        assertEquals(28f, fit.edge)
        assertEquals(560f - 56f, fit.cardWidth * 2 + fit.gap, 0.01f)
    }

    @Test
    fun aNarrowPanelNeverGivesACardANegativeWidth() {
        assertEquals(0f, saveConflictFit(20f, 300f).cardWidth)
    }
}
