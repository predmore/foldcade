package app.foldcade.plugins.gamenative

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.PLUGIN_API_MINOR
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.Platform
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerExtra
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.StartDisplay
import java.util.Locale

/**
 * PC platform, the GameNative player, and [GameNativeLibrary].
 * This entry does not register a metadata provider. RomM does not configure
 * this catalog, and the RomM metadata default does not apply to it.
 *
 * This bundled entry stays under the GNU GPLv3. An independent plugin that
 * uses only the published API may use any license.
 */
class GameNativeEntry : PluginEntry {
    override val apiVersion: Int = PLUGIN_API_VERSION
    override val apiMinor: Int = PLUGIN_API_MINOR

    val player: GameNativePlayer = GameNativePlayer()
    val library: GameNativeLibrary = GameNativeLibrary()

    override val platforms: List<Platform> = listOf(PcPlatform)
    override val players: List<Player> = listOf(player)
    override val libraries = listOf(library)
}

/**
 * PC games GameNative already has. This is not a file type, so it claims no extensions.
 */
object PcPlatform : Platform {
    override val id: String = ID
    override val displayName: String = DISPLAY_NAME
    override val extensions: Set<String> = emptySet()

    const val ID: String = "pc"
    const val DISPLAY_NAME: String = "PC"
}

/**
 * GameNative on Android.
 *
 * Read from GameNative `a21a96c5cc0a9131bf94ba3dabac2d0e0d8acddf`:
 * - `app/build.gradle.kts` sets `namespace = "app.gamenative"` and
 *   `applicationId = "app.gamenative"`. The `release-gold` build type adds
 *   `applicationIdSuffix = ".gold"`. That suffix does not change the namespace.
 * - `app/src/main/AndroidManifest.xml` exports `.MainActivity` (`singleTop`)
 *   with an `app.gamenative.LAUNCH_GAME` filter.
 * - `IntentLaunchManager` reads int extra `app_id` and string extra `game_source`.
 *   A missing or unknown `game_source` on this action becomes `STEAM`.
 *   The source must match a `GameSource` name. The enum at that commit is
 *   `STEAM`, `CUSTOM_GAME`, `GOG`, `EPIC`, and `AMAZON`.
 * - `ShortcutUtils.createPinnedShortcut` starts the same activity with
 *   `FLAG_ACTIVITY_NEW_TASK` and `FLAG_ACTIVITY_CLEAR_TOP`, and the same extras.
 *   It does not send `container_config`. This player does not either.
 *
 * The class string is the same for both application ids. The package half of
 * the component is the application id that was resolved. [VANILLA_PACKAGE] is
 * first, so it is the one used when both are installed.
 *
 * Saves stay inside GameNative. This player declares no save location and does
 * not read GameNative's database.
 */
class GameNativePlayer : Player {
    override val id: String = ID
    override val displayName: String = DISPLAY_NAME
    override val platformId: String = PcPlatform.ID
    override val packageNames: List<String> = PACKAGES
    override val needsLocalFile: Boolean = false
    override val startDisplay: StartDisplay = StartDisplay.PickerChoice
    override val occupiesBothDisplays: Boolean = false
    override val requiresImportedGame: Boolean = true

    override fun saveDeclarations(game: Game): List<SaveDeclaration> = emptyList()

    override fun launchIntent(request: LaunchRequest): PlayerIntent {
        val activity = mainActivityClass(request.resolvedPackage)
            ?: throw PluginException.NotFound("GameNative is not installed.")
        val target = request.target as? LaunchTarget.AppRef
            ?: throw PluginException.NotFound("GameNative needs an app id.")
        val appId = target.values[EXTRA_APP_ID]?.toIntOrNull()
        if (appId == null || appId <= 0) {
            throw PluginException.NotFound("GameNative needs an app id.")
        }
        val source = target.values[EXTRA_GAME_SOURCE]?.trim()?.uppercase(Locale.ROOT)
        if (source == null || source !in GAME_SOURCES) {
            throw PluginException.NotFound("GameNative needs a game source.")
        }
        return PlayerIntent(
            packageName = request.resolvedPackage,
            componentClass = activity,
            action = ACTION_LAUNCH_GAME,
            extras = listOf(
                PlayerExtra.Integer(EXTRA_APP_ID, appId),
                PlayerExtra.Text(EXTRA_GAME_SOURCE, source),
            ),
            flags = setOf(LaunchFlag.NewTask, LaunchFlag.ClearTop),
        )
    }

    companion object {
        const val ID: String = "gamenative"
        const val DISPLAY_NAME: String = "GameNative"
        const val ACTION_LAUNCH_GAME: String = "app.gamenative.LAUNCH_GAME"
        const val EXTRA_APP_ID: String = "app_id"
        const val EXTRA_GAME_SOURCE: String = "game_source"
        const val DEFAULT_SOURCE: String = "STEAM"
        const val VANILLA_PACKAGE: String = "app.gamenative"
        const val GOLD_PACKAGE: String = "app.gamenative.gold"
        val PACKAGES: List<String> = listOf(VANILLA_PACKAGE, GOLD_PACKAGE)
    }
}

/**
 * `GameSource` names from GameNative `LibraryItem.kt` at
 * `a21a96c5cc0a9131bf94ba3dabac2d0e0d8acddf`.
 */
val GAME_SOURCES: Set<String> = setOf(
    "STEAM",
    "CUSTOM_GAME",
    "GOG",
    "EPIC",
    "AMAZON",
)

/**
 * Component class for a GameNative application id.
 * The manifest activity is `.MainActivity` under namespace `app.gamenative`.
 */
fun mainActivityClass(packageName: String): String? = when (packageName) {
    GameNativePlayer.VANILLA_PACKAGE, GameNativePlayer.GOLD_PACKAGE -> MAIN_ACTIVITY
    else -> null
}

const val MAIN_ACTIVITY: String = "app.gamenative.MainActivity"
