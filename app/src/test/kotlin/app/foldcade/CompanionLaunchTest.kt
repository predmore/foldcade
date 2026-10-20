package app.foldcade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionLaunchTest {
    @Test
    fun aStartInFlightBlocksASecondAndAFailureLetsTheNextOneThrough() {
        val launch = CompanionLaunch()
        assertTrue(launch.begin(2))
        assertFalse(launch.begin(2))
        assertEquals(2, launch.targetDisplayId)
        launch.failed()
        assertFalse(launch.starting)
        assertTrue(launch.begin(2))
    }

    @Test
    fun theCompanionArrivingClearsTheStart() {
        val launch = CompanionLaunch()
        launch.begin(2)
        launch.arrived()
        assertFalse(launch.starting)
        assertEquals(null, launch.targetDisplayId)
        assertTrue(launch.begin(2))
    }

    @Test
    fun removingTheTargetDisplayBeforeItArrivesAllowsAnotherStart() {
        val launch = CompanionLaunch()
        launch.begin(2)
        launch.displayRemoved(displayId = 3, companionAliveOnIt = false)
        assertTrue(launch.starting)
        launch.displayRemoved(displayId = 2, companionAliveOnIt = true)
        assertTrue(launch.starting)
        launch.displayRemoved(displayId = 2, companionAliveOnIt = false)
        assertFalse(launch.starting)
        assertTrue(launch.begin(2))
    }
}
