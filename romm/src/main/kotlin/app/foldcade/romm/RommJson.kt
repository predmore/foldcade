package app.foldcade.romm

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit

internal fun md5Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("MD5").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
}

internal fun instantText(instant: Instant): String =
    instant.truncatedTo(ChronoUnit.SECONDS).toString()

internal fun parseElement(text: String): JsonElement = try {
    kotlinx.serialization.json.Json.parseToJsonElement(text)
} catch (e: Exception) {
    throw RommResponseException("RomM returned JSON this client could not read")
}

internal fun parseObject(bytes: ByteArray): JsonObject {
    val element = parseElement(bytes.toString(Charsets.UTF_8))
    if (element !is JsonObject) throw RommResponseException("RomM returned a JSON value that is not an object")
    return element
}

internal fun JsonObject.reqString(name: String): String {
    val value = this[name] ?: throw RommResponseException("response missing $name")
    if (value is JsonNull) throw RommResponseException("response $name is null")
    return value.jsonPrimitive.content
}

internal fun JsonObject.optString(name: String): String? {
    val value = this[name] ?: return null
    if (value is JsonNull) return null
    return value.jsonPrimitive.contentOrNull
}

internal fun JsonObject.reqLong(name: String): Long {
    val value = this[name] ?: throw RommResponseException("response missing $name")
    return value.jsonPrimitive.long
}

internal fun JsonObject.optLong(name: String): Long? {
    val value = this[name] ?: return null
    if (value is JsonNull) return null
    return value.jsonPrimitive.longOrNull
}

internal fun JsonObject.reqInt(name: String): Int = reqLong(name).toInt()

internal fun JsonObject.reqBool(name: String): Boolean {
    val value = this[name] ?: throw RommResponseException("response missing $name")
    return value.jsonPrimitive.boolean
}

internal fun JsonObject.stringList(name: String): List<String> {
    val value = this[name] ?: throw RommResponseException("response missing $name")
    if (value is JsonNull) return emptyList()
    return value.jsonArray.map { it.jsonPrimitive.content }
}

internal fun detailText(bytes: ByteArray): String {
    val text = bytes.toString(Charsets.UTF_8).trim()
    if (text.isEmpty()) return ""
    val element = try {
        parseElement(text)
    } catch (_: RommResponseException) {
        return text
    }
    if (element !is JsonObject) return text
    return when (val detail = element["detail"]) {
        null, is JsonNull -> text
        is JsonPrimitive -> detail.content
        is JsonObject -> detail.optString("message") ?: detail.optString("error") ?: detail.toString()
        else -> detail.toString()
    }
}

internal fun deviceAuthBody(
    clientDeviceIdentifier: String,
    name: String,
    clientVersion: String,
): String = buildJsonObject {
    put("client_device_identifier", clientDeviceIdentifier)
    put("name", name)
    put("client", RommContract.CLIENT_SLUG)
    put("platform", RommContract.PLATFORM)
    put("client_version", clientVersion)
    put(
        "requested_scopes",
        JsonArray(RommContract.DEVICE_AUTH_SCOPES.map { JsonPrimitive(it) }),
    )
}.toString()

internal fun deviceTokenBody(deviceCode: String): String = buildJsonObject {
    put("device_code", deviceCode)
}.toString()

internal fun deviceWriteBody(
    name: String,
    clientVersion: String,
    hostname: String?,
    macAddress: String?,
): String = buildJsonObject {
    put("name", name)
    put("platform", RommContract.PLATFORM)
    put("client", RommContract.CLIENT_SLUG)
    put("client_version", clientVersion)
    put("sync_mode", RommContract.SYNC_MODE)
    if (hostname != null) put("hostname", hostname)
    if (macAddress != null) put("mac_address", macAddress)
}.toString()

internal fun negotiateBody(
    deviceId: String,
    saves: List<LocalSave>,
    romIds: List<Long>,
    emulators: List<String>?,
): String {
    val scope = romIds.ifEmpty { saves.map { it.romId }.distinct() }
    return buildJsonObject {
        put("device_id", deviceId)
        put(
            "saves",
            buildJsonArray {
                saves.forEach { save ->
                    val bytes = java.nio.file.Files.readAllBytes(save.file)
                    add(
                        buildJsonObject {
                            put("rom_id", save.romId)
                            put("file_name", save.fileName)
                            if (save.slot == null) put("slot", JsonNull) else put("slot", save.slot)
                            if (save.emulator == null) put("emulator", JsonNull) else put("emulator", save.emulator)
                            put("content_hash", md5Hex(bytes))
                            put("updated_at", instantText(save.updatedAt))
                            put("file_size_bytes", bytes.size.toLong())
                        },
                    )
                }
            },
        )
        put("rom_ids", JsonArray(scope.map { JsonPrimitive(it) }))
        put("restore_unlisted", false)
        if (emulators == null) {
            put("emulators", JsonNull)
        } else {
            put("emulators", JsonArray(emulators.map { JsonPrimitive(it) }))
        }
    }.toString()
}

internal fun completeBody(operationsCompleted: Int, operationsFailed: Int): String = buildJsonObject {
    put("operations_completed", operationsCompleted)
    put("operations_failed", operationsFailed)
}.toString()

internal fun confirmBody(deviceId: String, contentHash: String): String = buildJsonObject {
    put("device_id", deviceId)
    put("content_hash", contentHash)
}.toString()

internal fun parseHeartbeat(bytes: ByteArray): Heartbeat {
    val system = parseObject(bytes)["SYSTEM"]?.jsonObject
        ?: throw RommResponseException("heartbeat missing SYSTEM")
    return Heartbeat(system.reqString("VERSION"))
}

