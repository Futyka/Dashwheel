package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlcanFramesTest {
    @Test fun parsesStandardData() {
        assertEquals("408 [3] 01 A2 FF", SlcanFrames.parse("t408301A2FF"))
    }
    @Test fun parsesExtendedData() {
        assertEquals("18DAF110 [2] 12 34", SlcanFrames.parse("T18DAF11021234"))
    }
    @Test fun parsesRemoteFrames() {
        assertEquals("408 [2]  RTR", SlcanFrames.parse("r4082"))
    }
    @Test fun rejectsMalformedFrames() {
        assertNull(SlcanFrames.parse("V123"))
        assertNull(SlcanFrames.parse("t4083AA"))
        assertNull(SlcanFrames.parse("t4089"))
        assertNull(SlcanFrames.parse("tFFF1AA"))
        assertNull(SlcanFrames.parse("t4081GG"))
    }
}
