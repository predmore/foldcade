package app.foldcade.language

import androidx.compose.ui.graphics.Color
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeZipTest {
    @Test
    fun badFieldsFallBackAndFocusCannotHide() {
        val parsed = parseThemeJson(
            """
            {
              "name": "Sample",
              "background": "#112233",
              "focus": "#112233",
              "muted": "nope",
              "iconRadius": 9,
              "artScale": 0.1
            }
            """.trimIndent(),
        )
        assertNotNull(parsed)
        parsed!!
        assertEquals("Sample", parsed.name)
        assertEquals(Color(0xFF112233), parsed.background)
        assertEquals(Color(0xFFFFFFFF), parsed.focus)
        assertEquals(Color(0xFF8A8A8A), parsed.muted)
        assertEquals(0.5f, parsed.iconRadius)
        assertEquals(0.7f, parsed.artScale)
        assertEquals(BackgroundMotion.Off, parsed.backgroundMotion)
    }

    @Test
    fun aZipWithoutANameIsRejected() {
        assertNull(parseThemeJson("""{"background":"#000000"}"""))
        assertNull(parseThemeJson("not json"))
    }

    @Test
    fun bottomPanelKeepsFourColumns() {
        assertEquals(4, Metrics.columns)
        assertTrue(focusClearsNeighborGap(1240f))
        assertTrue(focusClearsNeighborGap(1920f))
        assertTrue(Metrics.chromeClearancePx > Metrics.focusStrokePx)
    }

    @Test
    fun referenceZipMatchesTheContractFile() {
        val source = repoFile("themes/afterglow/theme.json").readText()
        val parsed = parseThemeJson(source)
        assertNotNull(parsed)
        parsed!!
        assertEquals("Afterglow", parsed.name)
        assertEquals(Color(0xFF000000), parsed.background)
        assertEquals(Color(0xFF000000), parsed.surface)
        assertEquals(Color(0xFFC8FFE4), parsed.focus)
        assertEquals(0.28f, parsed.iconRadius)
        assertEquals(0.92f, parsed.artScale)
        assertEquals(BackgroundMotion.Ribbons, parsed.backgroundMotion)
        assertTrue(parsed.focus != parsed.background)
        ZipFile(repoFile("app/src/main/assets/themes/afterglow.zip")).use { zip ->
            val packed = zip.getInputStream(zip.getEntry("theme.json")).readBytes().decodeToString()
            assertEquals(source.trim(), packed.trim())
            listOf(
                "font.ttf",
                "wallpaper-top.png",
                "wallpaper-bottom.png",
                "preview.png",
                "sounds/move.ogg",
                "sounds/activate.ogg",
                "sounds/back.ogg",
                "sounds/notify.ogg",
                "marks/dual.png",
                "marks/pocket.png",
                "marks/desk.png",
                "marks/beam.png",
                "LICENSE",
            ).forEach { name -> assertNotNull(name, zip.getEntry(name)) }
        }
    }

    private fun repoFile(relative: String): File {
        val cwd = File(".").canonicalFile
        val candidates = listOf(
            File(cwd, relative),
            File(cwd, "../$relative"),
            File(cwd.parentFile, relative),
        )
        return candidates.firstOrNull { it.isFile } ?: error("missing $relative from $cwd")
    }
}
