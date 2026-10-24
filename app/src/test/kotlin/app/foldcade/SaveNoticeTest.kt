package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.SyncOutcome
import app.foldcade.api.plugin.SyncResult
import app.foldcade.language.Copy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveNoticeTest {
    private val game = Game(
        backendId = "romm",
        remoteKey = "42",
        platformId = "3ds",
        availability = Availability.LocalOnly,
        label = "Bravely Second",
    )

    @Test
    fun onlyKeptCopiesAndWaitingUploadsBecomeNotices() {
        val kept = saveNotice(game, SyncOutcome.KeptBoth, nowMillis = 7L)!!
        assertEquals(Copy.savesKeptTitle, kept.title)
        assertEquals("Bravely Second · ${Copy.savesKeptBody}", kept.body)
        assertEquals(7L, kept.postedMillis)
        assertEquals(false, kept.openable)
        assertEquals(Copy.offlineSaveTitle, saveNotice(game, SyncOutcome.Queued)!!.title)
        // One per game: the next outcome for it replaces the last.
        assertEquals(kept.id, saveNotice(game, SyncOutcome.Queued)!!.id)
        listOf(SyncOutcome.Unchanged, SyncOutcome.Uploaded, SyncOutcome.Downloaded).forEach {
            assertNull(saveNotice(game, it))
        }
    }

    @Test
    fun aHandBackThatUploadsInTheBackgroundSaysNothing() {
        val notices = ReturnSyncNotices(patienceMillis = 100)
        // RomM keeps the save and answers Queued before its upload starts.
        assertNull(notices.returned("romm", game, SyncOutcome.Queued, nowMillis = 0))
        assertNull(notices.synced("romm", "42", SyncResult(SyncOutcome.Uploaded), nowMillis = 5))
        assertTrue(notices.expire(nowMillis = 1_000).isEmpty())
        // Another game's sync is not this one's answer.
        assertNull(notices.returned("romm", game, SyncOutcome.Queued, nowMillis = 0))
        assertNull(notices.synced("romm", "43", SyncResult(SyncOutcome.Queued), nowMillis = 5))
        assertNull(notices.synced("romm#2", "42", SyncResult(SyncOutcome.Queued), nowMillis = 5))
        assertTrue(notices.expire(nowMillis = 1_000).isNotEmpty())
    }

    @Test
    fun aSyncThatFinishesBeforeTheHandBackReturnsIsItsAnswer() {
        val notices = ReturnSyncNotices(patienceMillis = 100)
        // The upload ends before reconcile returns Queued: no waiting notice, then or later.
        notices.handingBack("romm", game)
        assertNull(notices.synced("romm", "42", SyncResult(SyncOutcome.Uploaded), nowMillis = 1))
        assertNull(notices.returned("romm", game, SyncOutcome.Queued, nowMillis = 2))
        assertTrue(notices.expire(nowMillis = 1_000).isEmpty())
        // One that ends kept both says so as the hand-back returns.
        notices.handingBack("romm", game)
        assertNull(notices.synced("romm", "42", SyncResult(SyncOutcome.KeptBoth), nowMillis = 1))
        assertEquals(Copy.savesKeptTitle, notices.returned("romm", game, SyncOutcome.Queued, nowMillis = 2)!!.title)
        // One still waiting settles nothing, so the hand-back waits.
        notices.handingBack("romm", game)
        assertNull(notices.synced("romm", "42", SyncResult(SyncOutcome.Queued), nowMillis = 1))
        assertNull(notices.returned("romm", game, SyncOutcome.Queued, nowMillis = 2))
        assertEquals(listOf(Copy.offlineSaveTitle), notices.expire(nowMillis = 102).map { it.title })
    }

    @Test
    fun aGamesSyncThatGetsThroughSettlesEveryHandBackOfIt() {
        val notices = ReturnSyncNotices(patienceMillis = 100)
        // A second hand-back while the first still waits: the first's sync, still waiting, settles neither.
        assertNull(notices.returned("romm", game, SyncOutcome.Queued, nowMillis = 0))
        notices.handingBack("romm", game)
        assertNull(notices.synced("romm", "42", SyncResult(SyncOutcome.Queued), nowMillis = 10))
        assertNull(notices.returned("romm", game, SyncOutcome.Queued, nowMillis = 20))
        assertNull(notices.synced("romm", "42", SyncResult(SyncOutcome.Uploaded), nowMillis = 30))
        assertTrue(notices.expire(nowMillis = 1_000).isEmpty())
        // The first's sync never reported; the second's gets through before it returns, and settles both.
        assertNull(notices.returned("romm", game, SyncOutcome.Queued, nowMillis = 2_000))
        notices.handingBack("romm", game)
        assertNull(notices.synced("romm", "42", SyncResult(SyncOutcome.Uploaded), nowMillis = 2_010))
        assertNull(notices.returned("romm", game, SyncOutcome.Queued, nowMillis = 2_020))
        assertTrue(notices.expire(nowMillis = 3_000).isEmpty())
        // A launch's sync, still waiting on a lock the upload holds, is not the upload's answer.
        assertNull(notices.returned("romm", game, SyncOutcome.Queued, nowMillis = 4_000))
        assertNull(notices.synced("romm", "42", SyncResult(SyncOutcome.Queued), nowMillis = 4_010))
        assertNull(notices.synced("romm", "42", SyncResult(SyncOutcome.Uploaded), nowMillis = 4_020))
        assertTrue(notices.expire(nowMillis = 5_000).isEmpty())
        // Kept both, while a second hand-back is on its way, is said once.
        assertNull(notices.returned("romm", game, SyncOutcome.Queued, nowMillis = 6_000))
        notices.handingBack("romm", game)
        assertEquals(Copy.savesKeptTitle, notices.synced("romm", "42", SyncResult(SyncOutcome.KeptBoth), nowMillis = 6_010)!!.title)
        assertNull(notices.returned("romm", game, SyncOutcome.Queued, nowMillis = 6_020))
        assertTrue(notices.expire(nowMillis = 7_000).isEmpty())
    }

    @Test
    fun aWaitHoldsWhileAnotherHandBackOfTheGameIsOnItsWay() {
        val notices = ReturnSyncNotices(patienceMillis = 100)
        assertNull(notices.returned("romm", game, SyncOutcome.Queued, nowMillis = 0))
        notices.handingBack("romm", game)
        // Its patience runs out while the next one is with the library: no notice yet.
        assertTrue(notices.expire(nowMillis = 100).isEmpty())
        assertNull(notices.synced("romm", "42", SyncResult(SyncOutcome.Uploaded), nowMillis = 110))
        assertNull(notices.returned("romm", game, SyncOutcome.Queued, nowMillis = 120))
        assertTrue(notices.expire(nowMillis = 1_000).isEmpty())
        // When the next one fails before the library has it, the earlier wait is due again.
        assertNull(notices.returned("romm", game, SyncOutcome.Queued, nowMillis = 2_000))
        notices.handingBack("romm", game)
        assertTrue(notices.expire(nowMillis = 2_100).isEmpty())
        notices.dropped("romm", game)
        assertEquals(listOf(Copy.offlineSaveTitle), notices.expire(nowMillis = 2_110).map { it.title })
    }

    @Test
    fun aDroppedHandBackLeavesTheOneBeforeItWaiting() {
        val notices = ReturnSyncNotices(patienceMillis = 100)
        notices.handingBack("romm", game)
        notices.dropped("romm", game)
        assertNull(notices.synced("romm", "42", SyncResult(SyncOutcome.KeptBoth), nowMillis = 5))
        assertTrue(notices.expire(nowMillis = 1_000).isEmpty())
        // The first still waits after the second fails.
        assertNull(notices.returned("romm", game, SyncOutcome.Queued, nowMillis = 2_000))
        notices.handingBack("romm", game)
        notices.dropped("romm", game)
        assertEquals(Copy.savesKeptTitle, notices.synced("romm", "42", SyncResult(SyncOutcome.KeptBoth), nowMillis = 2_010)!!.title)
        assertNull(notices.returned("romm", game, SyncOutcome.Queued, nowMillis = 3_000))
        assertEquals(listOf(Copy.offlineSaveTitle), notices.expire(nowMillis = 3_100).map { it.title })
    }

    @Test
    fun aHandBackStillWaitingOrKeptBothSaysSo() {
        val notices = ReturnSyncNotices(patienceMillis = 100)
        // A sync that is still waiting leaves the notice to the patience.
        assertNull(notices.returned("romm", game, SyncOutcome.Queued, nowMillis = 0))
        assertNull(notices.synced("romm", "42", SyncResult(SyncOutcome.Queued), nowMillis = 5))
        assertEquals(listOf(Copy.offlineSaveTitle), notices.expire(nowMillis = 100).map { it.title })
        assertNull(notices.returned("romm", game, SyncOutcome.Queued, nowMillis = 0))
        assertEquals(Copy.savesKeptTitle, notices.synced("romm", "42", SyncResult(SyncOutcome.KeptBoth), nowMillis = 5)!!.title)
        // A library that keeps both at once says so at once.
        assertEquals(Copy.savesKeptTitle, notices.returned("romm", game, SyncOutcome.KeptBoth, nowMillis = 0)!!.title)
        // One that never reports is waiting, once the patience runs out.
        assertNull(notices.returned("romm", game, SyncOutcome.Queued, nowMillis = 0))
        assertTrue(notices.expire(nowMillis = 99).isEmpty())
        assertEquals(listOf(Copy.offlineSaveTitle), notices.expire(nowMillis = 100).map { it.title })
        assertTrue(notices.expire(nowMillis = 200).isEmpty())
        // A launch's own sync report, with no hand-back waiting, is not a notice.
        assertNull(notices.synced("romm", "42", SyncResult(SyncOutcome.Queued), nowMillis = 300))
        assertNull(notices.synced("romm", "42", SyncResult(SyncOutcome.KeptBoth), nowMillis = 300))
    }
}
