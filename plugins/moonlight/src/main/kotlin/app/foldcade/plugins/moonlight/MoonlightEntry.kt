package app.foldcade.plugins.moonlight

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
import java.util.UUID

/**
 * Moonlight platform, player, and library.
 * Pairing and the host's app list stay inside Moonlight. This entry does not
 * pair, and it does not open Moonlight's database.
 *
 * A plugin Foldcade loads in-process is part of the GPLv3 work when distributed.
 */
class MoonlightEntry : PluginEntry {
    override val apiVersion: Int = PLUGIN_API_VERSION
    override val apiMinor: Int = PLUGIN_API_MINOR

    val player: MoonlightPlayer = MoonlightPlayer()
    val library: MoonlightLibrary = MoonlightLibrary()

    override val platforms: List<Platform> = listOf(MoonlightPlatform)
    override val players: List<Player> = listOf(player)
    override val libraries = listOf(library)
}

/**
 * Not a file type. The local-folder scan does not classify files as Moonlight.
 */
object MoonlightPlatform : Platform {
    const val ID: String = "moonlight"
    override val id: String = ID
    override val displayName: String = "Moonlight"
    override val extensions: Set<String> = emptySet()
}

/**
 * Official Moonlight on Android.
 *
 * Read from moonlight-android `b48494cb96bff23d8886c4775cc4f39a1075495d`:
 * - `app/build.gradle` sets `namespace 'com.limelight'`. The `nonRoot` flavor
 *   sets `applicationId "com.limelight"`. That is the official client.
 * - `ShortcutTrampoline` is exported. A pinned game shortcut targets it with
 *   string extra `UUID` (`AppView.UUID_EXTRA`) and string extra `AppId`
 *   (`Game.EXTRA_APP_ID`). The app id is not an int on this activity.
 * - The activity looks a computer up by name only when the UUID is missing.
 *   That lookup reads Moonlight's database. This intent always sends the UUID.
 *
 * The official client does not take both panels. The stream starts on the
 * screen the picker targets. The other screen keeps the picker. Home on the
 * screen that has input returns Foldcade on that screen only.
 */
class MoonlightPlayer : Player {
    override val id: String = ID
    override val displayName: String = DISPLAY_NAME
    override val platformId: String = MoonlightPlatform.ID
    override val packageNames: List<String> = PACKAGES
    override val needsLocalFile: Boolean = false
    override val startDisplay: StartDisplay = StartDisplay.PickerChoice
    override val occupiesBothDisplays: Boolean = false
    override val requiresImportedGame: Boolean = false

    override fun saveDeclarations(game: Game): List<SaveDeclaration> = emptyList()

    override fun launchIntent(request: LaunchRequest): PlayerIntent {
        if (request.resolvedPackage != OFFICIAL_PACKAGE) {
            throw PluginException.NotFound("Moonlight is not installed.")
        }
        val target = request.target as? LaunchTarget.AppRef
            ?: throw PluginException.NotFound(NEEDS_APP)
        val hostUuid = target.values[HOST_UUID]?.trim()
        val appId = target.values[APP_ID]?.trim()
        if (hostUuid == null || appId == null || !isHostUuid(hostUuid) || !isAppId(appId)) {
            throw PluginException.NotFound(NEEDS_APP)
        }
        return PlayerIntent(
            packageName = request.resolvedPackage,
            componentClass = SHORTCUT_TRAMPOLINE,
            action = ACTION_VIEW,
            extras = listOf(
                PlayerExtra.Text(key = EXTRA_HOST_UUID, value = hostUuid),
                PlayerExtra.Text(key = EXTRA_APP_ID, value = appId),
            ),
            flags = setOf(LaunchFlag.NewTask),
        )
    }

    companion object {
        const val ID: String = "moonlight"
        const val DISPLAY_NAME: String = "Moonlight"
        const val OFFICIAL_PACKAGE: String = "com.limelight"
        const val ACTION_VIEW: String = "android.intent.action.VIEW"
        const val EXTRA_HOST_UUID: String = "UUID"
        const val EXTRA_APP_ID: String = "AppId"
        const val HOST_UUID: String = "host_uuid"
        const val APP_ID: String = "app_id"
        const val NEEDS_APP: String =
            "Pin a shortcut in Moonlight, or confirm an import. Foldcade does not read Moonlight's database."
        val PACKAGES: List<String> = listOf(OFFICIAL_PACKAGE)
    }
}

/** Absolute class. The namespace stays `com.limelight` for the official application id. */
const val SHORTCUT_TRAMPOLINE: String = "com.limelight.ShortcutTrampoline"

/**
 * A host and an app id Moonlight already put on a shortcut, or that the user confirmed.
 * [appId] stays a string. This is not a row from Moonlight's database.
 */
data class MoonlightApp(
    val hostUuid: String,
    val appId: String,
    val label: String,
) {
    val remoteKey: String = "${hostUuid.lowercase(Locale.ROOT)}|$appId"

    init {
        require(isHostUuid(hostUuid)) { "host UUID" }
        require(isAppId(appId)) { "app id" }
        require(label.isNotBlank()) { "label" }
    }
}

/**
 * Parses one pinned shortcut. A computer shortcut with no app id is not a game.
 * A file path or a content URI is not a host. Returns null when the shortcut
 * cannot start a stream.
 */
fun moonlightApp(hostUuid: String?, appId: String?, label: String?): MoonlightApp? {
    val uuid = hostUuid?.trim()?.takeIf { isHostUuid(it) } ?: return null
    val id = appId?.trim()?.takeIf { isAppId(it) } ?: return null
    val name = label?.trim()?.takeIf { it.isNotEmpty() } ?: id
    return MoonlightApp(hostUuid = uuid, appId = id, label = name)
}

internal fun isHostUuid(value: String?): Boolean {
    val text = value?.trim().orEmpty()
    if (text.isEmpty()) return false
    return try {
        UUID.fromString(text).toString().isNotEmpty()
    } catch (_: IllegalArgumentException) {
        false
    }
}

internal fun isAppId(value: String?): Boolean {
    val text = value?.trim().orEmpty()
    if (text.isEmpty() || text.any { it !in '0'..'9' }) return false
    return text.toIntOrNull() != null
}
