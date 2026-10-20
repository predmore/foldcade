package app.foldcade

import android.content.pm.ApplicationInfo
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.StartDisplay
import app.foldcade.host.PluginHost
import app.foldcade.language.Copy
import app.foldcade.language.HOME_ALL
import app.foldcade.language.HOME_ANDROID_APPS
import app.foldcade.language.HOME_ANDROID_GAMES
import app.foldcade.language.HOME_ROOT
import app.foldcade.language.HomeBoard
import app.foldcade.language.HomeFolder
import app.foldcade.language.HomeGrid
import app.foldcade.language.HostScreen
import app.foldcade.language.LaunchableApp
import app.foldcade.language.Meaning
import app.foldcade.language.Effect
import app.foldcade.language.Row
import app.foldcade.language.tiled
import app.foldcade.language.androidHomeId
import app.foldcade.language.decodeHome
import app.foldcade.language.encodeHome
import app.foldcade.language.leftRows
import app.foldcade.language.panelRows
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidShelfHostTest {
    private val game = LaunchableApp("com.example.game", "Example", systemGame = true)
    private val settings = LaunchableApp("com.android.settings", "Settings", systemGame = false)
    private val azahar = LaunchableApp("org.azahar_emu.azahar", "Azahar", systemGame = false)

    @Test
    fun manifestListsLaunchersWithoutQueryAllPackages() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("<queries>"))
        assertTrue(manifest.contains("android.intent.action.MAIN"))
        assertTrue(manifest.contains("android.intent.category.LAUNCHER"))
        assertFalse(manifest.contains("android.permission.QUERY_ALL_PACKAGES"))
        val source = File("src/main/kotlin/app/foldcade/InstalledApps.kt").readText()
        assertTrue(source.contains("queryIntentActivities"))
        assertTrue(source.contains("ACTION_MAIN"))
        assertTrue(source.contains("CATEGORY_LAUNCHER"))
        assertFalse(source.contains("QUERY_ALL_PACKAGES"))
        assertFalse(source.contains("MATCH_ALL"))
    }

    @Suppress("DEPRECATION")
    @Test
    fun systemGameUsesCategoryOrLegacyFlag() {
        assertTrue(isSystemGame(ApplicationInfo.CATEGORY_GAME, 0))
        assertTrue(isSystemGame(ApplicationInfo.CATEGORY_UNDEFINED, ApplicationInfo.FLAG_IS_GAME))
        assertFalse(isSystemGame(ApplicationInfo.CATEGORY_UNDEFINED, 0))
        assertFalse(isSystemGame(ApplicationInfo.CATEGORY_AUDIO, 0))
    }

    @Test
    fun launchablesSkipThisAppAndBlankPackages() {
        val rows = listOf(
            ScannedLaunchable("app.foldcade", "Foldcade", ApplicationInfo.CATEGORY_UNDEFINED, 0),
            ScannedLaunchable("", "Blank", ApplicationInfo.CATEGORY_GAME, 0),
            ScannedLaunchable("com.android.settings", "", ApplicationInfo.CATEGORY_UNDEFINED, 0),
            ScannedLaunchable("com.android.settings", "Settings again", ApplicationInfo.CATEGORY_UNDEFINED, 0),
            ScannedLaunchable("com.example.game", "Example", ApplicationInfo.CATEGORY_GAME, 0),
        )
        val listed = launchablesFrom(rows, ownPackage = "app.foldcade")
        assertEquals(listOf("com.android.settings", "com.example.game"), listed.map { it.packageName })
        assertEquals("com.android.settings", listed.first().label)
        assertTrue(listed.last().systemGame)
        assertFalse(listed.first().systemGame)
    }

    @Test
    fun installedAppLaunchDoesNotAskForASaveFolder() {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        host.register(entry(SaveThrowingPlayer(listOf(azahar.packageName))))
        val opened = host.openInstalledApp(azahar.packageName)
        assertEquals("android:${azahar.packageName}", opened.sessionId)
        assertFalse(opened.needsSaveFolder)
        assertEquals(setOf(azahar.packageName), host.playerPackageNames())
    }

    @Test
    fun lOneAppsAndGamesOpenTheAndroidFoldersWhereAppsMove() {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        host.register(entry(SaveThrowingPlayer(listOf(azahar.packageName))))
        val store = SessionStore(MemoryPrefs())
        val shell = ShellController(store, host)
        shell.setHomeRoleHeld(true)
        shell.setInstalledApps(listOf(game, settings, azahar), host.playerPackageNames())

        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        assertNull(choose(shell, Row.Apps))
        // Apps is the board's Android Apps folder, not a grid of its own.
        assertEquals(HomeGrid.StandIns, shell.model.homeGrid)
        assertFalse(shell.model.atLibraryRoot)
        assertNull(shell.model.panel)
        assertEquals(0, shell.model.focus.cellIndex)
        assertEquals(listOf("Settings"), titles(shell))
        assertEquals("android:com.android.settings", shell.focusedGame()?.id)
        assertFalse(host.openInstalledApp(settings.packageName).needsSaveFolder)

        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        val rows = panelRows(shell.model.panel!!, shell.model)
        assertTrue(Row.MoveApp in rows)
        assertTrue(Row.Hide in rows)
        assertEquals(Copy.moveToGames, app.foldcade.language.rowLabel(Row.MoveApp, shell.model))
        choose(shell, Row.MoveApp)
        // Its last app gone, Android Apps leaves Home, so the grid is back on the root.
        assertTrue(shell.model.atLibraryRoot)
        assertFalse("Android Apps" in titles(shell))
        val board = decodeHome(store.homeBoardRaw())
        assertTrue(androidHomeId(settings.packageName) in board.folders.getValue(HOME_ANDROID_GAMES).slots)
        assertFalse(androidHomeId(settings.packageName) in board.folders[HOME_ANDROID_APPS]?.slots.orEmpty())

        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        choose(shell, Row.AndroidGames)
        assertFalse(shell.model.atLibraryRoot)
        assertEquals(setOf("Example", "Settings"), titles(shell).filter { it.isNotEmpty() }.toSet())
        // Moved to Games, it is a game: All lists it, and still does once it is hidden.
        shell.openHomeAll()
        assertTrue("Settings" in titles(shell))
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        choose(shell, Row.AndroidGames)
        // The grid fills each column top to bottom, so Settings, the second tile, is under the first.
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        assertEquals(androidHomeId(settings.packageName), shell.focusedGame()?.id)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.Hide)
        assertTrue(androidHomeId(settings.packageName) in decodeHome(store.homeBoardRaw()).hidden)
        shell.openHomeAll()
        assertTrue("Settings" in titles(shell))
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        choose(shell, Row.AndroidGames)
        // Back from it lands on Home, as from any folder.
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertTrue(shell.model.atLibraryRoot)
        assertTrue(shell.atRest())

        // The emulator is in Emulators, not hidden: Hidden lists only Settings.
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        choose(shell, Row.EditHome)
        choose(shell, Row.Hidden)
        assertEquals(HomeGrid.Hidden, shell.model.homeGrid)
        assertEquals(listOf("Settings"), titles(shell))
    }

    @Test
    fun lOneGamesOpensARetiredFolderWithoutPuttingItBackOnHome() {
        val store = SessionStore(MemoryPrefs())
        val id = androidHomeId(game.packageName)
        store.saveHomeBoard(encodeHome(HomeBoard(
            slots = listOf(HOME_ALL),
            folders = mapOf(HOME_ANDROID_GAMES to HomeFolder(HOME_ANDROID_GAMES, "Android Games", bucket = app.foldcade.language.HomeKind.AndroidGame, slots = listOf(id))),
            retired = setOf(HOME_ANDROID_GAMES),
        )))
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.setInstalledApps(listOf(game), emptySet())
        assertFalse(HOME_ANDROID_GAMES in decodeHome(store.homeBoardRaw()).slots)

        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        choose(shell, Row.AndroidGames)
        assertFalse(shell.model.atLibraryRoot)
        assertEquals(listOf("Example"), titles(shell))
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertTrue(shell.model.atLibraryRoot)
        val board = decodeHome(store.homeBoardRaw())
        assertFalse(HOME_ANDROID_GAMES in board.slots)
        assertTrue(HOME_ANDROID_GAMES in board.retired)
    }

    @Test
    fun lOneAppsMakesTheFolderWhenTheBoardHasNone() {
        val store = SessionStore(MemoryPrefs())
        // New games stay off Home, so no app was filed and the board has no Android Apps folder.
        store.saveHomeBoard(encodeHome(HomeBoard(slots = listOf(HOME_ALL), addNewToHome = false)))
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.setInstalledApps(listOf(settings), emptySet())
        assertFalse(HOME_ANDROID_APPS in decodeHome(store.homeBoardRaw()).folders)

        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        assertNull(choose(shell, Row.Apps))
        assertEquals(HomeGrid.StandIns, shell.model.homeGrid)
        assertNull(shell.model.panel)
        assertTrue(HOME_ANDROID_APPS in decodeHome(store.homeBoardRaw()).folders)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertTrue(shell.atRest())
    }

    @Test
    fun appsHiddenOnAShelfAreHiddenOnHomeOnce() {
        val prefs = MemoryPrefs()
        prefs.edit()
            .putStringSet("android_shelf_hidden", setOf(settings.packageName))
            .putStringSet("android_shelf_favorites", setOf(game.packageName))
            .putStringSet("android_shelf_games", setOf(settings.packageName))
            .apply()
        val shell = ShellController(SessionStore(prefs), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.setInstalledApps(listOf(settings, game), emptySet())
        val id = androidHomeId(settings.packageName)
        assertTrue(id in decodeHome(SessionStore(prefs).homeBoardRaw()).hidden)
        listOf("android_shelf_hidden", "android_shelf_favorites", "android_shelf_games", "android_shelf_apps", "android_shelf_shown")
            .forEach { assertFalse(it, prefs.contains(it)) }

        shell.showHomeGrid(HomeGrid.Hidden)
        assertEquals(listOf("Settings"), titles(shell))
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.ShowHidden)
        assertFalse(id in decodeHome(SessionStore(prefs).homeBoardRaw()).hidden)

        // The next launch has nothing left to take, so Settings stays shown.
        val restarted = ShellController(SessionStore(prefs), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        restarted.setInstalledApps(listOf(settings, game), emptySet())
        assertFalse(id in decodeHome(SessionStore(prefs).homeBoardRaw()).hidden)
        restarted.showHomeGrid(HomeGrid.Hidden)
        assertEquals(0, restarted.model.count)
    }

    @Test
    fun hideFromAnAndroidFolderIsUndone() {
        val store = SessionStore(MemoryPrefs())
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.setInstalledApps(listOf(settings, game), emptySet())
        shell.openHomeFolder(HOME_ANDROID_APPS)
        assertEquals(listOf("Settings"), titles(shell))

        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        // Hide has no X: X takes a tile off its spot, and a folder keeps every game of its kind.
        assertNull(app.foldcade.language.chordIndex(panelRows(shell.model.panel!!, shell.model), Meaning.ActivateBottom))
        choose(shell, Row.Hide)
        val id = androidHomeId(settings.packageName)
        assertTrue(id in decodeHome(store.homeBoardRaw()).hidden)
        assertEquals(Copy.hidden, shell.model.undoVerb)

        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.Undo)
        assertFalse(id in decodeHome(store.homeBoardRaw()).hidden)
        assertEquals("Settings", titles(shell).firstOrNull { it.isNotEmpty() })
    }

    @Test
    fun addToHomePinsAnAppAndUnpinningItIsUndone() {
        val prefs = MemoryPrefs()
        val store = SessionStore(prefs)
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.setInstalledApps(listOf(settings, game), emptySet())
        shell.openHomeFolder(HOME_ANDROID_APPS)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.AddToCollection)
        val home = Row.Collection(HOME_ROOT, Copy.home)
        // Home is Add to's first choice.
        assertEquals(home, panelRows(shell.model.panel!!, shell.model).first())
        choose(shell, home)
        // Add to stays open, so the next choice is one press away.
        assertNotNull(shell.model.panel)
        val id = androidHomeId(settings.packageName)
        // A pin on Home, and still in its folder: one tile on the root, one in the folder.
        val placed = decodeHome(store.homeBoardRaw())
        assertEquals(1, placed.slots.count { it == id })
        assertEquals(listOf(HOME_ANDROID_APPS), placed.folders.values.filter { id in it.slots }.map { it.id })
        assertNull(shell.model.undoVerb)

        val restarted = ShellController(SessionStore(prefs), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        restarted.setInstalledApps(listOf(settings, game), emptySet())
        assertTrue("Settings" in titles(restarted))

        choose(shell, home)
        assertFalse(id in decodeHome(store.homeBoardRaw()).slots)
        assertEquals(Copy.unpinned, shell.model.undoVerb)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertNull(shell.model.panel)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.Undo)
        assertEquals(1, decodeHome(store.homeBoardRaw()).slots.count { it == id })
    }

    @Test
    fun addAppToAUserFolderRestoresAnAppRemovedFromHome() {
        val prefs = MemoryPrefs()
        val store = SessionStore(prefs)
        val folder = HomeFolder("home.user.1", "Utilities", userMade = true)
        val id = androidHomeId(settings.packageName)
        store.saveHomeBoard(encodeHome(HomeBoard(
            slots = listOf(HOME_ALL, folder.id),
            folders = mapOf(folder.id to folder),
            hidden = setOf(id),
            addNewToHome = false,
        )))
        val notes = LaunchableApp("com.example.notes", "Notes", systemGame = false)
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.setInstalledApps(listOf(settings, notes), emptySet())
        shell.showHomeGrid(HomeGrid.Hidden)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.ShowHidden)
        shell.openHomeFolder(HOME_ANDROID_APPS)
        assertEquals(listOf("Settings"), titles(shell))
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.AddToCollection)
        choose(shell, Row.Collection(folder.id, folder.name))

        val placed = decodeHome(store.homeBoardRaw())
        assertEquals(listOf(id), placed.folders[folder.id]?.slots)
        assertFalse(id in placed.hidden)
        assertFalse(placed.addNewToHome)
        assertFalse(androidHomeId(notes.packageName) in placed.slots)
        assertFalse(placed.folders.values.any { androidHomeId(notes.packageName) in it.slots })
        shell.setInstalledApps(listOf(settings, notes), emptySet())
        assertEquals(listOf(id), decodeHome(store.homeBoardRaw()).folders[folder.id]?.slots)
    }

    /** Walks the D-pad to [row], across L1's tiles where it has to, and activates it. */
    private fun choose(shell: ShellController, row: Row): Effect? {
        val panel = shell.model.panel ?: error("panel closed")
        val rows = panelRows(panel, shell.model)
        val index = rows.indexOf(row)
        check(index >= 0) { "missing row $row" }
        repeat(rows.size * 2) {
            val at = shell.model.panel?.index ?: return@repeat
            if (at == index) return@repeat
            val step = when {
                rows[at].tiled() && row.tiled() -> if (index > at) Meaning.MoveRight else Meaning.MoveLeft
                index > at -> Meaning.MoveDown
                else -> Meaning.MoveUp
            }
            shell.onMeaning(step, HostScreen.Bottom)
        }
        check(shell.model.panel?.index == index) { "could not reach $row" }
        return shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
    }

    private fun titles(shell: ShellController): List<String> {
        return List(shell.model.count) { index -> shell.tileFromOrder(index)?.title ?: "" }
    }

    private fun entry(player: Player) = object : PluginEntry {
        override val apiVersion = PLUGIN_API_VERSION
        override val players = listOf(player)
    }
}

private class SaveThrowingPlayer(override val packageNames: List<String>) : Player {
    override val id = "player.azahar"
    override val displayName = "Azahar"
    override val platformId = "nintendo-3ds"
    override val needsLocalFile = true
    override val startDisplay = StartDisplay.Primary
    override val occupiesBothDisplays = true
    override val requiresImportedGame = false

    override fun saveDeclarations(game: Game): List<SaveDeclaration> = error("missing save folder")

    override fun launchIntent(request: LaunchRequest): PlayerIntent = error("missing save folder")
}
