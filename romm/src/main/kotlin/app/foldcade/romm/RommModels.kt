package app.foldcade.romm

import java.nio.file.Path
import java.time.Instant
import kotlinx.serialization.Serializable

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

@Serializable
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

@Serializable
data class RomFileSummary(
    val id: Long,
    val fileName: String,
    val fileSizeBytes: Long,
    /** Lowercase hex MD5 RomM computed for this file, when it has one. */
    val md5Hash: String? = null,
    /** `category`, such as `game`, `update`, or `dlc`. Null when RomM gave the file none. */
    val category: String? = null,
    /** `is_top_level`: the file sits in the ROM's folder itself, not a folder inside it. Null when not sent. */
    val isTopLevel: Boolean? = null,
)

@Serializable
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
    /** `ra_id`: the game's RetroAchievements id. Null when RomM has no match for it. */
    val raId: Long? = null,
    /** `merged_ra_metadata.achievements`, in `display_order`. Empty without a match. */
    val achievements: List<RaAchievement> = emptyList(),
)

/**
 * One RetroAchievements achievement as RomM lists it. Every field but
 * [badgeId] can be missing. [badgePath] and [badgePathLock] are on the RomM
 * server and load without the token; the urls are on RetroAchievements' own host.
 */
@Serializable
data class RaAchievement(
    val title: String?,
    val description: String?,
    val points: Int?,
    /** Joins this achievement to [RaGameProgress.earnedBadgeIds]. */
    val badgeId: String?,
    val badgePath: String?,
    val badgePathLock: String?,
    val badgeUrl: String?,
    val badgeUrlLock: String?,
    val displayOrder: Int?,
)

/**
 * `GET /api/users/me`, for RetroAchievements. [raUsername] is null until the
 * user links one in RomM. [progress] is what RomM last fetched for them.
 */
data class RommUser(
    val raUsername: String?,
    val progress: List<RaGameProgress>,
)

/** One game in `ra_progression.results`. Joined to a ROM on [RomSummary.raId]. */
data class RaGameProgress(
    val romRaId: Long,
    val maxPossible: Int?,
    val numAwarded: Int?,
    val numAwardedHardcore: Int?,
    /** `earned_achievements[].id`: the badge ids of what the user has earned. */
    val earnedBadgeIds: Set<String>,
)

data class RomPage(
    val items: List<RomSummary>,
    val total: Long?,
    val limit: Int,
    val offset: Int,
)

/** A title and its cover candidates from `GET /api/search/cover`, independent of the library. */
data class RomCoverMatch(
    val name: String,
    val resources: List<RomCoverResource>,
)

data class RomCoverResource(
    val url: String,
    val type: String,
    val width: Int,
    val height: Int,
    val nsfw: Boolean,
    val humor: Boolean,
    val epilepsy: Boolean,
)

/** Where [RommClient.downloadRom] is. Reported on its IO thread. */
sealed interface RomProgress {
    /**
     * [bytes] of the ROM are on the device, a resumed part included. [total]
     * is null when neither RomM's file list nor the response gives a size.
     */
    data class Receiving(val bytes: Long, val total: Long?) : RomProgress

    /** Every byte is here, and its size and hash are being checked. */
    data object Checking : RomProgress
}

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

/**
 * [archivedSaveId] is null when the same local bytes were already archived by an earlier try.
 * [localCopy] and [remoteCopy] are preserved bytes for the shell's choice. Null when a copy
 * could not be written. Neither is the slot file, so choosing one does not delete the other.
 */
data class KeptBoth(
    val romId: Long,
    val slot: String?,
    val archivedSaveId: Long?,
    val serverSaveId: Long,
    val serverFile: Path,
    val localEmulator: String? = null,
    val localUpdatedAtMillis: Long = 0L,
    val localSizeBytes: Long = 0L,
    val remoteEmulator: String? = null,
    val remoteUpdatedAtMillis: Long = 0L,
    val remoteSizeBytes: Long = 0L,
    val localCopy: Path? = null,
    val remoteCopy: Path? = null,
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

