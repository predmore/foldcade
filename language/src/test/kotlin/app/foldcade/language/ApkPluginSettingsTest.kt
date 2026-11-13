package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class ApkPluginSettingsTest {
    private val plugin = ApkPluginSetting("test.plugin", "test.plugin.Play", "Test plugin", "1", "ab:cd:ef:01", true, setOf("gamestream"), emptySet(), false)
    private val moonlight = PluginRegistry.moonlight.packageName
    private val catalogPage = SettingsInner.PluginCatalog(moonlight)
    private fun catalogs(setting: PluginCatalogSetting) = mapOf(moonlight to setting)

    @Test fun catalogIsPresentWithoutAnInstalledPlugin() {
        val model = PickerModel(count = 4, rowsPerPage = 1)
        assertEquals(listOf(Row.SettingsTile(catalogPage)), settingsRows(SettingsCategory.Plugins, model))
        assertEquals(listOf(Row.PluginInstall(moonlight)), settingsInnerRows(catalogPage, model))
        assertEquals("Foldcade Moonlight", settingsInnerTitle(catalogPage, model))
        assertEquals("Moonlight", settingsTileLabel(catalogPage, model))
        assertEquals(PluginAction("Check again", "Not available yet"), ApkPluginCopy.action(PluginCatalogSetting(PluginCatalogStatus.NotPublished)))
    }

    @Test fun theRegistryNamesTrustedRepositoriesAndSignersButNoVersions() {
        requireValidRegistry(PluginRegistry.sources)
        val source = PluginRegistry.moonlight
        assertEquals("predmore/foldcade-moonlight", source.repository)
        assertEquals("foldcade-moonlight.apk", source.asset)
        assertEquals(setOf("36f6883086e694e0a8b245421543063e6b96450970bd4030198543a59e6581ff"), source.signers)
        assertSame(source, PluginRegistry.source(moonlight))
        assertNull(PluginRegistry.source("test.plugin"))
        val invalid = listOf(
            listOf(source, source.copy(label = "Again")),
            listOf(source.copy(repository = "predmore")), listOf(source.copy(repository = "predmore/..")),
            listOf(source.copy(repository = "pred more/x")), listOf(source.copy(repository = "a/b/c")),
            listOf(source.copy(asset = "../x.apk")), listOf(source.copy(asset = "x.zip")), listOf(source.copy(asset = "dir/x.apk")),
            listOf(source.copy(signers = emptySet())), listOf(source.copy(signers = setOf("AB".repeat(32)))),
            listOf(source.copy(signers = setOf("ab"))), listOf(source.copy(packageName = "nopackage")), listOf(source.copy(label = " ")),
            listOf(source.copy(name = "")),
        )
        invalid.forEach { sources ->
            try { requireValidRegistry(sources); fail("Accepted $sources") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun aRegistryPluginsTileSaysItsShortNameAndItsPageTheFullOne() {
        val release = plugin.copy(packageName = moonlight, label = "Foldcade Moonlight")
        val debug = plugin.copy(packageName = "$moonlight.debug", label = "Foldcade Moonlight (Debug)")
        val model = PickerModel(count = 4, rowsPerPage = 1, apkPlugins = listOf(release, debug, plugin))
        val page = SettingsInner.ApkPlugin(moonlight)
        val debugPage = SettingsInner.ApkPlugin(debug.packageName)
        assertEquals("Moonlight", settingsTileLabel(page, model))
        assertEquals("Foldcade Moonlight", settingsInnerTitle(page, model))
        assertEquals("Moonlight Debug", settingsTileLabel(debugPage, model))
        // Its page is titled as the release is, and wears Debug beside the name.
        assertEquals("Foldcade Moonlight", settingsInnerTitle(debugPage, model))
        assertEquals(listOf(PageChip("Debug")), settingsInnerChips(debugPage, model))
        assertEquals(emptyList<PageChip>(), settingsInnerChips(page, model))
        assertEquals(PluginRegistry.moonlight.detail, settingsInnerDetail(debugPage, model))
        // A plugin the registry doesn't name is called what Android calls it.
        assertEquals("Test plugin", settingsTileLabel(SettingsInner.ApkPlugin(plugin.packageName), model))
    }

    @Test fun scopeConsentAndRoutingAreIndependentAndOnlyRequestedScopesAppear() {
        val page = SettingsInner.ApkPlugin(plugin.packageName)
        val model = PickerModel(count = 4, rowsPerPage = 1, apkPlugins = listOf(plugin))
        val grant = Row.PluginGrant(plugin.packageName, "gamestream")
        assertTrue(grant in settingsInnerRows(page, model))
        assertFalse(Row.PluginRouting(plugin.packageName) in settingsInnerRows(page, model))
        assertEquals(RowControl.Toggle(false), rowControl(grant, model))
        val consented = model.copy(apkPlugins = listOf(plugin.copy(granted = setOf("gamestream"), useForMoonlight = true)))
        assertTrue(Row.PluginRouting(plugin.packageName) in settingsInnerRows(page, consented))
        assertEquals(RowControl.Toggle(true), rowControl(grant, consented))
        assertEquals(RowControl.Toggle(true), rowControl(Row.PluginRouting(plugin.packageName), consented))
        assertFalse(settingsInnerRows(page, model.copy(apkPlugins = listOf(plugin.copy(scopes = emptySet())))).any { it is Row.PluginGrant })
    }

    @Test fun anInstalledPluginLeadsWithItsReleaseThenSharingThenRouting() {
        val installed = plugin.copy(packageName = moonlight, granted = setOf("gamestream"), useForMoonlight = true)
        val model = PickerModel(count = 4, rowsPerPage = 1, apkPlugins = listOf(installed),
            pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Ready, installed = true)))
        val page = SettingsInner.ApkPlugin(installed.packageName)
        assertEquals(listOf(
            Row.PluginInstall(moonlight), Row.PluginGrant(installed.packageName, "gamestream"), Row.PluginRouting(installed.packageName),
        ), settingsInnerRows(page, model))
        val opened = reduce(model.onSetting(Row.SettingsTile(page)), Meaning.Activate).first
        assertEquals(Row.PluginInstall(moonlight), settingsFocus(opened))
        // What it is reads under the rows, not as rows.
        assertEquals(listOf(PageFact("Version", "1"), PageFact("Package", moonlight), PageFact("Signer", "ab:cd:ef:01")), pluginFacts(SettingsInner.ApkPlugin(moonlight), model))
        assertEquals(PluginRegistry.moonlight.detail, settingsInnerDetail(page, model))
        assertEquals(ApkPluginCopy.unsupported, settingsInnerDetail(page, model.copy(apkPlugins = listOf(installed.copy(supported = false)))))
        assertEquals("Share paired computers", rowText(Row.PluginGrant(installed.packageName, "gamestream"), model).label)
        assertEquals("Update", rowText(Row.PluginInstall(moonlight), model).label)
        assertEquals(ApkPluginCopy.consent, settingsRowCaption(Row.PluginGrant(installed.packageName, "gamestream"), model))
        assertNull(settingsRowCaption(Row.PluginRouting(installed.packageName), model))
        assertEquals(ApkPluginCopy.officialMoonlight,
            settingsRowCaption(Row.PluginRouting(installed.packageName), model.copy(apkPlugins = listOf(installed.copy(useForMoonlight = false)))))
    }

    @Test fun aPluginOutsideTheRegistryHasNoInstallOrUpdateRows() {
        val page = SettingsInner.ApkPlugin(plugin.packageName)
        val model = PickerModel(count = 4, rowsPerPage = 1, apkPlugins = listOf(plugin))
        assertFalse(settingsInnerRows(page, model).any { it is Row.PluginInstall })
        assertNull(reduce(model.onSetting(Row.SettingsTile(page)), Meaning.Activate).second)
    }

    @Test fun anAvailableUpdateNamesTheReleaseAndCanBeInstalled() {
        val installed = plugin.copy(packageName = moonlight)
        val update = PluginCatalogSetting(PluginCatalogStatus.UpdateAvailable, installed = true, latest = "12.2-foldcade.2")
        val model = PickerModel(count = 4, rowsPerPage = 1, apkPlugins = listOf(installed), pluginCatalogs = catalogs(update))
        assertTrue(update.canInstall)
        assertEquals(PluginAction("Update", "12.2-foldcade.2", fresh = true), ApkPluginCopy.action(update))
        assertEquals(RowText("Update", "12.2-foldcade.2"), rowText(Row.PluginInstall(moonlight), model))
        assertEquals(RowControl.Value("12.2-foldcade.2", accent = true), rowControl(Row.PluginInstall(moonlight), model))
        assertEquals(Effect.InstallPlugin(moonlight), reduce(model.onSetting(Row.PluginInstall(moonlight)), Meaning.Activate).second)
        // Current: the same row checks again, and says so with the check.
        val current = model.copy(pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Current, installed = true)))
        assertEquals(RowText("Check for updates", "Up to date"), rowText(Row.PluginInstall(moonlight), current))
        assertEquals(RowControl.Value("Up to date", healthy = true), rowControl(Row.PluginInstall(moonlight), current))
        assertEquals(Effect.CheckPluginRelease(moonlight), reduce(current.onSetting(Row.PluginInstall(moonlight)), Meaning.Activate).second)
        // Installed, and the release does not state its versionCode: offer it without claiming it is newer.
        val unhinted = PluginCatalogSetting(PluginCatalogStatus.Ready, installed = true, latest = "12.2-foldcade.2")
        assertEquals(PluginAction("Update", "12.2-foldcade.2"), ApkPluginCopy.action(unhinted))
        assertEquals(PluginAction("Install", "12.2-foldcade.2"), ApkPluginCopy.action(unhinted.copy(installed = false)))
    }

    @Test fun anUnavailableInstallShowsItsStateAndTheSameRowChecksAgain() {
        val model = PickerModel(count = 4, rowsPerPage = 1, pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.NotPublished)))
        val opened = reduce(model.onSetting(Row.SettingsTile(catalogPage)), Meaning.Activate).first
        assertEquals(Row.PluginInstall(moonlight), settingsFocus(opened))
        assertEquals(RowText("Check again", "Not available yet"), rowText(Row.PluginInstall(moonlight), model))
        assertEquals(RowControl.Value("Not available yet"), rowControl(Row.PluginInstall(moonlight), model))
        assertEquals(Effect.CheckPluginRelease(moonlight), reduce(opened, Meaning.Activate).second)
        val unavailable = model.copy(pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Unavailable)))
        assertEquals(RowControl.Value("Unavailable", attention = true), rowControl(Row.PluginInstall(moonlight), unavailable))
        val ready = model.copy(pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Ready)))
        val actionable = reduce(ready.onSetting(Row.SettingsTile(catalogPage)), Meaning.Activate).first
        assertEquals(Row.PluginInstall(moonlight), settingsFocus(actionable))
    }

    @Test fun openingARegistryPluginsPageChecksItsReleaseUnlessACheckIsRunning() {
        val ready = PickerModel(count = 4, rowsPerPage = 1, pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Ready)))
        assertEquals(Effect.CheckPluginRelease(moonlight, onlyIfStale = true),
            reduce(ready.onSetting(Row.SettingsTile(catalogPage)), Meaning.Activate).second)
        val installed = ready.copy(apkPlugins = listOf(plugin.copy(packageName = moonlight)))
        assertEquals(Effect.CheckPluginRelease(moonlight, onlyIfStale = true),
            reduce(installed.onSetting(Row.SettingsTile(SettingsInner.ApkPlugin(moonlight))), Meaning.Activate).second)
        for (busy in listOf(PluginCatalogStatus.Checking, PluginCatalogStatus.Downloading, PluginCatalogStatus.Installing)) {
            val model = ready.copy(pluginCatalogs = catalogs(PluginCatalogSetting(busy)))
            assertNull(reduce(model.onSetting(Row.SettingsTile(catalogPage)), Meaning.Activate).second)
        }
    }

    @Test fun theRowsActOnTheirOwnPlugin() {
        val model = PickerModel(count = 4, rowsPerPage = 1, pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Ready)))
            .onSetting(Row.SettingsTile(catalogPage))
        val page = reduce(model, Meaning.Activate).first
        assertEquals(Row.PluginInstall(moonlight), settingsFocus(page))
        assertEquals(Effect.InstallPlugin(moonlight), reduce(page, Meaning.Activate).second)
        // Nothing while a check, download or install is under way; its state reads in full ink over the fill.
        for (busy in listOf(PluginCatalogStatus.Checking, PluginCatalogStatus.Downloading, PluginCatalogStatus.Installing)) {
            val running = page.copy(pluginCatalogs = catalogs(PluginCatalogSetting(busy, progress = 40)))
            assertNull(reduce(running, Meaning.Activate).second)
            assertTrue((rowControl(Row.PluginInstall(moonlight), running) as RowControl.Value).strong)
        }
        assertEquals(RowText("Installing", "40%"), rowText(Row.PluginInstall(moonlight),
            page.copy(pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Downloading, progress = 40)))))
        // A failure says why under the row, and A tries again.
        val failed = page.copy(pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Failed, detail = ApkPluginCopy.downloadFailed)))
        assertEquals(ApkPluginCopy.downloadFailed, settingsRowCaption(Row.PluginInstall(moonlight), failed))
        assertEquals(Effect.InstallPlugin(moonlight), reduce(failed, Meaning.Activate).second)
    }

    @Test fun successfulInstallAndDiscoveryInEitherOrderOpenSharingWithoutStealingLaterFocus() {
        val installed = plugin.copy(packageName = moonlight)
        val initial = PickerModel(count = 4, rowsPerPage = 1, pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Installing)))
            .onSetting(Row.SettingsTile(catalogPage))
        val catalog = reduce(initial, Meaning.Activate).first
        for (discoveryFirst in listOf(true, false)) {
            val first = if (discoveryFirst) catalog.copy(apkPlugins = listOf(installed))
                else catalog.copy(pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Installed, installed = true)))
            assertEquals(catalogPage, afterApkPluginInstall(catalog, first).settings?.inner)
            val both = first.copy(apkPlugins = listOf(installed), pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Installed, installed = true)))
            val focused = afterApkPluginInstall(first, both)
            assertEquals(SettingsInner.ApkPlugin(installed.packageName), focused.settings?.inner)
            assertEquals(Row.PluginGrant(installed.packageName, "gamestream"), settingsFocus(focused))
            val moved = focused.copy(settings = focused.settings!!.copy(row = 2))
            assertEquals(moved, afterApkPluginInstall(moved, moved.copy(apkPlugins = listOf(installed))))
            val elsewhere = first.copy(settings = null)
            assertNull(afterApkPluginInstall(elsewhere, both.copy(settings = null)).settings)
        }
    }

    @Test fun anUpdateLeavesFocusOnTheUpdateRowNotTheSharingSwitch() {
        val installed = plugin.copy(packageName = moonlight)
        val page = SettingsInner.ApkPlugin(moonlight)
        val updating = PickerModel(count = 4, rowsPerPage = 1, apkPlugins = listOf(installed),
            pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.UpdateAvailable, installed = true)))
        val opened = reduce(updating.onSetting(Row.SettingsTile(page)), Meaning.Activate).first
        val onUpdate = opened.copy(settings = opened.settings!!.copy(row = settingsInnerRows(page, opened).indexOf(Row.PluginInstall(moonlight))),
            pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Installing, installed = true)))
        val done = onUpdate.copy(pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Installed, installed = true)))
        val after = afterApkPluginInstall(onUpdate, done)
        assertEquals(page, after.settings?.inner)
        assertEquals(Row.PluginInstall(moonlight), settingsFocus(after))
    }

    @Test fun installReturnsOnceTheInstalledPluginIsGone() {
        assertFalse(PluginCatalogSetting(PluginCatalogStatus.Installed, installed = true).canInstall)
        assertTrue(PluginCatalogSetting(PluginCatalogStatus.Installed, installed = false).canInstall)
        assertTrue(PluginCatalogSetting(PluginCatalogStatus.Current, installed = false).canInstall)
    }

    @Test fun aPluginNotYetCheckedIsCheckingAndKnowsWhetherItIsInstalled() {
        val model = PickerModel(count = 4, rowsPerPage = 1, apkPlugins = listOf(plugin.copy(packageName = moonlight)))
        assertEquals(PluginCatalogSetting(PluginCatalogStatus.Checking, installed = true), model.pluginCatalog(moonlight))
        assertEquals(PluginCatalogSetting(PluginCatalogStatus.Checking, installed = false), PickerModel(count = 4, rowsPerPage = 1).pluginCatalog(moonlight))
    }

    @Test fun onlyAPluginSignedAsItsRegistryEntryPinsReadsVerified() {
        val signer = PluginRegistry.moonlight.signers.single()
        val release = plugin.copy(packageName = moonlight, fingerprint = "36:f6:88:30", signers = setOf(signer))
        assertTrue(release.verified)
        assertEquals(PageFact("Signer", "36:f6:88:30", verified = true),
            pluginFacts(SettingsInner.ApkPlugin(moonlight), PickerModel(count = 4, rowsPerPage = 1, apkPlugins = listOf(release)))[2])
        assertFalse(release.copy(signers = setOf(signer, "00".repeat(32))).verified)
        assertFalse(release.copy(signers = emptySet()).verified)
        // A debug build has no entry of its own, so it shows its fingerprint alone.
        assertFalse(release.copy(packageName = "$moonlight.debug").verified)
    }

    @Test fun onlyRegistryPluginsAndTheirDebugBuildsSayWhatTheyDo() {
        val model = PickerModel(count = 4, rowsPerPage = 1, apkPlugins = listOf(plugin))
        assertNull(settingsInnerDetail(SettingsInner.ApkPlugin(plugin.packageName), model))
        assertEquals(PluginRegistry.moonlight.detail, settingsInnerDetail(SettingsInner.ApkPlugin(moonlight), model))
        assertEquals(PluginRegistry.moonlight.detail, settingsInnerDetail(SettingsInner.ApkPlugin("$moonlight.debug"), model))
        assertEquals(PluginRegistry.moonlight.detail, settingsInnerDetail(catalogPage, model))
    }

    @Test fun pluginsAreListedAsCardsThatSayWhatNeedsDoing() {
        val signer = PluginRegistry.moonlight.signers.single()
        val release = plugin.copy(packageName = moonlight, version = "12.2-foldcade.3 (3267)", signers = setOf(signer), granted = setOf("gamestream"))
        val debug = plugin.copy(packageName = "$moonlight.debug", version = "12.2-foldcade.2")
        val model = PickerModel(count = 4, rowsPerPage = 1, apkPlugins = listOf(release, debug),
            pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Current, installed = true)))
        val rows = settingsRows(SettingsCategory.Plugins, model)
        // Rows, not tiles: Up and Down walk them, and A opens one.
        assertTrue(rows.none { it.settingsTile() })
        assertEquals(PluginListing("Foldcade Moonlight", "12.2-foldcade.3", "Up to date", healthy = true, verified = true), pluginListing(SettingsInner.ApkPlugin(moonlight), model))
        // Nothing shared: it can do nothing yet, which is what its card says.
        assertEquals(PluginListing("Foldcade Moonlight", "12.2-foldcade.2 · Debug", "Off"), pluginListing(SettingsInner.ApkPlugin(debug.packageName), model))
        val update = model.copy(pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.UpdateAvailable, installed = true, latest = "12.3")))
        assertEquals(PluginListing("Foldcade Moonlight", "12.2-foldcade.3", "Update available", fresh = true, verified = true), pluginListing(SettingsInner.ApkPlugin(moonlight), update))
        // A check that could not reach the release is no news on the card.
        val unreachable = model.copy(pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Unavailable, installed = true)))
        assertEquals(PluginListing("Foldcade Moonlight", "12.2-foldcade.3", null, verified = true), pluginListing(SettingsInner.ApkPlugin(moonlight), unreachable))
        // An update outranks Off: it is the news, and installing it keeps the switch as it was.
        val offWithUpdate = update.copy(apkPlugins = listOf(release.copy(granted = emptySet()), debug))
        assertTrue(pluginListing(SettingsInner.ApkPlugin(moonlight), offWithUpdate)!!.fresh)
        // Under way or failed reads as the page's release row does, even before sharing is on.
        val off = model.copy(apkPlugins = listOf(release.copy(granted = emptySet()), debug))
        val downloading = off.copy(pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Downloading, installed = true, progress = 40)))
        assertEquals("40%", pluginListing(SettingsInner.ApkPlugin(moonlight), downloading)!!.value)
        val failed = off.copy(pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Failed, installed = true)))
        assertEquals(PluginListing("Foldcade Moonlight", "12.2-foldcade.3", "Didn’t finish", attention = true, verified = true), pluginListing(SettingsInner.ApkPlugin(moonlight), failed))
        // Android's permission step and a check under way are named, never left blank or read as Off.
        val asking = off.copy(pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.NeedsPermission, installed = true)))
        assertEquals("Allow installs", pluginListing(SettingsInner.ApkPlugin(moonlight), asking)!!.value)
        val checking = model.copy(pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Checking, installed = true)))
        assertEquals("Checking…", pluginListing(SettingsInner.ApkPlugin(moonlight), checking)!!.value)
        assertEquals("Allow installs", pluginListing(catalogPage, PickerModel(count = 4, rowsPerPage = 1,
            pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.NeedsPermission))))!!.value)
        // A failed first install is no fresh offer.
        val failedCatalog = PickerModel(count = 4, rowsPerPage = 1, pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Failed)))
        assertEquals(PluginListing("Foldcade Moonlight", PluginRegistry.moonlight.detail, "Didn’t finish", attention = true), pluginListing(catalogPage, failedCatalog))
        val uninstalled = PickerModel(count = 4, rowsPerPage = 1, pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Ready)))
        assertEquals(PluginListing("Foldcade Moonlight", PluginRegistry.moonlight.detail, "Install", offer = true), pluginListing(catalogPage, uninstalled))
        val opened = reduce(uninstalled.copy(settings = SettingsPage(HostScreen.Top, category = settingsCategories(uninstalled).indexOf(SettingsCategory.Plugins), onRows = true)), Meaning.Activate).first
        assertEquals(catalogPage, opened.settings?.inner)
        // Back returns to the card that opened it.
        assertEquals(0, reduce(opened, Meaning.Back).first.settings?.row)
    }

    @Test fun aPluginWithNothingToSwitchStillOpensItsPage() {
        val bare = plugin.copy(scopes = emptySet())
        val model = PickerModel(count = 4, rowsPerPage = 1, apkPlugins = listOf(bare))
        val page = SettingsInner.ApkPlugin(bare.packageName)
        val opened = reduce(model.onSetting(Row.SettingsTile(page)), Meaning.Activate).first
        assertEquals(page, opened.settings?.inner)
        assertEquals(page, settingsShown(opened.settings!!, opened).first.inner)
        assertEquals(3, pluginFacts(page, opened).size)
        // B still returns to its card.
        assertNull(reduce(opened, Meaning.Back).first.settings?.inner)
    }

    @Test fun aFocusedPluginCardKeepsTheFocusWhenTheListChangesAroundIt() {
        val release = plugin.copy(packageName = moonlight, label = "Foldcade Moonlight")
        val debug = plugin.copy(packageName = "$moonlight.debug", label = "Foldcade Moonlight (Debug)")
        val both = PickerModel(count = 4, rowsPerPage = 1, apkPlugins = listOf(release, debug))
        val category = settingsCategories(both).indexOf(SettingsCategory.Plugins)
        val onDebug = both.copy(settings = SettingsPage(HostScreen.Top, category = category, row = 1, onRows = true))
        assertEquals(Row.SettingsTile(SettingsInner.ApkPlugin(debug.packageName)), settingsFocus(onDebug))
        // The release is removed some other way: the catalog card takes its place after the debug build.
        val removed = retainSettingsShelf(onDebug, onDebug.copy(apkPlugins = listOf(debug)))
        assertEquals(Row.SettingsTile(SettingsInner.ApkPlugin(debug.packageName)), settingsFocus(removed))
    }
}
