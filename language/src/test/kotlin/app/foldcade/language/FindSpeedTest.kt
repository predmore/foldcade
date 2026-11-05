package app.foldcade.language

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Find ranks on every key press, on the main thread, so a search must stay well inside a
 * frame for a big library and never blow up on an odd title. A busy machine can stall one
 * sample for tens of milliseconds. An 82 ms key failed a 40 ms budget during a parallel
 * testDebugUnitTest on 2026-11-05, while the same check passed alone. These checks warm up,
 * keep the best of several runs, and then require that best time to grow with the input.
 * A scan that became quadratic, or a word match that tried every order, jumps past the
 * limits below. A stalled sample does not.
 */
class FindSpeedTest {
    @Test
    fun wordsThatAllMatchEachOtherDoNotExplodeIntoEveryOrder() {
        // Review r1: eleven query words that each start any of eleven title words, then one that
        // starts none, took about 13 seconds when every word order was tried. 11!/6! is 55,440,
        // so that walk cannot stay under the growth limit.
        val pace = pace(
            { repeatMatch(allMatchTitle(SMALL_WORDS), allMatchQuery(SMALL_WORDS)) },
            { repeatMatch(allMatchTitle(LARGE_WORDS), allMatchQuery(LARGE_WORDS)) },
        )
        assertNull(matchTitle(allMatchTitle(LARGE_WORDS), allMatchQuery(LARGE_WORDS)))
        assertGrowsBelow(pace, WORD_GROWTH_LIMIT, "$LARGE_WORDS all-match words against $SMALL_WORDS")
        assertTrue(
            "$REPEATS tries took ${ms(pace.largeBest)} ms",
            pace.largeBest < ADVERSARIAL_BUDGET_NS,
        )
    }

    @Test
    fun aTitleOneLetterOffEverywhereStaysQuick() {
        // Every query word is a typo away from every title word, and none matches exactly.
        val pace = pace(
            { repeatMatch(typoTitle(SMALL_WORDS), typoQuery(SMALL_WORDS)) },
            { repeatMatch(typoTitle(LARGE_WORDS), typoQuery(LARGE_WORDS)) },
        )
        assertGrowsBelow(pace, WORD_GROWTH_LIMIT, "$LARGE_WORDS one-letter-off words against $SMALL_WORDS")
        assertTrue(
            "$REPEATS tries took ${ms(pace.largeBest)} ms",
            pace.largeBest < ADVERSARIAL_BUDGET_NS,
        )
    }

    @Test
    fun typingIntoAFiveThousandGameLibraryKeepsUpWithEveryKey() {
        val typed = "the legend of zelda ocarina"
        val small = library(SMALL_LIBRARY)
        val large = library(LIBRARY)
        val pace = pace({ typeAll(small, typed) }, { typeAll(large, typed) })
        // 5× titles. Linear stays near 5×; comparing every title with every other is 25×.
        // 12× still clears a lopsided sample and fails that quadratic jump.
        assertGrowsBelow(pace, LIBRARY_GROWTH_LIMIT, "$LIBRARY titles against $SMALL_LIBRARY")
        val slowest = slowestKey(large, typed)
        assertTrue("the slowest key took ${ms(slowest)} ms", slowest < LIBRARY_BUDGET_NS)
    }

    /** One timed pass over [REPEATS] matches. The result is kept so the JIT runs the search. */
    private fun repeatMatch(title: String, query: String) {
        repeat(REPEATS) { sink += matchTitle(title, query)?.at ?: 1 }
    }

    private fun typeAll(index: FindIndex, typed: String) {
        for (end in 1..typed.length) sink += findResults(index, typed.take(end), ALL).size
    }

    /**
     * Best time of each size, and the median of their per-trial ratios. Trials alternate, and
     * the median ignores a trial where the machine stalled only one of the two.
     */
    private fun pace(smallBlock: () -> Unit, largeBlock: () -> Unit): Pace {
        repeat(WARMUPS) {
            smallBlock()
            largeBlock()
        }
        val smalls = LongArray(TRIALS)
        val larges = LongArray(TRIALS)
        val ratios = DoubleArray(TRIALS)
        repeat(TRIALS) { trial ->
            smalls[trial] = nanos(smallBlock)
            larges[trial] = nanos(largeBlock)
            ratios[trial] = larges[trial].toDouble() / smalls[trial].coerceAtLeast(1L).toDouble()
        }
        ratios.sort()
        assertTrue(sink > 0)
        return Pace(smalls.min(), larges.min(), ratios[TRIALS / 2])
    }

