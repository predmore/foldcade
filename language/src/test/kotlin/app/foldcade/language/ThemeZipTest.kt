package app.foldcade.language

import androidx.compose.ui.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
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
        assertEquals(Color(0xFF1C1C20), parsed.surface)
        assertEquals(Color(0xFFC8FFE4), parsed.focus)
        assertEquals(0.28f, parsed.iconRadius)
        assertEquals(0.92f, parsed.artScale)
        assertEquals(BackgroundMotion.Ribbons, parsed.backgroundMotion)
        assertTrue(parsed.focus != parsed.background)
        ZipFile(afterglowZip()).use { zip ->
            val packed = zip.getInputStream(zip.getEntry("theme.json")).readBytes().decodeToString()
            assertEquals(source.trim(), packed.trim())
            listOf(
                "font.ttf",
                "OFL-Nunito.txt",
                "preview.png",
                "sounds/move.ogg",
                "sounds/activate.ogg",
                "sounds/back.ogg",
                "sounds/notify.ogg",
                "LICENSE",
            ).forEach { name -> assertNotNull(name, zip.getEntry(name)) }
            // Nothing reads sprites or effect shaders, so the pack does not ship any to look like hooks.
            assertTrue(zip.entries().toList().none { it.name.startsWith("sprites/") || it.name.startsWith("effects/") })
            assertNull(zip.getEntry("marks/desk.png"))
            assertNull(zip.getEntry("wallpaper-top.png"))
            assertNull(zip.getEntry("wallpaper-bottom.png"))
        }
        val loaded = readThemeZip(afterglowZip().inputStream())
        assertNotNull(loaded)
        loaded!!
        assertEquals("Afterglow", loaded.file.name)
        assertEquals("font.ttf", fontZipName(loaded.entries.keys))
        assertNull(wallpaperZipName(loaded.entries.keys, top = true))
        assertNull(wallpaperZipName(loaded.entries.keys, top = false))
        listOf("move", "activate", "back", "notify").forEach { slot ->
            assertTrue(loaded.entries.containsKey(soundZipName(slot)))
        }
    }

    @Test
    fun wallpapersAndFontAcceptTheAlternateExtensions() {
        assertEquals(
            "wallpaper-top.png",
            wallpaperZipName(setOf("wallpaper-top.webp", "wallpaper-top.png"), top = true),
        )
        assertEquals("wallpaper-bottom.webp", wallpaperZipName(setOf("wallpaper-bottom.webp"), top = false))
        assertNull(wallpaperZipName(emptySet(), top = true))
        assertEquals("font.ttf", fontZipName(setOf("font.otf", "font.ttf")))
        assertEquals("font.otf", fontZipName(setOf("font.otf")))
        assertNull(fontZipName(emptySet()))
        assertEquals("sounds/notify.ogg", soundZipName("notify"))
    }

    @Test
    fun letterboxFitsInsideThePanel() {
        val wide = letterbox(2000f, 500f, 1000f, 1000f)
        assertEquals(1000f, wide.width, 0.01f)
        assertEquals(250f, wide.height, 0.01f)
        assertEquals(0f, wide.left, 0.01f)
        assertEquals(375f, wide.top, 0.01f)
        val tall = letterbox(500f, 2000f, 1920f, 1080f)
        assertEquals(270f, tall.width, 0.01f)
        assertEquals(1080f, tall.height, 0.01f)
        assertEquals(825f, tall.left, 0.01f)
        assertEquals(0f, tall.top, 0.01f)
        val empty = letterbox(0f, 10f, 100f, 100f)
        assertEquals(0f, empty.width, 0.01f)
    }

    @Test
    fun anInvalidZipIsNotATheme() {
        assertNull(readThemeZip(byteArrayOf(1, 2, 3, 4).inputStream()))
        assertNull(readThemeZip(zipBytes("theme.json" to """{"background":"#000000"}""").inputStream()))
        assertNull(readThemeZip(zipBytes("theme.json" to "not json").inputStream()))
    }

    @Test
    fun applicationCodeCannotBeSmuggledInAsAnUnusedOrOversizedAsset() {
        for (suffix in listOf("java", "KT", "kts", "class", "dex", "jar", "so", "apk", "js")) {
            assertNull(suffix, readThemeZip(zipBytes(
                "theme.json" to """{"version":2,"id":"test","name":"Test"}""",
                "unused/payload.$suffix" to "code",
            ).inputStream()))
        }
        assertNull(readThemeZip(zipBytes(
            "theme.json" to """{"name":"Test"}""",
            "payload.dex" to "x".repeat(8 * 1024 * 1024 + 1),
        ).inputStream()))
    }

    @Test
    fun aZipKeepsWallpaperFontAndSoundsAndDropsAPathEscape() {
        val loaded = readThemeZip(
            zipBytes(
                "theme.json" to """{"name":"Sample","iconRadius":0.2,"artScale":0.8}""",
                "wallpaper-top.webp" to "top",
                "wallpaper-bottom.png" to "bottom",
                "font.otf" to "font",
                "sounds/move.ogg" to "m",
                "sounds/activate.ogg" to "a",
                "sounds/back.ogg" to "b",
                "sounds/notify.ogg" to "n",
                "../escape.txt" to "no",
                "sounds/../theme.json" to """{"name":"Replaced"}""",
            ).inputStream(),
        )
        assertNotNull(loaded)
        loaded!!
        assertEquals("Sample", loaded.file.name)
        assertEquals(0.2f, loaded.file.iconRadius)
        assertEquals(0.8f, loaded.file.artScale)
        assertEquals(Color(0xFF000000), loaded.file.background)
        val topName = wallpaperZipName(loaded.entries.keys, top = true)
        val bottomName = wallpaperZipName(loaded.entries.keys, top = false)
        assertEquals("wallpaper-top.webp", topName)
        assertEquals("wallpaper-bottom.png", bottomName)
        assertEquals("top", topName?.let { loaded.entries[it] }?.decodeToString())
        assertEquals("bottom", bottomName?.let { loaded.entries[it] }?.decodeToString())
        assertEquals("font.otf", fontZipName(loaded.entries.keys))
        assertEquals("m", loaded.entries[soundZipName("move")]?.decodeToString())
        assertNull(loaded.entries["../escape.txt"])
        assertEquals("Sample", loaded.file.name)
    }

    private fun zipBytes(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
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
