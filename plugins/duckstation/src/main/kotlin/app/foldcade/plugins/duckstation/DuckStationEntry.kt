package app.foldcade.plugins.duckstation

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.HostedSaveFolder
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

/**
 * PlayStation player for DuckStation. The platform id is the local-folder
 * catalog's `playstation`. This entry does not declare that platform again,
 * and does not scan a folder.
 *
 * This bundled entry is licensed under MIT.
 */
class DuckStationEntry : PluginEntry {
    override val apiVersion: Int = PLUGIN_API_VERSION
    override val apiMinor: Int = PLUGIN_API_MINOR

    val player: DuckStationPlayer = DuckStationPlayer()

    override val players: List<Player> = listOf(player)
}

/**
 * DuckStation's 2026 Android rewrite (read from build 11833).
 *
 * The launch starts `EmulationActivity` with the game in the `bootPath` extra, as
 * DuckStation's file layer names a document, see [duckStationPath]. The rewrite
 * opens only those names, and its own files, and reads a `content:` URI as a file
 * path. A URI with no such name goes as it is. The URI is also the intent data,
 * because the shell grants read on the data only. Older builds also read
 * `resumeState`, which is set to false.
 *
 * DuckStation keeps its data, `memcards` and `bios` among it, in its own
 * `Android/data/<package>/files` folder. The folder the user picks for it is that
 * data folder, where the picker opens, see [suggestedSaveFolder], and a BIOS a
 * library has goes in its `bios`, see [firmwareFileName], where DuckStation finds
 * it with nothing changed.
 *
 * Memory cards cannot sync from there: DuckStation writes a card private to
 * itself, and no other app can read it. So Foldcade hosts a folder for them, see
 * [HostedSaveFolder], which the user picks once as DuckStation's Memory Cards
 * folder. DuckStation then writes its cards through Foldcade. Card 1 of a game is
 * the slot: DuckStation's default card per game, named after the game, see
 * [saveFileName]. Until DuckStation has the folder, its cards are not declared,
 * and they stay where they are. A missing folder does not stop launch.
 *
 * DuckStation draws on one screen. The game opens on the screen the picker
 * targets, and the other screen keeps the picker.
 */
class DuckStationPlayer : Player, SaveFolderHolder, HostedSaveFolder {
    override val id: String = ID
    override val displayName: String = DISPLAY_NAME
    override val platformId: String = PLATFORM_ID
    override val packageNames: List<String> = listOf(PACKAGE)
    override val needsLocalFile: Boolean = true
    override val startDisplay: StartDisplay = StartDisplay.PickerChoice
    override val occupiesBothDisplays: Boolean = false
    override val requiresImportedGame: Boolean = false

    @Volatile
    private var saveFolder: String? = null

    override fun bindSaveFolder(contentUri: String) {
        saveFolder = contentUri.takeIf { it.startsWith("content:") }
    }

    override fun hasSaveFolder(): Boolean = saveFolder != null

    /**
     * DuckStation's own data folder. Android lets the picker choose it on the Thor,
     * so nothing in DuckStation has to change. Where it does not, the user moves
     * DuckStation's Memory Cards and BIOS folders and picks the folder holding them.
     */
    override val suggestedSaveFolder: String = DATA_FOLDER

    /** DuckStation's data folder holds these, and so does a folder its two were moved to. */
    override val saveFolderMarkers: Set<String> = setOf(MEMCARDS, BIOS)

    @Volatile
    private var cards: String? = null

    @Volatile
    private var cardsInUse: Boolean = false

    override val hostedFolderTitle: String = "DuckStation memory cards"

    override val hostedFolderSteps: String =
        "In DuckStation, open App Settings, turn on Show Advanced Settings in the ⋮ menu, and open " +
            "Folders › Memory Cards › Choose New Location. Pick Foldcade in the picker’s menu, tap " +
            "Use this folder and Allow, then Copy Files to bring the cards you have."

    override fun bindHostedFolder(contentUri: String, inUse: Boolean) {
        cards = contentUri.takeIf { it.startsWith("content:") }
        cardsInUse = inUse && cards != null
    }

    override fun hostedFolderInUse(): Boolean = cardsInUse

    override fun saveDeclarations(game: Game): List<SaveDeclaration> =
        listOf(SaveDeclaration(slot = CARD_SLOT, locationUri = cards?.takeIf { cardsInUse }))

    /**
     * `<title>_1.mcd`, in its Memory Cards folder: DuckStation's card in slot 1 for a game, with the
     * card type "separate card per game" (`System::GetGameMemoryCardPath`). Its
     * default names the card after the game's title in its database, and its file
     * title choice after the game file. For a game file with a Redump name, as
     * RomM keeps them, the two are the same: the title is the file's name without
     * its extension, and a disc set's `.m3u` carries the set's name. DuckStation's
     * `Path::SanitizeFileName` replaces control characters, `/`, and `*` with `_`.
     */
    override fun saveFileName(slot: String, romFileName: String): String? {
        if (slot != CARD_SLOT) return null
        val title = romFileName.substringAfterLast('/').let { name ->
            if ('.' in name) name.substringBeforeLast('.') else name
        }
        if (title.isBlank()) return null
        val sanitized = title.map { c -> if (c.code <= 31 || c == '/' || c == '*') '_' else c }.joinToString("")
        return "${sanitized}_1.mcd"
    }

