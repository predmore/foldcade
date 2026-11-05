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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import app.foldcade.language.fullPageOn
import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.CredentialLookup
import app.foldcade.api.plugin.CredentialStore
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameActivity
import app.foldcade.api.plugin.HostedSaveFolder
import app.foldcade.api.plugin.LaunchFlag
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
import app.foldcade.plugins.eden.EDEN_PACKAGES
import app.foldcade.plugins.eden.EdenPlayer
import app.foldcade.plugins.romm.RommCoverArt
import app.foldcade.plugins.romm.RommFirmware
import app.foldcade.plugins.romm.RommPlugins
import app.foldcade.romm.FirmwareSummary
import app.foldcade.romm.RomProgress
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
import coil3.request.allowHardware
import coil3.toBitmap
import android.service.notification.NotificationListenerService
import app.foldcade.language.homeGameId
import app.foldcade.language.FoldNotice
import app.foldcade.language.orderedNotices
import app.foldcade.language.brightnessLevel
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.Motion
import app.foldcade.language.Copy
import app.foldcade.language.EmptyGrid
import app.foldcade.language.RomFolderOffer
import app.foldcade.language.GridKind
import app.foldcade.language.LibraryFolder
import app.foldcade.language.SignedInBackend
import app.foldcade.language.builtInTheme
import app.foldcade.localfolder.LocalFolderBackend
import app.foldcade.localfolder.LocalMediaProvider
import app.foldcade.localfolder.LocalMediaIndex
import app.foldcade.localfolder.buildLocalMediaIndex
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
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.lang.ref.WeakReference
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class FoldcadeApp : Application() {
    lateinit var store: SessionStore
        private set

    internal val setupBackups by lazy { SetupBackups(filesDir, getSharedPreferences("foldcade", MODE_PRIVATE)) }
    internal val backupWork = Mutex()
    internal var pendingSetupRestore: app.foldcade.host.backup.SetupBackup? = null
    internal var pendingSetupAction = app.foldcade.language.RestoreAction.Restore
    internal var restoreNoticeShown = false

    /** Waits for startup save preparation as well as plugin entries. A player must not launch before it. */
    internal suspend fun awaitPlugins() = pluginsReady.await()

    /** The grid can publish before this; every player launch still waits for save preparation. */
    internal val savePreparationReady: Boolean get() = pluginsReady.isCompleted && !pluginsReady.isCancelled

    /** One save flow at a time: a launch's placement, or a return's read-back. */
    internal val saveFlow = Mutex()
    internal var savePickerSerial = 0L
    internal var savePickerRequest: SavePickerRequest? = null

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

    /** First-time configuration, read and changed only on IO. */
    internal val thorPresets: AndroidThorPresets by lazy { AndroidThorPresets(this) }

    /** Moves library saves in and out; replaced files are kept in `save-backups`. */
    internal val playerSaves: PlayerSaves by lazy {
        val documents = AndroidSaveDocuments(contentResolver)
        PlayerSaves(
            documents,
            store,
            filesDir.toPath().resolve("save-backups"),
            filesDir.toPath().resolve("save-handover"),
        ) { uri -> ContentGameFile(contentResolver, uri) }
    }

    /** Launch-only firmware state; source versions avoid repeated BIOS reads and ZIP copies. */
    internal val playerFirmware: PlayerFirmware by lazy { PlayerFirmware(this, playerSaves) { store.playerFirmwareFolder("retroarch") } }

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

    /**
     * The Settings activity until it is destroyed, so Open an app can finish it even
     * while Android covers it. Only a resumed activity takes companion keys.
     */
    internal var settingsPageActivity: app.foldcade.language.PanelKeyActivity? = null

    /** Mark the page before Home stops, so its Settings parent never moves to the other screen. */
    internal fun noteSettingsPage(activity: android.app.Activity) {
        val panel = Displays(this).panelFor(activity, store.session.defaultDisplayIsTop) ?: return
        store.place(panel, ExternalApp("foldcade:settings-page", foldcadePage = true))
    }

    internal fun settingsPageCovered(activity: android.app.Activity) {
        // onStop means another activity may cover the page without Home resuming:
        // https://developer.android.com/guide/components/activities/activity-lifecycle#onstop
        if (settingsPageActivity !== activity) return
        Displays(this).panelFor(activity, store.session.defaultDisplayIsTop)?.let { panel ->
            store.update { it.settingsPageCovered(panel) }
        }
    }

    /** The other app in front of a screen, for the command center beside it. */
    val frontApps by lazy { FrontApps(UsageAccess(this), packageName) }

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

    private val rommCoverArt by lazy { RommCoverArt(publicArt::allows) { RommPlugins.wiring } }

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

    /** Facts for library records. Phase B observes these with rememberGameDetails. */
    val gameInfo: GameInfo by lazy { GameInfo(plugins) }

    var infoRevision by mutableStateOf(0)
        private set

    private val localMedia: LocalMediaProvider?
        get() = plugins.metadata(LocalMediaProvider.ID) as? LocalMediaProvider

    /** Decodes and caches covers. Public art loads through :net, RomM's through its own client. */
    val images: ImageLoader by lazy {
        val romm = RommArtwork { RommPlugins.wiring }
        ImageLoader.Builder(this)
            .components {
                add(
                    CoverFetcher.Factory { uri ->
                        when {
                            isMoonlightPosterUri(uri) -> readMoonlightPoster(this@FoldcadeApp, uri)
                            moonlightStreams.isHostArt(uri) -> readHostArt(uri)
                            uri.startsWith("content://") -> readLibraryArtwork(contentResolver, uri,
                                libraryTrees(), localMedia?.index()?.artworkUris.orEmpty())
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

    /**
     * What each move on a game's Play activity did, by the game it came from and the game it
     * went to, so A puts it back while the page is open. Cleared when a game's page opens.
     */
    private val playMoves = HashMap<Pair<String, String>, List<app.foldcade.host.play.PlayMove>>()

    /** Tiles whose cover was kept in this process, so a page drawing them again does not keep them again. */
    private val keptPlayArt = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * Moves every finished session of [from] onto [to], from a game's Play activity. False
     * when nothing could move, as when [from]'s only play is still running.
     */
    fun movePlay(from: String, to: String): Boolean {
        val moves = plays.move(from, to)
        if (moves.isEmpty()) {
            playMoves.remove(from to to)
            return false
        }
        playMoves[from to to] = moves
        // The game it went to may never have been played: its name now has history to read.
        shell.playTitle(to)?.let { plays.rememberName(shell.tileOf(to), it) }
        return true
    }

    /** Puts back the last [movePlay] of [from] onto [to]. */
    fun undoPlayMove(from: String, to: String) {
        playMoves.remove(from to to)?.let(plays::undo)
    }

    /** A tile's play time for its Y menu, read once per change to the log: menus ask on every publish. */
    private fun playTimeOf(tileId: String): Long {
        val catalog = shell.catalogNow()
        if (playTimeStamp != plays.stamp || playTimeCatalog !== catalog) {
            playTimeStamp = plays.stamp
            playTimeCatalog = catalog
            playTimes.clear()
            playFound.clear()
        }
        // A copy folding into the tile changes its total without a new session, so its ids are the key too.
        return playTimes.getOrPut(tileId to shell.historyIds(tileId)) { plays.shown(tileId).activeMillis }
    }

    private var playTimeStamp = -1
    private var playTimeCatalog: List<Any>? = null
    private val playTimes = HashMap<Pair<String, Set<String>>, Long>()

    /**
     * The other games a game's page can take play from, likeliest first: each tile with play
     * but [tileId] and its copies, by [app.foldcade.language.playSourceOrder].
     */
    fun playSources(tileId: String): List<String> {
        // A page opening starts with nothing to put back: Undo is for this visit's moves.
        playMoves.clear()
        val (title, sources) = sourcesFor(tileId)
        return app.foldcade.language.playSourceOrder(title, sources).map { it.id }
    }

    /** [tileId]'s name, and every other tile with play as a source for its page. */
    private fun sourcesFor(tileId: String): Pair<String, List<app.foldcade.language.PlaySource>> {
        val own = shell.historyIds(tileId) + shell.tileOf(tileId)
        val title = shell.playTitle(tileId) ?: plays.name(tileId).orEmpty()
        val sources = plays.played().values
            .filter { it.gameId !in own && it.activeMillis > 0L }
            .map { totals ->
                val current = shell.playTitle(totals.gameId)
                app.foldcade.language.PlaySource(
                    id = totals.gameId,
                    name = playName(totals.gameId, current, plays.name(totals.gameId)),
                    gone = shell.libraryTitle(totals.gameId) == null,
                    activeMillis = totals.activeMillis,
                )
            }
        return title to sources
    }

    /** For a game's Play time row: the time a gone game of its name left, read once per change to the log. */
    private fun playFoundFor(tileId: String): Long? {
        // Its time is read first, which starts both caches over after a change to the log.
        playTimeOf(tileId)
        // A scan can take a game away, or bring one back, without a change to the log.
        val catalog = shell.catalogNow()
        if (catalog !== playFoundCatalog) {
            playFoundCatalog = catalog
            playFound.clear()
        }
        val key = tileId to shell.historyIds(tileId)
        // Most games have nothing found, so null is kept too rather than looked for again.
        if (key in playFound) return playFound[key]
        val (title, sources) = sourcesFor(tileId)
        return app.foldcade.language.foundSource(title, sources)?.activeMillis.also { playFound[key] = it }
    }

    private val playFound = HashMap<Pair<String, Set<String>>, Long?>()
    private var playFoundCatalog: List<Any>? = null

    /**
     * Keeps [tileId]'s cover beside its play, from [uri] when the caller has drawn it, else
     * from wherever the grid finds its art. A game's cover is kept once a process unless a
     * session starts, which keeps it again, so new art is what history shows.
     */
    fun keepPlayArt(tileId: String, uri: String? = null, again: Boolean = false) {
        if (!again && tileId in keptPlayArt) return
        // Marked while it runs, so a page drawing the cover again waits; kept only once written.
        keptPlayArt += tileId
        themeTrialScope.launch {
            var kept = false
            try {
                kept = keepPlayArtNow(tileId, uri)
            } finally {
                if (!kept) keptPlayArt -= tileId
            }
        }
    }

    /** [keepPlayArt]'s work, on the main thread until the image loads. True once the cover is written. */
    private suspend fun keepPlayArtNow(tileId: String, uri: String?): Boolean {
        val game = shell.findGame(tileId).takeIf { !it.emptyShelfHint && it.id.isNotEmpty() } ?: return false
        val record = shell.artRecord(game)
        val query = shell.artQuery(game)
        val source = uri ?: (covers.cached(record, query) ?: if (record != null || query != null) covers.fetch(record, query) else null)
            ?.let { it.square ?: it.cover } ?: return false
        return withContext(Dispatchers.IO) {
            try {
                val request = coil3.request.ImageRequest.Builder(this@FoldcadeApp)
                    .data(CoverImage(source))
                    .size(PLAY_ART_PX)
                    .allowHardware(false)
                    .build()
                val image = (images.execute(request) as? coil3.request.SuccessResult)?.image ?: return@withContext false
                plays.art.keep(tileId, image.toBitmap())
                true
            } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("FoldcadePlay", "Cover not kept", error)
                false
            }
        }
    }

    /** Both screens' brightness, for the R1 slider. */
    lateinit var brightness: ScreenBrightness
        private set

    /** The external player session opened by the host launch path. */
    val externalPlay = ExternalPlay()

    /** Games left open in their players behind Home. Their tiles read Paused, and A brings them back. */
    internal val runningGames = RunningGames().also { games ->
        games.onChange = { ids -> if (::shell.isInitialized) shell.running = ids }
    }

    /** A library launch held while its player's save folder is asked for. It outlives a recreated Home, as the ask does. */
    internal var pendingSaveAsk: PendingBackendLaunch? = null

    /** The player the hosted-saves ask is about, and the library launch waiting on it, if any. Kept here as the ask is, for either Home to answer. */
    internal var hostedAsk: HostedAsk? = null

    /** The tile Restart asked about, until its dialog closes. The app keeps it, as it keeps the dialog, for either Home to answer. */
    internal var restartAsk: String? = null

    /** The exact run and companion list the Close confirmation named. */
    internal var closeGameAsk: RunningGame? = null
    internal val closingPlayers = mutableSetOf<String>()

    internal fun canCloseGame(tileId: String): Boolean = runningOn(tileId)?.let {
        canClearPlayerTask(it.packageName) && it.packageName !in closingPlayers && packageManager.getLaunchIntentForPackage(it.packageName) != null
    } == true

    internal fun moveGameTarget(tileId: String): Panel? {
        val game = runningOn(tileId) ?: return null
        // A launcher aimed at the other display can create a menu task there.
        // A reusable start alone does not promise a move; the player must opt in.
        if (game.packageName in closingPlayers || game.resumeStart == null) return null
        val saveAwaitsLoad = game.saveFolderKey?.let(store::saveAwaitsLoad) == true
        val target = game.moveTarget(store.session, saveAwaitsLoad) ?: return null
        val activity = game.activity ?: return null
        val changes = try {
            packageManager.getActivityInfo(android.content.ComponentName(game.packageName, activity), 0).configChanges
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            return null
        }
        if (!handlesPanelSizeChange(changes)) return null
        val assignment = Displays(this).assignment(store.session.defaultDisplayIsTop)
        if (target == Panel.Bottom && assignment.bottomDisplayId == null) return null
        return target
    }

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

    internal fun homeOnDisplay(displayId: Int): FoldcadeHomeActivity? = homes[displayId]?.get()?.takeUnless { it.isDestroyed || it.isFinishing }

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

    /**
     * Where the grid has slid to. Both screens' scenes follow it, so the top slides with
     * the bottom's grid as the 3DS's top screen does.
     */
    internal val backdropCamera = app.foldcade.ui.BackdropCamera()

    /** A launching game's art, growing over both screens while the game opens. */
    internal val launchBlooms = LaunchBlooms()

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
    /** Launches wait for save discovery, health, cores and the download migration. */
    private val pluginsReady = CompletableDeferred<Unit>()
    private val saveFoldersReady = CompletableDeferred<Unit>()
    /** RomM needs save bindings and migrated downloads, but not unrelated shelves. */
    private val rommReady = CompletableDeferred<Unit>()
    private val shelfPinsReady = CompletableDeferred<Unit>()

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
                    app.foldcade.ui.LauncherIcons.clear()
                    reloadInstalledApps()
                    val changed = intent.data?.schemeSpecificPart
                    // A game cannot still run in an emulator that is gone, so its tile falls back
                    // to the game's choice, and Close and Move find the run that replaces it.
                    if (intent.action == Intent.ACTION_PACKAGE_REMOVED && changed != null) runningGames.forget(changed)
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
        startupTracing = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        // Before the packs load, so a pack that crashed Foldcade twice is already back out of the way.
        perfMark("theme-recovery-start")
        val themeRollbacks = try {
            themeInstalls.begin(processExits())
        } catch (error: Exception) { Log.w("FoldcadeTheme", "Theme trials skipped", error); emptyList() }
        perfMark("theme-recovery-end")
        applySetupRestore()
        // Restore applies first. Replay history on IO while Main prepares the theme, but join it
        // before constructing the shell so its first Recent order and play totals are complete.
        val preparedPlays = scope.async {
            perfMark("history-prepare-start")
            PlaySessions.open(File(filesDir, "play-sessions/events.log")).also { perfMark("history-prepare-end") }
        }
        store = SessionStore(getSharedPreferences(SessionStore.PREFS_NAME, MODE_PRIVATE))
        runningGames.restore(store.playersHoldingGames())
        runningGames.onPlayersChange = store::setPlayersHoldingGames
        buttonPromptFirstSession = !store.buttonPromptSeenLaunch()
        if (buttonPromptFirstSession) store.setButtonPromptSeenLaunch()
        credentials = AndroidCredentialStore(this)
        plugins = PluginHost(Dispatchers.IO, credentials)
        music = HomeMusic(this, store) { slot ->
            ::shell.isInitialized && themePacks.getOrNull(shell.model.themeIndex - 1)?.sounds?.has(slot) == true
        }
        var artSession = store.session
        store.afterSessionChanged = {
            val next = store.session
            if (homeIconCacheMayTrim(artSession, next)) {
                // The screen beside the game draws its new page after this change. What that page
                // shows by the time the fade ends stays cached, and so does what the game covered.
                val unshown = app.foldcade.ui.ThemeIconImages.unshown()
                noticeHandler.postDelayed({
                    val released = app.foldcade.ui.ThemeIconImages.trim(unshown)
                    Log.d("Foldcade", "Home icon cache released $released unused bytes")
                }, HOME_ICON_TRIM_DELAY_MS)
            }
            artSession = next
            music.onSessionChanged()
            if (::shell.isInitialized) shell.noteSessionChanged()
            refreshHomeRecovery()
        }
        perfMark("themes-start")
        val manifests = mutableMapOf<String, ThemeFile>()
        themePacks = PackagedTheme.loadAll(this) { archive, manifest ->
            manifests[archive] = manifest
            try { themeInstalls.read(archive, manifest) } catch (error: Exception) { Log.w("FoldcadeTheme", "Theme trials not saved", error) }
        }
        perfMark("themes-end")
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
        perfMark("history-start")
        plays = runBlocking { preparedPlays.await() }
        perfMark("history-end")
        val sliderTick = SliderTick()
        plays.packageOf = { gameId ->
            playTimePackage(gameId) { shelfId ->
                val platform = Shelf.games.firstOrNull { it.id == shelfId }?.platformId
                val player = platform?.let { playerFor(shelfId, it) }
                player?.packageNames?.let { names -> names.firstOrNull(::packageInstalled) ?: names.firstOrNull() }
            }
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
            rememberedPlayName = plays::name,
            onMoonlightRemoved = ::unpinMoonlightGame,
            onDroidDeckRemoved = ::unpinDroidDeckGame,
            onGameNativeRemoved = { key -> pluginLoad.launch {
                val game = (plugins.library(GameNativeLibrary.ID) as? GameNativeLibrary)?.confirmed()?.firstOrNull { it.remoteKey == key }
                if (game != null && unpinGameNativeShortcut(this@FoldcadeApp, game)) refreshGameNative()
            } },
            onBrightness = { position -> brightness.write(brightnessLevel(position)) },
            onSameGames = { groups -> saveArtChoices(artChoices.adopt(groups)) },
            appVersion = installedVersion(),
            onSavePickerClosed = { onSaveConflicts?.invoke() },
            saveFolderPick = ::saveFolderPick,
            restoreResultPending = { setupBackups.hasNotice() },
            onRestoreFoldersConnected = { pluginLoad.launch { refreshGameNative(); publishDroidDeck() } },
            onSaveHealthChanged = { players ->
                saveSetupNotices.update(players)
                publishNotices()
                updateWaitingSaveNotices()
            },
        )
        shell.runningPlayer = { id -> runningOn(id)?.takeIf { packageInstalled(it.packageName) }?.player }
        shell.onPlayerChoiceChanged = { scope -> dismissNotice("foldcade:player-choice:$scope") }
        shell.onPlayerAvailabilityChanged = ::clearUsablePlayerChoiceNotices
        shell.canCloseGame = ::canCloseGame
        shell.moveGameScreen = { id -> moveGameTarget(id)?.let { if (it == Panel.Top) app.foldcade.language.HostScreen.Top else app.foldcade.language.HostScreen.Bottom } }
        // Loading ends once the shell has resolved the choices against the packs.
        perfMark("theme-loaded-start")
        try { themeInstalls.loaded(manifests, themeArchivesInUse()) } catch (error: Exception) { Log.w("FoldcadeTheme", "Theme trials not saved", error) }
        perfMark("theme-loaded-end")
        // A pack chosen in Settings is in use from that moment, so a crash right after counts against it.
        themeTrialScope.launch {
            snapshotFlow { themeArchivesInUse() }.collect { inUse ->
                if (themeInstalls.ledger.themes.any { it.trial != null }) saveThemeTrial(inUse, 0)
            }
        }
        themeRollbacks.forEach { postNotice(themeRollbackNotice(it)) }
        // R1's sign-in notice comes and goes with RomM's sign-in.
        scope.launch(Dispatchers.Main.immediate) { snapshotFlow { shell.model.rommSignIn }.collect { publishNotices() } }
        // The SteamGridDB form's phone page listens while that form is open on a shown screen, and not a moment longer.
        scope.launch(Dispatchers.Main.immediate) {
            snapshotFlow { (homeShownNow && shell.model.connectOpen && shell.model.connectKind == app.foldcade.language.ConnectKind.ArtKey) to shell.model.connectBusy }
                .collect { (wanted, busy) ->
                    keyDrop.want(wanted)
                    // A phone's key whose check ended, refused or set aside by an edit, gets a fresh phone page.
                    keyDrop.checking(busy)
                }
        }
        // The RomM address page follows the same visible-form lifetime as the key page.
        scope.launch(Dispatchers.Main.immediate) {
            snapshotFlow {
                val model = shell.model
                Triple(homeShownNow && model.connectOpen && model.connectKind == app.foldcade.language.ConnectKind.Romm &&
                    !model.connectBusy && model.connectCode == null && shell.connectToken.isBlank() && app.foldcade.language.rommNeedsAddress(model), model.connectRetry, model.connectOrigin)
            }.collect { (wanted, retry, _) -> rommPhone.want(wanted, retry) }
        }
        scope.launch(Dispatchers.Main.immediate) {
            snapshotFlow { homeShownNow && shell.model.connectOpen && shell.model.connectKind == app.foldcade.language.ConnectKind.Romm }
                .collectLatest { open ->
                    if (!open || shell.model.connectOrigin.isNotBlank()) return@collectLatest
                    shell.showNearbyRomm(null, searching = true)
                    val found = withContext(Dispatchers.IO) {
                        lanAddress(getSystemService(ConnectivityManager::class.java))?.hostAddress?.let { app.foldcade.romm.findNearbyRomm(it) }
                    }
                    shell.showNearbyRomm(found, searching = false)
                }
        }
        // A game several libraries have is one tile, and its copies' play counts on it.
        plays.historyIds = shell::historyIds
        plays.tileOf = shell::tileOf
        plays.usageGames = shell::usageGameIds
        // A game's cover is kept when it is played, so its history keeps showing it after it leaves.
        plays.onStarted = { tile -> keepPlayArt(tile, again = true) }
        shell.playSources = ::playSources
        shell.playTimeOf = ::playTimeOf
        shell.playFoundFor = ::playFoundFor
        shell.anyPlay = plays::hasPlay
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
            showHome = {
                settingsPageActivity?.finish()
                shell.goHome()
            },
            busy = shell::holdsCommandCenter,
        )
        // A page that moved onto a command center, as when an app took the top while Settings was open, makes
        // that screen browse, as Start does there: the page, and any menu it opens, draws and takes keys and taps.
        scope.launch(Dispatchers.Main.immediate) {
            // Either order counts: the page moving onto the screen, or the screen turning into a command center under it.
            // Full pages only: a dialog alone draws over the command center and takes its keys without it.
            snapshotFlow { Panel.entries.filter { shell.model.fullPageOn(it.screen()) && store.session.surfaceOn(it) == Surface.CommandCenter } }
                .collect { panels ->
                    panels.forEach { panel ->
                        store.update { it.browse(panel) }
                        // Its idle timer, as Start's browse has, hands the screen back to the command center once the page has closed.
                        commandCenter.touch(panel)
                    }
                }
        }
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
                if (reported.refreshesSaveHealth()) {
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
                    store.restoreCredentials(credentials),
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
        // This one read also serves both activities' initial resume.
        val initialSaveRefresh = refreshFoundSaveFolders(changed = false)
        pluginLoad.launch {
            playersLoaded.await()
            try {
                restoreMoonlightPins()
                restoreDroidDeckPins()
            } finally {
                perfMark("shelf-pins-ready")
                shelfPinsReady.complete(Unit)
            }
            refreshGameNative()
            readMoonlightShelf()
            readDroidDeckShelf()
            reloadInstalledApps()
        }
        pluginLoad.launch {
            prepareStartup(playersLoaded, rommReady, pluginsReady,
                onFailure = { Log.w("Foldcade", "Plugin preparation failed", it) },
            ) {
                try {
                    perfMark("entries-start")
                    plugins.load(classLoader)
                    perfMark("entries-end")
                } finally {
                    playersLoaded.complete(Unit)
                }
                // Found ROM folders join the first scan, so their games never arrive in a second pass.
                val rootsChanged = try {
                    perfMark("rom-roots-start")
                    romRootLook.withLock { lookForRomRoots() }.changed.also { perfMark("rom-roots-end") }
                } finally {
                    startupRootsLooked.complete(Unit)
                }
                // Listing games needs entries, not save-folder providers or old ROM copies. Found
                // folders whose access was turned off (Android restarts Foldcade then) still reload,
                // so their games leave the board rather than stay as tiles that cannot open.
                // A found folder deleted while Foldcade was closed leaves the set too, and its games with it.
                if (libraryTrees().isNotEmpty() || store.foundRoots().isNotEmpty() || rootsChanged) reloadFolder(startup = true)
                perfMark("save-prepare-start")
                saveFoldersReady.await()
                readRetroArchCores()
                moveRommDownloadsToSharedStorage()
                publishRomm()
                rommReady.complete(Unit)
                syncRomm()
                refreshUnsyncedSaves()
                initialSaveRefresh.await()
                perfMark("save-prepare-end")
                pluginsReady.complete(Unit)
            }
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
    private val firmwareNoticeDismissals = FirmwareNoticeDismissals()

    /** The device's notifications, from [NoticeListener]. Main thread. */
    fun setAndroidNotices(notices: List<FoldNotice>) {
        androidNotices = notices
        publishNotices()
    }

    /** A notice of Foldcade's own. One with the same id replaces it. */
    fun postNotice(notice: FoldNotice) {
        if (!firmwareNoticeDismissals.allows(notice)) return
        foldcadeNotices = foldcadeNotices.filter { it.id != notice.id } + notice
        publishNotices()
    }

    /** Removes a notice; only explicit user actions suppress later optional BIOS guidance. */
    fun dismissNotice(id: String, byUser: Boolean = false) {
        firmwareNoticeDismissals.dismiss(foldcadeNotices.firstOrNull { it.id == id }, byUser)
        playerChoiceNotices.remove(id)
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
        foldcadeNotices.filter { it.dismissable }.forEach { firmwareNoticeDismissals.dismiss(it, byUser = true) }
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
        val now = System.currentTimeMillis()
        // RomM waiting for a sign-in stands in R1 until it is signed in. Nothing else would say its saves wait.
        val signIn = if (shell.model.rommSignIn) listOf(app.foldcade.language.rommSignInNotice(rommSignInSince ?: now.also { rommSignInSince = it })) else {
            rommSignInSince = null
            emptyList()
        }
        shell.setNotices(orderedNotices(signIn + foldcadeNotices + androidNotices + saveSetupNotices.notices(now)))
    }

    /** When RomM began waiting for a sign-in, so its notice keeps its place among the others. */
    private var rommSignInSince: Long? = null

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

    /**
     * Checks the SteamGridDB key, typed or sent from the phone, with one search, then keeps
     * it in the credential store. A refused or unreachable key leaves the form open with a note.
     */
    internal fun saveArtKey() {
        val key = shell.connectToken.trim()
        if (key.isEmpty()) return
        val ticket = shell.beginSetupValidation() ?: return
        scope.launch {
            try {
                val accepted = checkSteamGridDbKey(key)
                if (accepted != true) {
                    withContext(Dispatchers.Main.immediate) {
                        if (shell.setupValidationCurrent(ticket)) {
                            shell.showConnectNote(if (accepted == false) Copy.artKeyRefused else Copy.artKeyUnreachable)
                        }
                    }
                    return@launch
                }
                editCredentials {
                    if (!withContext(Dispatchers.Main.immediate) { shell.setupValidationCurrent(ticket) }) return@editCredentials
                    val saved = try {
                        credentials.put(
                            SteamGridDb.CREDENTIAL_ID,
                            SteamGridDb.CREDENTIAL_KEY,
                            Credential.ApiToken(key),
                        )
                        true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        false
                    }
                    if (saved) {
                        steamGridDbKey = key
                        art.forgetMisses()
                    }
                    withContext(Dispatchers.Main.immediate) {
                        if (saved) shell.setArtKeySaved(true)
                        if (shell.setupValidationCurrent(ticket)) {
                            if (saved) shell.closeConnect() else shell.showConnectNote(Copy.artKeyUnreachable)
                        }
                    }
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    shell.finishSetupValidation(ticket)
                }
            }
        }
    }

    /**
     * The SteamGridDB form's phone page. It listens only while that form is open on a shown
     * screen; a key that arrives goes into the form and is checked at once.
     */
    internal val keyDrop by lazy {
        KeyDropHost(
            connectivity = getSystemService(ConnectivityManager::class.java),
            scope = scope,
            colors = {
                val theme = paintFor(shell.model.themeIndex).theme
                app.foldcade.language.KeyDropPage.Colors(
                    background = KeyDropHost.hex(theme.background),
                    surface = KeyDropHost.hex(theme.surface),
                    text = KeyDropHost.hex(theme.onBackground),
                    muted = KeyDropHost.hex(theme.muted),
                    accent = KeyDropHost.hex(theme.focus),
                )
            },
            onKey = { key ->
                // The newest key wins: a different one retires a check of a typed key still running.
                if (shell.model.connectOpen && shell.model.connectKind == app.foldcade.language.ConnectKind.ArtKey) {
                    shell.editToken(key)
                    saveArtKey()
                }
            },
        )
    }

    internal val rommPhone by lazy {
        RommPhoneHost(getSystemService(ConnectivityManager::class.java), scope, colors = {
            val theme = paintFor(shell.model.themeIndex).theme
            app.foldcade.language.KeyDropPage.Colors(
                KeyDropHost.hex(theme.background), KeyDropHost.hex(theme.surface), KeyDropHost.hex(theme.onBackground),
                KeyDropHost.hex(theme.muted), KeyDropHost.hex(theme.focus),
            )
        }, onAddress = address@{ origin ->
            val home = homes.values.firstNotNullOfOrNull { it.get()?.takeIf { home -> home.shellVisible } } ?: return@address null
            home.beginRommFromPhone(origin)
            val visit = shell.connectVisit
            val revision = shell.connectOriginRevision
            // RomM returns /pair/device?user_code=<public user_code>. The device_code and access token
            // stay in the existing polling flow, never in the LAN response:
            // https://github.com/rommapp/romm/blob/5.4.1/backend/utils/device_auth.py
            val model = snapshotFlow { shell.model }.first { model ->
                !homeShownNow || !shell.deviceCodeFormCurrent(visit, revision) || model.connectCode != null || model.connectRetry
            }
            if (homeShownNow && shell.deviceCodeFormCurrent(visit, revision)) model.connectCode?.url?.let { app.foldcade.romm.rommPhoneApprovalUrl(origin, it) } else null
        })
    }

    /** True while any Home screen is shown, for what runs only then. */
    private var homeShownNow by mutableStateOf(false)

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
        val romm = readRommRecovery(store.restoreCredentials(credentials), store.rommOrigin())
        val ids = try {
            credentials.pluginIds()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptySet()
        }
        // The art key is not a library. RomM has its own connection row.
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
    /**
     * One GameNative refresh at a time. Each reads the .steam games when its turn comes, so a
     * refresh started before a folder was forgotten cannot finish last with that folder's games.
     */
    private val gameNativeRefresh = Mutex()

    private suspend fun refreshGameNative(): Unit = gameNativeRefresh.withLock { refreshGameNativeNow() }

    private suspend fun refreshGameNativeNow() {
        val library = plugins.library(GameNativeLibrary.ID) as? GameNativeLibrary ?: return
        Shelf.gameNativeInstalled = gameNativePackagePresent()
        val files = steamFiles
        try {
            val reported = refreshGameNativeCatalog(this, library, File(filesDir, "gamenative/catalog.txt"), files.route.gameNative)
            withContext(Dispatchers.Main.immediate) {
                if (reported.releasesRestore(files.reported, libraryTrees().isNotEmpty()) && Shelf.gameNativeInstalled && store.restoreFolders().isEmpty()) shell.reportRestoreSources(setOf("gamenative"))
                if (reported.settles(files.reported, libraryTrees().isNotEmpty())) shell.settleGameNative()
                shell.noteShelfChanged()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Unit
        }
    }

    fun refreshGameNativeShelf() { pluginLoad.launch { pluginsReady.await(); refreshGameNative() } }

    fun acceptedGameNativePin(packageName: String, shortcutId: String) {
        val pin = app.foldcade.plugins.gamenative.gameNativeShortcut(packageName, shortcutId, null, true, true) ?: return
        pluginLoad.launch {
            pluginsReady.await()
            refreshGameNative()
            val game = (plugins.library(GameNativeLibrary.ID) as? GameNativeLibrary)?.confirmed()
                ?.firstOrNull { it.appId == pin.appId && it.shortcutPackage == packageName } ?: pin
            withContext(Dispatchers.Main.immediate) { shell.placeGameNativePin("gamenative.${game.remoteKey}") }
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

    private suspend fun restorePlayerSaveFolders() {
        for (id in plugins.playerIds()) {
            val player = plugins.player(id) ?: continue
            if (player !is SaveFolderHolder) continue
            val uri = playerSaveFolder(player) ?: continue
            player.bindSaveFolder(uri)
        }
        findPlayerSaveFolders()
        refreshHostedFolders()
        contentResolver.notifyChange(DocumentsContract.buildRootsUri(hostedAuthority), null, false)
    }

    private val saveFolderFindLock = Mutex()
    private val saveFolderStarts = ConcurrentHashMap<String, String>()

    /** Reuses writable trees and retires found folders whose reach has gone. */
    internal suspend fun findPlayerSaveFolders() = withContext(Dispatchers.IO) {
        saveFolderFindLock.withLock {
            perfMark("discovery")
            if (!::plugins.isInitialized) return@withLock
            val players = plugins.playerIds().mapNotNull(plugins::player)
                .filter { it is SaveFolderHolder }.distinctBy { it.saveFolderId() }
            val android = AndroidSaveFolderFinder(this@FoldcadeApp)
            val grants = android.grants(players.filterIsInstance<SaveFolderHolder>())
            val newlyFound = mutableListOf<Pair<String, String>>()
            var firstFound: String? = null
            for (player in players) {
                // Eden's explicit, per-package data-folder choice also supplies keys and firmware.
                // Keep it ahead of discovery; its external-files place is useful with an older SAF grant.
                val edenPackage = if (player is EdenPlayer) installedEdenPackage(player, ::packageInstalled) else null
                if (player is EdenPlayer && (edenPackage == null || store.edenFolder(edenPackage) != null)) continue
                val holder = if (player is EdenPlayer) object : SaveFolderHolder by player {
                    override val saveFolderPlaces = listOf("Android/data/$edenPackage/files")
                } else player as SaveFolderHolder
                val key = player.saveFolderId()
                val saved = store.playerSaveFolder(key)?.let { SavedSaveFolder(it, store.playerSaveFolderFound(key)) }
                val finder = android.finder(player, grants)
                val result = finder.find(holder)
                if (result.placeStart != null) saveFolderStarts[key] = result.placeStart else saveFolderStarts.remove(key)
                val next = detectedSaveFolder(saved) { result.folder }
                // A found folder was just probed. A chosen one only needs its grant: a slow provider is no reason to drop it.
                val bound = next != null && android.holdsGrant(next.uri, grants)
                // A picker can finish while the provider is being read. Its choice wins.
                withContext(Dispatchers.Main.immediate) {
                    if (edenPackage != null && store.edenFolder(edenPackage) != null) return@withContext
                    if (store.playerSaveFolder(key) != saved?.uri || store.playerSaveFolderFound(key) != (saved?.found == true)) return@withContext
                    if (next != saved) store.setPlayerSaveFolder(key, next?.uri, next?.found == true)
                    val uri = next?.uri?.takeIf { bound }.orEmpty()
                    // Only an emulator on the device is news. Eden has no standalone save-folder tile,
                    // so its find stays a readable notice instead of reopening a folder picker.
                    if (next?.found == true && bound && next.uri != saved?.uri && player.packageNames.any(::packageInstalled)) {
                        newlyFound += player.displayName to folderPathLabel(next.uri)
                        if (firstFound == null && player !is EdenPlayer) firstFound = player.id
                    }
                    for (id in plugins.playerIds()) {
                        val sharing = plugins.player(id) ?: continue
                        if (sharing is SaveFolderHolder && sharing.saveFolderId() == key) sharing.bindSaveFolder(uri)
                    }
                }
            }
            withContext(Dispatchers.Main.immediate) {
                shell.refreshPlayerSaves()
                // A folder bound without asking says where, so a wrong one is never bound unseen. A opens
                // Set up saves on the first one with a save-folder tile, whose page says where again.
                if (newlyFound.isNotEmpty()) postNotice(FoldNotice(
                    id = SAVES_FOUND_NOTICE_ID, title = Copy.savesFoundTitle, body = Copy.savesFoundBody(newlyFound),
                    postedMillis = System.currentTimeMillis(), savePlayerId = firstFound,
                ))
            }
        }
    }

    /** A changed grant must be read even if it arrives during another discovery. Main thread. */
    internal fun refreshFoundSaveFolders(changed: Boolean = true): kotlinx.coroutines.Deferred<Unit> =
        saveRefreshes.request(discover = true, changed = changed)

    private var saveFoldersRestored = false
    private val saveRefreshes = SaveRefreshes(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        checking = { shell.setCheckingSaves(it) },
    ) { discover ->
        playersLoaded.await()
        val notes = try {
            withContext(Dispatchers.IO) {
                if (!saveFoldersRestored) {
                    restorePlayerSaveFolders()
                    saveFoldersRestored = true
                } else if (discover) {
                    findPlayerSaveFolders()
                    refreshHostedFolders()
                }
                // A library can reconcile saves with the bound folders while Settings reads card health.
                saveFoldersReady.complete(Unit)
                readPlayerFolderNotes()
            }
        } catch (failure: Throwable) {
            saveFoldersReady.completeExceptionally(failure)
            throw failure
        }
        refreshEdenSetting()
        shell.setThorSetups(notes.thor)
        shell.setSaveSync(notes.sync, notes.setup)
        shell.refreshPlayerSaves()
        shell.noteShelfChanged()
    }

    private val homeSaveReturns = HomeSaveReturns()

    internal fun noteHomePaused() = homeSaveReturns.notePaused()

    internal fun refreshSavesOnReturn(returnedFromGame: Boolean) {
        if (homeSaveReturns.needsRefresh(returnedFromGame)) refreshFoundSaveFolders()
    }

    internal fun saveFolderStart(player: Player?): String? = (player as? SaveFolderHolder)?.let {
        (if (player is app.foldcade.plugins.retroarch.RetroArchPlayer)
            app.foldcade.plugins.retroarch.DEFAULT_SAVE_FOLDER else null)
            ?: saveFolderStarts[player.saveFolderId()] ?: it.suggestedSaveFolder ?: it.saveFolderPlaces.firstOrNull()?.let(::primaryFolderDocument)
    }

    /** True when the picker opens at a folder that is there: a place found on the device, or the player's own. */
    internal fun saveFolderOpensThere(player: Player?): Boolean = (player as? SaveFolderHolder)?.let {
        saveFolderStarts[player.saveFolderId()] != null || it.suggestedSaveFolder != null
    } == true

    /**
     * How Android's picker meets [player]'s save folder, for Set up saves: the root of the
     * player's own files, which Android shows alone, a folder that is there, or nowhere known.
     */
    internal fun saveFolderPick(player: Player): app.foldcade.language.FolderPick =
        (player as? SaveFolderHolder)?.let { saveFolderPickOf(it.suggestedSaveFolder, saveFolderOpensThere(player)) }
            ?: app.foldcade.language.FolderPick.Browse

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

    private val thorSetupNotices = app.foldcade.language.ThorSetupNotices()

    /** Apply before launch; publishing one settings snapshot and a notice never delays Home drawing. */
    internal suspend fun applyThorPreset(player: Player) {
        if (player.id != "azahar") return
        var appliedNow = false
        val result = withContext(Dispatchers.IO) {
            thorPresets.apply(player.id, playerSaveFolder(player)) { appliedNow = true }
        } ?: return
        withContext(Dispatchers.Main.immediate) {
            shell.setThorSetup(player.id, result)
            if (result.status in setOf(app.foldcade.language.ThorSetupStatus.Applied, app.foldcade.language.ThorSetupStatus.Kept, app.foldcade.language.ThorSetupStatus.Restored)) {
                dismissNotice(app.foldcade.language.ThorSetupCopy.noticePrefix + player.id)
            }
            thorSetupNotices.update(player.id, result.status, System.currentTimeMillis(), appliedNow)?.let(::postNotice)
        }
    }

    /**
     * Reads whether each player's saves sync, off the main thread, and updates
     * Settings. A card image is large, so this is not part of drawing a frame.
     */
    fun refreshSaveSync() {
        if (!::plugins.isInitialized || !::shell.isInitialized) return
        saveRefreshes.request(discover = false)
    }

    internal fun noteSaveLaunch(route: GameRoute) {
        shell.savePlayer(route)?.let { saveSetupNotices.launch(shell.saveNoticePlayerId(it.id)) }
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
        val settled = waitingSaveNotices.settled(foldcadeNotices)
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
        perfMark("health")
        val sync = linkedMapOf<String, app.foldcade.api.plugin.SaveSyncState>()
        val setup = linkedMapOf<String, String>()
        for (id in plugins.playerIds()) {
            val player = plugins.player(id) ?: continue
            if (player !is app.foldcade.api.plugin.SaveFolderHolder) continue
            val uri = playerSaveFolder(player) ?: continue
            playerSaves.saveSync(player, uri)?.let { sync[player.id] = it }
            playerSaves.setupSteps(player, uri)?.let { setup[player.id] = it }
        }
        // Eden keeps Switch saves per user, so one with none has nowhere to put them. Reading its
        // provider can start Eden, so a folder that has shown a user is not read again.
        val eden = plugins.player("eden") as? EdenPlayer
        eden?.let(::playerSaveFolder)?.takeIf { it != edenUserFolder }?.let { folder ->
            if (playerSaves.edenHasUser(eden, folder)) edenUserFolder = folder else setup.putIfAbsent(eden.id, Copy.edenUserSteps)
        }
        val thor = listOfNotNull(plugins.player("azahar")).associate { player ->
            player.id to thorPresets.read(player.id, playerSaveFolder(player))!!
        }
        return PlayerFolderNotes(sync, setup, thor)
    }

    /** The Eden folder last seen holding a user. Users are not taken away in passing. */
    @Volatile
    private var edenUserFolder: String? = null

    private data class PlayerFolderNotes(
        val sync: Map<String, app.foldcade.api.plugin.SaveSyncState>,
        val setup: Map<String, String>,
        val thor: Map<String, app.foldcade.language.ThorSetupSetting>,
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

    /** Game-specific resolution is shared with save health and every shell hint. */
    fun playerFor(gameId: String?, platformId: String): Player? =
        playerFor(gameId, platformId, ::packageInstalled)

    /** A catalog load passes one package snapshot; launches keep the live default above. */
    private fun playerFor(gameId: String?, platformId: String, installed: (String) -> Boolean): Player? =
        gameId?.let { playerResolution(it, platformId, installed).player } ?: shell.playerResolution(null, platformId, installed).player

    internal fun playerResolution(
        gameId: String, platformId: String, installed: (String) -> Boolean = ::packageInstalled,
    ): PlayerResolution {
        val resolved = shell.playerResolution(gameId, platformId, installed)
        // A game noted as running stays with its emulator, through Restart and a picked save too.
        // A changed choice applies once that run is closed, or its emulator is uninstalled.
        val running = runningOn(gameId)?.takeIf { installed(it.packageName) }?.player
        return if (running == null) resolved else resolved.copy(player = running, skipped = emptyList())
    }

    /**
     * True when starting [gameId] may open RetroArch: it is installed for the platform, and it
     * is what the game resolves to now or what a saved choice names. Its cores are read first
     * then, since the core decides both the choice and where RetroArch keeps the save. Other
     * launches skip the read, which starts RetroArch's process.
     */
    internal fun mayOpenRetroArch(gameId: String, platformId: String): Boolean {
        val players = plugins.playersFor(platformId)
        val retroArch = players.filterIsInstance<RetroArchPlayer>().filter { it.packageNames.any(::packageInstalled) }
        if (retroArch.isEmpty()) return false
        if (playerResolution(gameId, platformId).player is RetroArchPlayer) return true
        val saved = listOfNotNull(shell.gamePlayerChoice(gameId), store.systemPlayerChoice(shell.canonicalSystem(platformId)))
        return saved.any { choice -> retroArch.any { it.id == choice.playerId } }
    }

    private data class ChoiceNotice(val platformId: String, val choice: PlayerChoice)
    private val playerChoiceNotices = mutableMapOf<String, ChoiceNotice>()

    /** Called only once the resolved player actually starts. The saved preference stays. */
    internal fun notePlayerChoiceLaunch(gameId: String, platformId: String, resolution: PlayerResolution) {
        val label = playerChoiceLabel(resolution.player) ?: return
        val choices = buildList {
            add("game:${shell.tileOf(gameId)}" to shell.gamePlayerChoice(gameId))
            if (resolution.source != PlayerChoiceSource.Game) {
                val system = shell.canonicalSystem(platformId)
                add("system:$system" to store.systemPlayerChoice(system))
            }
        }
        for ((scope, choice) in choices) {
            val id = "foldcade:player-choice:$scope"
            if (choice != null && choice in resolution.skipped) {
                playerChoiceNotices[id] = ChoiceNotice(platformId, choice)
                postNotice(FoldNotice(id, app.foldcade.language.PlayerChoiceCopy.opened(label),
                    app.foldcade.language.PlayerChoiceCopy.unavailable(choice), postedMillis = System.currentTimeMillis(),
                    // A system's choice is fixed in Settings › Emulators; a game's in its own Y menu.
                    settingsCategory = app.foldcade.language.SettingsCategory.Players.takeIf { scope.startsWith("system:") }))
            } else if (foldcadeNotices.any { it.id == id }) dismissNotice(id)
        }
    }

    private fun clearUsablePlayerChoiceNotices() {
        for ((id, saved) in playerChoiceNotices.toMap()) {
            val resolved = resolvePlayer(plugins.playersFor(saved.platformId), gameChoice = saved.choice,
                installed = { it.packageNames.any(::packageInstalled) },
                coreFiles = { player -> (player as? RetroArchPlayer)?.let { it.packageNames.firstOrNull(::packageInstalled)?.let(it.cores::coreFiles) } })
            if (resolved.source == PlayerChoiceSource.Game) {
                playerChoiceNotices.remove(id)
                dismissNotice(id)
            }
        }
    }

    internal val retroArchCoreReader: RetroArchCoreReader by lazy { RetroArchCoreReader(this, store::retroArchCoresFolder) }
    internal val retroArchCoreDownload by lazy {
        RetroArchCoreDownload {
            java.io.File(externalMediaDirs.firstOrNull() ?: throw java.io.IOException("Shared storage unavailable"), "retroarch-cores")
        }
    }

    /** The granted Eden folder, per installed package. */
    internal val edenFolderReader: EdenFolderReader by lazy {
        EdenFolderReader(this, store::edenFolder) { store.playerSaveFolder("eden") }
    }

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

    /** Switch keys and firmware on the signed-in RomM server. */
    internal val rommFirmware: RommFirmware by lazy { RommFirmware { RommPlugins.wiring } }

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
                override suspend fun server() = rommFirmware.listSwitch()

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

    /** Eden has one grant for saves, keys and firmware. Other players use their save-folder store. */
    internal fun playerSaveFolder(player: Player): String? = if (player is EdenPlayer) {
        installedEdenPackage(player, ::packageInstalled)?.let { edenFolderReader.granted(it)?.toString() }
    } else if (player is SaveFolderHolder) {
        store.playerSaveFolder(player.saveFolderId())
    } else null

    /** Settings › Emulators › Eden keys and firmware. Null while Eden is not installed. */
    internal fun refreshEdenSetting() {
        val installed = EDEN_PACKAGES.firstOrNull(::packageInstalled)
        val folder = installed?.let { edenFolderReader.granted(it)?.toString() }
        (plugins.player("eden") as? EdenPlayer)?.bindSaveFolder(folder.orEmpty())
        val setting = installed?.let { EdenSetting(fromRomm = folder != null, packageName = it) }
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
        val changed = player.cores.update(byPackage)
        val active = player.cores.activePackage()
        val setting = active?.let { RetroArchSetting(cores = byPackage[it]?.size, packageName = it) }
        // The cores decide the Systems and Open with choices too, so a changed set republishes them.
        withContext(Dispatchers.Main.immediate) { if (changed || shell.model.retroArch != setting) shell.setRetroArch(setting) }
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
        val normal = chooseStart(intent, other, store.lastPlayerStart(intent.packageName), store.saveAwaitsLoad(player.saveFolderId()))
        return launchWork.retroArchCore?.sideload(normal) ?: normal
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
        val key = startKey(launchWork.retroArchCore?.installedIntent(intent) ?: intent)
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
        shelfPinsReady.await()
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
            if (read != null) {
                shell.settleMoonlight()
            }
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
    private suspend fun putMoonlightOnShelf(reportedHost: String? = null) {
        val games = plugins.moonlightShelfGames()
        withContext(Dispatchers.Main.immediate) { shell.publishMoonlightCatalog(games, reportedHost) }
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

    fun forgetMoonlightHost(key: String) {
        shell.reportRestoreSources(setOf("moonlight:$key"))
        shell.noteShelfChanged()
        moonlightStreams.forget(key)
    }

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
        shelfPinsReady.await()
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
    private val droidDeckPublish = Mutex()

    private suspend fun publishDroidDeck(): Unit = droidDeckPublish.withLock {
        val library = plugins.library(DroidDeckLibrary.ID) as? DroidDeckLibrary ?: return@withLock
        val files = steamFiles
        val pinsRead = droidDeckShortcutsRead
        library.replaceGames(droidDeckPinned + files.route.droidDeck)
        Shelf.droidDeckInstalled = packageInstalled(DroidDeckPlayer.PACKAGE)
        Shelf.droidDeckGames = plugins.droidDeckShelfGames()
        // With no folder there is no scan to wait for.
        val settled = pinsRead && (files.reported || libraryTrees().isEmpty())
        withContext(Dispatchers.Main.immediate) {
            if (settled) {
                if (Shelf.droidDeckInstalled && store.restoreFolders().isEmpty()) shell.reportRestoreSources(setOf("droiddeck"))
                shell.settleDroidDeck()
            }
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
     * Gives the saved RomM server its origin. Editing an origin calls this again
     * and then [syncRomm], which reconnects. Shared roots stay on [app.foldcade.plugins.romm.RommPlugins].
     */
    private fun configureSavedRomm() {
        if (plugins.library(RommCredentials.PLUGIN_ID) == null) return
        val origin = store.rommOrigin()
        if (!origin.isNullOrBlank()) {
            plugins.configure(RommCredentials.PLUGIN_ID, mapOf(ROMM_ORIGIN_KEY to origin))
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
            libraryAuthority = libraryAuthority,
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
        // A pick that covers a found folder, or sits in one, lists those games itself.
        synchronized(foundRootsLock) {
            store.addFolderTree(uri.toString())
            val picked = pickedStorageFolders(store.folderTrees())
            store.setFoundRoots(store.foundRoots().filter { id -> picked.none { containsFolder(it, id) || containsFolder(id, it) } })
        }
        val picked = uri.toString()
        val reload = reloadFolder()
        refreshFoundSaveFolders()
        // A folder that brings nothing says so where it was picked, with the way to pick again.
        scope.launch(Dispatchers.Main.immediate) {
            reload.join()
            // Art and info are sorted out after the scan, and a folder of art alone is no empty pick.
            mediaIndexJob?.join()
            if ((plugins.library(LocalFolderBackend.ID) as? LocalFolderBackend)?.mediaScan() == null) return@launch
            val folder = libraryFolders().firstOrNull { it.uri == picked } ?: return@launch
            val read = boundForest?.reportedTrees?.contains(picked) == true
            if (!read || folder.games > 0 || folder.media) return@launch
            val screen = shell.model.settings?.screen ?: shell.model.layout.menuScreen
            shell.present(app.foldcade.language.emptyFolderDialog(folder.label, screen))
        }
    }

    /**
     * Stops reading [uri]. Its games, and its .steam files, leave the grid. A found folder,
     * or a picked one in Android's own storage, is never found again by itself.
     */
    fun forgetFolder(uri: String) {
        val found = synchronized(foundRootsLock) {
            store.foundRoots().firstOrNull { romRootTree(libraryAuthority, it) == uri }?.also(store::removeRoot)
                ?: run { pickedStorageFolders(listOf(uri)).forEach(store::removeRoot); null }
        }
        if (found != null) {
            reloadFolder()
            return
        }
        shell.removeFolderTree(uri)
        releaseUnusedFolderGrant(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        reloadFolder()
    }

    /** Removing one saved reference must not revoke another saved folder's access. */
    fun releaseUnusedFolderGrant(uri: String, flags: Int) {
        if (store.folderTreeInUse(uri)) return
        try {
            contentResolver.releasePersistableUriPermission(Uri.parse(folderGrantUri(uri)), flags)
        } catch (_: SecurityException) {
            // Already released.
        }
    }

    /** The folders the library reads, named for the Sources rows. */
    fun libraryFolders(): List<LibraryFolder> {
        val scan = (plugins.library(LocalFolderBackend.ID) as? LocalFolderBackend)?.mediaScan()
        val gameUris = scan?.let { it.games.map { game -> game.documentUri } + it.steamShortcuts.map { game -> game.documentUri } }.orEmpty()
        val sources = localMedia?.index()?.sourceUris.orEmpty()
        val grantOf = { uri: String -> runCatching { folderGrantUri(uri) }.getOrNull() }
        val gamesIn = gameUris.groupingBy(grantOf).eachCount()
        val mediaIn = sources.mapNotNullTo(HashSet(), grantOf)
        val picked = store.libraryFolders().map { folder ->
            val grant = grantOf(folder.uri)
            folder.copy(
                artAndInfo = isMediaOnlyLibraryTree(folder.uri, sources, gameUris),
                path = runCatching { folderPathLabel(folder.uri) }.getOrNull(),
                games = gamesIn[grant] ?: 0,
                media = grant in mediaIn,
            )
        }
        val found = foundRootIds().map { id ->
            val tree = romRootTree(libraryAuthority, id)
            // The tile already names a card, so the page's line gives only its folder.
            LibraryFolder(tree, romRootTileLabel(id), path = id.substringAfter(':'), games = gamesIn[tree] ?: 0, media = tree in mediaIn, found = true)
        }
        return picked + found
    }

    /** [LibraryFilesProvider]'s authority in this build. */
    internal val libraryAuthority: String by lazy { packageName + LibraryFilesProvider.AUTHORITY_SUFFIX }

    /** Android's all-files access when Foldcade last looked for ROM folders. Only [lookForRomRoots] writes it. */
    @Volatile
    private var allFilesHeld = false

    /** The found folders the library reads now: none without all-files access. Any thread. */
    internal fun foundRootIds(): List<String> = if (allFilesHeld) store.foundRoots() else emptyList()

    /** Every folder the library reads: the player's picks, then the ROM folders Foldcade found. */
    internal fun libraryTrees(): List<String> = store.folderTrees() + foundRootIds().map { romRootTree(libraryAuthority, it) }

    /** What one look for ROM folders saw, and whether the folders the library reads changed. */
    private class RomRootLook(val seen: List<RomRoot>, val changed: Boolean)

    private val romRootLook = Mutex()
    private val startupRootsLooked = CompletableDeferred<Unit>()
    private var romRootRefresh: Job? = null

    /**
     * Looks for `ROMs`, `roms` and `Emulation/roms` on every volume ([findRomRoots]). With
     * all-files access the ones no pick covers and the player has not removed become found
     * folders; a card that is out keeps its folders, so their games come back with it. Without
     * it nothing changes, and what was seen is only offered. Reads the disk: under [romRootLook],
     * never on Main.
     */
    private fun lookForRomRoots(): RomRootLook {
        val held = Environment.isExternalStorageManager()
        val wasHeld = allFilesHeld
        val volumes = try {
            mountedVolumes(this)
        } catch (failure: RuntimeException) {
            Log.w("Foldcade", "Storage volumes unreadable", failure)
            emptyList()
        }
        val listed = findRomRoots(volumes)
        // The disk is read; picks and removals are read now, under the lock Remove and Add a folder take.
        val (seen, before, next) = synchronized(foundRootsLock) {
            val picked = pickedStorageFolders(store.folderTrees())
            val removed = store.removedRoots()
            val seen = usableRomRoots(listed, picked, removed)
            val before = store.foundRoots()
            val next = if (!held) before else {
                val mounted = volumes.mapTo(HashSet()) { it.id }
                val away = before.filter { it.substringBefore(':') !in mounted && romRootUsable(it, picked, removed) }
                (seen.map { it.documentId } + away).distinct()
            }
            if (next != before) store.setFoundRoots(next)
            Triple(seen, before, next)
        }
        allFilesHeld = held
        return RomRootLook(seen, next != before || held != wasHeld && next.isNotEmpty())
    }

    /** Guards the found folders' store against a look that read the disk before a Remove or a pick. */
    private val foundRootsLock = Any()

    /**
     * Back on Home: Android's all-files page may have been answered, or a card put in. Looks
     * again off Main and scans only when the found folders changed, keeping the grid as it is
     * meanwhile. Then the one-time asks get their turn, see [offerSetupAsks].
     */
    internal fun refreshRomRoots() {
        if (romRootRefresh?.isActive == true) return
        romRootRefresh = pluginLoad.launch {
            startupRootsLooked.await()
            val look = romRootLook.withLock { lookForRomRoots() }
            // Games found just now count for the asks: RetroArch's cores ask names their systems.
            if (look.changed) reloadFolder(startup = true).join()
            val roleHeld = getSystemService(android.app.role.RoleManager::class.java)
                .isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
            val apps = listOfNotNull(
                MoonlightLibrary.DISPLAY_NAME.takeIf { packageInstalled(app.foldcade.plugins.moonlight.MoonlightPlayer.OFFICIAL_PACKAGE) },
                GameNativeLibrary.DISPLAY_NAME.takeIf { gameNativePackagePresent() },
                DroidDeckLibrary.DISPLAY_NAME.takeIf { packageInstalled(DroidDeckPlayer.PACKAGE) },
            )
            withContext(Dispatchers.Main.immediate) { offerSetupAsks(look.seen, roleHeld, apps) }
        }
    }

    /**
     * At most one ask per return Home, each once: first reading the ROM folders Foldcade saw,
     * then being Home. A restore's notice goes first.
     */
    private fun offerSetupAsks(seen: List<RomRoot>, roleHeld: Boolean, apps: List<String>) {
        shell.setHomeRoleHeld(roleHeld)
        val places = seen.map { romRootProse(it.documentId) }.distinct()
        val systems = seen.flatMap { it.systems }.distinct()
        // What was seen but can't be read yet leads Home's ways to add games, until access is allowed.
        shell.setRomFolderOffer(seen.firstOrNull()?.takeIf { !allFilesHeld }?.let { RomFolderOffer(places, systems, it.documentId) })
        // A ROM folder seen on this device: Add a folder's picker opens there, not at the top of storage, where Android can't use the folder.
        shell.setFolderStart(seen.firstOrNull()?.documentId)
        if (setupBackups.hasNotice()) return
        if (shell.maybeOfferFindGames(places, systems)) return
        // The offer is still owed: it waits for a moment without a dialog rather than queue behind Home.
        if (!allFilesHeld && seen.isNotEmpty() && !store.findGamesOffered()) return
        // No ROM folder to offer: RomM, a phone code away, or a folder of the player's own.
        if (shell.maybeAskAddGames()) return
        if (shell.maybeOfferRetroArchCores()) return
        shell.maybeAskHome(roleHeld, apps)
    }

    /**
     * Games just arrived: RetroArch's cores ask, if their systems need it, comes now rather than
     * at the first launch. Main thread. A restore's notice and a dialog already up go first.
     */
    private fun offerCoresForNewGames() {
        if (setupBackups.hasNotice()) return
        shell.maybeOfferRetroArchCores()
    }

    /**
     * Tells R1 once how many games found folders brought, after the scan that first sees
     * games in them. A folder with none yet waits, so games copied in later are told then.
     * Main thread.
     */
    private fun announceFoundGames() {
        val announced = store.announcedRoots()
        val fresh = foundRootIds().filter { it !in announced }
        if (fresh.isEmpty()) return
        val scan = (plugins.library(LocalFolderBackend.ID) as? LocalFolderBackend)?.mediaScan() ?: return
        val uris = scan.games.map { it.documentUri } + scan.steamShortcuts.map { it.documentUri }
        val counts = uris.groupingBy { runCatching { folderGrantUri(it) }.getOrNull() }.eachCount()
        val told = fresh.mapNotNull { id -> counts[romRootTree(libraryAuthority, id)]?.takeIf { it > 0 }?.let { id to it } }
        if (told.isEmpty()) return
        store.setAnnouncedRoots(announced + told.map { it.first })
        val places = told.map { romRootPlace(it.first) }
        postNotice(FoldNotice(
            id = FOUND_GAMES_NOTICE_ID,
            title = app.foldcade.language.FoundFolderCopy.foundTitle(told.sumOf { it.second }, places),
            body = app.foldcade.language.FoundFolderCopy.foundBody(places),
            postedMillis = System.currentTimeMillis(),
            settingsCategory = app.foldcade.language.SettingsCategory.Library,
        ))
    }

    /** Routed games and their completed-folder report travel together, including across an in-flight shortcut read. */
    @Volatile
    private var steamFiles = SteamFileReport()

    /** Captured with its folder snapshot, then routed only after local publication. */
    private class FoundSteamFiles(val files: List<SteamShortcut>, val current: () -> Boolean)

    private suspend fun useSteamFiles(found: FoundSteamFiles) {
        shelfPinsReady.await()
        routeSteamFiles(found)
    }

    /** Splits the .steam files between GameNative and DroidDeck. See [routeSteamFiles]. */
    private val steamRouting = Mutex()

    private suspend fun routeSteamFiles(found: FoundSteamFiles? = null): Unit = steamRouting.withLock {
        // A newer scan or forgotten folder can supersede a report while pins are restoring.
        if (found != null && !found.current()) return@withLock
        val before = steamFiles
        val files = found?.files ?: before.files
        val route = routeSteamFiles(
            files,
            gameNativeInstalled = gameNativePackagePresent(),
            droidDeckInstalled = packageInstalled(DroidDeckPlayer.PACKAGE),
        )
        steamFiles = SteamFileReport(files, route, found != null || before.reported)
        if (!before.reported || route.gameNative != before.route.gameNative) {
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

    private val folderRefreshes = FolderRefreshes(pluginLoad, 5_000L, SystemClock::elapsedRealtime)

    /** The folders the backend is bound to, so a background walk can tell whether it reached them all. */
    @Volatile
    private var boundForest: FolderForest? = null

    private var mediaIndexJob: Job? = null

    /** Builds after the ROM list is ready, and publishes only for the scan still bound to this forest. */
    private suspend fun refreshLocalMedia(backend: LocalFolderBackend, forest: FolderForest) = withContext(Dispatchers.Main.immediate) {
        if (boundForest !== forest) return@withContext
        val scan = backend.mediaScan() ?: return@withContext
        mediaIndexJob?.cancel()
        mediaIndexJob = pluginLoad.launch {
            try {
                val context = coroutineContext
                val index = buildLocalMediaIndex(scan, forest::children, forest::readText) { !context.isActive }
                withContext(Dispatchers.Main.immediate) {
                    if (boundForest !== forest || backend.mediaScan() !== scan || libraryTrees().isEmpty()) return@withContext
                    if (localMedia?.replace(index) == true) {
                        covers.forget(LocalFolderBackend.ID) { true }
                        gameInfo.forget(LocalFolderBackend.ID)
                        artRevision++
                        infoRevision++
                    }
                    // A tree can gain or lose games without changing its indexed media.
                    shell.setFolders(libraryFolders())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("Foldcade", "Local media index failed", failure)
            }
        }
    }

    /**
     * Returning Home walks the folders again in the background, so a DroidDeck or
     * GameNative sync shows without a restart. The board stays as it is while the
     * walk runs, and an unchanged or unreadable folder leaves it alone, as does a
     * walk that could not reach every saved folder: a card that is briefly away
     * keeps its games on Home. A reload that binds the folders again wins. Returns the walk,
     * or the one already running.
     */
    fun refreshFolderQuietly(force: Boolean = false): Job = folderRefreshes.quietly(force) {
        pluginLoad.launch {
            playersLoaded.await()
            val saved = libraryTrees()
            if (saved.isEmpty()) return@launch
            val backend = plugins.library(LocalFolderBackend.ID) as? LocalFolderBackend ?: return@launch
            val forest = boundForest ?: return@launch
            val refresh = try {
                perfMark("folder-walk quiet")
                readFolderRefresh(backend, { forest.complete && boundForest === forest }) {
                    loadPlatforms(
                        plugins,
                        LocalFolderBackend.ID,
                        folderName = backend::folderName,
                        occupiesBoth = { playerFor(null, it)?.occupiesBothDisplays == true },
                        connect = false,
                    )
                } ?: return@launch
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (fatal: VirtualMachineError) {
                throw fatal
            } catch (failure: Exception) {
                Log.w("Foldcade", "Folder refresh failed", failure)
                return@launch
            }
            val loaded = refresh.library
            if (loaded == LoadedLibrary.Unreachable) return@launch
            val found = folderRead.withLock {
                if (boundForest !== forest) return@withLock null
                val scan = backend.mediaScan()
                FoundSteamFiles(backend.steamShortcuts()) {
                    boundForest === forest && backend.mediaScan() === scan && libraryTrees() == saved
                }
            }
            // loadPlatforms uses the completed rescan, so the media index uses that same snapshot.
            if (refresh.changed) refreshLocalMedia(backend, forest)
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
                if (libraryTrees().isEmpty() || boundForest !== forest) return@withContext
                if (!refresh.changed) {
                    // The cached catalog is still current. Report a reconnected empty tree
                    // without republishing a scan and invalidating an in-flight media index.
                    shell.finishFolderReport(true, forest.reportedTrees.toSet(),
                        empty = backend.mediaScan()?.platforms?.isEmpty() == true, plugins.platformDefinitions())
                    shell.noteShelfChanged()
                } else if (loaded is LoadedLibrary.Platforms) {
                    shell.reportRestoreFolders(forest.reportedTrees.toSet())
                    shell.ingestLibrary(
                        LocalFolderBackend.ID,
                        games,
                        plugins.platformDefinitions(),
                        loaded.entries.associate { (it.platformId ?: it.id) to it.title },
                    )
                } else {
                    shell.reportRestoreFolders(forest.reportedTrees.toSet())
                    shell.dropLibrary(LocalFolderBackend.ID)
                }
                val waiting = shell.model.libraryGrid && shell.model.emptyGrid != EmptyGrid.Loading
                if (waiting && shell.hasLibraryGames()) shell.showCuratedHome()
                // Games copied into a found folder that had none are told about now.
                if (refresh.changed) announceFoundGames()
                offerCoresForNewGames()
            }
            found?.let { useSteamFiles(it) }
        }
    }

    fun reloadFolder(startup: Boolean = false): Job {
        libraryRetry = { reloadFolder() }
        val ticket = libraryTicket.incrementAndGet()
        return folderRefreshes.record(pluginLoad.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            playersLoaded.await()
            if (ticket != libraryTicket.get()) return@launch
            val saved = libraryTrees()
            withContext(Dispatchers.Main.immediate) { shell.setFolders(libraryFolders()) }
            if (saved.isEmpty()) {
                withContext(Dispatchers.Main.immediate) {
                    if (ticket != libraryTicket.get()) return@withContext
                    mediaIndexJob?.cancel()
                    boundForest = null
                    localMedia?.replace(LocalMediaIndex.Empty)
                    covers.forget(LocalFolderBackend.ID) { true }
                    gameInfo.forget(LocalFolderBackend.ID)
                    artRevision++
                    infoRevision++
                    // The last folder was forgotten: its games leave the board.
                    shell.dropLibrary(LocalFolderBackend.ID)
                    // RomM's games are on the same board, so no folder is not no library. At startup
                    // (found folders whose access was turned off) Home keeps its board, as a fresh one does.
                    if (shell.hasLibraryGames() || startup) shell.showCuratedHome(fromStartup = startup) else shell.showNoLibrary()
                }
                useSteamFiles(FoundSteamFiles(emptyList()) { boundForest == null && libraryTrees().isEmpty() })
                return@launch
            }
            // Home can draw its saved places before this scan finishes. An early startup read
            // must not replace that first grid with Loading; an explicit reload still shows it.
            if (!startup) withContext(Dispatchers.Main.immediate) {
                if (ticket == libraryTicket.get()) shell.showLoading(insidePlatform = false)
            }
            if (ticket != libraryTicket.get()) return@launch
            val backend = plugins.library(LocalFolderBackend.ID) as? LocalFolderBackend
            if (backend == null) {
                showFailure(ticket, insidePlatform = false)
                return@launch
            }
            val read = readFolders(ticket, backend, saved)
            deliverLibrary(ticket, LocalFolderBackend.ID, read.library, startup = startup,
                hasSteamFiles = read.steam?.files?.isNotEmpty() ?: steamFiles.files.isNotEmpty())
            withContext(Dispatchers.Main.immediate) {
                if (ticket != libraryTicket.get()) return@withContext
                announceFoundGames()
                // A folder just chosen: its games may need RetroArch's cores. Startup asks in its own order.
                if (!startup) offerCoresForNewGames()
            }
            read.steam?.let { useSteamFiles(it) }
        }).also { it.start() }
    }

    /** One folder read at a time, since each binds the backend to its own tree. */
    private val folderRead = Mutex()

    private data class FolderRead(val library: LoadedLibrary?, val steam: FoundSteamFiles? = null)

    /** Scans [saved] and captures their .steam files without waiting for shortcut restoration. */
    private suspend fun readFolders(ticket: Int, backend: LocalFolderBackend, saved: List<String>): FolderRead = folderRead.withLock {
        if (ticket != libraryTicket.get()) return@withLock FolderRead(null)
        try {
            val forest = FolderForest(contentResolver, saved.map(Uri::parse))
            backend.bindTree(forest.root(), forest::children)
            boundForest = forest
            perfMark("folder-walk reload")
            val loaded = loadPlatforms(
                plugins,
                LocalFolderBackend.ID,
                folderName = backend::folderName,
                occupiesBoth = { playerFor(null, it)?.occupiesBothDisplays == true },
            )
            // A failed read says nothing about the .steam files, and a newer reload has its own.
            val found = if (loaded != LoadedLibrary.Unreachable && ticket == libraryTicket.get()) {
                refreshLocalMedia(backend, forest)
                val scan = backend.mediaScan()
                if (forest.complete) FoundSteamFiles(backend.steamShortcuts()) {
                    boundForest === forest && backend.mediaScan() === scan && libraryTrees() == saved
                } else null
            } else null
            FolderRead(loaded, found)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (fatal: VirtualMachineError) {
            throw fatal
        } catch (failure: Exception) {
            Log.w("Foldcade", "Folder library failed", failure)
            FolderRead(null)
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
        homeShownNow = shown
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
        from.startOnDisplay(Intent(from, RestartActivity::class.java),
            android.view.Display.DEFAULT_DISPLAY, multipleTask = false)
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
            libraryTrees().isEmpty() -> null
            !model.libraryGrid -> refreshFolderQuietly(force = true)
            // A failed read tries again, and an empty folder looks again. An open platform is left alone.
            model.gridKind == GridKind.Unreachable -> retryLibrary()
            model.emptyGrid == EmptyGrid.NoPlatforms -> reloadFolder()
            else -> null
        }
        val romm = joinOrSyncRomm()
        withContext(Dispatchers.IO) {
            // After the folders, which route their .steam files into the catalog.
            folders?.join()
            refreshGameNative()
        }
        romm.join()
        // Folders always answer; RomM said its own outcome as it ended.
        withContext(Dispatchers.Main.immediate) { shell.noteLibrarySynced() }
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
            playersLoaded.await()
            if (ticket != libraryTicket.get()) return@launch
            withContext(Dispatchers.Main.immediate) {
                if (ticket == libraryTicket.get()) shell.showLoading(insidePlatform = true)
            }
            val installed = plugins.playerPackageNames().filterTo(HashSet(), ::packageInstalled)
            val games = try {
                val folderName = (plugins.library(libraryId) as? LocalFolderBackend)?.let { backend ->
                    backend::folderName
                } ?: { "" }
                loadGames(
                    plugins,
                    libraryId,
                    platformId,
                    folderName = folderName,
                    occupiesBoth = { playerFor(null, it, installed::contains)?.occupiesBothDisplays == true },
                    gameOccupiesBoth = { game -> playerFor(shell.tileOf(homeGameId(game.backendId, game.remoteKey)), game.platformId, installed::contains)?.occupiesBothDisplays == true },
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
        val sync = syncRomm()
        // After RomM has reconnected: a read racing the reconnect fails as if RomM were down (seen on the Thor).
        scope.launch(Dispatchers.Main.immediate) {
            sync.join()
            onNetworkBack?.invoke()
        }
    }

    /** Home's hook for a validated network coming back, as to reload a Saves page that had none. */
    var onNetworkBack: (() -> Unit)? = null

    /** True while Android has a network it has validated, so a failed RomM read is RomM's, not the device's. */
    internal fun hasValidatedNetwork(): Boolean = try {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        connectivity?.getNetworkCapabilities(connectivity.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    } catch (_: SecurityException) {
        true
    }

    /**
     * Puts RomM's games on the same home board as the folder's, or takes them off
     * once no server is saved. The grid does not change mode while this runs. A
     * server that cannot be reached leaves the board as it was, placed tiles included.
     */
    /** Launch waits join startup's refresh instead of repeatedly cancelling it. */
    fun joinOrSyncRomm(): Job = synchronized(rommTicket) {
        rommRefreshJob?.takeIf { it.isActive } ?: syncRomm()
    }

    fun syncRomm(): Job {
        synchronized(rommTicket) {
            val ticket = rommTicket.incrementAndGet()
            val readAt = shell.deviceMark()
            rommRefreshJob?.cancel()
            return pluginLoad.launch {
                rommReady.await()
                if (ticket != rommTicket.get()) return@launch
                configureSavedRomm()
                val libraryId = RommCredentials.PLUGIN_ID
                if (store.rommOrigin() == null) {
                    withContext(Dispatchers.Main.immediate) {
                        if (ticket == rommTicket.get()) shell.dropLibrary(libraryId)
                    }
                    return@launch
                }
                try {
                    app.foldcade.plugins.romm.refreshRommCatalog(
                        plugins.library(libraryId),
                        cached = { snapshot ->
                            val savedGames = gameEntries(
                                plugins, libraryId, snapshot.games,
                                gameOccupiesBoth = { game -> playerFor(shell.tileOf(homeGameId(game.backendId, game.remoteKey)), game.platformId)?.occupiesBothDisplays == true },
                            )
                            withContext(Dispatchers.Main.immediate) {
                                if (ticket != rommTicket.get() || store.rommOrigin() == null) return@withContext
                                shell.ingestLibrary(
                                    libraryId, savedGames, plugins.platformDefinitions(),
                                    snapshot.platforms.associate { it.platformId to it.displayName },
                                    readAt = readAt,
                                    restoreReported = false,
                                    settles = false,
                                )
                                val waiting = shell.model.libraryGrid && shell.model.emptyGrid != EmptyGrid.Loading
                                if (waiting && savedGames.isNotEmpty()) shell.showCuratedHome()
                            }
                        },
                    ) {
                        val listed = when (val loaded = loadPlatforms(
                            plugins,
                            libraryId,
                            occupiesBoth = { playerFor(null, it)?.occupiesBothDisplays == true },
                        )) {
                            is LoadedLibrary.Platforms -> loaded
                            LoadedLibrary.NoPlatforms -> null
                            LoadedLibrary.Unreachable -> {
                                // Said where Sync games was pressed, and on RomM's tile, until a read gets through.
                                withContext(Dispatchers.Main.immediate) { if (ticket == rommTicket.get()) shell.setRommReached(false) }
                                return@refreshRommCatalog
                            }
                        }
                        val games = listed?.let { loadEveryGame(libraryId, it) }.orEmpty()
                        // A list read with the saved token means it works again, after a refusal that passed.
                        val tokenWorks = lookupOrUnreadable(store.restoreCredentials(credentials), RommCredentials.PLUGIN_ID, RommCredentials.ACCESS_TOKEN) is CredentialLookup.Present
                        withContext(Dispatchers.Main.immediate) {
                            if (ticket != rommTicket.get() || store.rommOrigin() == null) return@withContext
                            // Only a live answer counts: an offline cache would read with a refused token too.
                            val live = app.foldcade.plugins.romm.rommCatalogLive(plugins.library(libraryId))
                            if (tokenWorks && live) shell.rommAccepted()
                            // A list from the saved copy means RomM didn't answer this time.
                            shell.setRommReached(live)
                            shell.ingestLibrary(
                                libraryId,
                                games,
                                plugins.platformDefinitions(),
                                listed?.entries.orEmpty().associate { (it.platformId ?: it.id) to it.title },
                                readAt = readAt,
                                restoreReported = app.foldcade.plugins.romm.rommCatalogLive(plugins.library(libraryId)),
                            )
                            val waiting = shell.model.libraryGrid && shell.model.emptyGrid != EmptyGrid.Loading
                            if (waiting && games.isNotEmpty()) shell.showCuratedHome()
                            offerCoresForNewGames()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: PluginException.NotAuthenticated) {
                    // A restore leaves the token out on purpose, so its sign-in is still to do: R1's notice, RomM's
                    // tile and its places already say so, and it doesn't interrupt every launch with a dialog.
                    // A token RomM refused, or one that could not be read, still asks.
                    val restored = store.restoreRommNeedsSignIn()
                    withContext(Dispatchers.Main.immediate) {
                        if (ticket == rommTicket.get() && store.rommOrigin() != null) {
                            // Refused, not unreachable: until a sign-in its saves wait, and RomM's places say so.
                            shell.rommRefused()
                            if (!restored) shell.askToSignInAgain()
                        }
                    }
                } catch (failure: Exception) {
                    Log.w("Foldcade", "RomM library failed", failure)
                    withContext(Dispatchers.Main.immediate) { if (ticket == rommTicket.get() && store.rommOrigin() != null) shell.setRommReached(false) }
                }
            }.also { rommRefreshJob = it }
        }
    }

    /**
     * A library with platforms lands on the curated home grid.
     * Games are read here, off the main thread, then the board is updated.
     */
    private suspend fun deliverLibrary(
        ticket: Int,
        libraryId: String,
        loaded: LoadedLibrary?,
        startup: Boolean = false,
        hasSteamFiles: Boolean = false,
    ) {
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
            val onlyFound = libraryId == LocalFolderBackend.ID && store.folderTrees().isEmpty() && foundRootIds().isNotEmpty()
            val folderReported = libraryId == LocalFolderBackend.ID && loaded != LoadedLibrary.Unreachable && loaded != null &&
                (loaded !is LoadedLibrary.Platforms || games != null) && shell.finishFolderReport(
                    boundForest?.complete == true, boundForest?.reportedTrees?.toSet().orEmpty(),
                    empty = loaded == LoadedLibrary.NoPlatforms, plugins.platformDefinitions(),
                )
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
                    shell.showCuratedHome(fromStartup = startup)
                    perfMark("library-published games=${games.size}")
                }
                // A folder of .steam files has games, just none of its own: GameNative's, DroidDeck's, or both.
                // Folders Foldcade found by itself never take Home over: an empty one (ES-DE makes
                // every system folder) has no games yet, and an unreachable one is a card that is out.
                LoadedLibrary.NoPlatforms -> if ((hasSteamFiles || onlyFound) && folderReported) {
                    shell.dropLibrary(libraryId)
                    shell.showCuratedHome(fromStartup = startup)
                } else if (onlyFound) {
                    // A card that is out may still hold games: they stay until a complete scan says otherwise.
                    shell.showCuratedHome(fromStartup = startup)
                } else {
                    shell.showNoPlatforms(libraryId)
                }
                LoadedLibrary.Unreachable, null -> if (onlyFound) shell.showCuratedHome(fromStartup = startup)
                    else shell.showUnreachable(insidePlatform = false)
            }
        }
    }

    private suspend fun loadEveryGame(libraryId: String, loaded: LoadedLibrary.Platforms): List<GridEntry> {
        val installed = plugins.playerPackageNames().filterTo(HashSet(), ::packageInstalled)
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
                occupiesBoth = { playerFor(null, it, installed::contains)?.occupiesBothDisplays == true },
                gameOccupiesBoth = { game -> playerFor(shell.tileOf(homeGameId(game.backendId, game.remoteKey)), game.platformId, installed::contains)?.occupiesBothDisplays == true },
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

/** A kept cover's size: the grid's own tile size. */
private const val PLAY_ART_PX = 384

/** How long a hosted save folder waits, at most, for the players to load while Foldcade starts. */
private const val PLAYERS_LOAD_WAIT_MS = 10_000L

/** One notice for every folder found without asking; a later find replaces it. */
private const val SAVES_FOUND_NOTICE_ID = "foldcade:saves-found"
private const val FOUND_GAMES_NOTICE_ID = "foldcade:found-games"

/** How often a shown Home adds to the trial of the packs in use. */
private const val THEME_TRIAL_TICK_MILLIS = 15_000L

private val ABNORMAL_EXITS = setOf(
    ApplicationExitInfo.REASON_CRASH,
    ApplicationExitInfo.REASON_CRASH_NATIVE,
    ApplicationExitInfo.REASON_ANR,
)
