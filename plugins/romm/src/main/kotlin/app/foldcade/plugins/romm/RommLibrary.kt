package app.foldcade.plugins.romm

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.DeviceCodeChallenge
import app.foldcade.api.plugin.DeviceCodePoll
import app.foldcade.api.plugin.FirmwareBytes
import app.foldcade.api.plugin.FirmwareOffer
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameActivity
import app.foldcade.api.plugin.GameActivityListener
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
import app.foldcade.api.plugin.SaveConflict
import app.foldcade.api.plugin.SaveConflictChoice
import app.foldcade.api.plugin.SaveSet
import app.foldcade.api.plugin.SaveSlot
import app.foldcade.api.plugin.Setup
import app.foldcade.api.plugin.SyncOutcome
import app.foldcade.api.plugin.SyncResult
import app.foldcade.romm.KeptBoth
import app.foldcade.romm.LocalSave
import app.foldcade.romm.RomFileSummary
import app.foldcade.romm.RomProgress
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
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

internal class RommLibrary internal constructor(
    private val gate: RommGate,
    override val instanceId: String,
    private val supply: () -> RommSupply?,
    private val slot: AtomicReference<RommWiring?>?,
) : LibraryBackend {
    /** Tests pass their own gate. Production uses [open]. */
    constructor(gate: RommGate) : this(gate, ROMM_LIBRARY_ID, { null }, null)

    private val listedBackups = java.util.concurrent.ConcurrentHashMap<java.nio.file.Path, Set<String>>()

    override val id = ROMM_LIBRARY_ID
    override val displayName = "RomM"

    internal val connection: RommGate get() = gate

    override fun setup(): List<Setup> = listOf(
        Setup.Text(key = ROMM_ORIGIN_KEY, prompt = "Server"),
        Setup.DeviceCode(credentialKey = rommCredentialKey(instanceId)),
    )

    override fun configure(values: Map<String, String>) {
        val held = slot ?: return
        val origin = values[ROMM_ORIGIN_KEY] ?: return
        val trimmed = origin.trim()
        if (trimmed.isEmpty()) {
            held.set(null)
            return
        }
        val shared = supply() ?: return
        held.set(shared.wiring(trimmed, rommCredentialKey(instanceId)))
    }

    override suspend fun beginDeviceCode(): DeviceCodeChallenge {
        val wiring = gate.wiring()
        val challenge = RommPairing(wiring).begin(wiring.data.hostName())
        return DeviceCodeChallenge(
            deviceCode = challenge.deviceCode,
            userCode = challenge.userCode,
            verificationUrl = challenge.verificationUrl,
            intervalSeconds = challenge.intervalSeconds,
            expiresInSeconds = challenge.expiresInSeconds,
        )
    }

    override suspend fun pollDeviceCode(deviceCode: String): DeviceCodePoll =
        when (val result = RommPairing(gate.wiring()).poll(deviceCode)) {
            RommPairingPoll.Pending -> DeviceCodePoll.Pending
            RommPairingPoll.SlowDown -> DeviceCodePoll.SlowDown
            RommPairingPoll.Denied -> DeviceCodePoll.Denied
            RommPairingPoll.Expired -> DeviceCodePoll.Expired
            is RommPairingPoll.Approved -> DeviceCodePoll.Approved(result.accessToken)
        }

    companion object {
        fun open(instanceId: String, supply: () -> RommSupply? = { RommPlugins.supply }): RommLibrary {
            val slot = AtomicReference<RommWiring?>(null)
            return RommLibrary(
                gate = RommGate {
                    slot.get() ?: if (instanceId == ROMM_LIBRARY_ID) RommPlugins.wiring else null
                },
                instanceId = instanceId,
                supply = supply,
                slot = slot,
            )
        }
    }

    /** Runs [syncPending] after a connect. One at a time; [disconnect] stops it. */
    private val background = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> pendingRunning.set(false) },
    )
    private val pendingRunning = AtomicBoolean(false)
    private val romLocks = ConcurrentHashMap<Long, Mutex>()
    /** One ROM's file at a time: a download, a launch's check of it, or a delete. */
    private val fileLocks = ConcurrentHashMap<Long, Mutex>()
    @Volatile private var offlineCatalog = false

    @Volatile
    private var listener: GameActivityListener? = null
    private val conflictListeners = CopyOnWriteArrayList<() -> Unit>()

    override fun observeActivity(listener: GameActivityListener) {
        this.listener = listener
    }

    /** Tells the host what is happening to [romId]. A listener that throws is not this backend's failure. */
    private fun report(romId: Long, activity: GameActivity) {
        try {
            listener?.onActivity(romId.toString(), activity)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Unit
        }
    }

    /** Download progress for [romId]. See [downloadReporter]. */
    private fun downloadReporter(romId: Long): (RomProgress) -> Unit =
        downloadReporter { report(romId, it) }

    /**
     * Download progress, at most one report per whole percent and
     * [PROGRESS_GAP_NS], so a fast link does not flood the host. The last byte is always told.
     */
    private fun downloadReporter(emit: (GameActivity) -> Unit): (RomProgress) -> Unit {
        var shownAt = 0L
        var shown = -1L
        return { step ->
            when (step) {
                is RomProgress.Receiving -> {
                    val total = step.total
                    val percent = if (total != null && total > 0) step.bytes * 100 / total else -1L
                    val now = System.nanoTime()
                    val last = total != null && step.bytes >= total
                    if (last || (percent != shown && now - shownAt >= PROGRESS_GAP_NS)) {
                        shownAt = now
                        shown = percent
                        emit(GameActivity.Downloading(step.bytes, total))
                    }
                }
                RomProgress.Checking -> emit(GameActivity.Checking)
            }
        }
    }

    /**
     * One sync of [romId] the host can watch: syncing first, then its result. A
     * player whose saves Foldcade can't reach ([unreachable]) handed nothing
     * over, so the tile says the saves didn't sync, whatever the server did.
     */
    private suspend fun syncRomReported(romId: Long, emulator: String?, unreachable: Boolean = false): SyncResult {
        report(romId, GameActivity.SyncingSaves)
        var result: SyncResult? = null
        try {
            return syncRom(romId, emulator).also { result = it }
        } finally {
            report(romId, result?.let { GameActivity.Synced(if (unreachable) unreachableSync() else it) } ?: GameActivity.Idle)
        }
    }

    /**
     * Checks the server and the device, then starts a sync of every ROM whose
     * saves changed since they last agreed with RomM. That sync runs in the
     * background, so a slow server does not hold the library open.
     */
    override suspend fun connect() {
        val catalog = gate.restoreCatalog()
        offlineCatalog = false
        try {
            gate.call { wiring, ops ->
                ops.heartbeat()
                rememberDevice(wiring, ops)
            }
        } catch (unavailable: PluginException.Unavailable) {
            if (!catalog.hasPlatforms()) throw unavailable
            offlineCatalog = true
            return
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
                if (waiting || SaveSelection(wiring.data.romSaves(romId)).pending()) syncRomReported(romId, emulator = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Unit
            }
        }
    }

    override suspend fun listPlatforms(): List<ListedPlatform> {
        val wiring = gate.wiring()
        val catalog = gate.restoreCatalog(wiring)
        val platforms = if (offlineCatalog) catalog.platforms() else try {
            gate.call { current, ops ->
                ops.platforms().also { gate.catalog(current).rememberPlatforms(it) }
            }
        } catch (unavailable: PluginException.Unavailable) {
            if (!catalog.hasPlatforms()) throw unavailable
            offlineCatalog = true
            catalog.platforms()
        }
        return platforms.map { platform ->
            ListedPlatform(
                platformId = alignedPlatformId(wiring.platforms, platform.slug),
                displayName = platform.displayName,
                gameCount = platform.romCount.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            )
        }
    }

    override suspend fun listGames(platformId: String, query: GameQuery): GamePage {
        val wiring = gate.wiring()
        val catalog = gate.restoreCatalog(wiring)
        if (offlineCatalog) return cachedGames(wiring, catalog, platformId, query)
        return try {
            listOnlineGames(platformId, query)
        } catch (unavailable: PluginException.Unavailable) {
            cachedGames(wiring, catalog, platformId, query, unavailable)
        }
    }

    private fun cachedGames(
        wiring: RommWiring,
        catalog: RommCatalog,
        platformId: String,
        query: GameQuery,
        failure: PluginException.Unavailable = PluginException.Unavailable("RomM is offline; this catalog has not been saved yet"),
    ): GamePage {
        val numeric = matchPlatform(platformId, wiring.platforms, catalog)?.id ?: throw failure
        val listed = catalog.listing(numeric) ?: throw failure
        val text = query.text.trim()
        val matches = if (text.isEmpty()) listed else listed.filter { rom ->
            (listOfNotNull(rom.name, rom.fsName) + rom.alternativeNames).any { it.contains(text, ignoreCase = true) }
        }
        val offset = query.offset.coerceAtLeast(0).coerceAtMost(matches.size)
        val end = (offset.toLong() + query.limit.coerceIn(1, 10_000)).coerceAtMost(matches.size.toLong()).toInt()
        return GamePage(
            games = matches.subList(offset, end).map { it.toGame(wiring.romRoot, wiring.platforms, instanceId) },
            nextOffset = end.takeIf { it < matches.size },
            total = matches.size,
        )
    }

    private suspend fun listOnlineGames(platformId: String, query: GameQuery): GamePage = gate.call { wiring, ops ->
        val catalog = gate.catalog(wiring)
        val numeric = resolvePlatform(ops, platformId, wiring.platforms, catalog)
            ?: return@call GamePage(games = emptyList(), nextOffset = null, total = 0)
        val limit = query.limit.coerceIn(1, 10_000)
        val offset = query.offset.coerceAtLeast(0)
        val romQuery = RomQuery(
            platformIds = listOf(numeric),
            searchTerm = query.text.trim().ifEmpty { null },
            limit = limit,
            offset = offset,
        )
        val page = ops.roms(romQuery)
        val games = runInterruptible(Dispatchers.IO) {
            page.items.map { it.toGame(wiring.romRoot, wiring.platforms, instanceId) }
        }
        val consumed = page.offset + page.items.size
        val knownTotal = page.total
        val next = when {
            page.items.isEmpty() || page.items.size < limit -> null
            knownTotal != null && consumed.toLong() >= knownTotal -> null
            else -> consumed
        }
        catalog.rememberPage(numeric, romQuery, page, finished = next == null)
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
    override suspend fun ensureLocal(game: Game): LaunchTarget = fileLock(game.romId()).withLock {
        val wiring = gate.wiring()
        val romId = game.romId()
        val catalog = gate.restoreCatalog(wiring)
        val cached = runInterruptible(Dispatchers.IO) {
            val spec = catalog.rom(game.remoteKey)?.bytes() ?: readRomSpec(wiring.romRoot, romId)
            spec?.let { adoptCachedRom(wiring.romRoot, romId, it) }
        }
        if (cached != null) {
            val boot = runInterruptible(Dispatchers.IO) { bootFile(cached.first, cached.second, game.platformId) }
            return@withLock launchTarget(wiring, boot)
        }
        report(romId, GameActivity.Downloading(0L, null))
        var done = false
        try {
            gate.call { current, ops ->
                val rom = romFor(game, ops, gate.catalog(current))
                val spec = rom.bytes()
                val reporter = downloadReporter(rom.id)
                // One bar for the game and its add-ons. A total is shown only when every size is known.
                val sizes = listOf(spec.expectedSize) + spec.addOns.map { it.size }
                val total = if (sizes.all { it != null }) sizes.sumOf { it!! } else null
                var before = 0L
                val progress: (RomProgress) -> Unit = { step ->
                    reporter(if (step is RomProgress.Receiving) RomProgress.Receiving(before + step.bytes, total) else step)
                }
                val path = ops.downloadRom(
                    romId = rom.id,
                    fileName = spec.fileName,
                    cacheRoot = current.romRoot,
                    fileIds = spec.fileIds,
                    expectedSize = spec.expectedSize,
                    expectedMd5 = spec.md5,
                    progress = progress,
                )
                before += spec.expectedSize ?: 0L
                for (addOn in spec.addOns) {
                    ops.downloadRom(
                        romId = rom.id,
                        fileName = addOn.fileName,
                        cacheRoot = current.romRoot,
                        fileIds = listOf(addOn.id),
                        expectedSize = addOn.size,
                        expectedMd5 = addOn.md5,
                        progress = progress,
                        target = RommClient.besideRom(path, addOn.fileName),
                    )
                    before += addOn.size
                }
                val boot = runInterruptible(Dispatchers.IO) {
                    if (spec.addOns.isNotEmpty()) pruneRomFolder(path, spec)
                    writeRomSpec(current.romRoot, rom.id, spec)
                    bootFile(path, spec, game.platformId)
                }
                launchTarget(current, boot)
            }.also { done = true }
        } finally {
            // A finished download is followed by the launch's save sync, which reports next.
            if (!done) report(romId, GameActivity.Idle)
        }
    }

    /**
     * The game file a player opens, as a file in its folder: Eden finds a game's
     * update and DLC beside it, a player handed a `.cue` finds its tracks, and
     * DuckStation can name only a document in a tree. The folder holds this game's
     * download alone.
     */
    private fun launchTarget(wiring: RommWiring, rom: Path): LaunchTarget.ContentUri =
        LaunchTarget.ContentUri(uri = wiring.contentUris.folderUriFor(rom))

    /**
     * Deletes [game]'s downloaded ROM and the file list kept beside it, with no
     * network call. Saves are under the data root, so they stay and sync as before.
     */
    override suspend fun removeLocal(game: Game): Unit = fileLock(game.romId()).withLock {
        val wiring = gate.wiring()
        val romId = game.romId()
        try {
            runInterruptible(Dispatchers.IO) { dropRom(wiring.romRoot, romId) }
        } catch (error: IOException) {
            throw PluginException.Unavailable(error.message ?: "The download could not be deleted", error)
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
        return SaveSet(ownerBackendId = instanceId, slots = slots)
    }

    /**
     * Syncs this game's saves, then returns them to place. A sync that cannot
     * finish does not stop the launch: the local saves are placed and stay
     * unsynced until RomM answers.
     */
    override suspend fun prepareLaunch(game: Game, player: Player): Placement =
        negotiate(game, player, ensureLocal(game))

    /**
     * The same sync as [prepareLaunch], for a launch that opens another library's copy
     * of this ROM. Saves are kept apart from the download, so nothing is downloaded.
     */
    override suspend fun prepareSaves(game: Game, player: Player, target: LaunchTarget): Placement =
        negotiate(game, player, target)

    /**
     * The file a download of [game] is saved as, from the catalog or, offline, from
     * the file list kept beside a download. Null for a ROM of several files.
     */
    override suspend fun fileName(game: Game): String? {
        val wiring = gate.peek() ?: return null
        val romId = game.remoteKey.toLongOrNull() ?: return null
        val catalog = gate.restoreCatalog(wiring)
        val spec = runInterruptible(Dispatchers.IO) {
            catalog.rom(game.remoteKey)?.bytes() ?: readRomSpec(wiring.romRoot, romId)
        }?.takeIf { it.fileIds.isEmpty() } ?: return null
        // The name the download is stored under, which is the file the player opens.
        return try {
            RommClient.romCacheFile(wiring.romRoot, romId, spec.fileName, spec.fileIds).fileName.toString()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private suspend fun negotiate(game: Game, player: Player, target: LaunchTarget): Placement = coroutineScope {
        val wiring = gate.wiring()
        val romId = game.romId()
        // Fetched beside the save sync. After the saves, a checked file the player
        // would use keeps the short wait. Otherwise the launch waits that same
        // short time to learn whether the listing is fetching a file this player names.
        val firmwareProgress = LaunchProgress()
        val downloading = CompletableDeferred<Boolean>()
        val fetching = if (target is LaunchTarget.ContentUri) {
            background.async { fetchFirmware(wiring, game, player, firmwareProgress, downloading) }
        } else {
            null
        }
        // A slow server does not hold the game. A cut-short sync leaves files whole: each write is a rename.
        val supportsSync = player.supportsSaveSync(game)
        val sync = if (supportsSync) {
            report(romId, GameActivity.SyncingSaves)
            withTimeoutOrNull(LAUNCH_SYNC_LIMIT_MS) { syncRom(romId, player.id) }
                ?: pending(wiring, romId, "RomM took too long. Saves will sync later.")
        } else SyncResult(SyncOutcome.Unchanged)
        val visibleSync = if (supportsSync && savesUnreachable(game, player)) unreachableSync() else sync
        val shown = if (supportsSync) GameActivity.Synced(visibleSync) else GameActivity.Idle
        report(romId, shown)
        // A sync still running in the background may rename a save at any time, so
        // the shell gets copies whose hashes match their bytes.
        // A file the player would use is already checked, so a slow fetch must not
        // hold the launch or change what the tile says. Nothing usable yet: wait
        // the same short time for the listing. A file this player names, a zip
        // member included, is on its way, so wait out that download and show it.
        // Nothing named, a failed listing, or no answer: the checked copies, which
        // may be none, and the tile stays on the sync.
        val firmware = fetching?.let { job ->
            val checked = checkedFirmwareFor(wiring, game, player)
            if (checked.usable) {
                withTimeoutOrNull(FIRMWARE_AFTER_SYNC_MS) { job.await() } ?: checked.files
            } else {
                when (withTimeoutOrNull(FIRMWARE_AFTER_SYNC_MS) { downloading.await() }) {
                    true -> {
                        val told = AtomicBoolean(false)
                        if (job.isActive) {
                            firmwareProgress.follow { activity ->
                                told.set(true)
                                // The game is here already: what is arriving is its firmware.
                                val said = (activity as? GameActivity.Downloading)
                                    ?.let { GameActivity.GettingFirmware(it.bytesSoFar, it.totalBytes) } ?: activity
                                report(romId, said)
                            }
                        }
                        val got = try {
                            withTimeoutOrNull(FIRMWARE_FETCH_LIMIT_MS) { job.await() }
                        } finally {
                            firmwareProgress.stop()
                        }
                        // The tile was on the download. Put back the same sync mark it had.
                        if (told.get()) report(romId, shown)
                        got ?: checked.files
                    }
                    // The listing already answered. Take its result if it has one.
                    false -> withTimeoutOrNull(FIRMWARE_AFTER_SYNC_MS) { job.await() } ?: checked.files
                    // The short wait already elapsed, and the listing still has not answered.
                    null -> checked.files
                }
            }
        }.orEmpty()
        Placement(withFirmware(target, firmware), if (supportsSync) snapshotSaves(wiring, romId) else emptyList(), visibleSync)
    }

    /** [target] with [firmware] added, see [firmwareFor]. */
    private fun withFirmware(target: LaunchTarget, firmware: List<FirmwareBytes>): LaunchTarget =
        if (target is LaunchTarget.ContentUri && firmware.isNotEmpty()) target.copy(firmware = target.firmware + firmware) else target

    /**
     * The firmware [player] asks for that RomM keeps for [game]'s platform, such as a
     * console's BIOS, each file downloaded and checked. Null when RomM did not answer,
     * or [offlineCatalog] says it will not. Firmware never stops a launch.
     *
     * [downloading] is completed once the listing is filtered, true when a file this
     * player names is about to be fetched. A fetch that never gets that far completes
     * it false.
     */
    private suspend fun fetchFirmware(
        wiring: RommWiring,
        game: Game,
        player: Player,
        progress: LaunchProgress,
        downloading: CompletableDeferred<Boolean>,
    ): List<FirmwareBytes>? {
        try {
            if (offlineCatalog) return null
            val platformId = gate.restoreCatalog(wiring).rom(game.remoteKey)?.platformId ?: return emptyList()
            val dir = firmwareDir(wiring, platformId)
            return try {
                withTimeoutOrNull(FIRMWARE_FETCH_LIMIT_MS) {
                    gate.call { _, ops ->
                        val wanted = ops.firmware(platformId)
                            .filter { isPlainFileName(it.fileName) && player.wantsFirmware(it.fileName) }
                        // The launch is waiting on this before it will wait out a download.
                        downloading.complete(wanted.isNotEmpty())
                        if (wanted.isEmpty()) {
                            emptyList()
                        } else {
                            // One bar for every file this launch is fetching. RomM gives each size.
                            val total = wanted.sumOf { it.fileSizeBytes }
                            val reporter = downloadReporter { progress.note(it) }
                            reporter(RomProgress.Receiving(0L, total))
                            var before = 0L
                            wanted.flatMap { firmware ->
                                val moved = AtomicBoolean(false)
                                val path = ops.downloadFirmware(firmware, dir.resolve(firmware.fileName)) { step ->
                                    moved.set(true)
                                    val scaled = if (step is RomProgress.Receiving) {
                                        RomProgress.Receiving(before + step.bytes, total)
                                    } else {
                                        step
                                    }
                                    reporter(scaled)
                                }
                                before += firmware.fileSizeBytes
                                // Already checked: nothing was transferred, and the bar still moves past it.
                                if (!moved.get()) reporter(RomProgress.Receiving(before, total))
                                // A zip is offered as itself and, when the player wants one, as each file it holds.
                                val members = runInterruptible(Dispatchers.IO) {
                                    unpackFirmwareZip(path, planning = { progress.note(GameActivity.Checking) }) {
                                        player.wantsFirmware(it)
                                    }
                                }
                                listOf(firmware.fileName to path) + members
                            }
                        }
                    }
                }?.filter { player.wantsFirmware(it.first) }
                ?.map { (name, path) -> FirmwareBytes(name = name, contentUri = wiring.contentUris.uriFor(path)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        } finally {
            // No listing, a failed one, or a fetch that ended first: nothing is on its way.
            downloading.complete(false)
        }
    }

    /** Firmware already on disk, and whether the player would write any of it. */
    private data class CheckedFirmware(
        val files: List<FirmwareBytes>,
        val usable: Boolean,
    )

    /**
     * The firmware [player] asks for that was downloaded and checked before, with
     * no network call. [CheckedFirmware.usable] is true when that set holds a file
     * [player] would write, see [firmwareIsUsable].
     */
    private suspend fun checkedFirmwareFor(wiring: RommWiring, game: Game, player: Player): CheckedFirmware = try {
        val platformId = gate.restoreCatalog(wiring).rom(game.remoteKey)?.platformId
        if (platformId == null) {
            CheckedFirmware(emptyList(), false)
        } else {
            // Headers are read here, off the caller, with the other firmware disk reads.
            runInterruptible(Dispatchers.IO) {
                val files = checkedFirmware(firmwareDir(wiring, platformId)).filter { player.wantsFirmware(it.first) }
                CheckedFirmware(
                    files.map { (name, path) -> FirmwareBytes(name = name, contentUri = wiring.contentUris.uriFor(path)) },
                    firmwareIsUsable(player, files),
                )
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        CheckedFirmware(emptyList(), false)
    }

    /**
     * True when [files] holds firmware [player] would write. Each file is offered
     * under the name it is kept by, with its first [Player.firmwareHeaderSize]
     * bytes. A zip whose unpack is already checked has its members here as
     * `<zip>/<file>`, the same names a launch offers. A player that leaves
     * [Player.chooseFirmware] as it stands takes every file, so any checked file
     * counts. No choice, or a player that throws, counts as none.
     */
    private fun firmwareIsUsable(player: Player, files: List<Pair<String, Path>>): Boolean {
        if (files.isEmpty()) return false
        val size = try {
            player.firmwareHeaderSize
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return false
        }
        if (size < 0) return false
        val offers = files.map { (name, path) -> FirmwareOffer(name, firmwareHeader(path, size)) }
        return try {
            player.chooseFirmware(offers).isNotEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    /** The first [size] bytes of [path], fewer when the file is shorter. Empty when it cannot be read. */
    private fun firmwareHeader(path: Path, size: Int): ByteArray {
        if (size == 0) return ByteArray(0)
        return try {
            Files.newInputStream(path).use { input ->
                val bytes = ByteArray(size)
                var filled = 0
                while (filled < size) {
                    val count = input.read(bytes, filled, size - filled)
                    if (count < 0) break
                    filled += count
                }
                if (filled == size) bytes else bytes.copyOf(filled)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (Thread.currentThread().isInterrupted) throw CancellationException("firmware header read was cancelled")
            ByteArray(0)
        }
    }

    private fun firmwareDir(wiring: RommWiring, platformId: Long): Path =
        wiring.romRoot.resolve(FIRMWARE_DIR).resolve(platformId.toString())

    private fun Player.wantsFirmware(name: String): Boolean = try {
        firmwareFileName(name) != null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
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
        if (!player.supportsSaveSync(game)) return pending(wiring, romId, null)
        val unreachable = savesUnreachable(game, player)
        background.launch { syncRomReported(romId, player.id, unreachable) }
        return if (unreachable) unreachableSync() else pending(wiring, romId, null)
    }

    /**
     * True when [player] keeps a save slot where Foldcade has no place to read or
     * write it, as a player with no save folder chosen does. Nothing moves
     * between it and RomM then.
     */
    private fun savesUnreachable(game: Game, player: Player): Boolean = try {
        val slots = player.saveDeclarations(game)
        slots.isEmpty() || slots.any { it.locationUri == null }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        true
    }

    /** How a sync reads when the player's saves could not be reached: not synced. */
    private fun unreachableSync(): SyncResult = SyncResult(SyncOutcome.Unchanged, SAVE_UNREACHABLE)

    override suspend fun serverSaves(game: Game, player: Player?): List<app.foldcade.api.plugin.ServerSave> {
        val romId = game.romId()
        return romLock(romId).withLock {
            gate.session { wiring, ops ->
                rememberDevice(wiring, ops)
                val listed = ops.listSaves(romId, wiring.rememberedDevice!!.deviceId)
                    .filter { it.romId == romId }.sortedWith(compareByDescending<app.foldcade.romm.RommSave> { it.updatedAt }.thenByDescending { it.id })
                listedBackups[wiring.data.romSaves(romId)] = listed.filter {
                    it.slot == null && it.fileName.matches(Regex("""foldcade-kept-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\.(sav|zip)"""))
                }.mapTo(HashSet()) { it.id.toString() }
                val slots = player?.saveDeclarations(game).orEmpty().map { it.slot }.toSet()
                val ledger = wiring.data.ledger(romId)
                val playing = slots.mapNotNull { slot ->
                    val held = wiring.data.slotFile(romId, slot)
                    val matchesLedger = runInterruptible(Dispatchers.IO) {
                        Files.isRegularFile(held) && ledger.slot(slot)?.syncedHash == md5File(held)
                    }
                    val hash = if (matchesLedger) runInterruptible(Dispatchers.IO) { app.foldcade.romm.rommSaveHash(held) } else null
                    listed.firstOrNull { it.slot == slot && hash != null && it.contentHash?.lowercase() == hash }?.id
                        ?: listed.firstOrNull { it.slot == slot }?.id
                }.toSet()
                listed.map { save ->
                    app.foldcade.api.plugin.ServerSave(
                        save.id.toString(), save.slot, save.emulator,
                        save.deviceSyncs.firstOrNull { it.deviceId == save.originDeviceId }?.deviceName?.takeIf { it.isNotBlank() }
                            ?: save.emulator?.takeIf { it.isNotBlank() },
                        save.updatedAt.toEpochMilli(), save.sizeBytes, save.id in playing,
                        save.screenshotId?.let { "${RommClient.normalizeOrigin(wiring.origin)}/api/screenshots/$it/content" },
                    )
                }
            }
        }
    }

    override suspend fun serverSaveBackups(game: Game): Set<String> =
        listedBackups[gate.wiring().data.romSaves(game.romId())].orEmpty()

    override suspend fun syncServerSaves(game: Game, player: Player?): SyncResult = serverSaveWork(game) { wiring, ops, romId ->
        strictSaveSync(wiring, ops, romId, player?.id).toResult()
    }

    override suspend fun playServerSave(game: Game, player: Player, saveId: String, beforeCommit: () -> Unit): List<PlacedSave> = serverSaveWork(game) { wiring, ops, romId ->
        requireSavePlayer(game, player)
        // Every bundled syncing player owns one slot. Do not guess for a future multi-slot player.
        val slot = player.saveDeclarations(game).singleOrNull()?.slot
            ?: throw PluginException.Unavailable("Choose a player with one save slot")
        chooseSave(wiring, ops, romId, saveId, slot, player.id, player.id, beforeCommit)
    }

    override suspend fun copyServerSave(game: Game, saveId: String, toSlot: String, toEmulator: String?, player: Player?, beforeCommit: () -> Unit): List<PlacedSave> =
        serverSaveWork(game) { wiring, ops, romId ->
            val own = player?.saveDeclarations(game).orEmpty().any { it.slot == toSlot }
            if (own) requireSavePlayer(game, player!!)
            chooseSave(wiring, ops, romId, saveId, toSlot, toEmulator, player?.id.takeIf { own }, beforeCommit)
        }

    /** Strict sync, download and promotion all share the ROM lock, including concurrent return syncs. */
    private suspend fun <T> serverSaveWork(game: Game, action: suspend (RommWiring, RommOps, Long) -> T): T {
        val romId = game.romId()
        report(romId, GameActivity.SyncingSaves)
        var finished = false
        try {
            return romLock(romId).withLock {
                gate.session { wiring, ops ->
                    rememberDevice(wiring, ops)
                    recoverSelection(wiring, ops, romId)
                    action(wiring, ops, romId)
                }
            }.also { finished = true }
        } finally {
            report(romId, if (finished) GameActivity.Synced(SyncResult(SyncOutcome.Uploaded)) else GameActivity.Idle)
        }
    }

    private fun requireSavePlayer(game: Game, player: Player) {
        if (!player.supportsSaveSync(game) || savesUnreachable(game, player))
            throw PluginException.Unavailable(SAVE_UNREACHABLE)
    }

    private suspend fun strictSaveSync(wiring: RommWiring, ops: RommOps, romId: Long, emulator: String?): SaveSyncReport {
        val report = syncOnce(wiring, ops, romId, emulator)
        rememberConflicts(wiring, report)
        if (report.queued.isNotEmpty() || !report.completedSession)
            throw PluginException.Unavailable("Save sync did not finish", app.foldcade.romm.RommUnavailable("Save sync is waiting for RomM"))
        if (report.keptBoth.isNotEmpty() || report.failed.isNotEmpty() ||
            runInterruptible(Dispatchers.IO) { unsyncedSlots(wiring, romId).isNotEmpty() }) {
            throw PluginException.Unavailable("Save sync did not finish")
        }
        return report
    }

    private suspend fun chooseSave(wiring: RommWiring, ops: RommOps, romId: Long, id: String, slot: String, emulator: String?, localPlayer: String?, beforeCommit: () -> Unit): List<PlacedSave> {
        strictSaveSync(wiring, ops, romId, localPlayer)
        val device = wiring.rememberedDevice!!.deviceId
        val listed = ops.listSaves(romId, device).filter { it.romId == romId }
        val chosen = listed.singleOrNull { it.id.toString() == id } ?: throw PluginException.NotFound("Save is no longer available")
        // A foreign destination must still be the slot the confirmation named. No arbitrary new slots.
        val latest = listed.filter { it.slot == slot }.maxWithOrNull(compareBy<app.foldcade.romm.RommSave> { it.updatedAt }.thenBy { it.id })
        if (localPlayer == null && (latest == null || latest.emulator != emulator))
            throw PluginException.Unavailable("The destination slot changed; choose it again")
        val hash = chosen.contentHash?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{32}")) }
            ?: throw PluginException.Unavailable("This save has no verifiable content hash")
        val selection = SaveSelection(wiring.data.romSaves(romId))
        ops.downloadSave(chosen, device, selection.staged)
        val bytes = runInterruptible(Dispatchers.IO) {
            val bytes = Files.readAllBytes(selection.staged)
            if (bytes.size.toLong() != chosen.sizeBytes || app.foldcade.romm.rommSaveHash(bytes) != hash)
                throw PluginException.Unavailable("The downloaded save did not verify")
            bytes
        }
        // The server can have a slot retention cap even though we never request autocleanup.
        // Keep the current head outside slot retention before a confirmed overwrite can prune it.
        if (latest != null && latest.contentHash?.lowercase() != hash) {
            val previousHash = latest.contentHash?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{32}")) }
                ?: throw PluginException.Unavailable("The current slot head cannot be verified")
            if (listed.none { it.slot == null && it.contentHash?.lowercase() == previousHash }) {
                val previous = wiring.data.romSaves(romId).resolve("previous-head.sav")
                ops.downloadSave(latest, device, previous)
                val previousBytes = runInterruptible(Dispatchers.IO) {
                    val held = Files.readAllBytes(previous)
                    if (held.size.toLong() != latest.sizeBytes || app.foldcade.romm.rommSaveHash(held) != previousHash)
                        throw PluginException.Unavailable("The current slot head did not verify")
                    held
                }
                ops.uploadArchive(latest, device, previousHash, previousBytes)
            }
        }
        if (localPlayer != null) runInterruptible(Dispatchers.IO) {
            val current = wiring.data.slotFile(romId, slot)
            selection.begin(slot, localPlayer, md5File(selection.staged), hash, if (Files.exists(current)) md5File(current) else null)
        }
        // All refusals above leave the player's fresh-load marker alone. From this point an
        // upload may have committed even if its response is lost, so retain the marker then.
        try {
            if (localPlayer != null) beforeCommit()
        } catch (cancelled: CancellationException) {
            runInterruptible(Dispatchers.IO) { selection.abandon() }
            throw cancelled
        } catch (failure: Exception) {
            runInterruptible(Dispatchers.IO) { selection.abandon() }
            throw PluginException.SaveChoiceRefused("The save commit could not start", failure)
        }
        // Slot uploads get a unique versioned filename on RomM. Never send autocleanup.
        try {
            ops.uploadSave(romId, if (localPlayer == null) latest!!.fileName else chosen.fileName, slot, emulator, device, hash, bytes)
        } catch (refused: RommException) {
            val definitive = refused is app.foldcade.romm.RommSlotMoved || refused is app.foldcade.romm.RommUnauthenticated ||
                (refused is RommHttpException && refused.status in 400..499)
            if (!definitive) throw refused
            if (localPlayer != null) runInterruptible(Dispatchers.IO) { selection.abandon() }
            throw PluginException.SaveChoiceRefused("RomM refused the save choice", refused)
        }
        if (localPlayer == null) return emptyList()
        runInterruptible(Dispatchers.IO) { selection.finish(wiring.data, romId) }
        return snapshotSaves(wiring, romId)
    }

    private suspend fun recoverSelection(wiring: RommWiring, ops: RommOps, romId: Long) {
        val selection = SaveSelection(wiring.data.romSaves(romId))
        if (!runInterruptible(Dispatchers.IO) { selection.pending() }) return
        val listed = ops.listSaves(romId, wiring.rememberedDevice!!.deviceId).filter { it.romId == romId }
        runInterruptible(Dispatchers.IO) { selection.recover(wiring.data, romId, listed) }
    }

    override suspend fun unsyncedSaves(): Set<String> {
        val wiring = gate.wiring()
        return buildSet {
            for (romId in runInterruptible(Dispatchers.IO) { wiring.data.romsWithSaves() }) {
                romLock(romId).withLock {
                    if (runInterruptible(Dispatchers.IO) { unsyncedSlots(wiring, romId).isNotEmpty() }) add(romId.toString())
                }
            }
        }
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
            rememberConflicts(wiring, report)
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
        recoverSelection(wiring, ops, romId)
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

    private fun fileLock(romId: Long): Mutex = fileLocks.computeIfAbsent(romId) { Mutex() }

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
        catalog: RommCatalog,
    ): Long? {
        matchPlatform(platformId, definitions, catalog)?.let { return it.id }
        val listed = ops.platforms()
        catalog.rememberPlatforms(listed)
        return matchPlatform(platformId, definitions, catalog)?.id
    }

    private fun matchPlatform(platformId: String, definitions: List<Platform>, catalog: RommCatalog): app.foldcade.romm.PlatformSummary? =
        catalog.platforms().firstOrNull { platform ->
            platform.slug.equals(platformId, ignoreCase = true) ||
                platform.id.toString() == platformId ||
                alignedPlatformId(definitions, platform.slug).equals(platformId, ignoreCase = true)
        }

    private suspend fun romFor(game: Game, ops: RommOps, catalog: RommCatalog): RomSummary {
        if (game.backendId != instanceId) {
            throw PluginException.NotFound("This game is not from RomM")
        }
        catalog.rom(game.remoteKey)?.let { return it }
        val romId = game.romId()
        val page = ops.roms(
            RomQuery(
                searchTerm = game.label.trim().ifEmpty { null },
                limit = 50,
                offset = 0,
            ),
        )
        catalog.rememberRoms(page.items)
        return page.items.find { it.id == romId }
            ?: throw PluginException.NotFound("RomM has no ROM ${game.remoteKey}")
    }

    override suspend fun pendingSaveConflicts(): List<SaveConflict> {
        val wiring = gate.peek() ?: return emptyList()
        return runInterruptible(Dispatchers.IO) {
            SaveConflictLog(wiring.data.conflictsFile()).pending().map { it.toConflict() }
        }
    }

    override suspend fun resolveSaveConflict(id: String, choice: SaveConflictChoice) {
        val wiring = gate.peek() ?: return
        val romId = id.substringBefore(':').toLongOrNull() ?: return
        romLock(romId).withLock {
            val changed = runInterruptible(Dispatchers.IO) {
                SaveConflictLog(wiring.data.conflictsFile()).resolve(id, choice)
            }
            if (changed) conflictsChanged()
        }
    }

    override suspend fun reopenDeferredSaveConflicts() {
        val wiring = gate.peek() ?: return
        val changed = runInterruptible(Dispatchers.IO) {
            SaveConflictLog(wiring.data.conflictsFile()).reopenDeferred()
        }
        if (changed) conflictsChanged()
    }

    override fun addSaveConflictListener(listener: () -> Unit): AutoCloseable {
        conflictListeners += listener
        return AutoCloseable { conflictListeners -= listener }
    }

    /**
     * Writes each kept-both pair into the conflict log. A copy that could not be
     * preserved is skipped: the archive and the slot file are still both on disk.
     * Failures here do not fail the sync.
     */
    private fun rememberConflicts(wiring: RommWiring, report: SaveSyncReport) {
        if (report.keptBoth.isEmpty()) return
        try {
            val log = SaveConflictLog(wiring.data.conflictsFile())
            var changed = false
            for (kept in report.keptBoth) {
                val entry = kept.toEntry(wiring) ?: continue
                if (log.record(entry)) changed = true
            }
            if (changed) conflictsChanged()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Unit
        }
    }

    private fun KeptBoth.toEntry(wiring: RommWiring): ConflictEntry? {
        val slotName = slot ?: return null
        val localPath = localCopy ?: return null
        val remotePath = remoteCopy ?: return null
        if (!Files.isRegularFile(localPath) || !Files.isRegularFile(remotePath)) return null
        val rom = gate.catalog(wiring).rom(romId.toString())
        val label = rom?.name?.takeIf { it.isNotBlank() }
            ?: rom?.fsName?.takeIf { it.isNotBlank() }
            ?: "ROM $romId"
        return ConflictEntry(
            id = "$romId:$slotName",
            romId = romId,
            slot = slotName,
            gameLabel = label,
            localSource = saveSource(wiring.deviceName, localEmulator, "This device"),
            localUpdatedAtMillis = localUpdatedAtMillis,
            localSizeBytes = localSizeBytes,
            localHash = md5File(localPath),
            remoteSource = saveSource(device = null, emulator = remoteEmulator, fallback = "RomM"),
            remoteUpdatedAtMillis = remoteUpdatedAtMillis,
            remoteSizeBytes = remoteSizeBytes,
            remoteHash = md5File(remotePath),
            localCopy = localPath.toString(),
            remoteCopy = remotePath.toString(),
            slotFile = serverFile.toString(),
            status = ConflictStatus.Open,
            choice = null,
        )
    }

    private fun conflictsChanged() {
        conflictListeners.forEach { listener ->
            try {
                listener()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Unit
            }
        }
    }
}

internal fun Game.romId(): Long =
    remoteKey.toLongOrNull()?.takeIf { it >= 1 }
        ?: throw PluginException.NotFound("RomM game key is not a ROM id")

internal fun RomSummary.bytes(): RomBytes {
    soleGameFile()?.let { game ->
        val taken = mutableSetOf(game.fileName.lowercase())
        val addOns = files.filter { it.category in ADD_ON_CATEGORIES }.map { file ->
            // Two files of one name in different folders still get a file each.
            val name = if (taken.add(file.fileName.lowercase())) file.fileName else "${file.id}-${file.fileName}"
            taken += name.lowercase()
            AddOn(file.id, name, file.fileSizeBytes, file.md5Hash?.takeUnless { RommClient.isArchiveName(file.fileName) })
        }
        return RomBytes(
            fileName = game.fileName,
            fileIds = listOf(game.id),
            expectedSize = game.fileSizeBytes,
            md5 = game.md5Hash?.takeUnless { RommClient.isArchiveName(game.fileName) },
            addOns = addOns,
        )
    }
    discSheet()?.let { sheet ->
        return RomBytes(
            fileName = sheet.fileName,
            fileIds = listOf(sheet.id),
            expectedSize = sheet.fileSizeBytes,
            md5 = sheet.md5Hash,
            addOns = files.filter { it !== sheet }.map { AddOn(it.id, it.fileName, it.fileSizeBytes, it.md5Hash) },
        )
    }
    // One file in a folder of its own, such as a PS3 ISO: RomM sends that file as it is, so its size and hash check it.
    if (hasNestedSingleFile && !hasMultipleFiles) {
        files.singleOrNull()?.let { file ->
            return RomBytes(
                fileName = file.fileName,
                fileIds = listOf(file.id),
                expectedSize = file.fileSizeBytes.takeIf { it > 0 },
                md5 = file.md5Hash?.takeUnless { RommClient.isArchiveName(file.fileName) },
            )
        }
    }
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

/**
 * The one file a folder ROM boots, when RomM can say which: the only file at the
 * top of the folder, with every other file filed under a category such as `update`,
 * `dlc`, or `manual`. A Switch game with its update in `updates/` is one.
 *
 * Asked for several files, RomM sends one zip of them, which no player opens as
 * the game. So the game is fetched alone, and its updates and DLC each on their own.
 * A folder of one file, or one this cannot read, keeps the request it had.
 */
private fun RomSummary.soleGameFile(): RomFileSummary? {
    if (hasSimpleSingleFile || files.size < 2) return null
    val game = files.filter { it.isTopLevel == true }.singleOrNull() ?: return null
    if (game.category != null && game.category != GAME_CATEGORY) return null
    val rest = files.filter { it !== game }
    if (rest.any { it.isTopLevel != false || it.category == null || it.category == GAME_CATEGORY }) return null
    return game
}

/**
 * The file a disc game boots, when its folder is a disc image split into files: a
 * `.cue` and its `.bin` tracks, or an `.m3u` naming each disc. A sheet names the
 * other files by their names alone, so they are fetched one by one into one folder
 * under the names RomM has, and the player is handed the sheet in that folder.
 * A folder of two or more whole-disc images, such as `.chd` or `.iso` files, is a
 * game of that many discs, fetched the same way.
 *
 * An `.m3u` wins over the discs it lists, and sheets over whole-disc images.
 * Several discs with no `.m3u` get one, see [discPlaylist], and this is Disc 1. A folder with files below its top, an
 * archive, or two names that differ only in case keeps the request it had.
 */
private fun RomSummary.discSheet(): RomFileSummary? {
    if (hasSimpleSingleFile || files.size < 2) return null
    if (files.any { it.isTopLevel != true || RommClient.isArchiveName(it.fileName) }) return null
    if (files.map { it.fileName.lowercase() }.toSet().size != files.size) return null
    fun sheets(extensions: Set<String>) =
        files.filter { it.fileName.substringAfterLast('.', "").lowercase() in extensions }
    sheets(PLAYLIST_EXTENSIONS).let { playlists -> if (playlists.isNotEmpty()) return playlists.singleOrNull() }
    sheets(DISC_SHEET_EXTENSIONS).minByOrNull { it.fileName.lowercase() }?.let { return it }
    val images = sheets(DISC_IMAGE_EXTENSIONS).takeIf { it.size >= 2 } ?: return null
    return images.minWithOrNull(compareBy<RomFileSummary> { discNumber(it.fileName) }.thenBy { it.fileName.lowercase() })
}

/** The number in a disc name's `(Disc 2)`, or past every disc for a name with none. */
private fun discNumber(name: String): Int = DISC_TAG.find(name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE

/** What [RommClient.downloadRom] needs for one ROM. [md5] is null when RomM has no hash for it. */
internal data class RomBytes(
    val fileName: String,
    val fileIds: List<Long>,
    val expectedSize: Long?,
    val md5: String? = null,
    /** Updates and DLC, downloaded into the same folder as the game. */
    val addOns: List<AddOn> = emptyList(),
)

/** A file RomM files as `update` or `dlc` for a game. [fileName] is its name beside the game. */
internal data class AddOn(
    val id: Long,
    val fileName: String,
    val size: Long,
    val md5: String? = null,
)

/**
 * For a game of several discs whose folder has no playlist: the `.m3u` name and
 * the disc sheets it lists, Disc 1 first. Handed a playlist, a player can change
 * discs, and DuckStation names the set's memory card after it, which is the set's
 * name with the disc taken out, as `Final Fantasy VII (USA).m3u` for discs named
 * `Final Fantasy VII (USA) (Disc 1).cue` and on. Null for a game of one disc, or
 * one with a playlist of its own.
 */
internal fun discPlaylist(spec: RomBytes): Pair<String, List<String>>? {
    val names = listOf(spec.fileName) + spec.addOns.map { it.fileName }
    fun extension(name: String) = name.substringAfterLast('.', "").lowercase()
    if (names.any { extension(it) in PLAYLIST_EXTENSIONS }) return null
    // Each sheet and each whole-disc image is a disc, so a set may mix them. When two of
    // them are one disc, as a sheet and an image of it, the sheets are the discs.
    val sheets = names.filter { extension(it) in DISC_SHEET_EXTENSIONS }
    val all = sheets + names.filter { extension(it) in DISC_IMAGE_EXTENSIONS }
    val numbers = all.map(::discNumber).filter { it != Int.MAX_VALUE }
    val discs = if (numbers.size == all.size && numbers.toSet().size == numbers.size) all else sheets.ifEmpty { all }
    if (discs.size < 2 || spec.fileName !in discs) return null
    val ordered = discs.sortedWith(compareBy<String> { discNumber(it) }.thenBy { it.lowercase() })
    val title = ordered.first().substringBeforeLast('.').replace(DISC_TAG, "").replace(Regex("\\s+"), " ").trim()
    val name = "${title.ifEmpty { "Discs" }}.m3u"
    if (!isPlainFileName(name) || names.any { it.equals(name, ignoreCase = true) }) return null
    return name to ordered
}

/**
 * The file a player is handed for [spec], downloaded as [rom] for a game of
 * [platformId]: the game file, or the playlist of its discs, written beside them
 * and marked checked like a download when [discPlaylist] names one, so the folder
 * serves it. Only on [PLAYLIST_PLATFORMS], whose players all open a playlist.
 */
internal fun bootFile(rom: Path, spec: RomBytes, platformId: String): Path {
    if (platformId !in PLAYLIST_PLATFORMS) return rom
    val (name, discs) = discPlaylist(spec) ?: return rom
    val playlist = rom.resolveSibling(name)
    // Each disc by the name it is kept under beside the playlist.
    val bytes = discs.joinToString("\n", postfix = "\n") { RommClient.besideRom(rom, it).fileName.toString() }
        .toByteArray(Charsets.UTF_8)
    val md5 = md5Hex(bytes)
    if (!RommClient.isVerifiedRom(playlist, bytes.size.toLong(), md5)) {
        writeDurably(playlist, bytes)
        if (!RommClient.adoptLocalRom(playlist, bytes.size.toLong(), md5)) return rom
    }
    return playlist
}

/** The downloaded game file for [spec] when it and its add-ons still match their verified markers, else null. */
internal fun verifiedRom(cacheRoot: Path, romId: Long, spec: RomBytes): Path? {
    if (!RommClient.canCheckRom(spec.fileIds, spec.expectedSize, spec.md5)) return null
    val target = RommClient.romCacheFile(cacheRoot, romId, spec.fileName, spec.fileIds)
    if (!RommClient.isVerifiedRom(target, spec.expectedSize, comparableMd5(spec))) return null
    val addOnsVerified = spec.addOns.all { addOn ->
        RommClient.isVerifiedRom(
            RommClient.besideRom(target, addOn.fileName),
            addOn.size,
            comparableFileMd5(addOn.fileName, addOn.md5),
        )
    }
    return target.takeIf { addOnsVerified }
}

/**
 * Deletes finished downloads in [rom]'s folder that [spec] no longer names, such as
 * an update RomM has since replaced, so a player that loads the folder finds only
 * this game's current files.
 */
internal fun pruneRomFolder(rom: Path, spec: RomBytes) {
    val keep = (listOf(rom) + spec.addOns.map { RommClient.besideRom(rom, it.fileName) })
        .map { it.fileName.toString() }
        .toSet() + listOfNotNull(discPlaylist(spec)?.first)
    val markers = Files.list(rom.parent).use { entries ->
        entries.iterator().asSequence().filter { it.fileName.toString().endsWith(VERIFIED_SUFFIX) }.toList()
    }
    for (marker in markers) {
        val file = marker.resolveSibling(marker.fileName.toString().removeSuffix(VERIFIED_SUFFIX))
        if (file.fileName.toString() in keep) continue
        Files.deleteIfExists(file)
        Files.deleteIfExists(marker)
    }
}

/**
 * A finished download that matches [spec], even when the process died before the
 * marker was written. The marker is written here, so an offline launch does not
 * try the network. Add-ons have to match too. A file of the wrong size or hash
 * is left for a real download.
 */
internal fun adoptCachedRom(cacheRoot: Path, romId: Long, spec: RomBytes): Pair<Path, RomBytes>? {
    val target = RommClient.romCacheFile(cacheRoot, romId, spec.fileName, spec.fileIds)
    if (!RommClient.adoptLocalRom(target, spec.expectedSize, comparableMd5(spec))) return null
    val addOnsMatch = spec.addOns.all { addOn ->
        RommClient.adoptLocalRom(
            RommClient.besideRom(target, addOn.fileName),
            addOn.size,
            comparableFileMd5(addOn.fileName, addOn.md5),
        )
    }
    return if (addOnsMatch) target to spec else null
}

/** RomM's hash of an archive is of the members, not the file, so it cannot be compared. */
private fun comparableMd5(spec: RomBytes): String? = comparableFileMd5(spec.fileName, spec.md5)

private fun comparableFileMd5(fileName: String, md5: String?): String? =
    md5?.lowercase()?.takeUnless { RommClient.isArchiveName(fileName) }

/** Kept beside the ROM, so a launch after a restart with no network can find it. */
internal fun writeRomSpec(cacheRoot: Path, romId: Long, spec: RomBytes) {
    val json = buildJsonObject {
        put("file_name", spec.fileName)
        put("file_ids", JsonArray(spec.fileIds.map { JsonPrimitive(it) }))
        spec.expectedSize?.let { put("expected_size", it) }
        spec.md5?.let { put("md5", it) }
        if (spec.addOns.isNotEmpty()) {
            put(
                "add_ons",
                JsonArray(
                    spec.addOns.map { addOn ->
                        buildJsonObject {
                            put("id", addOn.id)
                            put("file_name", addOn.fileName)
                            put("size", addOn.size)
                            addOn.md5?.let { put("md5", it) }
                        }
                    },
                ),
            )
        }
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
            addOns = it["add_ons"]?.jsonArray?.map { element ->
                val addOn = element as? JsonObject ?: return null
                AddOn(
                    id = addOn["id"]?.jsonPrimitive?.longOrNull ?: return null,
                    fileName = addOn["file_name"]?.jsonPrimitive?.contentOrNull ?: return null,
                    size = addOn["size"]?.jsonPrimitive?.longOrNull ?: return null,
                    md5 = addOn["md5"]?.jsonPrimitive?.contentOrNull,
                )
            } ?: emptyList(),
        )
    }
} catch (_: Exception) {
    null
}

private fun romSpecFile(cacheRoot: Path, romId: Long): Path =
    romDir(cacheRoot, romId).resolve(ROM_SPEC_FILE)

/** Everything downloaded for [romId]: its file, a partial one, and its file list. */
private fun romDir(cacheRoot: Path, romId: Long): Path =
    cacheRoot.resolve("roms").resolve(romId.toString())

internal fun dropRom(cacheRoot: Path, romId: Long) {
    val dir = romDir(cacheRoot, romId)
    if (Files.exists(dir) && !dir.toFile().deleteRecursively()) throw IOException("Could not delete $dir")
}

internal fun alignedPlatformId(definitions: Iterable<Platform>, slug: String): String =
    canonicalPlatformId(definitions, slug) ?: slug

internal fun RomSummary.toGame(
    cacheRoot: Path,
    definitions: Iterable<Platform>,
    instanceId: String = ROMM_LIBRARY_ID,
): Game = Game(
    backendId = instanceId,
    remoteKey = id.toString(),
    platformId = alignedPlatformId(definitions, platformSlug),
    availability = cacheAvailability(cacheRoot, this),
    label = name?.takeIf { it.isNotBlank() } ?: fsName,
    // RomM's own figure for the whole ROM, as its web page shows it.
    sizeBytes = fsSizeBytes.takeIf { it > 0 },
)

internal fun cacheAvailability(cacheRoot: Path, rom: RomSummary): Availability {
    val spec = rom.bytes()
    val target = RommClient.romCacheFile(cacheRoot, rom.id, spec.fileName, spec.fileIds)
    val files = listOf(target to (spec.expectedSize to spec.md5)) + spec.addOns.map { addOn ->
        val hash = addOn.md5?.takeUnless { RommClient.isArchiveName(addOn.fileName) }
        RommClient.besideRom(target, addOn.fileName) to (addOn.size as Long? to hash)
    }
    val whole = files.all { (file, expected) ->
        Files.isRegularFile(file) &&
            !Files.exists(file.resolveSibling(file.fileName.toString() + ".partial")) &&
            localRomMatches(file, expected.first, expected.second)
    }
    return if (whole) Availability.Cached else Availability.RemoteOnly
}

/** A file on disk matches the size and hash RomM published. A missing check is not a mismatch. */
/**
 * The firmware in [dir] that was downloaded and checked, by name: each file
 * with its verified marker. A newer copy that stopped part way, beside it, does
 * not touch it, since firmware is moved into place only once it is whole.
 */
internal fun checkedFirmware(dir: Path): List<Pair<String, Path>> {
    if (!Files.isDirectory(dir)) return emptyList()
    val files = Files.list(dir).use { stream -> stream.collect(java.util.stream.Collectors.toList()) }
    val checked = files
        .filter { file -> FIRMWARE_SIDE_FILES.none { file.fileName.toString().endsWith(it) } }
        .filter { RommClient.isVerifiedRom(it, null, null, besidePartial = true) }
        .map { it.fileName.toString() to it }
    // A zip's files, unpacked beside it, under `<zip name>/<file name>`.
    val members = checked.flatMap { checkedZipMembers(it.second) }
    return (checked + members).sortedBy { it.first }
}

/** A name that stays one file in its folder: no folder in it, and not `.` or `..`. */
internal fun isPlainFileName(name: String): Boolean =
    name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' }

internal fun localRomMatches(target: Path, expectedSize: Long?, expectedMd5: String?): Boolean {
    if (expectedSize != null && Files.size(target) != expectedSize) return false
    if (expectedMd5 != null && !md5File(target).equals(expectedMd5, ignoreCase = true)) return false
    return true
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

/**
 * Notes from a firmware fetch. [follow] publishes them, including the latest
 * already taken, until [stop]. A launch follows only while it waits on a first
 * download, so a checked copy's short wait stays quiet.
 *
 * Publishing holds [lock], the same lock [stop] takes. A listener only records
 * the note, so that hold is cheap, and once [stop] returns nothing more is published.
 */
internal class LaunchProgress {
    private val lock = Any()
    private var live = false
    private var latest: GameActivity? = null
    private var sink: ((GameActivity) -> Unit)? = null

    fun note(activity: GameActivity) {
        synchronized(lock) {
            latest = activity
            if (live) sink?.invoke(activity)
        }
    }

    fun follow(sink: (GameActivity) -> Unit) {
        synchronized(lock) {
            this.sink = sink
            live = true
            latest?.let(sink)
        }
    }

    fun stop() {
        synchronized(lock) {
            live = false
            sink = null
        }
    }
}

/** The shortest time between two download reports for one game. */
private const val PROGRESS_GAP_NS = 100_000_000L

/** How long a launch waits for its save sync. */
private const val LAUNCH_SYNC_LIMIT_MS = 20_000L

/**
 * How long a launch waits for a newer copy when firmware it would use is already
 * checked, and how long it waits to hear whether a listing is fetching a file
 * this player names.
 */
private const val FIRMWARE_AFTER_SYNC_MS = 5_000L

/**
 * How long a firmware download may run. A launch waits this long only when that
 * listing is fetching a file this player names and nothing usable is checked yet.
 */
private const val FIRMWARE_FETCH_LIMIT_MS = 300_000L

/** Why a sync with no reachable save slot reads as not synced. */
internal const val SAVE_UNREACHABLE = "This player’s save location is not available for this game"

/** Under the download root: each platform's firmware, in a folder named by RomM's platform id. */
internal const val FIRMWARE_DIR = "firmware"

/** What [RommClient.downloadFirmware] keeps beside a file: its marker, and a download not finished. */
private val FIRMWARE_SIDE_FILES = listOf(".verified", ".partial")

/** The slot name RomM uses for a save with no slot. */
internal const val ARCHIVE_SLOT = "archive"

private const val ROM_SPEC_FILE = "rom.json"

/** [RommClient.downloadRom] marks a finished, checked file with this sibling. */
private const val VERIFIED_SUFFIX = ".verified"

/** RomM's `category` for the file that boots. */
private const val GAME_CATEGORY = "game"

/** RomM's `category` values for files a player loads with the game, from its folder. */
private val ADD_ON_CATEGORIES = setOf("update", "dlc")

/** A list of a game's discs. */
private val PLAYLIST_EXTENSIONS = setOf("m3u")

/** Sheets that name a disc's track files: CUE, Dreamcast GDI, CloneCD. */
private val DISC_SHEET_EXTENSIONS = setOf("cue", "gdi", "ccd")

/** Images that hold a whole disc in one file. A `.pbp` holds every disc of a set, so it is not one. */
private val DISC_IMAGE_EXTENSIONS = setOf("chd", "iso")

/**
 * Platforms whose players all open an `.m3u`: DuckStation, and RetroArch's
 * PlayStation cores. A game of several discs there is handed a playlist of them.
 */
internal val PLAYLIST_PLATFORMS = setOf("playstation")

/** A disc number in a file name, as Redump writes it: ` (Disc 2)`, also ` (Disc 2 of 3)` or ` (CD 2)`. */
private val DISC_TAG = Regex("\\s*\\((?:Disc|Disk|CD)\\s*(\\d+)(?:\\s+of\\s+\\d+)?\\)", RegexOption.IGNORE_CASE)
