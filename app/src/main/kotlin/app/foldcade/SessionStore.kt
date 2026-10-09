package app.foldcade

import android.content.SharedPreferences
import app.foldcade.api.ExternalApp
import app.foldcade.api.Panel
import app.foldcade.api.Session
import app.foldcade.language.ButtonLabels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SessionStore(private val prefs: SharedPreferences) {
    var session by mutableStateOf(load())
        private set

    fun update(block: (Session) -> Session) {
        val next = block(session)
        if (next == session) return
        session = next
        save(next)
    }

    private fun load(): Session = Session(
        defaultDisplayIsTop = prefs.getBoolean(KEY_PRIMARY_TOP, true),
        gameScreens = decode(prefs.getString(KEY_GAME_SCREENS, null)),
        platformScreens = decode(prefs.getString(KEY_PLATFORM_SCREENS, null)),
    )

    private fun save(session: Session) {
        prefs.edit()
            .putBoolean(KEY_PRIMARY_TOP, session.defaultDisplayIsTop)
            .putString(KEY_GAME_SCREENS, encode(session.gameScreens))
            .putString(KEY_PLATFORM_SCREENS, encode(session.platformScreens))
            .apply()
    }

    fun buttonLabels(): ButtonLabels =
        if (prefs.getString(KEY_LABELS, ButtonLabels.Nintendo.name) == ButtonLabels.Xbox.name) {
            ButtonLabels.Xbox
        } else {
            ButtonLabels.Nintendo
        }

    fun setButtonLabels(labels: ButtonLabels) {
        prefs.edit().putString(KEY_LABELS, labels.name).apply()
    }

    fun homePromptSettled(): Boolean = prefs.getBoolean(KEY_HOME_PROMPTED, false)

    fun setHomePromptSettled() {
        prefs.edit().putBoolean(KEY_HOME_PROMPTED, true).apply()
    }

    fun setFolderTree(uri: String) {
        prefs.edit().putString(KEY_FOLDER, uri).apply()
    }

    fun folderGrantPending(): Boolean = !prefs.getBoolean(KEY_FOLDER_EXPLAINED, false)

    fun setFolderGrantExplained() {
        prefs.edit().putBoolean(KEY_FOLDER_EXPLAINED, true).apply()
    }

    private fun encode(screens: Map<String, Panel>): String =
        screens.entries.joinToString(",") { "${it.key}=${it.value.name}" }

    private fun decode(raw: String?): Map<String, Panel> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(",").mapNotNull { part ->
            val bits = part.split("=", limit = 2)
            if (bits.size != 2) return@mapNotNull null
            val panel = runCatching { Panel.valueOf(bits[1]) }.getOrNull() ?: return@mapNotNull null
            bits[0] to panel
        }.toMap()
    }

    fun place(panel: Panel, app: ExternalApp) = update { it.place(panel, app) }

    companion object {
        private const val KEY_PRIMARY_TOP = "primary_top"
        private const val KEY_GAME_SCREENS = "game_screens"
        private const val KEY_PLATFORM_SCREENS = "platform_screens"
        private const val KEY_LABELS = "button_labels"
        private const val KEY_HOME_PROMPTED = "home_prompted"
        private const val KEY_FOLDER = "folder_tree"
        private const val KEY_FOLDER_EXPLAINED = "folder_explained"
    }
}
