package app.foldcade.music

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.foldcade.SessionStore
import app.foldcade.language.DEFAULT_BACKGROUND_MUSIC
import app.foldcade.language.DEFAULT_TRACK_TITLE
import app.foldcade.language.HomeMusicSetting
import app.foldcade.language.Meaning
import app.foldcade.language.Motion
import app.foldcade.language.MusicTrack
import app.foldcade.language.backgroundMusicFromThemeJson
import app.foldcade.language.musicTrack
import app.foldcade.language.musicTracksFromManifest
import app.foldcade.language.playbackLevel

/**
 * Loops the home bed while a Foldcade home is in front, and stops when a game
 * or another app takes the screen. The fade is the 1.5 s entry and exit the
 * home loop uses. It is not a layout transition, so it does not borrow a
 * motion duration. The curve is still [Motion]'s arrive and leave pair.
 */
class HomeMusic(
    private val app: Application,
    private val store: SessionStore,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val audio = app.getSystemService(AudioManager::class.java)
    private var player: ExoPlayer? = null
    private var loadedAsset: String? = null
    private var themeJson: String? = null
    private var resumedHomes = 0
    private var suppressed = false
    private var fadeToken = 0
    private var duckToken = 0
    private var ducked = false
    private val catalog: List<MusicTrack> = loadCatalog()

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshForStream()
        }
    }

    init {
        val filter = IntentFilter(VOLUME_CHANGED)
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        app.registerReceiver(volumeReceiver, filter, Context.RECEIVER_EXPORTED)
    }

    fun setThemeJson(json: String?) {
        themeJson = json
        val next = resolvedAsset()
        val existing = player ?: return
        if (next == loadedAsset) return
        loadedAsset = next
        existing.setMediaItem(MediaItem.fromUri("asset:///$next"))
        existing.prepare()
    }

    fun onHomeResume() {
        val wasAway = resumedHomes == 0
        resumedHomes++
        if (suppressed || wasAway) {
            suppressed = false
            fadeIn()
        }
    }

    fun onHomePause() {
        resumedHomes = (resumedHomes - 1).coerceAtLeast(0)
        if (resumedHomes == 0) fadeOut()
    }

    /** A game or any other app Foldcade starts. The loop stays down until home resumes. */
    fun onExternalLaunch() {
        suppressed = true
        fadeOut()
    }

    fun trackTitle(): String = selectedTrack()?.title ?: DEFAULT_TRACK_TITLE

    fun apply(setting: HomeMusicSetting) {
        reloadIfTrackChanged()
        if (resumedHomes == 0 || suppressed) return
        if (playbackLevel(setting, ducked, mediaMuted()) <= 0f) {
            fadeOut()
            return
        }
        val existing = player
        if (existing != null && existing.isPlaying) {
            fadeToken++
            existing.volume = targetLevel()
        } else {
            fadeIn()
        }
    }

    /**
     * Duck only when this theme actually has the UI sound for [meaning].
     * The built-in theme has none, so the loop does not dip on every key.
     */
    fun duckForThemeSound(meaning: Meaning) {
        val slot = when (meaning) {
            Meaning.MoveUp, Meaning.MoveDown, Meaning.MoveLeft, Meaning.MoveRight -> "move"
            Meaning.Activate -> "activate"
            Meaning.Back -> "back"
            else -> return
        }
        if (!assetExists("sounds/$slot.ogg")) return
        val existing = player ?: return
        if (!existing.isPlaying) return
        ducked = true
        existing.volume = targetLevel()
        val token = ++duckToken
        handler.postDelayed({
            if (token != duckToken) return@postDelayed
            ducked = false
            if (existing.isPlaying) existing.volume = targetLevel()
        }, 220)
    }

    private fun refreshForStream() {
        if (resumedHomes == 0 || suppressed) return
        if (mediaMuted() || !store.musicEnabled()) fadeOut() else fadeIn()
    }

    private fun fadeIn() {
        if (playbackLevel(current(), ducked, mediaMuted()) <= 0f) {
            holdSilent()
            return
        }
        val existing = ensurePlayer() ?: return
        existing.play()
        fade(targetLevel(), FADE_MS, arriving = true, pauseAtEnd = false)
    }

    private fun fadeOut() {
        if (player == null) return
        fade(0f, FADE_MS, arriving = false, pauseAtEnd = true)
    }

    private fun holdSilent() {
        fadeToken++
        player?.let {
            it.volume = 0f
            it.pause()
        }
    }

    private fun fade(target: Float, durationMs: Long, arriving: Boolean, pauseAtEnd: Boolean) {
        val existing = if (target > 0f) ensurePlayer() ?: return else player ?: return
        val token = ++fadeToken
        val from = existing.volume
        val easing = if (arriving) Motion.easingArrive else Motion.easingLeave
        val steps = 24
        val stepMs = (durationMs / steps).coerceAtLeast(16)
        if (target > 0f) existing.play()
        fun frame(index: Int) {
            if (token != fadeToken) return
            val t = index / steps.toFloat()
            existing.volume = from + (target - from) * easing.transform(t)
            if (index < steps) {
                handler.postDelayed({ frame(index + 1) }, stepMs)
            } else {
                existing.volume = target
                if (pauseAtEnd && target <= 0.001f) existing.pause()
            }
        }
        frame(1)
    }

    private fun ensurePlayer(): ExoPlayer? {
        player?.let { return it }
        val asset = resolvedAsset()
        if (!assetExists(asset)) {
            Log.e(TAG, "Home music asset $asset is not in the apk. Rebuild so renderHomeMusic can package it.")
            return null
        }
        val created = ExoPlayer.Builder(app).build()
        created.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
        created.repeatMode = Player.REPEAT_MODE_ONE
        created.volume = 0f
        created.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Home music stopped because the loop could not play.", error)
                    created.pause()
                }
            },
        )
        created.setMediaItem(MediaItem.fromUri("asset:///$asset"))
        created.prepare()
        loadedAsset = asset
        player = created
        return created
    }

    private fun selectedTrack(): MusicTrack? = musicTrack(catalog, store.musicTrackId())

    private fun resolvedAsset(): String {
        val named = backgroundMusicFromThemeJson(themeJson)
        if (named != DEFAULT_BACKGROUND_MUSIC && assetExists(named)) return named
        return selectedTrack()?.file ?: DEFAULT_BACKGROUND_MUSIC
    }

    private fun reloadIfTrackChanged() {
        val existing = player ?: return
        val next = resolvedAsset()
        if (next == loadedAsset || !assetExists(next)) return
        loadedAsset = next
        existing.setMediaItem(MediaItem.fromUri("asset:///$next"))
        existing.prepare()
    }

    private fun loadCatalog(): List<MusicTrack> {
        val json = runCatching { app.assets.open("music/manifest.json").bufferedReader().use { it.readText() } }
            .getOrNull()
        return musicTracksFromManifest(json)
    }

    private fun assetExists(name: String): Boolean =
        runCatching {
            app.assets.open(name).close()
            true
        }.getOrDefault(false)

    private fun current(): HomeMusicSetting =
        HomeMusicSetting(store.musicEnabled(), store.musicVolume(), store.musicTrackId())

    private fun targetLevel(): Float = playbackLevel(current(), ducked, mediaMuted = false)

    private fun mediaMuted(): Boolean {
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0) return true
        return audio.isStreamMute(AudioManager.STREAM_MUSIC)
    }

    private companion object {
        const val TAG = "HomeMusic"
        const val FADE_MS = 1500L
        const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
    }
}
