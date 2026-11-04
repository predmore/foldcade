package app.foldcade

import app.foldcade.api.plugin.PlayerExtra
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.net.PublicHttps
import app.foldcade.plugins.retroarch.Core
import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal const val CORE_SIDELOAD_ACTIVITY = "com.retroarch.browser.debug.CoreSideloadActivity"

/** Match RetroArch's installed native library directory, rather than the launcher's own ABI. */
internal enum class RetroArchAbi(val directory: String, val elfClass: Int, val machine: Int) {
    Arm64("arm64-v8a", 2, 183), Arm32("armeabi-v7a", 1, 40);

    companion object {
        fun fromLibraryDirectory(path: String): RetroArchAbi? = when (path.substringAfterLast('/')) {
            "arm64" -> Arm64
            "arm" -> Arm32
            else -> null
        }
    }
}

internal data class PreparedRetroArchCore(val packageName: String, val core: Core, val file: File) {
    /**
     * Install and open as one handoff, after Foldcade has placed saves. No RetroArch menu visit.
     * v1.22.2/pkg/android/phoenix/src/com/retroarch/browser/debug/CoreSideloadActivity.java:
     * copies LIBRETRO to cores/, then starts RetroActivityFuture with ROM. No .info is required.
     */
    fun sideload(intent: PlayerIntent): PlayerIntent {
        if (intent.packageName != packageName) return intent
        val corePath = intent.extras.filterIsInstance<PlayerExtra.Text>().firstOrNull { it.key == "LIBRETRO" }?.value
        if (corePath?.substringAfterLast('/') != core.fileName) return intent
        return intent.copy(componentClass = CORE_SIDELOAD_ACTIVITY, extras = intent.extras.map {
            if (it is PlayerExtra.Text && it.key == "LIBRETRO") it.copy(value = file.absolutePath) else it
        })
    }

    fun installedIntent(intent: PlayerIntent): PlayerIntent =
        if (intent.packageName == packageName && intent.componentClass == CORE_SIDELOAD_ACTIVITY) intent.copy(
            componentClass = app.foldcade.plugins.retroarch.RETRO_ACTIVITY,
            extras = intent.extras.map {
                if (it is PlayerExtra.Text && it.key == "LIBRETRO") it.copy(value = "/data/user/0/$packageName/cores/${core.fileName}") else it
            },
        ) else intent
}

/** Missing cores only. Installed cores and the player's core choices are never replaced. */
internal class RetroArchCoreDownload(private val directory: () -> File) {
    private val mutex = Mutex()

    suspend fun prepare(
        packageName: String,
        core: Core,
        abi: RetroArchAbi,
        progress: (Long, Long?) -> Unit,
    ): PreparedRetroArchCore = mutex.withLock {
        withContext(Dispatchers.IO) {
            // Keep a validated archive for retries, but publish a .so only after every check passes.
            val folder = File(directory(), "${abi.directory}/${core.file}")
            check(folder.mkdirs() || folder.isDirectory)
            val archive = File(folder, "core.zip")
            val partial = File(folder, "download.part")
            val unpacking = File(folder, "core.part")
            val coreFile = File(folder, core.fileName)
            try {
                if (!archive.isFile) {
                    PublicHttps(setOf("buildbot.libretro.com")).use { http ->
                        http.download(coreDownloadUrl(core, abi), partial, MAX_CORE_ZIP_BYTES, progress)
                    }
                    currentCoroutineContext().ensureActive()
                    if (!partial.renameTo(archive)) throw IOException("Cannot keep core archive")
                }
                progress(archive.length(), archive.length())
                extractCore(archive, unpacking, core, abi)
                currentCoroutineContext().ensureActive()
                if (!unpacking.renameTo(coreFile)) throw IOException("Cannot stage core")
                PreparedRetroArchCore(packageName, core, coreFile)
            } catch (failure: Exception) {
                // A corrupt cached response must not make Retry repeat the same failure forever.
                archive.delete()
                throw failure
            } finally {
                partial.delete()
                unpacking.delete()
            }
        }
    }
}

// The versioned 1.22.2 Android directory contains APKs, no core archives. RetroArch's own
// updater uses the nightly feed: https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/
internal fun coreDownloadUrl(core: Core, abi: RetroArchAbi): String =
    "https://buildbot.libretro.com/nightly/android/latest/${abi.directory}/${core.fileName}.zip"

private const val MAX_CORE_ZIP_BYTES = 64L * 1024 * 1024
private const val MAX_CORE_BYTES = 256L * 1024 * 1024

/** Check central-directory size, streamed size, CRC, and the ELF class/type/machine. */
internal suspend fun extractCore(archive: File, destination: File, core: Core, abi: RetroArchAbi) {
    ZipFile(archive).use { zip ->
        val entries = zip.entries().toList()
        val entry = entries.singleOrNull()?.takeIf { !it.isDirectory && it.name == core.fileName }
            ?: throw IOException("Unexpected core archive")
        if (entry.size !in 64..MAX_CORE_BYTES) throw IOException("Invalid core size")
        val checksum = CRC32()
        var size = 0L
        zip.getInputStream(entry).use { input ->
            destination.outputStream().use { output ->
                val header = input.readNBytes(64)
                if (!validCoreElf(header, abi)) throw IOException("Wrong core architecture")
                checksum.update(header)
                output.write(header)
                size += header.size
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    size += count
                    if (size > MAX_CORE_BYTES || size > entry.size) throw IOException("Core is too large")
                    checksum.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
                if (size != entry.size || checksum.value != entry.crc) throw IOException("Damaged core archive")
                output.fd.sync()
            }
        }
    }
}

internal fun validCoreElf(header: ByteArray, abi: RetroArchAbi): Boolean = header.size >= 64 &&
    header.take(4) == listOf(0x7f.toByte(), 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()) &&
    header[4].toInt() == abi.elfClass && header[5].toInt() == 1 && header[6].toInt() == 1 &&
    header[16].toInt() == 3 && header[17].toInt() == 0 && // ET_DYN, little endian
    (header[18].toInt() and 255) == abi.machine && header[19].toInt() == 0
