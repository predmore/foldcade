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
        assertEquals(listOf(Row.PluginInstall(moonlight), Row.PluginCheck(moonlight)), settingsInnerRows(catalogPage, model))
        assertEquals("Foldcade Moonlight", settingsInnerTitle(catalogPage, model))
        assertEquals("Not published yet", ApkPluginCopy.status(PluginCatalogSetting(PluginCatalogStatus.NotPublished)))
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
        )
        invalid.forEach { sources ->
            try { requireValidRegistry(sources); fail("Accepted $sources") } catch (_: IllegalArgumentException) { }
        }
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

    @Test fun anInstalledPluginLeadsWithSharingThenRoutingAndInstallControlsBeforeIdentity() {
        val installed = plugin.copy(packageName = moonlight, granted = setOf("gamestream"), useForMoonlight = true)
        val model = PickerModel(count = 4, rowsPerPage = 1, apkPlugins = listOf(installed),
            pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Ready, installed = true)))
        val page = SettingsInner.ApkPlugin(installed.packageName)
        assertEquals(listOf(
            Row.PluginGrant(installed.packageName, "gamestream"), Row.PluginRouting(installed.packageName),
            Row.PluginInstall(moonlight), Row.PluginCheck(moonlight), Row.PluginInfo(installed.packageName, PluginInfoField.Version),
            Row.PluginInfo(installed.packageName, PluginInfoField.Package), Row.PluginInfo(installed.packageName, PluginInfoField.Signer),
        ), settingsInnerRows(page, model))
        val opened = reduce(model.onSetting(Row.SettingsTile(page)), Meaning.Activate).first
        assertEquals(Row.PluginGrant(installed.packageName, "gamestream"), settingsFocus(opened))
        assertEquals(RowText("Package", installed.packageName), rowText(Row.PluginInfo(installed.packageName, PluginInfoField.Package), model))
        assertEquals(PluginRegistry.moonlight.detail, settingsInnerDetail(page, model))
        assertEquals(ApkPluginCopy.unsupported, settingsInnerDetail(page, model.copy(apkPlugins = listOf(installed.copy(supported = false)))))
        assertEquals("Share paired computers", rowText(Row.PluginGrant(installed.packageName, "gamestream"), model).label)
        assertEquals("Check for updates", rowText(Row.PluginCheck(moonlight), model).label)
        assertEquals("Update", rowText(Row.PluginInstall(moonlight), model).label)
    }

    @Test fun aPluginOutsideTheRegistryHasNoInstallOrUpdateRows() {
        val page = SettingsInner.ApkPlugin(plugin.packageName)
        val model = PickerModel(count = 4, rowsPerPage = 1, apkPlugins = listOf(plugin))
        assertFalse(settingsInnerRows(page, model).any { it is Row.PluginInstall || it is Row.PluginCheck })
        assertNull(reduce(model.onSetting(Row.SettingsTile(page)), Meaning.Activate).second)
    }

    @Test fun anAvailableUpdateNamesTheReleaseAndCanBeInstalled() {
        val installed = plugin.copy(packageName = moonlight)
        val update = PluginCatalogSetting(PluginCatalogStatus.UpdateAvailable, installed = true, latest = "12.2-foldcade.2")
        val model = PickerModel(count = 4, rowsPerPage = 1, apkPlugins = listOf(installed), pluginCatalogs = catalogs(update))
        assertTrue(update.canInstall)
        assertEquals("Update available · 12.2-foldcade.2", ApkPluginCopy.status(update))
        assertEquals("Update available", ApkPluginCopy.status(update.copy(latest = null)))
        assertEquals(RowText("Update", null), rowText(Row.PluginInstall(moonlight), model))
        assertEquals(RowControl.Opens, rowControl(Row.PluginInstall(moonlight), model))
        val current = model.copy(pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Current, installed = true)))
        assertEquals(RowControl.Value("Up to date"), rowControl(Row.PluginInstall(moonlight), current))
        // Installed, and the release does not state its versionCode: offer it without claiming it is newer.
        val unhinted = PluginCatalogSetting(PluginCatalogStatus.Ready, installed = true, latest = "12.2-foldcade.2")
        assertEquals("Latest release · 12.2-foldcade.2", ApkPluginCopy.status(unhinted))
        assertEquals("Latest release available", ApkPluginCopy.status(unhinted.copy(latest = null)))
        assertEquals("Ready to install", ApkPluginCopy.status(unhinted.copy(installed = false)))
    }

    @Test fun anUnavailableInstallShowsItsStateAndOpeningTheCatalogFocusesCheckAgain() {
        val model = PickerModel(count = 4, rowsPerPage = 1, pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.NotPublished)))
        val opened = reduce(model.onSetting(Row.SettingsTile(catalogPage)), Meaning.Activate).first
        assertEquals(Row.PluginCheck(moonlight), settingsFocus(opened))
        assertEquals(RowText("Install", "Not available yet"), rowText(Row.PluginInstall(moonlight), model))
        assertEquals(RowControl.Value("Not available yet"), rowControl(Row.PluginInstall(moonlight), model))
        assertEquals("Check again", rowText(Row.PluginCheck(moonlight), model).label)
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
        val checking = page.copy(pluginCatalogs = catalogs(PluginCatalogSetting(PluginCatalogStatus.Checking)))
        assertNull(reduce(checking, Meaning.Activate).second)
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

    @Test fun onlyRegistryPluginsAndTheirDebugBuildsSayWhatTheyDo() {
        val model = PickerModel(count = 4, rowsPerPage = 1, apkPlugins = listOf(plugin))
        assertNull(settingsInnerDetail(SettingsInner.ApkPlugin(plugin.packageName), model))
        assertEquals(PluginRegistry.moonlight.detail, settingsInnerDetail(SettingsInner.ApkPlugin(moonlight), model))
        assertEquals(PluginRegistry.moonlight.detail, settingsInnerDetail(SettingsInner.ApkPlugin("$moonlight.debug"), model))
        assertEquals(PluginRegistry.moonlight.detail, settingsInnerDetail(catalogPage, model))
    }
}
