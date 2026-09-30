package com.cashu.me.Core.Fedimint

import java.security.MessageDigest
import java.util.Base64
import java.util.TreeMap

/**
 * Receiver for Fedimint's animated ecash QR codes (the `fedimint-fountain`
 * crate: fragments are `fedimint` + base32hex of a consensus-encoded fragment).
 *
 * The first `n` fragments are the plain message slices; later ones XOR several
 * slices chosen by a ChaCha20-seeded RNG, which lets a scanner that missed a
 * frame still recover. The RNG selection mirrors rand 0.8 on a 64-bit target.
 * Every reassembled message is checked against the sender's SHA-256 checksum,
 * so a selection mismatch can only ever cost a rescan, never corrupt notes.
 */
class FedimintFountainDecoder {
    private class Meta(val simpleFragments: Int, val messageLength: Int, val checksum: ByteArray) {
        val fragmentLength: Int get() = (messageLength + simpleFragments - 1) / simpleFragments

        fun sameAs(other: Meta) = simpleFragments == other.simpleFragments &&
            messageLength == other.messageLength && checksum.contentEquals(other.checksum)
    }

    private class Fragment(val meta: Meta, val index: Long, val data: ByteArray)

    private val decoded = TreeMap<Int, ByteArray>()
    private val buffer = LinkedHashMap<List<Int>, ByteArray>()
    private var meta: Meta? = null

    /** Fraction of the message's source slices recovered so far, 0..1. */
    val progress: Float
        get() {
            val m = meta ?: return 0f
            return (decoded.size.toFloat() / m.simpleFragments).coerceIn(0f, 1f)
        }

    fun reset() {
        decoded.clear()
        buffer.clear()
        meta = null
    }

    /**
     * Feeds one scanned frame. Returns the reassembled message bytes once
     * complete; null while more frames are needed or when [frame] is not a
     * fountain fragment (check [isFragment] to tell those apart).
     */
    fun receive(frame: String): ByteArray? {
        val fragment = parse(frame) ?: return null
        val current = meta
        if (current == null) {
            meta = fragment.meta
        } else if (!current.sameAs(fragment.meta)) {
            // A different transmission started: drop the old one.
            reset()
            meta = fragment.meta
        }
        val m = meta!!
        if (fragment.data.size != m.fragmentLength) return null

        val indexes = chooseFragments(m.simpleFragments, m.checksum, fragment.index)
        if (indexes.size == 1) processSimple(indexes[0], fragment.data) else processComplex(indexes, fragment.data)
        return message()
    }

    private fun message(): ByteArray? {
        val m = meta ?: return null
        if (decoded.size < m.simpleFragments) return null
        val out = ByteArray(m.messageLength)
        var offset = 0
        for (chunk in decoded.values) {
            val take = minOf(chunk.size, m.messageLength - offset)
            if (take <= 0) break
            System.arraycopy(chunk, 0, out, offset, take)
            offset += take
        }
        if (!checksum(out).contentEquals(m.checksum)) {
            // Wrong reassembly (or a foreign transmission): start over.
            reset()
            return null
        }
        return out
    }

    private fun processSimple(index: Int, data: ByteArray) {
        decoded[index] = data
        val queue = ArrayDeque(decoded.entries.map { it.key to it.value })
        while (queue.isNotEmpty()) {
            val (solvedIndex, solved) = queue.removeLast()
            for ((key, value) in buffer.entries.filter { solvedIndex in it.key }) {
                buffer.remove(key)
                val remaining = key.filter { it != solvedIndex }
                val data2 = value.copyOf()
                xor(data2, solved)
                if (remaining.size == 1) {
                    decoded[remaining[0]] = data2
                    queue.addLast(remaining[0] to data2)
                } else {
                    buffer[remaining] = data2
                }
            }
        }
    }

    private fun processComplex(indexes: List<Int>, data: ByteArray) {
        val toRemove = indexes.filter { it in decoded }
        if (indexes.size == toRemove.size) return
        val out = data.copyOf()
        for (r in toRemove) xor(out, decoded.getValue(r))
        val remaining = indexes.filter { it !in toRemove }
        if (remaining.size == 1) decoded[remaining[0]] = out else buffer[remaining] = out
    }

