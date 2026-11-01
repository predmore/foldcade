package app.foldcade.localfolder

import app.foldcade.api.plugin.ArtworkRole
import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalMediaIndexTest {
    @Test fun scanDefersMediaAndIndexMergesEsDeAndLegacyFacts() = runBlocking {
        val tree = MediaTree()
        val root = tree.dir("Library")
        val psx = tree.dir("psx", root)
        val sub = tree.dir("RPG", psx)
        val rom = tree.file("Game (USA).m3u", sub)
        val media = tree.dir("media", psx)
        val images = tree.dir("images", media)
        val cover = tree.file("different-cover.png", images)
        tree.file("gamelist.xml", psx, """<gameList><game><path>./RPG/Game (USA).m3u</path>
            <name>Game</name><desc>Legacy description</desc><image>./media/images/different-cover.png</image>
            <releasedate>19970131T000000</releasedate></game></gameList>""")
        val es = tree.dir("ES-DE", root)
        val downloaded = tree.dir("downloaded_media", es)
        val system = tree.dir("psx", downloaded)
        val screens = tree.dir("screenshots", system)
        val screenSub = tree.dir("RPG", screens)
        val screen = tree.file("Game (USA).webp", screenSub)
        val titles = tree.dir("titlescreens", system)
        tree.file("Game (USA).png", tree.dir("RPG", titles))
        tree.file("not-a-game.chd", tree.dir("videos", system))
        val scan = scanFolderTree(root, childrenOf = tree::children)
        assertEquals(1, scan.games.size)
        assertEquals("RPG/Game (USA).m3u", scan.games.single().relativePath)
        assertEquals(psx.documentUri, scan.games.single().systemUri)
        assertFalse(tree.queries.containsKey(downloaded.documentUri))
        assertFalse(tree.queries.containsKey(media.documentUri))
        tree.queries.clear()
        val index = buildLocalMediaIndex(scan, tree::children, tree::read)
        assertTrue(tree.queries.values.all { it == 1 })
        val provider = LocalMediaProvider()
        assertTrue(provider.replace(index))
        assertFalse(provider.replace(index))
        val game = Game(LocalFolderBackend.ID, rom.documentUri, "playstation", Availability.LocalOnly, "Game")
        assertEquals(setOf(cover.documentUri, screen.documentUri), provider.fetch(game)!!.artwork.map { it.uri }.toSet())
        assertEquals(1997, provider.cachedDetails(game)?.releaseYear)
        assertEquals("Legacy description", provider.details(game)?.summary)
        assertNull(provider.cached(game.copy(backendId = "romm")))
        assertNull(provider.cached(game.copy(platformId = "snes")))
        provider.replace(LocalMediaIndex.Empty)
        assertNull(provider.cached(game))
    }

    @Test fun standaloneGamelistsAndCommonImgsWorkAcrossPickedTrees() {
        val tree = MediaTree()
        val root = tree.dir("Forest")
        val gba = tree.dir("gba", root)
        val rom = tree.file("A & B.gba", gba)
        val image = tree.file("A & B.png", tree.dir("Imgs", gba))
        val es = tree.dir("ES-DE", root)
        val lists = tree.dir("gamelists", es)
        tree.file("gamelist.xml", tree.dir("gba", lists), """<gameList><game><path>./A &amp; B.gba</path>
            <desc>An adventure.</desc><genre>Adventure</genre></game></gameList>""")
        val scan = scanFolderTree(root, childrenOf = tree::children)
        val index = buildLocalMediaIndex(scan, tree::children, tree::read)
        assertEquals(image.documentUri, index.games[rom.documentUri]!!.artwork.single().uri)
        assertEquals("An adventure.", index.games[rom.documentUri]!!.details?.summary)
        assertTrue(index.sourceUris.contains(lists.documentUri))
    }

    @Test fun gamelistCannotLoadAnInventedUriOrLeaveTheSystemRoot() {
        val tree = MediaTree()
        val root = tree.dir("gba")
        val rom = tree.file("Game.gba", root)
        tree.file("gamelist.xml", root, """<gameList><game><path>Game.gba</path>
            <image>content://other/tree/secret</image><fanart>../outside.png</fanart>
            <desc>Facts still work.</desc></game></gameList>""")
        val index = buildLocalMediaIndex(scanFolderTree(root, childrenOf = tree::children), tree::children, tree::read)
        assertTrue(index.games[rom.documentUri]!!.artwork.isEmpty())
        assertEquals("Facts still work.", index.games[rom.documentUri]!!.details?.summary)
    }

    @Test fun absoluteRomPathsIdentifyFilesButBrokenImagesFallBackWithinSaf() {
        val tree = MediaTree()
        val root = tree.dir("gba")
        val rom = tree.file("Game.gba", root)
        val thumbnail = tree.file("odd-name.png", tree.dir("downloaded_images", root))
        val screen = tree.file("Game.png", tree.dir("screenshots", root))
        val title = tree.file("other-name.png", tree.dir("titlescreens", root))
        tree.file("gamelist.xml", root, """<gameList><game><path>/old/roms/gba/Game.gba</path>
            <image>./missing.png</image><thumbnail>./downloaded_images/odd-name.png</thumbnail>
            <titleshot>./titlescreens/other-name.png</titleshot></game></gameList>""")
        val index = buildLocalMediaIndex(scanFolderTree(root, childrenOf = tree::children), tree::children, tree::read)
        val art = index.games[rom.documentUri]!!.artwork
        assertEquals(thumbnail.documentUri, art.single { it.role == ArtworkRole.Cover }.uri)
        assertEquals(screen.documentUri, art.single { it.role == ArtworkRole.Screenshot }.uri)
        assertFalse(art.any { it.uri == title.documentUri })
    }

    @Test(expected = CancellationException::class)
    fun cancelledIndexNeverPublishes() {
        buildLocalMediaIndex(FolderScan(listOf(FolderGame("x", "x.gba", "x", "game-boy-advance")), emptyList()),
            { emptyList() }, { _, _ -> null }, { true })
    }

    @Test fun realisticColdIndexQueriesDirectoriesOnceAndKeepsOnlyMatchedGames() {
        val tree = MediaTree()
        val root = tree.dir("Forest")
        val roms = tree.dir("ROMs", root)
        val es = tree.dir("ES-DE", root)
        val downloaded = tree.dir("downloaded_media", es)
        val gamelists = tree.dir("gamelists", es)
        val systemNames = listOf("nes", "snes", "gba", "gb", "gbc", "n64", "gc", "wii", "psx", "ps2",
            "psp", "nds", "n3ds", "megadrive", "mastersystem", "gamegear", "segacd", "sega32x", "pcengine", "saturn")
        for (systemName in systemNames) {
            assertNotNull(systemName, platformFromFolderName(systemName))
            val systemRoms = tree.dir(systemName, roms)
            val mediaSystem = tree.dir(systemName, downloaded)
            val roles = listOf("covers", "screenshots", "marquees", "fanart").map { tree.dir(it, mediaSystem) }
            val body = StringBuilder("<gameList>")
            repeat(100) { n ->
                val name = "Game ${n.toString().padStart(3, '0')} (USA)"
                tree.file("$name.zip", systemRoms)
                for (role in roles) tree.file("$name.png", role)
                body.append("<game><path>./$name.zip</path><desc>${"An adventure with a hero. ".repeat(24)}</desc>")
                    .append("<genre>Adventure / Platform</genre><releasedate>19950101T000000</releasedate>")
                    .append("<developer>Example Studio</developer><publisher>Example Publisher</publisher><players>1-2</players></game>")
            }
            tree.file("gamelist.xml", tree.dir(systemName, gamelists), body.append("</gameList>").toString())
        }
        val scan = scanFolderTree(root, childrenOf = tree::children)
        val scanQueries = tree.queries.values.sum()
        assertEquals(2_000, scan.games.size)
        tree.queries.clear()
        val start = System.nanoTime()
        val index = buildLocalMediaIndex(scan, tree::children, tree::read)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(2_000, index.games.size)
        assertEquals(8_000, index.artworkUris.size)
        assertTrue(tree.queries.values.all { it == 1 })
        assertEquals(122, tree.queries.values.sum())
        println("MEDIA_INDEX: games=${index.games.size}, scanQueries=$scanQueries, mediaQueries=${tree.queries.values.sum()}, textReads=${tree.textReads}, elapsedMs=$elapsedMs")
        val chars = index.games.values.sumOf { entry ->
            entry.artwork.sumOf { it.uri.length } + entry.details!!.let { details ->
                details.summary!!.length + details.genres.sumOf(String::length) + details.developer!!.length +
                    details.publisher!!.length + details.players!!.length
            } + entry.stemPath.length + entry.romPath.orEmpty().length
        }
        val estimate = estimateMediaHeap(index)
        println("MEDIA_INDEX: retainedChars=$chars, charsPerGame=${chars / 2_000}; UTF16 object graph estimate=$estimate bytes, bytesPerGame=${estimate / 2_000}")
    }
}

