package app.foldcade.romm

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class RommTimestampTest {
    @Test fun rommOffsetsAndZuluUseTheSameAndroidCompatibleParser() {
        val expected = parseRommTime("2026-10-01T03:29:49Z")
        assertEquals(expected, parseRommTime("2026-10-01T03:29:49+00:00"))
        assertEquals(expected, parseRommTime("2026-10-01T05:29:49+02:00"))
        assertEquals(expected, parseRommTime("2026-09-30T20:29:49-07:00"))
    }

    @Test fun rommSourcesCannotFallBackToTheStockInstantParser() {
        var root = File(System.getProperty("user.dir"))
        while (!File(root, "settings.gradle.kts").exists()) root = root.parentFile ?: error("Repo root missing")
        File(root, "romm/src/main").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            assertFalse("Use parseRommTime for Android compatibility: $file", file.readText().contains(Regex("\\bInstant\\s*\\.\\s*parse\\s*\\(")))
        }
    }

    @Test fun screenshotAuthenticationIsLimitedToTheInstancesExactContentPath() {
        val origin = "https://romm.example/instance"
        assertTrue(RommClient.isSaveScreenshotUri(origin, "$origin/api/screenshots/9/content"))
        for (uri in listOf("https://romm.example/api/screenshots/9/content", "$origin/api/screenshots/9", "$origin/api/screenshots/9/content/extra", "$origin/api/screenshots%2F9/content", "https://other.example/instance/api/screenshots/9/content")) {
            assertFalse(uri, RommClient.isSaveScreenshotUri(origin, uri))
        }
    }

    @Test fun aNamelessDeviceDoesNotBreakTheSaveList() {
        val save = parseSaves("""[{"id":1,"rom_id":3,"file_name":"save.sav","slot":"sav","emulator":"melonds",
            "content_hash":null,"updated_at":"2026-10-01T03:29:49+00:00","file_size_bytes":10,
            "origin_device_id":"nameless","device_syncs":[{"device_id":"nameless","device_name":null}],"screenshot":null}]""".toByteArray()).single()
        assertNull(save.deviceSyncs.single().deviceName)
        assertEquals(parseRommTime("2026-10-01T03:29:49Z"), save.updatedAt)
    }
}
