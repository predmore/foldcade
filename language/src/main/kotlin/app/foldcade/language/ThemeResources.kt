package app.foldcade.language

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.json.*

fun themeColor(text: String): Color? {
    if (text.length != 7 || !text.startsWith('#')) return null
    val value = text.drop(1).toLongOrNull(16) ?: return null
    return Color(0xFF000000L or value)
}

/** [color] as a manifest writes it, `#RRGGBB`. */
fun themeColorText(color: Color): String = "#%06X".format(color.toArgb() and 0xFFFFFF)

enum class ThemeIconBlend { SrcOver, Screen }

/**
 * One icon file and how it is drawn. Its bytes are read from the archive each time [bytes] is
 * asked for, so a pack's icons are not held in memory before they are drawn. [archive] names the
 * copy of the archive they come from; without one the bytes themselves name the icon.
 */
class ThemeIconAsset(
    val owner: String,
    val file: String,
    private val read: () -> ByteArray?,
    val accent: Color?,
    val tint: Boolean,
    val blend: ThemeIconBlend = ThemeIconBlend.SrcOver,
    private val archive: String = "",
) {
    constructor(owner: String, file: String, bytes: ByteArray, accent: Color?, tint: Boolean, blend: ThemeIconBlend = ThemeIconBlend.SrcOver) :
        this(owner, file, { bytes }, accent, tint, blend)

    /** The file's bytes, or none when the archive no longer has them. */
    val bytes: ByteArray get() = read() ?: ByteArray(0)

    val cacheKey: String by lazy { if (archive.isNotEmpty()) "$owner/$file/$archive" else "$owner/$file/${themeAssetCacheKey(bytes)}" }

    fun copy(
        owner: String = this.owner,
        accent: Color? = this.accent,
        tint: Boolean = this.tint,
        blend: ThemeIconBlend = this.blend,
    ): ThemeIconAsset = ThemeIconAsset(owner, file, read, accent, tint, blend, archive)
}

fun themeAssetCacheKey(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * [zips] in an order that loads each pack after the one it `extends`, with the id each builds on;
 * null is Afterglow. A pack whose parent is not installed, or whose chain loops back to it, builds
 * on Afterglow instead, and [report] says why. The first copy of an id is the one built on, as
 * [loadThemeCatalog] keeps it.
 */
fun orderByExtends(zips: List<ThemeZip>, report: (ThemeZip, ThemeDiagnostic) -> Unit = { _, _ -> }): List<Pair<ThemeZip, String?>> {
    val byId = linkedMapOf<String, ThemeZip>()
    zips.forEach { byId.putIfAbsent(it.file.id, it) }
    val ordered = mutableListOf<Pair<ThemeZip, String?>>()
    val done = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<ThemeZip, Boolean>())
    val walking = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<ThemeZip, Boolean>())
    fun visit(zip: ThemeZip) {
        if (zip in done) return
        walking += zip
        val wanted = zip.file.extends?.takeIf { it != FALLBACK_THEME_ID }
        val parent = wanted?.let(byId::get)
        val builtOn = when {
            wanted == null -> null
            parent == null -> null.also { report(zip, ThemeDiagnostic("theme.json", "extends '$wanted', which is not installed; building on Afterglow")) }
            parent in walking -> null.also { report(zip, ThemeDiagnostic("theme.json", "extends '$wanted', which leads back to this pack; building on Afterglow")) }
            else -> wanted.also { visit(parent) }
        }
        walking -= zip
        done += zip
        ordered += zip to builtOn
    }
    zips.forEach(::visit)
    return ordered
}

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
    /** Every slot name Afterglow fills, which the host's closed slot families are checked against. */
    val slotNames: Set<String> = icons.keys,
    /** The packs this one builds on, nearest first, ending before Afterglow. */
    val parents: List<String> = emptyList(),
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
 * [ThemeComposition] falls back to the inherited icons slot by slot. A pack that `extends` another
 * has that pack as [inherit], and wears its icons where it has none of its own.
 */
