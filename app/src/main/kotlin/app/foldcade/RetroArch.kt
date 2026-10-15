package app.foldcade

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import app.foldcade.api.plugin.Player
import app.foldcade.plugins.retroarch.Core
import app.foldcade.plugins.retroarch.CoreChoice
import app.foldcade.plugins.retroarch.INTERNAL_DATA
import app.foldcade.plugins.retroarch.PACKAGES
import app.foldcade.plugins.retroarch.RetroArchPlayer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Which cores each installed RetroArch has. RetroArch keeps them in its
 * private data directory, which no other app can list, so Foldcade reads them
 * one of two ways:
 * - through a folder the user granted once in Android's picker. RetroArch
 *   publishes its data directory, `cores/` included, as a documents provider
 *   named `<package>.documents`;
 * - by asking, on RetroArch builds that answer `com.retroarch.QUERY_INSTALLED_CORES`
 *   (nightly builds since March 2026). A build that does not answer is not
 *   asked again until it is updated.
 * Null means Foldcade cannot see the cores.
 */
internal class RetroArchCoreReader(private val context: Context) {
    private val silent = ConcurrentHashMap<String, Long>()
    private val asking = Mutex()

    suspend fun read(packageName: String): Set<String>? =
        fromGrantedFolder(packageName) ?: fromQuery(packageName)

    /** The grant the user gave for [packageName]'s documents provider, if any. */
    fun grantedFolder(packageName: String): Uri? =
        context.contentResolver.persistedUriPermissions
            .firstOrNull { it.isReadPermission && it.uri.authority == documentsAuthority(packageName) }
            ?.uri

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

    private suspend fun fromQuery(packageName: String): Set<String>? = asking.withLock {
        val version = versionOf(packageName) ?: return@withLock null
        if (silent[packageName] == version) return@withLock null
        val answer = CompletableDeferred<Set<String>>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val cores = intent.getStringArrayExtra(EXTRA_CORES) ?: return
                answer.complete(cores.filter { it.endsWith(CORE_SUFFIX) }.toSet())
            }
        }
        context.registerReceiver(receiver, IntentFilter(ACTION_RESULT), Context.RECEIVER_EXPORTED)
        try {
            val query = Intent(ACTION_QUERY)
                .setPackage(packageName)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            context.sendBroadcast(query)
            withTimeoutOrNull(QUERY_TIMEOUT_MS) { answer.await() }
                .also { if (it == null) silent[packageName] = version }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    private fun versionOf(packageName: String): Long? = try {
        context.packageManager.getPackageInfo(packageName, 0).lastUpdateTime
    } catch (_: PackageManager.NameNotFoundException) {
        null
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
        const val ACTION_QUERY: String = "com.retroarch.QUERY_INSTALLED_CORES"
        const val ACTION_RESULT: String = "com.retroarch.INSTALLED_CORES_RESULT"
        const val EXTRA_CORES: String = "CORES"
        private const val CORES = "cores"
        private const val CORE_SUFFIX = "_libretro_android.so"
        private const val QUERY_TIMEOUT_MS = 1_500L

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
 * Launch order across apps, lowest first: RetroArch, then standalone emulators,
 * then RetroArch for a system whose standalone emulator is the better fit.
 */
internal fun playerRank(player: Player): Int {
    val retroArch = player as? RetroArchPlayer ?: return 1
    return if (retroArch.system.aheadOfStandalone) 0 else 2
}
