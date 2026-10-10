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
import app.foldcade.language.HomeGrid
import app.foldcade.language.HostScreen
import app.foldcade.language.LaunchableApp
import app.foldcade.language.Meaning
import app.foldcade.language.Row
import app.foldcade.language.leftRows
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

        val rows = leftRows()
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        repeat(rows.indexOf(Row.Apps)) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        val opened = shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
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
        assertEquals(Copy.pin, rowLabelAt(shell, 0))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertTrue(shell.focusedGame()?.favorite == true)
        assertEquals(Copy.unpin, rowLabelAt(shell, 0))

        shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(HomeGrid.Apps, shell.model.homeGrid)
        assertEquals(0, shell.model.count)

        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        val gamesIndex = leftRows().indexOf(Row.AndroidGames)
        repeat(gamesIndex) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(HomeGrid.AndroidGames, shell.model.homeGrid)
        assertEquals(listOf("Settings", "Example"), titles(shell))

        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        val hiddenIndex = leftRows().indexOf(Row.HiddenApps)
        repeat(hiddenIndex) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(listOf("Azahar"), titles(shell))
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(0, shell.model.count)

        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        repeat(leftRows().indexOf(Row.Apps)) {
            shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom)
        }
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
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
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        val restored = SessionStore(prefs).appShelfState()
        assertTrue(restored.record(settings.packageName).favorite)
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
