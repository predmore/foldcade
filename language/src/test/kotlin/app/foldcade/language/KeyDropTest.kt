package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyDropTest {
    private val colors = KeyDropPage.Colors("#0b0d12", "#1a1d24", "#f2f4f8", "#8a90a0", "#5fd3a5")
    private val url = "http://192.168.1.123:41234/AbCdEfGhIjKlMnOpQrStUv"

    @Test
    fun theCodeAndItsLineFollowThePhone() {
        // Opening: nothing to scan yet.
        assertNull(keyDropQr(null))
        assertEquals(KeyDropCopy.opening, keyDropLine(null))
        assertEquals(url, keyDropQr(KeyDrop.Open(url)))
        assertEquals(KeyDropCopy.scan, keyDropLine(KeyDrop.Open(url)))
        assertEquals(KeyDropCopy.phoneHere, keyDropLine(KeyDrop.Open(url, phoneHere = true)))
        // A sent key is being checked: the code goes, so nobody scans a closed page.
        assertNull(keyDropQr(KeyDrop.Sent))
        assertEquals(KeyDropCopy.sent, keyDropLine(KeyDrop.Sent))
        // Without Wi-Fi, SteamGridDB's own page, and the key is typed.
        assertEquals(STEAMGRIDDB_API_PAGE, keyDropQr(KeyDrop.Offline))
        assertEquals(KeyDropCopy.scanOffline, keyDropLine(KeyDrop.Offline))
    }

    @Test
    fun bothCodesFitTheQrEncoder() {
        assertNotNull(QrCode.encode(url))
        assertNotNull(QrCode.encode(STEAMGRIDDB_API_PAGE))
    }

    @Test
    fun thePhonesPagePostsTheKeyToItsOwnPathAndLinksToSteamGridDb() {
        val page = KeyDropPage.form("/token123", colors)
        assertTrue(page.contains("<form method=\"post\" action=\"/token123\">"))
        assertTrue(page.contains("name=\"key\""))
        assertTrue(page.contains("href=\"$STEAMGRIDDB_API_PAGE\""))
        assertTrue(page.contains("rel=\"noopener noreferrer\""))
        assertTrue(page.contains("<meta name=\"referrer\" content=\"no-referrer\">"))
        assertTrue(page.contains("#5fd3a5"))
        // Nothing to fetch and no script.
        assertFalse(page.contains("<script"))
        assertFalse(page.contains("src="))
    }

    @Test
    fun onlyPlainColoursAndAQuotedActionReachThePage() {
        val page = KeyDropPage.form("/a\"><script>", colors.copy(accent = "red;}</style><script>"))
        assertFalse(page.contains("<script>"))
        assertTrue(page.contains("action=\"/a&quot;&gt;&lt;script&gt;\""))
        assertTrue(page.contains("#000000"))
    }

    @Test
    fun theDonePageSaysWhatHappened() {
        assertTrue(KeyDropPage.done(true, colors).contains(KeyDropCopy.pageSent))
        assertTrue(KeyDropPage.done(false, colors).contains(KeyDropCopy.pageEmpty))
    }
}
