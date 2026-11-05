package app.foldcade

import app.foldcade.artwork.ArtScene

/** Fixed images for one layout. Keeping them here also keeps them through cache eviction. */
internal data class HeroLayout<T>(
    val backdrop: T? = null,
    val wide: Boolean = false,
    val cover: T? = null,
    val logo: T? = null,
    val screen: T? = null,
)

internal enum class HeroImageRole { Backdrop, Ambient, Cover, Logo, Screen }

/** No part is published until every image this layout needs has decoded successfully. */
internal suspend fun <T> loadHeroLayout(
    cover: String?,
    scene: ArtScene?,
    decode: suspend (String, HeroImageRole) -> T?,
): HeroLayout<T>? {
    suspend fun image(uri: String?, role: HeroImageRole): T? = uri?.let { decode(it, role) }
    val wide = scene?.background
    val backdrop = image(wide ?: cover, if (wide != null) HeroImageRole.Backdrop else HeroImageRole.Ambient)
    val logo = if (wide != null) image(scene.logo, HeroImageRole.Logo) else null
    val box = if (logo == null) image(cover, HeroImageRole.Cover) else null
    val screen = if (wide == null) image(scene?.screen, HeroImageRole.Screen) else null
    if ((wide != null && backdrop == null) || (wide != null && scene.logo != null && logo == null) ||
        (cover != null && logo == null && (box == null || backdrop == null)) ||
        (wide == null && scene?.screen != null && screen == null)) return null
    return HeroLayout(backdrop, wide != null, box, logo, screen)
}
