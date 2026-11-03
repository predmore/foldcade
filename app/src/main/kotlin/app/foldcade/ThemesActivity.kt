package app.foldcade

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.foldcade.language.Meaning
import app.foldcade.language.PanelKeyActivity
import app.foldcade.language.Theme
import app.foldcade.language.ThemeEntry
import app.foldcade.language.ThemeInstallRefusal
import app.foldcade.language.ThemesAction
import app.foldcade.language.ThemesCopy
import app.foldcade.language.ThemesPage
import app.foldcade.language.ThemesRow
import app.foldcade.language.PromptKey
import app.foldcade.language.promptKeyOf
import app.foldcade.language.themeFacts
import app.foldcade.ui.LocalFoldTheme
import app.foldcade.ui.PageHeader
import app.foldcade.ui.SETTINGS_PAGE_H
import app.foldcade.ui.SETTINGS_PAGE_V
import app.foldcade.ui.settingsCard
import app.foldcade.ui.keepInView
import app.foldcade.ui.rememberSelectionSlots
import app.foldcade.ui.ringScroll
import app.foldcade.ui.selectionHighlight
import app.foldcade.ui.selectionSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings › Look › Themes. Every pack, what it says about itself, and what Foldcade found while
 * loading it, so a theme's author can read the report on the device. Add a theme installs a zip
 * from the file picker for the next start; X twice takes an installed one out. Keys stay on
 * [PanelKeyActivity], so this screen does not name key codes.
 */
class ThemesActivity : PanelKeyActivity() {
    private val app get() = application as FoldcadeApp
    private var page by mutableStateOf(ThemesPage(emptyList()))

    private val pick = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::install) }

    override fun pickerIsFocused(): Boolean = true

    /**
     * Only the D-pad repeats here. A held bottom-screen button would otherwise arm a removal and confirm it in one
     * press, and a held A would open the picker again as it closes.
     */
    override fun onMeaningRepeated(meaning: Meaning, repeatCount: Int) {
        if (repeatCount == 0 || meaning == Meaning.MoveUp || meaning == Meaning.MoveDown) onMeaning(meaning)
    }

    /** The player's own Confirm and Back, as Home reads them. */
    override fun faceMap(): app.foldcade.language.FaceMap = app.shell.model.faceMap

    override fun onMeaning(meaning: Meaning) {
        val (next, action) = page.on(meaning, faceMap())
        page = next
        when (action) {
            null -> Unit
            ThemesAction.Close -> finish()
            ThemesAction.Add -> pick.launch(ZIP_TYPES)
            ThemesAction.Restart -> app.restart(this)
            is ThemesAction.Remove -> remove(action.entry)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = ThemesPage(app.themeEntries(), restartable = app.themesChanged)
        val theme = app.paintFor(app.shell.model.themeIndex)
        setContent {
            CompositionLocalProvider(LocalFoldTheme provides theme) {
                val back = promptKeyOf(faceMap().backKey) ?: PromptKey.FaceB
                ThemesScreen(theme.theme, page, back, theme.theme.motion.animatorScale(LocalContext.current.contentResolver)) { onMeaning(Meaning.Back) }
            }
        }
        hideSystemBars()
    }

    override fun onStart() {
        super.onStart()
        app.settingsPagesOpen++
    }

    override fun onStop() {
        app.settingsPagesOpen--
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun install(uri: Uri) {
        page = page.adding()
        lifecycleScope.launch {
            val note = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use(app::installTheme)
                } catch (_: Exception) { null }
            }
            page = when (note) {
                is ThemeInstallResult.Installed -> page.settled(ThemesCopy.added, changed = true)
                is ThemeInstallResult.Refused -> page.settled(ThemesCopy.refused(note.reason), changed = false)
                null -> page.settled(ThemesCopy.refused(ThemeInstallRefusal.NotATheme), changed = false)
            }
        }
    }

    private fun remove(entry: ThemeEntry) {
        val pack = app.themePacks.firstOrNull { it.resources.id == entry.id } ?: return
        page = page.adding()
        lifecycleScope.launch {
            val removed = withContext(Dispatchers.IO) { runCatching { app.removeTheme(pack) }.getOrDefault(false) }
            // A pack taken out leaves the list, though it stays in use until the restart.
            page = if (removed) page.settled(ThemesCopy.removed, changed = true, packs = page.packs - entry)
                else page.settled(ThemesCopy.notRemoved, changed = false)
        }
    }
}

