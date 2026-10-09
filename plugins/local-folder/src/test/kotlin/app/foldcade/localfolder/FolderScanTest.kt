package app.foldcade.localfolder

import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderScanTest {
    @Test
    fun aTreeBecomesGamesKeyedByDocumentUri() {
        val root = dir(
            "content://tree/roms",
            "roms",
            listOf(
                dir(
                    "content://tree/3ds",
                    "Nintendo 3DS",
                    listOf(
                        dir(
                            "content://tree/3ds/usa",
                            "USA",
                            listOf(
                                file(
                                    "content://tree/3ds/usa/mario",
                                    "Super Mario (USA).cci",
                                ),
                            ),
                        ),
                    ),
                ),
                dir(
                    "content://tree/nds",
                    "nds",
                    listOf(
                        file("content://tree/nds/a", "Zelda.nds"),
                        file("content://tree/nds/a-save", "Zelda.sav"),
                        file("content://tree/nds/a-dsv", "Zelda.dsv"),
                        file("content://tree/nds/notes", "notes.txt"),
                        file("content://tree/nds/zip", "Packed.7z"),
                    ),
                ),
                file("content://tree/loose", "loose.nds"),
                file("content://tree/zip", "archive.zip"),
                dir(
                    "content://tree/bios",
                    "bios",
                    listOf(file("content://tree/bios/fw", "firmware.bin")),
                ),
                dir(
                    "content://tree/ps1",
                    "PlayStation",
                    listOf(
                        file("content://tree/ps1/bin", "Track.bin"),
                        file("content://tree/ps1/cue", "Track.cue"),
                    ),
                ),
            ),
        )

        val scan = scan(root)

        assertEquals(
            listOf(
                game("content://tree/3ds/usa/mario", "Super Mario (USA).cci", "Super Mario (USA)", "nintendo-3ds"),
                game("content://tree/loose", "loose.nds", "loose", "nintendo-ds"),
                game("content://tree/nds/zip", "Packed.7z", "Packed", "nintendo-ds"),
                game("content://tree/nds/a", "Zelda.nds", "Zelda", "nintendo-ds"),
                game("content://tree/ps1/cue", "Track.cue", "Track", "playstation"),
            ),
            scan.games,
        )
        assertEquals(
            listOf("nintendo-3ds", "nintendo-ds", "playstation"),
            scan.platforms.map { it.id },
        )
        assertEquals(
            listOf("Nintendo 3DS", "Nintendo DS", "PlayStation"),
            scan.platforms.map { it.name },
        )
        assertFalse(scan.cancelled)
    }

    @Test
    fun theFolderWinsForArchivesAndTheExtensionWinsOnAContradiction() {
        val root = dir(
            "content://root",
            "roms",
            listOf(
                dir(
                    "content://nds",
                    "Nintendo DS",
                    listOf(
                        file("content://nds/zip", "Cart.zip"),
                        file("content://nds/cci", "Misfiled.cci"),
                    ),
                ),
                dir(
                    "content://snes",
                    "snes",
                    listOf(file("content://snes/zip", "Cart.zip")),
                ),
            ),
        )

        val scan = scan(root)

        assertEquals("nintendo-ds", scan.games.first { it.documentUri == "content://nds/zip" }.platformId)
        assertEquals("nintendo-3ds", scan.games.first { it.documentUri == "content://nds/cci" }.platformId)
        assertEquals("snes", scan.games.first { it.documentUri == "content://snes/zip" }.platformId)
    }

    @Test
    fun sharedDiscImagesFollowTheFolderTheyAreIn() {
        val root = dir(
            "content://root",
            "roms",
            listOf(
                file("content://loose", "loose.iso"),
                dir("content://ps2", "ps2", listOf(file("content://ps2/game", "Game.iso"))),
                dir("content://wii", "wii", listOf(file("content://wii/game", "Game.rvz"))),
                dir("content://gc", "gc", listOf(file("content://gc/game", "Game.iso"))),
            ),
        )

        val scan = scan(root)

        assertEquals(
            listOf(
                game("content://gc/game", "Game.iso", "Game", "gamecube"),
                game("content://ps2/game", "Game.iso", "Game", "playstation-2"),
                game("content://wii/game", "Game.rvz", "Game", "wii"),
            ),
            scan.games,
        )
    }

    @Test
    fun aPickedPlatformFolderIsTheRoot() {
        val root = dir(
            "content://3ds",
            "Nintendo 3DS",
            listOf(file("content://3ds/game", "Game.zcci")),
        )

        val scan = scan(root)

        assertEquals("nintendo-3ds", scan.games.single().platformId)
        assertEquals("Game", scan.games.single().title)
    }

    @Test
    fun aFileRootIsClassifiedAndDoesNotAskForChildren() {
        val game = file("content://rom/game.nds", "game.nds")
        val scan = scanFolderTree(game.entry()) { error("files have no children") }
        assertEquals("nintendo-ds", scan.games.single().platformId)
        assertEquals("content://rom/game.nds", scan.games.single().documentUri)
    }

    @Test
    fun bestEffort3dsExtensionStillClassifies() {
        val scan = scan(file("content://rom/old", "Old.3ds"))
        assertEquals("nintendo-3ds", scan.games.single().platformId)
        assertEquals("Old", scan.games.single().title)
    }

    @Test
    fun markdownIsNotAGenesisGameUnlessTheFolderSaysSo() {
        val root = dir(
            "content://root",
            "roms",
            listOf(
                file("content://readme", "README.md"),
                dir(
                    "content://gen",
                    "genesis",
                    listOf(
                        file("content://gen/readme", "README.md"),
                        file("content://gen/game", "Sonic.md"),
                        file("content://gen/gen", "Sonic.gen"),
                    ),
                ),
            ),
        )

        val scan = scan(root)

        assertEquals(
            listOf(
                game("content://gen/game", "Sonic.md", "Sonic", "genesis"),
                game("content://gen/gen", "Sonic.gen", "Sonic", "genesis"),
            ),
            scan.games,
        )
    }

    @Test
    fun pcExecutablesAreNotFolderGames() {
        val root = dir(
            "content://pc",
            "pc",
            listOf(file("content://pc/game", "Game.exe")),
        )
        assertTrue(scan(root).games.isEmpty())
    }

    @Test
    fun hiddenFilesAndBlankEntriesAreSkipped() {
        val root = dir(
            "content://root",
            "nds",
            listOf(
                file("content://hidden", ".hidden.nds"),
                file("content://apple", "._game.nds"),
                file("content://blank-name", "   "),
                file("   ", "game.nds"),
                file("content://real", " game.NDS "),
            ),
        )

        val scan = scan(root)

        assertEquals(listOf("content://real"), scan.games.map { it.documentUri })
        assertEquals("game", scan.games.single().title)
        assertEquals("nintendo-ds", scan.games.single().platformId)
    }

    @Test
    fun aDirectoryIsNotAGameEvenWhenItsNameHasAnExtension() {
        val root = dir(
            "content://root",
            "roms",
            listOf(
                dir(
                    "content://fake",
                    "game.nds",
                    listOf(file("content://inside", "real.sfc")),
                ),
            ),
        )
        val scan = scan(root)
        assertEquals("snes", scan.games.single().platformId)
        assertEquals("real", scan.games.single().title)
    }

    @Test
    fun posixDirectoryMimeIsAFolder() {
        val root = FolderEntry("content://nds", "nds", "inode/directory")
        val child = file("content://nds/game", "game.nds").entry()
        val scan = scanFolderTree(root) { listOf(child) }
        assertEquals("nintendo-ds", scan.games.single().platformId)
    }

    @Test
    fun duplicateDocumentUrisCollapseToTheFirst() {
        val root = FolderEntry("content://root", "roms", DOCUMENT_DIRECTORY_MIME)
        val first = file("content://same", "Alpha.nds").entry()
        val second = file("content://same", "Beta.nds").entry()
        val scan = scanFolderTree(root) { listOf(first, second) }
        assertEquals("Alpha", scan.games.single().title)
    }

    @Test
    fun aCycleStops() {
        val root = FolderEntry("content://root", "nds", DOCUMENT_DIRECTORY_MIME)
        val nested = FolderEntry("content://nested", "USA", DOCUMENT_DIRECTORY_MIME)
        val game = file("content://game", "game.nds").entry()
        var nestedVisits = 0
        val scan = scanFolderTree(root) { entry ->
            when (entry.documentUri) {
                "content://root" -> listOf(nested)
                "content://nested" -> {
                    nestedVisits += 1
                    listOf(root, game)
                }
                else -> emptyList()
            }
        }
        assertEquals(1, nestedVisits)
        assertEquals("content://game", scan.games.single().documentUri)
        assertFalse(scan.cancelled)
    }

    @Test
    fun cancellationStopsBeforeTheNextDocument() {
        val root = dir(
            "content://root",
            "roms",
            listOf(
                dir(
                    "content://nds",
                    "nds",
                    listOf(file("content://nds/game", "game.nds")),
                ),
            ),
        )
        val listed = mutableListOf<String>()
        var cancel = false
        val scan = scanFolderTree(
            root = root.entry(),
            childrenOf = { entry ->
                listed += entry.documentUri
                if (entry.documentUri == "content://root") cancel = true
                nodes(root)(entry)
            },
            isCancelled = { cancel },
        )
        assertTrue(scan.cancelled)
        assertEquals(listOf("content://root"), listed)
        assertTrue(scan.games.isEmpty())
    }

    @Test
    fun anAlreadyCancelledScanDoesNotWalk() {
        val root = file("content://game", "game.nds")
        val scan = scanFolderTree(root.entry(), isCancelled = { true }) { error("walked") }
        assertTrue(scan.cancelled)
        assertTrue(scan.games.isEmpty())
        assertTrue(scan.platforms.isEmpty())
    }

    @Test
    fun savesArtworkAndFirmwareFoldersAreNotWalked() {
        val listed = mutableListOf<String>()
        val root = dir(
            "content://root",
            "roms",
            listOf(
                dir("content://saves", "saves", listOf(file("content://saves/a", "game.nds"))),
                dir("content://art", "artwork", listOf(file("content://art/a", "cover.png"))),
                dir("content://fw", "firmware", listOf(file("content://fw/a", "bios.bin"))),
                file("content://real", "game.cci"),
            ),
        )
        val scan = scanFolderTree(root.entry()) { entry ->
            listed += entry.documentUri
            nodes(root)(entry)
        }
        assertEquals(listOf("content://root"), listed)
        assertEquals(listOf("content://real"), scan.games.map { it.documentUri })
    }

    @Test
    fun aDeepTreeStaysOnTheHeap() {
        var node = file("content://game", "game.cci")
        repeat(1_000) { depth ->
            node = dir("content://d$depth", "folder", listOf(node))
        }
        val scan = scan(node)
        assertEquals("nintendo-3ds", scan.games.single().platformId)
        assertEquals("game", scan.games.single().title)
    }

    @Test
    fun anEmptyFolderHasNoPlatforms() {
        val scan = scan(dir("content://root", "roms"))
        assertTrue(scan.games.isEmpty())
        assertTrue(scan.platforms.isEmpty())
        assertFalse(scan.cancelled)
    }

    @Test
    fun titlesSortWithinAPlatformAndUrisBreakTies() {
        val root = dir(
            "content://snes",
            "snes",
            listOf(
                file("content://b", "b.sfc"),
                file("content://a2", "A.sfc"),
                file("content://a1", "A.sfc"),
            ),
        )
        assertEquals(
            listOf("content://a1", "content://a2", "content://b"),
            scan(root).games.map { it.documentUri },
        )
    }
}

