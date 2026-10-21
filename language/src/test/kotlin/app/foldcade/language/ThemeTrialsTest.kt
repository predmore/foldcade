package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTrialsTest {
    private fun manifest(extra: String = "") =
        parseThemeJson("""{"version": 2, "id": "neon", "name": "Neon"$extra}""")!!

    @Test
    fun releasesCompareByNumberAndFillMissingParts() {
        assertEquals(ThemeRelease(1, 2, 0), ThemeRelease.parse("1.2"))
        assertEquals(ThemeRelease(3), ThemeRelease.parse("3"))
        assertTrue(ThemeRelease.parse("1.10.0")!! > ThemeRelease.parse("1.9.9")!!)
        listOf("", "1.", "v1.2", "1.2.3.4", "1.-2", "1.2b", "1234567").forEach { assertNull(it, ThemeRelease.parse(it)) }
    }

    @Test
    fun sourceIsAGitHubRepositoryInLowerCase() {
        val file = manifest(""", "source": {"github": "Some-One/Foldcade-Neon"}""")
        assertEquals(ThemeSource.GitHub("some-one", "foldcade-neon"), file.source)
        assertEquals(file.source, ThemeSource.fromWire(file.source!!.wire))
        listOf(
            """"someone/neon"""", """{"github": "someone"}""", """{"github": "a/b/c"}""",
            """{"github": "-bad/neon"}""", """{"github": "someone/.."}""", """{"github": "a/b", "url": "x"}""",
        ).forEach { assertNull(it, manifest(""", "source": $it""").source) }
    }

    @Test
    fun hostIsAWholeNumberFromOneAndDefaultsToOne() {
        assertEquals(1, manifest().host)
        assertEquals(2, manifest(""", "host": 2""").host)
        listOf("0", "1.5", "\"1\"", "[1]").forEach { assertNull(it, manifest(""", "host": $it""").host) }
    }

    @Test
    fun releaseAndSourceAreNeverInheritedFromAfterglow() {
        val base = parseThemeJson("""{"version": 2, "id": "afterglow", "name": "Afterglow", "release": "9.0.0", "source": {"github": "a/b"}}""")!!
        val pack = manifest().inheriting(base)
        assertNull(pack.release)
        assertNull(pack.source)
    }

    @Test
    fun unreadableFieldsAreReportedAndThePackStillLoads() {
        val zip = ThemeZip(manifest(""", "release": "soon", "host": 7, "source": "x""""), mapOf())
        val messages = resolveThemeResources(zip).diagnostics.map { it.message }
        assertTrue(messages.any { it.startsWith("release") })
        assertTrue(messages.any { it.startsWith("host 7") })
        assertTrue(messages.any { it.startsWith("source") })
    }

    @Test
    fun anArchiveThatArrivesOrChangesGoesOnTrialUnlessAdopted() {
        val adopted = ThemeLedger().arrive(mapOf("a.zip" to "1:1"), adopt = true)
        assertNull(adopted.theme("a.zip")!!.trial)
        val changed = adopted.arrive(mapOf("a.zip" to "2:2", "b.zip" to "1:1"))
        assertEquals(ThemeTrial(), changed.theme("a.zip")!!.trial)
        assertEquals(ThemeTrial(), changed.theme("b.zip")!!.trial)
        assertEquals(listOf("b.zip"), changed.arrive(mapOf("b.zip" to "1:1")).themes.map { it.file })
    }

    @Test
    fun onlyCrashesWhileTheTrialCopyWasInUseCount() {
        val ledger = ThemeLedger().arrive(mapOf("neon.zip" to "1:1", "dusk.zip" to "1:1")).startRunning()
            .loaded(mapOf("neon.zip" to manifest(), "dusk.zip" to manifest().copy(id = "dusk", name = "Dusk")), setOf("neon.zip"))
        val crash = listOf(ThemeProcessExit(10, abnormal = true))
        val (once, none) = ledger.afterExits(crash)
        assertEquals(1, once.theme("neon.zip")!!.trial!!.strikes)
        assertEquals(0, once.theme("dusk.zip")!!.trial!!.strikes)
        assertEquals(emptyList<String>(), none)
        // The same exit is not counted twice, and a clean one counts nothing.
        val (again, _) = once.afterExits(crash + ThemeProcessExit(11, abnormal = false))
        assertEquals(1, again.theme("neon.zip")!!.trial!!.strikes)
        val (twice, failed) = again.afterExits(listOf(ThemeProcessExit(12, abnormal = true)))
        assertEquals(listOf("neon.zip"), failed)
        assertEquals(2, twice.theme("neon.zip")!!.trial!!.strikes)
    }

    @Test
    fun aStartThatDiedLoadingBlamesEveryPackOnTrialItWasLoading() {
        val loading = ThemeLedger().arrive(mapOf("neon.zip" to "1:1")).startRunning()
        val (struck, _) = loading.afterExits(listOf(ThemeProcessExit(1, abnormal = true)))
        assertEquals(1, struck.theme("neon.zip")!!.trial!!.strikes)
        val (clean, _) = loading.loaded(emptyMap(), emptySet()).afterExits(listOf(ThemeProcessExit(1, abnormal = true)))
        assertEquals(0, clean.theme("neon.zip")!!.trial!!.strikes)
    }

    @Test
    fun anUpdateInstalledWhileTheOldCopyRanIsNotBlamedForIt() {
        val running = ThemeLedger().arrive(mapOf("neon.zip" to "1:1"), adopt = true).startRunning()
            .loaded(mapOf("neon.zip" to manifest()), setOf("neon.zip"))
        val updated = running.withTheme(running.theme("neon.zip")!!.copy(stamp = "2:2", trial = ThemeTrial()))
        val (after, _) = updated.afterExits(listOf(ThemeProcessExit(1, abnormal = true)))
        assertEquals(0, after.theme("neon.zip")!!.trial!!.strikes)
        assertEquals(ThemeTrial(), updated.shown(THEME_TRIAL_SHOWN_MILLIS).theme("neon.zip")!!.trial)
    }

    @Test
    fun aTrialEndsOnceItsPackHasBeenShownLongEnough() {
        val ledger = ThemeLedger().arrive(mapOf("neon.zip" to "1:1")).startRunning().loaded(emptyMap(), setOf("neon.zip"))
        val partway = ledger.shown(THEME_TRIAL_SHOWN_MILLIS - 1)
        assertEquals(THEME_TRIAL_SHOWN_MILLIS - 1, partway.theme("neon.zip")!!.trial!!.shownMillis)
        val elsewhere = partway.copy(inUse = setOf("other.zip"))
        assertEquals(elsewhere, elsewhere.shown(60_000))
        assertNull(partway.shown(1).theme("neon.zip")!!.trial)
    }

    @Test
    fun aReleaseThatWentBackIsRefusedAfterwards() {
        val ledger = ThemeLedger().arrive(mapOf("neon.zip" to "1:1")).startRunning()
            .loaded(mapOf("neon.zip" to manifest(""", "release": "2.0.0"""")), emptySet())
        assertEquals(setOf(ThemeRelease(2)), ledger.refuse("neon.zip").refused["neon"])
        val back = ledger.rolledBack("neon.zip", restored = null)
        assertEquals(emptyList<InstalledTheme>(), back.themes)
        assertEquals(setOf(ThemeRelease(2)), back.refused["neon"])
        assertEquals(ThemeInstallRefusal.WentBack, checkThemeInstall(manifest(""", "release": "2.0.0""""), back, setOf("afterglow")))
        assertNull(checkThemeInstall(manifest(""", "release": "2.0.1""""), back, setOf("afterglow")))
    }

    @Test
    fun anUpdateMustComeFromTheSameSourceWithAHigherRelease() {
        val github = """, "source": {"github": "a/neon"}"""
        val installed = ThemeLedger().arrive(mapOf("neon.zip" to "1:1"), adopt = true)
            .loaded(mapOf("neon.zip" to manifest(""", "release": "1.2.0"$github""")), emptySet())
        val bundled = setOf("afterglow")
        fun check(extra: String) = checkThemeInstall(manifest(extra), installed, bundled)
        assertNull(check(""", "release": "1.3"$github"""))
        assertEquals(ThemeInstallRefusal.NotNewer, check(""", "release": "1.2"$github"""))
        assertEquals(ThemeInstallRefusal.OtherSource, check(""", "release": "1.3", "source": {"github": "b/neon"}"""))
        assertEquals(ThemeInstallRefusal.OtherSource, check(""", "release": "1.3""""))
        assertEquals(ThemeInstallRefusal.NoRelease, check(github))
        assertEquals(ThemeInstallRefusal.NewerHost, check(""", "release": "1.3", "host": ${THEME_HOST_LEVEL + 1}$github"""))
        assertEquals(ThemeInstallRefusal.NewerHost, check(""", "release": "1.3", "host": "one"$github"""))
        assertEquals(ThemeInstallRefusal.NoId, checkThemeInstall(parseThemeJson("""{"name": "Old", "release": "1"}""")!!, installed, bundled))
        assertEquals(ThemeInstallRefusal.Bundled, checkThemeInstall(manifest().copy(id = "afterglow", release = ThemeRelease(9)), installed, bundled))
    }

    @Test
    fun theLedgerSurvivesEncoding() {
        val ledger = ThemeLedger(
            themes = listOf(InstalledTheme("neon.zip", "1:2", "neon", "Neon", ThemeRelease(1, 2), ThemeSource.GitHub("a", "b"), ThemeRelease(1), ThemeTrial(1, 500))),
            refused = mapOf("neon" to setOf(ThemeRelease(1, 3))),
            loading = setOf("neon.zip"),
            running = mapOf("neon.zip" to "1:2"),
            inUse = setOf("neon.zip"),
            pending = InstalledTheme("dusk.zip", "3:4", trial = ThemeTrial()),
            lastExitMillis = 99,
        )
        assertEquals(ledger, ThemeLedger.decode(ledger.encode()))
        assertNull(ThemeLedger.decode("not json"))
    }

    @Test
    fun aPackThatDiesLoadingIsStillRefusedOnceItsManifestWasRead() {
        val reading = ThemeLedger().arrive(mapOf("neon.zip" to "1:1")).startRunning()
            .read("neon.zip", manifest(""", "release": "3.0.0""""))
        val (struck, _) = reading.afterExits(listOf(ThemeProcessExit(1, abnormal = true), ThemeProcessExit(2, abnormal = true)))
        assertEquals(2, struck.theme("neon.zip")!!.trial!!.strikes)
        assertEquals(setOf(ThemeRelease(3)), struck.refuse("neon.zip").refused["neon"])
    }
}
