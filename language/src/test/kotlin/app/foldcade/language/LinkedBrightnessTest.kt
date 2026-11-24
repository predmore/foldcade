package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R1's brightness: one slider for both screens, split for a while, then linked again. */
class LinkedBrightnessTest {
    private val thor = PluginRegistry.thor.packageName
    private fun device(top: Float = 0.5f, bottom: Float = 0.5f) = parseDeviceState(thor, mapOf(
        "ok" to true, "device" to DEVICE_THOR, "root" to "granted", "capabilities" to arrayOf("brightness", "fan"),
        "fan" to "balanced", "brightnessTop" to top, "brightnessBottom" to bottom,
    ))!!

    /** R1 open on its brightness row, the top at [top] and the bottom at [bottom] (0 to 1), with or without the plugin. */
    private fun r1(top: Int = 50, bottom: Float = 0.5f, plugin: Boolean = true, allowed: Boolean = true): PickerModel {
        val closed = PickerModel(count = 4, rowsPerPage = 1, brightness = top, brightnessAllowed = allowed,
            device = if (plugin) device(bottom = bottom) else null)
        return reduce(closed, Meaning.RightPanel).first
    }

    private fun PickerModel.rows() = panelRows(panel!!, this).drop(quickSettings().size).takeWhile { it == Row.Brightness || it == Row.BottomBrightness }
    private fun PickerModel.focused() = panelRows(panel!!, this)[panel!!.index]
    private fun PickerModel.hints() = panelHints(this)!!.map { it.label }

    @Test fun withoutThePluginR1IsTodaysOneSlider() {
        val r1 = r1(plugin = false)
        assertEquals(listOf(Row.Brightness), r1.rows())
        assertEquals(Row.Brightness, r1.focused())
        assertEquals(RowText(BrightnessCopy.row, "50%"), rowText(Row.Brightness, r1))
        assertEquals(listOf(BrightnessCopy.adjust, Copy.back), r1.hints())
        // Left and right move Android's level alone; A does nothing.
        val (right, effect) = reduce(r1, Meaning.MoveRight)
        assertEquals(55, right.brightness)
        assertNull(effect)
        assertEquals(r1.copy(panel = r1.panel), reduce(r1, Meaning.Activate).first.copy(menuRows = r1.menuRows))
        assertNull(reduce(r1, Meaning.Activate).second)
        // Without permission A asks, as ever.
        assertEquals(DialogKind.Brightness, reduce(r1(plugin = false, allowed = false), Meaning.Activate).first.dialog?.kind)
    }

    @Test fun matchingScreensShowOneSliderThatDrivesBoth() {
        val r1 = r1()
        assertTrue(r1.brightnessLinked())
        assertEquals(listOf(Row.Brightness), r1.rows())
        assertEquals(BrightnessCopy.row, rowText(Row.Brightness, r1).label)
        assertEquals(listOf(BrightnessCopy.adjust, BrightnessCopy.split, Copy.back), r1.hints())
        // The top through Android, the bottom through the plugin, at the same position.
        val (right, effect) = reduce(r1, Meaning.MoveRight)
        assertEquals(55, right.brightness)
        assertEquals(Effect.SetBottomBrightness(55), effect)
        assertEquals(0.55f, right.device!!.brightnessBottom!!, 0.0001f)
        assertTrue(right.brightnessLinked())
        assertEquals(Effect.SetBottomBrightness(45), reduce(r1, Meaning.MoveLeft).second)
        // A point apart is rounding, not a difference.
        assertTrue(r1(top = 50, bottom = 0.51f).brightnessLinked())
        assertFalse(r1(top = 50, bottom = 0.53f).brightnessLinked())
    }

