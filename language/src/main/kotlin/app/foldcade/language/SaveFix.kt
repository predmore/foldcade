package app.foldcade.language

/**
 * The incident owns repair progress. Navigation itself is transient.
 * [game] is the game a held launch or an open Saves page is for, so the finished page can open
 * its Saves when [hasRommDestination] is true. [safe] is that game's RomM copy once the host
 * has read it. [offline] is the network as the host last saw it.
 */
data class SaveFixPage(
    val incidentId: String,
    val screen: HostScreen,
    val steps: String?,
    val index: Int = 0,
    val note: String? = null,
    val playerId: String? = null,
    val resolved: SaveIncident? = null,
    val game: String? = null,
    val safe: SaveFixSafe? = null,
    val offline: Boolean = false,
    val mustQuitInEmulator: Boolean = false,
    val hasRommDestination: Boolean = false,
    val playerManagesFolder: Boolean = false,
    val stagedDestinations: Map<String, SaveFixCopyDestination>? = null,
)

/** A destination read from the staged file's sidecar. A missing entry stays unchecked. */
enum class SaveFixCopyDestination { Romm, Device }

/** A RomM copy of [game] the host has seen: [atMillis] its receipt, or null when only versions are known. */
data class SaveFixSafe(val game: String, val atMillis: Long?)

/** What each of the Fix page's buttons does, in the order they sit. */
enum class SaveFixAction { ChooseFolder, OpenEmulator, ConfirmClosed, ChooseSave, ChooseProgress, OpenSaves, Retry, Close }

fun saveFixHeading(incident: SaveIncident): String = if (incident.kind == IncidentKind.MaybeNewSave && !incident.hostedCardMismatch) SaveProtectionCopy.twoSaves
    else SaveProtectionCopy.fixTitle(incident.playerName)

/**
 * The folder Foldcade knows the fix must end on: where the emulator saves now, or, when only
 * access was lost, the folder it already watched. Null when only the emulator can say.
 */
fun saveFixTarget(incident: SaveIncident): FolderRef? =
    if (incident.playerKey == "retroarch" && incident.gameIds.isNotEmpty() || incident.armsX2OutsideCard) null
    else incident.live ?: incident.bound.takeIf { incident.kind == IncidentKind.LostAccess }

/**
 * A known folder needs one button, which opens the picker there: sending the player into the
 * emulator would only have them change its folder again. Unknown, the emulator comes first.
 */
fun saveFixActions(incident: SaveIncident, page: SaveFixPage): List<SaveFixAction> {
    val repair = saveFixRepair(incident, page)
    val finish = if (page.hasRommDestination && page.game != null && !page.offline) SaveFixAction.ChooseSave else SaveFixAction.Close
    return when {
        repair == RepairState.Done -> listOf(finish)
        incident.isEdenSessionChange -> listOf(SaveFixAction.OpenEmulator, finish)
        page.hasRommDestination && repair == RepairState.Guided && incident.kind == IncidentKind.MaybeNewSave && incident.subject != null ->
            listOf(SaveFixAction.OpenSaves)
        repair is RepairState.Choosing -> listOf(SaveFixAction.ChooseProgress)
        repair is RepairState.WaitingForRomm -> listOf(SaveFixAction.Retry)
        repair is RepairState.WaitingForClose -> if (page.mustQuitInEmulator) listOf(SaveFixAction.OpenEmulator)
            else listOf(SaveFixAction.ConfirmClosed, SaveFixAction.OpenEmulator)
        repair in setOf(RepairState.Keeping, RepairState.Rebinding) || repair is RepairState.Placing -> emptyList()
        page.playerManagesFolder -> listOf(SaveFixAction.OpenEmulator)
        saveFixTarget(incident) != null -> listOf(SaveFixAction.ChooseFolder)
        else -> listOf(SaveFixAction.OpenEmulator, SaveFixAction.ChooseFolder)
    }
}

/** An earlier automatic repair can still be recorded after the RomM destination is gone. */
private fun saveFixRepair(incident: SaveIncident, page: SaveFixPage): RepairState =
    if (page.hasRommDestination || incident.repair == RepairState.Done) incident.repair else RepairState.Guided

fun saveFixLabel(action: SaveFixAction, incident: SaveIncident): String = when (action) {
    SaveFixAction.ChooseFolder -> SaveProtectionCopy.chooseFolder
    SaveFixAction.OpenEmulator -> SaveSetupCopy.open(incident.playerName)
    SaveFixAction.ConfirmClosed -> SaveProtectionCopy.confirmClose
    SaveFixAction.ChooseSave -> SaveProtectionCopy.chooseSave
    SaveFixAction.ChooseProgress -> SaveProtectionCopy.chooseSave
    SaveFixAction.OpenSaves -> SaveProtectionCopy.openSaves
    SaveFixAction.Retry -> SaveProtectionCopy.tryAgain
    SaveFixAction.Close -> SaveSetupCopy.done
}

