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
import app.foldcade.language.fadeInOnFocusReturn
import app.foldcade.language.homeMusicMayStart
import app.foldcade.language.homeMusicStaysSuppressed
import app.foldcade.language.musicTrack
import app.foldcade.language.musicTracksFromManifest
import app.foldcade.language.packagedHomeMusicFile
import app.foldcade.language.playbackLevel

/**
 * Loops the home bed while a Foldcade home is in front, and stops when a game
 * or another app takes the screen. The fade is the 1.5 s entry and exit the
 * home loop uses. It is not a layout transition, so it does not borrow a
 * motion duration. The curve is still [Motion]'s arrive and leave pair.
 *
 * The player requests audio focus only while it is actually playing. Media3
 * keeps AUDIOFOCUS_GAIN across pause(), so a finished fade releases the player.
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
    private var awaitingFocusFade = false
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
        app.registerReceiver(volumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
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
        if (gameInFront()) {
            suppressed = true
            if (player != null) fadeOut()
            return
        }
        val wasSuppressed = suppressed
        suppressed = homeMusicStaysSuppressed(suppressed, gameInFront = false)
        if (!mayStart()) return
        if (wasAway || wasSuppressed) fadeIn()
    }

    fun onHomePause() {
        resumedHomes = (resumedHomes - 1).coerceAtLeast(0)
        if (resumedHomes == 0) fadeOut()
    }

    /** A game or any other app Foldcade starts. The loop stays down until that game leaves. */
    fun onExternalLaunch() {
        suppressed = true
        fadeOut()
    }

    /** Session updates when a game is placed or cleared. Suppression follows the game, not the resume. */
    fun onSessionChanged() {
        if (gameInFront()) {
            suppressed = true
            if (player != null) fadeOut()
            return
        }
        val wasSuppressed = suppressed
        suppressed = false
        if (wasSuppressed && mayStart()) fadeIn()
    }

    fun trackTitle(): String = selectedTrack()?.title ?: DEFAULT_TRACK_TITLE

    fun apply(setting: HomeMusicSetting) {
        reloadIfTrackChanged()
        if (!mayStart()) return
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
            if (player === existing && existing.isPlaying) existing.volume = targetLevel()
        }, 220)
    }

    private fun refreshForStream() {
        if (!mayStart()) return
        if (mediaMuted() || !store.musicEnabled()) fadeOut() else fadeIn()
    }

    private fun mayStart(): Boolean = homeMusicMayStart(
        homeInFront = resumedHomes > 0,
        gameInFront = gameInFront(),
        otherAudioActive = otherAudioActive(),
    )

    private fun gameInFront(): Boolean = !store.session.bothScreensFree()

    /** True when some other app owns the music stream. Our own player does not count. */
    private fun otherAudioActive(): Boolean {
        if (player?.isPlaying == true) return false
        return audio.isMusicActive
    }

    private fun fadeIn() {
        if (playbackLevel(current(), ducked, mediaMuted()) <= 0f) {
            releasePlayer()
            return
        }
        val existing = ensurePlayer() ?: return
        existing.play()
        fade(targetLevel(), FADE_MS, arriving = true, releaseAtEnd = false)
    }

    private fun fadeOut() {
        if (player == null) return
        fade(0f, FADE_MS, arriving = false, releaseAtEnd = true)
    }

    private fun fade(target: Float, durationMs: Long, arriving: Boolean, releaseAtEnd: Boolean) {
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
                if (releaseAtEnd && target <= 0.001f) releasePlayer()
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
        created.addListener(focusListener(created))
        created.setMediaItem(MediaItem.fromUri("asset:///$asset"))
        created.prepare()
        loadedAsset = asset
        player = created
        return created
    }

    private fun focusListener(created: ExoPlayer) = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Home music stopped because the loop could not play.", error)
            releasePlayer()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) return
            if (!playWhenReady) {
                // Permanent loss. pause() would keep AUDIOFOCUS_GAIN, so drop the player.
                fadeToken++
                awaitingFocusFade = false
                handler.post { releasePlayer() }
                return
            }
            noteFocusLoss(created)
            if (created.isPlaying) resumeAfterFocus()
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) {
                noteFocusLoss(created)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && awaitingFocusFade) resumeAfterFocus()
        }
    }

    private fun noteFocusLoss(created: ExoPlayer) {
        fadeToken++
        awaitingFocusFade = true
        created.volume = 0f
    }

    private fun resumeAfterFocus() {
        if (!awaitingFocusFade) return
        val resume = fadeInOnFocusReturn(
            playbackResumed = true,
            fromAudioFocus = true,
            homeInFront = resumedHomes > 0,
            gameInFront = gameInFront(),
        )
        awaitingFocusFade = false
        handler.post {
            if (!resume || suppressed || gameInFront() || resumedHomes == 0) {
                releasePlayer()
                return@post
            }
            player?.volume = 0f
            fadeIn()
        }
    }

    /** pause() does not abandon AUDIOFOCUS_GAIN. Release does. */
    private fun releasePlayer() {
        fadeToken++
        awaitingFocusFade = false
        val existing = player ?: return
        player = null
        loadedAsset = null
        existing.release()
    }

    private fun selectedTrack(): MusicTrack? = musicTrack(catalog, store.musicTrackId())

    private fun resolvedAsset(): String = packagedHomeMusicFile(
        themeJson = themeJson,
        selectedFile = selectedTrack()?.file,
        assetExists = ::assetExists,
    )

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
