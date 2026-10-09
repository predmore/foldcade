package app.foldcade.language

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/**
 * Foldcade surfaces dispatch keys through [meaningOf] only.
 * When the picker is not the focused surface, the map is not consulted.
 */
abstract class PanelKeyActivity : ComponentActivity() {
    protected abstract fun pickerIsFocused(): Boolean

    /** Hero and picker both count. An external app does not. */
    protected open fun foldcadeSurfaceFocused(): Boolean = pickerIsFocused()

    protected abstract fun onMeaning(meaning: Meaning)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (pickerIsFocused()) onMeaning(Meaning.Back)
                }
            },
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return false
        val meaning = meaningOf(keyCode, event.repeatCount) ?: return false
        val shoulder = meaning == Meaning.LeftPanel || meaning == Meaning.RightPanel
        if (pickerIsFocused() || (shoulder && foldcadeSurfaceFocused())) {
            onMeaning(meaning)
            return true
        }
        return false
    }
}
