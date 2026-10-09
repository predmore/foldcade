package app.foldcade

import android.app.Application
import app.foldcade.api.plugin.CredentialLookup
import app.foldcade.api.plugin.CredentialStore
import app.foldcade.api.plugin.RommCredentials
import app.foldcade.credentials.AndroidCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.SignedInBackend
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class FoldcadeApp : Application() {
    lateinit var store: SessionStore
        private set

    /**
     * Host-owned secrets. Plugins do not open this store.
     * RomM reads its token through [rommTokenSource], which looks up this
     * same instance. There is no second token file.
     */
    lateinit var credentials: CredentialStore
        private set

    lateinit var shell: ShellController
        private set
    lateinit var plugins: PluginHost
        private set
    var companionLaunched: Boolean = false

    /**
     * Sign-in and credential work. Keystore seal and open run here, on
     * [Dispatchers.IO], not on the main thread. Compose updates hop back to main.
     */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val credentialGate = Mutex()
    private val pluginLoad = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        store = SessionStore(getSharedPreferences("foldcade", MODE_PRIVATE))
        credentials = AndroidCredentialStore(this)
        plugins = PluginHost(Dispatchers.IO, credentials)
        shell = ShellController(store, plugins)
        refreshCredentials()
        pluginLoad.launch {
            plugins.load(classLoader)
            publishRomm()
        }
    }

    fun refreshCredentials() {
        scope.launch {
            credentialGate.withLock { loadSignedIn() }
        }
    }

    suspend fun editCredentials(block: suspend () -> Unit) {
        credentialGate.withLock { block() }
    }

    private suspend fun loadSignedIn() {
        val romm = lookupOrUnreadable(
            credentials,
            RommCredentials.PLUGIN_ID,
            RommCredentials.ACCESS_TOKEN,
        )
        val stored = if (romm is CredentialLookup.Unreadable) {
            emptyList()
        } else {
            val ids = try {
                credentials.pluginIds()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptySet()
            }
            ids.map { SignedInBackend(it, backendLabel(it)) }
        }
        val recovery = signedInRecovery(romm, stored)
        withContext(Dispatchers.Main.immediate) {
            shell.applyRecovery(recovery)
            publishRomm()
        }
    }

    /** Installs or clears [app.foldcade.plugins.romm.RommPlugins] from the saved origin and this store. */
    fun publishRomm() {
        publishRommWiring(
            store.rommOrigin(),
            credentials,
            rommCacheRoot(),
            plugins.platformDefinitions(),
        )
    }

    private fun rommCacheRoot(): Path = cacheDir.toPath().resolve("romm")

    fun backendLabel(pluginId: String): String = when (pluginId) {
        RommCredentials.PLUGIN_ID -> "RomM"
        else -> pluginId
    }
}
