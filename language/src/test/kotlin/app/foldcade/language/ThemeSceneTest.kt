package app.foldcade.language

import kotlin.math.PI
import kotlin.math.round
import org.junit.Assert.*
import org.junit.Test

class ThemeSceneTest {
    private fun afterglow(): ThemeResources = resolveThemeResources(readThemeZip(
        afterglowZip().inputStream(),
    )!!)

    @Test
    fun aBrokenSceneWearsTheInheritedBackdrop() {
        val afterglow = BackgroundMotion("afterglow:ribbons")
        val asked = BackgroundMotion("night:ribbons")
        assertEquals(afterglow, backdropAfterBrokenScene(BackgroundMotion.Off, asked, sceneFileCount = 1, afterglow))
        assertEquals(asked, backdropAfterBrokenScene(asked, asked, sceneFileCount = 1, afterglow))
        assertEquals(BackgroundMotion.Off, backdropAfterBrokenScene(BackgroundMotion.Off, BackgroundMotion.Off, sceneFileCount = 0, afterglow))
        assertEquals(BackgroundMotion.Off, backdropAfterBrokenScene(BackgroundMotion.Off, asked, sceneFileCount = 1, null))
    }

    @Test
    fun referencePackContainsOnlyResourcesAndCompilesEveryScene() {
        val pack = afterglow()
        assertEquals(emptyList<ThemeDiagnostic>(), pack.diagnostics)
        assertEquals(3, pack.scenes.size)
        assertEquals(BackgroundMotion("afterglow:ribbons"), pack.defaultBackground)
        assertNotNull(pack.icons["mark.nintendo-3ds"])
        assertNotNull(pack.icons["button.a.filled"])
        assertNotNull(pack.icons["status.battery.100.charging"])
        assertNotNull(pack.icons["mark.droiddeck"])
        assertNotNull(pack.icons["mark.emulators"])
        assertNotNull(pack.icons["mark.collection-body"])
        assertNotNull(pack.icons["mark.collection-face"])
        assertNotNull(pack.icons["symbol.star"])
        assertNotNull(pack.icons["symbol.two-players"])
        assertNotNull(pack.icons["symbol.nintendo-3ds"])
        // Every build of an app wears its one object.
        assertEquals(pack.icons["app.com.retroarch"]?.file, pack.icons["app.com.retroarch.aarch64"]?.file)
        assertNotNull(pack.icons["app.com.android.chrome"])
        assertNotNull(pack.icons["app.org.azahar_emu.azahar"])
        assertEquals(367, pack.icons.size)
    }

    @Test
    fun themeRibbonsAndStaticBindTheSameGpuInputsAsTheReferenceModel() {
        val pack = afterglow()
        for ((id, mode) in listOf("ribbons" to BackgroundMotion.Ribbons, "static" to BackgroundMotion.Static)) {
            val scene = pack.scenes.getValue(BackgroundMotion("afterglow:$id"))
            val shader = scene.layers.first() as SceneLayer.Shader
            val state = scene.state()
            for (width in listOf(1240f, 1920f)) for (time in listOf(0f, 3.5f, 12f, 21.9f, 47f, 180f)) for (moving in listOf(false, true)) {
                state.update(time.toDouble(), width, 1080f, moving, 0f)
                val expected = RibbonField()
                assertTrue(fieldRibbons(mode, time, width, 1080f, moving, expected))
                fun check(name: String, values: FloatArray) {
                    val actual = shader.uniforms.getValue(name).map { it.evaluate(state.values).toFloat() }.toFloatArray()
                    assertArrayEquals("$id $name at $time", values, actual, 0.0001f)
                }
                check("shape", expected.shape)
                check("drift", expected.drift); check("start", expected.start); check("end", expected.end)
                check("swell", expected.swell); check("reach", floatArrayOf(expected.reach))
            }
        }
    }

    @Test
    fun ribbonsKeepMovingSmoothlyAfterMonthsOfUptime() {
        // Launchers run for weeks between reboots. A Float clock rounds a 1/60s step to
        // nothing after about 12 days; the sweep must still advance by exactly one frame.
        val scene = afterglow().scenes.getValue(BackgroundMotion("afterglow:ribbons"))
        val shader = scene.layers.first() as SceneLayer.Shader
        val drift = shader.uniforms.getValue("drift")
        val state = scene.state()
        val frame = 1.0 / 60.0
        val start = 90.0 * 24 * 60 * 60
        val phases = (0..10).map { step ->
            state.update(start + step * frame, 1920f, 1080f, true, 0f)
            drift.map { it.evaluate(state.values) }
        }
        val expected = 2 * PI * frame / 100.0
        for ((before, after) in phases.zipWithNext()) {
            var turned = after[0] - before[0]
            turned -= 2 * PI * round(turned / (2 * PI))
            assertEquals(expected, turned, 1e-9)
            for (strand in 0 until 3) assertTrue("pulse ${after[strand * 4 + 2]}", after[strand * 4 + 2] in 0.0..1.0)
        }
    }

