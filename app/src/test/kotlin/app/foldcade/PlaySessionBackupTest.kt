package app.foldcade

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaySessionBackupTest {
    @Test
    fun playHistoryIsNotExcludedFromBackup() {
        val rules = listOf("backup_rules.xml", "data_extraction_rules.xml").map { rulesText(it) }
        rules.forEach { text ->
            val excludes = text.lineSequence().filter { "<exclude" in it }.toList()
            assertTrue(excludes.any { "datastore" in it })
            assertFalse(excludes.any { """path=".""" in it && """domain="file"""" in it })
            assertFalse(excludes.any { "play-sessions" in it })
            assertFalse(text.contains("no_backup"))
        }
    }

    private fun rulesText(name: String): String {
        val candidates = listOf(
            File("src/main/res/xml/$name"),
            File("app/src/main/res/xml/$name"),
        )
        return candidates.first { it.isFile }.readText()
    }
}
