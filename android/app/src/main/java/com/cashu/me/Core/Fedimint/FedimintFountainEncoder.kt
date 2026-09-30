package com.cashu.me.Core.Fedimint

import java.security.MessageDigest

/**
 * Emits Fedimint animated-QR frames for a consensus-encoded ecash message.
 *
 * Only the plain source slices are sent, cycled forever: a receiver that sees
 * every slice at least once reassembles the notes, and the endless loop gives a
 * missed frame another chance. That keeps the frames independent of the
 * XOR-combination RNG, so any fedimint-fountain receiver decodes them.
 */
class FedimintFountainEncoder(private val message: ByteArray, maxFragmentBytes: Int) {
    // Same sizing as fedimint-fountain: ceil(len / ceil(len / max)).
    private val fragmentLength: Int = run {
        val slices = (message.size + maxFragmentBytes - 1) / maxFragmentBytes
        (message.size + slices - 1) / slices
    }
    private val padded = message.copyOf(((message.size + fragmentLength - 1) / fragmentLength) * fragmentLength)
    private val checksum = MessageDigest.getInstance("SHA-256").digest(message).copyOf(4)
    private var next = 0L

    /** Number of distinct slices (frames per loop). */
    val sourceCount: Int = padded.size / fragmentLength

    fun nextFrame(): String {
        val index = (next++ % sourceCount).toInt()
        val data = padded.copyOfRange(index * fragmentLength, (index + 1) * fragmentLength)
        val out = java.io.ByteArrayOutputStream()
        fun u32(value: Long) = repeat(4) { out.write((value ushr (8 * (3 - it))).toInt() and 0xFF) }
        u32(sourceCount.toLong())
        u32(message.size.toLong())
        out.write(checksum)
        u32(index.toLong())
        when {
            data.size < 0xFD -> out.write(data.size)
            data.size <= 0xFFFF -> { out.write(0xFD); out.write(data.size ushr 8); out.write(data.size and 0xFF) }
            else -> { out.write(0xFE); u32(data.size.toLong()) }
        }
        out.write(data)
        return FedimintFountainDecoder.PREFIX + FedimintFountainDecoder.base32Encode(out.toByteArray())
    }

    companion object {
        /** Encoder for a notes string (`fedimint…` base32 or base64), or null if it can't be decoded. */
        fun forNotes(notes: String, maxFragmentBytes: Int): FedimintFountainEncoder? {
            val bytes = FedimintSupport.notesBytes(notes) ?: return null
            if (bytes.isEmpty() || maxFragmentBytes <= 0) return null
            return FedimintFountainEncoder(bytes, maxFragmentBytes)
        }
    }
}