    private fun parse(frame: String): Fragment? = runCatching {
        val bytes = base32Decode(frame.trim().lowercase().removePrefix(PREFIX)) ?: return null
        val reader = Reader(bytes)
        val simple = reader.u32().toInt()
        val length = reader.u32().toInt()
        val checksum = reader.bytes(4)
        val index = reader.u32()
        val data = reader.bytes(reader.varInt().toInt())
        if (!reader.atEnd || simple <= 0 || length <= 0 || simple > MAX_FRAGMENTS || length > MAX_MESSAGE) return null
        val meta = Meta(simple, length, checksum)
        if (meta.fragmentLength <= 0) return null
        Fragment(meta, index, data)
    }.getOrNull()

    private class Reader(private val bytes: ByteArray) {
        private var pos = 0
        val atEnd: Boolean get() = pos == bytes.size

        fun bytes(n: Int): ByteArray {
            require(n >= 0 && pos + n <= bytes.size)
            return bytes.copyOfRange(pos, pos + n).also { pos += n }
        }

        fun u32(): Long = bytes(4).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }

        fun varInt(): Long = when (val first = bytes(1)[0].toInt() and 0xFF) {
            0xFD -> bytes(2).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
            0xFE -> u32()
            0xFF -> bytes(8).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
            else -> first.toLong()
        }
    }

    companion object {
        const val PREFIX = "fedimint"
        private const val MAX_FRAGMENTS = 100_000
        private const val MAX_MESSAGE = 16 * 1024 * 1024
        private const val ALPHABET = "0123456789abcdefghijklmnopqrstuv"

        /** Whether [frame] parses as a fountain fragment (as opposed to whole notes or something else). */
        fun isFragment(frame: String): Boolean {
            val trimmed = frame.trim()
            if (!trimmed.startsWith(PREFIX, ignoreCase = true)) return false
            return FedimintFountainDecoder().let { it.parse(trimmed) != null }
        }

        /** Fedimint's base32hex (RFC 4648 alphabet, lowercase, no padding, least-significant bits first). */
        internal fun base32Decode(input: String): ByteArray? {
            val out = java.io.ByteArrayOutputStream()
            var buffer = 0
            var bits = 0
            for (ch in input) {
                val value = ALPHABET.indexOf(ch)
                if (value < 0) return null
                buffer = buffer or (value shl bits)
                bits += 5
                while (bits >= 8) {
                    out.write(buffer and 0xFF)
                    buffer = buffer ushr 8
                    bits -= 8
                }
            }
            return out.toByteArray()
        }

        internal fun base32Encode(input: ByteArray): String {
            val out = StringBuilder()
            var buffer = 0
            var bits = 0
            for (byte in input) {
                buffer = buffer or ((byte.toInt() and 0xFF) shl bits)
                bits += 8
                while (bits >= 5) {
                    out.append(ALPHABET[buffer and 0x1F])
                    buffer = buffer ushr 5
                    bits -= 5
                }
            }
            if (bits > 0) out.append(ALPHABET[buffer and 0x1F])
            return out.toString()
        }

        /**
         * Candidate textual encodings for a reassembled ecash message; the caller keeps the
         * first one its notes parser accepts.
         */
        fun notesCandidates(message: ByteArray): List<String> = listOf(
            PREFIX + base32Encode(message),
            Base64.getUrlEncoder().withoutPadding().encodeToString(message),
            Base64.getEncoder().encodeToString(message),
            Base64.getUrlEncoder().encodeToString(message),
        )

        private fun checksum(data: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(data).copyOf(4)

        private fun xor(target: ByteArray, other: ByteArray) {
            for (i in target.indices) target[i] = (target[i].toInt() xor other[i].toInt()).toByte()
        }

        /** Port of `choose_fragments` from `fedimint-fountain`. */
        internal fun chooseFragments(count: Int, checksum: ByteArray, index: Long): List<Int> {
            if (index < count) return listOf(index.toInt())

            val seedInput = ByteArray(8).also {
                System.arraycopy(checksum, 0, it, 0, 4)
                for (i in 0 until 4) it[4 + i] = (index ushr (8 * (3 - i))).toByte()
            }
            val rng = ChaCha20Rng(MessageDigest.getInstance("SHA-256").digest(seedInput))

            // WeightedIndex over 1/k (Ideal Soliton) then Uniform<f64> sampling, as in rand 0.8.
            val cumulative = DoubleArray(count - 1)
            var total = 1.0
            for (x in 1 until count) {
                cumulative[x - 1] = total
                total += 1.0 / (x + 1)
            }
            var scale = total
            val maxRand = 1.0 - Math.ulp(1.0)
            while (scale * maxRand >= total) scale = Math.nextDown(scale)
            val chosen = (Double.fromBits(0x3FF0000000000000L or (rng.nextU64().toLong() ushr 12)) - 1.0) * scale
            var degree = cumulative.count { it <= chosen } + 1
            if (degree > count) degree = count

            // IteratorRandom::choose_multiple (reservoir sampling) over 0 until count.
            val reservoir = MutableList(degree) { it }
            for (i in 0 until count - degree) {
                val k = rng.genRange((i + 1 + degree).toULong()).toInt()
                if (k < reservoir.size) reservoir[k] = degree + i
            }
            return reservoir
        }
    }
}

