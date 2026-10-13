package app.foldcade.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import app.foldcade.CoverImage
import app.foldcade.artwork.ArtQuery
import app.foldcade.language.artPickerHints
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.foldcade.FoldcadeApp
import app.foldcade.language.ArtCandidate
import app.foldcade.language.ArtCopy
import app.foldcade.language.ArtKind
import app.foldcade.language.ArtPickerState
import app.foldcade.language.Meaning
import app.foldcade.language.TypeRamp
import kotlin.coroutines.cancellation.CancellationException

@Composable
internal fun ArtPickerScreen(app: FoldcadeApp, state: ArtPickerState, scale: Float) {
    val theme = LocalFoldTheme.current.theme
    val query = app.shell.focusedGame()?.let(app.shell::artQuery)
    LaunchedEffect(state.gameId, state.kind) {
        if (query == null) return@LaunchedEffect
        try {
            val kind = when (state.kind) { ArtKind.Tile -> "grids"; ArtKind.Hero -> "heroes"; ArtKind.Logo -> "logos" }
            val candidates = app.steamGridDb.candidates(query, kind).map { ArtCandidate(it.id, it.url, it.thumbnail) }
            val current = selectedArt(app, state, query)
            val index = candidates.indexOfFirst { it.url == current }.coerceAtLeast(0)
            app.shell.artCandidates(state.copy(candidates = candidates, index = index, loading = false))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            app.shell.artCandidates(state.copy(loading = false, error = true))
        }
    }
    val tabs = remember { mutableMapOf<ArtKind, ArtPickerState>() }
    tabs[state.kind] = state
    val slots = rememberSelectionSlots()
    val reach = ringReach()
    Column(Modifier.fillMaxSize().background(theme.background).padding(reach)) {
        Row(Modifier.fillMaxWidth().selectionHighlight(slots, state.kind.ordinal, true, scale)) {
            ArtCopy.tabs.forEachIndexed { index, title ->
                Box(Modifier.weight(1f).height(44.dp).selectionSlot(slots, index).clickable {
                    val distance = (index - state.kind.ordinal + 3) % 3
                    repeat(distance) { app.shell.artInput(Meaning.RightPanel) }
                }, contentAlignment = Alignment.Center) {
                    BasicText(title, style = TextStyle(color = theme.onBackground, fontSize = TypeRamp.menuRow))
                }
            }
        }
        Crossfade(state.kind, animationSpec = theme.motion.arrive(theme.motion.durationTravel, scale), modifier = Modifier.weight(1f)) { kind ->
            val shown = tabs[kind] ?: state.copy(kind = kind, candidates = emptyList(), loading = true)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val cellWidth = (maxWidth - reach * 2) / 4
                val cellHeight = maxHeight / 3
                val scroll = rememberLazyListState()
                LaunchedEffect(shown.index) {
                    val column = shown.index / 3
                    if (column < scroll.firstVisibleItemIndex) scroll.animateScrollToItem(column)
                    else if (column >= scroll.firstVisibleItemIndex + 4) scroll.animateScrollToItem((column - 3).coerceAtLeast(0))
                }
                val gridSlots = rememberSelectionSlots()
                var origin by remember { mutableStateOf(Offset.Zero) }
                LazyRow(state = scroll, modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }, contentPadding = PaddingValues(reach)) {
                    val count = if (shown.loading) 12 else shown.candidates.size
                    items((count + 2) / 3) { column ->
                        Column(Modifier.width(cellWidth)) {
                            repeat(3) { row ->
                                val index = column * 3 + row
                                val candidate = shown.candidates.getOrNull(index)
                                Box(Modifier.width(cellWidth).height((cellHeight - reach * 2 / 3).coerceAtLeast(1.dp)).padding(6.dp)) {
                                    if (candidate != null || shown.loading) {
                                        ArtCandidateTile(app, candidate, candidate != null && candidate.url == selectedArt(app, state.copy(kind = kind), query), Modifier.fillMaxSize()
                                            .onGloballyPositioned { coordinates ->
                                                val topLeft = coordinates.positionInRoot() - origin
                                                val rect = Rect(topLeft, coordinates.size.toSize())
                                                if (gridSlots.rows[index] != rect) gridSlots.rows[index] = rect
                                            }.clickable(enabled = candidate != null) {
                                                app.shell.artCandidates(shown.copy(index = index, preview = true), loadingResult = false)
                                                app.shell.artInput(Meaning.Activate)
                                            }, scale)
                                    }
                                }
                            }
                        }
                    }
                }
                // Slots share the viewport coordinates, so one ring glides between candidates.
                Box(Modifier.fillMaxSize().selectionHighlight(gridSlots, shown.index.takeIf { !shown.loading && shown.candidates.isNotEmpty() }, true, scale))
                if (!shown.loading && shown.candidates.isEmpty()) BasicText(
                    if (shown.error) ArtCopy.error else ArtCopy.empty,
                    modifier = Modifier.align(Alignment.Center), style = TextStyle(color = theme.muted, fontSize = TypeRamp.menuRow),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            artPickerHints(app.shell.model.faceMap).forEach { HintWord(app.shell.model, it) }
        }
    }
}

@Composable
private fun ArtCandidateTile(app: FoldcadeApp, candidate: ArtCandidate?, current: Boolean, modifier: Modifier, scale: Float) {
    val theme = LocalFoldTheme.current.theme
    val context = LocalContext.current
    val request = remember(candidate?.thumbnail) {
        ImageRequest.Builder(context).data(candidate?.thumbnail?.let(::CoverImage)).size(384).build()
    }
    val painter = rememberAsyncImagePainter(request, app.images)
    val status by painter.state.collectAsState()
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(theme.surface)) {
        Crossfade(status is AsyncImagePainter.State.Success, animationSpec = theme.motion.arrive(theme.motion.durationFocus, scale)) { loaded ->
            if (loaded) Image(painter, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else if (candidate != null && status is AsyncImagePainter.State.Error) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText(ArtCopy.error, style = TextStyle(color = theme.muted, fontSize = TypeRamp.hint), maxLines = 1)
                }
            } else {
                val transition = rememberInfiniteTransition()
                val shimmer by transition.animateFloat(0f, 1f, theme.motion.pulse(scale))
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(
                    listOf(theme.surface, theme.focus.copy(alpha = 0.18f), theme.surface),
                    start = Offset(shimmer * 600f - 300f, 0f), end = Offset(shimmer * 600f, 300f),
                )))
            }
        }
        if (current) BasicText(ArtCopy.current, Modifier.align(Alignment.TopEnd).background(theme.surface).padding(6.dp), style = TextStyle(color = theme.focus, fontSize = TypeRamp.menuRow))
    }
}

private fun selectedArt(app: FoldcadeApp, state: ArtPickerState, query: ArtQuery?): String? =
    app.artChoices.get(state.gameId, state.kind) ?: when (state.kind) {
        ArtKind.Tile -> app.covers.cached(app.shell.focusedRecord(), query)?.let { it.square ?: it.cover }
        ArtKind.Hero -> app.covers.cachedScene(app.shell.focusedRecord(), query)?.background
        ArtKind.Logo -> app.covers.cachedScene(app.shell.focusedRecord(), query)?.logo
    }
