package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileActivityTest {
    @Test fun aCatalogWaitUsesAFriendlyNameUntilTheTitleArrives() {
        assertEquals(Copy.yourGame, pendingGameTitle("lib:romm:23", "lib:romm:23", TileActivity.LoadingLibrary))
        assertEquals("Gain Ground", pendingGameTitle("Gain Ground", "lib:romm:23", TileActivity.LoadingLibrary))
        assertEquals("Gain Ground", pendingGameTitle("Gain Ground", "lib:romm:23", null))
    }

    @Test fun aCatalogWaitPrefersTheCatalogThenHistoryThenTheOfflineTitle() {
        val id = "lib:romm:23"
        val wait = TileActivity.LoadingLibrary
        assertEquals("Live title", pendingGameTitle("Live title", id, wait, "Played title", "Cached title"))
        assertEquals("Played title", pendingGameTitle(id, id, wait, "Played title", "Cached title"))
        assertEquals("Cached title", pendingGameTitle(id, id, wait, "  ", "Cached title"))
        assertEquals(Copy.yourGame, pendingGameTitle(id, id, wait, id, "  "))
        assertEquals("Played title", pendingGameTitle("  ", id, wait, " Played title ", null))
        assertEquals("Live title", pendingGameTitle("Live title", id, TileActivity.PreparingOnServer, "Played title", "Cached title"))
    }

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

    @Test fun coreProgressNamesTheCoreAndKeepsTheDownloadFraction() {
        assertEquals("Getting Gambatte…", activityLine(TileActivity.Downloading(null, core = "Gambatte")))
        assertEquals("Getting Genesis Plus GX… 42%", activityLine(TileActivity.Downloading(.42f, core = "Genesis Plus GX")))
        val dialog = coreDownloadFailedDialog("Gambatte", HostScreen.Top)
        assertEquals(listOf(DialogButton.TryAgain, DialogButton.OpenRetroArch, DialogButton.NotNow), dialog.buttons)
        assertEquals(2, dialog.safeIndex)
    }

    @Test
    fun aMarkStaysLongEnoughToSeeAndNoLonger() {
        assertTrue(Motion.durationSyncMark > Motion.durationFocus + Motion.durationTravel)
        assertTrue(Motion.durationSyncMark <= 3_000)
    }
}
