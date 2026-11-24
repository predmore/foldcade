package app.foldcade

import app.foldcade.language.*
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class UpdateStoreTest {
    @Test fun upgradesReadStateNotesAndReadyDismissalsSurviveAnotherProcess() {
        val directory = Files.createTempDirectory("updates-test").toFile()
        try {
            val file = directory.resolve("ledger.json")
            val store = UpdateStore(file)
            store.observe("app.foldcade.debug", "Foldcade Debug", 1, "one", 100)
            assertTrue(store.unread().isEmpty())
            store.observe("app.foldcade.debug", "Foldcade Debug", 2, "two", 200)
            val update = store.unread().single()
            store.keepNotes(update.packageName, listOf(ReleaseNotes(2, "two", "New stuff")), rangeKey(update))
            store.hideReady("plugin", 3)
            store.checked(400)
            val reopened = UpdateStore(file)
            assertEquals(update, reopened.unread().single())
            assertEquals("New stuff", reopened.cached(update)!!.single().body)
            assertEquals(400L, reopened.lastAutomaticCheck)
            assertTrue(reopened.readyHidden("plugin", 3))
            assertFalse(reopened.readyHidden("plugin", 4))
            reopened.read(update.packageName)
            assertTrue(UpdateStore(file).unread().isEmpty())
            assertEquals(1L, reopened.update(update.packageName, "Foldcade Debug", false)!!.fromCode)
        } finally { directory.deleteRecursively() }
    }

    @Test fun aRestartWithinAnHourWaitsAndClockChangesNeverCreateAHugeDelay() {
        assertEquals(30_000L, automaticStartupDelay(10_000, 0))
        assertEquals(3_500_000L, automaticStartupDelay(200_000, 100_000))
        assertEquals(30_000L, automaticStartupDelay(4_000_000, 100_000))
        assertEquals(30_000L, automaticStartupDelay(50_000, 100_000))
    }
}
