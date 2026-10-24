package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.SaveFolderSearch
import app.foldcade.api.plugin.StartDisplay
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LibraryReturnTest {
    private val game = Game("romm", "37", "playstation-2", Availability.Cached, "NFSU2")
    private val player = object : Player {
        override val id = "armsx2"
        override val displayName = "ARMSX2"
        override val platformId = "playstation-2"
        override val packageNames = emptyList<String>()
        override val needsLocalFile = true
        override val startDisplay = StartDisplay.PickerChoice
        override val occupiesBothDisplays = false
        override val requiresImportedGame = false
        override fun saveDeclarations(game: Game) = emptyList<SaveDeclaration>()
        override fun launchIntent(request: LaunchRequest): PlayerIntent = error("unused")
    }

    @Test
    fun aReturnUsesAFreshLookAndKeepsTheStoredOneWhenThatLookFindsNothing() {
        val docs = object : SaveDocuments {
            override fun displayName(uri: String) = "game.iso"
            override fun find(folderUri: String, name: String): String? = null
            override fun create(folderUri: String, name: String): String? = null
            override fun read(uri: String): ByteArray? = null
            override fun write(uri: String, bytes: ByteArray) = Unit
            override fun delete(uri: String) = false
        }
        val saves = PlayerSaves(docs, Marks(), Files.createTempDirectory("return-backups"), Files.createTempDirectory("return-handover"))
        val stored = SaveSpot("card1", "content://tree", "game.zip", search = SaveFolderSearch("memcards/card", "SLUS-21065"))
        val pending = LibraryReturn("romm", game, player, listOf(stored), "content://roms/game.iso")
        assertEquals(listOf(stored), returnSpots(saves, pending))
        val finding = object : Player by player {
            override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("card1", "content://tree"))
            override fun findSaveFolders(
                slot: String,
                game: app.foldcade.api.plugin.GameFileReader,
                folder: app.foldcade.api.plugin.SaveFolderAccess,
                forLaunch: Boolean,
            ): SaveFolderSearch? {
                check(!forLaunch)
                check(folder.write("memcards/card/note", byteArrayOf(1)).not())
                return SaveFolderSearch("memcards/mcd001.ps2", "SLUS-21065")
            }
        }
        val fresh = returnSpots(saves, pending.copy(player = finding))
        assertEquals("memcards/mcd001.ps2", fresh.single().search?.parent)
    }

    @Test
    fun aPlayerWithoutASearchKeepsTheSpotsStoredAtLaunch() {
        var asked = false
        val plain = object : Player by player {
            override fun saveDeclarations(game: Game): List<SaveDeclaration> {
                asked = true
                return listOf(SaveDeclaration("sav", "content://other"))
            }

            override fun saveFileName(slot: String, romFileName: String) = "changed.sav"
        }
        val stored = SaveSpot("sav", "content://tree", "Tetris.sav")
        val pending = LibraryReturn("romm", game, plain, listOf(stored), "content://roms/game.nds")
        assertEquals(listOf(stored), returnSpots(shelf(), pending))
        assertFalse(asked)
    }

    @Test
    fun aThrowOnTheWayBackKeepsTheSpotsStoredAtLaunch() {
        val throwing = object : Player by player {
            override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("card1", "content://tree"))
            override fun findSaveFolders(
                slot: String,
                game: app.foldcade.api.plugin.GameFileReader,
                folder: app.foldcade.api.plugin.SaveFolderAccess,
                forLaunch: Boolean,
            ): SaveFolderSearch? = error("no card")
        }
        val stored = SaveSpot("card1", "content://tree", "game.zip", search = SaveFolderSearch("memcards/card", "SLUS-21065"))
        val pending = LibraryReturn("romm", game, throwing, listOf(stored), "content://roms/game.iso")
        assertEquals(listOf(stored), returnSpots(shelf(), pending))
    }

    private fun shelf(): PlayerSaves {
        val docs = object : SaveDocuments {
            override fun displayName(uri: String) = "game.iso"
            override fun find(folderUri: String, name: String): String? = null
            override fun create(folderUri: String, name: String): String? = null
            override fun read(uri: String): ByteArray? = null
            override fun write(uri: String, bytes: ByteArray) = Unit
            override fun delete(uri: String) = false
        }
        return PlayerSaves(docs, Marks(), Files.createTempDirectory("return-backups"), Files.createTempDirectory("return-handover"))
    }

    private class Marks : SaveMarks {
        override fun mark(key: String): String? = null
        override fun setMark(key: String, hash: String) = Unit
        override fun clearMark(key: String) = Unit
    }
}
