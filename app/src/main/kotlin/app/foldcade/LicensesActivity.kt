package app.foldcade

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.foldcade.language.LicenseCopy
import app.foldcade.language.Meaning
import app.foldcade.language.PanelKeyActivity
import app.foldcade.language.PromptKey
import app.foldcade.language.Theme
import app.foldcade.language.noticeBlocks
import app.foldcade.language.promptKeyOf
import app.foldcade.ui.LocalFoldTheme
import app.foldcade.ui.PageHeader
import app.foldcade.ui.control
import app.foldcade.ui.SETTINGS_PAGE_H
import app.foldcade.ui.SETTINGS_PAGE_V
import app.foldcade.ui.settingsCard
import kotlinx.coroutines.launch

/** Asset names for the source notices packaged by packageLicenses. */
internal const val FOLDCADE_LICENSE_ASSET = "Foldcade_LICENSE.txt"
internal const val MUSESCORE_LICENSE_ASSET = "MuseScore_General_License.md"

/**
 * Foldcade and MuseScore General MIT notices. Back returns to the left panel.
 * Keys stay on [PanelKeyActivity], so this screen does not name key codes.
 */
class LicensesActivity : PanelKeyActivity() {
    private var handle: (Meaning) -> Unit = { meaning ->
        if (meaning == Meaning.Back) finish()
    }

    override fun pickerIsFocused(): Boolean = true

    /** The player's own Confirm and Back, as Home reads them, so the Back the header names is the one that closes. */
    override fun faceMap(): app.foldcade.language.FaceMap = (application as FoldcadeApp).shell.model.faceMap

    override fun onMeaning(meaning: Meaning) {
        handle(meaning)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as FoldcadeApp).noteSettingsPage(this)
        val foldcadeNotice = assets.open(FOLDCADE_LICENSE_ASSET).bufferedReader().use { it.readText() }
        val museScoreNotice = assets.open(MUSESCORE_LICENSE_ASSET).bufferedReader().use { it.readText() }
        val notice = "Foldcade\n\n${foldcadeNotice.trim()}\n\n$museScoreNotice"
        val app = application as FoldcadeApp
        val paint = app.paintFor(app.shell.model.themeIndex)
        val theme = paint.theme
        setContent {
            val scroll = rememberScrollState()
            val scope = rememberCoroutineScope()
            val scale = theme.motion.animatorScale(LocalContext.current.contentResolver)
            SideEffect {
                handle = { meaning ->
                    when (meaning) {
                        Meaning.Back -> finish()
                        Meaning.MoveDown -> scope.launch { scroll.page(1, theme, scale) }
                        Meaning.MoveUp -> scope.launch { scroll.page(-1, theme, scale) }
                        else -> Unit
                    }
                }
            }
            val back = promptKeyOf(app.shell.model.faceMap.backKey) ?: PromptKey.FaceB
            // The theme's own key glyphs and card, as Settings draws them.
            CompositionLocalProvider(LocalFoldTheme provides paint) {
                LicensesScreen(theme = theme, notice = notice, scroll = scroll, back = back, onBack = ::finish)
            }
        }
        hideSystemBars()
    }

    override fun onStart() {
        super.onStart()
        (application as FoldcadeApp).settingsPageActivity = this
    }

    override fun onStop() {
        (application as FoldcadeApp).let { if (it.settingsPageActivity === this) it.settingsPageActivity = null }
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }
}

/**
 * Settings' shell: Back's pill over one card, as tall as the page, that the D-pad pages through.
 * The notices wrap to the card ([noticeBlocks]); a bar in the card's gutter shows how far down
 * the page is and that there is more.
 */
@Composable
private fun LicensesScreen(theme: Theme, notice: String, scroll: ScrollState, back: PromptKey, onBack: () -> Unit) {
    val blocks = remember(notice) { noticeBlocks(notice) }
    Column(
        Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = SETTINGS_PAGE_H, vertical = SETTINGS_PAGE_V),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PageHeader(theme, back, LicenseCopy.title, onBack)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .settingsCard(theme)
                .padding(start = 28.dp, end = 14.dp),
        ) {
            Column(
                Modifier.fillMaxSize().padding(end = 22.dp).verticalScroll(scroll).padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                blocks.forEach { block ->
                    BasicText(
                        text = block.text,
                        style = TextStyle(
                            color = theme.onBackground,
                            fontFamily = if (block.heading) theme.displayFont else theme.font,
                            fontSize = if (block.heading) theme.style.type.dialogTitle else theme.style.type.dialogBody,
                            fontWeight = if (block.heading) theme.style.type.strongWeight else theme.style.type.bodyWeight,
                        ),
                    )
                }
            }
            ScrollBar(theme, scroll, Modifier.align(Alignment.CenterEnd).padding(vertical = 24.dp))
        }
    }
}

/** The strip bar's look, standing up: the track muted, the thumb as tall as the share of the page in view. */
@Composable
private fun ScrollBar(theme: Theme, scroll: ScrollState, modifier: Modifier) {
    if (scroll.maxValue <= 0 || scroll.viewportSize <= 0) return
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxHeight().width(with(density) { theme.style.tile.stripPx.toDp() })) {
        val total = (scroll.maxValue + scroll.viewportSize).toFloat()
        val thumb = maxHeight * (scroll.viewportSize / total)
        Box(Modifier.fillMaxSize().background(theme.muted.copy(alpha = theme.style.tile.stripAlpha), theme.style.shape.control()))
        Box(
            Modifier
                .graphicsLayer { translationY = (maxHeight - thumb).toPx() * (scroll.value.toFloat() / scroll.maxValue) }
                .fillMaxWidth()
                .height(thumb)
                .background(theme.onBackground, theme.style.shape.control()),
        )
    }
}

/** A page at a time, gliding, as the grid's strip does. */
private suspend fun ScrollState.page(direction: Int, theme: Theme, scale: Float) {
    val step = (viewportSize * PAGE_SHARE).toInt().coerceAtLeast(240)
    animateScrollTo((value + direction * step).coerceIn(0, maxValue), theme.motion.arrive(theme.motion.durationTravel, scale))
}

/** A page step leaves a line or two of the last page in view, so the reader keeps their place. */
private const val PAGE_SHARE = 0.85f
