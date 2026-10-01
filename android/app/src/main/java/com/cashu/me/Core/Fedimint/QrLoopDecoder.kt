package com.cashu.me.Core.Fedimint

import java.security.MessageDigest
import java.util.Base64

/**
 * Receiver for gre's `qrloop` animated QR codes, the format the Fedi app uses
 * for ecash too large for one code. Every frame is standard base64 of either:
 *
 * - a data frame: replica nonce (0..9), frame count (u16 BE), frame index
 *   (u16 BE), then one fixed-size chunk;
 * - a fountain frame: 100, k (u16 BE), k frame indexes (u16 BE), then the XOR
 *   of those chunks, so a frame the camera missed can be rebuilt.
 *
 * The chunks join into length (u32 BE) + MD5 + payload + zero padding, and the
 * MD5 is checked before the payload is returned. Port of qrloop's `importer.ts`.
 */
class QrLoopDecoder {
    private sealed interface Frame
    private class DataFrame(val count: Int, val index: Int, val data: ByteArray) : Frame
    private class FountainFrame(val indexes: List<Int>, val data: ByteArray) : Frame

    /** Frame count of the transmission being collected; 0 before the first data frame. */
    private var count = 0
    private var chunkSize = 0
    private val chunks = HashMap<Int, ByteArray>()
    private val fountains = ArrayList<FountainFrame>()
    private val seenFountains = LinkedHashSet<String>()

    /** Fraction of the transmission's chunks recovered so far, 0..1. */
    val progress: Float
        get() = if (count == 0) 0f else (chunks.size.toFloat() / count).coerceIn(0f, 1f)

    fun reset() {
        count = 0
        chunkSize = 0
        chunks.clear()
        fountains.clear()
        seenFountains.clear()
    }

    /**
     * Feeds one scanned frame. Returns the payload bytes once every chunk is in
     * and the MD5 matches; null while more frames are needed or when [frame] is
     * not a qrloop frame (check [isFrame] to tell those apart).
     */
    fun receive(frame: String): ByteArray? {
        val trimmed = frame.trim()
        when (val parsed = parse(trimmed) ?: return null) {
            is DataFrame -> {
                if (parsed.count != count || parsed.data.size != chunkSize) {
                    // A different transmission. Fountains queued before any data
                    // frame arrived may belong to it; ones from an old session don't.
                    if (count != 0) fountains.clear()
                    chunks.clear()
                    count = parsed.count
                    chunkSize = parsed.data.size
                }
                // The nonce only numbers replicas of the same data, so it is ignored.
                chunks[parsed.index] = parsed.data
            }
            is FountainFrame -> {
                if (!seenFountains.add(trimmed)) return null
                if (seenFountains.size > MAX_SEEN_FOUNTAINS) seenFountains.remove(seenFountains.first())
                fountains.add(parsed)
                if (fountains.size > MAX_QUEUED_FOUNTAINS) fountains.removeAt(0)
            }
        }
        resolveFountains()
        return message()
    }

    private fun resolveFountains() {
        if (count == 0) return
        var i = 0
        while (i < fountains.size) {
            val fountain = fountains[i]
            if (fountain.data.size != chunkSize || fountain.indexes.any { it >= count }) {
                fountains.removeAt(i)
                continue
            }
            val missing = fountain.indexes.filter { it !in chunks }
            when (missing.size) {
                0 -> fountains.removeAt(i)
                1 -> {
                    val recovered = fountain.data.copyOf()
                    for (index in fountain.indexes) if (index != missing[0]) xor(recovered, chunks.getValue(index))
                    chunks[missing[0]] = recovered
                    fountains.removeAt(i)
                    // A recovered chunk can unlock fountains already passed over.
                    i = 0
                }
                else -> i++
            }
        }
    }

    private fun message(): ByteArray? {
        if (count == 0 || chunks.size < count) return null
        val joined = ByteArray(count * chunkSize)
        for (index in 0 until count) {
            System.arraycopy(chunks.getValue(index), 0, joined, index * chunkSize, chunkSize)
        }
        val length = if (joined.size >= HEADER_LENGTH) u32(joined, 0) else -1L
        if (length < 0 || HEADER_LENGTH + length > joined.size) {
            reset()
            return null
        }
        val payload = joined.copyOfRange(HEADER_LENGTH, HEADER_LENGTH + length.toInt())
        if (!MessageDigest.getInstance("MD5").digest(payload).contentEquals(joined.copyOfRange(4, HEADER_LENGTH))) {
            // Wrong reassembly (or a foreign transmission): start over.
            reset()
            return null
        }
        return payload
    }

    companion object {
        private const val MAX_NONCE = 10
        private const val FOUNTAIN_V1 = 100
        private const val HEADER_LENGTH = 20
        private const val MAX_FRAMES = 4096
        private const val MAX_SEEN_FOUNTAINS = 512
        private const val MAX_QUEUED_FOUNTAINS = 128

        /** Whether [frame] parses as a qrloop data or fountain frame. */
        fun isFrame(frame: String): Boolean = parse(frame.trim()) != null

        private fun parse(frame: String): Frame? {
            // Only canonical standard base64 qualifies: no `:`, `-`, `_`, whitespace,
            // or stray trailing bits, which keeps other payloads out.
            val bytes = runCatching { Base64.getDecoder().decode(frame) }.getOrNull() ?: return null
            if (Base64.getEncoder().withoutPadding().encodeToString(bytes) != frame.trimEnd('=')) return null
            if (bytes.size < 6) return null
            val head = bytes[0].toInt() and 0xFF
            return when {
                head < MAX_NONCE -> {
                    val count = u16(bytes, 1)
                    val index = u16(bytes, 3)
                    if (count !in 1..MAX_FRAMES || index >= count) null
                    else DataFrame(count, index, bytes.copyOfRange(5, bytes.size))
                }
                head == FOUNTAIN_V1 -> {
                    val k = u16(bytes, 1)
                    val dataStart = 3 + 2 * k
                    if (k < 1 || dataStart >= bytes.size) return null
                    val indexes = List(k) { u16(bytes, 3 + 2 * it) }
                    if (indexes.any { it >= MAX_FRAMES } || indexes.toSet().size != k) null
                    else FountainFrame(indexes, bytes.copyOfRange(dataStart, bytes.size))
                }
                else -> null
            }
        }

        private fun u16(bytes: ByteArray, at: Int): Int =
            ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)

        private fun u32(bytes: ByteArray, at: Int): Long =
            (0 until 4).fold(0L) { acc, i -> (acc shl 8) or (bytes[at + i].toLong() and 0xFF) }

        private fun xor(target: ByteArray, other: ByteArray) {
            for (i in target.indices) target[i] = (target[i].toInt() xor other[i].toInt()).toByte()
        }
    }
}
