package app.foldcade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SaveConflictHandoffTest {
    @Test
    fun aLivingStarterRefreshesItself() {
        val homes = listOf(SaveConflictHome(1, destroyed = false, resumed = true))
        assertNull(saveConflictSurvivor(starterAlive = true, homes))
    }

    @Test
    fun aDeadStarterHandsTheConflictToTheResumedHome() {
        val homes = listOf(
            SaveConflictHome(1, destroyed = true, resumed = false),
            SaveConflictHome(2, destroyed = false, resumed = false),
            SaveConflictHome(3, destroyed = false, resumed = true),
        )
        assertEquals(3, saveConflictSurvivor(starterAlive = false, homes))
    }

    @Test
    fun aDeadStarterWithNoResumedHomeUsesOneThatIsStillUp() {
        val homes = listOf(
            SaveConflictHome(1, destroyed = true, resumed = false),
            SaveConflictHome(2, destroyed = false, resumed = false),
        )
        assertEquals(2, saveConflictSurvivor(starterAlive = false, homes))
    }

    @Test
    fun noHomeLeftMeansNothingToRefresh() {
        val homes = listOf(SaveConflictHome(1, destroyed = true, resumed = false))
        assertNull(saveConflictSurvivor(starterAlive = false, homes))
    }
}
