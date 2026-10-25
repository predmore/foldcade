package app.foldcade

import app.foldcade.api.plugin.*
import app.foldcade.plugins.melonds.MelonDsEntry
import app.foldcade.plugins.retroarch.RetroArchEntry
import app.foldcade.plugins.duckstation.DuckStationEntry
import app.foldcade.plugins.azahar.AzaharEntry
import app.foldcade.plugins.armsx2.ArmsX2Entry
import app.foldcade.plugins.aps3e.Aps3eEntry
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RemoteSaveSpotsTest {
    @Test fun eachBundledPlayerResolvesOnlyWhatItsFilenameProves() {
        val root = Files.createTempDirectory("remote-save-spots")
        val docs = object : SaveDocuments {
            override fun displayName(uri: String): String? = error("No ROM document may be opened")
            override fun find(folderUri: String, name: String): String? = null
            override fun create(folderUri: String, name: String): String? = error("Read only")
            override fun read(uri: String): ByteArray? = error("No ROM or save bytes needed")
            override fun write(uri: String, bytes: ByteArray) = error("Read only")
            override fun delete(uri: String): Boolean = error("Read only")
            override fun children(folderUri: String, path: String): List<SaveChild> = emptyList()
        }
        val marks = object : SaveMarks {
            override fun mark(key: String): String? = null
            override fun setMark(key: String, hash: String) = error("Read only")
            override fun clearMark(key: String) = error("Read only")
        }
        try {
            val saves = PlayerSaves(docs, marks, root.resolve("backups"), root.resolve("handover"))
            val melon = MelonDsEntry().player
            val retro = RetroArchEntry().players.first()
            val duck = DuckStationEntry().player.apply { bindHostedFolder("content://saves", true) }
            val azahar = AzaharEntry().player
            val arms = ArmsX2Entry().player
            val ps3 = Aps3eEntry().player
            for ((player, resolves) in listOf(melon to true, retro to true, duck to true, azahar to false, arms to false, ps3 to false)) {
                (player as SaveFolderHolder).bindSaveFolder("content://saves")
                val game = Game("romm", "3", player.platformId, Availability.RemoteOnly, "Game")
                val spots = saves.spotsForFileName(player, game, "Game.nds")
                assertEquals(player.id, resolves, spots.isNotEmpty())
                if (resolves) assertEquals(1, spots.size)
            }
            assertEquals("Game.sav", saves.spotsForFileName(melon, Game("romm", "3", melon.platformId, Availability.RemoteOnly, "Game"), "Game.nds").single().fileName)
        } finally { root.toFile().deleteRecursively() }
    }
}
