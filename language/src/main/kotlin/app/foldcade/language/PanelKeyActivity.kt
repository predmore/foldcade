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
    private companion object {
        /** Keys captured by typing or a confirmation hold stay away from the grid until key-up, on either screen. */
        val capturedKeys = HeldKeyGuard()
    }

    protected abstract fun pickerIsFocused(): Boolean

    /** Hero and picker both count. An external app does not. */
    protected open fun foldcadeSurfaceFocused(): Boolean = pickerIsFocused()

    protected abstract fun onMeaning(meaning: Meaning)

    protected open fun onInputKey(meaning: Meaning?, key: Int): Boolean = false

    protected open fun onInputCanceled() = Unit

    /** Characters a hardware keyboard typed into the open field (see [typedText]). Its keys have no [Meaning]. */
    protected open fun onTypedText(text: String) = Unit

    protected open fun onMeaningReleased(meaning: Meaning, canceled: Boolean) = Unit

    protected open fun onMeaningRepeated(meaning: Meaning, repeatCount: Int) = onMeaning(meaning)

    protected open fun faceMap(): FaceMap = FaceMap.standard()

    /** Moving a tile, picking an icon, typing, and All library keys. Idle everywhere else. */
    protected open fun homeKeys(): HomeKeys = HomeKeys.Idle

    protected open fun onPromptHeld(key: PromptKey, held: Boolean) = Unit

    protected open fun capturingHold(): Boolean = false

    protected open fun capturingConfirm(): Boolean = false

    protected open fun onCalibrateConfirm(key: PromptKey) = Unit

    /** A form of text rows is open. Its rows take D-pad up and down, which a focused text field would keep. */
    protected open fun rowsTakeVerticalKeys(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!pickerIsFocused()) return
                    val meaning = when (homeKeys()) {
                        HomeKeys.Moving, HomeKeys.PickingIcon -> Meaning.CancelHold
                        else -> Meaning.Back
                    }
                    onMeaning(meaning)
                }
            },
        )
    }

    /** Form navigation runs before a text view, in either an activity or a presentation. */
    fun dispatchFormKeyEvent(event: KeyEvent): Boolean? {
        val vertical = event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
        if (vertical && rowsTakeVerticalKeys()) {
            return when (event.action) {
                KeyEvent.ACTION_DOWN -> onKeyDown(event.keyCode, event)
                else -> onKeyUp(event.keyCode, event)
            }
        }
        return null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        dispatchFormKeyEvent(event) ?: super.dispatchKeyEvent(event)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val prompt = promptKeyOf(keyCode)
        if (prompt != null && event.repeatCount == 0) onPromptHeld(prompt, true)
        if (onInputKey(meaningOf(keyCode, 0, faceMap(), homeKeys()), keyCode)) return true
        if (prompt != null && prompt.face && event.repeatCount == 0 && capturingConfirm()) {
            onCalibrateConfirm(prompt)
            // Holding the newly chosen Confirm must not immediately arm calibration again.
            capturedKeys.capture(keyCode)
            return true
        }
        if (capturedKeys.swallows(keyCode, event.repeatCount, homeKeys().guardsHeldKeys || capturingHold())) return true
        if (keyCode == KeyEvent.KEYCODE_BACK) return false
        val meaning = meaningOf(keyCode, event.repeatCount, faceMap(), homeKeys())
            ?: return typesText(event) || consumesUnmapped(keyCode)
        val shoulder = meaning == Meaning.LeftPanel || meaning == Meaning.RightPanel
        if (pickerIsFocused() || (shoulder && foldcadeSurfaceFocused())) {
            onMeaningRepeated(meaning, event.repeatCount)
            if (capturingHold()) capturedKeys.capture(keyCode)
            return true
        }
        return consumesUnmapped(keyCode)
    }

    /**
     * A letter, digit or mark from a hardware keyboard, while a field takes text. Held, it
     * repeats as a keyboard's keys do. A shortcut with Ctrl or Meta types nothing.
     */
    private fun typesText(event: KeyEvent): Boolean {
        if (event.isCtrlPressed || event.isMetaPressed || !pickerIsFocused()) return false
        val text = typedText(event.getUnicodeChar(event.metaState), homeKeys()) ?: return false
        onTypedText(text)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCanceled) onInputCanceled()
        val prompt = promptKeyOf(keyCode)
        if (prompt != null) onPromptHeld(prompt, false)
        capturedKeys.up(keyCode, event.isCanceled)
        meaningOf(keyCode, 0, faceMap(), homeKeys())?.let { onMeaningReleased(it, event.isCanceled) }
        return consumesUnmapped(keyCode)
    }
}
