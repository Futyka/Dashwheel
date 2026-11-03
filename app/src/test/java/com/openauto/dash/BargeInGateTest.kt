package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Test

class BargeInGateTest {
    /** The echo at [rms] for long enough that the gate may open. */
    private fun BargeInGate.settleOn(rms: Float) = repeat(9) { assertEquals(0, hear(rms)) }

    @Test
    fun geminisOwnEchoStaysOut() {
        val gate = BargeInGate()
        gate.settleOn(0.1f)
        repeat(50) { assertEquals(0, gate.hear(0.12f)) }
    }

    @Test
    fun aVoiceOverTheEchoGoesUpWithTheChunksBeforeIt() {
        val gate = BargeInGate()
        gate.settleOn(0.02f)
        assertEquals(0, gate.hear(0.2f))
        assertEquals(0, gate.hear(0.2f))
        assertEquals(3, gate.hear(0.2f))
        assertEquals(1, gate.hear(0.01f))
    }

    @Test
    fun aBumpAloneDoesNotOpen() {
        val gate = BargeInGate()
        gate.settleOn(0.02f)
        assertEquals(0, gate.hear(0.5f))
        assertEquals(0, gate.hear(0.02f))
        assertEquals(0, gate.hear(0.5f))
        assertEquals(0, gate.hear(0.5f))
        assertEquals(0, gate.hear(0.02f))
    }

    @Test
    fun theGateClosesAgain() {
        val gate = BargeInGate()
        gate.settleOn(0.02f)
        gate.hear(0.2f)
        gate.hear(0.2f)
        assertEquals(3, gate.hear(0.2f))
        repeat(15) { assertEquals(1, gate.hear(0.02f)) }
        assertEquals(0, gate.hear(0.02f))
    }

    @Test
    fun rmsOfFullScaleIsOneAndSilenceZero() {
        val full = ByteArray(4) { if (it % 2 == 0) 0 else Byte.MIN_VALUE }
        assertEquals(1f, rms(full, 4), 0.001f)
        assertEquals(0f, rms(ByteArray(4), 4), 0f)
    }
}

class BargeInGateEchoArrivalTest {
    /** The answer is queued, the cabin is quiet for a few chunks, then the echo arrives loud. */
    @Test
    fun theAnswersOwnFirstWordsDoNotOpenTheGate() {
        val gate = BargeInGate()
        repeat(3) { assertEquals(0, gate.hear(0.0005f)) }
        listOf(0.05f, 0.055f, 0.03f, 0.06f, 0.058f, 0.02f, 0.07f, 0.06f, 0.07f, 0.065f, 0.07f).forEach { assertEquals(0, gate.hear(it)) }
    }

    /** The answer starts soft and rises: the rise is Gemini's voice, not the driver's. */
    @Test
    fun aSoftStartThenTheFullVoiceDoesNotOpenTheGate() {
        val gate = BargeInGate()
        repeat(3) { assertEquals(0, gate.hear(0.0005f)) }
        listOf(0.02f, 0.06f, 0.07f, 0.08f, 0.07f, 0.08f, 0.06f, 0.08f, 0.07f).forEach { assertEquals(0, gate.hear(it)) }
        // Settled on 0.08: the voice at that level, and a stressed word, go on staying out.
        listOf(0.08f, 0.07f, 0.15f, 0.08f, 0.15f, 0.15f, 0.08f).forEach { assertEquals(0, gate.hear(it)) }
    }

    @Test
    fun aVoiceWellOverTheEchoStillGoesUp() {
        val gate = BargeInGate()
        repeat(3) { assertEquals(0, gate.hear(0.0005f)) }
        repeat(9) { assertEquals(0, gate.hear(0.05f)) }
        assertEquals(0, gate.hear(0.2f))
        assertEquals(0, gate.hear(0.2f))
        assertEquals(3, gate.hear(0.2f))
    }
}

class MicGainTest {
    private fun chunk(peak: Int) = ByteArray(400).also { pcm ->
        for (i in 0 until 200) {
            val v = if (i % 2 == 0) peak else -peak
            pcm[i * 2] = v.toByte()
            pcm[i * 2 + 1] = (v shr 8).toByte()
        }
    }

    @Test
    fun aQuietVoiceIsBroughtUpAndALoudOneIsNotClipped() {
        val gain = MicGain()
        val quiet = chunk(400)
        gain.apply(quiet, quiet.size, learn = true)
        assertEquals(32f, gain.gain, 0f)
        assertEquals(400 * 32 / 32768f, rms(quiet, quiet.size), 0.01f)
        val loud = chunk(8000)
        gain.apply(loud, loud.size, learn = true)
        assertEquals(16384 / 32768f, rms(loud, loud.size), 0.01f)
    }

    @Test
    fun whileGeminiSpeaksTheGainIsKeptNotLearnt() {
        val gain = MicGain()
        gain.apply(chunk(400), 400, learn = true)
        val echo = chunk(8000)
        gain.apply(echo, echo.size, learn = false)
        assertEquals(32f, gain.gain, 0f)
        assertEquals(1f, rms(echo, echo.size), 0.01f)
    }
}
