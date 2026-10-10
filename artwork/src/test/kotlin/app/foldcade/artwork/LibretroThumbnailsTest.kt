package app.foldcade.artwork

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LibretroThumbnailsTest {
    /** Names from the server's Nintendo 3DS listing. */
    private val listing = listOf(
        "Bravely Default (USA) (En,Ja,Fr,De,Es,It)",
        "Bravely Second - End Layer (Europe) (En,Fr,De,Es,It)",
        "Bravely Second - End Layer (Japan)",
        "Bravely Second - End Layer (USA) (En,Fr,Es)",
        "Super Mario 3D Land (Europe) (En,Fr,De,Es,It,Nl,Pt,Ru) (Rev 1)",
        "Super Mario 3D Land (Europe) (En,Fr,De,Es,It,Nl,Pt,Ru)",
        "Super Mario 3D Land (USA) (En,Fr,Es) (Rev 1)",
        "Super Mario 3D Land (USA) (En,Fr,Es)",
        "Sonic Boom - Fire _ Ice (Europe) (En,Fr,De,Es,It)",
        "Sonic Boom - Fire _ Ice (USA) (En,Fr,Es)",
        "Sonic Boom - Shattered Crystal (USA) (En,Fr,Es)",
        "Digimon Universe - Appli Monsters (Japan)",
        "Kid Icarus - Uprising (Europe) (En,Fr,De,Es,It)",
        "Kid Icarus - Uprising (USA) (En,Fr,Es)",
        "Legend of Zelda, The - Ocarina of Time 3D (Europe) (En,Fr,De,Es,It)",
        "Legend of Zelda, The - Ocarina of Time 3D (USA) (En,Fr,Es) (Rev 1)",
        "Legend of Zelda, The - Ocarina of Time 3D (USA) (En,Fr,Es)",
        "Luigi's Mansion (Europe) (En,Fr,De,Es,It)",
        "Luigi's Mansion (USA)",
        "Luigi's Mansion - Dark Moon (USA) (En,Fr,Es)",
    )

    @Test
    fun theThorsFilesFindTheirBoxArt() {
        val cases = mapOf(
            "Bravely Second - End Layer (USA) (En,Fr,Es).cci" to "Bravely Second - End Layer (USA) (En,Fr,Es)",
            "Super Mario 3D Land (CTR-P-AREE) (v0.3.0) (U).legit.cci" to "Super Mario 3D Land (USA) (En,Fr,Es)",
            "Sonic Boom- Fire & Ice (CTR-P-BS6E) (v0.0.0) (W).piratelegit.cci" to "Sonic Boom - Fire _ Ice (USA) (En,Fr,Es)",
            "Digimon Universe Appli Monsters [ENG v1.3] For Emu [Citra].cci" to "Digimon Universe - Appli Monsters (Japan)",
            "Kid Icarus- Uprising.cci" to "Kid Icarus - Uprising (USA) (En,Fr,Es)",
            "Legend of Zelda, The - Ocarina of Time 3D (USA) (En,Fr,Es) (Rev 1).cci" to
                "Legend of Zelda, The - Ocarina of Time 3D (USA) (En,Fr,Es) (Rev 1)",
            "Luigi's Mansion (USA).cci" to "Luigi's Mansion (USA)",
        )
        cases.forEach { (file, name) -> assertEquals(file, name, bestThumbnail(listing, file, "")) }
    }

    @Test
    fun aFileFromEuropePrefersTheEuropeanBox() {
        assertEquals(
            "Kid Icarus - Uprising (Europe) (En,Fr,De,Es,It)",
            bestThumbnail(listing, "Kid Icarus - Uprising (Europe).cci", ""),
        )
    }

    @Test
    fun aTitleAloneMatchesWithTheArticleInFront() {
        assertEquals(
            "Legend of Zelda, The - Ocarina of Time 3D (USA) (En,Fr,Es)",
            bestThumbnail(listing, null, "The Legend of Zelda - Ocarina of Time 3D"),
        )
        assertNull(bestThumbnail(listing, null, "Tetris DS"))
        assertNull(bestThumbnail(listing, "Mansion.cci", "Mansion"))
    }

    @Test
    fun theListingIsReadOncePerSystemAndKeptOnDisk() = runBlocking {
        val dir = Files.createTempDirectory("libretro").toFile()
        val http = FakeHttp(
            mapOf(
                "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%203DS/Named_Boxarts/" to ArtResponse(200, page().toByteArray()),
            ),
        )
        val source = LibretroThumbnails(http, dir)
        val query = ArtQuery("k", "Luigi's Mansion", "nintendo-3ds", "Luigi's Mansion (USA).cci")
        val found = source.find(query)
        assertEquals(
            "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%203DS/Named_Boxarts/Luigi%27s%20Mansion%20%28USA%29.png",
            found?.cover,
        )
        source.find(query.copy(key = "k2"))
        assertEquals(1, http.gets)
        // A second source over the same folder reads the saved listing, not the server.
        LibretroThumbnails(FakeHttp(emptyMap()), dir).find(query)
        assertEquals(listOf("Luigi's Mansion (USA)", "Super Mario 3D Land (USA) (En,Fr,Es)"), File(dir, "Nintendo_Nintendo_3DS.txt").readLines())
    }

    @Test
    fun theSceneIsTheTitleScreenThenASnapshot() = runBlocking {
        val listing = "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%203DS/Named_Boxarts/"
        val titles = "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%203DS/Named_Titles/Luigi%27s%20Mansion%20%28USA%29.png"
        val snaps = "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%203DS/Named_Snaps/Luigi%27s%20Mansion%20%28USA%29.png"
        val query = ArtQuery("k", "Luigi's Mansion", "nintendo-3ds", "Luigi's Mansion (USA).cci")
        val pages = mapOf(listing to ArtResponse(200, page().toByteArray()))
        val withTitle = LibretroThumbnails(FakeHttp(pages, mapOf(titles to 200)), Files.createTempDirectory("t").toFile())
        assertEquals(ArtScene(screen = titles), withTitle.scene(query))
        val snapOnly = LibretroThumbnails(FakeHttp(pages, mapOf(snaps to 200)), Files.createTempDirectory("s").toFile())
        assertEquals(ArtScene(screen = snaps), snapOnly.scene(query))
        assertNull(LibretroThumbnails(FakeHttp(pages), Files.createTempDirectory("n").toFile()).scene(query))
    }

    @Test
    fun aSceneTriesTheNextReleaseWhenTheBestHasNoTitle() = runBlocking {
        val base = "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%203DS"
        val page = """
            <a href="Kid%20Icarus%20-%20Uprising%20(USA).png">x</a>
            <a href="Kid%20Icarus%20-%20Uprising%20(USA)%20(En,Fr,Es).png">x</a>
        """.trimIndent()
        val title = "$base/Named_Titles/Kid%20Icarus%20-%20Uprising%20%28USA%29%20%28En%2CFr%2CEs%29.png"
        val http = FakeHttp(mapOf("$base/Named_Boxarts/" to ArtResponse(200, page.toByteArray())), mapOf(title to 200))
        val query = ArtQuery("k", "Kid Icarus- Uprising", "nintendo-3ds", "Kid Icarus- Uprising.cci")
        assertEquals(ArtScene(screen = title), LibretroThumbnails(http, Files.createTempDirectory("k").toFile()).scene(query))
    }

    @Test
    fun anApacheListingParsesToNames() {
        assertEquals(listOf("Luigi's Mansion (USA)", "Super Mario 3D Land (USA) (En,Fr,Es)"), parseListing(page()))
    }

    private fun page(): String = """
        <tr><td><a href="?C=N;O=D">Name</a></td></tr>
        <tr><td><a href="/Nintendo%20-%20Nintendo%203DS/">Parent Directory</a></td></tr>
        <tr><td><a href="Luigi's%20Mansion%20(USA).png">Luigi's Mansion (USA).png</a></td></tr>
        <tr><td><a href="Super%20Mario%203D%20Land%20(USA)%20(En,Fr,Es).png">Super Mario 3D Land</a></td></tr>
    """.trimIndent()
}

internal class FakeHttp(
    private val pages: Map<String, ArtResponse>,
    private val statuses: Map<String, Int> = emptyMap(),
) : ArtHttp {
    var gets = 0
    val heads = mutableListOf<String>()
    val sent = mutableListOf<Pair<String, Map<String, String>>>()

    override suspend fun get(url: String, headers: Map<String, String>): ArtResponse {
        gets++
        sent += url to headers
        return pages[url] ?: throw java.io.IOException("offline: $url")
    }

    override suspend fun status(url: String): Int {
        heads += url
        return statuses[url] ?: 404
    }
}
