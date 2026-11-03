package com.openauto.dash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The microphone waits for a turn instruction, not for a player an app keeps open. */
class GuidanceGateTest {

    @Test
    fun aTurnInstructionMutesTheMicrophone() {
        val gate = GuidanceGate(10_000L)
        assertTrue(gate.update(talking = true, now = 1_000L))
        assertTrue(gate.update(talking = true, now = 4_000L))
        assertFalse(gate.update(talking = false, now = 5_000L))
    }

    @Test
    fun aPlayerKeptOpenStopsCountingAfterAWhile() {
        val gate = GuidanceGate(10_000L)
        assertTrue(gate.update(talking = true, now = 1_000L))
        assertTrue(gate.update(talking = true, now = 11_000L))
        assertFalse(gate.update(talking = true, now = 11_250L))
        assertFalse(gate.update(talking = true, now = 60_000L))
        // Once it stops, the next instruction counts again from its start.
        assertFalse(gate.update(talking = false, now = 61_000L))
        assertTrue(gate.update(talking = true, now = 62_000L))
    }

    @Test
    fun resetForgetsTheRunningPlayer() {
        val gate = GuidanceGate(10_000L)
        assertTrue(gate.update(talking = true, now = 1_000L))
        gate.reset()
        assertTrue(gate.update(talking = true, now = 20_000L))
    }
}
