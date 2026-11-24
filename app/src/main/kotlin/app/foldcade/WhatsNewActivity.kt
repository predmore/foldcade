package app.foldcade

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import app.foldcade.language.*
import app.foldcade.ui.LocalFoldTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The same native page and companion as Licenses, with cached release notes and an explicit retry. */
class WhatsNewActivity : PanelKeyActivity() {
    private var handle: (Meaning) -> Unit = { if (it == Meaning.Back) finish() }
    private var failed by mutableStateOf(false)
    override fun pickerIsFocused(): Boolean = true
    override fun faceMap(): FaceMap = (application as FoldcadeApp).shell.model.faceMap
    override fun companionTitle(): String = UpdateCopy.title
    override fun companionHints(): List<HintKey> = updatesHints(faceMap(), failed)
    override fun onMeaning(meaning: Meaning) { handle(meaning) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FoldcadeApp
        val update = intent.readUpdate() ?: run { finish(); return }
        val paint = app.paintFor(app.shell.model.themeIndex)
        val theme = paint.theme
        setContent {
            val scroll = rememberScrollState()
            val scope = rememberCoroutineScope()
            val scale = theme.motion.animatorScale(LocalContext.current.contentResolver)
            var text by remember { mutableStateOf(UpdateCopy.loading) }
            var request by remember { mutableIntStateOf(0) }
            LaunchedEffect(request) {
                text = UpdateCopy.loading
                failed = false
                text = try {
                    val notes = withContext(Dispatchers.IO) { app.componentUpdates.notes(update) }
                    updateNotesText(update, notes)
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
                } catch (_: Exception) { failed = true; UpdateCopy.failed }
            }
            SideEffect {
                handle = { meaning -> when (meaning) {
                    Meaning.Back -> finish()
                    Meaning.MoveDown -> scope.launch { scroll.page(1, theme, scale) }
                    Meaning.MoveUp -> scope.launch { scroll.page(-1, theme, scale) }
                    Meaning.Activate -> if (failed) request++ else Unit
                    else -> Unit
                } }
            }
            val back = promptKeyOf(faceMap().backKey) ?: PromptKey.FaceB
            CompositionLocalProvider(LocalFoldTheme provides paint) {
                NoticePage(theme, UpdateCopy.title, text, scroll, back, ::finish, smallLine = update.fromName?.let(UpdateCopy::from))
            }
        }
        hideSystemBars()
    }

    override fun onStart() {
        super.onStart()
        (application as FoldcadeApp).let { it.settingsPageActivity = this; it.noteSettingsPage(this) }
    }
    override fun onStop() { (application as FoldcadeApp).settingsPageCovered(this); super.onStop() }
    override fun onDestroy() {
        (application as FoldcadeApp).let { if (it.settingsPageActivity === this) it.settingsPageActivity = null }
        super.onDestroy()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }
}

internal fun putUpdate(intent: Intent, update: ComponentUpdate) {
    intent.putExtra("updatePackage", update.packageName).putExtra("updateLabel", update.label)
        .putExtra("fromCode", update.fromCode).putExtra("fromName", update.fromName)
        .putExtra("toCode", update.toCode).putExtra("toName", update.toName)
}
private fun Intent.readUpdate(): ComponentUpdate? {
    val packageName = getStringExtra("updatePackage") ?: return null
    return ComponentUpdate(packageName, getStringExtra("updateLabel").orEmpty(), getLongExtra("fromCode", 0),
        getStringExtra("fromName"), getLongExtra("toCode", 0), getStringExtra("toName").orEmpty(), 0)
}