/** The bottom card's words: the one thing to do now. [SaveFixCard.detail] follows any steps. */
data class SaveFixCard(val body: String, val detail: String?)

fun saveFixCard(incident: SaveIncident, page: SaveFixPage): SaveFixCard {
    val repair = saveFixRepair(incident, page)
    val automatic = when (repair) {
        RepairState.Keeping -> SaveProtectionCopy.keepingCopies
        is RepairState.WaitingForRomm -> SaveProtectionCopy.waitForRomm
        is RepairState.WaitingForClose -> if (page.mustQuitInEmulator) SaveProtectionCopy.quitInEmulator(page.game, incident.playerName)
            else SaveProtectionCopy.waitForClose(page.game, incident.playerName)
        RepairState.Rebinding -> SaveProtectionCopy.usingFolder(saveFixTarget(incident)?.label.orEmpty())
        is RepairState.Choosing -> SaveProtectionCopy.bothKept
        is RepairState.Placing -> SaveProtectionCopy.placingSave
        RepairState.Done -> saveFixTarget(incident)?.let { SaveProtectionCopy.repairDone(it.label) }
            .takeIf { incident.automaticDone && page.hasRommDestination }
        else -> null
    }
    if (automatic != null) return SaveFixCard(automatic, page.note)
    if (page.hasRommDestination && repair == RepairState.Guided && incident.kind == IncidentKind.MaybeNewSave && incident.subject != null)
        return SaveFixCard(SaveProtectionCopy.chooseInSaves, page.note)
    if (repair == RepairState.Done) {
        val destinations = page.stagedDestinations
        return SaveFixCard(when {
            destinations == null && (!page.hasRommDestination || page.game == null) -> "${SaveProtectionCopy.folderSet}."
            (!page.hasRommDestination || page.game == null) &&
                incident.staged.any { destinations?.get(it) == SaveFixCopyDestination.Device } -> SaveProtectionCopy.guidedDoneLocal
            !page.hasRommDestination && incident.staged.any { destinations?.get(it) == SaveFixCopyDestination.Romm } -> SaveProtectionCopy.guidedDoneRommWaiting
            !page.hasRommDestination && incident.staged.isNotEmpty() -> SaveProtectionCopy.guidedDoneCopiesUnchecked
            !page.hasRommDestination && incident.kept.isNotEmpty() -> SaveProtectionCopy.guidedDoneKeptOnRomm
            !page.hasRommDestination -> SaveProtectionCopy.guidedDoneWithoutLocalCopy
            page.offline -> SaveProtectionCopy.guidedDoneOffline
            else -> SaveProtectionCopy.guidedDone
        }, page.note)
    }
    if (incident.isEdenSessionChange) return SaveFixCard(
        if (page.hasRommDestination) "Check the user in Eden’s Profile Manager, then choose a save in Y › Saves."
        else "Check the user in Eden’s Profile Manager.", page.note)
    if (page.playerManagesFolder && page.steps != null) return SaveFixCard(page.steps, page.note)
    saveFixTarget(incident)?.let { return SaveFixCard(SaveProtectionCopy.chooseThere(it.label), page.note) }
    val steps = page.steps ?: return SaveFixCard(SaveProtectionCopy.findFolder(incident.playerName), page.note)
    return SaveFixCard(steps, listOfNotNull(SaveProtectionCopy.thenChoose, page.note).joinToString(" "))
}

/** One line of the top screen's checklist. [value] is what is known of it, as "2 kept". */
data class SaveFixStep(val title: String, val state: SaveFixStepState, val value: String? = null)

enum class SaveFixStepState { Done, Now, Later }

