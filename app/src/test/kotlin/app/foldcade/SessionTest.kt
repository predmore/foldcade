package app.foldcade

import app.foldcade.language.ScreenLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    @Test fun settingsActivitiesArePageDestinationsWithoutASecondGrid() {
        for (layout in ScreenLayout.entries) {
            val page = Session(layout = layout).place(Panel.Top, ExternalApp("themes", foldcadePage = true))
            assertNull(page.surfaceOn(Panel.Top))
            assertTrue(page.pageAvailable(Panel.Top))
            assertTrue(page.besideFoldcadePage(Panel.Bottom))
            assertEquals(Panel.Top, page.pageKeyTarget(Panel.Bottom))
            assertNull(page.pageKeyTarget(Panel.Top))
            assertEquals(if (layout == ScreenLayout.TopOnly) Surface.Dark else Surface.CommandCenter, page.surfaceOn(Panel.Bottom))
            val returned = page.foldcadeResumed(Panel.Top)
            assertTrue(returned.bothScreensFree())
            assertFalse(returned.besideFoldcadePage(Panel.Bottom))
        }
        val game = Session().place(Panel.Top, ExternalApp("game"))
        assertFalse(game.pageAvailable(Panel.Top))
        assertFalse(game.besideFoldcadePage(Panel.Bottom))
        assertNull(game.pageKeyTarget(Panel.Bottom))
        val below = Session().place(Panel.Bottom, ExternalApp("licenses", foldcadePage = true))
        assertEquals(Panel.Bottom, below.pageKeyTarget(Panel.Top))
        assertNull(below.pageKeyTarget(Panel.Bottom))
    }
    private val one = ExternalApp("stand-in.one")
    private val two = ExternalApp("stand-in.two")
    private val both = ExternalApp("both-panels", occupiesBothDisplays = true)

    @Test
    fun idleIsHeroOnTopAndPickerOnBottom() {
        val session = Session()
        assertEquals(Surface.Hero, session.surfaceOn(Panel.Top))
        assertEquals(Surface.Picker, session.surfaceOn(Panel.Bottom))
        assertEquals(Panel.Top, session.singleScreenTarget(Panel.Top))
        assertEquals(Panel.Bottom, session.singleScreenTarget(Panel.Bottom))
        assertTrue(session.defaultDisplayIsTop)
    }

    @Test
    fun runsSeesAGameOnEitherScreen() {
        assertFalse(Session().runs(one.id))
        assertTrue(Session().launch(one, Panel.Bottom).runs(one.id))
        assertTrue(Session().launch(one, Panel.Top).launch(two, Panel.Bottom).runs(one.id))
        assertFalse(Session().launch(two, Panel.Top).runs(one.id))
    }

    @Test
    fun xLaunchesOnTheBottomAndLeavesTheCommandCenterOnTop() {
        val launched = Session().launch(one, Panel.Bottom)
        assertEquals(one, launched.bottomApp)
        assertNull(launched.topApp)
        assertEquals(Surface.CommandCenter, launched.surfaceOn(Panel.Top))
        assertNull(launched.surfaceOn(Panel.Bottom))
    }

    @Test
    fun launchOnTopLeavesTheCommandCenterOnBottom() {
        val launched = Session().launch(one)
        assertEquals(one, launched.topApp)
        assertNull(launched.bottomApp)
        assertNull(launched.surfaceOn(Panel.Top))
        assertEquals(Surface.CommandCenter, launched.surfaceOn(Panel.Bottom))
    }

    @Test
    fun theIconEditorTakesTheHeroOrBesideAnAppTheGrid() {
        assertEquals(Panel.Top, Session().editorPanel())
        // A game on the bottom: the top browses, and the editor covers that grid.
        val bottom = Session().launch(one, Panel.Bottom)
        assertNull(bottom.editorPanel())
        assertEquals(Panel.Top, bottom.browse(Panel.Top).editorPanel())
        assertEquals(Panel.Bottom, Session().launch(one).browse(Panel.Bottom).editorPanel())
        assertNull(Session().launch(one).launch(two).editorPanel())
    }

    @Test
    fun secondLaunchFillsTheOnlyFreePanel() {
        val bothFull = Session().launch(one).launch(two)
        assertEquals(one, bothFull.topApp)
        assertEquals(two, bothFull.bottomApp)
        assertNull(bothFull.surfaceOn(Panel.Top))
        assertNull(bothFull.surfaceOn(Panel.Bottom))
        assertNull(bothFull.singleScreenTarget(Panel.Top))
        assertEquals(bothFull, bothFull.launch(ExternalApp("ignored")))
    }

    @Test
    fun aTakenScreenSendsTheLaunchToTheFreeOneAndReplacesNothing() {
        val appOnTop = Session().launch(one)
        assertEquals(Panel.Bottom, appOnTop.singleScreenTarget(Panel.Top))
        val appOnBottom = Session().launch(one, Panel.Bottom)
        assertEquals(Panel.Top, appOnBottom.singleScreenTarget(Panel.Bottom))
    }

    @Test
    fun homeClearsOnlyThePanelWithInput() {
        val full = Session().launch(one).launch(two)
        val homeTop = full.home(Panel.Top)
        assertNull(homeTop.topApp)
        assertEquals(two, homeTop.bottomApp)
        assertEquals(Surface.CommandCenter, homeTop.surfaceOn(Panel.Top))
        assertNull(homeTop.surfaceOn(Panel.Bottom))

        val homeBottom = full.home(Panel.Bottom)
        assertEquals(one, homeBottom.topApp)
        assertNull(homeBottom.bottomApp)
        assertNull(homeBottom.surfaceOn(Panel.Top))
        assertEquals(Surface.CommandCenter, homeBottom.surfaceOn(Panel.Bottom))
    }

    @Test
    fun homeOnTheCommandCenterDoesNotDismissTheOtherApp() {
        val appOnTop = Session().launch(one)
        val after = appOnTop.home(Panel.Bottom)
        assertEquals(one, after.topApp)
        assertEquals(Surface.CommandCenter, after.surfaceOn(Panel.Bottom))
    }

    @Test
    fun homeOnTheLastExternalAppRestoresIdle() {
        val idle = Session().launch(one).home(Panel.Top)
        assertEquals(Surface.Hero, idle.surfaceOn(Panel.Top))
        assertEquals(Surface.Picker, idle.surfaceOn(Panel.Bottom))

        val idleAgain = Session().launch(one, Panel.Bottom).home(Panel.Bottom)
        assertEquals(Surface.Hero, idleAgain.surfaceOn(Panel.Top))
        assertEquals(Surface.Picker, idleAgain.surfaceOn(Panel.Bottom))
    }

    @Test
    fun homeDoesNotMoveDisplayRoles() {
        val session = Session(defaultDisplayIsTop = false)
        val after = session.launch(one).launch(two).home(Panel.Top)
        assertFalse(after.defaultDisplayIsTop)
    }

    @Test
    fun bothPanelAppClearsBothSurfacesUntilHomeOnTop() {
        val running = Session().launch(both)
        assertEquals(both, running.topApp)
        assertEquals(both, running.bottomApp)
        assertNull(running.surfaceOn(Panel.Top))
        assertNull(running.surfaceOn(Panel.Bottom))

        val homeTop = running.home(Panel.Top)
        assertTrue(homeTop.bothScreensFree())
        assertEquals(Surface.Hero, homeTop.surfaceOn(Panel.Top))
        assertEquals(Surface.Picker, homeTop.surfaceOn(Panel.Bottom))
    }

    @Test
    fun homeOnTheBottomOfABothPanelAppClearsOnlyTheBottom() {
        val homeBottom = Session().launch(both).home(Panel.Bottom)
        assertEquals(both, homeBottom.topApp)
        assertNull(homeBottom.bottomApp)
        assertEquals(Surface.CommandCenter, homeBottom.surfaceOn(Panel.Bottom))

        val idle = homeBottom.home(Panel.Top)
        assertTrue(idle.bothScreensFree())
    }

    @Test
    fun foldcadeResumedClearsTheSingleScreenAppOnThatPanel() {
        val full = Session().launch(one).launch(two)
        val top = full.foldcadeResumed(Panel.Top)
        assertNull(top.topApp)
        assertEquals(two, top.bottomApp)

        val bottom = full.foldcadeResumed(Panel.Bottom)
        assertEquals(one, bottom.topApp)
        assertNull(bottom.bottomApp)
    }

    @Test
    fun foldcadeResumedOnTopEndsABothPanelApp() {
        val idle = Session().launch(both).foldcadeResumed(Panel.Top)
        assertTrue(idle.bothScreensFree())
    }

    @Test
    fun foldcadeResumedOnTheBottomKeepsABothPanelApp() {
        // The app's second-screen window draws over the bottom Foldcade without pausing it.
        val running = Session().launch(both)
        assertEquals(running, running.foldcadeResumed(Panel.Bottom))
    }

    @Test
    fun foldcadeResumedOnAFreePanelChangesNothing() {
        val appOnTop = Session().launch(one)
        assertEquals(appOnTop, appOnTop.foldcadeResumed(Panel.Bottom))
        assertEquals(Session(), Session().foldcadeResumed(Panel.Top))
    }

    @Test
    fun pickerHandlesKeysOnlyWhenItIsTheFocusedSurface() {
        val idle = Session()
        assertFalse(idle.pickerHandlesKeys(Panel.Top))
        assertTrue(idle.pickerHandlesKeys(Panel.Bottom))

        // Beside an app the command center takes keys until the user opens the grid there.
        val appOnTop = idle.launch(one)
        assertFalse(appOnTop.pickerHandlesKeys(Panel.Top))
        assertFalse(appOnTop.pickerHandlesKeys(Panel.Bottom))
        assertTrue(appOnTop.browse(Panel.Bottom).pickerHandlesKeys(Panel.Bottom))

        val appOnBottom = Session().launch(one, Panel.Bottom)
        assertFalse(appOnBottom.pickerHandlesKeys(Panel.Top))
        assertTrue(appOnBottom.browse(Panel.Top).pickerHandlesKeys(Panel.Top))
        assertFalse(appOnBottom.pickerHandlesKeys(Panel.Bottom))

        val full = appOnTop.launch(two)
        assertFalse(full.pickerHandlesKeys(Panel.Top))
        assertFalse(full.pickerHandlesKeys(Panel.Bottom))
    }

    @Test
    fun islandsOpenOnlyWhileTheTopScreenIsFree() {
        assertTrue(Session().islandsOpen())
        assertTrue(Session().launch(one, Panel.Bottom).browse(Panel.Top).islandsOpen())
        // Browsing beside an app on top, the menu would open under that app.
        assertFalse(Session().launch(one).browse(Panel.Bottom).islandsOpen())
    }

    @Test
    fun browsingShowsTheGridBesideAnAppUntilSomethingTakesThatScreen() {
        val appOnTop = Session().launch(one)
        val browsing = appOnTop.browse(Panel.Bottom)
        assertEquals(Panel.Bottom, browsing.browsing)
        assertEquals(Surface.Picker, browsing.surfaceOn(Panel.Bottom))
        assertEquals(Surface.CommandCenter, browsing.stopBrowsing().surfaceOn(Panel.Bottom))

        // A second app on the browsed screen ends it, and so does the first app leaving.
        assertNull(browsing.launch(two).browsing)
        assertNull(browsing.home(Panel.Top).browsing)
        assertEquals(Surface.Picker, browsing.home(Panel.Top).surfaceOn(Panel.Bottom))
        // Home on the browsed screen leaves the grid there.
        assertEquals(Panel.Bottom, browsing.home(Panel.Bottom).browsing)
    }

    @Test
    fun browseOnlyOpensACommandCenter() {
        val idle = Session()
        assertEquals(idle, idle.browse(Panel.Bottom))
        val appOnTop = idle.launch(one)
        assertEquals(appOnTop, appOnTop.browse(Panel.Top))
        val bothPanels = idle.launch(both)
        assertEquals(bothPanels, bothPanels.browse(Panel.Bottom))
    }

    @Test
    fun theCommandCenterRestsDimUntilItsScreenIsWoken() {
        val appOnTop = Session().launch(one)
        assertTrue(commandCenterDimmed(appOnTop, Panel.Bottom, awake = null))
        assertFalse(commandCenterDimmed(appOnTop, Panel.Bottom, awake = Panel.Bottom))
        assertFalse(commandCenterDimmed(appOnTop, Panel.Top, awake = null))
        // The grid opened from it, a Foldcade page, and an app never dim.
        assertFalse(commandCenterDimmed(appOnTop.browse(Panel.Bottom), Panel.Bottom, awake = null))
        assertFalse(commandCenterDimmed(Session(), Panel.Bottom, awake = null))
        assertFalse(commandCenterDimmed(appOnTop.launch(two), Panel.Bottom, awake = null))
    }

    @Test
    fun clearIfCurrentDoesNotClearADifferentApp() {
        val session = Session().launch(one)
        assertEquals(session, session.clearIfCurrent(Panel.Top, "other"))
        assertNull(session.clearIfCurrent(Panel.Top, one.id).topApp)
    }
    @Test fun startUsesTheSameTopScreenOwnershipGuardAsShoulders() {
        val running = Session().launch(one, Panel.Top).browse(Panel.Bottom)
        assertEquals(Surface.Picker, running.surfaceOn(Panel.Bottom))
        for (meaning in listOf(app.foldcade.language.Meaning.Menu,
            app.foldcade.language.Meaning.LeftPanel, app.foldcade.language.Meaning.RightPanel)) {
            assertFalse(!app.foldcade.language.opensTopMenu(meaning) || running.islandsOpen())
            assertTrue(!app.foldcade.language.opensTopMenu(meaning) || Session().islandsOpen())
        }
    }

    @Test
    fun swappedPutsTheGridOnTopAndTheHeroWithItsIslandsBelow() {
        val swapped = Session(layout = ScreenLayout.ShowcaseBottom)
        assertEquals(Surface.Picker, swapped.surfaceOn(Panel.Top))
        assertEquals(Surface.Hero, swapped.surfaceOn(Panel.Bottom))
        assertEquals(Panel.Top, swapped.gridPanel())
        assertEquals(Panel.Bottom, swapped.menuPanel())
        assertEquals(Panel.Bottom, swapped.heroPanel())
        assertEquals(Panel.Top, swapped.controlsPanel())
        assertNull(swapped.darkPanel())
        assertTrue(swapped.islandsOpen())
        // A game on top leaves the bottom its command center, and its islands.
        val gameOnTop = swapped.launch(one, Panel.Top)
        assertEquals(Surface.CommandCenter, gameOnTop.surfaceOn(Panel.Bottom))
        assertTrue(gameOnTop.islandsOpen())
        assertEquals(Panel.Bottom, gameOnTop.controlsPanel())
        // A game on the bottom covers the islands, as one on top does in Classic.
        val gameBelow = swapped.launch(one, Panel.Bottom)
        assertEquals(Surface.CommandCenter, gameBelow.surfaceOn(Panel.Top))
        assertFalse(gameBelow.islandsOpen())
        assertNull(gameBelow.heroPanel())
    }

    @Test
    fun topOnlyLeavesTheBottomDarkEvenBesideAGame() {
        val one = Session(layout = ScreenLayout.TopOnly)
        assertEquals(Surface.Picker, one.surfaceOn(Panel.Top))
        assertEquals(Surface.Dark, one.surfaceOn(Panel.Bottom))
        assertNull(one.heroPanel())
        assertEquals(Panel.Top, one.menuPanel())
        assertEquals(Panel.Top, one.controlsPanel())
        assertTrue(one.draws(Panel.Top))
        assertFalse(one.draws(Panel.Bottom))
        assertTrue(one.islandsOpen())
        // A game on top: the bottom stays dark, with nothing to browse.
        val gameOnTop = one.launch(this.one, Panel.Top)
        assertEquals(Surface.Dark, gameOnTop.surfaceOn(Panel.Bottom))
        assertEquals(gameOnTop, gameOnTop.browse(Panel.Bottom))
        assertNull(gameOnTop.controlsPanel())
        assertFalse(gameOnTop.islandsOpen())
        // X opens a game on the dark screen, and the grid's screen beside it is the command center.
        val gameBelow = one.launch(this.one, Panel.Bottom)
        assertNull(gameBelow.surfaceOn(Panel.Bottom))
        assertEquals(Surface.CommandCenter, gameBelow.surfaceOn(Panel.Top))
        assertEquals(Surface.Picker, gameBelow.browse(Panel.Top).surfaceOn(Panel.Top))
        // Home back from it leaves the bottom dark again.
        assertEquals(Surface.Dark, gameBelow.home(Panel.Bottom).surfaceOn(Panel.Bottom))
        // The dark screen's keys drive the grid only while the grid's screen is Foldcade's.
        assertTrue(one.takesKeys(Panel.Bottom))
        assertFalse(gameOnTop.takesKeys(Panel.Bottom))
        assertFalse(gameOnTop.takesKeys(Panel.Top))
        assertTrue(gameBelow.takesKeys(Panel.Top))
        // The dark screen is not dimmed as a command center: it has its own darkness.
        assertFalse(commandCenterDimmed(gameOnTop, Panel.Bottom, awake = null))
    }

    @Test
    fun aGameOpensOnPlayOnUnlessItIsTaken() {
        val below = Session(playOn = Panel.Bottom)
        assertEquals(one, below.launch(one).bottomApp)
        assertNull(below.launch(one).topApp)
        // Taken: the other screen, as before.
        assertEquals(two, below.launch(one).launch(two).topApp)
        assertEquals(one, Session().launch(one).topApp)
    }

    @Test
    fun aNewLayoutKeepsEveryAppAndDropsBrowsingOnAScreenThatWentDark() {
        val browsing = Session().launch(one, Panel.Top).browse(Panel.Bottom)
        assertEquals(Panel.Bottom, browsing.browsing)
        val dark = browsing.withLayout(ScreenLayout.TopOnly)
        assertEquals(one, dark.topApp)
        assertNull(dark.browsing)
        assertEquals(Surface.Dark, dark.surfaceOn(Panel.Bottom))
        val swapped = browsing.withLayout(ScreenLayout.ShowcaseBottom)
        assertEquals(Panel.Bottom, swapped.browsing)
        assertEquals(Surface.Picker, swapped.surfaceOn(Panel.Bottom))
        assertSame(browsing, browsing.withLayout(ScreenLayout.ShowcaseTop))
    }
}
