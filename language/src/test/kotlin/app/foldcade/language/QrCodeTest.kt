package app.foldcade.language

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Every code Foldcade draws must scan: each is read back with ZXing, a test-only dependency. */
class QrCodeTest {
    private fun decode(code: QrCode, scale: Int = 4, quiet: Int = 4): String {
        val side = (code.size + quiet * 2) * scale
        val pixels = IntArray(side * side) { index ->
            val x = (index % side) / scale - quiet
            val y = (index / side) / scale - quiet
            val dark = x in 0 until code.size && y in 0 until code.size && code.dark(x, y)
            if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        return QRCodeReader().decode(bitmap, mapOf(DecodeHintType.PURE_BARCODE to true, DecodeHintType.CHARACTER_SET to "UTF-8")).text
    }

    @Test
    fun pairingAddressesScan() {
        val texts = listOf(
            "https://romm.example.dev/pair/device?user_code=EUUJNSZV",
            "http://192.168.1.20:8080/pair/device?user_code=W65YVX5K",
            "A",
            "https://romm.example.dev",
        )
        for (text in texts) assertEquals(text, decode(QrCode.encode(text)!!))
    }

    @Test
    fun everyVersionScans() {
        // Lengths that land on each version from 1 to 10, including the 16-bit count from 10.
        for (length in listOf(1, 14, 15, 26, 27, 42, 43, 62, 63, 84, 85, 106, 107, 122, 123, 152, 153, 180, 181, 213)) {
            val text = (0 until length).map { "abcdefghijklmnopqrstuvwxyz0123456789"[it % 36] }.joinToString("")
            val code = QrCode.encode(text)!!
            assertEquals("length $length", text, decode(code))
        }
    }

    @Test
    fun picksTheSmallestVersion() {
        assertEquals(21, QrCode.encode("x".repeat(14))!!.size)
        assertEquals(25, QrCode.encode("x".repeat(15))!!.size)
        assertEquals(57, QrCode.encode("x".repeat(213))!!.size)
    }

    @Test
    fun tooLongHasNoCode() {
        assertNull(QrCode.encode("x".repeat(214)))
    }

    @Test
    fun nonAsciiScans() {
        val text = "https://römm.example/pair?c=ÄÖÜ"
        assertEquals(text, decode(QrCode.encode(text)!!))
    }
}
