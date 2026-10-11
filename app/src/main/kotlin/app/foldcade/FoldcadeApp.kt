package app.foldcade

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.CredentialLookup
import app.foldcade.api.plugin.CredentialStore
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.plugins.gamenative.CatalogGame
import app.foldcade.plugins.gamenative.GameNativeLibrary
import app.foldcade.plugins.gamenative.GameNativePlayer
import app.foldcade.plugins.moonlight.MoonlightApp
import app.foldcade.plugins.moonlight.MoonlightCatalog
import app.foldcade.plugins.moonlight.MoonlightLibrary
import app.foldcade.plugins.moonlight.moonlightApp
import app.foldcade.plugins.moonlight.moonlightShortcutKey
import app.foldcade.credentials.AndroidCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.plugins.romm.RommArtwork
import app.foldcade.plugins.romm.SaveBytes
import app.foldcade.artwork.ART_HOSTS
import app.foldcade.artwork.ArtBytes
import app.foldcade.artwork.ArtFinder
import app.foldcade.artwork.ArtIndex
import app.foldcade.artwork.LibretroThumbnails
import app.foldcade.artwork.PublicArtHttp
import app.foldcade.artwork.SteamGridDb
import app.foldcade.artwork.SteamStoreArt
import app.foldcade.net.PublicHttps
import coil3.ImageLoader
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.Copy
import app.foldcade.language.EmptyGrid
import app.foldcade.language.HostScreen
import app.foldcade.language.LibraryFolder
import app.foldcade.language.MoonlightDiscoveredApp
import app.foldcade.language.MoonlightSource
import app.foldcade.language.SignedInBackend
import app.foldcade.language.decodeMoonlightApps
import app.foldcade.language.builtInTheme
import app.foldcade.localfolder.LocalFolderBackend
import app.foldcade.localfolder.SteamShortcut
import app.foldcade.music.HomeMusic
import app.foldcade.ui.FoldPaint
import java.io.File
import java.nio.file.Path
import java.util.Locale
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

    /** Waits until the plugin entries are loaded, so a player can be found by id. */
    internal suspend fun awaitPlugins() = pluginsReady.await()

    /** One save flow at a time: a launch's placement, or a return's read-back. */
    internal val saveFlow = Mutex()

    /** Moves library saves in and out of each player's save folder. Replaced files are kept in `save-backups`. */
    internal val playerSaves: PlayerSaves by lazy {
        PlayerSaves(AndroidSaveDocuments(contentResolver), store, filesDir.toPath().resolve("save-backups"))
    }

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

    /** Public art hosts over HTTPS only. RomM has its own client. */
    private val publicArt: PublicHttps by lazy { PublicHttps(ART_HOSTS) }

    /** The user's SteamGridDB key, read from the credential store at start. Null when none is saved. */
    @Volatile
    var steamGridDbKey: String? = null

    /**
     * Art from public sources: Steam's CDN for Steam games, then libretro's
     * thumbnails for ROMs, then SteamGridDB when a key is saved. Results and
     * misses are kept in filesDir, listings and images in cacheDir. Nothing is
     * sent while the Game art setting is off.
     */
    val art: ArtFinder by lazy {
        val http = PublicArtHttp(publicArt)
        val sources = listOf(
            SteamStoreArt(http),
            LibretroThumbnails(http, File(cacheDir, "art/libretro")),
            SteamGridDb(http) { steamGridDbKey },
        )
        ArtFinder({ sources }, ArtIndex(File(filesDir, "art/index.tsv"))) { shell.model.artwork }
    }

    private val artBytes: ArtBytes by lazy { ArtBytes(File(cacheDir, "art/images"), PublicArtHttp(publicArt)) }

    /** Whether SteamGridDB accepts [key]. Null when it cannot be reached. */
    suspend fun checkSteamGridDbKey(key: String): Boolean? = SteamGridDb.accepts(PublicArtHttp(publicArt), key)

    /** Cover addresses: the metadata providers first, then the public sources. */
    val covers: CoverArt by lazy { CoverArt(plugins, art) }

    /** Decodes and caches covers. Public art loads through :net, RomM's through its own client. */
    val images: ImageLoader by lazy {
        val romm = RommArtwork()
        ImageLoader.Builder(this)
            .components {
                add(
                    CoverFetcher.Factory { uri ->
                        when {
                            !publicArt.allows(uri) -> romm.load(uri)
                            shell.model.artwork -> artBytes.load(uri)
                            else -> null
                        }
                    },
                    CoverImage::class,
                )
                add(CoverKeyer, CoverImage::class)
            }
            .build()
    }

    /** Local play history. Not a plugin API. */
    lateinit var plays: PlaySessions
        private set

    /** The external player session opened by the host launch path. */
    val externalPlay = ExternalPlay()
    var companionLaunched: Boolean = false

    /** True only for the process that first recorded a launch. Calibration waits for the next one. */
    var buttonPromptFirstSession: Boolean = false
        private set

    /** Set before composition when a debug capture freezes the island morph. */
    var islandHold by mutableStateOf<IslandHold?>(null)

    /**
     * Sign-in and credential work. Keystore seal and open run here, on
     * [Dispatchers.IO], not on the main thread. Compose updates hop back to main.
     */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val credentialGate = Mutex()
    private val pluginLoad = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pluginsReady = CompletableDeferred<Unit>()

    /** Computer names from the last Moonlight shortcut read, keyed by lowercase host UUID. */
    @Volatile
    private var moonlightHostNames: Map<String, String> = emptyMap()
    private val libraryTicket = AtomicInteger()
    private val rommTicket = AtomicInteger()
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
        val themeTrackList = List(names.size) { index -> if (index == 0) null else packaged?.music }
        val initialTheme = if (names.size > 1) 1 else 0
        music.setThemeChoice(themeTrackList.getOrNull(initialTheme))
        plays = PlaySessions.open(File(filesDir, "play-sessions/events.log"))
        val sliderTick = SliderTick()
        plays.packageOf = { gameId ->
            val platform = Shelf.games.firstOrNull { it.id == gameId }?.platformId
            val player = platform?.let { preferredPlayer(plugins.playersFor(it), ::packageInstalled) }
            player?.packageNames?.let { names -> names.firstOrNull(::packageInstalled) ?: names.firstOrNull() }
        }
        shell = ShellController(
            store,
            plugins,
            music::apply,
            onTheme = { index -> music.setThemeChoice(themeTrackList.getOrNull(index)) },
            themeNames = names,
            themeMotions = motions,
            cue = { index, slot ->
                index > 0 && packaged?.sounds?.play(slot) == true
            },
            bareTick = sliderTick::play,
            homeTracks = music.homeTracks(),
            themeTracks = themeTrackList,
            lastPlayedMillis = plays::lastPlayedMillis,
            onMoonlightCatalog = { refreshMoonlightShelf() },
            onReviewMoonlight = ::reviewMoonlightImport,
        )
        plays.onChanged = shell::notePlayChanged
        rommPublish = RommPublish(
            read = {
                readRommPublish(
                    origin = store::rommOrigin,
                    platforms = plugins::platformDefinitions,
                    cacheRoot = ::rommCacheRoot,
                    dataRoot = ::rommDataRoot,
                )
            },
            apply = { request ->
                publishRommWiring(
                    request.origin,
                    credentials,
                    request.cacheRoot,
                    request.platforms,
                    // The manifest's FileProvider authority. The debug build's id ends in .debug.
                    contentAuthority = "$packageName.romm.cache",
                    dataRoot = request.dataRoot,
                    saveBytes = SaveBytes { uri -> AndroidSaveDocuments(contentResolver).read(uri) },
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
        getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(rommReconnect)
        refreshCredentials()
        reloadInstalledApps()
        pluginLoad.launch {
            try {
                plugins.load(classLoader)
                restorePlayerSaveFolders()
                publishRomm()
                refreshGameNative()
                refreshMoonlightShelf()
                withContext(Dispatchers.Main.immediate) {
                    shell.refreshPlayerSaves()
                    shell.noteShelfChanged()
                }
                reloadInstalledApps()
            } finally {
                if (!pluginsReady.isCompleted) pluginsReady.complete(Unit)
            }
            if (store.folderTrees().isNotEmpty()) reloadFolder()
            syncRomm()
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
            // The SteamGridDB key is an art source, not a library to sign out of.
            ids.filter { it != SteamGridDb.CREDENTIAL_ID }.map { SignedInBackend(it, backendLabel(it)) }
        }
        val artKey = lookupOrUnreadable(credentials, SteamGridDb.CREDENTIAL_ID, SteamGridDb.CREDENTIAL_KEY)
        steamGridDbKey = ((artKey as? CredentialLookup.Present)?.credential as? Credential.ApiToken)?.value
        val recovery = signedInRecovery(romm, stored)
        withContext(Dispatchers.Main.immediate) {
            shell.setArtKeySaved(steamGridDbKey != null)
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
            refreshGameNativeCatalog(this, library, File(filesDir, "gamenative/catalog.txt"), steamFileGames)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Unit
        }
    }

    /** `getPackageInfo` on the GameNative package list. Not a query of every package. */
    private fun gameNativePackagePresent(): Boolean =
        GameNativePlayer.PACKAGES.any(::packageInstalled)

    private fun packageInstalled(name: String): Boolean =
        try {
            packageManager.getPackageInfo(name, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private fun restorePlayerSaveFolders() {
        for (id in plugins.playerIds()) {
            val player = plugins.player(id) as? SaveFolderHolder ?: continue
            val uri = store.playerSaveFolder(id) ?: continue
            player.bindSaveFolder(uri)
        }
    }

    /**
     * Reads Moonlight's shortcuts when this app is the home app, then puts the
     * selected catalog on the shelf. A failed read leaves the previous pinned
     * list and computer names. The source setting chooses the imported list or
     * the pins. A first discovery opens the confirm sheet unless the user
     * already answered.
     */
    fun refreshMoonlightShelf() {
        pluginLoad.launch {
            val library = plugins.library(MoonlightLibrary.ID) as? MoonlightLibrary ?: return@launch
            restoreMoonlightCatalog(library)
            val read = queryMoonlightShortcuts(this@FoldcadeApp)
            if (read != null) {
                library.replacePinned(read.games)
                moonlightHostNames = read.hostNames
            }
            val tiles = plugins.moonlightShelfGames()
            Shelf.moonlightGames = tiles
            val discovered = read?.games.orEmpty().map(::discoveredMoonlightApp)
            withContext(Dispatchers.Main.immediate) {
                shell.noteShelfChanged()
                shell.maybeOfferMoonlightImport(discovered)
            }
        }
    }

    /**
     * [PinShortcutActivity] accepted Moonlight's Create shortcut for [shortcutId].
     * The game is read back from Android's pinned shortcuts, not from the
     * request, then joins a confirmed import so the Imported list shows it too.
     */
    fun acceptedMoonlightPin(shortcutId: String) {
        val key = moonlightShortcutKey(shortcutId) ?: return
        pluginLoad.launch {
            pluginsReady.await()
            val read = queryMoonlightShortcuts(this@FoldcadeApp)
            if (read != null) moonlightHostNames = read.hostNames
            val app = read?.games?.firstOrNull { it.remoteKey == key }
            if (app != null) {
                withContext(Dispatchers.Main.immediate) { shell.addMoonlightPin(discoveredMoonlightApp(app)) }
            }
            refreshMoonlightShelf()
        }
    }

    /** The sheet falls back to the host UUID when no shortcut named the computer. */
    private fun discoveredMoonlightApp(app: MoonlightApp): MoonlightDiscoveredApp = MoonlightDiscoveredApp(
        hostUuid = app.hostUuid,
        hostName = moonlightHostNames[app.hostUuid.lowercase(Locale.ROOT)].orEmpty(),
        appId = app.appId,
        label = app.label,
    )

    /**
     * Imported list is empty until the user confirms the sheet.
     * Choosing it from the left panel opens that sheet when pins exist.
     */
    private fun reviewMoonlightImport(screen: HostScreen) {
        val library = plugins.library(MoonlightLibrary.ID) as? MoonlightLibrary
        val discovered = library?.pinnedApps().orEmpty().map(::discoveredMoonlightApp)
        if (discovered.isEmpty()) {
            shell.selectImportedList()
            return
        }
        shell.presentMoonlightSheet(discovered, screen)
    }

    private fun restoreMoonlightCatalog(library: MoonlightLibrary) {
        if (store.moonlightImportConfirmed()) {
            val apps = decodeMoonlightApps(store.moonlightImportEncoded()).mapNotNull { stored ->
                moonlightApp(stored.hostUuid, stored.appId, stored.label)
            }
            library.confirmImport(apps)
        }
        val catalog = if (store.moonlightSource() == MoonlightSource.ImportedList) {
            MoonlightCatalog.Imported
        } else {
            MoonlightCatalog.Pinned
        }
        library.useCatalog(catalog)
    }

    fun publishRomm() {
        rommPublish.publish()
    }

    private fun rommCacheRoot(): Path = cacheDir.toPath().resolve("romm")

    /** Saves and the RomM device id. Not the cache: the system may clear that. */
    private fun rommDataRoot(): Path = filesDir.toPath().resolve("romm")

    fun backendLabel(pluginId: String): String = when (pluginId) {
        RommCredentials.PLUGIN_ID -> "RomM"
        else -> pluginId
    }

    /** Adds the picked tree to the folders the library reads, and scans them all. */
    fun useFolder(uri: Uri) {
        store.addFolderTree(uri.toString())
        reloadFolder()
    }

    /** Stops reading [uri]. Its games, and its .steam files, leave the grid. */
    fun forgetFolder(uri: String) {
        store.removeFolderTree(uri)
        try {
            contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Already released. The folder is off the list either way.
        }
        shell.setFolders(libraryFolders())
        reloadFolder()
    }

    /** The folder's own name, such as "roms" for primary:roms. */
    private fun folderLabel(uri: String): String {
        val documentId = try {
            DocumentsContract.getTreeDocumentId(Uri.parse(uri))
        } catch (_: IllegalArgumentException) {
            return uri
        }
        return documentId.substringAfterLast('/').substringAfterLast(':').ifEmpty { documentId }
    }

    /** The folders the library reads, named for the Sources rows. */
    fun libraryFolders(): List<LibraryFolder> = store.folderTrees().map { uri ->
        LibraryFolder(uri, folderLabel(uri))
    }

    /** The GameNative games named by .steam files in the folders. They join the GameNative catalog. */
    @Volatile
    private var steamFileGames: List<CatalogGame> = emptyList()

    private suspend fun useSteamFiles(found: List<SteamShortcut>) {
        val games = found.map { CatalogGame(title = it.title, appId = it.appId, gameSource = GameNativePlayer.DEFAULT_SOURCE) }
        if (games == steamFileGames) return
        steamFileGames = games
        refreshGameNative()
        withContext(Dispatchers.Main.immediate) { shell.noteShelfChanged() }
    }

    fun reloadFolder() {
        libraryRetry = { reloadFolder() }
        val ticket = libraryTicket.incrementAndGet()
        pluginLoad.launch {
            pluginsReady.await()
            if (ticket != libraryTicket.get()) return@launch
            val saved = store.folderTrees()
            withContext(Dispatchers.Main.immediate) { shell.setFolders(libraryFolders()) }
            if (saved.isEmpty()) {
                useSteamFiles(emptyList())
                withContext(Dispatchers.Main.immediate) {
                    if (ticket != libraryTicket.get()) return@withContext
                    // The last folder was forgotten: its games leave the board.
                    shell.dropLibrary(LocalFolderBackend.ID)
                    // RomM's games are on the same board, so no folder is not no library.
                    if (shell.hasLibraryGames()) shell.showCuratedHome() else shell.showNoLibrary()
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
                val forest = FolderForest(contentResolver, saved.map(Uri::parse))
                backend.bindTree(forest.root(), forest::children)
                loadPlatforms(
                    plugins,
                    LocalFolderBackend.ID,
                    folderName = backend::folderName,
                    occupiesBoth = { platformOccupiesBoth(plugins, it) },
                ).also { useSteamFiles(backend.steamShortcuts()) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (fatal: VirtualMachineError) {
                throw fatal
            } catch (failure: Exception) {
                Log.w("Foldcade", "Folder library failed", failure)
                null
            }
            deliverLibrary(ticket, LocalFolderBackend.ID, loaded)
        }
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

    /**
     * Puts RomM's games on the same home board as the folder's, or takes them off
     * once no server is saved. The grid does not change mode while this runs. A
     * server that cannot be reached leaves the board as it was, placed tiles included.
     */
    /**
     * A network coming back syncs the RomM saves that changed while it was gone.
     * It waits until Android has validated the network, so a captive portal or a
     * link with no route yet does not use up the retry.
     */
    private val rommReconnect = object : ConnectivityManager.NetworkCallback() {
        private var validated: Network? = null

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val ready = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (!ready) {
                if (validated == network) validated = null
                return
            }
            if (validated == network) return
            validated = network
            reconnectRomm()
        }

        override fun onLost(network: Network) {
            if (validated == network) validated = null
        }
    }

    /** Connecting starts the RomM plugin's sync of unsynced saves. A failure waits for the next network. */
    private fun reconnectRomm() {
        pluginLoad.launch {
            pluginsReady.await()
            if (store.rommOrigin().isNullOrBlank()) return@launch
            try {
                plugins.connect(RommCredentials.PLUGIN_ID)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (fatal: VirtualMachineError) {
                throw fatal
            } catch (_: Exception) {
                Unit
            }
        }
    }

    fun syncRomm() {
        val ticket = rommTicket.incrementAndGet()
        pluginLoad.launch {
            pluginsReady.await()
            if (ticket != rommTicket.get()) return@launch
            val libraryId = RommCredentials.PLUGIN_ID
            if (store.rommOrigin().isNullOrBlank()) {
                withContext(Dispatchers.Main.immediate) {
                    if (ticket == rommTicket.get()) shell.dropLibrary(libraryId)
                }
                return@launch
            }
            val loaded = try {
                val listed = loadPlatforms(
                    plugins,
                    libraryId,
                    occupiesBoth = { platformOccupiesBoth(plugins, it) },
                )
                when (listed) {
                    is LoadedLibrary.Platforms -> listed to loadEveryGame(libraryId, listed)
                    LoadedLibrary.NoPlatforms -> null to emptyList()
                    LoadedLibrary.Unreachable -> null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (fatal: VirtualMachineError) {
                throw fatal
            } catch (failure: Exception) {
                Log.w("Foldcade", "RomM library failed", failure)
                null
            }
            val (listed, games) = loaded ?: return@launch
            withContext(Dispatchers.Main.immediate) {
                if (ticket != rommTicket.get()) return@withContext
                shell.ingestLibrary(
                    libraryId,
                    games,
                    plugins.platformDefinitions(),
                    listed?.entries.orEmpty().associate { (it.platformId ?: it.id) to it.title },
                )
                // An empty state such as No library yet gives way once there are games to show.
                val waiting = shell.model.libraryGrid && shell.model.emptyGrid != EmptyGrid.Loading
                if (waiting && games.isNotEmpty()) shell.showCuratedHome()
            }
        }
    }

    /**
     * A library with platforms lands on the curated home grid.
     * Games are read here, off the main thread, then the board is updated.
     */
    private suspend fun deliverLibrary(ticket: Int, libraryId: String, loaded: LoadedLibrary?) {
        val games = if (loaded is LoadedLibrary.Platforms) {
            try {
                loadEveryGame(libraryId, loaded)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (fatal: VirtualMachineError) {
                throw fatal
            } catch (failure: Exception) {
                Log.w("Foldcade", "Library games failed", failure)
                null
            }
        } else {
            null
        }
        withContext(Dispatchers.Main.immediate) {
            if (ticket != libraryTicket.get()) return@withContext
            when (loaded) {
                is LoadedLibrary.Platforms -> if (games == null) {
                    shell.showUnreachable(insidePlatform = false)
                } else {
                    shell.ingestLibrary(
                        libraryId,
                        games,
                        plugins.platformDefinitions(),
                        loaded.entries.associate { (it.platformId ?: it.id) to it.title },
                    )
                    shell.showCuratedHome()
                }
                // A folder of .steam files has games, just none of its own.
                LoadedLibrary.NoPlatforms -> if (steamFileGames.isNotEmpty() && libraryId == LocalFolderBackend.ID) {
                    shell.dropLibrary(libraryId)
                    shell.showCuratedHome()
                } else {
                    shell.showNoPlatforms(libraryId)
                }
                LoadedLibrary.Unreachable, null -> shell.showUnreachable(insidePlatform = false)
            }
        }
    }

    private suspend fun loadEveryGame(libraryId: String, loaded: LoadedLibrary.Platforms): List<GridEntry> {
        val backend = plugins.library(libraryId)
        val folderName = (backend as? LocalFolderBackend)?.let { it::folderName } ?: { "" }
        val games = ArrayList<GridEntry>()
        for (entry in loaded.entries) {
            val platformId = entry.platformId ?: continue
            // A platform the listing counted as empty has nothing to fetch.
            if (entry.shortText == "0") continue
            games += loadGames(
                plugins,
                libraryId,
                platformId,
                folderName = folderName,
                occupiesBoth = { platformOccupiesBoth(plugins, it) },
            )
        }
        return games
    }

    private suspend fun showFailure(ticket: Int, insidePlatform: Boolean) {
        withContext(Dispatchers.Main.immediate) {
            if (ticket == libraryTicket.get()) shell.showUnreachable(insidePlatform)
        }
    }
}
