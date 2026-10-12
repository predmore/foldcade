package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class ThemeSceneTest {
    private fun afterglow(): ThemeResources = resolveThemeResources(readThemeZip(
        afterglowZip().inputStream(),
    )!!)

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
        assertEquals(168, pack.icons.size)
    }

    @Test
    fun themeRibbonsAndStaticBindTheSameGpuInputsAsTheReferenceModel() {
        val pack = afterglow()
        for ((id, mode) in listOf("ribbons" to BackgroundMotion.Ribbons, "static" to BackgroundMotion.Static)) {
            val scene = pack.scenes.getValue(BackgroundMotion("afterglow:$id"))
            val shader = scene.layers.first() as SceneLayer.Shader
            val state = scene.state()
            for (width in listOf(1240f, 1920f)) for (time in listOf(0f, 3.5f, 12f, 21.9f, 47f, 180f)) for (moving in listOf(false, true)) {
                state.update(time, width, 1080f, moving, 0f)
                val expected = RibbonField()
                assertTrue(fieldRibbons(mode, time, width, 1080f, moving, expected))
                fun check(name: String, values: FloatArray) {
                    val actual = shader.uniforms.getValue(name).map { it.evaluate(state.values) }.toFloatArray()
                    assertArrayEquals("$id $name at $time", values, actual, 0.0001f)
                }
                check("shape", expected.shape)
                check("drift", expected.drift); check("start", expected.start); check("end", expected.end)
                check("swell", expected.swell); check("reach", floatArrayOf(expected.reach))
            }
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
            state.update(time, width, 1080f, true, 0f)
            val previous = BackdropFrame()
            layoutBackdrop(BackgroundMotion.Ribbons, time, width, 1080f, true, previous)
            for ((spec, kind) in listOf(bead to MarkKind.Bead, shimmer to MarkKind.Shimmer)) {
                val expected = previous.discs.take(previous.discCount).firstOrNull { it.kind == kind }
                assertEquals(expected != null, spec.color[3].evaluate(state.values) > 0f)
                if (expected != null) {
                    val x: Float; val y: Float
                    if (spec.path != null) {
                        sampler.sample(state.values, spec.along.evaluate(state.values)); x = sampler.x; y = sampler.y
                    } else { x = spec.x.evaluate(state.values); y = spec.y.evaluate(state.values) }
                    assertEquals(expected.cx, x, 0.01f); assertEquals(expected.cy, y, 0.01f)
                    listOf(expected.red, expected.green, expected.blue).forEachIndexed { index, value ->
                        assertEquals(value.toFloat(), spec.color[index].evaluate(state.values) * 255f, 1.01f)
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
            state.update(time, 1240f, 1080f, true, 1f)
            val previous = BackdropFrame()
            layoutBackdrop(BackgroundMotion.Embers, time, 1240f, 1080f, true, previous)
            repeat(line.repeat) { index ->
                state.instance(index)
                assertEquals(previous.lines[index].y[0], line.y.evaluate(state.values), 0.01f)
            }
            repeat(disc.repeat) { index ->
                state.instance(index)
                assertEquals(previous.discs[index].cx, disc.x.evaluate(state.values), 0.01f)
                assertEquals(previous.discs[index].cy, disc.y.evaluate(state.values), 0.01f)
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
        val values = FloatArray(symbols.size)
        values[time] = 2f
        assertEquals(6f, expression.evaluate(values), 0.0001f)
        values[time] = 3f
        assertEquals(6f, expression.evaluate(values), 0.0001f)
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
        fun value(name: String) = state.values[scene.symbols.index(name)]
        state.update(-0.5f, 100f, 100f, true, 0f)
        assertEquals(5f, value("looped"), 0.0001f)
        assertEquals(2f, value("held"), 0.0001f)
        state.update(0.25f, 100f, 100f, true, 0f)
        assertEquals(2.5f, value("looped"), 0.0001f)
        assertEquals(3f, value("held"), 0.0001f)
        assertEquals(0.15625f, value("smooth"), 0.0001f)
        state.instance(1)
        assertEquals(7.5f, value("offset"), 0.0001f)
        state.update(2f, 100f, 100f, true, 0f)
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
        val state = scene.state().apply { update(0f, 1920f, 1080f, false, 0f) }
        assertEquals(960f, (scene.layers[1] as SceneLayer.Image).width.evaluate(state.values), 0.0001f)
        val diagnostics = mutableListOf<ThemeDiagnostic>()
        assertNull(parseThemeScene(text, "texture.json", mapOf("sky.agsl" to shader), diagnostics::add))
        assertTrue(diagnostics.single().message.contains("sky: Missing asset 'sky.png'"))
    }
}
