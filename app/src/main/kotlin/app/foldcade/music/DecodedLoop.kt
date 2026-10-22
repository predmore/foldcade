package app.foldcade.music

import android.content.res.AssetFileDescriptor
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Home loops decoded once to 16-bit PCM, in WAV files under the cache. Playing PCM needs no
 * decoder, and a looping Ogg kept one busy for as long as home was up.
 *
 * [ready] answers at once: the file when it is there, else null, and the first ask for a
 * source starts its decode on a thread of its own. The caller plays the encoded file
 * meanwhile, and the decoded one the next time it makes a player.
 */
internal class DecodedLoop(cacheDir: File) {
    private val folder = File(cacheDir, FOLDER)
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "HomeMusicDecode").apply { priority = Thread.MIN_PRIORITY }
    }
    private val started = mutableSetOf<String>()

    /** A file to decode: [key] changes whenever its bytes may have, and [open] points an extractor at it. */
    class Source(val key: String, val open: (MediaExtractor) -> Unit)

    fun ready(source: Source): File? {
        val file = fileFor(source.key)
        if (file.isFile) return file
        synchronized(started) { if (!started.add(source.key)) return null }
        worker.execute { decode(source, file) }
        return null
    }

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return File(folder, digest.take(12).joinToString("") { "%02x".format(it) } + ".wav")
    }

    private fun decode(source: Source, file: File) {
        folder.mkdirs()
        val partial = File(folder, file.name + ".partial")
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            source.open(extractor)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("no audio track")
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            // The container's length, so a decoder's padding at the end does not open a gap at the loop point.
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else -1L
            RandomAccessFile(partial, "rw").use { out ->
                out.setLength(0)
                out.write(ByteArray(WAV_HEADER_BYTES))
                var written = 0L
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var limit = Long.MAX_VALUE
                val chunk = ByteArray(CHUNK_BYTES)
                while (true) {
                    if (!inputDone) {
                        val index = decoder.dequeueInputBuffer(TIMEOUT_US)
                        if (index >= 0) {
                            val size = extractor.readSampleData(decoder.getInputBuffer(index)!!, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val index = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val output = decoder.outputFormat
                        val encoding = if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            output.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else {
                            AudioFormat.ENCODING_PCM_16BIT
                        }
                        require(encoding == AudioFormat.ENCODING_PCM_16BIT) { "decoder gives PCM encoding $encoding" }
                        rate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (durationUs > 0) limit = durationUs * rate / 1_000_000L * channels * BYTES_PER_SAMPLE
                        continue
                    }
                    if (index < 0) continue
                    val buffer = decoder.getOutputBuffer(index)!!
                    buffer.position(info.offset)
                    var left = minOf(info.size.toLong(), limit - written).coerceAtLeast(0L).toInt()
                    while (left > 0) {
                        val step = minOf(left, chunk.size)
                        buffer.get(chunk, 0, step)
                        out.write(chunk, 0, step)
                        written += step
                        left -= step
                    }
                    decoder.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
                require(written > 0) { "decoded nothing" }
                out.seek(0)
                out.write(wavHeader(written, rate, channels))
            }
            // Only one decoded loop is kept: the one now chosen.
            folder.listFiles()?.filter { it != partial && it != file }?.forEach { it.delete() }
            if (!partial.renameTo(file)) error("cannot keep ${file.name}")
        } catch (error: Exception) {
            // The encoded file keeps playing as before. The next process tries again.
            Log.w(TAG, "Home music stays encoded: ${error.message}")
            partial.delete()
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    companion object {
        private const val TAG = "HomeMusic"
        private const val FOLDER = "home-music"
        private const val WAV_HEADER_BYTES = 44
        private const val BYTES_PER_SAMPLE = 2
        private const val CHUNK_BYTES = 64 * 1024
        private const val TIMEOUT_US = 10_000L

        /** An extractor opener for an asset kept uncompressed in the APK. */
        fun asset(descriptor: () -> AssetFileDescriptor): (MediaExtractor) -> Unit = { extractor ->
            descriptor().use { extractor.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        }
    }
}

/** A canonical 44-byte header for [dataBytes] of 16-bit PCM. */
internal fun wavHeader(dataBytes: Long, rate: Int, channels: Int): ByteArray =
    ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt((36 + dataBytes).toInt()); put("WAVE".toByteArray())
        put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(channels.toShort())
        putInt(rate); putInt(rate * channels * 2)
        putShort((channels * 2).toShort()); putShort(16)
        put("data".toByteArray()); putInt(dataBytes.toInt())
    }.array()
