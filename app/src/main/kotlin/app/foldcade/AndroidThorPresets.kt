package app.foldcade

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import app.foldcade.language.ThorSetupOption
import app.foldcade.language.ThorSetupSetting
import app.foldcade.language.ThorSetupStatus
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/** Azahar presets use its authoritative user config without starting an emulator provider. */
internal class AndroidThorPresets(private val context: Context) {
    private val presets = ThorPresets(context.filesDir.toPath().resolve("emulator-presets"))
    private val packages = listOf("org.azahar_emu.azahar", "io.github.lime3ds.android")

    fun read(playerId: String, folder: String?): ThorSetupSetting? = operate(playerId, folder, Operation.Read)
    fun apply(playerId: String, folder: String?, onApplied: () -> Unit = {}): ThorSetupSetting? = operate(playerId, folder, Operation.Apply, onApplied)
    fun restore(playerId: String, folder: String?): ThorSetupSetting? = operate(playerId, folder, Operation.Restore)
    private enum class Operation { Read, Apply, Restore }

    @Synchronized
    private fun operate(playerId: String, folder: String?, operation: Operation, onApplied: () -> Unit = {}): ThorSetupSetting? {
        if (playerId != "azahar") return null
        fun state(status: ThorSetupStatus) = ThorSetupSetting(status)
        if (folder == null) return state(ThorSetupStatus.ChooseFolder)
        return try {
            val tree = Uri.parse(folder)
            // SAF grants do not imply raw path access. Only use this provider's known local ids,
            // under the SAME chosen tree. No scanning or guessing among Azahar's stale grants.
            val id = folderDocumentId(folder)
            val access = AndroidSaveFolderFinder(context)
            if (!access.holdsGrant(folder, access.grants(emptyList()))) return state(ThorSetupStatus.ChooseFolder)
            if (tree.authority != SharedPaths.EXTERNAL_STORAGE_AUTHORITY && tree.authority != "${context.packageName}.storage") {
                return state(ThorSetupStatus.FileAccess)
            }
            val document = DocumentsContract.buildDocumentUri(SharedPaths.EXTERNAL_STORAGE_AUTHORITY, id)
            val path = SharedPaths.externalStoragePath(document.toString())?.let(java.nio.file.Paths::get)
                ?: return state(ThorSetupStatus.FileAccess)
            if (id.substringAfter(':').split('/').firstOrNull().equals("Android", ignoreCase = true)) return state(ThorSetupStatus.Manual)
            val identity = "azahar\n$path"
            val file = LocalPresetFile(path.resolve("config/config.ini"))
            val prior = presets.status(identity, file)
            val supported = packages.firstNotNullOfOrNull { name ->
                runCatching { context.packageManager.getPackageInfo(name, 0).versionName }.getOrNull()
            }?.let { it == "2126.2" || it.startsWith("2126.2-") } == true
            val hasRecord = prior != ThorPresetStatus.Ready
            val status = when {
                // Reading a journal needs no external-storage permission. Undo asks for it if revoked.
                operation == Operation.Read && hasRecord -> prior
                operation == Operation.Apply && hasRecord && !presets.canRetry(identity) -> prior
                !supported && operation != Operation.Restore -> ThorPresetStatus.Manual
                operation == Operation.Read -> presets.inspect(identity, grantedConfig(folder),
                    writable = Files.isWritable(path.resolve("config")), stopped = ::stopped)
                !Files.isWritable(path.resolve("config")) -> {
                    // Inspect through the held grant even when a direct replacement is unavailable.
                    // Apply may only consume an already-set file here; it can never write it.
                    if (operation == Operation.Apply) presets.inspect(identity, grantedConfig(folder),
                        writable = false, stopped = ::stopped) else ThorPresetStatus.FileAccess
                }
                operation == Operation.Restore -> presets.restore(identity, file, ::stopped)
                else -> presets.apply(identity, file, onApplied = onApplied, stopped = ::stopped)
            }
            ThorSetupSetting(
                ThorSetupStatus.valueOf(status.name),
                presets.edits(identity).mapNotNull { key -> when (key) {
                    "layout_option" -> ThorSetupOption.MainScreen
                    "secondary_display_layout" -> ThorSetupOption.OtherScreen
                    "resolution_factor" -> ThorSetupOption.Resolution
                    "shaders_accurate_mul" -> ThorSetupOption.AccurateShading
                    else -> null
                } }.toSet(),
                presets.canRestore(identity),
            )
        } catch (_: java.nio.file.AccessDeniedException) { state(ThorSetupStatus.FileAccess) }
        catch (_: SecurityException) { state(ThorSetupStatus.FileAccess) }
        catch (_: Exception) { state(ThorSetupStatus.Failed) }
    }

