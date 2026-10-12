package app.foldcade.plugins.droiddeck

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.PlayerExtra
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.StartDisplay
import java.util.ServiceLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DroidDeckPlayerTest {
    private val game = Game(
        backendId = DroidDeckLibrary.ID,
        remoteKey = "1145350",
        platformId = DroidDeckPlatform.ID,
        availability = Availability.LocalOnly,
        label = "Hades II",
    )

    @Test
    fun serviceFileLoadsTheEntryWithoutMetadata() {
        val entry = ServiceLoader.load(PluginEntry::class.java, DroidDeckPlayerTest::class.java.classLoader)
            .filterIsInstance<DroidDeckEntry>().single()
        assertEquals(DroidDeckPlatform.ID, entry.platforms.single().id)
        assertEquals(DroidDeckPlayer.ID, entry.players.single().id)
        assertEquals(DroidDeckLibrary.ID, entry.libraries.single().id)
        assertTrue(entry.metadataProviders.isEmpty())
        assertTrue(entry.platforms.single().extensions.isEmpty())
    }

    @Test
    fun oneScreenAndOneApplicationId() {
        val player = DroidDeckPlayer()
        assertEquals(listOf("com.droiddeck.launcher"), player.packageNames)
        assertFalse(player.occupiesBothDisplays)
        assertEquals(StartDisplay.PickerChoice, player.startDisplay)
        assertFalse(player.needsLocalFile)
        assertTrue(player.requiresImportedGame)
        assertEquals(DroidDeckPlatform.ID, player.platformId)
        assertTrue(player.saveDeclarations(game).isEmpty())
    }

    @Test
    fun launchSendsTheGameIdAsAStringWithNoSource() {
        val intent = launch(LaunchTarget.AppRef(mapOf(DroidDeckPlayer.GAME_ID to "1145350")))
        assertEquals("com.droiddeck.launcher", intent.packageName)
        assertEquals("com.droiddeck.launcher.MainActivity", intent.componentClass)
        assertEquals("com.droiddeck.launcher.LAUNCH_GAME", intent.action)
        assertNull(intent.dataUri)
        assertEquals(setOf(LaunchFlag.NewTask, LaunchFlag.ClearTop), intent.flags)
        assertEquals(listOf(PlayerExtra.Text("app_id", "1145350")), intent.extras)
    }

    @Test
    fun aGameAddedOutsideSteamKeepsItsWholeId() {
        // (shortcut app id << 32) | 0x02000000: past an int and past a signed long's half.
        val id = "18446744069448138752"
        val intent = launch(LaunchTarget.AppRef(mapOf(DroidDeckPlayer.GAME_ID to id)))
        assertEquals(listOf(PlayerExtra.Text("app_id", id)), intent.extras)
    }

    @Test
    fun noGameOpensDroidDeck() {
        val intent = launch(LaunchTarget.AppRef(emptyMap()))
        assertEquals("com.droiddeck.launcher.MainActivity", intent.componentClass)
        assertEquals("android.intent.action.MAIN", intent.action)
        assertTrue(intent.extras.isEmpty())
        assertEquals(setOf(LaunchFlag.NewTask), intent.flags)
    }

    @Test
    fun aBadIdIsRefused() {
        for (bad in listOf("", "0", "007", "-5", "12a", "18446744073709551616", "123456789012345678901")) {
            val failure = launchFailure(LaunchTarget.AppRef(mapOf(DroidDeckPlayer.GAME_ID to bad)))
            assertEquals(bad, DroidDeckPlayer.NEEDS_GAME, failure.message)
        }
    }

    @Test
    fun aFileIsNotAGame() {
        val failure = launchFailure(LaunchTarget.ContentUri("content://games/hades.steam"))
        assertEquals(DroidDeckPlayer.NEEDS_GAME, failure.message)
    }

    @Test
    fun anotherPackageIsNotInstalled() {
        val failure = launchFailure(LaunchTarget.AppRef(emptyMap()), packageName = "app.gamenative")
        assertTrue(failure is PluginException.NotFound)
        assertEquals("DroidDeck is not installed.", failure.message)
    }

    @Test
    fun steamAppIdOnlyForSteamGames() {
        assertEquals(1145350, droidDeckSteamAppId("1145350"))
        assertNull(droidDeckSteamAppId("18446744069448138752"))
        assertNull(droidDeckSteamAppId("4294967296"))
    }

    private fun launch(target: LaunchTarget) =
        DroidDeckPlayer().launchIntent(LaunchRequest(game, target, DroidDeckPlayer.PACKAGE))

    private fun launchFailure(
        target: LaunchTarget,
        packageName: String = DroidDeckPlayer.PACKAGE,
    ): PluginException = try {
        DroidDeckPlayer().launchIntent(LaunchRequest(game, target, packageName))
        error("expected a plugin failure")
    } catch (failure: PluginException) {
        failure
    }
}
