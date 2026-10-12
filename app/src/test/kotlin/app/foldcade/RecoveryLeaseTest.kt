package app.foldcade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryLeaseTest {
    private class Windows {
        var shows = 0
        var dismissals = 0
        var canShow = true
        val pending = mutableListOf<() -> Unit>()
        val lease = RecoveryLease(
            show = { shows++; canShow },
            dismiss = { dismissals++ },
            later = { _, work -> pending += work },
        )
        fun runPending() = pending.toList().also { pending.clear() }.forEach { it() }
    }

    @Test fun repairsOnceThenStaysQuietThroughRepeatedVisibilityUpdates() {
        val windows = Windows()
        windows.lease.update(true)
        repeat(10) { windows.lease.update(true) }
        assertEquals(1, windows.shows)
        windows.runPending()
        assertEquals(2, windows.shows)
        windows.runPending()
        assertEquals(2, windows.shows)
        assertTrue(windows.lease.active)
    }

    @Test fun launchingAnAppCancelsTheDelayedRecovery() {
        val windows = Windows()
        windows.lease.update(true)
        windows.lease.update(false)
        windows.runPending()
        assertEquals(1, windows.shows)
        assertEquals(1, windows.dismissals)
        assertFalse(windows.lease.active)
    }

    @Test fun sleepAndWakeDoNotLetAnOldRepairRaiseTheNewWindow() {
        val windows = Windows()
        windows.lease.update(true)
        windows.lease.update(false)
        windows.lease.update(true)
        windows.runPending()
        assertEquals(3, windows.shows)
        assertEquals(1, windows.dismissals)
    }

    @Test fun explicitHomeSupersedesPendingRepair() {
        val windows = Windows()
        windows.lease.update(true)
        windows.lease.update(true, reclaim = true)
        windows.runPending()
        assertEquals(3, windows.shows)
    }

    @Test fun failedPresentationFallsBackWithoutAnAutomaticRetryLoop() {
        val windows = Windows().apply { canShow = false }
        windows.lease.update(true)
        repeat(10) { windows.lease.update(true) }
        assertFalse(windows.lease.active)
        assertEquals(1, windows.shows)
        assertTrue(windows.pending.isEmpty())
    }

    @Test fun failedRepairWaitsForAnExplicitReclaim() {
        val windows = Windows()
        windows.lease.update(true)
        windows.canShow = false
        windows.runPending()
        assertFalse(windows.lease.active)
        windows.canShow = true
        windows.lease.update(true)
        assertEquals(2, windows.shows)
        windows.lease.update(true, reclaim = true)
        assertEquals(3, windows.shows)
        assertTrue(windows.lease.active)
    }

    @Test fun anAppOpenedOutsideFoldcadeYieldsRecoveryOnItsOtherScreen() {
        val unknown = Session().place(Panel.Top, ExternalApp(OTHER_APP_ID))
        assertFalse(canRecoverPanel(unknown, Panel.Bottom))
        assertFalse(canRecoverPanel(unknown.browse(Panel.Bottom), Panel.Bottom))
        assertFalse(canRecoverPanel(unknown, Panel.Top))
        assertTrue(canRecoverPanel(unknown.home(Panel.Top), Panel.Bottom))
    }

    @Test fun knownSingleScreenAppsAllowRecoveryOnlyOnTheFreePanel() {
        for (target in Panel.entries) {
            val playing = Session().launch(ExternalApp("single"), target)
            assertTrue(canRecoverPanel(playing, target.flip()))
            assertFalse(canRecoverPanel(playing, target))
        }
        val dual = Session().launch(ExternalApp("emulator", occupiesBothDisplays = true))
        for (panel in Panel.entries) assertFalse(canRecoverPanel(dual, panel))
    }

    @Test fun secondaryResumeKeepsBothScreenAppInChargeUntilPrimaryReturns() {
        val playing = Session().launch(ExternalApp("emulator", occupiesBothDisplays = true))
        val secondary = playing.foldcadeResumed(Panel.Bottom)
        assertFalse(canRecoverPanel(secondary, Panel.Bottom))
        assertTrue(canRecoverPanel(secondary.foldcadeResumed(Panel.Top), Panel.Bottom))
    }
}