    @Test
    fun eachSeedShowsItsOwnMomentOfEveryScene() {
        val pack = afterglow()
        for (id in listOf("ribbons", "static", "embers")) {
            val scene = pack.scenes.getValue(BackgroundMotion("afterglow:$id"))
            fun inputs(seed: Double, time: Double): List<Double> {
                val state = scene.state(seed).apply { update(time, 1240f, 1080f, true, 0f) }
                return scene.layers.flatMap { layer ->
                    (0 until layer.repeat).flatMap { index ->
                        state.instance(index)
                        when (layer) {
                            is SceneLayer.Shader -> layer.uniforms.values.flatten()
                            is SceneLayer.Disc -> listOf(layer.x, layer.y, layer.along) + layer.color
                            is SceneLayer.Line -> listOf(layer.y)
                            is SceneLayer.Image -> emptyList()
                        }.map { it.evaluate(state.values) }
                    }
                }
            }
            for (time in listOf(0.0, 47.0, 900.0)) {
                val one = inputs(0.25, time)
                assertNotEquals("$id two seeds at $time", one, inputs(0.75, time))
                assertEquals("$id is deterministic for a seed", one, inputs(0.25, time))
            }
        }
    }

    @Test
    fun aSceneThatDeclaresItsOwnSeedKeepsIt() {
        val scene = parseThemeScene("""{"version":1,"id":"own","name":"Own",
          "variables":{"seed":17,"twice":"seed * 2"},"layers":[]}""", "own.json", emptyMap())!!
        val state = scene.state(0.4).apply { update(3.0, 100f, 100f, true, 0f) }
        assertEquals(34.0, state.values[scene.symbols.index("twice")], 1e-9)
    }

    @Test
    fun scenesExtendABaseSceneByOverridingVariablesAndReusingLayers() {
        val shader = "uniform float gain; half4 main(float2 p) { return half4(gain); }".toByteArray()
        val base = """{"version":1,"id":"base","name":"Base","frameRate":30,
          "variables":{"pace":"time","gain":"pace * 2"},
          "layers":[{"name":"sky","type":"shader","file":"sky.agsl","uniforms":{"gain":"gain"}},
                    {"name":"dot","type":"disc","radius":2,"color":[1,1,1,1]}]}""".toByteArray()
        val child = """{"version":1,"extends":"base.json","id":"child","name":"Child","frameRate":1,
          "variables":{"pace":"time / 10","extra":"gain + 1"},"layers":["sky"]}"""
        val entries = mapOf("base.json" to base, "sky.agsl" to shader)
        val scene = parseThemeScene(child, "child.json", entries)!!
        assertEquals(listOf("sky"), scene.layers.map { it.name })
        val state = scene.state().apply { update(20.0, 100f, 100f, true, 0f) }
        // The override keeps the base's position, so the base's later gain sees it.
        assertEquals(4.0, state.values[scene.symbols.index("gain")], 1e-9)
        assertEquals(5.0, state.values[scene.symbols.index("extra")], 1e-9)
        assertEquals(1f, scene.frameRate.evaluate(state.values).toFloat(), 0f)
        assertEquals(2, parseThemeScene(child.replace(""","layers":["sky"]""", ""), "child.json", entries)!!.layers.size)
        for ((text, message) in listOf(
            child.replace("\"sky\"]", "\"moon\"]") to "no layer named 'moon'",
            child.replace("base.json", "gone.json") to "Missing base scene 'gone.json'",
            child.replace("base.json", "child.json") to "loops",
            """{"version":1,"id":"lone","name":"Lone","layers":["sky"]}""" to "only a scene that extends another",
        )) {
            val diagnostics = mutableListOf<ThemeDiagnostic>()
            assertNull(parseThemeScene(text, "child.json", entries + ("child.json" to text.toByteArray()), diagnostics::add))
            assertTrue(diagnostics.single().message, diagnostics.single().message.contains(message))
        }
    }

