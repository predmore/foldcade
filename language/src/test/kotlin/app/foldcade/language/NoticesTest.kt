package app.foldcade.language

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoticesTest {
    @Test
    fun standingSaveNoticesOpenTheirOwnPlayersFixAndCanBeDismissed() {
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
            assertEquals(Effect.DismissNotice(notice.id), reduce(focused, Meaning.Options).second)
            assertEquals(true, panelHints(focused)!!.any { it.label == Copy.setUp })
            val (missing, noEffect) = reduce(focused.copy(playerSaves = emptyList()), Meaning.Activate)
            assertNull(noEffect)
            assertNull(missing.dialog)
        }
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
    fun aOpensOnTopXOnTheBottomAndRoneCloses() {
        val focused = onNotice(chat)
        val (top, open) = reduce(focused, Meaning.Activate)
        assertEquals(Effect.OpenNotice("chat", onBottom = false), open)
        assertNull(top.panel)
        val (bottom, below) = reduce(focused, Meaning.ActivateBottom)
        assertEquals(Effect.OpenNotice("chat", onBottom = true), below)
        assertNull(bottom.panel)
    }

    @Test
    fun yAndDeleteDismissAndTheNextNoticeMovesUnderTheFocus() {
        val focused = onNotice(chat)
        listOf(Meaning.Options, Meaning.Dismiss).forEach { meaning ->
            val (after, effect) = reduce(focused, meaning)
            assertEquals(Effect.DismissNotice("chat"), effect)
            assertEquals(listOf(mail, saved, pinned), after.notices)
            assertEquals(focused.panel?.index, after.panel?.index)
            assertEquals(Row.Notice("mail"), panelRows(after.panel!!, after)[after.panel!!.index])
        }
        val (kept, none) = reduce(onNotice(pinned), Meaning.Options)
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
        assertEquals(focused, reduce(focused, Meaning.ActivateBottom).first)
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
                HintKey(PromptKey.FaceA, Copy.hintTop),
                HintKey(PromptKey.FaceX, Copy.hintBottom),
                HintKey(PromptKey.FaceY, NoticeCopy.dismiss),
                HintKey(PromptKey.FaceB, Copy.back),
            ),
            panelHints(onNotice(chat)),
        )
        assertEquals(
            listOf(HintKey(PromptKey.FaceY, NoticeCopy.dismiss), HintKey(PromptKey.FaceB, Copy.back)),
            panelHints(onNotice(saved)),
        )
        assertEquals(listOf(HintKey(PromptKey.FaceB, Copy.back)), panelHints(onNotice(pinned))?.drop(2))
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

    private fun model(notices: List<FoldNotice>) = PickerModel(count = 2, rowsPerPage = 2, notices = notices)

    /** R1 open with the focus on [notice], among all four. */
    private fun onNotice(notice: FoldNotice): PickerModel {
        val opened = reduce(model(listOf(chat, mail, saved, pinned)), Meaning.RightPanel).first
        val index = panelRows(opened.panel!!, opened).indexOf(Row.Notice(notice.id))
        return opened.copy(panel = opened.panel!!.copy(index = index))
    }
}
