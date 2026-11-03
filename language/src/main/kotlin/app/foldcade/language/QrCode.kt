package app.foldcade.language

/**
 * A QR code for a short text, such as RomM's pairing address, so a phone can open it from the
 * screen. Byte mode, error correction level M, versions 1 to 10 (up to 213 bytes). It is drawn
 * from the theme's own colours, so nothing here knows how it looks.
 *
 * Written for Foldcade from ISO/IEC 18004; no QR library ships in the app.
 */
class QrCode private constructor(val size: Int, private val modules: BooleanArray) {
    /** True for a dark module. (0, 0) is the top left. */
    fun dark(x: Int, y: Int): Boolean = modules[y * size + x]

    companion object {
        /** [text] as UTF-8, in the smallest version that holds it, or null when none does. */
        fun encode(text: String): QrCode? {
            val data = text.toByteArray(Charsets.UTF_8)
            val version = (1..MAX_VERSION).firstOrNull { capacityBits(it) >= bitsFor(data.size, it) } ?: return null
            val codewords = withErrorCorrection(dataCodewords(data, version), version)
            val matrix = Matrix(version)
            matrix.drawFunctionPatterns()
            matrix.drawCodewords(codewords)
            // Each mask on its own copy; the one that reads most like noise wins.
            val best = (0 until 8).minBy { mask -> matrix.copy().apply { applyMask(mask); drawFormat(mask) }.penalty() }
            matrix.applyMask(best)
            matrix.drawFormat(best)
            return QrCode(matrix.size, matrix.cells)
        }

        private const val MAX_VERSION = 10

        // Level M, versions 1..10: error-correction codewords per block, and blocks.
        private val ECC_PER_BLOCK = intArrayOf(0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26)
        private val BLOCKS = intArrayOf(0, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5)

        /** Level M's two format bits. */
        private const val LEVEL_M = 0

        private fun rawModules(version: Int): Int {
            var result = (16 * version + 128) * version + 64
            if (version >= 2) {
                val align = version / 7 + 2
                result -= (25 * align - 10) * align - 55
                if (version >= 7) result -= 36
            }
            return result
        }

        private fun dataCodewordCount(version: Int): Int = rawModules(version) / 8 - ECC_PER_BLOCK[version] * BLOCKS[version]

        private fun capacityBits(version: Int): Int = dataCodewordCount(version) * 8

        private fun countBits(version: Int): Int = if (version < 10) 8 else 16

        private fun bitsFor(bytes: Int, version: Int): Int =
            if (bytes >= (1 shl countBits(version))) Int.MAX_VALUE else 4 + countBits(version) + bytes * 8

        /** Mode, length, the bytes, the terminator, then the spec's two pad bytes in turn. */
        private fun dataCodewords(data: ByteArray, version: Int): IntArray {
            val bits = ArrayList<Boolean>()
            fun put(value: Int, length: Int) { for (i in length - 1 downTo 0) bits.add((value ushr i) and 1 == 1) }
            put(0b0100, 4)
            put(data.size, countBits(version))
            data.forEach { put(it.toInt() and 0xFF, 8) }
            val capacity = capacityBits(version)
            put(0, minOf(4, capacity - bits.size))
            put(0, (8 - bits.size % 8) % 8)
            var pad = 0xEC
            while (bits.size < capacity) {
                put(pad, 8)
                pad = pad xor 0xEC xor 0x11
            }
            return IntArray(bits.size / 8) { index -> (0 until 8).fold(0) { byte, bit -> (byte shl 1) or if (bits[index * 8 + bit]) 1 else 0 } }
        }

        /** Splits the data into blocks, appends each block's Reed-Solomon codewords, and interleaves them. */
        private fun withErrorCorrection(data: IntArray, version: Int): IntArray {
            val blocks = BLOCKS[version]
            val eccLength = ECC_PER_BLOCK[version]
            val raw = rawModules(version) / 8
            val shortBlocks = blocks - raw % blocks
            val shortLength = raw / blocks
            val divisor = reedSolomonDivisor(eccLength)
            var at = 0
            val all = (0 until blocks).map { index ->
                val length = shortLength - eccLength + if (index < shortBlocks) 0 else 1
                val block = data.copyOfRange(at, at + length)
                at += length
                val ecc = reedSolomonRemainder(block, divisor)
                // A short block takes a placeholder, skipped when interleaving, so the columns line up.
                (if (index < shortBlocks) block + 0 else block) + ecc
            }
            val result = ArrayList<Int>(raw)
            for (i in all[0].indices) {
                all.forEachIndexed { index, block -> if (i != shortLength - eccLength || index >= shortBlocks) result.add(block[i]) }
            }
            return result.toIntArray()
        }

        private fun reedSolomonDivisor(degree: Int): IntArray {
            val result = IntArray(degree)
            result[degree - 1] = 1
            var root = 1
            repeat(degree) {
                for (j in 0 until degree) {
                    result[j] = multiply(result[j], root)
                    if (j + 1 < degree) result[j] = result[j] xor result[j + 1]
                }
                root = multiply(root, 0x02)
            }
            return result
        }

        private fun reedSolomonRemainder(data: IntArray, divisor: IntArray): IntArray {
            val result = IntArray(divisor.size)
            for (byte in data) {
                val factor = byte xor result[0]
                for (i in 0 until result.size - 1) result[i] = result[i + 1]
                result[result.size - 1] = 0
                for (i in result.indices) result[i] = result[i] xor multiply(divisor[i], factor)
            }
            return result
        }

        /** Multiplication in GF(2^8) modulo x^8 + x^4 + x^3 + x^2 + 1. */
        private fun multiply(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z and 0xFF
        }
    }

