package app.foldcade

import app.foldcade.api.plugin.*
import app.foldcade.plugins.moonlight.MoonlightPlayer
import org.junit.Assert.*
import org.junit.Test

class ApkMoonlightPlayerTest {
    @Test fun aPausedApkPlayerDoesNotOverrideTheOfficialRouteAfterRevocation() {
        val apk = ApkMoonlightPlayer(testApkPlugin(), "test.host", "host-uuid", "Computer", "App")
        val official = MoonlightPlayer()
        val fallback = PlayerResolution(official, PlayerChoiceSource.Automatic)
        assertSame(official, fallback.withRunningPlayer(apk).player)
        val routed = PlayerResolution(apk, PlayerChoiceSource.Automatic)
        assertSame(apk, routed.withRunningPlayer(apk).player)
        // Existing running-player behavior for ordinary players remains the same.
        assertSame(official, routed.withRunningPlayer(official).player)
    }

    @Test fun buildsTheExactProtocolExtrasAndUsesTheRealAssignmentAtStart() {
        val plugin = testApkPlugin()
        val player = ApkMoonlightPlayer(plugin, "test.host", "host-uuid", "Computer", "Host app name")
        val game = Game("moonlight", "host-uuid|42", "moonlight", Availability.LocalOnly, label = "Tile title")
        val intent = player.launchIntent(LaunchRequest(game, LaunchTarget.AppRef(mapOf(MoonlightPlayer.HOST_UUID to "host-uuid", MoonlightPlayer.APP_ID to "42")), plugin.packageName))
            .withApkPluginDisplays(8, 12)
        assertTrue(player.occupiesBothDisplays)
        assertEquals(StartDisplay.Primary, player.startDisplay)
        assertEquals("app.foldcade.plugin.action.PLAY", intent.action)
        assertEquals(setOf(LaunchFlag.NewTask), intent.flags)
        assertEquals(plugin.packageName, intent.packageName)
        assertEquals(plugin.activity, intent.componentClass)
        val text = intent.extras.filterIsInstance<PlayerExtra.Text>().associate { it.key to it.value }
        assertEquals(mapOf(
            "app.foldcade.plugin.extra.BROKER_AUTHORITY" to "test.host.plugins",
            "app.foldcade.plugin.extra.TITLE" to "Tile title",
            "app.foldcade.gamestream.extra.HOST_UUID" to "host-uuid",
            "app.foldcade.gamestream.extra.HOST_NAME" to "Computer",
            "app.foldcade.gamestream.extra.APP_ID" to "42",
            "app.foldcade.gamestream.extra.APP_NAME" to "Host app name",
        ), text)
        val integers = intent.extras.filterIsInstance<PlayerExtra.Integer>().associate { it.key to it.value }
        assertEquals(mapOf("app.foldcade.plugin.extra.PROTOCOL" to 1, "app.foldcade.plugin.extra.TOP_DISPLAY" to 8, "app.foldcade.plugin.extra.BOTTOM_DISPLAY" to 12), integers)
        val single = ApkMoonlightPlayer(plugin.copy(bothScreens = false), "test.host", "host-uuid", "Computer", "App")
        assertFalse(single.occupiesBothDisplays)
        assertEquals(StartDisplay.PickerChoice, single.startDisplay)
        assertEquals(-1, intent.withApkPluginDisplays(3, null).extras.filterIsInstance<PlayerExtra.Integer>().single { it.key.endsWith("BOTTOM_DISPLAY") }.value)
    }
}
