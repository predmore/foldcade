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
            val listed = RommFirmware {
                listOf(
                    wiring(root, "https://slug.example", bySlug),
                    wiring(root, "https://fs.example", byFs),
                )
            }.listSwitch()
            assertEquals(
                listOf(
                    RommSwitchFirmware("https://slug.example", listOf(FirmwareSummary(4, 18, "prod.keys", 8, null)), false),
                    RommSwitchFirmware("https://fs.example", listOf(FirmwareSummary(5, 9, "Firmware.23.0.1.zip", 40, null)), false),
                ),
                listed,
            )
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
            val listed = RommFirmware { listOf(wiring(root, "https://romm.example", ops)) }.listSwitch()
            assertEquals(listOf(RommSwitchFirmware("https://romm.example", emptyList(), failed = false)), listed)
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
            val listed = RommFirmware {
                listOf(
                    wiring(root, "https://none.example", ScriptedOps(), token = null, opened = opened),
                    wiring(root, "https://blank.example", ScriptedOps(), token = "  ", opened = opened),
                )
            }.listSwitch()
            assertEquals(
                listOf(
                    RommSwitchFirmware("https://none.example", emptyList(), failed = true),
                    RommSwitchFirmware("https://blank.example", emptyList(), failed = true),
                ),
                listed,
            )
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
            val listed = RommFirmware { listOf(wiring(root, "https://romm.example", ops)) }.listSwitch()
            assertEquals(listOf(RommSwitchFirmware("https://romm.example", emptyList(), failed = true)), listed)
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
            val listed = RommFirmware { listOf(wiring(root, "https://romm.example", ops)) }.listSwitch()
            assertEquals(listOf(RommSwitchFirmware("https://romm.example", emptyList(), failed = true)), listed)
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
            val real = ScriptedOps().apply {
                onPlatforms = { listOf(platform(18, "switch")) }
                onFirmware = { listOf(FirmwareSummary(4, 18, "prod.keys", 8, null)) }
            }
            val listed = RommFirmware {
                listOf(
                    wiring(root, "  ", ScriptedOps(), opened = opened),
                    wiring(root, "https://romm.example", real, opened = opened),
                )
            }.listSwitch()
            assertEquals(
                listOf(RommSwitchFirmware("https://romm.example", listOf(FirmwareSummary(4, 18, "prod.keys", 8, null)), false)),
                listed,
            )
            assertEquals(listOf("https://romm.example"), opened)
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
                onDownloadFirmware = { id, name, target, size, md5, _ ->
                    calls += id
                    assertEquals("prod.keys", name)
                    assertEquals(12L, size)
                    assertEquals("abc", md5)
                    Files.createDirectories(target.parent)
                    Files.write(target, "keys".toByteArray())
                    target
                }
            }
            val other = ScriptedOps().apply {
                onDownloadFirmware = { _, _, _, _, _, _ -> error("wrong server") }
            }
            val firmware = RommFirmware {
                listOf(
                    wiring(root, "https://one.example", other, opened = opened),
                    wiring(root, "https://two.example", ops, opened = opened),
                )
            }
            val target = root.resolve("out/prod.keys")
            val saved = firmware.download("https://two.example", 4, "prod.keys", target, 12, "abc")
            assertEquals(target, saved)
            assertEquals("keys", Files.readString(target))
            assertEquals(listOf("https://two.example"), opened)
            assertEquals(listOf(4L), calls)
            val missing = runCatching {
                firmware.download("https://missing.example", 4, "prod.keys", target, 12, "abc")
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
