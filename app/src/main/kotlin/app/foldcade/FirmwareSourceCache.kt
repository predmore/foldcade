package app.foldcade

import app.foldcade.api.plugin.FirmwareBytes
import java.util.concurrent.ConcurrentHashMap

internal data class FirmwareSourceVersion(val size: Long, val modifiedMillis: Long)

/** Only provider-reported file versions are reused; unknown timestamps are probed again. */
internal class FirmwareSourceCache {
    private data class Cached(val version: FirmwareSourceVersion, val files: List<FirmwareBytes>)
    private val entries = ConcurrentHashMap<String, Cached>()

    fun get(key: String, version: FirmwareSourceVersion): List<FirmwareBytes>? =
        if (version.modifiedMillis <= 0L) null else entries[key]?.takeIf { it.version == version }?.files

    fun put(key: String, version: FirmwareSourceVersion, files: List<FirmwareBytes>) {
        if (version.modifiedMillis <= 0L) return
        if (entries.size >= 512) entries.clear()
        entries[key] = Cached(version, files.toList())
    }
}
