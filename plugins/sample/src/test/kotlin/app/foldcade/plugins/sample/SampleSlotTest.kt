package app.foldcade.plugins.sample

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.MetadataProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleSlotTest {
    private val game = Game(
        backendId = "sample.library",
        remoteKey = "content://sample/1",
        platformId = "sample.platform",
        availability = Availability.LocalOnly,
        label = "Sample game",
    )

    @Test
    fun libraryAndMetadataAreSeparateSlots() {
        val entry = SampleEntry()
        val library = entry.libraries.single()
        val metadata = entry.metadataProviders.single()
        assertEquals("sample.library", library.id)
        assertEquals("Sample library", library.displayName)
        assertEquals("sample.metadata", metadata.id)
        assertFalse(library is MetadataProvider)
        assertFalse(metadata is LibraryBackend)
        assertEquals("sample.platform", entry.platforms.single().id)
        assertEquals("sample.platform", entry.players.single().platformId)
        assertTrue(entry.players.single().packageNames.isNotEmpty())
    }

    @Test
    fun cachedIsSynchronousAndTheLibraryDoesNoWork() = runBlocking {
        val entry = SampleEntry()
        val metadata = entry.metadataProviders.single()
        assertNull(metadata.cached(game))
        assertEquals("Sample game", metadata.fetch(game).title)
        val page = entry.libraries.single().listGames("sample.platform", GameQuery())
        assertTrue(page.games.isEmpty())
        assertNull(page.nextOffset)
    }
}
