package com.openauto.unitproto.fyt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FytProtocolTest {

    @Test
    fun onlyARead_answersWithABody() {
        assertTrue(FytProtocol.moduleReplyHasBody(FytProtocol.TX_GET))
        for (tx in listOf(FytProtocol.TX_CMD, FytProtocol.TX_REGISTER, FytProtocol.TX_UNREGISTER)) {
            assertFalse("tx $tx", FytProtocol.moduleReplyHasBody(tx))
        }
    }

    @Test
    fun theCanbusId_splitsIntoDecoderAndCar() {
        // honda-customizer's RZC Civic profile 0x29012A: box 0x012A, car 0x29.
        assertEquals(0x012A, FytCanbus.decoderOf(0x29012A))
        assertEquals(0x29, FytCanbus.carOf(0x29012A))
        assertEquals(0x0141, FytCanbus.decoderOf(0xB0141))
        assertEquals(0xB, FytCanbus.carOf(0xB0141))
    }

    @Test
    fun speedAndRpm_takePlausibleValuesOnly() {
        assertEquals(57f, FytCanbus.speedKmh(intArrayOf(57)))
        assertNull(FytCanbus.speedKmh(intArrayOf(-1)))
        assertNull(FytCanbus.speedKmh(intArrayOf(999)))
        assertNull(FytCanbus.speedKmh(null))
        assertNull(FytCanbus.speedKmh(intArrayOf()))
        assertEquals(2450, FytCanbus.rpm(intArrayOf(2450)))
        assertNull(FytCanbus.rpm(intArrayOf(65535)))
    }

    @Test
    fun finderKeys_roundTrip() {
        assertEquals("fyt:7:1031#0", FytKey.int(7, 1031, 0))
        assertEquals(FytKey.Parsed(7, 1031, 'i', 0), FytKey.parse("fyt:7:1031#0"))
        assertEquals(FytKey.Parsed(2, 7, 's', 1), FytKey.parse("fyt:2:7#s1"))
        assertEquals(FytKey.Parsed(7, 148, 'f', 0), FytKey.parse(FytKey.float(7, 148, 0)))
        assertNull(FytKey.parse("can:65:0C38"))
        assertNull(FytKey.parse("fyt:7:1031"))
        assertNull(FytKey.parse("fyt:x:1031#0"))
    }

    @Test
    fun anUpdate_becomesOneValuePerPiece() {
        val v = FytKey.values(2, 7, intArrayOf(1, 2), floatArrayOf(0.5f), arrayOf("0", "Pixel", null))
        assertEquals(
            mapOf("fyt:2:7#0" to "1", "fyt:2:7#1" to "2", "fyt:2:7#f0" to "0.5", "fyt:2:7#s0" to "0", "fyt:2:7#s1" to "Pixel"),
            v
        )
        assertTrue(FytKey.values(7, 1031, null, null, null).isEmpty())
    }

    @Test
    fun moduleNames_areKnownForTheCommonOnes() {
        assertEquals("canbus", FytModules.name(FytModules.CANBUS))
        assertEquals("bt", FytModules.name(FytModules.BT))
        assertEquals("module42", FytModules.name(42))
        assertTrue(FytCanbus.U_CUR_SPEED in FytCanbus.SNIFF_RANGE)
        assertTrue(FytCanbus.GENERIC.contains(FytCanbus.U_CANBUS_ID))
    }

    @Test
    fun theVwCodes_areReadAsTheTesterLoggedThem() {
        assertTrue(FytVw.isVw("Volkswagen"))
        assertTrue(FytVw.isVw(" VW "))
        assertTrue(FytVw.isVw("SEAT"))
        assertFalse(FytVw.isVw("Peugeot"))
        assertFalse(FytVw.isVw(""))
        assertEquals(true, FytVw.handbrake(intArrayOf(0)))
        assertEquals(false, FytVw.handbrake(intArrayOf(1)))
        assertNull(FytVw.handbrake(intArrayOf(7)))
        assertEquals(12.34, FytVw.volts(intArrayOf(1234))!!, 1e-9)
        assertNull(FytVw.volts(intArrayOf(0)))
        assertEquals(57.5f, FytVw.speedKmh(intArrayOf(5750)))
        assertNull(FytVw.speedKmh(intArrayOf(-100)))
        assertEquals(-4.5, FytVw.outsideTemp(intArrayOf(-45))!!, 1e-9)
        assertNull(FytVw.outsideTemp(intArrayOf(9999)))
        assertEquals(187_654.0, FytVw.odometerKm(intArrayOf(187_654))!!, 1e-9)
        assertEquals(850, FytVw.rpm(intArrayOf(850)))
        assertTrue(FytVw.CODES.all { it in FytCanbus.SNIFF_RANGE && it !in FytCanbus.GENERIC })
    }
}
