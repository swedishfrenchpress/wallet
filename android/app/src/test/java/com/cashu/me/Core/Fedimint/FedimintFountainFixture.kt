package com.cashu.me.Core.Fedimint

import java.security.MessageDigest

/** Builds `fedimint-fountain` source fragments, as another Fedimint wallet would show them. */
internal object FedimintFountainFixture {
    private fun u32(value: Long) = ByteArray(4) { (value ushr (8 * (3 - it))).toByte() }

    fun frame(message: ByteArray, simple: Int, index: Long, data: ByteArray): String {
        val checksum = MessageDigest.getInstance("SHA-256").digest(message).copyOf(4)
        val bytes = u32(simple.toLong()) + u32(message.size.toLong()) + checksum + u32(index) +
            byteArrayOf(data.size.toByte()) + data
        return "fedimint" + FedimintFountainDecoder.base32Encode(bytes)
    }

    fun sourceFrames(message: ByteArray, slices: Int): List<String> {
        val length = (message.size + slices - 1) / slices
        val padded = message.copyOf(length * slices)
        return (0 until slices).map { i ->
            frame(message, slices, i.toLong(), padded.copyOfRange(i * length, (i + 1) * length))
        }
    }
}
