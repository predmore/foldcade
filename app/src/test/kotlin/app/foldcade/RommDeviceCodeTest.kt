package app.foldcade

import app.foldcade.api.plugin.DeviceCodeChallenge
import app.foldcade.api.plugin.DeviceCodePoll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RommDeviceCodeTest {
    @Test
    fun pollingWaitsThenReturnsTheApprovedToken() = runBlocking {
        val waits = mutableListOf<Long>()
        var now = 0L
        val polls = ArrayDeque(listOf(DeviceCodePoll.Pending, DeviceCodePoll.SlowDown, DeviceCodePoll.Approved("rmm_secret")))
        val outcome = pollDeviceCode(
            begin = {
                DeviceCodeChallenge("dev", "WDJB", "https://host/pair", intervalSeconds = 5, expiresInSeconds = 60)
            },
            poll = { polls.removeFirst() },
            onChallenge = { assertEquals("WDJB", it.userCode) },
            wait = { waits += it },
            now = { now },
        )
        assertEquals(DeviceCodePoll.Approved("rmm_secret"), outcome)
        assertEquals(listOf(5_000L, 5_000L, 10_000L), waits)
        assertTrue(polls.isEmpty())
    }
}
