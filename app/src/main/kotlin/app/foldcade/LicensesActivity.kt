package app.foldcade

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.foldcade.language.Copy
import app.foldcade.language.LicenseCopy
import app.foldcade.language.Meaning
import app.foldcade.language.PanelKeyActivity
import app.foldcade.language.Theme
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

    override fun onMeaning(meaning: Meaning) {
        handle(meaning)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val foldcadeNotice = assets.open(FOLDCADE_LICENSE_ASSET).bufferedReader().use { it.readText() }
        val museScoreNotice = assets.open(MUSESCORE_LICENSE_ASSET).bufferedReader().use { it.readText() }
        val notice = "Foldcade\n\n${foldcadeNotice.trim()}\n\n$museScoreNotice"
        val app = application as FoldcadeApp
        val theme = app.paintFor(app.shell.model.themeIndex).theme
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
            LicensesScreen(theme = theme, notice = notice, scroll = scroll)
        }
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }
}

@Composable
private fun LicensesScreen(theme: Theme, notice: String, scroll: ScrollState) {
    Column(
        Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        BasicText(
            text = LicenseCopy.title,
            style = TextStyle(
                color = theme.onBackground,
                fontFamily = theme.displayFont,
                fontSize = theme.style.type.dialogTitle,
                fontWeight = theme.style.type.bodyWeight,
            ),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(top = 24.dp)
                .verticalScroll(scroll),
        ) {
            BasicText(
                text = notice.trim(),
                style = TextStyle(
                    color = theme.onBackground,
                    fontFamily = theme.font,
                    fontSize = theme.style.type.dialogBody,
                    fontWeight = theme.style.type.bodyWeight,
                ),
            )
        }
        BasicText(
            text = Copy.back,
            modifier = Modifier.padding(top = 16.dp),
            style = TextStyle(
                color = theme.muted,
                fontFamily = theme.font,
                fontSize = theme.style.type.hint,
                fontWeight = theme.style.type.bodyWeight,
            ),
        )
    }
}

private suspend fun ScrollState.page(direction: Int) {
    val step = viewportSize.coerceAtLeast(240)
    scrollTo((value + direction * step).coerceIn(0, maxValue))
}
