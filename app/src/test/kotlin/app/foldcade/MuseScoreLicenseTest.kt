package app.foldcade

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MuseScoreLicenseTest {
    @Test
    fun theNoticeShipsFromTheLicenseFile() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val activity = manifest.substringAfter(".LicensesActivity").substringBefore("/>")
        assertTrue(activity.contains("android:exported=\"false\""))
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("licenses/MuseScore_General_License.md"))
        assertTrue(gradle.contains("packageMuseScoreLicense"))
        assertFalse(File("src/main/assets/$MUSESCORE_LICENSE_ASSET").isFile)
        val notice = licenseFile().readText()
        listOf(
            "Frank Wen",
            "Michael Cowgill",
            "S. Christian Collins",
            "Ethan Winer",
            "Michael Schorsch",
        ).forEach { name ->
            assertTrue(name, notice.contains(name))
        }
        assertTrue(notice.contains("MIT license"))
        assertTrue(notice.contains("Permission is hereby granted"))
    }

    private fun licenseFile(): File {
        val name = "licenses/$MUSESCORE_LICENSE_ASSET"
        val cwd = File(".").canonicalFile
        return listOf(File(cwd, "../$name"), File(cwd, name), File(cwd.parentFile, name))
            .firstOrNull { it.isFile }
            ?: error("missing $name from $cwd")
    }
}