fun saveFixSteps(incident: SaveIncident, page: SaveFixPage): List<SaveFixStep> {
    if (incident.kind == IncidentKind.MaybeNewSave && !incident.isEdenSessionChange && !incident.hostedCardMismatch) return emptyList()
    val destinations = page.stagedDestinations
    val deviceCopies = incident.staged.count { destinations?.get(it) == SaveFixCopyDestination.Device }
    val rommCopies = incident.staged.count { destinations?.get(it) == SaveFixCopyDestination.Romm }
    val deviceStep = if (deviceCopies == 0) null else
        SaveFixStep(SaveProtectionCopy.stepKeepLocal, SaveFixStepState.Done, SaveProtectionCopy.stepKept(deviceCopies))
    val uncheckedStep = SaveFixStep(SaveProtectionCopy.stepCopiesUnchecked, SaveFixStepState.Later)
        .takeIf { destinations != null && deviceCopies + rommCopies < incident.staged.size }
    val otherCopies = listOfNotNull(deviceStep, uncheckedStep)
    val rommTitle = if (otherCopies.isEmpty()) SaveProtectionCopy.stepKeep else SaveProtectionCopy.stepKeepRomm

    if (page.hasRommDestination && (incident.automaticDone || incident.repair in setOf(RepairState.Keeping, RepairState.Rebinding) ||
        incident.repair is RepairState.WaitingForRomm || incident.repair is RepairState.WaitingForClose ||
        incident.repair is RepairState.Choosing || incident.repair is RepairState.Placing)) {
        val closing = incident.repair as? RepairState.WaitingForClose
        val step = when (incident.repair) {
            RepairState.Keeping, is RepairState.WaitingForRomm -> 0
            is RepairState.WaitingForClose, RepairState.Rebinding -> 1
            is RepairState.Choosing, is RepairState.Placing -> 2
            else -> 3
        }
        val steps = listOf(rommTitle, SaveProtectionCopy.stepFolder(incident.playerName, true), SaveProtectionCopy.stepRestore)
            .mapIndexed { i, label -> SaveFixStep(label, when {
                i == 0 && rommCopies > 0 -> SaveFixStepState.Now
                i == 0 && closing?.backupsKept == false -> SaveFixStepState.Later
                i < step -> SaveFixStepState.Done
                i == step -> SaveFixStepState.Now
                else -> SaveFixStepState.Later
            }, when {
                i != 0 -> null
                rommCopies > 0 || incident.repair is RepairState.WaitingForRomm -> SaveProtectionCopy.stepWaiting
                step > 0 && incident.kept.isNotEmpty() -> SaveProtectionCopy.stepKept(incident.kept.size)
                else -> null
            }) }
        return if (destinations == null) steps.drop(1) else steps.take(1) + otherCopies + steps.drop(1)
    }
    val bound = incident.repair == RepairState.Done
    val keep = when {
        destinations == null -> null
        rommCopies > 0 -> SaveFixStep(rommTitle, SaveFixStepState.Now, SaveProtectionCopy.stepWaiting)
        incident.kept.isNotEmpty() -> SaveFixStep(rommTitle, SaveFixStepState.Done, SaveProtectionCopy.stepKept(incident.kept.size))
        otherCopies.isNotEmpty() -> null
        !page.hasRommDestination -> SaveFixStep(SaveProtectionCopy.stepKeepLocal, SaveFixStepState.Later, SaveProtectionCopy.stepNotKeptYet)
        incident.isEdenGrant -> SaveFixStep(rommTitle, SaveFixStepState.Later)
        else -> SaveFixStep(rommTitle, SaveFixStepState.Later, SaveProtectionCopy.stepNotKeptYet)
    }
    val copySteps = listOfNotNull(keep) + otherCopies
    val choose = SaveFixStep(SaveProtectionCopy.stepChoose, if (bound) SaveFixStepState.Now else SaveFixStepState.Later)
        .takeIf { page.hasRommDestination }
    if (page.playerManagesFolder) return copySteps + listOfNotNull(choose.takeIf { incident.hostedCardMismatch })
    if (incident.isEdenSessionChange) return copySteps + listOfNotNull(
        SaveFixStep("Check your Eden user", SaveFixStepState.Now),
        choose,
    )
    return copySteps + listOfNotNull(
        SaveFixStep(SaveProtectionCopy.stepFolder(incident.playerName, saveFixTarget(incident) != null), if (bound) SaveFixStepState.Done else SaveFixStepState.Now),
        choose,
    )
}

/** The folder Foldcade watches once the fix is done, as the player's setting now reads it. */
fun saveFixWatching(model: PickerModel, page: SaveFixPage, incident: SaveIncident): String? =
    when (val location = model.playerSaves.firstOrNull { it.playerId == page.playerId }?.location) {
        is LocationFact.Bound -> location.root.label
        is LocationFact.Verified -> location.root.label
        else -> null
    } ?: saveFixTarget(incident)?.label

/** Settings › Emulators' Save folder row during an incident: the folder it watches, so the Check row can say what is wrong. */
fun saveFolderState(incident: SaveIncident): String = incident.bound?.label ?: when (incident.kind) {
    IncidentKind.WrongFolder, IncidentKind.MaybeWrongFolder -> "Wrong folder"
    IncidentKind.FolderEmptied -> "Folder empty"
    IncidentKind.LostAccess -> "No access"
    IncidentKind.MaybeNewSave -> if (incident.isEdenSessionChange) "Check Eden" else "Two saves"
}

