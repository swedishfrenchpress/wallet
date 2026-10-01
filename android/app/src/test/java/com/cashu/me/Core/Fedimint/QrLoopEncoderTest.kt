package com.cashu.me.Core.Fedimint

import com.cashu.me.Core.Fedimint.QrLoopFixture.FEDI_FRAMES
import com.cashu.me.Core.Fedimint.QrLoopFixture.FEDI_NOTES_BASE64
import com.cashu.me.Core.Fedimint.QrLoopFixture.FRAMES
import com.cashu.me.Core.Fedimint.QrLoopFixture.PAYLOAD
import java.util.Base64
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrLoopEncoderTest {
    @Test
    fun matchesTheReferenceExporterByteForByte() {
        // Both fixtures come from qrloop 1.4.1's dataToFrames, fountain frames included.
        assertEquals(FRAMES, QrLoopEncoder.frames(PAYLOAD.encodeToByteArray(), dataSize = 40, loops = 2))
        assertEquals(FEDI_FRAMES, QrLoopEncoder.frames(Base64.getDecoder().decode(FEDI_NOTES_BASE64)))
    }

    @Test
    fun longNotesRoundTripEvenWhenJoinedMidLoopWithMissedFrames() {
        val notes = Base64.getEncoder().encodeToString(Random(3).nextBytes(2026)).encodeToByteArray()
        val frames = QrLoopEncoder.frames(notes, dataSize = 200)
        assertTrue(frames.size > 10)
        assertTrue(frames.all(QrLoopDecoder::isFrame))
        // Start partway through, skip every seventh frame, and keep looping like the screen does.
        val decoder = QrLoopDecoder()
        var payload: ByteArray? = null
        var shown = 0
        var i = 5
        while (payload == null && shown < frames.size * 3) {
            if (shown % 7 != 3) payload = decoder.receive(frames[i % frames.size])
            i++
            shown++
        }
        assertArrayEquals(notes, payload)
    }
}
