package app.foldcade

import androidx.compose.ui.graphics.toArgb
import app.foldcade.language.Theme
import app.foldcade.language.ThemeStyle
import app.foldcade.language.builtInTheme
import app.foldcade.language.readThemeZip
import app.foldcade.ui.FocusRing
import java.io.InputStream

/** The same paint and resolved corners the shell draws, without fonts, assets or file names. */
internal fun apkPluginAppearance(theme: Theme): BrokerReply {
    val ring = FocusRing(theme)
    return BrokerReply.success(
        "background" to theme.background.toArgb(),
        "surface" to theme.surface.toArgb(),
        "onBackground" to theme.onBackground.toArgb(),
        "muted" to theme.muted.toArgb(),
        "focus" to theme.focus.toArgb(),
        "focusRingStart" to ring.start.toArgb(),
        "focusRingEnd" to ring.end.toArgb(),
        "attention" to theme.style.colors.attention.toArgb(),
        "round" to theme.style.shape.round.coerceIn(0f, .5f),
        "cardCornerDp" to theme.style.metrics.cardCornerDp.coerceIn(0f, 64f),
    )
}

/** Resolve the bundled Afterglow archive's paint without creating native resources or caches. */
internal fun readApkPluginAppearance(input: InputStream): BrokerReply? = input.use {
    readThemeZip(it)?.use { zip ->
        val file = zip.file
        apkPluginAppearance(builtInTheme().copy(
            background = file.background, surface = file.surface, onBackground = file.onBackground,
            muted = file.muted, focus = file.focus, style = ThemeStyle(file.style),
        ))
    }
}
