package app.foldcade.plugins.duckstation

import app.foldcade.api.plugin.SaveFolderAccess
import app.foldcade.api.plugin.SaveFolderEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuckStationCardSettingsTest {
    @Test fun gameOverridesOnlyTheKeysItSets() {
        val result = readDuckStationCardSettings(Folder(mapOf(
            "settings.ini" to """
                ; [MemoryCards]
                [BIOS]
                Directory = ignored
                [MemoryCards]
                Directory = "/android_content/app.foldcade.saves/tree@foldcade/duckstation"
                Card1Type = PerGameTitle
                Card1Path = shared;card.mcd
            """.trimIndent(),
            "gamesettings/SCUS-94163.ini" to "[MemoryCards]\nCard1Type=PerGameFileTitle",
            "gamesettings/SCUS-94164.ini" to "[MemoryCards]\nDirectory=/sdcard/PS1 Cards\nCard1Path=\nCard2Path=other.mcd",
            "gamesettings/SCUS-94165.ini" to "[MemoryCards]\nCard1Type=None",
            "gamesettings/notes.txt" to "ignored",
        )))!!

        val global = DuckStationCardSettings("/android_content/app.foldcade.saves/tree@foldcade/duckstation", "PerGameTitle", "shared;card.mcd", null)
        assertEquals(listOf(
            global,
            global.copy(card1Type = "PerGameFileTitle"),
            global.copy(directory = "/sdcard/PS1 Cards", card1Path = null, card2Path = "other.mcd"),
            global.copy(card1Type = "None"),
        ), result)
    }

    @Test fun defaultsAndInvalidOverrideValuesFollowGlobalSettings() {
        val result = readDuckStationCardSettings(Folder(mapOf(
            "settings.ini" to "\uFEFF[MemoryCards]\r\nDirectory=\r\nCard1Type=Shared",
            "gamesettings/SCUS-94163.ini" to "[MemoryCards]\nCard1Type=FutureType",
        )))
        assertEquals(listOf(DuckStationCardSettings("memcards", "Shared", null, null)), result)
        assertEquals(listOf(DuckStationCardSettings(null, "PerGameTitle", null, null)),
            readDuckStationCardSettings(Folder(mapOf("settings.ini" to "[BIOS]\nEnabled = true"))))
    }

    @Test fun incompleteReadsGiveNoConfigEvidence() {
        assertNull(readDuckStationCardSettings(Folder(emptyMap())))
        assertNull(readDuckStationCardSettings(Folder(mapOf(
            "settings.ini" to "[MemoryCards]\nDirectory=/sdcard/PS1",
            "gamesettings/SCUS-94163.ini" to "[MemoryCards\nDirectory=/sdcard/Testing",
        ))))
        assertNull(readDuckStationCardSettings(Folder(mapOf(
            "settings.ini" to "[MemoryCards]\nDirectory=/sdcard/PS1",
            "gamesettings/SCUS-94163.ini" to "x".repeat(129 * 1024),
        ))))
    }

    private class Folder(private val files: Map<String, String>) : SaveFolderAccess {
        override fun list(path: String): List<SaveFolderEntry>? = when (path) {
            "" -> files.keys.map { it.substringBefore('/') }.distinct().map { SaveFolderEntry(it, it == "gamesettings", -1) }
            "gamesettings" -> files.keys.filter { it.startsWith("gamesettings/") }
                .map { SaveFolderEntry(it.substringAfter('/'), false, -1) }
            else -> null
        }
        override fun read(path: String, limit: Int) = files[path]?.encodeToByteArray()?.takeIf { it.size <= limit }
        override fun read(path: String, offset: Long, length: Int): ByteArray? = null
        override fun write(path: String, bytes: ByteArray): Boolean = error("Read only")
        override fun createFolder(path: String): Boolean = error("Read only")
        override fun moveToBackup(path: String): Boolean = error("Read only")
    }
}
