package app.foldcade.romm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class HttpStackGuardTest {
    @Test
    fun rommClientIsTheOnlyHttpStack() {
        val root = projectRoot()
        val needles = listOf(
            "OkHttpClient(",
            "OkHttpClient.Builder",
            "newBuilder(",
            "HttpURLConnection(",
            "java.net.http.HttpClient",
            "openConnection",
            "openStream",
            "URL.readText",
            "URL.readBytes",
        )
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
                if (readsUrlDirectly(text)) hits += "$rel reads a URL with readText or readBytes"
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

    /**
     * `URL("http://host").readText()` and `readBytes()` skip [RommClient]'s origin guard.
     * Asset and file reads that never construct a [java.net.URL] stay allowed.
     */
    private fun readsUrlDirectly(text: String): Boolean {
        val reads = text.contains("readText(") || text.contains("readBytes(")
        if (!reads) return false
        val importsUrl = Regex("""(?m)^\s*import\s+java\.net\.URL\s*$""").containsMatchIn(text)
        val constructsUrl = Regex("""\bjava\.net\.URL\s*\(|(?<![.\w])URL\s*\(""").containsMatchIn(text)
        return importsUrl || constructsUrl
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
