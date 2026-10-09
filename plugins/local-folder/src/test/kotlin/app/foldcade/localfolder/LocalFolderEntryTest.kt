package app.foldcade.localfolder

import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.MetadataProvider
import app.foldcade.api.plugin.PLUGIN_API_MINOR
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.canonicalPlatformId
import java.util.ServiceLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFolderEntryTest {
    @Test
    fun serviceLoaderRegistersTheLibraryAndItsPlatforms() {
        val loaded = ServiceLoader.load(
            PluginEntry::class.java,
            LocalFolderEntry::class.java.classLoader,
        ).toList()
        val entry = loaded.filterIsInstance<LocalFolderEntry>().single()
        assertEquals(PLUGIN_API_VERSION, entry.apiVersion)
        assertEquals(PLUGIN_API_MINOR, entry.apiMinor)
        assertTrue(entry.libraries.single() === entry.folder)
        assertEquals("local-folder", entry.folder.id)
        assertEquals("Local folder", entry.folder.displayName)
        assertTrue(entry.metadataProviders.isEmpty())
        assertTrue(entry.players.isEmpty())
        assertEquals(
            PlatformCatalog.specs.map { it.platform.id },
            entry.platforms.map { it.id },
        )
        assertEquals(
            PlatformCatalog.specs.map { it.platform.name },
            entry.platforms.map { it.displayName },
        )
        assertEquals(
            PlatformCatalog.specs.map { it.extensions },
            entry.platforms.map { it.extensions },
        )
        assertEquals(
            PlatformCatalog.specs.map { it.aliases },
            entry.platforms.map { it.aliases },
        )
        assertEquals("nintendo-3ds", canonicalPlatformId(entry.platforms, "3ds"))
        assertEquals("nintendo-3ds", canonicalPlatformId(entry.platforms, "N3DS"))
        assertEquals("nintendo-3ds", canonicalPlatformId(entry.platforms, "new-nintendo-3ds"))
        assertEquals("nintendo-ds", canonicalPlatformId(entry.platforms, "nds"))
        assertEquals("snes", canonicalPlatformId(entry.platforms, "snes"))
        assertEquals("snes", canonicalPlatformId(entry.platforms, "sfam"))
        assertEquals("nes", canonicalPlatformId(entry.platforms, "nes"))
        assertEquals("nes", canonicalPlatformId(entry.platforms, "famicom"))
        assertEquals("psp", canonicalPlatformId(entry.platforms, "psp"))
        assertEquals("genesis", canonicalPlatformId(entry.platforms, "genesis"))
        assertEquals("game-boy", canonicalPlatformId(entry.platforms, "gb"))
        assertEquals("game-boy-color", canonicalPlatformId(entry.platforms, "gbc"))
        assertEquals("game-boy-advance", canonicalPlatformId(entry.platforms, "gba"))
        assertEquals("nintendo-64", canonicalPlatformId(entry.platforms, "n64"))
        assertEquals("gamecube", canonicalPlatformId(entry.platforms, "ngc"))
        assertEquals("playstation", canonicalPlatformId(entry.platforms, "psx"))
        assertEquals("playstation-2", canonicalPlatformId(entry.platforms, "ps2"))
        assertEquals("master-system", canonicalPlatformId(entry.platforms, "sms"))
        assertEquals("game-gear", canonicalPlatformId(entry.platforms, "gamegear"))
        assertFalse(MetadataProvider::class.java.isAssignableFrom(entry.folder.javaClass))

        val advertised = LocalFolderEntry::class.java.classLoader
            .getResourceAsStream("META-INF/services/app.foldcade.api.plugin.PluginEntry")!!
            .bufferedReader()
            .readLines()
            .map { it.substringBefore("#").trim() }
            .filter { it.isNotEmpty() }
        assertEquals(listOf("app.foldcade.localfolder.LocalFolderEntry"), advertised)
    }

    @Test
    fun theRegisteredLibraryScansAfterTheTreeIsBound() = runBlocking {
        val entry = ServiceLoader.load(
            PluginEntry::class.java,
            LocalFolderEntry::class.java.classLoader,
        ).filterIsInstance<LocalFolderEntry>().single()
        val root = FolderEntry("content://3ds", "3ds", DOCUMENT_DIRECTORY_MIME)
        val game = FolderEntry("content://3ds/game", "Game.cci", "application/octet-stream")
        entry.folder.bindTree(root) { listOf(game) }

        val page = entry.folder.listGames("nintendo-3ds", GameQuery())
        assertEquals("content://3ds/game", page.games.single().remoteKey)
        assertEquals("nintendo-3ds", page.games.single().platformId)
        assertNull(entry.folder.listGames("3ds", GameQuery()).nextOffset)
        assertEquals(0, entry.folder.listGames("3ds", GameQuery()).total)
    }

    @Test
    fun connectWithoutATreeIsUnavailable() = runBlocking {
        val folder = LocalFolderBackend()
        val failure = runCatching { folder.connect() }.exceptionOrNull()
        assertTrue(failure is PluginException.Unavailable)
        assertFalse(failure is kotlin.coroutines.cancellation.CancellationException)
    }
}