    /** The modules being drawn, and which of them belong to the fixed patterns that data and masks skip. */
    private class Matrix(val version: Int) {
        val size = version * 4 + 17
        private var dark = BooleanArray(size * size)
        private var fixed = BooleanArray(size * size)

        val cells: BooleanArray get() = dark

        fun copy(): Matrix = Matrix(version).also {
            it.dark = dark.copyOf()
            it.fixed = fixed.copyOf()
        }

        private fun setFunction(x: Int, y: Int, on: Boolean) {
            dark[y * size + x] = on
            fixed[y * size + x] = true
        }

        fun drawFunctionPatterns() {
            for (i in 0 until size) {
                setFunction(6, i, i % 2 == 0)
                setFunction(i, 6, i % 2 == 0)
            }
            finder(3, 3)
            finder(size - 4, 3)
            finder(3, size - 4)
            val positions = alignmentPositions()
            val last = positions.lastIndex
            for (i in positions.indices) for (j in positions.indices) {
                if ((i == 0 && j == 0) || (i == 0 && j == last) || (i == last && j == 0)) continue
                for (dy in -2..2) for (dx in -2..2) setFunction(positions[i] + dx, positions[j] + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
            }
            // Reserve the format areas now; the chosen mask's bits overwrite them.
            drawFormat(0)
            drawVersion()
        }

        private fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val x = cx + dx
                val y = cy + dy
                if (x !in 0 until size || y !in 0 until size) continue
                val distance = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                setFunction(x, y, distance != 2 && distance != 4)
            }
        }

        private fun alignmentPositions(): List<Int> {
            if (version == 1) return emptyList()
            val count = version / 7 + 2
            val step = (version * 8 + count * 3 + 5) / (count * 4 - 4) * 2
            val result = ArrayList<Int>()
            var position = size - 7
            repeat(count - 1) {
                result.add(0, position)
                position -= step
            }
            result.add(0, 6)
            return result
        }

        fun drawFormat(mask: Int) {
            val data = (LEVEL_M shl 3) or mask
            var remainder = data
            repeat(10) { remainder = (remainder shl 1) xor ((remainder ushr 9) * 0x537) }
            val bits = ((data shl 10) or remainder) xor 0x5412
            fun bit(i: Int) = (bits ushr i) and 1 == 1
            for (i in 0..5) setFunction(8, i, bit(i))
            setFunction(8, 7, bit(6))
            setFunction(8, 8, bit(7))
            setFunction(7, 8, bit(8))
            for (i in 9 until 15) setFunction(14 - i, 8, bit(i))
            for (i in 0 until 8) setFunction(size - 1 - i, 8, bit(i))
            for (i in 8 until 15) setFunction(8, size - 15 + i, bit(i))
            setFunction(8, size - 8, true)
        }

        private fun drawVersion() {
            if (version < 7) return
            var remainder = version
            repeat(12) { remainder = (remainder shl 1) xor ((remainder ushr 11) * 0x1F25) }
            val bits = (version shl 12) or remainder
            for (i in 0 until 18) {
                val on = (bits ushr i) and 1 == 1
                val a = size - 11 + i % 3
                val b = i / 3
                setFunction(a, b, on)
                setFunction(b, a, on)
            }
        }

        /** Two columns at a time from the right, zig-zagging up then down, skipping the timing column. */
        fun drawCodewords(codewords: IntArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vertical in 0 until size) for (j in 0..1) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vertical else vertical
                    if (!fixed[y * size + x] && i < codewords.size * 8) {
                        dark[y * size + x] = (codewords[i ushr 3] ushr (7 - (i and 7))) and 1 == 1
                        i++
                    }
                }
                right -= 2
            }
        }

        fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                if (fixed[y * size + x]) continue
                val flip = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (flip) dark[y * size + x] = !dark[y * size + x]
            }
        }

        /** The spec's four penalty rules: long runs, 2×2 blocks, finder look-alikes, and imbalance. */
        fun penalty(): Int {
            var score = 0
            fun at(x: Int, y: Int) = dark[y * size + x]
            for (line in 0 until size) for (horizontal in listOf(true, false)) {
                var run = 1
                for (i in 1 until size) {
                    val same = if (horizontal) at(i, line) == at(i - 1, line) else at(line, i) == at(line, i - 1)
                    if (same) {
                        run++
                        if (run == 5) score += 3 else if (run > 5) score++
                    } else run = 1
                }
                // 1:1:3:1:1 with four light modules on either side.
                for (i in 0..size - 11) {
                    val window = BooleanArray(11) { k -> if (horizontal) at(i + k, line) else at(line, i + k) }
                    if (window.contentEquals(FINDER_LEFT) || window.contentEquals(FINDER_RIGHT)) score += 40
                }
            }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = at(x, y)
                if (c == at(x + 1, y) && c == at(x, y + 1) && c == at(x + 1, y + 1)) score += 3
            }
            val darkCount = dark.count { it }
            val total = size * size
            val k = (kotlin.math.abs(darkCount * 20 - total * 10) + total - 1) / total - 1
            score += k.coerceAtLeast(0) * 10
            return score
        }

        private companion object {
            val FINDER_LEFT = "10111010000".map { it == '1' }.toBooleanArray()
            val FINDER_RIGHT = "00001011101".map { it == '1' }.toBooleanArray()
        }
    }
}
