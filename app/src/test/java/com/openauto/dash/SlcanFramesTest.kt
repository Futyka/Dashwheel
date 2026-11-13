package com.openauto.dash

import org.junit.Assert.*
import org.junit.Test

class SlcanFramesTest {
    @Test fun standardFrame() {
        val frame = SlcanFrames.parse("t408301A2FF")!!
        assertEquals(0x408L, frame.id)
        assertFalse(frame.extended)
        assertFalse(frame.remote)
        assertEquals(3, frame.dlc)
        assertEquals("01 A2 FF", frame.hexData())
    }

    @Test fun extendedFrame() {
        val frame = SlcanFrames.parse("T18DAF11021234")!!
        assertEquals(0x18DAF110L, frame.id)
        assertTrue(frame.extended)
        assertEquals("12 34", frame.hexData())
    }

    @Test fun remoteFramesAndTimestamps() {
        val remote = SlcanFrames.parse("r4082")!!
        assertTrue(remote.remote)
        assertEquals(2, remote.dlc)
        assertTrue(remote.data.isEmpty())
        assertEquals("AB", SlcanFrames.parse("t1001AB000A")!!.hexData())
    }

    @Test fun malformedInputAndFirmwareResponsesAreIgnored() {
        for (raw in listOf("V123", "z", "t4083AA", "t4089", "tFFF1AA", "t4081GG", "t4081ABxxx", "TFFFFFFFF0")) {
            assertNull(raw, SlcanFrames.parse(raw))
        }
    }
}
