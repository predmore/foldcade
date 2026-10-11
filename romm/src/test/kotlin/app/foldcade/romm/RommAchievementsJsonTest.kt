package app.foldcade.romm

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RommAchievementsJsonTest {
    @Test
    fun achievementsComeInDisplayOrderAndAnUnmatchedRomHasNone() {
        val metadata = Json.parseToJsonElement(
            """
            {"first_release_date": 778377600, "achievements": [
              {"ra_id": 2, "title": "Stage Two", "description": null, "points": 5, "badge_id": "25176",
               "badge_path": "/b/25176.png", "badge_path_lock": "/b/25176_lock.png", "display_order": 2, "type": null},
              {"ra_id": 1, "title": "Stage One", "description": "Clear Stage 1", "points": 4, "badge_id": "25175",
               "badge_path": "/b/25175.png", "display_order": 1},
              "not an object"
            ]}
            """,
        )
        val list = parseAchievements(metadata)
        assertEquals(listOf("Stage One", "Stage Two"), list.map { it.title })
        assertEquals("25175", list[0].badgeId)
        assertNull(list[0].badgePathLock)
        assertNull(list[1].description)
        assertEquals(5, list[1].points)
        // RomM sends {} for a ROM with no RetroAchievements match.
        assertTrue(parseAchievements(Json.parseToJsonElement("{}")).isEmpty())
        assertTrue(parseAchievements(null).isEmpty())
    }

    @Test
    fun theUserCarriesProgressPerGameAndEarnedBadgeIds() {
        val user = parseUser(
            """
            {"id": 1, "ra_username": "player", "ra_progression": {"total": 1, "results": [
              {"rom_ra_id": 120, "max_possible": 80, "num_awarded": 12, "num_awarded_hardcore": 10,
               "earned_achievements": [{"id": "25175", "date": "2024-03-02 18:41:07"}]}
            ]}}
            """.toByteArray(),
        )
        assertEquals("player", user.raUsername)
        val game = user.progress.single()
        assertEquals(120L, game.romRaId)
        assertEquals(80, game.maxPossible)
        assertEquals(setOf("25175"), game.earnedBadgeIds)

        val unlinked = parseUser("""{"id": 1, "ra_username": null, "ra_progression": {}}""".toByteArray())
        assertNull(unlinked.raUsername)
        assertTrue(unlinked.progress.isEmpty())
    }
}
