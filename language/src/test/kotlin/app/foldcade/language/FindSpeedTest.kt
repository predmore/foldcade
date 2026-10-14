package app.foldcade.language

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Find ranks on every key press, on the main thread, so a search must stay well inside a
 * frame for a big library and never blow up on an odd title. Budgets are generous for a
 * busy build machine; a search that explored word orders one by one took seconds.
 */
class FindSpeedTest {
    @Test
    fun wordsThatAllMatchEachOtherDoNotExplodeIntoEveryOrder() {
        // Review r1: eleven query words that each start any of eleven title words, then one that
        // starts none, took about 13 seconds for this one title.
        val title = (1..11).joinToString(" ") { "alpha$it" }
        val query = List(10) { "a" }.joinToString(" ") + " zz"
        val took = millis { repeat(REPEATS) { matchTitle(title, query) } }
        assertNull(matchTitle(title, query))
        assertTrue("$REPEATS tries took $took ms", took < ADVERSARIAL_BUDGET_MS)
    }

    @Test
    fun aTitleOneLetterOffEverywhereStaysQuick() {
        // Every query word is a typo away from every title word, and none matches exactly.
        val title = (1..11).joinToString(" ") { "kart${'a' + it}" }
        val query = List(11) { "karx" }.joinToString(" ")
        val took = millis { repeat(REPEATS) { matchTitle(title, query) } }
        assertTrue("$REPEATS tries took $took ms", took < ADVERSARIAL_BUDGET_MS)
    }

    @Test
    fun typingIntoAFiveThousandGameLibraryKeepsUpWithEveryKey() {
        val random = Random(7)
        val words = listOf(
            "the", "legend", "of", "zelda", "super", "mario", "kart", "world", "bros", "party", "pokemon", "red",
            "blue", "metroid", "prime", "castlevania", "dracula", "sonic", "hedgehog", "final", "fantasy", "dragon",
            "quest", "kirby", "star", "fox", "donkey", "kong", "country", "street", "fighter", "mega", "man", "x",
            "2", "3", "64", "advance", "deluxe", "ii", "iii", "returns", "origins", "chronicles", "tactics",
        )
        val entries = List(LIBRARY) { index ->
            val title = List(random.nextInt(1, 9)) { words[random.nextInt(words.size)] }.joinToString(" ")
            FindEntry("g$index", title.replaceFirstChar { it.uppercase() }, FindKind.Game, lastPlayedMillis = random.nextLong(0, 1000))
        }
        val index = FindIndex(entries)
        val all = FindFilter(FindFilterKind.All, FindCopy.all)
        val typed = "the legend of zelda ocarina"
        // Warm the JIT first, as a phone's compiled code is: a cold JVM measures its compiler.
        repeat(WARMUPS) { for (end in 1..typed.length) findResults(index, typed.take(end), all) }
        var slowest = 0L
        for (end in 1..typed.length) {
            val took = millis { findResults(index, typed.take(end), all) }
            slowest = maxOf(slowest, took)
        }
        assertTrue("the slowest key took $slowest ms", slowest < LIBRARY_BUDGET_MS)
    }

    private inline fun millis(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    private companion object {
        const val REPEATS = 100
        const val ADVERSARIAL_BUDGET_MS = 100L
        const val LIBRARY = 5_000
        const val WARMUPS = 10

        // About 1.5 ms a key here, warm; the budget leaves room for a busy machine.
        const val LIBRARY_BUDGET_MS = 40L
    }
}
