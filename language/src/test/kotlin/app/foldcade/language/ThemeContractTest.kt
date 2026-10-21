package app.foldcade.language

import androidx.compose.ui.graphics.Color
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `themes/contract.json` is what third-party packs are written against. It has to match the
 * engine, and nothing may leave it without a rename or a retirement in [ThemeContract].
 */
class ThemeContractTest {
    private val afterglow by lazy { resolveThemeResources(readThemeZip(afterglowZip().inputStream())!!) }
    private val contract = File(repoRoot(), "themes/contract.json")
    private val schema = File(repoRoot(), "themes/theme.schema.json")

    @Test
    fun theContractAndSchemaAreCurrent() {
        val written = contract.takeIf { it.isFile }?.readText()
        val vanished = written?.let { vanishedFromContract(Json.parseToJsonElement(it).jsonObject, afterglow) }.orEmpty()
        // A pack in the wild may use any of these. Name each in ThemeContract's renamed or retired lists.
        assertEquals("Left the contract without a rename or a retirement", emptyList<String>(), vanished)
        if (System.getProperty("foldcade.writeThemeContract") == "true") {
            contract.writeText(themeContractJson(afterglow))
            schema.writeText(themeSchemaJson(afterglow))
        }
        val stale = "is stale: run ./gradlew :language:testDebugUnitTest --tests '*ThemeContractTest*' -PwriteThemeContract=true"
        assertEquals("themes/contract.json $stale", themeContractJson(afterglow), contract.readText())
        assertEquals("themes/theme.schema.json $stale", themeSchemaJson(afterglow), schema.readText())
    }

    @Test
    fun renamesLeadToLiveNamesAndRetiredNamesStayRetired() {
        val tokens = afterglow.style.specs.keys
        ThemeContract.renamedTokens.forEach { (old, now) ->
            assertTrue("$old leads to $now, which the engine does not read", now in tokens)
            assertTrue("$old is read again", old !in tokens)
        }
        ThemeContract.retiredTokens.forEach { assertTrue("$it is read again", it !in tokens) }
        ThemeContract.renamedSlots.forEach { (old, now) ->
            assertTrue("$old leads to $now, which Afterglow does not fill", now in afterglow.icons)
            assertTrue("$old is filled again", old !in afterglow.icons)
        }
        ThemeContract.retiredSlots.forEach { assertTrue("$it is filled again", it !in afterglow.icons) }
        assertEquals(emptySet<String>(), ThemeContract.soundCues.values.filterNotNull().toSet() - ThemeContract.soundCues.keys)
    }

    @Test
    fun anOldTokenNameKeepsWorkingAndSaysWhatItBecame() {
        val reports = mutableListOf<ThemeDiagnostic>()
        val style = ThemeStyle(
            Json.parseToJsonElement("""{"shape":{"cornerRound":0.1},"tile":{"gone":1}}""").jsonObject,
            reports::add,
            renamed = mapOf("shape.cornerRound" to "shape.round"),
            retired = setOf("tile.gone"),
        )
        assertEquals(.1f, style.shape.round)
        assertEquals(
            listOf("Renamed to shape.round; this name still works", "Retired; this token no longer does anything"),
            reports.map { it.message },
        )
    }

    @Test
    fun anOldSlotNameFillsTheSlotItBecameUnlessThePackFillsThatToo() {
        val icons = Json.parseToJsonElement("""{"button.old":{"file":"a.xml"},"badge.was":{"file":"b.xml"},"badge.now":{"file":"c.xml"}}""").jsonObject
        val parsed = iconFields(icons, mapOf("button.old" to "button.new", "badge.was" to "badge.now"))
        assertEquals("a.xml", parsed["button.new"]?.file)
        assertEquals("c.xml", parsed["badge.now"]?.file)
        assertEquals(setOf("button.new", "badge.now"), parsed.keys)
    }

    @Test
    fun aMisspeltHostSlotIsReportedAndANewPlatformIsNot() {
        val file = parseThemeJson("""{"version":2,"id":"typo","name":"Typo","icons":{"button.aa":{"file":"a.xml"},"mark.sega-cd":{"file":"a.xml"}}}""")!!
        val pack = resolveThemeResources(ThemeZip(file, mapOf("a.xml" to byteArrayOf(1))), afterglow)
        assertEquals(listOf("theme.json:icons.button.aa"), pack.diagnostics.map { it.source })
        assertTrue("mark.sega-cd" in pack.icons)
    }

