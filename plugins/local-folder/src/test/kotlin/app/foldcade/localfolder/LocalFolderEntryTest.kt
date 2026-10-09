package app.foldcade.localfolder

import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.MetadataProvider
import app.foldcade.api.plugin.PLUGIN_API_MINOR
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.PluginException
import java.util.ServiceLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFolderEntryTest {
    @Test
    fun serviceLoaderRegistersTheLibraryOnly() {
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
        assertTrue(entry.platforms.isEmpty())
        assertTrue(entry.players.isEmpty())
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
