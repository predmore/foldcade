package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameFileReader
import app.foldcade.api.plugin.SaveFolderPart
import app.foldcade.language.ConfigSavePath
import app.foldcade.language.Evidence
import app.foldcade.language.EvidenceKind
import app.foldcade.language.ProbeSnapshot
import app.foldcade.language.RootVerdict
import app.foldcade.language.SaveIncident
import app.foldcade.language.SaveProtectionCopy
import app.foldcade.plugins.dolphin.DolphinCard
import app.foldcade.plugins.dolphin.DolphinPlayer
import app.foldcade.plugins.dolphin.GameCubeDisc

/** Config copied to shared storage cannot prove the active setting. The provider's backing
 * directory is opaque, so exclude paths that could be its own default card via either stock root. */
internal fun dolphinAlternate(card: DolphinCard?, disc: GameCubeDisc, ownProvider: Boolean): ConfigSavePath? {
    if (!ownProvider) return null
    val expected = disc.defaultCardPath
    val path = (card as? DolphinCard.Custom)?.path ?: return null
    val defaults = listOf(DolphinPlayer.LEGACY_FOLDER, "Android/data/org.dolphinemu.dolphinemu/files")
        .map { "$it/$expected" }
    if (defaults.any { it.equals(path, ignoreCase = true) }) return null
    return ConfigSavePath(expected, "primary:$path", null, proves = true)
}

internal fun dolphinConfigVerdict(snapshot: ProbeSnapshot): RootVerdict {
    val path = snapshot.configSavePath
    return if (path?.proves == true && path.effective != null && !path.matches)
        RootVerdict.UsesOther(null, Evidence(EvidenceKind.Config, SaveProtectionCopy.dolphinCustomCardDetail), snapshot.atMillis)
    else RootVerdict.NoNews
}

/** The game filter still applies on a configured custom folder, including folders shared by games. */
internal fun stageDolphinCard(saves: PlayerSaves, copies: SaveIncidentCopies, player: DolphinPlayer, game: Game,
    reader: GameFileReader, cardUri: String, incident: SaveIncident): Boolean {
    val filter = player.saveArchiveFilter(DolphinPlayer.SLOT, reader) ?: return false
    val spot = SaveSpot(DolphinPlayer.SLOT, cardUri, "gci.zip", listOf(SaveFolderPart("", "")), filter = filter)
    val bytes = saves.readSpot(spot, strict = true, limit = SAVE_ZIP_LIMIT)?.first ?: return true
    copies.stageBackup(game, player, spot, bytes, incident)
    return true
}
