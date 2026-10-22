package app.foldcade.plugins.moonlight

import app.foldcade.api.plugin.Artwork
import app.foldcade.api.plugin.ArtworkRole
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameMeta
import app.foldcade.api.plugin.MetadataProvider
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * The box art Moonlight already has for a game.
 *
 * Read from moonlight-android `b48494cb96bff23d8886c4775cc4f39a1075495d`:
 * Moonlight keeps each game's box art from the host once its game list has
 * shown it. `PosterContentProvider` is exported with no permission, at
 * `content://poster.com.limelight/boxart/<host UUID>/<app id>`, read-only.
 * The UUID is matched as Moonlight stored it, so its case is kept.
 *
 * A launcher cannot list that provider, so [poster] asks for one game. The
 * app sets it, and may answer with box art it got from a paired computer
 * instead. Until it is set, no game has a poster.
 */
class MoonlightArt(private val library: MoonlightLibrary) : MetadataProvider {
    override val id: String = ID
    override val displayName: String = MoonlightLibrary.DISPLAY_NAME

    /** What is known of [MoonlightApp]'s box art. Off the main thread. */
    @Volatile
    var poster: suspend (MoonlightApp) -> Poster = { Poster.None }

    /** Each game asked about, by remote key: its poster address, or [NONE]. */
    private val found = ConcurrentHashMap<String, String>()

    override fun cached(game: Game): GameMeta? {
        if (game.backendId != MoonlightLibrary.ID) return null
        return found[game.remoteKey]?.takeIf { it != NONE }?.let { meta(game, it) }
    }

    override suspend fun fetch(game: Game): GameMeta? {
        if (game.backendId != MoonlightLibrary.ID) return null
        found[game.remoteKey]?.let { return if (it == NONE) null else meta(game, it) }
        val app = library.app(game.remoteKey) ?: return null
        val answer = poster(app)
        coroutineContext.ensureActive()
        // Not yet is asked again; a poster or a definite none is kept.
        when (answer) {
            is Poster.At -> found[game.remoteKey] = answer.uri
            Poster.None -> found[game.remoteKey] = NONE
            Poster.NotYet -> Unit
        }
        return (answer as? Poster.At)?.let { meta(game, it.uri) }
    }

    /** Asks again for every game of the computer [hostKey], a lower-case UUID. */
    fun forgetHost(hostKey: String) {
        found.keys.removeIf { it.startsWith("$hostKey|") }
    }

    private fun meta(game: Game, uri: String) = GameMeta(
        title = game.label,
        artwork = listOf(Artwork(ArtworkRole.Cover, uri)),
    )

    companion object {
        const val ID: String = "moonlight"
        private const val NONE = ""
    }
}

/** What the app knows of one game's box art. */
sealed interface Poster {
    data class At(val uri: String) : Poster

    /** There is none to find. */
    data object None : Poster

    /** There may be, once its computer answers. */
    data object NotYet : Poster
}

/** Moonlight's poster for one game. [hostUuid] is as Moonlight stored it. */
fun moonlightPosterUri(hostUuid: String, appId: String): String =
    "content://$POSTER_AUTHORITY/boxart/$hostUuid/$appId"

/** True for an address [moonlightPosterUri] made: the only content Foldcade reads from Moonlight. */
fun isMoonlightPosterUri(uri: String): Boolean = POSTER_URI.matches(uri)

/** `PosterContentProvider`'s authority for the official client, `poster.` and the application id. */
const val POSTER_AUTHORITY: String = "poster.${MoonlightPlayer.OFFICIAL_PACKAGE}"

private val POSTER_URI = Regex(
    "content://" + Regex.escape(POSTER_AUTHORITY) +
        "/boxart/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/[0-9]{1,10}",
)
