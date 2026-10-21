package app.foldcade

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.RuntimeShader
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.foldcade.language.backdropAfterBrokenScene
import app.foldcade.language.BackgroundMotion
import app.foldcade.language.FALLBACK_THEME_ID
import app.foldcade.language.MusicTrack
import app.foldcade.language.Theme
import app.foldcade.language.ThemeContract
import app.foldcade.language.ThemeDiagnostic
import app.foldcade.language.ThemeFile
import app.foldcade.language.SceneLayer
import app.foldcade.language.ThemeResources
import app.foldcade.language.resolveThemeResources
import app.foldcade.language.loadThemeCatalog
import app.foldcade.language.openThemeArchive
import app.foldcade.language.orderByExtends
import app.foldcade.language.themeAssetCacheKey
import app.foldcade.language.ThemeZip
import java.io.InputStream
import app.foldcade.language.fontOrInherited
import app.foldcade.language.fontZipName
import app.foldcade.language.readThemeZip
import app.foldcade.language.soundZipName
import app.foldcade.language.themeMusicTrack
import app.foldcade.language.wallpaperZipName
import app.foldcade.ui.FoldPaint
import app.foldcade.ui.ThemeWallpapers
import java.io.File

/**
 * One loaded theme zip. Afterglow, the reference pack, loads first, and every other pack inherits
 * from it, or from the pack it `extends`: colours, style tokens, font and sounds the pack leaves
 * out are its parent's. An installed pack is read in place, so its wallpapers, sounds and music
 * are read only once something shows or plays them.
 */
