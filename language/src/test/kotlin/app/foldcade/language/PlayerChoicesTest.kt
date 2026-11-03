package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class PlayerChoicesTest {
    private val first = PlayerChoice("pizza", label = "Pizza Boy GBA")
    private val second = PlayerChoice("retroarch.gba", "mgba", "mGBA (RetroArch)")
    private val gone = PlayerChoice("myboy", label = "My Boy!")
    private val choice = PlayerChoiceRow(listOf(first, second), inherited = first.label)
    private val model = PickerModel(count = 3, rowsPerPage = 1)

    @Test fun defaultAndAutomaticShowWhatTheyWillUse() {
        assertEquals("Default · Pizza Boy GBA", choice.value())
        assertEquals("Auto · Pizza Boy GBA", choice.copy(system = true).value())
        assertEquals("Automatic", choice.copy(system = true, inherited = null).value())
        assertEquals("Default", choice.copy(inherited = null).value())
        assertEquals("mGBA (RetroArch)", choice.copy(selected = second).value())
    }

    @Test fun missingChoiceStaysUntilSteppedAwayAndDefaultClearsIt() {
        val missing = choice.copy(selected = gone, selectedUsable = false)
        assertEquals("My Boy! · not installed", missing.value())
        assertEquals(listOf(null, first, second, gone), missing.values)
        assertNull(missing.step(1))
        assertEquals(listOf(null, first, second), missing.copy(selected = null).values)
        assertEquals(second, missing.step(-1))
        assertEquals(first, choice.step(1))
        assertEquals(second, choice.step(-1))
        assertNull(choice.copy(selected = second).step(1))
        // A generic RetroArch choice made while cores were unknown remains usable later.
        val generic = PlayerChoice("retroarch.gba", label = "RetroArch")
        val knownNow = choice.copy(selected = generic, selectedUsable = true)
        assertEquals("RetroArch", knownNow.value())
        assertEquals(generic, knownNow.values.last())
    }

    @Test fun visibilityRequiresTwoOptionsOrASavedChoice() {
        assertFalse(choice.copy(options = listOf(first)).visible)
        assertFalse(choice.copy(options = emptyList()).visible)
        assertTrue(choice.visible)
        assertTrue(choice.copy(options = emptyList(), selected = gone).visible)
        val systems = listOf(
            SystemPlayerChoice("z", "Zed", choice),
            SystemPlayerChoice("a", "Alpha", choice),
            SystemPlayerChoice("hidden", "Hidden", choice.copy(options = listOf(first))),
        )
        val withSystems = model.copy(systemPlayers = systems)
        assertEquals(listOf(Row.SystemPlayer("a"), Row.SystemPlayer("z")), settingsInnerRows(SettingsInner.Systems, withSystems))
        // No system with a choice: no Systems row, and no Emulators category for it alone.
        assertTrue(settingsRows(SettingsCategory.Players, model).isEmpty())
        assertFalse(SettingsCategory.Players in settingsCategories(model.copy(systemPlayers = systems.takeLast(1))))
        assertEquals(listOf(Row.Systems), settingsRows(SettingsCategory.Players, withSystems))
        assertEquals(RowControl.Opens, rowControl(Row.Systems, withSystems))
        val empty = withSystems.openSettings(HostScreen.Top, SettingsCategory.Players)
        val opened = reduce(reduce(empty, Meaning.MoveRight).first, Meaning.Activate).first
        assertEquals(SettingsInner.Systems, settingsShown(opened.settings!!, opened).first.inner)
        assertEquals(Row.Systems, settingsFocus(reduce(opened, Meaning.Back).first))
    }

    @Test fun openWithIsAChoiceAfterTheVerbTilesAndBeforePlayTime() {
        val tile = TileActions(game = "menu-target", art = true, saves = true, playTime = 30, openWith = choice)
        val rows = actionRows(tile)
        assertEquals(listOf(Row.ChooseArt, Row.Saves, Row.OpenWith, Row.PlayTime), rows)
        val shown = model.copy(tileActions = tile)
        assertEquals(RowControl.Choice("Default · Pizza Boy GBA", short = "Pizza Boy GBA"), rowControl(Row.OpenWith, shown))
        val panel = SidePanel(Side.Left, HostScreen.Top, PanelLevel.Actions, rows.indexOf(Row.OpenWith), GridFocus(cellIndex = 2))
        val menu = shown.copy(panel = panel)
        assertEquals(Effect.SetGamePlayerChoice("menu-target", first), reduce(menu, Meaning.Activate).second)
        assertEquals(Effect.SetGamePlayerChoice("menu-target", second), reduce(menu, Meaning.MoveLeft).second)
        assertEquals(Effect.SetGamePlayerChoice("menu-target", first), reduce(menu, Meaning.MoveRight).second)
        assertNull(reduce(menu, Meaning.Activate, repeatCount = 1).second)
        val kept = menu.copy(tileActions = tile.copy(kept = true), focus = GridFocus(cellIndex = 0))
        assertEquals(Effect.SetGamePlayerChoice("menu-target", first), reduce(kept, Meaning.Activate).second)
    }

    @Test fun systemsOpensAsARowAndHeldADoesNotChangeItsFirstSystem() {
        val shown = model.copy(systemPlayers = listOf(SystemPlayerChoice("gba", "Game Boy Advance", choice.copy(system = true))))
        val page = shown.openSettings(HostScreen.Top, SettingsCategory.Players)
        assertFalse(reduce(page, Meaning.Activate, repeatCount = 1).first.settings!!.onRows)
        val onRow = reduce(page, Meaning.MoveRight).first
        val opened = reduce(onRow, Meaning.Activate).first
        assertEquals(SettingsInner.Systems, opened.settings!!.inner)
        assertEquals(PlayerChoiceCopy.systems, settingsInnerTitle(SettingsInner.Systems, opened))
        assertNull(reduce(opened, Meaning.Activate, repeatCount = 1).second)
        assertEquals(Effect.SetSystemPlayerChoice("gba", first), reduce(opened, Meaning.Activate).second)
        assertEquals(Effect.SetSystemPlayerChoice("gba", second), reduce(opened, Meaning.MoveLeft).second)
        assertEquals(RowControl.Choice("Auto · Pizza Boy GBA", short = "Pizza Boy GBA"), rowControl(Row.SystemPlayer("gba"), opened))
        val back = reduce(opened, Meaning.Back).first
        assertNull(back.settings!!.inner)
        assertEquals(Row.Systems, settingsFocus(back))
    }

    @Test fun fallbackNoticeOpensItsSystemsRow() {
        val notice = FoldNotice(PLAYER_CHOICE_SYSTEM_NOTICE + "gba", "Opened", "Fallback", settingsCategory = SettingsCategory.Players)
        // The skipped system choice is still saved, so Systems lists it and Emulators is there.
        val saved = SystemPlayerChoice("gba", "Game Boy Advance", choice.copy(system = true, selected = gone, selectedUsable = false))
        val other = SystemPlayerChoice("a", "Atari 2600", choice.copy(system = true))
        val shown = model.copy(systemPlayers = listOf(other, saved), notices = listOf(notice), panel = SidePanel(Side.Right, HostScreen.Top, PanelLevel.Root, 0, GridFocus()))
        val focused = shown.copy(panel = shown.panel!!.copy(index = panelRows(shown.panel!!, shown).indexOf(Row.Notice(notice.id))))
        val next = reduce(focused, Meaning.Activate).first
        assertEquals(SettingsCategory.Players, settingsCategories(next)[next.settings!!.category])
        assertNull(next.saveSetup)
        assertEquals(SettingsInner.Systems, next.settings!!.inner)
        assertEquals(Row.SystemPlayer("gba"), settingsFocus(next))
        // The missing choice reads in the attention colour, on Systems and in Open with.
        assertEquals(RowControl.Choice("My Boy! · not installed", attention = true), rowControl(Row.SystemPlayer("gba"), next))
        assertEquals(RowControl.Choice("Auto · Pizza Boy GBA", short = "Pizza Boy GBA"), rowControl(Row.SystemPlayer("a"), next))
        val missingGame = model.copy(tileActions = TileActions(game = "g", openWith = choice.copy(selected = gone, selectedUsable = false)))
        assertEquals(RowControl.Choice("My Boy! · not installed", attention = true), rowControl(Row.OpenWith, missingGame))
        assertEquals("RetroArch's mGBA core isn't installed.", PlayerChoiceCopy.unavailable(second))
    }
}
