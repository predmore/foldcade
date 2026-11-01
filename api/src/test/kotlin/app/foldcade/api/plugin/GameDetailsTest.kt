package app.foldcade.api.plugin

import org.junit.Assert.*
import org.junit.Test

class GameDetailsTest {
    @Test fun cleansFactsAndKeepsSourceSpelling() {
        assertEquals(GameDetails("One two three", 1991, listOf("Action", "RPG"), "A B", "C", "1-4"),
            normalizeGameDetails(GameDetails(" One\n two\u00a0three ", 1991,
                listOf(" Action ", "", "action", "RPG"), " A\tB ", " C ", " 1-4 "), 2026))
    }

    @Test fun rejectsEmptyFactsAndImplausibleYears() {
        assertNull(normalizeGameDetails(GameDetails(" \n", 0, listOf(" ")), 2026))
        assertNull(normalizeGameDetails(GameDetails(releaseYear = 1949), 2026))
        assertNull(normalizeGameDetails(GameDetails(releaseYear = 2029), 2026))
        assertEquals(2028, normalizeGameDetails(GameDetails(releaseYear = 2028), 2026)?.releaseYear)
        assertEquals(1950, normalizeGameDetails(GameDetails(releaseYear = 1950), 2026)?.releaseYear)
    }

    @Test fun capsAtSentenceThenWordAndDoesNotSplitSurrogates() {
        val sentence = "a".repeat(700) + "."
        assertEquals(sentence + "…", normalizeGameDetails(GameDetails(sentence + " b".repeat(600)))?.summary)
        assertEquals("word ".repeat(239).trimEnd() + "…",
            normalizeGameDetails(GameDetails("word ".repeat(400)))?.summary)
        val unicode = normalizeGameDetails(GameDetails("🎮".repeat(1_000)))!!.summary!!
        assertTrue(unicode.length <= 1_200)
        assertFalse(unicode[unicode.lastIndex - 1].isHighSurrogate())
    }
}
