package app.foldcade.plugins.dolphin

import app.foldcade.api.plugin.*
import org.junit.Assert.*
import org.junit.Test
import java.util.ServiceLoader

class DolphinTest {
    @Test fun bothDolphinPlatformsKeepTheirLaunchIdsAndOnlyGameCubeSyncs() {
        val players = DolphinEntry().players
        assertEquals(listOf("dolphin.gamecube", "dolphin.wii"), players.map { it.id })
        val game = Game("test", "1", "wii", Availability.LocalOnly, "Game")
        val wii = players.last()
        assertFalse(wii.supportsSaveSync(game))
        assertTrue(wii.saveDeclarations(game).isEmpty())
        val launch = wii.launchIntent(LaunchRequest(game, LaunchTarget.ContentUri("content://test/game.iso"), "org.dolphinemu.dolphinemu"))
        assertEquals(listOf(PlayerExtra.Text("AutoStartFile", "content://test/game.iso")), launch.extras)
        assertEquals("org.dolphinemu.dolphinemu.ui.main.TvMainActivity", launch.componentClass)
        assertTrue(launch.grantReadUri)
        assertEquals(setOf(LaunchFlag.NewTask), launch.flags)
    }
    @Test fun discIdentityAndRegionComeFromTheDisc() {
        assertEquals(GameCubeDisc("GFZE8P", "USA"), gameCubeDisc(disc()))
        assertEquals(GameCubeDisc("GFZE8P", "JAP"), gameCubeDisc(disc(region = 0)))
        assertNull(gameCubeDisc(disc(region = 99)))
        assertNull(gameCubeDisc(disc(magic = false)))
    }

    @Test fun cardSelectionIsByGciContentsAndLeavesOtherGamesAlone() {
        val player = DolphinPlayer()
        val folder = Folder()
        assertEquals(listOf(SaveFolderPart("", "GC/USA/Card A")), player.findSaveFolderParts("gci", disc(), folder))
        val filter = player.saveArchiveFilter("gci", disc())!!
        assertTrue(filter.accepts("arbitrary.gci", gci("GFZE8P")))
        assertFalse(filter.accepts("8P-GFZE-fzc.gci", gci("GZLE01")))
        assertFalse(filter.accepts("nested/save.gci", gci("GFZE8P")))
        assertFalse(filter.accepts("save.gci", ByteArray(63)))
        folder.ini = "[Core]\nSlotA = 1\n"
        assertNull(player.findSaveFolderParts("gci", disc(), folder))
        assertTrue(player.saveSyncState(folder) is SaveSyncState.NeedsSetup)
        folder.ini = "[Core]\nSlotA = 8\nGCIFolderAPath = /some/card\n"
        assertNull(player.findSaveFolderParts("gci", disc(), folder))
        folder.ini = "\uFEFF[core] # comment\nslota = 1\n"
        assertFalse(defaultFolderCard(folder))
        folder.ini = "[Core]\nSlotA = 1\nslota = 8\n"
        assertTrue(defaultFolderCard(folder))
        folder.ini = "[Core]\n [Other]\nSlotA = 1\n"
        assertFalse(defaultFolderCard(folder))
        assertTrue(ServiceLoader.load(PluginEntry::class.java).any { it is DolphinEntry })
    }

    @Test fun redundantPerGameCardSettingsStillUseTheDefaultFolder() {
        val player = DolphinPlayer()
        val folder = Folder().apply {
            ini = "[Core]\nSlotA = 8\nMemcardAPath = /unused/card.raw\n"
        }
        for (name in listOf("G.ini", "GFZ.ini", "GFZE8P.ini", "GFZE8Pr1.ini")) {
            folder.gameInis.clear()
            folder.gameInis[name] = "[Core]\nSlotA = 1\nSlotA = 8\nGCIFolderAPath = \"\"\nGCIFolderAPathOverride =\nMemcardAPath = /unused/card.raw\n"
            assertEquals(name, listOf(SaveFolderPart("", "GC/USA/Card A")), player.findSaveFolderParts("gci", disc(), folder))
        }
        folder.gameInis.clear()
        folder.gameInis["GFZE8P.ini"] = "[Video_Settings]\nSlotA = 1\nGCIFolderAPath = /irrelevant\n[Main.Core]\nSlotA = 8\n"
        assertTrue(defaultFolderCard(folder, "GFZE8P"))
        folder.gameInis["GFZE8P.ini"] = "[Main.Core]\nSlotA = 1\n"
        assertFalse(defaultFolderCard(folder, "GFZE8P"))
        folder.gameInis["GFZE8P.ini"] = "[Core]\nSlotA = 1\n"
        assertNull(player.findSaveFolderParts("gci", disc(), folder))
    }

    @Test fun gciPathSettingsAreStringsEvenWhenTheirTextLooksBoolean() {
        val folder = Folder()
        for (key in listOf("GCIFolderAPath", "GCIFolderAPathOverride")) {
            for (path in listOf("False", "false", "0", "True", "/custom/card")) {
                folder.ini = "[Core]\nSlotA = 8\n$key = $path\n"
                assertFalse("$key = $path", defaultFolderCard(folder))
                folder.ini = "[Core]\nSlotA = 8\n"
                folder.gameInis["GFZE8P.ini"] = "[Core]\n$key = $path\n"
                assertFalse("per-game $key = $path", defaultFolderCard(folder, "GFZE8P"))
            }
            folder.gameInis.clear()
        }
    }

    private fun disc(region: Int = 1, magic: Boolean = true) = object : GameFileReader {
        override fun read(offset: Long, length: Int) = ByteArray(0x460).also {
            "GFZE8P".toByteArray().copyInto(it)
            if (magic) byteArrayOf(0xc2.toByte(), 0x33, 0x9f.toByte(), 0x3d).copyInto(it, 0x1c)
            it[0x45b] = region.toByte()
        }.let { it.copyOfRange(offset.toInt(), minOf(it.size, offset.toInt() + length)) }
    }
    private fun gci(id: String) = ByteArray(64).also { id.toByteArray().copyInto(it) }
    private class Folder : SaveFolderAccess {
        var ini: String? = null
        val gameInis = linkedMapOf<String, String>()
        override fun list(path: String): List<SaveFolderEntry>? = when(path) {
            "Config" -> ini?.let { listOf(SaveFolderEntry("Dolphin.ini", false, it.length.toLong())) }.orEmpty()
            "GameSettings" -> gameInis.map { (name, text) -> SaveFolderEntry(name, false, text.length.toLong()) }
            else -> null
        }
        override fun read(path: String, limit: Int) = when {
            path == "Config/Dolphin.ini" -> ini
            path.startsWith("GameSettings/") -> gameInis[path.removePrefix("GameSettings/")]
            else -> null
        }?.toByteArray()
        override fun read(path: String, offset: Long, length: Int): ByteArray? = null
        override fun write(path: String, bytes: ByteArray): Boolean = error("read only")
        override fun createFolder(path: String): Boolean = error("read only")
        override fun moveToBackup(path: String): Boolean = error("read only")
    }
}
