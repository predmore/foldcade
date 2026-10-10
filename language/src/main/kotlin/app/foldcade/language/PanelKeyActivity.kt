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

    protected open fun faceMap(): FaceMap = FaceMap.standard()

    protected open fun onPromptHeld(key: PromptKey, held: Boolean) = Unit

    protected open fun capturingConfirm(): Boolean = false

    protected open fun onCalibrateConfirm(key: PromptKey) = Unit

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
        val prompt = promptKeyOf(keyCode)
        if (prompt != null && event.repeatCount == 0) onPromptHeld(prompt, true)
        if (prompt != null && prompt.face && event.repeatCount == 0 && capturingConfirm()) {
            onCalibrateConfirm(prompt)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) return false
        val meaning = meaningOf(keyCode, event.repeatCount, faceMap()) ?: return false
        val shoulder = meaning == Meaning.LeftPanel || meaning == Meaning.RightPanel
        if (pickerIsFocused() || (shoulder && foldcadeSurfaceFocused())) {
            onMeaning(meaning)
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val prompt = promptKeyOf(keyCode)
        if (prompt != null) onPromptHeld(prompt, false)
        return false
    }
}
