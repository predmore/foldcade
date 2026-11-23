package app.foldcade.plugins.armsx2

import app.foldcade.api.plugin.SaveFolderAccess
import app.foldcade.api.plugin.SaveFolderEntry
import org.junit.Assert.*
import org.junit.Test

class Ps2BiosFolderTest {
    private val player = ArmsX2Player()

    private class Folder(val ini: String?, val readError: Boolean = false) : SaveFolderAccess {
        override fun read(path: String, limit: Int): ByteArray? {
            assertEquals(Ps2Card.INI, path)
            if (readError) throw java.io.IOException("unreadable ini")
            return ini?.toByteArray()?.takeIf { it.size <= limit }
        }
        override fun list(path: String): List<SaveFolderEntry>? = error("unexpected list")
        override fun read(path: String, offset: Long, length: Int): ByteArray? = error("unexpected ranged read")
        override fun write(path: String, bytes: ByteArray): Boolean = error("unexpected write")
        override fun createFolder(path: String): Boolean = error("unexpected write")
        override fun moveToBackup(path: String): Boolean = error("unexpected write")
    }

    private fun outside(pick: String, bios: String): Boolean =
        player.firmwareNeedsImport(Folder("[Folders]\nBios = $bios\n"), pick)

    @Test fun relativeBiosKeepsThePickedFolderBehaviour() {
        for (bios in listOf("bios", "bios/", "other/bios", "\"bios\"", "")) {
            assertFalse(bios, outside("primary:ARMSX2", bios))
        }
    }

    @Test fun defaultAndroidDataFolderIsInsideAndTrailingSlashesAreIgnored() {
        val pick = "primary:Android/data/com.armsx2/files"
        for (bios in listOf("/storage/emulated/0/Android/data/com.armsx2/files/bios", "\"/storage/emulated/0/Android/data/com.armsx2/files/bios///\"")) {
            assertFalse(bios, outside(pick, bios))
            assertFalse(bios, outside("$pick///", bios))
        }
    }

    @Test fun customPrimaryFolderDistinguishesItsBiosFromThePrivateCopy() {
        assertTrue(outside("primary:ARMSX2", "/storage/emulated/0/Android/data/com.armsx2/files/bios"))
        assertFalse(outside("primary:ARMSX2", "/storage/emulated/0/ARMSX2/bios/"))
        assertTrue(outside("primary:ARMSX2", "/storage/emulated/0/ARMSX2/bios-old"))
        assertFalse(outside("primary:", "/storage/emulated/0/bios"))
    }

    @Test fun sdCardFolderUsesItsOwnVolumePath() {
        assertFalse(outside("ABCD-1234:Emulators/ARMSX2/", "/storage/ABCD-1234/Emulators/ARMSX2/bios/"))
        assertTrue(outside("ABCD-1234:Emulators/ARMSX2", "/storage/emulated/0/Android/data/com.armsx2/files/bios"))
        assertTrue(outside("ABCD-1234:Emulators/ARMSX2", "/storage/FFFF-5678/Emulators/ARMSX2/bios"))
        assertFalse(outside("ABCD-1234:", "/storage/ABCD-1234/bios"))
    }

    @Test fun missingUnreadableOrGarbledIniKeepsThePickedFolderBehaviour() {
        for (ini in listOf(null, "", "not an ini", "[Folders\nBios = /outside/bios", "[Filenames]\nBIOS = console.bin", "[Folders]\n; Bios = /outside/bios\n# Bios = /outside/bios", "[Folders]\nBios =")) {
            assertFalse(ini, player.firmwareNeedsImport(Folder(ini), "primary:ARMSX2"))
        }
        assertFalse(player.firmwareNeedsImport(Folder("[Folders]\nBios = /outside/bios\n" + "x".repeat(INI_LIMIT)), "primary:ARMSX2"))
        assertFalse(player.firmwareNeedsImport(Folder("[Folders]\nBios = /outside/bios", readError = true), "primary:ARMSX2"))
        assertFalse(player.firmwareNeedsImport(Folder("[Folders]\nBios = /outside/bios"), null))
    }

    @Test fun biosUsesTheSameSectionQuotesAndCommentsAsMemoryCardSettings() {
        val ini = "; comment\r\n[Folders]\r\n Bios = \"/storage/emulated/0/ARMSX2/bios/\"\r\n[Other]\r\nBios = /outside/bios\r\n[MemoryCards]\r\nSlot1_Enable = 0\r\nSlot1_Filename = \"my card.ps2\"\r\n"
        assertFalse(player.firmwareNeedsImport(Folder(ini), "primary:ARMSX2"))
        assertEquals(Slot1(false, "my card.ps2"), parseSlot1(ini))
    }
}
