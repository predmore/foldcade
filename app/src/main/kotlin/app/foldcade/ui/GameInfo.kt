// SPDX-License-Identifier: MIT

package app.foldcade.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.foldcade.FoldcadeApp
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameDetails

/** Loaded facts immediately, then one cancellable lookup for the focused library record. */
@Composable
internal fun rememberGameDetails(app: FoldcadeApp, record: Game?): GameDetails? {
    return rememberGameDetailsState(app, record).details
}

/** A completed lookup with no facts is different from one still waiting on a provider. */
internal data class GameDetailsState(val details: GameDetails?, val resolved: Boolean)

@Composable
internal fun rememberGameDetailsState(app: FoldcadeApp, record: Game?): GameDetailsState {
    val revision = app.infoRevision
    val connections = app.shell.model.signedIn
    val cached = remember(record, revision, connections) { app.gameInfo.cached(record) }
    var fetched by remember(record, revision, connections) { mutableStateOf(GameDetailsState(null, false)) }
    LaunchedEffect(record, revision, connections) {
        if (record != null && cached == null) fetched = GameDetailsState(app.gameInfo.fetch(record), true)
    }
    return if (cached != null || record == null) GameDetailsState(cached, true) else fetched
}
