package app.foldcade

import app.foldcade.api.plugin.SaveSyncState
import app.foldcade.plugins.armsx2.ArmsX2Player
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SaveReadSessionTest {
    @Test
    fun theBlankCardCheckOpensAndResolvesTheCardOnceAndTheNextCheckIsFresh() = withSaves { saves, docs ->
        assertEquals(SaveSyncState.Syncing, saves.saveSync(ArmsX2Player(), FOLDER))
        assertEquals(129, docs.reads) // Superblock, then 128 bounded slices of the 8 MiB card.
        assertEquals(1, docs.opens)
        assertEquals(1, docs.finds)
        assertEquals(1, docs.closes)
        docs.lastByte = 1
        assertEquals(true, saves.saveSync(ArmsX2Player(), FOLDER) is SaveSyncState.NeedsSetup)
        assertEquals(2, docs.opens)
        assertEquals(2, docs.closes)
    }

    @Test
    fun anUnreadableCardStillNeedsSetupAndTheOpenHandleCloses() = withSaves { saves, docs ->
        docs.unreadable = true
        assertEquals(true, saves.saveSync(ArmsX2Player(), FOLDER) is SaveSyncState.NeedsSetup)
        assertEquals(1, docs.opens)
        assertEquals(1, docs.closes)
    }

    @Test
    fun aFatalReadClosesItsHandleToo() = withSaves { saves, docs ->
        docs.fatal = true
        assertThrows(LinkageError::class.java) { saves.saveSync(ArmsX2Player(), FOLDER) }
        assertEquals(1, docs.closes)
    }

    private fun withSaves(test: (PlayerSaves, CardDocuments) -> Unit) {
        val directory = Files.createTempDirectory("card-health")
        try {
            val marks = object : SaveMarks {
                override fun mark(key: String): String? = null
                override fun setMark(key: String, hash: String) = error("Health must not write marks")
                override fun clearMark(key: String) = error("Health must not clear marks")
            }
            val docs = CardDocuments()
            test(PlayerSaves(docs, marks, directory, directory.resolve("handover")), docs)
        } finally { directory.toFile().deleteRecursively() }
    }

    private class CardDocuments : SaveDocuments {
        var opens = 0
        var closes = 0
        var finds = 0
        var reads = 0
        var lastByte: Byte = -1
        var unreadable = false
        var fatal = false
        override fun displayName(uri: String): String? = null
        override fun create(folderUri: String, name: String): String? = error("Read only")
        override fun write(uri: String, bytes: ByteArray) = error("Read only")
        override fun delete(uri: String): Boolean = error("Read only")
        override fun read(uri: String): ByteArray? = error("Must not read the whole card into memory")
        override fun find(folderUri: String, name: String): String? {
            finds++
            return if (name == "memcards/mcd001.ps2") "$FOLDER/$name" else null
        }
        override fun children(folderUri: String, path: String): List<SaveChild>? = when (path) {
            "" -> listOf(SaveChild("memcards", "$FOLDER/memcards", true, 0))
            "memcards" -> listOf(SaveChild("mcd001.ps2", "$FOLDER/memcards/mcd001.ps2", false, SIZE))
            else -> null
        }
        override fun openRead(uri: String): SaveDocumentRead {
            opens++
            return object : SaveDocumentRead {
                override fun read(offset: Long, length: Int): ByteArray? {
                    reads++
                    if (fatal) throw LinkageError("read failed")
                    if (unreadable) return null
                    val count = minOf(length.toLong(), SIZE - offset).toInt()
                    return ByteArray(count) { -1 }.also { bytes ->
                        if (offset + count == SIZE) bytes[bytes.lastIndex] = lastByte
                    }
                }
                override fun close() { closes++ }
            }
        }
    }

    private companion object {
        const val FOLDER = "content://card-root"
        const val SIZE = 8_388_608L
    }
}
