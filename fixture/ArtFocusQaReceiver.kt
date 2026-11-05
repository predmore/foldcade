package app.foldcade

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.foldcade.language.*
import java.io.File
import org.json.JSONObject

/** External debug-only QA fixture, injected by /tmp init script; never shipped in the PR. */
class ArtFocusQaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as FoldcadeApp
        val shell = app.shell
        val step = intent.getStringExtra("step") ?: return
        when (step) {
            "open" -> {
                shell.setArtKeySaved(true)
                shell.openHomeAll()
                shell.onMeaning(Meaning.Options, HostScreen.Bottom)
                val panel = checkNotNull(shell.model.panel)
                val row = panelRows(panel, shell.model).indexOf(Row.ChooseArt)
                check(row >= 0)
                shell.touchPanel(row, panel.screen)
                shell.touchPanel(row, panel.screen)
                shell.artInput(Meaning.RightPanel)
                val state = checkNotNull(shell.artPicker)
                val choices = listOf(620, 220, 400, 440, 570, 730, 10, 70).map { id ->
                    val url = "https://shared.steamstatic.com/store_item_assets/steam/apps/$id/library_hero.jpg"
                    ArtCandidate("$id", url, url)
                }
                shell.artCandidates(state.copy(candidates = choices, index = 0, findingMatches = false,
                    match = null, loading = false), loadingResult = false)
            }
            "select" -> shell.artPicker?.let { shell.artCandidates(it.copy(index = 5), loadingResult = false) }
            "late" -> shell.artPicker?.let { shell.artCurrent(it.gameId, mapOf(
                ArtKind.Tile to it.candidates[0].url, ArtKind.Logo to it.candidates[1].url)) }
            "apply" -> shell.artInput(Meaning.Activate)
        }
        val state = shell.artPicker
        val result = JSONObject().put("step", step).put("index", state?.index)
            .put("kind", state?.kind?.name).put("url", state?.focused?.url).put("game", state?.gameId)
        File(context.filesDir, "qa-art-selection.json").writeText(result.toString())
    }
}
