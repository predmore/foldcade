package app.foldcade.plugins.romm

import app.foldcade.romm.RegisteredDevice
import app.foldcade.romm.RommClient
import app.foldcade.romm.SaveLedger
import app.foldcade.romm.writeDurably
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.stream.Collectors

/**
 * Files this plugin keeps where the system does not evict them, under [root]:
 *
 * - `device.json`: the RomM device registered for each origin, so a restart does
 *   not register a new device and lose its sync history;
 * - `install-id`: a random name sent as the device host name, so RomM can match
 *   this install again if `device.json` is lost;
 * - `servers/<origin key>/saves/<rom>/slot-<slot>/<name>.sav`: exactly one file per slot;
 * - `servers/<origin key>/saves/<rom>/ledger.json`: what each slot last agreed with RomM ([SaveLedger]).
 *
 * Saves sit under their server, because a ROM id means a different game on
 * another server. Nothing here is a secret. The token stays in the credential store.
 */
internal class RommData(val root: Path, origin: String) {
    private val server: Path = root.resolve("servers").resolve(serverKey(origin))

    fun device(origin: String): RegisteredDevice? {
        val entry = devices()[canonicalOrigin(origin)] as? JsonObject ?: return null
        val id = entry["device_id"]?.jsonPrimitive?.contentOrNull ?: return null
        val version = entry["client_version"]?.jsonPrimitive?.contentOrNull ?: return null
        return RegisteredDevice(id, version)
    }

    @Synchronized
    fun saveDevice(origin: String, device: RegisteredDevice?) {
        val next = devices().toMutableMap()
        val key = canonicalOrigin(origin)
        if (device == null) {
            next.remove(key)
        } else {
            next[key] = buildJsonObject {
                put("device_id", device.deviceId)
                put("client_version", device.clientVersion)
            }
        }
        writeDurably(root.resolve(DEVICE_FILE), JsonObject(next).toString().toByteArray(Charsets.UTF_8))
    }

    /** `foldcade-` and twelve random hex digits, made once per install. */
    @Synchronized
    fun hostName(): String {
        val file = root.resolve(INSTALL_FILE)
        try {
            val stored = String(Files.readAllBytes(file), Charsets.UTF_8).trim()
            if (stored.isNotEmpty()) return stored
        } catch (_: IOException) {
            Unit
        }
        val bytes = ByteArray(6).also { SecureRandom().nextBytes(it) }
        val made = "foldcade-" + bytes.joinToString("") { "%02x".format(it) }
        writeDurably(file, made.toByteArray(Charsets.UTF_8))
        return made
    }

    fun savesRoot(): Path = server.resolve("saves")

    /** Copies of saves handed to the shell for one launch. Replaced on the next launch of that ROM. */
    fun placingRoot(romId: Long): Path = server.resolve("placing").resolve(romId.toString())

    fun romSaves(romId: Long): Path = savesRoot().resolve(romId.toString())

    fun ledger(romId: Long): SaveLedger = SaveLedger(romSaves(romId).resolve(SaveLedger.FILE_NAME))

    /** The one file that holds [slot]. Its name is what an upload calls the save. */
    fun slotFile(romId: Long, slot: String): Path =
        romSaves(romId).resolve(SLOT_DIR_PREFIX + encodeSlot(slot)).resolve(slotFileName(slot))

    /** ROM ids with a save directory, in no order. */
    fun romsWithSaves(): List<Long> {
        val saves = savesRoot()
        if (!Files.isDirectory(saves)) return emptyList()
        return Files.list(saves).use { stream ->
            stream.collect(Collectors.toList()).mapNotNull { it.fileName.toString().toLongOrNull()?.takeIf { id -> id >= 1 } }
        }
    }

    /** Slot names that have a directory for [romId]. */
    fun slots(romId: Long): List<String> {
        val dir = romSaves(romId)
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream.collect(Collectors.toList())
                .filter { Files.isDirectory(it) }
                .mapNotNull { path ->
                    val name = path.fileName.toString()
                    if (!name.startsWith(SLOT_DIR_PREFIX)) null else decodeSlot(name.removePrefix(SLOT_DIR_PREFIX))
                }
        }
    }

    private fun devices(): Map<String, kotlinx.serialization.json.JsonElement> = try {
        val text = String(Files.readAllBytes(root.resolve(DEVICE_FILE)), Charsets.UTF_8)
        Json.parseToJsonElement(text) as? JsonObject ?: emptyMap()
    } catch (_: Exception) {
        emptyMap()
    }

    companion object {
        /**
         * A short, file-safe name for one server. The origin is normalized first,
         * so `https://host/` and `https://host/api` are the same server.
         */
        internal fun serverKey(origin: String): String =
            MessageDigest.getInstance("SHA-256").digest(canonicalOrigin(origin).toByteArray(Charsets.UTF_8))
                .take(8).joinToString("") { "%02x".format(it) }

        internal fun canonicalOrigin(origin: String): String = try {
            RommClient.normalizeOrigin(origin)
        } catch (_: IllegalArgumentException) {
            origin.trim()
        }

        private const val DEVICE_FILE = "device.json"
        private const val INSTALL_FILE = "install-id"

        /** Keeps a slot directory from ever being `.`, `..`, or `.trash`. */
        private const val SLOT_DIR_PREFIX = "slot-"

        private fun encodeSlot(slot: String): String =
            URLEncoder.encode(slot, StandardCharsets.UTF_8).replace("+", "%20")

        private fun decodeSlot(encoded: String): String? = try {
            URLDecoder.decode(encoded, StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }

        /** The slot name kept to safe file-name characters, with `.sav`. */
        internal fun slotFileName(slot: String): String {
            val safe = slot.map { if (it.isLetterOrDigit() || it in "._- ") it else '_' }
                .joinToString("")
                .trim('.', ' ')
            return (safe.ifEmpty { "save" }) + ".sav"
        }
    }
}
