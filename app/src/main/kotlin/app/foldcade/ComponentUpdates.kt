package app.foldcade

import android.app.AlarmManager
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.runtime.snapshotFlow
import app.foldcade.language.*
import app.foldcade.net.PublicHttps
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

internal const val AUTO_UPDATE_INTERVAL_MS = 6 * 60 * 60_000L
internal const val AUTO_UPDATE_RETRY_MS = 30 * 60_000L
internal const val AUTO_UPDATE_RESTART_MS = 60 * 60_000L

/** Wall time only throttles a restart. All live-process deadlines use the elapsed clock. */
internal fun automaticStartupDelay(now: Long, lastSuccess: Long): Long =
    if (lastSuccess > 0 && now >= lastSuccess) (AUTO_UPDATE_RESTART_MS - (now - lastSuccess)).coerceAtLeast(30_000L) else 30_000L

/** One scheduler and one quiet-moment gate for the app and every installed registry plugin. */
internal class ComponentUpdates(private val app: FoldcadeApp) {
    private var store: UpdateStore? = null
    private val loading by lazy { app.scope.async {
        UpdateStore(File(app.noBackupFilesDir, "updates/ledger.json"), app.scope).also { loaded ->
            observeVersions(loaded)
            withContext(Dispatchers.Main.immediate) { store = loaded; publishNotices() }
        }
    } }
    private val main = Handler(Looper.getMainLooper())
    private val alarms = app.getSystemService(AlarmManager::class.java)
    private var screenOffAt: Long? = null
    private val screenOffAlarm = AlarmManager.OnAlarmListener { reevaluate() }
    private var lastInput = SystemClock.elapsedRealtime()
    private val idle = Runnable { reevaluate() }
    private var started = false
    private var wasHomeReady = false
    private val observations = Mutex()
    private val checkWake = Channel<Unit>(Channel.CONFLATED)
    /** A setting the player just changed asks for the scheduled check now, so what it selects is staged without waiting hours. */
    @Volatile private var checkSoon = false

    val selfEligible: Boolean by lazy {
        val signers = try { app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signerDigests() }
            catch (_: PackageManager.NameNotFoundException) { emptySet() }
        mayUpdateFoldcade(app.packageName, app.getString(R.string.foldcade_build_origin), signers)
    }

    fun label(packageName: String): String = when (packageName) {
        app.packageName -> if (packageName.endsWith(".debug")) "Foldcade Debug" else "Foldcade"
        else -> PluginRegistry.source(packageName)?.label ?: packageName
    }

    /** Package broadcasts and startup use the same comparison, including updates installed outside Foldcade. */
    fun observeInstalled() {
        app.scope.launch {
            val loaded = loading.await()
            observations.withLock {
                observeVersions(loaded)
                withContext(Dispatchers.Main.immediate) { publishNotices() }
            }
        }
    }

    private fun observeVersions(loaded: UpdateStore) {
        (listOf(app.packageName) + PluginRegistry.sources.map { it.packageName }).forEach { packageName ->
            try {
                val info = app.packageManager.getPackageInfo(packageName, 0)
                loaded.observe(packageName, label(packageName), info.longVersionCode, info.versionName.orEmpty(), System.currentTimeMillis())
            } catch (_: PackageManager.NameNotFoundException) { }
        }
    }

