package com.cashu.me.Core.Fedimint

/**
 * Frames from the reference `qrloop` 1.4.1 exporter, the format Fedi's animated
 * ecash QR uses: `dataToFrames(PAYLOAD, 40, 2)`. That is 8 chunks of 40 bytes,
 * two replica loops (nonces 0 and 1), and one fountain frame per loop
 * (FRAMES[1] XORs chunks 2,7,0,4; FRAMES[10] XORs chunks 5,2,0,3).
 *
 * PAYLOAD is synthetic notes-shaped text, not real ecash.
 */
internal object QrLoopFixture {
    const val PAYLOAD =
        "ssqpBMTZJz03T6AVQz_G01uGyeSBxAg_-ZYFwqeVn9okeLHaPetl8AnsZvPIMFhhDupK-ae_ZPUTI_4_3lg9CsjHmfpQxk6Yl2Gm" +
            "B_Uz69JbQgLBO0EvZVbc0xH2WXw1gCMjYnU503oUgphFhCJ6i4gqx4rkktYC9b1KAs83bVuYhEDFNUfHCVKXU1LFmWHKb4RhJDlD" +
            "iHgSB9QrHr5ipQYxn5T8edJf-VPXWCTGOWaEYeAiN26vKK_2kr_7nJKI60D4zPChavctoqFdmcw5aovQV1xJrZ34HSDvgHqTu279"

    val FRAMES = listOf(
        "AAAIAAAAAAEsKW27MuBK6ha1BzuHBTWEVXNzcXBCTVRaSnowM1Q2QVZRel9H",
        "ZAAEAAIABwAAAARQZkJqZC3iO8turlz6AGuNRnGfGC9MX0gyFzUVDRlxTSNQQh0GL0lb",
        "AAAIAAEwMXVHeWVTQnhBZ18tWllGd3FlVm45b2tlTEhhUGV0bDhBbnNadlBJ",
        "AAAIAAJNRmhoRHVwSy1hZV9aUFVUSV80XzNsZzlDc2pIbWZwUXhrNllsMkdt",
        "AAAIAANCX1V6NjlKYlFnTEJPMEV2WlZiYzB4SDJXWHcxZ0NNalluVTUwM29V",
        "AAAIAARncGhGaENKNmk0Z3F4NHJra3RZQzliMUtBczgzYlZ1WWhFREZOVWZI",
        "AAAIAAVDVktYVTFMRm1XSEtiNFJoSkRsRGlIZ1NCOVFySHI1aXBRWXhuNVQ4",
        "AAAIAAZlZEpmLVZQWFdDVEdPV2FFWWVBaU4yNnZLS18ya3JfN25KS0k2MEQ0",
        "AAAIAAd6UENoYXZjdG9xRmRtY3c1YW92UVYxeEpyWjM0SFNEdmdIcVR1Mjc5",
        "AQAIAAAAAAEsKW27MuBK6ha1BzuHBTWEVXNzcXBCTVRaSnowM1Q2QVZRel9H",
        "ZAAEAAUAAgAAAANMT3dmDhDNXfEbi0DCU3nNXHi+LRkvOSgUXxhRCC04YQVie0JjTiNH",
        "AQAIAAEwMXVHeWVTQnhBZ18tWllGd3FlVm45b2tlTEhhUGV0bDhBbnNadlBJ",
        "AQAIAAJNRmhoRHVwSy1hZV9aUFVUSV80XzNsZzlDc2pIbWZwUXhrNllsMkdt",
        "AQAIAANCX1V6NjlKYlFnTEJPMEV2WlZiYzB4SDJXWHcxZ0NNalluVTUwM29V",
        "AQAIAARncGhGaENKNmk0Z3F4NHJra3RZQzliMUtBczgzYlZ1WWhFREZOVWZI",
        "AQAIAAVDVktYVTFMRm1XSEtiNFJoSkRsRGlIZ1NCOVFySHI1aXBRWXhuNVQ4",
        "AQAIAAZlZEpmLVZQWFdDVEdPV2FFWWVBaU4yNnZLS18ya3JfN25KS0k2MEQ0",
        "AQAIAAd6UENoYXZjdG9xRmRtY3c1YW92UVYxeEpyWjM0SFNEdmdIcVR1Mjc5",
    )

    /** Loop 1 (nonce 0): data chunk i is at FRAMES[0] for i = 0, else FRAMES[i + 1]. */
    fun loop1Data(index: Int): String = if (index == 0) FRAMES[0] else FRAMES[index + 1]

    /** Loop 2 (nonce 1): same chunks, numbered as a replica. */
    fun loop2Data(index: Int): String = if (index == 0) FRAMES[9] else FRAMES[index + 10]

    const val LOOP1_FOUNTAIN = 1
    const val LOOP2_FOUNTAIN = 10

    /**
     * What Fedi actually shows for base64 notes: its `ecashToQrFrameData` frames the
     * raw bytes behind the base64 (`Buffer.from(notes, 'base64')`), with qrloop's
     * defaults (120-byte chunks, one loop). The payload is binary, not UTF-8.
     * Synthetic bytes, not real ecash.
     */
    const val FEDI_NOTES_BASE64 =
        "1IUQRlIyq0i/djH5Kw7E6LQjASW0ABw9nfNBQRjoZZtHxlzAYcXEi0vadT5ny+dU6gWLGNVtxOgEP5DJREvsb8NDhP/Za0ln1S+O" +
            "3N0ogRSa8fV/FrtwVTnT+QfsfV/U0d0gXcgSm/n2M95UKX1QETDndz94D5aYOTkluJSEk/l0fhxhsTAnvmVWod1bPa48qCPxewuv" +
            "cpdhPVyvR3nL8cRyWRPgBXmg9r9pwF8FXG7Pk53jFgcLCBVobx+xrqb8HRbYBBJ/S2SVTt9Awe235XTtklfhheS3wSYXsVD4XZLw" +
            "q67sWuIwszYyGA1KzoEOk6gerpQjUH7SkIySm9Wk20+Bp34="

    val FEDI_FRAMES = listOf(
        "AAADAAAAAAEEBKIutIuYyx/Cfn3DK8ewZ9SFEEZSMqtIv3Yx+SsOxOi0IwEltAAcPZ3zQUEY6GWbR8ZcwGHFxItL2nU+Z8vnVOoF" +
            "ixjVbcToBD+QyURL7G/DQ4T/2WtJZ9UvjtzdKIEUmvH1fxa7cFU50/kH7H1f1NHdIF0=",
        "AAADAAHIEpv59jPeVCl9UBEw53c/eA+WmDk5JbiUhJP5dH4cYbEwJ75lVqHdWz2uPKgj8XsLr3KXYT1cr0d5y/HEclkT4AV5oPa/" +
            "acBfBVxuz5Od4xYHCwgVaG8fsa6m/B0W2AQSf0tklU7fQMHtt+V07ZJX4YXkt8EmF7E=",
        "AAADAAJQ+F2S8Kuu7FriMLM2MhgNSs6BDpOoHq6UI1B+0pCMkpvVpNtPgad+AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    )
}