    @Test fun aSplitsTheSliderAndTheFocusLandsOnTop() {
        val split = reduce(r1(), Meaning.Activate).first
        assertEquals(listOf(Row.Brightness, Row.BottomBrightness), split.rows())
        assertEquals(Row.Brightness, split.focused())
        assertEquals(RowText(DeviceCopy.top, "50%"), rowText(Row.Brightness, split))
        assertEquals(RowText(DeviceCopy.bottom, "50%"), rowText(Row.BottomBrightness, split))
        assertEquals(listOf(BrightnessCopy.adjust, BrightnessCopy.link, Copy.back), split.hints())
        // Each moves its own screen now.
        val (top, topEffect) = reduce(split, Meaning.MoveRight)
        assertEquals(55, top.brightness)
        assertNull(topEffect)
        assertEquals(0.5f, top.device!!.brightnessBottom!!, 0.0001f)
        val bottom = reduce(split, Meaning.MoveDown).first
        assertEquals(Row.BottomBrightness, bottom.focused())
        assertEquals(listOf(BrightnessCopy.adjust, BrightnessCopy.link, Copy.back), bottom.hints())
        assertEquals(Effect.SetBottomBrightness(45), reduce(bottom, Meaning.MoveLeft).second)
    }

    @Test fun withoutPermissionALinkedSliderAsksRatherThanSplits() {
        val asked = reduce(r1(allowed = false), Meaning.Activate).first
        assertEquals(DialogKind.Brightness, asked.dialog?.kind)
        assertFalse(asked.brightnessUnlinked)
        assertEquals(listOf(BrightnessCopy.allow, Copy.back), r1(allowed = false).hints())
    }

    @Test fun linkTakesTheLevelOfTheSliderAWasPressedOn() {
        val split = reduce(r1(), Meaning.Activate).first
        // From Top at 70: the bottom takes 70.
        val top = reduce(reduce(reduce(reduce(split, Meaning.MoveRight).first, Meaning.MoveRight).first, Meaning.MoveRight).first, Meaning.MoveRight).first
        assertEquals(70, top.brightness)
        val (fromTop, topEffect) = reduce(top, Meaning.Activate)
        assertEquals(Effect.SetBottomBrightness(70), topEffect)
        assertEquals(listOf(Row.Brightness), fromTop.rows())
        assertTrue(fromTop.brightnessLinked())
        assertEquals(0.7f, fromTop.device!!.brightnessBottom!!, 0.0001f)
        // From Bottom at 30: the top takes 30, and the focus goes to the one slider.
        val bottom = reduce(reduce(reduce(split, Meaning.MoveDown).first, Meaning.MoveLeft).first, Meaning.MoveLeft).first
        assertEquals(40, bottomBrightnessPosition(bottom.device!!.brightnessBottom!!))
        val (fromBottom, bottomEffect) = reduce(bottom, Meaning.Activate)
        assertEquals(40, fromBottom.brightness)
        assertEquals(Effect.SetBottomBrightness(40), bottomEffect)
        assertEquals(listOf(Row.Brightness), fromBottom.rows())
        assertEquals(Row.Brightness, fromBottom.focused())
    }

    @Test fun screensThatDifferOpenSplitAndLinkFixesThem() {
        val differ = r1(top = 60, bottom = 0.3f)
        assertEquals(listOf(Row.Brightness, Row.BottomBrightness), differ.rows())
        assertEquals(Row.Brightness, differ.focused())
        assertEquals(BrightnessCopy.link, differ.hints()[1])
        val linked = reduce(differ, Meaning.Activate).first
        assertEquals(listOf(Row.Brightness), linked.rows())
        assertEquals(60, bottomBrightnessPosition(linked.device!!.brightnessBottom!!))
    }

    @Test fun aSplitLastsTheR1VisitItWasMadeIn() {
        val split = reduce(r1(), Meaning.Activate).first
        assertTrue(split.brightnessUnlinked)
        // R1 closes with nothing changed; the shell settles the model as it does every change.
        val closed = reduce(split, Meaning.RightPanel).first
        val settled = retainMenuRows(split, closed)
        assertFalse(settled.brightnessUnlinked)
        val again = reduce(settled, Meaning.RightPanel).first
        assertEquals(listOf(Row.Brightness), again.rows())
        // Still open, it stays split.
        assertTrue(retainMenuRows(split, reduce(split, Meaning.MoveDown).first).brightnessUnlinked)
    }
}
