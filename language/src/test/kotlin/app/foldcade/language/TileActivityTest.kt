package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileActivityTest {
    @Test
    fun theTopScreenSaysWhatTheLibraryIsDoing() {
        assertEquals("Downloading", activityLine(TileActivity.Downloading(null)))
        assertEquals("Downloading 0%", activityLine(TileActivity.Downloading(0f)))
        assertEquals("Downloading 42%", activityLine(TileActivity.Downloading(0.429f)))
        assertEquals("Downloading 100%", activityLine(TileActivity.Downloading(1.4f)))
        assertEquals("Getting firmware", activityLine(TileActivity.Downloading(null, firmware = true)))
        assertEquals("Getting firmware 0%", activityLine(TileActivity.Downloading(0f, firmware = true)))
        assertEquals("Getting firmware 58%", activityLine(TileActivity.Downloading(0.589f, firmware = true)))
        assertEquals("Getting firmware 100%", activityLine(TileActivity.Downloading(1.4f, firmware = true)))
        assertEquals("Checking", activityLine(TileActivity.Checking))
        assertEquals("Syncing saves", activityLine(TileActivity.Syncing))
        assertEquals("Saves synced", activityLine(TileActivity.Synced(SyncMark.Synced, 0L)))
        assertEquals("Kept both saves", activityLine(TileActivity.Synced(SyncMark.KeptBoth, 0L)))
        assertEquals("Saves will sync later", activityLine(TileActivity.Synced(SyncMark.Later, 0L)))
        assertEquals("Saves didn’t sync", activityLine(TileActivity.Synced(SyncMark.NotSynced, 0L)))
    }

    @Test
    fun onlyASyncThatRanEarnsTheCheck() {
        assertTrue(SyncMark.Synced.worked)
        assertTrue(SyncMark.KeptBoth.worked)
        assertFalse(SyncMark.Later.worked)
        assertFalse(SyncMark.NotSynced.worked)
    }

    @Test
    fun aMarkStaysLongEnoughToSeeAndNoLonger() {
        assertTrue(Motion.durationSyncMark > Motion.durationFocus + Motion.durationTravel)
        assertTrue(Motion.durationSyncMark <= 3_000)
    }
}
