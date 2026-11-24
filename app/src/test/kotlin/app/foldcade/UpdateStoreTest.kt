package app.foldcade

import app.foldcade.language.*
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class UpdateStoreTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @After fun stopWriter() { scope.cancel() }

    @Test fun upgradesReadStateNotesAndReadyDismissalsSurviveAnotherProcess() = runBlocking {
        val directory = Files.createTempDirectory("updates-test").toFile()
        try {
            val file = directory.resolve("ledger.json")
            val store = UpdateStore(file, scope)
            store.observe("app.foldcade.debug", "Foldcade Debug", 1, "one", 100)
            assertTrue(store.unread().isEmpty())
            store.observe("app.foldcade.debug", "Foldcade Debug", 2, "two", 200)
            val update = store.unread().single()
            store.keepNotes(update.packageName, listOf(ReleaseNotes(2, "two", "New stuff")), rangeKey(update))
            store.hideReady("plugin", 3)
            store.checked(400)
            store.flush()
            val reopened = UpdateStore(file, scope)
            assertEquals(update, reopened.unread().single())
            assertEquals("New stuff", reopened.cached(update)!!.single().body)
            assertEquals(400L, reopened.lastAutomaticCheck)
            assertTrue(reopened.readyHidden("plugin", 3))
            assertFalse(reopened.readyHidden("plugin", 4))
            reopened.read(update.packageName)
            reopened.flush()
            assertTrue(UpdateStore(file, scope).unread().isEmpty())
            assertEquals(1L, reopened.update(update.packageName, "Foldcade Debug")!!.fromCode)
        } finally { directory.deleteRecursively() }
    }

    @Test fun everyNotesEntryPointUsesTheMergedUnreadRangeUntilItIsRead() = runBlocking {
        val directory = Files.createTempDirectory("updates-range").toFile()
        try {
            val store = UpdateStore(directory.resolve("ledger.json"), scope)
            for (pkg in listOf("app.foldcade", "app.foldcade.moonlight")) {
                for (code in listOf(10L, 11L, 13L)) store.observe(pkg, pkg, code, "$code", code)
                assertEquals(10L, store.update(pkg, pkg)!!.fromCode)
                store.read(pkg)
                assertEquals(11L, store.update(pkg, pkg)!!.fromCode)
            }
            store.flush()
        } finally { directory.deleteRecursively() }
    }

    @Test fun clearingNoticesWritesTheSmallLedgerAndLeavesTheNotesCacheAlone() = runBlocking {
        val directory = Files.createTempDirectory("updates-cache").toFile()
        try {
            val file = directory.resolve("ledger.json")
            val store = UpdateStore(file, scope)
            for (pkg in listOf("self", "plugin")) {
                store.observe(pkg, pkg, 1, "one", 1)
                store.observe(pkg, pkg, 2, "two", 2)
                store.keepNotes(pkg, listOf(ReleaseNotes(2, "two", "notes".repeat(100_000))))
            }
            store.flush()
            assertFalse(JSONObject(file.readText()).has("notes"))
            val cache = directory.resolve("notes.json")
            val contents = cache.readText()
            assertTrue(cache.setLastModified(1_000))
            store.dismiss(setOf("self", "plugin"), mapOf("plugin" to 3L))
            assertTrue(store.unread().isEmpty())
            assertTrue(store.readyHidden("plugin", 3))
            store.flush()
            assertEquals(1_000L, cache.lastModified())
            assertEquals(contents, cache.readText())
            assertTrue(UpdateStore(file, scope).unread().isEmpty())
        } finally { directory.deleteRecursively() }
    }

    @Test fun oldCombinedLedgersMigrateTheirNotesWithoutLosingUnreadHistory() = runBlocking {
        val directory = Files.createTempDirectory("updates-migration").toFile()
        try {
            val file = directory.resolve("ledger.json")
            file.writeText("""{"versions":{"self":{"code":2,"name":"two"}},"unread":[{"package":"self","label":"Self","fromCode":1,"fromName":"one","toCode":2,"toName":"two","atMillis":3}],"notes":{"self":[{"code":2,"name":"two","body":"Offline notes"}]},"fetched":["self:1:2"]}""")
            val store = UpdateStore(file, scope)
            assertEquals("Offline notes", store.cached(store.unread().single())!!.single().body)
            store.flush()
            assertFalse(JSONObject(file.readText()).has("notes"))
            val reopened = UpdateStore(file, scope)
            assertEquals("Offline notes", reopened.cached(reopened.unread().single())!!.single().body)
        } finally { directory.deleteRecursively() }
    }

    @Test fun failedMetadataWritesKeepTheLauncherStateUsableInMemory() = runBlocking {
        val directory = Files.createTempDirectory("updates-no-space").toFile()
        try {
            val blocked = directory.resolve("blocked").apply { writeText("a file cannot be a directory") }
            val store = UpdateStore(blocked.resolve("ledger.json"), scope)
            store.observe("self", "Self", 1, "one", 1)
            store.observe("self", "Self", 2, "two", 2)
            val update = store.unread().single()
            store.keepNotes("self", listOf(ReleaseNotes(2, "two", "Still available")), rangeKey(update))
            store.flush() // Both durable writes fail with IOException.
            assertEquals(update, store.unread().single())
            assertEquals("Still available", store.cached(update)!!.single().body)
            store.dismiss(setOf("self"), mapOf("plugin" to 2L))
            store.checked(3)
            store.flush()
            assertTrue(store.unread().isEmpty())
            assertTrue(store.readyHidden("plugin", 2))
            assertEquals(3L, store.lastAutomaticCheck)
        } finally { directory.deleteRecursively() }
    }

    @Test fun aRestartWithinAnHourWaitsAndClockChangesNeverCreateAHugeDelay() {
        assertEquals(30_000L, automaticStartupDelay(10_000, 0))
        assertEquals(3_500_000L, automaticStartupDelay(200_000, 100_000))
        assertEquals(30_000L, automaticStartupDelay(4_000_000, 100_000))
        assertEquals(30_000L, automaticStartupDelay(50_000, 100_000))
    }
}
