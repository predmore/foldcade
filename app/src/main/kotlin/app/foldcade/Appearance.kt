package app.foldcade

import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.foldcade.language.backdropAfterBrokenScene
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.FALLBACK_THEME_ID
import app.foldcade.language.MusicTrack
import app.foldcade.language.Theme
import app.foldcade.language.ThemeResources
import app.foldcade.language.resolveThemeResources
import app.foldcade.language.loadThemeCatalog
import app.foldcade.language.themeAssetCacheKey
import app.foldcade.language.ThemeZip
import java.io.InputStream
import app.foldcade.language.fontZipName
import app.foldcade.language.readThemeZip
import app.foldcade.language.soundZipName
import app.foldcade.language.themeMusicTrack
import app.foldcade.language.wallpaperZipName
import app.foldcade.ui.FoldPaint
import java.io.File

/**
 * One loaded theme zip. Afterglow, the reference pack, loads first, and every other pack inherits
 * from it: colours, style tokens, font and sounds the pack leaves out are Afterglow's.
 */
class PackagedTheme(
    val name: String,
    val paint: FoldPaint,
    val sounds: ThemeSounds,
    val backgroundMotion: BackgroundMotion,
    val music: MusicTrack?,
    val resources: ThemeResources,
    /** The archive's files, so a pack loaded after this one can inherit them. */
    internal val entries: Map<String, ByteArray> = emptyMap(),
) {
    companion object {
        private const val ASSET = "themes/afterglow.zip"

        fun load(context: Context): PackagedTheme? = try {
            load(context, context.assets.open(ASSET))
        } catch (error: Exception) { Log.w("FoldcadeTheme", "Default theme ignored", error); null }

        fun loadAll(context: Context): List<PackagedTheme> {
            val sources: List<Pair<String, () -> InputStream>> = context.assets.list("themes").orEmpty().filter { it.endsWith(".zip") }.sorted()
                .map { name -> "themes/$name" to { context.assets.open("themes/$name") } } +
                File(context.filesDir, "themes").listFiles().orEmpty().filter { it.extension == "zip" }.sortedBy { it.name }
                    .map { file -> file.name to { file.inputStream() } }
            val archives = sources.asSequence().mapNotNull { (source, open) ->
                try { open().use(::readThemeZip) }
                catch (error: Exception) { Log.w("FoldcadeTheme", "Theme $source ignored", error); null }
            }
            var fallback: PackagedTheme? = null
            return loadThemeCatalog(archives, { zip ->
                load(context, zip, fallback)?.also { if (fallback == null && it.resources.id == FALLBACK_THEME_ID) fallback = it }
            }) { diagnostic ->
                Log.w("FoldcadeTheme", "${diagnostic.source}: ${diagnostic.message}")
            }
        }

        /** One zip, inheriting from [inherit] as [loadAll] has every pack but Afterglow inherit. */
        fun load(context: Context, input: InputStream, inherit: PackagedTheme? = null): PackagedTheme? {
            val zip = input.use { readThemeZip(it) } ?: return null
            return load(context, zip, inherit?.takeIf { zip.file.id != it.resources.id })
        }

        private fun load(context: Context, zip: ThemeZip, inherit: PackagedTheme? = null): PackagedTheme? = try {
            val entries = zip.entries
            val resources = resolveThemeResources(zip, inherit?.resources)
            val file = resources.file
            resources.diagnostics.forEach { Log.w("FoldcadeTheme", "${file.id}/${it.source}: ${it.message}") }
            val json = entries["theme.json"]?.decodeToString()
            // A pack without its own font or sounds wears Afterglow's.
            val fontEntries = if (fontZipName(entries.keys) != null) entries else inherit?.entries.orEmpty()
            val fontName = fontZipName(fontEntries.keys)
            val theme = Theme(
                background = file.background,
                surface = file.surface,
                onBackground = file.onBackground,
                muted = file.muted,
                focus = file.focus,
                iconRadius = file.iconRadius,
                artScale = file.artScale,
                font = loadFont(context, fontName?.let { fontEntries[it] }, fontName),
                style = resources.style,
            )
            PackagedTheme(
                name = file.name,
                paint = FoldPaint(
                    theme = theme,
                    wallpaperTop = decode(wallpaperZipName(entries.keys, top = true)?.let { entries[it] }),
                    wallpaperBottom = decode(wallpaperZipName(entries.keys, top = false)?.let { entries[it] }),
                    icons = resources.icons.mapValues { (_, icon) -> listOf(icon) },
                ),
                sounds = ThemeSounds(context, entries + inherit?.entries.orEmpty().filterKeys { it.startsWith("sounds/") && it !in entries }, resources.style.sound.volume),
                backgroundMotion = backdropAfterBrokenScene(
                    resources.defaultBackground,
                    resources.file.backgroundMotion,
                    resources.file.sceneFiles.size,
                    inherit?.resources?.defaultBackground,
                ),
                music = themeMusic(context, file.name, json, entries),
                resources = resources,
                entries = entries,
            )
        } catch (error: Exception) {
            Log.w("Foldcade", "Theme zip ignored", error)
            null
        }


        private fun loadFont(context: Context, bytes: ByteArray?, name: String?): FontFamily {
            if (bytes == null || name == null) return FontFamily.SansSerif
            return try {
                val ext = name.substringAfterLast('.', "ttf")
                val file = File(context.cacheDir, "theme-${themeAssetCacheKey(bytes)}-font.$ext")
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
            val out = File(context.cacheDir, "theme-${themeAssetCacheKey(bytes)}-music-$safe")
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

/** The four theme sound slots, played at [volume]. A missing clip is skipped. */
class ThemeSounds(context: Context, entries: Map<String, ByteArray>, private val volume: Float) {
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
                val file = File(context.cacheDir, "theme-${themeAssetCacheKey(bytes)}-$slot.ogg")
                file.writeBytes(bytes)
                val id = pool.load(file.absolutePath, 1)
                if (id != 0) ids[slot] = id
            } catch (error: Exception) {
                Log.w("Foldcade", "Theme sound ignored", error)
            }
        }
    }

    /** True when [slot] is heard: the clip loaded and the theme's volume is above zero. */
    fun has(slot: String): Boolean = volume > 0f && (ids[slot] ?: 0) != 0

    /** True when this theme has [slot] and the tick was started. */
    fun play(slot: String): Boolean {
        val id = ids[slot] ?: return false
        if (id == 0) return false
        pool.play(id, volume, volume, 1, 0, 1f)
        return true
    }
}
