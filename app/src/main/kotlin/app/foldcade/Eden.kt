package app.foldcade

import app.foldcade.api.plugin.Player
import app.foldcade.plugins.emulators.EDEN_PACKAGES
import app.foldcade.plugins.romm.RommSwitchFirmware
import app.foldcade.romm.FirmwareSummary
import app.foldcade.romm.RomProgress
import app.foldcade.romm.RommFirmwareMismatch
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The installed Eden package a launch will use, or null when [player] is not Eden. */
internal fun installedEdenPackage(player: Player, installed: (String) -> Boolean): String? =
    player.packageNames.firstOrNull { it in EDEN_PACKAGES && installed(it) }

/** What stands between an Eden launch and its start. */
internal sealed interface EdenGate {
    /** The folder is granted. Add whatever Eden is missing, then start. */
    data object Check : EdenGate

    /** Ask once for Eden's folder. RomM has `prod.keys` or a firmware zip. */
    data object Offer : EdenGate

    /**
     * Start as usual. [markOffered] stays false: an empty server and a RomM
     * error are not the user declining, so the next launch asks again.
     */
    data class Ready(val markOffered: Boolean = false) : EdenGate
}

/**
 * Grant first. RomM is asked only when there is no grant and Foldcade has not
 * offered yet. [rommHasSwitchFiles] null, or a throw, is a RomM or network
 * error: [EdenGate.Ready] and the offer is not marked.
 */
