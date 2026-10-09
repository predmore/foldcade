package app.foldcade.plugins.gamenative

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.PlayerExtra
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.StartDisplay
import java.util.ServiceLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameNativePlayerTest {
    private val game = Game(
        backendId = GameNativeLibrary.ID,
        remoteKey = "STEAM_730",
        platformId = PcPlatform.ID,
        availability = Availability.LocalOnly,
        label = "Counter-Strike",
    )

    @Test
    fun serviceFileLoadsTheEntryWithoutMetadata() {
        val entry = ServiceLoader.load(
            app.foldcade.api.plugin.PluginEntry::class.java,
            GameNativePlayerTest::class.java.classLoader,
        ).filterIsInstance<GameNativeEntry>().single()
        assertEquals(PcPlatform.ID, entry.platforms.single().id)
        assertEquals(GameNativePlayer.ID, entry.players.single().id)
        assertEquals(GameNativeLibrary.ID, entry.libraries.single().id)
        assertTrue(entry.metadataProviders.isEmpty())
        assertTrue(entry.platforms.single().extensions.isEmpty())
        assertTrue(entry.platforms.single().aliases.isEmpty())
    }

    @Test
    fun oneScreenAndBothApplicationIds() {
        val player = GameNativePlayer()
        assertEquals(
            listOf(GameNativePlayer.VANILLA_PACKAGE, GameNativePlayer.GOLD_PACKAGE),
            player.packageNames,
        )
        assertFalse(player.occupiesBothDisplays)
        assertEquals(StartDisplay.PickerChoice, player.startDisplay)
        assertFalse(player.needsLocalFile)
        assertTrue(player.requiresImportedGame)
        assertEquals(PcPlatform.ID, player.platformId)
        assertTrue(player.saveDeclarations(game).isEmpty())
    }

    @Test
    fun launchSendsAnIntAppIdAndAGameSource() {
        val intent = GameNativePlayer().launchIntent(
            LaunchRequest(
                game = game,
                target = LaunchTarget.AppRef(
                    mapOf("app_id" to "730", "game_source" to "steam"),
                ),
                resolvedPackage = GameNativePlayer.VANILLA_PACKAGE,
            ),
        )
        assertEquals(GameNativePlayer.VANILLA_PACKAGE, intent.packageName)
        assertEquals(MAIN_ACTIVITY, intent.componentClass)
        assertEquals(GameNativePlayer.ACTION_LAUNCH_GAME, intent.action)
        assertNull(intent.dataUri)
        assertFalse(intent.grantReadUri)
        assertNull(intent.mimeType)
        assertEquals(setOf(LaunchFlag.NewTask, LaunchFlag.ClearTop), intent.flags)
        val appId = intent.extras.filterIsInstance<PlayerExtra.Integer>().single()
        val source = intent.extras.filterIsInstance<PlayerExtra.Text>().single()
        assertEquals(GameNativePlayer.EXTRA_APP_ID, appId.key)
        assertEquals(730, appId.value)
        assertEquals(GameNativePlayer.EXTRA_GAME_SOURCE, source.key)
        assertEquals("STEAM", source.value)
        assertTrue(intent.extras.none { it.key == "container_config" })
    }

    @Test
    fun goldPackageUsesTheSameActivity() {
        val intent = GameNativePlayer().launchIntent(
            LaunchRequest(
                game = game,
                target = LaunchTarget.AppRef(mapOf("app_id" to "1", "game_source" to "GOG")),
                resolvedPackage = GameNativePlayer.GOLD_PACKAGE,
            ),
        )
        assertEquals(GameNativePlayer.GOLD_PACKAGE, intent.packageName)
        assertEquals(MAIN_ACTIVITY, intent.componentClass)
    }

    @Test
    fun unknownPackageIsNotInstalled() {
        val failure = launchFailure(packageName = "app.example.other")
        assertTrue(failure is PluginException.NotFound)
        assertEquals("GameNative is not installed.", failure.message)
    }

    @Test
    fun missingAppIdIsRefused() {
        val failure = launchFailure(target = LaunchTarget.AppRef(mapOf("game_source" to "STEAM")))
        assertEquals("GameNative needs an app id.", failure.message)
    }

    @Test
    fun aFileIsNotAnAppRef() {
        val failure = launchFailure(target = LaunchTarget.ContentUri("content://games/title"))
        assertEquals("GameNative needs an app id.", failure.message)
    }

    @Test
    fun unknownSourceIsRefused() {
        val failure = launchFailure(
            target = LaunchTarget.AppRef(mapOf("app_id" to "5", "game_source" to "ITCH")),
        )
        assertEquals("GameNative needs a game source.", failure.message)
    }

    private fun launchFailure(
        packageName: String = GameNativePlayer.VANILLA_PACKAGE,
        target: LaunchTarget = LaunchTarget.AppRef(mapOf("app_id" to "1", "game_source" to "STEAM")),
    ): PluginException = try {
        GameNativePlayer().launchIntent(
            LaunchRequest(game = game, target = target, resolvedPackage = packageName),
        )
        error("expected a plugin failure")
    } catch (failure: PluginException) {
        failure
    }
}
