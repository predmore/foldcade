package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class SaveProtectionTest {
    private val root = FolderRef("primary:saves/Azahar", "saves/Azahar")
    private val live = FolderRef("primary:Azahar", "Azahar")
    private val evidence = Evidence(EvidenceKind.FreshLog, "Azahar’s log")
    private val incident = SaveIncident("WrongFolder:azahar", IncidentKind.WrongFolder, "azahar", "Azahar", emptyList(), root, live, evidence, 20)
    private val verified = LocationFact.Verified(root, evidence, 20)
    private val facts = SaveFacts(SaveKeeper.Emulator("Azahar"), verified, SpotFact.Resolved, OffDeviceFact.OnRomm(10), null, emptyList(), true)

    @Test fun fullPrecedenceTable() {
        assertEquals(SaveStatus.Problem(incident), deriveSaveStatus(facts.copy(incidents = listOf(incident), waitingSinceMillis = 1)))
        assertTrue(deriveSaveStatus(facts.copy(location = LocationFact.Mismatch(root, live, evidence, 20))) is SaveStatus.Problem)
        for (why in listOf(NoDestinationWhy.SignedOut, NoDestinationWhy.ServerTooOld)) {
            assertEquals(SaveStatus.NotSyncing(destination = why), deriveSaveStatus(facts.copy(location = LocationFact.Missing(SaveIssue.NoFolder), offDevice = OffDeviceFact.NoDestination(why))))
        }
        assertEquals(SaveStatus.NotSyncing(SaveIssue.LostAccess), deriveSaveStatus(facts.copy(location = LocationFact.Missing(SaveIssue.LostAccess), waitingSinceMillis = 1)))
        assertEquals(SaveStatus.NotSyncing(spot = SpotIssue.NoTitleId), deriveSaveStatus(facts.copy(spot = SpotFact.Unresolved(SpotIssue.NoTitleId))))
        assertEquals(SaveStatus.Waiting(1), deriveSaveStatus(facts.copy(waitingSinceMillis = 1)))
        for (why in listOf(NoDestinationWhy.NotOnRomm, NoDestinationWhy.PlayerCantSync)) {
            assertTrue(deriveSaveStatus(facts.copy(offDevice = OffDeviceFact.NoDestination(why))) is SaveStatus.DeviceOnly)
        }
        val keepers = listOf(SaveKeeper.Partner("DroidDeck", "Steam Cloud") to UnconfirmedWhy.PartnerCloud,
            SaveKeeper.HostComputer to UnconfirmedWhy.HostComputer, SaveKeeper.App to UnconfirmedWhy.AppKeepsSaves, SaveKeeper.NoPlayer to UnconfirmedWhy.NoPlayer)
        for ((keeper, why) in keepers) {
            assertEquals(why, (deriveSaveStatus(facts.copy(keeper = keeper)) as SaveStatus.Unconfirmed).why)
            assertEquals(SaveStatus.Waiting(1), deriveSaveStatus(facts.copy(keeper = keeper, waitingSinceMillis = 1)))
        }
        assertEquals(SaveStatus.Unconfirmed(UnconfirmedWhy.FolderNotChecked), deriveSaveStatus(facts.copy(location = LocationFact.Bound(root))))
        assertEquals(SaveStatus.Unconfirmed(UnconfirmedWhy.GameNotChecked), deriveSaveStatus(facts.copy(spot = SpotFact.Unchecked)))
        assertEquals(SaveStatus.Unconfirmed(UnconfirmedWhy.RommUnreachable, 10), deriveSaveStatus(facts.copy(offDevice = OffDeviceFact.Unreachable(10))))
        assertEquals(SaveStatus.NothingYet, deriveSaveStatus(facts.copy(offDevice = OffDeviceFact.NothingYet)))
        assertEquals(SaveStatus.Protected(10, 20), deriveSaveStatus(facts))
        assertEquals(SaveStatus.Protected(10, 20), deriveSaveStatus(facts.copy(incidents = listOf(incident.copy(repair = RepairState.Done)))))
        assertTrue(deriveSaveStatus(facts.copy(offDevice = OffDeviceFact.Unknown)) is SaveStatus.Unconfirmed)
    }

    @Test fun noProtectedWithoutVerifiedAndOnRomm() {
        val locations = listOf(LocationFact.NotApplicable, LocationFact.Missing(SaveIssue.NoFolder), LocationFact.Bound(root), verified,
            LocationFact.Mismatch(root, live, evidence, 20))
        val destinations = listOf(OffDeviceFact.OnRomm(null), OffDeviceFact.OnRomm(10), OffDeviceFact.NothingYet,
            OffDeviceFact.NoDestination(NoDestinationWhy.NotOnRomm), OffDeviceFact.NoDestination(NoDestinationWhy.SignedOut),
            OffDeviceFact.Unreachable(null), OffDeviceFact.PartnerUnknown("Steam"), OffDeviceFact.Unknown)
        for (location in locations) for (destination in destinations) for (spot in listOf(SpotFact.Unchecked, SpotFact.Resolved, SpotFact.Unresolved(SpotIssue.Unreadable))) {
            val status = deriveSaveStatus(facts.copy(location = location, offDevice = destination, spot = spot))
            assertEquals(location is LocationFact.Verified && destination is OffDeviceFact.OnRomm && spot == SpotFact.Resolved, status is SaveStatus.Protected)
        }
    }

    @Test fun badgesAndCopyTellTheTruth() {
        assertFalse(SaveStatus.Unconfirmed(UnconfirmedWhy.FolderNotChecked).needsAttention)
        assertFalse(SaveStatus.DeviceOnly(NoDestinationWhy.NotOnRomm).needsAttention)
        assertTrue(SaveStatus.DeviceOnly(NoDestinationWhy.NotOnRomm, playedHere = true).needsAttention)
        assertEquals("Save folder not checked yet", saveStatusLine(SaveStatus.Unconfirmed(UnconfirmedWhy.FolderNotChecked)))
        assertEquals("Saves in Steam Cloud · not confirmed", saveStatusLine(SaveStatus.Unconfirmed(UnconfirmedWhy.PartnerCloud, keeper = SaveKeeper.Partner("DroidDeck", "Steam Cloud"))))
        assertEquals("Saved to RomM", saveStatusLine(SaveStatus.Protected(null, 20)))
        assertFalse(SaveProtectionCopy.body(incident).contains("safe"))
        assertTrue(SaveProtectionCopy.body(incident, hasVersions = true).contains("Your saves on RomM are safe."))
        assertEquals("Play anyway", dialogButtonLabel(DialogButton.PlayWithoutSyncing))
    }

    @Test fun attentionLeadsButTransferProgressStillShows() {
        val fault = SaveStatus.NotSyncing(SaveIssue.NoFolder)
        val waiting = SaveStatus.Waiting(1)
        assertTrue(saveHealthLeads(fault, TileActivity.Syncing))
        assertNull(heroActivityLine(fault, TileActivity.Syncing))
        assertFalse(saveHealthLeads(waiting, TileActivity.Syncing))
        assertEquals(Copy.syncingSaves, heroActivityLine(waiting, TileActivity.Syncing))
        assertTrue(saveHealthLeads(fault, TileActivity.Downloading(.5f)))
        assertTrue(saveHealthLeads(SaveStatus.Unconfirmed(UnconfirmedWhy.NoPlayer), null))
    }

    @Test fun anUnknownLiveRootDescribesTheLastSessionWithoutClaimingAPermanentMove() {
        val bound = root.copy(identity = "primary:Azahar", label = "Azahar")
        val unknown = incident.copy(bound = bound, live = null)
        val opening = "Azahar didn’t use the folder Azahar last time you played."
        val action = "Fix it first, or this session’s progress isn’t protected."
        assertEquals("Azahar is saving somewhere else", SaveProtectionCopy.title(unknown))
        assertEquals("$opening $action", SaveProtectionCopy.body(unknown))
        assertEquals("$opening ${SaveProtectionCopy.safeOnRomm(10, false)} $action", SaveProtectionCopy.body(unknown, 10))
        assertEquals("$opening Your saves on RomM are safe. $action", SaveProtectionCopy.body(unknown, hasVersions = true))
        assertEquals("Azahar’s log in Azahar didn’t change while you played.", SaveProtectionCopy.unchangedLog("Azahar", bound))
        // An incident saved by the previous build gets the same Check/Fix detail without a migration.
        assertEquals("Azahar’s log in Azahar didn’t change while you played.",
            SaveProtectionCopy.incidentDetail(unknown.copy(evidence = evidence.copy(detail = "log/azahar_log.txt did not advance after launch"))))
        assertEquals(evidence.detail, SaveProtectionCopy.incidentDetail(incident))
    }

    @Test fun folderSetHasNoCheckAndAnIncidentReturnsToTheSetupCount() {
        val setting = PlayerSaveSetting("azahar", "Azahar", true, installed = true, location = LocationFact.Bound(root))
        val model = PickerModel(1, 1, playerSaves = listOf(setting), boardEmulators = setOf("azahar"))
        val tile = Row.SettingsTile(SettingsInner.Emulator("azahar"))
        assertNull(settingsTileStatus(tile, model))
        assertEquals(SetupStatus.FolderSet, setupStatus(saveSetupTiles(model, SaveSetupPage()).single(), SaveSetupPage()))
        val checked = model.copy(playerSaves = listOf(setting.copy(location = verified)))
        assertEquals(TileStatus.Fine, settingsTileStatus(tile, checked))
        val broken = checked.copy(playerSaves = listOf(setting.copy(location = verified, incident = incident)), saveIncidents = listOf(incident))
        assertEquals(TileStatus.Attention, settingsTileStatus(tile, broken))
        assertEquals(1, savesDue(broken).size)
        assertEquals(SetupStep.Fix(incident.id), savesDue(broken).single().step)
        assertEquals("Wrong folder", rowText(Row.SaveCheck("azahar"), broken).value)
        val fix = broken.copy(saveFix = SaveFixPage(incident.id, HostScreen.Bottom, null))
        assertEquals(Effect.PickFixFolder(incident.id), reduce(fix, Meaning.Activate).second)
        assertNull(reduce(fix, Meaning.Activate, repeatCount = 1).second)
        assertEquals(Effect.CancelSaveFix, reduce(fix, Meaning.Back).second)
        assertNull(reduce(fix, Meaning.Options).second)
        val maybe = incident.copy(kind = IncidentKind.MaybeWrongFolder)
        assertEquals(Effect.ConfirmSaveLocation(incident.id), reduce(fix.copy(saveIncidents = listOf(maybe)), Meaning.Options).second)
    }

}
