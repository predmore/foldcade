package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchTarget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LaunchPreparationTest {
    private val local = Game("folder", "disc", "playstation", Availability.LocalOnly, "Disc")
    private val owner = local.copy(backendId = "romm", remoteKey = "12")
    private val target = LaunchTarget.ContentUri("content://disc")

    @Test fun aNegotiatedOwnerSuppliesFirmwareEvenWhenAnotherCopyPlays() = runBlocking {
        val work = LaunchWork()
        val steps = mutableListOf<String>()
        val prepared = PreparedLaunch(target, emptyList(), owner)
        assertSame(prepared, work.prepareFiles(work.begin(), local,
            saves = { steps += "saves"; prepared },
            firmware = { offered, backendId ->
                assertSame(target, offered)
                assertEquals(owner.backendId, backendId)
                steps += "firmware"
                true
            },
        ))
        assertEquals(listOf("saves", "firmware"), steps)
    }

    @Test fun aSaveFailureStopsBeforeFirmwareAndAFirmwareFailureStopsBeforeHandoff() = runBlocking {
        val work = LaunchWork()
        val failure = SaveNotPlaced()
        assertSame(failure, runCatching {
            work.prepareFiles(work.begin(), local,
                saves = { throw failure },
                firmware = { _, _ -> error("failed save must stop first") },
            )
        }.exceptionOrNull())
        var passes = 0
        assertNull(work.prepareFiles(work.begin(), local,
            saves = { PreparedLaunch(target, emptyList()) },
            firmware = { _, backendId ->
                assertEquals(local.backendId, backendId)
                passes++
                false
            },
        ))
        assertEquals(1, passes)
    }

    @Test fun cancellationBetweenSavesAndFirmwareNeverPlacesABios() = runBlocking {
        val work = LaunchWork()
        val ticket = work.begin()
        assertNull(work.prepareFiles(ticket, local,
            saves = { work.cancel(); PreparedLaunch(target, emptyList(), owner) },
            firmware = { _, _ -> error("cancelled launch must not place firmware") },
        ))
        assertNull(work.prepareFiles(ticket, local,
            saves = { error("stale launch must not prepare saves") },
            firmware = { _, _ -> error("stale launch must not place firmware") },
        ))
    }
}
