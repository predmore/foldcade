package app.foldcade

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SharedStorageTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun storage() = SharedStorage(temporary.root)

    @Test fun idsNameThePrimaryVolumeAndChildrenByPathSegments() {
        val storage = storage()
        assertEquals(temporary.root.canonicalFile, storage.fileOf("primary:"))
        assertEquals(File(temporary.root.canonicalFile, "saves/nds"), storage.fileOf("primary:saves/nds"))
        assertTrue(storage.isChild("primary:", "primary:saves/nds"))
        assertTrue(storage.isChild("primary:saves", "primary:saves/nds"))
        assertFalse(storage.isChild("primary:saves", "primary:saves"))
        assertFalse(storage.isChild("primary:save", "primary:saves/nds"))
        assertFalse(storage.isChild("primary:saves/nds", "primary:saves"))
        assertEquals("primary:saves/nds", storage.childId("primary:saves", "nds"))
        assertEquals("primary:saves", storage.childId("primary:", "saves"))
        assertEquals("primary:", storage.parentId("primary:saves"))
        assertEquals("primary:saves", storage.parentId("primary:saves/nds"))
    }

    @Test fun androidDataAndObbAreRefusedEvenWithDifferentCase() {
        for (path in listOf("Android/data", "Android/data/com.player/files", "Android/obb/game", "android/DATA/game", "ANDROID/OBB")) {
            assertThrows(SecurityException::class.java) { storage().fileOf("primary:$path") }
            assertThrows(SecurityException::class.java) { storage().isChild("primary:", "primary:$path") }
        }
        for (path in listOf("Android", "Android/media", "Android/database", "games/Android/data")) storage().fileOf("primary:$path")
    }

    @Test fun escapesMalformedIdsAndOtherVolumesAreRefused() {
        for (id in listOf("primary:../outside", "primary:saves/../../outside", "primary:saves/../other", "primary:/outside", "primary:saves//nds", "primary:saves/./nds", "primary:saves/", "primary:saves\\nds", "primary:bad\u0000name", "other:saves", "saves")) {
            assertThrows(id, SecurityException::class.java) { storage().fileOf(id) }
        }
        assertThrows(IllegalArgumentException::class.java) { storage().childId("primary:saves", "../outside") }
    }

    @Test fun linksOutsideTheVolumeAndLinksWithinItAreRefused() {
        val outside = Files.createTempDirectory("foldcade-storage-outside").toFile()
        try {
            Files.createSymbolicLink(File(temporary.root, "outside").toPath(), outside.toPath())
            temporary.newFolder("saves")
            Files.createSymbolicLink(File(temporary.root, "alias").toPath(), File(temporary.root, "saves").toPath())
            Files.createSymbolicLink(File(temporary.root, "missing").toPath(), File(outside, "not-yet-created").toPath())
            for (path in listOf("outside", "outside/save.sav", "alias", "alias/save.sav", "missing")) {
                assertThrows(SecurityException::class.java) { storage().fileOf("primary:$path") }
            }
        } finally {
            outside.deleteRecursively()
        }
    }

    @Test fun rootAliasesResolveButTheVolumeAndAndroidCannotBeMovedOrDeleted() {
        val alias = File(temporary.root, "volume")
        val root = temporary.newFolder("real-volume")
        Files.createSymbolicLink(alias.toPath(), root.toPath())
        assertEquals(File(root.canonicalFile, "saves"), SharedStorage(alias).fileOf("primary:saves"))
        for (id in listOf("primary:", "primary:Android", "primary:android")) {
            assertThrows(SecurityException::class.java) { storage().requireMutable(id) }
        }
        storage().requireMutable("primary:saves")
    }

    @Test fun onlyTheOwnUidWithCurrentAccessCanUseStorage() {
        requireStorageAccess(123, 123, granted = true)
        assertThrows(SecurityException::class.java) { requireStorageAccess(124, 123, granted = true) }
        assertThrows(SecurityException::class.java) { requireStorageAccess(1000, 123, granted = true) }
        assertThrows(SecurityException::class.java) { requireStorageAccess(123, 123, granted = false) }
        assertThrows(SecurityException::class.java) { requireStorageAccess(124, 123, granted = false) }
    }
}
