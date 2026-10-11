package app.foldcade

import app.foldcade.api.plugin.DeviceCodeChallenge
import app.foldcade.api.plugin.DeviceCodePoll
import kotlinx.coroutines.delay

/**
 * Shows [begin]'s challenge, then polls until the server approves, denies, or the code expires.
 * [DeviceCodePoll.SlowDown] waits five seconds longer, which is what that answer means.
 */
internal suspend fun pollDeviceCode(
    begin: suspend () -> DeviceCodeChallenge,
    poll: suspend (String) -> DeviceCodePoll,
    onChallenge: suspend (DeviceCodeChallenge) -> Unit,
    wait: suspend (Long) -> Unit = { delay(it) },
    now: () -> Long = { System.nanoTime() },
): DeviceCodePoll {
    val challenge = begin()
    onChallenge(challenge)
    var interval = challenge.intervalSeconds.coerceAtLeast(1)
    val deadline = now() + challenge.expiresInSeconds.coerceAtLeast(1) * 1_000_000_000L
    while (true) {
        wait(interval * 1_000L)
        if (now() > deadline) return DeviceCodePoll.Expired
        when (val result = poll(challenge.deviceCode)) {
            DeviceCodePoll.Pending -> Unit
            DeviceCodePoll.SlowDown -> interval += 5
            DeviceCodePoll.Denied, DeviceCodePoll.Expired, is DeviceCodePoll.Approved -> return result
        }
    }
}
