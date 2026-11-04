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

/** The source stamp survives process death; incomplete copies are never reused. */
internal fun reuseFirmwareArchive(
    root: java.io.File,
    uri: String,
    name: String,
    version: FirmwareSourceVersion,
    copy: (java.io.File) -> Unit,
): java.io.File {
    val key = java.security.MessageDigest.getInstance("SHA-256").digest(uri.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    val folder = java.io.File(root, key).apply { mkdirs() }
    val target = java.io.File(folder, name)
    val stamp = java.io.File(folder, ".source-version")
    val expected = "${version.size}:${version.modifiedMillis}"
    if (version.modifiedMillis > 0 && target.isFile && target.length() == version.size &&
        runCatching { stamp.readText() }.getOrNull() == expected) return target
    val partial = java.io.File.createTempFile(".archive-", ".partial", folder)
    try {
        copy(partial)
        check(version.size <= 0 || partial.length() == version.size)
        check(partial.renameTo(target))
        if (version.modifiedMillis > 0) stamp.writeText(expected) else stamp.delete()
        return target
    } finally { partial.delete() }
}
