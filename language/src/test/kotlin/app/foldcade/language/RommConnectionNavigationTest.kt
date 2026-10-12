package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RommConnectionNavigationTest {
    private val connections = listOf(SignedInBackend("romm", "RomM"), SignedInBackend("romm#2", "two.example"))
    private val sources = PickerModel(
        count = 1,
        rowsPerPage = 1,
        signedIn = connections,
        panel = SidePanel(Side.Left, HostScreen.Top, PanelLevel.Library, 0, GridFocus()),
    )

    @Test
    fun eachSavedServerHasOneEntryAndASingleAddAction() {
        assertEquals(
            listOf(Row.RommConnection("romm", "RomM"), Row.RommConnection("romm#2", "two.example"), Row.AddFolder, Row.AddRomm),
            libraryRows(connections),
        )
        assertEquals(listOf(Row.AddFolder, Row.AddRomm), libraryRows())
    }

    @Test
    fun openingAConnectionIsSafeAndEditingReturnsToItsActions() {
        for (index in connections.indices) {
            val selected = sources.copy(panel = sources.panel!!.copy(index = index))
            val (opened, effect) = reduce(selected, Meaning.Activate)
            assertNull(effect)
            assertFalse(opened.connectOpen)
            assertEquals(connections[index], opened.panel?.connection)
            val rows = panelRows(opened.panel!!, opened)
            assertEquals("Edit connection", rowLabel(rows[0], opened))
            assertEquals("Sign out", rowLabel(rows[1], opened))

            val (editing, edit) = reduce(opened, Meaning.Activate)
            assertEquals(Effect.EditRommConnection(connections[index].pluginId), edit)
            assertTrue(editing.connectOpen)
            assertFalse(editing.connectAnother)
            assertNull(editing.panel)
            assertEquals(opened.panel, reduce(editing, Meaning.Back).first.panel)
            assertEquals(selected.panel, reduce(opened, Meaning.Back).first.panel)
        }
    }

    @Test
    fun signOutOnlyTargetsTheConnectionWhoseActionsAreOpen() {
        val selected = sources.copy(panel = sources.panel!!.copy(index = 1))
        val opened = reduce(selected, Meaning.Activate).first
        val onSignOut = reduce(opened, Meaning.MoveDown).first
        val (closed, effect) = reduce(onSignOut, Meaning.Activate)
        assertEquals(Effect.ForgetCredentials("romm#2"), effect)
        assertNull(closed.panel)
        assertFalse(closed.connectOpen)
    }

    @Test
    fun aConnectionOpenedFromSettingsReturnsToTheSameRow() {
        val selected = sources.copy(panel = null).onSetting(Row.RommConnection("romm#2", "two.example"))
        val (opened, effect) = reduce(selected, Meaning.Activate)
        assertNull(effect)
        assertNull(opened.settings)
        assertEquals(PanelLevel.RommConnection, opened.panel?.level)
        val editing = reduce(opened, Meaning.Activate).first
        val actions = reduce(editing, Meaning.Back).first
        val returned = reduce(actions, Meaning.Back).first
        assertNull(returned.panel)
        assertEquals(selected.settings, returned.settings)
    }
}
