package com.cashu.me.Views.Components

import com.cashu.me.Core.Fedimint.FedimintSupport
import com.cashu.me.Core.Fedimint.QrLoopDecoder
import java.util.Base64
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-side QR frame tests. The animated (NUT-16) path needs the CDK native
 * library and is exercised on-device in `QRCodeViewInstrumentedTest`.
 */
class QRCodeViewTest {
    @Test
    fun staticOnlyKeepsPayloadUnchanged() {
        val sequence = qrFrameSequence(
            content = "lnbc1static",
            staticOnly = true,
            chunkSize = QRSize.Small.chunkSize,
        )

        assertEquals("lnbc1static", sequence.firstFrame)
        assertEquals(1, sequence.totalParts)
        assertNull(sequence.nextFrame)
    }

    @Test
    fun shortPayloadStaysASingleStaticFrame() {
        val content = "cashuAshort"
        val sequence = qrFrameSequence(
            content = content,
            staticOnly = false,
            chunkSize = QRSize.Large.chunkSize,
        )

        assertEquals(content, sequence.firstFrame)
        assertEquals(1, sequence.totalParts)
        assertNull(sequence.nextFrame)
    }

    @Test
    fun longNonTokenPayloadFallsBackToStatic() {
        // NUT-16 envelopes only carry Cashu tokens; a long non-token string
        // (no valid token encoding) must not be UR-fragmented.
        val content = "lnbc1" + "abcdef0123456789".repeat(30)
        val sequence = qrFrameSequence(
            content = content,
            staticOnly = false,
            chunkSize = QRSize.Small.chunkSize,
        )

        assertEquals(content, sequence.firstFrame)
        assertEquals(1, sequence.totalParts)
        assertNull(sequence.nextFrame)
    }

    @Test
    fun longFedimintNotesAnimateAsQrLoopFramesCarryingTheNotesText() {
        val notes = Base64.getEncoder().encodeToString(Random(5).nextBytes(2026))
        val previous = FedimintSupport.notesParser
        FedimintSupport.notesParser = { raw -> if (raw == notes) 2L else null }
        try {
            val sequence = qrFrameSequence(content = notes, staticOnly = false, chunkSize = QRSize.Large.chunkSize)
            assertTrue(sequence.totalParts > 1)
            val next = sequence.nextFrame!!
            val shown = listOf(sequence.firstFrame) + List(sequence.totalParts * 2) { next() }
            assertTrue(shown.all(QrLoopDecoder::isFrame))
            // The animation cycles: after one full loop it is back on the first frame.
            assertEquals(sequence.firstFrame, shown[sequence.totalParts])

            val decoder = QrLoopDecoder()
            val payload = shown.firstNotNullOfOrNull(decoder::receive)!!
            assertEquals(notes, payload.decodeToString())
            assertEquals(notes, FedimintSupport.notesFromQrLoopPayload(payload))
        } finally {
            FedimintSupport.notesParser = previous
        }
    }
}
