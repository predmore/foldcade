package app.foldcade

import app.foldcade.api.plugin.Player
import app.foldcade.language.Evidence
import app.foldcade.language.EvidenceKind
import app.foldcade.language.IncidentKind
import app.foldcade.language.ProbeSnapshot
import app.foldcade.language.RootVerdict
import app.foldcade.plugins.aps3e.Aps3ePlayer
import app.foldcade.plugins.dolphin.DolphinPlayer
import java.net.URI

/** The supported providers use different document IDs. An authority or provider descendant alone
 * does not prove the data root. aPS3e publishes the exact path of getExternalFilesDir("aps3e"). */
internal fun providerSaveRootPackage(player: Player, uri: String): String? = runCatching {
    val parsed = URI(uri)
    if (parsed.scheme != "content") return null
    val id = folderDocumentId(uri).trimEnd('/')
    when (player) {
        is Aps3ePlayer -> player.packageNames.firstOrNull { name ->
            parsed.authority == "$name.provider" &&
                Regex("/storage/emulated/[0-9]+/Android/data/${Regex.escape(name)}/files/aps3e").matches(id)
        }
        is DolphinPlayer -> player.packageNames.single().takeIf { parsed.authority == DolphinPlayer.PROVIDER && id == "root" }
        else -> null
    }
}.getOrNull()

/** Kept separate from config and session writes, so rechecking a root cannot erase a card incident. */
internal fun providerSaveRootVerdict(player: Player, snapshot: ProbeSnapshot, owner: String?,
    verifiedProviderPackage: String?, installChanged: Boolean): RootVerdict {
    val bound = snapshot.roots.single { it.bound }
    val evidence = Evidence(EvidenceKind.OwnProvider, "${player.displayName}’s own folder")
    return when {
        owner == null -> RootVerdict.NoNews
        installChanged || snapshot.installFirstMillis != null && snapshot.packageName != owner ->
            RootVerdict.Lost(IncidentKind.LostAccess, evidence.copy(detail = "Allow ${player.displayName}’s folder for this installation."))
        snapshot.installFirstMillis != null && verifiedProviderPackage == owner && bound.reachable ->
            RootVerdict.Uses(bound.root, evidence, snapshot.atMillis)
        else -> RootVerdict.NoNews
    }
}
