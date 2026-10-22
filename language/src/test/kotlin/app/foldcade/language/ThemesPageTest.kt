package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemesPageTest {
    private fun entry(id: String, removable: Boolean) =
        ThemeEntry(id, id, "1.0.0", ThemeAbout(), emptyList(), removable = removable, inUse = false, onTrial = false)

    private val page = ThemesPage(listOf(entry("afterglow", false), entry("dusk", true)))

    @Test
    fun theRowsArePacksThenAddThenRestartOnceSomethingChanged() {
        assertEquals(listOf(ThemesRow.Pack(page.packs[0]), ThemesRow.Pack(page.packs[1]), ThemesRow.Add), page.rows)
        assertEquals(ThemesRow.Restart, page.settled(ThemesCopy.added, changed = true).rows.last())
    }

    @Test
    fun aOnAddOpensThePickerAndAOnRestartRestarts() {
        val onAdd = page.on(Meaning.MoveDown).first.on(Meaning.MoveDown).first
        assertEquals(ThemesAction.Add, onAdd.on(Meaning.Activate).second)
        val changed = onAdd.settled(ThemesCopy.added, changed = true).on(Meaning.MoveDown).first
        val (restarting, action) = changed.on(Meaning.Activate)
        assertEquals(ThemesAction.Restart, action)
        // Nothing else is pressed while Foldcade starts again.
        assertNull(restarting.on(Meaning.MoveUp).second)
    }

    @Test
    fun xTwiceRemovesAnInstalledPackAndNeverAShippedOne() {
        assertNull(page.on(Meaning.ActivateBottom).second)
        assertFalse(page.on(Meaning.ActivateBottom).first.armed)
        val dusk = page.on(Meaning.MoveDown).first
        val armed = dusk.on(Meaning.ActivateBottom).first
        assertTrue(armed.armed)
        assertEquals("Press X again to remove", armed.note)
        assertEquals(ThemesAction.Remove(page.packs[1]), armed.on(Meaning.ActivateBottom).second)
        // Moving away disarms it.
        assertFalse(armed.on(Meaning.MoveUp).first.armed)
    }

    @Test
    fun theRemovalNamesTheButtonCalibrationLeftForTheBottomScreen() {
        // Confirm on X moves the bottom screen to the first face left free.
        val faces = faceMapWithConfirm(FaceMap.standard(), PromptKey.FaceX)
        val armed = page.on(Meaning.MoveDown, faces).first.on(Meaning.ActivateBottom, faces).first
        assertEquals("Press ${letterOfKey(actionFaces(faces).bottom)?.name} again to remove", armed.note)
        assertFalse(armed.note == "Press X again to remove")
    }

    @Test
    fun anInstallInFlightTakesNoKeys() {
        val adding = page.adding()
        assertEquals(adding to null, adding.on(Meaning.Back))
        assertEquals(ThemesAction.Close, adding.settled(ThemesCopy.added, changed = true).on(Meaning.Back).second)
    }

    @Test
    fun packsLoadAfterTheOnesTheyExtendAndABrokenChainBuildsOnAfterglow() {
        fun zip(json: String) = ThemeZip(parseThemeJson(json)!!, emptyMap())
        val child = zip("""{"version":2,"id":"child","name":"Child","extends":"parent"}""")
        val parent = zip("""{"version":2,"id":"parent","name":"Parent"}""")
        val orphan = zip("""{"version":2,"id":"orphan","name":"Orphan","extends":"gone"}""")
        val a = zip("""{"version":2,"id":"a","name":"A","extends":"b"}""")
        val b = zip("""{"version":2,"id":"b","name":"B","extends":"a"}""")
        val notes = mutableListOf<Pair<String, String>>()
        val order = orderByExtends(listOf(child, parent, orphan, a, b)) { zip, note -> notes += zip.file.id to note.message }
        assertEquals(listOf("parent" to null, "child" to "parent", "orphan" to null, "b" to null, "a" to "b"), order.map { it.first.file.id to it.second })
        assertEquals(listOf("orphan", "b"), notes.map { it.first })
    }
}
