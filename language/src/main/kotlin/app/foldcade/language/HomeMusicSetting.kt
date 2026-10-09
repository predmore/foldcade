package app.foldcade.language

import kotlin.math.roundToInt

/**
 * Home-loop preference. The left panel edits this. Playback lives in the app.
 * Default level is a quarter of the media volume. The device mute is not a setting.
 */
data class HomeMusicSetting(
    val enabled: Boolean = true,
    val volume: Float = DEFAULT_VOLUME,
) {
    fun toggled(): HomeMusicSetting = copy(enabled = !enabled)

    /** Activate steps by 5 points and wraps from full to silent. */
    fun stepped(): HomeMusicSetting {
        val percent = (volume.coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100)
        val next = if (percent >= 100) 0 else (percent + STEP_PERCENT).coerceAtMost(100)
        return copy(volume = next / 100f)
    }

    fun withVolume(value: Float): HomeMusicSetting = copy(volume = value.coerceIn(0f, 1f))

    companion object {
        const val DEFAULT_VOLUME = 0.25f
        const val STEP_PERCENT = 5
        const val UI_SOUND_DUCK = 0.4f
    }
}

object MusicCopy {
    const val row = "Music"
    const val on = "On"
    const val off = "Off"
    const val volume = "Volume"

    fun musicLabel(enabled: Boolean): String = "$row  ${if (enabled) on else off}"

    fun volumeLabel(level: Float): String {
        val percent = (level.coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100)
        return "$volume  $percent%"
    }
}

/**
 * Player level before the entry fade. Zero when music is off, the slider is at
 * zero, or the media stream is muted. UI sounds duck the loop. They do not replace it.
 */
fun playbackLevel(
    setting: HomeMusicSetting,
    uiSoundActive: Boolean,
    mediaMuted: Boolean,
): Float {
    if (!setting.enabled || mediaMuted) return 0f
    val level = setting.volume.coerceIn(0f, 1f)
    return if (uiSoundActive) level * HomeMusicSetting.UI_SOUND_DUCK else level
}

/** Theme JSON key. Absent means the packaged default loop. */
const val THEME_BACKGROUND_MUSIC = "backgroundMusic"

/** Asset name packaged for the default theme. Stable across renders. */
const val DEFAULT_BACKGROUND_MUSIC = "foldcade_home_loop.ogg"

/**
 * Optional `backgroundMusic` string in theme.json. A community theme names a
 * track inside the zip. A missing or blank field keeps [DEFAULT_BACKGROUND_MUSIC].
 */
fun backgroundMusicFromThemeJson(json: String?): String {
    if (json.isNullOrBlank()) return DEFAULT_BACKGROUND_MUSIC
    val match = Regex(""""backgroundMusic"\s*:\s*"([^"\\]*)"""").find(json) ?: return DEFAULT_BACKGROUND_MUSIC
    val value = match.groupValues[1].trim()
    if (value.isEmpty() || value.startsWith("/") || value.contains('\\') || value.contains("..")) {
        return DEFAULT_BACKGROUND_MUSIC
    }
    return value
}