    @Test
    fun aScaleFollowsTheTokensAPackInheritsAndLeavesItsOwnAlone() {
        val file = parseThemeJson(
            """{"version":2,"id":"square","name":"Square","style":{
                "base":{"cornerScale":0,"durationScale":0.5,"typeScale":1.25},
                "shape":{"thumbCornerDp":6}}}""",
        )!!
        val square = resolveThemeResources(ThemeZip(file, emptyMap()), afterglow).style
        assertEquals(0f, square.shape.round)
        assertEquals(0f, square.metrics.cardCornerDp)
        assertEquals(6f, square.shape.thumbCornerDp)
        assertEquals(Math.round(afterglow.style.motion.durationFocus * .5f), square.motion.durationFocus)
        assertEquals(afterglow.style.type.heroTitle.value * 1.25f, square.type.heroTitle.value)
        // Strokes keep Afterglow's, since this pack did not scale them.
        assertEquals(afterglow.style.focusRing.stroke, square.focusRing.stroke)
        // A fraction scaled past its domain stops at the edge.
        val bright = resolveThemeResources(ThemeZip(parseThemeJson("""{"version":2,"id":"b","name":"B","style":{"base":{"glowScale":5}}}""")!!, emptyMap()), afterglow).style
        assertEquals(1f, bright.glow.markLightPeak)
    }

    @Test
    fun aPackThatExtendsAnotherInheritsItsResolvedLookAndIcons() {
        val parentFile = parseThemeJson(
            """{"version":2,"id":"dusk","name":"Dusk","background":"#101018","icons":{"badge.cloud":{"file":"cloud.png"}},
                "style":{"base":{"cornerScale":0.5},"colors":{"scrim":"#203040"}}}""",
        )!!
        val parent = resolveThemeResources(ThemeZip(parentFile, mapOf("cloud.png" to byteArrayOf(1))), afterglow)
        val childFile = parseThemeJson("""{"version":2,"id":"dusk-red","name":"Dusk red","extends":"dusk","focus":"#FF2020"}""")!!
        val child = resolveThemeResources(ThemeZip(childFile, emptyMap()), parent)
        assertEquals(Color(0xFF101018), child.file.background)
        assertEquals(Color(0xFFFF2020), child.file.focus)
        assertEquals(Color(0xFF203040), child.style.colors.scrim)
        // The parent's halved corners, once: the child does not halve them again.
        assertEquals(parent.style.metrics.cardCornerDp, child.style.metrics.cardCornerDp)
        assertEquals(afterglow.style.metrics.cardCornerDp / 2f, child.style.metrics.cardCornerDp)
        assertEquals("dusk", child.icons["badge.cloud"]?.owner)
        assertEquals(listOf("dusk"), child.parents)
        // A pack that does not say it extends Dusk keeps its icons to itself.
        val plain = resolveThemeResources(ThemeZip(parseThemeJson("""{"version":2,"id":"p","name":"P"}""")!!, emptyMap()), parent)
        assertEquals(null, plain.icons["badge.cloud"])
        assertEquals(emptyList<String>(), child.diagnostics)
    }

    @Test
    fun aPackDescribesItselfAndIsToldWhatItsDescriptionMisses() {
        val file = parseThemeJson(
            """{"version":2,"id":"about","name":"About","author":" Someone ","license":"CC-BY-4.0","description":"Soft",
                "preview":"preview.png","fonts":{"regular":"fonts/a.ttf","bold":"fonts/b.ttf","serif":"fonts/c.ttf"},"extends":"about"}""",
        )!!
        assertEquals(ThemeAbout("Someone", "Soft", "CC-BY-4.0", "preview.png"), file.about)
        assertEquals(mapOf("regular" to "fonts/a.ttf", "bold" to "fonts/b.ttf"), file.fonts)
        assertEquals(null, file.extends)
        val pack = resolveThemeResources(ThemeZip(file, mapOf("fonts/a.ttf" to byteArrayOf(1))), afterglow)
        assertEquals(
            setOf("extends must name another pack's id", "Missing preview image", "Missing font for 'bold'", "Unknown font face"),
            pack.diagnostics.map { it.message }.toSet(),
        )
    }

    @Test
    fun aSceneVariableNamedLikeALaterInputKeepsTheScenesOwn() {
        val scene = """{"version":1,"id":"s","name":"S","variables":{"hour":"7"},"layers":[]}"""
        val parsed = parseThemeScene(scene, "s.json", emptyMap())!!
        val state = parsed.state()
        state.update(0.0, 10f, 10f, true, 0f, 1f, SceneLive(hour = 20f, accentR = .5f))
        assertEquals(7.0, state.values[parsed.symbols.index("hour")], 0.0)
        assertEquals(.5, state.values[parsed.symbols.index("accentR")], 1e-6)
    }

    @Test
    fun soundCuesFallBackToOnesThatAlreadyPlayed() {
        assertEquals(listOf("open", "activate"), ThemeContract.soundChain("open"))
        assertEquals(listOf("erase", "back"), ThemeContract.soundChain("erase"))
        assertEquals(listOf("refuse"), ThemeContract.soundChain("refuse"))
    }

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: error("user.dir"))
        while (!File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile ?: error("settings.gradle.kts not found")
        return dir
    }
}
