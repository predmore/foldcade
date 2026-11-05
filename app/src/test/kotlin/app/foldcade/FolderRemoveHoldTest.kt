package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

/** A library folder's Remove, on its Settings page, is held, as deleting a download is. */
class FolderRemoveHoldTest {
    private var time = 0L
    private val roms = LibraryFolder("content://roms", "roms", games = 3)

    @Test
    fun holdingARemovesTheFolder() {
        val shell = onRemove()
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(Row.ForgetFolder(roms.uri, roms.label), shell.model.deleteHold?.row)
        // Nothing goes until the hold is done, and the page stays on Remove.
        assertEquals(SettingsInner.Folder(roms.uri), shell.model.settings?.inner)
        time += DELETE_HOLD_MILLIS / 2
        assertNull(shell.tickRecovery())
        assertNotNull(shell.model.deleteHold)
        assertTrue(shell.model.deleteProgress > 0f)
        time += DELETE_HOLD_MILLIS
        assertEquals(Effect.ForgetFolder(roms.uri), shell.tickRecovery())
        assertNull(shell.model.deleteHold)
    }

    @Test
    fun lettingGoEarlyKeepsTheFolder() {
        val shell = onRemove()
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        time += DELETE_HOLD_MILLIS / 2
        assertNull(shell.releaseMeaning(Meaning.Activate, HostScreen.Bottom.name))
        assertNull(shell.model.deleteHold)
        time += DELETE_HOLD_MILLIS
        assertNull(shell.tickRecovery())
        assertEquals(SettingsInner.Folder(roms.uri), shell.model.settings?.inner)
    }

    @Test
    fun leavingTheRowCancels() {
        val shell = onRemove()
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        // Another key cancels the hold and does nothing else.
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertNull(shell.model.deleteHold)
        assertEquals(SettingsInner.Folder(roms.uri), shell.model.settings?.inner)
    }

    @Test
    fun aFolderAddedFromTheAddTileKeepsTheFocusWhereAddWas() {
        // No other source: the new folder's tile lands on Add's place, which the shelf would otherwise hold to Add.
        val shell = ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()), nowMillis = { time })
        shell.onMeaning(Meaning.Menu, HostScreen.Bottom)
        repeat(settingsCategories(shell.model).indexOf(SettingsCategory.Library)) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        assertEquals(Row.SettingsTile(SettingsInner.Add), settingsFocus(shell.model))
        shell.setFolders(listOf(roms))
        assertEquals(Row.SettingsTile(SettingsInner.Folder(roms.uri)), settingsFocus(shell.model))
    }

    private fun onRemove(): ShellController {
        val shell = ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()), nowMillis = { time })
        shell.setFolders(listOf(roms))
        // Start, then down to Library, and into its shelf, whose first tile is the folder.
        shell.onMeaning(Meaning.Menu, HostScreen.Bottom)
        repeat(settingsCategories(shell.model).indexOf(SettingsCategory.Library)) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        val rows = settingsShown(shell.model.settings!!, shell.model).second
        assertEquals(Row.SettingsTile(SettingsInner.Folder(roms.uri)), rows[shell.model.settings!!.row])
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(Row.ForgetFolder(roms.uri, roms.label), settingsFocus(shell.model))
        return shell
    }
}
