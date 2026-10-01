package com.cashu.me.Core.Fedimint

import com.cashu.me.Models.FederationGuardian

/**
 * Reads the guardian endpoints out of a Fedimint invite code.
 *
 * The SDK hands back an invite as an opaque `fed1…` string, but the code is a
 * bech32m wrapper around a consensus-encoded `Vec<InviteCodePart>`: each part
 * is a BigSize variant index plus a length-prefixed body, where variant 0 is a
 * guardian API endpoint (`url`, `peer`) and variant 1 the federation id.
 * Unknown variants are skipped by length, as upstream's decoder does.
 *
 * An invite usually names only some guardians (often just one); the full
 * count comes from the federation's config via the SDK's preview.
 */
object FedimintInviteCode {
    data class Decoded(
        val federationId: String?,
        val guardians: List<FederationGuardian>,
    )

    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val BECH32M_CONST = 0x2bc830a3
    private const val CHECKSUM_LENGTH = 6
    private const val PART_API = 0L
    private const val PART_FEDERATION_ID = 1L

    fun decode(invite: String): Decoded? = runCatching {
        val bytes = bech32mPayload(invite.trim()) ?: return null
        val reader = Reader(bytes)
        val parts = reader.bigSize()
        var federationId: String? = null
        val guardians = mutableListOf<FederationGuardian>()
        repeat(parts.toInt()) {
            val variant = reader.bigSize()
            val body = Reader(reader.bytes(reader.bigSize().toInt()))
            when (variant) {
                PART_API -> {
                    val url = String(body.bytes(body.bigSize().toInt()), Charsets.UTF_8)
                    guardians += FederationGuardian(peerId = body.bigSize().toInt(), url = url)
                }
                PART_FEDERATION_ID -> federationId = body.bytes(32).joinToString("") { "%02x".format(it) }
            }
        }
        Decoded(federationId, guardians.distinctBy { it.peerId }.sortedBy { it.peerId })
    }.getOrNull()

    /** The 8-bit payload of a bech32m string, or null when the checksum doesn't hold. */
    private fun bech32mPayload(value: String): ByteArray? {
        val lower = value.lowercase()
        if (lower != value && value.uppercase() != value) return null
        val separator = lower.lastIndexOf('1')
        if (separator < 1 || lower.length - separator - 1 < CHECKSUM_LENGTH) return null
        val hrp = lower.substring(0, separator)
        val data = lower.substring(separator + 1).map { char ->
            CHARSET.indexOf(char).takeIf { it >= 0 } ?: return null
        }
        val expanded = hrp.map { it.code shr 5 } + 0 + hrp.map { it.code and 31 }
        if (polymod(expanded + data) != BECH32M_CONST) return null

        val out = java.io.ByteArrayOutputStream()
        var accumulator = 0
        var bits = 0
        for (value5 in data.dropLast(CHECKSUM_LENGTH)) {
            accumulator = (accumulator shl 5) or value5
            bits += 5
            while (bits >= 8) {
                bits -= 8
                out.write((accumulator shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    private fun polymod(values: List<Int>): Int {
        val generator = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var checksum = 1
        for (value in values) {
            val top = checksum ushr 25
            checksum = ((checksum and 0x1ffffff) shl 5) xor value
            for (i in 0 until 5) {
                if ((top shr i) and 1 == 1) checksum = checksum xor generator[i]
            }
        }
        return checksum
    }

    /** Fedimint's consensus encoding: BigSize integers (big-endian after a 0xFD/0xFE/0xFF marker). */
    private class Reader(private val bytes: ByteArray) {
        private var position = 0

        fun bigSize(): Long {
            val first = bytes[position++].toInt() and 0xFF
            val width = when (first) {
                0xFD -> 2
                0xFE -> 4
                0xFF -> 8
                else -> return first.toLong()
            }
            var value = 0L
            repeat(width) { value = (value shl 8) or (bytes[position++].toLong() and 0xFF) }
            return value
        }

        fun bytes(count: Int): ByteArray {
            require(count >= 0 && position + count <= bytes.size) { "Truncated invite code." }
            return bytes.copyOfRange(position, position + count).also { position += count }
        }
    }
}
