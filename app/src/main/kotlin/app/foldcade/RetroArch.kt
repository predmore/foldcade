package app.foldcade

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageInfo
import android.net.Uri
import android.provider.DocumentsContract
import app.foldcade.api.plugin.Player
import app.foldcade.plugins.armsx2.ArmsX2Player
import app.foldcade.plugins.retroarch.Core
import app.foldcade.plugins.retroarch.CoreChoice
import app.foldcade.plugins.retroarch.INTERNAL_DATA
import app.foldcade.plugins.retroarch.PACKAGES
import app.foldcade.plugins.retroarch.RetroArchPlayer

/**
 * Which cores each installed RetroArch has. RetroArch keeps them in its
 * private data directory, which no other app can list. It publishes that
 * directory, `cores/` included, as a documents provider named
 * `<package>.documents`, so Foldcade reads the cores through a folder the user
 * granted once in Android's picker. Null means Foldcade cannot see them.
 *
 * Nightly builds also answer a `com.retroarch.QUERY_INSTALLED_CORES` broadcast.
 * Its reply is a broadcast any app could send, and Android 13 cannot tell who
 * sent it, so Foldcade does not use it.
 */
internal class RetroArchCoreReader(
    private val context: Context,
    private val chosenFolder: (packageName: String) -> String?,
) {
    fun read(packageName: String): Set<String>? = fromGrantedFolder(packageName)

    /** True when [tree] lists RetroArch's cores: its data folder, or `cores/` itself. */
    fun holdsCores(tree: Uri): Boolean = try {
        val children = children(context.contentResolver, tree, DocumentsContract.getTreeDocumentId(tree))
        children.any { it.name == CORES && it.directory } || children.any { !it.directory && it.name.endsWith(CORE_SUFFIX) }
    } catch (_: Exception) {
        false
    }

    /** The folder the user chose for [packageName]'s cores, while Foldcade still holds its grant. */
    fun grantedFolder(packageName: String): Uri? {
        val chosen = chosenFolder(packageName)?.let(Uri::parse) ?: return null
        val held = context.contentResolver.persistedUriPermissions.any { it.isReadPermission && it.uri == chosen }
        return chosen.takeIf { held && it.authority == documentsAuthority(packageName) }
    }

    private fun fromGrantedFolder(packageName: String): Set<String>? {
        val tree = grantedFolder(packageName) ?: return null
        val resolver = context.contentResolver
        return try {
            val root = DocumentsContract.getTreeDocumentId(tree)
            val rootChildren = children(resolver, tree, root)
            // The data directory holds cores/. A grant on cores/ itself lists the cores directly.
            val coresId = rootChildren.firstOrNull { it.name == CORES && it.directory }?.id
            val listed = if (coresId != null) children(resolver, tree, coresId) else rootChildren
            listed.filter { !it.directory && it.name.endsWith(CORE_SUFFIX) }.map { it.name }.toSet()
        } catch (_: Exception) {
            null
        }
    }

    private class Child(val id: String, val name: String, val directory: Boolean)

    private fun children(resolver: ContentResolver, tree: Uri, parentId: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        return resolver.query(uri, columns, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    add(Child(id, name, cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR))
                }
            }
        } ?: throw IllegalStateException("RetroArch's folder could not be listed")
    }

    companion object {
        private const val CORES = "cores"
        private const val CORE_SUFFIX = "_libretro_android.so"

        fun documentsAuthority(packageName: String): String = "$packageName.documents"

        /**
         * Where the picker opens: the root RetroArch's documents provider lists,
         * its data directory. The root id is the directory's path. Android's
         * picker opens a root URI here; a document URI needs a lookup this
         * provider does not answer, and the picker would open elsewhere.
         */
        fun dataFolder(packageName: String): Uri =
            DocumentsContract.buildRootUri(documentsAuthority(packageName), "$INTERNAL_DATA/$packageName")

        /** The RetroArch package a granted tree belongs to, or null for any other folder. */
        fun packageOf(tree: Uri): String? = PACKAGES.firstOrNull { tree.authority == documentsAuthority(it) }
    }
}

/** Whether RetroArch can open a game by its path in shared storage. */
internal enum class RetroArchStorage {
    Granted,

    /** It asks for storage and the user has not allowed it yet. Opening RetroArch once asks. */
    NotAllowed,

    /** It does not ask for storage at all, as RetroArch from Google Play. */
    Never,
}

/**
 * RetroArch 1.22 targets Android 9 and reads shared storage with the storage
 * permission. Newer builds ask for all-files access, which another app cannot
 * check, so they count as granted.
 */
internal fun retroArchStorage(info: PackageInfo): RetroArchStorage {
    val requested = info.requestedPermissions.orEmpty().toList()
    val flags = info.requestedPermissionsFlags ?: IntArray(0)
    if (MANAGE_STORAGE in requested) return RetroArchStorage.Granted
    val read = requested.indexOf(READ_STORAGE)
    if (read < 0) return RetroArchStorage.Never
    val granted = flags.getOrNull(read)?.let { it and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 } == true
    return if (granted) RetroArchStorage.Granted else RetroArchStorage.NotAllowed
}

private const val MANAGE_STORAGE = "android.permission.MANAGE_EXTERNAL_STORAGE"
private const val READ_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"

/** What stands between a RetroArch launch and its start. */
internal sealed interface RetroArchGate {
    data object Ready : RetroArchGate

    /** Foldcade cannot see the cores and has not asked yet. */
    data object OfferCores : RetroArchGate

    data class MissingCore(val core: Core) : RetroArchGate

    data class Storage(val storage: RetroArchStorage) : RetroArchGate
}

/**
 * Storage first, since no core can open a file RetroArch cannot read. Cores
 * Foldcade cannot see are asked about once; after that the launch tries the
 * system's first core.
 */
internal fun retroArchGate(
    player: RetroArchPlayer,
    packageName: String,
    storage: RetroArchStorage,
    coresOffered: Boolean,
): RetroArchGate {
    if (storage != RetroArchStorage.Granted) return RetroArchGate.Storage(storage)
    return when (val choice = player.coreChoice(packageName)) {
        is CoreChoice.Ready -> RetroArchGate.Ready
        is CoreChoice.Missing -> RetroArchGate.MissingCore(choice.wanted)
        is CoreChoice.Unknown -> if (coresOffered) RetroArchGate.Ready else RetroArchGate.OfferCores
    }
}

/** A RetroArch player runs only with a core Foldcade knows is installed. Every other player can run. */
internal fun runnableWith(player: Player, installedPackage: String?): Boolean {
    val retroArch = player as? RetroArchPlayer ?: return true
    return installedPackage != null && retroArch.ready(installedPackage)
}

/**
 * Launch order across apps, lowest first.
 *
 * RetroArch leads a platform when its core is the better fit, and gives way
 * when a standalone player is (`aheadOfStandalone`). Every other player shares
 * one rank, in the order plugins were registered, except ARMSX2. NetherSX2 and
 * EmuCoreX stay in the emulators table, so registration order would put
 * whichever plugin loaded first ahead. ARMSX2's own rank puts it ahead of that
 * table whichever plugin is registered first. DuckStation does not need this:
 * the PlayStation row left the table, and RetroArch gives way there.
 */
internal fun playerRank(player: Player): Int = when {
    player is RetroArchPlayer && player.system.aheadOfStandalone -> 0
    player is ArmsX2Player -> 1
    player is RetroArchPlayer -> 3
    else -> 2
}