    @Test
    fun authoredBeadAndShimmerPreservePositionsColorsAndVisibility() {
        val scene = afterglow().scenes.getValue(BackgroundMotion("afterglow:ribbons"))
        val state = scene.state()
        val bead = scene.layers[1] as SceneLayer.Disc
        val sampler = ScenePathSampler(bead.path!!, scene)
        val shimmer = scene.layers[2] as SceneLayer.Disc
        for (width in listOf(1240f, 1920f)) for (time in listOf(0f, 0.5f, 3.5f, 7.8f, 10f, 13.7f, 25.5f, 47.5f)) {
            state.update(time.toDouble(), width, 1080f, true, 0f)
            val previous = BackdropFrame()
            layoutBackdrop(BackgroundMotion.Ribbons, time, width, 1080f, true, previous)
            for ((spec, kind) in listOf(bead to MarkKind.Bead, shimmer to MarkKind.Shimmer)) {
                val expected = previous.discs.take(previous.discCount).firstOrNull { it.kind == kind }
                assertEquals(expected != null, spec.color[3].evaluate(state.values).toFloat() > 0f)
                if (expected != null) {
                    val x: Float; val y: Float
                    if (spec.path != null) {
                        sampler.sample(state.values, spec.along.evaluate(state.values).toFloat()); x = sampler.x; y = sampler.y
                    } else { x = spec.x.evaluate(state.values).toFloat(); y = spec.y.evaluate(state.values).toFloat() }
                    assertEquals(expected.cx, x, 0.01f); assertEquals(expected.cy, y, 0.01f)
                    listOf(expected.red, expected.green, expected.blue).forEachIndexed { index, value ->
                        assertEquals(value.toFloat(), spec.color[index].evaluate(state.values).toFloat() * 255f, 1.01f)
                    }
                }
            }
        }
    }

    @Test
    fun authoredEmbersPreserveEachParticleAndHorizon() {
        val scene = afterglow().scenes.getValue(BackgroundMotion("afterglow:embers"))
        val state = scene.state()
        val line = scene.layers[0] as SceneLayer.Line
        val disc = scene.layers[1] as SceneLayer.Disc
        for (time in listOf(0f, 12f, 60f, 120f)) {
            state.update(time.toDouble(), 1240f, 1080f, true, 1f)
            val previous = BackdropFrame()
            layoutBackdrop(BackgroundMotion.Embers, time, 1240f, 1080f, true, previous)
            repeat(line.repeat) { index ->
                state.instance(index)
                assertEquals(previous.lines[index].y[0], line.y.evaluate(state.values).toFloat(), 0.01f)
            }
            repeat(disc.repeat) { index ->
                state.instance(index)
                assertEquals(previous.discs[index].cx, disc.x.evaluate(state.values).toFloat(), 0.01f)
                assertEquals(previous.discs[index].cy, disc.y.evaluate(state.values).toFloat(), 0.01f)
            }
        }
    }

    @Test
    fun independentIconProvidersKeepBackgroundAssetsOwnedByTheirOriginalPack() {
        val base = afterglow()
        val icon = ThemeIconAsset("ink", "ink.png", byteArrayOf(1), null, false)
        val overlay = base.copy(id = "ink", icons = mapOf("mark.nintendo-3ds" to icon), scenes = emptyMap())
        val composition = ThemeComposition(base, overlay)
        assertSame(icon, composition.iconCandidates("mark.nintendo-3ds").first())
        assertEquals("afterglow", composition.iconCandidates("mark.folder").first().owner)
        assertEquals(3, base.scenes.size)
        assertEquals(listOf("ink", "afterglow"), composition.iconCandidates("mark.nintendo-3ds").map { it.owner })
    }

    @Test
    fun invalidScenesReportTheirAssetAndLayerWithoutDiscardingOtherCategories() {
        val diagnostics = mutableListOf<ThemeDiagnostic>()
        val scene = parseThemeScene("""{"version":1,"id":"bad","name":"Bad","layers":[{"name":"sparks","type":"disc","x":"missing + 1"}]}""", "scenes/bad.json", emptyMap(), diagnostics::add)
        assertNull(scene)
        assertEquals("scenes/bad.json", diagnostics.single().source)
        assertTrue(diagnostics.single().message.contains("sparks"))
        assertTrue(diagnostics.single().message.contains("missing"))
        assertNull(parseThemeJson("""{"name":"Bad","version":{}}"""))
    }

    @Test
    fun legacyBackgroundChoicesMigrateAndNewPackIdsAreStable() {
        val choices = listOf(BackgroundChoice(BackgroundMotion("afterglow:ribbons"), "Ribbons"), BackgroundChoice(BackgroundMotion.Off, "Off"))
        assertEquals(BackgroundMotion("afterglow:ribbons"), resolveBackgroundChoice(BackgroundMotion.Ribbons, choices, BackgroundMotion.Off))
        assertEquals(BackgroundMotion.Off, resolveBackgroundChoice(BackgroundMotion("missing:scene"), choices, BackgroundMotion.Off))
        assertEquals(BackgroundMotion("aurora:stars"), BackgroundMotion.fromWire("aurora:stars"))
        val both = choices + BackgroundChoice(BackgroundMotion("other:ribbons"), "Other")
        assertEquals(BackgroundMotion.Off, resolveBackgroundChoice(BackgroundMotion.Ribbons, both, BackgroundMotion.Off))
    }

