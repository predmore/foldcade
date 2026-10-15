package app.foldcade

import app.foldcade.api.plugin.LaunchTarget
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SharedPathsTest {
    private lateinit var shared: Path
    private lateinit var private: Path

    @Before
    fun setUp() {
        shared = Files.createTempDirectory("media-romm")
        private = Files.createTempDirectory("cache-romm")
    }

    @After
    fun tearDown() {
        shared.toFile().deleteRecursively()
        private.toFile().deleteRecursively()
    }

    @Test
    fun aLocalFolderDocumentIsItsPathOnTheVolume() {
        assertEquals(
            "/storage/emulated/0/ROMs/genesis/Disney Collection, The (Europe).md",
            SharedPaths.externalStoragePath(
                "content://com.android.externalstorage.documents/tree/primary%3AROMs" +
                    "/document/primary%3AROMs%2Fgenesis%2FDisney%20Collection%2C%20The%20(Europe).md",
            ),
        )
        assertEquals(
            "/storage/emulated/0/Download/Game.gba",
            SharedPaths.externalStoragePath(
                "content://com.android.externalstorage.documents/document/primary%3ADownload%2FGame.gba",
            ),
        )
        // A plus is a plus, not a space.
        assertEquals(
            "/storage/1A2B-3C4D/gba/Mario+Luigi.gba",
            SharedPaths.externalStoragePath(
                "content://com.android.externalstorage.documents/document/1A2B-3C4D%3Agba%2FMario+Luigi.gba",
            ),
        )
        assertEquals(
            "/storage/emulated/0/Documents/roms/Tetris.gb",
            SharedPaths.externalStoragePath(
                "content://com.android.externalstorage.documents/document/home%3Aroms%2FTetris.gb",
            ),
        )
    }

    @Test
    fun anythingElseHasNoSharedPath() {
        for (uri in listOf(
            // Another provider, a volume root, a folder's tree, a broken escape, a way out.
            "content://com.android.providers.downloads.documents/document/msf%3A42",
            "content://com.android.externalstorage.documents/document/primary%3A",
            "content://com.android.externalstorage.documents/tree/primary%3AROMs",
            "content://com.android.externalstorage.documents/document/primary%3AROMs%2Fx%2",
            "content://com.android.externalstorage.documents/document/primary%3AROMs%2F..%2F..%2Fdata",
            "content://com.android.externalstorage.documents/document/usb%3Agame.gba",
            "file:///storage/emulated/0/ROMs/game.gba",
        )) {
            assertNull(uri, SharedPaths.externalStoragePath(uri))
        }
    }

    @Test
    fun anOwnFileCountsOnlyInASharedRoot() {
        val inShared = shared.resolve("servers/abc/roms/11/Mario Kart.gba")
        val inPrivate = private.resolve("servers/abc/roms/11/Mario Kart.gba")
        for (file in listOf(inShared, inPrivate)) {
            Files.createDirectories(file.parent)
            Files.write(file, byteArrayOf(1))
        }
        val files = mapOf("content://own/shared" to inShared, "content://own/private" to inPrivate)
        val paths = SharedPaths(ownFile = { files[it] }, sharedRoots = { listOf(shared) })
        assertEquals(
            LaunchTarget.SharedFile(inShared.toAbsolutePath().normalize().toString(), "content://own/shared"),
            paths.target("content://own/shared"),
        )
        assertNull(paths.target("content://own/private"))
        assertNull(paths.target("content://own/unknown"))
        // A download that is gone is not handed over.
        Files.delete(inShared)
        assertNull(paths.pathFor("content://own/shared"))
    }

    @Test
    fun oldDownloadsMoveAndAnExistingCopyWins() {
        val from = private.resolve("servers")
        val to = shared.resolve("servers")
        val rom = from.resolve("abc/roms/11/Mario Kart.gba")
        val spec = from.resolve("abc/roms/11/rom.json")
        val both = from.resolve("abc/roms/12/Ristar.md")
        for (file in listOf(rom, spec, both)) {
            Files.createDirectories(file.parent)
            Files.write(file, file.fileName.toString().toByteArray())
        }
        Files.createDirectories(to.resolve("abc/roms/12"))
        Files.write(to.resolve("abc/roms/12/Ristar.md"), "newer".toByteArray())
        assertTrue(moveRommDownloads(from, to))
        assertEquals("Mario Kart.gba", Files.readString(to.resolve("abc/roms/11/Mario Kart.gba")))
        assertEquals("rom.json", Files.readString(to.resolve("abc/roms/11/rom.json")))
        assertEquals("newer", Files.readString(to.resolve("abc/roms/12/Ristar.md")))
        assertFalse(Files.exists(from))
        val left = Files.walk(to).use { stream -> stream.filter { it.fileName.toString().endsWith(".moving") }.count() }
        assertEquals(0L, left)
        // Nothing to move is not a failure.
        assertTrue(moveRommDownloads(from, to))
    }

    @Test
    fun coverArtStaysInTheCache() {
        val from = private.resolve("servers")
        val to = shared.resolve("servers")
        val art = from.resolve("abc/artwork/0f3a")
        val rom = from.resolve("abc/roms/11/Game.gba")
        for (file in listOf(art, rom)) {
            Files.createDirectories(file.parent)
            Files.write(file, byteArrayOf(1))
        }
        assertTrue(moveRommDownloads(from, to) { it.any { part -> part.toString() == "artwork" } })
        assertTrue(Files.exists(art))
        assertTrue(Files.exists(to.resolve("abc/roms/11/Game.gba")))
        assertFalse(Files.exists(to.resolve("abc/artwork")))
    }
}
