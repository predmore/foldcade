package app.foldcade.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.foldcade.FoldcadeApp
import app.foldcade.language.FolderGuide
import app.foldcade.language.SaveSetupCopy
import app.foldcade.language.SettingsCopy

/** The free screen during an Android tree pick. There are no controls or stale page hints here. */
@Composable
internal fun FolderGuideScreen(app: FoldcadeApp, guide: FolderGuide) {
    val theme = LocalFoldTheme.current.theme
    val player = guide.playerId?.let(app.plugins::player)
    val name = player?.displayName ?: SettingsCopy.library
    val packageName = app.shell.model.playerSaves.firstOrNull { it.playerId == guide.playerId }?.packages?.firstOrNull()
        ?: player?.packageNames?.firstOrNull()
    Box(
        Modifier.fillMaxSize().background(theme.background.copy(alpha = 1f)).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.settingsCard(theme).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                val themed = appGlyph(packageName)
                val icon = if (themed == null) launcherIcon(packageName) else null
                when {
                    player == null -> markGlyph("folder")?.let { MarkIcon(it, 1f) }
                    themed != null -> MarkIcon(themed, 1f)
                    icon != null -> LauncherImage(icon, contentDescription = null, modifier = Modifier.fillMaxSize())
                    else -> SymbolIcon("emulators", 1f, theme.onBackground)
                }
            }
            BasicText(name, style = TextStyle(
                color = theme.onBackground, fontFamily = theme.font,
                fontSize = theme.style.type.sideRow, fontWeight = theme.style.type.strongWeight,
                textAlign = TextAlign.Center,
            ))
            BasicText(
                if (guide.opensThere || player == null) SaveSetupCopy.useThisFolder else SaveSetupCopy.browse(name),
                style = TextStyle(color = theme.muted, fontFamily = theme.font, fontSize = theme.style.type.dialogBody, textAlign = TextAlign.Center),
            )
        }
    }
}
