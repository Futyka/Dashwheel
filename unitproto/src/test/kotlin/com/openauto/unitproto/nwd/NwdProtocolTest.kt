package com.openauto.unitproto.nwd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NwdProtocolTest {
    @Test
    fun volts_areTenths_andZeroIsNoValue() {
        assertEquals(12.6, NwdProtocol.volts(126)!!, 0.001)
        assertEquals(12.6, NwdProtocol.volts(-126)!!, 0.001)
        assertNull(NwdProtocol.volts(0))
    }

    @Test
    fun headlights_areBitTwoOfTheMcuInputs() {
        assertTrue(NwdProtocol.headlightsOn(2))
        assertTrue(NwdProtocol.headlightsOn(7))
        assertFalse(NwdProtocol.headlightsOn(5))
    }
}
