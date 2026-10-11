package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.SyncOutcome
import app.foldcade.language.Copy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
