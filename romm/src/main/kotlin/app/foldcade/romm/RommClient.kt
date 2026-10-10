package app.foldcade.romm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import app.foldcade.net.HttpStack
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * HTTP client for one RomM instance.
 *
 * Calls suspend and run on [Dispatchers.IO]. Cancelling the calling coroutine
 * cancels the OkHttp call. Connect, read, and write timeouts bound every call.
 *
 * [origin] is the instance root, such as `https://romm.example`. A trailing
 * `/api` is stripped, in any case. A username or password in the URL is rejected
 * so it is never saved with the origin. The token is a Client API Token (`rmm_…`)
 * or the token device-code sign-in returns. This class does not store it.
 *
 * It does not implement a library backend or a metadata provider.
 *
 * Its client comes from [HttpStack], the app's only HTTP stack. Cleartext is an explicit LAN opt-in:
 * an http [origin] must be a loopback, private, link-local, or Tailscale address,
 * or a localhost, `.local`, `.home.arpa`, or `.ts.net` name. The network-security config
 * cannot name a dynamic LAN address, so it still permits cleartext, and
 * [RommCleartextInterceptor] rejects any other http request, including redirects.
 * A same-scheme redirect to another origin is not followed, so the bearer
 * stays on [origin]. An http to https redirect is left for the setup hint.
 */
