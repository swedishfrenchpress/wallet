package com.cashu.me.Core.Fedimint

import java.security.MessageDigest
import java.util.Base64
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Emits gre's `qrloop` animated-QR frames, the format Fedi scans; Fedimint's
 * ecash app and this app's [QrLoopDecoder] read it too. Port of qrloop's
 * `exporter.ts`, including its seeded fountain selection, so the frames match
 * the reference library byte for byte.
 */
object QrLoopEncoder {
    private const val MAX_NONCE = 10
    private const val FOUNTAIN_V1 = 100

    /** One animation loop (or [loops] replicas) of base64 frames carrying [data]. */
    fun frames(data: ByteArray, dataSize: Int = 120, loops: Int = 1): List<String> {
        require(dataSize > 0 && loops > 0)
        val wrapped = ByteArray(4) { (data.size ushr (8 * (3 - it))).toByte() } +
            MessageDigest.getInstance("MD5").digest(data) + data
        // qrloop's deterministic RNG; StrictMath is fdlibm, like V8's Math.sin.
        var seed = 1.0
        val random = {
            val x = StrictMath.sin(seed++) * 10000
            x - floor(x)
        }
        return (0 until loops).flatMap { loop(wrapped, dataSize, it % MAX_NONCE, random) }
    }

    private fun loop(wrapped: ByteArray, dataSize: Int, nonce: Int, random: () -> Double): List<String> {
        val count = (wrapped.size + dataSize - 1) / dataSize
        val chunks = List(count) { i ->
            wrapped.copyOfRange(i * dataSize, minOf((i + 1) * dataSize, wrapped.size)).copyOf(dataSize)
        }
        val fountains = if (count > 2) {
            val k = ceil(count / 2.0).toInt()
            List(count / 6) {
                val picked = List(count) { i -> i to random() }.sortedBy { it.second }.take(k).map { it.first }
                fountainFrame(chunks, picked)
            }
        } else {
            emptyList()
        }
        val fountainEach = if (fountains.isEmpty()) 0 else count / fountains.size
        var next = 0
        return buildList {
            for (i in 0 until count) {
                add(encode(byteArrayOf(nonce.toByte()) + u16(count) + u16(i) + chunks[i]))
                if (fountainEach > 0 && i % fountainEach == 0 && next < fountains.size) add(fountains[next++])
            }
        }
    }

    private fun fountainFrame(chunks: List<ByteArray>, indexes: List<Int>): String {
        val data = chunks[indexes[0]].copyOf()
        for (index in indexes.drop(1)) {
            val chunk = chunks[index]
            for (j in data.indices) data[j] = (data[j].toInt() xor chunk[j].toInt()).toByte()
        }
        val head = byteArrayOf(FOUNTAIN_V1.toByte()) + u16(indexes.size) + indexes.flatMap { u16(it).toList() }
        return encode(head + data)
    }

    private fun u16(value: Int) = byteArrayOf((value ushr 8).toByte(), value.toByte())

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
