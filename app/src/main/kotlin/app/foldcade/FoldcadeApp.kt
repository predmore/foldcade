package app.foldcade

import android.app.Application
import app.foldcade.api.plugin.CredentialLookup
import app.foldcade.api.plugin.CredentialStore
import app.foldcade.api.plugin.RommCredentials
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.credentials.AndroidCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.Copy
import app.foldcade.language.SignedInBackend
import app.foldcade.language.builtInTheme
import app.foldcade.music.HomeMusic
import app.foldcade.ui.FoldPaint
import java.io.File
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

    lateinit var music: HomeMusic
        private set
    lateinit var shell: ShellController
        private set
    lateinit var plugins: PluginHost
        private set
    var packaged: PackagedTheme? = null
        private set

    /** Local play history. Not a plugin API. */
    lateinit var plays: PlaySessions
        private set
    var companionLaunched: Boolean = false

    /**
     * Sign-in and credential work. Keystore seal and open run here, on
     * [Dispatchers.IO], not on the main thread. Compose updates hop back to main.
     */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val credentialGate = Mutex()
    private val pluginLoad = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var rommPublish: RommPublish

    override fun onCreate() {
        super.onCreate()
        store = SessionStore(getSharedPreferences("foldcade", MODE_PRIVATE))
        credentials = AndroidCredentialStore(this)
        plugins = PluginHost(Dispatchers.IO, credentials)
        music = HomeMusic(this, store)
        store.afterSessionChanged = music::onSessionChanged
        packaged = PackagedTheme.load(this)
        val names = listOf(Copy.builtIn) + listOfNotNull(packaged?.name)
        val motions = listOf(BackgroundMotion.Off) + listOfNotNull(packaged?.backgroundMotion)
        plays = PlaySessions.open(File(filesDir, "play-sessions/events.log"))
        shell = ShellController(
            store,
            plugins,
            music::apply,
            music.trackTitle(),
            names,
            motions,
            cue = { index, slot ->
                if (index > 0) packaged?.sounds?.play(slot)
            },
            lastPlayedMillis = plays::lastPlayedMillis,
        )
        plays.onChanged = shell::notePlayChanged
        rommPublish = RommPublish(
            read = {
                readRommPublish(
                    origin = store::rommOrigin,
                    platforms = plugins::platformDefinitions,
                    cacheRoot = ::rommCacheRoot,
                )
            },
            apply = { request ->
                publishRommWiring(
                    request.origin,
                    credentials,
                    request.cacheRoot,
                    request.platforms,
                )
            },
        )
        refreshCredentials()
        pluginLoad.launch {
            plugins.load(classLoader)
            restorePlayerSaveFolders()
            publishRomm()
            withContext(Dispatchers.Main.immediate) {
                shell.refreshPlayerSaves()
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::music.isInitialized) music.onTrimMemory(level)
    }

    fun paintFor(themeIndex: Int): FoldPaint {
        val theme = packaged
        if (theme != null && themeIndex > 0) return theme.paint
        return FoldPaint(builtInTheme())
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

    /**
     * Installs or clears [app.foldcade.plugins.romm.RommPlugins].
     * Reads the saved origin, then the host platform list, then the cache directory.
     * A later call supersedes an earlier one. The same origin, cache, and platforms
     * leave the current wiring in place.
     */
    private fun restorePlayerSaveFolders() {
        for (id in plugins.playerIds()) {
            val player = plugins.player(id) as? SaveFolderHolder ?: continue
            val uri = store.playerSaveFolder(id) ?: continue
            player.bindSaveFolder(uri)
        }
    }

    fun publishRomm() {
        rommPublish.publish()
    }

    private fun rommCacheRoot(): Path = cacheDir.toPath().resolve("romm")

    fun backendLabel(pluginId: String): String = when (pluginId) {
        RommCredentials.PLUGIN_ID -> "RomM"
        else -> pluginId
    }
}
