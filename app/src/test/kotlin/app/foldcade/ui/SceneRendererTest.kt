package app.foldcade.ui

import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import app.foldcade.language.AnimationExpression
import app.foldcade.language.parseThemeScene
import org.junit.Assert.*
import org.junit.Test

class SceneRendererTest {
    @Test
    fun invalidVariablesAndSchedulerExpressionsCannotEscapeTheRenderer() {
        for (property in listOf(
            """"variables":{"bad":"clamp(1,2,0)"}""",
            """"variables":{"bad":{"keyframes":[[0,0],[1,1]],"time":"0/0"}}""",
            """"frameRate":"clamp(30,60,0)"""",
        )) {
            val scene = parseThemeScene("""{"version":1,"id":"bad","name":"Bad",$property,"layers":[]}""", "bad.json", emptyMap())!!
            val renderer = SceneRenderer(scene)
            repeat(2) {
                renderer.draw(CanvasDrawScope(), 1f, true, 0f)
                assertNull(renderer.frameRate(1f, 640f, 480f, true, 0f, 1f))
            }
            assertEquals(1, renderer.diagnostics.size)
            assertTrue(renderer.diagnostics.single().source.startsWith("bad.json:"))
        }
    }

    @Test
    fun schedulerUsesMeasuredViewportBeforeDrawingAndAfterResize() {
        val scene = parseThemeScene("""{
            "version":1,"id":"viewport","name":"Viewport",
            "variables":{"rate":"width / height"},"frameRate":"rate","layers":[]
        }""", "viewport.json", emptyMap())!!
        val renderer = SceneRenderer(scene)
        assertEquals(2f, renderer.frameRate(0f, 640f, 320f, true, 0f, 1f)!!, 0f)
        assertEquals(0.5f, renderer.frameRate(0f, 320f, 640f, true, 0f, 1f)!!, 0f)
        assertTrue(renderer.diagnostics.isEmpty())
        assertEquals(0L, renderer.metrics.frames)
    }

    @Test
    fun failedLayersSkipTheirRepeatedVariableEvaluationOnLaterFrames() {
        val parsed = parseThemeScene("""{
            "version":1,"id":"bad-layer","name":"Bad layer","variables":{"counter":0},
            "layers":[{"type":"line","name":"broken","repeat":10,"color":["clamp(1,2,0)",1,1,1]}]
        }""", "layer.json", emptyMap())!!
        var evaluations = 0
        val scene = parsed.copy(variables = listOf(parsed.symbols.index("counter") to AnimationExpression { (++evaluations).toFloat() }))
        val renderer = SceneRenderer(scene)
        renderer.draw(CanvasDrawScope(), 0f, true, 0f)
        assertEquals(2, evaluations)
        assertEquals("layer.json:broken", renderer.diagnostics.single().source)
        renderer.draw(CanvasDrawScope(), 1f, true, 0f)
        assertEquals(3, evaluations)
        assertEquals(1, renderer.diagnostics.size)
        assertEquals(0, renderer.metrics.layers)
    }
}
