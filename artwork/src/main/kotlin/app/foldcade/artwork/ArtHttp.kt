package app.foldcade.artwork

import app.foldcade.net.PublicHttps

/** The calls art sources make. [PublicArtHttp] in the app, a fake in tests. */
interface ArtHttp {
    /** Status and body. Throws when the host cannot be reached. */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): ArtResponse

    /** Status of a HEAD request. Throws when the host cannot be reached. */
    suspend fun status(url: String): Int
}

class ArtResponse(val status: Int, val body: ByteArray)

/** Every host an art source reads from. [PublicHttps] refuses any other. */
val ART_HOSTS: Set<String> = setOf(
    LibretroThumbnails.HOST,
    SteamStoreArt.HOST,
)

class PublicArtHttp(private val http: PublicHttps) : ArtHttp {
    override suspend fun get(url: String, headers: Map<String, String>): ArtResponse =
        http.get(url, headers).let { ArtResponse(it.status, it.body) }

    override suspend fun status(url: String): Int = http.status(url)
}
