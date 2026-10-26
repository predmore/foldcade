package app.foldcade.plugins.romm

import app.foldcade.romm.FirmwareSummary
import app.foldcade.romm.RomProgress
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

/**
 * Switch firmware on one signed-in RomM server.
 * [failed] means that server could not be asked. [files] is empty when it
 * answered and has nothing for Switch.
 */
data class RommSwitchFirmware(
    val origin: String,
    val files: List<FirmwareSummary>,
    val failed: Boolean,
)

/**
 * Switch keys and firmware from the configured RomM server.
 */
class RommFirmware(
    private val wiring: () -> RommWiring?,
) {
    suspend fun listSwitch(): RommSwitchFirmware? = withContext(Dispatchers.IO) {
        wiring()?.takeIf { it.origin.isNotBlank() }?.let { ask(it) }
    }

    suspend fun download(
        origin: String,
        file: FirmwareSummary,
        target: Path,
        progress: ((RomProgress) -> Unit)? = null,
    ): Path {
        val wiring = wiring()?.takeIf { it.origin == origin }
            ?: throw IllegalStateException("RomM server is not connected")
        val token = token(wiring) ?: throw IllegalStateException("Sign in to RomM")
        val ops = wiring.open(wiring.origin) { token }
        try {
            return ops.downloadFirmware(file, target, progress)
        } finally {
            ops.close()
        }
    }

    private suspend fun ask(wiring: RommWiring): RommSwitchFirmware {
        val ops = try {
            val token = token(wiring) ?: return RommSwitchFirmware(wiring.origin, emptyList(), failed = true)
            wiring.open(wiring.origin) { token }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return RommSwitchFirmware(wiring.origin, emptyList(), failed = true)
        }
        try {
            val platform = ops.platforms().firstOrNull { platform ->
                platform.slug.equals(SWITCH, ignoreCase = true) || platform.fsSlug.equals(SWITCH, ignoreCase = true)
            } ?: return RommSwitchFirmware(wiring.origin, emptyList(), failed = false)
            return RommSwitchFirmware(wiring.origin, ops.firmware(platform.id), failed = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return RommSwitchFirmware(wiring.origin, emptyList(), failed = true)
        } finally {
            ops.close()
        }
    }

    private suspend fun token(wiring: RommWiring): String? = runInterruptible(Dispatchers.IO) {
        wiring.tokenSource.accessToken()?.trim()?.removePrefix("Bearer ")?.trim()?.ifEmpty { null }
    }

    private companion object {
        const val SWITCH = "switch"
    }
}
