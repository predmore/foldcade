package app.foldcade.language

import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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

/**
 * One choosable loop. Manifest rows come from music/tracks/manifest.json.
 * A theme row has [fromTheme] and is offered only while that theme is selected.
 * The setting stores [id], not the file name.
 */
data class MusicTrack(
    val id: String,
    val title: String,
    val composer: String,
    val license: String,
    val file: String,
    val fromTheme: Boolean = false,
)

/** Id for the track a theme zip offers. It is not a manifest id. */
const val THEME_TRACK_ID = "theme"

fun defaultLanternlightTrack(): MusicTrack = MusicTrack(
    id = HomeMusicSetting.DEFAULT_TRACK_ID,
    title = DEFAULT_TRACK_TITLE,
    composer = "Foldcade project",
    license = "GPLv3",
    file = DEFAULT_BACKGROUND_MUSIC,
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
 * Only objects in the `tracks` array are tracks. A bare object is not a list.
 */
fun musicTracksFromManifest(json: String?): List<MusicTrack> {
    val tracks = jsonObject(json)?.get("tracks") as? JsonArray ?: return emptyList()
    return tracks.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = obj.text("id") ?: return@mapNotNull null
        val title = obj.text("title") ?: return@mapNotNull null
        val composer = obj.text("composer") ?: return@mapNotNull null
        val license = obj.text("license") ?: return@mapNotNull null
        val file = obj.text("file") ?: return@mapNotNull null
        if (!safeMusicPath(file)) null else MusicTrack(id, title, composer, license, file)
    }
}

/** The selected track, or Lanternlight when the id is missing from [tracks]. */
fun musicTrack(tracks: List<MusicTrack>, id: String): MusicTrack? {
    tracks.firstOrNull { it.id == id }?.let { return it }
    return tracks.firstOrNull { it.id == HomeMusicSetting.DEFAULT_TRACK_ID }
}

/**
 * Home tracks, plus the theme's own track when this theme offers one.
 * An empty home list still offers Lanternlight, so the row has somewhere to land.
 * A theme track whose file is already a home track is not listed twice.
 */
fun offeredMusicTracks(home: List<MusicTrack>, theme: MusicTrack?): List<MusicTrack> {
    val base = home.ifEmpty { listOf(defaultLanternlightTrack()) }
    if (theme == null || !theme.fromTheme) return base
    if (base.any { it.id == theme.id || it.file == theme.file }) return base
    return base + theme
}

/** [id] when it is in [tracks], otherwise Lanternlight, otherwise the first track. */
fun selectedMusicTrack(tracks: List<MusicTrack>, id: String): MusicTrack {
    val list = tracks.ifEmpty { listOf(defaultLanternlightTrack()) }
    list.firstOrNull { it.id == id }?.let { return it }
    list.firstOrNull { it.id == HomeMusicSetting.DEFAULT_TRACK_ID }?.let { return it }
    return list.first()
}

/**
 * Next or previous track. [direction] is negative to go backward.
 * One track stays put. A missing [id] starts from the track [selectedMusicTrack] would show.
 */
fun cycledMusicTrack(tracks: List<MusicTrack>, id: String, direction: Int): MusicTrack {
    val list = tracks.ifEmpty { listOf(defaultLanternlightTrack()) }
    val current = selectedMusicTrack(list, id)
    val index = list.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
    val step = if (direction < 0) -1 else 1
    return list[(index + step).mod(list.size)]
}

/**
 * The track a theme zip offers, or null when it does not name one.
 * [entries] are the zip's file names. The path has to be one of them.
 * Lanternlight, a blank field, and a path that leaves the zip are not an offer.
 */
fun themeMusicTrack(themeName: String, themeJson: String?, entries: Set<String>): MusicTrack? {
    val path = backgroundMusicFromThemeJson(themeJson)
    if (path == DEFAULT_BACKGROUND_MUSIC || path !in entries) return null
    val name = themeName.trim().ifEmpty { path.substringAfterLast('/').substringBeforeLast('.') }
    return MusicTrack(
        id = THEME_TRACK_ID,
        title = name,
        composer = name,
        license = "theme",
        file = path,
        fromTheme = true,
    )
}

/**
 * Home music plays while Foldcade is in front on every screen and no launched
 * game holds either one, however Foldcade got there. Any app open on either
 * screen stops it. Another app's audio does not hold it back.
 */
fun homeMusicMayStart(
    homeOnEveryScreen: Boolean,
    gameInFront: Boolean,
): Boolean = homeOnEveryScreen && !gameInFront

/**
 * True when a Foldcade home is resumed on every lit screen. A screen that is
 * off does not count against it, but at least one home must be resumed.
 */
fun <P> homeOnEveryScreen(resumed: Set<P>, lit: Set<P>): Boolean =
    resumed.isNotEmpty() && resumed.containsAll(lit)

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
 * The file the picker selected, when that file is available, otherwise Lanternlight.
 * A theme track plays only when it is the selection. This does not read theme.json.
 */
fun packagedHomeMusicFile(
    selectedFile: String?,
    assetExists: (String) -> Boolean,
): String {
    if (!selectedFile.isNullOrBlank() && assetExists(selectedFile)) return selectedFile
    return DEFAULT_BACKGROUND_MUSIC
}

/**
 * Optional `backgroundMusic` string in theme.json. A community theme names a
 * file inside its zip. The picker offers that file while the theme is selected.
 * A missing, blank, or non-string field keeps [DEFAULT_BACKGROUND_MUSIC], which is not a second track.
 */
fun backgroundMusicFromThemeJson(json: String?): String {
    val value = jsonObject(json)?.text(THEME_BACKGROUND_MUSIC) ?: return DEFAULT_BACKGROUND_MUSIC
    if (!safeMusicPath(value)) return DEFAULT_BACKGROUND_MUSIC
    return value
}

private fun jsonObject(json: String?): JsonObject? {
    if (json.isNullOrBlank()) return null
    val element = runCatching { Json.parseToJsonElement(json) }.getOrNull() ?: return null
    return element as? JsonObject
}

private fun JsonObject.text(name: String): String? {
    val value = this[name] as? JsonPrimitive ?: return null
    if (!value.isString) return null
    return value.content.trim().ifEmpty { null }
}

private fun safeMusicPath(value: String): Boolean =
    !value.startsWith("/") && !value.contains('\\') && !value.contains("..")
