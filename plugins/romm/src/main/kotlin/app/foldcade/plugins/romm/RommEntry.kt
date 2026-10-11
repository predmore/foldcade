package app.foldcade.plugins.romm

import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.MetadataProvider
import app.foldcade.api.plugin.PLUGIN_API_MINOR
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.Platform
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.romm.DeviceAuthChallenge
import app.foldcade.romm.DeviceTokenPoll
import app.foldcade.romm.RegisteredDevice
import app.foldcade.romm.RommClient
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException

const val ROMM_LIBRARY_ID = "romm"
const val ROMM_METADATA_ID = "romm.metadata"
const val ROMM_CONTENT_AUTHORITY = "app.foldcade.romm.cache"

/**
 * Bytes of a save the shell observed, when the URI is not one this plugin minted.
 * The default reads nothing. The host supplies a reader for its content URIs.
 */
fun interface SaveBytes {
    fun read(contentUri: String): ByteArray?
}

/**
 * One configured RomM server.
 * [tokenSource] supplies the client token. This object does not write that token.
 *
 * [cacheRoot] holds downloaded ROMs. The system may clear it, and a ROM can be
 * downloaded again. [dataRoot] holds saves, the sync ledger, and the device id,
 * none of which can be downloaded again, so it must not be a cache directory.
 * ROMs and saves are kept apart per [origin].
 *
 * [rememberedDevice] is the RomM device for [origin]. It is kept in [dataRoot],
 * so a restart keeps the same device and RomM keeps its sync history.
 * It is not a credential.
 *
 * [platforms] are definitions the host already has. A RomM slug that matches one
 * of their ids or aliases becomes that canonical id. Slugs with no match stay
 * the slug. This plugin does not keep a second slug table.
 *
 * [saveBytes] reads a save the player wrote at a URI this plugin did not mint.
 */
class RommWiring(
    val origin: String,
    val tokenSource: RommTokenSource,
    val cacheRoot: Path,
    val dataRoot: Path,
    val contentUris: ContentUriAdapter = CachePathContentUri(cacheRoot, dataRoot = dataRoot),
    val platforms: List<Platform> = emptyList(),
    val clientVersion: String = "0.1.0",
    val deviceName: String = "Foldcade",
    val saveBytes: SaveBytes = SaveBytes { null },
) {
    internal val data = RommData(dataRoot, origin)

    /** Downloaded ROMs for [origin]. A ROM id names a different game on another server. */
    internal val romRoot: Path = cacheRoot.resolve("servers").resolve(RommData.serverKey(origin))

    @Volatile
    private var device: RegisteredDevice? = null

    @Volatile
    private var deviceLoaded = false

    var rememberedDevice: RegisteredDevice?
        get() {
            if (!deviceLoaded) {
                device = data.device(origin)
                deviceLoaded = true
            }
            return device
        }
        set(value) {
            if (value == rememberedDevice) return
            device = value
            deviceLoaded = true
            data.saveDevice(origin, value)
        }

    internal var open: (String, () -> String?) -> RommOps = { server, token ->
        ClientRommOps(RommClient(server, token))
    }
}

/**
 * The wiring [RommEntry] reads. The host installs this before connect.
 * Replacing it does not write a token.
 */
object RommPlugins {
    @Volatile
    var wiring: RommWiring? = null
        private set

    fun install(wiring: RommWiring) {
        this.wiring = wiring
    }

    fun clear() {
        wiring = null
    }
}

/**
 * Device-code sign-in over the client. The approved token is returned to the
 * caller. This type does not store it.
 */
class RommPairing(
    private val wiring: RommWiring,
) {
    suspend fun begin(deviceIdentifier: String): RommPairingChallenge =
        unauthenticated { ops ->
            val challenge = ops.beginDeviceAuth(deviceIdentifier, wiring.deviceName, wiring.clientVersion)
            challenge.toPublic(ops.verificationUrl(challenge))
        }

    suspend fun poll(deviceCode: String): RommPairingPoll =
        unauthenticated { ops ->
            val result = ops.pollDeviceToken(deviceCode).toPublic()
            if (result is RommPairingPoll.Approved) {
                wiring.rememberedDevice = RegisteredDevice(result.deviceId, wiring.clientVersion)
            }
            result
        }

    private suspend fun <T> unauthenticated(block: suspend (RommOps) -> T): T {
        val ops = wiring.open(wiring.origin) { null }
        try {
            return translate { block(ops) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            ops.close()
        }
    }
}

data class RommPairingChallenge(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val intervalSeconds: Int,
    val expiresInSeconds: Int,
)

sealed class RommPairingPoll {
    data object Pending : RommPairingPoll()
    data object SlowDown : RommPairingPoll()
    data object Denied : RommPairingPoll()
    data object Expired : RommPairingPoll()

    data class Approved(
        val accessToken: String,
        val deviceId: String,
        val scopes: List<String>,
    ) : RommPairingPoll()
}

private fun DeviceAuthChallenge.toPublic(verificationUrl: String) = RommPairingChallenge(
    deviceCode = deviceCode,
    userCode = userCode,
    verificationUrl = verificationUrl,
    intervalSeconds = intervalSeconds,
    expiresInSeconds = expiresInSeconds,
)

private fun DeviceTokenPoll.toPublic(): RommPairingPoll = when (this) {
    DeviceTokenPoll.Pending -> RommPairingPoll.Pending
    DeviceTokenPoll.SlowDown -> RommPairingPoll.SlowDown
    DeviceTokenPoll.Denied -> RommPairingPoll.Denied
    DeviceTokenPoll.Expired -> RommPairingPoll.Expired
    is DeviceTokenPoll.Approved -> RommPairingPoll.Approved(accessToken, deviceId, scopes)
}

/**
 * Registers the RomM library and the RomM metadata provider as separate slots.
 * Choosing [ROMM_LIBRARY_ID] selects [ROMM_METADATA_ID] by default. The shell
 * does not read RomM fields. A RomM slug stays the slug unless
 * [RommWiring.platforms] already aliases it. There is no private slug map.
 *
 * This bundled entry stays under the GNU GPLv3. An independent plugin that
 * uses only the published API may use any license.
 */
class RommEntry : PluginEntry {
    private val gate = RommGate { RommPlugins.wiring }

    override val apiVersion = PLUGIN_API_VERSION
    override val apiMinor = PLUGIN_API_MINOR
    override val libraries: List<LibraryBackend> = listOf(RommLibrary(gate))
    override val metadataProviders: List<MetadataProvider> = listOf(RommMetadata(gate))
}