    /** Fastest sample of each key press. One stalled press no longer fails the whole check. */
    private fun slowestKey(index: FindIndex, typed: String): Long {
        repeat(WARMUPS) { typeAll(index, typed) }
        val best = LongArray(typed.length) { Long.MAX_VALUE }
        repeat(TRIALS) {
            for (end in 1..typed.length) {
                val start = System.nanoTime()
                sink += findResults(index, typed.take(end), ALL).size
                val took = System.nanoTime() - start
                if (took < best[end - 1]) best[end - 1] = took
            }
        }
        assertTrue(sink > 0)
        return best.max()
    }

    private fun assertGrowsBelow(pace: Pace, limit: Double, label: String) {
        assertTrue(
            "$label grew ${"%.1f".format(pace.medianRatio)}× " +
                "(median of $TRIALS ratios, limit ${"%.0f".format(limit)}×; " +
                "fastest samples ${ms(pace.smallBest)} ms and ${ms(pace.largeBest)} ms)",
            pace.medianRatio < limit,
        )
    }

    private inline fun nanos(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    private fun library(size: Int): FindIndex {
        val random = Random(7)
        val entries = List(size) { index ->
            val title = List(random.nextInt(1, 9)) { WORDS[random.nextInt(WORDS.size)] }.joinToString(" ")
            FindEntry(
                "g$index",
                title.replaceFirstChar { it.uppercase() },
                FindKind.Game,
                lastPlayedMillis = random.nextLong(0, 1000),
            )
        }
        return FindIndex(entries)
    }

    private fun ms(nanos: Long): String = "%.1f".format(nanos / 1_000_000.0)

    private fun allMatchTitle(words: Int): String = (1..words).joinToString(" ") { "alpha$it" }

    /** One fewer "a" than [words]. Each starts every title word. The last word starts none. */
    private fun allMatchQuery(words: Int): String = List(words - 1) { "a" }.joinToString(" ") + " zz"

    private fun typoTitle(words: Int): String = (1..words).joinToString(" ") { "kart${'a' + it}" }

    private fun typoQuery(words: Int): String = List(words) { "karx" }.joinToString(" ")

    private var sink = 0

    private data class Pace(val smallBest: Long, val largeBest: Long, val medianRatio: Double)

    private companion object {
        const val REPEATS = 400
        const val WARMUPS = 5
        const val TRIALS = 9
        const val SMALL_WORDS = 6
        const val LARGE_WORDS = 11
        const val SMALL_LIBRARY = 1_000
        const val LIBRARY = 5_000

        /**
         * How many times slower 11 words may be than 6. A warm run grows about 3×, and a
         * cubic match of these sizes is about 6×. Trying every order is 11!/6! = 55,440×,
         * so 12× still rejects that walk and leaves room when the machine is busy.
         */
        const val WORD_GROWTH_LIMIT = 12.0

        /**
         * How many times slower 5,000 titles may be than 1,000. A warm run grows about 5×,
         * which is the linear scan. Quadratic is 25×, so 12× still catches a search that
         * compares titles in pairs, with room for a lopsided sample.
         */
        const val LIBRARY_GROWTH_LIMIT = 12.0

        /** Best warmed sample of [REPEATS] odd titles. One such title used to take seconds. */
        const val ADVERSARIAL_BUDGET_NS = 250L * 1_000_000L

        /**
         * Best warmed sample of one key in the 5,000-game library. A quiet run is about 1.5 ms;
         * this is the best of [TRIALS], after the 82 ms single-sample stall is thrown out.
         */
        const val LIBRARY_BUDGET_NS = 80L * 1_000_000L

        val ALL = FindFilter(FindFilterKind.All, FindCopy.all)
        val WORDS = listOf(
            "the", "legend", "of", "zelda", "super", "mario", "kart", "world", "bros", "party", "pokemon", "red",
            "blue", "metroid", "prime", "castlevania", "dracula", "sonic", "hedgehog", "final", "fantasy", "dragon",
            "quest", "kirby", "star", "fox", "donkey", "kong", "country", "street", "fighter", "mega", "man", "x",
            "2", "3", "64", "advance", "deluxe", "ii", "iii", "returns", "origins", "chronicles", "tactics",
        )
    }
}
