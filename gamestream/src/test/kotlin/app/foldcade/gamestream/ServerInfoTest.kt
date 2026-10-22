package app.foldcade.gamestream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerInfoTest {
    @Test
    fun readsAnUnpairedSunshineReply() {
        // game-win's reply to an unpaired http request, 2026-10-21.
        val info = parseServerInfo(SUNSHINE_FREE)!!
        assertEquals("8FE9E201-578A-7C2C-B017-F2A9A9D86886", info.uniqueId)
        assertEquals("8fe9e201-578a-7c2c-b017-f2a9a9d86886", info.key)
        assertEquals("game-win", info.hostname)
        assertEquals(47984, info.httpsPort)
        assertNull(info.runningAppId)
        // All zeros means the host did not say.
        assertNull(info.mac)
    }

    @Test
    fun aBusyHostNamesTheAppItStreams() {
        val busy = SUNSHINE_FREE
            .replace("<currentgame>0</currentgame>", "<currentgame>881448767</currentgame>")
            .replace("SUNSHINE_SERVER_FREE", "SUNSHINE_SERVER_BUSY")
        assertEquals("881448767", parseServerInfo(busy)!!.runningAppId)
    }

    @Test
    fun apolloBlanksTheRunningAppOverHttp() {
        // Apollo sends currentgame 0 and FREE to an http request even while it streams.
        assertNull(parseServerInfo(SUNSHINE_FREE)!!.runningAppId)
        val stale = SUNSHINE_FREE.replace("<currentgame>0</currentgame>", "<currentgame>881448767</currentgame>")
        assertNull(parseServerInfo(stale)!!.runningAppId)
    }

    @Test
    fun aRealMacIsKept() {
        val paired = SUNSHINE_FREE.replace("00:00:00:00:00:00", "3C:7C:3F:12:AB:01")
        assertEquals("3C:7C:3F:12:AB:01", parseServerInfo(paired)!!.mac)
    }

    @Test
    fun refusesErrorsAndOtherReplies() {
        assertNull(parseServerInfo("""<root status_code="401" status_message="The client is not authorized."/>"""))
        assertNull(parseServerInfo("<html><body>router</body></html>"))
        assertNull(parseServerInfo(SUNSHINE_FREE.replace("8FE9E201-578A-7C2C-B017-F2A9A9D86886", "not-a-uuid")))
        assertNull(parseServerInfo("""<!DOCTYPE root [<!ENTITY x "y">]>""" + SUNSHINE_FREE))
    }

    @Test
    fun unescapesTheHostName() {
        val named = SUNSHINE_FREE.replace("<hostname>game-win</hostname>", "<hostname>Tom &amp; Jo&apos;s PC</hostname>")
        assertEquals("Tom & Jo's PC", parseServerInfo(named)!!.hostname)
    }

    @Test
    fun appIdsAreNonNegative32BitDecimals() {
        assertEquals(true, isAppId("881448767"))
        assertEquals(true, isAppId("2147483647"))
        assertEquals(false, isAppId("2147483648"))
        assertEquals(false, isAppId("-1"))
        assertEquals(false, isAppId(""))
        assertEquals(false, isAppId("12a"))
    }

    @Test
    fun anIpv6AddressGoesInBrackets() {
        assertEquals("[fe80::1]", hostPart("fe80::1%wlan0"))
        assertEquals("172.16.10.100", hostPart("172.16.10.100"))
    }

    private companion object {
        const val SUNSHINE_FREE = """<?xml version="1.0" encoding="utf-8"?>
<root status_code="200"><hostname>game-win</hostname><appversion>7.1.431.-1</appversion><GfeVersion>3.23.0.74</GfeVersion><uniqueid>8FE9E201-578A-7C2C-B017-F2A9A9D86886</uniqueid><HttpsPort>47984</HttpsPort><ExternalPort>47989</ExternalPort><MaxLumaPixelsHEVC>1869449984</MaxLumaPixelsHEVC><mac>00:00:00:00:00:00</mac><LocalIP>172.16.10.100</LocalIP><ServerCodecModeSupport>2032385</ServerCodecModeSupport><PairStatus>0</PairStatus><currentgame>0</currentgame><state>SUNSHINE_SERVER_FREE</state></root>"""
    }
}
