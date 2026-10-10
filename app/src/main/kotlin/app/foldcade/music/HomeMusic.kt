package app.foldcade.music

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.foldcade.Displays
import app.foldcade.SessionStore
import app.foldcade.api.Panel
import app.foldcade.language.DuckLatch
import app.foldcade.language.HomeMusicSetting
import app.foldcade.language.Meaning
import app.foldcade.language.Motion
import app.foldcade.language.MusicTrack
import app.foldcade.language.fadeInOnFocusReturn
import app.foldcade.language.homeMusicMayStart
import app.foldcade.language.homeOnEveryScreen
import app.foldcade.language.defaultLanternlightTrack
import app.foldcade.language.musicTracksFromManifest
import app.foldcade.language.offeredMusicTracks
import app.foldcade.language.packagedHomeMusicFile
import app.foldcade.language.playbackLevel
import app.foldcade.language.selectedMusicTrack
import java.io.File
import java.util.Locale

/**
 * Loops the home bed while Foldcade is in front on every screen, and stops when
 * a game or any other app takes either one. Every change of that state goes
 * through [reconcile], so how Foldcade came back does not matter. The fade is the 1.5 s entry and exit the
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
    private var loadedKey: String? = null
    private var themeChoice: MusicTrack? = null
    private val displays = Displays(app)
    private val resumedPanels = mutableSetOf<Panel>()

    /** Another app took audio focus for good while home was in front. Cleared when home leaves a screen. */
    private var yieldedFocus = false
    private var awaitingFocusFade = false
    private var fadeToken = 0
    private val duck = DuckLatch()
    private var fadingOut = false
    private val catalog: List<MusicTrack> = loadCatalog()

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshForStream()
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = reconcile()
        override fun onDisplayRemoved(displayId: Int) = reconcile()
        override fun onDisplayChanged(displayId: Int) = reconcile()
    }

    init {
        val filter = IntentFilter(VOLUME_CHANGED)
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        app.registerReceiver(volumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        app.getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, handler)
    }

    /** The theme's own track, or null on the built-in theme and on a theme with no loop. */
    fun setThemeChoice(track: MusicTrack?) {
        if (!onMainThread()) {
            handler.post { setThemeChoice(track) }
            return
        }
        themeChoice = track?.takeIf { it.fromTheme }
        reloadIfTrackChanged()
    }

    fun homeTracks(): List<MusicTrack> = catalog

    fun onHomeResume(panel: Panel) {
        if (!onMainThread()) {
            handler.post { onHomeResume(panel) }
            return
        }
        resumedPanels += panel
        reconcile()
    }

    fun onHomePause(panel: Panel) {
        if (!onMainThread()) {
            handler.post { onHomePause(panel) }
            return
        }
        resumedPanels -= panel
        yieldedFocus = false
        reconcile()
    }

    /** A game or any other app Foldcade starts. Fades now, before its panel pauses. */
    fun onExternalLaunch() {
        if (!onMainThread()) {
            handler.post { onExternalLaunch() }
            return
        }
        fadeOut()
    }

    /** A game was placed on a panel or left one. */
    fun onSessionChanged() {
        if (!onMainThread()) {
            handler.post { onSessionChanged() }
            return
        }
        reconcile()
    }

    fun trackTitle(): String = selectedTrack().title

    fun apply(setting: HomeMusicSetting) {
        if (!onMainThread()) {
            handler.post { apply(setting) }
            return
        }
        reloadIfTrackChanged()
        if (!mayStart()) return
        val level = playbackLevel(setting, duck.active, mediaMuted())
        if (!setting.enabled || mediaMuted()) {
            fadeOut()
            return
        }
        // A slider move is audible now. The 1.5 s fade stays for entering and
        // leaving home. Waiting for that fade is what made a drag feel late.
        if (level <= 0f) {
            val existing = player ?: return
            fadingOut = false
            writeNow(existing, 0f, "slider")
            return
        }
        val existing = ensurePlayer() ?: return
        fadingOut = false
        existing.play()
        writeNow(existing, level, "slider")
    }

    /**
     * Duck only when this theme actually has the UI sound for [meaning].
     * The built-in theme has none, so the loop does not dip on every key.
     */
    fun duckForThemeSound(meaning: Meaning) {
        if (!onMainThread()) {
            handler.post { duckForThemeSound(meaning) }
            return
        }
        val slot = when (meaning) {
            Meaning.MoveUp, Meaning.MoveDown, Meaning.MoveLeft, Meaning.MoveRight -> "move"
            Meaning.Activate -> "activate"
            Meaning.Back -> "back"
            else -> return
        }
        if (!assetExists("sounds/$slot.ogg")) return
        val existing = player ?: return
        if (!existing.isPlaying) return
        val generation = duck.begin()
        if (!fadingOut) writeNow(existing, targetLevel(), "duck")
        handler.postDelayed({
            if (!duck.end(generation)) return@postDelayed
            val current = player
            if (current == null || fadingOut || !current.isPlaying) return@postDelayed
            writeNow(current, targetLevel(), "unduck")
        }, 220)
    }

    private fun refreshForStream() = reconcile()

    /** Plays exactly while [mayStart], the setting is on, and the stream is audible. */
    private fun reconcile() {
        if (!onMainThread()) {
            handler.post { reconcile() }
            return
        }
        if (!mayStart() || !store.musicEnabled() || mediaMuted()) {
            // Display changes arrive often. Restarting a running fade would only stretch it.
            if (!fadingOut) fadeOut()
            return
        }
        if (yieldedFocus) return
        val existing = player
        if (existing == null || fadingOut || !existing.playWhenReady) fadeIn()
    }

    private fun mayStart(): Boolean = homeMusicMayStart(
        homeOnEveryScreen = homeOnEveryScreen(),
        gameInFront = gameInFront(),
    )

    private fun homeOnEveryScreen(): Boolean {
        val assignment = displays.assignment(store.session.defaultDisplayIsTop)
        val manager = app.getSystemService(DisplayManager::class.java)
        val lit = assignment.allIds()
            .filter { manager.getDisplay(it)?.state == Display.STATE_ON }
            .mapNotNull { assignment.panelFor(it) }
            .toSet()
        return homeOnEveryScreen(resumedPanels, lit)
    }

    private fun gameInFront(): Boolean = !store.session.bothScreensFree()

    private fun fadeIn() {
        fadingOut = false
        if (playbackLevel(current(), duck.active, mediaMuted()) <= 0f) {
            releasePlayer()
            return
        }
        val existing = ensurePlayer() ?: return
        existing.play()
        fade(arriving = true, releaseAtEnd = false) { targetLevel() }
    }

    private fun fadeOut() {
        if (player == null) return
        fadingOut = true
        fade(arriving = false, releaseAtEnd = true) { 0f }
    }

    /**
     * Each frame reads [targetOf] again. A duck that ends during the fade
     * cannot leave the player at the ducked level, and a cancelled fade
     * (slider, duck, release) stops writing.
     */
    private fun fade(arriving: Boolean, releaseAtEnd: Boolean, targetOf: () -> Float) {
        val existing = if (!releaseAtEnd) ensurePlayer() ?: return else player ?: return
        val token = ++fadeToken
        val from = existing.volume
        val easing = if (arriving) Motion.easingArrive else Motion.easingLeave
        val steps = 24
        val stepMs = (FADE_MS / steps).coerceAtLeast(16)
        if (!releaseAtEnd) existing.play()
        fun frame(index: Int) {
            if (token != fadeToken) return
            val target = targetOf()
            val t = index / steps.toFloat()
            existing.volume = from + (target - from) * easing.transform(t)
            if (index < steps) {
                handler.postDelayed({ frame(index + 1) }, stepMs)
            } else {
                val end = targetOf()
                existing.volume = end
                if (releaseAtEnd && end <= 0.001f) releasePlayer()
            }
        }
        frame(1)
    }

    /** Cancels an in-progress fade so the next frame cannot overwrite [level]. */
    private fun writeNow(existing: ExoPlayer, level: Float, reason: String) {
        fadeToken++
        existing.volume = level
        Log.i(
            TAG,
            "player.volume=%.3f slider=%.2f ducked=%s reason=%s".format(
                Locale.US,
                existing.volume,
                current().volume,
                duck.active,
                reason,
            ),
        )
    }

    private fun ensurePlayer(): ExoPlayer? {
        player?.let { return it }
        val playable = resolvedPlayable()
        if (playable == null) {
            Log.e(TAG, "Home music asset is not in the apk. Rebuild so renderHomeMusic can package it.")
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
        created.setMediaItem(playable.item)
        created.prepare()
        loadedKey = playable.key
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
                // Permanent loss. pause() would keep AUDIOFOCUS_GAIN, so drop the player,
                // and do not take the focus back until home leaves a screen.
                yieldedFocus = true
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
            homeInFront = homeOnEveryScreen(),
            gameInFront = gameInFront(),
        )
        awaitingFocusFade = false
        handler.post {
            if (!resume || !mayStart()) {
                releasePlayer()
                return@post
            }
            player?.volume = 0f
            fadeIn()
        }
    }

    /**
     * Drop the player only after the UI is hidden. A running-low trim while
     * home is in front must leave the loop playing.
     */
    fun onTrimMemory(level: Int) {
        if (!onMainThread()) {
            handler.post { onTrimMemory(level) }
            return
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) releasePlayer()
    }

    /** pause() does not abandon AUDIOFOCUS_GAIN. Release does. */
    private fun releasePlayer() {
        fadeToken++
        duck.clear()
        fadingOut = false
        awaitingFocusFade = false
        val existing = player ?: return
        player = null
        loadedKey = null
        existing.release()
    }

    private fun selectedTrack(): MusicTrack =
        selectedMusicTrack(offeredMusicTracks(catalog, themeChoice), store.musicTrackId())

    private fun resolvedPlayable(): Playable? {
        val selected = selectedTrack()
        if (selected.fromTheme) {
            return themePlayable(selected) ?: assetPlayable(defaultLanternlightTrack())
        }
        return assetPlayable(selected) ?: assetPlayable(defaultLanternlightTrack())
    }

    private fun assetPlayable(track: MusicTrack): Playable? {
        val file = packagedHomeMusicFile(track.file, ::assetExists)
        if (!assetExists(file)) return null
        return Playable(file, MediaItem.fromUri("asset:///$file"))
    }

    /** Theme audio is a file this process wrote under its own cache. */
    private fun themePlayable(track: MusicTrack): Playable? {
        val file = runCatching { File(track.file).canonicalFile }.getOrNull() ?: return null
        val cache = runCatching { app.cacheDir.canonicalFile }.getOrNull() ?: return null
        if (file.parentFile != cache || !file.isFile) return null
        return Playable(file.absolutePath, MediaItem.fromUri(Uri.fromFile(file)))
    }

    private fun reloadIfTrackChanged() {
        val existing = player ?: return
        val next = resolvedPlayable() ?: return
        if (next.key == loadedKey) return
        loadedKey = next.key
        existing.setMediaItem(next.item)
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

    private fun onMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    private fun current(): HomeMusicSetting =
        HomeMusicSetting(store.musicEnabled(), store.musicVolume(), store.musicTrackId())

    private fun targetLevel(): Float = playbackLevel(current(), duck.active, mediaMuted = false)

    private fun mediaMuted(): Boolean {
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0) return true
        return audio.isStreamMute(AudioManager.STREAM_MUSIC)
    }

    private data class Playable(val key: String, val item: MediaItem)

    private companion object {
        const val TAG = "HomeMusic"
        const val FADE_MS = 1500L
        const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
    }
}
