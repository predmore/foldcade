package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsHintsTest {
    private val moonlight = PluginRegistry.moonlight.packageName
    private val plugin = ApkPluginSetting(moonlight, "$moonlight.Play", "Foldcade Moonlight", "1", "ab:cd", true, setOf("gamestream"), emptySet(), false)
    private val a = PromptKey.FaceA
    private val b = PromptKey.FaceB

    private fun on(model: PickerModel, category: SettingsCategory, inner: SettingsInner? = null, row: Int = 0, onRows: Boolean = true) =
        model.copy(settings = SettingsPage(HostScreen.Top, category = settingsCategories(model).indexOf(category), row = row, onRows = onRows, inner = inner))

    @Test fun theRailNamesOpenAndClose() {
        val model = on(PickerModel(count = 1, rowsPerPage = 1), SettingsCategory.Personalization, onRows = false)
        assertEquals(listOf(HintKey(a, "Open"), HintKey(b, "Close")), settingsHints(model))
    }

    @Test fun aRowNamesWhatAOrTheDPadDoesThenBack() {
        val base = PickerModel(count = 1, rowsPerPage = 1, apkPlugins = listOf(plugin),
            pluginCatalogs = mapOf(moonlight to PluginCatalogSetting(PluginCatalogStatus.UpdateAvailable, installed = true, latest = "2")))
        val page = SettingsInner.ApkPlugin(moonlight)
        assertEquals(listOf(HintKey(a, "Update"), HintKey(b, "Back")), settingsHints(on(base, SettingsCategory.Plugins, page, row = 0)))
        assertEquals(listOf(HintKey(a, "Turn on"), HintKey(b, "Back")), settingsHints(on(base, SettingsCategory.Plugins, page, row = 1)))
        // A download under way names only B: A does nothing until it finishes.
        val busy = base.copy(pluginCatalogs = mapOf(moonlight to PluginCatalogSetting(PluginCatalogStatus.Downloading, installed = true)))
        assertEquals(listOf(HintKey(b, "Back")), settingsHints(on(busy, SettingsCategory.Plugins, page, row = 0)))
        // A choice steps with the D-pad's sides; a way in opens.
        val look = on(base, SettingsCategory.Personalization, row = settingsRows(SettingsCategory.Personalization, base).indexOf(Row.MotionSpeed))
        assertEquals(listOf(HintKey(PromptKey.DpadSides, "Change"), HintKey(b, "Back")), settingsHints(look))
        val labels = on(base, SettingsCategory.Personalization, row = settingsRows(SettingsCategory.Personalization, base).indexOf(Row.ButtonLabels))
        assertEquals(listOf(HintKey(a, "Set up"), HintKey(b, "Back")), settingsHints(labels))
        // Android owns the all-files switch: A opens its page, so the legend says Open, not Turn on.
        val library = on(base, SettingsCategory.Library, row = settingsRows(SettingsCategory.Library, base).indexOf(Row.FindGames))
        assertEquals(listOf(HintKey(a, "Open"), HintKey(b, "Back")), settingsHints(library))
        val plugins = on(base, SettingsCategory.Plugins, row = 0)
        assertEquals(listOf(HintKey(a, "Open"), HintKey(b, "Back")), settingsHints(plugins))
    }
}
