package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ScreenLayoutTest {
    @Test
    fun eachLayoutNamesItsGridHeroDarkAndMenuScreens() {
        with(ScreenLayout.ShowcaseTop) {
            assertEquals(HostScreen.Bottom, gridScreen)
            assertEquals(HostScreen.Top, showcaseScreen)
            assertNull(darkScreen)
            assertEquals(HostScreen.Top, menuScreen)
        }
        with(ScreenLayout.ShowcaseBottom) {
            assertEquals(HostScreen.Top, gridScreen)
            assertEquals(HostScreen.Bottom, showcaseScreen)
            assertNull(darkScreen)
            assertEquals(HostScreen.Bottom, menuScreen)
        }
        // One screen: the islands sit over the grid, and the bottom is dark.
        with(ScreenLayout.TopOnly) {
            assertEquals(HostScreen.Top, gridScreen)
            assertNull(showcaseScreen)
            assertEquals(HostScreen.Bottom, darkScreen)
            assertEquals(HostScreen.Top, menuScreen)
        }
    }

    @Test
    fun aSavedIdReadsBackAndAnUnknownOneIsTheDefault() {
        ScreenLayout.entries.forEach { assertEquals(it, ScreenLayout.fromId(it.id)) }
        assertEquals(ScreenLayout.ShowcaseTop, ScreenLayout.fromId(null))
        assertEquals(ScreenLayout.ShowcaseTop, ScreenLayout.fromId("bottom-only"))
    }

    @Test
    fun theIslandsOpenOnTheLayoutsMenuScreenAndSettingsOnTop() {
        for (layout in ScreenLayout.entries) {
            val model = PickerModel(count = 4, rowsPerPage = 2, layout = layout)
            // A key from either screen opens the island where the island is.
            for (from in HostScreen.entries) {
                assertEquals(layout.menuScreen, reduce(model, Meaning.LeftPanel, from).first.panel?.screen)
                assertEquals(layout.menuScreen, reduce(model, Meaning.RightPanel, from).first.panel?.screen)
                // Settings takes the larger top screen in every layout.
                assertEquals(HostScreen.Top, reduce(model, Meaning.Menu, from).first.settings?.screen)
            }
        }
    }

    @Test
    fun layoutStepsThroughEveryChoiceBothWaysAndPlayOnFlips() {
        val start = PickerModel(count = 2, rowsPerPage = 2).onSetting(Row.ScreenLayout)
        assertEquals(ScreenCopy.showcaseTop, rowText(Row.ScreenLayout, start).value)
        val right = reduce(start, Meaning.MoveRight).first
        assertEquals(ScreenLayout.ShowcaseBottom, right.layout)
        assertEquals(ScreenCopy.showcaseBottom, rowText(Row.ScreenLayout, right).value)
        val again = reduce(right, Meaning.MoveRight).first
        assertEquals(ScreenLayout.TopOnly, again.layout)
        assertEquals(ScreenCopy.topOnly, rowText(Row.ScreenLayout, again).value)
        assertEquals(ScreenLayout.ShowcaseTop, reduce(again, Meaning.MoveRight).first.layout)
        assertEquals(ScreenLayout.TopOnly, reduce(start, Meaning.MoveLeft).first.layout)
        assertEquals(RowControl.Choice(ScreenCopy.showcaseTop), rowControl(Row.ScreenLayout, start))

        val playOn = PickerModel(count = 2, rowsPerPage = 2).onSetting(Row.PlayOn)
        assertEquals(Copy.top, rowText(Row.PlayOn, playOn).value)
        val bottom = reduce(playOn, Meaning.Activate).first
        assertEquals(HostScreen.Bottom, bottom.playOn)
        assertEquals(Copy.bottom, rowText(Row.PlayOn, bottom).value)
        assertEquals(HostScreen.Top, reduce(bottom, Meaning.MoveLeft).first.playOn)
    }

    @Test
    fun nothingIsLeftOnTheDarkScreen() {
        val dialog = homePrompt(HostScreen.Bottom)
        val model = PickerModel(count = 2, rowsPerPage = 2, layout = ScreenLayout.TopOnly, dialog = dialog, connectScreen = HostScreen.Bottom)
        val lit = model.offTheDarkScreen()
        assertEquals(HostScreen.Top, lit.dialog?.screen)
        assertEquals(HostScreen.Top, lit.connectScreen)
        // With two screens lit nothing moves.
        val both = model.copy(layout = ScreenLayout.ShowcaseBottom)
        assertSame(both, both.offTheDarkScreen())
        // Already on the lit screen: the same model.
        assertSame(lit, lit.offTheDarkScreen())
    }

    @Test
    fun aAndXNameThePlayOnScreenAndTheOther() {
        val faces = FaceMap.standard()
        val top = gridHints(null, HomeKeys.Idle, faces, screens = true)
        assertEquals(listOf(Copy.hintTop, Copy.hintBottom), top.take(2).map { it.label })
        val bottom = gridHints(null, HomeKeys.Idle, faces, screens = true, playOn = HostScreen.Bottom)
        assertEquals(listOf(Copy.hintBottom, Copy.hintTop), bottom.take(2).map { it.label })
        assertEquals(top.take(2).map { it.key }, bottom.take(2).map { it.key })
        val found = findResultHints(faces, screens = true, options = false, playOn = HostScreen.Bottom)
        assertEquals(listOf(Copy.hintBottom, Copy.hintTop), found.take(2).map { it.label })
    }

    @Test
    fun aConnectionDrawerFromSettingsLeftOnTheOldScreenOpensWhereThePageWas() {
        // Settings opened in Classic, on top, then Layout stepped to Swapped: the page stays put,
        // and RomM's drawer opens on that screen, which Foldcade still shows. The islands draw it there.
        val signedIn = PickerModel(count = 2, rowsPerPage = 2, signedIn = listOf(SignedInBackend("romm", "RomM")))
        val connection = librarySources(signedIn).first()
        val swapped = signedIn.onSetting(connection, HostScreen.Top).copy(layout = ScreenLayout.ShowcaseBottom)
        val drawer = reduce(swapped, Meaning.Activate).first
        assertEquals(PanelLevel.RommConnection, drawer.panel?.level)
        assertEquals(HostScreen.Top, drawer.panel?.screen)
    }

    @Test
    fun aPageAimedAtAScreenAGameHoldsOpensWhereFoldcadeIs() {
        // Swapped with a game on top: Settings asked for the top, and gets the islands' screen.
        val model = PickerModel(count = 2, rowsPerPage = 2, layout = ScreenLayout.ShowcaseBottom)
        val opened = reduce(model, Meaning.Menu).first
        assertEquals(HostScreen.Top, opened.settings?.screen)
        val moved = opened.onShownScreens { it == HostScreen.Bottom }
        assertEquals(HostScreen.Bottom, moved.settings?.screen)
        // Nothing shown: nothing to move to.
        assertSame(opened, opened.onShownScreens { false })
    }
}