    /**
     * `bios/<name>`. DuckStation finds a BIOS in its BIOS folder by its bytes,
     * whatever the file is called, so any firmware RomM keeps for the platform
     * goes there under its own name.
     */
    override fun firmwareFileName(name: String): String? {
        if (name.isBlank() || name == "." || name == ".." || name.any { it == '/' || it == '\\' || it.code <= 31 }) {
            return null
        }
        return "$BIOS/$name"
    }

    override fun launchIntent(request: LaunchRequest): PlayerIntent {
        if (request.resolvedPackage != PACKAGE) {
            throw PluginException.NotFound("$DISPLAY_NAME is not installed.")
        }
        val uri = (request.target as? LaunchTarget.ContentUri)?.uri
        if (uri == null || !uri.startsWith("content:")) {
            throw PluginException.NotFound("$DISPLAY_NAME needs a content URI.")
        }
        return PlayerIntent(
            packageName = PACKAGE,
            componentClass = EMULATION_ACTIVITY,
            action = ACTION_MAIN,
            dataUri = uri,
            extras = listOf(
                PlayerExtra.Text(BOOT_PATH, duckStationPath(uri) ?: uri),
                PlayerExtra.BooleanFlag(RESUME_STATE, false),
            ),
            grantReadUri = true,
            // Replaces a game that is already running.
            flags = setOf(LaunchFlag.NewTask, LaunchFlag.ClearTop, LaunchFlag.ClearTask),
        )
    }

    companion object {
        const val ID: String = "duckstation"
        const val DISPLAY_NAME: String = "DuckStation"
        const val PLATFORM_ID: String = "playstation"
        const val PACKAGE: String = "com.github.stenzek.duckstation"
        const val EMULATION_ACTIVITY: String = "com.github.stenzek.duckstation.EmulationActivity"
        const val ACTION_MAIN: String = "android.intent.action.MAIN"
        const val BOOT_PATH: String = "bootPath"
        const val RESUME_STATE: String = "resumeState"

        /** Memory card 1, the game's own card. */
        const val CARD_SLOT: String = "card1"

        /** DuckStation's data folder in shared storage, as a document Android's picker opens at. */
        const val DATA_FOLDER: String =
            "content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fdata%2Fcom.github.stenzek.duckstation%2Ffiles"

        /** DuckStation's own names for these folders under its data folder. */
        const val MEMCARDS: String = "memcards"
        const val BIOS: String = "bios"
    }
}

/**
 * [uri], a document in a tree, as DuckStation names it:
 * `/android_content/<authority>/tree/<tree id>/document/<document id>`, each id
 * decoded. An id `volume:path` is written `tree@volume/path`. DuckStation builds
 * the URI back from that name, and lists a cue sheet's folder in the tree to
 * find its tracks.
 *
 * Null for a URI that is not a document in a tree, or whose name would not read
 * back as the same URI: an id with an empty part, or a part DuckStation would
 * take for `tree` or `document`.
 */
internal fun duckStationPath(uri: String): String? {
    if (!uri.startsWith(CONTENT)) return null
    val segments = uri.removePrefix(CONTENT).substringBefore('?').substringBefore('#').split('/')
    if (segments.size != 5 || segments[0].isEmpty() || segments[1] != TREE || segments[3] != DOCUMENT) return null
    val tree = percentDecode(segments[2]) ?: return null
    val document = percentDecode(segments[4]) ?: return null
    val named = listOf(TREE to tree, DOCUMENT to document).map { (keyword, id) -> pathPart(keyword, id) ?: return null }
    return "/android_content/${segments[0]}" + named.joinToString("")
}

/**
 * `/<keyword>/<id>`, or `/<keyword>@<volume>/<path>` for `volume:path`. A volume
 * comes before the id's first `/`, so a colon in a file's name stays in its name.
 */
private fun pathPart(keyword: String, id: String): String? {
    if (id.isEmpty()) return null
    val colon = id.indexOf(':').takeIf { it >= 0 && '/' !in id.substring(0, it) } ?: -1
    val (head, rest) = if (colon >= 0) "$keyword@${id.substring(0, colon)}" to id.substring(colon + 1) else keyword to id
    val parts = if (rest.isEmpty()) emptyList() else rest.split('/')
    if (parts.any { it.isEmpty() || it == TREE || it == DOCUMENT || it.startsWith("$TREE@") || it.startsWith("$DOCUMENT@") }) {
        return null
    }
    return "/$head" + parts.joinToString("") { "/$it" }
}

/** RFC 3986 percent-decoding as UTF-8. A `+` stays a plus. Null for a broken escape. */
private fun percentDecode(text: String): String? {
    val out = java.io.ByteArrayOutputStream(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '%') {
            val byte = text.substring(i + 1, minOf(i + 3, text.length)).takeIf { it.length == 2 }?.toIntOrNull(16)
                ?: return null
            out.write(byte)
            i += 3
        } else {
            out.write(c.toString().toByteArray(Charsets.UTF_8))
            i += 1
        }
    }
    return out.toString(Charsets.UTF_8.name())
}

private const val CONTENT: String = "content://"
private const val TREE: String = "tree"
private const val DOCUMENT: String = "document"
