package app.foldcade.plugins.eden

import app.foldcade.api.plugin.GameFileReader
import app.foldcade.api.plugin.SaveFolderAccess
import app.foldcade.api.plugin.SaveFolderEntry
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

class SwitchTitleTest {
    private val folder = Folder().apply {
        files["keys/prod.keys"] = "header_key = ${KEY.joinToString("") { "%02x".format(it) }}\n".toByteArray()
    }

    @Test fun normalizesBaseUpdateDlcAndMultiProgramIds() {
        for (id in listOf(BASE, "0100755017EE0800", "0100755017EE0003", "0100755017EE1001", "0100755017EE1802")) {
            assertEquals(id, BASE, applicationId(id))
        }
        for (id in listOf("0000755017EE0000", "0200755017EE0000", "0100755017EE000", "not a title id")) assertNull(applicationId(id))
    }

    @Test fun aTagWinsBeforeOpeningTheGameOrKeysAndAmbiguousTagsRefuse() {
        val unreadable = object : GameFileReader {
            override val fileName = "Fantasy [0100755017ee0800].nsp"
            override fun read(offset: Long, length: Int): ByteArray? = error("No read")
        }
        assertEquals(BASE, switchTitleId(unreadable, Folder()))
        assertNull(switchTitleId(reader(ticket(), "Game [$BASE] [$OTHER].nsp"), folder))
        assertEquals(BASE, switchTitleId(reader(ticket(), "Game [$BASE] [0100755017EE1001].nsp"), folder))
        assertEquals(BASE, switchTitleId(reader(ticket(), "Game [0200000000000000].nsp"), folder))
    }

    @Test fun nspAndNszTicketNamesNeedNoKeysAndSeveralBasesRefuse() {
        for (extension in listOf("nsp", "nsz")) {
            val bytes = partition(false, listOf("0100755017EE08000000000000000000.tik" to byteArrayOf(1)))
            assertEquals(BASE, switchTitleId(reader(bytes, "Game.$extension"), Folder()))
        }
        val same = partition(false, listOf(
            "${BASE}0000000000000000.tik" to byteArrayOf(1),
            "0100755017EE10010000000000000000.tik" to byteArrayOf(2),
        ))
        assertEquals(BASE, switchTitleId(reader(same), Folder()))
        val multi = partition(false, listOf("${BASE}0000000000000000.tik" to byteArrayOf(1), "${OTHER}0000000000000000.tik" to byteArrayOf(2)))
        assertNull(switchTitleId(reader(multi), folder))
    }

    @Test fun ticketlessNspAndNczDecryptWithTheHeaderKey() {
        for (extension in listOf("nca", "ncz")) {
            val bytes = partition(false, listOf("program.$extension" to nca("0100755017EE0800", 0), "meta.nca" to nca(BASE, 1, "NCA2")))
            assertEquals(BASE, switchTitleId(reader(bytes), folder))
            assertNull(switchTitleId(reader(bytes), Folder()))
        }
        val control = partition(false, listOf("control.nca" to nca(BASE, 2)))
        assertEquals(BASE, switchTitleId(reader(control), folder))
    }

    @Test fun xciAndXczReadOnlyTheSecurePartitionWithOrWithoutTheKeyArea() {
        for (keyArea in listOf(0, 0x1000)) {
            val bytes = xci(keyArea, listOf("control.nca" to nca(BASE, 2), "program.ncz" to nca(BASE, 0)))
            assertEquals(BASE, switchTitleId(reader(bytes), folder))
        }
    }

    @Test fun multiTitleNcasRefuseEvenWhenOneIsTheProgram() {
        for (bytes in listOf(
            partition(false, listOf("a.nca" to nca(BASE, 0), "b.nca" to nca(OTHER, 1))),
            xci(0, listOf("a.nca" to nca(BASE, 0), "b.nca" to nca(OTHER, 2))),
        )) assertNull(switchTitleId(reader(bytes), folder))
    }

    @Test fun xtsMatchesATestOnlyEncryptUsingBigEndianSectorNumbers() {
        val plain = ByteArray(0x200) { (it * 17 + 3).toByte() }
        for (sector in listOf(0L, 1L, 0x102L)) {
            val encrypted = encrypt(plain, KEY, sector)
            assertArrayEquals(plain, decryptNcaSector(encrypted, KEY, sector))
        }

    }

    @Test fun truncatedGarbageOversizedAndUnreadableInputsNeverYieldATitle() {
        val good = partition(false, listOf("a.nca" to nca(BASE, 0)))
        for (size in listOf(0, 3, 15, 30, 0x300)) assertNull(switchTitleId(reader(good.copyOf(size)), folder))
        val hugeCount = ticket().apply { putInt(4, Int.MAX_VALUE) }
        val hugeStrings = ticket().apply { putInt(8, Int.MAX_VALUE) }
        val badName = ticket().apply { putInt(0x20, Int.MAX_VALUE) }
        val overflow = ticket().apply { putLong(0x10, Long.MAX_VALUE) }
        val negative = ticket().apply { putLong(0x18, -1) }
        val badMagic = good.copyOf().apply { this[0] = 1 }
        for (bytes in listOf(hugeCount, hugeStrings, badName, overflow, negative, badMagic, ByteArray(4096))) assertNull(switchTitleId(reader(bytes), folder))
        assertNull(switchTitleId(object : GameFileReader { override fun read(offset: Long, length: Int): ByteArray? = error("unreadable") }, folder))
        folder.files["keys/prod.keys"] = "header_key = not a key".toByteArray()
        assertNull(switchTitleId(reader(good), folder))
    }