private class MediaTree {
    private val paths = HashMap<String, String>()
    private val files = HashMap<String, MutableList<FolderEntry>>()
    private val text = HashMap<String, String>()
    val queries = HashMap<String, Int>()
    var textReads = 0
    fun dir(name: String, parent: FolderEntry? = null): FolderEntry = add(name, DOCUMENT_DIRECTORY_MIME, parent)
    fun file(name: String, parent: FolderEntry, body: String? = null): FolderEntry =
        add(name, "application/octet-stream", parent).also { if (body != null) text[it.documentUri] = body }
    private fun add(name: String, mime: String, parent: FolderEntry?): FolderEntry {
        val path = parent?.let { paths.getValue(it.documentUri) + "/" }.orEmpty() + name
        val id = java.net.URLEncoder.encode("primary:Emulation/$path", "UTF-8").replace("+", "%20")
        val entry = FolderEntry("content://com.android.externalstorage.documents/tree/primary%3AEmulation/document/$id", name, mime)
        paths[entry.documentUri] = path
        files[entry.documentUri] = ArrayList()
        parent?.let { files.getValue(it.documentUri).add(entry) }
        return entry
    }
    fun children(entry: FolderEntry): List<FolderEntry> {
        queries[entry.documentUri] = queries.getOrDefault(entry.documentUri, 0) + 1
        return files.getValue(entry.documentUri)
    }
    fun read(entry: FolderEntry, maxBytes: Int): String? {
        textReads++
        return text[entry.documentUri]?.takeIf { it.toByteArray().size <= maxBytes }
    }
}

