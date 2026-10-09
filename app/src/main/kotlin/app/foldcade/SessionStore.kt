package app.foldcade

import android.content.SharedPreferences
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.MotionSpeed
import app.foldcade.api.ExternalApp
import app.foldcade.api.Panel
import app.foldcade.api.Session
import app.foldcade.language.HomeMusicSetting
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

    fun setFolderTree(uri: String) {
        prefs.edit().putString(KEY_FOLDER, uri).apply()
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
    }
}
