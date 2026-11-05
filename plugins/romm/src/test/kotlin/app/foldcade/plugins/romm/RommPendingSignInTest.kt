package app.foldcade.plugins.romm

import app.foldcade.api.plugin.DeviceCodePoll
import app.foldcade.romm.DeviceAuthChallenge
import app.foldcade.romm.DeviceTokenPoll
import app.foldcade.romm.RegisteredDevice
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class RommPendingSignInTest {
    @Test fun backingOutOfAnotherServersPairingKeepsTheLivePluginAndSavedDeviceOnTheSavedOrigin() = runBlocking {
        assertUnapprovedAttemptIsIsolated(cancel = true)
    }

    @Test fun expiryOnAnotherServerKeepsTheLivePluginAndSavedDeviceOnTheSavedOrigin() = runBlocking {
        assertUnapprovedAttemptIsIsolated(cancel = false)
    }

    private suspend fun assertUnapprovedAttemptIsIsolated(cancel: Boolean) = coroutineScope {
        val root = Files.createTempDirectory("romm-pending-sign-in")
        val saved = "https://saved.example"
        val unapproved = "http://192.168.1.7"
        // This is the removed-token/restore case that is allowed to begin pairing a different address.
        val supply = RommSupply(root.resolve("cache"), root.resolve("data"), tokenSource = RommTokenSource { null })
        val live = RommLibrary.open { supply }.apply { configure(mapOf(ROMM_ORIGIN_KEY to saved)) }
        val savedWiring = live.connection.wiring()
        savedWiring.rememberedDevice = RegisteredDevice("saved-device", supply.clientVersion)
        val deviceFile = supply.dataRoot.resolve("device.json")
        val before = Files.readString(deviceFile)
        try {
            val attempt = supply.pendingSignIn(unapproved) as RommLibrary
            val polling = CompletableDeferred<Unit>()
            val neverApproved = CompletableDeferred<DeviceTokenPoll>()
            val destinations = mutableListOf<String>()
            val ops = ScriptedOps().apply {
                onBegin = { _, _, _ -> DeviceAuthChallenge("dev", "ABCD1234", "/pair/device", "/pair/device?user_code=ABCD1234", 600, 5) }
                onUrl = { "$unapproved/pair/device?user_code=ABCD1234" }
                onPoll = {
                    polling.complete(Unit)
                    if (cancel) neverApproved.await() else DeviceTokenPoll.Expired
                }
            }
            attempt.connection.wiring().open = { origin, token ->
                destinations += origin
                assertNull(token())
                ops
            }
            val challenge = attempt.beginDeviceCode()
            assertEquals("$unapproved/pair/device?user_code=ABCD1234", challenge.verificationUrl)
            assertEquals(saved, live.connection.wiring().origin)
            val poll = async { attempt.pollDeviceCode(challenge.deviceCode) }
            withTimeout(2_000) { polling.await() }
            if (cancel) {
                poll.cancelAndJoin()
                assertTrue(poll.isCancelled)
            } else {
                assertEquals(DeviceCodePoll.Expired, poll.await())
            }
            assertEquals(listOf(unapproved, unapproved), destinations)
            assertSame(savedWiring, live.connection.wiring())
            assertEquals(saved, live.connection.wiring().origin)
            assertEquals("saved-device", live.connection.wiring().rememberedDevice?.deviceId)
            assertEquals(before, Files.readString(deviceFile))
            assertNull(attempt.connection.wiring().rememberedDevice)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
