package app.foldcade.language

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ThemeFailureTest {
    private fun scene(properties: String) = parseThemeScene(
        """{"version":1,"id":"probe","name":"Probe",$properties}""", "backgrounds/probe.json", emptyMap(),
    )!!

    @Test
    fun sceneWideErrorsStopEvaluationAndReportOnce() {
        for (variables in listOf(
            """{"bad":"clamp(1,2,0)"}""",
            """{"bad":{"keyframes":[[0,0],[1,1]],"time":"0/0"}}""",
        )) {
            val scene = scene(""""variables":$variables,"layers":[]""")
            val reports = mutableListOf<ThemeDiagnostic>()
            val runtime = SceneEvaluation(scene, reports::add)
            repeat(3) {
                assertNull(runtime.evaluate("variables") { runtime.state.update(0.0, 100f, 100f, true, 0f) })
                assertNull(runtime.frameRate(0.0, 100f, 100f, true, 0f, 1f))
            }
            assertTrue(runtime.failed)
            assertEquals("backgrounds/probe.json:variables", reports.single().source)
            assertTrue(reports.single().message.isNotBlank())
        }
    }

    @Test
    fun schedulerExpressionErrorsAndInvalidRatesStopWithDiagnostics() {
        for (rate in listOf("clamp(30,60,0)", "0/0", "-1")) {
            val reports = mutableListOf<ThemeDiagnostic>()
            val runtime = SceneEvaluation(scene(""""frameRate":"$rate","layers":[]"""), reports::add)
            assertNull(runtime.frameRate(1.0, 100f, 100f, true, 0f, 1f))
            assertNull(runtime.frameRate(2.0, 100f, 100f, true, 0f, 1f))
            assertEquals("backgrounds/probe.json:frameRate", reports.single().source)
        }
        val runtime = SceneEvaluation(scene(""""frameRate":0,"layers":[]"""))
        assertEquals(0f, runtime.frameRate(1.0, 100f, 100f, true, 0f, 1f)!!, 0f)
        assertFalse(runtime.failed)
    }

    @Test
    fun pathsUpdateChainedAndKeyframedVariablesAndRestoreInstanceState() {
        val scene = scene(""""variables":{
            "wave":"u*100", "derived":"wave+index",
            "track":{"keyframes":[[0,0],[1,100]],"time":"u","loop":false},
            "constant":"time+index"
        },"layers":[{"type":"disc","radius":2,"color":[1,1,1,1],
            "path":{"x":"track","y":"derived","samples":3}}]""")
        val state = scene.state()
        state.update(7.0, 100f, 100f, true, 0f)
        state.instance(2)
        val previous = state.values.copyOf()
        val sampler = ScenePathSampler((scene.layers.single() as SceneLayer.Disc).path!!, scene)
        sampler.sample(state.values, .5f)
        assertEquals(50f, sampler.x, .001f)
        assertEquals(52f, sampler.y, .001f)
        assertArrayEquals(previous, state.values, 0.0)
        assertEquals(3, scene.sampledVariables.size)
    }

    @Test
    fun failingPathRestoresVariablesBeforeTheLayerIsDisabled() {
        val scene = scene(""""variables":{"wave":"clamp(1,0,if(u>.2,-1,2))"},
            "layers":[{"type":"disc","radius":2,"color":[1,1,1,1],
            "path":{"x":"u","y":"wave","samples":3}}]""")
        val state = scene.state()
        state.update(0.0, 100f, 100f, true, 0f)
        val previous = state.values.copyOf()
        val sampler = ScenePathSampler((scene.layers.single() as SceneLayer.Disc).path!!, scene)
        try { sampler.sample(state.values, .5f); fail("Invalid clamp must fail the layer") }
        catch (_: IllegalArgumentException) { }
        assertArrayEquals(previous, state.values, 0.0)
    }

    @Test
    fun invalidStyleTokensFallBackBeforeTheyReachNativeControls() {
        val reports = mutableListOf<ThemeDiagnostic>()
        val style = ThemeStyle(Json.parseToJsonElement("""{
            "metrics":{"insetFraction":-.1,"cardCornerDp":-4,"gapFraction":"NaN"},
            "glow":{"restRim":1,"focusPeak":2},
            "focusRing":{"glowAlpha":-1,"glowSteps":2.5},
            "motion":{"durationFocus":-2,"scaleFocus":0}, "type":{"hint":-1}
        }""").jsonObject, reports::add)
        assertEquals(Metrics.insetFraction, style.metrics.insetFraction, 0f)
        assertEquals(Metrics.cardCornerDp, style.metrics.cardCornerDp, 0f)
        assertEquals(Metrics.gapFraction, style.metrics.gapFraction, 0f)
        assertEquals(.42f, style.glow.restRim, 0f)
        assertEquals(1f, style.glow.focusPeak, 0f)
        assertEquals(.10f, style.focusRing.glowAlpha, 0f)
        assertEquals(4, style.focusRing.glowSteps)
        assertEquals(180, style.motion.durationFocus)
        assertEquals(1.05f, style.motion.scaleFocus, 0f)
        assertEquals(TypeRamp.hint, style.type.hint)
        assertEquals(10, reports.size)
        assertTrue(reports.any { it.source == "theme.json:style.metrics.insetFraction" })
    }

    @Test
    fun validStyleTokensAndOvershootingCurvesKeepAuthorIntent() {
        val reports = mutableListOf<ThemeDiagnostic>()
        val style = ThemeStyle(Json.parseToJsonElement("""{
            "metrics":{"insetFraction":0,"cardCornerDp":80},
            "focusRing":{"glowSteps":1000}, "type":{"hint":42},
            "motion":{"scaleFocus":1.4,"arrive":[.1,-.5,.3,1.5],"leave":[0,0,null,1,1]}
        }""").jsonObject, reports::add)
        assertEquals(0f, style.metrics.insetFraction, 0f)
        assertEquals(80f, style.metrics.cardCornerDp, 0f)
        assertEquals(1000, style.focusRing.glowSteps)
        assertEquals(42f, style.type.hint.value, 0f)
        assertEquals(1.4f, style.motion.scaleFocus, 0f)
        assertArrayEquals(floatArrayOf(.1f, -.5f, .3f, 1.5f), style.motion.arrive, 0f)
        assertArrayEquals(floatArrayOf(.3f, 0f, .8f, .15f), style.motion.leave, 0f)
        assertEquals("theme.json:style.motion.leave", reports.single().source)
    }

    @Test
    fun legacyMarksComposeAsIconsAndRetainScreenBlending() {
        val file = parseThemeJson("""{"name":"Legacy"}""")!!
        val legacy = resolveThemeResources(ThemeZip(file, mapOf("marks/nintendo-3ds.png" to byteArrayOf(1))))
        val asset = legacy.icons.getValue("mark.nintendo-3ds")
        assertEquals(ThemeIconBlend.Screen, asset.blend)
        val modern = asset.copy(owner = "modern", blend = ThemeIconBlend.SrcOver)
        val base = legacy.copy(id = "modern", icons = mapOf("mark.nintendo-3ds" to modern))
        assertSame(asset, ThemeComposition(base, legacy).iconCandidates("mark.nintendo-3ds").first())
        assertSame(modern, ThemeComposition(legacy, base).iconCandidates("mark.nintendo-3ds").first())
    }

    @Test
    fun explicitIconSlotsWinOverLegacyConventions() {
        val file = parseThemeJson("""{"version":2,"id":"mixed","name":"Mixed", "icons":{
            "mark.folder":{"file":"icons/folder.png","accent":"#FF0000","tint":true}
        }}""")!!
        val pack = resolveThemeResources(ThemeZip(file, mapOf("marks/folder.png" to byteArrayOf(1), "icons/folder.png" to byteArrayOf(2))))
        assertEquals("icons/folder.png", pack.icons.getValue("mark.folder").file)
        assertEquals(ThemeIconBlend.SrcOver, pack.icons.getValue("mark.folder").blend)
    }

    @Test
    fun resolvedIconMetadataFollowsTheSuccessfulAssetAndSurvivesEviction() {
        val bad = ThemeIconAsset("override", "bad.png", byteArrayOf(1), Color.Red, true)
        val good = ThemeIconAsset("base", "good.png", byteArrayOf(2), Color.Blue, false)
        val cache = mutableMapOf(good.cacheKey to "decoded bitmap")
        val resolved = resolveThemeIcon(listOf(bad, good)) { cache[it.cacheKey] }!!
        cache.clear()
        assertSame(good, resolved.asset)
        assertEquals(Color.Blue, resolved.asset.accent)
        assertFalse(resolved.asset.tint)
        assertEquals("decoded bitmap", resolved.image)
    }

    @Test
    fun duplicateIdsCannotMaterializeOrOverwriteTheAcceptedPacksFiles() {
        val file = parseThemeJson("""{"version":2,"id":"same","name":"Same"}""")!!
        val first = ThemeZip(file, mapOf("font.ttf" to byteArrayOf(1)))
        val duplicate = ThemeZip(file, mapOf("font.ttf" to byteArrayOf(2)))
        val reports = mutableListOf<ThemeDiagnostic>()
        val directory = Files.createTempDirectory("theme-catalog-test").toFile()
        try {
            val cached = directory.resolve("font.ttf")
            val catalog = loadThemeCatalog(sequenceOf(first, duplicate), { zip ->
                cached.writeBytes(zip.entries.getValue("font.ttf"))
                zip.file.id
            }, reports::add)
            assertEquals(listOf("same"), catalog)
            assertArrayEquals(byteArrayOf(1), cached.readBytes())
            assertTrue(reports.single().message.contains("Duplicate theme id 'same'"))
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun failedPacksDoNotReserveTheirIdAndAssetCachesTrackContent() {
        val file = parseThemeJson("""{"name":"Legacy"}""")!!
        val first = ThemeZip(file, emptyMap())
        val good = ThemeZip(file, mapOf("font.ttf" to byteArrayOf(2)))
        assertEquals(listOf("Legacy"), loadThemeCatalog(sequenceOf(first, good), { if (it.entries.isEmpty()) null else it.file.id }))
        assertEquals(themeAssetCacheKey(byteArrayOf(1)), themeAssetCacheKey(byteArrayOf(1)))
        assertNotEquals(themeAssetCacheKey(byteArrayOf(1)), themeAssetCacheKey(byteArrayOf(2)))
    }
}
