package app.foldcade.gamestream

import org.junit.Assert.*
import org.junit.Test

class BrokerRequestTest {
    @Test fun stripsEverySpellingOfIdentityAndKeepsOtherParametersEncoded() {
        assertEquals(
            "appid=42&rikey=ab%2Bcd&mode=1920x1080x60&uniqueid=host-id&uuid=request-id",
            brokerQuery("uniqueid=theirs&%75uid=theirs&appid=42&UUID=x&rikey=ab%2Bcd&uniqueid=again&mode=1920x1080x60", "host-id", "request-id"),
        )
    }

    @Test fun queryBoundsAndUnsafeCharactersAreRefused() {
        for (query in listOf("x".repeat(4097), "mode=x#fragment", "x=1\ny=2", "x=1\ry=2", "?x=1", "x=%zz")) {
            try { brokerQuery(query, "id"); fail("Accepted unsafe query") } catch (_: IllegalArgumentException) { }
        }
        assertTrue(brokerQuery("x".repeat(4096), "id").startsWith("x".repeat(4096)))
        assertEquals("uniqueid=id&uuid=req", brokerQuery(null, "id", "req"))
    }

    @Test fun onlyTheFiveDocumentedPathsAreAllowed() {
        assertEquals(setOf("serverinfo", "applist", "launch", "resume", "cancel"), BROKER_PATHS)
        for (path in listOf("pair", "unpair", "/launch", "../launch", "launch?appid=1", "Launch")) assertFalse(path in BROKER_PATHS)
    }

    @Test fun appIdsUseTheSameDecodedNamesAndValuesAsQuerySanitising() {
        assertEquals(listOf("42", "43", "4 2"), brokerAppIds("%61pp%69d=%34%32&APPID=43&appid=4+2&mode=x"))
        assertEquals(emptyList<String>(), brokerAppIds(null))
    }
}
