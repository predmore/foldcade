package app.foldcade

import android.app.Activity
import android.content.pm.LauncherApps
import android.os.Bundle
import app.foldcade.plugins.moonlight.MoonlightPlayer
import app.foldcade.plugins.moonlight.moonlightShortcutKey

/**
 * Android sends shortcut pin requests to the default home app. Moonlight's
 * Create shortcut on a game is one. Without this activity Android tells
 * Moonlight that pinning is unsupported, and no game shortcut ever reaches
 * Foldcade.
 *
 * A Moonlight game shortcut is accepted without asking, because the user just
 * chose Create shortcut. The shelf picks it up on the next shortcut read. Other
 * shortcuts have nowhere to go on the home grid, so those requests are left
 * unanswered and nothing is pinned. This activity draws nothing.
 */
class PinShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptMoonlightGame()
        finish()
    }

    private fun acceptMoonlightGame() {
        val request = getSystemService(LauncherApps::class.java)?.getPinItemRequest(intent) ?: return
        if (!request.isValid || request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) return
        val shortcut = request.shortcutInfo ?: return
        if (shortcut.`package` != MoonlightPlayer.OFFICIAL_PACKAGE || moonlightShortcutKey(shortcut.id) == null) return
        // Throws when the request was already answered.
        val accepted = try {
            request.accept()
        } catch (_: IllegalStateException) {
            false
        }
        if (accepted) (application as FoldcadeApp).acceptedMoonlightPin(shortcut.id)
    }
}