/** Conservative heap estimate: 16-byte headers, four-byte refs, eight-byte alignment and UTF-16 strings. */
private fun estimateMediaHeap(root: Any): Long {
    val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
    fun aligned(size: Long) = (size + 7) / 8 * 8
    fun tableCapacity(size: Int): Int { var capacity = 16; while (capacity * 3 / 4 < size) capacity *= 2; return capacity }
    fun bytes(value: Any?): Long {
        if (value == null || value is Enum<*> || !seen.add(value)) return 0
        return when (value) {
            is String -> 24 + aligned(16 + value.length * 2L)
            is Map<*, *> -> 56 + aligned(16 + tableCapacity(value.size) * 4L) +
                value.entries.sumOf { 40 + bytes(it.key) + bytes(it.value) }
            is Set<*> -> 16 + 48 + aligned(16 + tableCapacity(value.size) * 4L) +
                value.sumOf { 32 + bytes(it) }
            is Collection<*> -> 24 + aligned(16 + value.size * 4L) + value.sumOf { bytes(it) }
            else -> {
                val fields = value.javaClass.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
                aligned(16 + fields.sumOf { if (it.type == java.lang.Long.TYPE || it.type == java.lang.Double.TYPE) 8L else 4L }) +
                    fields.filterNot { it.type.isPrimitive }.sumOf { it.isAccessible = true; bytes(it.get(value)) }
            }
        }
    }
    return bytes(root)
}
