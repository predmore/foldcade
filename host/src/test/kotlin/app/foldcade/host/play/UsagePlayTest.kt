package app.foldcade.host.play

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsagePlayTest {
    @Test
    fun resumedAndPausedCountOnlyTheForegroundStretch() {
        val spans = usageSpans(
            listOf(
                UsageSample("org.game", UsageMove.Resumed, 1_000),
                UsageSample("org.game", UsageMove.Paused, 4_000),
                UsageSample("org.game", UsageMove.Resumed, 10_000),
                UsageSample("org.game", UsageMove.Paused, 11_000),
            ),
        )
        val play = packagePlay(spans, nowMillis = 20_000)["org.game"]
        assertEquals(4_000L, play?.activeMillis)
        assertEquals(11_000L, play?.lastPlayedMillis)
    }

    @Test
    fun playOutsideFoldcadeIsStillASession() {
        val spans = usageSpans(
            listOf(
                UsageSample("org.game", UsageMove.Resumed, 5_000),
                UsageSample("org.game", UsageMove.Paused, 8_000),
            ),
        )
        val shown = shownPlay(
            lifecycleActive = 0L,
            lifecycleLast = null,
            usage = packagePlay(spans, nowMillis = 9_000)["org.game"],
            granted = true,
        )
        assertEquals(3_000L, shown.activeMillis)
        assertEquals(8_000L, shown.lastPlayedMillis)
        assertFalse(shown.approximate)
    }

    @Test
    fun aSecondResumeDoesNotRestartTheStretch() {
        val spans = usageSpans(
            listOf(
                UsageSample("org.game", UsageMove.Resumed, 1_000),
                UsageSample("org.game", UsageMove.Resumed, 3_000),
                UsageSample("org.game", UsageMove.Paused, 6_000),
            ),
        )
        assertEquals(1, spans.size)
        assertEquals(1_000L, spans.single().resumedAt)
        assertEquals(5_000L, packagePlay(spans, nowMillis = 6_000)["org.game"]?.activeMillis)
    }

    @Test
    fun pauseWithoutResumeAndABackwardGapAddNothing() {
        assertTrue(usageSpans(listOf(UsageSample("org.game", UsageMove.Paused, 2_000))).isEmpty())
        val backward = packagePlay(
            listOf(UsageSpan("org.game", resumedAt = 5_000, pausedAt = 4_000)),
            nowMillis = 9_000,
        )
        assertEquals(0L, backward["org.game"]?.activeMillis)
    }

    @Test
    fun packagesStaySeparate() {
        val spans = usageSpans(
            listOf(
                UsageSample("org.game", UsageMove.Resumed, 1_000),
                UsageSample("org.other", UsageMove.Resumed, 1_000),
                UsageSample("org.other", UsageMove.Paused, 9_000),
                UsageSample("org.game", UsageMove.Paused, 2_000),
            ),
        )
        val plays = packagePlay(spans, nowMillis = 9_000)
        assertEquals(1_000L, plays["org.game"]?.activeMillis)
        assertEquals(8_000L, plays["org.other"]?.activeMillis)
    }

    @Test
    fun anOpenStretchRunsUntilNow() {
        val spans = usageSpans(listOf(UsageSample("org.game", UsageMove.Resumed, 1_000)))
        assertNull(spans.single().pausedAt)
        assertEquals(2_000L, packagePlay(spans, nowMillis = 3_000)["org.game"]?.activeMillis)
    }

    @Test
    fun withoutAccessLifecycleTimeStaysApproximate() {
        val shown = shownPlay(
            lifecycleActive = 6_000L,
            lifecycleLast = 6_000L,
            usage = PackagePlay("org.game", 99_000L, 99_000L),
            granted = false,
        )
        assertEquals(6_000L, shown.activeMillis)
        assertEquals(6_000L, shown.lastPlayedMillis)
        assertTrue(shown.approximate)
    }

    @Test
    fun accessWithNoEventsKeepsLifecycleTime() {
        val shown = shownPlay(4_000L, 4_000L, usage = null, granted = true)
        assertEquals(4_000L, shown.activeMillis)
        assertFalse(shown.approximate)
    }
}
