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

    @Test fun matchingReadIsPositiveEvenWhenCloseIsUnobservable() {
        val log = HostedCardOpens()
        val id = log.opened(open(expected, 11, mode = "rw"))
        assertEquals(HostedCardVerdict.Expected(11), judge(log))
        log.closed(id, 13, failed = true)
        val closed = log.during(10, 20).single()
        assertEquals(13L, closed.closedAtMillis)
        assertEquals("rw", closed.mode)
        assertTrue(closed.canRead && closed.canWrite && closed.closeFailed)
        assertEquals(HostedCardVerdict.Expected(11), judge(log))
    }

    @Test fun otherPackagesUidsFoldersSlotsAndTimesGiveNoVerdict() {
        val log = HostedCardOpens()
        log.opened(open(expected, 9))
        log.opened(open(expected, 21))
        log.opened(open(expected, 11).copy(caller = duck.copy(uid = 13)))
        log.opened(open(expected, 11).copy(caller = duck.copy(packageName = "file.manager")))
        log.opened(open(expected, 11).copy(documentId = "foldcade:other/$expected"))
        log.opened(open("A different slot_2.mcd", 12))
        log.opened(open("shared_card_1.mcd", 12))
        log.opened(open("untitled.tmp", 12, "w"))
        assertEquals(HostedCardVerdict.Unknown, judge(log))
        assertEquals(HostedCardVerdict.Unknown, judge(HostedCardOpens()))
    }

    @Test fun titleMismatchNamesTheObservedCardAndNeverVerifiesThePlacedCard() {
        val log = HostedCardOpens()
        log.opened(open(actual, 11))
        assertEquals(HostedCardVerdict.Different(listOf(actual), actual, 11), judge(log))
        log.opened(open(expected, 12))
        // Multiple slot-1 paths make it unsafe to attach either set of bytes to this game.
        assertEquals(HostedCardVerdict.Different(listOf(actual), null, 11), judge(log))
    }

    @Test fun publicationUsesTheFinalNameAndRequiresAnObservedWriteFromSameCaller() {
        val log = HostedCardOpens()
        log.opened(open("tmp", 11, "w"))
        log.published(duck.copy(uid = 123), "$root/tmp", "$root/$actual", 12)
        assertEquals(HostedCardVerdict.Unknown, judge(log))
        log.published(duck, "$root/tmp", "$root/$actual", 13)
        assertEquals(HostedCardVerdict.Different(listOf(actual), actual, 13), judge(log))
        assertEquals(HostedCardVerdict.Unknown, judge(log, end = 12))
    }

    @Test fun incompleteLogCannotMakeATruncatedSessionLookLikeOneExactMatch() {
        val log = HostedCardOpens(capacity = 2)
        log.opened(open(actual, 11))
        log.opened(open(expected, 12))
        log.opened(open(expected, 13))
        assertEquals(HostedCardVerdict.Unknown, judge(log))
        assertEquals(HostedCardVerdict.Expected(13), judge(log, start = 12))
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

    private fun judge(log: HostedCardOpens, start: Long = 10, end: Long = 20) =
        judgeHostedCard(log.during(start, end), duck, root, expected, start, end)
}
