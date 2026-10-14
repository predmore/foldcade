package app.foldcade

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.foldcade.language.KeyFocus
import app.foldcade.language.Meaning
import app.foldcade.language.NAME_LIMIT
import app.foldcade.language.Typing
import app.foldcade.language.TypingCue
import app.foldcade.language.TypingStep
import app.foldcade.language.on
import app.foldcade.language.openTyping
import app.foldcade.language.press
import app.foldcade.language.withText

/**
 * One open controller keyboard and what happens when it closes. A host opens one while
 * its keys are [app.foldcade.language.HomeKeys.Typing], hands it every meaning and tap,
 * and draws [typing]; the keyboard never raises Android's own. [onDone] gets the trimmed
 * text and [onCancel] runs on B. [live], when given, hears every change to the text,
 * for a search that filters as it is typed. [chips] are one-press names above the keys.
 *
 * The RomM and SteamGridDB forms still use Android's keyboard, since their text is pasted.
 * Either could open one of these instead, with a longer [limit].
 */
class TypingSession(
    text: String,
    chips: List<String> = emptyList(),
    startOnChips: Boolean = false,
    limit: Int = NAME_LIMIT,
    private val live: ((String) -> Unit)? = null,
    private val onDone: (String) -> Unit,
    private val onCancel: () -> Unit,
) {
    var typing: Typing by mutableStateOf(openTyping(text, chips, startOnChips, limit))
        private set

    val text: String get() = typing.draft.text

    /** One key. The step's cue is the sound it makes. */
    fun handle(meaning: Meaning): TypingStep = apply(typing.on(meaning))

    /** A tap on a key or chip. */
    fun press(at: KeyFocus): TypingStep = apply(typing.press(at))

    /** Replaces the text outright, caret at its end, as a host clearing a search would. */
    fun setText(next: String) {
        apply(TypingStep(typing.withText(next), TypingCue.None))
    }

    private fun apply(step: TypingStep): TypingStep {
        val before = typing.draft.text
        typing = step.typing
        if (live != null && step.typing.draft.text != before) live.invoke(step.typing.draft.text)
        val saved = step.saved
        when {
            saved != null -> onDone(saved)
            step.cancelled -> onCancel()
        }
        return step
    }
}
