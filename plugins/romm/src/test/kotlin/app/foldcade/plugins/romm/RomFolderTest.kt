package app.foldcade.plugins.romm

import app.foldcade.romm.RomFileSummary
import app.foldcade.romm.RomSummary
import app.foldcade.romm.RommClient
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomFolderTest {
    @Test
    fun aFolderWithItsGameOnTopFetchesThatFileAloneAndItsUpdateAndDlcBesideIt() {
        val spec = folder(
            RomFileSummary(10, "Game [Update].nsp", 2, category = "update", isTopLevel = false),
            RomFileSummary(11, "Game.nsp", 5, md5Hash = "a".repeat(32), category = "game", isTopLevel = true),
            RomFileSummary(12, "Game [DLC].nsp", 3, category = "dlc", isTopLevel = false),
            RomFileSummary(13, "Manual.pdf", 4, category = "manual", isTopLevel = false),
        ).bytes()

        assertEquals("Game.nsp", spec.fileName)
        assertEquals(listOf(11L), spec.fileIds)
        assertEquals(5L, spec.expectedSize)
        assertEquals("a".repeat(32), spec.md5)
        // The manual is not something a player loads.
        assertEquals(listOf(10L, 12L), spec.addOns.map { it.id })
    }

    @Test
    fun twoAddOnsOfOneNameEachKeepAFile() {
        val spec = folder(
            RomFileSummary(11, "Game.nsp", 5, category = "game", isTopLevel = true),
            RomFileSummary(20, "Content.nsp", 2, category = "update", isTopLevel = false),
            RomFileSummary(21, "content.nsp", 3, category = "dlc", isTopLevel = false),
            RomFileSummary(22, "game.nsp", 3, category = "dlc", isTopLevel = false),
        ).bytes()

        assertEquals(listOf("Content.nsp", "21-content.nsp", "22-game.nsp"), spec.addOns.map { it.fileName })
    }

    @Test
    fun aFolderThisCannotSplitKeepsTheRequestItHad() {
        // A loose file with no category may be a disc track the game needs.
        val loose = folder(
            RomFileSummary(11, "Game.cue", 1, category = "game", isTopLevel = true),
            RomFileSummary(12, "Track 1.bin", 9, isTopLevel = false),
        ).bytes()
        assertEquals(listOf(11L, 12L), loose.fileIds)
        assertTrue(loose.addOns.isEmpty())

        // A server that does not say which file is on top.
        val unmarked = folder(
            RomFileSummary(11, "Game.nsp", 5),
            RomFileSummary(12, "Update.nsp", 2, category = "update"),
        ).bytes()
        assertEquals(listOf(11L, 12L), unmarked.fileIds)
        assertTrue(unmarked.addOns.isEmpty())
    }

    @Test
    fun theOldUpdateLeavesTheFolderWhenRommReplacesIt() {
        val root = Files.createTempDirectory("romm-prune")
        val game = root.resolve("roms/35/f35/Game.nsp")
        Files.createDirectories(game.parent)
        for (name in listOf("Game.nsp", "Update v2.nsp", "Update v1.nsp")) {
            Files.write(game.resolveSibling(name), byteArrayOf(1))
            Files.writeString(game.resolveSibling("$name.verified"), "{}")
        }
        // A file this did not finish is not this cleanup's to delete.
        Files.write(game.resolveSibling("Other.nsp"), byteArrayOf(1))
        val spec = RomBytes("Game.nsp", listOf(35), 1, addOns = listOf(AddOn(36, "Update v2.nsp", 1)))

        pruneRomFolder(game, spec)

        assertTrue(Files.exists(game))
        assertTrue(Files.exists(game.resolveSibling("Update v2.nsp")))
        assertTrue(Files.exists(game.resolveSibling("Update v2.nsp.verified")))
        assertFalse(Files.exists(game.resolveSibling("Update v1.nsp")))
        assertFalse(Files.exists(game.resolveSibling("Update v1.nsp.verified")))
        assertTrue(Files.exists(game.resolveSibling("Other.nsp")))
    }

    @Test
    fun aGameIsNotHereUntilItsAddOnsAreToo() {
        val root = Files.createTempDirectory("romm-addons")
        val spec = RomBytes("Game.nsp", listOf(35), 1, addOns = listOf(AddOn(36, "Update.nsp", 1)))
        val game = RommClient.romCacheFile(root, 35, spec.fileName, spec.fileIds)
        Files.createDirectories(game.parent)
        Files.write(game, byteArrayOf(1))
        Files.writeString(game.resolveSibling("Game.nsp.verified"), """{"size":1,"md5":"x"}""")
        assertNull(verifiedRom(root, 35, spec))

        Files.write(game.resolveSibling("Update.nsp"), byteArrayOf(1))
        Files.writeString(game.resolveSibling("Update.nsp.verified"), """{"size":1,"md5":"y"}""")
        assertEquals(game, verifiedRom(root, 35, spec))
    }

    @Test
    fun aFolderUriNamesTheFileInsideItsFolderAndMapsBack() {
        val root = Files.createTempDirectory("romm-media")
        val adapter = CachePathContentUri(root, cachePath = ROMM_MEDIA_PATH, folderAuthority = "app.foldcade.romm.folders")
        val file = root.resolve("servers/abc/roms/35/f35/Fantasy Life i.nsp")

        val uri = adapter.folderUriFor(file)

        assertEquals(
            "content://app.foldcade.romm.folders/tree/romm-media%2Fservers%2Fabc%2Froms%2F35%2Ff35" +
                "/document/romm-media%2Fservers%2Fabc%2Froms%2F35%2Ff35%2FFantasy%20Life%20i.nsp",
            uri,
        )
        assertEquals(file.toAbsolutePath().normalize(), adapter.pathFor(uri))
        // A document outside the tree it names, or one that climbs out of the root, maps to nothing.
        assertNull(adapter.pathFor(uri.replace("tree/romm-media%2Fservers%2Fabc%2Froms%2F35%2Ff35", "tree/romm-media%2Fother")))
        assertNull(
            adapter.pathFor(
                "content://app.foldcade.romm.folders/tree/romm-media%2Fa/document/romm-media%2Fa%2F..%2F..%2Fsecret",
            ),
        )
        // Without a folder provider, the plain URI.
        assertEquals(CachePathContentUri(root).uriFor(file), CachePathContentUri(root).folderUriFor(file))
    }

    @Test
    fun documentIdsStayInsideTheirRoots() {
        val media = Files.createTempDirectory("romm-media")
        val private = Files.createTempDirectory("romm-private")
        val documents = RomFolderDocuments(mapOf(ROMM_MEDIA_PATH to media, "romm" to private))
        val file = private.resolve("roms/1/a.nds")

        assertEquals("romm/roms/1/a.nds", documents.documentId(file))
        assertEquals(file.toAbsolutePath().normalize(), documents.pathFor("romm/roms/1/a.nds"))
        assertNull(documents.documentId(media.parent.resolve("elsewhere.bin")))
        assertNull(documents.pathFor("romm/../escape"))
        assertNull(documents.pathFor("romm//a"))
        assertNull(documents.pathFor("unknown/roms/1/a.nds"))
        assertTrue(RomFolderDocuments.isInside("romm/roms/1", "romm/roms/1/a.nds"))
        assertFalse(RomFolderDocuments.isInside("romm/roms/1", "romm/roms/12/a.nds"))
    }

    private fun folder(vararg files: RomFileSummary) = RomSummary(
        id = 35,
        name = "Game",
        fsName = "Game",
        fsExtension = "",
        fsSizeBytes = files.sumOf { it.fileSizeBytes },
        platformId = 3,
        platformSlug = "switch",
        platformFsSlug = "switch",
        platformDisplayName = "Nintendo Switch",
        alternativeNames = emptyList(),
        pathCoverSmall = null,
        pathCoverLarge = null,
        urlCover = null,
        hasSimpleSingleFile = false,
        hasNestedSingleFile = true,
        hasMultipleFiles = false,
        files = files.toList(),
    )
}
