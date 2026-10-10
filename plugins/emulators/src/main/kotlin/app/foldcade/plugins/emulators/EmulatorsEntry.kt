package app.foldcade.plugins.emulators

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.PLUGIN_API_MINOR
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerExtra
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.StartDisplay

/**
 * Standalone emulators for the platforms the local-folder catalog classifies.
 * Each [Emulator] in [EMULATORS] becomes one player per platform it runs.
 * Players for one platform keep the order of [EMULATORS]. The shell launches
 * the first one that is installed.
 *
 * This entry does not declare platforms. The local-folder catalog owns those
 * ids. A second, different declaration would be rejected along with these players.
 *
 * Only launches that are data belong here. An emulator that needs save slots,
 * both displays, or its own launch code gets its own plugin, like Azahar and
 * melonDS. The host stores this entry whole, so one bad row drops every player.
 *
 * This bundled entry stays under the GNU GPLv3. An independent plugin that
 * uses only the published API may use any license.
 */
class EmulatorsEntry : PluginEntry {
    override val apiVersion: Int = PLUGIN_API_VERSION
    override val apiMinor: Int = PLUGIN_API_MINOR

    override val players: List<Player> = EMULATORS.flatMap { emulator ->
        emulator.platformIds.map { platformId -> EmulatorPlayer(emulator, platformId) }
    }
}

/** How the game content URI reaches the emulator. */
sealed interface RomHandoff {
    /** The URI is the intent data. */
    data object Data : RomHandoff

    /**
     * The URI is the string extra [key].
     * The URI is also the intent data, because the shell grants read on the data only.
     */
    data class Extra(val key: String) : RomHandoff
}

/**
 * One standalone emulator app.
 *
 * [activities] maps each application id to its absolute activity class, in
 * priority order. A fork or a paid build is another entry. The first installed
 * id is the one launched.
 *
 * [action] is the intent action. The component is explicit, so the action
 * does not pick the activity. Some activities read it.
 */
data class Emulator(
    val id: String,
    val displayName: String,
    val platformIds: List<String>,
    val activities: Map<String, String>,
    val action: String = ACTION_VIEW,
    val handoff: RomHandoff = RomHandoff.Data,
    val extras: List<PlayerExtra> = emptyList(),
    val flags: Set<LaunchFlag> = setOf(LaunchFlag.NewTask),
)

/**
 * [emulator] running games for [platformId].
 *
 * The launch hands over a content URI with a read grant. A file path, a
 * `file:` URI, and an app reference are refused. Nothing here asks for
 * all-files access.
 *
 * Saves stay inside the emulator. This player declares no save slot and does
 * not ask for a save folder.
 *
 * These emulators draw on one screen. The game opens on the screen the picker
 * targets, and the other screen keeps the picker.
 */
class EmulatorPlayer(
    val emulator: Emulator,
    override val platformId: String,
) : Player {
    override val id: String = "${emulator.id}.$platformId"
    override val displayName: String = emulator.displayName
    override val packageNames: List<String> = emulator.activities.keys.toList()
    override val needsLocalFile: Boolean = true
    override val startDisplay: StartDisplay = StartDisplay.PickerChoice
    override val occupiesBothDisplays: Boolean = false
    override val requiresImportedGame: Boolean = false

    override fun saveDeclarations(game: Game): List<SaveDeclaration> = emptyList()

    override fun launchIntent(request: LaunchRequest): PlayerIntent {
        val activity = emulator.activities[request.resolvedPackage]
            ?: throw PluginException.NotFound("${emulator.displayName} is not installed.")
        val uri = (request.target as? LaunchTarget.ContentUri)?.uri
        if (uri == null || !uri.startsWith("content:")) {
            throw PluginException.NotFound("${emulator.displayName} needs a content URI.")
        }
        val romExtra = when (val handoff = emulator.handoff) {
            RomHandoff.Data -> emptyList()
            is RomHandoff.Extra -> listOf(PlayerExtra.Text(handoff.key, uri))
        }
        return PlayerIntent(
            packageName = request.resolvedPackage,
            componentClass = activity,
            action = emulator.action,
            dataUri = uri,
            extras = romExtra + emulator.extras,
            grantReadUri = true,
            flags = emulator.flags,
        )
    }
}

const val ACTION_VIEW: String = "android.intent.action.VIEW"
const val ACTION_MAIN: String = "android.intent.action.MAIN"
