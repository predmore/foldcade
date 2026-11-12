package app.foldcade

import app.foldcade.language.PluginCatalogSetting
import app.foldcade.language.PluginCatalogStatus
import app.foldcade.language.PluginRegistry
import org.junit.Assert.*
import org.junit.Test

class ApkPluginCatalogTest {
    private val trusted = setOf("a".repeat(64), "b".repeat(64))
    private val source = PluginRegistry.moonlight.copy(signers = trusted)
    private val archive = PluginArchive("app.foldcade.moonlight", 7, trusted)

    @Test fun requiresTheRegisteredPackageANewerVersionAndTheExactPinnedSignerSet() {
        assertEquals(PluginVerification.Verified, verifyPluginArchive(archive, null, source))
        assertEquals(PluginVerification.Verified, verifyPluginArchive(archive, 6, source.copy(signers = trusted.reversed().toSet())))
        assertEquals(PluginVerification.WrongPackage, verifyPluginArchive(archive.copy(packageName = "other"), null, source))
        assertEquals(PluginVerification.NotNewer, verifyPluginArchive(archive, 7, source))
        assertEquals(PluginVerification.NotNewer, verifyPluginArchive(archive, 8, source))
        assertEquals(PluginVerification.NotNewer, verifyPluginArchive(archive.copy(versionCode = 0), null, source))
        for (signers in listOf(emptySet(), setOf("a".repeat(64)), trusted + "c".repeat(64))) {
            assertEquals(PluginVerification.WrongSigner, verifyPluginArchive(archive.copy(signers = signers), null, source))
        }
        assertEquals(PluginVerification.WrongSigner, verifyPluginArchive(archive, null, source.copy(signers = emptySet())))
    }

    @Test fun theRealRegistryTrustsFoldcadesReleaseKeyNotTheRunningBuilds() {
        val released = PluginArchive("app.foldcade.moonlight", 3262, PluginRegistry.moonlight.signers)
        assertEquals(PluginVerification.Verified, verifyPluginArchive(released, null, PluginRegistry.moonlight))
        assertEquals(PluginVerification.WrongSigner, verifyPluginArchive(released.copy(signers = setOf("d".repeat(64))), null, PluginRegistry.moonlight))
        assertEquals("https://api.github.com/repos/predmore/foldcade-moonlight/releases/latest", latestReleaseApi(PluginRegistry.moonlight))
    }

    private val sourceTarball = """{"name":"foldcade-moonlight-v1-source.tar.gz","size":5,"browser_download_url":"https://github.com/predmore/foldcade-moonlight/releases/download/v1/foldcade-moonlight-v1-source.tar.gz"}"""

    private fun release(
        url: String = "https://github.com/predmore/foldcade-moonlight/releases/download/v1/foldcade-moonlight.apk",
        size: Long = 100,
        body: String? = "versionName: 1\nversionCode: 3262\ncommit: abc\n\nNotes",
    ): String {
        val bodyJson = body?.let { org.json.JSONObject.quote(it) } ?: "null"
        return """{"tag_name":"v1","body":$bodyJson,"assets":[$sourceTarball,{"name":"foldcade-moonlight.apk","size":$size,"browser_download_url":"$url"}]}"""
    }

    @Test fun selectsOnlyTheRegisteredAssetAndReadsTheVersionCodeHint() {
        val parsed = parsePluginRelease(release(), source)!!
        assertEquals("v1", parsed.tag)
        assertEquals("1", parsed.name)
        assertEquals(100L, parsed.bytes)
        assertEquals(3262L, parsed.versionCode)
        assertNull(parsePluginRelease(release(body = null), source)!!.versionCode)
        assertNull(parsePluginRelease("""{"assets":[]}""", source))
        assertNull(parsePluginRelease("""{"draft":true,"assets":[]}""", source))
        assertNull(parsePluginRelease("""{"prerelease":true,"assets":[]}""", source))
        assertNull(parsePluginRelease("""{"assets":[{"name":"other.apk"}]}""", source))
        assertNull(parsePluginRelease("""{"assets":[$sourceTarball]}""", source))
    }

    @Test fun theHintIsOneWholeVersionCodeLine() {
        assertEquals(3262L, releaseVersionCode("versionName: 12.2-foldcade.1\r\nversionCode: 3262\r\n"))
        assertEquals(7L, releaseVersionCode("  versionCode:7  "))
        assertEquals(1L, releaseVersionCode("versionCode: 1\nversionCode: 2"))
        for (body in listOf("", "Notes", "versionCode: 0", "versionCode: -1", "versionCode: 12a", "versionCode: 1 2",
            "The versionCode: 5 line", "versionCode:", "versionCode: 1234567890123456789")) {
            assertNull(body, releaseVersionCode(body))
        }
    }

