package app.foldcade

import android.app.Application
import app.foldcade.api.plugin.CredentialLookup
import app.foldcade.api.plugin.CredentialStore
import app.foldcade.api.plugin.RommCredentials
import app.foldcade.credentials.AndroidCredentialStore
import app.foldcade.language.SignedInBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

class FoldcadeApp : Application() {
    lateinit var store: SessionStore
        private set

    /**
     * Host-owned secrets. Plugin wiring should pass this same instance into
     * the plugin host. Plugins do not read this store except through the
     * namespaced access the host gives them.
     */
    lateinit var credentials: CredentialStore
        private set

    lateinit var shell: ShellController
        private set
    var companionLaunched: Boolean = false

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val credentialGate = Mutex()

    override fun onCreate() {
        super.onCreate()
        store = SessionStore(getSharedPreferences("foldcade", MODE_PRIVATE))
        credentials = AndroidCredentialStore(this)
        shell = ShellController(store)
        refreshCredentials()
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
        val romm = try {
            credentials.lookup(RommCredentials.PLUGIN_ID, RommCredentials.ACCESS_TOKEN)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CredentialLookup.Unreadable
        }
        if (romm is CredentialLookup.Unreadable) {
            shell.setSignedIn(emptyList())
            shell.askToSignInAgain()
            return
        }
        val ids = try {
            credentials.pluginIds()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptySet()
        }
        shell.setSignedIn(ids.map { SignedInBackend(it, backendLabel(it)) })
    }

    fun backendLabel(pluginId: String): String = when (pluginId) {
        RommCredentials.PLUGIN_ID -> "RomM"
        else -> pluginId
    }
}