/** ChaCha20 keystream with a zero nonce and counter, matching `rand_chacha::ChaCha20Rng::from_seed`. */
internal class ChaCha20Rng(seed: ByteArray) {
    private val key = IntArray(8) { i ->
        (seed[4 * i].toInt() and 0xFF) or ((seed[4 * i + 1].toInt() and 0xFF) shl 8) or
            ((seed[4 * i + 2].toInt() and 0xFF) shl 16) or ((seed[4 * i + 3].toInt() and 0xFF) shl 24)
    }
    private var counter = 0L
    private var block = IntArray(16)
    private var used = 16

    private fun nextWord(): Int {
        if (used == 16) {
            block = generateBlock(counter++)
            used = 0
        }
        return block[used++]
    }

    fun nextU64(): ULong {
        val lo = nextWord().toUInt().toULong()
        val hi = nextWord().toUInt().toULong()
        return (hi shl 32) or lo
    }

    /** `rng.gen_range(0..range)` for a usize on a 64-bit target (rand 0.8 widening-multiply rejection). */
    fun genRange(range: ULong): ULong {
        val zone = (range shl range.countLeadingZeroBits()) - 1uL
        while (true) {
            val v = nextU64()
            val vLo = v and 0xFFFFFFFFuL
            val vHi = v shr 32
            val lo = vLo * range
            val mid = vHi * range + (lo shr 32)
            val hi = mid shr 32
            val low64 = (mid shl 32) or (lo and 0xFFFFFFFFuL)
            if (low64 <= zone) return hi
        }
    }

    internal fun generateBlock(blockCounter: Long): IntArray {
        val s = IntArray(16)
        s[0] = 0x61707865; s[1] = 0x3320646e; s[2] = 0x79622d32; s[3] = 0x6b206574
        for (i in 0 until 8) s[4 + i] = key[i]
        s[12] = blockCounter.toInt()
        s[13] = (blockCounter ushr 32).toInt()
        s[14] = 0
        s[15] = 0
        val x = s.copyOf()
        repeat(10) {
            quarter(x, 0, 4, 8, 12); quarter(x, 1, 5, 9, 13); quarter(x, 2, 6, 10, 14); quarter(x, 3, 7, 11, 15)
            quarter(x, 0, 5, 10, 15); quarter(x, 1, 6, 11, 12); quarter(x, 2, 7, 8, 13); quarter(x, 3, 4, 9, 14)
        }
        for (i in 0 until 16) x[i] += s[i]
        return x
    }

    private fun quarter(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
        x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] xor x[a], 16)
        x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] xor x[c], 12)
        x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] xor x[a], 8)
        x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] xor x[c], 7)
    }
}
