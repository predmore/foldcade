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
        assertEquals(0f, Motion.heroSlidePx(0f, fadeOnly = true), 0.001f)
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
        assertEquals(Motion.heroSlideUpPx, Motion.heroSlidePx(0f, fadeOnly = false), 0.001f)
        assertEquals(0f, Motion.heroSlidePx(1f, fadeOnly = false), 0.001f)
        assertTrue(Motion.heroScale(0.5f, fadeOnly = false) < Motion.scaleRest)
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
