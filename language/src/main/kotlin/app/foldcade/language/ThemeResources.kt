package app.foldcade.language

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.*

fun themeColor(text: String): Color? {
    if (text.length != 7 || !text.startsWith('#')) return null
    val value = text.drop(1).toLongOrNull(16) ?: return null
    return Color(0xFF000000L or value)
}

enum class ThemeIconBlend { SrcOver, Screen }

data class ThemeIconAsset(
    val owner: String, val file: String, val bytes: ByteArray, val accent: Color?, val tint: Boolean,
    val blend: ThemeIconBlend = ThemeIconBlend.SrcOver,
) {
    val cacheKey: String by lazy { "$owner/$file/${themeAssetCacheKey(bytes)}" }
}

fun themeAssetCacheKey(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }

/** Duplicate IDs are rejected before materialization can write caches or create native resources. */
fun <T : Any> loadThemeCatalog(
    archives: Sequence<ThemeZip>,
    materialize: (ThemeZip) -> T?,
    report: (ThemeDiagnostic) -> Unit = {},
): List<T> {
    val seen = mutableSetOf<String>()
    val result = mutableListOf<T>()
    for (zip in archives) {
        if (zip.file.id in seen) report(ThemeDiagnostic("theme.json", "Duplicate theme id '${zip.file.id}' ignored"))
        else materialize(zip)?.let { seen += zip.file.id; result += it }
    }
    return result
}

/** Keep presentation metadata attached to the image that actually resolved, even after cache eviction. */
data class ResolvedThemeIcon<T>(val asset: ThemeIconAsset, val image: T)

fun <T : Any> resolveThemeIcon(candidates: List<ThemeIconAsset>, load: (ThemeIconAsset) -> T?): ResolvedThemeIcon<T>? {
    for (asset in candidates) load(asset)?.let { return ResolvedThemeIcon(asset, it) }
    return null
}

data class ThemeResources(
    val id: String,
    val name: String,
    val file: ThemeFile,
    val icons: Map<String, ThemeIconAsset>,
    val scenes: Map<BackgroundMotion, ThemeScene>,
    val diagnostics: List<ThemeDiagnostic>,
    val style: ThemeStyle = ThemeStyle(file.style),
) {
    val defaultBackground: BackgroundMotion get() = scenes.keys.firstOrNull {
        it == file.backgroundMotion || it.wire.substringAfter(':') == file.backgroundMotion.wire
    } ?: BackgroundMotion.Off
}

/**
 * A scene the pack asked for and did not load wears [inherited], which is
 * Afterglow's backdrop when that pack loaded. A pack that asked for no scene
 * stays off. A scene that loaded is kept.
 */
fun backdropAfterBrokenScene(
    resolved: BackgroundMotion,
    asked: BackgroundMotion,
    sceneFileCount: Int,
    inherited: BackgroundMotion?,
): BackgroundMotion {
    if (resolved != BackgroundMotion.Off) return resolved
    if (asked == BackgroundMotion.Off || sceneFileCount == 0) return BackgroundMotion.Off
    return inherited?.takeUnless { it == BackgroundMotion.Off } ?: BackgroundMotion.Off
}

/** The id of the pack every other pack inherits from, and whose icons back every composition. */
const val FALLBACK_THEME_ID: String = "afterglow"

/**
 * One pack's resources. With [inherit], Afterglow's for any other pack, colours and style tokens
 * the pack leaves out or gets wrong come from [inherit]. Icons and scenes stay the pack's own;
 * [ThemeComposition] falls back to the inherited icons slot by slot.
 */
fun resolveThemeResources(zip: ThemeZip, inherit: ThemeResources? = null): ThemeResources {
    val diagnostics = mutableListOf<ThemeDiagnostic>()
    val file = inherit?.let { zip.file.inheriting(it.file) } ?: zip.file
    val style = ThemeStyle(file.style, diagnostics::add, inheritedStyle(zip.file, inherit))
    val legacyIcons = zip.entries.filterKeys { it.startsWith("marks/") && it.endsWith(".png") }.map { (path, bytes) ->
        "mark." + path.removePrefix("marks/").removeSuffix(".png") to
            ThemeIconAsset(file.id, path, bytes, null, false, ThemeIconBlend.Screen)
    }.toMap()
    val icons = legacyIcons + file.icons.mapNotNull { (slot, icon) ->
        val bytes = zip.entries[icon.file]
        if (bytes == null) { diagnostics += ThemeDiagnostic(icon.file, "Missing icon for '$slot'"); null }
        else slot to ThemeIconAsset(file.id, icon.file, bytes, icon.accent, icon.tint)
    }.toMap()
    val scenes = linkedMapOf<BackgroundMotion, ThemeScene>()
    file.sceneFiles.forEach { path ->
        val bytes = zip.entries[path]
        if (themeZipEntryName(path) != path || bytes == null) {
            diagnostics += ThemeDiagnostic(path, "Missing scene asset")
        } else {
            val scene = parseThemeScene(bytes.decodeToString(), path, zip.entries, diagnostics::add)
            if (scene != null) {
                val key = BackgroundMotion("${file.id}:${scene.id}")
                if (key in scenes) diagnostics += ThemeDiagnostic(path, "Duplicate scene id '${scene.id}'")
                else scenes[key] = scene
            }
        }
    }
    return ThemeResources(file.id, file.name, file, icons, scenes, diagnostics, style)
}

/**
 * The style a pack inherits. A pack that names its own `focus` and leaves the ring's ends out wants
 * a ring in that colour, not Afterglow's gradient, so those two tokens are not inherited then.
 */
private fun inheritedStyle(own: ThemeFile, inherit: ThemeResources?): JsonObject {
    val base = inherit?.file?.style ?: return JsonObject(emptyMap())
    val ring = base["focusRing"] as? JsonObject ?: return base
    if ("focus" !in own.root) return base
    val ownRing = own.style["focusRing"] as? JsonObject
    val kept = ring.filterKeys { it !in setOf("start", "end") || ownRing?.containsKey(it) == true }
    return JsonObject(base + ("focusRing" to JsonObject(kept)))
}

/** Asset ownership stays with its pack. Categories never merge another pack's internal files. */
data class ThemeComposition(val base: ThemeResources?, val iconProvider: ThemeResources? = null, val fallback: ThemeResources? = null) {
    fun iconCandidates(slot: String): List<ThemeIconAsset> = listOfNotNull(
        iconProvider?.icons?.get(slot), base?.icons?.get(slot), fallback?.icons?.get(slot),
    ).distinctBy { it.cacheKey }
}

data class BackgroundChoice(val motion: BackgroundMotion, val name: String)

/** Old settings used local names. Resolve them only when they unambiguously name an installed scene. */
fun resolveBackgroundChoice(saved: BackgroundMotion, choices: List<BackgroundChoice>, fallback: BackgroundMotion): BackgroundMotion {
    if (choices.any { it.motion == saved }) return saved
    val legacy = choices.filter { it.motion.wire.substringAfter(':') == saved.wire }
    return legacy.singleOrNull()?.motion ?: fallback
}

fun rgba(red: Float, green: Float, blue: Float, alpha: Float): Color = Color(red, green, blue, alpha)
