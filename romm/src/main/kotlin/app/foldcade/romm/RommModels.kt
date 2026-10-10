package app.foldcade.romm

import java.nio.file.Path
import java.time.Instant

data class Heartbeat(val version: String)

data class OpenApiInfo(val title: String?, val version: String, val major: Int)

data class DeviceAuthChallenge(
    val deviceCode: String,
    val userCode: String,
    val verificationPath: String,
    val verificationPathComplete: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
) {
    override fun toString(): String =
        "DeviceAuthChallenge(deviceCode=***, userCode=$userCode, verificationPath=$verificationPath, " +
            "verificationPathComplete=$verificationPathComplete, expiresInSeconds=$expiresInSeconds, " +
            "intervalSeconds=$intervalSeconds)"
}

sealed class DeviceTokenPoll {
    data object Pending : DeviceTokenPoll()
    data object SlowDown : DeviceTokenPoll()
    data object Denied : DeviceTokenPoll()
    data object Expired : DeviceTokenPoll()

    data class Approved(
        val accessToken: String,
        val deviceId: String,
        val scopes: List<String>,
        val expiresAt: String?,
    ) : DeviceTokenPoll() {
        override fun toString(): String =
            "Approved(accessToken=***, deviceId=$deviceId, scopes=$scopes, expiresAt=$expiresAt)"
    }
}

data class PlatformSummary(
    val id: Long,
    val name: String,
    val displayName: String,
    val slug: String,
    val fsSlug: String,
    val abbreviation: String,
    val alternativeNames: List<String>,
    val romCount: Long,
)

data class RomQuery(
    val platformIds: List<Long> = emptyList(),
    val searchTerm: String? = null,
    val limit: Int = 50,
    val offset: Int = 0,
)

data class RomFileSummary(
    val id: Long,
    val fileName: String,
    val fileSizeBytes: Long,
    /** Lowercase hex MD5 RomM computed for this file, when it has one. */
    val md5Hash: String? = null,
)

data class RomSummary(
    val id: Long,
    val name: String?,
    val fsName: String,
    val fsExtension: String,
    val fsSizeBytes: Long,
    val platformId: Long,
    val platformSlug: String,
    val platformFsSlug: String,
    val platformDisplayName: String,
    val alternativeNames: List<String>,
    val pathCoverSmall: String?,
    val pathCoverLarge: String?,
    val urlCover: String?,
    val hasSimpleSingleFile: Boolean,
    val hasNestedSingleFile: Boolean,
    val hasMultipleFiles: Boolean,
    val files: List<RomFileSummary>,
    /** `merged_screenshots`: screenshot paths on the server, from its scrapers. */
    val screenshots: List<String> = emptyList(),
)

data class RomPage(
    val items: List<RomSummary>,
    val total: Long?,
    val limit: Int,
    val offset: Int,
)

data class RegisteredDevice(
    val deviceId: String,
    val clientVersion: String,
)

/** A save file this device holds. [slot] null is an archive and is not paired. */
data class LocalSave(
    val romId: Long,
    val fileName: String,
    val slot: String?,
    val emulator: String?,
    val updatedAt: Instant,
    val file: Path,
)

data class SyncOperation(
    val action: String,
    val romId: Long,
    val saveId: Long?,
    val fileName: String,
    val slot: String?,
    val emulator: String?,
    val reason: String,
    val serverUpdatedAt: String?,
    val serverContentHash: String?,
)

data class NegotiateResult(
    val sessionId: Long,
    val operations: List<SyncOperation>,
    val totalUpload: Int,
    val totalDownload: Int,
    val totalConflict: Int,
    val totalNoOp: Int,
    val totalDelete: Int,
    val serverVersion: String,
)

/** [archivedSaveId] is null when the same local bytes were already archived by an earlier try. */
data class KeptBoth(
    val romId: Long,
    val slot: String?,
    val archivedSaveId: Long?,
    val serverSaveId: Long,
    val serverFile: Path,
)

data class SaveSyncReport(
    val sessionId: Long,
    val serverVersion: String,
    val downloaded: List<Path>,
    val uploadedSaveIds: List<Long>,
    val keptBoth: List<KeptBoth>,
    val deleted: List<Path>,
    val queued: List<String>,
    val failed: List<String>,
    val completedSession: Boolean,
)

data class CompletedSync(val sessionId: Long, val status: String?)

