package app.foldcade.language

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoticesTest {
    private val chat = FoldNotice("chat", "Alex", "you on tonight?", "com.discord", postedMillis = 3_000L, openable = true)
    private val mail = FoldNotice("mail", "Receipt", "Thanks", "com.mail", postedMillis = 2_000L, openable = true)
    private val saved = FoldNotice("foldcade:save", "Both saves were kept", "Bravely Second · archived", postedMillis = 1_000L)
    private val pinned = FoldNotice("vpn", "VPN", "Connected", "com.vpn", postedMillis = 500L, openable = true, dismissable = false)

    @Test
    fun rOneListsNoticesUnderBrightnessThenClearAllThenTheTiles() {
        val rows = rightRows(listOf(chat, mail, pinned))
        assertEquals(
            listOf(Row.Brightness, Row.Notice("chat"), Row.Notice("mail"), Row.Notice("vpn"), Row.ClearNotices) +
                quickSettings().map { Row.QuickTile(it) },
            rows,
        )
        // One removable notice needs no Clear all.
        assertEquals(false, Row.ClearNotices in rightRows(listOf(chat, pinned)))
        // Without access, R1 asks where the notices would be.
        assertEquals(listOf(Row.Brightness, Row.NoticeAccess), rightRows(emptyList(), askNoticeAccess = true).take(2))
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
        assertEquals(0, after.panel?.index)
    }

    @Test
    fun theAccessRowAsksAndBackSaysNotNow() {
        val opened = reduce(model(emptyList()).copy(askNoticeAccess = true), Meaning.RightPanel).first
        val row = reduce(opened, Meaning.MoveDown).first
        assertEquals("Notifications  Allow", rowLabel(Row.NoticeAccess, row))
        val asked = reduce(row, Meaning.Activate).first
        assertEquals(DialogKind.NoticeAccess, asked.dialog?.kind)
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
    fun deleteDismissesExceptWhileNamingAFolder() {
        assertEquals(Meaning.Dismiss, meaningOf(KeyEvent.KEYCODE_DEL))
        assertEquals(Meaning.Dismiss, meaningOf(KeyEvent.KEYCODE_FORWARD_DEL))
        assertNull(meaningOf(KeyEvent.KEYCODE_DEL, homeKeys = HomeKeys.Renaming))
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
