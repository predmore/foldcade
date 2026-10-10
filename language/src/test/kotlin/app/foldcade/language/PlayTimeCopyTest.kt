package app.foldcade.language

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayTimeCopyTest {
    @Test
    fun playedLineUsesActiveMinutes() {
        assertEquals("Played  —", playedLine(0))
        assertEquals("Played  <1m", playedLine(59_000))
        assertEquals("Played  1m", playedLine(60_000))
        assertEquals("Played  1h", playedLine(3_600_000))
        assertEquals("Played  1h 1m", playedLine(3_660_000))
        assertEquals("Approximate", approximatePlayNote(approximate = true))
        assertEquals(null, approximatePlayNote(approximate = false))
    }

    @Test
    fun lastPlayedLineUsesTheLocalDay() {
        val zone = ZoneOffset.UTC
        val now = Instant.parse("2026-10-09T15:00:00Z").toEpochMilli()
        assertEquals("Last played  —", lastPlayedLine(null, now, zone))
        assertEquals("Last played  Today", lastPlayedLine(Instant.parse("2026-10-09T01:00:00Z").toEpochMilli(), now, zone))
        assertEquals("Last played  Yesterday", lastPlayedLine(Instant.parse("2026-10-08T23:00:00Z").toEpochMilli(), now, zone))
        assertEquals("Last played  2 Mar", lastPlayedLine(Instant.parse("2026-03-02T12:00:00Z").toEpochMilli(), now, zone))
        assertEquals("Last played  9 Oct 2025", lastPlayedLine(Instant.parse("2025-10-09T12:00:00Z").toEpochMilli(), now, zone))
    }

    @Test
    fun usageAccessIsOfferedOnceAfterASessionAndFromTheLeftPanel() {
        assertFalse(shouldOfferUsageAccess(hasTrackedSession = false, granted = false, alreadyOffered = false, dialogOpen = false))
        assertFalse(shouldOfferUsageAccess(hasTrackedSession = true, granted = true, alreadyOffered = false, dialogOpen = false))
        assertFalse(shouldOfferUsageAccess(hasTrackedSession = true, granted = false, alreadyOffered = true, dialogOpen = false))
        assertFalse(shouldOfferUsageAccess(hasTrackedSession = true, granted = false, alreadyOffered = false, dialogOpen = true))
        assertTrue(shouldOfferUsageAccess(hasTrackedSession = true, granted = false, alreadyOffered = false, dialogOpen = false))
        val prompt = usageAccessPrompt()
        assertEquals(DialogKind.UsageAccess, prompt.kind)
        assertEquals(DialogButton.NotNow, prompt.buttons[prompt.safeIndex])
        assertNull(PickerModel(count = 1, rowsPerPage = 1, showLaunchTarget = false).dialog)
        val opened = PickerModel(count = 1, rowsPerPage = 1, showLaunchTarget = false).onSetting(Row.UsageAccess)
        val asking = reduce(opened, Meaning.Activate).first
        assertEquals(DialogKind.UsageAccess, asking.dialog?.kind)
        assertEquals("Play time  Approximate", rowLabel(Row.UsageAccess, opened))
        assertEquals("Play time  All launchers", rowLabel(Row.UsageAccess, opened.copy(usageGranted = true)))
    }

    @Test
    fun orderRowCyclesRecentlyPlayed() {
        val opened = PickerModel(count = 1, rowsPerPage = 1, showLaunchTarget = false).onSetting(Row.Order)
        val next = reduce(opened, Meaning.Activate).first
        assertEquals(LibrarySort.RecentlyPlayed, next.sort)
        assertEquals("Order  Recently played", rowLabel(Row.Order, next))
        val back = reduce(next, Meaning.Activate).first
        assertEquals(LibrarySort.Listed, back.sort)
        assertEquals("Order  Library", orderLabel(back.sort))
    }

    @Test
    fun recentlyPlayedOrderIsWhatTheGridShows() {
        val sorted = PickerModel(
            count = 3,
            rowsPerPage = 1,
            showLaunchTarget = false,
            sort = LibrarySort.RecentlyPlayed,
            recentFirst = listOf(2, 0, 1),
        )
        assertEquals(listOf(2, 0, 1), displayOrder(sorted))
        val arranging = sorted.copy(arranging = true, order = listOf(1, 0, 2))
        assertEquals(listOf(1, 0, 2), displayOrder(arranging))
        assertEquals(listOf(0, 1, 2), displayOrder(sorted.copy(sort = LibrarySort.Listed, order = emptyList())))
    }
}
