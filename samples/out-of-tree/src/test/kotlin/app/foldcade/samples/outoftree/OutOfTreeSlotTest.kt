package app.foldcade.samples.outoftree

import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.MetadataProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OutOfTreeSlotTest {
    @Test
    fun libraryAndMetadataAreSeparateSlots() {
        val entry = OutOfTreeEntry()
        val library = entry.libraries.single()
        val metadata = entry.metadataProviders.single()
        assertEquals("outoftree.library", library.id)
        assertEquals("outoftree.metadata", metadata.id)
        assertFalse(library is MetadataProvider)
        assertFalse(metadata is LibraryBackend)
        assertEquals("outoftree.platform", entry.platforms.single().id)
        assertEquals("outoftree.player", entry.players.single().id)
        assertEquals("outoftree.platform", entry.players.single().platformId)
    }
}
