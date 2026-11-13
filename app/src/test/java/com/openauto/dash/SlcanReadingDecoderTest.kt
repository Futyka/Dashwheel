package com.openauto.dash

import org.junit.Assert.*
import org.junit.Test

class SlcanReadingDecoderTest {
    @Test fun computesConfiguredValueFromReceivedData() {
        val frame = SlcanFrames.parse("t408301A2FF")!!
        assertEquals(162.0, SlcanReadingDecoder.value(frame, 0x408, false, "B")!!, 0.0001)
        assertEquals(0x01A2.toDouble(), SlcanReadingDecoder.value(frame, 0x408, false, "A*256+B")!!, 0.0001)
    }
    @Test fun ignoresUnrelatedAndRemoteFrames() {
        val frame = SlcanFrames.parse("t408301A2FF")!!
        assertNull(SlcanReadingDecoder.value(frame, 0x409, false, "A"))
        assertNull(SlcanReadingDecoder.value(frame, 0x408, true, "A"))
        assertNull(SlcanReadingDecoder.value(SlcanFrames.parse("r4083")!!, 0x408, false, "A"))
    }
}
