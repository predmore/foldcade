package app.foldcade.plugins.sample

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameMeta
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.GamePage
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.ListedPlatform
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
 * In-tree sample. It implements the four interfaces and does nothing else.
 * Library and metadata are separate objects so the host can store them in separate slots.
 *
 * A plugin Foldcade loads in-process is part of the GPLv3 work when distributed.
 * Community plugins use this same entry point and those same license terms.
 */
class SampleEntry : PluginEntry {
    override val apiVersion = PLUGIN_API_VERSION
    override val platforms: List<Platform> = listOf(SamplePlatform())
    override val players: List<Player> = listOf(SamplePlayer())
    override val libraries: List<LibraryBackend> = listOf(SampleLibrary())
    override val metadataProviders: List<MetadataProvider> = listOf(SampleMetadata())
}

private class SamplePlatform : Platform {
    override val id = "sample.platform"
    override val displayName = "Sample"
    override val extensions = setOf("sample")
}

private class SamplePlayer : Player {
    override val id = "sample.player"
    override val displayName = "Sample player"
    override val platformId = "sample.platform"
    override val packageNames = listOf("app.foldcade.sample")
    override val needsLocalFile = true
    override val startDisplay = StartDisplay.PickerChoice
    override val occupiesBothDisplays = false
    override val requiresImportedGame = false

    override fun saveDeclarations(game: Game): List<SaveDeclaration> = emptyList()

    override fun launchIntent(request: LaunchRequest): PlayerIntent = PlayerIntent(
        packageName = request.resolvedPackage,
        componentClass = "app.foldcade.sample.PlayActivity",
        action = "app.foldcade.sample.PLAY",
        dataUri = (request.target as? LaunchTarget.ContentUri)?.uri,
        grantReadUri = request.target is LaunchTarget.ContentUri,
    )
}

private class SampleLibrary : LibraryBackend {
    override val id = "sample.library"
    override val displayName = "Sample library"

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

    override suspend fun ensureLocal(game: Game): LaunchTarget {
        cancellable()
        return LaunchTarget.ContentUri(uri = game.remoteKey)
    }

    override suspend fun saves(game: Game): SaveSet {
        cancellable()
        return SaveSet(ownerBackendId = id, slots = emptyList())
    }

    override suspend fun prepareLaunch(game: Game, player: Player): Placement {
        cancellable()
        return Placement(target = LaunchTarget.ContentUri(uri = game.remoteKey))
    }

    override suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves): SyncResult {
        cancellable()
        return SyncResult(SyncOutcome.Unchanged)
    }
}

private class SampleMetadata : MetadataProvider {
    override val id = "sample.metadata"
    override val displayName = "Sample metadata"

    override fun cached(game: Game): GameMeta? = null

    override suspend fun fetch(game: Game): GameMeta? {
        cancellable()
        return GameMeta(title = game.label)
    }
}

private suspend fun cancellable() {
    coroutineContext.ensureActive()
    yield()
}
