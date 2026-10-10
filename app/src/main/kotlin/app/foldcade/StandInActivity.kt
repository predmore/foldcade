package app.foldcade

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import app.foldcade.host.play.PLAY_CHECKPOINT_INTERVAL_MS
import app.foldcade.host.play.PlaySignal
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
import app.foldcade.language.TypeRamp
import app.foldcade.language.builtInTheme

/**
 * A stand-in for an external app. Keys are delivered here and not interpreted
 * by Foldcade. Returning false leaves them unconsumed.
 */
class StandInActivity : ComponentActivity() {
    private var lastKey by mutableStateOf<Int?>(null)
    private var placedOn: Panel? = null
    private var screenOff: BroadcastReceiver? = null
    private val checkpointHandler = Handler(Looper.getMainLooper())
    private val checkpointTick: Runnable = Runnable {
        playId()?.let { (application as FoldcadeApp).plays.checkpoint(it) }
        checkpointHandler.postDelayed(checkpointTick, PLAY_CHECKPOINT_INTERVAL_MS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val opened = intent.data
        if (opened != null) {
            runCatching { contentResolver.openInputStream(opened)?.close() }
        }
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
        val app = application as FoldcadeApp
        val id = intent.getStringExtra(EXTRA_ID)
        if (id != null) {
            val panel = Displays(this).panelFor(this, app.store.session.defaultDisplayIsTop)
            if (panel != null) {
                placedOn = panel
                app.store.place(panel, ExternalApp(id))
            }
        }
        watchScreenOff()
        playId()?.let { app.plays.signal(it, PlaySignal.Resume) }
        checkpointHandler.removeCallbacks(checkpointTick)
        checkpointHandler.postDelayed(checkpointTick, PLAY_CHECKPOINT_INTERVAL_MS)
    }

    override fun onPause() {
        checkpointHandler.removeCallbacks(checkpointTick)
        stopWatchingScreenOff()
        playId()?.let { id ->
            val interactive = getSystemService(PowerManager::class.java)?.isInteractive ?: true
            val signal = if (interactive) PlaySignal.Background else PlaySignal.Sleep
            (application as FoldcadeApp).plays.signal(id, signal)
        }
        super.onPause()
    }

    override fun onStop() {
        val id = intent.getStringExtra(EXTRA_ID)
        val panel = placedOn
        if (isFinishing && id != null && panel != null) {
            (application as FoldcadeApp).store.update { it.clearIfCurrent(panel, id) }
        }
        if (isFinishing) playId()?.let { (application as FoldcadeApp).plays.signal(it, PlaySignal.End) }
        super.onStop()
    }

    private fun playId(): String? = intent.getStringExtra(EXTRA_PLAY)

    private fun watchScreenOff() {
        if (screenOff != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_SCREEN_OFF) return
                playId()?.let { id ->
                    val plays = (application as FoldcadeApp).plays
                    plays.checkpoint(id)
                    plays.signal(id, PlaySignal.Sleep)
                }
            }
        }
        screenOff = receiver
        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED)
    }

    private fun stopWatchingScreenOff() {
        val receiver = screenOff ?: return
        screenOff = null
        try {
            unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            Unit
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        lastKey = keyCode
        return false
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PLAY = "play"
    }
}
