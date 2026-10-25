package app.foldcade

import app.foldcade.api.plugin.*
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import app.foldcade.localfolder.LocalFolderEntry
import app.foldcade.plugins.melonds.MelonDsEntry
import app.foldcade.plugins.retroarch.RetroArchEntry
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class ShellSaveHealthTest {
    @Test fun retroArchSystemsShareOneStandingNoticeAndItsSettingsFix() {
        val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        plugins.register(LocalFolderEntry())
        val retro = RetroArchEntry()
        plugins.register(retro)
        var setupPlayers = emptyMap<String, String>()
        val shell = ShellController(SessionStore(MemoryPrefs()), plugins, onSaveHealthChanged = { setupPlayers = it })
        val players = retro.players.take(2)
        val entries = players.mapIndexed { index, player ->
            val game = Game("romm", index.toString(), player.platformId, Availability.RemoteOnly, "Game $index")
            GridEntry(game.remoteKey, game.label, "", game.platformId, null, false, game, "game$index.rom")
        }
        shell.ingestLibrary("romm", entries, emptyList())
        val setting = shell.model.playerSaves.single()
        assertEquals(mapOf(setting.playerId to setting.label), setupPlayers)
        for (player in players) assertEquals(setting.playerId, shell.saveNoticePlayerId(player.id))
    }

    @Test fun healthFollowsFoldedCopiesAndSurvivesTransientMarksThenClearsOnUpload() {
        val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        plugins.register(LocalFolderEntry())
        val melon = MelonDsEntry()
        plugins.register(melon)
        var setupPlayers = emptyMap<String, String>()
        val shell = ShellController(SessionStore(MemoryPrefs()), plugins, onSaveHealthChanged = { setupPlayers = it })
        val player = plugins.playersFor("nintendo-ds").first()
        val server = Game("romm", "42", "nintendo-ds", Availability.Cached, "Tetris")
        val other = server.copy(remoteKey = "43", label = "Puzzle")
        val local = server.copy(backendId = "local-folder", remoteKey = "tree", availability = Availability.LocalOnly)
        fun entry(game: Game, file: String) = GridEntry(game.remoteKey, game.label, "", game.platformId, null, true, game, file)
        shell.ingestLibrary("romm", listOf(entry(server, "Tetris.nds"), entry(other, "Puzzle.nds")), emptyList())
        shell.ingestLibrary("local-folder", listOf(entry(local, "Tetris.nds")), emptyList())
        val id = homeGameId("local-folder", "tree")
        assertEquals(SaveHealth.NotSyncing(SaveIssue.NoFolder), shell.saveHealthOf(id))
        assertEquals(mapOf(player.id to player.displayName), setupPlayers) // Two server games, one player notice.
        shell.noteSaveReachable(server, player.id, false)
        (player as SaveFolderHolder).bindSaveFolder("content://saves")
        shell.refreshPlayerSaves()
        assertEquals(SaveHealth.Fine, shell.saveHealthOf(id))
        assertTrue(setupPlayers.isEmpty())
        shell.setUnsyncedSaves("romm", setOf("42"))
        assertEquals(SaveHealth.Waiting, shell.saveHealthOf(id))
        val until = shell.noteActivity("romm", "42", GameActivity.Synced(SyncResult(SyncOutcome.Queued)), 100)!!
        assertEquals(SaveHealth.Waiting, shell.saveHealthOf(id))
        shell.expireActivity("romm", "42", until)
        assertEquals(SaveHealth.Waiting, shell.saveHealthOf(id))
        shell.setUnsyncedSaves("romm", emptySet())
        assertEquals(SaveHealth.Fine, shell.saveHealthOf(id))
        shell.noteSaveReachable(server, player.id, false)
        assertEquals(SaveHealth.NotSyncing(SaveIssue.NoSlot), shell.saveHealthOf(id))
        shell.noteSaveReachable(server, player.id, true)
        assertEquals(SaveHealth.Fine, shell.saveHealthOf(id))
    }
}
