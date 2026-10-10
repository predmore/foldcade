package app.foldcade

import android.content.SharedPreferences
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.MotionSpeed
import app.foldcade.language.AndroidShelf
import app.foldcade.language.AppShelfRecord
import app.foldcade.language.AppShelfState
import app.foldcade.language.HomeMusicSetting
import app.foldcade.language.LibrarySort
import app.foldcade.language.MoonlightPlacement
import app.foldcade.language.MoonlightSource
import app.foldcade.language.MoonlightStoredApp
import app.foldcade.language.PromptKey
import app.foldcade.language.decodeMoonlightApps
import app.foldcade.language.encodeMoonlightApps
import app.foldcade.language.moonlightPlacementKeys
import app.foldcade.language.moonlightPlacementsFromKeys
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SessionStore(private val prefs: SharedPreferences) {
    var session by mutableStateOf(load())
        private set

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
        gameScreens = decode(prefs.getString(KEY_GAME_SCREENS, null)),
        platformScreens = decode(prefs.getString(KEY_PLATFORM_SCREENS, null)),
    )

    private fun save(session: Session) {
        prefs.edit()
            .putBoolean(KEY_PRIMARY_TOP, session.defaultDisplayIsTop)
            .putString(KEY_GAME_SCREENS, encode(session.gameScreens))
            .putString(KEY_PLATFORM_SCREENS, encode(session.platformScreens))
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

    fun musicEnabled(): Boolean = prefs.getBoolean(KEY_MUSIC_ENABLED, true)

    fun musicVolume(): Float =
        prefs.getFloat(KEY_MUSIC_VOLUME, HomeMusicSetting.DEFAULT_VOLUME).coerceIn(0f, 1f)

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
            .putFloat(KEY_MUSIC_VOLUME, volume.coerceIn(0f, 1f))
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

    fun setArtworkEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ARTWORK, enabled).apply()
    }

    fun moonlightSource(): MoonlightSource =
        if (prefs.getString(KEY_MOONLIGHT_SOURCE, null) == MoonlightSource.ImportedList.name) {
            MoonlightSource.ImportedList
        } else {
            MoonlightSource.PinnedShortcuts
        }

    fun setMoonlightSource(source: MoonlightSource) {
        prefs.edit().putString(KEY_MOONLIGHT_SOURCE, source.name).apply()
    }

    fun moonlightImportConfirmed(): Boolean = prefs.getBoolean(KEY_MOONLIGHT_IMPORT_CONFIRMED, false)

    fun moonlightImportSettled(): Boolean = prefs.getBoolean(KEY_MOONLIGHT_IMPORT_SETTLED, false)

    fun setMoonlightImportSettled() {
        prefs.edit().putBoolean(KEY_MOONLIGHT_IMPORT_SETTLED, true).apply()
    }

    fun moonlightImportEncoded(): String? = prefs.getString(KEY_MOONLIGHT_IMPORT, null)

    fun moonlightImportedApps(): List<MoonlightStoredApp> = decodeMoonlightApps(moonlightImportEncoded())

    fun setMoonlightImport(apps: List<MoonlightStoredApp>) {
        prefs.edit()
            .putString(KEY_MOONLIGHT_IMPORT, encodeMoonlightApps(apps))
            .putBoolean(KEY_MOONLIGHT_IMPORT_CONFIRMED, true)
            .apply()
    }

    fun moonlightPlacements(): List<MoonlightPlacement> =
        moonlightPlacementsFromKeys(stringSet(KEY_MOONLIGHT_PLACEMENTS))

    fun setMoonlightPlacements(placements: List<MoonlightPlacement>) {
        prefs.edit().putStringSet(KEY_MOONLIGHT_PLACEMENTS, moonlightPlacementKeys(placements).toMutableSet()).apply()
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

    private fun encode(screens: Map<String, Panel>): String =
        screens.entries.joinToString(",") { "${it.key}=${it.value.name}" }

    private fun decode(raw: String?): Map<String, Panel> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(",").mapNotNull { part ->
            val bits = part.split("=", limit = 2)
            if (bits.size != 2) return@mapNotNull null
            val panel = runCatching { Panel.valueOf(bits[1]) }.getOrNull() ?: return@mapNotNull null
            bits[0] to panel
        }.toMap()
    }

    fun place(panel: Panel, app: ExternalApp) = update { it.place(panel, app) }

    companion object {
        private const val KEY_PRIMARY_TOP = "primary_top"
        private const val KEY_GAME_SCREENS = "game_screens"
        private const val KEY_PLATFORM_SCREENS = "platform_screens"
        private const val KEY_HOME_PROMPTED = "home_prompted"
        private const val KEY_FOLDER = "folder_tree"
        private const val KEY_FOLDER_EXPLAINED = "folder_explained"
        private const val KEY_ROMM_ORIGIN = "romm_origin"
        private const val KEY_MUSIC_ENABLED = "music_enabled"
        private const val KEY_MUSIC_VOLUME = "music_volume"
        private const val KEY_MUSIC_TRACK = "music_track"
        private const val KEY_BACKGROUND = "background_motion"
        private const val KEY_MOTION_SPEED = "motion_speed"
        private const val KEY_ARTWORK = "artwork"
        private const val KEY_LIBRARY_SORT = "library_sort"
        private const val KEY_HOME_BOARD = "home_board"
        private const val KEY_SHELF_GAMES = "android_shelf_games"
        private const val KEY_SHELF_APPS = "android_shelf_apps"
        private const val KEY_SHELF_HIDDEN = "android_shelf_hidden"
        private const val KEY_SHELF_FAVORITES = "android_shelf_favorites"
        private const val KEY_SHELF_SHOWN = "android_shelf_shown"
        private const val KEY_BUTTON_PROMPT_SEEN = "button_prompt_seen"
        private const val KEY_USAGE_PROMPTED = "usage_prompted"
        private const val KEY_MOONLIGHT_SOURCE = "moonlight_source"
        private const val KEY_MOONLIGHT_IMPORT = "moonlight_import"
        private const val KEY_MOONLIGHT_IMPORT_CONFIRMED = "moonlight_import_confirmed"
        private const val KEY_MOONLIGHT_IMPORT_SETTLED = "moonlight_import_settled"
        private const val KEY_MOONLIGHT_PLACEMENTS = "moonlight_placements"

        private fun buttonPromptConfirmKey(deviceKey: String) = "button_prompt_confirm:$deviceKey"

        private fun buttonPromptDismissedKey(deviceKey: String) = "button_prompt_dismissed:$deviceKey"

        private fun saveFolderKey(playerId: String) = "player_save_folder:$playerId"

        private fun saveFolderSkipKey(playerId: String) = "player_save_folder_skipped:$playerId"
    }
}