    @Test
    fun expressionsCompileOnceAndSupportConditionalTracksWithoutApplicationCallbacks() {
        val symbols = AnimationSymbols()
        val time = symbols.define("time")
        val expression = symbols.compile("if(time > 0, sin(pi / 2) * 4 + 2, 1 / 0)")
        val values = DoubleArray(symbols.size)
        values[time] = 2.0
        assertEquals(6.0, expression.evaluate(values), 0.0001)
        values[time] = 3.0
        assertEquals(6.0, expression.evaluate(values), 0.0001)
        try { symbols.compile("launchGame(time)"); fail("Application functions must not resolve") } catch (_: IllegalStateException) {}
    }

    @Test
    fun novelSceneIdsCanBeManifestDefaultsAndNamespacesRoundTrip() {
        val scene = """{"version":1,"id":"aurora","name":"Aurora","layers":[]}""".toByteArray()
        for (default in listOf("aurora", "night:aurora")) {
            val manifest = """{"version":2,"id":"night","name":"Night","backgroundMotion":"$default","scenes":["aurora.json"]}"""
            val pack = resolveThemeResources(ThemeZip(parseThemeJson(manifest)!!, mapOf("aurora.json" to scene)))
            assertEquals(BackgroundMotion("night:aurora"), pack.defaultBackground)
            assertEquals(pack.defaultBackground, BackgroundMotion.fromWire(pack.defaultBackground.wire))
        }
        assertNull(parseThemeJson("""{"version":2,"id":"night sky","name":"Night"}"""))
    }

    @Test
    fun keyframesLoopInBothTimeDirectionsAndNonLoopingTracksHoldTheirEndpoints() {
        val scene = parseThemeScene("""{
          "version":1,"id":"tracks","name":"Tracks",
          "variables":{
            "looped":{"keyframes":[[0,0],[1,10],[2,0]]},
            "held":{"keyframes":[[0,2],[1,6]],"loop":false},
            "smooth":{"keyframes":[[0,0],[1,1]],"loop":false,"easing":"smooth"},
            "offset":{"keyframes":[[0,0],[1,10],[2,0]],"time":"time + index * 0.5"}
          },"layers":[]
        }""", "tracks.json", emptyMap())!!
        val state = scene.state()
        fun value(name: String) = state.values[scene.symbols.index(name)].toFloat()
        state.update(-0.5, 100f, 100f, true, 0f)
        assertEquals(5f, value("looped"), 0.0001f)
        assertEquals(2f, value("held"), 0.0001f)
        state.update(0.25, 100f, 100f, true, 0f)
        assertEquals(2.5f, value("looped"), 0.0001f)
        assertEquals(3f, value("held"), 0.0001f)
        assertEquals(0.15625f, value("smooth"), 0.0001f)
        state.instance(1)
        assertEquals(7.5f, value("offset"), 0.0001f)
        state.update(2.0, 100f, 100f, true, 0f)
        assertEquals(0f, value("looped"), 0.0001f)
        assertEquals(6f, value("held"), 0.0001f)
        assertNull(parseThemeScene("""{"version":1,"id":"bad","name":"Bad","variables":{"x":{"keyframes":[[1,0],[1,2]]}},"layers":[]}""", "duplicate-times.json", emptyMap()))
    }

    @Test
    fun imageLayersAndShaderInputsResolveOnlyWithinTheOwningArchive() {
        val texture = byteArrayOf(1, 2, 3)
        val shader = "uniform shader picture; half4 main(float2 p) { return picture.eval(p); }".toByteArray()
        val text = """{"version":1,"id":"texture","name":"Texture","layers":[
          {"type":"shader","name":"sky","file":"sky.agsl","inputs":{"picture":"sky.png"}},
          {"type":"image","file":"sky.png","width":"width / 2","alpha":0.5}
        ]}"""
        val scene = parseThemeScene(text, "texture.json", mapOf("sky.agsl" to shader, "sky.png" to texture))!!
        assertSame(texture, scene.textures.getValue("sky.png"))
        assertEquals(mapOf("picture" to "sky.png"), (scene.layers[0] as SceneLayer.Shader).inputs)
        val state = scene.state().apply { update(0.0, 1920f, 1080f, false, 0f) }
        assertEquals(960f, (scene.layers[1] as SceneLayer.Image).width.evaluate(state.values).toFloat(), 0.0001f)
        val diagnostics = mutableListOf<ThemeDiagnostic>()
        assertNull(parseThemeScene(text, "texture.json", mapOf("sky.agsl" to shader), diagnostics::add))
        assertTrue(diagnostics.single().message.contains("sky: Missing asset 'sky.png'"))
    }
}
