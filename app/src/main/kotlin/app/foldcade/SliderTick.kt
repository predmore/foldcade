package app.foldcade

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Soft tick for a slider step when the theme has no move clip.
 * Original 45 ms tone at 780 Hz, the same shape as the theme move tick.
 * No sample library and no third-party mark.
 */
class SliderTick {
    private val track: AudioTrack? = build()

    fun play() {
        val clip = track ?: return
        if (clip.playState == AudioTrack.PLAYSTATE_PLAYING) clip.pause()
        clip.reloadStaticData()
        clip.play()
    }

    private fun build(): AudioTrack? = runCatching {
        val pcm = synthesize()
        val format = AudioFormat.Builder()
            .setSampleRate(RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        created.write(pcm, 0, pcm.size)
        created
    }.getOrNull().also {
        if (it == null) android.util.Log.w("Foldcade", "Slider tick unavailable")
    }

    private fun synthesize(): ShortArray {
        val count = (RATE * 0.045f).toInt()
        val samples = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / RATE
            val env = sin(PI * min(1.0, t / 0.045)).pow(0.6)
            val attack = min(1f, t / 0.008f)
            val wave = sin(2.0 * PI * 780.0 * t) + 0.12 * sin(4.0 * PI * 780.0 * t)
            val sample = wave * 0.45 * env * attack
            samples[i] = (sample * 32767.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return samples
    }

    private companion object {
        const val RATE = 44100
    }
}