    fun start() {
        if (started) return
        started = true
        screenChanged()
        app.scope.launch {
            val loaded = loading.await()
            app.awaitPlugins()
            var nextCheck = SystemClock.elapsedRealtime() + automaticStartupDelay(System.currentTimeMillis(), loaded.lastAutomaticCheck)
            while (true) {
                if (checkSoon) { checkSoon = false; nextCheck = SystemClock.elapsedRealtime() }
                val remaining = nextCheck - SystemClock.elapsedRealtime()
                if (remaining > 0) {
                    // Screen-on wakes this wait too: time asleep counts on the elapsed clock.
                    withTimeoutOrNull(remaining) { checkWake.receive() }
                    continue
                }
                var success = true
                val enabled = withContext(Dispatchers.Main.immediate) { app.shell.model.automaticUpdates }
                for (catalog in app.apkPluginCatalogs.values) {
                    val installed = try { app.packageManager.getPackageInfo(catalog.source.packageName, 0); true }
                        catch (_: PackageManager.NameNotFoundException) { false }
                    if (installed) success = catalog.automaticCheck(enabled) && success
                }
                if (success) loaded.checked(System.currentTimeMillis())
                withContext(Dispatchers.Main.immediate) { reevaluate() }
                nextCheck = SystemClock.elapsedRealtime() + if (success) AUTO_UPDATE_INTERVAL_MS else AUTO_UPDATE_RETRY_MS
            }
        }
        app.scope.launch(Dispatchers.Main.immediate) {
            // Elapsed durations have their own deadlines; model changes should compare only gate state.
            snapshotFlow { gate().copy(idleMillis = 0, screenOffMillis = 0) }.collect { reevaluate() }
        }
        app.scope.launch(Dispatchers.Main.immediate) {
            var previous = app.shell.model.includePrereleases
            snapshotFlow { app.shell.model.includePrereleases }.collect { include ->
                if (include != previous && selfEligible) {
                    previous = include
                    app.apkPluginCatalogs[PluginRegistry.foldcade.packageName]?.channelChanged()
                    checkNow()
                }
            }
        }
        app.scope.launch(Dispatchers.Main.immediate) {
            var previous = app.shell.model.automaticUpdates
            snapshotFlow { app.shell.model.automaticUpdates }.collect { on ->
                // Turned back on: a release a check found while it was off is downloaded now, not at the next tick.
                if (on && !previous) checkNow()
                previous = on
            }
        }
        // About opens directly from the category list, unlike a plugin's inner page.
        app.scope.launch(Dispatchers.Main.immediate) {
            snapshotFlow {
                app.shell.model.settings?.let { settingsCategories(app.shell.model).getOrNull(it.category) == SettingsCategory.About }
            }.collect { about ->
                if (about == true && selfEligible) app.apkPluginCatalogs[PluginRegistry.foldcade.packageName]?.checkRelease(onlyIfStale = true)
            }
        }
    }

    private fun checkNow() {
        checkSoon = true
        checkWake.trySend(Unit)
    }

    /** Called for Home input and taps; a new deadline replaces the old one. */
    fun playerInput() {
        lastInput = SystemClock.elapsedRealtime()
        main.removeCallbacks(idle)
        main.postDelayed(idle, SELF_UPDATE_IDLE_MS)
    }

