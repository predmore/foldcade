package app.foldcade.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.foldcade.FoldcadeApp
import app.foldcade.language.*

/** The guided PR 1 path: explain the evidence, choose the folder, then recover in Saves. */
@Composable
internal fun SaveFixScreen(app: FoldcadeApp, page: SaveFixPage, controls: Boolean, dispatch: (Effect?) -> Unit) {
    val theme = LocalFoldTheme.current.theme
    val incident = app.shell.model.saveIncidents.firstOrNull { it.id == page.incidentId } ?: return
    val done = incident.repair == RepairState.Done
    val bodyStyle = TextStyle(color = theme.onBackground, fontFamily = theme.font, fontSize = theme.style.type.dialogBody)
    Column(Modifier.fillMaxSize().background(theme.background).padding(48.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        BasicText(SaveProtectionCopy.fixTitle(incident.playerName), style = bodyStyle.copy(fontSize = theme.style.type.heroTitle, fontWeight = theme.style.type.strongWeight))
        if (!controls) {
            BasicText(SaveProtectionCopy.title(incident), style = bodyStyle.copy(color = theme.style.colors.attention))
            incident.live?.let { BasicText(SaveProtectionCopy.liveFolder(incident.playerName, it.label), style = bodyStyle) }
            incident.bound?.let { BasicText(SaveProtectionCopy.watchedFolder(it.label), style = bodyStyle) }
            BasicText(incident.evidence.detail, style = bodyStyle.copy(color = theme.muted))
            if (incident.staged.isNotEmpty()) BasicText(SaveProtectionCopy.keptHere, style = bodyStyle)
            return@Column
        }
        if (done) {
            BasicText(SaveProtectionCopy.guidedDone, style = bodyStyle)
        } else {
            BasicText(SaveProtectionCopy.bindingTitle(incident.playerName, incident.live != null), style = bodyStyle)
            BasicText(page.steps ?: SaveSetupCopy.useThisFolder, style = bodyStyle)
            BasicText(SaveProtectionCopy.thenChoose, style = bodyStyle)
            incident.live?.let { BasicText(it.label, style = bodyStyle.copy(color = theme.muted)) }
        }
        page.note?.let { BasicText(it, style = bodyStyle.copy(color = theme.style.colors.attention)) }
        Spacer(Modifier.weight(1f))
        val buttons = if (done) listOf(SaveSetupCopy.done) else listOf(SaveProtectionCopy.chooseFolder, SaveSetupCopy.open(incident.playerName), SaveProtectionCopy.autoFind)
        buttons.forEachIndexed { index, label ->
            BasicText(label, style = bodyStyle.copy(color = if (index == page.index) theme.focus else theme.onBackground),
                modifier = Modifier.fillMaxWidth().hostPress { dispatch(app.shell.touchSaveFix(index)) }.padding(vertical = 10.dp))
        }
        if (incident.kind in setOf(IncidentKind.MaybeWrongFolder, IncidentKind.MaybeNewSave)) {
            Row(Modifier.hostPress { dispatch(app.shell.onMeaning(Meaning.Options, page.screen)) }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PromptImage(promptGlyph(actionFaces(app.shell.model.faceMap).optionsGlyph), 28.dp)
                BasicText(SaveProtectionCopy.itsFine, style = bodyStyle.copy(color = theme.muted))
            }
        }
        Row(Modifier.hostPress { dispatch(app.shell.onMeaning(Meaning.Back, page.screen)) }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PromptImage(promptGlyph(promptKeyOf(app.shell.model.faceMap.backKey) ?: PromptKey.FaceB), 28.dp)
            BasicText(SaveProtectionCopy.later, style = bodyStyle.copy(color = theme.muted))
        }
    }
}
