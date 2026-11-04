package app.foldcade

import app.foldcade.api.plugin.FirmwareBytes
import org.junit.Assert.*
import org.junit.Test

class FirmwareSourceCacheTest {
    @Test fun unchangedArchivesAndUnrelatedFilesDoNotNeedAnotherReadOrUnpack() {
        val cache = FirmwareSourceCache()
        val version = FirmwareSourceVersion(181087479, 12345)
        val files = listOf(FirmwareBytes("PS2_BIOS.zip/console.bin", "content://cache/console"))
        assertNull(cache.get("arms:zip", version))
        cache.put("arms:zip", version, files)
        assertEquals(files, cache.get("arms:zip", version))
        cache.put("ps1:unrelated-zip", version, emptyList())
        assertEquals(emptyList<FirmwareBytes>(), cache.get("ps1:unrelated-zip", version))
        assertNull(cache.get("arms:zip", version.copy(size = version.size + 1)))
        assertNull(cache.get("arms:zip", version.copy(modifiedMillis = version.modifiedMillis + 1)))
        assertNull(cache.get("a-different-core:zip", version))
    }
    @Test fun unknownProviderTimestampsDoNotHideAChangedSource() {
        val cache = FirmwareSourceCache()
        val version = FirmwareSourceVersion(524288, 0)
        cache.put("source", version, emptyList())
        assertNull(cache.get("source", version))
    }
    @Test fun aSourceVersionSurvivesProcessDeathWithoutCopyingTheArchiveAgain() {
        val root = kotlin.io.path.createTempDirectory("firmware-archive").toFile()
        try {
            val version = FirmwareSourceVersion(4, 12345)
            val first = reuseFirmwareArchive(root, "content://bios/collection", "bios.zip", version) { it.writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val reopened = reuseFirmwareArchive(root, "content://bios/collection", "bios.zip", version) { error("copied again after restart") }
            assertEquals(first, reopened)
            assertEquals(listOf<Byte>(1, 2, 3, 4), reopened.readBytes().toList())
            java.io.File(first.parentFile, "bios.zip.local-version").writeText("old-stamp")
            val changed = reuseFirmwareArchive(root, "content://bios/collection", "bios.zip", version.copy(modifiedMillis = 12346)) { it.writeBytes(byteArrayOf(5, 6, 7, 8)) }
            assertEquals(listOf<Byte>(5, 6, 7, 8), changed.readBytes().toList())
            assertFalse(java.io.File(first.parentFile, "bios.zip.local-version").exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun anInterruptedArchiveCopyLeavesNoPartialAndKeepsTheLastCompleteCopy() {
        val root = kotlin.io.path.createTempDirectory("firmware-archive").toFile()
        try {
            val version = FirmwareSourceVersion(4, 12345)
            val old = reuseFirmwareArchive(root, "content://bios/collection", "bios.zip", version) { it.writeBytes(byteArrayOf(1, 2, 3, 4)) }
            try {
                reuseFirmwareArchive(root, "content://bios/collection", "bios.zip", version.copy(modifiedMillis = 12346)) {
                    it.writeBytes(byteArrayOf(9))
                    throw java.io.IOException("storage disconnected")
                }
                fail("copy should fail")
            } catch (_: java.io.IOException) { }
            assertEquals(listOf<Byte>(1, 2, 3, 4), old.readBytes().toList())
            assertFalse(root.walkTopDown().any { it.name.endsWith(".partial") })
        } finally { root.deleteRecursively() }
    }

    @Test fun unknownFoldersNeverProveBiosMissingOrBlockWorkingEmulators() {
        assertFalse(FirmwareResult(emptyList(), listOf("PlayStation BIOS (.bin)"), true, "DuckStation/bios").blocksLaunch)
        assertFalse(FirmwareResult(emptyList(), emptyList(), true, "RetroArch/system").blocksLaunch)
        assertFalse(FirmwareResult(emptyList(), emptyList(), false, "RetroArch/system").blocksLaunch)
        assertTrue(FirmwareResult(emptyList(), listOf("bios_CD_U.bin"), false, "RetroArch/system").blocksLaunch)
    }
}
