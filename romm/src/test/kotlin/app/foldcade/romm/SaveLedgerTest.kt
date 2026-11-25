package app.foldcade.romm

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class SaveLedgerTest {
    @Test fun acceptedBytesHaveAReceiptTimeAndOldLedgersHaveNoInventedTime() {
        val dir = Files.createTempDirectory("save-ledger")
        val file = dir.resolve("ledger.json")
        Files.writeString(file, """{"slots":{"sav":{"synced_hash":"old","emulator":"azahar"}}}""")
        val ledger = SaveLedger(file)
        assertNull(ledger.slot("sav")!!.syncedAtMillis)
        val before = System.currentTimeMillis()
        ledger.markSynced("sav", "new", null)
        val receipt = SaveLedger(file).slot("sav")!!
        assertEquals("new", receipt.syncedHash)
        assertEquals("azahar", receipt.emulator)
        assertTrue(receipt.syncedAtMillis!! >= before)
        assertTrue(Files.readString(file).contains("synced_at"))
        ledger.update("sav") { it.copy(archivedHash = "kept") }
        assertEquals(receipt.syncedAtMillis, ledger.slot("sav")!!.syncedAtMillis)
    }
}
