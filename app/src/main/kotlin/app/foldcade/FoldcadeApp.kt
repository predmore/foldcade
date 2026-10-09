package app.foldcade

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import app.foldcade.api.plugin.CredentialLookup
import app.foldcade.api.plugin.CredentialStore
import app.foldcade.api.plugin.RommCredentials
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.plugins.gamenative.GameNativeLibrary
import app.foldcade.plugins.gamenative.GameNativePlayer
import app.foldcade.credentials.AndroidCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.Copy
import app.foldcade.language.SignedInBackend
import app.foldcade.language.builtInTheme
import app.foldcade.localfolder.LocalFolderBackend
import app.foldcade.music.HomeMusic
import app.foldcade.ui.FoldPaint
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
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

    /** The external player session opened by the host launch path. */
    val externalPlay = ExternalPlay()
    var companionLaunched: Boolean = false

    /** True only for the process that first recorded a launch. Calibration waits for the next one. */
    var buttonPromptFirstSession: Boolean = false
        private set

    /**
     * Sign-in and credential work. Keystore seal and open run here, on
     * [Dispatchers.IO], not on the main thread. Compose updates hop back to main.
     */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val credentialGate = Mutex()
    private val pluginLoad = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pluginsReady = CompletableDeferred<Unit>()
    private val libraryTicket = AtomicInteger()
    private var libraryRetry: () -> Unit = { reloadFolder() }
    private lateinit var rommPublish: RommPublish

    private val packageChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_CHANGED,
                -> reloadInstalledApps()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = SessionStore(getSharedPreferences("foldcade", MODE_PRIVATE))
        buttonPromptFirstSession = !store.buttonPromptSeenLaunch()
        if (buttonPromptFirstSession) store.setButtonPromptSeenLaunch()
        credentials = AndroidCredentialStore(this)
        plugins = PluginHost(Dispatchers.IO, credentials)
        music = HomeMusic(this, store)
        store.afterSessionChanged = music::onSessionChanged
        packaged = PackagedTheme.load(this)
        val names = listOf(Copy.builtIn) + listOfNotNull(packaged?.name)
        val motions = listOf(BackgroundMotion.Off) + listOfNotNull(packaged?.backgroundMotion)
        plays = PlaySessions.open(File(filesDir, "play-sessions/events.log"))
        val sliderTick = SliderTick()
        shell = ShellController(
            store,
            plugins,
            music::apply,
            music.trackTitle(),
            names,
            motions,
            cue = { index, slot ->
                index > 0 && packaged?.sounds?.play(slot) == true
            },
            bareTick = sliderTick::play,
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
        registerReceiver(
            packageChanges,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            RECEIVER_EXPORTED,
        )
        refreshCredentials()
        reloadInstalledApps()
        pluginLoad.launch {
            try {
                plugins.load(classLoader)
                restorePlayerSaveFolders()
                publishRomm()
                refreshGameNative()
                withContext(Dispatchers.Main.immediate) {
                    shell.refreshPlayerSaves()
                    shell.noteShelfChanged()
                }
                reloadInstalledApps()
            } finally {
                if (!pluginsReady.isCompleted) pluginsReady.complete(Unit)
            }
            if (!store.folderTree().isNullOrBlank()) reloadFolder()
        }
    }

    /** MAIN / LAUNCHER list. Package add, remove, and change call this again. */
    fun reloadInstalledApps() {
        scope.launch {
            val apps = InstalledAppCatalog(packageManager, packageName).list()
            val players = plugins.playerPackageNames()
            withContext(Dispatchers.Main.immediate) {
                if (::shell.isInitialized) shell.setInstalledApps(apps, players)
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
    /** Shortcuts and the stored list. A failure leaves the shelf tiles already there. */
    private fun refreshGameNative() {
        val library = plugins.library(GameNativeLibrary.ID) as? GameNativeLibrary ?: return
        Shelf.gameNativeInstalled = gameNativePackagePresent()
        try {
            refreshGameNativeCatalog(this, library, File(filesDir, "gamenative/catalog.txt"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Unit
        }
    }

    /** `getPackageInfo` on the GameNative package list. Not a query of every package. */
    private fun gameNativePackagePresent(): Boolean =
        GameNativePlayer.PACKAGES.any { name ->
            try {
                packageManager.getPackageInfo(name, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }

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

    /** Stores the picked tree and scans it. The grid shows that library. */
    fun useFolder(uri: Uri) {
        store.setFolderTree(uri.toString())
        reloadFolder()
    }

    fun reloadFolder() {
        libraryRetry = { reloadFolder() }
        val ticket = libraryTicket.incrementAndGet()
        pluginLoad.launch {
            pluginsReady.await()
            if (ticket != libraryTicket.get()) return@launch
            val saved = store.folderTree()
            if (saved.isNullOrBlank()) {
                withContext(Dispatchers.Main.immediate) {
                    if (ticket == libraryTicket.get()) shell.showNoLibrary()
                }
                return@launch
            }
            withContext(Dispatchers.Main.immediate) {
                if (ticket == libraryTicket.get()) shell.showLoading(insidePlatform = false)
            }
            if (ticket != libraryTicket.get()) return@launch
            val backend = plugins.library(LocalFolderBackend.ID) as? LocalFolderBackend
            if (backend == null) {
                showFailure(ticket, insidePlatform = false)
                return@launch
            }
            val loaded = try {
                val tree = DocumentTree(contentResolver, Uri.parse(saved))
                backend.bindTree(tree.root(), tree::children)
                loadPlatforms(
                    plugins,
                    LocalFolderBackend.ID,
                    folderName = backend::folderName,
                    occupiesBoth = { platformOccupiesBoth(plugins, it) },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (fatal: VirtualMachineError) {
                throw fatal
            } catch (failure: Exception) {
                Log.w("Foldcade", "Folder library failed", failure)
                null
            }
            withContext(Dispatchers.Main.immediate) {
                if (ticket != libraryTicket.get()) return@withContext
                if (loaded == null) {
                    shell.showUnreachable(insidePlatform = false)
                } else {
                    applyLoaded(LocalFolderBackend.ID, loaded)
                }
            }
        }
    }

    fun activateLibrary(label: String) {
        if (label == Copy.setUpRomm) {
            shell.openConnect(store.rommOrigin().orEmpty())
            return
        }
        val id = plugins.libraryIds().firstOrNull { libraryId ->
            runCatching { plugins.libraryLabel(libraryId) }.getOrNull() == label
        } ?: return
        if (id == LocalFolderBackend.ID) {
            reloadFolder()
            return
        }
        libraryRetry = { activateLibrary(label) }
        loadLibrary(id)
    }

    fun openPlatform(index: Int) {
        val entry = shell.entryAt(index) ?: return
        val libraryId = shell.activeLibraryId ?: return
        val platformId = entry.platformId ?: return
        shell.rememberPlatformPlace()
        libraryRetry = { openPlatformId(libraryId, platformId) }
        openPlatformId(libraryId, platformId)
    }

    fun leavePlatform() {
        libraryTicket.incrementAndGet()
        libraryRetry = { reloadFolder() }
        shell.restorePlatforms()
    }

    fun retryLibrary() {
        libraryRetry()
    }

    private fun openPlatformId(libraryId: String, platformId: String) {
        val ticket = libraryTicket.incrementAndGet()
        pluginLoad.launch {
            pluginsReady.await()
            if (ticket != libraryTicket.get()) return@launch
            withContext(Dispatchers.Main.immediate) {
                if (ticket == libraryTicket.get()) shell.showLoading(insidePlatform = true)
            }
            val games = try {
                val folderName = (plugins.library(libraryId) as? LocalFolderBackend)?.let { backend ->
                    backend::folderName
                } ?: { "" }
                loadGames(
                    plugins,
                    libraryId,
                    platformId,
                    folderName = folderName,
                    occupiesBoth = { platformOccupiesBoth(plugins, it) },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (fatal: VirtualMachineError) {
                throw fatal
            } catch (failure: Exception) {
                Log.w("Foldcade", "Folder games failed", failure)
                null
            }
            withContext(Dispatchers.Main.immediate) {
                if (ticket != libraryTicket.get()) return@withContext
                if (games == null) shell.showUnreachable(insidePlatform = true) else shell.showGames(games)
            }
        }
    }

    private fun loadLibrary(libraryId: String) {
        val ticket = libraryTicket.incrementAndGet()
        pluginLoad.launch {
            pluginsReady.await()
            if (ticket != libraryTicket.get()) return@launch
            withContext(Dispatchers.Main.immediate) {
                if (ticket == libraryTicket.get()) shell.showLoading(insidePlatform = false)
            }
            val loaded = try {
                loadPlatforms(
                    plugins,
                    libraryId,
                    occupiesBoth = { platformOccupiesBoth(plugins, it) },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (fatal: VirtualMachineError) {
                throw fatal
            } catch (failure: Exception) {
                Log.w("Foldcade", "Library failed", failure)
                null
            }
            withContext(Dispatchers.Main.immediate) {
                if (ticket != libraryTicket.get()) return@withContext
                if (loaded == null) shell.showUnreachable(insidePlatform = false) else applyLoaded(libraryId, loaded)
            }
        }
    }

    private fun applyLoaded(libraryId: String, loaded: LoadedLibrary) {
        when (loaded) {
            is LoadedLibrary.Platforms -> shell.showPlatforms(libraryId, loaded.entries)
            LoadedLibrary.NoPlatforms -> shell.showNoPlatforms(libraryId)
            LoadedLibrary.Unreachable -> shell.showUnreachable(insidePlatform = false)
        }
    }

    private suspend fun showFailure(ticket: Int, insidePlatform: Boolean) {
        withContext(Dispatchers.Main.immediate) {
            if (ticket == libraryTicket.get()) shell.showUnreachable(insidePlatform)
        }
    }
}
