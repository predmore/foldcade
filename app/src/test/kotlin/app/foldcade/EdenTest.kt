package app.foldcade

import app.foldcade.plugins.emulators.EDEN_PACKAGES
import app.foldcade.plugins.emulators.EmulatorsEntry
import app.foldcade.plugins.romm.RommSwitchFirmware
import app.foldcade.romm.FirmwareSummary
import app.foldcade.romm.RomProgress
import app.foldcade.romm.RommFirmwareMismatch
import app.foldcade.romm.RommResponseException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EdenTest {
    private val eden = EmulatorsEntry().players.single { it.id == "eden.nintendo-switch" }

    @Test
    fun theGateAsksRommOnlyWhenItMightOffer() = runBlocking {
        var calls = 0
        val ask = suspend {
            calls++
            true
        }
        assertEquals(EdenGate.Check, edenGate(holdsGrant = true, offered = false, ask))
        assertEquals(EdenGate.Ready(markOffered = false), edenGate(holdsGrant = false, offered = true, ask))
        assertEquals(0, calls)
        assertEquals(EdenGate.Offer, edenGate(holdsGrant = false, offered = false, ask))
        assertEquals(EdenGate.Ready(markOffered = false), edenGate(holdsGrant = false, offered = false) { false })
        assertEquals(EdenGate.Ready(markOffered = false), edenGate(holdsGrant = false, offered = false) { null })
        val failed = edenGate(holdsGrant = false, offered = false) { error("romm") }
        assertEquals(EdenGate.Ready(markOffered = false), failed)
        val cancelled = runCatching {
            edenGate(holdsGrant = false, offered = false) { throw CancellationException("stop") }
        }.exceptionOrNull()
        assertTrue(cancelled is CancellationException)
        assertEquals(1, calls)
    }

    @Test
    fun aRestartStaysUntilEdenIsStoppedOrTheDeviceReboots() {
        assertTrue(edenRestartStillNeeded(pendingBoot = 4, currentBoot = 4, edenStopped = false))
        assertFalse(edenRestartStillNeeded(pendingBoot = 4, currentBoot = 5, edenStopped = false))
        assertFalse(edenRestartStillNeeded(pendingBoot = 5, currentBoot = 4, edenStopped = false))
        assertFalse(edenRestartStillNeeded(pendingBoot = 4, currentBoot = 4, edenStopped = true))
        assertFalse(edenRestartStillNeeded(pendingBoot = 4, currentBoot = 9, edenStopped = true))
        assertTrue(edenRestartStillNeeded(pendingBoot = -1, currentBoot = 5, edenStopped = false))
        assertTrue(edenRestartStillNeeded(pendingBoot = 4, currentBoot = -1, edenStopped = false))
        assertTrue(edenRestartStillNeeded(pendingBoot = -1, currentBoot = -1, edenStopped = false))
        assertFalse(edenRestartStillNeeded(pendingBoot = -1, currentBoot = 5, edenStopped = true))
        assertFalse(edenRestartStillNeeded(pendingBoot = 4, currentBoot = -1, edenStopped = true))
        assertFalse(edenRestartStillNeeded(pendingBoot = -1, currentBoot = -1, edenStopped = true))
    }

    @Test
    fun rommErrorsAreNotAnOfferAndAReachableFileIs() {
        val keys = file(1, "prod.keys")
        val title = file(2, "TITLE.KEYS")
        val zip = file(3, "Firmware.1.0.zip")
        val other = file(4, "readme.txt")
        assertEquals(false, rommHasSwitchSetup(emptyList()))
        assertNull(rommHasSwitchSetup(listOf(romm("a", failed = true))))
        assertEquals(
            false,
            rommHasSwitchSetup(listOf(romm("a", failed = true), romm("b", other))),
        )
        assertEquals(true, rommHasSwitchSetup(listOf(romm("a", keys))))
        assertEquals(false, rommHasSwitchSetup(listOf(romm("a", title))))
        assertEquals(true, rommHasSwitchSetup(listOf(romm("a", zip))))
        assertEquals(false, rommHasSwitchSetup(listOf(romm("a", other))))
        assertEquals(
            true,
            rommHasSwitchSetup(listOf(romm("a", failed = true), romm("b", keys))),
        )
    }

    @Test
    fun firmwareChoiceFollowsTheVersionThenTheId() {
        val older = file(1, "Firmware.1.2.zip")
        val newer = file(2, "Firmware.1.10.zip")
        val tieLow = file(3, "Firmware.2.0.zip")
        val tieHigh = file(8, "Firmware.2.0.ZIP")
        val plainLow = file(3, "a.zip")
        val plainHigh = file(8, "b.zip")
        val versioned = file(1, "Firmware.1.0.zip")
        val unversioned = file(9, "notes.zip")
        assertEquals(newer, chooseFirmwareZip(listOf(older, newer)))
        assertEquals(tieHigh, chooseFirmwareZip(listOf(tieLow, tieHigh)))
        assertEquals(plainHigh, chooseFirmwareZip(listOf(plainLow, plainHigh, file(4, "prod.keys"))))
        assertEquals(versioned, chooseFirmwareZip(listOf(unversioned, versioned)))
        assertEquals(
            file(8, "Firmware.1.2.0.zip"),
            chooseFirmwareZip(listOf(file(1, "Firmware.1.2.zip"), file(8, "Firmware.1.2.0.zip"))),
        )
        assertNull(chooseFirmwareZip(listOf(file(1, "prod.keys"))))
        assertNull(chooseFirmwareZip(emptyList()))
    }

    @Test
    fun aRefusedFirmwareKeyFollowsTheMd5OrTheSize() {
        val hashed = file(4, "Firmware.1.0.zip", 10, md5 = "AbC")
        val otherHash = file(4, "Firmware.1.0.zip", 10, md5 = "def")
        val sized = file(4, "Firmware.1.0.zip", 10)
        val resized = file(4, "Firmware.1.0.zip", 11)
        assertEquals("https://romm\t4\tabc", refusedFirmwareKey("https://romm", hashed))
        assertEquals("https://romm\t4\tsize:10", refusedFirmwareKey("https://romm", sized))
        assertEquals("https://romm\t4\tsize:10", refusedFirmwareKey("https://romm", file(4, "Firmware.1.0.zip", 10, md5 = "")))
        assertNotEquals(refusedFirmwareKey("https://romm", hashed), refusedFirmwareKey("https://romm", otherHash))
        assertNotEquals(refusedFirmwareKey("https://romm", sized), refusedFirmwareKey("https://romm", resized))
        assertNotEquals(refusedFirmwareKey("https://romm", hashed), refusedFirmwareKey("https://other", hashed))
    }

    @Test
    fun keyNamesMatchIgnoringCaseAndTakeTheHighestId() {
        val low = file(2, "PROD.KEYS")
        val high = file(5, "prod.keys")
        assertEquals(high, chooseKeyFile(listOf(low, high, file(9, "title.keys")), "prod.keys"))
        assertEquals(file(9, "Title.Keys"), chooseKeyFile(listOf(file(1, "prod.keys"), file(9, "Title.Keys")), "title.keys"))
        assertNull(chooseKeyFile(listOf(file(1, "readme.txt")), "prod.keys"))
    }

    @Test
    fun aFirmwareZipIsOnlyTopLevelNcaFiles() {
        assertTrue(firmwareZipOk(listOf(FirmwareZipEntry("a.nca", false), FirmwareZipEntry("B.NCA", false))))
        assertFalse(firmwareZipOk(emptyList()))
        assertFalse(firmwareZipOk(listOf(FirmwareZipEntry("dir/a.nca", false))))
        assertFalse(firmwareZipOk(listOf(FirmwareZipEntry("dir/", true))))
        assertFalse(firmwareZipOk(listOf(FirmwareZipEntry("a.nca", true))))
        assertFalse(firmwareZipOk(listOf(FirmwareZipEntry("readme.txt", false))))
        assertFalse(firmwareZipOk(listOf(FirmwareZipEntry("../a.nca", false))))
        assertFalse(firmwareZipOk(listOf(FirmwareZipEntry("a\\b.nca", false))))
        assertFalse(firmwareZipOk(listOf(FirmwareZipEntry("a.nca/", false))))
    }

    @Test
    fun missingMeansAnEmptyKeysFileOrAPartialFirmwareMarker() {
        val full = EdenInventory(4, 4, listOf("a.nca"), partialMarker = false)
        assertFalse(edenGaps(full).any)
        val emptyKeys = EdenInventory(0, null, listOf("a.nca"), partialMarker = false)
        assertTrue(edenGaps(emptyKeys).prodKeys)
        assertTrue(edenGaps(emptyKeys).titleKeys)
        assertFalse(edenGaps(emptyKeys).firmware)
        val partial = EdenInventory(4, 4, listOf("a.nca"), partialMarker = true)
        assertTrue(edenGaps(partial).firmware)
        assertFalse(edenGaps(partial).prodKeys)
        val noNca = EdenInventory(4, 4, listOf("readme.txt"), partialMarker = false)
        assertTrue(edenGaps(noNca).firmware)
    }

    @Test
    fun theInstalledEdenPackageIsTheFirstOnePresent() {
        assertEquals(EDEN_PACKAGES, eden.packageNames)
        assertEquals("dev.eden.eden_emulator", installedEdenPackage(eden) { it == "dev.eden.eden_emulator" })
        assertEquals("dev.legacy.eden_emulator", installedEdenPackage(eden) { it == "dev.legacy.eden_emulator" })
        assertEquals("dev.eden.eden_emulator", installedEdenPackage(eden) { true })
        assertNull(installedEdenPackage(eden) { false })
        val dolphin = EmulatorsEntry().players.first { it.id.startsWith("dolphin") }
        assertNull(installedEdenPackage(dolphin) { true })
    }

    @Test
    fun keysAreAddedOnceAndABadCopyIsRemoved() = runBlocking {
        val tree = MemoryEden()
        tree.files["saves/user.sav"] = "leave".toByteArray()
        val keys = "prod-bytes".toByteArray()
        val title = "title-bytes".toByteArray()
        val source = FakeFiles(
            listOf(
                server("https://down", failed = true),
                server("https://romm", file(4, "PROD.KEYS", keys.size), file(7, "title.keys", title.size)),
            ),
            mapOf(4L to keys, 7L to title),
        )
        val warnings = mutableListOf<String>()
        val cache = Files.createTempDirectory("eden-keys")
        try {
            installer(tree, source, cache, warnings).ensure(PKG)
            assertTrue(keys.contentEquals(tree.files["keys/prod.keys"]))
            assertTrue(title.contentEquals(tree.files["keys/title.keys"]))
            assertTrue(tree.files.keys.none { it.endsWith(".foldcade-partial") })
            assertEquals(listOf(4L, 7L), source.asked)
            assertCacheEmpty(cache)
            // A non-empty file is left alone, including one written above.
            source.asked.clear()
            installer(tree, source, cache, warnings).ensure(PKG)
            assertTrue(source.asked.isEmpty())
            assertTrue("leave".toByteArray().contentEquals(tree.files["saves/user.sav"]))
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun anEmptyKeysFileIsReplacedAndAMismatchIsDeleted() = runBlocking {
        val tree = MemoryEden()
        tree.files["keys/prod.keys"] = ByteArray(0)
        val keys = "fresh".toByteArray()
        val source = FakeFiles(listOf(server("https://romm", file(3, "prod.keys", keys.size))), mapOf(3L to keys))
        val cache = Files.createTempDirectory("eden-empty-key")
        try {
            installer(tree, source, cache, mutableListOf()).ensure(PKG)
            assertTrue(keys.contentEquals(tree.files["keys/prod.keys"]))
            assertFalse(tree.files.containsKey("keys/prod.keys.foldcade-partial"))
            tree.readOverride = { path -> if (path == "keys/prod.keys.foldcade-partial") byteArrayOf(9) else null }
            tree.files.remove("keys/prod.keys")
            val warnings = mutableListOf<String>()
            installer(tree, source, cache, warnings).ensure(PKG)
            assertFalse(tree.files.containsKey("keys/prod.keys"))
            assertFalse(tree.files.containsKey("keys/prod.keys.foldcade-partial"))
            assertTrue(warnings.any { it.contains("did not match") })
            assertCacheEmpty(cache)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun aDownloadFailureLeavesNoKeysFile() = runBlocking {
        val tree = MemoryEden()
        val source = FakeFiles(
            listOf(server("https://romm", file(3, "prod.keys", 4))),
            emptyMap(),
            fail = setOf(3),
        )
        val warnings = mutableListOf<String>()
        val cache = Files.createTempDirectory("eden-key-fail")
        try {
            installer(tree, source, cache, warnings).ensure(PKG)
            assertFalse(tree.files.containsKey("keys/prod.keys"))
            assertTrue(warnings.isNotEmpty())
            assertCacheEmpty(cache)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun aKeyIsRenamedIntoPlace() = runBlocking {
        val tree = MemoryEden()
        val keys = "prod-bytes".toByteArray()
        val source = FakeFiles(listOf(server("https://romm", file(4, "prod.keys", keys.size))), mapOf(4L to keys))
        val cache = Files.createTempDirectory("eden-key-rename")
        try {
            installer(tree, source, cache, mutableListOf()).ensure(PKG)
            assertEquals(setOf("keys/prod.keys"), tree.files.keys)
            assertTrue(keys.contentEquals(tree.files["keys/prod.keys"]))
            assertCacheEmpty(cache)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun aFailedKeyWriteLeavesNoProdKeys() = runBlocking {
        val tree = MemoryEden()
        tree.failWrite = "keys/prod.keys.foldcade-partial"
        val keys = "prod-bytes".toByteArray()
        val source = FakeFiles(listOf(server("https://romm", file(4, "prod.keys", keys.size))), mapOf(4L to keys))
        val warnings = mutableListOf<String>()
        val cache = Files.createTempDirectory("eden-key-write-fail")
        try {
            installer(tree, source, cache, warnings).ensure(PKG)
            assertFalse(tree.files.containsKey("keys/prod.keys"))
            assertFalse(tree.files.containsKey("keys/prod.keys.foldcade-partial"))
            assertTrue(warnings.isNotEmpty())
            assertCacheEmpty(cache)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun aLeftoverKeyTempIsReplaced() = runBlocking {
        val tree = MemoryEden()
        tree.files["keys/prod.keys.foldcade-partial"] = "stale".toByteArray()
        val keys = "prod-bytes".toByteArray()
        val source = FakeFiles(listOf(server("https://romm", file(4, "prod.keys", keys.size))), mapOf(4L to keys))
        val cache = Files.createTempDirectory("eden-key-leftover")
        try {
            installer(tree, source, cache, mutableListOf()).ensure(PKG)
            assertEquals(setOf("keys/prod.keys"), tree.files.keys)
            assertTrue(keys.contentEquals(tree.files["keys/prod.keys"]))
            assertCacheEmpty(cache)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun aFailedKeyRenameLeavesNoProdKeys() = runBlocking {
        val tree = MemoryEden()
        tree.files["keys/prod.keys"] = ByteArray(0)
        tree.failRename = "keys/prod.keys.foldcade-partial"
        val keys = "prod-bytes".toByteArray()
        val source = FakeFiles(listOf(server("https://romm", file(4, "prod.keys", keys.size))), mapOf(4L to keys))
        val warnings = mutableListOf<String>()
        val cache = Files.createTempDirectory("eden-key-rename-fail")
        try {
            installer(tree, source, cache, warnings).ensure(PKG)
            assertFalse(tree.files.containsKey("keys/prod.keys"))
            assertFalse(tree.files.containsKey("keys/prod.keys.foldcade-partial"))
            assertTrue(warnings.isNotEmpty())
            assertCacheEmpty(cache)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun firmwareIsInstalledFromTheNewestZipAndTheCacheIsRemoved() = runBlocking {
        val tree = MemoryEden()
        tree.files["keys/prod.keys"] = "keep".toByteArray()
        tree.files["keys/title.keys"] = "keep".toByteArray()
        val nca = byteArrayOf(1, 2, 3, 4)
        val older = zipBytes("old.nca" to byteArrayOf(9))
        val newer = zipBytes("new.nca" to nca)
        val source = FakeFiles(
            listOf(server("https://romm", file(1, "Firmware.1.2.zip", older.size), file(2, "Firmware.1.10.zip", newer.size))),
            mapOf(1L to older, 2L to newer),
        )
        val progress = mutableListOf<RomProgress>()
        val cache = Files.createTempDirectory("eden-fw")
        try {
            installer(tree, source, cache, mutableListOf()).ensure(PKG, onProgress = { progress += it })
            assertEquals(listOf(2L), source.asked)
            assertTrue(nca.contentEquals(tree.files["nand/system/Contents/registered/new.nca"]))
            assertFalse(tree.files.containsKey("nand/system/Contents/foldcade-firmware.partial"))
            assertTrue(progress.any { it is RomProgress.Receiving })
            assertCacheEmpty(cache)
            assertTrue("keep".toByteArray().contentEquals(tree.files["keys/prod.keys"]))
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun firmwareProgressIsReportedBeforeTheDownloadIsCalled() = runBlocking {
        val tree = MemoryEden()
        tree.files["keys/prod.keys"] = "keep".toByteArray()
        tree.files["keys/title.keys"] = "keep".toByteArray()
        val zip = zipBytes("new.nca" to byteArrayOf(1, 2, 3, 4))
        val source = FakeFiles(
            listOf(server("https://romm", file(2, "Firmware.1.10.zip", zip.size))),
            mapOf(2L to zip),
        )
        val seen = mutableListOf<RomProgress>()
        var atCall = listOf<RomProgress>()
        source.beforeDownload = { atCall = seen.toList() }
        val cache = Files.createTempDirectory("eden-progress-first")
        try {
            installer(tree, source, cache, mutableListOf()).ensure(PKG, onProgress = { seen += it })
            assertEquals(listOf(RomProgress.Receiving(0, zip.size.toLong())), atCall)
            assertEquals(listOf(2L), source.asked)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun aPartialInstallIsReplacedAndAFailedCopyRemovesWhatItWrote() = runBlocking {
        val tree = MemoryEden()
        tree.files["keys/prod.keys"] = "keep".toByteArray()
        tree.files["keys/title.keys"] = "keep".toByteArray()
        tree.files[MARKER] = ByteArray(0)
        tree.files["$REGISTERED/old.nca"] = byteArrayOf(7)
        val good = zipBytes("new.nca" to byteArrayOf(1, 2))
        val source = FakeFiles(listOf(server("https://romm", file(5, "Firmware.3.0.zip", good.size))), mapOf(5L to good))
        val cache = Files.createTempDirectory("eden-partial")
        try {
            installer(tree, source, cache, mutableListOf()).ensure(PKG)
            assertFalse(tree.files.containsKey("$REGISTERED/old.nca"))
            assertTrue(byteArrayOf(1, 2).contentEquals(tree.files["$REGISTERED/new.nca"]))
            assertFalse(tree.files.containsKey(MARKER))

            tree.files.clear()
            tree.files["keys/prod.keys"] = "keep".toByteArray()
            tree.files["keys/title.keys"] = "keep".toByteArray()
            tree.files[MARKER] = ByteArray(0)
            tree.files["$REGISTERED/old.nca"] = byteArrayOf(7)
            tree.failDelete = "$REGISTERED/old.nca"
            val warnings = mutableListOf<String>()
            installer(tree, source, cache, warnings).ensure(PKG)
            assertTrue(tree.files.containsKey(MARKER))
            assertTrue(byteArrayOf(7).contentEquals(tree.files["$REGISTERED/old.nca"]))
            assertFalse(tree.files.containsKey("$REGISTERED/new.nca"))
            assertTrue(warnings.isNotEmpty())

            tree.failDelete = null
            tree.files.clear()
            tree.files["keys/prod.keys"] = "keep".toByteArray()
            tree.files["keys/title.keys"] = "keep".toByteArray()
            val both = zipBytes("a.nca" to byteArrayOf(1), "b.nca" to byteArrayOf(2))
            val failing = FakeFiles(listOf(server("https://romm", file(6, "Firmware.4.0.zip", both.size))), mapOf(6L to both))
            tree.failWrite = "$REGISTERED/b.nca"
            installer(tree, failing, cache, warnings).ensure(PKG)
            assertFalse(tree.files.containsKey("$REGISTERED/a.nca"))
            assertFalse(tree.files.containsKey("$REGISTERED/b.nca"))
            assertFalse(tree.files.containsKey(MARKER))
            assertCacheEmpty(cache)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun aFailedRollbackKeepsTheMarkerAndTheNextEnsureRecovers() = runBlocking {
        val tree = MemoryEden()
        tree.files["keys/prod.keys"] = "keep".toByteArray()
        tree.files["keys/title.keys"] = "keep".toByteArray()
        val both = zipBytes("a.nca" to byteArrayOf(1), "b.nca" to byteArrayOf(2))
        val source = FakeFiles(listOf(server("https://romm", file(6, "Firmware.4.0.zip", both.size))), mapOf(6L to both))
        tree.failWrite = "$REGISTERED/b.nca"
        tree.failDelete = "$REGISTERED/a.nca"
        val warnings = mutableListOf<String>()
        val cache = Files.createTempDirectory("eden-rollback")
        try {
            installer(tree, source, cache, warnings).ensure(PKG)
            assertTrue(tree.files.containsKey(MARKER))
            assertTrue(byteArrayOf(1).contentEquals(tree.files["$REGISTERED/a.nca"]))
            assertFalse(tree.files.containsKey("$REGISTERED/b.nca"))
            assertTrue(warnings.isNotEmpty())

            tree.failWrite = null
            tree.failDelete = null
            installer(tree, source, cache, warnings).ensure(PKG)
            assertFalse(tree.files.containsKey(MARKER))
            assertTrue(byteArrayOf(1).contentEquals(tree.files["$REGISTERED/a.nca"]))
            assertTrue(byteArrayOf(2).contentEquals(tree.files["$REGISTERED/b.nca"]))
            assertCacheEmpty(cache)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun aCancelledRollbackKeepsTheMarkerWhenADeleteFails() = runBlocking {
        val tree = MemoryEden()
        tree.files["keys/prod.keys"] = "keep".toByteArray()
        tree.files["keys/title.keys"] = "keep".toByteArray()
        val both = zipBytes("a.nca" to byteArrayOf(1), "b.nca" to byteArrayOf(2))
        val source = FakeFiles(listOf(server("https://romm", file(6, "Firmware.5.0.zip", both.size))), mapOf(6L to both))
        tree.cancelWrite = "$REGISTERED/b.nca"
        tree.failDelete = "$REGISTERED/a.nca"
        val cache = Files.createTempDirectory("eden-cancel-rollback")
        try {
            val error = runCatching { installer(tree, source, cache, mutableListOf()).ensure(PKG) }.exceptionOrNull()
            assertTrue(error is CancellationException)
            assertTrue(tree.files.containsKey(MARKER))
            assertTrue(byteArrayOf(1).contentEquals(tree.files["$REGISTERED/a.nca"]))
            assertFalse(tree.files.containsKey("$REGISTERED/b.nca"))

            tree.cancelWrite = null
            tree.failDelete = null
            installer(tree, source, cache, mutableListOf()).ensure(PKG)
            assertFalse(tree.files.containsKey(MARKER))
            assertTrue(byteArrayOf(1).contentEquals(tree.files["$REGISTERED/a.nca"]))
            assertTrue(byteArrayOf(2).contentEquals(tree.files["$REGISTERED/b.nca"]))
            assertCacheEmpty(cache)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun aBadZipAndFirmwareAlreadyThereAreNotWritten() = runBlocking {
        val tree = MemoryEden()
        tree.files["keys/prod.keys"] = "keep".toByteArray()
        tree.files["keys/title.keys"] = "keep".toByteArray()
        val nested = zipBytes("dir/a.nca" to byteArrayOf(1))
        val source = FakeFiles(listOf(server("https://romm", file(8, "Firmware.1.0.zip", nested.size))), mapOf(8L to nested))
        val warnings = mutableListOf<String>()
        val cache = Files.createTempDirectory("eden-bad-zip")
        try {
            installer(tree, source, cache, warnings).ensure(PKG)
            assertTrue(warnings.any { it.contains("refused") })
            assertFalse(tree.files.keys.any { it.endsWith(".nca") })
            assertFalse(tree.files.containsKey(MARKER))
            assertCacheEmpty(cache)

            tree.files["$REGISTERED/already.nca"] = byteArrayOf(3)
            source.asked.clear()
            warnings.clear()
            installer(tree, source, cache, warnings).ensure(PKG)
            assertTrue(source.asked.isEmpty())
            assertTrue(byteArrayOf(3).contentEquals(tree.files["$REGISTERED/already.nca"]))
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun firmwareThatArrivesDuringTheDownloadIsLeftAlone() = runBlocking {
        val tree = MemoryEden()
        tree.files["keys/prod.keys"] = "keep".toByteArray()
        tree.files["keys/title.keys"] = "keep".toByteArray()
        val zip = zipBytes("from-zip.nca" to byteArrayOf(1))
        val source = FakeFiles(listOf(server("https://romm", file(2, "Firmware.9.0.zip", zip.size))), mapOf(2L to zip))
        source.beforeWrite = { tree.files["$REGISTERED/already.nca"] = byteArrayOf(5) }
        val cache = Files.createTempDirectory("eden-race")
        try {
            installer(tree, source, cache, mutableListOf()).ensure(PKG)
            assertTrue(byteArrayOf(5).contentEquals(tree.files["$REGISTERED/already.nca"]))
            assertFalse(tree.files.containsKey("$REGISTERED/from-zip.nca"))
            assertFalse(tree.files.containsKey(MARKER))
            assertCacheEmpty(cache)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun aCancelledInstallPropagatesAndRemovesTheTempFile() = runBlocking {
        val tree = MemoryEden()
        val source = FakeFiles(listOf(server("https://romm", file(3, "prod.keys", 4))), emptyMap())
        source.cancel = true
        val cache = Files.createTempDirectory("eden-cancel")
        try {
            val error = runCatching { installer(tree, source, cache, mutableListOf()).ensure(PKG) }.exceptionOrNull()
            assertTrue(error is CancellationException)
            assertFalse(tree.files.containsKey("keys/prod.keys"))
            assertCacheEmpty(cache)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun oneInstallRunsAtATime() = runBlocking {
        val tree = MemoryEden()
        tree.files["keys/title.keys"] = "keep".toByteArray()
        val keys = "one".toByteArray()
        val release = CompletableDeferred<Unit>()
        val entered = AtomicInteger(0)
        val source = FakeFiles(listOf(server("https://romm", file(1, "prod.keys", keys.size))), mapOf(1L to keys))
        source.onDownload = {
            entered.incrementAndGet()
            release.await()
        }
        val cache = Files.createTempDirectory("eden-mutex")
        try {
            val installing = installer(tree, source, cache, mutableListOf())
            val first = async(Dispatchers.IO) { installing.ensure(PKG) }
            while (entered.get() == 0) delay(10)
            val second = async(Dispatchers.IO) { installing.ensure(PKG) }
            delay(50)
            assertEquals(1, entered.get())
            release.complete(Unit)
            first.await()
            second.await()
            assertTrue(keys.contentEquals(tree.files["keys/prod.keys"]))
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun aMissingTitleKeysFileDoesNotContactRomm() = runBlocking {
        val tree = MemoryEden()
        tree.files["keys/prod.keys"] = "keep".toByteArray()
        tree.files["$REGISTERED/already.nca"] = byteArrayOf(3)
        val title = "title".toByteArray()
        val source = FakeFiles(listOf(server("https://romm", file(7, "title.keys", title.size))), mapOf(7L to title))
        val cache = Files.createTempDirectory("eden-title-only")
        try {
            installer(tree, source, cache, mutableListOf()).ensure(PKG)
            assertEquals(0, source.listings)
            assertTrue(source.asked.isEmpty())
            assertFalse(tree.files.containsKey("keys/title.keys"))
            assertTrue("keep".toByteArray().contentEquals(tree.files["keys/prod.keys"]))
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun titleKeysAreFilledWhenFirmwareBringsRommIn() = runBlocking {
        val tree = MemoryEden()
        tree.files["keys/prod.keys"] = "keep".toByteArray()
        val title = "title-bytes".toByteArray()
        val zip = zipBytes("new.nca" to byteArrayOf(1, 2))
        val source = FakeFiles(
            listOf(server("https://romm", file(7, "title.keys", title.size), file(2, "Firmware.1.0.zip", zip.size))),
            mapOf(7L to title, 2L to zip),
        )
        val cache = Files.createTempDirectory("eden-title-with-fw")
        try {
            installer(tree, source, cache, mutableListOf()).ensure(PKG)
            assertEquals(1, source.listings)
            assertTrue(title.contentEquals(tree.files["keys/title.keys"]))
            assertTrue(byteArrayOf(1, 2).contentEquals(tree.files["$REGISTERED/new.nca"]))
            assertCacheEmpty(cache)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun aRefusedFirmwareLayoutIsSkippedForTheNextZip() = runBlocking {
        val tree = edenWithKeys()
        val bad = zipBytes("dir/a.nca" to byteArrayOf(1))
        val good = zipBytes("ok.nca" to byteArrayOf(2, 3))
        val later = zipBytes("later.nca" to byteArrayOf(9))
        val badFile = file(9, "Firmware.2.0.zip", bad.size, md5 = "bad")
        val goodFile = file(3, "Firmware.1.0.zip", good.size, md5 = "good")
        val laterFile = file(1, "Firmware.9.0.zip", later.size, md5 = "later")
        val source = FakeFiles(
            listOf(server("https://romm", badFile, goodFile), server("https://other", laterFile)),
            mapOf(9L to bad, 3L to good, 1L to later),
        )
        val refused = linkedSetOf<String>()
        val warnings = mutableListOf<String>()
        val cache = Files.createTempDirectory("eden-refuse-layout")
        try {
            val installing = installer(tree, source, cache, warnings, refused)
            installing.ensure(PKG)
            assertEquals(listOf(9L), source.asked)
            assertEquals(setOf(refusedFirmwareKey("https://romm", badFile)), refused)
            assertTrue(warnings.any { it.contains("refused") })
            assertFalse(tree.files.keys.any { it.endsWith(".nca") })

            source.asked.clear()
            warnings.clear()
            installing.ensure(PKG)
            assertEquals(listOf(3L), source.asked)
            assertTrue(byteArrayOf(2, 3).contentEquals(tree.files["$REGISTERED/ok.nca"]))
            assertFalse(tree.files.containsKey("$REGISTERED/later.nca"))
            assertEquals(setOf(refusedFirmwareKey("https://romm", badFile)), refused)

            tree.files.remove("$REGISTERED/ok.nca")
            refused += refusedFirmwareKey("https://romm", goodFile)
            source.asked.clear()
            installing.ensure(PKG)
            assertEquals(listOf(1L), source.asked)
            assertTrue(byteArrayOf(9).contentEquals(tree.files["$REGISTERED/later.nca"]))

            tree.files.remove("$REGISTERED/later.nca")
            refused += refusedFirmwareKey("https://other", laterFile)
            source.asked.clear()
            installing.ensure(PKG)
            assertTrue(source.asked.isEmpty())
            assertCacheEmpty(cache)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun anUnreadableFirmwareZipIsSkippedForTheNextZip() = runBlocking {
        val tree = edenWithKeys()
        val broken = "not a zip at all".toByteArray()
        val good = zipBytes("ok.nca" to byteArrayOf(2, 3))
        val brokenFile = file(9, "Firmware.2.0.zip", broken.size, md5 = "broken")
        val goodFile = file(3, "Firmware.1.0.zip", good.size, md5 = "good")
        val source = FakeFiles(
            listOf(server("https://romm", brokenFile, goodFile)),
            mapOf(9L to broken, 3L to good),
        )
        val refused = linkedSetOf<String>()
        val cache = Files.createTempDirectory("eden-refuse-unreadable")
        try {
            val installing = installer(tree, source, cache, mutableListOf(), refused)
            installing.ensure(PKG)
            assertEquals(listOf(9L), source.asked)
            assertEquals(setOf(refusedFirmwareKey("https://romm", brokenFile)), refused)
            assertFalse(tree.files.keys.any { it.endsWith(".nca") })
            assertFalse(tree.files.containsKey(MARKER))

            source.asked.clear()
            installing.ensure(PKG)
            assertEquals(listOf(3L), source.asked)
            assertTrue(byteArrayOf(2, 3).contentEquals(tree.files["$REGISTERED/ok.nca"]))
            assertCacheEmpty(cache)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun aFirmwareHashMismatchIsRemembered() = runBlocking {
        val tree = edenWithKeys()
        val listed = file(4, "Firmware.1.0.zip", 340, md5 = "abc")
        val source = FakeFiles(listOf(server("https://romm", listed)), emptyMap())
        source.downloadError = {
            RommFirmwareMismatch()
        }
        val refused = linkedSetOf<String>()
        val cache = Files.createTempDirectory("eden-refuse-hash")
        try {
            val installing = installer(tree, source, cache, mutableListOf(), refused)
            installing.ensure(PKG)
            assertEquals(listOf(4L), source.asked)
            assertEquals(setOf(refusedFirmwareKey("https://romm", listed)), refused)
            assertCacheEmpty(cache)

            source.asked.clear()
            installing.ensure(PKG)
            assertTrue(source.asked.isEmpty())
            assertEquals(setOf(refusedFirmwareKey("https://romm", listed)), refused)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun aFirmwareNetworkErrorIsRetried() = runBlocking {
        val tree = edenWithKeys()
        val zip = zipBytes("ok.nca" to byteArrayOf(1, 2))
        val listed = file(4, "Firmware.1.0.zip", zip.size, md5 = "abc")
        val source = FakeFiles(listOf(server("https://romm", listed)), mapOf(4L to zip))
        source.downloadError = { IOException("offline") }
        val refused = linkedSetOf<String>()
        val cache = Files.createTempDirectory("eden-fw-network")
        try {
            val installing = installer(tree, source, cache, mutableListOf(), refused)
            installing.ensure(PKG)
            assertEquals(listOf(4L), source.asked)
            assertTrue(refused.isEmpty())
            assertFalse(tree.files.keys.any { it.endsWith(".nca") })

            source.downloadError = null
            source.asked.clear()
            installing.ensure(PKG)
            assertEquals(listOf(4L), source.asked)
            assertTrue(byteArrayOf(1, 2).contentEquals(tree.files["$REGISTERED/ok.nca"]))
            assertTrue(refused.isEmpty())
            assertCacheEmpty(cache)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun anotherFirmwareResponseIsNotRemembered() = runBlocking {
        val tree = edenWithKeys()
        val zip = zipBytes("ok.nca" to byteArrayOf(8))
        val listed = file(4, "Firmware.1.0.zip", zip.size, md5 = "abc")
        val source = FakeFiles(listOf(server("https://romm", listed)), mapOf(4L to zip))
        source.downloadError = { RommResponseException("server returned 206 without a partial file") }
        val refused = linkedSetOf<String>()
        val cache = Files.createTempDirectory("eden-fw-206")
        try {
            val installing = installer(tree, source, cache, mutableListOf(), refused)
            installing.ensure(PKG)
            assertEquals(listOf(4L), source.asked)
            assertTrue(refused.isEmpty())

            source.downloadError = null
            source.asked.clear()
            installing.ensure(PKG)
            assertEquals(listOf(4L), source.asked)
            assertTrue(byteArrayOf(8).contentEquals(tree.files["$REGISTERED/ok.nca"]))
            assertTrue(refused.isEmpty())
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun aRefusedFirmwareZipIsTriedAgainWhenItsMd5Changes() = runBlocking {
        val tree = edenWithKeys()
        val bad = zipBytes("readme.txt" to byteArrayOf(1))
        val good = zipBytes("ok.nca" to byteArrayOf(4))
        val listed = file(4, "Firmware.3.0.zip", bad.size, md5 = "old")
        val source = FakeFiles(listOf(server("https://romm", listed)), mapOf(4L to bad))
        val refused = linkedSetOf<String>()
        val cache = Files.createTempDirectory("eden-refuse-md5")
        try {
            val installing = installer(tree, source, cache, mutableListOf(), refused)
            installing.ensure(PKG)
            assertEquals(listOf(4L), source.asked)
            assertEquals(setOf(refusedFirmwareKey("https://romm", listed)), refused)
            assertFalse(tree.files.keys.any { it.endsWith(".nca") })

            val replaced = listed.copy(md5 = "new", sizeBytes = good.size.toLong())
            source.servers = listOf(server("https://romm", replaced))
            source.bodies = mapOf(4L to good)
            source.asked.clear()
            installing.ensure(PKG)
            assertEquals(listOf(4L), source.asked)
            assertTrue(byteArrayOf(4).contentEquals(tree.files["$REGISTERED/ok.nca"]))
            assertFalse(refused.contains(refusedFirmwareKey("https://romm", replaced)))
            assertCacheEmpty(cache)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun aFirmwareWriteFailureIsNotRemembered() = runBlocking {
        val tree = edenWithKeys()
        val zip = zipBytes("a.nca" to byteArrayOf(1))
        val listed = file(6, "Firmware.4.0.zip", zip.size, md5 = "abc")
        val source = FakeFiles(listOf(server("https://romm", listed)), mapOf(6L to zip))
        tree.failWrite = "$REGISTERED/a.nca"
        val refused = linkedSetOf<String>()
        val cache = Files.createTempDirectory("eden-fw-write")
        try {
            val installing = installer(tree, source, cache, mutableListOf(), refused)
            installing.ensure(PKG)
            assertEquals(listOf(6L), source.asked)
            assertTrue(refused.isEmpty())
            assertFalse(tree.files.containsKey("$REGISTERED/a.nca"))

            tree.failWrite = null
            source.asked.clear()
            installing.ensure(PKG)
            assertEquals(listOf(6L), source.asked)
            assertTrue(byteArrayOf(1).contentEquals(tree.files["$REGISTERED/a.nca"]))
            assertTrue(refused.isEmpty())
            assertCacheEmpty(cache)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun aCancelledFirmwareDownloadIsNotRemembered() = runBlocking {
        val tree = edenWithKeys()
        val zip = zipBytes("a.nca" to byteArrayOf(1))
        val listed = file(4, "Firmware.5.0.zip", zip.size, md5 = "abc")
        val source = FakeFiles(listOf(server("https://romm", listed)), mapOf(4L to zip))
        source.cancel = true
        val refused = linkedSetOf<String>()
        val cache = Files.createTempDirectory("eden-fw-cancel")
        try {
            val installing = installer(tree, source, cache, mutableListOf(), refused)
            val error = runCatching { installing.ensure(PKG) }.exceptionOrNull()
            assertTrue(error is CancellationException)
            assertEquals(listOf(4L), source.asked)
            assertTrue(refused.isEmpty())
            assertCacheEmpty(cache)

            source.cancel = false
            source.asked.clear()
            installing.ensure(PKG)
            assertEquals(listOf(4L), source.asked)
            assertTrue(byteArrayOf(1).contentEquals(tree.files["$REGISTERED/a.nca"]))
            assertTrue(refused.isEmpty())
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun noFolderMeansNothingIsAsked() = runBlocking {
        val source = FakeFiles(listOf(server("https://romm", file(1, "prod.keys", 1))), mapOf(1L to byteArrayOf(1)))
        val cache = Files.createTempDirectory("eden-none")
        try {
            EdenInstaller(openTree = { null }, source = source, cacheDirectory = { cache }).ensure(PKG)
            assertTrue(source.asked.isEmpty())
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun aKeyAddedBeforeACancelIsStillReported() = runBlocking {
        val cache = Files.createTempDirectory("eden-added-cancel")
        try {
            val eden = MemoryEden()
            val keyBytes = "prod-bytes".toByteArray()
            val zip = zipBytes("ok.nca" to byteArrayOf(1))
            val source = FakeFiles(
                listOf(server("https://romm", file(4, "prod.keys", keyBytes.size), file(2, "Firmware.1.0.zip", zip.size))),
                mapOf(4L to keyBytes, 2L to zip),
            )
            // The key lands, then the firmware download is cancelled.
            source.onDownload = { if (source.asked.last() == 2L) source.cancel = true }
            var added = 0
            val error = runCatching {
                installer(eden, source, cache, mutableListOf()).ensure(PKG, onAdded = { added++ })
            }.exceptionOrNull()
            assertTrue(error is CancellationException)
            assertEquals(1, added)
            assertTrue(keyBytes.contentEquals(eden.files["keys/prod.keys"]))
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun ensureReportsWhetherAFileWasAdded() = runBlocking {
        val cache = Files.createTempDirectory("eden-added")
        try {
            val keysOnly = MemoryEden().apply {
                files["keys/title.keys"] = "keep".toByteArray()
                files["$REGISTERED/already.nca"] = byteArrayOf(1)
            }
            val keyBytes = "prod-bytes".toByteArray()
            val keySource = FakeFiles(
                listOf(server("https://romm", file(4, "prod.keys", keyBytes.size))),
                mapOf(4L to keyBytes),
            )
            assertTrue(installer(keysOnly, keySource, cache, mutableListOf()).ensure(PKG).added)
            assertTrue(keyBytes.contentEquals(keysOnly.files["keys/prod.keys"]))

            val firmwareOnly = edenWithKeys()
            val zip = zipBytes("ok.nca" to byteArrayOf(1, 2))
            val firmwareSource = FakeFiles(
                listOf(server("https://romm", file(2, "Firmware.1.0.zip", zip.size))),
                mapOf(2L to zip),
            )
            assertTrue(installer(firmwareOnly, firmwareSource, cache, mutableListOf()).ensure(PKG).added)
            assertTrue(byteArrayOf(1, 2).contentEquals(firmwareOnly.files["$REGISTERED/ok.nca"]))

            val both = MemoryEden()
            val title = "title-bytes".toByteArray()
            val bothZip = zipBytes("both.nca" to byteArrayOf(3))
            val bothSource = FakeFiles(
                listOf(
                    server(
                        "https://romm",
                        file(4, "prod.keys", keyBytes.size),
                        file(7, "title.keys", title.size),
                        file(2, "Firmware.2.0.zip", bothZip.size),
                    ),
                ),
                mapOf(4L to keyBytes, 7L to title, 2L to bothZip),
            )
            assertTrue(installer(both, bothSource, cache, mutableListOf()).ensure(PKG).added)
            assertTrue(keyBytes.contentEquals(both.files["keys/prod.keys"]))
            assertTrue(title.contentEquals(both.files["keys/title.keys"]))
            assertTrue(byteArrayOf(3).contentEquals(both.files["$REGISTERED/both.nca"]))

            val full = MemoryEden().apply {
                files["keys/prod.keys"] = "keep".toByteArray()
                files["keys/title.keys"] = "keep".toByteArray()
                files["$REGISTERED/already.nca"] = byteArrayOf(1)
            }
            val quiet = FakeFiles(listOf(server("https://romm", file(4, "prod.keys", 1))), mapOf(4L to byteArrayOf(1)))
            assertFalse(installer(full, quiet, cache, mutableListOf()).ensure(PKG).added)
            assertEquals(0, quiet.listings)

            val failed = MemoryEden().apply { files["$REGISTERED/already.nca"] = byteArrayOf(1) }
            val failSource = FakeFiles(
                listOf(server("https://romm", file(3, "prod.keys", 4))),
                emptyMap(),
                fail = setOf(3),
            )
            assertFalse(installer(failed, failSource, cache, mutableListOf()).ensure(PKG).added)
            assertFalse(failed.files.containsKey("keys/prod.keys"))

            val refusedTree = edenWithKeys()
            val bad = zipBytes("dir/a.nca" to byteArrayOf(1))
            val badFile = file(9, "Firmware.2.0.zip", bad.size, md5 = "bad")
            val refuseSource = FakeFiles(listOf(server("https://romm", badFile)), mapOf(9L to bad))
            val refused = linkedSetOf<String>()
            assertFalse(installer(refusedTree, refuseSource, cache, mutableListOf(), refused).ensure(PKG).added)
            assertEquals(setOf(refusedFirmwareKey("https://romm", badFile)), refused)
            assertFalse(refusedTree.files.keys.any { it.endsWith(".nca") })

            val mixed = MemoryEden()
            val mixedKeys = "prod-bytes".toByteArray()
            val mixedBad = zipBytes("readme.txt" to byteArrayOf(1))
            val mixedZip = file(9, "Firmware.1.0.zip", mixedBad.size, md5 = "bad")
            val mixedSource = FakeFiles(
                listOf(server("https://romm", file(4, "prod.keys", mixedKeys.size), mixedZip)),
                mapOf(4L to mixedKeys, 9L to mixedBad),
            )
            val mixedRefused = linkedSetOf<String>()
            assertTrue(installer(mixed, mixedSource, cache, mutableListOf(), mixedRefused).ensure(PKG).added)
            assertTrue(mixedKeys.contentEquals(mixed.files["keys/prod.keys"]))
            assertTrue(mixedRefused.isNotEmpty())

            val absent = FakeFiles(listOf(server("https://romm", file(1, "prod.keys", 1))), mapOf(1L to byteArrayOf(1)))
            val none = EdenInstaller(openTree = { null }, source = absent, cacheDirectory = { cache }).ensure(PKG)
            assertFalse(none.added)
            assertTrue(absent.asked.isEmpty())
            assertCacheEmpty(cache)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    private fun installer(
        tree: MemoryEden,
        source: FakeFiles,
        cache: Path,
        warnings: MutableList<String>,
        refused: MutableSet<String> = linkedSetOf(),
    ) = EdenInstaller(
        openTree = { tree },
        source = source,
        cacheDirectory = { cache },
        warn = { message, _ -> warnings += message },
        refusedFirmware = { refused.toSet() },
        rememberRefusedFirmware = { refused += it },
    )

    private fun edenWithKeys(): MemoryEden = MemoryEden().apply {
        files["keys/prod.keys"] = "keep".toByteArray()
        files["keys/title.keys"] = "keep".toByteArray()
    }

    private fun assertCacheEmpty(cache: Path) {
        if (!Files.isDirectory(cache)) return
        val names = Files.list(cache).use { stream -> stream.map { it.fileName.toString() }.toList() }
        assertEquals(emptyList<String>(), names)
    }

    private fun file(id: Long, name: String, size: Int = 1, md5: String? = null) =
        FirmwareSummary(id, 18, name, size.toLong(), md5)

    private fun server(origin: String, vararg files: FirmwareSummary, failed: Boolean = false) =
        EdenServerFiles(origin, files.toList(), failed)

    private fun romm(origin: String, vararg files: FirmwareSummary, failed: Boolean = false) =
        RommSwitchFirmware(origin, files.toList(), failed)

    private fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private class MemoryEden : EdenTree {
        val files = linkedMapOf<String, ByteArray>()
        val dirs = mutableSetOf<String>()
        var failWrite: String? = null
        var cancelWrite: String? = null
        var failDelete: String? = null
        var failRename: String? = null
        var readOverride: ((String) -> ByteArray?)? = null

        override fun exists(relativePath: String) = relativePath in files || relativePath in dirs

        override fun size(relativePath: String) = files[relativePath]?.size?.toLong()

        override fun list(relativeDir: String): List<EdenNode> {
            val prefix = if (relativeDir.isEmpty()) "" else "$relativeDir/"
            val seen = mutableSetOf<String>()
            val nodes = mutableListOf<EdenNode>()
            for (path in files.keys + dirs) {
                if (!path.startsWith(prefix)) continue
                val rest = path.removePrefix(prefix)
                if (rest.isEmpty() || '/' in rest || rest in seen) continue
                seen += rest
                val full = prefix + rest
                if (full in files) nodes += EdenNode(rest, false, files.getValue(full).size.toLong())
                else nodes += EdenNode(rest, true, 0)
            }
            return nodes
        }

        override fun read(relativePath: String): ByteArray =
            readOverride?.invoke(relativePath) ?: files[relativePath] ?: error("missing $relativePath")

        override fun write(relativePath: String, bytes: ByteArray) {
            if (failWrite == relativePath) error("write $relativePath")
            files[relativePath] = bytes.copyOf()
        }

        override fun writeStream(relativePath: String, source: InputStream): Long {
            if (cancelWrite == relativePath) throw CancellationException("stop")
            if (failWrite == relativePath) error("write $relativePath")
            val bytes = source.readBytes()
            files[relativePath] = bytes
            return bytes.size.toLong()
        }

        override fun delete(relativePath: String) {
            if (failDelete == relativePath) error("delete $relativePath")
            files.remove(relativePath)
            dirs.remove(relativePath)
        }

        override fun mkdir(relativePath: String) {
            dirs += relativePath
        }

        override fun rename(path: String, newName: String) {
            if (failRename == path) error("rename $path")
            val bytes = files[path] ?: error("missing $path")
            val parent = path.substringBeforeLast('/', "")
            val dest = if (parent.isEmpty()) newName else "$parent/$newName"
            files.remove(path)
            files[dest] = bytes
        }
    }

    private class FakeFiles(
        servers: List<EdenServerFiles>,
        bytes: Map<Long, ByteArray>,
        fail: Set<Long> = emptySet(),
    ) : EdenFileSource {
        var servers: List<EdenServerFiles> = servers
        var bodies: Map<Long, ByteArray> = bytes
        var fail: Set<Long> = fail
        val asked = mutableListOf<Long>()
        var listings = 0
        var cancel = false
        var downloadError: (() -> Throwable)? = null
        var beforeWrite: () -> Unit = {}
        var beforeDownload: () -> Unit = {}
        var onDownload: suspend () -> Unit = {}

        override suspend fun servers(): List<EdenServerFiles> {
            listings++
            return servers
        }

        override suspend fun download(
            origin: String,
            file: FirmwareSummary,
            target: Path,
            progress: ((RomProgress) -> Unit)?,
        ) {
            beforeDownload()
            asked += file.id
            onDownload()
            if (cancel) throw CancellationException("stop")
            downloadError?.let { throw it() }
            if (file.id in fail) error("md5")
            beforeWrite()
            val body = bodies.getValue(file.id)
            target.parent?.let { Files.createDirectories(it) }
            Files.write(target, body)
            progress?.invoke(RomProgress.Receiving(body.size.toLong(), file.sizeBytes))
            progress?.invoke(RomProgress.Checking)
        }
    }

    private companion object {
        const val PKG = "dev.eden.eden_emulator"
        const val REGISTERED = "nand/system/Contents/registered"
        const val MARKER = "nand/system/Contents/foldcade-firmware.partial"
    }
}