private class Node(
    val uri: String,
    val name: String,
    val directory: Boolean,
    val children: List<Node> = emptyList(),
) {
    fun entry(): FolderEntry = FolderEntry(
        documentUri = uri,
        displayName = name,
        mimeType = if (directory) DOCUMENT_DIRECTORY_MIME else "application/octet-stream",
    )
}

private fun dir(uri: String, name: String, children: List<Node> = emptyList()): Node =
    Node(uri, name, directory = true, children = children)

private fun file(uri: String, name: String): Node =
    Node(uri, name, directory = false)

private fun nodes(root: Node): (FolderEntry) -> List<FolderEntry> {
    val byUri = HashMap<String, Node>()
    val pending = ArrayDeque<Node>()
    pending.add(root)
    while (pending.isNotEmpty()) {
        val node = pending.removeFirst()
        check(byUri.put(node.uri, node) == null) { "duplicate fixture uri ${node.uri}" }
        pending.addAll(node.children)
    }
    return { entry ->
        byUri[entry.documentUri]?.children?.map { it.entry() }.orEmpty()
    }
}

private fun scan(root: Node): FolderScan =
    scanFolderTree(root.entry(), childrenOf = nodes(root))

private fun game(uri: String, fileName: String, title: String, platformId: String): FolderGame =
    FolderGame(
        documentUri = uri,
        fileName = fileName,
        title = title,
        platformId = platformId,
    )
