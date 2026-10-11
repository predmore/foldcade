package app.foldcade

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class PlaySessionBackupTest {
    @Test
    fun everyBackupModeIncludesSettingsAndFilesButExcludesCredentials() {
        for (mode in backupModes()) {
            assertEquals(setOf("sharedpref" to "foldcade.xml", "file" to "."), mode.rules("include"))
            val excluded = mode.rules("exclude")
            assertTrue("file" to "datastore" in excluded)
            assertFalse("sharedpref" to "." in excluded)
            assertFalse("sharedpref" to "foldcade.xml" in excluded)
            assertFalse("file" to "." in excluded)
        }
    }

    @Test
    fun playHistoryIsNotExcludedFromBackup() {
        for (mode in backupModes()) {
            assertFalse(mode.rules("exclude").any { (domain, path) ->
                domain == "file" && (path == "." || path == "play-sessions" || path.startsWith("play-sessions/"))
            })
        }
    }

    private fun backupModes(): List<Element> {
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val legacy = parser.parse(rulesText("backup_rules.xml").byteInputStream()).documentElement
        val modern = parser.parse(rulesText("data_extraction_rules.xml").byteInputStream()).documentElement
        return listOf(legacy) + listOf("cloud-backup", "device-transfer").map {
            modern.getElementsByTagName(it).item(0) as Element
        }
    }

    private fun Element.rules(tag: String): Set<Pair<String, String>> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { index ->
            val rule = nodes.item(index) as Element
            rule.getAttribute("domain") to rule.getAttribute("path")
        }.toSet()
    }

    private fun rulesText(name: String): String {
        val candidates = listOf(
            File("src/main/res/xml/$name"),
            File("app/src/main/res/xml/$name"),
        )
        return candidates.first { it.isFile }.readText()
    }
}
