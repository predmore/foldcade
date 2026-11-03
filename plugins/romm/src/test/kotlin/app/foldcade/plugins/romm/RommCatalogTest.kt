package app.foldcade.plugins.romm

import app.foldcade.romm.PlatformSummary
import app.foldcade.romm.RomPage
import app.foldcade.romm.RomQuery
import app.foldcade.romm.RomSummary
import app.foldcade.romm.writeDurably
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RommCatalogTest {
    @Test fun a500GameRefreshWritesOnceThenAnUnchangedRefreshWritesNothing() = fixture { file ->
        var writes = 0
        val catalog = RommCatalog(file, ORIGIN) { path, bytes ->
            writes++
            writeDurably(path, bytes)
        }
        fun refresh() {
            catalog.beginRefresh()
            try {
                catalog.rememberPlatforms((1L..10L).map { platform(it, 50) })
                for (platform in 1L..10L) {
                    val games = (1L..50L).map { rom(platform * 100 + it, platform) }
                    catalog.rememberPage(platform, RomQuery(offset = 0, limit = 50), RomPage(games, 50, 50, 0), finished = true)
                    assertEquals(50, catalog.listing(platform)!!.size)
                    assertEquals("Game ${platform * 100 + 1}", catalog.rom((platform * 100 + 1).toString())!!.name)
                }
            } finally {
                catalog.finishRefresh()
            }
        }
        refresh()
        assertEquals(1, writes)
        val cold = RommCatalog(file, ORIGIN).also { it.restore() }
        assertEquals(500, (1L..10L).sumOf { cold.listing(it)!!.size })
        refresh()
        assertEquals(1, writes)
        // Equality survives a process restart, rather than relying on an in-memory dirty bit.
        var coldWrites = 0
        val restarted = RommCatalog(file, ORIGIN) { _, _ -> coldWrites++ }
        restarted.beginRefresh()
        restarted.rememberPlatforms((1L..10L).map { platform(it, 50) })
        for (platform in 1L..10L) {
            val games = (1L..50L).map { rom(platform * 100 + it, platform) }
            restarted.rememberPage(platform, RomQuery(), RomPage(games, 50, 50, 0), finished = true)
        }
        restarted.finishRefresh()
        assertEquals(0, coldWrites)
    }

    @Test fun interruptedPaginationFlushesMetadataAndCompletedSystemsButKeepsTheLastCompleteListing() = fixture { file ->
        var writes = 0
        val catalog = RommCatalog(file, ORIGIN) { path, bytes -> writes++; writeDurably(path, bytes) }
        catalog.beginRefresh()
        catalog.rememberPlatforms(listOf(platform(1, 2), platform(2, 1)))
        catalog.rememberPage(1, RomQuery(), RomPage(listOf(rom(11, 1), rom(12, 1)), 2, 50, 0), true)
        catalog.finishRefresh()
        writes = 0
        catalog.beginRefresh()
        try {
            catalog.rememberPage(1, RomQuery(limit = 1), RomPage(listOf(rom(11, 1).copy(name = "Renamed")), 2, 1, 0), false)
            catalog.rememberPage(2, RomQuery(), RomPage(listOf(rom(21, 2)), 1, 50, 0), true)
            assertEquals(0, writes)
        } finally {
            catalog.finishRefresh()
        }
        assertEquals(1, writes)
        val cold = RommCatalog(file, ORIGIN).also { it.restore() }
        assertEquals(listOf(11L, 12L), cold.listing(1)!!.map { it.id })
        assertEquals("Renamed", cold.rom("11")!!.name)
        assertEquals(listOf(21L), cold.listing(2)!!.map { it.id })
        // A subsequent stray second page must not finish a refresh interrupted earlier.
        catalog.rememberPage(1, RomQuery(offset = 1, limit = 1), RomPage(listOf(rom(13, 1)), 2, 1, 1), true)
        assertEquals(listOf(11L, 12L), catalog.listing(1)!!.map { it.id })
    }

    @Test fun failedPersistenceRetriesAndCredentialOnlyChangesDoNotRewrite() = fixture { file ->
        var attempts = 0
        val catalog = RommCatalog(file, ORIGIN) { path, bytes ->
            attempts++
            if (attempts == 1) throw IOException("disk full")
            writeDurably(path, bytes)
        }
        catalog.beginRefresh()
        catalog.rememberRoms(listOf(rom(11, 1).copy(pathCoverSmall = "/cover?token=first")))
        catalog.finishRefresh()
        assertFalse(Files.exists(file))
        catalog.beginRefresh()
        catalog.finishRefresh()
        assertTrue(Files.exists(file))
        catalog.rememberRoms(listOf(rom(11, 1).copy(pathCoverSmall = "/cover?token=second")))
        assertEquals(2, attempts)
        assertFalse(Files.readString(file).contains("token="))
    }

    private fun platform(id: Long, count: Long) = PlatformSummary(id, "System $id", "System $id", "system$id", "system$id", "", emptyList(), count)
    private fun rom(id: Long, platform: Long) = RomSummary(
        id, "Game $id", "$id.rom", "rom", 9, platform, "system$platform", "system$platform", "System $platform",
        emptyList(), null, null, null, true, false, false, emptyList(),
    )

    private fun fixture(test: (java.nio.file.Path) -> Unit) {
        val root = Files.createTempDirectory("romm-catalog-test")
        try { test(root.resolve("catalog.json")) } finally { root.toFile().deleteRecursively() }
    }

    companion object { private const val ORIGIN = "https://romm.example" }
}
