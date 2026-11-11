package app.foldcade

import org.junit.Assert.*
import org.junit.Test

class ApkPluginCatalogTest {
    private val trusted = setOf("a".repeat(64), "b".repeat(64))
    private val archive = PluginArchive("app.foldcade.moonlight", 7, trusted)

    @Test fun requiresTheCatalogPackageANewerVersionAndTheExactCurrentSignerSet() {
        assertEquals(PluginVerification.Verified, verifyPluginArchive(archive, null, trusted))
        assertEquals(PluginVerification.Verified, verifyPluginArchive(archive, 6, trusted.reversed().toSet()))
        assertEquals(PluginVerification.WrongPackage, verifyPluginArchive(archive.copy(packageName = "other"), null, trusted))
        assertEquals(PluginVerification.NotNewer, verifyPluginArchive(archive, 7, trusted))
        assertEquals(PluginVerification.NotNewer, verifyPluginArchive(archive, 8, trusted))
        assertEquals(PluginVerification.NotNewer, verifyPluginArchive(archive.copy(versionCode = 0), null, trusted))
        for (signers in listOf(emptySet(), setOf("a".repeat(64)), trusted + "c".repeat(64))) {
            assertEquals(PluginVerification.WrongSigner, verifyPluginArchive(archive.copy(signers = signers), null, trusted))
        }
        assertEquals(PluginVerification.WrongSigner, verifyPluginArchive(archive, null, emptySet()))
    }

    private fun release(url: String = "https://github.com/predmore/foldcade-moonlight/releases/download/v1/foldcade-moonlight.apk", size: Long = 100): String =
        """{"tag_name":"v1","assets":[{"name":"foldcade-moonlight.apk","size":$size,"browser_download_url":"$url"}]}"""

    @Test fun selectsOnlyTheNamedReleaseAsset() {
        val parsed = parsePluginRelease(release())!!
        assertEquals("v1", parsed.tag)
        assertEquals(100L, parsed.bytes)
        assertNull(parsePluginRelease("""{"assets":[]}"""))
        assertNull(parsePluginRelease("""{"draft":true,"assets":[]}"""))
        assertNull(parsePluginRelease("""{"prerelease":true,"assets":[]}"""))
        assertNull(parsePluginRelease("""{"assets":[{"name":"other.apk"}]}"""))
    }

    @Test fun refusesMalformedOrUnexpectedDownloadLocationsAndSizes() {
        val valid = parsePluginRelease(release())!!.url
        val invalid = listOf(
            release(valid.replace("https:", "http:")), release(valid.replace("github.com", "untrusted.example")),
            release(valid.replace("github.com", "user@github.com")), release(valid + "#fragment"), release(valid + "?x=1"),
            release(valid.replace("/predmore/", "/someone/")), release(valid.replace("/v1/", "/../")),
            release(size = -1), release(size = MAX_PLUGIN_APK_BYTES + 1), "{}", "not json",
        )
        invalid.forEach { json ->
            try { parsePluginRelease(json); fail("Accepted invalid release") } catch (_: Exception) { }
        }
    }
}
