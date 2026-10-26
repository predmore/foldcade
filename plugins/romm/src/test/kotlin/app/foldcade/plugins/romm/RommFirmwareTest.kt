package app.foldcade.plugins.romm

import app.foldcade.romm.FirmwareSummary
import app.foldcade.romm.PlatformSummary
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RommFirmwareTest {
    @Test
    fun switchIsMatchedBySlugOrByFsSlug() = runBlocking {
        val root = Files.createTempDirectory("romm-firmware-switch")
        try {
            val bySlug = ScriptedOps().apply {
                onPlatforms = { listOf(platform(3, "3ds"), platform(18, "Switch", fsSlug = "nx")) }
                onFirmware = { id ->
                    assertEquals(18L, id)
                    listOf(FirmwareSummary(4, 18, "prod.keys", 8, null))
                }
            }
            val byFs = ScriptedOps().apply {
                onPlatforms = { listOf(platform(9, "nintendo-switch", fsSlug = "switch")) }
                onFirmware = { id ->
                    assertEquals(9L, id)
                    listOf(FirmwareSummary(5, 9, "Firmware.23.0.1.zip", 40, null))
                }
            }
            for ((origin, ops, expected) in listOf(
                Triple("https://slug.example", bySlug, FirmwareSummary(4, 18, "prod.keys", 8, null)),
                Triple("https://fs.example", byFs, FirmwareSummary(5, 9, "Firmware.23.0.1.zip", 40, null)),
            )) {
                val listed = RommFirmware { wiring(root, origin, ops) }.listSwitch()
                assertEquals(RommSwitchFirmware(origin, listOf(expected), false), listed)
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun noSwitchPlatformIsAnEmptyAnswer() = runBlocking {
        val root = Files.createTempDirectory("romm-firmware-none")
        try {
            var firmwareCalls = 0
            val ops = ScriptedOps().apply {
                onPlatforms = { listOf(platform(3, "3ds")) }
                onFirmware = {
                    firmwareCalls++
                    emptyList()
                }
            }
            val listed = RommFirmware { wiring(root, "https://romm.example", ops) }.listSwitch()
            assertEquals(RommSwitchFirmware("https://romm.example", emptyList(), failed = false), listed)
            assertEquals(0, firmwareCalls)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun noTokenIsAFailureAndDoesNotOpen() = runBlocking {
        val root = Files.createTempDirectory("romm-firmware-token")
        try {
            val opened = mutableListOf<String>()
            for (token in listOf(null, "  ")) {
                val listed = RommFirmware { wiring(root, "https://romm.example", ScriptedOps(), token = token, opened = opened) }.listSwitch()
                assertEquals(RommSwitchFirmware("https://romm.example", emptyList(), failed = true), listed)
            }
            assertTrue(opened.isEmpty())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun aPlatformsFailureIsReported() = runBlocking {
        val root = Files.createTempDirectory("romm-firmware-platforms")
        try {
            var closed = false
            val ops = ScriptedOps().apply {
                onPlatforms = { error("platforms down") }
                onClose = { closed = true }
            }
            val listed = RommFirmware { wiring(root, "https://romm.example", ops) }.listSwitch()
            assertEquals(RommSwitchFirmware("https://romm.example", emptyList(), failed = true), listed)
            assertTrue(closed)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun aFirmwareFailureIsReported() = runBlocking {
        val root = Files.createTempDirectory("romm-firmware-list")
        try {
            var closed = false
            val ops = ScriptedOps().apply {
                onPlatforms = { listOf(platform(18, "switch")) }
                onFirmware = { error("firmware down") }
                onClose = { closed = true }
            }
            val listed = RommFirmware { wiring(root, "https://romm.example", ops) }.listSwitch()
            assertEquals(RommSwitchFirmware("https://romm.example", emptyList(), failed = true), listed)
            assertTrue(closed)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun aBlankOriginIsSkipped() = runBlocking {
        val root = Files.createTempDirectory("romm-firmware-blank")
        try {
            val opened = mutableListOf<String>()
            val listed = RommFirmware { wiring(root, "  ", ScriptedOps(), opened = opened) }.listSwitch()
            assertEquals(null, listed)
            assertTrue(opened.isEmpty())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun downloadGoesToTheGivenOriginAndRefusesAnUnknownOne() = runBlocking {
        val root = Files.createTempDirectory("romm-firmware-download")
        try {
            val opened = mutableListOf<String>()
            val calls = mutableListOf<Long>()
            val ops = ScriptedOps().apply {
                onDownloadFirmware = { file, target ->
                    calls += file.id
                    assertEquals("prod.keys", file.fileName)
                    assertEquals(12L, file.fileSizeBytes)
                    assertEquals("abc", file.md5Hash)
                    Files.createDirectories(target.parent)
                    Files.write(target, "keys".toByteArray())
                    target
                }
            }
            val firmware = RommFirmware { wiring(root, "https://two.example", ops, opened = opened) }
            val target = root.resolve("out/prod.keys")
            val keys = FirmwareSummary(4, 18, "prod.keys", 12, "abc")
            val saved = firmware.download("https://two.example", keys, target)
            assertEquals(target, saved)
            assertEquals("keys", Files.readString(target))
            assertEquals(listOf("https://two.example"), opened)
            assertEquals(listOf(4L), calls)
            val missing = runCatching {
                firmware.download("https://missing.example", keys, target)
            }.exceptionOrNull()
            assertTrue(missing is IllegalStateException)
            assertEquals("RomM server is not connected", missing?.message)
            assertEquals(listOf("https://two.example"), opened)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}

private fun wiring(
    root: Path,
    origin: String,
    ops: RommOps,
    token: String? = "rmm_test",
    opened: MutableList<String>? = null,
): RommWiring = RommWiring(
    origin,
    RommTokenSource { token },
    root,
    root.resolve("data"),
).apply {
    open = { server, _ ->
        opened?.add(server)
        ops
    }
}

private fun platform(id: Long, slug: String, fsSlug: String = slug) = PlatformSummary(
    id = id,
    name = slug,
    displayName = slug,
    slug = slug,
    fsSlug = fsSlug,
    abbreviation = slug,
    alternativeNames = emptyList(),
    romCount = 0,
)