internal fun parseChallenge(bytes: ByteArray): DeviceAuthChallenge {
    val obj = parseObject(bytes)
    return DeviceAuthChallenge(
        deviceCode = obj.reqString("device_code"),
        userCode = obj.reqString("user_code"),
        verificationPath = obj.reqString("verification_path"),
        verificationPathComplete = obj.reqString("verification_path_complete"),
        expiresInSeconds = obj.reqInt("expires_in"),
        intervalSeconds = obj.reqInt("interval"),
    )
}

internal fun parseApproved(bytes: ByteArray): DeviceTokenPoll.Approved {
    val obj = parseObject(bytes)
    return DeviceTokenPoll.Approved(
        accessToken = obj.reqString("access_token"),
        deviceId = obj.reqString("device_id"),
        scopes = obj.stringList("scopes"),
        expiresAt = obj.optString("expires_at"),
    )
}

internal fun parsePlatform(obj: JsonObject): PlatformSummary {
    val slug = obj.reqString("slug")
    return PlatformSummary(
        id = obj.reqLong("id"),
        name = obj.reqString("name"),
        displayName = obj.optString("display_name") ?: obj.reqString("name"),
        slug = slug,
        fsSlug = obj.reqString("fs_slug"),
        abbreviation = obj.optString("abbreviation") ?: slug,
        alternativeNames = obj.optionalStringList("alternative_names"),
        romCount = obj.reqLong("rom_count"),
    )
}

/** 5.3.1 platform objects omit this. A missing or null value is an empty list. */
internal fun JsonObject.optionalStringList(name: String): List<String> {
    val value = this[name] ?: return emptyList()
    if (value is JsonNull) return emptyList()
    return value.jsonArray.map { it.jsonPrimitive.content }
}

internal fun parseRomFile(obj: JsonObject): RomFileSummary = RomFileSummary(
    id = obj.reqLong("id"),
    fileName = obj.reqString("file_name"),
    fileSizeBytes = obj.reqLong("file_size_bytes"),
)

internal fun parseRom(obj: JsonObject): RomSummary = RomSummary(
    id = obj.reqLong("id"),
    name = obj.optString("name"),
    fsName = obj.reqString("fs_name"),
    fsExtension = obj.reqString("fs_extension"),
    fsSizeBytes = obj.reqLong("fs_size_bytes"),
    platformId = obj.reqLong("platform_id"),
    platformSlug = obj.reqString("platform_slug"),
    platformFsSlug = obj.reqString("platform_fs_slug"),
    platformDisplayName = obj.reqString("platform_display_name"),
    alternativeNames = obj.stringList("alternative_names"),
    pathCoverSmall = obj.optString("path_cover_small"),
    pathCoverLarge = obj.optString("path_cover_large"),
    urlCover = obj.optString("url_cover"),
    hasSimpleSingleFile = obj.reqBool("has_simple_single_file"),
    hasNestedSingleFile = obj.reqBool("has_nested_single_file"),
    hasMultipleFiles = obj.reqBool("has_multiple_files"),
    files = obj["files"]?.jsonArray?.map { parseRomFile(it.jsonObject) } ?: emptyList(),
)

internal fun parseRomPage(bytes: ByteArray): RomPage {
    val obj = parseObject(bytes)
    val items = obj["items"]?.jsonArray ?: throw RommResponseException("response missing items")
    return RomPage(
        items = items.map { parseRom(it.jsonObject) },
        total = obj.optLong("total"),
        limit = obj.reqInt("limit"),
        offset = obj.reqInt("offset"),
    )
}

internal fun parseOperation(obj: JsonObject): SyncOperation = SyncOperation(
    action = obj.reqString("action"),
    romId = obj.reqLong("rom_id"),
    saveId = obj.optLong("save_id"),
    fileName = obj.reqString("file_name"),
    slot = obj.optString("slot"),
    emulator = obj.optString("emulator"),
    reason = obj.reqString("reason"),
    serverUpdatedAt = obj.optString("server_updated_at"),
    serverContentHash = obj.optString("server_content_hash"),
)

internal fun parseNegotiate(bytes: ByteArray, serverVersion: String): NegotiateResult {
    val obj = parseObject(bytes)
    val operations = obj["operations"]?.jsonArray
        ?: throw RommResponseException("response missing operations")
    return NegotiateResult(
        sessionId = obj.reqLong("session_id"),
        operations = operations.map { parseOperation(it.jsonObject) },
        totalUpload = obj.reqInt("total_upload"),
        totalDownload = obj.reqInt("total_download"),
        totalConflict = obj.reqInt("total_conflict"),
        totalNoOp = obj.reqInt("total_no_op"),
        totalDelete = obj.reqInt("total_delete"),
        serverVersion = serverVersion,
    )
}

internal fun parseSaveId(bytes: ByteArray): Long = parseObject(bytes).reqLong("id")

internal fun parseCompleted(bytes: ByteArray): CompletedSync {
    val session = parseObject(bytes)["session"]?.jsonObject
        ?: throw RommResponseException("response missing session")
    return CompletedSync(session.reqLong("id"), session.optString("status"))
}

internal fun safeFileName(name: String): String {
    val base = name.substringAfterLast('/').substringAfterLast('\\').trim()
    if (base.isEmpty() || base == "." || base == ".." || base.contains('\u0000')) {
        throw IllegalArgumentException("RomM file name cannot be stored")
    }
    return base
}
