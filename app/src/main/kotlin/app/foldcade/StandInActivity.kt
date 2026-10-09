package app.foldcade

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import app.foldcade.api.ExternalApp
import app.foldcade.api.Panel
import app.foldcade.language.TypeRamp
import app.foldcade.language.builtInTheme

/**
 * A stand-in for an external app. Keys are delivered here and not interpreted
 * by Foldcade. Returning false leaves them unconsumed.
 */
class StandInActivity : ComponentActivity() {
    private var lastKey by mutableStateOf<Int?>(null)
    private var placedOn: Panel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val theme = builtInTheme()
            val title = intent.getStringExtra(EXTRA_TITLE) ?: intent.getStringExtra(EXTRA_ID) ?: ""
            val key = lastKey
            Box(
                modifier = Modifier.fillMaxSize().background(theme.background),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = if (key == null) title else "$title\nkey $key",
                    style = TextStyle(
                        color = theme.onBackground,
                        fontFamily = theme.font,
                        fontSize = TypeRamp.heroTitle,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val app = application as FoldcadeApp
        val panel = Displays(this).panelFor(this, app.store.session.defaultDisplayIsTop) ?: return
        placedOn = panel
        app.store.place(panel, ExternalApp(id))
    }

    override fun onStop() {
        val id = intent.getStringExtra(EXTRA_ID)
        val panel = placedOn
        if (isFinishing && id != null && panel != null) {
            (application as FoldcadeApp).store.update { it.clearIfCurrent(panel, id) }
        }
        super.onStop()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        lastKey = keyCode
        return false
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
    }
}
