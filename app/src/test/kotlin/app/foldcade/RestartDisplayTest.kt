package app.foldcade

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM guard for the Android boundary; the Thor recheck verifies actual task placement. */
class RestartDisplayTest {
    @Test fun bothRestartHopsExplicitlyTargetTheDefaultDisplay() {
        val app = File("src/main/kotlin/app/foldcade/FoldcadeApp.kt").readText()
        val restart = app.substringAfter("internal fun restart(").substringBefore("/**")
        val helper = File("src/main/kotlin/app/foldcade/RestartActivity.kt").readText()
        for ((name, source) in listOf("restart entry" to restart, "Home relaunch" to helper)) {
            assertTrue("$name must choose a display", source.contains("startOnDisplay("))
            assertTrue("$name must choose the physical default display", source.contains("Display.DEFAULT_DISPLAY"))
            assertTrue("$name must reuse Home's task", source.contains("multipleTask = false"))
        }
    }
}
