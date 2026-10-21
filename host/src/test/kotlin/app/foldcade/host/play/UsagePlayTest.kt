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
    fun aClosedLidDoesNotCountForTheGameStillInFront() {
        val spans = usageSpans(
            listOf(
                UsageSample("org.game", UsageMove.Resumed, 1_000),
                UsageSample("android", UsageMove.ScreenOff, 4_000),
                UsageSample("android", UsageMove.ScreenOn, 3_604_000),
                UsageSample("org.game", UsageMove.Paused, 3_606_000),
            ),
        )
        assertEquals(5_000L, packagePlay(spans, nowMillis = 3_700_000)["org.game"]?.activeMillis)
    }

    @Test
    fun aGameStillAsleepAtNowStopsAtTheScreenTurningOff() {
        val spans = usageSpans(
            listOf(
                UsageSample("org.game", UsageMove.Resumed, 1_000),
                UsageSample("android", UsageMove.ScreenOff, 4_000),
            ),
        )
        val play = packagePlay(spans, nowMillis = 3_600_000)["org.game"]
        assertEquals(3_000L, play?.activeMillis)
        assertEquals(4_000L, play?.lastPlayedMillis)
    }

    @Test
    fun aGameThatLeftWhileTheScreenWasOffDoesNotComeBack() {
        val spans = usageSpans(
            listOf(
                UsageSample("org.game", UsageMove.Resumed, 1_000),
                UsageSample("android", UsageMove.ScreenOff, 4_000),
                UsageSample("org.game", UsageMove.Paused, 5_000),
                UsageSample("android", UsageMove.ScreenOn, 9_000),
            ),
        )
        assertEquals(3_000L, packagePlay(spans, nowMillis = 20_000)["org.game"]?.activeMillis)
    }

    @Test
    fun aGameThatResumedWhileTheScreenWasOffStartsWhenItTurnsOn() {
        val spans = usageSpans(
            listOf(
                UsageSample("android", UsageMove.ScreenOff, 1_000),
                UsageSample("org.game", UsageMove.Resumed, 2_000),
                UsageSample("android", UsageMove.ScreenOn, 8_000),
                UsageSample("org.game", UsageMove.Paused, 10_000),
            ),
        )
        assertEquals(2_000L, packagePlay(spans, nowMillis = 20_000)["org.game"]?.activeMillis)
    }

    @Test
    fun aForwardJumpThatStaysInThePastIsCapped() {
        val start = 1_000L
        val jump = start + MAX_USAGE_STRETCH_MS + 3_600_000L
        val spans = listOf(UsageSpan("org.game", resumedAt = start, pausedAt = jump))
        val play = packagePlay(spans, nowMillis = jump + 1_000L)["org.game"]
        assertEquals(MAX_USAGE_STRETCH_MS, play?.activeMillis)
        val sitting = packagePlay(
            listOf(UsageSpan("org.game", resumedAt = start, pausedAt = start + 2 * 3_600_000L)),
            nowMillis = start + 3 * 3_600_000L,
        )["org.game"]
        assertEquals(2 * 3_600_000L, sitting?.activeMillis)
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

    @Test
    fun aFuturePauseDoesNotInflateTimeOrStayLastPlayed() {
        val spans = listOf(
            UsageSpan("org.game", resumedAt = 1_000, pausedAt = 4_000),
            UsageSpan("org.game", resumedAt = 5_000, pausedAt = 5_000_000),
        )
        val play = packagePlay(spans, nowMillis = 9_000)["org.game"]
        assertEquals(7_000L, play?.activeMillis)
        assertEquals(4_000L, play?.lastPlayedMillis)
    }

    @Test
    fun aRemovedStretchComesOutOfItsPackagesUsageTime() {
        val spans = listOf(
            UsageSpan("org.game", 0L, 10_000L),
            UsageSpan("org.game", 20_000L, null),
            UsageSpan("org.other", 0L, 10_000L),
        )
        val cut = withoutStretches(spans, mapOf("org.game" to listOf(2_000L..4_000L, 22_000L..23_000L)), nowMillis = 30_000L)
        val play = packagePlay(cut, nowMillis = 30_000L)
        // 10s less 2s, and the open 10s less 1s; another package keeps all its time.
        assertEquals(17_000L, play["org.game"]?.activeMillis)
        assertEquals(30_000L, play["org.game"]?.lastPlayedMillis)
        assertEquals(10_000L, play["org.other"]?.activeMillis)
        // A stretch cut whole is gone, and with it its last play.
        val gone = packagePlay(withoutStretches(listOf(UsageSpan("org.game", 0L, 10_000L)), mapOf("org.game" to listOf(0L..10_000L)), 30_000L), 30_000L)
        assertNull(gone["org.game"])
    }
}
