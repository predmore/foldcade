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
    fun controllerOpensAppsPinsAndMovesWithoutBlockingOnSaves() {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        host.register(entry(SaveThrowingPlayer(listOf(azahar.packageName))))
        val shell = ShellController(SessionStore(MemoryPrefs()), host)
        shell.setHomeRoleHeld(true)
        shell.setInstalledApps(listOf(game, settings, azahar), host.playerPackageNames())

        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        val opened = choose(shell, Row.Apps)
        assertNull(opened)
        assertEquals(HomeGrid.Apps, shell.model.homeGrid)
        assertNull(shell.model.panel)
        assertEquals(0, shell.model.focus.cellIndex)
        assertNull(shell.model.focus.chrome)
        assertEquals(1, shell.model.count)
        assertEquals("Settings", shell.focusedGame()?.title)
        assertEquals("android:com.android.settings", shell.focusedGame()?.id)
        assertFalse(host.openInstalledApp(settings.packageName).needsSaveFolder)

        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.PinApp)
        assertTrue(shell.focusedGame()?.favorite == true)
        assertEquals(Copy.keepOnTop, app.foldcade.language.rowLabel(Row.PinApp, shell.model))

        repeat(panelRows(shell.model.panel!!, shell.model).indexOf(Row.MoveApp)) {
            shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        }
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(HomeGrid.Apps, shell.model.homeGrid)
        assertEquals(0, shell.model.count)

        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        choose(shell, Row.AndroidGames)
        assertEquals(HomeGrid.AndroidGames, shell.model.homeGrid)
        assertEquals(listOf("Settings", "Example"), titles(shell))

        // Hidden apps are under Edit home.
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        choose(shell, Row.EditHome)
        val hiddenIndex = panelRows(shell.model.panel!!, shell.model).indexOf(Row.HiddenApps)
        repeat(hiddenIndex) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(listOf("Azahar"), titles(shell))
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(0, shell.model.count)

        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        choose(shell, Row.Apps)
        assertEquals(listOf("Azahar"), titles(shell))
    }

    @Test
    fun shelfChoicesRoundTrip() {
        val prefs = MemoryPrefs()
        val store = SessionStore(prefs)
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.setInstalledApps(listOf(settings), emptySet())
        shell.showHomeGrid(HomeGrid.Apps)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.PinApp)
        val restored = SessionStore(prefs).appShelfState()
        assertTrue(restored.record(settings.packageName).favorite)
    }

    @Test
    fun bAfterLOneAppsReturnsToHomeWhenOpenedFromAFolder() {
        val shell = ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.setInstalledApps(listOf(settings), emptySet())
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals("Settings", shell.focusedGame()?.title)

        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        choose(shell, Row.Apps)
        assertEquals(HomeGrid.Apps, shell.model.homeGrid)
        assertNull(shell.onMeaning(Meaning.Back, HostScreen.Bottom))
        assertEquals(HomeGrid.StandIns, shell.model.homeGrid)
        assertTrue(shell.atRest())
        assertTrue("Android Apps" in titles(shell))
    }

    @Test
    fun addAppToHomeKeepsItInAppsAndRemembersOneHomeTile() {
        val prefs = MemoryPrefs()
        val store = SessionStore(prefs)
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.setInstalledApps(listOf(settings, game), emptySet())
        shell.showHomeGrid(HomeGrid.Apps)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.PinTile)
        assertNull(shell.model.panel)
        assertEquals(HomeGrid.Apps, shell.model.homeGrid)
        assertEquals("Settings", shell.focusedGame()?.title)
        assertNull(store.appShelfState().record(settings.packageName).shelf)
        // A pin on Home, and still in its folder: one tile on the root, one in the folder.
        val placed = decodeHome(store.homeBoardRaw())
        assertTrue(androidHomeId(settings.packageName) in placed.slots)
        assertEquals(1, placed.slots.count { it == androidHomeId(settings.packageName) })
        assertEquals(listOf(HOME_ANDROID_APPS), placed.folders.values.filter { androidHomeId(settings.packageName) in it.slots }.map { it.id })

        val restarted = ShellController(SessionStore(prefs), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        restarted.setInstalledApps(listOf(settings, game), emptySet())
        assertTrue("Settings" in titles(restarted))
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertTrue("Settings" in titles(shell))
        assertTrue(shell.atRest())
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
        shell.showHomeGrid(HomeGrid.Apps)
        shell.showHomeGrid(HomeGrid.HiddenApps)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.ShowHidden)
        shell.showHomeGrid(HomeGrid.Apps)
        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        assertEquals("Settings", shell.focusedGame()?.title)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        choose(shell, Row.AddToCollection)
        choose(shell, Row.Collection(folder.id, folder.name))

        val placed = decodeHome(store.homeBoardRaw())
        assertEquals(listOf(id), placed.folders[folder.id]?.slots)
        assertFalse(id in placed.hidden)
        assertFalse(placed.addNewToHome)
        assertEquals(HomeGrid.Apps, shell.model.homeGrid)
        assertEquals(listOf("Notes", "Settings"), titles(shell))
        assertFalse(androidHomeId(notes.packageName) in placed.slots)
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

    private fun rowLabelAt(shell: ShellController, index: Int): String {
        val panel = shell.model.panel ?: error("panel closed")
        val rows = app.foldcade.language.panelRows(panel, shell.model)
        return app.foldcade.language.rowLabel(rows[index], shell.model)
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
