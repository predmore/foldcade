package app.foldcade.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt

/**
 * An app's launcher icon. A [fullBleed] one is an adaptive icon's layers with no mask,
 * square to its edges, so whatever shows it gives it the tile's rounded corner.
 */
internal data class LauncherArt(val image: ImageBitmap, val fullBleed: Boolean)

@Composable
internal fun launcherIcon(packageName: String?): LauncherArt? {
    val context = LocalContext.current
    return remember(packageName) {
        if (packageName == null) return@remember null
        val drawable = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
            ?: return@remember null
        runCatching { launcherArt(drawable) }.getOrNull()
    }
}

private fun launcherArt(drawable: Drawable): LauncherArt {
    if (drawable !is AdaptiveIconDrawable) return LauncherArt(drawable.toBitmap().asImageBitmap(), fullBleed = false)
    // As big as a tile's cover, so it stays sharp filling the tile.
    val bitmap = Bitmap.createBitmap(TILE_PX, TILE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val (from, to) = bleedBounds(TILE_PX)
    // Background, then foreground, each bigger than the bitmap, so only the part a launcher shows lands in it.
    for (layer in listOf(drawable.background, drawable.foreground)) {
        layer ?: continue
        layer.setBounds(from, from, to, to)
        layer.draw(canvas)
    }
    return LauncherArt(bitmap.asImageBitmap(), fullBleed = true)
}

/**
 * Where an adaptive layer goes to fill [px] square: half as big again, centred. A layer
 * is 108dp and launchers show its middle 72dp, so that middle lands exactly on the bitmap.
 */
internal fun bleedBounds(px: Int): Pair<Int, Int> {
    val spill = (px * 0.25f).roundToInt()
    return -spill to px + spill
}

/**
 * An app's icon away from the grid. A full-bleed icon takes the largest square that
 * fits and the tile's corner, so it never shows as a raw square; a legacy icon draws as it is.
 */
@Composable
internal fun LauncherImage(art: LauncherArt, contentDescription: String?, modifier: Modifier = Modifier) {
    if (!art.fullBleed) {
        Image(bitmap = art.image, contentDescription = contentDescription, modifier = modifier, contentScale = ContentScale.Fit)
        return
    }
    val radius = LocalFoldTheme.current.theme.iconRadius
    Image(
        bitmap = art.image,
        contentDescription = contentDescription,
        modifier = modifier
            .wrapContentSize()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(percent = (radius * 100).roundToInt())),
        contentScale = ContentScale.Crop,
    )
}
