package app.foldcade

import android.content.SharedPreferences
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GamePage
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.ListedPlatform
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.Placement
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveSet
import app.foldcade.host.PluginHost
import app.foldcade.language.HostScreen
import app.foldcade.language.Meaning
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellHostTest {
    @Test
    fun libraryNamesComeFromTheGuardedAccessor() {
        val host = PluginHost(Dispatchers.Unconfined)
        host.register(entry(LabelLibrary("sample.library", "Sample library")))
        val shell = shell(host)
        openLibrary(shell)
        assertEquals(listOf("Sample library"), shell.model.backends)
        assertFalse(shell.model.unavailable)
    }

    @Test
    fun pluginCallExceptionBecomesUnavailable() {
        val host = PluginHost(Dispatchers.Unconfined)
        host.register(entry(object : LabelLibrary("boom.library", "Boom") {
            override val displayName: String
                get() = throw IllegalStateException("label")
        }))
        val shell = shell(host)
        openLibrary(shell)
        assertTrue(shell.model.unavailable)
    }

    @Test
    fun typedPluginExceptionIsNotTheUnavailableState() {
        val host = PluginHost(Dispatchers.Unconfined)
        host.register(entry(object : LabelLibrary("typed.library", "Typed") {
            override val displayName: String
                get() = throw PluginException.Unavailable("offline")
        }))
        val shell = shell(host)
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        val failure = runCatching { shell.onMeaning(Meaning.Activate, HostScreen.Bottom) }
        assertTrue(failure.exceptionOrNull() is PluginException.Unavailable)
        assertFalse(shell.model.unavailable)
    }

    @Test
    fun noRegisteredLibraryStaysAvailable() {
        val shell = shell(PluginHost(Dispatchers.Unconfined))
        openLibrary(shell)
        assertFalse(shell.model.unavailable)
        assertTrue(shell.model.backends.isEmpty())
    }

    private fun shell(host: PluginHost) = ShellController(SessionStore(MemoryPrefs()), host)

    private fun openLibrary(shell: ShellController) {
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
    }

    private fun entry(library: LibraryBackend) = object : PluginEntry {
        override val apiVersion = PLUGIN_API_VERSION
        override val libraries = listOf(library)
    }
}

private open class LabelLibrary(
    override val id: String,
    private val label: String,
) : LibraryBackend {
    override val displayName: String
        get() = label

    override suspend fun connect() = Unit

    override suspend fun disconnect() = Unit

    override suspend fun listPlatforms(): List<ListedPlatform> = emptyList()

    override suspend fun listGames(platformId: String, query: GameQuery): GamePage =
        GamePage(emptyList(), null)

    override suspend fun ensureLocal(game: Game): LaunchTarget =
        LaunchTarget.ContentUri(uri = game.remoteKey)

    override suspend fun saves(game: Game): SaveSet = SaveSet(id, emptyList())

    override suspend fun prepareLaunch(game: Game, player: Player): Placement =
        Placement(target = LaunchTarget.ContentUri(uri = game.remoteKey))

    override suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves) =
        error("unused")
}

private class MemoryPrefs : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = mutableMapOf()

    override fun getString(key: String?, defValue: String?): String? = defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues

    override fun getInt(key: String?, defValue: Int): Int = defValue

    override fun getLong(key: String?, defValue: Long): Long = defValue

    override fun getFloat(key: String?, defValue: Float): Float = defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue

    override fun contains(key: String?): Boolean = false

    override fun edit(): SharedPreferences.Editor = error("unused")

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit
}
