package app.foldcade.plugins.romm

import app.foldcade.romm.DeviceAuthChallenge
import app.foldcade.romm.DeviceTokenPoll
import app.foldcade.romm.Heartbeat
import app.foldcade.romm.LocalSave
import app.foldcade.romm.PlatformSummary
import app.foldcade.romm.RegisteredDevice
import app.foldcade.romm.RomPage
import app.foldcade.romm.RomProgress
import app.foldcade.romm.RomQuery
import app.foldcade.romm.RomSummary
import app.foldcade.romm.RomCoverMatch
import app.foldcade.romm.RommClient
import app.foldcade.romm.RommUser
import app.foldcade.romm.SaveSyncReport
import app.foldcade.romm.SaveLedger
import app.foldcade.romm.SyncOperation
import java.nio.file.Path

/** The calls this plugin makes. Every one is already on [RommClient]. */
internal interface RommOps : AutoCloseable {
    suspend fun heartbeat(): Heartbeat

    suspend fun platforms(): List<PlatformSummary>

    suspend fun roms(query: RomQuery): RomPage

    suspend fun rom(id: Long): RomSummary

    suspend fun me(): RommUser

    suspend fun searchCovers(title: String): List<RomCoverMatch>

    suspend fun downloadRom(
        romId: Long,
        fileName: String,
        cacheRoot: Path,
        fileIds: List<Long>,
        expectedSize: Long?,
        expectedMd5: String?,
        progress: ((RomProgress) -> Unit)? = null,
        /** Where the file goes. Null is [RommClient.romCacheFile]. */
        target: Path? = null,
    ): Path

    suspend fun registerDevice(
        stored: RegisteredDevice?,
        name: String,
        clientVersion: String,
        hostname: String?,
    ): RegisteredDevice

    suspend fun syncSaves(
        deviceId: String,
        saves: List<LocalSave>,
        romIds: List<Long>,
        emulators: List<String>?,
        destination: (SyncOperation) -> Path,
        ledger: SaveLedger,
    ): SaveSyncReport

    suspend fun beginDeviceAuth(
        clientDeviceIdentifier: String,
        name: String,
        clientVersion: String,
    ): DeviceAuthChallenge

    suspend fun pollDeviceToken(deviceCode: String): DeviceTokenPoll

    fun verificationUrl(challenge: DeviceAuthChallenge): String

    suspend fun artwork(uri: String): ByteArray
}

internal class ClientRommOps(private val client: RommClient) : RommOps {
    override suspend fun heartbeat() = client.heartbeat()

    override suspend fun platforms() = client.platforms()

    override suspend fun roms(query: RomQuery) = client.roms(query)

    override suspend fun rom(id: Long) = client.rom(id)

    override suspend fun me() = client.me()

    override suspend fun searchCovers(title: String) = client.searchCovers(title)

    override suspend fun downloadRom(
        romId: Long,
        fileName: String,
        cacheRoot: Path,
        fileIds: List<Long>,
        expectedSize: Long?,
        expectedMd5: String?,
        progress: ((RomProgress) -> Unit)?,
        target: Path?,
    ) = if (target == null) {
        client.downloadRom(romId, fileName, cacheRoot, fileIds, expectedSize, expectedMd5, progress)
    } else {
        client.downloadRom(romId, fileName, cacheRoot, fileIds, expectedSize, expectedMd5, progress, target)
    }

    override suspend fun registerDevice(
        stored: RegisteredDevice?,
        name: String,
        clientVersion: String,
        hostname: String?,
    ) = client.registerDevice(stored, name, clientVersion, hostname)

    override suspend fun syncSaves(
        deviceId: String,
        saves: List<LocalSave>,
        romIds: List<Long>,
        emulators: List<String>?,
        destination: (SyncOperation) -> Path,
        ledger: SaveLedger,
    ) = client.syncSaves(deviceId, saves, romIds, emulators, destination, ledger)

    override suspend fun beginDeviceAuth(clientDeviceIdentifier: String, name: String, clientVersion: String) =
        client.beginDeviceAuth(clientDeviceIdentifier, name, clientVersion)

    override suspend fun pollDeviceToken(deviceCode: String) = client.pollDeviceToken(deviceCode)

    override fun verificationUrl(challenge: DeviceAuthChallenge) = client.verificationUrl(challenge)

    override suspend fun artwork(uri: String) = client.artwork(uri)

    override fun close() = client.close()
}