class RommClient(
    origin: String,
    private val accessToken: () -> String? = { null },
    connectTimeout: Duration = Duration.ofSeconds(15),
    readTimeout: Duration = Duration.ofSeconds(30),
    writeTimeout: Duration = Duration.ofSeconds(30),
    httpLog: ((String) -> Unit)? = null,
) : AutoCloseable {
    val origin: String = normalizeOrigin(origin)

    private val http: OkHttpClient = HttpStack.client(
        connectTimeout = connectTimeout,
        readTimeout = readTimeout,
        writeTimeout = writeTimeout,
        followSslRedirects = false,
        network = listOf(RommCleartextInterceptor(origin)),
        application = listOfNotNull(httpLog?.let(::RedactingLoggingInterceptor)),
    )

    suspend fun heartbeat(): Heartbeat {
        val raw = exchange(api("/heartbeat"), "GET", authenticated = false)
        raw.require(200)
        return parseHeartbeat(raw.body)
    }

    /**
     * One unauthenticated `GET /api/heartbeat`. Does not send the token.
     * An http origin that redirects to https returns [TRY_HTTPS_HINT].
     * The client does not follow that redirect (`followSslRedirects` is false).
     */
    suspend fun redirectHint(): String? {
        val raw = try {
            exchange(api("/heartbeat"), "GET", authenticated = false)
        } catch (e: CancellationException) {
            throw e
        } catch (_: RommUnavailable) {
            return null
        }
        return httpsRedirectHint(raw.status, raw.header("Location"))
    }

    /**
     * One authenticated call, after the redirect hint.
     * [RommSignInResult.StayOnForm] means the token was not accepted or the
     * server could not be reached. The caller leaves the connect form open.
     */
    suspend fun confirmSignIn(): RommSignInResult {
        val hint = try {
            redirectHint()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        return try {
            platforms()
            RommSignInResult.Accepted(hint)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RommHttpException) {
            RommSignInResult.StayOnForm(hint)
        } catch (_: RommUnavailable) {
            RommSignInResult.StayOnForm(hint)
        } catch (_: RommException) {
            RommSignInResult.StayOnForm(hint)
        }
    }

    /**
     * One artwork image, as bytes. [uri] must be on [origin]: any other address
     * is refused before a request is made. No bearer is sent, because an artwork
     * address is loaded as that string alone. A body over [maxBytes] is refused.
     */
    suspend fun artwork(uri: String, maxBytes: Int = ARTWORK_MAX_BYTES): ByteArray {
        if (!sameOrigin(origin, uri)) throw RommResponseException("artwork is not on the RomM origin")
        val raw = exchange(URI.create(uri), "GET", authenticated = false)
        raw.require(200)
        if (raw.body.size > maxBytes) throw RommResponseException("artwork is over $maxBytes bytes")
        return raw.body
    }

    suspend fun openApiInfo(): OpenApiInfo {
        val raw = try {
            exchange(URI.create("$origin/openapi.json"), "GET", authenticated = false)
        } catch (e: RommUnavailable) {
            throw e
        }
        if (raw.status != 200) throw RommProtocolUnreadable("HTTP ${raw.status}")
        val info = try {
            parseObject(raw.body)["info"]?.jsonObject
        } catch (e: RommResponseException) {
            throw RommProtocolUnreadable("not JSON")
        } ?: throw RommProtocolUnreadable("no info object")
        val version = info.optString("version") ?: throw RommProtocolUnreadable("no info.version")
        val parsed = RommVersion.parse(version) ?: throw RommProtocolUnreadable(version)
        return OpenApiInfo(info.optString("title"), version, parsed.major)
    }

    /**
     * Device-code sign-in. One poll is [pollDeviceToken]. The caller waits
     * [DeviceAuthChallenge.intervalSeconds] between polls.
     */
    suspend fun beginDeviceAuth(
        clientDeviceIdentifier: String,
        name: String,
        clientVersion: String,
    ): DeviceAuthChallenge {
        require(clientDeviceIdentifier.isNotBlank()) { "client device identifier is empty" }
        val beat = heartbeat()
        val version = RommVersion.parse(beat.version)
            ?: throw RommUnsupportedServer(
                beat.version,
                "Device-code sign-in needs a RomM version, and this heartbeat did not have one.",
            )
        if (!version.isAtLeast(5, 0, 0)) {
            throw RommUnsupportedServer(
                beat.version,
                "Device-code sign-in needs RomM 5.0.0 or newer. This server is ${beat.version}.",
            )
        }
        val raw = exchange(
            api("/auth/device/init"),
            "POST",
            authenticated = false,
            body = deviceAuthBody(clientDeviceIdentifier, name, clientVersion),
            contentType = "application/json",
        )
        raw.require(201)
        return parseChallenge(raw.body)
    }

    fun verificationUrl(challenge: DeviceAuthChallenge): String {
        val path = challenge.verificationPathComplete
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return origin + if (path.startsWith("/")) path else "/$path"
    }

    suspend fun pollDeviceToken(deviceCode: String): DeviceTokenPoll {
        val raw = exchange(
            api("/auth/device/token"),
            "POST",
            authenticated = false,
            body = deviceTokenBody(deviceCode),
            contentType = "application/json",
        )
        if (raw.status == 200) return parseApproved(raw.body)
        if (raw.status == 400) {
            return when (raw.detail()) {
                "authorization_pending" -> DeviceTokenPoll.Pending
                "slow_down" -> DeviceTokenPoll.SlowDown
                "access_denied" -> DeviceTokenPoll.Denied
                "expired_token" -> DeviceTokenPoll.Expired
                else -> throw rommHttp(400, raw.detail())
            }
        }
        throw rommHttp(raw.status, raw.detail())
    }

    suspend fun platforms(): List<PlatformSummary> {
        val raw = exchange(api("/platforms"), "GET", authenticated = true)
        raw.require(200)
        val element = parseElement(raw.body.toString(Charsets.UTF_8))
        if (element !is JsonArray) throw RommResponseException("platforms response is not an array")
        return element.map { parsePlatform(it.jsonObject) }
    }

    suspend fun roms(query: RomQuery = RomQuery()): RomPage {
        require(query.limit in 1..10_000) { "limit must be 1..10000" }
        require(query.offset >= 0) { "offset must be >= 0" }
        val params = mutableListOf(
            "limit" to query.limit.toString(),
            "offset" to query.offset.toString(),
            "with_files" to "true",
            "with_char_index" to "false",
            "with_filter_values" to "false",
            "with_rom_id_index" to "false",
            "order_by" to "",
        )
        if (!query.searchTerm.isNullOrBlank()) params += "search_term" to query.searchTerm
        query.platformIds.forEach { params += "platform_ids" to it.toString() }
        val raw = exchange(api("/roms", params), "GET", authenticated = true)
        raw.require(200)
        return parseRomPage(raw.body)
    }

    /**
     * One ROM by id. `GET /api/roms/{id}/simple` is the list item shape and
     * needs `roms.read`. A missing id is HTTP 404.
     */
    suspend fun rom(id: Long): RomSummary {
        require(id >= 1)
        val raw = exchange(api("/roms/$id/simple"), "GET", authenticated = true)
        raw.require(200)
        return parseRom(parseObject(raw.body))
    }

    /**
     * Downloads one ROM into [cacheRoot] with `purpose=play`. Does not send `format`.
     *
     * A finished file is reused only when its verified marker matches
     * [expectedSize] and [expectedMd5]. A finished file from before markers is
     * hashed once. A file that no longer matches is downloaded again.
     *
     * A `.partial` sibling is resumed with `Range` and `If-Range`, using the
     * `ETag` or `Last-Modified` the first response sent. A partial without one,
     * or a 206 that does not start where the partial ends, starts over. The
     * finished bytes must match [expectedSize] and [expectedMd5] when RomM
     * gave them, and are synced to storage before the rename that publishes them.
     * Two calls for the same file take turns.
     */
    suspend fun downloadRom(
        romId: Long,
        fileName: String,
        cacheRoot: Path,
        fileIds: List<Long> = emptyList(),
        expectedSize: Long? = null,
        expectedMd5: String? = null,
    ): Path {
        require(romId >= 1)
        val target = romCacheFile(cacheRoot, romId, fileName, fileIds)
        // RomM hashes an archive by its members, not its bytes, so only its size can be checked.
        val md5 = expectedMd5?.lowercase()?.takeUnless { isArchiveName(fileName) }
        return romLock(target).withLock {
            downloadRomLocked(romId, fileName, target, fileIds, expectedSize, md5)
        }
    }

    private suspend fun downloadRomLocked(
        romId: Long,
        fileName: String,
        target: Path,
        fileIds: List<Long>,
        expectedSize: Long?,
        expectedMd5: String?,
    ): Path {
        val partial = partialOf(target)
        val validatorFile = target.resolveSibling(target.fileName.toString() + VALIDATOR_SUFFIX)
        if (Files.isRegularFile(target) && !Files.exists(partial)) {
            if (isVerifiedRom(target, expectedSize, expectedMd5)) return target
            val reusable = withContext(Dispatchers.IO) {
                val size = Files.size(target)
                if (expectedSize != null && size != expectedSize) return@withContext false
                val md5 = runInterruptible { md5File(target) }
                if (expectedMd5 != null && md5 != expectedMd5) return@withContext false
                writeVerifiedMarker(target, size, md5)
                true
            }
            if (reusable) return target
            // The old file stays until a verified replacement is renamed over it.
            Files.deleteIfExists(markerOf(target))
        }
        val params = mutableListOf("purpose" to "play")
        if (fileIds.isNotEmpty()) params += "file_ids" to fileIds.joinToString(",")
        val uri = api("/roms/$romId/content/${encode(safeFileName(fileName))}", params)
        val validator = readValidator(validatorFile)
        val resumeFrom = if (validator != null && Files.isRegularFile(partial) && Files.size(partial) > 0) {
            Files.size(partial)
        } else {
            Files.deleteIfExists(partial)
            Files.deleteIfExists(validatorFile)
            null
        }
        try {
            streamRom(uri, partial, validatorFile, resumeFrom, validator)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RommException) {
            val restart = resumeFrom != null && (e is RangeRejected || (e is RommHttpException && e.status == 416))
            if (!restart) throw e
            Files.deleteIfExists(partial)
            Files.deleteIfExists(validatorFile)
            streamRom(uri, partial, validatorFile, resumeFrom = null, validator = null)
        }
        withContext(Dispatchers.IO) {
            val size = Files.size(partial)
            val md5 = runInterruptible { md5File(partial) }
            if ((expectedSize != null && size != expectedSize) || (expectedMd5 != null && md5 != expectedMd5)) {
                Files.deleteIfExists(partial)
                Files.deleteIfExists(validatorFile)
                throw RommResponseException("The ROM RomM sent does not match its size or hash. Try again.")
            }
            forceToDisk(partial)
            moveIntoPlace(partial, target)
            writeVerifiedMarker(target, size, md5)
            Files.deleteIfExists(validatorFile)
        }
        return target
    }

    private suspend fun streamRom(
        uri: URI,
        partial: Path,
        validatorFile: Path,
        resumeFrom: Long?,
        validator: String?,
    ) {
        streamGet(uri, partial, range = resumeFrom, ifRange = validator) { status, headers ->
            when (status) {
                200 -> {
                    // A full body replaces the partial. Keep what lets a later try resume it.
                    val next = strongValidator(headers)
                    if (next == null) Files.deleteIfExists(validatorFile) else writeDurably(validatorFile, next.toByteArray())
                }
                206 -> {
                    val start = contentRangeStart(headers)
                    if (start == null || start != resumeFrom) throw RangeRejected()
                }
            }
        }
    }

    /**
     * Registers this install. A stored id whose [clientVersion] still matches
     * is returned without a request. Otherwise `PUT /api/devices/{id}`, and
     * `POST /api/devices` when that id is gone. The post sends `allow_existing`.
     * A 409 `device_exists` response is the existing device, not a failure.
     * Pass the device id from device-code sign-in as [stored] so this does
     * not create a second device.
     */
    suspend fun registerDevice(
        stored: RegisteredDevice?,
        name: String,
        clientVersion: String,
        hostname: String? = null,
        macAddress: String? = null,
    ): RegisteredDevice {
        if (stored != null && stored.clientVersion == clientVersion) return stored
        val body = deviceWriteBody(name, clientVersion, hostname, macAddress)
        if (stored != null) {
            val put = exchange(
                api("/devices/${encode(stored.deviceId)}"),
                "PUT",
                authenticated = true,
                body = body,
                contentType = "application/json",
            )
            when (put.status) {
                200 -> return stored.copy(clientVersion = clientVersion)
                404 -> Unit
                else -> throw rommHttp(put.status, put.detail())
            }
        }
        val post = exchange(
            api("/devices"),
            "POST",
            authenticated = true,
            body = deviceWriteBody(name, clientVersion, hostname, macAddress, allowExisting = true),
            contentType = "application/json",
        )
        if (post.status == 409) {
            val existing = deviceExistsId(post.body)
            if (existing != null) return RegisteredDevice(existing, clientVersion)
        }
        post.require(200, 201)
        return RegisteredDevice(parseObject(post.body).reqString("device_id"), clientVersion)
    }

    /**
     * Asks the server what to do with [saves].
     *
     * Refuses when the OpenAPI major is newer than [RommContract.TESTED_MAJOR].
     * Save sync needs 5.4.0 or newer; an older server throws
     * [RommUnsupportedServer] and no session is opened. Browse and download
     * do not use this gate.
     */
    suspend fun negotiate(
        deviceId: String,
        saves: List<LocalSave>,
        romIds: List<Long> = emptyList(),
        emulators: List<String>? = null,
    ): NegotiateResult {
        saves.forEach { save ->
            require(Files.isRegularFile(save.file)) { "save file is missing: ${save.fileName}" }
        }
        val info = openApiInfo()
        val parsed = RommVersion.parse(info.version) ?: throw RommProtocolUnreadable(info.version)
        if (parsed.major > RommContract.TESTED_MAJOR) {
            throw RommProtocolMismatch(info.version, RommContract.TESTED_MAJOR)
        }
        if (!parsed.isAtLeast(5, 4, 0)) {
            throw RommUnsupportedServer(
                info.version,
                "RomM server too old for save sync (${info.version}). Save sync needs 5.4.0 or newer.",
            )
        }
        val raw = exchange(
            api("/sync/negotiate"),
            "POST",
            authenticated = true,
            body = negotiateBody(deviceId, saves, romIds, emulators),
            contentType = "application/json",
        )
        raw.require(200)
        return parseNegotiate(raw.body, info.version)
    }

    suspend fun uploadSave(
        romId: Long,
        fileName: String,
        slot: String?,
        emulator: String?,
        deviceId: String,
        sessionId: Long?,
        contentHash: String,
        bytes: ByteArray,
    ): Long {
        val params = mutableListOf(
            "rom_id" to romId.toString(),
            "device_id" to deviceId,
            "content_hash" to contentHash,
            "overwrite" to "false",
        )
        if (slot != null) params += "slot" to slot
        if (emulator != null) params += "emulator" to emulator
        if (sessionId != null) params += "session_id" to sessionId.toString()
        val (contentType, body) = multipart(fileName, bytes)
        val raw = exchange(
            api("/saves", params),
            "POST",
            authenticated = true,
            bodyBytes = body,
            contentType = contentType,
        )
        if (raw.status == 409) throw RommSlotMoved(raw.detail())
        raw.require(200)
        return parseSaveId(raw.body)
    }

    /**
     * Writes save [saveId] to [destination]. The bytes land in a `.partial`
     * sibling first. They must match [expectedHash] when RomM sent one, and are
     * synced to storage before the rename that replaces [destination].
     * A mismatch leaves [destination] as it was.
     */
    suspend fun downloadSave(
        saveId: Long,
        deviceId: String,
        sessionId: Long,
        destination: Path,
        expectedHash: String? = null,
    ) {
        val uri = api(
            "/saves/$saveId/content",
            listOf(
                "device_id" to deviceId,
                "session_id" to sessionId.toString(),
                "optimistic" to "false",
            ),
        )
        val partial = partialOf(destination)
        streamGet(uri, partial, range = null)
        withContext(Dispatchers.IO) {
            if (expectedHash != null && md5File(partial) != expectedHash.lowercase()) {
                Files.deleteIfExists(partial)
                throw RommResponseException("The save RomM sent does not match its hash")
            }
            forceToDisk(partial)
            moveIntoPlace(partial, destination)
        }
    }

    suspend fun confirmSaveDownloaded(saveId: Long, deviceId: String, contentHash: String) {
        val raw = exchange(
            api("/saves/$saveId/downloaded"),
            "POST",
            authenticated = true,
            body = confirmBody(deviceId, contentHash),
            contentType = "application/json",
        )
        raw.require(200)
    }

    suspend fun completeSync(sessionId: Long, operationsCompleted: Int, operationsFailed: Int): CompletedSync {
        val raw = exchange(
            api("/sync/sessions/$sessionId/complete"),
            "POST",
            authenticated = true,
            body = completeBody(operationsCompleted, operationsFailed),
            contentType = "application/json",
        )
        raw.require(200)
        return parseCompleted(raw.body)
    }

    /**
     * One negotiate, then the operations, then complete. Call it before launch
     * and again after the player exits.
     *
     * [ledger] records what this device last agreed with RomM. RomM decides from
     * timestamps, and a device clock can be wrong, so a local save whose hash
     * differs from its ledger entry is treated as progress RomM has not seen:
     * - a download over it keeps both: the local bytes are uploaded first as an
     *   archive with a null slot and a unique name, then the server copy is written;
     * - a no-op that would leave it behind a different server copy uploads it.
     * A null [ledger] knows nothing, so every local save counts as unsynced.
     *
     * A replaced local save is copied to the slot's trash first. A download must
     * match the hash RomM sent before it replaces anything. An upload that cannot
     * reach the server stays unsynced in the ledger and is named in
     * [SaveSyncReport.queued]; the next sync retries it through a fresh
     * negotiate, so an old copy is never replayed over a newer one.
     *
     * Cancellation is not a network failure: it is rethrown and the session is
     * not completed. A failed negotiate throws and does not change local files.
     */
    suspend fun syncSaves(
        deviceId: String,
        saves: List<LocalSave>,
        romIds: List<Long> = emptyList(),
        emulators: List<String>? = null,
        destination: (SyncOperation) -> Path,
        ledger: SaveLedger? = null,
    ): SaveSyncReport {
        val negotiated = negotiate(deviceId, saves, romIds, emulators)
        val run = SyncRun(deviceId, negotiated.sessionId, ledger)

        for (op in negotiated.operations) {
            try {
                when (op.action) {
                    "no_op" -> {
                        val local = pairedLocal(saves, op)
                        val hash = local?.let { withContext(Dispatchers.IO) { md5File(it.file) } }
                        val serverHash = comparableServerHash(op)
                        if (local != null && hash != null && serverHash != null && hash != serverHash &&
                            run.unsynced(local, hash)
                        ) {
                            // RomM saw an older timestamp. These bytes changed since the last sync here.
                            try {
                                run.upload(local)
                            } catch (moved: RommSlotMoved) {
                                // The slot moved on too. Keep both, or the next negotiate says no_op again.
                                if (op.saveId == null) throw moved
                                run.keepBoth(local, op, destination(op))
                            }
                        } else {
                            if (local != null && hash != null && hash == serverHash) {
                                run.markSynced(local.slot, hash, op.emulator ?: local.emulator)
                            }
                            run.completed++
                        }
                    }
                    "upload" -> {
                        val local = localSave(saves, op)
                        val hash = withContext(Dispatchers.IO) { md5File(local.file) }
                        val serverHash = comparableServerHash(op)
                        when {
                            serverHash == hash -> {
                                run.markSynced(local.slot, hash, local.emulator)
                                run.completed++
                            }
                            // Nothing new here: these are the bytes last agreed with RomM. Sending them
                            // would put an older version back on top, so take the server copy instead.
                            serverHash != null && op.saveId != null && !run.unsynced(local, hash) -> {
                                run.download(op, destination(op))
                                run.completed++
                            }
                            else -> run.upload(local)
                        }
                    }
                    "download" -> {
                        val local = pairedLocal(saves, op)
                        if (local != null && Files.isRegularFile(local.file) &&
                            run.unsynced(local, withContext(Dispatchers.IO) { md5File(local.file) })
                        ) {
                            run.keepBoth(local, op, destination(op))
                        } else {
                            run.download(op, destination(op))
                            run.completed++
                        }
                    }
                    "delete" -> {
                        val local = saves.find {
                            it.romId == op.romId &&
                                it.slot == op.slot &&
                                it.fileName == op.fileName &&
                                it.emulator == op.emulator
                        }
                        // Progress RomM never saw is kept on the server before the local file goes.
                        if (local != null && Files.isRegularFile(local.file) &&
                            run.unsynced(local, withContext(Dispatchers.IO) { md5File(local.file) }) &&
                            !run.archive(local)
                        ) {
                            continue
                        }
                        if (local != null && withContext(Dispatchers.IO) { moveSaveToTrash(local.file) }) {
                            run.deleted.add(local.file)
                            local.slot?.let { slot -> ledger?.update(slot) { null } }
                        }
                        run.completed++
                    }
                    "conflict" -> {
                        val local = pairedLocal(saves, op)
                        if (local == null || !Files.isRegularFile(local.file)) {
                            run.download(op, destination(op))
                            run.completed++
                        } else {
                            run.keepBoth(local, op, destination(op))
                        }
                    }
                    else -> {
                        run.failed++
                        run.failures += "unknown action ${op.action}"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RommException) {
                run.failed++
                run.failures += e.message ?: op.action
            } catch (e: IOException) {
                // A local file could not be read or written. That operation waits for the next sync.
                run.failed++
                run.failures += e.message ?: op.action
            }
        }

        val sessionClosed = try {
            completeSync(negotiated.sessionId, run.completed, run.failed)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: RommUnavailable) {
            false
        } catch (_: RommHttpException) {
            false
        }

        return SaveSyncReport(
            sessionId = negotiated.sessionId,
            serverVersion = negotiated.serverVersion,
            downloaded = run.downloaded,
            uploadedSaveIds = run.uploaded,
            keptBoth = run.keptBoth,
            deleted = run.deleted,
            queued = run.queued,
            failed = run.failures,
            completedSession = sessionClosed,
        )
    }

    override fun close() {
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    /** State and steps for one [syncSaves] call. */
    private inner class SyncRun(
        val deviceId: String,
        val sessionId: Long,
        val ledger: SaveLedger?,
    ) {
        var completed = 0
        var failed = 0
        val downloaded = mutableListOf<Path>()
        val uploaded = mutableListOf<Long>()
        val keptBoth = mutableListOf<KeptBoth>()
        val archived = mutableListOf<Long>()
        val deleted = mutableListOf<Path>()
        val queued = mutableListOf<String>()
        val failures = mutableListOf<String>()

        fun unsynced(local: LocalSave, hash: String): Boolean {
            val slot = local.slot ?: return true
            return ledger?.unsynced(slot, hash) ?: true
        }

        fun markSynced(slot: String?, hash: String, emulator: String?) {
            if (slot != null) ledger?.markSynced(slot, hash, emulator)
        }

        /** Uploads [local] into its slot. Counts the result. */
        suspend fun upload(local: LocalSave) {
            val bytes = withContext(Dispatchers.IO) { Files.readAllBytes(local.file) }
            val hash = md5Hex(bytes)
            val id = sendOrHold(local, bytes, hash, local.slot) ?: return
            uploaded += id
            markSynced(local.slot, hash, local.emulator)
            completed++
        }

        /**
         * Uploads [local] as an archive unless these bytes already are one, then
         * writes the server copy over it. Counts the result.
         */
        suspend fun keepBoth(local: LocalSave, op: SyncOperation, path: Path) {
            val saveId = op.saveId ?: throw RommResponseException("${op.action} is missing save_id")
            val before = archived.size
            if (!archive(local)) return
            download(op, path)
            keptBoth += KeptBoth(op.romId, op.slot, archived.drop(before).firstOrNull(), saveId, path)
            completed++
        }

        /**
         * Uploads [local] as an archive with a null slot and a unique name, unless
         * these bytes already are one. False when RomM could not be reached; the
         * save is then named in [queued] and the caller must not replace it.
         */
        suspend fun archive(local: LocalSave): Boolean {
            val bytes = withContext(Dispatchers.IO) { Files.readAllBytes(local.file) }
            val hash = md5Hex(bytes)
            if (local.slot != null && ledger?.slot(local.slot)?.archivedHash == hash) return true
            val copy = local.copy(fileName = archiveFileName(local.fileName, archiveStamp()), slot = null)
            val id = sendOrHold(copy, bytes, hash, slot = null, name = local.fileName) ?: return false
            archived += id
            local.slot?.let { slot -> ledger?.update(slot) { it.copy(archivedHash = hash) } }
            return true
        }

        /**
         * Writes the server copy to [path]. What [path] held is copied to the
         * slot's trash first. The ledger moves before the confirm, so a lost
         * confirm cannot make the new bytes look unsynced.
         */
        suspend fun download(op: SyncOperation, path: Path) {
            val saveId = op.saveId ?: throw RommResponseException("${op.action} is missing save_id")
            val expected = comparableServerHash(op)
            withContext(Dispatchers.IO) {
                if (Files.isRegularFile(path) && md5File(path) != expected) copySaveToTrash(path)
            }
            downloadSave(saveId, deviceId, sessionId, path, expected)
            val hash = withContext(Dispatchers.IO) { md5File(path) }
            markSynced(op.slot, hash, op.emulator)
            downloaded.add(path)
            confirmSaveDownloaded(saveId, deviceId, hash)
        }

        /**
         * Sends one upload. Null when RomM could not be reached: the save stays
         * unsynced for the next negotiate and is named in [queued].
         */
        private suspend fun sendOrHold(
            local: LocalSave,
            bytes: ByteArray,
            hash: String,
            slot: String?,
            name: String = local.fileName,
        ): Long? =
            try {
                uploadSave(
                    romId = local.romId,
                    fileName = local.fileName,
                    slot = slot,
                    emulator = local.emulator,
                    deviceId = deviceId,
                    sessionId = sessionId,
                    contentHash = hash,
                    bytes = bytes,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: RommUnavailable) {
                queued += name
                failures += e.message ?: "upload failed"
                failed++
                null
            }
    }

    /**
     * RomM's hash of the server save when it is an MD5 of the file's bytes.
     * RomM hashes an archive by its members, so an archive's hash cannot be
     * compared with the file and is left unchecked.
     */
    private fun comparableServerHash(op: SyncOperation): String? =
        op.serverContentHash?.lowercase()?.takeUnless { isArchiveName(op.fileName) }

    private fun pairedLocal(saves: List<LocalSave>, op: SyncOperation): LocalSave? =
        saves.find { it.romId == op.romId && it.slot == op.slot && it.fileName == op.fileName }
            ?: saves.find { it.romId == op.romId && it.slot == op.slot }

    private fun localSave(saves: List<LocalSave>, op: SyncOperation): LocalSave =
        pairedLocal(saves, op)
            ?: throw RommResponseException("negotiate asked for ${op.fileName} and it was not local")

    /**
     * One GET into [partial]. [onHeaders] sees the status and headers before
     * any body byte is written and may throw to refuse them.
     */
    private suspend fun streamGet(
        uri: URI,
        partial: Path,
        range: Long?,
        ifRange: String? = null,
        onHeaders: ((Int, Map<String, List<String>>) -> Unit)? = null,
    ) {
        val headers = mutableMapOf<String, String>()
        if (range != null) headers["Range"] = "bytes=$range-"
        if (range != null && ifRange != null) headers["If-Range"] = ifRange
        val raw = exchange(
            uri,
            "GET",
            authenticated = true,
            extraHeaders = headers,
            streamTo = partial,
            append = range != null,
            onHeaders = onHeaders,
        )
        if (raw.status == 202) {
            throw RommConversionPending(raw.header("Retry-After")?.toLongOrNull())
        }
        if (raw.status == 206 && range == null) {
            throw RommResponseException("server returned 206 without a partial file")
        }
        raw.require(200, 206)
    }

    private fun api(path: String, query: List<Pair<String, String>> = emptyList()): URI {
        val q = if (query.isEmpty()) {
            ""
        } else {
            query.joinToString("&", prefix = "?") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }
        }
        return URI.create("$origin/api$path$q")
    }

    private suspend fun exchange(
        uri: URI,
        method: String,
        authenticated: Boolean,
        body: String? = null,
        bodyBytes: ByteArray? = null,
        contentType: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        streamTo: Path? = null,
        append: Boolean = false,
        onHeaders: ((Int, Map<String, List<String>>) -> Unit)? = null,
    ): RawResponse = withContext(Dispatchers.IO) {
        val token = bearer()
        if (authenticated && token == null) throw RommUnauthenticated()
        val requestBody = when {
            bodyBytes != null -> bodyBytes.toRequestBody(contentType?.toMediaType())
            body != null -> body.toRequestBody((contentType ?: "application/json").toMediaType())
            method == "GET" || method == "HEAD" -> null
            else -> ByteArray(0).toRequestBody(contentType?.toMediaType())
        }
        val builder = Request.Builder().url(uri.toString())
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody ?: ByteArray(0).toRequestBody(null))
            "PUT" -> builder.put(requestBody ?: ByteArray(0).toRequestBody(null))
            else -> builder.method(method, requestBody)
        }
        if (token != null && authenticated) builder.header("Authorization", "Bearer $token")
        extraHeaders.forEach { (key, value) -> builder.header(key, value) }
        val call = http.newCall(builder.build())
        // Stays suspended for the header wait and the body copy. Cancelling
        // this exchange cancels the OkHttp call, which unblocks a read.
        coroutineScope {
            val watching = launch(start = CoroutineStart.UNDISPATCHED) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    continuation.invokeOnCancellation { call.cancel() }
                }
            }
            try {
                val response = try {
                    call.await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    if (call.isCanceled()) throw CancellationException("RomM request was cancelled", e)
                    throw RommUnavailable(e.message ?: "RomM is unreachable", e)
                }
                response.use { open ->
                    val headers = open.headers.names().associateWith { open.headers.values(it) }
                    val status = open.code
                    try {
                        onHeaders?.invoke(status, headers)
                        if (streamTo != null && (status == 200 || status == 206) && !(status == 206 && !append)) {
                            val input = open.body?.byteStream()
                                ?: throw RommResponseException("RomM returned an empty body")
                            input.use { stream ->
                                streamTo.parent?.let { Files.createDirectories(it) }
                                val options = if (append && status == 206) {
                                    arrayOf(
                                        StandardOpenOption.CREATE,
                                        StandardOpenOption.WRITE,
                                        StandardOpenOption.APPEND,
                                    )
                                } else {
                                    arrayOf(
                                        StandardOpenOption.CREATE,
                                        StandardOpenOption.WRITE,
                                        StandardOpenOption.TRUNCATE_EXISTING,
                                    )
                                }
                                FileChannel.open(streamTo, *options).use { channel ->
                                    copyUntilCanceled(call, stream, Channels.newOutputStream(channel))
                                    channel.force(true)
                                }
                            }
                        }
                        val bytes = if (streamTo != null) {
                            ByteArray(0)
                        } else {
                            open.body?.bytes() ?: ByteArray(0)
                        }
                        RawResponse(status, headers, bytes)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        if (call.isCanceled()) throw CancellationException("RomM request was cancelled", e)
                        throw RommUnavailable(e.message ?: "RomM is unreachable", e)
                    }
                }
            } finally {
                watching.cancel()
            }
        }
    }

    private fun copyUntilCanceled(call: Call, input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = try {
                input.read(buffer)
            } catch (e: IOException) {
                if (call.isCanceled()) throw CancellationException("RomM request was cancelled", e)
                throw e
            }
            if (read < 0) return
            if (call.isCanceled()) throw CancellationException("RomM request was cancelled")
            output.write(buffer, 0, read)
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, _, _ -> response.close() }
            }
        })
    }

    private fun bearer(): String? =
        accessToken()?.trim()?.removePrefix("Bearer ")?.trim()?.ifEmpty { null }

    private class RawResponse(
        val status: Int,
        val headers: Map<String, List<String>>,
        val body: ByteArray,
    ) {
        fun header(name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

        fun detail(): String = detailText(body)

        fun require(vararg codes: Int) {
            if (status !in codes) throw rommHttp(status, detail())
        }
    }

    companion object {
        /** A cover is well under this. A larger body is not an image the hero needs. */
        const val ARTWORK_MAX_BYTES: Int = 8 * 1024 * 1024

        /** The origin as this client uses it: scheme and host lowercased, no trailing `/` or `/api`. */
        fun normalizeOrigin(raw: String): String {
            var text = raw.trim().trimEnd('/')
            text = stripTrailingApi(text)
            val marker = "://"
            val split = text.indexOf(marker)
            require(split > 0) { "RomM origin must be an http(s) URL" }
            val scheme = text.substring(0, split).lowercase()
            require(scheme == "http" || scheme == "https") { "RomM origin must be an http(s) URL" }
            val rest = text.substring(split + marker.length)
            require(rest.isNotEmpty() && !rest.startsWith("/")) { "RomM origin must be an http(s) URL" }
            val cut = rest.indexOfAny(charArrayOf('/', '?', '#'))
            val authority = if (cut < 0) rest else rest.substring(0, cut)
            val tail = if (cut < 0) "" else rest.substring(cut)
            require(!authority.contains('@')) { "RomM origin must not include a username or password" }
            val (host, port) = splitHost(authority)
            require(host.isNotEmpty() && host != "[]") { "RomM origin must be an http(s) URL" }
            if (scheme == "http") {
                val bare = if (host.startsWith("[") && host.endsWith("]")) {
                    host.substring(1, host.length - 1)
                } else {
                    host
                }
                require(isLanHost(bare)) { "Cleartext RomM origins are only for a LAN address" }
            }
            return scheme + marker + host + port + tail
        }

        /** Drops a trailing `/api` in any case. `https://host/API` becomes `https://host`. */
        private fun stripTrailingApi(text: String): String {
            val suffix = "/api"
            if (text.length < suffix.length) return text
            val start = text.length - suffix.length
            if (!text.regionMatches(start, suffix, 0, suffix.length, ignoreCase = true)) return text
            return text.dropLast(suffix.length).trimEnd('/')
        }

        private fun splitHost(hostPort: String): Pair<String, String> {
            if (hostPort.startsWith("[")) {
                val end = hostPort.indexOf(']')
                require(end > 1) { "RomM origin must be an http(s) URL" }
                val port = hostPort.substring(end + 1)
                require(port.isEmpty() || port.startsWith(":")) { "RomM origin must be an http(s) URL" }
                return hostPort.substring(0, end + 1).lowercase() to port
            }
            val colon = hostPort.lastIndexOf(':')
            if (colon > 0 && hostPort.substring(colon + 1).all { it.isDigit() }) {
                return hostPort.substring(0, colon).lowercase() to hostPort.substring(colon)
            }
            return hostPort.lowercase() to ""
        }

        /** File [downloadRom] writes. A `.partial` sibling means the download is not finished. */
        fun romCacheFile(root: Path, romId: Long, fileName: String, fileIds: List<Long>): Path {
            val safe = safeFileName(fileName)
            val dir = if (fileIds.isEmpty()) {
                root.resolve("roms").resolve(romId.toString())
            } else {
                root.resolve("roms").resolve(romId.toString()).resolve("f" + fileIds.joinToString("-"))
            }
            return dir.resolve(safe)
        }

        private fun encode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

        private fun multipart(fileName: String, bytes: ByteArray): Pair<String, ByteArray> {
            val boundary = "foldcade-${UUID.randomUUID()}"
            val header = buildString {
                append("--").append(boundary).append("\r\n")
                append("Content-Disposition: form-data; name=\"saveFile\"; filename=\"")
                append(fileName.replace("\"", "")).append("\"\r\n")
                append("Content-Type: application/octet-stream\r\n\r\n")
            }.toByteArray(Charsets.UTF_8)
            val ending = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
            val body = ByteArray(header.size + bytes.size + ending.size)
            System.arraycopy(header, 0, body, 0, header.size)
            System.arraycopy(bytes, 0, body, header.size, bytes.size)
            System.arraycopy(ending, 0, body, header.size + bytes.size, ending.size)
            return "multipart/form-data; boundary=$boundary" to body
        }

        /**
         * True when [target] is a finished download whose verified marker matches
         * [expectedSize] and [expectedMd5]. A null expectation is not checked.
         * Reads no ROM bytes, so it is cheap enough for a launch with no network.
         */
        fun isVerifiedRom(target: Path, expectedSize: Long?, expectedMd5: String?): Boolean {
            if (!Files.isRegularFile(target) || Files.exists(partialOf(target))) return false
            val size = Files.size(target)
            if (expectedSize != null && size != expectedSize) return false
            val marker = try {
                parseObject(Files.readAllBytes(markerOf(target)))
            } catch (_: Exception) {
                return false
            }
            if (marker.optLong("size") != size) return false
            val md5 = marker.optString("md5") ?: return false
            return expectedMd5 == null || md5.equals(expectedMd5, ignoreCase = true)
        }

        private fun writeVerifiedMarker(target: Path, size: Long, md5: String) {
            writeDurably(markerOf(target), """{"size":$size,"md5":"$md5"}""".toByteArray())
        }

        private fun markerOf(target: Path): Path = target.resolveSibling(target.fileName.toString() + MARKER_SUFFIX)

        /** RomM hashes these by the files inside them, so their MD5 is not RomM's hash. */
        fun isArchiveName(fileName: String): Boolean {
            val name = fileName.lowercase()
            return ARCHIVE_SUFFIXES.any { name.endsWith(it) }
        }

        private val ARCHIVE_SUFFIXES = listOf(
            ".zip", ".7z", ".rar", ".tar", ".tar.gz", ".tgz", ".tar.xz", ".txz", ".tar.bz2", ".tbz2",
        )

        internal fun partialOf(target: Path): Path = target.resolveSibling(target.fileName.toString() + ".partial")

        private fun readValidator(file: Path): String? = try {
            String(Files.readAllBytes(file), Charsets.UTF_8).trim().ifEmpty { null }
        } catch (_: IOException) {
            null
        }

        /** A strong `ETag`, else `Last-Modified`. A weak `ETag` cannot guard `If-Range`. */
        private fun strongValidator(headers: Map<String, List<String>>): String? {
            val etag = headerValue(headers, "ETag")
            if (etag != null && !etag.startsWith("W/")) return etag
            return headerValue(headers, "Last-Modified")
        }

        /** Start of `Content-Range: bytes <start>-<end>/<total>`. */
        private fun contentRangeStart(headers: Map<String, List<String>>): Long? {
            val value = headerValue(headers, "Content-Range") ?: return null
            return value.trim().removePrefix("bytes").trim().substringBefore('-').toLongOrNull()
        }

        private fun headerValue(headers: Map<String, List<String>>, name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()?.trim()

        private val romLocks = ConcurrentHashMap<Path, Mutex>()

        private fun romLock(target: Path): Mutex =
            romLocks.computeIfAbsent(target.toAbsolutePath().normalize()) { Mutex() }

        private const val MARKER_SUFFIX = ".verified"
        private const val VALIDATOR_SUFFIX = ".partial.validator"
    }
}

/** A 206 that does not continue the partial on disk. The download starts over. */
private class RangeRejected : RommException("RomM resumed the download at the wrong offset")
