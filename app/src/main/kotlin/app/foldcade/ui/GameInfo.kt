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
    val revision = app.infoRevision
    val connections = app.shell.model.signedIn
    val cached = remember(record, revision, connections) { app.gameInfo.cached(record) }
    var fetched by remember(record, revision, connections) { mutableStateOf<GameDetails?>(null) }
    LaunchedEffect(record, revision, connections) {
        if (record != null && cached == null) fetched = app.gameInfo.fetch(record)
    }
    return cached ?: fetched
}
