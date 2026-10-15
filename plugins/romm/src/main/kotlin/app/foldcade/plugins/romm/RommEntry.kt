package app.foldcade.plugins.romm

import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.LibraryInstance
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
import java.util.concurrent.CopyOnWriteArrayList
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
 * [cacheRoot] holds downloaded ROMs, which can be downloaded again. A host may
 * keep it in shared storage, so a player that opens games by path can read them.
 * [artCacheRoot] holds cover art, which the system may clear; it defaults to
 * [cacheRoot]. [dataRoot] holds saves, the sync ledger, and the device id, none
 * of which can be downloaded again, so it must not be a cache directory.
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
    val artCacheRoot: Path = cacheRoot,
) {
    internal val data = RommData(dataRoot, origin)

    /** Downloaded ROMs for [origin]. A ROM id names a different game on another server. */
    internal val romRoot: Path = cacheRoot.resolve("servers").resolve(RommData.serverKey(origin))

    /** Cover art for [origin]. */
    internal val artRoot: Path = artCacheRoot.resolve("servers").resolve(RommData.serverKey(origin)).resolve("artwork")

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
 * Roots, platforms, and the token reader every RomM instance shares.
 * The origin is not here. Each instance builds its wiring from [LibraryBackend.configure].
 * Per-instance roots still arrive only through [RommPlugins]. A plugin outside this
 * repository has no data root. That limit predates instance ids.
 */
class RommSupply(
    val cacheRoot: Path,
    val dataRoot: Path,
    val platforms: List<Platform> = emptyList(),
    val contentUris: ContentUriAdapter? = null,
    val clientVersion: String = "0.1.0",
    val deviceName: String = "Foldcade",
    val saveBytes: SaveBytes = SaveBytes { null },
    val tokenFor: (credentialKey: String) -> String? = { null },
    val artCacheRoot: Path = cacheRoot,
) {
    fun wiring(origin: String, credentialKey: String): RommWiring = RommWiring(
        origin = origin,
        tokenSource = RommTokenSource { tokenFor(credentialKey) },
        cacheRoot = cacheRoot,
        dataRoot = dataRoot,
        contentUris = contentUris ?: CachePathContentUri(cacheRoot, dataRoot = dataRoot),
        platforms = platforms,
        clientVersion = clientVersion,
        deviceName = deviceName,
        saveBytes = saveBytes,
        artCacheRoot = artCacheRoot,
    )
}

/**
 * Shared RomM supply, plus the default instance's wiring for cover art.
 * [wiring] is that default server, not a slot that holds every origin.
 * Replacing it does not write a token.
 */
object RommPlugins {
    @Volatile
    var supply: RommSupply? = null
        private set

    @Volatile
    var wiring: RommWiring? = null
        private set

    fun installSupply(supply: RommSupply) {
        this.supply = supply
    }

    fun install(wiring: RommWiring) {
        this.wiring = wiring
    }

    /** Drops the default instance's wiring and leaves [supply] for any other instance. */
    fun clearDefault() {
        wiring = null
    }

    fun clear() {
        wiring = null
        supply = null
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
 * This bundled entry is licensed under MIT.
 */
class RommEntry : PluginEntry {
    private val instances = CopyOnWriteArrayList<RommLibrary>()
    private val library = RommLibrary.open(ROMM_LIBRARY_ID).also { instances.add(it) }
    private val metadata = RommMetadata { id -> instances.firstOrNull { it.instanceId == id }?.connection }

    override val apiVersion = PLUGIN_API_VERSION
    override val apiMinor = PLUGIN_API_MINOR
    override val libraries: List<LibraryBackend> = listOf(library)
    override val metadataProviders: List<MetadataProvider> = listOf(metadata)

    override fun openLibrary(instance: LibraryInstance): LibraryBackend? {
        if (instance.typeId != ROMM_LIBRARY_ID) return null
        instances.firstOrNull { it.instanceId == instance.instanceId }?.let { return it }
        return RommLibrary.open(instance.instanceId).also { instances.add(it) }
    }
}
