package app.foldcade.language

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Words on screen use the typographer's apostrophe, as `Copy` always has: "can’t", not "can't"
 * (review-D S6). Scans every string literal in :language and the plugins, whose messages a
 * dialog shows as they are. Comments and the theme contract's author-facing descriptions,
 * which are written to contract.json, are not on screen and are left out.
 */
class CopyPunctuationTest {
    @Test fun userFacingStringsUseCurlyApostrophes() {
        val root = repoRoot()
        val files = kotlinFiles(File(root, "language/src/main")) +
            (File(root, "plugins").listFiles().orEmpty().flatMap { kotlinFiles(File(it, "src/main")) })
        val offenders = files.filter { it.name !in AUTHOR_FACING }.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.trim()
                if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) return@mapIndexedNotNull null
                val literals = LITERAL.findAll(line.substringBefore(" // ")).map { it.groupValues[1] }
                if (literals.any { STRAIGHT.containsMatchIn(it) }) "${file.relativeTo(root)}:${index + 1}: ${code.take(100)}" else null
            }
        }
        assertTrue("Straight apostrophes in user-facing strings; use ’:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test fun theScanCatchesAStraightApostrophe() {
        assertTrue(STRAIGHT.containsMatchIn(LITERAL.find("val x = \"That file isn't here.\"")!!.groupValues[1]))
        assertTrue(LITERAL.findAll("val x = \"That file isn’t here.\"").none { STRAIGHT.containsMatchIn(it.groupValues[1]) })
    }

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: error("user.dir"))
        while (true) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: error("settings.gradle.kts not found")
        }
    }

    private fun kotlinFiles(dir: File): List<File> =
        if (!dir.isDirectory) emptyList() else dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private companion object {
        val LITERAL = Regex("\"((?:[^\"\\\\\\n]|\\\\.)*)\"")
        val STRAIGHT = Regex("[A-Za-z]'[A-Za-z]")
        val AUTHOR_FACING = setOf("ThemeContract.kt", "ThemeContractFile.kt")
    }
}
