package app.foldcade

import app.foldcade.host.play.PlayAway
import app.foldcade.host.play.PlayEvent
import app.foldcade.host.play.PlayKind
import app.foldcade.language.PlaySpan
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaySpansTest {
    @Test
    fun eachStretchInFrontLastsAsLongAsTheActiveTimeItGained() {
        val events = listOf(
            PlayEvent("s1", "game", PlayKind.Started, 1_000L, null, 0L),
            // Ten minutes in front, then the device slept for an hour.
            PlayEvent("s1", "game", PlayKind.Backgrounded, 601_000L, PlayAway.Sleep, 600_000L),
            PlayEvent("s1", "game", PlayKind.Resumed, 4_201_000L, null, 600_000L),
            PlayEvent("s1", "game", PlayKind.Ended, 4_501_000L, null, 900_000L),
            // A session still in front has no stretch yet.
            PlayEvent("s2", "other", PlayKind.Started, 5_000_000L, null, 0L),
        )
        assertEquals(
            listOf(
                PlaySpan("game", 1_000L, 601_000L, "s1"),
                PlaySpan("game", 4_201_000L, 4_501_000L, "s1"),
            ),
            playSpans(events),
        )
    }

    @Test
    fun aRemovedSessionHasNoStretchesAndTheOthersKeepTheirs() {
        val events = listOf(
            PlayEvent("night", "game", PlayKind.Started, 1_000L, null, 0L),
            PlayEvent("night", "game", PlayKind.Ended, 54_001_000L, null, 54_000_000L),
            PlayEvent("short", "game", PlayKind.Started, 60_000_000L, null, 0L),
            PlayEvent("short", "game", PlayKind.Ended, 60_600_000L, null, 600_000L),
            PlayEvent("night", "game", PlayKind.Removed, 61_000_000L, null, 0L),
        )
        assertEquals(listOf(PlaySpan("game", 60_000_000L, 60_600_000L, "short")), playSpans(events))
        // Usage stats saw the removed night too, from when it came to the front to when it left.
        assertEquals(listOf("game" to (1_000L..54_001_000L)), removedStretches(events))
    }
}
