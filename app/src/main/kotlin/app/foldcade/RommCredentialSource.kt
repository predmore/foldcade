package app.foldcade

import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.CredentialLookup
import app.foldcade.api.plugin.CredentialStore
import app.foldcade.api.plugin.Platform
import app.foldcade.plugins.romm.CachePathContentUri
import app.foldcade.plugins.romm.ROMM_CONTENT_AUTHORITY
import app.foldcade.plugins.romm.RommPlugins
import app.foldcade.plugins.romm.RommTokenSource
import android.os.Looper
import app.foldcade.plugins.romm.RommWiring
import app.foldcade.plugins.romm.SaveBytes
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking

/**
 * Reads the host [CredentialStore] through the RomM plugin's [RommTokenSource].
 * The plugin does not keep a second copy of the token.
 * A password entry is ignored: the merged RomM client has no password login.
 * [CredentialLookup.Unreadable] becomes null so the caller can ask for sign-in.
 *
 * [RommGate] calls [RommTokenSource.accessToken] on [kotlinx.coroutines.Dispatchers.IO].
 * A DataStore read is refused on the main thread.
 */
fun rommTokenSource(store: CredentialStore): RommTokenSource = RommTokenSource {
    readRommToken(store, onMainThread = rommTokenReadOnMainThread())
}

internal fun rommTokenReadOnMainThread(): Boolean {
    val main = Looper.getMainLooper() ?: return false
    return Looper.myLooper() == main
}

internal fun readRommToken(store: CredentialStore, onMainThread: Boolean): String? {
    check(!onMainThread) { "RomM token reads run off the main thread" }
    val found = try {
        runBlocking { store.lookup(RommCredentials.PLUGIN_ID, RommCredentials.ACCESS_TOKEN) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
    return when (found) {
        is CredentialLookup.Present -> when (val credential = found.credential) {
            is Credential.ApiToken -> credential.value
            is Credential.Password -> null
        }
        CredentialLookup.Absent, CredentialLookup.Unreadable, null -> null
    }
}

/**
 * Points [RommPlugins] at [store]. A blank [origin] removes the wiring.
 * The token is not copied into the wiring.
 *
 * [cacheRoot] holds downloaded ROMs and may be cleared by the system.
 * [dataRoot] holds saves and the device id, and must not be a cache directory.
 * [saveBytes] reads a save the player wrote at a content URI.
 *
 * The same origin, roots, and platform list leave the current wiring in place.
 * [platforms] is compared in list order.
 */
fun publishRommWiring(
    origin: String?,
    store: CredentialStore,
    cacheRoot: Path,
    platforms: List<Platform> = emptyList(),
    contentAuthority: String = ROMM_CONTENT_AUTHORITY,
    dataRoot: Path,
    saveBytes: SaveBytes = SaveBytes { null },
) {
    val current = RommPlugins.wiring
    if (origin.isNullOrBlank()) {
        if (current != null) RommPlugins.clear()
        return
    }
    if (
        current != null &&
        current.origin == origin &&
        current.cacheRoot == cacheRoot &&
        current.dataRoot == dataRoot &&
        current.platforms == platforms
    ) {
        return
    }
    RommPlugins.install(
        RommWiring(
            origin = origin,
            tokenSource = rommTokenSource(store),
            cacheRoot = cacheRoot,
            dataRoot = dataRoot,
            contentUris = CachePathContentUri(cacheRoot, contentAuthority, dataRoot),
            platforms = platforms,
            saveBytes = saveBytes,
        ),
    )
}

/**
 * What [readRommPublish] collected, in that function's order.
 * [platforms] stays in host registration order.
 */
internal data class RommPublishRequest(
    val origin: String?,
    val platforms: List<Platform>,
    val cacheRoot: Path,
    val dataRoot: Path,
)

/**
 * Saved origin, then the host platform list, then the cache directory, then the data directory.
 */
internal fun readRommPublish(
    origin: () -> String?,
    platforms: () -> List<Platform>,
    cacheRoot: () -> Path,
    dataRoot: () -> Path,
): RommPublishRequest {
    val saved = origin()
    val definitions = platforms()
    val cache = cacheRoot()
    return RommPublishRequest(saved, definitions, cache, dataRoot())
}

/**
 * Serializes RomM publishes. A later [publish] supersedes an earlier one:
 * the earlier call applies only when no later call has started.
 */
internal class RommPublish(
    private val read: () -> RommPublishRequest,
    private val apply: (RommPublishRequest) -> Unit,
) {
    private val gate = Any()
    private var ticket = 0L

    fun publish() {
        val mine = synchronized(gate) { ++ticket }
        // If read throws, this ticket stays the newest and an earlier call does not apply.
        val request = read()
        synchronized(gate) {
            if (mine != ticket) return
            apply(request)
        }
    }
}
