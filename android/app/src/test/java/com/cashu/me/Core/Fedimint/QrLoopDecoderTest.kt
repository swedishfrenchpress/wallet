package com.cashu.me.Core.Fedimint

import com.cashu.me.Core.Fedimint.QrLoopFixture.FEDI_FRAMES
import com.cashu.me.Core.Fedimint.QrLoopFixture.FEDI_NOTES_BASE64
import com.cashu.me.Core.Fedimint.QrLoopFixture.FRAMES
import com.cashu.me.Core.Fedimint.QrLoopFixture.LOOP1_FOUNTAIN
import com.cashu.me.Core.Fedimint.QrLoopFixture.LOOP2_FOUNTAIN
import com.cashu.me.Core.Fedimint.QrLoopFixture.PAYLOAD
import com.cashu.me.Core.Fedimint.QrLoopFixture.loop1Data
import com.cashu.me.Core.Fedimint.QrLoopFixture.loop2Data
import java.security.MessageDigest
import java.util.Base64
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrLoopDecoderTest {
    /** Feeds [frames] in order; returns the first completed payload as text. */
    private fun QrLoopDecoder.feed(frames: List<String>): String? {
        for (frame in frames) receive(frame)?.let { return it.decodeToString() }
        return null
    }

    /** qrloop's exporter for one loop without fountains, for transmissions the fixture doesn't cover. */
    private fun loop(payload: ByteArray, chunkSize: Int, nonce: Int = 0): List<String> {
        val wrapped = ByteArray(4) { (payload.size ushr (8 * (3 - it))).toByte() } +
            MessageDigest.getInstance("MD5").digest(payload) + payload
        val count = (wrapped.size + chunkSize - 1) / chunkSize
        val padded = wrapped.copyOf(count * chunkSize)
        return (0 until count).map { i ->
            val head = byteArrayOf(nonce.toByte(), (count ushr 8).toByte(), count.toByte(), (i ushr 8).toByte(), i.toByte())
            Base64.getEncoder().encodeToString(head + padded.copyOfRange(i * chunkSize, (i + 1) * chunkSize))
        }
    }

    private fun withNotesParser(parser: (String) -> Long?, block: () -> Unit) {
        val previous = FedimintSupport.notesParser
        FedimintSupport.notesParser = parser
        try {
            block()
        } finally {
            FedimintSupport.notesParser = previous
        }
    }

    @Test
    fun referenceChunksReassembleThePayloadOnTheLastOne() {
        val decoder = QrLoopDecoder()
        val chunks = (0 until 8).map(::loop1Data)
        chunks.dropLast(1).forEach { assertNull(decoder.receive(it)) }
        assertEquals(PAYLOAD, decoder.receive(chunks.last())?.decodeToString())
    }

    @Test
    fun theReferenceLoopCompletesBeforeItsLastFrameThanksToItsFountain() {
        // In loop order the fountain (2,7,0,4) supplies chunk 7 once chunk 6 is in.
        val decoder = QrLoopDecoder()
        val loop1 = FRAMES.subList(0, LOOP2_FOUNTAIN - 1)
        loop1.dropLast(2).forEach { assertNull(decoder.receive(it)) }
        assertEquals(PAYLOAD, decoder.receive(loop1[loop1.size - 2])?.decodeToString())
    }

    @Test
    fun chunksArriveInAnyOrderFromEitherReplica() {
        // The nonce only numbers replicas, so chunks from both loops combine.
        val mixed = (0 until 8).map { if (it % 2 == 0) loop1Data(it) else loop2Data(it) }.shuffled(Random(7))
        assertEquals(PAYLOAD, QrLoopDecoder().feed(mixed))
    }

    @Test
    fun aMissedChunkIsRebuiltFromTheLoopsFountain() {
        // Chunk 7 is never seen; the loop-1 fountain (2,7,0,4) rebuilds it once 2, 0 and 4 are in.
        val frames = FRAMES.subList(0, LOOP2_FOUNTAIN - 1).filter { it != loop1Data(7) }
        assertEquals(PAYLOAD, QrLoopDecoder().feed(frames))
    }

    @Test
    fun aFountainSeenBeforeAnyDataFrameIsKept() {
        // Joining mid-loop on the loop-2 fountain (5,2,0,3), then never seeing chunk 5.
        val frames = listOf(FRAMES[LOOP2_FOUNTAIN]) + (0 until 8).filter { it != 5 }.map(::loop1Data)
        assertEquals(PAYLOAD, QrLoopDecoder().feed(frames))
    }

    @Test
    fun repeatedFramesDoNotAdvanceProgress() {
        val decoder = QrLoopDecoder()
        repeat(3) { decoder.receive(loop1Data(0)) }
        decoder.receive(loop2Data(0))
        decoder.receive(FRAMES[LOOP1_FOUNTAIN])
        assertEquals(1f / 8, decoder.progress)
        (1 until 4).forEach { decoder.receive(loop1Data(it)) }
        assertEquals(0.5f, decoder.progress)
    }

    @Test
    fun aCorruptedChunkFailsTheChecksumAndStartsOver() {
        val bytes = Base64.getDecoder().decode(loop1Data(3))
        bytes[10] = (bytes[10].toInt() xor 0x01).toByte()
        val corrupted = Base64.getEncoder().encodeToString(bytes)
        val decoder = QrLoopDecoder()
        assertNull(decoder.feed((0 until 8).map { if (it == 3) corrupted else loop1Data(it) }))
        assertEquals(0f, decoder.progress)
    }

    @Test
    fun aDifferentTransmissionReplacesAPartialOne() {
        val other = "fedimint notes for a second scan".encodeToByteArray()
        // Chunk size 41 also gives padded base64 frames, which the fixture doesn't.
        val otherFrames = loop(other, chunkSize = 41, nonce = 3)
        assertTrue(otherFrames.all { it.endsWith("=") })
        val decoder = QrLoopDecoder()
        (0 until 3).forEach { decoder.receive(loop1Data(it)) }
        assertEquals(other.decodeToString(), decoder.feed(otherFrames))
    }

    @Test
    fun recognisesQrLoopFramesButNotOtherScannableText() {
        FRAMES.forEach { assertTrue(it, QrLoopDecoder.isFrame(it)) }
        val fountainFragment = FedimintFountainFixture.sourceFrames(ByteArray(300) { it.toByte() }, slices = 3)[0]
        listOf(
            "cashuAeyJ0b2tlbiI6W3sibWludCI6Imh0dHBzOi8vODMzMy5zcGFjZTozMzM4IiwicHJvb2ZzIjpbXX1dfQ",
            "cashuBpGF0gaJhaUgArSaMTR9YJmFwgaNhYQFhc3hAOWE2ZGJiODQ3YmQyMzJiYTc2ZGIwZGYxOTcyMTZiMjlkM2I4Y2MxNDU1M2Nk",
            "creqApWF0ZXNhdGFhAWF1Y3NhdGFtgXgiaHR0cHM6Ly9ub2ZlZXMudGVzdG51dC5jYXNodS5zcGFjZQ",
            "lnbc2500u1pvjluezsp5zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zygspp5qqqsyqcyq5rqwzqfqqqsyqc",
            "ur:bytes/1-3/lpadaxcsencylobemohsgmoyadtaaeae",
            fountainFragment,
            "fed11qgqrgvnhwden5te0v9k8q6rp9ekh2arfdeukuet595cr2ttpd3jhq6rzve6zuer9wchxvetyd938gcewvdhk6tcqqysptkuvknc7erjgf4em3zfh90kffqf9srujn6q53d6r056e4apze5cw27h75",
            "BC1QAR0SRRR7XFKVY5L643LYDNW9RE59GTZZWF5MDQ",
            "bitcoin:bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq",
            "02" + "ab".repeat(32),
            "https://mint.example.com",
            // A frame in base64url (`-`/`_`) is not canonical qrloop output.
            "AAAIAAAAAAEsKW27MuBK6ha1BzuHBTWEVXNzcXBCTVRaSnowM1Q2QVZRel-_",
        ).forEach { assertFalse(it, QrLoopDecoder.isFrame(it)) }
    }

    @Test
    fun fedisBinaryNotesPayloadComesBackAsNotes() {
        val decoder = QrLoopDecoder()
        val payload = FEDI_FRAMES.firstNotNullOfOrNull(decoder::receive)
        assertArrayEquals(Base64.getDecoder().decode(FEDI_NOTES_BASE64), payload)
        // This is where the scanner used to give up: the payload is not text.
        assertNull(runCatching { payload!!.decodeToString(throwOnInvalidSequence = true) }.getOrNull())
        // Stand-in for the SDK: accepts any textual encoding of these notes' bytes.
        withNotesParser({ raw -> if (FedimintSupport.notesBytes(raw).contentEquals(payload)) 21L else null }) {
            val notes = FedimintSupport.notesFromQrLoopPayload(payload!!)
            assertNotNull(notes)
            assertArrayEquals(payload, FedimintSupport.notesBytes(notes!!))
        }
    }

    @Test
    fun fedimintTextNotesPayloadComesBackUnchanged() {
        val text = "fedimint" + FedimintFountainDecoder.base32Encode(ByteArray(40) { (it * 3 + 1).toByte() })
        withNotesParser({ raw -> if (raw == text) 21L else null }) {
            assertEquals(text, FedimintSupport.notesFromQrLoopPayload(text.encodeToByteArray()))
        }
    }

    @Test
    fun aPayloadThatIsNotNotesYieldsNoNotes() {
        withNotesParser({ null }) {
            assertNull(FedimintSupport.notesFromQrLoopPayload(PAYLOAD.encodeToByteArray()))
        }
    }
}
