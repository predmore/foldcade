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
import app.foldcade.romm.RommProtocolMismatch
import app.foldcade.romm.RommProtocolUnreadable
import app.foldcade.romm.RommUnavailable
import app.foldcade.romm.RommUnsupportedServer
import app.foldcade.romm.SaveSyncReport
import app.foldcade.romm.SaveUploadQueue
import app.foldcade.romm.SyncOperation
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

internal class RommLibrary(
    private val gate: RommGate,
) : LibraryBackend {
    override val id = ROMM_LIBRARY_ID
    override val displayName = "RomM"

    override suspend fun connect() {
        gate.call { wiring, ops ->
            ops.heartbeat()
            rememberDevice(wiring, ops)
            ops.flushUploads(SaveUploadQueue(wiring.cacheRoot.resolve("upload-queue")))
        }
    }

    override suspend fun disconnect() {
        gate.disconnect()
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
            page.items.map { it.toGame(wiring.cacheRoot, wiring.platforms) }
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

    override suspend fun ensureLocal(game: Game): LaunchTarget = gate.call { wiring, ops ->
        val rom = romFor(game, ops)
        val spec = rom.bytes()
        val path = ops.downloadRom(
            romId = rom.id,
            fileName = spec.fileName,
            cacheRoot = wiring.cacheRoot,
            fileIds = spec.fileIds,
            expectedSize = spec.expectedSize,
        )
        LaunchTarget.ContentUri(uri = wiring.contentUris.uriFor(path))
    }

    override suspend fun saves(game: Game): SaveSet {
        val wiring = gate.wiring()
        val romId = game.romId()
        val slots = runInterruptible(Dispatchers.IO) {
            try {
                localSaves(wiring.cacheRoot, romId, emulator = null).map { save ->
                    SaveSlot(
                        slot = save.slot ?: "archive",
                        contentHash = md5File(save.file),
                        lastPlayerId = null,
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

    override suspend fun prepareLaunch(game: Game, player: Player): Placement {
        val target = ensureLocal(game)
        val wiring = gate.wiring()
        val romId = game.romId()
        return try {
            val report = gate.session { current, ops ->
                rememberDevice(current, ops)
                val saves = runInterruptible(Dispatchers.IO) {
                    localSaves(current.cacheRoot, romId, player.id)
                }
                ops.syncSaves(
                    deviceId = current.rememberedDevice?.deviceId
                        ?: throw PluginException.NotAuthenticated("RomM device is not registered"),
                    saves = saves,
                    romIds = listOf(romId),
                    emulators = listOf(player.id),
                    destination = { op -> saveDestination(current.cacheRoot, op) },
                    queue = SaveUploadQueue(current.cacheRoot.resolve("upload-queue")),
                )
            }
            Placement(target, placedSaves(wiring, romId), report.toResult())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: RommUnsupportedServer) {
            skipped(target, wiring, romId, error.message)
        } catch (error: RommProtocolMismatch) {
            skipped(target, wiring, romId, error.message)
        } catch (error: RommProtocolUnreadable) {
            skipped(target, wiring, romId, error.message)
        } catch (error: RommUnavailable) {
            runInterruptible(Dispatchers.IO) { queueLocal(wiring, romId, player.id) }
            Placement(target, placedSaves(wiring, romId), SyncResult(SyncOutcome.Queued, error.message))
        } catch (plugin: PluginException) {
            throw plugin
        } catch (error: RommException) {
            throw error.toPluginException()
        }
    }

    override suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves): SyncResult {
        val wiring = gate.wiring()
        val romId = game.romId()
        runInterruptible(Dispatchers.IO) {
            writeObserved(wiring, romId, observed)
        }
        return try {
            val report = gate.session { current, ops ->
                rememberDevice(current, ops)
                val saves = runInterruptible(Dispatchers.IO) {
                    localSaves(current.cacheRoot, romId, player.id)
                }
                ops.syncSaves(
                    deviceId = current.rememberedDevice?.deviceId
                        ?: throw PluginException.NotAuthenticated("RomM device is not registered"),
                    saves = saves,
                    romIds = listOf(romId),
                    emulators = listOf(player.id),
                    destination = { op -> saveDestination(current.cacheRoot, op) },
                    queue = SaveUploadQueue(current.cacheRoot.resolve("upload-queue")),
                )
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
        } catch (error: RommUnavailable) {
            runInterruptible(Dispatchers.IO) { queueLocal(wiring, romId, player.id) }
            SyncResult(SyncOutcome.Queued, error.message)
        } catch (plugin: PluginException) {
            throw plugin
        } catch (error: RommException) {
            throw error.toPluginException()
        }
    }

    private suspend fun rememberDevice(wiring: RommWiring, ops: RommOps) {
        wiring.rememberedDevice = ops.registerDevice(
            stored = wiring.rememberedDevice,
            name = wiring.deviceName,
            clientVersion = wiring.clientVersion,
        )
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

    private suspend fun skipped(target: LaunchTarget, wiring: RommWiring, romId: Long, message: String?) =
        Placement(target, placedSaves(wiring, romId), SyncResult(SyncOutcome.Unchanged, message))
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
    val expectedSize = if (fileIds.isEmpty()) files.singleOrNull()?.fileSizeBytes ?: fsSizeBytes else null
    return RomBytes(fileName, fileIds, expectedSize)
}

internal data class RomBytes(
    val fileName: String,
    val fileIds: List<Long>,
    val expectedSize: Long?,
)

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
        SyncOutcome.Queued -> "Upload queued until RomM is reachable"
        else -> failed.firstOrNull()
    }
    return SyncResult(outcome, note)
}

internal fun localSaves(cacheRoot: Path, romId: Long, emulator: String?): List<LocalSave> {
    val root = cacheRoot.resolve("saves").resolve(romId.toString())
    if (!Files.isDirectory(root)) return emptyList()
    val found = mutableListOf<LocalSave>()
    Files.list(root).use { slots ->
        slots.filter { Files.isDirectory(it) }.forEach { slotDir ->
            if (Thread.currentThread().isInterrupted) {
                throw CancellationException("RomM save scan was cancelled")
            }
            val slot = slotDir.fileName.toString()
            Files.list(slotDir).use { files ->
                // `.trash` is a directory. Only regular files in the slot are saves.
                files.filter { Files.isRegularFile(it) }.forEach { file ->
                    found += LocalSave(
                        romId = romId,
                        fileName = file.fileName.toString(),
                        slot = slot,
                        emulator = emulator,
                        updatedAt = Files.getLastModifiedTime(file).toInstant(),
                        file = file,
                    )
                }
            }
        }
    }
    return found
}

internal fun saveDestination(cacheRoot: Path, op: SyncOperation): Path {
    val slot = op.slot ?: "archive"
    val name = safeSaveName(op.fileName.ifBlank { "$slot.sav" })
    return cacheRoot.resolve("saves").resolve(op.romId.toString()).resolve(slot).resolve(name)
}

internal suspend fun placedSaves(wiring: RommWiring, romId: Long): List<PlacedSave> =
    runInterruptible(Dispatchers.IO) {
        localSaves(wiring.cacheRoot, romId, emulator = null).map { save ->
            PlacedSave(
                slot = save.slot ?: "archive",
                contentUri = wiring.contentUris.uriFor(save.file),
                contentHash = md5File(save.file),
            )
        }
    }

internal fun writeObserved(wiring: RommWiring, romId: Long, observed: ObservedSaves) {
    for (slot in observed.slots) {
        if (Thread.currentThread().isInterrupted) {
            throw CancellationException("RomM save write was cancelled")
        }
        val uri = slot.contentUri ?: continue
        val existing = wiring.contentUris.pathFor(uri)
        val bytes = when {
            existing != null && Files.isRegularFile(existing) -> Files.readAllBytes(existing)
            else -> wiring.saveBytes.read(uri)
        } ?: continue
        val slotName = safeSaveName(slot.slot)
        val dest = wiring.cacheRoot.resolve("saves").resolve(romId.toString()).resolve(slotName)
            .resolve(safeSaveName("$slotName.sav"))
        Files.createDirectories(dest.parent)
        Files.write(dest, bytes)
    }
}

internal fun queueLocal(wiring: RommWiring, romId: Long, emulator: String?) {
    val deviceId = wiring.rememberedDevice?.deviceId ?: return
    val queue = SaveUploadQueue(wiring.cacheRoot.resolve("upload-queue"))
    for (save in localSaves(wiring.cacheRoot, romId, emulator)) {
        if (Thread.currentThread().isInterrupted) {
            throw CancellationException("RomM save queue was cancelled")
        }
        val bytes = Files.readAllBytes(save.file)
        queue.enqueue(save, deviceId, md5Hex(bytes), bytes)
    }
}

internal fun safeSaveName(name: String): String {
    val base = name.substringAfterLast('/').substringAfterLast('\\').trim()
    if (base.isEmpty() || base == "." || base == ".." || base.contains('\u0000')) return "save.sav"
    return base
}

internal fun md5Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("MD5").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
}

internal fun md5File(path: Path): String {
    val digest = MessageDigest.getInstance("MD5")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(8 * 1024)
        while (true) {
            if (Thread.currentThread().isInterrupted) {
                throw CancellationException("RomM save hash was cancelled")
            }
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
