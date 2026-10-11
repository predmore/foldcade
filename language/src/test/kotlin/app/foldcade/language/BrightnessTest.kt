package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightnessTest {
    @Test
    fun theCurveSpansAndroidsManualRangeAndNeverStepsBack() {
        assertEquals(1, brightnessLevel(0))
        assertEquals(255, brightnessLevel(100))
        val levels = (0..100).map(::brightnessLevel)
        assertTrue(levels.zipWithNext().all { (a, b) -> b >= a })
        // Android's slider curve: halfway along is far below half the level.
        assertEquals(22, brightnessLevel(50))
        assertEquals(66, brightnessPosition(44))
        assertEquals(44, brightnessLevel(brightnessPosition(44)))
    }

    @Test
    fun rOneOpensOnBrightnessAndTheSideKeysStepIt() {
        val opened = reduce(allowed(position = 60), Meaning.RightPanel).first
        assertEquals(Row.Brightness, panelRows(opened.panel!!, opened)[opened.panel!!.index])
        assertEquals(listOf(HintKey(PromptKey.DpadSides, BrightnessCopy.adjust), HintKey(PromptKey.FaceB, Copy.back)), panelHints(opened))
        val brighter = reduce(opened, Meaning.MoveRight).first
        assertEquals(65, brighter.brightness)
        assertEquals(opened.panel, brighter.panel)
        assertEquals(55, reduce(opened, Meaning.MoveLeft).first.brightness)
        assertEquals(100, reduce(allowed(position = 98), Meaning.RightPanel).first.let { reduce(it, Meaning.MoveRight).first.brightness })
        // A does nothing once access is granted, and the row reads its percent.
        val (pressed, effect) = reduce(opened, Meaning.Activate)
        assertEquals(opened, pressed)
        assertNull(effect)
        assertEquals("Brightness  60%", rowLabel(Row.Brightness, opened))
    }

    @Test
    fun withoutAccessEveryKeyOnTheRowAsksFirst() {
        val opened = reduce(PickerModel(count = 2, rowsPerPage = 2), Meaning.RightPanel).first
        assertNull(panelHints(opened))
        assertEquals("Brightness  Allow", rowLabel(Row.Brightness, opened))
        listOf(Meaning.Activate, Meaning.MoveLeft, Meaning.MoveRight).forEach { meaning ->
            val asked = reduce(opened, meaning).first
            assertEquals(DialogKind.Brightness, asked.dialog?.kind)
            assertEquals(opened.brightness, asked.brightness)
            assertEquals(DialogButton.Allow, asked.dialog!!.buttons[asked.dialog!!.index])
            val (_, choice) = reduce(asked, Meaning.Back)
            assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.Brightness), choice)
        }
    }

    @Test
    fun keyboardBracketsOpenTheDrawersAndEscapeGoesBack() {
        assertEquals(Meaning.LeftPanel, meaningOf(android.view.KeyEvent.KEYCODE_LEFT_BRACKET))
        assertEquals(Meaning.RightPanel, meaningOf(android.view.KeyEvent.KEYCODE_RIGHT_BRACKET))
        assertNull(meaningOf(android.view.KeyEvent.KEYCODE_RIGHT_BRACKET, repeatCount = 1))
        assertEquals(Meaning.Back, meaningOf(android.view.KeyEvent.KEYCODE_ESCAPE))
        assertEquals(Meaning.CancelHold, meaningOf(android.view.KeyEvent.KEYCODE_ESCAPE, homeKeys = HomeKeys.Moving))
    }

    private fun allowed(position: Int) =
        PickerModel(count = 2, rowsPerPage = 2, brightness = position, brightnessAllowed = true)
}
