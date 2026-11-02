package app.foldcade.localfolder

import app.foldcade.api.plugin.ArtworkRole
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.*
import org.junit.Test

class GamelistTest {
    @Test fun readsEsDeFactsWithAttributesCommentsBomAndEntities() {
        val game = parseGamelist("""
            ﻿<?xml version="1.0"?><gameList>
            <!-- <game><path>fake.zip</path></game> -->
            <folder><path>folder</path><name>Ignored</name></folder>
            <game id="23" source="ScreenScraper">
              <path>./Sub/Dragon &amp; Knight (USA).zip</path>
              <name>Dragon &amp; Knight &#x1F409; &#33;</name>
              <desc><![CDATA[First & second <chapter>. &amp;]]> More &quot;story&quot; &apos;here&apos; & stray.</desc>
              <releasedate>19950311T000000</releasedate><genre>Action, Adventure / RPG</genre>
              <developer>Studio</developer><publisher>Publisher</publisher><players>1-4</players>
              <unknown foo="bar"/><rating>0.95</rating>
            </game></gameList>
        """.trimIndent()).single()
        assertEquals("./Sub/Dragon & Knight (USA).zip", game.path)
        assertEquals("Dragon & Knight 🐉 !", game.name)
        assertEquals("First & second <chapter>. &amp; More \"story\" 'here' & stray.", game.details?.summary)
        assertEquals(1995, game.details?.releaseYear)
        assertEquals(listOf("Action", "Adventure", "RPG"), game.details?.genres)
        assertEquals("1-4", game.details?.players)
    }

    @Test fun legacyMediaTagsHaveStablePrecedenceAndEmptyFieldsHaveNone() {
        val game = parseGamelist("""<gameList><game><path>./x.zip</path>
            <image>./images/x-image.png</image><thumbnail>./thumb/x.png</thumbnail>
            <boxart>./box/x.webp</boxart><marquee>./wheel/x.png</marquee><fanart>./fanart/x.jpg</fanart>
            <titleshot>./titles/x.png</titleshot><screenshot>./snaps/x.png</screenshot>
            <desc/><releasedate>00000000T000000</releasedate><players> </players>
            </game></gameList>""").single()
        assertNull(game.details)
        assertEquals(listOf("./box/x.webp", "./thumb/x.png", "./images/x-image.png"),
            game.images.filter { it.role == ArtworkRole.Cover }.sortedBy { it.rank }.map { it.path })
        assertEquals(mapOf(ArtworkRole.Cover to "./box/x.webp", ArtworkRole.Logo to "./wheel/x.png",
            ArtworkRole.Background to "./fanart/x.jpg", ArtworkRole.Screenshot to "./snaps/x.png"), game.images.groupBy { it.role }.mapValues { (_, images) -> images.first().path })
    }

    @Test fun malformedRecordsDoNotCostLaterGamesAndDoNotExpandDeclarations() {
        val games = parseGamelist("""<!DOCTYPE gameList [<!ENTITY external SYSTEM "file:///secrets">
            <!ENTITY bomb "&bomb;&bomb;">]><gameList>
            <game><path>broken.zip</path><desc>Unclosed
            <game><path>good.zip</path><desc>A stray & and &unknown; &external; &#xD800; &#1114112;</desc></game>
            <game><name>No path</name></game><game><path>last.zip</path><desc>less < than</desc></game>
            <game><path>unfinished.zip</path>
        """)
        assertEquals(listOf("good.zip", "last.zip"), games.map { it.path })
        assertEquals("A stray & and &unknown; &external; &#xD800; &#1114112;", games[0].details?.summary)
        assertEquals("less < than", games[1].details?.summary)
    }

    @Test fun capsDescriptionsAndRefusesOversizedInput() {
        val summary = parseGamelist("<game><path>x.zip</path><desc>${"word ".repeat(20_000)}</desc></game>")
            .single().details!!.summary!!
        assertTrue(summary.length <= 1_200)
        assertTrue(summary.endsWith("…"))
        assertTrue(parseGamelist(" ".repeat(MAX_GAMELIST_BYTES + 1)).isEmpty())
    }

    @Test(expected = CancellationException::class)
    fun cancellationStopsParsing() { parseGamelist("<game><path>x.zip</path></game>") { true } }
}
