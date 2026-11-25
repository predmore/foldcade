package app.foldcade

import app.foldcade.language.FolderRef
import app.foldcade.plugins.duckstation.DuckStationCardSettings
import app.foldcade.plugins.duckstation.DuckStationPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostedCardOpensTest {
    private val duck = HostedCardCaller(1443, DuckStationPlayer.PACKAGE)
    private val root = HostedFolders.rootId(DuckStationPlayer.ID)
    private val expected = "RomM filename_1.mcd"
    private val actual = "Title in DuckStation_1.mcd"

    @Test fun matchingReadIsPositiveAcrossDetachAndCloseErrors() {
        val log = log()
        val id = log.opened(open(expected, 11))
        assertEquals(HostedCardVerdict.Expected(11), judge(log))
        for (status in listOf(HostedCardClose.Detached, HostedCardClose.Failed)) {
            log.closed(id, 13, status)
            val closed = log.during(10, 20)!!.single()
            assertEquals(13L, closed.closedAtMillis)
            assertEquals("r", closed.mode)
            assertEquals(status, closed.closeStatus)
            assertEquals(HostedCardVerdict.Expected(11), judge(log))
        }
    }

    @Test fun aBareWriteGivesNoCardProofAndFailedCloseInvalidatesPublication() {
        val log = log()
        val id = log.opened(open(expected, 11, mode = "rw"))
        log.closed(id, 12, HostedCardClose.Detached)
        assertEquals(HostedCardVerdict.Unknown, judge(log))
        val temp = log.opened(open("tmp", 13, "w"))
        log.closed(temp, 14, HostedCardClose.Failed)
        log.published(duck, "$root/tmp", "$root/$expected", 15)
        assertEquals(HostedCardVerdict.Unknown, judge(log))
    }

    @Test fun otherPackagesUidsFoldersSlotsAndTimesGiveNoVerdict() {
        val log = log()
        log.opened(open(expected, 9))
        log.opened(open(expected, 21))
        log.opened(open(expected, 11).copy(caller = duck.copy(uid = 13)))
        log.opened(open(expected, 11).copy(caller = duck.copy(packageName = "file.manager")))
        log.opened(open(expected, 11).copy(documentId = "foldcade:other/$expected"))
        log.opened(open("A different slot_2.mcd", 12))
        log.opened(open("shared_card_1.mcd", 12))
        log.opened(open("untitled.tmp", 12, "w"))
        assertEquals(HostedCardVerdict.Unknown, judge(log))
        assertEquals(HostedCardVerdict.Unknown, judge(log()))
    }

    @Test fun titleMismatchNamesTheObservedCardAndNeverVerifiesThePlacedCard() {
        val log = log()
        log.opened(open(actual, 11))
        assertEquals(HostedCardVerdict.Different(listOf(actual), actual, 11), judge(log))
        log.opened(open(expected, 12))
        // Multiple slot-1 paths make it unsafe to attach either set of bytes to this game.
        assertEquals(HostedCardVerdict.Different(listOf(actual), null, 11), judge(log))
    }

    @Test fun publicationCanVerifyTheExpectedCardAndRequiresAnObservedWriteFromSameCaller() {
        val log = log()
        val temp = log.opened(open("tmp", 11, "w"))
        log.closed(temp, 12, HostedCardClose.Detached)
        log.published(duck.copy(uid = 123), "$root/tmp", "$root/$expected", 12)
        assertEquals(HostedCardVerdict.Unknown, judge(log))
        log.published(duck, "$root/tmp", "$root/$expected", 13)
        assertEquals(HostedCardVerdict.Expected(13), judge(log))
        assertEquals(HostedCardVerdict.Unknown, judge(log, end = 12))
    }

    @Test fun previousGameWritesCannotNameThisGamesCard() {
        val log = log()
        log.opened(open(actual, 11, "w"))
        log.opened(open("tmp", 12, "w"))
        log.published(duck, "$root/tmp", "$root/$actual", 13)
        assertEquals(HostedCardVerdict.Unknown, judge(log))
        log.opened(open(actual, 14, "rw"))
        assertEquals(HostedCardVerdict.Unknown, judge(log))
        log.opened(open(expected, 15))
        assertEquals(HostedCardVerdict.Expected(15), judge(log))
    }

    @Test fun incompleteLogCannotMakeATruncatedSessionLookLikeOneExactMatch() {
        val log = HostedCardOpens(capacity = 2, recordingSinceMillis = 0)
        log.opened(open(actual, 11))
        log.opened(open(expected, 12))
        log.opened(open(expected, 13))
        assertEquals(HostedCardVerdict.Unknown, judge(log))
        assertEquals(HostedCardVerdict.Expected(13), judge(log, start = 12))
        assertNull(log.during(10, 20))
        assertNull(HostedCardOpens(recordingSinceMillis = 11).during(10, 20))
        assertEquals(emptyList<HostedCardOpen>(), log().during(10, 20))
    }

    @Test fun selectedRomOpenAndSustainedGameplayActivityAreBothRequired() {
        val roms = DuckStationRomOpens()
        roms.opened(duck.copy(uid = 7), "selected.chd", 11)
        roms.opened(duck, "other.chd", 12)
        assertNull(roms.firstOpen(duck, "selected.chd", 10, 30_000))
        roms.opened(duck, "selected.chd", 100)
        val read = roms.firstOpen(duck, "selected.chd", 10, 30_000)!!
        val front = FrontMove(duck.packageName, DuckStationPlayer.EMULATION_ACTIVITY, true, 50)
        val paused = front.copy(shown = false, atMillis = 117)
        assertFalse(duckStationGameReached(emptyList(), read, 10, 30_000))
        assertFalse(duckStationGameReached(listOf(front.copy(className = "Menu")), read, 10, 30_000))
        assertFalse(duckStationGameReached(listOf(front, paused), read, 10, 30_000))
        assertFalse(duckStationGameReached(listOf(front), read, 10, 20_099))
        assertTrue(duckStationGameReached(listOf(front), read, 10, 20_100))
    }

    @Test fun configCanNameSharedSlotOneButSharedAndSlotTwoBytesStayOutOfGameBackups() {
        val data = DuckStationPlayer().suggestedSaveFolder
        val bound = FolderRef("app.foldcade.saves:$root", "DuckStation memory cards")
        val settings = DuckStationCardSettings("/android_content/app.foldcade.saves/tree@foldcade/duckstation", "Shared", "Custom.mcd", expected)
        val config = DuckStationSaveEvidence(listOf(settings), data, "app.foldcade")
        val opens = listOf(open("Custom.mcd", 11), open(expected, 11))
        assertEquals(HostedCardVerdict.Different(listOf("Custom.mcd"), null, 11), judgeHostedCard(opens, duck, root, expected, 10, 20,
            config.sharedCard1(bound), config.possibleSharedCards(bound), config.possibleSlot2Cards(bound)))
    }

    @Test fun configComparesDocumentIdentityAndRequiresAgreementAcrossGameOverrides() {
        val data = DuckStationPlayer().suggestedSaveFolder
        val hostedPath = "/android_content/app.foldcade.saves/tree@foldcade/duckstation"
        val global = DuckStationCardSettings(hostedPath, "PerGameTitle", null, null)
        fun evidence(vararg settings: DuckStationCardSettings) = DuckStationSaveEvidence(settings.toList(), data, "app.foldcade")
        assertEquals("app.foldcade.saves:$root", evidence(global, global.copy(
            directory = "$hostedPath/document@foldcade/duckstation", card1Type = "PerGameFileTitle")).directory()?.identity)
        assertEquals(FolderRef("primary:PS1 Saves", "PS1 Saves"), evidence(global.copy(directory = "/sdcard/PS1 Saves"),
            global.copy(directory = "/android_content/com.android.externalstorage.documents/tree@primary/PS1 Saves")).directory())
        assertEquals("primary:Android/data/${DuckStationPlayer.PACKAGE}/files/memcards", evidence(global.copy(directory = "./memcards")).directory()?.identity)
        assertNull(evidence(global, global.copy(directory = "/sdcard/PS1 Saves")).directory())
        assertNull(evidence(global.copy(directory = "/android_content/app.foldcade.saves/invalid")).directory())
        assertFalse(evidence(global.copy(card1Type = "None"), global.copy(card1Type = "NonPersistent")).hasPersistentCard1)
    }

    private fun open(name: String, at: Long, mode: String = "r") = HostedCardOpen(
        duck, "$root/$name", mode, canRead = 'r' in mode, canWrite = 'w' in mode, openedAtMillis = at,
    )

    private fun log() = HostedCardOpens(recordingSinceMillis = 0)

    private fun judge(log: HostedCardOpens, start: Long = 10, end: Long = 20) =
        judgeHostedCard(log.during(start, end).orEmpty(), duck, root, expected, start, end)
}
