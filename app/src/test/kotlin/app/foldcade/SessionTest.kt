package app.foldcade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    private val one = ExternalApp("stand-in.one")
    private val two = ExternalApp("stand-in.two")
    private val both = ExternalApp("both-panels", occupiesBothDisplays = true)

    @Test
    fun idleIsHeroOnTopAndPickerOnBottom() {
        val session = Session()
        assertEquals(Surface.Hero, session.surfaceOn(Panel.Top))
        assertEquals(Surface.Picker, session.surfaceOn(Panel.Bottom))
        assertEquals(Panel.Top, session.singleScreenTarget("stand-in.one", null))
        assertTrue(session.defaultDisplayIsTop)
        assertTrue(session.launchTargetControlVisible(occupiesBothDisplays = false))
        assertFalse(session.launchTargetControlVisible(occupiesBothDisplays = true))
    }

    @Test
    fun storedGameScreenThenPlatformThenTop() {
        val platformOnly = Session(platformScreens = mapOf("stand-ins" to Panel.Bottom))
        assertEquals(Panel.Bottom, platformOnly.singleScreenTarget("stand-in.one", "stand-ins"))
        val gameWins = platformOnly.copy(gameScreens = mapOf("stand-in.one" to Panel.Top))
        assertEquals(Panel.Top, gameWins.singleScreenTarget("stand-in.one", "stand-ins"))
    }

    @Test
    fun cycleStoresOnTheGameNotAGlobalFaceButton() {
        val cycled = Session().cycleStoredScreen("stand-in.one", "stand-ins", onPlatform = false)
        assertEquals(Panel.Bottom, cycled.gameScreens["stand-in.one"])
        assertTrue(cycled.platformScreens.isEmpty())
        val again = cycled.cycleStoredScreen("stand-in.one", "stand-ins", onPlatform = false)
        assertEquals(Panel.Top, again.gameScreens["stand-in.one"])
    }

    @Test
    fun cycleOnAPlatformStoresOnThePlatform() {
        val cycled = Session().cycleStoredScreen("ignored", "stand-ins", onPlatform = true)
        assertEquals(Panel.Bottom, cycled.platformScreens["stand-ins"])
        assertTrue(cycled.gameScreens.isEmpty())
    }

    @Test
    fun idleLaunchUsesTheStoredScreen() {
        val session = Session(gameScreens = mapOf("stand-in.one" to Panel.Bottom))
        val launched = session.launch(one)
        assertEquals(one, launched.bottomApp)
        assertNull(launched.topApp)
        assertEquals(Surface.Picker, launched.surfaceOn(Panel.Top))
        assertNull(launched.surfaceOn(Panel.Bottom))
        assertFalse(launched.launchTargetControlVisible(false))
    }

    @Test
    fun launchOnTopLeavesPickerOnBottom() {
        val launched = Session().launch(one)
        assertEquals(one, launched.topApp)
        assertNull(launched.bottomApp)
        assertNull(launched.surfaceOn(Panel.Top))
        assertEquals(Surface.Picker, launched.surfaceOn(Panel.Bottom))
    }

    @Test
    fun secondLaunchFillsTheOnlyFreePanel() {
        val bothFull = Session().launch(one).launch(two)
        assertEquals(one, bothFull.topApp)
        assertEquals(two, bothFull.bottomApp)
        assertNull(bothFull.surfaceOn(Panel.Top))
        assertNull(bothFull.surfaceOn(Panel.Bottom))
        assertNull(bothFull.singleScreenTarget("other", null))
        assertEquals(bothFull, bothFull.launch(ExternalApp("ignored")))
    }

    @Test
    fun onlyFreePanelIgnoresTheStoredScreen() {
        val appOnTop = Session().launch(one).copy(gameScreens = mapOf("stand-in.two" to Panel.Top))
        assertEquals(Panel.Bottom, appOnTop.singleScreenTarget("stand-in.two", null))
        val appOnBottom = Session(gameScreens = mapOf(one.id to Panel.Bottom)).launch(one)
        assertEquals(Panel.Top, appOnBottom.singleScreenTarget("stand-in.two", null))
    }

    @Test
    fun homeClearsOnlyThePanelWithInput() {
        val full = Session().launch(one).launch(two)
        val homeTop = full.home(Panel.Top)
        assertNull(homeTop.topApp)
        assertEquals(two, homeTop.bottomApp)
        assertEquals(Surface.Picker, homeTop.surfaceOn(Panel.Top))
        assertNull(homeTop.surfaceOn(Panel.Bottom))

        val homeBottom = full.home(Panel.Bottom)
        assertEquals(one, homeBottom.topApp)
        assertNull(homeBottom.bottomApp)
        assertNull(homeBottom.surfaceOn(Panel.Top))
        assertEquals(Surface.Picker, homeBottom.surfaceOn(Panel.Bottom))
    }

    @Test
    fun homeOnThePickerDoesNotDismissTheOtherApp() {
        val appOnTop = Session().launch(one)
        val after = appOnTop.home(Panel.Bottom)
        assertEquals(one, after.topApp)
        assertEquals(Surface.Picker, after.surfaceOn(Panel.Bottom))
    }

    @Test
    fun homeOnTheLastExternalAppRestoresIdle() {
        val idle = Session().launch(one).home(Panel.Top)
        assertEquals(Surface.Hero, idle.surfaceOn(Panel.Top))
        assertEquals(Surface.Picker, idle.surfaceOn(Panel.Bottom))

        val idleAgain = Session(gameScreens = mapOf(one.id to Panel.Bottom)).launch(one).home(Panel.Bottom)
        assertEquals(Surface.Hero, idleAgain.surfaceOn(Panel.Top))
        assertEquals(Surface.Picker, idleAgain.surfaceOn(Panel.Bottom))
    }

    @Test
    fun homeDoesNotMoveStoredScreensOrDisplayRoles() {
        val session = Session(
            gameScreens = mapOf(one.id to Panel.Bottom),
            defaultDisplayIsTop = false,
        )
        val after = session.launch(one).launch(two).home(Panel.Top)
        assertEquals(Panel.Bottom, after.gameScreens[one.id])
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
        assertEquals(Surface.Picker, homeBottom.surfaceOn(Panel.Bottom))

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

        val appOnTop = idle.launch(one)
        assertFalse(appOnTop.pickerHandlesKeys(Panel.Top))
        assertTrue(appOnTop.pickerHandlesKeys(Panel.Bottom))

        val appOnBottom = Session(gameScreens = mapOf(one.id to Panel.Bottom)).launch(one)
        assertTrue(appOnBottom.pickerHandlesKeys(Panel.Top))
        assertFalse(appOnBottom.pickerHandlesKeys(Panel.Bottom))

        val full = appOnTop.launch(two)
        assertFalse(full.pickerHandlesKeys(Panel.Top))
        assertFalse(full.pickerHandlesKeys(Panel.Bottom))
    }

    @Test
    fun clearIfCurrentDoesNotClearADifferentApp() {
        val session = Session().launch(one)
        assertEquals(session, session.clearIfCurrent(Panel.Top, "other"))
        assertNull(session.clearIfCurrent(Panel.Top, one.id).topApp)
    }
}
