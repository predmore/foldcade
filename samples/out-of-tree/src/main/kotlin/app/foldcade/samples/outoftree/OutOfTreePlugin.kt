package app.foldcade.samples.outoftree

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameHandoff
import app.foldcade.api.plugin.GameMeta
import app.foldcade.api.plugin.GamePage
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.ListedPlatform
import app.foldcade.api.plugin.LocalCopy
import app.foldcade.api.plugin.MetadataProvider
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.Placement
import app.foldcade.api.plugin.Platform
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.SaveSet
import app.foldcade.api.plugin.StartDisplay
import app.foldcade.api.plugin.SyncOutcome
import app.foldcade.api.plugin.SyncResult
import app.foldcade.host.PluginEntry
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

/**
 * Out-of-tree sample. The host does not compile against this module.
 * It is loaded from its jar through [PluginEntry], the same entry an external plugin uses.
 * Library and metadata are separate objects in separate slots.
 *
 * A plugin Foldcade loads in-process is part of the GPLv3 work when distributed.
 */
class OutOfTreeEntry : PluginEntry {
    override val platforms: List<Platform> = listOf(OutOfTreePlatform())
    override val players: List<Player> = listOf(OutOfTreePlayer())
    override val libraries: List<LibraryBackend> = listOf(OutOfTreeLibrary())
    override val metadataProviders: List<MetadataProvider> = listOf(OutOfTreeMetadata())
}

private class OutOfTreePlatform : Platform {
    override val id = "outoftree.platform"
    override val displayName = "Out of tree"
    override val extensions = emptySet<String>()
}

private class OutOfTreePlayer : Player {
    override val id = "outoftree.player"
    override val displayName = "Out-of-tree player"
    override val platformId = "outoftree.platform"
    override val packageNames = listOf("app.foldcade.outoftree")
    override val handoff = GameHandoff.ContentUri
    override val startDisplay = StartDisplay.PickerChoice
    override val occupiesBothDisplays = false
    override val requiresImportedGame = false

    override fun saveDeclarations(game: Game): List<SaveDeclaration> = emptyList()

    override fun launchIntent(request: LaunchRequest): PlayerIntent = PlayerIntent(
        packageName = request.resolvedPackage,
        componentClass = "app.foldcade.outoftree.PlayActivity",
        action = "app.foldcade.outoftree.PLAY",
        dataUri = request.local.contentUri,
        grantReadUri = false,
    )
}

private class OutOfTreeLibrary : LibraryBackend {
    override val id = "outoftree.library"
    override val displayName = "Out-of-tree library"

    override suspend fun connect() = cancellable()

    override suspend fun disconnect() = cancellable()

    override suspend fun listPlatforms(): List<ListedPlatform> {
        cancellable()
        return emptyList()
    }

    override suspend fun listGames(platformId: String, query: GameQuery): GamePage {
        cancellable()
        return GamePage(games = emptyList(), nextOffset = null)
    }

    override suspend fun ensureLocal(game: Game): LocalCopy {
        cancellable()
        return LocalCopy(contentUri = game.remoteKey)
    }

    override suspend fun saves(game: Game): SaveSet {
        cancellable()
        return SaveSet(ownerBackendId = id, slots = emptyList())
    }

    override suspend fun prepareLaunch(game: Game, player: Player): Placement {
        cancellable()
        return Placement(local = LocalCopy(contentUri = game.remoteKey))
    }

    override suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves): SyncResult {
        cancellable()
        return SyncResult(SyncOutcome.Unchanged)
    }
}

private class OutOfTreeMetadata : MetadataProvider {
    override val id = "outoftree.metadata"
    override val displayName = "Out-of-tree metadata"

    override fun cached(game: Game): GameMeta? = null

    override suspend fun fetch(game: Game): GameMeta {
        cancellable()
        return GameMeta(title = game.label)
    }
}

private suspend fun cancellable() {
    coroutineContext.ensureActive()
    yield()
}
