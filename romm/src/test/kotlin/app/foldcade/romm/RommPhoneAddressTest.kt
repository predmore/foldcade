package app.foldcade.romm

import org.junit.Assert.*
import org.junit.Test

class RommPhoneAddressTest {
    @Test fun acceptsOnlyServerLocationsWithoutCredentials() {
        assertEquals("https://romm.example", normalizeRommPhoneAddress(" https://romm.example/api/ "))
        assertEquals("http://192.168.1.2:8080/romm", normalizeRommPhoneAddress("http://192.168.1.2:8080/romm"))
        for (bad in listOf("javascript:alert(1)", "ftp://romm.example", "https://u:p@romm.example", "https://romm.example?token=secret", "https://romm.example/#token", "http://public.example", "https://", "https://romm.example\\@other.example")) {
            assertNull(bad, normalizeRommPhoneAddress(bad))
        }
    }
    @Test fun approvalLinkIsOnTheChosenRommAndNeverChangesProtocol() {
        val link = "https://romm.example/pair/device?user_code=ABCD1234"
        assertEquals(link, rommPhoneApprovalUrl("https://romm.example", link))
        assertNull(rommPhoneApprovalUrl("https://romm.example", "http://romm.example/pair/device"))
        assertNull(rommPhoneApprovalUrl("https://romm.example", "https://other.example/pair/device"))
        assertNull(rommPhoneApprovalUrl("https://romm.example", "javascript:alert(1)"))
    }
}
