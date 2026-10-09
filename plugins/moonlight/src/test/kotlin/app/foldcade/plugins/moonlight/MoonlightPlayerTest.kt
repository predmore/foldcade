package app.foldcade.plugins.moonlight

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.PlayerExtra
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.StartDisplay
import java.util.ServiceLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonlightPlayerTest {
    private val host = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6"
    private val game = Game(
        backendId = MoonlightLibrary.ID,
        remoteKey = "$host|123",
        platformId = MoonlightPlatform.ID,
        availability = Availability.LocalOnly,
        label = "Desktop",
    )

    @Test
    fun serviceFileLoadsTheEntry() {
        val entry = ServiceLoader.load(
            app.foldcade.api.plugin.PluginEntry::class.java,
            MoonlightPlayerTest::class.java.classLoader,
        ).filterIsInstance<MoonlightEntry>().single()
        assertEquals(MoonlightPlatform.ID, entry.platforms.single().id)
        assertTrue(entry.platforms.single().extensions.isEmpty())
        assertEquals(MoonlightPlayer.ID, entry.players.single().id)
        assertTrue(entry.library === entry.libraries.single())
        assertTrue(entry.metadataProviders.isEmpty())
    }

    @Test
    fun officialClientTakesOneScreen() {
        val player = MoonlightPlayer()
        assertEquals(listOf("com.limelight"), player.packageNames)
        assertEquals("moonlight", player.id)
        assertEquals("moonlight", player.platformId)
        assertFalse(player.needsLocalFile)
        assertFalse(player.occupiesBothDisplays)
        assertFalse(player.requiresImportedGame)
        assertEquals(StartDisplay.PickerChoice, player.startDisplay)
        assertFalse(SaveFolderHolder::class.java.isAssignableFrom(player.javaClass))
        assertTrue(player.saveDeclarations(game).isEmpty())
    }

    @Test
    fun shortcutTrampolineSendsHostUuidAndStringAppId() {
        val intent = MoonlightPlayer().launchIntent(
            LaunchRequest(
                game = game,
                target = LaunchTarget.AppRef(
                    mapOf(MoonlightPlayer.HOST_UUID to host, MoonlightPlayer.APP_ID to "123"),
                ),
                resolvedPackage = MoonlightPlayer.OFFICIAL_PACKAGE,
            ),
        )
        assertEquals("com.limelight", intent.packageName)
        assertEquals(SHORTCUT_TRAMPOLINE, intent.componentClass)
        assertEquals(MoonlightPlayer.ACTION_VIEW, intent.action)
        assertNull(intent.dataUri)
        assertNull(intent.mimeType)
        assertFalse(intent.grantReadUri)
        assertEquals(setOf(LaunchFlag.NewTask), intent.flags)
        assertEquals(2, intent.extras.size)
        val uuid = intent.extras[0] as PlayerExtra.Text
        val appId = intent.extras[1] as PlayerExtra.Text
        assertEquals("UUID", uuid.key)
        assertEquals(host, uuid.value)
        assertEquals("AppId", appId.key)
        assertEquals("123", appId.value)
        assertTrue(intent.extras.none { it is PlayerExtra.Integer })
    }

    @Test
    fun aFileOrAnotherPackageIsRefused() {
        val player = MoonlightPlayer()
        val refused = listOf(
            LaunchRequest(
                game = game,
                target = LaunchTarget.ContentUri("content://games/title"),
                resolvedPackage = player.packageNames.first(),
            ),
            LaunchRequest(
                game = game,
                target = LaunchTarget.AppRef(mapOf(MoonlightPlayer.HOST_UUID to host)),
                resolvedPackage = player.packageNames.first(),
            ),
            LaunchRequest(
                game = game,
                target = LaunchTarget.AppRef(
                    mapOf(MoonlightPlayer.HOST_UUID to host, MoonlightPlayer.APP_ID to "123"),
                ),
                resolvedPackage = "com.limelight.unofficial",
            ),
            LaunchRequest(
                game = game,
                target = LaunchTarget.AppRef(
                    mapOf(
                        MoonlightPlayer.HOST_UUID to "/data/data/com.limelight/databases/computers.db",
                        MoonlightPlayer.APP_ID to "123",
                    ),
                ),
                resolvedPackage = player.packageNames.first(),
            ),
        )
        refused.forEach { request ->
            val error = runCatching { player.launchIntent(request) }.exceptionOrNull()
            assertTrue(error is PluginException.NotFound)
        }
    }

    @Test
    fun shortcutParserDropsComputerShortcutsAndDatabasePaths() {
        val parsed = moonlightApp(host, "123", "Desktop")
        assertEquals(host, parsed?.hostUuid)
        assertEquals("123", parsed?.appId)
        assertEquals("Desktop", parsed?.label)
        assertNull(moonlightApp(host, null, "Living room"))
        assertNull(moonlightApp(host, "", "Living room"))
        assertNull(moonlightApp("content://com.limelight/computers", "1", "Desktop"))
        assertNull(moonlightApp("/data/data/com.limelight/databases/computers.db", "1", "Desktop"))
        assertEquals("123", moonlightApp(host, "123", "  ")?.label)
    }
}
