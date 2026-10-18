package app.foldcade

import app.foldcade.host.play.PlayAway
import app.foldcade.host.play.PlayClock
import app.foldcade.host.play.PlayKind
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPlayTest {
    @Test
    fun aFailedLaunchEndsTheSessionAndDoesNotRememberTheDisplay() {
        val plays = plays()
        val external = ExternalPlay()
        val failure = runCatching {
            external.open(plays, "nintendo-ds.melonds", "Game", 2) { error("missing") }
        }
        assertTrue(failure.exceptionOrNull() is IllegalStateException)
        assertNull(external.sessionId)
        assertNull(external.displayId)
        assertEquals(listOf(PlayKind.Started, PlayKind.Ended), plays.events().map { it.kind })
        assertEquals("nintendo-ds.melonds", plays.events().first().gameId)
        external.endIfDisplayHome(plays, 2)
        assertEquals(2, plays.events().size)
    }

    @Test
    fun aCancelledLaunchEndsTheSession() {
        val plays = plays()
        val external = ExternalPlay()
        val failure = runCatching {
            external.open(plays, "nintendo-3ds.azahar", "Game", 2) { throw CancellationException("stopped") }
        }
        assertTrue(failure.exceptionOrNull() is CancellationException)
        assertEquals(listOf(PlayKind.Started, PlayKind.Ended), plays.events().map { it.kind })
        assertNull(external.sessionId)
    }

    @Test
    fun theSessionStaysOpenUntilTheLaunchDisplayIsHome() {
        val plays = plays()
        val external = ExternalPlay()
        external.open(plays, "nintendo-ds.melonds", "Game", 2) {}
        assertEquals(listOf(PlayKind.Started), plays.events().map { it.kind })
        external.endIfDisplayHome(plays, 1)
        assertEquals(1, plays.events().size)
        external.endIfDisplayHome(plays, 2)
        assertEquals(listOf(PlayKind.Started, PlayKind.Ended), plays.events().map { it.kind })
        assertEquals("nintendo-ds.melonds", plays.events().last().gameId)
        external.endIfDisplayHome(plays, 2)
        assertEquals(2, plays.events().size)
    }

    @Test
    fun aSecondLaunchEndsTheSessionStillOpen() {
        val plays = plays()
        val external = ExternalPlay()
        external.open(plays, "nintendo-3ds.azahar", "Game", 2) {}
        external.open(plays, "nintendo-ds.melonds", "Game", 2) {}
        assertEquals(
            listOf(PlayKind.Started, PlayKind.Ended, PlayKind.Started),
            plays.events().map { it.kind },
        )
        assertEquals("nintendo-ds.melonds", plays.events().last().gameId)
    }

    @Test
    fun timeWithTheLaunchDisplayOffDoesNotCount() {
        var now = 0L
        val plays = plays(PlayClock { now })
        val external = ExternalPlay()
        external.open(plays, "nintendo-3ds.azahar", "Game", 2) {}
        now = 5_000L
        external.displayOn(plays, false)
        now = 3_605_000L
        external.displayOn(plays, false)
        external.displayOn(plays, true)
        external.displayOn(plays, true)
        now = 3_607_000L
        external.endIfDisplayHome(plays, 2)
        assertEquals(
            listOf(PlayKind.Started, PlayKind.Backgrounded, PlayKind.Resumed, PlayKind.Ended),
            plays.events().map { it.kind },
        )
        assertEquals(PlayAway.Sleep, plays.events()[1].away)
        assertEquals(7_000L, plays.totals("nintendo-3ds.azahar").activeMillis)
    }

    @Test
    fun theDisplayTurningOffWithNoSessionOpenRecordsNothing() {
        val plays = plays()
        val external = ExternalPlay()
        external.displayOn(plays, false)
        external.displayOn(plays, true)
        assertTrue(plays.events().isEmpty())
    }

    private fun plays(clock: PlayClock? = null): PlaySessions {
        val dir = Files.createTempDirectory("external-play")
        val file = File(dir.toFile(), "events.log")
        return if (clock == null) PlaySessions.open(file) else PlaySessions.open(file, clock)
    }
}
