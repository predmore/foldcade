package app.foldcade

import app.foldcade.language.ThemeInstallRefusal
import app.foldcade.language.ThemeLedger
import app.foldcade.language.ThemeProcessExit
import app.foldcade.language.ThemeRelease
import app.foldcade.language.ThemeTrial
import app.foldcade.language.parseThemeJson
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeInstallsTest {
    private val root: File = Files.createTempDirectory("theme-installs").toFile()
    private val themes = File(root, "themes")
    private val bundled = setOf("afterglow")
    private var clock = 0L

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun pack(release: String, id: String = "neon", name: String = "Neon", source: String = "a/neon"): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("theme.json"))
            zip.write("""{"version": 2, "id": "$id", "name": "$name", "release": "$release", "source": {"github": "$source"}}""".encodeToByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    /** One start: trials, then the packs load as [PackagedTheme.loadAll] reads them, with [inUse] chosen. */
    private fun start(vararg exits: ThemeProcessExit, inUse: Set<String> = setOf("neon.zip")): Pair<ThemeInstalls, List<app.foldcade.language.ThemeRollback>> {
        val installs = ThemeInstalls(root)
        val rollbacks = installs.begin(exits.toList())
        val manifests = themes.listFiles().orEmpty().associate { file ->
            file.name to parseThemeJson(java.util.zip.ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry("theme.json")).readBytes().decodeToString() })!!
        }
        installs.loaded(manifests, inUse intersect manifests.keys)
        return installs to rollbacks
    }

    /** A start that showed Home long enough for the packs in use to end their trials. */
    private fun startAndProve() = start().first.shown(10 * 60_000L)

    private fun crash() = ThemeProcessExit(++clock, abnormal = true)

    private fun install(installs: ThemeInstalls, bytes: ByteArray, valid: Boolean = true) =
        installs.install(bytes.inputStream(), bundled) { valid }

    private fun installedRelease(): String? =
        java.util.zip.ZipFile(File(themes, "neon.zip")).use { zip -> zip.getInputStream(zip.getEntry("theme.json")).readBytes().decodeToString() }
            .let { parseThemeJson(it)?.release?.toString() }

    @Test
    fun aFirstInstallGoesOnTrialAndIsTakenOutAfterTwoCrashes() {
        val (installs, _) = start()
        val result = install(installs, pack("1.0.0"))
        assertTrue(result is ThemeInstallResult.Installed)
        assertTrue(File(themes, "neon.zip").isFile)
        // The start that installed it never loaded it, so its own crash does not count.
        start(crash())
        val (_, first) = start(crash())
        assertEquals(emptyList<Any>(), first)
        val (after, second) = start(crash())
        assertEquals(1, second.size)
        assertFalse(second.single().restored)
        assertFalse(File(themes, "neon.zip").exists())
        assertEquals(setOf(ThemeRelease(1)), after.ledger.refused["neon"])
        assertEquals(ThemeInstallResult.Refused(ThemeInstallRefusal.WentBack), install(after, pack("1.0.0")))
    }

    @Test
    fun anUpdateThatCrashesPutsTheCopyBeforeItBack() {
        install(start().first, pack("1.0.0"))
        // Shown long enough, the first release ends its trial.
        startAndProve()
        val (installs, _) = start()
        assertNull(installs.ledger.theme("neon.zip")!!.trial)
        assertTrue(install(installs, pack("1.1.0")) is ThemeInstallResult.Installed)
        assertEquals("1.1.0", installedRelease())
        start()
        start(crash())
        val (after, rollbacks) = start(crash())
        assertTrue(rollbacks.single().restored)
        assertEquals(ThemeRelease(1, 1), rollbacks.single().release)
        assertEquals("1.0.0", installedRelease())
        val restored = after.ledger.theme("neon.zip")!!
        assertEquals(ThemeRelease(1), restored.release)
        assertNull(restored.trial)
        assertFalse(File(root, "themes-previous/neon.zip").exists())
    }

    @Test
    fun aCrashWithAnotherPackInUseDoesNotCount() {
        install(start().first, pack("1.0.0"))
        start(inUse = emptySet())
        start(crash(), inUse = emptySet())
        val (installs, rollbacks) = start(crash(), inUse = emptySet())
        assertEquals(emptyList<Any>(), rollbacks)
        assertEquals(ThemeTrial(), installs.ledger.theme("neon.zip")!!.trial)
    }

    @Test
    fun theUpdateRulesRefuseWhatTheyShould() {
        val (installs, _) = start()
        install(installs, pack("1.0.0"))
        assertEquals(ThemeInstallResult.Refused(ThemeInstallRefusal.NotNewer), install(installs, pack("1.0.0")))
        assertEquals(ThemeInstallResult.Refused(ThemeInstallRefusal.OtherSource), install(installs, pack("2.0.0", source = "b/neon")))
        assertEquals(ThemeInstallResult.Refused(ThemeInstallRefusal.Broken), install(installs, pack("2.0.0"), valid = false))
        assertEquals(ThemeInstallResult.Refused(ThemeInstallRefusal.Bundled), install(installs, pack("2.0.0", id = "afterglow")))
        assertEquals(ThemeInstallResult.Refused(ThemeInstallRefusal.NotATheme), install(installs, "not a zip".encodeToByteArray()))
        assertEquals("1.0.0", installedRelease())
        assertEquals(listOf("neon.zip"), themes.list()!!.toList())
        assertFalse(File(root, "themes-staging/download").exists())
    }

    @Test
    fun anUpdateCutOffMidMoveIsFinishedAtTheNextStart() {
        install(start().first, pack("1.0.0"))
        startAndProve()
        // Staged and written down, then stopped before the old archive moved aside.
        val staged = File(root, "themes-staging/neon.zip").apply { parentFile.mkdirs(); writeBytes(pack("1.1.0")) }
        val ledgerFile = File(root, "theme-installs.json")
        val ledger = ThemeLedger.decode(ledgerFile.readText())!!
        val update = ledger.theme("neon.zip")!!.copy(stamp = "${staged.length()}:${staged.lastModified()}", release = ThemeRelease(1, 1),
            previous = ThemeRelease(1), trial = ThemeTrial())
        ledgerFile.writeText(ledger.copy(pending = update).encode())
        val (installs, _) = start()
        assertEquals("1.1.0", installedRelease())
        assertTrue(File(root, "themes-previous/neon.zip").isFile)
        assertNull(installs.ledger.pending)
        assertEquals(ThemeTrial(), installs.ledger.theme("neon.zip")!!.trial)
    }

    @Test
    fun aPackPushedOverAnUpdateForgetsTheCopyKeptForThatUpdate() {
        install(start().first, pack("1.0.0"))
        startAndProve()
        install(start().first, pack("1.1.0"))
        assertTrue(File(root, "themes-previous/neon.zip").isFile)
        File(themes, "neon.zip").apply { writeBytes(pack("5.0.0")); setLastModified(lastModified() + 5_000) }
        start()
        assertFalse(File(root, "themes-previous/neon.zip").exists())
        start(crash())
        start(crash())
        assertFalse(File(themes, "neon.zip").exists())
    }

    @Test
    fun packsAlreadyThereWhenTheLedgerStartsAreNotOnTrial() {
        themes.mkdirs()
        File(themes, "mine.zip").writeBytes(pack("1.0.0", id = "mine", name = "Mine"))
        val (installs, _) = start(crash(), crash(), inUse = setOf("mine.zip"))
        assertNull(installs.ledger.theme("mine.zip")!!.trial)
        assertEquals("mine", installs.ledger.theme("mine.zip")!!.id)
    }

    @Test
    fun anUpdateOverOneStillOnTrialKeepsTheProvenCopy() {
        install(start().first, pack("1.0.0"))
        startAndProve()
        install(start().first, pack("1.1.0"))
        // 1.1.0 is still on trial when 1.2.0 arrives, so 1.0.0 stays the copy kept.
        install(start().first, pack("1.2.0"))
        assertEquals(ThemeRelease(1), ThemeInstalls(root).also { it.begin(emptyList()) }.ledger.theme("neon.zip")!!.previous)
        start()
        start(crash())
        val (_, rollbacks) = start(crash())
        assertTrue(rollbacks.single().restored)
        assertEquals("1.0.0", installedRelease())
    }

    @Test
    fun aRollbackCutOffAfterItsRefusalIsFinishedAndStillRefused() {
        install(start().first, pack("1.0.0"))
        start()
        start(crash())
        // The refusal was written; the stop came before the archive moved.
        val ledgerFile = File(root, "theme-installs.json")
        val struck = ThemeLedger.decode(ledgerFile.readText())!!
        val theme = struck.theme("neon.zip")!!
        ledgerFile.writeText(struck.withTheme(theme.copy(trial = ThemeTrial(strikes = 2))).refuse("neon.zip").encode())
        val (installs, rollbacks) = start()
        assertFalse(rollbacks.single().restored)
        assertFalse(File(themes, "neon.zip").exists())
        assertEquals(ThemeInstallResult.Refused(ThemeInstallRefusal.WentBack), install(installs, pack("1.0.0")))
    }
}