fun resolveThemeResources(zip: ThemeZip, inherit: ThemeResources? = null): ThemeResources {
    val diagnostics = mutableListOf<ThemeDiagnostic>()
    val file = inherit?.let { zip.file.inheriting(it.file) } ?: zip.file
    diagnostics += themeReleaseDiagnostics(file)
    diagnostics += themeAboutDiagnostics(file, zip.entries.keys)
    val style = ThemeStyle(file.style, diagnostics::add, inheritedStyle(zip.file, inherit))
    fun asset(path: String, accent: Color?, tint: Boolean, blend: ThemeIconBlend) =
        if (zip.key.isEmpty()) ThemeIconAsset(file.id, path, zip.entries[path] ?: ByteArray(0), accent, tint, blend)
        else ThemeIconAsset(file.id, path, { zip.entries[path] }, accent, tint, blend, zip.key)
    val legacyIcons = zip.entries.keys.filter { it.startsWith("marks/") && it.endsWith(".png") }.associate { path ->
        "mark." + path.removePrefix("marks/").removeSuffix(".png") to asset(path, null, false, ThemeIconBlend.Screen)
    }
    val own = legacyIcons + file.icons.mapNotNull { (slot, icon) ->
        if (icon.file !in zip.entries.keys) { diagnostics += ThemeDiagnostic(icon.file, "Missing icon for '$slot'"); null }
        else slot to asset(icon.file, icon.accent, icon.tint, ThemeIconBlend.SrcOver)
    }.toMap()
    val known = inherit?.slotNames
    if (known != null) file.icons.keys.filter { slot ->
        slot.substringBefore('.') in ThemeContract.closedSlotFamilies && slot !in known
    }.forEach { slot -> diagnostics += ThemeDiagnostic("theme.json:icons.$slot", "Unknown icon slot") }
    file.icons.keys.filter { it in ThemeContract.retiredSlots }
        .forEach { slot -> diagnostics += ThemeDiagnostic("theme.json:icons.$slot", "Retired; the host no longer draws this slot") }
    (zip.file.root["icons"] as? JsonObject)?.keys?.mapNotNull { old -> ThemeContract.renamedSlots[old]?.let { old to it } }
        ?.forEach { (old, new) -> diagnostics += ThemeDiagnostic("theme.json:icons.$old", "Renamed to $new; this name still works") }
    // Building on Afterglow by name is what every pack does anyway; its icons stay a fallback.
    val extended = inherit?.takeIf { file.extends != null && it.id == file.extends && it.id != FALLBACK_THEME_ID }
    val icons = (extended?.icons.orEmpty()) + own
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
    return ThemeResources(
        file.id, file.name, file, icons, scenes, diagnostics, style,
        slotNames = known ?: icons.keys,
        parents = extended?.let { listOf(it.id) + it.parents }.orEmpty(),
    )
}

/** What a pack's description of itself names that its archive does not have. */
private fun themeAboutDiagnostics(file: ThemeFile, names: Set<String>): List<ThemeDiagnostic> = buildList {
    if ("extends" in file.root && file.extends == null) add(ThemeDiagnostic("theme.json", "extends must name another pack's id"))
    if ("preview" in file.root && (file.about.preview == null || file.about.preview !in names)) add(ThemeDiagnostic("theme.json", "Missing preview image"))
    (file.root["fonts"] as? JsonObject)?.keys?.forEach { face ->
        val path = file.fonts[face]
        when {
            face !in ThemeContract.fontFaces -> add(ThemeDiagnostic("theme.json:fonts.$face", "Unknown font face"))
            path == null -> add(ThemeDiagnostic("theme.json:fonts.$face", "A font face is an archive path"))
            path !in names -> add(ThemeDiagnostic(path, "Missing font for '$face'"))
        }
    }
}

/**
 * The style a pack inherits. A pack that names its own `focus` and leaves the ring's ends out wants
 * a ring in that colour, not Afterglow's gradient, so those two tokens are not inherited then.
 */
private fun inheritedStyle(own: ThemeFile, inherit: ThemeResources?): JsonObject {
    val base = inherit?.style?.resolvedJson() ?: return JsonObject(emptyMap())
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
