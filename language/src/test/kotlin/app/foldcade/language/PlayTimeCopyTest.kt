package app.foldcade.language

import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
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
        val us = DateStyle()
        assertEquals("Last played  —", lastPlayedLine(null, now, zone, us))
        assertEquals("Last played  Today", lastPlayedLine(Instant.parse("2026-10-09T01:00:00Z").toEpochMilli(), now, zone, us))
        assertEquals("Last played  Yesterday", lastPlayedLine(Instant.parse("2026-10-08T23:00:00Z").toEpochMilli(), now, zone, us))
        assertEquals("Last played  Mar 2", lastPlayedLine(Instant.parse("2026-03-02T12:00:00Z").toEpochMilli(), now, zone, us))
        assertEquals("Last played  Oct 9, 2025", lastPlayedLine(Instant.parse("2025-10-09T12:00:00Z").toEpochMilli(), now, zone, us))
    }

    @Test
    fun lastPlayedLineFollowsADayFirstLocale() {
        val zone = ZoneOffset.UTC
        val now = Instant.parse("2026-10-09T15:00:00Z").toEpochMilli()
        // What Android's best patterns give for en-GB.
        val gb = DateStyle(Locale.UK, monthDayPattern = "d MMM", monthDayYearPattern = "d MMM y", is24Hour = true)
        assertEquals("Last played  2 Mar", lastPlayedLine(Instant.parse("2026-03-02T12:00:00Z").toEpochMilli(), now, zone, gb))
        assertEquals("Last played  9 Oct 2025", lastPlayedLine(Instant.parse("2025-10-09T12:00:00Z").toEpochMilli(), now, zone, gb))
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
        assertNull(PickerModel(count = 1, rowsPerPage = 1).dialog)
        val opened = PickerModel(count = 1, rowsPerPage = 1).onSetting(Row.UsageAccess)
        val asking = reduce(opened, Meaning.Activate).first
        assertEquals(DialogKind.UsageAccess, asking.dialog?.kind)
        assertEquals("Play time  Foldcade only", rowLabel(Row.UsageAccess, opened))
        assertEquals("Play time  Everywhere", rowLabel(Row.UsageAccess, opened.copy(usageGranted = true)))
    }

    @Test
    fun anArrangementIsWhatTheGridShows() {
        val listed = PickerModel(count = 3, rowsPerPage = 1)
        assertEquals(listOf(0, 1, 2), displayOrder(listed))
        assertEquals(listOf(1, 0, 2), displayOrder(listed.copy(arranging = true, order = listOf(1, 0, 2))))
    }

    @Test
    fun heroPlayLineIsOneLineOrNothing() {
        val zone = java.time.ZoneOffset.UTC
        val now = 1_760_000_000_000L
        val us = DateStyle()
        assertNull(heroPlayLine(0L, null, now, zone, us))
        assertEquals("Played 1m · Today", heroPlayLine(61_000L, now, now, zone, us))
        assertEquals("Played 2h 5m · Yesterday", heroPlayLine(125 * 60_000L, now - 86_400_000L, now, zone, us))
        // 1_760_000_000_000 is 9 Oct 2025; a week before reads in the device's order.
        assertEquals("Played 1m · Oct 2", heroPlayLine(61_000L, now - 7 * 86_400_000L, now, zone, us))
    }
}
