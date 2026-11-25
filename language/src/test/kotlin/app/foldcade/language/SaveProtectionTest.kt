package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class SaveProtectionTest {
    @Test fun retroArchSettingsExplainsAnInaccessibleConfigUntilASaveWriteVerifiesTheFolder() {
        val id = "retroarch.game-boy-advance"
        val root = FolderRef("primary:RetroArch/saves", "RetroArch/saves")
        val setting = PlayerSaveSetting(id, "RetroArch", chosen = true, location = LocationFact.Bound(root, configUnavailable = true))
        val model = PickerModel(count = 0, rowsPerPage = 1, playerSaves = listOf(setting))
        assertEquals("Can’t read config", rowText(Row.SaveCheck(id), model).value)
        val checked = setting.copy(location = LocationFact.Verified(root, Evidence(EvidenceKind.SessionWrite, "Game saved there"), System.currentTimeMillis()))
        assertTrue(rowText(Row.SaveCheck(id), model.copy(playerSaves = listOf(checked))).value.orEmpty().startsWith("Checked "))
    }

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
        assertEquals(SaveStatus.Unconfirmed(UnconfirmedWhy.RommCopyUnconfirmed),
            deriveSaveStatus(facts.copy(offDevice = OffDeviceFact.Unknown)))
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
        val hero = HeroSubject.Item(HeroItem("game", "Game", "Nintendo 3DS"))
        val problem = SaveStatus.Problem(incident)
        val afterSync = heroFactText(hero, problem, TileActivity.Synced(SyncMark.Synced, 10_000)).text
        assertTrue(afterSync.startsWith(SaveProtectionCopy.title(incident)))
        assertFalse(afterSync.contains(Copy.savesSynced))
    }

    @Test fun syncCompletionCannotMakeAnEmptyOrUncheckedSlotLookSaved() {
        val hero = HeroSubject.Item(HeroItem("game", "Game", "PlayStation"))
        val synced = TileActivity.Synced(SyncMark.Synced, 10_000)
        assertTrue(heroFactText(hero, SaveStatus.NothingYet, synced).text.startsWith("No saves yet"))
        val unchecked = SaveStatus.Unconfirmed(UnconfirmedWhy.FolderNotChecked)
        assertTrue(heroFactText(hero, unchecked, synced).text.startsWith("Save folder not checked yet"))
        assertEquals(Copy.savesSynced, heroActivityLine(SaveStatus.Protected(now, now), synced))
    }

    @Test fun anUnknownLiveRootDescribesTheLastSessionWithoutClaimingAPermanentMove() {
        val bound = root.copy(identity = "primary:Azahar", label = "Azahar")
        val unknown = incident.copy(bound = bound, live = null)
        val opening = "Azahar didn’t use the folder Azahar last time you played."
        val action = "Fix it first, or this session’s progress isn’t protected."
        assertEquals("Azahar is saving somewhere else", SaveProtectionCopy.title(unknown))
        assertEquals("$opening $action", SaveProtectionCopy.body(unknown, stop = true))
        assertEquals("$opening ${SaveProtectionCopy.safeOnRomm(10, false)} $action", SaveProtectionCopy.body(unknown, 10, stop = true))
        assertEquals("$opening Your saves on RomM are safe. $action", SaveProtectionCopy.body(unknown, hasVersions = true, stop = true))
        assertEquals("$opening Fix it before you play.", SaveProtectionCopy.body(unknown))
        assertEquals("Azahar didn’t use this folder last time you played.", SaveProtectionCopy.unchangedLog("Azahar"))
        // An incident saved by the previous build gets the same Check/Fix detail without a migration.
        assertEquals("Azahar didn’t use this folder last time you played.",
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
        // The folder row names the folder and the Check row what was found, so "Wrong folder" isn't said twice.
        assertEquals(root.label, rowText(Row.PlayerSave("azahar"), broken).value)
        assertEquals("Saving in ${incident.live!!.label}", rowText(Row.SaveCheck("azahar"), broken).value)
        val fix = broken.copy(saveFix = SaveFixPage(incident.id, HostScreen.Bottom, null))
        assertEquals(Effect.PickFixFolder(incident.id), reduce(fix, Meaning.Activate).second)
        assertNull(reduce(fix, Meaning.Activate, repeatCount = 1).second)
        assertEquals(Effect.CancelSaveFix, reduce(fix, Meaning.Back).second)
        assertNull(reduce(fix, Meaning.Options).second)
        val maybe = incident.copy(kind = IncidentKind.MaybeWrongFolder)
        assertEquals(Effect.ConfirmSaveLocation(incident.id), reduce(fix.copy(saveIncidents = listOf(maybe)), Meaning.Options).second)
    }

    private val zone = java.time.ZoneId.of("UTC")
    private val now = java.time.ZonedDateTime.of(2026, 11, 24, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
    private fun at(day: Int, hour: Int, minute: Int, year: Int = 2026, month: Int = 11) =
        java.time.ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
    private val clock24 = DateStyle(is24Hour = true)

    @Test fun oneDateFormatForEverySaveSurface() {
        assertEquals("Today, 13:37", saveWhen(at(24, 13, 37), now, clock24, zone))
        assertEquals("Yesterday, 13:37", saveWhen(at(23, 13, 37), now, clock24, zone))
        assertEquals("Nov 22, 13:37", saveWhen(at(22, 13, 37), now, clock24, zone))
        assertEquals("Dec 30, 2025, 13:37", saveWhen(at(30, 13, 37, 2025, 12), now, clock24, zone))
        assertEquals("today, 13:37", saveWhen(at(24, 13, 37), now, clock24, zone, inSentence = true))
    }

    @Test fun receiptLinesReadPlainly() {
        assertEquals("Saved to RomM today", saveStatusLine(SaveStatus.Protected(now - 60_000, 20), now))
        assertEquals("Saved to RomM yesterday", saveStatusLine(SaveStatus.Protected(now - 86_400_000, 20), now))
        assertEquals("Saved to RomM 3 days ago", saveStatusLine(SaveStatus.Protected(now - 3 * 86_400_000L, 20), now))
        assertEquals("Can’t reach RomM · last saved today", saveStatusLine(SaveStatus.Unconfirmed(UnconfirmedWhy.RommUnreachable, now - 60_000), now))
        assertEquals("Can’t reach RomM", saveStatusLine(SaveStatus.Unconfirmed(UnconfirmedWhy.RommUnreachable), now))
    }

    @Test fun aKnownLiveFolderIsCalledAFolderAndOnlyTheStopNamesTheCost() {
        val known = incident.copy(bound = live, live = FolderRef("primary:AzaharQA", "AzaharQA"))
        val opening = "Azahar now saves in AzaharQA instead of the Azahar folder."
        assertEquals("$opening Fix it first, or this session’s progress isn’t protected.", SaveProtectionCopy.body(known, stop = true))
        assertEquals("$opening Fix it before you play.", SaveProtectionCopy.body(known))
        val stop = saveProtectionDialog(known, HostScreen.Bottom, at(24, 13, 37), now = now, style = clock24)
        assertTrue(stop.body, stop.body.contains("Your save on RomM is safe, from ${saveWhen(at(24, 13, 37), now, clock24, inSentence = true)}."))
        assertTrue(stop.body.endsWith("this session’s progress isn’t protected."))
    }

    @Test fun r1IncidentBodiesLeadWithTheProblemAndKeepTheActionShort() {
        val known = incident.copy(bound = live, live = FolderRef("primary:AzaharQA", "AzaharQA"))
        val cases = listOf(
            known to "Saving in AzaharQA. Fix it before you play.",
            known.copy(live = null) to "Not saving in Azahar. Fix it before you play.",
            known.copy(kind = IncidentKind.LostAccess, live = null) to "Can’t see Azahar. Fix it before you play.",
            known.copy(kind = IncidentKind.FolderEmptied, live = null) to "Azahar is empty. Fix it before you play.",
            known.copy(kind = IncidentKind.MaybeWrongFolder, live = null) to "May be saving elsewhere. Check before you play.",
            known.copy(kind = IncidentKind.MaybeNewSave) to "Two saves. Choose which to play.",
        )
        for ((problem, expected) in cases) {
            val body = SaveProtectionCopy.noticeBody(problem)
            assertEquals(expected, body)
            assertTrue(body, body.length <= 50)
            assertFalse(body.contains('\n'))
            assertFalse(body.contains("RomM")) // A folder-wide notice has no game receipt.
        }
    }

    @Test fun r1CardMismatchNamesTheCardAndKeepsTheActionVisible() {
        val card = "SLUS-00067_1.mcd"
        val mismatch = incident.copy(kind = IncidentKind.MaybeNewSave, live = FolderRef("card", card),
            evidence = Evidence(EvidenceKind.HostedOpen, "DuckStation opened $card."))
        assertEquals("Using SLUS-00067_1.mcd. Check before you play.", SaveProtectionCopy.noticeBody(mismatch))
        val long = SaveProtectionCopy.noticeBody(mismatch.copy(live = FolderRef("card", "Castlevania - Symphony of the Night (USA)_1.mcd")))
        assertTrue(long, long.length <= 50)
        assertTrue(long, long.startsWith("Using Castlevania"))
        assertTrue(long, long.endsWith("… Check before you play."))
    }

    @Test fun duckStationSilenceExplainsTheFolderAndCanBeDismissed() {
        val silence = incident.copy(kind = IncidentKind.MaybeWrongFolder, playerName = "DuckStation", live = null,
            evidence = Evidence(EvidenceKind.HostedOpen, SaveProtectionCopy.noHostedCard))
        assertFalse(silence.blocksLaunch)
        assertEquals("DuckStation may be saving somewhere else", SaveProtectionCopy.title(silence))
        assertTrue(SaveProtectionCopy.body(silence).contains("didn’t open a memory card from Foldcade’s folder"))
        assertTrue(SaveProtectionCopy.body(silence).contains("Folders › Memory Cards and choose Foldcade"))
        val page = SaveFixPage(silence.id, HostScreen.Bottom, null, playerManagesFolder = true)
        val model = PickerModel(1, 1, saveIncidents = listOf(silence))
        assertEquals(Effect.ConfirmSaveLocation(silence.id), applySaveFix(model, page, Meaning.Options, 0).second)
    }

    @Test fun configAccessIsShownSeparatelyFromHostedProof() {
        val setting = PlayerSaveSetting("duckstation", "DuckStation", false, hostedInUse = true, installed = true,
            location = verified, saveConfigReadable = false)
        val model = PickerModel(1, 1, playerSaves = listOf(setting))
        val row = Row.SaveConfig("duckstation")
        assertTrue(row in emulatorShelf(model).single().rows)
        assertEquals("Can’t read config", rowText(row, model).value)
        assertTrue(rowText(Row.SaveCheck("duckstation"), model).value.orEmpty().startsWith("Checked"))
        assertEquals("Readable", rowText(row, model.copy(playerSaves = listOf(setting.copy(saveConfigReadable = true)))).value)
    }

    @Test fun checkDetailSaysTheEvidenceInWords() {
        val checked = at(24, 13, 27)
        val whenText = saveWhen(checked, now, clock24, inSentence = true)
        assertEquals("Azahar used this folder $whenText.",
            SaveProtectionCopy.checkDetail(LocationFact.Verified(live, Evidence(EvidenceKind.FreshLog, "log/azahar_log.txt in Azahar"), checked), "Azahar", now, clock24))
        assertEquals("Azahar saved here $whenText.",
            SaveProtectionCopy.checkDetail(LocationFact.Verified(live, Evidence(EvidenceKind.SessionWrite, "Picross saved there"), checked), "Azahar", now, clock24))
        assertEquals("Eden’s own folder.",
            SaveProtectionCopy.checkDetail(LocationFact.Verified(live, Evidence(EvidenceKind.OwnProvider, ""), checked), "Eden", now, clock24))
        assertEquals("RetroArch used this folder $whenText.",
            SaveProtectionCopy.checkDetail(LocationFact.Verified(live, Evidence(EvidenceKind.Config, "com.retroarch holds saves"), checked), "RetroArch", now, clock24))
    }

    @Test fun settingsRowsDuringAnIncidentAndBeforeACheck() {
        val bound = FolderRef("primary:Azahar", "Azahar")
        val open = incident.copy(bound = bound, live = FolderRef("primary:AzaharQA", "AzaharQA"))
        val setting = PlayerSaveSetting("azahar", "Azahar", true, installed = true, location = LocationFact.Bound(bound), incident = open)
        val model = PickerModel(1, 1, playerSaves = listOf(setting), boardEmulators = setOf("azahar"))
        val folder = rowControl(Row.PlayerSave("azahar"), model) as RowControl.Value
        assertEquals("Azahar", folder.text)
        assertTrue(folder.attention)
        assertFalse(folder.healthy)
        val check = rowControl(Row.SaveCheck("azahar"), model) as RowControl.Value
        assertEquals("Saving in AzaharQA", check.text)
        assertTrue(check.attention)
        assertEquals("Not saving here", rowText(Row.SaveCheck("azahar"), model.copy(playerSaves = listOf(setting.copy(incident = open.copy(live = null))))).value)
        // Bound but unchecked: the folder's name, no check, and never "Folder set" on this page.
        val unchecked = model.copy(playerSaves = listOf(setting.copy(incident = null, location = LocationFact.Bound(FolderRef("primary:MD.emu", "MD.emu")))))
        val quiet = rowControl(Row.PlayerSave("azahar"), unchecked) as RowControl.Value
        assertEquals("MD.emu", quiet.text)
        assertFalse(quiet.healthy)
        assertFalse(quiet.attention)
    }

    @Test fun theCheckRowWaitsForABoundFolder() {
        fun rows(setting: PlayerSaveSetting) = emulatorShelf(PickerModel(1, 1, playerSaves = listOf(setting))).single().rows
        val unset = PlayerSaveSetting("armsx2", "ARMSX2", false, installed = true)
        assertFalse(rows(unset).contains(Row.SaveCheck("armsx2")))
        assertTrue(rows(unset.copy(chosen = true, location = LocationFact.Bound(root))).contains(Row.SaveCheck("armsx2")))
    }

    @Test fun r1SetupCountLeavesIncidentsToTheirOwnNotice() {
        val open = incident.copy(playerKey = "azahar")
        val broken = PlayerSaveSetting("azahar", "Azahar", true, installed = true, location = verified, incident = open)
        val unset = PlayerSaveSetting("melonds", "melonDS", false, installed = true)
        val model = PickerModel(1, 1, playerSaves = listOf(broken, unset), boardEmulators = setOf("azahar", "melonds"), saveIncidents = listOf(open))
        assertEquals(setOf("azahar", "melonds"), savesDue(model).map { it.key }.toSet())
        assertEquals(listOf("melonds"), savesDueNotice(model).map { it.key })
    }

    @Test fun theFixPageAsksForOneThingAtATime() {
        val known = incident.copy(bound = live, live = FolderRef("primary:AzaharQA", "AzaharQA"))
        val page = SaveFixPage(known.id, HostScreen.Bottom, SaveProtectionCopy.locationSteps("azahar"), playerId = "azahar")
        // A known folder: one button, straight to the picker there. No emulator steps, no Auto-find.
        assertEquals(listOf(SaveFixAction.ChooseFolder), saveFixActions(known, page))
        assertEquals(SaveFixCard("Choose AzaharQA, then tap Use this folder › Allow.", null), saveFixCard(known, page))
        // Unknown: the emulator's own steps, and Open Azahar first.
        val unknown = known.copy(live = null)
        assertEquals(listOf(SaveFixAction.OpenEmulator, SaveFixAction.ChooseFolder), saveFixActions(unknown, page))
        assertTrue(saveFixCard(unknown, page).body.contains("Select User Folder"))
        assertEquals(SaveProtectionCopy.thenChoose, saveFixCard(unknown, page).detail)
        val model = PickerModel(1, 1, saveIncidents = listOf(unknown), saveFix = page)
        assertEquals(Effect.OpenPlayer("azahar"), applySaveFix(model, page, Meaning.Activate, 0).second)
        val right = applySaveFix(model, page, Meaning.MoveRight, 0).first.saveFix!!
        assertEquals(Effect.PickFixFolder(unknown.id), applySaveFix(model, right, Meaning.Activate, 0).second)
        assertEquals(1, applySaveFix(model, right, Meaning.MoveRight, 0).first.saveFix!!.index)
        // Lost access already knows its folder.
        val lost = known.copy(kind = IncidentKind.LostAccess, live = null)
        assertEquals(live, saveFixTarget(lost))
        assertEquals(listOf(SaveFixAction.ChooseFolder), saveFixActions(lost, page))
    }

    @Test fun theFinishedFixPageOpensSavesOnlyWhenItCan() {
        val done = incident.copy(repair = RepairState.Done)
        val online = SaveFixPage(done.id, HostScreen.Bottom, null, game = "Picross 3D: Round 2", hasRommDestination = true)
        assertEquals(listOf(SaveFixAction.ChooseSave), saveFixActions(done, online))
        assertEquals("Choose save", saveFixLabel(SaveFixAction.ChooseSave, done))
        assertEquals(SaveProtectionCopy.guidedDone, saveFixCard(done, online).body)
        val model = PickerModel(1, 1, saveIncidents = listOf(done), saveFix = online)
        assertEquals(Effect.FinishSaveFix(done.id), applySaveFix(model, online, Meaning.Activate, 0).second)
        val offline = online.copy(offline = true)
        assertEquals(listOf(SaveFixAction.Close), saveFixActions(done, offline))
        assertEquals("Folder set. Join Wi-Fi, then choose your save in Y › Saves.", saveFixCard(done, offline).body)
        assertEquals(Effect.FinishSaveFix(done.id, openSaves = false), applySaveFix(model, offline, Meaning.Activate, 0).second)
        assertEquals(listOf(SaveFixAction.Close), saveFixActions(done, online.copy(game = null)))
        // The top screen says what Foldcade watches now, from the player's setting.
        val watching = model.copy(playerSaves = listOf(PlayerSaveSetting("azahar", "Azahar", true, location = LocationFact.Bound(FolderRef("primary:AzaharQA", "AzaharQA")))))
        assertEquals("AzaharQA", saveFixWatching(watching, online.copy(playerId = "azahar"), done))
        assertEquals("Foldcade now watches AzaharQA.", SaveProtectionCopy.nowWatches("AzaharQA"))
    }

    @Test fun theFixChecklistFollowsTheIncident() {
        val page = SaveFixPage(incident.id, HostScreen.Bottom, null, game = "Picross 3D: Round 2", hasRommDestination = true)
        val waiting = incident.copy(staged = listOf("file:///queued"))
        assertEquals(listOf(SaveFixStepState.Now, SaveFixStepState.Now, SaveFixStepState.Later), saveFixSteps(waiting, page).map { it.state })
        assertEquals("Waiting for RomM", saveFixSteps(waiting, page).first().value)
        val kept = incident.copy(kept = listOf("131", "133"), repair = RepairState.Done)
        assertEquals(listOf(SaveFixStepState.Done, SaveFixStepState.Done, SaveFixStepState.Now), saveFixSteps(kept, page).map { it.state })
        assertEquals("2 kept", saveFixSteps(kept, page).first().value)
        assertEquals("Use the folder Azahar uses", saveFixSteps(incident, page)[1].title)
        assertEquals("Choose Azahar’s folder", saveFixSteps(incident.copy(live = null), page)[1].title)
        assertEquals(SaveFixStep("Keep every copy on RomM", SaveFixStepState.Later, "Not kept yet"), saveFixSteps(incident, page).first())
        // RomM holding some copy of the game doesn't mean this incident's copies are kept.
        assertEquals(SaveFixStep("Keep every copy on RomM", SaveFixStepState.Later, "Not kept yet"),
            saveFixSteps(incident, page.copy(safe = SaveFixSafe("Picross 3D: Round 2", 100L))).first())
    }

    @Test fun localGuidedFixShowsStagedCopiesAndFinishesWithoutASaveChoice() {
        val localRetroArch = incident.copy(playerKey = "retroarch", playerName = "RetroArch",
            gameIds = listOf("lib:local-folder:nes"), staged = listOf("file:///save-handover/copy.save"), repair = RepairState.Guided)
        val page = SaveFixPage(localRetroArch.id, HostScreen.Bottom, null, game = "NES game")
        val done = localRetroArch.copy(repair = RepairState.Done)
        assertEquals(listOf(
            SaveFixStep("Keep a copy on this device", SaveFixStepState.Done, "1 kept"),
            SaveFixStep("Choose RetroArch’s folder", SaveFixStepState.Now),
        ), saveFixSteps(localRetroArch, page))
        assertEquals(listOf(SaveFixStepState.Done, SaveFixStepState.Done), saveFixSteps(done, page).map { it.state })
        for (offline in listOf(false, true)) {
            val current = page.copy(offline = offline)
            assertEquals("Folder set. A copy of your save is kept on this device.", saveFixCard(done, current).body)
            val model = PickerModel(1, 1, saveIncidents = listOf(done), saveFix = current)
            assertEquals(listOf(SaveFixAction.Close), saveFixActions(done, current))
            assertEquals(Effect.FinishSaveFix(done.id, openSaves = false), applySaveFix(model, current, Meaning.Activate, 0).second)
        }
    }

    @Test fun emulatorWideFixWithoutADestinationReportsMissingLocalCopies() {
        val page = SaveFixPage(incident.id, HostScreen.Bottom, null, game = "Local game")
        // An earlier repair can still be recorded after its RomM origin goes away.
        val waiting = incident.copy(repair = RepairState.WaitingForRomm())
        assertEquals(SaveFixStep("Keep a copy on this device", SaveFixStepState.Later, "Not kept yet"),
            saveFixSteps(waiting, page).first())
        assertEquals(SaveFixCard("Choose Live, then tap Use this folder › Allow.", null), saveFixCard(waiting.copy(live = FolderRef("primary:Live", "Live")), page))
        val done = incident.copy(repair = RepairState.Done)
        assertEquals("Folder set. No copy is kept on this device yet.", saveFixCard(done, page).body)
        assertEquals(listOf(SaveFixAction.Close), saveFixActions(done, page))
        // Copies kept on RomM before its destination went away still count as kept.
        val keptOnRomm = done.copy(kept = listOf("131"))
        assertEquals(SaveFixStep("Keep every copy on RomM", SaveFixStepState.Done, "1 kept"), saveFixSteps(keptOnRomm, page).first())
        assertEquals("Folder set. Your copies are kept on RomM.", saveFixCard(keptOnRomm, page).body)
    }

    @Test fun theFixChecklistShowsEdenSessionGuidanceForAnAccountChange() {
        val eden = SaveIncident("MaybeNewSave:eden", IncidentKind.MaybeNewSave, "eden", "Eden", listOf("game"),
            null, null, Evidence(EvidenceKind.Config, SaveProtectionCopy.edenUserChanged), 20, kept = listOf("backup"))
        val page = SaveFixPage(eden.id, HostScreen.Bottom, null, hasRommDestination = true)
        assertEquals(listOf(
            SaveFixStep(SaveProtectionCopy.stepKeep, SaveFixStepState.Done, SaveProtectionCopy.stepKept(1)),
            SaveFixStep("Check your Eden user", SaveFixStepState.Now),
            SaveFixStep(SaveProtectionCopy.stepChoose, SaveFixStepState.Later),
        ), saveFixSteps(eden, page))
        val reset = eden.copy(evidence = Evidence(EvidenceKind.SessionWrite, SaveProtectionCopy.mayBeNewGame))
        assertTrue(saveFixSteps(reset, page).isEmpty())
    }
}
