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

    /** One start: trials, then the packs load as [PackagedTheme.loadAll] reads them. */
    private fun start(vararg exits: ThemeProcessExit, choices: Set<String> = setOf("neon")): Pair<ThemeInstalls, List<app.foldcade.language.ThemeRollback>> {
        val installs = ThemeInstalls(root)
        val rollbacks = installs.begin(exits.toList(), choices)
        installs.loaded(themes.listFiles().orEmpty().associate { file ->
            file.name to parseThemeJson(java.util.zip.ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry("theme.json")).readBytes().decodeToString() })!!
        })
        return installs to rollbacks
    }

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
        start().first.shown(setOf("neon"), 10 * 60_000L)
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
        start()
        start(crash(), choices = setOf("afterglow"))
        val (installs, rollbacks) = start(crash(), choices = setOf("afterglow"))
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
        start().first.shown(setOf("neon"), 10 * 60_000L)
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
        start().first.shown(setOf("neon"), 10 * 60_000L)
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
        val (installs, _) = start(crash(), crash(), choices = setOf("mine"))
        assertNull(installs.ledger.theme("mine.zip")!!.trial)
        assertEquals("mine", installs.ledger.theme("mine.zip")!!.id)
    }
}
