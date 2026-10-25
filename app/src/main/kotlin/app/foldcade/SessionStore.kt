package app.foldcade

import android.content.SharedPreferences
import app.foldcade.language.FALLBACK_THEME_ID
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.MotionSpeed
import app.foldcade.language.decodeSameGames
import app.foldcade.language.encodeSameGames
import app.foldcade.language.savedBeforeCollections
import app.foldcade.language.HomeMusicSetting
import app.foldcade.language.LibrarySort
import app.foldcade.language.PromptKey
import app.foldcade.plugins.droiddeck.DroidDeckGame
import app.foldcade.plugins.droiddeck.droidDeckGame
import app.foldcade.plugins.moonlight.MoonlightApp
import app.foldcade.plugins.moonlight.moonlightApp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Files were added to [packageName] while the device boot count was [bootCount]. */
data class EdenRestartPending(val packageName: String, val bootCount: Int)

class SessionStore(private val prefs: SharedPreferences) : SaveMarks {
    var session by mutableStateOf(load())
        private set

    init {
        // The Moonlight import sheet and its source switch are gone. Every pin is on Home.
        if (RETIRED_MOONLIGHT_KEYS.any(prefs::contains)) {
            prefs.edit().apply { RETIRED_MOONLIGHT_KEYS.forEach { remove(it) } }.apply()
        }
        // Declining save sync is a choice for one launch, never a saved preference.
        val retiredSaveOffers = prefs.all.keys.filter {
            it.startsWith("player_save_folder_skipped:") || it.startsWith("player_hosted_saves_skipped:")
        }
        if (retiredSaveOffers.isNotEmpty()) {
            prefs.edit().apply { retiredSaveOffers.forEach { remove(it) } }.apply()
        }
    }

    /** Fired after [session] changes. Home music watches games entering and leaving either screen. */
    var afterSessionChanged: (() -> Unit)? = null

    fun update(block: (Session) -> Session) {
        val next = block(session)
        if (next == session) return
        session = next
        save(next)
        afterSessionChanged?.invoke()
    }

    private fun load(): Session = Session(
        defaultDisplayIsTop = prefs.getBoolean(KEY_PRIMARY_TOP, true),
    )

    private fun save(session: Session) {
        prefs.edit()
            .putBoolean(KEY_PRIMARY_TOP, session.defaultDisplayIsTop)
            // The launch button picks the screen now; per-game and per-platform choices are gone.
            .remove(KEY_GAME_SCREENS)
            .remove(KEY_PLATFORM_SCREENS)
            .apply()
    }

    fun homePromptSettled(): Boolean = prefs.getBoolean(KEY_HOME_PROMPTED, false)

    fun setHomePromptSettled() {
        prefs.edit().putBoolean(KEY_HOME_PROMPTED, true).apply()
    }

    /**
     * Every folder the library reads, in the order they were added. One URI per
     * line, so a single folder saved before this list reads back as itself.
     */
    fun folderTrees(): List<String> =
        prefs.getString(KEY_FOLDER, null).orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun addFolderTree(uri: String) {
        saveFolderTrees((folderTrees() + uri).distinct())
    }

    fun removeFolderTree(uri: String) {
        saveFolderTrees(folderTrees() - uri)
    }

    private fun saveFolderTrees(trees: List<String>) {
        prefs.edit().putString(KEY_FOLDER, trees.joinToString("\n")).apply()
    }

    fun folderGrantPending(): Boolean = !prefs.getBoolean(KEY_FOLDER_EXPLAINED, false)

    fun setFolderGrantExplained() {
        prefs.edit().putBoolean(KEY_FOLDER_EXPLAINED, true).apply()
    }

    fun rommOrigin(): String? = prefs.getString(KEY_ROMM_ORIGIN, null)?.takeIf { it.isNotBlank() }

    fun setRommOrigin(origin: String) {
        prefs.edit().putString(KEY_ROMM_ORIGIN, origin).apply()
    }

    fun clearRommOrigin() {
        prefs.edit().remove(KEY_ROMM_ORIGIN).apply()
    }

    /** Every saved connection, with the original instance first. */
    fun rommServers(): List<Pair<String, String>> =
        listOfNotNull(rommOrigin()?.let { "romm" to it }) + rommInstances()

    fun rommServerOrigin(instanceId: String): String? =
        rommServers().firstOrNull { it.first == instanceId }?.second

