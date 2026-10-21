package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroTransitionTest {
    @Test
    fun motionOffIsAShortFadeWithoutScaleOrSlide() {
        assertTrue(Motion.heroFadeOnly(MotionSpeed.Off, animatorScale = 1f))
        assertEquals(Motion.durationShort, Motion.heroDuration(MotionSpeed.Off, animatorScale = 1f))
        assertEquals(Motion.durationShort, Motion.heroDuration(MotionSpeed.Off, animatorScale = 2f))
        assertEquals(1f, Motion.heroScale(0f, fadeOnly = true), 0.001f)
        assertEquals(Motion.durationShort, Motion.sceneDuration(MotionSpeed.Off, animatorScale = 1f))
    }

    @Test
    fun removedAnimationsUseTheSameShortFade() {
        assertTrue(Motion.heroFadeOnly(MotionSpeed.Slow, animatorScale = 0f))
        assertTrue(Motion.heroFadeOnly(MotionSpeed.Slower, animatorScale = 0f))
        assertEquals(Motion.durationShort, Motion.heroDuration(MotionSpeed.Slow, animatorScale = 0f))
        assertEquals(Motion.durationShort, Motion.heroDuration(MotionSpeed.Slower, animatorScale = -1f))
    }

    @Test
    fun slowerTakesTwiceTheTravelAndStillEases() {
        assertFalse(Motion.heroFadeOnly(MotionSpeed.Slow, animatorScale = 1f))
        assertFalse(Motion.heroFadeOnly(MotionSpeed.Slower, animatorScale = 1f))
        assertEquals(Motion.durationTravel, Motion.heroDuration(MotionSpeed.Slow, animatorScale = 1f))
        assertEquals(Motion.durationTravel * 2, Motion.heroDuration(MotionSpeed.Slower, animatorScale = 1f))
        assertEquals(Motion.durationTravel * 4, Motion.heroDuration(MotionSpeed.Slower, animatorScale = 2f))
        assertEquals(Motion.heroScaleFrom, Motion.heroScale(0f, fadeOnly = false), 0.001f)
        assertEquals(Motion.scaleRest, Motion.heroScale(1f, fadeOnly = false), 0.001f)
        assertTrue(Motion.heroScale(0.5f, fadeOnly = false) < Motion.scaleRest)
        assertEquals(Motion.durationScene * 2, Motion.sceneDuration(MotionSpeed.Slower, animatorScale = 1f))
        assertTrue(Motion.sceneDuration(MotionSpeed.Slow, 1f) > Motion.heroDuration(MotionSpeed.Slow, 1f))
    }

    @Test
    fun artArrivesFromTheWayTheCursorWentAndLeavesOnward() {
        assertEquals(Motion.heroTravelPx, Motion.heroEntryX(HeroTravel.Right), 0.001f)
        assertEquals(-Motion.heroTravelPx, Motion.heroExitX(HeroTravel.Right), 0.001f)
        assertEquals(0f, Motion.heroEntryY(HeroTravel.Right), 0.001f)
        assertEquals(Motion.heroTravelPx, Motion.heroEntryY(HeroTravel.Down), 0.001f)
        assertEquals(-Motion.heroTravelPx, Motion.heroExitY(HeroTravel.Down), 0.001f)
        assertEquals(-Motion.heroTravelPx, Motion.heroEntryY(HeroTravel.Up), 0.001f)
        // A jump rises a little and fades out where it is.
        assertEquals(Motion.heroSlideUpPx, Motion.heroEntryY(HeroTravel.None), 0.001f)
        assertEquals(0f, Motion.heroEntryX(HeroTravel.None), 0.001f)
        assertEquals(0f, Motion.heroExitX(HeroTravel.None), 0.001f)
        assertEquals(0f, Motion.heroExitY(HeroTravel.None), 0.001f)
    }

    @Test
    fun wideArtNeverShowsAnEdgeWhileItTravels() {
        assertEquals(Motion.sceneSettleFrom, Motion.sceneScale(1f), 0.001f)
        assertEquals(Motion.sceneOverscan, Motion.sceneScale(0f), 0.001f)
        assertEquals(Motion.sceneOverscan, Motion.sceneScale(-1f), 0.001f)
        // The Thor's panels are 1080 pixels high, their shortest side, so half the
        // overscan there covers the wide art's travel up or down.
        val shortestSidePx = 1080f
        val spare = shortestSidePx * (Motion.sceneOverscan - 1f) / 2f
        assertTrue(Motion.heroTravelPx * Motion.sceneDepth < spare)
    }

    @Test
    fun travelFollowsTheStripAndTheLongerStep() {
        // Columns of three: 0, 1, 2 down the first column, 3 at the top of the next.
        assertEquals(HeroTravel.Down, heroTravel(0, 1))
        assertEquals(HeroTravel.Up, heroTravel(2, 1))
        assertEquals(HeroTravel.Right, heroTravel(0, 3))
        assertEquals(HeroTravel.Left, heroTravel(3, 0))
        assertEquals(HeroTravel.Right, heroTravel(2, 5))
        // Across and down by one each reads across.
        assertEquals(HeroTravel.Right, heroTravel(0, 4))
        // Far along the strip, across still wins.
        assertEquals(HeroTravel.Right, heroTravel(1, 13))
        assertEquals(HeroTravel.None, heroTravel(null, 3))
        assertEquals(HeroTravel.None, heroTravel(3, 3))
    }

    @Test
    fun aHeldMoveKeepsTheSpeedItHas() {
        val fresh = Motion.arrive(Motion.durationTravel, 1f)
        // Still, or reversing, starts fresh.
        assertEquals(fresh, Motion.glide(Motion.durationTravel, 1f, distance = 1f, velocity = 0f))
        assertEquals(fresh, Motion.glide(Motion.durationTravel, 1f, distance = 1f, velocity = -3f))
        assertEquals(fresh, Motion.glide(Motion.durationTravel, 1f, distance = 0f, velocity = 3f))
        assertEquals(
            Motion.arrive(Motion.durationTravel, 0f),
            Motion.glide(Motion.durationTravel, 0f, distance = 1f, velocity = 3f),
        )
        // Mid-travel, the curve opens at the current speed instead of a fresh move's top speed.
        val distance = 2f
        val velocity = 20f
        val millis = Motion.durationTravel
        val carried = Motion.glide(millis, 1f, distance, velocity) as androidx.compose.animation.core.TweenSpec<Float>
        assertEquals(millis, carried.durationMillis)
        val dt = 0.001f
        val opening = carried.easing.transform(dt) / dt * distance / (millis / 1000f)
        assertEquals(velocity, opening, velocity * 0.15f)
        assertEquals(1f, carried.easing.transform(1f), 0.001f)
    }

    @Test
    fun rapidFocusReplacesTheDestinationAndDropsTheLayerAlreadyLeaving() {
        val resting = HeroBlend(front = "a", back = null, frontAlpha = 1f, backAlpha = 0f)
        val towardB = retargetHero(resting, "b").copy(frontAlpha = 0.4f, backAlpha = 0.55f)
        val towardC = retargetHero(towardB, "c")
        assertEquals("c", towardC.front)
        assertEquals("b", towardC.back)
        assertEquals(0f, towardC.frontAlpha, 0.001f)
        assertEquals(0.4f, towardC.backAlpha, 0.001f)
        assertFalse(towardC.back == "a")
        val stillC = retargetHero(towardC.copy(frontAlpha = 0.25f), "c")
        assertEquals("c", stillC.front)
        assertEquals("b", stillC.back)
        assertEquals(0.25f, stillC.frontAlpha, 0.001f)
    }

    @Test
    fun sameItemKeepsItsPlaceWhenOnlyTheCopyChanges() {
        val first = HeroBlend(
            front = HeroSubject.Item(HeroItem(key = "one", title = "One", detail = "A")),
            back = null,
            frontAlpha = 1f,
            backAlpha = 0f,
        )
        val renamed = HeroSubject.Item(HeroItem(key = "one", title = "One", detail = "B"))
        val next = retargetHero(first, renamed) { left, right -> left?.key == right?.key }
        assertEquals("B", (next.front as HeroSubject.Item).item.detail)
        assertNull(next.back)
        assertEquals(1f, next.frontAlpha, 0.001f)
    }

    @Test
    fun folderShowsItsNameAndCountAndNoArt() {
        val subject = HeroSubject.Folder(HeroFolder(key = "nintendo-3ds", name = "Nintendo 3DS", count = 12))
        val copy = heroCopy(subject)
        assertEquals("Nintendo 3DS", copy.title)
        assertEquals("12 games", copy.detail)
        assertFalse(copy.showsArt)
        assertEquals("1 game", folderCountLine(1))
        assertEquals("0 games", folderCountLine(0))
        assertEquals("0 games", folderCountLine(-4))
        assertEquals("14 apps", folderCountLine(14, apps = true))
        assertEquals("1 app", folderCountLine(1, apps = true))
        // Android Apps and Emulators count apps on the top screen as on their tiles.
        assertEquals("7 apps", heroCopy(HeroSubject.Folder(HeroFolder(key = HOME_EMULATORS, name = "Emulators", count = 7, apps = true))).detail)
        val unknown = heroCopy(HeroSubject.Folder(HeroFolder(key = "nds", name = "Nintendo DS", count = null)))
        assertEquals("Nintendo DS", unknown.title)
        assertEquals("", unknown.detail)
        assertFalse(unknown.showsArt)
    }

    @Test
    fun aFolderWithAMarkShowsIt() {
        val marked = HeroFolder(key = "nintendo-3ds", name = "Nintendo 3DS", count = 12, mark = "dual")
        assertTrue(heroCopy(HeroSubject.Folder(marked)).showsArt)
    }

    @Test
    fun heroFactsJoinWhatIsKnownOnce() {
        assertEquals(
            "Nintendo 3DS · On this device · Played 4h · Yesterday",
            heroFacts("Puzzle", "Nintendo 3DS", "On this device", null, " ", "Played 4h · Yesterday"),
        )
        assertEquals("Nintendo DS", heroFacts("Puzzle", "Nintendo DS", "Nintendo DS"))
        assertEquals("", heroFacts("Puzzle", null, ""))
        // A platform tile named for its platform does not repeat the name.
        assertEquals("", heroFacts("PC", "PC"))
    }

    @Test
    fun settledFrontIsNotACrossfadeLayer() {
        assertFalse(
            heroCrossfadeActive(hasFront = true, hasBack = false, frontAlpha = 1f, backAlpha = 0f),
        )
        assertFalse(
            heroCrossfadeActive(hasFront = false, hasBack = false, frontAlpha = 0f, backAlpha = 0f),
        )
        assertTrue(
            heroCrossfadeActive(hasFront = true, hasBack = true, frontAlpha = 0.4f, backAlpha = 0.6f),
        )
        assertTrue(
            heroCrossfadeActive(hasFront = true, hasBack = false, frontAlpha = 0.2f, backAlpha = 0f),
        )
        assertTrue(
            heroCrossfadeActive(hasFront = false, hasBack = false, frontAlpha = 1f, backAlpha = 0.2f),
        )
    }

    @Test
    fun itemKeepsItsNameAndArt() {
        val copy = heroCopy(
            HeroSubject.Item(
                HeroItem(key = "shelf.clamshell", title = "Clamshell", detail = "Dual screen", mark = "clamshell"),
            ),
        )
        assertEquals("Clamshell", copy.title)
        assertEquals("Dual screen", copy.detail)
        assertTrue(copy.showsArt)
    }
}
