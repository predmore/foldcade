package app.foldcade.romm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
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
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
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
 * This is the app's only HTTP stack. Cleartext is an explicit LAN opt-in:
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

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeout)
        .readTimeout(readTimeout)
        .writeTimeout(writeTimeout)
        .followRedirects(true)
        .followSslRedirects(false)
        .addNetworkInterceptor(RommCleartextInterceptor(origin))
        .apply {
            if (httpLog != null) addInterceptor(RedactingLoggingInterceptor(httpLog))
        }
        .build()

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
     * A finished file is reused. A `.partial` sibling is resumed with `Range`.
     */
    suspend fun downloadRom(
        romId: Long,
        fileName: String,
        cacheRoot: Path,
        fileIds: List<Long> = emptyList(),
        expectedSize: Long? = null,
    ): Path {
        require(romId >= 1)
        val target = romCacheFile(cacheRoot, romId, fileName, fileIds)
        val partial = target.resolveSibling(target.fileName.toString() + ".partial")
        if (Files.isRegularFile(target) && !Files.exists(partial)) {
            if (expectedSize == null || Files.size(target) == expectedSize) return target
            Files.delete(target)
        }
        val params = mutableListOf("purpose" to "play")
        if (fileIds.isNotEmpty()) params += "file_ids" to fileIds.joinToString(",")
        val uri = api("/roms/$romId/content/${encode(safeFileName(fileName))}", params)
        val append = Files.isRegularFile(partial) && Files.size(partial) > 0
        try {
            streamGet(uri, partial, range = if (append) Files.size(partial) else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RommHttpException) {
            if (e.status == 416 && append) {
                Files.deleteIfExists(partial)
                streamGet(uri, partial, range = null)
            } else {
                throw e
            }
        }
        moveIntoPlace(partial, target)
        return target
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

    suspend fun downloadSave(
        saveId: Long,
        deviceId: String,
        sessionId: Long,
        destination: Path,
    ) {
        val uri = api(
            "/saves/$saveId/content",
            listOf(
                "device_id" to deviceId,
                "session_id" to sessionId.toString(),
                "optimistic" to "false",
            ),
        )
        val partial = destination.resolveSibling(destination.fileName.toString() + ".partial")
        streamGet(uri, partial, range = null)
        moveIntoPlace(partial, destination)
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
     * and again after the player exits. A conflict archives the local bytes
     * under a unique file name with a null slot, then writes the server copy.
     * The name has to be unique: RomM stores a slot-less save by file name.
     * An upload that cannot reach the server is copied into [queue] when one
     * is given. Cancellation is not a network failure: it is rethrown, nothing
     * is queued, and the session is not completed. A failed negotiate throws
     * and does not change local files.
     */
    suspend fun syncSaves(
        deviceId: String,
        saves: List<LocalSave>,
        romIds: List<Long> = emptyList(),
        emulators: List<String>? = null,
        destination: (SyncOperation) -> Path,
        queue: SaveUploadQueue? = null,
    ): SaveSyncReport {
        val negotiated = negotiate(deviceId, saves, romIds, emulators)
        var completed = 0
        var failed = 0
        val downloaded = mutableListOf<Path>()
        val uploaded = mutableListOf<Long>()
        val keptBoth = mutableListOf<KeptBoth>()
        val deleted = mutableListOf<Path>()
        val queued = mutableListOf<String>()
        val failures = mutableListOf<String>()

        for (op in negotiated.operations) {
            try {
                when (op.action) {
                    "no_op" -> completed++
                    "upload" -> {
                        val local = localSave(saves, op)
                        val id = uploadOrQueue(local, deviceId, negotiated.sessionId, queue, queued, failures)
                        if (id == null) {
                            failed++
                        } else {
                            uploaded += id
                            completed++
                        }
                    }
                    "download" -> {
                        val saveId = op.saveId
                            ?: throw RommResponseException("download is missing save_id")
                        val path = destination(op)
                        downloadSave(saveId, deviceId, negotiated.sessionId, path)
                        confirmSaveDownloaded(saveId, deviceId, md5Hex(Files.readAllBytes(path)))
                        downloaded.add(path)
                        completed++
                    }
                    "delete" -> {
                        val local = saves.find {
                            it.romId == op.romId &&
                                it.slot == op.slot &&
                                it.fileName == op.fileName &&
                                it.emulator == op.emulator
                        }
                        if (local != null && moveSaveToTrash(local.file)) deleted.add(local.file)
                        completed++
                    }
                    "conflict" -> {
                        val local = localSave(saves, op)
                        val saveId = op.saveId
                            ?: throw RommResponseException("conflict is missing save_id")
                        val archivedLocal = local.copy(
                            fileName = archiveFileName(local.fileName, archiveStamp()),
                            slot = null,
                        )
                        val archived = uploadOrQueue(
                            archivedLocal,
                            deviceId,
                            negotiated.sessionId,
                            queue,
                            queued,
                            failures,
                            slotOverride = null,
                        )
                        if (archived == null) {
                            failed++
                            continue
                        }
                        val path = destination(op)
                        downloadSave(saveId, deviceId, negotiated.sessionId, path)
                        confirmSaveDownloaded(saveId, deviceId, md5Hex(Files.readAllBytes(path)))
                        keptBoth += KeptBoth(op.romId, op.slot, archived, saveId, path)
                        downloaded.add(path)
                        completed++
                    }
                    else -> {
                        failed++
                        failures += "unknown action ${op.action}"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RommException) {
                failed++
                failures += e.message ?: op.action
            }
        }

        val sessionClosed = try {
            completeSync(negotiated.sessionId, completed, failed)
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
            downloaded = downloaded,
            uploadedSaveIds = uploaded,
            keptBoth = keptBoth,
            deleted = deleted,
            queued = queued,
            failed = failures,
            completedSession = sessionClosed,
        )
    }

    override fun close() {
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    private suspend fun uploadOrQueue(
        local: LocalSave,
        deviceId: String,
        sessionId: Long,
        queue: SaveUploadQueue?,
        queued: MutableList<String>,
        failures: MutableList<String>,
        slotOverride: String? = local.slot,
    ): Long? {
        val bytes = Files.readAllBytes(local.file)
        val hash = md5Hex(bytes)
        return try {
            uploadSave(
                romId = local.romId,
                fileName = local.fileName,
                slot = slotOverride,
                emulator = local.emulator,
                deviceId = deviceId,
                sessionId = sessionId,
                contentHash = hash,
                bytes = bytes,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: RommUnavailable) {
            if (queue != null) {
                queue.enqueue(local, deviceId, hash, bytes, slotOverride)
                queued += local.fileName
            }
            failures += e.message ?: "upload failed"
            null
        }
    }

    private fun localSave(saves: List<LocalSave>, op: SyncOperation): LocalSave =
        saves.find { it.romId == op.romId && it.slot == op.slot && it.fileName == op.fileName }
            ?: saves.find { it.romId == op.romId && it.slot == op.slot }
            ?: throw RommResponseException("negotiate asked for ${op.fileName} and it was not local")

    private suspend fun streamGet(uri: URI, partial: Path, range: Long?) {
        val headers = mutableMapOf<String, String>()
        if (range != null) headers["Range"] = "bytes=$range-"
        val raw = exchange(
            uri,
            "GET",
            authenticated = true,
            extraHeaders = headers,
            streamTo = partial,
            append = range != null,
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
                                Files.newOutputStream(streamTo, *options).use { out ->
                                    copyUntilCanceled(call, stream, out)
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

        internal fun normalizeOrigin(raw: String): String {
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
         * Moves [file] into a `.trash` directory beside it.
         * The name is unique so a second delete does not replace the first.
         * Returns false when [file] is already gone.
         */
        internal fun moveSaveToTrash(file: Path): Boolean {
            if (!Files.exists(file)) return false
            val parent = file.parent
            if (parent == null) return Files.deleteIfExists(file)
            val trash = parent.resolve(".trash")
            Files.createDirectories(trash)
            val dest = trash.resolve(trashedName(file.fileName.toString()))
            try {
                Files.move(file, dest, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(file, dest)
            }
            return true
        }

        private fun trashedName(fileName: String): String {
            val stamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now())
            val unique = UUID.randomUUID().toString().substring(0, 8)
            return "$stamp-$unique-$fileName"
        }

        private fun moveIntoPlace(partial: Path, target: Path) {
            Files.createDirectories(target.parent)
            try {
                Files.move(
                    partial,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
