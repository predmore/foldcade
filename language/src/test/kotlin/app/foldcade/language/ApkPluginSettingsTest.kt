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
}
