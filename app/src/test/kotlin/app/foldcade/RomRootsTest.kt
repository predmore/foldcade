package app.foldcade

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Foldcade finds `ROMs`, `roms` and `Emulation/roms` on each volume by itself. */
class RomRootsTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun volume(id: String, vararg dirs: String): VolumeDir {
        val root = temporary.newFolder(id)
        dirs.forEach { File(root, it).mkdirs() }
        return VolumeDir(id, root)
    }

    @Test
    fun aRootCountsWhenItHoldsASystemFolder() {
        val primary = volume("primary", "ROMs/genesis", "ROMs/nds", "ROMs/moonlight", "Emulation/roms/Nintendo - Game Boy Advance", "Download/gba")
        val card = volume("BB42-D432", "roms/psx", "roms/wswan")
        val roots = findRomRoots(listOf(primary, card))
        assertEquals(
            listOf(
                RomRoot("primary", "ROMs", listOf("Nintendo DS", "Sega Genesis")),
                RomRoot("primary", "Emulation/roms", listOf("Game Boy Advance")),
                RomRoot("BB42-D432", "roms", listOf("PlayStation", "WonderSwan")),
            ),
            roots,
        )
    }

    /** The Thor's ROMs folder holds only Moonlight's .dpt shortcuts, which is not a ROM folder. */
    @Test
    fun aRootOfShortcutsAloneIsNotOne() {
        val primary = volume("primary", "ROMs/moonlight", "emulation/ROMS/bios")
        File(primary.dir, "ROMs/moonlight/Forza Horizon 6.dpt").writeText("{}")
        assertEquals(emptyList<RomRoot>(), findRomRoots(listOf(primary)))
    }

    @Test
    fun gameNativeExportsNeedNoSystemFolderAndRespectRemoval() {
        val primary = volume("primary", "ROMs/GameNative", "GameNative", "Download/GameNative", "Emulation/roms/epic")
        File(primary.dir, "GameNative/A Game.gog").writeText("42")
        val roots = findRomRoots(listOf(primary))
        assertEquals(setOf("ROMs", "Emulation/roms"), roots.map { it.path }.toSet())
        assertTrue(roots.all { it.systems == listOf("GameNative") })
        assertEquals(roots, usableRomRoots(roots, emptyList(), setOf("primary:GameNative")))
        assertEquals(roots.filter { it.path != "ROMs" }, usableRomRoots(roots, listOf("primary:ROMs/GameNative"), emptySet()))
    }

    @Test
    fun gameNativeInstallsAndContainerExportsAreNeverLibraryCandidates() {
        val primary = volume("primary", "GameNative/huge-game", "Download/GameNative/container")
        File(primary.dir, "GameNative/huge-game/bundled.nes").writeText("ROM-shaped PC asset")
        File(primary.dir, "GameNative/accidental.steam").writeText("42")
        File(primary.dir, "Download/GameNative/container/data.gog").writeText("42")
        assertTrue(findRomRoots(listOf(primary)).isEmpty())
    }

    @Test
    fun anyCaseCountsAndALinkIsNotFollowed() {
        val primary = volume("primary", "EMULATION/Roms/3ds", "elsewhere/snes")
        Files.createSymbolicLink(File(primary.dir, "roms").toPath(), File(primary.dir, "elsewhere").toPath())
        assertEquals(listOf(RomRoot("primary", "EMULATION/Roms", listOf("Nintendo 3DS"))), findRomRoots(listOf(primary)))
    }

    @Test
    fun aVolumeThatCannotBeListedHasNone() {
        val primary = volume("primary", "ROMs/gba")
        assertEquals(emptyList<RomRoot>(), findRomRoots(listOf(primary)) { null })
    }

    @Test
    fun picksAndRemovedFoldersKeepFoundOnesAway() {
        val roms = RomRoot("primary", "ROMs", listOf("Sega Genesis"))
        val emulation = RomRoot("primary", "Emulation/roms", listOf("PSP"))
        val card = RomRoot("BB42-D432", "roms", listOf("PlayStation"))
        val all = listOf(roms, emulation, card)
        assertEquals(all, usableRomRoots(all, picked = emptyList(), removed = emptySet()))
        // A pick of the same folder, one inside it, or one around it lists those games already.
        assertEquals(listOf(emulation, card), usableRomRoots(all, listOf("primary:ROMs"), emptySet()))
        assertEquals(listOf(emulation, card), usableRomRoots(all, listOf("primary:ROMs/3ds"), emptySet()))
        assertEquals(listOf(card), usableRomRoots(all, listOf("primary:"), emptySet()))
        // A name prefix is not a folder around it.
        assertEquals(all, usableRomRoots(all, listOf("primary:ROMs2"), emptySet()))
        // Removed stays removed, and a removed folder keeps the ones inside it away too.
        assertEquals(listOf(roms, emulation), usableRomRoots(all, emptyList(), setOf("BB42-D432:roms")))
        assertEquals(listOf(roms, card), usableRomRoots(all, emptyList(), setOf("primary:Emulation")))
        // A removed pick inside a root keeps the root away too, so its games don't come back through it.
        assertEquals(listOf(emulation, card), usableRomRoots(all, emptyList(), setOf("primary:ROMs/genesis")))
    }

    @Test
    fun onlyPicksOnAndroidsStorageCanOverlap() {
        assertEquals(
            listOf("primary:ROMs", "1A2B-3C4D:roms"),
            pickedStorageFolders(
                listOf(
                    "content://com.android.externalstorage.documents/tree/primary%3AROMs",
                    "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3Aroms",
                    "content://com.google.android.apps.docs.storage/tree/acc%3D1%3Bdoc%3Droot",
                    "not a uri",
                ),
            ),
        )
    }

    @Test
    fun aRootIsATreeOnFoldcadesOwnProvider() {
        val tree = romRootTree("app.foldcade.library", "primary:Emulation/roms")
        assertEquals("content://app.foldcade.library/tree/primary%3AEmulation%2Froms", tree)
        assertEquals("primary:Emulation/roms", folderTreeId(tree))
        // A game in it groups under its root as a picked folder's games do.
        assertEquals(tree, folderGrantUri("$tree/document/primary%3AEmulation%2Froms%2Fgba%2FGame.gba"))
        // A player that opens games by path gets the file's path.
        val paths = SharedPaths({ null }, { emptyList() }, libraryAuthority = "app.foldcade.library")
        assertEquals("/storage/emulated/0/Emulation/roms/gba/Game.gba", paths.pathFor("$tree/document/primary%3AEmulation%2Froms%2Fgba%2FGame.gba"))
        assertEquals(
            "/storage/BB42-D432/roms/psx/Disc.cue",
            paths.pathFor("content://app.foldcade.library/tree/BB42-D432%3Aroms/document/BB42-D432%3Aroms%2Fpsx%2FDisc.cue"),
        )
        // Another build's provider is not this one.
        assertEquals(null, paths.pathFor("content://app.foldcade.debug.library/tree/primary%3AROMs/document/primary%3AROMs%2Fa.gba"))
    }

    @Test
    fun namesReadAsTheFolderOrTheCard() {
        assertEquals("ROMs", romRootTileLabel("primary:ROMs"))
        assertEquals("roms", romRootTileLabel("primary:Emulation/roms"))
        assertEquals("SD card", romRootTileLabel("BB42-D432:roms"))
        assertEquals("Emulation/roms", romRootPlace("primary:Emulation/roms"))
        assertEquals("SD card · roms", romRootPlace("BB42-D432:roms"))
    }

    @Test
    fun aRemovableVolumeKeepsItsOwnIds() {
        val card = temporary.newFolder("card")
        File(card, "roms/psx").mkdirs()
        val storage = SharedStorage(card, "BB42-D432")
        assertEquals(File(card.canonicalFile, "roms/psx"), storage.fileOf("BB42-D432:roms/psx"))
        assertEquals("BB42-D432:roms/psx/a.cue", storage.childId("BB42-D432:roms/psx", "a.cue"))
        assertEquals("BB42-D432:roms", storage.parentId("BB42-D432:roms/psx"))
        assertTrue(runCatching { storage.fileOf("primary:roms") }.exceptionOrNull() is SecurityException)
        assertTrue(runCatching { storage.fileOf("BB42-D432:Android/data/x") }.exceptionOrNull() is SecurityException)
        assertTrue(runCatching { storage.fileOf("BB42-D432:roms/../..") }.exceptionOrNull() is SecurityException)
    }
}
