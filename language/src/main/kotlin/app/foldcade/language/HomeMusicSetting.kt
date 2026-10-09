package app.foldcade.language

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Home-loop preference. The left panel edits this. Playback lives in the app.
 * Default level is half of the slider. The device mute is not a setting.
 */
data class HomeMusicSetting(
    val enabled: Boolean = true,
    val volume: Float = DEFAULT_VOLUME,
    val trackId: String = DEFAULT_TRACK_ID,
) {
    fun toggled(): HomeMusicSetting = copy(enabled = !enabled)

    /** Activate steps by 5 points and wraps from full to silent. */
    fun stepped(): HomeMusicSetting {
        val current = percent()
        val next = if (current >= 100) 0 else (current + STEP_PERCENT).coerceAtMost(100)
        return copy(volume = next / 100f)
    }

    /**
     * D-pad steps by 5 points and stops at silent or full.
     * [direction] is -1 to lower and +1 to raise.
     */
    fun nudged(direction: Int): HomeMusicSetting {
        val next = (percent() + direction * STEP_PERCENT).coerceIn(0, 100)
        return copy(volume = next / 100f)
    }

    fun withVolume(value: Float): HomeMusicSetting = copy(volume = value.coerceIn(0f, 1f))

    private fun percent(): Int =
        (volume.coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100)

    companion object {
        const val DEFAULT_VOLUME = 0.5f
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
 * Slider position to linear amplitude.
 * Full scale is exactly 1. A square curve would leave 25% near silence, so this
 * uses the square root. A quarter of the slider is half amplitude, about -6 dB.
 */
fun perceptualAmplitude(position: Float): Float {
    val slider = position.coerceIn(0f, 1f)
    if (slider <= 0f) return 0f
    if (slider >= 1f) return 1f
    return sqrt(slider)
}

/**
 * Player level before the entry fade. Zero when music is off, the slider is at
 * zero, or the media stream is muted. The slider uses [perceptualAmplitude], so
 * 100% is full scale and 25% is still audible. UI sounds duck the loop.
 * They do not replace it.
 */
fun playbackLevel(
    setting: HomeMusicSetting,
    uiSoundActive: Boolean,
    mediaMuted: Boolean,
): Float {
    if (!setting.enabled || mediaMuted) return 0f
    val amplitude = perceptualAmplitude(setting.volume)
    return if (uiSoundActive) amplitude * HomeMusicSetting.UI_SOUND_DUCK else amplitude
}

/**
 * A UI-sound duck. A late callback from an older duck cannot turn it back on,
 * and [clear] drops it when the player is released.
 */
class DuckLatch {
    var active: Boolean = false
        private set

    private var generation: Int = 0

    fun begin(): Int {
        active = true
        generation += 1
        return generation
    }

    /** Clears the duck when [generation] is still the latest [begin]. */
    fun end(generation: Int): Boolean {
        if (generation != this.generation) return false
        active = false
        return true
    }

    fun clear() {
        generation += 1
        active = false
    }
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
 * Home music may request audio focus only while a Foldcade home is in front,
 * no launched game is in front on either screen, and another app is not already
 * playing on the music stream.
 */
fun homeMusicMayStart(
    homeInFront: Boolean,
    gameInFront: Boolean,
    otherAudioActive: Boolean,
): Boolean = homeInFront && !gameInFront && !otherAudioActive

/**
 * A resumed home does not clear this while the launched game is still in front.
 * The flag drops only after that game has left both screens.
 */
fun homeMusicStaysSuppressed(suppressed: Boolean, gameInFront: Boolean): Boolean =
    suppressed && gameInFront

/**
 * After a transient audio-focus loss, playback should fade in from silence
 * instead of jumping back to the target level. A game still in front does not
 * take the focus back.
 */
fun fadeInOnFocusReturn(
    playbackResumed: Boolean,
    fromAudioFocus: Boolean,
    homeInFront: Boolean,
    gameInFront: Boolean,
): Boolean = playbackResumed && fromAudioFocus && homeInFront && !gameInFront

/**
 * Theme path, then the selected manifest file, then Lanternlight.
 * A manifest entry whose asset is missing does not stay silent.
 */
fun packagedHomeMusicFile(
    themeJson: String?,
    selectedFile: String?,
    assetExists: (String) -> Boolean,
): String {
    val named = backgroundMusicFromThemeJson(themeJson)
    if (named != DEFAULT_BACKGROUND_MUSIC && assetExists(named)) return named
    if (!selectedFile.isNullOrBlank() && assetExists(selectedFile)) return selectedFile
    return DEFAULT_BACKGROUND_MUSIC
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
