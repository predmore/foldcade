package app.foldcade.plugins.romm

import app.foldcade.romm.RomFileSummary
import app.foldcade.romm.RomSummary
import app.foldcade.romm.RommClient
import app.foldcade.romm.md5File
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
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

    @Test
    fun aZipOfFirmwareIsUnpackedBesideItselfAndOfferedByFile() {
        val dir = Files.createTempDirectory("firmware-zip")
        try {
            val zip = dir.resolve("PS2_BIOS.zip")
            Files.write(
                zip,
                zipOf(
                    "Misc BIOS/ps2-0230a-20080220.bin" to "usa".toByteArray(),
                    "DVD Firmware/" to ByteArray(0),
                    "DVD Firmware/ps2-dvd-110u.bin" to "dvd".toByteArray(),
                    "a/boot.bin" to "first".toByteArray(),
                    "b/boot.bin" to "second".toByteArray(),
                    "keys\\civ.bin" to "key".toByteArray(),
                    "skip/." to "no".toByteArray(),
                ),
            )
            assertTrue(RommClient.adoptLocalRom(zip, Files.size(zip), null))
            assertEquals(listOf("PS2_BIOS.zip"), checkedFirmware(dir).map { it.first })
            val unpacked = unpackFirmwareZip(zip) { it.endsWith("ps2-0230a-20080220.bin") || it.endsWith("boot.bin") }
            assertEquals(
                listOf(
                    "PS2_BIOS.zip/boot.bin",
                    "PS2_BIOS.zip/ps2-0230a-20080220.bin",
                    "PS2_BIOS.zip/ps2-dvd-110u.bin",
                ),
                unpacked.map { it.first },
            )
            assertArrayEquals("first".toByteArray(), Files.readAllBytes(unpacked.first { it.first.endsWith("boot.bin") }.second))
            // The zip itself stays, and the unpacked files are offered with it.
            assertEquals(
                listOf(
                    "PS2_BIOS.zip",
                    "PS2_BIOS.zip/boot.bin",
                    "PS2_BIOS.zip/ps2-0230a-20080220.bin",
                    "PS2_BIOS.zip/ps2-dvd-110u.bin",
                ),
                checkedFirmware(dir).map { it.first },
            )
            // A player that wants no file inside does not unpack a zip that is not one yet.
            val plain = dir.resolve("readme.bin")
            Files.write(plain, "notes".toByteArray())
            assertTrue(RommClient.adoptLocalRom(plain, 5, null))
            assertTrue(unpackFirmwareZip(plain) { true }.isEmpty())
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun aPlayerThatWantsNoZipMemberLeavesTheZipPacked() {
        val dir = Files.createTempDirectory("firmware-zip-packed")
        try {
            val zip = dir.resolve("bios.zip")
            Files.write(zip, zipOf("boot.bin" to "bios".toByteArray()))
            assertTrue(RommClient.adoptLocalRom(zip, Files.size(zip), null))
            assertTrue(unpackFirmwareZip(zip) { !it.contains('/') }.isEmpty())
            assertFalse(Files.exists(dir.resolve("bios.zip.unpacked")))
            assertEquals(listOf("bios.zip"), checkedFirmware(dir).map { it.first })
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun aZipOverTheCapIsNotUnpacked() {
        assertEquals(32L * 1024 * 1024, FIRMWARE_ZIP_FILE_CAP)
        assertEquals(1024L * 1024 * 1024, FIRMWARE_ZIP_TOTAL_CAP)
        val dir = Files.createTempDirectory("firmware-zip-cap")
        try {
            val zip = dir.resolve("bios.zip")
            Files.write(zip, zipOf("big.bin" to ByteArray(8), "small.bin" to ByteArray(4)))
            assertTrue(RommClient.adoptLocalRom(zip, Files.size(zip), null))
            assertTrue(unpackFirmwareZip(zip, fileCap = 7, totalCap = 100) { true }.isEmpty())
            assertFalse(Files.exists(dir.resolve("bios.zip.unpacked")))
            assertFalse(Files.exists(dir.resolve("bios.zip.unpacked.partial")))
            // The two files together are over the unpack, so neither is written.
            assertTrue(unpackFirmwareZip(zip, fileCap = 8, totalCap = 10) { true }.isEmpty())
            assertEquals(listOf("bios.zip"), checkedFirmware(dir).map { it.first })
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun aPartialUnpackIsNotOfferedAndANewZipReplacesTheOldOne() {
        val dir = Files.createTempDirectory("firmware-zip-partial")
        try {
            val zip = dir.resolve("bios.zip")
            Files.write(zip, zipOf("boot.bin" to "v1".toByteArray()))
            assertTrue(RommClient.adoptLocalRom(zip, Files.size(zip), null))
            assertEquals("bios.zip/boot.bin", unpackFirmwareZip(zip) { true }.single().first)
            // A newer copy that stopped part way, beside the finished one, does not hide it.
            Files.createDirectories(dir.resolve("bios.zip.unpacked.partial"))
            Files.write(dir.resolve("bios.zip.unpacked.partial/boot.bin"), "torn".toByteArray())
            assertArrayEquals("v1".toByteArray(), Files.readAllBytes(checkedFirmware(dir).single { it.first.endsWith("boot.bin") }.second))
            // No stamp: the files that were written are not offered.
            Files.delete(dir.resolve("bios.zip.unpacked.verified"))
            assertEquals(listOf("bios.zip"), checkedFirmware(dir).map { it.first })
            Files.write(zip, zipOf("boot.bin" to "v2".toByteArray()))
            assertTrue(RommClient.adoptLocalRom(zip, Files.size(zip), md5File(zip)))
            val replaced = unpackFirmwareZip(zip) { true }.single()
            assertEquals("bios.zip/boot.bin", replaced.first)
            assertArrayEquals("v2".toByteArray(), Files.readAllBytes(replaced.second))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun aStoredMemberWithABadCrcIsNotUnpacked() {
        val dir = Files.createTempDirectory("firmware-zip-crc")
        try {
            val zip = dir.resolve("bios.zip")
            val good = storedZip("boot.bin" to "bios".toByteArray(), "note.txt" to "ok".toByteArray())
            Files.write(zip, good)
            assertTrue(RommClient.adoptLocalRom(zip, Files.size(zip), null))
            val unpacked = unpackFirmwareZip(zip) { true }
            assertEquals(listOf("bios.zip/boot.bin", "bios.zip/note.txt"), unpacked.map { it.first })
            assertArrayEquals("bios".toByteArray(), Files.readAllBytes(unpacked.first().second))
            dir.resolve("bios.zip.unpacked").toFile().deleteRecursively()
            Files.delete(dir.resolve("bios.zip.unpacked.verified"))
            val corrupt = withCrc(good, 0, delta = 1)
            ZipFile(Files.write(zip, corrupt).toFile()).use { archive ->
                val entry = archive.getEntry("boot.bin")
                val bytes = archive.getInputStream(entry).use { it.readBytes() }
                val crc = CRC32().apply { update(bytes) }
                assertEquals("bios", String(bytes))
                assertTrue(crc.value != entry.crc)
            }
            assertTrue(RommClient.adoptLocalRom(zip, Files.size(zip), md5File(zip)))
            assertTrue(unpackFirmwareZip(zip) { true }.isEmpty())
            assertFalse(Files.exists(dir.resolve("bios.zip.unpacked")))
            assertFalse(Files.exists(dir.resolve("bios.zip.unpacked.partial")))
            assertFalse(Files.exists(dir.resolve("bios.zip.unpacked.verified")))
            assertEquals(listOf("bios.zip"), checkedFirmware(dir).map { it.first })
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun aMemberShorterThanItDeclaresIsNotUnpacked() {
        val dir = Files.createTempDirectory("firmware-zip-short")
        try {
            val zip = dir.resolve("bios.zip")
            val payload = ByteArray(10) { 'a'.code.toByte() }
            val stored = storedZip("boot.bin" to payload, "note.txt" to "ok".toByteArray())
            val short = withUncompressedSize(stored, 0, declared = 1_000)
            ZipFile(Files.write(zip, short).toFile()).use { archive ->
                val entry = archive.getEntry("boot.bin")
                val bytes = archive.getInputStream(entry).use { it.readBytes() }
                assertEquals(10, bytes.size)
                assertEquals(1_000L, entry.size)
            }
            assertTrue(RommClient.adoptLocalRom(zip, Files.size(zip), null))
            assertTrue(unpackFirmwareZip(zip) { true }.isEmpty())
            assertFalse(Files.exists(dir.resolve("bios.zip.unpacked")))
            assertFalse(Files.exists(dir.resolve("bios.zip.unpacked.partial")))
            assertFalse(Files.exists(dir.resolve("bios.zip.unpacked.verified")))
            assertEquals(listOf("bios.zip"), checkedFirmware(dir).map { it.first })
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun theFirstCopyOfADuplicatedNameIsUnpacked() {
        val dir = Files.createTempDirectory("firmware-zip-dup")
        try {
            val exact = dir.resolve("exact.zip")
            // ZipOutputStream refuses a repeated name, so the second entry is renamed in place.
            val repeated = withSameName(
                storedZip("boot.bin" to "first".toByteArray(), "boot.bi2" to "second".toByteArray()),
                "boot.bin",
            )
            Files.write(exact, repeated)
            assertTrue(RommClient.adoptLocalRom(exact, Files.size(exact), null))
            val exactUnpacked = unpackFirmwareZip(exact) { true }
            // Android refuses a zip whose entries repeat a name, so nothing is offered.
            // Where the archive opens, the first copy is the one kept.
            val kept = exactUnpacked.singleOrNull()
            assertTrue(kept == null || kept.first == "exact.zip/boot.bin")
            if (kept != null) assertArrayEquals("first".toByteArray(), Files.readAllBytes(kept.second))
            val nested = dir.resolve("nested.zip")
            Files.write(nested, zipOf("a/boot.bin" to "first".toByteArray(), "b/boot.bin" to "second".toByteArray()))
            assertTrue(RommClient.adoptLocalRom(nested, Files.size(nested), null))
            val nestedUnpacked = unpackFirmwareZip(nested) { true }.single()
            assertEquals("nested.zip/boot.bin", nestedUnpacked.first)
            assertArrayEquals("first".toByteArray(), Files.readAllBytes(nestedUnpacked.second))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun aMemberNamedLikeUnpackBookkeepingIsOffered() {
        val dir = Files.createTempDirectory("firmware-zip-names")
        try {
            val zip = dir.resolve("bios.zip")
            // boot.bin.partial before boot.bin: that order used to overwrite the first.
            Files.write(
                zip,
                zipOf(
                    "retail.partial" to "retail-partial".toByteArray(),
                    "retail.verified" to "retail-verified".toByteArray(),
                    "boot.bin.partial" to "boot-partial".toByteArray(),
                    "boot.bin" to "boot".toByteArray(),
                ),
            )
            assertTrue(RommClient.adoptLocalRom(zip, Files.size(zip), null))
            val unpacked = unpackFirmwareZip(zip) { true }
            assertEquals(
                listOf(
                    "bios.zip/boot.bin",
                    "bios.zip/boot.bin.partial",
                    "bios.zip/retail.partial",
                    "bios.zip/retail.verified",
                ),
                unpacked.map { it.first },
            )
            assertArrayEquals("boot".toByteArray(), Files.readAllBytes(unpacked[0].second))
            assertArrayEquals("boot-partial".toByteArray(), Files.readAllBytes(unpacked[1].second))
            assertArrayEquals("retail-partial".toByteArray(), Files.readAllBytes(unpacked[2].second))
            assertArrayEquals("retail-verified".toByteArray(), Files.readAllBytes(unpacked[3].second))
            assertEquals(
                listOf(
                    "bios.zip",
                    "bios.zip/boot.bin",
                    "bios.zip/boot.bin.partial",
                    "bios.zip/retail.partial",
                    "bios.zip/retail.verified",
                ),
                checkedFirmware(dir).map { it.first },
            )
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun aZipReplacedWhileItIsPlannedIsNotStamped() {
        val dir = Files.createTempDirectory("firmware-zip-race")
        try {
            val zip = dir.resolve("bios.zip")
            Files.write(zip, zipOf("boot.bin" to "old".toByteArray()))
            assertTrue(RommClient.adoptLocalRom(zip, Files.size(zip), null))
            val replaced = unpackFirmwareZip(zip, planning = {
                Files.write(
                    zip,
                    zipOf("boot.bin" to "new".toByteArray(), "extra.bin" to "more".toByteArray()),
                )
                assertTrue(RommClient.adoptLocalRom(zip, Files.size(zip), md5File(zip)))
            }) { true }
            assertTrue(replaced.isEmpty())
            assertFalse(Files.exists(dir.resolve("bios.zip.unpacked")))
            assertFalse(Files.exists(dir.resolve("bios.zip.unpacked.partial")))
            assertFalse(Files.exists(dir.resolve("bios.zip.unpacked.verified")))
            assertEquals(listOf("bios.zip"), checkedFirmware(dir).map { it.first })
            val unpacked = unpackFirmwareZip(zip) { true }
            assertEquals(listOf("bios.zip/boot.bin", "bios.zip/extra.bin"), unpacked.map { it.first })
            assertArrayEquals("new".toByteArray(), Files.readAllBytes(unpacked.first { it.first.endsWith("boot.bin") }.second))
            assertArrayEquals("more".toByteArray(), Files.readAllBytes(unpacked.first { it.first.endsWith("extra.bin") }.second))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun twoUnpacksOfOneZipAtOnceKeepEveryMember() {
        val dir = Files.createTempDirectory("firmware-zip-concurrent")
        try {
            val zip = dir.resolve("bios.zip")
            val planned = List(24) { index ->
                "%02d.bin".format(index) to ByteArray(64 * 1024) { ((index * 17 + it) and 0xff).toByte() }
            }
            Files.write(zip, zipOf(*planned.toTypedArray()))
            assertTrue(RommClient.adoptLocalRom(zip, Files.size(zip), null))
            // Both enter the unpack together. They share one work folder.
            val start = CyclicBarrier(2)
            val unpacked = arrayOfNulls<List<Pair<String, Path>>>(2)
            val failures = arrayOfNulls<Throwable>(2)
            val threads = List(2) { index ->
                Thread {
                    try {
                        start.await(30, TimeUnit.SECONDS)
                        unpacked[index] = unpackFirmwareZip(zip) { true }
                    } catch (failure: Throwable) {
                        failures[index] = failure
                    }
                }.apply { isDaemon = true }
            }
            threads.forEach { it.start() }
            threads.forEach { thread ->
                thread.join(60_000)
                assertFalse("unpack did not finish", thread.isAlive)
            }
            assertNull(failures[0]?.stackTraceToString(), failures[0])
            assertNull(failures[1]?.stackTraceToString(), failures[1])
            val expectedNames = planned.map { "bios.zip/${it.first}" }.sorted()
            val expectedBytes = planned.toMap()
            for (index in 0..1) {
                val files = checkNotNull(unpacked[index])
                assertEquals(expectedNames, files.map { it.first })
                for ((name, path) in files) {
                    assertArrayEquals(expectedBytes.getValue(name.substringAfterLast('/')), Files.readAllBytes(path))
                }
            }
            val folder = dir.resolve("bios.zip.unpacked")
            for ((name, bytes) in planned) {
                assertArrayEquals(bytes, Files.readAllBytes(folder.resolve(name)))
            }
            assertFalse(Files.exists(dir.resolve("bios.zip.unpacked.partial")))
            // A later launch trusts the stamp, so a short publish would stay short.
            val again = unpackFirmwareZip(zip) { true }
            assertEquals(expectedNames, again.map { it.first })
            for ((name, path) in again) {
                assertArrayEquals(expectedBytes.getValue(name.substringAfterLast('/')), Files.readAllBytes(path))
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                val entry = ZipEntry(name)
                zip.putNextEntry(entry)
                if (!name.endsWith("/")) zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** A zip whose members are stored, so a bad CRC is not checked by the inflater. */
    private fun storedZip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                val crc = CRC32().apply { update(bytes) }
                val entry = ZipEntry(name)
                entry.method = ZipEntry.STORED
                entry.size = bytes.size.toLong()
                entry.crc = crc.value
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Every entry is named [name]. The zip was written with names of that same length. */
    private fun withSameName(zip: ByteArray, name: String): ByteArray {
        val copy = zip.copyOf()
        val spans = zipSpans(copy)
        val bytes = name.toByteArray(Charsets.UTF_8)
        for (i in spans.locals.indices) {
            check(u16(copy, spans.locals[i] + 26) == bytes.size)
            bytes.copyInto(copy, spans.locals[i] + 30)
            bytes.copyInto(copy, spans.centrals[i] + 46)
        }
        return copy
    }

    /** [entry] keeps its bytes. Its CRC, in the local header and the central directory, moves by [delta]. */
    private fun withCrc(zip: ByteArray, entry: Int, delta: Long): ByteArray {
        val copy = zip.copyOf()
        val spans = zipSpans(copy)
        val crc = u32(copy, spans.locals[entry] + 14) + delta
        putU32(copy, spans.locals[entry] + 14, crc)
        putU32(copy, spans.centrals[entry] + 16, crc)
        return copy
    }

    /** [entry] keeps its stored bytes. Its uncompressed size becomes [declared]. */
    private fun withUncompressedSize(zip: ByteArray, entry: Int, declared: Int): ByteArray {
        val copy = zip.copyOf()
        val spans = zipSpans(copy)
        putU32(copy, spans.locals[entry] + 22, declared.toLong())
        putU32(copy, spans.centrals[entry] + 24, declared.toLong())
        return copy
    }

    private class ZipSpans(val locals: List<Int>, val centrals: List<Int>)

    private fun zipSpans(zip: ByteArray): ZipSpans {
        val locals = mutableListOf<Int>()
        var at = 0
        while (at + 30 <= zip.size && u32(zip, at) == 0x04034b50L) {
            locals += at
            val compressed = u32(zip, at + 18).toInt()
            val nameLen = u16(zip, at + 26)
            val extraLen = u16(zip, at + 28)
            at += 30 + nameLen + extraLen + compressed
        }
        val centrals = mutableListOf<Int>()
        while (at + 46 <= zip.size && u32(zip, at) == 0x02014b50L) {
            centrals += at
            val nameLen = u16(zip, at + 28)
            val extraLen = u16(zip, at + 30)
            val commentLen = u16(zip, at + 32)
            at += 46 + nameLen + extraLen + commentLen
        }
        return ZipSpans(locals, centrals)
    }

    private fun u16(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xff) or ((bytes[at + 1].toInt() and 0xff) shl 8)

    private fun u32(bytes: ByteArray, at: Int): Long =
        (bytes[at].toLong() and 0xff) or
            ((bytes[at + 1].toLong() and 0xff) shl 8) or
            ((bytes[at + 2].toLong() and 0xff) shl 16) or
            ((bytes[at + 3].toLong() and 0xff) shl 24)

    private fun putU32(bytes: ByteArray, at: Int, value: Long) {
        val bits = value.toInt()
        bytes[at] = bits.toByte()
        bytes[at + 1] = (bits ushr 8).toByte()
        bytes[at + 2] = (bits ushr 16).toByte()
        bytes[at + 3] = (bits ushr 24).toByte()
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
