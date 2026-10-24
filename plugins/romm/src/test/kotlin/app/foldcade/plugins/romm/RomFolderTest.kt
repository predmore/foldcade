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
    fun aCueAndItsTracksAreFetchedOneByOneAndTheCueBoots() {
        // As RomM 5.4 lists Castlevania: Symphony of the Night: every file on top, each a `game`.
        val spec = folder(
            RomFileSummary(37, "Castlevania (Track 1).bin", 9, md5Hash = "1".repeat(32), category = "game", isTopLevel = true),
            RomFileSummary(38, "Castlevania (Track 2).bin", 4, md5Hash = "2".repeat(32), category = "game", isTopLevel = true),
            RomFileSummary(39, "Castlevania.cue", 1, md5Hash = "3".repeat(32), category = "game", isTopLevel = true),
        ).bytes()

        assertEquals("Castlevania.cue", spec.fileName)
        assertEquals(listOf(39L), spec.fileIds)
        assertEquals(1L, spec.expectedSize)
        assertEquals("3".repeat(32), spec.md5)
        // The cue names its tracks, so they keep RomM's names.
        assertEquals(listOf("Castlevania (Track 1).bin", "Castlevania (Track 2).bin"), spec.addOns.map { it.fileName })
        assertEquals(listOf("1".repeat(32), "2".repeat(32)), spec.addOns.map { it.md5 })
    }

    @Test
    fun aPlaylistBootsAllItsDiscsAndWithoutOneTheFirstDiscBoots() {
        val discs = arrayOf(
            RomFileSummary(41, "Game (Disc 2).cue", 1, isTopLevel = true),
            RomFileSummary(42, "Game (Disc 2).bin", 9, isTopLevel = true),
            RomFileSummary(43, "Game (Disc 1).cue", 1, isTopLevel = true),
            RomFileSummary(44, "Game (Disc 1).bin", 9, isTopLevel = true),
        )
        val playlist = folder(*discs, RomFileSummary(40, "Game.m3u", 1, isTopLevel = true)).bytes()
        assertEquals("Game.m3u", playlist.fileName)
        assertEquals(listOf(41L, 42L, 43L, 44L), playlist.addOns.map { it.id })

        assertEquals("Game (Disc 1).cue", folder(*discs).bytes().fileName)
    }

    @Test
    fun aDiscFolderThisCannotLayOutKeepsTheRequestItHad() {
        val nested = folder(
            RomFileSummary(11, "Game.cue", 1, isTopLevel = true),
            RomFileSummary(12, "Game.bin", 9, isTopLevel = null),
        ).bytes()
        assertEquals(listOf(11L, 12L), nested.fileIds)

        val sameNameButCase = folder(
            RomFileSummary(11, "Game.cue", 1, isTopLevel = true),
            RomFileSummary(12, "game.bin", 9, isTopLevel = true),
            RomFileSummary(13, "Game.BIN", 9, isTopLevel = true),
        ).bytes()
        assertEquals(listOf(11L, 12L, 13L), sameNameButCase.fileIds)

        val zipped = folder(
            RomFileSummary(11, "Game.cue", 1, isTopLevel = true),
            RomFileSummary(12, "Tracks.zip", 9, isTopLevel = true),
        ).bytes()
        assertEquals(listOf(11L, 12L), zipped.fileIds)

        val twoPlaylists = folder(
            RomFileSummary(11, "A.m3u", 1, isTopLevel = true),
            RomFileSummary(12, "B.m3u", 1, isTopLevel = true),
            RomFileSummary(13, "A.cue", 1, isTopLevel = true),
        ).bytes()
        assertEquals(listOf(11L, 12L, 13L), twoPlaylists.fileIds)

        val noSheet = folder(
            RomFileSummary(11, "Game.iso", 9, isTopLevel = true),
            RomFileSummary(12, "Readme.txt", 1, isTopLevel = true),
        ).bytes()
        assertEquals(listOf(11L, 12L), noSheet.fileIds)
        assertTrue(noSheet.addOns.isEmpty())
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

    @Test
    fun severalDiscsWithNoPlaylistGetOneNamedForTheSet() {
        val set = folder(
            RomFileSummary(41, "Final Fantasy VII (USA) (Disc 2).cue", 1, isTopLevel = true),
            RomFileSummary(42, "Final Fantasy VII (USA) (Disc 2).bin", 9, isTopLevel = true),
            RomFileSummary(43, "Final Fantasy VII (USA) (Disc 1).cue", 1, isTopLevel = true),
            RomFileSummary(44, "Final Fantasy VII (USA) (Disc 1).bin", 9, isTopLevel = true),
            RomFileSummary(45, "Final Fantasy VII (USA) (Disc 10).cue", 1, isTopLevel = true),
            RomFileSummary(46, "Final Fantasy VII (USA) (Disc 10).bin", 9, isTopLevel = true),
        ).bytes()
        val discs = listOf(
            "Final Fantasy VII (USA) (Disc 1).cue",
            "Final Fantasy VII (USA) (Disc 2).cue",
            "Final Fantasy VII (USA) (Disc 10).cue",
        )
        assertEquals("Final Fantasy VII (USA).m3u" to discs, discPlaylist(set))

        val dir = Files.createTempDirectory("rom-playlist")
        try {
            val rom = dir.resolve(set.fileName)
            Files.writeString(rom, "cue")
            val boot = bootFile(rom, set, "playstation")
            assertEquals(dir.resolve("Final Fantasy VII (USA).m3u"), boot)
            assertEquals(discs.joinToString("\n", postfix = "\n"), Files.readString(boot))
            // Marked like a download, so the folder serves it, and kept by the folder's clean-up.
            assertTrue(RommClient.isVerifiedRom(boot, null, null))
            pruneRomFolder(rom, set)
            assertTrue(Files.exists(boot))
            assertEquals(boot, bootFile(rom, set, "playstation"))
            // Players of other platforms keep getting Disc 1.
            assertEquals(rom, bootFile(rom, set, "saturn"))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun aFolderOfWholeDiscImagesIsAGameOfThatManyDiscs() {
        val set = folder(
            RomFileSummary(61, "Parasite Eve (USA) (Disc 2).chd", 8, md5Hash = "2".repeat(32), isTopLevel = true),
            RomFileSummary(62, "Parasite Eve (USA) (Disc 1).chd", 9, md5Hash = "1".repeat(32), isTopLevel = true),
        ).bytes()
        // Fetched one by one, not as RomM's zip of both, with Disc 1 as the game.
        assertEquals("Parasite Eve (USA) (Disc 1).chd", set.fileName)
        assertEquals(listOf(62L), set.fileIds)
        assertEquals(9L, set.expectedSize)
        assertEquals("1".repeat(32), set.md5)
        assertEquals(listOf("Parasite Eve (USA) (Disc 2).chd"), set.addOns.map { it.fileName })
        assertEquals(
            "Parasite Eve (USA).m3u" to listOf("Parasite Eve (USA) (Disc 1).chd", "Parasite Eve (USA) (Disc 2).chd"),
            discPlaylist(set),
        )

        // Separate .iso discs are a set too.
        val isos = folder(
            RomFileSummary(65, "Metal Gear Solid (USA) (Disc 2).iso", 8, isTopLevel = true),
            RomFileSummary(66, "Metal Gear Solid (USA) (Disc 1).iso", 9, isTopLevel = true),
        ).bytes()
        assertEquals("Metal Gear Solid (USA) (Disc 1).iso", isos.fileName)
        assertEquals(listOf(66L), isos.fileIds)
        assertEquals("Metal Gear Solid (USA).m3u", discPlaylist(isos)?.first)

        // One image is a game of one disc.
        val one = folder(
            RomFileSummary(71, "Game.chd", 9, isTopLevel = true),
            RomFileSummary(72, "Readme.txt", 1, isTopLevel = true),
        ).bytes()
        assertEquals(listOf(71L, 72L), one.fileIds)

        // A set may mix a sheet's disc with whole-disc images: the sheet boots, and the playlist has them all.
        val mixed = folder(
            RomFileSummary(81, "Game (Disc 1).cue", 1, isTopLevel = true),
            RomFileSummary(82, "Game (Disc 1).bin", 9, isTopLevel = true),
            RomFileSummary(83, "Game (Disc 3).chd", 9, isTopLevel = true),
            RomFileSummary(84, "Game (Disc 2).chd", 9, isTopLevel = true),
        ).bytes()
        assertEquals("Game (Disc 1).cue", mixed.fileName)
        assertEquals(
            "Game.m3u" to listOf("Game (Disc 1).cue", "Game (Disc 2).chd", "Game (Disc 3).chd"),
            discPlaylist(mixed),
        )
        // A sheet and an image of the same disc are one disc twice: the sheets are the discs.
        val twice = folder(
            RomFileSummary(91, "Game (Disc 1).cue", 1, isTopLevel = true),
            RomFileSummary(92, "Game (Disc 2).cue", 1, isTopLevel = true),
            RomFileSummary(93, "Game (Disc 1).chd", 9, isTopLevel = true),
        ).bytes()
        assertEquals("Game.m3u" to listOf("Game (Disc 1).cue", "Game (Disc 2).cue"), discPlaylist(twice))
    }

    @Test
    fun checkedFirmwareOutlastsANewerCopyThatStoppedPartWay() {
        val dir = Files.createTempDirectory("firmware")
        try {
            val bios = dir.resolve("SCPH1001.BIN")
            Files.writeString(bios, "bios v1")
            assertTrue(RommClient.adoptLocalRom(bios, 7, null))
            Files.writeString(dir.resolve("SCPH1001.BIN.partial"), "bios v")
            assertEquals(listOf("SCPH1001.BIN" to bios), checkedFirmware(dir))
            // A file with no marker was never checked.
            Files.writeString(dir.resolve("other.bin"), "other")
            assertEquals(listOf("SCPH1001.BIN"), checkedFirmware(dir).map { it.first })
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun aPlaylistNamesEachDiscAsItIsKept() {
        // RomM may report a name with space before it, which the disc is kept without.
        val set = folder(
            RomFileSummary(71, " Game (Disc 1).chd", 9, isTopLevel = true),
            RomFileSummary(72, "  Game (Disc 2).chd", 9, isTopLevel = true),
        ).bytes()
        val dir = Files.createTempDirectory("rom-playlist-names")
        try {
            val rom = RommClient.besideRom(dir.resolve("x"), set.fileName)
            Files.writeString(rom, "disc")
            val boot = bootFile(rom, set, "playstation")
            assertEquals("Game (Disc 1).chd\nGame (Disc 2).chd\n", Files.readString(boot))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun oneDiscOrAPlaylistOfItsOwnNeedsNoPlaylist() {
        val one = folder(
            RomFileSummary(1, "Castlevania (USA).cue", 1, isTopLevel = true),
            RomFileSummary(2, "Castlevania (USA) (Track 1).bin", 9, isTopLevel = true),
            RomFileSummary(3, "Castlevania (USA) (Track 2).bin", 9, isTopLevel = true),
        ).bytes()
        assertNull(discPlaylist(one))
        val own = folder(
            RomFileSummary(40, "Game.m3u", 1, isTopLevel = true),
            RomFileSummary(41, "Game (Disc 1).cue", 1, isTopLevel = true),
            RomFileSummary(42, "Game (Disc 2).cue", 1, isTopLevel = true),
        ).bytes()
        assertNull(discPlaylist(own))
        // Discs with no number in their names still get a playlist, in name order.
        val unnumbered = folder(
            RomFileSummary(51, "Game B.cue", 1, isTopLevel = true),
            RomFileSummary(52, "Game A.cue", 1, isTopLevel = true),
        ).bytes()
        assertEquals("Game A.m3u" to listOf("Game A.cue", "Game B.cue"), discPlaylist(unnumbered))
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
