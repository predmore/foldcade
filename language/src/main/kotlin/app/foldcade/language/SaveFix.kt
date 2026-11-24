package app.foldcade.language

/** Guided repair only. Binding is persisted with the incident; navigation itself is transient. */
data class SaveFixPage(val incidentId: String, val screen: HostScreen, val steps: String?, val index: Int = 0, val note: String? = null, val playerId: String? = null, val resolved: SaveIncident? = null)

fun saveFolderState(incident: SaveIncident): String = when (incident.kind) {
    IncidentKind.WrongFolder, IncidentKind.MaybeWrongFolder -> "Wrong folder"
    IncidentKind.FolderEmptied -> "Folder empty"
    IncidentKind.LostAccess -> "No access"
    IncidentKind.MaybeNewSave -> "Two saves"
}

fun applySaveFix(model: PickerModel, page: SaveFixPage, meaning: Meaning, repeatCount: Int): Pair<PickerModel, Effect?> {
    if (repeatCount > 0 && meaning !in setOf(Meaning.MoveUp, Meaning.MoveDown, Meaning.MoveLeft, Meaning.MoveRight)) return model to null
    val incident = model.saveIncidents.firstOrNull { it.id == page.incidentId } ?: page.resolved ?: return model.copy(saveFix = null) to null
    val done = incident.repair == RepairState.Done
    val last = if (done) 0 else 2
    return when (meaning) {
        Meaning.Options -> model to Effect.ConfirmSaveLocation(incident.id).takeIf { incident.kind in setOf(IncidentKind.MaybeWrongFolder, IncidentKind.MaybeNewSave) }
        Meaning.Back -> model.copy(saveFix = null) to Effect.CancelSaveFix
        Meaning.MoveUp, Meaning.MoveLeft -> model.copy(saveFix = page.copy(index = (page.index - 1).coerceAtLeast(0))) to null
        Meaning.MoveDown, Meaning.MoveRight -> model.copy(saveFix = page.copy(index = (page.index + 1).coerceAtMost(last))) to null
        Meaning.Activate -> model to if (done) Effect.FinishSaveFix(incident.id) else when (page.index) {
            0 -> Effect.PickFixFolder(incident.id)
            1 -> Effect.OpenPlayer(page.playerId ?: incident.playerKey)
            else -> Effect.OpenAllFilesAccess
        }
        else -> model to null
    }
}

fun saveProtectionDialog(incident: SaveIncident, screen: HostScreen, receipt: Long? = null, hasVersions: Boolean = false): DialogState = DialogState(
    kind = DialogKind.SaveProtection,
    title = SaveProtectionCopy.title(incident), body = SaveProtectionCopy.body(incident, receipt, hasVersions),
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