    @Test fun aCheckSaysUpdateAvailableOrCurrentOnlyWhenTheReleaseStatesItsVersionCode() {
        val hinted = PluginRelease("v2", "https://example", 1, versionCode = 10)
        val unhinted = hinted.copy(versionCode = null)
        assertEquals(PluginCatalogStatus.NotPublished, releaseStatus(null, null))
        assertEquals(PluginCatalogStatus.NotPublished, releaseStatus(null, 9))
        assertEquals(PluginCatalogStatus.Ready, releaseStatus(hinted, null))
        assertEquals(PluginCatalogStatus.Ready, releaseStatus(unhinted, 9))
        assertEquals(PluginCatalogStatus.UpdateAvailable, releaseStatus(hinted, 9))
        assertEquals(PluginCatalogStatus.Current, releaseStatus(hinted, 10))
        assertEquals(PluginCatalogStatus.Current, releaseStatus(hinted, 11))
    }

    @Test fun reassessingChangesAStatusOnlyForNews() {
        val release = PluginRelease("v2", "https://example", 1, versionCode = 10)
        fun shown(status: PluginCatalogStatus) = PluginCatalogSetting(status)
        // Nothing new: an install result, Not published, and a status already current all stay.
        assertNull(reassessedStatus(shown(PluginCatalogStatus.Failed), 9, 9, true, release))
        assertNull(reassessedStatus(shown(PluginCatalogStatus.NotPublished), null, null, true, null))
        assertNull(reassessedStatus(shown(PluginCatalogStatus.UpdateAvailable), 9, 9, true, release))
        // Installed after Foldcade's own install was worked out against the new version, so it stays for consent focus.
        assertNull(reassessedStatus(shown(PluginCatalogStatus.Installed), 10, 10, true, release))
        // Installed, then removed some other way.
        assertEquals(PluginCatalogStatus.Ready, reassessedStatus(shown(PluginCatalogStatus.Installed), 10, null, true, release))
        // Installed was recorded before Android reported the package; it stays so discovery can move on to consent.
        assertNull(reassessedStatus(shown(PluginCatalogStatus.Installed), null, 10, true, release))
        assertNull(reassessedStatus(shown(PluginCatalogStatus.Installed), 9, 11, true, release))
        // Updated some other way while an update was offered.
        assertEquals(PluginCatalogStatus.Current, reassessedStatus(shown(PluginCatalogStatus.UpdateAvailable), 9, 10, true, release))
        // Android allowed installs while the row asked for that; until then, Allow installs stays.
        assertEquals(PluginCatalogStatus.UpdateAvailable, reassessedStatus(shown(PluginCatalogStatus.NeedsPermission), 9, 9, true, release))
        assertNull(reassessedStatus(shown(PluginCatalogStatus.NeedsPermission), 9, 9, false, release))
        // A version change with no release known says nothing new.
        assertNull(reassessedStatus(shown(PluginCatalogStatus.Unavailable), 9, 10, true, null))
        // A running check, download or install is never interrupted.
        for (busy in listOf(PluginCatalogStatus.Checking, PluginCatalogStatus.Downloading, PluginCatalogStatus.Installing)) {
            assertNull(reassessedStatus(shown(busy), 9, 10, true, release))
        }
    }

    @Test fun refusesMalformedOrUnexpectedDownloadLocationsAndSizes() {
        val valid = parsePluginRelease(release(), source)!!.url
        val invalid = listOf(
            release(valid.replace("https:", "http:")), release(valid.replace("github.com", "untrusted.example")),
            release(valid.replace("github.com", "user@github.com")), release(valid + "#fragment"), release(valid + "?x=1"),
            release(valid.replace("/predmore/", "/someone/")), release(valid.replace("/foldcade-moonlight/releases", "/other/releases")),
            release(valid.replace("/v1/", "/../")), release(valid.replace("github.com", "github.com:8443")),
            release(size = -1), release(size = MAX_PLUGIN_APK_BYTES + 1), "{}", "not json",
        )
        invalid.forEach { json ->
            try { parsePluginRelease(json, source); fail("Accepted invalid release") } catch (_: Exception) { }
        }
    }

    @Test fun anotherRegistryEntryAcceptsOnlyItsOwnRepositoryAndAsset() {
        val other = source.copy(packageName = "example.plugin", repository = "someone/plugin", asset = "plugin.apk")
        val url = "https://github.com/someone/plugin/releases/download/v3/plugin.apk"
        val json = """{"tag_name":"v3","assets":[{"name":"plugin.apk","size":10,"browser_download_url":"$url"}]}"""
        assertEquals(url, parsePluginRelease(json, other)!!.url)
        assertNull(parsePluginRelease(json, source))
        val moonlightUrl = """{"tag_name":"v3","assets":[{"name":"plugin.apk","size":10,"browser_download_url":"https://github.com/predmore/foldcade-moonlight/releases/download/v3/plugin.apk"}]}"""
        try { parsePluginRelease(moonlightUrl, other); fail("Accepted another repository's asset") } catch (_: IllegalArgumentException) { }
    }
}
