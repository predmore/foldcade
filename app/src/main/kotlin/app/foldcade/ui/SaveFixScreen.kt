package app.foldcade.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.foldcade.FoldcadeApp
import app.foldcade.R
import app.foldcade.language.*

/**
 * The top screen shows what happened and where the fix stands. The bottom screen shows the
 * current step and its actions.
 */
@Composable
internal fun SaveFixScreen(app: FoldcadeApp, page: SaveFixPage, controls: Boolean, dispatch: (Effect?) -> Unit) {
    val theme = LocalFoldTheme.current.theme
    val model = app.shell.model
    val incident = model.saveIncidents.firstOrNull { it.id == page.incidentId } ?: page.resolved ?: return
    if (!controls) {
        SaveFixTop(model, page, incident, rememberDateStyle(app))
        return
    }
    val actions = saveFixActions(incident, page)
    val card = saveFixCard(incident, page)
    val faces = actionFaces(model.faceMap)
    val back = promptKeyOf(model.faceMap.backKey) ?: PromptKey.FaceB
    val hints = listOfNotNull(
        HintKey(faces.optionsGlyph, SaveProtectionCopy.itsFine).takeIf { canConfirmSaveLocation(incident) },
        HintKey(back, SaveProtectionCopy.later),
    )
    Box(Modifier.fillMaxSize().background(theme.background)) {
        DialogFrame(
            app = app,
            title = if (incident.isEdenSessionChange) SaveProtectionCopy.title(incident) else saveFixHeading(incident),
            body = card.body,
            detail = card.detail,
            buttons = actions.map { saveFixLabel(it, incident) },
            focused = page.index.coerceIn(0, actions.lastIndex.coerceAtLeast(0)),
            hints = hints,
            onButton = { index -> dispatch(app.shell.touchSaveFix(index)) },
        )
    }
}

/** What happened, whether RomM holds the save, the steps, and the two folders side by side. No file paths. */
@Composable
private fun SaveFixTop(model: PickerModel, page: SaveFixPage, incident: SaveIncident, dates: DateStyle) {
    val theme = LocalFoldTheme.current.theme
    val design = theme.style
    val done = incident.repair == RepairState.Done
    val body = TextStyle(color = theme.onBackground, fontFamily = theme.font, fontSize = design.type.dialogBody)
    val muted = body.copy(color = theme.muted)
    Column(Modifier.fillMaxSize().background(theme.background).padding(48.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        BasicText(if (incident.isEdenSessionChange) "Check Eden’s user" else saveFixHeading(incident),
            style = body.copy(fontFamily = theme.displayFont, fontSize = design.type.heroTitle, fontWeight = design.type.strongWeight))
        if (done) {
            saveFixWatching(model, page, incident)?.let { BasicText(SaveProtectionCopy.nowWatches(it), style = body) }
        } else {
            BasicText(SaveProtectionCopy.title(incident), style = body.copy(color = design.colors.attention))
        }
        page.safe?.takeIf { page.hasRommDestination }?.let { safe ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(if (safe.atMillis != null) SaveProtectionCopy.safeHeadline else SaveProtectionCopy.safeHeadlineVersions, style = body)
                BasicText(listOfNotNull(safe.game, safe.atMillis?.let { saveWhen(it, System.currentTimeMillis(), dates) }).joinToString(" · "),
                    style = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val steps = saveFixSteps(incident, page)
        if (steps.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                steps.forEach { step -> FixStep(step) }
            }
        }
        if (!done && !incident.isEdenSessionChange && (steps.isNotEmpty() || page.playerManagesFolder)) FolderRows(incident)
        page.note?.let { BasicText(it, style = body.copy(color = design.colors.attention)) }
    }
}

/** One step: a check once done, a mark on the one to do now, a ring for later, then its value. */
@Composable
private fun FixStep(step: SaveFixStep) {
    val theme = LocalFoldTheme.current.theme
    val design = theme.style
    val style = TextStyle(fontFamily = theme.font, fontSize = design.type.dialogBody)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            when (step.state) {
                SaveFixStepState.Done -> {
                    val check = themeIcon("badge.check", R.drawable.ic_badge_check)
                    Image(check.painter, contentDescription = null, colorFilter = check.tint(theme.focus), modifier = Modifier.size(24.dp))
                }
                SaveFixStepState.Now -> BasicText("▸", style = style.copy(color = theme.focus, fontWeight = design.type.strongWeight))
                SaveFixStepState.Later -> BasicText("○", style = style.copy(color = theme.muted))
            }
        }
        BasicText(step.title, style = style.copy(
            color = if (step.state == SaveFixStepState.Later) theme.muted else theme.onBackground,
            fontWeight = if (step.state == SaveFixStepState.Now) design.type.strongWeight else design.type.bodyWeight,
        ))
        step.value?.let { BasicText(it, style = style.copy(color = theme.muted)) }
    }
}

/**
 * Where the emulator saves now and where Foldcade was watching, as two real columns: the labels
 * in one, the folders lined up in the other. Without the live folder, what the check found instead.
 */
@Composable
private fun FolderRows(incident: SaveIncident) {
    val theme = LocalFoldTheme.current.theme
    val design = theme.style
    val label = TextStyle(color = theme.muted, fontFamily = theme.font, fontSize = design.type.dialogBody)
    val value = label.copy(color = theme.onBackground, fontWeight = design.type.strongWeight)
    if (incident.saveLayoutChanged) {
        BasicText(SaveProtectionCopy.title(incident), style = label)
        return
    }
    val rows = listOfNotNull(
        incident.live?.let { (when {
            incident.hostedCardMismatch -> "Card opened"
            incident.isEdenGrant -> "Allow this folder"
            else -> SaveProtectionCopy.nowSavesIn(incident.playerName)
        }) to it.label },
        incident.bound?.let { (if (incident.hostedCardMismatch) "Card Foldcade expected" else SaveProtectionCopy.wasWatching) to it.label },
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (rows.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { rows.forEach { BasicText(it.first, style = label, maxLines = 1) } }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { rows.forEach { BasicText(it.second, style = value, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        }
        // A known live folder says it all; otherwise the evidence, in words and without paths.
        if (incident.live == null) BasicText(SaveProtectionCopy.incidentDetail(incident), style = label)
    }
}