    /**
     * Servers other than the default instance `romm`.
     * Each line is `instanceId` then a tab then the origin. `romm_origin` stays the default.
     */
    fun rommInstances(): List<Pair<String, String>> =
        prefs.getString(KEY_ROMM_INSTANCES, null).orEmpty().lineSequence().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@mapNotNull null
            val id = line.substring(0, tab).trim()
            val origin = line.substring(tab + 1).trim()
            if (id.isEmpty() || origin.isEmpty() || id == "romm") null else id to origin
        }.toList()

    fun setRommInstance(instanceId: String, origin: String) {
        val next = rommInstances().filter { it.first != instanceId } + (instanceId to origin)
        writeRommInstances(next)
    }

    fun removeRommInstance(instanceId: String) {
        writeRommInstances(rommInstances().filter { it.first != instanceId })
    }

    private fun writeRommInstances(rows: List<Pair<String, String>>) {
        if (rows.isEmpty()) {
            prefs.edit().remove(KEY_ROMM_INSTANCES).apply()
        } else {
            prefs.edit().putString(KEY_ROMM_INSTANCES, rows.joinToString("\n") { "${it.first}\t${it.second}" }).apply()
        }
    }

    fun musicEnabled(): Boolean = prefs.getBoolean(KEY_MUSIC_ENABLED, true)

    fun musicVolume(): Float =
        safeMusicVolume(prefs.getFloat(KEY_MUSIC_VOLUME, HomeMusicSetting.DEFAULT_VOLUME))

    fun musicTrackId(): String =
        prefs.getString(KEY_MUSIC_TRACK, HomeMusicSetting.DEFAULT_TRACK_ID)
            ?.trim()
            ?.ifEmpty { HomeMusicSetting.DEFAULT_TRACK_ID }
            ?: HomeMusicSetting.DEFAULT_TRACK_ID

    fun librarySort(): LibrarySort =
        if (prefs.getString(KEY_LIBRARY_SORT, null) == LibrarySort.RecentlyPlayed.name) {
            LibrarySort.RecentlyPlayed
        } else {
            LibrarySort.Listed
        }

    fun usagePromptOffered(): Boolean = prefs.getBoolean(KEY_USAGE_PROMPTED, false)

    fun setUsagePromptOffered() {
        prefs.edit().putBoolean(KEY_USAGE_PROMPTED, true).apply()
    }

    fun setLibrarySort(sort: LibrarySort) {
        prefs.edit().putString(KEY_LIBRARY_SORT, sort.name).apply()
    }

    fun playerSaveFolder(playerId: String): String? =
        prefs.getString(saveFolderKey(playerId), null)?.takeIf { it.isNotBlank() }

    fun setPlayerSaveFolder(playerId: String, uri: String) {
        prefs.edit().putString(saveFolderKey(playerId), uri).apply()
    }

    /** A tree grant can be shared by a library source, several players, RetroArch's cores, and Eden's folder. */
    fun folderTreeInUse(uri: String): Boolean = uri in folderTrees() ||
        prefs.all.any { (key, value) ->
            (key.startsWith(PLAYER_SAVE_FOLDER_PREFIX) || key.startsWith(CORES_FOLDER_PREFIX) || key.startsWith(EDEN_FOLDER_PREFIX)) &&
                value == uri
        }

    override fun mark(key: String): String? = prefs.getString(saveMarkKey(key), null)

    /** Written before the game starts, so it is committed rather than applied. */
    override fun setMark(key: String, hash: String) {
        prefs.edit().putString(saveMarkKey(key), hash).commit()
    }

    override fun clearMark(key: String) {
        prefs.edit().remove(saveMarkKey(key)).commit()
    }

    /** Launched games whose saves have not been read back yet, one per game. Survives the process. */
    fun pendingSaveReturns(): List<String> =
        prefs.all.filterKeys { it.startsWith(PENDING_SAVE_RETURN_PREFIX) }.values.filterIsInstance<String>()

    fun setPendingSaveReturn(gameKey: String, value: String?) {
        val key = PENDING_SAVE_RETURN_PREFIX + gameKey
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.commit()
    }

    /**
     * A key for the last intent started for [packageName], for a player that
     * cannot switch games in place. Kept across restarts of Foldcade, since the
     * player can outlive it.
     */
    fun lastPlayerStart(packageName: String): String? = prefs.getString(LAST_START_PREFIX + packageName, null)

    fun setLastPlayerStart(packageName: String, key: String) {
        prefs.edit().putString(LAST_START_PREFIX + packageName, key).commit()
    }

    /**
     * Players Foldcade started a game in that may still hold it. Kept across
     * restarts of Foldcade, since the player can outlive it, so the next start
     * there replaces that game instead of opening a second copy of the player.
     */
    fun playersHoldingGames(): Set<String> = stringSet(KEY_PLAYERS_HOLDING_GAMES)

    fun setPlayersHoldingGames(packages: Set<String>) {
        if (packages == playersHoldingGames()) return
        prefs.edit().putStringSet(KEY_PLAYERS_HOLDING_GAMES, packages).commit()
    }

    /**
     * True while a save was written for players keyed [saveFolderKey] and none
     * of them has started since. A player still holding that game must start fresh.
     */
    fun saveAwaitsLoad(saveFolderKey: String): Boolean = prefs.getBoolean(SAVE_AWAITS_LOAD_PREFIX + saveFolderKey, false)

    fun setSaveAwaitsLoad(saveFolderKey: String, waiting: Boolean) {
        prefs.edit().apply {
            if (waiting) putBoolean(SAVE_AWAITS_LOAD_PREFIX + saveFolderKey, true) else remove(SAVE_AWAITS_LOAD_PREFIX + saveFolderKey)
        }.commit()
    }

    /** The tree the user chose so Foldcade can list [packageName]'s cores. */
    fun retroArchCoresFolder(packageName: String): String? = prefs.getString(CORES_FOLDER_PREFIX + packageName, null)

    fun setRetroArchCoresFolder(packageName: String, uri: String) {
        prefs.edit().putString(CORES_FOLDER_PREFIX + packageName, uri).apply()
    }

    /** True once Foldcade asked to see RetroArch's cores at a launch. Settings still offers it. */
    fun retroArchCoresOffered(): Boolean = prefs.getBoolean(KEY_RETROARCH_CORES_OFFERED, false)

    fun setRetroArchCoresOffered() {
        prefs.edit().putBoolean(KEY_RETROARCH_CORES_OFFERED, true).apply()
    }

    /** The tree the user chose so Foldcade can add [packageName]'s keys and firmware. */
    fun edenFolder(packageName: String): String? = prefs.getString(EDEN_FOLDER_PREFIX + packageName, null)

    fun setEdenFolder(packageName: String, uri: String) {
        prefs.edit().putString(EDEN_FOLDER_PREFIX + packageName, uri).apply()
    }

    /** True once Foldcade asked to see Eden's folder at a launch. Settings still offers it. */
    fun edenFolderOffered(): Boolean = prefs.getBoolean(KEY_EDEN_FOLDER_OFFERED, false)

    fun setEdenFolderOffered() {
        prefs.edit().putBoolean(KEY_EDEN_FOLDER_OFFERED, true).apply()
    }

    /**
     * Eden has not started since Foldcade added files to [EdenRestartPending.packageName].
     * Written with [apply] from the install worker as each file lands.
     */
    fun edenRestartPending(): EdenRestartPending? {
        val packageName = prefs.getString(KEY_EDEN_RESTART_PACKAGE, null)?.takeIf { it.isNotEmpty() } ?: return null
        return EdenRestartPending(packageName, prefs.getInt(KEY_EDEN_RESTART_BOOT, -1))
    }

    fun setEdenRestartPending(packageName: String, bootCount: Int) {
        if (packageName.isEmpty()) return
        prefs.edit()
            .putString(KEY_EDEN_RESTART_PACKAGE, packageName)
            .putInt(KEY_EDEN_RESTART_BOOT, bootCount)
            .apply()
    }

    fun clearEdenRestartPending() {
        prefs.edit().remove(KEY_EDEN_RESTART_PACKAGE).remove(KEY_EDEN_RESTART_BOOT).apply()
    }

    /**
     * Firmware zips Eden refused, keyed by server, firmware id, and md5 or size.
     * At most 20. Adding one past that drops the oldest.
     */
    fun refusedEdenFirmware(): Set<String> = refusedEdenFirmwareRows().map { it.second }.toSet()

    fun rememberRefusedEdenFirmware(key: String) {
        if (key.isEmpty()) return
        val existing = refusedEdenFirmwareRows()
        if (existing.any { it.second == key }) return
        val order = (existing.maxOfOrNull { it.first } ?: 0L) + 1L
        val kept = (existing + (order to key)).takeLast(REFUSED_EDEN_FIRMWARE_CAP)
        // The order sits in front of the key so a full set can drop the oldest. Committed so a launch that dies still skips this zip.
        val stored = kept.map { (index, value) -> "$index\t$value" }.toSet()
        prefs.edit().putStringSet(KEY_REFUSED_EDEN_FIRMWARE, stored).commit()
    }

    private fun refusedEdenFirmwareRows(): List<Pair<Long, String>> =
        stringSet(KEY_REFUSED_EDEN_FIRMWARE).mapNotNull { entry ->
            val index = entry.substringBefore('\t').toLongOrNull() ?: return@mapNotNull null
            val key = entry.substringAfter('\t')
            if (key.isEmpty()) null else index to key
        }.sortedBy { it.first }

    /**
     * The apps a release with Android shelves hid there, once: the shelves' saved state is
     * forgotten as it is read. Installed apps are filed on Home now, which hides them itself.
     */
    fun takeShelfHiddenApps(): Set<String> {
        val hidden = stringSet(KEY_SHELF_HIDDEN)
        val keys = listOf(KEY_SHELF_GAMES, KEY_SHELF_APPS, KEY_SHELF_HIDDEN, KEY_SHELF_FAVORITES, KEY_SHELF_SHOWN)
        if (keys.any(prefs::contains)) prefs.edit().apply { keys.forEach(::remove) }.apply()
        return hidden
    }

    fun homeBoardRaw(): String? = prefs.getString(KEY_HOME_BOARD, null)?.takeIf { it.isNotBlank() }

    /**
     * Keeps the saved board, exactly as a release before collections wrote it, the first time
     * this release loads it. It is written once and never replaced, so a later release could
     * put the old Home back. Nothing reads it yet.
     */
    fun keepHomeBoardBeforeCollections() {
        val raw = homeBoardRaw() ?: return
        if (!savedBeforeCollections(raw) || prefs.contains(KEY_HOME_BOARD_BEFORE_COLLECTIONS)) return
        prefs.edit().putString(KEY_HOME_BOARD_BEFORE_COLLECTIONS, raw).apply()
    }

    /** The board [keepHomeBoardBeforeCollections] kept, or null when there was none to keep. */
    fun homeBoardBeforeCollections(): String? = prefs.getString(KEY_HOME_BOARD_BEFORE_COLLECTIONS, null)

    fun saveHomeBoard(raw: String) {
        // Focus, open panels, and unfinished edits can publish the same saved layout.
        if (raw == homeBoardRaw()) return
        prefs.edit().putString(KEY_HOME_BOARD, raw).apply()
    }

    /** Each copy's id to the tile it folds into, so a tile keeps its id while a copy is away. */
    fun sameGames(): Map<String, String> = decodeSameGames(prefs.getString(KEY_SAME_GAMES, null))

    fun saveSameGames(aliases: Map<String, String>) {
        // Written before the board that uses it. A board saved without it heals at the next merge.
        prefs.edit().putString(KEY_SAME_GAMES, encodeSameGames(aliases)).commit()
    }

    private fun stringSet(key: String): Set<String> = prefs.getStringSet(key, emptySet()).orEmpty().toSet()

    fun setMusic(enabled: Boolean, volume: Float, trackId: String) {
        val id = trackId.trim().ifEmpty { HomeMusicSetting.DEFAULT_TRACK_ID }
        prefs.edit()
            .putBoolean(KEY_MUSIC_ENABLED, enabled)
            .putFloat(KEY_MUSIC_VOLUME, safeMusicVolume(volume))
            .putString(KEY_MUSIC_TRACK, id)
            .apply()
    }

    fun iconTheme(): String? = prefs.getString("icon_theme_id", null)

    fun setIconTheme(id: String?) { prefs.edit().putString("icon_theme_id", id).apply() }

    fun backgroundMotionPinned(): Boolean = prefs.contains(KEY_BACKGROUND)

    fun backgroundMotion(): BackgroundMotion =
        BackgroundMotion.fromWire(prefs.getString(KEY_BACKGROUND, null)) ?: BackgroundMotion.Off

    fun setBackgroundMotion(motion: BackgroundMotion) {
        prefs.edit().putString(KEY_BACKGROUND, motion.wire).apply()
    }

    fun motionSpeed(): MotionSpeed =
        MotionSpeed.fromWire(prefs.getString(KEY_MOTION_SPEED, null)) ?: MotionSpeed.Slow

    fun setMotionSpeed(speed: MotionSpeed) {
        prefs.edit().putString(KEY_MOTION_SPEED, speed.wire).apply()
    }

    fun artworkEnabled(): Boolean = prefs.getBoolean(KEY_ARTWORK, true)

    /** Restore stable pack IDs, with the old display-name preferences as a migration fallback. */
    fun themeIndex(names: List<String>, legacyNames: List<String> = names): Int {
        val saved = prefs.getString(KEY_THEME, null)
        return names.indexOf(saved).takeIf { it >= 0 }
            ?: legacyNames.indexOf(saved).takeIf { it >= 0 }
            ?: fallbackThemeIndex(names)
    }

    fun setTheme(name: String) {
        prefs.edit().putString(KEY_THEME, name).apply()
    }

    fun setArtworkEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ARTWORK, enabled).apply()
    }

    /**
     * The Moonlight games Foldcade last read from Android. They fill the shelf at
     * start, so Moonlight tiles keep their names before Android answers again.
     */
    fun moonlightPins(): List<MoonlightApp> {
        val raw = prefs.getString(KEY_MOONLIGHT_PINS, null)
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(PIN_RECORD).mapNotNull { record ->
            val bits = record.split(PIN_FIELD)
            if (bits.size != 3) null else moonlightApp(hostUuid = bits[0], appId = bits[1], label = bits[2])
        }
    }

    fun setMoonlightPins(apps: List<MoonlightApp>) {
        val raw = apps.joinToString(PIN_RECORD) { app ->
            listOf(app.hostUuid, app.appId, app.label).joinToString(PIN_FIELD) { field ->
                field.replace(PIN_FIELD, " ").replace(PIN_RECORD, " ")
            }
        }
        prefs.edit().putString(KEY_MOONLIGHT_PINS, raw).apply()
    }

    /** Moonlight's computer names by host UUID, as its shortcuts last gave them. */
    fun moonlightHostNames(): Map<String, String> {
        val raw = prefs.getString(KEY_MOONLIGHT_HOSTS, null)
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(PIN_RECORD).mapNotNull { record ->
            val bits = record.split(PIN_FIELD)
            if (bits.size != 2 || bits[0].isBlank() || bits[1].isBlank()) null else bits[0] to bits[1]
        }.toMap()
    }

    fun setMoonlightHostNames(names: Map<String, String>) {
        val raw = names.entries.joinToString(PIN_RECORD) { (uuid, name) ->
            listOf(uuid, name).joinToString(PIN_FIELD) { it.replace(PIN_FIELD, " ").replace(PIN_RECORD, " ") }
        }
        prefs.edit().putString(KEY_MOONLIGHT_HOSTS, raw).apply()
    }

    /**
     * The computers Foldcade paired with, and the fingerprint of the key it paired
     * as. Android moves these settings to a new device but never the key, so a
     * list made with another key is dropped as it is read.
     */
    internal fun pairedStreamHosts(identity: String?): List<PairedStreamHost> {
        val raw = prefs.getString(KEY_PAIRED_HOSTS, null)
        if (raw.isNullOrBlank()) return emptyList()
        val records = raw.split(PIN_RECORD)
        if (identity == null || records.firstOrNull() != identity) return emptyList()
        return records.drop(1).mapNotNull { record ->
            val bits = record.split(PIN_FIELD)
            if (bits.size != 4) return@mapNotNull null
            val der = try {
                java.util.Base64.getDecoder().decode(bits[3])
            } catch (_: IllegalArgumentException) {
                return@mapNotNull null
            }
            val certificate = app.foldcade.gamestream.certificate(der) ?: return@mapNotNull null
            val port = bits[2].toIntOrNull()?.takeIf { it in 1..65535 } ?: return@mapNotNull null
            PairedStreamHost(uuid = bits[0], name = bits[1], httpsPort = port, certificate = certificate)
        }
    }

    internal fun setPairedStreamHosts(identity: String, hosts: List<PairedStreamHost>) {
        val raw = (listOf(identity) + hosts.map { host ->
            listOf(host.uuid, host.name, host.httpsPort.toString(), java.util.Base64.getEncoder().encodeToString(host.certificate.encoded))
                .joinToString(PIN_FIELD) { it.replace(PIN_FIELD, " ").replace(PIN_RECORD, " ") }
        }).joinToString(PIN_RECORD)
        prefs.edit().putString(KEY_PAIRED_HOSTS, raw).apply()
    }

    /** The apps each paired computer last listed. They fill the shelf at start, before it answers again. */
    fun listedStreamApps(): List<MoonlightApp> {
        val raw = prefs.getString(KEY_LISTED_APPS, null)
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(PIN_RECORD).mapNotNull { record ->
            val bits = record.split(PIN_FIELD)
            if (bits.size != 3) null else moonlightApp(hostUuid = bits[0], appId = bits[1], label = bits[2])
        }
    }

    fun setListedStreamApps(apps: List<MoonlightApp>) {
        val raw = apps.joinToString(PIN_RECORD) { app ->
            listOf(app.hostUuid, app.appId, app.label).joinToString(PIN_FIELD) { it.replace(PIN_FIELD, " ").replace(PIN_RECORD, " ") }
        }
        prefs.edit().putString(KEY_LISTED_APPS, raw).apply()
    }

    /**
     * This device's GameStream client id: 16 hex digits, made once. Hosts see it
     * when Foldcade asks them about themselves. It is not Moonlight's.
     */
    fun gameStreamClientId(): String {
        prefs.getString(KEY_GAMESTREAM_CLIENT, null)?.takeIf { GAMESTREAM_CLIENT.matches(it) }?.let { return it }
        val random = java.security.SecureRandom()
        val made = buildString { repeat(16) { append("0123456789abcdef"[random.nextInt(16)]) } }
        prefs.edit().putString(KEY_GAMESTREAM_CLIENT, made).apply()
        return made
    }

    /**
     * The DroidDeck games Foldcade last read from Android's pinned shortcuts.
     * They fill the shelf at start, so DroidDeck tiles keep their names before Android answers again.
     */
    fun droidDeckPins(): List<DroidDeckGame> {
        val raw = prefs.getString(KEY_DROIDDECK_PINS, null)
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(PIN_RECORD).mapNotNull { record ->
            val bits = record.split(PIN_FIELD)
            if (bits.size != 2) null else droidDeckGame(gameId = bits[0], title = bits[1])
        }
    }

    fun setDroidDeckPins(games: List<DroidDeckGame>) {
        val raw = games.joinToString(PIN_RECORD) { game ->
            listOf(game.gameId, game.title).joinToString(PIN_FIELD) { field ->
                field.replace(PIN_FIELD, " ").replace(PIN_RECORD, " ")
            }
        }
        prefs.edit().putString(KEY_DROIDDECK_PINS, raw).apply()
    }

    fun buttonPromptSeenLaunch(): Boolean = prefs.getBoolean(KEY_BUTTON_PROMPT_SEEN, false)

    fun setButtonPromptSeenLaunch() {
        prefs.edit().putBoolean(KEY_BUTTON_PROMPT_SEEN, true).apply()
    }

    fun buttonPromptConfirm(deviceKey: String): PromptKey? {
        val name = prefs.getString(buttonPromptConfirmKey(deviceKey), null) ?: return null
        return runCatching { PromptKey.valueOf(name) }.getOrNull()
    }

    fun buttonPromptDismissed(deviceKey: String): Boolean =
        prefs.getBoolean(buttonPromptDismissedKey(deviceKey), false)

    fun saveButtonPrompt(deviceKey: String, confirm: PromptKey?, dismissed: Boolean) {
        val editor = prefs.edit().putBoolean(buttonPromptDismissedKey(deviceKey), dismissed)
        if (confirm == null) editor.remove(buttonPromptConfirmKey(deviceKey))
        else editor.putString(buttonPromptConfirmKey(deviceKey), confirm.name)
        editor.apply()
    }

    fun place(panel: Panel, app: ExternalApp) = update { it.place(panel, app) }

    companion object {
        private fun safeMusicVolume(volume: Float): Float =
            if (volume.isFinite()) volume.coerceIn(0f, 1f) else HomeMusicSetting.DEFAULT_VOLUME

        private const val KEY_PRIMARY_TOP = "primary_top"
        private const val KEY_GAME_SCREENS = "game_screens"
        private const val KEY_PLATFORM_SCREENS = "platform_screens"
        private const val KEY_HOME_PROMPTED = "home_prompted"
        private const val KEY_FOLDER = "folder_tree"
        private const val KEY_FOLDER_EXPLAINED = "folder_explained"
        private const val KEY_ROMM_ORIGIN = "romm_origin"
        private const val KEY_ROMM_INSTANCES = "romm_instances"
        private const val KEY_MUSIC_ENABLED = "music_enabled"
        private const val KEY_MUSIC_VOLUME = "music_volume"
        private const val KEY_MUSIC_TRACK = "music_track"
        private const val KEY_BACKGROUND = "background_motion"
        private const val KEY_MOTION_SPEED = "motion_speed"
        private const val KEY_ARTWORK = "artwork"
        private const val KEY_THEME = "theme"
        private const val KEY_LIBRARY_SORT = "library_sort"
        private const val KEY_HOME_BOARD = "home_board"
        private const val KEY_SAME_GAMES = "same_games"
        private const val KEY_HOME_BOARD_BEFORE_COLLECTIONS = "home_board_before_collections"
        private const val KEY_SHELF_GAMES = "android_shelf_games"
        private const val KEY_SHELF_APPS = "android_shelf_apps"
        private const val KEY_SHELF_HIDDEN = "android_shelf_hidden"
        private const val KEY_SHELF_FAVORITES = "android_shelf_favorites"
        private const val KEY_SHELF_SHOWN = "android_shelf_shown"
        private const val KEY_BUTTON_PROMPT_SEEN = "button_prompt_seen"
        private const val KEY_USAGE_PROMPTED = "usage_prompted"
        private const val KEY_RETROARCH_CORES_OFFERED = "retroarch_cores_offered"
        private const val KEY_EDEN_FOLDER_OFFERED = "eden_folder_offered"
        private const val KEY_EDEN_RESTART_PACKAGE = "eden_restart_package"
        private const val KEY_EDEN_RESTART_BOOT = "eden_restart_boot"
        private const val KEY_REFUSED_EDEN_FIRMWARE = "refused_eden_firmware"
        private const val REFUSED_EDEN_FIRMWARE_CAP = 20
        private const val LAST_START_PREFIX = "last_player_start:"
        private const val KEY_PLAYERS_HOLDING_GAMES = "players_holding_games"
        private const val SAVE_AWAITS_LOAD_PREFIX = "save_awaits_load:"
        private const val CORES_FOLDER_PREFIX = "retroarch_cores_folder:"
        private const val EDEN_FOLDER_PREFIX = "eden_folder:"
        private const val KEY_MOONLIGHT_PINS = "moonlight_pins"
        private const val KEY_MOONLIGHT_HOSTS = "moonlight_hosts"
        private const val KEY_GAMESTREAM_CLIENT = "gamestream_client_id"
        private const val KEY_PAIRED_HOSTS = "moonlight_paired_hosts"
        private const val KEY_LISTED_APPS = "moonlight_listed_apps"
        private val GAMESTREAM_CLIENT = Regex("[0-9a-f]{16}")
        private const val KEY_DROIDDECK_PINS = "droiddeck_pins"
        private const val PIN_FIELD = "\u001f"
        private const val PIN_RECORD = "\u001e"

        /** The import sheet's list, answer, and placements, and the source switch. */
        internal val RETIRED_MOONLIGHT_KEYS: List<String> = listOf(
            "moonlight_source",
            "moonlight_import",
            "moonlight_import_confirmed",
            "moonlight_import_settled",
            "moonlight_placements",
        )

        private fun buttonPromptConfirmKey(deviceKey: String) = "button_prompt_confirm:$deviceKey"

        private fun buttonPromptDismissedKey(deviceKey: String) = "button_prompt_dismissed:$deviceKey"

        private const val PLAYER_SAVE_FOLDER_PREFIX = "player_save_folder:"

        private fun saveFolderKey(playerId: String) = PLAYER_SAVE_FOLDER_PREFIX + playerId


        private fun saveMarkKey(key: String) = "player_save_mark:$key"

        private const val PENDING_SAVE_RETURN_PREFIX = "pending_save_return:"
    }
}

/**
 * Where a missing saved theme lands. Afterglow's slot when that pack loaded,
 * otherwise slot 0. Slot 0 is the last resort when Afterglow itself failed to load.
 */
internal fun fallbackThemeIndex(ids: List<String>): Int {
    val afterglow = ids.indexOfFirst {
        it.equals(FALLBACK_THEME_ID, ignoreCase = true) || it.equals("Afterglow", ignoreCase = true)
    }
    return if (afterglow >= 0) afterglow else 0
}
