package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RommConnectionNavigationTest {
    private val connections = listOf(SignedInBackend("romm", "RomM"))
    private val home = PickerModel(count = 1, rowsPerPage = 1, signedIn = connections)

    @Test
    fun aSavedServerHidesTheAddAction() {
        assertEquals(
            listOf(Row.RommConnection("romm", "RomM"), Row.AddFolder),
            libraryRows(connections),
        )
        assertEquals(listOf(Row.AddFolder, Row.AddRomm), libraryRows())
    }

    @Test
    fun aSavedAddressStillShowsItsConnectionWhenTheTokenNeedsRecovery() {
        assertEquals(
            listOf(Row.RommConnection("romm", "RomM"), Row.AddFolder),
            libraryRows(rommSaved = true),
        )
    }

    @Test
    fun openingAConnectionIsSafeAndEditingReturnsToItsActions() {
        for (index in connections.indices) {
            val selected = home.onSetting(Row.RommConnection(connections[index].pluginId, connections[index].label))
            val (opened, effect) = reduce(selected, Meaning.Activate)
            assertNull(effect)
            assertFalse(opened.connectOpen)
            assertEquals(connections[index], opened.panel?.connection)
            val rows = panelRows(opened.panel!!, opened)
            assertEquals("Edit connection", rowLabel(rows[0], opened))
            assertEquals("Sign out", rowLabel(rows[1], opened))

            val (editing, edit) = reduce(opened, Meaning.Activate)
            assertEquals(Effect.EditRommConnection, edit)
            assertTrue(editing.connectOpen)
            assertNull(editing.panel)
            assertEquals(opened.panel, reduce(editing, Meaning.Back).first.panel)
            assertEquals(selected.settings, reduce(opened, Meaning.Back).first.settings)
        }
    }

    @Test
    fun signOutOnlyTargetsTheConnectionWhoseActionsAreOpen() {
        val selected = home.onSetting(Row.RommConnection("romm", "RomM"))
        val opened = reduce(selected, Meaning.Activate).first
        val onSignOut = reduce(opened, Meaning.MoveDown).first
        val (closed, effect) = reduce(onSignOut, Meaning.Activate)
        assertEquals(Effect.ForgetCredentials("romm"), effect)
        assertNull(closed.panel)
        assertFalse(closed.connectOpen)
    }

    @Test
    fun aConnectionOpenedFromSettingsReturnsToTheSameRow() {
        val selected = home.onSetting(Row.RommConnection("romm", "RomM"))
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
