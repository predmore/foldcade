package app.foldcade

import app.foldcade.api.plugin.PlayerExtra
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.plugins.retroarch.Core
import app.foldcade.plugins.retroarch.RETRO_ACTIVITY
import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RetroArchCoreDownloadTest {
    @get:Rule val temp = TemporaryFolder()
    private val core = Core("gambatte", "Gambatte")
    private val abi = RetroArchAbi.Arm64
    private fun elf(abi: RetroArchAbi = this.abi) = ByteArray(128).apply {
        byteArrayOf(0x7f, 69, 76, 70, abi.elfClass.toByte(), 1, 1).copyInto(this)
        this[16] = 3; this[18] = abi.machine.toByte()
    }
    private fun archive(name: String = core.fileName, bytes: ByteArray = elf()): File = temp.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(name).apply {
                method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = size
                crc = CRC32().apply { update(bytes) }.value
            })
            zip.write(bytes); zip.closeEntry()
        }
    }
    private fun rejects(file: File, abi: RetroArchAbi = this.abi) = runBlocking {
        try { extractCore(file, temp.newFile(), core, abi); fail("Invalid core accepted") } catch (_: IOException) { }
    }
    @Test fun aValidArchiveExtractsExactlyTheCore() = runBlocking {
        val output = temp.newFile()
        extractCore(archive(), output, core, abi)
        assertArrayEquals(elf(), output.readBytes())
    }
    @Test fun wrongNamesTraversalAndWrongArchitecturesAreRefused() {
        rejects(archive("../${core.fileName}"))
        rejects(archive("other.so"))
        rejects(archive(bytes = elf(RetroArchAbi.Arm32)))
        rejects(archive(bytes = elf().apply { this[16] = 2 }))
        rejects(archive(bytes = ByteArray(64)))
        rejects(archive(bytes = ByteArray(12)))
    }
    @Test fun crcDamageAndTruncationAreRefused() {
        val damaged = archive()
        val bytes = damaged.readBytes()
        bytes[30 + core.fileName.length + 80] = 123 // payload, preserving the ELF header
        damaged.writeBytes(bytes)
        rejects(damaged)
        val truncated = archive()
        truncated.writeBytes(truncated.readBytes().dropLast(16).toByteArray())
        rejects(truncated)
    }
    @Test fun theArchiveMustHaveExactlyOneEntry() {
        val file = temp.newFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            listOf(core.fileName, "extra").forEach { name -> zip.putNextEntry(ZipEntry(name)); zip.write(elf()); zip.closeEntry() }
        }
        rejects(file)
    }
    @Test fun abiTracksRetroArchRatherThanTheLauncher() {
        assertEquals(abi, RetroArchAbi.fromLibraryDirectory("/data/app/pkg/lib/arm64"))
        assertEquals(RetroArchAbi.Arm32, RetroArchAbi.fromLibraryDirectory("/data/app/pkg/lib/arm"))
        assertNull(RetroArchAbi.fromLibraryDirectory("/data/app/pkg/lib/x86_64"))
        assertTrue(coreDownloadUrl(core, abi).endsWith("/arm64-v8a/gambatte_libretro_android.so.zip"))
        assertTrue(validCoreElf(elf(RetroArchAbi.Arm32), RetroArchAbi.Arm32))
    }
    @Test fun sideloadKeepsTheRomAndOnlyChangesThePreparedCoreInItsPackage() {
        val prepared = PreparedRetroArchCore("com.retroarch.aarch64", core, File("/storage/emulated/0/Android/media/app.foldcade/cores/${core.fileName}"))
        val intent = PlayerIntent(packageName = prepared.packageName, componentClass = RETRO_ACTIVITY, action = "android.intent.action.MAIN",
            extras = listOf(PlayerExtra.Text("LIBRETRO", "/data/user/0/${prepared.packageName}/cores/${core.fileName}"), PlayerExtra.Text("ROM", "/storage/emulated/0/test.gb")))
        val sideload = prepared.sideload(intent)
        assertEquals(CORE_SIDELOAD_ACTIVITY, sideload.componentClass)
        assertTrue(PlayerExtra.Text("LIBRETRO", prepared.file.absolutePath) in sideload.extras)
        assertTrue(PlayerExtra.Text("ROM", "/storage/emulated/0/test.gb") in sideload.extras)
        assertEquals(intent, prepared.installedIntent(sideload))
        assertEquals(intent.copy(packageName = "other"), prepared.sideload(intent.copy(packageName = "other")))
        val otherCore = intent.copy(extras = listOf(PlayerExtra.Text("LIBRETRO", "/cores/mgba_libretro_android.so")))
        assertEquals(otherCore, prepared.sideload(otherCore))
    }
}
