package app.foldcade

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Runs in its own process while Foldcade's own goes away, then brings Home back, so packs
 * installed or taken out load. It draws nothing. See [FoldcadeApp.restart].
 */
class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Long enough for the old process to have gone, so Home starts in a new one.
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(
                Intent(this, PrimaryHomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            finish()
            Runtime.getRuntime().exit(0)
        }, RESTART_DELAY_MILLIS)
    }
}

private const val RESTART_DELAY_MILLIS = 300L
