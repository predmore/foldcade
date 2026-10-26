package app.foldcade.plugins.romm

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.romm.RomFileSummary
import app.foldcade.romm.RomSummary
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RommSetupTest {
    @Test
    fun metadataUsesTheConnectionAndRejectsOtherBackends() {
        val root = Files.createTempDirectory("romm-meta")
        try {
            RommPlugins.installSupply(RommSupply(cacheRoot = root, dataRoot = root.resolve("data")))
            val library = RommLibrary.open()
            library.configure(mapOf(ROMM_ORIGIN_KEY to "https://one.example"))
            library.connection.catalog.rememberRoms(listOf(rom(42, "One")))
            val metadata = RommMetadata(library.connection)
            assertEquals("One", metadata.cached(game(ROMM_LIBRARY_ID))?.title)
            assertNull(metadata.cached(game("local-folder")))
        } finally {
            RommPlugins.clear()
            root.toFile().deleteRecursively()
        }
    }
}

private fun game(backendId: String) = Game(
    backendId = backendId,
    remoteKey = "42",
    platformId = "snes",
    availability = Availability.RemoteOnly,
    label = backendId,
)

private fun rom(id: Long, name: String) = RomSummary(
    id = id,
    name = name,
    fsName = "$name.sfc",
    fsExtension = "sfc",
    fsSizeBytes = 1,
    platformId = 1,
    platformSlug = "snes",
    platformFsSlug = "snes",
    platformDisplayName = "SNES",
    alternativeNames = emptyList(),
    pathCoverSmall = null,
    pathCoverLarge = null,
    urlCover = null,
    hasSimpleSingleFile = true,
    hasNestedSingleFile = false,
    hasMultipleFiles = false,
    files = listOf(RomFileSummary(1, "$name.sfc", 1)),
)
