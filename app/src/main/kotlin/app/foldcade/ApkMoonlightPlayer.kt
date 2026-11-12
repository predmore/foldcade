package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerExtra
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.StartDisplay
import app.foldcade.plugins.moonlight.MoonlightPlayer

/** Adapter for an out-of-process player. The APK has no dependency on the in-process API. */
internal class ApkMoonlightPlayer(
    val plugin: ApkPlugin,
    private val hostPackage: String,
    private val hostUuid: String,
    private val hostName: String,
    private val appName: String,
) : Player {
    override val id: String = "apk:${plugin.packageName}"
    override val displayName: String = plugin.label
    override val platformId: String = "moonlight"
    override val packageNames: List<String> = listOf(plugin.packageName)
    override val needsLocalFile: Boolean = false
    override val requiresImportedGame: Boolean = false
    override val occupiesBothDisplays: Boolean = plugin.bothScreens
    override val startDisplay: StartDisplay = if (plugin.bothScreens) StartDisplay.Primary else StartDisplay.PickerChoice
    override fun saveDeclarations(game: Game): List<SaveDeclaration> = emptyList()

    override fun launchIntent(request: LaunchRequest): PlayerIntent {
        val target = request.target as? LaunchTarget.AppRef ?: throw PluginException.NotFound("No Moonlight game")
        val appId = target.values[MoonlightPlayer.APP_ID]?.takeIf { app.foldcade.gamestream.isAppId(it) }
            ?: throw PluginException.NotFound("Invalid GameStream app id")
        if (!target.values[MoonlightPlayer.HOST_UUID].equals(hostUuid, ignoreCase = true)) throw PluginException.NotFound("Computer changed")
        val common = ApkPluginProtocol.EXTRA
        val game = ApkPluginProtocol.GAMESTREAM_EXTRA
        return PlayerIntent(
            packageName = plugin.packageName, componentClass = plugin.activity, action = ApkPluginProtocol.PLAY,
            flags = setOf(LaunchFlag.NewTask),
            extras = listOf(
                PlayerExtra.Integer(common + "PROTOCOL", plugin.protocol),
                PlayerExtra.Text(common + "BROKER_AUTHORITY", "$hostPackage.plugins"),
                PlayerExtra.Text(common + "TITLE", request.game.label),
                PlayerExtra.Text(game + "HOST_UUID", hostUuid),
                PlayerExtra.Text(game + "HOST_NAME", hostName),
                PlayerExtra.Text(game + "APP_ID", appId),
                PlayerExtra.Text(game + "APP_NAME", appName),
            ),
        )
    }
}

/** Display ids are assigned at the actual start, including resume and restart. */
internal fun PlayerIntent.withApkPluginDisplays(top: Int, bottom: Int?): PlayerIntent {
    if (action != ApkPluginProtocol.PLAY) return this
    val prefix = ApkPluginProtocol.EXTRA
    return copy(extras = extras.filterNot { it.key == prefix + "TOP_DISPLAY" || it.key == prefix + "BOTTOM_DISPLAY" } + listOf(
        PlayerExtra.Integer(prefix + "TOP_DISPLAY", top), PlayerExtra.Integer(prefix + "BOTTOM_DISPLAY", bottom ?: -1),
    ))
}

/** A paused APK player cannot keep routing a game after its grant or routing choice is off. */
internal fun PlayerResolution.withRunningPlayer(running: Player?): PlayerResolution =
    if (running == null || (running is ApkMoonlightPlayer && player?.id != running.id)) this
    else copy(player = running, skipped = emptyList())
