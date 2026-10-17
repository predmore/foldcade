package app.foldcade.language

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Afterglow is every pack's fallback, so it has to exercise the whole style API. */
class ThemeInheritanceTest {
    private val afterglow by lazy { resolveThemeResources(readThemeZip(afterglowZip().inputStream())!!) }

    @Test
    fun afterglowDeclaresEveryTokenTheEngineReads() {
        assertEquals(emptyList<ThemeDiagnostic>(), afterglow.diagnostics)
        assertEquals(emptySet<String>(), afterglow.style.defaulted)
        assertTrue(afterglow.style.tokens.size > 150)
    }

    @Test
    fun afterglowDiffersFromTheEngineDefaultsOnlyWhereItMeansTo() {
        val engine = ThemeStyle().values
        val differing = afterglow.style.values.filter { (token, value) -> engine[token] != value }.keys
        // The built-in theme has no ring gradient and gives resting marks a faint glow.
        assertEquals(setOf("focusRing.start", "focusRing.end", "glow.restPeak"), differing)
    }

    @Test
    fun aPackInheritsWhatItLeavesOutFromAfterglow() {
        val file = parseThemeJson(
            """{"version":2,"id":"dusk","name":"Dusk","background":"#101018",
                "style":{"shape":{"round":0},"menu":{"scrimAlpha":2},"colors":{"scrim":"#203040"},"typo":{"hint":12}}}""",
        )!!
        val dusk = resolveThemeResources(ThemeZip(file, emptyMap()), afterglow)
        val pack = dusk.style
        assertEquals(Color(0xFF101018), dusk.file.background)
        assertEquals(afterglow.file.focus, dusk.file.focus)
        assertEquals(afterglow.file.iconRadius, dusk.file.iconRadius)
        assertEquals(0f, pack.shape.round)
        assertEquals(Color(0xFF203040), pack.colors.scrim)
        // An invalid token falls back to Afterglow's value, not the engine's, and is reported.
        assertEquals(afterglow.style.menu.scrimAlpha, pack.menu.scrimAlpha)
        assertEquals(afterglow.style.glow.restPeak, pack.glow.restPeak)
        assertEquals(afterglow.style.collections.color(CollectionTint.Teal), pack.collections.color(CollectionTint.Teal))
        assertEquals(FontWeight.SemiBold, pack.type.strongWeight)
        assertEquals(emptySet<String>(), pack.defaulted)
        assertEquals(setOf("theme.json:style.menu.scrimAlpha", "theme.json:style.typo"), dusk.diagnostics.map { it.source }.toSet())
    }

    @Test
    fun aPackWithItsOwnFocusColourGetsARingInIt() {
        fun pack(json: String) = resolveThemeResources(ThemeZip(parseThemeJson(json)!!, emptyMap()), afterglow).style.focusRing
        val red = pack("""{"version":2,"id":"red","name":"Red","focus":"#FF2020"}""")
        assertEquals(null, red.start)
        assertEquals(null, red.end)
        val half = pack("""{"version":2,"id":"half","name":"Half","focus":"#FF2020","style":{"focusRing":{"start":"#102030"}}}""")
        assertEquals(Color(0xFF102030), half.start)
        assertEquals(null, half.end)
        val plain = pack("""{"version":2,"id":"plain","name":"Plain"}""")
        assertEquals(afterglow.style.focusRing.start, plain.start)
        assertEquals(afterglow.style.focusRing.end, plain.end)
    }

    @Test
    fun withoutAfterglowAPackKeepsTheEngineDefaults() {
        val file = parseThemeJson("""{"name":"Alone","style":{"shape":{"round":0.25}}}""")!!
        val alone = resolveThemeResources(ThemeZip(file, emptyMap()))
        assertEquals(.25f, alone.style.shape.round)
        assertEquals(builtInTheme().focus, alone.file.focus)
        assertEquals(.22f, alone.style.glow.restPeak)
        assertTrue("shape.round" !in alone.style.defaulted)
        assertTrue("glow.restPeak" in alone.style.defaulted)
    }

    @Test
    fun collectionColoursAreTheThemesAndKeepTheirIds() {
        val style = ThemeStyle(Json.parseToJsonElement("""{"collections":{"gold":"#123456","teal":"nope"}}""").jsonObject)
        assertEquals(Color(0xFF123456), style.collections.color(CollectionTint.Gold))
        assertEquals(CollectionTint.Teal.color, style.collections.color(CollectionTint.Teal))
    }

    @Test
    fun everyIconTheAppDrawsIsInAfterglow() {
        val sources = File(repoRoot(), "app/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        val named = Regex("""themeIcon(?:OrNull)?\("([a-z0-9.\-]+)"""").findAll(sources).map { it.groupValues[1] }
        val drawables = Regex("""R\.drawable\.([a-z0-9_]+)""").findAll(sources).map { it.groupValues[1] }
            .filterNot { it.startsWith("ic_launcher") }.map(::slotOf)
        val prompts = Regex(""""(ic_btn_[a-z0-9_]+)"""").findAll(sources).map { slotOf(it.groupValues[1]) }
        val wanted = (named + drawables + prompts).toSortedSet()
        assertTrue(wanted.size > 60)
        assertEquals(emptySet<String>(), wanted - afterglow.icons.keys)
        // Prompts also draw a held `.filled` variant for every button.
        assertEquals(emptySet<String>(), prompts.filterNot { it.endsWith(".filled") }.map { "$it.filled" }.toSet() - afterglow.icons.keys)
    }

    @Test
    fun theGuideNamesEveryToken() {
        val guide = File(repoRoot(), "docs/themes.md").readText()
        assertEquals(emptyList<String>(), afterglow.style.tokens.filter { "`$it`" !in guide })
    }

    /** The slot an Afterglow file fills, as `tools/art/icons.py` names it. */
    private fun slotOf(file: String): String = listOf(
        Triple("ic_btn_", "button.", '.'), Triple("ic_status_", "status.", '.'), Triple("ic_badge_", "badge.", '.'),
        Triple("ic_control_", "control.", '.'), Triple("mark_", "mark.", '-'), Triple("symbol_", "symbol.", '-'),
    ).first { file.startsWith(it.first) }.let { (prefix, slot, separator) -> slot + file.removePrefix(prefix).replace('_', separator) }

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: error("user.dir"))
        while (!File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile ?: error("settings.gradle.kts not found")
        return dir
    }
}
