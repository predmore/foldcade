package app.foldcade

import app.foldcade.host.play.PlayAway
import app.foldcade.host.play.PlayClock
import app.foldcade.host.play.PlayKind
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun aPlayerExitAfterTheStartEndsTheSession() {
        val plays = plays()
        val external = ExternalPlay()
        external.open(plays, "nintendo-ds.melonds", "Game", 2, packageName = "com.example.player", nowMillis = 1_000L) {}
        assertEquals("com.example.player", external.packageName)
        assertEquals(1_000L, external.startedAtMillis)
        assertFalse(playerDiedAfterStart(1_000L, listOf(999L)))
        assertTrue(playerDiedAfterStart(1_000L, listOf(500L, 1_000L)))
        external.endBecausePlayerGone(plays)
        assertNull(external.sessionId)
        assertNull(external.packageName)
        assertNull(external.startedAtMillis)
        assertEquals(listOf(PlayKind.Started, PlayKind.Ended), plays.events().map { it.kind })
        external.endBecausePlayerGone(plays)
        assertEquals(2, plays.events().size)
    }

    @Test
    fun aRestartIgnoresTheExitThatSwitchedGames() {
        val external = ExternalPlay()
        external.open(plays(), "nintendo-ds.melonds", "Game", 2, packageName = "com.retroarch", nowMillis = 1_000L) {}
        external.notePlayerRestarted(5_000L)
        assertFalse(playerDiedAfterStart(external.startedAtMillis!!, listOf(2_000L)))
        assertTrue(playerDiedAfterStart(external.startedAtMillis!!, listOf(5_000L)))
    }

    @Test
    fun aFailedLaunchDoesNotRememberThePlayer() {
        val external = ExternalPlay()
        runCatching {
            external.open(plays(), "nintendo-ds.melonds", "Game", 2, packageName = "com.example.player", nowMillis = 1_000L) {
                error("missing")
            }
        }
        assertNull(external.packageName)
        assertNull(external.startedAtMillis)
    }

    @Test
    fun foldcadesOwnPackageIsNotWatchedAndARestartWindowIsNotACrash() {
        assertFalse(watchPlayerPackage(null, "app.foldcade"))
        assertFalse(watchPlayerPackage("app.foldcade", "app.foldcade"))
        assertFalse(watchPlayerPackage("  ", "app.foldcade"))
        assertTrue(watchPlayerPackage("com.retroarch", "app.foldcade"))
        assertTrue(restartStillExpected(1_000L, 4_000L))
        assertFalse(restartStillExpected(4_001L, 4_000L))
        assertFalse(restartStillExpected(1_000L, null))
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
