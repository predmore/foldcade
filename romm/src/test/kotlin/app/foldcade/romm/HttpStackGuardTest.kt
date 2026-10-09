package app.foldcade.romm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class HttpStackGuardTest {
    @Test
    fun rommClientIsTheOnlyHttpStack() {
        val root = projectRoot()
        val needles = listOf("OkHttpClient(", "HttpURLConnection(", "java.net.http.HttpClient")
        val allowedClient = "romm/src/main/kotlin/app/foldcade/romm/RommClient.kt"
        val hits = mutableListOf<String>()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.path.contains("${File.separator}src${File.separator}main${File.separator}") }
            .filter { "build" !in it.path.split(File.separator) }
            .forEach { file ->
                val rel = root.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                if (rel == allowedClient) return@forEach
                val text = file.readText()
                needles.filter { text.contains(it) }.forEach { hits += "$rel contains $it" }
            }
        root.walkTopDown()
            .filter { it.isFile && it.name == "build.gradle.kts" }
            .forEach { file ->
                val rel = root.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                if (rel == "romm/build.gradle.kts") return@forEach
                if (file.readText().contains("libs.okhttp")) hits += "$rel depends on okhttp"
            }
        assertEquals(emptyList<String>(), hits)
    }

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        check(File(dir, "settings.gradle.kts").isFile) { "settings.gradle.kts not found from ${System.getProperty("user.dir")}" }
        return dir
    }
}
