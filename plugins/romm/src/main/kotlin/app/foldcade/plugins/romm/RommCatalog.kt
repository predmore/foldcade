package app.foldcade.plugins.romm

import app.foldcade.romm.PlatformSummary
import app.foldcade.romm.RomPage
import app.foldcade.romm.RomQuery
import app.foldcade.romm.RomSummary
import app.foldcade.romm.writeDurably
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Server-scoped metadata and complete, unfiltered listings. All disk work runs on IO. */
internal class RommCatalog(
    private val file: Path,
    private val origin: String,
    private val write: (Path, ByteArray) -> Unit = ::writeDurably,
) {
    private var restored = false
    private var platforms: List<PlatformSummary>? = null
    /**
     * Concurrent so [rom] can answer the main thread's focus lookups without the
     * monitor, which a sync holds while it encodes and writes the whole snapshot.
     */
    private val roms = ConcurrentHashMap<String, RomSummary>()
    private val listings = HashMap<Long, List<String>>()
    private val incoming = HashMap<Long, MutableList<String>>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var refreshes = 0
    private var dirty = false
    private var persisted: Snapshot? = null

    @Synchronized
    fun restore() {
        if (restored) return
        restored = true
        try {
            val saved = json.decodeFromString<Snapshot>(String(Files.readAllBytes(file), Charsets.UTF_8))
            if (saved.version != 1) return
            platforms = saved.platforms
            saved.roms.forEach { roms.putIfAbsent(it.id.toString(), it) }
            listings.putAll(saved.listings)
            persisted = snapshot()
        } catch (_: IOException) {
            // A first launch or an evicted file needs a fresh listing.
        } catch (_: SerializationException) {
            // A damaged snapshot must not stop online setup or a verified ROM launch.
        } catch (_: IllegalArgumentException) {
            Unit
        }
    }

    @Synchronized
    fun rememberPlatforms(items: List<PlatformSummary>) {
        restore()
        if (platforms != items) {
            platforms = items.toList()
            dirty = true
        }
        saveIfReady()
    }

    @Synchronized
    fun rememberRoms(items: List<RomSummary>) {
        restore()
        items.forEach {
            val previous = roms.put(it.id.toString(), it)
            if (previous?.withoutArtworkCredentials() != it.withoutArtworkCredentials()) dirty = true
        }
        saveIfReady()
    }

    /** A search or interrupted refresh cannot replace a complete offline listing. */
    @Synchronized
    fun rememberPage(platformId: Long, query: RomQuery, page: RomPage, finished: Boolean) {
        rememberRoms(page.items)
        if (!query.searchTerm.isNullOrBlank()) return
        if (query.offset == 0) incoming[platformId] = ArrayList()
        val collected = incoming[platformId] ?: return
        if (collected.size != query.offset) {
            incoming.remove(platformId)
            return
        }
        collected += page.items.map { it.id.toString() }
        if (finished) {
            val complete = collected.distinct()
            if (listings.put(platformId, complete) != complete) dirty = true
            incoming.remove(platformId)
            saveIfReady()
        }
    }

    /** Page updates stay available in memory; a refresh pays for one durable snapshot. */
    @Synchronized
    fun beginRefresh() {
        restore()
        if (refreshes++ == 0) incoming.clear()
    }

    /** Also flushes on cancellation, keeping completed listings and the previous incomplete one. */
    @Synchronized
    fun finishRefresh() {
        check(refreshes > 0)
        if (--refreshes == 0) incoming.clear()
        saveIfReady()
    }

    fun rom(remoteKey: String): RomSummary? = roms[remoteKey]

    @Synchronized
    fun platforms(): List<PlatformSummary> = platforms.orEmpty().toList()

    @Synchronized
    fun hasPlatforms(): Boolean = platforms != null

    @Synchronized
    fun listing(platformId: Long): List<RomSummary>? = listings[platformId]?.mapNotNull(roms::get)

    private fun snapshot() = Snapshot(
        platforms = platforms,
        roms = roms.values.sortedBy { it.id }.map { it.withoutArtworkCredentials() },
        listings = listings.toMap(),
    )

    private fun saveIfReady() {
        if (refreshes != 0 || !dirty) return
        try {
            val snapshot = snapshot()
            if (snapshot != persisted) {
                write(file, json.encodeToString(snapshot).toByteArray(Charsets.UTF_8))
                persisted = snapshot
            }
            dirty = false
        } catch (_: IOException) {
            // Metadata persistence must not fail a successful network request.
        }
    }

    private fun RomSummary.withoutArtworkCredentials(): RomSummary = copy(
        pathCoverSmall = absoluteHttp(origin, pathCoverSmall),
        pathCoverLarge = absoluteHttp(origin, pathCoverLarge),
        urlCover = absoluteHttp(origin, urlCover),
        screenshots = screenshots.mapNotNull { absoluteHttp(origin, it) },
        achievements = achievements.map { achievement ->
            achievement.copy(
                badgePath = absoluteHttp(origin, achievement.badgePath),
                badgePathLock = absoluteHttp(origin, achievement.badgePathLock),
                badgeUrl = absoluteHttp(origin, achievement.badgeUrl),
                badgeUrlLock = absoluteHttp(origin, achievement.badgeUrlLock),
            )
        },
    )

    @Serializable
    private data class Snapshot(
        val version: Int = 1,
        val platforms: List<PlatformSummary>?,
        val roms: List<RomSummary>,
        val listings: Map<Long, List<String>>,
    )
}
