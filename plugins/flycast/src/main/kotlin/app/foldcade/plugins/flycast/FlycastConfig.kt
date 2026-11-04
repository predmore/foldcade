package app.foldcade.plugins.flycast

import app.foldcade.api.plugin.SaveFolderAccess
import java.net.URI

/** Mirrors cfg/ini.cpp and option.h (case-sensitive names, last value wins).
 * Per-game keys are config.PerGameVmu in the product-number section; VMUPath
 * is global and never per-game. All parsing is read only.
 * https://github.com/flyinghead/flycast/tree/7adbf3caef251b153e348df3f469a0f35878339d/core/cfg
 */
internal class FlycastConfig(private val values: Map<Pair<String, String>, String>) {
    fun perGame(id: String?): Boolean {
        if (booleanValue(id, "UsePhysicalVmuMemory", false)) return false
        return booleanValue(id, "PerGameVmu", true)
    }

    private fun booleanValue(id: String?, name: String, default: Boolean): Boolean {
        val value = id?.let { values[it to "config.$name"] } ?: values["config" to name] ?: return default
        return value.lowercase() in setOf("yes", "true", "on", "1")
    }

    fun vmuDirectory(root: String?): String? {
        val value = values["config" to "Dreamcast.VMUPath"].orEmpty()
        if (value.isEmpty()) return "data"
        val rootId = documentId(root ?: return null) ?: return null
        val destId = (if (value.startsWith("content:")) documentId(value) else when {
            value.startsWith("/storage/emulated/0/") -> "primary:" + value.removePrefix("/storage/emulated/0/")
            value.startsWith("/sdcard/") -> "primary:" + value.removePrefix("/sdcard/")
            else -> null
        }) ?: return null
        if (value.startsWith("content:")) {
            val authority = runCatching { URI(value).authority }.getOrNull() ?: return null
            if (authority != runCatching { URI(root).authority }.getOrNull() && authority != "com.android.externalstorage.documents") return null
        }
        val prefix = rootId.trimEnd('/')
        val relative = if (destId.trimEnd('/') == prefix) "" else destId.removePrefix("$prefix/").takeIf { it != destId } ?: return null
        return relative.takeIf { it.isEmpty() || it.split('/').all { part -> part.isNotEmpty() && part != "." && part != ".." && '\\' !in part && part.all { c -> c.code >= 32 } } }
    }
}

private fun documentId(uri: String): String? = runCatching {
    val path = URI(uri).path
    when {
        "/document/" in path -> path.substringAfter("/document/")
        "/tree/" in path -> path.substringAfter("/tree/")
        else -> null
    }
}.getOrNull()

internal fun flycastConfig(folder: SaveFolderAccess): FlycastConfig? {
    val bytes = folder.read("emu.cfg", 256 * 1024) ?: return null
    if (bytes.size >= 256 * 1024) return null
    val values = mutableMapOf<Pair<String, String>, String>()
    var section = ""
    for (line in bytes.toString(Charsets.UTF_8).split('\n')) {
        val start = line.trimStart()
        when {
            start.isEmpty() || start.startsWith(';') -> continue
            start.startsWith('[') -> { if (']' in start) section = start.substringAfter('[').substringBefore(']') }
            '=' in line -> {
                val key = line.substringBefore('=').trim(' ', '\t')
                var value = line.substringAfter('=').removePrefix(" ")
                if (value.startsWith('"')) value = value.drop(1).substringBeforeLast('"')
                values[section to key] = value
            }
        }
    }
    return FlycastConfig(values)
}