    /** A brief lid close must never authorize a process-killing self update. */
    fun screenChanged(off: Boolean = app.getSystemService(PowerManager::class.java)?.isInteractive == false) {
        if (off) {
            if (screenOffAt == null) {
                val off = SystemClock.elapsedRealtime()
                screenOffAt = off
                alarms?.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, off + SELF_UPDATE_SCREEN_OFF_MS,
                    "foldcade:self-update", screenOffAlarm, main)
            }
        } else {
            screenOffAt = null
            alarms?.cancel(screenOffAlarm)
        }
        reevaluate()
    }

    val pluginWorkInFlight: Boolean get() = app.apkPluginCatalogs.values.any {
        it.source != PluginRegistry.foldcade && it.installInFlight
    }

    private fun gate(): UpdateGate = UpdateGate(
        automatic = app.shell.model.automaticUpdates,
        screensFree = app.store.session.bothScreensFree(),
        saveSync = app.saveFlow.isLocked || app.shell.model.librarySyncing || app.shell.activity.values.any { it == TileActivity.Syncing },
        romDownload = app.shell.downloading.isNotEmpty() || app.shell.activity.values.any {
            it is TileActivity.Downloading || it == TileActivity.Checking || it == TileActivity.PreparingOnServer
        },
        themeInstall = app.updateThemeBusy,
        backup = app.backupWork.isLocked,
        launching = app.launchWork.active || app.launchWork.waitingToLight || app.suppressSaveConflict,
        screenOff = app.getSystemService(PowerManager::class.java)?.isInteractive == false,
        homeShown = app.updateHomeShown,
        idleMillis = SystemClock.elapsedRealtime() - lastInput,
        screenOffMillis = screenOffAt?.let { SystemClock.elapsedRealtime() - it } ?: 0,
        holdingPlayers = app.runningGames.holdingPackages(),
        pluginWork = pluginWorkInFlight,
    )

    fun mayInstall(packageName: String): Boolean = mayInstallUpdate(packageName, packageName == app.packageName, gate())

    /** Event driven: a gate change, Home return, screen-off or the idle deadline. */
    fun reevaluate() {
        if (!started) return
        checkWake.trySend(Unit)
        publishNotices()
        var gate = gate()
        val homeReady = gate.homeShown && gate.screensFree
        if (homeReady && !wasHomeReady) {
            lastInput = SystemClock.elapsedRealtime()
            gate = gate.copy(idleMillis = 0)
        }
        wasHomeReady = homeReady
        val catalogs = app.apkPluginCatalogs.values
        catalogs.filter { it.source != PluginRegistry.foldcade }.forEach { catalog ->
            if (mayInstallUpdate(catalog.source.packageName, false, gate)) catalog.installAutomatically()
        }
        // Starting a plugin above makes its job active immediately. Self goes last and checks again at commit.
        catalogs.firstOrNull { it.source == PluginRegistry.foldcade }?.let { self ->
            if (mayInstall(self.source.packageName)) self.installAutomatically()
        }
        main.removeCallbacks(idle)
        if (gate.idleMillis < SELF_UPDATE_IDLE_MS) main.postDelayed(idle, SELF_UPDATE_IDLE_MS - gate.idleMillis)
    }

    suspend fun keepRelease(packageName: String, release: PluginRelease) {
        loading.await().keepNotes(packageName, listOf(ReleaseNotes(release.versionCode, release.name.orEmpty(), release.body, release.tag == "pre-release", release.tag)))
    }

    fun publishNotices() {
        val store = store ?: return // Loaded history is observed before any update notice is published.
        val notices = store.unread().map { it.notice() }.toMutableList()
        app.apkPluginCatalogs.values.forEach { catalog ->
            val code = catalog.stagedCode
            if (catalog.needsConfirmation && code != null && !store.readyHidden(catalog.source.packageName, code)) {
                notices += readyUpdateNotice(catalog.source.packageName, catalog.source.label, catalog.stagedName.orEmpty(), catalog.stagedAt)
            }
        }
        app.replaceUpdateNotices(notices)
    }

    fun dismiss(ids: List<String>) {
        val store = store ?: return
        val read = mutableSetOf<String>()
        val ready = mutableMapOf<String, Long>()
        ids.forEach { id -> when {
            id.startsWith(UpdateCopy.updatedPrefix) -> read += id.removePrefix(UpdateCopy.updatedPrefix)
            id.startsWith(UpdateCopy.readyPrefix) -> {
                val packageName = id.removePrefix(UpdateCopy.readyPrefix)
                app.apkPluginCatalogs[packageName]?.stagedCode?.let { ready[packageName] = it }
            }
        } }
        if (read.isNotEmpty() || ready.isNotEmpty()) store.dismiss(read, ready)
    }

    suspend fun update(packageName: String): ComponentUpdate? = loading.await().update(packageName, label(packageName))
    suspend fun flush() = loading.await().flush()

    suspend fun notes(update: ComponentUpdate): List<ReleaseNotes> {
        val store = loading.await()
        store.cached(update)?.let { return it }
        val source = if (update.packageName == app.packageName) PluginRegistry.foldcade else PluginRegistry.source(update.packageName)
            ?: return emptyList()
        try {
            PublicHttps(setOf("api.github.com")).use { http ->
                val response = http.get("https://api.github.com/repos/${source.repository}/releases?per_page=30",
                    headers = mapOf("Accept" to "application/vnd.github+json"), maxBytes = 2 * 1024 * 1024)
                check(response.status == 200)
                val releases = JSONArray(response.body.decodeToString()).objects().filterNot { it.optBoolean("draft") }.map { release ->
                    val body = if (release.isNull("body")) "" else release.optString("body")
                    val name = body.lineSequence().firstOrNull { it.startsWith("versionName:") }?.substringAfter(':')?.trim()
                        ?: release.optString("tag_name").removePrefix("v")
                    ReleaseNotes(releaseVersionCode(body), name, body, release.optBoolean("prerelease"), release.optString("tag_name"))
                }
                store.keepNotes(update.packageName, releases, rangeKey(update))
                return store.known(update)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (failure: Exception) {
            // A downloaded release still has its notes when GitHub is offline or its rolling tag moved.
            return store.known(update).ifEmpty { throw failure }
        }
    }
}
