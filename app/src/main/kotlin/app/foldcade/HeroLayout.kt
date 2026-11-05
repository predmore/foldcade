package app.foldcade

import app.foldcade.artwork.ArtScene
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Fixed images for one layout. Keeping them here also keeps them through cache eviction. */
internal data class HeroLayout<T>(
    val backdrop: T? = null,
    val wide: Boolean = false,
    val cover: T? = null,
    val logo: T? = null,
    val screen: T? = null,
)

internal enum class HeroImageRole { Backdrop, Ambient, Cover, Logo, Screen }

/** Decode each complete candidate together; a failed part falls back to the best usable layout. */
internal suspend fun <T> loadHeroLayout(
    cover: String?,
    scene: ArtScene?,
    decode: suspend (String, HeroImageRole) -> T?,
): HeroLayout<T>? = coroutineScope {
    suspend fun image(uri: String?, role: HeroImageRole): T? = uri?.let { decode(it, role) }
    val wide = scene?.background
    if (wide != null) {
        val background = async { image(wide, HeroImageRole.Backdrop) }
        val logo = async { image(scene.logo, HeroImageRole.Logo) }
        val backdrop = background.await()
        val name = logo.await()
        if (backdrop != null) {
            val box = if (name == null) image(cover, HeroImageRole.Cover) else null
            return@coroutineScope HeroLayout(backdrop, wide = true, cover = box, logo = name)
        }
    }
    val ambient = async { image(cover, HeroImageRole.Ambient) }
    val box = async { image(cover, HeroImageRole.Cover) }
    val shot = async { image(scene?.screen, HeroImageRole.Screen) }
    val coverImage = box.await()
    val backdrop = ambient.await() ?: coverImage
    val screen = shot.await()
    // A screenshot alone is still the game's own art and can fill the cover's place.
    if (coverImage == null && backdrop == null && screen != null) {
        HeroLayout(backdrop = screen, cover = screen)
    } else if (backdrop != null || coverImage != null) {
        HeroLayout(backdrop, cover = coverImage ?: backdrop, screen = screen)
    } else {
        // Known images that failed are different from a subject that has no art.
        // Failure keeps a previous layout; an intentional no-art state can fade it out.
        HeroLayout<T>().takeIf { cover == null && (scene == null || scene.isEmpty) }
    }
}