/** What the picker offers. Many file managers call a zip a stream of bytes. */
private val ZIP_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

@Composable
private fun ThemesScreen(theme: Theme, page: ThemesPage, back: PromptKey, scale: Float, onBack: () -> Unit) {
    val design = theme.style
    // Settings' shell: the head over the packs on the left, and the focused pack's card on the right.
    Row(
        Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = SETTINGS_PAGE_H, vertical = SETTINGS_PAGE_V),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(0.32f).fillMaxHeight()) {
            PageHeader(theme, back, ThemesCopy.title, onBack)
            val slots = rememberSelectionSlots()
            Column(
                Modifier
                    .padding(top = 20.dp)
                    .weight(1f)
                    .fillMaxWidth()
                    .ringScroll()
                    .selectionHighlight(slots, page.focus, ring = true, scale = scale),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                page.rows.forEachIndexed { index, row ->
                    val focused = index == page.focus
                    val label = when (row) {
                        is ThemesRow.Pack -> row.entry.name
                        ThemesRow.Add -> ThemesCopy.add
                        ThemesRow.Restart -> ThemesCopy.restart
                    }
                    BasicText(
                        label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectionSlot(slots, index)
                            .keepInView(focused)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        style = textStyle(theme, if (focused) theme.onBackground else theme.onBackground.copy(alpha = design.menu.labelRestAlpha), design.type.menuRow),
                    )
                }
            }
            // What the page says back, as why an install was refused, under the rows it is about.
            page.note?.let {
                BasicText(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = textStyle(theme, theme.onBackground, design.type.hint))
            }
        }
        Box(
            Modifier
                .weight(0.68f)
                .fillMaxHeight()
                .settingsCard(theme)
                .padding(28.dp),
        ) {
            (page.focused as? ThemesRow.Pack)?.entry?.let { PackDetails(theme, it) }
        }
    }
}

/** A pack's own words about itself, then anything Foldcade found wrong while loading it. */
@Composable
private fun PackDetails(theme: Theme, entry: ThemeEntry) {
    val design = theme.style
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(entry.name, style = textStyle(theme, theme.onBackground, design.type.dialogTitle, display = true))
        val facts = themeFacts(entry)
        if (facts.isNotEmpty()) BasicText(facts.joinToString(" · "), style = textStyle(theme, theme.muted, design.type.hint))
        entry.about.description?.let { BasicText(it, style = textStyle(theme, theme.onBackground, design.type.dialogBody)) }
        if (entry.parents.isNotEmpty()) BasicText("${ThemesCopy.builds} ${entry.parents.joinToString(" › ")}", style = textStyle(theme, theme.muted, design.type.hint))
        entry.about.license?.let { BasicText("${ThemesCopy.license} $it", style = textStyle(theme, theme.muted, design.type.hint)) }
        // A pack that loaded cleanly says nothing more; an author reads what went wrong here.
        entry.diagnostics.forEachIndexed { index, it ->
            BasicText(
                "${it.source}  ${it.message}",
                style = textStyle(theme, theme.onBackground, design.type.hint),
                modifier = if (index == 0) Modifier.padding(top = 8.dp) else Modifier,
            )
        }
    }
}

private fun textStyle(theme: Theme, color: androidx.compose.ui.graphics.Color, size: TextUnit, display: Boolean = false) = TextStyle(
    color = color,
    fontSize = size,
    fontFamily = if (display) theme.displayFont else theme.font,
    fontWeight = theme.style.type.bodyWeight,
)
