package app.foldcade

import android.content.SharedPreferences
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.MotionSpeed
import app.foldcade.language.AndroidShelf
import app.foldcade.language.AppShelfRecord
import app.foldcade.language.AppShelfState
import app.foldcade.language.HomeMusicSetting
import app.foldcade.language.LibrarySort
import app.foldcade.language.PromptKey
import app.foldcade.plugins.moonlight.MoonlightApp
import app.foldcade.plugins.moonlight.moonlightApp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SessionStore(private val prefs: SharedPreferences) : SaveMarks {
    var session by mutableStateOf(load())
        private set

    init {
        // The Moonlight import sheet and its source switch are gone. Every pin is on Home.
        if (RETIRED_MOONLIGHT_KEYS.any(prefs::contains)) {
            prefs.edit().apply { RETIRED_MOONLIGHT_KEYS.forEach { remove(it) } }.apply()
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

    /** A tree grant can be shared by a library source and several players. */
    fun folderTreeInUse(uri: String): Boolean = uri in folderTrees() ||
        prefs.all.any { (key, value) -> key.startsWith(PLAYER_SAVE_FOLDER_PREFIX) && value == uri }

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

    /** True after Not now. The save-folder offer stays quiet for this player. */
    fun saveFolderPromptSkipped(playerId: String): Boolean =
        prefs.getBoolean(saveFolderSkipKey(playerId), false)

    fun setSaveFolderPromptSkipped(playerId: String) {
        prefs.edit().putBoolean(saveFolderSkipKey(playerId), true).apply()
    }

    fun appShelfState(): AppShelfState {
        val games = stringSet(KEY_SHELF_GAMES)
        val apps = stringSet(KEY_SHELF_APPS)
        val hidden = stringSet(KEY_SHELF_HIDDEN)
        val favorites = stringSet(KEY_SHELF_FAVORITES)
        val shown = stringSet(KEY_SHELF_SHOWN)
        val names = games + apps + hidden + favorites + shown
        return AppShelfState(
            names.associateWith { name ->
                AppShelfRecord(
                    shelf = when {
                        name in games -> AndroidShelf.Games
                        name in apps -> AndroidShelf.Apps
                        else -> null
                    },
                    hidden = name in hidden,
                    favorite = name in favorites,
                    showDespitePlayer = name in shown,
                )
            },
        )
    }

    fun homeBoardRaw(): String? = prefs.getString(KEY_HOME_BOARD, null)?.takeIf { it.isNotBlank() }

    fun saveHomeBoard(raw: String) {
        // Focus, open panels, and unfinished edits can publish the same saved layout.
        if (raw == homeBoardRaw()) return
        prefs.edit().putString(KEY_HOME_BOARD, raw).apply()
    }

    fun saveAppShelfState(state: AppShelfState) {
        val games = mutableSetOf<String>()
        val apps = mutableSetOf<String>()
        val hidden = mutableSetOf<String>()
        val favorites = mutableSetOf<String>()
        val shown = mutableSetOf<String>()
        state.records.forEach { (name, record) ->
            when (record.shelf) {
                AndroidShelf.Games -> games += name
                AndroidShelf.Apps -> apps += name
                null -> Unit
            }
            if (record.hidden) hidden += name
            if (record.favorite) favorites += name
            if (record.showDespitePlayer) shown += name
        }
        prefs.edit()
            .putStringSet(KEY_SHELF_GAMES, games)
            .putStringSet(KEY_SHELF_APPS, apps)
            .putStringSet(KEY_SHELF_HIDDEN, hidden)
            .putStringSet(KEY_SHELF_FAVORITES, favorites)
            .putStringSet(KEY_SHELF_SHOWN, shown)
            .apply()
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

    /** Store the theme's name so a changed list order cannot select another theme. */
    fun themeIndex(names: List<String>): Int {
        val saved = prefs.getString(KEY_THEME, null)
        return names.indexOf(saved).takeIf { it >= 0 } ?: if (names.size > 1) 1 else 0
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
        private const val KEY_SHELF_GAMES = "android_shelf_games"
        private const val KEY_SHELF_APPS = "android_shelf_apps"
        private const val KEY_SHELF_HIDDEN = "android_shelf_hidden"
        private const val KEY_SHELF_FAVORITES = "android_shelf_favorites"
        private const val KEY_SHELF_SHOWN = "android_shelf_shown"
        private const val KEY_BUTTON_PROMPT_SEEN = "button_prompt_seen"
        private const val KEY_USAGE_PROMPTED = "usage_prompted"
        private const val KEY_MOONLIGHT_PINS = "moonlight_pins"
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

        private fun saveFolderSkipKey(playerId: String) = "player_save_folder_skipped:$playerId"

        private fun saveMarkKey(key: String) = "player_save_mark:$key"

        private const val PENDING_SAVE_RETURN_PREFIX = "pending_save_return:"
    }
}
