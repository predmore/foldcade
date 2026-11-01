package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchBloomTest {
    @Test
    fun motionOffAndRemovedAnimationsOpenTheGameWithoutABloom() {
        assertNull(Motion.launchBloom(MotionSpeed.Off, animatorScale = 1f))
        assertNull(Motion.launchBloom(MotionSpeed.Slow, animatorScale = 0f))
        assertNull(Motion.launchBloom(MotionSpeed.Slower, animatorScale = 0f))
        assertNotNull(Motion.launchBloom(MotionSpeed.Slow, animatorScale = 1f))
    }

    @Test
    fun slowerAndAndroidsScaleStretchTheBloom() {
        val slow = Motion.launchBloom(MotionSpeed.Slow, 1f)!!
        val slower = Motion.launchBloom(MotionSpeed.Slower, 1f)!!
        val scaled = Motion.launchBloom(MotionSpeed.Slow, 2f)!!
        assertEquals(Motion.durationLaunch, slow.growMillis)
        assertEquals(Motion.durationLaunchHold, slow.holdMillis)
        assertEquals(Motion.durationScene, slow.leaveMillis)
        assertEquals(slow.growMillis * 2, slower.growMillis)
        assertEquals(slow.holdMillis * 2, slower.holdMillis)
        assertEquals(slow.growMillis * 2, scaled.growMillis)
    }

    @Test
    fun theArtGrowsFastThenSettlesFillingTheScreen() {
        val bloom = Motion.launchBloom(MotionSpeed.Slow, 1f)!!
        assertEquals(0f, bloom.grow(0), 0.001f)
        assertEquals(1f, bloom.grow(bloom.growMillis.toLong()), 0.001f)
        assertEquals(1f, bloom.grow(bloom.growMillis * 3L), 0.001f)
        // It leaves the tile gently: one frame moves it only a little, so a held-up frame never jumps.
        assertTrue(bloom.grow(16) < 0.05f)
        assertTrue(bloom.grow(16) > 0f)
        // Then most of the way by half time, so the screen fills before the game opens.
        assertTrue(bloom.grow(bloom.growMillis / 2L) > 0.6f)
        // Art with no place on the screen fades in over the same span.
        assertEquals(0f, bloom.arrive(0), 0.001f)
        assertEquals(1f, bloom.arrive(bloom.growMillis.toLong()), 0.001f)
    }

    @Test
    fun thePushKeepsMovingAndRestsAtItsScale() {
        val bloom = Motion.launchBloom(MotionSpeed.Slow, 1f)!!
        assertEquals(1f, bloom.pushScale(0), 0.0001f)
        val early = bloom.pushScale(bloom.pushMillis / 4L)
        val later = bloom.pushScale(bloom.pushMillis / 2L)
        assertTrue(early > 1f && later > early && later < bloom.push)
        assertEquals(bloom.push, bloom.pushScale(bloom.pushMillis.toLong()), 0.0001f)
        assertEquals(bloom.push, bloom.pushScale(bloom.pushMillis * 4L), 0.0001f)
    }

    @Test
    fun theScreenBesideTheGameGivesWayOnceGrownAndHeld() {
        val bloom = Motion.launchBloom(MotionSpeed.Slow, 1f)!!
        val leaves = bloom.leavesAt(gameHere = false)
        assertEquals((bloom.growMillis + bloom.holdMillis).toLong(), leaves)
        assertEquals(1f, bloom.presence(leaves, leaves), 0.001f)
        assertEquals(1f, bloom.presence(0, leaves), 0.001f)
        assertEquals(0f, bloom.presence(leaves + bloom.leaveMillis, leaves), 0.001f)
        assertFalse(bloom.done(leaves + bloom.leaveMillis - 1, leaves))
        assertTrue(bloom.done(leaves + bloom.leaveMillis, leaves))
    }

    @Test
    fun theGamesScreenWaitsForTheGameButNotForever() {
        val bloom = Motion.launchBloom(MotionSpeed.Slow, 1f)!!
        val leaves = bloom.leavesAt(gameHere = true)
        assertEquals(LaunchBloom.WAIT_MILLIS, leaves)
        assertTrue(leaves > bloom.leavesAt(gameHere = false))
        assertTrue(bloom.done(leaves + bloom.leaveMillis, leaves))
    }
}
