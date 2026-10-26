package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings › Library has a switch for each of Moonlight's computers. On pairs; off forgets, after asking. */
class StreamHostSettingsTest {
    private val gameWin = StreamHostSetting(key = "8fe9e201-578a-7c2c-b017-f2a9a9d86886", name = "game-win", paired = false)
    private val model = PickerModel(count = 4, rowsPerPage = 1, streamHosts = listOf(gameWin))

    @Test
    fun eachComputerHasATileWhosePageIsItsSwitch() {
        val rows = settingsRows(SettingsCategory.Library, model)
        val tile = Row.SettingsTile(SettingsInner.Host(gameWin.key))
        assertTrue(rows.indexOf(tile) in 0 until rows.indexOf(Row.Order))
        assertEquals("game-win", rowText(tile, model).label)
        val row = Row.StreamHost(gameWin.key)
        assertEquals(listOf(row), settingsInnerRows(SettingsInner.Host(gameWin.key), model))
        assertEquals(Copy.hostGames, rowText(row, model).label)
        assertEquals(RowControl.Toggle(false), rowControl(row, model))
        val paired = model.copy(streamHosts = listOf(gameWin.copy(paired = true)))
        assertEquals(RowControl.Toggle(true), rowControl(row, paired))
        assertFalse(settingsRows(SettingsCategory.Library, PickerModel(count = 4, rowsPerPage = 1)).any { it is Row.SettingsTile && it.page is SettingsInner.Host })
    }

    @Test
    fun turningItOnPairsAndTheCodeDialogStopsIt() {
        val (next, effect) = reduce(model.onSetting(Row.StreamHost(gameWin.key)), Meaning.Activate)
        assertEquals(Effect.PairStreamHost(gameWin.key), effect)
        assertTrue(next.settings!!.onRows)

        val showing = next.copy(dialog = pairHostDialog("game-win", "4821", gameWin.key, HostScreen.Top))
        assertEquals("Enter 4821 on game-win", showing.dialog!!.title)
        val (closed, stop) = reduce(showing, Meaning.Back)
        assertNull(closed.dialog)
        assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.PairHost, gameWin.key), stop)
    }

    @Test
    fun turningItOffAsksAndOpensOnNotNow() {
        val paired = model.copy(streamHosts = listOf(gameWin.copy(paired = true)))
        val (asking, effect) = reduce(paired.onSetting(Row.StreamHost(gameWin.key)), Meaning.Activate)
        assertNull(effect)
        val dialog = asking.dialog!!
        assertEquals(DialogKind.ForgetHost, dialog.kind)
        assertEquals("Forget game-win?", dialog.title)
        assertEquals(DialogButton.NotNow, dialog.buttons[dialog.index])
        // Back keeps the pairing.
        assertEquals(Effect.DialogChoice(DialogButton.NotNow, DialogKind.ForgetHost, gameWin.key), reduce(asking, Meaning.Back).second)
        val forget = reduce(reduce(asking, Meaning.MoveLeft).first, Meaning.Activate).second
        assertEquals(Effect.DialogChoice(DialogButton.ForgetHost, DialogKind.ForgetHost, gameWin.key), forget)
    }

    @Test
    fun aFailedPairingSaysWhy() {
        assertEquals(Copy.pairWrongPinTitle, pairFailedDialog("game-win", PairFailure.WrongPin, HostScreen.Top).title)
        assertEquals("game-win didn’t answer", pairFailedDialog("game-win", PairFailure.Unreachable, HostScreen.Top).title)
        assertEquals("game-win didn’t pair", pairFailedDialog("game-win", PairFailure.Refused, HostScreen.Top).title)
    }
}
