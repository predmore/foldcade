package app.foldcade.plugins.romm

import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.MetadataProvider
import app.foldcade.api.plugin.PLUGIN_API_MINOR
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.romm.RommContract
import java.io.File
import java.util.ServiceLoader
import kotlin.text.RegexOption
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RommEntryTest {
    @Test
    fun serviceLoaderRegistersLibraryAndMetadataAsSeparateSlots() {
        val entry = ServiceLoader.load(
            PluginEntry::class.java,
            RommEntry::class.java.classLoader,
        ).filterIsInstance<RommEntry>().single()
        val library = entry.libraries.single()
        val metadata = entry.metadataProviders.single()
        assertEquals(PLUGIN_API_VERSION, entry.apiVersion)
        assertEquals(PLUGIN_API_MINOR, entry.apiMinor)
        assertEquals("romm", ROMM_LIBRARY_ID)
        assertEquals("romm.metadata", ROMM_METADATA_ID)
        assertEquals(ROMM_LIBRARY_ID, library.id)
        assertEquals("RomM", library.displayName)
        assertEquals(ROMM_METADATA_ID, metadata.id)
        assertEquals("RomM metadata", metadata.displayName)
        assertFalse(library is MetadataProvider)
        assertFalse(metadata is LibraryBackend)
        assertTrue(library !== metadata)
        assertTrue(entry.platforms.isEmpty())
        assertTrue(entry.players.isEmpty())

        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        host.register(entry)
        assertEquals(library.id, host.library(ROMM_LIBRARY_ID)?.id)
        assertEquals(metadata.id, host.metadata(ROMM_METADATA_ID)?.id)
        assertTrue(host.library(ROMM_METADATA_ID) == null)
        assertTrue(host.metadata(ROMM_LIBRARY_ID) == null)
    }

    @Test
    fun deviceAuthScopesOmitReadsNothingCallsYet() {
        assertTrue(RommContract.DEVICE_AUTH_SCOPES.contains("roms.read"))
        assertFalse(RommContract.DEVICE_AUTH_SCOPES.contains("roms.user.read"))
        assertFalse(RommContract.DEVICE_AUTH_SCOPES.contains("collections.read"))
        assertFalse(RommContract.DEVICE_AUTH_SCOPES.contains("tasks.run"))
        assertFalse(RommContract.DEVICE_AUTH_SCOPES.contains("roms.user.write"))
    }

    @Test
    fun pluginDoesNotTouchKeystoreDataStoreOrAllFilesAccess() {
        val banned = listOf(
            "AndroidKeyStore",
            "KeyGenParameterSpec",
            "DataStore",
            "MANAGE_EXTERNAL_STORAGE",
            "java.net.http",
        )
        val hits = File("src/main").walk().filter { it.isFile }.flatMap { file ->
            val text = file.readText()
                .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
                .lineSequence()
                .filterNot { line -> line.trimStart().startsWith("//") }
                .joinToString("\n")
            banned.filter { needle -> text.contains(needle) }.map { needle -> "${file.path} contains $needle" }
        }.toList()
        assertEquals(emptyList<String>(), hits)
    }
}
