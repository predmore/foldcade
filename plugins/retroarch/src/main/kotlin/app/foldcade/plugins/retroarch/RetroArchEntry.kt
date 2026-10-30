package app.foldcade.plugins.retroarch

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
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.StartDisplay
import java.util.concurrent.atomic.AtomicReference

/**
 * RetroArch, one player per system in [SYSTEMS].
 *
 * The platform ids are the local-folder catalog's. This entry does not declare
 * platforms. The host stores this entry whole, so one bad row drops every player.
 *
 * Every player shares one [cores] view and one save folder: RetroArch is one
 * app with one core directory and one save directory.
 *
 * This bundled entry is licensed under MIT.
 */
class RetroArchEntry : PluginEntry {
    override val apiVersion: Int = PLUGIN_API_VERSION
    override val apiMinor: Int = PLUGIN_API_MINOR

    val cores: RetroArchCores = RetroArchCores()
    private val saves = SharedSaveFolder()

    override val players: List<Player> = SYSTEMS.map { system -> RetroArchPlayer(system, cores, saves) }
}

/**
 * The cores each installed RetroArch has, as far as the shell can tell.
 * RetroArch keeps its cores in its private data directory, so the shell reads
 * them through a folder the user granted, or asks a build that answers.
 */
class RetroArchCores {
    private val installed = AtomicReference<Map<String, Set<String>?>>(emptyMap())

    /**
     * Every installed RetroArch package, mapped to its core file names, or to
     * null when the shell cannot see them. A package that is not a key is not installed.
     */
    fun update(byPackage: Map<String, Set<String>?>) {
        installed.set(byPackage.filterKeys { it in PACKAGES })
    }

    /** The package a launch resolves to: the first of [PACKAGES] that is installed. */
    fun activePackage(): String? = PACKAGES.firstOrNull { it in installed.get() }

    /** Null when [packageName] is installed but its cores are unknown, or it is not installed. */
    fun coreFiles(packageName: String): Set<String>? = installed.get()[packageName]
}

/** Which core a launch of one system would load. */
sealed interface CoreChoice {
    /** [core] is installed. */
    data class Ready(val core: Core) : CoreChoice

    /** The shell can see the cores, and none for this system is installed. [wanted] is the first choice. */
    data class Missing(val wanted: Core) : CoreChoice

    /** The shell cannot see the cores. [assumed] is the one a launch would try. */
    data class Unknown(val assumed: Core) : CoreChoice
}

/** One folder for every RetroArch player. */
internal class SharedSaveFolder {
    @Volatile
    var uri: String? = null
}

/**
 * RetroArch running [system].
 *
 * The launch starts `RetroActivityFuture` with the game's absolute path in
 * `ROM` and the core's path in `LIBRETRO`. RetroArch 1.22 reads only those
 * string extras; it does not read the intent data, and it opens a game only
 * by path. The configuration, data, and storage extras are what RetroArch's
 * own launcher passes, so a 1.22 start finds the user's settings and folders.
 *
 * A core that is not installed leaves RetroArch on a black screen, so the
 * shell checks [coreChoice] before it starts the player.
 *
 * RetroArch writes battery saves as `<save folder>/<core name>/<game>.srm`
 * by default ("Sort Saves into Folders by Core Name" on, saves not next to the
 * game). The folder declared here is RetroArch's save folder, which the user
 * picks once for every system.
 */
