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
import app.foldcade.language.fontZipName
import app.foldcade.language.readThemeZip
import app.foldcade.language.soundZipName
import app.foldcade.language.themeMusicTrack
import app.foldcade.language.wallpaperZipName
import app.foldcade.ui.FoldPaint
import java.io.File

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

        fun load(context: Context): PackagedTheme? {
            val zip = try {
                readThemeZip(context.assets.open(ASSET))
            } catch (error: Exception) {
                Log.w("Foldcade", "Theme zip ignored", error)
                null
            } ?: return null
            return try {
                val entries = zip.entries
                val file = zip.file
                val json = entries["theme.json"]?.decodeToString()
                val fontName = fontZipName(entries.keys)
                val theme = Theme(
                    background = file.background,
                    surface = file.surface,
                    onBackground = file.onBackground,
                    muted = file.muted,
                    focus = file.focus,
                    iconRadius = file.iconRadius,
                    artScale = file.artScale,
                    font = loadFont(context, fontName?.let { entries[it] }, fontName),
                )
                val marks = entries
                    .filterKeys { it.startsWith("marks/") && it.endsWith(".png") }
                    .mapNotNull { (path, bytes) ->
                        val bitmap = decode(bytes) ?: return@mapNotNull null
                        path.removePrefix("marks/").removeSuffix(".png") to bitmap
                    }
                    .toMap()
                PackagedTheme(
                    name = file.name,
                    paint = FoldPaint(
                        theme = theme,
                        wallpaperTop = decode(wallpaperZipName(entries.keys, top = true)?.let { entries[it] }),
                        wallpaperBottom = decode(wallpaperZipName(entries.keys, top = false)?.let { entries[it] }),
                        marks = marks,
                    ),
                    sounds = ThemeSounds(context, entries),
                    backgroundMotion = file.backgroundMotion,
                    music = themeMusic(context, file.name, json, entries),
                )
            } catch (error: Exception) {
                Log.w("Foldcade", "Theme zip ignored", error)
                null
            }
        }

        private fun loadFont(context: Context, bytes: ByteArray?, name: String?): FontFamily {
            if (bytes == null || name == null) return FontFamily.SansSerif
            return try {
                val ext = name.substringAfterLast('.', "ttf")
                val file = File(context.cacheDir, "afterglow-font.$ext")
                file.writeBytes(bytes)
                FontFamily(Font(file))
            } catch (error: Exception) {
                Log.w("Foldcade", "Theme font ignored", error)
                FontFamily.SansSerif
            }
        }

        private fun themeMusic(
            context: Context,
            name: String,
            json: String?,
            entries: Map<String, ByteArray>,
        ): MusicTrack? = try {
            themeMusicFile(context, name, json, entries)
        } catch (error: Exception) {
            Log.w("Foldcade", "Theme music ignored", error)
            null
        }

        private fun themeMusicFile(
            context: Context,
            name: String,
            json: String?,
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
            return try {
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                bitmap.asImageBitmap()
            } catch (error: Exception) {
                Log.w("Foldcade", "Theme image ignored", error)
                null
            }
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
            val bytes = entries[soundZipName(slot)] ?: return@forEach
            try {
                val file = File(context.cacheDir, "afterglow-$slot.ogg")
                file.writeBytes(bytes)
                val id = pool.load(file.absolutePath, 1)
                if (id != 0) ids[slot] = id
            } catch (error: Exception) {
                Log.w("Foldcade", "Theme sound ignored", error)
            }
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
