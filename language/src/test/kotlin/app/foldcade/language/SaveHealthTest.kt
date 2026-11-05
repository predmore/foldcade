package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class SaveHealthTest {
    @Test fun afterglowAttentionHasReadableContrastAndItsOwnRole() {
        val theme = builtInTheme()
        fun luminance(color: androidx.compose.ui.graphics.Color): Double {
            fun channel(value: Float): Double = if (value <= .04045f) value / 12.92 else Math.pow((value + .055) / 1.055, 2.4)
            return .2126 * channel(color.red) + .7152 * channel(color.green) + .0722 * channel(color.blue)
        }
        val ratio = (luminance(theme.style.colors.attention) + .05) / (luminance(theme.surface) + .05)
        assertTrue("Attention contrast: $ratio", ratio >= 4.5)
        assertEquals(TokenRole.Attention, theme.style.specs.getValue("colors.attention").role)
    }
    @Test fun everyInputCombinationHasAConservativeOutcome() {
        for (emulator in listOf(false, true)) for (server in listOf(false, true))
            for (supported in listOf(false, true)) for (waiting in listOf(false, true))
                for (issue in listOf(null) + SaveIssue.entries) {
                    val actual = deriveSaveHealth(emulator, server, supported, issue, waiting)
                    val expected = when {
                        !emulator -> SaveHealth.Fine
                        !server || !supported -> SaveHealth.DeviceOnly
                        issue != null -> SaveHealth.NotSyncing(issue)
                        waiting -> SaveHealth.Waiting
                        else -> SaveHealth.Fine
                    }
                    assertEquals(expected, actual)
                }
    }

    @Test fun attentionStaysFirstDuringDownloadsAndTheirResults() {
        for (health in listOf(SaveHealth.Waiting, SaveHealth.NotSyncing(SaveIssue.NoFolder), SaveHealth.DeviceOnly)) {
            assertTrue(saveHealthLeads(health, null))
            assertTrue(saveHealthLeads(health, TileActivity.Paused))
            assertTrue(saveHealthLeads(health, TileActivity.OnHost("Computer")))
            for (activity in listOf(TileActivity.Downloading(null), TileActivity.Checking, TileActivity.Syncing,
                TileActivity.Synced(SyncMark.Synced, 2400))) {
                val syncing = activity == TileActivity.Syncing || activity is TileActivity.Synced
                assertEquals(if (syncing) health is SaveHealth.NotSyncing else health.needsAttention, saveHealthLeads(health, activity))
            }
        }
        assertNull(saveHealthLine(SaveHealth.Fine))
        assertEquals("Saves stay on this device", saveHealthLine(SaveHealth.DeviceOnly))
        assertFalse(SaveHealth.DeviceOnly.needsAttention)
        assertTrue(SaveHealth.Waiting.needsAttention)
        assertTrue(SaveHealth.NotSyncing(SaveIssue.NoSlot).needsAttention)
    }
    @Test fun syncActivityDoesNotRepeatWaitingOrHideASetupFault() {
        val later = TileActivity.Synced(SyncMark.Later, 2400)
        assertFalse(saveHealthLeads(SaveHealth.Waiting, TileActivity.Syncing))
        assertFalse(saveHealthLeads(SaveHealth.Waiting, later))
        assertEquals(Copy.syncingSaves, heroActivityLine(SaveHealth.Waiting, TileActivity.Syncing))
        assertEquals(Copy.savesSyncLater, heroActivityLine(SaveHealth.Waiting, later))
        assertTrue(saveHealthLeads(SaveHealth.Waiting, null))
        val fault = SaveHealth.NotSyncing(SaveIssue.NeedsSetup)
        assertTrue(saveHealthLeads(fault, TileActivity.Syncing))
        assertTrue(saveHealthLeads(fault, later))
        assertNull(heroActivityLine(fault, TileActivity.Syncing))
        assertNull(heroActivityLine(fault, later))
        assertTrue(saveHealthLeads(fault, TileActivity.Downloading(.5f)))
        assertEquals("${Copy.downloading} 50%", heroActivityLine(fault, TileActivity.Downloading(.5f)))
    }

}
