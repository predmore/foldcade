package app.foldcade.romm

import org.junit.Assert.*
import org.junit.Test

class RommDiscoveryTest {
    @Test fun probesOnlyABoundedListOnThisPrivateSlash24() {
        val candidates = rommCandidates("192.168.2.100")
        assertTrue(candidates.size <= 12)
        assertTrue(candidates.all { it.startsWith("http://192.168.2.") && !it.substringAfter("://").contains(':') })
        assertFalse(candidates.contains("http://192.168.2.100"))
        assertEquals(emptyList<String>(), rommCandidates("8.8.8.8"))
        assertEquals(emptyList<String>(), rommCandidates("192.168.0.999"))
        assertEquals(emptyList<String>(), rommCandidates("fe80::1"))
        assertTrue(rommCandidates("10.1.2.1").none { it.endsWith(".0") || it.endsWith(".255") })
    }

    @Test fun aGenericHealthCheckDoesNotCountAsRomm() {
        assertFalse(isRommHeartbeat("OK"))
        assertFalse(isRommHeartbeat("""{"SYSTEM":{"VERSION":"5.4.1"}}"""))
        assertTrue(isRommHeartbeat("""{"SYSTEM":{"VERSION":"5.4.1"},"METADATA_SOURCES":{"ANY_SOURCE_ENABLED":true},"EMULATION":{"DISABLE_EMULATOR_JS":false}}"""))
    }
}
