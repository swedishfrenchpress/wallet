package com.cashu.me.Core.Fedimint

import java.security.MessageDigest
import org.junit.Test
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class FedimintFountainDecoderTest {
    private fun u32(value: Long) = ByteArray(4) { (value ushr (8 * (3 - it))).toByte() }

    private fun frame(message: ByteArray, simple: Int, index: Long, data: ByteArray): String {
        val checksum = MessageDigest.getInstance("SHA-256").digest(message).copyOf(4)
        val bytes = u32(simple.toLong()) + u32(message.size.toLong()) + checksum + u32(index) +
            byteArrayOf(data.size.toByte()) + data
        return "fedimint" + FedimintFountainDecoder.base32Encode(bytes)
    }

    private fun sourceFrames(message: ByteArray, slices: Int): List<String> {
        val length = (message.size + slices - 1) / slices
        val padded = message.copyOf(length * slices)
        return (0 until slices).map { i ->
            frame(message, slices, i.toLong(), padded.copyOfRange(i * length, (i + 1) * length))
        }
    }

    @Test
    fun chaCha20MatchesTheRfcKeystreamForAZeroKey() {
        val block = ChaCha20Rng(ByteArray(32)).generateBlock(0)
        val bytes = ByteArray(8) { i -> (block[i / 4] ushr (8 * (i % 4))).toByte() }
        // Keystream for key=0, nonce=0, counter=0 begins 76 b8 e0 ad a0 f1 3d 90.
        assertEquals("76b8e0ada0f13d90", bytes.joinToString("") { "%02x".format(it) })
        assertEquals(0x903df1a0ade0b876uL, ChaCha20Rng(ByteArray(32)).nextU64())
    }

    @Test
    fun base32RoundTrips() {
        val data = ByteArray(37) { (it * 7 + 3).toByte() }
        assertArrayEquals(data, FedimintFountainDecoder.base32Decode(FedimintFountainDecoder.base32Encode(data)))
    }

    @Test
    fun sourceFragmentsReassembleInAnyOrderAndWithDuplicates() {
        val message = ByteArray(28) { (it + 1).toByte() }
        val frames = sourceFrames(message, 4)
        val decoder = FedimintFountainDecoder()
        assertNull(decoder.receive(frames[2]))
        assertNull(decoder.receive(frames[2]))
        assertNull(decoder.receive(frames[0]))
        assertNull(decoder.receive(frames[3]))
        assertTrue(decoder.progress in 0.5f..0.99f)
        assertArrayEquals(message, decoder.receive(frames[1]))
    }

    @Test
    fun aCorruptedFragmentIsRejectedByTheChecksum() {
        val message = ByteArray(20) { (it + 5).toByte() }
        val frames = sourceFrames(message, 2).toMutableList()
        val bad = frame(message, 2, 1, ByteArray(10) { 0x55 })
        val decoder = FedimintFountainDecoder()
        decoder.receive(frames[0])
        assertNull(decoder.receive(bad))
        assertEquals(0f, decoder.progress)
    }

    @Test
    fun recognisesFragmentsButNotOtherStrings() {
        val message = ByteArray(9) { it.toByte() }
        assertTrue(FedimintFountainDecoder.isFragment(sourceFrames(message, 1)[0]))
        assertFalse(FedimintFountainDecoder.isFragment("fedimint:notafragment"))
        assertFalse(FedimintFountainDecoder.isFragment("lnbc1..."))
    }

    @Test
    fun combinedFragmentSelectionIsDeterministicAndInRange() {
        val checksum = byteArrayOf(1, 2, 3, 4)
        repeat(50) { i ->
            val index = 10L + i
            val picked = FedimintFountainDecoder.chooseFragments(10, checksum, index)
            assertEquals(picked, FedimintFountainDecoder.chooseFragments(10, checksum, index))
            assertTrue(picked.isNotEmpty() && picked.all { it in 0 until 10 } && picked.toSet().size == picked.size)
        }
    }
}
