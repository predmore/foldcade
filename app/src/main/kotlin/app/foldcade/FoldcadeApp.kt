package app.foldcade

import app.foldcade.language.AboutCopy
import app.foldcade.language.ArtChoices
import app.foldcade.language.ThemeFile
import app.foldcade.language.MusicTrack
import app.foldcade.language.ThemeProcessExit
import app.foldcade.language.ArtMatch
import app.foldcade.language.ArtKind
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.view.Display
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.CredentialLookup
import app.foldcade.api.plugin.CredentialStore
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameActivity
import app.foldcade.api.plugin.HostedSaveFolder
import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.LibraryInstance
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveFolderHolder
import app.foldcade.api.plugin.SyncOutcome
import app.foldcade.language.EdenSetting
import app.foldcade.language.RetroArchSetting
import app.foldcade.plugins.droiddeck.DroidDeckGame
import app.foldcade.plugins.droiddeck.DroidDeckLibrary
import app.foldcade.plugins.droiddeck.DroidDeckPlayer
import app.foldcade.plugins.droiddeck.droidDeckShortcutKey
import app.foldcade.plugins.gamenative.CatalogGame
import app.foldcade.plugins.gamenative.GameNativeLibrary
import app.foldcade.plugins.gamenative.GameNativePlayer
import app.foldcade.plugins.moonlight.MoonlightArt
import app.foldcade.plugins.moonlight.MoonlightLibrary
import app.foldcade.plugins.moonlight.isMoonlightPosterUri
import app.foldcade.plugins.moonlight.moonlightShortcutKey
import app.foldcade.credentials.AndroidCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.plugins.retroarch.PACKAGES as RETROARCH_PACKAGES
import app.foldcade.plugins.retroarch.RetroArchPlayer
import app.foldcade.plugins.romm.CachePathContentUri
import app.foldcade.plugins.romm.RomFolderDocuments
import app.foldcade.plugins.romm.ROMM_MEDIA_PATH
import app.foldcade.plugins.romm.ROMM_ORIGIN_KEY
import app.foldcade.plugins.romm.RommArtwork
import app.foldcade.plugins.emulators.EDEN_PACKAGES
import app.foldcade.plugins.romm.RommCoverArt
import app.foldcade.plugins.romm.RommFirmware
import app.foldcade.romm.FirmwareSummary
import app.foldcade.romm.RomProgress
import app.foldcade.plugins.romm.SaveBytes
import app.foldcade.plugins.romm.rommOriginAlreadyConfigured
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
import android.service.notification.NotificationListenerService
import app.foldcade.language.FoldNotice
import app.foldcade.language.orderedNotices
import app.foldcade.language.brightnessLevel
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.Motion
import app.foldcade.language.Copy
import app.foldcade.language.EmptyGrid
import app.foldcade.language.GridKind
import app.foldcade.language.LibraryFolder
import app.foldcade.language.SignedInBackend
import app.foldcade.language.builtInTheme
import app.foldcade.localfolder.LocalFolderBackend
import app.foldcade.localfolder.SteamShortcut
import app.foldcade.language.FALLBACK_THEME_ID
import app.foldcade.music.HomeMusic
import app.foldcade.ui.FoldPaint
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.lang.ref.WeakReference
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class FoldcadeApp : Application() {
    lateinit var store: SessionStore
        private set

    /** Waits until the plugin entries are loaded, so a player can be found by id. */
    internal suspend fun awaitPlugins() = pluginsReady.await()

    /** One save flow at a time: a launch's placement, or a return's read-back. */
    internal val saveFlow = Mutex()

    /** A return's save notice, held until the library says how its sync ended. Main thread only. */
    private val returnNotices = ReturnSyncNotices()
    private val saveSetupNotices = SaveSetupNotices()
    private val waitingSaveNotices = WaitingSaveNotices()
    private val unsyncedSaveKeys = mutableMapOf<String, Set<String>>()
    private val saveHealthReads = mutableMapOf<String, Job>()
    private val noticeHandler by lazy { Handler(Looper.getMainLooper()) }

    /** A hand-back of [game] is about to go to [libraryId], whose sync may report before it returns. Main thread only. */
    internal fun expectSaveReturn(libraryId: String, game: Game) = returnNotices.handingBack(libraryId, game)

    /** The hand-back [expectSaveReturn] announced failed. One it held back may be due now. Main thread only. */
    internal fun dropSaveReturn(libraryId: String, game: Game) {
        returnNotices.dropped(libraryId, game)
        returnNotices.expire(System.currentTimeMillis()).forEach(::postNotice)
    }

    /**
     * Posts the notice for a hand-back of [game] that returned [outcome], or holds it
     * for the sync the library finishes in the background. Main thread only.
     */
    internal fun noteSaveReturn(libraryId: String, game: Game, outcome: SyncOutcome) {
        val now = System.currentTimeMillis()
        returnNotices.returned(libraryId, game, outcome, now)?.let(::postNotice) ?: noticeHandler.postDelayed(
            { returnNotices.expire(System.currentTimeMillis()).forEach(::postNotice) },
            RETURN_SYNC_PATIENCE_MS,
        )
    }

    /** Moves library saves in and out of each player's save folder. Replaced files are kept in `save-backups`. */
    internal val playerSaves: PlayerSaves by lazy {
        val documents = AndroidSaveDocuments(contentResolver)
        PlayerSaves(
            documents,
            store,
            filesDir.toPath().resolve("save-backups"),
            filesDir.toPath().resolve("save-handover"),
        ) { uri -> ContentGameFile(contentResolver, uri) }
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
    var themePacks: List<PackagedTheme> = emptyList()
        private set
    var packaged: PackagedTheme? = null
        private set

    /** The packs in files/themes, and the trials that keep a crashing one from staying. */
    internal val themeInstalls: ThemeInstalls by lazy { ThemeInstalls(filesDir) }
    private var themeTrialClock: Job? = null
    private val themeTrialScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** When Home's shown time was last counted towards the trials. Main thread. */
    private var themeTrialMark = 0L
    private var bundledThemeIds: Set<String> = setOf(FALLBACK_THEME_ID)

    /**
     * Installs a downloaded pack, on trial from the next start. This is the theme updater's way in;
     * call it off the main thread.
     */
    internal fun installTheme(input: InputStream): ThemeInstallResult =
        themeInstalls.install(input, bundledThemeIds) { zip ->
            // A pack that extends one already installed is checked over that one, as it will load.
            val parent = zip.file.extends?.let { id -> themePacks.firstOrNull { it.resources.id == id } }
            themeInstallable(zip, (parent ?: packaged)?.resources)
        }.also { if (it is ThemeInstallResult.Installed) themesChanged = true }

    /** Takes an installed pack out for the next start. False for a pack Foldcade ships. */
    internal fun removeTheme(pack: PackagedTheme): Boolean {
        val archive = pack.archive ?: return false
        return themeInstalls.remove(archive).also { if (it) themesChanged = true }
    }

    /** A pack was installed or taken out since this process started; a restart shows it. */
    var themesChanged by mutableStateOf(false)
        private set

    private val themeTurn = java.util.concurrent.atomic.AtomicInteger()

    /**
     * The theme the shell has chosen, with its sounds loaded and its loop written for the player.
     * Only the latest choice reaches [then], so a slow write cannot put an older loop back.
     */
    private fun themeChosen(index: Int, then: (MusicTrack?) -> Unit) {
        val pack = themePacks.getOrNull(index - 1)
        val turn = themeTurn.incrementAndGet()
        scope.launch {
            pack?.sounds?.warm()
            val track = pack?.prepareMusic()
            if (turn == themeTurn.get()) then(track)
        }
    }

    /** Public art hosts over HTTPS only. RomM has its own client. */
    private val publicArt: PublicHttps by lazy { PublicHttps(ART_HOSTS) }

    /** The user's SteamGridDB key, read from the credential store at start. Null when none is saved. */
    @Volatile
    var steamGridDbKey: String? = null

    private val rommCoverArt by lazy { RommCoverArt(publicArt::allows) { rommWirings(store).firstOrNull() } }

    val steamGridDb by lazy { SteamGridDb(PublicArtHttp(publicArt)) { steamGridDbKey } }
    private val artChoiceFile by lazy { File(filesDir, "art/choices.json") }
    var artChoices by mutableStateOf(ArtChoices())
        private set
    var artRevision by mutableStateOf(0)
        private set

    fun chooseArt(id: String, kind: ArtKind, url: String?) {
        saveArtChoices(artChoices.set(id, kind, url))
    }

    fun chooseArtMatch(id: String, match: ArtMatch) {
        saveArtChoices(artChoices.setMatch(id, match))
    }

    private fun saveArtChoices(next: ArtChoices) {
        artChoiceFile.parentFile?.mkdirs()
        val temp = File(artChoiceFile.parentFile, "choices.tmp")
        temp.writeText(next.encode())
        check(temp.renameTo(artChoiceFile))
        artChoices = next
        artRevision++
    }

    /**
     * Art from Steam's CDN for Steam games, then libretro's
     * thumbnails for ROMs, then the connected RomM server's cover search,
     * then SteamGridDB when a key is saved. Results and
     * misses are kept in filesDir, listings and images in cacheDir. Nothing is
     * sent while the Game art setting is off.
     */
    val art: ArtFinder by lazy {
        val http = PublicArtHttp(publicArt)
        val sources = listOf(
            SteamStoreArt(http),
            LibretroThumbnails(http, File(cacheDir, "art/libretro")),
            rommCoverArt,
            steamGridDb,
        )
        ArtFinder({ sources }, ArtIndex(File(filesDir, "art/index.tsv"))) { shell.model.artwork }
    }

    private val artBytes: ArtBytes by lazy { ArtBytes(File(cacheDir, "art/images"), PublicArtHttp(publicArt)) }

    /** Whether SteamGridDB accepts [key]. Null when it cannot be reached. */
    suspend fun checkSteamGridDbKey(key: String): Boolean? = SteamGridDb.accepts(PublicArtHttp(publicArt), key)

    /** Cover addresses: the metadata providers first, then the public sources. */
    val covers: CoverArt by lazy { CoverArt(plugins, art) { artChoices } }

    /** Decodes and caches covers. Public art loads through :net, RomM's through its own client. */
    val images: ImageLoader by lazy {
        val romm = RommArtwork { rommWirings(store) }
        ImageLoader.Builder(this)
            .components {
                add(
                    CoverFetcher.Factory { uri ->
                        when {
                            isMoonlightPosterUri(uri) -> readMoonlightPoster(this@FoldcadeApp, uri)
                            moonlightStreams.isHostArt(uri) -> readHostArt(uri)
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

    /** Both screens' brightness, for the R1 slider. */
    lateinit var brightness: ScreenBrightness
        private set

    /** The external player session opened by the host launch path. */
    val externalPlay = ExternalPlay()

    /** Games left open in their players behind Home. Their tiles read Paused, and A brings them back. */
    internal val runningGames = RunningGames().also { games ->
        games.onChange = { ids -> if (::shell.isInitialized) shell.running = ids }
    }

    /**
     * True while usage access lets Foldcade see what a resume brought to the front
     * (process exits would take DUMP, which no normal app holds). Without it a game
     * may have closed unseen and its player's front door would open the player's own
     * menu, so no game is offered as paused.
     */
    internal var closesVisible = false
        private set(value) {
            field = value
            if (::shell.isInitialized) shell.resumable = value
        }

    /** A library launch held while its player's save folder is asked for. It outlives a recreated Home, as the ask does. */
    internal var pendingSaveAsk: PendingBackendLaunch? = null

    /** The player the hosted-saves ask is about, and the library launch waiting on it, if any. Kept here as the ask is, for either Home to answer. */
    internal var hostedAsk: HostedAsk? = null

    /** The tile Restart asked about, until its dialog closes. The app keeps it, as it keeps the dialog, for either Home to answer. */
    internal var restartAsk: String? = null

    /** The running game on tile [tileId], under whichever id its tile has now. */
    internal fun runningOn(tileId: String): RunningGame? = runningGames.forTile(tileId, shell::tileOf)

    /** The display whose Home last came back through the Home button, until its resume reads it. */
    internal var homeRecalledOn: Int? = null

    /** Looks for the open player's process exit while Home stays behind it. */
    private var playerExitWatch: Job? = null

    /** The quiet screen beside a single-screen app, and how bright it is. */
    lateinit var commandCenter: CommandCenter
        private set
    private val homes = mutableMapOf<Int, WeakReference<FoldcadeHomeActivity>>()
    /** A stopped emulator can recreate its window again. Ownership still gates each showing. */
    internal var panelRecoveryRequested = false
        private set

    internal fun requestPanelRecovery(reclaim: Boolean = true) {
        panelRecoveryRequested = true
        refreshHomeRecovery(reclaim)
    }

    private fun refreshHomeRecovery(reclaim: Boolean = false) {
        homes.values.toList().forEach { it.get()?.refreshRecoveryWindow(reclaim) }
    }

    internal fun companionAlive(displayId: Int): Boolean = homes[displayId]?.get()?.let {
        !it.isDestroyed && !it.isFinishing
    } == true

    /** Android may replace an explicit task with its Home task. Keep one Foldcade surface per display. */
    internal fun homeCreated(activity: FoldcadeHomeActivity) {
        val displayId = activity.display?.displayId ?: return
        val previous = homes.put(displayId, WeakReference(activity))?.get()
        if (activity is CompanionHomeActivity) companionLaunch.arrived()
        if (previous != null && previous !== activity && !previous.isDestroyed) previous.finishAndRemoveTask()
    }

    internal fun homeDestroyed(activity: FoldcadeHomeActivity) {
        val displayId = activity.display?.displayId ?: return
        if (homes[displayId]?.get() === activity) homes.remove(displayId)
    }

    /**
     * The screen that started a download is gone. Ask a Home that is still up
     * to show a save conflict that download left behind.
     */
    internal fun refreshSaveConflictOnSurvivor() {
        val snapshot = homes.mapNotNull { (displayId, ref) ->
            val activity = ref.get() ?: return@mapNotNull null
            SaveConflictHome(
                displayId,
                destroyed = activity.isDestroyed,
                resumed = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
            )
        }
        val displayId = saveConflictSurvivor(starterAlive = false, snapshot) ?: return
        homes[displayId]?.get()?.refreshSaveConflict()
    }

    /** A start for the other screen is on its way, so a second resume does not start another. */
    internal val companionLaunch = CompanionLaunch()

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
    /** Both displays cancel the same pending launch, including its network transfer. */
    internal val launchWork = LaunchWork()

    /** Battery, network, Bluetooth, and the clock. Both screens show the same reading. */
    val deviceStatus: DeviceStatusWatcher by lazy { DeviceStatusWatcher(this, scope) }

    /** Moonlight's computers: how each is, the ones Foldcade paired with, and their apps. */
    internal val moonlightStreams: MoonlightStreams by lazy {
        MoonlightStreams(
            context = this,
            store = store,
            scope = scope,
            shell = { shell },
            library = { plugins.library(MoonlightLibrary.ID) as? MoonlightLibrary },
            refreshShelf = ::putMoonlightOnShelf,
            refreshArt = ::refreshMoonlightArt,
        )
    }
    private val credentialGate = Mutex()
    private val pluginLoad = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pluginsReady = CompletableDeferred<Unit>()

    /** The plugin entries are loaded, so their players are known, long before [pluginsReady]. */
    private val playersLoaded = CompletableDeferred<Unit>()
    private val libraryTicket = AtomicInteger()
    private val rommTicket = AtomicInteger()
    private var rommRefreshJob: Job? = null
    private var libraryRetry: () -> Job = { reloadFolder() }
    private lateinit var rommPublish: RommPublish

    /** Closing the lid turns the screens off. An external game's time stops until its screen is on again. */
    private val playScreen = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = notePlayScreen()
    }

    private val playDisplay = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = onDisplayChanged(displayId)
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == externalPlay.displayId) notePlayScreen()
        }
    }

    /**
     * After a player start, watch that package's process exits. Home coming
     * forward still ends the session the usual way. A crash that never brings
     * Home forward ends it here, so play time stops. Foldcade's own package
     * is the stand-in and is not watched. Without usage access the lookup
     * throws and the watch stops; the session then waits for Home, as before.
     */
    internal fun noteExternalPlayerOpened() {
        playerExitWatch?.cancel()
        playerExitWatch = null
        val watched = externalPlay.packageName
        if (watched == null || !watchPlayerPackage(watched, packageName)) return
        playerExitWatch = scope.launch {
            while (isActive) {
                delay(PLAYER_EXIT_POLL_MS)
                if (externalPlay.sessionId == null || externalPlay.packageName != watched) return@launch
                val exits = try {
                    playerExitTimestamps(watched)
                } catch (_: SecurityException) {
                    return@launch
                }
                val ended = withContext(Dispatchers.Main.immediate) {
                    if (externalPlay.sessionId == null || externalPlay.packageName != watched) return@withContext true
                    if (restartStillExpected(SystemClock.uptimeMillis(), pendingRestart?.until)) return@withContext false
                    val started = externalPlay.startedAtMillis ?: return@withContext true
                    if (!playerDiedAfterStart(started, exits)) return@withContext false
                    externalPlay.endBecausePlayerGone(plays)
                    runningGames.forget(watched)
                    true
                }
                if (ended) return@launch
            }
        }
    }

    /**
     * Reads whether usage access is granted, which decides whether any game reads
     * Paused ([closesVisible]). Without it the games stay noted, so a start in
     * their player still replaces their task.
     */
    internal fun pruneRunningGames() {
        if (runningGames.packages().isEmpty()) return
        val visible = UsageAccess(this).granted()
        if (closesVisible != visible) closesVisible = visible
    }

    /** Process-exit times for [packageName], newest history Android still has. */
    internal fun playerExitTimestamps(packageName: String): List<Long> {
        val manager = getSystemService(ActivityManager::class.java) ?: return emptyList()
        return manager.getHistoricalProcessExitReasons(packageName, 0, 20).map { it.timestamp }
    }

    /** "0.1.2 (230)" from the installed package. The build has no BuildConfig to read it from. */
    private fun installedVersion(): String = try {
        val info = packageManager.getPackageInfo(packageName, 0)
        AboutCopy.version(info.versionName.orEmpty(), info.longVersionCode)
    } catch (_: PackageManager.NameNotFoundException) {
        ""
    }

    /** Reads the external game's screen now. A game opened with the lid already closed starts asleep. */
    internal fun notePlayScreen() {
        val displayId = externalPlay.displayId ?: return
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive ?: true
        val state = getSystemService(DisplayManager::class.java)?.getDisplay(displayId)?.state
        externalPlay.displayOn(plays, interactive && state == Display.STATE_ON)
    }

    private val packageChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_CHANGED,
                -> {
                    reloadInstalledApps()
                    val changed = intent.data?.schemeSpecificPart
                    if (changed in GameNativePlayer.PACKAGES || changed == DroidDeckPlayer.PACKAGE) rerouteSteamFiles()
                    if (changed in RETROARCH_PACKAGES) refreshRetroArchCores()
                    if (changed in EDEN_PACKAGES) refreshEdenSetting()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // RestartActivity's process only brings Home back. It must not load packs or count exits.
        if (getProcessName() != packageName) return
        store = SessionStore(getSharedPreferences("foldcade", MODE_PRIVATE))
        runningGames.restore(store.playersHoldingGames())
        runningGames.onPlayersChange = store::setPlayersHoldingGames
        buttonPromptFirstSession = !store.buttonPromptSeenLaunch()
        if (buttonPromptFirstSession) store.setButtonPromptSeenLaunch()
        credentials = AndroidCredentialStore(this)
        plugins = PluginHost(Dispatchers.IO, credentials)
        music = HomeMusic(this, store) { slot ->
            ::shell.isInitialized && themePacks.getOrNull(shell.model.themeIndex - 1)?.sounds?.has(slot) == true
        }
        store.afterSessionChanged = {
            music.onSessionChanged()
            refreshHomeRecovery()
        }
        // Before the packs load, so a pack that crashed Foldcade twice is already back out of the way.
        val themeRollbacks = try {
            themeInstalls.begin(processExits())
        } catch (error: Exception) { Log.w("FoldcadeTheme", "Theme trials skipped", error); emptyList() }
        val manifests = mutableMapOf<String, ThemeFile>()
        themePacks = PackagedTheme.loadAll(this) { archive, manifest ->
            manifests[archive] = manifest
            try { themeInstalls.read(archive, manifest) } catch (error: Exception) { Log.w("FoldcadeTheme", "Theme trials not saved", error) }
        }
        bundledThemeIds = setOf(FALLBACK_THEME_ID) + themePacks.filter { it.archive == null }.map { it.resources.id }
        packaged = themePacks.firstOrNull { it.resources.id == FALLBACK_THEME_ID }
        val names = listOf(Copy.builtIn) + themePacks.map { it.name }
        val ids = listOf("builtin") + themePacks.map { it.resources.id }
        val sceneChoices = themePacks.flatMap { pack -> pack.resources.scenes.map { (id, scene) ->
            app.foldcade.language.BackgroundChoice(id, if (pack.resources.id == "afterglow") scene.name else "${pack.name} · ${scene.name}")
        } } + app.foldcade.language.BackgroundChoice(BackgroundMotion.Off, Copy.motionOff)
        val motions = listOf(BackgroundMotion.Off) + themePacks.map { pack ->
            if (pack.resources.file.version == 1 && pack.resources.scenes.isEmpty())
                app.foldcade.language.resolveBackgroundChoice(pack.resources.file.backgroundMotion, sceneChoices, BackgroundMotion.Off)
            else pack.backgroundMotion
        }
        val themeTrackList = listOf(null) + themePacks.map { it.music }
        val initialTheme = store.themeIndex(ids, names)
        themeChosen(initialTheme, music::setThemeChoice)
        plays = PlaySessions.open(File(filesDir, "play-sessions/events.log"))
        val sliderTick = SliderTick()
        plays.packageOf = { gameId ->
            val platform = Shelf.games.firstOrNull { it.id == gameId }?.platformId
            val player = platform?.let(::playerFor)
            player?.packageNames?.let { names -> names.firstOrNull(::packageInstalled) ?: names.firstOrNull() }
        }
        artChoices = ArtChoices.decode(artChoiceFile.takeIf { it.isFile }?.readText())
        shell = ShellController(
            store,
            plugins,
            music::apply,
            onArtApply = ::chooseArt,
            savedArtMatch = { artChoices.matches[it] },
            onArtMatch = ::chooseArtMatch,
            onTheme = { index -> themeChosen(index, music::setThemeChoice) },
            themeNames = names,
            themeMotions = motions,
            themeIds = ids,
            backgroundChoices = sceneChoices,
            cue = { index, slot ->
                themePacks.getOrNull(index - 1)?.sounds?.play(slot) == true
            },
            bareTick = sliderTick::play,
            homeTracks = music.homeTracks(),
            themeTracks = themeTrackList,
            lastPlayedMillis = plays::lastPlayedMillis,
            playMillis = { plays.shown(it).activeMillis },
            onMoonlightRemoved = ::unpinMoonlightGame,
            onDroidDeckRemoved = ::unpinDroidDeckGame,
            onBrightness = { position -> brightness.write(brightnessLevel(position)) },
            onSameGames = { groups -> saveArtChoices(artChoices.adopt(groups)) },
            appVersion = installedVersion(),
            onSaveHealthChanged = { players ->
                saveSetupNotices.update(players)
                publishNotices()
                updateWaitingSaveNotices()
            },
        )
        // Loading ends once the shell has resolved the choices against the packs.
        try { themeInstalls.loaded(manifests, themeArchivesInUse()) } catch (error: Exception) { Log.w("FoldcadeTheme", "Theme trials not saved", error) }
        // A pack chosen in Settings is in use from that moment, so a crash right after counts against it.
        themeTrialScope.launch {
            snapshotFlow { themeArchivesInUse() }.collect { inUse ->
                if (themeInstalls.ledger.themes.any { it.trial != null }) saveThemeTrial(inUse, 0)
            }
        }
        themeRollbacks.forEach { postNotice(themeRollbackNotice(it)) }
        // A game several libraries have is one tile, and its copies' play counts on it.
        plays.historyIds = shell::historyIds
        plays.tileOf = shell::tileOf
        shell.playDaySessions = { weeksBack, day ->
            app.foldcade.language.playDaySessions(plays.spans(), System.currentTimeMillis(), java.time.ZoneId.systemDefault(), weeksBack, day)
                .map { it.sessionId }
        }
        // Moving art is saved apart from the tiles, so a stop between the two is put right here.
        artChoices.adopt(shell.sameGroups()).takeIf { it != artChoices }?.let(::saveArtChoices)
        plays.onChanged = shell::notePlayChanged
        commandCenter = CommandCenter(
            store = store,
            scope = scope,
            load = { record ->
                awaitPlugins()
                plugins.metadataIds().firstNotNullOfOrNull { id -> plugins.fetchAchievements(id, record) }
            },
            showHome = shell::goHome,
            busy = shell::holdsCommandCenter,
        )
        brightness = ScreenBrightness(this)
        // The Thor's own quick settings, or Android's, can move it too. The slider follows.
        brightness.observe(::refreshBrightness)
        refreshBrightness()
        refreshNoticeAccess()
        // Libraries report from their own threads. The shell hears them in order on the main one.
        val main = Handler(Looper.getMainLooper())
        plugins.activityListener = { libraryId, remoteKey, reported ->
            main.post {
                if (reported is GameActivity.Synced) {
                    returnNotices.synced(libraryId, remoteKey, reported.result, System.currentTimeMillis())?.let(::postNotice)
                }
                val until = shell.noteActivity(libraryId, remoteKey, reported, System.currentTimeMillis())
                if (reported is GameActivity.Synced || reported == GameActivity.SyncingSaves || reported == GameActivity.Idle) {
                    refreshUnsyncedSaves(libraryId)
                }
                if (until != null) {
                    main.postDelayed(
                        { shell.expireActivity(libraryId, remoteKey, until) },
                        Motion.durationSyncMark.toLong(),
                    )
                }
            }
        }
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
                    contentAuthority = rommAuthority,
                    cachePath = rommCachePath(request.cacheRoot),
                    folderAuthority = rommFolderAuthority,
                    dataRoot = request.dataRoot,
                    artCacheRoot = rommPrivateRoot(),
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
        registerReceiver(
            playScreen,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            RECEIVER_NOT_EXPORTED,
        )
        getSystemService(DisplayManager::class.java)?.registerDisplayListener(playDisplay, main)
        getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(rommReconnect)
        refreshCredentials()
        reloadInstalledApps()
        pluginLoad.launch {
            try {
                try {
                    plugins.load(classLoader)
                } finally {
                    playersLoaded.complete(Unit)
                }
                restorePlayerSaveFolders()
                readRetroArchCores()
                moveRommDownloadsToSharedStorage()
                publishRomm()
                refreshGameNative()
                restoreMoonlightPins()
                refreshMoonlightShelf()
                restoreDroidDeckPins()
                refreshDroidDeckShelf()
                val notes = readPlayerFolderNotes()
                withContext(Dispatchers.Main.immediate) {
                    refreshEdenSetting()
                    shell.setSaveSync(notes.sync, notes.setup)
                    shell.refreshPlayerSaves()
                    shell.noteShelfChanged()
                }
                reloadInstalledApps()
                refreshUnsyncedSaves()
            } finally {
                playersLoaded.complete(Unit)
                if (!pluginsReady.isCompleted) pluginsReady.complete(Unit)
            }
            if (store.folderTrees().isNotEmpty()) reloadFolder()
            syncRomm()
            try {
                plugins.reopenDeferredSaveConflicts()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Unit
            }
            plugins.addSaveConflictListener {
                scope.launch(Dispatchers.Main.immediate) { onSaveConflicts?.invoke() }
            }
            withContext(Dispatchers.Main.immediate) { onSaveConflicts?.invoke() }
        }
    }

    /** The activity refreshes the bottom screen when a library kept both saves. */
    var onSaveConflicts: (() -> Unit)? = null

    /** True while a game is being started, so a conflict waits until Foldcade is in front again. */
    @Volatile
    var suppressSaveConflict: Boolean = false

    private var androidNotices: List<FoldNotice> = emptyList()
    private var foldcadeNotices: List<FoldNotice> = emptyList()

    /** The device's notifications, from [NoticeListener]. Main thread. */
    fun setAndroidNotices(notices: List<FoldNotice>) {
        androidNotices = notices
        publishNotices()
    }

    /** A notice of Foldcade's own. One with the same id replaces it. */
    fun postNotice(notice: FoldNotice) {
        foldcadeNotices = foldcadeNotices.filter { it.id != notice.id } + notice
        publishNotices()
    }

    /** Y on a notice. Android's go through the listener, which then publishes the rest. */
    fun dismissNotice(id: String) {
        saveSetupNotices.dismiss(id)
        if (id.startsWith("foldcade:save-setup:")) {
            publishNotices()
            return
        }
        if (foldcadeNotices.any { it.id == id }) {
            foldcadeNotices = foldcadeNotices.filter { it.id != id }
            publishNotices()
            return
        }
        NoticeListener.connected?.cancelNotification(id)
    }

    fun clearNotices() {
        saveSetupNotices.clear()
        foldcadeNotices = foldcadeNotices.filter { !it.dismissable }
        publishNotices()
        NoticeListener.connected?.cancelAllNotifications()
    }

    /**
     * Whether R1 asks for notification access. Right after the user grants it,
     * Android may not have bound the listener yet, so this asks it to.
     */
    fun refreshNoticeAccess() {
        val granted = NoticeListener.granted(this)
        shell.setNoticeAccess(ask = !granted)
        if (granted && NoticeListener.connected == null) {
            NotificationListenerService.requestRebind(NoticeListener.component(this))
        }
    }

    private fun publishNotices() {
        shell.setNotices(orderedNotices(foldcadeNotices + androidNotices + saveSetupNotices.notices(System.currentTimeMillis())))
    }

    /** Reads the level and the write access again. Resume calls this after Android's access page. */
    fun refreshBrightness() {
        shell.setBrightness(brightness.level(), brightness.allowed())
    }

    /** MAIN / LAUNCHER list. Package add, remove, and change call this again. */
    fun reloadInstalledApps() {
        scope.launch {
            val apps = InstalledAppCatalog(packageManager, packageName).list()
            val players = plugins.playerPackageNames()
            // Checked as a launch checks them: an emulator may hide its launcher entry.
            val installedPlayers = players.filterTo(HashSet(), ::packageInstalled)
            withContext(Dispatchers.Main.immediate) {
                if (::shell.isInitialized) shell.setInstalledApps(apps, players, installedPlayers)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::music.isInitialized) music.onTrimMemory(level)
    }

    private val composedPaints = mutableMapOf<Pair<Int, Int?>, FoldPaint>()

    fun paintFor(themeIndex: Int): FoldPaint = composedPaints.getOrPut(themeIndex to shell.model.iconThemeIndex) {
        val base = themePacks.getOrNull(themeIndex - 1)
        val override = shell.model.iconThemeIndex?.let { themePacks.getOrNull(it - 1) }
        val composition = app.foldcade.language.ThemeComposition(base?.resources, override?.resources, packaged?.resources)
        val slots = (composition.base?.icons?.keys.orEmpty() + composition.iconProvider?.icons?.keys.orEmpty() + composition.fallback?.icons?.keys.orEmpty()).toSet()
        val paint = base?.paint ?: FoldPaint(builtInTheme())
        val icons = if (shell.model.iconThemeIndex == 0) emptyMap() else slots.associateWith(composition::iconCandidates)
        paint.copy(icons = icons)
    }

    fun backgroundFor(motion: BackgroundMotion): app.foldcade.language.ThemeScene? =
        themePacks.firstNotNullOfOrNull { it.resources.scenes[motion] }

    fun refreshCredentials() {
        scope.launch {
            credentialGate.withLock { loadSignedIn() }
        }
    }

    suspend fun editCredentials(block: suspend () -> Unit) {
        credentialGate.withLock { block() }
    }

    private suspend fun loadSignedIn() {
        val romm = readRommRecovery(credentials, store.rommServers())
        val ids = try {
            credentials.pluginIds()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptySet()
        }
        // The art key is not a library. RomM connections have their own rows under one plugin id.
        val others = ids
            .filter { it != SteamGridDb.CREDENTIAL_ID && it != RommCredentials.PLUGIN_ID }
            .map { SignedInBackend(it, backendLabel(it)) }
        val artKey = lookupOrUnreadable(credentials, SteamGridDb.CREDENTIAL_ID, SteamGridDb.CREDENTIAL_KEY)
        steamGridDbKey = ((artKey as? CredentialLookup.Present)?.credential as? Credential.ApiToken)?.value
        val recovery = romm.copy(backends = romm.backends + others)
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
    private suspend fun refreshGameNative() {
        val library = plugins.library(GameNativeLibrary.ID) as? GameNativeLibrary ?: return
        Shelf.gameNativeInstalled = gameNativePackagePresent()
        try {
            refreshGameNativeCatalog(this, library, File(filesDir, "gamenative/catalog.txt"), steamFileGames)
            withContext(Dispatchers.Main.immediate) {
                shell.settleGameNative()
                shell.noteShelfChanged()
            }
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
            val player = plugins.player(id) ?: continue
            if (player !is SaveFolderHolder) continue
            val uri = store.playerSaveFolder(player.saveFolderId()) ?: continue
            player.bindSaveFolder(uri)
        }
        refreshHostedFolders()
        contentResolver.notifyChange(DocumentsContract.buildRootsUri(hostedAuthority), null, false)
    }

    /** The manifest's [HostedSavesProvider] authority. */
    val hostedAuthority: String get() = "$packageName${HostedSavesProvider.AUTHORITY_SUFFIX}"

    /** The save folders [HostedSavesProvider] serves: one for each player that keeps its saves there. */
    internal val hostedFolders: HostedFolders by lazy {
        HostedFolders(filesDir.resolve("hosted-saves")) {
            // A player can ask for its saves while Foldcade is still starting, as after a reboot. Its folder
            // is known once the plugins are, which takes a moment and no network. Binder threads only wait.
            if (!playersLoaded.isCompleted) runBlocking { withTimeoutOrNull(PLAYERS_LOAD_WAIT_MS) { playersLoaded.await() } }
            plugins.playerIds().mapNotNull { id ->
                (plugins.player(id) as? HostedSaveFolder)?.let { HostedFolder(id, it.hostedFolderTitle) }
            }
        }
    }

    /**
     * Reads whether each player's saves sync, off the main thread, and updates
     * Settings. A card image is large, so this is not part of drawing a frame.
     */
    fun refreshSaveSync() {
        if (!::plugins.isInitialized || !::shell.isInitialized) return
        pluginLoad.launch {
            val notes = readPlayerFolderNotes()
            withContext(Dispatchers.Main.immediate) {
                shell.setSaveSync(notes.sync, notes.setup)
                shell.refreshPlayerSaves()
            }
        }
    }

    internal fun noteSaveLaunch(route: GameRoute) {
        shell.savePlayer(route.play.platformId)?.let { saveSetupNotices.launch(shell.saveNoticePlayerId(it.id)) }
        publishNotices()
    }

    /** Superseded reads are cancelled on main, so only the latest completed snapshot publishes. */
    private fun refreshUnsyncedSaves(libraryId: String? = null) {
        scope.launch(Dispatchers.Main.immediate) {
            val ids = libraryId?.let(::listOf) ?: plugins.libraryIds()
            for (id in ids) {
                saveHealthReads.remove(id)?.cancel()
                saveHealthReads[id] = launch {
                    val keys = try {
                        plugins.unsyncedSaves(id)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        return@launch // Failed reads keep the last known waiting set.
                    }
                    unsyncedSaveKeys[id] = keys
                    shell.setUnsyncedSaves(id, keys)
                    updateWaitingSaveNotices()
                }
            }
        }
    }

    private val waitingSaveExpiry = Runnable { updateWaitingSaveNotices() }

    private fun updateWaitingSaveNotices() {
        val games = unsyncedSaveKeys.flatMap { (libraryId, keys) ->
            keys.mapNotNull { key -> shell.saveGame(libraryId, key) }
        }
        waitingSaveNotices.update(games, System.currentTimeMillis()).forEach(::postNotice)
        val waitingIds = games.map { "foldcade:save:${it.backendId}:${it.remoteKey}" }.toSet()
        val settled = foldcadeNotices.filter { it.title == Copy.offlineSaveTitle &&
            it.id !in waitingIds && unsyncedSaveKeys.keys.any { library -> it.id.startsWith("foldcade:save:$library:") } }
        if (settled.isNotEmpty()) {
            foldcadeNotices = foldcadeNotices - settled.toSet()
            publishNotices()
        }
        noticeHandler.removeCallbacks(waitingSaveExpiry)
        waitingSaveNotices.nextDueMillis()?.let { due ->
            noticeHandler.postDelayed(waitingSaveExpiry, (due - System.currentTimeMillis()).coerceAtLeast(1L))
        }
    }

    private fun readPlayerFolderNotes(): PlayerFolderNotes {
        val sync = linkedMapOf<String, app.foldcade.api.plugin.SaveSyncState>()
        val setup = linkedMapOf<String, String>()
        for (id in plugins.playerIds()) {
            val player = plugins.player(id) ?: continue
            if (player !is app.foldcade.api.plugin.SaveFolderHolder) continue
            val uri = store.playerSaveFolder(player.saveFolderId()) ?: continue
            playerSaves.saveSync(player, uri)?.let { sync[player.id] = it }
            playerSaves.setupSteps(player, uri)?.let { setup[player.id] = it }
        }
        return PlayerFolderNotes(sync, setup)
    }

    private data class PlayerFolderNotes(
        val sync: Map<String, app.foldcade.api.plugin.SaveSyncState>,
        val setup: Map<String, String>,
    )

    /**
     * Hands each player its hosted folder, and whether it has been given it: the
     * player itself holds write access to that folder, from the grant Android's
     * picker gave it. Another app given the folder, such as a file manager, does
     * not count. Cheap; call it before reading whether a player has it.
     */
    fun refreshHostedFolders() {
        for (id in plugins.playerIds()) {
            val player = plugins.player(id) as? HostedSaveFolder ?: continue
            val uri = DocumentsContract.buildTreeDocumentUri(hostedAuthority, HostedFolders.rootId(id))
            val inUse = (player as? Player)?.packageNames.orEmpty().any { name -> holdsWrite(uri, name) }
            player.bindHostedFolder(uri.toString(), inUse)
        }
    }

    /** True when installed package [name] may write [uri], which a grant on it allows. */
    private fun holdsWrite(uri: Uri, name: String): Boolean = try {
        val uid = packageManager.getApplicationInfo(name, 0).uid
        // Any pid but the system's: Android answers for the uid, from the grants it holds.
        checkUriPermission(uri, -1, uid, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    /**
     * The player a game on [platformId] launches with, see [rankPlayers]. RetroArch
     * counts as runnable only with a core Foldcade knows is installed.
     */
    fun playerFor(platformId: String): Player? = preferredPlayer(
        plugins.playersFor(platformId),
        runnable = { runnableWith(it, it.packageNames.firstOrNull(::packageInstalled)) },
        rank = ::playerRank,
        installed = ::packageInstalled,
    )

    /** Every player for [platformId], in the order [playerFor] tries them. */
    fun rankedPlayers(platformId: String): List<Player> = rankPlayers(
        plugins.playersFor(platformId),
        installed = { player -> player.packageNames.any(::packageInstalled) },
        runnable = { runnableWith(it, it.packageNames.firstOrNull(::packageInstalled)) },
        rank = ::playerRank,
    )

    internal val retroArchCoreReader: RetroArchCoreReader by lazy { RetroArchCoreReader(this, store::retroArchCoresFolder) }

    /** The granted Eden folder, per installed package. */
    internal val edenFolderReader: EdenFolderReader by lazy { EdenFolderReader(this, store::edenFolder) }

    /**
     * The Eden package Foldcade added files to that has not started since.
     * Set from the install's worker as each file lands. Cleared when the device
     * boot count changes, or when Eden is in the stopped state Force stop sets.
     */
    internal fun edenRestartPending(): EdenRestartPending? = store.edenRestartPending()

    /** Records [Settings.Global.BOOT_COUNT], or -1 when Android does not report one. */
    internal fun noteEdenRestartPending(packageName: String) {
        store.setEdenRestartPending(packageName, deviceBootCount())
    }

    internal fun clearEdenRestartPending() {
        store.clearEdenRestartPending()
    }

    private fun deviceBootCount(): Int =
        Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)

    /** Switch keys and firmware on every signed-in RomM server, in the order they were added. */
    internal val rommFirmware: RommFirmware by lazy { RommFirmware { rommWirings(store) } }

    /**
     * Adds the keys and firmware the granted Eden folder is missing.
     * One install at a time. The cached zip lives under `cache/eden-firmware`.
     */
    internal val edenInstaller: EdenInstaller by lazy {
        EdenInstaller(
            openTree = { packageName ->
                val tree = edenFolderReader.granted(packageName) ?: return@EdenInstaller null
                EdenDocumentTree(contentResolver, tree)
            },
            source = object : EdenFileSource {
                override suspend fun servers(): List<EdenServerFiles> = rommFirmware.listSwitch().map { server ->
                    EdenServerFiles(server.origin, server.files, server.failed)
                }

                override suspend fun download(
                    origin: String,
                    file: FirmwareSummary,
                    target: Path,
                    progress: ((RomProgress) -> Unit)?,
                ) {
                    rommFirmware.download(origin, file, target, progress)
                }
            },
            cacheDirectory = {
                val dir = File(cacheDir, "eden-firmware")
                dir.mkdirs()
                dir.toPath()
            },
            refusedFirmware = store::refusedEdenFirmware,
            rememberRefusedFirmware = store::rememberRefusedEdenFirmware,
        )
    }

    /** Settings › Emulators › Eden keys and firmware. Null while Eden is not installed. */
    internal fun refreshEdenSetting() {
        val installed = EDEN_PACKAGES.firstOrNull(::packageInstalled)
        val setting = installed?.let { EdenSetting(fromRomm = edenFolderReader.granted(it) != null) }
        shell.setEden(setting)
    }
    private val retroArchRead = Mutex()

    /** Any RetroArch player. They share one view of the cores. */
    fun retroArchPlayer(): RetroArchPlayer? =
        plugins.playerIds().firstNotNullOfOrNull { plugins.player(it) as? RetroArchPlayer }

    /** Reads RetroArch's cores again, off the main thread. */
    fun refreshRetroArchCores(): Job = pluginLoad.launch { readRetroArchCores() }

    @Volatile
    private var retroArchReadAt = 0L

    /**
     * [refreshRetroArchCores] when Foldcade comes back to the front, at most once a minute.
     * Listing RetroArch's folder starts its process, so not on every return.
     * A RetroArch launch reads them again regardless.
     */
    fun refreshRetroArchCoresOnReturn() {
        val now = SystemClock.elapsedRealtime()
        if (retroArchReadAt != 0L && now - retroArchReadAt < RETROARCH_RETURN_READ_MS) return
        retroArchReadAt = now
        refreshRetroArchCores()
    }

    /**
     * Which cores each installed RetroArch has, for the players and for Settings.
     * Runs at start, when a RetroArch package changes, when Foldcade comes back
     * to the front, and before each RetroArch launch.
     */
    suspend fun readRetroArchCores() = retroArchRead.withLock {
        val player = retroArchPlayer() ?: return@withLock
        retroArchReadAt = SystemClock.elapsedRealtime()
        val installed = RETROARCH_PACKAGES.filter(::packageInstalled)
        val byPackage = installed.associateWith { retroArchCoreReader.read(it) }
        player.cores.update(byPackage)
        val active = player.cores.activePackage()
        val setting = active?.let { RetroArchSetting(cores = byPackage[it]?.size) }
        withContext(Dispatchers.Main.immediate) { shell.setRetroArch(setting) }
    }

    /**
     * The intent to start for [player]. A player that cannot switch games in
     * place ([LaunchFlag.ReplaceRunning]) may run this game already, under
     * either of the two ways RetroArch can name it. Normally the start uses the
     * one it was last started with, so the game resumes. When a save was written
     * since, it uses the other one, so the player starts fresh and loads it.
     */
    fun startIntent(player: Player, intent: PlayerIntent): PlayerIntent {
        if (LaunchFlag.ReplaceRunning !in intent.flags) return intent
        val other = (player as? RetroArchPlayer)?.freshStart(intent) ?: return intent
        return chooseStart(intent, other, store.lastPlayerStart(intent.packageName), store.saveAwaitsLoad(player.saveFolderId()))
    }

    /**
     * Records [intent] as started for [player]. True when it asks for
     * [LaunchFlag.ReplaceRunning] and is not the last one started for its
     * package, so the player may quit when it arrives. RetroArch 1.22 does:
     * handed another game, it quits and expects its launcher to start it again.
     */
    fun noteStarted(player: Player, intent: PlayerIntent): Boolean {
        store.setSaveAwaitsLoad(player.saveFolderId(), false)
        if (LaunchFlag.ReplaceRunning !in intent.flags) return false
        val key = startKey(intent)
        val replacing = store.lastPlayerStart(intent.packageName) != key
        store.setLastPlayerStart(intent.packageName, key)
        return replacing
    }

    /** A start to repeat if Foldcade comes back before [PendingRestart.until], see [noteStarted]. */
    @Volatile
    var pendingRestart: PendingRestart? = null

    /** Reads Moonlight's pinned games again and puts them on the shelf. */
    fun refreshMoonlightShelf() {
        pluginLoad.launch { readMoonlightShelf() }
    }

    /**
     * Reads Moonlight's shortcuts when this app is the home app and puts every
     * enabled pinned game on the shelf, and so on Home. A failed read leaves the
     * previous list. Only a read that ran settles the Moonlight tiles, so one
     * missing from the shelf is taken off Home only once Android has said so.
     */
    private suspend fun readMoonlightShelf() {
        val library = plugins.library(MoonlightLibrary.ID) as? MoonlightLibrary ?: return
        val read = queryMoonlightShortcuts(this)
        if (read != null) {
            library.replacePinned(read.games)
            store.setMoonlightPins(read.games)
            store.setMoonlightHostNames(read.hostNames)
            moonlightStreams.hosts.knowNames(read.hostNames)
        }
        Shelf.moonlightGames = plugins.moonlightShelfGames()
        withContext(Dispatchers.Main.immediate) {
            if (read != null) shell.settleMoonlight()
            shell.noteShelfChanged()
        }
    }

    /** The last read fills the shelf at start, before Android answers. It never settles. */
    private suspend fun restoreMoonlightPins() {
        val library = plugins.library(MoonlightLibrary.ID) as? MoonlightLibrary ?: return
        library.replacePinned(store.moonlightPins())
        (plugins.metadata(MoonlightArt.ID) as? MoonlightArt)?.poster = moonlightStreams::poster
        moonlightStreams.restore(store.moonlightHostNames())
        Shelf.moonlightGames = plugins.moonlightShelfGames()
    }

    /**
     * The Moonlight catalog changed without a shortcut read: a paired computer listed its
     * apps, or one was forgotten. That is a live answer, as a shortcut read is, so a game it
     * dropped leaves Home even while Foldcade is not the home app and cannot read shortcuts.
     */
    private suspend fun putMoonlightOnShelf() {
        Shelf.moonlightGames = plugins.moonlightShelfGames()
        withContext(Dispatchers.Main.immediate) {
            shell.settleMoonlight()
            shell.noteShelfChanged()
        }
    }

    /** Tiles of the computer [hostKey] ask for their art again. Main thread. */
    private fun refreshMoonlightArt(hostKey: String) {
        (plugins.metadata(MoonlightArt.ID) as? MoonlightArt)?.forgetHost(hostKey)
        covers.forget(MoonlightLibrary.ID) { it.startsWith("$hostKey|") }
        artRevision++
    }

    /** A paired computer's box art, as [MoonlightStreams.poster] saved it. */
    private suspend fun readHostArt(uri: String): ByteArray? = withContext(Dispatchers.IO) {
        android.net.Uri.parse(uri).path?.let(::File)?.takeIf { it.isFile && it.length() <= 8L * 1024 * 1024 }?.readBytes()
    }

    /** Settings › Library: pair with Moonlight's computer [key]. */
    fun pairMoonlightHost(key: String, screen: app.foldcade.language.HostScreen) = moonlightStreams.pair(key, screen)

    fun cancelMoonlightPairing() = moonlightStreams.cancelPairing()

    fun forgetMoonlightHost(key: String) = moonlightStreams.forget(key)

    /**
     * [PinShortcutActivity] accepted Moonlight's Create shortcut for [shortcutId].
     * The game is read back from Android's pinned shortcuts, not from the
     * request, then placed on Home.
     */
    fun acceptedMoonlightPin(shortcutId: String) {
        val key = moonlightShortcutKey(shortcutId) ?: return
        pluginLoad.launch {
            pluginsReady.await()
            readMoonlightShelf()
            withContext(Dispatchers.Main.immediate) { shell.placeMoonlightPin(key) }
        }
    }

    /** Remove from Home on a Moonlight game drops Foldcade's pin for it. */
    private fun unpinMoonlightGame(remoteKey: String) {
        pluginLoad.launch {
            if (unpinMoonlightShortcut(this@FoldcadeApp, remoteKey)) readMoonlightShelf()
        }
    }

    /** DroidDeck's pinned games from the last shortcut read, or the saved list before one ran. */
    @Volatile
    private var droidDeckPinned: List<DroidDeckGame> = emptyList()

    /** DroidDeck games named by .steam files in the folders. */
    @Volatile
    private var droidDeckFileGames: List<DroidDeckGame> = emptyList()

    @Volatile
    private var droidDeckShortcutsRead = false

    /** Reads DroidDeck's pinned games again and puts them on the shelf. */
    fun refreshDroidDeckShelf() {
        pluginLoad.launch { readDroidDeckShelf() }
    }

    /**
     * Reads DroidDeck's shortcuts when this app is the home app. A failed read
     * leaves the previous list. Only a read that ran can settle the DroidDeck
     * tiles, so one missing from the shelf is taken off Home only once Android
     * and the folder scan have both said so.
     */
    private suspend fun readDroidDeckShelf() {
        if (plugins.library(DroidDeckLibrary.ID) !is DroidDeckLibrary) return
        val read = queryDroidDeckShortcuts(this)
        if (read != null) {
            droidDeckPinned = read
            droidDeckShortcutsRead = true
            store.setDroidDeckPins(read)
        }
        publishDroidDeck()
    }

    /** The last read fills the shelf at start, before Android answers. It never settles. */
    private suspend fun restoreDroidDeckPins() {
        droidDeckPinned = store.droidDeckPins()
        publishDroidDeck()
    }

    /**
     * Pinned games first, so a pinned game keeps DroidDeck's whole name over
     * the shortened file name. Settles once both sources have answered.
     */
    private suspend fun publishDroidDeck() {
        val library = plugins.library(DroidDeckLibrary.ID) as? DroidDeckLibrary ?: return
        library.replaceGames(droidDeckPinned + droidDeckFileGames)
        Shelf.droidDeckInstalled = packageInstalled(DroidDeckPlayer.PACKAGE)
        Shelf.droidDeckGames = plugins.droidDeckShelfGames()
        // With no folder there is no scan to wait for.
        val settled = droidDeckShortcutsRead && (steamFilesRead || store.folderTrees().isEmpty())
        withContext(Dispatchers.Main.immediate) {
            if (settled) shell.settleDroidDeck()
            shell.noteShelfChanged()
        }
    }

    /**
     * [PinShortcutActivity] accepted DroidDeck's Add to home screen for [shortcutId].
     * The game is read back from Android's pinned shortcuts, then placed on Home.
     */
    fun acceptedDroidDeckPin(shortcutId: String) {
        val key = droidDeckShortcutKey(shortcutId) ?: return
        pluginLoad.launch {
            pluginsReady.await()
            readDroidDeckShelf()
            withContext(Dispatchers.Main.immediate) { shell.placeDroidDeckPin(Shelf.DROIDDECK_PREFIX + key) }
        }
    }

    /** Remove from Home on a DroidDeck game drops Foldcade's pin for it. A synced file stays. */
    private fun unpinDroidDeckGame(remoteKey: String) {
        pluginLoad.launch {
            if (unpinDroidDeckShortcut(this@FoldcadeApp, remoteKey)) readDroidDeckShelf()
        }
    }

    fun publishRomm() {
        rommPublish.publish()
        configureSavedRomm()
        pluginLoad.launch { rommCoverArt.disconnect() }
    }

    /**
     * Gives each saved RomM server its origin. Editing an origin calls this again
     * and then [syncRomm], which reconnects. Shared roots stay on [app.foldcade.plugins.romm.RommPlugins].
     */
    private fun configureSavedRomm() {
        if (plugins.library(RommCredentials.PLUGIN_ID) == null) return
        val origin = store.rommOrigin()
        if (!origin.isNullOrBlank()) {
            plugins.configure(RommCredentials.PLUGIN_ID, mapOf(ROMM_ORIGIN_KEY to origin))
        }
        val others = store.rommInstances()
        for ((id, extra) in others) {
            val taken = listOfNotNull(origin) + others.filter { it.first != id }.map { it.second }
            if (rommOriginAlreadyConfigured(taken, extra)) continue
            if (plugins.library(id) == null) {
                try {
                    plugins.openInstance(LibraryInstance(id, RommCredentials.PLUGIN_ID))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    continue
                }
            }
            plugins.configure(id, mapOf(ROMM_ORIGIN_KEY to extra))
        }
    }

    /**
     * Downloaded RomM games. They live in Foldcade's folder under `Android/media`,
     * which a player that opens games by path, such as RetroArch, can read. Only
     * without shared storage do they stay in the app cache.
     */
    private fun rommCacheRoot(): Path = rommSharedRoot() ?: rommPrivateRoot()

    private fun rommPrivateRoot(): Path = cacheDir.toPath().resolve("romm")

    private fun rommSharedRoot(): Path? {
        val media = externalMediaDirs.firstOrNull() ?: return null
        if (Environment.getExternalStorageState(media) != Environment.MEDIA_MOUNTED) return null
        return media.toPath().resolve("romm")
    }

    private fun rommCachePath(root: Path): String = if (root == rommPrivateRoot()) "romm" else ROMM_MEDIA_PATH

    private val rommAuthority: String get() = "$packageName.romm.cache"

    /** The manifest's [RommFolderProvider] authority. */
    val rommFolderAuthority: String get() = "$packageName.romm.folders"

    /** Document ids [RommFolderProvider] serves: downloads in either root, named as in their content URIs. */
    internal fun rommFolderDocuments(): RomFolderDocuments = RomFolderDocuments(
        buildMap {
            put(rommCachePath(rommPrivateRoot()), rommPrivateRoot())
            rommSharedRoot()?.let { put(ROMM_MEDIA_PATH, it) }
        },
    )

    /** Downloads an older version kept in the app cache, moved once to the shared root. Cover art stays. */
    private fun moveRommDownloadsToSharedStorage() {
        val shared = rommSharedRoot() ?: return
        val old = rommPrivateRoot()
        for (folder in listOf("servers", "roms")) {
            moveRommDownloads(old.resolve(folder), shared.resolve(folder)) { it.any { part -> part.toString() == "artwork" } }
        }
    }

    /** Paths for games a player opens by path. RomM downloads count only in the shared root. */
    internal val sharedPaths: SharedPaths by lazy {
        SharedPaths(
            ownFile = { uri ->
                val root = rommCacheRoot()
                CachePathContentUri(root, rommAuthority, rommDataRoot(), rommCachePath(root), rommFolderAuthority)
                    .pathFor(uri)
            },
            sharedRoots = { listOfNotNull(rommSharedRoot()) },
        )
    }

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
        releaseUnusedFolderGrant(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        shell.setFolders(libraryFolders())
        reloadFolder()
    }

    /** Removing one saved reference must not revoke another saved folder's access. */
    fun releaseUnusedFolderGrant(uri: String, flags: Int) {
        if (store.folderTreeInUse(uri)) return
        try {
            contentResolver.releasePersistableUriPermission(Uri.parse(uri), flags)
        } catch (_: SecurityException) {
            // Already released.
        }
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

    /** The .steam files the last finished scan found, before they are routed. */
    @Volatile
    private var steamFiles: List<SteamShortcut> = emptyList()

    @Volatile
    private var steamFilesRead = false

    private suspend fun useSteamFiles(found: List<SteamShortcut>) {
        steamFiles = found
        steamFilesRead = true
        routeSteamFiles()
    }

    /** Splits the .steam files between GameNative and DroidDeck. See [routeSteamFiles]. */
    private suspend fun routeSteamFiles() {
        val route = routeSteamFiles(
            steamFiles,
            gameNativeInstalled = gameNativePackagePresent(),
            droidDeckInstalled = packageInstalled(DroidDeckPlayer.PACKAGE),
        )
        droidDeckFileGames = route.droidDeck
        if (route.gameNative != steamFileGames) {
            steamFileGames = route.gameNative
            refreshGameNative()
        }
        publishDroidDeck()
    }

    /** GameNative or DroidDeck came or went, so the .steam files may belong to the other now. */
    private fun rerouteSteamFiles() {
        pluginLoad.launch {
            pluginsReady.await()
            routeSteamFiles()
        }
    }

    @Volatile
    private var folderRefresh: Job? = null

    /** The folders the backend is bound to, so a background walk can tell whether it reached them all. */
    @Volatile
    private var boundForest: FolderForest? = null

    /**
     * Returning Home walks the folders again in the background, so a DroidDeck or
     * GameNative sync shows without a restart. The board stays as it is while the
     * walk runs, and an unchanged or unreadable folder leaves it alone, as does a
     * walk that could not reach every saved folder: a card that is briefly away
     * keeps its games on Home. A reload that binds the folders again wins. Returns the walk,
     * or the one already running.
     */
    fun refreshFolderQuietly(): Job {
        folderRefresh?.takeIf { it.isActive }?.let { return it }
        return pluginLoad.launch {
            pluginsReady.await()
            if (store.folderTrees().isEmpty()) return@launch
            val backend = plugins.library(LocalFolderBackend.ID) as? LocalFolderBackend ?: return@launch
            val forest = boundForest ?: return@launch
            val loaded = try {
                if (!backend.rescan()) return@launch
                if (!forest.complete) return@launch
                loadPlatforms(
                    plugins,
                    LocalFolderBackend.ID,
                    folderName = backend::folderName,
                    occupiesBoth = { platformOccupiesBoth(plugins, it, ::packageInstalled) },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (fatal: VirtualMachineError) {
                throw fatal
            } catch (failure: Exception) {
                Log.w("Foldcade", "Folder refresh failed", failure)
                return@launch
            }
            if (loaded == LoadedLibrary.Unreachable) return@launch
            folderRead.withLock {
                if (boundForest === forest) useSteamFiles(backend.steamShortcuts())
            }
            val games = when (loaded) {
                is LoadedLibrary.Platforms -> try {
                    loadEveryGame(LocalFolderBackend.ID, loaded)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.w("Foldcade", "Folder refresh failed", failure)
                    return@launch
                }
                else -> emptyList()
            }
            withContext(Dispatchers.Main.immediate) {
                // The last folder was forgotten meanwhile, and its games already left.
                if (store.folderTrees().isEmpty() || boundForest !== forest) return@withContext
                if (loaded is LoadedLibrary.Platforms) {
                    shell.ingestLibrary(
                        LocalFolderBackend.ID,
                        games,
                        plugins.platformDefinitions(),
                        loaded.entries.associate { (it.platformId ?: it.id) to it.title },
                    )
                } else {
                    shell.dropLibrary(LocalFolderBackend.ID)
                }
                val waiting = shell.model.libraryGrid && shell.model.emptyGrid != EmptyGrid.Loading
                if (waiting && shell.hasLibraryGames()) shell.showCuratedHome()
            }
        }.also { folderRefresh = it }
    }

    fun reloadFolder(): Job {
        libraryRetry = { reloadFolder() }
        val ticket = libraryTicket.incrementAndGet()
        return pluginLoad.launch {
            pluginsReady.await()
            if (ticket != libraryTicket.get()) return@launch
            val saved = store.folderTrees()
            withContext(Dispatchers.Main.immediate) { shell.setFolders(libraryFolders()) }
            if (saved.isEmpty()) {
                folderRead.withLock { useSteamFiles(emptyList()) }
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
            deliverLibrary(ticket, LocalFolderBackend.ID, readFolders(ticket, backend, saved))
        }
    }

    /** One folder read at a time, since each binds the backend to its own tree. */
    private val folderRead = Mutex()

    /** Scans [saved] and routes their .steam files. Null when the read failed. */
    private suspend fun readFolders(ticket: Int, backend: LocalFolderBackend, saved: List<String>): LoadedLibrary? = folderRead.withLock {
        try {
            val forest = FolderForest(contentResolver, saved.map(Uri::parse))
            backend.bindTree(forest.root(), forest::children)
            boundForest = forest
            val loaded = loadPlatforms(
                plugins,
                LocalFolderBackend.ID,
                folderName = backend::folderName,
                occupiesBoth = { platformOccupiesBoth(plugins, it, ::packageInstalled) },
            )
            // A failed read says nothing about the .steam files, and a newer reload has its own.
            if (loaded != LoadedLibrary.Unreachable && ticket == libraryTicket.get()) {
                useSteamFiles(backend.steamShortcuts())
            }
            loaded
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (fatal: VirtualMachineError) {
            throw fatal
        } catch (failure: Exception) {
            Log.w("Foldcade", "Folder library failed", failure)
            null
        }
    }

    internal val libraryResync = LibraryResync(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        intervalMillis = RESYNC_INTERVAL_MILLIS,
        now = SystemClock::elapsedRealtime,
        onRunning = { shell.setLibrarySyncing(it) },
        work = ::resyncLibraries,
    )

    /** A Home screen was shown or covered. The resync and theme trial clocks run while any one is shown. */
    internal fun noteHomeShown() {
        val shown = homes.values.any { it.get()?.shellVisible == true }
        libraryResync.noteShown(shown)
        noteThemeTrialShown(shown)
        moonlightStreams.hosts.watch(shown)
    }

    /**
     * While Home is shown, the packs in use on trial count the time towards ending their trial,
     * every tick and the part of one when Home is covered. Main thread.
     */
    private fun noteThemeTrialShown(shown: Boolean) {
        if (shown == (themeTrialClock?.isActive == true)) return
        themeTrialClock?.cancel()
        themeTrialClock = null
        if (!shown) {
            creditThemeTrial()
            return
        }
        if (themeInstalls.ledger.themes.none { it.trial != null }) return
        themeTrialMark = SystemClock.elapsedRealtime()
        saveThemeTrial(themeArchivesInUse(), 0)
        themeTrialClock = themeTrialScope.launch {
            while (themeInstalls.ledger.themes.any { it.trial != null }) {
                delay(THEME_TRIAL_TICK_MILLIS)
                creditThemeTrial()
            }
        }
    }

    /** Counts the time since the last credit towards the trials of the packs in use. Main thread. */
    private fun creditThemeTrial() {
        val now = SystemClock.elapsedRealtime()
        val millis = (now - themeTrialMark).coerceAtLeast(0)
        themeTrialMark = now
        saveThemeTrial(themeArchivesInUse(), millis)
    }

    private fun saveThemeTrial(inUse: Set<String>, millis: Long) {
        scope.launch {
            try {
                themeInstalls.using(inUse)
                if (millis > 0) themeInstalls.shown(millis)
            } catch (error: Exception) { Log.w("FoldcadeTheme", "Theme trial not saved", error) }
        }
    }

    /**
     * The archives in files/themes the shell has in use: its theme, its icon theme, and the pack
     * of the background it shows. Main thread.
     */
    private fun themeArchivesInUse(): Set<String> = themePacksInUse().mapNotNull { it.archive }.toSet()

    /** The packs the saved theme, icon and background choices resolve to, with the packs each builds on. */
    private fun themePacksInUse(): List<PackagedTheme> {
        val model = shell.model
        val theme = themePacks.getOrNull(model.themeIndex - 1)
        val icons = model.iconThemeIndex?.let { themePacks.getOrNull(it - 1) }
        val background = themePacks.firstOrNull { model.backgroundMotion in it.resources.scenes }
        // A pack that extends another draws that one's icons and sounds too.
        return listOfNotNull(theme, icons, background).flatMap { listOf(it) + it.parents }.distinct()
    }

    /** Every pack as Settings › Look › Themes lists it. */
    internal fun themeEntries(): List<app.foldcade.language.ThemeEntry> {
        val inUse = themePacksInUse().map { it.resources.id }.toSet()
        val trials = themeInstalls.ledger.themes.filter { it.trial != null }.map { it.file }.toSet()
        // A pack taken out still loads until the restart, but the page has let it go.
        return themePacks.filter { pack -> pack.archive == null || themeInstalls.ledger.theme(pack.archive) != null }.map { pack ->
            app.foldcade.language.ThemeEntry(
                id = pack.resources.id,
                name = pack.name,
                release = pack.resources.file.release?.toString(),
                about = pack.resources.file.about,
                diagnostics = pack.resources.diagnostics,
                removable = pack.archive != null,
                inUse = pack.resources.id in inUse,
                onTrial = pack.archive != null && pack.archive in trials,
                parents = pack.parents.map { it.name },
            )
        }
    }

    /**
     * Starts Foldcade again, so packs installed or taken out load. A helper in its own process
     * brings Home back once this one has gone; this one leaves as an app does, not as a crash,
     * so no pack on trial is blamed.
     */
    internal fun restart(from: android.app.Activity) {
        from.startActivity(Intent(from, RestartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        from.finishAffinity()
        Runtime.getRuntime().exit(0)
    }

    /** Android's record of how this process ended before. A crash or ANR counts against a pack on trial. */
    private fun processExits(): List<ThemeProcessExit> {
        val manager = getSystemService(ActivityManager::class.java) ?: return emptyList()
        return manager.getHistoricalProcessExitReasons(packageName, 0, 16)
            .filter { it.processName == packageName }
            .map { ThemeProcessExit(it.timestamp, it.reason in ABNORMAL_EXITS) }
    }

    /**
     * Every library reads its games again: the folders, each RomM server, and the GameNative
     * catalog. New games join the board, gone ones leave it, and nothing else moves. The
     * Moonlight and DroidDeck shortcuts are read each time Home comes forward, so not here.
     */
    private suspend fun resyncLibraries() {
        pluginsReady.await()
        Log.i("Foldcade", "library-resync start")
        val model = shell.model
        val folders = when {
            store.folderTrees().isEmpty() -> null
            !model.libraryGrid -> refreshFolderQuietly()
            // A failed read tries again, and an empty folder looks again. An open platform is left alone.
            model.gridKind == GridKind.Unreachable -> retryLibrary()
            model.emptyGrid == EmptyGrid.NoPlatforms -> reloadFolder()
            else -> null
        }
        val romm = synchronized(rommTicket) { rommRefreshJob?.takeIf { it.isActive } } ?: syncRomm()
        withContext(Dispatchers.IO) {
            // After the folders, which route their .steam files into the catalog.
            folders?.join()
            refreshGameNative()
        }
        romm.join()
        Log.i("Foldcade", "library-resync end")
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

    fun retryLibrary(): Job {
        return libraryRetry()
    }

    private fun openPlatformId(libraryId: String, platformId: String): Job {
        val ticket = libraryTicket.incrementAndGet()
        return pluginLoad.launch {
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
                    occupiesBoth = { platformOccupiesBoth(plugins, it, ::packageInstalled) },
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
     * A network coming back refreshes the RomM catalog and syncs saves changed offline.
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

    /** Reloading also connects, which starts the plugin's sync of unsynced saves. */
    private fun reconnectRomm() {
        syncRomm()
    }

    /**
     * Puts RomM's games on the same home board as the folder's, or takes them off
     * once no server is saved. The grid does not change mode while this runs. A
     * server that cannot be reached leaves the board as it was, placed tiles included.
     */
    fun syncRomm(): Job {
        synchronized(rommTicket) {
            val ticket = rommTicket.incrementAndGet()
            val readAt = shell.deviceMark()
            rommRefreshJob?.cancel()
            return pluginLoad.launch {
                pluginsReady.await()
                if (ticket != rommTicket.get()) return@launch
                configureSavedRomm()
                val ids = store.rommServers().map { it.first }
                if (ids.isEmpty()) {
                    withContext(Dispatchers.Main.immediate) {
                        if (ticket == rommTicket.get()) shell.dropLibrary(RommCredentials.PLUGIN_ID)
                    }
                    return@launch
                }
                refreshRommServers(
                    ids = ids,
                    load = { libraryId ->
                        when (val listed = loadPlatforms(
                            plugins,
                            libraryId,
                            occupiesBoth = { platformOccupiesBoth(plugins, it, ::packageInstalled) },
                        )) {
                            is LoadedLibrary.Platforms -> listed to loadEveryGame(libraryId, listed)
                            LoadedLibrary.NoPlatforms -> null to emptyList<GridEntry>()
                            LoadedLibrary.Unreachable -> null
                        }
                    },
                    onSignIn = { libraryId ->
                        withContext(Dispatchers.Main.immediate) {
                            if (ticket == rommTicket.get() && store.rommServerOrigin(libraryId) != null) {
                                shell.askToSignInAgain(libraryId)
                            }
                        }
                    },
                    onFailure = { libraryId, failure -> Log.w("Foldcade", "RomM library $libraryId failed", failure) },
                    onLoaded = { libraryId, (listed, games) ->
                        withContext(Dispatchers.Main.immediate) {
                            if (ticket != rommTicket.get() || store.rommServerOrigin(libraryId) == null) return@withContext
                            shell.ingestLibrary(
                                libraryId,
                                games,
                                plugins.platformDefinitions(),
                                listed?.entries.orEmpty().associate { (it.platformId ?: it.id) to it.title },
                                readAt = readAt,
                            )
                            val waiting = shell.model.libraryGrid && shell.model.emptyGrid != EmptyGrid.Loading
                            if (waiting && games.isNotEmpty()) shell.showCuratedHome()
                        }
                    },
                )
            }.also { rommRefreshJob = it }
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
                // A folder of .steam files has games, just none of its own: GameNative's, DroidDeck's, or both.
                LoadedLibrary.NoPlatforms -> if (steamFiles.isNotEmpty() && libraryId == LocalFolderBackend.ID) {
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
                occupiesBoth = { platformOccupiesBoth(plugins, it, ::packageInstalled) },
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

/** The shortest time between two reads of RetroArch's cores when Foldcade returns to the front. */
private const val RETROARCH_RETURN_READ_MS = 60_000L

/** How long a hosted save folder waits, at most, for the players to load while Foldcade starts. */
private const val PLAYERS_LOAD_WAIT_MS = 10_000L

/** How often a shown Home adds to the trial of the packs in use. */
private const val THEME_TRIAL_TICK_MILLIS = 15_000L

private val ABNORMAL_EXITS = setOf(
    ApplicationExitInfo.REASON_CRASH,
    ApplicationExitInfo.REASON_CRASH_NATIVE,
    ApplicationExitInfo.REASON_ANR,
)
