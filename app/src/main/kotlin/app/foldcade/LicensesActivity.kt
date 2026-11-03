package app.foldcade

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.foldcade.language.LicenseCopy
import app.foldcade.language.Meaning
import app.foldcade.language.PanelKeyActivity
import app.foldcade.language.PromptKey
import app.foldcade.language.Theme
import app.foldcade.language.promptKeyOf
import app.foldcade.ui.LocalFoldTheme
import app.foldcade.ui.PageHeader
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
        val foldcadeNotice = assets.open(FOLDCADE_LICENSE_ASSET).bufferedReader().use { it.readText() }
        val museScoreNotice = assets.open(MUSESCORE_LICENSE_ASSET).bufferedReader().use { it.readText() }
        val notice = "Foldcade\n\n${foldcadeNotice.trim()}\n\n$museScoreNotice"
        val app = application as FoldcadeApp
        val paint = app.paintFor(app.shell.model.themeIndex)
        val theme = paint.theme
        setContent {
            val scroll = rememberScrollState()
            val scope = rememberCoroutineScope()
            SideEffect {
                handle = { meaning ->
                    when (meaning) {
                        Meaning.Back -> finish()
                        Meaning.MoveDown -> scope.launch { scroll.page(1) }
                        Meaning.MoveUp -> scope.launch { scroll.page(-1) }
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
        (application as FoldcadeApp).settingsPagesOpen++
    }

    override fun onStop() {
        (application as FoldcadeApp).settingsPagesOpen--
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }
}

/** Settings' shell: Back's pill over one card, as tall as the page, that the D-pad pages through. */
@Composable
private fun LicensesScreen(theme: Theme, notice: String, scroll: ScrollState, back: PromptKey, onBack: () -> Unit) {
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
                .padding(horizontal = 28.dp),
        ) {
            BasicText(
                text = notice.trim(),
                modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(vertical = 24.dp),
                style = TextStyle(
                    color = theme.onBackground,
                    fontFamily = theme.font,
                    fontSize = theme.style.type.dialogBody,
                    fontWeight = theme.style.type.bodyWeight,
                ),
            )
        }
    }
}

private suspend fun ScrollState.page(direction: Int) {
    val step = viewportSize.coerceAtLeast(240)
    scrollTo((value + direction * step).coerceIn(0, maxValue))
}
