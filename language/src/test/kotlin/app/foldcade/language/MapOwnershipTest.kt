package app.foldcade.language

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MapOwnershipTest {
    @Test
    fun onlyTheLanguageModuleNamesKeysOrThemeTokens() {
        val root = repoRoot()
        val outside = kotlinFiles(File(root, "app/src/main")) + kotlinFiles(File(root, "api/src/main"))
        val banned = listOf(
            "KEYCODE_",
            "meaningOf",
            "onGenericMotionEvent",
            "AXIS_",
            "Color(",
            "MaterialTheme",
            "Launch on top",
            "Launch on bottom",
        )
        outside.forEach { file ->
            val text = file.readText()
            banned.forEach { token ->
                assertFalse("$file contains $token", text.contains(token))
            }
            if (file.name != "StandInActivity.kt") {
                assertFalse(text.contains("fun onKeyDown"))
            }
            assertFalse("$file names a text size", text.contains(Regex("\\d\\.sp\\b")))
        }
        val standIn = File(root, "app/src/main/kotlin/app/foldcade/StandInActivity.kt").readText()
        val handler = standIn.substringAfter("override fun onKeyDown")
        assertTrue(handler.contains("return false"))
        assertFalse(handler.contains("meaningOf"))
        assertFalse(handler.contains("Meaning"))
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.intent.category.HOME"))
        assertTrue(manifest.contains("android.intent.category.SECONDARY_HOME"))
        assertFalse(manifest.contains("AccessibilityService"))
        assertFalse(manifest.contains("SYSTEM_ALERT_WINDOW"))
        val language = kotlinFiles(File(root, "language/src/main"))
        val sources = (outside + language).joinToString("\n") { it.readText() }
        language.forEach { file ->
            val text = file.readText()
            if (file.name == "Motion.kt") {
                assertTrue(text.contains("tween("))
            } else {
                assertFalse("${file.name} writes its own tween", text.contains("tween("))
                assertFalse(text.contains("CubicBezierEasing"))
            }
        }
        outside.forEach { file ->
            assertFalse(file.readText().contains("tween("))
            assertFalse(file.readText().contains("CubicBezierEasing"))
        }
        assertFalse(sources.contains("LinearEasing"))
        assertFalse(sources.contains("spring("))
        assertFalse(sources.contains("onGenericMotionEvent"))
        assertFalse(sources.contains("AXIS_"))
    }

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: error("user.dir"))
        while (true) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: error("settings.gradle.kts not found")
        }
    }

    private fun kotlinFiles(dir: File): List<File> {
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }
}
