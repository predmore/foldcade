package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class ApkPluginSettingsTest {
    private val plugin = ApkPluginSetting("test.plugin", "test.plugin.Play", "Test plugin", "1", "ab:cd:ef:01", true, setOf("gamestream"), emptySet(), false)

    @Test fun catalogIsPresentWithoutAnInstalledPlugin() {
        val model = PickerModel(count = 4, rowsPerPage = 1)
        assertEquals(listOf(Row.SettingsTile(SettingsInner.PluginCatalog)), settingsRows(SettingsCategory.Plugins, model))
        assertEquals(listOf(Row.PluginInstall, Row.PluginCheck), settingsInnerRows(SettingsInner.PluginCatalog, model))
        assertEquals("Not published yet", ApkPluginCopy.status(PluginCatalogSetting(PluginCatalogStatus.NotPublished)))
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
        val installed = plugin.copy(packageName = ApkPluginCopy.catalogPackage, granted = setOf("gamestream"), useForMoonlight = true)
        val model = PickerModel(count = 4, rowsPerPage = 1, apkPlugins = listOf(installed),
            pluginCatalog = PluginCatalogSetting(PluginCatalogStatus.Ready, installed = true))
        val page = SettingsInner.ApkPlugin(installed.packageName)
        assertEquals(listOf(
            Row.PluginGrant(installed.packageName, "gamestream"), Row.PluginRouting(installed.packageName),
            Row.PluginInstall, Row.PluginCheck, Row.PluginInfo(installed.packageName, PluginInfoField.Version),
            Row.PluginInfo(installed.packageName, PluginInfoField.Package), Row.PluginInfo(installed.packageName, PluginInfoField.Signer),
        ), settingsInnerRows(page, model))
        val opened = reduce(model.onSetting(Row.SettingsTile(page)), Meaning.Activate).first
        assertEquals(Row.PluginGrant(installed.packageName, "gamestream"), settingsFocus(opened))
        assertEquals(RowText("Package", installed.packageName), rowText(Row.PluginInfo(installed.packageName, PluginInfoField.Package), model))
        assertEquals(ApkPluginCopy.catalogDetail, settingsInnerDetail(page, model))
        assertEquals(ApkPluginCopy.unsupported, settingsInnerDetail(page, model.copy(apkPlugins = listOf(installed.copy(supported = false)))))
        assertEquals("Share paired computers", rowText(Row.PluginGrant(installed.packageName, "gamestream"), model).label)
        assertEquals("Check for updates", rowText(Row.PluginCheck, model).label)
    }

    @Test fun anUnavailableInstallShowsItsStateAndOpeningTheCatalogFocusesCheckAgain() {
        val model = PickerModel(count = 4, rowsPerPage = 1, pluginCatalog = PluginCatalogSetting(PluginCatalogStatus.NotPublished))
        val opened = reduce(model.onSetting(Row.SettingsTile(SettingsInner.PluginCatalog)), Meaning.Activate).first
        assertEquals(Row.PluginCheck, settingsFocus(opened))
        assertEquals(RowText("Install", "Not available yet"), rowText(Row.PluginInstall, model))
        assertEquals(RowControl.Value("Not available yet"), rowControl(Row.PluginInstall, model))
        assertEquals("Check again", rowText(Row.PluginCheck, model).label)
        val ready = model.copy(pluginCatalog = PluginCatalogSetting(PluginCatalogStatus.Ready))
        val actionable = reduce(ready.onSetting(Row.SettingsTile(SettingsInner.PluginCatalog)), Meaning.Activate).first
        assertEquals(Row.PluginInstall, settingsFocus(actionable))
    }

    @Test fun successfulInstallAndDiscoveryInEitherOrderOpenSharingWithoutStealingLaterFocus() {
        val installed = plugin.copy(packageName = ApkPluginCopy.catalogPackage)
        val initial = PickerModel(count = 4, rowsPerPage = 1, pluginCatalog = PluginCatalogSetting(PluginCatalogStatus.Installing))
            .onSetting(Row.SettingsTile(SettingsInner.PluginCatalog))
        val catalog = reduce(initial, Meaning.Activate).first
        for (discoveryFirst in listOf(true, false)) {
            val first = if (discoveryFirst) catalog.copy(apkPlugins = listOf(installed))
                else catalog.copy(pluginCatalog = PluginCatalogSetting(PluginCatalogStatus.Installed, installed = true))
            assertEquals(SettingsInner.PluginCatalog, afterApkPluginInstall(catalog, first).settings?.inner)
            val both = first.copy(apkPlugins = listOf(installed), pluginCatalog = PluginCatalogSetting(PluginCatalogStatus.Installed, installed = true))
            val focused = afterApkPluginInstall(first, both)
            assertEquals(SettingsInner.ApkPlugin(installed.packageName), focused.settings?.inner)
            assertEquals(Row.PluginGrant(installed.packageName, "gamestream"), settingsFocus(focused))
            val moved = focused.copy(settings = focused.settings!!.copy(row = 2))
            assertEquals(moved, afterApkPluginInstall(moved, moved.copy(apkPlugins = listOf(installed))))
            val elsewhere = first.copy(settings = null)
            assertNull(afterApkPluginInstall(elsewhere, both.copy(settings = null)).settings)
        }
    }

    @Test fun installReturnsOnceTheInstalledPluginIsGone() {
        assertFalse(PluginCatalogSetting(PluginCatalogStatus.Installed, installed = true).canInstall)
        assertTrue(PluginCatalogSetting(PluginCatalogStatus.Installed, installed = false).canInstall)
        assertTrue(PluginCatalogSetting(PluginCatalogStatus.Current, installed = false).canInstall)
    }

    @Test fun onlyFoldcadeMoonlightClaimsBothScreens() {
        val model = PickerModel(count = 4, rowsPerPage = 1, apkPlugins = listOf(plugin))
        assertNull(settingsInnerDetail(SettingsInner.ApkPlugin(plugin.packageName), model))
        assertEquals(ApkPluginCopy.catalogDetail, settingsInnerDetail(SettingsInner.ApkPlugin(ApkPluginCopy.catalogPackage), model))
    }
}