    @Test fun scansAreBoundedAndDoNotGuessFromAPartialSample() {
        val files = (0..16).map { "$it.nca" to nca(BASE, 0) }
        var reads = 0
        val bytes = partition(false, files)
        val game = object : GameFileReader {
            override fun read(offset: Long, length: Int): ByteArray? {
                reads++
                assertTrue(length <= 1024 * 1024)
                return bytes.sliceAt(offset, length)
            }
        }
        assertNull(switchTitleId(game, folder))
        assertTrue(reads <= 4)
    }

    private fun ticket() = partition(false, listOf("${BASE}0000000000000000.tik" to byteArrayOf(1)))

    private fun nca(id: String, type: Int, magic: String = "NCA3"): ByteArray {
        val plain = ByteArray(0x200)
        magic.toByteArray().copyInto(plain)
        plain[5] = type.toByte()
        plain.putLong(0x10, id.toLong(16))
        return ByteArray(0x4000).apply { encrypt(plain, KEY, 1).copyInto(this, 0x200) }
    }

    private fun xci(keyArea: Int, files: List<Pair<String, ByteArray>>): ByteArray {
        val secure = partition(true, files)
        val root = partition(true, listOf("normal" to byteArrayOf(0), "secure" to secure))
        return ByteArray(keyArea + 0x10000 + root.size).apply {
            "HEAD".toByteArray().copyInto(this, keyArea + 0x100)
            putLong(keyArea + 0x130, 0x10000)
            root.copyInto(this, keyArea + 0x10000)
        }
    }

    private fun partition(hashed: Boolean, files: List<Pair<String, ByteArray>>): ByteArray {
        val stride = if (hashed) 0x40 else 0x18
        val names = files.joinToString("") { it.first + '\u0000' }.toByteArray()
        val start = 0x10 + stride * files.size + names.size
        val out = ByteArray(start + files.sumOf { it.second.size })
        (if (hashed) "HFS0" else "PFS0").toByteArray().copyInto(out)
        out.putInt(4, files.size)
        out.putInt(8, names.size)
        names.copyInto(out, 0x10 + stride * files.size)
        var offset = 0
        var name = 0
        for ((index, file) in files.withIndex()) {
            out.putLong(0x10 + index * stride, offset.toLong())
            out.putLong(0x18 + index * stride, file.second.size.toLong())
            out.putInt(0x20 + index * stride, name)
            file.second.copyInto(out, start + offset)
            offset += file.second.size
            name += file.first.length + 1
        }
        return out
    }

    private fun reader(bytes: ByteArray, name: String? = null) = object : GameFileReader {
        override val fileName = name
        override fun read(offset: Long, length: Int): ByteArray? = bytes.sliceAt(offset, length)
    }

    private companion object {
        const val BASE = "0100755017EE0000"
        const val OTHER = "01004A4010FEA000"
        val KEY = ByteArray(32) { (it + 1).toByte() }
    }
}

/** Test-only encryption with a BigInteger GF multiply, separate from the decrypt's byte loop. */
private fun encrypt(plain: ByteArray, key: ByteArray, sector: Long): ByteArray {
    fun aes(bytes: ByteArray, part: ByteArray): ByteArray = Cipher.getInstance("AES/ECB/NoPadding").run {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(part, "AES")); doFinal(bytes)
    }
    val number = ByteArray(16)
    ByteBuffer.wrap(number).putLong(8, sector)
    var tweak = BigInteger(1, aes(number, key.copyOfRange(16, 32)).reversedArray())
    val mask = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)
    return ByteArray(plain.size).apply {
        for (at in plain.indices step 16) {
            val big = tweak.toByteArray().takeLast(16).toByteArray()
            val t = ByteArray(16)
            big.reversedArray().copyInto(t)
            val block = ByteArray(16) { (plain[at + it].toInt() xor t[it].toInt()).toByte() }
            val encoded = aes(block, key.copyOfRange(0, 16))
            for (i in 0..15) this[at + i] = (encoded[i].toInt() xor t[i].toInt()).toByte()
            val carry = tweak.testBit(127)
            tweak = tweak.shiftLeft(1).and(mask)
            if (carry) tweak = tweak.xor(BigInteger.valueOf(0x87))
        }
    }
}

internal class Folder : SaveFolderAccess {
    val files = mutableMapOf<String, ByteArray>()
    var users: List<SaveFolderEntry>? = null
    override fun list(path: String) = if (path == EdenPlayer.SAVES) users else null
    override fun read(path: String, limit: Int) = files[path]?.takeIf { it.size <= limit }
    override fun read(path: String, offset: Long, length: Int) = files[path]?.sliceAt(offset, length)
    override fun write(path: String, bytes: ByteArray): Boolean = error("Read only")
    override fun createFolder(path: String): Boolean = error("Read only")
    override fun moveToBackup(path: String): Boolean = error("Read only")
}

private fun ByteArray.sliceAt(offset: Long, length: Int): ByteArray? =
    if (offset < 0 || offset > size || length < 0) null else copyOfRange(offset.toInt(), minOf(size.toLong(), offset + length).toInt())
private fun ByteArray.putInt(at: Int, value: Int) { ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).putInt(at, value) }
private fun ByteArray.putLong(at: Int, value: Long) { ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).putLong(at, value) }
