package com.openauto.unitproto.topway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopwayProtocolTest {

    @Test
    fun reverse_isArg1OneOrZero() {
        assertEquals(true, TopwayProtocol.reverse(1))
        assertEquals(false, TopwayProtocol.reverse(0))
        assertNull(TopwayProtocol.reverse(2))
        assertTrue(TopwayProtocol.isReverse(523))
        assertTrue(TopwayProtocol.isReverse(40732))
        assertFalse(TopwayProtocol.isReverse(1281))
    }

    @Test
    fun canboxFrames_becomeByteValuesForTheFinder() {
        assertEquals("tw:1281:64[]", TopwayProtocol.frameKey(TopwayProtocol.CH_CANBOX, 0x40))
        assertEquals("00 7F 80 FF", TopwayProtocol.hex(byteArrayOf(0, 0x7F, 0x80.toByte(), 0xFF.toByte())))
        assertTrue(TopwayProtocol.carriesFrames(1281))
        assertFalse(TopwayProtocol.carriesFrames(523))
    }

    @Test
    fun opensOnlyWhatItReads() {
        // As the car app numbers them: a short each, 0x9F1C negative.
        assertEquals(listOf<Short>(523, -24804, 1281, 267), TopwayProtocol.LISTENED.toList())
    }
}
