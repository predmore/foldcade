package app.foldcade

import android.app.Activity
import android.content.pm.LauncherApps
import android.os.Bundle
import app.foldcade.plugins.droiddeck.DroidDeckPlayer
import app.foldcade.plugins.droiddeck.droidDeckShortcutKey
import app.foldcade.plugins.moonlight.MoonlightPlayer
import app.foldcade.plugins.moonlight.moonlightShortcutKey

/**
 * Android sends shortcut pin requests to the default home app. Moonlight's
 * Create shortcut on a game is one, and so is DroidDeck's Add to home screen.
 * Without this activity Android tells the app that pinning is unsupported,
 * and no game shortcut ever reaches Foldcade.
 *
 * A game shortcut from either app is accepted without asking, because the
 * user just chose to pin it. The shelf picks it up on the next shortcut read.
 * Other shortcuts have nowhere to go on the home grid, so those requests are
 * left unanswered and nothing is pinned. This activity draws nothing.
 */
class PinShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptGame()
        finish()
    }

    private fun acceptGame() {
        val request = getSystemService(LauncherApps::class.java)?.getPinItemRequest(intent) ?: return
        if (!request.isValid || request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) return
        val shortcut = request.shortcutInfo ?: return
        val accepted: () -> Unit = when {
            shortcut.`package` == MoonlightPlayer.OFFICIAL_PACKAGE && moonlightShortcutKey(shortcut.id) != null ->
                { -> (application as FoldcadeApp).acceptedMoonlightPin(shortcut.id) }
            shortcut.`package` == DroidDeckPlayer.PACKAGE && droidDeckShortcutKey(shortcut.id) != null ->
                { -> (application as FoldcadeApp).acceptedDroidDeckPin(shortcut.id) }
            else -> return
        }
        // Throws when the request was already answered.
        val pinned = try {
            request.accept()
        } catch (_: IllegalStateException) {
            false
        }
        if (pinned) accepted()
    }
}
