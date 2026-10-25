package app.foldcade.plugins.aps3e

import app.foldcade.api.plugin.FirmwareOffer

/**
 * The one PUP to stage, the highest version whose header starts with `SCEUF`.
 * `version.txt` is entry `0x100`, and it counts only when those bytes sit in
 * the header the shell passed. A version that cannot be read ranks below one
 * that can. Equal versions keep the earlier offer. None of them means nothing
 * is written.
 */
internal fun choosePs3Pup(offered: List<FirmwareOffer>): List<FirmwareOffer> {
    val pups = offered.filter { it.header.size >= SCEUF.size && it.header.copyOfRange(0, SCEUF.size).contentEquals(SCEUF) }
    if (pups.isEmpty()) return emptyList()
    var best = pups.first()
    var bestVersion = pupVersion(best.header)
    for (offer in pups.drop(1)) {
        val version = pupVersion(offer.header)
        val better = when {
            version == null -> false
            bestVersion == null -> true
            else -> compareVersions(version, bestVersion) > 0
        }
        if (better) {
            best = offer
            bestVersion = version
        }
    }
    return listOf(best)
}

/** Dotted numeric parts of entry `0x100`, when that entry's bytes are inside [header]. */
internal fun pupVersion(header: ByteArray): List<Int>? {
    if (header.size < 48 + 32 || !header.copyOfRange(0, SCEUF.size).contentEquals(SCEUF)) return null
    val count = u64be(header, 24) ?: return null
    if (count > MAX_ENTRIES) return null
    for (index in 0L until count) {
        val at = 48L + index * 32L
        if (at < 0 || at > Int.MAX_VALUE - 32 || at + 32 > header.size) return null
        val entry = at.toInt()
        val id = u64be(header, entry) ?: return null
        if (id != VERSION_ENTRY) continue
        val offset = u64be(header, entry + 8) ?: return null
        val length = u64be(header, entry + 16) ?: return null
        if (offset < 0 || length < 0 || offset > header.size || length > header.size - offset) return null
        val end = offset + length
        if (end > Int.MAX_VALUE) return null
        val text = header.copyOfRange(offset.toInt(), end.toInt()).toString(Charsets.UTF_8)
        return versionParts(text)
    }
    return null
}

private fun versionParts(text: String): List<Int>? {
    val line = text.trim().lineSequence().firstOrNull()?.trim().orEmpty()
    if (line.isEmpty()) return null
    val parts = line.split('.')
    if (parts.any { part -> part.isEmpty() || part.any { !it.isDigit() } }) return null
    return try {
        parts.map { it.toInt() }
    } catch (_: NumberFormatException) {
        null
    }
}

private fun compareVersions(left: List<Int>, right: List<Int>): Int {
    val count = maxOf(left.size, right.size)
    for (index in 0 until count) {
        val compared = left.getOrElse(index) { 0 }.compareTo(right.getOrElse(index) { 0 })
        if (compared != 0) return compared
    }
    return 0
}

private fun u64be(bytes: ByteArray, at: Int): Long? {
    if (at < 0 || at + 8 > bytes.size) return null
    var value = 0L
    for (index in 0 until 8) {
        value = (value shl 8) or (bytes[at + index].toLong() and 0xFF)
    }
    return value.takeIf { it >= 0 }
}

private val SCEUF = byteArrayOf(0x53, 0x43, 0x45, 0x55, 0x46, 0, 0, 0)

private const val VERSION_ENTRY = 0x100L
private const val MAX_ENTRIES = 10_000L
