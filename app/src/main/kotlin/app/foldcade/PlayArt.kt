package app.foldcade

import android.graphics.Bitmap
import app.foldcade.host.play.isPlayArtName
import app.foldcade.host.play.playArtName
import java.io.File
import java.io.FileOutputStream

/**
 * Each played game's cover, kept beside its play history under `files/play-sessions/art`.
 *
 * Covers otherwise come from a library or an art site, and leave with the game: a ROM
 * renamed, replaced, or removed, or a server signed out of. Play activity and a game's
 * own page still show the game then, from here. One small square-ish image per game,
 * named by [playArtName], so a cover is the same file in an export. Files, not cache:
 * Android does not evict them, and backup and export take them.
 */
class PlayArt(private val dir: File) {
    fun file(gameId: String): File? = File(dir, playArtName(gameId)).takeIf { it.isFile && it.length() > 0L }

    fun files(): List<File> = dir.listFiles()?.filter { it.isFile && isPlayArtName(it.name) }.orEmpty()

    /** Keeps [bitmap] as [gameId]'s cover, replacing an older one. Runs on the caller's thread: call it off the main one. */
    fun keep(gameId: String, bitmap: Bitmap) {
        val scaled = fitted(bitmap)
        write(playArtName(gameId)) { out ->
            check(scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, QUALITY, out)) { "cover not encoded" }
        }
        if (scaled !== bitmap) scaled.recycle()
    }

    /** Keeps each imported cover this device has none of. Returns how many it kept. */
    fun adopt(covers: Map<String, ByteArray>): Int = covers.count { (name, bytes) ->
        if (!isPlayArtName(name) || bytes.isEmpty() || File(dir, name).isFile) return@count false
        runCatching { write(name) { it.write(bytes) } }.isSuccess
    }

    private fun write(name: String, body: (FileOutputStream) -> Unit) {
        dir.mkdirs()
        val target = File(dir, name)
        val temp = File(dir, "$name.tmp")
        try {
            FileOutputStream(temp).use { out ->
                body(out)
                out.fd.sync()
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
            }
        } finally {
            temp.delete()
        }
    }

    private fun fitted(source: Bitmap): Bitmap {
        // A hardware bitmap has no pixels to read, so it is copied first.
        val bitmap = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= SIZE) return bitmap
        val scale = SIZE.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
    }

    private companion object {
        /** The grid's own tile size, so a kept cover is as sharp as a tile. */
        const val SIZE = 384
        const val QUALITY = 85
    }
}
