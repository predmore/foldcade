package app.foldcade.plugins.romm

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GamePage
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.ListedPlatform
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.Placement
import app.foldcade.api.plugin.PlacedSave
import app.foldcade.api.plugin.Platform
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.canonicalPlatformId
import app.foldcade.api.plugin.SaveSet
import app.foldcade.api.plugin.SaveSlot
import app.foldcade.api.plugin.SyncOutcome
import app.foldcade.api.plugin.SyncResult
import app.foldcade.romm.LocalSave
import app.foldcade.romm.RomQuery
import app.foldcade.romm.RomSummary
import app.foldcade.romm.RommClient
import app.foldcade.romm.RommException
import app.foldcade.romm.RommHttpException
import app.foldcade.romm.RommProtocolMismatch
import app.foldcade.romm.RommProtocolUnreadable
import app.foldcade.romm.RommUnsupportedServer
import app.foldcade.romm.SaveSyncReport
import app.foldcade.romm.copySaveToTrash
import app.foldcade.romm.md5File
import app.foldcade.romm.writeDurably
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal class RommLibrary(
    private val gate: RommGate,
) : LibraryBackend {
    override val id = ROMM_LIBRARY_ID
    override val displayName = "RomM"

    /** Runs [syncPending] after a connect. One at a time; [disconnect] stops it. */
    private val background = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> pendingRunning.set(false) },
    )
    private val pendingRunning = AtomicBoolean(false)
    private val romLocks = ConcurrentHashMap<Long, Mutex>()

    /**
     * Checks the server and the device, then starts a sync of every ROM whose
     * saves changed since they last agreed with RomM. That sync runs in the
     * background, so a slow server does not hold the library open.
     */
    override suspend fun connect() {
        gate.call { wiring, ops ->
            ops.heartbeat()
            rememberDevice(wiring, ops)
        }
        val wiring = gate.wiring()
        try {
            runInterruptible(Dispatchers.IO) { dropLegacyCache(wiring.cacheRoot, wiring.dataRoot, wiring.romRoot) }
        } catch (_: IOException) {
            // Only disk space is at stake. The next connect tries again.
            Unit
        }
        if (pendingRunning.compareAndSet(false, true)) {
            background.launch {
                try {
                    syncPending()
                } finally {
                    pendingRunning.set(false)
                }
            }
        }
    }

    override suspend fun disconnect() {
        background.coroutineContext.cancelChildren()
        gate.disconnect()
    }

    /** Syncs each ROM with an unsynced slot. A ROM that fails is left for the next connect. */
    internal suspend fun syncPending() {
        val wiring = gate.peek() ?: return
        val roms = runInterruptible(Dispatchers.IO) { wiring.data.romsWithSaves() }
        for (romId in roms) {
            try {
                val waiting = runInterruptible(Dispatchers.IO) { unsyncedSlots(wiring, romId).isNotEmpty() }
                if (waiting) syncRom(romId, emulator = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Unit
            }
        }
    }

    override suspend fun listPlatforms(): List<ListedPlatform> = gate.call { wiring, ops ->
        val platforms = ops.platforms()
        gate.catalog.rememberPlatforms(platforms)
        platforms.map { platform ->
            ListedPlatform(
                platformId = alignedPlatformId(wiring.platforms, platform.slug),
                displayName = platform.displayName,
                gameCount = platform.romCount.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            )
        }
    }

    override suspend fun listGames(platformId: String, query: GameQuery): GamePage = gate.call { wiring, ops ->
        val numeric = resolvePlatform(ops, platformId, wiring.platforms)
            ?: return@call GamePage(games = emptyList(), nextOffset = null, total = 0)
        val limit = query.limit.coerceIn(1, 10_000)
        val offset = query.offset.coerceAtLeast(0)
        val page = ops.roms(
            RomQuery(
                platformIds = listOf(numeric),
                searchTerm = query.text.trim().ifEmpty { null },
                limit = limit,
                offset = offset,
            ),
        )
        gate.catalog.rememberRoms(page.items)
        val games = runInterruptible(Dispatchers.IO) {
            page.items.map { it.toGame(wiring.romRoot, wiring.platforms) }
        }
        val consumed = page.offset + page.items.size
        val knownTotal = page.total
        val next = when {
            page.items.isEmpty() || page.items.size < limit -> null
            knownTotal != null && consumed.toLong() >= knownTotal -> null
            else -> consumed
        }
        GamePage(
            games = games,
            nextOffset = next,
            total = page.total?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt(),
        )
    }

    /**
     * A ROM already downloaded and verified is returned from disk, with no
     * network call and no token, so a cached game starts offline. Otherwise the
     * ROM is downloaded, checked against RomM's size and hash, and its file
     * list is kept beside it for the next offline start.
     */
    override suspend fun ensureLocal(game: Game): LaunchTarget {
        val wiring = gate.wiring()
        val romId = game.romId()
        val cached = runInterruptible(Dispatchers.IO) {
            val spec = gate.catalog.rom(game.remoteKey)?.bytes() ?: readRomSpec(wiring.romRoot, romId)
            spec?.let { verifiedRom(wiring.romRoot, romId, it) }
        }
        if (cached != null) return LaunchTarget.ContentUri(uri = wiring.contentUris.uriFor(cached))
        return gate.call { current, ops ->
            val rom = romFor(game, ops)
            val spec = rom.bytes()
            val path = ops.downloadRom(
                romId = rom.id,
                fileName = spec.fileName,
                cacheRoot = current.romRoot,
                fileIds = spec.fileIds,
                expectedSize = spec.expectedSize,
                expectedMd5 = spec.md5,
            )
            runInterruptible(Dispatchers.IO) { writeRomSpec(current.romRoot, rom.id, spec) }
            LaunchTarget.ContentUri(uri = current.contentUris.uriFor(path))
        }
    }

    override suspend fun saves(game: Game): SaveSet {
        val wiring = gate.wiring()
        val romId = game.romId()
        val slots = runInterruptible(Dispatchers.IO) {
            try {
                localSaves(wiring.data, romId, emulator = null).map { save ->
                    SaveSlot(
                        slot = save.slot ?: ARCHIVE_SLOT,
                        contentHash = md5File(save.file),
                        lastPlayerId = save.emulator,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                throw PluginException.Unavailable(error.message ?: "RomM save could not be read", error)
            }
        }
        return SaveSet(ownerBackendId = id, slots = slots)
    }

    /**
     * Syncs this game's saves, then returns them to place. A sync that cannot
     * finish does not stop the launch: the local saves are placed and stay
     * unsynced until RomM answers.
     */
    override suspend fun prepareLaunch(game: Game, player: Player): Placement {
        val target = ensureLocal(game)
        val wiring = gate.wiring()
        val romId = game.romId()
        // A slow server does not hold the game. A cut-short sync leaves files whole: each write is a rename.
        val sync = withTimeoutOrNull(LAUNCH_SYNC_LIMIT_MS) { syncRom(romId, player.id) }
            ?: pending(wiring, romId, "RomM took too long. Saves will sync later.")
        // A sync still running in the background may rename a save at any time, so
        // the shell gets copies whose hashes match their bytes.
        return Placement(target, snapshotSaves(wiring, romId), sync)
    }

    /**
     * Keeps what the player wrote, then starts a sync in the background and
     * returns. The observed bytes are written durably before any network call,
     * and the slot's previous copy goes to its trash. The caller does not wait
     * on the network, so a weak link does not hold up the next launch.
     */
    override suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves): SyncResult {
        val wiring = gate.wiring()
        val romId = game.romId()
        romLock(romId).withLock {
            try {
                runInterruptible(Dispatchers.IO) { writeObserved(wiring, romId, player.id, observed) }
            } catch (error: IOException) {
                throw PluginException.Unavailable(error.message ?: "The save could not be kept", error)
            }
        }
        background.launch { syncRom(romId, player.id) }
        return pending(wiring, romId, null)
    }

    /**
     * One negotiate for [romId]. Never throws except for cancellation: an
     * unreachable server, an HTTP error, an unreadable reply, a token problem,
     * or a local file error becomes a [SyncResult], so a sync never stops a
     * launch. [emulator] is the player in hand. Without one, each slot's last
     * player is used.
     */
    private suspend fun syncRom(romId: Long, emulator: String?): SyncResult = romLock(romId).withLock {
        val wiring = gate.peek() ?: return@withLock SyncResult(SyncOutcome.Unchanged, "RomM is not set up")
        try {
            val report = gate.session { current, ops ->
                rememberDevice(current, ops)
                try {
                    syncOnce(current, ops, romId, emulator)
                } catch (gone: RommHttpException) {
                    // RomM no longer knows this device. Register again once and retry.
                    if (gone.status != 404) throw gone
                    current.rememberedDevice = null
                    rememberDevice(current, ops)
                    syncOnce(current, ops, romId, emulator)
                }
            }
            report.toResult()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: RommUnsupportedServer) {
            SyncResult(SyncOutcome.Unchanged, error.message)
        } catch (error: RommProtocolMismatch) {
            SyncResult(SyncOutcome.Unchanged, error.message)
        } catch (error: RommProtocolUnreadable) {
            SyncResult(SyncOutcome.Unchanged, error.message)
        } catch (error: PluginException.NotAuthenticated) {
            pending(wiring, romId, error.message ?: "Sign in to RomM to sync saves")
        } catch (error: Exception) {
            pending(wiring, romId, error.message)
        }
    }

    private suspend fun syncOnce(wiring: RommWiring, ops: RommOps, romId: Long, emulator: String?): SaveSyncReport {
        val deviceId = wiring.rememberedDevice?.deviceId
            ?: throw PluginException.NotAuthenticated("RomM device is not registered")
        val saves = runInterruptible(Dispatchers.IO) { localSaves(wiring.data, romId, emulator) }
        val players = (listOfNotNull(emulator) + saves.mapNotNull { it.emulator }).distinct()
        return ops.syncSaves(
            deviceId = deviceId,
            saves = saves,
            romIds = listOf(romId),
            emulators = players.ifEmpty { null },
            destination = { op -> wiring.data.slotFile(romId, op.slot ?: ARCHIVE_SLOT) },
            ledger = wiring.data.ledger(romId),
        )
    }

    /** Queued when a save is waiting for RomM, else unchanged. */
    private suspend fun pending(wiring: RommWiring, romId: Long, note: String?): SyncResult {
        val waiting = try {
            runInterruptible(Dispatchers.IO) { unsyncedSlots(wiring, romId).isNotEmpty() }
        } catch (_: IOException) {
            true
        }
        return if (waiting) {
            SyncResult(SyncOutcome.Queued, note ?: "Saves will sync when RomM is reachable")
        } else {
            SyncResult(SyncOutcome.Unchanged, note)
        }
    }

    private fun romLock(romId: Long): Mutex = romLocks.computeIfAbsent(romId) { Mutex() }

    private suspend fun rememberDevice(wiring: RommWiring, ops: RommOps) {
        val hostname = runInterruptible(Dispatchers.IO) { wiring.data.hostName() }
        val registered = ops.registerDevice(
            stored = wiring.rememberedDevice,
            name = wiring.deviceName,
            clientVersion = wiring.clientVersion,
            hostname = hostname,
        )
        runInterruptible(Dispatchers.IO) { wiring.rememberedDevice = registered }
    }

    private suspend fun resolvePlatform(
        ops: RommOps,
        platformId: String,
        definitions: List<Platform>,
    ): Long? {
        matchPlatform(platformId, definitions)?.let { return it.id }
        val listed = ops.platforms()
        gate.catalog.rememberPlatforms(listed)
        return matchPlatform(platformId, definitions)?.id
    }

    private fun matchPlatform(platformId: String, definitions: List<Platform>): app.foldcade.romm.PlatformSummary? =
        gate.catalog.platforms().firstOrNull { platform ->
            platform.slug.equals(platformId, ignoreCase = true) ||
                platform.id.toString() == platformId ||
                alignedPlatformId(definitions, platform.slug).equals(platformId, ignoreCase = true)
        }

    private suspend fun romFor(game: Game, ops: RommOps): RomSummary {
        if (game.backendId != ROMM_LIBRARY_ID) {
            throw PluginException.NotFound("This game is not from RomM")
        }
        gate.catalog.rom(game.remoteKey)?.let { return it }
        val romId = game.romId()
        val page = ops.roms(
            RomQuery(
                searchTerm = game.label.trim().ifEmpty { null },
                limit = 50,
                offset = 0,
            ),
        )
        gate.catalog.rememberRoms(page.items)
        return page.items.find { it.id == romId }
            ?: throw PluginException.NotFound("RomM has no ROM ${game.remoteKey}")
    }
}

internal fun Game.romId(): Long =
    remoteKey.toLongOrNull()?.takeIf { it >= 1 }
        ?: throw PluginException.NotFound("RomM game key is not a ROM id")

internal fun RomSummary.bytes(): RomBytes {
    val multiple = hasMultipleFiles || hasNestedSingleFile
    val fileIds = if (multiple) files.map { it.id } else emptyList()
    val fileName = when {
        hasSimpleSingleFile -> files.singleOrNull()?.fileName ?: fsName
        else -> files.firstOrNull()?.fileName ?: fsName
    }
    val single = if (fileIds.isEmpty()) files.singleOrNull() else null
    val expectedSize = if (fileIds.isEmpty()) single?.fileSizeBytes ?: fsSizeBytes else null
    // RomM hashes an archive by its members, so that hash cannot check the downloaded bytes.
    val md5 = single?.md5Hash?.takeUnless { RommClient.isArchiveName(fileName) }
    return RomBytes(fileName, fileIds, expectedSize, md5)
}

/** What [RommClient.downloadRom] needs for one ROM. [md5] is null when RomM has no hash for it. */
internal data class RomBytes(
    val fileName: String,
    val fileIds: List<Long>,
    val expectedSize: Long?,
    val md5: String? = null,
)

/** The downloaded file for [spec] when its verified marker still matches, else null. */
internal fun verifiedRom(cacheRoot: Path, romId: Long, spec: RomBytes): Path? {
    val target = RommClient.romCacheFile(cacheRoot, romId, spec.fileName, spec.fileIds)
    return target.takeIf { RommClient.isVerifiedRom(it, spec.expectedSize, spec.md5) }
}

/** Kept beside the ROM, so a launch after a restart with no network can find it. */
internal fun writeRomSpec(cacheRoot: Path, romId: Long, spec: RomBytes) {
    val json = buildJsonObject {
        put("file_name", spec.fileName)
        put("file_ids", JsonArray(spec.fileIds.map { JsonPrimitive(it) }))
        spec.expectedSize?.let { put("expected_size", it) }
        spec.md5?.let { put("md5", it) }
    }
    writeDurably(romSpecFile(cacheRoot, romId), json.toString().toByteArray(Charsets.UTF_8))
}

internal fun readRomSpec(cacheRoot: Path, romId: Long): RomBytes? = try {
    val obj = Json.parseToJsonElement(String(Files.readAllBytes(romSpecFile(cacheRoot, romId)), Charsets.UTF_8))
        as? JsonObject
    obj?.let {
        RomBytes(
            fileName = it["file_name"]?.jsonPrimitive?.contentOrNull ?: return null,
            fileIds = it["file_ids"]?.jsonArray?.mapNotNull { id -> id.jsonPrimitive.longOrNull } ?: emptyList(),
            expectedSize = it["expected_size"]?.jsonPrimitive?.longOrNull,
            md5 = it["md5"]?.jsonPrimitive?.contentOrNull,
        )
    }
} catch (_: Exception) {
    null
}

private fun romSpecFile(cacheRoot: Path, romId: Long): Path =
    cacheRoot.resolve("roms").resolve(romId.toString()).resolve(ROM_SPEC_FILE)

internal fun alignedPlatformId(definitions: Iterable<Platform>, slug: String): String =
    canonicalPlatformId(definitions, slug) ?: slug

internal fun RomSummary.toGame(cacheRoot: Path, definitions: Iterable<Platform>): Game = Game(
    backendId = ROMM_LIBRARY_ID,
    remoteKey = id.toString(),
    platformId = alignedPlatformId(definitions, platformSlug),
    availability = cacheAvailability(cacheRoot, this),
    label = name?.takeIf { it.isNotBlank() } ?: fsName,
)

internal fun cacheAvailability(cacheRoot: Path, rom: RomSummary): Availability {
    val spec = rom.bytes()
    val target = RommClient.romCacheFile(cacheRoot, rom.id, spec.fileName, spec.fileIds)
    val partial = target.resolveSibling(target.fileName.toString() + ".partial")
    return if (Files.isRegularFile(target) && !Files.exists(partial)) {
        Availability.Cached
    } else {
        Availability.RemoteOnly
    }
}

internal fun SaveSyncReport.toResult(): SyncResult {
    val outcome = when {
        keptBoth.isNotEmpty() -> SyncOutcome.KeptBoth
        queued.isNotEmpty() -> SyncOutcome.Queued
        uploadedSaveIds.isNotEmpty() -> SyncOutcome.Uploaded
        downloaded.isNotEmpty() -> SyncOutcome.Downloaded
        else -> SyncOutcome.Unchanged
    }
    val note = when (outcome) {
        SyncOutcome.KeptBoth -> "Kept both copies"
        SyncOutcome.Queued -> "Saves will sync when RomM is reachable"
        else -> failed.firstOrNull()
    }
    return SyncResult(outcome, note)
}

/**
 * The saves held for [romId], one per slot. A slot's player is the one that
 * last wrote it, else [emulator].
 */
internal fun localSaves(data: RommData, romId: Long, emulator: String?): List<LocalSave> {
    val ledger = data.ledger(romId).slots()
    return data.slots(romId).mapNotNull { slot ->
        if (Thread.currentThread().isInterrupted) throw CancellationException("RomM save scan was cancelled")
        val file = data.slotFile(romId, slot)
        if (!Files.isRegularFile(file)) return@mapNotNull null
        LocalSave(
            romId = romId,
            fileName = file.fileName.toString(),
            slot = slot,
            emulator = ledger[slot]?.emulator ?: emulator,
            updatedAt = Files.getLastModifiedTime(file).toInstant(),
            file = file,
        )
    }
}

/** Slots of [romId] whose bytes differ from what they last agreed with RomM. */
internal fun unsyncedSlots(wiring: RommWiring, romId: Long): List<String> {
    val ledger = wiring.data.ledger(romId)
    return localSaves(wiring.data, romId, emulator = null)
        .filter { save -> save.slot?.let { ledger.unsynced(it, md5File(save.file)) } ?: false }
        .mapNotNull { it.slot }
}

/**
 * Copies of [romId]'s saves under the placing directory, each with the hash of
 * the copy. A save renamed after this does not change what the shell reads.
 */
internal suspend fun snapshotSaves(wiring: RommWiring, romId: Long): List<PlacedSave> =
    runInterruptible(Dispatchers.IO) {
        val dir = wiring.data.placingRoot(romId)
        if (Files.exists(dir)) dir.toFile().deleteRecursively()
        localSaves(wiring.data, romId, emulator = null).mapIndexed { index, save ->
            val copy = dir.resolve(index.toString()).resolve(save.fileName)
            Files.createDirectories(copy.parent)
            Files.copy(save.file, copy)
            PlacedSave(
                slot = save.slot ?: ARCHIVE_SLOT,
                contentUri = wiring.contentUris.uriFor(copy),
                contentHash = md5File(copy),
            )
        }
    }

/**
 * Writes each observed slot into its one file, durably. A slot that cannot be
 * read throws, so the shell does not count it as kept. Bytes equal to what is
 * there already change nothing. Different bytes copy the old file to the slot's
 * trash first. The ledger's synced hash is left alone, so the new bytes count
 * as unsynced until RomM takes them.
 */
internal fun writeObserved(wiring: RommWiring, romId: Long, playerId: String, observed: ObservedSaves) {
    val ledger = wiring.data.ledger(romId)
    for (slot in observed.slots) {
        if (Thread.currentThread().isInterrupted) {
            throw CancellationException("RomM save write was cancelled")
        }
        val uri = slot.contentUri ?: continue
        val existing = wiring.contentUris.pathFor(uri)
        // A save that cannot be read must fail the call, or the shell would count it as kept.
        val bytes = when {
            existing != null && Files.isRegularFile(existing) -> Files.readAllBytes(existing)
            else -> wiring.saveBytes.read(uri)
        } ?: throw PluginException.Unavailable("The ${slot.slot} save could not be read")
        val dest = wiring.data.slotFile(romId, slot.slot)
        if (Files.isRegularFile(dest) && md5File(dest) == md5Hex(bytes)) {
            ledger.update(slot.slot) { it.copy(emulator = slot.playerId.ifBlank { playerId }) }
            continue
        }
        copySaveToTrash(dest)
        writeDurably(dest, bytes)
        ledger.update(slot.slot) { it.copy(emulator = slot.playerId.ifBlank { playerId }) }
    }
}

/**
 * Cleans up what older versions kept in the evictable cache. Copies of server
 * saves and an upload queue whose entries could replay an old save over a newer
 * one are removed. Neither holds bytes RomM lacks: saves were never placed for a
 * player before, so every copy there came from RomM. Downloaded ROMs move under
 * [romRoot], the server configured now. A ROM there without a verified marker is
 * checked against RomM's hash before it is used.
 */
internal fun dropLegacyCache(cacheRoot: Path, dataRoot: Path, romRoot: Path) {
    // Never the saves this version keeps, even if both roots were set to one directory.
    if (cacheRoot.toAbsolutePath().normalize() == dataRoot.toAbsolutePath().normalize()) return
    for (name in listOf("saves", "upload-queue")) {
        val dir = cacheRoot.resolve(name)
        if (Files.exists(dir)) dir.toFile().deleteRecursively()
    }
    val oldRoms = cacheRoot.resolve("roms")
    val newRoms = romRoot.resolve("roms")
    if (!Files.isDirectory(oldRoms)) return
    Files.createDirectories(newRoms)
    val entries = Files.list(oldRoms).use { stream -> stream.collect(java.util.stream.Collectors.toList()) }
    for (entry in entries) {
        val dest = newRoms.resolve(entry.fileName.toString())
        // A ROM already downloaded under this server wins. The old copy is cache.
        if (Files.exists(dest)) entry.toFile().deleteRecursively() else Files.move(entry, dest)
    }
    Files.deleteIfExists(oldRoms)
}

private fun md5Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

/** How long a launch waits for its save sync. */
private const val LAUNCH_SYNC_LIMIT_MS = 20_000L

/** The slot name RomM uses for a save with no slot. */
internal const val ARCHIVE_SLOT = "archive"

private const val ROM_SPEC_FILE = "rom.json"
