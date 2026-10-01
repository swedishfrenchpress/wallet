package com.cashu.me.Views.Components

import com.cashu.me.Core.Fedimint.FedimintFountainEncoder
import com.cashu.me.Core.Fedimint.FedimintSupport
import com.cashu.me.Core.Fedimint.QrLoopDecoder
import com.cashu.me.Core.Fedimint.QrLoopFixture
import java.util.Base64
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScannedCodeKindTest {
    // Notes whose bytes happen to read as a valid qrloop header (count 1, index 0).
    private val frameShapedNotes = Base64.getEncoder().encodeToString(
        ByteArray(60) { (it * 13 + 7).toByte() }.also { it[0] = 2; it[1] = 0; it[2] = 1; it[3] = 0; it[4] = 0 },
    )
    private val fountainFragment =
        FedimintFountainEncoder(ByteArray(300) { it.toByte() }, maxFragmentBytes = 100).nextFrame()
    private val cashuPayloads = listOf(
        "cashuAeyJ0b2tlbiI6W3sibWludCI6Imh0dHBzOi8vODMzMy5zcGFjZTozMzM4IiwicHJvb2ZzIjpbXX1dfQ",
        "cashuBpGF0gaJhaUgArSaMTR9YJmFwgaNhYQFhc3hAOWE2ZGJiODQ3YmQyMzJiYTc2ZGIwZGYxOTcyMTZiMjlkM2I4Y2MxNDU1M2Nk",
        "creqApWF0ZXNhdGFhAWF1Y3NhdGFtgXgiaHR0cHM6Ly9ub2ZlZXMudGVzdG51dC5jYXNodS5zcGFjZQ",
        "lnbc2500u1pvjluezsp5zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zygspp5qqqsyqcyq5rqwzqfqqqsyqc",
        "BC1QAR0SRRR7XFKVY5L643LYDNW9RE59GTZZWF5MDQ",
        "bitcoin:bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq",
        "02" + "ab".repeat(32),
        "https://mint.example.com",
    )
    private var previousParser: ((String) -> Long?)? = null

    @Before
    fun saveParser() {
        previousParser = FedimintSupport.notesParser
    }

    @After
    fun restoreParser() {
        FedimintSupport.notesParser = previousParser
    }

    private fun withFedimint() {
        FedimintSupport.notesParser = { raw -> if (raw == frameShapedNotes) 21L else null }
    }

    @Test
    fun cashuFlavorOnlyRoutesUrFramesAsAnimated() {
        FedimintSupport.notesParser = null
        assertEquals(ScannedCodeKind.CashuAnimated, scannedCodeKind("ur:bytes/1-3/lpadaxcsencylobemohsgmoyadtaaeae"))
        (QrLoopFixture.FRAMES + fountainFragment + cashuPayloads).forEach {
            assertEquals(it, ScannedCodeKind.Static, scannedCodeKind(it))
        }
    }

    @Test
    fun cashuPayloadsKeepTheirRoutesWhenFedimintIsAvailable() {
        withFedimint()
        assertEquals(ScannedCodeKind.CashuAnimated, scannedCodeKind("ur:bytes/1-3/lpadaxcsencylobemohsgmoyadtaaeae"))
        cashuPayloads.forEach { assertEquals(it, ScannedCodeKind.Static, scannedCodeKind(it)) }
    }

    @Test
    fun wholeNotesWinOverTheQrLoopFrameShape() {
        withFedimint()
        assertTrue(QrLoopDecoder.isFrame(frameShapedNotes))
        assertEquals(ScannedCodeKind.Static, scannedCodeKind(frameShapedNotes))
    }

    @Test
    fun animatedFedimintFramesReachTheirDecoders() {
        withFedimint()
        (QrLoopFixture.FRAMES + QrLoopFixture.FEDI_FRAMES).forEach { assertEquals(it, ScannedCodeKind.QrLoop, scannedCodeKind(it)) }
        assertEquals(ScannedCodeKind.FedimintFountain, scannedCodeKind(fountainFragment))
    }
}