    /** Only read through the chosen grant; missing or unreadable bytes cannot be mistaken for a tuned config. */
    private fun grantedConfig(folder: String): PresetFile = object : PresetFile {
        private val docs = AndroidSaveDocuments(context.contentResolver)
        private val entries by lazy { docs.children(folder, "config") }
        override fun read(): ByteArray? {
            val entry = entries?.firstOrNull { it.name == "config.ini" } ?: return null
            check(!entry.isFolder)
            val bytes = docs.head(entry.uri, CONFIG_LIMIT + 1) ?: error("config could not be read")
            check(bytes.size <= CONFIG_LIMIT)
            return bytes
        }
        override fun hasPriorBackup(): Boolean = entries.orEmpty()
            .any { it.name.startsWith("config.ini.foldcade-original-") }
        override fun backup(name: String, bytes: ByteArray) = error("read only")
        override fun replace(expected: ByteArray?, bytes: ByteArray?, stopped: () -> Boolean): Boolean = error("read only")
    }

    private companion object { const val CONFIG_LIMIT = 1024 * 1024 }

    /**
     * Android has no public API that reliably lists another app's cached processes. A cached
     * Azahar still holds settings and can overwrite them on exit. Require the package STOPPED
     * flag (new install or App info > Force stop); background/paused is never enough.
     * https://developer.android.com/reference/android/content/pm/ApplicationInfo#FLAG_STOPPED
     */
    private fun stopped(): Boolean = packageToClose() == null

    fun packageToClose(): String? = packages.firstOrNull { name ->
        val info = try { context.packageManager.getApplicationInfo(name, 0) }
        catch (_: PackageManager.NameNotFoundException) { return@firstOrNull false }
        info.flags and ApplicationInfo.FLAG_STOPPED == 0
    }

}

/** Ordinary local rename(2) is the only replacement used; SAF's unique-name rename is unsuitable. */
internal class LocalPresetFile(private val path: Path) : PresetFile {
    override fun read(): ByteArray? {
        checkSafePath()
        if (Files.notExists(path)) return null
        check(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= LIMIT)
        return Files.newInputStream(path).use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                check(out.size() + count <= LIMIT)
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        }
    }

    override fun hasPriorBackup(): Boolean {
        checkSafePath()
        return Files.newDirectoryStream(path.parent, "config.ini.foldcade-original-*").use { it.iterator().hasNext() }
    }

    override fun backup(name: String, bytes: ByteArray) {
        check(name.startsWith("config.ini.foldcade-") && '/' !in name && '\\' !in name)
        checkSafePath()
        val target = path.resolveSibling(name)
        check(!Files.exists(target))
        durablePresetWrite(target, bytes)
        check(Files.readAllBytes(target).contentEquals(bytes))
    }

    override fun replace(expected: ByteArray?, bytes: ByteArray?, stopped: () -> Boolean): Boolean {
        checkSafePath()
        // Stage and fsync before checking, so the race window contains only two reads and rename.
        val temporary = if (bytes != null) Files.createTempFile(path.parent, ".foldcade-preset-", ".tmp") else null
        try {
            if (temporary != null) java.io.FileOutputStream(temporary.toFile()).use { it.write(bytes!!); it.fd.sync() }
            if (!sameBytes(read(), expected) || !stopped()) return false
            if (temporary == null) Files.deleteIfExists(path) else Files.move(temporary, path,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            return sameBytes(read(), bytes)
        } finally { temporary?.let(Files::deleteIfExists) }
    }

    private fun checkSafePath() {
        var parent: Path? = path
        while (parent != null) {
            check(!Files.isSymbolicLink(parent))
            parent = parent.parent
        }
    }

    private fun sameBytes(a: ByteArray?, b: ByteArray?): Boolean = if (a == null || b == null) a == null && b == null else a.contentEquals(b)
    private companion object { const val LIMIT = 1024 * 1024 }
}