internal suspend fun edenGate(
    holdsGrant: Boolean,
    offered: Boolean,
    rommHasSwitchFiles: suspend () -> Boolean?,
): EdenGate {
    if (holdsGrant) return EdenGate.Check
    if (offered) return EdenGate.Ready(markOffered = false)
    val has = try {
        rommHasSwitchFiles()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
    return if (has == true) EdenGate.Offer else EdenGate.Ready(markOffered = false)
}

/**
 * True while files added at [pendingBoot] still need Eden to start again.
 * A changed boot count means the device restarted. [edenStopped] is Android's
 * stopped state: Force stop sets it, and the next start of Eden clears it.
 * A boot count of -1, on either side, is unknown and counts as unchanged.
 */
internal fun edenRestartStillNeeded(pendingBoot: Int, currentBoot: Int, edenStopped: Boolean): Boolean {
    if (pendingBoot != -1 && currentBoot != -1 && pendingBoot != currentBoot) return false
    return !edenStopped
}

/** True when RomM has `prod.keys` or a firmware zip; null when it could not be reached. */
internal fun rommHasSwitchSetup(server: RommSwitchFirmware?): Boolean? = when {
    server == null -> false
    server.failed -> null
    else -> server.files.any(::countsAsSwitchSetup)
}

/** `prod.keys`, or a firmware zip. `title.keys` alone is not a reason to offer the folder. */
internal fun countsAsSwitchSetup(file: FirmwareSummary): Boolean =
    file.fileName.equals(PROD_KEYS, ignoreCase = true) ||
        file.fileName.endsWith(".zip", ignoreCase = true)

/** The `prod.keys` or `title.keys` file, matching the name in any case. Ties take the highest id. */
internal fun chooseKeyFile(files: List<FirmwareSummary>, name: String): FirmwareSummary? =
    files.filter { it.fileName.equals(name, ignoreCase = true) }.maxByOrNull { it.id }

/**
 * The firmware zip to install. The highest dotted version in the name wins
 * (`1.2` before `1.10` is not how these compare: each part is a number).
 * A tie, or no version in any name, takes the highest RomM id.
 * A file with no version loses to one that has one.
 */
internal fun chooseFirmwareZip(files: List<FirmwareSummary>): FirmwareSummary? {
    val zips = files.filter { it.fileName.endsWith(".zip", ignoreCase = true) }
    if (zips.isEmpty()) return null
    val ranked = zips.map { it to dottedVersion(it.fileName) }
    val versioned = ranked.filter { it.second != null }
    val pool = if (versioned.isNotEmpty()) versioned else ranked
    return pool.maxWith(Comparator { left, right ->
        val versions = compareVersions(left.second.orEmpty(), right.second.orEmpty())
        if (versions != 0) versions else left.first.id.compareTo(right.first.id)
    }).first
}

private val DOTTED_VERSION = Regex("""\d+(?:\.\d+)+""")

internal fun dottedVersion(name: String): List<Int>? {
    val match = DOTTED_VERSION.find(name) ?: return null
    return try {
        match.value.split('.').map { it.toInt() }
    } catch (_: NumberFormatException) {
        null
    }
}

private fun compareVersions(left: List<Int>, right: List<Int>): Int {
    val count = maxOf(left.size, right.size)
    for (index in 0 until count) {
        val difference = (left.getOrNull(index) ?: 0).compareTo(right.getOrNull(index) ?: 0)
        if (difference != 0) return difference
    }
    return 0
}

/**
 * A firmware zip Eden will not download again, until this identity changes.
 * RomM's md5 when it sent one, lowercase. Otherwise the listed size, so a zip
 * with no hash is tried again when that size changes.
 */
internal fun refusedFirmwareKey(origin: String, file: FirmwareSummary): String {
    val mark = file.md5Hash?.lowercase()?.takeIf { it.isNotEmpty() } ?: "size:${file.fileSizeBytes}"
    return "$origin\t${file.id}\t$mark"
}

/** True when the downloaded firmware did not match the size or md5 RomM listed. Any other failure is tried again. */
internal fun firmwareBytesRefused(error: Throwable): Boolean = error is RommFirmwareMismatch

/** One entry of a firmware zip, as Eden's installer will see it. */
internal data class FirmwareZipEntry(val name: String, val directory: Boolean)

/**
 * Eden installs a zip whose every entry is a top-level `.nca` file.
 * A directory, a nested path, `..`, or anything else is refused. An empty zip is refused.
 */
internal fun firmwareZipOk(entries: List<FirmwareZipEntry>): Boolean {
    if (entries.isEmpty()) return false
    return entries.all { entry ->
        val name = entry.name
        !entry.directory &&
            name.isNotEmpty() &&
            !name.endsWith("/") &&
            '/' !in name &&
            '\\' !in name &&
            ".." !in name &&
            name.endsWith(".nca", ignoreCase = true)
    }
}

/** What Eden's folder currently holds, as sizes. Null means the file is absent. */
internal data class EdenInventory(
    val prodKeysSize: Long?,
    val titleKeysSize: Long?,
    val ncaNames: List<String>,
    val partialMarker: Boolean,
)

/** What Foldcade still has to add. An empty keys file counts as missing. */
internal data class EdenGaps(
    val prodKeys: Boolean,
    val titleKeys: Boolean,
    val firmware: Boolean,
) {
    val any: Boolean get() = prodKeys || titleKeys || firmware
}

/** A partial-install marker means the firmware copy was interrupted, so it is missing. */
internal fun edenGaps(inventory: EdenInventory): EdenGaps = EdenGaps(
    prodKeys = inventory.prodKeysSize == null || inventory.prodKeysSize <= 0L,
    titleKeys = inventory.titleKeysSize == null || inventory.titleKeysSize <= 0L,
    firmware = inventory.partialMarker || inventory.ncaNames.none { it.endsWith(".nca", ignoreCase = true) },
)

/** One child of a directory in Eden's folder. */
internal data class EdenNode(val name: String, val directory: Boolean, val size: Long)

/**
 * Eden's data folder, through whatever grant Foldcade holds.
 * Paths are relative to that folder. Only `keys/` and `nand/system/Contents/` are written.
 */
internal interface EdenTree {
    fun exists(relativePath: String): Boolean
    fun size(relativePath: String): Long?
    fun list(relativeDir: String): List<EdenNode>
    fun read(relativePath: String): ByteArray
    fun write(relativePath: String, bytes: ByteArray)
    fun writeStream(relativePath: String, source: InputStream): Long
    fun delete(relativePath: String)
    fun mkdir(relativePath: String)

    /** Renames [path] to [newName] in the same directory. */
    fun rename(path: String, newName: String)
}

/** Where missing keys and firmware are downloaded from. */
internal interface EdenFileSource {
    suspend fun server(): RommSwitchFirmware?

    suspend fun download(
        origin: String,
        file: FirmwareSummary,
        target: Path,
        progress: ((RomProgress) -> Unit)?,
    )
}

/** Whether [EdenInstaller.ensure] put a file into Eden's folder on this pass. */
internal data class EdenEnsure(val added: Boolean)

/**
 * Adds the Switch keys and firmware Eden is missing.
 * One install at a time. RomM is contacted only when `prod.keys` or firmware
 * is missing; a missing `title.keys` is filled in that same pass.
 * [EdenEnsure.added] is true when a key file was renamed into place, or a
 * firmware install finished. A failure is logged and swallowed, and so is a
 * zip Eden refuses: added stays false, the launch still starts, and the next
 * one tries again because the file is still missing.
 * A firmware zip Eden refuses for its layout, or whose bytes do not match
 * RomM's size or md5, is skipped until that zip changes on RomM.
 */
internal class EdenInstaller(
    private val openTree: (packageName: String) -> EdenTree?,
    private val source: EdenFileSource,
    private val cacheDirectory: () -> Path,
    private val warn: (String, Throwable?) -> Unit = { message, error ->
        if (error == null) android.util.Log.w(TAG, message) else android.util.Log.w(TAG, message, error)
    },
    private val refusedFirmware: () -> Set<String> = { emptySet() },
    private val rememberRefusedFirmware: (String) -> Unit = {},
) {
    private val gate = Mutex()

    /**
     * [onAdded] runs as each file lands, on this worker, so a pass cancelled
     * after it added something still says so.
     */
    suspend fun ensure(
        packageName: String,
        onProgress: ((RomProgress) -> Unit)? = null,
        onAdded: () -> Unit = {},
    ): EdenEnsure =
        gate.withLock {
            withContext(Dispatchers.IO) {
                try {
                    EdenEnsure(addMissing(packageName, onProgress, onAdded))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    warn("Could not add Eden keys or firmware", error)
                    EdenEnsure(added = false)
                }
            }
        }

    /** True when a key was renamed into place, or a firmware install finished. */
    private suspend fun addMissing(
        packageName: String,
        onProgress: ((RomProgress) -> Unit)?,
        onAdded: () -> Unit,
    ): Boolean {
        val tree = openTree(packageName) ?: return false
        val gaps = edenGaps(inventory(tree))
        // A missing title.keys does not contact RomM by itself.
        if (!gaps.prodKeys && !gaps.firmware) return false
        val server = source.server()?.takeUnless { it.failed } ?: return false
        var added = false
        fun landed() {
            added = true
            onAdded()
        }
        if (gaps.prodKeys && installKey(tree, server, PROD_KEYS, KEYS_PROD)) landed()
        if (gaps.titleKeys && installKey(tree, server, TITLE_KEYS, KEYS_TITLE)) landed()
        if (gaps.firmware && installFirmware(tree, server, onProgress)) landed()
        return added
    }

    private fun inventory(tree: EdenTree): EdenInventory = EdenInventory(
        prodKeysSize = tree.size(KEYS_PROD),
        titleKeysSize = tree.size(KEYS_TITLE),
        ncaNames = tree.list(REGISTERED).filter { !it.directory }.map { it.name },
        partialMarker = tree.exists(MARKER),
    )

    /**
     * Stages the key at `keys/<name>.foldcade-partial`, checks the bytes, then renames it to `<name>`.
     * A failure deletes that temp. `<name>` stays as it was until the rename, except an empty file,
     * which is removed once the temp checks out so the rename can land.
     * True when [name] was renamed into place and the bytes there match.
     */
    private suspend fun installKey(tree: EdenTree, server: RommSwitchFirmware, name: String, path: String): Boolean {
        val existing = tree.size(path)
        if (existing != null && existing > 0L) return false
        val file = chooseKeyFile(server.files, name) ?: return false
        val cache = cacheDirectory()
        val downloaded = cache.resolve("key-${file.id}")
        val partial = path + KEY_PARTIAL_SUFFIX
        var partialLeft = false
        try {
            source.download(server.origin, file, downloaded, progress = null)
            val bytes = Files.readAllBytes(downloaded)
            if (tree.size(path)?.let { it > 0L } == true) return false
            tree.mkdir(KEYS)
            if (tree.exists(partial)) tree.delete(partial)
            partialLeft = true
            tree.write(partial, bytes)
            if (!tree.read(partial).contentEquals(bytes)) {
                warn("Eden $name did not match after writing", null)
                return false
            }
            val current = tree.size(path)
            if (current != null && current > 0L) return false
            if (current != null) tree.delete(path)
            tree.rename(partial, name)
            partialLeft = false
            if (tree.size(path) != bytes.size.toLong()) {
                runCatching { tree.delete(path) }
                warn("Eden $name did not match after writing", null)
                return false
            }
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            warn("Could not add Eden $name", error)
        } finally {
            if (partialLeft) runCatching { tree.delete(partial) }
            withContext(NonCancellable) { deleteDownload(downloaded) }
        }
        return false
    }

    /** True when the firmware copy finished and the partial marker was removed. */
    private suspend fun installFirmware(
        tree: EdenTree,
        server: RommSwitchFirmware,
        onProgress: ((RomProgress) -> Unit)?,
    ): Boolean {
        val refused = refusedFirmware()
        val file = chooseFirmwareZip(server.files.filter { refusedFirmwareKey(server.origin, it) !in refused }) ?: return false
        val cache = cacheDirectory()
        val target = cache.resolve("${file.id}.zip")
        try {
            // The tile is marked on the first report. Send it before RomM answers, or a second press restarts this download.
            onProgress?.invoke(RomProgress.Receiving(0, file.fileSizeBytes.takeIf { it > 0L }))
            try {
                source.download(server.origin, file, target, onProgress)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (firmwareBytesRefused(error)) rememberRefused(server.origin, file)
                throw error
            }
            // Bytes RomM vouched for that are not a zip are refused like a bad layout.
            val opened = try {
                ZipFile(target.toFile())
            } catch (unreadable: ZipException) {
                rememberRefused(server.origin, file)
                warn("Eden firmware zip could not be read", unreadable)
                return false
            }
            opened.use { archive ->
                val entries = archive.entries().toList()
                if (!firmwareZipOk(entries.map { FirmwareZipEntry(it.name, it.isDirectory) })) {
                    rememberRefused(server.origin, file)
                    warn("Eden firmware zip was refused", null)
                    return false
                }
                val markerAlready = tree.exists(MARKER)
                // A complete firmware that appeared while the zip downloaded stays.
                if (!markerAlready && hasFirmware(tree)) return false
                mkdirFirmware(tree)
                if (!tree.exists(MARKER)) tree.write(MARKER, ByteArray(0))
                if (markerAlready) {
                    try {
                        firmwareNames(tree).forEach { tree.delete("$REGISTERED/$it") }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        // The marker stays, so the next launch still treats this copy as partial.
                        warn("Could not install Eden firmware", error)
                        return false
                    }
                }
                val written = mutableListOf<String>()
                try {
                    for (entry in entries) {
                        val dest = "$REGISTERED/${entry.name}"
                        // Count it before the write, so a failed copy is still removed.
                        written += dest
                        val count = archive.getInputStream(entry).use { tree.writeStream(dest, it) }
                        val stored = tree.size(dest)
                        val expected = entry.size
                        if (stored == null || stored != count || (expected >= 0 && count != expected)) {
                            error("firmware file ${entry.name} size")
                        }
                    }
                    tree.delete(MARKER)
                    return true
                } catch (cancelled: CancellationException) {
                    rollbackWritten(tree, written)
                    throw cancelled
                } catch (error: Exception) {
                    rollbackWritten(tree, written)
                    // A damaged entry fails the same way every time. A write to Eden's folder may not.
                    if (error is ZipException) rememberRefused(server.origin, file)
                    warn("Could not install Eden firmware", error)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            warn("Could not install Eden firmware", error)
        } finally {
            withContext(NonCancellable) {
                deleteDownload(target)
            }
        }
        return false
    }

    /**
     * Drops the NCAs this attempt wrote. The partial marker stays when any of
     * those deletes fails, so the next launch still treats the copy as partial.
     */
    private fun rollbackWritten(tree: EdenTree, written: List<String>) {
        var cleared = true
        for (path in written) {
            if (runCatching { tree.delete(path) }.isFailure) cleared = false
        }
        if (cleared) runCatching { tree.delete(MARKER) }
    }

    /** A finished download, its partial, and the verified marker RomM's client keeps beside it. Nothing is kept for offline use. */
    private fun deleteDownload(target: Path) {
        val name = target.fileName.toString()
        listOf(name, "$name.partial", "$name.verified").forEach { Files.deleteIfExists(target.resolveSibling(it)) }
    }

    private fun hasFirmware(tree: EdenTree): Boolean = firmwareNames(tree).isNotEmpty()

    private fun firmwareNames(tree: EdenTree): List<String> =
        tree.list(REGISTERED).filter { !it.directory && it.name.endsWith(".nca", ignoreCase = true) }.map { it.name }

    private fun mkdirFirmware(tree: EdenTree) {
        listOf("nand", "nand/system", CONTENTS, REGISTERED).forEach { tree.mkdir(it) }
    }

    private fun rememberRefused(origin: String, file: FirmwareSummary) {
        rememberRefusedFirmware(refusedFirmwareKey(origin, file))
    }

    private companion object {
        const val TAG = "Foldcade"
    }
}

private const val PROD_KEYS = "prod.keys"
private const val TITLE_KEYS = "title.keys"
private const val KEYS = "keys"
private const val KEYS_PROD = "keys/prod.keys"
private const val KEYS_TITLE = "keys/title.keys"
private const val KEY_PARTIAL_SUFFIX = ".foldcade-partial"
private const val CONTENTS = "nand/system/Contents"
private const val REGISTERED = "nand/system/Contents/registered"
private const val MARKER = "nand/system/Contents/foldcade-firmware.partial"
