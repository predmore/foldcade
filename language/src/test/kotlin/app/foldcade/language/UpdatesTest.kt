package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class UpdatesTest {
    private val packageName = "app.foldcade"
    private fun UpdateLedger.seen(code: Long, name: String = "$code") = observed(packageName, "Foldcade", code, name, code * 1000)

    @Test fun firstSightSameVersionAndDowngradesStayQuiet() {
        val first = UpdateLedger().seen(10)
        assertTrue(first.unread.isEmpty())
        assertEquals(first, first.seen(10))
        assertTrue(first.seen(9).unread.isEmpty())
        assertEquals(9L, first.seen(9).versions[packageName]!!.code)
    }

    @Test fun unreadUpgradesMergeFromTheOriginalVersionAndPersistUntilRead() {
        val first = UpdateLedger().seen(10)
        val next = first.seen(11).seen(13)
        assertEquals(listOf(ComponentUpdate(packageName, "Foldcade", 10, "10", 13, "13", 13000)), next.unread)
        assertEquals(11L, next.versions[packageName]!!.previousCode)
        val read = next.read(packageName)
        assertTrue(read.unread.isEmpty())
        assertEquals(next.versions, read.versions)
        assertTrue(read.seen(13).unread.isEmpty())
        assertEquals(13L, read.seen(14).unread.single().fromCode)
        assertEquals(next, next.read("other"))
    }

    @Test fun eachComponentHasItsOwnNoticeAndReadingOneKeepsTheOther() {
        val ledger = UpdateLedger().seen(1).observed("plugin", "Plugin", 1, "one", 100)
            .seen(2).observed("plugin", "Plugin", 2, "two", 3000)
        val notice = ledger.unread.first().notice()
        assertEquals("foldcade:updated:app.foldcade", notice.id)
        assertEquals("Foldcade updated", notice.title)
        assertEquals("2 · See what’s new", notice.body)
        assertEquals(packageName, notice.packageName)
        assertEquals(packageName, notice.updatePackage)
        assertTrue(notice.dismissable)
        assertEquals(2000L, notice.postedMillis)
        assertEquals("plugin", ledger.read(packageName).unread.single().packageName)
        val ready = readyUpdateNotice("plugin", "Foldcade Moonlight", "12.2", 5000)
        assertEquals("foldcade:update-ready:plugin", ready.id)
        assertEquals("Foldcade Moonlight 12.2 is ready", ready.title)
        assertEquals(UpdateCopy.readyBody, ready.body)
        assertEquals("plugin", ready.installUpdatePackage)
    }

    @Test fun aPluginNeedsFreeScreensAndNoCompetingWorkAndSelfAlsoNeedsAnIdleHomeOrScreenOff() {
        val open = UpdateGate(automatic = true, screensFree = true)
        assertTrue(mayInstallUpdate(false, open))
        assertFalse(mayInstallUpdate(true, open))
        assertFalse(mayInstallUpdate(true, open.copy(idleMillis = SELF_UPDATE_IDLE_MS - 1)))
        assertTrue(mayInstallUpdate(true, open.copy(idleMillis = SELF_UPDATE_IDLE_MS)))
        assertTrue(mayInstallUpdate(true, open.copy(homeShown = false, screenOff = true)))
        assertFalse(mayInstallUpdate(false, open.copy(homeShown = false)))
        val blocked = listOf(open.copy(automatic = false), open.copy(screensFree = false), open.copy(saveSync = true),
            open.copy(romDownload = true), open.copy(themeInstall = true), open.copy(backup = true), open.copy(launching = true))
        blocked.forEach { gate ->
            assertFalse(mayInstallUpdate(false, gate))
            assertFalse(mayInstallUpdate(true, gate.copy(screenOff = true, idleMillis = SELF_UPDATE_IDLE_MS)))
        }
    }

    @Test fun onlyTheCiReleasePackageWithTheReleaseSignerUpdatesItself() {
        val signers = PluginRegistry.foldcade.signers
        assertTrue(mayUpdateFoldcade("app.foldcade", "ci", signers))
        assertFalse(mayUpdateFoldcade("app.foldcade.debug", "ci", signers))
        assertFalse(mayUpdateFoldcade("app.foldcade", "local", signers))
        assertFalse(mayUpdateFoldcade("app.foldcade", "ci", emptySet()))
        assertFalse(mayUpdateFoldcade("app.foldcade", "ci", signers + "a".repeat(64)))
        assertFalse(PluginRegistry.foldcade in PluginRegistry.sources)
    }

    @Test fun realReleaseBodiesBecomeReadableNotesWithoutBuildOrGithubBookkeeping() {
        fun fixture(name: String): String = javaClass.getResource("/updates/$name.md")!!.readText()
        val foldcade = cleanReleaseNotes(fixture("foldcade"))
        assertFalse(foldcade.contains("versionCode:"))
        assertFalse(foldcade.contains("commit:"))
        assertFalse(foldcade.contains("The release asset is"))
        assertTrue(noticeBlocks(foldcade).any { it.heading && it.text == "Thor" })
        assertTrue(foldcade.contains("(#416)"))
        val moonlight = cleanReleaseNotes(fixture("moonlight"))
        assertTrue(moonlight.contains("• Play the stream at the top screen's volume"))
        assertFalse(moonlight.contains(" by @"))
        assertFalse(moonlight.contains("Full Changelog"))
        assertFalse(moonlight.contains("Complete source"))
        assertTrue(noticeBlocks(moonlight).any { it.heading && it.text == "What's Changed" })
        assertEquals("Foldcade Thor 0.1.0", cleanReleaseNotes(fixture("thor")))
        assertEquals("• A change\n\nSee the guide", cleanReleaseNotes("versionName: 1\nversionCode: 1\ncommit: a\n\n- A change\n\nSee [the guide](https://example.com)\nAsset name: foldcade.apk"))
        assertEquals("", cleanReleaseNotes("versionCode: 1\n\nAsset name: foldcade.apk"))
    }

    @Test fun notesCoverOnlyTheUpgradeRangeNewestFirstAndRollingOnlyAtTheInstalledCode() {
        fun note(code: Long, prerelease: Boolean = false, tag: String = "v$code") = ReleaseNotes(code, "$code", "Notes $code", prerelease, tag)
        val notes = listOf(note(3), note(1), note(2), note(4), note(5), note(4, true, "pre-release"),
            note(3, true, "pre-release"), note(4, true, "other"), ReleaseNotes(null, "unknown", "notes"))
        assertEquals(listOf(4L, 3L, 2L), notesInRange(notes, 1, 4).map { it.code })
        assertTrue(notesInRange(listOf(note(3, true, "pre-release")), 1, 4).isEmpty())
        assertEquals(4L, notesInRange(listOf(note(4, true, "pre-release")), 1, 4).single().code)
        val update = ComponentUpdate(packageName, "Foldcade", 1, "one", 4, "four", 0)
        val text = updateNotesText(update, notesInRange(notes, 1, 4))
        assertTrue(text.startsWith("# Foldcade four\n\nUpdated from one"))
        assertTrue(text.indexOf("## 4") < text.indexOf("## 3"))
        assertTrue(updateNotesText(update, emptyList()).contains(UpdateCopy.empty))
    }

    @Test fun aboutRowsAndCaptionsMatchBuildEligibilityAndHintsNameTheReleaseAction() {
        val debug = PickerModel(1, 1, appPackage = "app.foldcade.debug")
        assertEquals(listOf(Row.AutomaticUpdates, Row.WhatsNew("app.foldcade.debug"), Row.Version, Row.Licenses), settingsRows(SettingsCategory.About, debug))
        val ci = debug.copy(appPackage = "app.foldcade", selfUpdates = true)
        val release = Row.PluginInstall("app.foldcade")
        assertEquals(listOf(release, Row.AutomaticUpdates, Row.IncludePrereleases, Row.WhatsNew("app.foldcade"), Row.Version, Row.Licenses), settingsRows(SettingsCategory.About, ci))
        assertEquals(UpdateCopy.automaticCaption, settingsRowCaption(Row.AutomaticUpdates, ci))
        assertEquals(UpdateCopy.prereleasesCaption, settingsRowCaption(Row.IncludePrereleases, ci))
        assertEquals(RowControl.Toggle(true), rowControl(Row.AutomaticUpdates, ci))
        assertEquals(RowControl.Toggle(false), rowControl(Row.IncludePrereleases, ci))
        val available = ci.copy(pluginCatalogs = mapOf("app.foldcade" to PluginCatalogSetting(PluginCatalogStatus.UpdateAvailable, true, latest = "2"))).onSetting(release)
        assertEquals(ApkPluginCopy.update, settingsHints(available).first().label)
        assertEquals(Effect.InstallPlugin("app.foldcade"), reduce(available, Meaning.Activate).second)
        assertFalse(reduce(ci.onSetting(Row.AutomaticUpdates), Meaning.Activate).first.automaticUpdates)
        assertTrue(reduce(ci.onSetting(Row.IncludePrereleases), Meaning.Activate).first.includePrereleases)
        assertEquals(Effect.OpenWhatsNew("app.foldcade"), reduce(ci.onSetting(Row.WhatsNew("app.foldcade")), Meaning.Activate).second)
        assertEquals(UpdateCopy.retry, updatesHints(FaceMap.standard(), true).first().label)
    }

    @Test fun updateNoticesRouteToNotesAndReadyNoticesRouteToTheirStagedApk() {
        val update = ComponentUpdate(packageName, "Foldcade", 1, "one", 2, "two", 100).notice()
        val ready = readyUpdateNotice(packageName, "Foldcade", "two", 100)
        for ((notice, expected) in listOf(update to Effect.OpenWhatsNew(packageName, unread = true), ready to Effect.InstallPlugin(packageName, staged = true))) {
            val model = PickerModel(1, 1, notices = listOf(notice), askNoticeAccess = false)
            val right = reduce(model, Meaning.RightPanel).first
            val focused = right.copy(panel = right.panel!!.copy(index = panelRows(right.panel!!, right).indexOf(Row.Notice(notice.id))))
            val (opened, effect) = reduce(focused, Meaning.Activate)
            assertEquals(expected, effect)
            if (notice == update) assertEquals(focused.panel, opened.panel) else assertNull(opened.panel)
            assertNull(opened.dialog)
            assertEquals(Effect.DismissNotice(notice.id), reduce(focused, Meaning.ActivateBottom).second)
            assertTrue(panelHints(focused)!!.any { it.label == (if (notice == update) NoticeCopy.open else ApkPluginCopy.install) })
        }
    }
}
