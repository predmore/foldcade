package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class PageCompanionTest {
    private val home = PickerModel(count = 4, rowsPerPage = 3)

    @Test fun formsGiveTheOtherScreenACompanionInEveryLayout() {
        for (layout in ScreenLayout.entries) for (kind in ConnectKind.entries) {
            val form = home.copy(layout = layout, connectOpen = true, connectKind = kind, connectScreen = HostScreen.Top)
            assertEquals(if (kind == ConnectKind.ArtKey) CompanionPage.ArtKey else CompanionPage.Romm, form.companionPageOn(HostScreen.Bottom))
            assertNull(form.companionPageOn(HostScreen.Top))
            // A game holding the top moves the form and its companion together.
            val moved = form.onShownScreens { it == HostScreen.Bottom }
            assertNull(moved.companionPageOn(HostScreen.Bottom))
            assertEquals(form.companionPageOn(HostScreen.Bottom), moved.companionPageOn(HostScreen.Top))
        }
    }

    @Test fun aFormNamesItselfRatherThanItsReturnPage() {
        val form = home.copy(connectOpen = true, connectScreen = HostScreen.Top, connectKind = ConnectKind.ArtKey,
            settings = SettingsPage(HostScreen.Top))
        assertEquals(CompanionPage.ArtKey, form.companionPageOn(HostScreen.Bottom))
        assertEquals(CompanionPage.Settings, form.copy(connectOpen = false).companionPageOn(HostScreen.Bottom))
    }

    @Test fun aTreeHandoffReplacesTheOtherScreenAndTakesNoPageKeys() {
        for (screen in HostScreen.entries) {
            val guide = FolderGuide(screen, "azahar", opensThere = true)
            val model = home.copy(settings = SettingsPage(HostScreen.Top), folderGuide = guide)
            assertNull(model.folderGuideOn(screen))
            assertEquals(guide, model.folderGuideOn(screen.other()))
            for (key in Meaning.entries) {
                assertEquals(model to null, reduce(model, key, screen.other()))
            }
            assertNull(model.copy(folderGuide = null).folderGuideOn(screen.other()))
        }
    }

    @Test fun everyR1AndroidTileClosesTheDrawerBeforeHandingOff() {
        val opened = reduce(home.copy(focus = GridFocus(cellIndex = 2)), Meaning.RightPanel).first
        for (setting in QuickSetting.entries) {
            val panel = opened.panel!!
            val onTile = opened.copy(panel = panel.copy(index = panelRows(panel, opened).indexOf(Row.QuickTile(setting))))
            val (next, effect) = reduce(onTile, Meaning.Activate)
            assertEquals(Effect.OpenAndroidSetting(setting.androidSetting()), effect)
            assertNull(next.panel)
            assertEquals(panel.grid, next.focus)
        }
    }
}