class RetroArchPlayer internal constructor(
    val system: RetroArchSystem,
    /** The view every RetroArch player shares. The shell keeps it current. */
    val cores: RetroArchCores,
    private val saves: SharedSaveFolder,
) : Player, SaveFolderHolder {
    override val id: String = "$ID_PREFIX${system.platformId}"
    override val displayName: String = DISPLAY_NAME
    override val platformId: String = system.platformId
    override val packageNames: List<String> = PACKAGES
    override val needsLocalFile: Boolean = true
    override val opensByPath: Boolean = true
    override val startDisplay: StartDisplay = StartDisplay.PickerChoice
    override val occupiesBothDisplays: Boolean = false
    override val requiresImportedGame: Boolean = false

    override val saveFolderKey: String = SAVE_FOLDER_KEY
    override val suggestedSaveFolder: String = DEFAULT_SAVE_FOLDER

    override fun bindSaveFolder(contentUri: String) {
        saves.uri = contentUri.takeIf { it.startsWith("content:") }
    }

    override fun hasSaveFolder(): Boolean = saves.uri != null

    override val saveFolderPlaces: List<String> = listOf("RetroArch/saves")

    /** The core a launch through [packageName] loads. */
    fun coreChoice(packageName: String): CoreChoice {
        val files = cores.coreFiles(packageName) ?: return CoreChoice.Unknown(system.cores.first())
        val core = system.cores.firstOrNull { it.fileName in files } ?: return CoreChoice.Missing(system.cores.first())
        return CoreChoice.Ready(core)
    }

    /** True when the shell knows a core for this system is installed in [packageName]. */
    fun ready(packageName: String): Boolean = coreChoice(packageName) is CoreChoice.Ready

    override fun saveDeclarations(game: Game): List<SaveDeclaration> =
        listOf(SaveDeclaration(slot = SAVE_SLOT, locationUri = saves.uri))

    /**
     * `<core name>/<game name without its extension>.srm`, for the core the
     * active package would load. RetroArch takes off only the last extension.
     */
    override fun saveFileName(slot: String, romFileName: String): String? {
        if (slot != SAVE_SLOT || romFileName.isBlank()) return null
        val core = cores.activePackage()?.let { chosenCore(it) } ?: system.cores.first()
        val base = romFileName.substringBeforeLast('.').ifEmpty { romFileName }
        return "${core.libraryName}/$base.srm"
    }

    override fun launchIntent(request: LaunchRequest): PlayerIntent {
        val packageName = request.resolvedPackage
        if (packageName !in PACKAGES) throw PluginException.NotFound("RetroArch is not installed.")
        val file = request.target as? LaunchTarget.SharedFile
            ?: throw PluginException.NotFound("RetroArch needs a game in shared storage.")
        if (!file.path.startsWith("/")) throw PluginException.NotFound("RetroArch needs a game in shared storage.")
        val core = chosenCore(packageName)
        val data = "$INTERNAL_DATA/$packageName"
        val external = "$SHARED_STORAGE/Android/data/$packageName/files"
        return PlayerIntent(
            packageName = packageName,
            componentClass = RETRO_ACTIVITY,
            action = ACTION_MAIN,
            extras = listOf(
                PlayerExtra.Text("ROM", file.path),
                PlayerExtra.Text("LIBRETRO", "$data/cores/${core.fileName}"),
                PlayerExtra.Text("CONFIGFILE", "$external/retroarch.cfg"),
                PlayerExtra.Text("DATADIR", data),
                PlayerExtra.Text("SDCARD", SHARED_STORAGE),
                PlayerExtra.Text("EXTERNAL", external),
            ),
            flags = setOf(LaunchFlag.NewTask, LaunchFlag.ReplaceRunning),
        )
    }

    /**
     * [intent] as one RetroArch starts fresh even while it runs that game:
     * the core path names the same file through `/data/data`. RetroArch tells
     * games apart by the `ROM` and `LIBRETRO` strings, so it quits, and the
     * shell starts it again ([LaunchFlag.ReplaceRunning]). The new process
     * reads the save the shell just placed, rather than resume with the old
     * one in memory. A second call gives back the usual path.
     */
    fun freshStart(intent: PlayerIntent): PlayerIntent = intent.copy(
        extras = intent.extras.map { extra ->
            if (extra !is PlayerExtra.Text || extra.key != "LIBRETRO") return@map extra
            val swapped = when {
                extra.value.startsWith("$INTERNAL_DATA/") -> LEGACY_DATA + extra.value.removePrefix(INTERNAL_DATA)
                extra.value.startsWith("$LEGACY_DATA/") -> INTERNAL_DATA + extra.value.removePrefix(LEGACY_DATA)
                else -> extra.value
            }
            extra.copy(value = swapped)
        },
    )

    /** The installed core, or the first choice when none is known to be installed. */
    private fun chosenCore(packageName: String): Core = when (val choice = coreChoice(packageName)) {
        is CoreChoice.Ready -> choice.core
        is CoreChoice.Missing -> choice.wanted
        is CoreChoice.Unknown -> choice.assumed
    }

    companion object {
        const val ID_PREFIX: String = "retroarch."
        const val DISPLAY_NAME: String = "RetroArch"
        const val SAVE_SLOT: String = "srm"
        const val SAVE_FOLDER_KEY: String = "retroarch"
    }
}

/**
 * Application ids, best first. `com.retroarch.aarch64` is the 64-bit build
 * from retroarch.com (and RetroArch Plus on Google Play). `com.retroarch` is
 * the F-Droid and Google Play build. `com.retroarch.ra32` is the 32-bit build.
 * Every build has the same activity and the same layout under its own id.
 */
val PACKAGES: List<String> = listOf("com.retroarch.aarch64", "com.retroarch", "com.retroarch.ra32")

/** Exported, `singleInstance`, and reads the game from its `ROM` extra in every release. */
const val RETRO_ACTIVITY: String = "com.retroarch.browser.retroactivity.RetroActivityFuture"

/**
 * RetroArch's own entry activity, for opening it without a game. On nightly
 * builds it is an alias of [RETRO_ACTIVITY].
 */
const val MAIN_MENU_ACTIVITY: String = "com.retroarch.browser.mainmenu.MainMenuActivity"

/** RetroArch's private data directory, `ApplicationInfo.dataDir` for the device owner. Its cores are in `cores/`. */
const val INTERNAL_DATA: String = "/data/user/0"

/** The same directories as [INTERNAL_DATA], through the older link every Android keeps. */
const val LEGACY_DATA: String = "/data/data"

/** Primary shared storage. RetroArch keeps its user folders under `RetroArch/` here. */
const val SHARED_STORAGE: String = "/storage/emulated/0"

/** RetroArch's default save directory, `RetroArch/saves` on primary shared storage, as a document. */
const val DEFAULT_SAVE_FOLDER: String =
    "content://com.android.externalstorage.documents/document/primary%3ARetroArch%2Fsaves"

const val ACTION_MAIN: String = "android.intent.action.MAIN"
