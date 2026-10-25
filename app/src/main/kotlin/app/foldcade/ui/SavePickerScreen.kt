package app.foldcade.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.foldcade.CoverImage
import app.foldcade.FoldcadeApp
import app.foldcade.language.*
import coil3.compose.rememberAsyncImagePainter

@Composable
internal fun SavePickerTop(app: FoldcadeApp, state: SavePickerState, dates: DateStyle, scale: Float) {
    val theme = LocalFoldTheme.current.theme
    val fit = theme.style.saves
    val focused = state.focused
    Column(Modifier.fillMaxSize().background(theme.background).padding(fit.edgeDp.dp), verticalArrangement = Arrangement.spacedBy(fit.gapDp.dp)) {
        SaveText(state.gameLabel, title = true)
        Crossfade(focused?.id, animationSpec = theme.motion.arrive(theme.motion.durationTravel, scale), modifier = Modifier.weight(1f)) { id ->
            val save = state.saves.firstOrNull { it.id == id } ?: state.copying?.takeIf { it.id == id }
            if (save == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SaveText(SavePickerCopy.explanation(state.health))
                }
            } else Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(fit.edgeDp.dp)) {
                save.screenshotUri?.let { uri ->
                    val painter = rememberAsyncImagePainter(CoverImage(uri), app.images)
                    Image(painter, null, Modifier.fillMaxWidth(fit.screenshotShare).fillMaxHeight().clip(RoundedCornerShape(theme.style.shape.thumbCornerDp.dp)), contentScale = ContentScale.Fit)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(fit.gapDp.dp)) {
                    SaveDetail(SavePickerCopy.whenLabel, formatSaveWhen(save.updatedAtMillis, dates))
                    SaveDetail(SavePickerCopy.fromLabel, listOfNotNull(SavePickerCopy.source(save), save.emulator?.takeUnless { it == save.source }).joinToString(" · "))
                    SaveDetail(SavePickerCopy.slotLabel, SavePickerCopy.slot(save))
                    SaveDetail(SavePickerCopy.sizeLabel, formatSaveSize(save.sizeBytes))
                    if (save.playing) PlayingPill()
                }
            }
        }
    }
}

