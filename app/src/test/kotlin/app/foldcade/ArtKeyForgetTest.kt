package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

/** Settings › Library › SteamGridDB forgets a saved key only on the dialog's Forget key. */
class ArtKeyForgetTest {
    private val forget = Effect.DialogChoice(DialogButton.ForgetKey, DialogKind.ForgetArtKey)
    private val keep = Effect.DialogChoice(DialogButton.KeepKey, DialogKind.ForgetArtKey)

    private fun shell() = ShellController(SessionStore(MemoryPrefs()), PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        .also { it.setArtKeySaved(true) }

    /** Start, then down to Library, into its rows, and down to SteamGridDB. */
    private fun onArtKeyRow(shell: ShellController) {
        shell.onMeaning(Meaning.Menu, HostScreen.Bottom)
        val category = settingsCategories(shell.model).indexOf(SettingsCategory.Library)
        repeat(category) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        val row = settingsRows(SettingsCategory.Library, shell.model).indexOf(Row.ArtKey)
        repeat(row) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        assertEquals(row, shell.model.settings?.row)
        assertEquals(Copy.forgetKey, rowText(Row.ArtKey, shell.model).value)
    }

    @Test fun oneActivationOnlyAsksAndEveryWayOutKeepsTheKey() {
        val shell = shell()
        onArtKeyRow(shell)
        val effects = mutableListOf<Effect?>()
        effects += shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(DialogKind.ForgetArtKey, shell.model.dialog?.kind)
        // The shoulders, Start and the D-pad stay on the dialog without choosing.
        for (away in listOf(Meaning.LeftPanel, Meaning.RightPanel, Meaning.Menu, Meaning.MoveDown, Meaning.MoveRight)) {
            effects += shell.onMeaning(away, HostScreen.Bottom)
            assertEquals(DialogKind.ForgetArtKey, shell.model.dialog?.kind)
        }
        // Home leaves the dialog waiting for a choice, rather than making one.
        shell.goHome()
        assertEquals(DialogKind.ForgetArtKey, shell.model.dialog?.kind)
        effects += shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertNull(shell.model.dialog)
        assertNotNull(shell.model.settings)
        // A held A that opened the dialog, or a second press, lands on Keep key.
        effects += shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        effects += shell.onMeaning(Meaning.Activate, HostScreen.Bottom, repeatCount = 1)
        assertNull(shell.model.dialog)
        // Settings opened with Start is on the top screen, and so is its dialog: a tap there on Keep key.
        effects += shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(HostScreen.Top, shell.model.dialog?.screen)
        effects += shell.touchDialog(1, HostScreen.Top)
        assertNull(shell.model.dialog)
        assertTrue(effects.none { it == forget })
        assertEquals(listOf(keep, keep, keep), effects.filterNotNull())
        assertTrue(shell.model.artKeySaved)
        assertEquals(Copy.forgetKey, rowText(Row.ArtKey, shell.model).value)
    }

    @Test fun touchesOnSettingsUnderTheDialogDoNothing() {
        val shell = shell()
        onArtKeyRow(shell)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        val dialog = shell.model.dialog!!
        assertEquals(DialogKind.ForgetArtKey, dialog.kind)
        // Focus on Forget key, so a stray Activate would forget the key.
        shell.onMeaning(Meaning.MoveLeft, HostScreen.Bottom)
        val page = shell.model.settings!!
        val sound = settingsCategories(shell.model).indexOf(SettingsCategory.Sound)
        val effects = mutableListOf<Effect?>()
        for (screen in HostScreen.entries) {
            effects += shell.touchSettingsCategory(sound, screen)
            effects += shell.touchSettingsCategory(page.category, screen)
            settingsRows(SettingsCategory.Library, shell.model).indices.forEach { row ->
                effects += shell.touchSettingsRow(row, screen)
            }
            effects += shell.touchPanel(0, screen)
        }
        // The back pill beside the page, on the other screen.
        effects += shell.closeSettings()
        assertTrue(effects.all { it == null })
        assertEquals(page, shell.model.settings)
        assertEquals(dialog.copy(index = 0), shell.model.dialog)
        assertTrue(shell.model.artKeySaved)
        // The dialog's own buttons still answer.
        assertEquals(keep, shell.touchDialog(1, dialog.screen))
        assertNull(shell.model.dialog)
        assertEquals(page, shell.model.settings)
    }

    @Test fun forgetKeyIsTheOneChoiceThatForgets() {
        val shell = shell()
        onArtKeyRow(shell)
        assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        assertNull(shell.onMeaning(Meaning.MoveLeft, HostScreen.Bottom))
        assertEquals(forget, shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        assertNull(shell.model.dialog)
        // The app clears the key from the store, then marks it gone.
        shell.setArtKeySaved(false)
        assertEquals(Copy.addKey, rowText(Row.ArtKey, shell.model).value)

        shell.setArtKeySaved(true)
        assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        assertNull(shell.touchDialog(0, HostScreen.Bottom))
        assertEquals(forget, shell.touchDialog(0, HostScreen.Top))
    }
}
