package app.foldcade.plugins.romm

import app.foldcade.api.plugin.PluginException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentUriTest {
    @Test
    fun cachePathRoundTripsAndRejectsEscape() {
        val root = Files.createTempDirectory("romm-cache")
        val file = root.resolve("roms").resolve("1234").resolve("mario game.cci")
        Files.createDirectories(file.parent)
        Files.write(file, byteArrayOf(1))
        val adapter = CachePathContentUri(root)
        val uri = adapter.uriFor(file)
        assertEquals(
            "content://app.foldcade.romm.cache/romm/roms/1234/mario%20game.cci",
            uri,
        )
        assertEquals(file.toAbsolutePath().normalize(), adapter.pathFor(uri)!!.toAbsolutePath().normalize())
        assertNull(adapter.pathFor("content://other/romm/roms/1234/mario%20game.cci"))
        assertNull(adapter.pathFor("content://app.foldcade.romm.cache/romm/../secret"))
        val outside = root.parent.resolve("secret.bin")
        val escaped = runCatching { adapter.uriFor(outside) }.exceptionOrNull()
        assertTrue(escaped is PluginException.NotFound)
    }
}