@Composable
internal fun SavePickerBottom(app: FoldcadeApp, state: SavePickerState, dates: DateStyle, scale: Float, dispatch: (Effect?) -> Unit) {
    val theme = LocalFoldTheme.current.theme
    val fit = theme.style.saves
    val slots = rememberSelectionSlots()
    val model = app.shell.model
    val back = promptKeyOf(model.faceMap.backKey) ?: PromptKey.FaceB
    val confirm = promptKeyOf(model.faceMap.confirmKey) ?: PromptKey.FaceA
    Box(Modifier.fillMaxSize().background(theme.background)) {
        Column(Modifier.fillMaxSize().padding(fit.edgeDp.dp), verticalArrangement = Arrangement.spacedBy(fit.gapDp.dp)) {
            SaveText(if (state.copying == null) SavePickerCopy.title else SavePickerCopy.copy, title = true)
            when {
                state.loading || state.busy -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val transition = rememberInfiniteTransition()
                    val pulse by transition.animateFloat(theme.style.menu.labelRestAlpha, 1f, theme.motion.pulse(scale))
                    BasicText(if (state.busy) SavePickerCopy.working else SavePickerCopy.loading,
                        style = TextStyle(color = theme.onBackground.copy(alpha = pulse), fontSize = theme.style.type.menuRow, fontFamily = theme.font))
                }
                state.error -> Box(Modifier.weight(1f).fillMaxWidth().hostPress { dispatch(app.shell.onMeaning(Meaning.Activate, HostScreen.Bottom)) }, contentAlignment = Alignment.Center) {
                    SaveText(SavePickerCopy.error)
                }
                else -> {
                    val scroll = rememberScrollState()
                    val focus = if (state.copying == null) state.index else state.destinationIndex
                    Column(Modifier.weight(1f).fillMaxWidth().menuFade(scroll).verticalScroll(scroll).padding(ringReach())
                        .selectionHighlight(slots, focus, state.confirmation == null, scale)) {
                        if (state.copying == null) {
                            SaveListRow(SavePickerCopy.header(state.health), null, false, state.index == 0,
                                Modifier.selectionSlot(slots, 0).keepInView(state.index == 0).hostPress { dispatch(app.shell.touchSavePickerHeader(state.target)) },
                                attention = state.health.needsAttention)
                            val now = System.currentTimeMillis()
                            state.saves.forEachIndexed { index, save ->
                                SaveListRow(SavePickerCopy.row(save), relativeSaveWhen(save.updatedAtMillis, now, dates), save.playing, state.index == index + 1,
                                    Modifier.selectionSlot(slots, index + 1).keepInView(state.index == index + 1).hostPress { dispatch(app.shell.touchSavePickerSave(state.target, save.id)) })
                            }
                            if (state.saves.isEmpty()) SaveText(SavePickerCopy.empty)
                        } else {
                            val destinations = state.destinations()
                            destinations.forEachIndexed { index, destination ->
                                SaveListRow(SavePickerCopy.destination(destination), null, false, state.destinationIndex == index,
                                    Modifier.selectionSlot(slots, index).keepInView(state.destinationIndex == index).hostPress { dispatch(app.shell.touchSavePickerDestination(state.target, destination)) })
                            }
                            if (destinations.isEmpty()) SaveText(SavePickerCopy.noDestinations)
                        }
                    }
                }
            }
            state.message?.let { SaveText(it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(fit.edgeDp.dp)) {
                if (!state.loading && !state.busy && (state.error || state.copying != null || state.index > 0 || state.health.needsAttention)) HintWord(model, HintKey(confirm,
                    if (state.error) SavePickerCopy.retry else if (state.copying != null) SavePickerCopy.copyAction
                    else if (state.index > 0) SavePickerCopy.play else when (state.health) {
                        is SaveHealth.NotSyncing -> Copy.setUp
                        SaveHealth.Waiting -> Copy.syncGames
                        else -> SavePickerCopy.choose
                    }))
                if (!state.loading && !state.busy && !state.error && state.copying == null && state.focused != null) {
                    Row(Modifier.hostPress { dispatch(app.shell.onMeaning(Meaning.Options, HostScreen.Bottom)) }) {
                        HintWord(model, HintKey(actionFaces(model.faceMap).optionsGlyph, SavePickerCopy.copy))
                    }
                }
                if (!state.busy) Row(Modifier.hostPress { dispatch(app.shell.onMeaning(Meaning.Back, HostScreen.Bottom)) }) { HintWord(model, HintKey(back, Copy.back)) }
            }
        }
        state.confirmation?.let { choice ->
            val buttons = rememberSelectionSlots()
            Box(Modifier.fillMaxSize().background(theme.style.colors.scrim.copy(alpha = theme.style.dialog.scrimAlpha)), contentAlignment = Alignment.BottomCenter) {
                Column(Modifier.fillMaxWidth().padding(fit.edgeDp.dp).clip(RoundedCornerShape(theme.style.metrics.cardCornerDp.dp)).background(theme.surface).padding(fit.edgeDp.dp),
                    verticalArrangement = Arrangement.spacedBy(fit.gapDp.dp)) {
                    SaveText(SavePickerCopy.confirmationTitle(choice, state.playerName), title = true)
                    SaveText(SavePickerCopy.confirmationBody(choice, state.target.playerId, state.playerName, dates, state.restartsOpenGame))
                    Column(Modifier.fillMaxWidth().selectionHighlight(buttons, state.confirmIndex, true, scale)) {
                        val action = when (choice) { is SavePickerChoice.Play -> SavePickerCopy.play; is SavePickerChoice.Copy -> SavePickerCopy.copyAction; SavePickerChoice.Setup -> Copy.setUp }
                        listOf(action, SavePickerCopy.cancel).forEachIndexed { index, label ->
                            SaveListRow(label, null, false, state.confirmIndex == index,
                                Modifier.selectionSlot(buttons, index).hostPress { dispatch(app.shell.touchSavePickerConfirm(state.target, choice, index)) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveListRow(label: String, whenText: String?, playing: Boolean, focused: Boolean, modifier: Modifier, attention: Boolean = false) {
    val theme = LocalFoldTheme.current.theme
    val fit = theme.style.saves
    Row(modifier.fillMaxWidth().height(fit.rowHeightDp.dp).padding(horizontal = fit.gapDp.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(fit.gapDp.dp)) {
        BasicText(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = if (attention) theme.style.colors.attention else theme.onBackground.copy(alpha = if (focused) 1f else theme.style.menu.labelRestAlpha), fontSize = theme.style.type.menuRow, fontFamily = theme.font))
        if (playing) PlayingPill()
        whenText?.let { BasicText(it, maxLines = 1, style = TextStyle(color = theme.muted, fontSize = theme.style.type.hint, fontFamily = theme.font)) }
    }
}

@Composable
private fun PlayingPill() {
    val theme = LocalFoldTheme.current.theme
    BasicText(SavePickerCopy.playing, Modifier.clip(theme.style.shape.control()).background(theme.focus.copy(alpha = theme.style.dialog.chipStrongAlpha)).padding(horizontal = theme.style.saves.gapDp.dp),
        maxLines = 1, style = TextStyle(color = theme.focus, fontSize = theme.style.type.hint, fontFamily = theme.font))
}

@Composable
private fun SaveDetail(label: String, value: String) {
    val theme = LocalFoldTheme.current.theme
    Column { BasicText(label, style = TextStyle(color = theme.muted, fontSize = theme.style.type.hint, fontFamily = theme.font)); SaveText(value) }
}

@Composable
private fun SaveText(value: String, title: Boolean = false) {
    val theme = LocalFoldTheme.current.theme
    BasicText(value, style = TextStyle(color = theme.onBackground, fontSize = if (title) theme.style.type.heroTitle else theme.style.type.menuRow,
        fontFamily = if (title) theme.displayFont else theme.font, fontWeight = if (title) theme.style.type.strongWeight else theme.style.type.bodyWeight))
}
