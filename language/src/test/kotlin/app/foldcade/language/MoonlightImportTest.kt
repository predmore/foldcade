package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonlightImportTest {
    private val hostA = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6"
    private val hostB = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"

    private fun discovered() = listOf(
        MoonlightDiscoveredApp(hostA, "Living room", "881448767", "Desktop"),
        MoonlightDiscoveredApp(hostA, "Living room", "123", "Celeste"),
        MoonlightDiscoveredApp(hostB, "Office", "7", "Hades"),
    )

    @Test
    fun theSheetListsEveryHostAndStartsChecked() {
        val sheet = moonlightImportSheet(discovered(), HostScreen.Bottom)
        assertEquals(HostScreen.Bottom, sheet?.screen)
        assertEquals(MoonlightSheetTarget.App, sheet?.target)
        assertEquals("Desktop", sheet?.focusedApp()?.label)
        assertTrue(sheet!!.apps.all { it.checked })
        val sections = moonlightSheetSections(sheet.apps)
        assertEquals(listOf("Living room", "Office"), sections.map { it.hostName })
        assertEquals(listOf("Desktop", "Celeste"), sections[0].rows.map { it.app.label })
        assertEquals(listOf("Hades"), sections[1].rows.map { it.app.label })
        assertEquals(MOONLIGHT_FOLDER_ID, "home.moonlight")
        assertNull(moonlightImportSheet(emptyList(), HostScreen.Top))
    }

    @Test
    fun focusWalksTheAppsThenTheButtonsAndDoesNotWrap() {
        val sheet = moonlightImportSheet(discovered(), HostScreen.Bottom)!!
        val desktop = applyMoonlightSheet(sheet, Meaning.MoveDown).first
        assertEquals("Celeste", desktop.focusedApp()?.label)
        val hades = applyMoonlightSheet(desktop, Meaning.MoveDown).first
        assertEquals("Hades", hades.focusedApp()?.label)
        val import = applyMoonlightSheet(hades, Meaning.MoveDown).first
        assertEquals(MoonlightSheetTarget.Import, import.target)
        val notNow = applyMoonlightSheet(import, Meaning.MoveRight).first
        assertEquals(MoonlightSheetTarget.NotNow, notNow.target)
        val stayed = applyMoonlightSheet(notNow, Meaning.MoveRight).first
        assertEquals(MoonlightSheetTarget.NotNow, stayed.target)
        val backToImport = applyMoonlightSheet(stayed, Meaning.MoveLeft).first
        assertEquals(MoonlightSheetTarget.Import, backToImport.target)
        val lastApp = applyMoonlightSheet(backToImport, Meaning.MoveUp).first
        assertEquals("Hades", lastApp.focusedApp()?.label)
        val held = applyMoonlightSheet(sheet, Meaning.MoveUp).first
        assertEquals(0, held.index)
        val shoulders = applyMoonlightSheet(import, Meaning.LeftPanel).first
        assertEquals(MoonlightSheetTarget.Import, shoulders.target)
    }

    @Test
    fun importAddsCheckedAppsToAllAppsAndTheMoonlightFolder() {
        val sheet = moonlightImportSheet(discovered(), HostScreen.Bottom)!!
        val celeste = applyMoonlightSheet(applyMoonlightSheet(sheet, Meaning.MoveDown).first, Meaning.Activate).first
        assertFalse(celeste.apps[1].checked)
        val onImport = celeste.copy(index = celeste.apps.size)
        val kept = MoonlightPlacement(
            remoteKey = "already|1",
            allApps = true,
            moonlightFolder = true,
        )
        val model = PickerModel(
            count = 1,
            rowsPerPage = 1,
            moonlightSheet = onImport,
            moonlightPlacements = listOf(kept),
        )
        val (imported, effect) = reduce(model, Meaning.Activate)
        val confirm = effect as Effect.ConfirmMoonlightImport
        assertEquals(listOf("Desktop", "Hades"), confirm.checked.map { it.label })
        assertNull(imported.moonlightSheet)
        assertEquals(MoonlightSource.ImportedList, imported.moonlightSource)
        assertTrue(imported.moonlightImportConfirmed)
        assertEquals(
            listOf(kept.remoteKey, celeste.apps[0].remoteKey, celeste.apps[2].remoteKey).sorted(),
            imported.moonlightPlacements.map { it.remoteKey },
        )
        assertTrue(imported.moonlightPlacements.all { it.allApps && it.moonlightFolder })
        assertFalse(imported.moonlightPlacements.any { it.remoteKey == celeste.apps[1].remoteKey })
    }

    @Test
    fun notNowSkipsAndLeavesPlacementsAlone() {
        val sheet = moonlightImportSheet(discovered(), HostScreen.Top)!!
        val kept = listOf(
            MoonlightPlacement("kept|9", allApps = true, moonlightFolder = true),
        )
        val model = PickerModel(
            count = 1,
            rowsPerPage = 1,
            moonlightSource = MoonlightSource.PinnedShortcuts,
            moonlightPlacements = kept,
            moonlightSheet = sheet,
        )
        val (skipped, effect) = reduce(model, Meaning.Back)
        assertEquals(Effect.SkipMoonlightImport, effect)
        assertNull(skipped.moonlightSheet)
        assertEquals(MoonlightSource.PinnedShortcuts, skipped.moonlightSource)
        assertEquals(kept, skipped.moonlightPlacements)
        assertFalse(skipped.moonlightImportConfirmed)
        val onNotNow = reduce(model.copy(moonlightSheet = sheet.copy(index = sheet.apps.size + 1)), Meaning.Activate)
        assertEquals(Effect.SkipMoonlightImport, onNotNow.second)
        assertEquals(kept, onNotNow.first.moonlightPlacements)
    }

    @Test
    fun switchingTheSourceNeverDeletesPlacements() {
        val kept = listOf(
            MoonlightPlacement("$hostA|123", allApps = true, moonlightFolder = true),
        )
        val opened = PickerModel(
            count = 1,
            rowsPerPage = 1,
            moonlightSource = MoonlightSource.ImportedList,
            moonlightPlacements = kept,
            moonlightImportConfirmed = true,
        ).onSetting(Row.MoonlightSource)
        assertEquals(
            "Moonlight  Imported list",
            rowLabel(Row.MoonlightSource, opened),
        )
        val pinned = reduce(opened, Meaning.Activate).first
        assertEquals(MoonlightSource.PinnedShortcuts, pinned.moonlightSource)
        assertEquals("Moonlight  Pinned shortcuts only", rowLabel(Row.MoonlightSource, pinned))
        assertEquals(kept, pinned.moonlightPlacements)
        val back = reduce(pinned, Meaning.Activate).first
        assertEquals(MoonlightSource.ImportedList, back.moonlightSource)
        assertEquals(kept, back.moonlightPlacements)
        assertEquals(opened.settings, back.settings)
    }

    @Test
    fun theSourceRowOpensTheSheetUntilAnImportExists() {
        val opened = PickerModel(count = 1, rowsPerPage = 1).onSetting(Row.MoonlightSource)
        val (stayed, effect) = reduce(opened, Meaning.Activate)
        assertEquals(Effect.ReviewMoonlightImport, effect)
        assertEquals(MoonlightSource.PinnedShortcuts, stayed.moonlightSource)
        assertTrue(stayed.moonlightPlacements.isEmpty())
        val shoulders = reduce(stayed.copy(moonlightSheet = moonlightImportSheet(discovered(), HostScreen.Bottom)), Meaning.RightPanel).first
        assertEquals(MoonlightSheetTarget.App, shoulders.moonlightSheet?.target)
    }

    @Test
    fun storedAppsRoundTripAndBlankRowsDrop() {
        val apps = listOf(
            MoonlightStoredApp(hostA, "881448767", "Desk|top"),
            MoonlightStoredApp(hostB, "7", "Ha\u001fdes"),
        )
        val encoded = encodeMoonlightApps(apps)
        val decoded = decodeMoonlightApps(encoded)
        assertEquals(hostA, decoded[0].hostUuid)
        assertEquals("881448767", decoded[0].appId)
        assertEquals("Desk|top", decoded[0].label)
        assertEquals("Ha des", decoded[1].label)
        assertTrue(decodeMoonlightApps(null).isEmpty())
        assertTrue(decodeMoonlightApps("nope").isEmpty())
        val keys = moonlightPlacementKeys(
            listOf(MoonlightPlacement("b|2", allApps = true, moonlightFolder = true)),
        )
        val restored = moonlightPlacementsFromKeys(keys + "a|1")
        assertEquals(listOf("a|1", "b|2"), restored.map { it.remoteKey })
        assertTrue(restored.all { it.allApps && it.moonlightFolder })
    }
}
