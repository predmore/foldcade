package app.foldcade.language

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticesTest {
    @Test
    fun aMergedSaveNoticeOpensSetUpSavesAtTheFirstEmulatorThatNeedsYou() {
        val notice = FoldNotice("foldcade:save-setup:emulators", Copy.savesNotSyncing, Copy.savesNotSyncingEmulatorsBody(2),
            settingsCategory = SettingsCategory.Players)
        val base = model(listOf(notice)).copy(
            count = 4,
            focus = GridFocus(cellIndex = 2, lastColumn = 1),
            playerSaves = listOf(
                PlayerSaveSetting("melonds", "melonDS", false, installed = true),
                PlayerSaveSetting("armsx2", "ARMSX2", false, installed = true),
            ),
        )
        for (screen in HostScreen.entries) {
            val right = reduce(base, Meaning.RightPanel, screen).first
            val focused = right.copy(panel = right.panel!!.copy(index = panelRows(right.panel!!, right).indexOf(Row.Notice(notice.id))))
            val (opened, effect) = reduce(focused, Meaning.Activate, screen)
            assertNull(effect)
            assertNull(opened.panel)
            assertNull(opened.dialog)
            assertNull(opened.settings)
            // A to Z, as Settings › Emulators lists them.
            assertEquals("armsx2", opened.saveSetup!!.focus)
            assertEquals(focused.panel!!.screen, opened.saveSetup!!.screen)
            assertEquals(true, panelHints(focused)!!.any { it.label == Copy.setUp })
            val closed = reduce(opened, Meaning.Back, screen).first
            assertNull(closed.saveSetup)
            assertNull(closed.settings)
            assertEquals(base.focus, closed.focus)
            assertEquals(Effect.DismissNotice(notice.id), reduce(focused, Meaning.ActivateBottom, screen).second)
            assertEquals(false, Row.ClearNotices in rightRows(listOf(notice)))
        }
    }

    @Test
    fun oneEmulatorsSaveNoticeOpensSetUpSavesOnItsOwnTile() {
        val notice = FoldNotice("foldcade:save-setup:melonds", "melonDS saves aren’t syncing", Copy.savesNotSyncingBody, savePlayerId = "melonds")
        val base = model(listOf(notice)).copy(playerSaves = listOf(
            PlayerSaveSetting("armsx2", "ARMSX2", false, installed = true),
            PlayerSaveSetting("melonds", "melonDS", false, installed = true),
        ))
        val right = reduce(base, Meaning.RightPanel).first
        val focused = right.copy(panel = right.panel!!.copy(index = panelRows(right.panel!!, right).indexOf(Row.Notice(notice.id))))
        val (opened, effect) = reduce(focused, Meaning.Activate)
        assertNull(effect)
        assertNull(opened.panel)
        assertEquals("melonds", opened.saveSetup!!.focus)
        // The A that opened it, still held, does not go on to open the picker.
        assertNull(reduce(opened, Meaning.Activate, repeatCount = 1).second)
        assertEquals(Effect.ChoosePlayerSave("melonds"), reduce(opened, Meaning.Activate).second)
    }

    @Test
    fun theSavesFoundNoticeOpensSetUpSavesOnTheFirstEmulatorFoundNotFindSaves() {
        val notice = FoldNotice("foldcade:saves-found", Copy.savesFoundTitle, Copy.savesFoundBody(listOf("melonDS" to "saves/nds")), savePlayerId = "melonds")
        val base = model(listOf(notice)).copy(playerSaves = listOf(
            PlayerSaveSetting("armsx2", "ARMSX2", false, installed = true),
            PlayerSaveSetting("melonds", "melonDS", true, installed = true, foundIn = "saves/nds"),
            PlayerSaveSetting("retroarch.nes", "RetroArch", false, installed = true, findable = true),
        ))
        // Find saves leads the strip, and ARMSX2 is the first that needs you; neither is where it opens.
        assertEquals(FIND_SAVES_TILE, base.openSaveSetup(HostScreen.Top).saveSetup!!.focus)
        val right = reduce(base, Meaning.RightPanel).first
        val focused = right.copy(panel = right.panel!!.copy(index = panelRows(right.panel!!, right).indexOf(Row.Notice(notice.id))))
        assertEquals(true, panelHints(focused)!!.any { it.label == Copy.setUp })
        val (opened, effect) = reduce(focused, Meaning.Activate)
        assertNull(effect)
        val page = opened.saveSetup!!
        assertEquals("melonds", page.focus)
        val tile = saveSetupTiles(opened, page).first { it.key == "melonds" }
        assertEquals("Found in saves/nds.", SaveSetupCopy.stepBody(tile))
    }

    @Test
    fun aSaveNoticeForAnEmulatorNotOnTheStripStillOpensItsOwnFixAndCanBeDismissed() {
        val notice = FoldNotice("setup", "ARMSX2 saves aren’t syncing", "Set up to keep them on RomM.", savePlayerId = "armsx2")
        val cases = listOf(
            PlayerSaveSetting("armsx2", "ARMSX2", false) to Effect.ChoosePlayerSave("armsx2"),
            PlayerSaveSetting("armsx2", "ARMSX2", false, hostedTitle = "Cards") to Effect.SetUpHostedSaves("armsx2"),
            PlayerSaveSetting("armsx2", "ARMSX2", true, hostedTitle = "Cards") to Effect.SetUpHostedSaves("armsx2"),
            PlayerSaveSetting("armsx2", "ARMSX2", true, saveSyncSteps = "Pick a folder card") to Effect.ShowSaveSync("armsx2"),
            PlayerSaveSetting("armsx2", "ARMSX2", true, setupSteps = "Install firmware") to Effect.ShowPlayerSetup("armsx2"),
        )
        for ((setting, expected) in cases) {
            val opened = reduce(model(listOf(notice)).copy(playerSaves = listOf(setting)), Meaning.RightPanel).first
            val focused = opened.copy(panel = opened.panel!!.copy(index = panelRows(opened.panel!!, opened).indexOf(Row.Notice(notice.id))))
            val (closed, effect) = reduce(focused, Meaning.Activate)
            assertEquals(expected, effect)
            assertNull(closed.panel)
            assertNull(closed.dialog)
            assertEquals(Effect.DismissNotice(notice.id), reduce(focused, Meaning.ActivateBottom).second)
            assertEquals(true, panelHints(focused)!!.any { it.label == Copy.setUp })
            val (missing, fallback) = reduce(focused.copy(playerSaves = emptyList()), Meaning.Activate)
            assertEquals(Effect.ChoosePlayerSave("armsx2"), fallback)
            assertNull(missing.panel)
            assertNull(missing.dialog)
        }
    }

    @Test
    fun anEdenSaveNoticeWithoutAPlayerSaveRowOpensItsFolderPicker() {
        val notice = FoldNotice("setup", "Eden saves aren’t syncing", "Set up to keep them on RomM.", savePlayerId = "eden")
        val opened = reduce(model(listOf(notice)).copy(eden = EdenSetting(fromRomm = false)), Meaning.RightPanel).first
        val focused = opened.copy(panel = opened.panel!!.copy(index = panelRows(opened.panel!!, opened).indexOf(Row.Notice(notice.id))))
        val (closed, effect) = reduce(focused, Meaning.Activate)
        assertEquals(Effect.ChoosePlayerSave("eden"), effect)
        assertNull(closed.panel)
        assertNull(closed.dialog)
        assertEquals(focused.panel!!.grid, closed.focus)
    }
    private val chat = FoldNotice("chat", "Alex", "you on tonight?", "com.discord", postedMillis = 3_000L, openable = true)
    private val mail = FoldNotice("mail", "Receipt", "Thanks", "com.mail", postedMillis = 2_000L, openable = true)
    private val saved = FoldNotice("foldcade:save", "Both saves were kept", "Bravely Second · archived", postedMillis = 1_000L)
    private val pinned = FoldNotice("vpn", "VPN", "Connected", "com.vpn", postedMillis = 500L, openable = true, dismissable = false)

    @Test
    fun rOneListsTheTilesThenBrightnessThenNoticesThenClearAll() {
        val rows = rightRows(listOf(chat, mail, pinned))
        assertEquals(
            quickSettings().map { Row.QuickTile(it) } +
                listOf(Row.Brightness, Row.Notice("chat"), Row.Notice("mail"), Row.Notice("vpn"), Row.ClearNotices),
            rows,
        )
        // One removable notice needs no Clear all.
        assertEquals(false, Row.ClearNotices in rightRows(listOf(chat, pinned)))
        // Access is asked from Settings, so R1 never shows the row.
        val asking = model(emptyList()).copy(askNoticeAccess = true)
        val opened = reduce(asking, Meaning.RightPanel).first
        assertEquals(false, Row.NoticeAccess in panelRows(opened.panel!!, opened))
    }

    @Test
    fun aOpensTheNoticeAndRoneCloses() {
        val focused = onNotice(chat)
        val (top, open) = reduce(focused, Meaning.Activate)
        assertEquals(Effect.OpenNotice("chat"), open)
        assertNull(top.panel)
    }

    @Test
    fun yDoesNothingOnANotice() {
        val focused = onNotice(chat)
        assertEquals(focused to null, reduce(focused, Meaning.Options))
    }

    @Test
    fun xAndDeleteDismissAndTheNextNoticeMovesUnderTheFocus() {
        val focused = onNotice(chat)
        listOf(Meaning.ActivateBottom, Meaning.Dismiss).forEach { meaning ->
            val (after, effect) = reduce(focused, meaning)
            assertEquals(Effect.DismissNotice("chat"), effect)
            assertEquals(listOf(mail, saved, pinned), after.notices)
            assertEquals(focused.panel?.index, after.panel?.index)
            assertEquals(Row.Notice("mail"), panelRows(after.panel!!, after)[after.panel!!.index])
        }
        // A held X or Delete takes only the first.
        listOf(Meaning.ActivateBottom, Meaning.Dismiss).forEach { meaning ->
            val after = reduce(focused, meaning).first
            assertEquals(after, reduce(after, meaning, repeatCount = 1).first)
        }
        val (kept, none) = reduce(onNotice(pinned), Meaning.ActivateBottom)
        assertNull(none)
        assertEquals(listOf(chat, mail, saved, pinned), kept.notices)
    }

    @Test
    fun aFoldcadeNoticeHasNoAppSoAShowsAllOfItsText() {
        val focused = onNotice(saved)
        val (read, effect) = reduce(focused, Meaning.Activate)
        assertNull(effect)
        assertEquals(DialogKind.Notice, read.dialog?.kind)
        assertEquals("Bravely Second · archived", read.dialog?.body)
        assertEquals(focused.panel, read.panel)
    }

    @Test
    fun clearAllKeepsOnlyWhatAndroidWillNotClear() {
        val model = model(listOf(chat, mail, pinned))
        val opened = reduce(model, Meaning.RightPanel).first
        val clear = opened.copy(panel = opened.panel!!.copy(index = panelRows(opened.panel!!, opened).indexOf(Row.ClearNotices)))
        val (after, effect) = reduce(clear, Meaning.Activate)
        assertEquals(Effect.ClearNotices, effect)
        assertEquals(listOf(pinned), after.notices)
        assertEquals(Row.Brightness, panelRows(after.panel!!, after)[after.panel!!.index])
    }

    @Test
    fun theSettingsRowAsksAndBackSaysNotNow() {
        assertEquals(false, Row.NoticeAccess in settingsRows(SettingsCategory.Screens, model(emptyList())))
        val row = model(emptyList()).copy(askNoticeAccess = true).onSetting(Row.NoticeAccess)
        assertEquals("Notifications  Allow", rowLabel(Row.NoticeAccess, row))
        val asked = reduce(row, Meaning.Activate).first
        assertEquals(DialogKind.NoticeAccess, asked.dialog?.kind)
        assertEquals(row.settings, asked.settings)
        assertEquals(DialogButton.Allow, asked.dialog!!.buttons[asked.dialog!!.index])
        assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.NoticeAccess), reduce(asked, Meaning.Back).second)
    }

    @Test
    fun theHintRowNamesWhatANoticeTakes() {
        assertEquals(
            listOf(
                HintKey(PromptKey.FaceA, NoticeCopy.open),
                HintKey(PromptKey.FaceX, NoticeCopy.dismiss),
                HintKey(PromptKey.FaceB, Copy.back),
            ),
            panelHints(onNotice(chat)),
        )
        assertEquals(
            listOf(HintKey(PromptKey.FaceX, NoticeCopy.dismiss), HintKey(PromptKey.FaceB, Copy.back)),
            panelHints(onNotice(saved)),
        )
        assertEquals(
            listOf(HintKey(PromptKey.FaceA, NoticeCopy.open), HintKey(PromptKey.FaceB, Copy.back)),
            panelHints(onNotice(pinned)),
        )
    }

    @Test
    fun deleteDismissesExceptWhileTyping() {
        assertEquals(Meaning.Dismiss, meaningOf(KeyEvent.KEYCODE_DEL))
        assertEquals(Meaning.Dismiss, meaningOf(KeyEvent.KEYCODE_FORWARD_DEL))
        assertEquals(Meaning.Erase, meaningOf(KeyEvent.KEYCODE_DEL, homeKeys = HomeKeys.Typing))
    }

    @Test
    fun agesAreShortAndNewestComesFirst() {
        assertEquals("now", noticeAge(0L, 59_000L))
        assertEquals("5m", noticeAge(0L, 5 * 60_000L))
        assertEquals("3h", noticeAge(0L, 3 * 3_600_000L))
        assertEquals("2d", noticeAge(0L, 2 * 86_400_000L))
        assertEquals("now", noticeAge(10_000L, 0L))
        assertEquals(listOf(chat, mail, saved), orderedNotices(listOf(saved, chat, mail)))
    }

    @Test
    fun rOneFollowsAndroidsListWhileItIsOpen() {
        val focused = retainMenuRows(PickerModel(count = 2, rowsPerPage = 2), onNotice(chat))
        // Android takes the notice away after X, and a new one arrives.
        val news = FoldNotice("news", "Patch notes", "1.2", "com.news", postedMillis = 4_000L, openable = true)
        val next = retainMenuRows(focused, focused.copy(notices = listOf(news, mail, saved, pinned)))
        val rows = panelRows(next.panel!!, next)
        assertFalse(Row.Notice("chat") in rows)
        assertTrue(Row.Notice("news") in rows)
        assertTrue(rows[next.panel!!.index] is Row.Notice)
    }

    private fun model(notices: List<FoldNotice>) = PickerModel(count = 2, rowsPerPage = 2, notices = notices)

    /** R1 open with the focus on [notice], among all four. */
    private fun onNotice(notice: FoldNotice): PickerModel {
        val opened = reduce(model(listOf(chat, mail, saved, pinned)), Meaning.RightPanel).first
        val index = panelRows(opened.panel!!, opened).indexOf(Row.Notice(notice.id))
        return opened.copy(panel = opened.panel!!.copy(index = index))
    }
}
