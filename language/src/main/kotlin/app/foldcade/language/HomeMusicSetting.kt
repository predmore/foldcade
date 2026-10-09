package app.foldcade.language

import kotlin.math.roundToInt

/**
 * Home-loop preference. The left panel edits this. Playback lives in the app.
 * Default level is a quarter of the media volume. The device mute is not a setting.
 */
data class HomeMusicSetting(
    val enabled: Boolean = true,
    val volume: Float = DEFAULT_VOLUME,
    val trackId: String = DEFAULT_TRACK_ID,
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
        const val DEFAULT_TRACK_ID = "lanternlight"
    }
}

/** One row of music/tracks/manifest.json. The setting stores [id], not the file name. */
data class MusicTrack(
    val id: String,
    val title: String,
    val composer: String,
    val license: String,
    val file: String,
)

object MusicCopy {
    const val row = "Music"
    const val on = "On"
    const val off = "Off"
    const val volume = "Volume"
    const val track = "Track"

    fun musicLabel(enabled: Boolean): String = "$row  ${if (enabled) on else off}"

    fun trackLabel(name: String): String = "$track  $name"

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

/** Theme JSON key. Absent means the selected track from the manifest. */
const val THEME_BACKGROUND_MUSIC = "backgroundMusic"

/** Packaged file for Lanternlight. The manifest `file` field is this path. */
const val DEFAULT_BACKGROUND_MUSIC = "music/lanternlight.ogg"

const val DEFAULT_TRACK_TITLE = "Lanternlight"

/**
 * Tracks listed in music/tracks/manifest.json. A blank or broken manifest is empty.
 * The caller falls back to [DEFAULT_TRACK_TITLE] and [DEFAULT_BACKGROUND_MUSIC].
 */
fun musicTracksFromManifest(json: String?): List<MusicTrack> {
    if (json.isNullOrBlank()) return emptyList()
    val field = Regex(""""(id|title|composer|license|file)"\s*:\s*"([^"\\]*)"""")
    return Regex("""\{[^{}]*\}""").findAll(json).mapNotNull { obj ->
        val fields = field.findAll(obj.value).associate { it.groupValues[1] to it.groupValues[2] }
        val id = fields["id"]?.trim().orEmpty()
        val title = fields["title"]?.trim().orEmpty()
        val composer = fields["composer"]?.trim().orEmpty()
        val license = fields["license"]?.trim().orEmpty()
        val file = fields["file"]?.trim().orEmpty()
        val usable = id.isNotEmpty() && title.isNotEmpty() && composer.isNotEmpty() &&
            license.isNotEmpty() && file.isNotEmpty() &&
            !file.startsWith("/") && !file.contains('\\') && !file.contains("..")
        if (!usable) null else MusicTrack(id, title, composer, license, file)
    }.toList()
}

/** The selected track, or Lanternlight when the id is missing from [tracks]. */
fun musicTrack(tracks: List<MusicTrack>, id: String): MusicTrack? {
    tracks.firstOrNull { it.id == id }?.let { return it }
    return tracks.firstOrNull { it.id == HomeMusicSetting.DEFAULT_TRACK_ID }
}

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
