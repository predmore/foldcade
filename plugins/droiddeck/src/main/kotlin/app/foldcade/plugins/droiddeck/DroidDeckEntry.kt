package app.foldcade.plugins.droiddeck

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

/**
 * DroidDeck platform, player, and library.
 * The Steam client, its login, and its saves stay inside DroidDeck. This entry
 * does not open DroidDeck's files, and it does not register a metadata provider.
 *
 * This bundled entry is licensed under MIT.
 */
class DroidDeckEntry : PluginEntry {
    override val apiVersion: Int = PLUGIN_API_VERSION
    override val apiMinor: Int = PLUGIN_API_MINOR

    val player: DroidDeckPlayer = DroidDeckPlayer()
    val library: DroidDeckLibrary = DroidDeckLibrary()

    override val platforms: List<Platform> = listOf(DroidDeckPlatform)
    override val players: List<Player> = listOf(player)
    override val libraries = listOf(library)
}

/**
 * Games DroidDeck runs in its own Steam client. Not a file type: a `.steam`
 * file is a launch file, and the shell routes it, not the folder scan.
 * Its own platform, so a DroidDeck game always opens in DroidDeck, even when
 * GameNative is installed too.
 */
object DroidDeckPlatform : Platform {
    const val ID: String = "droiddeck"
    override val id: String = ID
    override val displayName: String = "DroidDeck"
    override val extensions: Set<String> = emptySet()
}

/**
 * DroidDeck on Android.
 *
 * Read from DroidDeck `de1a734fb531887154f883b8c52b8bc9de89f903`:
 * - `app/build.gradle` sets `namespace` and `applicationId` to `com.droiddeck.launcher`,
 *   with no suffix on any build type. It targets SDK 28.
 * - `AndroidManifest.xml` exports `.MainActivity` with a
 *   `com.droiddeck.launcher.LAUNCH_GAME` filter and a `droiddeck://game/<id>` VIEW filter.
 * - `GameLaunchIntent.readId` reads extra `app_id` as an int or a decimal string.
 *   A `game_source` other than `STEAM` is refused, and a missing one is accepted.
 *   The id is Steam's 64-bit game id, so a game added outside Steam does not fit
 *   an int. This player always sends a string and no source.
 * - `GameShortcuts.launchIntent` starts the same activity with `FLAG_ACTIVITY_CLEAR_TOP`.
 *   `MainActivity` reads the request on create and on a new intent, starts Steam
 *   when it is not running, and hands a running Steam session the game.
 *   A link launch returns home when the game ends.
 *
 * With no game id, the intent opens DroidDeck's own front end, as the built-in
 * DroidDeck tile does.
 * Saves stay in Steam Cloud. This player declares no save location.
 */
class DroidDeckPlayer : Player {
    override val id: String = ID
    override val displayName: String = DISPLAY_NAME
    override val platformId: String = DroidDeckPlatform.ID
    override val packageNames: List<String> = PACKAGES
    override val needsLocalFile: Boolean = false
    override val startDisplay: StartDisplay = StartDisplay.PickerChoice
    override val occupiesBothDisplays: Boolean = false
    override val requiresImportedGame: Boolean = true

    override fun saveDeclarations(game: Game): List<SaveDeclaration> = emptyList()

    override fun launchIntent(request: LaunchRequest): PlayerIntent {
        if (request.resolvedPackage != PACKAGE) {
            throw PluginException.NotFound("DroidDeck is not installed.")
        }
        val target = request.target as? LaunchTarget.AppRef
            ?: throw PluginException.NotFound(NEEDS_GAME)
        val raw = target.values[GAME_ID]
            ?: return PlayerIntent(
                packageName = PACKAGE,
                componentClass = MAIN_ACTIVITY,
                action = ACTION_MAIN,
                flags = setOf(LaunchFlag.NewTask),
            )
        val gameId = droidDeckGameId(raw) ?: throw PluginException.NotFound(NEEDS_GAME)
        return PlayerIntent(
            packageName = PACKAGE,
            componentClass = MAIN_ACTIVITY,
            action = ACTION_LAUNCH_GAME,
            extras = listOf(PlayerExtra.Text(EXTRA_APP_ID, gameId)),
            flags = setOf(LaunchFlag.NewTask, LaunchFlag.ClearTop),
        )
    }

    companion object {
        const val ID: String = "droiddeck"
        const val DISPLAY_NAME: String = "DroidDeck"
        const val PACKAGE: String = "com.droiddeck.launcher"
        const val MAIN_ACTIVITY: String = "com.droiddeck.launcher.MainActivity"
        const val ACTION_MAIN: String = "android.intent.action.MAIN"
        const val ACTION_LAUNCH_GAME: String = "com.droiddeck.launcher.LAUNCH_GAME"
        const val EXTRA_APP_ID: String = "app_id"

        /** The [LaunchTarget.AppRef] key for the game id. */
        const val GAME_ID: String = "game_id"
        const val NEEDS_GAME: String =
            "Choose Add to home screen on the game in DroidDeck. Foldcade does not read DroidDeck's library."
        val PACKAGES: List<String> = listOf(PACKAGE)
    }
}

/**
 * One game DroidDeck can start, by its id. [gameId] is canonical decimal:
 * a Steam app id, or the 64-bit id Steam gives a game added outside Steam.
 * This is not a row from DroidDeck's library.
 */
data class DroidDeckGame(
    val gameId: String,
    val title: String,
) {
    val remoteKey: String get() = gameId

    init {
        require(droidDeckGameId(gameId) == gameId) { "game id" }
        require(title.isNotBlank()) { "title" }
    }
}

/** A [DroidDeckGame], or null when [gameId] is not one DroidDeck takes. A blank title falls back to the id. */
fun droidDeckGame(gameId: String?, title: String?): DroidDeckGame? {
    val id = gameId?.let(::droidDeckGameId) ?: return null
    val name = title?.trim()?.replace('\n', ' ')?.replace('\r', ' ')?.takeIf { it.isNotEmpty() } ?: id
    return DroidDeckGame(gameId = id, title = name)
}

/**
 * [raw] as DroidDeck's `GameLaunchLink.validId` accepts it: 1 to 20 digits,
 * no leading zero, at most the largest unsigned 64-bit value. Null otherwise.
 * Surrounding space is ignored.
 */
fun droidDeckGameId(raw: String): String? {
    val text = raw.trim()
    if (!GAME_ID_PATTERN.matches(text)) return null
    return text.takeIf { it.toULongOrNull() != null }
}

/**
 * The Steam app id behind [gameId], for art. Null for a game added outside
 * Steam: its id carries a shortcut id in the high 32 bits.
 */
fun droidDeckSteamAppId(gameId: String): Int? = gameId.toIntOrNull()?.takeIf { it > 0 }

private val GAME_ID_PATTERN = Regex("[1-9][0-9]{0,19}")
