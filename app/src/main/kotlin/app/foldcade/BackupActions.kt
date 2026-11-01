package app.foldcade

import android.content.Context
import android.os.Build
import app.foldcade.host.backup.*
import app.foldcade.language.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Call off the main thread. SharedPreferences copies its values for the snapshot. */
internal fun FoldcadeApp.setupSnapshot(): SetupBackup {
    val all = getSharedPreferences("foldcade", Context.MODE_PRIVATE).all
    val portable = BackupSettings.portable(all)
    val home = portable["home_board"] as? String ?: encodeHome(HomeBoard())
    val summary = backupHomeSummary(decodeHome(home), emptySet())
    val info = packageManager.getPackageInfo(packageName, 0)
    val folders = (store.folderTrees().map { uri ->
        val name = runCatching { java.net.URI(uri).path.substringAfterLast('/').substringAfterLast(':') }.getOrDefault("ROMs")
        BackupFolder(uri, name.ifBlank { "ROMs" })
    } + store.restoreFolders()).distinctBy { it.uri }
    val play = ByteArrayOutputStream().also(plays::export).toByteArray()
    val themes = File(filesDir, "themes").listFiles().orEmpty().filter { it.isFile && it.extension == "zip" }.sortedBy { it.name }
    require(themes.sumOf { it.length() } <= SetupArchive.MAX_THEMES_BYTES) { "Themes are too large" }
    val players = all.entries.filter { it.key.startsWith("player_save_folder:") && it.value is String && (it.value as String).isNotBlank() }
        .map { it.key.removePrefix("player_save_folder:") }
    val theme = (all["theme"] as? String)?.let { id -> themePacks.firstOrNull { it.resources.id == id }?.name ?: id } ?: "Afterglow"
    return SetupBackup(
        BackupManifest(System.currentTimeMillis(), info.versionName.orEmpty(), info.longVersionCode, Build.MODEL,
            BackupSummary(summary.games, summary.collections, summary.hidden, theme), folders, players,
            store.moonlightPins().isNotEmpty() || all["moonlight_paired_hosts"] != null ||
                store.restoreHolds().sources.values.any { it.any { source -> source.startsWith("moonlight:") || source == "moonlight" } },
            all.any { (key, value) -> key.startsWith("retroarch_cores_folder:") && value is String && value.isNotBlank() },
            all.any { (key, value) -> key.startsWith("eden_folder:") && value is String && value.isNotBlank() }),
        portable - setOf("home_board", "same_games"), home, portable["same_games"] as? String ?: "",
        File(filesDir, "art/choices.json").takeIf { it.isFile }?.readText() ?: "{}", play,
        themes.associate { it.name to it.readBytes() },
    )
}

internal fun backupPreview(backup: SetupBackup, available: Set<String>): Pair<String, String> {
    val summary = backupHomeSummary(decodeHome(backup.home), available + decodeSameGames(backup.sameGames).filterKeys { it in available }.values)
    val date = DateTimeFormatter.ofPattern("MMM d, yyyy").format(Instant.ofEpochMilli(backup.manifest.createdMillis).atZone(ZoneId.systemDefault()))
    return date to BackupCopy.preview(summary.games, summary.collections, summary.hidden, backup.manifest.summary.theme, summary.missingGames, summary.missingApps)
}

/** Before SessionStore exists. Add a theme's validator still decides which restored packs load. */
internal fun FoldcadeApp.applySetupRestore() {
    if (!setupBackups.pending()) return
    kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        val grants = contentResolver.persistedUriPermissions.filter { it.isReadPermission }.mapTo(mutableSetOf()) { it.uri.toString() }
        val packs = PackagedTheme.loadAll(this@applySetupRestore)
        val resources = packs.associate { it.resources.id to it.resources }.toMutableMap()
        val bundled = setOf(FALLBACK_THEME_ID) + packs.filter { it.archive == null }.map { it.resources.id }
        setupBackups.applyStaged(grants, restoreThemes = { archives ->
            // Inspect in place, as Add a theme does. Reading a whole inner zip just to find its
            // manifest could inflate a resource pack far past the backup's memory budget.
            fun manifest(bytes: ByteArray): ThemeFile? {
                val temporary = File.createTempFile("backup-theme-", ".zip", cacheDir)
                return try {
                    temporary.writeBytes(bytes)
                    openThemeArchive(temporary)?.use { it.file }
                } finally { temporary.delete() }
            }
            val manifests = archives.mapValues { runCatching { manifest(it.value) }.getOrNull() }
            val remaining = archives.toMutableMap()
            val opened = mutableListOf<ThemeZip>()
            try {
                while (remaining.isNotEmpty()) {
                    val next = remaining.entries.firstOrNull { (name, _) ->
                        val parent = manifests[name]?.extends
                        parent == null || remaining.keys.none { manifests[it]?.id == parent }
                    } ?: remaining.entries.first()
                    try {
                        val parent = manifests[next.key]?.extends?.let(resources::get) ?: resources[FALLBACK_THEME_ID]
                        val installed = themeInstalls.install(next.value.inputStream(), bundled, restoring = true) { zip ->
                            themeInstallable(zip, parent)
                        }
                        if (installed is ThemeInstallResult.Installed) {
                            openThemeArchive(File(filesDir, "themes/${installed.theme.file}"))?.let { zip ->
                                opened += zip
                                resources[zip.file.id] = resolveThemeResources(zip, parent)
                            }
                        }
                    } catch (failure: Exception) {
                        android.util.Log.w("FoldcadeBackup", "Restored theme ${next.key} skipped", failure)
                    }
                    remaining.remove(next.key)
                }
            } finally { opened.forEach { it.close() } }
        })
    }
}
