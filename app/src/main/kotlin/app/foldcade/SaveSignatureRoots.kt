package app.foldcade

import app.foldcade.api.plugin.LiveSignal
import app.foldcade.api.plugin.SignalStrength
import app.foldcade.language.RootReading
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.util.ArrayDeque

/** A fresh signature already naming the bound root makes a storage walk unnecessary. */
internal fun needsSignatureDiscovery(bound: RootReading, signature: String, afterMillis: Long): Boolean =
    bound.signals.none { it.source == signature && it.proves && it.names == bound.root.identity &&
        it.modifiedAtMillis?.let { at -> at > afterMillis } == true }

/** Root depths 0–2 only; signature reads never cause recursive traversal. Caller holds all-files access. */
internal fun saveSignatureRoots(
    volume: File,
    signature: String,
    afterMillis: Long,
    signals: (String) -> List<LiveSignal>,
): List<String> {
    if (signature.split('/').any { it.isEmpty() || it == "." || it == ".." || '\\' in it }) return emptyList()
    val storage = SharedStorage(volume)
    val pending = ArrayDeque<Pair<String, Int>>().apply { add("primary:" to 0) }
    val matches = mutableListOf<Pair<String, Long>>()
    var entries = 0
    while (pending.isNotEmpty()) {
        val (id, depth) = pending.removeFirst()
        val directory = try { storage.fileOf(id) } catch (_: Exception) { continue }
        val file = try { storage.fileOf(id.trimEnd('/') + (if (id.endsWith(':')) "" else "/") + signature) } catch (_: Exception) { null }
        if (file?.isFile == true && file.lastModified() > afterMillis) {
            val fresh = try { signals(id).filter { signal -> signal.source == signature && signal.strength == SignalStrength.Proves &&
                signal.modifiedAtMillis != null && signal.modifiedAtMillis!! > afterMillis &&
                signal.namesUri?.let { storageIdentity(it) } == id
            }.maxOfOrNull { it.modifiedAtMillis!! } } catch (_: Exception) { null }
            if (fresh != null) matches += id to fresh
        }
        if (depth == 2 || entries >= 8192) continue
        try {
            Files.newDirectoryStream(directory.toPath()).use { children ->
                for (child in children) {
                    if (++entries > 8192) break
                    if (!Files.isDirectory(child, NOFOLLOW_LINKS)) continue
                    val childId = try { storage.childId(id, child.fileName.toString()) } catch (_: Exception) { continue }
                    val folder = try { storage.fileOf(childId) } catch (_: Exception) { continue }
                    if (folder.isDirectory) pending.add(childId to depth + 1)
                }
            }
        } catch (_: Exception) { Unit }
    }
    return matches.sortedByDescending { it.second }.map { it.first }.take(4)
}
