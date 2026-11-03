package app.foldcade.artwork

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.system.measureNanoTime

/** Opt in with FOLDCADE_MATCHER_TIMING=1; no elapsed-time assertions in CI. */
class ThumbnailIndexTimingTest {
    @Test fun compareRepeatedFullRankingAndExactCoverLookups() {
        assumeTrue(System.getenv("FOLDCADE_MATCHER_TIMING") == "1")
        // A large system, with several releases per title. Both versions see exactly these names.
        val names = (1..2500).flatMap { title ->
            listOf("USA", "Europe", "Japan", "World").map { "Game $title ($it)" }
        }
        var index = ThumbnailIndex(emptyList())
        val builds = List(9) { measureNanoTime { index = ThumbnailIndex(names) } / 1e6 }
        val queries = (1..100).map { "Game ${it * 23} (USA).rom" }
        repeat(2) { for (file in queries) {
            assertEquals(legacyRankedThumbnails(names, file, ""), index.ranked(file, ""))
        } }
        var consumed = 0
        fun batch(lookup: (String) -> List<String>) = measureNanoTime {
            for (file in queries) consumed += lookup(file).sumOf { it.length }
        } / 1e6
        val old = List(7) { batch { legacyRankedThumbnails(names, it, "") } }
        val ranked = List(7) { batch { index.ranked(it, "") } }
        val exact = List(7) { batch { listOfNotNull(index.best(it, "")) } }
        println("MATCHER_TIMING names=${names.size} queries=${queries.size} consumed=$consumed")
        println("index_build_ms=$builds")
        println("old_full_ranking_100_queries_ms=$old")
        println("indexed_full_ranking_100_queries_ms=$ranked")
        println("indexed_exact_cover_100_queries_ms=$exact")
    }
}
