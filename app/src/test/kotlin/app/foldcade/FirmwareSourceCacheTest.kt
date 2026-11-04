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
    @Test fun unknownFoldersNeverProveBiosMissingOrBlockWorkingEmulators() {
        assertFalse(FirmwareResult(emptyList(), listOf("PlayStation BIOS (.bin)"), true, "DuckStation/bios").blocksLaunch)
        assertFalse(FirmwareResult(emptyList(), emptyList(), true, "RetroArch/system").blocksLaunch)
        assertFalse(FirmwareResult(emptyList(), emptyList(), false, "RetroArch/system").blocksLaunch)
        assertTrue(FirmwareResult(emptyList(), listOf("bios_CD_U.bin"), false, "RetroArch/system").blocksLaunch)
    }
}
