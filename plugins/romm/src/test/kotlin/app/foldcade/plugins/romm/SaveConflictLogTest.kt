package app.foldcade.plugins.romm

import app.foldcade.api.plugin.SaveConflictChoice
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveConflictLogTest {
    @Test
    fun keepLocalWritesThatCopyAndLeavesTheOther() {
        val fixture = fixture()
        val log = SaveConflictLog(fixture.log)
        assertTrue(log.record(fixture.entry))
        assertTrue(log.resolve(fixture.entry.id, SaveConflictChoice.KeepLocal))
        assertArrayEquals("local".toByteArray(), Files.readAllBytes(fixture.slot))
        assertBothCopies(fixture)
        assertTrue(log.pending().isEmpty())
    }

    @Test
    fun keepRemoteWritesThatCopyAndLeavesTheOther() {
        val fixture = fixture()
        val log = SaveConflictLog(fixture.log)
        log.record(fixture.entry)
        assertTrue(log.resolve(fixture.entry.id, SaveConflictChoice.KeepRemote))
        assertArrayEquals("remote".toByteArray(), Files.readAllBytes(fixture.slot))
        assertBothCopies(fixture)
        assertTrue(log.pending().isEmpty())
    }

    @Test
    fun keepBothLeavesTheSlotAndBothCopies() {
        val fixture = fixture()
        val log = SaveConflictLog(fixture.log)
        log.record(fixture.entry)
        Files.write(fixture.slot, "remote".toByteArray())
        assertTrue(log.resolve(fixture.entry.id, SaveConflictChoice.KeepBoth))
        assertArrayEquals("remote".toByteArray(), Files.readAllBytes(fixture.slot))
        assertBothCopies(fixture)
        assertTrue(log.pending().isEmpty())
        assertEquals(ConflictStatus.Closed, log.find(fixture.entry.id)?.status)
    }

    @Test
    fun decideLaterKeepsBothAndAsksAgainOnTheNextLaunch() {
        val fixture = fixture()
        val log = SaveConflictLog(fixture.log)
        log.record(fixture.entry)
        Files.write(fixture.slot, "remote".toByteArray())
        assertTrue(log.resolve(fixture.entry.id, SaveConflictChoice.DecideLater))
        assertArrayEquals("remote".toByteArray(), Files.readAllBytes(fixture.slot))
        assertBothCopies(fixture)
        assertTrue(log.pending().isEmpty())
        assertEquals(ConflictStatus.Deferred, log.find(fixture.entry.id)?.status)
        assertTrue(log.reopenDeferred())
        assertEquals(listOf(fixture.entry.id), log.pending().map { it.id })
        assertBothCopies(fixture)
    }

    @Test
    fun theSameBytesAfterKeepLocalRestoreTheChosenCopy() {
        val fixture = fixture()
        val log = SaveConflictLog(fixture.log)
        log.record(fixture.entry)
        log.resolve(fixture.entry.id, SaveConflictChoice.KeepLocal)
        Files.write(fixture.slot, "remote".toByteArray())
        assertFalse(log.record(fixture.entry))
        assertArrayEquals("local".toByteArray(), Files.readAllBytes(fixture.slot))
        assertBothCopies(fixture)
        assertTrue(log.pending().isEmpty())
    }

    private fun assertBothCopies(fixture: Fixture) {
        assertArrayEquals("local".toByteArray(), Files.readAllBytes(fixture.local))
        assertArrayEquals("remote".toByteArray(), Files.readAllBytes(fixture.remote))
    }

    private fun fixture(): Fixture {
        val root = Files.createTempDirectory("save-conflict")
        val local = root.resolve("local.sav")
        val remote = root.resolve("remote.sav")
        val slot = root.resolve("slot.sav")
        Files.write(local, "local".toByteArray())
        Files.write(remote, "remote".toByteArray())
        Files.write(slot, "remote".toByteArray())
        val entry = ConflictEntry(
            id = "1234:autosave",
            romId = 1234,
            slot = "autosave",
            gameLabel = "Bravely Second",
            localSource = "This device · Azahar",
            localUpdatedAtMillis = 1_713_438_000_000,
            localSizeBytes = 5,
            localHash = "local-hash",
            remoteSource = "Steam Deck · Azahar",
            remoteUpdatedAtMillis = 1_713_441_600_000,
            remoteSizeBytes = 6,
            remoteHash = "remote-hash",
            localCopy = local.toString(),
            remoteCopy = remote.toString(),
            slotFile = slot.toString(),
            status = ConflictStatus.Open,
            choice = null,
        )
        return Fixture(root.resolve("conflicts.json"), local, remote, slot, entry)
    }

    private class Fixture(
        val log: java.nio.file.Path,
        val local: java.nio.file.Path,
        val remote: java.nio.file.Path,
        val slot: java.nio.file.Path,
        val entry: ConflictEntry,
    )
}
