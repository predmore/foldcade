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

    @Test fun deletingAPinFirstGameKeepsItsExportOffHomeAcrossOtherMergesAndRestart() {
        val pin = app.foldcade.plugins.gamenative.gameNativeShortcut("app.gamenative", "game_42", "Game", true, true)!!
        val export = app.foldcade.plugins.gamenative.CatalogGame("Game", 42, "STEAM")
        val merged = app.foldcade.plugins.gamenative.mergeGameNativeGames(
            app.foldcade.plugins.gamenative.restoreGameNativePins(listOf(pin), listOf(pin)), listOf(export),
        ).single()
        val entry = app.foldcade.plugins.gamenative.GameNativeEntry()
        entry.library.confirm(listOf(merged))
        val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()).apply { register(entry) }
        val store = SessionStore(MemoryPrefs())
        val id = "gamenative.${pin.remoteKey}"
        val exportId = "gamenative.${export.remoteKey}"
        Shelf.catalog = listOf(ShelfGame(id, "Game", "", "pc", libraryId = "gamenative", remoteKey = pin.remoteKey))
        var time = 0L
        val deleted = mutableListOf<String>()
        val shell = ShellController(store, plugins, nowMillis = { time }, onGameNativeRemoved = { deleted += it })
        shell.settleGameNative()
        shell.noteShelfChanged()
        val folderIndex = (0 until shell.model.count).first { shell.homeFace(it)?.title == "GameNative" }
        val columns = app.foldcade.language.GridStrip.column(folderIndex) - app.foldcade.language.GridStrip.column(shell.model.focus.cellIndex)
        repeat(kotlin.math.abs(columns)) { shell.onMeaning(if (columns > 0) app.foldcade.language.Meaning.MoveRight else app.foldcade.language.Meaning.MoveLeft, app.foldcade.language.HostScreen.Bottom) }
        val rows = app.foldcade.language.GridStrip.row(folderIndex) - app.foldcade.language.GridStrip.row(shell.model.focus.cellIndex)
        repeat(kotlin.math.abs(rows)) { shell.onMeaning(if (rows > 0) app.foldcade.language.Meaning.MoveDown else app.foldcade.language.Meaning.MoveUp, app.foldcade.language.HostScreen.Bottom) }
        shell.onMeaning(app.foldcade.language.Meaning.Activate, app.foldcade.language.HostScreen.Bottom)
        shell.onMeaning(app.foldcade.language.Meaning.Options, app.foldcade.language.HostScreen.Bottom)
        val menuRows = app.foldcade.language.panelRows(shell.model.panel!!, shell.model)
        walkPanelTo(shell, menuRows.indexOf(app.foldcade.language.Row.DeleteShortcut), app.foldcade.language.HostScreen.Bottom)
        shell.onMeaning(app.foldcade.language.Meaning.Activate, app.foldcade.language.HostScreen.Bottom)
        assertNotNull(shell.model.deleteHold)
        shell.setPromptHeld(app.foldcade.language.PromptKey.FaceA, true)
        time += app.foldcade.language.DELETE_HOLD_MILLIS
        shell.tickRecovery()
        assertEquals(listOf(pin.remoteKey), deleted)
        repeat(2) { shell.noteShelfChanged() }
        assertTrue(exportId in decodeHome(store.homeBoardRaw()).offHome)
        entry.library.confirm(listOf(export))
        Shelf.catalog = listOf(ShelfGame(exportId, "Game", "", "pc", libraryId = "gamenative", remoteKey = export.remoteKey, shortcut = false))
        shell.noteShelfChanged()
        assertFalse(onHome(decodeHome(store.homeBoardRaw()), exportId))
        assertTrue(exportId in decodeHome(store.homeBoardRaw()).offHome)
        val restarted = ShellController(store, plugins)
        restarted.settleGameNative()
        restarted.noteShelfChanged()
        assertFalse(onHome(decodeHome(store.homeBoardRaw()), exportId))
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
