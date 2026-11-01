package app.foldcade

import android.content.Context
import android.os.Build
import app.foldcade.host.backup.*
import app.foldcade.language.*
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Call off the main thread. SharedPreferences copies its values for the snapshot. */
internal fun FoldcadeApp.setupSnapshot(work: File, includeThemes: Boolean = true): SetupBackup {
    val all = getSharedPreferences("foldcade", Context.MODE_PRIVATE).all
    val portable = BackupSettings.portable(all)
    val home = portable["home_board"] as? String ?: encodeHome(HomeBoard())
    val summary = backupHomeSummary(decodeHome(home), emptySet())
    val info = packageManager.getPackageInfo(packageName, 0)
    val folders = carriedBackupFolders(store.folderTrees(), store.restoreFolders())
    val play = File(work, "play-history.zip")
    play.outputStream().use(plays::export)
    val themes = if (includeThemes) File(filesDir, "themes").listFiles().orEmpty().filter { it.isFile && it.extension == "zip" }.sortedBy { it.name } else emptyList()
    val art = File(filesDir, "art/choices.json")
    require(!art.isFile || art.length() <= 8L * 1024 * 1024) { "Art choices are too large" }
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
        art.takeIf { it.isFile }?.readText() ?: "{}", byteArrayOf(),
        playHistoryFile = play, themeFiles = themes.associateBy { it.name },
    )
}

internal fun carriedBackupFolders(trees: List<String>, missing: List<BackupFolder>): List<BackupFolder> =
    (trees.map { uri ->
        val name = runCatching { java.net.URI(uri).path.substringAfterLast('/').substringAfterLast(':') }.getOrDefault("ROMs")
        BackupFolder(uri, name.ifBlank { "ROMs" })
    } + missing).distinctBy { it.uri }

/** Outstanding picks use the existing source tile and its Remove page, even after a card changes. */
internal fun libraryFoldersWithOutstandingRestores(current: List<LibraryFolder>, missing: List<BackupFolder>): List<LibraryFolder> {
    val pending = missing.mapTo(mutableSetOf()) { it.uri }
    return (current + missing.map { LibraryFolder(it.uri, it.name) }).distinctBy { it.uri }.map {
        if (it.uri in pending) it.copy(label = "${it.label} · ${BackupCopy.pickAgain}") else it
    }
}

internal fun backupPreview(backup: SetupBackup, available: Set<String>, currentOrigin: String? = null): Pair<String, String> {
    val summary = backupHomeSummary(decodeHome(backup.home), available + decodeSameGames(backup.sameGames).filterKeys { it in available }.values)
    val date = DateTimeFormatter.ofPattern("MMM d, yyyy").format(Instant.ofEpochMilli(backup.manifest.createdMillis).atZone(ZoneId.systemDefault()))
    val origin = backup.settings["romm_origin"] as? String
    val switches = origin?.takeIf { it != currentOrigin }?.let {
        val host = java.net.URI(it).host
        if (currentOrigin.isNullOrBlank()) BackupCopy.connectRomm(host) else BackupCopy.switchedRomm(host)
    }
    return date to listOfNotNull(BackupCopy.preview(summary.games, summary.collections, summary.hidden, backup.manifest.summary.theme, summary.missingGames, summary.missingApps), switches).joinToString(" · ")
}

internal fun storedComputersNeedPairing(store: SessionStore, identity: String?): Boolean = store.pairedStreamHosts(identity).isEmpty()

/** Before SessionStore exists. Add a theme's validator still decides which restored packs load. */
internal fun FoldcadeApp.applySetupRestore() {
    kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        val grants = if (setupBackups.pending()) contentResolver.persistedUriPermissions.filter { it.isReadPermission }
            .mapTo(mutableSetOf()) { it.uri.toString() } else emptySet()
        setupBackups.resume(grants, restoreThemes = { archives ->
            val packs = PackagedTheme.loadAll(this@applySetupRestore)
            val resources = packs.associate { it.resources.id to it.resources }.toMutableMap()
            val bundled = setOf(FALLBACK_THEME_ID) + packs.filter { it.archive == null }.map { it.resources.id }
            val manifests = archives.mapValues { try { openThemeArchive(it.value)?.use { it.file } } catch (_: Exception) { null } }
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
                        val installed = next.value.inputStream().use { input -> themeInstalls.install(input, bundled, restoring = true) { zip ->
                            themeInstallable(zip, parent)
                        } }
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
