package app.foldcade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Local builds layer src/local/res over the published icon. See tools/art/appicon.py. */
class LocalBuildIconTest {
    private val main = File("src/main/res")
    private val local = File("src/local/res")

    private fun files(root: File) = root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).path }.toSortedSet()

    @Test
    fun theLocalLayersOnlyReplaceTheLaunchersOwn() {
        assertEquals(
            sortedSetOf("drawable/ic_launcher_foreground.xml", "drawable/ic_launcher_monochrome.xml"),
            files(local),
        )
        files(local).forEach { path ->
            val published = File(main, path)
            assertTrue("$path overrides nothing", published.isFile)
            assertNotEquals("$path is the published layer", published.readText(), File(local, path).readText())
        }
    }

    @Test
    fun monochromeLayersAreWhiteOnly() {
        // A themed icon keeps only alpha, so a black "cut" would be tinted like the rest.
        listOf(main, local).forEach { root ->
            val colors = Regex("#[0-9A-Fa-f]{8}").findAll(File(root, "drawable/ic_launcher_monochrome.xml").readText())
                .map { it.value.uppercase() }.toSet()
            assertTrue(colors.isNotEmpty())
            colors.forEach { assertTrue("$root monochrome paints $it", it.endsWith("FFFFFF")) }
        }
    }

    @Test
    fun everyLabelFollowsTheBuild() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("android:label=\"Foldcade\""))
        assertEquals(2, Regex(Regex.escape("android:label=\"\${foldcadeLabel}\"")).findAll(manifest).count())
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("addStaticSourceDirectory(\"src/local/res\")"))
        assertTrue(gradle.contains("environmentVariable(\"GITHUB_ACTIONS\")"))
    }
}
