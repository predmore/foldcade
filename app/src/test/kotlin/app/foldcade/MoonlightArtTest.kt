package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class MoonlightArtTest {
    private val shell = ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))

    @Test
    fun aMoonlightTileAsksByItsTitle() {
        val stream = ShelfGame(
            id = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6|881448767",
            title = "Desktop",
            shortText = "Moonlight",
            platformId = "moonlight",
            libraryId = "moonlight",
            remoteKey = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6|881448767",
        )
        val query = shell.artQuery(stream)!!
        // SteamGridDB takes only a game of the same name, so "Desktop" keeps its mark.
        assertEquals("Desktop", query.title)
        assertEquals(null, query.steamAppId)
    }
}
