package app.foldcade.net

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanHttpTest {
    private val http = LanHttp()

    @Test
    fun allowsOnlyLanHosts() {
        assertTrue(http.allows("http://172.16.10.100:47989/serverinfo"))
        assertTrue(http.allows("http://192.168.1.20:47989/serverinfo"))
        assertTrue(http.allows("http://100.101.102.103:47989/serverinfo"))
        assertTrue(http.allows("https://[fe80::1]:47984/applist"))
        assertTrue(http.allows("http://game-win.local:47989/serverinfo"))
        assertFalse(http.allows("http://game-win:47989/serverinfo"))
        assertFalse(http.allows("http://8.8.8.8:47989/serverinfo"))
        assertFalse(http.allows("https://example.com/"))
        assertFalse(http.allows("ftp://192.168.1.20/"))
    }

    @Test
    fun aPublicHostIsRefusedBeforeAnythingIsSent() {
        val refused = runBlocking { runCatching { http.get("http://8.8.8.8:47989/serverinfo") } }
        assertEquals(LanHttpException::class, refused.exceptionOrNull()!!::class)
    }
}
