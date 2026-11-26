package app.foldcade.romm

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Expected values are from RomM 5.4's `hash_save_content`, run in Python on the same files. */
class RommSaveHashTest {
    @Test
    fun aZipHashesByItsFilesWhicheverOrderAndFoldersItLists() {
        val expected = "06962f13ce5a4e15b9ae34f3b72bcc89"
        val listed = zipOf(
            "extdata/icon" to "icon",
            "data/" to null,
            "data/00000001/" to null,
            "data/00000001/00.SAV" to "abc",
            "data/00000001.metadata" to "meta",
        )
        val bare = zipOf(
            "data/00000001.metadata" to "meta",
            "data/00000001/00.SAV" to "abc",
            "extdata/icon" to "icon",
        )
        assertEquals(expected, rommSaveHash(listed))
        assertEquals(expected, rommSaveHash(bare))
    }

    @Test
    fun entriesAreOrderedByNameBeforeTheirHashesAreJoined() {
        // Sorting whole `name:md5` lines would put `main.bak` first, as `.` sorts before `:`.
        assertEquals("adfc46863a7cbf973e804e49868effc4", rommSaveHash(zipOf("data/main" to "one", "data/main.bak" to "two")))
    }

    @Test
    fun namesPastTheBmpSortByCodePointAsRommDoes() {
        // U+F900 sorts before U+1F600 by code point, after it by UTF-16 unit (a surrogate, U+D83D).
        // Expected from RomM 5.4.1's order (Python `sorted` on the names), joined and hashed the same way.
        val zip = zipOf("data/\uD83D\uDE00.bin" to "two", "data/\uF900.bin" to "one")
        assertEquals("4ce4682478897ca9f16a2770dde7478b", rommSaveHash(zip))
    }

    @Test
    fun namesInsideTheBmpKeepTheOrderExistingLedgersWereHashedWith() {
        val names = listOf("data/main.bak", "data/Z", "data/main", "data/é", "data/\uF900", "data/ä/1", "data/a", "data/0")
        assertEquals(names.sorted(), names.sortedWith(CodePointOrder))
        assertEquals("adfc46863a7cbf973e804e49868effc4", rommSaveHash(zipOf("data/main.bak" to "two", "data/main" to "one")))
    }

    @Test
    fun anythingElseIsTheMd5OfItsBytes() {
        assertEquals("8d90078ef0b3778140a7fa0ab541cfd6", rommSaveHash("plain save".toByteArray()))
        assertEquals(md5Hex(ByteArray(8192)), rommSaveHash(ByteArray(8192)))
    }

    @Test
    fun aBrokenZipHasNoHash() {
        val zip = zipOf("data/a" to "x")
        // Keep the end record, so it still looks like a zip, and break the entry before it.
        val broken = zip.copyOf().also { bytes -> for (i in 0 until 30) bytes[i] = 0 }
        assertNull(rommSaveHash(broken))
    }

    private fun zipOf(vararg entries: Pair<String, String?>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, text) in entries) {
                zip.putNextEntry(ZipEntry(name))
                if (text != null) zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
