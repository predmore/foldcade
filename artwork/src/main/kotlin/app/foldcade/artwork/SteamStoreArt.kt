package app.foldcade.artwork

/**
 * A Steam game's library art from Steam's public CDN. No key is needed.
 * The portrait library capsule is the cover and the library hero is the background.
 * Older apps without a library capsule are left to the next source.
 */
class SteamStoreArt(private val http: ArtHttp) : ArtSource {
    override val id: String = "steam"

    override fun handles(query: ArtQuery): Boolean = query.steamAppId != null

    override suspend fun find(query: ArtQuery): ArtSet? {
        val appId = query.steamAppId ?: return null
        val base = "https://$HOST/store_item_assets/steam/apps/$appId"
        val cover = listOf("$base/library_600x900_2x.jpg", "$base/library_600x900.jpg")
            .firstOrNull { http.status(it) == 200 }
            ?: return null
        return ArtSet(source = id, cover = cover, background = "$base/library_hero.jpg")
    }

    /** The library hero behind the game, and its logo on a clear background. */
    override suspend fun scene(query: ArtQuery): ArtScene? {
        val appId = query.steamAppId ?: return null
        val base = "https://$HOST/store_item_assets/steam/apps/$appId"
        val background = "$base/library_hero.jpg".takeIf { http.status(it) == 200 }
        val logo = "$base/logo.png".takeIf { http.status(it) == 200 }
        return ArtScene(background = background, logo = logo).takeUnless { it.isEmpty }
    }

    companion object {
        const val HOST: String = "shared.steamstatic.com"
    }
}
