package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class SettingsReturnTest {
    private fun shell() = ShellController(
        SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()),
    )

    private fun ShellController.openSettingsRow(row: Row): SettingsPage {
        onMeaning(Meaning.LeftPanel, HostScreen.Top)
        repeat(leftRows().indexOf(Row.Settings)) { onMeaning(Meaning.MoveDown, HostScreen.Top) }
        onMeaning(Meaning.Activate, HostScreen.Top)
        // A row on a tile's page, as Add a RomM server is on Library's Add, opens that page first.
        fun holds(rows: List<Row>) = row in rows ||
            rows.any { it is Row.SettingsTile && row in settingsInnerRows(it.page, model) }
        val category = settingsCategories(model).indexOfFirst { holds(settingsRows(it, model)) }
        touchSettingsCategory(category, HostScreen.Top)
        val rows = settingsRows(settingsCategories(model)[category], model)
        val tile = rows.indexOfFirst { it is Row.SettingsTile && row in settingsInnerRows(it.page, model) }
        if (row !in rows) touchSettingsRow(tile, HostScreen.Top)
        val inner = model.settings?.inner
        val shown = if (inner != null) settingsInnerRows(inner, model) else rows
        touchSettingsRow(shown.indexOf(row), HostScreen.Top)
        return model.connectReturnSettings!!
    }

    @Test fun successfulSaveReturnsToTheSettingsRowAndConsumesItsReturnTarget() {
        for (row in listOf(Row.AddRomm, Row.ArtKey)) {
            val shell = shell()
            val page = shell.openSettingsRow(row)
            // The activity handles the reducer's opening effect before the user can leave.
            if (row == Row.AddRomm) shell.openAddRomm() else shell.openArtKey()
            assertEquals(page, shell.model.connectReturnSettings)
            shell.closeConnect()
            assertEquals(page, shell.model.settings)
            assertNull(shell.model.panel)
            assertFalse(shell.model.connectOpen)
            assertNull(shell.model.connectReturnSettings)
        }
    }

    @Test fun replacingTheFormCannotResurrectSettingsOnALaterConnection() {
        val shell = shell()
        shell.openSettingsRow(Row.AddRomm)
        shell.presentSavePicker(SavePickerState(
            SavePickerTarget("game", "romm", "1", "melonds", 1), "Game", "melonDS",
            SaveHealth.NotSyncing(SaveIssue.NoFolder), loading = false,
        ))
        assertFalse(shell.model.connectOpen)
        assertNull(shell.model.connectReturnSettings)
        shell.presentSavePicker(null)
        shell.openConnect("https://example.com")
        shell.closeConnect()
        assertNull(shell.model.settings)
    }
}
