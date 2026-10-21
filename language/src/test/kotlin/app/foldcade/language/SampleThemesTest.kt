package app.foldcade.language

import androidx.compose.ui.graphics.Color
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `samples/themes` are written the way a third-party pack is, against the contract. Each must
 * load with nothing to report: a token or slot that changed without an alias breaks them first.
 */
class SampleThemesTest {
    private val afterglow by lazy { resolveThemeResources(readThemeZip(afterglowZip().inputStream())!!) }

    private fun samples(): List<ThemeZip> = File(repoRoot(), "samples/themes").listFiles().orEmpty()
        .filter { File(it, "theme.json").isFile }.sortedBy { it.name }.map { dir ->
            val entries = dir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(dir).invariantSeparatorsPath to it.readBytes() }
            ThemeZip(parseThemeJson(entries.getValue("theme.json").decodeToString())!!, entries)
        }

    private fun loaded(): Map<String, ThemeResources> {
        val loaded = linkedMapOf<String, ThemeResources>()
        orderByExtends(samples()).forEach { (zip, parent) ->
            loaded[zip.file.id] = resolveThemeResources(zip, parent?.let(loaded::getValue) ?: afterglow)
        }
        return loaded
    }

    @Test
    fun everySampleLoadsWithNothingToReport() {
        val packs = loaded()
        assertTrue(packs.size >= 2)
        packs.values.forEach { pack ->
            assertEquals(pack.id, emptyList<ThemeDiagnostic>(), pack.diagnostics)
            assertEquals(pack.id, pack.file.sceneFiles.size, pack.scenes.size)
        }
    }

    @Test
    fun aPackThatExtendsTheLightSampleKeepsItsColoursAndSquaresItsCorners() {
        val packs = loaded()
        val daylight = packs.getValue("daylight")
        val square = packs.getValue("daylight-square")
        assertEquals(Color(0xFFF4F1EA), square.file.background)
        assertEquals(daylight.style.colors.highlight, square.style.colors.highlight)
        assertEquals(0f, square.style.metrics.cardCornerDp)
        assertEquals(0f, square.style.shape.keyCorner)
        // The tiles too: their corner is a manifest field the corner scale also covers.
        assertEquals(0f, square.file.iconRadius)
        assertEquals(.2f, daylight.file.iconRadius)
        assertEquals(afterglow.style.metrics.cardCornerDp * .6f, daylight.style.metrics.cardCornerDp)
        assertEquals("daylight", square.icons.getValue("badge.cloud").owner)
        assertEquals(listOf("daylight"), square.parents)
    }

    @Test
    fun theSunReadsTheClockAndTheAccent() {
        val scene = loaded().getValue("daylight").scenes.values.single()
        assertTrue(scene.reads("hour"))
        assertTrue(scene.reads("accentR"))
    }

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: error("user.dir"))
        while (!File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile ?: error("settings.gradle.kts not found")
        return dir
    }
}
