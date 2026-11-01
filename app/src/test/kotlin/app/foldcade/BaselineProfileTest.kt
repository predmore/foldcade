package app.foldcade

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineProfileTest {
    @Test fun profileIncludesTheLiveStartupAndControllerPaths() {
        val app = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "src/main/AndroidManifest.xml").isFile || File(it, "app/src/main/AndroidManifest.xml").isFile }
            .let { if (File(it, "app").isDirectory) File(it, "app") else it }
        val rules = File(app, "src/main/baseline-prof.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue("Regenerate the app baseline profile", rules.size > 100)
        val live = mapOf(
            "FoldcadeApp" to "FoldcadeApp.kt",
            "PrimaryHomeActivity" to "HomeActivity.kt",
            "CompanionHomeActivity" to "HomeActivity.kt",
            "ShellController" to "ShellController.kt",
            "ui/PanelScreenKt" to "ui/PanelScreen.kt",
        )
        live.forEach { (type, source) ->
            val file = File(app, "src/main/kotlin/app/foldcade/$source")
            assertTrue("Profile target moved: $type", file.isFile)
            if (!type.endsWith("Kt")) {
                val declaration = Regex("\\bclass\\s+${Regex.escape(type.substringAfterLast('/'))}\\b")
                assertTrue("Profile target was removed: $type", declaration.containsMatchIn(file.readText()))
            }
            assertTrue("Profile lost $type", rules.any { "Lapp/foldcade/$type;" in it })
        }
        assertFalse("Never profile the harness", rules.any { "/baselineprofile/" in it })
    }
}
