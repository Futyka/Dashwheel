package com.openauto.dash

import org.junit.Assert.*
import org.junit.Test

class Smart451CanPresetTest {
    @Test fun constantsMatchFieldObservations() {
        assertEquals(500_000, Smart451CanPreset.BITRATE_BITS_PER_SECOND)
        assertEquals("S6", Smart451CanPreset.SLCAN_BITRATE_COMMAND)
        assertEquals(0x408L, Smart451CanPreset.FUEL_CANDIDATE_ID)
    }

    @Test fun neverGuessesUncalibratedFuel() {
        val frame = SlcanFrames.parse("t408301A2FF")!!
        assertNull(Smart451CanPreset.fuelPercent(frame, null))
        assertNull(Smart451CanPreset.fuelPercent(frame, ""))
        assertEquals(1.0, Smart451CanPreset.fuelPercent(frame, "A")!!, 0.00001)
        assertNull(Smart451CanPreset.fuelPercent(frame, "A*256+B"))
        assertNull(Smart451CanPreset.fuelPercent(SlcanFrames.parse("t409301A2FF")!!, "A"))
    }
}
