package app.foldcade.romm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class RommDetailsJsonTest {
    private fun fixture(name: String) = javaClass.getResourceAsStream("/fixtures/$name")!!.use { it.readBytes() }

    @Test fun readsTheLive54AggregateShapeWithoutGuessingCompanyRoles() {
        val rom = parseRom(parseObject(fixture("rom-details-5.4.1.json")))
        assertTrue(rom.summary!!.startsWith("Sonic is here"))
        assertEquals(listOf("Adventure", "Platform"), rom.genres)
        assertEquals(1437436800000, rom.firstReleaseDate)
        assertEquals(listOf("M2"), rom.developers)
        assertEquals(listOf("Sega Games"), rom.publishers)
        assertEquals("1-2", rom.playerCount)
    }

    @Test fun missingMalformedAndUnidentifiedFactsDoNotBreakTheRom() {
        val base = parseObject(fixture("rom-simple.json"))
        val old = parseRom(base)
        assertNull(old.summary)
        assertTrue(old.developers.isEmpty())
        for (malformed in listOf("null", "[]", "42", "{}",
            """{"first_release_date":{},"genres":["Action",42,{}],"companies":["Unknown role"]}""")) {
            val rom = parseRom(JsonObject(base + ("metadatum" to Json.parseToJsonElement(malformed)) +
                ("summary" to JsonPrimitive(42))))
            assertNull(rom.summary)
            assertNull(rom.firstReleaseDate)
            assertTrue(rom.developers.isEmpty())
            assertTrue(rom.publishers.isEmpty())
            assertTrue(rom.genres.all { it == "Action" })
        }
    }

    @Test fun summariesAreBoundedBeforeEnteringTheCatalog() {
        val base = parseObject(fixture("rom-simple.json"))
        val rom = parseRom(JsonObject(base + ("summary" to JsonPrimitive("x".repeat(100_000)))))
        assertEquals(2_000, rom.summary!!.length)
    }

    @Test fun anOldSerializedCatalogStillDeserializes() {
        val rom = parseRom(parseObject(fixture("rom-simple.json")))
        val old = Json.parseToJsonElement(Json.encodeToString(rom)).jsonObject
        val restored = Json.decodeFromString<RomSummary>(JsonObject(old - setOf("summary", "genres", "firstReleaseDate",
            "developers", "publishers", "playerCount")).toString())
        assertEquals(rom, restored)
    }
}
