package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Test

/** The key each MCU message is kept under in the raw view (issue #122). */
class McuReaderKeyTest {

    private fun bytes(hex: String) = hex.split(' ').map { it.toInt(16) }

    @Test
    fun anRzcFrameIsKeptUnderItsType() {
        assertEquals("65.0C", McuReader.keyOf(65, bytes("41 fd 0c 38 80 00")))
    }

    @Test
    fun aHiworldFrameIsKeptUnderItsTypeToo() {
        // From the Hiworld Peugeot 508 box's log: the radar frame and a trip frame, no longer one key.
        assertEquals("65.41", McuReader.keyOf(65, bytes("41 5a a5 0c 41 ff ff ff ff ff ff ff ff 00 00 3f 04 87")))
        assertEquals("65.14", McuReader.keyOf(65, bytes("41 5a a5 0a 14 00 41 00 23 07 ed 00 00 00 00 75")))
    }

    @Test
    fun otherMessagesKeepTheCommandAlone() {
        assertEquals("41", McuReader.keyOf(41, bytes("29 7a")))
        assertEquals("65", McuReader.keyOf(65, bytes("41 5a")))
    }
}
