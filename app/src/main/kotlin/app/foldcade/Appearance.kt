package app.foldcade

import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.MusicTrack
import app.foldcade.language.Theme
import app.foldcade.language.parseThemeJson
import app.foldcade.language.themeMusicTrack
import app.foldcade.ui.FoldPaint
import java.io.File
import java.util.zip.ZipInputStream

/** The reference theme zip, selected unless the user cycles back to the built-in theme. */
class PackagedTheme(
    val name: String,
    val paint: FoldPaint,
    val sounds: ThemeSounds,
    val backgroundMotion: BackgroundMotion,
    val music: MusicTrack?,
) {
    companion object {
        private const val ASSET = "themes/afterglow.zip"

        fun load(context: Context): PackagedTheme? = try {
            val entries = readZip(context)
            val json = entries["theme.json"]?.decodeToString() ?: return null
            val file = parseThemeJson(json) ?: return null
            val font = loadFont(context, entries["font.ttf"])
            val theme = Theme(
                background = file.background,
                surface = file.surface,
                onBackground = file.onBackground,
                muted = file.muted,
                focus = file.focus,
                iconRadius = file.iconRadius,
                artScale = file.artScale,
                font = font,
            )
            val marks = entries
                .filterKeys { it.startsWith("marks/") && it.endsWith(".png") }
                .mapNotNull { (path, bytes) ->
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@mapNotNull null
                    path.removePrefix("marks/").removeSuffix(".png") to bitmap.asImageBitmap()
                }
                .toMap()
            PackagedTheme(
                name = file.name,
                paint = FoldPaint(
                    theme = theme,
                    wallpaperTop = decode(entries["wallpaper-top.png"]),
                    wallpaperBottom = decode(entries["wallpaper-bottom.png"]),
                    marks = marks,
                ),
                sounds = ThemeSounds(context, entries),
                backgroundMotion = file.backgroundMotion,
                music = themeMusicFile(context, file.name, json, entries),
            )
        } catch (error: Exception) {
            Log.w("Foldcade", "Theme zip ignored", error)
            null
        }

        private fun readZip(context: Context): Map<String, ByteArray> {
            val entries = linkedMapOf<String, ByteArray>()
            ZipInputStream(context.assets.open(ASSET)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                    zip.closeEntry()
                }
            }
            return entries
        }

        private fun loadFont(context: Context, bytes: ByteArray?): FontFamily {
            if (bytes == null) return FontFamily.SansSerif
            val file = File(context.cacheDir, "afterglow-font.ttf")
            file.writeBytes(bytes)
            return FontFamily(Font(file))
        }

        private fun themeMusicFile(
            context: Context,
            name: String,
            json: String,
            entries: Map<String, ByteArray>,
        ): MusicTrack? {
            val described = themeMusicTrack(name, json, entries.keys) ?: return null
            val bytes = entries[described.file] ?: return null
            if (bytes.isEmpty()) return null
            val safe = described.file.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val out = File(context.cacheDir, "theme-music-$safe")
            out.writeBytes(bytes)
            return described.copy(file = out.absolutePath)
        }

        private fun decode(bytes: ByteArray?): androidx.compose.ui.graphics.ImageBitmap? {
            if (bytes == null) return null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            return bitmap.asImageBitmap()
        }
    }
}

/** The four theme sound slots. A missing clip is skipped. */
class ThemeSounds(context: Context, entries: Map<String, ByteArray>) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = mutableMapOf<String, Int>()

    init {
        listOf("move", "activate", "back", "notify").forEach { slot ->
            val bytes = entries["sounds/$slot.ogg"] ?: return@forEach
            val file = File(context.cacheDir, "afterglow-$slot.ogg")
            file.writeBytes(bytes)
            ids[slot] = pool.load(file.absolutePath, 1)
        }
    }

    /** True when this theme has [slot] and the tick was started. */
    fun play(slot: String): Boolean {
        val id = ids[slot] ?: return false
        if (id == 0) return false
        pool.play(id, 0.4f, 0.4f, 1, 0, 1f)
        return true
    }
}
