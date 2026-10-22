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
 * A launcher cannot list that provider, so [posterExists] asks for one
 * address. The app sets it; until then no game has a poster.
 */
class MoonlightArt(private val library: MoonlightLibrary) : MetadataProvider {
    override val id: String = ID
    override val displayName: String = MoonlightLibrary.DISPLAY_NAME

    /** True when Moonlight serves an image at the address. Off the main thread. */
    @Volatile
    var posterExists: suspend (String) -> Boolean = { false }

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
        val uri = moonlightPosterUri(app.hostUuid, app.appId)
        val exists = posterExists(uri)
        coroutineContext.ensureActive()
        found[game.remoteKey] = if (exists) uri else NONE
        return if (exists) meta(game, uri) else null
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
