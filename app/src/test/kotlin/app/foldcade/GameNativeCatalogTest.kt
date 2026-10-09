package app.foldcade

import app.foldcade.plugins.gamenative.CatalogGame
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class GameNativeCatalogTest {
    @Test
    fun aConfirmedListRoundTripsThroughTheCatalogFile() {
        val dir = Files.createTempDirectory("gamenative-catalog").toFile()
        val file = dir.resolve("catalog.txt")
        val games = listOf(
            CatalogGame("Counter-Strike", 730, "STEAM"),
            CatalogGame("A title", 4, "EPIC"),
        )
        writeCatalog(file, games)
        assertEquals(games, readCatalog(file))
        writeCatalog(file, emptyList())
        assertEquals(emptyList<CatalogGame>(), readCatalog(file))
    }
}