/** Settings › Emulators' Check row during an incident: what the evidence says, once. */
fun saveCheckState(incident: SaveIncident): String = when (incident.kind) {
    IncidentKind.WrongFolder -> if (incident.saveLayoutChanged) "Save layout changed"
        else incident.live?.let { if (incident.isEdenGrant) "Allow ${it.label}" else "Saving in ${it.label}" } ?: "Not saving here"
    IncidentKind.MaybeWrongFolder -> "May save elsewhere"
    IncidentKind.FolderEmptied -> "Folder empty"
    IncidentKind.LostAccess -> "No access"
    IncidentKind.MaybeNewSave -> when {
        incident.isEdenSessionChange -> "Check Eden"
        incident.hostedCardMismatch -> "Different card"
        else -> "Two saves"
    }
}

fun applySaveFix(model: PickerModel, page: SaveFixPage, meaning: Meaning, repeatCount: Int): Pair<PickerModel, Effect?> {
    if (repeatCount > 0 && meaning !in setOf(Meaning.MoveUp, Meaning.MoveDown, Meaning.MoveLeft, Meaning.MoveRight)) return model to null
    val incident = model.saveIncidents.firstOrNull { it.id == page.incidentId } ?: page.resolved ?: return model.copy(saveFix = null) to null
    val actions = saveFixActions(incident, page)
    val last = actions.lastIndex.coerceAtLeast(0)
    return when (meaning) {
        Meaning.Options -> model to Effect.ConfirmSaveLocation(incident.id).takeIf { canConfirmSaveLocation(incident) }
        Meaning.Back -> model.copy(saveFix = null) to Effect.CancelSaveFix
        Meaning.MoveUp, Meaning.MoveLeft -> model.copy(saveFix = page.copy(index = (page.index - 1).coerceAtLeast(0))) to null
        Meaning.MoveDown, Meaning.MoveRight -> model.copy(saveFix = page.copy(index = (page.index + 1).coerceAtMost(last))) to null
        Meaning.Activate -> model to when (actions.getOrNull(page.index.coerceIn(0, last))) {
            SaveFixAction.ChooseFolder -> Effect.PickFixFolder(incident.id)
            SaveFixAction.OpenEmulator -> Effect.OpenPlayer(page.playerId ?: incident.playerKey)
            SaveFixAction.ConfirmClosed -> return model.copy(dialog = saveFixCloseDialog(page, incident)) to null
            SaveFixAction.ChooseSave -> Effect.FinishSaveFix(incident.id)
            SaveFixAction.ChooseProgress -> Effect.ChooseRepairSave(incident.id)
            SaveFixAction.OpenSaves -> return model.copy(saveFix = null) to incident.subject?.let { Effect.OpenSaves(homeGameId(it.libraryId, it.gameId)) }
            SaveFixAction.Retry -> Effect.RetrySaveRepair(incident.id)
            SaveFixAction.Close -> Effect.FinishSaveFix(incident.id, openSaves = false)
            null -> null
        }
        else -> model to null
    }
}

private fun saveFixCloseDialog(page: SaveFixPage, incident: SaveIncident): DialogState = closeGameDialog(page.screen).copy(
    title = SaveProtectionCopy.alreadyQuit,
    body = SaveProtectionCopy.confirmCloseBody(page.game ?: incident.subject?.label, incident.playerName),
    buttons = listOf(DialogButton.ContinueFix, DialogButton.NotNow),
    subject = incident.id,
)

fun canConfirmSaveLocation(incident: SaveIncident): Boolean =
    incident.kind == IncidentKind.MaybeWrongFolder && incident.repair != RepairState.Done

fun saveProtectionDialog(
    incident: SaveIncident,
    screen: HostScreen,
    receipt: Long? = null,
    hasVersions: Boolean = false,
    now: Long = System.currentTimeMillis(),
    style: DateStyle = DateStyle(),
): DialogState = DialogState(
    kind = DialogKind.SaveProtection,
    title = SaveProtectionCopy.title(incident), body = SaveProtectionCopy.body(incident, receipt, hasVersions, stop = true, now = now, style = style),
    buttons = listOf(when (incident.kind) {
        IncidentKind.FolderEmptied -> DialogButton.PutSaveBack
        IncidentKind.LostAccess -> DialogButton.Allow
        else -> DialogButton.FixSaves
    }, DialogButton.PlayWithoutSyncing), index = 0, safeIndex = BACK_CLOSES,
    screen = screen, subject = incident.id, holdsLaunch = true,
)

fun saveFolderFineDialog(incident: SaveIncident, screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.SaveFolderFine, title = "Keep watching ${incident.bound?.label}?",
    body = "Only if ${incident.playerName} saves there. Foldcade checks again next time.",
    buttons = listOf(DialogButton.KeepWatchingSaveFolder, DialogButton.NotNow), index = 1, safeIndex = 1,
    screen = screen, subject = incident.id,
)
