package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.HomeBoard
import app.foldcade.language.decodeHome
import app.foldcade.language.encodeHome
import app.foldcade.language.onHome
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class GameNativeHomeTest {
    @After fun clearShelf() { Shelf.catalog = emptyList(); Shelf.gameNativeInstalled = false }

    @Test fun aCompletedFolderScanCanRemoveAnExportWithoutShortcutHostPermission() {
        val id = "gamenative.GOG_42"
        val store = SessionStore(MemoryPrefs())
        Shelf.catalog = listOf(ShelfGame(id, "Game", "", "pc", libraryId = "gamenative", remoteKey = "GOG_42", shortcut = false))
        val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        ShellController(store, plugins).apply { settleGameNative(); noteShelfChanged() }
        assertTrue(onHome(decodeHome(store.homeBoardRaw()), id))
        Shelf.catalog = emptyList()
        val restarted = ShellController(store, plugins)
        val read = GameNativeCatalogRead(shortcutsRead = false, hasGames = false, storedCatalog = false)
        assertFalse(read.settles(foldersRead = false, hasFolders = true))
        if (read.settles(foldersRead = true, hasFolders = true)) restarted.settleGameNative()
        restarted.noteShelfChanged()
        assertFalse(onHome(decodeHome(store.homeBoardRaw()), id))
    }

    @Test fun aPinGoesHomeWithNewGamesOffAndKeepsItsPlaceUntilTheSourcesSettle() {
        val id = "gamenative.SHORTCUT_app.gamenative_42"
        val store = SessionStore(MemoryPrefs())
        store.saveHomeBoard(encodeHome(HomeBoard(addNewToHome = false)))
        Shelf.catalog = listOf(ShelfGame(id, "Game", "", "pc", libraryId = "gamenative", remoteKey = "SHORTCUT_app.gamenative_42"))
        val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        val shell = ShellController(store, plugins)
        shell.settleGameNative()
        shell.noteShelfChanged()
        assertFalse(onHome(decodeHome(store.homeBoardRaw()), id))
        shell.placeGameNativePin(id)
        assertTrue(onHome(decodeHome(store.homeBoardRaw()), id))
        Shelf.catalog = emptyList()
        val restarted = ShellController(store, plugins)
        restarted.noteShelfChanged()
        assertTrue(onHome(decodeHome(store.homeBoardRaw()), id))
        restarted.settleGameNative()
        restarted.noteShelfChanged()
        assertFalse(onHome(decodeHome(store.homeBoardRaw()), id))
    }
}