class PackagedTheme(
    val name: String,
    val paint: FoldPaint,
    val sounds: ThemeSounds,
    val backgroundMotion: BackgroundMotion,
    /** The pack's loop, where [prepareMusic] writes it for the player. */
    val music: MusicTrack?,
    val resources: ThemeResources,
    /** The archive's files, read as they are asked for. */
    internal val entries: Map<String, ByteArray> = emptyMap(),
    /** Its archive's name in files/themes. Null for a pack Foldcade ships. */
    val archive: String? = null,
    /** The packs this one builds on, nearest first, Afterglow left out. */
    val parents: List<PackagedTheme> = emptyList(),
    private val musicBytes: () -> ByteArray? = { null },
) {
    /**
     * Writes the pack's loop where the player reads it, the first time it is wanted, and returns
     * it. Off the main thread: a loop can be megabytes.
     */
    fun prepareMusic(): MusicTrack? {
        val track = music ?: return null
        val out = File(track.file)
        if (out.isFile) return track
        val bytes = musicBytes()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            val part = File(out.parentFile, out.name + ".part")
            part.writeBytes(bytes)
            if (!part.renameTo(out)) part.delete()
            track.takeIf { out.isFile }
        } catch (error: Exception) {
            Log.w("Foldcade", "Theme music ignored", error)
            null
        }
    }

    companion object {
        private const val ASSET = "themes/afterglow.zip"

        fun load(context: Context): PackagedTheme? = try {
            load(context, context.assets.open(ASSET))
        } catch (error: Exception) { Log.w("FoldcadeTheme", "Default theme ignored", error); null }

        /** Every pack, Afterglow first. [read] hears the manifest of each archive in `files/themes` that parsed. */
        fun loadAll(context: Context, read: (archive: String, manifest: ThemeFile) -> Unit = { _, _ -> }): List<PackagedTheme> {
            // The shipped packs have no archive name; those in files/themes are named for the ledger.
            val shipped = context.assets.list("themes").orEmpty().filter { it.endsWith(".zip") }.sorted().mapNotNull { name ->
                try { context.assets.open("themes/$name").use { readThemeZip(it, shippedKey(context, name)) } }
                catch (error: Exception) { Log.w("FoldcadeTheme", "Theme themes/$name ignored", error); null }
            }
            val archiveOf = java.util.IdentityHashMap<ThemeZip, String>()
            // Installed packs are read in place: only their manifests now.
            val installed = File(context.filesDir, "themes").listFiles().orEmpty().filter { it.extension == "zip" }.sortedBy { it.name }.mapNotNull { file ->
                try {
                    openThemeArchive(file)?.also { zip -> archiveOf[zip] = file.name; read(file.name, zip.file) }
                        ?: null.also { Log.w("FoldcadeTheme", "Theme ${file.name} ignored") }
                } catch (error: Exception) { Log.w("FoldcadeTheme", "Theme ${file.name} ignored", error); null }
            }
            // Afterglow first, then each pack after the one it extends.
            val zips = shipped.sortedBy { it.file.id != FALLBACK_THEME_ID } + installed
            val notes = java.util.IdentityHashMap<ThemeZip, MutableList<ThemeDiagnostic>>()
            val order = orderByExtends(zips) { zip, note -> notes.getOrPut(zip) { mutableListOf() } += note }
            val parentOf = java.util.IdentityHashMap<ThemeZip, String?>().apply { order.forEach { (zip, parent) -> put(zip, parent) } }
            val loaded = mutableMapOf<String, PackagedTheme>()
            return loadThemeCatalog(order.asSequence().map { it.first }, { zip ->
                val fallback = loaded[FALLBACK_THEME_ID]
                val parent = parentOf[zip]?.let(loaded::get)
                load(context, zip, (parent ?: fallback).takeIf { zip.file.id != FALLBACK_THEME_ID }, archiveOf[zip], notes[zip].orEmpty())
                    ?.also { loaded.putIfAbsent(it.resources.id, it) }
            }) { diagnostic ->
                Log.w("FoldcadeTheme", "${diagnostic.source}: ${diagnostic.message}")
            }.also { packs -> zips.filter { zip -> packs.none { it.entries === zip.entries } }.forEach(ThemeZip::close) }
        }

        /** One zip, inheriting from [inherit] as [loadAll] has every pack but Afterglow inherit. */
        fun load(context: Context, input: InputStream, inherit: PackagedTheme? = null): PackagedTheme? {
            val zip = input.use { readThemeZip(it) } ?: return null
            return load(context, zip, inherit?.takeIf { zip.file.id != it.resources.id })
        }

        /** Names a shipped archive's files by the build that shipped them, so extracted files are found again. */
        private fun shippedKey(context: Context, name: String): String {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            return "asset:$name:${info.longVersionCode}:${info.lastUpdateTime}"
        }

        private fun load(
            context: Context,
            zip: ThemeZip,
            inherit: PackagedTheme? = null,
            archive: String? = null,
            notes: List<ThemeDiagnostic> = emptyList(),
        ): PackagedTheme? = try {
            val entries = zip.entries
            val resolved = resolveThemeResources(zip, inherit?.resources)
            val resources = if (notes.isEmpty()) resolved else resolved.copy(diagnostics = notes + resolved.diagnostics)
            val file = resources.file
            resources.diagnostics.forEach { Log.w("FoldcadeTheme", "${file.id}/${it.source}: ${it.message}") }
            val json = entries["theme.json"]?.decodeToString()
            val parents = inherit?.takeIf { it.resources.id in resources.parents }?.let { listOf(it) + it.parents }.orEmpty()
            val style = resources.style
            // A pack without faces of its own wears its parent's.
            val regular = file.fonts["regular"] ?: fontZipName(entries.keys)
            val inheritedFont = inherit?.paint?.theme
            val font = fontOrInherited(
                regular?.let { loadFont(context, entries[it], it, file.fonts["bold"]?.let { bold -> entries[bold]?.let { bold to it } }, style.type.bodyWeight, style.type.strongWeight) },
                inheritedFont?.font,
            )
            // A display face of the parent's own carries over; one the parent only inherited as its font does not.
            val display = file.fonts["display"]?.let { loadFont(context, entries[it], it, null, style.type.bodyWeight, style.type.strongWeight) }
                ?: inheritedFont?.displayFont?.takeIf { regular == null && it !== inheritedFont.font }
                ?: font
            val theme = Theme(
                background = file.background,
                surface = file.surface,
                onBackground = file.onBackground,
                muted = file.muted,
                focus = file.focus,
                iconRadius = file.iconRadius,
                artScale = file.artScale,
                font = font,
                style = style,
                displayFont = display,
            )
            val described = themeMusicTrack(file.name, json, entries.keys)
            val musicFile = described?.let { track ->
                val safe = track.file.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val name = if (zip.key.isEmpty()) entries[track.file]?.let(::themeAssetCacheKey) else themeAssetCacheKey("${zip.key}/${track.file}".encodeToByteArray())
                name?.let { File(context.cacheDir, "theme-$it-music-$safe") }
            }
            PackagedTheme(
                name = file.name,
                paint = FoldPaint(
                    theme = theme,
                    wallpapers = ThemeWallpapers { top -> decode(wallpaperZipName(entries.keys, top)?.let { entries[it] }) },
                    icons = resources.icons.mapValues { (_, icon) -> listOf(icon) },
                ),
                sounds = ThemeSounds(context, listOf(entries) + (inherit?.sounds?.layers.orEmpty()), style.sound.volume),
                backgroundMotion = backdropAfterBrokenScene(
                    resources.defaultBackground,
                    resources.file.backgroundMotion,
                    resources.file.sceneFiles.size,
                    inherit?.resources?.defaultBackground,
                ),
                music = described?.takeIf { musicFile != null }?.copy(file = musicFile!!.absolutePath),
                resources = resources,
                entries = entries,
                archive = archive,
                parents = parents,
                musicBytes = { described?.let { entries[it.file] } },
            )
        } catch (error: Exception) {
            Log.w("Foldcade", "Theme zip ignored", error)
            null
        }

        /**
         * [name]'s face, with [bold] as its strong weight when the pack has one. A variable face
         * (one with a `wght` axis) is set to [body] and [strong] itself, so it needs no faux bold.
         */
        private fun loadFont(
            context: Context,
            bytes: ByteArray?,
            name: String,
            bold: Pair<String, ByteArray>?,
            body: FontWeight,
            strong: FontWeight,
        ): FontFamily? {
            if (bytes == null) return null
            return try {
                val regular = cachedFont(context, bytes, name)
                val faces = if (variableWeight(bytes)) {
                    listOf(body, strong).distinct().map { weight -> Font(regular, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))) }
                } else {
                    listOf(Font(regular, body)) + listOfNotNull(bold?.let { (boldName, boldBytes) -> Font(cachedFont(context, boldBytes, boldName), strong) })
                }
                FontFamily(faces)
            } catch (error: Exception) {
                Log.w("Foldcade", "Theme font ignored", error)
                null
            }
        }

        private fun cachedFont(context: Context, bytes: ByteArray, name: String): File {
            val ext = name.substringAfterLast('.', "ttf")
            val file = File(context.cacheDir, "theme-${themeAssetCacheKey(bytes)}-font.$ext")
            if (!file.isFile || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
            return file
        }

        private fun decode(bytes: ByteArray?): ImageBitmap? {
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

/** True when the OpenType face in [bytes] has a `wght` variation axis. */
internal fun variableWeight(bytes: ByteArray): Boolean {
    fun u16(at: Int) = if (at + 2 <= bytes.size) ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF) else -1
    fun u32(at: Int) = if (at + 4 <= bytes.size) (u16(at).toLong() shl 16) or u16(at + 2).toLong() else -1L
    val tables = u16(4)
    if (tables <= 0) return false
    for (index in 0 until tables) {
        val record = 12 + index * 16
        if (record + 16 > bytes.size) return false
        if (String(bytes, record, 4, Charsets.ISO_8859_1) != "fvar") continue
        val offset = u32(record + 8).takeIf { it in 0 until bytes.size }?.toInt() ?: return false
        val axesAt = offset + u16(offset + 4)
        val axes = u16(offset + 8)
        val axisSize = u16(offset + 10)
        if (axes <= 0 || axisSize < 4) return false
        return (0 until axes).any { axis ->
            val at = axesAt + axis * axisSize
            at + 4 <= bytes.size && String(bytes, at, 4, Charsets.ISO_8859_1) == "wght"
        }
    }
    return false
}

/**
 * True when [zip] is whole enough to replace a pack: every icon and scene it names resolves over
 * [inherit], and every shader in its scenes compiles on this device.
 */
internal fun themeInstallable(zip: ThemeZip, inherit: ThemeResources?): Boolean = try {
    val resources = resolveThemeResources(zip, inherit)
    resources.file.icons.keys.all { it in resources.icons } &&
        resources.scenes.size == resources.file.sceneFiles.size &&
        resources.scenes.values.all { scene ->
            scene.layers.filterIsInstance<SceneLayer.Shader>().all { layer ->
                try { RuntimeShader(layer.source); true }
                catch (error: IllegalArgumentException) { Log.w("FoldcadeTheme", "${scene.source}:${layer.name}", error); false }
            }
        }
} catch (error: Exception) {
    Log.w("FoldcadeTheme", "Theme ${zip.file.id} cannot install", error)
    false
}

/**
 * A pack's sounds, by [ThemeContract.soundCues] name, played at [volume]. [layers] are the pack's
 * files, then its parent's, ending with Afterglow's: a cue plays the first clip of its chain found
 * in the pack itself before looking in a parent, so a pack that only ships `activate` hears it
 * wherever `open` plays. Nothing is read until [warm] or the first cue.
 */
class ThemeSounds(private val context: Context, internal val layers: List<Map<String, ByteArray>>, private val volume: Float) {
    /** Each cue's clip, as its file in [layers]. A cue that falls through to silence has none. */
    private val clips: Map<String, Pair<Int, String>> = ThemeContract.soundCues.keys.mapNotNull { cue ->
        layers.withIndex().firstNotNullOfOrNull { (layer, files) ->
            ThemeContract.soundChain(cue).firstOrNull { soundZipName(it) in files.keys }?.let { cue to (layer to it) }
        }
    }.toMap()

    private var pool: SoundPool? = null
    private val ids = mutableMapOf<Pair<Int, String>, Int>()

    /** Loads the clips now, so the first cue is not lost while they decode. */
    @Synchronized
    fun warm() {
        if (pool != null) return
        val made = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        pool = made
        clips.values.distinct().forEach { clip ->
            val (layer, slot) = clip
            val bytes = layers[layer][soundZipName(slot)] ?: return@forEach
            try {
                val file = File(context.cacheDir, "theme-${themeAssetCacheKey(bytes)}-$slot.ogg")
                if (!file.isFile || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
                val id = made.load(file.absolutePath, 1)
                if (id != 0) ids[clip] = id
            } catch (error: Exception) {
                Log.w("Foldcade", "Theme sound ignored", error)
            }
        }
    }

    /** True when [slot] is heard: a clip in its chain is there and the theme's volume is above zero. */
    fun has(slot: String): Boolean = volume > 0f && slot in clips

    /** True when this theme has a clip for [slot] and the tick was started. */
    @Synchronized
    fun play(slot: String): Boolean {
        val clip = clips[slot] ?: return false
        warm()
        val id = ids[clip] ?: return false
        pool?.play(id, volume, volume, 1, 0, 1f)
        return true
    }
}
